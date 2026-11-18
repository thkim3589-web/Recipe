// 레시피 찾기 — 오프라인용 서비스워커
// 화면 파일은 "인터넷 먼저, 안 되면 저장본" 방식이라 업데이트가 바로 반영돼요.
const CACHE = 'recipe-v1';
const CORE = ['./recipe-finder.html', './index.html', './manifest.json', './icons/icon-192.png', './icons/icon-512.png'];
self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(CORE)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const req = e.request;
  if(req.method !== 'GET') return;
  const url = new URL(req.url);
  // 유튜브·AI API 호출은 항상 인터넷으로 (저장하지 않음)
  if(/googleapis\.com|anthropic\.com|raw\.githubusercontent\.com/.test(url.host)) return;
  const isFont = /fonts\.(googleapis|gstatic)\.com/.test(url.host);
  const isThumb = /ytimg\.com/.test(url.host);
  if(url.origin === location.origin){
    // 인터넷 먼저, 실패하면 저장본
    e.respondWith(fetch(req).then(res => {
      if(res.ok){ const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
      return res;
    }).catch(() => caches.match(req, { ignoreSearch: true }).then(r => r || caches.match('./recipe-finder.html'))));
  } else if(isFont || isThumb){
    // 글꼴·썸네일은 저장본 먼저
    e.respondWith(caches.match(req).then(hit => hit || fetch(req).then(res => {
      const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); return res;
    })));
  }
});
