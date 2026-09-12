import Foundation

public struct Song: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public let title: String
    public let author: String
    public let thumbnail: String
    public let durationSeconds: Int
    public var streamUrl: String?
    public var isLiked: Bool
    public var lyrics: String?
    public var views: Int64

    public init(
        id: String,
        title: String,
        author: String,
        thumbnail: String,
        durationSeconds: Int = 0,
        streamUrl: String? = nil,
        isLiked: Bool = false,
        lyrics: String? = nil,
        views: Int64 = 0
    ) {
        self.id = id
        self.title = title
        self.author = author
        self.thumbnail = thumbnail
        self.durationSeconds = durationSeconds
        self.streamUrl = streamUrl
        self.isLiked = isLiked
        self.lyrics = lyrics
        self.views = views
    }

    public var durationFormatted: String {
        let mins = durationSeconds / 60
        let secs = durationSeconds % 60
        return String(format: "%d:%02d", mins, secs)
    }

    public var highResThumbnail: String {
        if thumbnail.contains("w120-h120") || thumbnail.contains("w60-h60") {
            return thumbnail
                .replacingOccurrences(of: "w120-h120", with: "w544-h544")
                .replacingOccurrences(of: "w60-h60", with: "w544-h544")
        }
        return thumbnail
    }

    public var resolvedThumbnailUrl: URL? {
        var str = thumbnail.trimmingCharacters(in: .whitespacesAndNewlines)
        if str.hasPrefix("//") {
            str = "https:" + str
        } else if str.hasPrefix("http://") {
            str = "https://" + str.dropFirst(7)
        }
        if str.isEmpty || str.contains("default/hqdefault.jpg") || str.contains("vi/yt_") || str.contains("vi/card_") {
            if id.count == 11 && !id.hasPrefix("yt_") && !id.hasPrefix("card_") {
                str = "https://i.ytimg.com/vi/\(id)/hqdefault.jpg"
            } else {
                str = "https://lh3.googleusercontent.com/9_qmVOvLuS7mCYj7nw6Zgy01K3NKSaI8YvLu_spdTL6i1goKHEVoMZGVCetG7RzlI2M4e4gjM6DquZ3L=w500"
            }
        }
        if str.contains("w120-h120") || str.contains("w60-h60") {
            str = str.replacingOccurrences(of: "w120-h120", with: "w544-h544")
                     .replacingOccurrences(of: "w60-h60", with: "w544-h544")
        }
        return URL(string: str)
    }
}

public struct CategoryChip: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public let label: String
    public let params: String

    public init(id: String, label: String, params: String) {
        self.id = id
        self.label = label
        self.params = params
    }
}

public enum ShelfLayoutStyle: String, Codable {
    case quickPicksGrid = "QUICK_PICKS_GRID"
    case carouselSquare = "CAROUSEL_SQUARE"
    case videoWidescreen = "VIDEO_WIDESCREEN"
    case chartsRanked = "CHARTS_RANKED"
    case artistSpotlight = "ARTIST_SPOTLIGHT"
    case heroBanner = "HERO_BANNER"
}

public struct HomeShelf: Identifiable, Codable {
    public var id: String { title }
    public let title: String
    public let subtitle: String?
    public let style: ShelfLayoutStyle
    public let songs: [Song]

    public init(title: String, subtitle: String? = nil, style: ShelfLayoutStyle = .carouselSquare, songs: [Song]) {
        self.title = title
        self.subtitle = subtitle
        self.style = style
        self.songs = songs
    }
}

public struct StreamingResult: Codable {
    public let url: String
    public let audioUrl: String?
    public let headers: [String: String]

    public init(url: String, audioUrl: String? = nil, headers: [String: String] = [:]) {
        self.url = url
        self.audioUrl = audioUrl
        self.headers = headers
    }
}
