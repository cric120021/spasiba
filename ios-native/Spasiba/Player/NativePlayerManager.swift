import Foundation
import AVFoundation
import MediaPlayer
import Combine

public final class NativePlayerManager: ObservableObject {
    public static let shared = NativePlayerManager()

    private var player: AVPlayer?
    private var timeObserver: Any?
    private var cancellables = Set<AnyCancellable>()
    private var sleepTimer: Timer?
    private var currentArtwork: MPMediaItemArtwork?
    private var currentArtworkSongId: String?

    @Published public private(set) var currentSong: Song?
    @Published public var isPlaying: Bool = false
    @Published public var isBuffering: Bool = false
    @Published public var currentTime: Double = 0.0
    @Published public var duration: Double = 0.0
    @Published public var playbackProgress: Double = 0.0

    public var positionMs: Int64 {
        Int64(currentTime * 1000)
    }

    public var durationMs: Int64 {
        Int64(duration * 1000)
    }

    public var likedSongIds: Set<String> {
        SpasibaDatabase.shared.likedSongIds
    }

    public var smartQueue: SmartQueueEngine { queueEngine }
    public var repository: MusicRepository { MusicRepository.shared }

    public func playNextInQueue(_ song: Song) {
        queueEngine.addNext(song)
        persistQueue()
    }

    // Video & Extra Controls
    @Published public var isVideoMode: Bool = false
    @Published public var videoQuality: String = "1080p"
    @Published public var availableVideoQualities: [String] = ["1080p", "720p", "480p", "360p"]
    @Published public var videoAspectRatio: Double = 16.0 / 9.0
    @Published public var playbackSpeed: Float = 1.0
    @Published public var currentSleepTimerMinutes: Int = 0

    // Lyrics
    @Published public var syncedLyrics: [LyricsLine] = []
    @Published public var isLyricsLoading: Bool = false

    public let queueEngine = SmartQueueEngine.shared

    public func addToQueue(_ song: Song) {
        queueEngine.addToQueue(song)
        persistQueue()
    }

    public var avPlayer: AVPlayer? { player }
    public var queue: [Song] { queueEngine.queue }
    public var isLoading: Bool { isBuffering }

    private var radioQueueTask: Task<Void, Never>?

    private init() {
        configureAudioSession()
        setupAudioBridge()
        setupRemoteCommandCenter()
        setupQueueObserver()
        setupQueueSync()
        restoreQueueSnapshot()
    }

    private var failedVideoIds = Set<String>()
    private var isResolvingFallback: Bool = false

    private func setupAudioBridge() {
        let bridge = HeadlessAudioBridge.shared
        bridge.onStateChange = { [weak self] isPlaying, isBuffering in
            guard let self = self else { return }
            self.isPlaying = isPlaying
            self.isBuffering = isBuffering
            self.updateNowPlayingInfo()
        }

        bridge.onTimeUpdate = { [weak self] currentTime, duration in
            guard let self = self else { return }
            self.currentTime = currentTime
            if duration > 0 {
                self.duration = duration
                self.playbackProgress = min(1.0, currentTime / duration)
            }
            self.updateNowPlayingInfo()
        }

        bridge.onTrackEnded = { [weak self] in
            guard let self = self else { return }
            NSLog("[NativePlayerManager] Track finished via HeadlessAudioBridge")
            self.handleTrackFinished()
        }

        bridge.onError = { [weak self] code in
            guard let self = self else { return }
            self.handlePlaybackError(code: code)
        }

        bridge.onNextTrack = { [weak self] in
            guard let self = self else { return }
            NSLog("[NativePlayerManager] Bridge received nextTrack event")
            DispatchQueue.main.async {
                self.playNext()
            }
        }

        bridge.onPreviousTrack = { [weak self] in
            guard let self = self else { return }
            NSLog("[NativePlayerManager] Bridge received prevTrack event")
            DispatchQueue.main.async {
                self.playPrevious()
            }
        }
    }

    private func handlePlaybackError(code: Int) {
        guard let song = currentSong else {
            self.isBuffering = false
            return
        }
        NSLog("[NativePlayerManager] Playback error code: %d for song: %@ (ID: %@)", code, song.title, song.id)
        self.isBuffering = false

        if (code == 150 || code == 152 || code == 101 || code == 100) && !isResolvingFallback {
            isResolvingFallback = true
            failedVideoIds.insert(song.id)
            NSLog("[NativePlayerManager] Searching fallback video for restricted track: %@", song.title)

            Task {
                let query = "\(song.title) \(song.author) official video"
                let candidates = await YouTubeMusicApi.searchSongs(query: query)
                if let alternative = candidates.first(where: { $0.id != song.id && !self.failedVideoIds.contains($0.id) }) {
                    NSLog("[NativePlayerManager] Found alternative playable ID: %@ for: %@", alternative.id, song.title)
                    await MainActor.run {
                        self.isResolvingFallback = false
                        self.isBuffering = true
                        HeadlessAudioBridge.shared.loadAndPlay(videoId: alternative.id)
                    }
                    return
                }

                await MainActor.run {
                    self.isResolvingFallback = false
                    NSLog("[NativePlayerManager] No alternative found, advancing to next song")
                    self.playNext()
                }
            }
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
                self?.playNext()
            }
        }
    }

    private func setupQueueSync() {
        queueEngine.objectWillChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
    }

    private func configureAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, policy: .longFormAudio, options: [])
            try session.setActive(true)
        } catch {
            print("Failed to activate AVAudioSession: \(error)")
        }
    }

    private func setupQueueObserver() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleTrackFinished),
            name: .AVPlayerItemDidPlayToEndTime,
            object: nil
        )
    }

    private func persistQueue() {
        QueueSnapshotCache.shared.saveSnapshot(
            songs: queueEngine.queue,
            currentIndex: queueEngine.currentIndex,
            positionMs: Int64(currentTime * 1000)
        )
    }

    private func restoreQueueSnapshot() {
        guard let snapshot = QueueSnapshotCache.shared.restoreSnapshot(),
              !snapshot.songs.isEmpty,
              snapshot.currentIndex >= 0,
              snapshot.currentIndex < snapshot.songs.count else {
            return
        }
        queueEngine.setQueue(snapshot.songs, startIndex: snapshot.currentIndex)
        if let current = queueEngine.currentSong {
            self.currentSong = current
            self.duration = max(1.0, Double(current.durationSeconds))
            if snapshot.positionMs > 0 {
                self.currentTime = Double(snapshot.positionMs) / 1000.0
            }
            NSLog("[NativePlayerManager] Restored queue snapshot: %d tracks, index=%d", snapshot.songs.count, snapshot.currentIndex)
        }
    }

    public func maybeReplenishRadioQueue(for song: Song? = nil) {
        let currentQueue = queueEngine.queue
        let curIndex = queueEngine.currentIndex
        guard !currentQueue.isEmpty, curIndex >= 0 else { return }

        let remainingAhead = currentQueue.count - 1 - curIndex
        if remainingAhead <= 3 && currentQueue.count < 15 {
            let currentTrack = song ?? currentQueue[curIndex]
            radioQueueTask?.cancel()
            radioQueueTask = Task {
                let candidateTracks = await QuickPicksEngine.generateRadioQueue(seedSong: currentTrack)
                guard !Task.isCancelled, !candidateTracks.isEmpty else { return }
                await MainActor.run {
                    let activeHistoryAndCurrent = Array(self.queueEngine.queue.prefix(curIndex + 1))
                    let deduplicatedCandidates = candidateTracks.filter { cand in
                        !activeHistoryAndCurrent.contains(where: { $0.id == cand.id })
                    }
                    let updatedQueue = Array((activeHistoryAndCurrent + deduplicatedCandidates)
                        .prefix(curIndex + 1 + SmartQueueEngine.UPCOMING_QUEUE_TARGET))
                    if updatedQueue.count > self.queueEngine.queue.count {
                        self.queueEngine.setQueue(updatedQueue, startIndex: curIndex)
                        self.persistQueue()
                        NSLog("[NativePlayerManager] RADIO QUEUE TOP-UP: Queue capped to %d tracks", updatedQueue.count)
                    }
                }
            }
        }
    }

    public func replenishRadioQueue(for song: Song) {
        maybeReplenishRadioQueue(for: song)
    }

    public func playSingleSongWithRadio(_ clickedSong: Song, sourceLabel: String = "Spasiba Radio") {
        queueEngine.setQueue([clickedSong], startIndex: 0)
        playInternal(song: clickedSong)
        persistQueue()

        radioQueueTask?.cancel()
        radioQueueTask = Task {
            let radioTracks = await YouTubeMusicApi.fetchYouTubeMusicNextRadioQueue(videoId: clickedSong.id)
            guard !Task.isCancelled, !radioTracks.isEmpty else { return }
            await MainActor.run {
                let filtered = radioTracks.filter { $0.id != clickedSong.id }
                let newQueue = Array(([clickedSong] + filtered).prefix(SmartQueueEngine.UPCOMING_QUEUE_TARGET + 1))
                self.queueEngine.setQueue(newQueue, startIndex: 0)
                self.persistQueue()
                NSLog("[NativePlayerManager] PLAY SINGLE SONG RADIO: Queue set to %d YouTube Automix tracks", newQueue.count)
            }
        }
    }

    public func playQueue(_ songs: [Song], startIndex: Int = 0, sourceLabel: String? = nil) {
        guard !songs.isEmpty else { return }
        let validIndex = max(0, min(startIndex, songs.count - 1))
        queueEngine.setQueue(songs, startIndex: validIndex)
        let selected = songs[validIndex]
        playInternal(song: selected)
        persistQueue()
        if (songs.count - 1 - validIndex) <= 3 {
            maybeReplenishRadioQueue(for: selected)
        }
    }

    public func toggleLike(_ song: Song) {
        SessionBrainManager.shared.recordLike(song: song)
        SpasibaDatabase.shared.toggleLike(song: song)
    }

    public var database: SpasibaDatabase {
        return SpasibaDatabase.shared
    }

    public func play(song: Song) {
        if queueEngine.queue.isEmpty || !queueEngine.queue.contains(where: { $0.id == song.id }) {
            playSingleSongWithRadio(song)
        } else if let idx = queueEngine.queue.firstIndex(where: { $0.id == song.id }) {
            queueEngine.setCurrentIndex(idx)
            playInternal(song: song)
            persistQueue()
            maybeReplenishRadioQueue(for: song)
        }
    }

    private func playInternal(song: Song) {
        self.currentSong = song
        self.isBuffering = true
        self.currentTime = 0.0
        self.duration = max(1.0, Double(song.durationSeconds))
        SessionBrainManager.shared.recordPlayStart(song: song)

        // Reset lyrics for new song
        self.syncedLyrics = []

        // 1. Check if track is cached locally on disk
        if let localFile = SpasibaAudioCacheManager.shared.findCachedAudioFile(videoId: song.id) {
            NSLog("[NativePlayerManager] Playing from local cache: %@", localFile.path)
            self.setupPlayer(with: localFile, headers: [:])
            return
        }

        // 2. Dispatch online stream to HeadlessAudioBridge (bypasses 403 Forbidden & handles YouTube PO tokens)
        NSLog("[NativePlayerManager] Streaming via HeadlessAudioBridge for: %@ (%@)", song.title, song.id)
        if player != nil {
            player?.pause()
            player = nil
        }
        HeadlessAudioBridge.shared.loadAndPlay(videoId: song.id)
        self.updateNowPlayingInfo()
    }

    private func setupPlayer(with url: URL, headers: [String: String]) {
        // Clean up previous observer
        if let observer = timeObserver {
            player?.removeTimeObserver(observer)
            timeObserver = nil
        }

        let asset = AVURLAsset(url: url, options: ["AVURLAssetHTTPHeaderFieldsKey": headers])
        let playerItem = AVPlayerItem(asset: asset)
        
        if player == nil {
            player = AVPlayer(playerItem: playerItem)
            player?.automaticallyWaitsToMinimizeStalling = true
        } else {
            player?.replaceCurrentItem(with: playerItem)
        }

        // Apply playback speed
        player?.rate = isPlaying ? playbackSpeed : 0.0

        // Add periodic time observer for 4x/sec scrubber updates
        let interval = CMTime(seconds: 0.25, preferredTimescale: 600)
        timeObserver = player?.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self = self else { return }
            let current = CMTimeGetSeconds(time)
            self.currentTime = current
            if self.duration > 0 {
                self.playbackProgress = min(1.0, current / self.duration)
            }
            self.updateNowPlayingInfo()
        }

        player?.play()
        if playbackSpeed != 1.0 {
            player?.rate = playbackSpeed
        }
        self.isPlaying = true
        self.isBuffering = false
        self.updateNowPlayingInfo()
    }

    public func play() {
        if player != nil {
            player?.play()
            if playbackSpeed != 1.0 {
                player?.rate = playbackSpeed
            }
        } else {
            HeadlessAudioBridge.shared.play()
        }
        self.isPlaying = true
        updateNowPlayingInfo()
    }

    public func pause() {
        player?.pause()
        HeadlessAudioBridge.shared.pause()
        self.isPlaying = false
        updateNowPlayingInfo()
    }

    public func togglePlayPause() {
        if isPlaying {
            pause()
        } else {
            play()
        }
    }

    public func seek(to seconds: Double) {
        if let player = player {
            let targetTime = CMTime(seconds: seconds, preferredTimescale: 600)
            player.seek(to: targetTime, toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] _ in
                guard let self = self else { return }
                self.currentTime = seconds
                if self.isPlaying && self.playbackSpeed != 1.0 {
                    self.player?.rate = self.playbackSpeed
                }
                self.updateNowPlayingInfo()
            }
        } else {
            HeadlessAudioBridge.shared.seek(to: seconds)
            self.currentTime = seconds
            self.updateNowPlayingInfo()
        }
    }

    public func seekTo(_ ms: Int64) {
        seek(to: Double(ms) / 1000.0)
    }

    public func seekRelative(_ seconds: Double) {
        let target = max(0, min(duration, currentTime + seconds))
        seek(to: target)
    }

    public func setPlaybackSpeed(_ speed: Float) {
        self.playbackSpeed = speed
        if isPlaying {
            player?.rate = speed
        }
        HeadlessAudioBridge.shared.setPlaybackRate(speed)
    }

    public func setSleepTimer(minutes: Int) {
        self.currentSleepTimerMinutes = minutes
        sleepTimer?.invalidate()
        sleepTimer = nil
        if minutes > 0 {
            sleepTimer = Timer.scheduledTimer(withTimeInterval: Double(minutes * 60), repeats: false) { [weak self] _ in
                DispatchQueue.main.async {
                    self?.player?.pause()
                    self?.isPlaying = false
                    self?.currentSleepTimerMinutes = 0
                }
            }
        }
    }

    public func setVideoQuality(_ quality: String) {
        self.videoQuality = quality
    }

    public func disableVideoMode() {
        self.isVideoMode = false
    }

    public func enableVideoMode() {
        self.isVideoMode = true
    }

    public func fetchLyricsForCurrentSong() {
        guard let song = currentSong else { return }
        isLyricsLoading = true
        Task {
            let fetched = await LrcLyricsFetcher.shared.fetchLyrics(
                trackName: song.title,
                artistName: song.author,
                durationSeconds: song.durationSeconds
            )
            await MainActor.run {
                self.syncedLyrics = fetched
                self.isLyricsLoading = false
            }
        }
    }

    public func playNext(isUserSkip: Bool = true) {
        if isUserSkip, let current = currentSong {
            SessionBrainManager.shared.recordSkip(song: current)
        }

        if let nextSong = queueEngine.advanceNext() {
            playInternal(song: nextSong)
            persistQueue()
            maybeReplenishRadioQueue()
        } else if let current = currentSong {
            // Reached end of queue or single track playing: immediately fetch radio and advance
            radioQueueTask?.cancel()
            radioQueueTask = Task {
                let candidateTracks = await QuickPicksEngine.generateRadioQueue(seedSong: current)
                guard !Task.isCancelled, !candidateTracks.isEmpty else { return }
                await MainActor.run {
                    let filtered = candidateTracks.filter { $0.id != current.id }
                    if !filtered.isEmpty {
                        self.queueEngine.appendTracks(filtered)
                        self.persistQueue()
                        if let next = self.queueEngine.advanceNext() {
                            self.playInternal(song: next)
                        }
                    }
                }
            }
        }
    }

    public func playPrevious() {
        if currentTime > 3.0 {
            seek(to: 0)
        } else if let prevSong = queueEngine.retreatPrevious() {
            playInternal(song: prevSong)
            persistQueue()
        } else {
            seek(to: 0)
        }
    }

    @objc private func handleTrackFinished() {
        NSLog("[NativePlayerManager] Track finished")
        if queueEngine.repeatMode == .one {
            seek(to: 0)
            if player != nil {
                player?.play()
                isPlaying = true
            } else if let song = currentSong {
                HeadlessAudioBridge.shared.loadAndPlay(videoId: song.id)
            }
        } else {
            playNext(isUserSkip: false)
        }
    }

    // MARK: - Lock Screen & Control Center Integration
    private func setupRemoteCommandCenter() {
        UIApplication.shared.beginReceivingRemoteControlEvents()
        let commandCenter = MPRemoteCommandCenter.shared()

        // Clean any existing targets to avoid duplicate triggers
        commandCenter.playCommand.removeTarget(nil)
        commandCenter.pauseCommand.removeTarget(nil)
        commandCenter.togglePlayPauseCommand.removeTarget(nil)
        commandCenter.nextTrackCommand.removeTarget(nil)
        commandCenter.previousTrackCommand.removeTarget(nil)
        commandCenter.changePlaybackPositionCommand.removeTarget(nil)
        commandCenter.skipForwardCommand.removeTarget(nil)
        commandCenter.skipBackwardCommand.removeTarget(nil)
        commandCenter.seekForwardCommand.removeTarget(nil)
        commandCenter.seekBackwardCommand.removeTarget(nil)

        // Completely disable skip interval commands so iOS shows Next / Previous track buttons
        commandCenter.skipForwardCommand.preferredIntervals = []
        commandCenter.skipForwardCommand.isEnabled = false
        commandCenter.skipBackwardCommand.preferredIntervals = []
        commandCenter.skipBackwardCommand.isEnabled = false
        commandCenter.seekForwardCommand.isEnabled = false
        commandCenter.seekBackwardCommand.isEnabled = false

        commandCenter.playCommand.isEnabled = true
        commandCenter.playCommand.addTarget { [weak self] _ in
            guard let self = self else { return .commandFailed }
            NSLog("[NativePlayerManager] Remote command: playCommand")
            DispatchQueue.main.async {
                self.play()
            }
            return .success
        }

        commandCenter.pauseCommand.isEnabled = true
        commandCenter.pauseCommand.addTarget { [weak self] _ in
            guard let self = self else { return .commandFailed }
            NSLog("[NativePlayerManager] Remote command: pauseCommand")
            DispatchQueue.main.async {
                self.pause()
            }
            return .success
        }

        commandCenter.togglePlayPauseCommand.isEnabled = true
        commandCenter.togglePlayPauseCommand.addTarget { [weak self] _ in
            guard let self = self else { return .commandFailed }
            NSLog("[NativePlayerManager] Remote command: togglePlayPauseCommand")
            DispatchQueue.main.async {
                self.togglePlayPause()
            }
            return .success
        }

        commandCenter.nextTrackCommand.isEnabled = true
        commandCenter.nextTrackCommand.addTarget { [weak self] _ in
            guard let self = self else { return .commandFailed }
            NSLog("[NativePlayerManager] Remote command: nextTrackCommand")
            DispatchQueue.main.async {
                self.playNext()
            }
            return .success
        }

        commandCenter.previousTrackCommand.isEnabled = true
        commandCenter.previousTrackCommand.addTarget { [weak self] _ in
            guard let self = self else { return .commandFailed }
            NSLog("[NativePlayerManager] Remote command: previousTrackCommand")
            DispatchQueue.main.async {
                self.playPrevious()
            }
            return .success
        }

        commandCenter.changePlaybackPositionCommand.isEnabled = true
        commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let self = self, let positionEvent = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            DispatchQueue.main.async {
                self.seek(to: positionEvent.positionTime)
            }
            return .success
        }
    }

    private func updateNowPlayingInfo() {
        guard let song = currentSong else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }

        var nowPlayingInfo = [String: Any]()
        nowPlayingInfo[MPMediaItemPropertyTitle] = song.title
        nowPlayingInfo[MPMediaItemPropertyArtist] = song.author
        nowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = duration
        nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? Double(playbackSpeed) : 0.0
        nowPlayingInfo[MPNowPlayingInfoPropertyDefaultPlaybackRate] = 1.0
        nowPlayingInfo[MPNowPlayingInfoPropertyMediaType] = MPNowPlayingInfoMediaType.audio.rawValue
        
        let qIndex = max(1, queueEngine.currentIndex + 1)
        let qCount = max(qIndex + 5, queueEngine.queue.count + 2)
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackQueueIndex] = qIndex
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackQueueCount] = qCount

        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.skipForwardCommand.preferredIntervals = []
        commandCenter.skipForwardCommand.isEnabled = false
        commandCenter.skipBackwardCommand.preferredIntervals = []
        commandCenter.skipBackwardCommand.isEnabled = false
        commandCenter.nextTrackCommand.isEnabled = true
        commandCenter.previousTrackCommand.isEnabled = true

        // Cached artwork attachment
        if let artwork = currentArtwork, currentArtworkSongId == song.id {
            nowPlayingInfo[MPMediaItemPropertyArtwork] = artwork
        } else if currentArtworkSongId != song.id {
            currentArtworkSongId = song.id
            currentArtwork = nil
            if let thumbUrl = URL(string: song.highResThumbnail) {
                URLSession.shared.dataTask(with: thumbUrl) { [weak self] data, _, _ in
                    guard let self = self, let data = data, let image = UIImage(data: data) else { return }
                    let art = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
                    DispatchQueue.main.async {
                        self.currentArtwork = art
                        self.updateNowPlayingInfo()
                    }
                }.resume()
            }
        }

        MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
    }
}

