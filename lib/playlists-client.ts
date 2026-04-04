"use client";

import { readPersistedCache, removePersistedCache, writePersistedCache } from "@/lib/client-persisted-cache";
import { emitPlaylistsUpdatedEvent } from "@/lib/playlist-events";
import type { Playlist } from "@/lib/types";

type PlaylistsLibrary = {
  playlists: Playlist[];
  sharedPlaylists: Playlist[];
};

type PlaylistsResponse = {
  playlists?: Playlist[];
  sharedPlaylists?: Playlist[];
  error?: string;
};

const PLAYLISTS_CACHE_TTL_MS = 20_000;
const PLAYLISTS_PERSISTED_CACHE_TTL_MS = 1000 * 60 * 60 * 24 * 30;
const PLAYLISTS_PERSISTED_CACHE_KEY = "playlists-library";

let cachedLibrary: PlaylistsLibrary | undefined;
let cachedLibraryExpiresAt = 0;
let pendingLibraryRequest: Promise<PlaylistsLibrary> | null = null;
let playlistsCacheRevision = 0;

function setLibraryCache(nextLibrary: PlaylistsLibrary) {
  cachedLibrary = nextLibrary;
  cachedLibraryExpiresAt = Date.now() + PLAYLISTS_CACHE_TTL_MS;
  writePersistedCache(PLAYLISTS_PERSISTED_CACHE_KEY, nextLibrary, PLAYLISTS_PERSISTED_CACHE_TTL_MS);
  return nextLibrary;
}

export function readCachedPlaylistsLibrary() {
  if (cachedLibrary && cachedLibraryExpiresAt > Date.now()) {
    return cachedLibrary;
  }

  const persisted = readPersistedCache<PlaylistsLibrary>(PLAYLISTS_PERSISTED_CACHE_KEY);

  if (persisted !== undefined) {
    cachedLibrary = persisted;
    cachedLibraryExpiresAt = Date.now() + PLAYLISTS_CACHE_TTL_MS;
    return persisted;
  }

  return undefined;
}

export function invalidatePlaylistsCache() {
  cachedLibrary = undefined;
  cachedLibraryExpiresAt = 0;
  pendingLibraryRequest = null;
  playlistsCacheRevision += 1;
  removePersistedCache(PLAYLISTS_PERSISTED_CACHE_KEY);
}

export function notifyPlaylistsUpdated() {
  invalidatePlaylistsCache();
  emitPlaylistsUpdatedEvent();
}

export async function fetchPlaylistsLibrary(options?: { force?: boolean }) {
  const force = options?.force ?? false;

  if (!force && cachedLibrary && cachedLibraryExpiresAt > Date.now()) {
    return cachedLibrary;
  }

  if (!force) {
    const persisted = readCachedPlaylistsLibrary();

    if (persisted !== undefined) {
      return persisted;
    }
  }

  if (!force && pendingLibraryRequest) {
    return pendingLibraryRequest;
  }

  const requestRevision = playlistsCacheRevision;
  const request = fetch("/api/playlists", {
    method: "GET",
    credentials: "same-origin",
    cache: "no-store",
    headers: {
      Accept: "application/json"
    }
  })
    .then(async (response) => {
      const payload = (await response.json().catch(() => null)) as PlaylistsResponse | null;

      if (!response.ok) {
        throw new Error(payload?.error || "Unable to load playlists.");
      }

      const nextLibrary = {
        playlists: payload?.playlists || [],
        sharedPlaylists: payload?.sharedPlaylists || []
      };

      if (requestRevision === playlistsCacheRevision) {
        setLibraryCache(nextLibrary);
      }

      return nextLibrary;
    })
    .catch((error) => {
      if (cachedLibrary) {
        return cachedLibrary;
      }

      throw error;
    })
    .finally(() => {
      pendingLibraryRequest = null;
    });

  pendingLibraryRequest = request;
  return request;
}

export async function syncPlaylistsLibraryWithServer() {
  const nextLibrary = await fetchPlaylistsLibrary({ force: true });
  emitPlaylistsUpdatedEvent();
  return nextLibrary;
}
