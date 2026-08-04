"use client";

import { getDeviceInfo } from "@/lib/device";
import { recordLocalHistorySong, updateLocalLikedSongs } from "@/lib/profile-client";
import { ensureRecommendationSession, recordRecommendationEvent } from "@/lib/recommendation-session";
import { fetchSearchResponseWithBudget } from "@/lib/search-client";
import { isSafeModeEnabled, noteNetworkFailure, noteNetworkSuccess } from "@/lib/resource-governor";
import {
  applyEngagementToListenerProfile,
  createEmptyListenerProfile,
  getListenerProfileArtistKey,
  getListenerProfileTrackKey,
  normalizeListenerProfile,
  pickNewerListenerProfile
} from "@/lib/listener-profile";
import type { EngagementAction, EngagementPayload, ListenerProfile, Song, StreamCandidate, StreamResponse } from "@/lib/types";
import { loadYouTubeIframeApi, type YouTubePlayer } from "@/lib/youtube-iframe";
import {
  cacheSearchResponse as cachePersistentSearchResponse,
  cacheStreamResponse,
  readCachedSearchResponse as readPersistentSearchResponse,
  readCachedStreamResponse,
  readListenerProfileSnapshot,
  readRecommendationTrackPool,
  writeListenerProfileSnapshot
} from "@/lib/zero-waste-idb";
import {
  clamp,
  extractExpiryTimestamp,
  getMeaningfulSongTokens,
  getPrimaryArtistName,
  normalizeSongTitle,
  scheduleIdleTask
} from "@/lib/utils";
import { getPlayerState, playerActions, setPlayerState } from "@/store/playerStore";

type StreamCacheValue = StreamResponse & {
  cachedAt: number;
};

type BlobCacheValue = {
  objectUrl: string;
  mimeType: string | null;
  bytes: number;
  cachedAt: number;
};

type PlaybackQueueContext = {
  queueSource?: "default" | "playlist";
  queueSourceLabel?: string | null;
  playlistBaseTrackIds?: string[];
  playlistMixEnabled?: boolean;
};

type QueuedEngagementEvent = EngagementPayload & {
  signals?: string[];
};

const streamCache = new Map<string, StreamCacheValue>();
const pendingStreamRequests = new Map<string, Promise<StreamCacheValue>>();
const blobCache = new Map<string, BlobCacheValue>();
const pendingBlobRequests = new Map<string, Promise<BlobCacheValue | null>>();
const preloadedAudios = new Map<string, HTMLAudioElement>();
const relatedQueueCache = new Map<string, Song[]>();
const pendingRelatedQueueRequests = new Map<string, Promise<Song[]>>();

const VISITOR_ID_STORAGE_KEY = "spasiba_guest_id";
const VISITOR_ID_COOKIE_KEY = "spasiba_guest_id";
const VISITOR_ID_MAX_AGE_SECONDS = 60 * 60 * 24 * 365 * 10;
const LISTENER_PROFILE_STORAGE_PREFIX = "spasiba_listener_profile";
const ENGAGEMENT_QUEUE_STORAGE_PREFIX = "spasiba_engagement_queue";
const STREAM_FRESH_BUFFER_MS = 30_000;
const STREAM_CACHE_LIMIT = 48;
const RELATED_QUEUE_CACHE_LIMIT = 96;
const PRELOAD_AUDIO_LIMIT = 6;
const NEXT_TRACK_PRELOAD_COUNT = 2;
const IOS_NEXT_TRACK_PRELOAD_COUNT = 6;
const IOS_BLOB_PREFETCH_COUNT = 3;
const IOS_BLOB_CACHE_LIMIT = 3;
const IOS_BLOB_MAX_BYTES = 32 * 1024 * 1024;
const IOS_BLOB_MAX_DURATION_SECONDS = 60 * 12;
const PLAYBACK_ATTEMPT_TIMEOUT_MS = 12_000;
const PLAYBACK_COMPLETE_THRESHOLD = 0.92;
const SKIP_EARLY_THRESHOLD = 0.22;
const SKIP_MID_THRESHOLD = 0.72;
const UPCOMING_QUEUE_TARGET = 10;
const MAX_PLAYBACK_QUEUE_LENGTH = 80;
const PREVIOUS_CONTEXT_LIMIT = 2;
const SMART_QUEUE_ARTIST_CAP = 2;
const SMART_QUEUE_PRIMARY_FETCH_LIMIT = 24;
const SMART_QUEUE_SECONDARY_FETCH_LIMIT = 10;
const SMART_QUEUE_CONTEXT_ANCHOR_LIMIT = 2;
const SMART_QUEUE_STAGNATION_LIMIT = 3;
const SMART_QUEUE_FALLBACK_INJECTION_LIMIT = 1;
const SMART_QUEUE_MIN_YOUTUBE_DOMINANCE = 8;
const SEARCH_CANDIDATE_BANK_LIMIT = 100;
const MAX_QUEUE_TRACK_DURATION_SECONDS = 8 * 60;
const QUEUE_DURATION_MULTIPLIER = 2.15;
const RECENT_SESSION_REPEAT_WINDOW = 12;
const RECENT_SESSION_ARTIST_PENALTY = 16;
const ENGAGEMENT_BATCH_LIMIT = 30;
const ENGAGEMENT_BATCH_FLUSH_DELAY_MS = 45_000;
const MAX_PENDING_ENGAGEMENT_EVENTS = 96;
const RELATED_QUEUE_PERSISTED_TTL_MS = 1000 * 60 * 60 * 24 * 3;
const DEFERRED_QUEUE_ENHANCEMENT_DELAY_MS = 1_400;
const DEFERRED_QUEUE_ENHANCEMENT_IDLE_TIMEOUT_MS = 2_000;
const TITLE_VARIANT_PATTERN =
  /\b(lyrics?|lyric|live|remix|slowed|reverb|cover|karaoke|shorts?|sped\s*up|bass\s*boosted|instrumental|nightcore|edit|edit\s*audio|mashup|mix|mega\s*mix|playlist|jukebox|non[\s-]*stop|reels?|status|whatsapp|tiktok|capcut|dialogue|scene|meme|template|bgm|ringtone)\b/i;
const LOW_QUALITY_QUEUE_PATTERN =
  /\b(reels?|shorts?|status|whatsapp|tiktok|capcut|edit\s*audio|dialogue|scene|meme|template|ringtone|interview|podcast|episode|reaction|review|trailer|teaser|speech|lecture|news|vlog|roast|prank|full\s*movie|behind\s*the\s*scenes|making\s*of|mashup|mix|mega\s*mix|playlist|jukebox|non[\s-]*stop|best\s+of|hour|hours|loop)\b/i;
const MUSIC_IDENTITY_PATTERN =
  /\b(official|audio|video|song|songs|music|records|topic|vevo|album|single|provided to youtube|ost|soundtrack)\b/i;
const GENERIC_COMPILATION_PATTERN =
  /\b(top|best|latest|new|global|hollywood|viral|trending|hits?)\b.*\b(song|songs|music|playlist|list)\b|\b(song|songs)\s*list\b|\bnew\s+english\s+song\b/i;
const MIN_QUEUE_TRACK_DURATION_SECONDS = 75;
const PUNJABI_PATTERN =
  /[\u0A00-\u0A7F]|\b(punjabi|diljit|aujla|shubh|sidhu|dhillon|dosanjh|randhawa|punjab)\b/i;
const BOLLYWOOD_PATTERN =
  /[\u0900-\u097F]|\b(bollywood|hindi|arijit|jubin|shreya|atif|darshan|vishal|neha|tulsi|mohit|pritam)\b/i;
const LOFI_PATTERN = /\b(lofi|chill|beats?|ambient|study|focus|sleep|instrumental)\b/i;
const LOVE_PATTERN = /\b(love|romantic|romance|heart|ishq|pyaar|pyar|mohabbat|dil)\b/i;
const SAD_PATTERN = /\b(sad|heartbreak|breakup|broken|alone|emotional|pain|cry)\b/i;
const ENERGY_PATTERN = /\b(workout|gym|run|running|energy|party|hype|pump|motivation)\b/i;
const TRENDING_PATTERN = /\b(trending|viral|hits?|top|chart|popular)\b/i;
const INDIE_PATTERN = /\b(indie|acoustic|non film|alt|alternative|independent)\b/i;

type QueueSignal =
  | "bollywood"
  | "english-global"
  | "punjabi"
  | "lofi-chill"
  | "love-romantic"
  | "sad-emotional"
  | "high-energy-workout"
  | "trending-viral"
  | "indie-non-film";

const SIGNAL_QUERY_TEMPLATES: Record<QueueSignal, readonly string[]> = {
  bollywood: ["bollywood official audio", "hindi film songs official audio", "romantic bollywood official audio"],
  "english-global": ["pop official audio", "global pop official audio", "english pop anthems official audio"],
  punjabi: ["punjabi official audio", "punjabi pop official audio", "punjabi romantic official audio"],
  "lofi-chill": ["lofi official audio", "chill pop official audio", "soft chill official audio"],
  "love-romantic": ["romantic official audio", "love songs official audio", "soft pop official audio"],
  "sad-emotional": ["sad official audio", "heartbreak official audio", "emotional pop official audio"],
  "high-energy-workout": ["workout official audio", "high energy pop official audio", "party anthem official audio"],
  "trending-viral": ["chart hits official audio", "viral hits official audio", "top pop official audio"],
  "indie-non-film": ["indie official audio", "alt pop official audio", "independent pop official audio"]
};

const NETWORK_ENGAGEMENT_ACTIONS = new Set<EngagementAction>([
  "play_start",
  "skip_early",
  "skip_mid",
  "skip_late",
  "complete"
]);

function touchMapEntry<T>(cache: Map<string, T>, key: string, value: T) {
  cache.delete(key);
  cache.set(key, value);
  return value;
}

function pruneMapEntries<T>(cache: Map<string, T>, limit: number, dispose?: (value: T) => void) {
  while (cache.size > limit) {
    const oldestEntry = cache.entries().next().value as [string, T] | undefined;

    if (!oldestEntry) {
      break;
    }

    const [oldestKey, oldestValue] = oldestEntry;
    cache.delete(oldestKey);
    dispose?.(oldestValue);
  }
}

let singletonAudio: HTMLAudioElement | null = null;
let listenersAttached = false;
let progressAnimationFrame: number | null = null;
let sourceSelectionInProgress = false;
let hiddenYouTubePlayer: YouTubePlayer | null = null;
let hiddenYouTubePlayerPromise: Promise<YouTubePlayer> | null = null;
let hiddenYouTubePlayerMount: HTMLDivElement | null = null;
let youtubeSyncInterval: number | null = null;
let activePlaybackEngine: "audio" | "youtube" = "audio";
let hiddenYouTubeMutedForVisibleVideo = false;
let htmlAudioMutedForVisibleVideo = false;
let visibleVideoModeActive = false;
let visibleVideoModeSuspendingUnderlying = false;
let preferEmbeddedPlayback = false;
let browserYoutubeClientPromise: Promise<any> | null = null;
let lastLoadedYouTubeQueueKey = "";
let lastLoadedYouTubePlaylistIds: string[] = [];
let lastLoadedYouTubeQueueIndex = 0;
let pendingHiddenYouTubeQueueSync: { queue: Song[]; currentIndex: number } | null = null;
let listenerProfile = createEmptyListenerProfile();
let listenerProfileVisitorId = "";
let listenerProfileHydrationPromise: Promise<void> | null = null;
const SILENT_AUDIO_WAV = "data:audio/wav;base64,UklGRjIAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQAAAAA=";

function syncMediaSessionMetadata(track: Song) {
  if (typeof navigator === "undefined" || !("mediaSession" in navigator)) {
    return;
  }

  try {
    navigator.mediaSession.playbackState = "playing";
    navigator.mediaSession.metadata = new MediaMetadata({
      title: track.title,
      artist: track.author,
      album: "Spasiba",
      artwork: [
        { src: track.thumbnail, sizes: "96x96", type: "image/jpeg" },
        { src: track.thumbnail, sizes: "128x128", type: "image/jpeg" },
        { src: track.thumbnail, sizes: "192x192", type: "image/jpeg" },
        { src: track.thumbnail, sizes: "256x256", type: "image/jpeg" },
        { src: track.thumbnail, sizes: "384x384", type: "image/jpeg" },
        { src: track.thumbnail, sizes: "512x512", type: "image/jpeg" }
      ]
    });
  } catch {
    // Best effort metadata sync
  }
}

let backgroundPlaybackGuardUntil = 0;
let backgroundPlaybackResumeAttempts = 0;
let manualPauseRequested = false;
let pendingPlaybackErrorTimeout: number | null = null;
let deferredQueueEnhancementTimeout: number | null = null;
let cancelDeferredQueueEnhancementIdleTask: (() => void) | null = null;
let deferredQueueEnhancementTrackId: string | null = null;
let currentPlaybackSession: {
  song: Song;
  started: boolean;
  maxProgressSeconds: number;
  durationSeconds: number;
  finalized: boolean;
} | null = null;
let queuedEngagementEvents: QueuedEngagementEvent[] = [];
let engagementFlushTimeout: number | null = null;
let engagementFlushPromise: Promise<void> | null = null;
let engagementListenersAttached = false;

function isHostedRuntime() {
  if (typeof window === "undefined") {
    return false;
  }

  const { hostname } = window.location;
  return hostname !== "localhost" && hostname !== "127.0.0.1" && hostname !== "::1";
}

function callNativeBridge(action: string, ...args: unknown[]) {
  if (!shouldUseNativePlayback()) {
    return false;
  }

  try {
    const native = (window as any).SpasibaAndroid;
    if (!native) {
      return false;
    }

    switch (action) {
      case "play": {
        const [videoId, title, artist, artworkUrl] = args as [string, string, string, string];
        if (artworkUrl && typeof native.playCachedAudioWithArtwork === "function") {
          native.playCachedAudioWithArtwork(videoId, title, artist, artworkUrl);
        } else if (typeof native.playCachedAudio === "function") {
          native.playCachedAudio(videoId, title, artist);
        }
        return true;
      }
      case "pause": {
        if (typeof native.pauseCachedAudio === "function") {
          native.pauseCachedAudio();
        }
        return true;
      }
      case "resume": {
        if (typeof native.resumeCachedAudio === "function") {
          native.resumeCachedAudio();
        }
        return true;
      }
      case "stop": {
        if (typeof native.stopCachedAudio === "function") {
          native.stopCachedAudio();
        }
        return true;
      }
      case "seek": {
        if (typeof native.seekCachedAudio === "function") {
          native.seekCachedAudio(args[0] as number);
        }
        return true;
      }
      case "setVolume": {
        if (typeof native.setCachedAudioVolume === "function") {
          native.setCachedAudioVolume(args[0] as number);
        }
        return true;
      }
      case "setQueue": {
        if (typeof native.setCachedAudioQueue === "function") {
          const [queueJson, activeVideoId] = args as [string, string];
          native.setCachedAudioQueue(queueJson, activeVideoId);
        }
        return true;
      }
      case "snapshot": {
        if (typeof native.nativePlaybackSnapshot === "function") {
          const raw = native.nativePlaybackSnapshot();
          if (raw) {
            try {
              return JSON.parse(raw);
            } catch {
              return null;
            }
          }
        }
        return null;
      }
      default:
        return false;
    }
  } catch {
    return false;
  }
}

function buildNativeQueuePayload(queue: Song[], currentIndex: number) {
  const tracks = queue.map((track) => ({
    videoId: (track as any).videoId || track.id,
    title: track.title,
    artist: track.author,
    artworkUrl: track.thumbnail || ((track as any).videoId ? `https://i.ytimg.com/vi/${(track as any).videoId}/hqdefault.jpg` : "")
  }));

  return {
    queueJson: JSON.stringify(tracks),
    activeVideoId: (queue[currentIndex] as any)?.videoId || queue[currentIndex]?.id || ""
  };
}

function shouldGuardBackgroundPlayback() {
  const device = getDeviceInfo();
  return device.isIOS || device.isAndroid;
}

export function shouldUseNativePlayback() {
  return typeof window !== "undefined" && Boolean((window as any).SpasibaAndroid);
}

function armBackgroundPlaybackGuard() {
  backgroundPlaybackGuardUntil = Date.now() + 1000 * 60 * 60 * 24;
  backgroundPlaybackResumeAttempts = 0;
}

function clearBackgroundPlaybackGuard() {
  backgroundPlaybackGuardUntil = 0;
  backgroundPlaybackResumeAttempts = 0;
}

function markManualPauseRequested() {
  manualPauseRequested = true;
}

function clearManualPauseRequested() {
  manualPauseRequested = false;
}

function shouldResumeBackgroundPlayback() {
  if (manualPauseRequested) {
    return false;
  }

  if (typeof document === "undefined" || !document.hidden) {
    return false;
  }

  const device = getDeviceInfo();
  if (!device.isIOS && !device.isAndroid) {
    return false;
  }

  const { currentSong, isPlaying } = getPlayerState();
  return Boolean(currentSong && isPlaying);
}

function noteBackgroundPlaybackResumeAttempt() {
  backgroundPlaybackResumeAttempts += 1;
}

function clearPendingPlaybackError() {
  if (pendingPlaybackErrorTimeout === null || typeof window === "undefined") {
    return;
  }

  window.clearTimeout(pendingPlaybackErrorTimeout);
  pendingPlaybackErrorTimeout = null;
}

function queuePlaybackError(message: string, delayMs = 900) {
  clearPendingPlaybackError();

  if (typeof window === "undefined") {
    playerActions.setError(message);
    playerActions.setPlayback(false);
    return;
  }

  pendingPlaybackErrorTimeout = window.setTimeout(() => {
    pendingPlaybackErrorTimeout = null;
    playerActions.setError(message);
    playerActions.setPlayback(false);
  }, delayMs);
}

function clearDeferredQueueEnhancement() {
  if (typeof window !== "undefined" && deferredQueueEnhancementTimeout !== null) {
    window.clearTimeout(deferredQueueEnhancementTimeout);
  }

  deferredQueueEnhancementTimeout = null;

  if (cancelDeferredQueueEnhancementIdleTask) {
    cancelDeferredQueueEnhancementIdleTask();
    cancelDeferredQueueEnhancementIdleTask = null;
  }

  deferredQueueEnhancementTrackId = null;
}

async function runDeferredQueueEnhancement(trackId: string) {
  const stateBeforeFill = getPlayerState();

  if (stateBeforeFill.currentSong?.id !== trackId) {
    return;
  }

  const nextQueue = await fillSmartQueue(
    stateBeforeFill.currentSong,
    stateBeforeFill.queue,
    stateBeforeFill.currentIndex
  );
  const stateAfterFill = getPlayerState();

  if (stateAfterFill.currentSong?.id !== trackId) {
    return;
  }

  const queueToPrewarm = stateAfterFill.queue.length > 0 ? stateAfterFill.queue : nextQueue;
  const currentIndexToPrewarm =
    stateAfterFill.currentIndex >= 0 ? stateAfterFill.currentIndex : stateBeforeFill.currentIndex;

  if (queueToPrewarm.length === 0 || currentIndexToPrewarm < 0) {
    return;
  }

  await prewarmUpcomingTracks(queueToPrewarm, currentIndexToPrewarm);
}

function scheduleDeferredQueueEnhancement(trackId: string, delayMs = DEFERRED_QUEUE_ENHANCEMENT_DELAY_MS) {
  if (typeof window === "undefined") {
    return;
  }

  clearDeferredQueueEnhancement();
  deferredQueueEnhancementTrackId = trackId;
  deferredQueueEnhancementTimeout = window.setTimeout(() => {
    deferredQueueEnhancementTimeout = null;
    cancelDeferredQueueEnhancementIdleTask = scheduleIdleTask(() => {
      cancelDeferredQueueEnhancementIdleTask = null;
      const scheduledTrackId = deferredQueueEnhancementTrackId;
      deferredQueueEnhancementTrackId = null;

      if (!scheduledTrackId) {
        return;
      }

      void runDeferredQueueEnhancement(scheduledTrackId);
    }, DEFERRED_QUEUE_ENHANCEMENT_IDLE_TIMEOUT_MS);
  }, delayMs);
}

function shouldAllowEmbeddedFallback() {
  if (getDeviceInfo().isIOS) {
    return false;
  }
  return isHostedRuntime();
}

function shouldTryBrowserSideStreamResolution() {
  return false;
}

function getUpcomingPreloadCount() {
  const device = getDeviceInfo();
  const connectionType = device.connectionType?.toLowerCase() || "";
  const isSlowConnection = connectionType.includes("2g") || connectionType.includes("3g");

  if (device.saveData || isSlowConnection) {
    return 1;
  }

  if (device.deviceMemory !== null && device.deviceMemory <= 2) {
    return 1;
  }

  if (!device.isIOS && !device.isAndroid) {
    return 3;
  }

  if (device.deviceMemory !== null && device.deviceMemory >= 8 && device.isStandalone) {
    return 3;
  }

  return NEXT_TRACK_PRELOAD_COUNT;
}

function shouldUseEmbeddedPlayback() {
  if (getDeviceInfo().isIOS) {
    return false;
  }
  return preferEmbeddedPlayback;
}

function isGlobalStreamFailureMessage(message: string) {
  return /yt-dlp|sign in to confirm|unable to resolve a playable audio stream|not available on this machine/i.test(message);
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
    return "";
  }

  const existingLocalId = window.localStorage.getItem(VISITOR_ID_STORAGE_KEY);
  const existingCookieId = readCookieValue(VISITOR_ID_COOKIE_KEY);
  const visitorId = existingLocalId || existingCookieId || window.crypto.randomUUID();

  window.localStorage.setItem(VISITOR_ID_STORAGE_KEY, visitorId);
  writeVisitorCookie(visitorId);
  return visitorId;
}

function getListenerProfileStorageKey(visitorId: string) {
  return `${LISTENER_PROFILE_STORAGE_PREFIX}:${visitorId}`;
}

function getEngagementQueueStorageKey(visitorId: string) {
  return `${ENGAGEMENT_QUEUE_STORAGE_PREFIX}:${visitorId}`;
}

function loadStoredListenerProfile(visitorId: string) {
  if (typeof window === "undefined" || !visitorId) {
    return createEmptyListenerProfile();
  }

  try {
    const stored = window.localStorage.getItem(getListenerProfileStorageKey(visitorId));

    if (!stored) {
      return createEmptyListenerProfile();
    }

    return normalizeListenerProfile(JSON.parse(stored) as ListenerProfile);
  } catch {
    return createEmptyListenerProfile();
  }
}

function persistListenerProfile(visitorId: string, profile: ListenerProfile) {
  if (typeof window === "undefined" || !visitorId) {
    return;
  }

  window.localStorage.setItem(getListenerProfileStorageKey(visitorId), JSON.stringify(profile));
}

function loadStoredEngagementQueue(visitorId: string) {
  if (typeof window === "undefined" || !visitorId) {
    return [] as QueuedEngagementEvent[];
  }

  try {
    const stored = window.localStorage.getItem(getEngagementQueueStorageKey(visitorId));

    if (!stored) {
      return [];
    }

    const parsed = JSON.parse(stored) as QueuedEngagementEvent[] | null;
    return Array.isArray(parsed) ? parsed.slice(-MAX_PENDING_ENGAGEMENT_EVENTS) : [];
  } catch {
    return [];
  }
}

function persistQueuedEngagementEvents(visitorId: string, events: QueuedEngagementEvent[]) {
  if (typeof window === "undefined" || !visitorId) {
    return;
  }

  try {
    if (!events.length) {
      window.localStorage.removeItem(getEngagementQueueStorageKey(visitorId));
      return;
    }

    window.localStorage.setItem(getEngagementQueueStorageKey(visitorId), JSON.stringify(events.slice(-MAX_PENDING_ENGAGEMENT_EVENTS)));
  } catch {
    // Ignore local persistence failures.
  }
}

function hasListenerProfileData(profile: ListenerProfile) {
  return (
    Object.keys(profile.artists || {}).length > 0 ||
    Object.keys(profile.tracks || {}).length > 0 ||
    Object.keys(profile.signals || {}).length > 0
  );
}

function setListenerProfile(nextProfile: ListenerProfile, visitorId = listenerProfileVisitorId) {
  listenerProfile = normalizeListenerProfile(nextProfile);

  if (visitorId) {
    listenerProfileVisitorId = visitorId;
    persistListenerProfile(visitorId, listenerProfile);
    void writeListenerProfileSnapshot(listenerProfile, visitorId);
  }
}

function getQueueContext(options?: PlaybackQueueContext) {
  return {
    queueSource: options?.queueSource ?? "default",
    queueSourceLabel: options?.queueSourceLabel ?? null,
    playlistBaseTrackIds: options?.playlistBaseTrackIds ?? [],
    playlistMixEnabled: options?.playlistMixEnabled ?? false
  } satisfies Required<PlaybackQueueContext>;
}

function hydrateListenerProfile() {
  const visitorId = ensureVisitorId();

  if (!visitorId) {
    return Promise.resolve();
  }

  if (listenerProfileVisitorId !== visitorId) {
    listenerProfileVisitorId = visitorId;
    setListenerProfile(loadStoredListenerProfile(visitorId), visitorId);
    queuedEngagementEvents = loadStoredEngagementQueue(visitorId);
  }

  if (!listenerProfileHydrationPromise) {
    if (queuedEngagementEvents.length) {
      listenerProfileHydrationPromise = flushQueuedEngagementEvents()
        .catch(() => {
          // Keep local queue until the next open.
        })
        .finally(() => {
          listenerProfileHydrationPromise = null;
        });
    } else if (!hasListenerProfileData(listenerProfile)) {
      listenerProfileHydrationPromise = readListenerProfileSnapshot(visitorId)
        .then(async (storedSnapshot) => {
          if (storedSnapshot && hasListenerProfileData(storedSnapshot)) {
            setListenerProfile(pickNewerListenerProfile(listenerProfile, storedSnapshot), visitorId);
            return;
          }

          const response = await fetch("/api/engagement", {
            method: "GET",
            credentials: "same-origin",
            cache: "no-store",
            headers: {
              Accept: "application/json"
            }
          });

          if (!response.ok) {
            throw new Error("Unable to load listener profile.");
          }

          const payload = (await response.json().catch(() => null)) as { profile?: ListenerProfile | null } | null;
          const serverProfile = normalizeListenerProfile(payload?.profile);
          setListenerProfile(pickNewerListenerProfile(listenerProfile, serverProfile), visitorId);
        })
        .catch(() => {
          // The local profile is enough to keep queueing fast.
        })
        .finally(() => {
          listenerProfileHydrationPromise = null;
        });
    } else {
      listenerProfileHydrationPromise = Promise.resolve().finally(() => {
        listenerProfileHydrationPromise = null;
      });
    }
  }

  return listenerProfileHydrationPromise;
}

function getTrackSignals(track: Song) {
  return Array.from(detectTrackSignals(track));
}

function getPlaybackProgressFraction(progressSeconds: number, durationSeconds: number) {
  if (!durationSeconds || durationSeconds <= 0) {
    return null;
  }

  return clamp(progressSeconds / durationSeconds, 0, 1);
}

function ensurePlaybackSession(track: Song) {
  if (!currentPlaybackSession || currentPlaybackSession.song.id !== track.id) {
    currentPlaybackSession = {
      song: track,
      started: false,
      maxProgressSeconds: 0,
      durationSeconds: track.durationSeconds || 0,
      finalized: false
    };
  }

  return currentPlaybackSession;
}

function notePlaybackProgress(currentTime: number, duration: number) {
  if (!currentPlaybackSession) {
    return;
  }

  if (duration > 0) {
    currentPlaybackSession.durationSeconds = Math.max(currentPlaybackSession.durationSeconds, duration);
  }

  if (currentTime > 0) {
    currentPlaybackSession.maxProgressSeconds = Math.max(currentPlaybackSession.maxProgressSeconds, currentTime);
  }
}

function clearEngagementFlushTimer() {
  if (engagementFlushTimeout === null || typeof window === "undefined") {
    return;
  }

  window.clearTimeout(engagementFlushTimeout);
  engagementFlushTimeout = null;
}

function scheduleEngagementFlush(delayMs = ENGAGEMENT_BATCH_FLUSH_DELAY_MS) {
  if (typeof window === "undefined") {
    return;
  }

  clearEngagementFlushTimer();
  engagementFlushTimeout = window.setTimeout(() => {
    engagementFlushTimeout = null;
    void flushQueuedEngagementEvents();
  }, delayMs);
}

function ensureEngagementListeners() {
  if (engagementListenersAttached || typeof window === "undefined") {
    return;
  }

  engagementListenersAttached = true;

  const handleLifecycleFlush = () => {
    persistQueuedEngagementEvents(listenerProfileVisitorId, queuedEngagementEvents);
  };

  document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
      handleLifecycleFlush();
    }
  });
  window.addEventListener("pagehide", handleLifecycleFlush);
  window.addEventListener("beforeunload", handleLifecycleFlush);
}

function mergeServerListenerProfile(profile: ListenerProfile | null | undefined, visitorId: string) {
  if (!profile) {
    return;
  }

  setListenerProfile(pickNewerListenerProfile(listenerProfile, profile), visitorId);
}

async function flushQueuedEngagementEvents(useKeepAlive = false) {
  const visitorId = ensureVisitorId();

  if (!visitorId || !queuedEngagementEvents.length) {
    return;
  }

  if (engagementFlushPromise) {
    await engagementFlushPromise;

    if (!queuedEngagementEvents.length) {
      return;
    }
  }

  clearEngagementFlushTimer();

  const batch = queuedEngagementEvents.slice(0, ENGAGEMENT_BATCH_LIMIT);
  queuedEngagementEvents = queuedEngagementEvents.slice(batch.length);
  persistQueuedEngagementEvents(visitorId, queuedEngagementEvents);

  engagementFlushPromise = fetch("/api/engagement", {
    method: "POST",
    credentials: "same-origin",
    keepalive: useKeepAlive || batch.length <= 4,
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json"
    },
    body: JSON.stringify({
      events: batch,
      profile: listenerProfile
    })
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error("Unable to sync listener profile.");
      }

      const payload = (await response.json().catch(() => null)) as { profile?: ListenerProfile | null } | null;
      mergeServerListenerProfile(payload?.profile, visitorId);
    })
    .catch(() => {
      queuedEngagementEvents = [...batch, ...queuedEngagementEvents].slice(-MAX_PENDING_ENGAGEMENT_EVENTS);
      persistQueuedEngagementEvents(visitorId, queuedEngagementEvents);
    })
    .finally(() => {
      engagementFlushPromise = null;
    });

  await engagementFlushPromise;

  if (queuedEngagementEvents.length) {
    scheduleEngagementFlush(queuedEngagementEvents.length >= ENGAGEMENT_BATCH_LIMIT ? 120 : ENGAGEMENT_BATCH_FLUSH_DELAY_MS);
  }
}

function recordEngagementAction(song: Song, action: EngagementAction, progress: number | null = null) {
  const visitorId = ensureVisitorId();
  const signals = getTrackSignals(song);
  const nextProfile = applyEngagementToListenerProfile(listenerProfile, song, action, signals);

  setListenerProfile(nextProfile, visitorId);
  ensureEngagementListeners();

  if (action === "play_start") {
    recordLocalHistorySong(song);
  } else if (action === "like") {
    updateLocalLikedSongs(song, true);
  } else if (action === "unlike") {
    updateLocalLikedSongs(song, false);
  }

  if (!NETWORK_ENGAGEMENT_ACTIONS.has(action)) {
    return;
  }

  recordRecommendationEvent(song, action, signals);

  const nextEvent = {
    action,
    song,
    progress,
    signals
  };
  const lastQueuedEvent = queuedEngagementEvents[queuedEngagementEvents.length - 1];

  queuedEngagementEvents =
    lastQueuedEvent &&
    lastQueuedEvent.action === nextEvent.action &&
    lastQueuedEvent.song.id === nextEvent.song.id &&
    (lastQueuedEvent.progress ?? null) === (nextEvent.progress ?? null)
      ? queuedEngagementEvents
      : [...queuedEngagementEvents, nextEvent].slice(-MAX_PENDING_ENGAGEMENT_EVENTS);
  persistQueuedEngagementEvents(visitorId, queuedEngagementEvents);

  if (queuedEngagementEvents.length >= ENGAGEMENT_BATCH_LIMIT) {
    void flushQueuedEngagementEvents();
    return;
  }

  scheduleEngagementFlush();
}

function markPlaybackStarted(song: Song | null) {
  if (!song) {
    return;
  }

  const session = ensurePlaybackSession(song);

  if (session.started) {
    return;
  }

  session.started = true;
  recordEngagementAction(song, "play_start", getPlaybackProgressFraction(session.maxProgressSeconds, session.durationSeconds));
}

function classifySkipAction(progressFraction: number | null): EngagementAction {
  if (progressFraction === null || progressFraction < SKIP_EARLY_THRESHOLD) {
    return "skip_early";
  }

  if (progressFraction < SKIP_MID_THRESHOLD) {
    return "skip_mid";
  }

  return "skip_late";
}

function finalizePlaybackSession(mode: "switch" | "ended") {
  const session = currentPlaybackSession;

  if (!session || session.finalized || !session.started) {
    return;
  }

  const progressFraction = getPlaybackProgressFraction(session.maxProgressSeconds, session.durationSeconds);
  const action = mode === "ended" || (progressFraction ?? 0) >= PLAYBACK_COMPLETE_THRESHOLD ? "complete" : classifySkipAction(progressFraction);

  recordEngagementAction(session.song, action, progressFraction);
  currentPlaybackSession = null;
}

function preparePlaybackTransition(nextTrack: Song | null) {
  clearPendingPlaybackError();
  pendingHiddenYouTubeQueueSync = null;
  const activeTrack = currentPlaybackSession?.song || getPlayerState().currentSong;

  if (activeTrack && nextTrack && activeTrack.id !== nextTrack.id) {
    finalizePlaybackSession("switch");
  }

  if (nextTrack) {
    ensurePlaybackSession(nextTrack);
  }
}

function stopYouTubeSync() {
  if (youtubeSyncInterval === null) {
    return;
  }

  window.clearInterval(youtubeSyncInterval);
  youtubeSyncInterval = null;
}

function syncYouTubeTiming(flushPendingQueueSync = true) {
  if (!hiddenYouTubePlayer || activePlaybackEngine !== "youtube") {
    return;
  }

  if (visibleVideoModeActive) {
    return;
  }

  const duration = hiddenYouTubePlayer.getDuration() || 0;
  const currentTime = hiddenYouTubePlayer.getCurrentTime() || 0;
  const bufferedFraction = hiddenYouTubePlayer.getVideoLoadedFraction?.() || 0;
  playerActions.setTiming(currentTime, duration, duration > 0 ? duration * bufferedFraction : 0);
  notePlaybackProgress(currentTime, duration);
  if (flushPendingQueueSync) {
    void flushPendingHiddenYouTubeQueueSync();
  }
}

function startYouTubeSync() {
  if (youtubeSyncInterval !== null) {
    return;
  }

  syncYouTubeTiming();
  youtubeSyncInterval = window.setInterval(() => {
    syncYouTubeTiming();
  }, 250);
}

function stopHtmlAudioPlayback() {
  const audio = getAudio();
  audio.pause();
  audio.removeAttribute("src");
  audio.load();
  stopProgressLoop();
}

function pauseYouTubePlayback() {
  hiddenYouTubePlayer?.pauseVideo();
  stopYouTubeSync();
}

function muteHiddenYouTubeForVisibleVideoMode() {
  if (!hiddenYouTubePlayer) {
    return;
  }

  hiddenYouTubeMutedForVisibleVideo = true;

  if (hiddenYouTubePlayer.mute) {
    hiddenYouTubePlayer.mute();
    return;
  }

  hiddenYouTubePlayer.setVolume(0);
}

function muteHtmlAudioForVisibleVideoMode() {
  const audio = getAudio();
  htmlAudioMutedForVisibleVideo = true;
  audio.muted = true;
}

function restoreHiddenYouTubeAfterVisibleVideoMode() {
  if (!hiddenYouTubeMutedForVisibleVideo || !hiddenYouTubePlayer) {
    return;
  }

  hiddenYouTubeMutedForVisibleVideo = false;
  hiddenYouTubePlayer.unMute?.();
  hiddenYouTubePlayer.setVolume(getPlayerState().volume * 100);
}

function restoreHtmlAudioAfterVisibleVideoMode() {
  if (!htmlAudioMutedForVisibleVideo) {
    return;
  }

  htmlAudioMutedForVisibleVideo = false;
  getAudio().muted = false;
}

function restoreUnderlyingPlaybackAfterVisibleVideoMode() {
  visibleVideoModeActive = false;
  visibleVideoModeSuspendingUnderlying = false;
  restoreHiddenYouTubeAfterVisibleVideoMode();
  restoreHtmlAudioAfterVisibleVideoMode();
}

function resetPlaybackTimingState() {
  playerActions.setTiming(0, 0, 0);
}

function rememberLoadedYouTubeQueue(queue: Song[], currentIndex: number) {
  const playlistIds = queue.map((track) => track.videoId).filter(Boolean);
  const safeIndex = clamp(currentIndex, 0, Math.max(playlistIds.length - 1, 0));
  lastLoadedYouTubePlaylistIds = playlistIds;
  lastLoadedYouTubeQueueIndex = safeIndex;
  lastLoadedYouTubeQueueKey = `${playlistIds.join(",")}::${safeIndex}`;
}

function rememberLoadedYouTubeQueueIndex(currentIndex: number) {
  lastLoadedYouTubeQueueIndex = clamp(currentIndex, 0, Math.max(lastLoadedYouTubePlaylistIds.length - 1, 0));
  lastLoadedYouTubeQueueKey = `${lastLoadedYouTubePlaylistIds.join(",")}::${lastLoadedYouTubeQueueIndex}`;
}

function syncCurrentSongFromYouTubePlayer() {
  if (!hiddenYouTubePlayer || activePlaybackEngine !== "youtube") {
    return;
  }

  const videoId = hiddenYouTubePlayer.getVideoData?.().video_id?.trim();

  if (!videoId) {
    return;
  }

  const { queue, currentIndex, currentSong } = getPlayerState();
  const nextIndex = queue.findIndex((track) => track.videoId === videoId || track.id === videoId);

  if (nextIndex < 0) {
    return;
  }

  const nextTrack = queue[nextIndex];

  if (!nextTrack || (currentSong?.id === nextTrack.id && currentIndex === nextIndex)) {
    return;
  }

  if (lastLoadedYouTubePlaylistIds[nextIndex] === videoId) {
    rememberLoadedYouTubeQueueIndex(nextIndex);
  }

  preparePlaybackTransition(nextTrack);

  const nextPlaybackQueue = buildPlaybackQueue(nextTrack, queue, nextIndex);

  resetPlaybackTimingState();
  setPlayerState((previous) => ({
    ...previous,
    currentSong: nextTrack,
    queue: nextPlaybackQueue.queue,
    currentIndex: nextPlaybackQueue.currentIndex,
    error: null
  }));
  playerActions.rememberSessionTrack(nextTrack);

  scheduleDeferredQueueEnhancement(nextTrack.id);
}

function loadYouTubePlaybackQueue(player: YouTubePlayer, queue: Song[], currentIndex: number, startSeconds = 0) {
  const playlistIds = queue.map((track) => track.videoId).filter(Boolean);
  const safeIndex = clamp(currentIndex, 0, Math.max(playlistIds.length - 1, 0));
  pendingHiddenYouTubeQueueSync = null;
  rememberLoadedYouTubeQueue(queue, safeIndex);

  if (playlistIds.length > 1 && typeof player.loadPlaylist === "function") {
    player.loadPlaylist({
      playlist: playlistIds,
      index: safeIndex,
      startSeconds
    });
    return;
  }

  const activeTrack = queue[safeIndex];

  if (activeTrack) {
    player.loadVideoById(activeTrack.videoId, startSeconds);
  }
}

function isHiddenYouTubeQueueReady(queue: Song[], currentIndex: number) {
  if (pendingHiddenYouTubeQueueSync) {
    return false;
  }

  const safeIndex = clamp(currentIndex, 0, Math.max(queue.length - 1, 0));
  const expectedVideoId = queue[safeIndex]?.videoId || "";
  const loadedVideoId = lastLoadedYouTubePlaylistIds[safeIndex] || "";

  if (!expectedVideoId || !loadedVideoId) {
    return false;
  }

  return expectedVideoId === loadedVideoId;
}

async function flushPendingHiddenYouTubeQueueSync(force = false) {
  if (!pendingHiddenYouTubeQueueSync || !hiddenYouTubePlayer || activePlaybackEngine !== "youtube") {
    return false;
  }

  if (!force && getPlayerState().isPlaying) {
    return false;
  }

  const currentTime = hiddenYouTubePlayer.getCurrentTime() || 0;

  const nextSync = pendingHiddenYouTubeQueueSync;
  pendingHiddenYouTubeQueueSync = null;

  loadYouTubePlaybackQueue(hiddenYouTubePlayer, nextSync.queue, nextSync.currentIndex, currentTime);

  if (getPlayerState().isPlaying) {
    hiddenYouTubePlayer.playVideo();
  } else {
    hiddenYouTubePlayer.pauseVideo();
  }

  return true;
}

function syncHiddenYouTubeQueueWithState() {
  if (!hiddenYouTubePlayer || activePlaybackEngine !== "youtube") {
    return;
  }

  const { queue, currentIndex, currentSong, isPlaying } = getPlayerState();

  if (!currentSong || !queue.length) {
    return;
  }

  const playlistIds = queue.map((track) => track.videoId).filter(Boolean);
  const safeIndex = clamp(currentIndex, 0, Math.max(playlistIds.length - 1, 0));
  const nextQueueKey = `${playlistIds.join(",")}::${safeIndex}`;

  if (nextQueueKey === lastLoadedYouTubeQueueKey) {
    return;
  }

  const currentVideoId = hiddenYouTubePlayer.getVideoData?.().video_id?.trim() || "";
  const currentLoadedVideoId = lastLoadedYouTubePlaylistIds[lastLoadedYouTubeQueueIndex] || "";
  const nextVideoId = playlistIds[safeIndex] || "";
  const sharedPrefixLength = Math.min(lastLoadedYouTubePlaylistIds.length, playlistIds.length);
  const queueChangedBeforeTail = Array.from({ length: sharedPrefixLength }).some(
    (_, index) => lastLoadedYouTubePlaylistIds[index] !== playlistIds[index]
  );
  const queueShrank = playlistIds.length < lastLoadedYouTubePlaylistIds.length;
  const activeIndexChanged = safeIndex !== lastLoadedYouTubeQueueIndex;
  const activeTrackChanged = Boolean(nextVideoId && currentVideoId && currentVideoId !== nextVideoId);
  const loadedTrackChanged = Boolean(nextVideoId && currentLoadedVideoId && currentLoadedVideoId !== nextVideoId);
  const tailOnlyGrowth = !queueChangedBeforeTail && !queueShrank && playlistIds.length > lastLoadedYouTubePlaylistIds.length;

  if (
    tailOnlyGrowth &&
    !activeIndexChanged &&
    !activeTrackChanged &&
    !loadedTrackChanged &&
    getPlayerState().isPlaying
  ) {
    pendingHiddenYouTubeQueueSync = {
      queue: [...queue],
      currentIndex: safeIndex
    };
    return;
  }

  const currentTime = hiddenYouTubePlayer.getCurrentTime() || 0;
  loadYouTubePlaybackQueue(hiddenYouTubePlayer, queue, safeIndex, currentTime);

  if (isPlaying) {
    hiddenYouTubePlayer.playVideo();
  } else {
    hiddenYouTubePlayer.pauseVideo();
  }
}

function getOrCreateHiddenYouTubeMount() {
  if (hiddenYouTubePlayerMount) {
    return hiddenYouTubePlayerMount;
  }

  const mount = document.createElement("div");
  mount.id = "spasiba-youtube-audio-host";
  mount.setAttribute("aria-hidden", "true");
  mount.style.position = "fixed";
  mount.style.width = "1px";
  mount.style.height = "1px";
  mount.style.opacity = "0";
  mount.style.pointerEvents = "none";
  mount.style.left = "-9999px";
  mount.style.top = "0";
  document.body.appendChild(mount);
  hiddenYouTubePlayerMount = mount;
  return mount;
}

async function ensureHiddenYouTubePlayer() {
  if (hiddenYouTubePlayer) {
    return hiddenYouTubePlayer;
  }

  if (hiddenYouTubePlayerPromise) {
    return hiddenYouTubePlayerPromise;
  }

  hiddenYouTubePlayerPromise = loadYouTubeIframeApi().then(
    () =>
      new Promise<YouTubePlayer>((resolve, reject) => {
        if (!window.YT?.Player) {
          reject(new Error("YouTube playback is not available."));
          return;
        }

        const mount = getOrCreateHiddenYouTubeMount();
        const player = new window.YT.Player(mount, {
          playerVars: {
            autoplay: 0,
            controls: 0,
            playsinline: 1,
            rel: 0,
            modestbranding: 1,
            enablejsapi: 1,
            origin: window.location.origin
          },
          events: {
            onReady: (event) => {
              hiddenYouTubePlayer = event.target;
              event.target.setVolume(getPlayerState().volume * 100);
              resolve(event.target);
            },
            onStateChange: (event) => {
              if (activePlaybackEngine !== "youtube") {
                return;
              }

              const playerState = window.YT?.PlayerState;

              if (!playerState) {
                return;
              }

              if (event.data === playerState.PLAYING) {
                clearPendingPlaybackError();
                clearBackgroundPlaybackGuard();
                clearManualPauseRequested();
                syncCurrentSongFromYouTubePlayer();
                markPlaybackStarted(getPlayerState().currentSong);
                playerActions.setPlayback(true);
                playerActions.setLoading(false);
                playerActions.setError(null);
                startYouTubeSync();
                return;
              }

              if (event.data === playerState.PAUSED) {
                if (visibleVideoModeSuspendingUnderlying) {
                  stopYouTubeSync();
                  return;
                }

                if (shouldResumeBackgroundPlayback()) {
                  noteBackgroundPlaybackResumeAttempt();
                  playerActions.setLoading(true);
                  event.target.playVideo();
                  return;
                }

                clearPendingPlaybackError();
                syncCurrentSongFromYouTubePlayer();
                playerActions.setPlayback(false);
                playerActions.setLoading(false);
                syncYouTubeTiming(false);
                stopYouTubeSync();
                void flushPendingHiddenYouTubeQueueSync(true);
                return;
              }

              if (event.data === playerState.BUFFERING) {
                clearPendingPlaybackError();
                syncCurrentSongFromYouTubePlayer();
                playerActions.setLoading(true);
                startYouTubeSync();
                return;
              }

              if (event.data === playerState.ENDED) {
                finalizePlaybackSession("ended");
                syncCurrentSongFromYouTubePlayer();
                playerActions.setPlayback(false);
                stopYouTubeSync();

                const { queue, currentIndex } = getPlayerState();
                const nextTrack = queue[currentIndex + 1];

                if (nextTrack) {
                  if (!isHiddenYouTubeQueueReady(queue, currentIndex + 1)) {
                    playerActions.setLoading(true);
                    void audioController.playNext();
                    return;
                  }

                  playerActions.setLoading(true);
                  return;
                }

                playerActions.setLoading(false);
                return;
              }

              syncYouTubeTiming();
            },
            onError: () => {
              if (activePlaybackEngine !== "youtube") {
                return;
              }

              stopYouTubeSync();
              queuePlaybackError("Playback could not start. Try another track.");
            }
          }
        });
      })
  );

  return hiddenYouTubePlayerPromise.finally(() => {
    hiddenYouTubePlayerPromise = null;
  });
}

function warmEmbeddedPlayback() {
  if (!shouldUseEmbeddedPlayback()) {
    return;
  }

  void ensureHiddenYouTubePlayer().catch(() => {
    // Warmup is best-effort only.
  });
}

function getArtistKey(author: string) {
  return normalizeSongTitle(getPrimaryArtistName(author) || author);
}

function detectTrackSignals(track: Song) {
  const text = `${track.title} ${track.author}`.toLowerCase();
  const signals = new Set<QueueSignal>();

  if (PUNJABI_PATTERN.test(text)) {
    signals.add("punjabi");
  } else if (BOLLYWOOD_PATTERN.test(text)) {
    signals.add("bollywood");
  } else {
    signals.add("english-global");
  }

  if (LOFI_PATTERN.test(text)) {
    signals.add("lofi-chill");
  }

  if (LOVE_PATTERN.test(text)) {
    signals.add("love-romantic");
  }

  if (SAD_PATTERN.test(text)) {
    signals.add("sad-emotional");
  }

  if (ENERGY_PATTERN.test(text)) {
    signals.add("high-energy-workout");
  }

  if (INDIE_PATTERN.test(text)) {
    signals.add("indie-non-film");
  }

  if (TRENDING_PATTERN.test(text) || track.views > 100_000_000) {
    signals.add("trending-viral");
  }

  return signals;
}

function inferQueueSignals(reference: Song, queue: Song[]) {
  const counts = new Map<QueueSignal, number>();
  const candidateTracks = [reference, ...queue.filter((candidate) => candidate.id !== reference.id).slice(0, 6)];

  for (const candidate of candidateTracks) {
    for (const signal of detectTrackSignals(candidate)) {
      counts.set(signal, (counts.get(signal) || 0) + 1);
    }
  }

  const inferredSignals = new Set<QueueSignal>(detectTrackSignals(reference));
  const rankedSignals = Array.from(counts.entries())
    .sort((left, right) => right[1] - left[1])
    .map(([signal]) => signal);

  for (const signal of rankedSignals) {
    if ((counts.get(signal) || 0) >= 2 || inferredSignals.size < 2) {
      inferredSignals.add(signal);
    }

    if (inferredSignals.size >= 3) {
      break;
    }
  }

  return Array.from(inferredSignals);
}

function createRelatedQueueCacheKey(track: Song, signals: QueueSignal[]) {
  return `${track.id}:${signals.slice().sort().join(",")}`;
}

function getSignalQueries(signals: QueueSignal[]) {
  return signals.flatMap((signal) => SIGNAL_QUERY_TEMPLATES[signal] || []);
}

function buildQueueSearchBankQueries(track: Song, signals: QueueSignal[]) {
  const primaryArtist = getPrimaryArtistName(track.author) || track.author;
  const cleanedTitle = getMeaningfulSongTokens(track.title).slice(0, 4).join(" ");
  const artistQueryBase = primaryArtist.trim();
  const titleQueryBase = [artistQueryBase, cleanedTitle].filter(Boolean).join(" ").trim();
  const signalQueries = getSignalQueries(signals);

  return Array.from(
    new Set(
      [
        [artistQueryBase, "official audio"].filter(Boolean).join(" "),
        [artistQueryBase, "top tracks official audio"].filter(Boolean).join(" "),
        signalQueries[0],
        signalQueries[1],
        [titleQueryBase, "official audio"].filter(Boolean).join(" ")
      ]
        .map((query) => query?.trim())
        .filter(Boolean)
    )
  ).slice(0, 4);
}

function getTrackIdentityKey(track: Song) {
  const artistKey = getArtistKey(track.author);
  const titleKey = normalizeSongTitle(track.title);
  return [artistKey, titleKey].filter(Boolean).join("::");
}

function countSharedTokens(left: string[], right: string[]) {
  const rightTokens = new Set(right);
  let shared = 0;

  for (const token of new Set(left)) {
    if (rightTokens.has(token)) {
      shared += 1;
    }
  }

  return shared;
}

function getTitleSimilarity(leftTitle: string, rightTitle: string) {
  const leftTokens = getMeaningfulSongTokens(leftTitle);
  const rightTokens = getMeaningfulSongTokens(rightTitle);

  if (!leftTokens.length || !rightTokens.length) {
    return 0;
  }

  const shared = countSharedTokens(leftTokens, rightTokens);
  return shared / Math.min(leftTokens.length, rightTokens.length);
}

function isLikelySameSong(reference: Song, candidate: Song) {
  if (reference.id === candidate.id || reference.videoId === candidate.videoId) {
    return true;
  }

  const referenceTitleKey = normalizeSongTitle(reference.title);
  const candidateTitleKey = normalizeSongTitle(candidate.title);

  if (!referenceTitleKey || !candidateTitleKey) {
    return false;
  }

  if (referenceTitleKey === candidateTitleKey) {
    return true;
  }

  const sameArtist = getArtistKey(reference.author) === getArtistKey(candidate.author);

  if (sameArtist && (referenceTitleKey.includes(candidateTitleKey) || candidateTitleKey.includes(referenceTitleKey))) {
    return true;
  }

  const similarity = getTitleSimilarity(reference.title, candidate.title);
  return sameArtist ? similarity >= 0.7 : similarity >= 0.9;
}

function scoreRelatedCandidate(reference: Song, candidate: Song) {
  const sameArtist = getArtistKey(reference.author) === getArtistKey(candidate.author);
  const referenceSignals = detectTrackSignals(reference);
  const candidateSignals = detectTrackSignals(candidate);
  const titleSimilarity = getTitleSimilarity(reference.title, candidate.title);
  const sharedSignals = Array.from(referenceSignals).filter((signal) => candidateSignals.has(signal)).length;
  let score = 12 + sharedSignals * 16;

  if (sameArtist) {
    score -= 6;
  }

  score += Math.min(22, Math.log10(Math.max(candidate.views || 1, 1)) * 4);

  if (/\btopic\b/i.test(candidate.author)) {
    score += 8;
  }

  if (/\bofficial\b|\baudio\b/i.test(candidate.title)) {
    score += 6;
  }

  if (candidateSignals.has("trending-viral")) {
    score += 4;
  }

  if (TITLE_VARIANT_PATTERN.test(candidate.title) || TITLE_VARIANT_PATTERN.test(candidate.author)) {
    score -= 35;
  }

  if (GENERIC_COMPILATION_PATTERN.test(candidate.title) || GENERIC_COMPILATION_PATTERN.test(candidate.author)) {
    score -= 55;
  }

  if (titleSimilarity > 0.45) {
    score -= 20;
  } else if (titleSimilarity > 0.25) {
    score -= 10;
  }

  return score;
}

function getRecommendedQueueDurationCap(reference: Song | null) {
  if (!reference?.durationSeconds) {
    return MAX_QUEUE_TRACK_DURATION_SECONDS;
  }

  return Math.max(210, Math.min(MAX_QUEUE_TRACK_DURATION_SECONDS, Math.round(reference.durationSeconds * QUEUE_DURATION_MULTIPLIER)));
}

function isQueueableTrack(candidate: Song, reference: Song | null = null) {
  const noisyText = `${candidate.title} ${candidate.author}`;

  if (LOW_QUALITY_QUEUE_PATTERN.test(noisyText)) {
    return false;
  }

  if (GENERIC_COMPILATION_PATTERN.test(candidate.title) || GENERIC_COMPILATION_PATTERN.test(candidate.author)) {
    return false;
  }

  if (candidate.durationSeconds > 0 && candidate.durationSeconds < MIN_QUEUE_TRACK_DURATION_SECONDS) {
    return false;
  }

  if (candidate.durationSeconds > getRecommendedQueueDurationCap(reference)) {
    return false;
  }

  const normalizedTitle = normalizeSongTitle(candidate.title);
  const normalizedAuthor = normalizeSongTitle(candidate.author);
  const titleWordCount = normalizedTitle ? normalizedTitle.split(" ").length : 0;
  const hasMusicIdentity = MUSIC_IDENTITY_PATTERN.test(noisyText);

  if (!hasMusicIdentity && titleWordCount > 8) {
    return false;
  }

  if (!hasMusicIdentity && normalizedAuthor.split(" ").length > 6) {
    return false;
  }

  return true;
}

function appendDistinctTracks(reference: Song, baseQueue: Song[], additions: Song[], limit = MAX_PLAYBACK_QUEUE_LENGTH) {
  const nextQueue = [...baseQueue];
  const seenTrackIds = new Set(baseQueue.map((track) => track.id));
  const seenIdentityKeys = new Set(baseQueue.map(getTrackIdentityKey).filter(Boolean));
  const artistCounts = new Map<string, number>();

  for (const track of baseQueue) {
    const artistKey = getArtistKey(track.author);

    if (!artistKey) {
      continue;
    }

    artistCounts.set(artistKey, (artistCounts.get(artistKey) || 0) + 1);
  }

  for (const candidate of additions) {
    const hasEquivalentTrack = nextQueue.some(
      (queuedTrack) => queuedTrack.id === candidate.id || isLikelySameSong(queuedTrack, candidate)
    );

    if (
      !isQueueableTrack(candidate, reference) ||
      seenTrackIds.has(candidate.id) ||
      hasEquivalentTrack ||
      isLikelySameSong(reference, candidate)
    ) {
      continue;
    }

    const identityKey = getTrackIdentityKey(candidate);

    if (identityKey && seenIdentityKeys.has(identityKey)) {
      continue;
    }

    const artistKey = getArtistKey(candidate.author);

    if (artistKey && (artistCounts.get(artistKey) || 0) >= SMART_QUEUE_ARTIST_CAP) {
      continue;
    }

    nextQueue.push(candidate);
    seenTrackIds.add(candidate.id);

    if (identityKey) {
      seenIdentityKeys.add(identityKey);
    }

    if (artistKey) {
      artistCounts.set(artistKey, (artistCounts.get(artistKey) || 0) + 1);
    }

    if (nextQueue.length >= limit) {
      break;
    }
  }

  return nextQueue;
}

function rankRelatedSongs(reference: Song, candidates: Song[], limit: number, baseQueue: Song[] = [], appearanceCounts = new Map<string, number>()) {
  const rankedCandidates = candidates
    .filter((candidate) => isQueueableTrack(candidate, reference))
    .filter((candidate) => !isLikelySameSong(reference, candidate))
    .sort((left, right) => {
      const rightScore = scoreRelatedCandidate(reference, right) + (appearanceCounts.get(right.id) || 1) * 6;
      const leftScore = scoreRelatedCandidate(reference, left) + (appearanceCounts.get(left.id) || 1) * 6;
      return rightScore - leftScore;
    });

  return appendDistinctTracks(reference, baseQueue, rankedCandidates, baseQueue.length + limit).slice(baseQueue.length, baseQueue.length + limit);
}

function dedupeSongs(tracks: Song[]) {
  const seenTrackIds = new Set<string>();

  return tracks.filter((track) => {
    if (seenTrackIds.has(track.id)) {
      return false;
    }

    seenTrackIds.add(track.id);
    return true;
  });
}

function buildPlaybackQueue(track: Song, queue: Song[], currentIndex: number) {
  const safeQueue = queue.length > 0 ? dedupeSongs(queue) : [track];
  let nextQueue = safeQueue.length > 0 ? safeQueue : [track];
  let nextCurrentIndex =
    currentIndex >= 0 && currentIndex < nextQueue.length ? currentIndex : nextQueue.findIndex((candidate) => candidate.id === track.id);

  if (nextCurrentIndex < 0) {
    nextQueue = [...nextQueue, track];
    nextCurrentIndex = nextQueue.length - 1;
  } else if (nextQueue[nextCurrentIndex]?.id !== track.id) {
    nextCurrentIndex = nextQueue.findIndex((candidate) => candidate.id === track.id);

    if (nextCurrentIndex < 0) {
      nextQueue = [...nextQueue, track];
      nextCurrentIndex = nextQueue.length - 1;
    }
  }

  if (nextQueue.length > MAX_PLAYBACK_QUEUE_LENGTH) {
    const trimStart = Math.max(0, nextCurrentIndex - PREVIOUS_CONTEXT_LIMIT);
    const desiredEnd = Math.min(nextQueue.length, trimStart + MAX_PLAYBACK_QUEUE_LENGTH);
    nextQueue = nextQueue.slice(trimStart, desiredEnd);
    nextCurrentIndex = Math.max(0, nextQueue.findIndex((candidate) => candidate.id === track.id));
  }

  return {
    queue: nextQueue.length > 0 ? nextQueue : [track],
    currentIndex: nextCurrentIndex
  };
}

async function fetchRelatedSongs(track: Song, queue: Song[], limit = UPCOMING_QUEUE_TARGET - 1) {
  const signals = inferQueueSignals(track, queue);
  const cacheKey = createRelatedQueueCacheKey(track, signals);
  const cached = relatedQueueCache.get(cacheKey);

  if (cached) {
    touchMapEntry(relatedQueueCache, cacheKey, cached);
    return cached.slice(0, limit);
  }

  const pending = pendingRelatedQueueRequests.get(cacheKey);

  if (pending) {
    return pending.then((tracks) => tracks.slice(0, limit));
  }

  const persisted = await readPersistentSearchResponse(cacheKey, true);

  if (persisted?.results?.length) {
    touchMapEntry(relatedQueueCache, cacheKey, persisted.results);
    pruneMapEntries(relatedQueueCache, RELATED_QUEUE_CACHE_LIMIT);
    return persisted.results.slice(0, limit);
  }

  const youtubeFetchLimit = Math.max(SMART_QUEUE_PRIMARY_FETCH_LIMIT, limit + 8);
  const request = fetchSearchResponseWithBudget({
    relatedTo: track.videoId,
    limit: youtubeFetchLimit,
    purpose: "related"
  })
    .then((payload) => payload.results)
    .then(async (youtubeRelated) => {
      const cleanedRelated = appendDistinctTracks(track, [], youtubeRelated, youtubeFetchLimit);
      const minYoutubeResultThreshold = Math.min(limit, Math.max(4, Math.ceil(limit * 0.7)));

      if (cleanedRelated.length >= limit || cleanedRelated.length >= minYoutubeResultThreshold) {
        touchMapEntry(relatedQueueCache, cacheKey, cleanedRelated);
        pruneMapEntries(relatedQueueCache, RELATED_QUEUE_CACHE_LIMIT);
        void cachePersistentSearchResponse(
          cacheKey,
          {
            query: track.title,
            results: cleanedRelated,
            cachedAt: new Date().toISOString()
          },
          { related: true, ttlMs: RELATED_QUEUE_PERSISTED_TTL_MS }
        );
        return cleanedRelated.slice(0, limit);
      }

      if (youtubeRelated.length > 0 && cleanedRelated.length > 0) {
        touchMapEntry(relatedQueueCache, cacheKey, cleanedRelated);
        pruneMapEntries(relatedQueueCache, RELATED_QUEUE_CACHE_LIMIT);
        void cachePersistentSearchResponse(
          cacheKey,
          {
            query: track.title,
            results: cleanedRelated,
            cachedAt: new Date().toISOString()
          },
          { related: true, ttlMs: RELATED_QUEUE_PERSISTED_TTL_MS }
        );
        return cleanedRelated.slice(0, limit);
      }

      const fallbackCandidates = Array.from(relatedQueueCache.values()).flat();
      const appearanceCounts = new Map<string, number>();

      for (const [rank, candidate] of fallbackCandidates.entries()) {
        appearanceCounts.set(candidate.id, Math.max(1, 6 - Math.floor(rank / 10)));
      }

      if (youtubeRelated.length === 0 && fallbackCandidates.length === 0) {
        throw new Error("Unable to build the next-song queue.");
      }

      const relatedTracks = [
        ...cleanedRelated,
        ...rankRelatedSongs(
          track,
          fallbackCandidates,
          Math.max(0, Math.min(SMART_QUEUE_FALLBACK_INJECTION_LIMIT, limit - cleanedRelated.length)),
          cleanedRelated,
          appearanceCounts
        )
      ].slice(0, Math.max(cleanedRelated.length, Math.min(limit, cleanedRelated.length + SMART_QUEUE_FALLBACK_INJECTION_LIMIT)));

      touchMapEntry(relatedQueueCache, cacheKey, relatedTracks);
      pruneMapEntries(relatedQueueCache, RELATED_QUEUE_CACHE_LIMIT);
      void cachePersistentSearchResponse(
        cacheKey,
        {
          query: track.title,
          results: relatedTracks,
          cachedAt: new Date().toISOString()
        },
        { related: true, ttlMs: RELATED_QUEUE_PERSISTED_TTL_MS }
      );
      return relatedTracks.slice(0, limit);
    })
    .finally(() => {
      pendingRelatedQueueRequests.delete(cacheKey);
    });

  pendingRelatedQueueRequests.set(cacheKey, request);
  return request;
}

function createQueueSelectionState(queue: Song[]) {
  const seenTrackIds = new Set(queue.map((track) => track.id));
  const seenIdentityKeys = new Set(queue.map(getTrackIdentityKey).filter(Boolean));
  const artistCounts = new Map<string, number>();

  for (const track of queue) {
    const artistKey = getArtistKey(track.author);

    if (!artistKey) {
      continue;
    }

    artistCounts.set(artistKey, (artistCounts.get(artistKey) || 0) + 1);
  }

  return {
    seenTrackIds,
    seenIdentityKeys,
    artistCounts
  };
}

type QueueSelectionState = ReturnType<typeof createQueueSelectionState>;

type CandidatePoolEntry = {
  track: Song;
  supportScore: number;
  rootAffinity: number;
  bestAnchorAffinity: number;
  signalHits: number;
  anchorIds: Set<string>;
  sourceCount: number;
};

function registerQueueSelectionTrack(selectionState: QueueSelectionState, track: Song) {
  selectionState.seenTrackIds.add(track.id);

  const identityKey = getTrackIdentityKey(track);

  if (identityKey) {
    selectionState.seenIdentityKeys.add(identityKey);
  }

  const artistKey = getArtistKey(track.author);

  if (artistKey) {
    selectionState.artistCounts.set(artistKey, (selectionState.artistCounts.get(artistKey) || 0) + 1);
  }
}

function getSharedSignalCount(signalSet: Set<QueueSignal>, candidate: Song) {
  const candidateSignals = detectTrackSignals(candidate);
  let sharedSignals = 0;

  for (const signal of signalSet) {
    if (candidateSignals.has(signal)) {
      sharedSignals += 1;
    }
  }

  return sharedSignals;
}

function getTrackQualityBoost(candidate: Song) {
  let score = Math.min(18, Math.log10(Math.max(candidate.views || 1, 1)) * 3);

  if (/\btopic\b/i.test(candidate.author)) {
    score += 9;
  }

  if (/\bvevo\b/i.test(candidate.author)) {
    score += 6;
  }

  if (/\bofficial\b|\baudio\b|\bvideo\b/i.test(candidate.title)) {
    score += 8;
  }

  if (candidate.durationSeconds >= 150 && candidate.durationSeconds <= 360) {
    score += 6;
  } else if (candidate.durationSeconds > 0 && candidate.durationSeconds < 120) {
    score -= 10;
  }

  return score;
}

function getQueueDiversityPenalty(queue: Song[], candidate: Song) {
  const candidateArtistKey = getArtistKey(candidate.author);
  const recentTracks = queue.slice(-4);
  let penalty = 0;

  for (const recentTrack of recentTracks) {
    if (candidateArtistKey && candidateArtistKey === getArtistKey(recentTrack.author)) {
      penalty += 48;
    }

    const titleSimilarity = getTitleSimilarity(recentTrack.title, candidate.title);

    if (titleSimilarity > 0.45) {
      penalty += Math.round(titleSimilarity * 42);
    } else if (titleSimilarity > 0.25) {
      penalty += Math.round(titleSimilarity * 18);
    }
  }

  return penalty;
}

function getRecentSessionTracks(limit = RECENT_SESSION_REPEAT_WINDOW) {
  const { sessionHistory } = getPlayerState();
  return Array.isArray(sessionHistory) ? sessionHistory.slice(-Math.max(limit, 0)) : [];
}

function getRecentSessionRepeatPenalty(candidate: Song) {
  const recentSessionTracks = getRecentSessionTracks();

  if (!recentSessionTracks.length) {
    return 0;
  }

  const candidateArtistKey = getArtistKey(candidate.author);
  let penalty = 0;

  for (const [index, recentTrack] of recentSessionTracks.slice().reverse().entries()) {
    const recencyWeight = Math.max(1, RECENT_SESSION_REPEAT_WINDOW - index);

    if (recentTrack.id === candidate.id || isLikelySameSong(recentTrack, candidate)) {
      penalty += 120 + recencyWeight * 8;
      continue;
    }

    if (candidateArtistKey && candidateArtistKey === getArtistKey(recentTrack.author)) {
      penalty += RECENT_SESSION_ARTIST_PENALTY + recencyWeight * 2;
    }
  }

  return penalty;
}

function getListenerPreferenceScore(candidate: Song) {
  const artistScore = listenerProfile.artists[getListenerProfileArtistKey(candidate.author)] || 0;
  const trackScore = listenerProfile.tracks[getListenerProfileTrackKey(candidate)] || 0;
  const signalScore = getTrackSignals(candidate).reduce((total, signal) => total + (listenerProfile.signals[signal] || 0), 0);

  return clamp(artistScore * 2.2 + trackScore * 1.8 + signalScore * 1.35, -70, 90);
}

function createCandidatePoolEntry(track: Song): CandidatePoolEntry {
  return {
    track,
    supportScore: 0,
    rootAffinity: 0,
    bestAnchorAffinity: 0,
    signalHits: 0,
    anchorIds: new Set<string>(),
    sourceCount: 0
  };
}

function addCandidateToPool(
  pool: Map<string, CandidatePoolEntry>,
  rootTrack: Song,
  anchorTrack: Song,
  signalSet: Set<QueueSignal>,
  candidate: Song,
  rank: number,
  sourceWeight: number
) {
  if (!isQueueableTrack(candidate, rootTrack) || candidate.id === rootTrack.id) {
    return;
  }

  if (isLikelySameSong(rootTrack, candidate) || isLikelySameSong(anchorTrack, candidate)) {
    return;
  }

  const entry = pool.get(candidate.id) ?? createCandidatePoolEntry(candidate);
  entry.track = candidate;
  entry.supportScore += Math.max(6, sourceWeight - rank * 3);
  entry.rootAffinity = Math.max(entry.rootAffinity, scoreRelatedCandidate(rootTrack, candidate));
  entry.bestAnchorAffinity = Math.max(entry.bestAnchorAffinity, scoreRelatedCandidate(anchorTrack, candidate));
  entry.signalHits = Math.max(entry.signalHits, getSharedSignalCount(signalSet, candidate));
  entry.anchorIds.add(anchorTrack.id);
  entry.sourceCount += 1;
  pool.set(candidate.id, entry);
}

function getQueueAnchorTracks(rootTrack: Song, queue: Song[], currentIndex: number) {
  const safeIndex = clamp(currentIndex, 0, Math.max(queue.length - 1, 0));
  const anchorCandidates = [
    queue[queue.length - 1] || rootTrack,
    queue[safeIndex] || rootTrack,
    rootTrack
  ].filter(Boolean) as Song[];
  const uniqueAnchors: Song[] = [];
  const seenTrackIds = new Set<string>();

  for (const candidate of anchorCandidates) {
    if (seenTrackIds.has(candidate.id)) {
      continue;
    }

    seenTrackIds.add(candidate.id);
    uniqueAnchors.push(candidate);

    if (uniqueAnchors.length >= SMART_QUEUE_CONTEXT_ANCHOR_LIMIT) {
      break;
    }
  }

  return uniqueAnchors;
}
async function buildRelatedCandidatePool(rootTrack: Song, queue: Song[], currentIndex: number) {
  const signalSet = new Set<QueueSignal>(inferQueueSignals(rootTrack, queue));
  const anchors = getQueueAnchorTracks(rootTrack, queue, currentIndex);
  const pool = new Map<string, CandidatePoolEntry>();
  const directResults = await Promise.allSettled(
    anchors.map((anchor, index) =>
      fetchRelatedSongs(anchor, queue, index === 0 ? SMART_QUEUE_PRIMARY_FETCH_LIMIT : SMART_QUEUE_SECONDARY_FETCH_LIMIT)
    )
  );

  directResults.forEach((result, index) => {
    if (result.status !== "fulfilled") {
      return;
    }

    const anchor = anchors[index];
    const sourceWeight = index === 0 ? 56 : anchor.id === rootTrack.id ? 50 : 44 - index * 2;

    result.value.forEach((candidate, rank) => {
      addCandidateToPool(pool, rootTrack, anchor, signalSet, candidate, rank, sourceWeight);
    });
  });

  return { pool, signalSet };
}

async function expandRelatedCandidatePool(
  pool: Map<string, CandidatePoolEntry>,
  rootTrack: Song,
  anchorTrack: Song,
  queue: Song[],
  signalSet: Set<QueueSignal>
) {
  try {
    const candidates = await fetchRelatedSongs(anchorTrack, queue, SMART_QUEUE_SECONDARY_FETCH_LIMIT);

    candidates.forEach((candidate, rank) => {
      addCandidateToPool(pool, rootTrack, anchorTrack, signalSet, candidate, rank, 28);
    });
  } catch {
    // Best-effort queue enrichment.
  }
}

function scoreQueuePoolCandidate(rootTrack: Song, anchorTrack: Song, queue: Song[], entry: CandidatePoolEntry) {
  const candidate = entry.track;
  const anchorArtistKey = getArtistKey(anchorTrack.author);
  const candidateArtistKey = getArtistKey(candidate.author);
  let score = entry.supportScore;

  score += entry.rootAffinity * 0.9;
  score += scoreRelatedCandidate(anchorTrack, candidate) * 1.35;
  score += entry.bestAnchorAffinity * 0.7;
  score += entry.signalHits * 11;
  score += entry.anchorIds.size * 8;
  score += entry.sourceCount * 5;
  score += getTrackQualityBoost(candidate);
  score += getListenerPreferenceScore(candidate);
  score -= getQueueDiversityPenalty(queue, candidate);
  score -= getRecentSessionRepeatPenalty(candidate);

  if (candidateArtistKey && anchorArtistKey && candidateArtistKey !== anchorArtistKey) {
    score += 10;
  }

  return score;
}

function pickBestQueueCandidate(
  rootTrack: Song,
  anchorTrack: Song,
  queue: Song[],
  selectionState: QueueSelectionState,
  pool: Map<string, CandidatePoolEntry>
) {
  let bestEntry: CandidatePoolEntry | null = null;
  let bestScore = -Infinity;

  for (const entry of pool.values()) {
    const candidate = entry.track;

    if (selectionState.seenTrackIds.has(candidate.id)) {
      continue;
    }

    if (isLikelySameSong(rootTrack, candidate) || isLikelySameSong(anchorTrack, candidate)) {
      continue;
    }

    const identityKey = getTrackIdentityKey(candidate);

    if (identityKey && selectionState.seenIdentityKeys.has(identityKey)) {
      continue;
    }

    const artistKey = getArtistKey(candidate.author);

    if (artistKey && (selectionState.artistCounts.get(artistKey) || 0) >= SMART_QUEUE_ARTIST_CAP) {
      continue;
    }

    const candidateScore = scoreQueuePoolCandidate(rootTrack, anchorTrack, queue, entry);

    if (candidateScore > bestScore) {
      bestScore = candidateScore;
      bestEntry = entry;
    }
  }

  return bestEntry?.track ?? null;
}

async function buildChainedRelatedQueue(rootTrack: Song, queue: Song[], currentIndex: number, targetLength: number) {
  let nextQueue = [...queue];
  let anchorTrack = nextQueue[nextQueue.length - 1] || nextQueue[Math.max(currentIndex, 0)] || rootTrack;
  let stagnationCount = 0;
  const selectionState = createQueueSelectionState(nextQueue);
  const expandedAnchors = new Set<string>();
  const { pool, signalSet } = await buildRelatedCandidatePool(rootTrack, nextQueue, currentIndex);

  while (nextQueue.length < targetLength && stagnationCount < SMART_QUEUE_STAGNATION_LIMIT) {
    const nextTrack = pickBestQueueCandidate(rootTrack, anchorTrack, nextQueue, selectionState, pool);

    if (!nextTrack) {
      const expansionTrack = expandedAnchors.has(anchorTrack.id) ? rootTrack : anchorTrack;

      if (expandedAnchors.has(expansionTrack.id)) {
        stagnationCount += 1;
        break;
      }

      expandedAnchors.add(expansionTrack.id);
      const poolSizeBeforeExpansion = pool.size;
      await expandRelatedCandidatePool(pool, rootTrack, expansionTrack, nextQueue, signalSet);
      stagnationCount = pool.size > poolSizeBeforeExpansion ? 0 : stagnationCount + 1;
      anchorTrack = expansionTrack.id === rootTrack.id ? rootTrack : anchorTrack;
      continue;
    }

    nextQueue = [...nextQueue, nextTrack];
    registerQueueSelectionTrack(selectionState, nextTrack);
    pool.delete(nextTrack.id);
    anchorTrack = nextTrack;
    stagnationCount = 0;

    if (nextQueue.length < targetLength && pool.size < SMART_QUEUE_SECONDARY_FETCH_LIMIT && !expandedAnchors.has(nextTrack.id)) {
      expandedAnchors.add(nextTrack.id);
      await expandRelatedCandidatePool(pool, rootTrack, nextTrack, nextQueue, signalSet);
    }
  }

  return nextQueue;
}

function rankLocalTrackPoolForQueueFloor(rootTrack: Song, queue: Song[], candidates: Song[], limit: number) {
  const recentSessionTracks = getRecentSessionTracks();
  const recentArtistCounts = new Map<string, number>();

  for (const track of recentSessionTracks) {
    const artistKey = getArtistKey(track.author);

    if (!artistKey) {
      continue;
    }

    recentArtistCounts.set(artistKey, (recentArtistCounts.get(artistKey) || 0) + 1);
  }

  const rankedCandidates = candidates
    .filter((candidate) => isQueueableTrack(candidate, rootTrack))
    .filter((candidate) => !isLikelySameSong(rootTrack, candidate))
    .filter(
      (candidate) =>
        !recentSessionTracks.some((recentTrack) => recentTrack.id === candidate.id || isLikelySameSong(recentTrack, candidate))
    )
    .sort((left, right) => {
      const rightArtistPenalty = (recentArtistCounts.get(getArtistKey(right.author)) || 0) * RECENT_SESSION_ARTIST_PENALTY;
      const leftArtistPenalty = (recentArtistCounts.get(getArtistKey(left.author)) || 0) * RECENT_SESSION_ARTIST_PENALTY;
      const rightScore = scoreRelatedCandidate(rootTrack, right) + getTrackQualityBoost(right) + getListenerPreferenceScore(right) - rightArtistPenalty;
      const leftScore = scoreRelatedCandidate(rootTrack, left) + getTrackQualityBoost(left) + getListenerPreferenceScore(left) - leftArtistPenalty;
      return rightScore - leftScore;
    });

  return appendDistinctTracks(rootTrack, queue, rankedCandidates, queue.length + limit).slice(queue.length, queue.length + limit);
}

async function extendQueueFromLocalTrackPool(rootTrack: Song, queue: Song[], targetLength: number) {
  if (queue.length >= targetLength) {
    return queue;
  }

  const additionsNeeded = Math.max(0, targetLength - queue.length);

  if (additionsNeeded === 0) {
    return queue;
  }

  const trackPool = await readRecommendationTrackPool(SEARCH_CANDIDATE_BANK_LIMIT);

  if (!trackPool.length) {
    return queue;
  }

  let nextQueue = appendDistinctTracks(
    rootTrack,
    queue,
    rankLocalTrackPoolForQueueFloor(rootTrack, queue, trackPool, additionsNeeded),
    targetLength
  );

  if (nextQueue.length >= targetLength) {
    return nextQueue;
  }

  const remainingNeeded = Math.max(0, targetLength - nextQueue.length);

  if (remainingNeeded === 0) {
    return nextQueue;
  }

  nextQueue = appendDistinctTracks(
    rootTrack,
    nextQueue,
    rankRelatedSongs(rootTrack, trackPool, remainingNeeded, nextQueue),
    targetLength
  );

  return nextQueue;
}

function syncLiveQueueStateIfCurrentTrack(trackId: string, queue: Song[], preferredCurrentIndex: number) {
  if (!queue.length || getPlayerState().currentSong?.id !== trackId) {
    return;
  }

  const locatedIndex = queue.findIndex((queuedTrack) => queuedTrack.id === trackId);
  const nextCurrentIndex =
    locatedIndex >= 0 ? locatedIndex : Math.max(0, Math.min(preferredCurrentIndex, queue.length - 1));

  setPlayerState((previous) => ({
    ...previous,
    queue,
    currentIndex: nextCurrentIndex
  }));

  syncHiddenYouTubeQueueWithState();
}

async function fillSmartQueue(track: Song, queue: Song[], currentIndex: number) {
  if (isSafeModeEnabled()) {
    return queue;
  }

  const nextPlaybackQueue = buildPlaybackQueue(track, queue, currentIndex);
  const baseQueue = nextPlaybackQueue.queue;
  const baseCurrentIndex = nextPlaybackQueue.currentIndex;
  const { queueSource, playlistMixEnabled } = getPlayerState();

  if (queueSource === "playlist" && !playlistMixEnabled) {
    return baseQueue;
  }

  const targetLength = Math.min(MAX_PLAYBACK_QUEUE_LENGTH, baseCurrentIndex + 1 + UPCOMING_QUEUE_TARGET);
  const existingUpcomingCount = Math.max(baseQueue.length - baseCurrentIndex - 1, 0);

  if (existingUpcomingCount >= UPCOMING_QUEUE_TARGET) {
    return baseQueue;
  }

  let nextQueue = [...baseQueue];
  const primaryFetchLimit = Math.min(
    SMART_QUEUE_PRIMARY_FETCH_LIMIT,
    Math.max(UPCOMING_QUEUE_TARGET + 4, targetLength - baseCurrentIndex - 1)
  );

  try {
    const rootRelatedSongs = await fetchRelatedSongs(track, nextQueue, primaryFetchLimit);
    nextQueue = appendDistinctTracks(track, nextQueue, rootRelatedSongs, targetLength);
    syncLiveQueueStateIfCurrentTrack(track.id, nextQueue, baseCurrentIndex);
  } catch {
    // Keep going with local-only recovery below.
  }

  try {
    nextQueue = await extendQueueFromLocalTrackPool(track, nextQueue, targetLength);
    syncLiveQueueStateIfCurrentTrack(track.id, nextQueue, baseCurrentIndex);
  } catch {
    // Best-effort only.
  }

  if (nextQueue.length >= targetLength) {
    return nextQueue;
  }

  try {
    const currentIndexForChain = Math.max(
      0,
      nextQueue.findIndex((queuedTrack) => queuedTrack.id === track.id)
    );
    nextQueue = await buildChainedRelatedQueue(track, nextQueue, currentIndexForChain, targetLength);
    syncLiveQueueStateIfCurrentTrack(track.id, nextQueue, currentIndexForChain);
  } catch {
    // Best-effort only.
  }

  return nextQueue.length > 0 ? nextQueue : baseQueue;
}

function getAudio() {
  if (!singletonAudio) {
    singletonAudio = new Audio();
    singletonAudio.preload = "auto";
    singletonAudio.volume = getPlayerState().volume;
    singletonAudio.setAttribute("playsinline", "true");
    singletonAudio.setAttribute("webkit-playsinline", "true");
    singletonAudio.setAttribute("x-webkit-airplay", "allow");
  }

  return singletonAudio;
}

function getBufferedProgress(audio: HTMLAudioElement) {
  if (!audio.duration || !audio.buffered.length) {
    return 0;
  }

  return audio.buffered.end(audio.buffered.length - 1);
}

function getMediaErrorMessage(mediaError: MediaError | null, mimeType: string | null = null) {
  const mimeLabel = mimeType ? ` (${mimeType})` : "";

  switch (mediaError?.code) {
    case MediaError.MEDIA_ERR_ABORTED:
      return `Playback was aborted by the browser${mimeLabel}.`;
    case MediaError.MEDIA_ERR_NETWORK:
      return `The stream failed during network loading${mimeLabel}.`;
    case MediaError.MEDIA_ERR_DECODE:
      return `The browser could not decode this stream${mimeLabel}.`;
    case MediaError.MEDIA_ERR_SRC_NOT_SUPPORTED:
      return `This source format is not supported by the browser${mimeLabel}.`;
    default:
      return mimeType ? `Playback failed for source ${mimeType}.` : "Playback could not start.";
  }
}

function syncTiming() {
  if (visibleVideoModeActive || shouldUseNativePlayback()) {
    return;
  }

  const audio = getAudio();
  playerActions.setTiming(audio.currentTime, audio.duration || 0, getBufferedProgress(audio));
  notePlaybackProgress(audio.currentTime, audio.duration || 0);
}

function stopProgressLoop() {
  if (progressAnimationFrame === null) {
    return;
  }

  window.cancelAnimationFrame(progressAnimationFrame);
  progressAnimationFrame = null;
}

function startProgressLoop() {
  if (progressAnimationFrame !== null) {
    return;
  }

  const tick = () => {
    const audio = getAudio();

    syncTiming();

    if (audio.paused) {
      progressAnimationFrame = null;
      return;
    }

    progressAnimationFrame = window.requestAnimationFrame(tick);
  };

  progressAnimationFrame = window.requestAnimationFrame(tick);
}

function isFreshStream(stream: Pick<StreamResponse, "expiresAt">) {
  return !stream.expiresAt || stream.expiresAt > Date.now() + STREAM_FRESH_BUFFER_MS;
}

function getStreamCandidates(stream: StreamResponse): StreamCandidate[] {
  const candidates = stream.streamCandidates?.length
    ? stream.streamCandidates
    : [
        {
          url: stream.streamUrl,
          mimeType: stream.mimeType,
          expiresAt: stream.expiresAt
        }
      ];

  return candidates.filter((candidate) => isFreshStream(candidate));
}

function getCandidateSupportScore(audio: HTMLAudioElement, candidate: StreamCandidate) {
  if (!candidate.mimeType) {
    return 0;
  }

  const isIOS = getDeviceInfo().isIOS;
  const mime = candidate.mimeType.toLowerCase();

  if (isIOS) {
    if (mime.includes("webm") || mime.includes("opus")) {
      return -10;
    }
    if (mime.includes("mp4") || mime.includes("m4a") || mime.includes("aac") || mime.includes("mpeg")) {
      return 100;
    }
  }

  const support = audio.canPlayType(candidate.mimeType);

  if (support === "probably") {
    return 2;
  }

  if (support === "maybe") {
    return 1;
  }

  return 0;
}

function rankStreamCandidates(audio: HTMLAudioElement, stream: StreamResponse) {
  const candidates = getStreamCandidates(stream);
  const isIOS = getDeviceInfo().isIOS;

  const validCandidates = isIOS
    ? candidates.filter((c) => getCandidateSupportScore(audio, c) > 0)
    : candidates;

  const listToRank = validCandidates.length > 0 ? validCandidates : candidates;

  return [...listToRank].sort((left, right) => {
    const supportDifference = getCandidateSupportScore(audio, right) - getCandidateSupportScore(audio, left);

    if (supportDifference !== 0) {
      return supportDifference;
    }

    return Number(right.expiresAt || 0) - Number(left.expiresAt || 0);
  });
}

function parseMimeBase(mimeType: string | null | undefined) {
  return mimeType?.split(";")[0]?.trim().toLowerCase() || "";
}

function withBlobCandidate(trackId: string, stream: StreamResponse): StreamCacheValue {
  const cachedBlob = blobCache.get(trackId);

  if (!cachedBlob) {
    return {
      ...stream,
      cachedAt: Date.now()
    };
  }

  touchMapEntry(blobCache, trackId, cachedBlob);

  const blobCandidate: StreamCandidate = {
    url: cachedBlob.objectUrl,
    mimeType: cachedBlob.mimeType,
    expiresAt: null
  };
  const remainingCandidates = getStreamCandidates(stream).filter((candidate) => candidate.url !== cachedBlob.objectUrl);

  return {
    ...stream,
    streamUrl: cachedBlob.objectUrl,
    mimeType: cachedBlob.mimeType,
    expiresAt: null,
    streamCandidates: [blobCandidate, ...remainingCandidates],
    cachedAt: Date.now()
  };
}

function rememberStream(trackId: string, stream: StreamResponse): StreamCacheValue {
  const value: StreamCacheValue = {
    ...stream,
    cachedAt: Date.now()
  };

  touchMapEntry(streamCache, trackId, value);
  pruneMapEntries(streamCache, STREAM_CACHE_LIMIT);
  return withBlobCandidate(trackId, value);
}

function getPersistedStreamTtl(stream: StreamResponse) {
  if (!stream.expiresAt) {
    return 1000 * 60 * 15;
  }

  return Math.max(1_000, stream.expiresAt - Date.now() - STREAM_FRESH_BUFFER_MS);
}

function persistResolvedStream(track: Song, resolved: StreamResponse) {
  return cacheStreamResponse(track.videoId, resolved, getPersistedStreamTtl(resolved));
}

async function getBrowserYoutubeClient() {
  if (!browserYoutubeClientPromise) {
    browserYoutubeClientPromise = import("youtubei.js").then(({ Innertube }) =>
      Innertube.create({
        client_type: "WEB" as never,
        device_category: "desktop",
        generate_session_locally: true,
        enable_session_cache: false
      })
    );
  }

  return browserYoutubeClientPromise;
}

async function resolveBrowserSideStream(track: Song): Promise<StreamCacheValue | null> {
  if (!shouldTryBrowserSideStreamResolution()) {
    return null;
  }

  try {
    const youtube = await getBrowserYoutubeClient();
    const info = await youtube.getBasicInfo(track.videoId, {
      client: "WEB"
    });
    const streamingData = info.streaming_data as
      | {
          adaptive_formats?: Array<{
            url?: string;
            mime_type?: string;
            bitrate?: number;
            has_audio?: boolean;
          }>;
          hls_manifest_url?: string | null;
          server_abr_streaming_url?: string | null;
        }
      | undefined;

    if (!streamingData) {
      return null;
    }

    const seenUrls = new Set<string>();
    const streamCandidates: StreamCandidate[] = [];
    const adaptiveAudioFormats = (streamingData.adaptive_formats || [])
      .filter((format) => format.has_audio)
      .sort((left, right) => (right.bitrate || 0) - (left.bitrate || 0));

    const pushCandidate = (url?: string | null, mimeType?: string | null) => {
      if (!url || seenUrls.has(url)) {
        return;
      }

      seenUrls.add(url);
      streamCandidates.push({
        url,
        mimeType: mimeType || null,
        expiresAt: extractExpiryTimestamp(url)
      });
    };

    pushCandidate(streamingData.server_abr_streaming_url, adaptiveAudioFormats[0]?.mime_type || "audio/mp4");
    pushCandidate(streamingData.hls_manifest_url, "application/vnd.apple.mpegurl");

    for (const format of adaptiveAudioFormats) {
      pushCandidate(format.url, format.mime_type);
    }

    const primaryCandidate = streamCandidates[0];

    if (!primaryCandidate?.url) {
      return null;
    }

    return rememberStream(track.id, {
      streamUrl: primaryCandidate.url,
      mimeType: primaryCandidate.mimeType,
      expiresAt: primaryCandidate.expiresAt,
      streamCandidates
    });
  } catch {
    return null;
  }
}

function getCachedResolvedStream(trackId: string) {
  const cached = streamCache.get(trackId);

  if (!cached) {
    return null;
  }

  if (!isFreshStream(cached)) {
    streamCache.delete(trackId);
    return null;
  }

  touchMapEntry(streamCache, trackId, cached);
  return withBlobCandidate(trackId, cached);
}

async function getPersistedResolvedStream(track: Song) {
  const cached = await readCachedStreamResponse(track.videoId);

  if (!cached || !isFreshStream(cached)) {
    return null;
  }

  return rememberStream(track.id, cached);
}

function seedStreamFromTrack(track: Song): StreamCacheValue | null {
  const cached = streamCache.get(track.id);

  if (cached && isFreshStream(cached)) {
    touchMapEntry(streamCache, track.id, cached);
    return cached;
  }

  if (cached && !isFreshStream(cached)) {
    streamCache.delete(track.id);
  }

  if (!track.streamUrl) {
    return null;
  }

  const seededStream: StreamResponse = {
    streamUrl: track.streamUrl,
    mimeType: track.streamMimeType ?? null,
    expiresAt: track.streamExpiresAt ?? null,
    streamCandidates:
      track.streamCandidates && track.streamCandidates.length > 0
        ? track.streamCandidates
        : [
            {
              url: track.streamUrl,
              mimeType: track.streamMimeType ?? null,
              expiresAt: track.streamExpiresAt ?? null
            }
          ]
  };

  if (!isFreshStream(seededStream)) {
    return null;
  }

  return rememberStream(track.id, seededStream);
}

function disposeBlobCacheValue(value: BlobCacheValue) {
  URL.revokeObjectURL(value.objectUrl);
}

function rememberBlob(trackId: string, blob: BlobCacheValue) {
  const existing = blobCache.get(trackId);

  if (existing && existing.objectUrl !== blob.objectUrl) {
    disposeBlobCacheValue(existing);
  }

  touchMapEntry(blobCache, trackId, blob);
  pruneMapEntries(blobCache, IOS_BLOB_CACHE_LIMIT, disposeBlobCacheValue);

  return blob;
}

function shouldBlobPrefetchTrack(track: Song) {
  if (!shouldTryBrowserSideStreamResolution()) {
    return false;
  }

  if (!track.durationSeconds || track.durationSeconds > IOS_BLOB_MAX_DURATION_SECONDS) {
    return false;
  }

  return true;
}

async function prefetchTrackBlob(track: Song, resolved: StreamResponse) {
  if (!shouldBlobPrefetchTrack(track)) {
    return null;
  }

  const cachedBlob = blobCache.get(track.id);

  if (cachedBlob) {
    touchMapEntry(blobCache, track.id, cachedBlob);
    return cachedBlob;
  }

  const pending = pendingBlobRequests.get(track.id);

  if (pending) {
    return pending;
  }

  const audio = getAudio();
  const candidate = rankStreamCandidates(audio, resolved).find((streamCandidate) => {
    const mimeBase = parseMimeBase(streamCandidate.mimeType);

    return mimeBase === "audio/mp4" || mimeBase === "audio/webm" || mimeBase === "video/mp4";
  });

  if (!candidate?.url) {
    return null;
  }

  const request = fetch(candidate.url, {
    method: "GET",
    headers: {
      Accept: "*/*"
    }
  })
    .then(async (response) => {
      if (!response.ok) {
        return null;
      }

      const contentLength = Number(response.headers.get("content-length") || 0);

      if (contentLength > IOS_BLOB_MAX_BYTES) {
        await response.body?.cancel().catch(() => {
          // Best-effort early exit for large files.
        });
        return null;
      }

      const blob = await response.blob();

      if (!blob.size || blob.size > IOS_BLOB_MAX_BYTES) {
        return null;
      }

      const objectUrl = URL.createObjectURL(blob);
      const cachedValue = rememberBlob(track.id, {
        objectUrl,
        mimeType: blob.type || candidate.mimeType,
        bytes: blob.size,
        cachedAt: Date.now()
      });
      const refreshedStream = withBlobCandidate(track.id, resolved);
      touchMapEntry(streamCache, track.id, refreshedStream);
      pruneMapEntries(streamCache, STREAM_CACHE_LIMIT);
      preloadResolvedTrack(track, refreshedStream);
      return cachedValue;
    })
    .catch(() => null)
    .finally(() => {
      pendingBlobRequests.delete(track.id);
    });

  pendingBlobRequests.set(track.id, request);
  return request;
}

function releasePreloadedAudio(audio: HTMLAudioElement) {
  audio.pause();
  audio.removeAttribute("src");
  audio.load();
}

function touchPreloadedAudio(trackId: string, audio: HTMLAudioElement) {
  touchMapEntry(preloadedAudios, trackId, audio);
  pruneMapEntries(preloadedAudios, PRELOAD_AUDIO_LIMIT, releasePreloadedAudio);
}

function preloadResolvedTrack(track: Song, resolved: StreamResponse) {
  if (!isFreshStream(resolved)) {
    return;
  }

  const preloadAudioBase = getAudio();
  const rankedCandidates = rankStreamCandidates(preloadAudioBase, resolved);
  const selectedCandidate = rankedCandidates.find((candidate) => getCandidateSupportScore(preloadAudioBase, candidate) > 0) || rankedCandidates[0];

  if (!selectedCandidate || getAudio().src === selectedCandidate.url) {
    return;
  }

  const existingPreload = preloadedAudios.get(track.id);

  if (existingPreload && existingPreload.src === selectedCandidate.url) {
    touchPreloadedAudio(track.id, existingPreload);
    return;
  }

  if (existingPreload) {
    releasePreloadedAudio(existingPreload);
  }

  const preloadAudio = new Audio();
  preloadAudio.preload = "auto";
  preloadAudio.src = selectedCandidate.url;
  preloadAudio.load();
  touchPreloadedAudio(track.id, preloadAudio);
}

function attachAudioListeners() {
  if (listenersAttached) {
    return;
  }

  const audio = getAudio();

  audio.addEventListener("loadstart", () => {
    clearPendingPlaybackError();
    playerActions.setLoading(true);
    playerActions.setError(null);
  });

  audio.addEventListener("canplay", () => {
    clearPendingPlaybackError();
    playerActions.setLoading(false);
    syncTiming();
  });

  audio.addEventListener("waiting", () => {
    playerActions.setLoading(true);
  });

  audio.addEventListener("playing", () => {
    clearPendingPlaybackError();
    clearBackgroundPlaybackGuard();
    clearManualPauseRequested();
    markPlaybackStarted(getPlayerState().currentSong);
    playerActions.setPlayback(true);
    playerActions.setLoading(false);
    startProgressLoop();
  });

  audio.addEventListener("pause", () => {
    if (visibleVideoModeSuspendingUnderlying) {
      stopProgressLoop();
      return;
    }

    if (shouldResumeBackgroundPlayback()) {
      noteBackgroundPlaybackResumeAttempt();
      playerActions.setLoading(true);
      void audio.play().catch(() => {
        playerActions.setLoading(false);
      });
      return;
    }

    playerActions.setPlayback(false);
    stopProgressLoop();
  });

  audio.addEventListener("timeupdate", () => {
    syncTiming();
  });

  audio.addEventListener("progress", () => {
    syncTiming();
  });

  audio.addEventListener("durationchange", () => {
    syncTiming();
  });

  audio.addEventListener("volumechange", () => {
    playerActions.setVolume(audio.volume);
  });

  audio.addEventListener("ended", () => {
    stopProgressLoop();
    finalizePlaybackSession("ended");
    void audioController.playNext();
  });

  audio.addEventListener("error", () => {
    stopProgressLoop();

    if (sourceSelectionInProgress) {
      return;
    }

    queuePlaybackError(getMediaErrorMessage(audio.error) + " Try another track.");
  });

  listenersAttached = true;
}

async function fetchStream(track: Song): Promise<StreamCacheValue> {
  const seeded = seedStreamFromTrack(track);

  if (seeded) {
    void persistResolvedStream(track, seeded);
    preloadResolvedTrack(track, seeded);
    return seeded;
  }

  const persisted = await getPersistedResolvedStream(track);

  if (persisted) {
    preloadResolvedTrack(track, persisted);
    return persisted;
  }

  const browserResolved = await resolveBrowserSideStream(track);

  if (browserResolved) {
    void persistResolvedStream(track, browserResolved);
    preloadResolvedTrack(track, browserResolved);
    return browserResolved;
  }

  const pending = pendingStreamRequests.get(track.id);

  if (pending) {
    return pending;
  }

  const request = fetch(`/api/stream?videoId=${encodeURIComponent(track.videoId)}`, {
    method: "GET",
    headers: {
      Accept: "application/json"
    }
  })
    .then(async (response) => {
      if (!response.ok) {
        const payload = (await response.json().catch(() => null)) as { error?: string } | null;
        throw new Error(payload?.error || "Unable to resolve audio stream.");
      }

      const payload = (await response.json()) as StreamResponse;
      noteNetworkSuccess();
      const value = rememberStream(track.id, payload);
      void persistResolvedStream(track, value);
      preloadResolvedTrack(track, value);
      return value;
    })
    .catch((error) => {
      noteNetworkFailure();
      throw error;
    })
    .finally(() => {
      pendingStreamRequests.delete(track.id);
    });

  pendingStreamRequests.set(track.id, request);
  return request;
}

async function playResolvedTrack(track: Song, resolved: StreamResponse) {
  const audio = getAudio();
  const currentTrackId = getPlayerState().currentSong?.id;

  if (currentTrackId !== track.id) {
    return;
  }

  const rankedCandidates = rankStreamCandidates(audio, resolved);
  let lastError: unknown = null;
  sourceSelectionInProgress = true;

  if (rankedCandidates.length === 0) {
    sourceSelectionInProgress = false;
    throw new Error("No browser-playable stream candidates were available for this track.");
  }

  try {
    for (const candidate of rankedCandidates) {
      if (getPlayerState().currentSong?.id !== track.id) {
        return;
      }

      try {
        await new Promise<void>((resolve, reject) => {
          let settled = false;
          let timeoutId: number | null = null;

          const cleanup = () => {
            audio.removeEventListener("playing", handlePlaying);
            audio.removeEventListener("canplay", handleCanPlay);
            audio.removeEventListener("error", handleError);

            if (timeoutId !== null) {
              window.clearTimeout(timeoutId);
            }
          };

          const finishResolve = () => {
            if (settled) {
              return;
            }

            settled = true;
            cleanup();
            resolve();
          };

          const finishReject = (error: unknown) => {
            if (settled) {
              return;
            }

            settled = true;
            cleanup();
            reject(error);
          };

          const handlePlaying = () => {
            finishResolve();
          };

          const handleCanPlay = () => {
            finishResolve();
          };

          const handleError = () => {
            finishReject(new Error(getMediaErrorMessage(audio.error, candidate.mimeType)));
          };

          audio.addEventListener("playing", handlePlaying);
          audio.addEventListener("canplay", handleCanPlay);
          audio.addEventListener("error", handleError);

          timeoutId = window.setTimeout(() => {
            if (audio.src === candidate.url && !audio.error) {
              finishResolve();
              return;
            }

            finishReject(new Error(`Playback could not start${candidate.mimeType ? ` for ${candidate.mimeType}` : ""}.`));
          }, PLAYBACK_ATTEMPT_TIMEOUT_MS);

          const isIOS = getDeviceInfo().isIOS;
          const targetUrl =
            candidate.url.startsWith("blob:") || candidate.url.startsWith("/api/")
              ? candidate.url
              : isIOS && (candidate.url.includes("googlevideo.com") || candidate.url.includes("http"))
                ? `/api/audio-proxy?url=${encodeURIComponent(candidate.url)}`
                : candidate.url;

          if (audio.src !== targetUrl) {
            audio.src = targetUrl;
            audio.load();
          }

          clearManualPauseRequested();
          void audio.play().then(finishResolve).catch(finishReject);
        });

        return;
      } catch (error) {
        lastError = error;
      }
    }
  } finally {
    sourceSelectionInProgress = false;
  }

  throw lastError instanceof Error ? lastError : new Error("Playback failed.");
}

async function prewarmUpcomingTracks(queue: Song[], currentIndex: number) {
  const upcomingTracks = queue.slice(currentIndex + 1, currentIndex + 1 + getUpcomingPreloadCount());

  await Promise.all(
    upcomingTracks.map(async (track) => {
      try {
        return await audioController.prepareTrack(track);
      } catch {
        // Ignore warmup failures so playback stays smooth.
        return null;
      }
    })
  );
}

async function playWithEmbeddedYouTube(track: Song, queue: Song[] = [track], currentIndex = 0, options?: PlaybackQueueContext) {
  if (shouldUseNativePlayback()) {
    return audioController.playSong(track, queue, currentIndex, options);
  }

  const nextPlaybackQueue = buildPlaybackQueue(track, queue, currentIndex);
  const queueContext = getQueueContext(options);
  const sameTrack = getPlayerState().currentSong?.id === track.id && activePlaybackEngine === "youtube";

  if (sameTrack) {
    setPlayerState({
      currentSong: track,
      queue: nextPlaybackQueue.queue,
      currentIndex: nextPlaybackQueue.currentIndex,
      queueSource: queueContext.queueSource,
      queueSourceLabel: queueContext.queueSourceLabel,
      playlistBaseTrackIds: queueContext.playlistBaseTrackIds,
      playlistMixEnabled: queueContext.playlistMixEnabled,
      isLoading: false,
      error: null
    });

    const player = await ensureHiddenYouTubePlayer();
    player.setVolume(getPlayerState().volume * 100);

    if (window.YT?.PlayerState && player.getPlayerState() !== window.YT.PlayerState.PLAYING) {
      clearManualPauseRequested();
      player.playVideo();
    }

    scheduleDeferredQueueEnhancement(track.id);
    return;
  }

  playerActions.primePlayback(track, nextPlaybackQueue.queue, nextPlaybackQueue.currentIndex, queueContext);

  preparePlaybackTransition(track);
  const player = await ensureHiddenYouTubePlayer();
  preferEmbeddedPlayback = true;
  activePlaybackEngine = "youtube";
  restoreUnderlyingPlaybackAfterVisibleVideoMode();
  resetPlaybackTimingState();
  stopHtmlAudioPlayback();
  player.setVolume(getPlayerState().volume * 100);
  loadYouTubePlaybackQueue(player, nextPlaybackQueue.queue, nextPlaybackQueue.currentIndex, 0);
  clearManualPauseRequested();
  player.playVideo();
  syncYouTubeTiming();
  scheduleDeferredQueueEnhancement(track.id);
}

export const audioController = {
  init() {
    attachAudioListeners();
    preferEmbeddedPlayback = isHostedRuntime() && shouldAllowEmbeddedFallback();
    void hydrateListenerProfile();
    ensureRecommendationSession();

    if (isHostedRuntime() && shouldAllowEmbeddedFallback()) {
      warmEmbeddedPlayback();
    }
  },
  armBackgroundPlaybackGuard() {
    armBackgroundPlaybackGuard();
  },
  clearBackgroundPlaybackGuard() {
    clearBackgroundPlaybackGuard();
  },
  seedTrack(track: Song) {
    if (shouldUseEmbeddedPlayback()) {
      warmEmbeddedPlayback();
      return;
    }

    const seeded = seedStreamFromTrack(track);

    if (seeded) {
      preloadResolvedTrack(track, seeded);
    }
  },
  seedTracks(tracks: Song[]) {
    if (shouldUseEmbeddedPlayback()) {
      warmEmbeddedPlayback();
      return;
    }

    tracks.forEach((track) => {
      this.seedTrack(track);
    });
  },
  async prepareTrack(track: Song) {
    attachAudioListeners();

    if (shouldUseNativePlayback()) {
      return null;
    }

    if (shouldUseEmbeddedPlayback()) {
      warmEmbeddedPlayback();
      return null;
    }

    try {
      const resolved = await fetchStream(track);
      preloadResolvedTrack(track, resolved);
      return resolved;
    } catch {
      // Warmups are best-effort only.
      return null;
    }
  },
  async playSong(track: Song, queue: Song[] = [track], currentIndex = 0, options?: PlaybackQueueContext) {
    attachAudioListeners();
    restoreUnderlyingPlaybackAfterVisibleVideoMode();
    const queueContext = getQueueContext(options);

    if (shouldUseNativePlayback()) {
      const payload = buildNativeQueuePayload(queue, currentIndex);
      const artworkUrl = track.thumbnail || ((track as any).videoId ? `https://i.ytimg.com/vi/${(track as any).videoId}/hqdefault.jpg` : "");
      callNativeBridge("setQueue", payload.queueJson, payload.activeVideoId);
      callNativeBridge("play", (track as any).videoId || track.id, track.title, track.author, artworkUrl);

      const nextPlaybackQueue = buildPlaybackQueue(track, queue, currentIndex);
      playerActions.primePlayback(track, nextPlaybackQueue.queue, nextPlaybackQueue.currentIndex, queueContext);
      preparePlaybackTransition(track);

      setPlayerState((prev) => ({
        ...prev,
        currentSong: track,
        queue: nextPlaybackQueue.queue,
        currentIndex: nextPlaybackQueue.currentIndex,
        queueSource: queueContext.queueSource,
        queueSourceLabel: queueContext.queueSourceLabel,
        playlistBaseTrackIds: queueContext.playlistBaseTrackIds,
        playlistMixEnabled: queueContext.playlistMixEnabled,
        isPlaying: true,
        isLoading: false,
        error: null
      }));
      scheduleDeferredQueueEnhancement(track.id);
      return;
    }

    if (shouldUseEmbeddedPlayback()) {
      try {
        await playWithEmbeddedYouTube(track, queue, currentIndex, queueContext);
      } catch (error) {
        playerActions.setError(error instanceof Error ? error.message : "Playback failed.");
        playerActions.setPlayback(false);
      }
      return;
    }

    const audio = getAudio();
    const nextPlaybackQueue = buildPlaybackQueue(track, queue, currentIndex);
    const currentSongId = getPlayerState().currentSong?.id;
    const isSameTrack = currentSongId === track.id;

    if (!isSameTrack) {
      preparePlaybackTransition(track);
    }

    if (isSameTrack && audio.src) {
      setPlayerState({
        currentSong: track,
        queue: nextPlaybackQueue.queue,
        currentIndex: nextPlaybackQueue.currentIndex,
        queueSource: queueContext.queueSource,
        queueSourceLabel: queueContext.queueSourceLabel,
        playlistBaseTrackIds: queueContext.playlistBaseTrackIds,
        playlistMixEnabled: queueContext.playlistMixEnabled,
        isLoading: false,
        error: null
      });

      if (!audio.paused) {
        scheduleDeferredQueueEnhancement(track.id);
        return;
      }

      try {
        const cachedStream = getCachedResolvedStream(track.id);

        if (cachedStream) {
          await playResolvedTrack(track, cachedStream);
        } else {
          await audio.play();
        }

        scheduleDeferredQueueEnhancement(track.id);
      } catch (error) {
        playerActions.setError(error instanceof Error ? error.message : "Playback failed.");
      }

      return;
    }

    playerActions.primePlayback(track, nextPlaybackQueue.queue, nextPlaybackQueue.currentIndex, queueContext);
    syncMediaSessionMetadata(track);

    // Synchronously unlock singletonAudio with silent WAV on user gesture
    if (audio.paused || !audio.src) {
      try {
        audio.src = SILENT_AUDIO_WAV;
        void audio.play().catch(() => {});
      } catch {
        // Best effort
      }
    }

    try {
      const resolved = await fetchStream(track);
      activePlaybackEngine = "audio";
      pauseYouTubePlayback();
      await playResolvedTrack(track, resolved);
      scheduleDeferredQueueEnhancement(track.id);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Playback failed.";
      const canFallbackToEmbeddedPlayback = shouldAllowEmbeddedFallback();

      if (canFallbackToEmbeddedPlayback && isGlobalStreamFailureMessage(message)) {
        preferEmbeddedPlayback = true;
      }

      if (canFallbackToEmbeddedPlayback) {
        try {
          await playWithEmbeddedYouTube(track, nextPlaybackQueue.queue, nextPlaybackQueue.currentIndex, queueContext);
          return;
        } catch (fallbackError) {
          playerActions.setError(fallbackError instanceof Error ? fallbackError.message : message);
          playerActions.setPlayback(false);
          return;
        }
      }

      playerActions.setError(message);
      playerActions.setPlayback(false);
    }
  },
  async playNext() {
    let { queue, currentIndex, currentSong, queueSource, queueSourceLabel, playlistBaseTrackIds, playlistMixEnabled } = getPlayerState();
    let nextIndex = currentIndex + 1;
    let nextTrack = queue[nextIndex];

    if (!nextTrack && currentSong) {
      const replenishedQueue = await fillSmartQueue(currentSong, queue, currentIndex);
      const nextPlaybackQueue = buildPlaybackQueue(currentSong, replenishedQueue, currentIndex);

      if (getPlayerState().currentSong?.id === currentSong.id) {
        setPlayerState((previous) => ({
          ...previous,
          queue: nextPlaybackQueue.queue,
          currentIndex: nextPlaybackQueue.currentIndex
        }));

        syncHiddenYouTubeQueueWithState();
      }

      queue = nextPlaybackQueue.queue;
      currentIndex = nextPlaybackQueue.currentIndex;
      nextIndex = currentIndex + 1;
      nextTrack = queue[nextIndex];
    }

    if (!nextTrack) {
      if (shouldUseNativePlayback()) {
        callNativeBridge("stop");
      } else if (activePlaybackEngine === "youtube") {
        pauseYouTubePlayback();
      } else {
        const audio = getAudio();
        audio.pause();
      }

      playerActions.setPlayback(false);
      return;
    }

    preparePlaybackTransition(nextTrack);

    if (shouldUseNativePlayback()) {
      await this.playSong(nextTrack, queue, nextIndex, {
        queueSource,
        queueSourceLabel,
        playlistBaseTrackIds,
        playlistMixEnabled
      });
      return;
    }

    if (activePlaybackEngine === "youtube" && hiddenYouTubePlayer?.nextVideo && isHiddenYouTubeQueueReady(queue, nextIndex)) {
      const nextPlaybackQueue = buildPlaybackQueue(nextTrack, queue, nextIndex);
      resetPlaybackTimingState();
      setPlayerState((previous) => ({
        ...previous,
        currentSong: nextTrack,
        queue: nextPlaybackQueue.queue,
        currentIndex: nextPlaybackQueue.currentIndex,
        isLoading: true,
        error: null
      }));
      playerActions.rememberSessionTrack(nextTrack);
      rememberLoadedYouTubeQueueIndex(nextPlaybackQueue.currentIndex);
      scheduleDeferredQueueEnhancement(nextTrack.id);
      hiddenYouTubePlayer.nextVideo();
      return;
    }

    await this.playSong(nextTrack, queue, nextIndex, {
      queueSource,
      queueSourceLabel,
      playlistBaseTrackIds,
      playlistMixEnabled
    });
  },
  async playPrevious() {
    if (shouldUseNativePlayback()) {
      const { queue, currentIndex, queueSource, queueSourceLabel, playlistBaseTrackIds, playlistMixEnabled } = getPlayerState();
      const previousIndex = currentIndex - 1;
      const previousTrack = queue[previousIndex];

      if (!previousTrack) {
        if (shouldUseNativePlayback()) {
          callNativeBridge("seek", 0);
          return;
        }
        return;
      }

      preparePlaybackTransition(previousTrack);
      await this.playSong(previousTrack, queue, previousIndex, {
        queueSource,
        queueSourceLabel,
        playlistBaseTrackIds,
        playlistMixEnabled
      });
      return;
    }

    if (activePlaybackEngine === "youtube" && hiddenYouTubePlayer) {
      if (hiddenYouTubePlayer.getCurrentTime() > 5) {
        hiddenYouTubePlayer.seekTo(0, true);
        syncYouTubeTiming();
        return;
      }
    } else {
      const audio = getAudio();

      if (audio.currentTime > 5) {
        audio.currentTime = 0;
        return;
      }
    }

    const { queue, currentIndex, queueSource, queueSourceLabel, playlistBaseTrackIds, playlistMixEnabled } = getPlayerState();
    const previousIndex = currentIndex - 1;
    const previousTrack = queue[previousIndex];

    if (!previousTrack) {
      if (activePlaybackEngine === "youtube" && hiddenYouTubePlayer) {
        hiddenYouTubePlayer.seekTo(0, true);
        syncYouTubeTiming();
      } else {
        getAudio().currentTime = 0;
      }

      return;
    }

    preparePlaybackTransition(previousTrack);

    if (activePlaybackEngine === "youtube" && hiddenYouTubePlayer?.previousVideo && isHiddenYouTubeQueueReady(queue, previousIndex)) {
      const nextPlaybackQueue = buildPlaybackQueue(previousTrack, queue, previousIndex);
      resetPlaybackTimingState();
      setPlayerState((previous) => ({
        ...previous,
        currentSong: previousTrack,
        queue: nextPlaybackQueue.queue,
        currentIndex: nextPlaybackQueue.currentIndex,
        isLoading: true,
        error: null
      }));
      playerActions.rememberSessionTrack(previousTrack);
      rememberLoadedYouTubeQueueIndex(nextPlaybackQueue.currentIndex);
      scheduleDeferredQueueEnhancement(previousTrack.id);
      hiddenYouTubePlayer.previousVideo();
      return;
    }

    await this.playSong(previousTrack, queue, previousIndex, {
      queueSource,
      queueSourceLabel,
      playlistBaseTrackIds,
      playlistMixEnabled
    });
  },
  async togglePlayback() {
    attachAudioListeners();
    const { currentSong, queue, currentIndex, queueSource, queueSourceLabel, playlistBaseTrackIds, playlistMixEnabled } = getPlayerState();

    if (!currentSong) {
      return;
    }

    if (shouldUseNativePlayback()) {
      const isPlaying = getPlayerState().isPlaying;

      if (isPlaying) {
        markManualPauseRequested();
        callNativeBridge("pause");
      } else {
        clearManualPauseRequested();
        callNativeBridge("resume");
      }

      setPlayerState((prev) => ({
        ...prev,
        isPlaying: !isPlaying,
        isLoading: false
      }));
      return;
    }

    if (activePlaybackEngine === "youtube") {
      if (!hiddenYouTubePlayer) {
        const { queue, currentIndex, queueSource, queueSourceLabel, playlistBaseTrackIds, playlistMixEnabled } = getPlayerState();

        await playWithEmbeddedYouTube(currentSong, queue, currentIndex, {
          queueSource,
          queueSourceLabel,
          playlistBaseTrackIds,
          playlistMixEnabled
        });
        return;
      }

      const player = await ensureHiddenYouTubePlayer();
      const playerState = window.YT?.PlayerState;
      const currentState = playerState ? player.getPlayerState() : null;

      if (playerState && currentState === playerState.PLAYING) {
        markManualPauseRequested();
        player.pauseVideo();
      } else {
        clearManualPauseRequested();
        restoreHiddenYouTubeAfterVisibleVideoMode();
        player.playVideo();
      }

      return;
    }

    const audio = getAudio();

    if (audio.paused) {
      try {
        const cachedStream = getCachedResolvedStream(currentSong.id);

        if (cachedStream) {
          await playResolvedTrack(currentSong, cachedStream);
        } else if (!audio.src) {
          await this.playSong(currentSong, queue, currentIndex, {
            queueSource,
            queueSourceLabel,
            playlistBaseTrackIds,
            playlistMixEnabled
          });
        } else {
          clearManualPauseRequested();
          await audio.play();
        }
      } catch (error) {
        playerActions.setError(error instanceof Error ? error.message : "Playback failed.");
      }
      return;
    }

    markManualPauseRequested();
    audio.pause();
  },
  getAudioElement() {
    return singletonAudio;
  },
  async playExplicit() {
    clearManualPauseRequested();
    const audio = getAudio();
    if (audio.paused && audio.src) {
      try {
        await audio.play();
      } catch {
        // Best effort
      }
    }
  },
  pauseExplicit() {
    markManualPauseRequested();
    const audio = getAudio();
    if (!audio.paused) {
      audio.pause();
    }
  },
  resume() {
    const { isPlaying } = getPlayerState();
    if (!isPlaying) {
      void this.togglePlayback();
    }
  },
  isActivelyPlaying() {
    const playerState = window.YT?.PlayerState;

    if (activePlaybackEngine === "youtube" && hiddenYouTubePlayer && playerState) {
      const currentState = hiddenYouTubePlayer.getPlayerState();
      return currentState === playerState.PLAYING || currentState === playerState.BUFFERING;
    }

    const audio = getAudio();
    return !audio.paused && !audio.ended;
  },
  seek(progress: number) {
    if (shouldUseNativePlayback()) {
      const duration = getPlayerState().duration;

      if (!duration) {
        return;
      }

      const safeProgress = clamp(progress, 0, 1);
      const seconds = safeProgress * duration;
      callNativeBridge("seek", seconds);
      playerActions.setTiming(seconds, duration, duration * safeProgress);
      return;
    }

    if (activePlaybackEngine === "youtube" && hiddenYouTubePlayer) {
      const duration = hiddenYouTubePlayer.getDuration() || 0;

      if (!duration) {
        return;
      }

      const safeProgress = clamp(progress, 0, 1);
      hiddenYouTubePlayer.seekTo(duration * safeProgress, true);
      syncYouTubeTiming();
      return;
    }

    const audio = getAudio();

    if (!audio.duration) {
      return;
    }

    const safeProgress = clamp(progress, 0, 1);
    audio.currentTime = audio.duration * safeProgress;
  },
  setVolume(volume: number) {
    if (shouldUseNativePlayback()) {
      const safeVolume = clamp(volume, 0, 1);
      callNativeBridge("setVolume", Math.round(safeVolume * 100));
      setPlayerState((prev) => ({ ...prev, volume: safeVolume }));
      return;
    }

    const audio = getAudio();
    const safeVolume = clamp(volume, 0, 1);
    audio.volume = safeVolume;

    if (hiddenYouTubePlayer) {
      if (hiddenYouTubeMutedForVisibleVideo) {
        return;
      }

      hiddenYouTubePlayer.setVolume(safeVolume * 100);
    }
  },
  prepareForVisibleVideoMode() {
    visibleVideoModeActive = true;

    if (shouldUseNativePlayback()) {
      callNativeBridge("pause");
      return;
    }

    if (activePlaybackEngine === "youtube") {
      muteHiddenYouTubeForVisibleVideoMode();
      return;
    }

    muteHtmlAudioForVisibleVideoMode();
  },
  suspendUnderlyingForVisibleVideoMode() {
    visibleVideoModeSuspendingUnderlying = true;

    if (shouldUseNativePlayback()) {
      callNativeBridge("pause");
      return;
    }

    if (activePlaybackEngine === "youtube") {
      pauseYouTubePlayback();
      return;
    }

    getAudio().pause();
  },
  completeVisibleVideoModeStart() {
    visibleVideoModeSuspendingUnderlying = false;
  },
  pause() {
    if (shouldUseNativePlayback()) {
      markManualPauseRequested();
      callNativeBridge("pause");
      setPlayerState((prev) => ({ ...prev, isPlaying: false }));
      return;
    }

    if (activePlaybackEngine === "youtube") {
      markManualPauseRequested();
      pauseYouTubePlayback();
      return;
    }

    markManualPauseRequested();
    getAudio().pause();
  },
  seekToSeconds(seconds: number) {
    if (shouldUseNativePlayback()) {
      callNativeBridge("seek", Math.max(0, seconds));
      playerActions.setTiming(seconds, getPlayerState().duration, seconds);
      return;
    }

    if (activePlaybackEngine === "youtube" && hiddenYouTubePlayer) {
      hiddenYouTubePlayer.seekTo(Math.max(0, seconds), true);
      syncYouTubeTiming();
      return;
    }

    const audio = getAudio();
    const safeTime = Math.max(0, seconds);

    if (Number.isFinite(audio.duration) && audio.duration > 0) {
      audio.currentTime = clamp(safeTime, 0, audio.duration);
    } else {
      audio.currentTime = safeTime;
    }

    syncTiming();
  },
  restoreAfterVisibleVideoMode() {
    restoreUnderlyingPlaybackAfterVisibleVideoMode();
  },
  addToQueue(track: Song) {
    void hydrateListenerProfile();
    const { currentSong, queue, currentIndex } = getPlayerState();

    if (!currentSong) {
      playerActions.setQueue([track], 0);
      playerActions.showToast(`Added "${track.title}" to queue.`);
      recordEngagementAction(track, "queue_add");
      void this.prepareTrack(track);
      return;
    }

    const baseQueue = queue.length > 0 ? queue : [currentSong];
    const withoutTrack = baseQueue.filter((queuedTrack, index) => {
      if (queuedTrack.id !== track.id) {
        return true;
      }

      return index === currentIndex && queuedTrack.id === currentSong.id;
    });
    const insertAt = Math.min(currentIndex + 1, withoutTrack.length);
    const nextQueue = dedupeSongs([
      ...withoutTrack.slice(0, insertAt),
      track,
      ...withoutTrack.slice(insertAt)
    ]).slice(0, MAX_PLAYBACK_QUEUE_LENGTH);
    const nextCurrentIndex = Math.max(
      0,
      nextQueue.findIndex((queuedTrack) => queuedTrack.id === currentSong.id)
    );

    setPlayerState((previous) => ({
      ...previous,
      queue: nextQueue,
      currentIndex: nextCurrentIndex,
      currentSong
    }));

    syncHiddenYouTubeQueueWithState();
    playerActions.showToast(`Added "${track.title}" to queue.`);
    recordEngagementAction(track, "queue_add");
    void this.prepareTrack(track);
    void prewarmUpcomingTracks(nextQueue, nextCurrentIndex);
  },
  recordTasteAction(song: Song, action: Exclude<EngagementAction, "play_start" | "skip_early" | "skip_mid" | "skip_late" | "complete">) {
    void hydrateListenerProfile();
    recordEngagementAction(song, action);
  },
  resetListenerProfile() {
    const visitorId = ensureVisitorId();

    listenerProfileHydrationPromise = null;
    queuedEngagementEvents = [];
    clearEngagementFlushTimer();
    persistQueuedEngagementEvents(visitorId, []);
    setListenerProfile(createEmptyListenerProfile(), visitorId);
    relatedQueueCache.clear();
    pendingRelatedQueueRequests.clear();
  },
  setPlaylistMixEnabled(enabled: boolean) {
    const { currentSong, queue, currentIndex, queueSource, playlistBaseTrackIds, queueSourceLabel } = getPlayerState();

    playerActions.setPlaylistMixEnabled(enabled);

    if (!currentSong || queueSource !== "playlist") {
      return;
    }

    if (!enabled) {
      const baseTrackIds = new Set(playlistBaseTrackIds);
      const trimmedQueue = queue.filter((track, index) => index <= currentIndex || baseTrackIds.has(track.id));
      const nextPlaybackQueue = buildPlaybackQueue(currentSong, trimmedQueue, Math.min(currentIndex, trimmedQueue.length - 1));

      setPlayerState((previous) => ({
        ...previous,
        queue: nextPlaybackQueue.queue,
        currentIndex: nextPlaybackQueue.currentIndex,
        queueSourceLabel,
        playlistMixEnabled: false
      }));

      syncHiddenYouTubeQueueWithState();
      void prewarmUpcomingTracks(nextPlaybackQueue.queue, nextPlaybackQueue.currentIndex);
      return;
    }

    setPlayerState((previous) => ({
      ...previous,
      playlistMixEnabled: true
    }));
    void fillSmartQueue(currentSong, queue, currentIndex);
  },
  reorderQueue(fromIndex: number, toIndex: number) {
    const { queue, currentIndex, currentSong } = getPlayerState();

    if (
      fromIndex === toIndex ||
      fromIndex <= currentIndex ||
      toIndex <= currentIndex ||
      fromIndex < 0 ||
      toIndex < 0 ||
      fromIndex >= queue.length ||
      toIndex >= queue.length
    ) {
      return;
    }

    const nextQueue = [...queue];
    const [movedTrack] = nextQueue.splice(fromIndex, 1);

    if (!movedTrack) {
      return;
    }

    nextQueue.splice(toIndex, 0, movedTrack);

    setPlayerState((previous) => ({
      ...previous,
      queue: nextQueue,
      currentIndex,
      currentSong
    }));

    syncHiddenYouTubeQueueWithState();
    void prewarmUpcomingTracks(nextQueue, currentIndex);
  },
  removeFromQueue(index: number) {
    const { queue, currentIndex } = getPlayerState();

    if (index <= currentIndex || index < 0 || index >= queue.length) {
      return;
    }

    const nextQueue = queue.filter((_, i) => i !== index);

    setPlayerState((previous) => ({
      ...previous,
      queue: nextQueue
    }));

    syncHiddenYouTubeQueueWithState();
    void prewarmUpcomingTracks(nextQueue, currentIndex);
  },
  closePlayer() {
    clearPendingPlaybackError();
    clearDeferredQueueEnhancement();
    finalizePlaybackSession("switch");
    pendingHiddenYouTubeQueueSync = null;
    lastLoadedYouTubePlaylistIds = [];
    lastLoadedYouTubeQueueIndex = 0;
    lastLoadedYouTubeQueueKey = "";

    if (shouldUseNativePlayback()) {
      callNativeBridge("stop");
    }

    stopYouTubeSync();
    pauseYouTubePlayback();
    stopHtmlAudioPlayback();
    resetPlaybackTimingState();
    currentPlaybackSession = null;

    setPlayerState((previous) => ({
      ...previous,
      currentSong: null,
      queue: [],
      currentIndex: -1,
      queueSource: "default",
      queueSourceLabel: null,
      playlistBaseTrackIds: [],
      playlistMixEnabled: false,
      isPlaying: false,
      isLoading: false,
      error: null
    }));
  }
};
