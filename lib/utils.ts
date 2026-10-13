export function formatDuration(totalSeconds: number): string {
  if (!Number.isFinite(totalSeconds) || totalSeconds <= 0) {
    return "0:00";
  }

  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = Math.floor(totalSeconds % 60);

  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
  }

  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

export function formatPlaybackTime(totalSeconds: number): string {
  if (!Number.isFinite(totalSeconds) || totalSeconds < 0) {
    return "0:00";
  }

  const minutes = Math.floor(totalSeconds / 60);
  const seconds = Math.floor(totalSeconds % 60);
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

export function formatCompactCount(value: number): string {
  if (!Number.isFinite(value) || value <= 0) {
    return "Fresh";
  }

  if (value >= 1_000_000_000) {
    return `${(value / 1_000_000_000).toFixed(value >= 10_000_000_000 ? 0 : 1)}B`;
  }

  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(value >= 10_000_000 ? 0 : 1)}M`;
  }

  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(value >= 10_000 ? 0 : 1)}K`;
  }

  return `${value}`;
}

export function extractExpiryTimestamp(url: string): number | null {
  try {
    const parsed = new URL(url);
    const expire = parsed.searchParams.get("expire");

    if (!expire) {
      return null;
    }

    const asNumber = Number(expire);
    return Number.isFinite(asNumber) ? asNumber * 1000 : null;
  } catch {
    return null;
  }
}

export function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

export function createQueryKey(value: string): string {
  return value.trim().toLowerCase();
}

const SONG_STOP_WORDS = new Set([
  "the",
  "and",
  "for",
  "with",
  "from",
  "your",
  "you",
  "this",
  "that",
  "into",
  "onto",
  "over",
  "under",
  "official",
  "music",
  "audio",
  "video",
  "song"
]);

const ARTIST_SPLIT_PATTERN = /,|&| x | X |\/| featuring | feat\.? | ft\.? /i;

function normalizeToken(value: string) {
  return value.replace(/[^a-z0-9]+/gi, "").toLowerCase();
}

export function stripTitleNoise(value: string) {
  return value
    .replace(/\([^)]*\)/g, " ")
    .replace(/\[[^\]]*]/g, " ")
    .replace(
      /\b(official|video|music|lyrics?|lyric|audio|hd|4k|visualizer|feat\.?|ft\.?|remix|live|version|slowed|reverb|cover|karaoke|shorts?|sped\s*up|bass\s*boosted|instrumental)\b/gi,
      " "
    )
    .replace(/[|:/\\-]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function getMeaningfulSongTokens(value: string) {
  return stripTitleNoise(value)
    .split(" ")
    .map(normalizeToken)
    .filter((part) => part.length > 1 && !SONG_STOP_WORDS.has(part));
}

export function normalizeSongTitle(value: string) {
  return getMeaningfulSongTokens(value).join(" ");
}

export function getPrimaryArtistName(author: string) {
  const [primaryArtist = ""] = stripTitleNoise(author).split(ARTIST_SPLIT_PATTERN);
  return primaryArtist.trim();
}

export function buildRelatedSearchQueries(title: string, author: string) {
  const cleanedTitle = getMeaningfulSongTokens(title)
    .slice(0, 4)
    .join(" ");
  const cleanedAuthor = getMeaningfulSongTokens(getPrimaryArtistName(author) || author)
    .slice(0, 5)
    .join(" ");
  const queryAnchor = cleanedAuthor || cleanedTitle;

  const queries = [
    [cleanedAuthor || queryAnchor, cleanedTitle, "official audio"].filter(Boolean).join(" "),
    [cleanedTitle, "official audio"].filter(Boolean).join(" "),
    [cleanedAuthor || queryAnchor, "top songs official audio"].filter(Boolean).join(" ")
  ]
    .map((query) => query.trim())
    .filter(Boolean);

  return Array.from(new Set(queries));
}

export function buildRelatedSearchQuery(title: string, author: string) {
  return (
    buildRelatedSearchQueries(title, author)[0] ||
    [getPrimaryArtistName(author), normalizeSongTitle(title), "official audio"].filter(Boolean).join(" ")
  );
}

type IdleWindow = Window &
  typeof globalThis & {
    requestIdleCallback?: (callback: () => void, options?: { timeout: number }) => number;
    cancelIdleCallback?: (handle: number) => void;
  };

export function scheduleIdleTask(task: () => void, timeout = 0) {
  const idleWindow = window as IdleWindow;

  if (idleWindow.requestIdleCallback && idleWindow.cancelIdleCallback) {
    const idleCallbackId = idleWindow.requestIdleCallback(() => task(), { timeout });
    return () => idleWindow.cancelIdleCallback?.(idleCallbackId);
  }

  const timeoutId = window.setTimeout(task, 0);
  return () => window.clearTimeout(timeoutId);
}
