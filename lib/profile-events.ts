export const PROFILE_UPDATED_EVENT = "spasiba:profile-updated";

export function emitProfileUpdatedEvent() {
  if (typeof window === "undefined") {
    return;
  }

  window.dispatchEvent(new CustomEvent(PROFILE_UPDATED_EVENT));
}
