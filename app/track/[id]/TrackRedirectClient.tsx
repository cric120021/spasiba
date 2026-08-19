"use client";

import { useEffect } from "react";

interface Props {
  deepLinkUrl: string;
}

export default function TrackRedirectClient({ deepLinkUrl }: Props) {
  useEffect(() => {
    if (typeof window !== "undefined") {
      const isAndroid = /android/i.test(navigator.userAgent);
      if (isAndroid) {
        // Attempt immediate zero-delay custom scheme jump
        window.location.href = deepLinkUrl;

        // Fallback after 600ms to intent URI if custom scheme was swallowed by in-app browser
        const timer = setTimeout(() => {
          try {
            const rawTrackPart = deepLinkUrl.replace(/^spasiba:\/\//, "");
            const intentUrl = `intent://${rawTrackPart}#Intent;scheme=spasiba;package=app.spasiba.nativeapp2.debug;end`;
            window.location.href = intentUrl;
          } catch (e) {
            // Ignore if blocked
          }
        }, 600);

        return () => clearTimeout(timer);
      }
    }
  }, [deepLinkUrl]);

  return null;
}
