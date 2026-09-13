import Foundation
import UIKit
import Combine

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
    @Published public var myDeviceId: String = FcmTokenManager.shared.deviceId
    @Published public var partnerDeviceId: String? = nil
    @Published public var partnerName: String? = nil
    @Published public var partnerPhotoUri: String? = nil
    @Published public var sessionId: String? = nil
    @Published public var pendingInviteSender: String? = nil
    @Published public var friendEventTimestamp: TimeInterval = Date().timeIntervalSince1970
    @Published public var flyingReactions: [String] = []

    private var isPolling = false
    private var processedSignalIds = Set<String>()
    private var pollingTimer: Timer? = nil

    private init() {
        let syncedId = FcmTokenManager.shared.deviceId
        self.myDeviceId = syncedId.isEmpty ? "SPASIBA-000000" : syncedId

        startSignalPolling()
    }

    public static func normalizeDeviceId(_ raw: String) -> String {
        var trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("@") {
            trimmed = String(trimmed.dropFirst()).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if !trimmed.uppercased().hasPrefix("SPASIBA-") {
            trimmed = "SPASIBA-\(trimmed)"
        }
        return trimmed.uppercased()
    }

    public var myDisplayName: String {
        let savedName = UserDefaults.standard.string(forKey: "username")?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let name = savedName, !name.isEmpty, name != "Spasiba Listener", !name.hasPrefix("SPASIBA-") {
            return name
        }
        return UIDevice.current.name.isEmpty ? "Spasiba Listener" : UIDevice.current.name
    }

    // MARK: - HTTP Signal Dispatch
    public func sendHttpSignal(targetId: String, type: String, payload: [String: Any]) {
        let target = Self.normalizeDeviceId(targetId)
        guard !target.isEmpty else { return }

        var fullPayload = payload
        fullPayload["type"] = type
        fullPayload["senderId"] = myDeviceId
        fullPayload["senderName"] = myDisplayName

        let bodyJson: [String: Any] = [
            "targetId": target,
            "fromId": myDeviceId,
            "sessionId": sessionId ?? "",
            "payload": fullPayload
        ]

        guard let url = URL(string: "\(FcmTokenManager.API_BASE_URL)/api/eunoia-signal"),
              let bodyData = try? JSONSerialization.data(withJSONObject: bodyJson, options: []) else {
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = bodyData

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                print("[EunoiaSessionManager] sendHttpSignal '\(type)' failed: \(error.localizedDescription)")
            } else {
                print("[EunoiaSessionManager] sendHttpSignal '\(type)' to \(target) dispatched successfully")
            }
        }.resume()
    }

    // MARK: - Invites & Session Lifecycle
    public func sendInvite(friendId: String, friendName: String) {
        let target = Self.normalizeDeviceId(friendId)
        guard !target.isEmpty else { return }

        let sessId = "ROOM-\(UUID().uuidString.prefix(6).uppercased())"
        DispatchQueue.main.async {
            self.sessionStatus = .inviteSent
            self.partnerDeviceId = target
            let resolvedName = friendName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? (EunoiaFriendManager.shared.friends.first(where: { $0.friendId.caseInsensitiveCompare(target) == .orderedSame })?.displayName ?? "Friend (\(String(target.suffix(6))))")
                : friendName
            self.partnerName = resolvedName
            self.sessionId = sessId
            self.friendEventTimestamp = Date().timeIntervalSince1970
        }

        let myName = myDisplayName
        let currentSong = NativePlayerManager.shared.currentSong
        let positionMs = NativePlayerManager.shared.positionMs
        let isPlaying = NativePlayerManager.shared.isPlaying

        // 1. Direct Real-Time HTTP Signal to Partner (Immediate Delivery)
        let inviteSignalPayload: [String: Any] = [
            "type": "EUNOIA_INVITE",
            "inviterName": myName,
            "senderName": myName,
            "sessionId": sessId,
            "songTitle": currentSong?.title ?? "Music Track"
        ]
        sendHttpSignal(targetId: target, type: "EUNOIA_INVITE", payload: inviteSignalPayload)

        // 2. Server-side Push / DB Invite (/api/eunoia/invite)
        var inviteBody: [String: Any] = [
            "inviterDeviceId": myDeviceId,
            "inviteeDeviceId": target,
            "inviterName": myName,
            "positionMs": positionMs,
            "isPlaying": isPlaying
        ]
        if let song = currentSong {
            inviteBody["currentSong"] = [
                "id": song.id,
                "title": song.title,
                "author": song.author,
                "thumbnail": song.thumbnail,
                "durationSeconds": song.durationSeconds
            ]
        }

        guard let inviteUrl = URL(string: "\(FcmTokenManager.API_BASE_URL)/api/eunoia/invite"),
              let inviteData = try? JSONSerialization.data(withJSONObject: inviteBody, options: []) else {
            return
        }

        var inviteReq = URLRequest(url: inviteUrl)
        inviteReq.httpMethod = "POST"
        inviteReq.setValue("application/json", forHTTPHeaderField: "Content-Type")
        inviteReq.httpBody = inviteData

        URLSession.shared.dataTask(with: inviteReq) { [weak self] data, response, error in
            if let error = error {
                print("[EunoiaSessionManager] sendInvite API error: \(error.localizedDescription)")
            } else if let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let createdSessId = json["sessionId"] as? String, !createdSessId.isEmpty {
                DispatchQueue.main.async {
                    self?.sessionId = createdSessId
                }
                print("[EunoiaSessionManager] sendInvite API success: sessionId=\(createdSessId)")
            }
        }.resume()
    }

    public func quickInvite(friend: EunoiaFriend) {
        sendInvite(friendId: friend.friendId, friendName: friend.displayName)
    }

    public func receiveSimulatedInvite(inviterDeviceId: String, sessionId: String) {
        let target = Self.normalizeDeviceId(inviterDeviceId)
        DispatchQueue.main.async {
            self.pendingInviteSender = target
            self.sessionId = sessionId
            self.sessionStatus = .inviteReceived
            self.friendEventTimestamp = Date().timeIntervalSince1970
        }
    }

    public func acceptInvite(senderId: String, sessionId: String? = nil) {
        let target = Self.normalizeDeviceId(senderId)
        let sid = sessionId ?? self.sessionId ?? "ROOM-\(UUID().uuidString.prefix(6).uppercased())"

        DispatchQueue.main.async {
            self.pendingInviteSender = nil
            self.partnerDeviceId = target
            self.partnerName = EunoiaFriendManager.shared.friends.first(where: { $0.friendId.caseInsensitiveCompare(target) == .orderedSame })?.displayName ?? "Friend (\(String(target.suffix(6))))"
            self.sessionStatus = .connected
            self.sessionId = sid
            self.friendEventTimestamp = Date().timeIntervalSince1970
        }

        let acceptPayload: [String: Any] = [
            "type": "ACCEPT",
            "senderId": myDeviceId,
            "senderName": myDisplayName,
            "sessionId": sid
        ]
        sendHttpSignal(targetId: target, type: "ACCEPT", payload: acceptPayload)
    }

    public func rejectInvite(senderId: String) {
        let target = Self.normalizeDeviceId(senderId)
        DispatchQueue.main.async {
            if self.pendingInviteSender == senderId || self.pendingInviteSender == target {
                self.pendingInviteSender = nil
                self.sessionStatus = .idle
            }
            self.friendEventTimestamp = Date().timeIntervalSince1970
        }

        let rejectPayload: [String: Any] = [
            "type": "REJECT_BUSY",
            "senderId": myDeviceId,
            "senderName": myDisplayName
        ]
        sendHttpSignal(targetId: target, type: "REJECT_BUSY", payload: rejectPayload)
    }

    public func acceptInvite() {
        if let pending = pendingInviteSender, !pending.isEmpty {
            acceptInvite(senderId: pending, sessionId: sessionId)
        }
    }

    public func rejectInvite() {
        if let pending = pendingInviteSender, !pending.isEmpty {
            rejectInvite(senderId: pending)
        } else {
            dismissInviteLocally()
        }
    }

    public func dismissInviteLocally() {
        self.pendingInviteSender = nil
        if self.sessionStatus == .inviteReceived {
            self.sessionStatus = .idle
        }
        self.friendEventTimestamp = Date().timeIntervalSince1970
    }

    public func endSession() {
        if let partner = partnerDeviceId {
            let endPayload: [String: Any] = [
                "type": "END_SESSION",
                "senderId": myDeviceId
            ]
            sendHttpSignal(targetId: partner, type: "END_SESSION", payload: endPayload)
        }

        DispatchQueue.main.async {
            self.sessionStatus = .idle
            self.partnerDeviceId = nil
            self.partnerName = nil
            self.partnerPhotoUri = nil
            self.sessionId = nil
            self.friendEventTimestamp = Date().timeIntervalSince1970
        }
    }

    // MARK: - Friend Request Signals
    public func sendFriendRequestSignal(friendId: String, friendName: String) {
        sendFriendRequestSignal(friendCode: friendId, friendName: friendName)
    }

    public func sendFriendRequestSignal(friendCode: String, friendName: String) {
        let target = Self.normalizeDeviceId(friendCode)
        guard !target.isEmpty else { return }

        EunoiaFriendManager.shared.addOrSendFriendRequest(
            friendId: target,
            name: friendName,
            isOutgoing: true
        )
        self.friendEventTimestamp = Date().timeIntervalSince1970

        let payload: [String: Any] = [
            "type": "FRIEND_REQUEST",
            "senderId": myDeviceId,
            "senderName": myDisplayName
        ]
        sendHttpSignal(targetId: target, type: "FRIEND_REQUEST", payload: payload)
    }

    public func acceptFriendRequestSignal(friendId: String, friendName: String) {
        let target = Self.normalizeDeviceId(friendId)
        EunoiaFriendManager.shared.acceptFriendRequest(friendId: target, newName: friendName)
        self.friendEventTimestamp = Date().timeIntervalSince1970

        let payload: [String: Any] = [
            "type": "FRIEND_ACCEPT",
            "senderId": myDeviceId,
            "senderName": myDisplayName
        ]
        sendHttpSignal(targetId: target, type: "FRIEND_ACCEPT", payload: payload)
    }

    public func removeFriendSignal(friendId: String) {
        let target = Self.normalizeDeviceId(friendId)
        EunoiaFriendManager.shared.removeFriend(friendId: target)
        self.friendEventTimestamp = Date().timeIntervalSince1970

        let payload: [String: Any] = [
            "type": "FRIEND_REMOVE",
            "senderId": myDeviceId
        ]
        sendHttpSignal(targetId: target, type: "FRIEND_REMOVE", payload: payload)
    }

    public func sendFriendChatMessageSignal(_ friendId: String, _ text: String) {
        let target = Self.normalizeDeviceId(friendId)
        let myName = myDisplayName

        EunoiaFriendManager.shared.addChatMessage(
            friendId: target,
            senderId: myDeviceId,
            senderName: myName,
            text: text,
            isPartner: false
        )
        self.friendEventTimestamp = Date().timeIntervalSince1970

        let payload: [String: Any] = [
            "type": "FRIEND_CHAT_MESSAGE",
            "senderId": myDeviceId,
            "senderName": myName,
            "text": text,
            "timestamp": Int64(Date().timeIntervalSince1970 * 1000)
        ]
        sendHttpSignal(targetId: target, type: "FRIEND_CHAT_MESSAGE", payload: payload)
    }

    public func deleteFriendChatHistorySignal(_ friendId: String) {
        let target = Self.normalizeDeviceId(friendId)
        EunoiaFriendManager.shared.clearChatHistory(friendId: target)
        self.friendEventTimestamp = Date().timeIntervalSince1970
    }

    // MARK: - Real-Time Background Signal Poller
    public func startSignalPolling() {
        guard !isPolling else { return }
        isPolling = true

        DispatchQueue.main.async {
            self.pollingTimer?.invalidate()
            self.pollingTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] _ in
                self?.pollSignalsOnce()
            }
        }
        pollSignalsOnce()
    }

    private func pollSignalsOnce() {
        let myId = myDeviceId.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !myId.isEmpty else { return }

        guard let url = URL(string: "\(FcmTokenManager.API_BASE_URL)/api/eunoia-signal?deviceId=\(myId)") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 8

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            guard let self = self,
                  let httpResponse = response as? HTTPURLResponse,
                  (200...299).contains(httpResponse.statusCode),
                  let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let signals = json["signals"] as? [[String: Any]] else {
                return
            }

            for sig in signals {
                let from = (sig["fromId"] as? String ?? "").uppercased()
                if from == myId { continue }

                let payload = sig["payload"] as? [String: Any] ?? [:]
                let type = payload["type"] as? String ?? sig["type"] as? String ?? ""

                let sigId = (sig["id"] as? String) ?? (sig["_id"] as? String) ?? "\(from)_\(sig["createdAt"] ?? "")_\(type)_\(payload["timestamp"] ?? "")"
                if self.processedSignalIds.contains(sigId) { continue }
                self.processedSignalIds.insert(sigId)
                if self.processedSignalIds.count > 500 {
                    self.processedSignalIds.removeAll()
                }

                DispatchQueue.main.async {
                    self.handleIncomingSignal(type: type, from: from, payload: payload)
                }
            }
        }.resume()
    }

    private func handleIncomingSignal(type: String, from: String, payload: [String: Any]) {
        print("[EunoiaSessionManager] Incoming signal '\(type)' from \(from)")
        switch type {
        case "EUNOIA_INVITE":
            let sessId = payload["sessionId"] as? String ?? "ROOM-\(String(from.suffix(4)))"
            let inviterName = (payload["inviterName"] as? String) ?? (payload["senderName"] as? String) ?? "Friend (\(String(from.suffix(6))))"
            let songTitle = (payload["songTitle"] as? String) ?? "Music Track"

            self.partnerName = inviterName
            self.receiveSimulatedInvite(inviterDeviceId: from, sessionId: sessId)

            SpasibaFcmService.shared.showInviteNotification(
                sessionId: sessId,
                inviterDeviceId: from,
                inviterName: inviterName,
                songTitle: songTitle
            )

        case "ACCEPT":
            let sessId = payload["sessionId"] as? String
            self.acceptInvite(senderId: from, sessionId: sessId)

        case "REJECT", "REJECT_BUSY":
            if self.partnerDeviceId == from {
                self.sessionStatus = .rejectedBusy
                self.friendEventTimestamp = Date().timeIntervalSince1970
            }

        case "END_SESSION":
            if self.partnerDeviceId == from {
                self.sessionStatus = .idle
                self.partnerDeviceId = nil
                self.sessionId = nil
                self.friendEventTimestamp = Date().timeIntervalSince1970
            }

        case "FRIEND_REQUEST":
            let senderName = (payload["senderName"] as? String) ?? (payload["inviterName"] as? String) ?? "Friend (\(String(from.suffix(6))))"
            let photoUri = payload["photoUri"] as? String
            EunoiaFriendManager.shared.addOrSendFriendRequest(
                friendId: from,
                name: senderName,
                isOutgoing: false,
                photoUri: photoUri
            )
            self.friendEventTimestamp = Date().timeIntervalSince1970

        case "FRIEND_ACCEPT":
            let senderName = (payload["senderName"] as? String) ?? ""
            EunoiaFriendManager.shared.acceptFriendRequest(friendId: from, newName: senderName)
            self.friendEventTimestamp = Date().timeIntervalSince1970

        case "FRIEND_REMOVE":
            EunoiaFriendManager.shared.removeFriend(friendId: from)
            self.friendEventTimestamp = Date().timeIntervalSince1970

        case "FRIEND_CHAT_MESSAGE":
            let senderName = (payload["senderName"] as? String) ?? "Friend"
            let text = (payload["text"] as? String) ?? ""
            if !text.isEmpty {
                EunoiaFriendManager.shared.addChatMessage(
                    friendId: from,
                    senderId: from,
                    senderName: senderName,
                    text: text,
                    isPartner: true
                )
                self.friendEventTimestamp = Date().timeIntervalSince1970
            }

        case "reaction":
            if let emoji = payload["emoji"] as? String, !emoji.isEmpty {
                self.flyingReactions.append(emoji)
            }

        default:
            break
        }
    }
}
