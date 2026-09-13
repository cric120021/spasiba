import SwiftUI

// MARK: - Spotify & YouTube Music Design System Tokens
private let spotifyGreen = Color(hex: 0x1DB954)
private let spotifyDarkBackground = Color.black
private let spotifyCardSurface = Color(hex: 0x282828)
private let spotifyCardHover = Color(hex: 0x333333)
private let textSecondaryGray = Color(hex: 0xB3B3B3)

public struct HomeScreen: View {
    @ObservedObject var player = NativePlayerManager.shared
    @ObservedObject var db = SpasibaDatabase.shared

    // State
    @State private var selectedMoodFilter: String? = nil
    @State private var selectedCategory: CategoryChip? = nil
    @State private var categorySearchSongs: [Song] = []
    @State private var categoryShelvesState: [HomeShelf] = []
    @State private var liveHomeShelves: [HomeShelf] = YouTubeMusicApi.shared.cachedHomeShelves.isEmpty ? [
        HomeShelf(
            title: "Quick picks",
            subtitle: "Listen again",
            style: .quickPicksGrid,
            songs: YouTubeMusicApi.getFallbackTracks()
        )
    ] : YouTubeMusicApi.shared.cachedHomeShelves

    @State private var topChartsState: [Song] = YouTubeMusicApi.getFallbackTracks()
    @State private var punjabiSongsState: [Song] = []
    @State private var hindiSongsState: [Song] = []
    @State private var englishSongsState: [Song] = []
    @State private var southSongsState: [Song] = []
    @State private var haryanviSongsState: [Song] = []
    @State private var bhojpuriSongsState: [Song] = []

    @State private var categoryChipsState: [CategoryChip] = YouTubeMusicApi.defaultCategories
    @State private var isCategoryLoading: Bool = false
    @State private var isShelvesLoading: Bool = false

    @State private var selectedLanguages: Set<String> = ["Hindi", "Punjabi", "English"]
    @State private var unreadNotificationsCount: Int = 0
    @State private var artistAvatars: [String: String] = [:]

    public var onSearchClick: () -> Void
    public var onLibraryClick: () -> Void
    public var onProfileClick: () -> Void
    public var onNotificationsClick: () -> Void
    public var onArtistClick: ((String) -> Void)?
    public var onSongClick: ((Song, [Song], Int) -> Void)?
    public var onLikeClick: ((String) -> Void)?
    public var onOptionsClick: ((Song) -> Void)?

    public init(
        onSearchClick: @escaping () -> Void = {},
        onLibraryClick: @escaping () -> Void = {},
        onProfileClick: @escaping () -> Void = {},
        onNotificationsClick: @escaping () -> Void = {},
        onArtistClick: ((String) -> Void)? = nil,
        onSongClick: ((Song, [Song], Int) -> Void)? = nil,
        onLikeClick: ((String) -> Void)? = nil,
        onOptionsClick: ((Song) -> Void)? = nil
    ) {
        self.onSearchClick = onSearchClick
        self.onLibraryClick = onLibraryClick
        self.onProfileClick = onProfileClick
        self.onNotificationsClick = onNotificationsClick
        self.onArtistClick = onArtistClick
        self.onSongClick = onSongClick
        self.onLikeClick = onLikeClick
        self.onOptionsClick = onOptionsClick
    }

    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5...11: return "Good morning"
        case 12...16: return "Good afternoon"
        default: return "Good evening"
        }
    }

    private var activeFilterName: String? {
        selectedCategory?.label ?? selectedMoodFilter
    }

    private var filteredSongs: [Song] {
        let recommended = liveHomeShelves.flatMap { $0.songs }
        if let filter = activeFilterName?.lowercased() {
            let matching = recommended.filter {
                $0.title.lowercased().contains(filter) || $0.author.lowercased().contains(filter)
            }
            var combined = categorySearchSongs + matching + recommended
            var seen = Set<String>()
            combined = combined.filter { seen.insert($0.id).inserted }
            return combined.isEmpty ? recommended : combined
        }
        return recommended
    }

    private var featuredHeroSong: Song? {
        filteredSongs.first
    }

    private var displayShelves: [HomeShelf] {
        let rawShelves: [HomeShelf]
        if activeFilterName != nil {
            rawShelves = categoryShelvesState
        } else {
            rawShelves = liveHomeShelves.filter { shelf in
                let t = shelf.title.lowercased()
                return !t.contains("quick") && !t.contains("speed dial")
            }
        }

        let isPodcastFilter = activeFilterName?.lowercased().contains("podcast") == true
        return rawShelves.map { shelf in
            let cleanedTitle: String
            if isPodcastFilter || shelf.title.lowercased().contains("podcast") {
                let t = shelf.title.replacingOccurrences(of: "(?i)\\bpodcasts?\\b", with: "", options: .regularExpression)
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                cleanedTitle = t.isEmpty ? "Featured Shows" : t
            } else {
                cleanedTitle = shelf.title
            }
            return HomeShelf(title: cleanedTitle, subtitle: shelf.subtitle, style: shelf.style, songs: shelf.songs)
        }
    }

    private var quickPicks: [Song] {
        if let apiShelf = liveHomeShelves.first(where: { shelf in
            let t = shelf.title.lowercased()
            return t.contains("quick") || t.contains("pick") || t.contains("favorite") || t.contains("dial") || t.contains("radio") || t.contains("mixed")
        }), !apiShelf.songs.isEmpty {
            return Array(apiShelf.songs.prefix(16))
        } else if !filteredSongs.isEmpty {
            return Array(filteredSongs.prefix(16))
        }
        return []
    }

private struct QuickPickColumn: Identifiable {
    let id: String
    let songs: [Song]
}

    private var quickPickColumns: [QuickPickColumn] {
        let chunks = stride(from: 0, to: quickPicks.count, by: 4).map {
            Array(quickPicks[$0..<min($0 + 4, quickPicks.count)])
        }
        return chunks.enumerated().map { QuickPickColumn(id: $0.element.first?.id ?? "\($0.offset)", songs: $0.element) }
    }

    private var mixedTracks: [Song] {
        let likedIds = Set(db.likedSongs.map { $0.id })
        let likedTrackList = filteredSongs.filter { likedIds.contains($0.id) }
        var combined = likedTrackList + filteredSongs
        var seen = Set<String>()
        combined = combined.filter { seen.insert($0.id).inserted }
        return Array(combined.prefix(6))
    }

    private var jumpBackInTracks: [Song] {
        if !db.recentlyPlayed.isEmpty {
            return Array(db.recentlyPlayed.prefix(6))
        } else {
            return Array(filteredSongs.prefix(6))
        }
    }

    private var favoriteArtistNames: [String] {
        let allTracks = db.recentlyPlayed + db.likedSongs + filteredSongs
        let validAuthors = allTracks.map { $0.author }.filter { $0 != "Artist" && !$0.isEmpty }
        var counts: [String: Int] = [:]
        for a in validAuthors {
            counts[a, default: 0] += 1
        }
        return counts.sorted { $0.value > $1.value }.map { $0.key }.prefix(10).map { $0 }
    }

    public var body: some View {
        ZStack {
            spotifyDarkBackground.ignoresSafeArea()

            if isShelvesLoading && liveHomeShelves.isEmpty {
                HomeScreenSkeleton()
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        // 1. Dynamic Header with Time Greeting & Top Action Target Icons
                        headerSection
                            .padding(.top, 2)
                            .padding(.bottom, 4)

                        // Quick Picks Horizontal 4x4 Swipe Grid
                        if activeFilterName == nil && !quickPicks.isEmpty {
                            quickPicksSection
                                .padding(.bottom, 24)
                        }

                        // Live YouTube Music Fetched Home Shelves & Category Shelves
                        if !displayShelves.isEmpty {
                            ForEach(displayShelves) { shelf in
                                shelfSection(shelf: shelf)
                                    .padding(.bottom, 24)
                            }
                        } else if activeFilterName != nil && !isCategoryLoading {
                            exploringCategoryPlaceholder
                        }

                        // 2. Default Home Sections (Only shown when no Category Filter is active)
                        if activeFilterName == nil {
                            defaultHomeSections
                        }

                        Spacer().frame(height: 120) // Clearance for mini player dock
                    }
                    .frame(maxWidth: .infinity)
                }
                .frame(maxWidth: .infinity)
                .refreshable {
                    await reloadAllData()
                }
            }
        }
        .task {
            await initialLoad()
        }
    }

    // MARK: - Header Section
    private var headerSection: some View {
        VStack(spacing: 0) {
            HStack(alignment: .center) {
                // Greeting & Brand Header
                VStack(alignment: .leading, spacing: 2) {
                    Text(greeting.uppercased())
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(spotifyGreen)
                        .tracking(1.5)

                    Text("Spasiba")
                        .font(.system(size: 26, weight: .black))
                        .foregroundStyle(SpasibaTheme.silverGradient)
                }

                Spacer()

                // Notification Bell Button with Red Badge
                Button(action: { onNotificationsClick() }) {
                    ZStack(alignment: .topTrailing) {
                        Circle()
                            .fill(Color.white.opacity(0.08))
                            .frame(width: 40, height: 40)
                            .overlay(
                                Image(systemName: "bell.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(.white)
                            )

                        if unreadNotificationsCount > 0 {
                            Text("\(unreadNotificationsCount)")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(Color(hex: 0xEF4444))
                                .clipShape(Capsule())
                                .offset(x: 4, y: -4)
                        }
                    }
                    .frame(width: 44, height: 44)
                }
                .buttonStyle(PlainButtonStyle())

                Spacer().frame(width: 8)

                // Profile Avatar Button
                Button(action: { onProfileClick() }) {
                    Circle()
                        .fill(Color.white.opacity(0.15))
                        .frame(width: 40, height: 40)
                        .overlay(
                            Image(systemName: "person.fill")
                                .font(.system(size: 18))
                                .foregroundColor(.white)
                        )
                }
                .buttonStyle(PlainButtonStyle())
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 6)

            // Dynamic YouTube Music Top Horizontal Filter Pills Bar
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(categoryChipsState) { chip in
                        let isSelected = activeFilterName?.caseInsensitiveCompare(chip.label) == .orderedSame
                        let isThisChipLoading = isSelected && isCategoryLoading

                        Button(action: {
                            handleCategorySelect(chip)
                        }) {
                            HStack(spacing: 6) {
                                if isThisChipLoading {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: isSelected ? .black : .white))
                                        .scaleEffect(0.7)
                                }
                                Text(chip.label)
                                    .font(.system(size: 13, weight: isSelected ? .heavy : .medium))
                                    .foregroundColor(isSelected ? .black : .white)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 7)
                            .background(isSelected ? Color.white : Color.white.opacity(0.08))
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(isSelected ? Color.white : Color.white.opacity(0.12), lineWidth: 1)
                            )
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
            }
            .padding(.top, 4)
        }
    }

    // MARK: - Quick Picks Section
    private var quickPicksSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("START RADIO")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(spotifyGreen)
                        .tracking(1.4)

                    Text("Quick picks")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(.white)
                }
                Spacer()
                Button(action: {
                    Task {
                        await reloadQuickPicks()
                    }
                }) {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(textSecondaryGray)
                        .padding(8)
                        .background(Color.white.opacity(0.08))
                        .clipShape(Circle())
                }
                .buttonStyle(PlainButtonStyle())
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 4)

            Spacer().frame(height: 12)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 16) {
                    ForEach(quickPickColumns) { column in
                        let columnSongs = column.songs
                        VStack(spacing: 10) {
                            ForEach(columnSongs) { song in
                                let isPlaying = song.id == player.currentSong?.id
                                Button(action: {
                                    let globalIndex = quickPicks.firstIndex(of: song) ?? 0
                                    player.playQueue(quickPicks, startIndex: globalIndex)
                                    db.addToHistory(song: song)
                                }) {
                                    HStack(spacing: 12) {
                                        SongArtworkView(song: song, cornerRadius: 6)
                                            .frame(width: 48, height: 48)

                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(song.title)
                                                .font(.system(size: 14, weight: .semibold))
                                                .foregroundColor(isPlaying ? spotifyGreen : .white)
                                                .lineLimit(1)
                                                .truncationMode(.tail)

                                            Text(song.author)
                                                .font(.system(size: 12))
                                                .foregroundColor(textSecondaryGray)
                                                .lineLimit(1)
                                                .truncationMode(.tail)
                                        }
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                    .padding(6)
                                    .frame(maxWidth: .infinity)
                                    .background(Color.white.opacity(0.05))
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                }
                                .buttonStyle(PlainButtonStyle())
                            }
                        }
                        .frame(width: 300)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    // MARK: - Display Shelves Section
    private func shelfSection(shelf: HomeShelf) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(shelf.title)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .truncationMode(.tail)

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(textSecondaryGray)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .contentShape(Rectangle())
            .onTapGesture {
                onSearchClick()
            }

            Spacer().frame(height: 12)

            if shelf.style == .videoWidescreen {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 14) {
                        ForEach(shelf.songs) { song in
                            Button(action: {
                                player.playQueue(shelf.songs, startIndex: shelf.songs.firstIndex(of: song) ?? 0)
                                db.addToHistory(song: song)
                            }) {
                                VStack(alignment: .leading, spacing: 6) {
                                    ZStack(alignment: .bottomTrailing) {
                                        SongArtworkView(song: song, cornerRadius: 8)
                                            .frame(width: 220, height: 124)

                                        Text("VIDEO")
                                            .font(.system(size: 9, weight: .bold))
                                            .foregroundColor(.white)
                                            .padding(.horizontal, 4)
                                            .padding(.vertical, 2)
                                            .background(Color.black.opacity(0.7))
                                            .clipShape(RoundedRectangle(cornerRadius: 4))
                                            .padding(6)
                                    }

                                    Text(song.title)
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                        .padding(.horizontal, 2)

                                    Text(song.author)
                                        .font(.system(size: 12))
                                        .foregroundColor(textSecondaryGray)
                                        .lineLimit(1)
                                        .padding(.horizontal, 2)
                                }
                                .frame(width: 220)
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                    .padding(.horizontal, 16)
                }
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 14) {
                        ForEach(shelf.songs) { song in
                            Button(action: {
                                player.playQueue(shelf.songs, startIndex: shelf.songs.firstIndex(of: song) ?? 0)
                                db.addToHistory(song: song)
                            }) {
                                VStack(alignment: .leading, spacing: 8) {
                                    SongArtworkView(song: song, cornerRadius: 8)
                                        .frame(width: 148, height: 148)

                                    Text(song.title)
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                        .padding(.horizontal, 2)

                                    Text(song.author)
                                        .font(.system(size: 11))
                                        .foregroundColor(textSecondaryGray)
                                        .lineLimit(1)
                                        .padding(.horizontal, 2)
                                }
                                .frame(width: 148)
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                    .padding(.horizontal, 16)
                }
            }
        }
    }

    private var exploringCategoryPlaceholder: some View {
        VStack(spacing: 6) {
            Text("Exploring \(activeFilterName ?? "")")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)

            Text("Top picks for \(activeFilterName ?? "") mood")
                .font(.system(size: 13))
                .foregroundColor(textSecondaryGray)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 24)
        .padding(.vertical, 32)
    }

    // MARK: - Default Home Sections
    private var defaultHomeSections: some View {
        VStack(spacing: 0) {
            // Category Song Rails
            if !topChartsState.isEmpty {
                categorySongRail(title: "Top Charts 2026", eyebrow: "GLOBAL CHARTS", songs: Array(topChartsState.prefix(5)))
            }
            if selectedLanguages.contains("Punjabi") && !punjabiSongsState.isEmpty {
                categorySongRail(title: "Punjabi Hits", eyebrow: "PUNJABI SPOTLIGHT", songs: punjabiSongsState)
            }
            if selectedLanguages.contains("Hindi") && !hindiSongsState.isEmpty {
                categorySongRail(title: "Hindi Bollywood", eyebrow: "FOR HINDI MOODS", songs: hindiSongsState)
            }
            if selectedLanguages.contains("English") && !englishSongsState.isEmpty {
                categorySongRail(title: "English Pop Hits", eyebrow: "GLOBAL HITS", songs: englishSongsState)
            }
            if (selectedLanguages.contains("South Hits") || selectedLanguages.contains("South")) && !southSongsState.isEmpty {
                categorySongRail(title: "South Tamil & Telugu", eyebrow: "SOUTH SPOTLIGHT", songs: southSongsState)
            }
            if selectedLanguages.contains("Haryanvi") && !haryanviSongsState.isEmpty {
                categorySongRail(title: "Haryanvi Explosives", eyebrow: "LOCAL HITS", songs: haryanviSongsState)
            }
            if selectedLanguages.contains("Bhojpuri") && !bhojpuriSongsState.isEmpty {
                categorySongRail(title: "Bhojpuri Party", eyebrow: "FOLK & DANCE", songs: bhojpuriSongsState)
            }

            // Hero Recommendation Spotlight
            if let hero = featuredHeroSong {
                heroSpotlightCard(song: hero)
                    .padding(.bottom, 18)
            }

            // Spotify 2-Column Shortcuts Grid
            if !filteredSongs.isEmpty {
                spotifyShortcutsGrid
                    .padding(.bottom, 24)
            }

            // "Mixed For You" Horizontal Shelf
            if !mixedTracks.isEmpty {
                mixedForYouSection
                    .padding(.bottom, 24)
            }

            // "Jump Back In" Horizontal Shelf
            if !jumpBackInTracks.isEmpty {
                categorySongRail(title: "Jump back in", eyebrow: "RECENTLY PLAYED", songs: jumpBackInTracks)
                    .padding(.bottom, 24)
            }

            // Favorite Artists Horizontal Rail
            if !favoriteArtistNames.isEmpty {
                favoriteArtistsSection
                    .padding(.bottom, 24)
            }
        }
    }

    private var favoriteArtistsSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text("YOUR ROTATION")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(spotifyGreen)
                    .tracking(1.4)

                Text("Favorite artists")
                    .font(.system(size: 19, weight: .bold))
                    .foregroundColor(.white)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 4)

            Spacer().frame(height: 12)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 16) {
                    ForEach(favoriteArtistNames, id: \.self) { artistName in
                        Button(action: {
                            if let onArtistClick = onArtistClick {
                                onArtistClick(artistName)
                            } else {
                                onSearchClick()
                            }
                        }) {
                            VStack(spacing: 8) {
                                ZStack {
                                    if let url = artistAvatars[artistName] {
                                        SongArtworkView(url: url, cornerRadius: 40)
                                    } else {
                                        Circle()
                                            .fill(Color(hex: 0x222222))
                                        Image(systemName: "person.fill")
                                            .font(.system(size: 32))
                                            .foregroundColor(Color.white.opacity(0.6))
                                    }
                                }
                                .frame(width: 80, height: 80)
                                .clipShape(Circle())

                                Text(artistName)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(.white)
                                    .lineLimit(1)
                                    .frame(width: 84)
                            }
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private func reloadQuickPicks() async {
        let refreshed = await YouTubeMusicApi.shared.searchSongs(query: "quick picks")
        if !refreshed.isEmpty {
            await MainActor.run {
                self.liveHomeShelves.removeAll(where: {
                    let t = $0.title.lowercased()
                    return t.contains("quick") || t.contains("pick")
                })
                self.liveHomeShelves.insert(HomeShelf(title: "Quick picks", subtitle: "Start radio", style: .quickPicksGrid, songs: refreshed), at: 0)
            }
        }
    }

    private func categorySongRail(title: String, eyebrow: String? = nil, songs: [Song]) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                if let eyebrow = eyebrow {
                    Text(eyebrow)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(spotifyGreen)
                        .tracking(1.4)
                        .lineLimit(1)
                        .truncationMode(.tail)
                }

                Text(title)
                    .font(.system(size: 19, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .truncationMode(.tail)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 4)

            Spacer().frame(height: 10)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(songs) { song in
                        Button(action: {
                            player.playQueue(songs, startIndex: songs.firstIndex(of: song) ?? 0)
                            db.addToHistory(song: song)
                        }) {
                            VStack(alignment: .leading, spacing: 8) {
                                SongArtworkView(song: song, cornerRadius: 10)
                                    .frame(width: 140, height: 140)

                                Text(song.title)
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(song.id == player.currentSong?.id ? spotifyGreen : .white)
                                    .lineLimit(1)

                                Text(song.author)
                                    .font(.system(size: 11))
                                    .foregroundColor(textSecondaryGray)
                                    .lineLimit(1)
                            }
                            .frame(width: 140)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal, 16)
            }
        }
        .padding(.bottom, 22)
    }

    private func heroSpotlightCard(song: Song) -> some View {
        Button(action: {
            player.playQueue(filteredSongs, startIndex: 0)
            db.addToHistory(song: song)
        }) {
            ZStack {
                // Background Thumbnail Image
                SongArtworkView(song: song, cornerRadius: 0)
                    .frame(height: 170)
                    .clipped()

                // Dark Horizontal Gradient Overlay
                LinearGradient(
                    colors: [
                        Color(hex: 0x0F0F0F).opacity(0.95),
                        Color(hex: 0x1E1E1E).opacity(0.80),
                        Color.clear
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )

                // Foreground Content
                HStack(alignment: .center, spacing: 12) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("RECOMMENDED FOR YOU")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(spotifyGreen)
                            .tracking(1.2)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(spotifyGreen.opacity(0.2))
                            .clipShape(RoundedRectangle(cornerRadius: 12))

                        Text(song.title)
                            .font(.system(size: 18, weight: .heavy))
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .truncationMode(.tail)

                        Text(song.author)
                            .font(.system(size: 13))
                            .foregroundColor(textSecondaryGray)
                            .lineLimit(1)
                            .truncationMode(.tail)

                        HStack(spacing: 4) {
                            Image(systemName: "play.fill")
                                .font(.system(size: 14))
                                .foregroundColor(.black)

                            Text("PLAY")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.black)
                                .tracking(0.8)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(spotifyGreen)
                        .clipShape(Capsule())
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    SongArtworkView(song: song, cornerRadius: 8)
                        .frame(width: 100, height: 100)
                }
                .padding(16)
            }
            .frame(height: 170)
            .frame(maxWidth: .infinity)
            .background(spotifyCardSurface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .padding(.horizontal, 16)
        }
        .buttonStyle(PlainButtonStyle())
    }

    private var spotifyShortcutsGrid: some View {
        let displayTracks = Array(filteredSongs.prefix(5))
        return VStack(spacing: 8) {
            // Row 1: Liked Songs & Track 1
            HStack(spacing: 8) {
                // Liked Songs Shortcut Card
                Button(action: {
                    onLibraryClick()
                }) {
                    HStack(spacing: 0) {
                        LinearGradient(
                            colors: [Color(hex: 0x450AF5), Color(hex: 0xC4EFD9)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                        .frame(width: 56, height: 56)
                        .overlay(
                            Image(systemName: "heart.fill")
                                .font(.system(size: 20))
                                .foregroundColor(.white)
                        )

                        Text("Liked Songs (\(db.likedSongs.count))")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(2)
                            .truncationMode(.tail)
                            .padding(.horizontal, 8)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .frame(height: 56)
                    .frame(maxWidth: .infinity)
                    .background(spotifyCardSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(PlainButtonStyle())

                if let track = displayTracks.first {
                    let isPlaying = track.id == player.currentSong?.id
                    shortcutTrackCard(track: track, isPlaying: isPlaying)
                } else {
                    Spacer().frame(maxWidth: .infinity)
                }
            }

            // Rows 2 & 3: Remaining 4 tracks
            let remainingTracks = Array(displayTracks.dropFirst())
            ForEach(0..<((remainingTracks.count + 1) / 2), id: \.self) { rowIdx in
                let startIndex = rowIdx * 2
                HStack(spacing: 8) {
                    if startIndex < remainingTracks.count {
                        let track = remainingTracks[startIndex]
                        shortcutTrackCard(track: track, isPlaying: track.id == player.currentSong?.id)
                    }
                    if startIndex + 1 < remainingTracks.count {
                        let track = remainingTracks[startIndex + 1]
                        shortcutTrackCard(track: track, isPlaying: track.id == player.currentSong?.id)
                    } else if remainingTracks.count % 2 != 0 {
                        Spacer().frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .padding(.horizontal, 16)
    }

    private func shortcutTrackCard(track: Song, isPlaying: Bool) -> some View {
        Button(action: {
            let idx = filteredSongs.firstIndex(of: track) ?? 0
            player.playQueue(filteredSongs, startIndex: idx)
            db.addToHistory(song: track)
        }) {
            HStack(spacing: 0) {
                SongArtworkView(song: track, cornerRadius: 8)
                    .frame(width: 56, height: 56)

                Text(track.title)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(isPlaying ? spotifyGreen : .white)
                    .lineLimit(2)
                    .truncationMode(.tail)
                    .padding(.horizontal, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(height: 56)
            .frame(maxWidth: .infinity)
            .background(isPlaying ? Color(hex: 0x1DB954).opacity(0.20) : spotifyCardSurface)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(PlainButtonStyle())
    }

    private var mixedForYouSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Mixed for you")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 18))
                    .foregroundColor(textSecondaryGray)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 4)

            Spacer().frame(height: 12)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(Array(mixedTracks.enumerated()), id: \.element.id) { index, song in
                        Button(action: {
                            player.playQueue(filteredSongs, startIndex: filteredSongs.firstIndex(of: song) ?? 0)
                            db.addToHistory(song: song)
                        }) {
                            VStack(alignment: .leading, spacing: 8) {
                                ZStack(alignment: .topLeading) {
                                    SongArtworkView(song: song, cornerRadius: 8)
                                        .frame(width: 156, height: 156)

                                    LinearGradient(
                                        colors: [Color.clear, Color.black.opacity(0.5)],
                                        startPoint: .top,
                                        endPoint: .bottom
                                    )
                                    .clipShape(RoundedRectangle(cornerRadius: 8))

                                    Text("DAILY MIX \(index + 1)")
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundColor(.white)
                                        .tracking(1.2)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 3)
                                        .background(Color.black.opacity(0.6))
                                        .clipShape(RoundedRectangle(cornerRadius: 6))
                                        .padding(6)
                                }

                                Text(song.title)
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white)
                                    .lineLimit(1)

                                Text("\(song.author) and more")
                                    .font(.system(size: 12))
                                    .foregroundColor(textSecondaryGray)
                                    .lineLimit(2)
                            }
                            .frame(width: 156)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    // MARK: - Data Loading & Actions
    private func handleCategorySelect(_ chip: CategoryChip) {
        let isCurrent = activeFilterName?.caseInsensitiveCompare(chip.label) == .orderedSame
        if isCurrent {
            selectedMoodFilter = nil
            selectedCategory = nil
            categorySearchSongs = []
            categoryShelvesState = []
        } else {
            selectedMoodFilter = chip.label
            selectedCategory = chip
            loadCategoryShelves(chip)
        }
    }

    private func loadCategoryShelves(_ chip: CategoryChip) {
        isCategoryLoading = true
        categoryShelvesState = []
        Task {
            let catLower = chip.label.lowercased()
            if catLower.contains("podcast") {
                let shelves = await YouTubeMusicApi.shared.fetchPodcastTopicShelves()
                await MainActor.run {
                    self.categoryShelvesState = shelves
                    self.isCategoryLoading = false
                }
            } else {
                let rawYtParam: String? = {
                    if !chip.params.isEmpty { return chip.params }
                    switch catLower {
                    case "romance": return "ggMPOZ3ZyZXF1ZW5jeT0y"
                    case "workout": return "ggMPOZ3ZyZXF1ZW5jeT0z"
                    case "energize": return "ggMPOZ3ZyZXF1ZW5jeT00"
                    case "relax": return "ggMPOZ3ZyZXF1ZW5jeT01"
                    case "focus": return "ggMPOZ3ZyZXF1ZW5jeT06"
                    case "commute": return "ggMPOZ3ZyZXF1ZW5jeT03"
                    case "sad": return "ggMPOZ3ZyZXF1ZW5jeT04"
                    case "sleep": return "ggMPOZ3ZyZXF1ZW5jeT05"
                    default: return nil
                    }
                }()

                let shelves = await YouTubeMusicApi.shared.fetchYouTubeMusicBrowseShelves(browseId: "FEmusic_home", params: rawYtParam)
                await MainActor.run {
                    self.categoryShelvesState = shelves
                    self.isCategoryLoading = false
                }
            }
        }
    }

    private func initialLoad() async {
        if liveHomeShelves.count > 1 && !punjabiSongsState.isEmpty {
            return // Data already loaded; skip redundant network refetches!
        }

        Task {
            let chips = await YouTubeMusicApi.shared.fetchCategoryChips()
            if !chips.isEmpty {
                await MainActor.run { self.categoryChipsState = chips }
            }
        }

        Task {
            let shelves = await YouTubeMusicApi.shared.fetchYouTubeMusicHomeShelves()
            if !shelves.isEmpty {
                await MainActor.run {
                    self.liveHomeShelves = shelves
                    self.isShelvesLoading = false
                }
            }
        }

        Task {
            let charts = await YouTubeMusicApi.shared.searchSongs(query: "latest release songs 2026 official")
            if !charts.isEmpty {
                await MainActor.run { self.topChartsState = charts }
            }
        }

        Task {
            let punjabi = await YouTubeMusicApi.shared.searchSongs(query: "latest punjabi songs 2026 official")
            if !punjabi.isEmpty {
                await MainActor.run { self.punjabiSongsState = punjabi }
            }
        }

        Task {
            let hindi = await YouTubeMusicApi.shared.searchSongs(query: "latest hindi songs 2026 official")
            if !hindi.isEmpty {
                await MainActor.run { self.hindiSongsState = hindi }
            }
        }

        Task {
            let english = await YouTubeMusicApi.shared.searchSongs(query: "latest english pop songs 2026 official")
            if !english.isEmpty {
                await MainActor.run { self.englishSongsState = english }
            }
        }

        // Prefetch artist avatars for favorite artists rotation
        let names = self.favoriteArtistNames
        if !names.isEmpty {
            Task {
                var loaded: [String: String] = [:]
                for name in names {
                    let url = await YouTubeMusicApi.shared.getArtistAvatarUrl(artist: name)
                    if !url.isEmpty {
                        loaded[name] = url
                    }
                }
                await MainActor.run {
                    self.artistAvatars = loaded
                }
            }
        }
    }

    private func reloadAllData() async {
        Task {
            let chips = await YouTubeMusicApi.shared.fetchCategoryChips()
            if !chips.isEmpty {
                await MainActor.run { self.categoryChipsState = chips }
            }
        }

        Task {
            let shelves = await YouTubeMusicApi.shared.fetchYouTubeMusicHomeShelves(forceRefresh: true)
            if !shelves.isEmpty {
                await MainActor.run {
                    self.liveHomeShelves = shelves
                    self.isShelvesLoading = false
                }
            }
        }

        Task {
            let charts = await YouTubeMusicApi.shared.searchSongs(query: "latest release songs 2026 official")
            if !charts.isEmpty {
                await MainActor.run { self.topChartsState = charts }
            }
        }

        Task {
            let punjabi = await YouTubeMusicApi.shared.searchSongs(query: "latest punjabi songs 2026 official")
            if !punjabi.isEmpty {
                await MainActor.run { self.punjabiSongsState = punjabi }
            }
        }

        Task {
            let hindi = await YouTubeMusicApi.shared.searchSongs(query: "latest hindi songs 2026 official")
            if !hindi.isEmpty {
                await MainActor.run { self.hindiSongsState = hindi }
            }
        }

        Task {
            let english = await YouTubeMusicApi.shared.searchSongs(query: "latest english pop songs 2026 official")
            if !english.isEmpty {
                await MainActor.run { self.englishSongsState = english }
            }
        }

        if let currentCat = selectedCategory {
            loadCategoryShelves(currentCat)
        }
    }
}
