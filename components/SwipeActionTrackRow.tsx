"use client";

import { memo, type ReactNode, useCallback, useRef, useState } from "react";

import ArtworkImage from "@/components/ArtworkImage";
import type { Song } from "@/lib/types";

type SwipeActionTrackRowProps = {
  song: Song;
  title?: string;
  subtitle?: string;
  trailingText?: string;
  leadingSlot?: ReactNode;
  actionLabel: string;
  actionAriaLabel?: string;
  actionBusyLabel?: string;
  onPlay: () => void;
  onAction: () => Promise<void> | void;
};

const MAX_SWIPE_PX = 108;
const REVEAL_THRESHOLD_PX = 42;
const ACTION_THRESHOLD_PX = 86;

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function SwipeActionTrackRowComponent({
  song,
  title,
  subtitle,
  trailingText,
  leadingSlot,
  actionLabel,
  actionAriaLabel,
  actionBusyLabel,
  onPlay,
  onAction
}: SwipeActionTrackRowProps) {
  const gestureRef = useRef<{
    startX: number;
    startY: number;
    swiping: boolean;
    aborted: boolean;
  } | null>(null);
  const [offset, setOffset] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [busy, setBusy] = useState(false);

  const resetSwipe = useCallback(() => {
    setDragging(false);
    setOffset(0);
    gestureRef.current = null;
  }, []);

  const runAction = useCallback(async () => {
    if (busy) {
      return;
    }

    setBusy(true);

    try {
      await onAction();
    } finally {
      setBusy(false);
      setDragging(false);
      setOffset(0);
      gestureRef.current = null;
    }
  }, [busy, onAction]);

  const handleTouchStart = useCallback((event: React.TouchEvent<HTMLButtonElement>) => {
    if (busy || event.touches.length !== 1) {
      return;
    }

    const touch = event.touches[0];
    gestureRef.current = {
      startX: touch.clientX,
      startY: touch.clientY,
      swiping: false,
      aborted: false
    };
  }, [busy]);

  const handleTouchMove = useCallback((event: React.TouchEvent<HTMLButtonElement>) => {
    const gesture = gestureRef.current;

    if (!gesture || busy || event.touches.length !== 1) {
      return;
    }

    const touch = event.touches[0];
    const deltaX = touch.clientX - gesture.startX;
    const deltaY = touch.clientY - gesture.startY;

    if (gesture.aborted) {
      return;
    }

    if (!gesture.swiping) {
      if (Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > 8) {
        gesture.aborted = true;
        return;
      }

      if (deltaX < -8 && Math.abs(deltaX) > Math.abs(deltaY)) {
        gesture.swiping = true;
        setDragging(true);
      } else {
        return;
      }
    }

    event.preventDefault();
    setOffset(clamp(deltaX, -MAX_SWIPE_PX, 0));
  }, [busy]);

  const handleTouchEnd = useCallback(() => {
    const gesture = gestureRef.current;

    if (!gesture) {
      return;
    }

    gestureRef.current = null;

    if (!gesture.swiping) {
      setDragging(false);
      return;
    }

    setDragging(false);

    if (offset <= -ACTION_THRESHOLD_PX) {
      void runAction();
      return;
    }

    if (offset <= -REVEAL_THRESHOLD_PX) {
      setOffset(-MAX_SWIPE_PX);
      return;
    }

    setOffset(0);
  }, [offset, runAction]);

  const handleTouchCancel = useCallback(() => {
    resetSwipe();
  }, [resetSwipe]);

  const handlePlay = useCallback(() => {
    if (busy) {
      return;
    }

    if (offset < -8) {
      setOffset(0);
      return;
    }

    onPlay();
  }, [busy, offset, onPlay]);

  return (
    <div className="relative overflow-hidden rounded-[20px]">
      <div className="absolute inset-0 flex items-stretch justify-end rounded-[20px] bg-gradient-to-l from-rose-500/30 via-rose-400/16 to-transparent">
        <button
          type="button"
          onClick={() => void runAction()}
          disabled={busy}
          className="flex min-w-[108px] items-center justify-center rounded-[20px] bg-rose-500/88 px-4 text-[11px] font-semibold uppercase tracking-[0.2em] text-white transition hover:bg-rose-400 disabled:cursor-wait disabled:opacity-70"
          aria-label={actionAriaLabel || actionLabel}
        >
          {busy ? actionBusyLabel || `${actionLabel}...` : actionLabel}
        </button>
      </div>

      <button
        type="button"
        onClick={handlePlay}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
        onTouchCancel={handleTouchCancel}
        className="glass-panel relative flex w-full items-center gap-3 rounded-[20px] p-2.5 text-left transition hover:border-teal-300/20 hover:bg-white/[0.08] sm:p-3"
        style={{
          transform: `translate3d(${offset}px, 0, 0)`,
          transition: dragging ? "none" : "transform 180ms ease"
        }}
      >
        {leadingSlot ? (
          <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-white/[0.06] text-[11px] font-semibold text-slate-300">
            {leadingSlot}
          </div>
        ) : null}

        <div className="relative h-14 w-14 shrink-0 overflow-hidden rounded-[16px] border border-white/10 bg-white/[0.04]">
          <ArtworkImage src={song.thumbnail} alt={title || song.title} fill sizes="56px" quality={75} className="object-cover" />
        </div>

        <div className="min-w-0 flex-1">
          <p className="line-clamp-1 text-[14px] font-medium text-white sm:text-[15px]">{title || song.title}</p>
          <p className="mt-1 line-clamp-1 text-[11px] uppercase tracking-[0.16em] text-slate-400">
            {subtitle || song.author}
          </p>
        </div>

        <span className="shrink-0 text-[11px] uppercase tracking-[0.18em] text-slate-300">{trailingText || song.duration}</span>
      </button>
    </div>
  );
}

const SwipeActionTrackRow = memo(SwipeActionTrackRowComponent);

export default SwipeActionTrackRow;
