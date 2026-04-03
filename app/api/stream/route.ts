import { NextRequest, NextResponse } from "next/server";

import { getAudioStream } from "@/lib/youtube";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  const videoId = request.nextUrl.searchParams.get("videoId")?.trim();
  const url = request.nextUrl.searchParams.get("url")?.trim();
  const target = videoId || url;

  if (!target) {
    return NextResponse.json(
      {
        error: "A videoId or url is required."
      },
      {
        status: 400
      }
    );
  }

  try {
    const response = await getAudioStream(target);
    return NextResponse.json(response, {
      headers: {
        "Cache-Control": "no-store"
      }
    });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Stream resolution failed."
      },
      {
        status: 500
      }
    );
  }
}
