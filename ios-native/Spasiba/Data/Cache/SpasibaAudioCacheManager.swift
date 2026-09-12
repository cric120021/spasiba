import Foundation

// MARK: - Models from SpasibaAudioCacheManager.kt

public struct LocalCachedAudio: Equatable {
    public let videoId: String
    public let file: URL

    public init(videoId: String, file: URL) {
        self.videoId = videoId
        self.file = file
    }
}

// MARK: - Spasiba Audio Cache Manager

public final class SpasibaAudioCacheManager {
    public static let shared = SpasibaAudioCacheManager()

    private let nativeCache = SpasibaNativeAudioCache.shared

    public init() {}

    public func getOrDownloadAudio(videoId: String) async throws -> LocalCachedAudio {
        let cached = try await nativeCache.getOrDownload(videoId)
        return LocalCachedAudio(videoId: cached.videoId, file: cached.file)
    }

    public func findCachedAudioFile(videoId: String) -> URL? {
        return nativeCache.findCachedAudioFile(videoId: videoId)
    }

    public func deleteTrackFiles(videoId: String) {
        nativeCache.deleteTrackFiles(videoId: videoId)
    }

    public func pruneCache() {
        nativeCache.pruneCache()
    }

    public func clearAll() {
        nativeCache.pruneCache()
    }

    public func clearCache() {
        nativeCache.pruneCache()
    }
}
