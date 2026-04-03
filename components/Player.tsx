"use client";

import Image from "next/image";
import Link from "next/link";
import type { SyntheticEvent } from "react";
import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";

import { audioController } from "@/lib/audio-controller";
import { loadYouTubeIframeApi, type YouTubePlayer } from "@/lib/youtube-iframe";
import { formatPlaybackTime } from "@/lib/utils";
import { playerActions, setPlayerState, usePlayerStore } from "@/store/playerStore";

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
          className="fixed inset-0 z-[70] overflow-y-auto overscroll-contain bg-[#030612]/92 px-3 py-3 backdrop-blur-2xl sm:px-6 sm:py-6"
          onClick={handleCloseExpanded}
        >
          <div
            className="mx-auto flex min-h-full w-full max-w-7xl flex-col rounded-[24px] border border-white/8 bg-[rgba(9,14,24,0.94)] p-4 shadow-[0_40px_140px_rgba(0,0,0,0.5)] sm:rounded-[34px] sm:p-6 lg:p-8"
            onClick={stopPropagation}
          >
            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <p className="text-[10px] uppercase tracking-[0.24em] text-slate-400 sm:text-[11px] sm:tracking-[0.32em]">Spasiba Player</p>
                <h2 className="mt-2 text-[28px] font-semibold tracking-tight text-white sm:mt-3 sm:text-4xl">{currentSong.title}</h2>
                <p className="mt-2 text-[15px] text-slate-300/75 sm:text-base">{currentSong.author}</p>
              </div>

              <div className="flex flex-wrap items-center gap-2.5 sm:gap-3">
                <div className="flex items-center rounded-full border border-white/10 bg-white/[0.04] p-1">
                  <button
                    type="button"
                    onClick={() => handleModeChange("audio")}
                    className={`rounded-full px-3 py-2 text-[13px] transition sm:px-4 sm:text-sm ${mode === "audio" ? "bg-white text-slate-950" : "text-slate-300"}`}
                  >
                    Audio
                  </button>
                  <button
                    type="button"
                    onClick={() => handleModeChange("video")}
                    className={`rounded-full px-3 py-2 text-[13px] transition sm:px-4 sm:text-sm ${mode === "video" ? "bg-white text-slate-950" : "text-slate-300"}`}
                  >
                    Video
                  </button>
                </div>

                <Link
                  href={currentSong.url}
                  target="_blank"
                  rel="noreferrer"
                  className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-2 text-[13px] text-slate-200 transition hover:bg-white/[0.1] sm:px-4 sm:text-sm"
                >
                  Open on YouTube
                </Link>

                <div onClick={stopPropagation}>
                  <PlayerTrackMenu
                    song={currentSong}
                    onClosePlayer={() => setExpanded(false)}
                    className="flex h-10 w-10 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] text-slate-200 transition hover:bg-white/[0.1]"
                  />
                </div>

                <button
                  type="button"
                  onClick={handleCloseExpanded}
                  className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-2 text-[13px] text-slate-200 transition hover:bg-white/[0.1] sm:px-4 sm:text-sm"
                >
                  Close
                </button>
              </div>
            </div>

            <div className="mt-6 grid gap-6 lg:grid-cols-[1.02fr_0.98fr]">
              <div className="glass-panel flex min-h-[260px] items-center justify-center rounded-[22px] p-3 sm:min-h-[320px] sm:rounded-[30px] sm:p-6">
                {mode === "video" ? (
                  <div className="aspect-video w-full overflow-hidden rounded-[20px] bg-black sm:rounded-[26px]">
                    <div ref={videoContainerRef} className="h-full w-full" />
                  </div>
                ) : (
                  <div className="relative aspect-square w-full max-w-[620px] overflow-hidden rounded-[22px] bg-white/[0.05] sm:rounded-[30px]">
                    <Image
                      src={currentSong.thumbnail}
                      alt={currentSong.title}
                      fill
                      sizes="(max-width: 1024px) 80vw, 42vw"
                      quality={75}
                      className="object-cover"
                    />
                  </div>
                )}
              </div>

              <div className="flex min-h-0 flex-col gap-6 lg:overflow-y-auto lg:pr-1">
                <div className="grid gap-4 xl:grid-cols-[1fr_1fr]">
                  <div className="rounded-[22px] border border-white/8 bg-white/[0.03] p-4 sm:rounded-[28px] sm:p-5">
                    <p className="text-[10px] uppercase tracking-[0.22em] text-slate-400 sm:text-[11px] sm:tracking-[0.28em]">{mode === "video" ? "Video mode" : "Audio mode"}</p>
                    <p className="mt-3 text-[15px] leading-7 text-slate-200/90 sm:text-base">
                      {mode === "video"
                        ? "The same progress bar now drives the video too, so seeking, pause, and resume stay in sync."
                        : "Tap the mini player anytime to return here for artwork, queue, and bigger controls."}
                    </p>
                  </div>

                  <div className="rounded-[22px] border border-white/8 bg-white/[0.03] p-4 sm:rounded-[28px] sm:p-5">
                    <p className="text-[10px] uppercase tracking-[0.22em] text-slate-400 sm:text-[11px] sm:tracking-[0.28em]">Up next</p>
                    <p className="mt-3 text-xl font-semibold tracking-tight text-white sm:text-2xl">{totalQueuedTracks}</p>
                    <p className="mt-2 text-[13px] text-slate-300/75 sm:text-sm">tracks lined up in the queue</p>
                  </div>
                </div>

                <div className="space-y-5">
                  <div className="flex items-center justify-center gap-3 sm:gap-4">
                    <button
                      type="button"
                      onClick={handlePrevious}
                      className="rounded-full border border-white/10 bg-white/[0.04] p-3 text-white transition hover:bg-white/[0.1] sm:p-4"
                    >
                      <svg aria-hidden viewBox="0 0 24 24" className="h-5 w-5 sm:h-6 sm:w-6">
                        <path fill="currentColor" d="M7 6h2v12H7zm3 6.5v-1l8-5v11z" />
                      </svg>
                    </button>

                    <button
                      type="button"
                      onClick={handleTogglePlayback}
                      className="flex h-16 w-16 items-center justify-center rounded-full bg-white text-slate-950 shadow-xl shadow-black/40 transition hover:scale-[1.02] hover:bg-teal-300 sm:h-20 sm:w-20"
                    >
                      {isLoading ? (
                        <div className="h-7 w-7 rounded-full border-[3px] border-slate-950/25 border-t-slate-950 animate-spin sm:h-8 sm:w-8" aria-hidden />
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
                    >
                      <svg aria-hidden viewBox="0 0 24 24" className="h-5 w-5 sm:h-6 sm:w-6">
                        <path fill="currentColor" d="M15 6h2v12h-2zm-1 5.5v1l-8 5v-11z" />
                      </svg>
                    </button>
                  </div>

                  <div className="space-y-3">
                    <div className="relative h-3 overflow-hidden rounded-full bg-white/8">
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

                  <div className="flex items-center gap-4">
                    <span className="text-xs uppercase tracking-[0.24em] text-slate-400">Volume</span>
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

                <div className="rounded-[22px] border border-white/8 bg-white/[0.03] p-4 sm:rounded-[28px] sm:p-5">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <p className="text-[10px] uppercase tracking-[0.22em] text-slate-400 sm:text-[11px] sm:tracking-[0.28em]">Queue</p>
                      <p className="mt-2 text-[13px] text-slate-300/75 sm:text-sm">Drag songs to change what plays next.</p>
                    </div>
                    {queueSource === "playlist" ? (
                      <div className="flex items-center gap-3">
                        <div className="text-right">
                          <p className="text-[10px] uppercase tracking-[0.22em] text-slate-400">Mix</p>
                          <p className="mt-1 text-xs text-slate-300/70">{playlistMixEnabled ? "Suggestions continue after your playlist" : "Playlist only"}</p>
                        </div>
                        <button
                          type="button"
                          onClick={(event) => {
                            event.stopPropagation();
                            audioController.setPlaylistMixEnabled(!playlistMixEnabled);
                          }}
                          className={`rounded-full border px-4 py-2 text-[11px] uppercase tracking-[0.22em] transition ${
                            playlistMixEnabled
                              ? "border-teal-300/30 bg-teal-300/14 text-teal-100"
                              : "border-white/10 bg-white/[0.04] text-slate-200"
                          }`}
                        >
                          {playlistMixEnabled ? "Mix on" : "Mix off"}
                        </button>
                      </div>
                    ) : null}
                  </div>

                  <div className="mt-4 max-h-[36vh] space-y-3 overflow-y-auto pr-1 sm:mt-5 sm:max-h-[42vh] sm:pr-2">
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
                          className={`flex w-full items-center gap-3 rounded-[18px] border px-3 py-3 text-left transition sm:gap-4 sm:rounded-[22px] sm:px-4 sm:py-4 ${
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

                          <div className="relative h-12 w-12 shrink-0 overflow-hidden rounded-[16px] bg-white/[0.05] sm:h-14 sm:w-14 sm:rounded-2xl">
                            <Image src={track.thumbnail} alt={track.title} fill sizes="(max-width: 640px) 48px, 56px" quality={70} className="object-cover" />
                          </div>

                          <div className="min-w-0 flex-1">
                            <p className="line-clamp-1 text-[13px] font-medium text-white sm:text-sm">{track.title}</p>
                            <p className="mt-1 line-clamp-1 text-xs text-slate-400">{track.author}</p>
                          </div>

                          <span className="shrink-0 text-[10px] uppercase tracking-[0.18em] text-slate-300 sm:text-xs sm:tracking-[0.22em]">{track.duration}</span>
                        </div>
                      ))
                    ) : (
                      <div className="rounded-[18px] border border-dashed border-white/10 bg-white/[0.03] px-4 py-5 text-[13px] text-slate-300/75 sm:rounded-[22px] sm:text-sm">
                        The smart queue is still warming up.
                      </div>
                    )}
                  </div>
                </div>

                {error && !isLoading ? <p className="text-sm text-rose-200">{error}</p> : null}
              </div>
            </div>
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
      <div className="app-player-dock fixed inset-x-0 bottom-0 z-50 p-3 sm:p-4">
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
                  <Image src={currentSong.thumbnail} alt={currentSong.title} fill sizes="(max-width: 640px) 56px, 64px" quality={70} className="object-cover" />
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
      ) : null}
    </>
  );
}

const Player = memo(PlayerComponent);

export default Player;
