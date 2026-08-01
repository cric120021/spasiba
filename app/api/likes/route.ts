import { NextRequest, NextResponse } from "next/server";

import { getVisitorLikedSongs, isTrackLiked, likeTrack, unlikeTrack } from "@/lib/likes";
import { invalidateVisitorProfileCache } from "@/lib/profile";
import type { Song } from "@/lib/types";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function getVisitorId(request: NextRequest) {
  return request.cookies.get("spasiba_guest_id")?.value?.trim() || "";
}

export async function GET(request: NextRequest) {
  const visitorId = getVisitorId(request);

  if (!visitorId) {
    return NextResponse.json({ liked: false, songs: [] });
  }

  const videoId = request.nextUrl.searchParams.get("videoId")?.trim() || "";

  try {
    if (videoId) {
      const liked = await isTrackLiked(visitorId, videoId);
      return NextResponse.json({ liked });
    }

    const songs = await getVisitorLikedSongs(visitorId);
    return NextResponse.json({ songs });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to load likes."
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
    return NextResponse.json({ error: "Visitor id is missing." }, { status: 400 });
  }

  const payload = (await request.json().catch(() => null)) as { song?: Song } | null;
  const song = payload?.song;

  if (!song?.videoId || !song.title || !song.author) {
    return NextResponse.json({ error: "A valid song payload is required." }, { status: 400 });
  }

  try {
    await likeTrack(visitorId, song);
    invalidateVisitorProfileCache(visitorId, "full");
    return NextResponse.json({ ok: true, liked: true });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to like this song."
      },
      {
        status: 500
      }
    );
  }
}

export async function DELETE(request: NextRequest) {
  const visitorId = getVisitorId(request);

  if (!visitorId) {
    return NextResponse.json({ error: "Visitor id is missing." }, { status: 400 });
  }

  const videoId = request.nextUrl.searchParams.get("videoId")?.trim() || "";

  if (!videoId) {
    return NextResponse.json({ error: "A valid video id is required." }, { status: 400 });
  }

  try {
    await unlikeTrack(visitorId, videoId);
    invalidateVisitorProfileCache(visitorId, "full");
    return NextResponse.json({ ok: true, liked: false });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to remove this song from likes."
      },
      {
        status: 500
      }
    );
  }
}
