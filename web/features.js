// AstroTab web: Herramientas y Más (satélites, auroras, eventos, Luna, plan, bitácora, alarmas, linterna) y ficha de objeto.
// Los cálculos vienen de pro.js (lógica de AstroTab Android traducida).
'use strict';
const num = id => parseFloat(String($(id).value).replace(',', '.'));
let eclipseRows = [];

// ---------- Equipo ----------
function equipment() {
  try { return new Pro.Equipment({ focal: num('focIn'), aperture: num('apIn'), sensor: $('sensorIn').value || 'FULL', pixel: num('pixIn'), tracking: $('trackIn').checked }); }
  catch { return new Pro.Equipment({}); }
}
function renderEquipment() {
  let e; try { e = new Pro.Equipment({ focal: num('focIn'), aperture: num('apIn'), sensor: $('sensorIn').value || 'FULL', pixel: num('pixIn'), tracking: $('trackIn').checked }); }
  catch { $('expOut').innerHTML = '<div>Revisa focal, diafragma y píxel.</div>'; return; }
  const fw = Pro.Optics.field(e.width, e.focal), fh = Pro.Optics.field(e.height, e.focal);
  $('expOut').innerHTML = `<div><b>${Pro.Optics.rule500(e).toFixed(1)} s</b><span>Regla 500</span></div><div><b>${Pro.Optics.npf(e, 0).toFixed(1)} s</b><span>NPF en el ecuador celeste</span></div>` +
    `<div><b>${fw.toFixed(1)}° × ${fh.toFixed(1)}°</b><span>Campo</span></div><div><b>${Pro.Optics.scale(e).toFixed(1)}″</b><span>Por píxel</span></div>`;
  store.set('equip', { focal: e.focal, aperture: e.aperture, sensor: e.sensor.name, pixel: e.pixel, tracking: e.tracking });
  renderTools(); if (typeof renderSky === 'function' && skyOpt.frame) renderSky();
}
function setEquipInputs(q) { $('focIn').value = q.focal; $('apIn').value = q.aperture; $('sensorIn').value = (q.sensor && q.sensor.name) || q.sensor || 'FULL'; $('pixIn').value = q.pixel; $('trackIn').checked = !!q.tracking; }
function renderProfiles() {
  let list = []; try { list = Pro.EquipmentProfiles.read(); } catch { /* sin perfiles */ }
  $('profileSel').innerHTML = list.length ? list.map(p => `<option value="${esc(p.id)}">${esc(p.name)}</option>`).join('') : '<option value="">Sin perfiles</option>';
  return list;
}

// ---------- Herramientas ----------
function renderTools() {
  if (!place) return;
  const e = equipment(), pol = Pro.AstroPhoto.polar(Date.now(), place);
  const c = $('polarClock'), dpr = devicePixelRatio || 1; c.width = 150 * dpr; c.height = 150 * dpr; c.style.width = c.style.height = '150px';
  const g = c.getContext('2d'); g.setTransform(dpr, 0, 0, dpr, 0, 0); const r = 62;
  g.strokeStyle = '#7fb8ff'; g.lineWidth = 1.5; g.beginPath(); g.arc(75, 75, r, 0, 7); g.stroke(); g.beginPath(); g.arc(75, 75, r * 0.55, 0, 7); g.stroke();
  g.beginPath(); g.moveTo(75, 8); g.lineTo(75, 142); g.moveTo(8, 75); g.lineTo(142, 75); g.strokeStyle = 'rgba(127,184,255,.3)'; g.stroke();
  g.fillStyle = '#93a4bd'; g.font = '11px system-ui'; g.textAlign = 'center'; g.textBaseline = 'middle';
  for (const h of [12, 3, 6, 9]) { const a = (h % 12) / 12 * 2 * Math.PI; g.fillText(h, 75 + (r + 0) * Math.sin(a) * 0.82, 75 - r * Math.cos(a) * 0.82); }
  const a = pol.clockAngle * Math.PI / 180; g.fillStyle = '#ffb020'; g.beginPath(); g.arc(75 + r * Math.sin(a), 75 - r * Math.cos(a), 6, 0, 7); g.fill();
  const hh = pol.clockAngle / 30, hours = Math.floor(hh), mins = Math.round((hh - hours) * 60) % 60;
  $('polarOut').innerHTML = `<b class="big">${hours === 0 ? 12 : hours}:${String(mins).padStart(2, '0')}</b><br>${esc(pol.star)} en el buscador polar.<br><span class="tag">A ${pol.radius.toFixed(2)}° del polo · ángulo horario ${pol.hourAngle.toFixed(1)}°</span>`;
  const gal = Pro.AstroPhoto.galactic(tonight(), place);
  $('mwOut').innerHTML = gal.highest ? `Núcleo más alto a las <b>${fmt(gal.highest)}</b> (${Math.round(gal.altitude)}° ${compass(gal.azimuth)}).` : 'El núcleo no se ve esta noche con noche cerrada. Horas al mes:';
  if (!renderTools.months || renderTools.monthsKey !== placeKey()) { renderTools.months = Pro.AstroPhoto.galacticMonths(new Date().getFullYear(), place); renderTools.monthsKey = placeKey(); }
  const byMonth = [...renderTools.months].sort((x, y) => x[0] - y[0]), maxH = Math.max(1, ...byMonth.map(x => x[1]));
  $('mwMonths').innerHTML = byMonth.map(([m, h]) => `<div style="height:${Math.max(2, h / maxH * 100)}%" title="${h.toFixed(1)} h"><span>${'EFMAMJJASOND'[m - 1]}</span></div>`).join('');
  try { const f = Pro.ProTools.focus(e, num('focusDist')); $('focusOut').innerHTML = `Hiperfocal <b>${f.hyperfocalMetres.toFixed(1)} m</b> · nítido de ${f.nearMetres.toFixed(1)} m a ${f.farMetres == null ? '∞' : f.farMetres.toFixed(1) + ' m'}`; }
  catch { $('focusOut').textContent = 'La distancia debe ser mayor que la focal.'; }
  try { const t = Pro.ProTools.timelapse(num('tlInt'), num('tlDur') * 60, num('tlFps'), num('tlMb'));
    $('tlOut').innerHTML = `<b>${t.frames}</b> fotos · vídeo de ${t.clipSeconds.toFixed(1)} s · ${(t.megabytes / 1024).toFixed(1)} GB · el cielo gira ${Pro.ProTools.arc(num('tlInt')).toFixed(2)}° entre fotos`; }
  catch { $('tlOut').textContent = 'Revisa los datos.'; }
  try { const fw = Pro.Optics.field(e.width, e.focal), fh = Pro.Optics.field(e.height, e.focal), nx = Pro.ProTools.mosaicAxis(num('mosCov'), fw, num('mosOv')), ny = Pro.ProTools.mosaicAxis(num('mosCov'), fh, num('mosOv'));
    $('mosOut').innerHTML = `<b>${nx} × ${ny}</b> = ${nx * ny} fotos`; } catch { $('mosOut').textContent = 'Solape entre 0 y 95 %.'; }
  try { const s = Pro.ProTools.stack(Math.round(num('stFrames')), num('stExp'));
    $('stOut').innerHTML = `Señal/ruido <b>×${s.snrGain.toFixed(1)}</b> · ${(s.integrationSeconds / 60).toFixed(0)} min · darks ${s.darks}, flats ${s.flats}, bias ${s.bias}`; } catch { $('stOut').textContent = 'Revisa tomas y exposición.'; }
}

// ---------- Temporizador ----------
let timer = null, timerTick = null, lastPhase = '', wakeLock = null, audio = null;
function beep(freq = 880, ms = 180) {
  try { audio = audio || new (window.AudioContext || window.webkitAudioContext)(); const o = audio.createOscillator(), gg = audio.createGain();
    o.frequency.value = freq; o.connect(gg); gg.connect(audio.destination); gg.gain.value = 0.2; o.start(); o.stop(audio.currentTime + ms / 1000); } catch { /* sin audio */ }
  if (navigator.vibrate) navigator.vibrate(ms);
}
const mmss = ms => { const s = Math.ceil(ms / 1000); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; };
async function startTimer() {
  try { const plan = new Pro.SessionPlan({ lights: Math.round(num('ssLights')), exposureSeconds: num('ssExp'), pauseSeconds: num('ssPause'), darks: Math.round(num('ssDarks')) });
    timer = new Pro.SessionTimer(plan, () => performance.now()); lastPhase = ''; beep(1200);
    try { wakeLock = await navigator.wakeLock?.request('screen'); } catch { /* puede negarse */ }
    clearInterval(timerTick); timerTick = setInterval(tickTimer, 250); tickTimer(); } catch { $('ssShow').textContent = 'Revisa los datos.'; }
}
function tickTimer() {
  if (!timer) return; const s = timer.state(), ph = s.done ? 'fin' : s.index + (s.exposing ? 'e' : 'p');
  if (ph !== lastPhase) { if (lastPhase) beep(s.exposing ? 1200 : 660); lastPhase = ph; }
  $('ssShow').innerHTML = s.done ? 'Sesión terminada' : `${esc(timer.plan.kind(s.index))} ${s.index + 1}/${timer.plan.count}<br>${s.exposing ? 'Exponiendo' : 'Pausa'} <b>${mmss(s.remainingMillis)}</b><br><span class="tag">Quedan ${mmss(s.totalRemainingMillis)}</span>`;
  if (s.done) stopTimer(false);
}
function stopTimer(manual = true) { if (timer && manual) timer.stop(); clearInterval(timerTick); timer = null; try { wakeLock?.release(); } catch { /* */ } wakeLock = null; if (manual) $('ssShow').textContent = 'Parado'; }

// ---------- Satélites ----------
let tleData = store.get('tle', null), passCache = null, satMin = 10, satIss = false;
async function refreshTle(force) {
  if (!useNet || !navigator.onLine || (!force && tleData && Date.now() - tleData.fetched < 12 * HOUR)) return;
  $('satStatus').textContent = 'Descargando órbitas de CelesTrak…';
  try { tleData = await Sats.fetchTle(); store.set('tle', tleData); passCache = null; } catch (e) { $('satStatus').textContent = 'No se pudieron descargar: ' + e.message; }
}
function passes() {
  if (!tleData || !place) return [];
  const key = placeKey() + satMin + satIss + tleData.fetched;
  if (!passCache || passCache.key !== key || Date.now() - passCache.at > 5 * 60000)
    passCache = { key, at: Date.now(), list: Sats.passes(tleData.tle, place, Date.now() - 10 * 60000, 48, satMin, satIss) };
  return passCache.list;
}
function skySatTracks(t) {  // Para sky.js: pasos que ocurren cerca de la hora mostrada.
  return passes().filter(p => t >= p.track[0].time - 2 * 60000 && t <= p.track[p.track.length - 1].time + 2 * 60000)
    .map(p => ({ name: p.tle.name, pts: p.track, now: p.track.reduce((a, b) => Math.abs(b.time - t) < Math.abs(a.time - t) ? b : a) }));
}
function polarPlot(c, track) {
  const dpr = devicePixelRatio || 1; c.width = 96 * dpr; c.height = 96 * dpr; const g = c.getContext('2d'); g.setTransform(dpr, 0, 0, dpr, 0, 0);
  const r = 40, P = (alt, az) => { const rr = r * (90 - alt) / 90; return [48 + rr * Math.sin(az * Math.PI / 180), 48 - rr * Math.cos(az * Math.PI / 180)]; };
  g.strokeStyle = 'rgba(147,164,189,.6)'; g.lineWidth = 1; for (const k of [1, 0.66, 0.33]) { g.beginPath(); g.arc(48, 48, r * k, 0, 7); g.stroke(); }
  g.fillStyle = '#93a4bd'; g.font = '9px system-ui'; g.textAlign = 'center'; g.fillText('N', 48, 6); g.fillText('S', 48, 95); g.fillText('E', 93, 51); g.fillText('O', 3, 51);
  g.strokeStyle = '#7fb8ff'; g.lineWidth = 2; g.beginPath(); track.forEach((p, i) => { const q = P(p.alt, p.az); i ? g.lineTo(q[0], q[1]) : g.moveTo(q[0], q[1]); }); g.stroke();
  const e = P(track[track.length - 1].alt, track[track.length - 1].az); g.fillStyle = '#7fb8ff'; g.beginPath(); g.arc(e[0], e[1], 3, 0, 7); g.fill();
}
function renderSats() {
  if (!place) return;
  if (!tleData) { $('satStatus').textContent = useNet ? 'Pulsa «Actualizar TLE».' : 'Activa internet en Ajustes.'; $('satList').innerHTML = ''; return; }
  const list = passes(), age = (Date.now() - tleData.fetched) / HOUR;
  $('satStatus').textContent = `TLE ${fmtDM(tleData.fetched)} ${fmt(tleData.fetched)} · ${tleData.tle.length} satélites · próximas 48 h${age > 72 ? ' · órbitas antiguas' : ''}`;
  $('satList').innerHTML = list.slice(0, 30).map((p, i) => { const lv = p.peak.alt >= 50 ? 'Alto' : p.peak.alt >= 25 ? 'Medio' : 'Bajo';
    return `<div class="pass" data-pass="${i}"><svg class="ic big"><use href="#i-sat"/></svg><div><div>${esc(p.tle.name)}</div>` +
      `<div class="row2"><span class="ptime">${new Date(p.start.time).toLocaleTimeString('es-ES')}</span><span class="tag">${fmtDay(p.start.time)}</span><span class="badge">${lv} · ${Math.round(p.peak.alt)}°</span></div>` +
      `<div class="tag">Inicio ${compass(p.start.az)} → Máx ${fmt(p.peak.time)} ${compass(p.peak.az)} → Fin ${fmt(p.end.time)} ${compass(p.end.az)}</div></div><canvas data-plot="${i}"></canvas></div>`; }).join('') ||
    '<div class="card muted">Ningún paso visible con ese filtro.</div>';
  $('satList').querySelectorAll('canvas[data-plot]').forEach(c => polarPlot(c, list[+c.dataset.plot].track));
}

// ---------- Auroras ----------
let kpData = store.get('kp', null);
async function refreshKp() {
  if (!useNet || !navigator.onLine || (kpData && Date.now() - kpData.fetched < 30 * 60000)) return;
  try { const get = u => fetch(u).then(r => { if (!r.ok) throw Error('NOAA HTTP ' + r.status); return r.json(); });
    const [obs, fc] = await Promise.all([get('https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json'), get('https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json')]);
    const rows = a => a.filter(r => !Array.isArray(r) || r[0] !== 'time_tag').map(r => Array.isArray(r) ? { time_tag: r[0], Kp: r[1], observed: r[2] } : r), s = [];
    for (const r of rows(obs)) s.push({ time: Date.parse(r.time_tag + 'Z'), value: +(r.Kp ?? r.kp), kind: 'OBSERVED', duration: 3 * HOUR });
    for (const r of rows(fc)) { const t = Date.parse(r.time_tag + 'Z'); if (s.some(x => x.time === t)) continue;
      s.push({ time: t, value: +(r.kp ?? r.Kp), kind: r.observed === 'observed' ? 'OBSERVED' : r.observed === 'estimated' ? 'ESTIMATED' : 'PREDICTED', duration: 3 * HOUR }); }
    kpData = { fetched: Date.now(), samples: s.filter(x => isFinite(x.time) && isFinite(x.value)).sort((a, b) => a.time - b.time) }; store.set('kp', kpData);
  } catch (e) { $('auroraBox').innerHTML = `<p class="muted">Sin datos de NOAA: ${esc(e.message)}</p>`; }
}
const kpNow = () => kpData ? [...kpData.samples].reverse().find(s => s.kind !== 'PREDICTED' && s.time <= Date.now()) : null;
function renderAurora() {
  if (!place) return; const need = Pro.Aurora.threshold(place.lat, place.lon), cur = kpNow();
  $('auroraBox').innerHTML = `<div class="kpis"><div><b>${cur ? cur.value.toFixed(1) : '—'}</b><span>Kp ahora</span></div><div><b>${need > 9 ? '> 9' : need.toFixed(1)}</b><span>Kp necesario aquí</span></div>` +
    `<div><b>${Math.round(Astro.geomagLat(place.lat, place.lon))}°</b><span>Latitud geomagnética</span></div></div>` +
    (kpData && Pro.Aurora.currentAlert(kpData.samples, Date.now(), place) ? '<p class="good">¡Kp suficiente ahora! Mira al norte con el cielo oscuro.</p>' : '');
  if (!kpData) return;
  const data = kpData.samples.filter(s => s.time > Date.now() - 48 * HOUR && s.time < Date.now() + 72 * HOUR), { g, W, H } = canvas2d($('kpChart'), 140);
  if (!data.length) return; const t0 = data[0].time, t1 = data[data.length - 1].time + 3 * HOUR, x = t => (t - t0) / (t1 - t0) * (W - 24) + 22, y = v => H - 18 - v / 9 * (H - 26);
  g.fillStyle = '#93a4bd'; g.font = '11px system-ui'; g.strokeStyle = '#22304a';
  for (const v of [0, 3, 5, 7, 9]) { g.beginPath(); g.moveTo(22, y(v)); g.lineTo(W, y(v)); g.stroke(); g.fillText(v, 4, y(v) + 4); }
  for (const s of data) { g.fillStyle = s.value >= need ? '#6fd3a0' : s.value >= 5 ? '#ffb020' : s.kind === 'PREDICTED' ? 'rgba(127,184,255,.45)' : '#7fb8ff';
    g.fillRect(x(s.time) + 1, y(s.value), x(s.time + 3 * HOUR) - x(s.time) - 2, H - 18 - y(s.value)); }
  const xn = x(Date.now()); g.fillStyle = '#ff7a7a'; g.fillRect(xn, 6, 1.5, H - 24); g.fillStyle = '#93a4bd'; g.fillText('ahora', xn + 3, 14);
  if (need <= 9) { g.strokeStyle = '#6fd3a0'; g.setLineDash([4, 3]); g.beginPath(); g.moveTo(22, y(need)); g.lineTo(W, y(need)); g.stroke(); g.setLineDash([]); }
}

// ---------- Eventos y Luna ----------
function renderEvents() {
  if (!place) return; let ev = []; try { ev = Pro.EventCalendar.month(Date.now(), place, eclipseRows); } catch { ev = []; }
  $('eventList').innerHTML = ev.filter(e => e.time > Date.now() - 12 * HOUR).slice(0, 40).map(e => `<li><span>${e.highlighted ? '★ <b>' : ''}${esc(e.title)}${e.highlighted ? '</b>' : ''}<br><span class="tag">${esc(e.summary || e.kind || '')}</span></span>` +
    `<span class="accent nowrap">${fmtDM(e.time)} ${fmt(e.time)}</span></li>`).join('') || '<li class="tag">Sin eventos este mes.</li>';
  $('meteorList').innerHTML = Pro.ProAstro.meteors(Date.now(), place).map(m => `<li><span><b>${esc(m.shower.name)}</b> · THZ ${m.shower.zhr}<br>` +
    `<span class="tag">Radiante a ${Math.round(m.radiantAltitude)}° a medianoche · culmina ${fmt(m.transit)} · Luna ${Math.round(m.illumination * 100)} %</span></span><span class="accent">${fmtDM(m.peak)}</span></li>`).join('');
}
let moonKey = '';
function renderMoonCal() {
  if (!place || !$('moonCal').closest('.panel').classList.contains('on')) return;
  const key = placeKey() + new Date().toDateString(); if (moonKey === key) return; moonKey = key;
  const days = Pro.ProAstro.moonMonth(Date.now(), place), pad = (new Date(days[0].time).getDay() + 6) % 7, today = new Date().getDate();
  $('moonCal').innerHTML = ['L', 'M', 'X', 'J', 'V', 'S', 'D'].map(d => `<span class="tag">${d}</span>`).join('') + '<span></span>'.repeat(pad) +
    days.map(d => `<div${d.day === today ? ' style="outline:2px solid var(--accent)"' : ''}><b>${d.day}</b><canvas data-age="${d.age}" data-ill="${d.illumination}"></canvas>${Math.round(d.illumination * 100)} %<br><span class="tag">${d.moonlessHours.toFixed(1)} h</span></div>`).join('');
  $('moonCal').querySelectorAll('canvas').forEach(c => { const dpr = devicePixelRatio || 1; c.width = 26 * dpr; c.height = 26 * dpr; const g = c.getContext('2d'); g.setTransform(dpr, 0, 0, dpr, 0, 0);
    moonGlyph(g, 13, 13, 11, { age: +c.dataset.age, illumination: +c.dataset.ill }); });
}

// ---------- Ficha de objeto y plan ----------
let sessionReq = store.get('sessionReq', []), currentObj = null;
function openObject(id) {
  const o = messier.find(m => m.id === id); if (!o || !place) return; currentObj = o;
  const night = tonight(), plan = Pro.AstroPhoto.plan(o, night, place, objMin), fit = Pro.Optics.fit(equipment(), o);
  $('odTitle').textContent = o.id + (o.name ? ' · ' + o.name : '');
  $('odFacts').innerHTML = `<dt>Tipo</dt><dd>${esc(TYPE[o.type] || '')}</dd><dt>Catálogo</dt><dd>${esc(o.ngc || '—')}</dd><dt>Magnitud</dt><dd>${o.mag ?? '—'}</dd><dt>Tamaño</dt><dd>${esc(o.size || '—')}′</dd>` +
    `<dt>Horas útiles (≥${objMin}°, noche cerrada)</dt><dd>${plan.usefulHours.toFixed(1)} h</dd><dt>Culmina</dt><dd>${fmt(plan.transit)} a ${Math.round(plan.transitAltitude)}°</dd>` +
    `<dt>Ventanas</dt><dd>${plan.windows.map(w => fmt(w.start) + '–' + fmt(w.end)).join(', ') || '—'}</dd><dt>Nota para foto</dt><dd>${plan.score}/100 (altura ${Math.round(plan.altitudeFactor * 100)} % · Luna ${Math.round(plan.moonFactor * 100)} %)</dd>` +
    `<dt>Con tu equipo</dt><dd>${fit ? (fit.fits ? 'Cabe' : 'No cabe') + ` · ocupa ${Math.round(Math.max(fit.widthPercent, fit.heightPercent))} % del encuadre` : '—'}</dd>`;
  const dlg = $('objDlg'); if (!dlg.open) dlg.showModal();
  requestAnimationFrame(() => { const { g, W, H } = canvas2d($('odChart'), 150), n = plan.altitudes.length, x = i => i / (n - 1) * W, y = a => H - 18 - Math.max(0, a) / 90 * (H - 26);
    night.samples.forEach((s, i) => { if (s.sun < -18) { g.fillStyle = '#16213a'; g.fillRect(x(i), 0, W / n + 1, H - 16); } });
    g.strokeStyle = '#22304a'; g.fillStyle = '#93a4bd'; g.font = '11px system-ui';
    for (const a of [0, 30, 60, 90]) { g.beginPath(); g.moveTo(0, y(a)); g.lineTo(W, y(a)); g.stroke(); g.fillText(a + '°', 3, y(a) - 3); }
    g.strokeStyle = '#e8e2c8'; g.lineWidth = 1; g.beginPath(); night.samples.forEach((s, i) => i ? g.lineTo(x(i), y(s.moon)) : g.moveTo(x(i), y(s.moon))); g.stroke();
    g.strokeStyle = '#6fd3a0'; g.lineWidth = 2.5; g.beginPath(); plan.altitudes.forEach((a, i) => i ? g.lineTo(x(i), y(a)) : g.moveTo(x(i), y(a))); g.stroke();
    for (let t = Math.ceil(night.start / (3 * HOUR)) * 3 * HOUR; t < night.end; t += 3 * HOUR) g.fillText(fmt(t), (t - night.start) / (night.end - night.start) * W - 14, H - 3); });
}
function renderPlan() {
  $('planList').innerHTML = sessionReq.map(r => `<li><span>${r.id}${NAMES[r.id] ? ' · ' + NAMES[r.id] : ''}</span><span class="tag">${r.integrationMinutes} min <button class="pill" data-rm="${r.id}">Quitar</button></span></li>`).join('') ||
    '<li class="tag">Añade objetos desde su ficha (Objetos → toca uno → «Añadir al plan»).</li>';
}
function makePlan() {
  if (!sessionReq.length || !place) return;
  const night = tonight(), plans = sessionReq.map(r => Pro.AstroPhoto.plan(messier.find(m => m.id === r.id), night, place, objMin));
  let out; try { out = Pro.SessionPlanner.propose(sessionReq, plans, num('planExp'), num('planPause'), equipment()); } catch (e) { $('planOut').innerHTML = `<li class="bad">${esc(e.message)}</li>`; return; }
  const dark = night.dark.reduce((h, s) => h + s.hours, 0), used = out.blocks.reduce((h, b) => h + (b.end - b.start) / HOUR, 0);
  $('planHead').textContent = `Noche ${fmtDM(night.start)} → ${fmtDM(night.end)} · ${Math.round(used / Math.max(0.1, dark) * 100)} % de la oscuridad ocupada`;
  const { g, W, H } = canvas2d($('planBar'), 90), d0 = night.dark.length ? night.dark[0].start : night.start, d1 = night.dark.length ? night.dark[night.dark.length - 1].end : night.end;
  const x = t => (t - d0) / (d1 - d0) * W, cols = ['#7fb8ff', '#ffb020', '#6fd3a0', '#b39dff', '#ff7a7a'];
  g.fillStyle = '#93a4bd'; g.font = '11px system-ui'; g.fillText(fmt(d0), 2, 12); g.textAlign = 'right'; g.fillText(fmt(d1), W - 2, 12); g.textAlign = 'start';
  out.blocks.forEach((b, i) => { g.fillStyle = cols[i % cols.length]; g.fillRect(x(b.start), 30, Math.max(2, x(b.end) - x(b.start) - 2), 44);
    g.fillStyle = '#04101f'; g.font = '600 12px system-ui'; if (x(b.end) - x(b.start) > 34) g.fillText(b.id, x(b.start) + 6, 56); });
  $('planOut').innerHTML = out.blocks.map(b => `<li><span><b>${b.id}</b> · ${b.frames} × ${b.exposure.toFixed(0)} s</span><span class="accent">${fmt(b.start)}–${fmt(b.end)}</span></li>`).join('') +
    (out.unscheduled.length ? `<li class="bad">Sin hueco esta noche: ${out.unscheduled.join(', ')}</li>` : '');
}

// ---------- Bitácora y alarmas ----------
const book = new Pro.LogBook(), alarms = new Pro.AlarmStore();
const ALARM_LABEL = { ASTRONOMICAL_DUSK: 'Noche cerrada', MOONRISE: 'Salida de la Luna', ISS: 'Paso de la ISS', KP: 'Kp suficiente para auroras' };
function renderBook() {
  let rows = []; try { rows = book.read(); } catch { $('lbList').innerHTML = '<li class="bad">Bitácora dañada: expórtala y empieza otra.</li>'; return; }
  $('lbList').innerHTML = rows.slice(-30).reverse().map(r => `<li><span><b>${esc(r.target)}</b> · ${r.frames} × ${r.exposure} s · ${Math.round(r.frames * r.exposure / 60)} min<br><span class="tag">${esc(r.equipment)} ${esc(r.notes)}</span></span><span class="tag">${fmtDM(r.time)}</span></li>`).join('') || '<li class="tag">Aún no hay sesiones.</li>';
}
function nextAlarmTime(kind) {
  const now = Date.now(), ev = Pro.AstroPhoto.events(tonight(), place).concat(Pro.AstroPhoto.events(Pro.AstroPhoto.timeline(now + 24 * HOUR, place), place));
  return Pro.AlarmPolicy.next(kind, now, ev, [], passes());
}
function renderAlarms() {
  let list = []; try { list = alarms.read(); } catch { /* */ }
  $('alList').innerHTML = list.map(a => `<li><span>${esc(a.label)}<br><span class="tag">${a.time ? fmtDay(a.time) + ' ' + fmt(a.time) : 'cuando ocurra'}</span></span><button class="pill" data-del="${a.id}">Quitar</button></li>`).join('') || '<li class="tag">Sin alarmas.</li>';
}
function checkAlarms() {
  let list = []; try { list = alarms.read(); } catch { return; }
  const now = Date.now(), kp = kpData ? Pro.AlarmPolicy.kpReached(kpData.samples, now, Pro.Aurora.threshold(place.lat, place.lon)) : false;
  for (const a of list) { if (!Pro.AlarmPolicy.due(a, now, kp)) continue;
    const shown = store.get('alarmShown', {}); if (shown[a.id] && now - shown[a.id] < 3 * HOUR) continue; shown[a.id] = now; store.set('alarmShown', shown);
    beep(988, 400); setTimeout(() => beep(1318, 400), 500);
    if ('Notification' in window && Notification.permission === 'granted') new Notification('AstroTab', { body: a.label });
    alert('⏰ ' + a.label); }
}

// ---------- Inicio: ISS y Kp ----------
function renderHomeExtras() {
  const iss = tleData ? Sats.passes(tleData.tle, place, Date.now(), 48, 10, true)[0] : null;
  $('issNext').textContent = iss ? `${fmtDM(iss.start.time)} ${fmt(iss.start.time)} · ${Math.round(iss.peak.alt)}°` : (useNet ? 'Sin pasos visibles en 48 h' : 'Activa internet');
  const cur = kpNow(), need = Pro.Aurora.threshold(place.lat, place.lon);
  $('tKp').innerHTML = cur ? `${cur.value.toFixed(1)}<small> Kp</small>` : '—'; $('tKpSub').textContent = `Necesario: Kp ${need > 9 ? '> 9' : need.toFixed(1)}`;
}

// ---------- Paneles de Más y arranque ----------
function showPanel(p) {
  document.querySelectorAll('#moreGrid button').forEach(b => b.classList.toggle('on', b.dataset.p === p));
  document.querySelectorAll('.panel').forEach(x => x.classList.toggle('on', x.dataset.panel === p)); store.set('panel', p);
  if (p === 'luna') renderMoonCal(); if (p === 'aur') renderAurora(); if (p === 'sat') renderSats(); if (p === 'plan') { renderPlan(); }
  if (p === 'torch') { $('torch').hidden = false; $('torch').style.opacity = $('torchLevel').value / 100; }
}
function renderExtras() { if (!place || !messier.length) return; renderEquipment(); renderSats(); renderAurora(); renderEvents(); renderMoonCal(); renderBook(); renderAlarms(); renderPlan(); renderHomeExtras(); }
(function wireExtras() {
  $('sensorIn').innerHTML = Object.values(Pro.SensorFormat).map(s => `<option value="${s.name}">${esc(s.label)}</option>`).join('');
  setEquipInputs(store.get('equip', { focal: 24, aperture: 2.8, sensor: 'APS_C', pixel: 3.9, tracking: false }));
  ['focIn', 'apIn', 'pixIn', 'sensorIn', 'trackIn'].forEach(id => $(id).addEventListener('input', renderEquipment));
  ['focusDist', 'tlInt', 'tlDur', 'tlFps', 'tlMb', 'mosCov', 'mosOv', 'stFrames', 'stExp'].forEach(id => $(id).addEventListener('input', renderTools));
  renderProfiles();
  $('profileSave').onclick = () => { const name = $('profileName').value.trim(); if (!name) return; const list = renderProfiles().filter(p => p.name !== name);
    list.push(new Pro.EquipmentProfile({ id: 'p' + Date.now(), name: name.slice(0, 80), equipment: equipment() })); try { Pro.EquipmentProfiles.save(list); } catch (e) { alert(e.message); } renderProfiles(); };
  $('profileLoad').onclick = () => { const p = renderProfiles().find(x => x.id === $('profileSel').value); if (p) { setEquipInputs(p.equipment); renderEquipment(); } };
  $('ssStart').onclick = startTimer; $('ssStop').onclick = () => stopTimer(true);
  $('moreGrid').onclick = e => { const b = e.target.closest('button[data-p]'); if (b) showPanel(b.dataset.p); };
  $('satRefresh').onclick = async () => { await refreshTle(true); renderSats(); };
  $('satIss').onclick = () => { satIss = !satIss; $('satIss').classList.toggle('on', satIss); renderSats(); };
  document.querySelectorAll('[data-min]').forEach(b => b.onclick = () => { satMin = +b.dataset.min; document.querySelectorAll('[data-min]').forEach(x => x.classList.toggle('on', x === b)); renderSats(); });
  $('satList').onclick = e => { const d = e.target.closest('[data-pass]'); if (!d) return; const p = passes()[+d.dataset.pass];
    skyOffset = Math.round((p.peak.time - Date.now()) / 60000); $('skySlider').value = Math.max(-720, Math.min(720, skyOffset));
    view.bearing = p.peak.az; view.elevation = Math.min(80, Math.max(10, p.peak.alt)); showTab('cielo'); };
  $('lbAdd').onclick = () => { try { book.append(new Pro.LogEntry({ time: Date.now(), target: $('lbTarget').value.trim() || '—', equipment: $('lbEquip').value.trim(),
      frames: Math.round(num('lbFrames')), exposure: num('lbExp'), notes: $('lbNotes').value.trim() })); $('lbNotes').value = ''; renderBook(); } catch (e) { alert('Revisa tomas y exposición.'); } };
  $('lbExport').onclick = () => { const blob = new Blob([book.csv()], { type: 'text/csv' }), a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'bitacora-astrotab.csv'; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 5000); };
  $('alAdd').onclick = async () => { const kind = $('alKind').value, time = kind === 'KP' ? null : nextAlarmTime(kind);
    if (kind !== 'KP' && !time) { alert('No hay un próximo evento de ese tipo (para la ISS hacen falta las órbitas).'); return; }
    try { alarms.add(kind, ALARM_LABEL[kind], time, true, null); } catch (e) { alert(e.message); }
    if ('Notification' in window && Notification.permission === 'default') { try { await Notification.requestPermission(); } catch { /* */ } } renderAlarms(); };
  $('alList').onclick = e => { const b = e.target.closest('button[data-del]'); if (b) { alarms.remove(+b.dataset.del); renderAlarms(); } };
  $('torchBtn').onclick = () => { $('torch').hidden = false; $('torch').style.opacity = $('torchLevel').value / 100; };
  $('torchLevel').oninput = () => { $('torch').style.opacity = Math.max(0.1, $('torchLevel').value / 100); }; $('torchOff').onclick = () => { $('torch').hidden = true; };
  $('odClose').onclick = () => $('objDlg').close();
  $('odAdd').onclick = () => { if (!currentObj) return; const m = Math.max(1, Math.min(720, Math.round(num('odMinutes')) || 60));
    sessionReq = sessionReq.filter(r => r.id !== currentObj.id).concat([{ id: currentObj.id, integrationMinutes: m }]); store.set('sessionReq', sessionReq); renderPlan(); $('odAdd').textContent = 'Añadido ✓'; setTimeout(() => $('odAdd').textContent = 'Añadir al plan', 1500); };
  $('odSky').onclick = () => { if (!currentObj) return; $('objDlg').close(); showTab('cielo'); centerOn({ label: currentObj.id + (currentObj.name ? ' · ' + currentObj.name : ''), kind: TYPE[currentObj.type], ra: currentObj.ra, dec: currentObj.dec, mag: currentObj.mag, messier: currentObj }); };
  $('planList').onclick = e => { const b = e.target.closest('button[data-rm]'); if (b) { sessionReq = sessionReq.filter(r => r.id !== b.dataset.rm); store.set('sessionReq', sessionReq); renderPlan(); } };
  $('planMake').onclick = makePlan; $('planClear').onclick = () => { sessionReq = []; store.set('sessionReq', sessionReq); renderPlan(); $('planOut').innerHTML = ''; };
  showPanel(store.get('panel', 'sat') === 'torch' ? 'sat' : store.get('panel', 'sat'));
  setInterval(() => { if (place) checkAlarms(); }, 30000);
})();
(async function startExtras() {
  for (let i = 0; i < 100 && !messier.length; i++) await new Promise(r => setTimeout(r, 100));
  $('skyNames').innerHTML = (stars ? stars.names : []).concat(messier.map(m => m.id), messier.filter(m => m.name).map(m => m.name), Astro.PLANETS, ['Luna']).map(n => `<option value="${esc(n)}">`).join('');
  try { eclipseRows = Pro.EventCalendar.eclipses(await (await fetch('data/eclipses.tsv')).text()); } catch { eclipseRows = []; }
  renderExtras();
  await Promise.all([refreshTle(false), refreshKp()]); passCache = null; renderExtras(); if (typeof renderSky === 'function') renderSky();
  setInterval(async () => { await refreshKp(); renderAurora(); renderHomeExtras(); }, 30 * 60000);
})();
