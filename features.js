// AstroTab web: el resto de AstroTab Android (satélites, auroras, eventos, Luna, planificador, 7 noches, foto,
// sesión, bitácora, alarmas, equipos, linterna, búsqueda, encuadre y brújula). Cálculos en pro.js (portado de Android).
'use strict';
const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmtDate = ms => new Date(ms).toLocaleDateString('es-ES', { day: 'numeric', month: 'short' });
const num = id => parseFloat(String($(id).value).replace(',', '.'));

// ---------- Noche (cacheada 10 min) ----------
let nightCache = null;
function tonight() {
  const key = placeKey();
  if (!nightCache || nightCache.key !== key || Date.now() - nightCache.at > 10 * 60000)
    nightCache = { key, at: Date.now(), night: Pro.AstroPhoto.timeline(Date.now(), place) };
  return nightCache.night;
}

// ---------- Equipo y perfiles ----------
function equipment() {
  try {
    return new Pro.Equipment({ focal: num('focIn'), aperture: num('apIn'), sensor: $('sensorIn').value || 'FULL',
      pixel: num('pixIn'), tracking: $('trackIn').checked });
  } catch { return new Pro.Equipment({}); }
}
function renderEquipment() {
  let e; try { e = new Pro.Equipment({ focal: num('focIn'), aperture: num('apIn'), sensor: $('sensorIn').value || 'FULL', pixel: num('pixIn'), tracking: $('trackIn').checked }); }
  catch { $('expOut').textContent = 'Revisa focal, diafragma y píxel (deben ser mayores que 0).'; return; }
  const npf0 = Pro.Optics.npf(e, 0), npf45 = Pro.Optics.npf(e, 45);
  const fw = Pro.Optics.field(e.width, e.focal), fh = Pro.Optics.field(e.height, e.focal);
  $('expOut').innerHTML = `Regla 500: <b>${Pro.Optics.rule500(e).toFixed(1)} s</b> · NPF en el ecuador celeste: <b>${npf0.toFixed(1)} s</b> (a +45°: ${npf45.toFixed(1)} s)<br>` +
    `<span class="tag">Campo ${fw.toFixed(1)}° × ${fh.toFixed(1)}° · escala ${Pro.Optics.scale(e).toFixed(1)}″/píxel${e.tracking ? ' · con seguimiento' : ''}</span>`;
  store.set('equip', { focal: e.focal, aperture: e.aperture, sensor: e.sensor.name, pixel: e.pixel, tracking: e.tracking });
  renderTools(); if ($('showFrame').checked) renderSky();
}
function setEquipInputs(q) {
  $('focIn').value = q.focal; $('apIn').value = q.aperture; $('sensorIn').value = q.sensor && q.sensor.name || q.sensor || 'FULL';
  $('pixIn').value = q.pixel; $('trackIn').checked = !!q.tracking;
}
function renderProfiles() {
  let list = []; try { list = Pro.EquipmentProfiles.read(); } catch { /* sin perfiles */ }
  $('profileSel').innerHTML = list.length ? list.map(p => `<option value="${esc(p.id)}">${esc(p.name)}</option>`).join('') : '<option value="">Sin perfiles</option>';
  return list;
}

// ---------- Satélites ----------
let tleData = store.get('tle', null), passCache = null, satHighlight = null;
async function refreshTle(force) {
  if (!useNet || !navigator.onLine) return;
  if (!force && tleData && Date.now() - tleData.fetched < 12 * HOUR) return;
  $('satStatus').textContent = 'Descargando órbitas de CelesTrak…';
  try { tleData = await Sats.fetchTle(); store.set('tle', tleData); passCache = null; }
  catch (e) { $('satStatus').textContent = 'No se pudieron descargar las órbitas: ' + e.message; }
}
function passes() {
  if (!tleData || !place) return [];
  const key = placeKey() + $('satMin').value + $('satIss').checked + tleData.fetched;
  if (!passCache || passCache.key !== key || Date.now() - passCache.at > 5 * 60000)
    passCache = { key, at: Date.now(), list: Sats.passes(tleData.tle, place, Date.now() - 10 * 60000, 24, +$('satMin').value, $('satIss').checked) };
  return passCache.list;
}
function renderSats() {
  if (!place) return;
  if (!useNet && !tleData) { $('satStatus').textContent = 'Activa «Descargar previsión» en Ubicación para bajar las órbitas.'; $('satList').innerHTML = ''; return; }
  if (!tleData) { $('satList').innerHTML = ''; return; }
  const list = passes();
  const age = (Date.now() - tleData.fetched) / HOUR;
  $('satStatus').textContent = `${tleData.tle.length} satélites · órbitas de hace ${age < 1 ? 'menos de 1 h' : Math.round(age) + ' h'} · próximas 24 h`;
  $('satList').innerHTML = list.slice(0, 25).map((p, i) => `<li data-pass="${i}" role="button" tabindex="0"><span><b>${esc(p.tle.name)}</b><br>` +
    `<span class="tag">${fmtDay(p.start.time)} ${fmt(p.start.time)} → ${fmt(p.end.time)} · ${compass(p.start.az)} → ${compass(p.end.az)}${p.old ? ' · órbita antigua' : ''}</span></span>` +
    `<span class="good">${Math.round(p.peak.alt)}°<br><span class="tag">${fmt(p.peak.time)}</span></span></li>`).join('') ||
    '<li class="tag">Ningún paso visible en las próximas 24 h con ese filtro.</li>';
}

// ---------- Auroras (NOAA SWPC) ----------
let kpData = store.get('kp', null);
async function refreshKp() {
  if (!useNet || !navigator.onLine || (kpData && Date.now() - kpData.fetched < 30 * 60000)) return;
  try {
    const get = u => fetch(u).then(r => { if (!r.ok) throw Error('NOAA HTTP ' + r.status); return r.json(); });
    const [obs, fc] = await Promise.all([get('https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json'),
      get('https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json')]);
    const rows = arr => arr.filter(r => !Array.isArray(r) || r[0] !== 'time_tag').map(r => Array.isArray(r) ? { time_tag: r[0], Kp: r[1], observed: r[2] } : r);
    const samples = [];
    for (const r of rows(obs)) samples.push({ time: Date.parse(r.time_tag + 'Z'), value: +(r.Kp ?? r.kp), kind: 'OBSERVED', duration: 3 * HOUR });
    for (const r of rows(fc)) { const t = Date.parse(r.time_tag + 'Z'); if (samples.some(s => s.time === t)) continue;
      samples.push({ time: t, value: +(r.kp ?? r.Kp), kind: r.observed === 'observed' ? 'OBSERVED' : r.observed === 'estimated' ? 'ESTIMATED' : 'PREDICTED', duration: 3 * HOUR }); }
    kpData = { fetched: Date.now(), samples: samples.filter(s => isFinite(s.time) && isFinite(s.value)).sort((a, b) => a.time - b.time) };
    store.set('kp', kpData);
  } catch (e) { $('auroraBox').innerHTML = `<p class="muted">Sin datos de NOAA: ${esc(e.message)}</p>`; }
}
function renderAurora() {
  if (!place) return;
  const need = Pro.Aurora.threshold(place.lat, place.lon);
  if (!kpData) { $('auroraBox').innerHTML = `<p>Necesitas un Kp de <b>${need > 9 ? 'más de 9 (casi imposible aquí)' : need.toFixed(1)}</b> para tener opciones en tu latitud.</p><p class="muted">${useNet ? 'Descargando Kp…' : 'Activa la previsión para ver el Kp.'}</p>`; return; }
  const now = Date.now(), cur = [...kpData.samples].reverse().find(s => s.kind !== 'PREDICTED' && s.time <= now);
  const next = kpData.samples.filter(s => s.time > now && s.time < now + 72 * HOUR), max = next.reduce((m, s) => s.value > m.value ? s : m, { value: -1 });
  const alert = Pro.Aurora.currentAlert(kpData.samples, now, place);
  $('auroraBox').innerHTML = `<dl><dt>Kp ahora</dt><dd class="${alert ? 'good' : ''}">${cur ? cur.value.toFixed(1) : '—'}</dd>` +
    `<dt>Máximo previsto (72 h)</dt><dd>${max.value >= 0 ? max.value.toFixed(1) + ' · ' + fmtDay(max.time) + ' ' + fmt(max.time) : '—'}</dd>` +
    `<dt>Kp necesario aquí</dt><dd>${need > 9 ? '> 9' : need.toFixed(1)}</dd></dl>` +
    (alert ? '<p class="good">¡Kp suficiente ahora! Mira al norte con el cielo oscuro.</p>' : '');
}

// ---------- Eventos, meteoros y calendario lunar ----------
let eclipseRows = [];
function renderEvents() {
  if (!place) return;
  let ev = []; try { ev = Pro.EventCalendar.month(Date.now(), place, eclipseRows); } catch (e) { ev = []; }
  $('eventList').innerHTML = ev.filter(e => e.time > Date.now() - 12 * HOUR).slice(0, 30).map(e => `<li><span>${e.highlighted ? '<b>' : ''}${esc(e.title)}${e.highlighted ? '</b>' : ''}<br><span class="tag">${esc(e.detail || '')}</span></span>` +
    `<span class="tag">${fmtDate(e.time)} ${fmt(e.time)}</span></li>`).join('') || '<li class="tag">Sin eventos este mes.</li>';
  const met = Pro.ProAstro.meteors(Date.now(), place);
  $('meteorList').innerHTML = met.map(m => `<li><span>${esc(m.shower.name)} <span class="tag">THZ ${m.shower.zhr}</span><br>` +
    `<span class="tag">radiante a ${Math.round(m.radiantAltitude)}° a medianoche · Luna ${Math.round(m.illumination * 100)} %</span></span><span>${fmtDate(m.peak)}</span></li>`).join('');
}
let moonCalDone = '';
function renderMoonCal() {
  if (!place || !$('moonCal').closest('details').open) return;
  const key = placeKey() + new Date().toDateString(); if (moonCalDone === key) return; moonCalDone = key;
  const days = Pro.ProAstro.moonMonth(Date.now(), place), first = new Date(days[0].time).getDay();
  const pad = (first + 6) % 7, today = new Date().getDate();
  $('moonCal').innerHTML = ['L', 'M', 'X', 'J', 'V', 'S', 'D'].map(d => `<span class="tag">${d}</span>`).join('') + '<span></span>'.repeat(pad) +
    days.map(d => `<div${d.day === today ? ' style="outline:2px solid var(--blue)"' : ''}><b>${d.day}</b>${Math.round(d.illumination * 100)} %<br><span class="tag">${d.moonlessHours.toFixed(1)} h</span></div>`).join('');
}

// ---------- Herramientas de foto ----------
function renderTools() {
  if (!place) return;
  const e = equipment(), pol = Pro.AstroPhoto.polar(Date.now(), place);
  const c = $('polarClock'), g = c.getContext('2d'), r = 60;
  g.clearRect(0, 0, 140, 140); g.strokeStyle = '#7fb8ff'; g.lineWidth = 2; g.beginPath(); g.arc(70, 70, r, 0, 7); g.stroke();
  g.fillStyle = '#94a7c1'; g.font = '11px system-ui'; g.textAlign = 'center'; g.textBaseline = 'middle';
  for (let h = 0; h < 12; h++) { const a = h / 12 * 2 * Math.PI; g.fillText(h === 0 ? '12' : h, 70 + (r - 12) * Math.sin(a), 70 - (r - 12) * Math.cos(a)); }
  const a = pol.clockAngle * Math.PI / 180; g.fillStyle = '#ffb020'; g.beginPath(); g.arc(70 + r * Math.sin(a), 70 - r * Math.cos(a), 6, 0, 7); g.fill();
  const hh = pol.clockAngle / 30, hours = Math.floor(hh), mins = Math.round((hh - hours) * 60) % 60;
  $('polarOut').innerHTML = `${esc(pol.star)} en la posición de las <b>${hours === 0 ? 12 : hours}:${String(mins).padStart(2, '0')}</b> del reloj del buscador polar.<br><span class="tag">Distancia al polo ${pol.radius.toFixed(2)}° · ángulo horario ${pol.hourAngle.toFixed(1)}°</span>`;
  const gal = Pro.AstroPhoto.galactic(tonight(), place);
  $('mwOut').innerHTML = gal.highest ? `El núcleo de la Vía Láctea está más alto a las <b>${fmt(gal.highest)}</b> (${Math.round(gal.altitude)}° ${compass(gal.azimuth)}).` +
    `<br><span class="tag">Visible con noche cerrada: ${gal.windows.map(w => fmt(w.start) + '–' + fmt(w.end)).join(', ')}</span>` :
    'El núcleo no se ve esta noche con noche cerrada (sale de día o muy bajo). Mejor de abril a septiembre.';
  try { const f = Pro.ProTools.focus(e, num('focusDist'));
    $('focusOut').innerHTML = `Hiperfocal <b>${f.hyperfocalMetres.toFixed(1)} m</b> · nítido de ${f.nearMetres.toFixed(1)} m a ${f.farMetres == null ? '∞' : f.farMetres.toFixed(1) + ' m'}`; }
  catch { $('focusOut').textContent = 'La distancia debe ser mayor que la focal.'; }
  try { const t = Pro.ProTools.timelapse(num('tlInt'), num('tlDur') * 60, num('tlFps'), num('tlMb'));
    $('tlOut').innerHTML = `<b>${t.frames}</b> fotos · vídeo de ${t.clipSeconds.toFixed(1)} s · ${(t.megabytes / 1024).toFixed(1)} GB · el cielo gira ${Pro.ProTools.arc(num('tlInt')).toFixed(2)}° entre fotos`; }
  catch { $('tlOut').textContent = 'Revisa los datos del timelapse.'; }
  try { const fw = Pro.Optics.field(e.width, e.focal), fh = Pro.Optics.field(e.height, e.focal), cov = num('mosCov'), ov = num('mosOv');
    const nx = Pro.ProTools.mosaicAxis(cov, fw, ov), ny = Pro.ProTools.mosaicAxis(cov, fh, ov);
    $('mosOut').innerHTML = `<b>${nx} × ${ny}</b> = ${nx * ny} fotos (campo ${fw.toFixed(1)}° × ${fh.toFixed(1)}°)`; }
  catch { $('mosOut').textContent = 'Revisa zona y solape (0–95 %).'; }
  try { const s = Pro.ProTools.stack(Math.round(num('stFrames')), num('stExp'));
    $('stOut').innerHTML = `Señal/ruido ×${s.snrGain.toFixed(1)} · integración ${(s.integrationSeconds / 60).toFixed(0)} min · darks ${s.darks}, flats ${s.flats}, bias ${s.bias}`; }
  catch { $('stOut').textContent = 'Revisa tomas y exposición.'; }
}

// ---------- Sesión con temporizador ----------
let timer = null, timerTick = null, lastPhase = '', wakeLock = null, audio = null;
function beep(freq = 880, ms = 180) {
  try { audio = audio || new (window.AudioContext || window.webkitAudioContext)();
    const o = audio.createOscillator(), g = audio.createGain(); o.frequency.value = freq; o.connect(g); g.connect(audio.destination);
    g.gain.value = 0.2; o.start(); o.stop(audio.currentTime + ms / 1000); } catch { /* sin audio */ }
  if (navigator.vibrate) navigator.vibrate(ms);
}
const mmss = ms => { const s = Math.ceil(ms / 1000); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; };
async function startTimer() {
  try {
    const plan = new Pro.SessionPlan({ lights: Math.round(num('ssLights')), exposureSeconds: num('ssExp'), pauseSeconds: num('ssPause'), darks: Math.round(num('ssDarks')) });
    timer = new Pro.SessionTimer(plan, () => performance.now()); lastPhase = ''; beep(1200);
    try { wakeLock = await navigator.wakeLock?.request('screen'); } catch { /* el sistema puede negarlo */ }
    clearInterval(timerTick); timerTick = setInterval(tickTimer, 250); tickTimer();
  } catch { $('ssShow').textContent = 'Revisa los datos de la sesión.'; }
}
function tickTimer() {
  if (!timer) return;
  const s = timer.state(), phase = s.done ? 'fin' : s.index + (s.exposing ? 'e' : 'p');
  if (phase !== lastPhase) { if (lastPhase) beep(s.exposing ? 1200 : 660); lastPhase = phase; }
  $('ssShow').innerHTML = s.done ? 'Sesión terminada' :
    `${esc(timer.plan.kind(s.index))} ${s.index + 1}/${timer.plan.count}<br>${s.exposing ? 'Exponiendo' : 'Pausa'} ${mmss(s.remainingMillis)}<br><span class="tag">Quedan ${mmss(s.totalRemainingMillis)}</span>`;
  if (s.done) stopTimer(false);
}
function stopTimer(manual = true) {
  if (timer && manual) timer.stop(); clearInterval(timerTick); timerTick = null; timer = null;
  try { wakeLock?.release(); } catch { /* ya liberado */ } wakeLock = null;
  if (manual) $('ssShow').textContent = 'Parado';
}

// ---------- Bitácora ----------
const book = new Pro.LogBook();
function renderBook() {
  let rows = []; try { rows = book.read(); } catch { $('lbList').innerHTML = '<li class="bad">Bitácora dañada: expórtala y empieza otra.</li>'; return; }
  $('lbList').innerHTML = rows.slice(-20).reverse().map(r => `<li><span><b>${esc(r.target)}</b> · ${r.frames} × ${r.exposure} s<br><span class="tag">${esc(r.equipment)} ${esc(r.notes)}</span></span>` +
    `<span class="tag">${fmtDate(r.time)}</span></li>`).join('') || '<li class="tag">Aún no hay sesiones.</li>';
}

// ---------- Alarmas (con la app abierta) ----------
const alarms = new Pro.AlarmStore();
const ALARM_LABEL = { ASTRONOMICAL_DUSK: 'Noche cerrada', MOONRISE: 'Salida de la Luna', ISS: 'Paso de la ISS', KP: 'Kp suficiente' };
function nextAlarmTime(kind) {
  const now = Date.now(), ev = Pro.AstroPhoto.events(tonight(), place);
  const later = kind === 'ASTRONOMICAL_DUSK' || kind === 'MOONRISE' ? Pro.AstroPhoto.events(Pro.AstroPhoto.timeline(now + 24 * HOUR, place), place) : [];
  return Pro.AlarmPolicy.next(kind, now, ev.concat(later), [], passes());
}
function renderAlarms() {
  let list = []; try { list = alarms.read(); } catch { /* vacías */ }
  $('alList').innerHTML = list.map(a => `<li><span>${esc(a.label)}<br><span class="tag">${a.time ? fmtDay(a.time) + ' ' + fmt(a.time) : 'cuando ocurra'}</span></span>` +
    `<button data-del="${a.id}">Quitar</button></li>`).join('') || '<li class="tag">Sin alarmas.</li>';
}
function checkAlarms() {
  let list = []; try { list = alarms.read(); } catch { return; }
  const now = Date.now(), kp = kpData ? Pro.AlarmPolicy.kpReached(kpData.samples, now, Pro.Aurora.threshold(place.lat, place.lon)) : false;
  for (const a of list) {
    if (!Pro.AlarmPolicy.due(a, now, kp)) continue;
    const shown = store.get('alarmShown', {}); if (shown[a.id] && now - shown[a.id] < 3 * HOUR) continue;
    shown[a.id] = now; store.set('alarmShown', shown);
    beep(988, 400); setTimeout(() => beep(1318, 400), 500);
    if ('Notification' in window && Notification.permission === 'granted') new Notification('AstroTab', { body: a.label });
    alert('⏰ ' + a.label);
  }
}

// ---------- Objetos: ficha y plan de sesión ----------
let sessionReq = store.get('sessionReq', []), currentObj = null;
function openObject(id) {
  const o = messier.find(m => m.id === id); if (!o || !place) return;
  currentObj = o;
  const night = tonight(), plan = Pro.AstroPhoto.plan(o, night, place, 30), fit = Pro.Optics.fit(equipment(), o);
  $('objTitle').textContent = o.id + (o.name ? ' · ' + o.name : '');
  $('objFacts').innerHTML = `<dt>Tipo</dt><dd>${esc(TYPE[o.type] || '')}</dd><dt>Magnitud</dt><dd>${o.mag ?? '—'}</dd><dt>Tamaño</dt><dd>${esc(o.size || '—')}′</dd>` +
    `<dt>Horas útiles (≥30°, noche cerrada)</dt><dd>${plan.usefulHours.toFixed(1)} h</dd><dt>Culmina</dt><dd>${fmt(plan.transit)} a ${Math.round(plan.transitAltitude)}°</dd>` +
    `<dt>Nota para foto</dt><dd>${plan.score}/100</dd><dt>Con tu equipo</dt><dd>${fit ? (fit.fits ? 'Cabe' : 'No cabe') + ` (${Math.round(Math.max(fit.widthPercent, fit.heightPercent))} % del encuadre)` : '—'}</dd>`;
  drawChart(night, plan.altitudes);
  $('objCard').hidden = false; $('objCard').scrollIntoView({ behavior: 'smooth' });
}
function drawChart(night, alts) {
  const c = $('objChart'), dpr = devicePixelRatio || 1, W = c.clientWidth || 300, H = 120;
  c.width = W * dpr; c.height = H * dpr; const g = c.getContext('2d'); g.setTransform(dpr, 0, 0, dpr, 0, 0);
  g.clearRect(0, 0, W, H); const n = alts.length, x = i => i / (n - 1) * W, y = a => H - 10 - Math.max(0, a) / 90 * (H - 20);
  night.samples.forEach((s, i) => { if (s.sun < -18) { g.fillStyle = 'rgba(127,184,255,.08)'; g.fillRect(x(i), 0, W / n + 1, H); } });
  g.strokeStyle = '#253249'; g.beginPath(); g.moveTo(0, y(30)); g.lineTo(W, y(30)); g.stroke();
  g.strokeStyle = '#5fd38a'; g.lineWidth = 2; g.beginPath(); alts.forEach((a, i) => i ? g.lineTo(x(i), y(a)) : g.moveTo(x(i), y(a))); g.stroke();
  g.fillStyle = '#94a7c1'; g.font = '11px system-ui'; g.fillText(fmt(night.start), 2, H - 1); g.fillText(fmt(night.end), W - 34, H - 1); g.fillText('30°', 2, y(30) - 3);
}
function renderPlan() {
  $('planList').innerHTML = sessionReq.map(r => `<li><span>${r.id}${NAMES[r.id] ? ' · ' + NAMES[r.id] : ''}</span><span class="tag">${r.integrationMinutes} min</span></li>`).join('') || '<li class="tag">Sesión vacía.</li>';
}
function makePlan() {
  if (!sessionReq.length || !place) return;
  const night = tonight(), plans = sessionReq.map(r => Pro.AstroPhoto.plan(messier.find(m => m.id === r.id), night, place, 30));
  try {
    const out = Pro.SessionPlanner.propose(sessionReq, plans, num('planExp'), num('planPause'), equipment());
    $('planOut').innerHTML = out.blocks.map(b => `<li><span><b>${b.id}</b> · ${b.frames} × ${b.exposure.toFixed(0)} s</span><span>${fmt(b.start)}–${fmt(b.end)}</span></li>`).join('') +
      (out.unscheduled.length ? `<li class="bad">Sin hueco esta noche: ${out.unscheduled.join(', ')}</li>` : '') || '<li class="tag">No hay ventanas útiles esta noche.</li>';
  } catch (e) { $('planOut').innerHTML = `<li class="bad">${esc(e.message)}</li>`; }
}

// ---------- Tiempo: 7 noches ----------
function renderWeek() {
  if (!place) return;
  const rows = weather && weather.key === placeKey() ? weather.hours : null;
  let nights = []; try { nights = Pro.Forecast.week(Date.now(), place, rows, useNet); } catch { nights = []; }
  const best = Pro.Forecast.best(nights);
  $('weekList').innerHTML = nights.map(n => `<li><span>${best && best.start === n.start ? '⭐ ' : ''}${fmtDay(n.start)}<br><span class="tag">` +
    `${n.darkHours.toFixed(1)} h oscuras · nubes ${n.clouds == null ? '—' : Math.round(n.clouds) + ' %'} · Luna ${n.moon == null ? '—' : Math.round(n.moon * 100) + ' %'} · chorro ${Pro.Forecast.turbulence(n.jet)}</span></span>` +
    `<span class="${n.score == null ? 'tag' : n.score >= 60 ? 'good' : n.score >= 30 ? 'mid' : 'bad'}">${n.score == null ? 'sin datos' : n.score + '/100'}</span></li>`).join('');
}

// ---------- Cielo: búsqueda, encuadre, satélites y brújula ----------
let skyTarget = null;
function findTarget(q) {
  q = q.trim().toLowerCase(); if (!q) return null;
  const d = Astro.dayNumber(skyTime);
  if (q === 'luna') { const m = Astro.moon(d).eq; return { label: 'Luna', ra: m.ra, dec: m.dec }; }
  const p = Astro.PLANETS.findIndex(n => n.toLowerCase() === q);
  if (p >= 0) { const e = Astro.planet(p, d).eq; return { label: Astro.PLANETS[p], ra: e.ra, dec: e.dec }; }
  const m = messier.find(o => o.id.toLowerCase() === q || (o.name && o.name.toLowerCase() === q) || q === o.id.slice(1));
  if (m) return { label: m.id + (m.name ? ' · ' + m.name : ''), ra: m.ra, dec: m.dec, messier: m };
  for (let i = 0; i < stars.n; i++) if (stars.name[i] !== 255 && stars.names[stars.name[i]].toLowerCase() === q)
    return { label: stars.names[stars.name[i]], ra: stars.ra[i], dec: stars.dec[i] };
  return null;
}
function drawSkyExtras(g, proj, t) {
  if ($('showSats').checked) for (const ps of passes()) {
    if (t < ps.track[0].time - 60000 || t > ps.track[ps.track.length - 1].time + 60000) continue;
    g.strokeStyle = '#ffb020'; g.setLineDash([4, 4]); g.beginPath(); let started = false;
    for (const pt of ps.track) { const a = horPix(pt.alt, pt.az, proj); if (!a) continue; started ? g.lineTo(a[0], a[1]) : g.moveTo(a[0], a[1]); started = true; }
    g.stroke(); g.setLineDash([]);
  }
  if (skyTarget) {
    const p = proj(skyTarget.ra, skyTarget.dec);
    if (p) {
      g.strokeStyle = '#ff6b6b'; g.lineWidth = 2; g.beginPath(); g.arc(p[0], p[1], 14, 0, 7); g.stroke(); g.lineWidth = 1;
      if ($('showFrame').checked) {
        const e = equipment(), q = proj(skyTarget.ra, Math.min(89.9, skyTarget.dec + 1)), ppd = q ? Math.hypot(q[0] - p[0], q[1] - p[1]) : 0;
        const w = Pro.Optics.field(e.width, e.focal) * ppd, h = Pro.Optics.field(e.height, e.focal) * ppd;
        g.strokeStyle = '#7fb8ff'; g.strokeRect(p[0] - w / 2, p[1] - h / 2, w, h);
        g.fillStyle = '#7fb8ff'; g.fillText(`${e.focal} mm`, p[0] - w / 2, p[1] - h / 2 - 4);
      }
    }
  }
}
// Alt/az directo a píxeles: misma fórmula estereográfica que proj de app.js.
function horPix(alt, az, proj) {
  const c = $('sky'), W = c.clientWidth, R = W / 2 - 14; if (alt < -1) return null;
  const r = R * Math.tan((90 - alt) * Math.PI / 360) / Math.tan(Math.PI / 4), a = (az + skyRot) * Math.PI / 180;
  return [W / 2 - r * Math.sin(a), W / 2 - r * Math.cos(a)];
}
let compassOn = false;
function onOrientation(e) {
  const h = e.webkitCompassHeading != null ? e.webkitCompassHeading : e.alpha != null ? 360 - e.alpha : null;
  if (h == null) return; skyRot = -h; requestAnimationFrame(renderSky);
}
async function toggleCompass() {
  if (compassOn) { removeEventListener('deviceorientation', onOrientation); compassOn = false; $('compassBtn').textContent = 'Brújula'; return; }
  try {
    if (typeof DeviceOrientationEvent !== 'undefined' && DeviceOrientationEvent.requestPermission) {
      if (await DeviceOrientationEvent.requestPermission() !== 'granted') throw Error('permiso denegado');
    }
    addEventListener('deviceorientation', onOrientation); compassOn = true; $('compassBtn').textContent = 'Brújula: activa';
    $('skyInfo').textContent = 'Sujeta el móvil en horizontal: arriba del mapa queda hacia donde miras.';
  } catch (e) { $('skyInfo').textContent = 'No se pudo usar la brújula: ' + e.message; }
}

// ---------- Arranque y enlaces ----------
function renderExtras() {
  if (!place || !messier.length) return;
  renderSats(); renderAurora(); renderEvents(); renderMoonCal(); renderTools(); renderBook(); renderAlarms(); renderPlan(); renderWeek();
}
(function wireExtras() {
  $('sensorIn').innerHTML = Object.values(Pro.SensorFormat).map(s => `<option value="${s.name}">${esc(s.label)}</option>`).join('');
  setEquipInputs(store.get('equip', { focal: 24, aperture: 2.8, sensor: 'APS_C', pixel: 3.9, tracking: false }));
  if (!$('sensorIn').value) $('sensorIn').value = 'FULL';
  ['focIn', 'apIn', 'pixIn', 'sensorIn', 'trackIn'].forEach(id => $(id).addEventListener('input', renderEquipment));
  ['focusDist', 'tlInt', 'tlDur', 'tlFps', 'tlMb', 'mosCov', 'mosOv', 'stFrames', 'stExp'].forEach(id => $(id).addEventListener('input', renderTools));
  renderProfiles();
  $('profileSave').onclick = () => { const name = $('profileName').value.trim(); if (!name) return;
    const list = renderProfiles().filter(p => p.name !== name);
    list.push(new Pro.EquipmentProfile({ id: 'p' + Date.now(), name: name.slice(0, 80), equipment: equipment() }));
    try { Pro.EquipmentProfiles.save(list); } catch (e) { alert(e.message); } renderProfiles(); };
  $('profileLoad').onclick = () => { const p = renderProfiles().find(x => x.id === $('profileSel').value); if (p) { setEquipInputs(p.equipment); renderEquipment(); } };
  $('satRefresh').onclick = async () => { await refreshTle(true); renderSats(); };
  $('satIss').onchange = $('satMin').onchange = renderSats;
  $('satList').onclick = e => { const li = e.target.closest('li[data-pass]'); if (!li) return; const p = passes()[+li.dataset.pass];
    skyTime = p.peak.time; skyTarget = null; document.querySelector('.tabs button[data-tab="cielo"]').click();
    $('skyInfo').textContent = `${p.tle.name}: máximo ${Math.round(p.peak.alt)}° ${compass(p.peak.az)} a las ${fmt(p.peak.time)}`; };
  $('moonCal').closest('details').addEventListener('toggle', renderMoonCal);
  $('ssStart').onclick = startTimer; $('ssStop').onclick = () => stopTimer(true);
  $('lbAdd').onclick = () => { try { book.append(new Pro.LogEntry({ time: Date.now(), target: $('lbTarget').value.trim() || '—', equipment: $('lbEquip').value.trim(),
      frames: Math.round(num('lbFrames')), exposure: num('lbExp'), notes: $('lbNotes').value.trim() })); $('lbNotes').value = ''; renderBook(); }
    catch (e) { alert('Revisa tomas y exposición. ' + e.message); } };
  $('lbExport').onclick = () => { const blob = new Blob([book.csv()], { type: 'text/csv' }), a = document.createElement('a');
    a.href = URL.createObjectURL(blob); a.download = 'bitacora-astrotab.csv'; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 5000); };
  $('alAdd').onclick = async () => { const kind = $('alKind').value, time = kind === 'KP' ? null : nextAlarmTime(kind);
    if (kind !== 'KP' && !time) { alert('No hay un próximo evento de ese tipo (¿faltan las órbitas para la ISS?).'); return; }
    try { alarms.add(kind, ALARM_LABEL[kind], time, true, null); } catch (e) { alert(e.message); }
    if ('Notification' in window && Notification.permission === 'default') { try { await Notification.requestPermission(); } catch { /* opcional */ } }
    renderAlarms(); };
  $('alList').onclick = e => { const b = e.target.closest('button[data-del]'); if (b) { alarms.remove(+b.dataset.del); renderAlarms(); } };
  $('torchBtn').onclick = () => { $('torch').hidden = false; $('torch').style.opacity = $('torchLevel').value / 100; };
  $('torchLevel').oninput = () => { $('torch').style.opacity = Math.max(0.1, $('torchLevel').value / 100); };
  $('torchOff').onclick = () => { $('torch').hidden = true; };
  $('objList').addEventListener('click', e => { const li = e.target.closest('li[data-id]'); if (li) openObject(li.dataset.id); });
  $('objClose').onclick = () => { $('objCard').hidden = true; };
  $('objAdd').onclick = () => { if (!currentObj) return; $('planList').closest('details').open = true; const minutes = Math.max(1, Math.min(720, Math.round(num('objMinutes')) || 60));
    sessionReq = sessionReq.filter(r => r.id !== currentObj.id).concat([{ id: currentObj.id, integrationMinutes: minutes }]); store.set('sessionReq', sessionReq); renderPlan(); };
  $('planMake').onclick = makePlan; $('planClear').onclick = () => { sessionReq = []; store.set('sessionReq', sessionReq); renderPlan(); $('planOut').innerHTML = ''; };
  $('skyFind').onclick = () => { if (!stars) return; const t = findTarget($('skySearch').value);
    if (!t) { $('skyInfo').textContent = 'No lo encuentro. Prueba con «Vega», «M42» o «Júpiter».'; return; }
    skyTarget = t; const h = horizonAt(t.ra, t.dec, skyTime);
    $('skyInfo').textContent = `${t.label}: ${h.alt > 0 ? Math.round(h.alt) + '° de altura hacia el ' + compass(h.az) : 'bajo el horizonte ahora'}`; renderSky(); };
  $('skySearch').addEventListener('keydown', e => { if (e.key === 'Enter') $('skyFind').click(); });
  $('showFrame').onchange = $('showSats').onchange = renderSky;
  $('compassBtn').onclick = toggleCompass;
  setInterval(() => { if (place) checkAlarms(); }, 30000);
})();
(async function startExtras() {
  // Espera a que app.js cargue los catálogos.
  for (let i = 0; i < 100 && !messier.length; i++) await new Promise(r => setTimeout(r, 100));
  $('skyNames').innerHTML = (stars ? stars.names : []).concat(messier.map(m => m.id + (m.name ? '' : '')), messier.filter(m => m.name).map(m => m.name), Astro.PLANETS, ['Luna'])
    .map(n => `<option value="${esc(n)}">`).join('');
  try { eclipseRows = Pro.EventCalendar.eclipses(await (await fetch('data/eclipses.tsv')).text()); } catch { eclipseRows = []; }
  renderEquipment(); renderExtras();
  await Promise.all([refreshTle(false), refreshKp()]); passCache = null; renderExtras();
  setInterval(async () => { await refreshKp(); renderAurora(); }, 30 * 60000);
})();
