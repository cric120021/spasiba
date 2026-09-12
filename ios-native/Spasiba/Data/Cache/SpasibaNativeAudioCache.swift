import Foundation
import os.log

// MARK: - Models from SpasibaNativeAudioCache.kt

public struct CachedAudio: Equatable {
    public let videoId: String
    public let file: URL
    public let mimeType: String

    public init(videoId: String, file: URL, mimeType: String) {
        self.videoId = videoId
        self.file = file
        self.mimeType = mimeType
    }
}

public struct StreamingAudio: Equatable {
    public let videoId: String
    public let url: String
    public let mimeType: String?
    public let headers: [String: String]
    public let durationSeconds: Int

    public init(
        videoId: String,
        url: String,
        mimeType: String? = nil,
        headers: [String: String] = [:],
        durationSeconds: Int = 0
    ) {
        self.videoId = videoId
        self.url = url
        self.mimeType = mimeType
        self.headers = headers
        self.durationSeconds = durationSeconds
    }
}

public struct ByteRange: Equatable {
    public let start: Int64
    public let endInclusive: Int64

    public init(start: Int64, endInclusive: Int64) {
        self.start = start
        self.endInclusive = endInclusive
    }
}

// MARK: - Spasiba Native Audio Cache

public final class SpasibaNativeAudioCache {
    public static let shared = SpasibaNativeAudioCache()

    public static let SPASIBA_HOST = "spasiba.vercel.app"
    public static let STREAM_ROUTE = "/api/stream"
    public static let AUDIO_ROUTE_SEGMENT = "__spasiba_native_audio"
    public static let CACHE_DIR_NAME = "spasiba-native-audio"
    public static let MAX_CACHED_TRACKS = 20
    public static let MAX_AUDIO_FILE_BYTES: Int64 = 30 * 1024 * 1024 // 30 MB
    public static let STREAM_CACHE_TTL: TimeInterval = 3 * 3600 // 3 hours
    public static let FORMAT_SELECTOR = "140/251/139/250/249/bestaudio[ext=m4a]/bestaudio/best"

    private static let VIDEO_ID_PATTERN = try! NSRegularExpression(pattern: "^[A-Za-z0-9_-]{6,32}$")

    private let audioDir: URL
    private let trackLocks = NSLock()
    private let resolveLocks = NSLock()
    private var streamCacheMap: [String: (stream: StreamingAudio, date: Date)] = [:]
    private var isSessionPrepared = false
    private let sessionLock = NSLock()

    public init() {
        let cachePath = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        self.audioDir = cachePath.appendingPathComponent(Self.CACHE_DIR_NAME, isDirectory: true)
        prepareSessionCache()
    }

    public func prepareSessionCache() {
        sessionLock.lock()
        defer { sessionLock.unlock() }

        if !isSessionPrepared {
            try? FileManager.default.createDirectory(at: audioDir, withIntermediateDirectories: true)
            evictOldAudioCacheIfNecessary()
            isSessionPrepared = true
            NSLog("[SpasibaNativeAudio] Initialized persistent 200MB LRU audio cache at %@", audioDir.path)
        }
    }

    private func evictOldAudioCacheIfNecessary() {
        let maxBytes: Int64 = 200 * 1024 * 1024 // 200 MB LRU
        guard let files = try? FileManager.default.contentsOfDirectory(
            at: audioDir,
            includingPropertiesForKeys: [.fileSizeKey, .contentModificationDateKey],
            options: .skipsHiddenFiles
        ) else { return }

        var totalBytes: Int64 = 0
        var fileAttrs: [(url: URL, size: Int64, date: Date)] = []

        for file in files {
            let values = try? file.resourceValues(forKeys: [.fileSizeKey, .contentModificationDateKey])
            let size = Int64(values?.fileSize ?? 0)
            let date = values?.contentModificationDate ?? Date.distantPast
            totalBytes += size
            fileAttrs.append((url: file, size: size, date: date))
        }

        if totalBytes > maxBytes {
            fileAttrs.sort { $0.date < $1.date }
            for item in fileAttrs {
                try? FileManager.default.removeItem(at: item.url)
                totalBytes -= item.size
                if totalBytes <= maxBytes { break }
            }
        }
    }

    public func invalidateStreamCache(_ rawVideoId: String) {
        guard let videoId = Self.normalizeVideoId(rawVideoId) else { return }
        resolveLocks.lock()
        streamCacheMap.removeValue(forKey: videoId)
        resolveLocks.unlock()
        NSLog("[SpasibaNativeAudio] Invalidated in-memory stream cache for videoId=%@", videoId)
    }

    public func resolveStreamingAudio(_ rawVideoId: String, forceRefresh: Bool = false) async throws -> StreamingAudio {
        guard let videoId = Self.normalizeVideoId(rawVideoId) else {
            throw NSError(domain: "SpasibaNativeAudioCache", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid YouTube video id."])
        }

        if forceRefresh {
            resolveLocks.lock()
            streamCacheMap.removeValue(forKey: videoId)
            resolveLocks.unlock()
        }

        resolveLocks.lock()
        if !forceRefresh, let cached = streamCacheMap[videoId], Date().timeIntervalSince(cached.date) < Self.STREAM_CACHE_TTL {
            resolveLocks.unlock()
            NSLog("[SpasibaNativeAudio] STREAM RESOLVE HIT IN-MEMORY CACHE VIDEO_ID=%@", videoId)
            return cached.stream
        }
        resolveLocks.unlock()

        guard let extracted = await SpasibaNativeExtractor.shared.resolveAudioStream(videoId: videoId) else {
            throw NSError(domain: "SpasibaNativeAudioCache", code: 502, userInfo: [NSLocalizedDescriptionKey: "Failed to extract audio stream for \(videoId)"])
        }

        let streamUrl = extracted.url
        let mimeType = Self.inferMimeTypeForExtension("m4a") ?? "audio/mp4"

        var finalHeaders = extracted.headers
        let matchingUa: String
        if streamUrl.contains("c=ANDROID_VR") {
            matchingUa = "com.google.android.apps.youtube.vr.oculus/1.59.19 (Linux; U; Android 12)"
        } else if streamUrl.contains("c=ANDROID_TESTSUITE") {
            matchingUa = "com.google.android.youtube/1.9.0 (Linux; U; Android 14)"
        } else if streamUrl.contains("c=ANDROID_MUSIC") {
            matchingUa = "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; gzip)"
        } else if streamUrl.contains("c=ANDROID") {
            matchingUa = "com.google.android.youtube/19.29.37 (Linux; U; Android 14)"
        } else if streamUrl.contains("c=IOS") {
            matchingUa = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X)"
        } else if streamUrl.contains("c=TVHTML5") || streamUrl.contains("c=TV") {
            matchingUa = "Mozilla/5.0 (SmartHub; SMART-TV; U; Linux/SmartTV) AppleWebKit/537.42"
        } else if streamUrl.contains("c=WEB_EMBEDDED") || streamUrl.contains("c=WEB") {
            matchingUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        } else {
            matchingUa = finalHeaders["User-Agent"] ?? "com.google.android.youtube/19.29.37 (Linux; U; Android 14)"
        }

        finalHeaders["User-Agent"] = matchingUa
        if streamUrl.contains("c=WEB_EMBEDDED") || streamUrl.contains("c=WEB") {
            finalHeaders["Origin"] = "https://www.youtube.com"
            finalHeaders["Referer"] = "https://www.youtube.com/"
        }

        let result = StreamingAudio(
            videoId: videoId,
            url: streamUrl,
            mimeType: mimeType,
            headers: finalHeaders,
            durationSeconds: 210
        )

        resolveLocks.lock()
        streamCacheMap[videoId] = (result, Date())
        resolveLocks.unlock()

        NSLog("[SpasibaNativeAudio] [AUDIO_STREAM_RESOLVE_SUCCESS] Resolved audio stream for videoId=%@ UA=%@", videoId, matchingUa)
        return result
    }

    public func prefetchStream(_ rawVideoId: String) {
        guard let videoId = Self.normalizeVideoId(rawVideoId) else { return }
        resolveLocks.lock()
        if let cached = streamCacheMap[videoId], Date().timeIntervalSince(cached.date) < Self.STREAM_CACHE_TTL {
            resolveLocks.unlock()
            return
        }
        resolveLocks.unlock()

        Task.detached(priority: .background) {
            _ = try? await self.resolveStreamingAudio(videoId)
        }
    }

    public func getOrDownload(_ rawVideoId: String) async throws -> CachedAudio {
        prepareSessionCache()
        guard let videoId = Self.normalizeVideoId(rawVideoId) else {
            throw NSError(domain: "SpasibaNativeAudioCache", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid YouTube video id."])
        }

        if let cached = findCachedAudioFile(videoId: videoId) {
            updateLastModified(for: cached)
            return CachedAudio(videoId: videoId, file: cached, mimeType: Self.inferMimeType(for: cached))
        }

        return try await downloadAudio(videoId: videoId)
    }

    public func downloadAudio(videoId: String) async throws -> CachedAudio {
        deleteTrackFiles(videoId: videoId)

        let streaming = try await resolveStreamingAudio(videoId)
        guard let url = URL(string: streaming.url) else {
            throw NSError(domain: "SpasibaNativeAudioCache", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid stream url."])
        }

        let tempFile = audioDir.appendingPathComponent("\(videoId).part")
        let finalFile = audioDir.appendingPathComponent("\(videoId).m4a")

        try? FileManager.default.removeItem(at: tempFile)
        try? FileManager.default.removeItem(at: finalFile)

        var request = URLRequest(url: url)
        request.timeoutInterval = 30.0
        for (k, v) in streaming.headers {
            request.setValue(v, forHTTPHeaderField: k)
        }

        let (downloadedLocation, response) = try await HttpClientProvider.shared.session.download(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            try? FileManager.default.removeItem(at: downloadedLocation)
            throw NSError(domain: "SpasibaNativeAudioCache", code: 502, userInfo: [NSLocalizedDescriptionKey: "Audio download returned error."])
        }

        let fileSize = (try? FileManager.default.attributesOfItem(atPath: downloadedLocation.path)[.size] as? Int64) ?? 0
        if fileSize > Self.MAX_AUDIO_FILE_BYTES {
            try? FileManager.default.removeItem(at: downloadedLocation)
            throw NSError(domain: "SpasibaNativeAudioCache", code: 413, userInfo: [NSLocalizedDescriptionKey: "Audio file exceeds maximum allowed size."])
        }

        try FileManager.default.moveItem(at: downloadedLocation, to: finalFile)
        updateLastModified(for: finalFile)
        pruneCache()

        NSLog("[SpasibaNativeAudio] DOWNLOAD FINISHED VIDEO_ID=%@ BYTES=%lld FILE=%@", videoId, fileSize, finalFile.lastPathComponent)
        return CachedAudio(videoId: videoId, file: finalFile, mimeType: Self.inferMimeType(for: finalFile))
    }

    public func findCachedAudioFile(videoId: String) -> URL? {
        guard let files = try? FileManager.default.contentsOfDirectory(
            at: audioDir,
            includingPropertiesForKeys: [.contentModificationDateKey, .fileSizeKey],
            options: .skipsHiddenFiles
        ) else { return nil }

        let matching = files.filter { file in
            let name = file.lastPathComponent
            let size = (try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
            return name.hasPrefix("\(videoId).") && !name.hasSuffix(".part") && !name.hasSuffix(".ytdl") && size > 0
        }

        return matching.max { a, b in
            let dateA = (try? a.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? Date.distantPast
            let dateB = (try? b.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? Date.distantPast
            return dateA < dateB
        }
    }

    public func deleteTrackFiles(videoId: String) {
        guard let files = try? FileManager.default.contentsOfDirectory(at: audioDir, includingPropertiesForKeys: nil) else { return }
        for file in files where file.lastPathComponent.hasPrefix("\(videoId).") {
            try? FileManager.default.removeItem(at: file)
        }
    }

    public func pruneCache() {
        guard let files = try? FileManager.default.contentsOfDirectory(
            at: audioDir,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: .skipsHiddenFiles
        ) else { return }

        let sorted = files
            .filter { !($0.lastPathComponent.hasSuffix(".part") || $0.lastPathComponent.hasSuffix(".ytdl")) }
            .sorted { a, b in
                let dateA = (try? a.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? Date.distantPast
                let dateB = (try? b.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? Date.distantPast
                return dateA > dateB
            }

        if sorted.count > Self.MAX_CACHED_TRACKS {
            for file in sorted.dropFirst(Self.MAX_CACHED_TRACKS) {
                try? FileManager.default.removeItem(at: file)
            }
        }
    }

    public func buildNativeAudioUrl(cached: CachedAudio) -> String {
        let encodedId = cached.videoId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cached.videoId
        let encodedName = cached.file.lastPathComponent.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cached.file.lastPathComponent
        return "https://\(Self.SPASIBA_HOST)/\(Self.AUDIO_ROUTE_SEGMENT)/\(encodedId)/\(encodedName)"
    }

    private func updateLastModified(for file: URL) {
        try? FileManager.default.setAttributes([.modificationDate: Date()], ofItemAtPath: file.path)
    }

    // MARK: - Normalization & Mime Types

    public static func normalizeVideoId(_ rawValue: String?) -> String? {
        let value = (rawValue ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if value.isEmpty { return nil }

        let range = NSRange(location: 0, length: value.utf16.count)
        if VIDEO_ID_PATTERN.firstMatch(in: value, options: [], range: range) != nil {
            return value
        }

        guard let components = URLComponents(string: value) else { return nil }
        if let queryId = components.queryItems?.first(where: { $0.name == "v" })?.value,
           VIDEO_ID_PATTERN.firstMatch(in: queryId, options: [], range: NSRange(location: 0, length: queryId.utf16.count)) != nil {
            return queryId
        }

        if components.host?.contains("youtu.be") == true {
            let pathSegments = components.path.components(separatedBy: "/").filter { !$0.isEmpty }
            if let first = pathSegments.first,
               VIDEO_ID_PATTERN.firstMatch(in: first, options: [], range: NSRange(location: 0, length: first.utf16.count)) != nil {
                return first
            }
        }

        let pathSegments = components.path.components(separatedBy: "/").filter { !$0.isEmpty }
        if pathSegments.count >= 2 && ["embed", "shorts", "live"].contains(pathSegments[0]) {
            let second = pathSegments[1]
            if VIDEO_ID_PATTERN.firstMatch(in: second, options: [], range: NSRange(location: 0, length: second.utf16.count)) != nil {
                return second
            }
        }

        return nil
    }

    public static func inferMimeType(for file: URL) -> String {
        let ext = file.pathExtension.lowercased()
        return inferMimeTypeForExtension(ext) ?? "application/octet-stream"
    }

    public static func inferMimeTypeForExtension(_ extensionStr: String?) -> String? {
        guard let ext = extensionStr?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() else {
            return nil
        }
        let cleanExt = ext.hasPrefix(".") ? String(ext.dropFirst()) : ext
        switch cleanExt {
        case "m4a", "mp4", "aac":
            return "audio/mp4"
        case "webm":
            return "audio/webm"
        case "mp3":
            return "audio/mpeg"
        case "ogg", "opus":
            return "audio/ogg"
        default:
            return nil
        }
    }

    public static func parseRange(rangeHeader: String?, fileLength: Int64) -> ByteRange? {
        guard let header = rangeHeader, header.hasPrefix("bytes="), fileLength > 0 else {
            return nil
        }

        let parts = header.replacingOccurrences(of: "bytes=", with: "").components(separatedBy: "-")
        guard parts.count == 2 else { return nil }

        let start = Int64(parts[0])
        let end = Int64(parts[1])

        if let s = start {
            let safeStart = max(0, min(fileLength - 1, s))
            let safeEnd = max(safeStart, min(fileLength - 1, end ?? (fileLength - 1)))
            return ByteRange(start: safeStart, endInclusive: safeEnd)
        } else if let e = end {
            let suffixLen = max(1, min(fileLength, e))
            return ByteRange(start: fileLength - suffixLen, endInclusive: fileLength - 1)
        }

        return nil
    }

    public static func jsonEscape(_ value: String) -> String {
        var output = ""
        output.reserveCapacity(value.count + 16)
        for char in value {
            switch char {
            case "\\": output.append("\\\\")
            case "\"": output.append("\\\"")
            case "\n": output.append("\\n")
            case "\r": output.append("\\r")
            case "\t": output.append("\\t")
            default:
                if let scalar = char.unicodeScalars.first, scalar.value < 0x20 {
                    output.append(String(format: "\\u%04x", scalar.value))
                } else {
                    output.append(char)
                }
            }
        }
        return output
    }
}
