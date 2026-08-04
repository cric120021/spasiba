"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import { audioController } from "@/lib/audio-controller";
import { getHomeCategoryConfig, HOME_CATEGORY_CHIPS } from "@/lib/home-category-chips";
import { fetchCategoryFeed, readCachedCategoryFeed } from "@/lib/home-category-feed-client";
import type { HomeCategoryFeed } from "@/lib/home-category-feed";
import type { Song, TrendingSection } from "@/lib/types";
import { formatCompactCount } from "@/lib/utils";
import { usePlayerStore } from "@/store/playerStore";

import ArtworkImage from "@/components/ArtworkImage";
import LibrarySection from "@/components/LibrarySection";
import SongGrid from "@/components/SongGrid";

function buildInstantCategoryFeed(categoryId: string, homeSections: TrendingSection[]): HomeCategoryFeed | null {
  const config = getHomeCategoryConfig(categoryId);
  if (!config) return null;

  const allTracks = homeSections.flatMap((s) => s.tracks);
  if (!allTracks.length) return null;

  const normalizedCat = categoryId.toLowerCase();
  const matchingTracks = allTracks.filter(
    (t) => t.title.toLowerCase().includes(normalizedCat) || t.author.toLowerCase().includes(normalizedCat)
  );

  const pool = matchingTracks.length >= 4 ? matchingTracks : allTracks;

  const sections = config.sections.map((sec, idx) => {
    const offset = (idx * 4) % pool.length;
    const sliced = pool.slice(offset, offset + 12);
    const finalTracks = sliced.length > 0 ? sliced : pool.slice(0, 8);

    return {
      ...sec,
      tracks: finalTracks
    };
  });

  return {
    ...config,
    sections
  };
}

type HomeClassicProps = {
  sections: TrendingSection[];
};

export default function HomeClassic({ sections }: HomeClassicProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const selectedCategoryId = searchParams.get("category");
  const activeCategoryConfig = useMemo(
    () => (selectedCategoryId ? getHomeCategoryConfig(selectedCategoryId) : null),
    [selectedCategoryId]
  );

  const [categoryFeed, setCategoryFeed] = useState<HomeCategoryFeed | null>(() =>
    selectedCategoryId ? readCachedCategoryFeed(selectedCategoryId) : null
  );

  const effectiveCategoryFeed = useMemo(() => {
    if (!selectedCategoryId || !activeCategoryConfig) {
      return null;
    }
    return (
      categoryFeed ||
      readCachedCategoryFeed(selectedCategoryId) ||
      buildInstantCategoryFeed(selectedCategoryId, sections)
    );
  }, [selectedCategoryId, activeCategoryConfig, categoryFeed, sections]);

  useEffect(() => {
    if (!selectedCategoryId || !activeCategoryConfig) {
      setCategoryFeed(null);
      return;
    }

    const cached = readCachedCategoryFeed(selectedCategoryId);
    if (cached) {
      setCategoryFeed(cached);
      return;
    }

    const controller = new AbortController();

    void fetchCategoryFeed(selectedCategoryId, { signal: controller.signal })
      .then((feed) => {
        if (feed) {
          setCategoryFeed(feed);
        }
      })
      .catch(() => {});

    return () => {
      controller.abort();
    };
  }, [selectedCategoryId, activeCategoryConfig]);

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

  const playCategoryTrack = useCallback(
    (song: Song, queue: Song[]) => {
      if (song.id === currentSongId) {
        void audioController.togglePlayback();
        return;
      }

      const queueIndex = queue.findIndex((track) => track.id === song.id);
      void audioController.playSong(song, queue, Math.max(queueIndex, 0));
    },
    [currentSongId]
  );

  const playAllQuickPicks = useCallback(() => {
    if (!featuredSection?.tracks.length) {
      return;
    }

    void audioController.playSong(featuredSection.tracks[0], featuredSection.tracks, 0);
  }, [featuredSection]);

  const prefetchCategoryPage = useCallback(
    (categoryId: string) => {
      router.prefetch(`/category/${categoryId}`);
    },
    [router]
  );

  useEffect(() => {
    const warmCategories = HOME_CATEGORY_CHIPS.slice(0, 3);
    const idleWindow = window as Window &
      typeof globalThis & {
        requestIdleCallback?: (callback: () => void, options?: { timeout: number }) => number;
      };

    const warm = () => {
      warmCategories.forEach((category) => {
        prefetchCategoryPage(category.id);
      });
    };

    if (idleWindow.requestIdleCallback) {
      idleWindow.requestIdleCallback(warm, { timeout: 1600 });
      return;
    }

    const timer = window.setTimeout(warm, 400);
    return () => window.clearTimeout(timer);
  }, [prefetchCategoryPage]);

  if (activeCategoryConfig) {
    const categorySections = effectiveCategoryFeed?.sections || [];

    return (
      <div
        className="category-page-shell home-page-shell"
        style={{
          ["--category-accent-rgb" as string]: activeCategoryConfig.headerRgb,
          ["--category-backdrop-image" as string]: `url('${activeCategoryConfig.backdropPath}')`
        }}
      >
        <section className="category-page-shell__hero sm:rounded-[34px]">
          <div className="category-page-shell__hero-content space-y-3">
            <p className="text-[10px] font-medium uppercase tracking-[0.26em] text-white/60">
              {activeCategoryConfig.eyebrow}
            </p>
            <h1 className="text-[28px] font-bold tracking-tight text-white sm:text-[34px]">
              {activeCategoryConfig.title}
            </h1>
          </div>
        </section>

        <div className="category-page-shell__feed space-y-7 sm:space-y-8">
          {categorySections.map((section) => (
              <section key={section.id} className="space-y-3">
                <div className="flex items-center justify-between gap-3">
                  <h2 className="text-[21px] font-semibold tracking-tight text-white sm:text-[23px]">
                    {section.title}
                  </h2>
                  {section.layout === "quick-picks" ? (
                    <button
                      type="button"
                      onClick={() => {
                        if (section.tracks.length > 0) {
                          playCategoryTrack(section.tracks[0], section.tracks);
                        }
                      }}
                      className="shrink-0 whitespace-nowrap rounded-full border border-white/[0.08] bg-white/[0.03] px-2.5 py-1 text-[8px] font-medium uppercase leading-none tracking-[0.14em] text-slate-100 transition hover:bg-white/[0.06] sm:px-3 sm:text-[9px]"
                    >
                      Play all
                    </button>
                  ) : null}
                </div>

                {section.layout === "quick-picks" ? (
                  <div className="horizontal-card-rail !gap-2 !px-0 !pb-1">
                    {Array.from({ length: Math.ceil(section.tracks.length / 4) }).map((_, colIdx) => {
                      const colTracks = section.tracks.slice(colIdx * 4, colIdx * 4 + 4);

                      return (
                        <div key={`cat-quick-${colIdx}`} className="flex flex-col gap-1.5 min-w-[260px] sm:min-w-[300px]">
                          {colTracks.map((track) => {
                            const isCurrent = track.id === currentSongId;

                            return (
                              <button
                                key={track.id}
                                type="button"
                                onClick={() => playCategoryTrack(track, section.tracks)}
                                className="flex w-full items-center gap-3 rounded-[15px] px-1.5 py-1.5 text-left transition hover:bg-white/[0.04]"
                              >
                                <div className="relative h-11 w-11 shrink-0 overflow-hidden rounded-[13px] bg-slate-900">
                                  <ArtworkImage
                                    src={track.thumbnail}
                                    alt={track.title}
                                    fill
                                    sizes="44px"
                                    quality={70}
                                    className="object-cover scale-[1.03]"
                                  />
                                </div>
                                <div className="min-w-0 flex-1">
                                  <p className="line-clamp-1 text-[13px] font-medium text-white">{track.title}</p>
                                  <p className="line-clamp-1 text-[11px] text-slate-300/72">{track.author}</p>
                                </div>
                                <div className="flex h-7.5 w-7.5 shrink-0 items-center justify-center rounded-full bg-white/[0.04] text-white/86">
                                  {isCurrent && isPlaying ? (
                                    <svg aria-hidden viewBox="0 0 24 24" className="h-3.5 w-3.5">
                                      <path fill="currentColor" d="M7 5h3v14H7zm7 0h3v14h-3z" />
                                    </svg>
                                  ) : (
                                    <svg aria-hidden viewBox="0 0 24 24" className="h-3.5 w-3.5">
                                      <path fill="currentColor" d="M8 5.14v13.72a1 1 0 0 0 1.53.848l10.08-6.86a1 1 0 0 0 0-1.696L9.53 4.292A1 1 0 0 0 8 5.14" />
                                    </svg>
                                  )}
                                </div>
                              </button>
                            );
                          })}
                        </div>
                      );
                    })}
                  </div>
                ) : (
                  <div className="horizontal-card-rail !gap-2 !px-0 !pb-1">
                    {section.tracks.map((track, idx) => (
                      <button
                        key={track.id}
                        type="button"
                        onClick={() => playCategoryTrack(track, section.tracks)}
                        className="group flex-[0_0_clamp(114px,31vw,132px)] text-left"
                      >
                        <div className="relative aspect-square overflow-hidden rounded-[16px] bg-black/10 ring-1 ring-white/[0.05]">
                          <ArtworkImage
                            src={track.thumbnail}
                            alt={track.title}
                            fill
                            sizes="(max-width: 640px) 31vw, 132px"
                            quality={72}
                            className="object-cover transition duration-500 group-hover:scale-[1.03]"
                          />
                          <div className="absolute inset-0 bg-gradient-to-t from-black/42 via-transparent to-transparent" />
                          {section.layout === "mixed" ? (
                            <div className="absolute left-2 top-2 rounded-full bg-black/24 px-1.5 py-1 text-[7px] uppercase tracking-[0.18em] text-white/66">
                              Mix {String(idx + 1).padStart(2, "0")}
                            </div>
                          ) : null}
                        </div>
                        <div className="space-y-1 px-0.5 pt-1.5">
                          <p className="line-clamp-1 text-[12px] font-medium text-white">{track.title}</p>
                          <p className="line-clamp-2 text-[10px] leading-4 text-white/62">{track.author}</p>
                        </div>
                      </button>
                    ))}
                  </div>
                )}
              </section>
            ))}

          <LibrarySection />
        </div>
      </div>
    );
  }

  return (
    <div
      className="category-page-shell home-page-shell"
      style={{
        ["--category-backdrop-image" as string]: "var(--home-backdrop-image)"
      }}
    >
      <section className="category-page-shell__hero sm:rounded-[34px]">
        <div className="category-page-shell__hero-content">
          {quickPickColumns.length > 0 ? (
            <div className="space-y-3">
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-[23px] font-semibold tracking-tight text-white sm:text-[25px]">Quick picks</h2>
                <button
                  type="button"
                  onClick={playAllQuickPicks}
                  className="shrink-0 whitespace-nowrap rounded-full border border-white/[0.08] bg-white/[0.03] px-2.5 py-1 text-[8px] font-medium uppercase leading-none tracking-[0.14em] text-slate-100 transition hover:bg-white/[0.06] sm:px-3 sm:text-[9px]"
                >
                  Play all
                </button>
              </div>

              <div className="horizontal-card-rail !gap-2 !px-0 !pb-1">
                {quickPickColumns.map((column, columnIndex) => (
                  <div key={`hero-quick-picks-${columnIndex}`} className="flex flex-col gap-1.5">
                    {column.map((track) => {
                      const isCurrent = track.id === currentSongId;

                      return (
                        <button
                          key={track.id}
                          type="button"
                          onClick={() => playFeaturedTrack(track)}
                          className="flex w-full items-center gap-3 rounded-[15px] px-1.5 py-1.5 text-left transition hover:bg-white/[0.04]"
                        >
                          <div className="relative h-11 w-11 shrink-0 overflow-hidden rounded-[13px] bg-slate-900">
                            <ArtworkImage
                              src={track.thumbnail}
                              alt={track.title}
                              fill
                              sizes="44px"
                              quality={70}
                              className="object-cover scale-[1.03]"
                            />
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="line-clamp-1 text-[13px] font-medium text-white">{track.title}</p>
                            <p className="line-clamp-1 text-[11px] text-slate-300/72">
                              {track.author} <span className="text-slate-400">• {formatCompactCount(track.views)}</span>
                            </p>
                          </div>
                          <div className="flex h-7.5 w-7.5 shrink-0 items-center justify-center rounded-full bg-white/[0.04] text-white/86">
                            {isCurrent && isPlaying ? (
                              <svg aria-hidden viewBox="0 0 24 24" className="h-3.5 w-3.5">
                                <path fill="currentColor" d="M7 5h3v14H7zm7 0h3v14h-3z" />
                              </svg>
                            ) : (
                              <svg aria-hidden viewBox="0 0 24 24" className="h-3.5 w-3.5">
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
            </div>
          ) : null}
        </div>
      </section>

      <div className="category-page-shell__feed space-y-7 sm:space-y-8">
        {mixedTracks.length > 0 ? (
          <section className="space-y-2.5">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-[21px] font-semibold tracking-tight text-white sm:text-[23px]">Mixed for you</h2>
            <span className="text-[22px] leading-none text-white/62">›</span>
          </div>

          <div className="horizontal-card-rail !gap-2 !px-0 !pb-1">
            {mixedTracks.map((track, index) => (
              <button
                key={track.id}
                type="button"
                onClick={() => playFeaturedTrack(track)}
                className="group flex-[0_0_clamp(114px,31vw,132px)] text-left"
              >
                <div className="relative aspect-square overflow-hidden rounded-[16px] bg-black/10 ring-1 ring-white/[0.05]">
                  <ArtworkImage
                    src={track.thumbnail}
                    alt={track.title}
                    fill
                    sizes="(max-width: 640px) 31vw, 132px"
                    quality={72}
                    className="object-cover transition duration-500 group-hover:scale-[1.03]"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-black/42 via-transparent to-transparent" />
                  <div className="absolute left-2 top-2 rounded-full bg-black/24 px-1.5 py-1 text-[7px] uppercase tracking-[0.18em] text-white/66">
                    Mix {String(index + 1).padStart(2, "0")}
                  </div>
                </div>
                <div className="space-y-1 px-0.5 pt-1.5">
                  <p className="line-clamp-1 text-[12px] font-medium text-white">{track.title}</p>
                  <p className="line-clamp-2 text-[10px] leading-4 text-white/62">{track.author}</p>
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
          <section className="rounded-[28px] border border-white/[0.05] bg-[#070a11]/84 p-6 text-sm leading-7 text-slate-300/78 sm:p-8 sm:text-base">
            Music is loading. You can use the search bar above while the home feed fills in.
          </section>
        )}
      </div>
    </div>
  );
}
