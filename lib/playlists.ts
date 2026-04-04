import type { InArgs } from "@libsql/client";

import { ensureDbSchema, getDb, hasDatabaseAuthToken } from "@/lib/db";
import { findVisitorByUsername } from "@/lib/profile";
import type { Playlist, PlaylistShareGrant, Song } from "@/lib/types";

type PlaylistRow = {
  id: string;
  name: string;
  share_token: string | null;
  owner_name?: string | null;
  created_at: number;
  updated_at: number;
  track_count: number;
  cover_video_id?: string | null;
  cover_title?: string | null;
  cover_thumbnail?: string | null;
  cover_duration?: string | null;
  cover_duration_seconds?: number | null;
  cover_author?: string | null;
  cover_url?: string | null;
  cover_views?: number | null;
};

type PlaylistTrackRow = {
  playlist_id: string;
  video_id: string;
  title: string;
  thumbnail: string;
  duration: string;
  duration_seconds: number;
  author: string;
  url: string;
  views: number;
};

type PlaylistShareGrantRow = {
  grantee_username: string;
  username: string;
  display_name?: string | null;
  created_at: number;
};

function assertDatabaseConfigured() {
  if (!hasDatabaseAuthToken()) {
    throw new Error("Turso auth token is missing. Add TURSO_AUTH_TOKEN to continue.");
  }
}

function createTimestamp() {
  return Date.now();
}

function rowNumber(value: unknown) {
  return typeof value === "number" ? value : Number(value || 0);
}

function rowString(value: unknown) {
  return typeof value === "string" ? value : String(value || "");
}

function mapTrackRow(row: PlaylistTrackRow): Song {
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

function mapCoverTrackFromRow(row: PlaylistRow): Song | null {
  const videoId = row.cover_video_id ? rowString(row.cover_video_id).trim() : "";

  if (!videoId) {
    return null;
  }

  return {
    id: videoId,
    videoId,
    title: rowString(row.cover_title),
    thumbnail: rowString(row.cover_thumbnail),
    duration: rowString(row.cover_duration),
    durationSeconds: rowNumber(row.cover_duration_seconds),
    author: rowString(row.cover_author),
    url: rowString(row.cover_url),
    views: rowNumber(row.cover_views)
  };
}

function mapPlaylistRow(row: PlaylistRow): Playlist {
  return {
    id: row.id,
    name: row.name,
    createdAt: new Date(rowNumber(row.created_at)).toISOString(),
    updatedAt: new Date(rowNumber(row.updated_at)).toISOString(),
    shareToken: row.share_token ? rowString(row.share_token) : null,
    ownerName: row.owner_name ? rowString(row.owner_name) : null,
    trackCount: rowNumber(row.track_count),
    coverTrack: mapCoverTrackFromRow(row),
    tracks: []
  };
}

function mapShareGrantRow(row: PlaylistShareGrantRow): PlaylistShareGrant {
  return {
    username: rowString(row.username),
    displayName: rowString(row.display_name).trim() || null,
    createdAt: new Date(rowNumber(row.created_at)).toISOString()
  };
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

function createInClauseArgs(values: string[]) {
  return values.map(() => "?").join(", ");
}

async function loadPlaylistTracks(playlistIds: string[]) {
  if (!playlistIds.length) {
    return new Map<string, Song[]>();
  }

  const db = getDb();
  const trackArgs: InArgs = playlistIds;
  const tracksResult = await db.execute({
    sql: `
      SELECT
        playlist_id,
        video_id,
        title,
        thumbnail,
        duration,
        duration_seconds,
        author,
        url,
        views
      FROM playlist_tracks
      WHERE playlist_id IN (${createInClauseArgs(playlistIds)})
      ORDER BY created_at DESC
    `,
    args: trackArgs
  });

  const tracksByPlaylist = new Map<string, Song[]>();

  tracksResult.rows.forEach((row) => {
    const playlistId = rowString(row.playlist_id);
    const tracks = tracksByPlaylist.get(playlistId) || [];
    tracks.push(
      mapTrackRow({
        playlist_id: playlistId,
        video_id: rowString(row.video_id),
        title: rowString(row.title),
        thumbnail: rowString(row.thumbnail),
        duration: rowString(row.duration),
        duration_seconds: rowNumber(row.duration_seconds),
        author: rowString(row.author),
        url: rowString(row.url),
        views: rowNumber(row.views)
      })
    );
    tracksByPlaylist.set(playlistId, tracks);
  });

  return tracksByPlaylist;
}

function createShareToken() {
  return crypto.randomUUID().replaceAll("-", "").slice(0, 10).toUpperCase();
}

async function loadPlaylistByOwner(visitorId: string, playlistId: string) {
  const db = getDb();
  const result = await db.execute({
    sql: `
      SELECT id, visitor_id, share_token
      FROM playlists
      WHERE id = ? AND visitor_id = ?
      LIMIT 1
    `,
    args: [playlistId, visitorId]
  });

  return result.rows[0] || null;
}

async function loadPlaylistByShareToken(shareToken: string) {
  const db = getDb();
  const result = await db.execute({
    sql: `
      SELECT id, visitor_id, share_token
      FROM playlists
      WHERE share_token = ?
      LIMIT 1
    `,
    args: [shareToken.trim()]
  });

  return result.rows[0] || null;
}

async function ensurePlaylistShareToken(visitorId: string, playlistId: string) {
  const existing = await loadPlaylistByOwner(visitorId, playlistId);

  if (!existing) {
    throw new Error("Playlist not found.");
  }

  const existingShareToken = rowString(existing.share_token).trim();

  if (existingShareToken) {
    return existingShareToken;
  }

  const db = getDb();

  for (let attempt = 0; attempt < 5; attempt += 1) {
    const shareToken = createShareToken();

    try {
      await db.execute({
        sql: `
          UPDATE playlists
          SET share_token = ?
          WHERE id = ? AND visitor_id = ?
        `,
        args: [shareToken, playlistId, visitorId]
      });

      return shareToken;
    } catch (error) {
      const message = error instanceof Error ? error.message.toLowerCase() : "";

      if (!message.includes("unique")) {
        throw error;
      }
    }
  }

  throw new Error("Unable to create a share code right now.");
}

async function loadPlaylistShareGrants(playlistId: string) {
  const db = getDb();
  const result = await db.execute({
    sql: `
      SELECT
        playlist_share_grants.grantee_username,
        visitors.username,
        visitors.display_name,
        playlist_share_grants.created_at
      FROM playlist_share_grants
      LEFT JOIN visitors ON visitors.username = playlist_share_grants.grantee_username
      WHERE playlist_share_grants.playlist_id = ?
      ORDER BY playlist_share_grants.created_at ASC
    `,
    args: [playlistId]
  });

  return result.rows.map((row) =>
    mapShareGrantRow({
      grantee_username: rowString(row.grantee_username),
      username: rowString(row.username) || rowString(row.grantee_username),
      display_name: rowString(row.display_name) || null,
      created_at: rowNumber(row.created_at)
    })
  );
}

async function getVisitorUsername(visitorId: string) {
  const db = getDb();
  const result = await db.execute({
    sql: `
      SELECT username
      FROM visitors
      WHERE id = ?
      LIMIT 1
    `,
    args: [visitorId]
  });

  return rowString(result.rows[0]?.username).trim().toLowerCase();
}

async function hasPlaylistGrant(visitorId: string, playlistId: string) {
  const username = await getVisitorUsername(visitorId);

  if (!username) {
    return false;
  }

  const db = getDb();
  const result = await db.execute({
    sql: `
      SELECT 1
      FROM playlist_share_grants
      WHERE playlist_id = ? AND grantee_username = ?
      LIMIT 1
    `,
    args: [playlistId, username]
  });

  return Boolean(result.rows[0]);
}

export async function getVisitorPlaylists(visitorId: string): Promise<Playlist[]> {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const db = getDb();
  const playlistsResult = await db.execute({
    sql: `
      SELECT
        playlists.id,
        playlists.name,
        playlists.share_token,
        visitors.display_name AS owner_name,
        playlists.created_at,
        playlists.updated_at,
        (
          SELECT pt.video_id
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_video_id,
        (
          SELECT pt.title
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_title,
        (
          SELECT pt.thumbnail
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_thumbnail,
        (
          SELECT pt.duration
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_duration,
        (
          SELECT pt.duration_seconds
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_duration_seconds,
        (
          SELECT pt.author
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_author,
        (
          SELECT pt.url
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_url,
        (
          SELECT pt.views
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_views,
        COUNT(playlist_tracks.id) AS track_count
      FROM playlists
      LEFT JOIN visitors ON visitors.id = playlists.visitor_id
      LEFT JOIN playlist_tracks ON playlist_tracks.playlist_id = playlists.id
      WHERE playlists.visitor_id = ?
      GROUP BY playlists.id
      ORDER BY playlists.updated_at DESC, playlists.created_at DESC
    `,
    args: [visitorId]
  });

  const playlists = playlistsResult.rows.map((row) =>
    mapPlaylistRow({
      id: rowString(row.id),
      name: rowString(row.name),
      share_token: rowString(row.share_token) || null,
      owner_name: rowString(row.owner_name) || null,
      created_at: rowNumber(row.created_at),
      updated_at: rowNumber(row.updated_at),
      track_count: rowNumber(row.track_count),
      cover_video_id: rowString(row.cover_video_id) || null,
      cover_title: rowString(row.cover_title) || null,
      cover_thumbnail: rowString(row.cover_thumbnail) || null,
      cover_duration: rowString(row.cover_duration) || null,
      cover_duration_seconds: rowNumber(row.cover_duration_seconds),
      cover_author: rowString(row.cover_author) || null,
      cover_url: rowString(row.cover_url) || null,
      cover_views: rowNumber(row.cover_views)
    })
  );

  return playlists;
}

export async function createPlaylist(visitorId: string, name: string) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const trimmedName = name.trim();

  if (!trimmedName) {
    throw new Error("Playlist name is required.");
  }

  const db = getDb();
  const timestamp = createTimestamp();
  const playlistId = crypto.randomUUID();

  await db.execute({
    sql: `
      INSERT INTO playlists (id, visitor_id, name, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?)
    `,
    args: [playlistId, visitorId, trimmedName.slice(0, 80), timestamp, timestamp]
  });

  return {
    id: playlistId,
    name: trimmedName.slice(0, 80),
    createdAt: new Date(timestamp).toISOString(),
    updatedAt: new Date(timestamp).toISOString(),
    shareToken: null,
    trackCount: 0,
    coverTrack: null,
    tracks: []
  } satisfies Playlist;
}

export async function addTrackToPlaylist(visitorId: string, playlistId: string, song: Song) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const db = getDb();
  const playlistResult = await db.execute({
    sql: `
      SELECT id
      FROM playlists
      WHERE id = ? AND visitor_id = ?
      LIMIT 1
    `,
    args: [playlistId, visitorId]
  });

  if (!playlistResult.rows.length) {
    throw new Error("Playlist not found.");
  }

  const timestamp = createTimestamp();

  await db.batch(
    [
      {
        sql: `
          INSERT INTO playlist_tracks (
            id,
            playlist_id,
            video_id,
            title,
            thumbnail,
            duration,
            duration_seconds,
            author,
            url,
            views,
            created_at
          )
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          ON CONFLICT(playlist_id, video_id) DO UPDATE SET
            title = excluded.title,
            thumbnail = excluded.thumbnail,
            duration = excluded.duration,
            duration_seconds = excluded.duration_seconds,
            author = excluded.author,
            url = excluded.url,
            views = excluded.views
        `,
        args: [
          crypto.randomUUID(),
          playlistId,
          song.videoId,
          song.title,
          song.thumbnail,
          song.duration,
          song.durationSeconds,
          song.author,
          song.url,
          song.views,
          timestamp
        ]
      },
      {
        sql: `
          UPDATE playlists
          SET updated_at = ?
          WHERE id = ?
        `,
        args: [timestamp, playlistId]
      }
    ],
    "write"
  );
}

export async function removeTrackFromPlaylist(visitorId: string, playlistId: string, videoId: string) {
  if (!videoId.trim()) {
    throw new Error("A valid video id is required.");
  }

  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const db = getDb();
  const playlistResult = await db.execute({
    sql: `
      SELECT id
      FROM playlists
      WHERE id = ? AND visitor_id = ?
      LIMIT 1
    `,
    args: [playlistId, visitorId]
  });

  if (!playlistResult.rows.length) {
    throw new Error("Playlist not found.");
  }

  const timestamp = createTimestamp();

  await db.batch(
    [
      {
        sql: `
          DELETE FROM playlist_tracks
          WHERE playlist_id = ? AND video_id = ?
        `,
        args: [playlistId, videoId]
      },
      {
        sql: `
          UPDATE playlists
          SET updated_at = ?
          WHERE id = ?
        `,
        args: [timestamp, playlistId]
      }
    ],
    "write"
  );
}

export async function getVisitorPlaylistById(visitorId: string, playlistId: string): Promise<Playlist | null> {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const db = getDb();
  const playlistResult = await db.execute({
    sql: `
      SELECT
        playlists.id,
        playlists.name,
        playlists.share_token,
        visitors.display_name AS owner_name,
        playlists.created_at,
        playlists.updated_at,
        COUNT(playlist_tracks.id) AS track_count
      FROM playlists
      LEFT JOIN visitors ON visitors.id = playlists.visitor_id
      LEFT JOIN playlist_tracks ON playlist_tracks.playlist_id = playlists.id
      WHERE playlists.visitor_id = ? AND playlists.id = ?
      GROUP BY playlists.id
      LIMIT 1
    `,
    args: [visitorId, playlistId]
  });

  const row = playlistResult.rows[0];

  if (!row) {
    return null;
  }

  const playlist = mapPlaylistRow({
    id: rowString(row.id),
    name: rowString(row.name),
    share_token: rowString(row.share_token) || null,
    owner_name: rowString(row.owner_name) || null,
    created_at: rowNumber(row.created_at),
    updated_at: rowNumber(row.updated_at),
    track_count: rowNumber(row.track_count)
  });

  const tracksByPlaylist = await loadPlaylistTracks([playlist.id]);

  return {
    ...playlist,
    coverTrack: tracksByPlaylist.get(playlist.id)?.[0] || playlist.coverTrack || null,
    tracks: tracksByPlaylist.get(playlist.id) || []
  };
}

export async function renamePlaylist(visitorId: string, playlistId: string, name: string) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const trimmedName = name.trim();

  if (!trimmedName) {
    throw new Error("Playlist name is required.");
  }

  const db = getDb();
  const timestamp = createTimestamp();
  const result = await db.execute({
    sql: `
      UPDATE playlists
      SET name = ?, updated_at = ?
      WHERE id = ? AND visitor_id = ?
    `,
    args: [trimmedName.slice(0, 80), timestamp, playlistId, visitorId]
  });

  if (rowNumber(result.rowsAffected) === 0) {
    throw new Error("Playlist not found.");
  }

  const playlist = await getVisitorPlaylistById(visitorId, playlistId);

  if (!playlist) {
    throw new Error("Playlist not found.");
  }

  return playlist;
}

export async function deletePlaylist(visitorId: string, playlistId: string) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const db = getDb();
  const playlistResult = await db.execute({
    sql: `
      SELECT id
      FROM playlists
      WHERE id = ? AND visitor_id = ?
      LIMIT 1
    `,
    args: [playlistId, visitorId]
  });

  if (!playlistResult.rows.length) {
    throw new Error("Playlist not found.");
  }

  await db.batch(
    [
      {
        sql: `
          DELETE FROM playlist_tracks
          WHERE playlist_id = ?
        `,
        args: [playlistId]
      },
      {
        sql: `
          DELETE FROM playlists
          WHERE id = ? AND visitor_id = ?
        `,
        args: [playlistId, visitorId]
      }
    ],
    "write"
  );
}

export async function getOrCreatePlaylistShareToken(visitorId: string, playlistId: string) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);
  return ensurePlaylistShareToken(visitorId, playlistId);
}

export async function revokePlaylistShare(visitorId: string, playlistId: string) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const db = getDb();
  const playlistResult = await db.execute({
    sql: `
      SELECT id
      FROM playlists
      WHERE id = ? AND visitor_id = ?
      LIMIT 1
    `,
    args: [playlistId, visitorId]
  });

  if (!playlistResult.rows.length) {
    throw new Error("Playlist not found.");
  }

  await db.batch(
    [
      {
        sql: `
          UPDATE playlists
          SET share_token = NULL
          WHERE id = ? AND visitor_id = ?
        `,
        args: [playlistId, visitorId]
      },
      {
        sql: `
          DELETE FROM shared_playlist_library
          WHERE playlist_id = ?
        `,
        args: [playlistId]
      },
      {
        sql: `
          DELETE FROM playlist_share_grants
          WHERE playlist_id = ?
        `,
        args: [playlistId]
      }
    ],
    "write"
  );
}

export async function getPlaylistShareAccess(visitorId: string, playlistId: string) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const playlist = await loadPlaylistByOwner(visitorId, playlistId);

  if (!playlist) {
    throw new Error("Playlist not found.");
  }

  const shareToken = await ensurePlaylistShareToken(visitorId, playlistId);
  const grants = await loadPlaylistShareGrants(playlistId);

  return {
    shareToken,
    grants
  };
}

export async function grantPlaylistAccessByUsername(visitorId: string, playlistId: string, username: string) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const playlist = await loadPlaylistByOwner(visitorId, playlistId);

  if (!playlist) {
    throw new Error("Playlist not found.");
  }

  const targetVisitor = await findVisitorByUsername(username);

  if (!targetVisitor) {
    throw new Error("That username was not found.");
  }

  if (targetVisitor.visitorId === visitorId) {
    throw new Error("You already own this playlist.");
  }

  const shareToken = await ensurePlaylistShareToken(visitorId, playlistId);
  const db = getDb();
  const timestamp = createTimestamp();

  await db.execute({
    sql: `
      INSERT INTO playlist_share_grants (playlist_id, grantee_username, granted_by_visitor_id, created_at)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(playlist_id, grantee_username) DO NOTHING
    `,
    args: [playlistId, targetVisitor.username, visitorId, timestamp]
  });

  const grants = await loadPlaylistShareGrants(playlistId);

  return {
    shareToken,
    grants,
    grantedUser: targetVisitor
  };
}

export async function revokePlaylistAccessForVisitor(visitorId: string, playlistId: string, granteeVisitorId: string) {
  return revokePlaylistAccessForUsername(visitorId, playlistId, granteeVisitorId);
}

export async function revokePlaylistAccessForUsername(visitorId: string, playlistId: string, username: string) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const playlist = await loadPlaylistByOwner(visitorId, playlistId);

  if (!playlist) {
    throw new Error("Playlist not found.");
  }

  const db = getDb();

  await db.batch(
    [
      {
        sql: `
          DELETE FROM playlist_share_grants
          WHERE playlist_id = ? AND grantee_username = ?
        `,
        args: [playlistId, username.trim().toLowerCase()]
      },
      {
        sql: `
          DELETE FROM shared_playlist_library
          WHERE playlist_id = ?
            AND subscriber_visitor_id IN (
              SELECT id
              FROM visitors
              WHERE username = ?
            )
        `,
        args: [playlistId, username.trim().toLowerCase()]
      }
    ],
    "write"
  );

  return loadPlaylistShareGrants(playlistId);
}

export async function saveSharedPlaylistForVisitor(visitorId: string, shareToken: string) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const trimmedShareToken = shareToken.trim();

  if (!trimmedShareToken) {
    throw new Error("A valid share token is required.");
  }

  const playlistRow = await loadPlaylistByShareToken(trimmedShareToken);

  if (!playlistRow) {
    throw new Error("Shared playlist not found.");
  }

  const playlistId = rowString(playlistRow.id);
  const ownerVisitorId = rowString(playlistRow.visitor_id);

  if (ownerVisitorId === visitorId) {
    return;
  }

  if (!(await hasPlaylistGrant(visitorId, playlistId))) {
    throw new Error("You do not have access to this playlist.");
  }

  const db = getDb();

  await db.execute({
    sql: `
      INSERT INTO shared_playlist_library (playlist_id, subscriber_visitor_id, created_at)
      VALUES (?, ?, ?)
      ON CONFLICT(playlist_id, subscriber_visitor_id) DO NOTHING
    `,
    args: [playlistId, visitorId, createTimestamp()]
  });
}

export async function removeSharedPlaylistForVisitor(visitorId: string, shareToken: string) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const trimmedShareToken = shareToken.trim();

  if (!trimmedShareToken) {
    return;
  }

  const db = getDb();
  await db.execute({
    sql: `
      DELETE FROM shared_playlist_library
      WHERE subscriber_visitor_id = ?
        AND playlist_id IN (
          SELECT id
          FROM playlists
          WHERE share_token = ?
        )
    `,
    args: [visitorId, trimmedShareToken]
  });
}

export async function isSharedPlaylistSaved(visitorId: string, shareToken: string) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const trimmedShareToken = shareToken.trim();

  if (!trimmedShareToken) {
    return false;
  }

  const playlistRow = await loadPlaylistByShareToken(trimmedShareToken);

  if (!playlistRow) {
    return false;
  }

  const playlistId = rowString(playlistRow.id);
  const ownerVisitorId = rowString(playlistRow.visitor_id);

  if (ownerVisitorId !== visitorId && !(await hasPlaylistGrant(visitorId, playlistId))) {
    return false;
  }

  const db = getDb();
  const result = await db.execute({
    sql: `
      SELECT 1
      FROM shared_playlist_library
      WHERE subscriber_visitor_id = ? AND playlist_id = ?
      LIMIT 1
    `,
    args: [visitorId, playlistId]
  });

  return Boolean(result.rows[0]);
}

export async function getVisitorSharedPlaylists(visitorId: string): Promise<Playlist[]> {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const username = await getVisitorUsername(visitorId);

  if (!username) {
    return [];
  }

  const db = getDb();
  const playlistsResult = await db.execute({
    sql: `
      SELECT
        playlists.id,
        playlists.name,
        playlists.share_token,
        visitors.display_name AS owner_name,
        playlists.created_at,
        playlists.updated_at,
        MAX(playlist_share_grants.created_at) AS saved_at,
        (
          SELECT pt.video_id
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_video_id,
        (
          SELECT pt.title
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_title,
        (
          SELECT pt.thumbnail
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_thumbnail,
        (
          SELECT pt.duration
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_duration,
        (
          SELECT pt.duration_seconds
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_duration_seconds,
        (
          SELECT pt.author
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_author,
        (
          SELECT pt.url
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_url,
        (
          SELECT pt.views
          FROM playlist_tracks AS pt
          WHERE pt.playlist_id = playlists.id
          ORDER BY pt.created_at DESC
          LIMIT 1
        ) AS cover_views,
        COUNT(playlist_tracks.id) AS track_count
      FROM playlist_share_grants
      JOIN playlists ON playlists.id = playlist_share_grants.playlist_id
      LEFT JOIN visitors ON visitors.id = playlists.visitor_id
      LEFT JOIN playlist_tracks ON playlist_tracks.playlist_id = playlists.id
      WHERE playlist_share_grants.grantee_username = ?
        AND playlists.share_token IS NOT NULL
      GROUP BY playlists.id
      ORDER BY saved_at DESC, playlists.updated_at DESC
    `,
    args: [username]
  });

  const playlists = playlistsResult.rows.map((row) => ({
    ...mapPlaylistRow({
      id: rowString(row.id),
      name: rowString(row.name),
      share_token: rowString(row.share_token) || null,
      owner_name: rowString(row.owner_name) || null,
      created_at: rowNumber(row.created_at),
      updated_at: rowNumber(row.updated_at),
      track_count: rowNumber(row.track_count),
      cover_video_id: rowString(row.cover_video_id) || null,
      cover_title: rowString(row.cover_title) || null,
      cover_thumbnail: rowString(row.cover_thumbnail) || null,
      cover_duration: rowString(row.cover_duration) || null,
      cover_duration_seconds: rowNumber(row.cover_duration_seconds),
      cover_author: rowString(row.cover_author) || null,
      cover_url: rowString(row.cover_url) || null,
      cover_views: rowNumber(row.cover_views)
    }),
    isShared: true
  }));

  return playlists;
}

export async function getSharedPlaylist(shareToken: string): Promise<Playlist | null> {
  assertDatabaseConfigured();
  await ensureDbSchema();

  const trimmedShareToken = shareToken.trim();

  if (!trimmedShareToken) {
    return null;
  }

  const db = getDb();
  const playlistResult = await db.execute({
    sql: `
      SELECT
        playlists.id,
        playlists.name,
        playlists.share_token,
        visitors.display_name AS owner_name,
        playlists.created_at,
        playlists.updated_at,
        COUNT(playlist_tracks.id) AS track_count
      FROM playlists
      LEFT JOIN visitors ON visitors.id = playlists.visitor_id
      LEFT JOIN playlist_tracks ON playlist_tracks.playlist_id = playlists.id
      WHERE playlists.share_token = ?
      GROUP BY playlists.id
      LIMIT 1
    `,
    args: [trimmedShareToken]
  });

  const row = playlistResult.rows[0];

  if (!row) {
    return null;
  }

  const playlist = mapPlaylistRow({
    id: rowString(row.id),
    name: rowString(row.name),
    share_token: rowString(row.share_token) || null,
    owner_name: rowString(row.owner_name) || null,
    created_at: rowNumber(row.created_at),
    updated_at: rowNumber(row.updated_at),
    track_count: rowNumber(row.track_count)
  });

  const tracksByPlaylist = await loadPlaylistTracks([playlist.id]);

  return {
    ...playlist,
    coverTrack: tracksByPlaylist.get(playlist.id)?.[0] || playlist.coverTrack || null,
    tracks: tracksByPlaylist.get(playlist.id) || []
  };
}

export async function getSharedPlaylistForVisitor(visitorId: string, shareToken: string): Promise<Playlist | null> {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const playlistRow = await loadPlaylistByShareToken(shareToken.trim());

  if (!playlistRow) {
    return null;
  }

  const playlistId = rowString(playlistRow.id);
  const ownerVisitorId = rowString(playlistRow.visitor_id);

  if (ownerVisitorId !== visitorId && !(await hasPlaylistGrant(visitorId, playlistId))) {
    return null;
  }

  return getSharedPlaylist(shareToken);
}

export async function unlockSharedPlaylistForVisitor(visitorId: string, shareCode: string) {
  assertDatabaseConfigured();
  await ensureDbSchema();
  await ensureVisitor(visitorId);

  const trimmedCode = shareCode.trim();

  if (!trimmedCode) {
    throw new Error("Enter a valid code.");
  }

  const playlistRow = await loadPlaylistByShareToken(trimmedCode);

  if (!playlistRow) {
    throw new Error("That share code was not found.");
  }

  const playlistId = rowString(playlistRow.id);
  const ownerVisitorId = rowString(playlistRow.visitor_id);

  if (ownerVisitorId !== visitorId && !(await hasPlaylistGrant(visitorId, playlistId))) {
    throw new Error("This playlist has not been shared with your username.");
  }

  await saveSharedPlaylistForVisitor(visitorId, trimmedCode);
  const playlist = await getSharedPlaylist(trimmedCode);

  if (!playlist) {
    throw new Error("Shared playlist not found.");
  }

  return {
    shareToken: trimmedCode,
    playlist
  };
}
