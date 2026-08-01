"use client";

import { readPersistedCache, readPersistedCacheRecord, removePersistedCache, writePersistedCache } from "@/lib/client-persisted-cache";
import { emitPlaylistsUpdatedEvent } from "@/lib/playlist-events";
import { isCacheOnlyModeEnabled, noteNetworkFailure, noteNetworkSuccess } from "@/lib/resource-governor";
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

const PLAYLISTS_CACHE_TTL_MS = 1000 * 60 * 60 * 12;
const PLAYLISTS_PERSISTED_CACHE_TTL_MS = 1000 * 60 * 60 * 24 * 30;
const PLAYLISTS_BACKGROUND_SYNC_INTERVAL_MS = 1000 * 60 * 60 * 24 * 7;
const PLAYLISTS_PERSISTED_CACHE_KEY = "playlists-library";

let cachedLibrary: PlaylistsLibrary | undefined;
let cachedLibraryExpiresAt = 0;
let pendingLibraryRequest: Promise<PlaylistsLibrary> | null = null;
let playlistsCacheRevision = 0;

function createEmptyLibrary(): PlaylistsLibrary {
  return {
    playlists: [],
    sharedPlaylists: []
  };
}

function setLibraryCache(nextLibrary: PlaylistsLibrary) {
  cachedLibrary = nextLibrary;
  cachedLibraryExpiresAt = Date.now() + PLAYLISTS_CACHE_TTL_MS;
  writePersistedCache(PLAYLISTS_PERSISTED_CACHE_KEY, nextLibrary, PLAYLISTS_PERSISTED_CACHE_TTL_MS);
  return nextLibrary;
}

export function primePlaylistsLibrary(nextLibrary: PlaylistsLibrary, options?: { emit?: boolean }) {
  const normalizedLibrary = {
    playlists: nextLibrary.playlists || [],
    sharedPlaylists: nextLibrary.sharedPlaylists || []
  } satisfies PlaylistsLibrary;

  setLibraryCache(normalizedLibrary);

  if (options?.emit !== false) {
    emitPlaylistsUpdatedEvent();
  }

  return normalizedLibrary;
}

export function mutateCachedPlaylistsLibrary(
  updater: (current: PlaylistsLibrary) => PlaylistsLibrary,
  options?: { emit?: boolean }
) {
  const nextLibrary = updater(readCachedPlaylistsLibrary() || createEmptyLibrary());
  return primePlaylistsLibrary(nextLibrary, options);
}

export function upsertCachedOwnedPlaylist(nextPlaylist: Playlist, options?: { emit?: boolean }) {
  return mutateCachedPlaylistsLibrary(
    (current) => {
      const existingIndex = current.playlists.findIndex((playlist) => playlist.id === nextPlaylist.id);
      const nextPlaylists =
        existingIndex >= 0
          ? current.playlists.map((playlist) => (playlist.id === nextPlaylist.id ? nextPlaylist : playlist))
          : [nextPlaylist, ...current.playlists];

      return {
        ...current,
        playlists: nextPlaylists
      };
    },
    options
  );
}

export function removeCachedOwnedPlaylist(playlistId: string, options?: { emit?: boolean }) {
  return mutateCachedPlaylistsLibrary(
    (current) => ({
      ...current,
      playlists: current.playlists.filter((playlist) => playlist.id !== playlistId),
      sharedPlaylists: current.sharedPlaylists.filter((playlist) => playlist.id !== playlistId)
    }),
    options
  );
}

export function upsertCachedSharedPlaylist(nextPlaylist: Playlist, options?: { emit?: boolean }) {
  return mutateCachedPlaylistsLibrary(
    (current) => {
      const existingIndex = current.sharedPlaylists.findIndex((playlist) => playlist.id === nextPlaylist.id);
      const normalizedPlaylist = {
        ...nextPlaylist,
        isShared: true
      } satisfies Playlist;
      const nextSharedPlaylists =
        existingIndex >= 0
          ? current.sharedPlaylists.map((playlist) => (playlist.id === normalizedPlaylist.id ? normalizedPlaylist : playlist))
          : [normalizedPlaylist, ...current.sharedPlaylists];

      return {
        ...current,
        sharedPlaylists: nextSharedPlaylists
      };
    },
    options
  );
}

export function removeCachedSharedPlaylist(playlistId: string, options?: { emit?: boolean }) {
  return mutateCachedPlaylistsLibrary(
    (current) => ({
      ...current,
      sharedPlaylists: current.sharedPlaylists.filter((playlist) => playlist.id !== playlistId)
    }),
    options
  );
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

export function getCachedPlaylistsLibrarySnapshot() {
  return readCachedPlaylistsLibrary();
}

export function getCachedPlaylistById(playlistId: string) {
  return readCachedPlaylistsLibrary()?.playlists.find((playlist) => playlist.id === playlistId) || null;
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
  const persistedLibrary = readCachedPlaylistsLibrary();

  if (!force && cachedLibrary && cachedLibraryExpiresAt > Date.now()) {
    return cachedLibrary;
  }

  if (!force) {
    if (persistedLibrary !== undefined) {
      return persistedLibrary;
    }
  }

  if (isCacheOnlyModeEnabled() && persistedLibrary !== undefined) {
    return persistedLibrary;
  }

  if (!force && pendingLibraryRequest) {
    return pendingLibraryRequest;
  }

  const requestRevision = playlistsCacheRevision;
  const request = fetch("/api/playlists", {
    method: "GET",
    credentials: "same-origin",
    cache: force ? "reload" : "default",
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

      noteNetworkSuccess();

      return nextLibrary;
    })
    .catch((error) => {
      noteNetworkFailure();

      if (persistedLibrary) {
        return persistedLibrary;
      }

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
  const cachedSnapshot = readCachedPlaylistsLibrary();
  const persisted = readPersistedCacheRecord<PlaylistsLibrary>(PLAYLISTS_PERSISTED_CACHE_KEY);

  if (persisted && Date.now() - persisted.storedAt < PLAYLISTS_BACKGROUND_SYNC_INTERVAL_MS) {
    return cachedSnapshot || persisted.value;
  }

  if (isCacheOnlyModeEnabled() && cachedSnapshot !== undefined) {
    return cachedSnapshot;
  }

  const nextLibrary = await fetchPlaylistsLibrary({ force: true });
  emitPlaylistsUpdatedEvent();
  return nextLibrary;
}
