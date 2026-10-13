"use client";

import Link from "next/link";
import { memo, useCallback, useMemo, useState } from "react";

import { audioController } from "@/lib/audio-controller";
import type { Playlist } from "@/lib/types";
import { emitPlaylistsUpdatedEvent } from "@/lib/playlist-events";
import { upsertCachedOwnedPlaylist } from "@/lib/playlists-client";
import { playerActions } from "@/store/playerStore";

import ArtworkImage from "@/components/ArtworkImage";
import SwipeActionTrackRow from "@/components/SwipeActionTrackRow";

type PlaylistDetailViewProps = {
  playlist: Playlist;
};

function getStableCoverTrack(playlist: Playlist) {
  if (!playlist.tracks.length) {
    return null;
  }

  let hash = 0;

  for (const character of `${playlist.id}:${playlist.updatedAt}`) {
    hash = (hash * 31 + character.charCodeAt(0)) | 0;
  }

  return playlist.tracks[Math.abs(hash) % playlist.tracks.length] || playlist.tracks[0];
}

function PlaylistDetailViewComponent({ playlist }: PlaylistDetailViewProps) {
  const [tracks, setTracks] = useState(playlist.tracks);
  const playlistView = useMemo(
    () => ({
      ...playlist,
      trackCount: tracks.length,
      tracks
    }),
    [playlist, tracks]
  );
  const coverTrack = useMemo(() => getStableCoverTrack(playlistView), [playlistView]);

  const handlePlayTrack = useCallback(
    (index: number) => {
      const track = tracks[index];

      if (!track) {
        return;
      }

      void audioController.playSong(track, tracks, index, {
        queueSource: "playlist",
        queueSourceLabel: playlistView.name,
        playlistBaseTrackIds: tracks.map((playlistTrack) => playlistTrack.id),
        playlistMixEnabled: false
      });
    },
    [playlistView.name, tracks]
  );

  const handleRemoveTrack = useCallback(
    async (videoId: string, title: string) => {
      const response = await fetch(`/api/playlists/${encodeURIComponent(playlistView.id)}/tracks?videoId=${encodeURIComponent(videoId)}`, {
        method: "DELETE",
        credentials: "same-origin",
        headers: {
          Accept: "application/json"
        }
      });
      const payload = (await response.json().catch(() => null)) as { error?: string; playlist?: Playlist | null } | null;

      if (!response.ok) {
        throw new Error(payload?.error || "Unable to remove this song from the playlist.");
      }

      setTracks((previous) => previous.filter((track) => track.videoId !== videoId));
      if (payload?.playlist) {
        upsertCachedOwnedPlaylist(payload.playlist, { emit: false });
      }
      emitPlaylistsUpdatedEvent();
      playerActions.showToast(`Removed "${title}" from ${playlistView.name}.`);
    },
    [playlistView.id, playlistView.name]
  );

  return (
    <div className="space-y-6">
      <section className="overflow-hidden rounded-[24px] border border-white/8 bg-[linear-gradient(180deg,rgba(12,18,30,0.94),rgba(8,12,20,0.94))] shadow-[0_20px_60px_rgba(0,0,0,0.24)] sm:rounded-[28px]">
        <div className="flex flex-col gap-0 md:flex-row">
          <div className="relative aspect-square bg-white/[0.04] md:w-[228px] md:min-w-[228px]">
            {coverTrack ? (
              <>
                <ArtworkImage src={coverTrack.thumbnail} alt={playlistView.name} fill sizes="(max-width: 768px) 100vw, 228px" quality={75} className="object-cover" />
                <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/20 to-transparent" />
              </>
            ) : (
              <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(74,222,208,0.28),transparent_55%),linear-gradient(180deg,#0a1020,#050816)]" />
            )}
          </div>

          <div className="flex flex-1 flex-col justify-between p-5 sm:p-6 lg:p-7">
            <div>
              <Link
                href="/library"
                className="inline-flex rounded-full border border-white/10 bg-white/[0.04] px-3.5 py-1.5 text-[10px] uppercase tracking-[0.18em] text-slate-300 transition hover:bg-white/[0.08]"
              >
                Back to library
              </Link>
              <p className="mt-5 text-[10px] uppercase tracking-[0.26em] text-slate-400">Playlist</p>
              <h1 className="mt-3 text-[28px] font-semibold tracking-tight text-white sm:text-[38px]">{playlistView.name}</h1>
              <p className="mt-3 max-w-xl text-[13px] leading-6 text-slate-300/70 sm:text-[14px]">
                Tap any song to start from there, or swipe left on a row to remove it from this playlist.
              </p>
            </div>

            <div className="mt-6 flex flex-wrap items-center gap-2.5">
              <button
                type="button"
                onClick={() => handlePlayTrack(0)}
                disabled={!tracks.length}
                className="rounded-full border border-white/12 bg-white px-4 py-2 text-[13px] font-medium text-slate-950 transition hover:bg-teal-300 disabled:cursor-not-allowed disabled:bg-white/20 disabled:text-white/60"
              >
                {tracks.length ? "Play playlist" : "Empty playlist"}
              </button>
              <div className="rounded-full border border-white/10 bg-white/[0.04] px-3.5 py-2 text-[10px] uppercase tracking-[0.18em] text-slate-300">
                {tracks.length} songs
              </div>
            </div>
          </div>
        </div>
      </section>

      {tracks.length === 0 ? (
        <section className="glass-panel rounded-[24px] p-5 text-sm text-slate-300/75">This playlist does not have any songs yet.</section>
      ) : (
        <section className="space-y-2.5">
          {tracks.map((track, index) => (
            <SwipeActionTrackRow
              key={`${playlistView.id}-${track.id}-${index}`}
              song={track}
              leadingSlot={index + 1}
              actionLabel="Remove"
              actionBusyLabel="Removing..."
              actionAriaLabel={`Remove ${track.title} from ${playlistView.name}`}
              onPlay={() => handlePlayTrack(index)}
              onAction={() => handleRemoveTrack(track.videoId, track.title)}
            />
          ))}
        </section>
      )}
    </div>
  );
}

const PlaylistDetailView = memo(PlaylistDetailViewComponent);

export default PlaylistDetailView;
