"use client";

import type { ElementType, ReactNode } from "react";

type RowProps = {
  as?: ElementType;
  children: ReactNode;
  className?: string;
  density?: "dense" | "comfortable";
};

export default function Row({ as: Component = "div", children, className, density = "comfortable" }: RowProps) {
  return (
    <Component
      className={[
        "ui-row",
        density === "dense" ? "ui-row--dense" : "ui-row--comfortable",
        className || ""
      ]
        .filter(Boolean)
        .join(" ")}
    >
      {children}
    </Component>
  );
}
