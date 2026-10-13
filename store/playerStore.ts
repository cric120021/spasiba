"use client";

import { useRef, useSyncExternalStore } from "react";

import type { Song } from "@/lib/types";

export type PlayerState = {
  currentSong: Song | null;
  queue: Song[];
  currentIndex: number;
  sessionHistory: Song[];
  queueSource: "default" | "playlist";
  queueSourceLabel: string | null;
  playlistBaseTrackIds: string[];
  playlistMixEnabled: boolean;
  isPlaying: boolean;
  isLoading: boolean;
  currentTime: number;
  duration: number;
  progress: number;
  buffered: number;
  volume: number;
  error: string | null;
  toast: { id: number; message: string } | null;
  hydrated: boolean;
};

const initialState: PlayerState = {
  currentSong: null,
  queue: [],
  currentIndex: -1,
  sessionHistory: [],
  queueSource: "default",
  queueSourceLabel: null,
  playlistBaseTrackIds: [],
  playlistMixEnabled: false,
  isPlaying: false,
  isLoading: false,
  currentTime: 0,
  duration: 0,
  progress: 0,
  buffered: 0,
  volume: 0.88,
  error: null,
  toast: null,
  hydrated: false
};

let state = initialState;
const listeners = new Set<() => void>();

function emitChange() {
  listeners.forEach((listener) => listener());
}

export function getPlayerState() {
  return state;
}

export function subscribeToPlayerStore(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function setPlayerState(
  updater: Partial<PlayerState> | ((previousState: PlayerState) => Partial<PlayerState> | PlayerState)
) {
  const partial = typeof updater === "function" ? updater(state) : updater;
  const nextState = {
    ...state,
    ...partial
  };
  const keys = Object.keys(partial) as Array<keyof PlayerState>;

  if (keys.length === 0) {
    return;
  }

  if (keys.every((key) => Object.is(state[key], nextState[key]))) {
    return;
  }

  state = nextState;
  emitChange();
}

export function usePlayerStore<T>(selector: (snapshot: PlayerState) => T, isEqual: (a: T, b: T) => boolean = Object.is) {
  const selectedRef = useRef(selector(getPlayerState()));

  return useSyncExternalStore(
    subscribeToPlayerStore,
    () => {
      const nextSelected = selector(getPlayerState());

      if (isEqual(selectedRef.current, nextSelected)) {
        return selectedRef.current;
      }

      selectedRef.current = nextSelected;
      return nextSelected;
    },
    () => selector(initialState)
  );
}

export const playerActions = {
  hydrate() {
    setPlayerState({ hydrated: true });
  },
  restoreSnapshot(snapshot: {
    queue: Song[];
    currentIndex: number;
    queueSource: "default" | "playlist";
    queueSourceLabel: string | null;
    playlistBaseTrackIds: string[];
    playlistMixEnabled: boolean;
  }) {
    const queue = Array.isArray(snapshot.queue) ? snapshot.queue : [];
    const currentIndex = queue.length ? Math.min(Math.max(0, snapshot.currentIndex), queue.length - 1) : -1;
    const currentSong = currentIndex >= 0 ? queue[currentIndex] || null : null;

    setPlayerState({
      currentSong,
      queue,
      currentIndex,
      sessionHistory: currentSong ? [currentSong] : [],
      queueSource: snapshot.queueSource,
      queueSourceLabel: snapshot.queueSourceLabel,
      playlistBaseTrackIds: snapshot.playlistBaseTrackIds,
      playlistMixEnabled: snapshot.playlistMixEnabled,
      isPlaying: false,
      isLoading: false,
      currentTime: 0,
      duration: 0,
      progress: 0,
      buffered: 0,
      error: null
    });
  },
  primePlayback(
    song: Song,
    queue: Song[],
    currentIndex: number,
    options?: {
      queueSource?: "default" | "playlist";
      queueSourceLabel?: string | null;
      playlistBaseTrackIds?: string[];
      playlistMixEnabled?: boolean;
    }
  ) {
    setPlayerState((previous) => ({
      currentSong: song,
      queue,
      currentIndex,
      sessionHistory:
        previous.sessionHistory[previous.sessionHistory.length - 1]?.id === song.id
          ? previous.sessionHistory
          : [...previous.sessionHistory, song].slice(-160),
      queueSource: options?.queueSource ?? previous.queueSource,
      queueSourceLabel: options?.queueSourceLabel ?? previous.queueSourceLabel,
      playlistBaseTrackIds: options?.playlistBaseTrackIds ?? previous.playlistBaseTrackIds,
      playlistMixEnabled: options?.playlistMixEnabled ?? previous.playlistMixEnabled,
      isPlaying: false,
      isLoading: true,
      currentTime: 0,
      duration: 0,
      buffered: 0,
      progress: 0,
      error: null
    }));
  },
  setQueue(queue: Song[], currentIndex = 0) {
    setPlayerState((previous) => ({
      queue,
      currentIndex,
      currentSong: queue[currentIndex] || previous.currentSong
    }));
  },
  setPlayback(isPlaying: boolean) {
    setPlayerState({ isPlaying });
  },
  setLoading(isLoading: boolean) {
    setPlayerState({ isLoading });
  },
  setTiming(currentTime: number, duration: number, buffered: number) {
    setPlayerState({
      currentTime,
      duration,
      buffered,
      progress: duration > 0 ? currentTime / duration : 0
    });
  },
  setVolume(volume: number) {
    setPlayerState({ volume });
  },
  setError(error: string | null) {
    setPlayerState({
      error,
      isLoading: false
    });
  },
  showToast(message: string) {
    setPlayerState({
      toast: {
        id: Date.now(),
        message
      }
    });
  },
  clearToast() {
    setPlayerState({
      toast: null
    });
  },
  rememberSessionTrack(song: Song) {
    setPlayerState((previous) => ({
      sessionHistory:
        previous.sessionHistory[previous.sessionHistory.length - 1]?.id === song.id
          ? previous.sessionHistory
          : [...previous.sessionHistory, song].slice(-160)
    }));
  },
  setQueueContext(
    queueSource: "default" | "playlist",
    queueSourceLabel: string | null,
    playlistBaseTrackIds: string[] = [],
    playlistMixEnabled = false
  ) {
    setPlayerState({
      queueSource,
      queueSourceLabel,
      playlistBaseTrackIds,
      playlistMixEnabled
    });
  },
  setPlaylistMixEnabled(playlistMixEnabled: boolean) {
    setPlayerState({ playlistMixEnabled });
  }
};
