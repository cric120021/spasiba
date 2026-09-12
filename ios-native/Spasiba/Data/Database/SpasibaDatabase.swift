import Foundation

public struct Playlist: Identifiable, Codable, Equatable {
    public let id: String
    public var name: String
    public var songs: [Song]
    public let createdAt: Date
    public var shareCode: String?
    public var isShared: Bool

    public init(
        id: String = UUID().uuidString,
        name: String,
        songs: [Song] = [],
        createdAt: Date = Date(),
        shareCode: String? = nil,
        isShared: Bool = false
    ) {
        self.id = id
        self.name = name
        self.songs = songs
        self.createdAt = createdAt
        self.shareCode = shareCode
        self.isShared = isShared
    }
}

public final class SpasibaDatabase: ObservableObject {
    public static let shared = SpasibaDatabase()

    private let userDefaults = UserDefaults.standard
    private let likedSongsKey = "spasiba_liked_songs"
    private let historyKey = "spasiba_playback_history"
    private let playlistsKey = "spasiba_playlists"

    @Published public private(set) var likedSongs: [Song] = []
    @Published public private(set) var recentlyPlayed: [Song] = []
    @Published public var playlists: [Playlist] = []
    public var likedSongIds: Set<String> {
        return Set(likedSongs.map { song in song.id })
    }

    private init() {
        loadData()
    }

    private func loadData() {
        if let data = userDefaults.data(forKey: likedSongsKey),
           let decoded = try? JSONDecoder().decode([Song].self, from: data) {
            self.likedSongs = decoded
        }
        if let data = userDefaults.data(forKey: historyKey),
           let decoded = try? JSONDecoder().decode([Song].self, from: data) {
            self.recentlyPlayed = decoded
        }
        if let data = userDefaults.data(forKey: playlistsKey),
           let decoded = try? JSONDecoder().decode([Playlist].self, from: data) {
            self.playlists = decoded
        }
    }

    public func toggleLike(song: Song) {
        if let idx = likedSongs.firstIndex(where: { $0.id == song.id }) {
            likedSongs.remove(at: idx)
        } else {
            var s = song
            s.isLiked = true
            likedSongs.insert(s, at: 0)
        }
        saveLikes()
    }

    public func isLiked(songId: String) -> Bool {
        return likedSongs.contains(where: { $0.id == songId })
    }

    public func addToHistory(song: Song) {
        recentlyPlayed.removeAll(where: { $0.id == song.id })
        recentlyPlayed.insert(song, at: 0)
        if recentlyPlayed.count > 100 {
            recentlyPlayed = Array(recentlyPlayed.prefix(100))
        }
        saveHistory()
    }

    @discardableResult
    public func createPlaylist(name: String) -> Playlist {
        let pl = Playlist(name: name)
        playlists.append(pl)
        savePlaylists()
        return pl
    }

    public func updatePlaylistName(playlistId: String, newName: String) {
        guard let idx = playlists.firstIndex(where: { $0.id == playlistId }) else { return }
        playlists[idx].name = newName
        savePlaylists()
    }

    public func deletePlaylist(playlistId: String) {
        playlists.removeAll(where: { $0.id == playlistId })
        savePlaylists()
    }

    public func addSongToPlaylist(playlistId: String, song: Song) {
        guard let idx = playlists.firstIndex(where: { $0.id == playlistId }) else { return }
        if !playlists[idx].songs.contains(where: { $0.id == song.id }) {
            playlists[idx].songs.append(song)
            savePlaylists()
        }
    }

    public func removeSongFromPlaylist(playlistId: String, songId: String) {
        guard let idx = playlists.firstIndex(where: { $0.id == playlistId }) else { return }
        playlists[idx].songs.removeAll(where: { $0.id == songId })
        savePlaylists()
    }

    public func getSongsForPlaylist(playlistId: String) -> [Song] {
        return playlists.first(where: { $0.id == playlistId })?.songs ?? []
    }

    @discardableResult
    public func importSharedPlaylist(name: String, songs: [Song], shareCode: String?) -> Playlist {
        let pl = Playlist(name: name, songs: songs, shareCode: shareCode, isShared: true)
        playlists.append(pl)
        savePlaylists()
        return pl
    }

    public func forceSyncWithRemoteCloud() {
        // Reload from local storage and trigger UI notifications
        loadData()
    }

    private func saveLikes() {
        if let encoded = try? JSONEncoder().encode(likedSongs) {
            userDefaults.set(encoded, forKey: likedSongsKey)
        }
    }

    private func saveHistory() {
        if let encoded = try? JSONEncoder().encode(recentlyPlayed) {
            userDefaults.set(encoded, forKey: historyKey)
        }
    }

    private func savePlaylists() {
        if let encoded = try? JSONEncoder().encode(playlists) {
            userDefaults.set(encoded, forKey: playlistsKey)
        }
    }

    // MARK: - DAO Accessors

    public static func getDatabase() -> SpasibaDatabase {
        return shared
    }

    public func likedSongDao() -> LikedSongDao {
        return LikedSongDao(database: self)
    }

    public func playlistDao() -> PlaylistDao {
        return PlaylistDao(database: self)
    }

    public func searchHistoryDao() -> SearchHistoryDao {
        return SearchHistoryDao()
    }

    public func listeningHistoryDao() -> ListeningHistoryDao {
        return ListeningHistoryDao(database: self)
    }

    public func userProfileDao() -> UserProfileDao {
        return UserProfileDao()
    }
}
