import Foundation

public enum EunoiaFriendStatus: String, Codable {
    case accepted = "ACCEPTED"
    case pendingOutgoing = "PENDING_OUTGOING"
    case pendingIncoming = "PENDING_INCOMING"
}

public struct EunoiaFriend: Identifiable, Codable, Equatable {
    public var id: String { friendId }
    public let friendId: String
    public var name: String
    public var status: EunoiaFriendStatus
    public let addedAtMs: Int64
    public var photoUri: String?

    public init(
        friendId: String,
        name: String,
        status: EunoiaFriendStatus = .accepted,
        addedAtMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        photoUri: String? = nil
    ) {
        self.friendId = friendId
        self.name = name
        self.status = status
        self.addedAtMs = addedAtMs
        self.photoUri = photoUri
    }

    public var displayName: String {
        if !name.trimmingCharacters(in: .whitespaces).isEmpty &&
            !name.starts(with: "SPASIBA-") &&
            !name.starts(with: "Friend (") {
            return name
        }
        let suffix = friendId.count > 6 ? String(friendId.suffix(6)) : friendId
        return "Friend (\(suffix))"
    }
}

public struct EunoiaFriendChatMessage: Identifiable, Codable, Equatable {
    public let id: String
    public let friendId: String
    public let senderId: String
    public let senderName: String
    public let isPartner: Bool
    public let messageText: String
    public let timestampMs: Int64
    public let replyToText: String?
    public let replyToSender: String?
    public let mediaUri: String?
    public let isVoiceNote: Bool
    public let voiceDurationSec: Int

    public init(
        id: String = UUID().uuidString,
        friendId: String,
        senderId: String,
        senderName: String,
        isPartner: Bool,
        messageText: String,
        timestampMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        replyToText: String? = nil,
        replyToSender: String? = nil,
        mediaUri: String? = nil,
        isVoiceNote: Bool = false,
        voiceDurationSec: Int = 0
    ) {
        self.id = id
        self.friendId = friendId
        self.senderId = senderId
        self.senderName = senderName
        self.isPartner = isPartner
        self.messageText = messageText
        self.timestampMs = timestampMs
        self.replyToText = replyToText
        self.replyToSender = replyToSender
        self.mediaUri = mediaUri
        self.isVoiceNote = isVoiceNote
        self.voiceDurationSec = voiceDurationSec
    }
}

public final class EunoiaFriendManager: ObservableObject {
    public static let shared = EunoiaFriendManager()

    private let userDefaults = UserDefaults.standard
    private let friendsKey = "spasiba_friends_v2"

    @Published public private(set) var friends: [EunoiaFriend] = []

    private init() {
        loadFriends()
    }

    private func loadFriends() {
        if let data = userDefaults.data(forKey: friendsKey),
           let decoded = try? JSONDecoder().decode([EunoiaFriend].self, from: data) {
            self.friends = decoded
        }
    }

    public func getFriends(statusFilter: EunoiaFriendStatus? = nil) -> [EunoiaFriend] {
        if let filter = statusFilter {
            return friends.filter { $0.status == filter }
        }
        return friends
    }

    public func getAcceptedFriends() -> [EunoiaFriend] {
        return friends.filter { $0.status == .accepted }
    }

    public func getPendingRequests() -> [EunoiaFriend] {
        return friends.filter { $0.status == .pendingOutgoing || $0.status == .pendingIncoming }
    }

    public func addFriend(friendId: String, name: String) -> Bool {
        return addOrSendFriendRequest(friendId: friendId, name: name, isOutgoing: true)
    }

    @discardableResult
    public func addOrSendFriendRequest(
        friendId: String,
        name: String,
        isOutgoing: Bool = true,
        photoUri: String? = nil
    ) -> Bool {
        let trimmedId = friendId.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !trimmedId.isEmpty else { return false }

        let displayId: String
        if !trimmedId.lowercased().starts(with: "spasiba-") && trimmedId.count <= 8 {
            displayId = "SPASIBA-\(trimmedId)"
        } else {
            displayId = trimmedId
        }

        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedName = cleanName.isEmpty ? "Friend (\(String(displayId.suffix(6))))" : cleanName
        let status: EunoiaFriendStatus = isOutgoing ? .pendingOutgoing : .pendingIncoming

        var current = friends
        let existing = current.first(where: { $0.friendId.caseInsensitiveCompare(displayId) == .orderedSame })
        let finalPhoto = photoUri ?? existing?.photoUri
        current.removeAll(where: { $0.friendId.caseInsensitiveCompare(displayId) == .orderedSame })
        current.insert(EunoiaFriend(friendId: displayId, name: trimmedName, status: status, photoUri: finalPhoto), at: 0)

        saveFriends(current)
        return true
    }

    @discardableResult
    public func acceptFriendRequest(friendId: String, newName: String? = nil, photoUri: String? = nil) -> Bool {
        guard let index = friends.firstIndex(where: { $0.friendId.caseInsensitiveCompare(friendId.trimmingCharacters(in: .whitespaces)) == .orderedSame }) else {
            return false
        }

        var updated = friends
        let existing = updated[index]
        let cleanName: String
        if let newName = newName?.trimmingCharacters(in: .whitespaces),
           !newName.isEmpty, !newName.starts(with: "SPASIBA-"), !newName.starts(with: "Friend (") {
            cleanName = newName
        } else {
            cleanName = existing.name
        }

        let finalPhoto = photoUri ?? existing.photoUri
        updated[index] = EunoiaFriend(
            friendId: existing.friendId,
            name: cleanName,
            status: .accepted,
            addedAtMs: existing.addedAtMs,
            photoUri: finalPhoto
        )

        saveFriends(updated)
        return true
    }

    @discardableResult
    public func updateFriendName(friendId: String, newName: String) -> Bool {
        let trimmedName = newName.trimmingCharacters(in: .whitespaces)
        guard !trimmedName.isEmpty, !trimmedName.starts(with: "SPASIBA-"), !trimmedName.starts(with: "Friend (") else {
            return false
        }

        guard let index = friends.firstIndex(where: { $0.friendId.caseInsensitiveCompare(friendId.trimmingCharacters(in: .whitespaces)) == .orderedSame }) else {
            return false
        }

        var updated = friends
        var f = updated[index]
        f.name = trimmedName
        updated[index] = f
        saveFriends(updated)
        return true
    }

    @discardableResult
    public func updateFriendPhoto(friendId: String, newPhotoUri: String) -> Bool {
        guard !newPhotoUri.trimmingCharacters(in: .whitespaces).isEmpty else { return false }
        guard let index = friends.firstIndex(where: { $0.friendId.caseInsensitiveCompare(friendId.trimmingCharacters(in: .whitespaces)) == .orderedSame }) else {
            return false
        }

        var updated = friends
        var f = updated[index]
        f.photoUri = newPhotoUri
        updated[index] = f
        saveFriends(updated)
        return true
    }

    @discardableResult
    public func removeFriend(friendId: String) -> Bool {
        let beforeCount = friends.count
        var updated = friends
        updated.removeAll(where: { $0.friendId.caseInsensitiveCompare(friendId.trimmingCharacters(in: .whitespaces)) == .orderedSame })
        if updated.count != beforeCount {
            saveFriends(updated)
            return true
        }
        return false
    }

    private func saveFriends(_ newFriends: [EunoiaFriend]) {
        self.friends = newFriends
        if let encoded = try? JSONEncoder().encode(newFriends) {
            userDefaults.set(encoded, forKey: friendsKey)
        }
        EunoiaSessionManager.shared.friendEventTimestamp = Date().timeIntervalSince1970
    }

    // MARK: - 7-Day Chat History Persistence

    public func getChatHistory(friendId: String, days: Int = 7) -> [EunoiaFriendChatMessage] {
        let key = "chat_history_\(friendId.trimmingCharacters(in: .whitespaces).uppercased())"
        guard let data = userDefaults.data(forKey: key),
              let messages = try? JSONDecoder().decode([EunoiaFriendChatMessage].self, from: data) else {
            return []
        }

        let cutoffMs = Int64((Date().timeIntervalSince1970 - Double(days * 86400)) * 1000)
        return messages.filter { $0.timestampMs >= cutoffMs }.sorted(by: { $0.timestampMs < $1.timestampMs })
    }

    @discardableResult
    public func addChatMessage(
        friendId: String,
        senderId: String,
        senderName: String,
        text: String,
        isPartner: Bool,
        replyToText: String? = nil,
        replyToSender: String? = nil,
        mediaUri: String? = nil,
        isVoiceNote: Bool = false,
        voiceDurationSec: Int = 0
    ) -> EunoiaFriendChatMessage {
        let trimmedFriendId = friendId.trimmingCharacters(in: .whitespaces).uppercased()
        let msg = EunoiaFriendChatMessage(
            friendId: trimmedFriendId,
            senderId: senderId,
            senderName: senderName,
            isPartner: isPartner,
            messageText: text,
            replyToText: replyToText,
            replyToSender: replyToSender,
            mediaUri: mediaUri,
            isVoiceNote: isVoiceNote,
            voiceDurationSec: voiceDurationSec
        )

        var history = getChatHistory(friendId: trimmedFriendId, days: 7)
        history.append(msg)
        saveChatHistory(friendId: trimmedFriendId, messages: history)
        return msg
    }

    @discardableResult
    public func clearChatHistory(friendId: String) -> Bool {
        let key = "chat_history_\(friendId.trimmingCharacters(in: .whitespaces).uppercased())"
        userDefaults.removeObject(forKey: key)
        EunoiaSessionManager.shared.friendEventTimestamp = Date().timeIntervalSince1970
        return true
    }

    private func saveChatHistory(friendId: String, messages: [EunoiaFriendChatMessage]) {
        let key = "chat_history_\(friendId.trimmingCharacters(in: .whitespaces).uppercased())"
        if let encoded = try? JSONEncoder().encode(messages) {
            userDefaults.set(encoded, forKey: key)
        }
        EunoiaSessionManager.shared.friendEventTimestamp = Date().timeIntervalSince1970
    }
}
