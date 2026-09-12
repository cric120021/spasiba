import SwiftUI

public struct FriendsListScreen: View {
    public var onBackClick: () -> Void
    public var onQuickInviteFriend: (EunoiaFriend) -> Void
    public var onOpenFriendChat: (EunoiaFriend) -> Void

    @ObservedObject var friendManager = EunoiaFriendManager.shared
    @ObservedObject var sessionManager = EunoiaSessionManager.shared

    @State private var selectedTab: Int = 0 // 0 = Friends, 1 = Pending / Requests
    @State private var isAddFriendDialogOpen: Bool = false
    @State private var friendCodeInput: String = ""
    @State private var friendNameInput: String = ""
    @State private var toastMessage: String? = nil

    public init(
        onBackClick: @escaping () -> Void = {},
        onQuickInviteFriend: @escaping (EunoiaFriend) -> Void = { _ in },
        onOpenFriendChat: @escaping (EunoiaFriend) -> Void = { _ in }
    ) {
        self.onBackClick = onBackClick
        self.onQuickInviteFriend = onQuickInviteFriend
        self.onOpenFriendChat = onOpenFriendChat
    }

    private var acceptedFriends: [EunoiaFriend] {
        friendManager.friends.filter { $0.status == .accepted }
    }

    private var pendingRequests: [EunoiaFriend] {
        friendManager.friends.filter { $0.status == .pendingOutgoing || $0.status == .pendingIncoming }
    }

    private var incomingRequests: [EunoiaFriend] {
        pendingRequests.filter { $0.status == .pendingIncoming }
    }

    private var outgoingRequests: [EunoiaFriend] {
        pendingRequests.filter { $0.status == .pendingOutgoing }
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 0) {
                // Header Row
                headerSection

                // Tabs Row
                tabsSection

                // Tab Content
                if selectedTab == 0 {
                    friendsTabContent
                } else {
                    pendingRequestsTabContent
                }
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

            // Add Friend Dialog
            if isAddFriendDialogOpen {
                addFriendDialogView
            }
        }
    }

    // MARK: - Header & Tabs

    private var headerSection: some View {
        HStack(alignment: .center) {
            Button(action: onBackClick) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 38, height: 38)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("My Friends")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)

                Text("\(acceptedFriends.count) Connections • \(pendingRequests.count) Pending")
                    .font(.system(size: 12))
                    .foregroundColor(SpasibaTheme.textMuted)
            }

            Spacer()

            Button(action: {
                friendCodeInput = ""
                friendNameInput = ""
                isAddFriendDialogOpen = true
            }) {
                HStack(spacing: 4) {
                    Image(systemName: "person.badge.plus")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.black)
                    Text("Add Friend")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.black)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .padding(.bottom, 10)
    }

    private var tabsSection: some View {
        HStack(spacing: 0) {
            Button(action: { selectedTab = 0 }) {
                VStack(spacing: 8) {
                    Text("Friends (\(acceptedFriends.count))")
                        .font(.system(size: 14, weight: selectedTab == 0 ? .bold : .medium))
                        .foregroundColor(selectedTab == 0 ? .white : SpasibaTheme.textMuted)

                    Rectangle()
                        .fill(selectedTab == 0 ? Color.white : Color.clear)
                        .frame(height: 2)
                }
            }
            .frame(maxWidth: .infinity)

            Button(action: { selectedTab = 1 }) {
                VStack(spacing: 8) {
                    Text("Pending / Requests (\(pendingRequests.count))")
                        .font(.system(size: 14, weight: selectedTab == 1 ? .bold : .medium))
                        .foregroundColor(selectedTab == 1 ? Color.white : SpasibaTheme.textMuted)

                    Rectangle()
                        .fill(selectedTab == 1 ? Color.white : Color.clear)
                        .frame(height: 2)
                }
            }
            .frame(maxWidth: .infinity)
        }
        .padding(.top, 4)
    }

    // MARK: - Friends Tab

    private var friendsTabContent: some View {
        Group {
            if acceptedFriends.isEmpty {
                VStack {
                    Spacer()
                    Text("No friends connected yet.\nTap '+ Add Friend' to send an invite by code!")
                        .font(.system(size: 14))
                        .foregroundColor(SpasibaTheme.textMuted)
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)
                        .padding(32)
                    Spacer()
                }
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(spacing: 10) {
                        ForEach(acceptedFriends) { friend in
                            friendCardItem(friend: friend)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .padding(.bottom, 120)
                }
            }
        }
    }

    private func friendCardItem(friend: EunoiaFriend) -> some View {
        Button(action: { onOpenFriendChat(friend) }) {
            HStack(spacing: 12) {
                // Avatar
                ZStack {
                    Circle()
                        .fill(Color.white.opacity(0.15))
                        .frame(width: 44, height: 44)

                    if let photo = friend.photoUri, !photo.isEmpty {
                        AsyncImage(url: URL(string: photo)) { phase in
                            if let img = phase.image {
                                img.resizable().aspectRatio(contentMode: .fill)
                            } else {
                                Text(String(friend.displayName.prefix(1)).uppercased())
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                            }
                        }
                        .frame(width: 44, height: 44)
                        .clipShape(Circle())
                    } else {
                        Text(String(friend.displayName.prefix(1)).uppercased())
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                    }
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(friend.displayName)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(SpasibaTheme.textStrong)

                    Text(friend.friendId)
                        .font(.system(size: 12))
                        .foregroundColor(SpasibaTheme.textMuted)
                }

                Spacer()

                // Chat button
                Button(action: { onOpenFriendChat(friend) }) {
                    Image(systemName: "bubble.left.fill")
                        .font(.system(size: 16))
                        .foregroundColor(.white)
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(BorderlessButtonStyle())

                // Quick Invite bolt button
                Button(action: { onQuickInviteFriend(friend) }) {
                    HStack(spacing: 4) {
                        Image(systemName: "bolt.fill")
                            .font(.system(size: 12))
                            .foregroundColor(.black)
                        Text("Invite")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.black)
                    }
                    .padding(.horizontal, 10)
                    .frame(height: 34)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(BorderlessButtonStyle())
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Color.white.opacity(0.04))
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(Color.white.opacity(0.08), lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }

    // MARK: - Pending Tab

    private var pendingRequestsTabContent: some View {
        Group {
            if pendingRequests.isEmpty {
                VStack {
                    Spacer()
                    Text("No pending friend requests.\nWhen friends invite you, their requests will show up here!")
                        .font(.system(size: 14))
                        .foregroundColor(SpasibaTheme.textMuted)
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)
                        .padding(32)
                    Spacer()
                }
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(alignment: .leading, spacing: 10) {
                        if !incomingRequests.isEmpty {
                            Text("INCOMING REQUESTS")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(SpasibaTheme.accent)
                                .tracking(1.4)
                                .padding(.top, 8)
                                .padding(.horizontal, 4)

                            ForEach(incomingRequests) { friend in
                                pendingIncomingCard(friend: friend)
                            }
                        }

                        if !outgoingRequests.isEmpty {
                            Text("OUTGOING INVITES")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(Color(hex: 0xF59E0B))
                                .tracking(1.4)
                                .padding(.top, 14)
                                .padding(.horizontal, 4)

                            ForEach(outgoingRequests) { friend in
                                pendingOutgoingCard(friend: friend)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .padding(.bottom, 120)
                }
            }
        }
    }

    private func pendingIncomingCard(friend: EunoiaFriend) -> some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(Color.white.opacity(0.2))
                    .frame(width: 40, height: 40)
                Text(String(friend.displayName.prefix(1)).uppercased())
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.white)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(friend.displayName)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.white)

                Text("Sent you a friend request")
                    .font(.system(size: 11))
                    .foregroundColor(SpasibaTheme.accent)
            }

            Spacer()

            Button(action: {
                sessionManager.acceptFriendRequestSignal(friendId: friend.friendId, friendName: friend.name)
                selectedTab = 0
                showToast("Accepted friend request from \(friend.name)!")
            }) {
                HStack(spacing: 4) {
                    Image(systemName: "checkmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.black)
                    Text("Accept")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.black)
                }
                .padding(.horizontal, 12)
                .frame(height: 34)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            Button(action: {
                sessionManager.removeFriendSignal(friendId: friend.friendId)
            }) {
                Image(systemName: "xmark")
                    .font(.system(size: 14))
                    .foregroundColor(SpasibaTheme.textMuted)
                    .frame(width: 34, height: 34)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(Color.white.opacity(0.04))
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.white.opacity(0.3), lineWidth: 1)
        )
    }

    private func pendingOutgoingCard(friend: EunoiaFriend) -> some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(Color(hex: 0xF59E0B).opacity(0.2))
                    .frame(width: 40, height: 40)
                Text(String(friend.displayName.prefix(1)).uppercased())
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Color(hex: 0xF59E0B))
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(friend.displayName)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.white)

                Text("Request pending...")
                    .font(.system(size: 11))
                    .foregroundColor(Color(hex: 0xF59E0B))
            }

            Spacer()

            Button(action: {
                sessionManager.removeFriendSignal(friendId: friend.friendId)
            }) {
                Image(systemName: "xmark")
                    .font(.system(size: 14))
                    .foregroundColor(SpasibaTheme.textMuted)
                    .frame(width: 34, height: 34)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(Color.white.opacity(0.04))
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color(hex: 0xF59E0B).opacity(0.3), lineWidth: 1)
        )
    }

    // MARK: - Add Friend Dialog

    private var addFriendDialogView: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()

            VStack(alignment: .leading, spacing: 12) {
                Text("Send Friend Request")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)

                Text("Enter your friend's code or Device ID:")
                    .font(.system(size: 13))
                    .foregroundColor(SpasibaTheme.textMuted)

                TextField("e.g. SPASIBA-9A2F41", text: $friendCodeInput)
                    .autocapitalization(.allCharacters)
                    .foregroundColor(.white)
                    .padding(12)
                    .background(Color.white.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color.white.opacity(0.2), lineWidth: 1)
                    )

                Text("Friend Name (Optional):")
                    .font(.system(size: 13))
                    .foregroundColor(SpasibaTheme.textMuted)
                    .padding(.top, 4)

                TextField("e.g. Alex", text: $friendNameInput)
                    .foregroundColor(.white)
                    .padding(12)
                    .background(Color.white.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color.white.opacity(0.2), lineWidth: 1)
                    )

                HStack {
                    Spacer()

                    Button("Cancel") {
                        isAddFriendDialogOpen = false
                    }
                    .foregroundColor(SpasibaTheme.textMuted)
                    .padding(.horizontal, 14)

                    Button("Send Request") {
                        let trimmedCode = friendCodeInput.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
                        if !trimmedCode.isEmpty {
                            sessionManager.sendFriendRequestSignal(friendCode: trimmedCode, friendName: friendNameInput.trimmingCharacters(in: .whitespacesAndNewlines))
                            isAddFriendDialogOpen = false
                            selectedTab = 1
                            showToast("Friend request sent to \(trimmedCode)!")
                        }
                    }
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.black)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .padding(.top, 8)
            }
            .padding(22)
            .background(Color(hex: 0x14141E))
            .clipShape(RoundedRectangle(cornerRadius: 20))
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
