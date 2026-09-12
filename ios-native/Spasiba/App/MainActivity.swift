import SwiftUI
import Combine

/// 1:1 Swift counterpart of MainActivity.kt
public struct MainActivity: View {
    public static let requestedTab = CurrentValueSubject<Int?, Never>(nil)
    public static let requestedChatFriendId = CurrentValueSubject<String?, Never>(nil)
    public static let requestedChatFriendName = CurrentValueSubject<String?, Never>(nil)

    public init() {}

    public var body: some View {
        SpasibaAppShell()
    }
}
