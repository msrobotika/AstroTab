// Satélites: TLE de CelesTrak (opcional, con la red activada) y SGP4 de satellite.js (MIT) en el propio dispositivo.
// Visible = sobre el horizonte, iluminado por el Sol (sombra cónica, como en Android) y el observador con Sol < −6°.
'use strict';
const Sats = (() => {
  const ISS = 'https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=TLE';
  const VISUAL = 'https://celestrak.org/NORAD/elements/gp.php?GROUP=visual&FORMAT=TLE';
  const MAX = 200;
  // Checksum oficial TLE: suma de dígitos y '-' cuenta 1, módulo 10.
  const valid = (l, p) => l.length === 69 && l.startsWith(p) && /\d$/.test(l) &&
    [...l.slice(0, 68)].reduce((s, c) => s + (c >= '0' && c <= '9' ? +c : c === '-' ? 1 : 0), 0) % 10 === +l[68];
  function parse(raw) {
    if (raw.length > 2e6) throw Error('TLE demasiado grande');
    const lines = raw.split(/\r?\n/).map(l => l.trimEnd()).filter(Boolean), out = [];
    for (let i = 0; i < lines.length && out.length < MAX;) {
      const name = lines[i].startsWith('1 ') ? 'NORAD ' + lines[i].slice(2, 7).trim() : lines[i++].replace(/^0 /, '').trim();
      const a = lines[i++], b = lines[i++];
      if (!a || !b || !valid(a, '1 ') || !valid(b, '2 ') || a.slice(2, 7) !== b.slice(2, 7)) throw Error('Checksum o identificación TLE inválidos');
      out.push({ name, number: +a.slice(2, 7), line1: a, line2: b, epoch: epochOf(a) });
    }
    if (!out.length) throw Error('Sin TLE');
    return out.filter((t, k) => out.findIndex(u => u.number === t.number) === k);
  }
  function epochOf(a) {
    const yy = +a.slice(18, 20), day = parseFloat(a.slice(20, 32));
    return Date.UTC(yy < 57 ? 2000 + yy : 1900 + yy, 0, 1) + (day - 1) * 86400000;
  }
  async function fetchTle() {
    const [iss, vis] = await Promise.all([ISS, VISUAL].map(u => fetch(u).then(r => { if (!r.ok) throw Error('CelesTrak HTTP ' + r.status); return r.text(); })));
    const all = parse(iss + '\n' + vis);
    return { fetched: Date.now(), tle: all };
  }
  // Iluminación del satélite: misma umbra cónica que SatelliteGeometry.point (km, ECI).
  function sunlit(eci, t) {
    const d = Astro.dayNumber(t), s = Astro.sunState(d), lon = s.lon * Astro.RAD, e = (23.4393 - 3.563e-7 * d) * Astro.RAD;
    const sx = Math.cos(lon), sy = Math.sin(lon) * Math.cos(e), sz = Math.sin(lon) * Math.sin(e);
    const along = eci.x * sx + eci.y * sy + eci.z * sz;
    const radial = Math.sqrt(Math.max(0, eci.x * eci.x + eci.y * eci.y + eci.z * eci.z - along * along));
    return !(along < 0 && radial < 6378.137 + along * (695700 - 6378.137) / 149597870.7);
  }
  function point(rec, t, gd) {
    const date = new Date(t), pv = satellite.propagate(rec, date);
    if (!pv || !pv.position || !isFinite(pv.position.x)) return null;
    const gmst = satellite.gstime(date), look = satellite.ecfToLookAngles(gd, satellite.eciToEcf(pv.position, gmst));
    return { time: t, alt: look.elevation / Astro.RAD, az: Astro.rev(look.azimuth / Astro.RAD), lit: sunlit(pv.position, t) };
  }
  // Pasos en las próximas `hours` horas con pico visible ≥ minimum grados.
  function passes(tles, place, from, hours = 24, minimum = 10, onlyIss = false) {
    const gd = { latitude: place.lat * Astro.RAD, longitude: place.lon * Astro.RAD, height: 0 }, out = [];
    const darkAt = t => Astro.sunAlt(t, place) < -6;
    for (const tle of tles) {
      if (onlyIss && tle.number !== 25544) continue;
      let rec; try { rec = satellite.twoline2satrec(tle.line1, tle.line2); } catch { continue; }
      let cur = null;
      for (let t = from; t <= from + hours * 3600000; t += 30000) {
        const p = point(rec, t, gd); if (!p) break;
        if (p.alt > 0) { p.visible = p.lit && darkAt(t); (cur = cur || { tle, track: [] }).track.push(p); }
        else if (cur) { finish(cur, out, minimum); cur = null; }
      }
      if (cur) finish(cur, out, minimum);
    }
    return out.sort((a, b) => a.start.time - b.start.time);
  }
  function finish(p, out, minimum) {
    const vis = p.track.filter(x => x.visible);
    if (!vis.length) return;
    p.peak = vis.reduce((a, b) => (b.alt > a.alt ? b : a));
    if (p.peak.alt < minimum) return;
    p.start = vis[0]; p.end = vis[vis.length - 1];
    p.old = Math.abs(p.start.time - p.tle.epoch) > 72 * 3600000;  // TLE viejos: la hora puede desviarse
    out.push(p);
  }
  return { parse, fetchTle, passes };
})();
