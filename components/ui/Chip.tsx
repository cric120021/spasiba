"use client";

import type { ButtonHTMLAttributes, ReactNode } from "react";

type ChipProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode;
  active?: boolean;
};

export default function Chip({ children, className, active = false, type = "button", ...props }: ChipProps) {
  return (
    <button
      type={type}
      className={["ui-chip", active ? "ui-chip--active" : "", className || ""].filter(Boolean).join(" ")}
      {...props}
    >
      {children}
    </button>
  );
}
