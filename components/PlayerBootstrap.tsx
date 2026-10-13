"use client";

import { useEffect } from "react";

import { audioController } from "@/lib/audio-controller";
import { getDeviceInfo } from "@/lib/device";
import { clearPlayerQueueSnapshot, readPlayerQueueSnapshot, writePlayerQueueSnapshot } from "@/lib/player-queue-cache";
import { getPlayerState, playerActions, setPlayerState, usePlayerStore } from "@/store/playerStore";

const VISITOR_ID_STORAGE_KEY = "spasiba_guest_id";
const VISITOR_ID_COOKIE_KEY = "spasiba_guest_id";
const VISITOR_ID_MAX_AGE_SECONDS = 60 * 60 * 24 * 365 * 10;

type NavigatorWithAudioSession = Navigator & {
  audioSession?: {
    type?: string;
  };
};

function isTypingTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) {
    return false;
  }

  return target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName);
}

function readCookieValue(name: string) {
  if (typeof document === "undefined") {
    return null;
  }

  const prefix = `${name}=`;
  const cookie = document.cookie
    .split(";")
    .map((value) => value.trim())
    .find((value) => value.startsWith(prefix));

  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null;
}

function writeVisitorCookie(id: string) {
  if (typeof document === "undefined") {
    return;
  }

  const secure = window.location.protocol === "https:" ? "; Secure" : "";
  document.cookie = `${VISITOR_ID_COOKIE_KEY}=${encodeURIComponent(id)}; Path=/; Max-Age=${VISITOR_ID_MAX_AGE_SECONDS}; SameSite=Lax${secure}`;
}

function ensureVisitorId() {
  if (typeof window === "undefined") {
    return;
  }

  const existingLocalId = window.localStorage.getItem(VISITOR_ID_STORAGE_KEY);
  const existingCookieId = readCookieValue(VISITOR_ID_COOKIE_KEY);
  const visitorId = existingLocalId || existingCookieId || window.crypto.randomUUID();

  window.localStorage.setItem(VISITOR_ID_STORAGE_KEY, visitorId);
  writeVisitorCookie(visitorId);
}

export default function PlayerBootstrap() {
  const deviceInfo = getDeviceInfo();
  const isAndroid = deviceInfo.isAndroid;
  const isAndroidStandalone = deviceInfo.isAndroid && deviceInfo.isStandalone;
  const isMobileSessionDevice = deviceInfo.isAndroid || deviceInfo.isIOS;
  const isMobileStandalone = deviceInfo.isStandalone && isMobileSessionDevice;
  const currentSong = usePlayerStore((state) => state.currentSong);
  const queue = usePlayerStore((state) => state.queue);
  const currentIndex = usePlayerStore((state) => state.currentIndex);
  const queueSource = usePlayerStore((state) => state.queueSource);
  const queueSourceLabel = usePlayerStore((state) => state.queueSourceLabel);
  const playlistBaseTrackIds = usePlayerStore((state) => state.playlistBaseTrackIds);
  const playlistMixEnabled = usePlayerStore((state) => state.playlistMixEnabled);
  const isPlaying = usePlayerStore((state) => state.isPlaying);
  const isLoading = usePlayerStore((state) => state.isLoading);
  const currentTime = usePlayerStore((state) => state.currentTime);
  const duration = usePlayerStore((state) => state.duration);

  useEffect(() => {
    ensureVisitorId();
    const cachedQueueSnapshot = readPlayerQueueSnapshot();

    if (cachedQueueSnapshot) {
      playerActions.restoreSnapshot(cachedQueueSnapshot);
    }

    audioController.init();
    playerActions.hydrate();

    if (cachedQueueSnapshot?.queue?.length) {
      const restoredTrack = cachedQueueSnapshot.queue[cachedQueueSnapshot.currentIndex];

      if (restoredTrack) {
        void audioController.prepareTrack(restoredTrack);
      }
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (isTypingTarget(event.target)) {
        return;
      }

      if (event.code === "Space") {
        event.preventDefault();
        void audioController.togglePlayback();
      }

      if (event.code === "ArrowRight") {
        event.preventDefault();
        void audioController.playNext();
      }

      if (event.code === "ArrowLeft") {
        event.preventDefault();
        void audioController.playPrevious();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    (window as any).SpasibaAndroidBridge = {
      play: () => void audioController.resume(),
      resume: () => void audioController.resume(),
      pause: () => void audioController.pause(),
      next: () => void audioController.playNext(),
      previous: () => void audioController.playPrevious(),
      seek: (seconds: number) => audioController.seekToSeconds(seconds)
    };

    const previousListener = (window as any).__spasibaAndroidNativeAudioDidChange;

    (window as any).__spasibaAndroidNativeAudioDidChange = (snapshot: any) => {
      try {
        previousListener?.(snapshot);
      } catch (_) {}

      if (!snapshot) {
        return;
      }

      const { queue, currentSong, isPlaying, isLoading, currentTime, duration } = getPlayerState();
      const videoId = String(snapshot.videoId || currentSong?.videoId || currentSong?.id || "");
      const isNativePlaying = snapshot.state === "playing";
      const isNativeLoading = snapshot.state === "loading";
      const snapCurrentTime = Number(snapshot.currentTime || 0);
      const snapDuration = Number(snapshot.duration || 0);

      let foundIndex = queue.findIndex((s) => s.id === videoId);
      let targetSong = foundIndex >= 0 ? queue[foundIndex] : null;

      if (!targetSong && (snapshot.title || snapshot.artist)) {
        targetSong = {
          id: videoId,
          title: snapshot.title || "Spasiba Track",
          author: snapshot.artist || "Unknown Artist",
          thumbnail: snapshot.artworkUrl || `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`,
          duration: Math.round(snapDuration)
        } as any;
      }

      if (targetSong && targetSong.id !== currentSong?.id) {
        if (foundIndex >= 0) {
          setPlayerState({
            queue,
            currentIndex: foundIndex,
            currentSong: targetSong,
            isPlaying: isNativePlaying,
            isLoading: isNativeLoading,
            currentTime: snapCurrentTime,
            duration: snapDuration > 0 ? snapDuration : duration,
            progress: snapDuration > 0 ? snapCurrentTime / snapDuration : 0
          });
          return;
        } else if (targetSong) {
          setPlayerState({
            currentSong: targetSong,
            queue: [...queue, targetSong],
            currentIndex: queue.length,
            isPlaying: isNativePlaying,
            isLoading: isNativeLoading,
            currentTime: snapCurrentTime,
            duration: snapDuration > 0 ? snapDuration : duration,
            progress: snapDuration > 0 ? snapCurrentTime / snapDuration : 0
          });
          return;
        }
      }

      setPlayerState({
        isPlaying: isNativePlaying,
        isLoading: isNativeLoading,
        currentTime: snapCurrentTime,
        duration: snapDuration > 0 ? snapDuration : duration,
        progress: snapDuration > 0 ? snapCurrentTime / snapDuration : 0
      });
    };

    return () => {
      (window as any).__spasibaAndroidNativeAudioDidChange = previousListener;
    };
  }, []);

  useEffect(() => {
    if (!currentSong || !queue.length || currentIndex < 0) {
      clearPlayerQueueSnapshot();
      return;
    }

    writePlayerQueueSnapshot({
      queue,
      currentIndex,
      queueSource,
      queueSourceLabel,
      playlistBaseTrackIds,
      playlistMixEnabled
    });
  }, [currentIndex, currentSong, playlistBaseTrackIds, playlistMixEnabled, queue, queueSource, queueSourceLabel]);

  useEffect(() => {
    if (!isMobileSessionDevice || typeof navigator === "undefined") {
      return;
    }

    try {
      const navigation = navigator as NavigatorWithAudioSession;

      if (navigation.audioSession) {
        navigation.audioSession.type = "playback";
      }
    } catch {
      // Best-effort mobile playback hint only.
    }
  }, [isMobileSessionDevice]);

  useEffect(() => {
    if (!isMobileSessionDevice || typeof navigator === "undefined" || !("mediaSession" in navigator)) {
      return;
    }

    try {
      navigator.mediaSession.setActionHandler("play", () => {
        void audioController.playExplicit();
      });
      navigator.mediaSession.setActionHandler("pause", () => {
        audioController.pauseExplicit();
      });
      navigator.mediaSession.setActionHandler("previoustrack", () => {
        void audioController.playPrevious();
      });
      navigator.mediaSession.setActionHandler("nexttrack", () => {
        void audioController.playNext();
      });
      navigator.mediaSession.setActionHandler("seekto", (details) => {
        if (typeof details.seekTime === "number") {
          audioController.seekToSeconds(details.seekTime);
        }
      });
      navigator.mediaSession.setActionHandler("seekbackward", (details) => {
        const { currentTime: activeTime } = getPlayerState();
        audioController.seekToSeconds(activeTime - (details.seekOffset || 10));
      });
      navigator.mediaSession.setActionHandler("seekforward", (details) => {
        const { currentTime: activeTime } = getPlayerState();
        audioController.seekToSeconds(activeTime + (details.seekOffset || 10));
      });
    } catch {
      // Android / iOS browsers can expose Media Session with partial action support.
    }

    return () => {
      try {
        navigator.mediaSession.setActionHandler("play", null);
        navigator.mediaSession.setActionHandler("pause", null);
        navigator.mediaSession.setActionHandler("previoustrack", null);
        navigator.mediaSession.setActionHandler("nexttrack", null);
        navigator.mediaSession.setActionHandler("seekto", null);
        navigator.mediaSession.setActionHandler("seekbackward", null);
        navigator.mediaSession.setActionHandler("seekforward", null);
      } catch {
        // Ignore cleanup issues on partial implementations.
      }
    };
  }, [isMobileSessionDevice]);

  useEffect(() => {
    if (!isMobileSessionDevice || typeof navigator === "undefined" || !("mediaSession" in navigator)) {
      return;
    }

    navigator.mediaSession.playbackState = currentSong ? (isPlaying || isLoading ? "playing" : "paused") : "none";

    if (!currentSong) {
      navigator.mediaSession.metadata = null;
      return;
    }

    try {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: currentSong.title,
        artist: currentSong.author,
        album: "Spasiba",
        artwork: [
          { src: currentSong.thumbnail, sizes: "96x96", type: "image/jpeg" },
          { src: currentSong.thumbnail, sizes: "128x128", type: "image/jpeg" },
          { src: currentSong.thumbnail, sizes: "192x192", type: "image/jpeg" },
          { src: currentSong.thumbnail, sizes: "256x256", type: "image/jpeg" },
          { src: currentSong.thumbnail, sizes: "384x384", type: "image/jpeg" },
          { src: currentSong.thumbnail, sizes: "512x512", type: "image/jpeg" }
        ]
      });
    } catch {
      // Ignore metadata issues on browsers with partial support.
    }
  }, [currentSong, isLoading, isPlaying, isMobileSessionDevice]);

  useEffect(() => {
    if (!isMobileSessionDevice || typeof document === "undefined" || typeof navigator === "undefined" || !("mediaSession" in navigator)) {
      return;
    }

    const handleVisibilityChange = () => {
      if (!document.hidden) {
        audioController.clearBackgroundPlaybackGuard();
        return;
      }

      const { currentSong: activeSong, isPlaying: activePlaying, isLoading: activeLoading } = getPlayerState();

      if (!activeSong) {
        return;
      }

      if (activePlaying || activeLoading) {
        audioController.armBackgroundPlaybackGuard();
        const audio = audioController.getAudioElement();
        if (audio && audio.paused && audio.src) {
          void audio.play().catch(() => {});
        }
      }

      navigator.mediaSession.playbackState = activePlaying || activeLoading ? "playing" : "paused";

      try {
        navigator.mediaSession.metadata = new MediaMetadata({
          title: activeSong.title,
          artist: activeSong.author,
          album: "Spasiba",
          artwork: [
            { src: activeSong.thumbnail, sizes: "96x96", type: "image/jpeg" },
            { src: activeSong.thumbnail, sizes: "128x128", type: "image/jpeg" },
            { src: activeSong.thumbnail, sizes: "192x192", type: "image/jpeg" },
            { src: activeSong.thumbnail, sizes: "256x256", type: "image/jpeg" },
            { src: activeSong.thumbnail, sizes: "384x384", type: "image/jpeg" },
            { src: activeSong.thumbnail, sizes: "512x512", type: "image/jpeg" }
          ]
        });
      } catch {
        // Best-effort refresh right before the app is backgrounded.
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => document.removeEventListener("visibilitychange", handleVisibilityChange);
  }, [isMobileSessionDevice]);

  useEffect(() => {
    if (
      !isMobileSessionDevice ||
      typeof navigator === "undefined" ||
      !("mediaSession" in navigator) ||
      typeof navigator.mediaSession.setPositionState !== "function" ||
      !duration ||
      !Number.isFinite(duration) ||
      duration <= 0
    ) {
      return;
    }

    try {
      const validPosition = Math.max(0, Math.min(currentTime, duration));
      const validDuration = Math.max(validPosition + 0.1, duration);
      navigator.mediaSession.setPositionState({
        duration: validDuration,
        playbackRate: 1,
        position: validPosition
      });
    } catch {
      // Position state is optional and can fail on partial implementations.
    }
  }, [Math.floor(currentTime), Math.floor(duration), isMobileSessionDevice]);

  return null;
}
