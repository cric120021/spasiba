"use client";

import Image from "next/image";
import { memo, useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

import { audioController } from "@/lib/audio-controller";
import type { Song } from "@/lib/types";
import { formatCompactCount } from "@/lib/utils";

import SaveToPlaylistButton from "@/components/SaveToPlaylistButton";

type SongCardProps = {
  song: Song;
  isActive: boolean;
  isPlaying: boolean;
  onPlay: (song: Song) => void;
  onHover: (song: Song) => void;
};

function SongCardComponent({ song, isActive, isPlaying, onPlay, onHover }: SongCardProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [playlistModalOpen, setPlaylistModalOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement | null>(null);
  const menuButtonRef = useRef<HTMLButtonElement | null>(null);
  const [mounted, setMounted] = useState(false);
  const [menuPosition, setMenuPosition] = useState<{ top: number; left: number } | null>(null);

  useEffect(() => {
    setMounted(true);
  }, []);

  const updateMenuPosition = useCallback(() => {
    const button = menuButtonRef.current;

    if (!button) {
      return;
    }

    const rect = button.getBoundingClientRect();
    const menuWidth = 224;
    const viewportPadding = 16;
    const nextLeft = Math.min(
      Math.max(rect.right - menuWidth, viewportPadding),
      window.innerWidth - menuWidth - viewportPadding
    );

    setMenuPosition({
      top: rect.bottom + 12,
      left: nextLeft
    });
  }, []);

  useEffect(() => {
    if (!menuOpen) {
      return;
    }

    updateMenuPosition();

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node;

      if (!menuRef.current?.contains(target) && !menuButtonRef.current?.contains(target)) {
        setMenuOpen(false);
      }
    };

    const handleViewportChange = () => {
      updateMenuPosition();
    };

    window.addEventListener("pointerdown", handlePointerDown);
    window.addEventListener("resize", handleViewportChange);
    window.addEventListener("scroll", handleViewportChange, true);

    return () => {
      window.removeEventListener("pointerdown", handlePointerDown);
      window.removeEventListener("resize", handleViewportChange);
      window.removeEventListener("scroll", handleViewportChange, true);
    };
  }, [menuOpen, updateMenuPosition]);

  return (
    <article
      className="group glass-panel relative rounded-[22px] transition duration-300 hover:-translate-y-1 hover:border-teal-300/25 hover:bg-white/10 sm:rounded-[28px]"
      onPointerEnter={() => onHover(song)}
      onPointerDown={() => onHover(song)}
      onTouchStart={() => onHover(song)}
      onFocusCapture={() => onHover(song)}
    >
      <div className="flex w-full flex-col">
        <div className="relative aspect-square overflow-hidden rounded-t-[22px] sm:rounded-t-[28px]">
          <Image
            src={song.thumbnail}
            alt={song.title}
            fill
            sizes="(max-width: 640px) 44vw, (max-width: 1024px) 200px, 220px"
            quality={70}
            className="object-cover transition duration-500 group-hover:scale-105"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/5 to-transparent opacity-80" />
          <button type="button" onClick={() => onPlay(song)} className="absolute inset-0 z-[1]" aria-label={`Play ${song.title}`} />
          <div className="absolute left-3 right-3 top-3 z-[2] flex items-center justify-between sm:left-4 sm:right-4 sm:top-4">
            <span className="rounded-full border border-white/10 bg-slate-950/60 px-2.5 py-1 text-[9px] uppercase tracking-[0.22em] text-white/75 backdrop-blur-md sm:px-3 sm:text-[10px] sm:tracking-[0.28em]">
              {song.duration}
            </span>
          </div>
          <div className="absolute right-3 top-3 z-10 sm:right-4 sm:top-4">
            <button
              ref={menuButtonRef}
              type="button"
              onClick={(event) => {
                event.preventDefault();
                event.stopPropagation();
                setMenuOpen((previous) => !previous);
              }}
              className="flex h-9 w-9 items-center justify-center rounded-full border border-white/10 bg-slate-950/70 text-white/90 backdrop-blur-md transition hover:bg-slate-950/90 sm:h-10 sm:w-10"
              aria-label="More actions"
            >
              <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4 sm:h-5 sm:w-5">
                <path fill="currentColor" d="M12 7a2 2 0 1 0 0-4a2 2 0 0 0 0 4m0 7a2 2 0 1 0 0-4a2 2 0 0 0 0 4m0 7a2 2 0 1 0 0-4a2 2 0 0 0 0 4" />
              </svg>
            </button>
          </div>
          <button
            type="button"
            onClick={(event) => {
              event.preventDefault();
              event.stopPropagation();
              onPlay(song);
            }}
            className="absolute bottom-3 right-3 z-[3] flex h-11 w-11 items-center justify-center rounded-full bg-white text-slate-950 shadow-xl transition duration-300 group-hover:scale-105 group-hover:bg-teal-300 sm:bottom-4 sm:right-4 sm:h-12 sm:w-12"
            aria-label={isActive && isPlaying ? `Pause ${song.title}` : `Play ${song.title}`}
          >
            <svg aria-hidden viewBox="0 0 24 24" className="h-5 w-5 sm:h-6 sm:w-6">
              {isActive && isPlaying ? (
                <path fill="currentColor" d="M7 5h3v14H7zm7 0h3v14h-3z" />
              ) : (
                <path fill="currentColor" d="M8 5.14v13.72a1 1 0 0 0 1.53.848l10.08-6.86a1 1 0 0 0 0-1.696L9.53 4.292A1 1 0 0 0 8 5.14" />
              )}
            </svg>
          </button>
          {isActive ? (
            <div className="absolute bottom-3 left-3 z-[2] rounded-full border border-teal-300/35 bg-teal-300/[0.18] px-2.5 py-1 text-[9px] uppercase tracking-[0.22em] text-teal-100 sm:bottom-4 sm:left-4 sm:px-3 sm:text-[10px] sm:tracking-[0.28em]">
              {isPlaying ? "Playing" : "Selected"}
            </div>
          ) : null}
        </div>

        <button type="button" onClick={() => onPlay(song)} className="flex flex-1 flex-col gap-1.5 p-3 text-left sm:gap-2 sm:p-4">
          <h3 className="line-clamp-2 text-[14px] font-semibold tracking-tight text-white sm:text-base">{song.title}</h3>
          <div className="mt-auto flex items-center justify-between gap-2 text-[12px] text-slate-300/80 sm:gap-3 sm:text-sm">
            <span className="line-clamp-1">{song.author}</span>
            <span className="shrink-0 text-[10px] uppercase tracking-[0.18em] text-slate-400 sm:text-xs sm:tracking-[0.22em]">{formatCompactCount(song.views)}</span>
          </div>
        </button>
      </div>

      {mounted && menuOpen && menuPosition
        ? createPortal(
            <div
              ref={menuRef}
              className="fixed z-[130] w-56 rounded-[22px] border border-white/10 bg-[rgba(8,14,24,0.98)] p-2 shadow-[0_24px_70px_rgba(0,0,0,0.55)] backdrop-blur-xl"
              style={{
                top: `${menuPosition.top}px`,
                left: `${menuPosition.left}px`
              }}
            >
              <button
                type="button"
                onClick={(event) => {
                  event.preventDefault();
                  event.stopPropagation();
                  setMenuOpen(false);
                  setPlaylistModalOpen(true);
                }}
                className="flex w-full items-center justify-between rounded-2xl px-4 py-3 text-sm font-medium text-slate-100 transition hover:bg-white/[0.08]"
              >
                <span>Add to playlist</span>
              </button>
              <button
                type="button"
                onClick={(event) => {
                  event.preventDefault();
                  event.stopPropagation();
                  setMenuOpen(false);
                  audioController.addToQueue(song);
                }}
                className="flex w-full items-center rounded-2xl px-4 py-3 text-left text-sm font-medium text-slate-100 transition hover:bg-white/[0.08]"
              >
                <span className="whitespace-nowrap">Add next in queue</span>
              </button>
            </div>,
            document.body
          )
        : null}
      <SaveToPlaylistButton song={song} open={playlistModalOpen} onOpenChange={setPlaylistModalOpen} hideTrigger />
    </article>
  );
}

const SongCard = memo(SongCardComponent);

export default SongCard;
