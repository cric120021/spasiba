import { createClient, type Client } from "@libsql/client";

const DEFAULT_TURSO_DATABASE_URL = "libsql://music-app-db-jigar-23.aws-ap-south-1.turso.io";
const SCHEMA_READY_KEY = "spasiba_schema";
const SCHEMA_VERSION = 4;
const VISITOR_READY_TTL_MS = 1000 * 60 * 15;

let client: Client | null = null;
let schemaPromise: Promise<void> | null = null;
const visitorReadyCache = new Map<string, number>();

function getDatabaseUrl() {
  const rawUrl = process.env.TURSO_DATABASE_URL || process.env.LIBSQL_URL || DEFAULT_TURSO_DATABASE_URL;
  if (rawUrl.startsWith("libsql://")) {
    return rawUrl.replace("libsql://", "https://");
  }
  return rawUrl;
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

async function isSchemaMarkedReady(db: Client) {
  try {
    const result = await db.execute({
      sql: `
        SELECT version
        FROM schema_meta
        WHERE key = ?
        LIMIT 1
      `,
      args: [SCHEMA_READY_KEY]
    });

    return Number(result.rows[0]?.version || 0) >= SCHEMA_VERSION;
  } catch {
    return false;
  }
}

let isSchemaReadyInMemory = false;

export async function ensureDbSchema() {
  if (isSchemaReadyInMemory) return;

  if (!schemaPromise) {
    schemaPromise = (async () => {
      const db = getDb();

      if (await isSchemaMarkedReady(db)) {
        isSchemaReadyInMemory = true;
        return;
      }

      await db.batch(
        [
          `
            CREATE TABLE IF NOT EXISTS schema_meta (
              key TEXT PRIMARY KEY,
              version INTEGER NOT NULL,
              updated_at INTEGER NOT NULL
            )
          `,
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
              track_count INTEGER NOT NULL DEFAULT 0,
              cover_video_id TEXT,
              cover_title TEXT,
              cover_thumbnail TEXT,
              cover_duration TEXT,
              cover_duration_seconds INTEGER NOT NULL DEFAULT 0,
              cover_author TEXT,
              cover_url TEXT,
              cover_views INTEGER NOT NULL DEFAULT 0,
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
              granted_at INTEGER NOT NULL,
              PRIMARY KEY (playlist_id, grantee_username)
            )
          `,
          `
            CREATE TABLE IF NOT EXISTS eunoia_signals (
              id TEXT PRIMARY KEY,
              target_id TEXT NOT NULL,
              from_id TEXT NOT NULL,
              payload_json TEXT NOT NULL,
              created_at INTEGER NOT NULL
            )
          `,
          `
            CREATE TABLE IF NOT EXISTS devices (
              device_id TEXT PRIMARY KEY,
              user_id TEXT,
              fcm_token TEXT,
              app_version TEXT,
              mobile_number TEXT,
              is_blocked INTEGER NOT NULL DEFAULT 0,
              eunoia_disabled INTEGER NOT NULL DEFAULT 0,
              block_reason TEXT,
              last_seen INTEGER NOT NULL
            )
          `,
          `
            CREATE TABLE IF NOT EXISTS blocked_numbers (
              mobile_number TEXT PRIMARY KEY,
              reason TEXT,
              created_at INTEGER NOT NULL
            )
          `,
          `
            CREATE TABLE IF NOT EXISTS eunoia_sessions (
              session_id TEXT PRIMARY KEY,
              inviter_device_id TEXT NOT NULL,
              invitee_device_id TEXT NOT NULL,
              status TEXT NOT NULL,
              current_song_json TEXT,
              queue_json TEXT,
              position_ms INTEGER NOT NULL DEFAULT 0,
              is_playing INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              expires_at INTEGER NOT NULL
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
          `CREATE INDEX IF NOT EXISTS playlist_share_grants_playlist_idx ON playlist_share_grants(playlist_id, created_at DESC)`,
          `CREATE INDEX IF NOT EXISTS eunoia_signals_target_idx ON eunoia_signals(target_id, created_at ASC)`,
          `CREATE INDEX IF NOT EXISTS eunoia_sessions_inviter_idx ON eunoia_sessions(inviter_device_id, updated_at DESC)`,
          `CREATE INDEX IF NOT EXISTS eunoia_sessions_invitee_idx ON eunoia_sessions(invitee_device_id, updated_at DESC)`
        ],
        "write"
      );

      const playlistColumns = await db.execute("PRAGMA table_info(playlists)");
      const playlistColumnNames = new Set(playlistColumns.rows.map((row) => String(row.name || "").toLowerCase()));
      const hasShareTokenColumn = playlistColumnNames.has("share_token");
      const shouldBackfillPlaylistSummaries =
        !playlistColumnNames.has("track_count") ||
        !playlistColumnNames.has("cover_video_id") ||
        !playlistColumnNames.has("cover_title") ||
        !playlistColumnNames.has("cover_thumbnail") ||
        !playlistColumnNames.has("cover_duration") ||
        !playlistColumnNames.has("cover_duration_seconds") ||
        !playlistColumnNames.has("cover_author") ||
        !playlistColumnNames.has("cover_url") ||
        !playlistColumnNames.has("cover_views");
      const visitorColumns = await db.execute("PRAGMA table_info(visitors)");
      const visitorColumnNames = new Set(visitorColumns.rows.map((row) => String(row.name || "").toLowerCase()));
      const engagementColumns = await db.execute("PRAGMA table_info(engagement_events)");
      const engagementColumnNames = new Set(engagementColumns.rows.map((row) => String(row.name || "").toLowerCase()));

      if (!hasShareTokenColumn) {
        await db.execute("ALTER TABLE playlists ADD COLUMN share_token TEXT");
      }

      if (!playlistColumnNames.has("track_count")) {
        await db.execute("ALTER TABLE playlists ADD COLUMN track_count INTEGER NOT NULL DEFAULT 0");
      }

      if (!playlistColumnNames.has("cover_video_id")) {
        await db.execute("ALTER TABLE playlists ADD COLUMN cover_video_id TEXT");
      }

      if (!playlistColumnNames.has("cover_title")) {
        await db.execute("ALTER TABLE playlists ADD COLUMN cover_title TEXT");
      }

      if (!playlistColumnNames.has("cover_thumbnail")) {
        await db.execute("ALTER TABLE playlists ADD COLUMN cover_thumbnail TEXT");
      }

      if (!playlistColumnNames.has("cover_duration")) {
        await db.execute("ALTER TABLE playlists ADD COLUMN cover_duration TEXT");
      }

      if (!playlistColumnNames.has("cover_duration_seconds")) {
        await db.execute("ALTER TABLE playlists ADD COLUMN cover_duration_seconds INTEGER NOT NULL DEFAULT 0");
      }

      if (!playlistColumnNames.has("cover_author")) {
        await db.execute("ALTER TABLE playlists ADD COLUMN cover_author TEXT");
      }

      if (!playlistColumnNames.has("cover_url")) {
        await db.execute("ALTER TABLE playlists ADD COLUMN cover_url TEXT");
      }

      if (!playlistColumnNames.has("cover_views")) {
        await db.execute("ALTER TABLE playlists ADD COLUMN cover_views INTEGER NOT NULL DEFAULT 0");
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

      const deviceColumns = await db.execute("PRAGMA table_info(devices)");
      const deviceColumnNames = new Set(deviceColumns.rows.map((row) => String(row.name || "").toLowerCase()));

      if (!deviceColumnNames.has("mobile_number")) {
        await db.execute("ALTER TABLE devices ADD COLUMN mobile_number TEXT");
      }
      if (!deviceColumnNames.has("is_blocked")) {
        await db.execute("ALTER TABLE devices ADD COLUMN is_blocked INTEGER NOT NULL DEFAULT 0");
      }
      if (!deviceColumnNames.has("eunoia_disabled")) {
        await db.execute("ALTER TABLE devices ADD COLUMN eunoia_disabled INTEGER NOT NULL DEFAULT 0");
      }
      if (!deviceColumnNames.has("block_reason")) {
        await db.execute("ALTER TABLE devices ADD COLUMN block_reason TEXT");
      }

      if (shouldBackfillPlaylistSummaries) {
        await db.execute(`
          UPDATE playlists
          SET
            track_count = COALESCE((
              SELECT COUNT(*)
              FROM playlist_tracks
              WHERE playlist_tracks.playlist_id = playlists.id
            ), 0),
            cover_video_id = (
              SELECT pt.video_id
              FROM playlist_tracks AS pt
              WHERE pt.playlist_id = playlists.id
              ORDER BY pt.created_at DESC
              LIMIT 1
            ),
            cover_title = (
              SELECT pt.title
              FROM playlist_tracks AS pt
              WHERE pt.playlist_id = playlists.id
              ORDER BY pt.created_at DESC
              LIMIT 1
            ),
            cover_thumbnail = (
              SELECT pt.thumbnail
              FROM playlist_tracks AS pt
              WHERE pt.playlist_id = playlists.id
              ORDER BY pt.created_at DESC
              LIMIT 1
            ),
            cover_duration = (
              SELECT pt.duration
              FROM playlist_tracks AS pt
              WHERE pt.playlist_id = playlists.id
              ORDER BY pt.created_at DESC
              LIMIT 1
            ),
            cover_duration_seconds = COALESCE((
              SELECT pt.duration_seconds
              FROM playlist_tracks AS pt
              WHERE pt.playlist_id = playlists.id
              ORDER BY pt.created_at DESC
              LIMIT 1
            ), 0),
            cover_author = (
              SELECT pt.author
              FROM playlist_tracks AS pt
              WHERE pt.playlist_id = playlists.id
              ORDER BY pt.created_at DESC
              LIMIT 1
            ),
            cover_url = (
              SELECT pt.url
              FROM playlist_tracks AS pt
              WHERE pt.playlist_id = playlists.id
              ORDER BY pt.created_at DESC
              LIMIT 1
            ),
            cover_views = COALESCE((
              SELECT pt.views
              FROM playlist_tracks AS pt
              WHERE pt.playlist_id = playlists.id
              ORDER BY pt.created_at DESC
              LIMIT 1
            ), 0)
        `);
      }

      await db.batch(
        [
          "CREATE UNIQUE INDEX IF NOT EXISTS playlists_share_token_idx ON playlists(share_token)",
          "CREATE UNIQUE INDEX IF NOT EXISTS visitors_username_idx ON visitors(username)",
          {
            sql: `
              INSERT INTO schema_meta (key, version, updated_at)
              VALUES (?, ?, ?)
              ON CONFLICT(key) DO UPDATE SET
                version = excluded.version,
                updated_at = excluded.updated_at
            `,
            args: [SCHEMA_READY_KEY, SCHEMA_VERSION, Date.now()]
          }
        ],
        "write"
      );
      isSchemaReadyInMemory = true;
    })().catch((error) => {
      isSchemaReadyInMemory = false;
      schemaPromise = null;
      throw error;
    });
  }

  return schemaPromise;
}

export async function ensureVisitorRecord(visitorId: string) {
  const normalizedVisitorId = visitorId.trim();

  if (!normalizedVisitorId) {
    return;
  }

  const readyUntil = visitorReadyCache.get(normalizedVisitorId) || 0;

  if (readyUntil > Date.now()) {
    return;
  }

  const db = getDb();
  await db.execute({
    sql: `
      INSERT INTO visitors (id, created_at)
      VALUES (?, ?)
      ON CONFLICT(id) DO NOTHING
    `,
    args: [normalizedVisitorId, Date.now()]
  });

  visitorReadyCache.set(normalizedVisitorId, Date.now() + VISITOR_READY_TTL_MS);
}

export function invalidateVisitorRecordWarmCache(visitorId: string) {
  if (!visitorId.trim()) {
    return;
  }

  visitorReadyCache.delete(visitorId.trim());
}
