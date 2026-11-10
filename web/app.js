// AstroTab web (PWA para iPhone/iPad). Sin dependencias ni cuentas; la ubicación solo vive en este dispositivo.
'use strict';
const $ = id => document.getElementById(id);
const HOUR = 3600000;
const NAMES = {M1:'Nebulosa del Cangrejo',M6:'Mariposa',M7:'Cúmulo de Ptolomeo',M8:'Laguna',M11:'Pato Salvaje',M13:'Gran Cúmulo de Hércules',
  M16:'Águila',M17:'Omega',M20:'Trífida',M24:'Nube estelar de Sagitario',M27:'Mancuerna',M31:'Andrómeda',M33:'Triángulo',M42:'Nebulosa de Orión',
  M44:'Pesebre',M45:'Pléyades',M51:'Remolino',M57:'Anillo',M63:'Girasol',M64:'Ojo Negro',M73:'Asterismo de cuatro estrellas',M76:'Pequeña Mancuerna',
  M77:'Cetus A',M81:'Galaxia de Bode',M82:'Cigarro',M83:'Molinillo Austral',M87:'Virgo A',M94:'Ojo de Gato',M97:'Búho',M101:'Molinillo',M102:'Huso',M104:'Sombrero'};
const TYPE = {gc:'Cúmulo globular',oc:'Cúmulo abierto',pn:'Nebulosa planetaria',snr:'Resto de supernova',sfr:'Región de formación estelar',
  rn:'Nebulosa de reflexión',pos:'Agrupación aparente',s:'Galaxia',e:'Galaxia',i:'Galaxia'};
const GROUP = t => ({s:'gx',e:'gx',i:'gx',snr:'neb',sfr:'neb',pn:'neb',rn:'neb',gc:'gc',oc:'oc'}[t] || '');

// ---------- Ajustes (solo localStorage; nada se envía salvo la consulta de nubes) ----------
const store = {
  get(k, def) { try { const v = localStorage.getItem('astrotab.' + k); return v === null ? def : JSON.parse(v); } catch { return def; } },
  set(k, v) { try { localStorage.setItem('astrotab.' + k, JSON.stringify(v)); } catch { /* modo privado: sin persistencia */ } }
};
let place = store.get('place', null);       // {lat, lon, name}
let bortle = store.get('bortle', 5), useNet = store.get('net', true);
let weather = store.get('weather', null);   // {key, fetched, hours:[...]}
let stars = null, mw = null, messier = [];

// ---------- Catálogos binarios (DataInputStream de Java: big-endian) ----------
function readStars(buf) {
  const v = new DataView(buf); let o = 0;
  const u16 = () => { const x = v.getUint16(o); o += 2; return x; }, i16 = () => { const x = v.getInt16(o); o += 2; return x; };
  const u8 = () => v.getUint8(o++), i8 = () => v.getInt8(o++);
  if (v.getUint32(0) !== 0x41544331) throw Error('Catálogo no reconocido'); o = 4;
  const n = u16(), np = u16(), npoly = u16(), nlab = u16(), nname = u16(), ncon = u16();
  const s = { n, ra: new Float32Array(n), dec: new Float32Array(n), mag: new Float32Array(n), bv: new Int8Array(n), name: new Uint8Array(n) };
  for (let i = 0; i < n; i++) { s.ra[i] = u16() * 360 / 65536; s.dec[i] = i16() / 100; s.mag[i] = i8() / 10; s.bv[i] = i8(); u8(); s.name[i] = u8(); }
  s.pra = new Float32Array(np); s.pdec = new Float32Array(np);
  for (let i = 0; i < np; i++) { s.pra[i] = u16() * 360 / 65536; s.pdec[i] = i16() / 100; }
  s.polys = []; for (let i = 0; i < npoly; i++) s.polys.push([u8(), u16(), u8()]);
  for (let i = 0; i < nlab; i++) { u16(); i16(); u8(); }
  const dec = new TextDecoder(), text = () => { const len = u16(), t = dec.decode(new Uint8Array(buf, o, len)); o += len; return t; };
  s.names = []; for (let i = 0; i < nname; i++) s.names.push(text());
  for (let i = 0; i < ncon; i++) text();
  return s;
}
function readMilkyWay(buf) {
  const v = new DataView(buf); let o = 4;
  if (v.getUint32(0) !== 0x4D573031) throw Error('Vía Láctea no reconocida');
  const rings = v.getUint16(o); o += 4; const out = [];
  for (let r = 0; r < rings; r++) {
    const flag = v.getUint8(o++), len = v.getUint16(o); o += 2; const pts = [];
    for (let i = 0; i < len; i++) { pts.push([v.getUint16(o) * 360 / 65536, v.getInt16(o + 2) / 100]); o += 4; }
    out.push({ layer: flag >> 1, pts });
  }
  return out;
}
async function loadCatalogs() {
  const [a, b, c] = await Promise.all(['data/stars.bin', 'data/milkyway.bin', 'data/messier.tsv'].map(u => fetch(u)));
  stars = readStars(await a.arrayBuffer()); mw = readMilkyWay(await b.arrayBuffer());
  messier = (await c.text()).trim().split(/\r?\n/).map(l => { const p = l.split('\t');
    return { id: p[0], ra: +p[1], dec: +p[2], type: p[3], mag: p[4] ? +p[4] : null, size: p[5], name: NAMES[p[0]] || '', ngc: p[8] }; });
}

// ---------- Utilidades de tiempo y astros ----------
const fmt = ms => ms == null ? '—' : new Date(ms).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
const fmtDay = ms => new Date(ms).toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric' });
function horizonAt(ra, dec, ms) { return Astro.toHorizon(ra, dec, Astro.lst(ms, place.lon), place.lat); }
// Noche que empieza esta tarde (o la actual si ya es de noche): de la puesta a la salida del Sol.
function nightBounds(now) {
  const alt = t => Astro.sunAlt(t, place);
  const noon = new Date(now); noon.setHours(alt(now) < -0.833 && noon.getHours() < 12 ? -12 : 12, 0, 0, 0);
  const set = Astro.nextCross(noon.getTime(), 24, -0.833, false, alt);
  const rise = set ? Astro.nextCross(set, 24, -0.833, true, alt) : null;
  const darkStart = Astro.nextCross(noon.getTime(), 24, -18, false, alt);
  const darkEnd = darkStart ? Astro.nextCross(darkStart, 24, -18, true, alt) : null;
  return { set, rise, darkStart, darkEnd, noon: noon.getTime() };
}
function hourFactors(t, cloud, w) {
  const sa = Astro.sunAlt(t, place), ma = Astro.moonAlt(t, place), ill = Astro.moonPhase(Astro.dayNumber(t)).illumination;
  const dark = sa < -18 ? 1 : sa < -12 ? 0.5 : 0;
  const moon = ma > 0 ? ill * (0.5 + 0.5 * Math.min(1, ma / 30)) : 0;
  const dew = w && w.t != null && w.dp != null && w.t - w.dp < 3 ? 0.65 : 1;
  const wind = !w || w.wind == null ? 1 : w.wind >= 30 ? 0.4 : w.wind >= 15 ? 0.7 : 1;
  const rain = 1 - ((w && w.rain) || 0) / 100;
  return { sa, ma, dark, moon, quality: cloud == null ? null : (1 - cloud / 100) * (1 - 0.8 * moon) * dark * dew * wind * rain };
}

// ---------- Nubes (Open-Meteo, sin clave; ubicación redondeada a 2 decimales) ----------
const placeKey = () => place ? place.lat.toFixed(2) + ',' + place.lon.toFixed(2) : '';
async function fetchWeather(force) {
  if (!place || !useNet || !navigator.onLine) return;
  if (!force && weather && weather.key === placeKey() && Date.now() - weather.fetched < 2 * HOUR) return;
  const [la, lo] = placeKey().split(',');
  const url = `https://api.open-meteo.com/v1/forecast?latitude=${la}&longitude=${lo}&hourly=cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high,precipitation_probability,temperature_2m,dew_point_2m,wind_speed_10m&wind_speed_unit=kmh&timezone=UTC&forecast_days=3`;
  try {
    const r = await fetch(url); if (!r.ok) throw Error('HTTP ' + r.status);
    const j = await r.json(), h = j.hourly;
    weather = { key: placeKey(), fetched: Date.now(), hours: h.time.map((t, i) => ({ time: Date.parse(t + 'Z'), cover: h.cloud_cover[i],
      low: h.cloud_cover_low[i], mid: h.cloud_cover_mid[i], high: h.cloud_cover_high[i], rain: h.precipitation_probability[i],
      t: h.temperature_2m[i], dp: h.dew_point_2m[i], wind: h.wind_speed_10m[i] })) };
    store.set('weather', weather);
  } catch (e) { weather = weather && weather.key === placeKey() ? weather : null; $('wxStatus').textContent = 'Sin conexión con Open-Meteo: ' + e.message; }
}
const wxAt = t => weather && weather.key === placeKey() ? weather.hours.find(h => t >= h.time && t < h.time + HOUR) : null;

// ---------- Pestaña NOCHE ----------
function moonDraw(phase) {
  const c = $('moonIcon'), g = c.getContext('2d'), r = 28, cx = 32, cy = 32;
  g.clearRect(0, 0, 64, 64); g.fillStyle = '#1a2233'; g.beginPath(); g.arc(cx, cy, r, 0, 7); g.fill();
  const k = Math.cos(phase.age * Math.PI / 180), waxing = phase.age < 180;
  g.fillStyle = '#e8e2c8'; g.beginPath();
  g.arc(cx, cy, r, -Math.PI / 2, Math.PI / 2, !waxing);
  g.ellipse(cx, cy, Math.abs(k) * r, r, 0, Math.PI / 2, -Math.PI / 2, (k > 0) === waxing);
  g.fill();
}
function renderNight() {
  if (!place) return;
  const now = Date.now(), nb = nightBounds(now), d = Astro.dayNumber(now), ph = Astro.moonPhase(d);
  const malt = t => Astro.moonAlt(t, place);
  const moonUp = malt(now) > 0;
  const mrise = Astro.nextCross(now - (moonUp ? 24 * HOUR : 0), 24, 0, true, malt), mset = Astro.nextCross(now, 24, 0, false, malt);
  $('sunTimes').innerHTML = `<dt>Puesta</dt><dd>${fmt(nb.set)}</dd><dt>Noche cerrada</dt><dd>${fmt(nb.darkStart)}</dd>` +
    `<dt>Fin de la noche</dt><dd>${fmt(nb.darkEnd)}</dd><dt>Salida</dt><dd>${fmt(nb.rise)}</dd>`;
  $('moonTimes').innerHTML = `<dt>Fase</dt><dd>${Astro.phaseName(ph.age)}</dd><dt>Iluminada</dt><dd>${Math.round(ph.illumination * 100)} %</dd>` +
    `<dt>${moonUp ? 'Salió' : 'Sale'}</dt><dd>${fmt(mrise)}</dd><dt>Se pone</dt><dd>${fmt(mset)}</dd>`;
  moonDraw(ph);
  // Nota de la noche: mejor ventana de 2 h con datos de nubes (fórmula de AstroTab Android).
  const start = nb.set || nb.noon, end = nb.rise || start + 14 * HOUR, rows = [];
  for (let t = Math.ceil(start / HOUR) * HOUR; t < end; t += HOUR) { const w = wxAt(t + HOUR / 2); rows.push({ t, ...hourFactors(t + HOUR / 2, w ? w.cover : null, w) }); }
  let best = null;
  for (let i = 0; i + 1 < rows.length; i++) {
    const a = rows[i], b = rows[i + 1];
    if (a.quality == null || b.quality == null || b.t + HOUR < now) continue;
    const q = (a.quality + b.quality) / 2;
    if (!best || q > best.q) best = { q, t: a.t };
  }
  const darkHours = rows.filter(r => r.dark === 1).length;
  const sc = $('nightScore');
  if (best) {
    const score = Math.round(best.q * (1 - (bortle - 1) * 0.06) * 10);
    sc.textContent = score; sc.className = 'score ' + (score >= 7 ? 'good' : score >= 4 ? 'mid' : 'bad');
    $('nightTitle').textContent = `Nota de la noche: ${score}/10`;
    $('nightSummary').textContent = darkHours ? `Mejor rato: ${fmt(best.t)}–${fmt(best.t + 2 * HOUR)}. ${darkHours} h de noche cerrada.` : 'Sin noche astronómica hoy.';
  } else {
    sc.textContent = '–'; sc.className = 'score';
    $('nightTitle').textContent = 'Esta noche';
    $('nightSummary').textContent = (useNet ? 'Sin previsión de nubes todavía. ' : 'Previsión de nubes desactivada. ') + `${darkHours} h de noche cerrada.`;
  }
  // Planetas: altura a media noche cerrada (o a la puesta del Sol + 2 h).
  const mid = nb.darkStart && nb.darkEnd ? (nb.darkStart + nb.darkEnd) / 2 : start + 2 * HOUR, dm = Astro.dayNumber(mid);
  $('planetList').innerHTML = Astro.PLANETS.map((n, p) => { const pl = Astro.planet(p, dm), h = horizonAt(pl.eq.ra, pl.eq.dec, mid);
    return `<li><span>${n} <span class="tag">mag ${pl.mag.toFixed(1)}</span></span><span class="${h.alt > 10 ? 'good' : 'tag'}">${h.alt > 0 ? Math.round(h.alt) + '° · ' + compass(h.az) : 'bajo el horizonte'}</span></li>`; }).join('') +
    `<li class="tag">Alturas a las ${fmt(mid)}</li>`;
  const visible = messier.map(m => ({ m, h: horizonAt(m.ra, m.dec, now) })).filter(x => x.h.alt > 25 && x.m.mag != null && x.m.mag < 7.5)
    .sort((a, b) => a.m.mag - b.m.mag).slice(0, 6);
  $('bestObjects').innerHTML = Astro.sunAlt(now, place) > -12 ? '<li class="tag">Aún hay luz: vuelve cuando anochezca.</li>' :
    visible.map(x => `<li><span>${x.m.id}${x.m.name ? ' · ' + x.m.name : ''}</span><span class="tag">${Math.round(x.h.alt)}° ${compass(x.h.az)}</span></li>`).join('') || '<li class="tag">Nada brillante alto ahora.</li>';
}
const compass = az => ['N', 'NE', 'E', 'SE', 'S', 'SO', 'O', 'NO'][Math.round(az / 45) % 8];

// ---------- Pestaña CIELO: proyección estereográfica, cénit en el centro, norte arriba ----------
let skyTime = Date.now(), skyRot = 0, skyHits = [];
function renderSky() {
  if (!place || !stars) return;
  const c = $('sky'), dpr = window.devicePixelRatio || 1, W = c.clientWidth;
  if (W < 60) return;  // pestaña oculta: se dibuja al abrirla
  if (c.width !== W * dpr) { c.width = W * dpr; c.height = W * dpr; }
  const g = c.getContext('2d'); g.setTransform(dpr, 0, 0, dpr, 0, 0);
  const R = W / 2 - 14, cx = W / 2, cy = W / 2, lstv = Astro.lst(skyTime, place.lon), lat = place.lat, lim = Astro.limitingMag(bortle);
  const proj = (ra, dec) => { const h = Astro.toHorizon(ra, dec, lstv, lat); if (h.alt < -1) return null;
    const r = R * Math.tan((90 - h.alt) * Math.PI / 360) / Math.tan(Math.PI / 4), a = (h.az + skyRot) * Math.PI / 180;
    return [cx - r * Math.sin(a), cy - r * Math.cos(a), h]; };
  g.clearRect(0, 0, W, W);
  g.fillStyle = '#060b18'; g.beginPath(); g.arc(cx, cy, R, 0, 7); g.fill();
  g.save(); g.beginPath(); g.arc(cx, cy, R, 0, 7); g.clip();
  g.fillStyle = 'rgba(160,180,230,0.05)';
  for (const ring of mw) { g.beginPath(); let started = false;
    for (const [ra, dec] of ring.pts) { const p = proj(ra, dec); if (!p) { started = false; continue; } started ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1]); started = true; }
    g.fill(); }
  if ($('showLines').checked) { g.strokeStyle = 'rgba(127,184,255,0.35)'; g.lineWidth = 1;
    for (const [, first, n] of stars.polys) { g.beginPath(); let started = false;
      for (let i = first; i < first + n; i++) { const p = proj(stars.pra[i], stars.pdec[i]); if (!p) { started = false; continue; } started ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1]); started = true; }
      g.stroke(); } }
  skyHits = [];
  for (let i = 0; i < stars.n; i++) { const m = stars.mag[i]; if (m > lim) continue;
    const p = proj(stars.ra[i], stars.dec[i]); if (!p) continue;
    const bv = stars.bv[i] / 10, col = bv < 0 ? '#bcd4ff' : bv < 0.6 ? '#ffffff' : bv < 1.2 ? '#fff0c8' : '#ffc89a';
    g.fillStyle = col; g.beginPath(); g.arc(p[0], p[1], Math.max(0.6, 3.4 - m * 0.55), 0, 7); g.fill();
    if (stars.name[i] !== 255) skyHits.push({ x: p[0], y: p[1], label: stars.names[stars.name[i]], extra: `mag ${m.toFixed(1)} · ${Math.round(p[2].alt)}° ${compass(p[2].az)}` }); }
  if ($('showMessier').checked) { g.strokeStyle = '#5fd38a';
    for (const o of messier) { const p = proj(o.ra, o.dec); if (!p) continue; g.strokeRect(p[0] - 3, p[1] - 3, 6, 6);
      skyHits.push({ x: p[0], y: p[1], label: o.id + (o.name ? ' · ' + o.name : ''), extra: `${TYPE[o.type] || ''} · ${Math.round(p[2].alt)}°` }); } }
  const d = Astro.dayNumber(skyTime);
  Astro.PLANETS.forEach((n, k) => { const pl = Astro.planet(k, d), p = proj(pl.eq.ra, pl.eq.dec); if (!p) return;
    g.fillStyle = '#ffb020'; g.beginPath(); g.arc(p[0], p[1], 4, 0, 7); g.fill(); g.fillText(n, p[0] + 6, p[1] - 4);
    skyHits.push({ x: p[0], y: p[1], label: n, extra: `mag ${pl.mag.toFixed(1)} · ${Math.round(p[2].alt)}°` }); });
  const mo = Astro.moon(d).eq, pm = proj(mo.ra, mo.dec);
  if (pm) { g.fillStyle = '#e8e2c8'; g.beginPath(); g.arc(pm[0], pm[1], 7, 0, 7); g.fill(); skyHits.push({ x: pm[0], y: pm[1], label: 'Luna', extra: `${Math.round(Astro.moonPhase(d).illumination * 100)} % · ${Math.round(pm[2].alt)}°` }); }
  g.restore();
  g.strokeStyle = '#253249'; g.lineWidth = 2; g.beginPath(); g.arc(cx, cy, R, 0, 7); g.stroke();
  g.fillStyle = '#94a7c1'; g.font = '13px system-ui'; g.textAlign = 'center'; g.textBaseline = 'middle';
  [['N', 0], ['E', 90], ['S', 180], ['O', 270]].forEach(([t, az]) => { const a = (az + skyRot) * Math.PI / 180; g.fillText(t, cx - (R + 8) * Math.sin(a), cy - (R + 8) * Math.cos(a)); });
  g.textAlign = 'start';
  const sa = Astro.sunAlt(skyTime, place);
  $('skyTime').textContent = `${fmtDay(skyTime)} ${fmt(skyTime)} · Sol a ${Math.round(sa)}° · estrellas hasta magnitud ${lim} (Bortle ${bortle}). Arrastra para girar.`;
}
function skyInput() {
  const c = $('sky'); let drag = null;
  c.addEventListener('pointerdown', e => { drag = { x: e.clientX, rot: skyRot, moved: false }; c.setPointerCapture(e.pointerId); });
  c.addEventListener('pointermove', e => { if (!drag) return; const dx = e.clientX - drag.x; if (Math.abs(dx) > 4) drag.moved = true;
    skyRot = drag.rot - dx * 0.5; requestAnimationFrame(renderSky); });
  c.addEventListener('pointerup', e => { if (drag && !drag.moved) { const r = c.getBoundingClientRect(), x = e.clientX - r.left, y = e.clientY - r.top;
      let best = null, bd = 22 * 22; for (const h of skyHits) { const dd = (h.x - x) ** 2 + (h.y - y) ** 2; if (dd < bd) { bd = dd; best = h; } }
      $('skyInfo').textContent = best ? `${best.label} · ${best.extra}` : 'Nada aquí: toca más cerca de una estrella'; }
    drag = null; });
  $('timeMinus').onclick = () => { skyTime -= HOUR; renderSky(); };
  $('timePlus').onclick = () => { skyTime += HOUR; renderSky(); };
  $('timeNow').onclick = () => { skyTime = Date.now(); renderSky(); };
  $('showLines').onchange = $('showMessier').onchange = renderSky;
}

// ---------- Pestaña OBJETOS ----------
function renderObjects() {
  if (!place) return;
  const now = Date.now(), nb = nightBounds(now), from = nb.darkStart || nb.set || now, to = nb.darkEnd || nb.rise || from + 8 * HOUR;
  const filter = $('objFilter').value, onlyVisible = $('objVisible').checked;
  const rows = messier.filter(o => !filter || GROUP(o.type) === filter).map(o => {
    let best = -90, bestT = from; for (let t = from; t <= to; t += 20 * 60000) { const a = horizonAt(o.ra, o.dec, t).alt; if (a > best) { best = a; bestT = t; } }
    return { o, best, bestT, now: horizonAt(o.ra, o.dec, now) };
  }).filter(r => !onlyVisible || r.best > 20).sort((a, b) => b.best - a.best);
  $('objList').innerHTML = rows.map(r => `<li><span><b>${r.o.id}</b>${r.o.name ? ' · ' + r.o.name : ''}<br><span class="tag">${TYPE[r.o.type] || ''} · mag ${r.o.mag ?? '—'} · ${r.o.ngc}</span></span>` +
    `<span style="text-align:right"><span class="${r.best > 45 ? 'good' : r.best > 20 ? 'mid' : 'bad'}">${Math.round(r.best)}°</span> a las ${fmt(r.bestT)}<br>` +
    `<span class="tag">ahora ${Math.round(r.now.alt)}° ${compass(r.now.az)}</span></span></li>`).join('') || '<li class="tag">Ningún objeto con esos filtros.</li>';
}

// ---------- Pestaña TIEMPO ----------
function renderWeather() {
  const tbl = $('wxTable');
  if (!place) { $('wxStatus').textContent = 'Elige tu ubicación en Más.'; tbl.innerHTML = ''; return; }
  if (!useNet) { $('wxStatus').textContent = 'Previsión desactivada en Más.'; tbl.innerHTML = ''; return; }
  if (!weather || weather.key !== placeKey()) { $('wxStatus').textContent = 'Descargando previsión…'; tbl.innerHTML = ''; return; }
  $('wxStatus').textContent = `Open-Meteo · actualizado a las ${fmt(weather.fetched)}`;
  const now = Date.now() - HOUR, cls = v => v < 30 ? 'good' : v < 70 ? 'mid' : 'bad';
  tbl.innerHTML = '<tr><th>Hora</th><th>Calidad</th><th>Nubes</th><th>Bajas</th><th>Medias</th><th>Altas</th><th>Lluvia</th><th>Rocío</th><th>Viento</th></tr>' +
    weather.hours.filter(h => h.time >= now).slice(0, 48).map(h => { const f = hourFactors(h.time + HOUR / 2, h.cover, h);
      const q = f.quality == null ? '—' : f.dark ? Math.round(f.quality * 10) : '·';
      return `<tr class="${f.dark === 1 ? 'dark' : ''}"><td>${fmtDay(h.time)} ${fmt(h.time)}</td><td>${q}</td><td class="${cls(h.cover)}">${h.cover} %</td>` +
        `<td>${h.low} %</td><td>${h.mid} %</td><td>${h.high} %</td><td>${h.rain ?? '—'} %</td><td class="${h.t - h.dp < 3 ? 'bad' : ''}">${h.t - h.dp < 3 ? 'alto' : 'bajo'}</td><td>${Math.round(h.wind)} km/h</td></tr>`; }).join('');
}

// ---------- Pestaña MÁS: exposición (regla 500 y NPF simplificada) ----------
function renderExposure() {
  const f = +$('focIn').value, n = +$('apIn').value, crop = +$('cropIn').value || 1, p = +$('pixIn').value;
  if (!(f > 0 && n > 0 && p > 0)) { $('expOut').textContent = 'Revisa los datos.'; return; }
  const r500 = 500 / (f * crop), npf = (35 * n + 30 * p) / f;
  $('expOut').innerHTML = `Regla 500: <b>${r500.toFixed(1)} s</b><br>NPF (más estricta): <b>${npf.toFixed(1)} s</b>`;
}

// ---------- Ubicación y arranque ----------
function showPlace() {
  $('placeLabel').textContent = place ? `${place.name || 'Mi ubicación'} · ${place.lat.toFixed(2)}°, ${place.lon.toFixed(2)}°` : 'Sin ubicación: ve a Más';
  if (place) { $('latIn').value = place.lat; $('lonIn').value = place.lon; $('nameIn').value = place.name || ''; }
  $('bortleIn').value = bortle; $('netIn').checked = useNet;
}
function savePlace(lat, lon, name) {
  if (!(Math.abs(lat) <= 90 && Math.abs(lon) <= 180)) { $('saveMsg').textContent = 'Latitud entre −90 y 90 y longitud entre −180 y 180.'; return false; }
  place = { lat, lon, name: (name || '').slice(0, 40) }; store.set('place', place); return true;
}
function renderAll() { showPlace(); renderNight(); renderSky(); renderObjects(); renderWeather(); }
function wire() {
  document.querySelectorAll('.tabs button').forEach(b => b.onclick = () => {
    document.querySelectorAll('.tabs button').forEach(x => x.classList.toggle('active', x === b));
    document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t.id === 'tab-' + b.dataset.tab));
    if (b.dataset.tab === 'cielo') requestAnimationFrame(renderSky);
    store.set('tab', b.dataset.tab);
  });
  const red = store.get('red', false), setRed = on => { document.documentElement.classList.toggle('red', on); $('redBtn').setAttribute('aria-pressed', on); store.set('red', on); };
  setRed(red); $('redBtn').onclick = () => setRed(!document.documentElement.classList.contains('red'));
  $('gpsBtn').onclick = () => {
    if (!navigator.geolocation) { $('saveMsg').textContent = 'Este navegador no da la ubicación: escríbela a mano.'; return; }
    $('saveMsg').textContent = 'Pidiendo ubicación…';
    navigator.geolocation.getCurrentPosition(p => { savePlace(+p.coords.latitude.toFixed(4), +p.coords.longitude.toFixed(4), $('nameIn').value || 'Mi ubicación');
      $('saveMsg').textContent = 'Ubicación guardada.'; refresh(true); },
    e => { $('saveMsg').textContent = 'No se pudo obtener la ubicación (' + e.message + '). Escríbela a mano.'; }, { enableHighAccuracy: false, timeout: 15000, maximumAge: 600000 });
  };
  $('saveBtn').onclick = () => {
    const lat = parseFloat($('latIn').value.replace(',', '.')), lon = parseFloat($('lonIn').value.replace(',', '.'));
    bortle = Math.max(1, Math.min(9, parseInt($('bortleIn').value, 10) || 5)); useNet = $('netIn').checked;
    store.set('bortle', bortle); store.set('net', useNet);
    if (savePlace(lat, lon, $('nameIn').value)) { $('saveMsg').textContent = 'Guardado.'; refresh(true); }
  };
  ['focIn', 'apIn', 'cropIn', 'pixIn'].forEach(id => $(id).oninput = renderExposure);
  $('objFilter').onchange = $('objVisible').onchange = renderObjects;
  window.addEventListener('resize', () => requestAnimationFrame(renderSky));
  skyInput();
  const tab = store.get('tab', 'noche'); document.querySelector(`.tabs button[data-tab="${tab}"]`)?.click();
}
async function refresh(force) { renderAll(); await fetchWeather(force); renderAll(); }
(async () => {
  wire(); renderExposure(); showPlace();
  try { await loadCatalogs(); } catch (e) { $('nightSummary').textContent = 'No se pudieron cargar los catálogos: ' + e.message; return; }
  await refresh(false);
  setInterval(() => { if (Math.abs(skyTime - Date.now()) < 2 * 60000) skyTime = Date.now(); renderAll(); }, 60000);
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
})();
