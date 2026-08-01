import { NextRequest, NextResponse } from "next/server";

import { getHomeCategoryFeed } from "@/lib/home-category-feed";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const CATEGORY_FEED_CACHE_CONTROL = "public, s-maxage=604800, stale-while-revalidate=2592000";

function formatDurationMs(startedAt: number) {
  return Math.max(0, Date.now() - startedAt);
}

export async function GET(request: NextRequest) {
  const startedAt = Date.now();
  const categoryId = request.nextUrl.searchParams.get("categoryId")?.trim() || "";
  let cacheStatus = "miss";

  if (!categoryId) {
    return NextResponse.json(
      {
        error: "A category id is required."
      },
      {
        status: 400
      }
    );
  }

  try {
    const feed = await getHomeCategoryFeed(categoryId, {
      onCacheHit: () => {
        cacheStatus = "hit";
      },
      onCacheMiss: () => {
        cacheStatus = "miss";
      }
    });
    const durationMs = formatDurationMs(startedAt);

    if (!feed) {
      console.info(`[spasiba-runtime] route=/api/category-feed cache=${cacheStatus} durationMs=${durationMs} notFound=true`);
      return NextResponse.json(
        {
          error: "Category not found."
        },
        {
          status: 404
        }
      );
    }

    return NextResponse.json(feed, {
      headers: {
        "Cache-Control": CATEGORY_FEED_CACHE_CONTROL,
        "Server-Timing": `total;dur=${durationMs}`,
        "X-Spasiba-Cache": cacheStatus
      }
    });
  } catch (error) {
    const durationMs = formatDurationMs(startedAt);
    console.info(`[spasiba-runtime] route=/api/category-feed cache=${cacheStatus} durationMs=${durationMs} error=true`);
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to load category feed."
      },
      {
        status: 500
      }
    );
  }
}
