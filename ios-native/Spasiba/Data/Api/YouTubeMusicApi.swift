import Foundation

public final class YouTubeMusicApi {
    public static let shared = YouTubeMusicApi()

    public static let defaultCategories: [CategoryChip] = [
        CategoryChip(id: "punjabi", label: "Punjabi", params: "latest punjabi songs official"),
        CategoryChip(id: "hindi", label: "Hindi Hits", params: "latest hindi songs official"),
        CategoryChip(id: "bollywood", label: "Bollywood", params: "latest bollywood songs official"),
        CategoryChip(id: "english", label: "English Pop", params: "latest english pop songs official"),
        CategoryChip(id: "south", label: "South Hits", params: "latest tamil telugu hits official"),
        CategoryChip(id: "haryanvi", label: "Haryanvi", params: "latest haryanvi songs official"),
        CategoryChip(id: "bhojpuri", label: "Bhojpuri", params: "latest bhojpuri songs official"),
        CategoryChip(id: "gujarati", label: "Gujarati", params: "latest gujarati songs official"),
        CategoryChip(id: "bengali", label: "Bengali", params: "latest bengali songs official"),
        CategoryChip(id: "marathi", label: "Marathi", params: "latest marathi songs official"),
        CategoryChip(id: "malayalam", label: "Malayalam", params: "latest malayalam songs official"),
        CategoryChip(id: "kannada", label: "Kannada", params: "latest kannada songs official"),
        CategoryChip(id: "latin", label: "Latin & Reggaeton", params: "latest latin reggaeton hits official"),
        CategoryChip(id: "kpop", label: "K-Pop", params: "latest kpop songs official"),
        CategoryChip(id: "relax", label: "Chill & Relax", params: "chill acoustic songs"),
        CategoryChip(id: "energize", label: "Energize", params: "top energize songs"),
        CategoryChip(id: "workout", label: "Workout", params: "top workout gym songs"),
        CategoryChip(id: "focus", label: "Focus & Study", params: "lofi study beats"),
        CategoryChip(id: "party", label: "Party Hits", params: "latest party dance hits official"),
        CategoryChip(id: "romance", label: "Romance", params: "latest romantic love songs official"),
        CategoryChip(id: "hiphop", label: "Hip-Hop & Rap", params: "latest hip hop rap songs official"),
        CategoryChip(id: "rock", label: "Rock Classics", params: "classic rock songs")
    ]

    public var dynamicCategoryChips: [CategoryChip] = defaultCategories
    public var cachedHomeShelves: [HomeShelf] = []
    public var cachedTopCharts: [Song] = []
    public var cachedPunjabiSongs: [Song] = []
    public var cachedHindiSongs: [Song] = []
    public var cachedEnglishSongs: [Song] = []
    public var cachedSouthSongs: [Song] = []
    public var cachedHaryanviSongs: [Song] = []
    public var cachedBhojpuriSongs: [Song] = []

    private var searchCache: [String: [Song]] = [:]
    private var artistAvatarCache: [String: String] = [:]
    private var visitorData: String? = nil

    private init() {}

    public func fetchCategoryChips() async -> [CategoryChip] {
        if !dynamicCategoryChips.isEmpty && dynamicCategoryChips != YouTubeMusicApi.defaultCategories {
            return dynamicCategoryChips
        }
        _ = await fetchYouTubeMusicBrowseShelves(browseId: "FEmusic_home")
        return dynamicCategoryChips.isEmpty ? YouTubeMusicApi.defaultCategories : dynamicCategoryChips
    }

    public func fetchYouTubeMusicHomeShelves(forceRefresh: Bool = false) async -> [HomeShelf] {
        return await fetchYouTubeMusicBrowseShelves(browseId: "FEmusic_home", forceRefresh: forceRefresh)
    }

    public func fetchYouTubeMusicBrowseShelves(browseId: String = "FEmusic_home", params: String? = nil, forceRefresh: Bool = false) async -> [HomeShelf] {
        if browseId == "FEmusic_home" && (params == nil || params?.isEmpty == true) && !forceRefresh && !cachedHomeShelves.isEmpty {
            return cachedHomeShelves
        }
        guard let url = URL(string: "https://music.youtube.com/youtubei/v1/browse") else { return [] }

        var payload: [String: Any] = [
            "browseId": browseId,
            "context": [
                "client": [
                    "clientName": "WEB_REMIX",
                    "clientVersion": "1.20260701.01.00",
                    "hl": "en",
                    "gl": "IN"
                ]
            ]
        ]
        if let params = params, !params.isEmpty {
            payload["params"] = params
        }
        if let visitor = visitorData, var ctx = payload["context"] as? [String: Any], var client = ctx["client"] as? [String: Any] {
            client["visitorData"] = visitor
            ctx["client"] = client
            payload["context"] = ctx
        }

        guard let httpBody = try? JSONSerialization.data(withJSONObject: payload) else { return [] }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.setValue("https://music.youtube.com", forHTTPHeaderField: "Origin")
        request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36", forHTTPHeaderField: "User-Agent")
        request.httpBody = httpBody
        request.timeoutInterval = 10.0

        do {
            let (data, response) = try await HttpClientProvider.shared.session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                return await fallbackShelves(for: params)
            }

            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                return await fallbackShelves(for: params)
            }

            if let respCtx = json["responseContext"] as? [String: Any], let vData = respCtx["visitorData"] as? String {
                self.visitorData = vData
            }

            let shelves = parseBrowseContents(json)
            if !shelves.isEmpty {
                if browseId == "FEmusic_home" && (params == nil || params?.isEmpty == true) {
                    self.cachedHomeShelves = shelves
                }
                return shelves
            }
            return await fallbackShelves(for: params)
        } catch {
            return await fallbackShelves(for: params)
        }
    }

    public func fetchPodcastTopicShelves() async -> [HomeShelf] {
        async let p1 = searchSongs(query: "top podcasts 2026 episodes")
        async let p2 = searchSongs(query: "the ranveer show beerbiceps official podcast")
        async let p3 = searchSongs(query: "huberman lab podcast episodes")

        let (r1, r2, r3) = await (p1, p2, p3)
        var shelves: [HomeShelf] = []
        if !r1.isEmpty {
            shelves.append(HomeShelf(title: "Featured Shows", subtitle: "Top trending podcast episodes", style: .videoWidescreen, songs: r1))
        }
        if !r2.isEmpty {
            shelves.append(HomeShelf(title: "Conversations & Stories", subtitle: "Deep talk & perspectives", style: .carouselSquare, songs: r2))
        }
        if !r3.isEmpty {
            shelves.append(HomeShelf(title: "Science & Knowledge", subtitle: "Learn from experts", style: .carouselSquare, songs: r3))
        }
        return shelves
    }

    public func getArtistAvatarUrl(artist: String) async -> String {
        let key = artist.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if key.isEmpty { return "" }
        if let cached = artistAvatarCache[key] { return cached }

        let songs = await searchSongs(query: "\(artist) official artist")
        if let first = songs.first {
            artistAvatarCache[key] = first.thumbnail
            return first.thumbnail
        }
        return ""
    }

    private func fallbackShelves(for params: String?) async -> [HomeShelf] {
        if let params = params, !params.isEmpty {
            let songs = await searchSongs(query: "latest songs \(params)")
            return [HomeShelf(title: "Featured Selections", subtitle: "Curated tracks", style: .carouselSquare, songs: songs)]
        }
        return await fetchHomeShelves()
    }

    public func fetchHomeShelves() async -> [HomeShelf] {
        if !cachedHomeShelves.isEmpty {
            return cachedHomeShelves
        }

        async let punjabi = searchSongs(query: "latest punjabi songs 2026")
        async let hindi = searchSongs(query: "latest hindi bollywood hits 2026")
        async let english = searchSongs(query: "global top billboard pop hits 2026")
        async let trending = searchSongs(query: "trending songs world 2026")

        let (pSongs, hSongs, eSongs, tSongs) = await (punjabi, hindi, english, trending)

        var shelves: [HomeShelf] = []
        if !tSongs.isEmpty {
            shelves.append(HomeShelf(title: "Top Trending Worldwide", subtitle: "Most played tracks right now", style: .heroBanner, songs: tSongs))
        }
        if !pSongs.isEmpty {
            shelves.append(HomeShelf(title: "Quick picks", subtitle: "Start radio", style: .quickPicksGrid, songs: pSongs))
        }
        if !hSongs.isEmpty {
            shelves.append(HomeShelf(title: "Bollywood & Hindi Hits", subtitle: "Feel-good melodies & anthems", style: .carouselSquare, songs: hSongs))
        }
        if !eSongs.isEmpty {
            shelves.append(HomeShelf(title: "Music Videos", subtitle: "Watch & listen", style: .videoWidescreen, songs: eSongs))
        }

        cachedHomeShelves = shelves
        return shelves
    }

    public func fetchYouTubeMusicSearch(query: String, params: String? = nil) async -> [Song] {
        let cleanQuery = query.replacingOccurrences(of: "%20", with: " ").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanQuery.isEmpty else { return [] }

        guard let url = URL(string: "https://music.youtube.com/youtubei/v1/search") else { return [] }

        var payload: [String: Any] = [
            "query": cleanQuery,
            "context": [
                "client": [
                    "clientName": "WEB_REMIX",
                    "clientVersion": "1.20260701.01.00",
                    "hl": "en",
                    "gl": "IN"
                ]
            ]
        ]
        if let params = params, !params.isEmpty {
            payload["params"] = params
        }
        if let visitor = visitorData, var ctx = payload["context"] as? [String: Any], var client = ctx["client"] as? [String: Any] {
            client["visitorData"] = visitor
            ctx["client"] = client
            payload["context"] = ctx
        }

        guard let httpBody = try? JSONSerialization.data(withJSONObject: payload) else { return [] }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.setValue("https://music.youtube.com", forHTTPHeaderField: "Origin")
        request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36", forHTTPHeaderField: "User-Agent")
        request.httpBody = httpBody
        request.timeoutInterval = 8.0

        do {
            let (data, response) = try await HttpClientProvider.shared.session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else { return [] }

            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [] }
            if let respCtx = json["responseContext"] as? [String: Any], let vData = respCtx["visitorData"] as? String {
                self.visitorData = vData
            }
            return parseSearchContents(json)
        } catch {
            return []
        }
    }

    public func fetchHtmlYoutubeSearch(query: String) async -> [Song] {
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanQuery.isEmpty else { return [] }

        guard let encoded = cleanQuery.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://www.youtube.com/results?search_query=\(encoded)") else { return [] }

        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 8.0

        do {
            let (data, response) = try await HttpClientProvider.shared.session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200,
                  let html = String(data: data, encoding: .utf8) else { return [] }

            var songs: [Song] = []
            let initialMarker = "var ytInitialData = "
            if let startRange = html.range(of: initialMarker) {
                let jsonStart = html[startRange.upperBound...]
                if let endRange = jsonStart.range(of: ";</script>") {
                    let jsonString = String(jsonStart[..<endRange.lowerBound])
                    if let jsonData = jsonString.data(using: .utf8),
                       let rootObj = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any],
                       let contents = rootObj["contents"] as? [String: Any],
                       let twoCol = contents["twoColumnSearchResultsRenderer"] as? [String: Any],
                       let primary = twoCol["primaryContents"] as? [String: Any],
                       let sectionList = primary["sectionListRenderer"] as? [String: Any],
                       let secContents = sectionList["contents"] as? [[String: Any]] {
                        for sec in secContents {
                            guard let itemSection = sec["itemSectionRenderer"] as? [String: Any],
                                  let items = itemSection["contents"] as? [[String: Any]] else { continue }
                            for item in items {
                                guard let videoRenderer = item["videoRenderer"] as? [String: Any],
                                      let videoId = videoRenderer["videoId"] as? String,
                                      videoId.count == 11,
                                      !songs.contains(where: { $0.id == videoId }) else { continue }

                                let titleRuns = (videoRenderer["title"] as? [String: Any])?["runs"] as? [[String: Any]]
                                guard let title = titleRuns?.first?["text"] as? String,
                                      !title.isEmpty,
                                      title.range(of: #"^\d+:\d+(:\d+)?$"#, options: .regularExpression) == nil,
                                      !title.hasSuffix("views") else { continue }

                                let ownerRuns = ((videoRenderer["ownerText"] as? [String: Any])?["runs"] as? [[String: Any]]) ??
                                    ((videoRenderer["longBylineText"] as? [String: Any])?["runs"] as? [[String: Any]])
                                let author = ownerRuns?.first?["text"] as? String ?? "Artist"
                                let thumb = "https://i.ytimg.com/vi/\(videoId)/hqdefault.jpg"

                                songs.append(Song(id: videoId, title: title, author: author, thumbnail: thumb, durationSeconds: 210))
                                if songs.count >= 15 { break }
                            }
                            if songs.count >= 15 { break }
                        }
                    }
                }
            }

            // Fallback regex if initial data didn't extract songs
            if songs.isEmpty {
                let pattern = #""videoId":"([a-zA-Z0-9_-]{11})".*?"title":\{"runs":\[\{"text":"([^"]+)"\}\]\}.*?"ownerText":\{"runs":\[\{"text":"([^"]+)"\}\]\}"#
                if let regex = try? NSRegularExpression(pattern: pattern, options: []) {
                    let nsString = html as NSString
                    let matches = regex.matches(in: html, options: [], range: NSRange(location: 0, length: nsString.length))
                    for match in matches {
                        if songs.count >= 15 { break }
                        guard match.numberOfRanges >= 4 else { continue }
                        let vId = nsString.substring(with: match.range(at: 1))
                        let t = nsString.substring(with: match.range(at: 2))
                        let a = nsString.substring(with: match.range(at: 3))

                        if vId.count == 11 && !songs.contains(where: { $0.id == vId }) &&
                           !t.isEmpty && t.range(of: #"^\d+:\d+(:\d+)?$"#, options: .regularExpression) == nil &&
                           !t.hasSuffix("views") && !t.contains("ago") {
                            songs.append(Song(id: vId, title: t, author: a.isEmpty ? "Artist" : a, thumbnail: "https://i.ytimg.com/vi/\(vId)/hqdefault.jpg", durationSeconds: 210))
                        }
                    }
                }
            }

            return songs
        } catch {
            return []
        }
    }

    public func searchSongs(query: String) async -> [Song] {
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanQuery.isEmpty else { return [] }

        let cacheKey = cleanQuery.lowercased()
        if let cached = searchCache[cacheKey], !cached.isEmpty {
            return cached
        }

        // Tier 0: Direct YouTube Music InnerTube Search (Filtered by Songs: "egWKAQI=")
        let ytMusicSongs = await fetchYouTubeMusicSearch(query: cleanQuery, params: "egWKAQI=")
        if ytMusicSongs.count >= 3 {
            let ytMusicGeneral = await fetchYouTubeMusicSearch(query: cleanQuery, params: nil)
            let topCard = ytMusicGeneral.first(where: { generalSong in
                !ytMusicSongs.contains(where: { $0.id == generalSong.id })
            })
            var finalSongs = ytMusicSongs
            if let topCard = topCard {
                finalSongs.insert(topCard, at: 0)
            }
            var seen = Set<String>()
            let result = finalSongs.filter { seen.insert($0.id).inserted }
            searchCache[cacheKey] = result
            return result
        }

        // Tier 1: YouTube Music General Search (Unfiltered fallback)
        let ytMusicGeneral = await fetchYouTubeMusicSearch(query: cleanQuery, params: nil)
        if !ytMusicGeneral.isEmpty {
            let combined = (ytMusicGeneral + ytMusicSongs)
            var seen = Set<String>()
            let result = combined.filter { seen.insert($0.id).inserted }
            if result.count >= 3 {
                searchCache[cacheKey] = result
                return result
            }
        }

        // Tier 2: HTML Scrape Fallback
        let htmlResults = await fetchHtmlYoutubeSearch(query: cleanQuery)
        if !htmlResults.isEmpty {
            let combined = (ytMusicSongs + htmlResults)
            var seen = Set<String>()
            let result = combined.filter { seen.insert($0.id).inserted }
            searchCache[cacheKey] = result
            return result
        }

        let fallback = ytMusicSongs.isEmpty ? ytMusicGeneral : ytMusicSongs
        if !fallback.isEmpty {
            searchCache[cacheKey] = fallback
        }
        return fallback
    }

    private func parseBrowseContents(_ json: [String: Any]) -> [HomeShelf] {
        var shelves: [HomeShelf] = []

        guard let contents = json["contents"] as? [String: Any],
              let singleCol = contents["singleColumnBrowseResultsRenderer"] as? [String: Any],
              let tabs = singleCol["tabs"] as? [[String: Any]] else {
            return []
        }

        for tab in tabs {
            guard let tabRenderer = tab["tabRenderer"] as? [String: Any],
                  let tabContent = tabRenderer["content"] as? [String: Any],
                  let sectionList = tabContent["sectionListRenderer"] as? [String: Any] else { continue }

            // Extract dynamic chips if present
            if let header = sectionList["header"] as? [String: Any],
               let chipCloud = header["chipCloudRenderer"] as? [String: Any],
               let chips = chipCloud["chips"] as? [[String: Any]] {
                var extractedChips: [CategoryChip] = []
                for chip in chips {
                    guard let chipData = chip["chipCloudChipRenderer"] as? [String: Any],
                          let textObj = chipData["text"] as? [String: Any],
                          let runs = textObj["runs"] as? [[String: Any]],
                          let label = runs.first?["text"] as? String else { continue }
                    let nav = chipData["navigationEndpoint"] as? [String: Any]
                    let browseEnd = nav?["browseEndpoint"] as? [String: Any]
                    let p = browseEnd?["params"] as? String ?? ""
                    extractedChips.append(CategoryChip(id: label.lowercased(), label: label, params: p))
                }
                if !extractedChips.isEmpty {
                    self.dynamicCategoryChips = extractedChips
                }
            }

            guard let sectionContents = sectionList["contents"] as? [[String: Any]] else { continue }

            for section in sectionContents {
                if let musicCarousel = section["musicCarouselShelfRenderer"] as? [String: Any] {
                    let headerTitle = extractHeaderTitle(musicCarousel)
                    let contentsList = musicCarousel["contents"] as? [[String: Any]] ?? []
                    var songs: [Song] = []
                    for item in contentsList {
                        if let song = parseTwoRowItem(item) ?? parseSongItem(item) {
                            songs.append(song)
                        }
                    }
                    if !songs.isEmpty {
                        let isVideo = headerTitle.lowercased().contains("video")
                        shelves.append(
                            HomeShelf(
                                title: headerTitle.isEmpty ? "Featured" : headerTitle,
                                subtitle: nil,
                                style: isVideo ? .videoWidescreen : .carouselSquare,
                                songs: songs
                            )
                        )
                    }
                }
            }
        }

        return shelves
    }

    private func extractHeaderTitle(_ container: [String: Any]) -> String {
        if let header = container["header"] as? [String: Any],
           let basicHeader = header["musicCarouselShelfBasicHeaderRenderer"] as? [String: Any],
           let titleObj = basicHeader["title"] as? [String: Any],
           let runs = titleObj["runs"] as? [[String: Any]],
           let title = runs.first?["text"] as? String {
            return title
        }
        return ""
    }

    private func parseTwoRowItem(_ item: [String: Any]) -> Song? {
        guard let itemRenderer = item["musicTwoRowItemRenderer"] as? [String: Any] else { return nil }

        var videoId = ""
        if let nav = itemRenderer["navigationEndpoint"] as? [String: Any] {
            if let watchEnd = nav["watchEndpoint"] as? [String: Any],
               let vId = watchEnd["videoId"] as? String {
                videoId = vId
            }
        }
        if videoId.isEmpty, let overlay = itemRenderer["thumbnailOverlay"] as? [String: Any],
           let playBtn = overlay["musicItemThumbnailOverlayRenderer"] as? [String: Any],
           let playNav = (playBtn["content"] as? [String: Any])?["musicPlayButtonRenderer"] as? [String: Any],
           let endPoint = playNav["playNavigationEndpoint"] as? [String: Any],
           let watchEnd = endPoint["watchEndpoint"] as? [String: Any],
           let vId = watchEnd["videoId"] as? String {
            videoId = vId
        }
        if videoId.count != 11 {
            let thumbUrl = extractThumbnail(itemRenderer)
            if thumbUrl.contains("/vi/") {
                let parts = thumbUrl.components(separatedBy: "/vi/")
                if parts.count > 1 {
                    let cand = String(parts[1].prefix(11))
                    if cand.count == 11 && !cand.contains("/") && !cand.contains("?") {
                        videoId = cand
                    }
                }
            }
        }

        let titleObj = itemRenderer["title"] as? [String: Any]
        let titleRuns = titleObj?["runs"] as? [[String: Any]]
        let titleText = titleRuns?.first?["text"] as? String ?? "Unknown Title"

        let subObj = itemRenderer["subtitle"] as? [String: Any]
        let subRuns = subObj?["runs"] as? [[String: Any]]
        let authorText = subRuns?.compactMap { $0["text"] as? String }.joined() ?? "Artist"

        var thumb = extractThumbnail(itemRenderer)
        let finalId = videoId.count == 11 ? videoId : "yt_\(abs(titleText.hashValue))"
        if thumb.isEmpty {
            if videoId.count == 11 {
                thumb = "https://i.ytimg.com/vi/\(videoId)/hqdefault.jpg"
            }
        }

        return Song(
            id: finalId,
            title: titleText,
            author: authorText,
            thumbnail: thumb,
            durationSeconds: 215
        )
    }

    private func parseSearchContents(_ json: [String: Any]) -> [Song] {
        var songs: [Song] = []

        guard let contents = json["contents"] as? [String: Any],
              let tabbedResults = contents["tabbedSearchResultsRenderer"] as? [String: Any],
              let tabs = tabbedResults["tabs"] as? [[String: Any]],
              let firstTab = tabs.first,
              let tabRenderer = firstTab["tabRenderer"] as? [String: Any],
              let tabContent = tabRenderer["content"] as? [String: Any],
              let sectionList = tabContent["sectionListRenderer"] as? [String: Any],
              let sectionContents = sectionList["contents"] as? [[String: Any]] else {
            return []
        }

        for section in sectionContents {
            // 1. YouTube Music Official Top Result Card (musicCardShelfRenderer)
            if let cardShelf = section["musicCardShelfRenderer"] as? [String: Any] {
                let cardTitleRuns = (cardShelf["title"] as? [String: Any])?["runs"] as? [[String: Any]]
                let cardTitle = cardTitleRuns?.first?["text"] as? String ?? ""

                var cardVideoId = ""
                let onTap = cardShelf["onTap"] as? [String: Any]
                let buttons = cardShelf["buttons"] as? [[String: Any]]
                let firstBtn = buttons?.first?["buttonRenderer"] as? [String: Any]
                let cmd = firstBtn?["command"] as? [String: Any]
                let watchEndpoint = (onTap?["watchEndpoint"] as? [String: Any]) ?? (cmd?["watchEndpoint"] as? [String: Any])
                if let vId = watchEndpoint?["videoId"] as? String, vId.count == 11 {
                    cardVideoId = vId
                }

                let cardSubtitleRuns = (cardShelf["subtitle"] as? [String: Any])?["runs"] as? [[String: Any]]
                let cardAuthor = cardSubtitleRuns?.compactMap { $0["text"] as? String }.joined() ?? "Artist"
                var cardThumb = extractThumbnail(cardShelf)
                if cardThumb.isEmpty && cardVideoId.count == 11 {
                    cardThumb = "https://i.ytimg.com/vi/\(cardVideoId)/hqdefault.jpg"
                }

                if cardVideoId.count == 11 && !cardTitle.trimmingCharacters(in: .whitespaces).isEmpty && !songs.contains(where: { $0.id == cardVideoId }) {
                    songs.append(Song(id: cardVideoId, title: cardTitle, author: cardAuthor, thumbnail: cardThumb, durationSeconds: 210))
                }
            }

            // 2. YouTube Music Categorized Shelves (musicShelfRenderer)
            if let musicShelf = section["musicShelfRenderer"] as? [String: Any],
               let items = musicShelf["contents"] as? [[String: Any]] {
                for item in items {
                    if let song = parseSongItem(item) {
                        if !songs.contains(where: { $0.id == song.id }) {
                            songs.append(song)
                        }
                    }
                }
            }
        }

        return songs
    }

    private func parseSongItem(_ item: [String: Any]) -> Song? {
        guard let itemRenderer = item["musicResponsiveListItemRenderer"] as? [String: Any] else { return nil }

        guard let flexColumns = itemRenderer["flexColumns"] as? [[String: Any]], !flexColumns.isEmpty else { return nil }

        let titleColumn = flexColumns[0]
        guard let titleFlex = titleColumn["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any],
              let titleTextObj = titleFlex["text"] as? [String: Any],
              let titleRuns = titleTextObj["runs"] as? [[String: Any]],
              let title = titleRuns.first?["text"] as? String,
              !title.trimmingCharacters(in: .whitespaces).isEmpty else {
            return nil
        }

        var videoId = ""

        // A. Check playlistItemData
        if let playlistItemData = itemRenderer["playlistItemData"] as? [String: Any],
           let vId = playlistItemData["videoId"] as? String, vId.count == 11 {
            videoId = vId
        }

        // B. Check overlay playNavigationEndpoint
        if videoId.count != 11 {
            if let overlay = itemRenderer["overlay"] as? [String: Any],
               let thumbOverlay = overlay["musicItemThumbnailOverlayRenderer"] as? [String: Any],
               let content = thumbOverlay["content"] as? [String: Any],
               let playBtn = content["musicPlayButtonRenderer"] as? [String: Any],
               let playNav = playBtn["playNavigationEndpoint"] as? [String: Any],
               let watchEnd = playNav["watchEndpoint"] as? [String: Any],
               let vId = watchEnd["videoId"] as? String, vId.count == 11 {
                videoId = vId
            }
        }

        // C. Check navigationEndpoint or hover overlay
        if videoId.count != 11 {
            let nav = (itemRenderer["navigationEndpoint"] as? [String: Any]) ??
                ((itemRenderer["overlay"] as? [String: Any])?["musicItemHoverOverlayRenderer"] as? [String: Any])?["content"] as? [String: Any]
            if let navEndpoint = nav?["watchEndpoint"] as? [String: Any],
               let vId = navEndpoint["videoId"] as? String, vId.count == 11 {
                videoId = vId
            }
        }

        // D. Check title runs navigationEndpoint
        if videoId.count != 11 {
            for run in titleRuns {
                if let runNav = run["navigationEndpoint"] as? [String: Any],
                   let watchEnd = runNav["watchEndpoint"] as? [String: Any],
                   let vId = watchEnd["videoId"] as? String, vId.count == 11 {
                    videoId = vId
                    break
                }
            }
        }

        // E. Check thumbnail URL fallback for /vi/{id}/
        if videoId.count != 11 {
            let thumbUrl = extractThumbnail(itemRenderer)
            if thumbUrl.contains("/vi/") {
                let parts = thumbUrl.components(separatedBy: "/vi/")
                if parts.count > 1 {
                    let cand = String(parts[1].prefix(11))
                    if cand.count == 11 && !cand.contains("/") && !cand.contains("?") {
                        videoId = cand
                    }
                }
            }
        }

        guard videoId.count == 11 else { return nil }

        var author = "Artist"
        var parsedViews: Int64 = 0
        if flexColumns.count > 1 {
            let authorColumn = flexColumns[1]
            if let authorFlex = authorColumn["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any],
               let authorTextObj = authorFlex["text"] as? [String: Any],
               let authorRuns = authorTextObj["runs"] as? [[String: Any]] {
                for run in authorRuns {
                    guard let t = (run["text"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines), !t.isEmpty else { continue }
                    let tLower = t.lowercased()
                    if tLower.contains("views") || tLower.contains("plays") {
                        parsedViews = parseViewCount(t)
                    } else if t != "•" && t != "Song" && t != "Video" && t != "Explicit" && t.range(of: #"^\d+:\d+(:\d+)?$"#, options: .regularExpression) == nil {
                        if author == "Artist" {
                            author = t
                        }
                    }
                }
            }
        }

        var thumbnail = extractThumbnail(itemRenderer)
        if thumbnail.isEmpty {
            thumbnail = "https://i.ytimg.com/vi/\(videoId)/hqdefault.jpg"
        }

        return Song(
            id: videoId,
            title: title,
            author: author,
            thumbnail: thumbnail,
            durationSeconds: 210,
            views: parsedViews
        )
    }

    private func parseViewCount(_ text: String) -> Int64 {
        let cleaned = text.replacingOccurrences(of: "[^0-9.KkMmBb]", with: "", options: .regularExpression)
        if cleaned.hasSuffix("K") || cleaned.hasSuffix("k") {
            let num = Double(cleaned.dropLast()) ?? 0
            return Int64(num * 1_000)
        } else if cleaned.hasSuffix("M") || cleaned.hasSuffix("m") {
            let num = Double(cleaned.dropLast()) ?? 0
            return Int64(num * 1_000_000)
        } else if cleaned.hasSuffix("B") || cleaned.hasSuffix("b") {
            let num = Double(cleaned.dropLast()) ?? 0
            return Int64(num * 1_000_000_000)
        }
        return Int64(cleaned) ?? 0
    }

    private func extractThumbnail(_ container: [String: Any]) -> String {
        // 1. Check thumbnailRenderer -> musicThumbnailRenderer -> thumbnail -> thumbnails
        if let thumbRenderer = (container["thumbnailRenderer"] as? [String: Any])?["musicThumbnailRenderer"] as? [String: Any],
           let innerThumb = thumbRenderer["thumbnail"] as? [String: Any],
           let thumbArray = innerThumb["thumbnails"] as? [[String: Any]],
           let last = thumbArray.last,
           let url = last["url"] as? String, !url.isEmpty {
            return cleanThumbnailUrl(url)
        }
        // 2. Check thumbnail -> musicThumbnailRenderer -> thumbnail -> thumbnails
        if let thumbRenderer = (container["thumbnail"] as? [String: Any])?["musicThumbnailRenderer"] as? [String: Any],
           let innerThumb = thumbRenderer["thumbnail"] as? [String: Any],
           let thumbArray = innerThumb["thumbnails"] as? [[String: Any]],
           let last = thumbArray.last,
           let url = last["url"] as? String, !url.isEmpty {
            return cleanThumbnailUrl(url)
        }
        // 3. Check thumbnail -> thumbnails directly
        if let thumbObj = container["thumbnail"] as? [String: Any],
           let thumbArray = thumbObj["thumbnails"] as? [[String: Any]],
           let last = thumbArray.last,
           let url = last["url"] as? String, !url.isEmpty {
            return cleanThumbnailUrl(url)
        }
        // 4. Check thumbnails directly on container
        if let thumbArray = container["thumbnails"] as? [[String: Any]],
           let last = thumbArray.last,
           let url = last["url"] as? String, !url.isEmpty {
            return cleanThumbnailUrl(url)
        }
        // 5. Check container["thumbnail"] as String directly
        if let url = container["thumbnail"] as? String, !url.isEmpty {
            return cleanThumbnailUrl(url)
        }
        return ""
    }

    private func cleanThumbnailUrl(_ url: String) -> String {
        var str = url.trimmingCharacters(in: .whitespacesAndNewlines)
        if str.hasPrefix("//") {
            str = "https:" + str
        } else if str.hasPrefix("http://") {
            str = "https://" + str.dropFirst(7)
        }
        return str
    }

    // MARK: - Recommendations & Fallbacks

    public static func getFallbackTracks() -> [Song] {
        return [
            Song(id: "BddP6PYo2gs", title: "Kesariya", author: "Arijit Singh, Pritam", thumbnail: "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg", durationSeconds: 268),
            Song(id: "cUM9sK4D-G8", title: "Softly", author: "Karan Aujla, Ikky", thumbnail: "https://i.ytimg.com/vi/cUM9sK4D-G8/hqdefault.jpg", durationSeconds: 154),
            Song(id: "RLzC55ai0eo", title: "Heeriye", author: "Jasleen Royal, Arijit Singh", thumbnail: "https://i.ytimg.com/vi/RLzC55ai0eo/hqdefault.jpg", durationSeconds: 194),
            Song(id: "Umqb9KENgmk", title: "Tum Hi Ho", author: "Arijit Singh, Mithoon", thumbnail: "https://i.ytimg.com/vi/Umqb9KENgmk/hqdefault.jpg", durationSeconds: 262),
            Song(id: "3tmd-ClpJxA", title: "Blinding Lights", author: "The Weeknd", thumbnail: "https://i.ytimg.com/vi/3tmd-ClpJxA/hqdefault.jpg", durationSeconds: 200),
            Song(id: "kJQP7kiw5Fk", title: "Despacito", author: "Luis Fonsi ft. Daddy Yankee", thumbnail: "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg", durationSeconds: 228)
        ]
    }

    public func getFallbackTracks() -> [Song] {
        return Self.getFallbackTracks()
    }

    public static func getHomeRecommendations() async -> [Song] {
        return await shared.getHomeRecommendations()
    }

    public func getHomeRecommendations() async -> [Song] {
        let trendingSongs = await searchSongs(query: "top music 2026")
        return !trendingSongs.isEmpty ? trendingSongs : getFallbackTracks()
    }

    // MARK: - Related & Radio Streams

    public static func fetchWebRelatedSongs(videoId: String) async -> [Song] {
        return await shared.fetchWebRelatedSongs(videoId: videoId)
    }

    public func fetchWebRelatedSongs(videoId: String) async -> [Song] {
        guard !videoId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let url = URL(string: "https://spasiba.vercel.app/api/search?relatedTo=\(videoId)&limit=20") else {
            return []
        }

        var request = URLRequest(url: url)
        request.setValue("SpasibaNative2/1.0", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 8.0

        do {
            let (data, response) = try await HttpClientProvider.shared.session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else { return [] }
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let resultsArray = json["results"] as? [[String: Any]] else {
                return []
            }

            var songs: [Song] = []
            for obj in resultsArray {
                guard let id = (obj["id"] as? String) ?? (obj["videoId"] as? String) else { continue }
                let title = (obj["title"] as? String) ?? "Track"
                let author = (obj["author"] as? String) ?? (obj["artist"] as? String) ?? "Artist"
                let thumbnail = (obj["thumbnail"] as? String) ?? (obj["artworkUrl"] as? String) ?? "https://i.ytimg.com/vi/\(id)/hqdefault.jpg"
                let duration = (obj["durationSeconds"] as? Int) ?? 210
                songs.append(Song(id: id, title: title, author: author, thumbnail: thumbnail, durationSeconds: duration))
            }
            return songs
        } catch {
            return []
        }
    }

    public static func fetchYouTubeMusicNextRadioQueue(videoId: String) async -> [Song] {
        return await shared.fetchYouTubeMusicNextRadioQueue(videoId: videoId)
    }

    public func fetchYouTubeMusicNextRadioQueue(videoId: String) async -> [Song] {
        guard !videoId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let url = URL(string: "https://www.youtube.com/youtubei/v1/next") else {
            return []
        }

        let payload: [String: Any] = [
            "videoId": videoId,
            "playlistId": "RDAMVM\(videoId)",
            "enableAutomix": true,
            "isRadio": true,
            "context": [
                "client": [
                    "clientName": "WEB_REMIX",
                    "clientVersion": "1.20240101.01.00",
                    "hl": "en",
                    "gl": "US"
                ]
            ]
        ]

        guard let httpBody = try? JSONSerialization.data(withJSONObject: payload) else { return [] }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.setValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36", forHTTPHeaderField: "User-Agent")
        request.setValue("https://music.youtube.com", forHTTPHeaderField: "Origin")
        request.httpBody = httpBody
        request.timeoutInterval = 8.0

        do {
            let (data, response) = try await HttpClientProvider.shared.session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else { return [] }
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [] }

            var songs: [Song] = []
            if let contents = json["contents"] as? [String: Any],
               let singleColumn = contents["singleColumnMusicWatchNextResultsRenderer"] as? [String: Any],
               let tabbedRenderer = singleColumn["tabbedRenderer"] as? [String: Any],
               let watchNextTabbed = tabbedRenderer["watchNextTabbedResultsRenderer"] as? [String: Any],
               let tabs = watchNextTabbed["tabs"] as? [[String: Any]],
               let firstTab = tabs.first,
               let tabRenderer = firstTab["tabRenderer"] as? [String: Any],
               let tabContent = tabRenderer["content"] as? [String: Any],
               let musicQueue = tabContent["musicQueueRenderer"] as? [String: Any],
               let queueContent = musicQueue["content"] as? [String: Any],
               let playlistPanel = queueContent["playlistPanelRenderer"] as? [String: Any],
               let panelContents = playlistPanel["contents"] as? [[String: Any]] {

                for item in panelContents {
                    guard let panelVideo = item["playlistPanelVideoRenderer"] as? [String: Any],
                          let vid = panelVideo["videoId"] as? String else { continue }

                    let titleRuns = (panelVideo["title"] as? [String: Any])?["runs"] as? [[String: Any]]
                    let title = titleRuns?.first?["text"] as? String ?? "Track"

                    let bylineRuns = (panelVideo["longBylineText"] as? [String: Any])?["runs"] as? [[String: Any]]
                    let author = bylineRuns?.first?["text"] as? String ?? "Artist"
                    let thumb = "https://i.ytimg.com/vi/\(vid)/hqdefault.jpg"

                    songs.append(Song(id: vid, title: title, author: author, thumbnail: thumb, durationSeconds: 210))
                }
            }
            return songs
        } catch {
            return []
        }
    }

    // MARK: - Stream Resolution

    public static func resolveStreamResult(videoId: String) async -> StreamingResult? {
        return await shared.resolveStreamResult(videoId: videoId)
    }

    public func resolveStreamResult(videoId: String) async -> StreamingResult? {
        guard let extracted = await SpasibaNativeExtractor.shared.resolveAudioStream(videoId: videoId) else {
            return nil
        }
        return StreamingResult(
            url: extracted.url,
            audioUrl: extracted.audioUrl,
            headers: extracted.headers
        )
    }

    public static func resolveVideoStreamResult(videoId: String, quality: String = "720p") async -> StreamingResult? {
        return await shared.resolveVideoStreamResult(videoId: videoId, quality: quality)
    }

    public func resolveVideoStreamResult(videoId: String, quality: String = "720p") async -> StreamingResult? {
        var extracted = await SpasibaNativeExtractor.shared.resolveVideoStream(videoId: videoId, quality: quality)
        if extracted == nil {
            extracted = await SpasibaNativeExtractor.shared.resolveAudioStream(videoId: videoId)
        }
        guard let finalExtracted = extracted else { return nil }
        return StreamingResult(
            url: finalExtracted.url,
            audioUrl: finalExtracted.audioUrl,
            headers: finalExtracted.headers
        )
    }

    // MARK: - Official Artist Page

    public static func fetchOfficialArtistPage(artistName: String) async -> ArtistPageData {
        return await shared.fetchOfficialArtistPage(artistName: artistName)
    }

    public func fetchOfficialArtistPage(artistName: String) async -> ArtistPageData {
        let topSongs = await searchSongs(query: "\(artistName) top tracks")
        let videos = await searchSongs(query: "\(artistName) music videos")
        let avatar = topSongs.first?.thumbnail
        return ArtistPageData(
            artistName: artistName,
            avatarUrl: avatar,
            bannerUrl: avatar,
            topSongs: topSongs,
            recentVideos: videos
        )
    }

    // MARK: - Static search convenience

    public static func searchSongs(query: String) async -> [Song] {
        return await shared.searchSongs(query: query)
    }
}

public struct ArtistPageData {
    public let artistName: String
    public let avatarUrl: String?
    public let bannerUrl: String?
    public let topSongs: [Song]
    public let recentVideos: [Song]

    public init(
        artistName: String,
        avatarUrl: String? = nil,
        bannerUrl: String? = nil,
        topSongs: [Song] = [],
        recentVideos: [Song] = []
    ) {
        self.artistName = artistName
        self.avatarUrl = avatarUrl
        self.bannerUrl = bannerUrl
        self.topSongs = topSongs
        self.recentVideos = recentVideos
    }
}


// MARK: - Search Suggestions & Unified Search (1:1 Android Native 2 Parity)

public enum SuggestionItem: Identifiable {
    case query(query: String, isHistory: Bool)
    case song(song: Song)
    case artist(artistName: String, avatarUrl: String?, subtitle: String)

    public var id: String {
        switch self {
        case .query(let q, _): return "q_\(q)"
        case .song(let s): return "s_\(s.id)"
        case .artist(let a, _, _): return "a_\(a)"
        }
    }
}

public struct ArtistSearchResult {
    public let artistName: String
    public let avatarUrl: String?
    public let browseId: String?
    public let topSongs: [Song]
    public let matchPercentage: Float

    public init(
        artistName: String,
        avatarUrl: String? = nil,
        browseId: String? = nil,
        topSongs: [Song] = [],
        matchPercentage: Float = 0
    ) {
        self.artistName = artistName
        self.avatarUrl = avatarUrl
        self.browseId = browseId
        self.topSongs = topSongs
        self.matchPercentage = matchPercentage
    }
}

public struct UnifiedSearchResult {
    public let query: String
    public let isArtistSearch: Bool
    public let detectedArtist: ArtistSearchResult?
    public let songs: [Song]

    public init(
        query: String = "",
        isArtistSearch: Bool = false,
        detectedArtist: ArtistSearchResult? = nil,
        songs: [Song] = []
    ) {
        self.query = query
        self.isArtistSearch = isArtistSearch
        self.detectedArtist = detectedArtist
        self.songs = songs
    }
}

extension YouTubeMusicApi {
    public func fetchSearchSuggestions(input: String) async -> [SuggestionItem] {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }

        guard let url = URL(string: "https://music.youtube.com/youtubei/v1/music/get_search_suggestions") else {
            return []
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("https://music.youtube.com", forHTTPHeaderField: "Origin")
        request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36", forHTTPHeaderField: "User-Agent")

        let payload: [String: Any] = [
            "input": trimmed,
            "context": [
                "client": [
                    "clientName": "WEB_REMIX",
                    "clientVersion": "1.20260701.01.00",
                    "hl": "en",
                    "gl": "IN"
                ]
            ]
        ]

        guard let httpBody = try? JSONSerialization.data(withJSONObject: payload) else { return [] }
        request.httpBody = httpBody

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let contents = json["contents"] as? [[String: Any]] else {
                return fallbackSuggestions(trimmed: trimmed)
            }

            var itemsList: [SuggestionItem] = []

            for sec in contents {
                guard let sectionRenderer = sec["searchSuggestionsSectionRenderer"] as? [String: Any],
                      let contentsArray = sectionRenderer["contents"] as? [[String: Any]] else { continue }

                for itemElem in contentsArray {
                    if let suggestionRenderer = itemElem["searchSuggestionRenderer"] as? [String: Any],
                       let suggestion = suggestionRenderer["suggestion"] as? [String: Any],
                       let runs = suggestion["runs"] as? [[String: Any]] {
                        let text = runs.compactMap { $0["text"] as? String }.joined()
                        if !text.isEmpty {
                            itemsList.append(.query(query: text, isHistory: false))
                        }
                        continue
                    }

                    guard let responsiveItem = itemElem["musicResponsiveListItemRenderer"] as? [String: Any],
                          let flexColumns = responsiveItem["flexColumns"] as? [[String: Any]],
                          !flexColumns.isEmpty else { continue }

                    let titleCol = flexColumns[0]["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any]
                    let titleRuns = (titleCol?["text"] as? [String: Any])?["runs"] as? [[String: Any]]
                    let titleText = titleRuns?.compactMap { $0["text"] as? String }.joined() ?? ""

                    var subtitleText = ""
                    if flexColumns.count > 1 {
                        let subCol = flexColumns[1]["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any]
                        let subRuns = (subCol?["text"] as? [String: Any])?["runs"] as? [[String: Any]]
                        subtitleText = subRuns?.compactMap { $0["text"] as? String }.joined() ?? ""
                    }

                    var thumbUrl: String? = nil
                    if let thumbRenderer = (responsiveItem["thumbnail"] as? [String: Any])?["musicThumbnailRenderer"] as? [String: Any],
                       let thumbObj = thumbRenderer["thumbnail"] as? [String: Any],
                       let thumbnails = thumbObj["thumbnails"] as? [[String: Any]] {
                        thumbUrl = thumbnails.last?["url"] as? String
                    }

                    var videoId = ""
                    if let navEndpoint = responsiveItem["navigationEndpoint"] as? [String: Any],
                       let watchEndpoint = navEndpoint["watchEndpoint"] as? [String: Any],
                       let vId = watchEndpoint["videoId"] as? String {
                        videoId = vId
                    }

                    if videoId.count == 11 {
                        let song = Song(
                            id: videoId,
                            title: titleText,
                            author: subtitleText.isEmpty ? "Song" : subtitleText,
                            thumbnail: thumbUrl ?? "https://i.ytimg.com/vi/\(videoId)/hqdefault.jpg",
                            durationSeconds: 210
                        )
                        itemsList.append(.song(song: song))
                    } else if subtitleText.localizedCaseInsensitiveContains("artist") ||
                              subtitleText.localizedCaseInsensitiveContains("subscribers") {
                        itemsList.append(.artist(artistName: titleText, avatarUrl: thumbUrl, subtitle: subtitleText))
                    } else if !titleText.isEmpty {
                        itemsList.append(.query(query: titleText, isHistory: false))
                    }
                }
            }

            if !itemsList.isEmpty {
                return itemsList
            }
        } catch {}

        return fallbackSuggestions(trimmed: trimmed)
    }

    private func fallbackSuggestions(trimmed: String) -> [SuggestionItem] {
        return [.query(query: trimmed, isHistory: false)]
    }

    public func searchUnified(query: String) async -> UnifiedSearchResult {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return UnifiedSearchResult()
        }

        let songs = await searchSongs(query: trimmed)
        let suggestions = await fetchSearchSuggestions(input: trimmed)

        // Check for matched artist
        var matchedArtistName: String? = nil
        var matchedAvatar: String? = nil

        for item in suggestions {
            if case let .artist(name, avatar, _) = item {
                if name.localizedCaseInsensitiveContains(trimmed) || trimmed.localizedCaseInsensitiveContains(name) {
                    matchedArtistName = name
                    matchedAvatar = avatar
                    break
                }
            }
        }

        if let artistName = matchedArtistName {
            let artistTop = songs.filter {
                $0.author.localizedCaseInsensitiveContains(artistName) ||
                $0.title.localizedCaseInsensitiveContains(artistName)
            }
            let detected = ArtistSearchResult(
                artistName: artistName,
                avatarUrl: matchedAvatar ?? songs.first?.thumbnail,
                topSongs: artistTop.isEmpty ? Array(songs.prefix(5)) : artistTop,
                matchPercentage: 1.0
            )
            return UnifiedSearchResult(
                query: trimmed,
                isArtistSearch: true,
                detectedArtist: detected,
                songs: songs
            )
        }

        return UnifiedSearchResult(
            query: trimmed,
            isArtistSearch: false,
            detectedArtist: nil,
            songs: songs
        )
    }

    // MARK: - Official Music Video Resolution
    public func resolveOfficialMusicVideo(title: String, artist: String) async -> Song? {
        let cleanTitle = title
            .replacingOccurrences(of: #"(?i)\b(official|audio|video|lyric|lyrics|remix|hd|4k|song|track)\b"#, with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanArtist = artist
            .replacingOccurrences(of: #"(?i)\b(official|topic|vevo)\b"#, with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)

        let searchQueries = [
            "\(cleanTitle) \(cleanArtist) official music video",
            "\(cleanTitle) \(cleanArtist) official video",
            "\(cleanTitle) official music video"
        ]

        // 1. YouTube Music Videos filter ("EgWKAQIQAWoKEAkQBRAKEAMQBA==")
        for query in searchQueries {
            let candidates = await fetchYouTubeMusicSearch(query: query, params: "EgWKAQIQAWoKEAkQBRAKEAMQBA==")
            if let match = candidates.first(where: { $0.id.count == 11 }) {
                return match
            }
        }

        // 2. Fallback: YouTube HTML Search
        for query in searchQueries {
            let candidates = await fetchHtmlYoutubeSearch(query: query)
            if let match = candidates.first(where: { $0.id.count == 11 }) {
                return match
            }
        }

        return nil
    }

    public static func resolveOfficialMusicVideo(title: String, artist: String) async -> Song? {
        return await shared.resolveOfficialMusicVideo(title: title, artist: artist)
    }
}

