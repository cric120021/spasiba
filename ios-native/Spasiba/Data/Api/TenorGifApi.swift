import Foundation

public struct TenorGifItem: Identifiable, Codable {
    public let id: String
    public let title: String
    public let previewUrl: String
    public let gifUrl: String

    public init(id: String, title: String, previewUrl: String, gifUrl: String) {
        self.id = id
        self.title = title
        self.previewUrl = previewUrl
        self.gifUrl = gifUrl
    }
}

public final class TenorGifApi {
    public static let shared = TenorGifApi()
    private static let apiKey = "LIVDSRZULEXM"

    private init() {}

    public static func searchGifs(query: String = "") async -> [TenorGifItem] {
        return await shared.searchGifs(query: query)
    }

    public func searchGifs(query: String = "") async -> [TenorGifItem] {
        let clean = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let endpoint: String
        if clean.isEmpty {
            endpoint = "https://g.tenor.com/v1/trending?key=\(Self.apiKey)&limit=30"
        } else {
            let encoded = clean.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? clean
            endpoint = "https://g.tenor.com/v1/search?q=\(encoded)&key=\(Self.apiKey)&limit=30"
        }

        guard let url = URL(string: endpoint) else { return [] }
        var request = URLRequest(url: url)
        request.setValue("SpasibaNative2/1.0", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 8.0

        do {
            let (data, response) = try await HttpClientProvider.shared.session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                return []
            }

            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let results = json["results"] as? [[String: Any]] else {
                return []
            }

            var gifItems: [TenorGifItem] = []
            for elem in results {
                let id = elem["id"] as? String ?? UUID().uuidString
                let title = elem["title"] as? String ?? "GIF"
                guard let mediaArray = elem["media"] as? [[String: Any]], let firstMedia = mediaArray.first else {
                    continue
                }

                let gifMedia = (firstMedia["gif"] as? [String: Any])
                    ?? (firstMedia["mediumgif"] as? [String: Any])
                    ?? (firstMedia["tinygif"] as? [String: Any])
                let nanoMedia = (firstMedia["nanogif"] as? [String: Any])
                    ?? (firstMedia["tinygif"] as? [String: Any])
                    ?? gifMedia

                guard let gifUrl = gifMedia?["url"] as? String else { continue }
                let previewUrl = (nanoMedia?["url"] as? String) ?? gifUrl

                gifItems.append(TenorGifItem(id: id, title: title, previewUrl: previewUrl, gifUrl: gifUrl))
            }
            return gifItems
        } catch {
            return []
        }
    }
}
