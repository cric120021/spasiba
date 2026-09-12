import SwiftUI

public struct PlaylistOperationsScreen: View {
    @ObservedObject var playerManager: NativePlayerManager = .shared
    @ObservedObject var database: SpasibaDatabase = .shared
    public var onBackClick: () -> Void

    @State private var activeSelectedPlaylist: Playlist? = nil
    @State private var isCreateDialogOpen: Bool = false
    @State private var createNameInput: String = ""

    @State private var isImportDialogOpen: Bool = false
    @State private var importCodeInput: String = ""

    @State private var isRenameDialogOpen: Bool = false
    @State private var renameTargetPlaylist: Playlist? = nil
    @State private var renameInput: String = ""

    @State private var playlistMenuTarget: Playlist? = nil
    @State private var isSyncing: Bool = false
    @State private var toastMessage: String? = nil

    public init(
        playerManager: NativePlayerManager = .shared,
        onBackClick: @escaping () -> Void = {}
    ) {
        self.playerManager = playerManager
        self.onBackClick = onBackClick
    }

    public var body: some View {
        ZStack {
            SpasibaTheme.background.ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                LazyVStack(spacing: 16) {
                    // 1. Navigation & Header
                    navigationHeaderSection

                    // 2. Quick Action Buttons (+ New Playlist, Import Code)
                    quickActionButtonsSection

                    // 3. Content Area: Detailed Songs View or Playlists Overview
                    if let pl = activeSelectedPlaylist {
                        playlistDetailsSection(playlist: pl)
                    } else {
                        playlistsOverviewSection
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 140)
            }

            // Toast overlay
            if let toast = toastMessage {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.85))
                        .clipShape(Capsule())
                        .padding(.bottom, 100)
                }
                .transition(.opacity)
            }

            // Dialogs
            if isCreateDialogOpen {
                createPlaylistDialogView
            }

            if isImportDialogOpen {
                importPlaylistDialogView
            }

            if isRenameDialogOpen, let target = renameTargetPlaylist {
                renamePlaylistDialogView(target: target)
            }
        }
    }

    // MARK: - Subviews

    private var navigationHeaderSection: some View {
        HStack(alignment: .center) {
            if activeSelectedPlaylist != nil {
                Button(action: { activeSelectedPlaylist = nil }) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .frame(width: 36, height: 36)
                        .background(Color.white.opacity(0.08))
                        .clipShape(Circle())
                }
            }

            SilverBrandHeader(
                subtitle: activeSelectedPlaylist != nil ? "Playlist: \(activeSelectedPlaylist!.name)" : "Manage & Share Playlists"
            )

            Button(action: {
                isSyncing = true
                database.forceSyncWithRemoteCloud()
                if let curId = activeSelectedPlaylist?.id {
                    activeSelectedPlaylist = database.playlists.first(where: { $0.id == curId })
                }
                isSyncing = false
                showToast("Database and playlists synced!")
            }) {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(SpasibaTheme.accent)
                    .frame(width: 38, height: 38)
                    .background(Color.white.opacity(0.08))
                    .clipShape(Circle())
                    .rotationEffect(.degrees(isSyncing ? 360 : 0))
                    .animation(isSyncing ? Animation.linear(duration: 1).repeatForever(autoreverses: false) : .default, value: isSyncing)
            }
        }
        .padding(.bottom, 6)
    }

    private var quickActionButtonsSection: some View {
        HStack(spacing: 10) {
            Button(action: {
                createNameInput = ""
                isCreateDialogOpen = true
            }) {
                HStack(spacing: 6) {
                    Image(systemName: "plus")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.black)
                    Text("New Playlist")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.black)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }

            Button(action: {
                importCodeInput = ""
                isImportDialogOpen = true
            }) {
                HStack(spacing: 6) {
                    Image(systemName: "arrow.down.circle")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                    Text("Import Playlist")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.white.opacity(0.15), lineWidth: 1)
                )
            }
        }
    }

    private func playlistDetailsSection(playlist: Playlist) -> some View {
        VStack(spacing: 16) {
            // Header card
            VStack(alignment: .leading, spacing: 6) {
                Text("PLAYLIST DETAILS")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(SpasibaTheme.accent)
                    .tracking(1.4)

                Text(playlist.name)
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(SpasibaTheme.textStrong)

                Text("\(playlist.songs.count) Tracks • Share Code: \(playlist.shareCode ?? "SPASIBA-LOCAL")")
                    .font(.system(size: 12))
                    .foregroundColor(SpasibaTheme.textMuted)

                Spacer().frame(height: 8)

                HStack(spacing: 10) {
                    Button(action: {
                        if !playlist.songs.isEmpty {
                            playerManager.playQueue(playlist.songs, startIndex: 0, sourceLabel: "Playlist: \(playlist.name)")
                        } else {
                            showToast("Playlist is empty")
                        }
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "play.fill")
                                .font(.system(size: 14))
                                .foregroundColor(.black)
                            Text("Play All")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.black)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 9)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }

                    Button(action: {
                        if !playlist.songs.isEmpty {
                            playerManager.playQueue(playlist.songs.shuffled(), startIndex: 0, sourceLabel: "Playlist Shuffle: \(playlist.name)")
                        } else {
                            showToast("Playlist is empty")
                        }
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "shuffle")
                                .font(.system(size: 14))
                                .foregroundColor(.white)
                            Text("Shuffle")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 9)
                        .background(Color.white.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }

                    Button(action: {
                        ShareUtils.sharePlaylist(playlistName: playlist.name, songs: playlist.songs, shareCode: playlist.shareCode)
                    }) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 16))
                            .foregroundColor(.white)
                            .frame(width: 40, height: 40)
                            .background(Color.white.opacity(0.1))
                            .clipShape(Circle())
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .background(Color.white.opacity(0.04))
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .overlay(
                RoundedRectangle(cornerRadius: 24)
                    .stroke(Color.white.opacity(0.1), lineWidth: 1)
            )

            // Songs List
            if playlist.songs.isEmpty {
                VStack(spacing: 8) {
                    Text("No tracks in this playlist yet. Add songs using track options in Search or Home!")
                        .font(.system(size: 13))
                        .foregroundColor(SpasibaTheme.textMuted)
                        .multilineTextAlignment(.center)
                        .padding(.vertical, 32)
                }
            } else {
                VStack(spacing: 4) {
                    ForEach(Array(playlist.songs.enumerated()), id: \.element.id) { index, song in
                        HStack(spacing: 0) {
                            SongTile(
                                song: song,
                                isPlaying: playerManager.currentSong?.id == song.id,
                                isLiked: database.isLiked(songId: song.id),
                                onSongClick: {
                                    playerManager.playQueue(playlist.songs, startIndex: index, sourceLabel: "Playlist: \(playlist.name)")
                                },
                                onLikeClick: {
                                    database.toggleLike(song: song)
                                },
                                onOptionsClick: {}
                            )

                            Button(action: {
                                database.removeSongFromPlaylist(playlistId: playlist.id, songId: song.id)
                                if let updated = database.playlists.first(where: { $0.id == playlist.id }) {
                                    activeSelectedPlaylist = updated
                                }
                                showToast("Removed from playlist")
                            }) {
                                Image(systemName: "xmark")
                                    .font(.system(size: 14))
                                    .foregroundColor(SpasibaTheme.textMuted)
                                    .frame(width: 36, height: 36)
                            }
                        }
                    }
                }
            }
        }
    }

    private var playlistsOverviewSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("MY PLAYLISTS & COLLECTIONS (\(database.playlists.count))")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(SpasibaTheme.textMuted)
                .tracking(1.4)

            if database.playlists.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "music.note.list")
                        .font(.system(size: 48))
                        .foregroundColor(SpasibaTheme.textMuted)
                    Text("No playlists found")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(SpasibaTheme.textStrong)
                    Text("Create a custom playlist or import one via share code to get started.")
                        .font(.system(size: 12))
                        .foregroundColor(SpasibaTheme.textMuted)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(32)
                .background(Color.white.opacity(0.03))
                .clipShape(RoundedRectangle(cornerRadius: 20))
            } else {
                VStack(spacing: 10) {
                    ForEach(database.playlists) { pl in
                        playlistCardItem(playlist: pl)
                    }
                }
            }
        }
    }

    private func playlistCardItem(playlist: Playlist) -> some View {
        Button(action: { activeSelectedPlaylist = playlist }) {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14)
                        .fill(Color(hex: 0x6366F1))
                        .frame(width: 52, height: 52)
                    Image(systemName: "music.note.list")
                        .font(.system(size: 24))
                        .foregroundColor(.white)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text(playlist.name)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(SpasibaTheme.textStrong)

                    Text(playlist.isShared ? "Shared • Code: \(playlist.shareCode ?? "")" : "Custom Room Playlist")
                        .font(.system(size: 12))
                        .foregroundColor(SpasibaTheme.textMuted)
                }

                Spacer()

                Menu {
                    Button(action: { activeSelectedPlaylist = playlist }) {
                        Label("Open Playlist", systemImage: "music.note.list")
                    }
                    Button(action: {
                        ShareUtils.sharePlaylist(playlistName: playlist.name, songs: playlist.songs, shareCode: playlist.shareCode)
                    }) {
                        Label("Share Playlist", systemImage: "square.and.arrow.up")
                    }
                    Button(action: {
                        renameTargetPlaylist = playlist
                        renameInput = playlist.name
                        isRenameDialogOpen = true
                    }) {
                        Label("Rename", systemImage: "pencil")
                    }
                    Button(role: .destructive, action: {
                        database.deletePlaylist(playlistId: playlist.id)
                        showToast("Playlist deleted")
                    }) {
                        Label("Delete", systemImage: "trash")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 16))
                        .foregroundColor(SpasibaTheme.textMuted)
                        .rotationEffect(.degrees(90))
                        .frame(width: 36, height: 36)
                }
            }
            .padding(14)
            .background(SpasibaTheme.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: 18))
        }
        .buttonStyle(PlainButtonStyle())
    }

    // MARK: - Dialogs

    private var createPlaylistDialogView: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 16) {
                Text("Create New Playlist")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)

                ZStack(alignment: .leading) {
                    if createNameInput.isEmpty {
                        Text("Playlist Name")
                            .foregroundColor(SpasibaTheme.textMuted)
                    }
                    TextField("", text: $createNameInput)
                        .foregroundColor(SpasibaTheme.textStrong)
                        .accentColor(.white)
                }
                .padding(12)
                .background(Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                )

                HStack {
                    Spacer()
                    Button("Cancel") {
                        isCreateDialogOpen = false
                    }
                    .foregroundColor(SpasibaTheme.textMuted)
                    .padding(.horizontal, 14)

                    Button("Create") {
                        let trimmed = createNameInput.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !trimmed.isEmpty {
                            database.createPlaylist(name: trimmed)
                            isCreateDialogOpen = false
                            showToast("Playlist '\(trimmed)' created!")
                        }
                    }
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.black)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
            .padding(20)
            .background(SpasibaTheme.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
            )
            .padding(.horizontal, 32)
        }
    }

    private var importPlaylistDialogView: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 14) {
                Text("Import Shared Playlist")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)

                Text("Paste a playlist code, deep link, or payload to sync it to your local database.")
                    .font(.system(size: 12))
                    .foregroundColor(SpasibaTheme.textMuted)

                ZStack(alignment: .leading) {
                    if importCodeInput.isEmpty {
                        Text("Share Code / Link")
                            .foregroundColor(SpasibaTheme.textMuted)
                    }
                    TextField("", text: $importCodeInput)
                        .foregroundColor(SpasibaTheme.textStrong)
                        .accentColor(.white)
                }
                .padding(12)
                .background(Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                )

                HStack {
                    Spacer()
                    Button("Cancel") {
                        isImportDialogOpen = false
                    }
                    .foregroundColor(SpasibaTheme.textMuted)
                    .padding(.horizontal, 14)

                    Button("Import & Sync") {
                        let trimmed = importCodeInput.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !trimmed.isEmpty {
                            if let parsed = ShareUtils.parsePlaylistShareCode(trimmed) {
                                database.importSharedPlaylist(name: parsed.name, songs: parsed.songs, shareCode: parsed.shareCode)
                                isImportDialogOpen = false
                                showToast("Imported playlist '\(parsed.name)' (\(parsed.songs.count) tracks)")
                            } else {
                                showToast("Invalid share code or payload format")
                            }
                        }
                    }
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.black)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
            .padding(20)
            .background(SpasibaTheme.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
            )
            .padding(.horizontal, 32)
        }
    }

    private func renamePlaylistDialogView(target: Playlist) -> some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 16) {
                Text("Rename Playlist")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)

                ZStack(alignment: .leading) {
                    if renameInput.isEmpty {
                        Text("New Name")
                            .foregroundColor(SpasibaTheme.textMuted)
                    }
                    TextField("", text: $renameInput)
                        .foregroundColor(SpasibaTheme.textStrong)
                        .accentColor(.white)
                }
                .padding(12)
                .background(Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                )

                HStack {
                    Spacer()
                    Button("Cancel") {
                        isRenameDialogOpen = false
                    }
                    .foregroundColor(SpasibaTheme.textMuted)
                    .padding(.horizontal, 14)

                    Button("Save") {
                        let trimmed = renameInput.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !trimmed.isEmpty {
                            database.updatePlaylistName(playlistId: target.id, newName: trimmed)
                            if activeSelectedPlaylist?.id == target.id {
                                activeSelectedPlaylist?.name = trimmed
                            }
                            isRenameDialogOpen = false
                            showToast("Renamed to '\(trimmed)'")
                        }
                    }
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.black)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
            .padding(20)
            .background(SpasibaTheme.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
            )
            .padding(.horizontal, 32)
        }
    }

    private func showToast(_ msg: String) {
        toastMessage = msg
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            if toastMessage == msg {
                toastMessage = nil
            }
        }
    }
}
