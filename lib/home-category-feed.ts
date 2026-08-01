import { unstable_cache } from "next/cache";

import { createQueryKey } from "@/lib/utils";
import { getHomeCategoryConfig, type HomeCategoryConfig, type HomeCategorySectionConfig } from "@/lib/home-category-chips";
import { searchYouTube } from "@/lib/youtube";
import type { Song } from "@/lib/types";

type CategoryFeedSection = HomeCategorySectionConfig & {
  tracks: Song[];
};

export type HomeCategoryFeed = Omit<HomeCategoryConfig, "sections"> & {
  sections: CategoryFeedSection[];
};

type CacheTelemetry = {
  onCacheHit?: () => void;
  onCacheMiss?: () => void;
};

const CATEGORY_PAGE_FETCH_LIMIT = 18;
const CATEGORY_SECTION_LIMIT = 12;
const CATEGORY_ARTIST_CAP = 2;
const CATEGORY_MIN_VISIBLE_TRACKS = 4;
const CATEGORY_FEED_TTL_MS = 1000 * 60 * 60 * 24 * 7;
const CATEGORY_NOISE_PATTERN =
  /\b(reels?|shorts?|status|whatsapp|tiktok|capcut|edit\s*audio|dialogue|scene|meme|template|ringtone|reaction|review|trailer|teaser|speech|lecture|news|vlog|roast|prank|full\s*movie|behind\s*the\s*scenes|making\s*of|mashup|mega\s*mix|playlist|jukebox)\b/i;

const categoryFeedCache = new Map<string, { value: HomeCategoryFeed; expiresAt: number }>();

function getArtistKey(song: Song) {
  return createQueryKey(song.author.split(",")[0] || song.author);
}

function getIdentityKey(song: Song) {
  return `${createQueryKey(song.title)}::${getArtistKey(song)}`;
}

function isGoodCategorySong(song: Song, section: HomeCategorySectionConfig) {
  const text = `${song.title} ${song.author}`;

  if (!song.videoId || !song.title.trim()) {
    return false;
  }

  if (CATEGORY_NOISE_PATTERN.test(text)) {
    return false;
  }

  if (song.durationSeconds > 0 && song.durationSeconds < 75) {
    return false;
  }

  if (!section.allowLongform && song.durationSeconds > 0 && song.durationSeconds > 8 * 60) {
    return false;
  }

  return true;
}

function appendDistinctCategoryTracks(baseTracks: Song[], additions: Song[], section: HomeCategorySectionConfig, limit: number) {
  const nextTracks = [...baseTracks];
  const seenIdentityKeys = new Set(baseTracks.map(getIdentityKey).filter(Boolean));
  const seenTrackIds = new Set(baseTracks.map((track) => track.id));
  const artistCounts = new Map<string, number>();

  for (const track of baseTracks) {
    const artistKey = getArtistKey(track);

    if (!artistKey) {
      continue;
    }

    artistCounts.set(artistKey, (artistCounts.get(artistKey) || 0) + 1);
  }

  for (const song of additions) {
    if (!isGoodCategorySong(song, section)) {
      continue;
    }

    const identityKey = getIdentityKey(song);

    if (!identityKey || seenIdentityKeys.has(identityKey) || seenTrackIds.has(song.id)) {
      continue;
    }

    const artistKey = getArtistKey(song);

    if (artistKey && (artistCounts.get(artistKey) || 0) >= CATEGORY_ARTIST_CAP) {
      continue;
    }

    nextTracks.push(song);
    seenIdentityKeys.add(identityKey);
    seenTrackIds.add(song.id);

    if (artistKey) {
      artistCounts.set(artistKey, (artistCounts.get(artistKey) || 0) + 1);
    }

    if (nextTracks.length >= limit) {
      break;
    }
  }

  return nextTracks;
}

async function getSectionTracks(section: HomeCategorySectionConfig) {
  const resultSets = await Promise.all(
    section.queries.map(async (query) => {
      try {
        return await searchYouTube(query, CATEGORY_PAGE_FETCH_LIMIT, {
          pages: 1,
          ...(section.searchCategory ? { category: section.searchCategory } : {})
        });
      } catch {
        return [];
      }
    })
  );

  const merged = new Map<
    string,
    {
      song: Song;
      score: number;
      queryIndex: number;
      rank: number;
    }
  >();

  resultSets.forEach((tracks, queryIndex) => {
    tracks.forEach((song, rank) => {
      if (!isGoodCategorySong(song, section)) {
        return;
      }

      const identityKey = getIdentityKey(song);

      if (!identityKey) {
        return;
      }

      const score = Math.max(1, 18 - rank) + (section.queries.length - queryIndex) * 3;
      const existing = merged.get(identityKey);

      if (!existing || score > existing.score) {
        merged.set(identityKey, {
          song,
          score,
          queryIndex,
          rank
        });
      }
    });
  });

  const ranked = Array.from(merged.values()).sort((left, right) => {
    if (right.score !== left.score) {
      return right.score - left.score;
    }

    if (left.queryIndex !== right.queryIndex) {
      return left.queryIndex - right.queryIndex;
    }

    return left.rank - right.rank;
  });

  const artistCounts = new Map<string, number>();
  const selected: Song[] = [];

  for (const entry of ranked) {
    const artistKey = getArtistKey(entry.song);

    if (artistKey && (artistCounts.get(artistKey) || 0) >= CATEGORY_ARTIST_CAP) {
      continue;
    }

    selected.push(entry.song);

    if (artistKey) {
      artistCounts.set(artistKey, (artistCounts.get(artistKey) || 0) + 1);
    }

    if (selected.length >= (section.limit || CATEGORY_SECTION_LIMIT)) {
      break;
    }
  }

  return selected;
}

async function getFallbackSectionTracks(config: HomeCategoryConfig, section: HomeCategorySectionConfig) {
  try {
    const fallbackQuery = `${config.query} ${section.title}`;
    const tracks = await searchYouTube(fallbackQuery, CATEGORY_PAGE_FETCH_LIMIT, {
      pages: 1,
      ...(section.searchCategory ? { category: section.searchCategory } : {})
    });

    const fallbackSection = {
      ...section,
      queries: [fallbackQuery]
    };
    const selected: Song[] = [];
    const seen = new Set<string>();
    const artistCounts = new Map<string, number>();

    for (const song of tracks) {
      if (!isGoodCategorySong(song, fallbackSection)) {
        continue;
      }

      const identityKey = getIdentityKey(song);

      if (!identityKey || seen.has(identityKey)) {
        continue;
      }

      const artistKey = getArtistKey(song);

      if (artistKey && (artistCounts.get(artistKey) || 0) >= CATEGORY_ARTIST_CAP) {
        continue;
      }

      seen.add(identityKey);
      selected.push(song);

      if (artistKey) {
        artistCounts.set(artistKey, (artistCounts.get(artistKey) || 0) + 1);
      }

      if (selected.length >= (section.limit || CATEGORY_SECTION_LIMIT)) {
        break;
      }
    }

    return selected;
  } catch {
    return [];
  }
}

async function buildHomeCategoryFeed(categoryId: string): Promise<HomeCategoryFeed | null> {
  const config = getHomeCategoryConfig(categoryId);

  if (!config) {
    return null;
  }

  const sections = await Promise.all(
    config.sections.map(async (section) => {
      const tracks = await getSectionTracks(section);

      if (tracks.length > 0) {
        return {
          ...section,
          tracks
        };
      }

      return {
        ...section,
        tracks: await getFallbackSectionTracks(config, section)
      };
    })
  );

  const pooledTracks = sections.flatMap((section) => section.tracks);
  const stabilizedSections = sections
    .map((section) => ({
      ...section,
      tracks:
        section.tracks.length >= CATEGORY_MIN_VISIBLE_TRACKS
          ? section.tracks
          : appendDistinctCategoryTracks(section.tracks, pooledTracks, section, CATEGORY_MIN_VISIBLE_TRACKS)
    }))
    .filter((section) => section.tracks.length > 0);

  const feed = {
    ...config,
    sections: stabilizedSections
  };

  return feed;
}

function loadCachedHomeCategoryFeed(categoryId: string) {
  return unstable_cache(async () => buildHomeCategoryFeed(categoryId), ["spasiba-category-feed", categoryId], {
    revalidate: Math.floor(CATEGORY_FEED_TTL_MS / 1000)
  })();
}

export async function getHomeCategoryFeed(categoryId: string, telemetry?: CacheTelemetry): Promise<HomeCategoryFeed | null> {
  const cached = categoryFeedCache.get(categoryId);

  if (cached && cached.expiresAt > Date.now()) {
    telemetry?.onCacheHit?.();
    return cached.value;
  }

  telemetry?.onCacheMiss?.();

  const feed = await loadCachedHomeCategoryFeed(categoryId);

  if (!feed) {
    return null;
  }

  categoryFeedCache.set(categoryId, {
    value: feed,
    expiresAt: Date.now() + CATEGORY_FEED_TTL_MS
  });

  return feed;
}
