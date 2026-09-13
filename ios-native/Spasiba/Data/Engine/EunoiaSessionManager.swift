import Foundation

public enum EunoiaSessionStatus: String, Codable {
    case idle = "IDLE"
    case inviteSent = "INVITE_SENT"
    case inviteReceived = "INVITE_RECEIVED"
    case connected = "CONNECTED"
    case rejectedBusy = "REJECTED_BUSY"
}

public final class EunoiaSessionManager: ObservableObject {
    public static let shared = EunoiaSessionManager()

    public static func getInstance() -> EunoiaSessionManager {
        return shared
    }

    @Published public var sessionStatus: EunoiaSessionStatus = .idle
    @Published public var myDeviceId: String = "SPASIBA-8A2F41"
    @Published public var partnerDeviceId: String? = nil
    @Published public var partnerName: String? = nil
    @Published public var partnerPhotoUri: String? = nil
    @Published public var sessionId: String? = nil
    @Published public var pendingInviteSender: String? = nil
    @Published public var friendEventTimestamp: TimeInterval = Date().timeIntervalSince1970

    private init() {
        let userDefaults = UserDefaults.standard
        if let storedId = userDefaults.string(forKey: "spasiba_device_id") {
            self.myDeviceId = storedId
        } else {
            let randomHex = String(format: "%06X", arc4random_uniform(0xFFFFFF))
            let newId = "SPASIBA-\(randomHex)"
            userDefaults.set(newId, forKey: "spasiba_device_id")
            self.myDeviceId = newId
        }
    }

    public func sendFriendRequestSignal(friendCode: String, friendName: String) {
        EunoiaFriendManager.shared.addOrSendFriendRequest(
            friendId: friendCode,
            name: friendName,
            isOutgoing: true
        )
        self.friendEventTimestamp = Date().timeIntervalSince1970
    }

    public func acceptFriendRequestSignal(friendId: String, friendName: String) {
        EunoiaFriendManager.shared.acceptFriendRequest(friendId: friendId, newName: friendName)
        self.friendEventTimestamp = Date().timeIntervalSince1970
    }

    public func removeFriendSignal(friendId: String) {
        EunoiaFriendManager.shared.removeFriend(friendId: friendId)
        self.friendEventTimestamp = Date().timeIntervalSince1970
    }

    public func sendFriendChatMessageSignal(_ friendId: String, _ text: String) {
        EunoiaFriendManager.shared.addChatMessage(
            friendId: friendId,
            senderId: myDeviceId,
            senderName: "You",
            text: text,
            isPartner: false
        )
        self.friendEventTimestamp = Date().timeIntervalSince1970
    }

    public func deleteFriendChatHistorySignal(_ friendId: String) {
        EunoiaFriendManager.shared.clearChatHistory(friendId: friendId)
        self.friendEventTimestamp = Date().timeIntervalSince1970
    }

    public func dismissInviteLocally() {
        self.pendingInviteSender = nil
        if self.sessionStatus == .inviteReceived {
            self.sessionStatus = .idle
        }
        self.friendEventTimestamp = Date().timeIntervalSince1970
    }

    public func quickInvite(friend: EunoiaFriend) {
        self.partnerDeviceId = friend.friendId
        self.partnerName = friend.displayName
        self.partnerPhotoUri = friend.photoUri
        self.sessionStatus = .inviteSent
        self.sessionId = "ROOM-\(String(friend.friendId.suffix(4)))"
    }

    public func sendInvite(friendId: String, friendName: String) {
        let friend = EunoiaFriend(friendId: friendId, name: friendName)
        quickInvite(friend: friend)
    }

    public func receiveSimulatedInvite(inviterDeviceId: String, sessionId: String) {
        DispatchQueue.main.async {
            self.pendingInviteSender = inviterDeviceId
            self.sessionId = sessionId
            self.sessionStatus = .inviteReceived
            self.friendEventTimestamp = Date().timeIntervalSince1970
        }
    }

    public func acceptInvite(senderId: String, sessionId: String? = nil) {
        DispatchQueue.main.async {
            self.pendingInviteSender = nil
            self.partnerDeviceId = senderId
            self.partnerName = EunoiaFriendManager.shared.friends.first(where: { $0.friendId.caseInsensitiveCompare(senderId) == .orderedSame })?.displayName ?? "Friend (\(String(senderId.suffix(6))))"
            self.sessionStatus = .connected
            if let sid = sessionId, !sid.isEmpty {
                self.sessionId = sid
            }
            self.friendEventTimestamp = Date().timeIntervalSince1970
        }
    }

    public func rejectInvite(senderId: String) {
        DispatchQueue.main.async {
            if self.pendingInviteSender == senderId {
                self.pendingInviteSender = nil
                self.sessionStatus = .idle
            }
            self.friendEventTimestamp = Date().timeIntervalSince1970
        }
    }

    public func endSession() {
        DispatchQueue.main.async {
            self.sessionStatus = .idle
            self.partnerDeviceId = nil
            self.partnerName = nil
            self.partnerPhotoUri = nil
            self.sessionId = nil
            self.friendEventTimestamp = Date().timeIntervalSince1970
        }
    }
}
