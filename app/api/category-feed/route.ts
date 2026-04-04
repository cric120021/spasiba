import { NextRequest, NextResponse } from "next/server";

import { getHomeCategoryFeed } from "@/lib/home-category-feed";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const CATEGORY_FEED_CACHE_CONTROL = "public, s-maxage=21600, stale-while-revalidate=172800";

export async function GET(request: NextRequest) {
  const categoryId = request.nextUrl.searchParams.get("categoryId")?.trim() || "";

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
    const feed = await getHomeCategoryFeed(categoryId);

    if (!feed) {
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
        "Cache-Control": CATEGORY_FEED_CACHE_CONTROL
      }
    });
  } catch (error) {
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
