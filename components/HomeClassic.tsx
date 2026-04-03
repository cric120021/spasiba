"use client";

import Image from "next/image";
import Link from "next/link";
import { useCallback, useMemo } from "react";

import { audioController } from "@/lib/audio-controller";
import type { Song, TrendingSection } from "@/lib/types";
import { formatCompactCount } from "@/lib/utils";
import { usePlayerStore } from "@/store/playerStore";

import LibrarySection from "@/components/LibrarySection";
import SongGrid from "@/components/SongGrid";

type HomeClassicProps = {
  sections: TrendingSection[];
};

export default function HomeClassic({ sections }: HomeClassicProps) {
  const availableSections = sections.filter((section) => section.tracks.length > 0);
  const [featuredSection, ...remainingSections] = availableSections;
  const currentSongId = usePlayerStore((state) => state.currentSong?.id || null);
  const isPlaying = usePlayerStore((state) => state.isPlaying);
  const quickPicks = useMemo(() => featuredSection?.tracks.slice(0, 12) || [], [featuredSection]);
  const mixedTracks = useMemo(() => featuredSection?.tracks.slice(0, 6) || [], [featuredSection]);
  const quickPickColumns = useMemo(() => {
    const columns: Song[][] = [];

    for (let index = 0; index < quickPicks.length; index += 4) {
      columns.push(quickPicks.slice(index, index + 4));
    }

    return columns;
  }, [quickPicks]);

  const playFeaturedTrack = useCallback(
    (song: Song) => {
      if (!featuredSection) {
        return;
      }

      if (song.id === currentSongId) {
        void audioController.togglePlayback();
        return;
      }

      const queueIndex = featuredSection.tracks.findIndex((track) => track.id === song.id);
      void audioController.playSong(song, featuredSection.tracks, Math.max(queueIndex, 0));
    },
    [currentSongId, featuredSection]
  );

  const playAllQuickPicks = useCallback(() => {
    if (!featuredSection?.tracks.length) {
      return;
    }

    void audioController.playSong(featuredSection.tracks[0], featuredSection.tracks, 0);
  }, [featuredSection]);

  return (
    <div className="space-y-7">
      <section className="-mx-1 flex snap-x snap-mandatory gap-2 overflow-x-auto px-1 pb-1 scrollbar-none sm:mx-0 sm:px-0">
        {sections.map((section) => (
          <Link
            key={section.id}
            href={`/search?q=${encodeURIComponent(section.query)}`}
            className="snap-start whitespace-nowrap rounded-full border border-white/[0.08] bg-white/[0.06] px-3 py-1.5 text-[11px] text-slate-100 transition hover:border-teal-300/35 hover:bg-white/10 sm:px-3.5 sm:py-2 sm:text-sm"
          >
            {section.title}
          </Link>
        ))}
      </section>

      {quickPickColumns.length > 0 ? (
        <section className="space-y-3.5">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="text-[24px] font-semibold tracking-tight text-white sm:text-[26px]">Quick picks</h2>
              <p className="text-[13px] text-slate-300/68">Start with the strongest suggestions first.</p>
            </div>
            <button
              type="button"
              onClick={playAllQuickPicks}
              className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-1 text-[9px] font-medium uppercase tracking-[0.18em] text-slate-100 transition hover:bg-white/[0.08]"
            >
              Play all
            </button>
          </div>

          <div className="horizontal-card-rail !gap-2.5 !px-0 !pb-1">
            {quickPickColumns.map((column, columnIndex) => (
              <div
                key={`quick-picks-${columnIndex}`}
                className="flex flex-col gap-2 rounded-[20px] border border-transparent bg-transparent p-1"
              >
                {column.map((track) => {
                  const isCurrent = track.id === currentSongId;

                  return (
                    <button
                      key={track.id}
                      type="button"
                      onClick={() => playFeaturedTrack(track)}
                      className="flex w-full items-center gap-3 rounded-[16px] px-2.5 py-2 text-left transition hover:bg-white/[0.05]"
                    >
                      <div className="relative h-12 w-12 shrink-0 overflow-hidden rounded-[14px] bg-white/[0.05]">
                        <Image src={track.thumbnail} alt={track.title} fill sizes="48px" quality={70} className="object-cover" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="line-clamp-1 text-[14px] font-medium text-white">{track.title}</p>
                        <p className="line-clamp-1 text-[12px] text-slate-300/76">
                          {track.author} <span className="text-slate-400">• {formatCompactCount(track.views)}</span>
                        </p>
                      </div>
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] text-white/88">
                        {isCurrent && isPlaying ? (
                          <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4">
                            <path fill="currentColor" d="M7 5h3v14H7zm7 0h3v14h-3z" />
                          </svg>
                        ) : (
                          <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4">
                            <path fill="currentColor" d="M8 5.14v13.72a1 1 0 0 0 1.53.848l10.08-6.86a1 1 0 0 0 0-1.696L9.53 4.292A1 1 0 0 0 8 5.14" />
                          </svg>
                        )}
                      </div>
                    </button>
                  );
                })}
              </div>
            ))}
          </div>
        </section>
      ) : null}

      {mixedTracks.length > 0 ? (
        <section className="space-y-3.5">
          <div>
            <h2 className="text-[24px] font-semibold tracking-tight text-white sm:text-[26px]">Mixed for you</h2>
            <p className="text-[13px] text-slate-300/68">Smaller art-led mixes, closer to a real music app shelf.</p>
          </div>

          <div className="horizontal-card-rail !gap-3 !px-0 !pb-1">
            {mixedTracks.map((track) => (
              <button
                key={track.id}
                type="button"
                onClick={() => playFeaturedTrack(track)}
                className="group relative flex-[0_0_clamp(164px,46vw,208px)] overflow-hidden rounded-[20px] border border-white/[0.08] bg-[#0a101a]/92 text-left shadow-[0_14px_34px_rgba(0,0,0,0.28)]"
              >
                <div className="relative aspect-square overflow-hidden">
                  <Image
                    src={track.thumbnail}
                    alt={track.title}
                    fill
                    unoptimized
                    sizes="(max-width: 640px) 46vw, 208px"
                    quality={72}
                    className="object-cover transition duration-500 group-hover:scale-[1.03]"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/18 to-transparent" />
                  <div className="absolute left-2.5 top-2.5 rounded-full border border-white/10 bg-black/40 px-2 py-1 text-[8px] uppercase tracking-[0.24em] text-white/74 backdrop-blur-md">
                    Mix
                  </div>
                  {track.id === currentSongId ? (
                    <div className="absolute bottom-2.5 left-2.5 rounded-full border border-teal-300/35 bg-teal-300/16 px-2 py-1 text-[8px] uppercase tracking-[0.22em] text-teal-100">
                      {isPlaying ? "Playing" : "Selected"}
                    </div>
                  ) : null}
                </div>
                <div className="space-y-1 p-2.5">
                  <p className="line-clamp-1 text-[14px] font-medium text-white">{track.title}</p>
                  <p className="line-clamp-1 text-[12px] text-slate-300/74">{track.author}</p>
                </div>
              </button>
            ))}
          </div>
        </section>
      ) : null}

      <LibrarySection />

      {availableSections.length > 0 ? (
        remainingSections.map((section) => (
          <SongGrid
            key={section.id}
            title={section.title}
            subtitle=""
            tracks={section.tracks}
            prefetchCount={1}
          />
        ))
      ) : (
        <section className="glass-panel rounded-[32px] p-6 text-sm leading-7 text-slate-300/78 sm:p-8 sm:text-base">
          Music is loading. You can use the search bar above while the home feed fills in.
        </section>
      )}
    </div>
  );
}
