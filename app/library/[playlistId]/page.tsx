import LibraryPlaylistPageClient from "@/components/LibraryPlaylistPageClient";

export default async function LibraryPlaylistPage({ params }: { params: Promise<{ playlistId: string }> }) {
  const { playlistId } = await params;

  return <LibraryPlaylistPageClient playlistId={playlistId} />;
}
