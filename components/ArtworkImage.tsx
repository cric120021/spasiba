"use client";

import Image, { type ImageProps } from "next/image";

type ArtworkImageProps = ImageProps;

function isRemoteArtworkSource(src: ImageProps["src"]) {
  return typeof src === "string" && /^https?:\/\//i.test(src);
}

export default function ArtworkImage({ src, unoptimized, referrerPolicy, ...props }: ArtworkImageProps) {
  const isRemoteSource = isRemoteArtworkSource(src);

  return (
    <Image
      src={src}
      unoptimized={typeof unoptimized === "boolean" ? unoptimized : isRemoteSource}
      referrerPolicy={isRemoteSource ? (referrerPolicy ?? "no-referrer") : referrerPolicy}
      {...props}
    />
  );
}
