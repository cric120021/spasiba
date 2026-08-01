"use client";

import { createQueryKey } from "@/lib/utils";

export type SearchHistoryEntry = {
  query: string;
  normalizedQuery: string;
  useCount: number;
  lastUsedAt: number;
  thumbnail: string | null;
  resultCount: number;
};

export const SEARCH_HISTORY_UPDATED_EVENT = "spasiba:search-history-updated";

const SEARCH_HISTORY_STORAGE_KEY = "spasiba:search-history:v1";
const SEARCH_HISTORY_MAX_ENTRIES = 640;
const SEARCH_HISTORY_STALE_TTL_MS = 1000 * 60 * 60 * 24 * 90;

function getStorage() {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

function emitSearchHistoryUpdate() {
  if (typeof window === "undefined") {
    return;
  }

  window.dispatchEvent(new CustomEvent(SEARCH_HISTORY_UPDATED_EVENT));
}

function getRetentionScore(entry: SearchHistoryEntry) {
  const ageDays = Math.max(0, (Date.now() - entry.lastUsedAt) / (1000 * 60 * 60 * 24));
  return entry.useCount * 24 - ageDays;
}

function normalizeHistoryEntries(entries: SearchHistoryEntry[]) {
  const now = Date.now();

  return entries
    .filter((entry) => {
      if (!entry?.query?.trim()) {
        return false;
      }

      if (!entry.normalizedQuery?.trim()) {
        return false;
      }

      return now - entry.lastUsedAt <= SEARCH_HISTORY_STALE_TTL_MS;
    })
    .sort((left, right) => {
      const rightScore = getRetentionScore(right);
      const leftScore = getRetentionScore(left);

      if (rightScore !== leftScore) {
        return rightScore - leftScore;
      }

      return right.lastUsedAt - left.lastUsedAt;
    })
    .slice(0, SEARCH_HISTORY_MAX_ENTRIES);
}

function readStoredHistory() {
  const storage = getStorage();

  if (!storage) {
    return [] as SearchHistoryEntry[];
  }

  try {
    const rawValue = storage.getItem(SEARCH_HISTORY_STORAGE_KEY);

    if (!rawValue) {
      return [];
    }

    const parsed = JSON.parse(rawValue) as SearchHistoryEntry[] | null;
    return Array.isArray(parsed) ? normalizeHistoryEntries(parsed) : [];
  } catch {
    return [];
  }
}

function writeStoredHistory(entries: SearchHistoryEntry[]) {
  const storage = getStorage();

  if (!storage) {
    return entries;
  }

  const normalized = normalizeHistoryEntries(entries);

  try {
    storage.setItem(SEARCH_HISTORY_STORAGE_KEY, JSON.stringify(normalized));
  } catch {
    // Ignore storage failures.
  }

  return normalized;
}

function upsertSearchHistoryEntry(
  query: string,
  options?: {
    touch?: boolean;
    thumbnail?: string | null;
    resultCount?: number;
  }
)
{
  const trimmedQuery = query.trim();
  const normalizedQuery = createQueryKey(trimmedQuery);

  if (!trimmedQuery || !normalizedQuery) {
    return [] as SearchHistoryEntry[];
  }

  const now = Date.now();
  const existingEntries = readStoredHistory();
  const existingEntry = existingEntries.find((entry) => entry.normalizedQuery === normalizedQuery);
  const nextEntries = existingEntries.filter((entry) => entry.normalizedQuery !== normalizedQuery);
  const nextEntry: SearchHistoryEntry = existingEntry
    ? {
        ...existingEntry,
        query: trimmedQuery,
        normalizedQuery,
        useCount: options?.touch === false ? existingEntry.useCount : existingEntry.useCount + 1,
        lastUsedAt: options?.touch === false ? existingEntry.lastUsedAt : now,
        thumbnail:
          typeof options?.thumbnail === "string"
            ? options.thumbnail
            : options?.thumbnail === null
              ? null
              : existingEntry.thumbnail,
        resultCount: typeof options?.resultCount === "number" ? options.resultCount : existingEntry.resultCount
      }
    : {
        query: trimmedQuery,
        normalizedQuery,
        useCount: options?.touch === false ? 1 : 1,
        lastUsedAt: now,
        thumbnail: options?.thumbnail ?? null,
        resultCount: typeof options?.resultCount === "number" ? options.resultCount : 0
      };

  const writtenEntries = writeStoredHistory([nextEntry, ...nextEntries]);
  emitSearchHistoryUpdate();
  return writtenEntries;
}

function matchesSearchHistory(entry: SearchHistoryEntry, query: string) {
  const normalizedQuery = createQueryKey(query);

  if (!normalizedQuery) {
    return true;
  }

  if (entry.normalizedQuery.includes(normalizedQuery)) {
    return true;
  }

  const queryTokens = normalizedQuery.split(/\s+/).filter(Boolean);
  return queryTokens.every((token) => entry.normalizedQuery.includes(token));
}

export function readMatchingSearchHistory(query: string, limit = 8) {
  return readStoredHistory()
    .filter((entry) => matchesSearchHistory(entry, query))
    .sort((left, right) => {
      const rightStartsWith = right.normalizedQuery.startsWith(createQueryKey(query || "")) ? 1 : 0;
      const leftStartsWith = left.normalizedQuery.startsWith(createQueryKey(query || "")) ? 1 : 0;

      if (rightStartsWith !== leftStartsWith) {
        return rightStartsWith - leftStartsWith;
      }

      const rightScore = getRetentionScore(right);
      const leftScore = getRetentionScore(left);

      if (rightScore !== leftScore) {
        return rightScore - leftScore;
      }

      return right.lastUsedAt - left.lastUsedAt;
    })
    .slice(0, Math.max(limit, 0));
}

export function rememberSearchHistoryQuery(query: string) {
  return upsertSearchHistoryEntry(query, { touch: true });
}

export function rememberSearchHistoryResult(query: string, options?: { thumbnail?: string | null; resultCount?: number }) {
  return upsertSearchHistoryEntry(query, {
    touch: false,
    thumbnail: options?.thumbnail,
    resultCount: options?.resultCount
  });
}

export function removeSearchHistoryQuery(query: string) {
  const normalizedQuery = createQueryKey(query);

  if (!normalizedQuery) {
    return [] as SearchHistoryEntry[];
  }

  const writtenEntries = writeStoredHistory(readStoredHistory().filter((entry) => entry.normalizedQuery !== normalizedQuery));
  emitSearchHistoryUpdate();
  return writtenEntries;
}
