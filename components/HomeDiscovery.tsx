"use client";

import { memo, useCallback, useEffect, useMemo, useState } from "react";

import { audioController } from "@/lib/audio-controller";
import type { Song, TrendingSection } from "@/lib/types";
import { usePlayerStore } from "@/store/playerStore";

import LibrarySection from "@/components/LibrarySection";
import ArtworkImage from "@/components/ArtworkImage";
import QuickPickRow from "@/components/QuickPickRow";
import SongGrid from "@/components/SongGrid";

type HomeDiscoveryProps = {
  sections: TrendingSection[];
};

const CATEGORY_THEME_MAP: Record<
  string,
  {
    eyebrow: string;
    overlayClassName: string;
    accentClassName: string;
  }
> = {
  bollywood: {
    eyebrow: "For Hindi moods",
    overlayClassName: "from-orange-300/24 via-[#070b15]/62 to-[#02030a]",
    accentClassName: "bg-orange-300/18 text-orange-100 border-orange-200/22"
  },
  "english-global": {
    eyebrow: "Global mood picks",
    overlayClassName: "from-sky-300/22 via-[#07101a]/64 to-[#02030a]",
    accentClassName: "bg-sky-300/18 text-sky-100 border-sky-200/22"
  },
  punjabi: {
    eyebrow: "Punjabi right now",
    overlayClassName: "from-fuchsia-300/22 via-[#090c17]/66 to-[#02030a]",
    accentClassName: "bg-fuchsia-300/18 text-fuchsia-100 border-fuchsia-200/22"
  },
  dogri: {
    eyebrow: "Dogri spotlight",
    overlayClassName: "from-amber-200/20 via-[#080c15]/66 to-[#02030a]",
    accentClassName: "bg-amber-200/16 text-amber-50 border-amber-100/18"
  },
  "lofi-chill": {
    eyebrow: "Ease into it",
    overlayClassName: "from-cyan-200/18 via-[#08111d]/66 to-[#02030a]",
    accentClassName: "bg-cyan-200/16 text-cyan-50 border-cyan-100/18"
  },
  "love-romantic": {
    eyebrow: "Warm and close",
    overlayClassName: "from-rose-200/22 via-[#110913]/64 to-[#02030a]",
    accentClassName: "bg-rose-200/16 text-rose-50 border-rose-100/18"
  },
  "sad-emotional": {
    eyebrow: "Lower and slower",
    overlayClassName: "from-indigo-200/22 via-[#070b18]/68 to-[#02030a]",
    accentClassName: "bg-indigo-200/16 text-indigo-50 border-indigo-100/18"
  },
  "high-energy-workout": {
    eyebrow: "Push harder",
    overlayClassName: "from-red-300/22 via-[#12070a]/62 to-[#02030a]",
    accentClassName: "bg-red-300/16 text-red-50 border-red-100/18"
  },
  "trending-viral": {
    eyebrow: "Moving fast",
    overlayClassName: "from-lime-200/18 via-[#07110f]/64 to-[#02030a]",
    accentClassName: "bg-lime-200/16 text-lime-50 border-lime-100/18"
  }
};

function HomeDiscoveryComponent({ sections }: HomeDiscoveryProps) {
  const currentSongId = usePlayerStore((state) => state.currentSong?.id || null);
  const isPlaying = usePlayerStore((state) => state.isPlaying);
  const availableSections = useMemo(() => sections.filter((section) => section.tracks.length > 0), [sections]);
  const [activeSectionId, setActiveSectionId] = useState(availableSections[0]?.id ?? "");

  useEffect(() => {
    if (!availableSections.length) {
      setActiveSectionId("");
      return;
    }

    setActiveSectionId((previous) =>
      previous && availableSections.some((section) => section.id === previous) ? previous : availableSections[0].id
    );
  }, [availableSections]);

  const activeSection = useMemo(
    () => availableSections.find((section) => section.id === activeSectionId) || availableSections[0] || null,
    [activeSectionId, availableSections]
  );
  const theme = activeSection ? CATEGORY_THEME_MAP[activeSection.id] || CATEGORY_THEME_MAP["english-global"] : null;
  const mixedTracks = useMemo(() => activeSection?.tracks.slice(0, 8) || [], [activeSection]);
  const quickPicks = useMemo(() => activeSection?.tracks.slice(0, 4) || [], [activeSection]);
  const supportingSections = useMemo(
    () => availableSections.filter((section) => section.id !== activeSection?.id).slice(0, 4),
    [activeSection?.id, availableSections]
  );

  const playTrack = useCallback(
    (song: Song) => {
      if (!activeSection) {
        return;
      }

      if (song.id === currentSongId) {
        void audioController.togglePlayback();
        return;
      }

      const queueIndex = activeSection.tracks.findIndex((track) => track.id === song.id);
      void audioController.playSong(song, activeSection.tracks, Math.max(queueIndex, 0));
    },
    [activeSection, currentSongId]
  );

  const playAll = useCallback(() => {
    if (!activeSection?.tracks.length) {
      return;
    }

    void audioController.playSong(activeSection.tracks[0], activeSection.tracks, 0);
  }, [activeSection]);

  if (!activeSection) {
    return (
      <section className="glass-panel rounded-[28px] p-6 text-sm leading-7 text-slate-300/78 sm:p-8 sm:text-base">
        Music is loading. You can use the search page while the home feed fills in.
      </section>
    );
  }

  const heroArtwork = activeSection.tracks[0]?.thumbnail;

  return (
    <div className="space-y-8">
      <section className="relative overflow-hidden rounded-[28px] border border-white/[0.08] bg-[#060a12] shadow-[0_30px_90px_rgba(0,0,0,0.34)] sm:rounded-[34px]">
        {heroArtwork ? (
          <>
            <ArtworkImage
              src={heroArtwork}
              alt={activeSection.title}
              fill
              sizes="100vw"
              quality={72}
              priority
              className="object-cover object-center opacity-34 blur-[3px] saturate-[1.08]"
            />
            <div className={`absolute inset-0 bg-gradient-to-b ${theme?.overlayClassName || "from-white/10 via-[#08101b]/66 to-[#02030a]"}`} />
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.12),transparent_28%),linear-gradient(180deg,rgba(2,3,10,0.05),rgba(2,3,10,0.72))]" />
          </>
        ) : (
          <div className={`absolute inset-0 bg-gradient-to-b ${theme?.overlayClassName || "from-white/10 via-[#08101b]/66 to-[#02030a]"}`} />
        )}

        <div className="relative space-y-5 px-4 py-5 sm:space-y-6 sm:px-6 sm:py-6">
          <div className="-mx-1 flex snap-x snap-mandatory gap-2 overflow-x-auto px-1 pb-1 scrollbar-none">
            {availableSections.map((section) => {
              const isActive = section.id === activeSection.id;

              return (
                <button
                  key={section.id}
                  type="button"
                  onClick={() => setActiveSectionId(section.id)}
                  className={[
                    "snap-start whitespace-nowrap rounded-full border px-4 py-2 text-[12px] font-medium transition",
                    isActive
                      ? "border-white/70 bg-white text-slate-950 shadow-[0_12px_32px_rgba(255,255,255,0.14)]"
                      : "border-white/10 bg-white/12 text-white/88 backdrop-blur-md hover:bg-white/18"
                  ].join(" ")}
                >
                  {section.title}
                </button>
              );
            })}
          </div>

          <div className="space-y-2">
            <div
              className={[
                "inline-flex items-center rounded-full border px-3 py-1 text-[10px] uppercase tracking-[0.24em]",
                theme?.accentClassName || "border-white/12 bg-white/10 text-white/82"
              ].join(" ")}
            >
              {theme?.eyebrow || "For you"}
            </div>
            <div className="max-w-2xl space-y-2">
              <h1 className="text-[32px] font-semibold leading-[0.98] tracking-tight text-white sm:text-[42px]">
                {activeSection.title}
              </h1>
              <p className="max-w-xl text-[15px] leading-6 text-slate-100/78 sm:text-base">{activeSection.blurb}</p>
            </div>
          </div>

          <div className="space-y-3">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-[10px] uppercase tracking-[0.28em] text-white/56">Now leaning toward</p>
                <h2 className="text-[28px] font-semibold tracking-tight text-white">Mixed for you</h2>
              </div>
            </div>

            <div className="horizontal-card-rail !pb-1">
              {mixedTracks.map((track) => (
                <button
                  key={track.id}
                  type="button"
                  onClick={() => playTrack(track)}
                  className="group relative overflow-hidden rounded-[26px] border border-white/10 bg-[#0a101a]/90 text-left shadow-[0_18px_40px_rgba(0,0,0,0.3)]"
                >
                  <div className="relative aspect-[0.92] overflow-hidden">
                    <ArtworkImage
                      src={track.thumbnail}
                      alt={track.title}
                      fill
                      sizes="(max-width: 640px) 72vw, 340px"
                      quality={72}
                      className="object-cover transition duration-500 group-hover:scale-[1.03]"
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/18 to-transparent" />
                    <div className="absolute left-3 top-3 rounded-full border border-white/10 bg-black/36 px-2.5 py-1 text-[9px] uppercase tracking-[0.24em] text-white/74 backdrop-blur-md">
                      Mix
                    </div>
                    {track.id === currentSongId ? (
                      <div className="absolute bottom-3 left-3 rounded-full border border-teal-300/35 bg-teal-300/16 px-2.5 py-1 text-[9px] uppercase tracking-[0.22em] text-teal-100">
                        {isPlaying ? "Playing" : "Selected"}
                      </div>
                    ) : null}
                    <div className="absolute inset-x-0 bottom-0 p-3">
                      <p className="line-clamp-2 text-[25px] font-semibold leading-[0.92] tracking-tight text-white">{track.title}</p>
                    </div>
                  </div>
                  <div className="space-y-1 p-3">
                    <p className="line-clamp-1 text-[15px] font-medium text-white">{track.title}</p>
                    <p className="line-clamp-1 text-sm text-slate-300/76">{track.author}</p>
                  </div>
                </button>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="space-y-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-[28px] font-semibold tracking-tight text-white">Quick picks</h2>
            <p className="text-[14px] text-slate-300/72">Start fast, then let the mood keep moving.</p>
          </div>
          <button
            type="button"
            onClick={playAll}
            className="inline-flex h-6 items-center justify-center rounded-full border border-white/[0.08] bg-white/[0.04] px-2 text-[8px] font-medium uppercase tracking-[0.08em] text-slate-100/92 transition hover:bg-white/[0.08] sm:h-6.5 sm:px-2.5 sm:text-[8px]"
          >
            Play all
          </button>
        </div>

        <div className="glass-panel-strong rounded-[28px] p-2.5 sm:p-3">
          {quickPicks.map((track) => {
            return (
              <QuickPickRow
                key={track.id}
                song={track}
                queue={activeSection.tracks}
                currentSongId={currentSongId}
                isPlaying={isPlaying}
                onPlay={(selectedTrack) => playTrack(selectedTrack)}
                variant="large"
              />
            );
          })}
        </div>
      </section>

      <LibrarySection />

      {supportingSections.map((section, index) => (
        <SongGrid
          key={section.id}
          title={index === 0 ? "More like this" : section.title}
          subtitle={index === 0 ? `${section.title} with a broader curve.` : section.blurb}
          tracks={section.tracks}
          prefetchCount={1}
        />
      ))}
    </div>
  );
}

const HomeDiscovery = memo(HomeDiscoveryComponent);

export default HomeDiscovery;
