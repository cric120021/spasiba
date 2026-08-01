import { ensureDbSchema, ensureVisitorRecord, getDb, hasDatabaseAuthToken } from "@/lib/db";
import {
  applyEngagementToListenerProfile,
  createEmptyListenerProfile,
  normalizeListenerProfile
} from "@/lib/listener-profile";
import type { EngagementPayload, ListenerProfile } from "@/lib/types";

type EngagementInput = EngagementPayload & {
  signals?: string[];
};

type CachedListenerProfileEntry = {
  value: ListenerProfile;
  expiresAt: number;
};

const LISTENER_PROFILE_CACHE_TTL_MS = 1000 * 60 * 10;
const listenerProfileCache = new Map<string, CachedListenerProfileEntry>();

function assertDatabaseConfigured() {
  if (!hasDatabaseAuthToken()) {
    throw new Error("Turso auth token is missing. Add TURSO_AUTH_TOKEN to continue.");
  }
}

function rowString(value: unknown) {
  return typeof value === "string" ? value : String(value || "");
}

function readCachedListenerProfile(visitorId: string) {
  const cached = listenerProfileCache.get(visitorId);

  if (!cached) {
    return null;
  }

  if (cached.expiresAt <= Date.now()) {
    listenerProfileCache.delete(visitorId);
    return null;
  }

  listenerProfileCache.delete(visitorId);
  listenerProfileCache.set(visitorId, cached);
  return cached.value;
}

function writeCachedListenerProfile(visitorId: string, profile: ListenerProfile) {
  listenerProfileCache.set(visitorId, {
    value: profile,
    expiresAt: Date.now() + LISTENER_PROFILE_CACHE_TTL_MS
  });

  return profile;
}

async function loadStoredListenerProfile(visitorId: string) {
  const db = getDb();
  const result = await db.execute({
    sql: `
      SELECT profile_json
      FROM listener_profiles
      WHERE visitor_id = ?
      LIMIT 1
    `,
    args: [visitorId]
  });

  const row = result.rows[0];

  if (!row?.profile_json) {
    return createEmptyListenerProfile();
  }

  try {
    return normalizeListenerProfile(JSON.parse(rowString(row.profile_json)) as ListenerProfile);
  } catch {
    return createEmptyListenerProfile();
  }
}

export async function getVisitorListenerProfile(visitorId: string) {
  if (!visitorId.trim()) {
    return createEmptyListenerProfile();
  }

  const cached = readCachedListenerProfile(visitorId);

  if (cached) {
    return cached;
  }

  assertDatabaseConfigured();
  await ensureDbSchema();

  return writeCachedListenerProfile(visitorId, await loadStoredListenerProfile(visitorId));
}

export async function recordVisitorEngagement(
  visitorId: string,
  payload: EngagementInput
) {
  return recordVisitorEngagementBatch(visitorId, [payload]);
}

export async function recordVisitorEngagementBatch(
  visitorId: string,
  payloads: EngagementInput[],
  nextProfileInput?: ListenerProfile | null
) {
  if (!visitorId.trim()) {
    return createEmptyListenerProfile();
  }

  const validPayloads = payloads.filter((payload) => payload?.song?.videoId && payload?.song?.title && payload?.action);

  if (!validPayloads.length) {
    return createEmptyListenerProfile();
  }

  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitorRecord(visitorId);

  const db = getDb();
  const hasProvidedProfile = Boolean(nextProfileInput);
  let nextProfile = hasProvidedProfile
    ? normalizeListenerProfile(nextProfileInput as ListenerProfile)
    : await loadStoredListenerProfile(visitorId);
  const batchTimestamp = Date.now();
  const statements = validPayloads.map((payload, index) => {
    const signals = Array.from(new Set((payload.signals || []).filter(Boolean).map((signal) => signal.trim().toLowerCase())));

    if (!hasProvidedProfile) {
      nextProfile = applyEngagementToListenerProfile(nextProfile, payload.song, payload.action, signals);
    }

    return {
      sql: `
        INSERT INTO engagement_events (
          id,
          visitor_id,
          video_id,
          title,
          thumbnail,
          duration,
          duration_seconds,
          author,
          url,
          views,
          action,
          progress,
          signals_json,
          created_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `,
      args: [
        crypto.randomUUID(),
        visitorId,
        payload.song.videoId,
        payload.song.title,
        payload.song.thumbnail,
        payload.song.duration,
        payload.song.durationSeconds,
        payload.song.author,
        payload.song.url,
        payload.song.views,
        payload.action,
        payload.progress ?? null,
        JSON.stringify(signals),
        batchTimestamp + index
      ]
    };
  });

  statements.push({
    sql: `
      INSERT INTO listener_profiles (visitor_id, profile_json, updated_at)
      VALUES (?, ?, ?)
      ON CONFLICT(visitor_id) DO UPDATE SET
        profile_json = excluded.profile_json,
        updated_at = excluded.updated_at
    `,
    args: [visitorId, JSON.stringify(nextProfile), batchTimestamp + validPayloads.length]
  });

  await db.batch(statements, "write");

  return writeCachedListenerProfile(visitorId, nextProfile);
}
