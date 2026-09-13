import SwiftUI

public struct ArtistDetailsScreen: View {
    public let artistName: String
    public let currentSong: Song?
    public let likedSongs: Set<String>
    public var onBackClick: () -> Void
    public var onSongClick: (Song, [Song], Int) -> Void
    public var onLikeClick: (String) -> Void
    public var onOptionsClick: (Song) -> Void

    @State private var top50Songs: [Song] = []
    @State private var recentVideos: [Song] = []
    @State private var avatarUrl: String? = nil
    @State private var isLoading: Bool = true
    @State private var isSubscribed: Bool = false
    @State private var selectedSongForOptions: Song? = nil
    @State private var showArtistOptionsSheet: Bool = false

    public init(
        artistName: String,
        currentSong: Song? = nil,
        likedSongs: Set<String> = [],
        onBackClick: @escaping () -> Void = {},
        onSongClick: @escaping (Song, [Song], Int) -> Void = { _, _, _ in },
        onLikeClick: @escaping (String) -> Void = { _ in },
        onOptionsClick: @escaping (Song) -> Void = { _ in }
    ) {
        self.artistName = artistName
        self.currentSong = currentSong
        self.likedSongs = likedSongs
        self.onBackClick = onBackClick
        self.onSongClick = onSongClick
        self.onLikeClick = onLikeClick
        self.onOptionsClick = onOptionsClick
    }

    private var screenWidth: CGFloat {
        UIScreen.main.bounds.width
    }

    public var body: some View {
        ZStack {
            Color(hex: 0x0D0D0F).ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                LazyVStack(spacing: 0) {
                    // 1. YouTube Music Dynamic Immersive Header Banner
                    headerBannerSection

                    // 2. Action Control Bar (Subscribe Pill, Shuffle Icon, Circular Play Button)
                    actionControlBarSection

                    // 3. Featured Single / Release Card
                    if let latestSingle = top50Songs.first {
                        featuredSingleCard(latestSingle)
                    }

                    // 4. SECTION 1: TOP 20 MOST RECENT CHANNEL VIDEOS (Widescreen Video Carousel)
                    if !recentVideos.isEmpty {
                        recentVideosCarouselSection
                    }

                    // 5. SECTION 2: TOP 50 MOST LISTENED SONGS HEADER
                    topSongsHeaderSection

                    // 6. Songs List or Loading Skeleton
                    songsListSection
                }
                .frame(width: screenWidth)
                .padding(.bottom, 140)
            }
            .frame(width: screenWidth)
            .clipped()
        }
        .frame(width: screenWidth)
        .clipped()
        .task(id: artistName) {
            await loadArtistData()
        }
        .sheet(item: $selectedSongForOptions) { song in
            TrackOptionsMenu(
                song: song,
                onDismiss: { selectedSongForOptions = nil },
                onAddToPlaylist: {},
                onViewArtist: { _ in },
                onDownloadTrack: { song, type, quality in
                    SpasibaDownloadManager.shared.startDownload(song: song, type: type, quality: quality)
                }
            )
            .compatPresentationDetentsMedium()
        }
        .sheet(isPresented: $showArtistOptionsSheet) {
            ArtistOptionsMenuSheet(
                artistName: artistName,
                subtitle: "Official Artist Profile",
                onDismiss: { showArtistOptionsSheet = false },
                onShufflePlay: {
                    if !top50Songs.isEmpty {
                        let shuffled = top50Songs.shuffled()
                        onSongClick(shuffled[0], shuffled, 0)
                    }
                },
                onStartMix: {
                    if let first = top50Songs.first {
                        onSongClick(first, top50Songs, 0)
                    }
                },
                onShare: {
                    shareArtistProfile()
                },
                onSubscribe: {
                    isSubscribed.toggle()
                },
                onRemoveFromHistory: {}
            )
            .compatPresentationDetentsMedium()
        }
    }

    // MARK: - Subviews

    private var headerBannerSection: some View {
        ZStack(alignment: .bottomLeading) {
            let displayAvatar = avatarUrl
                ?? top50Songs.first?.thumbnail
                ?? "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600"

            SongArtworkView(url: displayAvatar, cornerRadius: 0)
                .frame(maxWidth: .infinity)
                .frame(height: 380)
                .clipped()

            // Dark Immersive Vignette Blend
            LinearGradient(
                colors: [
                    Color.black.opacity(0.4),
                    Color.clear,
                    Color(hex: 0x0D0D0F).opacity(0.7),
                    Color(hex: 0x0D0D0F)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: 380)

            // Top Action Bar Icons (Back, Radio Mix, Share)
            VStack {
                HStack {
                    Button(action: onBackClick) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 38, height: 38)
                            .background(Color.black.opacity(0.4))
                            .clipShape(Circle())
                    }

                    Spacer()

                    HStack(spacing: 8) {
                        Button(action: {
                            if let first = top50Songs.first {
                                onSongClick(first, top50Songs, 0)
                            }
                        }) {
                            Image(systemName: "play.fill")
                                .font(.system(size: 14))
                                .foregroundColor(.white)
                                .frame(width: 38, height: 38)
                                .background(Color.black.opacity(0.4))
                                .clipShape(Circle())
                        }

                        Button(action: {
                            shareArtistProfile()
                        }) {
                            Image(systemName: "square.and.arrow.up")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(width: 38, height: 38)
                                .background(Color.black.opacity(0.4))
                                .clipShape(Circle())
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 50)

                Spacer()
            }
            .frame(height: 380)

            // Artist Title & Monthly Audience Text
            VStack(alignment: .leading, spacing: 2) {
                Text(artistName)
                    .font(.system(size: 36, weight: .heavy))
                    .foregroundColor(.white)
                    .lineLimit(1)

                Text("Official Artist Profile • YouTube Music")
                    .font(.system(size: 14))
                    .foregroundColor(Color.white.opacity(0.75))
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 12)
        }
        .frame(height: 380)
    }

    private var actionControlBarSection: some View {
        HStack(alignment: .center) {
            HStack(spacing: 10) {
                // Subscribe Pill Button
                Button(action: { isSubscribed.toggle() }) {
                    Text(isSubscribed ? "Subscribed" : "Subscribe")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(isSubscribed ? .white : .black)
                        .padding(.horizontal, 20)
                        .frame(height: 44)
                        .background(isSubscribed ? Color.white.opacity(0.15) : Color.white)
                        .clipShape(Capsule())
                }

                // Shuffle Pill Button
                Button(action: {
                    if !top50Songs.isEmpty {
                        let shuffled = top50Songs.shuffled()
                        onSongClick(shuffled[0], shuffled, 0)
                    }
                }) {
                    Image(systemName: "shuffle")
                        .font(.system(size: 18))
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .background(Color.white.opacity(0.12))
                        .clipShape(Circle())
                        .overlay(Circle().stroke(Color.white.opacity(0.15), lineWidth: 1))
                }
            }

            Spacer()

            // Main Big Circular Play Button
            Button(action: {
                if let first = top50Songs.first {
                    onSongClick(first, top50Songs, 0)
                }
            }) {
                Image(systemName: "play.fill")
                    .font(.system(size: 24))
                    .foregroundColor(.black)
                    .frame(width: 54, height: 54)
                    .background(Color.white)
                    .clipShape(Circle())
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 8)
    }

    private func featuredSingleCard(_ latestSingle: Song) -> some View {
        Button(action: { onSongClick(latestSingle, top50Songs, 0) }) {
            HStack(spacing: 14) {
                SongArtworkView(song: latestSingle, cornerRadius: 10)
                    .frame(width: 56, height: 56)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Listen to the top single")
                        .font(.system(size: 12))
                        .foregroundColor(Color.white.opacity(0.7))

                    Text(latestSingle.title)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 16))
                    .foregroundColor(Color.white.opacity(0.6))
            }
            .padding(12)
            .background(Color(hex: 0x1E1E22))
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.white.opacity(0.08), lineWidth: 1)
            )
            .padding(.horizontal, 20)
            .padding(.top, 10)
        }
        .buttonStyle(PlainButtonStyle())
    }

    private var recentVideosCarouselSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Latest Official Videos")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(SpasibaTheme.textStrong)

                Spacer()

                Text("Top 20")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(SpasibaTheme.accent)
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(Array(recentVideos.enumerated()), id: \.element.id) { idx, video in
                        Button(action: { onSongClick(video, recentVideos, idx) }) {
                            VStack(alignment: .leading, spacing: 6) {
                                ZStack(alignment: .bottomTrailing) {
                                    SongArtworkView(song: video, cornerRadius: 10)
                                        .frame(width: 220, height: 124)

                                    Text("OFFICIAL VIDEO")
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 2)
                                        .background(Color.red.opacity(0.85))
                                        .clipShape(RoundedRectangle(cornerRadius: 4))
                                        .padding(6)
                                }

                                Text(video.title)
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.white)
                                    .lineLimit(1)

                                Text(video.author)
                                    .font(.system(size: 11))
                                    .foregroundColor(SpasibaTheme.textMuted)
                                    .lineLimit(1)
                            }
                            .frame(width: 220)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal, 20)
            }
            .frame(width: screenWidth)
        }
        .frame(width: screenWidth)
    }

    private var topSongsHeaderSection: some View {
        HStack {
            Text("Top 50 Most Listened Songs")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(SpasibaTheme.textStrong)

            Spacer()

            Text("\(top50Songs.count) Tracks")
                .font(.system(size: 12))
                .foregroundColor(Color.white.opacity(0.6))
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 10)
    }

    @ViewBuilder
    private var songsListSection: some View {
        if isLoading {
            VStack(spacing: 12) {
                ForEach(0..<8) { _ in
                    SongTileSkeleton()
                }
            }
        } else if top50Songs.isEmpty {
            Text("No songs found for \(artistName).")
                .font(.system(size: 14))
                .foregroundColor(SpasibaTheme.textMuted)
                .padding(20)
        } else {
            VStack(spacing: 4) {
                ForEach(Array(top50Songs.enumerated()), id: \.element.id) { index, song in
                    let isPlayingThis = song.id == currentSong?.id
                    let isLiked = likedSongs.contains(song.id)

                    Button(action: { onSongClick(song, top50Songs, index) }) {
                        HStack(spacing: 14) {
                            Text("\(index + 1)")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(isPlayingThis ? Color(hex: 0x1DB954) : Color.white.opacity(0.5))
                                .frame(width: 32, alignment: .leading)

                            SongArtworkView(song: song, cornerRadius: 8)
                                .frame(width: 50, height: 50)

                            VStack(alignment: .leading, spacing: 3) {
                                Text(song.title)
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(isPlayingThis ? Color(hex: 0x1DB954) : SpasibaTheme.textStrong)
                                    .lineLimit(1)

                                Text("\(song.author) • \(song.durationFormatted)")
                                    .font(.system(size: 12))
                                    .foregroundColor(SpasibaTheme.textMuted)
                                    .lineLimit(1)
                            }

                            Spacer()

                            Button(action: { onLikeClick(song.id) }) {
                                Image(systemName: isLiked ? "heart.fill" : "heart")
                                    .font(.system(size: 18))
                                    .foregroundColor(isLiked ? Color(hex: 0xEF4444) : SpasibaTheme.textMuted)
                                    .frame(width: 36, height: 36)
                            }
                            .buttonStyle(BorderlessButtonStyle())

                            Button(action: {
                                selectedSongForOptions = song
                                onOptionsClick(song)
                            }) {
                                Image(systemName: "ellipsis")
                                    .font(.system(size: 16))
                                    .foregroundColor(SpasibaTheme.textMuted)
                                    .rotationEffect(.degrees(90))
                                    .frame(width: 36, height: 36)
                            }
                            .buttonStyle(BorderlessButtonStyle())
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 6)
                        .background(isPlayingThis ? Color.white.opacity(0.06) : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
        }
    }

    private func loadArtistData() async {
        isLoading = true
        let pageData = await YouTubeMusicApi.fetchOfficialArtistPage(artistName: artistName)
        avatarUrl = pageData.avatarUrl ?? pageData.bannerUrl
        top50Songs = pageData.topSongs
        recentVideos = pageData.recentVideos
        isLoading = false
    }

    private func shareArtistProfile() {
        let clean = artistName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? artistName
        let shareText = "Listen to \(artistName) on YouTube Music: https://music.youtube.com/search?q=\(clean)"
        let av = UIActivityViewController(activityItems: [shareText], applicationActivities: nil)
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let rootVC = windowScene.windows.first(where: { $0.isKeyWindow })?.rootViewController {
            rootVC.present(av, animated: true)
        }
    }
}
