export const spacing = {
  4: 4,
  8: 8,
  12: 12,
  16: 16,
  20: 20,
  24: 24,
  32: 32
} as const;

export const radii = {
  small: 8,
  default: 12,
  card: 16,
  surface: 20
} as const;

export const motion = {
  fast: 150,
  normal: 220,
  slow: 300,
  easing: "cubic-bezier(0.4, 0, 0.2, 1)"
} as const;

export const typography = {
  h1: {
    fontSize: "clamp(1.9rem, 4.4vw, 2.8rem)",
    lineHeight: 0.98,
    fontWeight: 700
  },
  h2: {
    fontSize: "clamp(1.25rem, 2.8vw, 1.7rem)",
    lineHeight: 1.06,
    fontWeight: 650
  },
  body: {
    fontSize: "0.94rem",
    lineHeight: 1.45,
    fontWeight: 500
  },
  meta: {
    fontSize: "0.74rem",
    lineHeight: 1.3,
    fontWeight: 500
  }
} as const;

export const uiModes = ["night", "focus", "party"] as const;
export type UiMode = (typeof uiModes)[number];
