"use client";

import {
  readLocalState,
  readPersistedCache,
  readPersistedCacheRecord,
  removeLocalState,
  removePersistedCache,
  writeLocalState,
  writePersistedCache
} from "@/lib/client-persisted-cache";
import { emitProfileUpdatedEvent } from "@/lib/profile-events";
import { isCacheOnlyModeEnabled, noteNetworkFailure, noteNetworkSuccess } from "@/lib/resource-governor";
import type { Song, UserProfile } from "@/lib/types";

type ProfileResponse = {
  profile?: UserProfile | null;
  error?: string;
};

type ClearScope = "history" | "suggestions";
type PendingProfilePatch = {
  name?: string;
  username?: string;
  profileImageDataUrl?: string | null;
};

const PROFILE_CACHE_TTL_MS = 1000 * 60 * 60 * 12;
const PROFILE_PERSISTED_CACHE_TTL_MS = 1000 * 60 * 60 * 24 * 30;
const PROFILE_BACKGROUND_SYNC_INTERVAL_MS = 1000 * 60 * 60 * 24 * 7;
const PROFILE_PERSISTED_CACHE_KEY = "profile";
const PROFILE_PENDING_PATCH_KEY = "profile-pending-patch";
const LOCAL_PROFILE_LIKES_LIMIT = 200;

let cachedProfile: UserProfile | null | undefined;
let cachedProfileExpiresAt = 0;
let pendingProfileRequest: Promise<UserProfile | null> | null = null;
let profileCacheRevision = 0;

function getCurrentMonthLabel() {
  return new Intl.DateTimeFormat(undefined, {
    month: "long",
    year: "numeric"
  }).format(new Date());
}

function createEmptyProfile(): UserProfile {
  return {
    name: "",
    username: "",
    profileImageDataUrl: null,
    monthLabel: getCurrentMonthLabel(),
    monthlyHistory: [],
    likedSongs: []
  };
}

function setProfileCache(profile: UserProfile | null) {
  cachedProfile = profile;
  cachedProfileExpiresAt = Date.now() + PROFILE_CACHE_TTL_MS;
  writePersistedCache(PROFILE_PERSISTED_CACHE_KEY, profile, PROFILE_PERSISTED_CACHE_TTL_MS);
  return profile;
}

function getPendingProfilePatch() {
  return readLocalState<PendingProfilePatch>(PROFILE_PENDING_PATCH_KEY);
}

function setPendingProfilePatch(patch: PendingProfilePatch | null) {
  if (!patch || !Object.keys(patch).length) {
    removeLocalState(PROFILE_PENDING_PATCH_KEY);
    return;
  }

  writeLocalState(PROFILE_PENDING_PATCH_KEY, patch);
}

function getLocalProfileSnapshot() {
  if (cachedProfile !== undefined) {
    return cachedProfile;
  }

  const persisted = readPersistedCache<UserProfile | null>(PROFILE_PERSISTED_CACHE_KEY);

  if (persisted !== undefined) {
    cachedProfile = persisted;
    cachedProfileExpiresAt = Date.now() + PROFILE_CACHE_TTL_MS;
    return persisted;
  }

  return undefined;
}

export function readCachedProfile() {
  return getLocalProfileSnapshot();
}

export function hasPendingProfileChanges() {
  return Boolean(getPendingProfilePatch());
}

function applyProfilePatch(baseProfile: UserProfile | null | undefined, patch: PendingProfilePatch) {
  const base = baseProfile || createEmptyProfile();

  return {
    ...base,
    ...(patch.name !== undefined ? { name: patch.name } : {}),
    ...(patch.username !== undefined ? { username: patch.username } : {}),
    ...(Object.prototype.hasOwnProperty.call(patch, "profileImageDataUrl")
      ? { profileImageDataUrl: patch.profileImageDataUrl ?? null }
      : {})
  } satisfies UserProfile;
}

function applyLocalProfile(nextProfile: UserProfile | null, emit = true) {
  setProfileCache(nextProfile);

  if (emit) {
    emitProfileUpdatedEvent();
  }

  return nextProfile;
}

export function invalidateProfileCache() {
  cachedProfile = undefined;
  cachedProfileExpiresAt = 0;
  pendingProfileRequest = null;
  profileCacheRevision += 1;
  removePersistedCache(PROFILE_PERSISTED_CACHE_KEY);
}

function mergeServerProfile(serverProfile: UserProfile | null | undefined, localProfile: UserProfile | null | undefined) {
  if (!serverProfile) {
    return serverProfile ?? localProfile ?? null;
  }

  if (!localProfile) {
    return serverProfile;
  }

  const hasLocalHistory = localProfile.monthlyHistory.length > 0;
  const hasLocalLikes = localProfile.likedSongs.length > 0;
  const mergedMonthlyHistory = hasLocalHistory
    ? [...localProfile.monthlyHistory, ...serverProfile.monthlyHistory]
        .reduce<typeof localProfile.monthlyHistory>((entries, track) => {
          if (!entries.some((entry) => entry.videoId === track.videoId)) {
            entries.push(track);
          }

          return entries;
        }, [])
        .sort((left, right) => Date.parse(right.lastListenedAt || "") - Date.parse(left.lastListenedAt || ""))
    : serverProfile.monthlyHistory;

  return {
    ...serverProfile,
    monthLabel: hasLocalHistory ? localProfile.monthLabel : serverProfile.monthLabel,
    monthlyHistory: mergedMonthlyHistory,
    likedSongs: hasLocalLikes ? localProfile.likedSongs : serverProfile.likedSongs
  } satisfies UserProfile;
}

export async function fetchProfile(options?: { force?: boolean; lean?: boolean }) {
  const force = options?.force ?? false;
  const localProfile = getLocalProfileSnapshot();
  const lean =
    options?.lean ??
    Boolean(localProfile && (localProfile.monthlyHistory.length > 0 || localProfile.likedSongs.length > 0));

  if (!force && cachedProfileExpiresAt > Date.now() && cachedProfile !== undefined) {
    return cachedProfile;
  }

  if (!force) {
    if (localProfile !== undefined) {
      return localProfile;
    }
  }

  if (isCacheOnlyModeEnabled() && localProfile !== undefined) {
    return localProfile;
  }

  if (!force && pendingProfileRequest) {
    return pendingProfileRequest;
  }

  const endpoint = lean ? "/api/profile?mode=light" : "/api/profile";
  const requestRevision = profileCacheRevision;
  const request = fetch(endpoint, {
    method: "GET",
    credentials: "same-origin",
    cache: force ? "reload" : "default",
    headers: {
      Accept: "application/json"
    }
  })
    .then(async (response) => {
      const payload = (await response.json().catch(() => null)) as ProfileResponse | null;

      if (!response.ok) {
        throw new Error(payload?.error || "Unable to load profile.");
      }

      const nextProfile = mergeServerProfile(payload?.profile || null, localProfile);

      if (requestRevision === profileCacheRevision) {
        setProfileCache(nextProfile);
      }

      noteNetworkSuccess();

      return nextProfile;
    })
    .catch((error) => {
      noteNetworkFailure();

      if (localProfile !== undefined) {
        return localProfile;
      }

      if (cachedProfile !== undefined) {
        return cachedProfile;
      }

      throw error;
    })
    .finally(() => {
      pendingProfileRequest = null;
    });

  pendingProfileRequest = request;
  return request;
}

export async function syncProfileWithServer() {
  const localProfile = getLocalProfileSnapshot();

  const pendingPatch = getPendingProfilePatch();

  if (!pendingPatch) {
    const persisted = readPersistedCacheRecord<UserProfile | null>(PROFILE_PERSISTED_CACHE_KEY);

    if (persisted && Date.now() - persisted.storedAt < PROFILE_BACKGROUND_SYNC_INTERVAL_MS) {
      return localProfile ?? persisted.value;
    }
  }

  if (isCacheOnlyModeEnabled() && localProfile !== undefined) {
    return localProfile;
  }

  if (pendingPatch && Object.keys(pendingPatch).length) {
    const patchResponse = await fetch("/api/profile", {
      method: "PATCH",
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json"
      },
      body: JSON.stringify(pendingPatch)
    });

    const patchPayload = (await patchResponse.json().catch(() => null)) as ProfileResponse | null;

    if (!patchResponse.ok) {
      throw new Error(patchPayload?.error || "Unable to save your profile.");
    }

    setPendingProfilePatch(null);
    const mergedProfile = mergeServerProfile(patchPayload?.profile || null, localProfile);
    applyLocalProfile(mergedProfile);
    return mergedProfile;
  }

  const profile = await fetchProfile({ force: true, lean: localProfile !== undefined });
  applyLocalProfile(profile);
  return profile;
}

export async function saveProfileName(name: string) {
  return updateProfile({
    name
  });
}

export async function saveProfileBasics(name: string, username: string) {
  return updateProfile({
    name,
    username
  });
}

export async function saveProfileUsername(username: string) {
  return updateProfile({
    username
  });
}

export async function saveProfilePhoto(profileImageDataUrl: string | null) {
  return updateProfile({
    profileImageDataUrl
  });
}

async function updateProfile(payloadInput: PendingProfilePatch) {
  const nextProfile = applyProfilePatch(getLocalProfileSnapshot(), payloadInput);
  const pendingPatch = {
    ...(getPendingProfilePatch() || {}),
    ...payloadInput
  };

  setPendingProfilePatch(pendingPatch);
  applyLocalProfile(nextProfile);
  return nextProfile;
}

export function recordLocalHistorySong(song: Song) {
  const existing = getLocalProfileSnapshot() || createEmptyProfile();
  const monthLabel = getCurrentMonthLabel();
  const previousHistory = existing.monthLabel === monthLabel ? existing.monthlyHistory : [];
  const existingEntry = previousHistory.find((track) => track.videoId === song.videoId);
  const nextEntry = {
    ...song,
    lastListenedAt: new Date().toISOString(),
    playCount: (existingEntry?.playCount || 0) + 1
  };
  const nextHistory = [nextEntry, ...previousHistory.filter((track) => track.videoId !== song.videoId)];

  applyLocalProfile({
    ...existing,
    monthLabel,
    monthlyHistory: nextHistory
  });
}

export function updateLocalLikedSongs(song: Song, liked: boolean) {
  const existing = getLocalProfileSnapshot() || createEmptyProfile();
  const nextLikedSongs = liked
    ? [
        {
          ...song,
          lastListenedAt: new Date().toISOString()
        },
        ...existing.likedSongs.filter((track) => track.videoId !== song.videoId)
      ].slice(0, LOCAL_PROFILE_LIKES_LIMIT)
    : existing.likedSongs.filter((track) => track.videoId !== song.videoId);

  applyLocalProfile({
    ...existing,
    likedSongs: nextLikedSongs
  });
}

export async function clearProfileData(scope: ClearScope) {
  const response = await fetch(`/api/profile?scope=${encodeURIComponent(scope)}`, {
    method: "DELETE",
    credentials: "same-origin",
    headers: {
      Accept: "application/json"
    }
  });

  const payload = (await response.json().catch(() => null)) as { ok?: boolean; error?: string } | null;

  if (!response.ok) {
    throw new Error(payload?.error || "Unable to clear profile data.");
  }

  if (scope === "history") {
    const existing = getLocalProfileSnapshot() || createEmptyProfile();
    applyLocalProfile({
      ...existing,
      monthLabel: getCurrentMonthLabel(),
      monthlyHistory: []
    });
    return;
  }

  invalidateProfileCache();
}
