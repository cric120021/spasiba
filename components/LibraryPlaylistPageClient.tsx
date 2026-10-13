"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import PlaylistDetailView from "@/components/PlaylistDetailView";
import { PLAYLISTS_UPDATED_EVENT } from "@/lib/playlist-events";
import { fetchPlaylistsLibrary, getCachedPlaylistById } from "@/lib/playlists-client";
import type { Playlist } from "@/lib/types";

type LibraryPlaylistPageClientProps = {
  playlistId: string;
};

export default function LibraryPlaylistPageClient({ playlistId }: LibraryPlaylistPageClientProps) {
  const [playlist, setPlaylist] = useState<Playlist | null>(() => getCachedPlaylistById(playlistId));
  const [loading, setLoading] = useState(() => getCachedPlaylistById(playlistId) === null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (options?: { force?: boolean }) => {
      const force = options?.force ?? false;

      if (!force) {
        const cachedPlaylist = getCachedPlaylistById(playlistId);

        if (cachedPlaylist) {
          setPlaylist(cachedPlaylist);
          setLoading(false);
          setError(null);
          return;
        }
      }

      setLoading((previous) => previous || getCachedPlaylistById(playlistId) === null);
      setError(null);

      try {
        const library = await fetchPlaylistsLibrary({ force });
        const nextPlaylist = library.playlists.find((entry) => entry.id === playlistId) || null;

        if (!nextPlaylist) {
          throw new Error("Playlist not found.");
        }

        setPlaylist(nextPlaylist);
      } catch (requestError) {
        setError(requestError instanceof Error ? requestError.message : "Unable to load this playlist.");
      } finally {
        setLoading(false);
      }
    },
    [playlistId]
  );

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

  if (loading) {
    return (
      <section className="rounded-[28px] border border-white/[0.05] bg-[#070a11]/84 p-6 text-sm leading-7 text-slate-300/78 sm:p-8 sm:text-base">
        Loading your playlist...
      </section>
    );
  }

  if (!playlist) {
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
  }

  return <PlaylistDetailView playlist={playlist} />;
}
