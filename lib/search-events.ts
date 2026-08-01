"use client";

export const SEARCH_SUBMIT_EVENT = "spasiba:search-submit";

export function dispatchSearchSubmit(query: string) {
  if (typeof window === "undefined") {
    return;
  }

  window.dispatchEvent(
    new CustomEvent(SEARCH_SUBMIT_EVENT, {
      detail: {
        query
      }
    })
  );
}
