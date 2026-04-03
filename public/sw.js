const CACHE_NAME = "spasiba-shell-v5";
const APP_SHELL = [
  "/",
  "/manifest.webmanifest",
  "/icon.svg",
  "/maskable-icon.svg",
  "/apple-splash.svg",
  "/pwa-shell.css",
  "/pwa-shell.js"
];
const CACHEABLE_DESTINATIONS = new Set(["style", "script", "font", "image"]);

self.addEventListener("install", (event) => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL))
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys.map((key) => {
            if (key !== CACHE_NAME) {
              return caches.delete(key);
            }

            return Promise.resolve(false);
          })
        )
      )
      .then(() => self.clients.claim())
  );
});

function isSameOrigin(url) {
  return url.origin === self.location.origin;
}

function shouldSkipRequest(request, url) {
  if (request.method !== "GET") {
    return true;
  }

  if (!isSameOrigin(url)) {
    return true;
  }

  if (url.pathname.startsWith("/api/")) {
    return true;
  }

  if (url.pathname.startsWith("/_next/image")) {
    return true;
  }

  return false;
}

function shouldCacheResponse(request, url, response) {
  if (!response || !response.ok || response.type !== "basic") {
    return false;
  }

  const cacheControl = response.headers.get("Cache-Control") || "";

  if (/no-store/i.test(cacheControl)) {
    return false;
  }

  if (request.mode === "navigate") {
    return !url.search;
  }

  if (url.search) {
    return false;
  }

  if (APP_SHELL.includes(url.pathname) || url.pathname.startsWith("/_next/static/")) {
    return true;
  }

  return CACHEABLE_DESTINATIONS.has(request.destination);
}

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);

  if (shouldSkipRequest(request, url)) {
    return;
  }

  if (request.mode === "navigate") {
    event.respondWith(
      fetch(request)
        .then((response) => {
          if (shouldCacheResponse(request, url, response)) {
            const clonedResponse = response.clone();
            event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.put(request, clonedResponse)));
          }

          return response;
        })
        .catch(async () => {
          const cachedResponse = await caches.match(request);

          if (cachedResponse) {
            return cachedResponse;
          }

          const cachedHome = await caches.match("/");

          if (cachedHome) {
            return cachedHome;
          }

          return new Response("<!doctype html><html><body style='margin:0;background:#000;color:#fff;font-family:-apple-system, BlinkMacSystemFont, sans-serif;display:flex;align-items:center;justify-content:center;height:100vh'>Offline</body></html>", {
            headers: {
              "Content-Type": "text/html; charset=utf-8"
            }
          });
        })
    );
    return;
  }

  event.respondWith(
    caches.match(request).then((cachedResponse) => {
      const networkFetch = fetch(request)
        .then((response) => {
          if (shouldCacheResponse(request, url, response)) {
            const clonedResponse = response.clone();
            event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.put(request, clonedResponse)));
          }

          return response;
        })
        .catch(() => cachedResponse);

      return cachedResponse || networkFetch;
    })
  );
});
