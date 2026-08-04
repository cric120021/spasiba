import fs from "fs";
import path from "path";

// Load .env.local manually
try {
  const envPath = path.resolve(process.cwd(), ".env.local");
  if (fs.existsSync(envPath)) {
    const content = fs.readFileSync(envPath, "utf-8");
    for (const line of content.split("\n")) {
      const trimmed = line.trim();
      if (trimmed && !trimmed.startsWith("#") && trimmed.includes("=")) {
        const [key, ...vals] = trimmed.split("=");
        process.env[key.trim()] = vals.join("=").trim();
      }
    }
  }
} catch (e) {
  console.warn("Warning loading .env.local:", e);
}

import { getDb, ensureDbSchema } from "../lib/db";

async function clearAllUsers() {
  console.log("Connecting to Turso database and clearing user records...");
  await ensureDbSchema();
  const db = getDb();

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

  console.log("SUCCESS: All user records, devices, profiles, and sessions cleared from database.");
}

clearAllUsers().catch(err => {
  console.error("Error clearing users:", err);
  process.exit(1);
});
