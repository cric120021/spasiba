"use client";

import Link from "next/link";
import { memo, useCallback, useEffect, useState } from "react";

import SongGrid from "@/components/SongGrid";
import { PROFILE_UPDATED_EVENT } from "@/lib/profile-events";
import { fetchProfile } from "@/lib/profile-client";
import type { Song, UserProfile } from "@/lib/types";

type ProfileCollectionPageProps = {
  kind: "history" | "liked";
};

function getCollection(profile: UserProfile | null, kind: "history" | "liked") {
  if (!profile) {
      return {
        title: kind === "history" ? "Recently played" : "Liked songs",
      subtitle: kind === "history" ? "Everything you listened to this month." : "Songs you marked with Like from the player.",
        tracks: [] as Song[]
      };
  }

  if (kind === "history") {
    return {
      title: "Recently played",
      subtitle: `Everything you listened to in ${profile.monthLabel}.`,
      tracks: profile.monthlyHistory
    };
  }

  return {
    title: "Liked songs",
    subtitle: "Songs you marked with Like from the player.",
    tracks: profile.likedSongs
  };
}

function ProfileCollectionPageComponent({ kind }: ProfileCollectionPageProps) {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);

    try {
      const nextProfile = await fetchProfile({ force: true });
      setProfile(nextProfile);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to load this section.");
    } finally {
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
    window.addEventListener("focus", handleUpdate);

    return () => {
      window.removeEventListener(PROFILE_UPDATED_EVENT, handleUpdate);
      window.removeEventListener("focus", handleUpdate);
    };
  }, [load]);

  const collection = getCollection(profile, kind);

  return (
    <div className="space-y-8">
      <section className="glass-panel-strong rounded-[36px] p-6 sm:p-8 lg:p-10">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div className="max-w-2xl">
            <p className="text-[11px] uppercase tracking-[0.32em] text-slate-400">Profile</p>
            <h1 className="mt-4 text-4xl font-semibold tracking-tight text-white sm:text-5xl">{collection.title}</h1>
            <p className="mt-4 text-sm leading-7 text-slate-300/78 sm:text-base">{collection.subtitle}</p>
          </div>
          <Link
            href="/profile"
            className="rounded-full border border-white/10 bg-white/[0.04] px-5 py-2.5 text-[11px] uppercase tracking-[0.24em] text-slate-200 transition hover:bg-white/[0.08]"
          >
            Back to profile
          </Link>
        </div>
      </section>

      {loading ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 lg:grid-cols-4 xl:grid-cols-6">
          {Array.from({ length: 12 }).map((_, index) => (
            <div key={index} className="shimmer aspect-[0.75] rounded-[24px]" />
          ))}
        </div>
      ) : error ? (
        <div className="glass-panel rounded-[28px] px-6 py-10 text-center text-sm text-rose-200">{error}</div>
      ) : collection.tracks.length ? (
        <SongGrid title={collection.title} subtitle={collection.subtitle} tracks={collection.tracks} prefetchCount={2} />
      ) : (
        <div className="glass-panel rounded-[28px] px-6 py-10 text-center text-sm text-slate-300/75">
          Nothing to show here yet.
        </div>
      )}
    </div>
  );
}

const ProfileCollectionPage = memo(ProfileCollectionPageComponent);

export default ProfileCollectionPage;
