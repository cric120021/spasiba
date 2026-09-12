import SwiftUI
import UIKit

public enum AppNavigationType: Equatable {
    case threeButton
    case twoButton
    case gestureNavigation
    case unknown

    // Kotlin enum naming compatibility
    public static let THREE_BUTTON = AppNavigationType.threeButton
    public static let TWO_BUTTON = AppNavigationType.twoButton
    public static let GESTURE_NAVIGATION = AppNavigationType.gestureNavigation
    public static let UNKNOWN = AppNavigationType.unknown
}

public enum NavigationUtils {
    public static func getSystemNavigationType() -> AppNavigationType {
        guard let windowScene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene ?? UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = windowScene.windows.first(where: { $0.isKeyWindow }) ?? windowScene.windows.first else {
            return .gestureNavigation
        }
        return window.safeAreaInsets.bottom > 0 ? .gestureNavigation : .threeButton
    }
}

public func rememberNavigationType() -> AppNavigationType {
    NavigationUtils.getSystemNavigationType()
}

public func getCustomBottomNavigationPadding() -> CGFloat {
    let navType = NavigationUtils.getSystemNavigationType()
    if navType == .gestureNavigation {
        return 3.0
    } else {
        guard let windowScene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene ?? UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = windowScene.windows.first(where: { $0.isKeyWindow }) ?? windowScene.windows.first else {
            return 0.0
        }
        return window.safeAreaInsets.bottom
    }
}
