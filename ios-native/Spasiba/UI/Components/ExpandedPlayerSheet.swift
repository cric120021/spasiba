import SwiftUI
import AVKit

public struct ExpandedPlayerSheet: View {
    @ObservedObject var playerManager = NativePlayerManager.shared
    @ObservedObject var db = SpasibaDatabase.shared
    @Environment(\.dismiss) private var dismiss

    // Configuration / Parameters
    public var song: Song? { playerManager.currentSong }
    public var isPlaying: Bool { playerManager.isPlaying }
    public var positionMs: Int64 { Int64(playerManager.currentTime * 1000) }
    public var durationMs: Int64 { Int64(playerManager.duration * 1000) }
    public var isLiked: Bool {
        guard let s = song else { return false }
        return db.isLiked(songId: s.id)
    }

    // UI States
    @State private var isVideoModeActive: Bool = false
    @State private var isFullscreenVideoOpen: Bool = false
    @State private var showVideoControls: Bool = true
    @State private var isVideoZoomed: Bool = false
    @State private var doubleTapSeekFeedback: String? = nil
    @State private var activeTab: String = "UP NEXT"

    @State private var isQualityDialogOpen: Bool = false
    @State private var isDownloadDialogOpen: Bool = false
    @State private var isDownloadQualityPickerOpen: Bool = false
    @State private var isOptionsMenuExpanded: Bool = false
    @State private var isSleepTimerModalOpen: Bool = false

    // Gestures
    @State private var sheetDragOffset: CGFloat = 0.0
    @State private var artDragOffset: CGFloat = 0.0

    // Vinyl animation state
    @State private var showVinylMode: Bool = false
    @State private var vinylRotation: Double = 0.0

    // Download item mock/state
    @State private var isDownloaded: Bool = false

    public init() {}

    public var body: some View {
        GeometryReader { geometry in
            let isLandscape = geometry.size.width > geometry.size.height

            ZStack {
                // Background
                if isVideoModeActive {
                    Color.black
                } else {
                    ambientBackground(width: geometry.size.width, height: geometry.size.height)
                }

                // Main Content
                if isLandscape && isVideoModeActive {
                    landscapeVideoLayout
                } else {
                    playerMainLayout(geometry: geometry)
                }

                // Landscape Fullscreen Video Modal Overlay
                if isFullscreenVideoOpen {
                    fullscreenVideoOverlay
                }

                // Transient double-tap seek feedback indicator
                if let feedback = doubleTapSeekFeedback {
                    Text(feedback)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.75))
                        .clipShape(Capsule())
                        .transition(.scale.combined(with: .opacity))
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .clipped()
            .offset(y: max(0, sheetDragOffset))
            .sheet(isPresented: $isQualityDialogOpen) {
                videoQualityDialog
                    .presentationDetents([.height(280)])
            }
            .sheet(isPresented: $isDownloadDialogOpen) {
                downloadOptionsDialog
                    .presentationDetents([.height(300)])
            }
            .sheet(isPresented: $isDownloadQualityPickerOpen) {
                downloadQualityDialog
                    .presentationDetents([.height(320)])
            }
            .sheet(isPresented: $isSleepTimerModalOpen) {
                EqualizerSleepTimerModal(
                    currentSpeed: playerManager.playbackSpeed,
                    currentSleepTimerMinutes: playerManager.currentSleepTimerMinutes,
                    onDismiss: { isSleepTimerModalOpen = false },
                    onSelectSpeed: { speed in
                        playerManager.setPlaybackSpeed(speed)
                    },
                    onSelectSleepTimer: { minutes in
                        playerManager.setSleepTimer(minutes: minutes)
                    }
                )
                .presentationDetents([.height(320)])
            }
        }
        .ignoresSafeArea()
        .onAppear {
            isVideoModeActive = playerManager.isVideoMode
            if activeTab == "LYRICS" {
                playerManager.fetchLyricsForCurrentSong()
            }
        }
        .onChange(of: playerManager.isVideoMode) { isVideo in
            isVideoModeActive = isVideo
        }
        .onChange(of: activeTab) { tab in
            if tab == "LYRICS" {
                playerManager.fetchLyricsForCurrentSong()
            }
        }
        .onChange(of: song?.id) { _ in
            if activeTab == "LYRICS" {
                playerManager.fetchLyricsForCurrentSong()
            }
        }
    }

    // MARK: - Ambient Dynamic Backdrop
    private func ambientBackground(width: CGFloat, height: CGFloat) -> some View {
        ZStack {
            if let song = song, let url = song.resolvedThumbnailUrl {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: width, height: height)
                            .clipped()
                            .blur(radius: 60)
                            .opacity(0.35)
                    }
                }
                .frame(width: width, height: height)
                .clipped()
            }

            LinearGradient(
                stops: [
                    .init(color: Color(hex: 0x121212), location: 0.0),
                    .init(color: Color(hex: 0x0A0A0A).opacity(0.95), location: 0.45),
                    .init(color: Color.black, location: 0.80),
                    .init(color: Color.black, location: 1.0)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: width, height: height)
        }
        .frame(width: width, height: height)
        .clipped()
    }

    // MARK: - Safe Area Helpers
    private var topSafeAreaInset: CGFloat {
        let windowScene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene ?? UIApplication.shared.connectedScenes.first as? UIWindowScene
        let window = windowScene?.windows.first(where: { $0.isKeyWindow }) ?? windowScene?.windows.first
        let inset = window?.safeAreaInsets.top ?? 0
        return inset > 0 ? inset : 48
    }

    private var bottomSafeAreaInset: CGFloat {
        let windowScene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene ?? UIApplication.shared.connectedScenes.first as? UIWindowScene
        let window = windowScene?.windows.first(where: { $0.isKeyWindow }) ?? windowScene?.windows.first
        let inset = window?.safeAreaInsets.bottom ?? 0
        return inset > 0 ? inset : 34
    }

    // MARK: - Main Player Layout (Scrollable matching Android Native 2)
    private func playerMainLayout(geometry: GeometryProxy) -> some View {
        let contentWidth = min(geometry.size.width, 540)
        let artSize = min(contentWidth * 0.82, 340)

        return VStack(spacing: 0) {
            // 1. Top Navigation Bar (pinned below status bar / notch)
            topNavigationBar
                .padding(.horizontal, 16)
                .padding(.top, topSafeAreaInset + 8)
                .padding(.bottom, 8)

            // 2. Scrollable Body
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    // Hero Media Display (Artwork / Vinyl / Video)
                    mediaDisplayBox(size: artSize)
                        .padding(.top, 8)
                        .padding(.bottom, 16)

                    // Track Metadata Row (Title, Author, Like Button, Share Button)
                    trackMetadataRow
                        .padding(.horizontal, 20)
                        .padding(.bottom, 8)

                    // YouTube Music Seeker Bar
                    seekerBarSection
                        .padding(.horizontal, 20)
                        .padding(.bottom, 10)

                    // Playback Controls Row (Shuffle, Prev, Play/Pause, Next, Repeat)
                    playbackControlsRow
                        .padding(.horizontal, 20)
                        .padding(.bottom, 16)

                    // Tabs Header: "UP NEXT" vs "LYRICS"
                    tabsHeader
                        .padding(.horizontal, 20)
                        .padding(.bottom, 12)

                    // Dynamic Section Below Tabs: UP NEXT Queue or Karaoke Lyrics
                    if activeTab == "UP NEXT" {
                        upNextQueueView
                            .padding(.horizontal, 20)
                    } else if activeTab == "LYRICS" {
                        KaraokeLyricsView(
                            syncedLines: playerManager.syncedLyrics,
                            positionMs: positionMs,
                            isLoading: playerManager.isLyricsLoading,
                            onSeekTo: { timestampMs in
                                playerManager.seekTo(timestampMs)
                            }
                        )
                        .padding(.horizontal, 20)
                    }

                    // Bottom Safe Area Spacing
                    Spacer()
                        .frame(height: max(bottomSafeAreaInset + 24, 36))
                }
                .frame(width: contentWidth)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(width: contentWidth)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Top Navigation Bar (Symmetrical 40pt side buttons + Centered Audio/Video switch)
    private var topNavigationBar: some View {
        HStack {
            // Down Arrow Button (Left - 40pt circle)
            Button(action: { dismiss() }) {
                Circle()
                    .fill(Color.white.opacity(0.08))
                    .frame(width: 40, height: 40)
                    .overlay(
                        Image(systemName: "chevron.down")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                    )
            }
            .buttonStyle(PlainButtonStyle())

            Spacer()

            // Center: Audio / Video Toggle Pill Switch (Matching Android Native 2 & YouTube Music)
            HStack(spacing: 0) {
                Button(action: {
                    withAnimation {
                        isVideoModeActive = false
                        playerManager.disableVideoMode()
                    }
                }) {
                    Text("Audio")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(!isVideoModeActive ? .black : .white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(!isVideoModeActive ? Color.white : Color.clear)
                        .clipShape(Capsule())
                }
                .buttonStyle(PlainButtonStyle())

                Button(action: {
                    // No-op per user request: clicking Video does nothing
                }) {
                    Text("Video")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white.opacity(0.6))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(Color.clear)
                        .clipShape(Capsule())
                }
                .buttonStyle(PlainButtonStyle())
            }
            .padding(2)
            .background(Color.white.opacity(0.10))
            .clipShape(Capsule())
            .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 1))

            Spacer()

            // Options Menu Button (Right - 40pt circle)
            Menu {
                if let song = song {
                    Button(action: {
                        ShareUtils.shareSong(song: song)
                    }) {
                        Label("Share Song", systemImage: "square.and.arrow.up")
                    }
                }

                Button(action: { isQualityDialogOpen = true }) {
                    Label("Video Quality (\(playerManager.videoQuality))", systemImage: "video")
                }

                if isDownloaded {
                    Button(role: .destructive, action: { isDownloaded = false }) {
                        Label("Remove download", systemImage: "trash")
                    }
                } else {
                    Button(action: { isDownloadDialogOpen = true }) {
                        Label("Download Track", systemImage: "arrow.down.circle")
                    }
                }

                Button(action: { isSleepTimerModalOpen = true }) {
                    Label("Speed & Sleep Timer", systemImage: "timer")
                }

                Button(action: {
                    withAnimation {
                        showVinylMode.toggle()
                    }
                }) {
                    Label(showVinylMode ? "Standard Artwork" : "Vinyl Record Mode", systemImage: "record.circle")
                }
            } label: {
                Circle()
                    .fill(Color.white.opacity(0.08))
                    .frame(width: 40, height: 40)
                    .overlay(
                        Image(systemName: "ellipsis")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                            .rotationEffect(.degrees(90))
                    )
            }
            .buttonStyle(PlainButtonStyle())
        }
        .contentShape(Rectangle())
        .gesture(dismissDragGesture)
    }

    private var dismissDragGesture: some Gesture {
        DragGesture(minimumDistance: 15)
            .onChanged { value in
                if value.translation.height > 0 {
                    sheetDragOffset = value.translation.height
                }
            }
            .onEnded { value in
                if value.translation.height > 120 {
                    dismiss()
                }
                withAnimation(.easeOut(duration: 0.25)) {
                    sheetDragOffset = 0
                }
            }
    }

    // MARK: - Media Display Box (Artwork / Vinyl / Video)
    private func mediaDisplayBox(size: CGFloat) -> some View {
        Group {
            if isVideoModeActive {
                // Video Mode View (16:9)
                ZStack {
                    if !isFullscreenVideoOpen {
                        if let player = playerManager.avPlayer {
                            VideoPlayer(player: player)
                                .aspectRatio(16 / 9, contentMode: isVideoZoomed ? .fill : .fit)
                                .frame(maxWidth: .infinity)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        } else {
                            ActiveVideoRepresentable()
                                .aspectRatio(16 / 9, contentMode: isVideoZoomed ? .fill : .fit)
                                .frame(maxWidth: .infinity)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                    } else {
                        Rectangle()
                            .fill(Color.black)
                            .aspectRatio(16 / 9, contentMode: .fit)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    // Video overlay controls
                    if showVideoControls || !isPlaying || playerManager.isLoading {
                        ZStack {
                            Color.black.opacity(0.5)

                            // Top Right Maximize Button
                            VStack {
                                HStack {
                                    Spacer()
                                    Button(action: { isFullscreenVideoOpen = true }) {
                                        Circle()
                                            .fill(Color.black.opacity(0.55))
                                            .frame(width: 38, height: 38)
                                            .overlay(
                                                Image(systemName: "viewfinder")
                                                    .font(.system(size: 18))
                                                    .foregroundColor(.white)
                                            )
                                    }
                                    .padding(10)
                                }
                                Spacer()
                            }

                            // Center Controls: Rewind 10s, Glass Play/Pause, Forward 10s
                            HStack(spacing: 32) {
                                Button(action: {
                                    playerManager.seekRelative(-10)
                                    showTransientFeedback("-10s")
                                }) {
                                    Circle()
                                        .fill(Color.black.opacity(0.45))
                                        .frame(width: 44, height: 44)
                                        .overlay(
                                            Image(systemName: "gobackward.10")
                                                .font(.system(size: 20))
                                                .foregroundColor(.white)
                                        )
                                }

                                Button(action: { playerManager.togglePlayPause() }) {
                                    ZStack {
                                        Circle()
                                            .fill(Color.white.opacity(0.25))
                                            .frame(width: 60, height: 60)
                                            .overlay(Circle().stroke(Color.white.opacity(0.4), lineWidth: 1))

                                        if playerManager.isLoading {
                                            ProgressView()
                                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                        } else {
                                            Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                                                .font(.system(size: 26))
                                                .foregroundColor(.white)
                                                .offset(x: isPlaying ? 0 : 2)
                                        }
                                    }
                                }

                                Button(action: {
                                    playerManager.seekRelative(10)
                                    showTransientFeedback("+10s")
                                }) {
                                    Circle()
                                        .fill(Color.black.opacity(0.45))
                                        .frame(width: 44, height: 44)
                                        .overlay(
                                            Image(systemName: "goforward.10")
                                                .font(.system(size: 20))
                                                .foregroundColor(.white)
                                        )
                                }
                            }
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
                .frame(maxWidth: .infinity)
                .aspectRatio(16 / 9, contentMode: .fit)
                .onTapGesture {
                    withAnimation {
                        showVideoControls.toggle()
                    }
                }
                .padding(.horizontal, 16)
            } else if showVinylMode {
                // Vinyl Spin Mode with Needle Drop Animation
                vinylTurntableSection(size: size)
            } else {
                // Standard Album Artwork with horizontal drag-to-skip
                if let song = song {
                    SongArtworkView(song: song, cornerRadius: max(14, size * 0.075))
                        .frame(width: size, height: size)
                        .shadow(color: Color.black.opacity(0.55), radius: 22, x: 0, y: 10)
                        .offset(x: artDragOffset)
                        .gesture(
                            DragGesture()
                                .onChanged { val in
                                    artDragOffset = val.translation.width
                                }
                                .onEnded { val in
                                    if artDragOffset > 80 {
                                        playerManager.playPrevious()
                                    } else if artDragOffset < -80 {
                                        playerManager.playNext()
                                    }
                                    withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                        artDragOffset = 0
                                    }
                                }
                        )
                }
            }
        }
    }

    // MARK: - Vinyl Turntable with Spinning Disc and Needle Drop
    private func vinylTurntableSection(size: CGFloat) -> some View {
        let baseSize = size
        let discSize = baseSize * 0.85
        let centerLabelSize = discSize * 0.33
        let scale = baseSize / 320.0

        return ZStack(alignment: .topTrailing) {
            // Turntable Platter Base
            RoundedRectangle(cornerRadius: baseSize * 0.0625)
                .fill(Color(hex: 0x121214))
                .frame(width: baseSize, height: baseSize)
                .overlay(
                    RoundedRectangle(cornerRadius: baseSize * 0.0625)
                        .stroke(Color.white.opacity(0.08), lineWidth: 1)
                )

            // Spinning Vinyl Disc - centered with slight left offset for tonearm
            TimelineView(.animation) { timeline in
                let rotation = isPlaying ? timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 2.0) * 180.0 : 0.0

                ZStack {
                    // Outer Black Vinyl with realistic grooves
                    Circle()
                        .fill(
                            RadialGradient(
                                gradient: Gradient(colors: [
                                    Color(hex: 0x1A1A1A),
                                    Color(hex: 0x0A0A0A),
                                    Color(hex: 0x222222),
                                    Color(hex: 0x080808),
                                    Color(hex: 0x181818),
                                    Color(hex: 0x050505)
                                ]),
                                center: .center,
                                startRadius: discSize * 0.15,
                                endRadius: discSize * 0.5
                            )
                        )
                        .frame(width: discSize, height: discSize)
                        .shadow(color: Color.black.opacity(0.7), radius: 12, x: 0, y: 6)

                    // Concentric vinyl grooves
                    ForEach(0..<6) { i in
                        let grooveFraction = 0.34 + Double(i) * 0.10
                        Circle()
                            .stroke(Color.white.opacity(0.04), lineWidth: 1)
                            .frame(width: discSize * CGFloat(grooveFraction), height: discSize * CGFloat(grooveFraction))
                    }

                    // Center Album Art Label
                    if let song = song {
                        SongArtworkView(song: song, cornerRadius: centerLabelSize / 2)
                            .frame(width: centerLabelSize, height: centerLabelSize)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Color.black, lineWidth: 3))

                        // Spindle Hole
                        Circle()
                            .fill(Color(hex: 0x202025))
                            .frame(width: discSize * 0.05, height: discSize * 0.05)
                            .overlay(Circle().stroke(Color(hex: 0x444444), lineWidth: 1.5))
                    }
                }
                .rotationEffect(.degrees(rotation))
            }
            .frame(width: discSize, height: discSize)
            .offset(x: -baseSize * 0.04, y: 0)

            // Turntable Tonearm / Needle (scales proportionally with platter)
            ZStack(alignment: .topTrailing) {
                // Pivot Base
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Color(hex: 0xD1D5DB), Color(hex: 0x4B5563)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 32, height: 32)
                    .shadow(color: Color.black.opacity(0.5), radius: 4)

                // Arm + Cartridge Head
                VStack(spacing: 0) {
                    // Arm rod
                    Capsule()
                        .fill(
                            LinearGradient(
                                colors: [Color(hex: 0xE5E7EB), Color(hex: 0x9CA3AF)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: 5, height: 130)

                    // Cartridge & Stylus
                    RoundedRectangle(cornerRadius: 2)
                        .fill(Color(hex: 0xEF4444))
                        .frame(width: 12, height: 22)
                        .overlay(
                            Rectangle()
                                .fill(Color.white)
                                .frame(width: 1.5, height: 6),
                            alignment: .bottom
                        )
                }
                .offset(x: -12, y: 16)
            }
            .scaleEffect(scale, anchor: .topTrailing)
            .rotationEffect(.degrees(isPlaying ? 12 : -22), anchor: .topTrailing)
            .animation(.spring(response: 0.6, dampingFraction: 0.7), value: isPlaying)
            .padding(.top, 14 * scale)
            .padding(.trailing, 18 * scale)
        }
        .frame(width: baseSize, height: baseSize)
    }

    // MARK: - Track Metadata Row (Title/Author + Heart Button, matching Spotify 1:1)
    private var trackMetadataRow: some View {
        HStack(alignment: .center, spacing: 12) {
            if let song = song {
                VStack(alignment: .leading, spacing: 4) {
                    Text(song.title)
                        .font(.system(size: 21, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .truncationMode(.tail)

                    Text(song.author)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(Color.white.opacity(0.7))
                        .lineLimit(1)
                        .truncationMode(.tail)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                HStack(spacing: 4) {
                    // Like button
                    Button(action: {
                        db.toggleLike(song: song)
                    }) {
                        Image(systemName: isLiked ? "heart.fill" : "heart")
                            .font(.system(size: 22))
                            .foregroundColor(isLiked ? Color(hex: 0xEF4444) : .white)
                            .frame(width: 42, height: 42)
                    }
                    .buttonStyle(PlainButtonStyle())

                    // Share button
                    Button(action: {
                        ShareUtils.shareSong(song: song)
                    }) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 20))
                            .foregroundColor(Color.white.opacity(0.85))
                            .frame(width: 42, height: 42)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
        }
    }

    // MARK: - YouTube Music Seeker Bar
    private var seekerBarSection: some View {
        YouTubeMusicSeekerBar(
            progressFraction: Float(playerManager.playbackProgress),
            positionFormatted: formatTime(playerManager.currentTime),
            durationFormatted: formatTime(playerManager.duration),
            onSeek: { frac in
                let target = Double(frac) * playerManager.duration
                playerManager.seek(to: target)
            }
        )
    }

    // MARK: - Playback Controls Row
    private var playbackControlsRow: some View {
        HStack {
            Spacer()

            // Shuffle Button
            Button(action: { playerManager.queueEngine.toggleShuffle() }) {
                Image(systemName: "shuffle")
                    .font(.system(size: 20))
                    .foregroundColor(playerManager.queueEngine.isShuffleEnabled ? Color(hex: 0x1DB954) : Color.white.opacity(0.7))
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(PlainButtonStyle())

            Spacer()

            // Previous Track Button
            Button(action: { playerManager.playPrevious() }) {
                Circle()
                    .fill(Color.white.opacity(0.08))
                    .frame(width: 44, height: 44)
                    .overlay(
                        Image(systemName: "backward.fill")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                    )
            }
            .buttonStyle(PlainButtonStyle())

            Spacer()

            // Big Glass Play/Pause Button
            Button(action: { playerManager.togglePlayPause() }) {
                ZStack {
                    Circle()
                        .fill(Color.white)
                        .frame(width: 62, height: 62)

                    if playerManager.isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .black))
                            .scaleEffect(1.1)
                    } else {
                        Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 28))
                            .foregroundColor(.black)
                            .offset(x: isPlaying ? 0 : 2)
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())

            Spacer()

            // Next Track Button
            Button(action: { playerManager.playNext() }) {
                Circle()
                    .fill(Color.white.opacity(0.08))
                    .frame(width: 44, height: 44)
                    .overlay(
                        Image(systemName: "forward.fill")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                    )
            }
            .buttonStyle(PlainButtonStyle())

            Spacer()

            // Repeat Button
            Button(action: { playerManager.queueEngine.cycleRepeatMode() }) {
                Image(systemName: repeatIconName)
                    .font(.system(size: 20))
                    .foregroundColor(playerManager.queueEngine.repeatMode != .off ? Color(hex: 0x1DB954) : Color.white.opacity(0.5))
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(PlainButtonStyle())

            Spacer()
        }
    }

    private var repeatIconName: String {
        switch playerManager.queueEngine.repeatMode {
        case .off: return "repeat"
        case .all: return "repeat"
        case .one: return "repeat.1"
        }
    }

    // MARK: - Tabs Header: UP NEXT vs LYRICS (Matching Android Native 2)
    private var tabsHeader: some View {
        HStack(spacing: 0) {
            ForEach(["UP NEXT", "LYRICS"], id: \.self) { tab in
                let isSelected = activeTab == tab
                Button(action: {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                        activeTab = tab
                    }
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: tabIcon(for: tab))
                            .font(.system(size: 11, weight: .bold))
                        Text(tabTitle(for: tab))
                            .font(.system(size: 11, weight: .bold))
                    }
                    .foregroundColor(isSelected ? .black : Color.white.opacity(0.7))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(isSelected ? Color.white : Color.clear)
                    .clipShape(Capsule())
                }
                .buttonStyle(PlainButtonStyle())
            }
        }
        .padding(3)
        .background(Color.white.opacity(0.08))
        .clipShape(Capsule())
        .overlay(Capsule().stroke(Color.white.opacity(0.12), lineWidth: 1))
    }

    private func tabIcon(for tab: String) -> String {
        switch tab {
        case "UP NEXT": return "list.bullet"
        case "LYRICS": return "quote.bubble.fill"
        default: return "music.note"
        }
    }

    private func tabTitle(for tab: String) -> String {
        switch tab {
        case "UP NEXT": return "Up Next"
        case "LYRICS": return "Lyrics"
        default: return tab
        }
    }

    // MARK: - UP NEXT Queue View
    private var upNextQueueView: some View {
        VStack(spacing: 6) {
            if playerManager.queue.isEmpty {
                VStack(spacing: 12) {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    Text("Loading queue...")
                        .font(.system(size: 14))
                        .foregroundColor(Color.white.opacity(0.6))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 32)
            } else {
                ForEach(playerManager.queue) { qSong in
                    let isCurrent = qSong.id == (song?.id ?? playerManager.currentSong?.id)
                    Button(action: {
                        if let idx = playerManager.queue.firstIndex(of: qSong) {
                            playerManager.playQueue(playerManager.queue, startIndex: idx)
                        }
                    }) {
                        HStack(spacing: 12) {
                            SongArtworkView(song: qSong, cornerRadius: 8)
                                .frame(width: 42, height: 42)
                                .fixedSize()

                            VStack(alignment: .leading, spacing: 2) {
                                Text(qSong.title)
                                    .font(.system(size: 14, weight: isCurrent ? .bold : .regular))
                                    .foregroundColor(isCurrent ? Color(hex: 0x1DB954) : Color.white.opacity(0.9))
                                    .lineLimit(1)
                                    .truncationMode(.tail)

                                Text(qSong.author)
                                    .font(.system(size: 12))
                                    .foregroundColor(Color.white.opacity(0.6))
                                    .lineLimit(1)
                                    .truncationMode(.tail)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)

                            if isCurrent {
                                EqualizerWaveformAnimation(isPlaying: isPlaying)
                                    .fixedSize()
                            }
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 8)
                        .background(isCurrent ? Color.white.opacity(0.12) : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(PlainButtonStyle())
                }

                if playerManager.queue.count <= 1 {
                    HStack(spacing: 8) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(0.8)
                        Text("Auto-generating radio queue...")
                            .font(.system(size: 13))
                            .foregroundColor(Color.white.opacity(0.6))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                }
            }
        }
    }

    // MARK: - Fullscreen Landscape Video Overlay
    private var fullscreenVideoOverlay: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if isFullscreenVideoOpen {
                if let player = playerManager.avPlayer {
                    VideoPlayer(player: player)
                        .aspectRatio(16 / 9, contentMode: isVideoZoomed ? .fill : .fit)
                        .ignoresSafeArea()
                } else {
                    ActiveVideoRepresentable()
                        .aspectRatio(16 / 9, contentMode: isVideoZoomed ? .fill : .fit)
                        .ignoresSafeArea()
                }
            }

            // Controls overlay
            if showVideoControls || !isPlaying || playerManager.isLoading {
                VStack {
                    // Top Bar: Back, Title, Aspect Toggle
                    HStack(spacing: 12) {
                        Button(action: { isFullscreenVideoOpen = false }) {
                            Circle()
                                .fill(Color.black.opacity(0.55))
                                .frame(width: 38, height: 38)
                                .overlay(
                                    Image(systemName: "arrow.backward")
                                        .font(.system(size: 18))
                                        .foregroundColor(.white)
                                )
                        }

                        if let song = song {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(song.title)
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                                    .lineLimit(1)
                                Text(song.author)
                                    .font(.system(size: 12))
                                    .foregroundColor(Color.white.opacity(0.7))
                                    .lineLimit(1)
                            }
                        }

                        Spacer()

                        Button(action: { isVideoZoomed.toggle() }) {
                            Circle()
                                .fill(Color.black.opacity(0.55))
                                .frame(width: 38, height: 38)
                                .overlay(
                                    Image(systemName: isVideoZoomed ? "aspectratio" : "viewfinder")
                                        .font(.system(size: 16))
                                        .foregroundColor(.white)
                                )
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)

                    Spacer()

                    // Center Controls: -10s, Play/Pause, +10s
                    HStack(spacing: 40) {
                        Button(action: {
                            playerManager.seekRelative(-10)
                            showTransientFeedback("-10s")
                        }) {
                            Circle()
                                .fill(Color.black.opacity(0.5))
                                .frame(width: 50, height: 50)
                                .overlay(
                                    Image(systemName: "gobackward.10")
                                        .font(.system(size: 24))
                                        .foregroundColor(.white)
                                )
                        }

                        Button(action: { playerManager.togglePlayPause() }) {
                            ZStack {
                                Circle()
                                    .fill(Color.white.opacity(0.25))
                                    .frame(width: 68, height: 68)
                                    .overlay(Circle().stroke(Color.white.opacity(0.45), lineWidth: 1.5))

                                if playerManager.isLoading {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                } else {
                                    Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                                        .font(.system(size: 30))
                                        .foregroundColor(.white)
                                        .offset(x: isPlaying ? 0 : 2)
                                }
                            }
                        }

                        Button(action: {
                            playerManager.seekRelative(10)
                            showTransientFeedback("+10s")
                        }) {
                            Circle()
                                .fill(Color.black.opacity(0.5))
                                .frame(width: 50, height: 50)
                                .overlay(
                                    Image(systemName: "goforward.10")
                                        .font(.system(size: 24))
                                        .foregroundColor(.white)
                                )
                        }
                    }

                    Spacer()

                    // Bottom Seeker Slider
                    VStack(spacing: 4) {
                        Slider(
                            value: Binding(
                                get: { playerManager.playbackProgress },
                                set: { frac in
                                    playerManager.seek(to: frac * playerManager.duration)
                                }
                            ),
                            in: 0...1
                        )
                        .accentColor(.white)

                        HStack {
                            Text(formatTime(playerManager.currentTime))
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                            Spacer()
                            Text(formatTime(playerManager.duration))
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 16)
                }
                .background(
                    LinearGradient(
                        colors: [Color.black.opacity(0.7), Color.clear, Color.black.opacity(0.8)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
            }
        }
        .onTapGesture {
            withAnimation {
                showVideoControls.toggle()
            }
        }
    }

    // MARK: - Landscape Video Layout
    private var landscapeVideoLayout: some View {
        HStack(spacing: 24) {
            mediaDisplayBox(size: 340)
                .frame(maxWidth: .infinity)

            VStack(spacing: 16) {
                Spacer()
                trackMetadataRow
                seekerBarSection
                playbackControlsRow
                Spacer()
            }
            .frame(maxWidth: 380)
            .padding(20)
        }
    }

    // MARK: - Dialogs
    private var videoQualityDialog: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Select Video Quality")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)
                .padding(.top, 20)

            VStack(spacing: 8) {
                ForEach(playerManager.availableVideoQualities, id: \.self) { q in
                    Button(action: {
                        playerManager.setVideoQuality(q)
                        isQualityDialogOpen = false
                    }) {
                        HStack {
                            Image(systemName: playerManager.videoQuality == q ? "largecircle.fill.circle" : "circle")
                                .foregroundColor(playerManager.videoQuality == q ? .white : Color.white.opacity(0.5))
                            Text(q)
                                .font(.system(size: 15, weight: .medium))
                                .foregroundColor(.white)
                            Spacer()
                        }
                        .padding(.vertical, 8)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }

            Spacer()
        }
        .padding(.horizontal, 24)
        .background(Color(hex: 0x181822).ignoresSafeArea())
    }

    private var downloadOptionsDialog: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let song = song {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Download Track")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                    Text(song.title)
                        .font(.system(size: 13))
                        .foregroundColor(Color.white.opacity(0.6))
                        .lineLimit(1)
                }
                .padding(.top, 20)

                Button(action: {
                    isDownloadDialogOpen = false
                    isDownloaded = true
                }) {
                    HStack(spacing: 12) {
                        Image(systemName: "music.note")
                            .font(.system(size: 20))
                            .foregroundColor(.white)

                        Text("Audio Only (Default - 320k)")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)

                        Spacer()
                    }
                    .padding(14)
                    .background(Color.white.opacity(0.15))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.white, lineWidth: 1))
                }
                .buttonStyle(PlainButtonStyle())

                Button(action: {
                    isDownloadDialogOpen = false
                    isDownloadQualityPickerOpen = true
                }) {
                    HStack(spacing: 12) {
                        Image(systemName: "video.fill")
                            .font(.system(size: 20))
                            .foregroundColor(Color.white.opacity(0.7))

                        Text("Video + Audio (Select Quality)")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)

                        Spacer()
                    }
                    .padding(14)
                    .background(Color.white.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(PlainButtonStyle())
            }

            Spacer()
        }
        .padding(.horizontal, 24)
        .background(Color(hex: 0x141416).ignoresSafeArea())
    }

    private var downloadQualityDialog: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Select Video Quality")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)
                .padding(.top, 20)

            ForEach(["1080p", "720p", "480p", "360p"], id: \.self) { q in
                Button(action: {
                    isDownloadQualityPickerOpen = false
                    isDownloaded = true
                }) {
                    Text("\(q) Video Quality")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(14)
                        .background(Color.white.opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(PlainButtonStyle())
            }

            Spacer()
        }
        .padding(.horizontal, 24)
        .background(Color(hex: 0x121214).ignoresSafeArea())
    }

    private func showTransientFeedback(_ text: String) {
        withAnimation {
            doubleTapSeekFeedback = text
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
            withAnimation {
                self.doubleTapSeekFeedback = nil
            }
        }
    }

    private func formatTime(_ seconds: Double) -> String {
        let mins = Int(seconds) / 60
        let secs = Int(seconds) % 60
        return String(format: "%d:%02d", mins, secs)
    }
}

// MARK: - Karaoke Lyrics View (Ported 1:1 from KaraokeLyricsView)
public struct KaraokeLyricsView: View {
    public let syncedLines: [LyricsLine]
    public let positionMs: Int64
    public let isLoading: Bool
    public var isExpandedTablet: Bool = false
    public let onSeekTo: (Int64) -> Void

    public init(
        syncedLines: [LyricsLine],
        positionMs: Int64,
        isLoading: Bool,
        isExpandedTablet: Bool = false,
        onSeekTo: @escaping (Int64) -> Void
    ) {
        self.syncedLines = syncedLines
        self.positionMs = positionMs
        self.isLoading = isLoading
        self.isExpandedTablet = isExpandedTablet
        self.onSeekTo = onSeekTo
    }

    private var activeIndex: Int {
        let curSec = Double(positionMs) / 1000.0
        let idx = syncedLines.lastIndex(where: { $0.timestampSeconds <= curSec }) ?? 0
        return max(0, min(idx, syncedLines.count - 1))
    }

    public var body: some View {
        Group {
            if isLoading {
                VStack(spacing: 12) {
                    Spacer()
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        .scaleEffect(1.2)
                    Text("Loading synced lyrics...")
                        .font(.system(size: 14))
                        .foregroundColor(Color.white.opacity(0.6))
                    Spacer()
                }
                .frame(maxWidth: .infinity)
                .frame(height: 260)
            } else if syncedLines.isEmpty {
                VStack(spacing: 10) {
                    Spacer()
                    Image(systemName: "quote.bubble")
                        .font(.system(size: 36))
                        .foregroundColor(Color.white.opacity(0.35))
                    Text("No lyrics available for this track.")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(Color.white.opacity(0.65))
                    Spacer()
                }
                .frame(maxWidth: .infinity)
                .frame(height: 200)
            } else {
                ScrollViewReader { proxy in
                    ScrollView(showsIndicators: false) {
                        LazyVStack(alignment: .leading, spacing: 20) {
                            Spacer().frame(height: 40)

                            ForEach(0..<syncedLines.count, id: \.self) { index in
                                let line = syncedLines[index]
                                let isActive = index == activeIndex

                                Text(line.text)
                                    .font(.system(
                                        size: isActive ? 24 : 18,
                                        weight: isActive ? .bold : .medium
                                    ))
                                    .foregroundColor(Color.white.opacity(isActive ? 1.0 : 0.40))
                                    .lineSpacing(6)
                                    .scaleEffect(isActive ? 1.04 : 1.0, anchor: .leading)
                                    .animation(.spring(response: 0.35, dampingFraction: 0.75), value: isActive)
                                    .contentShape(Rectangle())
                                    .onTapGesture {
                                        let targetMs = Int64(line.timestampSeconds * 1000)
                                        onSeekTo(targetMs)
                                    }
                                    .id(index)
                            }

                            Spacer().frame(height: 60)
                        }
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 450)
                    .mask(
                        LinearGradient(
                            stops: [
                                .init(color: .clear, location: 0.0),
                                .init(color: .white, location: 0.10),
                                .init(color: .white, location: 0.88),
                                .init(color: .clear, location: 1.0)
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .onChange(of: activeIndex) { newIndex in
                        withAnimation(.easeInOut(duration: 0.35)) {
                            proxy.scrollTo(max(0, newIndex), anchor: .center)
                        }
                    }
                }
            }
        }
    }
}

// MARK: - YouTube Music Dynamic Height Seeker Bar
public struct YouTubeMusicSeekerBar: View {
    public let progressFraction: Float
    public let positionFormatted: String
    public let durationFormatted: String
    public let onSeek: (Float) -> Void

    @State private var isSeeking: Bool = false
    @State private var userFraction: Float = 0.0

    public init(
        progressFraction: Float,
        positionFormatted: String,
        durationFormatted: String,
        onSeek: @escaping (Float) -> Void
    ) {
        self.progressFraction = progressFraction
        self.positionFormatted = positionFormatted
        self.durationFormatted = durationFormatted
        self.onSeek = onSeek
    }

    private var displayFraction: CGFloat {
        CGFloat(isSeeking ? userFraction : progressFraction).clamped(to: 0...1)
    }

    public var body: some View {
        VStack(spacing: 4) {
            GeometryReader { geometry in
                let width = geometry.size.width
                let barHeight: CGFloat = isSeeking ? 10.0 : 5.0

                ZStack(alignment: .leading) {
                    // Unplayed Track Background
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Color.white.opacity(0.20))
                        .frame(height: barHeight)

                    // Played Track Fill
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Color.white)
                        .frame(width: max(0, min(width, width * displayFraction)), height: barHeight)

                    // Glowing Seeking Pin Thumb (visible while seeking)
                    if isSeeking {
                        Circle()
                            .fill(Color.white)
                            .frame(width: 14, height: 14)
                            .offset(x: max(0, min(width - 14, width * displayFraction - 7)))
                            .shadow(color: Color.white.opacity(0.8), radius: 4)
                    }
                }
                .frame(height: 28)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            isSeeking = true
                            let frac = Float(value.location.x / width).clamped(to: 0...1)
                            userFraction = frac
                            onSeek(frac)
                        }
                        .onEnded { value in
                            let frac = Float(value.location.x / width).clamped(to: 0...1)
                            onSeek(frac)
                            isSeeking = false
                        }
                )
                .animation(.easeInOut(duration: 0.15), value: isSeeking)
            }
            .frame(height: 28)

            HStack {
                Text(positionFormatted)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(Color.white.opacity(0.7))

                Spacer()

                Text(durationFormatted)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(Color.white.opacity(0.7))
            }
        }
    }
}

// MARK: - Helper Container Box
private struct Box<Content: View>: View {
    let height: CGFloat
    let content: () -> Content

    init(height: CGFloat, @ViewBuilder content: @escaping () -> Content) {
        self.height = height
        self.content = content
    }

    var body: some View {
        HStack {
            Spacer()
            content()
            Spacer()
        }
        .frame(height: height)
    }
}

private extension Comparable {
    func clamped(to limits: ClosedRange<Self>) -> Self {
        min(max(self, limits.lowerBound), limits.upperBound)
    }
}
