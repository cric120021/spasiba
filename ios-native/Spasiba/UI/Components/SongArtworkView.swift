import SwiftUI

// MARK: - In-Memory Thread-Safe Image Cache (0ms Instant Display)
public final class SpasibaImageCache: @unchecked Sendable {
    public static let shared = SpasibaImageCache()

    private let memoryCache = NSCache<NSURL, UIImage>()

    private init() {
        memoryCache.countLimit = 600
        memoryCache.totalCostLimit = 150 * 1024 * 1024 // 150 MB memory limit
    }

    public func image(for url: URL) -> UIImage? {
        return memoryCache.object(forKey: url as NSURL)
    }

    public func insert(_ image: UIImage, for url: URL) {
        let cost = Int(image.size.width * image.size.height * 4)
        memoryCache.setObject(image, forKey: url as NSURL, cost: cost)
    }

    public func remove(for url: URL) {
        memoryCache.removeObject(forKey: url as NSURL)
    }
}

public struct SongArtworkView: View {
    public let song: Song?
    public let directUrl: String?
    public let directFallbackId: String?
    public var cornerRadius: CGFloat = 8

    @State private var loadedImage: UIImage? = nil
    @State private var hasFailedTarget: Bool = false

    public init(song: Song, cornerRadius: CGFloat = 8) {
        self.song = song
        self.directUrl = nil
        self.directFallbackId = nil
        self.cornerRadius = cornerRadius
    }

    public init(url: String?, fallbackId: String? = nil, cornerRadius: CGFloat = 8) {
        self.song = nil
        self.directUrl = url
        self.directFallbackId = fallbackId
        self.cornerRadius = cornerRadius
    }

    private var targetUrl: URL? {
        if let s = song {
            return s.resolvedThumbnailUrl
        }
        guard var str = directUrl?.trimmingCharacters(in: .whitespacesAndNewlines), !str.isEmpty else {
            return nil
        }
        if str.hasPrefix("//") {
            str = "https:" + str
        } else if str.hasPrefix("http://") {
            str = "https://" + str.dropFirst(7)
        }
        if str.contains("w120-h120") || str.contains("w60-h60") {
            str = str.replacingOccurrences(of: "w120-h120", with: "w544-h544")
                     .replacingOccurrences(of: "w60-h60", with: "w544-h544")
        }
        return URL(string: str)
    }

    private var fallbackUrl: URL? {
        let vid = song?.id ?? directFallbackId ?? ""
        if vid.count == 11 && !vid.hasPrefix("yt_") && !vid.hasPrefix("card_") {
            return URL(string: "https://i.ytimg.com/vi/\(vid)/hqdefault.jpg")
        }
        return nil
    }

    private var activeUrl: URL? {
        if !hasFailedTarget, let t = targetUrl {
            return t
        }
        return fallbackUrl
    }

    public var body: some View {
        let url = activeUrl
        let cached = url.flatMap { SpasibaImageCache.shared.image(for: $0) }

        ZStack {
            Color(hex: 0x222222)

            if let img = loadedImage ?? cached {
                Image(uiImage: img)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } else if let loadUrl = url {
                placeholderView
                    .task(id: loadUrl) {
                        await fetchImage(url: loadUrl)
                    }
            } else {
                placeholderView
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
    }

    private func fetchImage(url: URL) async {
        if let existing = SpasibaImageCache.shared.image(for: url) {
            await MainActor.run { self.loadedImage = existing }
            return
        }

        var request = URLRequest(url: url)
        request.cachePolicy = .returnCacheDataElseLoad
        request.timeoutInterval = 10.0

        do {
            let (data, response) = try await HttpClientProvider.shared.session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200,
                  let image = UIImage(data: data) else {
                handleFetchFailure(failedUrl: url)
                return
            }

            SpasibaImageCache.shared.insert(image, for: url)
            await MainActor.run {
                self.loadedImage = image
            }
        } catch {
            handleFetchFailure(failedUrl: url)
        }
    }

    private func handleFetchFailure(failedUrl: URL) {
        Task { @MainActor in
            if !hasFailedTarget, let fb = fallbackUrl, fb != failedUrl {
                self.hasFailedTarget = true
            }
        }
    }

    private var placeholderView: some View {
        ZStack {
            LinearGradient(
                colors: [Color(hex: 0x28282E), Color(hex: 0x1A1A1E)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Image(systemName: "music.note")
                .font(.system(size: 18))
                .foregroundColor(Color.white.opacity(0.35))
        }
    }
}
