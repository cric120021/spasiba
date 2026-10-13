"use client";

import { useEffect } from "react";

import { getDeviceInfo } from "@/lib/device";
import { applyUiMode, readUiMode } from "@/lib/ui-mode";

const SPLASH_SESSION_KEY = "spasiba_splash_seen_v1";

function hasSeenSplashThisSession() {
  try {
    return window.sessionStorage.getItem(SPLASH_SESSION_KEY) === "1";
  } catch {
    return false;
  }
}

function markSplashSeenThisSession() {
  try {
    window.sessionStorage.setItem(SPLASH_SESSION_KEY, "1");
  } catch {
    // Ignore storage issues; the app shell should still boot.
  }
}

function scheduleNonCriticalTask(task: () => void) {
  const idleWindow = window as Window &
    typeof globalThis & {
      requestIdleCallback?: (callback: () => void, options?: { timeout: number }) => number;
    };

  if (idleWindow.requestIdleCallback) {
    idleWindow.requestIdleCallback(task, { timeout: 2000 });
    return;
  }

  window.setTimeout(task, 400);
}

export default function PwaBootstrap() {
  useEffect(() => {
    document.documentElement.style.background = "#000";
    document.documentElement.style.colorScheme = "dark";
    document.body.style.background = "#000";
    document.body.style.colorScheme = "dark";
    applyUiMode(readUiMode());

    document.querySelectorAll('meta[name="theme-color"]').forEach((themeMeta) => {
      themeMeta.setAttribute("content", "#000000");
    });

    const { isIOS, isAndroid, isStandalone } = getDeviceInfo();
    const root = document.documentElement;
    const splashMinimumMs = 1100;
    const splashStartedAt = typeof performance !== "undefined" ? performance.now() : Date.now();
    let readyFrame: number | null = null;
    let readyTimer: number | null = null;
    const hasSeenSplash = hasSeenSplashThisSession();

    root.dataset.device = isIOS ? "ios" : isAndroid ? "android" : "web";
    root.classList.toggle("is-standalone", isStandalone);

    if (hasSeenSplash) {
      document.body.classList.add("app-ready");
    } else {
      readyFrame = window.requestAnimationFrame(() => {
        const now = typeof performance !== "undefined" ? performance.now() : Date.now();
        const remaining = Math.max(0, splashMinimumMs - (now - splashStartedAt));
        readyTimer = window.setTimeout(() => {
          markSplashSeenThisSession();
          document.body.classList.add("app-ready");
        }, remaining);
      });
    }

    scheduleNonCriticalTask(() => {
      if (!("serviceWorker" in navigator)) {
        return;
      }

      void navigator.serviceWorker
        .register("/sw.js", { scope: "/", updateViaCache: "none" })
        .then((registration) => registration.update())
        .catch(() => {
          // PWA boot should stay resilient even if SW update checks fail.
        });
    });

    return () => {
      if (readyFrame !== null) {
        window.cancelAnimationFrame(readyFrame);
      }

      if (readyTimer !== null) {
        window.clearTimeout(readyTimer);
      }
    };
  }, []);

  return null;
}
