const CACHE_NAME = "spasiba-shell-v10";
const SEARCH_CACHE_NAME = "spasiba-search-v1";
const CATEGORY_FEED_CACHE_NAME = "spasiba-category-feed-v2";
const ARTWORK_CACHE_NAME = "spasiba-artwork-v1";
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
const REMOTE_ARTWORK_HOSTS = new Set([
  "i.ytimg.com",
  "lh3.googleusercontent.com",
  "www.gstatic.com",
  "yt3.ggpht.com",
  "images.pexels.com"
]);

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
            if (key !== CACHE_NAME && key !== SEARCH_CACHE_NAME && key !== CATEGORY_FEED_CACHE_NAME && key !== ARTWORK_CACHE_NAME) {
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

function isRemoteArtworkRequest(request, url) {
  return request.destination === "image" && REMOTE_ARTWORK_HOSTS.has(url.hostname);
}

function shouldSkipRequest(request, url) {
  if (request.method !== "GET") {
    return true;
  }

  if (!isSameOrigin(url) && !isRemoteArtworkRequest(request, url)) {
    return true;
  }

  if (url.pathname.startsWith("/api/") && url.pathname !== "/api/search") {
    return true;
  }

  if (url.pathname.startsWith("/_next/image")) {
    return true;
  }

  return false;
}

function isSearchApiRequest(url) {
  return url.pathname === "/api/search";
}

function isCategoryFeedApiRequest(url) {
  return url.pathname === "/api/category-feed";
}

function shouldCacheSearchResponse(response) {
  if (!response || !response.ok || response.type !== "basic") {
    return false;
  }

  const cacheControl = response.headers.get("Cache-Control") || "";
  return !/no-store/i.test(cacheControl);
}

function shouldCacheCategoryFeedResponse(response) {
  if (!response || !response.ok || response.type !== "basic") {
    return false;
  }

  const cacheControl = response.headers.get("Cache-Control") || "";
  return !/no-store/i.test(cacheControl);
}

function shouldCacheArtworkResponse(response) {
  if (!response) {
    return false;
  }

  return response.ok || response.type === "opaque";
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

  if (isRemoteArtworkRequest(request, url)) {
    event.respondWith(
      caches.open(ARTWORK_CACHE_NAME).then(async (cache) => {
        const cachedResponse = await cache.match(request);

        if (cachedResponse) {
          return cachedResponse;
        }

        return fetch(request)
          .then((response) => {
            if (shouldCacheArtworkResponse(response)) {
              event.waitUntil(cache.put(request, response.clone()));
            }

            return response;
          })
          .catch(() => cachedResponse);
      })
    );
    return;
  }

  if (isCategoryFeedApiRequest(url)) {
    event.respondWith(
      caches.open(CATEGORY_FEED_CACHE_NAME).then(async (cache) => {
        const cachedResponse = await cache.match(request);
        return fetch(request)
          .then((response) => {
            if (shouldCacheCategoryFeedResponse(response)) {
              event.waitUntil(cache.put(request, response.clone()));
            }

            return response;
          })
          .catch(() => cachedResponse);
      })
    );
    return;
  }

  if (isSearchApiRequest(url)) {
    event.respondWith(
      caches.open(SEARCH_CACHE_NAME).then(async (cache) => {
        const cachedResponse = await cache.match(request);
        const networkFetch = fetch(request)
          .then((response) => {
            if (shouldCacheSearchResponse(response)) {
              event.waitUntil(cache.put(request, response.clone()));
            }

            return response;
          })
          .catch(() => cachedResponse);

        return cachedResponse || networkFetch;
      })
    );
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
