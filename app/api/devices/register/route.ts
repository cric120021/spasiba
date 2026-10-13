import { NextRequest, NextResponse } from "next/server";
import { ensureDbSchema, getDb } from "@/lib/db";

export async function POST(req: NextRequest) {
  try {
    await ensureDbSchema();
    const body = await req.json();
    const { userId, deviceId, fcmToken, appVersion, mobileNumber } = body;

    if (!deviceId) {
      return NextResponse.json({ error: "deviceId is required" }, { status: 400 });
    }

    const cleanDeviceId = String(deviceId).trim().toUpperCase();
    const cleanUserId = userId ? String(userId).trim() : null;
    const cleanToken = fcmToken ? String(fcmToken).trim() : null;
    const cleanVersion = appVersion ? String(appVersion).trim() : "2.0.0-NATIVE";
    const cleanMobile = mobileNumber ? String(mobileNumber).trim() : null;
    const now = Date.now();

    const db = getDb();

    // 1. Check if device is already blocked in DB
    const existingDevRes = await db.execute({
      sql: `SELECT is_blocked, eunoia_disabled, block_reason, mobile_number FROM devices WHERE device_id = ? LIMIT 1`,
      args: [cleanDeviceId]
    });
    const existingDev = existingDevRes.rows[0];

    let isBlocked = Boolean(existingDev?.is_blocked);
    let blockReason = existingDev?.block_reason ? String(existingDev.block_reason) : "Account or device restricted by administrator.";
    let eunoiaDisabled = Boolean(existingDev?.eunoia_disabled);
    const activeMobile = cleanMobile || (existingDev?.mobile_number ? String(existingDev.mobile_number) : `SIM-DEV-${cleanDeviceId}`);

    // 2. Check if Mobile Number is blocked in blocked_numbers table or associated with another blocked device
    if (activeMobile) {
      const blockedNumRes = await db.execute({
        sql: `SELECT reason FROM blocked_numbers WHERE mobile_number = ? LIMIT 1`,
        args: [activeMobile]
      });
      if (blockedNumRes.rows.length > 0) {
        isBlocked = true;
        blockReason = blockedNumRes.rows[0]?.reason ? String(blockedNumRes.rows[0].reason) : "Mobile number flagged for policy violation.";
      }

      const blockedDevRes = await db.execute({
        sql: `SELECT block_reason FROM devices WHERE mobile_number = ? AND is_blocked = 1 LIMIT 1`,
        args: [activeMobile]
      });
      if (blockedDevRes.rows.length > 0) {
        isBlocked = true;
        blockReason = blockedDevRes.rows[0]?.block_reason ? String(blockedDevRes.rows[0].block_reason) : "Mobile number associated with a blocked device.";
      }
    }

    // 3. Insert or Update Device record
    await db.execute({
      sql: `
        INSERT INTO devices (device_id, user_id, fcm_token, app_version, mobile_number, is_blocked, eunoia_disabled, block_reason, last_seen)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(device_id) DO UPDATE SET
          user_id = excluded.user_id,
          fcm_token = COALESCE(excluded.fcm_token, devices.fcm_token),
          app_version = excluded.app_version,
          mobile_number = excluded.mobile_number,
          is_blocked = excluded.is_blocked,
          block_reason = excluded.block_reason,
          last_seen = excluded.last_seen
      `,
      args: [
        cleanDeviceId,
        cleanUserId,
        cleanToken,
        cleanVersion,
        activeMobile,
        isBlocked ? 1 : 0,
        eunoiaDisabled ? 1 : 0,
        blockReason,
        now
      ]
    });

    return NextResponse.json({
      success: true,
      deviceId: cleanDeviceId,
      mobileNumber: activeMobile,
      isBlocked: isBlocked,
      blockReason: blockReason,
      eunoiaDisabled: eunoiaDisabled,
      registeredAt: now
    });
  } catch (error: any) {
    console.error("Device registration error:", error);
    return NextResponse.json({ error: error?.message || "Internal server error" }, { status: 500 });
  }
}
