"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";

import { audioController } from "@/lib/audio-controller";
import { fetchCategoryFeed, primeCategoryFeed, readCachedCategoryFeed } from "@/lib/home-category-feed-client";
import { getHomeCategoryConfig, HOME_CATEGORY_CHIPS } from "@/lib/home-category-chips";
import type { HomeCategoryFeed } from "@/lib/home-category-feed";
import type { Song } from "@/lib/types";
import { formatCompactCount } from "@/lib/utils";
import { usePlayerStore } from "@/store/playerStore";

import ArtworkImage from "@/components/ArtworkImage";

type CategoryFeedPageProps = {
  categoryId: string;
  initialFeed?: HomeCategoryFeed | null;
};

function CategoryHeroShelf({
  eyebrow,
  section,
  currentSongId,
  isPlaying,
  onPlay,
  onPlayAll
}: {
  eyebrow: string;
  section: HomeCategoryFeed["sections"][number];
  currentSongId: string | null;
  isPlaying: boolean;
  onPlay: (song: Song, queue: Song[]) => void;
  onPlayAll: (queue: Song[]) => void;
}) {
  const heroTracks = section.layout === "quick-picks" ? section.tracks.slice(0, 4) : section.tracks.slice(0, 8);

  return (
    <div className="space-y-2.5">
      <div className="space-y-0.5">
        <p className="text-[10px] font-medium uppercase tracking-[0.26em] text-white/56">{eyebrow}</p>
        <div className="flex items-center justify-between gap-3">
          <h1 className="text-[22px] font-semibold tracking-tight text-white sm:text-[26px]">{section.title}</h1>
          {section.layout === "quick-picks" ? (
            <button
              type="button"
              onClick={() => onPlayAll(section.tracks)}
              className="inline-flex h-8 items-center justify-center rounded-full border border-white/[0.08] bg-black/20 px-3 text-[10px] font-medium uppercase tracking-[0.16em] text-white/88 transition hover:bg-black/28"
            >
              Play all
            </button>
          ) : (
            <span className="text-[26px] leading-none text-white/78">›</span>
          )}
        </div>
      </div>

      {section.layout === "quick-picks" ? (
        <div className="space-y-0.5">
          {heroTracks.map((track) => {
            const isCurrent = track.id === currentSongId;

            return (
              <button
                key={track.id}
                type="button"
                onClick={() => onPlay(track, section.tracks)}
                className="flex w-full items-center gap-3 rounded-[15px] px-1.5 py-1.5 text-left transition hover:bg-white/[0.04]"
              >
                <div className="relative h-11 w-11 shrink-0 overflow-hidden rounded-[13px] bg-black/20">
                  <ArtworkImage src={track.thumbnail} alt={track.title} fill sizes="44px" quality={72} className="object-cover" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="line-clamp-1 text-[13px] font-medium text-white">{track.title}</p>
                  <p className="line-clamp-1 text-[11px] text-white/66">{track.author}</p>
                </div>
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-black/24 text-white/90">
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
      ) : (
        <div className="horizontal-card-rail !gap-2 !px-0 !pb-1">
          {heroTracks.map((track, index) => (
            <button
              key={track.id}
              type="button"
              onClick={() => onPlay(track, section.tracks)}
              className="group flex-[0_0_clamp(118px,33vw,140px)] text-left"
            >
              <div className="relative aspect-square overflow-hidden rounded-[16px] bg-[#080d14] ring-1 ring-white/[0.05]">
                <ArtworkImage
                  src={track.thumbnail}
                  alt={track.title}
                  fill
                  sizes="(max-width: 640px) 33vw, 140px"
                  quality={74}
                  className="object-cover transition duration-500 group-hover:scale-[1.03]"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-transparent" />
                {section.layout === "mixed" ? (
                  <div className="absolute left-2 top-2 rounded-full bg-black/24 px-1.5 py-1 text-[7px] uppercase tracking-[0.18em] text-white/68">
                    Mix {String(index + 1).padStart(2, "0")}
                  </div>
                ) : null}
              </div>
              <div className="space-y-1 px-0.5 pt-1.5">
                <p className="line-clamp-1 text-[12px] font-medium text-white">{track.title}</p>
                <p className="line-clamp-2 text-[10px] leading-4 text-white/64">{track.author}</p>
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function CategorySectionQuickPicks({
  title,
  tracks,
  currentSongId,
  isPlaying,
  onPlay,
  onPlayAll
}: {
  title: string;
  tracks: Song[];
  currentSongId: string | null;
  isPlaying: boolean;
  onPlay: (song: Song, queue: Song[]) => void;
  onPlayAll: (queue: Song[]) => void;
}) {
  const columns = useMemo(() => {
    const nextColumns: Song[][] = [];

    for (let index = 0; index < tracks.length; index += 4) {
      nextColumns.push(tracks.slice(index, index + 4));
    }

    return nextColumns;
  }, [tracks]);

  if (!columns.length) {
    return null;
  }

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-[22px] font-semibold tracking-tight text-white sm:text-[24px]">{title}</h2>
        <button
          type="button"
          onClick={() => onPlayAll(tracks)}
          className="shrink-0 whitespace-nowrap rounded-full border border-white/[0.08] bg-white/[0.03] px-2.5 py-1 text-[8px] font-medium uppercase leading-none tracking-[0.14em] text-slate-100 transition hover:bg-white/[0.06] sm:px-3 sm:text-[9px]"
        >
          Play all
        </button>
      </div>

      <div className="horizontal-card-rail !gap-2 !px-0 !pb-1">
        {columns.map((column, columnIndex) => (
          <div key={`${title}-${columnIndex}`} className="flex flex-col gap-1.5">
            {column.map((track) => {
              const isCurrent = track.id === currentSongId;

              return (
                <button
                  key={track.id}
                  type="button"
                  onClick={() => onPlay(track, tracks)}
                  className="flex w-full items-center gap-3 rounded-[15px] px-1.5 py-1.5 text-left transition hover:bg-white/[0.04]"
                >
                  <div className="relative h-11 w-11 shrink-0 overflow-hidden rounded-[13px] bg-slate-900">
                    <ArtworkImage src={track.thumbnail} alt={track.title} fill sizes="44px" quality={70} className="object-cover scale-[1.03]" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="line-clamp-1 text-[13px] font-medium text-white">{track.title}</p>
                    <p className="line-clamp-1 text-[11px] text-slate-300/72">
                      {track.author} <span className="text-slate-400">• {formatCompactCount(track.views)}</span>
                    </p>
                  </div>
                  <div className="flex h-7.5 w-7.5 shrink-0 items-center justify-center rounded-full bg-white/[0.04] text-white/88">
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
    </section>
  );
}

function CategorySectionMixed({
  title,
  tracks,
  currentSongId,
  isPlaying,
  onPlay
}: {
  title: string;
  tracks: Song[];
  currentSongId: string | null;
  isPlaying: boolean;
  onPlay: (song: Song, queue: Song[]) => void;
}) {
  if (!tracks.length) {
    return null;
  }

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-[22px] font-semibold tracking-tight text-white sm:text-[24px]">{title}</h2>
      </div>

      <div className="horizontal-card-rail !gap-2 !px-0 !pb-1">
        {tracks.map((track) => (
          <button
            key={track.id}
            type="button"
            onClick={() => onPlay(track, tracks)}
            className="group flex-[0_0_clamp(118px,33vw,140px)] text-left"
          >
            <div className="relative aspect-square overflow-hidden rounded-[16px] bg-[#080d14] ring-1 ring-white/[0.05]">
              <ArtworkImage
                src={track.thumbnail}
                alt={track.title}
                fill
                sizes="(max-width: 640px) 33vw, 140px"
                quality={72}
                className="object-cover transition duration-500 group-hover:scale-[1.03]"
              />
              <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-transparent" />
              <div className="absolute left-2 top-2 rounded-full bg-black/24 px-1.5 py-1 text-[7px] uppercase tracking-[0.18em] text-white/68">
                Mix
              </div>
              {track.id === currentSongId ? (
                <div className="absolute bottom-2 left-2 rounded-full bg-black/30 px-1.5 py-1 text-[7px] uppercase tracking-[0.18em] text-white/90">
                  {isPlaying ? "Playing" : "Selected"}
                </div>
              ) : null}
            </div>
            <div className="space-y-1 px-0.5 pt-1.5">
              <p className="line-clamp-1 text-[12px] font-medium text-white">{track.title}</p>
              <p className="line-clamp-2 text-[10px] leading-4 text-white/64">{track.author}</p>
            </div>
          </button>
        ))}
      </div>
    </section>
  );
}

function CategorySectionRail({
  title,
  tracks,
  onPlay
}: {
  title: string;
  tracks: Song[];
  onPlay: (song: Song, queue: Song[]) => void;
}) {
  if (!tracks.length) {
    return null;
  }

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-[22px] font-semibold tracking-tight text-white sm:text-[24px]">{title}</h2>
      </div>

      <div className="horizontal-card-rail !gap-2 !px-0 !pb-1">
        {tracks.map((track) => (
          <button
            key={track.id}
            type="button"
            onClick={() => onPlay(track, tracks)}
            className="group flex-[0_0_clamp(118px,33vw,140px)] text-left"
          >
            <div className="relative aspect-square overflow-hidden rounded-[16px] bg-[#080d14] ring-1 ring-white/[0.05]">
              <ArtworkImage
                src={track.thumbnail}
                alt={track.title}
                fill
                sizes="(max-width: 640px) 33vw, 140px"
                quality={72}
                className="object-cover transition duration-500 group-hover:scale-[1.03]"
              />
            </div>
            <div className="space-y-1 px-0.5 pt-1.5">
              <p className="line-clamp-1 text-[12px] font-medium text-white">{track.title}</p>
              <p className="line-clamp-2 text-[10px] leading-4 text-white/64">{track.author}</p>
            </div>
          </button>
        ))}
      </div>
    </section>
  );
}

function CategorySectionsSkeleton() {
  return (
    <div className="space-y-7">
      {[0, 1, 2].map((sectionIndex) => (
        <section key={`category-skeleton-${sectionIndex}`} className="space-y-3.5">
          <div className="h-7 w-40 rounded-full shimmer" />
          <div className="horizontal-card-rail !gap-3 !px-0 !pb-1">
            {[0, 1].map((itemIndex) => (
              <div key={`category-skeleton-card-${itemIndex}`} className="overflow-hidden rounded-[20px] border border-white/[0.08] bg-[#07101b]/90 p-3">
                <div className="aspect-square w-full rounded-[18px] shimmer" />
                <div className="mt-3 space-y-2">
                  <div className="h-4 w-3/4 rounded-full shimmer" />
                  <div className="h-3 w-1/2 rounded-full shimmer" />
                </div>
              </div>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}

export default function CategoryFeedPage({ categoryId, initialFeed = null }: CategoryFeedPageProps) {
  const router = useRouter();
  const currentSongId = usePlayerStore((state) => state.currentSong?.id || null);
  const isPlaying = usePlayerStore((state) => state.isPlaying);
  const categoryConfig = getHomeCategoryConfig(categoryId);
  const [feed, setFeed] = useState<HomeCategoryFeed | null>(() => readCachedCategoryFeed(categoryId) || initialFeed || null);
  const [isLoading, setIsLoading] = useState(!(readCachedCategoryFeed(categoryId) || initialFeed));
  const adjacentCategories = useMemo(() => {
    const index = HOME_CATEGORY_CHIPS.findIndex((category) => category.id === categoryId);

    if (index === -1) {
      return [];
    }

    return [HOME_CATEGORY_CHIPS[index - 1], HOME_CATEGORY_CHIPS[index + 1]].filter(
      (category): category is (typeof HOME_CATEGORY_CHIPS)[number] => Boolean(category)
    );
  }, [categoryId]);

  useEffect(() => {
    const cached = readCachedCategoryFeed(categoryId);

    if (cached) {
      setFeed(cached);
      setIsLoading(false);
      return;
    }

    if (initialFeed) {
      primeCategoryFeed(categoryId, initialFeed);
      setFeed(initialFeed);
      setIsLoading(false);
      return;
    }

    const controller = new AbortController();

    void fetchCategoryFeed(categoryId, { signal: controller.signal })
      .then((nextFeed) => {
        if (nextFeed) {
          setFeed(nextFeed);
        }
      })
      .catch(() => {
        // Best-effort only. Cached content remains visible.
      })
      .finally(() => {
        setIsLoading(false);
      });

    return () => {
      controller.abort();
    };
  }, [categoryId, initialFeed]);

  useEffect(() => {
    const idleWindow = window as Window &
      typeof globalThis & {
        requestIdleCallback?: (callback: () => void, options?: { timeout: number }) => number;
      };

    const warmAdjacentRoutes = () => {
      adjacentCategories.forEach((category) => {
        router.prefetch(`/category/${category.id}`);
      });
    };

    if (idleWindow.requestIdleCallback) {
      idleWindow.requestIdleCallback(warmAdjacentRoutes, { timeout: 1400 });
      return;
    }

    const timer = window.setTimeout(warmAdjacentRoutes, 250);
    return () => window.clearTimeout(timer);
  }, [adjacentCategories, router]);

  const playTrack = useCallback((song: Song, queue: Song[]) => {
    const queueIndex = queue.findIndex((track) => track.id === song.id);
    void audioController.playSong(song, queue, Math.max(queueIndex, 0));
  }, []);

  const playAll = useCallback((queue: Song[]) => {
    if (!queue.length) {
      return;
    }

    void audioController.playSong(queue[0], queue, 0);
  }, []);

  if (!categoryConfig) {
    return null;
  }

  const sections = feed?.sections || [];
  const heroSection = sections[0] || null;
  const remainingSections = heroSection ? sections.slice(1) : sections;

  return (
    <div
      className="category-page-shell"
      style={{
        ["--category-accent-rgb" as string]: categoryConfig.headerRgb,
        ["--category-backdrop-image" as string]: `url('${categoryConfig.backdropPath}')`
      }}
    >
      <section className="category-page-shell__hero sm:rounded-[34px]">
        <div className="category-page-shell__hero-content">
          {heroSection ? (
            <CategoryHeroShelf
              eyebrow={categoryConfig.eyebrow}
              section={heroSection}
              currentSongId={currentSongId}
              isPlaying={isPlaying}
              onPlay={playTrack}
              onPlayAll={playAll}
            />
          ) : !feed && isLoading ? (
            <div className="space-y-3">
              <div className="h-3 w-28 rounded-full shimmer" />
              <div className="h-8 w-44 rounded-full shimmer" />
              <div className="horizontal-card-rail !gap-3 !px-0 !pb-1">
                {[0, 1, 2].map((itemIndex) => (
                  <div key={`hero-skeleton-${itemIndex}`} className="flex-[0_0_clamp(136px,38vw,164px)]">
                    <div className="aspect-square rounded-[18px] shimmer" />
                    <div className="mt-2 space-y-2 px-1">
                      <div className="h-4 w-3/4 rounded-full shimmer" />
                      <div className="h-3 w-1/2 rounded-full shimmer" />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : null}
        </div>
      </section>

      <div className="category-page-shell__feed space-y-7 sm:space-y-8">
        {!feed && isLoading ? <CategorySectionsSkeleton /> : null}

        {remainingSections.map((section) => {
          if (section.layout === "quick-picks") {
            return (
              <CategorySectionQuickPicks
                key={section.id}
                title={section.title}
                tracks={section.tracks}
                currentSongId={currentSongId}
                isPlaying={isPlaying}
                onPlay={playTrack}
                onPlayAll={playAll}
              />
            );
          }

          if (section.layout === "mixed") {
            return (
              <CategorySectionMixed
                key={section.id}
                title={section.title}
                tracks={section.tracks}
                currentSongId={currentSongId}
                isPlaying={isPlaying}
                onPlay={playTrack}
              />
            );
          }

          return <CategorySectionRail key={section.id} title={section.title} tracks={section.tracks} onPlay={playTrack} />;
        })}
      </div>
    </div>
  );
}
