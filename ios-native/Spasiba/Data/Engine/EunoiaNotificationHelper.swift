import Foundation
import UserNotifications
import UIKit

public enum EunoiaNotificationHelper {
    public static let CHANNEL_ID = "eunoia_friends_channel_v3"
    public static let CHANNEL_NAME = "Friends & Listen Invites"

    public static func createNotificationChannel() {
        let center = UNUserNotificationCenter.current()
        let friendRequestCategory = UNNotificationCategory(
            identifier: CHANNEL_ID,
            actions: [],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )
        center.setNotificationCategories([friendRequestCategory])
    }

    public static func showFriendRequestNotification(senderId: String, senderName: String) {
        createNotificationChannel()

        let content = UNMutableNotificationContent()
        content.title = "Friend Request from \(senderName)"
        content.body = "Tap to accept and view in your Friends list!"
        content.sound = .default
        content.categoryIdentifier = CHANNEL_ID
        content.userInfo = [
            "requested_tab": 6,
            "sender_id": senderId
        ]

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.1, repeats: false)
        let request = UNNotificationRequest(
            identifier: "friend_req_\(senderId)",
            content: content,
            trigger: trigger
        )

        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("EunoiaNotificationHelper: Error showing friend request notification: \(error)")
            }
        }
    }

    public static func showFriendAcceptedNotification(senderId: String, senderName: String) {
        createNotificationChannel()

        let content = UNMutableNotificationContent()
        content.title = "\(senderName) accepted your friend request!"
        content.body = "You are now connected! Tap to listen together or chat."
        content.sound = .default
        content.categoryIdentifier = CHANNEL_ID
        content.userInfo = [
            "requested_tab": 6,
            "sender_id": senderId
        ]

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.1, repeats: false)
        let request = UNNotificationRequest(
            identifier: "friend_acc_\(senderId)",
            content: content,
            trigger: trigger
        )

        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("EunoiaNotificationHelper: Error showing friend accepted notification: \(error)")
            }
        }
    }

    public static func showFriendChatMessageNotification(senderId: String, senderName: String, messageText: String) {
        createNotificationChannel()

        let content = UNMutableNotificationContent()
        content.title = "Message from \(senderName)"
        content.body = messageText
        content.sound = .default
        content.categoryIdentifier = CHANNEL_ID
        content.userInfo = [
            "requested_tab": 7,
            "requested_chat_friend_id": senderId,
            "requested_chat_friend_name": senderName
        ]

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.1, repeats: false)
        let request = UNNotificationRequest(
            identifier: "friend_msg_\(senderId)_\(Date().timeIntervalSince1970)",
            content: content,
            trigger: trigger
        )

        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("EunoiaNotificationHelper: Error showing chat message notification: \(error)")
            }
        }
    }
}
