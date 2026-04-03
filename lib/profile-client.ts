"use client";

import type { UserProfile } from "@/lib/types";
import { emitProfileUpdatedEvent } from "@/lib/profile-events";

type ProfileResponse = {
  profile?: UserProfile | null;
  error?: string;
};

type ClearScope = "history" | "suggestions";

const PROFILE_CACHE_TTL_MS = 20_000;

let cachedProfile: UserProfile | null | undefined;
let cachedProfileExpiresAt = 0;
let pendingProfileRequest: Promise<UserProfile | null> | null = null;
let profileCacheRevision = 0;

function setProfileCache(profile: UserProfile | null) {
  cachedProfile = profile;
  cachedProfileExpiresAt = Date.now() + PROFILE_CACHE_TTL_MS;
  return profile;
}

export function invalidateProfileCache() {
  cachedProfile = undefined;
  cachedProfileExpiresAt = 0;
  pendingProfileRequest = null;
  profileCacheRevision += 1;
}

export async function fetchProfile(options?: { force?: boolean }) {
  const force = options?.force ?? false;

  if (!force && cachedProfileExpiresAt > Date.now() && cachedProfile !== undefined) {
    return cachedProfile;
  }

  if (!force && pendingProfileRequest) {
    return pendingProfileRequest;
  }

  const requestRevision = profileCacheRevision;
  const request = fetch("/api/profile", {
    method: "GET",
    credentials: "same-origin",
    cache: "no-store",
    headers: {
      Accept: "application/json"
    }
  })
    .then(async (response) => {
      const payload = (await response.json().catch(() => null)) as ProfileResponse | null;

      if (!response.ok) {
        throw new Error(payload?.error || "Unable to load profile.");
      }

      const nextProfile = payload?.profile || null;

      if (requestRevision === profileCacheRevision) {
        setProfileCache(nextProfile);
      }

      return nextProfile;
    })
    .catch((error) => {
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

async function updateProfile(payloadInput: { name?: string; username?: string; profileImageDataUrl?: string | null }) {
  const response = await fetch("/api/profile", {
    method: "PATCH",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json"
    },
    body: JSON.stringify(payloadInput)
  });

  const payload = (await response.json().catch(() => null)) as ProfileResponse | null;

  if (!response.ok || !payload?.profile) {
    throw new Error(payload?.error || "Unable to save your profile.");
  }

  invalidateProfileCache();
  setProfileCache(payload.profile);
  emitProfileUpdatedEvent();
  return payload.profile;
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

  invalidateProfileCache();
}
