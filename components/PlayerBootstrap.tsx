"use client";

import { useEffect } from "react";

import { audioController } from "@/lib/audio-controller";
import { getDeviceInfo } from "@/lib/device";
import { getPlayerState, playerActions, usePlayerStore } from "@/store/playerStore";

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
  const isPlaying = usePlayerStore((state) => state.isPlaying);
  const isLoading = usePlayerStore((state) => state.isLoading);
  const currentTime = usePlayerStore((state) => state.currentTime);
  const duration = usePlayerStore((state) => state.duration);

  useEffect(() => {
    ensureVisitorId();
    audioController.init();
    playerActions.hydrate();

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
        void audioController.togglePlayback();
      });
      navigator.mediaSession.setActionHandler("pause", () => {
        void audioController.togglePlayback();
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
      // Android browsers can expose Media Session with partial action support.
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
    if (!isMobileStandalone || typeof document === "undefined" || typeof navigator === "undefined" || !("mediaSession" in navigator)) {
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
  }, [isMobileStandalone]);

  useEffect(() => {
    if (
      !isMobileSessionDevice ||
      typeof navigator === "undefined" ||
      !("mediaSession" in navigator) ||
      typeof navigator.mediaSession.setPositionState !== "function" ||
      !duration ||
      !Number.isFinite(duration)
    ) {
      return;
    }

    try {
      navigator.mediaSession.setPositionState({
        duration,
        playbackRate: 1,
        position: Math.min(currentTime, duration)
      });
    } catch {
      // Position state is optional and can fail on partial implementations.
    }
  }, [currentTime, duration, isMobileSessionDevice]);

  return null;
}
