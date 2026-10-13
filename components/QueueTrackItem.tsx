"use client";

import { memo, useCallback, useRef, useState, type PointerEvent, type TouchEvent } from "react";
import ArtworkImage from "@/components/ArtworkImage";
import TrackActionsMenu from "@/components/TrackActionsMenu";
import type { Song } from "@/lib/types";

type QueueTrackItemProps = {
  track: Song;
  index: number;
  totalItems: number;
  isDragging?: boolean;
  isDropTarget?: boolean;
  onPlayNow: () => void;
  onRemove: () => void;
  onMove: (direction: -1 | 1) => void;
  onDragStart?: () => void;
  onDragEnd?: () => void;
  onDragOver?: (event: React.DragEvent) => void;
  onDrop?: (event: React.DragEvent) => void;
};

const SWIPE_REMOVE_THRESHOLD = -70;
const LONG_PRESS_MS = 450;

function QueueTrackItemComponent({
  track,
  index,
  totalItems,
  isDropTarget,
  onPlayNow,
  onRemove,
  onMove,
  onDragStart,
  onDragEnd,
  onDragOver,
  onDrop
}: QueueTrackItemProps) {
  const [swipeOffset, setSwipeOffset] = useState(0);
  const [isSwiping, setIsSwiping] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [anchorPos, setAnchorPos] = useState<{ top: number; left: number } | null>(null);

  const touchStartRef = useRef<{ x: number; y: number } | null>(null);
  const longPressTimerRef = useRef<number | null>(null);

  const clearLongPressTimer = useCallback(() => {
    if (longPressTimerRef.current !== null) {
      window.clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
  }, []);

  const handleTouchStart = useCallback((event: TouchEvent<HTMLDivElement>) => {
    if (event.touches.length !== 1) return;
    const touch = event.touches[0];
    touchStartRef.current = { x: touch.clientX, y: touch.clientY };

    clearLongPressTimer();
    longPressTimerRef.current = window.setTimeout(() => {
      setAnchorPos({ top: touch.clientY, left: Math.min(touch.clientX, window.innerWidth - 250) });
      setMenuOpen(true);
    }, LONG_PRESS_MS);
  }, [clearLongPressTimer]);

  const handleTouchMove = useCallback((event: TouchEvent<HTMLDivElement>) => {
    if (!touchStartRef.current || event.touches.length !== 1) return;
    const touch = event.touches[0];
    const deltaX = touch.clientX - touchStartRef.current.x;
    const deltaY = touch.clientY - touchStartRef.current.y;

    if (Math.abs(deltaY) > Math.abs(deltaX) + 5) {
      clearLongPressTimer();
      setSwipeOffset(0);
      setIsSwiping(false);
      return;
    }

    if (deltaX < -5) {
      clearLongPressTimer();
      setIsSwiping(true);
      setSwipeOffset(Math.max(-120, deltaX));
    }
  }, [clearLongPressTimer]);

  const handleTouchEnd = useCallback(() => {
    clearLongPressTimer();
    touchStartRef.current = null;

    if (swipeOffset <= SWIPE_REMOVE_THRESHOLD) {
      onRemove();
    }
    setSwipeOffset(0);
    setIsSwiping(false);
  }, [clearLongPressTimer, onRemove, swipeOffset]);

  const handlePointerDown = useCallback((event: PointerEvent<HTMLDivElement>) => {
    if (event.pointerType === "touch") return;
    clearLongPressTimer();
    longPressTimerRef.current = window.setTimeout(() => {
      setAnchorPos({ top: event.clientY, left: Math.min(event.clientX, window.innerWidth - 250) });
      setMenuOpen(true);
    }, LONG_PRESS_MS);
  }, [clearLongPressTimer]);

  const handlePointerUp = useCallback(() => {
    clearLongPressTimer();
  }, [clearLongPressTimer]);

  const handleContextMenu = useCallback((event: React.MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    setAnchorPos({ top: event.clientY, left: Math.min(event.clientX, window.innerWidth - 250) });
    setMenuOpen(true);
  }, []);

  return (
    <div className="relative overflow-hidden rounded-[18px]">
      <div className="absolute inset-0 flex items-center justify-end rounded-[18px] bg-rose-600 px-4 text-xs font-bold uppercase tracking-widest text-white">
        Remove
      </div>

      <div
        role="button"
        tabIndex={0}
        onClick={(event) => {
          if (swipeOffset !== 0) {
            setSwipeOffset(0);
            return;
          }
          if (!menuOpen) {
            onPlayNow();
          }
        }}
        onContextMenu={handleContextMenu}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
        onTouchCancel={handleTouchEnd}
        onPointerDown={handlePointerDown}
        onPointerUp={handlePointerUp}
        onPointerLeave={handlePointerUp}
        onDragOver={onDragOver}
        onDrop={onDrop}
        className={`relative flex w-full items-center gap-3 rounded-[18px] border px-3 py-3 text-left transition-transform ${
          isDropTarget
            ? "border-white bg-white text-slate-950 font-semibold"
            : "border-white/8 bg-white/[0.03] hover:bg-white/[0.08]"
        }`}
        style={{
          transform: `translate3d(${swipeOffset}px, 0, 0)`,
          transition: isSwiping ? "none" : "transform 200ms cubic-bezier(0.2, 0.8, 0.2, 1)"
        }}
      >
        <button
          type="button"
          draggable
          onClick={(event) => event.stopPropagation()}
          onDragStart={onDragStart}
          onDragEnd={onDragEnd}
          className="shrink-0 cursor-grab rounded-full border border-white/10 bg-white/[0.04] p-2 text-slate-300 active:cursor-grabbing"
          aria-label={`Reorder ${track.title}`}
          title="Drag to reorder"
        >
          <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4">
            <path
              fill="currentColor"
              d="M9 6a1.5 1.5 0 1 1 0-3a1.5 1.5 0 0 1 0 3m0 7a1.5 1.5 0 1 1 0-3a1.5 1.5 0 0 1 0 3m0 7a1.5 1.5 0 1 1 0-3A1.5 1.5 0 0 1 9 20m6-14a1.5 1.5 0 1 1 0-3a1.5 1.5 0 0 1 0 3m0 7a1.5 1.5 0 1 1 0-3a1.5 1.5 0 0 1 0 3m0 7a1.5 1.5 0 1 1 0-3a1.5 1.5 0 0 1 0 3"
            />
          </svg>
        </button>

        <div className="relative h-12 w-12 shrink-0 overflow-hidden rounded-[16px] bg-white/[0.05]">
          <ArtworkImage
            src={track.thumbnail}
            alt={track.title}
            fill
            sizes="48px"
            quality={70}
            className="object-cover"
          />
        </div>

        <div className="min-w-0 flex-1">
          <p className={`line-clamp-1 text-[13px] font-medium sm:text-sm ${isDropTarget ? "text-slate-950" : "text-white"}`}>
            {track.title}
          </p>
          <p className={`mt-1 line-clamp-1 text-xs ${isDropTarget ? "text-slate-700" : "text-slate-400"}`}>
            {track.author}
          </p>
        </div>

        <span className={`shrink-0 text-[10px] uppercase tracking-[0.18em] sm:text-xs sm:tracking-[0.22em] ${isDropTarget ? "text-slate-800" : "text-slate-300"}`}>
          {track.duration}
        </span>

        <div className="flex shrink-0 items-center gap-1.5" onClick={(event) => event.stopPropagation()}>
          <button
            type="button"
            onClick={() => onMove(-1)}
            disabled={index === 0}
            className="flex h-9 w-9 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] text-slate-200 transition enabled:hover:bg-white/[0.1] disabled:cursor-not-allowed disabled:opacity-35"
            aria-label={`Move ${track.title} up in queue`}
            title="Move up"
          >
            <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4">
              <path fill="currentColor" d="m12 7.2 5.6 5.6-1.4 1.4L12 10l-4.2 4.2-1.4-1.4z" />
            </svg>
          </button>

          <button
            type="button"
            onClick={() => onMove(1)}
            disabled={index === totalItems - 1}
            className="flex h-9 w-9 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] text-slate-200 transition enabled:hover:bg-white/[0.1] disabled:cursor-not-allowed disabled:opacity-35"
            aria-label={`Move ${track.title} down in queue`}
            title="Move down"
          >
            <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4">
              <path fill="currentColor" d="m7.8 9.8 1.4-1.4L12 11.2l2.8-2.8 1.4 1.4-4.2 4.2z" />
            </svg>
          </button>

          <TrackActionsMenu
            song={track}
            onPlayNow={onPlayNow}
            onRemoveFromQueue={onRemove}
            open={menuOpen}
            onOpenChange={setMenuOpen}
            anchorPosition={anchorPos}
            className="flex h-9 w-9 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] text-slate-200 transition hover:bg-white/[0.1]"
          />
        </div>
      </div>
    </div>
  );
}

const QueueTrackItem = memo(QueueTrackItemComponent);
export default QueueTrackItem;
