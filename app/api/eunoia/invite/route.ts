import { NextRequest, NextResponse } from "next/server";
import { ensureDbSchema, getDb } from "@/lib/db";
import fs from "fs";

async function getAccessTokenFromServiceAccount(serviceAccountJson: string): Promise<{ accessToken: string; projectId: string } | null> {
  try {
    const sa = JSON.parse(serviceAccountJson);
    const clientEmail = sa.client_email;
    const privateKey = sa.private_key;
    const projectId = sa.project_id;

    if (!clientEmail || !privateKey || !projectId) return null;

    const header = { alg: "RS256", typ: "JWT" };
    const iat = Math.floor(Date.now() / 1000);
    const exp = iat + 3600;

    const claimSet = {
      iss: clientEmail,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      exp,
      iat
    };

    const b64Header = Buffer.from(JSON.stringify(header)).toString("base64url");
    const b64ClaimSet = Buffer.from(JSON.stringify(claimSet)).toString("base64url");
    const signatureInput = `${b64Header}.${b64ClaimSet}`;

    const crypto = await import("crypto");
    const signer = crypto.createSign("RSA-SHA256");
    signer.update(signatureInput);
    const signature = signer.sign(privateKey, "base64url");

    const jwt = `${signatureInput}.${signature}`;

    const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion: jwt
      })
    });

    const tokenData = await tokenResponse.json();
    if (tokenData?.access_token) {
      return { accessToken: tokenData.access_token, projectId };
    }
    return null;
  } catch (err) {
    console.error("Failed to generate OAuth token from Service Account:", err);
    return null;
  }
}

async function sendFcmHttpV1Notification(fcmToken: string, payloadData: Record<string, string>) {
  let saJsonStr = process.env.FIREBASE_SERVICE_ACCOUNT_JSON || process.env.FIREBASE_SERVICE_ACCOUNT_KEY;

  if (!saJsonStr && process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    try {
      if (fs.existsSync(process.env.GOOGLE_APPLICATION_CREDENTIALS)) {
        saJsonStr = fs.readFileSync(process.env.GOOGLE_APPLICATION_CREDENTIALS, "utf-8");
      }
    } catch (e) {
      console.warn("Could not read GOOGLE_APPLICATION_CREDENTIALS file:", e);
    }
  }

  if (!saJsonStr) {
    console.log("No FIREBASE_SERVICE_ACCOUNT_JSON or GOOGLE_APPLICATION_CREDENTIALS configured. Invites fall back to eunoia_signals DB polling.");
    return false;
  }

  const oauthRes = await getAccessTokenFromServiceAccount(saJsonStr);
  if (!oauthRes) {
    console.warn("FCM Service Account credentials invalid or token generation failed.");
    return false;
  }

  const { accessToken, projectId } = oauthRes;

  try {
    const fcmV1Url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
    const response = await fetch(fcmV1Url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${accessToken}`
      },
      body: JSON.stringify({
        message: {
          token: fcmToken,
          data: payloadData,
          android: {
            priority: "high"
          }
        }
      })
    });

    const resJson = await response.json();
    console.log("FCM HTTP v1 Push Result:", resJson);
    return Boolean(resJson?.name);
  } catch (err) {
    console.error("FCM HTTP v1 Send Error:", err);
    return false;
  }
}

export async function POST(req: NextRequest) {
  try {
    await ensureDbSchema();
    const body = await req.json();
    const { inviterDeviceId, inviteeDeviceId, inviterName, currentSong, queue, positionMs, isPlaying } = body;

    if (!inviterDeviceId || !inviteeDeviceId) {
      return NextResponse.json({ error: "inviterDeviceId and inviteeDeviceId are required" }, { status: 400 });
    }

    const cleanInviter = String(inviterDeviceId).trim().toUpperCase();
    const cleanInvitee = String(inviteeDeviceId).trim().toUpperCase();
    const sessionId = `EUNOIA-${crypto.randomUUID().slice(0, 8).toUpperCase()}`;
    const now = Date.now();
    const expiresAt = now + 5 * 60 * 1000; // 5 minutes expiration

    const songJson = currentSong ? JSON.stringify(currentSong) : null;
    const queueJson = queue ? JSON.stringify(queue) : null;
    const pos = typeof positionMs === "number" ? positionMs : 0;
    const playingState = isPlaying ? 1 : 0;

    const db = getDb();

    // 1. Create Eunoia session record with full snapshot state
    await db.execute({
      sql: `
        INSERT INTO eunoia_sessions (session_id, inviter_device_id, invitee_device_id, status, current_song_json, queue_json, position_ms, is_playing, created_at, updated_at, expires_at)
        VALUES (?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?)
      `,
      args: [sessionId, cleanInviter, cleanInvitee, songJson, queueJson, pos, playingState, now, now, expiresAt]
    });

    // 2. Fetch invitee's FCM token from devices table
    const deviceRes = await db.execute({
      sql: `SELECT fcm_token FROM devices WHERE device_id = ? LIMIT 1`,
      args: [cleanInvitee]
    });

    const fcmToken = deviceRes.rows[0]?.fcm_token ? String(deviceRes.rows[0].fcm_token) : null;

    const invitePayload: Record<string, string> = {
      type: "EUNOIA_INVITE",
      sessionId: sessionId,
      inviterDeviceId: cleanInviter,
      inviterName: inviterName || cleanInviter,
      songTitle: currentSong?.title || "Music Track",
      expiresAt: String(expiresAt)
    };

    let fcmSent = false;
    if (fcmToken) {
      fcmSent = await sendFcmHttpV1Notification(fcmToken, invitePayload);
    }

    // 3. Always insert eunoia_signal for fallback real-time relay
    const signalId = crypto.randomUUID();
    await db.execute({
      sql: `INSERT INTO eunoia_signals (id, target_id, from_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?)`,
      args: [signalId, cleanInvitee, cleanInviter, JSON.stringify(invitePayload), now]
    });

    return NextResponse.json({
      success: true,
      sessionId,
      inviterDeviceId: cleanInviter,
      inviteeDeviceId: cleanInvitee,
      fcmSent,
      expiresAt
    });
  } catch (error: any) {
    console.error("Eunoia invite error:", error);
    return NextResponse.json({ error: error?.message || "Internal server error" }, { status: 500 });
  }
}
