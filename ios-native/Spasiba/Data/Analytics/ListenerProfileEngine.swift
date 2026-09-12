import Foundation

// MARK: - Listener Stats

public struct ListenerStats: Equatable {
    public let totalPlayedSongs: Int
    public let totalListeningMinutes: Int64
    public let topArtists: [(String, Int)]
    public let listeningStreakDays: Int
    public let engagementBadge: String
    public let recentSongs: [Song]

    public init(
        totalPlayedSongs: Int,
        totalListeningMinutes: Int64,
        topArtists: [(String, Int)],
        listeningStreakDays: Int,
        engagementBadge: String,
        recentSongs: [Song]
    ) {
        self.totalPlayedSongs = totalPlayedSongs
        self.totalListeningMinutes = totalListeningMinutes
        self.topArtists = topArtists
        self.listeningStreakDays = listeningStreakDays
        self.engagementBadge = engagementBadge
        self.recentSongs = recentSongs
    }

    public static func == (lhs: ListenerStats, rhs: ListenerStats) -> Bool {
        return lhs.totalPlayedSongs == rhs.totalPlayedSongs &&
            lhs.totalListeningMinutes == rhs.totalListeningMinutes &&
            lhs.topArtists.elementsEqual(rhs.topArtists, by: { $0.0 == $1.0 && $0.1 == $1.1 }) &&
            lhs.listeningStreakDays == rhs.listeningStreakDays &&
            lhs.engagementBadge == rhs.engagementBadge &&
            lhs.recentSongs == rhs.recentSongs
    }
}

// MARK: - Listener Profile Engine

public enum ListenerProfileEngine {

    public static func computeProfileStats(
        history: [ListeningHistoryEntity],
        likedSongsCount: Int
    ) -> ListenerStats {
        if history.isEmpty {
            return ListenerStats(
                totalPlayedSongs: 0,
                totalListeningMinutes: 0,
                topArtists: [],
                listeningStreakDays: 0,
                engagementBadge: "Music Explorer",
                recentSongs: []
            )
        }

        let totalPlayedSongs = history.count

        // Total listening minutes
        let totalSeconds = history.reduce(Int64(0)) { $0 + Int64($1.durationSeconds) }
        let totalMinutes = totalSeconds / 60

        // Top artists count
        var artistMap: [String: Int] = [:]
        for item in history {
            artistMap[item.author, default: 0] += 1
        }
        let artistCounts = artistMap
            .map { ($0.key, $0.value) }
            .sorted { $0.1 > $1.1 }
            .prefix(5)

        // Streak calculation (consecutive days with plays)
        let calendar = Calendar.current
        var uniqueDayKeys = Set<String>()
        for entity in history {
            let date = Date(timeIntervalSince1970: TimeInterval(entity.playedAt) / 1000.0)
            let year = calendar.component(.year, from: date)
            let dayOfYear = calendar.ordinality(of: .day, in: .year, for: date) ?? 0
            uniqueDayKeys.insert("\(year)-\(dayOfYear)")
        }
        let uniqueDays = uniqueDayKeys.count

        // Engagement badge algorithm
        let badge: String
        if totalPlayedSongs >= 100 || likedSongsCount >= 50 {
            badge = "Master Connoisseur 🎧"
        } else if totalPlayedSongs >= 40 || likedSongsCount >= 20 {
            badge = "Sonic Enthusiast ⚡"
        } else if totalPlayedSongs >= 10 || likedSongsCount >= 5 {
            badge = "Rhythm Explorer 🌟"
        } else {
            badge = "Music Adventurer 🚀"
        }

        return ListenerStats(
            totalPlayedSongs: totalPlayedSongs,
            totalListeningMinutes: totalMinutes,
            topArtists: Array(artistCounts),
            listeningStreakDays: uniqueDays,
            engagementBadge: badge,
            recentSongs: Array(history.prefix(10).map { $0.toSong() })
        )
    }

    public static func computeProfileStats(
        history: [Song],
        likedCount: Int
    ) -> ListenerStats {
        let entities = history.map {
            ListeningHistoryEntity(
                songId: $0.id,
                title: $0.title,
                author: $0.author,
                thumbnail: $0.thumbnail,
                durationSeconds: $0.durationSeconds
            )
        }
        return computeProfileStats(history: entities, likedSongsCount: likedCount)
    }
}

