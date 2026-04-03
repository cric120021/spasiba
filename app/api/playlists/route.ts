import { NextRequest, NextResponse } from "next/server";

import { createPlaylist, getVisitorPlaylists, getVisitorSharedPlaylists } from "@/lib/playlists";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function getVisitorId(request: NextRequest) {
  return request.cookies.get("spasiba_guest_id")?.value?.trim() || "";
}

export async function GET(request: NextRequest) {
  const visitorId = getVisitorId(request);

  if (!visitorId) {
    return NextResponse.json({ playlists: [], sharedPlaylists: [] });
  }

  try {
    const [playlists, sharedPlaylists] = await Promise.all([
      getVisitorPlaylists(visitorId),
      getVisitorSharedPlaylists(visitorId)
    ]);
    return NextResponse.json({ playlists, sharedPlaylists });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to load playlists."
      },
      {
        status: 500
      }
    );
  }
}

export async function POST(request: NextRequest) {
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

  const payload = (await request.json().catch(() => null)) as { name?: string } | null;
  const playlistName = payload?.name?.trim() || "";

  if (!playlistName) {
    return NextResponse.json(
      {
        error: "Playlist name is required."
      },
      {
        status: 400
      }
    );
  }

  try {
    const playlist = await createPlaylist(visitorId, playlistName);
    return NextResponse.json({ playlist }, { status: 201 });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to create playlist."
      },
      {
        status: 500
      }
    );
  }
}
