import { NextRequest, NextResponse } from "next/server";

import { getVisitorListenerProfile, recordVisitorEngagementBatch } from "@/lib/engagement";
import { invalidateVisitorProfileCache } from "@/lib/profile";
import type { EngagementPayload, ListenerProfile } from "@/lib/types";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const PROFILE_HISTORY_ACTIONS = new Set(["play_start", "complete", "skip_early", "skip_mid", "skip_late"]);

function getVisitorId(request: NextRequest) {
  return request.cookies.get("spasiba_guest_id")?.value?.trim() || "";
}

export async function GET(request: NextRequest) {
  const visitorId = getVisitorId(request);

  if (!visitorId) {
    return NextResponse.json({ profile: null });
  }

  try {
    const profile = await getVisitorListenerProfile(visitorId);
    return NextResponse.json({ profile });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to load listener profile."
      },
      {
        status: 500
      }
    );
  }
}

export async function POST(request: NextRequest) {
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
    | {
        events?: Array<
          EngagementPayload & {
            signals?: string[];
          }
        >;
        profile?: ListenerProfile | null;
      }
    | (EngagementPayload & {
        signals?: string[];
        profile?: ListenerProfile | null;
      })
    | null;

  const events = Array.isArray((payload as { events?: unknown })?.events)
    ? (((payload as { events?: unknown[] }).events || []) as Array<
        EngagementPayload & {
          signals?: string[];
        }
      >)
    : payload
      ? [
          payload as EngagementPayload & {
            signals?: string[];
          }
        ]
      : [];

  const validEvents = events.filter((event) => event?.song?.videoId && event?.song?.title && event?.action);

  if (!validEvents.length) {
    return NextResponse.json(
      {
        error: "Engagement payload is invalid."
      },
      {
        status: 400
      }
    );
  }

  try {
    const profile = await recordVisitorEngagementBatch(
      visitorId,
      validEvents,
      normalizeListenerProfilePayload((payload as { profile?: ListenerProfile | null } | null)?.profile)
    );
    if (validEvents.some((event) => PROFILE_HISTORY_ACTIONS.has(String(event.action || "").trim().toLowerCase()))) {
      invalidateVisitorProfileCache(visitorId, "full");
    }
    return NextResponse.json({ ok: true, profile, count: validEvents.length });
  } catch (error) {
    return NextResponse.json(
      {
        error: error instanceof Error ? error.message : "Unable to store engagement."
      },
      {
        status: 500
      }
    );
  }
}

function normalizeListenerProfilePayload(profile: ListenerProfile | null | undefined) {
  return profile && typeof profile === "object" ? profile : null;
}
