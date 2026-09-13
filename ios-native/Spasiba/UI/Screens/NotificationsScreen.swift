import SwiftUI

public struct EunoiaRequestItem: Identifiable, Equatable {
    public let id: String
    public let senderName: String
    public let avatarUrl: String
    public let message: String
    public let timeAgo: String
    public let type: String // "FRIEND", "ROOM"
    public var isNew: Bool
    public var actionState: String? // "ACCEPTED", "DECLINED", "JOINED"

    public init(
        id: String,
        senderName: String,
        avatarUrl: String = "",
        message: String,
        timeAgo: String = "Just now",
        type: String,
        isNew: Bool = false,
        actionState: String? = nil
    ) {
        self.id = id
        self.senderName = senderName
        self.avatarUrl = avatarUrl
        self.message = message
        self.timeAgo = timeAgo
        self.type = type
        self.isNew = isNew
        self.actionState = actionState
    }
}

public enum NotificationState {
    private static let dismissedKey = "spasiba_dismissed_notification_ids"
    private static let lastSeenKey = "last_notifications_seen_timestamp"

    public static func getDismissedIds() -> Set<String> {
        let array = UserDefaults.standard.stringArray(forKey: dismissedKey) ?? []
        return Set(array)
    }

    public static func dismissNotification(id: String) {
        var set = getDismissedIds()
        set.insert(id)
        UserDefaults.standard.set(Array(set), forKey: dismissedKey)
    }

    public static func clearAllNotifications(ids: [String]) {
        var set = getDismissedIds()
        set.formUnion(ids)
        UserDefaults.standard.set(Array(set), forKey: dismissedKey)
        markAllAsSeen()
    }

    public static func markAllAsSeen() {
        UserDefaults.standard.set(Date().timeIntervalSince1970 * 1000, forKey: lastSeenKey)
    }

    public static func getLastSeenTime() -> Double {
        return UserDefaults.standard.double(forKey: lastSeenKey)
    }
}

public struct NotificationsScreen: View {
    public var onBackClick: () -> Void
    public var onNavigateToRoom: (String) -> Void
    public var onNavigateToEunoia: () -> Void
    public var onProfileClick: () -> Void

    @ObservedObject var friendManager = EunoiaFriendManager.shared
    @ObservedObject var sessionManager = EunoiaSessionManager.shared

    @State private var notifications: [EunoiaRequestItem] = []
    @State private var userPhotoUri: String? = nil

    public init(
        onBackClick: @escaping () -> Void = {},
        onNavigateToRoom: @escaping (String) -> Void = { _ in },
        onNavigateToEunoia: @escaping () -> Void = {},
        onProfileClick: @escaping () -> Void = {}
    ) {
        self.onBackClick = onBackClick
        self.onNavigateToRoom = onNavigateToRoom
        self.onNavigateToEunoia = onNavigateToEunoia
        self.onProfileClick = onProfileClick
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 0) {
                // Top Activity Header
                topHeaderSection

                // Content
                if notifications.isEmpty {
                    emptyStateView
                } else {
                    notificationsListView
                }
            }
        }
        .onAppear {
            NotificationState.markAllAsSeen()
            refreshNotifications()
        }
        .onChange(of: friendManager.friends) { _ in
            refreshNotifications()
        }
        .onChange(of: sessionManager.sessionStatus) { _ in
            refreshNotifications()
        }
    }

    // MARK: - Subviews

    private var topHeaderSection: some View {
        HStack(alignment: .center) {
            Button(action: onBackClick) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
            }

            Text("Notifications")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.white)

            Spacer()

            if !notifications.isEmpty {
                Button(action: {
                    let ids = notifications.map { $0.id }
                    NotificationState.clearAllNotifications(ids: ids)
                    for item in notifications where item.type == "FRIEND" {
                        friendManager.removeFriend(friendId: item.id)
                    }
                    notifications.removeAll()
                    sessionManager.dismissInviteLocally()
                }) {
                    Text("Clear All")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(SpasibaTheme.accent)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                }
            }

            Button(action: onProfileClick) {
                if let photo = userPhotoUri, !photo.isEmpty {
                    AsyncImage(url: URL(string: photo)) { phase in
                        if let img = phase.image {
                            img.resizable().aspectRatio(contentMode: .fill)
                        } else {
                            Color.white.opacity(0.15)
                        }
                    }
                    .frame(width: 40, height: 40)
                    .clipShape(Circle())
                } else {
                    Image(systemName: "person.circle.fill")
                        .font(.system(size: 28))
                        .foregroundColor(.white)
                        .frame(width: 40, height: 40)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .padding(.bottom, 8)
    }

    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Spacer()

            ZStack {
                Circle()
                    .fill(Color(hex: 0x18181A))
                    .frame(width: 80, height: 80)

                Image(systemName: "bell.fill")
                    .font(.system(size: 36))
                    .foregroundColor(SpasibaTheme.accent)
            }

            Text("No new notifications")
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(.white)

            Text("You're all caught up! When friends send requests or invite you to listening rooms, they will appear here.")
                .font(.system(size: 14))
                .foregroundColor(SpasibaTheme.textMuted)
                .multilineTextAlignment(.center)
                .lineSpacing(4)
                .padding(.horizontal, 36)

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var notificationsListView: some View {
        ScrollView(.vertical, showsIndicators: false) {
            LazyVStack(spacing: 12) {
                // List Header
                HStack {
                    Text("New Activity & Invites")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)

                    Spacer()

                    Text("\(notifications.count)")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(SpasibaTheme.accent)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(Color.white.opacity(0.15))
                        .clipShape(Capsule())
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)

                ForEach(notifications) { item in
                    notificationCard(item: item)
                }
            }
            .padding(.bottom, 120)
        }
    }

    private func notificationCard(item: EunoiaRequestItem) -> some View {
        HStack(alignment: .top, spacing: 14) {
            // Avatar / Icon
            if !item.avatarUrl.isEmpty {
                AsyncImage(url: URL(string: item.avatarUrl)) { phase in
                    if let img = phase.image {
                        img.resizable().aspectRatio(contentMode: .fill)
                    } else {
                        Color.white.opacity(0.15)
                    }
                }
                .frame(width: 48, height: 48)
                .clipShape(Circle())
            } else {
                ZStack {
                    Circle()
                        .fill(item.type == "ROOM" ? Color(hex: 0xEC4899) : Color.white)
                        .frame(width: 48, height: 48)

                    Image(systemName: item.type == "ROOM" ? "headphones" : "person.badge.plus")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(item.type == "ROOM" ? .white : .black)
                }
            }

            // Text and actions
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("\(Text(item.senderName).bold().foregroundColor(.white)) \(Text(item.message).foregroundColor(.white))")
                        .font(.system(size: 14))
                        .lineSpacing(3)

                    Spacer()

                    if item.isNew && item.actionState == nil {
                        Text("NEW")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(.black)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.white)
                            .clipShape(Capsule())
                    }
                }

                Text(item.timeAgo)
                    .font(.system(size: 12))
                    .foregroundColor(SpasibaTheme.textMuted)

                // Actions
                if item.actionState == "ACCEPTED" {
                    Text("✓ Friend Request Accepted")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(Color(hex: 0x22D3EE))
                        .padding(.top, 4)
                } else if item.actionState == "DECLINED" {
                    Text("Request Declined")
                        .font(.system(size: 13))
                        .foregroundColor(SpasibaTheme.textMuted)
                        .padding(.top, 4)
                } else if item.type == "FRIEND" {
                    HStack(spacing: 10) {
                        Button(action: {
                            updateActionState(id: item.id, state: "ACCEPTED")
                            sessionManager.acceptFriendRequestSignal(friendId: item.id, friendName: item.senderName)
                            NotificationState.dismissNotification(id: item.id)
                            onNavigateToEunoia()
                        }) {
                            Text("Accept")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.black)
                                .padding(.horizontal, 16)
                                .frame(height: 34)
                                .background(Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: 16))
                        }

                        Button(action: {
                            updateActionState(id: item.id, state: "DECLINED")
                            sessionManager.removeFriendSignal(friendId: item.id)
                            NotificationState.dismissNotification(id: item.id)
                            notifications.removeAll(where: { $0.id == item.id })
                        }) {
                            Text("Decline")
                                .font(.system(size: 12))
                                .foregroundColor(.white)
                                .padding(.horizontal, 16)
                                .frame(height: 34)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 16)
                                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                                )
                        }
                    }
                    .padding(.top, 4)
                } else {
                    Button(action: {
                        updateActionState(id: item.id, state: "JOINED")
                        NotificationState.dismissNotification(id: item.id)
                        sessionManager.acceptInvite()
                        onNavigateToEunoia()
                    }) {
                        Text("Join Room")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)
                            .frame(height: 34)
                            .background(Color(hex: 0xEC4899))
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                    }
                    .padding(.top, 4)
                }
            }

            // Dismiss X Button
            Button(action: {
                NotificationState.dismissNotification(id: item.id)
                if item.type == "FRIEND" {
                    friendManager.removeFriend(friendId: item.id)
                } else {
                    sessionManager.dismissInviteLocally()
                }
                notifications.removeAll(where: { $0.id == item.id })
            }) {
                Image(systemName: "xmark")
                    .font(.system(size: 14))
                    .foregroundColor(SpasibaTheme.textMuted)
                    .frame(width: 28, height: 28)
            }
        }
        .padding(14)
        .background(Color.white.opacity(0.04))
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
        .padding(.horizontal, 16)
    }

    private func updateActionState(id: String, state: String) {
        if let idx = notifications.firstIndex(where: { $0.id == id }) {
            notifications[idx].actionState = state
        }
    }

    private func refreshNotifications() {
        let dismissed = NotificationState.getDismissedIds()
        let lastSeenTime = NotificationState.getLastSeenTime()

        var items: [EunoiaRequestItem] = []

        // Friend requests
        let pendingFriends = friendManager.getPendingRequests()
            .filter { $0.status == .pendingIncoming && !dismissed.contains($0.friendId) }
            .map { f in
                EunoiaRequestItem(
                    id: f.friendId,
                    senderName: f.name,
                    avatarUrl: f.photoUri ?? "",
                    message: "sent you a friend request on Eunoia",
                    timeAgo: "Just now",
                    type: "FRIEND",
                    isNew: Double(f.addedAtMs) > lastSeenTime || lastSeenTime == 0
                )
            }
        items.append(contentsOf: pendingFriends)

        // Room invites
        let currentSender = sessionManager.pendingInviteSender
        let currentSessionId = sessionManager.sessionId ?? currentSender ?? "active"
        let inviteId = "room_invite_\(currentSessionId)"

        if (sessionManager.sessionStatus == .inviteReceived || currentSender != nil) && !dismissed.contains(inviteId) {
            let partnerFriend = currentSender != nil ? friendManager.friends.first(where: { $0.friendId.caseInsensitiveCompare(currentSender!) == .orderedSame }) : nil
            let senderName = partnerFriend?.displayName ?? sessionManager.partnerName ?? currentSender ?? "Partner"

            items.append(
                EunoiaRequestItem(
                    id: inviteId,
                    senderName: senderName,
                    avatarUrl: partnerFriend?.photoUri ?? "",
                    message: "invited you to a listening room on Eunoia",
                    timeAgo: "Just now",
                    type: "ROOM",
                    isNew: true
                )
            )
        }

        self.notifications = items
    }
}
