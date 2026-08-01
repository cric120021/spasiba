"use client";

import { readCachedProfile } from "@/lib/profile-client";
import {
  applyEngagementToListenerProfile,
  createEmptyListenerProfile,
  normalizeListenerProfile
} from "@/lib/listener-profile";
import type { EngagementAction, ListenerProfile, Song } from "@/lib/types";
import { getPrimaryArtistName, normalizeSongTitle } from "@/lib/utils";
import {
  readListenerProfileSnapshot,
  writeListenerProfileSnapshot
} from "@/lib/zero-waste-idb";

export type SessionBrain = {
  intent: string | null;
  energy: "low" | "medium" | "high";
  skipRate: number;
  recentTracks: Song[];
  artistAffinity: Record<string, number>;
  bannedArtists: string[];
  plays: number;
  skips: number;
  updatedAt: number;
};

type RecommendationSessionEvent = {
  action: EngagementAction;
  song: Song;
  signals: string[];
  recordedAt: number;
};

export const RECOMMENDATION_SESSION_UPDATED_EVENT = "spasiba:recommendation-session-updated";

const SESSION_BRAIN_STORAGE_KEY = "spasiba:session-brain:v1";
const PENDING_PROFILE_EVENTS_KEY = "spasiba:recommendation-events:v1";
const VISITOR_ID_STORAGE_KEY = "spasiba_guest_id";
const LEGACY_LISTENER_PROFILE_PREFIX = "spasiba_listener_profile";
const RECENT_TRACK_LIMIT = 10;
const PENDING_EVENT_LIMIT = 120;

let sessionHydrated = false;
let lifecycleAttached = false;

function getSessionStorage() {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}

function createEmptySessionBrain(): SessionBrain {
  return {
    intent: null,
    energy: "medium",
    skipRate: 0,
    recentTracks: [],
    artistAffinity: {},
    bannedArtists: [],
    plays: 0,
    skips: 0,
    updatedAt: Date.now()
  };
}

function readSessionBrain() {
  const storage = getSessionStorage();

  if (!storage) {
    return createEmptySessionBrain();
  }

  try {
    const rawValue = storage.getItem(SESSION_BRAIN_STORAGE_KEY);

    if (!rawValue) {
      return createEmptySessionBrain();
    }

    const parsed = JSON.parse(rawValue) as Partial<SessionBrain> | null;
    return {
      ...createEmptySessionBrain(),
      ...parsed,
      recentTracks: Array.isArray(parsed?.recentTracks) ? parsed?.recentTracks : [],
      artistAffinity: parsed?.artistAffinity || {},
      bannedArtists: Array.isArray(parsed?.bannedArtists) ? parsed?.bannedArtists : []
    };
  } catch {
    return createEmptySessionBrain();
  }
}

function writeSessionBrain(nextBrain: SessionBrain) {
  const storage = getSessionStorage();

  if (!storage) {
    return nextBrain;
  }

  try {
    storage.setItem(SESSION_BRAIN_STORAGE_KEY, JSON.stringify(nextBrain));
  } catch {
    // Ignore session storage failures.
  }

  return nextBrain;
}

function readPendingEvents() {
  const storage = getSessionStorage();

  if (!storage) {
    return [] as RecommendationSessionEvent[];
  }

  try {
    const rawValue = storage.getItem(PENDING_PROFILE_EVENTS_KEY);

    if (!rawValue) {
      return [] as RecommendationSessionEvent[];
    }

    const parsed = JSON.parse(rawValue) as RecommendationSessionEvent[] | null;
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [] as RecommendationSessionEvent[];
  }
}

function writePendingEvents(events: RecommendationSessionEvent[]) {
  const storage = getSessionStorage();

  if (!storage) {
    return events;
  }

  try {
    storage.setItem(PENDING_PROFILE_EVENTS_KEY, JSON.stringify(events.slice(-PENDING_EVENT_LIMIT)));
  } catch {
    // Ignore session storage failures.
  }

  return events;
}

function getArtistKey(author: string) {
  return normalizeSongTitle(getPrimaryArtistName(author) || author);
}

function inferEnergyFromSignals(signals: string[]): SessionBrain["energy"] {
  if (signals.some((signal) => signal.includes("high-energy") || signal.includes("workout"))) {
    return "high";
  }

  if (signals.some((signal) => signal.includes("lofi") || signal.includes("sad") || signal.includes("sleep"))) {
    return "low";
  }

  return "medium";
}

function inferIntent(recentTracks: Song[], signals: string[]) {
  const recentArtists = new Set(recentTracks.slice(0, 3).map((track) => getArtistKey(track.author)).filter(Boolean));

  if (recentArtists.size <= 2 && recentTracks.length >= 3) {
    return "artist-lock";
  }

  if (signals.length > 0) {
    return signals[0];
  }

  const hour = new Date().getHours();

  if (hour >= 22 || hour <= 4) {
    return "night-chill";
  }

  return null;
}

function dispatchSessionUpdate() {
  if (typeof window === "undefined") {
    return;
  }

  window.dispatchEvent(new CustomEvent(RECOMMENDATION_SESSION_UPDATED_EVENT));
}

function readLegacyListenerProfile() {
  if (typeof window === "undefined") {
    return createEmptyListenerProfile();
  }

  try {
    const visitorId = window.localStorage.getItem(VISITOR_ID_STORAGE_KEY);

    if (visitorId) {
      const rawValue = window.localStorage.getItem(`${LEGACY_LISTENER_PROFILE_PREFIX}:${visitorId}`);

      if (rawValue) {
        return normalizeListenerProfile(JSON.parse(rawValue) as ListenerProfile);
      }
    }
  } catch {
    // Ignore local storage failures.
  }

  const cachedProfile = readCachedProfile();

  if (!cachedProfile) {
    return createEmptyListenerProfile();
  }

  let nextProfile = createEmptyListenerProfile();

  for (const track of cachedProfile.monthlyHistory.slice(0, 40)) {
    nextProfile = applyEngagementToListenerProfile(nextProfile, track, "play_start", []);
  }

  for (const track of cachedProfile.likedSongs.slice(0, 80)) {
    nextProfile = applyEngagementToListenerProfile(nextProfile, track, "like", []);
  }

  return nextProfile;
}

async function hydrateRecommendationSession() {
  if (sessionHydrated) {
    return;
  }

  sessionHydrated = true;
  const existingProfile = await readListenerProfileSnapshot();

  if (!existingProfile) {
    await writeListenerProfileSnapshot(readLegacyListenerProfile());
  }
}

function ensureLifecycleListeners() {
  if (lifecycleAttached || typeof window === "undefined") {
    return;
  }

  lifecycleAttached = true;

  const flush = () => {
    void flushRecommendationProfile();
  };

  document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
      flush();
    }
  });
  window.addEventListener("pagehide", flush);
  window.addEventListener("beforeunload", flush);
}

export function readSessionBrainSnapshot() {
  return readSessionBrain();
}

export function hasHeavyRecommendationUsage() {
  const snapshot = readSessionBrain();
  return snapshot.plays >= 5 || snapshot.skips >= 2 || snapshot.recentTracks.length >= 6;
}

export async function flushRecommendationProfile() {
  await hydrateRecommendationSession();
  const pendingEvents = readPendingEvents();

  if (!pendingEvents.length) {
    return readListenerProfileSnapshot();
  }

  const existingProfile = (await readListenerProfileSnapshot()) || createEmptyListenerProfile();
  let nextProfile = existingProfile;

  for (const event of pendingEvents) {
    nextProfile = applyEngagementToListenerProfile(nextProfile, event.song, event.action, event.signals);
  }

  writePendingEvents([]);
  await writeListenerProfileSnapshot(nextProfile);
  return nextProfile;
}

export function ensureRecommendationSession() {
  void hydrateRecommendationSession();
  ensureLifecycleListeners();
}

export function recordRecommendationEvent(song: Song, action: EngagementAction, signals: string[]) {
  ensureRecommendationSession();

  const currentBrain = readSessionBrain();
  const artistKey = getArtistKey(song.author);
  const isSkip = action === "skip_early" || action === "skip_mid" || action === "skip_late";
  const nextRecentTracks = [song, ...currentBrain.recentTracks.filter((track) => track.id !== song.id)].slice(0, RECENT_TRACK_LIMIT);
  const nextArtistAffinity = {
    ...currentBrain.artistAffinity,
    ...(artistKey
      ? {
          [artistKey]: (currentBrain.artistAffinity[artistKey] || 0) + (isSkip ? -1 : 1)
        }
      : {})
  };
  const repeatedSkipsForArtist =
    isSkip && artistKey
      ? nextRecentTracks.filter((track) => getArtistKey(track.author) === artistKey).length >= 2
      : false;

  const nextBrain: SessionBrain = {
    intent: inferIntent(nextRecentTracks, signals),
    energy: inferEnergyFromSignals(signals),
    skipRate: (currentBrain.skips + (isSkip ? 1 : 0)) / Math.max(1, currentBrain.plays + (!isSkip ? 1 : 0)),
    recentTracks: nextRecentTracks,
    artistAffinity: nextArtistAffinity,
    bannedArtists: repeatedSkipsForArtist && artistKey
      ? Array.from(new Set([...currentBrain.bannedArtists, artistKey])).slice(-8)
      : currentBrain.bannedArtists,
    plays: currentBrain.plays + (action === "play_start" ? 1 : 0),
    skips: currentBrain.skips + (isSkip ? 1 : 0),
    updatedAt: Date.now()
  };

  writeSessionBrain(nextBrain);

  const pendingEvents = readPendingEvents();
  const nextEvent: RecommendationSessionEvent = {
    action,
    song,
    signals,
    recordedAt: Date.now()
  };
  const lastEvent = pendingEvents[pendingEvents.length - 1];

  if (
    lastEvent &&
    lastEvent.song.id === nextEvent.song.id &&
    lastEvent.action === nextEvent.action &&
    nextEvent.recordedAt - lastEvent.recordedAt < 15_000
  ) {
    return;
  }

  writePendingEvents([...pendingEvents, nextEvent].slice(-PENDING_EVENT_LIMIT));
  dispatchSessionUpdate();
}
