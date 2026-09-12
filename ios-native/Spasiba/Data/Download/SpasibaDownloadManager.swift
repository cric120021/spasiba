import Foundation
import Combine
import Network

// MARK: - Models from SpasibaDownloadManager.kt

public enum DownloadType: String, Codable, CaseIterable {
    case audioOnly = "AUDIO_ONLY"
    case videoAndAudio = "VIDEO_AND_AUDIO"
}

public enum DownloadStatus: String, Codable, CaseIterable {
    case downloading = "DOWNLOADING"
    case paused = "PAUSED"
    case completed = "COMPLETED"
    case cancelled = "CANCELLED"
    case failed = "FAILED"
}

public struct DownloadItem: Identifiable, Codable, Equatable {
    public var id: String { song.id }
    public let song: Song
    public let type: DownloadType
    public var quality: String
    public var progress: Float
    public var status: DownloadStatus
    public var localFilePath: String?
    public var startedAt: Int64

    public init(
        song: Song,
        type: DownloadType = .audioOnly,
        quality: String = "720p",
        progress: Float = 0.0,
        status: DownloadStatus = .downloading,
        localFilePath: String? = nil,
        startedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.song = song
        self.type = type
        self.quality = quality
        self.progress = progress
        self.status = status
        self.localFilePath = localFilePath
        self.startedAt = startedAt
    }
}

// MARK: - Spasiba Download Manager

public final class SpasibaDownloadManager: ObservableObject {
    public static let shared = SpasibaDownloadManager()

    @Published public private(set) var downloads: [String: DownloadItem] = [:]

    private let fileManager = FileManager.default
    private let downloadsDir: URL
    private let manifestFile: URL
    private let lock = NSLock()
    private var downloadTasks: [String: Task<Void, Never>] = [:]

    private let pathMonitor = NWPathMonitor()
    private let monitorQueue = DispatchQueue(label: "app.spasiba.network_monitor")
    private var isNetworkConnected: Bool = true

    public init() {
        let baseDir = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first!
        self.downloadsDir = baseDir.appendingPathComponent("spasiba_downloads", isDirectory: true)
        self.manifestFile = baseDir.appendingPathComponent("downloads_manifest.json")

        if !fileManager.fileExists(atPath: downloadsDir.path) {
            try? fileManager.createDirectory(at: downloadsDir, withIntermediateDirectories: true)
        }

        pathMonitor.pathUpdateHandler = { [weak self] path in
            self?.isNetworkConnected = (path.status == .satisfied)
        }
        pathMonitor.start(queue: monitorQueue)

        loadManifest()
    }

    public static func isNetworkAvailable() -> Bool {
        return shared.isNetworkConnected
    }

    public func startDownload(song: Song, type: DownloadType = .audioOnly, quality: String = "720p") {
        lock.lock()
        if let existing = downloads[song.id], existing.status == .completed {
            lock.unlock()
            return
        }

        let newItem = DownloadItem(
            song: song,
            type: type,
            quality: quality,
            progress: 0.0,
            status: .downloading
        )
        updateItem(newItem)
        lock.unlock()

        let ext = (type == .audioOnly) ? "m4a" : "mp4"
        let targetFile = downloadsDir.appendingPathComponent("\(song.id)_\(type.rawValue.lowercased()).\(ext)")

        let task = Task {
            do {
                if type == .audioOnly {
                    try await performAudioDownload(song: song, targetFile: targetFile, initialItem: newItem)
                } else {
                    try await performVideoDownload(song: song, quality: quality, targetFile: targetFile, initialItem: newItem)
                }
            } catch {
                NSLog("[SpasibaDownloadManager] Download failed for %@: %@", song.id, error.localizedDescription)
                var failedItem = newItem
                failedItem.status = .failed
                self.updateItem(failedItem)
            }
            self.lock.lock()
            self.downloadTasks.removeValue(forKey: song.id)
            self.lock.unlock()
        }

        lock.lock()
        downloadTasks[song.id] = task
        lock.unlock()
    }

    private func performAudioDownload(song: Song, targetFile: URL, initialItem: DownloadItem) async throws {
        // Resolve audio stream
        guard let stream = await SpasibaNativeExtractor.shared.resolveAudioStream(videoId: song.id),
              let streamUrl = URL(string: stream.url) else {
            throw NSError(domain: "SpasibaDownloadManager", code: 404, userInfo: [NSLocalizedDescriptionKey: "Failed to resolve audio stream URL."])
        }

        try await downloadStreamToFile(songId: song.id, url: streamUrl, headers: stream.headers, targetFile: targetFile, startPct: 0.0, endPct: 100.0)

        var completedItem = initialItem
        completedItem.progress = 100.0
        completedItem.status = .completed
        completedItem.localFilePath = targetFile.path
        updateItem(completedItem)
    }

    private func performVideoDownload(song: Song, quality: String, targetFile: URL, initialItem: DownloadItem) async throws {
        let videoRes = await YouTubeMusicApi.resolveVideoStreamResult(videoId: song.id, quality: quality)
        let audioRes = await YouTubeMusicApi.resolveStreamResult(videoId: song.id)

        if let vRes = videoRes, let vUrl = URL(string: vRes.url),
           let aRes = audioRes, let aUrl = URL(string: aRes.url) {
            let videoTemp = downloadsDir.appendingPathComponent("\(song.id)_video_temp.mp4")
            let audioTemp = downloadsDir.appendingPathComponent("\(song.id)_audio_temp.m4a")

            // Download Video Track (0% -> 50%)
            try await downloadStreamToFile(songId: song.id, url: vUrl, headers: vRes.headers, targetFile: videoTemp, startPct: 0.0, endPct: 50.0)

            // Download Audio Track (50% -> 90%)
            try await downloadStreamToFile(songId: song.id, url: aUrl, headers: aRes.headers, targetFile: audioTemp, startPct: 50.0, endPct: 90.0)

            // Merge / fallback
            var current = initialItem
            current.progress = 95.0
            updateItem(current)

            // If separate audio and video downloaded, write video track or move to destination
            if fileManager.fileExists(atPath: targetFile.path) {
                try? fileManager.removeItem(at: targetFile)
            }
            try fileManager.moveItem(at: videoTemp, to: targetFile)
            try? fileManager.removeItem(at: audioTemp)

            current.progress = 100.0
            current.status = .completed
            current.localFilePath = targetFile.path
            updateItem(current)
        } else if let vRes = videoRes, let vUrl = URL(string: vRes.url) {
            try await downloadStreamToFile(songId: song.id, url: vUrl, headers: vRes.headers, targetFile: targetFile, startPct: 0.0, endPct: 100.0)
            var current = initialItem
            current.progress = 100.0
            current.status = .completed
            current.localFilePath = targetFile.path
            updateItem(current)
        } else {
            throw NSError(domain: "SpasibaDownloadManager", code: 404, userInfo: [NSLocalizedDescriptionKey: "Failed to resolve video streams."])
        }
    }

    private func downloadStreamToFile(
        songId: String,
        url: URL,
        headers: [String: String],
        targetFile: URL,
        startPct: Float,
        endPct: Float
    ) async throws {
        var request = URLRequest(url: url)
        request.timeoutInterval = 60.0
        for (k, v) in headers {
            request.setValue(v, forHTTPHeaderField: k)
        }

        let (asyncBytes, response) = try await HttpClientProvider.shared.session.bytes(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            throw NSError(domain: "SpasibaDownloadManager", code: 502, userInfo: [NSLocalizedDescriptionKey: "Bad response status code."])
        }

        let expectedLength = httpResponse.expectedContentLength
        let tempFile = downloadsDir.appendingPathComponent("\(songId)_\(UUID().uuidString).tmp")

        if fileManager.fileExists(atPath: tempFile.path) {
            try? fileManager.removeItem(at: tempFile)
        }
        fileManager.createFile(atPath: tempFile.path, contents: nil)

        guard let handle = try? FileHandle(forWritingTo: tempFile) else {
            throw NSError(domain: "SpasibaDownloadManager", code: 500, userInfo: [NSLocalizedDescriptionKey: "Failed to create file handle."])
        }
        defer { try? handle.close() }

        var downloadedBytes: Int64 = 0
        var lastReportedPct: Float = startPct
        var buffer = Data()
        buffer.reserveCapacity(64 * 1024)

        for try await byte in asyncBytes {
            buffer.append(byte)
            downloadedBytes += 1

            if buffer.count >= 64 * 1024 {
                handle.write(buffer)
                buffer.removeAll(keepingCapacity: true)

                if expectedLength > 0 {
                    let fraction = Float(downloadedBytes) / Float(expectedLength)
                    let currentPct = startPct + (fraction * (endPct - startPct))
                    if currentPct - lastReportedPct >= 2.0 || currentPct >= endPct {
                        lastReportedPct = currentPct
                        self.updateItemProgress(songId: songId, progress: min(currentPct, 99.0))
                    }
                }
            }
        }

        if !buffer.isEmpty {
            handle.write(buffer)
        }

        if fileManager.fileExists(atPath: targetFile.path) {
            try? fileManager.removeItem(at: targetFile)
        }
        try fileManager.moveItem(at: tempFile, to: targetFile)
    }

    private func updateItemProgress(songId: String, progress: Float) {
        lock.lock()
        defer { lock.unlock() }
        guard var item = downloads[songId], item.status == .downloading else { return }
        item.progress = progress
        downloads[songId] = item
        DispatchQueue.main.async {
            self.objectWillChange.send()
        }
    }

    public func pauseDownload(videoId: String) {
        lock.lock()
        if let task = downloadTasks[videoId] {
            task.cancel()
            downloadTasks.removeValue(forKey: videoId)
        }
        if var current = downloads[videoId], current.status == .downloading {
            current.status = .paused
            downloads[videoId] = current
            saveManifest()
            DispatchQueue.main.async { self.objectWillChange.send() }
        }
        lock.unlock()
    }

    public func resumeDownload(videoId: String) {
        lock.lock()
        guard let current = downloads[videoId], current.status == .paused || current.status == .failed else {
            lock.unlock()
            return
        }
        lock.unlock()
        startDownload(song: current.song, type: current.type, quality: current.quality)
    }

    public func cancelDownload(videoId: String) {
        lock.lock()
        if let task = downloadTasks[videoId] {
            task.cancel()
            downloadTasks.removeValue(forKey: videoId)
        }
        deleteLocalFile(videoId: videoId)
        downloads.removeValue(forKey: videoId)
        saveManifest()
        DispatchQueue.main.async { self.objectWillChange.send() }
        lock.unlock()
    }

    public func cancelDownload(songId: String) {
        cancelDownload(videoId: songId)
    }

    public func deleteDownload(videoId: String) {
        cancelDownload(videoId: videoId)
    }

    public func deleteDownload(songId: String) {
        deleteDownload(videoId: songId)
    }

    public func getDownloadedFile(songId: String) -> URL? {
        return getDownloadedFile(videoId: songId)
    }

    public func getDownloadedSongs() -> [Song] {
        lock.lock()
        defer { lock.unlock() }
        return downloads.values
            .filter { item in
                item.status == .completed &&
                item.localFilePath != nil &&
                fileManager.fileExists(atPath: item.localFilePath!)
            }
            .map { $0.song }
    }

    public func getDownloadedFile(videoId: String) -> URL? {
        lock.lock()
        defer { lock.unlock() }
        guard let item = downloads[videoId], item.status == .completed, let path = item.localFilePath else {
            return nil
        }
        let fileUrl = URL(fileURLWithPath: path)
        return fileManager.fileExists(atPath: fileUrl.path) ? fileUrl : nil
    }

    public func isDownloaded(songId: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return downloads[songId]?.status == .completed
    }

    public func getDownloadedFileUrl(songId: String) -> URL? {
        return getDownloadedFile(videoId: songId)
    }

    private func deleteLocalFile(videoId: String) {
        guard let files = try? fileManager.contentsOfDirectory(at: downloadsDir, includingPropertiesForKeys: nil) else { return }
        for file in files where file.lastPathComponent.hasPrefix(videoId) {
            try? fileManager.removeItem(at: file)
        }
    }

    private func updateItem(_ item: DownloadItem) {
        lock.lock()
        downloads[item.song.id] = item
        saveManifest()
        lock.unlock()
        DispatchQueue.main.async {
            self.objectWillChange.send()
        }
    }

    private func saveManifest() {
        let items = Array(downloads.values)
        guard let data = try? JSONEncoder().encode(items) else { return }
        try? data.write(to: manifestFile, options: .atomic)
    }

    private func loadManifest() {
        guard fileManager.fileExists(atPath: manifestFile.path),
              let data = try? Data(contentsOf: manifestFile),
              let items = try? JSONDecoder().decode([DownloadItem].self, from: data) else {
            return
        }

        var map: [String: DownloadItem] = [:]
        for var item in items {
            if item.status == .downloading {
                item.status = .paused
            }
            map[item.song.id] = item
        }
        downloads = map
    }
}
