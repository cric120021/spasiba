"use client";

import { createPortal } from "react-dom";
import { memo, useCallback, useEffect, useRef, useState, type MouseEvent as ReactMouseEvent } from "react";

import SaveToPlaylistButton from "@/components/SaveToPlaylistButton";
import TrackDetailsModal from "@/components/TrackDetailsModal";
import { audioController } from "@/lib/audio-controller";
import { emitProfileUpdatedEvent, PROFILE_UPDATED_EVENT } from "@/lib/profile-events";
import { readCachedProfile } from "@/lib/profile-client";
import { shareSong } from "@/lib/song-share";
import type { Song } from "@/lib/types";
import { playerActions } from "@/store/playerStore";

type TrackActionsMenuProps = {
  song: Song;
  className?: string;
  align?: "left" | "right";
  onPlayNow?: () => void;
  onRemoveFromQueue?: () => void;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  anchorPosition?: { top: number; left: number } | null;
};

type MenuPosition = {
  top: number;
  left: number;
};

function getMenuPosition(rect: DOMRect, align: "left" | "right") {
  const viewportPadding = 16;
  const menuWidth = 240;
  const estimatedMenuHeight = 260;
  const preferredLeft = align === "left" ? rect.left : rect.right - menuWidth;
  const left = Math.min(Math.max(viewportPadding, preferredLeft), window.innerWidth - menuWidth - viewportPadding);
  const belowTop = rect.bottom + 10;

  if (belowTop + estimatedMenuHeight <= window.innerHeight - viewportPadding) {
    return {
      left,
      top: belowTop
    };
  }

  return {
    left,
    top: Math.max(viewportPadding, rect.top - estimatedMenuHeight - 10)
  };
}

function TrackActionsMenuComponent({
  song,
  className,
  align = "right",
  onPlayNow,
  onRemoveFromQueue,
  open: controlledOpen,
  onOpenChange,
  anchorPosition
}: TrackActionsMenuProps) {
  const buttonRef = useRef<HTMLButtonElement | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);
  const [mounted, setMounted] = useState(false);
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false);
  const isControlled = typeof controlledOpen === "boolean";
  const open = isControlled ? controlledOpen : uncontrolledOpen;

  const setOpen = useCallback(
    (nextState: boolean) => {
      if (!isControlled) {
        setUncontrolledOpen(nextState);
      }
      onOpenChange?.(nextState);
    },
    [isControlled, onOpenChange]
  );

  const [playlistOpen, setPlaylistOpen] = useState(false);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [position, setPosition] = useState<MenuPosition | null>(null);
  const [liked, setLiked] = useState(() => Boolean(readCachedProfile()?.likedSongs.some((track) => track.videoId === song.videoId)));
  const [loadingLikeState, setLoadingLikeState] = useState(() => readCachedProfile() === undefined);
  const [togglingLike, setTogglingLike] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    const syncFromCache = () => {
      const cachedProfile = readCachedProfile();
      setLiked(Boolean(cachedProfile?.likedSongs.some((track) => track.videoId === song.videoId)));
      setLoadingLikeState(cachedProfile === undefined);
    };

    syncFromCache();
    window.addEventListener(PROFILE_UPDATED_EVENT, syncFromCache);
    return () => window.removeEventListener(PROFILE_UPDATED_EVENT, syncFromCache);
  }, [song.id, song.videoId]);

  useEffect(() => {
    if (!open) return;

    if (anchorPosition) {
      setPosition(anchorPosition);
    } else if (buttonRef.current) {
      setPosition(getMenuPosition(buttonRef.current.getBoundingClientRect(), align));
    } else {
      setPosition({ top: window.innerHeight / 3, left: Math.max(16, (window.innerWidth - 240) / 2) });
    }
  }, [anchorPosition, align, open]);

  useEffect(() => {
    if (!open) return;

    const handlePointerDown = (event: globalThis.MouseEvent) => {
      const target = event.target as Node | null;
      if (buttonRef.current?.contains(target) || menuRef.current?.contains(target)) return;
      setOpen(false);
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };

    window.addEventListener("mousedown", handlePointerDown);
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.removeEventListener("mousedown", handlePointerDown);
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [open, setOpen]);

  const handleToggleMenu = useCallback(
    (event: ReactMouseEvent<HTMLButtonElement>) => {
      event.preventDefault();
      event.stopPropagation();
      setOpen(!open);
    },
    [open, setOpen]
  );

  const handleToggleLike = useCallback(async () => {
    setTogglingLike(true);
    try {
      if (liked) {
        const response = await fetch(`/api/likes?videoId=${encodeURIComponent(song.videoId)}`, {
          method: "DELETE",
          credentials: "same-origin"
        });
        if (!response.ok) throw new Error("Unable to update likes.");
        setLiked(false);
        audioController.recordTasteAction(song, "unlike");
        playerActions.showToast(`Removed "${song.title}" from liked songs.`);
        emitProfileUpdatedEvent();
      } else {
        const response = await fetch("/api/likes", {
          method: "POST",
          credentials: "same-origin",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ song })
        });
        if (!response.ok) throw new Error("Unable to like this song.");
        setLiked(true);
        audioController.recordTasteAction(song, "like");
        playerActions.showToast(`Liked "${song.title}".`);
        emitProfileUpdatedEvent();
      }
      setOpen(false);
    } catch (error) {
      playerActions.showToast(error instanceof Error ? error.message : "Unable to update likes.");
    } finally {
      setTogglingLike(false);
    }
  }, [liked, setOpen, song]);

  const handleShareSong = useCallback(async () => {
    try {
      const result = await shareSong(song);
      if (result === "shared") playerActions.showToast(`Shared "${song.title}".`);
      else if (result === "copied") playerActions.showToast(`Copied "${song.title}" link.`);
    } catch (error) {
      playerActions.showToast(error instanceof Error ? error.message : "Unable to share this song.");
    } finally {
      setOpen(false);
    }
  }, [setOpen, song]);

  const menu = mounted && open && position
    ? createPortal(
        <div
          ref={menuRef}
          className="fixed z-[130] w-[240px] rounded-[24px] border border-white/12 bg-[rgba(8,14,24,0.98)] p-2 shadow-[0_24px_80px_rgba(0,0,0,0.6)] backdrop-blur-2xl animate-in fade-in zoom-in-95 duration-150"
          style={{ top: position.top, left: position.left }}
          onClick={(event) => event.stopPropagation()}
        >
          {onPlayNow ? (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                setOpen(false);
                onPlayNow();
              }}
              className="flex w-full items-center rounded-2xl px-4 py-3 text-left text-sm font-medium text-white transition hover:bg-white/[0.08]"
            >
              <span>Play Now</span>
            </button>
          ) : null}

          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              void handleToggleLike();
            }}
            disabled={loadingLikeState || togglingLike}
            className="flex w-full items-center rounded-2xl px-4 py-3 text-left text-sm font-medium text-white transition hover:bg-white/[0.08] disabled:cursor-wait disabled:opacity-70"
          >
            <span>{liked ? "Unlike" : "Like"}</span>
          </button>

          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              setOpen(false);
              setPlaylistOpen(true);
            }}
            className="flex w-full items-center rounded-2xl px-4 py-3 text-left text-sm font-medium text-white transition hover:bg-white/[0.08]"
          >
            <span>Add to playlist</span>
          </button>

          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              setOpen(false);
              setDetailsOpen(true);
            }}
            className="flex w-full items-center rounded-2xl px-4 py-3 text-left text-sm font-medium text-white transition hover:bg-white/[0.08]"
          >
            <span>Details</span>
          </button>

          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              setOpen(false);
              audioController.addToQueue(song);
              playerActions.showToast(`Added "${song.title}" next in queue.`);
            }}
            className="flex w-full items-center rounded-2xl px-4 py-3 text-left text-sm font-medium text-white transition hover:bg-white/[0.08]"
          >
            <span>Add next in queue</span>
          </button>

          {onRemoveFromQueue ? (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                setOpen(false);
                onRemoveFromQueue();
              }}
              className="flex w-full items-center rounded-2xl px-4 py-3 text-left text-sm font-medium text-rose-300 transition hover:bg-white/[0.08]"
            >
              <span>Remove from queue</span>
            </button>
          ) : null}

          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              void handleShareSong();
            }}
            className="flex w-full items-center rounded-2xl px-4 py-3 text-left text-sm font-medium text-white transition hover:bg-white/[0.08]"
          >
            <span>Share song</span>
          </button>
        </div>,
        document.body
      )
    : null;

  return (
    <>
      {className ? (
        <button
          ref={buttonRef}
          type="button"
          onClick={handleToggleMenu}
          className={className}
          aria-label="Track options"
        >
          <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4">
            <path fill="currentColor" d="M12 7a2 2 0 1 0 0-4a2 2 0 0 0 0 4m0 7a2 2 0 1 0 0-4a2 2 0 0 0 0 4m0 7a2 2 0 1 0 0-4a2 2 0 0 0 0 4" />
          </svg>
        </button>
      ) : null}

      {menu}

      <SaveToPlaylistButton song={song} open={playlistOpen} onOpenChange={setPlaylistOpen} hideTrigger />
      <TrackDetailsModal song={song} open={detailsOpen} onOpenChange={setDetailsOpen} />
    </>
  );
}

const TrackActionsMenu = memo(TrackActionsMenuComponent);
export default TrackActionsMenu;
