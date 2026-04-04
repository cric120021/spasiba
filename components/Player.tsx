"use client";

import Link from "next/link";
import type { SyntheticEvent } from "react";
import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";

import { audioController } from "@/lib/audio-controller";
import { loadYouTubeIframeApi, type YouTubePlayer } from "@/lib/youtube-iframe";
import { formatPlaybackTime } from "@/lib/utils";
import { playerActions, setPlayerState, usePlayerStore } from "@/store/playerStore";

import ArtworkImage from "@/components/ArtworkImage";
import PlayerTrackMenu from "@/components/PlayerTrackMenu";

type PlayerMode = "audio" | "video";

function PlayerComponent() {
  const currentSong = usePlayerStore((state) => state.currentSong);
  const queue = usePlayerStore((state) => state.queue);
  const currentIndex = usePlayerStore((state) => state.currentIndex);
  const isPlaying = usePlayerStore((state) => state.isPlaying);
  const isLoading = usePlayerStore((state) => state.isLoading);
  const progress = usePlayerStore((state) => state.progress);
  const currentTime = usePlayerStore((state) => state.currentTime);
  const duration = usePlayerStore((state) => state.duration);
  const buffered = usePlayerStore((state) => state.buffered);
  const volume = usePlayerStore((state) => state.volume);
  const queueSource = usePlayerStore((state) => state.queueSource);
  const queueSourceLabel = usePlayerStore((state) => state.queueSourceLabel);
  const playlistBaseTrackIds = usePlayerStore((state) => state.playlistBaseTrackIds);
  const playlistMixEnabled = usePlayerStore((state) => state.playlistMixEnabled);
  const error = usePlayerStore((state) => state.error);
  const toast = usePlayerStore((state) => state.toast);
  const [expanded, setExpanded] = useState(false);
  const [mode, setMode] = useState<PlayerMode>("audio");
  const [videoStartAt, setVideoStartAt] = useState(0);
  const [draggingQueueIndex, setDraggingQueueIndex] = useState<number | null>(null);
  const [dropQueueIndex, setDropQueueIndex] = useState<number | null>(null);
  const videoContainerRef = useRef<HTMLDivElement | null>(null);
  const videoPlayerRef = useRef<YouTubePlayer | null>(null);
  const videoSyncIntervalRef = useRef<number | null>(null);
  const shouldResumeAudioRef = useRef(false);

  const progressPercent = useMemo(() => `${Math.max(progress * 100, 0)}%`, [progress]);
  const bufferedPercent = useMemo(
    () => `${duration > 0 ? Math.min((buffered / duration) * 100, 100) : 0}%`,
    [buffered, duration]
  );
  const upcomingQueue = useMemo(() => queue.slice(currentIndex + 1), [currentIndex, queue]);
  const totalQueuedTracks = useMemo(() => Math.max(queue.length - currentIndex - 1, 0), [currentIndex, queue.length]);
  const queueContext = useMemo(
    () => ({
      queueSource,
      queueSourceLabel,
      playlistBaseTrackIds,
      playlistMixEnabled
    }),
    [playlistBaseTrackIds, playlistMixEnabled, queueSource, queueSourceLabel]
  );
  const expandedArtworkSize = useMemo(
    () => ({
      width: "min(calc(100vw - 32px), 34dvh, 380px)",
      height: "min(calc(100vw - 32px), 34dvh, 380px)"
    }),
    []
  );
  const expandedVideoSize = useMemo(
    () => ({
      width: "min(calc(100vw - 32px), 460px)",
      height: "min(calc((100vw - 32px) * 0.5625), 27dvh, 250px)"
    }),
    []
  );

  const stopPropagation = useCallback((event: SyntheticEvent) => {
    event.stopPropagation();
  }, []);

  const stopVideoSync = useCallback(() => {
    if (videoSyncIntervalRef.current === null) {
      return;
    }

    window.clearInterval(videoSyncIntervalRef.current);
    videoSyncIntervalRef.current = null;
  }, []);

  const syncVideoStore = useCallback(() => {
    const videoPlayer = videoPlayerRef.current;
    const playerState = window.YT?.PlayerState;

    if (!videoPlayer || !playerState) {
      return;
    }

    const nextCurrentTime = videoPlayer.getCurrentTime() || 0;
    const nextDuration = videoPlayer.getDuration() || 0;
    const bufferedFraction = videoPlayer.getVideoLoadedFraction?.() || 0;
    const nextPlayerState = videoPlayer.getPlayerState();

    setPlayerState((previous) => ({
      ...previous,
      currentTime: nextCurrentTime,
      duration: nextDuration,
      buffered: nextDuration > 0 ? nextDuration * bufferedFraction : 0,
      progress: nextDuration > 0 ? nextCurrentTime / nextDuration : 0,
      isPlaying: nextPlayerState === playerState.PLAYING,
      isLoading: nextPlayerState === playerState.BUFFERING
    }));
  }, []);

  const destroyVideoPlayer = useCallback(() => {
    stopVideoSync();

    if (videoPlayerRef.current) {
      videoPlayerRef.current.destroy();
      videoPlayerRef.current = null;
    }

    if (videoContainerRef.current) {
      videoContainerRef.current.innerHTML = "";
    }
  }, [stopVideoSync]);

  const startVideoSync = useCallback(() => {
    stopVideoSync();
    syncVideoStore();
    videoSyncIntervalRef.current = window.setInterval(syncVideoStore, 250);
  }, [stopVideoSync, syncVideoStore]);

  const syncBackToAudio = useCallback(() => {
    const playerState = window.YT?.PlayerState;
    const videoPlayer = videoPlayerRef.current;
    const resumeAt = videoPlayer ? videoPlayer.getCurrentTime() || currentTime : currentTime;
    const shouldResume = Boolean(videoPlayer && playerState && videoPlayer.getPlayerState() === playerState.PLAYING);

    destroyVideoPlayer();
    setMode("audio");
    audioController.seekToSeconds(resumeAt);

    if (shouldResume) {
      void audioController.togglePlayback();
    } else {
      setPlayerState((previous) => ({
        ...previous,
        isPlaying: false,
        isLoading: false
      }));
    }

    shouldResumeAudioRef.current = false;
  }, [currentTime, destroyVideoPlayer]);

  const handleSeek = useCallback(
    (value: number) => {
      if (mode === "video" && videoPlayerRef.current) {
        const videoPlayer = videoPlayerRef.current;
        const targetDuration = videoPlayer.getDuration() || duration;

        if (targetDuration > 0) {
          videoPlayer.seekTo((value / 100) * targetDuration, true);
          syncVideoStore();
        }
        return;
      }

      audioController.seek(value / 100);
    },
    [duration, mode, syncVideoStore]
  );

  const handleVolume = useCallback(
    (value: number) => {
      const nextVolume = value / 100;

      if (mode === "video" && videoPlayerRef.current) {
        videoPlayerRef.current.setVolume(value);
        setPlayerState((previous) => ({
          ...previous,
          volume: nextVolume
        }));
        return;
      }

      audioController.setVolume(nextVolume);
    },
    [mode]
  );

  const handleTogglePlayback = useCallback(() => {
    if (mode === "video" && videoPlayerRef.current && window.YT?.PlayerState) {
      const currentState = videoPlayerRef.current.getPlayerState();

      if (currentState === window.YT.PlayerState.PLAYING) {
        videoPlayerRef.current.pauseVideo();
      } else {
        videoPlayerRef.current.playVideo();
      }

      return;
    }

    void audioController.togglePlayback();
  }, [mode]);

  const handlePrevious = useCallback(() => {
    if (mode === "video" && videoPlayerRef.current && currentTime > 5) {
      videoPlayerRef.current.seekTo(0, true);
      syncVideoStore();
      return;
    }

    void audioController.playPrevious();
  }, [currentTime, mode, syncVideoStore]);

  const handleNext = useCallback(() => {
    void audioController.playNext();
  }, []);

  const handleQueueDrop = useCallback(
    (targetUpcomingIndex: number) => {
      if (draggingQueueIndex === null || draggingQueueIndex === targetUpcomingIndex) {
        setDraggingQueueIndex(null);
        setDropQueueIndex(null);
        return;
      }

      audioController.reorderQueue(currentIndex + 1 + draggingQueueIndex, currentIndex + 1 + targetUpcomingIndex);
      setDraggingQueueIndex(null);
      setDropQueueIndex(null);
    },
    [currentIndex, draggingQueueIndex]
  );

  const handleModeChange = useCallback(
    (nextMode: PlayerMode) => {
      if (!currentSong || nextMode === mode) {
        return;
      }

      if (nextMode === "video") {
        shouldResumeAudioRef.current = isPlaying || isLoading;
        setVideoStartAt(Math.floor(currentTime));
        audioController.pause();
        setMode("video");
        return;
      }

      syncBackToAudio();
    },
    [currentSong, currentTime, isLoading, isPlaying, mode, syncBackToAudio]
  );

  const handleCloseExpanded = useCallback(() => {
    if (mode === "video") {
      syncBackToAudio();
    }

    setExpanded(false);
  }, [mode, syncBackToAudio]);

  useEffect(() => {
    if (!currentSong) {
      destroyVideoPlayer();
      setExpanded(false);
      setMode("audio");
      shouldResumeAudioRef.current = false;
      return;
    }

    setMode("audio");
    shouldResumeAudioRef.current = false;
    destroyVideoPlayer();
  }, [currentSong?.id, destroyVideoPlayer]);

  useEffect(() => {
    if (!expanded || mode !== "video" || !currentSong || !videoContainerRef.current) {
      destroyVideoPlayer();
      return;
    }

    let cancelled = false;

    setPlayerState((previous) => ({
      ...previous,
      isLoading: true,
      error: null
    }));

    void loadYouTubeIframeApi()
      .then(() => {
        if (cancelled || !videoContainerRef.current || !window.YT?.Player) {
          return;
        }

        destroyVideoPlayer();

        videoPlayerRef.current = new window.YT.Player(videoContainerRef.current, {
          videoId: currentSong.videoId,
          playerVars: {
            autoplay: shouldResumeAudioRef.current ? 1 : 0,
            controls: 1,
            playsinline: 1,
            rel: 0,
            modestbranding: 1,
            start: Math.max(Math.floor(videoStartAt), 0),
            enablejsapi: 1,
            origin: window.location.origin
          },
          events: {
            onReady: (event) => {
              event.target.setVolume(volume * 100);

              if (videoStartAt > 0) {
                event.target.seekTo(videoStartAt, true);
              }

              if (shouldResumeAudioRef.current) {
                event.target.playVideo();
              } else {
                setPlayerState((previous) => ({
                  ...previous,
                  isLoading: false,
                  isPlaying: false
                }));
                syncVideoStore();
              }
            },
            onStateChange: (event) => {
              const playerState = window.YT?.PlayerState;

              if (!playerState) {
                return;
              }

              if (event.data === playerState.PLAYING) {
                setPlayerState((previous) => ({
                  ...previous,
                  isPlaying: true,
                  isLoading: false
                }));
                startVideoSync();
                return;
              }

              if (event.data === playerState.PAUSED) {
                setPlayerState((previous) => ({
                  ...previous,
                  isPlaying: false,
                  isLoading: false
                }));
                syncVideoStore();
                stopVideoSync();
                return;
              }

              if (event.data === playerState.BUFFERING) {
                setPlayerState((previous) => ({
                  ...previous,
                  isLoading: true
                }));
                startVideoSync();
                return;
              }

              if (event.data === playerState.ENDED) {
                stopVideoSync();
                setPlayerState((previous) => ({
                  ...previous,
                  isPlaying: false,
                  isLoading: false
                }));
                setMode("audio");
                destroyVideoPlayer();
                void audioController.playNext();
                return;
              }

              syncVideoStore();
            },
            onError: () => {
              stopVideoSync();
              setPlayerState((previous) => ({
                ...previous,
                isLoading: false,
                isPlaying: false,
                error: "Video playback could not start."
              }));
            }
          }
        });
      })
      .catch(() => {
        if (cancelled) {
          return;
        }

        setPlayerState((previous) => ({
          ...previous,
          isLoading: false,
          error: "Video playback could not start."
        }));
      });

    return () => {
      cancelled = true;
      destroyVideoPlayer();
    };
  }, [currentSong, destroyVideoPlayer, expanded, mode, startVideoSync, stopVideoSync, syncVideoStore, videoStartAt, volume]);

  useEffect(() => {
    return () => {
      destroyVideoPlayer();
    };
  }, [destroyVideoPlayer]);

  useEffect(() => {
    if (!toast) {
      return;
    }

    const timer = window.setTimeout(() => {
      playerActions.clearToast();
    }, 2200);

    return () => window.clearTimeout(timer);
  }, [toast]);

  if (!currentSong && !error && !toast) {
    return null;
  }

  return (
    <>
      {expanded && currentSong ? (
        <div
          className="fixed inset-0 z-[70] overflow-hidden overscroll-contain bg-[#030612]/92 px-3 pb-[calc(10px+env(safe-area-inset-bottom))] pt-[calc(10px+env(safe-area-inset-top))] backdrop-blur-2xl sm:px-6 sm:py-6"
          onClick={handleCloseExpanded}
        >
          <div
            className="mx-auto flex h-full min-h-0 w-full max-w-[560px] flex-col gap-3 sm:gap-4"
            onClick={stopPropagation}
          >
            <div className="relative shrink-0 overflow-hidden rounded-[28px] border border-white/8 bg-[linear-gradient(180deg,rgba(8,12,20,0.98),rgba(5,8,14,0.98))] p-4 shadow-[0_36px_120px_rgba(0,0,0,0.5)] sm:rounded-[34px] sm:p-5">
              {mode === "audio" ? (
                <div className="pointer-events-none absolute inset-x-6 top-8 h-40 overflow-hidden rounded-full opacity-40 blur-3xl">
                  <ArtworkImage src={currentSong.thumbnail} alt="" fill sizes="320px" quality={55} className="object-cover scale-125" />
                </div>
              ) : null}

              <div className="relative flex items-center justify-between gap-3">
                <button
                  type="button"
                  onClick={handleCloseExpanded}
                  className="flex h-11 w-11 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] text-slate-200 transition hover:bg-white/[0.1]"
                  aria-label="Minimize player"
                >
                  <svg aria-hidden viewBox="0 0 24 24" className="h-5 w-5">
                    <path fill="currentColor" d="m7.4 9.4 4.6 4.6 4.6-4.6 1.4 1.4-6 6-6-6z" />
                  </svg>
                </button>

                <div className="flex items-center rounded-full border border-white/10 bg-white/[0.04] p-1">
                  <button
                    type="button"
                    onClick={() => handleModeChange("audio")}
                    className={`rounded-full px-3 py-2 text-[12px] transition sm:px-4 sm:text-[13px] ${mode === "audio" ? "bg-white text-slate-950" : "text-slate-300"}`}
                  >
                    Audio
                  </button>
                  <button
                    type="button"
                    onClick={() => handleModeChange("video")}
                    className={`rounded-full px-3 py-2 text-[12px] transition sm:px-4 sm:text-[13px] ${mode === "video" ? "bg-white text-slate-950" : "text-slate-300"}`}
                  >
                    Video
                  </button>
                </div>

                <div className="flex items-center gap-2">
                  <Link
                    href={currentSong.url}
                    target="_blank"
                    rel="noreferrer"
                    className="flex h-11 w-11 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] text-slate-200 transition hover:bg-white/[0.1]"
                    aria-label="Open on YouTube"
                  >
                    <svg aria-hidden viewBox="0 0 24 24" className="h-5 w-5">
                      <path fill="currentColor" d="M14 3h7v7h-2V6.41l-8.29 8.3-1.42-1.42 8.3-8.29H14zm5 16H5V5h7V3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7h-2z" />
                    </svg>
                  </Link>

                  <div onClick={stopPropagation}>
                    <PlayerTrackMenu
                      song={currentSong}
                      onClosePlayer={() => setExpanded(false)}
                      className="flex h-11 w-11 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] text-slate-200 transition hover:bg-white/[0.1]"
                    />
                  </div>
                </div>
              </div>

              <div className="relative mt-4 flex justify-center">
                {mode === "video" ? (
                  <div
                    className="overflow-hidden rounded-[24px] bg-black sm:rounded-[28px]"
                    style={expandedVideoSize}
                  >
                    <div ref={videoContainerRef} className="h-full w-full" />
                  </div>
                ) : (
                  <div
                    className="relative overflow-hidden rounded-[24px] bg-white/[0.05] shadow-[0_18px_60px_rgba(0,0,0,0.35)] sm:rounded-[28px]"
                    style={expandedArtworkSize}
                  >
                    <ArtworkImage
                      src={currentSong.thumbnail}
                      alt={currentSong.title}
                      fill
                      sizes="(max-width: 640px) 72vw, 380px"
                      quality={75}
                      className="object-cover"
                    />
                  </div>
                )}
              </div>

              <div className="relative mt-4">
                <h2 className="text-[26px] font-semibold tracking-tight text-white sm:text-[32px]">{currentSong.title}</h2>
                <p className="mt-1.5 text-[15px] text-slate-300/76 sm:text-[18px]">{currentSong.author}</p>

                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <span className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-1.5 text-[10px] uppercase tracking-[0.18em] text-slate-200">
                    {queueSource === "playlist" ? "Playlist" : "Spasiba queue"}
                  </span>
                  <span className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-1.5 text-[10px] uppercase tracking-[0.18em] text-slate-300">
                    {totalQueuedTracks} next
                  </span>
                  {queueSourceLabel ? (
                    <span className="max-w-[200px] truncate rounded-full border border-white/10 bg-white/[0.04] px-3 py-1.5 text-[10px] uppercase tracking-[0.18em] text-slate-300">
                      {queueSourceLabel}
                    </span>
                  ) : null}
                </div>
              </div>

              <div className="relative mt-4 space-y-2.5">
                <div className="relative h-2.5 overflow-hidden rounded-full bg-white/8">
                  <div className="absolute inset-y-0 left-0 rounded-full bg-white/[0.15]" style={{ width: bufferedPercent }} />
                  <div className="absolute inset-y-0 left-0 rounded-full bg-gradient-to-r from-teal-300 to-sky-400" style={{ width: progressPercent }} />
                  <input
                    type="range"
                    min={0}
                    max={100}
                    value={Math.round(progress * 100)}
                    onChange={(event) => handleSeek(Number(event.target.value))}
                    className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
                    aria-label="Seek"
                  />
                </div>

                <div className="flex items-center justify-between text-[13px] text-slate-300/80 sm:text-sm">
                  <span>{formatPlaybackTime(currentTime)}</span>
                  <span>{formatPlaybackTime(duration)}</span>
                </div>
              </div>

              <div className="relative mt-5 flex items-center justify-center gap-3.5 sm:gap-5">
                <button
                  type="button"
                  onClick={handlePrevious}
                  className="rounded-full border border-white/10 bg-white/[0.04] p-3 text-white transition hover:bg-white/[0.1] sm:p-4"
                  aria-label="Previous"
                >
                  <svg aria-hidden viewBox="0 0 24 24" className="h-6 w-6">
                    <path fill="currentColor" d="M7 6h2v12H7zm3 6.5v-1l8-5v11z" />
                  </svg>
                </button>

                <button
                  type="button"
                  onClick={handleTogglePlayback}
                  className="flex h-16 w-16 items-center justify-center rounded-full bg-white text-slate-950 shadow-xl shadow-black/45 transition hover:scale-[1.02] hover:bg-teal-300 sm:h-20 sm:w-20"
                  aria-label={isPlaying ? "Pause" : "Play"}
                >
                  {isLoading ? (
                    <div className="h-7 w-7 animate-spin rounded-full border-[3px] border-slate-950/25 border-t-slate-950 sm:h-8 sm:w-8" aria-hidden />
                  ) : isPlaying ? (
                    <svg aria-hidden viewBox="0 0 24 24" className="h-8 w-8 sm:h-10 sm:w-10">
                      <path fill="currentColor" d="M7 5h3v14H7zm7 0h3v14h-3z" />
                    </svg>
                  ) : (
                    <svg aria-hidden viewBox="0 0 24 24" className="h-8 w-8 sm:h-10 sm:w-10">
                      <path fill="currentColor" d="M8 5.14v13.72a1 1 0 0 0 1.53.848l10.08-6.86a1 1 0 0 0 0-1.696L9.53 4.292A1 1 0 0 0 8 5.14" />
                    </svg>
                  )}
                </button>

                <button
                  type="button"
                  onClick={handleNext}
                  className="rounded-full border border-white/10 bg-white/[0.04] p-3 text-white transition hover:bg-white/[0.1] sm:p-4"
                  aria-label="Next"
                >
                  <svg aria-hidden viewBox="0 0 24 24" className="h-6 w-6">
                    <path fill="currentColor" d="M15 6h2v12h-2zm-1 5.5v1l-8 5v-11z" />
                  </svg>
                </button>
              </div>

              <div className="relative mt-4 flex items-center gap-3 rounded-full border border-white/8 bg-white/[0.03] px-4 py-2">
                <span className="text-[10px] uppercase tracking-[0.24em] text-slate-400">Volume</span>
                <input
                  type="range"
                  min={0}
                  max={100}
                  value={Math.round(volume * 100)}
                  onChange={(event) => handleVolume(Number(event.target.value))}
                  className="w-full"
                  aria-label="Volume"
                />
              </div>
            </div>

            <div className="min-h-0 flex-1 overflow-hidden rounded-[24px] border border-white/8 bg-[rgba(8,12,20,0.94)] p-4 shadow-[0_24px_80px_rgba(0,0,0,0.28)] sm:rounded-[30px] sm:p-5">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-[10px] uppercase tracking-[0.22em] text-slate-400 sm:text-[11px] sm:tracking-[0.28em]">Your queue</p>
                  <p className="mt-2 text-[13px] text-slate-300/72 sm:text-sm">Tap any song to jump there. Drag to change what plays next.</p>
                </div>
                {queueSource === "playlist" ? (
                  <button
                    type="button"
                    onClick={(event) => {
                      event.stopPropagation();
                      audioController.setPlaylistMixEnabled(!playlistMixEnabled);
                    }}
                    className={`shrink-0 rounded-full border px-4 py-2 text-[11px] uppercase tracking-[0.22em] transition ${
                      playlistMixEnabled
                        ? "border-teal-300/30 bg-teal-300/14 text-teal-100"
                        : "border-white/10 bg-white/[0.04] text-slate-200"
                    }`}
                  >
                    {playlistMixEnabled ? "Mix on" : "Mix off"}
                  </button>
                ) : null}
              </div>

              <div className="mt-4 h-full max-h-full space-y-3 overflow-y-auto pr-1 sm:pr-2">
                {upcomingQueue.length > 0 ? (
                  upcomingQueue.map((track, index) => (
                    <div
                      key={`${track.id}-${index}`}
                      role="button"
                      tabIndex={0}
                      onClick={() => void audioController.playSong(track, queue, currentIndex + index + 1, queueContext)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          void audioController.playSong(track, queue, currentIndex + index + 1, queueContext);
                        }
                      }}
                      onDragOver={(event) => {
                        event.preventDefault();
                        event.dataTransfer.dropEffect = "move";
                        setDropQueueIndex(index);
                      }}
                      onDrop={(event) => {
                        event.preventDefault();
                        handleQueueDrop(index);
                      }}
                      className={`flex w-full items-center gap-3 rounded-[18px] border px-3 py-3 text-left transition ${
                        dropQueueIndex === index
                          ? "border-teal-300/35 bg-teal-300/[0.12]"
                          : "border-white/8 bg-white/[0.03] hover:bg-white/[0.08]"
                      }`}
                    >
                      <button
                        type="button"
                        draggable
                        onClick={(event) => event.stopPropagation()}
                        onDragStart={(event) => {
                          setDraggingQueueIndex(index);
                          setDropQueueIndex(index);
                          event.dataTransfer.effectAllowed = "move";
                        }}
                        onDragEnd={() => {
                          setDraggingQueueIndex(null);
                          setDropQueueIndex(null);
                        }}
                        className="shrink-0 cursor-grab rounded-full border border-white/10 bg-white/[0.04] p-2 text-slate-300 active:cursor-grabbing"
                        aria-label={`Reorder ${track.title}`}
                      >
                        <svg aria-hidden viewBox="0 0 24 24" className="h-4 w-4">
                          <path
                            fill="currentColor"
                            d="M9 6a1.5 1.5 0 1 1 0-3a1.5 1.5 0 0 1 0 3m0 7a1.5 1.5 0 1 1 0-3a1.5 1.5 0 0 1 0 3m0 7a1.5 1.5 0 1 1 0-3A1.5 1.5 0 0 1 9 20m6-14a1.5 1.5 0 1 1 0-3a1.5 1.5 0 0 1 0 3m0 7a1.5 1.5 0 1 1 0-3a1.5 1.5 0 0 1 0 3m0 7a1.5 1.5 0 1 1 0-3a1.5 1.5 0 0 1 0 3"
                          />
                        </svg>
                      </button>

                      <div className="relative h-12 w-12 shrink-0 overflow-hidden rounded-[16px] bg-white/[0.05]">
                        <ArtworkImage src={track.thumbnail} alt={track.title} fill sizes="48px" quality={70} className="object-cover" />
                      </div>

                      <div className="min-w-0 flex-1">
                        <p className="line-clamp-1 text-[13px] font-medium text-white sm:text-sm">{track.title}</p>
                        <p className="mt-1 line-clamp-1 text-xs text-slate-400">{track.author}</p>
                      </div>

                      <span className="shrink-0 text-[10px] uppercase tracking-[0.18em] text-slate-300 sm:text-xs sm:tracking-[0.22em]">{track.duration}</span>
                    </div>
                  ))
                ) : (
                  <div className="rounded-[18px] border border-dashed border-white/10 bg-white/[0.03] px-4 py-5 text-[13px] text-slate-300/75 sm:text-sm">
                    The smart queue is still warming up.
                  </div>
                )}
              </div>
            </div>

            {error && !isLoading ? <p className="mt-4 text-sm text-rose-200">{error}</p> : null}
          </div>
        </div>
      ) : null}

      {toast ? (
        <div className="pointer-events-none fixed inset-x-0 bottom-[112px] z-[90] flex justify-center px-4 sm:bottom-[126px]">
          <div className="rounded-full border border-white/10 bg-[rgba(8,14,24,0.96)] px-4 py-2.5 text-[13px] font-medium text-white shadow-[0_20px_60px_rgba(0,0,0,0.45)] sm:px-5 sm:py-3 sm:text-sm">
            {toast.message}
          </div>
        </div>
      ) : null}

      {currentSong ? (
      <div className="app-player-dock fixed inset-x-0 bottom-0 z-50">
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-full bg-[linear-gradient(180deg,rgba(3,6,18,0),rgba(3,6,18,0.82)_28%,rgba(3,6,18,0.97))] backdrop-blur-xl" />
        <div className="relative px-3 pb-[calc(12px+env(safe-area-inset-bottom))] pt-2 sm:px-4 sm:pb-[calc(16px+env(safe-area-inset-bottom))] sm:pt-3">
        <div
          className="glass-panel-strong mx-auto flex max-w-7xl cursor-pointer flex-col gap-3 rounded-[22px] p-3 transition hover:bg-[rgba(16,24,38,0.96)] sm:gap-4 sm:rounded-[28px] sm:p-5"
          onClick={() => {
            setExpanded(true);
          }}
        >
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:gap-6">
            <div className="flex min-w-0 flex-1 items-center gap-4">
              <div className="relative h-14 w-14 overflow-hidden rounded-[18px] bg-white/[0.06] sm:h-16 sm:w-16 sm:rounded-2xl">
                {currentSong ? (
                  <ArtworkImage src={currentSong.thumbnail} alt={currentSong.title} fill sizes="(max-width: 640px) 56px, 64px" quality={70} className="object-cover" />
                ) : (
                  <div className="flex h-full items-center justify-center text-slate-500">
                    <svg aria-hidden viewBox="0 0 24 24" className="h-6 w-6 sm:h-7 sm:w-7">
                      <path
                        fill="currentColor"
                        d="M16 17a3 3 0 1 1-2-2.83V6.74l7-1.55V15a3 3 0 1 1-2-2.83V7.69l-3 .66zM6 19a3 3 0 1 1-2-2.83V8.74l7-1.55v9.98A3 3 0 0 1 6 19"
                      />
                    </svg>
                  </div>
                )}
              </div>

              <div className="min-w-0">
                <p className="text-[10px] uppercase tracking-[0.22em] text-slate-400 sm:text-[11px] sm:tracking-[0.28em]">Spasiba</p>
                <h2 className="line-clamp-1 text-[15px] font-semibold tracking-tight text-white sm:text-lg">
                  {currentSong?.title || "Choose a song"}
                </h2>
                <p className="line-clamp-1 text-[13px] text-slate-300/70 sm:text-sm">
                  {currentSong ? `${currentSong.author} • ${totalQueuedTracks} next` : "Ready"}
                </p>
              </div>

              {currentSong ? (
                <div onClick={stopPropagation}>
                  <PlayerTrackMenu
                    song={currentSong}
                    onClosePlayer={() => setExpanded(false)}
                    className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-white/10 bg-white/[0.05] text-slate-200 transition hover:bg-white/[0.1]"
                  />
                </div>
              ) : null}
            </div>

            <div className="flex items-center justify-between gap-3 sm:justify-center">
              <button
                type="button"
                onClick={(event) => {
                  event.stopPropagation();
                  handlePrevious();
                }}
                className="rounded-full border border-white/10 bg-white/[0.04] p-2.5 text-white transition hover:border-teal-300/35 hover:bg-white/10 sm:p-3"
                aria-label="Previous track"
              >
                <svg aria-hidden viewBox="0 0 24 24" className="h-4.5 w-4.5 sm:h-5 sm:w-5">
                  <path fill="currentColor" d="M7 6h2v12H7zm3 6.5v-1l8-5v11z" />
                </svg>
              </button>

              <button
                type="button"
                onClick={(event) => {
                  event.stopPropagation();
                  handleTogglePlayback();
                }}
                className="flex h-12 w-12 items-center justify-center rounded-full bg-white text-slate-950 shadow-xl shadow-black/30 transition hover:scale-[1.02] hover:bg-teal-300 sm:h-14 sm:w-14"
                aria-label={isPlaying ? "Pause" : "Play"}
              >
                {isLoading ? (
                  <div className="h-5 w-5 rounded-full border-2 border-slate-950/25 border-t-slate-950 animate-spin sm:h-6 sm:w-6" aria-hidden />
                ) : isPlaying ? (
                  <svg aria-hidden viewBox="0 0 24 24" className="h-6 w-6 sm:h-7 sm:w-7">
                    <path fill="currentColor" d="M7 5h3v14H7zm7 0h3v14h-3z" />
                  </svg>
                ) : (
                  <svg aria-hidden viewBox="0 0 24 24" className="h-6 w-6 sm:h-7 sm:w-7">
                    <path fill="currentColor" d="M8 5.14v13.72a1 1 0 0 0 1.53.848l10.08-6.86a1 1 0 0 0 0-1.696L9.53 4.292A1 1 0 0 0 8 5.14" />
                  </svg>
                )}
              </button>

              <button
                type="button"
                onClick={(event) => {
                  event.stopPropagation();
                  handleNext();
                }}
                className="rounded-full border border-white/10 bg-white/[0.04] p-2.5 text-white transition hover:border-teal-300/35 hover:bg-white/10 sm:p-3"
                aria-label="Next track"
              >
                <svg aria-hidden viewBox="0 0 24 24" className="h-4.5 w-4.5 sm:h-5 sm:w-5">
                  <path fill="currentColor" d="M15 6h2v12h-2zm-1 5.5v1l-8 5v-11z" />
                </svg>
              </button>
            </div>

            <div className="hidden items-center gap-3 lg:flex" onClick={stopPropagation}>
              <span className="text-xs uppercase tracking-[0.24em] text-slate-400">Vol</span>
              <input
                type="range"
                min={0}
                max={100}
                value={Math.round(volume * 100)}
                onChange={(event) => handleVolume(Number(event.target.value))}
                className="w-28"
                aria-label="Volume"
              />
            </div>
          </div>

          <div className="space-y-2" onClick={stopPropagation}>
            <div className="relative h-2 overflow-hidden rounded-full bg-white/8">
              <div className="absolute inset-y-0 left-0 rounded-full bg-white/[0.15]" style={{ width: bufferedPercent }} />
              <div className="absolute inset-y-0 left-0 rounded-full bg-gradient-to-r from-teal-300 to-sky-400" style={{ width: progressPercent }} />
              <input
                type="range"
                min={0}
                max={100}
                value={Math.round(progress * 100)}
                onChange={(event) => handleSeek(Number(event.target.value))}
                className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
                aria-label="Seek"
              />
            </div>

            <div className="flex items-center justify-between gap-3 text-xs uppercase tracking-[0.22em] text-slate-400">
              <span>{formatPlaybackTime(currentTime)}</span>
              <span>{isLoading ? "Loading..." : currentSong ? `${totalQueuedTracks} queued` : "Idle"}</span>
              <span>{formatPlaybackTime(duration)}</span>
            </div>
          </div>

          {error && !isLoading ? <p className="text-sm text-rose-200">{error}</p> : null}
        </div>
        </div>
      </div>
      ) : null}
    </>
  );
}

const Player = memo(PlayerComponent);

export default Player;
