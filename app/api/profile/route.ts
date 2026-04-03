import { NextRequest, NextResponse } from "next/server";

import {
  clearVisitorHistory,
  clearVisitorSuggestionData,
  getVisitorProfile,
  updateVisitorProfile
} from "@/lib/profile";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function getVisitorId(request: NextRequest) {
  return request.cookies.get("spasiba_guest_id")?.value?.trim() || "";
}

export async function GET(request: NextRequest) {
  const visitorId = getVisitorId(request);

  if (!visitorId) {
    return NextResponse.json({ profile: null });
  }

  try {
    const profile = await getVisitorProfile(visitorId);
    return NextResponse.json({ profile });
  } catch (error) {
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
    const profile = await getVisitorProfile(visitorId);
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
