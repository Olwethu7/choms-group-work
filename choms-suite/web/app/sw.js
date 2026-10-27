/* Service worker: caches the app shell so CHOMS opens with no signal. Data caching is handled in js/repo.js. */
const CACHE = 'choms-shell-v3';
const SHELL = ['./', 'index.html', 'css/app.css', '../assets/brand.css', '../assets/logo.svg', '../assets/hero.svg',
  'js/app.js', 'js/utils.js', 'js/repo.js', 'js/ui.js', 'js/entities.js', 'js/firebase.js', 'js/firebase-config.js', 'manifest.json'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => Promise.all(SHELL.map((u) => c.add(u).catch(() => null)))).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});

// Network-first for our own files (updates arrive when online); cache fallback when offline.
// Firebase SDK scripts and Google Fonts are cached on first use so the app also boots offline.
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET') return;
  const ours = url.origin === self.location.origin;
  const sdk = url.hostname === 'www.gstatic.com' && url.pathname.includes('/firebasejs/');
  const fonts = url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com';
  if (!ours && !sdk && !fonts) return; // Firestore / Auth API calls are never intercepted
  e.respondWith(
    fetch(e.request)
      .then((res) => {
        if (res.ok || res.type === 'opaque') { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); }
        return res;
      })
      .catch(() => caches.match(e.request).then((hit) => hit || (e.request.mode === 'navigate' ? caches.match('index.html') : Response.error())))
  );
});
