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
    <section className="deferred-section space-y-2.5">
      <div className="space-y-0.5">
        <h2 className="text-[20px] font-semibold tracking-tight text-white sm:text-[23px]">{title}</h2>
        {subtitle ? <p className="max-w-xl text-[10px] leading-4 text-slate-300/46 sm:text-[12px]">{subtitle}</p> : null}
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
