import type { EngagementAction, ListenerProfile, Song } from "@/lib/types";
import { clamp, getPrimaryArtistName, normalizeSongTitle } from "@/lib/utils";

const ACTION_WEIGHTS: Record<
  EngagementAction,
  {
    artist: number;
    track: number;
    signal: number;
  }
> = {
  play_start: {
    artist: 2,
    track: 2,
    signal: 1
  },
  skip_early: {
    artist: -7,
    track: -10,
    signal: -5
  },
  skip_mid: {
    artist: -4,
    track: -6,
    signal: -3
  },
  skip_late: {
    artist: -1,
    track: -2,
    signal: -1
  },
  complete: {
    artist: 8,
    track: 10,
    signal: 6
  },
  queue_add: {
    artist: 4,
    track: 7,
    signal: 4
  },
  playlist_add: {
    artist: 10,
    track: 14,
    signal: 8
  },
  like: {
    artist: 12,
    track: 18,
    signal: 10
  },
  unlike: {
    artist: -8,
    track: -14,
    signal: -6
  }
};

const PROFILE_SCORE_LIMIT = 120;
const MAX_ARTIST_PROFILE_SIZE = 180;
const MAX_TRACK_PROFILE_SIZE = 320;
const MAX_SIGNAL_PROFILE_SIZE = 48;

function limitProfileRecord(record: Record<string, number>, maxEntries: number) {
  return Object.fromEntries(
    Object.entries(record)
      .filter(([, value]) => Number.isFinite(value) && value !== 0)
      .sort((left, right) => Math.abs(right[1]) - Math.abs(left[1]))
      .slice(0, maxEntries)
  );
}

function updateProfileRecord(record: Record<string, number>, key: string, delta: number) {
  if (!key || !Number.isFinite(delta) || delta === 0) {
    return record;
  }

  return {
    ...record,
    [key]: clamp((record[key] || 0) + delta, -PROFILE_SCORE_LIMIT, PROFILE_SCORE_LIMIT)
  };
}

export function createEmptyListenerProfile(): ListenerProfile {
  return {
    artists: {},
    tracks: {},
    signals: {},
    updatedAt: new Date(0).toISOString()
  };
}

export function normalizeListenerProfile(profile: Partial<ListenerProfile> | null | undefined): ListenerProfile {
  return {
    artists: profile?.artists || {},
    tracks: profile?.tracks || {},
    signals: profile?.signals || {},
    updatedAt: profile?.updatedAt || new Date(0).toISOString()
  };
}

export function getListenerProfileArtistKey(author: string) {
  return normalizeSongTitle(getPrimaryArtistName(author) || author);
}

export function getListenerProfileTrackKey(song: Song) {
  const artistKey = getListenerProfileArtistKey(song.author);
  const titleKey = normalizeSongTitle(song.title);
  return [artistKey, titleKey].filter(Boolean).join("::");
}

export function applyEngagementToListenerProfile(
  profile: ListenerProfile,
  song: Song,
  action: EngagementAction,
  signals: string[]
) {
  const weights = ACTION_WEIGHTS[action];
  const nextSignals = Array.from(new Set(signals.filter(Boolean).map((signal) => signal.trim().toLowerCase())));
  const artistKey = getListenerProfileArtistKey(song.author);
  const trackKey = getListenerProfileTrackKey(song);

  let nextProfile: ListenerProfile = {
    ...normalizeListenerProfile(profile),
    artists: { ...normalizeListenerProfile(profile).artists },
    tracks: { ...normalizeListenerProfile(profile).tracks },
    signals: { ...normalizeListenerProfile(profile).signals },
    updatedAt: new Date().toISOString()
  };

  nextProfile.artists = updateProfileRecord(nextProfile.artists, artistKey, weights.artist);
  nextProfile.tracks = updateProfileRecord(nextProfile.tracks, trackKey, weights.track);

  for (const signal of nextSignals) {
    nextProfile.signals = updateProfileRecord(nextProfile.signals, signal, weights.signal);
  }

  nextProfile = {
    ...nextProfile,
    artists: limitProfileRecord(nextProfile.artists, MAX_ARTIST_PROFILE_SIZE),
    tracks: limitProfileRecord(nextProfile.tracks, MAX_TRACK_PROFILE_SIZE),
    signals: limitProfileRecord(nextProfile.signals, MAX_SIGNAL_PROFILE_SIZE)
  };

  return nextProfile;
}

export function pickNewerListenerProfile(left: ListenerProfile | null | undefined, right: ListenerProfile | null | undefined) {
  const normalizedLeft = normalizeListenerProfile(left);
  const normalizedRight = normalizeListenerProfile(right);
  return Date.parse(normalizedRight.updatedAt) > Date.parse(normalizedLeft.updatedAt) ? normalizedRight : normalizedLeft;
}
