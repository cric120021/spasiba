import SwiftUI

public struct CategoryFeedScreen: View {
    public let category: CategoryChip
    public var categoryFeedSongs: [Song] = []
    public var currentSong: Song? = nil
    public var likedSongs: Set<String> = []
    public var isLoading: Bool = false
    public var onBackClick: () -> Void = {}
    public var onSongClick: (Song, [Song], Int) -> Void = { _, _, _ in }
    public var onLikeClick: (String) -> Void = { _ in }
    public var onOptionsClick: (Song) -> Void = { _ in }
    public var onSwipeToQueue: (Song) -> Void = { _ in }

    @ObservedObject private var player = NativePlayerManager.shared
    @ObservedObject private var db = SpasibaDatabase.shared
    @State private var internalSongs: [Song] = []
    @State private var internalLoading: Bool = false

    public init(
        category: CategoryChip,
        categoryFeedSongs: [Song] = [],
        currentSong: Song? = nil,
        likedSongs: Set<String> = [],
        isLoading: Bool = false,
        onBackClick: @escaping () -> Void = {},
        onSongClick: @escaping (Song, [Song], Int) -> Void = { _, _, _ in },
        onLikeClick: @escaping (String) -> Void = { _ in },
        onOptionsClick: @escaping (Song) -> Void = { _ in },
        onSwipeToQueue: @escaping (Song) -> Void = { _ in }
    ) {
        self.category = category
        self.categoryFeedSongs = categoryFeedSongs
        self.currentSong = currentSong
        self.likedSongs = likedSongs
        self.isLoading = isLoading
        self.onBackClick = onBackClick
        self.onSongClick = onSongClick
        self.onLikeClick = onLikeClick
        self.onOptionsClick = onOptionsClick
        self.onSwipeToQueue = onSwipeToQueue
    }

    // Convenience initializer for chip only
    public init(chip: CategoryChip) {
        self.init(category: chip)
    }

    private var activeSongs: [Song] {
        categoryFeedSongs.isEmpty ? internalSongs : categoryFeedSongs
    }

    private var effectiveLoading: Bool {
        isLoading || (categoryFeedSongs.isEmpty && internalLoading && internalSongs.isEmpty)
    }

    private var activeCurrentSong: Song? {
        currentSong ?? player.currentSong
    }

    private var activeLikedSongs: Set<String> {
        likedSongs.isEmpty ? player.likedSongIds : likedSongs
    }

    private var accentColor: Color {
        SpasibaTheme.categoryAccentColor(for: category.id)
    }

    private var quickPicks: [Song] {
        Array(activeSongs.prefix(8))
    }

    private var quickPickColumns: [[Song]] {
        quickPicks.chunked(into: 4)
    }

    private var mixedTracks: [Song] {
        Array(activeSongs.dropFirst(4).prefix(6))
    }

    public var body: some View {
        ZStack {
            SpasibaTheme.background.ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                LazyVStack(alignment: .leading, spacing: 0) {
                    // 1. Category Hero Banner Header (matching CategoryFeedPage.tsx & CategoryFeedScreen.kt)
                    heroBannerHeaderSection

                    // 2. Category Quick Picks (2-Column Grid Layout, 4 items per column)
                    if !quickPickColumns.isEmpty {
                        quickPicksSection
                    }

                    // 3. Category Mixed Cards Section
                    if !effectiveLoading && !mixedTracks.isEmpty {
                        mixesSection
                    }

                    // 4. All Category Songs List Section
                    allTracksHeaderSection

                    if effectiveLoading {
                        ForEach(0..<6, id: \.self) { _ in
                            SongTileSkeleton()
                        }
                    } else {
                        ForEach(Array(activeSongs.enumerated()), id: \.element.id) { index, song in
                            SongTile(
                                song: song,
                                isPlaying: song.id == activeCurrentSong?.id && player.isPlaying,
                                isLiked: activeLikedSongs.contains(song.id),
                                onSongClick: {
                                    handleSongClick(song: song, index: index)
                                },
                                onLikeClick: {
                                    handleLikeClick(songId: song.id)
                                },
                                onOptionsClick: {
                                    onOptionsClick(song)
                                },
                                onSwipeToQueue: {
                                    handleSwipeToQueue(song: song)
                                }
                            )
                        }
                    }
                }
                .frame(maxWidth: 1280)
                .frame(maxWidth: .infinity)
                .padding(.bottom, 140)
            }
        }
        .task {
            if categoryFeedSongs.isEmpty && internalSongs.isEmpty {
                await fetchSongsForCategory()
            }
        }
    }

    // MARK: - 1. Category Hero Banner Header
    private var heroBannerHeaderSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Back Button Bar
            HStack {
                Button(action: onBackClick) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .frame(width: 36, height: 36)
                        .background(Color.white.opacity(0.08))
                        .clipShape(Circle())
                }
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)

            // Banner Title & Play All
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("SPASIBA CATEGORY")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(Color.white.opacity(0.7))
                        .tracking(2.2)

                    Text(category.label)
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.white)
                }

                Spacer()

                if !activeSongs.isEmpty {
                    Button(action: {
                        if let first = activeSongs.first {
                            handleSongClick(song: first, index: 0)
                        }
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "play.fill")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.black)

                            Text("PLAY")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.black)
                                .tracking(1.2)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color.white)
                        .clipShape(Capsule())
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 16)
        }
        .background(
            LinearGradient(
                colors: [
                    accentColor.opacity(0.45),
                    accentColor.opacity(0.15),
                    Color.clear
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }

    // MARK: - 2. Category Quick Picks (2-Column Grid Layout)
    private var quickPicksSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("\(category.label) Quick Picks")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.white)
                .padding(.horizontal, 16)
                .padding(.top, 4)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 12) {
                    ForEach(Array(quickPickColumns.enumerated()), id: \.offset) { _, columnTracks in
                        VStack(spacing: 10) {
                            ForEach(columnTracks) { track in
                                let isCurrentPlaying = track.id == activeCurrentSong?.id
                                HStack(spacing: 10) {
                                    SongArtworkView(song: track, cornerRadius: 12)
                                        .frame(width: 44, height: 44)

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(track.title)
                                            .font(.system(size: 13, weight: .semibold))
                                            .foregroundColor(.white)
                                            .lineLimit(1)

                                        Text("\(track.author) • \(track.durationFormatted)")
                                            .font(.system(size: 11, weight: .regular))
                                            .foregroundColor(Color.white.opacity(0.6))
                                            .lineLimit(1)
                                    }

                                    Spacer()

                                    Button(action: {
                                        onOptionsClick(track)
                                    }) {
                                        Image(systemName: "ellipsis")
                                            .font(.system(size: 14))
                                            .foregroundColor(Color.white.opacity(0.8))
                                            .frame(width: 32, height: 32)
                                            .background(Color.white.opacity(0.08))
                                            .clipShape(Circle())
                                    }
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .frame(width: 300)
                                .background(isCurrentPlaying ? Color.white.opacity(0.14) : Color.clear)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    let idx = activeSongs.firstIndex(where: { $0.id == track.id }) ?? 0
                                    handleSongClick(song: track, index: idx)
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
            .padding(.bottom, 20)
        }
    }

    // MARK: - 3. Category Mixed Cards Section
    private var mixesSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("\(category.label) Mixes")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.white)
                .padding(.horizontal, 16)
                .padding(.top, 4)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(Array(mixedTracks.enumerated()), id: \.element.id) { index, song in
                        VStack(alignment: .leading, spacing: 6) {
                            ZStack(alignment: .topLeading) {
                                SongArtworkView(song: song, cornerRadius: 16)
                                    .frame(width: 150, height: 150)

                                LinearGradient(
                                    colors: [Color.clear, Color.black.opacity(0.5)],
                                    startPoint: .top,
                                    endPoint: .bottom
                                )
                                .frame(width: 150, height: 150)
                                .clipShape(RoundedRectangle(cornerRadius: 16))

                                Text("\(category.label.uppercased()) \(index + 1)")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(.white)
                                    .tracking(1.2)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(accentColor.opacity(0.75))
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                    .padding(8)
                            }
                            .frame(width: 150, height: 150)

                            Text(song.title)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.white)
                                .lineLimit(1)
                                .frame(width: 150, alignment: .leading)

                            Text(song.author)
                                .font(.system(size: 11, weight: .regular))
                                .foregroundColor(SpasibaTheme.textMuted)
                                .lineLimit(1)
                                .frame(width: 150, alignment: .leading)
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            let idx = activeSongs.firstIndex(where: { $0.id == song.id }) ?? index
                            handleSongClick(song: song, index: idx)
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
            .padding(.bottom, 20)
        }
    }

    // MARK: - 4. All Category Songs List Section
    private var allTracksHeaderSection: some View {
        Text("All \(category.label) Tracks")
            .font(.system(size: 18, weight: .bold))
            .foregroundColor(SpasibaTheme.textStrong)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
    }

    // MARK: - Actions
    private func handleSongClick(song: Song, index: Int) {
        onSongClick(song, activeSongs, index)
    }

    private func handleLikeClick(songId: String) {
        if let s = activeSongs.first(where: { $0.id == songId }) {
            player.toggleLike(s)
        }
        onLikeClick(songId)
    }

    private func handleSwipeToQueue(song: Song) {
        player.playNextInQueue(song)
        onSwipeToQueue(song)
    }

    private func fetchSongsForCategory() async {
        internalLoading = true
        let fetched = await YouTubeMusicApi.shared.searchSongs(query: category.params.isEmpty ? category.label : category.params)
        await MainActor.run {
            self.internalSongs = fetched
            self.internalLoading = false
        }
    }
}

// MARK: - Array chunk helper
private extension Array {
    func chunked(into size: Int) -> [[Element]] {
        stride(from: 0, to: count, by: size).map {
            Array(self[$0 ..< Swift.min($0 + size, count)])
        }
    }
}
