import Foundation

public struct ExtractedStream: Equatable {
    public let url: String
    public let audioUrl: String?
    public let headers: [String: String]
    public let isMuxed: Bool
    public let qualityLabel: String
    public let bitrate: Int64
    public let clientName: String

    public init(
        url: String,
        audioUrl: String? = nil,
        headers: [String: String] = [:],
        isMuxed: Bool = false,
        qualityLabel: String = "audio",
        bitrate: Int64 = 0,
        clientName: String = "ANDROID_VR"
    ) {
        self.url = url
        self.audioUrl = audioUrl
        self.headers = headers
        self.isMuxed = isMuxed
        self.qualityLabel = qualityLabel
        self.bitrate = bitrate
        self.clientName = clientName
    }
}

public final class SpasibaNativeExtractor {
    public static let shared = SpasibaNativeExtractor()

    private let cacheLock = NSLock()
    private var streamCache: [String: (stream: ExtractedStream, timestamp: Date)] = [:]
    private let cacheTTL: TimeInterval = 20 * 60 // 20 minutes

    private let visitorStorageKey = "spasiba_cached_visitor_id"
    private var cachedVisitorId: String?

    private let androidVrUserAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    private let defaultWebUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private init() {
        cachedVisitorId = UserDefaults.standard.string(forKey: visitorStorageKey)
        if cachedVisitorId == nil || cachedVisitorId?.isEmpty == true {
            Task {
                _ = await self.getVisitorId(forceRefresh: true)
            }
        }
    }

    // MARK: - Visitor ID Token Resolution
    public func getVisitorId(forceRefresh: Bool = false) async -> String? {
        if !forceRefresh, let cached = cachedVisitorId, !cached.isEmpty {
            return cached
        }

        guard let url = URL(string: "https://www.youtube.com") else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue(defaultWebUserAgent, forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 8.0

        do {
            let (data, response) = try await HttpClientProvider.shared.session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200,
                  let html = String(data: data, encoding: .utf8) else {
                return nil
            }

            let pattern = "\"VISITOR_DATA\":\"([^\"]+)\""
            if let regex = try? NSRegularExpression(pattern: pattern),
               let match = regex.firstMatch(in: html, range: NSRange(location: 0, length: html.utf16.count)),
               let range = Range(match.range(at: 1), in: html) {
                let visitorId = String(html[range])
                self.cachedVisitorId = visitorId
                UserDefaults.standard.set(visitorId, forKey: visitorStorageKey)
                return visitorId
            }
        } catch {
            // Silently fall back to cached
        }

        return cachedVisitorId
    }

    // MARK: - Audio Stream Resolution
    public func resolveAudioStream(videoId: String) async -> ExtractedStream? {
        let cleanId = videoId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty else { return nil }

        let cacheKey = "audio_\(cleanId)"
        cacheLock.lock()
        if let cached = streamCache[cacheKey], Date().timeIntervalSince(cached.timestamp) < cacheTTL {
            cacheLock.unlock()
            return cached.stream
        }
        cacheLock.unlock()

        // 1. Primary Attempt: Android VR Client with cached Visitor ID
        if let stream = await attemptAndroidVrAudio(videoId: cleanId, forceRefreshVisitor: false) {
            cacheStream(key: cacheKey, stream: stream)
            return stream
        }

        // 2. Retry Attempt: Refresh Visitor ID and re-attempt Android VR
        if let stream = await attemptAndroidVrAudio(videoId: cleanId, forceRefreshVisitor: true) {
            cacheStream(key: cacheKey, stream: stream)
            return stream
        }

        // 3. Tertiary Fallback: Invidious mirror endpoints
        if let stream = await attemptInvidiousFallback(videoId: cleanId) {
            cacheStream(key: cacheKey, stream: stream)
            return stream
        }

        return nil
    }

    // MARK: - Video Stream Resolution
    public func resolveVideoStream(videoId: String, quality: String = "720p") async -> ExtractedStream? {
        let cleanId = videoId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty else { return nil }

        let cacheKey = "video_\(cleanId)_\(quality)"
        cacheLock.lock()
        if let cached = streamCache[cacheKey], Date().timeIntervalSince(cached.timestamp) < cacheTTL {
            cacheLock.unlock()
            return cached.stream
        }
        cacheLock.unlock()

        // Primary Attempt: Android VR Video
        if let stream = await attemptAndroidVrVideo(videoId: cleanId, quality: quality, forceRefreshVisitor: false) {
            cacheStream(key: cacheKey, stream: stream)
            return stream
        }

        // Retry Attempt with fresh Visitor ID
        if let stream = await attemptAndroidVrVideo(videoId: cleanId, quality: quality, forceRefreshVisitor: true) {
            cacheStream(key: cacheKey, stream: stream)
            return stream
        }

        return nil
    }

    private func cacheStream(key: String, stream: ExtractedStream) {
        cacheLock.lock()
        streamCache[key] = (stream, Date())
        cacheLock.unlock()
    }

    // MARK: - ANDROID_VR InnerTube Audio Extraction
    private func attemptAndroidVrAudio(videoId: String, forceRefreshVisitor: Bool) async -> ExtractedStream? {
        guard let visitorId = await getVisitorId(forceRefresh: forceRefreshVisitor) else { return nil }
        guard let playerUrl = URL(string: "https://www.youtube.com/youtubei/v1/player?prettyPrint=false") else { return nil }

        let payload: [String: Any] = [
            "videoId": videoId,
            "contentCheckOk": true,
            "racyCheckOk": true,
            "context": [
                "client": [
                    "clientName": "ANDROID_VR",
                    "clientVersion": "1.65.10",
                    "deviceMake": "Oculus",
                    "deviceModel": "Quest 3",
                    "androidSdkVersion": 32,
                    "userAgent": androidVrUserAgent,
                    "osName": "Android",
                    "osVersion": "12L",
                    "hl": "en",
                    "timeZone": "UTC",
                    "utcOffsetMinutes": 0
                ]
            ],
            "playbackContext": [
                "contentPlaybackContext": [
                    "html5Preference": "HTML5_PREF_WANTS"
                ]
            ]
        ]

        guard let httpBody = try? JSONSerialization.data(withJSONObject: payload) else { return nil }

        var request = URLRequest(url: playerUrl)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("28", forHTTPHeaderField: "X-Youtube-Client-Name")
        request.setValue("1.65.10", forHTTPHeaderField: "X-Youtube-Client-Version")
        request.setValue("https://www.youtube.com", forHTTPHeaderField: "Origin")
        request.setValue(androidVrUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue(visitorId, forHTTPHeaderField: "X-Goog-Visitor-Id")
        request.httpBody = httpBody
        request.timeoutInterval = 8.0

        do {
            let (data, response) = try await HttpClientProvider.shared.session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else { return nil }
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }

            if let playability = json["playabilityStatus"] as? [String: Any],
               let status = playability["status"] as? String,
               status == "LOGIN_REQUIRED" {
                return nil
            }

            guard let streamingData = json["streamingData"] as? [String: Any] else { return nil }

            var candidates: [[String: Any]] = []
            if let adaptive = streamingData["adaptiveFormats"] as? [[String: Any]] {
                for fmt in adaptive {
                    if let mime = fmt["mimeType"] as? String, mime.contains("audio") {
                        candidates.append(fmt)
                    }
                }
            }
            if candidates.isEmpty, let formats = streamingData["formats"] as? [[String: Any]] {
                candidates.append(contentsOf: formats)
            }

            let chosen = candidates.first(where: { ($0["itag"] as? Int) == 140 && ($0["url"] as? String)?.isEmpty == false })
                ?? candidates.first(where: { ($0["itag"] as? Int) == 251 && ($0["url"] as? String)?.isEmpty == false })
                ?? candidates.first(where: { ($0["url"] as? String)?.isEmpty == false })

            guard let chosenFormat = chosen,
                  let streamUrlStr = chosenFormat["url"] as? String,
                  !streamUrlStr.isEmpty else {
                return nil
            }

            let bitrate = chosenFormat["bitrate"] as? Int64 ?? 128000
            let qualityLabel = chosenFormat["qualityLabel"] as? String ?? "audio"
            let headers = [
                "User-Agent": androidVrUserAgent,
                "Origin": "https://www.youtube.com",
                "Referer": "https://www.youtube.com/"
            ]

            let probePassed = await probeStreamUrl(url: streamUrlStr, headers: headers)
            if probePassed {
                return ExtractedStream(
                    url: streamUrlStr,
                    audioUrl: streamUrlStr,
                    headers: headers,
                    isMuxed: false,
                    qualityLabel: qualityLabel,
                    bitrate: bitrate,
                    clientName: "ANDROID_VR"
                )
            }
        } catch {
            return nil
        }

        return nil
    }

    // MARK: - ANDROID_VR InnerTube Video Extraction
    private func attemptAndroidVrVideo(videoId: String, quality: String, forceRefreshVisitor: Bool) async -> ExtractedStream? {
        guard let visitorId = await getVisitorId(forceRefresh: forceRefreshVisitor) else { return nil }
        guard let playerUrl = URL(string: "https://www.youtube.com/youtubei/v1/player?prettyPrint=false") else { return nil }

        let payload: [String: Any] = [
            "videoId": videoId,
            "contentCheckOk": true,
            "racyCheckOk": true,
            "context": [
                "client": [
                    "clientName": "ANDROID_VR",
                    "clientVersion": "1.65.10",
                    "deviceMake": "Oculus",
                    "deviceModel": "Quest 3",
                    "androidSdkVersion": 32,
                    "userAgent": androidVrUserAgent,
                    "osName": "Android",
                    "osVersion": "12L",
                    "hl": "en",
                    "timeZone": "UTC",
                    "utcOffsetMinutes": 0
                ]
            ],
            "playbackContext": [
                "contentPlaybackContext": [
                    "html5Preference": "HTML5_PREF_WANTS"
                ]
            ]
        ]

        guard let httpBody = try? JSONSerialization.data(withJSONObject: payload) else { return nil }

        var request = URLRequest(url: playerUrl)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("28", forHTTPHeaderField: "X-Youtube-Client-Name")
        request.setValue("1.65.10", forHTTPHeaderField: "X-Youtube-Client-Version")
        request.setValue("https://www.youtube.com", forHTTPHeaderField: "Origin")
        request.setValue(androidVrUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue(visitorId, forHTTPHeaderField: "X-Goog-Visitor-Id")
        request.httpBody = httpBody
        request.timeoutInterval = 8.0

        do {
            let (data, response) = try await HttpClientProvider.shared.session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else { return nil }
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let streamingData = json["streamingData"] as? [String: Any] else { return nil }

            let headers = [
                "User-Agent": androidVrUserAgent,
                "Origin": "https://www.youtube.com",
                "Referer": "https://www.youtube.com/"
            ]

            // 1. Check for muxed video format (e.g. itag 18 - 360p MP4)
            if let formats = streamingData["formats"] as? [[String: Any]] {
                if let muxed = formats.first(where: { ($0["url"] as? String)?.isEmpty == false && (($0["mimeType"] as? String)?.contains("mp4") == true) }),
                   let mUrl = muxed["url"] as? String {
                    if await probeStreamUrl(url: mUrl, headers: headers) {
                        return ExtractedStream(
                            url: mUrl,
                            audioUrl: mUrl,
                            headers: headers,
                            isMuxed: true,
                            qualityLabel: muxed["qualityLabel"] as? String ?? "360p",
                            bitrate: muxed["bitrate"] as? Int64 ?? 500000,
                            clientName: "ANDROID_VR_MUXED"
                        )
                    }
                }
            }

            // 2. Adaptive video format
            var videoCandidates: [[String: Any]] = []
            var audioCandidateUrl: String?

            if let adaptive = streamingData["adaptiveFormats"] as? [[String: Any]] {
                for fmt in adaptive {
                    guard let mime = fmt["mimeType"] as? String, let u = fmt["url"] as? String, !u.isEmpty else { continue }
                    if mime.contains("video/mp4") {
                        videoCandidates.append(fmt)
                    } else if mime.contains("audio") && (fmt["itag"] as? Int) == 140 {
                        audioCandidateUrl = u
                    }
                }
            }

            if let chosenVideo = videoCandidates.first(where: { ($0["qualityLabel"] as? String)?.contains(quality) == true }) ?? videoCandidates.first,
               let vUrl = chosenVideo["url"] as? String {
                if await probeStreamUrl(url: vUrl, headers: headers) {
                    return ExtractedStream(
                        url: vUrl,
                        audioUrl: audioCandidateUrl ?? vUrl,
                        headers: headers,
                        isMuxed: false,
                        qualityLabel: chosenVideo["qualityLabel"] as? String ?? quality,
                        bitrate: chosenVideo["bitrate"] as? Int64 ?? 1500000,
                        clientName: "ANDROID_VR_ADAPTIVE"
                    )
                }
            }
        } catch {
            return nil
        }

        return nil
    }

    // MARK: - Stream URL 2-Byte Probe
    private func probeStreamUrl(url: String, headers: [String: String]) async -> Bool {
        guard let reqUrl = URL(string: url) else { return false }
        var request = URLRequest(url: reqUrl)
        request.httpMethod = "GET"
        request.setValue("bytes=0-1", forHTTPHeaderField: "Range")
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
        request.timeoutInterval = 4.0

        do {
            let (_, response) = try await HttpClientProvider.shared.session.data(for: request)
            if let http = response as? HTTPURLResponse {
                return http.statusCode == 200 || http.statusCode == 206
            }
        } catch {
            return false
        }
        return false
    }

    // MARK: - Invidious Public Mirror Fallback
    private func attemptInvidiousFallback(videoId: String) async -> ExtractedStream? {
        let instances = [
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://invidious.f5.si",
            "https://yt.chocolatemoo53.com"
        ]

        for inst in instances {
            guard let url = URL(string: "\(inst)/api/v1/videos/\(videoId)") else { continue }
            var req = URLRequest(url: url)
            req.timeoutInterval = 4.0
            req.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")

            do {
                let (data, response) = try await HttpClientProvider.shared.session.data(for: req)
                guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { continue }
                guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let adaptive = json["adaptiveFormats"] as? [[String: Any]] else { continue }

                for fmt in adaptive {
                    if let type = fmt["type"] as? String, type.contains("audio"),
                       let streamUrl = fmt["url"] as? String, !streamUrl.isEmpty {
                        return ExtractedStream(
                            url: streamUrl,
                            audioUrl: streamUrl,
                            headers: ["User-Agent": "Mozilla/5.0"],
                            isMuxed: false,
                            qualityLabel: "audio",
                            bitrate: fmt["bitrate"] as? Int64 ?? 128000,
                            clientName: "INVIDIOUS_FALLBACK"
                        )
                    }
                }
            } catch {
                continue
            }
        }
        return nil
    }
}

