"use client";

import Image from "next/image";
import { useMemo, useState } from "react";

type ArtworkProps = {
  src: string | null | undefined;
  alt: string;
  size?: number | string;
  radius?: "small" | "default" | "card" | "surface" | "circle";
  priority?: boolean;
  className?: string;
  imageClassName?: string;
  sizes?: string;
  quality?: number;
};

function resolveRadius(radius: ArtworkProps["radius"]) {
  switch (radius) {
    case "small":
      return "var(--radius-small)";
    case "default":
      return "var(--radius-default)";
    case "surface":
      return "var(--radius-surface)";
    case "circle":
      return "999px";
    case "card":
    default:
      return "var(--radius-card)";
  }
}

function isRemoteSource(src: string | null | undefined) {
  return typeof src === "string" && /^https?:\/\//i.test(src);
}

export default function Artwork({
  src,
  alt,
  size,
  radius = "card",
  priority = false,
  className,
  imageClassName,
  sizes = "100vw",
  quality = 74
}: ArtworkProps) {
  const [loaded, setLoaded] = useState(false);
  const [failed, setFailed] = useState(false);
  const radiusValue = resolveRadius(radius);
  const inlineSize = useMemo(() => {
    if (size === undefined) {
      return undefined;
    }

    return typeof size === "number" ? `${size}px` : size;
  }, [size]);

  return (
    <div
      className={[
        "relative overflow-hidden bg-white/[0.04] ui-party-saturate",
        className || ""
      ]
        .filter(Boolean)
        .join(" ")}
      style={{
        width: inlineSize,
        ...(inlineSize ? { height: inlineSize } : {}),
        aspectRatio: "1 / 1",
        borderRadius: radiusValue
      }}
    >
      {!loaded ? <div className="absolute inset-0 shimmer" aria-hidden /> : null}
      {src && !failed ? (
        <Image
          src={src}
          alt={alt}
          fill
          sizes={sizes}
          quality={quality}
          priority={priority}
          unoptimized={isRemoteSource(src)}
          referrerPolicy={isRemoteSource(src) ? "no-referrer" : undefined}
          className={["object-cover transition duration-300", imageClassName || ""].filter(Boolean).join(" ")}
          onLoad={() => setLoaded(true)}
          onError={() => {
            setFailed(true);
            setLoaded(true);
          }}
        />
      ) : (
        <div className="absolute inset-0 flex items-center justify-center text-white/52">
          <svg aria-hidden viewBox="0 0 24 24" className="h-7 w-7">
            <path
              fill="currentColor"
              d="M16 17a3 3 0 1 1-2-2.83V6.74l7-1.55V15a3 3 0 1 1-2-2.83V7.69l-3 .66zM6 19a3 3 0 1 1-2-2.83V8.74l7-1.55v9.98A3 3 0 0 1 6 19"
            />
          </svg>
        </div>
      )}
      <div className="pointer-events-none absolute inset-0 ring-1 ring-inset ring-white/[0.04]" style={{ borderRadius: radiusValue }} />
    </div>
  );
}
