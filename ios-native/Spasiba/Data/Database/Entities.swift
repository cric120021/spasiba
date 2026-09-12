import Foundation

// Exact 1:1 translation of Entities.kt from android-native-2
public struct LikedSongEntity: Codable, Equatable, Identifiable {
    public let id: String
    public let title: String
    public let author: String
    public let thumbnail: String
    public let durationSeconds: Int
    public let addedAt: Int64

    public init(id: String, title: String, author: String, thumbnail: String, durationSeconds: Int, addedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        self.id = id
        self.title = title
        self.author = author
        self.thumbnail = thumbnail
        self.durationSeconds = durationSeconds
        self.addedAt = addedAt
    }

    public func toSong() -> Song {
        return Song(id: id, title: title, author: author, thumbnail: thumbnail, durationSeconds: durationSeconds)
    }
}

public struct UserProfileEntity: Codable, Equatable {
    public var id: Int
    public var username: String
    public var visitorId: String
    public var hardwareDeviceId: String
    public var macAddressOrImei: String
    public var photoUri: String?
    public var isSynced: Bool
    public var registeredAt: Int64
    public var updatedAt: Int64

    public init(
        id: Int = 1,
        username: String = "Spasiba Listener",
        visitorId: String = "v_849201",
        hardwareDeviceId: String = "",
        macAddressOrImei: String = "",
        photoUri: String? = nil,
        isSynced: Bool = true,
        registeredAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        updatedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.username = username
        self.visitorId = visitorId
        self.hardwareDeviceId = hardwareDeviceId
        self.macAddressOrImei = macAddressOrImei
        self.photoUri = photoUri
        self.isSynced = isSynced
        self.registeredAt = registeredAt
        self.updatedAt = updatedAt
    }
}

public struct PlaylistEntity: Codable, Equatable, Identifiable {
    public let id: String
    public let name: String
    public let isShared: Bool
    public let shareCode: String?
    public let createdAt: Int64

    public init(id: String, name: String, isShared: Bool = false, shareCode: String? = nil, createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        self.id = id
        self.name = name
        self.isShared = isShared
        self.shareCode = shareCode
        self.createdAt = createdAt
    }
}

public struct PlaylistSongEntity: Codable, Equatable {
    public let playlistId: String
    public let songId: String
    public let title: String
    public let author: String
    public let thumbnail: String
    public let durationSeconds: Int
    public let position: Int

    public init(playlistId: String, songId: String, title: String, author: String, thumbnail: String, durationSeconds: Int, position: Int = 0) {
        self.playlistId = playlistId
        self.songId = songId
        self.title = title
        self.author = author
        self.thumbnail = thumbnail
        self.durationSeconds = durationSeconds
        self.position = position
    }

    public func toSong() -> Song {
        return Song(id: songId, title: title, author: author, thumbnail: thumbnail, durationSeconds: durationSeconds)
    }
}

public struct SearchHistoryEntity: Codable, Equatable, Identifiable {
    public var id: String { query }
    public let query: String
    public let searchedAt: Int64

    public init(query: String, searchedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        self.query = query
        self.searchedAt = searchedAt
    }
}

public struct ListeningHistoryEntity: Codable, Equatable, Identifiable {
    public var id: String { songId }
    public let songId: String
    public let title: String
    public let author: String
    public let thumbnail: String
    public let durationSeconds: Int
    public let playedAt: Int64

    public init(songId: String, title: String, author: String, thumbnail: String, durationSeconds: Int, playedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        self.songId = songId
        self.title = title
        self.author = author
        self.thumbnail = thumbnail
        self.durationSeconds = durationSeconds
        self.playedAt = playedAt
    }

    public func toSong() -> Song {
        return Song(id: songId, title: title, author: author, thumbnail: thumbnail, durationSeconds: durationSeconds)
    }
}

