import { cookies } from "next/headers";
import { notFound } from "next/navigation";

import SharedPlaylistView from "@/components/SharedPlaylistView";
import { getSharedPlaylistForVisitor } from "@/lib/playlists";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export default async function SharedPlaylistPage({ params }: { params: Promise<{ shareToken: string }> }) {
  const cookieStore = await cookies();
  const visitorId = cookieStore.get("spasiba_guest_id")?.value?.trim() || "";

  if (!visitorId) {
    notFound();
  }

  const { shareToken } = await params;
  const playlist = await getSharedPlaylistForVisitor(visitorId, shareToken);

  if (!playlist) {
    notFound();
  }

  return (
    <div className="space-y-8">
      <section className="glass-panel-strong rounded-[32px] p-6 sm:rounded-[36px] sm:p-8 lg:p-10">
        <p className="text-[11px] uppercase tracking-[0.32em] text-slate-400">Spasiba</p>
        <h1 className="mt-4 text-3xl font-semibold tracking-tight text-white sm:text-5xl">{playlist.name}</h1>
        <p className="mt-4 max-w-2xl text-sm leading-7 text-slate-300/78 sm:text-base">
          Shared with you through a playlist link. Press play on any song and it will open in the same Spasiba player.
        </p>
      </section>

      <SharedPlaylistView playlist={playlist} />
    </div>
  );
}
