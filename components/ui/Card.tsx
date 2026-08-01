"use client";

import type { ComponentPropsWithoutRef, ElementType, ReactNode } from "react";

type CardProps<T extends ElementType = "div"> = {
  as?: ElementType;
  children: ReactNode;
  className?: string;
  interactive?: boolean;
  surface?: boolean;
} & Omit<ComponentPropsWithoutRef<T>, "as" | "children" | "className">;

export default function Card({
  as: Component = "div",
  children,
  className,
  interactive = false,
  surface = false,
  ...props
}: CardProps) {
  return (
    <Component
      className={[
        "ui-card",
        interactive ? "ui-card--interactive ui-pressable" : "",
        surface ? "ui-card--surface" : "",
        className || ""
      ]
        .filter(Boolean)
        .join(" ")}
      {...props}
    >
      {children}
    </Component>
  );
}
