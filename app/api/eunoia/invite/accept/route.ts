import { NextRequest, NextResponse } from "next/server";
import { ensureDbSchema, getDb } from "@/lib/db";

export async function POST(req: NextRequest) {
  try {
    await ensureDbSchema();
    const body = await req.json();
    const { sessionId, deviceId } = body;

    if (!sessionId || !deviceId) {
      return NextResponse.json({ error: "sessionId and deviceId are required" }, { status: 400 });
    }

    const cleanDeviceId = String(deviceId).trim().toUpperCase();
    const db = getDb();
    const now = Date.now();

    // Fetch session
    const sessResult = await db.execute({
      sql: `SELECT * FROM eunoia_sessions WHERE session_id = ? LIMIT 1`,
      args: [sessionId]
    });

    if (sessResult.rows.length === 0) {
      return NextResponse.json({ error: "Session not found" }, { status: 404 });
    }

    const row = sessResult.rows[0];

    // Mark ACCEPTED
    await db.execute({
      sql: `UPDATE eunoia_sessions SET status = 'ACCEPTED', updated_at = ? WHERE session_id = ?`,
      args: [now, sessionId]
    });

    const currentSong = row.current_song_json ? JSON.parse(String(row.current_song_json)) : null;
    const queue = row.queue_json ? JSON.parse(String(row.queue_json)) : [];
    const inviterDeviceId = String(row.inviter_device_id);
    const partnerDeviceId = cleanDeviceId === inviterDeviceId ? String(row.invitee_device_id) : inviterDeviceId;

    // Send accept signal to inviter
    const acceptSignal = {
      type: "ACCEPT",
      sessionId,
      acceptedByDeviceId: cleanDeviceId,
      inviterDeviceId
    };

    await db.execute({
      sql: `INSERT INTO eunoia_signals (id, target_id, from_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?)`,
      args: [crypto.randomUUID(), inviterDeviceId, cleanDeviceId, JSON.stringify(acceptSignal), now]
    });

    const socketToken = `ST-${crypto.randomUUID().slice(0, 12)}`;

    return NextResponse.json({
      success: true,
      sessionId,
      status: "ACCEPTED",
      partnerDeviceId,
      socketToken,
      sessionSnapshot: {
        sessionId,
        inviterDeviceId,
        inviteeDeviceId: String(row.invitee_device_id),
        currentSong,
        queue,
        positionMs: Number(row.position_ms || 0),
        isPlaying: Boolean(row.is_playing)
      }
    });
  } catch (error: any) {
    console.error("Eunoia accept error:", error);
    return NextResponse.json({ error: error?.message || "Internal server error" }, { status: 500 });
  }
}
