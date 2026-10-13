import { NextRequest, NextResponse } from "next/server";

import { deletePlaylist, renamePlaylist } from "@/lib/playlists";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function getVisitorId(request: NextRequest) {
  return request.cookies.get("spasiba_guest_id")?.value?.trim() || "";
}

export async function PATCH(request: NextRequest, context: { params: Promise<{ playlistId: string }> }) {
  const visitorId = getVisitorId(request);

  if (!visitorId) {
    return NextResponse.json({ error: "Visitor id is missing." }, { status: 400 });
  }

  const { playlistId } = await context.params;
  const payload = (await request.json().catch(() => null)) as { name?: string } | null;
  const playlistName = payload?.name?.trim() || "";

  if (!playlistName) {
    return NextResponse.json({ error: "Playlist name is required." }, { status: 400 });
  }

  try {
    const playlist = await renamePlaylist(visitorId, playlistId, playlistName);
    return NextResponse.json({ playlist });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to update playlist."
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
    return NextResponse.json({ error: "Visitor id is missing." }, { status: 400 });
  }

  const { playlistId } = await context.params;

  if (!playlistId) {
    return NextResponse.json({ error: "Playlist id is required." }, { status: 400 });
  }

  try {
    await deletePlaylist(visitorId, playlistId);
    return NextResponse.json({ ok: true });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to delete playlist."
      },
      {
        status: 500
      }
    );
  }
}
