"use client";

import Image from "next/image";
import { memo, useCallback } from "react";

import { audioController } from "@/lib/audio-controller";
import type { Playlist } from "@/lib/types";

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
    <section className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-[11px] uppercase tracking-[0.3em] text-slate-400">Shared playlist</p>
          <h2 className="mt-3 text-2xl font-semibold tracking-tight text-white sm:text-3xl">{playlist.name}</h2>
          <p className="mt-3 text-sm leading-7 text-slate-300/75">
            Shared with your username{playlist.ownerName ? ` by ${playlist.ownerName}` : ""}. Tap any song to play it in your player.
          </p>
        </div>
        <div className="flex flex-wrap gap-3">
          <button
            type="button"
            onClick={() => handlePlayTrack(0)}
            disabled={!playlist.tracks.length}
            className="rounded-full border border-white/10 bg-white px-5 py-3 text-sm font-medium text-slate-950 transition hover:bg-teal-300 disabled:cursor-not-allowed disabled:bg-white/20 disabled:text-white/60"
          >
            {playlist.tracks.length ? "Play playlist" : "No songs yet"}
          </button>
          <div className="rounded-full border border-white/10 bg-white/[0.05] px-5 py-3 text-sm font-medium text-white">
            In your shared playlists
          </div>
        </div>
      </div>

      {playlist.tracks.length === 0 ? (
        <div className="glass-panel rounded-[28px] p-6 text-sm text-slate-300/75">This playlist does not have any songs yet.</div>
      ) : (
        <div className="space-y-3">
          {playlist.tracks.map((track, index) => (
            <button
              key={`${playlist.id}-${track.id}-${index}`}
              type="button"
              onClick={() => handlePlayTrack(index)}
              className="glass-panel flex w-full items-center gap-4 rounded-[26px] p-3 text-left transition hover:border-teal-300/20 hover:bg-white/[0.08] sm:p-4"
            >
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
        </div>
      )}
    </section>
  );
}

const SharedPlaylistView = memo(SharedPlaylistViewComponent);

export default SharedPlaylistView;
