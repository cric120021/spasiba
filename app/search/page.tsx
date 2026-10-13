import { Suspense } from "react";

import SearchResults from "@/components/SearchResults";

export default function SearchPage() {
  return (
    <Suspense
      fallback={
        <section className="glass-panel rounded-[32px] p-6 text-sm text-slate-300/78 sm:p-8 sm:text-base">
          Loading search results...
        </section>
      }
    >
      <SearchResults />
    </Suspense>
  );
}
