import { NextRequest, NextResponse } from "next/server";

import { unlockSharedPlaylistForVisitor } from "@/lib/playlists";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function getVisitorId(request: NextRequest) {
  return request.cookies.get("spasiba_guest_id")?.value?.trim() || "";
}

export async function POST(request: NextRequest) {
  const visitorId = getVisitorId(request);

  if (!visitorId) {
    return NextResponse.json({ error: "Visitor id is missing." }, { status: 400 });
  }

  const payload = (await request.json().catch(() => null)) as { code?: string } | null;
  const code = payload?.code?.trim() || "";

  if (!code) {
    return NextResponse.json({ error: "Enter a valid share code." }, { status: 400 });
  }

  try {
    const result = await unlockSharedPlaylistForVisitor(visitorId, code);
    return NextResponse.json({ ok: true, shareToken: result.shareToken, playlist: result.playlist });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to unlock this playlist."
      },
      {
        status: 500
      }
    );
  }
}
