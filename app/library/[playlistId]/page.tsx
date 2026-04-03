import { cookies } from "next/headers";
import { notFound } from "next/navigation";

import PlaylistDetailView from "@/components/PlaylistDetailView";
import { getVisitorPlaylistById } from "@/lib/playlists";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export default async function LibraryPlaylistPage({ params }: { params: Promise<{ playlistId: string }> }) {
  const cookieStore = await cookies();
  const visitorId = cookieStore.get("spasiba_guest_id")?.value?.trim() || "";

  if (!visitorId) {
    notFound();
  }

  const { playlistId } = await params;
  const playlist = await getVisitorPlaylistById(visitorId, playlistId);

  if (!playlist) {
    notFound();
  }

  return <PlaylistDetailView playlist={playlist} />;
}
