"use client";

import type { HTMLAttributes, ReactNode } from "react";

type PlayerSurfaceProps = {
  children: ReactNode;
  className?: string;
} & HTMLAttributes<HTMLDivElement>;

export default function PlayerSurface({ children, className, ...props }: PlayerSurfaceProps) {
  return (
    <div className={["ui-player-surface", className || ""].filter(Boolean).join(" ")} {...props}>
      {children}
    </div>
  );
}
