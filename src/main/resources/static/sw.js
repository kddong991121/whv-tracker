// 워홀 88 서비스 워커: "홈 화면에 추가"를 가능하게 하고, 인터넷이 끊기면 안내 화면을 보여준다.
// 개인 근무 기록 화면은 저장(캐시)하지 않는다. 같은 휴대폰을 다른 사람이 써도 기록이 남지 않게 하려는 것이다.
const CACHE = 'whv88-v1';
const STATIC = ['/offline.html', '/icons/icon-192.png', '/manifest.webmanifest'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(STATIC)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', e => {
  if (e.request.mode === 'navigate') {
    e.respondWith(fetch(e.request).catch(() => caches.match('/offline.html')));
  }
});
