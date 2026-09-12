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

    public func fetchYouTubeMusicHomeShelves() async -> [HomeShelf] {
        return await fetchYouTubeMusicBrowseShelves(browseId: "FEmusic_home")
    }

    public func fetchYouTubeMusicBrowseShelves(browseId: String = "FEmusic_home", params: String? = nil) async -> [HomeShelf] {
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

    public func searchSongs(query: String) async -> [Song] {
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanQuery.isEmpty else { return [] }

        if let cached = searchCache[cleanQuery.lowercased()] {
            return cached
        }

        guard let url = URL(string: "https://music.youtube.com/youtubei/v1/search") else { return [] }

        let payload: [String: Any] = [
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
            let songs = parseSearchContents(json)

            if !songs.isEmpty {
                searchCache[cleanQuery.lowercased()] = songs
            }
            return songs
        } catch {
            return []
        }
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
            } else {
                thumb = "https://lh3.googleusercontent.com/9_qmVOvLuS7mCYj7nw6Zgy01K3NKSaI8YvLu_spdTL6i1goKHEVoMZGVCetG7RzlI2M4e4gjM6DquZ3L=w500"
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
        var results: [Song] = []

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
            if let musicShelf = section["musicShelfRenderer"] as? [String: Any],
               let items = musicShelf["contents"] as? [[String: Any]] {
                for item in items {
                    if let song = parseSongItem(item) {
                        results.append(song)
                    }
                }
            } else if let cardShelf = section["musicCardShelfRenderer"] as? [String: Any],
                      let titleRuns = (cardShelf["title"] as? [String: Any])?["runs"] as? [[String: Any]],
                      let titleText = titleRuns.first?["text"] as? String {
                let author = ((cardShelf["subtitle"] as? [String: Any])?["runs"] as? [[String: Any]])?.compactMap { $0["text"] as? String }.joined() ?? "Artist"
                let thumb = extractThumbnail(cardShelf)
                results.append(Song(id: "card_\(abs(titleText.hashValue))", title: titleText, author: author, thumbnail: thumb, durationSeconds: 210))
            }
        }

        return results
    }

    private func parseSongItem(_ item: [String: Any]) -> Song? {
        guard let itemRenderer = item["musicResponsiveListItemRenderer"] as? [String: Any] else { return nil }

        var videoId = ""
        if let playlistItemData = itemRenderer["playlistItemData"] as? [String: Any],
           let vId = playlistItemData["videoId"] as? String {
            videoId = vId
        }

        guard let flexColumns = itemRenderer["flexColumns"] as? [[String: Any]], !flexColumns.isEmpty else { return nil }
        
        let titleColumn = flexColumns[0]
        guard let titleRuns = ((titleColumn["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any])?["text"] as? [String: Any])?["runs"] as? [[String: Any]],
              let titleText = titleRuns.first?["text"] as? String else {
            return nil
        }

        var authorText = "YouTube Music"
        if flexColumns.count > 1 {
            let authorColumn = flexColumns[1]
            if let authorRuns = ((authorColumn["musicResponsiveListItemFlexColumnRenderer"] as? [String: Any])?["text"] as? [String: Any])?["runs"] as? [[String: Any]] {
                authorText = authorRuns.compactMap { $0["text"] as? String }.joined()
            }
        }

        var thumb = extractThumbnail(itemRenderer)
        let finalId = videoId.count == 11 ? videoId : "yt_\(abs(titleText.hashValue))"
        if thumb.isEmpty {
            if videoId.count == 11 {
                thumb = "https://i.ytimg.com/vi/\(videoId)/hqdefault.jpg"
            } else {
                thumb = "https://lh3.googleusercontent.com/9_qmVOvLuS7mCYj7nw6Zgy01K3NKSaI8YvLu_spdTL6i1goKHEVoMZGVCetG7RzlI2M4e4gjM6DquZ3L=w500"
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
}
