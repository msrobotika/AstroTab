// AstroTab web (PWA para iPhone/iPad): estado, catálogos, Inicio, Noche, Objetos, Tiempo y Ajustes.
// Sin cuentas ni telemetría: la ubicación vive en el dispositivo y solo se envía redondeada a Open-Meteo.
'use strict';
const $ = id => document.getElementById(id);
const HOUR = 3600000;
const NAMES = {M1:'Nebulosa del Cangrejo',M6:'Mariposa',M7:'Cúmulo de Ptolomeo',M8:'Laguna',M11:'Pato Salvaje',M13:'Gran Cúmulo de Hércules',
  M16:'Águila',M17:'Omega',M20:'Trífida',M24:'Nube estelar de Sagitario',M27:'Mancuerna',M31:'Andrómeda',M33:'Triángulo',M42:'Nebulosa de Orión',
  M44:'Pesebre',M45:'Pléyades',M51:'Remolino',M57:'Anillo',M63:'Girasol',M64:'Ojo Negro',M73:'Asterismo de cuatro estrellas',M76:'Pequeña Mancuerna',
  M77:'Cetus A',M81:'Galaxia de Bode',M82:'Cigarro',M83:'Molinillo Austral',M87:'Virgo A',M94:'Ojo de Gato',M97:'Búho',M101:'Molinillo',M102:'Huso',M104:'Sombrero'};
const TYPE = {gc:'Cúmulo globular',oc:'Cúmulo abierto',pn:'Nebulosa planetaria',snr:'Resto de supernova',sfr:'Región de formación estelar',
  rn:'Nebulosa de reflexión',pos:'Agrupación aparente',s:'Galaxia espiral',e:'Galaxia elíptica',i:'Galaxia irregular'};
const GROUP = t => ({s:'gx',e:'gx',i:'gx',snr:'neb',sfr:'neb',pn:'neb',rn:'neb',gc:'cl',oc:'cl'}[t] || '');
const ICON = t => ({gx:'#i-obj',neb:'#i-nebula',cl:'#i-cluster'}[GROUP(t)] || '#i-cluster');
const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

// ---------- Ajustes persistentes (localStorage, con fallo silencioso en modo privado) ----------
const store = {
  get(k, def) { try { const v = localStorage.getItem('astrotab.' + k); return v === null ? def : JSON.parse(v); } catch { return def; } },
  set(k, v) { try { localStorage.setItem('astrotab.' + k, JSON.stringify(v)); } catch { /* sin persistencia */ } }
};
let place = store.get('place', null), bortle = store.get('bortle', 5), useNet = store.get('net', true);
let weather = store.get('weather', null), stars = null, mw = null, messier = [];

// ---------- Catálogos (formato DataInputStream de Java: big-endian) ----------
function readStars(buf) {
  const v = new DataView(buf); let o = 4;
  const u16 = () => { const x = v.getUint16(o); o += 2; return x; }, i16 = () => { const x = v.getInt16(o); o += 2; return x; };
  const u8 = () => v.getUint8(o++), i8 = () => v.getInt8(o++);
  if (v.getUint32(0) !== 0x41544331) throw Error('Catálogo no reconocido');
  const n = u16(), np = u16(), npoly = u16(), nlab = u16(), nname = u16(), ncon = u16();
  const s = { n, ra: new Float32Array(n), dec: new Float32Array(n), mag: new Float32Array(n), bv: new Int8Array(n), con: new Uint8Array(n), name: new Uint8Array(n) };
  for (let i = 0; i < n; i++) { s.ra[i] = u16() * 360 / 65536; s.dec[i] = i16() / 100; s.mag[i] = i8() / 10; s.bv[i] = i8(); s.con[i] = u8(); s.name[i] = u8(); }
  s.pra = new Float32Array(np); s.pdec = new Float32Array(np);
  for (let i = 0; i < np; i++) { s.pra[i] = u16() * 360 / 65536; s.pdec[i] = i16() / 100; }
  s.polys = []; for (let i = 0; i < npoly; i++) s.polys.push([u8(), u16(), u8()]);
  s.labels = []; for (let i = 0; i < nlab; i++) s.labels.push({ ra: u16() * 360 / 65536, dec: i16() / 100, rank: u8() });
  const td = new TextDecoder(), text = () => { const len = u16(), t = td.decode(new Uint8Array(buf, o, len)); o += len; return t; };
  s.names = []; for (let i = 0; i < nname; i++) s.names.push(text());
  s.cons = []; for (let i = 0; i < ncon; i++) s.cons.push(text().replace(/([a-záéíóúñ])([A-ZÁÉÍÓÚ])/g, '$1 $2'));  // «OsaMenor» → «Osa Menor»
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

// ---------- Utilidades ----------
const fmt = ms => ms == null ? '—' : new Date(ms).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
const fmtDay = ms => new Date(ms).toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric' });
const fmtDM = ms => new Date(ms).toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit' });
const compass = az => ['N', 'NE', 'E', 'SE', 'S', 'SO', 'O', 'NO'][Math.round(az / 45) % 8];
const zoneName = () => { try { return Intl.DateTimeFormat().resolvedOptions().timeZone; } catch { return ''; } };
function horizonAt(ra, dec, ms) { return Astro.toHorizon(ra, dec, Astro.lst(ms, place.lon), place.lat); }
let nightCache = null;
function tonight() {  // Línea de la noche (de mediodía a mediodía), cacheada 10 min.
  const key = placeKey();
  if (!nightCache || nightCache.key !== key || Date.now() - nightCache.at > 10 * 60000)
    nightCache = { key, at: Date.now(), night: Pro.AstroPhoto.timeline(Date.now(), place) };
  return nightCache.night;
}
function canvas2d(c, h) {  // Lienzo nítido en pantallas retina.
  const dpr = window.devicePixelRatio || 1, W = c.clientWidth || 300, H = h || c.clientHeight || +c.getAttribute('height') || 150;
  if (c.width !== Math.round(W * dpr) || c.height !== Math.round(H * dpr)) { c.width = Math.round(W * dpr); c.height = Math.round(H * dpr); }
  const g = c.getContext('2d'); g.setTransform(dpr, 0, 0, dpr, 0, 0); g.clearRect(0, 0, W, H); return { g, W, H };
}
function hourFactors(t, w) {
  const sa = Astro.sunAlt(t, place), ma = Astro.moonAlt(t, place), ill = Astro.moonPhase(Astro.dayNumber(t)).illumination;
  const dark = sa < -18 ? 1 : sa < -12 ? 0.5 : 0, moon = ma > 0 ? ill * (0.5 + 0.5 * Math.min(1, ma / 30)) : 0;
  const dew = w && w.t != null && w.dp != null && w.t - w.dp < 3 ? 0.65 : 1, wind = !w || w.wind == null ? 1 : w.wind >= 30 ? 0.4 : w.wind >= 15 ? 0.7 : 1;
  return { sa, ma, dark, moon, quality: !w ? null : (1 - w.cover / 100) * (1 - 0.8 * moon) * dark * dew * wind * (1 - (w.rain || 0) / 100) };
}

// ---------- Tiempo (Open-Meteo, sin clave; ubicación redondeada a 2 decimales) ----------
const placeKey = () => place ? place.lat.toFixed(2) + ',' + place.lon.toFixed(2) : '';
async function fetchWeather(force) {
  if (!place || !useNet || !navigator.onLine) return;
  if (!force && weather && weather.key === placeKey() && Date.now() - weather.fetched < 2 * HOUR) return;
  const [la, lo] = placeKey().split(',');
  const url = `https://api.open-meteo.com/v1/forecast?latitude=${la}&longitude=${lo}&hourly=cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high,precipitation_probability,temperature_2m,dew_point_2m,relative_humidity_2m,wind_speed_10m,wind_gusts_10m,visibility,pressure_msl,wind_speed_250hPa&wind_speed_unit=kmh&timezone=UTC&forecast_days=7`;
  try {
    const r = await fetch(url); if (!r.ok) throw Error('HTTP ' + r.status);
    const h = (await r.json()).hourly, at = (k, i) => h[k] ? h[k][i] : null;
    weather = { key: placeKey(), fetched: Date.now(), hours: h.time.map((t, i) => ({ time: Date.parse(t + 'Z'), cover: at('cloud_cover', i),
      low: at('cloud_cover_low', i), mid: at('cloud_cover_mid', i), high: at('cloud_cover_high', i), rain: at('precipitation_probability', i),
      t: at('temperature_2m', i), dp: at('dew_point_2m', i), hum: at('relative_humidity_2m', i), wind: at('wind_speed_10m', i),
      gust: at('wind_gusts_10m', i), vis: at('visibility', i), pres: at('pressure_msl', i), jet: at('wind_speed_250hPa', i) })) };
    store.set('weather', weather);
  } catch (e) { $('wxStatus').textContent = 'Sin conexión con Open-Meteo: ' + e.message; }
}
const wxRows = () => weather && weather.key === placeKey() ? weather.hours : null;
const wxAt = t => { const r = wxRows(); return r ? r.find(h => t >= h.time && t < h.time + HOUR) : null; };

// ---------- Cabecera y reloj ----------
function tickClock() {
  const now = new Date();
  $('clockLine').textContent = `${now.toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric' })} ${now.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })} · ${zoneName()}`;
  $('bigClock').textContent = now.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  const utc = now.toISOString().slice(11, 19);
  let tsl = '';
  if (place) { const l = Astro.lst(now.getTime(), place.lon) / 15, hh = Math.floor(l), mm = Math.floor((l - hh) * 60), ss = Math.floor(((l - hh) * 60 - mm) * 60);
    tsl = ` · TSL ${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}:${String(ss).padStart(2, '0')}`; }
  $('utcLine').textContent = `UTC ${utc}${tsl}`;
  $('dateLine').textContent = now.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
}
function showPlace() {
  $('placeLine').textContent = place ? `${place.name || 'mi ubicación'} · ${place.lat.toFixed(2)}°, ${place.lon.toFixed(2)}°` : 'sin ubicación · ve a Ajustes';
  if (place) { $('latIn').value = place.lat; $('lonIn').value = place.lon; $('nameIn').value = place.name || ''; }
  $('bortleIn').value = bortle; $('netIn').checked = useNet;
}

// ---------- INICIO ----------
function moonGlyph(g, cx, cy, r, phase) {  // Fase lunar con terminador elíptico.
  g.fillStyle = '#1d2638'; g.beginPath(); g.arc(cx, cy, r, 0, 7); g.fill();
  const k = Math.cos(phase.age * Math.PI / 180), waxing = phase.age < 180;
  g.fillStyle = '#e8e2c8'; g.beginPath(); g.arc(cx, cy, r, -Math.PI / 2, Math.PI / 2, !waxing);
  g.ellipse(cx, cy, Math.abs(k) * r, r, 0, Math.PI / 2, -Math.PI / 2, (k > 0) === waxing); g.fill();
}
function renderHome() {
  if (!place) return;
  const now = Date.now(), night = tonight(), d = Astro.dayNumber(now), ph = Astro.moonPhase(d);
  // Nota de la noche y mejor noche de la semana.
  let nights = []; try { nights = Pro.Forecast.week(now, place, wxRows(), useNet); } catch { nights = []; }
  const first = nights[0], best = Pro.Forecast.best(nights), score = first && first.score != null ? first.score : null;
  $('scoreNum').textContent = score == null ? '–' : score;
  $('gArc').style.strokeDasharray = `${(score || 0) / 100 * 236} 400`;
  $('gArc').style.stroke = score == null ? 'var(--panel2)' : score >= 60 ? 'var(--green)' : score >= 30 ? 'var(--amber)' : 'var(--red)';
  $('scoreTitle').textContent = score == null ? 'Sin previsión' : score >= 60 ? 'Noche buena' : score >= 30 ? 'Noche regular' : 'Noche mala';
  $('scoreTitle').className = 'big ' + (score == null ? '' : score >= 60 ? 'good' : score >= 30 ? 'mid' : 'bad');
  $('scoreSub').textContent = first && first.clouds != null ? `Nubes ${Math.round(first.clouds)} % · ${first.darkHours.toFixed(1)} h de noche cerrada` : (useNet ? 'Descargando previsión…' : 'Previsión desactivada en Ajustes');
  $('scoreBest').textContent = best ? `Mejor: ${fmtDay(best.start)} · ${best.score}/100` : '';
  // Barra de la noche.
  drawNightBar(night);
  const set = night.samples.find((s, i) => i && night.samples[i - 1].sun >= -0.833 && s.sun < -0.833);
  const rise = night.samples.find((s, i) => i && night.samples[i - 1].sun < -0.833 && s.sun >= -0.833);
  $('tlSet').textContent = 'Atardecer ' + fmt(set && set.time); $('tlRise').textContent = 'Amanecer ' + fmt(rise && rise.time);
  const sa = Astro.sunAlt(now, place);
  $('tlNow').textContent = 'Ahora: ' + (sa > -0.833 ? (now < (set ? set.time : 0) ? 'antes del atardecer' : 'de día') : sa > -6 ? 'crepúsculo civil' : sa > -12 ? 'crepúsculo náutico' : sa > -18 ? 'crepúsculo astronómico' : 'noche cerrada');
  // Tarjetas.
  const malt = t => Astro.moonAlt(t, place), up = malt(now) > 0;
  const mr = Astro.nextCross(now - (up ? 24 * HOUR : 0), 24, 0, true, malt), ms = Astro.nextCross(now, 24, 0, false, malt);
  $('tMoon').innerHTML = `${Math.round(ph.illumination * 100)}<small> %</small>`; $('tMoonSub').textContent = `${Astro.phaseName(ph.age)} · ↑ ${fmt(mr)} ↓ ${fmt(ms)}`;
  const dusk = night.samples.find((s, i) => i && night.samples[i - 1].sun >= -18 && s.sun < -18);
  $('tSun').textContent = fmt(set && set.time); $('tSunSub').textContent = `↑ ${fmt(rise && rise.time)} · astro ${fmt(dusk && dusk.time)}`;
  const w = wxAt(now);
  $('tWx').innerHTML = w ? `${Math.round(w.t)}<small> °C</small>` : '—';
  $('tWxSub').textContent = w ? `Rocío ${Math.round(w.t - w.dp)}° · ${Math.round(w.wind)} km/h · nubes ${w.cover} %` : (useNet ? 'Sin datos aún' : 'Previsión desactivada');
  // Planetas visibles y evento destacado.
  const vis = Pro.ProAstro.planets(night, place).filter(p => p.maximum > 5 && p.elongation > 12 && p.altitudes.some((a, i) => a > 5 && night.samples[i].sun < -6));
  $('planetsTonight').textContent = vis.length ? vis.map(p => p.name).join(' · ') : 'Ninguno bien situado';
  try { const ev = Pro.EventCalendar.month(now, place, typeof eclipseRows !== 'undefined' ? eclipseRows : []).find(e => e.time > now && e.highlighted);
    $('eventTonight').textContent = ev ? `★ ${fmtDM(ev.time)} ${ev.title}` : ''; } catch { $('eventTonight').textContent = ''; }
  // Mejores Messier.
  const top = Pro.TargetChoice.diverse(messier.map(o => Pro.AstroPhoto.plan(o, night, place, 30)).filter(p => p.usefulHours > 0.5), 3);
  $('bestMessier').innerHTML = top.map(p => `<button data-id="${p.target.id}"><svg class="ic"><use href="${ICON(p.target.type)}"/></svg><b>${p.target.id} · ${p.score}</b>${fmt(p.transit)}</button>`).join('') || '<span class="muted">Sin objetos con noche cerrada.</span>';
  if (typeof renderHomeExtras === 'function') renderHomeExtras();
}
function drawNightBar(night) {
  const { g, W, H } = canvas2d($('nightBar'), 26), n = night.samples.length, x = i => i / (n - 1) * W, now = Date.now();
  night.samples.forEach((s, i) => {
    const c = s.sun > -0.833 ? '#d38a2c' : s.sun > -6 ? '#7a6a9a' : s.sun > -12 ? '#3d4f86' : s.sun > -18 ? '#243457' : '#0b1222';
    g.fillStyle = c; g.fillRect(x(i), 0, W / n + 1, 16);
    if (s.moon > 0 && s.sun < -6) { g.fillStyle = `rgba(232,226,200,${0.25 + 0.6 * s.illumination})`; g.fillRect(x(i), 16, W / n + 1, 4); }
    const w = wxAt(s.time); if (w) { g.fillStyle = `rgba(255,122,122,${w.cover / 110})`; g.fillRect(x(i), 21, W / n + 1, 5); }
  });
  if (now > night.start && now < night.end) { const xn = (now - night.start) / (night.end - night.start) * W; g.fillStyle = '#7fb8ff'; g.fillRect(xn - 1, 0, 2, H); }
}

// ---------- NOCHE ----------
function renderNightTab() {
  if (!place) return;
  const night = tonight(), { g, W, H } = canvas2d($('nightChart'), 190), n = night.samples.length;
  const x = i => i / (n - 1) * W, y = a => H - 18 - (Math.max(-20, a) + 20) / 110 * (H - 28);
  night.samples.forEach((s, i) => { if (s.sun < -18) { g.fillStyle = '#121c33'; g.fillRect(x(i), 0, W / n + 1, H - 16); } });
  g.strokeStyle = '#22304a'; g.lineWidth = 1; g.fillStyle = '#93a4bd'; g.font = '11px system-ui';
  for (const a of [0, 30, 60, 90]) { g.beginPath(); g.moveTo(0, y(a)); g.lineTo(W, y(a)); g.stroke(); g.fillText(a + '°', 3, y(a) - 3); }
  const line = (vals, color, w = 2) => { g.strokeStyle = color; g.lineWidth = w; g.beginPath(); vals.forEach((a, i) => i ? g.lineTo(x(i), y(a)) : g.moveTo(x(i), y(a))); g.stroke(); };
  line(night.samples.map(s => s.sun), '#ffb020'); line(night.samples.map(s => s.moon), '#e8e2c8');
  const gc = { ra: 266.405, dec: -28.936 }; line(night.samples.map(s => horizonAt(gc.ra, gc.dec, s.time).alt), '#b39dff', 1.5);
  const cols = ['#c9b38f', '#fff2c4', '#ff8a65', '#ffd59a', '#e6cf8f'];
  Pro.ProAstro.planets(night, place).forEach(p => line(p.altitudes, cols[p.index], 1));
  for (let t = Math.ceil(night.start / (3 * HOUR)) * 3 * HOUR; t < night.end; t += 3 * HOUR) g.fillText(fmt(t), (t - night.start) / (night.end - night.start) * W - 14, H - 3);
  $('nightHead').textContent = `La noche · ${fmtDM(night.start)} → ${fmtDM(night.end)}`;
  $('nightEvents').innerHTML = Pro.AstroPhoto.events(night, place).map(e => `<li><span>${esc(e.label)}</span><span class="accent">${fmt(e.time)}</span></li>`).join('');
  $('nightPlanets').innerHTML = Pro.ProAstro.planets(night, place).map(p => `<li><span>${p.name} <span class="tag">mag ${p.magnitude.toFixed(1)} · elong. ${Math.round(p.elongation)}°</span></span>` +
    `<span class="tag">↑ ${fmt(p.rise)} · ${fmt(p.transit)} (${Math.round(p.maximum)}°) · ↓ ${fmt(p.set)}</span></li>`).join('');
  const gal = Pro.AstroPhoto.galactic(night, place);
  $('mwNight').innerHTML = gal.highest ? `Núcleo más alto a las <b>${fmt(gal.highest)}</b> (${Math.round(gal.altitude)}° ${compass(gal.azimuth)}).` : 'El núcleo galáctico no se ve esta noche con noche cerrada.';
}

// ---------- OBJETOS ----------
let objGroup = '', objMin = store.get('objMin', 30);
function spark(c, alts) {
  const { g, W, H } = canvas2d(c, 40), n = alts.length;
  g.strokeStyle = '#22304a'; g.beginPath(); g.moveTo(0, H - 2); g.lineTo(W, H - 2); g.stroke();
  g.strokeStyle = '#7fb8ff'; g.lineWidth = 1.6; g.beginPath();
  alts.forEach((a, i) => { const xx = i / (n - 1) * W, yy = H - 2 - Math.max(0, a) / 90 * (H - 4); i ? g.lineTo(xx, yy) : g.moveTo(xx, yy); }); g.stroke();
}
function renderObjects() {
  if (!place || !messier.length) return;
  const night = tonight(), q = $('objSearch').value.trim().toLowerCase().normalize('NFD').replace(/\p{M}/gu, '');
  const norm = s => (s || '').toLowerCase().normalize('NFD').replace(/\p{M}/gu, '');
  const rows = messier.filter(o => (!objGroup || GROUP(o.type) === objGroup) &&
      (!q || [o.id, o.id.slice(1), o.name, o.ngc].some(s => norm(s).includes(q))))
    .map(o => Pro.AstroPhoto.plan(o, night, place, objMin)).sort((a, b) => b.score - a.score || b.usefulHours - a.usefulHours);
  $('objCount').textContent = `${rows.length} objetos`;
  $('objList').innerHTML = rows.map(p => { const o = p.target, lab = p.score >= 60 ? ['Buena', 'good'] : p.score >= 30 ? ['Media', 'mid'] : ['Baja', 'bad'];
    return `<button class="obj" data-id="${o.id}"><svg class="ic"><use href="${ICON(o.type)}"/></svg><span><div class="oname">${o.id}${o.name ? ' · ' + esc(o.name) : ''}</div>` +
      `<div class="osub">${esc(TYPE[o.type] || '')}${o.mag != null ? ' · mag ' + o.mag : ''}</div></span><canvas class="ospark" data-spark="${o.id}"></canvas>` +
      `<span class="otime">${fmt(p.transit)}</span><span class="oscore ${lab[1]}">${p.score}</span><span class="badge ${lab[1]}">${lab[0]}</span></button>`; }).join('');
  const byId = Object.fromEntries(rows.map(p => [p.target.id, p]));
  $('objList').querySelectorAll('canvas[data-spark]').forEach(c => spark(c, byId[c.dataset.spark].miniAltitudes.filter((_, i) => i % 6 === 0)));
}

// ---------- TIEMPO ----------
function renderWeather() {
  const rows = wxRows();
  if (!place) { $('wxStatus').textContent = 'Elige tu ubicación en Ajustes.'; return; }
  if (!useNet) { $('wxStatus').textContent = 'Previsión desactivada en Ajustes.'; return; }
  if (!rows) { $('wxStatus').textContent = 'Descargando previsión…'; return; }
  $('wxStatus').textContent = `Open-Meteo · ${fmt(weather.fetched)}`;
  const now = Date.now() - HOUR, data = rows.filter(h => h.time >= now).slice(0, 48);
  if (!data.length) return;
  // Nubes apiladas: bajas, medias y altas, con la noche sombreada.
  const { g, W, H } = canvas2d($('cloudChart'), 200), n = data.length, x = i => i / (n - 1) * (W - 30) + 28, y = v => H - 22 - v / 100 * (H - 32);
  data.forEach((h, i) => { if (Astro.sunAlt(h.time + HOUR / 2, place) < -12) { g.fillStyle = '#1a2440'; g.fillRect(x(i) - (W - 30) / n / 2, 6, (W - 30) / n + 1, H - 28); } });
  g.fillStyle = '#93a4bd'; g.font = '11px system-ui'; g.strokeStyle = '#22304a';
  for (const v of [0, 25, 50, 75, 100]) { g.beginPath(); g.moveTo(28, y(v)); g.lineTo(W, y(v)); g.stroke(); g.fillText(v, 2, y(v) + 4); }
  const band = (lo, hi, color) => { g.fillStyle = color; g.beginPath();
    data.forEach((h, i) => { const v = hi(h); i ? g.lineTo(x(i), y(v)) : g.moveTo(x(i), y(v)); });
    for (let i = n - 1; i >= 0; i--) g.lineTo(x(i), y(lo(data[i]))); g.closePath(); g.fill(); };
  const L = h => Math.min(100, h.low || 0), M = h => Math.min(100, L(h) + (h.mid || 0) * (1 - L(h) / 100)), T = h => Math.min(100, M(h) + (h.high || 0) * (1 - M(h) / 100));
  band(() => 0, L, 'rgba(111,211,160,.85)'); band(L, M, 'rgba(255,176,32,.85)'); band(M, T, 'rgba(127,184,255,.75)');
  for (let i = 0; i < n; i += 12) g.fillText(`${fmtDM(data[i].time)} ${fmt(data[i].time)}`, Math.min(W - 70, x(i) - 10), H - 4);
  $('cloudChart').onclick = e => { const r = $('cloudChart').getBoundingClientRect(), i = Math.max(0, Math.min(n - 1, Math.round((e.clientX - r.left - 28) / (W - 30) * (n - 1))));
    const h = data[i], q = hourFactors(h.time + HOUR / 2, h);
    $('wxDetail').innerHTML = `<b>${fmtDay(h.time)} ${fmt(h.time)}</b> · calidad ${q.quality == null ? '—' : Math.round(q.quality * 100) + '/100'}<br>` +
      `Nubes ${h.cover} % (bajas ${h.low}, medias ${h.mid}, altas ${h.high}) · lluvia ${h.rain ?? '—'} %<br>` +
      `T ${h.t} °C · rocío ${h.dp} °C · HR ${h.hum ?? '—'} % · viento ${Math.round(h.wind)} km/h · rachas ${h.gust == null ? '—' : Math.round(h.gust)} km/h<br>` +
      `Visibilidad ${h.vis == null ? '—' : Math.round(h.vis / 1000) + ' km'} · presión ${h.pres == null ? '—' : Math.round(h.pres) + ' hPa'} · 250 hPa ${h.jet == null ? '—' : Math.round(h.jet) + ' km/h'} · turbulencia ${Pro.Forecast.turbulence(h.jet)}`; };
  // Temperatura y punto de rocío.
  const t2 = canvas2d($('tempChart'), 140), vals = data.flatMap(h => [h.t, h.dp]).filter(v => v != null);
  const lo = Math.floor(Math.min(...vals)) - 1, hi = Math.ceil(Math.max(...vals)) + 1, yy = v => t2.H - 18 - (v - lo) / (hi - lo) * (t2.H - 28);
  t2.g.strokeStyle = '#22304a'; t2.g.fillStyle = '#93a4bd'; t2.g.font = '11px system-ui';
  for (const v of [lo, Math.round((lo + hi) / 2), hi]) { t2.g.beginPath(); t2.g.moveTo(28, yy(v)); t2.g.lineTo(t2.W, yy(v)); t2.g.stroke(); t2.g.fillText(v, 2, yy(v) + 4); }
  data.forEach((h, i) => { if (h.t != null && h.dp != null && h.t - h.dp < 3) { t2.g.fillStyle = 'rgba(255,122,122,.10)'; t2.g.fillRect(x(i) - (t2.W - 30) / n / 2, 6, (t2.W - 30) / n + 1, t2.H - 24); } });
  const tl = (k, c) => { t2.g.strokeStyle = c; t2.g.lineWidth = 2; t2.g.beginPath(); data.forEach((h, i) => i ? t2.g.lineTo(x(i), yy(h[k])) : t2.g.moveTo(x(i), yy(h[k]))); t2.g.stroke(); };
  tl('t', '#7fb8ff'); tl('dp', '#ffb020');
  // 7 noches.
  let nights = []; try { nights = Pro.Forecast.week(Date.now(), place, rows, useNet); } catch { nights = []; }
  const best = Pro.Forecast.best(nights);
  $('weekList').innerHTML = nights.map(nn => `<div class="${best && best.start === nn.start ? 'best' : ''}">${fmtDay(nn.start)}` +
    `<b class="${nn.score == null ? '' : nn.score >= 60 ? 'good' : nn.score >= 30 ? 'mid' : 'bad'}">${nn.score == null ? '—' : nn.score}</b>` +
    `nubes ${nn.clouds == null ? '—' : Math.round(nn.clouds) + ' %'}<br>Luna ${nn.moon == null ? '—' : Math.round(nn.moon * 100) + ' %'}</div>`).join('');
}

// ---------- Navegación, ajustes y arranque ----------
function renderAll() {
  showPlace(); tickClock();
  if (!place || !stars) return;
  renderHome(); renderNightTab(); renderObjects(); renderWeather();
  if (typeof renderSky === 'function') renderSky();
  if (typeof renderExtras === 'function') renderExtras();
}
function showTab(name) {
  document.querySelectorAll('.tabs button').forEach(x => x.classList.toggle('active', x.dataset.tab === name));
  document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t.id === 'tab-' + name));
  store.set('tab', name);
  if (name === 'cielo' && typeof renderSky === 'function') requestAnimationFrame(renderSky);
  if (name === 'tiempo') requestAnimationFrame(renderWeather);
  if (name === 'noche') requestAnimationFrame(renderNightTab);
  if (name === 'inicio') requestAnimationFrame(renderHome);
  if (name === 'objetos') requestAnimationFrame(renderObjects);
}
function savePlace(lat, lon, name) {
  if (!(Math.abs(lat) <= 90 && Math.abs(lon) <= 180)) { $('saveMsg').textContent = 'Latitud entre −90 y 90 y longitud entre −180 y 180.'; return false; }
  place = { lat, lon, name: (name || '').slice(0, 40) }; store.set('place', place); nightCache = null; return true;
}
function wire() {
  document.querySelectorAll('.tabs button').forEach(b => b.onclick = () => showTab(b.dataset.tab));
  const setRed = on => { document.documentElement.classList.toggle('red', on); $('redBtn').setAttribute('aria-pressed', on); store.set('red', on); };
  setRed(store.get('red', false)); $('redBtn').onclick = () => setRed(!document.documentElement.classList.contains('red'));
  $('gpsBtn').onclick = () => {
    if (!navigator.geolocation) { $('saveMsg').textContent = 'Este navegador no da la ubicación: escríbela a mano.'; return; }
    $('saveMsg').textContent = 'Pidiendo ubicación…';
    navigator.geolocation.getCurrentPosition(p => { savePlace(+p.coords.latitude.toFixed(4), +p.coords.longitude.toFixed(4), $('nameIn').value || 'mi ubicación');
      $('saveMsg').textContent = 'Ubicación guardada.'; refresh(true); },
    e => { $('saveMsg').textContent = 'No se pudo obtener la ubicación (' + e.message + '). Escríbela a mano.'; }, { timeout: 15000, maximumAge: 600000 });
  };
  $('saveBtn').onclick = () => {
    const lat = parseFloat($('latIn').value.replace(',', '.')), lon = parseFloat($('lonIn').value.replace(',', '.'));
    bortle = Math.max(1, Math.min(9, parseInt($('bortleIn').value, 10) || 5)); useNet = $('netIn').checked;
    store.set('bortle', bortle); store.set('net', useNet);
    if (savePlace(lat, lon, $('nameIn').value)) { $('saveMsg').textContent = 'Guardado.'; refresh(true); }
  };
  $('objSearch').oninput = renderObjects;
  $('objChips').onclick = e => { const b = e.target.closest('button'); if (!b) return; objGroup = b.dataset.g;
    $('objChips').querySelectorAll('button').forEach(x => x.classList.toggle('on', x === b)); renderObjects(); };
  $('objMinBtn').onclick = () => { objMin = objMin === 30 ? 45 : objMin === 45 ? 15 : 30; store.set('objMin', objMin); $('objMin').textContent = objMin + '°'; renderObjects(); };
  $('objMin').textContent = objMin + '°';
  $('bestMessier').onclick = e => { const b = e.target.closest('button[data-id]'); if (b && typeof openObject === 'function') { showTab('objetos'); openObject(b.dataset.id); } };
  $('objList').onclick = e => { const b = e.target.closest('button[data-id]'); if (b && typeof openObject === 'function') openObject(b.dataset.id); };
  window.addEventListener('resize', () => requestAnimationFrame(() => { const t = store.get('tab', 'inicio'); showTab(t); }));
}
async function refresh(force) { renderAll(); await fetchWeather(force); renderAll(); }
(async () => {
  wire(); showPlace(); tickClock(); setInterval(tickClock, 1000);
  try { await loadCatalogs(); } catch (e) { $('scoreSub').textContent = 'No se pudieron cargar los catálogos: ' + e.message; return; }
  showTab(place ? store.get('tab', 'inicio') : 'ajustes');
  await refresh(false);
  setInterval(() => { nightCache = null; renderAll(); }, 5 * 60000);
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
})();
