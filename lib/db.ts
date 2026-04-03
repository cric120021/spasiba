import { createClient, type Client } from "@libsql/client";

const DEFAULT_TURSO_DATABASE_URL = "libsql://music-app-db-jigar-23.aws-ap-south-1.turso.io";

let client: Client | null = null;
let schemaPromise: Promise<void> | null = null;

function getDatabaseUrl() {
  return process.env.TURSO_DATABASE_URL || process.env.LIBSQL_URL || DEFAULT_TURSO_DATABASE_URL;
}

function getAuthToken() {
  return process.env.TURSO_AUTH_TOKEN || process.env.LIBSQL_AUTH_TOKEN || "";
}

export function getDb() {
  if (!client) {
    const authToken = getAuthToken();

    client = createClient({
      url: getDatabaseUrl(),
      authToken: authToken || undefined
    });
  }

  return client;
}

export function hasDatabaseAuthToken() {
  return Boolean(getAuthToken());
}

export async function ensureDbSchema() {
  if (!schemaPromise) {
    schemaPromise = (async () => {
      const db = getDb();

      await db.batch(
        [
          `
            CREATE TABLE IF NOT EXISTS visitors (
              id TEXT PRIMARY KEY,
              display_name TEXT,
              username TEXT,
              profile_image_data_url TEXT,
              created_at INTEGER NOT NULL
            )
          `,
          `
            CREATE TABLE IF NOT EXISTS playlists (
              id TEXT PRIMARY KEY,
              visitor_id TEXT NOT NULL,
              name TEXT NOT NULL,
              share_token TEXT,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL
            )
          `,
          `
            CREATE TABLE IF NOT EXISTS playlist_tracks (
              id TEXT PRIMARY KEY,
              playlist_id TEXT NOT NULL,
              video_id TEXT NOT NULL,
              title TEXT NOT NULL,
              thumbnail TEXT NOT NULL,
              duration TEXT NOT NULL,
              duration_seconds INTEGER NOT NULL,
              author TEXT NOT NULL,
              url TEXT NOT NULL,
              views INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              UNIQUE(playlist_id, video_id)
            )
          `,
          `
            CREATE TABLE IF NOT EXISTS engagement_events (
              id TEXT PRIMARY KEY,
              visitor_id TEXT NOT NULL,
              video_id TEXT NOT NULL,
              title TEXT NOT NULL,
              thumbnail TEXT NOT NULL DEFAULT '',
              duration TEXT NOT NULL DEFAULT '',
              duration_seconds INTEGER NOT NULL DEFAULT 0,
              author TEXT NOT NULL,
              url TEXT NOT NULL DEFAULT '',
              views INTEGER NOT NULL DEFAULT 0,
              action TEXT NOT NULL,
              progress REAL,
              signals_json TEXT NOT NULL,
              created_at INTEGER NOT NULL
            )
          `,
          `
            CREATE TABLE IF NOT EXISTS listener_profiles (
              visitor_id TEXT PRIMARY KEY,
              profile_json TEXT NOT NULL,
              updated_at INTEGER NOT NULL
            )
          `,
          `
            CREATE TABLE IF NOT EXISTS liked_tracks (
              visitor_id TEXT NOT NULL,
              video_id TEXT NOT NULL,
              title TEXT NOT NULL,
              thumbnail TEXT NOT NULL DEFAULT '',
              duration TEXT NOT NULL DEFAULT '',
              duration_seconds INTEGER NOT NULL DEFAULT 0,
              author TEXT NOT NULL,
              url TEXT NOT NULL DEFAULT '',
              views INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              PRIMARY KEY (visitor_id, video_id)
            )
          `,
          `
            CREATE TABLE IF NOT EXISTS shared_playlist_library (
              playlist_id TEXT NOT NULL,
              subscriber_visitor_id TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              PRIMARY KEY (playlist_id, subscriber_visitor_id)
            )
          `,
          `
            CREATE TABLE IF NOT EXISTS playlist_share_grants (
              playlist_id TEXT NOT NULL,
              grantee_username TEXT NOT NULL,
              granted_by_visitor_id TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              PRIMARY KEY (playlist_id, grantee_username)
            )
          `,
          `CREATE INDEX IF NOT EXISTS playlists_visitor_id_idx ON playlists(visitor_id)`,
          `CREATE INDEX IF NOT EXISTS playlists_visitor_updated_idx ON playlists(visitor_id, updated_at DESC)`,
          `CREATE INDEX IF NOT EXISTS playlist_tracks_playlist_id_idx ON playlist_tracks(playlist_id)`,
          `CREATE INDEX IF NOT EXISTS playlist_tracks_playlist_id_created_at_idx ON playlist_tracks(playlist_id, created_at DESC)`,
          `CREATE INDEX IF NOT EXISTS engagement_events_visitor_id_idx ON engagement_events(visitor_id, created_at DESC)`,
          `CREATE INDEX IF NOT EXISTS engagement_events_visitor_action_idx ON engagement_events(visitor_id, action, created_at DESC)`,
          `CREATE INDEX IF NOT EXISTS engagement_events_video_id_idx ON engagement_events(video_id, created_at DESC)`,
          `CREATE INDEX IF NOT EXISTS liked_tracks_visitor_id_idx ON liked_tracks(visitor_id, updated_at DESC)`,
          `CREATE INDEX IF NOT EXISTS shared_playlist_library_subscriber_idx ON shared_playlist_library(subscriber_visitor_id, created_at DESC)`,
          `CREATE INDEX IF NOT EXISTS shared_playlist_library_playlist_idx ON shared_playlist_library(playlist_id, created_at DESC)`,
          `CREATE INDEX IF NOT EXISTS playlist_share_grants_grantee_idx ON playlist_share_grants(grantee_username, created_at DESC)`,
          `CREATE INDEX IF NOT EXISTS playlist_share_grants_playlist_idx ON playlist_share_grants(playlist_id, created_at DESC)`
        ],
        "write"
      );

      const playlistColumns = await db.execute("PRAGMA table_info(playlists)");
      const hasShareTokenColumn = playlistColumns.rows.some((row) => String(row.name || "").toLowerCase() === "share_token");
      const visitorColumns = await db.execute("PRAGMA table_info(visitors)");
      const visitorColumnNames = new Set(visitorColumns.rows.map((row) => String(row.name || "").toLowerCase()));
      const engagementColumns = await db.execute("PRAGMA table_info(engagement_events)");
      const engagementColumnNames = new Set(engagementColumns.rows.map((row) => String(row.name || "").toLowerCase()));

      if (!hasShareTokenColumn) {
        await db.execute("ALTER TABLE playlists ADD COLUMN share_token TEXT");
      }

      if (!visitorColumnNames.has("display_name")) {
        await db.execute("ALTER TABLE visitors ADD COLUMN display_name TEXT");
      }

      if (!visitorColumnNames.has("username")) {
        await db.execute("ALTER TABLE visitors ADD COLUMN username TEXT");
      }

      if (!visitorColumnNames.has("profile_image_data_url")) {
        await db.execute("ALTER TABLE visitors ADD COLUMN profile_image_data_url TEXT");
      }

      if (!engagementColumnNames.has("thumbnail")) {
        await db.execute("ALTER TABLE engagement_events ADD COLUMN thumbnail TEXT NOT NULL DEFAULT ''");
      }

      if (!engagementColumnNames.has("duration")) {
        await db.execute("ALTER TABLE engagement_events ADD COLUMN duration TEXT NOT NULL DEFAULT ''");
      }

      if (!engagementColumnNames.has("duration_seconds")) {
        await db.execute("ALTER TABLE engagement_events ADD COLUMN duration_seconds INTEGER NOT NULL DEFAULT 0");
      }

      if (!engagementColumnNames.has("url")) {
        await db.execute("ALTER TABLE engagement_events ADD COLUMN url TEXT NOT NULL DEFAULT ''");
      }

      if (!engagementColumnNames.has("views")) {
        await db.execute("ALTER TABLE engagement_events ADD COLUMN views INTEGER NOT NULL DEFAULT 0");
      }

      await db.execute("CREATE UNIQUE INDEX IF NOT EXISTS playlists_share_token_idx ON playlists(share_token)");
      await db.execute("CREATE UNIQUE INDEX IF NOT EXISTS visitors_username_idx ON visitors(username)");
    })().catch((error) => {
      schemaPromise = null;
      throw error;
    });
  }

  return schemaPromise;
}
