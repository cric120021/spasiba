import Foundation
#if canImport(UIKit)
import UIKit
#endif

public final class MusicRepository: ObservableObject {
    public static let shared = MusicRepository()

    private let db = SpasibaDatabase.shared
    private let likedSongDao: LikedSongDao
    private let playlistDao: PlaylistDao
    private let searchHistoryDao: SearchHistoryDao
    private let listeningHistoryDao: ListeningHistoryDao
    private let nativeAudioCache = SpasibaNativeAudioCache.shared
    private let userProfileDao: UserProfileDao

    @Published public private(set) var likedSongs: [Song] = []
    @Published public private(set) var playlists: [PlaylistEntity] = []
    @Published public private(set) var listeningHistory: [Song] = []
    @Published public private(set) var listeningHistoryEntities: [ListeningHistoryEntity] = []
    @Published public private(set) var userProfile: UserProfileEntity?

    public init() {
        self.likedSongDao = db.likedSongDao()
        self.playlistDao = db.playlistDao()
        self.searchHistoryDao = db.searchHistoryDao()
        self.listeningHistoryDao = db.listeningHistoryDao()
        self.userProfileDao = db.userProfileDao()

        refreshLocalState()
    }

    private func refreshLocalState() {
        self.likedSongs = likedSongDao.getAllLikedSongs().map { $0.toSong() }
        self.playlists = playlistDao.getAllPlaylists()
        self.listeningHistoryEntities = listeningHistoryDao.getRecentHistory()
        self.listeningHistory = self.listeningHistoryEntities.map { $0.toSong() }
        self.userProfile = userProfileDao.getUserProfile()
    }

    // MARK: - Likes

    public func toggleLike(song: Song) async {
        if likedSongDao.isLiked(song.id) {
            likedSongDao.deleteLikedSong(song.id)
        } else {
            likedSongDao.insertLikedSong(
                LikedSongEntity(
                    id: song.id,
                    title: song.title,
                    author: song.author,
                    thumbnail: song.thumbnail,
                    durationSeconds: song.durationSeconds
                )
            )
        }
        await MainActor.run {
            self.likedSongs = self.likedSongDao.getAllLikedSongs().map { $0.toSong() }
        }
    }

    public func isLiked(songId: String) -> Bool {
        return likedSongDao.isLiked(songId)
    }

    public func isLiked(_ songId: String) -> Bool {
        return likedSongDao.isLiked(songId)
    }

    // MARK: - Search

    public func searchSongs(query: String) async -> [Song] {
        let clean = query.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        if !clean.isEmpty {
            searchHistoryDao.insertQuery(SearchHistoryEntity(query: clean))
        }
        return await YouTubeMusicApi.shared.searchSongs(query: query)
    }

    // MARK: - Stream & Download Resolution

    public func resolveStreamResult(videoId: String, forceRefresh: Bool = false) async -> StreamingResult? {
        if forceRefresh {
            nativeAudioCache.invalidateStreamCache(videoId)
        }
        return await YouTubeMusicApi.shared.resolveStreamResult(videoId: videoId)
    }

    public func resolveDownloadedAudioResult(videoId: String) async -> StreamingResult? {
        do {
            let cached = try await nativeAudioCache.getOrDownload(videoId)
            return StreamingResult(url: cached.file.path)
        } catch {
            return nil
        }
    }

    // MARK: - Recommendations & Category Feed

    public func getHomeQuickPicks(history: [Song], liked: [Song]) async -> [Song] {
        return await QuickPicksEngine.generateQuickPicks(history: history, likedSongs: liked)
    }

    public func getCategoryFeed(categoryQuery: String) async -> [Song] {
        return await QuickPicksEngine.generateCategoryFeed(categoryQuery: categoryQuery)
    }

    // MARK: - Listening History

    public func recordPlayedSong(song: Song) async {
        listeningHistoryDao.insertHistory(
            ListeningHistoryEntity(
                songId: song.id,
                title: song.title,
                author: song.author,
                thumbnail: song.thumbnail,
                durationSeconds: song.durationSeconds
            )
        )
        await MainActor.run {
            self.listeningHistoryEntities = self.listeningHistoryDao.getRecentHistory()
            self.listeningHistory = self.listeningHistoryEntities.map { $0.toSong() }
        }
    }

    public func clearListeningHistory() async {
        listeningHistoryDao.clearHistory()
        await MainActor.run {
            self.listeningHistoryEntities = []
            self.listeningHistory = []
        }
    }

    // MARK: - Playlists

    @discardableResult
    public func createPlaylist(_ name: String) -> PlaylistEntity {
        let newPlaylist = PlaylistEntity(id: UUID().uuidString, name: name)
        playlistDao.insertPlaylist(newPlaylist)
        DispatchQueue.main.async {
            self.playlists = self.playlistDao.getAllPlaylists()
        }
        return newPlaylist
    }

    @discardableResult
    public func createPlaylist(name: String, description: String = "") async -> PlaylistEntity {
        let newPlaylist = PlaylistEntity(id: UUID().uuidString, name: name)
        playlistDao.insertPlaylist(newPlaylist)
        await MainActor.run {
            self.playlists = self.playlistDao.getAllPlaylists()
        }
        return newPlaylist
    }

    public func addSongToPlaylist(playlistId: String, song: Song) async {
        playlistDao.insertPlaylistSong(
            PlaylistSongEntity(
                playlistId: playlistId,
                songId: song.id,
                title: song.title,
                author: song.author,
                thumbnail: song.thumbnail,
                durationSeconds: song.durationSeconds
            )
        )
    }

    public func getSongsForPlaylist(playlistId: String) -> [Song] {
        return playlistDao.getSongsForPlaylist(playlistId).map { $0.toSong() }
    }

    public func getSongsForPlaylistSync(playlistId: String) async -> [Song] {
        return playlistDao.getSongsForPlaylistSync(playlistId).map { $0.toSong() }
    }

    public func getSongs(forPlaylistId playlistId: String) -> [Song] {
        return getSongsForPlaylist(playlistId: playlistId)
    }

    public func updatePlaylistName(playlistId: String, name: String) async {
        playlistDao.updatePlaylistName(playlistId, name: name)
        await MainActor.run {
            self.playlists = self.playlistDao.getAllPlaylists()
        }
    }

    public func removeSongFromPlaylist(playlistId: String, songId: String) async {
        playlistDao.deletePlaylistSong(playlistId, songId: songId)
    }

    @discardableResult
    public func importSharedPlaylist(name: String, songs: [Song], shareCode: String? = nil) async -> String {
        let newPlaylistId = UUID().uuidString
        let code = shareCode ?? "SPASIBA-\(UUID().uuidString.prefix(6).uppercased())"
        let playlist = PlaylistEntity(
            id: newPlaylistId,
            name: name,
            isShared: true,
            shareCode: code
        )
        playlistDao.insertPlaylist(playlist)

        for (index, song) in songs.enumerated() {
            playlistDao.insertPlaylistSong(
                PlaylistSongEntity(
                    playlistId: newPlaylistId,
                    songId: song.id,
                    title: song.title,
                    author: song.author,
                    thumbnail: song.thumbnail,
                    durationSeconds: song.durationSeconds,
                    position: index
                )
            )
        }

        await MainActor.run {
            self.playlists = self.playlistDao.getAllPlaylists()
        }

        return newPlaylistId
    }

    public func deletePlaylist(playlistId: String) async {
        playlistDao.deletePlaylist(playlistId)
        await MainActor.run {
            self.playlists = self.playlistDao.getAllPlaylists()
        }
    }

    // MARK: - User Profile & Cloud Sync

    private func uriToBase64(photoUriStr: String) -> String? {
        guard let url = URL(string: photoUriStr) else { return nil }
        guard let data = try? Data(contentsOf: url) else { return nil }
        return "data:image/jpeg;base64," + data.base64EncodedString()
    }

    private func saveBase64ToFileUri(base64Str: String) -> String? {
        let pureBase64 = base64Str.replacingOccurrences(of: "data:image/jpeg;base64,", with: "")
        guard let data = Data(base64Encoded: pureBase64) else { return nil }
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let destFile = documents.appendingPathComponent("profile_photo.jpg")
        do {
            try data.write(to: destFile)
            return destFile.absoluteString
        } catch {
            return nil
        }
    }

    @discardableResult
    public func registerUserDeviceWithHardwareId() async -> UserProfileEntity {
        #if canImport(UIKit)
        let hardwareId = UIDevice.current.identifierForVendor?.uuidString ?? "SPASIBA_HW_\(UUID().uuidString.prefix(8).uppercased())"
        let deviceModel = UIDevice.current.model
        #else
        let hardwareId = "SPASIBA_HW_\(UUID().uuidString.prefix(8).uppercased())"
        let deviceModel = "Spasiba Listener"
        #endif

        let eunoiaDeviceId = UserDefaults.standard.string(forKey: "eunoia_my_device_id") ?? "v_\(abs(hardwareId.hashValue % 1000000))"
        let macOrImeiSurrogate = "HW_MAC_\(hardwareId.prefix(12).uppercased())"
        let visitorId = eunoiaDeviceId

        let existing = userProfileDao.getUserProfileSync()

        // Sync with Cloud Server upon app start / fresh reinstallation
        var cloudUsername: String?
        var cloudPhotoUri: String?

        if let syncUrl = URL(string: "https://spasiba.vercel.app/api/user/sync?hardwareId=\(hardwareId)") {
            var request = URLRequest(url: syncUrl)
            request.timeoutInterval = 5.0
            if let (data, response) = try? await HttpClientProvider.shared.session.data(for: request),
               let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200,
               let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
                if let u = json["username"] as? String, !u.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty {
                    cloudUsername = u
                }
                if let b64 = json["photoBase64"] as? String, !b64.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty {
                    cloudPhotoUri = saveBase64ToFileUri(base64Str: b64)
                }
            }
        }

        let userDefaultsUsername = UserDefaults.standard.string(forKey: "spasiba_username")
        let finalUsername: String
        if let exU = existing?.username, !exU.isEmpty && exU != "Spasiba Listener" {
            finalUsername = exU
        } else if let cU = cloudUsername, !cU.isEmpty && cU != "Spasiba Listener" {
            finalUsername = cU
        } else if let udU = userDefaultsUsername, !udU.isEmpty && udU != "Spasiba Listener" {
            finalUsername = udU
        } else {
            finalUsername = !deviceModel.isEmpty ? deviceModel : "Spasiba Listener"
        }

        let finalPhotoUri = (existing?.photoUri?.isEmpty == false ? existing?.photoUri : nil)
            ?? cloudPhotoUri
            ?? UserDefaults.standard.string(forKey: "profile_photo_uri")

        let updatedProfile = UserProfileEntity(
            id: 1,
            username: finalUsername,
            visitorId: visitorId,
            hardwareDeviceId: hardwareId,
            macAddressOrImei: macOrImeiSurrogate,
            photoUri: finalPhotoUri,
            isSynced: true,
            registeredAt: existing?.registeredAt ?? Int64(Date().timeIntervalSince1970 * 1000),
            updatedAt: Int64(Date().timeIntervalSince1970 * 1000)
        )

        userProfileDao.insertOrUpdateProfile(updatedProfile)
        UserDefaults.standard.set(finalUsername, forKey: "spasiba_username")
        if let photoUri = finalPhotoUri, !photoUri.isEmpty {
            UserDefaults.standard.set(photoUri, forKey: "profile_photo_uri")
        }

        // Push profile to cloud DB keyed by both hardware ID & Eunoia ID
        Task.detached(priority: .background) { [weak self] in
            guard let self = self,
                  let pushUrl = URL(string: "https://spasiba.vercel.app/api/user/sync") else { return }
            var payload: [String: Any] = [
                "hardwareId": hardwareId,
                "visitorId": visitorId,
                "username": finalUsername
            ]
            if let pUri = finalPhotoUri, let b64 = self.uriToBase64(photoUriStr: pUri) {
                payload["photoBase64"] = b64
            }

            if let body = try? JSONSerialization.data(withJSONObject: payload) {
                var req = URLRequest(url: pushUrl)
                req.httpMethod = "POST"
                req.setValue("application/json", forHTTPHeaderField: "Content-Type")
                req.httpBody = body
                _ = try? await HttpClientProvider.shared.session.data(for: req)
            }
        }

        await MainActor.run {
            self.userProfile = updatedProfile
        }

        return updatedProfile
    }

    public func saveProfilePhoto(photoUri: String) async {
        let existing = userProfileDao.getUserProfileSync()
        let currentHwId = existing?.hardwareDeviceId ?? ""
        let currentMac = existing?.macAddressOrImei ?? ""

        let updated = UserProfileEntity(
            id: 1,
            username: existing?.username ?? "Spasiba Listener",
            visitorId: existing?.visitorId ?? "v_849201",
            hardwareDeviceId: currentHwId,
            macAddressOrImei: currentMac,
            photoUri: photoUri,
            isSynced: true,
            updatedAt: Int64(Date().timeIntervalSince1970 * 1000)
        )
        userProfileDao.insertOrUpdateProfile(updated)
        UserDefaults.standard.set(photoUri, forKey: "profile_photo_uri")

        await MainActor.run {
            self.userProfile = updated
        }

        // Push photo to Cloud Server
        if let b64 = uriToBase64(photoUriStr: photoUri), !b64.isEmpty, !currentHwId.isEmpty {
            Task.detached(priority: .background) {
                guard let url = URL(string: "https://spasiba.vercel.app/api/user/sync") else { return }
                let payload: [String: Any] = [
                    "hardwareId": currentHwId,
                    "username": existing?.username ?? "Spasiba Listener",
                    "photoBase64": b64
                ]
                if let body = try? JSONSerialization.data(withJSONObject: payload) {
                    var req = URLRequest(url: url)
                    req.httpMethod = "POST"
                    req.setValue("application/json", forHTTPHeaderField: "Content-Type")
                    req.httpBody = body
                    _ = try? await HttpClientProvider.shared.session.data(for: req)
                }
            }
        }
    }

    public func updateUsername(username: String) async {
        userProfileDao.updateUsername(username)
        UserDefaults.standard.set(username, forKey: "spasiba_username")

        let existing = userProfileDao.getUserProfileSync()
        let hwId = existing?.hardwareDeviceId ?? ""

        await MainActor.run {
            self.userProfile = existing
        }

        if !hwId.isEmpty {
            Task.detached(priority: .background) {
                guard let url = URL(string: "https://spasiba.vercel.app/api/user/sync") else { return }
                let payload: [String: Any] = [
                    "hardwareId": hwId,
                    "username": username
                ]
                if let body = try? JSONSerialization.data(withJSONObject: payload) {
                    var req = URLRequest(url: url)
                    req.httpMethod = "POST"
                    req.setValue("application/json", forHTTPHeaderField: "Content-Type")
                    req.httpBody = body
                    _ = try? await HttpClientProvider.shared.session.data(for: req)
                }
            }
        }
    }

    @discardableResult
    public func forceSyncWithRemoteCloud() async -> Bool {
        // Sync local DB changes with remote database server & update sync state
        await registerUserDeviceWithHardwareId()
        userProfileDao.updateSyncStatus(true)

        let localPlaylists = playlistDao.getAllPlaylistsSync()
        for pl in localPlaylists {
            if pl.isShared, let shareCode = pl.shareCode, !shareCode.isEmpty {
                if let parsed = ShareUtils.parsePlaylistShareCode(shareCode), !parsed.songs.isEmpty {
                    playlistDao.deleteAllSongsForPlaylist(pl.id)
                    for (index, song) in parsed.songs.enumerated() {
                        playlistDao.insertPlaylistSong(
                            PlaylistSongEntity(
                                playlistId: pl.id,
                                songId: song.id,
                                title: song.title,
                                author: song.author,
                                thumbnail: song.thumbnail,
                                durationSeconds: song.durationSeconds,
                                position: index
                            )
                        )
                    }
                }
            }
        }

        await MainActor.run {
            self.playlists = self.playlistDao.getAllPlaylists()
        }

        return true
    }
}
