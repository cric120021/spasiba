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

const CATEGORY_PAGE_FETCH_LIMIT = 18;
const CATEGORY_SECTION_LIMIT = 12;
const CATEGORY_ARTIST_CAP = 2;
const CATEGORY_FEED_TTL_MS = 1000 * 60 * 60 * 6;
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

export async function getHomeCategoryFeed(categoryId: string): Promise<HomeCategoryFeed | null> {
  const cached = categoryFeedCache.get(categoryId);

  if (cached && cached.expiresAt > Date.now()) {
    return cached.value;
  }

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

  const feed = {
    ...config,
    sections
  };

  categoryFeedCache.set(categoryId, {
    value: feed,
    expiresAt: Date.now() + CATEGORY_FEED_TTL_MS
  });

  return feed;
}
