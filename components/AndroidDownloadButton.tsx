"use client";

import { memo, useEffect, useState } from "react";
import { getDeviceInfo } from "@/lib/device";

function AndroidDownloadButtonComponent() {
  const [showAndroidButton, setShowAndroidButton] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  useEffect(() => {
    const info = getDeviceInfo();
    // Strictly ONLY on Android OS. Never show on iOS, Windows, Mac, or desktop.
    if (info.isAndroid && !info.isIOS) {
      setShowAndroidButton(true);
    }
  }, []);

  if (!showAndroidButton) {
    return null;
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setShowConfirm(true)}
        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/[0.05] text-slate-100/84 transition hover:bg-white/[0.09] active:scale-95 sm:h-10 sm:w-10 sm:rounded-[18px]"
        aria-label="Download Spasiba App"
        title="Download Spasiba App"
      >
        <svg aria-hidden viewBox="0 0 24 24" className="h-4.5 w-4.5 fill-current">
          <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z" />
        </svg>
      </button>

      {showConfirm && (
        <div
          className="fixed inset-0 z-[180] flex items-center justify-center bg-black/70 px-4 backdrop-blur-sm animate-in fade-in duration-200"
          onClick={() => setShowConfirm(false)}
        >
          <div
            className="w-full max-w-sm rounded-2xl border border-white/10 bg-[#0d121f] p-5 shadow-2xl text-left"
            role="dialog"
            aria-modal="true"
            aria-labelledby="download-confirm-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start gap-3.5 mb-4">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-emerald-400/20 bg-emerald-500/10 text-emerald-400">
                <svg className="h-5 w-5 fill-current" viewBox="0 0 24 24">
                  <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z" />
                </svg>
              </div>
              <div className="min-w-0">
                <h3 id="download-confirm-title" className="text-sm font-semibold text-white">
                  Download Spasiba App
                </h3>
                <p className="mt-1 text-xs text-slate-300">
                  Do you want to download the Spasiba Android APK?
                </p>
                <p className="mt-1 text-[11px] text-slate-400">
                  Includes native background playback & lock screen audio controls.
                </p>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-white/5">
              <button
                type="button"
                onClick={() => setShowConfirm(false)}
                className="rounded-xl px-3.5 py-2 text-xs font-medium text-slate-400 transition hover:bg-white/5 hover:text-white"
              >
                Cancel
              </button>
              <a
                href="/download"
                download="spasiba.apk"
                onClick={() => setShowConfirm(false)}
                className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-500 px-4 py-2 text-xs font-semibold text-black shadow-md shadow-emerald-500/20 transition hover:bg-emerald-400 active:scale-95"
              >
                <span>Download</span>
              </a>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

const AndroidDownloadButton = memo(AndroidDownloadButtonComponent);

export default AndroidDownloadButton;
