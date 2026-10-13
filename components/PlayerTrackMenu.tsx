"use client";

import { createPortal } from "react-dom";
import { memo, type MouseEvent as ReactMouseEvent, useCallback, useEffect, useRef, useState } from "react";

import SaveToPlaylistButton from "@/components/SaveToPlaylistButton";
import { audioController } from "@/lib/audio-controller";
import { emitProfileUpdatedEvent, PROFILE_UPDATED_EVENT } from "@/lib/profile-events";
import { readCachedProfile } from "@/lib/profile-client";
import { shareSong } from "@/lib/song-share";
import type { Song } from "@/lib/types";
import { playerActions } from "@/store/playerStore";

import TrackDetailsModal from "@/components/TrackDetailsModal";

type PlayerTrackMenuProps = {
  song: Song;
  className?: string;
  align?: "left" | "right";
  onClosePlayer?: () => void;
};

type MenuPosition = {
  top: number;
  left: number;
};

function getMenuPosition(rect: DOMRect, align: "left" | "right") {
  const viewportPadding = 16;
  const menuWidth = 240;
  const estimatedMenuHeight = 220;
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

function PlayerTrackMenuComponent({ song, className, align = "right", onClosePlayer }: PlayerTrackMenuProps) {
  const buttonRef = useRef<HTMLButtonElement | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);
  const [mounted, setMounted] = useState(false);
  const [open, setOpen] = useState(false);
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

    return () => {
      window.removeEventListener(PROFILE_UPDATED_EVENT, syncFromCache);
    };
  }, [song.id, song.videoId]);

  useEffect(() => {
    if (!open) {
      return;
    }

    if (readCachedProfile() !== undefined) {
      return;
    }

    let cancelled = false;
    setLoadingLikeState(true);

    void fetch(`/api/likes?videoId=${encodeURIComponent(song.videoId)}`, {
      method: "GET",
      credentials: "same-origin",
      headers: {
        Accept: "application/json"
      }
    })
      .then(async (response) => {
        const payload = (await response.json().catch(() => null)) as { liked?: boolean; error?: string } | null;

        if (!response.ok) {
          throw new Error(payload?.error || "Unable to load likes.");
        }

        return Boolean(payload?.liked);
      })
      .then((likedFromServer) => {
        if (cancelled) {
          return;
        }

        setLiked(likedFromServer);
      })
      .catch(() => {
        if (!cancelled) {
          setLiked(false);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingLikeState(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [open, song.id, song.videoId]);

  useEffect(() => {
    if (!open) {
      return;
    }

    const handlePointerDown = (event: globalThis.MouseEvent) => {
      const target = event.target as Node | null;

      if (buttonRef.current?.contains(target) || menuRef.current?.contains(target)) {
        return;
      }

      setOpen(false);
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
      }
    };

    const handleScroll = () => {
      if (buttonRef.current) {
        setPosition(getMenuPosition(buttonRef.current.getBoundingClientRect(), align));
      }
    };

    window.addEventListener("mousedown", handlePointerDown);
    window.addEventListener("keydown", handleKeyDown);
    window.addEventListener("resize", handleScroll);
    window.addEventListener("scroll", handleScroll, true);

    return () => {
      window.removeEventListener("mousedown", handlePointerDown);
      window.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("resize", handleScroll);
      window.removeEventListener("scroll", handleScroll, true);
    };
  }, [align, open]);

  const handleToggleMenu = useCallback((event: ReactMouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();

    if (!open && buttonRef.current) {
      setPosition(getMenuPosition(buttonRef.current.getBoundingClientRect(), align));
    }

    setOpen((previous) => !previous);
  }, [align, open]);

  const handleToggleLike = useCallback(async () => {
    setTogglingLike(true);

    try {
      if (liked) {
        const response = await fetch(`/api/likes?videoId=${encodeURIComponent(song.videoId)}`, {
          method: "DELETE",
          credentials: "same-origin",
          headers: {
            Accept: "application/json"
          }
        });
        const payload = (await response.json().catch(() => null)) as { error?: string } | null;

        if (!response.ok) {
          throw new Error(payload?.error || "Unable to update likes.");
        }

        setLiked(false);
        audioController.recordTasteAction(song, "unlike");
        playerActions.showToast(`Removed "${song.title}" from liked songs.`);
        emitProfileUpdatedEvent();
      } else {
        const response = await fetch("/api/likes", {
          method: "POST",
          credentials: "same-origin",
          headers: {
            "Content-Type": "application/json",
            Accept: "application/json"
          },
          body: JSON.stringify({ song })
        });
        const payload = (await response.json().catch(() => null)) as { error?: string } | null;

        if (!response.ok) {
          throw new Error(payload?.error || "Unable to like this song.");
        }

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
  }, [liked, song]);

  const handleClosePlayer = useCallback(() => {
    setOpen(false);
    audioController.closePlayer();
    onClosePlayer?.();
  }, [onClosePlayer]);

  const handleShareSong = useCallback(async () => {
    try {
      const result = await shareSong(song);

      if (result === "shared") {
        playerActions.showToast(`Shared "${song.title}".`);
      } else if (result === "copied") {
        playerActions.showToast(`Copied "${song.title}" link.`);
      }
    } catch (error) {
      playerActions.showToast(error instanceof Error ? error.message : "Unable to share this song.");
    } finally {
      setOpen(false);
    }
  }, [song]);

  const menu = mounted && open && position
    ? createPortal(
        <div
          ref={menuRef}
          className="fixed z-[125] w-[240px] rounded-[22px] border border-white/10 bg-[rgba(8,14,24,0.98)] p-2 shadow-[0_22px_80px_rgba(0,0,0,0.45)] backdrop-blur-xl"
          style={{ left: position.left, top: position.top }}
          onClick={(event) => event.stopPropagation()}
        >
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              void handleToggleLike();
            }}
            disabled={loadingLikeState || togglingLike}
            className="flex w-full items-center rounded-[16px] px-4 py-3 text-left text-sm text-white transition hover:bg-white/[0.08] disabled:cursor-wait disabled:opacity-70"
          >
            <span>{liked ? "Unlike" : "Like"}</span>
          </button>

          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              void handleShareSong();
            }}
            className="flex w-full items-center rounded-[16px] px-4 py-3 text-left text-sm text-white transition hover:bg-white/[0.08]"
          >
            <span>Share song</span>
          </button>

          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              setOpen(false);
              setPlaylistOpen(true);
            }}
            className="flex w-full items-center rounded-[16px] px-4 py-3 text-left text-sm text-white transition hover:bg-white/[0.08]"
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
            className="flex w-full items-center rounded-[16px] px-4 py-3 text-left text-sm text-white transition hover:bg-white/[0.08]"
          >
            <span>Details</span>
          </button>

          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              handleClosePlayer();
            }}
            className="flex w-full items-center rounded-[16px] px-4 py-3 text-left text-sm text-rose-100 transition hover:bg-white/[0.08]"
          >
            <span>Close player</span>
          </button>
        </div>,
        document.body
      )
    : null;

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        onClick={handleToggleMenu}
        className={className}
        aria-label="Track options"
      >
        <svg aria-hidden viewBox="0 0 24 24" className="h-5 w-5">
          <path fill="currentColor" d="M12 7a1.75 1.75 0 1 1 0-3.5A1.75 1.75 0 0 1 12 7m0 7a1.75 1.75 0 1 1 0-3.5A1.75 1.75 0 0 1 12 14m0 7a1.75 1.75 0 1 1 0-3.5A1.75 1.75 0 0 1 12 21" />
        </svg>
      </button>

      {menu}

      <SaveToPlaylistButton song={song} open={playlistOpen} onOpenChange={setPlaylistOpen} hideTrigger />
      <TrackDetailsModal song={song} open={detailsOpen} onOpenChange={setDetailsOpen} />
    </>
  );
}

const PlayerTrackMenu = memo(PlayerTrackMenuComponent);

export default PlayerTrackMenu;
