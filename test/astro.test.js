// node web/test/astro.test.js — comprueba el motor portado contra efemérides conocidas (tolerancias de minutos).
const assert = require('node:assert/strict');
const Astro = require('../astro.js');
const madrid = { lat: 40.4168, lon: -3.7038 };
const at = s => Date.parse(s);
// Puesta de Sol en Madrid el 10/11/2026 hacia las 17:05 UTC (18:05 hora local).
const set = Astro.nextCross(at('2026-11-10T12:00:00Z'), 24, -0.833, false, t => Astro.sunAlt(t, madrid));
assert.ok(Math.abs(set - at('2026-11-10T17:05:00Z')) < 6 * 60000, 'puesta ' + new Date(set).toISOString());
// Luna nueva 9/11/2026 y luna llena 24/11/2026.
assert.ok(Astro.moonPhase(Astro.dayNumber(at('2026-11-09T07:00:00Z'))).illumination < 0.01);
assert.ok(Astro.moonPhase(Astro.dayNumber(at('2026-11-24T15:00:00Z'))).illumination > 0.99);
assert.equal(Astro.phaseName(Astro.moonPhase(Astro.dayNumber(at('2026-11-24T15:00:00Z'))).age), 'Luna llena');
// Polaris (RA 37.95°, Dec 89.26°) siempre a la altura de la latitud, ±1°.
for (const h of [0, 6, 12, 18]) {
  const t = at('2026-11-10T00:00:00Z') + h * 3600000;
  assert.ok(Math.abs(Astro.toHorizon(37.95, 89.26, Astro.lst(t, madrid.lon), madrid.lat).alt - madrid.lat) < 1);
}
// Venus nunca más brillante que −4,9 ni Saturno más débil que +1,5.
const d = Astro.dayNumber(at('2026-11-10T20:00:00Z'));
assert.ok(Astro.planet(1, d).mag > -4.9 && Astro.planet(4, d).mag < 1.5);
console.log('OK: puesta de Sol, fases lunares, Polar y magnitudes de planetas');
