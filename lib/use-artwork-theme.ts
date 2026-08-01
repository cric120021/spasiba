"use client";

import { useEffect, useMemo, useState } from "react";

const artworkThemeCache = new Map<string, string>();

function hashString(value: string) {
  let hash = 0;

  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) | 0;
  }

  return Math.abs(hash);
}

function fallbackRgbFromSource(source: string) {
  const hash = hashString(source || "spasiba");
  const hue = hash % 360;
  const saturation = 62 + (hash % 10);
  const lightness = 52 + (hash % 8);
  const a = saturation * Math.min(lightness, 100 - lightness) / 100;

  const f = (n: number) => {
    const k = (n + hue / 30) % 12;
    const color = lightness - a * Math.max(Math.min(k - 3, 9 - k, 1), -1);
    return Math.round((255 * color) / 100);
  };

  return `${f(0)} ${f(8)} ${f(4)}`;
}

async function sampleArtworkRgb(source: string) {
  const image = new window.Image();
  image.crossOrigin = "anonymous";
  image.referrerPolicy = "no-referrer";
  image.decoding = "async";

  await new Promise<void>((resolve, reject) => {
    image.onload = () => resolve();
    image.onerror = () => reject(new Error("Unable to load artwork."));
    image.src = source;
  });

  const canvas = document.createElement("canvas");
  const context = canvas.getContext("2d", { willReadFrequently: true });

  if (!context) {
    throw new Error("Canvas is unavailable.");
  }

  const size = 18;
  canvas.width = size;
  canvas.height = size;
  context.drawImage(image, 0, 0, size, size);
  const pixels = context.getImageData(0, 0, size, size).data;

  let totalWeight = 0;
  let red = 0;
  let green = 0;
  let blue = 0;

  for (let index = 0; index < pixels.length; index += 4) {
    const alpha = pixels[index + 3] / 255;

    if (alpha < 0.4) {
      continue;
    }

    const nextRed = pixels[index];
    const nextGreen = pixels[index + 1];
    const nextBlue = pixels[index + 2];
    const brightness = (nextRed * 0.299 + nextGreen * 0.587 + nextBlue * 0.114) / 255;
    const saturation = Math.max(nextRed, nextGreen, nextBlue) - Math.min(nextRed, nextGreen, nextBlue);
    const weight = alpha * (0.55 + saturation / 255) * (0.75 + brightness * 0.4);

    red += nextRed * weight;
    green += nextGreen * weight;
    blue += nextBlue * weight;
    totalWeight += weight;
  }

  if (totalWeight <= 0.001) {
    throw new Error("Artwork theme sampling failed.");
  }

  const sampledRed = Math.round(red / totalWeight);
  const sampledGreen = Math.round(green / totalWeight);
  const sampledBlue = Math.round(blue / totalWeight);

  return `${sampledRed} ${sampledGreen} ${sampledBlue}`;
}

async function resolveArtworkThemeRgb(source: string) {
  if (!source) {
    return fallbackRgbFromSource("spasiba");
  }

  const cached = artworkThemeCache.get(source);

  if (cached) {
    return cached;
  }

  try {
    const sampled = await sampleArtworkRgb(source);
    artworkThemeCache.set(source, sampled);
    return sampled;
  } catch {
    const fallback = fallbackRgbFromSource(source);
    artworkThemeCache.set(source, fallback);
    return fallback;
  }
}

export function useArtworkTheme(source: string | null | undefined) {
  const [rgb, setRgb] = useState<string>(() => fallbackRgbFromSource(source || "spasiba"));

  useEffect(() => {
    let cancelled = false;

    void resolveArtworkThemeRgb(source || "spasiba").then((resolved) => {
      if (!cancelled) {
        setRgb(resolved);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [source]);

  return useMemo(
    () => ({
      rgb,
      style: {
        ["--ui-accent-rgb" as string]: rgb
      }
    }),
    [rgb]
  );
}
