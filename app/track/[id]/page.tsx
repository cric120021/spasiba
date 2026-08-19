import { Metadata } from "next";
import Image from "next/image";
import TrackRedirectClient from "./TrackRedirectClient";

interface Props {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
}

export async function generateMetadata({ params, searchParams }: Props): Promise<Metadata> {
  const { id } = await params;
  const sParams = await searchParams;
  const title = (typeof sParams.title === "string" ? sParams.title : "") || "Spasiba Music";
  const author = (typeof sParams.author === "string" ? sParams.author : "") || "Shared Track";
  const art = (typeof sParams.art === "string" ? sParams.art : "") || `https://i.ytimg.com/vi/${id}/hqdefault.jpg`;

  return {
    title: `${title} - ${author} | Spasiba Music`,
    description: `Listen to '${title}' by ${author} on Spasiba Music.`,
    openGraph: {
      title: `${title} - ${author}`,
      description: `Listen to '${title}' on Spasiba Music`,
      images: [{ url: art }],
    },
  };
}

export default async function TrackPage({ params, searchParams }: Props) {
  const { id } = await params;
  const sParams = await searchParams;
  const title = (typeof sParams.title === "string" ? sParams.title : "") || "Unknown Title";
  const author = (typeof sParams.author === "string" ? sParams.author : "") || "Spasiba Music";
  const art = (typeof sParams.art === "string" ? sParams.art : "") || `https://i.ytimg.com/vi/${id}/hqdefault.jpg`;

  const queryString = new URLSearchParams();
  Object.entries(sParams).forEach(([k, v]) => {
    if (typeof v === "string") queryString.set(k, v);
  });
  const qs = queryString.toString() ? `?${queryString.toString()}` : "";
  const deepLinkUrl = `spasiba://track/${id}${qs}`;

  return (
    <div className="flex min-h-[85vh] flex-col items-center justify-center px-4 py-12 text-center">
      <TrackRedirectClient deepLinkUrl={deepLinkUrl} />

      <div className="w-full max-w-md rounded-3xl border border-white/10 bg-black/60 p-8 shadow-2xl backdrop-blur-2xl">
        <div className="relative mx-auto aspect-square w-48 overflow-hidden rounded-2xl border border-white/15 shadow-lg shadow-black/80 sm:w-56">
          <img
            src={art}
            alt={title}
            className="h-full w-full object-cover"
          />
        </div>

        <div className="mt-6 space-y-1">
          <h1 className="text-2xl font-bold text-white tracking-tight sm:text-3xl line-clamp-1">{title}</h1>
          <p className="text-sm font-medium text-red-400/90 sm:text-base">{author}</p>
        </div>

        <div className="mt-8 space-y-3">
          <a
            href={deepLinkUrl}
            className="inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-red-600 to-rose-700 px-6 py-4 text-base font-semibold text-white shadow-lg shadow-red-600/30 transition-all hover:scale-[1.02] active:scale-[0.98]"
          >
            <span>▶️ Open in Spasiba App</span>
          </a>

          <p className="text-xs text-white/50">
            If Spasiba is installed, it will play instantly.
          </p>
        </div>
      </div>
    </div>
  );
}
