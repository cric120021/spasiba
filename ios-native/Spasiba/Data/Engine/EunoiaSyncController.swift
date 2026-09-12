import Foundation

public final class EunoiaSyncController {
    private let tag = "EunoiaSyncController"
    public let playerManager: NativePlayerManager
    private let socketClient: EunoiaSocketClient
    public var isApplyingRemoteCommand: Bool = false
    public var lastRemoteAppliedSongId: String? = nil

    private static let DRIFT_IGNORE_MS: Int64 = 300      // +-300ms humanly imperceptible threshold
    private static let DRIFT_SMOOTH_MS: Int64 = 1500     // 300ms - 1500ms: adaptive speed rate adjustment
    private static let SPEED_CATCH_UP: Float = 1.03      // 3% faster to catch up smoothly
    private static let SPEED_SLOW_DOWN: Float = 0.97     // 3% slower to let remote catch up

    public init(playerManager: NativePlayerManager, socketClient: EunoiaSocketClient) {
        self.playerManager = playerManager
        self.socketClient = socketClient
    }

    /**
     * Called when a playback action happens locally (Play, Pause, Seek, Next, Previous)
     */
    public func emitLocalPlaybackCommand(action: String, extraPayload: [String: Any] = [:]) {
        if isApplyingRemoteCommand { return } // Avoid echo loops

        let song = playerManager.currentSong
        var payload: [String: Any] = [
            "action": action,
            "songId": song?.id ?? "",
            "positionMs": playerManager.positionMs,
            "isPlaying": playerManager.isPlaying,
            "timestamp": Int64(Date().timeIntervalSince1970 * 1000)
        ]

        for (k, v) in extraPayload {
            payload[k] = v
        }

        socketClient.sendEvent(eventType: action, payload: payload)
        print("[\(tag)] Emitted local playback command '\(action)'")
    }

    /**
     * Called when a socket event arrives from partner
     */
    public func handleRemoteSocketEvent(event: EunoiaSocketEvent) {
        let payload = event.payload
        let action = event.eventType

        print("[\(tag)] Handling remote socket event '\(action)' from \(event.senderDeviceId)")

        isApplyingRemoteCommand = true
        defer {
            isApplyingRemoteCommand = false
        }

        let remoteSongId = payload["songId"] as? String ?? ""
        let remoteTitle = payload["title"] as? String ?? "Track"
        let remoteAuthor = payload["author"] as? String ?? "Artist"
        let remoteThumb = payload["thumbnail"] as? String ?? "https://i.ytimg.com/vi/\(remoteSongId)/hqdefault.jpg"
        let remoteDuration = payload["durationSeconds"] as? Int ?? 180

        if !remoteSongId.isEmpty && playerManager.currentSong?.id != remoteSongId {
            let newSong = Song(
                id: remoteSongId,
                title: remoteTitle.isEmpty ? "Track" : remoteTitle,
                author: remoteAuthor.isEmpty ? "Artist" : remoteAuthor,
                thumbnail: remoteThumb.isEmpty ? "https://i.ytimg.com/vi/\(remoteSongId)/hqdefault.jpg" : remoteThumb,
                durationSeconds: remoteDuration
            )
            lastRemoteAppliedSongId = remoteSongId
            print("[\(tag)] Remote song mismatch detected (local='\(playerManager.currentSong?.id ?? "")', remote='\(remoteSongId)'). Loading remote song '\(newSong.title)'!")
            playerManager.play(song: newSong)
        }

        switch action {
        case "play":
            if !playerManager.isPlaying {
                playerManager.togglePlayPause()
            }
            syncPositionWithDriftCorrection(payload: payload, clientSentAt: event.clientSentAt, forceSeek: false)

        case "pause":
            if playerManager.isPlaying {
                playerManager.togglePlayPause()
            }
            playerManager.setPlaybackSpeed(1.0)
            syncPositionWithDriftCorrection(payload: payload, clientSentAt: event.clientSentAt, forceSeek: false)

        case "seek":
            let remotePosMs = payload["positionMs"] as? Int64 ?? -1
            let isRemotePlaying = payload["isPlaying"] as? Bool ?? playerManager.isPlaying
            if remotePosMs >= 0 {
                playerManager.setPlaybackSpeed(1.0)
                playerManager.seekTo(remotePosMs)
                if isRemotePlaying && !playerManager.isPlaying {
                    playerManager.togglePlayPause()
                } else if !isRemotePlaying && playerManager.isPlaying {
                    playerManager.togglePlayPause()
                }
            }

        case "next", "previous", "trackChanged":
            // Handled automatically via songId resolution above
            break

        case "heartbeat":
            syncPositionWithDriftCorrection(payload: payload, clientSentAt: event.clientSentAt, forceSeek: false)

        default:
            break
        }
    }

    private func syncPositionWithDriftCorrection(
        payload: [String: Any],
        clientSentAt: Int64,
        forceSeek: Bool = false
    ) {
        guard let remotePosMs = payload["positionMs"] as? Int64, remotePosMs >= 0 else { return }

        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let networkDelayMs = max(0, min(2000, nowMs - clientSentAt))
        let isRemotePlaying = payload["isPlaying"] as? Bool ?? playerManager.isPlaying

        let correctedPositionMs = isRemotePlaying ? (remotePosMs + networkDelayMs) : remotePosMs
        let myPosMs = playerManager.positionMs
        let diffMs = correctedPositionMs - myPosMs
        let absDriftMs = abs(diffMs)

        if absDriftMs <= Self.DRIFT_IGNORE_MS {
            playerManager.setPlaybackSpeed(1.0)
            return
        }

        if forceSeek || absDriftMs > Self.DRIFT_SMOOTH_MS {
            print("[\(tag)] Hard seek required (\(absDriftMs) ms drift > \(Self.DRIFT_SMOOTH_MS) ms threshold). Seeking to \(correctedPositionMs) ms")
            playerManager.setPlaybackSpeed(1.0)
            playerManager.seekTo(correctedPositionMs)
        } else {
            // Adaptive Playback Speed Rate Smoothing for 300ms - 1500ms drift
            if diffMs > 0 {
                print("[\(tag)] Follower behind host by \(diffMs) ms: boosting speed to \(Self.SPEED_CATCH_UP)x")
                playerManager.setPlaybackSpeed(Self.SPEED_CATCH_UP)
            } else {
                print("[\(tag)] Follower ahead of host by \(abs(diffMs)) ms: slowing speed to \(Self.SPEED_SLOW_DOWN)x")
                playerManager.setPlaybackSpeed(Self.SPEED_SLOW_DOWN)
            }
        }
    }
}
