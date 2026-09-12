import Foundation
import UserNotifications

public final class EunoiaInviteReceiver: NSObject, UNUserNotificationCenterDelegate {
    public static let shared = EunoiaInviteReceiver()

    public static let ACTION_ACCEPT_INVITE = "app.spasiba.nativeapp2.ACTION_ACCEPT_INVITE"
    public static let ACTION_REJECT_INVITE = "app.spasiba.nativeapp2.ACTION_REJECT_INVITE"
    public static let EXTRA_SESSION_ID = "extra_session_id"
    public static let EXTRA_INVITER_ID = "extra_inviter_id"

    private let tag = "EunoiaInviteReceiver"

    public func handleAction(actionIdentifier: String, userInfo: [AnyHashable: Any]) {
        let sessionId = userInfo[Self.EXTRA_SESSION_ID] as? String ?? userInfo["sessionId"] as? String ?? ""
        let inviterId = userInfo[Self.EXTRA_INVITER_ID] as? String ?? ""

        print("[\(tag)] Received action \(actionIdentifier) for session \(sessionId) from \(inviterId)")

        // Auto-dismiss notification from notification center
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ["eunoia_invite_\(sessionId)"])

        let sessionManager = EunoiaSessionManager.shared

        switch actionIdentifier {
        case Self.ACTION_ACCEPT_INVITE:
            DispatchQueue.main.async {
                sessionManager.acceptInvite(senderId: inviterId, sessionId: sessionId)
            }

        case Self.ACTION_REJECT_INVITE:
            DispatchQueue.main.async {
                sessionManager.rejectInvite(senderId: inviterId)
            }

        default:
            break
        }
    }

    // MARK: - UNUserNotificationCenterDelegate
    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let actionIdentifier = response.actionIdentifier
        let userInfo = response.notification.request.content.userInfo
        handleAction(actionIdentifier: actionIdentifier, userInfo: userInfo)
        completionHandler()
    }

    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .badge, .sound])
    }
}
