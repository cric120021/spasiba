import SwiftUI
import Speech
import AVFoundation

// MARK: - Voice Search Manager (SFSpeechRecognizer + AVAudioEngine)
@MainActor
public final class VoiceSearchManager: NSObject, ObservableObject {
    public static let shared = VoiceSearchManager()

    private let speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let audioEngine = AVAudioEngine()

    @Published public var isRecording = false
    @Published public var spokenText = ""

    public func startRecording(onSpeech: @escaping (String) -> Void, onFinished: @escaping (String) -> Void) {
        spokenText = ""
        SFSpeechRecognizer.requestAuthorization { authStatus in
            DispatchQueue.main.async {
                guard authStatus == .authorized else { return }
                AVAudioSession.sharedInstance().requestRecordPermission { granted in
                    DispatchQueue.main.async {
                        guard granted else { return }
                        self.beginAudioEngine(onSpeech: onSpeech, onFinished: onFinished)
                    }
                }
            }
        }
    }

    private func beginAudioEngine(onSpeech: @escaping (String) -> Void, onFinished: @escaping (String) -> Void) {
        stopRecording()

        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setCategory(.playAndRecord, mode: .measurement, options: [.duckOthers, .defaultToSpeaker])
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            print("AudioSession configuration failed: \(error)")
        }

        recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
        guard let request = recognitionRequest else { return }
        request.shouldReportPartialResults = true

        let inputNode = audioEngine.inputNode
        recognitionTask = speechRecognizer?.recognitionTask(with: request) { [weak self] result, error in
            guard let self = self else { return }
            if let result = result {
                let text = result.bestTranscription.formattedString
                self.spokenText = text
                onSpeech(text)
                if result.isFinal {
                    onFinished(text)
                    self.stopRecording()
                }
            }
            if error != nil {
                self.stopRecording()
            }
        }

        let format = inputNode.outputFormat(forBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            request.append(buffer)
        }

        audioEngine.prepare()
        do {
            try audioEngine.start()
            isRecording = true
        } catch {
            print("audioEngine start error: \(error)")
        }
    }

    public func stopRecording() {
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        recognitionRequest = nil
        recognitionTask = nil
        isRecording = false

        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [])
        try? AVAudioSession.sharedInstance().setActive(true)
    }
}

// MARK: - SearchScreen View
public struct SearchScreen: View {
    @ObservedObject var player = NativePlayerManager.shared
    @ObservedObject var repository = MusicRepository.shared
    @ObservedObject var historyManager = SearchHistoryManager.shared
    @ObservedObject var db = SpasibaDatabase.shared
    @StateObject private var voiceManager = VoiceSearchManager.shared

    @State private var query: String = ""
    @State private var isSearchSubmitted: Bool = false
    @State private var isSearching: Bool = false
    @State private var searchResults: [Song] = []
    @State private var detectedArtist: ArtistSearchResult? = nil
    @State private var suggestions: [SuggestionItem] = []
    @State private var isSuggestionsLoading: Bool = false
    @State private var selectedSongForOptions: Song? = nil

    // Voice Modal State
    @State private var isVoiceListeningModalOpen: Bool = false
    @State private var liveSpokenText: String = ""
    @State private var pulseScale: CGFloat = 1.0
    @State private var showClearHistoryAlert: Bool = false

    @FocusState private var isSearchFieldFocused: Bool
    @State private var isProgrammaticQueryChange: Bool = false

    public var onArtistClick: (String) -> Void

    public init(onArtistClick: @escaping (String) -> Void = { _ in }) {
        self.onArtistClick = onArtistClick
    }

    private var screenWidth: CGFloat {
        UIScreen.main.bounds.width
    }

    private func dismissKeyboard() {
        isSearchFieldFocused = false
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }

    public var body: some View {
        ZStack {
            Color.black
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture {
                    dismissKeyboard()
                }

            VStack(spacing: 0) {
                // 1. YouTube/Spotify style Search Input Bar (Matching Android SearchScreen.kt lines 228-325)
                HStack(spacing: 12) {
                    HStack(spacing: 10) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                            .onTapGesture {
                                if !query.trimmingCharacters(in: .whitespaces).isEmpty {
                                    performSearch(query)
                                }
                            }

                        ZStack(alignment: .leading) {
                            if query.isEmpty {
                                Text("Search songs, artists...")
                                    .font(.system(size: 16))
                                    .foregroundColor(SpasibaTheme.textMuted)
                                    .lineLimit(1)
                            }

                            TextField("", text: $query)
                                .font(.system(size: 16))
                                .foregroundColor(SpasibaTheme.textStrong)
                                .accentColor(.white)
                                .focused($isSearchFieldFocused)
                                .onChange(of: query) { newQ in
                                    if isProgrammaticQueryChange {
                                        isProgrammaticQueryChange = false
                                        return
                                    }
                                    isSearchSubmitted = false
                                    fetchSuggestions(for: newQ)
                                }
                                .onSubmit {
                                    performSearch(query)
                                }
                        }

                        if !query.isEmpty {
                            Button(action: {
                                isProgrammaticQueryChange = true
                                query = ""
                                isSearchSubmitted = false
                                suggestions = []
                            }) {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(SpasibaTheme.textMuted)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .frame(height: 58)
                    .background(Color(hex: 0x181818))
                    .cornerRadius(29)
                    .overlay(
                        RoundedRectangle(cornerRadius: 29)
                            .stroke(Color.white.opacity(0.12), lineWidth: 1)
                    )

                    // Voice / Mic Button (Matching Android 58dp circle with UiSurfaceElevated & border)
                    Button(action: {
                        dismissKeyboard()
                        startVoiceSearch()
                    }) {
                        Image(systemName: "mic.fill")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                            .frame(width: 58, height: 58)
                            .background(Color(hex: 0x181818))
                            .clipShape(Circle())
                            .overlay(
                                Circle()
                                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
                            )
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                .padding(.horizontal, 16)
                .padding(.top, 10)
                .padding(.bottom, 8)
                .frame(width: screenWidth)

                // 2. Main Search Content Area
                ZStack {
                    if query.trimmingCharacters(in: .whitespaces).isEmpty {
                        emptyQueryHistoryView
                    } else if !isSearchSubmitted {
                        autocompleteSuggestionsView
                    } else {
                        searchResultsView
                    }
                }
                .frame(width: screenWidth)
            }
            .frame(width: screenWidth)

            // 3. Voice Search Modal Overlay (matching SearchScreen.kt lines 1300-1430)
            if isVoiceListeningModalOpen {
                voiceSearchModalOverlay
            }
        }
        .frame(width: screenWidth)
        .clipped()
        .sheet(item: $selectedSongForOptions) { song in
            TrackOptionsMenu(
                song: song,
                isLiked: repository.isLiked(songId: song.id),
                onDismiss: { selectedSongForOptions = nil },
                onLikeToggle: {
                    player.toggleLike(song)
                },
                onViewArtist: { artist in
                    selectedSongForOptions = nil
                    onArtistClick(artist)
                }
            )
        }
        .alert("Clear Search History?", isPresented: $showClearHistoryAlert) {
            Button("Clear All", role: .destructive) {
                historyManager.clearAllHistory()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will remove all your recently searched queries, artists, and songs.")
        }
    }

    // MARK: - Mode 1: Empty Query / History View
    private var emptyQueryHistoryView: some View {
        let hasHistory = !historyManager.playedSongs.isEmpty ||
                         !historyManager.recentArtists.isEmpty ||
                         !historyManager.textQueries.isEmpty

        return Group {
            if hasHistory {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 14) {
                        // Section Header: "Recent searches" & "Clear All"
                        HStack {
                            Text("Recent searches")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.white)

                            Spacer()

                            Button(action: { showClearHistoryAlert = true }) {
                                Text("Clear All")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(Color(hex: 0x1DB954))
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 8)
                        .frame(width: screenWidth)

                        // Top Played Songs Horizontal Rail Carousel
                        if !historyManager.playedSongs.isEmpty {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 16) {
                                    ForEach(historyManager.playedSongs) { track in
                                        VStack(alignment: .leading, spacing: 6) {
                                            SongArtworkView(song: track, cornerRadius: 12)
                                                .frame(width: 105, height: 105)

                                            Text(track.title)
                                                .font(.system(size: 13, weight: .bold))
                                                .foregroundColor(.white)
                                                .lineLimit(1)
                                                .truncationMode(.tail)
                                                .frame(width: 105, alignment: .leading)

                                            Text(track.author)
                                                .font(.system(size: 11))
                                                .foregroundColor(Color.white.opacity(0.6))
                                                .lineLimit(1)
                                                .truncationMode(.tail)
                                                .frame(width: 105, alignment: .leading)
                                        }
                                        .frame(width: 105)
                                        .contentShape(Rectangle())
                                        .onTapGesture {
                                            dismissKeyboard()
                                            player.play(song: track)
                                        }
                                        .contextMenu {
                                            Button(role: .destructive) {
                                                historyManager.deletePlayedSong(songId: track.id)
                                            } label: {
                                                Label("Remove from History", systemImage: "trash")
                                            }
                                        }
                                    }
                                }
                                .padding(.horizontal, 20)
                            }
                            .frame(width: screenWidth)
                            .padding(.bottom, 6)
                        }

                        // Visited Recent Artists List
                        if !historyManager.recentArtists.isEmpty {
                            ForEach(historyManager.recentArtists) { artistItem in
                                HStack(spacing: 16) {
                                    ZStack {
                                        if let url = artistItem.avatarUrl {
                                            SongArtworkView(url: url, cornerRadius: 25)
                                        } else {
                                            Color.white.opacity(0.12)
                                            Image(systemName: "person.fill")
                                                .foregroundColor(Color.white.opacity(0.6))
                                        }
                                    }
                                    .frame(width: 50, height: 50)
                                    .clipShape(Circle())

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(artistItem.artistName)
                                            .font(.system(size: 16, weight: .bold))
                                            .foregroundColor(.white)
                                            .lineLimit(1)
                                            .truncationMode(.tail)
                                        Text(artistItem.subtitle)
                                            .font(.system(size: 12))
                                            .foregroundColor(Color.white.opacity(0.6))
                                            .lineLimit(1)
                                            .truncationMode(.tail)
                                    }

                                    Spacer()

                                    Button(action: {
                                        historyManager.deleteRecentArtist(artistName: artistItem.artistName)
                                    }) {
                                        Image(systemName: "xmark")
                                            .font(.system(size: 14))
                                            .foregroundColor(Color.white.opacity(0.5))
                                            .frame(width: 32, height: 32)
                                    }
                                }
                                .padding(.horizontal, 20)
                                .padding(.vertical, 6)
                                .frame(width: screenWidth)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    dismissKeyboard()
                                    onArtistClick(artistItem.artistName)
                                }
                                .contextMenu {
                                    Button(role: .destructive) {
                                        historyManager.deleteRecentArtist(artistName: artistItem.artistName)
                                    } label: {
                                        Label("Remove from History", systemImage: "trash")
                                    }
                                }
                            }
                        }

                        // Text Queries History List
                        if !historyManager.textQueries.isEmpty {
                            ForEach(historyManager.textQueries, id: \.self) { historyQuery in
                                HStack(spacing: 16) {
                                    Image(systemName: "clock.arrow.circlepath")
                                        .font(.system(size: 18))
                                        .foregroundColor(Color.white.opacity(0.5))

                                    Text(historyQuery)
                                        .font(.system(size: 16))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                        .truncationMode(.tail)

                                    Spacer()

                                    Button(action: {
                                        isProgrammaticQueryChange = false
                                        query = historyQuery
                                        isSearchFieldFocused = true
                                    }) {
                                        Image(systemName: "arrow.up.backward")
                                            .font(.system(size: 16))
                                            .foregroundColor(Color.white.opacity(0.5))
                                            .frame(width: 32, height: 32)
                                    }
                                }
                                .padding(.horizontal, 20)
                                .padding(.vertical, 8)
                                .frame(width: screenWidth)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    performSearch(historyQuery)
                                }
                                .contextMenu {
                                    Button(role: .destructive) {
                                        historyManager.deleteTextQuery(historyQuery)
                                    } label: {
                                        Label("Remove from History", systemImage: "trash")
                                    }
                                }
                            }
                        }
                    }
                    .frame(width: screenWidth)
                    .padding(.bottom, 120)
                }
                .frame(width: screenWidth)
                .clipped()
                .compatScrollDismissesKeyboardImmediately()
                .simultaneousGesture(DragGesture().onChanged { _ in dismissKeyboard() })
            } else {
                VStack(spacing: 12) {
                    Spacer()
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 64))
                        .foregroundColor(Color.white.opacity(0.2))

                    Text("Search Spasiba Music")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)

                    Text("Search for songs, artists, or albums")
                        .font(.system(size: 14))
                        .foregroundColor(Color.white.opacity(0.5))
                    Spacer()
                }
                .frame(width: screenWidth)
                .padding(.bottom, 100)
            }
        }
    }

    // MARK: - Mode 2: Real-time Autocomplete Suggestions
    private var autocompleteSuggestionsView: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                if isSuggestionsLoading && suggestions.isEmpty {
                    ForEach(0..<5, id: \.self) { _ in
                        SongTileSkeleton()
                            .frame(width: screenWidth - 32)
                    }
                } else {
                    ForEach(suggestions) { item in
                        switch item {
                        case .query(let q, let isHist):
                            HStack(spacing: 16) {
                                Image(systemName: isHist ? "clock.arrow.circlepath" : "magnifyingglass")
                                    .font(.system(size: 18))
                                    .foregroundColor(Color.white.opacity(0.5))

                                Text(q)
                                    .font(.system(size: 16, weight: .medium))
                                    .foregroundColor(.white)
                                    .lineLimit(1)
                                    .truncationMode(.tail)

                                Spacer()

                                Button(action: {
                                    isProgrammaticQueryChange = false
                                    query = q
                                    isSearchFieldFocused = true
                                }) {
                                    Image(systemName: "arrow.up.backward")
                                        .font(.system(size: 16))
                                        .foregroundColor(Color.white.opacity(0.5))
                                        .frame(width: 32, height: 32)
                                }
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 12)
                            .frame(width: screenWidth)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                performSearch(q)
                            }

                        case .song(let s):
                            HStack(spacing: 14) {
                                SongArtworkView(song: s, cornerRadius: 8)
                                    .frame(width: 48, height: 48)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(s.title)
                                        .font(.system(size: 15, weight: .bold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                        .truncationMode(.tail)
                                    Text(s.author)
                                        .font(.system(size: 12))
                                        .foregroundColor(Color.white.opacity(0.6))
                                        .lineLimit(1)
                                        .truncationMode(.tail)
                                }

                                Spacer()

                                Button(action: { selectedSongForOptions = s }) {
                                    Image(systemName: "ellipsis")
                                        .font(.system(size: 16))
                                        .foregroundColor(Color.white.opacity(0.5))
                                        .rotationEffect(.degrees(90))
                                        .frame(width: 32, height: 32)
                                }
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 8)
                            .frame(width: screenWidth)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                dismissKeyboard()
                                player.play(song: s)
                                historyManager.addPlayedSong(s)
                            }

                        case .artist(let name, let avatar, let sub):
                            HStack(spacing: 14) {
                                ZStack {
                                    if let url = avatar {
                                        SongArtworkView(url: url, cornerRadius: 24)
                                    } else {
                                        Color.white.opacity(0.12)
                                        Image(systemName: "person.fill")
                                            .foregroundColor(Color.white.opacity(0.6))
                                    }
                                }
                                .frame(width: 48, height: 48)
                                .clipShape(Circle())

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(name)
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                        .truncationMode(.tail)
                                    Text(sub)
                                        .font(.system(size: 12))
                                        .foregroundColor(Color.white.opacity(0.6))
                                        .lineLimit(1)
                                        .truncationMode(.tail)
                                }

                                Spacer()
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 8)
                            .frame(width: screenWidth)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                dismissKeyboard()
                                historyManager.addRecentArtist(artistName: name, avatarUrl: avatar, subtitle: sub)
                                onArtistClick(name)
                            }
                        }
                    }
                }
            }
            .frame(width: screenWidth)
            .padding(.bottom, 140)
        }
        .frame(width: screenWidth)
        .clipped()
        .compatScrollDismissesKeyboardImmediately()
        .simultaneousGesture(DragGesture().onChanged { _ in dismissKeyboard() })
        .background(
            Color.black
                .contentShape(Rectangle())
                .onTapGesture {
                    dismissKeyboard()
                }
        )
    }

    // MARK: - Mode 3: Executed Search Results View
    private var searchResultsView: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                if isSearching {
                    ForEach(0..<6, id: \.self) { _ in
                        SongTileSkeleton()
                            .frame(width: screenWidth - 32)
                    }
                } else if searchResults.isEmpty {
                    VStack(spacing: 12) {
                        Text("No songs found for \"\(query)\"")
                            .font(.system(size: 14))
                            .foregroundColor(Color.white.opacity(0.6))
                    }
                    .frame(width: screenWidth)
                    .padding(.vertical, 40)
                } else {
                    // 1. Featured Artist Card (matching SearchScreen.kt lines 835-950)
                    if let artist = detectedArtist {
                        VStack(alignment: .leading, spacing: 14) {
                            HStack(spacing: 14) {
                                ZStack {
                                    if let url = artist.avatarUrl {
                                        SongArtworkView(url: url, cornerRadius: 32)
                                    } else {
                                        Color.white.opacity(0.12)
                                        Image(systemName: "person.fill")
                                            .foregroundColor(.white)
                                    }
                                }
                                .frame(width: 64, height: 64)
                                .clipShape(Circle())

                                VStack(alignment: .leading, spacing: 3) {
                                    Text(artist.artistName)
                                        .font(.system(size: 18, weight: .bold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                        .truncationMode(.tail)
                                    Text("Official Artist Profile • Tap to view all")
                                        .font(.system(size: 12))
                                        .foregroundColor(Color.white.opacity(0.6))
                                        .lineLimit(1)
                                        .truncationMode(.tail)
                                }

                                Spacer()

                                Image(systemName: "chevron.right")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(Color.white.opacity(0.5))
                            }
                            .contentShape(Rectangle())
                            .onTapGesture {
                                dismissKeyboard()
                                onArtistClick(artist.artistName)
                            }

                            // Shuffle & Mix Radio Action Buttons
                            HStack(spacing: 10) {
                                Button(action: {
                                    dismissKeyboard()
                                    let tracks = artist.topSongs
                                    if !tracks.isEmpty {
                                        let shuffled = tracks.shuffled()
                                        player.playQueue(shuffled, startIndex: 0, sourceLabel: "Artist: \(artist.artistName)")
                                        if let f = shuffled.first { db.addToHistory(song: f) }
                                    }
                                }) {
                                    HStack(spacing: 8) {
                                        Image(systemName: "shuffle")
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(.black)
                                        Text("Shuffle")
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(.black)
                                    }
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 42)
                                    .background(Color.white)
                                    .clipShape(Capsule())
                                }
                                .buttonStyle(PlainButtonStyle())

                                Button(action: {
                                    dismissKeyboard()
                                    let tracks = artist.topSongs
                                    if let f = tracks.first {
                                        player.playQueue(tracks, startIndex: 0, sourceLabel: "Radio: \(artist.artistName)")
                                        db.addToHistory(song: f)
                                    }
                                }) {
                                    HStack(spacing: 8) {
                                        Image(systemName: "dot.radiowaves.left.and.right")
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(.white)
                                        Text("Mix Radio")
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(.white)
                                    }
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 42)
                                    .background(Color(hex: 0x2A2A30))
                                    .clipShape(Capsule())
                                }
                                .buttonStyle(PlainButtonStyle())
                            }

                            Divider().background(Color.white.opacity(0.08))

                            // Top 3 Embedded Tracks with Ranks 1, 2, 3
                            ForEach(Array(artist.topSongs.prefix(3).enumerated()), id: \.element.id) { idx, song in
                                HStack(spacing: 12) {
                                    Text("\(idx + 1)")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(Color.white.opacity(0.6))
                                        .frame(width: 18)

                                    SongArtworkView(song: song, cornerRadius: 8)
                                        .frame(width: 46, height: 46)

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(song.title)
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(song.id == player.currentSong?.id ? Color(hex: 0x1DB954) : .white)
                                            .lineLimit(1)
                                            .truncationMode(.tail)
                                        Text(song.author.isEmpty ? "Spasiba Music" : song.author)
                                            .font(.system(size: 12))
                                            .foregroundColor(Color.white.opacity(0.6))
                                            .lineLimit(1)
                                            .truncationMode(.tail)
                                    }

                                    Spacer()

                                    Button(action: { selectedSongForOptions = song }) {
                                        Image(systemName: "ellipsis")
                                            .font(.system(size: 16))
                                            .foregroundColor(Color.white.opacity(0.5))
                                            .rotationEffect(.degrees(90))
                                            .frame(width: 32, height: 32)
                                    }
                                }
                                .padding(.horizontal, 6)
                                .padding(.vertical, 6)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    dismissKeyboard()
                                    let tracks = artist.topSongs
                                    player.playQueue(tracks, startIndex: idx, sourceLabel: "Artist: \(artist.artistName)")
                                    db.addToHistory(song: song)
                                }
                            }
                        }
                        .padding(16)
                        .background(Color(hex: 0x181818))
                        .cornerRadius(20)
                        .overlay(
                            RoundedRectangle(cornerRadius: 20)
                                .stroke(Color.white.opacity(0.08), lineWidth: 1)
                        )
                        .frame(width: screenWidth - 32)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 16)
                    }

                    // 2. Normal Song Top Result Card (matching SearchScreen.kt lines 1060-1205)
                    else if let topSong = searchResults.first {
                        VStack(alignment: .leading, spacing: 14) {
                            // Top Row: Thumbnail, Title, Duration, 3-dots
                            HStack(spacing: 14) {
                                SongArtworkView(song: topSong, cornerRadius: 8)
                                    .frame(width: 56, height: 56)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(topSong.title)
                                        .font(.system(size: 17, weight: .bold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                        .truncationMode(.tail)

                                    let durFormatted = "\(topSong.durationSeconds / 60):\(String(format: "%02d", topSong.durationSeconds % 60))"
                                    let authorStr = (topSong.author.isEmpty || topSong.author == "Artist") ? "Spasiba Music" : topSong.author
                                    Text("\(authorStr)\(topSong.durationSeconds > 0 ? " • \(durFormatted)" : "")")
                                        .font(.system(size: 13))
                                        .foregroundColor(Color.white.opacity(0.6))
                                        .lineLimit(1)
                                        .truncationMode(.tail)
                                }

                                Spacer()

                                Button(action: { selectedSongForOptions = topSong }) {
                                    Image(systemName: "ellipsis")
                                        .font(.system(size: 16))
                                        .foregroundColor(Color.white.opacity(0.7))
                                        .rotationEffect(.degrees(90))
                                        .frame(width: 32, height: 32)
                                }
                            }
                            .contentShape(Rectangle())
                            .onTapGesture {
                                dismissKeyboard()
                                player.playQueue(searchResults, startIndex: 0)
                                db.addToHistory(song: topSong)
                            }

                            // Action Buttons: [ ▶ Play ] and [ ≡+ Save ]
                            HStack(spacing: 10) {
                                // Solid White Play Pill Button
                                Button(action: {
                                    dismissKeyboard()
                                    player.playQueue(searchResults, startIndex: 0)
                                    db.addToHistory(song: topSong)
                                }) {
                                    HStack(spacing: 6) {
                                        Image(systemName: "play.fill")
                                            .font(.system(size: 14))
                                            .foregroundColor(.black)
                                        Text("Play")
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(.black)
                                    }
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 42)
                                    .background(Color.white)
                                    .clipShape(Capsule())
                                }
                                .buttonStyle(PlainButtonStyle())

                                // Dark Translucent Save Pill Button
                                Button(action: {
                                    selectedSongForOptions = topSong
                                }) {
                                    HStack(spacing: 6) {
                                        Image(systemName: "plus.rectangle.on.rectangle")
                                            .font(.system(size: 14))
                                            .foregroundColor(.white)
                                        Text("Save")
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(.white)
                                    }
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 42)
                                    .background(Color.white.opacity(0.08))
                                    .clipShape(Capsule())
                                    .overlay(
                                        Capsule()
                                            .stroke(Color.white.opacity(0.2), lineWidth: 1)
                                    )
                                }
                                .buttonStyle(PlainButtonStyle())
                            }
                        }
                        .padding(14)
                        .background(Color(hex: 0x181818))
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18)
                                .stroke(Color.white.opacity(0.08), lineWidth: 1)
                        )
                        .frame(width: screenWidth - 32)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 16)

                        if searchResults.count > 1 {
                            HStack {
                                Text("MORE RESULTS")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(Color.white.opacity(0.6))
                                    .tracking(1.4)
                                Spacer()
                            }
                            .padding(.horizontal, 16)
                            .padding(.top, 4)
                            .padding(.bottom, 8)
                            .frame(width: screenWidth)
                        }
                    }

                    // 3. Search Results List (SongTiles - clean non-slidable rows)
                    let remainingList = detectedArtist != nil ? searchResults : Array(searchResults.dropFirst())
                    ForEach(Array(remainingList.enumerated()), id: \.element.id) { index, song in
                        SongTile(
                            song: song,
                            isPlaying: song.id == player.currentSong?.id,
                            isLiked: repository.isLiked(songId: song.id),
                            enableSwipe: false,
                            onSongClick: {
                                dismissKeyboard()
                                let globalIdx = searchResults.firstIndex(where: { $0.id == song.id }) ?? index
                                player.playQueue(searchResults, startIndex: globalIdx)
                                historyManager.addPlayedSong(song)
                            },
                            onLikeClick: { player.toggleLike(song) },
                            onOptionsClick: { selectedSongForOptions = song }
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
        .compatScrollDismissesKeyboardImmediately()
        .simultaneousGesture(DragGesture().onChanged { _ in dismissKeyboard() })
        .onAppear {
            dismissKeyboard()
        }
        .background(
            Color.black
                .contentShape(Rectangle())
                .onTapGesture {
                    dismissKeyboard()
                }
        )
    }

    // MARK: - Voice Search Modal Overlay (matching SearchScreen.kt lines 1300-1430)
    private var voiceSearchModalOverlay: some View {
        ZStack {
            Color.black.opacity(0.7).ignoresSafeArea()
                .onTapGesture {
                    closeVoiceSearch()
                }

            VStack(spacing: 0) {
                // Top Close Bar
                HStack {
                    Spacer()
                    Button(action: {
                        closeVoiceSearch()
                    }) {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 32, height: 32)
                            .background(Color.white.opacity(0.08))
                            .clipShape(Circle())
                    }
                }

                Spacer().frame(height: 10)

                // Hero Animated Pulsing Mic Circle
                ZStack {
                    // Outer Pulsing Glow Ring
                    Circle()
                        .fill(Color(hex: 0xEF4444).opacity(0.20))
                        .frame(width: 120 * pulseScale, height: 120 * pulseScale)

                    // Inner Pulsing Glow Ring
                    Circle()
                        .fill(Color(hex: 0xEF4444).opacity(0.40))
                        .frame(width: 90 * pulseScale, height: 90 * pulseScale)

                    // Core Mic Button
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [Color(hex: 0xEF4444), Color(hex: 0xDC2626)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        .frame(width: 76, height: 76)
                        .overlay(
                            Image(systemName: "mic.fill")
                                .font(.system(size: 36))
                                .foregroundColor(.white)
                        )
                }
                .frame(width: 140, height: 140)

                Spacer().frame(height: 20)

                // Real-time Spoken Words / Headline
                Text(liveSpokenText.isEmpty ? "Listening..." : liveSpokenText)
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)

                Spacer().frame(height: 8)

                // Subtitle / Prompt
                Text(liveSpokenText.isEmpty ? "Recite a song name, artist, or lyric to search" : "Reciting live...")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(Color(hex: 0x1DB954))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)

                Spacer().frame(height: 10)
            }
            .padding(24)
            .background(Color(hex: 0x141414))
            .cornerRadius(28)
            .overlay(
                RoundedRectangle(cornerRadius: 28)
                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
            )
            .frame(width: min(screenWidth - 48, 320))
            .padding(.horizontal, 24)
        }
        .frame(width: screenWidth)
        .clipped()
        .onAppear {
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) {
                pulseScale = 1.22
            }
        }
    }

    // MARK: - Actions
    private func startVoiceSearch() {
        dismissKeyboard()
        liveSpokenText = ""
        isVoiceListeningModalOpen = true
        voiceManager.startRecording(
            onSpeech: { text in
                self.liveSpokenText = text
            },
            onFinished: { text in
                self.closeVoiceSearch()
                self.performSearch(text)
            }
        )
    }

    private func closeVoiceSearch() {
        voiceManager.stopRecording()
        isVoiceListeningModalOpen = false
    }

    private func fetchSuggestions(for q: String) {
        let trimmed = q.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            suggestions = []
            return
        }

        isSuggestionsLoading = true
        Task {
            let res = await YouTubeMusicApi.shared.fetchSearchSuggestions(input: trimmed)
            await MainActor.run {
                self.suggestions = res
                self.isSuggestionsLoading = false
            }
        }
    }

    private func performSearch(_ q: String) {
        let trimmed = q.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        dismissKeyboard()
        isProgrammaticQueryChange = true
        self.query = trimmed
        self.isSearchSubmitted = true
        self.isSearching = true
        historyManager.addTextQuery(trimmed)

        Task {
            let unified = await YouTubeMusicApi.shared.searchUnified(query: trimmed)
            await MainActor.run {
                self.searchResults = unified.songs
                self.detectedArtist = unified.detectedArtist
                self.isSearching = false
            }
        }
    }
}
