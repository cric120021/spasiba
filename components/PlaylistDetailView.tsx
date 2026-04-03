"use client";

import Image from "next/image";
import Link from "next/link";
import { memo, useCallback, useMemo } from "react";

import { audioController } from "@/lib/audio-controller";
import type { Playlist } from "@/lib/types";

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
  const coverTrack = useMemo(() => getStableCoverTrack(playlist), [playlist]);

  const handlePlayTrack = useCallback(
    (index: number) => {
      const track = playlist.tracks[index];

      if (!track) {
        return;
      }

      void audioController.playSong(track, playlist.tracks, index, {
        queueSource: "playlist",
        queueSourceLabel: playlist.name,
        playlistBaseTrackIds: playlist.tracks.map((playlistTrack) => playlistTrack.id),
        playlistMixEnabled: false
      });
    },
    [playlist.name, playlist.tracks]
  );

  return (
    <div className="space-y-8">
      <section className="glass-panel-strong overflow-hidden rounded-[30px] sm:rounded-[36px]">
        <div className="grid gap-0 lg:grid-cols-[340px_minmax(0,1fr)]">
          <div className="relative aspect-square bg-white/[0.04] lg:aspect-auto">
            {coverTrack ? (
              <>
                <Image src={coverTrack.thumbnail} alt={playlist.name} fill sizes="(max-width: 1024px) 100vw, 340px" quality={75} className="object-cover" />
                <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/20 to-transparent" />
              </>
            ) : (
              <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(74,222,208,0.28),transparent_55%),linear-gradient(180deg,#0a1020,#050816)]" />
            )}
          </div>

          <div className="flex flex-col justify-between p-6 sm:p-8 lg:p-10">
            <div>
              <Link
                href="/library"
                className="inline-flex rounded-full border border-white/10 bg-white/[0.04] px-4 py-2 text-[11px] uppercase tracking-[0.24em] text-slate-300 transition hover:bg-white/[0.08]"
              >
                Back to library
              </Link>
              <p className="mt-6 text-[11px] uppercase tracking-[0.32em] text-slate-400">Playlist</p>
              <h1 className="mt-4 text-3xl font-semibold tracking-tight text-white sm:text-5xl">{playlist.name}</h1>
              <p className="mt-4 max-w-2xl text-sm leading-7 text-slate-300/78 sm:text-base">
                Tap any song to play it, or start the full playlist from the first track.
              </p>
            </div>

            <div className="mt-8 flex flex-wrap items-center gap-3">
              <button
                type="button"
                onClick={() => handlePlayTrack(0)}
                disabled={!playlist.tracks.length}
                className="rounded-full border border-white/12 bg-white px-5 py-3 text-sm font-medium text-slate-950 transition hover:bg-teal-300 disabled:cursor-not-allowed disabled:bg-white/20 disabled:text-white/60"
              >
                {playlist.tracks.length ? "Play playlist" : "Empty playlist"}
              </button>
              <div className="rounded-full border border-white/10 bg-white/[0.04] px-4 py-3 text-[11px] uppercase tracking-[0.24em] text-slate-300">
                {playlist.trackCount} songs
              </div>
            </div>
          </div>
        </div>
      </section>

      {playlist.tracks.length === 0 ? (
        <section className="glass-panel rounded-[28px] p-6 text-sm text-slate-300/75">This playlist does not have any songs yet.</section>
      ) : (
        <section className="space-y-3">
          {playlist.tracks.map((track, index) => (
            <button
              key={`${playlist.id}-${track.id}-${index}`}
              type="button"
              onClick={() => handlePlayTrack(index)}
              className="glass-panel flex w-full items-center gap-4 rounded-[24px] p-3 text-left transition hover:border-teal-300/20 hover:bg-white/[0.08] sm:p-4"
            >
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-white/[0.06] text-xs font-semibold text-slate-300">
                {index + 1}
              </div>
              <div className="relative h-16 w-16 shrink-0 overflow-hidden rounded-2xl border border-white/10 bg-white/[0.04]">
                <Image src={track.thumbnail} alt={track.title} fill sizes="64px" quality={75} className="object-cover" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="line-clamp-1 text-sm font-medium text-white sm:text-base">{track.title}</p>
                <p className="mt-1 line-clamp-1 text-xs uppercase tracking-[0.18em] text-slate-400 sm:text-[11px]">{track.author}</p>
              </div>
              <span className="shrink-0 text-xs uppercase tracking-[0.22em] text-slate-300">{track.duration}</span>
            </button>
          ))}
        </section>
      )}
    </div>
  );
}

const PlaylistDetailView = memo(PlaylistDetailViewComponent);

export default PlaylistDetailView;
