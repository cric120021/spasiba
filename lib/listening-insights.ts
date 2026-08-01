"use client";

import { readPlayerQueueSnapshot } from "@/lib/player-queue-cache";
import { readCachedProfile } from "@/lib/profile-client";

export function readContinueListeningSnapshot() {
  const snapshot = readPlayerQueueSnapshot();

  if (!snapshot?.queue?.length) {
    return null;
  }

  const currentTrack = snapshot.queue[snapshot.currentIndex];

  if (!currentTrack) {
    return null;
  }

  return {
    track: currentTrack,
    queue: snapshot.queue,
    currentIndex: snapshot.currentIndex,
    queueSource: snapshot.queueSource,
    queueSourceLabel: snapshot.queueSourceLabel,
    playlistBaseTrackIds: snapshot.playlistBaseTrackIds,
    playlistMixEnabled: snapshot.playlistMixEnabled
  };
}

export function readListeningDaypartInsight() {
  const profile = readCachedProfile();
  const entries = profile?.monthlyHistory || [];

  if (!entries.length) {
    return null;
  }

  const counts = {
    morning: 0,
    afternoon: 0,
    evening: 0,
    night: 0
  };

  entries.forEach((entry) => {
    const parsed = entry.lastListenedAt ? new Date(entry.lastListenedAt) : null;

    if (!parsed || Number.isNaN(parsed.getTime())) {
      return;
    }

    const hours = parsed.getHours();

    if (hours >= 5 && hours < 12) {
      counts.morning += 1;
      return;
    }

    if (hours >= 12 && hours < 17) {
      counts.afternoon += 1;
      return;
    }

    if (hours >= 17 && hours < 22) {
      counts.evening += 1;
      return;
    }

    counts.night += 1;
  });

  const dominant = Object.entries(counts).sort((left, right) => right[1] - left[1])[0];

  if (!dominant || dominant[1] < 3) {
    return null;
  }

  const labels = {
    morning: "Usually a morning listener",
    afternoon: "You lean into afternoon sessions",
    evening: "Evenings are your prime listening time",
    night: "You usually come back at night"
  } as const;

  return labels[dominant[0] as keyof typeof labels];
}
