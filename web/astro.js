// AstroTab web: motor astronómico portado de core/.../astro/Astro.kt (Schlyter, MIT, MS Robotika).
// Grados; d = días desde 2000-01-00 UTC. Aproximación para orientarse, no para guiar monturas.
'use strict';
const Astro = (() => {
  const RAD = Math.PI / 180;
  const rev = x => ((x % 360) + 360) % 360;
  const sind = x => Math.sin(x * RAD), cosd = x => Math.cos(x * RAD);
  const atan2d = (y, x) => Math.atan2(y, x) / RAD;
  const asind = x => Math.asin(Math.max(-1, Math.min(1, x))) / RAD;
  const dayNumber = ms => ms / 86400000 - 10956;
  const obliquity = d => 23.4393 - 3.563e-7 * d;

  function eclToEq(lon, lat, r, d) {
    const x = r * cosd(lon) * cosd(lat), y = r * sind(lon) * cosd(lat), z = r * sind(lat), e = obliquity(d);
    const ye = y * cosd(e) - z * sind(e), ze = y * sind(e) + z * cosd(e);
    return { ra: rev(atan2d(ye, x)), dec: atan2d(ze, Math.hypot(x, ye)), dist: r };
  }
  function eccAnomaly(m, e) {
    let E = m + e / RAD * sind(m) * (1 + e * cosd(m));
    for (let i = 0; i < 8; i++) {
      const delta = (E - e / RAD * sind(E) - m) / (1 - e * cosd(E));
      E -= delta;
      if (Math.abs(delta) < 1e-6) break;
    }
    return E;
  }
  function sunState(d) {
    const w = 282.9404 + 4.70935e-5 * d, e = 0.016709 - 1.151e-9 * d, m = rev(356.0470 + 0.9856002585 * d);
    const E = eccAnomaly(m, e), x = cosd(E) - e, y = Math.sqrt(1 - e * e) * sind(E);
    return { lon: rev(atan2d(y, x) + w), r: Math.hypot(x, y), meanLon: rev(w + m), anomaly: m };
  }
  const sun = d => { const s = sunState(d); return eclToEq(s.lon, 0, s.r, d); };
  function gmst(d) {
    let ut = ((d + 10956) % 1) * 24;
    if (ut < 0) ut += 24;
    return rev(sunState(d).meanLon + 180 + ut * 15);
  }
  function toHorizon(ra, dec, lst, lat) {
    const ha = lst - ra, x = cosd(ha) * cosd(dec), y = sind(ha) * cosd(dec), z = sind(dec);
    return { alt: asind(x * cosd(lat) + z * sind(lat)), az: rev(atan2d(y, x * sind(lat) - z * cosd(lat)) + 180) };
  }
  function moon(d) {
    const node = rev(125.1228 - 0.0529538083 * d), i = 5.1454, w = rev(318.0634 + 0.1643573223 * d);
    const a = 60.2666, e = 0.0549, m = rev(115.3654 + 13.0649929509 * d);
    const E = eccAnomaly(m, e), xv = a * (cosd(E) - e), yv = a * Math.sqrt(1 - e * e) * sind(E);
    const v = atan2d(yv, xv);
    let r = Math.hypot(xv, yv);
    const xh = r * (cosd(node) * cosd(v + w) - sind(node) * sind(v + w) * cosd(i));
    const yh = r * (sind(node) * cosd(v + w) + cosd(node) * sind(v + w) * cosd(i));
    const zh = r * sind(v + w) * sind(i);
    let lon = atan2d(yh, xh), lat = atan2d(zh, Math.hypot(xh, yh));
    const s = sunState(d), ms = s.anomaly, lm = rev(node + w + m), el = lm - s.meanLon, f = lm - node;
    lon += -1.274 * sind(m - 2 * el) + 0.658 * sind(2 * el) - 0.186 * sind(ms) - 0.059 * sind(2 * m - 2 * el) -
      0.057 * sind(m - 2 * el + ms) + 0.053 * sind(m + 2 * el) + 0.046 * sind(2 * el - ms) + 0.041 * sind(m - ms) -
      0.035 * sind(el) - 0.031 * sind(m + ms) - 0.015 * sind(2 * f - 2 * el) + 0.011 * sind(m - 4 * el);
    lat += -0.173 * sind(f - 2 * el) - 0.055 * sind(m - f - 2 * el) - 0.046 * sind(m + f - 2 * el) +
      0.033 * sind(f + 2 * el) + 0.017 * sind(2 * m + f);
    r += -0.58 * cosd(m - 2 * el) - 0.46 * cosd(2 * el);
    return { eq: eclToEq(rev(lon), lat, r, d), lon: rev(lon), lat };
  }
  function moonPhase(d) {
    const m = moon(d), s = sunState(d);
    const el = Math.acos(Math.max(-1, Math.min(1, cosd(m.lon - s.lon) * cosd(m.lat))));
    return { illumination: (1 - Math.cos(el)) / 2, age: rev(m.lon - s.lon) };
  }
  function moonHorizon(d, lst, lat) {
    const m = moon(d), h = toHorizon(m.eq.ra, m.eq.dec, lst, lat);
    return { alt: h.alt - asind(1 / m.eq.dist) * cosd(h.alt), az: h.az };
  }
  const EL = [
    [48.3313, 3.24587e-5, 7.0047, 5e-8, 29.1241, 1.01444e-5, 0.387098, 0.205635, 5.59e-10, 168.6562, 4.0923344368],
    [76.6799, 2.46590e-5, 3.3946, 2.75e-8, 54.8910, 1.38374e-5, 0.723330, 0.006773, -1.302e-9, 48.0052, 1.6021302244],
    [49.5574, 2.11081e-5, 1.8497, -1.78e-8, 286.5016, 2.92961e-5, 1.523688, 0.093405, 2.516e-9, 18.6021, 0.5240207766],
    [100.4542, 2.76854e-5, 1.3030, -1.557e-7, 273.8777, 1.64505e-5, 5.20256, 0.048498, 4.469e-9, 19.8950, 0.0830853001],
    [113.6634, 2.38980e-5, 2.4886, -1.081e-7, 339.3939, 2.97661e-5, 9.55475, 0.055546, -9.499e-9, 316.9670, 0.0334442282]];
  const PLANETS = ['Mercurio', 'Venus', 'Marte', 'Júpiter', 'Saturno'];
  function planet(p, d) {
    const k = EL[p], node = k[0] + k[1] * d, i = k[2] + k[3] * d, w = k[4] + k[5] * d;
    const a = k[6], e = k[7] + k[8] * d, m = rev(k[9] + k[10] * d);
    const E = eccAnomaly(m, e), xv = a * (cosd(E) - e), yv = a * Math.sqrt(1 - e * e) * sind(E);
    const v = atan2d(yv, xv), r = Math.hypot(xv, yv);
    let xh = r * (cosd(node) * cosd(v + w) - sind(node) * sind(v + w) * cosd(i));
    let yh = r * (sind(node) * cosd(v + w) + cosd(node) * sind(v + w) * cosd(i));
    let zh = r * sind(v + w) * sind(i);
    let lon = atan2d(yh, xh), lat = atan2d(zh, Math.hypot(xh, yh));
    if (p >= 3) {
      const mj = rev(19.8950 + 0.0830853001 * d), ms = rev(316.9670 + 0.0334442282 * d);
      if (p === 3) lon += -0.332 * sind(2 * mj - 5 * ms - 67.6) - 0.056 * sind(2 * mj - 2 * ms + 21) + 0.042 * sind(3 * mj - 5 * ms + 21) -
        0.036 * sind(mj - 2 * ms) + 0.022 * cosd(mj - ms) + 0.023 * sind(2 * mj - 3 * ms + 52) - 0.016 * sind(mj - 5 * ms - 69);
      else {
        lon += 0.812 * sind(2 * mj - 5 * ms - 67.6) - 0.229 * cosd(2 * mj - 4 * ms - 2) + 0.119 * sind(mj - 2 * ms - 3) +
          0.046 * sind(2 * mj - 6 * ms - 69) + 0.014 * sind(mj - 3 * ms + 32);
        lat += -0.020 * cosd(2 * mj - 4 * ms - 2) + 0.018 * sind(2 * mj - 6 * ms - 49);
      }
    }
    xh = r * cosd(lon) * cosd(lat); yh = r * sind(lon) * cosd(lat); zh = r * sind(lat);
    const s = sunState(d), xg = xh + s.r * cosd(s.lon), yg = yh + s.r * sind(s.lon);
    const dist = Math.sqrt(xg * xg + yg * yg + zh * zh);
    const eq = eclToEq(atan2d(yg, xg), atan2d(zh, Math.hypot(xg, yg)), dist, d);
    const phase = Math.acos(Math.max(-1, Math.min(1, (r * r + dist * dist - s.r * s.r) / (2 * r * dist)))) / RAD;
    let mag = [-0.36, -4.34, -1.51, -9.25, -9.0][p] + 5 * Math.log10(r * dist) + [0.027, 0.013, 0.016, 0.014, 0.044][p] * phase;
    if (p === 0) mag += 2.2e-13 * phase ** 6;
    if (p === 1) mag += 4.2e-7 * phase ** 3;
    return { eq, mag };
  }
  function phaseName(age) {
    if (age < 10 || age > 350) return 'Luna nueva';
    if (age < 80) return 'Creciente';
    if (age < 100) return 'Cuarto creciente';
    if (age < 170) return 'Gibosa creciente';
    if (age < 190) return 'Luna llena';
    if (age < 260) return 'Gibosa menguante';
    if (age < 280) return 'Cuarto menguante';
    return 'Menguante';
  }
  // Cruce de un nivel de altitud, cada 5 min y afinado por bisección. null = sin cruce en el intervalo.
  function nextCross(from, hours, level, rising, altitude) {
    let t = from, prev = altitude(t) - level;
    for (let k = 0; k < hours * 12; k++) {
      const end = t + 300000, next = altitude(end) - level;
      if ((rising && prev < 0 && next >= 0) || (!rising && prev > 0 && next <= 0)) {
        let lo = t, hi = end;
        for (let j = 0; j < 14; j++) { const mid = (lo + hi) / 2; if ((altitude(mid) >= level) === rising) hi = mid; else lo = mid; }
        return Math.round((lo + hi) / 2);
      }
      t = end; prev = next;
    }
    return null;
  }
  // Posición horizontal de todos los astros del sistema solar en un instante.
  function lst(ms, lon) { return rev(gmst(dayNumber(ms)) + lon); }
  const sunAlt = (ms, p) => { const d = dayNumber(ms), s = sun(d); return toHorizon(s.ra, s.dec, rev(gmst(d) + p.lon), p.lat).alt; };
  const moonAlt = (ms, p) => { const d = dayNumber(ms); return moonHorizon(d, rev(gmst(d) + p.lon), p.lat).alt; };
  const geomagLat = (lat, lon) => asind(sind(lat) * sind(80.8) + cosd(lat) * cosd(80.8) * cosd(lon + 72.6));
  const kpNeeded = gl => (66.5 - Math.abs(gl)) / 2.6;
  const limitingMag = b => [7.6, 7.1, 6.6, 6.1, 5.6, 5.1, 4.6, 4.1, 3.8][Math.max(1, Math.min(9, b)) - 1];
  return { RAD, rev, sind, cosd, dayNumber, sun, sunState, gmst, toHorizon, moon, moonPhase, moonHorizon, planet, PLANETS,
    phaseName, nextCross, lst, sunAlt, moonAlt, geomagLat, kpNeeded, limitingMag };
})();
if (typeof module !== 'undefined') module.exports = Astro;
