import fs from "fs";

async function getAccessTokenFromServiceAccount(saJsonStr: string): Promise<{ accessToken: string; projectId: string } | null> {
  try {
    const sa = JSON.parse(saJsonStr);
    const clientEmail = sa.client_email;
    const privateKey = sa.private_key;
    const projectId = sa.project_id;

    if (!clientEmail || !privateKey || !projectId) {
      console.warn("Invalid Service Account JSON: missing client_email, private_key, or project_id");
      return null;
    }

    const now = Math.floor(Date.now() / 1000);
    const claim = {
      iss: clientEmail,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      exp: now + 3600,
      iat: now
    };

    const crypto = await import("crypto");
    const base64UrlEncode = (str: string) => Buffer.from(str).toString("base64url");
    const header = base64UrlEncode(JSON.stringify({ alg: "RS256", typ: "JWT" }));
    const payload = base64UrlEncode(JSON.stringify(claim));
    const toSign = `${header}.${payload}`;

    const signer = crypto.createSign("RSA-SHA256");
    signer.update(toSign);
    const signature = signer.sign(privateKey, "base64url");
    const jwt = `${toSign}.${signature}`;

    const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion: jwt
      })
    });

    const tokenData = await tokenRes.json();
    if (tokenData.access_token) {
      return { accessToken: tokenData.access_token, projectId };
    }
    return null;
  } catch (err) {
    console.error("Failed to generate OAuth token from Service Account:", err);
    return null;
  }
}

export async function sendFcmHttpV1Notification(fcmToken: string, payloadData: Record<string, string>): Promise<boolean> {
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
    console.log("No FIREBASE_SERVICE_ACCOUNT_JSON or GOOGLE_APPLICATION_CREDENTIALS configured.");
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
    console.error("FCM HTTP v1 send error:", err);
    return false;
  }
}
