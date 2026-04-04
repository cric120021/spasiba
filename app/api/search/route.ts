import { NextRequest, NextResponse } from "next/server";

import { getRelatedSongs, searchYouTube } from "@/lib/youtube";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const SEARCH_CACHE_CONTROL = "public, s-maxage=14400, stale-while-revalidate=604800";

export async function GET(request: NextRequest) {
  const query = request.nextUrl.searchParams.get("q")?.trim() || "";
  const relatedTo = request.nextUrl.searchParams.get("relatedTo")?.trim() || "";
  const rawLimit = Number(request.nextUrl.searchParams.get("limit") || 12);
  const rawPages = Number(request.nextUrl.searchParams.get("pages") || 1);
  const pages = Number.isFinite(rawPages) && rawPages > 0 ? Math.min(rawPages, 5) : 1;
  const category = request.nextUrl.searchParams.get("category")?.trim() || "";
  const limit = Number.isFinite(rawLimit) && rawLimit > 0 ? Math.min(rawLimit, 100) : 12;

  if (relatedTo) {
    try {
      const results = await getRelatedSongs(relatedTo, limit);
      return NextResponse.json(
        {
          query,
          results,
          cachedAt: new Date().toISOString()
        },
        {
          headers: {
            "Cache-Control": SEARCH_CACHE_CONTROL
          }
        }
      );
    } catch (error) {
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
    const results = await searchYouTube(query, limit, { pages, category });
    return NextResponse.json(
      {
        query,
        results,
        cachedAt: new Date().toISOString()
      },
      {
        headers: {
          "Cache-Control": SEARCH_CACHE_CONTROL
        }
      }
    );
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Search failed."
      },
      {
        status: 500
      }
    );
  }
}
