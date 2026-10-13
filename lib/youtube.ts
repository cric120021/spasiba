import { execFile } from "node:child_process";
import { chmod } from "node:fs/promises";
import { resolve as resolvePath } from "node:path";
import { promisify } from "node:util";

import ytdl from "@distube/ytdl-core";
import { Innertube } from "youtubei.js";
// @ts-expect-error yt-search does not publish type declarations.
import ytSearch from "yt-search";

import type { Song, StreamCandidate, StreamResponse, TrendingSection } from "@/lib/types";
import { createQueryKey, extractExpiryTimestamp, formatDuration } from "@/lib/utils";

type CachedValue<T> = {
  expiresAt: number;
  value: T;
};

type SearchYouTubeOptions = {
  pages?: number;
  category?: string;
};

type CacheTelemetry = {
  onCacheHit?: () => void;
  onCacheMiss?: () => void;
};

const execFileAsync = promisify(execFile);

const SEARCH_TTL_MS = 1000 * 60 * 60 * 24;
const DISCOVERY_SECTIONS_TTL_MS = 1000 * 60 * 60 * 24 * 7;
const STREAM_TTL_MS = 1000 * 60 * 20;
const SEARCH_CACHE_LIMIT = 64;
const RELATED_CACHE_LIMIT = 64;
const STREAM_CACHE_LIMIT = 48;
const DISCOVERY_SECTIONS_CACHE_LIMIT = 1;
const DEFAULT_RESULT_LIMIT = 12;
const DISCOVERY_SECTION_TRACK_LIMIT = 24;
const DISCOVERY_FETCH_LIMIT = 30;
const DISCOVERY_ARTIST_CAP = 2;
const DISCOVERY_MIN_VISIBLE_TRACKS = 4;
const STREAM_FRESH_BUFFER_MS = 30_000;
const STREAM_CANDIDATE_LIMIT = 6;
const YT_DLP_FORMAT_SELECTOR = "18/bestaudio[ext=m4a]/best[ext=mp4]/bestaudio[acodec*=mp4a]/bestaudio[ext=webm]/bestaudio/best";
const YT_DLP_PRINT_TEMPLATE = "%(format_id)s\t%(ext)s\t%(acodec)s\t%(vcodec)s\t%(url)s";
const YT_DLP_EXEC_TIMEOUT_MS = 15_000;
const STREAM_PLAYER_CLIENT_SETS = [
  ["WEB", "WEB_EMBEDDED", "IOS", "ANDROID", "TV"],
  ["IOS", "ANDROID", "WEB"],
  ["WEB", "WEB_EMBEDDED"],
  ["IOS", "ANDROID", "TV"]
] as const;
const RELATED_PLAYER_CLIENTS = ["WEB", "IOS", "ANDROID", "WEB_EMBEDDED", "TV"] as const;

const searchCache = new Map<string, CachedValue<Song[]>>();
const relatedCache = new Map<string, CachedValue<Song[]>>();
const streamCache = new Map<string, CachedValue<StreamResponse>>();
const discoverySectionsCache = new Map<string, CachedValue<TrendingSection[]>>();
const pendingSearchRequests = new Map<string, Promise<Song[]>>();
const pendingRelatedRequests = new Map<string, Promise<Song[]>>();
const pendingStreamRequests = new Map<string, Promise<StreamResponse>>();
let pendingTrendingSectionsRequest: Promise<TrendingSection[]> | null = null;

let ytDlpBinaryPath: string | null | undefined;
let youtubeClientPromise: Promise<Innertube> | null = null;
const YOUTUBE_WEB_CLIENT = "WEB" as const;
const BUNDLED_YT_DLP_BINARY_PATH = resolvePath(process.cwd(), "bin/yt-dlp-linux");
const RELATED_NOISE_PATTERN =
  /\b(reels?|shorts?|status|whatsapp|tiktok|capcut|dialogue|scene|meme|template|ringtone|interview|podcast|episode|reaction|review|trailer|teaser|speech|lecture|news|vlog|roast|prank|full\s*movie|behind\s*the\s*scenes|making\s*of|mashup|mix|mega\s*mix|playlist|jukebox|non[\s-]*stop|songs?\s*list|top\s+\d+|new\s+english\s+song|english\s+songs?|bollywood\s+songs?|punjabi\s+songs?|hindi\s+songs?)\b/i;
const MIN_RELATED_DURATION_SECONDS = 75;
const DISCOVERY_NOISE_PATTERN =
  /\b(reels?|shorts?|status|whatsapp|tiktok|capcut|dialogue|scene|meme|template|ringtone|interview|podcast|episode|reaction|review|trailer|teaser|speech|lecture|news|vlog|roast|prank|full\s*movie|behind\s*the\s*scenes|making\s*of|mashup|mix|mega\s*mix|playlist|jukebox|non[\s-]*stop|songs?\s*list|top\s+\d+|best\s+of|hour|hours)\b/i;
const DISCOVERY_VARIANT_PATTERN = /\b(lyrics?|lyrical|slowed|reverb|sped\s*up|remix|live|edit|edit\s*audio|bass\s*boosted|nightcore|cover|karaoke)\b/i;
const DISCOVERY_MUSIC_SOURCE_PATTERN = /\b(official|audio|video|topic|records|music|vevo)\b/i;

type YouTubeFormat = {
  url?: string;
  mimeType?: string;
  audioBitrate?: number;
  bitrate?: number;
  hasAudio?: boolean;
  hasVideo?: boolean;
  isHLS?: boolean;
  isDashMPD?: boolean;
  container?: string | null;
  codecs?: string | null;
  contentLength?: string;
};

type SearchVideoResult = {
  videoId: string;
  title: string;
  thumbnail?: string;
  timestamp?: string;
  seconds?: number;
  author?: {
    name?: string;
  };
  url: string;
  views?: number;
};

type YouTubeJsStreamingData = {
  adaptive_formats?: Array<{
    url?: string;
    mime_type?: string;
    bitrate?: number;
    has_audio?: boolean;
    has_video?: boolean;
  }>;
  hls_manifest_url?: string | null;
  server_abr_streaming_url?: string | null;
};

type YtDlpCommandError = NodeJS.ErrnoException & {
  stderr?: string;
};

type YtSearchResponse = {
  videos?: SearchVideoResult[];
};

type RelatedVideoLike = {
  video_id?: string;
  id?: string;
  title?: { toString(): string } | string;
  author?:
    | {
        name?: string;
        toString?: () => string;
      }
    | string;
  short_byline_text?: { toString(): string } | string;
  long_byline_text?: { toString(): string } | string;
  duration?:
    | {
        text?: string;
        seconds?: number;
      }
    | null;
  length_text?: { toString(): string } | string;
  best_thumbnail?: {
    url?: string;
  };
  thumbnails?: Array<{
    url?: string;
  }>;
  endpoint?: {
    payload?: {
      videoId?: string;
    };
  };
  contents?: unknown[];
  primary?: unknown;
  counterpart?: unknown[];
  video_lockup?: unknown;
};

type MusicQueueVideoLike = {
  video_id?: string;
  title?: { toString(): string } | string;
  thumbnail?: Array<{
    url?: string;
  }>;
  duration?:
    | {
        text?: string;
        seconds?: number;
      }
    | null;
  author?: string;
  artists?: Array<{
    name?: string;
  }>;
  endpoint?: {
    payload?: {
      videoId?: string;
    };
  };
};

function isDefinedString(value: string | undefined): value is string {
  return Boolean(value);
}

function toDisplayString(value: unknown) {
  if (typeof value === "string") {
    return value;
  }

  if (value && typeof value === "object" && "toString" in value && typeof value.toString === "function") {
    const asString = value.toString();
    return typeof asString === "string" ? asString : "";
  }

  return "";
}

export const DISCOVERY_SECTIONS = [
  {
    id: "bollywood",
    title: "Hindi",
    blurb: "Recent Hindi songs, film hits, and melodic mainstream picks.",
    query: "latest hindi official video"
  },
  {
    id: "english-global",
    title: "English",
    blurb: "Popular English records with polished production and wide appeal.",
    query: "global pop official video"
  },
  {
    id: "punjabi",
    title: "Punjabi",
    blurb: "Current Punjabi tracks with strong hooks and energetic vocals.",
    query: "latest punjabi official video"
  },
  {
    id: "dogri",
    title: "Dogri",
    blurb: "Dogri songs and regional picks with melodic, rooted vocals.",
    query: "latest dogri official video"
  },
  {
    id: "lofi-chill",
    title: "Lofi",
    blurb: "Soft-focus instrumentals and easy listening for calmer moments.",
    query: "lofi hip hop beats"
  },
  {
    id: "love-romantic",
    title: "Romance",
    blurb: "Warm, melodic songs built around romance and softer emotion.",
    query: "romantic love songs official audio"
  },
  {
    id: "sad-emotional",
    title: "Sad",
    blurb: "Slower, emotional songs with reflective lyrics and mood.",
    query: "sad emotional songs official audio"
  },
  {
    id: "high-energy-workout",
    title: "Workout",
    blurb: "Pumped-up songs for runs, lifts, and fast movement.",
    query: "high energy workout songs official audio"
  },
  {
    id: "trending-viral",
    title: "Trending",
    blurb: "Songs spreading fast right now across short-form and playlists.",
    query: "viral trending songs official audio"
  }
] as const;

const DISCOVERY_SECTION_QUERY_BANK: Record<string, readonly string[]> = {
  bollywood: ["new hindi official audio", "hindi pop official video"],
  "english-global": ["english pop official audio", "viral pop official video"],
  punjabi: ["new punjabi official audio", "punjabi pop official video"],
  dogri: ["new dogri official audio", "dogri official song"]
};

function getDiscoveryArtistKey(song: Song) {
  return createQueryKey(song.author.split(",")[0] || song.author);
}

function getDiscoveryIdentityKey(song: Song) {
  return `${createQueryKey(song.title)}::${getDiscoveryArtistKey(song)}`;
}

function isGoodDiscoverySongCandidate(song: Song) {
  const text = `${song.title} ${song.author}`;

  if (DISCOVERY_NOISE_PATTERN.test(text)) {
    return false;
  }

  if (song.durationSeconds > 0 && song.durationSeconds < 95) {
    return false;
  }

  if (song.durationSeconds > 0 && song.durationSeconds > 8 * 60) {
    return false;
  }

  return Boolean(song.videoId && song.title.trim());
}

function getDiscoveryCandidateScore(
  song: Song,
  queryIndex: number,
  rank: number,
  supportScore: number,
  queryHits: number
) {
  let score = supportScore + queryHits * 16 - rank * 0.75 - queryIndex * 3;

  score += Math.min(28, Math.log10(Math.max(song.views || 1, 1)) * 5);

  if (DISCOVERY_MUSIC_SOURCE_PATTERN.test(song.title)) {
    score += 8;
  }

  if (DISCOVERY_MUSIC_SOURCE_PATTERN.test(song.author)) {
    score += 10;
  }

  if (song.durationSeconds >= 140 && song.durationSeconds <= 330) {
    score += 10;
  }

  if (DISCOVERY_VARIANT_PATTERN.test(song.title) || DISCOVERY_VARIANT_PATTERN.test(song.author)) {
    score -= 14;
  }

  return score;
}

function appendDistinctDiscoveryTracks(baseTracks: Song[], additions: Song[], limit: number) {
  const nextTracks = [...baseTracks];
  const seenIdentityKeys = new Set(baseTracks.map(getDiscoveryIdentityKey).filter(Boolean));
  const seenTrackIds = new Set(baseTracks.map((track) => track.id));
  const artistCounts = new Map<string, number>();

  for (const track of baseTracks) {
    const artistKey = getDiscoveryArtistKey(track);

    if (!artistKey) {
      continue;
    }

    artistCounts.set(artistKey, (artistCounts.get(artistKey) || 0) + 1);
  }

  for (const song of additions) {
    if (!isGoodDiscoverySongCandidate(song)) {
      continue;
    }

    const identityKey = getDiscoveryIdentityKey(song);

    if (!identityKey || seenIdentityKeys.has(identityKey) || seenTrackIds.has(song.id)) {
      continue;
    }

    const artistKey = getDiscoveryArtistKey(song);

    if (artistKey && (artistCounts.get(artistKey) || 0) >= DISCOVERY_ARTIST_CAP) {
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

function backfillDiscoverySections(sections: TrendingSection[]) {
  const pooledTracks = sections.flatMap((section) => section.tracks);

  return sections.map((section) => {
    if (section.tracks.length >= DISCOVERY_MIN_VISIBLE_TRACKS) {
      return section;
    }

    return {
      ...section,
      tracks: appendDistinctDiscoveryTracks(section.tracks, pooledTracks, DISCOVERY_MIN_VISIBLE_TRACKS)
    };
  });
}

async function getDiscoveryTracks(section: (typeof DISCOVERY_SECTIONS)[number]) {
  const queries = Array.from(new Set([section.query, ...(DISCOVERY_SECTION_QUERY_BANK[section.id] || [])])).filter(Boolean);
  const resultSets = await Promise.all(
    queries.map(async (query) => {
      try {
        return await searchYouTube(query, DISCOVERY_FETCH_LIMIT, {
          pages: 2,
          category: "music"
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
      supportScore: number;
      queryHits: number;
      bestQueryIndex: number;
      bestRank: number;
    }
  >();

  resultSets.forEach((tracks, queryIndex) => {
    tracks.forEach((song, rank) => {
      if (!isGoodDiscoverySongCandidate(song)) {
        return;
      }

      const identityKey = getDiscoveryIdentityKey(song);

      if (!identityKey) {
        return;
      }

      const existing = merged.get(identityKey);
      const rankWeight = Math.max(1, 12 - Math.floor(rank / 3));

      if (existing) {
        existing.supportScore += rankWeight;
        existing.queryHits += 1;
        existing.bestQueryIndex = Math.min(existing.bestQueryIndex, queryIndex);
        existing.bestRank = Math.min(existing.bestRank, rank);
        return;
      }

      merged.set(identityKey, {
        song,
        supportScore: rankWeight,
        queryHits: 1,
        bestQueryIndex: queryIndex,
        bestRank: rank
      });
    });
  });

  const ranked = Array.from(merged.values()).sort((left, right) => {
    const rightScore = getDiscoveryCandidateScore(
      right.song,
      right.bestQueryIndex,
      right.bestRank,
      right.supportScore,
      right.queryHits
    );
    const leftScore = getDiscoveryCandidateScore(
      left.song,
      left.bestQueryIndex,
      left.bestRank,
      left.supportScore,
      left.queryHits
    );
    return rightScore - leftScore;
  });

  const artistCounts = new Map<string, number>();
  const selected: Song[] = [];

  for (const entry of ranked) {
    const artistKey = getDiscoveryArtistKey(entry.song);

    if (artistKey && (artistCounts.get(artistKey) || 0) >= DISCOVERY_ARTIST_CAP) {
      continue;
    }

    selected.push(entry.song);

    if (artistKey) {
      artistCounts.set(artistKey, (artistCounts.get(artistKey) || 0) + 1);
    }

    if (selected.length >= DISCOVERY_SECTION_TRACK_LIMIT) {
      break;
    }
  }

  return selected;
}

function readCache<T>(cache: Map<string, CachedValue<T>>, key: string): T | null {
  const cached = cache.get(key);

  if (!cached) {
    return null;
  }

  if (cached.expiresAt <= Date.now()) {
    cache.delete(key);
    return null;
  }

  cache.delete(key);
  cache.set(key, cached);
  return cached.value;
}

function getCacheLimit(cache: Map<string, CachedValue<unknown>>) {
  if (cache === searchCache) {
    return SEARCH_CACHE_LIMIT;
  }

  if (cache === relatedCache) {
    return RELATED_CACHE_LIMIT;
  }

  if (cache === streamCache) {
    return STREAM_CACHE_LIMIT;
  }

  if (cache === discoverySectionsCache) {
    return DISCOVERY_SECTIONS_CACHE_LIMIT;
  }

  return 64;
}

function cloneTrendingSections(sections: TrendingSection[]) {
  return sections.map((section) => ({
    ...section,
    tracks: section.tracks.map((track) => ({ ...track }))
  }));
}

function pruneCache<T>(cache: Map<string, CachedValue<T>>) {
  const now = Date.now();

  for (const [key, entry] of cache) {
    if (entry.expiresAt <= now) {
      cache.delete(key);
    }
  }

  const limit = getCacheLimit(cache as Map<string, CachedValue<unknown>>);

  while (cache.size > limit) {
    const oldestKey = cache.keys().next().value as string | undefined;

    if (!oldestKey) {
      break;
    }

    cache.delete(oldestKey);
  }
}

function writeCache<T>(cache: Map<string, CachedValue<T>>, key: string, value: T, ttlMs: number) {
  cache.set(key, {
    value,
    expiresAt: Date.now() + ttlMs
  });
  pruneCache(cache);
}

function createStreamCacheKey(videoIdOrUrl: string) {
  const trimmed = videoIdOrUrl.trim();

  if (!trimmed.startsWith("http")) {
    return trimmed;
  }

  try {
    const parsed = new URL(trimmed);
    return parsed.searchParams.get("v") || trimmed;
  } catch {
    return trimmed;
  }
}

function createSearchCacheKey(query: string, limit: number, options: SearchYouTubeOptions) {
  const pages = Number.isFinite(options.pages) && (options.pages || 0) > 1 ? Math.min(options.pages || 1, 5) : 1;
  const category = options.category?.trim().toLowerCase() || "";
  return `${query}::${limit}::${pages}::${category}`;
}

function getYtDlpBinaryCandidates() {
  return Array.from(
    new Set([process.env.YT_DLP_BINARY, BUNDLED_YT_DLP_BINARY_PATH, "yt-dlp", "/opt/homebrew/bin/yt-dlp", "/usr/local/bin/yt-dlp"].filter(isDefinedString))
  );
}

function getYoutubeClient() {
  if (!youtubeClientPromise) {
    youtubeClientPromise = Innertube.create({
      client_type: YOUTUBE_WEB_CLIENT as never,
      device_category: "desktop",
      generate_session_locally: true,
      enable_session_cache: false
    });
  }

  return youtubeClientPromise;
}

function getFreshSongStream(song: Song): StreamResponse | null {
  if (!song.streamUrl) {
    return null;
  }

  if (song.streamExpiresAt && song.streamExpiresAt <= Date.now() + STREAM_FRESH_BUFFER_MS) {
    return null;
  }

  return {
    streamUrl: song.streamUrl,
    mimeType: song.streamMimeType ?? null,
    expiresAt: song.streamExpiresAt ?? null,
    streamCandidates:
      song.streamCandidates && song.streamCandidates.length > 0
        ? song.streamCandidates
        : [
            {
              url: song.streamUrl,
              mimeType: song.streamMimeType ?? null,
              expiresAt: song.streamExpiresAt ?? null
            }
          ]
  };
}

function mergeSongWithStream(song: Song, stream: StreamResponse): Song {
  return {
    ...song,
    streamUrl: stream.streamUrl,
    streamMimeType: stream.mimeType,
    streamExpiresAt: stream.expiresAt,
    streamCandidates: stream.streamCandidates
  };
}

function parseMimeBase(mimeType?: string) {
  return mimeType?.split(";")[0]?.trim().toLowerCase() || "";
}

function inferMimeTypeFromExt(ext?: string, vcodec?: string | null) {
  const normalizedExt = ext?.trim().toLowerCase() || "";
  const hasVideo = Boolean(vcodec && vcodec !== "none");

  switch (normalizedExt) {
    case "m4a":
      return "audio/mp4";
    case "mp4":
      return hasVideo ? "video/mp4" : "audio/mp4";
    case "webm":
      return hasVideo ? "video/webm" : "audio/webm";
    case "m3u8":
      return "application/vnd.apple.mpegurl";
    default:
      return null;
  }
}

function isPlayableMimeType(mimeType?: string) {
  const mimeBase = parseMimeBase(mimeType);
  return (
    mimeBase === "audio/mp4" ||
    mimeBase === "audio/webm" ||
    mimeBase === "video/mp4" ||
    mimeBase === "video/webm" ||
    mimeBase === "application/vnd.apple.mpegurl" ||
    mimeBase === "application/x-mpegurl"
  );
}

function getMimePriority(mimeType?: string) {
  const mimeBase = parseMimeBase(mimeType);

  switch (mimeBase) {
    case "audio/mp4":
      return 6;
    case "audio/webm":
      return 5;
    case "video/mp4":
      return 4;
    case "video/webm":
      return 3;
    case "application/vnd.apple.mpegurl":
    case "application/x-mpegurl":
      return 1;
    default:
      return 0;
  }
}

function getFormatScore(format: YouTubeFormat) {
  return (
    (format.hasAudio ? 1_000_000 : 0) +
    (!format.hasVideo ? 100_000 : 0) +
    (!format.isHLS ? 10_000 : 0) +
    (!format.isDashMPD ? 5_000 : 0) +
    getMimePriority(format.mimeType) * 1_000 +
    (format.audioBitrate || 0) * 10 +
    (format.bitrate || 0)
  );
}

function listPlayableFormats(formats: YouTubeFormat[]): YouTubeFormat[] {
  return formats
    .filter((format) => Boolean(format.url && format.hasAudio && format.mimeType && isPlayableMimeType(format.mimeType)))
    .sort((left, right) => getFormatScore(right) - getFormatScore(left));
}

function toStreamCandidates(formats: YouTubeFormat[]): StreamCandidate[] {
  const seenUrls = new Set<string>();
  const candidates: StreamCandidate[] = [];

  for (const format of listPlayableFormats(formats)) {
    if (!format.url || seenUrls.has(format.url)) {
      continue;
    }

    seenUrls.add(format.url);
    candidates.push({
      url: format.url,
      mimeType: format.mimeType || null,
      expiresAt: extractExpiryTimestamp(format.url)
    });

    if (candidates.length >= STREAM_CANDIDATE_LIMIT) {
      break;
    }
  }

  return candidates;
}

function isUnavailableBinaryCandidateError(error: unknown) {
  return (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    ["ENOENT", "EACCES", "ENOEXEC", "UNKNOWN"].includes(String((error as NodeJS.ErrnoException).code))
  );
}

async function resolveYtDlpBinary() {
  if (ytDlpBinaryPath !== undefined) {
    return ytDlpBinaryPath;
  }

  for (const candidate of getYtDlpBinaryCandidates()) {
    try {
      if (candidate === BUNDLED_YT_DLP_BINARY_PATH) {
        await chmod(candidate, 0o755).catch(() => {
          // Best-effort for deployed Linux bundles.
        });
      }

      await execFileAsync(candidate, ["--version"], {
        timeout: 3_000,
        maxBuffer: 128 * 1024
      });
      ytDlpBinaryPath = candidate;
      return ytDlpBinaryPath;
    } catch (error) {
      if (isUnavailableBinaryCandidateError(error)) {
        continue;
      }

      throw error;
    }
  }

  ytDlpBinaryPath = null;
  return ytDlpBinaryPath;
}

async function resolveBestAudioStreamWithYtDlp(videoUrl: string) {
  const binaryPath = await resolveYtDlpBinary();

  if (!binaryPath) {
    throw new Error("yt-dlp is not available on this machine.");
  }

  const { stdout } = await execFileAsync(
    binaryPath,
    ["--print", YT_DLP_PRINT_TEMPLATE, "-f", YT_DLP_FORMAT_SELECTOR, "--extractor-args", "youtube:player_client=ios,mweb", "--no-playlist", "--no-warnings", videoUrl],
    {
      timeout: YT_DLP_EXEC_TIMEOUT_MS,
      maxBuffer: 2 * 1024 * 1024
    }
  );

  const firstLine = stdout
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find(Boolean);

  if (!firstLine) {
    throw new Error("yt-dlp did not return a playable stream URL.");
  }

  const [formatId, ext, , vcodec, ...urlParts] = firstLine.split("\t");
  const url = urlParts.join("\t").trim();

  if (!formatId || !url) {
    throw new Error("yt-dlp returned an incomplete stream record.");
  }

  return [
    {
      url,
      mimeType: inferMimeTypeFromExt(ext, vcodec),
      expiresAt: extractExpiryTimestamp(url)
    }
  ] satisfies StreamCandidate[];
}

async function resolveBestAudioStreamWithYtdl(videoUrl: string) {
  const attemptErrors: string[] = [];

  for (const playerClients of STREAM_PLAYER_CLIENT_SETS) {
    try {
      const info = await ytdl.getInfo(videoUrl, {
        playerClients: [...playerClients]
      });
      const streamCandidates = toStreamCandidates(info.formats as YouTubeFormat[]);

      if (streamCandidates.length > 0) {
        return streamCandidates;
      }

      attemptErrors.push(`No browser-playable audio format for clients ${playerClients.join("/")}`);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unknown stream resolution error";
      attemptErrors.push(`${playerClients.join("/")}: ${message}`);
    }
  }

  throw new Error(
    attemptErrors.length > 0
      ? `Unable to resolve a playable audio stream. ${attemptErrors[0]}`
      : "Unable to resolve a playable audio stream."
  );
}

async function resolveBestAudioStreamWithYoutubeJs(videoIdOrUrl: string) {
  const target = createStreamCacheKey(videoIdOrUrl);
  const youtube = await getYoutubeClient();
  const info = await youtube.getBasicInfo(target, {
    client: YOUTUBE_WEB_CLIENT
  });
  const streamingData = info.streaming_data as YouTubeJsStreamingData | undefined;

  if (!streamingData) {
    throw new Error("youtubei.js did not expose streaming data.");
  }

  const candidates: StreamCandidate[] = [];
  const seenUrls = new Set<string>();
  const adaptiveAudioFormats = (streamingData.adaptive_formats || [])
    .filter((format) => format.has_audio)
    .sort((left, right) => {
      const mimeDifference = getMimePriority(right.mime_type) - getMimePriority(left.mime_type);

      if (mimeDifference !== 0) {
        return mimeDifference;
      }

      return (right.bitrate || 0) - (left.bitrate || 0);
    });

  const pushCandidate = (url?: string | null, mimeType?: string | null) => {
    if (!url || seenUrls.has(url)) {
      return;
    }

    seenUrls.add(url);
    candidates.push({
      url,
      mimeType: mimeType || null,
      expiresAt: extractExpiryTimestamp(url)
    });
  };

  const bestAdaptiveAudioFormat = adaptiveAudioFormats[0];

  pushCandidate(streamingData.server_abr_streaming_url, bestAdaptiveAudioFormat?.mime_type || "audio/mp4");
  pushCandidate(streamingData.hls_manifest_url, "application/vnd.apple.mpegurl");

  for (const format of adaptiveAudioFormats) {
    pushCandidate(format.url, format.mime_type);

    if (candidates.length >= STREAM_CANDIDATE_LIMIT) {
      break;
    }
  }

  if (candidates.length === 0) {
    throw new Error("youtubei.js did not return any direct stream candidates.");
  }

  return candidates;
}

async function resolveBestAudioStreamWithPiped(videoIdOrUrl: string): Promise<StreamCandidate[]> {
  const match = videoIdOrUrl.match(/(?:v=|\/)([a-zA-Z0-9_-]{11})/);
  const videoId = match ? match[1] : videoIdOrUrl;

  const PIPED_INSTANCES = [
    "https://pipedapi.kavin.rocks",
    "https://api.piped.privacydev.net",
    "https://pipedapi.mha.fi",
    "https://pipedapi.adminforge.de"
  ];

  for (const instance of PIPED_INSTANCES) {
    try {
      const response = await fetch(`${instance}/streams/${videoId}`, {
        headers: { "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)" },
        signal: AbortSignal.timeout(4000)
      });

      if (!response.ok) continue;

      const data = (await response.json()) as { audioStreams?: Array<{ url?: string; mimeType?: string; bitrate?: number }> };
      const audioStreams = data.audioStreams || [];
      if (!audioStreams.length) continue;

      // Filter for Safari/iOS-compatible container formats (MP4, M4A, AAC, MP3). Avoid WebM/Opus!
      const compatibleStreams = audioStreams.filter((stream) => {
        const mime = (stream.mimeType || "").toLowerCase();
        return mime.includes("mp4") || mime.includes("m4a") || mime.includes("aac") || mime.includes("mpeg");
      });

      const targetStreams = compatibleStreams.length > 0 ? compatibleStreams : audioStreams;
      const sortedStreams = targetStreams.sort((a, b) => (b.bitrate || 0) - (a.bitrate || 0));

      const candidates: StreamCandidate[] = [];
      for (const s of sortedStreams) {
        if (s.url) {
          candidates.push({
            url: s.url,
            mimeType: s.mimeType || "audio/mp4",
            expiresAt: Date.now() + 1000 * 60 * 60 * 3
          });
        }
      }

      if (candidates.length > 0) {
        return candidates;
      }
    } catch {
      // Try next Piped instance
    }
  }

  throw new Error("Piped API failed to resolve stream.");
}

async function resolveBestAudioStream(videoUrl: string) {
  const attemptErrors: string[] = [];

  try {
    return await resolveBestAudioStreamWithPiped(videoUrl);
  } catch (error) {
    const message = error instanceof Error ? error.message : "Piped failed.";
    attemptErrors.push(`piped: ${message}`);
  }

  try {
    return await resolveBestAudioStreamWithYoutubeJs(videoUrl);
  } catch (error) {
    const message = error instanceof Error ? error.message : "youtubei.js failed.";
    attemptErrors.push(`youtubei.js: ${message}`);
  }

  try {
    return await resolveBestAudioStreamWithYtDlp(videoUrl);
  } catch (error) {
    const message =
      error instanceof Error
        ? error.message
        : typeof error === "object" && error !== null && "stderr" in error
          ? String((error as YtDlpCommandError).stderr || "yt-dlp failed.")
          : "yt-dlp failed.";
    attemptErrors.push(`yt-dlp: ${message}`);
  }

  try {
    return await resolveBestAudioStreamWithYtdl(videoUrl);
  } catch (error) {
    const message = error instanceof Error ? error.message : "ytdl-core failed.";
    attemptErrors.push(`ytdl-core: ${message}`);
  }

  throw new Error(attemptErrors.join(" "));
}

function mapResultToSong(result: SearchVideoResult): Song {
  const durationSeconds = typeof result.seconds === "number" ? result.seconds : 0;

  return {
    id: result.videoId,
    videoId: result.videoId,
    title: result.title,
    thumbnail: result.thumbnail || `https://i.ytimg.com/vi/${result.videoId}/hqdefault.jpg`,
    duration: result.timestamp || formatDuration(durationSeconds),
    durationSeconds,
    author: result.author?.name || "YouTube",
    url: result.url,
    views: typeof result.views === "number" ? result.views : 0
  };
}

function mapRelatedNodeToSong(candidate: RelatedVideoLike): Song | null {
  const videoId = candidate.video_id || candidate.id || candidate.endpoint?.payload?.videoId;

  if (!videoId) {
    return null;
  }

  const title = toDisplayString(candidate.title).trim();

  if (!title) {
    return null;
  }

  const author =
    toDisplayString(candidate.author) || toDisplayString(candidate.short_byline_text) || toDisplayString(candidate.long_byline_text) || "YouTube";
  const durationText =
    typeof candidate.duration?.text === "string"
      ? candidate.duration.text
      : toDisplayString(candidate.length_text) || toDisplayString(candidate.duration?.text);
  const durationSeconds = typeof candidate.duration?.seconds === "number" ? candidate.duration.seconds : 0;
  const thumbnail =
    candidate.best_thumbnail?.url ||
    candidate.thumbnails?.find((item) => item?.url)?.url ||
    `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;

  return {
    id: videoId,
    videoId,
    title,
    thumbnail,
    duration: durationText || formatDuration(durationSeconds),
    durationSeconds,
    author,
    url: `https://www.youtube.com/watch?v=${videoId}`,
    views: 0
  };
}

function isGoodRelatedSongCandidate(song: Song) {
  const text = `${song.title} ${song.author}`;

  if (RELATED_NOISE_PATTERN.test(text)) {
    return false;
  }

  if (song.durationSeconds > 0 && song.durationSeconds < MIN_RELATED_DURATION_SECONDS) {
    return false;
  }

  return true;
}

function collectRelatedSongsFromNode(node: unknown, bucket: Song[], seenTrackIds: Set<string>) {
  if (!node || typeof node !== "object") {
    return;
  }

  const candidate = mapRelatedNodeToSong(node as RelatedVideoLike);

  if (candidate && isGoodRelatedSongCandidate(candidate) && !seenTrackIds.has(candidate.id)) {
    seenTrackIds.add(candidate.id);
    bucket.push(candidate);
  }

  const relatedNode = node as RelatedVideoLike & {
    contents?: unknown[];
    primary?: unknown;
    counterpart?: unknown[];
    video_lockup?: unknown;
  };

  for (const child of relatedNode.contents || []) {
    collectRelatedSongsFromNode(child, bucket, seenTrackIds);
  }

  if (relatedNode.primary) {
    collectRelatedSongsFromNode(relatedNode.primary, bucket, seenTrackIds);
  }

  for (const child of relatedNode.counterpart || []) {
    collectRelatedSongsFromNode(child, bucket, seenTrackIds);
  }

  if (relatedNode.video_lockup) {
    collectRelatedSongsFromNode(relatedNode.video_lockup, bucket, seenTrackIds);
  }
}

function mapMusicQueueVideoToSong(candidate: MusicQueueVideoLike): Song | null {
  const videoId = candidate.video_id || candidate.endpoint?.payload?.videoId || "";

  if (!videoId) {
    return null;
  }

  const title = toDisplayString(candidate.title).trim();

  if (!title) {
    return null;
  }

  const durationText = candidate.duration?.text || "";
  const durationSeconds = candidate.duration?.seconds || 0;
  const author =
    candidate.author ||
    candidate.artists
      ?.map((artist) => artist.name?.trim())
      .filter(Boolean)
      .join(", ") ||
    "YouTube Music";

  return {
    id: videoId,
    videoId,
    title,
    thumbnail: candidate.thumbnail?.[0]?.url || `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`,
    duration: durationText || formatDuration(durationSeconds),
    durationSeconds,
    author,
    url: `https://www.youtube.com/watch?v=${videoId}`,
    views: 0
  };
}

function collectMusicQueueSongs(node: unknown, bucket: Song[], seenTrackIds: Set<string>) {
  if (!node || typeof node !== "object") {
    return;
  }

  const queueNode = node as {
    primary?: unknown;
    counterpart?: unknown[];
  } & MusicQueueVideoLike;

  const directCandidate = mapMusicQueueVideoToSong(queueNode);

  if (directCandidate && isGoodRelatedSongCandidate(directCandidate) && !seenTrackIds.has(directCandidate.id)) {
    seenTrackIds.add(directCandidate.id);
    bucket.push(directCandidate);
  }

  if (queueNode.primary) {
    collectMusicQueueSongs(queueNode.primary, bucket, seenTrackIds);
  }

  for (const child of queueNode.counterpart || []) {
    collectMusicQueueSongs(child, bucket, seenTrackIds);
  }
}

export function rankSearchResults(query: string, songs: Song[]): Song[] {
  const normalizedQuery = createQueryKey(query);

  if (!normalizedQuery || !songs.length) {
    return songs;
  }

  const queryTokens = normalizedQuery.split(/\s+/).filter(Boolean);

  const matchesAuthor = (author: string) => {
    const normAuthor = createQueryKey(author);
    if (!normAuthor) return false;
    return normAuthor === normalizedQuery || normAuthor.includes(normalizedQuery) || normalizedQuery.includes(normAuthor);
  };

  const matchesTitle = (title: string) => {
    const normTitle = createQueryKey(title);
    if (!normTitle) return false;
    return normTitle.includes(normalizedQuery);
  };

  let authorMatchCount = 0;
  let titleMatchCount = 0;

  for (const song of songs) {
    if (matchesAuthor(song.author)) {
      authorMatchCount++;
    }
    if (matchesTitle(song.title)) {
      titleMatchCount++;
    }
  }

  // Only consider query an Artist Search if author matches outnumber or dominate title matches
  const isArtistSearch =
    authorMatchCount > 0 &&
    (titleMatchCount === 0 || (authorMatchCount >= songs.length * 0.4 && titleMatchCount < songs.length * 0.3));

  return [...songs].sort((left, right) => {
    const leftTitle = createQueryKey(left.title);
    const rightTitle = createQueryKey(right.title);
    const leftAuthor = createQueryKey(left.author);
    const rightAuthor = createQueryKey(right.author);

    let leftScore = 0;
    let rightScore = 0;

    if (isArtistSearch) {
      // ARTIST SEARCH MODE: Only put artist results on top when an artist name is searched
      if (leftAuthor === normalizedQuery || leftAuthor.includes(normalizedQuery) || normalizedQuery.includes(leftAuthor)) {
        leftScore += 80000;
      }
      if (rightAuthor === normalizedQuery || rightAuthor.includes(normalizedQuery) || normalizedQuery.includes(rightAuthor)) {
        rightScore += 80000;
      }

      leftScore += Math.min(10000, Math.log10(Math.max(1, left.views || 1)) * 1000);
      rightScore += Math.min(10000, Math.log10(Math.max(1, right.views || 1)) * 1000);
    } else {
      // NORMAL SONG SEARCH MODE: Song title matches MUST be on top!
      if (leftTitle === normalizedQuery) leftScore += 100000;
      if (rightTitle === normalizedQuery) rightScore += 100000;

      if (leftTitle.startsWith(normalizedQuery)) leftScore += 50000;
      if (rightTitle.startsWith(normalizedQuery)) rightScore += 50000;

      if (leftTitle.includes(normalizedQuery)) leftScore += 20000;
      if (rightTitle.includes(normalizedQuery)) rightScore += 20000;

      for (const token of queryTokens) {
        if (leftTitle.includes(token)) leftScore += 2000;
        if (rightTitle.includes(token)) rightScore += 2000;
      }

      // Heavily penalize songs whose title does not contain the query in normal song search mode
      if (!leftTitle.includes(normalizedQuery)) leftScore -= 30000;
      if (!rightTitle.includes(normalizedQuery)) rightScore -= 30000;

      leftScore += Math.min(5000, Math.log10(Math.max(1, left.views || 1)) * 500);
      rightScore += Math.min(5000, Math.log10(Math.max(1, right.views || 1)) * 500);
    }

    return rightScore - leftScore;
  });
}

async function searchYouTubeWithInnertube(
  query: string,
  fetchLimit: number,
  pages = 1
): Promise<Song[]> {
  const youtube = await getYoutubeClient();
  const searchResult = await youtube.search(query, { type: "video" });
  const rawVideos: unknown[] = Array.isArray(searchResult.videos) ? [...searchResult.videos] : [];

  let currentPage = 1;
  let currentResult = searchResult;
  while (currentPage < pages && currentResult.has_continuation && rawVideos.length < fetchLimit) {
    try {
      currentResult = await currentResult.getContinuation();
      if (Array.isArray(currentResult.videos) && currentResult.videos.length > 0) {
        rawVideos.push(...currentResult.videos);
      } else {
        break;
      }
      currentPage++;
    } catch {
      break;
    }
  }

  const songs: Song[] = [];
  const seenIds = new Set<string>();

  for (const item of rawVideos) {
    const video = item as {
      id?: string;
      title?: unknown;
      author?: unknown;
      duration?: { text?: string; seconds?: number };
      best_thumbnail?: { url?: string };
      thumbnails?: Array<{ url?: string }>;
      view_count?: { text?: string };
      short_view_count?: { text?: string };
    };

    if (!video || !video.id || seenIds.has(video.id)) {
      continue;
    }

    const title = toDisplayString(video.title).trim();
    if (!title) {
      continue;
    }

    const author =
      (typeof video.author === "object" && video.author && "name" in video.author
        ? String((video.author as { name?: unknown }).name)
        : toDisplayString(video.author)) || "YouTube";

    const durationText = video.duration?.text || "";
    const durationSeconds = typeof video.duration?.seconds === "number" ? video.duration.seconds : 0;

    const thumbnail =
      video.best_thumbnail?.url ||
      video.thumbnails?.[0]?.url ||
      `https://i.ytimg.com/vi/${video.id}/hqdefault.jpg`;

    const rawViews = video.view_count?.text || video.short_view_count?.text || "";
    const views = Number(rawViews.replace(/[^0-9]/g, "")) || 0;

    seenIds.add(video.id);
    songs.push({
      id: video.id,
      videoId: video.id,
      title,
      thumbnail,
      duration: durationText || formatDuration(durationSeconds),
      durationSeconds,
      author,
      url: `https://www.youtube.com/watch?v=${video.id}`,
      views
    });

    if (songs.length >= fetchLimit) {
      break;
    }
  }

  return songs;
}

export async function searchYouTube(
  query: string,
  limit = DEFAULT_RESULT_LIMIT,
  options: SearchYouTubeOptions = {},
  telemetry?: CacheTelemetry
): Promise<Song[]> {
  const normalized = createQueryKey(query);
  const fetchLimit = Math.max(limit, DEFAULT_RESULT_LIMIT);
  const pages = Number.isFinite(options.pages) && (options.pages || 0) > 1 ? Math.min(options.pages || 1, 5) : 1;
  const category = options.category?.trim() || "";
  const cacheKey = createSearchCacheKey(normalized, fetchLimit, { pages, category });
  const pendingKey = cacheKey;

  if (!normalized) {
    return [];
  }

  const cached = readCache(searchCache, cacheKey);

  if (cached) {
    telemetry?.onCacheHit?.();
    return cached.slice(0, limit);
  }

  const pending = pendingSearchRequests.get(pendingKey);

  if (pending) {
    telemetry?.onCacheHit?.();
    return pending.then((tracks) => tracks.slice(0, limit));
  }

  telemetry?.onCacheMiss?.();

  const request = (async () => {
    const searchQuery = category ? `${normalized} ${category}` : normalized;

    // Primary: Innertube (works reliably in cloud serverless environments like Vercel where HTML scraping is blocked)
    try {
      const innertubeTracks = await searchYouTubeWithInnertube(searchQuery, fetchLimit, pages);
      if (innertubeTracks.length > 0) {
        return innertubeTracks;
      }
    } catch (innertubeError) {
      console.warn("[spasiba-runtime] Innertube search failed, trying yt-search fallback:", innertubeError);
    }

    // Secondary / Fallback: yt-search
    try {
      const ytSearchResponse = await (
        ytSearch as (query: string | { query: string; pageEnd?: number; category?: string }) => Promise<YtSearchResponse>
      )(
        pages > 1 || category
          ? {
              query: normalized,
              pageEnd: pages,
              ...(category ? { category } : {})
            }
          : normalized
      );
      const videos = Array.isArray(ytSearchResponse.videos) ? ytSearchResponse.videos : [];
      return videos.slice(0, fetchLimit).map(mapResultToSong);
    } catch (ytSearchError) {
      throw new Error(
        `Search failed across all providers. (ytSearch: ${
          ytSearchError instanceof Error ? ytSearchError.message : String(ytSearchError)
        })`
      );
    }
  })()
    .then((rawTracks) => {
      const tracks = rankSearchResults(query, rawTracks);
      writeCache(searchCache, cacheKey, tracks, SEARCH_TTL_MS);
      return tracks;
    })
    .finally(() => {
      pendingSearchRequests.delete(pendingKey);
    });

  pendingSearchRequests.set(pendingKey, request);
  return request.then((tracks) => tracks.slice(0, limit));
}

export async function getRelatedSongs(videoId: string, limit = DEFAULT_RESULT_LIMIT, telemetry?: CacheTelemetry): Promise<Song[]> {
  const normalizedVideoId = videoId.trim();

  if (!normalizedVideoId) {
    return [];
  }

  const cached = readCache(relatedCache, normalizedVideoId);

  if (cached) {
    telemetry?.onCacheHit?.();
    return cached.slice(0, limit);
  }

  const pending = pendingRelatedRequests.get(normalizedVideoId);

  if (pending) {
    telemetry?.onCacheHit?.();
    return pending.then((tracks) => tracks.slice(0, limit));
  }

  telemetry?.onCacheMiss?.();

  const request = getYoutubeClient()
    .then(async (youtube) => {
      try {
        const upNextPanel = await youtube.music.getUpNext(normalizedVideoId, true);
        const musicQueueSongs: Song[] = [];
        const seenTrackIds = new Set<string>([normalizedVideoId]);

        for (const node of upNextPanel.contents || []) {
          collectMusicQueueSongs(node, musicQueueSongs, seenTrackIds);
        }

        if (musicQueueSongs.length > 0) {
          writeCache(relatedCache, normalizedVideoId, musicQueueSongs, SEARCH_TTL_MS);
          return musicQueueSongs;
        }
      } catch {
        // Fall through to the broader YouTube clients below.
      }

      let bestRelatedSongs: Song[] = [];

      for (const client of RELATED_PLAYER_CLIENTS) {
        try {
          const info = await youtube.getInfo(normalizedVideoId, { client: client as never });
          const relatedSongs: Song[] = [];
          const seenTrackIds = new Set<string>([normalizedVideoId]);

          for (const node of info.watch_next_feed || []) {
            collectRelatedSongsFromNode(node, relatedSongs, seenTrackIds);
          }

          if (relatedSongs.length === 0 && info.autoplay_video_endpoint?.payload?.videoId) {
            const autoplaySong = mapRelatedNodeToSong({
              video_id: info.autoplay_video_endpoint.payload.videoId,
              title: "",
              author: ""
            });

            if (autoplaySong && isGoodRelatedSongCandidate(autoplaySong) && !seenTrackIds.has(autoplaySong.id)) {
              relatedSongs.push(autoplaySong);
            }
          }

          if (relatedSongs.length > bestRelatedSongs.length) {
            bestRelatedSongs = relatedSongs;
          }

          if (bestRelatedSongs.length >= limit) {
            break;
          }
        } catch {
          // Try the next client profile.
        }
      }

      writeCache(relatedCache, normalizedVideoId, bestRelatedSongs, SEARCH_TTL_MS);
      return bestRelatedSongs;
    })
    .finally(() => {
      pendingRelatedRequests.delete(normalizedVideoId);
    });

  pendingRelatedRequests.set(normalizedVideoId, request);
  return request.then((tracks) => tracks.slice(0, limit));
}

export async function getTrendingSections(): Promise<TrendingSection[]> {
  const cacheKey = "home-trending-sections";
  const cached = readCache(discoverySectionsCache, cacheKey);

  if (cached) {
    return cloneTrendingSections(cached);
  }

  if (pendingTrendingSectionsRequest) {
    return pendingTrendingSectionsRequest.then((sections) => cloneTrendingSections(sections));
  }

  pendingTrendingSectionsRequest = Promise.all(
    DISCOVERY_SECTIONS.map(async (section) => ({
      ...section,
      tracks: await getDiscoveryTracks(section)
    }))
  )
    .then((sections) => {
      const stabilizedSections = backfillDiscoverySections(sections);
      writeCache(discoverySectionsCache, cacheKey, stabilizedSections, DISCOVERY_SECTIONS_TTL_MS);
      return stabilizedSections;
    })
    .finally(() => {
      pendingTrendingSectionsRequest = null;
    });

  return pendingTrendingSectionsRequest.then((sections) => cloneTrendingSections(sections));
}

export async function getAudioStream(videoIdOrUrl: string, telemetry?: CacheTelemetry): Promise<StreamResponse> {
  const videoUrl = videoIdOrUrl.startsWith("http")
    ? videoIdOrUrl
    : `https://www.youtube.com/watch?v=${videoIdOrUrl}`;
  const cacheKey = createStreamCacheKey(videoIdOrUrl);
  const cached = readCache(streamCache, cacheKey);

  if (cached) {
    telemetry?.onCacheHit?.();
    return cached;
  }

  const pending = pendingStreamRequests.get(cacheKey);

  if (pending) {
    telemetry?.onCacheHit?.();
    return pending;
  }

  telemetry?.onCacheMiss?.();

  const request = resolveBestAudioStream(videoUrl)
    .then((streamCandidates) => {
      const selectedStream = streamCandidates[0];

      if (!selectedStream?.url) {
        throw new Error("No audio stream available for this video.");
      }

      const response: StreamResponse = {
        streamUrl: selectedStream.url,
        mimeType: selectedStream.mimeType || null,
        expiresAt: selectedStream.expiresAt,
        streamCandidates
      };

      writeCache(
        streamCache,
        cacheKey,
        response,
        Math.min(STREAM_TTL_MS, Math.max(45_000, (selectedStream.expiresAt || 0) - Date.now()))
      );
      return response;
    })
    .finally(() => {
      pendingStreamRequests.delete(cacheKey);
    });

  pendingStreamRequests.set(cacheKey, request);
  return request;
}

export async function hydrateSongWithStream(song: Song): Promise<Song> {
  const existing = getFreshSongStream(song);

  if (existing) {
    return mergeSongWithStream(song, existing);
  }

  const stream = await getAudioStream(song.videoId);
  return mergeSongWithStream(song, stream);
}

export async function hydrateSongsWithStreams(songs: Song[], count = 1): Promise<Song[]> {
  const safeCount = Math.min(Math.max(count, 0), songs.length);

  if (safeCount === 0) {
    return songs;
  }

  const hydratedLeadingSongs = await Promise.all(
    songs.slice(0, safeCount).map(async (song) => {
      try {
        return await hydrateSongWithStream(song);
      } catch {
        return song;
      }
    })
  );

  return [...hydratedLeadingSongs, ...songs.slice(safeCount)];
}
