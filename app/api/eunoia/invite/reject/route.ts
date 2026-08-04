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

    const sessResult = await db.execute({
      sql: `SELECT * FROM eunoia_sessions WHERE session_id = ? LIMIT 1`,
      args: [sessionId]
    });

    if (sessResult.rows.length === 0) {
      return NextResponse.json({ error: "Session not found" }, { status: 404 });
    }

    const row = sessResult.rows[0];
    const inviterDeviceId = String(row.inviter_device_id);

    await db.execute({
      sql: `UPDATE eunoia_sessions SET status = 'REJECTED', updated_at = ? WHERE session_id = ?`,
      args: [now, sessionId]
    });

    const rejectSignal = {
      type: "REJECT",
      sessionId,
      rejectedByDeviceId: cleanDeviceId
    };

    await db.execute({
      sql: `INSERT INTO eunoia_signals (id, target_id, from_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?)`,
      args: [crypto.randomUUID(), inviterDeviceId, cleanDeviceId, JSON.stringify(rejectSignal), now]
    });

    return NextResponse.json({
      success: true,
      sessionId,
      status: "REJECTED"
    });
  } catch (error: any) {
    console.error("Eunoia reject error:", error);
    return NextResponse.json({ error: error?.message || "Internal server error" }, { status: 500 });
  }
}
