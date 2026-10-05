import { NextRequest, NextResponse } from "next/server";

import { getRelatedSongs, searchYouTube } from "@/lib/youtube";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const SEARCH_CACHE_CONTROL = "public, s-maxage=86400, stale-while-revalidate=2592000";

function formatDurationMs(startedAt: number) {
  return Math.max(0, Date.now() - startedAt);
}

export async function GET(request: NextRequest) {
  const startedAt = Date.now();
  const query = request.nextUrl.searchParams.get("q")?.trim() || "";
  const relatedTo = request.nextUrl.searchParams.get("relatedTo")?.trim() || "";
  const rawLimit = Number(request.nextUrl.searchParams.get("limit") || 12);
  const rawPages = Number(request.nextUrl.searchParams.get("pages") || 1);
  const pages = Number.isFinite(rawPages) && rawPages > 0 ? Math.min(rawPages, 5) : 1;
  const category = request.nextUrl.searchParams.get("category")?.trim() || "";
  const limit = Number.isFinite(rawLimit) && rawLimit > 0 ? Math.min(rawLimit, 100) : 12;
  let cacheStatus = "miss";

  if (relatedTo) {
    try {
      const results = await getRelatedSongs(relatedTo, limit, {
        onCacheHit: () => {
          cacheStatus = "hit";
        },
        onCacheMiss: () => {
          cacheStatus = "miss";
        }
      });
      const durationMs = formatDurationMs(startedAt);
      console.info(`[spasiba-runtime] route=/api/search type=related cache=${cacheStatus} durationMs=${durationMs}`);
      return NextResponse.json(
        {
          query,
          results,
          cachedAt: new Date().toISOString()
        },
        {
          headers: {
            "Cache-Control": SEARCH_CACHE_CONTROL,
            "Server-Timing": `total;dur=${durationMs}`,
            "X-Spasiba-Cache": cacheStatus
          }
        }
      );
    } catch (error) {
      const durationMs = formatDurationMs(startedAt);
      console.info(`[spasiba-runtime] route=/api/search type=related cache=${cacheStatus} durationMs=${durationMs} error=true`);
      return NextResponse.json(
        {
          error: error instanceof Error ? error.message : "Related search failed."
        },
        {
          status: 500
        }
      );
    }
  }

  if (!query) {
    return NextResponse.json(
      {
        error: "A search query is required."
      },
      {
        status: 400
      }
    );
  }

  try {
    const results = await searchYouTube(
      query,
      limit,
      { pages, category },
      {
        onCacheHit: () => {
          cacheStatus = "hit";
        },
        onCacheMiss: () => {
          cacheStatus = "miss";
        }
      }
    );
    const durationMs = formatDurationMs(startedAt);
    console.info(`[spasiba-runtime] route=/api/search type=query cache=${cacheStatus} durationMs=${durationMs}`);
    return NextResponse.json(
      {
        query,
        results,
        cachedAt: new Date().toISOString()
      },
      {
        headers: {
          "Cache-Control": SEARCH_CACHE_CONTROL,
          "Server-Timing": `total;dur=${durationMs}`,
          "X-Spasiba-Cache": cacheStatus
        }
      }
    );
  } catch (error) {
    const durationMs = formatDurationMs(startedAt);
    console.info(`[spasiba-runtime] route=/api/search type=query cache=${cacheStatus} durationMs=${durationMs} error=true`);
    return NextResponse.json(
      {
        error:
          error instanceof Error
            ? error.message
            : typeof error === "string" && error.trim()
            ? error
            : "Search failed."
      },
      {
        status: 500
      }
    );
  }
}
