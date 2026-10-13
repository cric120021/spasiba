"use client";

import {
  canComputeQuickPicks,
  getComputeDeadline,
  isSafeModeEnabled,
  markQuickPicksComputed
} from "@/lib/resource-governor";
import {
  cacheQuickPicksSnapshot,
  listZeroWasteCacheValues,
  readCachedQuickPicksSnapshot,
  readListenerProfileSnapshot,
  readRecommendationTrackPool,
  rememberRecommendationTracks
} from "@/lib/zero-waste-idb";
import { ensureRecommendationSession, flushRecommendationProfile, readSessionBrainSnapshot } from "@/lib/recommendation-session";
import { fetchSearchResponseWithBudget } from "@/lib/search-client";
import type { ListenerProfile, SearchResponse, Song, TrendingSection } from "@/lib/types";
import { getPrimaryArtistName, normalizeSongTitle } from "@/lib/utils";

export type RecommendationPhase = "cold" | "learning" | "adaptive" | "lock";

export type QuickPicksSnapshot = {
  tracks: Song[];
  phase: RecommendationPhase;
  computedAt: number;
  source: "cache" | "computed" | "fallback";
};

type CandidateBucket = "continueMood" | "goDeeper" | "safeFallback" | "exploreEdge";

const QUICK_PICKS_TRACK_LIMIT = 12;
const QUICK_PICKS_CACHE_TTL_MS = 1000 * 60 * 60 * 24 * 3;
const COLD_START_LIMIT = 18;
const QUALITY_FILTER_PATTERN =
  /\b(lyrics?|lyric|mashup|reels?|shorts?|edit|slowed|reverb|dialogue|scene|status|ringtone|template|reaction|review|podcast|episode|interview|prank)\b/i;
const PUNJABI_PATTERN =
  /[\u0A00-\u0A7F]|\b(punjabi|diljit|aujla|shubh|sidhu|dhillon|dosanjh|randhawa|punjab)\b/i;
const BOLLYWOOD_PATTERN =
  /[\u0900-\u097F]|\b(bollywood|hindi|arijit|jubin|shreya|atif|darshan|vishal|neha|tulsi|mohit|pritam)\b/i;
const LOFI_PATTERN = /\b(lofi|chill|beats?|ambient|study|focus|sleep|instrumental)\b/i;
const LOVE_PATTERN = /\b(love|romantic|romance|heart|ishq|pyaar|pyar|mohabbat|dil)\b/i;
const SAD_PATTERN = /\b(sad|heartbreak|breakup|broken|alone|emotional|pain|cry)\b/i;
const ENERGY_PATTERN = /\b(workout|gym|run|running|energy|party|hype|pump|motivation)\b/i;
const INDIE_PATTERN = /\b(indie|acoustic|non film|alt|alternative|independent)\b/i;
const TRENDING_PATTERN = /\b(trending|viral|hits?|top|chart|popular)\b/i;
const COLD_START_QUERIES = [
  "latest hindi official audio",
  "punjabi hits official audio",
  "lofi chill official audio",
  "english pop official audio"
] as const;

function getDayKey() {
  return new Date().toISOString().slice(0, 10);
}

function getQuickPicksCacheKey() {
  return "home-quick-picks:v2";
}

function hashValue(value: string) {
  let hash = 0;

  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) >>> 0;
  }

  return hash;
}

function getArtistKey(author: string) {
  return normalizeSongTitle(getPrimaryArtistName(author) || author);
}

function getTrackIdentityKey(track: Song) {
  return `${getArtistKey(track.author)}::${normalizeSongTitle(track.title)}`;
}

function detectTrackSignals(track: Song) {
  const text = `${track.title} ${track.author}`.toLowerCase();
  const signals = new Set<string>();

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

  return Array.from(signals);
}

function isHighQualityTrack(track: Song) {
  const title = `${track.title} ${track.author}`;

  if (!track.videoId || QUALITY_FILTER_PATTERN.test(title)) {
    return false;
  }

  if (track.durationSeconds > 0 && track.durationSeconds < 90) {
    return false;
  }

  return /\b(official|audio|video|vevo|topic)\b/i.test(title) || track.views >= 500_000;
}

function getPopularityScore(track: Song) {
  return Math.min(32, Math.log10(Math.max(1, track.views || 1)) * 4);
}

function seedFallbackQuickPicks(sections: TrendingSection[]) {
  const featuredTracks = sections.flatMap((section) => section.tracks).filter(isHighQualityTrack);
  const pickedTracks: Song[] = [];
  const seenTrackIds = new Set<string>();
  const artistCounts = new Map<string, number>();

  for (const track of featuredTracks) {
    const artistKey = getArtistKey(track.author);

    if (seenTrackIds.has(track.id) || (artistKey && (artistCounts.get(artistKey) || 0) >= 2)) {
      continue;
    }

    seenTrackIds.add(track.id);

    if (artistKey) {
      artistCounts.set(artistKey, (artistCounts.get(artistKey) || 0) + 1);
    }

    pickedTracks.push(track);

    if (pickedTracks.length >= QUICK_PICKS_TRACK_LIMIT) {
      break;
    }
  }

  return pickedTracks;
}

function countProfileSignals(profile: ListenerProfile) {
  return Object.values(profile.artists).filter((value) => Math.abs(value) >= 6).length;
}

function determinePhase(profile: ListenerProfile, playCount: number) {
  const strongSignals = countProfileSignals(profile);

  if (playCount < 3 && strongSignals < 3) {
    return "cold" satisfies RecommendationPhase;
  }

  if (playCount < 5 || strongSignals < 6) {
    return "learning" satisfies RecommendationPhase;
  }

  const topArtistScore = Math.max(0, ...Object.values(profile.artists));
  return topArtistScore >= 18 ? "lock" : "adaptive";
}

function scoreTrack(track: Song, profile: ListenerProfile) {
  const sessionBrain = readSessionBrainSnapshot();
  const activeIntent = sessionBrain.intent || "";
  const signals = detectTrackSignals(track);
  const artistKey = getArtistKey(track.author);
  const identityKey = getTrackIdentityKey(track);
  const intentMatch =
    activeIntent && signals.some((signal) => signal === activeIntent || signal.includes(activeIntent))
      ? 16
      : sessionBrain.energy === "low" && signals.includes("lofi-chill")
        ? 10
        : sessionBrain.energy === "high" && signals.includes("high-energy-workout")
          ? 10
          : 0;
  const sessionAffinity =
    (artistKey ? (sessionBrain.artistAffinity[artistKey] || 0) * 3 : 0) +
    signals.reduce((total, signal) => total + (signal === sessionBrain.intent ? 4 : 0), 0);
  const profileAffinity =
    (artistKey ? (profile.artists[artistKey] || 0) : 0) * 1.5 +
    (profile.tracks[identityKey] || 0) * 1.5 +
    signals.reduce((total, signal) => total + (profile.signals[signal] || 0), 0);
  const recentPenalty = sessionBrain.recentTracks.some((recentTrack) => recentTrack.id === track.id) ? 40 : 0;
  const repetitionPenalty = artistKey && sessionBrain.recentTracks.some((recentTrack) => getArtistKey(recentTrack.author) === artistKey) ? 18 : 0;
  const bannedPenalty = artistKey && sessionBrain.bannedArtists.includes(artistKey) ? 100 : 0;
  const freshnessBonus = recentPenalty === 0 ? 8 : 0;
  const qualityBoost = isHighQualityTrack(track) ? 10 : -24;
  const seedJitter = (hashValue(`${getDayKey()}:${track.id}`) % 1000) / 100;

  return intentMatch * 3 + sessionAffinity * 2 + profileAffinity * 1.5 + freshnessBonus + qualityBoost + getPopularityScore(track) - repetitionPenalty - bannedPenalty - recentPenalty + seedJitter;
}

function addTrackIfEligible(
  target: Song[],
  candidate: Song,
  state: {
    usedTrackIds: Set<string>;
    usedIdentityKeys: Set<string>;
    artistCounts: Map<string, number>;
  }
) {
  const artistKey = getArtistKey(candidate.author);
  const identityKey = getTrackIdentityKey(candidate);

  if (
    state.usedTrackIds.has(candidate.id) ||
    state.usedIdentityKeys.has(identityKey) ||
    !isHighQualityTrack(candidate) ||
    (artistKey && (state.artistCounts.get(artistKey) || 0) >= 2)
  ) {
    return false;
  }

  state.usedTrackIds.add(candidate.id);
  state.usedIdentityKeys.add(identityKey);

  if (artistKey) {
    state.artistCounts.set(artistKey, (state.artistCounts.get(artistKey) || 0) + 1);
  }

  target.push(candidate);
  return true;
}

function pickBucketTracks(
  bucket: CandidateBucket,
  candidates: Song[],
  profile: ListenerProfile,
  count: number,
  state: {
    usedTrackIds: Set<string>;
    usedIdentityKeys: Set<string>;
    artistCounts: Map<string, number>;
  },
  deadline: { exceeded(): boolean }
) {
  const sessionBrain = readSessionBrainSnapshot();
  const rankedCandidates = candidates
    .map((track) => ({
      track,
      signals: detectTrackSignals(track),
      score: scoreTrack(track, profile),
      popularity: getPopularityScore(track)
    }))
    .sort((left, right) => right.score - left.score);

  const pickedTracks: Song[] = [];

  for (const candidate of rankedCandidates) {
    if (deadline.exceeded()) {
      break;
    }

    const signals = candidate.signals;
    const prefersExplore =
      bucket === "exploreEdge" &&
      !signals.some((signal) => signal === sessionBrain.intent) &&
      !signals.includes("trending-viral");
    const prefersSafe = bucket === "safeFallback" && candidate.popularity >= 18;
    const prefersMood = bucket === "continueMood" && (signals.includes(sessionBrain.intent || "") || candidate.score >= 18);
    const prefersDepth = bucket === "goDeeper" && candidate.popularity < 26;

    if (
      (bucket === "exploreEdge" && !prefersExplore) ||
      (bucket === "safeFallback" && !prefersSafe) ||
      (bucket === "continueMood" && !prefersMood) ||
      (bucket === "goDeeper" && !prefersDepth)
    ) {
      continue;
    }

    if (addTrackIfEligible(pickedTracks, candidate.track, state) && pickedTracks.length >= count) {
      break;
    }
  }

  if (pickedTracks.length >= count) {
    return pickedTracks;
  }

  for (const candidate of rankedCandidates) {
    if (deadline.exceeded()) {
      break;
    }

    if (addTrackIfEligible(pickedTracks, candidate.track, state) && pickedTracks.length >= count) {
      break;
    }
  }

  return pickedTracks;
}

async function collectLocalCandidates(sections: TrendingSection[]) {
  const [trackPool, cachedSearchPools, cachedRelatedPools] = await Promise.all([
    readRecommendationTrackPool(180),
    listZeroWasteCacheValues<SearchResponse & { requestKey: string }>("search_cache", 10),
    listZeroWasteCacheValues<SearchResponse & { requestKey: string }>("related_cache", 8)
  ]);
  const uniqueTracks = new Map<string, Song>();

  for (const track of [...sections.flatMap((section) => section.tracks), ...trackPool]) {
    if (track?.id) {
      uniqueTracks.set(track.id, track);
    }
  }

  for (const payload of [...cachedSearchPools, ...cachedRelatedPools]) {
    for (const track of payload.results || []) {
      if (track?.id) {
        uniqueTracks.set(track.id, track);
      }
    }
  }

  return Array.from(uniqueTracks.values());
}

async function hydrateColdStartPool(existingCandidates: Song[]) {
  if (existingCandidates.length >= 12 || isSafeModeEnabled()) {
    return existingCandidates;
  }

  const query = COLD_START_QUERIES[hashValue(getDayKey()) % COLD_START_QUERIES.length];
  const searchResponse = await fetchSearchResponseWithBudget({
    query,
    limit: COLD_START_LIMIT,
    category: "music",
    purpose: "cold-start"
  }).catch(() => null);

  const seedTrack = searchResponse?.results.find(isHighQualityTrack);
  const relatedResponse =
    seedTrack
      ? await fetchSearchResponseWithBudget({
          relatedTo: seedTrack.videoId,
          limit: COLD_START_LIMIT,
          purpose: "cold-start"
        }).catch(() => null)
      : null;

  const nextCandidates = new Map(existingCandidates.map((track) => [track.id, track]));

  for (const track of [...(searchResponse?.results || []), ...(relatedResponse?.results || [])]) {
    if (track?.id) {
      nextCandidates.set(track.id, track);
    }
  }

  return Array.from(nextCandidates.values());
}

async function computeQuickPicks(sections: TrendingSection[], reason: "daily" | "heavy") {
  ensureRecommendationSession();
  const deadline = getComputeDeadline(48);
  const fallbackTracks = seedFallbackQuickPicks(sections);

  if (!canComputeQuickPicks(reason) || deadline.exceeded()) {
    return fallbackTracks;
  }

  const localProfile = (await readListenerProfileSnapshot()) || (await flushRecommendationProfile()) || {
    artists: {},
    tracks: {},
    signals: {},
    updatedAt: new Date(0).toISOString()
  };
  const phase = determinePhase(localProfile, readSessionBrainSnapshot().plays);
  let candidates = await collectLocalCandidates(sections);

  if (phase === "cold") {
    candidates = await hydrateColdStartPool(candidates);
  }

  if (deadline.exceeded() || candidates.length === 0) {
    return fallbackTracks;
  }

  const state = {
    usedTrackIds: new Set<string>(),
    usedIdentityKeys: new Set<string>(),
    artistCounts: new Map<string, number>()
  };

  const groupedTracks = [
    ...pickBucketTracks("continueMood", candidates, localProfile, 4, state, deadline),
    ...pickBucketTracks("goDeeper", candidates, localProfile, 3, state, deadline),
    ...pickBucketTracks("safeFallback", candidates, localProfile, 3, state, deadline),
    ...pickBucketTracks("exploreEdge", candidates, localProfile, 2, state, deadline)
  ];

  const nextTracks = groupedTracks.length > 0 ? groupedTracks.slice(0, QUICK_PICKS_TRACK_LIMIT) : fallbackTracks;
  markQuickPicksComputed();

  return nextTracks.length > 0 ? nextTracks : fallbackTracks;
}

export async function seedQuickPicksSources(sections: TrendingSection[]) {
  const sectionTracks = sections.flatMap((section) => section.tracks);

  if (!sectionTracks.length) {
    return;
  }

  await rememberRecommendationTracks(sectionTracks, 1000 * 60 * 60 * 24 * 7);
}

export async function readCachedQuickPicks() {
  return readCachedQuickPicksSnapshot<QuickPicksSnapshot>(getQuickPicksCacheKey());
}

export async function resolveQuickPicksSnapshot(sections: TrendingSection[]) {
  const cached = await readCachedQuickPicks();

  if (cached?.tracks?.length) {
    return {
      ...cached,
      source: "cache" as const
    };
  }

  const tracks = await computeQuickPicks(sections, "daily");
  const profile = (await readListenerProfileSnapshot()) || {
    artists: {},
    tracks: {},
    signals: {},
    updatedAt: new Date(0).toISOString()
  };
  const snapshot: QuickPicksSnapshot = {
    tracks,
    phase: determinePhase(profile, readSessionBrainSnapshot().plays),
    computedAt: Date.now(),
    source: "computed"
  };

  await cacheQuickPicksSnapshot(getQuickPicksCacheKey(), snapshot, QUICK_PICKS_CACHE_TTL_MS);
  return snapshot;
}

export function buildHomeFallbackQuickPicks(sections: TrendingSection[]) {
  return seedFallbackQuickPicks(sections);
}
