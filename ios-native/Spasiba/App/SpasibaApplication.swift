import Foundation
import SwiftUI

/// 1:1 Swift counterpart of SpasibaApplication.kt
public final class SpasibaApplication {
    public static let shared = SpasibaApplication()

    public let playerManager = NativePlayerManager.shared

    public init() {
        setup()
    }

    public func setup() {
        _ = EunoiaSessionManager.shared
        _ = SpasibaAudioCacheManager.shared
        _ = SpasibaDownloadManager.shared
    }
}
