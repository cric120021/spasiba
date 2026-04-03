"use client";

import Image from "next/image";
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

import type { SearchResponse, Song, UserProfile } from "@/lib/types";
import { PROFILE_UPDATED_EVENT } from "@/lib/profile-events";
import { fetchProfile } from "@/lib/profile-client";
import { createQueryKey } from "@/lib/utils";

const MIN_SEARCH_LENGTH = 2;
const SUGGESTION_LIMIT = 6;
const SUGGESTION_CACHE_TTL_MS = 1000 * 60 * 10;
const SUGGESTION_CACHE_LIMIT = 48;
const suggestionCache = new Map<string, { value: Song[]; expiresAt: number }>();
const pendingSuggestionRequests = new Map<string, Promise<Song[]>>();

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

async function fetchSuggestions(query: string, signal?: AbortSignal): Promise<Song[]> {
  const key = createQueryKey(query);

  if (!key) {
    return [];
  }

  const cached = readSuggestionCache(query);

  if (cached) {
    return cached;
  }

  const pending = pendingSuggestionRequests.get(key);

  if (pending) {
    return pending;
  }

  const request = fetch(`/api/search?q=${encodeURIComponent(query)}`, {
    method: "GET",
    signal,
    headers: {
      Accept: "application/json"
    }
  })
    .then(async (response) => {
      if (!response.ok) {
        const payload = (await response.json().catch(() => null)) as { error?: string } | null;
        throw new Error(payload?.error || "Suggestion search failed.");
      }

      const payload = (await response.json()) as SearchResponse;
      const suggestions = payload.results.slice(0, SUGGESTION_LIMIT);
      writeSuggestionCache(query, suggestions);
      return suggestions;
    })
    .finally(() => {
      pendingSuggestionRequests.delete(key);
    });

  pendingSuggestionRequests.set(key, request);
  return request;
}

function SearchBarComponent() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const currentQuery = searchParams.get("q") || "";
  const [query, setQuery] = useState(currentQuery);
  const [suggestions, setSuggestions] = useState<Song[]>([]);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [isSuggestionOpen, setIsSuggestionOpen] = useState(false);
  const [isSuggestionLoading, setIsSuggestionLoading] = useState(false);
  const [suggestionError, setSuggestionError] = useState<string | null>(null);
  const deferredQuery = useDeferredValue(query);
  const formRef = useRef<HTMLDivElement | null>(null);
  const prefetchedRoutesRef = useRef(false);

  useEffect(() => {
    setQuery(currentQuery);
    setIsSuggestionOpen(false);
    setSuggestionError(null);
  }, [currentQuery]);

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
      }
    }

    document.addEventListener("pointerdown", handlePointerDown);
    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, []);

  useEffect(() => {
    let cancelled = false;

    const loadProfile = async () => {
      try {
        const nextProfile = await fetchProfile();

        if (!cancelled) {
          setProfile(nextProfile);
        }
      } catch {
        if (!cancelled) {
          setProfile(null);
        }
      }
    };

    void loadProfile();

    const handleProfileUpdate = () => {
      void loadProfile();
    };

    window.addEventListener(PROFILE_UPDATED_EVENT, handleProfileUpdate);
    window.addEventListener("focus", handleProfileUpdate);

    return () => {
      cancelled = true;
      window.removeEventListener(PROFILE_UPDATED_EVENT, handleProfileUpdate);
      window.removeEventListener("focus", handleProfileUpdate);
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

    if (!isSuggestionOpen || normalizedQuery.length < MIN_SEARCH_LENGTH) {
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

    setIsSuggestionLoading(true);
    setSuggestionError(null);

    const timer = window.setTimeout(() => {
      void fetchSuggestions(normalizedQuery, controller.signal)
        .then((nextSuggestions) => {
          if (cancelled) {
            return;
          }

          setSuggestions(nextSuggestions);
          setIsSuggestionLoading(false);
        })
        .catch((error) => {
          if (cancelled) {
            return;
          }

          if (error instanceof DOMException && error.name === "AbortError") {
            return;
          }

          setSuggestions([]);
          setIsSuggestionLoading(false);
          setSuggestionError(error instanceof Error ? error.message : "Suggestion search failed.");
        });
    }, 250);

    return () => {
      cancelled = true;
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [isSuggestionOpen, normalizedQuery]);

  const submitSearch = useCallback(
    (nextQuery: string) => {
      const trimmed = nextQuery.trim();

      setIsSuggestionOpen(false);

      if (!trimmed) {
        if (pathname === "/search") {
          startTransition(() => {
            router.push("/search", { scroll: false });
          });
        }
        return;
      }

      startTransition(() => {
        router.push(`/search?q=${encodeURIComponent(trimmed)}`, { scroll: false });
      });
    },
    [pathname, router]
  );

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    submitSearch(query);
  };

  const renderProfileAvatar = () =>
    profile?.profileImageDataUrl ? (
      <div className="relative h-full w-full overflow-hidden rounded-[inherit]">
        <Image src={profile.profileImageDataUrl} alt={profile?.name || "Profile"} fill unoptimized className="object-cover" />
      </div>
    ) : (
      <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4 sm:h-5 sm:w-5">
        <path
          fill="currentColor"
          d="M12 12a4 4 0 1 0-4-4a4 4 0 0 0 4 4m0 2c-4.418 0-8 2.015-8 4.5V20h16v-1.5c0-2.485-3.582-4.5-8-4.5"
        />
      </svg>
    );

  return (
    <header className="app-header fixed inset-x-0 top-0 z-40 overflow-visible border-b border-white/8 bg-[#050816]/72 shadow-[0_18px_60px_rgba(0,0,0,0.42)] backdrop-blur-2xl before:pointer-events-none before:absolute before:inset-x-0 before:top-0 before:h-full before:bg-[radial-gradient(circle_at_top_left,rgba(45,212,191,0.14),transparent_34%),radial-gradient(circle_at_top_right,rgba(59,130,246,0.18),transparent_40%)]">
      <div className="relative mx-auto flex w-full max-w-7xl flex-col gap-2.5 px-3 py-2.5 sm:flex-row sm:items-center sm:gap-4 sm:px-6 sm:py-4 lg:px-8">
        <div className="flex items-center justify-between gap-3 sm:flex-none">
          <Link href="/" className="group flex shrink-0 items-center gap-2.5">
            <div className="flex h-10 w-10 items-center justify-center rounded-full border border-white/10 bg-white/[0.08] text-xs font-semibold tracking-[0.18em] text-white transition-colors duration-200 group-hover:bg-white/[0.12]">
              SP
            </div>
            <div className="min-w-0">
              <p className="text-[10px] uppercase tracking-[0.28em] text-slate-400/82">Music</p>
              <h1 className="text-[17px] font-semibold tracking-tight text-white">Spasiba</h1>
            </div>
          </Link>

          <div className="flex items-center gap-2 sm:hidden">
            <Link
              href="/library"
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-white/10 bg-white/[0.08] text-slate-200 transition hover:bg-white/[0.12]"
              aria-label="Open playlists"
            >
              <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4">
                <path
                  fill="currentColor"
                  d="M5 4a2 2 0 0 0-2 2v13.5a.5.5 0 0 0 .8.4L8 17l4.2 2.9a.5.5 0 0 0 .8-.4V6a2 2 0 0 0-2-2zm11 1h3v2h-3zm0 5h3v2h-3zm0 5h3v2h-3z"
                />
              </svg>
            </Link>
            <Link
              href="/profile"
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-white/10 bg-white/[0.08] text-slate-200 transition hover:bg-white/[0.12]"
              aria-label="Open profile"
            >
              {renderProfileAvatar()}
            </Link>
          </div>
        </div>

        <div ref={formRef} className="relative w-full min-w-0 sm:ml-auto sm:max-w-3xl sm:flex-1">
          <form onSubmit={handleSubmit}>
            <div className="flex items-center gap-2 rounded-[24px] border border-white/10 bg-[#0a101d]/92 px-3 py-2.5 shadow-[0_22px_52px_rgba(0,0,0,0.34)] transition duration-200 focus-within:border-teal-300/35 focus-within:bg-[#0b1321]/96 focus-within:shadow-[0_26px_60px_rgba(3,10,24,0.46)] sm:gap-3 sm:rounded-full sm:px-4 sm:py-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/[0.05] text-slate-300 sm:hidden">
                <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4 sm:h-5 sm:w-5">
                  <path
                    fill="currentColor"
                    d="M10.5 3a7.5 7.5 0 0 1 5.994 12.01l4.248 4.246a1 1 0 1 1-1.414 1.415l-4.248-4.247A7.5 7.5 0 1 1 10.5 3m0 2a5.5 5.5 0 1 0 0 11a5.5 5.5 0 0 0 0-11"
                  />
                </svg>
              </div>
              <svg aria-hidden viewBox="0 0 24 24" className="hidden h-4 w-4 shrink-0 text-slate-400 sm:block sm:h-5 sm:w-5">
                <path
                  fill="currentColor"
                  d="M10.5 3a7.5 7.5 0 0 1 5.994 12.01l4.248 4.246a1 1 0 1 1-1.414 1.415l-4.248-4.247A7.5 7.5 0 1 1 10.5 3m0 2a5.5 5.5 0 1 0 0 11a5.5 5.5 0 0 0 0-11"
                />
              </svg>
              <input
                value={query}
                onChange={(event) => {
                  setQuery(event.target.value);
                  setIsSuggestionOpen(true);
                }}
                onFocus={() => {
                  if (query.trim().length >= MIN_SEARCH_LENGTH) {
                    setIsSuggestionOpen(true);
                  }
                }}
                placeholder="Search songs, artists, or albums..."
                className="min-w-0 w-full bg-transparent text-[15px] text-white placeholder:text-slate-400/88 focus:outline-none sm:text-base"
                spellCheck={false}
                autoComplete="off"
                autoCorrect="off"
                autoCapitalize="none"
                enterKeyHint="search"
              />
              <button
                type="submit"
                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-teal-300/28 bg-teal-300/12 text-teal-100 transition hover:border-teal-200/45 hover:bg-teal-300/18 sm:hidden"
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
                className="hidden shrink-0 rounded-full border border-teal-300/30 bg-teal-300/12 px-4 py-2 text-[11px] font-medium uppercase tracking-[0.24em] text-teal-100 transition hover:border-teal-200/45 hover:bg-teal-300/18 sm:inline-flex"
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
                      onClick={() => submitSearch(song.title)}
                      className="flex w-full items-center gap-2.5 rounded-[20px] px-2 py-2.5 text-left transition hover:bg-white/[0.06] sm:gap-3 sm:rounded-[22px] sm:px-3 sm:py-3"
                    >
                      <div className="relative h-10 w-10 shrink-0 overflow-hidden rounded-[18px] bg-white/[0.05] sm:h-12 sm:w-12 sm:rounded-2xl">
                        <Image src={song.thumbnail} alt={song.title} fill sizes="(max-width: 640px) 40px, 48px" quality={70} className="object-cover" />
                      </div>
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
          <Link
            href="/library"
            className="glass-panel flex h-10 w-10 items-center justify-center rounded-[18px] text-slate-200 transition hover:bg-white/[0.1] sm:h-12 sm:w-12 sm:rounded-2xl"
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
            className="glass-panel flex h-10 w-10 items-center justify-center rounded-[18px] text-slate-200 transition hover:bg-white/[0.1] sm:h-12 sm:w-12 sm:rounded-2xl"
            aria-label="Open profile"
          >
            {renderProfileAvatar()}
          </Link>
        </div>
      </div>
    </header>
  );
}

const SearchBar = memo(SearchBarComponent);

export default SearchBar;
