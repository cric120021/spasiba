import Foundation

public struct LyricsLine: Identifiable, Equatable {
    public var id: Double { timestampSeconds }
    public let timestampSeconds: Double
    public let text: String

    public init(timestampSeconds: Double, text: String) {
        self.timestampSeconds = timestampSeconds
        self.text = text
    }
}

public final class LrcLyricsFetcher {
    public static let shared = LrcLyricsFetcher()

    private var lyricsCache: [String: [LyricsLine]] = [:]

    private init() {}

    public func fetchLyrics(trackName: String, artistName: String, durationSeconds: Int) async -> [LyricsLine] {
        let cacheKey = "\(trackName.lowercased())_\(artistName.lowercased())"
        if let cached = lyricsCache[cacheKey] {
            return cached
        }

        guard let encodedTrack = trackName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let encodedArtist = artistName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://lrclib.net/api/get?track_name=\(encodedTrack)&artist_name=\(encodedArtist)&duration=\(durationSeconds)") else {
            return []
        }

        do {
            let (data, response) = try await HttpClientProvider.shared.session.data(from: url)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else { return [] }

            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
               let syncedLyrics = json["syncedLyrics"] as? String {
                let parsed = parseLrc(syncedLyrics)
                if !parsed.isEmpty {
                    lyricsCache[cacheKey] = parsed
                }
                return parsed
            }
        } catch {
            return []
        }

        return []
    }

    public func parseLrc(_ lrcText: String) -> [LyricsLine] {
        var lines: [LyricsLine] = []
        let rawLines = lrcText.components(separatedBy: .newlines)

        for line in rawLines {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            guard trimmed.hasPrefix("[") && trimmed.contains("]") else { continue }

            let parts = trimmed.components(separatedBy: "]")
            guard parts.count >= 2 else { continue }

            let timeStr = parts[0].replacingOccurrences(of: "[", with: "")
            let text = parts[1...].joined(separator: "]").trimmingCharacters(in: .whitespaces)

            if let seconds = parseTimestamp(timeStr) {
                lines.append(LyricsLine(timestampSeconds: seconds, text: text))
            }
        }

        return lines.sorted { $0.timestampSeconds < $1.timestampSeconds }
    }

    private func parseTimestamp(_ str: String) -> Double? {
        let timeParts = str.components(separatedBy: ":")
        guard timeParts.count == 2,
              let minutes = Double(timeParts[0]),
              let seconds = Double(timeParts[1]) else {
            return nil
        }
        return (minutes * 60.0) + seconds
    }
}
