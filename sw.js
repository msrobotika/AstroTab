// Funciona sin conexión: la app y los catálogos se guardan; la previsión de nubes va siempre a la red.
const CACHE = 'astrotab-web-v3';
const FILES = ['./', 'index.html', 'style.css', 'astro.js', 'pro.js', 'sats.js', 'app.js', 'sky.js', 'features.js', 'vendor/satellite.min.js',
  'manifest.webmanifest', 'data/stars.bin', 'data/milkyway.bin', 'data/messier.tsv', 'data/eclipses.tsv',
  'icons/icon-180.png', 'icons/icon-192.png', 'icons/icon-512.png'];
self.addEventListener('install', e => e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting())));
self.addEventListener('activate', e => e.waitUntil(caches.keys()
  .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())));
self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  if (url.origin !== location.origin || e.request.method !== 'GET') return;  // Open-Meteo: red directa
  // Red primero (las actualizaciones llegan solas); sin conexión, la copia guardada.
  e.respondWith(fetch(e.request).then(r => {
    if (r.ok) { const copy = r.clone(); caches.open(CACHE).then(c => c.put(e.request, copy)); }
    return r;
  }).catch(() => caches.match(e.request, { ignoreSearch: true })));
});
