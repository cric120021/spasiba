"use client";

import { createPortal } from "react-dom";
import { memo, useEffect, useState } from "react";
import ArtworkImage from "@/components/ArtworkImage";
import type { Song } from "@/lib/types";

type TrackDetailsModalProps = {
  song: Song;
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

function TrackDetailsModalComponent({ song, open, onOpenChange }: TrackDetailsModalProps) {
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (!open) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onOpenChange(false);
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [open, onOpenChange]);

  if (!mounted || !open) return null;

  return createPortal(
    <div
      className="fixed inset-0 z-[150] flex items-center justify-center bg-black/85 p-4 backdrop-blur-xl animate-in fade-in duration-200"
      onClick={() => onOpenChange(false)}
    >
      <div
        className="w-full max-w-md overflow-hidden rounded-[28px] border border-white/12 bg-[rgba(10,16,28,0.96)] p-6 shadow-2xl backdrop-blur-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-white/10 pb-4">
          <h3 className="text-base font-bold uppercase tracking-[0.2em] text-white">Track Details</h3>
          <button
            type="button"
            onClick={() => onOpenChange(false)}
            className="rounded-full border border-white/10 bg-white/[0.06] p-2 text-slate-300 transition hover:bg-white/15 hover:text-white"
            aria-label="Close track details"
          >
            <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4">
              <path fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M18 6L6 18M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="mt-5 space-y-4">
          <div className="relative mx-auto h-48 w-48 overflow-hidden rounded-2xl border border-white/10 bg-white/5 shadow-lg">
            <ArtworkImage
              src={song.thumbnail}
              alt={song.title}
              fill
              sizes="192px"
              quality={85}
              className="object-cover"
            />
          </div>

          <div className="text-center">
            <h4 className="line-clamp-2 text-lg font-bold text-white">{song.title}</h4>
            <p className="mt-1 text-sm font-medium uppercase tracking-[0.16em] text-slate-400">{song.author}</p>
          </div>

          <div className="space-y-2.5 rounded-2xl border border-white/8 bg-white/[0.03] p-4 text-xs font-mono text-slate-300">
            <div className="flex justify-between border-b border-white/5 pb-2">
              <span className="text-slate-400 uppercase tracking-wider">Duration</span>
              <span className="font-semibold text-white">{song.duration || "N/A"}</span>
            </div>
            <div className="flex justify-between border-b border-white/5 pb-2">
              <span className="text-slate-400 uppercase tracking-wider">Video ID</span>
              <span className="font-semibold text-white">{song.videoId}</span>
            </div>
            {song.views ? (
              <div className="flex justify-between border-b border-white/5 pb-2">
                <span className="text-slate-400 uppercase tracking-wider">Views</span>
                <span className="font-semibold text-white">{song.views.toLocaleString()}</span>
              </div>
            ) : null}
            <div className="flex justify-between pt-1">
              <span className="text-slate-400 uppercase tracking-wider">Source</span>
              <span className="font-semibold text-white">YouTube</span>
            </div>
          </div>

          <div className="pt-2">
            <button
              type="button"
              onClick={() => {
                if (song.url || song.videoId) {
                  window.open(song.url || `https://www.youtube.com/watch?v=${song.videoId}`, "_blank", "noopener,noreferrer");
                }
              }}
              className="w-full rounded-2xl border border-white/15 bg-white px-4 py-3 text-sm font-bold text-slate-950 transition hover:bg-slate-200"
            >
              Open on YouTube
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
}

const TrackDetailsModal = memo(TrackDetailsModalComponent);
export default TrackDetailsModal;
