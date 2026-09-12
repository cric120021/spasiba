import Foundation
import UserNotifications
import UIKit

public final class SpasibaFcmService: NSObject {
    public static let shared = SpasibaFcmService()

    public static let CHANNEL_ID = "eunoia_push_invites"
    public static let NOTIFICATION_ID = 8888

    private let tag = "SpasibaFcmService"

    public func onNewToken(_ token: String) {
        print("[\(tag)] New FCM/APNs token received: \(token)")
        FcmTokenManager.shared.updateFcmToken(token)
    }

    public func handleRemoteNotification(userInfo: [AnyHashable: Any]) {
        print("[\(tag)] Push notification payload received: \(userInfo)")

        let data = userInfo["data"] as? [String: Any] ?? userInfo as? [String: Any] ?? [:]
        let messageType = data["type"] as? String ?? userInfo["type"] as? String ?? ""

        switch messageType {
        case "EUNOIA_INVITE":
            let sessionId = data["sessionId"] as? String ?? ""
            let inviterDeviceId = data["inviterDeviceId"] as? String ?? "Partner"
            let inviterName = data["inviterName"] as? String ?? inviterDeviceId
            let songTitle = data["songTitle"] as? String ?? "Music Track"

            // Notify in-app session manager with real sessionId
            EunoiaSessionManager.shared.receiveSimulatedInvite(inviterDeviceId: inviterDeviceId, sessionId: sessionId)

            // Display high priority notification with Accept / Reject buttons
            showInviteNotification(
                sessionId: sessionId,
                inviterDeviceId: inviterDeviceId,
                inviterName: inviterName,
                songTitle: songTitle
            )

        case "FRIEND_REQUEST":
            let senderId = data["senderId"] as? String ?? data["fromId"] as? String ?? data["inviterDeviceId"] as? String ?? ""
            let senderName = data["senderName"] as? String ?? data["inviterName"] as? String ?? "Friend (\(String(senderId.suffix(6))))"
            let photoUri = data["photoUri"] as? String ?? data["avatarUrl"] as? String
            if !senderId.isEmpty {
                print("[\(tag)] FRIEND_REQUEST received from senderId=\(senderId) (\(senderName))")
                EunoiaFriendManager.shared.addOrSendFriendRequest(
                    friendId: senderId,
                    name: senderName,
                    isOutgoing: false,
                    photoUri: photoUri
                )
                EunoiaSessionManager.shared.friendEventTimestamp = Date().timeIntervalSince1970
            }

        case "FRIEND_ACCEPT":
            let senderId = data["senderId"] as? String ?? data["fromId"] as? String ?? ""
            let senderName = data["senderName"] as? String ?? data["inviterName"] as? String
            if !senderId.isEmpty {
                print("[\(tag)] FRIEND_ACCEPT received from senderId=\(senderId)")
                EunoiaFriendManager.shared.acceptFriendRequest(friendId: senderId, newName: senderName ?? "")
                EunoiaSessionManager.shared.friendEventTimestamp = Date().timeIntervalSince1970
            }

        case "FRIEND_REMOVE":
            let senderId = data["senderId"] as? String ?? data["fromId"] as? String ?? ""
            if !senderId.isEmpty {
                print("[\(tag)] Silent FRIEND_REMOVE received for senderId=\(senderId)")
                EunoiaFriendManager.shared.removeFriend(friendId: senderId)
                EunoiaSessionManager.shared.friendEventTimestamp = Date().timeIntervalSince1970
            }

        default:
            break
        }
    }

    public func showInviteNotification(
        sessionId: String,
        inviterDeviceId: String,
        inviterName: String,
        songTitle: String
    ) {
        let center = UNUserNotificationCenter.current()

        let acceptAction = UNNotificationAction(
            identifier: EunoiaInviteReceiver.ACTION_ACCEPT_INVITE,
            title: "Accept",
            options: [.foreground]
        )
        let rejectAction = UNNotificationAction(
            identifier: EunoiaInviteReceiver.ACTION_REJECT_INVITE,
            title: "Reject",
            options: [.destructive]
        )

        let category = UNNotificationCategory(
            identifier: Self.CHANNEL_ID,
            actions: [acceptAction, rejectAction],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )

        center.setNotificationCategories([category])

        let content = UNMutableNotificationContent()
        content.title = "🎧 Listening Invite from \(inviterName)"
        content.body = "Song: \(songTitle) • Tap to listen together"
        content.sound = .default
        content.categoryIdentifier = Self.CHANNEL_ID
        content.userInfo = [
            "open_eunoia_screen": true,
            "sessionId": sessionId,
            EunoiaInviteReceiver.EXTRA_SESSION_ID: sessionId,
            EunoiaInviteReceiver.EXTRA_INVITER_ID: inviterDeviceId
        ]

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.1, repeats: false)
        let request = UNNotificationRequest(
            identifier: "eunoia_invite_\(sessionId)",
            content: content,
            trigger: trigger
        )

        center.add(request) { error in
            if let error = error {
                print("SpasibaFcmService: Error presenting invite notification: \(error)")
            }
        }
    }
}
