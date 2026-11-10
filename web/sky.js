// AstroTab web · Cielo: vista local hacia el horizonte con proyección estereográfica (como un planetario),
// arrastre para rumbo/altura, pellizco para zoom, toque para ficha y modo «apuntar con el móvil».
'use strict';
let skyTime = Date.now(), skyOffset = 0, skyTarget = null;
const view = Object.assign({ bearing: 180, elevation: 35, fov: 90 }, store.get('view', {}));
const skyOpt = Object.assign({ frame: false, messier: true, lines: true, names: true, sensor: false, dome: false }, store.get('skyOpt', {}));
const R = Math.PI / 180;
let hits = [], sensorOn = false;

// Vector unitario de un punto alt/az (x=este, y=norte, z=arriba).
const vec = (alt, az) => [Math.cos(alt * R) * Math.sin(az * R), Math.cos(alt * R) * Math.cos(az * R), Math.sin(alt * R)];
function camera(W, H) {
  const f = vec(view.elevation, view.bearing);
  let r = [Math.cos(view.bearing * R), -Math.sin(view.bearing * R), 0];   // derecha horizontal
  const u = [r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]];  // arriba = r × f
  const S = (Math.min(W, H * 1.25) / 2) / (2 * Math.tan(view.fov * R / 4));
  return { f, r, u, S, cx: W / 2, cy: H / 2, W, H };
}
function project(cam, alt, az) {
  const v = vec(alt, az), d = 1 + v[0] * cam.f[0] + v[1] * cam.f[1] + v[2] * cam.f[2];
  if (d < 0.08) return null;  // detrás del observador
  const x = (v[0] * cam.r[0] + v[1] * cam.r[1] + v[2] * cam.r[2]) / d, y = (v[0] * cam.u[0] + v[1] * cam.u[1] + v[2] * cam.u[2]) / d;
  return [cam.cx + 2 * cam.S * x, cam.cy - 2 * cam.S * y];
}
// Inversa de la proyección: altura (°) del punto de pantalla (x, y).
function altAt(cam, sx, sy) {
  const X = (sx - cam.cx) / (2 * cam.S), Y = (cam.cy - sy) / (2 * cam.S), q = X * X + Y * Y;
  const a = 2 * X / (1 + q), b = 2 * Y / (1 + q), c = (1 - q) / (1 + q);
  return Math.asin(Math.max(-1, Math.min(1, cam.r[2] * a + cam.u[2] * b + cam.f[2] * c))) / R;
}
const inView = (p, cam, m = 40) => p && p[0] > -m && p[0] < cam.W + m && p[1] > -m && p[1] < cam.H + m;

// Colocación de etiquetas sin solapes, por orden de prioridad.
function labeler() {
  const boxes = [];
  return (g, text, x, y, color, font) => {
    g.font = font; const w = g.measureText(text).width, h = 13, b = [x, y - h, x + w, y + 2];
    for (const o of boxes) if (b[0] < o[2] + 3 && b[2] + 3 > o[0] && b[1] < o[3] + 2 && b[3] + 2 > o[1]) return false;
    boxes.push(b); g.fillStyle = color; g.fillText(text, x, y); return true;
  };
}
function starColor(bv) { return bv < -2 ? '#9fbfff' : bv < 2 ? '#cfe0ff' : bv < 5 ? '#ffffff' : bv < 8 ? '#fff4d6' : bv < 12 ? '#ffd9a8' : '#ffb98a'; }

function renderSky() {
  const c = $('sky'); if (!place || !stars || !c.clientWidth) return;
  skyTime = Date.now() + skyOffset * 60000;
  const dpr = window.devicePixelRatio || 1, W = c.clientWidth, H = c.clientHeight;
  if (c.width !== Math.round(W * dpr) || c.height !== Math.round(H * dpr)) { c.width = Math.round(W * dpr); c.height = Math.round(H * dpr); }
  const g = c.getContext('2d'); g.setTransform(dpr, 0, 0, dpr, 0, 0);
  const cam = camera(W, H), lst = Astro.lst(skyTime, place.lon), lat = place.lat, d = Astro.dayNumber(skyTime);
  const hor = (ra, dec) => Astro.toHorizon(ra, dec, lst, lat), P = (ra, dec) => { const h = hor(ra, dec); return h.alt < -2 ? null : project(cam, h.alt, h.az); };
  const sunEq = Astro.sun(d), sun = hor(sunEq.ra, sunEq.dec), lim = Math.min(Astro.limitingMag(bortle), 4.2 + (120 - view.fov) / 25);
  const put = labeler(); hits = [];
  // Cielo: color según la altura del Sol y resplandor hacia donde está.
  const k = Math.max(0, Math.min(1, (sun.alt + 18) / 18));
  const top = `rgb(${5 + 30 * k},${9 + 50 * k},${18 + 90 * k})`, bot = `rgb(${12 + 70 * k},${18 + 60 * k},${34 + 80 * k})`;
  const grd = g.createLinearGradient(0, 0, 0, H); grd.addColorStop(0, top); grd.addColorStop(1, bot); g.fillStyle = grd; g.fillRect(0, 0, W, H);
  if (sun.alt > -18) { const ps = project(cam, Math.max(sun.alt, -2), sun.az);
    if (ps) { const rg = g.createRadialGradient(ps[0], ps[1], 0, ps[0], ps[1], W * 0.7); rg.addColorStop(0, `rgba(255,150,70,${0.35 * k})`); rg.addColorStop(1, 'rgba(255,150,70,0)'); g.fillStyle = rg; g.fillRect(0, 0, W, H); } }
  // Vía Láctea en capas (más capas = más brillo).
  if (k < 0.6) for (const ring of mw) {
    g.beginPath(); let started = false, prev = null;
    for (const [ra, dec] of ring.pts) { const p = P(ra, dec); if (!p || (prev && Math.hypot(p[0] - prev[0], p[1] - prev[1]) > W * 0.4)) { started = false; prev = p; continue; }
      started ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1]); started = true; prev = p; }
    g.fillStyle = `rgba(170,185,230,${(0.035 + ring.layer * 0.012) * (1 - k / 0.6)})`; g.fill();
  }
  // Rejilla de altura y rumbo.
  g.lineWidth = 1; g.font = '11px system-ui';
  for (let a = 15; a < 90; a += 15) { g.strokeStyle = 'rgba(127,184,255,.10)'; g.beginPath(); let s = false;
    for (let az = 0; az <= 360; az += 3) { const p = project(cam, a, az); if (!p) { s = false; continue; } s ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1]); s = true; } g.stroke();
    const lp = project(cam, a, view.bearing - view.fov * 0.42); if (inView(lp, cam, -10)) { g.fillStyle = 'rgba(200,215,240,.55)'; g.fillText(a + '°', lp[0] + 2, lp[1] - 3); } }
  for (let az = 0; az < 360; az += 15) { g.strokeStyle = az % 45 ? 'rgba(127,184,255,.06)' : 'rgba(127,184,255,.12)'; g.beginPath(); let s = false;
    for (let a = 0; a <= 88; a += 4) { const p = project(cam, a, az); if (!p) { s = false; continue; } s ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1]); s = true; } g.stroke(); }
  // Constelaciones: líneas y nombres.
  if (skyOpt.lines) { g.strokeStyle = 'rgba(127,184,255,.38)'; g.lineWidth = 1;
    for (const [, first, n] of stars.polys) { g.beginPath(); let s = false, prev = null;
      for (let i = first; i < first + n; i++) { const p = P(stars.pra[i], stars.pdec[i]);
        if (!p || (prev && Math.hypot(p[0] - prev[0], p[1] - prev[1]) > W * 0.5)) { s = false; prev = p; continue; } s ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1]); s = true; prev = p; }
      g.stroke(); } }
  // Objetos Messier con su símbolo.
  if (skyOpt.messier) for (const o of messier) { const p = P(o.ra, o.dec); if (!inView(p, cam)) continue;
    g.strokeStyle = 'rgba(111,211,160,.85)'; g.lineWidth = 1.2; const t = GROUP(o.type);
    if (t === 'gx') { g.beginPath(); g.ellipse(p[0], p[1], 6, 3, -0.5, 0, 7); g.stroke(); }
    else if (t === 'cl') { g.setLineDash([2, 2]); g.beginPath(); g.arc(p[0], p[1], 5, 0, 7); g.stroke(); g.setLineDash([]); }
    else { g.strokeRect(p[0] - 4, p[1] - 4, 8, 8); }
    if (view.fov < 70 && skyOpt.names) put(g, o.id, p[0] + 7, p[1] + 4, 'rgba(111,211,160,.85)', '11px system-ui');
    hits.push({ x: p[0], y: p[1], sel: { label: o.id + (o.name ? ' · ' + o.name : ''), kind: TYPE[o.type] || 'Messier', ra: o.ra, dec: o.dec, mag: o.mag, messier: o } }); }
  // Estrellas: tamaño y halo por magnitud, color por índice B−V.
  const order = [];
  for (let i = 0; i < stars.n; i++) { const m = stars.mag[i]; if (m > lim) continue; const p = P(stars.ra[i], stars.dec[i]); if (!inView(p, cam, 4)) continue;
    const r = Math.max(0.55, (lim + 1.2 - m) * 0.62 * Math.sqrt(90 / view.fov)), col = starColor(stars.bv[i]);
    if (m < 1.5) { const gl = g.createRadialGradient(p[0], p[1], 0, p[0], p[1], r * 4); gl.addColorStop(0, col); gl.addColorStop(1, 'rgba(255,255,255,0)');
      g.globalAlpha = 0.35; g.fillStyle = gl; g.beginPath(); g.arc(p[0], p[1], r * 4, 0, 7); g.fill(); g.globalAlpha = 1; }
    g.fillStyle = col; g.beginPath(); g.arc(p[0], p[1], r, 0, 7); g.fill();
    if (stars.name[i] !== 255) { order.push([m, i, p]);
      hits.push({ x: p[0], y: p[1], sel: { label: stars.names[stars.name[i]], kind: 'Estrella · ' + (stars.cons[stars.con[i]] || ''), ra: stars.ra[i], dec: stars.dec[i], mag: m } }); } }
  // Planetas, Sol y Luna.
  const bodyCols = ['#c9b38f', '#fff2c4', '#ff8a65', '#ffd59a', '#e6cf8f'];
  Astro.PLANETS.forEach((n, i) => { const pl = Astro.planet(i, d), p = P(pl.eq.ra, pl.eq.dec); if (!inView(p, cam)) return;
    g.fillStyle = bodyCols[i]; g.beginPath(); g.arc(p[0], p[1], 3.6 + Math.max(0, -pl.mag) * 0.6, 0, 7); g.fill();
    put(g, n, p[0] + 8, p[1] - 6, '#ffcf70', '600 13px system-ui');
    hits.push({ x: p[0], y: p[1], sel: { label: n, kind: 'Planeta', ra: pl.eq.ra, dec: pl.eq.dec, mag: pl.mag } }); });
  const ps = project(cam, sun.alt, sun.az); if (sun.alt > -1 && inView(ps, cam)) { g.fillStyle = '#ffd36b'; g.beginPath(); g.arc(ps[0], ps[1], 10, 0, 7); g.fill(); put(g, 'Sol', ps[0] + 12, ps[1] - 8, '#ffd36b', '600 13px system-ui'); }
  const mo = Astro.moon(d).eq, mh = Astro.moonHorizon(d, lst, lat), pm = mh.alt > -2 ? project(cam, mh.alt, mh.az) : null;
  if (inView(pm, cam)) { const rr = Math.max(7, 0.26 / view.fov * cam.W * 2); moonGlyph(g, pm[0], pm[1], rr, Astro.moonPhase(d)); put(g, 'Luna', pm[0] + rr + 4, pm[1] - rr, '#e8e2c8', '600 13px system-ui');
    hits.push({ x: pm[0], y: pm[1], sel: { label: 'Luna', kind: Astro.phaseName(Astro.moonPhase(d).age) + ` · ${Math.round(Astro.moonPhase(d).illumination * 100)} %`, ra: mo.ra, dec: mo.dec, moon: true } }); }
  // Nombres de estrellas (las más brillantes primero) y de constelaciones.
  if (skyOpt.names) { order.sort((a, b) => a[0] - b[0]);
    for (const [m, i, p] of order) { if (m > (view.fov < 60 ? 3.2 : 2.2)) break; put(g, stars.names[stars.name[i]], p[0] + 6, p[1] - 4, 'rgba(226,234,246,.92)', '12px system-ui'); }
    if (skyOpt.lines) stars.labels.forEach((L, i) => { if (L.rank > (view.fov < 60 ? 3 : 2)) return; const p = P(L.ra, L.dec); if (!inView(p, cam, -20)) return;
      put(g, stars.cons[i], p[0], p[1], 'rgba(127,184,255,.85)', '12px system-ui'); }); }
  // Satélites (features.js).
  if (typeof skySatTracks === 'function') for (const tr of skySatTracks(skyTime)) {
    g.strokeStyle = '#ffb020'; g.setLineDash([5, 4]); g.lineWidth = 1.5; g.beginPath(); let s = false;
    for (const pt of tr.pts) { const p = project(cam, pt.alt, pt.az); if (!p || pt.alt < 0) { s = false; continue; } s ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1]); s = true; }
    g.stroke(); g.setLineDash([]); if (tr.now) { const p = project(cam, tr.now.alt, tr.now.az); if (inView(p, cam)) { g.fillStyle = '#ffb020'; g.beginPath(); g.arc(p[0], p[1], 4, 0, 7); g.fill(); put(g, tr.name, p[0] + 7, p[1] + 14, '#ffb020', '12px system-ui'); } } }
  // Suelo: en cada columna, la altura baja hacia abajo; se busca dónde cruza 0° y se rellena por debajo.
  g.fillStyle = 'rgba(8,12,10,.94)'; const horizon = [];
  for (let x = 0; x <= W; x += 3) {
    if (altAt(cam, x, H) > 0) { horizon.push(null); continue; }
    let lo = 0, hi = H; if (altAt(cam, x, 0) <= 0) { g.fillRect(x, 0, 3, H); horizon.push(null); continue; }
    for (let k = 0; k < 18; k++) { const mid = (lo + hi) / 2; altAt(cam, x, mid) > 0 ? lo = mid : hi = mid; }
    g.fillRect(x, hi, 3, H - hi); horizon.push([x, hi]);
  }
  g.strokeStyle = 'rgba(147,164,189,.65)'; g.lineWidth = 1.2; g.beginPath(); let on = false;
  for (const p of horizon) { if (!p) { on = false; continue; } on ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1]); on = true; } g.stroke();
  g.font = '600 13px system-ui'; g.textAlign = 'center';
  ['N', 'NE', 'E', 'SE', 'S', 'SO', 'O', 'NO'].forEach((t, i) => { const p = project(cam, -1.5, i * 45); if (inView(p, cam, -6)) { g.fillStyle = t === 'N' ? '#ff9b9b' : '#c9d6ea'; g.fillText(t, p[0], Math.min(H - 6, p[1] + 16)); } });
  g.textAlign = 'start';
  // Objeto seleccionado y encuadre de cámara.
  if (skyTarget) { const p = P(skyTarget.ra, skyTarget.dec); if (inView(p, cam)) { g.strokeStyle = '#ff7a7a'; g.lineWidth = 2; g.beginPath(); g.arc(p[0], p[1], 15, 0, 7); g.stroke(); g.lineWidth = 1; } }
  if (skyOpt.frame && typeof equipment === 'function') {
    const e = equipment(), fw = Pro.Optics.field(e.width, e.focal), fh = Pro.Optics.field(e.height, e.focal);
    const w = 4 * cam.S * Math.tan(fw * R / 4), h = 4 * cam.S * Math.tan(fh * R / 4);
    g.strokeStyle = '#7fb8ff'; g.lineWidth = 1.6; g.strokeRect(cam.cx - w / 2, cam.cy - h / 2, w, h);
    g.beginPath(); g.moveTo(cam.cx - 12, cam.cy); g.lineTo(cam.cx + 12, cam.cy); g.moveTo(cam.cx, cam.cy - 12); g.lineTo(cam.cx, cam.cy + 12); g.stroke();
    g.fillStyle = '#7fb8ff'; g.font = '12px system-ui'; g.fillText(`${e.focal} mm · ${fw.toFixed(1)}° × ${fh.toFixed(1)}°`, cam.cx - w / 2, cam.cy - h / 2 - 6);
  }
  $('skyHud').textContent = `Rumbo ${Math.round(Astro.rev(view.bearing))}° ${compass(Astro.rev(view.bearing))}${sensorOn ? ' (móvil)' : ' manual'} · altura ${Math.round(view.elevation)}° · campo ${Math.round(view.fov)}°`;
  $('skyClock').textContent = new Date(skyTime).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' }) + (skyOffset ? ' *' : '');
}

// ---------- Selección y ficha rápida ----------
function selectSky(sel) {
  skyTarget = sel; const h = horizonAt(sel.ra, sel.dec, skyTime), alt = t => horizonAt(sel.ra, sel.dec, t).alt;
  const rise = Astro.nextCross(skyTime, 24, 0, true, alt), set = Astro.nextCross(skyTime, 24, 0, false, alt);
  $('skySel').hidden = false;
  $('skySel').innerHTML = `<div class="row2"><b class="grow">${esc(sel.label)}</b>${sel.messier ? `<button class="pill" id="selFicha">Ficha</button>` : ''}<button class="pill" id="selClose">✕</button></div>` +
    `<div class="tag">${esc(sel.kind || '')}${sel.mag != null ? ' · mag ' + sel.mag.toFixed(1) : ''}</div>` +
    `<div>${h.alt > 0 ? `${Math.round(h.alt)}° de altura hacia el ${compass(h.az)} (${Math.round(h.az)}°)` : 'Bajo el horizonte'} · ↑ ${fmt(rise)} · ↓ ${fmt(set)}</div>`;
  $('selClose').onclick = () => { $('skySel').hidden = true; skyTarget = null; renderSky(); };
  if (sel.messier) $('selFicha').onclick = () => { if (typeof openObject === 'function') openObject(sel.messier.id); };
  renderSky();
}
function centerOn(sel) {
  const h = horizonAt(sel.ra, sel.dec, skyTime);
  view.bearing = h.az; view.elevation = Math.max(5, Math.min(85, h.alt)); store.set('view', view); selectSky(sel);
}
function findSky(q) {
  q = q.trim().toLowerCase(); if (!q || !stars) return null; const d = Astro.dayNumber(skyTime);
  if (q === 'luna') { const m = Astro.moon(d).eq; return { label: 'Luna', kind: 'Luna', ra: m.ra, dec: m.dec, moon: true }; }
  const p = Astro.PLANETS.findIndex(n => n.toLowerCase() === q); if (p >= 0) { const pl = Astro.planet(p, d); return { label: Astro.PLANETS[p], kind: 'Planeta', ra: pl.eq.ra, dec: pl.eq.dec, mag: pl.mag }; }
  const m = messier.find(o => o.id.toLowerCase() === q || q === o.id.slice(1) || (o.name && o.name.toLowerCase() === q) || q.startsWith(o.id.toLowerCase() + ' '));
  if (m) return { label: m.id + (m.name ? ' · ' + m.name : ''), kind: TYPE[m.type], ra: m.ra, dec: m.dec, mag: m.mag, messier: m };
  for (let i = 0; i < stars.n; i++) if (stars.name[i] !== 255 && stars.names[stars.name[i]].toLowerCase() === q)
    return { label: stars.names[stars.name[i]], kind: 'Estrella · ' + (stars.cons[stars.con[i]] || ''), ra: stars.ra[i], dec: stars.dec[i], mag: stars.mag[i] };
  return null;
}

// ---------- Gestos: arrastrar, pellizcar, rueda y toque ----------
(function wireSky() {
  const c = $('sky'), pts = new Map(); let start = null;
  const save = () => { store.set('view', view); store.set('skyOpt', skyOpt); };
  c.addEventListener('pointerdown', e => { c.setPointerCapture(e.pointerId); pts.set(e.pointerId, [e.clientX, e.clientY]);
    start = { x: e.clientX, y: e.clientY, b: view.bearing, el: view.elevation, fov: view.fov, moved: false, dist: pts.size === 2 ? dist() : 0 }; });
  const dist = () => { const a = [...pts.values()]; return Math.hypot(a[0][0] - a[1][0], a[0][1] - a[1][1]); };
  c.addEventListener('pointermove', e => { if (!pts.has(e.pointerId) || !start) return; pts.set(e.pointerId, [e.clientX, e.clientY]);
    if (pts.size === 2) { if (!start.dist) start.dist = dist(); view.fov = Math.max(10, Math.min(190, start.fov * start.dist / Math.max(20, dist()))); start.moved = true; }
    else { const dx = e.clientX - start.x, dy = e.clientY - start.y; if (Math.hypot(dx, dy) > 5) start.moved = true;
      const k = view.fov / c.clientWidth; if (!sensorOn) { view.bearing = Astro.rev(start.b - dx * k); view.elevation = Math.max(-10, Math.min(90, start.el + dy * k)); } }
    requestAnimationFrame(renderSky); });
  const end = e => { pts.delete(e.pointerId);
    if (start && !start.moved && pts.size === 0) { const r = c.getBoundingClientRect(), x = e.clientX - r.left, y = e.clientY - r.top;
      let best = null, bd = 26 * 26; for (const h of hits) { const dd = (h.x - x) ** 2 + (h.y - y) ** 2; if (dd < bd) { bd = dd; best = h; } }
      if (best) selectSky(best.sel); }
    if (!pts.size) { start = null; save(); } };
  c.addEventListener('pointerup', end); c.addEventListener('pointercancel', end);
  c.addEventListener('wheel', e => { e.preventDefault(); view.fov = Math.max(10, Math.min(190, view.fov * (e.deltaY > 0 ? 1.1 : 0.9))); save(); renderSky(); }, { passive: false });
  const label = () => {
    $('tgFrame').innerHTML = `<svg class="ic"><use href="#i-tools"/></svg>Campo de cámara: <b>${skyOpt.frame ? 'sí' : 'no'}</b>`;
    $('tgMessier').innerHTML = `Messier: <b>${skyOpt.messier ? 'sí' : 'no'}</b>`; $('tgLines').innerHTML = `Constelaciones: <b>${skyOpt.lines ? 'sí' : 'no'}</b>`;
    $('tgNames').innerHTML = `Nombres: <b>${skyOpt.names ? 'sí' : 'no'}</b>`; $('tgSensor').innerHTML = `Apuntar con el móvil: <b>${sensorOn ? 'sí' : 'no'}</b>`;
    $('tgDome').innerHTML = `Vista: <b>${skyOpt.dome ? 'cielo entero' : 'horizonte'}</b>`; };
  const tog = (id, key) => $(id).onclick = () => { skyOpt[key] = !skyOpt[key]; save(); label(); renderSky(); };
  tog('tgFrame', 'frame'); tog('tgMessier', 'messier'); tog('tgLines', 'lines'); tog('tgNames', 'names');
  $('tgDome').onclick = () => { skyOpt.dome = !skyOpt.dome; if (skyOpt.dome) { view.elevation = 90; view.fov = 185; } else { view.elevation = 30; view.fov = 90; } save(); label(); renderSky(); };
  $('altDown').onclick = () => { view.elevation = Math.max(-10, view.elevation - 10); save(); renderSky(); };
  $('altUp').onclick = () => { view.elevation = Math.min(90, view.elevation + 10); save(); renderSky(); };
  $('skySlider').oninput = () => { skyOffset = +$('skySlider').value; renderSky(); };
  $('skyNow').onclick = () => { skyOffset = 0; $('skySlider').value = 0; renderSky(); };
  $('skySearch').addEventListener('change', () => { const t = findSky($('skySearch').value);
    if (t) centerOn(t); else { $('skySel').hidden = false; $('skySel').textContent = 'No lo encuentro. Prueba con «Vega», «M42» o «Júpiter».'; } });
  // Apuntar con el móvil: brújula para el rumbo y el ángulo beta para la altura (móvil en vertical, cámara al cielo).
  const orient = e => { const hdg = e.webkitCompassHeading != null ? e.webkitCompassHeading : (e.absolute && e.alpha != null ? 360 - e.alpha : null);
    // Móvil vertical con la pantalla hacia ti: beta=90° → cámara al horizonte; al inclinarlo hacia atrás la cámara sube.
    if (hdg != null) view.bearing = hdg; if (e.beta != null) view.elevation = Math.max(-10, Math.min(90, e.beta - 90));
    requestAnimationFrame(renderSky); };
  $('tgSensor').onclick = async () => {
    if (sensorOn) { removeEventListener('deviceorientation', orient); removeEventListener('deviceorientationabsolute', orient); sensorOn = false; label(); renderSky(); return; }
    try { if (typeof DeviceOrientationEvent !== 'undefined' && DeviceOrientationEvent.requestPermission && await DeviceOrientationEvent.requestPermission() !== 'granted') throw Error('permiso denegado');
      addEventListener('deviceorientation', orient); addEventListener('deviceorientationabsolute', orient); sensorOn = true; label();
      $('skySel').hidden = false; $('skySel').textContent = 'Sujeta el móvil en vertical y apunta la cámara al cielo. Calibra la brújula moviéndolo en ocho si se desvía.'; }
    catch (err) { $('skySel').hidden = false; $('skySel').textContent = 'No se pudo usar el sensor: ' + err.message; } };
  label();
  setInterval(() => { if (document.querySelector('#tab-cielo.active') && !skyOffset) renderSky(); }, 30000);
})();
