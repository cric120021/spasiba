"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import {
  FormEvent,
  memo,
  startTransition,
  useCallback,
  useDeferredValue,
  useEffect,
  useMemo,
  useRef,
  useState
} from "react";

import { audioController } from "@/lib/audio-controller";
import { getHomeCategoryConfig, HOME_CATEGORY_CHIPS } from "@/lib/home-category-chips";
import { prefetchCategoryFeed } from "@/lib/home-category-feed-client";
import {
  readMatchingSearchHistory,
  rememberSearchHistoryQuery,
  removeSearchHistoryQuery,
  SEARCH_HISTORY_UPDATED_EVENT,
  type SearchHistoryEntry
} from "@/lib/search-history";
import {
  cleanupLegacySearchStorage,
  fetchSearchResponseWithBudget,
  primeSearchResponse,
  readCachedSearchResponseWithBudget,
  readLocalSuggestionMatches,
  readSearchResponseFromMemory
} from "@/lib/search-client";
import type { SearchResponse, Song, UserProfile } from "@/lib/types";
import { PROFILE_UPDATED_EVENT } from "@/lib/profile-events";
import { readCachedProfile } from "@/lib/profile-client";
import { dispatchSearchSubmit } from "@/lib/search-events";
import { createQueryKey } from "@/lib/utils";
import { playerActions } from "@/store/playerStore";

import Artwork from "@/components/ui/Artwork";
import AndroidDownloadButton from "@/components/AndroidDownloadButton";

const MIN_SEARCH_LENGTH = 2;
const SUGGESTION_LIMIT = 6;
const SUGGESTION_CACHE_TTL_MS = 1000 * 60 * 60 * 24;
const SUGGESTION_CACHE_LIMIT = 48;
const SUGGESTION_DEBOUNCE_MS = 420;
const SUGGESTION_NETWORK_MIN_LENGTH = 3;
const LOCAL_SUGGESTION_CONFIDENCE_THRESHOLD = 4;
const SEARCH_HISTORY_LIMIT = 8;
const HISTORY_SWIPE_DELETE_WIDTH = 88;
const suggestionCache = new Map<string, { value: Song[]; expiresAt: number }>();
const pendingSuggestionRequests = new Map<string, Promise<Song[]>>();

type SpeechRecognitionResultShape = {
  transcript: string;
};

type SpeechRecognitionEventShape = Event & {
  results: ArrayLike<ArrayLike<SpeechRecognitionResultShape>>;
};

type SpeechRecognitionErrorEventShape = Event & {
  error?: string;
};

type SpeechRecognitionInstance = EventTarget & {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  start: () => void;
  stop: () => void;
  onstart: ((event: Event) => void) | null;
  onend: ((event: Event) => void) | null;
  onresult: ((event: SpeechRecognitionEventShape) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEventShape) => void) | null;
};

declare global {
  interface Window {
    SpeechRecognition?: new () => SpeechRecognitionInstance;
    webkitSpeechRecognition?: new () => SpeechRecognitionInstance;
  }
}

function readSuggestionCache(query: string) {
  const key = createQueryKey(query);
  const cached = suggestionCache.get(key);

  if (!cached) {
    return null;
  }

  if (cached.expiresAt <= Date.now()) {
    suggestionCache.delete(key);
    return null;
  }

  suggestionCache.delete(key);
  suggestionCache.set(key, cached);
  return cached.value;
}

function writeSuggestionCache(query: string, value: Song[]) {
  const key = createQueryKey(query);

  suggestionCache.set(key, {
    value,
    expiresAt: Date.now() + SUGGESTION_CACHE_TTL_MS
  });

  while (suggestionCache.size > SUGGESTION_CACHE_LIMIT) {
    const oldestKey = suggestionCache.keys().next().value as string | undefined;

    if (!oldestKey) {
      break;
    }

    suggestionCache.delete(oldestKey);
  }
}

function mergeUniqueSuggestions(...groups: Song[][]) {
  const merged = new Map<string, Song>();

  for (const group of groups) {
    for (const track of group) {
      if (!track?.id || merged.has(track.id)) {
        continue;
      }

      merged.set(track.id, track);
    }
  }

  return Array.from(merged.values()).slice(0, SUGGESTION_LIMIT);
}

function isStrongSuggestionMatch(query: string, track: Song) {
  const normalizedQuery = createQueryKey(query);
  const title = createQueryKey(track.title);
  const author = createQueryKey(track.author);
  const haystack = `${title} ${author}`.trim();

  if (!normalizedQuery || !haystack) {
    return false;
  }

  if (title === normalizedQuery || title.startsWith(normalizedQuery) || author.startsWith(normalizedQuery)) {
    return true;
  }

  if (haystack.includes(normalizedQuery)) {
    return true;
  }

  const queryTokens = normalizedQuery.split(/\s+/).filter(Boolean);

  if (!queryTokens.length) {
    return false;
  }

  return queryTokens.every((token) => haystack.includes(token));
}

function filterStrictSuggestionMatches(query: string, tracks: Song[]) {
  return tracks.filter((track) => isStrongSuggestionMatch(query, track)).slice(0, SUGGESTION_LIMIT);
}

function buildSeededSearchResponseForSuggestion(query: string, selectedSong: Song, sourceTracks: Song[]) {
  const deduped = new Map<string, Song>();

  [selectedSong, ...sourceTracks].forEach((track) => {
    if (!track?.id || deduped.has(track.id)) {
      return;
    }

    deduped.set(track.id, track);
  });

  return {
    query,
    results: Array.from(deduped.values()).slice(0, 12),
    cachedAt: new Date().toISOString()
  } satisfies SearchResponse;
}

async function fetchSuggestions(query: string, signal?: AbortSignal): Promise<Song[]> {
  const key = createQueryKey(query);

  if (!key) {
    return [];
  }

  const cached = readSuggestionCache(query);

  if (cached) {
    return cached;
  }

  const memoryCached = readSearchResponseFromMemory({
    query,
    purpose: "suggestion"
  });

  if (memoryCached?.results?.length) {
    const suggestions = memoryCached.results.slice(0, SUGGESTION_LIMIT);
    writeSuggestionCache(query, suggestions);
    return suggestions;
  }

  const persisted = await readCachedSearchResponseWithBudget({
    query,
    purpose: "suggestion"
  });

  if (persisted?.results?.length) {
    const suggestions = persisted.results.slice(0, SUGGESTION_LIMIT);
    writeSuggestionCache(query, suggestions);
    return suggestions;
  }

  const pending = pendingSuggestionRequests.get(key);

  if (pending) {
    return pending;
  }

  const wrappedRequest = fetchSearchResponseWithBudget({
    query,
    purpose: "suggestion",
    signal
  })
    .then((payload) => {
      const suggestions = payload.results.slice(0, SUGGESTION_LIMIT);
      writeSuggestionCache(query, suggestions);
      return suggestions;
    })
    .finally(() => {
      pendingSuggestionRequests.delete(key);
    });

  pendingSuggestionRequests.set(key, wrappedRequest);
  return wrappedRequest;
}

function SearchBarComponent() {
  const HOME_SHELL_RGB = "56 189 248";
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const headerRef = useRef<HTMLElement | null>(null);
  const headerHeightRef = useRef(112);
  const headerRafRef = useRef<number | null>(null);
  const currentQuery = searchParams.get("q") || "";
  const selectedCategoryParam = searchParams.get("category") || "";
  const activeCategoryId = useMemo(() => {
    if (selectedCategoryParam && getHomeCategoryConfig(selectedCategoryParam)) {
      return selectedCategoryParam;
    }
    if (!pathname.startsWith("/category/")) {
      return null;
    }

    const segments = pathname.split("/").filter(Boolean);
    return segments[1] || null;
  }, [pathname, selectedCategoryParam]);
  const activeCategoryConfig = useMemo(
    () => (activeCategoryId ? getHomeCategoryConfig(activeCategoryId) : null),
    [activeCategoryId]
  );
  const isCategoryPage = Boolean(activeCategoryConfig);
  const isHomePage = pathname === "/";
  const shouldAutoHideHeader = isCategoryPage || isHomePage;
  const useCompactHeader = isCategoryPage || isHomePage;
  const [query, setQuery] = useState(currentQuery);
  const [historyEntries, setHistoryEntries] = useState<SearchHistoryEntry[]>([]);
  const [suggestions, setSuggestions] = useState<Song[]>([]);
  const [profile, setProfile] = useState<UserProfile | null>(() => readCachedProfile() ?? null);
  const [isSuggestionOpen, setIsSuggestionOpen] = useState(false);
  const [isSearchInputFocused, setIsSearchInputFocused] = useState(false);
  const [isSuggestionLoading, setIsSuggestionLoading] = useState(false);
  const [suggestionError, setSuggestionError] = useState<string | null>(null);
  const [swipedHistoryQuery, setSwipedHistoryQuery] = useState<string | null>(null);
  const deferredQuery = useDeferredValue(query);
  const formRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const prefetchedRoutesRef = useRef(false);
  const recognitionRef = useRef<SpeechRecognitionInstance | null>(null);
  const voiceSearchAbortRef = useRef<AbortController | null>(null);
  const historySwipeRef = useRef<{
    query: string | null;
    startX: number;
    latestX: number;
  }>({
    query: null,
    startX: 0,
    latestX: 0
  });
  const [voiceSupported, setVoiceSupported] = useState(false);
  const [voicePending, setVoicePending] = useState(false);
  const [voiceListening, setVoiceListening] = useState(false);
  const [voiceSearching, setVoiceSearching] = useState(false);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    const root = document.documentElement;

    if (!useCompactHeader) {
      root.style.removeProperty("--measured-header-height");
      return;
    }

    const updateHeight = () => {
      if (!headerRef.current) {
        return;
      }

      const nextHeight = headerRef.current.offsetHeight;
      headerHeightRef.current = nextHeight;
      root.style.setProperty("--measured-header-height", `${nextHeight}px`);
    };

    updateHeight();
    window.addEventListener("resize", updateHeight);

    if (typeof ResizeObserver === "undefined" || !headerRef.current) {
      return () => {
        window.removeEventListener("resize", updateHeight);
        root.style.removeProperty("--measured-header-height");
      };
    }

    const observer = new ResizeObserver(() => updateHeight());
    observer.observe(headerRef.current);

    return () => {
      observer.disconnect();
      window.removeEventListener("resize", updateHeight);
      root.style.removeProperty("--measured-header-height");
    };
  }, [useCompactHeader]);

  useEffect(() => {
    setQuery(currentQuery);
    setSuggestionError(null);
    setSwipedHistoryQuery(null);
    setIsSuggestionOpen(false);

    if (pathname !== "/search") {
      setIsSearchInputFocused(false);
    }
  }, [currentQuery, pathname]);

  useEffect(() => {
    cleanupLegacySearchStorage();
  }, []);

  useEffect(() => {
    const syncHistoryEntries = () => {
      setHistoryEntries(readMatchingSearchHistory(query, SEARCH_HISTORY_LIMIT));
    };

    syncHistoryEntries();
    window.addEventListener(SEARCH_HISTORY_UPDATED_EVENT, syncHistoryEntries);

    return () => {
      window.removeEventListener(SEARCH_HISTORY_UPDATED_EVENT, syncHistoryEntries);
    };
  }, [query]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    const SpeechRecognitionConstructor = window.SpeechRecognition || window.webkitSpeechRecognition;

    if (!SpeechRecognitionConstructor) {
      setVoiceSupported(false);
      return;
    }

    const recognition = new SpeechRecognitionConstructor();
    recognition.lang = typeof navigator !== "undefined" && navigator.language ? navigator.language : "en-IN";
    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.onstart = () => {
      setVoicePending(false);
      setVoiceListening(true);
      setSuggestionError(null);
    };
    recognition.onend = () => {
      setVoicePending(false);
      setVoiceListening(false);
    };
    recognition.onerror = (event) => {
      setVoicePending(false);
      setVoiceListening(false);

      if (event.error === "aborted") {
        return;
      }

      if (event.error === "not-allowed" || event.error === "service-not-allowed") {
        playerActions.showToast("Microphone permission is blocked.");
        return;
      }

      if (event.error === "no-speech") {
        playerActions.showToast("I couldn’t hear anything.");
        return;
      }

      playerActions.showToast("Voice search is unavailable right now.");
    };

    recognitionRef.current = recognition;
    setVoiceSupported(true);

    return () => {
      recognition.onstart = null;
      recognition.onend = null;
      recognition.onresult = null;
      recognition.onerror = null;
      voiceSearchAbortRef.current?.abort();
      recognitionRef.current?.stop();
      recognitionRef.current = null;
    };
  }, []);

  const normalizedQuery = useMemo(() => deferredQuery.trim(), [deferredQuery]);
  const showSuggestions = useMemo(
    () =>
      isSuggestionOpen &&
      normalizedQuery.length >= MIN_SEARCH_LENGTH &&
      (suggestions.length > 0 || isSuggestionLoading || Boolean(suggestionError)),
    [isSuggestionLoading, isSuggestionOpen, normalizedQuery.length, suggestionError, suggestions.length]
  );

  useEffect(() => {
    function handlePointerDown(event: PointerEvent) {
      if (!formRef.current?.contains(event.target as Node)) {
        setIsSuggestionOpen(false);
        setIsSearchInputFocused(false);
        setSwipedHistoryQuery(null);
      }
    }

    document.addEventListener("pointerdown", handlePointerDown);
    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    const root = document.documentElement;
    const body = document.body;

    if (!shouldAutoHideHeader) {
      body.classList.remove("home-shell-active");
      body.classList.remove("category-shell-active");
      root.style.removeProperty("--category-accent-rgb");
      root.style.removeProperty("--category-header-progress");
      root.style.removeProperty("--category-backdrop-progress");
      root.style.removeProperty("--category-backdrop-image");
      headerRef.current?.style.removeProperty("transform");
      return;
    }

    const scroller = document.getElementById("app-scroll") || document.getElementById("app-main");
    if (isCategoryPage && activeCategoryConfig) {
      body.classList.remove("home-shell-active");
      body.classList.add("category-shell-active");
      root.style.setProperty("--category-accent-rgb", activeCategoryConfig.headerRgb);
      root.style.setProperty("--category-backdrop-image", `url('${activeCategoryConfig.backdropPath}')`);
      root.style.setProperty("--category-header-progress", "0");
      root.style.setProperty("--category-backdrop-progress", "0");
    } else if (isHomePage) {
      body.classList.add("home-shell-active");
      body.classList.remove("category-shell-active");
      root.style.setProperty("--category-accent-rgb", HOME_SHELL_RGB);
      root.style.removeProperty("--category-backdrop-image");
      root.style.setProperty("--category-header-progress", "0");
      root.style.setProperty("--category-backdrop-progress", "0");
    } else {
      body.classList.add("home-shell-active");
      body.classList.remove("category-shell-active");
      root.style.removeProperty("--category-accent-rgb");
      root.style.removeProperty("--category-header-progress");
      root.style.removeProperty("--category-backdrop-progress");
      root.style.removeProperty("--category-backdrop-image");
    }
    let latestScrollTop = Math.max(scroller?.scrollTop || 0, 0);
    let previousAppliedScrollTop = latestScrollTop;
    let headerOffset = 0;
    let lastHeaderProgress = -1;
    let lastBackdropProgress = -1;
    let lastScrollSignalAt = 0;

    const applyHeaderOffset = (nextOffset: number) => {
      if (Math.abs(nextOffset - headerOffset) < 0.1) {
        return;
      }

      headerOffset = nextOffset;
      headerRef.current?.style.setProperty("transform", `translate3d(0, ${-nextOffset}px, 0)`);
    };

    const syncHeader = () => {
      headerRafRef.current = null;
      const scrollTop = latestScrollTop;

      if (shouldAutoHideHeader) {
        const headerFadeDistance =
          typeof window !== "undefined" ? Math.max(window.innerHeight * 0.4, 240) : 240;
        const backdropFadeDistance =
          typeof window !== "undefined" ? Math.max(window.innerHeight * 0.52, 340) : 340;
        const headerProgress = Math.min(scrollTop / headerFadeDistance, 1);
        const backdropProgress = Math.min(scrollTop / backdropFadeDistance, 1);

        if (Math.abs(headerProgress - lastHeaderProgress) >= 0.002) {
          lastHeaderProgress = headerProgress;
          root.style.setProperty("--category-header-progress", headerProgress.toFixed(3));
        }

        if (Math.abs(backdropProgress - lastBackdropProgress) >= 0.002) {
          lastBackdropProgress = backdropProgress;
          root.style.setProperty("--category-backdrop-progress", backdropProgress.toFixed(3));
        }
      }

      const delta = scrollTop - previousAppliedScrollTop;
      previousAppliedScrollTop = scrollTop;
      const headerHeight = headerHeightRef.current || headerRef.current?.offsetHeight || 112;
      const maxHeaderOffset = Math.max(headerHeight - 4, 68);

      if (scrollTop <= 0.5) {
        applyHeaderOffset(0);
        return;
      }

      if (Math.abs(delta) < 0.03) {
        return;
      }

      applyHeaderOffset(Math.min(Math.max(headerOffset + delta, 0), maxHeaderOffset));
    };

    const runScrollLoop = (timestamp: number) => {
      syncHeader();

      if (timestamp - lastScrollSignalAt < 96) {
        headerRafRef.current = window.requestAnimationFrame(runScrollLoop);
        return;
      }

      headerRafRef.current = null;
    };

    const handleScroll = () => {
      latestScrollTop = Math.max(scroller?.scrollTop || 0, 0);
      lastScrollSignalAt = performance.now();

      if (headerRafRef.current === null) {
        headerRafRef.current = window.requestAnimationFrame(runScrollLoop);
      }
    };

    applyHeaderOffset(0);
    syncHeader();
    scroller?.addEventListener("scroll", handleScroll, { passive: true });

    return () => {
      if (headerRafRef.current !== null) {
        window.cancelAnimationFrame(headerRafRef.current);
        headerRafRef.current = null;
      }

      body.classList.remove("home-shell-active");
      body.classList.remove("category-shell-active");
      root.style.removeProperty("--category-accent-rgb");
      root.style.removeProperty("--category-header-progress");
      root.style.removeProperty("--category-backdrop-progress");
      root.style.removeProperty("--category-backdrop-image");
      headerRef.current?.style.removeProperty("transform");
      scroller?.removeEventListener("scroll", handleScroll);
    };
  }, [HOME_SHELL_RGB, activeCategoryConfig, isCategoryPage, isHomePage, shouldAutoHideHeader]);

  useEffect(() => {
    const handleProfileUpdate = () => {
      setProfile(readCachedProfile() ?? null);
    };

    window.addEventListener(PROFILE_UPDATED_EVENT, handleProfileUpdate);

    return () => {
      window.removeEventListener(PROFILE_UPDATED_EVENT, handleProfileUpdate);
    };
  }, []);

  useEffect(() => {
    if (prefetchedRoutesRef.current) {
      return;
    }

    prefetchedRoutesRef.current = true;

    const idleWindow = window as Window &
      typeof globalThis & {
        requestIdleCallback?: (callback: () => void, options?: { timeout: number }) => number;
      };

    const prefetchRoutes = () => {
      router.prefetch("/library");
      router.prefetch("/profile");
      router.prefetch("/search");
    };

    if (idleWindow.requestIdleCallback) {
      idleWindow.requestIdleCallback(prefetchRoutes, { timeout: 1800 });
      return;
    }

    const timer = window.setTimeout(prefetchRoutes, 350);
    return () => window.clearTimeout(timer);
  }, [router]);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();
    const normalizedCurrentQuery = createQueryKey(currentQuery);

    if (!isSuggestionOpen || normalizedQuery.length < MIN_SEARCH_LENGTH) {
      setSuggestions([]);
      setIsSuggestionLoading(false);
      setSuggestionError(null);
      return () => {
        cancelled = true;
        controller.abort();
      };
    }

    if (pathname === "/search" && createQueryKey(normalizedQuery) === normalizedCurrentQuery) {
      setSuggestions([]);
      setIsSuggestionLoading(false);
      setSuggestionError(null);

      return () => {
        cancelled = true;
        controller.abort();
      };
    }

    const cached = readSuggestionCache(normalizedQuery);

    if (cached) {
      setSuggestions(cached);
      setIsSuggestionLoading(false);
      setSuggestionError(null);
      return () => {
        cancelled = true;
        controller.abort();
      };
    }

    setSuggestionError(null);
    setIsSuggestionLoading(true);

    const timer = window.setTimeout(() => {
      void (async () => {
        const [localSuggestions, exactCachedResponse] = await Promise.all([
          readLocalSuggestionMatches(normalizedQuery, SUGGESTION_LIMIT),
          readCachedSearchResponseWithBudget({
            query: normalizedQuery,
            purpose: "suggestion"
          })
        ]);

        if (cancelled) {
          return;
        }

        const cachedSuggestions = filterStrictSuggestionMatches(
          normalizedQuery,
          exactCachedResponse?.results?.slice(0, SUGGESTION_LIMIT) || []
        );
        const nextSuggestions = mergeUniqueSuggestions(
          filterStrictSuggestionMatches(normalizedQuery, localSuggestions),
          cachedSuggestions
        );
        const localConfidence = historyEntries.length + nextSuggestions.length;

        if (nextSuggestions.length > 0) {
          writeSuggestionCache(normalizedQuery, nextSuggestions);
          setSuggestions(nextSuggestions);
        } else {
          setSuggestions([]);
        }

        if (
          normalizedQuery.length < SUGGESTION_NETWORK_MIN_LENGTH ||
          localConfidence >= LOCAL_SUGGESTION_CONFIDENCE_THRESHOLD
        ) {
          setIsSuggestionLoading(false);
          return;
        }

        try {
          const networkSuggestions = filterStrictSuggestionMatches(
            normalizedQuery,
            await fetchSuggestions(normalizedQuery, controller.signal)
          );

          if (cancelled) {
            return;
          }

          const mergedSuggestions = mergeUniqueSuggestions(nextSuggestions, networkSuggestions);
          writeSuggestionCache(normalizedQuery, mergedSuggestions);
          setSuggestions(mergedSuggestions);
          setIsSuggestionLoading(false);
        } catch (error) {
          if (cancelled) {
            return;
          }

          if (error instanceof DOMException && error.name === "AbortError") {
            return;
          }

          setIsSuggestionLoading(false);

          if (!nextSuggestions.length) {
            setSuggestionError(error instanceof Error ? error.message : "Suggestion search failed.");
          }
        }
      })();
    }, SUGGESTION_DEBOUNCE_MS);

    return () => {
      cancelled = true;
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [currentQuery, historyEntries.length, isSuggestionOpen, normalizedQuery, pathname]);

  const submitSearch = useCallback(
    (nextQuery: string) => {
      const trimmed = nextQuery.trim();
      const normalizedTrimmed = createQueryKey(trimmed);
      const normalizedCurrentQuery = createQueryKey(currentQuery);

      setIsSuggestionOpen(false);
      setIsSearchInputFocused(false);
      setSwipedHistoryQuery(null);
      inputRef.current?.blur();

      if (!trimmed) {
        if (pathname === "/search") {
          startTransition(() => {
            router.push("/search", { scroll: false });
          });
        }
        return;
      }

      rememberSearchHistoryQuery(trimmed);

      if (pathname === "/search" && normalizedTrimmed && normalizedTrimmed === normalizedCurrentQuery) {
        dispatchSearchSubmit(trimmed);
        return;
      }

      startTransition(() => {
        router.push(`/search?q=${encodeURIComponent(trimmed)}`, { scroll: false });
      });
    },
    [currentQuery, pathname, router]
  );

  const handleHistoryTouchStart = useCallback((historyQuery: string, clientX: number) => {
    historySwipeRef.current = {
      query: historyQuery,
      startX: clientX,
      latestX: clientX
    };
  }, []);

  const handleHistoryTouchMove = useCallback((historyQuery: string, clientX: number) => {
    if (historySwipeRef.current.query !== historyQuery) {
      return;
    }

    historySwipeRef.current.latestX = clientX;
    const deltaX = clientX - historySwipeRef.current.startX;

    if (deltaX <= -28) {
      setSwipedHistoryQuery(historyQuery);
      return;
    }

    if (deltaX >= 18) {
      setSwipedHistoryQuery(null);
    }
  }, []);

  const handleHistoryTouchEnd = useCallback(() => {
    historySwipeRef.current = {
      query: null,
      startX: 0,
      latestX: 0
    };
  }, []);

  const handleDeleteHistoryQuery = useCallback((historyQuery: string) => {
    removeSearchHistoryQuery(historyQuery);
    setSwipedHistoryQuery((previous) => (previous === historyQuery ? null : previous));
  }, []);

  const searchAndPlayFirst = useCallback(
    async (spokenQuery: string) => {
      const trimmed = spokenQuery.trim();

      if (!trimmed) {
        return;
      }

      setVoiceSearching(true);
      setSuggestionError(null);
      setQuery(trimmed);
      setIsSuggestionOpen(false);

      let controller: AbortController | null = null;

      try {
        voiceSearchAbortRef.current?.abort();
        controller = new AbortController();
        voiceSearchAbortRef.current = controller;
        rememberSearchHistoryQuery(trimmed);
        const payload = await fetchSearchResponseWithBudget({
          query: trimmed,
          purpose: "search",
          signal: controller.signal
        });
        const results = payload.results || [];
        const firstTrack = results[0];

        startTransition(() => {
          router.push(`/search?q=${encodeURIComponent(trimmed)}`, { scroll: false });
        });

        if (!firstTrack) {
          playerActions.showToast(`Nothing matched "${trimmed}".`);
          return;
        }

        await audioController.playSong(firstTrack, results.length ? results : [firstTrack], 0);
        playerActions.showToast(`Playing "${firstTrack.title}".`);
      } catch (error) {
        if (error instanceof DOMException && error.name === "AbortError") {
          return;
        }

        playerActions.showToast(error instanceof Error ? error.message : "Voice search failed.");
      } finally {
        if (controller && voiceSearchAbortRef.current === controller) {
          voiceSearchAbortRef.current = null;
        }
        setVoiceSearching(false);
      }
    },
    [router]
  );

  const handleCancelVoiceSearch = useCallback(() => {
    recognitionRef.current?.stop();
    voiceSearchAbortRef.current?.abort();
    voiceSearchAbortRef.current = null;
    setVoicePending(false);
    setVoiceListening(false);
    setVoiceSearching(false);
  }, []);

  const handleVoiceSearch = useCallback(() => {
    if (voiceSearching) {
      return;
    }

    const recognition = recognitionRef.current;

    if (!recognition || !voiceSupported) {
      playerActions.showToast("Voice search is not supported on this device.");
      return;
    }

    if (voiceListening) {
      recognition.stop();
      return;
    }

    setVoicePending(true);

    recognition.onresult = (event) => {
      const transcript = Array.from(event.results)
        .flatMap((result) => Array.from(result))
        .map((entry) => entry.transcript)
        .join(" ")
        .trim();

      if (!transcript) {
        playerActions.showToast("I couldn’t understand that.");
        return;
      }

      void searchAndPlayFirst(transcript);
    };

    try {
      recognition.start();
    } catch {
      setVoicePending(false);
      playerActions.showToast("Voice search is already running.");
    }
  }, [searchAndPlayFirst, voiceListening, voiceSearching, voiceSupported]);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    submitSearch(query);
  };

  const renderProfileAvatar = () =>
    profile?.profileImageDataUrl ? (
      <Artwork
        src={profile.profileImageDataUrl}
        alt={profile?.name || "Profile"}
        radius="circle"
        className="h-full w-full"
        sizes="48px"
        quality={80}
      />
    ) : (
      <span className="flex h-full w-full items-center justify-center rounded-[inherit] bg-white/[0.04]">
        <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4 sm:h-5 sm:w-5">
          <path
            fill="currentColor"
            d="M12 12a4 4 0 1 0-4-4a4 4 0 0 0 4 4m0 2c-4.418 0-8 2.015-8 4.5V20h16v-1.5c0-2.485-3.582-4.5-8-4.5"
          />
        </svg>
      </span>
    );

  const prefetchCategoryPage = useCallback(
    (categoryId: string) => {
      router.prefetch(`/category/${categoryId}`);
    },
    [router]
  );

  const showVoiceOverlay = voicePending || voiceListening || voiceSearching;
  const voiceOverlayLabel = voiceSearching ? "Finding and playing your song..." : voiceListening ? "Listening..." : "Opening microphone...";
  const voiceOverlayHint = voiceSearching ? "Hold on for a second." : "Speak naturally. I’ll play the top result.";
  const renderVoiceTrigger = (className: string) => (
    <button
      type="button"
      onClick={handleVoiceSearch}
      className={[
        className,
        voicePending || voiceListening || voiceSearching
          ? "bg-teal-300/14 text-white shadow-[0_0_0_1px_rgba(94,234,212,0.18)]"
          : ""
      ]
        .filter(Boolean)
        .join(" ")}
      aria-label={voiceListening ? "Stop voice search" : "Start voice search"}
      title={voiceListening ? "Stop voice search" : "Voice search"}
    >
      <svg aria-hidden viewBox="0 0 24 24" className="h-4.5 w-4.5 sm:h-5 sm:w-5">
        <path
          fill="currentColor"
          d="M12 2.8l1.76 5.44 5.44 1.76-5.44 1.76L12 17.2l-1.76-5.44-5.44-1.76 5.44-1.76zm6.5 10.7.96 2.94 2.94.96-2.94.96-.96 2.94-.96-2.94-2.94-.96 2.94-.96zm-13 1.2.96 2.94 2.94.96-2.94.96-.96 2.94-.96-2.94-2.94-.96 2.94-.96z"
        />
      </svg>
    </button>
  );

  const renderAiOrb = (size: "sm" | "lg") => {
    const outer = size === "sm" ? "h-11 w-11" : "h-28 w-28";
    const mid = size === "sm" ? "inset-[3px]" : "inset-[9px]";
    const inner = size === "sm" ? "inset-[9px]" : "inset-[22px]";
    const sparkle = size === "sm" ? "h-3 w-3" : "h-7 w-7";

    return (
      <span className={`relative block ${outer}`}>
        <span
          className={`absolute inset-0 rounded-full bg-[conic-gradient(from_210deg,rgba(45,212,191,0.38),rgba(56,189,248,0.28),rgba(255,255,255,0.16),rgba(45,212,191,0.38))] ${
            voiceListening || voiceSearching ? "animate-spin [animation-duration:6s]" : ""
          }`}
        />
        <span className={`absolute ${mid} rounded-full bg-[#07101a]`} />
        <span className={`absolute ${inner} rounded-full border border-white/8 bg-[radial-gradient(circle_at_35%_35%,rgba(58,190,255,0.16),rgba(8,12,20,0.98)_54%,rgba(3,6,12,0.99)_100%)] shadow-[0_0_16px_rgba(56,189,248,0.10)]`}>
          <span className="absolute inset-[16%] rounded-full border border-white/8" />
        </span>
        <span className="absolute inset-0 flex items-center justify-center">
          <svg aria-hidden viewBox="0 0 24 24" className={`${sparkle} text-white/82`}>
            <path
              fill="currentColor"
              d="M12 2.8l1.76 5.44 5.44 1.76-5.44 1.76L12 17.2l-1.76-5.44-5.44-1.76 5.44-1.76zm6.5 10.7.96 2.94 2.94.96-2.94.96-.96 2.94-.96-2.94-2.94-.96 2.94-.96zm-13 1.2.96 2.94 2.94.96-2.94.96-.96 2.94-.96-2.94-2.94-.96 2.94-.96z"
            />
          </svg>
        </span>
      </span>
    );
  };

  return (
    <>
      <header
        ref={headerRef}
        className={[
          "app-header ui-screen sticky top-0 z-40 overflow-visible border-b border-white/8 before:pointer-events-none before:absolute before:inset-x-0 before:top-0 before:h-full",
          useCompactHeader
            ? "app-header--category shadow-none before:bg-transparent"
            : "app-header--home shadow-none before:bg-transparent"
        ].join(" ")}
      >
        {useCompactHeader ? (
          <div className="relative mx-auto flex w-full max-w-7xl flex-col gap-4 px-4 pb-2.5 pt-2 sm:gap-[18px] sm:px-6 sm:pb-3 sm:pt-2.5 lg:px-8">
            <div className="flex items-center justify-between gap-3">
              <Link href="/" className="group flex shrink-0 items-center gap-2.5">
                <div className="flex h-9 w-9 items-center justify-center rounded-full border border-white/8 bg-white/[0.04] text-[11px] font-semibold tracking-[0.18em] text-white transition-colors duration-200 group-hover:bg-white/[0.07]">
                  SP
                </div>
                <div className="min-w-0">
                  <p className="text-[9px] uppercase tracking-[0.24em] text-slate-300/66">Music</p>
                  <h1 className="text-[16px] font-semibold tracking-tight text-white">Spasiba</h1>
                </div>
              </Link>

              <div className="flex items-center gap-2">
                {renderVoiceTrigger("flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/[0.05] text-slate-100/84 transition hover:bg-white/[0.09]")}
                <Link
                  href="/library"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/[0.05] text-slate-100/84 transition hover:bg-white/[0.09]"
                  aria-label="Open library"
      >
                  <svg aria-hidden viewBox="0 0 24 24" className="h-4.5 w-4.5">
                    <path
                      fill="currentColor"
                      d="M5 4a2 2 0 0 0-2 2v13.5a.5.5 0 0 0 .8.4L8 17l4.2 2.9a.5.5 0 0 0 .8-.4V6a2 2 0 0 0-2-2zm11 1h3v2h-3zm0 5h3v2h-3zm0 5h3v2h-3z"
                    />
                  </svg>
                </Link>
                <Link
                  href="/search"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/[0.05] text-slate-100/84 transition hover:bg-white/[0.09]"
                  aria-label="Open search"
                >
                  <svg aria-hidden viewBox="0 0 24 24" className="h-4.5 w-4.5">
                    <path
                      fill="currentColor"
                      d="M10.5 3a7.5 7.5 0 0 1 5.994 12.01l4.248 4.246a1 1 0 1 1-1.414 1.415l-4.248-4.247A7.5 7.5 0 1 1 10.5 3m0 2a5.5 5.5 0 1 0 0 11a5.5 5.5 0 0 0 0-11"
                    />
                  </svg>
                </Link>
                <AndroidDownloadButton />
                <Link
                  href="/profile"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/[0.05] text-slate-100/84 transition hover:bg-white/[0.09]"
                  aria-label="Open profile"
                >
                  {renderProfileAvatar()}
                </Link>
              </div>
            </div>

            <div className="category-page-shell__chips snap-x snap-mandatory !gap-2 !px-0 !pb-0.5">
              {HOME_CATEGORY_CHIPS.map((category) => {
                const isActive = activeCategoryId === category.id;
                const categoryTargetUrl = isActive ? "/" : `/?category=${category.id}`;

                return (
                  <Link
                    key={category.id}
                    href={categoryTargetUrl}
                    onClick={(e) => {
                      if (isHomePage) {
                        e.preventDefault();
                        if (isActive) {
                          router.push("/", { scroll: false });
                        } else {
                          router.push(`/?category=${category.id}`, { scroll: false });
                        }
                      }
                    }}
                    onMouseEnter={() => {
                      if (!isActive) {
                        prefetchCategoryFeed(category.id);
                      }
                    }}
                    onFocus={() => {
                      if (!isActive) {
                        prefetchCategoryFeed(category.id);
                      }
                    }}
                    onTouchStart={() => {
                      if (!isActive) {
                        prefetchCategoryFeed(category.id);
                      }
                    }}
                    className={[
                      "snap-start whitespace-nowrap rounded-full px-3.5 py-2 text-[11px] font-medium transition",
                      isActive
                        ? "bg-white font-semibold text-slate-950 shadow-[0_12px_32px_rgba(255,255,255,0.14)]"
                        : "border border-white/[0.07] bg-white/[0.05] text-white/82 hover:bg-white/10"
                    ].join(" ")}
                  >
                    {category.title}
                  </Link>
                );
              })}
            </div>
          </div>
        ) : (
          <div className="relative mx-auto flex w-full max-w-7xl flex-col gap-1.5 px-3 py-1.5 sm:flex-row sm:items-center sm:gap-2.5 sm:px-6 sm:py-2.5 lg:px-8">
            <div className="flex items-center justify-between gap-3 sm:flex-none">
              <Link href="/" className="group flex shrink-0 items-center gap-2.5">
                <div className="flex h-9 w-9 items-center justify-center rounded-full border border-white/8 bg-white/[0.04] text-[11px] font-semibold tracking-[0.18em] text-white transition-colors duration-200 group-hover:bg-white/[0.07]">
                  SP
                </div>
                <div className="min-w-0">
                  <p className="text-[9px] uppercase tracking-[0.24em] text-slate-400/76">Music</p>
                  <h1 className="text-[16px] font-semibold tracking-tight text-white">Spasiba</h1>
                </div>
              </Link>

              <div className="flex items-center gap-2 sm:hidden">
                {renderVoiceTrigger("flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/[0.05] text-slate-200 transition hover:bg-white/[0.08]")}
                <Link
                  href="/library"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/[0.05] text-slate-200 transition hover:bg-white/[0.08]"
                  aria-label="Open playlists"
                >
                  <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4">
                    <path
                      fill="currentColor"
                      d="M5 4a2 2 0 0 0-2 2v13.5a.5.5 0 0 0 .8.4L8 17l4.2 2.9a.5.5 0 0 0 .8-.4V6a2 2 0 0 0-2-2zm11 1h3v2h-3zm0 5h3v2h-3zm0 5h3v2h-3z"
                    />
                  </svg>
                </Link>
                <AndroidDownloadButton />
                <Link
                  href="/profile"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/[0.05] text-slate-200 transition hover:bg-white/[0.08]"
                  aria-label="Open profile"
                >
                  {renderProfileAvatar()}
                </Link>
              </div>
            </div>

            <div ref={formRef} className="relative w-full min-w-0 sm:ml-auto sm:max-w-3xl sm:flex-1">
              <form onSubmit={handleSubmit}>
                <div
                  className={`flex items-center gap-2 rounded-[18px] border px-3 py-1.5 shadow-[0_8px_18px_rgba(0,0,0,0.14)] transition duration-200 sm:gap-3 sm:rounded-full sm:px-4 sm:py-2 ${
                    voiceListening
                      ? "border-teal-300/18 bg-[rgba(10,12,16,0.94)] shadow-[0_12px_22px_rgba(4,20,30,0.18)]"
                      : "border-white/[0.06] bg-[rgba(255,255,255,0.04)] focus-within:border-white/[0.08] focus-within:bg-[rgba(255,255,255,0.055)] focus-within:shadow-[0_10px_20px_rgba(3,10,24,0.16)]"
                  }`}
                >
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/[0.02] text-white/52 sm:h-10 sm:w-10">
                    <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4">
                      <path
                        fill="currentColor"
                        d="M10.5 3a7.5 7.5 0 0 1 5.994 12.01l4.248 4.246a1 1 0 1 1-1.414 1.415l-4.248-4.247A7.5 7.5 0 1 1 10.5 3m0 2a5.5 5.5 0 1 0 0 11a5.5 5.5 0 0 0 0-11"
                      />
                    </svg>
                  </span>
                  <button
                    type="button"
                    onClick={handleVoiceSearch}
                    disabled={voiceSearching}
                    className="hidden"
                    aria-hidden="true"
                    tabIndex={-1}
                  >
                    Voice search
                  </button>
                  <input
                    value={query}
                    ref={inputRef}
                    onChange={(event) => {
                      setQuery(event.target.value);
                      setIsSuggestionOpen(true);
                    }}
                    onFocus={() => {
                      if (pathname !== "/search") {
                        const nextRoute = query.trim() ? `/search?q=${encodeURIComponent(query.trim())}` : "/search";
                        startTransition(() => {
                          router.push(nextRoute, { scroll: false });
                        });
                      }

                      if (query.trim().length >= MIN_SEARCH_LENGTH) {
                        setIsSuggestionOpen(true);
                      }
                    }}
                    onBlur={() => {
                      window.setTimeout(() => {
                        const activeElement = document.activeElement;

                        if (activeElement !== inputRef.current && !formRef.current?.contains(activeElement)) {
                          setIsSearchInputFocused(false);
                        }
                      }, 0);
                    }}
                    placeholder={voiceListening || voicePending ? "Listening..." : voiceSearching ? "Searching and playing..." : "Search songs, artists, or albums..."}
                    className="min-w-0 w-full bg-transparent text-[15px] text-white placeholder:text-slate-400/88 focus:outline-none sm:text-[15px]"
                    spellCheck={false}
                    autoComplete="off"
                    autoCorrect="off"
                    autoCapitalize="none"
                    enterKeyHint="search"
                  />
                  <button
                    type="submit"
                    className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-white/[0.08] bg-white/[0.04] text-white/84 transition hover:bg-white/[0.07] sm:hidden"
                    aria-label="Search"
                  >
                    <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4">
                      <path
                        fill="currentColor"
                        d="M10.5 3a7.5 7.5 0 0 1 5.994 12.01l4.248 4.246a1 1 0 1 1-1.414 1.415l-4.248-4.247A7.5 7.5 0 1 1 10.5 3m0 2a5.5 5.5 0 1 0 0 11a5.5 5.5 0 0 0 0-11"
                      />
                    </svg>
                  </button>
                  <button
                    type="submit"
                    className="hidden shrink-0 rounded-full border border-white/[0.08] bg-white/[0.04] px-3 py-1.5 text-[10px] font-medium uppercase tracking-[0.18em] text-white/78 transition hover:bg-white/[0.07] sm:inline-flex"
                  >
                    Search
                  </button>
                </div>
              </form>

              {showSuggestions ? (
                <div className="absolute inset-x-0 top-full mt-2 max-h-[min(60vh,28rem)] overflow-y-auto rounded-[26px] border border-white/10 bg-[#07101b]/96 p-2 shadow-[0_32px_90px_rgba(0,0,0,0.58)] backdrop-blur-2xl sm:mt-3 sm:rounded-[28px]">
                  {isSuggestionLoading ? (
                    <div className="space-y-2 p-2">
                      {Array.from({ length: 4 }).map((_, index) => (
                    <div key={index} className="flex items-center gap-3 rounded-[18px] px-2 py-2.5 sm:rounded-[20px] sm:px-3 sm:py-3">
                      <div className="shimmer h-10 w-10 rounded-[18px] sm:h-12 sm:w-12 sm:rounded-2xl" />
                      <div className="min-w-0 flex-1 space-y-2">
                        <div className="shimmer h-4 w-2/3 rounded-full" />
                        <div className="shimmer h-3 w-1/3 rounded-full" />
                      </div>
                    </div>
                  ))}
                </div>
              ) : null}

              {!isSuggestionLoading && suggestionError ? (
                <div className="rounded-[22px] px-4 py-4 text-sm text-rose-100/90">{suggestionError}</div>
              ) : null}

              {!isSuggestionLoading && !suggestionError && suggestions.length > 0 ? (
                <div className="space-y-1">
                  {suggestions.map((song) => (
                    <button
                      key={song.id}
                      type="button"
                      onMouseDown={(event) => event.preventDefault()}
                      onClick={() => {
                        setQuery(song.title);
                        submitSearch(song.title);
                      }}
                      className="flex w-full items-center gap-2.5 rounded-[20px] px-2 py-2.5 text-left transition hover:bg-white/[0.06] sm:gap-3 sm:rounded-[22px] sm:px-3 sm:py-3"
                    >
                      <Artwork
                        src={song.thumbnail}
                        alt={song.title}
                        size="40px"
                        radius="card"
                        sizes="(max-width: 640px) 40px, 48px"
                        quality={70}
                        className="shrink-0 sm:h-12 sm:w-12"
                      />
                      <div className="min-w-0 flex-1">
                        <p className="line-clamp-1 text-[13px] font-medium text-white sm:text-sm">{song.title}</p>
                        <p className="line-clamp-1 text-xs text-slate-400">{song.author}</p>
                      </div>
                      <span className="hidden shrink-0 text-[11px] uppercase tracking-[0.2em] text-slate-400 sm:block">{song.duration}</span>
                    </button>
                  ))}
                </div>
                  ) : null}
                </div>
              ) : null}
            </div>

            <div className="hidden shrink-0 items-center gap-2 sm:flex">
              {renderVoiceTrigger("flex h-10 w-10 items-center justify-center rounded-[18px] bg-white/[0.05] text-slate-200 transition hover:bg-white/[0.08] sm:h-11 sm:w-11 sm:rounded-2xl")}
              <Link
                href="/library"
                className="flex h-10 w-10 items-center justify-center rounded-[18px] bg-white/[0.05] text-slate-200 transition hover:bg-white/[0.08] sm:h-11 sm:w-11 sm:rounded-2xl"
                aria-label="Open playlists"
              >
                <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4 sm:h-5 sm:w-5">
                  <path
                    fill="currentColor"
                    d="M5 4a2 2 0 0 0-2 2v13.5a.5.5 0 0 0 .8.4L8 17l4.2 2.9a.5.5 0 0 0 .8-.4V6a2 2 0 0 0-2-2zm11 1h3v2h-3zm0 5h3v2h-3zm0 5h3v2h-3z"
                  />
                </svg>
              </Link>
              <Link
                href="/profile"
                className="flex h-10 w-10 items-center justify-center rounded-[18px] bg-white/[0.05] text-slate-200 transition hover:bg-white/[0.08] sm:h-11 sm:w-11 sm:rounded-2xl"
                aria-label="Open profile"
              >
                {renderProfileAvatar()}
              </Link>
            </div>
          </div>
        )}
      </header>

      {showVoiceOverlay ? (
        <div className="fixed inset-0 z-[160] flex items-center justify-center bg-slate-950/72 backdrop-blur-md">
          <div className="flex flex-col items-center gap-6 px-6 text-center">
            <div className="relative flex h-36 w-36 items-center justify-center">
              <div className={`absolute inset-0 rounded-full border border-white/8 bg-white/[0.03] ${voiceSearching ? "animate-ping" : "animate-pulse"}`} />
              <div className="absolute inset-[16%] rounded-full border border-white/7 bg-white/[0.02]" />
              <div className="relative">{renderAiOrb("lg")}</div>
            </div>

            <div className="space-y-2">
              <p className="text-[20px] font-semibold tracking-tight text-white sm:text-[24px]">{voiceOverlayLabel}</p>
              <p className="text-sm text-slate-300/82 sm:text-base">{voiceOverlayHint}</p>
            </div>

            <button
              type="button"
              onClick={handleCancelVoiceSearch}
              className="inline-flex items-center justify-center rounded-full border border-white/10 bg-white/[0.05] px-5 py-2.5 text-sm font-medium text-white transition hover:bg-white/[0.1]"
            >
              Cancel
            </button>
          </div>
        </div>
      ) : null}
    </>
  );
}

const SearchBar = memo(SearchBarComponent);

export default SearchBar;
