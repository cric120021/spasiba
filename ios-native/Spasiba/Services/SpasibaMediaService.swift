import Foundation
import AVFoundation
import MediaPlayer
import UIKit

public final class SpasibaMediaService: NSObject {
    public static let shared = SpasibaMediaService()

    public static let NOTIFICATION_ID = 2002
    public static let CHANNEL_ID = "spasiba_native2_channel"

    public static let ACTION_PLAY = "app.spasiba.nativeapp2.ACTION_PLAY"
    public static let ACTION_PAUSE = "app.spasiba.nativeapp2.ACTION_PAUSE"
    public static let ACTION_PREVIOUS = "app.spasiba.nativeapp2.ACTION_PREVIOUS"
    public static let ACTION_NEXT = "app.spasiba.nativeapp2.ACTION_NEXT"
    public static let ACTION_STOP = "app.spasiba.nativeapp2.ACTION_STOP"

    private let tag = "SpasibaMediaService"
    private var isConfigured = false
    private var cachedArtworkUrl: String?
    private var cachedArtwork: MPMediaItemArtwork?
    private var currentSongId: String?
    private var isPlayingState: Bool = false

    public var onPlayAction: (() -> Void)?
    public var onPauseAction: (() -> Void)?
    public var onTogglePlayPauseAction: (() -> Void)?
    public var onNextAction: (() -> Void)?
    public var onPreviousAction: (() -> Void)?
    public var onSeekAction: ((Double) -> Void)?
    public var onStopAction: (() -> Void)?

    private override init() {
        super.init()
    }

    public func startService() {
        guard !isConfigured else { return }
        configureAudioSession()
        setupRemoteCommands()
        isConfigured = true
        print("[\(tag)] SpasibaMediaService started & background audio session configured.")
    }

    private func configureAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [])
            try session.setActive(true)
        } catch {
            print("[\(tag)] Failed to configure AVAudioSession: \(error)")
        }
    }

    private func setupRemoteCommands() {
        let commandCenter = MPRemoteCommandCenter.shared()

        commandCenter.playCommand.isEnabled = true
        commandCenter.playCommand.addTarget { [weak self] _ in
            self?.onPlayAction?()
            return .success
        }

        commandCenter.pauseCommand.isEnabled = true
        commandCenter.pauseCommand.addTarget { [weak self] _ in
            self?.onPauseAction?()
            return .success
        }

        commandCenter.togglePlayPauseCommand.isEnabled = true
        commandCenter.togglePlayPauseCommand.addTarget { [weak self] _ in
            self?.onTogglePlayPauseAction?()
            return .success
        }

        commandCenter.nextTrackCommand.isEnabled = true
        commandCenter.nextTrackCommand.addTarget { [weak self] _ in
            self?.onNextAction?()
            return .success
        }

        commandCenter.previousTrackCommand.isEnabled = true
        commandCenter.previousTrackCommand.addTarget { [weak self] _ in
            self?.onPreviousAction?()
            return .success
        }

        commandCenter.changePlaybackPositionCommand.isEnabled = true
        commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let positionEvent = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            self?.onSeekAction?(positionEvent.positionTime)
            return .success
        }

        commandCenter.stopCommand.isEnabled = true
        commandCenter.stopCommand.addTarget { [weak self] _ in
            self?.onStopAction?()
            return .success
        }
    }

    public func updatePlayback(
        song: Song?,
        isPlaying: Bool,
        positionSeconds: Double
    ) {
        guard let song = song else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }

        self.isPlayingState = isPlaying
        var nowPlayingInfo = [String: Any]()
        nowPlayingInfo[MPMediaItemPropertyTitle] = song.title
        nowPlayingInfo[MPMediaItemPropertyArtist] = song.author
        nowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = Double(song.durationSeconds)
        nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = positionSeconds
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? 1.0 : 0.0

        if song.thumbnail == cachedArtworkUrl, let artwork = cachedArtwork {
            nowPlayingInfo[MPMediaItemPropertyArtwork] = artwork
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
        } else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
            fetchArtwork(urlString: song.thumbnail) { [weak self] artwork in
                guard let self = self, self.currentSongId == song.id else { return }
                self.cachedArtworkUrl = song.thumbnail
                self.cachedArtwork = artwork
                nowPlayingInfo[MPMediaItemPropertyArtwork] = artwork
                MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
            }
        }

        self.currentSongId = song.id
    }

    private func fetchArtwork(urlString: String, completion: @escaping (MPMediaItemArtwork?) -> Void) {
        guard let url = URL(string: urlString) else {
            completion(nil)
            return
        }

        URLSession.shared.dataTask(with: url) { data, _, _ in
            guard let data = data, let image = UIImage(data: data) else {
                completion(nil)
                return
            }

            let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            DispatchQueue.main.async {
                completion(artwork)
            }
        }.resume()
    }

    public func stopService() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        do {
            try AVAudioSession.sharedInstance().setActive(false)
        } catch {
            print("[\(tag)] Error deactivating audio session: \(error)")
        }
        print("[\(tag)] SpasibaMediaService stopped.")
    }
}
