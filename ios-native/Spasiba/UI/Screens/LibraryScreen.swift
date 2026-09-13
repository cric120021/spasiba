import SwiftUI

public struct LibraryScreen: View {
    @ObservedObject var repository = MusicRepository.shared
    @ObservedObject var player = NativePlayerManager.shared
    @ObservedObject var downloadManager = SpasibaDownloadManager.shared

    @State private var selectedTab: Int = 0 // 0 = Playlists, 1 = Liked, 2 = History, 3 = Downloads
    @State private var activePlaylistDetail: PlaylistEntity? = nil
    @State private var selectedPlaylistForOptions: PlaylistEntity? = nil
    @State private var isCreatePlaylistOpen: Bool = false
    @State private var newPlaylistName: String = ""
    @State private var isImportDialogOpen: Bool = false
    @State private var importInput: String = ""
    @State private var songToDeleteFromOffline: Song? = nil
    @State private var songToRemoveFromHistory: Song? = nil

    public init(initialTab: Int = 0) {
        _selectedTab = State(initialValue: initialTab)
    }

    private var screenWidth: CGFloat {
        UIScreen.main.bounds.width
    }

    private var currentSubtitle: String {
        switch selectedTab {
        case 0: return "Your custom & shared playlists"
        case 1: return "Your favorite & liked songs"
        case 2: return "Recent listening history"
        case 3: return "Downloaded tracks & offline audio cache"
        default: return "Your Music Library"
        }
    }

    public var body: some View {
        ZStack {
            Color(hex: 0x0D0D0F).ignoresSafeArea()

            if let playlist = activePlaylistDetail {
                playlistDetailView(playlist: playlist)
            } else {
                mainLibraryView
            }
        }
        .frame(width: screenWidth)
        .clipped()
        .sheet(item: $selectedPlaylistForOptions) { playlist in
            playlistOptionsSheet(playlist: playlist)
        }
        .sheet(isPresented: $isCreatePlaylistOpen) {
            createPlaylistSheet
        }
        .sheet(isPresented: $isImportDialogOpen) {
            importPlaylistSheet
        }
        .alert(item: $songToDeleteFromOffline) { song in
            Alert(
                title: Text("Delete Download"),
                message: Text("Are you sure you want to remove '\(song.title)' from offline storage?"),
                primaryButton: .destructive(Text("Delete")) {
                    downloadManager.deleteDownload(videoId: song.id)
                },
                secondaryButton: .cancel()
            )
        }
    }

    // MARK: - Main Library View
    private var mainLibraryView: some View {
        VStack(spacing: 0) {
            // Header matching YouTube Music design
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("LIBRARY")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(Color(hex: 0x1DB954))
                        .tracking(2.4)

                    Text("Spasiba")
                        .font(.system(size: 28, weight: .black))
                        .foregroundStyle(SpasibaTheme.silverGradient)

                    Text(currentSubtitle)
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: 0xB3B3B3))
                        .lineLimit(1)
                        .truncationMode(.tail)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                HStack(spacing: 2) {
                    // 1. Playlists Tab
                    Button(action: { selectedTab = 0 }) {
                        Image(systemName: "music.note.list")
                            .font(.system(size: 18))
                            .foregroundColor(selectedTab == 0 ? Color(hex: 0x1DB954) : Color.white.opacity(0.6))
                            .frame(width: 36, height: 36)
                    }

                    // 2. Liked Songs Tab
                    Button(action: { selectedTab = 1 }) {
                        Image(systemName: "heart.fill")
                            .font(.system(size: 18))
                            .foregroundColor(selectedTab == 1 ? Color(hex: 0xEF4444) : Color.white.opacity(0.6))
                            .frame(width: 36, height: 36)
                    }

                    // 3. History Tab
                    Button(action: { selectedTab = 2 }) {
                        Image(systemName: "clock.arrow.circlepath")
                            .font(.system(size: 18))
                            .foregroundColor(selectedTab == 2 ? Color(hex: 0x1DB954) : Color.white.opacity(0.6))
                            .frame(width: 36, height: 36)
                    }

                    // 4. Downloaded Songs Tab
                    Button(action: { selectedTab = 3 }) {
                        Image(systemName: "arrow.down.circle")
                            .font(.system(size: 18))
                            .foregroundColor(selectedTab == 3 ? Color(hex: 0x1DB954) : Color.white.opacity(0.6))
                            .frame(width: 36, height: 36)
                    }
                }
                .fixedSize()
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 6)
            .frame(width: screenWidth)

            // Content
            TabView(selection: $selectedTab) {
                playlistsTab.frame(width: screenWidth).tag(0)
                likedSongsTab.frame(width: screenWidth).tag(1)
                historyTab.frame(width: screenWidth).tag(2)
                downloadsTab.frame(width: screenWidth).tag(3)
            }
            .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
            .frame(width: screenWidth)
            .clipped()
        }
        .frame(width: screenWidth)
        .clipped()
    }

    // MARK: - Tab 0: Playlists
    private var playlistsTab: some View {
        ZStack(alignment: .bottomTrailing) {
            ScrollView {
                LazyVStack(spacing: 10) {
                    // Import Playlist Card
                    Button(action: { isImportDialogOpen = true }) {
                        HStack(spacing: 16) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Color(hex: 0x6366F1))
                                    .frame(width: 48, height: 48)
                                Image(systemName: "arrow.down.circle")
                                    .font(.system(size: 24))
                                    .foregroundColor(.white)
                            }

                            VStack(alignment: .leading, spacing: 3) {
                                Text("Import Playlist")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                                    .lineLimit(1)
                                    .truncationMode(.tail)
                                Text("Paste a Spasiba share link or code")
                                    .font(.system(size: 13))
                                    .foregroundColor(Color.white.opacity(0.6))
                                    .lineLimit(1)
                                    .truncationMode(.tail)
                            }

                            Spacer()
                        }
                        .padding(16)
                        .background(Color(hex: 0x6366F1).opacity(0.14))
                        .cornerRadius(16)
                    }
                    .buttonStyle(PlainButtonStyle())
                    .frame(width: screenWidth - 32)
                    .padding(.horizontal, 16)

                    if repository.playlists.isEmpty {
                        Text("No custom playlists created yet.")
                            .font(.system(size: 14))
                            .foregroundColor(Color.white.opacity(0.6))
                            .padding(.top, 40)
                    } else {
                        ForEach(repository.playlists) { pl in
                            playlistCard(playlist: pl)
                        }
                    }
                }
                .frame(width: screenWidth)
                .padding(.bottom, 140)
            }
            .frame(width: screenWidth)
            .clipped()

            // Floating Action Button
            Button(action: { isCreatePlaylistOpen = true }) {
                Image(systemName: "plus")
                    .font(.system(size: 26, weight: .bold))
                    .foregroundColor(.black)
                    .frame(width: 56, height: 56)
                    .background(Color(hex: 0x1DB954))
                    .clipShape(Circle())
                    .shadow(color: Color(hex: 0x1DB954).opacity(0.3), radius: 8, x: 0, y: 4)
            }
            .padding(.trailing, 20)
            .padding(.bottom, 100)
        }
        .frame(width: screenWidth)
        .clipped()
    }

    private func playlistCard(playlist: PlaylistEntity) -> some View {
        let pSongs = repository.getSongsForPlaylist(playlistId: playlist.id)
        let coverUrl = pSongs.first(where: { !$0.thumbnail.isEmpty })?.thumbnail

        return HStack(spacing: 16) {
            ZStack {
                if let url = coverUrl, let validUrl = URL(string: url) {
                    AsyncImage(url: validUrl) { phase in
                        if let img = phase.image {
                            img.resizable().aspectRatio(contentMode: .fill)
                        } else {
                            Color(hex: 0x262626)
                        }
                    }
                } else {
                    LinearGradient(
                        colors: [Color(hex: 0x6366F1), Color(hex: 0xA855F7), Color(hex: 0xEC4899)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    Text(String(playlist.name.prefix(1)).uppercased())
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(.white)
                }
            }
            .frame(width: 64, height: 64)
            .clipShape(RoundedRectangle(cornerRadius: 14))

            VStack(alignment: .leading, spacing: 4) {
                Text(playlist.name)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .truncationMode(.tail)

                Text("Custom User Playlist • \(pSongs.count) tracks")
                    .font(.system(size: 13))
                    .foregroundColor(Color.white.opacity(0.6))
                    .lineLimit(1)
                    .truncationMode(.tail)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Button(action: { selectedPlaylistForOptions = playlist }) {
                Image(systemName: "ellipsis")
                    .font(.system(size: 16))
                    .foregroundColor(Color.white.opacity(0.6))
                    .rotationEffect(.degrees(90))
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(PlainButtonStyle())
        }
        .padding(14)
        .frame(width: screenWidth - 32)
        .background(Color.white.opacity(0.06))
        .cornerRadius(18)
        .padding(.horizontal, 16)
        .contentShape(Rectangle())
        .onTapGesture {
            activePlaylistDetail = playlist
        }
    }

    // MARK: - Tab 1: Liked Songs
    private var likedSongsTab: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                // Liked Songs Hero Banner
                VStack(spacing: 14) {
                    let likedCover = repository.likedSongs.first(where: { !$0.thumbnail.isEmpty })?.thumbnail
                    ZStack {
                        if let url = likedCover, let validUrl = URL(string: url) {
                            AsyncImage(url: validUrl) { phase in
                                if let img = phase.image {
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } else {
                                    Color(hex: 0x222222)
                                }
                            }
                        } else {
                            LinearGradient(
                                colors: [Color(hex: 0xEF4444), Color(hex: 0x8B5CF6), Color(hex: 0xEC4899)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                            Image(systemName: "heart.fill")
                                .font(.system(size: 64))
                                .foregroundColor(.white)
                        }
                    }
                    .frame(width: 160, height: 160)
                    .clipShape(RoundedRectangle(cornerRadius: 20))

                    Text("Liked Songs")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .truncationMode(.tail)

                    Text("Auto playlist • \(repository.likedSongs.count) songs")
                        .font(.system(size: 14))
                        .foregroundColor(Color.white.opacity(0.6))
                        .lineLimit(1)
                        .truncationMode(.tail)

                    if !repository.likedSongs.isEmpty {
                        HStack(spacing: 16) {
                            Button(action: {
                                if !repository.likedSongs.isEmpty {
                                    player.playQueue(repository.likedSongs, startIndex: 0)
                                }
                            }) {
                                Image(systemName: "play.fill")
                                    .font(.system(size: 24))
                                    .foregroundColor(.black)
                                    .frame(width: 52, height: 52)
                                    .background(Color.white)
                                    .clipShape(Circle())
                            }

                            Button(action: {
                                let shuffled = repository.likedSongs.shuffled()
                                if !shuffled.isEmpty {
                                    player.playQueue(shuffled, startIndex: 0)
                                }
                            }) {
                                Image(systemName: "shuffle")
                                    .font(.system(size: 20))
                                    .foregroundColor(.white)
                                    .frame(width: 44, height: 44)
                                    .background(Color(hex: 0x26262A))
                                    .clipShape(Circle())
                            }
                        }
                    }
                }
                .padding(.vertical, 16)
                .frame(width: screenWidth)

                if repository.likedSongs.isEmpty {
                    Text("No liked tracks yet. Heart songs to save them here!")
                        .font(.system(size: 14))
                        .foregroundColor(Color.white.opacity(0.6))
                        .padding(.top, 40)
                } else {
                    ForEach(Array(repository.likedSongs.enumerated()), id: \.element.id) { index, song in
                        SongTile(
                            song: song,
                            isPlaying: song.id == player.currentSong?.id,
                            isLiked: true,
                            enableSwipe: false,
                            onSongClick: {
                                player.playQueue(repository.likedSongs, startIndex: index)
                            },
                            onLikeClick: { player.toggleLike(song) },
                            onOptionsClick: {}
                        )
                        .frame(width: screenWidth - 32)
                        .padding(.horizontal, 16)
                    }
                }
            }
            .frame(width: screenWidth)
            .padding(.bottom, 140)
        }
        .frame(width: screenWidth)
        .clipped()
    }

    // MARK: - Tab 2: History
    private var historyTab: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                if repository.listeningHistory.isEmpty {
                    Text("No recent listening history.")
                        .font(.system(size: 14))
                        .foregroundColor(Color.white.opacity(0.6))
                        .padding(.top, 40)
                } else {
                    ForEach(Array(repository.listeningHistory.enumerated()), id: \.element.id) { index, song in
                        SongTile(
                            song: song,
                            isPlaying: song.id == player.currentSong?.id,
                            isLiked: repository.likedSongs.contains(where: { $0.id == song.id }),
                            enableSwipe: false,
                            onSongClick: {
                                player.playQueue(repository.listeningHistory, startIndex: index)
                            },
                            onLikeClick: { player.toggleLike(song) },
                            onOptionsClick: {}
                        )
                        .frame(width: screenWidth - 32)
                        .padding(.horizontal, 16)
                    }
                }
            }
            .frame(width: screenWidth)
            .padding(.bottom, 140)
        }
        .frame(width: screenWidth)
        .clipped()
    }

    // MARK: - Tab 3: Downloads
    private var downloadsTab: some View {
        let downloadedList = downloadManager.getDownloadedSongs()
        let activeList = downloadManager.downloads.values.filter { $0.status != .completed }

        return ScrollView {
            LazyVStack(spacing: 0) {
                // Header Hero
                VStack(spacing: 12) {
                    Text("Playlist • \(downloadedList.count) \(downloadedList.count == 1 ? "song" : "songs")")
                        .font(.system(size: 13))
                        .foregroundColor(Color.white.opacity(0.6))
                        .lineLimit(1)
                        .truncationMode(.tail)

                    ZStack {
                        LinearGradient(
                            colors: [Color(hex: 0x6366F1), Color(hex: 0x3B82F6)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 70))
                            .foregroundColor(.white)
                    }
                    .frame(width: 180, height: 180)
                    .clipShape(RoundedRectangle(cornerRadius: 20))

                    Text("Downloaded songs")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .truncationMode(.tail)

                    HStack(spacing: 16) {
                        Image(systemName: "checkmark.circle")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                            .background(Color.white.opacity(0.12))
                            .clipShape(Circle())

                        if !downloadedList.isEmpty {
                            Button(action: {
                                player.playQueue(downloadedList, startIndex: 0)
                            }) {
                                Image(systemName: "play.fill")
                                    .font(.system(size: 26))
                                    .foregroundColor(.black)
                                    .frame(width: 56, height: 56)
                                    .background(Color.white)
                                    .clipShape(Circle())
                            }
                        }

                        Image(systemName: "ellipsis")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                            .rotationEffect(.degrees(90))
                            .frame(width: 44, height: 44)
                            .background(Color.white.opacity(0.12))
                            .clipShape(Circle())
                    }
                }
                .padding(.vertical, 16)
                .frame(width: screenWidth)

                // Active Downloads
                if !activeList.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Active Downloads")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(hex: 0x1DB954))
                            .padding(.horizontal, 16)

                        ForEach(Array(activeList), id: \.song.id) { item in
                            VStack(spacing: 6) {
                                HStack(spacing: 12) {
                                    SongArtworkView(song: item.song, cornerRadius: 8)
                                        .frame(width: 44, height: 44)

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(item.song.title)
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(.white)
                                            .lineLimit(1)
                                            .truncationMode(.tail)
                                        Text("\(item.type.rawValue) • \(item.quality) • \(Int(item.progress))% (\(item.status.rawValue))")
                                            .font(.system(size: 11))
                                            .foregroundColor(Color.white.opacity(0.6))
                                            .lineLimit(1)
                                            .truncationMode(.tail)
                                    }

                                    Spacer()

                                    Button(action: { downloadManager.cancelDownload(songId: item.song.id) }) {
                                        Image(systemName: "trash")
                                            .font(.system(size: 16))
                                            .foregroundColor(Color(hex: 0xEF4444))
                                    }
                                }

                                if item.status == .downloading {
                                    ProgressView(value: Double(item.progress), total: 100.0)
                                        .progressViewStyle(LinearProgressViewStyle(tint: Color(hex: 0x1DB954)))
                                }
                            }
                            .padding(12)
                            .frame(width: screenWidth - 32)
                            .background(Color.white.opacity(0.06))
                            .cornerRadius(12)
                            .padding(.horizontal, 16)
                        }
                    }
                    .padding(.bottom, 12)
                }

                // Downloaded songs list
                if downloadedList.isEmpty {
                    Text("No downloaded tracks yet. Use Download on any track to listen offline.")
                        .font(.system(size: 14))
                        .foregroundColor(Color.white.opacity(0.6))
                        .padding(.top, 40)
                } else {
                    ForEach(Array(downloadedList.enumerated()), id: \.element.id) { index, song in
                        SongTile(
                            song: song,
                            isPlaying: song.id == player.currentSong?.id,
                            isLiked: repository.likedSongs.contains(where: { $0.id == song.id }),
                            isDownloaded: true,
                            enableSwipe: false,
                            onSongClick: {
                                player.playQueue(downloadedList, startIndex: index)
                            },
                            onLikeClick: { player.toggleLike(song) },
                            onOptionsClick: {},
                            onLongClick: {
                                songToDeleteFromOffline = song
                            }
                        )
                        .frame(width: screenWidth - 32)
                        .padding(.horizontal, 16)
                    }
                }
            }
            .frame(width: screenWidth)
            .padding(.bottom, 140)
        }
        .frame(width: screenWidth)
        .clipped()
    }

    // MARK: - Playlist Detail View
    private func playlistDetailView(playlist: PlaylistEntity) -> some View {
        let pSongs = repository.getSongsForPlaylist(playlistId: playlist.id)
        let coverUrl = pSongs.first(where: { !$0.thumbnail.isEmpty })?.thumbnail

        return VStack(spacing: 0) {
            // Navigation Bar
            HStack {
                Button(action: { activePlaylistDetail = nil }) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .frame(width: 36, height: 36)
                }

                Text("Playlist")
                    .font(.system(size: 14))
                    .foregroundColor(Color.white.opacity(0.6))

                Spacer()

                Button(action: {
                    ShareUtils.sharePlaylist(playlistName: playlist.name, songs: pSongs, shareCode: playlist.shareCode)
                }) {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 18))
                        .foregroundColor(.white)
                        .frame(width: 36, height: 36)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .frame(width: screenWidth)

            ScrollView {
                LazyVStack(spacing: 0) {
                    // Hero Banner
                    VStack(spacing: 16) {
                        ZStack {
                            if let url = coverUrl, let validUrl = URL(string: url) {
                                AsyncImage(url: validUrl) { phase in
                                    if let img = phase.image {
                                        img.resizable().aspectRatio(contentMode: .fill)
                                    } else {
                                        Color(hex: 0x262626)
                                    }
                                }
                            } else {
                                LinearGradient(
                                    colors: [Color(hex: 0x6366F1), Color(hex: 0xA855F7), Color(hex: 0xEC4899)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                                Image(systemName: "music.note.list")
                                    .font(.system(size: 64))
                                    .foregroundColor(.white)
                            }
                        }
                        .frame(width: 180, height: 180)
                        .clipShape(RoundedRectangle(cornerRadius: 20))

                        Text(playlist.name)
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .truncationMode(.tail)

                        Text("Playlist • \(pSongs.count) songs")
                            .font(.system(size: 14))
                            .foregroundColor(Color.white.opacity(0.6))
                            .lineLimit(1)
                            .truncationMode(.tail)

                        if !pSongs.isEmpty {
                            HStack(spacing: 16) {
                                Button(action: {
                                    player.playQueue(pSongs, startIndex: 0)
                                }) {
                                    Image(systemName: "play.fill")
                                        .font(.system(size: 26))
                                        .foregroundColor(.black)
                                        .frame(width: 56, height: 56)
                                        .background(Color.white)
                                        .clipShape(Circle())
                                }

                                Button(action: {
                                    let shuffled = pSongs.shuffled()
                                    if !shuffled.isEmpty {
                                        player.playQueue(shuffled, startIndex: 0)
                                    }
                                }) {
                                    Image(systemName: "shuffle")
                                        .font(.system(size: 20))
                                        .foregroundColor(.white)
                                        .frame(width: 44, height: 44)
                                        .background(Color(hex: 0x26262A))
                                        .clipShape(Circle())
                                }
                            }
                        }
                    }
                    .padding(.vertical, 16)
                    .frame(width: screenWidth)

                    if pSongs.isEmpty {
                        Text("No songs in this playlist yet.")
                            .font(.system(size: 14))
                            .foregroundColor(Color.white.opacity(0.6))
                            .padding(.top, 40)
                    } else {
                        ForEach(Array(pSongs.enumerated()), id: \.element.id) { index, song in
                            SongTile(
                                song: song,
                                isPlaying: song.id == player.currentSong?.id,
                                isLiked: repository.likedSongs.contains(where: { $0.id == song.id }),
                                enableSwipe: false,
                                onSongClick: {
                                    player.playQueue(pSongs, startIndex: index)
                                },
                                onLikeClick: { player.toggleLike(song) },
                                onOptionsClick: {}
                            )
                            .frame(width: screenWidth - 32)
                            .padding(.horizontal, 16)
                        }
                    }
                }
                .frame(width: screenWidth)
                .padding(.bottom, 140)
            }
            .frame(width: screenWidth)
            .clipped()
        }
        .frame(width: screenWidth)
        .clipped()
    }

    // MARK: - Dialogs & Sheets
    private var createPlaylistSheet: some View {
        VStack(spacing: 20) {
            Text("Create Playlist")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(SpasibaTheme.textStrong)

            ZStack(alignment: .leading) {
                if newPlaylistName.isEmpty {
                    Text("Playlist Name")
                        .foregroundColor(SpasibaTheme.textMuted)
                }
                TextField("", text: $newPlaylistName)
                    .textFieldStyle(PlainTextFieldStyle())
                    .foregroundColor(SpasibaTheme.textStrong)
                    .accentColor(.white)
            }
            .padding()
            .background(Color.white.opacity(0.08))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.white.opacity(0.2), lineWidth: 1)
            )

            HStack(spacing: 12) {
                Button("Cancel") {
                    isCreatePlaylistOpen = false
                    newPlaylistName = ""
                }
                .foregroundColor(SpasibaTheme.textMuted)
                .frame(maxWidth: .infinity)
                .padding()

                Button("Create") {
                    if !newPlaylistName.trimmingCharacters(in: .whitespaces).isEmpty {
                        Task { _ = await repository.createPlaylist(name: newPlaylistName.trimmingCharacters(in: .whitespaces)) }
                        newPlaylistName = ""
                        isCreatePlaylistOpen = false
                    }
                }
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.white)
                .cornerRadius(12)
            }

            Spacer()
        }
        .padding(24)
        .background(SpasibaTheme.surfaceElevated.ignoresSafeArea())
    }

    private var importPlaylistSheet: some View {
        VStack(spacing: 20) {
            Text("Import Shared Playlist")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(SpasibaTheme.textStrong)

            ZStack(alignment: .leading) {
                if importInput.isEmpty {
                    Text("Share Code / Deep Link / Title")
                        .foregroundColor(SpasibaTheme.textMuted)
                }
                TextField("", text: $importInput)
                    .textFieldStyle(PlainTextFieldStyle())
                    .foregroundColor(SpasibaTheme.textStrong)
                    .accentColor(.white)
            }
            .padding()
            .background(Color.white.opacity(0.08))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.white.opacity(0.2), lineWidth: 1)
            )

            HStack(spacing: 12) {
                Button("Cancel") {
                    isImportDialogOpen = false
                    importInput = ""
                }
                .foregroundColor(SpasibaTheme.textMuted)
                .frame(maxWidth: .infinity)
                .padding()

                Button("Import") {
                    let trimmed = importInput.trimmingCharacters(in: .whitespaces)
                    if !trimmed.isEmpty {
                        if let parsed = ShareUtils.parsePlaylistShareCode(trimmed), !parsed.songs.isEmpty {
                            Task { _ = await repository.importSharedPlaylist(name: parsed.name, songs: parsed.songs, shareCode: parsed.shareCode) }
                        } else {
                            Task { _ = await repository.createPlaylist(name: trimmed) }
                        }
                        importInput = ""
                        isImportDialogOpen = false
                    }
                }
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.white)
                .cornerRadius(12)
            }

            Spacer()
        }
        .padding(24)
        .background(SpasibaTheme.surfaceElevated.ignoresSafeArea())
    }

    private func playlistOptionsSheet(playlist: PlaylistEntity) -> some View {
        VStack(spacing: 8) {
            Text(playlist.name)
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)
                .padding(.top, 16)
                .padding(.bottom, 8)

            Divider().background(Color.white.opacity(0.1))

            Button(action: {
                let pSongs = repository.getSongsForPlaylist(playlistId: playlist.id)
                if !pSongs.isEmpty {
                    player.playQueue(pSongs, startIndex: 0)
                }
                selectedPlaylistForOptions = nil
            }) {
                HStack {
                    Image(systemName: "play.fill")
                    Text("Play")
                    Spacer()
                }
                .foregroundColor(.white)
                .padding(.vertical, 12)
                .padding(.horizontal, 20)
            }

            Button(action: {
                let pSongs = repository.getSongsForPlaylist(playlistId: playlist.id)
                ShareUtils.sharePlaylist(playlistName: playlist.name, songs: pSongs, shareCode: playlist.shareCode)
                selectedPlaylistForOptions = nil
            }) {
                HStack {
                    Image(systemName: "square.and.arrow.up")
                    Text("Share Playlist")
                    Spacer()
                }
                .foregroundColor(.white)
                .padding(.vertical, 12)
                .padding(.horizontal, 20)
            }

            Button(action: {
                Task { await repository.deletePlaylist(playlistId: playlist.id) }
                selectedPlaylistForOptions = nil
            }) {
                HStack {
                    Image(systemName: "trash")
                    Text("Delete Playlist")
                    Spacer()
                }
                .foregroundColor(Color(hex: 0xEF4444))
                .padding(.vertical, 12)
                .padding(.horizontal, 20)
            }

            Spacer()
        }
        .background(Color(hex: 0x121214).ignoresSafeArea())
    }
}
