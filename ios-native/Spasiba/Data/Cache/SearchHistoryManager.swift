import Foundation

public struct RecentArtistItem: Identifiable, Codable, Equatable {
    public var id: String { artistName }
    public let artistName: String
    public let avatarUrl: String?
    public let subtitle: String

    public init(artistName: String, avatarUrl: String? = nil, subtitle: String = "Artist") {
        self.artistName = artistName
        self.avatarUrl = avatarUrl
        self.subtitle = subtitle
    }
}

public final class SearchHistoryManager: ObservableObject {
    public static let shared = SearchHistoryManager()

    private let textQueriesKey = "spasiba_search_text_queries"
    private let playedSongsKey = "spasiba_search_played_songs"
    private let recentArtistsKey = "spasiba_search_recent_artists"

    @Published public private(set) var textQueries: [String] = []
    @Published public private(set) var playedSongs: [Song] = []
    @Published public private(set) var recentArtists: [RecentArtistItem] = []

    // Backwards compatibility alias
    public var recentQueries: [String] {
        return textQueries
    }

    private init() {
        loadHistory()
    }

    private func loadHistory() {
        if let savedQueries = UserDefaults.standard.stringArray(forKey: textQueriesKey) {
            self.textQueries = savedQueries
        } else {
            self.textQueries = []
        }

        if let data = UserDefaults.standard.data(forKey: playedSongsKey),
           let decoded = try? JSONDecoder().decode([Song].self, from: data) {
            self.playedSongs = decoded
        }

        if let data = UserDefaults.standard.data(forKey: recentArtistsKey),
           let decoded = try? JSONDecoder().decode([RecentArtistItem].self, from: data) {
            self.recentArtists = decoded
        }
    }

    // 1. Text Queries
    public func getTextQueries() -> [String] {
        return textQueries
    }

    public func addTextQuery(_ query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        textQueries.removeAll(where: { $0.caseInsensitiveCompare(trimmed) == .orderedSame })
        textQueries.insert(trimmed, at: 0)
        if textQueries.count > 15 {
            textQueries = Array(textQueries.prefix(15))
        }
        UserDefaults.standard.set(textQueries, forKey: textQueriesKey)
    }

    public func addQuery(_ query: String) {
        addTextQuery(query)
    }

    public func deleteTextQuery(_ query: String) {
        textQueries.removeAll(where: { $0.caseInsensitiveCompare(query) == .orderedSame })
        UserDefaults.standard.set(textQueries, forKey: textQueriesKey)
    }

    public func removeQuery(_ query: String) {
        deleteTextQuery(query)
    }

    // 2. Played Songs
    public func getPlayedSongs() -> [Song] {
        return playedSongs
    }

    public func addPlayedSong(_ song: Song) {
        playedSongs.removeAll(where: { $0.id == song.id })
        playedSongs.insert(song, at: 0)
        if playedSongs.count > 20 {
            playedSongs = Array(playedSongs.prefix(20))
        }
        if let data = try? JSONEncoder().encode(playedSongs) {
            UserDefaults.standard.set(data, forKey: playedSongsKey)
        }

        // Remove song title from text queries so played items live only in rail
        textQueries.removeAll(where: { $0.caseInsensitiveCompare(song.title) == .orderedSame })
        UserDefaults.standard.set(textQueries, forKey: textQueriesKey)
    }

    public func deletePlayedSong(songId: String) {
        playedSongs.removeAll(where: { $0.id == songId })
        if let data = try? JSONEncoder().encode(playedSongs) {
            UserDefaults.standard.set(data, forKey: playedSongsKey)
        }
    }

    public func clearPlayedSongs() {
        playedSongs.removeAll()
        UserDefaults.standard.removeObject(forKey: playedSongsKey)
    }

    // 3. Recent Artists
    public func getRecentArtists() -> [RecentArtistItem] {
        return recentArtists
    }

    public func addRecentArtist(artistName: String, avatarUrl: String?, subtitle: String = "Artist") {
        let clean = artistName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }

        let existing = recentArtists.first(where: { $0.artistName.caseInsensitiveCompare(clean) == .orderedSame })
        let finalAvatar = (avatarUrl != nil && !avatarUrl!.isEmpty) ? avatarUrl : existing?.avatarUrl

        recentArtists.removeAll(where: { $0.artistName.caseInsensitiveCompare(clean) == .orderedSame })
        recentArtists.insert(RecentArtistItem(artistName: clean, avatarUrl: finalAvatar, subtitle: subtitle), at: 0)
        if recentArtists.count > 10 {
            recentArtists = Array(recentArtists.prefix(10))
        }

        if let data = try? JSONEncoder().encode(recentArtists) {
            UserDefaults.standard.set(data, forKey: recentArtistsKey)
        }

        textQueries.removeAll(where: { $0.caseInsensitiveCompare(clean) == .orderedSame })
        UserDefaults.standard.set(textQueries, forKey: textQueriesKey)
    }

    public func deleteRecentArtist(artistName: String) {
        recentArtists.removeAll(where: { $0.artistName.caseInsensitiveCompare(artistName) == .orderedSame })
        if let data = try? JSONEncoder().encode(recentArtists) {
            UserDefaults.standard.set(data, forKey: recentArtistsKey)
        }
    }

    public func clearAllHistory() {
        textQueries.removeAll()
        playedSongs.removeAll()
        recentArtists.removeAll()
        UserDefaults.standard.removeObject(forKey: textQueriesKey)
        UserDefaults.standard.removeObject(forKey: playedSongsKey)
        UserDefaults.standard.removeObject(forKey: recentArtistsKey)
    }

    public func clearAll() {
        clearAllHistory()
    }
}
