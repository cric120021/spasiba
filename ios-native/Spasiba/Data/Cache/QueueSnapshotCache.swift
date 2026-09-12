import Foundation

// Exact 1:1 translation of QueueSnapshotCache.kt from android-native-2
public struct QueueSnapshot: Codable, Equatable {
    public let songs: [Song]
    public let currentIndex: Int
    public let positionMs: Int64
    public let timestamp: Int64

    public init(
        songs: [Song],
        currentIndex: Int,
        positionMs: Int64 = 0,
        timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.songs = songs
        self.currentIndex = currentIndex
        self.positionMs = positionMs
        self.timestamp = timestamp
    }
}

public final class QueueSnapshotCache {
    public static let shared = QueueSnapshotCache()

    private let userDefaults: UserDefaults
    private let keyQueue = "key_queue_snapshot"
    private let threeDaysMs: Int64 = 3 * 24 * 60 * 60 * 1000

    public init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
    }

    public func saveSnapshot(songs: [Song], currentIndex: Int, positionMs: Int64 = 0) {
        if songs.isEmpty { return }
        let sanitizedSongs = songs.prefix(80).map { song in
            var s = song
            s.streamUrl = nil
            return s
        }
        let snapshot = QueueSnapshot(songs: Array(sanitizedSongs), currentIndex: currentIndex, positionMs: positionMs)
        DispatchQueue.global(qos: .background).async { [weak self] in
            guard let self = self else { return }
            if let data = try? JSONEncoder().encode(snapshot),
               let json = String(data: data, encoding: .utf8) {
                self.userDefaults.set(json, forKey: self.keyQueue)
            }
        }
    }

    public func restoreSnapshot() -> QueueSnapshot? {
        guard let json = userDefaults.string(forKey: keyQueue),
              let data = json.data(using: .utf8) else {
            return nil
        }
        do {
            let snapshot = try JSONDecoder().decode(QueueSnapshot.self, from: data)
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            if now - snapshot.timestamp > threeDaysMs {
                return nil
            } else {
                let sanitizedSongs = snapshot.songs.map { song -> Song in
                    var s = song
                    s.streamUrl = nil
                    return s
                }
                return QueueSnapshot(
                    songs: sanitizedSongs,
                    currentIndex: snapshot.currentIndex,
                    positionMs: snapshot.positionMs,
                    timestamp: snapshot.timestamp
                )
            }
        } catch {
            return nil
        }
    }
}
