"use client";

import { memo, useDeferredValue, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";

import { audioController } from "@/lib/audio-controller";
import { readPersistedCache, writePersistedCache } from "@/lib/client-persisted-cache";
import type { SearchResponse, Song } from "@/lib/types";
import { createQueryKey, scheduleIdleTask } from "@/lib/utils";

import SkeletonGrid from "@/components/SkeletonGrid";
import SongGrid from "@/components/SongGrid";

const searchCache = new Map<string, Song[]>();
const pendingRequests = new Map<string, Promise<Song[]>>();
const SEARCH_PRELOAD_COUNT = 1;
const SEARCH_RESULTS_CACHE_TTL_MS = 1000 * 60 * 60 * 4;

function getPersistedSearchCacheKey(query: string) {
  return `search-results:${createQueryKey(query)}`;
}

async function fetchSearchResults(query: string): Promise<Song[]> {
  const key = createQueryKey(query);
  const cached = searchCache.get(key);

  if (cached) {
    return cached;
  }

  const persisted = readPersistedCache<Song[]>(getPersistedSearchCacheKey(query));

  if (persisted) {
    searchCache.set(key, persisted);
    return persisted;
  }

  const pending = pendingRequests.get(key);

  if (pending) {
    return pending;
  }

  const request = fetch(`/api/search?q=${encodeURIComponent(query)}`, {
    method: "GET",
    headers: {
      Accept: "application/json"
    }
  })
    .then(async (response) => {
      if (!response.ok) {
        const payload = (await response.json().catch(() => null)) as { error?: string } | null;
        throw new Error(payload?.error || "Search failed.");
      }

      const payload = (await response.json()) as SearchResponse;
      searchCache.set(key, payload.results);
      writePersistedCache(getPersistedSearchCacheKey(query), payload.results, SEARCH_RESULTS_CACHE_TTL_MS);
      return payload.results;
    })
    .finally(() => {
      pendingRequests.delete(key);
    });

  pendingRequests.set(key, request);
  return request;
}

function SearchResultsComponent() {
  const searchParams = useSearchParams();
  const query = (searchParams.get("q") || "").trim();
  const sharedVideoId = (searchParams.get("v") || "").trim();
  const deferredQuery = useDeferredValue(query);
  const queryKey = useMemo(() => createQueryKey(query), [query]);
  const [results, setResults] = useState<Song[]>(() => searchCache.get(queryKey) || []);
  const [loading, setLoading] = useState(Boolean(query && !searchCache.get(queryKey)));
  const [error, setError] = useState<string | null>(null);
  const displayResults = useMemo(() => {
    if (!sharedVideoId) {
      return results;
    }

    const sharedIndex = results.findIndex((track) => track.videoId === sharedVideoId);

    if (sharedIndex <= 0) {
      return results;
    }

    const nextResults = [...results];
    const [sharedTrack] = nextResults.splice(sharedIndex, 1);

    nextResults.unshift(sharedTrack);
    return nextResults;
  }, [results, sharedVideoId]);

  useEffect(() => {
    let cancelled = false;

    if (!query) {
      setResults([]);
      setLoading(false);
      setError(null);
      return () => {
        cancelled = true;
      };
    }

    const cached = searchCache.get(queryKey);

    if (cached) {
      setResults(cached);
      setLoading(false);
      setError(null);
      return () => {
        cancelled = true;
      };
    }

    setResults([]);
    setLoading(true);
    setError(null);

    void fetchSearchResults(query)
      .then((nextResults) => {
        if (cancelled) {
          return;
        }

        setResults(nextResults);
        setLoading(false);
      })
      .catch((requestError) => {
        if (cancelled) {
          return;
        }

        setLoading(false);
        setError(requestError instanceof Error ? requestError.message : "Search failed.");
      });

    return () => {
      cancelled = true;
    };
  }, [query, queryKey]);

  useEffect(() => {
    if (!displayResults.length) {
      return;
    }

    const firstTrack = displayResults[0];
    audioController.seedTrack(firstTrack);
    void audioController.prepareTrack(firstTrack);
  }, [displayResults]);

  useEffect(() => {
    if (!displayResults.length) {
      return;
    }

    const warmupTracks = displayResults.slice(1, 1 + SEARCH_PRELOAD_COUNT);

    if (!warmupTracks.length) {
      return;
    }

    const warmup = () => {
      warmupTracks.forEach((track) => {
        void audioController.prepareTrack(track);
      });
    };

    return scheduleIdleTask(warmup, 200);
  }, [displayResults]);

  return (
    <div className="space-y-8">
      <section className="glass-panel-strong overflow-hidden rounded-[24px] px-4 py-4 sm:rounded-[32px] sm:p-8">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div className="space-y-2.5 sm:space-y-3">
            <p className="text-[10px] uppercase tracking-[0.28em] text-teal-200/70 sm:text-[11px] sm:tracking-[0.34em]">Spasiba Search</p>
            <h1 className="text-[24px] font-semibold leading-[1.04] tracking-tight text-white sm:text-5xl">
              <span className="sm:hidden">{deferredQuery || "Search"}</span>
              <span className="hidden sm:inline">{deferredQuery ? `"${deferredQuery}"` : "Search"}</span>
            </h1>
            <p className="max-w-2xl text-[14px] leading-5 text-slate-300/78 sm:text-base sm:leading-6">
              Tap a song and the music starts right away.
            </p>
          </div>
          <div className="flex items-center gap-3">
            <div className="rounded-full border border-white/[0.08] bg-white/[0.06] px-3 py-1.5 text-[10px] uppercase tracking-[0.18em] text-slate-300/80 sm:px-4 sm:py-2 sm:text-[11px] sm:tracking-[0.24em]">
              {displayResults.length} tracks
            </div>
          </div>
        </div>
      </section>

      {!query ? (
        <section className="glass-panel rounded-[24px] p-4 text-[15px] leading-7 text-slate-300/78 sm:rounded-[32px] sm:p-8 sm:text-base">
          Start typing in the search bar to find any song, artist, or album.
        </section>
      ) : null}

      {error ? (
        <section className="glass-panel rounded-[24px] border border-rose-400/25 p-4 text-sm text-rose-100 sm:rounded-[32px] sm:p-6 sm:text-base">
          {error}
        </section>
      ) : null}

      {loading && displayResults.length === 0 ? <SkeletonGrid count={12} /> : null}

      {query && displayResults.length > 0 ? (
        <SongGrid
          title="Results"
          subtitle="Tap any song to play."
          tracks={displayResults}
          prefetchCount={SEARCH_PRELOAD_COUNT}
        />
      ) : null}
    </div>
  );
}

const SearchResults = memo(SearchResultsComponent);

export default SearchResults;
