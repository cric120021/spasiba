import { NextRequest, NextResponse } from "next/server";

import { revokeSharedPlaylistAccessForVisitor } from "@/lib/playlists";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function getVisitorId(request: NextRequest) {
  return request.cookies.get("spasiba_guest_id")?.value?.trim() || "";
}

export async function DELETE(request: NextRequest, context: { params: Promise<{ shareToken: string }> }) {
  const visitorId = getVisitorId(request);

  if (!visitorId) {
    return NextResponse.json({ error: "Visitor id is missing." }, { status: 400 });
  }

  const { shareToken } = await context.params;

  try {
    await revokeSharedPlaylistAccessForVisitor(visitorId, shareToken);
    return NextResponse.json({ ok: true, revoked: true });
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
