import { NextRequest, NextResponse } from "next/server";

import {
  getPlaylistShareAccess,
  grantPlaylistAccessByUsername,
  revokePlaylistAccessForUsername,
  revokePlaylistShare
} from "@/lib/playlists";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function getVisitorId(request: NextRequest) {
  return request.cookies.get("spasiba_guest_id")?.value?.trim() || "";
}

export async function GET(request: NextRequest, context: { params: Promise<{ playlistId: string }> }) {
  const visitorId = getVisitorId(request);

  if (!visitorId) {
    return NextResponse.json({ error: "Visitor id is missing." }, { status: 400 });
  }

  const { playlistId } = await context.params;

  if (!playlistId) {
    return NextResponse.json({ error: "Playlist id is required." }, { status: 400 });
  }

  try {
    const access = await getPlaylistShareAccess(visitorId, playlistId);
    const origin = new URL(request.url).origin;
    return NextResponse.json({
      ...access,
      shareUrl: `${origin}/shared/${access.shareToken}`
    });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to load share access."
      },
      {
        status: 500
      }
    );
  }
}

export async function POST(request: NextRequest, context: { params: Promise<{ playlistId: string }> }) {
  const visitorId = getVisitorId(request);

  if (!visitorId) {
    return NextResponse.json(
      {
        error: "Visitor id is missing."
      },
      {
        status: 400
      }
    );
  }

  const { playlistId } = await context.params;

  if (!playlistId) {
    return NextResponse.json(
      {
        error: "Playlist id is required."
      },
      {
        status: 400
      }
    );
  }

  const payload = (await request.json().catch(() => null)) as { username?: string } | null;

  try {
    const access = await grantPlaylistAccessByUsername(visitorId, playlistId, payload?.username?.trim() || "");
    const origin = new URL(request.url).origin;

    return NextResponse.json({
      ...access,
      shareUrl: `${origin}/shared/${access.shareToken}`
    });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to create a share link."
      },
      {
        status: 500
      }
    );
  }
}

export async function DELETE(request: NextRequest, context: { params: Promise<{ playlistId: string }> }) {
  const visitorId = getVisitorId(request);

  if (!visitorId) {
    return NextResponse.json(
      {
        error: "Visitor id is missing."
      },
      {
        status: 400
      }
    );
  }

  const { playlistId } = await context.params;

  if (!playlistId) {
    return NextResponse.json(
      {
        error: "Playlist id is required."
      },
      {
        status: 400
      }
    );
  }

  const payload = (await request.json().catch(() => null)) as { username?: string } | null;

  try {
    const username = payload?.username?.trim() || "";

    if (username) {
      const grants = await revokePlaylistAccessForUsername(visitorId, playlistId, username);
      return NextResponse.json({ ok: true, grants });
    }

    await revokePlaylistShare(visitorId, playlistId);
    return NextResponse.json({ ok: true, grants: [] });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to revoke sharing."
      },
      {
        status: 500
      }
    );
  }
}
