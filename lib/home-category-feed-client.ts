"use client";

import { readPersistedCacheRecord, writePersistedCache } from "@/lib/client-persisted-cache";
import { isCacheOnlyModeEnabled, noteNetworkFailure, noteNetworkSuccess } from "@/lib/resource-governor";
import type { HomeCategoryFeed } from "@/lib/home-category-feed";

const CATEGORY_FEED_MEMORY_TTL_MS = 1000 * 60 * 60 * 12;
const CATEGORY_FEED_SOFT_REFRESH_TTL_MS = 1000 * 60 * 60 * 24 * 7;
const CATEGORY_FEED_PERSISTED_CACHE_TTL_MS = 1000 * 60 * 60 * 24 * 30;

const memoryCache = new Map<string, { value: HomeCategoryFeed; expiresAt: number }>();
const pendingRequests = new Map<string, Promise<HomeCategoryFeed | null>>();

function getCategoryFeedCacheKey(categoryId: string) {
  return `category-feed:v3:${categoryId}`;
}

function readMemoryCache(categoryId: string) {
  const cached = memoryCache.get(categoryId);

  if (!cached) {
    return null;
  }

  if (cached.expiresAt <= Date.now()) {
    memoryCache.delete(categoryId);
    return null;
  }

  return cached.value;
}

function writeCategoryFeedCache(categoryId: string, feed: HomeCategoryFeed) {
  const record = {
    value: feed,
    expiresAt: Date.now() + CATEGORY_FEED_MEMORY_TTL_MS
  };

  memoryCache.set(categoryId, record);
  writePersistedCache(getCategoryFeedCacheKey(categoryId), feed, CATEGORY_FEED_PERSISTED_CACHE_TTL_MS);
  return feed;
}

export function primeCategoryFeed(categoryId: string, feed: HomeCategoryFeed) {
  return writeCategoryFeedCache(categoryId, feed);
}

function readPersistedCategoryFeedRecord(categoryId: string) {
  return readPersistedCacheRecord<HomeCategoryFeed>(getCategoryFeedCacheKey(categoryId));
}

export function readCachedCategoryFeed(categoryId: string) {
  const memory = readMemoryCache(categoryId);

  if (memory) {
    return memory;
  }

  const persisted = readPersistedCategoryFeedRecord(categoryId);

  if (!persisted) {
    return null;
  }

  memoryCache.set(categoryId, {
    value: persisted.value,
    expiresAt: Date.now() + CATEGORY_FEED_MEMORY_TTL_MS
  });

  return persisted.value;
}

function createCategoryFeedRequest(categoryId: string, signal?: AbortSignal) {
  return fetch(`/api/category-feed?categoryId=${encodeURIComponent(categoryId)}`, {
    method: "GET",
    signal,
    headers: {
      Accept: "application/json"
    }
  }).then(async (response) => {
    const payload = (await response.json().catch(() => null)) as HomeCategoryFeed | { error?: string } | null;

    if (!response.ok) {
      if (response.status === 404) {
        return null;
      }

      throw new Error((payload as { error?: string } | null)?.error || "Unable to load category feed.");
    }

    noteNetworkSuccess();
    return writeCategoryFeedCache(categoryId, payload as HomeCategoryFeed);
  }).catch((error) => {
    noteNetworkFailure();
    throw error;
  });
}

function refreshCategoryFeedInBackground(categoryId: string) {
  if (isCacheOnlyModeEnabled()) {
    return;
  }

  if (pendingRequests.has(categoryId)) {
    return;
  }

  const request = createCategoryFeedRequest(categoryId)
    .catch(() => null)
    .finally(() => {
      pendingRequests.delete(categoryId);
    });

  pendingRequests.set(categoryId, request);
}

export async function fetchCategoryFeed(
  categoryId: string,
  options?: {
    force?: boolean;
    signal?: AbortSignal;
  }
) {
  const force = options?.force ?? false;
  const pending = pendingRequests.get(categoryId);

  if (pending) {
    return pending;
  }

  if (!force) {
    const cached = readCachedCategoryFeed(categoryId);

    if (cached) {
      const persisted = readPersistedCategoryFeedRecord(categoryId);

      if (persisted && Date.now() - persisted.storedAt >= CATEGORY_FEED_SOFT_REFRESH_TTL_MS) {
        refreshCategoryFeedInBackground(categoryId);
      }

      return cached;
    }
  }

  const cached = readCachedCategoryFeed(categoryId);

  if (isCacheOnlyModeEnabled() && cached) {
    return cached;
  }

  const request = createCategoryFeedRequest(categoryId, options?.signal)
    .finally(() => {
      pendingRequests.delete(categoryId);
    });

  pendingRequests.set(categoryId, request);
  return request;
}

export function prefetchCategoryFeed(categoryId: string, options?: { onlyIfMissing?: boolean }) {
  if (options?.onlyIfMissing && readCachedCategoryFeed(categoryId)) {
    return;
  }

  if (isCacheOnlyModeEnabled() && readCachedCategoryFeed(categoryId)) {
    return;
  }

  void fetchCategoryFeed(categoryId).catch(() => {
    // Best-effort only.
  });
}
