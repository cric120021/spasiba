import Foundation

// MARK: - LikedSongDao (Exact 1:1 match for Daos.kt)

public final class LikedSongDao {
    private let database: SpasibaDatabase

    public init(database: SpasibaDatabase) {
        self.database = database
    }

    public func insertLikedSong(_ song: LikedSongEntity) {
        database.toggleLike(song: song.toSong())
    }

    public func deleteLikedSong(_ songId: String) {
        if database.isLiked(songId: songId) {
            database.toggleLike(song: Song(id: songId, title: "", author: "", thumbnail: ""))
        }
    }

    public func getAllLikedSongs() -> [LikedSongEntity] {
        return database.likedSongs.map { s in
            LikedSongEntity(
                id: s.id,
                title: s.title,
                author: s.author,
                thumbnail: s.thumbnail,
                durationSeconds: s.durationSeconds
            )
        }
    }

    public func isLiked(_ songId: String) -> Bool {
        return database.isLiked(songId: songId)
    }
}

// MARK: - UserProfileDao (Exact 1:1 match for Daos.kt)

public final class UserProfileDao {
    private let key = "spasiba_user_profile_data"

    public init() {}

    public func insertOrUpdateProfile(_ profile: UserProfileEntity) {
        if let data = try? JSONEncoder().encode(profile) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }

    public func getUserProfile() -> UserProfileEntity? {
        return getUserProfileSync()
    }

    public func getUserProfileSync() -> UserProfileEntity? {
        if let data = UserDefaults.standard.data(forKey: key),
           let profile = try? JSONDecoder().decode(UserProfileEntity.self, from: data) {
            return profile
        }
        return nil
    }

    public func updatePhotoUri(_ photoUri: String?, updatedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        var profile = getUserProfileSync() ?? UserProfileEntity()
        profile.photoUri = photoUri
        profile.updatedAt = updatedAt
        insertOrUpdateProfile(profile)
    }

    public func updateUsername(_ username: String, updatedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        var profile = getUserProfileSync() ?? UserProfileEntity()
        profile.username = username
        profile.updatedAt = updatedAt
        insertOrUpdateProfile(profile)
    }

    public func updateSyncStatus(_ isSynced: Bool, updatedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        var profile = getUserProfileSync() ?? UserProfileEntity()
        profile.isSynced = isSynced
        profile.updatedAt = updatedAt
        insertOrUpdateProfile(profile)
    }
}

// MARK: - PlaylistDao (Exact 1:1 match for Daos.kt)

public final class PlaylistDao {
    private let database: SpasibaDatabase

    public init(database: SpasibaDatabase) {
        self.database = database
    }

    public func insertPlaylist(_ playlist: PlaylistEntity) {
        _ = database.createPlaylist(name: playlist.name)
        if let lastIdx = database.playlists.indices.last {
            database.playlists[lastIdx].shareCode = playlist.shareCode
            database.playlists[lastIdx].isShared = playlist.isShared
        }
    }

    public func deletePlaylist(_ playlistId: String) {
        database.deletePlaylist(playlistId: playlistId)
    }

    public func updatePlaylistName(_ playlistId: String, name: String) {
        database.updatePlaylistName(playlistId: playlistId, newName: name)
    }

    public func getAllPlaylists() -> [PlaylistEntity] {
        return database.playlists.map { pl in
            PlaylistEntity(
                id: pl.id,
                name: pl.name,
                isShared: pl.isShared,
                shareCode: pl.shareCode,
                createdAt: Int64(pl.createdAt.timeIntervalSince1970 * 1000)
            )
        }
    }

    public func getAllPlaylistsSync() -> [PlaylistEntity] {
        return getAllPlaylists()
    }

    public func getPlaylistById(_ playlistId: String) -> PlaylistEntity? {
        guard let pl = database.playlists.first(where: { $0.id == playlistId }) else { return nil }
        return PlaylistEntity(
            id: pl.id,
            name: pl.name,
            isShared: pl.isShared,
            shareCode: pl.shareCode,
            createdAt: Int64(pl.createdAt.timeIntervalSince1970 * 1000)
        )
    }

    public func insertPlaylistSong(_ song: PlaylistSongEntity) {
        database.addSongToPlaylist(playlistId: song.playlistId, song: song.toSong())
    }

    public func deletePlaylistSong(_ playlistId: String, songId: String) {
        database.removeSongFromPlaylist(playlistId: playlistId, songId: songId)
    }

    public func deleteAllSongsForPlaylist(_ playlistId: String) {
        guard let idx = database.playlists.firstIndex(where: { $0.id == playlistId }) else { return }
        database.playlists[idx].songs.removeAll()
    }

    public func getSongsForPlaylist(_ playlistId: String) -> [PlaylistSongEntity] {
        let songs = database.getSongsForPlaylist(playlistId: playlistId)
        return songs.enumerated().map { index, song in
            PlaylistSongEntity(
                playlistId: playlistId,
                songId: song.id,
                title: song.title,
                author: song.author,
                thumbnail: song.thumbnail,
                durationSeconds: song.durationSeconds,
                position: index
            )
        }
    }

    public func getSongsForPlaylistSync(_ playlistId: String) -> [PlaylistSongEntity] {
        return getSongsForPlaylist(playlistId)
    }
}

// MARK: - SearchHistoryDao (Exact 1:1 match for Daos.kt)

public final class SearchHistoryDao {
    private let manager = SearchHistoryManager.shared

    public init() {}

    public func insertQuery(_ entity: SearchHistoryEntity) {
        manager.addQuery(entity.query)
    }

    public func deleteQuery(_ query: String) {
        manager.removeQuery(query)
    }

    public func getRecentQueries() -> [SearchHistoryEntity] {
        return manager.recentQueries.map { SearchHistoryEntity(query: $0) }
    }

    public func clearHistory() {
        manager.clearAll()
    }
}

// MARK: - ListeningHistoryDao (Exact 1:1 match for Daos.kt)

public final class ListeningHistoryDao {
    private let database: SpasibaDatabase
    private let historyKey = "spasiba_listening_history_entities"

    public init(database: SpasibaDatabase) {
        self.database = database
    }

    public func insertHistory(_ item: ListeningHistoryEntity) {
        database.addToHistory(song: item.toSong())
        var entities = getRecentHistory()
        entities.removeAll { $0.songId == item.songId }
        entities.insert(item, at: 0)
        if entities.count > 50 {
            entities = Array(entities.prefix(50))
        }
        if let data = try? JSONEncoder().encode(entities) {
            UserDefaults.standard.set(data, forKey: historyKey)
        }
    }

    public func getRecentHistory() -> [ListeningHistoryEntity] {
        if let data = UserDefaults.standard.data(forKey: historyKey),
           let decoded = try? JSONDecoder().decode([ListeningHistoryEntity].self, from: data) {
            return decoded
        }
        return database.recentlyPlayed.prefix(50).map { s in
            ListeningHistoryEntity(
                songId: s.id,
                title: s.title,
                author: s.author,
                thumbnail: s.thumbnail,
                durationSeconds: s.durationSeconds
            )
        }
    }

    public func clearHistory() {
        UserDefaults.standard.removeObject(forKey: historyKey)
    }
}
