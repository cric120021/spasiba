"use client";

import { useRouter } from "next/navigation";
import { createPortal } from "react-dom";
import { memo, useCallback, useEffect, useRef, useState } from "react";

import { emitPlaylistsUpdatedEvent, PLAYLISTS_UPDATED_EVENT } from "@/lib/playlist-events";
import {
  fetchPlaylistsLibrary,
  primePlaylistsLibrary,
  readCachedPlaylistsLibrary,
  removeCachedOwnedPlaylist,
  removeCachedSharedPlaylist,
  upsertCachedOwnedPlaylist,
  upsertCachedSharedPlaylist
} from "@/lib/playlists-client";
import { playerActions } from "@/store/playerStore";
import type { Playlist, PlaylistShareGrant } from "@/lib/types";

import ArtworkImage from "@/components/ArtworkImage";

type PlaylistResponse = {
  playlist?: Playlist;
  error?: string;
};

type BasicApiResponse = {
  ok?: boolean;
  error?: string;
};

type ShareAccessResponse = {
  shareToken?: string;
  shareUrl?: string;
  grants?: PlaylistShareGrant[];
  grantedUser?: {
    visitorId: string;
    displayName: string | null;
    username: string;
  };
  error?: string;
};

type UnlockCodeResponse = {
  ok?: boolean;
  shareToken?: string;
  playlist?: Playlist;
  error?: string;
};

type MenuState = {
  playlist: Playlist;
  top: number;
  left: number;
};

async function loadShareAccess(playlistId: string) {
  const response = await fetch(`/api/playlists/${encodeURIComponent(playlistId)}/share`, {
    method: "GET",
    credentials: "same-origin",
    cache: "no-store",
    headers: {
      Accept: "application/json"
    }
  });

  const payload = (await response.json().catch(() => null)) as ShareAccessResponse | null;

  if (!response.ok || !payload?.shareToken) {
    throw new Error(payload?.error || "Unable to load share access.");
  }

  return {
    shareToken: payload.shareToken,
    shareUrl: payload.shareUrl || null,
    grants: payload.grants || []
  };
}

async function grantShareAccessRequest(playlistId: string, username: string) {
  const response = await fetch(`/api/playlists/${encodeURIComponent(playlistId)}/share`, {
    method: "POST",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json"
    },
    body: JSON.stringify({ username })
  });

  const payload = (await response.json().catch(() => null)) as ShareAccessResponse | null;

  if (!response.ok || !payload?.shareToken) {
    throw new Error(payload?.error || "Unable to share this playlist.");
  }

  return {
    shareToken: payload.shareToken,
    shareUrl: payload.shareUrl || null,
    grants: payload.grants || [],
    grantedUser: payload.grantedUser || null
  };
}

async function renamePlaylistRequest(playlistId: string, name: string) {
  const response = await fetch(`/api/playlists/${encodeURIComponent(playlistId)}`, {
    method: "PATCH",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json"
    },
    body: JSON.stringify({ name })
  });

  const payload = (await response.json().catch(() => null)) as PlaylistResponse | null;

  if (!response.ok || !payload?.playlist) {
    throw new Error(payload?.error || "Unable to update playlist.");
  }

  return payload.playlist;
}

async function deletePlaylistRequest(playlistId: string) {
  const response = await fetch(`/api/playlists/${encodeURIComponent(playlistId)}`, {
    method: "DELETE",
    credentials: "same-origin",
    headers: {
      Accept: "application/json"
    }
  });

  const payload = (await response.json().catch(() => null)) as BasicApiResponse | null;

  if (!response.ok) {
    throw new Error(payload?.error || "Unable to delete playlist.");
  }
}

async function deleteSharedPlaylistRequest(shareToken: string) {
  const response = await fetch(`/api/shared/${encodeURIComponent(shareToken)}/access`, {
    method: "DELETE",
    credentials: "same-origin",
    headers: {
      Accept: "application/json"
    }
  });

  const payload = (await response.json().catch(() => null)) as BasicApiResponse | null;

  if (!response.ok) {
    throw new Error(payload?.error || "Unable to remove this shared playlist.");
  }
}

async function revokeShareRequest(playlistId: string) {
  const response = await fetch(`/api/playlists/${encodeURIComponent(playlistId)}/share`, {
    method: "DELETE",
    credentials: "same-origin",
    headers: {
      Accept: "application/json"
    }
  });

  const payload = (await response.json().catch(() => null)) as BasicApiResponse | null;

  if (!response.ok) {
    throw new Error(payload?.error || "Unable to revoke sharing.");
  }
}

async function revokeShareAccessRequest(playlistId: string, username: string) {
  const response = await fetch(`/api/playlists/${encodeURIComponent(playlistId)}/share`, {
    method: "DELETE",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json"
    },
    body: JSON.stringify({ username })
  });

  const payload = (await response.json().catch(() => null)) as ShareAccessResponse | null;

  if (!response.ok) {
    throw new Error(payload?.error || "Unable to revoke access.");
  }

  return payload?.grants || [];
}

async function unlockSharedCodeRequest(code: string) {
  const response = await fetch("/api/shared/code", {
    method: "POST",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json"
    },
    body: JSON.stringify({ code })
  });

  const payload = (await response.json().catch(() => null)) as UnlockCodeResponse | null;

  if (!response.ok || !payload?.shareToken) {
    throw new Error(payload?.error || "Unable to open this shared playlist.");
  }

  return payload;
}

async function copyText(value: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value);
    return true;
  }

  const textarea = document.createElement("textarea");
  textarea.value = value;
  textarea.setAttribute("readonly", "true");
  textarea.style.position = "fixed";
  textarea.style.opacity = "0";
  document.body.appendChild(textarea);
  textarea.select();

  const copied = document.execCommand("copy");
  document.body.removeChild(textarea);
  return copied;
}

function getStableCoverTrack(playlist: Playlist) {
  if (playlist.coverTrack) {
    return playlist.coverTrack;
  }

  if (!playlist.tracks.length) {
    return null;
  }

  let hash = 0;

  for (const character of `${playlist.id}:${playlist.updatedAt}`) {
    hash = (hash * 31 + character.charCodeAt(0)) | 0;
  }

  return playlist.tracks[Math.abs(hash) % playlist.tracks.length] || playlist.tracks[0];
}

function getMenuPosition(rect: DOMRect) {
  const viewportPadding = 16;
  const menuWidth = 216;
  const estimatedMenuHeight = 224;
  const preferredLeft = rect.right - menuWidth;
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

function LibrarySectionComponent() {
  const initialLibrary = readCachedPlaylistsLibrary();
  const router = useRouter();
  const prefetchedRoutesRef = useRef<Set<string>>(new Set());
  const [mounted, setMounted] = useState(false);
  const [playlists, setPlaylists] = useState<Playlist[]>(() => initialLibrary?.playlists || []);
  const [sharedPlaylists, setSharedPlaylists] = useState<Playlist[]>(() => initialLibrary?.sharedPlaylists || []);
  const [loading, setLoading] = useState(() => initialLibrary === undefined);
  const [error, setError] = useState<string | null>(null);
  const [revokingPlaylistId, setRevokingPlaylistId] = useState<string | null>(null);
  const [menuState, setMenuState] = useState<MenuState | null>(null);
  const [playlistToShare, setPlaylistToShare] = useState<Playlist | null>(null);
  const [shareToken, setShareToken] = useState("");
  const [shareUrl, setShareUrl] = useState<string | null>(null);
  const [shareGrants, setShareGrants] = useState<PlaylistShareGrant[]>([]);
  const [shareUsername, setShareUsername] = useState("");
  const [shareLoading, setShareLoading] = useState(false);
  const [shareSaving, setShareSaving] = useState(false);
  const [shareError, setShareError] = useState<string | null>(null);
  const [codeModalOpen, setCodeModalOpen] = useState(false);
  const [shareCodeInput, setShareCodeInput] = useState("");
  const [codeSaving, setCodeSaving] = useState(false);
  const [codeError, setCodeError] = useState<string | null>(null);
  const [playlistToRename, setPlaylistToRename] = useState<Playlist | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [renameError, setRenameError] = useState<string | null>(null);
  const [renameSaving, setRenameSaving] = useState(false);
  const [playlistToDelete, setPlaylistToDelete] = useState<Playlist | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleteSaving, setDeleteSaving] = useState(false);
  const [sharedPlaylistToDelete, setSharedPlaylistToDelete] = useState<Playlist | null>(null);
  const [sharedDeleteError, setSharedDeleteError] = useState<string | null>(null);
  const [sharedDeleteSaving, setSharedDeleteSaving] = useState(false);

  const load = useCallback(async () => {
    setError(null);

    try {
      const nextLibrary = await fetchPlaylistsLibrary();
      primePlaylistsLibrary(nextLibrary, { emit: false });
      setPlaylists(nextLibrary.playlists);
      setSharedPlaylists(nextLibrary.sharedPlaylists);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to load playlists.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    setMounted(true);
    void load();

    const handleUpdate = () => {
      void load();
    };

    window.addEventListener(PLAYLISTS_UPDATED_EVENT, handleUpdate);

    return () => {
      window.removeEventListener(PLAYLISTS_UPDATED_EVENT, handleUpdate);
    };
  }, [load]);

  useEffect(() => {
    if (!menuState && !playlistToRename && !playlistToDelete && !sharedPlaylistToDelete && !playlistToShare && !codeModalOpen) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setMenuState(null);

        if (!renameSaving) {
          setPlaylistToRename(null);
        }

        if (!deleteSaving) {
          setPlaylistToDelete(null);
        }

        if (!sharedDeleteSaving) {
          setSharedPlaylistToDelete(null);
        }

        if (!shareSaving) {
          setPlaylistToShare(null);
          setCodeModalOpen(false);
        }
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [codeModalOpen, deleteSaving, menuState, playlistToDelete, playlistToRename, playlistToShare, renameSaving, shareSaving, sharedDeleteSaving, sharedPlaylistToDelete]);

  const openPlaylist = useCallback(
    (playlistId: string) => {
      router.push(`/library/${playlistId}`, { scroll: false });
    },
    [router]
  );

  const prefetchRoute = useCallback(
    (route: string) => {
      if (prefetchedRoutesRef.current.has(route)) {
        return;
      }

      prefetchedRoutesRef.current.add(route);
      router.prefetch(route);
    },
    [router]
  );

  const openSharedPlaylist = useCallback(
    (shareToken: string) => {
      const route = `/shared/${shareToken}`;
      prefetchRoute(route);
      router.push(route, { scroll: false });
    },
    [prefetchRoute, router]
  );

  const openMenu = useCallback((playlist: Playlist, target: HTMLElement) => {
    const rect = target.getBoundingClientRect();
    const position = getMenuPosition(rect);

    setMenuState({
      playlist,
      ...position
    });
  }, []);

  const handleSharePlaylist = useCallback(async (playlist: Playlist) => {
    setMenuState(null);
    setPlaylistToShare(playlist);
    setShareError(null);
    setShareUsername("");
    setShareLoading(true);

    try {
      const access = await loadShareAccess(playlist.id);
      setShareToken(access.shareToken);
      setShareUrl(access.shareUrl);
      setShareGrants(access.grants);
    } catch (shareError) {
      setShareError(shareError instanceof Error ? shareError.message : "Unable to load share access.");
    } finally {
      setShareLoading(false);
    }
  }, []);

  const handleCopyShareLink = useCallback(async () => {
    if (!shareUrl || !playlistToShare) {
      return;
    }

    try {
      const copied = await copyText(shareUrl);
      playerActions.showToast(copied ? `Copied ${playlistToShare.name} link.` : "Share link ready.");
    } catch {
      playerActions.showToast("Unable to copy the share link.");
    }
  }, [playlistToShare, shareUrl]);

  const handleCopyShareCode = useCallback(async () => {
    if (!shareToken || !playlistToShare) {
      return;
    }

    try {
      const copied = await copyText(shareToken);
      playerActions.showToast(copied ? `Copied ${playlistToShare.name} code.` : "Share code ready.");
    } catch {
      playerActions.showToast("Unable to copy the share code.");
    }
  }, [playlistToShare, shareToken]);

  const handleGrantShareAccess = useCallback(async () => {
    if (!playlistToShare) {
      return;
    }

    const trimmedUsername = shareUsername.trim();

    if (!trimmedUsername) {
      setShareError("Enter a username to share with.");
      return;
    }

    setShareSaving(true);
    setShareError(null);

    try {
      const nextAccess = await grantShareAccessRequest(playlistToShare.id, trimmedUsername);
      setShareToken(nextAccess.shareToken);
      setShareUrl(nextAccess.shareUrl);
      setShareGrants(nextAccess.grants);
      setShareUsername("");
      upsertCachedOwnedPlaylist(
        {
          ...playlistToShare,
          shareToken: nextAccess.shareToken
        },
        { emit: false }
      );
      emitPlaylistsUpdatedEvent();
      playerActions.showToast(
        nextAccess.grantedUser
          ? `Shared ${playlistToShare.name} with @${nextAccess.grantedUser.username}.`
          : `Shared ${playlistToShare.name}.`
      );
    } catch (requestError) {
      setShareError(requestError instanceof Error ? requestError.message : "Unable to share this playlist.");
    } finally {
      setShareSaving(false);
    }
  }, [playlistToShare, shareUsername]);

  const handleRevokeShareAccess = useCallback(
    async (grant: PlaylistShareGrant) => {
      if (!playlistToShare) {
        return;
      }

      setShareSaving(true);
      setShareError(null);

      try {
        const nextGrants = await revokeShareAccessRequest(playlistToShare.id, grant.username);
        setShareGrants(nextGrants);
        emitPlaylistsUpdatedEvent();
        playerActions.showToast(`Removed @${grant.username} from ${playlistToShare.name}.`);
      } catch (requestError) {
        setShareError(requestError instanceof Error ? requestError.message : "Unable to revoke access.");
      } finally {
        setShareSaving(false);
      }
    },
    [playlistToShare]
  );

  const handleUnlockCode = useCallback(async () => {
    const trimmedCode = shareCodeInput.trim();

    if (!trimmedCode) {
      setCodeError("Enter a share code.");
      return;
    }

    setCodeSaving(true);
    setCodeError(null);

    try {
      const result = await unlockSharedCodeRequest(trimmedCode);
      if (result.playlist) {
        upsertCachedSharedPlaylist(result.playlist, { emit: false });
      }
      emitPlaylistsUpdatedEvent();
      setCodeModalOpen(false);
      setShareCodeInput("");
      playerActions.showToast(`Opened ${result.playlist?.name || "shared playlist"}.`);
      if (result.shareToken) {
        prefetchRoute(`/shared/${result.shareToken}`);
        openSharedPlaylist(result.shareToken);
      }
    } catch (requestError) {
      setCodeError(requestError instanceof Error ? requestError.message : "Unable to open this shared playlist.");
    } finally {
      setCodeSaving(false);
    }
  }, [openSharedPlaylist, prefetchRoute, shareCodeInput]);

  const startRename = useCallback((playlist: Playlist) => {
    setMenuState(null);
    setRenameError(null);
    setRenameValue(playlist.name);
    setPlaylistToRename(playlist);
  }, []);

  const submitRename = useCallback(async () => {
    if (!playlistToRename) {
      return;
    }

    const trimmedName = renameValue.trim();

    if (!trimmedName) {
      setRenameError("Enter a playlist name.");
      return;
    }

    setRenameSaving(true);
    setRenameError(null);

    try {
      const updatedPlaylist = await renamePlaylistRequest(playlistToRename.id, trimmedName);
      setPlaylists((previous) => previous.map((playlist) => (playlist.id === updatedPlaylist.id ? updatedPlaylist : playlist)));
      upsertCachedOwnedPlaylist(updatedPlaylist, { emit: false });
      emitPlaylistsUpdatedEvent();
      playerActions.showToast(`Renamed to ${updatedPlaylist.name}.`);
      setPlaylistToRename(null);
    } catch (requestError) {
      setRenameError(requestError instanceof Error ? requestError.message : "Unable to update playlist.");
    } finally {
      setRenameSaving(false);
    }
  }, [playlistToRename, renameValue]);

  const startDelete = useCallback((playlist: Playlist) => {
    setMenuState(null);
    setDeleteError(null);
    setPlaylistToDelete(playlist);
  }, []);

  const startSharedDelete = useCallback((playlist: Playlist) => {
    setSharedDeleteError(null);
    setSharedPlaylistToDelete(playlist);
  }, []);

  const confirmDelete = useCallback(async () => {
    if (!playlistToDelete) {
      return;
    }

    setDeleteSaving(true);
    setDeleteError(null);

    try {
      await deletePlaylistRequest(playlistToDelete.id);
      setPlaylists((previous) => previous.filter((playlist) => playlist.id !== playlistToDelete.id));
      removeCachedOwnedPlaylist(playlistToDelete.id, { emit: false });
      emitPlaylistsUpdatedEvent();
      playerActions.showToast(`Deleted ${playlistToDelete.name}.`);
      setPlaylistToDelete(null);
    } catch (requestError) {
      setDeleteError(requestError instanceof Error ? requestError.message : "Unable to delete playlist.");
    } finally {
      setDeleteSaving(false);
    }
  }, [playlistToDelete]);

  const confirmSharedDelete = useCallback(async () => {
    if (!sharedPlaylistToDelete?.shareToken) {
      return;
    }

    setSharedDeleteSaving(true);
    setSharedDeleteError(null);

    try {
      await deleteSharedPlaylistRequest(sharedPlaylistToDelete.shareToken);
      setSharedPlaylists((previous) => previous.filter((playlist) => playlist.id !== sharedPlaylistToDelete.id));
      removeCachedSharedPlaylist(sharedPlaylistToDelete.id, { emit: false });
      emitPlaylistsUpdatedEvent();
      playerActions.showToast(`Removed ${sharedPlaylistToDelete.name} from your shared playlists.`);
      setSharedPlaylistToDelete(null);
    } catch (requestError) {
      setSharedDeleteError(requestError instanceof Error ? requestError.message : "Unable to remove this shared playlist.");
    } finally {
      setSharedDeleteSaving(false);
    }
  }, [sharedPlaylistToDelete]);

  const handleRevokeShare = useCallback(async (playlist: Playlist) => {
    setMenuState(null);
    setRevokingPlaylistId(playlist.id);

    try {
      await revokeShareRequest(playlist.id);
      const nextPlaylist = {
        ...playlist,
        shareToken: null
      } satisfies Playlist;
      setPlaylists((previous) => previous.map((entry) => (entry.id === playlist.id ? nextPlaylist : entry)));
      if (playlistToShare?.id === playlist.id) {
        setShareGrants([]);
        setShareToken("");
        setShareUrl(null);
        setPlaylistToShare(null);
      }
      upsertCachedOwnedPlaylist(nextPlaylist, { emit: false });
      emitPlaylistsUpdatedEvent();
      playerActions.showToast(`Stopped sharing ${playlist.name}.`);
    } catch (error) {
      playerActions.showToast(error instanceof Error ? error.message : "Unable to revoke sharing.");
    } finally {
      setRevokingPlaylistId(null);
    }
  }, [playlistToShare]);

  if (loading) {
    return <div className="shimmer h-52 rounded-[28px] sm:h-56 sm:rounded-[32px]" />;
  }

  if (error) {
    return (
      <section className="glass-panel rounded-[30px] border border-rose-400/20 p-6">
        <p className="text-sm text-rose-100">{error}</p>
      </section>
    );
  }

  const menu = menuState ? (
    <div className="fixed inset-0 z-[150]" onClick={() => setMenuState(null)}>
      <div
        className="absolute w-[216px] overflow-hidden rounded-[24px] border border-white/10 bg-[linear-gradient(180deg,rgba(13,20,34,0.98),rgba(8,12,22,0.98))] p-2 shadow-[0_30px_120px_rgba(0,0,0,0.55)]"
        style={{ top: `${menuState.top}px`, left: `${menuState.left}px` }}
        onClick={(event) => event.stopPropagation()}
      >
        <button
          type="button"
          onClick={() => void handleSharePlaylist(menuState.playlist)}
          className="flex w-full items-center justify-between rounded-2xl px-4 py-3 text-left text-sm text-white transition hover:bg-white/[0.08]"
        >
          <span>Share access</span>
          <span className="text-[11px] uppercase tracking-[0.22em] text-slate-400">Manage</span>
        </button>
        {menuState.playlist.shareToken ? (
          <button
            type="button"
            onClick={() => void handleRevokeShare(menuState.playlist)}
            className="flex w-full items-center justify-between rounded-2xl px-4 py-3 text-left text-sm text-amber-100 transition hover:bg-amber-400/10"
          >
            <span>Revoke share</span>
            <span className="text-[11px] uppercase tracking-[0.22em] text-amber-100/70">
              {revokingPlaylistId === menuState.playlist.id ? "..." : "Stop"}
            </span>
          </button>
        ) : null}
        <button
          type="button"
          onClick={() => startRename(menuState.playlist)}
          className="flex w-full items-center justify-between rounded-2xl px-4 py-3 text-left text-sm text-white transition hover:bg-white/[0.08]"
        >
          <span>Edit name</span>
          <span className="text-[11px] uppercase tracking-[0.22em] text-slate-400">Rename</span>
        </button>
        <button
          type="button"
          onClick={() => startDelete(menuState.playlist)}
          className="flex w-full items-center justify-between rounded-2xl px-4 py-3 text-left text-sm text-rose-200 transition hover:bg-rose-400/10"
        >
          <span>Delete</span>
          <span className="text-[11px] uppercase tracking-[0.22em] text-rose-200/70">Remove</span>
        </button>
      </div>
    </div>
  ) : null;

  const renameModal = playlistToRename ? (
    <div
      className="fixed inset-0 z-[160] overflow-y-auto overscroll-contain bg-slate-950/86 px-4 py-[max(1rem,env(safe-area-inset-top))] pb-[max(1rem,env(safe-area-inset-bottom))] backdrop-blur-xl sm:px-6"
      onClick={() => !renameSaving && setPlaylistToRename(null)}
    >
      <div className="flex min-h-full items-start justify-center py-4 sm:items-center sm:py-8">
        <div
          className="w-full max-w-md max-h-[calc(100dvh_-_2rem_-_env(safe-area-inset-top)_-_env(safe-area-inset-bottom))] overflow-y-auto rounded-[30px] border border-white/10 bg-[linear-gradient(180deg,rgba(13,20,34,0.98),rgba(8,12,22,0.98))] p-6 shadow-[0_30px_120px_rgba(0,0,0,0.6)]"
          onClick={(event) => event.stopPropagation()}
          role="dialog"
          aria-modal="true"
          aria-label="Rename playlist"
        >
          <p className="text-[11px] uppercase tracking-[0.28em] text-slate-400">Edit playlist</p>
          <h3 className="mt-3 text-2xl font-semibold tracking-tight text-white">Rename {playlistToRename.name}</h3>
          <p className="mt-3 text-sm leading-6 text-slate-300/78">Give this playlist a cleaner title. Your songs stay exactly where they are.</p>
          <input
            value={renameValue}
            onChange={(event) => setRenameValue(event.target.value)}
            placeholder="Late night drive"
            className="mt-5 w-full rounded-2xl border border-white/10 bg-white/[0.05] px-4 py-4 text-base text-white placeholder:text-slate-500 focus:border-teal-300/35 focus:outline-none"
          />
          {renameError ? <p className="mt-3 text-sm text-rose-200">{renameError}</p> : null}
          <div className="mt-5 flex gap-3">
            <button
              type="button"
              onClick={() => setPlaylistToRename(null)}
              disabled={renameSaving}
              className="flex-1 rounded-2xl border border-white/10 bg-[#050816] px-4 py-3 text-sm font-medium text-slate-100 transition hover:bg-[#0a1120] disabled:cursor-wait disabled:opacity-70"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={() => void submitRename()}
              disabled={renameSaving}
              className="flex-1 rounded-2xl border border-white/12 bg-white px-4 py-3 text-sm font-medium text-slate-950 transition hover:bg-teal-300 disabled:cursor-wait disabled:opacity-70"
            >
              {renameSaving ? "Saving..." : "Save name"}
            </button>
          </div>
        </div>
      </div>
    </div>
  ) : null;

  const deleteModal = playlistToDelete ? (
    <div
      className="fixed inset-0 z-[160] overflow-y-auto overscroll-contain bg-slate-950/86 px-4 py-[max(1rem,env(safe-area-inset-top))] pb-[max(1rem,env(safe-area-inset-bottom))] backdrop-blur-xl sm:px-6"
      onClick={() => !deleteSaving && setPlaylistToDelete(null)}
    >
      <div className="flex min-h-full items-start justify-center py-4 sm:items-center sm:py-8">
        <div
          className="w-full max-w-md max-h-[calc(100dvh_-_2rem_-_env(safe-area-inset-top)_-_env(safe-area-inset-bottom))] overflow-y-auto rounded-[30px] border border-white/10 bg-[linear-gradient(180deg,rgba(13,20,34,0.98),rgba(8,12,22,0.98))] p-6 shadow-[0_30px_120px_rgba(0,0,0,0.6)]"
          onClick={(event) => event.stopPropagation()}
          role="dialog"
          aria-modal="true"
          aria-label="Delete playlist"
        >
          <p className="text-[11px] uppercase tracking-[0.28em] text-slate-400">Delete playlist</p>
          <h3 className="mt-3 text-2xl font-semibold tracking-tight text-white">Delete {playlistToDelete.name}?</h3>
          <p className="mt-3 text-sm leading-6 text-slate-300/78">This removes the playlist and all songs saved inside it. This action cannot be undone.</p>
          {deleteError ? <p className="mt-3 text-sm text-rose-200">{deleteError}</p> : null}
          <div className="mt-5 flex gap-3">
            <button
              type="button"
              onClick={() => setPlaylistToDelete(null)}
              disabled={deleteSaving}
              className="flex-1 rounded-2xl border border-white/10 bg-[#050816] px-4 py-3 text-sm font-medium text-slate-100 transition hover:bg-[#0a1120] disabled:cursor-wait disabled:opacity-70"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={() => void confirmDelete()}
              disabled={deleteSaving}
              className="flex-1 rounded-2xl border border-rose-300/20 bg-rose-400/12 px-4 py-3 text-sm font-medium text-rose-100 transition hover:bg-rose-400/18 disabled:cursor-wait disabled:opacity-70"
            >
              {deleteSaving ? "Deleting..." : "Delete playlist"}
            </button>
          </div>
        </div>
      </div>
    </div>
  ) : null;

  const sharedDeleteModal = sharedPlaylistToDelete ? (
    <div
      className="fixed inset-0 z-[160] overflow-y-auto overscroll-contain bg-slate-950/86 px-4 py-[max(1rem,env(safe-area-inset-top))] pb-[max(1rem,env(safe-area-inset-bottom))] backdrop-blur-xl sm:px-6"
      onClick={() => !sharedDeleteSaving && setSharedPlaylistToDelete(null)}
    >
      <div className="flex min-h-full items-start justify-center py-4 sm:items-center sm:py-8">
        <div
          className="w-full max-w-md max-h-[calc(100dvh_-_2rem_-_env(safe-area-inset-top)_-_env(safe-area-inset-bottom))] overflow-y-auto rounded-[30px] border border-white/10 bg-[linear-gradient(180deg,rgba(13,20,34,0.98),rgba(8,12,22,0.98))] p-6 shadow-[0_30px_120px_rgba(0,0,0,0.6)]"
          onClick={(event) => event.stopPropagation()}
          role="dialog"
          aria-modal="true"
          aria-label="Remove shared playlist"
        >
          <p className="text-[11px] uppercase tracking-[0.28em] text-slate-400">Remove shared playlist</p>
          <h3 className="mt-3 text-2xl font-semibold tracking-tight text-white">Remove {sharedPlaylistToDelete.name}?</h3>
          <p className="mt-3 text-sm leading-6 text-slate-300/78">
            This removes the playlist from your shared library and revokes your access. You would need the owner to share it again before you can open it later.
          </p>
          {sharedDeleteError ? <p className="mt-3 text-sm text-rose-200">{sharedDeleteError}</p> : null}
          <div className="mt-5 flex gap-3">
            <button
              type="button"
              onClick={() => setSharedPlaylistToDelete(null)}
              disabled={sharedDeleteSaving}
              className="flex-1 rounded-2xl border border-white/10 bg-[#050816] px-4 py-3 text-sm font-medium text-slate-100 transition hover:bg-[#0a1120] disabled:cursor-wait disabled:opacity-70"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={() => void confirmSharedDelete()}
              disabled={sharedDeleteSaving}
              className="flex-1 rounded-2xl border border-rose-300/20 bg-rose-400/12 px-4 py-3 text-sm font-medium text-rose-100 transition hover:bg-rose-400/18 disabled:cursor-wait disabled:opacity-70"
            >
              {sharedDeleteSaving ? "Removing..." : "Remove access"}
            </button>
          </div>
        </div>
      </div>
    </div>
  ) : null;

  const shareModal = playlistToShare ? (
    <div
      className="fixed inset-0 z-[160] overflow-y-auto overscroll-contain bg-slate-950/86 px-4 py-[max(1rem,env(safe-area-inset-top))] pb-[max(1rem,env(safe-area-inset-bottom))] backdrop-blur-xl sm:px-6"
      onClick={() => !shareSaving && setPlaylistToShare(null)}
    >
      <div className="flex min-h-full items-start justify-center py-4 sm:items-center sm:py-8">
        <div
          className="w-full max-w-2xl max-h-[calc(100dvh_-_2rem_-_env(safe-area-inset-top)_-_env(safe-area-inset-bottom))] overflow-y-auto rounded-[30px] border border-white/10 bg-[linear-gradient(180deg,rgba(13,20,34,0.98),rgba(8,12,22,0.98))] p-6 shadow-[0_30px_120px_rgba(0,0,0,0.6)]"
          onClick={(event) => event.stopPropagation()}
          role="dialog"
          aria-modal="true"
          aria-label="Share playlist access"
        >
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="text-[11px] uppercase tracking-[0.28em] text-slate-400">Share access</p>
              <h3 className="mt-3 text-2xl font-semibold tracking-tight text-white">Share {playlistToShare.name}</h3>
              <p className="mt-3 text-sm leading-6 text-slate-300/78">
                Add usernames who can open this playlist, then give them the code so it appears in their shared library.
              </p>
            </div>
            <button
              type="button"
              onClick={() => setPlaylistToShare(null)}
              className="rounded-2xl border border-white/10 bg-[#050816] px-4 py-3 text-sm font-medium text-slate-100 transition hover:bg-[#0a1120]"
            >
              Close
            </button>
          </div>

          <div className="mt-6 grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
            <div className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
              <p className="text-[11px] uppercase tracking-[0.24em] text-slate-400">Share code</p>
              <div className="mt-3 flex flex-col gap-3 sm:flex-row sm:items-center">
                <div className="flex-1 rounded-2xl border border-white/10 bg-slate-950/55 px-4 py-3 text-base font-semibold tracking-[0.22em] text-white">
                  {shareLoading ? "Loading..." : shareToken || "No code yet"}
                </div>
                <button
                  type="button"
                  onClick={() => void handleCopyShareCode()}
                  disabled={!shareToken || shareLoading}
                  className="rounded-2xl border border-white/10 bg-white/[0.05] px-4 py-3 text-sm font-medium text-white transition hover:bg-white/[0.1] disabled:cursor-not-allowed disabled:opacity-60"
                >
                  Copy code
                </button>
                <button
                  type="button"
                  onClick={() => void handleCopyShareLink()}
                  disabled={!shareUrl || shareLoading}
                  className="rounded-2xl border border-white/10 bg-white/[0.05] px-4 py-3 text-sm font-medium text-white transition hover:bg-white/[0.1] disabled:cursor-not-allowed disabled:opacity-60"
                >
                  Copy link
                </button>
              </div>
              <p className="mt-3 text-xs leading-5 text-slate-400">Your friend opens Spasiba, taps Enter code, and uses this code after you add their username here.</p>
            </div>

            <div className="rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
              <p className="text-[11px] uppercase tracking-[0.24em] text-slate-400">Add username</p>
              <div className="mt-3 flex flex-col gap-3">
                <input
                  value={shareUsername}
                  onChange={(event) => setShareUsername(event.target.value)}
                  placeholder="friend_username"
                  className="w-full rounded-2xl border border-white/10 bg-slate-950/55 px-4 py-3 text-base text-white placeholder:text-slate-500 focus:border-teal-300/35 focus:outline-none"
                />
                <button
                  type="button"
                  onClick={() => void handleGrantShareAccess()}
                  disabled={shareSaving || shareLoading}
                  className="rounded-2xl border border-teal-300/25 bg-teal-300/12 px-4 py-3 text-sm font-medium text-teal-100 transition hover:bg-teal-300/18 disabled:cursor-wait disabled:opacity-70"
                >
                  {shareSaving ? "Saving..." : "Add access"}
                </button>
              </div>
            </div>
          </div>

          {shareError ? <p className="mt-4 text-sm text-rose-200">{shareError}</p> : null}

          <div className="mt-6 rounded-[24px] border border-white/10 bg-white/[0.04] p-5">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-[11px] uppercase tracking-[0.24em] text-slate-400">People with access</p>
                <p className="mt-2 text-sm text-slate-300/78">Revoke any person and the playlist disappears from their shared section.</p>
              </div>
              <div className="rounded-full border border-white/10 bg-slate-950/55 px-3 py-1.5 text-[10px] uppercase tracking-[0.22em] text-slate-300">
                {shareGrants.length} users
              </div>
            </div>

            {shareLoading ? (
              <div className="mt-4 space-y-3">
                <div className="shimmer h-16 rounded-2xl" />
                <div className="shimmer h-16 rounded-2xl" />
              </div>
            ) : shareGrants.length ? (
              <div className="mt-4 space-y-3">
                {shareGrants.map((grant) => (
                  <div
                    key={`${playlistToShare.id}-${grant.username}`}
                    className="flex flex-col gap-3 rounded-2xl border border-white/10 bg-slate-950/45 px-4 py-4 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div>
                      <p className="text-sm font-medium text-white">@{grant.username}</p>
                      <p className="mt-1 text-xs uppercase tracking-[0.18em] text-slate-400">
                        {grant.displayName ? `${grant.displayName} • ` : ""}added {new Date(grant.createdAt).toLocaleDateString()}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => void handleRevokeShareAccess(grant)}
                      disabled={shareSaving}
                      className="rounded-2xl border border-rose-300/18 bg-rose-400/10 px-4 py-3 text-sm font-medium text-rose-100 transition hover:bg-rose-400/16 disabled:cursor-wait disabled:opacity-70"
                    >
                      Revoke
                    </button>
                  </div>
                ))}
              </div>
            ) : (
              <div className="mt-4 rounded-2xl border border-dashed border-white/10 bg-slate-950/35 px-4 py-5 text-sm text-slate-300/72">
                No usernames added yet.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  ) : null;

  const codeModal = codeModalOpen ? (
    <div
      className="fixed inset-0 z-[160] overflow-y-auto overscroll-contain bg-slate-950/86 px-4 py-[max(1rem,env(safe-area-inset-top))] pb-[max(1rem,env(safe-area-inset-bottom))] backdrop-blur-xl sm:px-6"
      onClick={() => !codeSaving && setCodeModalOpen(false)}
    >
      <div className="flex min-h-full items-start justify-center py-4 sm:items-center sm:py-8">
        <div
          className="w-full max-w-md max-h-[calc(100dvh_-_2rem_-_env(safe-area-inset-top)_-_env(safe-area-inset-bottom))] overflow-y-auto rounded-[30px] border border-white/10 bg-[linear-gradient(180deg,rgba(13,20,34,0.98),rgba(8,12,22,0.98))] p-6 shadow-[0_30px_120px_rgba(0,0,0,0.6)]"
          onClick={(event) => event.stopPropagation()}
          role="dialog"
          aria-modal="true"
          aria-label="Enter share code"
        >
          <p className="text-[11px] uppercase tracking-[0.28em] text-slate-400">Shared playlist</p>
          <h3 className="mt-3 text-2xl font-semibold tracking-tight text-white">Enter code</h3>
          <p className="mt-3 text-sm leading-6 text-slate-300/78">Use the code your friend shared with your username-enabled profile.</p>
          <input
            value={shareCodeInput}
            onChange={(event) => setShareCodeInput(event.target.value)}
            placeholder="AB12CD34EF"
            className="mt-5 w-full rounded-2xl border border-white/10 bg-white/[0.05] px-4 py-4 text-base uppercase tracking-[0.2em] text-white placeholder:text-slate-500 focus:border-teal-300/35 focus:outline-none"
          />
          {codeError ? <p className="mt-3 text-sm text-rose-200">{codeError}</p> : null}
          <div className="mt-5 flex gap-3">
            <button
              type="button"
              onClick={() => setCodeModalOpen(false)}
              disabled={codeSaving}
              className="flex-1 rounded-2xl border border-white/10 bg-[#050816] px-4 py-3 text-sm font-medium text-slate-100 transition hover:bg-[#0a1120] disabled:cursor-wait disabled:opacity-70"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={() => void handleUnlockCode()}
              disabled={codeSaving}
              className="flex-1 rounded-2xl border border-white/12 bg-white px-4 py-3 text-sm font-medium text-slate-950 transition hover:bg-teal-300 disabled:cursor-wait disabled:opacity-70"
            >
              {codeSaving ? "Opening..." : "Open playlist"}
            </button>
          </div>
        </div>
      </div>
    </div>
  ) : null;

  return (
    <>
      <section className="deferred-section space-y-3.5">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-[10px] uppercase tracking-[0.24em] text-slate-400 sm:text-[11px] sm:tracking-[0.28em]">Personal</p>
            <h2 className="text-[21px] font-semibold tracking-tight text-white sm:text-[25px]">Your library</h2>
            <p className="max-w-xl text-[12px] leading-5 text-slate-300/62 sm:text-[14px]">
              Playlists you made and revisit often.
            </p>
          </div>
          <div className="rounded-full border border-white/[0.08] bg-white/[0.04] px-3 py-1 text-[9px] uppercase tracking-[0.18em] text-slate-300/80 sm:px-3.5 sm:py-1.5 sm:text-[10px] sm:tracking-[0.22em]">
            {playlists.length} playlists
          </div>
        </div>

        {playlists.length === 0 ? (
          <div className="glass-panel rounded-[24px] p-4 text-[15px] leading-7 text-slate-300/76 sm:rounded-[30px] sm:p-6 sm:text-base">
            Your saved playlists will appear here. Use the save button on any song to start building your library.
          </div>
        ) : (
          <div className="horizontal-card-rail playlist-card-rail !gap-3 !px-0 !pb-1">
            {playlists.map((playlist) => {
              const coverTrack = getStableCoverTrack(playlist);

              return (
                <article
                  key={playlist.id}
                  className="group relative overflow-hidden rounded-[22px] border border-white/8 bg-[linear-gradient(180deg,rgba(13,20,34,0.92),rgba(8,12,22,0.92))] shadow-[0_18px_48px_rgba(0,0,0,0.24)] transition hover:border-white/14 hover:bg-[linear-gradient(180deg,rgba(15,24,40,0.96),rgba(9,14,26,0.96))]"
                >
                  <button
                    type="button"
                    onClick={() => openPlaylist(playlist.id)}
                    onPointerEnter={() => prefetchRoute(`/library/${playlist.id}`)}
                    onFocus={() => prefetchRoute(`/library/${playlist.id}`)}
                    onTouchStart={() => prefetchRoute(`/library/${playlist.id}`)}
                    className="block w-full text-left"
                  >
                    <div className="relative aspect-[1/1.06] overflow-hidden bg-white/[0.04]">
                      {coverTrack ? (
                        <>
                          <ArtworkImage
                            src={coverTrack.thumbnail}
                            alt={playlist.name}
                            fill
                            sizes="(max-width: 640px) 100vw, (max-width: 1280px) 50vw, 33vw"
                            quality={75}
                            className="object-cover transition duration-300 group-hover:scale-[1.03]"
                          />
                          <div className="absolute inset-0 bg-[linear-gradient(180deg,rgba(4,8,18,0.08),rgba(4,8,18,0.35)_45%,rgba(4,8,18,0.92))]" />
                        </>
                      ) : (
                        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(74,222,208,0.24),transparent_55%),linear-gradient(180deg,#0a1020,#050816)]" />
                      )}

                      <div className="absolute left-3 top-3 rounded-full border border-white/10 bg-slate-950/70 px-2.5 py-1 text-[9px] uppercase tracking-[0.2em] text-white/80 backdrop-blur-md">
                        {playlist.trackCount} songs
                      </div>

                      <div className="absolute inset-x-0 bottom-0 p-3.5">
                        <p className="text-[10px] uppercase tracking-[0.24em] text-slate-300/70">Playlist</p>
                        <h3 className="mt-1.5 line-clamp-2 text-[18px] font-semibold tracking-tight text-white">{playlist.name}</h3>
                        <p className="mt-1.5 line-clamp-1 text-[12px] text-slate-200/70">
                          {coverTrack ? `Featuring ${coverTrack.author}` : "Start saving songs to bring this playlist to life."}
                        </p>
                      </div>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={(event) => {
                      event.preventDefault();
                      event.stopPropagation();
                      openMenu(playlist, event.currentTarget);
                    }}
                    className="absolute right-3 top-3 z-10 flex h-9 w-9 items-center justify-center rounded-full border border-white/10 bg-slate-950/70 text-lg text-white backdrop-blur-md transition hover:bg-slate-950/88"
                    aria-label={`Open actions for ${playlist.name}`}
                  >
                    <span className="-translate-y-[1px]">⋯</span>
                  </button>
                </article>
              );
            })}
          </div>
        )}
      </section>

      <section className="deferred-section space-y-3.5">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-[10px] uppercase tracking-[0.24em] text-slate-400 sm:text-[11px] sm:tracking-[0.28em]">Shared</p>
            <h2 className="text-[21px] font-semibold tracking-tight text-white sm:text-[25px]">Shared playlists</h2>
            <p className="max-w-xl text-[12px] leading-5 text-slate-300/62 sm:text-[14px]">
              Playlists friends shared with you.
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={() => {
                setCodeError(null);
                setShareCodeInput("");
                setCodeModalOpen(true);
              }}
              className="rounded-full border border-teal-300/18 bg-teal-300/10 px-3.5 py-1.5 text-[10px] uppercase tracking-[0.18em] text-teal-100 transition hover:bg-teal-300/16"
            >
              Enter code
            </button>
            <div className="rounded-full border border-white/[0.08] bg-white/[0.04] px-3 py-1 text-[9px] uppercase tracking-[0.18em] text-slate-300/80 sm:px-3.5 sm:py-1.5 sm:text-[10px] sm:tracking-[0.22em]">
              {sharedPlaylists.length} shared
            </div>
          </div>
        </div>

        {sharedPlaylists.length === 0 ? (
          <div className="glass-panel rounded-[24px] p-4 text-[15px] leading-7 text-slate-300/76 sm:rounded-[30px] sm:p-6 sm:text-base">
            Shared playlists you unlock with a code will show up here automatically.
          </div>
        ) : (
          <div className="horizontal-card-rail playlist-card-rail !gap-3 !px-0 !pb-1">
            {sharedPlaylists.map((playlist) => {
              const coverTrack = getStableCoverTrack(playlist);

              return (
                <article
                  key={`shared-${playlist.id}`}
                  className="group relative overflow-hidden rounded-[22px] border border-sky-300/10 bg-[linear-gradient(180deg,rgba(10,18,32,0.94),rgba(7,12,22,0.94))] shadow-[0_18px_48px_rgba(0,0,0,0.24)] transition hover:border-sky-300/18 hover:bg-[linear-gradient(180deg,rgba(12,22,38,0.96),rgba(8,14,26,0.96))]"
                >
                  <button
                    type="button"
                    onClick={(event) => {
                      event.stopPropagation();
                      startSharedDelete(playlist);
                    }}
                    className="absolute right-3 top-3 z-10 rounded-full border border-white/10 bg-slate-950/78 px-2.5 py-1 text-[9px] uppercase tracking-[0.18em] text-rose-100/88 backdrop-blur-md transition hover:border-rose-200/25 hover:bg-rose-400/12"
                    aria-label={`Remove ${playlist.name} from shared playlists`}
                  >
                    Delete
                  </button>
                  <button
                    type="button"
                    onClick={() => playlist.shareToken && openSharedPlaylist(playlist.shareToken)}
                    onPointerEnter={() => playlist.shareToken && prefetchRoute(`/shared/${playlist.shareToken}`)}
                    onFocus={() => playlist.shareToken && prefetchRoute(`/shared/${playlist.shareToken}`)}
                    onTouchStart={() => playlist.shareToken && prefetchRoute(`/shared/${playlist.shareToken}`)}
                    className="block w-full text-left"
                  >
                    <div className="relative aspect-[1/1.06] overflow-hidden bg-white/[0.04]">
                      {coverTrack ? (
                        <>
                          <ArtworkImage
                            src={coverTrack.thumbnail}
                            alt={playlist.name}
                            fill
                            sizes="(max-width: 640px) 100vw, (max-width: 1280px) 50vw, 33vw"
                            quality={75}
                            className="object-cover transition duration-300 group-hover:scale-[1.03]"
                          />
                          <div className="absolute inset-0 bg-[linear-gradient(180deg,rgba(4,8,18,0.08),rgba(4,8,18,0.35)_45%,rgba(4,8,18,0.92))]" />
                        </>
                      ) : (
                        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(56,189,248,0.24),transparent_55%),linear-gradient(180deg,#0a1020,#050816)]" />
                      )}

                      <div className="absolute left-3 top-3 rounded-full border border-white/10 bg-slate-950/70 px-2.5 py-1 text-[9px] uppercase tracking-[0.2em] text-white/80 backdrop-blur-md">
                        Shared
                      </div>

                      <div className="absolute inset-x-0 bottom-0 p-3.5">
                        <p className="text-[10px] uppercase tracking-[0.24em] text-slate-300/70">
                          {playlist.ownerName ? `By ${playlist.ownerName}` : "Shared playlist"}
                        </p>
                        <h3 className="mt-1.5 line-clamp-2 text-[18px] font-semibold tracking-tight text-white">{playlist.name}</h3>
                        <p className="mt-1.5 line-clamp-1 text-[12px] text-slate-200/70">
                          {coverTrack ? `Featuring ${coverTrack.author}` : "Open it to see the full shared track list."}
                        </p>
                      </div>
                    </div>
                  </button>
                </article>
              );
            })}
          </div>
        )}
      </section>

      {mounted && menu ? createPortal(menu, document.body) : null}
      {mounted && renameModal ? createPortal(renameModal, document.body) : null}
      {mounted && deleteModal ? createPortal(deleteModal, document.body) : null}
      {mounted && sharedDeleteModal ? createPortal(sharedDeleteModal, document.body) : null}
      {mounted && shareModal ? createPortal(shareModal, document.body) : null}
      {mounted && codeModal ? createPortal(codeModal, document.body) : null}
    </>
  );
}

const LibrarySection = memo(LibrarySectionComponent);

export default LibrarySection;
