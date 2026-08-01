"use client";

import { usePathname } from "next/navigation";
import { useEffect } from "react";

import { hasPendingProfileChanges, syncProfileWithServer } from "@/lib/profile-client";
import { ensureZeroWasteCacheMaintenance } from "@/lib/zero-waste-idb";

const SESSION_SYNC_FLAG = "spasiba:session-sync:v1";

export default function SessionDataBootstrap() {
  const pathname = usePathname();

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    const idleWindow = window as Window &
      typeof globalThis & {
        requestIdleCallback?: (callback: () => void, options?: { timeout: number }) => number;
        cancelIdleCallback?: (handle: number) => void;
      };

    if (window.sessionStorage.getItem(SESSION_SYNC_FLAG)) {
      return;
    }

    window.sessionStorage.setItem(SESSION_SYNC_FLAG, "1");

    let cancelled = false;
    let idleHandle: number | null = null;
    let timeoutHandle: number | null = null;

    const runSync = () => {
      if (cancelled) {
        return;
      }

      ensureZeroWasteCacheMaintenance();

      if (hasPendingProfileChanges() || pathname.startsWith("/profile")) {
        void syncProfileWithServer().catch(() => {
          // Local cache remains the source during this session.
        });
      }
    };

    if (idleWindow.requestIdleCallback) {
      idleHandle = idleWindow.requestIdleCallback(runSync, { timeout: 2000 });
    } else {
      timeoutHandle = window.setTimeout(runSync, 1200);
    }

    return () => {
      cancelled = true;

      if (idleHandle !== null && idleWindow.cancelIdleCallback) {
        idleWindow.cancelIdleCallback(idleHandle);
      }

      if (timeoutHandle !== null) {
        window.clearTimeout(timeoutHandle);
      }
    };
  }, [pathname]);

  return null;
}
