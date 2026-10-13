import SkeletonGrid from "@/components/SkeletonGrid";

export default function Loading() {
  return (
    <div className="space-y-8">
      <section className="glass-panel-strong rounded-[32px] p-6 sm:p-8">
        <div className="shimmer h-5 w-32 rounded-full" />
        <div className="mt-4 space-y-3">
          <div className="shimmer h-12 w-full max-w-2xl rounded-2xl" />
          <div className="shimmer h-6 w-full max-w-3xl rounded-2xl" />
        </div>
      </section>
      <SkeletonGrid count={12} />
    </div>
  );
}
