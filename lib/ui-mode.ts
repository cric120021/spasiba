"use client";

export const UI_MODE_STORAGE_KEY = "spasiba-ui-mode";
export const UI_MODE_EVENT = "spasiba-ui-mode-change";

export type UiMode = "night" | "focus" | "party";

export const UI_MODES: Array<{
  id: UiMode;
  label: string;
  description: string;
}> = [
  {
    id: "night",
    label: "Night",
    description: "Ultra-dark, calm, default mode."
  },
  {
    id: "focus",
    label: "Focus",
    description: "Reduced chrome and lower visual noise."
  },
  {
    id: "party",
    label: "Party",
    description: "Stronger glow, brighter accent energy."
  }
];

export function normalizeUiMode(value: unknown): UiMode {
  return value === "focus" || value === "party" ? value : "night";
}

export function readUiMode(): UiMode {
  if (typeof window === "undefined") {
    return "night";
  }

  try {
    return normalizeUiMode(window.localStorage.getItem(UI_MODE_STORAGE_KEY));
  } catch {
    return "night";
  }
}

export function applyUiMode(mode: UiMode) {
  if (typeof document === "undefined") {
    return mode;
  }

  document.documentElement.dataset.uiMode = mode;
  return mode;
}

export function setUiMode(mode: UiMode) {
  if (typeof window !== "undefined") {
    try {
      window.localStorage.setItem(UI_MODE_STORAGE_KEY, mode);
    } catch {
      // Ignore storage failures.
    }

    window.dispatchEvent(
      new CustomEvent(UI_MODE_EVENT, {
        detail: {
          mode
        }
      })
    );
  }

  return applyUiMode(mode);
}
