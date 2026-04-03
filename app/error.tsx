"use client";

type ErrorPageProps = {
  error: Error & { digest?: string };
  reset: () => void;
};

export default function ErrorPage({ error, reset }: ErrorPageProps) {
  return (
    <div className="space-y-6">
      <section className="glass-panel-strong rounded-[32px] p-6 sm:p-8">
        <p className="text-[11px] uppercase tracking-[0.3em] text-rose-200/80">Playback Interrupted</p>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white sm:text-4xl">
          Something slipped while loading the app shell.
        </h1>
        <p className="mt-4 max-w-2xl text-sm leading-7 text-slate-300/78 sm:text-base">
          {error.message || "A recoverable error occurred."}
        </p>
        <button
          type="button"
          onClick={reset}
          className="mt-6 rounded-full border border-white/10 bg-white/[0.06] px-5 py-3 text-sm text-white transition hover:border-teal-300/35 hover:bg-white/10"
        >
          Retry
        </button>
      </section>
    </div>
  );
}
