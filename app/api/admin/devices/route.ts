import { NextRequest, NextResponse } from "next/server";
import { ensureDbSchema, getDb } from "@/lib/db";

export async function GET(req: NextRequest) {
  try {
    await ensureDbSchema();
    const db = getDb();

    const searchParams = req.nextUrl.searchParams;
    const targetDeviceId = searchParams.get("deviceId");

    // Single Device Deep Inspector Mode
    if (targetDeviceId) {
      const cleanId = String(targetDeviceId).trim().toUpperCase();
      const devRes = await db.execute({
        sql: `
          SELECT 
            d.device_id, 
            d.user_id, 
            d.fcm_token,
            d.app_version, 
            d.mobile_number, 
            d.is_blocked, 
            d.eunoia_disabled, 
            d.last_seen,
            v.display_name,
            v.username,
            v.profile_image_data_url
          FROM devices d
          LEFT JOIN visitors v ON v.id = d.user_id OR v.id = d.device_id
          WHERE d.device_id = ? OR d.user_id = ?
          LIMIT 1
        `,
        args: [cleanId, cleanId]
      });

      if (devRes.rows.length === 0) {
        return NextResponse.json({ error: "Device not found" }, { status: 404 });
      }

      const row = devRes.rows[0];
      const userId = row.user_id ? String(row.user_id) : String(row.device_id);

      // Fetch stats
      const likedRes = await db.execute({
        sql: `SELECT COUNT(*) as cnt FROM liked_tracks WHERE visitor_id = ?`,
        args: [userId]
      });

      const playlistsRes = await db.execute({
        sql: `SELECT COUNT(*) as cnt FROM playlists WHERE visitor_id = ?`,
        args: [userId]
      });

      const recentEngagementsRes = await db.execute({
        sql: `SELECT video_id, title, author, action, created_at FROM engagement_events WHERE visitor_id = ? ORDER BY created_at DESC LIMIT 10`,
        args: [userId]
      });

      return NextResponse.json({
        success: true,
        device: {
          deviceId: String(row.device_id || ""),
          userId: row.user_id ? String(row.user_id) : null,
          fcmToken: row.fcm_token ? String(row.fcm_token) : null,
          displayName: row.display_name ? String(row.display_name) : null,
          username: row.username ? String(row.username) : null,
          profileImage: row.profile_image_data_url ? String(row.profile_image_data_url) : null,
          appVersion: String(row.app_version || "2.0.0"),
          mobileNumber: row.mobile_number ? String(row.mobile_number) : null,
          isBlocked: Boolean(row.is_blocked),
          eunoiaDisabled: Boolean(row.eunoia_disabled),
          lastSeen: Number(row.last_seen || 0),
          stats: {
            likedCount: Number(likedRes.rows[0]?.cnt || 0),
            playlistCount: Number(playlistsRes.rows[0]?.cnt || 0),
            recentActivity: recentEngagementsRes.rows.map(r => ({
              videoId: String(r.video_id || ""),
              title: String(r.title || ""),
              author: String(r.author || ""),
              action: String(r.action || ""),
              timestamp: Number(r.created_at || 0)
            }))
          }
        }
      });
    }
    
    // Summary Device List Mode
    const devicesRes = await db.execute({
      sql: `
        SELECT 
          d.device_id, 
          d.user_id, 
          d.app_version, 
          d.mobile_number, 
          d.is_blocked, 
          d.eunoia_disabled, 
          d.last_seen,
          v.display_name,
          v.username,
          v.profile_image_data_url
        FROM devices d
        LEFT JOIN visitors v ON v.id = d.user_id OR v.id = d.device_id
        ORDER BY d.last_seen DESC
        LIMIT 200
      `
    });

    const blockedNumbersRes = await db.execute({
      sql: `SELECT mobile_number, created_at FROM blocked_numbers ORDER BY created_at DESC LIMIT 200`
    });

    return NextResponse.json({
      success: true,
      devices: devicesRes.rows.map(row => ({
        deviceId: String(row.device_id || ""),
        userId: row.user_id ? String(row.user_id) : null,
        displayName: row.display_name ? String(row.display_name) : null,
        username: row.username ? String(row.username) : null,
        profileImage: row.profile_image_data_url ? String(row.profile_image_data_url) : null,
        appVersion: String(row.app_version || "2.0.0"),
        mobileNumber: row.mobile_number ? String(row.mobile_number) : null,
        isBlocked: Boolean(row.is_blocked),
        eunoiaDisabled: Boolean(row.eunoia_disabled),
        lastSeen: Number(row.last_seen || 0)
      })),
      blockedNumbers: blockedNumbersRes.rows.map(row => ({
        mobileNumber: String(row.mobile_number || ""),
        createdAt: Number(row.created_at || 0)
      }))
    });
  } catch (error: any) {
    console.error("Admin devices fetch error:", error);
    return NextResponse.json({ error: error?.message || "Internal server error" }, { status: 500 });
  }
}

export async function POST(req: NextRequest) {
  try {
    await ensureDbSchema();
    const body = await req.json();
    const { action, deviceId, mobileNumber, eunoiaDisabled } = body;
    const db = getDb();
    const now = Date.now();

    const cleanDeviceId = deviceId ? String(deviceId).trim().toUpperCase() : null;
    const cleanMobile = mobileNumber ? String(mobileNumber).trim() : null;

    if (action === "block") {
      if (!cleanDeviceId) return NextResponse.json({ error: "deviceId required" }, { status: 400 });
      
      // Block Device
      await db.execute({
        sql: `UPDATE devices SET is_blocked = 1 WHERE device_id = ?`,
        args: [cleanDeviceId]
      });

      // Cross-block associated mobile number if available
      const devRes = await db.execute({
        sql: `SELECT mobile_number FROM devices WHERE device_id = ? LIMIT 1`,
        args: [cleanDeviceId]
      });
      const associatedMobile = cleanMobile || (devRes.rows[0]?.mobile_number ? String(devRes.rows[0].mobile_number) : null);
      
      if (associatedMobile) {
        await db.execute({
          sql: `
            INSERT INTO blocked_numbers (mobile_number, created_at)
            VALUES (?, ?)
            ON CONFLICT(mobile_number) DO UPDATE SET created_at = excluded.created_at
          `,
          args: [associatedMobile, now]
        });

        // Block all other devices sharing this mobile number
        await db.execute({
          sql: `UPDATE devices SET is_blocked = 1 WHERE mobile_number = ?`,
          args: [associatedMobile]
        });
      }

      return NextResponse.json({ success: true, message: "Device & Mobile blocked" });
    }

    if (action === "unblock") {
      if (!cleanDeviceId) return NextResponse.json({ error: "deviceId required" }, { status: 400 });

      await db.execute({
        sql: `UPDATE devices SET is_blocked = 0 WHERE device_id = ?`,
        args: [cleanDeviceId]
      });

      if (cleanMobile) {
        await db.execute({
          sql: `DELETE FROM blocked_numbers WHERE mobile_number = ?`,
          args: [cleanMobile]
        });
        await db.execute({
          sql: `UPDATE devices SET is_blocked = 0 WHERE mobile_number = ?`,
          args: [cleanMobile]
        });
      }

      return NextResponse.json({ success: true, message: "Device unblocked" });
    }

    if (action === "block_mobile") {
      if (!cleanMobile) return NextResponse.json({ error: "mobileNumber required" }, { status: 400 });

      await db.execute({
        sql: `
          INSERT INTO blocked_numbers (mobile_number, created_at)
          VALUES (?, ?)
          ON CONFLICT(mobile_number) DO UPDATE SET created_at = excluded.created_at
        `,
        args: [cleanMobile, now]
      });

      await db.execute({
        sql: `UPDATE devices SET is_blocked = 1 WHERE mobile_number = ?`,
        args: [cleanMobile]
      });

      return NextResponse.json({ success: true, message: `Mobile ${cleanMobile} blocked` });
    }

    if (action === "toggle_eunoia") {
      if (!cleanDeviceId) return NextResponse.json({ error: "deviceId required" }, { status: 400 });

      const newDisabledState = Boolean(eunoiaDisabled);
      await db.execute({
        sql: `UPDATE devices SET eunoia_disabled = ? WHERE device_id = ?`,
        args: [newDisabledState ? 1 : 0, cleanDeviceId]
      });

      return NextResponse.json({ success: true, message: `Eunoia access updated` });
    }

    if (action === "set_mobile") {
      if (!cleanDeviceId || !cleanMobile) return NextResponse.json({ error: "deviceId and mobileNumber required" }, { status: 400 });

      await db.execute({
        sql: `UPDATE devices SET mobile_number = ? WHERE device_id = ?`,
        args: [cleanMobile, cleanDeviceId]
      });

      // Check if mobile number is already in blocked list
      const isBlockedRes = await db.execute({
        sql: `SELECT mobile_number FROM blocked_numbers WHERE mobile_number = ? LIMIT 1`,
        args: [cleanMobile]
      });

      if (isBlockedRes.rows.length > 0) {
        await db.execute({
          sql: `UPDATE devices SET is_blocked = 1 WHERE device_id = ?`,
          args: [cleanDeviceId]
        });
      }

      return NextResponse.json({ success: true, message: "Mobile number updated" });
    }

    if (action === "clear_all") {
      await db.batch([
        "DELETE FROM devices",
        "DELETE FROM visitors",
        "DELETE FROM listener_profiles",
        "DELETE FROM liked_tracks",
        "DELETE FROM playlists",
        "DELETE FROM playlist_tracks",
        "DELETE FROM engagement_events",
        "DELETE FROM eunoia_sessions",
        "DELETE FROM eunoia_signals",
        "DELETE FROM blocked_numbers",
        "DELETE FROM shared_playlist_library",
        "DELETE FROM playlist_share_grants"
      ], "write");

      return NextResponse.json({ success: true, message: "All user records and device database cleared successfully." });
    }

    return NextResponse.json({ error: "Invalid action" }, { status: 400 });
  } catch (error: any) {
    console.error("Admin POST action error:", error);
    return NextResponse.json({ error: error?.message || "Internal server error" }, { status: 500 });
  }
}
