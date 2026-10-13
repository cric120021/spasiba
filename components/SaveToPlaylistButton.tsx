"use client";

import { memo, useCallback, useEffect, useMemo, useState } from "react";

import { audioController } from "@/lib/audio-controller";
import { emitPlaylistsUpdatedEvent } from "@/lib/playlist-events";
import { fetchPlaylistsLibrary, upsertCachedOwnedPlaylist } from "@/lib/playlists-client";
import type { Playlist, Song } from "@/lib/types";
import { playerActions } from "@/store/playerStore";

import Artwork from "@/components/ui/Artwork";
import ModalSheet from "@/components/ui/ModalSheet";

type SaveToPlaylistButtonProps = {
  song: Song;
  className?: string;
  label?: string;
  onOpen?: () => void;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  hideTrigger?: boolean;
};

const VISITOR_ID_STORAGE_KEY = "spasiba_guest_id";
const VISITOR_ID_COOKIE_KEY = "spasiba_guest_id";
const VISITOR_ID_MAX_AGE_SECONDS = 60 * 60 * 24 * 365 * 10;

type PlaylistResponse = {
  playlist?: Playlist;
  error?: string;
};

type BasicApiResponse = {
  ok?: boolean;
  playlist?: Playlist | null;
  error?: string;
};

function readCookieValue(name: string) {
  if (typeof document === "undefined") {
    return null;
  }

  const prefix = `${name}=`;
  const cookie = document.cookie
    .split(";")
    .map((value) => value.trim())
    .find((value) => value.startsWith(prefix));

  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null;
}

function writeVisitorCookie(id: string) {
  if (typeof document === "undefined") {
    return;
  }

  const secure = window.location.protocol === "https:" ? "; Secure" : "";
  document.cookie = `${VISITOR_ID_COOKIE_KEY}=${encodeURIComponent(id)}; Path=/; Max-Age=${VISITOR_ID_MAX_AGE_SECONDS}; SameSite=Lax${secure}`;
}

function ensureVisitorId() {
  if (typeof window === "undefined") {
    return "";
  }

  const existingLocalId = window.localStorage.getItem(VISITOR_ID_STORAGE_KEY);
  const existingCookieId = readCookieValue(VISITOR_ID_COOKIE_KEY);
  const visitorId = existingLocalId || existingCookieId || window.crypto.randomUUID();

  window.localStorage.setItem(VISITOR_ID_STORAGE_KEY, visitorId);
  writeVisitorCookie(visitorId);
  return visitorId;
}

async function loadPlaylists(force = false) {
  ensureVisitorId();
  const library = await fetchPlaylistsLibrary({ force });
  return library.playlists;
}

async function createPlaylistRequest(name: string) {
  ensureVisitorId();
  const response = await fetch("/api/playlists", {
    method: "POST",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json"
    },
    body: JSON.stringify({ name })
  });

  const payload = (await response.json().catch(() => null)) as PlaylistResponse | null;

  if (!response.ok || !payload?.playlist) {
    throw new Error(payload?.error || "Unable to create playlist.");
  }

  return payload.playlist;
}

async function addSongToPlaylistRequest(playlistId: string, song: Song) {
  ensureVisitorId();
  const response = await fetch(`/api/playlists/${encodeURIComponent(playlistId)}/tracks`, {
    method: "POST",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json"
    },
    body: JSON.stringify({ song })
  });

  const payload = (await response.json().catch(() => null)) as BasicApiResponse | null;

  if (!response.ok) {
    throw new Error(payload?.error || "Unable to save song.");
  }

  return payload?.playlist || null;
}

function SaveToPlaylistButtonComponent({
  song,
  className,
  label = "Add to playlist",
  onOpen,
  open: controlledOpen,
  onOpenChange,
  hideTrigger = false
}: SaveToPlaylistButtonProps) {
  const [internalOpen, setInternalOpen] = useState(false);
  const [playlists, setPlaylists] = useState<Playlist[]>([]);
  const [newPlaylistName, setNewPlaylistName] = useState("");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const isControlled = typeof controlledOpen === "boolean";
  const isOpen = isControlled ? controlledOpen : internalOpen;

  const setOpenState = useCallback(
    (nextOpen: boolean) => {
      if (!isControlled) {
        setInternalOpen(nextOpen);
      }

      onOpenChange?.(nextOpen);
    },
    [isControlled, onOpenChange]
  );
  const refreshPlaylists = useCallback(async (force = false) => {
    setLoading(true);
    setError(null);
    const nextPlaylists = await loadPlaylists(force);
    setPlaylists(nextPlaylists);
    setLoading(false);
    return nextPlaylists;
  }, []);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    let cancelled = false;
    setMessage(null);

    void refreshPlaylists().catch((requestError) => {
      if (cancelled) {
        return;
      }

      setLoading(false);
      setError(requestError instanceof Error ? requestError.message : "Unable to load playlists.");
    });

    return () => {
      cancelled = true;
    };
  }, [isOpen, refreshPlaylists]);

  const playlistLabel = useMemo(() => (playlists.length === 1 ? "playlist" : "playlists"), [playlists.length]);

  const close = () => {
    setOpenState(false);
    setMessage(null);
    setError(null);
  };

  const handleSaveToPlaylist = async (playlistId: string, playlistName?: string) => {
    setSaving(true);
    setError(null);
    setMessage(null);

    try {
      const updatedPlaylist = await addSongToPlaylistRequest(playlistId, song);
      audioController.recordTasteAction(song, "playlist_add");
      if (updatedPlaylist) {
        upsertCachedOwnedPlaylist(updatedPlaylist, { emit: false });
        setPlaylists((previous) =>
          previous.map((playlist) => (playlist.id === updatedPlaylist.id ? updatedPlaylist : playlist))
        );
      }
      emitPlaylistsUpdatedEvent();
      const savedPlaylist = updatedPlaylist || playlists.find((playlist) => playlist.id === playlistId) || null;
      playerActions.showToast(`Added "${song.title}" to ${playlistName || savedPlaylist?.name || "playlist"}.`);
      setMessage(`Added to ${playlistName || savedPlaylist?.name || "playlist"}.`);
      setTimeout(() => {
        setOpenState(false);
        setMessage(null);
      }, 700);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to save song.");
    } finally {
      setSaving(false);
    }
  };

  const handleCreatePlaylist = async () => {
    const trimmedName = newPlaylistName.trim();

    if (!trimmedName) {
      setError("Enter a playlist name.");
      return;
    }

    setSaving(true);
    setError(null);
    setMessage(null);

    try {
      const playlist = await createPlaylistRequest(trimmedName);
      const updatedPlaylist = await addSongToPlaylistRequest(playlist.id, song);
      const finalPlaylist = updatedPlaylist || playlist;
      audioController.recordTasteAction(song, "playlist_add");
      upsertCachedOwnedPlaylist(finalPlaylist, { emit: false });
      setPlaylists((previous) => [finalPlaylist, ...previous.filter((entry) => entry.id !== finalPlaylist.id)]);
      setNewPlaylistName("");
      emitPlaylistsUpdatedEvent();
      playerActions.showToast(`Created ${finalPlaylist.name} and added "${song.title}".`);
      setMessage(`Created ${finalPlaylist.name} and added this song.`);
      setTimeout(() => {
        setOpenState(false);
        setMessage(null);
      }, 800);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to create playlist.");
    } finally {
      setSaving(false);
    }
  };

  const modal = (
    <ModalSheet
      open={isOpen}
      onClose={close}
      wide
      title="Save song to a playlist"
      subtitle="Create a new playlist or add this track to one you already have."
    >
      <div className="grid gap-0 lg:grid-cols-[360px_minmax(0,1fr)]">
            <div className="border-b border-white/8 bg-white/[0.02] p-6 sm:p-8 lg:border-b-0 lg:border-r">
              <div className="overflow-hidden rounded-[28px] border border-white/8 bg-white/[0.04]">
                <div className="relative aspect-square">
                  <Artwork
                    src={song.thumbnail}
                    alt={song.title}
                    sizes="(max-width: 1024px) 100vw, 360px"
                    quality={75}
                    className="h-full w-full"
                    imageClassName="object-cover"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/15 to-transparent" />
                  <div className="absolute left-4 top-4 rounded-full border border-white/10 bg-slate-950/70 px-3 py-1 text-[10px] uppercase tracking-[0.28em] text-white/80 backdrop-blur-md">
                    {song.duration}
                  </div>
                </div>

                <div className="space-y-3 p-5">
                  <p className="text-[11px] uppercase tracking-[0.24em] text-slate-400">Selected song</p>
                  <h4 className="text-xl font-semibold tracking-tight text-white">{song.title}</h4>
                  <p className="text-sm text-slate-300/75">{song.author}</p>
                </div>
              </div>

              <div className="mt-6 space-y-3">
                <label className="block text-[11px] uppercase tracking-[0.24em] text-slate-400">Create playlist</label>
                <div className="space-y-3">
                  <input
                    value={newPlaylistName}
                    onChange={(event) => setNewPlaylistName(event.target.value)}
                    placeholder="Sunday drive"
                    className="w-full rounded-2xl border border-white/10 bg-white/[0.05] px-4 py-4 text-base text-white placeholder:text-slate-500 focus:border-teal-300/35 focus:outline-none"
                  />
                  <button
                    type="button"
                    onClick={() => void handleCreatePlaylist()}
                    disabled={saving}
                    className="flex w-full items-center justify-center rounded-2xl bg-white px-4 py-4 text-base font-medium text-slate-950 transition hover:bg-teal-300 disabled:cursor-wait disabled:opacity-70"
                  >
                    {saving ? "Saving..." : "Create and add song"}
                  </button>
                </div>
              </div>
            </div>

            <div className="p-6 sm:p-8">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-[11px] uppercase tracking-[0.24em] text-slate-400">Your playlists</p>
                  <p className="mt-2 text-sm text-slate-300/75">
                    {playlists.length} {playlistLabel} ready
                  </p>
                </div>
              </div>

              <div className="mt-6 space-y-4">
                {loading ? (
                  <>
                    <div className="shimmer h-24 rounded-[24px]" />
                    <div className="shimmer h-24 rounded-[24px]" />
                    <div className="shimmer h-24 rounded-[24px]" />
                  </>
                ) : null}

                {!loading && playlists.length === 0 ? (
                  <div className="rounded-[28px] border border-dashed border-white/10 bg-white/[0.03] px-6 py-8 text-center text-sm text-slate-300/75">
                    No playlists yet. Create your first one on the left and this song will be added instantly.
                  </div>
                ) : null}

                {!loading
                  ? playlists.map((playlist) => (
                      <button
                        key={playlist.id}
                        type="button"
                        onClick={() => void handleSaveToPlaylist(playlist.id, playlist.name)}
                        disabled={saving}
                        className="flex w-full items-center justify-between gap-4 rounded-[26px] border border-white/10 bg-white/[0.05] px-5 py-5 text-left transition hover:border-teal-300/25 hover:bg-white/[0.09] disabled:cursor-wait disabled:opacity-70"
                      >
                        <div className="min-w-0">
                          <p className="truncate text-lg font-medium text-white">{playlist.name}</p>
                          <p className="mt-2 text-xs uppercase tracking-[0.22em] text-slate-400">{playlist.trackCount} songs</p>
                        </div>
                        <span className="shrink-0 rounded-full bg-white px-4 py-2 text-xs font-semibold uppercase tracking-[0.22em] text-slate-950">
                          Add song
                        </span>
                      </button>
                    ))
                  : null}
              </div>

              {message ? <p className="mt-5 text-sm text-teal-100">{message}</p> : null}
              {error ? <p className="mt-5 text-sm text-rose-200">{error}</p> : null}
            </div>
      </div>
    </ModalSheet>
  );

  return (
    <>
      {!hideTrigger ? (
        <button
          type="button"
          onClick={(event) => {
            event.preventDefault();
            event.stopPropagation();
            ensureVisitorId();
            setOpenState(true);
            onOpen?.();
          }}
          className={
            className ||
            "flex items-center justify-center rounded-2xl border border-white/10 bg-white/[0.05] px-4 py-3 text-sm font-medium text-slate-100 transition hover:bg-white/[0.1]"
          }
          aria-label="Save to playlist"
        >
          {label}
        </button>
      ) : null}
      {modal}
    </>
  );
}

const SaveToPlaylistButton = memo(SaveToPlaylistButtonComponent);

export default SaveToPlaylistButton;
