import { NextRequest, NextResponse } from "next/server";

import {
  clearVisitorHistory,
  clearVisitorSuggestionData,
  getVisitorProfile,
  getVisitorProfileSummary,
  updateVisitorProfile
} from "@/lib/profile";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
const PROFILE_CACHE_CONTROL = "private, max-age=86400, stale-while-revalidate=2592000";

function getVisitorId(request: NextRequest) {
  return request.cookies.get("spasiba_guest_id")?.value?.trim() || "";
}

function formatDurationMs(startedAt: number) {
  return Math.max(0, Date.now() - startedAt);
}

export async function GET(request: NextRequest) {
  const startedAt = Date.now();
  const visitorId = getVisitorId(request);
  const mode = request.nextUrl.searchParams.get("mode")?.trim().toLowerCase();

  if (!visitorId) {
    return NextResponse.json({ profile: null });
  }

  try {
    const profile = mode === "light" ? await getVisitorProfileSummary(visitorId) : await getVisitorProfile(visitorId);
    const durationMs = formatDurationMs(startedAt);
    console.info(`[spasiba-runtime] route=/api/profile durationMs=${durationMs}`);
    return NextResponse.json(
      { profile },
      {
        headers: {
          "Cache-Control": PROFILE_CACHE_CONTROL,
          "Server-Timing": `total;dur=${durationMs}`
        }
      }
    );
  } catch (error) {
    const durationMs = formatDurationMs(startedAt);
    console.info(`[spasiba-runtime] route=/api/profile durationMs=${durationMs} error=true`);
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to load profile."
      },
      {
        status: 500
      }
    );
  }
}

export async function PATCH(request: NextRequest) {
  const visitorId = getVisitorId(request);

  if (!visitorId) {
    return NextResponse.json(
      {
        error: "Visitor id is missing."
      },
      {
        status: 400
      }
    );
  }

  const payload = (await request.json().catch(() => null)) as
    | { name?: string; username?: string; profileImageDataUrl?: string | null }
    | null;

  try {
    const result = await updateVisitorProfile(visitorId, {
      name: payload?.name,
      username: payload?.username,
      ...(payload && Object.prototype.hasOwnProperty.call(payload, "profileImageDataUrl")
        ? { profileImageDataUrl: payload.profileImageDataUrl ?? null }
        : {})
    });
    const profile = await getVisitorProfileSummary(visitorId);
    return NextResponse.json({ ok: true, ...result, profile });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to update profile."
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
    return NextResponse.json(
      {
        error: "Visitor id is missing."
      },
      {
        status: 400
      }
    );
  }

  const scope = request.nextUrl.searchParams.get("scope")?.trim();

  if (scope !== "history" && scope !== "suggestions") {
    return NextResponse.json(
      {
        error: "A valid clear scope is required."
      },
      {
        status: 400
      }
    );
  }

  try {
    if (scope === "history") {
      await clearVisitorHistory(visitorId);
    } else {
      await clearVisitorSuggestionData(visitorId);
    }

    return NextResponse.json({ ok: true, scope });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to clear profile data."
      },
      {
        status: 500
      }
    );
  }
}
