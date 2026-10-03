"use client";

import { memo, useEffect, useMemo, useState } from "react";

import { getDeviceInfo } from "@/lib/device";

type BeforeInstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{
    outcome: "accepted" | "dismissed";
    platform: string;
  }>;
};

const DISMISS_KEY = "spasiba_install_prompt_dismissed";

function InstallPromptComponent() {
  const [promptEvent, setPromptEvent] = useState<BeforeInstallPromptEvent | null>(null);
  const [dismissed, setDismissed] = useState(false);
  const [deviceInfo, setDeviceInfo] = useState(() => getDeviceInfo());

  useEffect(() => {
    setDeviceInfo(getDeviceInfo());
    setDismissed(window.localStorage.getItem(DISMISS_KEY) === "1");

    const handleBeforeInstallPrompt = (event: Event) => {
      event.preventDefault();
      setPromptEvent(event as BeforeInstallPromptEvent);
      setDeviceInfo(getDeviceInfo());
    };

    const handleInstalled = () => {
      setPromptEvent(null);
      setDeviceInfo(getDeviceInfo());
    };

    window.addEventListener("beforeinstallprompt", handleBeforeInstallPrompt);
    window.addEventListener("appinstalled", handleInstalled);

    return () => {
      window.removeEventListener("beforeinstallprompt", handleBeforeInstallPrompt);
      window.removeEventListener("appinstalled", handleInstalled);
    };
  }, []);

  const shouldShow = useMemo(() => {
    if (dismissed || deviceInfo.isStandalone) {
      return false;
    }

    if (deviceInfo.isIOS) {
      return true;
    }

    if (deviceInfo.isAndroid) {
      return true;
    }

    return false;
  }, [deviceInfo.isIOS, deviceInfo.isAndroid, deviceInfo.isStandalone, dismissed]);

  if (!shouldShow) {
    return null;
  }

  const closePrompt = () => {
    setDismissed(true);
    window.localStorage.setItem(DISMISS_KEY, "1");
  };

  const isAndroidOnly = deviceInfo.isAndroid && !deviceInfo.isIOS;

  return (
    <div className="app-install-prompt pointer-events-none fixed inset-x-0 bottom-0 z-[60] px-3 sm:px-4">
      <div className="pointer-events-auto mx-auto max-w-xl rounded-[28px] border border-white/10 bg-[rgba(8,13,22,0.96)] p-4 shadow-[0_24px_70px_rgba(0,0,0,0.4)] backdrop-blur-xl sm:p-5">
        <div className="flex items-start justify-between gap-4">
          <div className="space-y-2">
            <p className="text-[11px] uppercase tracking-[0.28em] text-slate-400">
              {deviceInfo.isIOS ? "Install on iPhone" : "Spasiba for Android"}
            </p>
            <h2 className="text-lg font-semibold tracking-tight text-white">
              {isAndroidOnly ? "Download Native Android App" : "Open Spasiba like a native app"}
            </h2>
            <p className="text-sm leading-6 text-slate-300/78">
              {isAndroidOnly
                ? "Download the official APK in 1 single click for background play, offline tracks, and zero lag."
                : "Open Safari's Share menu, then tap Add to Home Screen."}
            </p>
          </div>

          <button
            type="button"
            onClick={closePrompt}
            className="rounded-full border border-white/10 bg-[#050816] px-3 py-2 text-[11px] uppercase tracking-[0.22em] text-slate-300 transition hover:border-teal-300/20 hover:bg-[#0a1120] hover:text-white"
          >
            Dismiss
          </button>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-3">
          {/* SINGLE CLICK ANDROID DOWNLOAD BUTTON - STRICTLY HIDDEN ON IOS */}
          {isAndroidOnly ? (
            <a
              href="/spasiba.apk"
              download="spasiba.apk"
              onClick={closePrompt}
              className="inline-flex items-center gap-2 rounded-2xl border border-emerald-400/40 bg-gradient-to-r from-emerald-500 to-teal-400 px-5 py-3 text-sm font-bold text-black shadow-lg shadow-emerald-500/20 transition hover:from-emerald-400 hover:to-teal-300 active:scale-95 cursor-pointer"
            >
              <svg className="h-4 w-4 fill-current" viewBox="0 0 24 24">
                <path d="M17.523 15.3414c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.551 0 .9993.4482.9993.9993.0001.5511-.4483.9997-.9993.9997m-11.046 0c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.5511 0 .9993.4482.9993.9993 0 .5511-.4482.9997-.9993.9997m11.4045-6.02l1.996-3.4572c.1056-.1828.043-.4167-.14-.5223-.1828-.1055-.4169-.043-.5224.1399l-2.0252 3.5078c-1.5794-.7224-3.3756-1.1245-5.3129-1.1245s-3.7335.4021-5.3129 1.1245L4.5369 5.4818c-.1055-.1829-.3396-.2454-.5224-.1399-.183.1056-.2456.3395-.14.5223l1.996 3.4572C2.6889 11.0858 0 14.8872 0 19.3418h24c0-4.4546-2.6889-8.256-5.877-10.0204" />
              </svg>
              <span>Download Android App (.apk)</span>
            </a>
          ) : null}

          <button
            type="button"
            onClick={closePrompt}
            className="rounded-2xl border border-white/10 bg-[#050816] px-4 py-3 text-sm font-medium text-slate-100 transition hover:border-white/20 hover:bg-[#0a1120]"
          >
            Continue in browser
          </button>
        </div>
      </div>
    </div>
  );
}

const InstallPrompt = memo(InstallPromptComponent);

export default InstallPrompt;
