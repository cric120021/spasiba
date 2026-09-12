import SwiftUI

public struct SaveToPlaylistSheet: View {
    public let song: Song
    public let playlists: [Playlist]
    public var onDismiss: () -> Void
    public var onSelectPlaylist: (Playlist) -> Void
    public var onCreateNewPlaylist: () -> Void

    public init(
        song: Song,
        playlists: [Playlist],
        onDismiss: @escaping () -> Void = {},
        onSelectPlaylist: @escaping (Playlist) -> Void = { _ in },
        onCreateNewPlaylist: @escaping () -> Void = {}
    ) {
        self.song = song
        self.playlists = playlists
        self.onDismiss = onDismiss
        self.onSelectPlaylist = onSelectPlaylist
        self.onCreateNewPlaylist = onCreateNewPlaylist
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Save to Playlist")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(SpasibaTheme.textStrong)
                .padding(.horizontal, 24)
                .padding(.vertical, 16)

            // Create New Playlist Row
            Button(action: {
                onDismiss()
                onCreateNewPlaylist()
            }) {
                HStack(spacing: 16) {
                    Image(systemName: "plus")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(SpasibaTheme.accent)
                        .frame(width: 26, height: 26)

                    Text("New Playlist")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(SpasibaTheme.accent)

                    Spacer()
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 14)
            }

            Divider().background(SpasibaTheme.border)

            // Playlists List
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(playlists) { playlist in
                        Button(action: {
                            onDismiss()
                            onSelectPlaylist(playlist)
                        }) {
                            HStack {
                                Text(playlist.name)
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundColor(SpasibaTheme.textStrong)

                                Spacer()

                                Text("\(playlist.songs.count) tracks")
                                    .font(.system(size: 12))
                                    .foregroundColor(SpasibaTheme.textMuted)
                            }
                            .padding(.horizontal, 24)
                            .padding(.vertical, 14)
                        }
                    }
                }
            }
            .frame(maxHeight: 300)

            Spacer().frame(height: 24)
        }
        .background(SpasibaTheme.surfaceElevated.ignoresSafeArea())
    }
}
