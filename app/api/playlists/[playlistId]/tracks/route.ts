import { NextRequest, NextResponse } from "next/server";

import { addTrackToPlaylist, getVisitorPlaylistById, removeTrackFromPlaylist } from "@/lib/playlists";
import type { Song } from "@/lib/types";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function getVisitorId(request: NextRequest) {
  return request.cookies.get("spasiba_guest_id")?.value?.trim() || "";
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
  const payload = (await request.json().catch(() => null)) as { song?: Song } | null;
  const song = payload?.song;

  if (!playlistId || !song?.videoId || !song.title || !song.thumbnail || !song.duration || !song.author || !song.url) {
    return NextResponse.json(
      {
        error: "A valid song payload is required."
      },
      {
        status: 400
      }
    );
  }

  try {
    await addTrackToPlaylist(visitorId, playlistId, song);
    const playlist = await getVisitorPlaylistById(visitorId, playlistId);
    return NextResponse.json({ ok: true, playlist });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to add track to playlist."
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
  const videoId = request.nextUrl.searchParams.get("videoId")?.trim() || "";

  if (!playlistId || !videoId) {
    return NextResponse.json(
      {
        error: "A valid video id is required."
      },
      {
        status: 400
      }
    );
  }

  try {
    await removeTrackFromPlaylist(visitorId, playlistId, videoId);
    const playlist = await getVisitorPlaylistById(visitorId, playlistId);
    return NextResponse.json({ ok: true, playlist });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to remove track from playlist."
      },
      {
        status: 500
      }
    );
  }
}
