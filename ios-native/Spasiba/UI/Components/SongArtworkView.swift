import SwiftUI

public struct SongArtworkView: View {
    public let song: Song?
    public let directUrl: String?
    public let directFallbackId: String?
    public var cornerRadius: CGFloat = 8

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
        return URL(string: "https://lh3.googleusercontent.com/9_qmVOvLuS7mCYj7nw6Zgy01K3NKSaI8YvLu_spdTL6i1goKHEVoMZGVCetG7RzlI2M4e4gjM6DquZ3L=w500")
    }

    public var body: some View {
        ZStack {
            Color(hex: 0x222222)

            if let url = targetUrl {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    case .failure:
                        // Automatic fallback to direct YouTube CDN
                        if let fb = fallbackUrl, fb != url {
                            AsyncImage(url: fb) { p2 in
                                if let img2 = p2.image {
                                    img2.resizable().aspectRatio(contentMode: .fill)
                                } else {
                                    placeholderView
                                }
                            }
                        } else {
                            placeholderView
                        }
                    case .empty:
                        ZStack {
                            Color(hex: 0x242426)
                            ProgressView()
                                .scaleEffect(0.6)
                                .progressViewStyle(CircularProgressViewStyle(tint: .white.opacity(0.4)))
                        }
                    @unknown default:
                        placeholderView
                    }
                }
            } else if let fb = fallbackUrl {
                AsyncImage(url: fb) { phase in
                    if let image = phase.image {
                        image.resizable().aspectRatio(contentMode: .fill)
                    } else {
                        placeholderView
                    }
                }
            } else {
                placeholderView
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
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
