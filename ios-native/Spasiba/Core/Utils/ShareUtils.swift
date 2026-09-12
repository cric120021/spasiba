import Foundation
#if canImport(UIKit)
import UIKit
#endif

public struct SharedPlaylistData: Equatable {
    public let name: String
    public let songs: [Song]
    public let shareCode: String

    public init(name: String, songs: [Song], shareCode: String) {
        self.name = name
        self.songs = songs
        self.shareCode = shareCode
    }
}

public enum ShareUtils {

    public static func shareSong(song: Song) {
        let encodedTitle = song.title.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? song.title
        let encodedAuthor = song.author.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? song.author
        let encodedThumb = song.thumbnail.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? song.thumbnail
        let songUrl = "https://spasiba.vercel.app/track/\(song.id)?title=\(encodedTitle)&author=\(encodedAuthor)&art=\(encodedThumb)&dur=\(song.durationSeconds)"
        let shareText = "🎵 Listening to '\(song.title)' by \(song.author) on Spasiba Music:\n\(songUrl)"

        #if canImport(UIKit)
        DispatchQueue.main.async {
            guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                  let rootVC = windowScene.windows.first?.rootViewController else { return }
            let activityVC = UIActivityViewController(activityItems: [shareText], applicationActivities: nil)
            rootVC.present(activityVC, animated: true)
        }
        #endif
    }

    public static func sharePlaylist(name: String, songs: [Song], shareCode: String? = nil) {
        sharePlaylist(playlistName: name, songs: songs, shareCode: shareCode)
    }

    public static func sharePlaylist(playlistName: String, songs: [Song], shareCode: String? = nil) {
        let code = shareCode ?? "SPASIBA-\(String(playlistName.prefix(3)).uppercased())-\(songs.count)"

        var tracksArray: [[String: Any]] = []
        for s in songs {
            let obj: [String: Any] = [
                "id": s.id,
                "t": s.title,
                "a": s.author,
                "d": s.durationSeconds,
                "m": s.thumbnail
            ]
            tracksArray.append(obj)
        }

        let rootObj: [String: Any] = [
            "name": playlistName,
            "code": code,
            "tracks": tracksArray
        ]

        var encodedPayload = ""
        if let jsonData = try? JSONSerialization.data(withJSONObject: rootObj) {
            encodedPayload = jsonData.base64EncodedString()
        }

        let deepLinkUrl = "spasiba://playlist?code=\(code)&payload=\(encodedPayload)"
        let trackPreview = songs.prefix(3).map { "• \($0.title) - \($0.author)" }.joined(separator: "\n")
        let moreCount = songs.count > 3 ? "... and \(songs.count - 3) more" : ""
        let shareText = """
        🎶 Check out my playlist '\(playlistName)' (\(songs.count) tracks) on Spasiba Music!

        Tracks Preview:
        \(trackPreview)
        \(moreCount)

        Import Code: \(code)
        Deep Link: \(deepLinkUrl)
        """.trimmingCharacters(in: .whitespacesAndNewlines)

        #if canImport(UIKit)
        DispatchQueue.main.async {
            guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                  let rootVC = windowScene.windows.first?.rootViewController else { return }
            let activityVC = UIActivityViewController(activityItems: [shareText], applicationActivities: nil)
            rootVC.present(activityVC, animated: true)
        }
        #endif
    }

    public static func parsePlaylistShareCode(_ input: String) -> SharedPlaylistData? {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return nil }

        // Case 1: Deep Link containing payload query param
        if trimmed.contains("payload=") {
            if let components = URLComponents(string: trimmed),
               let payloadItem = components.queryItems?.first(where: { $0.name == "payload" }),
               let payload = payloadItem.value,
               let data = Data(base64Encoded: payload) {
                if let jsonStr = String(data: data, encoding: .utf8),
                   let result = parsePlaylistJson(jsonStr) {
                    return result
                }
            }
        }

        // Case 2: Direct Base64 JSON Payload
        if trimmed.count > 20 && !trimmed.contains(" ") {
            if let data = Data(base64Encoded: trimmed),
               let jsonStr = String(data: data, encoding: .utf8),
               let result = parsePlaylistJson(jsonStr) {
                return result
            }
        }

        // Case 3: Raw JSON string
        if trimmed.hasPrefix("{") && trimmed.hasSuffix("}") {
            if let result = parsePlaylistJson(trimmed) {
                return result
            }
        }

        // Case 4: Plain text / Code / Search title fallback
        let codeName = trimmed.hasPrefix("SPASIBA-") ? trimmed : "Imported Playlist (\(trimmed))"
        return SharedPlaylistData(
            name: codeName,
            songs: [],
            shareCode: trimmed
        )
    }

    private static func parsePlaylistJson(_ jsonStr: String) -> SharedPlaylistData? {
        guard let data = jsonStr.data(using: .utf8),
              let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            return nil
        }

        let name = (root["name"] as? String) ?? "Shared Playlist"
        let code = (root["code"] as? String) ?? "SPASIBA-IMPORT"
        let tracksArray = (root["tracks"] as? [[String: Any]]) ?? []

        var songs: [Song] = []
        for item in tracksArray {
            guard let id = item["id"] as? String else { continue }
            let title = (item["t"] as? String) ?? "Unknown Track"
            let author = (item["a"] as? String) ?? "Unknown Artist"
            let duration = (item["d"] as? Int) ?? 180
            let thumbnail = (item["m"] as? String) ?? "https://i.ytimg.com/vi/\(id)/hqdefault.jpg"

            songs.append(Song(id: id, title: title, author: author, thumbnail: thumbnail, durationSeconds: duration))
        }

        return SharedPlaylistData(name: name, songs: songs, shareCode: code)
    }
}
