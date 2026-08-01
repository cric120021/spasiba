"use client";

import { createPortal } from "react-dom";
import { useEffect, useRef, useState } from "react";

import { readUiMode, setUiMode, UI_MODE_EVENT, UI_MODES, type UiMode } from "@/lib/ui-mode";

type MenuPosition = {
  top: number;
  left: number;
};

function getMenuPosition(rect: DOMRect) {
  const width = 224;
  const padding = 16;
  const left = Math.min(Math.max(padding, rect.right - width), window.innerWidth - width - padding);
  const belowTop = rect.bottom + 10;

  return {
    top: belowTop,
    left
  } satisfies MenuPosition;
}

export default function UiModeButton({ className }: { className?: string }) {
  const buttonRef = useRef<HTMLButtonElement | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);
  const [mounted, setMounted] = useState(false);
  const [open, setOpen] = useState(false);
  const [mode, setModeState] = useState<UiMode>("night");
  const [position, setPosition] = useState<MenuPosition | null>(null);

  useEffect(() => {
    setMounted(true);
    setModeState(readUiMode());
  }, []);

  useEffect(() => {
    const handleModeChange = (event: Event) => {
      const detail = (event as CustomEvent<{ mode?: UiMode }>).detail;
      setModeState(detail?.mode || readUiMode());
    };

    window.addEventListener(UI_MODE_EVENT, handleModeChange as EventListener);
    return () => window.removeEventListener(UI_MODE_EVENT, handleModeChange as EventListener);
  }, []);

  useEffect(() => {
    if (!open) {
      return;
    }

    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target as Node | null;

      if (buttonRef.current?.contains(target) || menuRef.current?.contains(target)) {
        return;
      }

      setOpen(false);
    };

    window.addEventListener("mousedown", handlePointerDown);
    window.addEventListener("scroll", () => setOpen(false), true);

    return () => {
      window.removeEventListener("mousedown", handlePointerDown);
      window.removeEventListener("scroll", () => setOpen(false), true);
    };
  }, [open]);

  const menu =
    mounted && open && position
      ? createPortal(
          <div
            ref={menuRef}
            className="fixed z-[170] w-56 rounded-[22px] border border-white/10 bg-[rgba(8,14,24,0.98)] p-2 shadow-[0_24px_70px_rgba(0,0,0,0.55)] backdrop-blur-xl"
            style={{ top: position.top, left: position.left }}
          >
            {UI_MODES.map((entry) => {
              const isActive = entry.id === mode;

              return (
                <button
                  key={entry.id}
                  type="button"
                  onClick={() => {
                    setUiMode(entry.id);
                    setModeState(entry.id);
                    setOpen(false);
                  }}
                  className={[
                    "flex w-full flex-col items-start rounded-2xl px-4 py-3 text-left transition",
                    isActive ? "bg-white/[0.09]" : "hover:bg-white/[0.06]"
                  ].join(" ")}
                >
                  <span className="text-sm font-semibold text-white">{entry.label}</span>
                  <span className="mt-1 text-xs text-slate-300/72">{entry.description}</span>
                </button>
              );
            })}
          </div>,
          document.body
        )
      : null;

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        onClick={(event) => {
          event.preventDefault();
          event.stopPropagation();

          if (!open && buttonRef.current) {
            setPosition(getMenuPosition(buttonRef.current.getBoundingClientRect()));
          }

          setOpen((previous) => !previous);
        }}
        className={className}
        aria-label={`UI mode: ${mode}`}
        title={`UI mode: ${mode}`}
      >
        <svg aria-hidden viewBox="0 0 24 24" className="h-4.5 w-4.5 sm:h-5 sm:w-5">
          <path
            fill="currentColor"
            d="M12 2.8l1.76 5.44 5.44 1.76-5.44 1.76L12 17.2l-1.76-5.44-5.44-1.76 5.44-1.76zm6.4 10.7.96 2.94 2.94.96-2.94.96-.96 2.94-.96-2.94-2.94-.96 2.94-.96zm-12.8 1.2.96 2.94 2.94.96-2.94.96-.96 2.94-.96-2.94-2.94-.96 2.94-.96z"
          />
        </svg>
      </button>
      {menu}
    </>
  );
}
