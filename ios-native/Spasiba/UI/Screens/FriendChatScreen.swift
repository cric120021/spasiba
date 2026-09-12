import SwiftUI
import PhotosUI

public struct FriendChatScreen: View {
    public let friendId: String
    public let friendName: String
    public var onBackClick: () -> Void
    public var onOpenDetails: () -> Void
    public var onRemoveFriend: (String) -> Void
    public var onQuickInvite: (EunoiaFriend) -> Void

    @ObservedObject var friendManager = EunoiaFriendManager.shared
    @ObservedObject var sessionManager = EunoiaSessionManager.shared

    @State private var messageInput: String = ""
    @State private var replyingToMessage: EunoiaFriendChatMessage? = nil
    @State private var isDeleteAllDialogOpen: Bool = false
    @State private var isRemoveFriendDialogOpen: Bool = false
    @State private var isGifStickerPickerOpen: Bool = false
    @State private var toastMessage: String? = nil

    // Photo picker
    @State private var selectedPhotoItem: PhotosPickerItem? = nil

    public init(
        friendId: String,
        friendName: String,
        onBackClick: @escaping () -> Void = {},
        onOpenDetails: @escaping () -> Void = {},
        onRemoveFriend: @escaping (String) -> Void = { _ in },
        onQuickInvite: @escaping (EunoiaFriend) -> Void = { _ in }
    ) {
        self.friendId = friendId
        self.friendName = friendName
        self.onBackClick = onBackClick
        self.onOpenDetails = onOpenDetails
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

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 0) {
                // WhatsApp Dark Top Header Bar (#0F0F0F)
                headerBarSection

                // Message Stream
                messageStreamSection

                // Reply Bar (if replying)
                if let replying = replyingToMessage {
                    replyBarSection(replying: replying)
                }

                // WhatsApp Dark Bottom Input Bar
                bottomInputBarSection
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
                        .padding(.bottom, 70)
                }
                .transition(.opacity)
            }

            // Dialogs & Sheets
            if isDeleteAllDialogOpen {
                deleteAllDialogView
            }

            if isGifStickerPickerOpen {
                GifStickerPickerView(
                    onSelectEmoji: { emoji in
                        messageInput += emoji
                        isGifStickerPickerOpen = false
                    },
                    onSelectGif: { gifUrl in
                        sessionManager.sendFriendChatMessageSignal(friendId, "[GIF:\(gifUrl)]")
                        isGifStickerPickerOpen = false
                    },
                    onClose: {
                        isGifStickerPickerOpen = false
                    }
                )
            }
        }
        .onChange(of: selectedPhotoItem) { newItem in
            Task {
                if let data = try? await newItem?.loadTransferable(type: Data.self) {
                    // Save temporary image or base64
                    let base64 = "data:image/jpeg;base64," + data.base64EncodedString()
                    friendManager.addChatMessage(
                        friendId: friendId,
                        senderId: sessionManager.myDeviceId,
                        senderName: "You",
                        text: "",
                        isPartner: false,
                        replyToText: replyingToMessage?.messageText,
                        replyToSender: replyingToMessage?.senderName,
                        mediaUri: base64
                    )
                    replyingToMessage = nil
                }
            }
        }
    }

    // MARK: - Header Bar

    private var headerBarSection: some View {
        HStack(alignment: .center, spacing: 8) {
            Button(action: onBackClick) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 36, height: 36)
            }

            Button(action: onOpenDetails) {
                HStack(spacing: 10) {
                    ZStack {
                        Circle()
                            .fill(Color(hex: 0x2A3942))
                            .frame(width: 38, height: 38)

                        if let photo = matchedFriend?.photoUri, !photo.isEmpty {
                            AsyncImage(url: URL(string: photo)) { phase in
                                if let img = phase.image {
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } else {
                                    Text(String(displayName.prefix(1)).uppercased())
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.white)
                                }
                            }
                            .frame(width: 38, height: 38)
                            .clipShape(Circle())
                        } else {
                            Text(String(displayName.prefix(1)).uppercased())
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                        }
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(displayName)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)

                        Text("Online")
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: 0x00A884))
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())

            Spacer()

            // Quick Invite Bolt Button
            Button(action: {
                let friendObj = matchedFriend ?? EunoiaFriend(friendId: friendId, name: friendName)
                onQuickInvite(friendObj)
            }) {
                Image(systemName: "bolt.fill")
                    .font(.system(size: 18))
                    .foregroundColor(.white)
                    .frame(width: 36, height: 36)
            }

            // 3-Dots Menu Button
            Button(action: { isDeleteAllDialogOpen = true }) {
                Image(systemName: "ellipsis")
                    .font(.system(size: 16))
                    .foregroundColor(.white)
                    .rotationEffect(.degrees(90))
                    .frame(width: 36, height: 36)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(Color(hex: 0x0F0F0F))
        .overlay(
            Rectangle()
                .frame(height: 0.8)
                .foregroundColor(Color(hex: 0x1F1F1F)),
            alignment: .bottom
        )
    }

    // MARK: - Message Stream

    private var messageStreamSection: some View {
        ScrollViewReader { proxy in
            ScrollView(.vertical, showsIndicators: false) {
                LazyVStack(spacing: 6) {
                    ForEach(Array(chatMessages.enumerated()), id: \.element.id) { index, msg in
                        let prevMsg = index > 0 ? chatMessages[index - 1] : nil
                        let showDate = prevMsg == nil || (msg.timestampMs - prevMsg!.timestampMs > 1800000)

                        if showDate {
                            dateSeparatorBadge(timestampMs: msg.timestampMs)
                        }

                        messageBubble(msg: msg)
                            .id(msg.id)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 12)
            }
            .onChange(of: chatMessages.count) { _ in
                if let last = chatMessages.last {
                    withAnimation {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
            }
        }
    }

    private func dateSeparatorBadge(timestampMs: Int64) -> some View {
        let date = Date(timeIntervalSince1970: Double(timestampMs) / 1000.0)
        let formatter = DateFormatter()
        formatter.dateFormat = "EEEE"
        let dateStr = formatter.string(from: date)

        return HStack {
            Spacer()
            Text(dateStr)
                .font(.system(size: 10, weight: .semibold))
                .foregroundColor(SpasibaTheme.textMuted)
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(Color(hex: 0x1E1F28))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.white.opacity(0.12), lineWidth: 0.5)
                )
            Spacer()
        }
        .padding(.vertical, 8)
    }

    private func messageBubble(msg: EunoiaFriendChatMessage) -> some View {
        let isPartner = msg.isPartner
        let date = Date(timeIntervalSince1970: Double(msg.timestampMs) / 1000.0)
        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "hh:mm a"
        let timeStr = timeFormatter.string(from: date)

        return HStack {
            if !isPartner { Spacer(minLength: 50) }

            VStack(alignment: isPartner ? .leading : .trailing, spacing: 4) {
                VStack(alignment: .leading, spacing: 4) {
                    // Reply quote preview
                    if let replyText = msg.replyToText, !replyText.isEmpty {
                        HStack(spacing: 6) {
                            RoundedRectangle(cornerRadius: 2)
                                .fill(Color(hex: 0x00A884))
                                .frame(width: 3)

                            VStack(alignment: .leading, spacing: 1) {
                                Text(msg.replyToSender ?? "Reply")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(Color(hex: 0x00A884))

                                Text(replyText)
                                    .font(.system(size: 11))
                                    .foregroundColor(Color.white.opacity(0.8))
                                    .lineLimit(1)
                            }
                        }
                        .padding(6)
                        .background(Color.black.opacity(0.3))
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                    }

                    // Shared photo preview
                    if let mediaUri = msg.mediaUri, !mediaUri.isEmpty {
                        AsyncImage(url: URL(string: mediaUri)) { phase in
                            if let img = phase.image {
                                img.resizable().aspectRatio(1.4, contentMode: .fill)
                            } else {
                                Color(hex: 0x1A1A24)
                            }
                        }
                        .frame(maxWidth: 240)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    }

                    // Voice Note bubble
                    if msg.isVoiceNote {
                        HStack(spacing: 10) {
                            ZStack {
                                Circle()
                                    .fill(isPartner ? Color(hex: 0x282834) : Color(hex: 0x00A884).opacity(0.25))
                                    .frame(width: 36, height: 36)

                                Image(systemName: "phone.fill")
                                    .font(.system(size: 16))
                                    .foregroundColor(Color(hex: 0x00A884))
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                Text(isPartner ? "Missed voice call" : "Voice Message")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white)

                                Text(isPartner ? "Tap to call back" : String(format: "0:%02d", msg.voiceDurationSec))
                                    .font(.system(size: 11))
                                    .foregroundColor(Color(hex: 0x888888))
                            }
                        }
                        .padding(.vertical, 4)
                    }

                    // Text or GIF
                    if !msg.messageText.isEmpty {
                        let isGif = msg.messageText.starts(with: "[GIF:") || msg.messageText.contains("tenor.com") || msg.messageText.contains(".gif")
                        if isGif {
                            let gifUrl = msg.messageText
                                .replacingOccurrences(of: "[GIF:", with: "")
                                .replacingOccurrences(of: "[STICKER:", with: "")
                                .replacingOccurrences(of: "]", with: "")

                            if gifUrl.starts(with: "http") {
                                AsyncImage(url: URL(string: gifUrl)) { phase in
                                    if let img = phase.image {
                                        img.resizable().aspectRatio(contentMode: .fill)
                                    } else {
                                        Color(hex: 0x1E1E2C)
                                    }
                                }
                                .frame(width: 220, height: 160)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                            } else {
                                Text(msg.messageText)
                                    .font(.system(size: 14))
                                    .foregroundColor(Color(hex: 0xE9EDEF))
                            }
                        } else {
                            Text(msg.messageText)
                                .font(.system(size: 14))
                                .foregroundColor(Color(hex: 0xE9EDEF))
                        }
                    }

                    // Timestamp
                    Text(timeStr)
                        .font(.system(size: 10))
                        .foregroundColor(Color(hex: 0x8696A0))
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(isPartner ? Color(hex: 0x2A2A34) : Color(hex: 0x005C4B))
                .clipShape(
                    RoundedRectangle(cornerRadius: 12)
                )
                .onTapGesture {
                    replyingToMessage = msg
                }
            }

            if isPartner { Spacer(minLength: 50) }
        }
    }

    // MARK: - Reply Bar

    private func replyBarSection(replying: EunoiaFriendChatMessage) -> some View {
        HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 2)
                .fill(Color(hex: 0x00A884))
                .frame(width: 3, height: 26)

            VStack(alignment: .leading, spacing: 2) {
                Text("Replying to \(replying.senderName)")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(Color(hex: 0x00A884))

                Text(replying.messageText.isEmpty ? "Media / Voice note" : replying.messageText)
                    .font(.system(size: 11))
                    .foregroundColor(Color(hex: 0x8696A0))
                    .lineLimit(1)
            }

            Spacer()

            Button(action: { replyingToMessage = nil }) {
                Image(systemName: "xmark")
                    .font(.system(size: 14))
                    .foregroundColor(Color(hex: 0x8696A0))
                    .frame(width: 28, height: 28)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(Color(hex: 0x202C33))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .padding(.horizontal, 8)
    }

    // MARK: - Bottom Input Bar

    private var bottomInputBarSection: some View {
        HStack(spacing: 8) {
            // Input pill container (#0F0F0F)
            HStack(spacing: 8) {
                // Emoji / GIF button
                Button(action: { isGifStickerPickerOpen.toggle() }) {
                    Image(systemName: "face.smiling")
                        .font(.system(size: 20))
                        .foregroundColor(Color(hex: 0x8696A0))
                        .frame(width: 32, height: 32)
                }

                // Text field
                ZStack(alignment: .leading) {
                    if messageInput.isEmpty {
                        Text("Message")
                            .font(.system(size: 15))
                            .foregroundColor(Color(hex: 0x8696A0))
                    }
                    TextField("", text: $messageInput)
                        .foregroundColor(Color(hex: 0xE9EDEF))
                        .accentColor(Color(hex: 0x00A884))
                        .font(.system(size: 15))
                }

                // Photos Attachment Picker
                PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                    Image(systemName: "paperclip")
                        .font(.system(size: 18))
                        .foregroundColor(Color(hex: 0x8696A0))
                        .frame(width: 32, height: 32)
                }
            }
            .padding(.horizontal, 10)
            .frame(height: 48)
            .background(Color(hex: 0x0F0F0F))
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .overlay(
                RoundedRectangle(cornerRadius: 24)
                    .stroke(Color(hex: 0x1F1F24), lineWidth: 1)
            )

            // Right floating Send / Voice circle (#00A884)
            let isSendActive = !messageInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            Button(action: {
                if isSendActive {
                    let text = messageInput.trimmingCharacters(in: .whitespacesAndNewlines)
                    messageInput = ""
                    sessionManager.sendFriendChatMessageSignal(friendId, text)
                    replyingToMessage = nil
                } else {
                    let duration = Int.random(in: 3...8)
                    friendManager.addChatMessage(
                        friendId: friendId,
                        senderId: sessionManager.myDeviceId,
                        senderName: "You",
                        text: "",
                        isPartner: false,
                        replyToText: replyingToMessage?.messageText,
                        replyToSender: replyingToMessage?.senderName,
                        isVoiceNote: true,
                        voiceDurationSec: duration
                    )
                    replyingToMessage = nil
                    showToast("Voice note sent 🎙️")
                }
            }) {
                ZStack {
                    Circle()
                        .fill(Color(hex: 0x00A884))
                        .frame(width: 48, height: 48)

                    Image(systemName: isSendActive ? "paperplane.fill" : "mic.fill")
                        .font(.system(size: 18))
                        .foregroundColor(.white)
                }
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 8)
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

    private func showToast(_ msg: String) {
        toastMessage = msg
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            if toastMessage == msg {
                toastMessage = nil
            }
        }
    }
}

// MARK: - Tenor GIF & Emoji Picker Sheet View

public struct GifStickerPickerView: View {
    public var onSelectEmoji: (String) -> Void
    public var onSelectGif: (String) -> Void
    public var onClose: () -> Void

    @State private var searchQuery: String = ""
    @State private var gifResults: [TenorGifItem] = []
    @State private var isLoadingGifs: Bool = false

    private let quickEmojis = ["🔥", "🎵", "❤️", "😂", "😍", "👍", "🎉", "😎", "🙌", "✨", "🎧", "⚡", "💬", "🌟", "🥳", "🎶"]
    private let trendingTags = ["Trending", "Dance", "Music", "LOL", "Vibes", "Party", "Love", "Sad", "Hyped"]

    public var body: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()

            VStack(alignment: .leading, spacing: 12) {
                // Header
                HStack {
                    HStack(spacing: 6) {
                        Text("Emojis & GIFs")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)

                        Text("TENOR")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(Color(hex: 0x0284C7))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color(hex: 0x0284C7).opacity(0.15))
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                    }

                    Spacer()

                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .font(.system(size: 14))
                            .foregroundColor(SpasibaTheme.textMuted)
                            .frame(width: 28, height: 28)
                    }
                }

                // Quick Emojis
                Text("QUICK EMOJIS")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(Color(hex: 0x8696A0))

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(quickEmojis, id: \.self) { emoji in
                            Button(action: { onSelectEmoji(emoji) }) {
                                Text(emoji)
                                    .font(.system(size: 20))
                                    .frame(width: 38, height: 38)
                                    .background(Color(hex: 0x202C33))
                                    .clipShape(Circle())
                            }
                        }
                    }
                }

                // Search field
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(SpasibaTheme.textMuted)

                    ZStack(alignment: .leading) {
                        if searchQuery.isEmpty {
                            Text("Search Tenor GIFs...")
                                .foregroundColor(SpasibaTheme.textMuted)
                                .font(.system(size: 14))
                        }
                        TextField("", text: $searchQuery)
                            .foregroundColor(.white)
                            .font(.system(size: 14))
                            .accentColor(Color(hex: 0x00A884))
                    }

                    if !searchQuery.isEmpty {
                        Button(action: { searchQuery = "" }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(SpasibaTheme.textMuted)
                        }
                    }
                }
                .padding(10)
                .background(Color(hex: 0x181824))
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Color.white.opacity(0.15), lineWidth: 1)
                )

                // Trending tags
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(trendingTags, id: \.self) { tag in
                            let isSelected = (searchQuery.caseInsensitiveCompare(tag) == .orderedSame) || (tag == "Trending" && searchQuery.isEmpty)
                            Button(action: {
                                searchQuery = (tag == "Trending") ? "" : tag
                            }) {
                                Text(tag)
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(isSelected ? .black : .white)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .background(isSelected ? Color(hex: 0x10B981) : Color(hex: 0x242436))
                                    .clipShape(RoundedRectangle(cornerRadius: 14))
                            }
                        }
                    }
                }

                // GIF Grid
                if isLoadingGifs {
                    VStack {
                        Spacer()
                        ProgressView()
                            .tint(.white)
                        Text("Loading GIFs...")
                            .font(.system(size: 12))
                            .foregroundColor(SpasibaTheme.textMuted)
                            .padding(.top, 4)
                        Spacer()
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 200)
                } else if gifResults.isEmpty {
                    VStack {
                        Spacer()
                        Text("No GIFs found")
                            .font(.system(size: 13))
                            .foregroundColor(SpasibaTheme.textMuted)
                        Spacer()
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 200)
                } else {
                    ScrollView {
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                            ForEach(gifResults) { item in
                                Button(action: { onSelectGif(item.gifUrl) }) {
                                    AsyncImage(url: URL(string: item.previewUrl)) { phase in
                                        if let img = phase.image {
                                            img.resizable().aspectRatio(contentMode: .fill)
                                        } else {
                                            Color(hex: 0x1E1E2C)
                                        }
                                    }
                                    .frame(height: 100)
                                    .clipped()
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 10)
                                            .stroke(Color.white.opacity(0.08), lineWidth: 1)
                                    )
                                }
                            }
                        }
                    }
                    .frame(height: 220)
                }
            }
            .padding(18)
            .background(Color(hex: 0x12121E))
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .padding(.horizontal, 20)
        }
        .task(id: searchQuery) {
            isLoadingGifs = true
            gifResults = await TenorGifApi.searchGifs(query: searchQuery)
            isLoadingGifs = false
        }
    }
}
