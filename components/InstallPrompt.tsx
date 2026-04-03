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
      return Boolean(promptEvent) || true;
    }

    return false;
  }, [deviceInfo.isIOS, deviceInfo.isAndroid, deviceInfo.isStandalone, dismissed, promptEvent]);

  if (!shouldShow) {
    return null;
  }

  const closePrompt = () => {
    setDismissed(true);
    window.localStorage.setItem(DISMISS_KEY, "1");
  };

  const installLabel = deviceInfo.isIOS
    ? "Open Safari's Share menu, then tap Add to Home Screen."
    : promptEvent
      ? "Install Spasiba for a fullscreen app with no browser chrome."
      : "Open your browser menu and choose Install App to launch fullscreen.";

  return (
    <div className="app-install-prompt pointer-events-none fixed inset-x-0 bottom-0 z-[60] px-3 sm:px-4">
      <div className="pointer-events-auto mx-auto max-w-xl rounded-[28px] border border-white/10 bg-[rgba(8,13,22,0.96)] p-4 shadow-[0_24px_70px_rgba(0,0,0,0.4)] backdrop-blur-xl sm:p-5">
        <div className="flex items-start justify-between gap-4">
          <div className="space-y-2">
            <p className="text-[11px] uppercase tracking-[0.28em] text-slate-400">
              {deviceInfo.isIOS ? "Install on iPhone" : "Install app"}
            </p>
            <h2 className="text-lg font-semibold tracking-tight text-white">Open Spasiba like a native app</h2>
            <p className="text-sm leading-6 text-slate-300/78">{installLabel}</p>
          </div>

          <button
            type="button"
            onClick={closePrompt}
            className="rounded-full border border-white/10 bg-[#050816] px-3 py-2 text-[11px] uppercase tracking-[0.22em] text-slate-300 transition hover:border-teal-300/20 hover:bg-[#0a1120] hover:text-white"
          >
            Dismiss
          </button>
        </div>

        <div className="mt-4 flex flex-wrap gap-3">
          {deviceInfo.isAndroid && promptEvent ? (
            <button
              type="button"
              onClick={() => {
                void promptEvent.prompt().then(() => promptEvent.userChoice).then(() => {
                  setPromptEvent(null);
                });
              }}
              className="rounded-2xl border border-white/12 bg-[#050816] px-4 py-3 text-sm font-medium text-white transition hover:border-teal-300/25 hover:bg-[#0a1120]"
            >
              Install App
            </button>
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
