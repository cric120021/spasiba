"use client";

import type { SearchResponse, Song } from "@/lib/types";
import { createQueryKey } from "@/lib/utils";
import {
  cacheSearchResponse,
  listZeroWasteCacheValues,
  readRecommendationTrackPool,
  readCachedSearchResponse as readPersistedSearchResponse
} from "@/lib/zero-waste-idb";
import {
  canIssueSearchRequest,
  consumeSearchRequest,
  enableCacheOnlyMode,
  noteNetworkFailure,
  noteNetworkSuccess
} from "@/lib/resource-governor";

type SearchRequestInput = {
  query?: string;
  relatedTo?: string;
  limit?: number;
  pages?: number;
  category?: string;
  purpose?: "search" | "suggestion" | "related" | "cold-start";
  signal?: AbortSignal;
};

const pendingRequests = new Map<string, Promise<SearchResponse>>();
const memorySearchCache = new Map<string, SearchResponse>();
const MEMORY_SEARCH_CACHE_LIMIT = 160;
// Queue-related lookups currently need up to 32 related songs on the richest path.
// Canonicalizing there lets smaller related requests reuse the same cached response.
const RELATED_CANONICAL_LIMIT = 32;
const LOCAL_SUGGESTION_TRACK_POOL_LIMIT = 120;
const LOCAL_SUGGESTION_SEARCH_POOL_LIMIT = 10;
const LOCAL_SUGGESTION_RELATED_POOL_LIMIT = 8;

function normalizeRelatedRequest(input: SearchRequestInput) {
  if (!input.relatedTo) {
    return input;
  }

  return {
    ...input,
    limit: Math.max(input.limit || RELATED_CANONICAL_LIMIT, RELATED_CANONICAL_LIMIT),
    pages: 1,
    category: undefined
  } satisfies SearchRequestInput;
}

function buildSearchRequestKey(input: SearchRequestInput) {
  const normalizedInput = normalizeRelatedRequest(input);

  return [
    normalizedInput.relatedTo
      ? `related:${createQueryKey(normalizedInput.relatedTo)}`
      : `query:${createQueryKey(normalizedInput.query || "")}`,
    `limit:${normalizedInput.limit || 12}`,
    `pages:${normalizedInput.pages || 1}`,
    `category:${createQueryKey(normalizedInput.category || "")}`
  ].join("|");
}

function buildSearchBudgetKey(input: SearchRequestInput) {
  if (input.relatedTo) {
    return `related:${createQueryKey(input.relatedTo)}`;
  }

  return `query:${createQueryKey(input.query || "")}`;
}

function sliceSearchResponseForInput(input: SearchRequestInput, response: SearchResponse) {
  if (!input.relatedTo || typeof input.limit !== "number" || response.results.length <= input.limit) {
    return response;
  }

  return {
    ...response,
    results: response.results.slice(0, input.limit)
  } satisfies SearchResponse;
}

function scoreSongForLocalSuggestion(query: string, track: Song) {
  const normalizedQuery = createQueryKey(query);
  const title = createQueryKey(track.title);
  const author = createQueryKey(track.author);
  const haystack = `${title} ${author}`.trim();

  if (!normalizedQuery || !haystack) {
    return -1;
  }

  let score = 0;

  if (title === normalizedQuery) {
    score += 200;
  } else if (title.startsWith(normalizedQuery)) {
    score += 150;
  } else if (title.includes(normalizedQuery)) {
    score += 100;
  }

  if (author === normalizedQuery || author.startsWith(normalizedQuery)) {
    score += title.includes(normalizedQuery) ? 20 : 80;
  }

  for (const token of normalizedQuery.split(/\s+/).filter(Boolean)) {
    if (title.includes(token)) {
      score += 18;
    }

    if (author.includes(token)) {
      score += 5;
    }
  }

  score += Math.min(18, Math.log10(Math.max(1, track.views || 1)) * 3);
  return score;
}

type SuggestionCandidate = {
  track: Song;
  score: number;
};

function rememberSuggestionCandidate(
  bucket: Map<string, SuggestionCandidate>,
  query: string,
  track: Song,
  sourceBoost = 0
) {
  if (!track?.id) {
    return;
  }

  const baseScore = scoreSongForLocalSuggestion(query, track);

  if (baseScore <= 0) {
    return;
  }

  const existing = bucket.get(track.id);
  const nextScore = baseScore + sourceBoost;

  if (!existing || nextScore > existing.score) {
    bucket.set(track.id, {
      track,
      score: nextScore
    });
  }
}

function scoreTrackForQuery(query: string, track: SearchResponse["results"][number]) {
  const normalizedQuery = createQueryKey(query);
  const haystack = createQueryKey(`${track.title} ${track.author}`);

  if (!normalizedQuery || !haystack) {
    return -1;
  }

  let score = 0;

  if (haystack.includes(normalizedQuery)) {
    score += 40;
  }

  for (const token of normalizedQuery.split(/\s+/).filter(Boolean)) {
    if (haystack.includes(token)) {
      score += 12;
    }
  }

  score += Math.min(18, Math.log10(Math.max(1, track.views || 1)) * 3);
  return score;
}

async function buildCachedFallbackResponse(input: SearchRequestInput) {
  const requestKey = buildSearchRequestKey(input);
  const related = Boolean(input.relatedTo);
  const exact = await readPersistedSearchResponse(requestKey, related);

  if (exact) {
    return sliceSearchResponseForInput(input, exact);
  }

  return {
    query: input.query || "",
    results: [],
    cachedAt: new Date().toISOString()
  } satisfies SearchResponse;
}

let legacySearchStorageCleaned = false;

export function cleanupLegacySearchStorage() {
  if (legacySearchStorageCleaned || typeof window === "undefined") {
    return;
  }

  legacySearchStorageCleaned = true;

  try {
    const storage = window.localStorage;
    const keysToRemove: string[] = [];

    for (let index = 0; index < storage.length; index += 1) {
      const key = storage.key(index);

      if (!key) {
        continue;
      }

      if (
        key.startsWith("spasiba:persisted-cache:v1:search-results:") ||
        key.startsWith("spasiba:persisted-cache:v1:search-suggestions:")
      ) {
        keysToRemove.push(key);
      }
    }

    keysToRemove.forEach((key) => {
      storage.removeItem(key);
    });
  } catch {
    // Ignore cleanup failures.
  }
}

export function buildSearchUrl(input: SearchRequestInput) {
  const params = new URLSearchParams();

  if (input.relatedTo) {
    params.set("relatedTo", input.relatedTo);
  }

  if (input.query) {
    params.set("q", input.query);
  }

  if (typeof input.limit === "number") {
    params.set("limit", String(input.limit));
  }

  if (typeof input.pages === "number") {
    params.set("pages", String(input.pages));
  }

  if (input.category) {
    params.set("category", input.category);
  }

  return `/api/search?${params.toString()}`;
}

function rememberSearchResponse(requestKey: string, response: SearchResponse) {
  memorySearchCache.delete(requestKey);
  memorySearchCache.set(requestKey, response);

  while (memorySearchCache.size > MEMORY_SEARCH_CACHE_LIMIT) {
    const oldestKey = memorySearchCache.keys().next().value as string | undefined;

    if (!oldestKey) {
      break;
    }

    memorySearchCache.delete(oldestKey);
  }

  return response;
}

export function readSearchResponseFromMemory(input: SearchRequestInput) {
  const requestKey = buildSearchRequestKey(input);
  const cached = memorySearchCache.get(requestKey) || null;

  if (!cached) {
    return null;
  }

  memorySearchCache.delete(requestKey);
  memorySearchCache.set(requestKey, cached);
  return sliceSearchResponseForInput(input, cached);
}

export async function readLocalSuggestionMatches(query: string, limit = 6) {
  const normalizedQuery = createQueryKey(query);

  if (!normalizedQuery) {
    return [] as Song[];
  }

  const candidates = new Map<string, SuggestionCandidate>();
  const memoryEntries = Array.from(memorySearchCache.values()).slice(-18).reverse();

  memoryEntries.forEach((payload, payloadIndex) => {
    payload.results.slice(0, 12).forEach((track) => {
      rememberSuggestionCandidate(candidates, query, track, Math.max(0, 36 - payloadIndex * 2));
    });
  });

  const [trackPool, cachedSearchPools, cachedRelatedPools] = await Promise.all([
    readRecommendationTrackPool(LOCAL_SUGGESTION_TRACK_POOL_LIMIT),
    listZeroWasteCacheValues<SearchResponse & { requestKey: string }>("search_cache", LOCAL_SUGGESTION_SEARCH_POOL_LIMIT),
    listZeroWasteCacheValues<SearchResponse & { requestKey: string }>("related_cache", LOCAL_SUGGESTION_RELATED_POOL_LIMIT)
  ]);

  cachedSearchPools.forEach((payload, payloadIndex) => {
    const queryBoost = createQueryKey(payload.query || "").startsWith(normalizedQuery) ? 16 : 0;

    payload.results.slice(0, 12).forEach((track) => {
      rememberSuggestionCandidate(candidates, query, track, Math.max(0, 24 - payloadIndex * 2) + queryBoost);
    });
  });

  cachedRelatedPools.forEach((payload, payloadIndex) => {
    payload.results.slice(0, 10).forEach((track) => {
      rememberSuggestionCandidate(candidates, query, track, Math.max(0, 14 - payloadIndex * 2));
    });
  });

  trackPool.slice(0, LOCAL_SUGGESTION_TRACK_POOL_LIMIT).forEach((track, trackIndex) => {
    rememberSuggestionCandidate(candidates, query, track, Math.max(0, 8 - Math.floor(trackIndex / 18)));
  });

  return Array.from(candidates.values())
    .sort((left, right) => right.score - left.score)
    .slice(0, Math.max(limit, 0))
    .map((entry) => entry.track);
}

export async function readCachedSearchResponseWithBudget(input: SearchRequestInput) {
  const requestKey = buildSearchRequestKey(input);
  const related = Boolean(input.relatedTo);
  const memoryCached = memorySearchCache.get(requestKey);

  if (memoryCached) {
    return sliceSearchResponseForInput(input, memoryCached);
  }

  const persisted = await readPersistedSearchResponse(requestKey, related);

  if (persisted) {
    rememberSearchResponse(requestKey, persisted);
    return sliceSearchResponseForInput(input, persisted);
  }

  return null;
}

export function primeSearchResponse(input: SearchRequestInput, response: SearchResponse) {
  const requestKey = buildSearchRequestKey(input);
  const related = Boolean(input.relatedTo);

  rememberSearchResponse(requestKey, response);
  void cacheSearchResponse(requestKey, response, {
    related
  });

  return response;
}

export async function fetchSearchResponseWithBudget(input: SearchRequestInput) {
  const normalizedInput = normalizeRelatedRequest(input);
  const requestKey = buildSearchRequestKey(normalizedInput);
  const budgetKey = buildSearchBudgetKey(input);
  const related = Boolean(normalizedInput.relatedTo);
  const shouldBypassBudget = normalizedInput.purpose === "search";
  const cached = await readCachedSearchResponseWithBudget(input);

  if (cached) {
    return cached;
  }

  const pending = pendingRequests.get(requestKey);

  if (pending) {
    return pending;
  }

  if (!shouldBypassBudget && (!canIssueSearchRequest(budgetKey) || !consumeSearchRequest(budgetKey))) {
    enableCacheOnlyMode();
    return rememberSearchResponse(requestKey, await buildCachedFallbackResponse(input));
  }

  const request = fetch(buildSearchUrl(normalizedInput), {
    method: "GET",
    signal: normalizedInput.signal,
    headers: {
      Accept: "application/json"
    }
  })
    .then(async (response) => {
      const payload = (await response.json().catch(() => null)) as SearchResponse | { error?: string } | null;

      if (!response.ok) {
        throw new Error((payload as { error?: string } | null)?.error || "Search failed.");
      }

      const searchResponse = payload as SearchResponse;
      noteNetworkSuccess();
      await cacheSearchResponse(requestKey, searchResponse, {
        related
      });
      return rememberSearchResponse(requestKey, searchResponse);
    })
    .catch(async (error) => {
      if (error instanceof DOMException && error.name === "AbortError") {
        throw error;
      }

      noteNetworkFailure();
      const fallback = await readPersistedSearchResponse(requestKey, related);

      if (fallback) {
        return rememberSearchResponse(requestKey, fallback);
      }

      throw error;
    })
    .finally(() => {
      pendingRequests.delete(requestKey);
    });

  pendingRequests.set(requestKey, request);
  return request;
}
