import { NextRequest, NextResponse } from "next/server";
import { ensureDbSchema, getDb } from "@/lib/db";

export async function POST(req: NextRequest) {
  try {
    await ensureDbSchema();
    const body = await req.json();
    let { targetId, fromId, sessionId, payload } = body;

    if (!fromId || !payload) {
      return NextResponse.json({ error: "Missing required fields: fromId and payload are required" }, { status: 400 });
    }

    const db = getDb();
    const cleanFrom = String(fromId).trim().toUpperCase();

    // If targetId is not explicitly provided, find the partner deviceId from eunoia_sessions
    if (!targetId && sessionId) {
      const sessRes = await db.execute({
        sql: `SELECT inviter_device_id, invitee_device_id FROM eunoia_sessions WHERE session_id = ? LIMIT 1`,
        args: [String(sessionId).trim()]
      });

      if (sessRes.rows.length > 0) {
        const inviter = String(sessRes.rows[0].inviter_device_id).toUpperCase();
        const invitee = String(sessRes.rows[0].invitee_device_id).toUpperCase();
        targetId = cleanFrom === inviter ? invitee : inviter;
      }
    }

    if (!targetId) {
      return NextResponse.json({ error: "Could not determine targetId for signal" }, { status: 400 });
    }

    const id = crypto.randomUUID();
    const now = Date.now();
    const payloadJson = typeof payload === "string" ? payload : JSON.stringify(payload);

    await db.execute({
      sql: `INSERT INTO eunoia_signals (id, target_id, from_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?)`,
      args: [id, String(targetId).toUpperCase(), cleanFrom, payloadJson, now]
    });

    // If signal is FRIEND_REMOVE or FRIEND_CHAT_MESSAGE, send silent FCM push to target device if registered
    const parsedPayload = typeof payload === "object" ? payload : JSON.parse(payloadJson);
    if (parsedPayload?.type === "FRIEND_REMOVE") {
      try {
        const deviceRes = await db.execute({
          sql: `SELECT fcm_token FROM user_devices WHERE device_id = ? AND fcm_token IS NOT NULL AND fcm_token != '' LIMIT 1`,
          args: [String(targetId).toUpperCase()]
        });
        const fcmToken = deviceRes.rows[0]?.fcm_token ? String(deviceRes.rows[0].fcm_token) : null;
        if (fcmToken) {
          const { sendFcmHttpV1Notification } = await import("@/lib/fcm");
          await sendFcmHttpV1Notification(fcmToken, {
            type: "FRIEND_REMOVE",
            senderId: cleanFrom
          });
        }
      } catch (fcmErr) {
        console.warn("Silent FCM FRIEND_REMOVE send error:", fcmErr);
      }
    }

    return NextResponse.json({ success: true, serverTime: Date.now() });
  } catch (error: any) {
    console.error("Eunoia POST Signal Error:", error);
    return NextResponse.json({ error: error?.message || "Internal server error" }, { status: 500 });
  }
}

export async function GET(req: NextRequest) {
  try {
    await ensureDbSchema();
    const { searchParams } = new URL(req.url);
    const deviceId = searchParams.get("deviceId")?.trim().toUpperCase();

    if (!deviceId) {
      return NextResponse.json({ signals: [], serverTime: Date.now() });
    }

    const db = getDb();
    const result = await db.execute({
      sql: `SELECT id, target_id, from_id, payload_json, created_at FROM eunoia_signals WHERE target_id = ? ORDER BY created_at ASC LIMIT 10`,
      args: [deviceId]
    });

    const signals = result.rows.map((row: any) => ({
      id: row.id,
      targetId: row.target_id,
      fromId: row.from_id,
      payload: JSON.parse(row.payload_json),
      createdAt: row.created_at
    }));

    if (signals.length > 0) {
      const ids = signals.map((s) => s.id);
      const placeholders = ids.map(() => "?").join(",");
      await db.execute({
        sql: `DELETE FROM eunoia_signals WHERE id IN (${placeholders})`,
        args: ids
      });
    }

    return NextResponse.json({ signals, serverTime: Date.now() });
  } catch (error: any) {
    console.error("Eunoia GET Signals Error:", error);
    return NextResponse.json({ signals: [] });
  }
}
