import Foundation

// Exact 1:1 translation of SessionBrainManager.kt from android-native-2
public final class SessionBrainManager {
    public static let shared = SessionBrainManager()

    private static let recentTrackLimit = 10
    private let lock = NSRecursiveLock()

    public var intent: String?
    public var energy: String = "medium"
    public private(set) var recentTracks: [Song] = []
    public private(set) var artistAffinity: [String: Int] = [:]
    public private(set) var bannedArtists: Set<String> = []
    public private(set) var playCount: Int = 0
    public private(set) var skipCount: Int = 0

    private init() {}

    public func recordPlayStart(song: Song) {
        lock.lock()
        defer { lock.unlock() }

        playCount += 1
        let artistKey = SmartQueueEngine.getArtistKey(song.author)
        if !artistKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            artistAffinity[artistKey] = (artistAffinity[artistKey] ?? 0) + 1
        }
        recentTracks.removeAll { $0.id == song.id }
        recentTracks.insert(song, at: 0)
        if recentTracks.count > Self.recentTrackLimit {
            recentTracks.removeLast()
        }
    }

    public func recordSkip(song: Song) {
        lock.lock()
        defer { lock.unlock() }

        skipCount += 1
        let artistKey = SmartQueueEngine.getArtistKey(song.author)
        if !artistKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let currentScore = (artistAffinity[artistKey] ?? 0) - 2
            artistAffinity[artistKey] = currentScore

            let skipsForArtist = recentTracks.filter { SmartQueueEngine.getArtistKey($0.author) == artistKey }.count
            if skipsForArtist >= 2 {
                bannedArtists.insert(artistKey)
            }
        }
    }

    public func recordLike(song: Song) {
        lock.lock()
        defer { lock.unlock() }

        let artistKey = SmartQueueEngine.getArtistKey(song.author)
        if !artistKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            artistAffinity[artistKey] = (artistAffinity[artistKey] ?? 0) + 3
            bannedArtists.remove(artistKey)
        }
    }

    public func getEngagementFactor() -> Double {
        lock.lock()
        defer { lock.unlock() }

        let engagement = (Double(playCount) * 0.12) + (Double(skipCount) * 0.18) + (Double(artistAffinity.count) * 0.05)
        return min(1.0, max(0.0, engagement))
    }
}
