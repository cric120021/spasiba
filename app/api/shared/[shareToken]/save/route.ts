import { NextRequest, NextResponse } from "next/server";

import {
  isSharedPlaylistSaved,
  removeSharedPlaylistForVisitor,
  saveSharedPlaylistForVisitor
} from "@/lib/playlists";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function getVisitorId(request: NextRequest) {
  return request.cookies.get("spasiba_guest_id")?.value?.trim() || "";
}

export async function GET(request: NextRequest, context: { params: Promise<{ shareToken: string }> }) {
  const visitorId = getVisitorId(request);

  if (!visitorId) {
    return NextResponse.json({ saved: false });
  }

  const { shareToken } = await context.params;

  try {
    const saved = await isSharedPlaylistSaved(visitorId, shareToken);
    return NextResponse.json({ saved });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to load shared playlist state."
      },
      {
        status: 500
      }
    );
  }
}

export async function POST(request: NextRequest, context: { params: Promise<{ shareToken: string }> }) {
  const visitorId = getVisitorId(request);

  if (!visitorId) {
    return NextResponse.json({ error: "Visitor id is missing." }, { status: 400 });
  }

  const { shareToken } = await context.params;

  try {
    await saveSharedPlaylistForVisitor(visitorId, shareToken);
    return NextResponse.json({ ok: true, saved: true });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to save this shared playlist."
      },
      {
        status: 500
      }
    );
  }
}

export async function DELETE(request: NextRequest, context: { params: Promise<{ shareToken: string }> }) {
  const visitorId = getVisitorId(request);

  if (!visitorId) {
    return NextResponse.json({ error: "Visitor id is missing." }, { status: 400 });
  }

  const { shareToken } = await context.params;

  try {
    await removeSharedPlaylistForVisitor(visitorId, shareToken);
    return NextResponse.json({ ok: true, saved: false });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to remove this shared playlist."
      },
      {
        status: 500
      }
    );
  }
}
