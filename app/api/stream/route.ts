import { NextRequest, NextResponse } from "next/server";

import { getAudioStream } from "@/lib/youtube";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function getStreamCacheControl(expiresAt: number | null | undefined) {
  if (!expiresAt) {
    return "public, max-age=300, s-maxage=300, stale-while-revalidate=900";
  }

  const remainingMs = expiresAt - Date.now() - 30_000;

  if (remainingMs <= 0) {
    return "no-store";
  }

  const sMaxAgeSeconds = Math.max(30, Math.min(900, Math.floor(remainingMs / 1000)));
  const maxAgeSeconds = Math.max(15, Math.min(300, sMaxAgeSeconds));

  return `public, max-age=${maxAgeSeconds}, s-maxage=${sMaxAgeSeconds}, stale-while-revalidate=900`;
}

function formatDurationMs(startedAt: number) {
  return Math.max(0, Date.now() - startedAt);
}

export async function GET(request: NextRequest) {
  const startedAt = Date.now();
  const videoId = request.nextUrl.searchParams.get("videoId")?.trim();
  const url = request.nextUrl.searchParams.get("url")?.trim();
  const target = videoId || url;
  let cacheStatus = "miss";

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
    const response = await getAudioStream(target, {
      onCacheHit: () => {
        cacheStatus = "hit";
      },
      onCacheMiss: () => {
        cacheStatus = "miss";
      }
    });
    const durationMs = formatDurationMs(startedAt);
    console.info(`[spasiba-runtime] route=/api/stream cache=${cacheStatus} durationMs=${durationMs} target=${videoId ? "videoId" : "url"}`);
    return NextResponse.json(response, {
      headers: {
        "Cache-Control": getStreamCacheControl(response.expiresAt),
        "Server-Timing": `total;dur=${durationMs}`,
        "X-Spasiba-Cache": cacheStatus
      }
    });
  } catch (error) {
    const durationMs = formatDurationMs(startedAt);
    console.info(`[spasiba-runtime] route=/api/stream cache=${cacheStatus} durationMs=${durationMs} error=true`);
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
