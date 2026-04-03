"use client";

export const PLAYLISTS_UPDATED_EVENT = "spasiba:playlists-updated";

export function emitPlaylistsUpdatedEvent() {
  if (typeof window === "undefined") {
    return;
  }

  window.dispatchEvent(new CustomEvent(PLAYLISTS_UPDATED_EVENT));
}
