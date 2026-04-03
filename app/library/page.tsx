import LibrarySection from "@/components/LibrarySection";

export default function LibraryPage() {
  return (
    <div className="space-y-8">
      <section className="glass-panel-strong rounded-[36px] p-6 sm:p-8 lg:p-10">
        <p className="text-[11px] uppercase tracking-[0.32em] text-slate-400">Spasiba</p>
        <h1 className="mt-4 text-4xl font-semibold tracking-tight text-white sm:text-5xl">Your playlists</h1>
        <p className="mt-4 max-w-2xl text-sm leading-7 text-slate-300/78 sm:text-base">
          Your own playlists plus the shared playlists you chose to keep in your library.
        </p>
      </section>

      <LibrarySection />
    </div>
  );
}
