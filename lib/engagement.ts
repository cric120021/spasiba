import { ensureDbSchema, getDb, hasDatabaseAuthToken } from "@/lib/db";
import {
  applyEngagementToListenerProfile,
  createEmptyListenerProfile,
  normalizeListenerProfile
} from "@/lib/listener-profile";
import type { EngagementPayload, ListenerProfile } from "@/lib/types";

type EngagementInput = EngagementPayload & {
  signals?: string[];
};

function assertDatabaseConfigured() {
  if (!hasDatabaseAuthToken()) {
    throw new Error("Turso auth token is missing. Add TURSO_AUTH_TOKEN to continue.");
  }
}

function createTimestamp() {
  return Date.now();
}

function rowString(value: unknown) {
  return typeof value === "string" ? value : String(value || "");
}

async function ensureVisitor(visitorId: string) {
  const db = getDb();

  await db.execute({
    sql: `
      INSERT INTO visitors (id, created_at)
      VALUES (?, ?)
      ON CONFLICT(id) DO NOTHING
    `,
    args: [visitorId, createTimestamp()]
  });
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

  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  return loadStoredListenerProfile(visitorId);
}

export async function recordVisitorEngagement(
  visitorId: string,
  payload: EngagementInput
) {
  return recordVisitorEngagementBatch(visitorId, [payload]);
}

export async function recordVisitorEngagementBatch(visitorId: string, payloads: EngagementInput[]) {
  if (!visitorId.trim()) {
    return createEmptyListenerProfile();
  }

  const validPayloads = payloads.filter((payload) => payload?.song?.videoId && payload?.song?.title && payload?.action);

  if (!validPayloads.length) {
    return createEmptyListenerProfile();
  }

  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const db = getDb();
  const currentProfile = await loadStoredListenerProfile(visitorId);
  let nextProfile = currentProfile;
  const batchTimestamp = createTimestamp();
  const statements = validPayloads.map((payload, index) => {
    const signals = Array.from(new Set((payload.signals || []).filter(Boolean).map((signal) => signal.trim().toLowerCase())));
    nextProfile = applyEngagementToListenerProfile(nextProfile, payload.song, payload.action, signals);

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

  return nextProfile;
}
