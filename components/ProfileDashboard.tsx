"use client";

import Image from "next/image";
import Link from "next/link";
import { memo, type ChangeEvent, useCallback, useEffect, useRef, useState } from "react";

import SongGrid from "@/components/SongGrid";
import { audioController } from "@/lib/audio-controller";
import { emitProfileUpdatedEvent, PROFILE_UPDATED_EVENT } from "@/lib/profile-events";
import type { UserProfile } from "@/lib/types";
import { clearProfileData, fetchProfile, readCachedProfile, saveProfileBasics, saveProfilePhoto } from "@/lib/profile-client";
import { playerActions } from "@/store/playerStore";

const PROFILE_PREVIEW_LIMIT = 8;

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(typeof reader.result === "string" ? reader.result : "");
    reader.onerror = () => reject(new Error("Unable to read this image."));
    reader.readAsDataURL(file);
  });
}

function loadImageElement(source: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new window.Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("Unable to load this image."));
    image.src = source;
  });
}

async function buildProfilePhotoDataUrl(file: File) {
  if (!file.type.startsWith("image/")) {
    throw new Error("Choose a valid image.");
  }

  const dataUrl = await readFileAsDataUrl(file);
  const image = await loadImageElement(dataUrl);
  const canvas = document.createElement("canvas");
  const size = 256;
  const cropSize = Math.min(image.width, image.height);
  const offsetX = Math.max(0, Math.floor((image.width - cropSize) / 2));
  const offsetY = Math.max(0, Math.floor((image.height - cropSize) / 2));
  canvas.width = size;
  canvas.height = size;
  const context = canvas.getContext("2d");

  if (!context) {
    throw new Error("Unable to process this image.");
  }

  context.drawImage(image, offsetX, offsetY, cropSize, cropSize, 0, 0, size, size);
  return canvas.toDataURL("image/webp", 0.84);
}

function ProfileDashboardComponent() {
  const initialProfile = readCachedProfile();
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const hasLoadedOnceRef = useRef(initialProfile !== undefined);
  const [profile, setProfile] = useState<UserProfile | null>(initialProfile ?? null);
  const [loading, setLoading] = useState(initialProfile === undefined);
  const [saving, setSaving] = useState(false);
  const [photoSaving, setPhotoSaving] = useState(false);
  const [clearingScope, setClearingScope] = useState<"history" | "suggestions" | null>(null);
  const [name, setName] = useState(initialProfile?.name || "");
  const [username, setUsername] = useState(initialProfile?.username || "");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const handleCopyUsername = useCallback(async () => {
    const value = profile?.username?.trim();

    if (!value) {
      return;
    }

    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(value);
      } else {
        const textarea = document.createElement("textarea");
        textarea.value = value;
        textarea.setAttribute("readonly", "true");
        textarea.style.position = "fixed";
        textarea.style.opacity = "0";
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand("copy");
        document.body.removeChild(textarea);
      }

      playerActions.showToast(`Copied @${value}.`);
    } catch {
      setError("Unable to copy your username right now.");
    }
  }, [profile?.username]);

  const load = useCallback(async () => {
    setError(null);

    try {
      const nextProfile = await fetchProfile();
      setProfile(nextProfile);
      setName(nextProfile?.name || "");
      setUsername(nextProfile?.username || "");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to load profile.");
    } finally {
      hasLoadedOnceRef.current = true;
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    const handleUpdate = () => {
      void load();
    };

    window.addEventListener(PROFILE_UPDATED_EVENT, handleUpdate);

    return () => {
      window.removeEventListener(PROFILE_UPDATED_EVENT, handleUpdate);
    };
  }, [load]);

  const handleSave = useCallback(async () => {
    setSaving(true);
    setError(null);
    setMessage(null);

    try {
      const nextProfile = await saveProfileBasics(name, username);
      setProfile(nextProfile);
      setName(nextProfile.name || "");
      setUsername(nextProfile.username || "");
      setMessage("Saved on this device. Cloud syncs next time you open the app.");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to save your profile.");
    } finally {
      setSaving(false);
    }
  }, [name, username]);

  const handlePhotoSelected = useCallback(async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];

    if (!file) {
      return;
    }

    setPhotoSaving(true);
    setError(null);
    setMessage(null);

    try {
      const nextPhotoDataUrl = await buildProfilePhotoDataUrl(file);
      const nextProfile = await saveProfilePhoto(nextPhotoDataUrl);
      setProfile(nextProfile);
      setMessage("Photo saved on this device. Cloud syncs next time you open the app.");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to update your photo.");
    } finally {
      event.target.value = "";
      setPhotoSaving(false);
    }
  }, []);

  const handleDeletePhoto = useCallback(async () => {
    setPhotoSaving(true);
    setError(null);
    setMessage(null);

    try {
      const nextProfile = await saveProfilePhoto(null);
      setProfile(nextProfile);
      setMessage("Photo removed on this device. Cloud syncs next time you open the app.");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to remove your photo.");
    } finally {
      setPhotoSaving(false);
    }
  }, []);

  const handleClearData = useCallback(
    async (scope: "history" | "suggestions") => {
      const confirmed = window.confirm(
        scope === "history"
          ? "Clear your previously viewed songs for this device?"
          : "Clear your saved suggestion profile for this device?"
      );

      if (!confirmed) {
        return;
      }

      setClearingScope(scope);
      setError(null);
      setMessage(null);

      try {
        await clearProfileData(scope);

        if (scope === "suggestions") {
          audioController.resetListenerProfile();
        }

        await load();
        emitProfileUpdatedEvent();
        setMessage(scope === "history" ? "Previously viewed songs cleared." : "Suggestions data cleared.");
      } catch (requestError) {
        setError(requestError instanceof Error ? requestError.message : "Unable to clear profile data.");
      } finally {
        setClearingScope(null);
      }
    },
    [load]
  );

  const monthlyPreview = profile?.monthlyHistory.slice(0, PROFILE_PREVIEW_LIMIT) || [];
  const likedPreview = profile?.likedSongs.slice(0, PROFILE_PREVIEW_LIMIT) || [];

  return (
    <div className="space-y-8">
      <section className="glass-panel-strong rounded-[34px] p-6 sm:p-8 lg:p-10">
        <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
          <div className="flex flex-col gap-5 sm:flex-row sm:items-center sm:gap-6">
            <div className="relative h-24 w-24 overflow-hidden rounded-[30px] border border-white/10 bg-white/[0.05]">
              {profile?.profileImageDataUrl ? (
                <Image src={profile.profileImageDataUrl} alt={profile?.name || "Profile photo"} fill unoptimized className="object-cover" />
              ) : (
                <div className="flex h-full w-full items-center justify-center text-slate-400">
                  <svg aria-hidden viewBox="0 0 24 24" className="h-10 w-10">
                    <path
                      fill="currentColor"
                      d="M12 12a4 4 0 1 0-4-4a4 4 0 0 0 4 4m0 2c-4.418 0-8 2.015-8 4.5V20h16v-1.5c0-2.485-3.582-4.5-8-4.5"
                    />
                  </svg>
                </div>
              )}
            </div>

            <div className="max-w-2xl">
            <p className="text-[11px] uppercase tracking-[0.32em] text-slate-400">Profile</p>
            <h1 className="mt-4 text-4xl font-semibold tracking-tight text-white sm:text-5xl">
              {profile?.name?.trim() ? profile.name : "Your music profile"}
            </h1>
            <p className="mt-4 text-sm leading-7 text-slate-300/78 sm:text-base">
              Keep your name here and let Spasiba remember what you listened to and what you kept coming back to.
            </p>
          </div>
          </div>

          <div className="w-full max-w-xl space-y-4">
            <div>
              <label className="block text-[11px] uppercase tracking-[0.24em] text-slate-400">Your name</label>
              <input
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="Enter your name"
                className="w-full rounded-2xl border border-white/10 bg-white/[0.05] px-4 py-3 text-base text-white placeholder:text-slate-500 focus:border-teal-300/35 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-[11px] uppercase tracking-[0.24em] text-slate-400">Username</label>
              <input
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="yourname"
                className="mt-2 w-full rounded-2xl border border-white/10 bg-white/[0.05] px-4 py-3 text-base text-white placeholder:text-slate-500 focus:border-teal-300/35 focus:outline-none"
              />
              <p className="mt-2 text-xs leading-5 text-slate-400">Use this unique username for playlist sharing and code access.</p>
            </div>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
              <button
                type="button"
                onClick={() => void handleSave()}
                disabled={saving}
                className="rounded-2xl border border-teal-300/25 bg-teal-300/12 px-5 py-3 text-sm font-medium text-teal-100 transition hover:bg-teal-300/18 disabled:cursor-wait disabled:opacity-70"
              >
                {saving ? "Saving..." : "Save"}
              </button>
              {profile?.username ? (
                <div className="flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.04] px-2 py-2 text-[11px] uppercase tracking-[0.22em] text-slate-300">
                  <span className="pl-2">@{profile.username}</span>
                  <button
                    type="button"
                    onClick={() => void handleCopyUsername()}
                    className="flex h-9 w-9 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] text-slate-200 transition hover:bg-white/[0.12]"
                    aria-label={`Copy @${profile.username}`}
                    title={`Copy @${profile.username}`}
                  >
                    <svg aria-hidden viewBox="0 0 24 24" className="h-4.5 w-4.5">
                      <path
                        fill="currentColor"
                        d="M16 1H6a2 2 0 0 0-2 2v12h2V3h10zm3 4H10a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2m0 16H10V7h9z"
                      />
                    </svg>
                  </button>
                </div>
              ) : null}
            </div>
            {message ? <p className="text-sm text-teal-100">{message}</p> : null}
            {error ? <p className="text-sm text-rose-200">{error}</p> : null}
          </div>
        </div>

        <div className="mt-6 flex flex-wrap items-center gap-3">
          <input ref={fileInputRef} type="file" accept="image/*" className="hidden" onChange={(event) => void handlePhotoSelected(event)} />
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={photoSaving}
            className="rounded-2xl border border-white/10 bg-white/[0.05] px-5 py-3 text-sm font-medium text-slate-100 transition hover:bg-white/[0.1] disabled:cursor-wait disabled:opacity-70"
          >
            {photoSaving ? "Saving photo..." : profile?.profileImageDataUrl ? "Add new photo" : "Add photo"}
          </button>
          {profile?.profileImageDataUrl ? (
            <button
              type="button"
              onClick={() => void handleDeletePhoto()}
              disabled={photoSaving}
              className="rounded-2xl border border-rose-300/18 bg-rose-300/10 px-5 py-3 text-sm font-medium text-rose-100 transition hover:bg-rose-300/16 disabled:cursor-wait disabled:opacity-70"
            >
              Delete photo
            </button>
          ) : null}
        </div>
      </section>

      <section className="glass-panel deferred-section rounded-[30px] p-5 sm:p-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="max-w-2xl">
            <p className="text-[11px] uppercase tracking-[0.24em] text-slate-400">Clear data</p>
            <h2 className="mt-2 text-2xl font-semibold tracking-tight text-white sm:text-3xl">Reset what Spasiba remembers</h2>
            <p className="mt-2 text-sm leading-6 text-slate-300/78">
              Clear your listening history or wipe the suggestion profile that powers the smart queue.
            </p>
          </div>

          <div className="flex flex-col gap-3 sm:flex-row">
            <button
              type="button"
              onClick={() => void handleClearData("history")}
              disabled={clearingScope !== null}
              className="rounded-2xl border border-white/10 bg-white/[0.05] px-5 py-3 text-sm font-medium text-slate-100 transition hover:bg-white/[0.1] disabled:cursor-wait disabled:opacity-70"
            >
              {clearingScope === "history" ? "Clearing..." : "Clear previously viewed"}
            </button>
            <button
              type="button"
              onClick={() => void handleClearData("suggestions")}
              disabled={clearingScope !== null}
              className="rounded-2xl border border-rose-300/18 bg-rose-300/10 px-5 py-3 text-sm font-medium text-rose-100 transition hover:bg-rose-300/16 disabled:cursor-wait disabled:opacity-70"
            >
              {clearingScope === "suggestions" ? "Clearing..." : "Clear suggestions data"}
            </button>
          </div>
        </div>
      </section>

      <section className="glass-panel deferred-section rounded-[30px] p-5 sm:p-6">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-[11px] uppercase tracking-[0.24em] text-slate-400">This month</p>
            <h2 className="mt-2 text-2xl font-semibold tracking-tight text-white sm:text-3xl">Previously listened songs</h2>
            <p className="mt-2 text-sm leading-6 text-slate-300/78">
              Everything you listened to in {profile?.monthLabel || "this month"}.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <div className="rounded-full border border-white/[0.08] bg-white/[0.04] px-4 py-2 text-[11px] uppercase tracking-[0.22em] text-slate-300/80">
              {profile?.monthlyHistory.length || 0} songs
            </div>
            {profile && profile.monthlyHistory.length > PROFILE_PREVIEW_LIMIT ? (
              <Link
                href="/profile/history"
                className="rounded-full border border-teal-300/20 bg-teal-300/10 px-4 py-2 text-[11px] uppercase tracking-[0.22em] text-teal-100 transition hover:bg-teal-300/16"
              >
                See all
              </Link>
            ) : null}
          </div>
        </div>
      </section>

      {loading ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 lg:grid-cols-4 xl:grid-cols-6">
          {Array.from({ length: 12 }).map((_, index) => (
            <div key={index} className="shimmer aspect-[0.75] rounded-[24px]" />
          ))}
        </div>
      ) : profile?.monthlyHistory.length ? (
        <SongGrid
          title="Recently played"
          subtitle={`Songs you listened to in ${profile.monthLabel}.`}
          tracks={monthlyPreview}
          prefetchCount={2}
        />
      ) : (
        <div className="glass-panel rounded-[28px] px-6 py-10 text-center text-sm text-slate-300/75">
          Start listening and your monthly history will show up here.
        </div>
      )}

      {profile?.likedSongs.length ? (
        <section className="glass-panel deferred-section rounded-[30px] p-5 sm:p-6">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="text-[11px] uppercase tracking-[0.24em] text-slate-400">Taste</p>
              <h2 className="mt-2 text-2xl font-semibold tracking-tight text-white sm:text-3xl">Liked songs</h2>
              <p className="mt-2 text-sm leading-6 text-slate-300/78">
                Songs you marked with Like from the player.
              </p>
            </div>
            <div className="flex items-center gap-2">
              <div className="rounded-full border border-white/[0.08] bg-white/[0.04] px-4 py-2 text-[11px] uppercase tracking-[0.22em] text-slate-300/80">
                {profile.likedSongs.length} songs
              </div>
              {profile.likedSongs.length > PROFILE_PREVIEW_LIMIT ? (
                <Link
                  href="/profile/liked"
                  className="rounded-full border border-teal-300/20 bg-teal-300/10 px-4 py-2 text-[11px] uppercase tracking-[0.22em] text-teal-100 transition hover:bg-teal-300/16"
                >
                  See all
                </Link>
              ) : null}
            </div>
          </div>
        </section>
      ) : null}

      {profile?.likedSongs.length ? (
        <SongGrid
          title="Liked songs"
          subtitle="Songs you marked with Like from the player."
          tracks={likedPreview}
          prefetchCount={2}
        />
      ) : (
        <section className="glass-panel rounded-[28px] px-6 py-10 text-center text-sm text-slate-300/75">
          Use Like from the player menu and your saved favorites will show up here.
        </section>
      )}
    </div>
  );
}

const ProfileDashboard = memo(ProfileDashboardComponent);

export default ProfileDashboard;
