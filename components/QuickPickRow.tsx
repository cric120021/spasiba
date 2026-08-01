"use client";

import { memo } from "react";

import type { Song } from "@/lib/types";
import { formatCompactCount } from "@/lib/utils";

import TrackActionsMenu from "@/components/TrackActionsMenu";
import Artwork from "@/components/ui/Artwork";
import Row from "@/components/ui/Row";

type QuickPickRowProps = {
  song: Song;
  queue: Song[];
  currentSongId: string | null;
  isPlaying: boolean;
  onPlay: (song: Song, queue: Song[]) => void;
  variant?: "compact" | "large";
};

function QuickPickRowComponent({
  song,
  queue,
  currentSongId,
  isPlaying,
  onPlay,
  variant = "compact"
}: QuickPickRowProps) {
  const isCurrent = song.id === currentSongId;
  const isLarge = variant === "large";

  return (
    <Row
      className={[
        "w-full text-left transition hover:bg-white/[0.06]",
        isLarge ? "ui-row--comfortable" : "ui-row--dense"
      ].join(" ")}
    >
      <button
        type="button"
        onClick={() => onPlay(song, queue)}
        className="flex min-w-0 flex-1 items-center gap-3 text-left"
      >
        <Artwork
          src={song.thumbnail}
          alt={song.title}
          size={isLarge ? 56 : 44}
          radius={isLarge ? "card" : "default"}
          sizes={isLarge ? "56px" : "44px"}
          quality={70}
          imageClassName={isLarge ? "object-cover" : "object-cover scale-[1.03]"}
          className="shrink-0 bg-slate-900"
        />
        <div className="min-w-0 flex-1">
          <p className={isLarge ? "line-clamp-1 text-[16px] font-medium text-white" : "line-clamp-1 text-[13px] font-medium text-white"}>
            {song.title}
          </p>
          <p className={isLarge ? "line-clamp-1 text-[13px] text-slate-300/76" : "line-clamp-1 text-[11px] text-slate-300/72"}>
            {song.author} <span className="text-slate-400">• {formatCompactCount(song.views)}</span>
          </p>
        </div>
      </button>

      <div className="flex shrink-0 items-center">
        <TrackActionsMenu
          song={song}
          className={[
            "flex items-center justify-center rounded-full border border-white/10 bg-white/[0.04] text-white/80 transition hover:bg-white/[0.08]",
            isLarge ? "h-10 w-10" : "h-7.5 w-7.5"
          ].join(" ")}
          />
      </div>
    </Row>
  );
}

const QuickPickRow = memo(QuickPickRowComponent);

export default QuickPickRow;
