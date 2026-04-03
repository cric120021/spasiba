import Link from "next/link";

import type { TrendingSection } from "@/lib/types";

import LibrarySection from "@/components/LibrarySection";
import SongGrid from "@/components/SongGrid";

type HomeClassicProps = {
  sections: TrendingSection[];
};

export default function HomeClassic({ sections }: HomeClassicProps) {
  const availableSections = sections.filter((section) => section.tracks.length > 0);
  const [featuredSection, ...remainingSections] = availableSections;

  return (
    <div className="space-y-8">
      <section className="glass-panel-strong rounded-[24px] p-4 sm:rounded-[32px] sm:p-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div className="space-y-3">
            <div className="inline-flex items-center gap-2 rounded-full border border-white/[0.08] bg-white/[0.05] px-3 py-1.5 text-[10px] uppercase tracking-[0.24em] text-slate-300 sm:px-4 sm:py-2 sm:text-[11px] sm:tracking-[0.3em]">
              Spasiba
            </div>
            <h1 className="max-w-3xl text-[28px] font-semibold tracking-tight text-white sm:text-4xl">
              Pick a category and play.
            </h1>
            <p className="max-w-3xl text-[15px] leading-6 text-slate-300/78 sm:text-base">
              Explore your mood, pick a song, and keep the music going.
            </p>
          </div>

          <div className="-mx-1 flex snap-x snap-mandatory gap-2 overflow-x-auto px-1 pb-1 sm:mx-0 sm:flex-wrap sm:overflow-visible sm:px-0 sm:pb-0">
            {sections.map((section) => (
              <Link
                key={section.id}
                href={`/search?q=${encodeURIComponent(section.query)}`}
                className="snap-start whitespace-nowrap rounded-full border border-white/[0.08] bg-white/[0.06] px-3 py-2 text-[11px] text-slate-100 transition hover:border-teal-300/35 hover:bg-white/10 sm:text-sm"
              >
                {section.title}
              </Link>
            ))}
          </div>
        </div>
      </section>

      {featuredSection ? (
        <SongGrid
          title={featuredSection.title}
          subtitle={featuredSection.blurb}
          tracks={featuredSection.tracks}
          prefetchCount={2}
        />
      ) : null}

      <LibrarySection />

      {availableSections.length > 0 ? (
        remainingSections.map((section) => (
          <SongGrid
            key={section.id}
            title={section.title}
            subtitle={section.blurb}
            tracks={section.tracks}
            prefetchCount={1}
          />
        ))
      ) : (
        <section className="glass-panel rounded-[32px] p-6 text-sm leading-7 text-slate-300/78 sm:p-8 sm:text-base">
          Music is loading. You can use the search bar above while the home feed fills in.
        </section>
      )}
    </div>
  );
}
