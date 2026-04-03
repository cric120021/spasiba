import LibrarySection from "@/components/LibrarySection";

export default function LibraryPage() {
  return (
    <div className="space-y-6">
      <section className="space-y-1.5">
        <p className="text-[10px] uppercase tracking-[0.28em] text-slate-400">Library</p>
        <div className="flex items-center justify-between gap-3">
          <h1 className="text-[28px] font-semibold tracking-tight text-white sm:text-[34px]">Your playlists</h1>
          <span className="rounded-full border border-white/8 bg-white/[0.03] px-3 py-1 text-[9px] uppercase tracking-[0.18em] text-slate-300/72">
            Saved
          </span>
        </div>
        <p className="max-w-xl text-[13px] leading-5 text-slate-300/66 sm:text-[14px]">
          Your own playlists plus the shared ones you kept.
        </p>
      </section>

      <LibrarySection />
    </div>
  );
}
