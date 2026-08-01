import { NextRequest, NextResponse } from "next/server";
import { ensureDbSchema, getDb } from "@/lib/db";
import { v4 as uuidv4 } from "uuid";

export async function POST(req: NextRequest) {
  try {
    await ensureDbSchema();
    const body = await req.json();
    const { targetId, fromId, payload } = body;

    if (!targetId || !fromId || !payload) {
      return NextResponse.json({ error: "Missing required fields" }, { status: 400 });
    }

    const db = getDb();
    const id = uuidv4();
    const now = Date.now();
    const payloadJson = typeof payload === "string" ? payload : JSON.stringify(payload);

    await db.execute({
      sql: `INSERT INTO eunoia_signals (id, target_id, from_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?)`,
      args: [id, targetId.toUpperCase(), fromId.toUpperCase(), payloadJson, now]
    });

    return NextResponse.json({ success: true });
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
      return NextResponse.json({ signals: [] });
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

    return NextResponse.json({ signals });
  } catch (error: any) {
    console.error("Eunoia GET Signals Error:", error);
    return NextResponse.json({ signals: [] });
  }
}
