"use client";

import type { Song } from "@/lib/types";

export type PlayerQueueSnapshot = {
  queue: Song[];
  currentIndex: number;
  queueSource: "default" | "playlist";
  queueSourceLabel: string | null;
  playlistBaseTrackIds: string[];
  playlistMixEnabled: boolean;
  storedAt: number;
};

const PLAYER_QUEUE_SNAPSHOT_STORAGE_KEY = "spasiba:player-queue:v1";
const PLAYER_QUEUE_SNAPSHOT_TTL_MS = 1000 * 60 * 60 * 24 * 3;
const PLAYER_QUEUE_SNAPSHOT_MAX_TRACKS = 80;

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

function normalizePlaylistBaseTrackIds(value: unknown) {
  if (!Array.isArray(value)) {
    return [] as string[];
  }

  return value.filter((entry): entry is string => typeof entry === "string" && entry.trim().length > 0).slice(0, 160);
}

function normalizeQueueSnapshot(value: unknown) {
  if (!value || typeof value !== "object") {
    return null;
  }

  const snapshot = value as Partial<PlayerQueueSnapshot>;
  const queue = Array.isArray(snapshot.queue)
    ? snapshot.queue.filter((track): track is Song => Boolean(track?.id && track?.videoId)).slice(0, PLAYER_QUEUE_SNAPSHOT_MAX_TRACKS)
    : [];

  if (!queue.length) {
    return null;
  }

  const currentIndex =
    typeof snapshot.currentIndex === "number" && Number.isFinite(snapshot.currentIndex)
      ? Math.min(Math.max(0, Math.trunc(snapshot.currentIndex)), queue.length - 1)
      : 0;

  return {
    queue,
    currentIndex,
    queueSource: snapshot.queueSource === "playlist" ? "playlist" : "default",
    queueSourceLabel: typeof snapshot.queueSourceLabel === "string" && snapshot.queueSourceLabel.trim() ? snapshot.queueSourceLabel : null,
    playlistBaseTrackIds: normalizePlaylistBaseTrackIds(snapshot.playlistBaseTrackIds),
    playlistMixEnabled: Boolean(snapshot.playlistMixEnabled),
    storedAt: typeof snapshot.storedAt === "number" && Number.isFinite(snapshot.storedAt) ? snapshot.storedAt : Date.now()
  } satisfies PlayerQueueSnapshot;
}

export function readPlayerQueueSnapshot() {
  const storage = getStorage();

  if (!storage) {
    return null;
  }

  try {
    const rawValue = storage.getItem(PLAYER_QUEUE_SNAPSHOT_STORAGE_KEY);

    if (!rawValue) {
      return null;
    }

    const parsed = normalizeQueueSnapshot(JSON.parse(rawValue));

    if (!parsed) {
      storage.removeItem(PLAYER_QUEUE_SNAPSHOT_STORAGE_KEY);
      return null;
    }

    if (Date.now() - parsed.storedAt > PLAYER_QUEUE_SNAPSHOT_TTL_MS) {
      storage.removeItem(PLAYER_QUEUE_SNAPSHOT_STORAGE_KEY);
      return null;
    }

    return parsed;
  } catch {
    return null;
  }
}

export function writePlayerQueueSnapshot(snapshot: Omit<PlayerQueueSnapshot, "storedAt">) {
  const storage = getStorage();

  if (!storage) {
    return null;
  }

  const normalized = normalizeQueueSnapshot({
    ...snapshot,
    storedAt: Date.now()
  });

  if (!normalized) {
    storage.removeItem(PLAYER_QUEUE_SNAPSHOT_STORAGE_KEY);
    return null;
  }

  try {
    storage.setItem(PLAYER_QUEUE_SNAPSHOT_STORAGE_KEY, JSON.stringify(normalized));
  } catch {
    // Ignore storage failures.
  }

  return normalized;
}

export function clearPlayerQueueSnapshot() {
  const storage = getStorage();

  if (!storage) {
    return;
  }

  try {
    storage.removeItem(PLAYER_QUEUE_SNAPSHOT_STORAGE_KEY);
  } catch {
    // Ignore storage failures.
  }
}
