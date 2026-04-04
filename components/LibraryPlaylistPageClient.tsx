"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";

import PlaylistDetailView from "@/components/PlaylistDetailView";
import { PLAYLISTS_UPDATED_EVENT } from "@/lib/playlist-events";
import { fetchPlaylistsLibrary, readCachedPlaylistsLibrary } from "@/lib/playlists-client";
import type { Playlist } from "@/lib/types";

type LibraryPlaylistPageClientProps = {
  playlistId: string;
};

function readCachedPlaylist(playlistId: string) {
  return readCachedPlaylistsLibrary()?.playlists.find((playlist) => playlist.id === playlistId) || null;
}

export default function LibraryPlaylistPageClient({ playlistId }: LibraryPlaylistPageClientProps) {
  const initialPlaylist = readCachedPlaylist(playlistId);
  const [playlist, setPlaylist] = useState<Playlist | null>(initialPlaylist);
  const [loading, setLoading] = useState(() => initialPlaylist === null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);

    try {
      const library = await fetchPlaylistsLibrary();
      setPlaylist(library.playlists.find((entry) => entry.id === playlistId) || null);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to load this playlist.");
    } finally {
      setLoading(false);
    }
  }, [playlistId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    const handleUpdate = () => {
      void load();
    };

    window.addEventListener(PLAYLISTS_UPDATED_EVENT, handleUpdate);
    return () => window.removeEventListener(PLAYLISTS_UPDATED_EVENT, handleUpdate);
  }, [load]);

  const content = useMemo(() => {
    if (playlist) {
      return <PlaylistDetailView playlist={playlist} />;
    }

    if (loading) {
      return (
        <section className="rounded-[28px] border border-white/[0.05] bg-[#070a11]/84 p-6 text-sm leading-7 text-slate-300/78 sm:p-8 sm:text-base">
          Loading your playlist...
        </section>
      );
    }

    return (
      <section className="space-y-4 rounded-[28px] border border-white/[0.05] bg-[#070a11]/84 p-6 text-sm leading-7 text-slate-300/78 sm:p-8 sm:text-base">
        <p>{error || "This playlist could not be found on this device right now."}</p>
        <div>
          <Link
            href="/library"
            className="inline-flex rounded-full border border-white/10 bg-white/[0.04] px-4 py-2 text-[11px] uppercase tracking-[0.18em] text-slate-200 transition hover:bg-white/[0.08]"
          >
            Back to library
          </Link>
        </div>
      </section>
    );
  }, [error, loading, playlist]);

  return <div className="space-y-6">{content}</div>;
}
