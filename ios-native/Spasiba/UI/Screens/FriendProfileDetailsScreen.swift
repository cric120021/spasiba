import SwiftUI
import UIKit

public struct FriendProfileDetailsScreen: View {
    public let friendId: String
    public let friendName: String
    public var onBackClick: () -> Void
    public var onOpenChat: () -> Void
    public var onRemoveFriend: (String) -> Void
    public var onQuickInvite: (EunoiaFriend) -> Void

    @ObservedObject var friendManager = EunoiaFriendManager.shared
    @ObservedObject var sessionManager = EunoiaSessionManager.shared

    @State private var selectedTab: Int = 0 // 0 = Music, 1 = GIFs, 2 = Links/Docs
    @State private var isMuted: Bool = false
    @State private var isDeleteAllDialogOpen: Bool = false
    @State private var isRemoveFriendDialogOpen: Bool = false
    @State private var toastMessage: String? = nil

    public init(
        friendId: String,
        friendName: String,
        onBackClick: @escaping () -> Void = {},
        onOpenChat: @escaping () -> Void = {},
        onRemoveFriend: @escaping (String) -> Void = { _ in },
        onQuickInvite: @escaping (EunoiaFriend) -> Void = { _ in }
    ) {
        self.friendId = friendId
        self.friendName = friendName
        self.onBackClick = onBackClick
        self.onOpenChat = onOpenChat
        self.onRemoveFriend = onRemoveFriend
        self.onQuickInvite = onQuickInvite
    }

    private var matchedFriend: EunoiaFriend? {
        friendManager.friends.first(where: { $0.friendId.caseInsensitiveCompare(friendId.trimmingCharacters(in: .whitespaces)) == .orderedSame })
    }

    private var displayName: String {
        matchedFriend?.name ?? friendName
    }

    private var chatMessages: [EunoiaFriendChatMessage] {
        friendManager.getChatHistory(friendId: friendId, days: 7)
    }

    private var musicItems: [EunoiaFriendChatMessage] {
        chatMessages.filter { $0.messageText.contains("http") || $0.messageText.starts(with: "🎵") }
    }

    private var gifItems: [EunoiaFriendChatMessage] {
        chatMessages.filter { $0.messageText.contains("[GIF:") || $0.messageText.contains("[STICKER:") }
    }

    private var docItems: [EunoiaFriendChatMessage] {
        chatMessages.filter { $0.messageText.contains("www.") || $0.messageText.contains(".com") || $0.messageText.contains("doc") }
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                LazyVStack(spacing: 0) {
                    // Top Bar
                    topBarSection

                    // Big Profile Photo Section
                    profileHeaderSection

                    // Quick Actions Row (Chat, Quick Invite, Mute)
                    quickActionsSection

                    // Shared Media, Links & Docs Section
                    sharedMediaSection

                    // Management Options (Clear chat, Unfriend)
                    managementOptionsSection
                }
                .padding(.bottom, 60)
            }

            // Toast overlay
            if let toast = toastMessage {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 10)
                        .background(Color(hex: 0x202028))
                        .clipShape(Capsule())
                        .padding(.bottom, 60)
                }
                .transition(.opacity)
            }

            // Confirmation Dialogs
            if isDeleteAllDialogOpen {
                deleteAllDialogView
            }

            if isRemoveFriendDialogOpen {
                removeFriendDialogView
            }
        }
    }

    // MARK: - Sections

    private var topBarSection: some View {
        HStack {
            Button(action: onBackClick) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 38, height: 38)
            }

            Text("Contact Info")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)

            Spacer()
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
    }

    private var profileHeaderSection: some View {
        VStack(spacing: 8) {
            ZStack {
                Circle()
                    .fill(Color(hex: 0x22222E))
                    .frame(width: 110, height: 110)

                if let photo = matchedFriend?.photoUri, !photo.isEmpty {
                    AsyncImage(url: URL(string: photo)) { phase in
                        if let img = phase.image {
                            img.resizable().aspectRatio(contentMode: .fill)
                        } else {
                            Text(String(displayName.prefix(1)).uppercased())
                                .font(.system(size: 46, weight: .heavy))
                                .foregroundColor(.white)
                        }
                    }
                    .frame(width: 110, height: 110)
                    .clipShape(Circle())
                } else {
                    Text(String(displayName.prefix(1)).uppercased())
                        .font(.system(size: 46, weight: .heavy))
                        .foregroundColor(.white)
                }
            }
            .padding(.top, 8)

            Text(displayName)
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(SpasibaTheme.textStrong)

            Button(action: {
                UIPasteboard.general.string = friendId
                showToast("Copied ID: \(friendId)")
            }) {
                HStack(spacing: 6) {
                    Text(friendId)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(Color(hex: 0xCCCCCC))
                    Image(systemName: "doc.on.doc")
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: 0xCCCCCC))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 5)
                .background(Color.white.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
    }

    private var quickActionsSection: some View {
        HStack(spacing: 10) {
            // Chat
            Button(action: onOpenChat) {
                VStack(spacing: 4) {
                    Image(systemName: "bubble.left.fill")
                        .font(.system(size: 20))
                        .foregroundColor(.white)
                    Text("Chat")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Color(hex: 0x1E1E28))
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }

            // Quick Invite
            Button(action: {
                let friendObj = matchedFriend ?? EunoiaFriend(friendId: friendId, name: friendName)
                onQuickInvite(friendObj)
            }) {
                VStack(spacing: 4) {
                    Image(systemName: "bolt.fill")
                        .font(.system(size: 20))
                        .foregroundColor(.white)
                    Text("Invite")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Color(hex: 0x1E1E28))
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }

            // Mute
            Button(action: {
                isMuted.toggle()
                showToast(isMuted ? "Muted notifications for \(displayName)" : "Unmuted notifications")
            }) {
                VStack(spacing: 4) {
                    Image(systemName: isMuted ? "bell.slash.fill" : "bell.fill")
                        .font(.system(size: 20))
                        .foregroundColor(isMuted ? Color(hex: 0xF59E0B) : .white)
                    Text(isMuted ? "Muted" : "Mute")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Color(hex: 0x1E1E28))
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 8)
    }

    private var sharedMediaSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("SHARED MEDIA, LINKS & DOCUMENTS")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(Color(hex: 0xA0A0A0))
                .tracking(1.2)

            // Filter Tabs
            HStack(spacing: 8) {
                filterTabButton(title: "Music (\(musicItems.count))", index: 0)
                filterTabButton(title: "GIFs (\(gifItems.count))", index: 1)
                filterTabButton(title: "Links & Docs (\(docItems.count))", index: 2)
            }

            let activeList = selectedTab == 0 ? musicItems : (selectedTab == 1 ? gifItems : docItems)

            if activeList.isEmpty {
                VStack(spacing: 6) {
                    Text("No shared items in this category yet.\nItems sent over 7 days show up here!")
                        .font(.system(size: 12))
                        .foregroundColor(SpasibaTheme.textMuted)
                        .multilineTextAlignment(.center)
                        .padding(.vertical, 20)
                }
                .frame(maxWidth: .infinity)
            } else {
                VStack(spacing: 8) {
                    ForEach(Array(activeList.prefix(5)), id: \.id) { item in
                        HStack(spacing: 10) {
                            Image(systemName: selectedTab == 0 ? "music.note" : (selectedTab == 1 ? "photo" : "link"))
                                .font(.system(size: 16))
                                .foregroundColor(.white)
                                .frame(width: 20)

                            Text(item.messageText)
                                .font(.system(size: 13))
                                .foregroundColor(.white)
                                .lineLimit(1)

                            Spacer()
                        }
                        .padding(12)
                        .background(Color(hex: 0x22222C))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
            }
        }
        .padding(16)
        .background(Color(hex: 0x14141E))
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
        .padding(.horizontal, 16)
        .padding(.top, 16)
    }

    private func filterTabButton(title: String, index: Int) -> some View {
        Button(action: { selectedTab = index }) {
            Text(title)
                .font(.system(size: 11, weight: selectedTab == index ? .bold : .medium))
                .foregroundColor(selectedTab == index ? .black : .white)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(selectedTab == index ? Color.white : Color(hex: 0x1E1E2C))
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    private var managementOptionsSection: some View {
        VStack(spacing: 0) {
            // Clear Chat History
            Button(action: { isDeleteAllDialogOpen = true }) {
                HStack(spacing: 14) {
                    Image(systemName: "trash")
                        .font(.system(size: 18))
                        .foregroundColor(Color(hex: 0xEF4444))

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Clear Chat History")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(Color(hex: 0xEF4444))

                        Text("Deletes 7-day messages for both you and \(displayName)")
                            .font(.system(size: 11))
                            .foregroundColor(SpasibaTheme.textMuted)
                    }

                    Spacer()
                }
                .padding(16)
            }

            Divider().background(Color.white.opacity(0.06))

            // Unfriend
            Button(action: { isRemoveFriendDialogOpen = true }) {
                HStack(spacing: 14) {
                    Image(systemName: "person.badge.minus")
                        .font(.system(size: 18))
                        .foregroundColor(Color(hex: 0xEF4444))

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Unfriend \(displayName)")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(Color(hex: 0xEF4444))

                        Text("Remove connection from both devices")
                            .font(.system(size: 11))
                            .foregroundColor(SpasibaTheme.textMuted)
                    }

                    Spacer()
                }
                .padding(16)
            }
        }
        .background(Color(hex: 0x14141E))
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
        .padding(.horizontal, 16)
        .padding(.top, 16)
    }

    // MARK: - Dialogs

    private var deleteAllDialogView: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()

            VStack(alignment: .leading, spacing: 14) {
                Text("Delete Chat History?")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)

                Text("This will clear all 7-day chat messages for BOTH you and \(displayName).")
                    .font(.system(size: 14))
                    .foregroundColor(SpasibaTheme.textMuted)

                HStack {
                    Spacer()

                    Button("Cancel") {
                        isDeleteAllDialogOpen = false
                    }
                    .foregroundColor(SpasibaTheme.textMuted)
                    .padding(.horizontal, 14)

                    Button("Delete for Both") {
                        sessionManager.deleteFriendChatHistorySignal(friendId)
                        isDeleteAllDialogOpen = false
                        showToast("Chat history deleted for both!")
                    }
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color(hex: 0xEF4444))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .padding(.top, 8)
            }
            .padding(22)
            .background(Color(hex: 0x181824))
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .padding(.horizontal, 32)
        }
    }

    private var removeFriendDialogView: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()

            VStack(alignment: .leading, spacing: 14) {
                Text("Remove \(displayName)?")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)

                Text("Are you sure you want to remove \(displayName)? Both of you will be unfriended.")
                    .font(.system(size: 14))
                    .foregroundColor(SpasibaTheme.textMuted)

                HStack {
                    Spacer()

                    Button("Cancel") {
                        isRemoveFriendDialogOpen = false
                    }
                    .foregroundColor(SpasibaTheme.textMuted)
                    .padding(.horizontal, 14)

                    Button("Unfriend") {
                        sessionManager.removeFriendSignal(friendId: friendId)
                        isRemoveFriendDialogOpen = false
                        onRemoveFriend(friendId)
                        onBackClick()
                    }
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color(hex: 0xEF4444))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .padding(.top, 8)
            }
            .padding(22)
            .background(Color(hex: 0x181824))
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .padding(.horizontal, 32)
        }
    }

    private func showToast(_ msg: String) {
        toastMessage = msg
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            if toastMessage == msg {
                toastMessage = nil
            }
        }
    }
}
