"use client";

import Link from "next/link";
import { memo, useCallback } from "react";

import { audioController } from "@/lib/audio-controller";
import type { Playlist } from "@/lib/types";

import ArtworkImage from "@/components/ArtworkImage";

type SharedPlaylistViewProps = {
  playlist: Playlist;
};

function SharedPlaylistViewComponent({ playlist }: SharedPlaylistViewProps) {
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
    <section className="space-y-6">
      <div className="overflow-hidden rounded-[24px] border border-sky-300/10 bg-[linear-gradient(180deg,rgba(10,18,32,0.94),rgba(7,12,22,0.94))] shadow-[0_20px_60px_rgba(0,0,0,0.24)] sm:rounded-[28px]">
        <div className="flex flex-col gap-0 md:flex-row">
          <div className="relative aspect-square bg-white/[0.04] md:w-[228px] md:min-w-[228px]">
            {playlist.coverTrack ? (
              <>
                <ArtworkImage src={playlist.coverTrack.thumbnail} alt={playlist.name} fill sizes="(max-width: 768px) 100vw, 228px" quality={75} className="object-cover" />
                <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/20 to-transparent" />
              </>
            ) : (
              <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(56,189,248,0.24),transparent_55%),linear-gradient(180deg,#0a1020,#050816)]" />
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
              <p className="mt-5 text-[10px] uppercase tracking-[0.26em] text-slate-400">Shared playlist</p>
              <h2 className="mt-3 text-[28px] font-semibold tracking-tight text-white sm:text-[38px]">{playlist.name}</h2>
              <p className="mt-3 text-[13px] leading-6 text-slate-300/70 sm:text-[14px]">
                Shared with your username{playlist.ownerName ? ` by ${playlist.ownerName}` : ""}. Tap any song to start from there.
              </p>
            </div>

            <div className="mt-6 flex flex-wrap gap-2.5">
              <button
                type="button"
                onClick={() => handlePlayTrack(0)}
                disabled={!playlist.tracks.length}
                className="rounded-full border border-white/10 bg-white px-4 py-2 text-[13px] font-medium text-slate-950 transition hover:bg-teal-300 disabled:cursor-not-allowed disabled:bg-white/20 disabled:text-white/60"
              >
                {playlist.tracks.length ? "Play playlist" : "No songs yet"}
              </button>
              <div className="rounded-full border border-white/10 bg-white/[0.05] px-3.5 py-2 text-[10px] uppercase tracking-[0.18em] text-white/88">
                In your shared playlists
              </div>
            </div>
          </div>
        </div>
      </div>

      {playlist.tracks.length === 0 ? (
        <div className="glass-panel rounded-[24px] p-5 text-sm text-slate-300/75">This playlist does not have any songs yet.</div>
      ) : (
        <div className="space-y-2.5">
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
                <ArtworkImage src={track.thumbnail} alt={track.title} fill sizes="56px" quality={75} className="object-cover" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="line-clamp-1 text-[14px] font-medium text-white sm:text-[15px]">{track.title}</p>
                <p className="mt-1 line-clamp-1 text-[11px] uppercase tracking-[0.16em] text-slate-400">{track.author}</p>
              </div>
              <span className="shrink-0 text-[11px] uppercase tracking-[0.18em] text-slate-300">{track.duration}</span>
            </button>
          ))}
        </div>
      )}
    </section>
  );
}

const SharedPlaylistView = memo(SharedPlaylistViewComponent);

export default SharedPlaylistView;
