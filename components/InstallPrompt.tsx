"use client";

import { memo, useEffect, useState } from "react";
import { getDeviceInfo } from "@/lib/device";

const DISMISS_KEY = "spasiba_android_install_prompt_dismissed_v2";

function InstallPromptComponent() {
  const [visible, setVisible] = useState(false);
  const [passcode, setPasscode] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [isVerifying, setIsVerifying] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
    setPasscode("");
    setError(null);
    setIsVerifying(false);
    setShowPassword(false);
    try {
      window.localStorage.setItem(DISMISS_KEY, "1");
    } catch (_e) {}
  };

  const handleDownload = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const cleanPass = passcode.trim();
    if (!cleanPass) {
      setError("Please enter the passcode.");
      return;
    }

    setIsVerifying(true);
    setError(null);

    try {
      const res = await fetch("/api/download/verify", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ passcode: cleanPass }),
      });

      const data = await res.json().catch(() => ({}));

      if (!res.ok || !data.success) {
        setError(data?.error || "Incorrect passcode. Please try again.");
        setIsVerifying(false);
        return;
      }

      // Passcode verified! Trigger download & dismiss prompt
      const downloadTarget =
        data.downloadUrl || `/download?code=${encodeURIComponent(cleanPass)}`;
      dismiss();
      window.location.href = downloadTarget;
    } catch (_err) {
      setError("Failed to verify passcode. Please check your connection.");
      setIsVerifying(false);
    }
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
        className="w-full max-w-[340px] rounded-[28px] border border-white/[0.08] bg-[#1e222a] p-6 shadow-2xl shadow-black/90 text-left animate-in zoom-in-95 duration-200"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-500/15 text-emerald-400 mb-4">
          <svg className="h-6 w-6 fill-current" viewBox="0 0 24 24">
            <path d="M17.523 15.3414c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.551 0 .9993.4482.9993.9993.0001.5511-.4483.9997-.9993.9997m-11.046 0c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.5511 0 .9993.4482.9993.9993 0 .5511-.4482.9997-.9993.9997m11.4045-6.02l1.996-3.4572c.1056-.1828.043-.4167-.14-.5223-.1828-.1055-.4169-.043-.5224.1399l-2.0252 3.5078c-1.5794-.7224-3.3756-1.1245-5.3129-1.1245s-3.7335.4021-5.3129 1.1245L4.5369 5.4818c-.1055-.1829-.3396-.2454-.5224-.1399-.183.1056-.2456.3395-.14.5223l1.996 3.4572C2.6889 11.0858 0 14.8872 0 19.3418h24c0-4.4546-2.6889-8.256-5.877-10.0204" />
          </svg>
        </div>

        <h2 id="android-prompt-title" className="text-xl font-medium tracking-tight text-white">
          Download Spasiba
        </h2>

        <p className="mt-1.5 text-sm text-slate-300 font-medium">
          spasiba.apk <span className="text-slate-400 font-normal">(72.1 MB)</span>
        </p>

        <p className="mt-1 text-xs text-slate-400 leading-relaxed">
          Enter the passcode from the GitHub README to download Spasiba for Android.
        </p>

        <form onSubmit={handleDownload} className="mt-4">
          <label htmlFor="prompt-download-passcode" className="block text-xs font-medium text-slate-300 mb-1.5">
            Passcode
          </label>

          <div className="relative">
            <input
              id="prompt-download-passcode"
              type={showPassword ? "text" : "password"}
              value={passcode}
              onChange={(e) => {
                setPasscode(e.target.value);
                if (error) setError(null);
              }}
              placeholder="Enter passcode"
              disabled={isVerifying}
              autoFocus
              className={`w-full rounded-xl border bg-black/40 px-3.5 py-2.5 pr-10 text-sm text-white placeholder-slate-500 outline-none transition focus:ring-1 ${
                error
                  ? "border-rose-500/80 focus:border-rose-500 focus:ring-rose-500"
                  : "border-white/10 focus:border-emerald-400 focus:ring-emerald-400/50"
              }`}
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-200 p-1 transition"
              aria-label={showPassword ? "Hide passcode" : "Show passcode"}
            >
              {showPassword ? (
                <svg viewBox="0 0 24 24" className="h-4 w-4 fill-none stroke-current stroke-2">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
                  <line x1="1" y1="1" x2="23" y2="23" />
                </svg>
              ) : (
                <svg viewBox="0 0 24 24" className="h-4 w-4 fill-none stroke-current stroke-2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
              )}
            </button>
          </div>

          {error && (
            <p className="mt-1.5 text-xs text-rose-400 font-medium">
              {error}
            </p>
          )}

          <div className="mt-6 flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={dismiss}
              disabled={isVerifying}
              className="rounded-full px-4 py-2 text-sm font-medium text-emerald-400 transition hover:bg-emerald-500/10 active:bg-emerald-500/20 disabled:opacity-50"
            >
              Cancel
            </button>

            <button
              type="submit"
              disabled={isVerifying || !passcode.trim()}
              className="inline-flex items-center justify-center rounded-full bg-emerald-400 px-5 py-2 text-sm font-semibold text-slate-950 shadow-md shadow-emerald-500/20 transition hover:bg-emerald-300 active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isVerifying ? "Verifying..." : "Download"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

const InstallPrompt = memo(InstallPromptComponent);

export default InstallPrompt;
