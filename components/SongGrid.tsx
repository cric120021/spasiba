"use client";

import { memo, useCallback, useEffect, useMemo } from "react";

import { audioController } from "@/lib/audio-controller";
import type { Song } from "@/lib/types";
import { scheduleIdleTask } from "@/lib/utils";
import { usePlayerStore } from "@/store/playerStore";

import SongCard from "@/components/SongCard";

type SongGridProps = {
  title: string;
  subtitle?: string;
  tracks: Song[];
  prefetchCount?: number;
};

function SongGridComponent({ title, subtitle, tracks, prefetchCount = 1 }: SongGridProps) {
  const currentSongId = usePlayerStore((state) => state.currentSong?.id || null);
  const isPlaying = usePlayerStore((state) => state.isPlaying);

  const warmupList = useMemo(() => tracks.slice(0, prefetchCount), [tracks, prefetchCount]);

  useEffect(() => {
    if (!warmupList.length) {
      return;
    }

    audioController.seedTracks(warmupList);
  }, [warmupList]);

  useEffect(() => {
    if (!warmupList.length) {
      return;
    }

    const warmup = () => {
      warmupList.forEach((track) => {
        void audioController.prepareTrack(track);
      });
    };

    return scheduleIdleTask(warmup, 250);
  }, [warmupList]);
  const playTrack = useCallback(
    (selectedTrack: Song) => {
      if (selectedTrack.id === currentSongId) {
        void audioController.togglePlayback();
        return;
      }

      void audioController.playSong(selectedTrack, [selectedTrack], 0);
    },
    [currentSongId]
  );

  const hoverTrack = useCallback((selectedTrack: Song) => {
    void audioController.prepareTrack(selectedTrack);
  }, []);

  return (
    <section className="deferred-section space-y-3.5">
      <div className="space-y-1">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-[21px] font-semibold tracking-tight text-white sm:text-[25px]">{title}</h2>
          <span className="rounded-full border border-white/8 bg-white/[0.03] px-2.5 py-1 text-[9px] uppercase tracking-[0.18em] text-slate-300/72">
            Curated
          </span>
        </div>
        {subtitle ? <p className="max-w-xl text-[12px] leading-5 text-slate-300/62 sm:text-[14px]">{subtitle}</p> : null}
      </div>

      <div className="deferred-grid song-card-rail">
        {tracks.map((track) => (
          <SongCard
            key={track.id}
            song={track}
            isActive={track.id === currentSongId}
            isPlaying={track.id === currentSongId && isPlaying}
            onPlay={playTrack}
            onHover={hoverTrack}
          />
        ))}
      </div>
    </section>
  );
}

const SongGrid = memo(SongGridComponent);

export default SongGrid;
