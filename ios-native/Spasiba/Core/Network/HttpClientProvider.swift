import Foundation

public final class HttpClientProvider {
    public static let shared = HttpClientProvider()

    public let session: URLSession

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10.0
        config.timeoutIntervalForResource = 30.0
        config.httpAdditionalHeaders = [
            "Accept-Encoding": "gzip, deflate",
            "User-Agent": "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X)"
        ]
        config.urlCache = URLCache(memoryCapacity: 50 * 1024 * 1024, diskCapacity: 200 * 1024 * 1024, diskPath: "spasiba_http_cache")
        self.session = URLSession(configuration: config)
    }
}
