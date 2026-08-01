import { ensureDbSchema, ensureVisitorRecord, getDb, hasDatabaseAuthToken } from "@/lib/db";
import { getVisitorLikedSongsDbReady } from "@/lib/likes";
import { normalizeUsername, validateUsername } from "@/lib/usernames";
import type { ProfileSongEntry, UserProfile } from "@/lib/types";

type EngagementRow = {
  video_id: string;
  title: string;
  thumbnail: string;
  duration: string;
  duration_seconds: number;
  author: string;
  url: string;
  views: number;
  action: string;
  created_at: number;
};

type CachedProfileEntry = {
  value: UserProfile;
  expiresAt: number;
};

const PROFILE_SUMMARY_CACHE_TTL_MS = 1000 * 60 * 15;
const PROFILE_FULL_CACHE_TTL_MS = 1000 * 60 * 10;
const visitorProfileSummaryCache = new Map<string, CachedProfileEntry>();
const visitorProfileFullCache = new Map<string, CachedProfileEntry>();

function assertDatabaseConfigured() {
  if (!hasDatabaseAuthToken()) {
    throw new Error("Turso auth token is missing. Add TURSO_AUTH_TOKEN to continue.");
  }
}

function rowNumber(value: unknown) {
  return typeof value === "number" ? value : Number(value || 0);
}

function rowString(value: unknown) {
  return typeof value === "string" ? value : String(value || "");
}

function normalizeProfileImageDataUrl(value: string | null | undefined) {
  if (value === null) {
    return null;
  }

  const trimmed = typeof value === "string" ? value.trim() : "";

  if (!trimmed) {
    return null;
  }

  if (!/^data:image\/(png|jpeg|jpg|webp|gif);base64,/i.test(trimmed)) {
    throw new Error("Profile photo must be a valid image.");
  }

  if (trimmed.length > 450_000) {
    throw new Error("Profile photo is too large.");
  }

  return trimmed;
}

function getMonthRange(referenceDate = new Date()) {
  const start = new Date(referenceDate.getFullYear(), referenceDate.getMonth(), 1);
  const end = new Date(referenceDate.getFullYear(), referenceDate.getMonth() + 1, 1);

  return {
    start: start.getTime(),
    end: end.getTime(),
    label: referenceDate.toLocaleString("en-US", {
      month: "long",
      year: "numeric"
    })
  };
}

function readCachedProfile(cache: Map<string, CachedProfileEntry>, visitorId: string) {
  const cached = cache.get(visitorId);

  if (!cached) {
    return null;
  }

  if (cached.expiresAt <= Date.now()) {
    cache.delete(visitorId);
    return null;
  }

  cache.delete(visitorId);
  cache.set(visitorId, cached);
  return cached.value;
}

function writeCachedProfile(cache: Map<string, CachedProfileEntry>, visitorId: string, profile: UserProfile, ttlMs: number) {
  cache.set(visitorId, {
    value: profile,
    expiresAt: Date.now() + ttlMs
  });

  return profile;
}

export function invalidateVisitorProfileCache(visitorId: string, scope: "all" | "summary" | "full" = "all") {
  if (!visitorId.trim()) {
    return;
  }

  if (scope === "all" || scope === "summary") {
    visitorProfileSummaryCache.delete(visitorId);
  }

  if (scope === "all" || scope === "full") {
    visitorProfileFullCache.delete(visitorId);
  }
}

function mapEngagementRowToSong(row: EngagementRow): ProfileSongEntry {
  return {
    id: row.video_id,
    videoId: row.video_id,
    title: row.title,
    thumbnail: row.thumbnail,
    duration: row.duration,
    durationSeconds: rowNumber(row.duration_seconds),
    author: row.author,
    url: row.url,
    views: rowNumber(row.views)
  };
}

function buildMonthlyHistory(rows: EngagementRow[]) {
  const entries = new Map<string, ProfileSongEntry>();

  for (const row of rows) {
    if (!(row.action === "play_start" || row.action === "complete" || row.action === "skip_early" || row.action === "skip_mid" || row.action === "skip_late")) {
      continue;
    }

    const existing = entries.get(row.video_id);
    const song = mapEngagementRowToSong(row);

    if (!existing) {
      entries.set(row.video_id, {
        ...song,
        lastListenedAt: new Date(rowNumber(row.created_at)).toISOString(),
        playCount: 1
      });
      continue;
    }

    entries.set(row.video_id, {
      ...existing,
      playCount: (existing.playCount || 0) + 1,
      lastListenedAt: existing.lastListenedAt || new Date(rowNumber(row.created_at)).toISOString()
    });
  }

  return Array.from(entries.values())
    .sort((left, right) => Date.parse(right.lastListenedAt || "") - Date.parse(left.lastListenedAt || ""));
}

function buildProfileSnapshot(
  name: string,
  username: string,
  profileImageDataUrl: string | null,
  monthLabel: string,
  monthlyHistory: ProfileSongEntry[] = [],
  likedSongs: ProfileSongEntry[] = []
): UserProfile {
  return {
    name,
    username,
    profileImageDataUrl,
    monthLabel,
    monthlyHistory,
    likedSongs
  };
}

async function readVisitorBasics(visitorId: string) {
  const db = getDb();
  const visitorResult = await db.execute({
    sql: `
      SELECT display_name, profile_image_data_url
      , username
      FROM visitors
      WHERE id = ?
      LIMIT 1
    `,
    args: [visitorId]
  });

  return {
    name: rowString(visitorResult.rows[0]?.display_name).trim(),
    username: rowString(visitorResult.rows[0]?.username).trim(),
    profileImageDataUrl: rowString(visitorResult.rows[0]?.profile_image_data_url).trim() || null
  };
}

export async function getVisitorProfileSummary(visitorId: string): Promise<UserProfile> {
  assertDatabaseConfigured();
  await ensureDbSchema();
  const cached = readCachedProfile(visitorProfileSummaryCache, visitorId);

  if (cached) {
    return cached;
  }

  const basics = await readVisitorBasics(visitorId);
  const { label } = getMonthRange();

  return writeCachedProfile(
    visitorProfileSummaryCache,
    visitorId,
    buildProfileSnapshot(basics.name, basics.username, basics.profileImageDataUrl, label),
    PROFILE_SUMMARY_CACHE_TTL_MS
  );
}

export async function getVisitorProfile(visitorId: string): Promise<UserProfile> {
  assertDatabaseConfigured();
  await ensureDbSchema();
  const cached = readCachedProfile(visitorProfileFullCache, visitorId);

  if (cached) {
    return cached;
  }

  const { start, end, label } = getMonthRange();
  const db = getDb();
  const basics = await readVisitorBasics(visitorId);

  const monthEventsResult = await db.execute({
    sql: `
      SELECT
        video_id,
        title,
        thumbnail,
        duration,
        duration_seconds,
        author,
        url,
        views,
        action,
        created_at
      FROM engagement_events
      WHERE visitor_id = ? AND created_at >= ? AND created_at < ?
      ORDER BY created_at DESC
    `,
    args: [visitorId, start, end]
  });

  const monthRows = monthEventsResult.rows.map((row) => ({
    video_id: rowString(row.video_id),
    title: rowString(row.title),
    thumbnail: rowString(row.thumbnail),
    duration: rowString(row.duration),
    duration_seconds: rowNumber(row.duration_seconds),
    author: rowString(row.author),
    url: rowString(row.url),
    views: rowNumber(row.views),
    action: rowString(row.action),
    created_at: rowNumber(row.created_at)
  }));

  const likedSongs = await getVisitorLikedSongsDbReady(visitorId);

  return writeCachedProfile(
    visitorProfileFullCache,
    visitorId,
    buildProfileSnapshot(
      basics.name,
      basics.username,
      basics.profileImageDataUrl,
      label,
      buildMonthlyHistory(monthRows),
      likedSongs
    ),
    PROFILE_FULL_CACHE_TTL_MS
  );
}

export async function updateVisitorProfile(
  visitorId: string,
  payload: {
    name?: string;
    username?: string;
    profileImageDataUrl?: string | null;
  }
) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitorRecord(visitorId);

  const db = getDb();
  const currentVisitorResult = await db.execute({
    sql: `
      SELECT display_name, profile_image_data_url
      , username
      FROM visitors
      WHERE id = ?
      LIMIT 1
    `,
    args: [visitorId]
  });
  const currentName = rowString(currentVisitorResult.rows[0]?.display_name).trim();
  const currentUsername = rowString(currentVisitorResult.rows[0]?.username).trim();
  const currentProfileImageDataUrl = rowString(currentVisitorResult.rows[0]?.profile_image_data_url).trim() || null;
  const nextName = typeof payload.name === "string" ? payload.name.trim().slice(0, 60) : currentName;
  const nextUsername =
    typeof payload.username === "string" ? validateUsername(payload.username) : currentUsername;
  const nextProfileImageDataUrl =
    Object.prototype.hasOwnProperty.call(payload, "profileImageDataUrl")
      ? normalizeProfileImageDataUrl(payload.profileImageDataUrl)
      : currentProfileImageDataUrl;

  try {
    const statements = [
      {
        sql: `
          UPDATE visitors
          SET display_name = ?, username = ?, profile_image_data_url = ?
          WHERE id = ?
        `,
        args: [nextName, nextUsername, nextProfileImageDataUrl, visitorId]
      }
    ];

    if (currentUsername && currentUsername !== nextUsername) {
      statements.push({
        sql: `
          UPDATE playlist_share_grants
          SET grantee_username = ?
          WHERE grantee_username = ?
        `,
        args: [nextUsername, currentUsername]
      });
    }

    await db.batch(statements, "write");
  } catch (error) {
    const message = error instanceof Error ? error.message.toLowerCase() : "";

    if (message.includes("unique")) {
      throw new Error("That username is already taken.");
    }

    throw error;
  }

  invalidateVisitorProfileCache(visitorId);

  return {
    name: nextName,
    username: nextUsername,
    profileImageDataUrl: nextProfileImageDataUrl
  };
}

export async function findVisitorByUsername(username: string) {
  assertDatabaseConfigured();
  await ensureDbSchema();

  const normalizedUsername = normalizeUsername(username);

  if (!normalizedUsername) {
    return null;
  }

  const db = getDb();
  const result = await db.execute({
    sql: `
      SELECT id, display_name, username
      FROM visitors
      WHERE username = ?
      LIMIT 1
    `,
    args: [normalizedUsername]
  });

  const row = result.rows[0];

  if (!row) {
    return null;
  }

  return {
    visitorId: rowString(row.id),
    displayName: rowString(row.display_name).trim() || null,
    username: rowString(row.username).trim()
  };
}

export async function clearVisitorHistory(visitorId: string) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitorRecord(visitorId);

  const db = getDb();
  await db.execute({
    sql: `
      DELETE FROM engagement_events
      WHERE visitor_id = ? AND action IN (?, ?, ?, ?, ?)
    `,
    args: [visitorId, "play_start", "skip_early", "skip_mid", "skip_late", "complete"]
  });

  invalidateVisitorProfileCache(visitorId, "full");
}

export async function clearVisitorSuggestionData(visitorId: string) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitorRecord(visitorId);

  const db = getDb();
  await db.execute({
    sql: `
      DELETE FROM listener_profiles
      WHERE visitor_id = ?
    `,
    args: [visitorId]
  });
}
