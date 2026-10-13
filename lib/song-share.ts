"use client";

import type { Song } from "@/lib/types";

type ShareSongResult = "shared" | "copied" | "cancelled";

function buildShareQuery(song: Song) {
  return `${song.title} ${song.author}`.replace(/\s+/g, " ").trim();
}

export function buildSongShareUrl(song: Song, origin?: string) {
  const baseOrigin =
    origin || (typeof window !== "undefined" ? window.location.origin : "");
  const params = new URLSearchParams({
    q: buildShareQuery(song),
    v: song.videoId
  });

  return `${baseOrigin}/search?${params.toString()}`;
}

export async function shareSong(song: Song): Promise<ShareSongResult> {
  const url = buildSongShareUrl(song);
  const shareData = {
    title: `${song.title} • ${song.author}`,
    text: `Listen to ${song.title} on Spasiba.`,
    url
  };

  if (typeof navigator !== "undefined" && "share" in navigator) {
    try {
      await navigator.share(shareData);
      return "shared";
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        return "cancelled";
      }
    }
  }

  if (typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(url);
    return "copied";
  }

  throw new Error("Sharing is not available on this device.");
}
