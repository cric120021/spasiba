"use client";

import { memo, useEffect, useState } from "react";
import { getDeviceInfo } from "@/lib/device";

const DISMISS_KEY = "spasiba_android_install_prompt_dismissed_v2";

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
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="android-prompt-title"
      className="fixed inset-0 z-[180] flex items-center justify-center bg-black/65 px-6 backdrop-blur-sm animate-in fade-in duration-200"
      onClick={dismiss}
    >
      <div
        className="w-full max-w-[320px] rounded-[28px] border border-white/[0.08] bg-[#1e222a] p-6 shadow-2xl shadow-black/90 text-left animate-in zoom-in-95 duration-200"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-500/15 text-emerald-400 mb-4">
          <svg className="h-6 w-6 fill-current" viewBox="0 0 24 24">
            <path d="M17.523 15.3414c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.551 0 .9993.4482.9993.9993.0001.5511-.4483.9997-.9993.9997m-11.046 0c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.5511 0 .9993.4482.9993.9993 0 .5511-.4482.9997-.9993.9997m11.4045-6.02l1.996-3.4572c.1056-.1828.043-.4167-.14-.5223-.1828-.1055-.4169-.043-.5224.1399l-2.0252 3.5078c-1.5794-.7224-3.3756-1.1245-5.3129-1.1245s-3.7335.4021-5.3129 1.1245L4.5369 5.4818c-.1055-.1829-.3396-.2454-.5224-.1399-.183.1056-.2456.3395-.14.5223l1.996 3.4572C2.6889 11.0858 0 14.8872 0 19.3418h24c0-4.4546-2.6889-8.256-5.877-10.0204" />
          </svg>
        </div>

        <h2 id="android-prompt-title" className="text-xl font-medium tracking-tight text-white">
          Do you want to download?
        </h2>

        <p className="mt-2 text-sm text-slate-300 font-medium">
          spasiba.apk <span className="text-slate-400 font-normal">(61.9 MB)</span>
        </p>

        <p className="mt-1 text-xs text-slate-400 leading-relaxed">
          Download Spasiba for Android for native background play and lock-screen controls.
        </p>

        <div className="mt-6 flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={dismiss}
            className="rounded-full px-4 py-2 text-sm font-medium text-emerald-400 transition hover:bg-emerald-500/10 active:bg-emerald-500/20"
          >
            Cancel
          </button>

          <a
            href="/download"
            download="spasiba.apk"
            onClick={dismiss}
            className="inline-flex items-center justify-center rounded-full bg-emerald-400 px-5 py-2 text-sm font-semibold text-slate-950 shadow-md shadow-emerald-500/20 transition hover:bg-emerald-300 active:scale-95"
          >
            Download
          </a>
        </div>
      </div>
    </div>
  );
}

const InstallPrompt = memo(InstallPromptComponent);

export default InstallPrompt;
