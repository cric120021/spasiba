import { ensureDbSchema, ensureVisitorRecord, getDb, hasDatabaseAuthToken } from "@/lib/db";
import type { ProfileSongEntry, Song } from "@/lib/types";

type LikedTrackRow = {
  video_id: string;
  title: string;
  thumbnail: string;
  duration: string;
  duration_seconds: number;
  author: string;
  url: string;
  views: number;
  updated_at: number;
};

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

function mapLikedTrackRow(row: LikedTrackRow): ProfileSongEntry {
  return {
    id: row.video_id,
    videoId: row.video_id,
    title: row.title,
    thumbnail: row.thumbnail,
    duration: row.duration,
    durationSeconds: rowNumber(row.duration_seconds),
    author: row.author,
    url: row.url,
    views: rowNumber(row.views),
    lastListenedAt: new Date(rowNumber(row.updated_at)).toISOString()
  };
}

async function listVisitorLikedSongs(visitorId: string): Promise<ProfileSongEntry[]> {
  const db = getDb();
  const result = await db.execute({
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
        updated_at
      FROM liked_tracks
      WHERE visitor_id = ?
      ORDER BY updated_at DESC
      LIMIT 200
    `,
    args: [visitorId]
  });

  return result.rows.map((row) =>
    mapLikedTrackRow({
      video_id: rowString(row.video_id),
      title: rowString(row.title),
      thumbnail: rowString(row.thumbnail),
      duration: rowString(row.duration),
      duration_seconds: rowNumber(row.duration_seconds),
      author: rowString(row.author),
      url: rowString(row.url),
      views: rowNumber(row.views),
      updated_at: rowNumber(row.updated_at)
    })
  );
}

export async function getVisitorLikedSongsDbReady(visitorId: string): Promise<ProfileSongEntry[]> {
  return listVisitorLikedSongs(visitorId);
}

export async function getVisitorLikedSongs(visitorId: string): Promise<ProfileSongEntry[]> {
  assertDatabaseConfigured();
  await ensureDbSchema();
  return listVisitorLikedSongs(visitorId);
}

export async function isTrackLiked(visitorId: string, videoId: string) {
  if (!videoId.trim()) {
    return false;
  }

  assertDatabaseConfigured();
  await ensureDbSchema();

  const db = getDb();
  const result = await db.execute({
    sql: `
      SELECT 1
      FROM liked_tracks
      WHERE visitor_id = ? AND video_id = ?
      LIMIT 1
    `,
    args: [visitorId, videoId]
  });

  return Boolean(result.rows[0]);
}

export async function likeTrack(visitorId: string, song: Song) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitorRecord(visitorId);

  const db = getDb();
  const timestamp = Date.now();

  await db.execute({
    sql: `
      INSERT INTO liked_tracks (
        visitor_id,
        video_id,
        title,
        thumbnail,
        duration,
        duration_seconds,
        author,
        url,
        views,
        created_at,
        updated_at
      )
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(visitor_id, video_id) DO UPDATE SET
        title = excluded.title,
        thumbnail = excluded.thumbnail,
        duration = excluded.duration,
        duration_seconds = excluded.duration_seconds,
        author = excluded.author,
        url = excluded.url,
        views = excluded.views,
        updated_at = excluded.updated_at
    `,
    args: [
      visitorId,
      song.videoId,
      song.title,
      song.thumbnail,
      song.duration,
      song.durationSeconds,
      song.author,
      song.url,
      song.views,
      timestamp,
      timestamp
    ]
  });
}

export async function unlikeTrack(visitorId: string, videoId: string) {
  if (!videoId.trim()) {
    return;
  }

  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitorRecord(visitorId);

  const db = getDb();
  await db.execute({
    sql: `
      DELETE FROM liked_tracks
      WHERE visitor_id = ? AND video_id = ?
    `,
    args: [visitorId, videoId]
  });
}
