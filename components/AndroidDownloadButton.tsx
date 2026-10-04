"use client";

import { memo, useEffect, useState } from "react";
import { getDeviceInfo } from "@/lib/device";

function AndroidDownloadButtonComponent() {
  const [showAndroidButton, setShowAndroidButton] = useState(false);

  useEffect(() => {
    const info = getDeviceInfo();
    // Strictly ONLY on Android systems. Never show on iOS.
    if (info.isAndroid && !info.isIOS) {
      setShowAndroidButton(true);
    }
  }, []);

  if (!showAndroidButton) {
    return null;
  }

  return (
    <a
      href="/spasiba.apk"
      download="spasiba.apk"
      className="inline-flex items-center gap-1.5 rounded-full border border-emerald-400/30 bg-emerald-500/15 px-3 py-1.5 text-xs font-bold text-emerald-300 shadow-sm backdrop-blur-sm transition hover:border-emerald-400/50 hover:bg-emerald-500/25 active:scale-95"
      title="Single-click download Spasiba for Android"
    >
      <svg className="h-3.5 w-3.5 fill-current text-emerald-400" viewBox="0 0 24 24">
        <path d="M17.523 15.3414c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.551 0 .9993.4482.9993.9993.0001.5511-.4483.9997-.9993.9997m-11.046 0c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.5511 0 .9993.4482.9993.9993 0 .5511-.4482.9997-.9993.9997m11.4045-6.02l1.996-3.4572c.1056-.1828.043-.4167-.14-.5223-.1828-.1055-.4169-.043-.5224.1399l-2.0252 3.5078c-1.5794-.7224-3.3756-1.1245-5.3129-1.1245s-3.7335.4021-5.3129 1.1245L4.5369 5.4818c-.1055-.1829-.3396-.2454-.5224-.1399-.183.1056-.2456.3395-.14.5223l1.996 3.4572C2.6889 11.0858 0 14.8872 0 19.3418h24c0-4.4546-2.6889-8.256-5.877-10.0204" />
      </svg>
      <span>Download APK</span>
    </a>
  );
}

const AndroidDownloadButton = memo(AndroidDownloadButtonComponent);

export default AndroidDownloadButton;
