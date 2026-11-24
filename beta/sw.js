// cloudgaze offline support: keeps a copy of the app so it opens without internet.
// The page itself is fetched fresh when online (so updates show up right away) and falls back to the copy when offline.
const CACHE = 'cloudgaze-v2.0-1';
const CORE = ['./', 'manifest.webmanifest', 'icons/icon-192.png', 'icons/icon-512.png'];
const KEEP = /^https:\/\/(www\.gstatic\.com\/firebasejs|fonts\.googleapis\.com|fonts\.gstatic\.com|sdk\.scdn\.co)\//;

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(CORE)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k.startsWith('cloudgaze-') && k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  const same = url.origin === location.origin && url.pathname.startsWith(new URL(self.registration.scope).pathname);
  if (req.mode === 'navigate' && same) {
    // the app: newest from the network, the saved copy when offline
    e.respondWith(fetch(req).then(r => { const copy = r.clone(); caches.open(CACHE).then(c => c.put('./', copy)); return r; })
      .catch(() => caches.match('./')));
    return;
  }
  if (same || KEEP.test(req.url)) {
    // icons, fonts and libraries: use the saved copy, refresh it in the background
    e.respondWith(caches.open(CACHE).then(async c => {
      const hit = await c.match(req);
      const net = fetch(req).then(r => { if (r.ok || r.type === 'opaque') c.put(req, r.clone()); return r; }).catch(() => hit);
      return hit || net;
    }));
  }
});
