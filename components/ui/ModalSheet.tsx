"use client";

import { createPortal } from "react-dom";
import { useEffect, useState, type ReactNode } from "react";

type ModalSheetProps = {
  open: boolean;
  onClose: () => void;
  title?: string;
  subtitle?: string;
  wide?: boolean;
  children: ReactNode;
  footer?: ReactNode;
};

export default function ModalSheet({
  open,
  onClose,
  title,
  subtitle,
  wide = false,
  children,
  footer
}: ModalSheetProps) {
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (!open) {
      return;
    }

    const previousOverflow = document.body.style.overflow;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };

    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [onClose, open]);

  if (!mounted || !open) {
    return null;
  }

  return createPortal(
    <div className="ui-sheet-backdrop" onClick={onClose}>
      <div
        className={["ui-sheet ui-screen", wide ? "ui-sheet--wide" : ""].filter(Boolean).join(" ")}
        onClick={(event) => event.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={title || "Spasiba sheet"}
      >
        {title || subtitle ? (
          <div className="ui-sheet__header">
            {title ? <h3 className="ui-title-h2 text-white">{title}</h3> : null}
            {subtitle ? <p className="mt-2 ui-body text-[var(--ui-text-muted)]">{subtitle}</p> : null}
          </div>
        ) : null}
        <div className="ui-sheet__body">{children}</div>
        {footer ? <div className="ui-sheet__body border-t border-white/8 pt-0">{footer}</div> : null}
      </div>
    </div>,
    document.body
  );
}
