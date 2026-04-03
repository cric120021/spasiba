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
    <div className="space-y-6">
      <section className="overflow-hidden rounded-[24px] border border-white/8 bg-[linear-gradient(180deg,rgba(12,18,30,0.94),rgba(8,12,20,0.94))] shadow-[0_20px_60px_rgba(0,0,0,0.24)] sm:rounded-[28px]">
        <div className="flex flex-col gap-0 md:flex-row">
          <div className="relative aspect-square bg-white/[0.04] md:w-[228px] md:min-w-[228px]">
            {coverTrack ? (
              <>
                <Image src={coverTrack.thumbnail} alt={playlist.name} fill sizes="(max-width: 768px) 100vw, 228px" quality={75} className="object-cover" />
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
              <h1 className="mt-3 text-[28px] font-semibold tracking-tight text-white sm:text-[38px]">{playlist.name}</h1>
              <p className="mt-3 max-w-xl text-[13px] leading-6 text-slate-300/70 sm:text-[14px]">
                Tap any song to start from there, or play the whole sequence from the top.
              </p>
            </div>

            <div className="mt-6 flex flex-wrap items-center gap-2.5">
              <button
                type="button"
                onClick={() => handlePlayTrack(0)}
                disabled={!playlist.tracks.length}
                className="rounded-full border border-white/12 bg-white px-4 py-2 text-[13px] font-medium text-slate-950 transition hover:bg-teal-300 disabled:cursor-not-allowed disabled:bg-white/20 disabled:text-white/60"
              >
                {playlist.tracks.length ? "Play playlist" : "Empty playlist"}
              </button>
              <div className="rounded-full border border-white/10 bg-white/[0.04] px-3.5 py-2 text-[10px] uppercase tracking-[0.18em] text-slate-300">
                {playlist.trackCount} songs
              </div>
            </div>
          </div>
        </div>
      </section>

      {playlist.tracks.length === 0 ? (
        <section className="glass-panel rounded-[24px] p-5 text-sm text-slate-300/75">This playlist does not have any songs yet.</section>
      ) : (
        <section className="space-y-2.5">
          {playlist.tracks.map((track, index) => (
            <button
              key={`${playlist.id}-${track.id}-${index}`}
              type="button"
              onClick={() => handlePlayTrack(index)}
              className="glass-panel flex w-full items-center gap-3 rounded-[20px] p-2.5 text-left transition hover:border-teal-300/20 hover:bg-white/[0.08] sm:p-3"
            >
              <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-white/[0.06] text-[11px] font-semibold text-slate-300">
                {index + 1}
              </div>
              <div className="relative h-14 w-14 shrink-0 overflow-hidden rounded-[16px] border border-white/10 bg-white/[0.04]">
                <Image src={track.thumbnail} alt={track.title} fill sizes="56px" quality={75} className="object-cover" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="line-clamp-1 text-[14px] font-medium text-white sm:text-[15px]">{track.title}</p>
                <p className="mt-1 line-clamp-1 text-[11px] uppercase tracking-[0.16em] text-slate-400">{track.author}</p>
              </div>
              <span className="shrink-0 text-[11px] uppercase tracking-[0.18em] text-slate-300">{track.duration}</span>
            </button>
          ))}
        </section>
      )}
    </div>
  );
}

const PlaylistDetailView = memo(PlaylistDetailViewComponent);

export default PlaylistDetailView;
