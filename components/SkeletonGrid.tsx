type SkeletonGridProps = {
  count?: number;
};

export default function SkeletonGrid({ count = 12 }: SkeletonGridProps) {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className="glass-panel overflow-hidden rounded-[28px]">
          <div className="shimmer aspect-square" />
          <div className="space-y-3 p-4">
            <div className="shimmer h-5 rounded-full" />
            <div className="shimmer h-4 w-2/3 rounded-full" />
          </div>
        </div>
      ))}
    </div>
  );
}
