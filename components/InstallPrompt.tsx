"use client";

import { memo, useEffect, useState } from "react";
import { getDeviceInfo } from "@/lib/device";

const DISMISS_KEY = "spasiba_android_install_prompt_dismissed_v1";
const APK_URL = "https://github.com/cric120021/spasiba/releases/download/v1.0.0/spasiba.apk";

function InstallPromptComponent() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    // Check if dismissed before
    if (typeof window !== "undefined" && window.localStorage.getItem(DISMISS_KEY) === "1") {
      return;
    }

    const info = getDeviceInfo();
    // Strictly ONLY on Android OS. Never show on iOS, Mac, Windows, or if already in standalone app mode.
    if (!info.isAndroid || info.isIOS || info.isStandalone) {
      return;
    }

    // Polite, non-intrusive entrance: appear gently after 1.5s
    const timer = window.setTimeout(() => {
      setVisible(true);
    }, 1500);

    return () => window.clearTimeout(timer);
  }, []);

  if (!visible) {
    return null;
  }

  const dismiss = () => {
    setVisible(false);
    try {
      window.localStorage.setItem(DISMISS_KEY, "1");
    } catch (_e) {}
  };

  return (
    <aside
      role="dialog"
      aria-labelledby="android-prompt-title"
      className="pointer-events-none fixed inset-x-0 bottom-4 z-50 flex justify-center px-4 animate-in fade-in slide-in-from-bottom-4 duration-300"
    >
      <div className="pointer-events-auto flex w-full max-w-md items-center justify-between gap-3 rounded-2xl border border-white/10 bg-[#070b14]/95 p-3.5 shadow-2xl shadow-black/80 backdrop-blur-xl sm:p-4">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-emerald-400/20 bg-emerald-500/10 text-emerald-400">
            <svg className="h-5 w-5 fill-current" viewBox="0 0 24 24">
              <path d="M17.523 15.3414c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.551 0 .9993.4482.9993.9993.0001.5511-.4483.9997-.9993.9997m-11.046 0c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.5511 0 .9993.4482.9993.9993 0 .5511-.4482.9997-.9993.9997m11.4045-6.02l1.996-3.4572c.1056-.1828.043-.4167-.14-.5223-.1828-.1055-.4169-.043-.5224.1399l-2.0252 3.5078c-1.5794-.7224-3.3756-1.1245-5.3129-1.1245s-3.7335.4021-5.3129 1.1245L4.5369 5.4818c-.1055-.1829-.3396-.2454-.5224-.1399-.183.1056-.2456.3395-.14.5223l1.996 3.4572C2.6889 11.0858 0 14.8872 0 19.3418h24c0-4.4546-2.6889-8.256-5.877-10.0204" />
            </svg>
          </div>

          <div className="min-w-0">
            <h2 id="android-prompt-title" className="text-xs font-semibold text-white">
              Spasiba for Android
            </h2>
            <p className="text-[11px] text-slate-400">
              Native background play & lock-screen audio
            </p>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-1.5">
          <a
            href={APK_URL}
            download="spasiba.apk"
            onClick={dismiss}
            className="inline-flex items-center gap-1.5 rounded-xl border border-emerald-400/40 bg-gradient-to-r from-emerald-500 to-teal-400 px-3 py-1.5 text-xs font-bold text-black shadow-md shadow-emerald-500/20 transition hover:from-emerald-400 hover:to-teal-300 active:scale-95 cursor-pointer"
          >
            <span>Install APK</span>
          </a>

          <button
            type="button"
            onClick={dismiss}
            className="rounded-xl px-2 py-1.5 text-[11px] font-medium text-slate-400 transition hover:text-white"
            aria-label="Dismiss prompt"
          >
            ✕
          </button>
        </div>
      </div>
    </aside>
  );
}

const InstallPrompt = memo(InstallPromptComponent);

export default InstallPrompt;
