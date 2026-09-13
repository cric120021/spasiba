import SwiftUI

public struct EunoiaScreen: View {
    @ObservedObject var player = NativePlayerManager.shared
    @ObservedObject var sessionManager = EunoiaSessionManager.shared
    @ObservedObject var friendManager = EunoiaFriendManager.shared
    @AppStorage("profile_photo_uri") private var myPhotoUri: String = ""
    @AppStorage("username") private var myUsername: String = "Spasiba Listener"

    @State private var isInviteDialogOpen: Bool = false
    @State private var inviteCodeInput: String = ""
    @State private var chatInputValue: String = ""
    @State private var isScrubbing: Bool = false
    @State private var scrubProgress: Double = 0.0
    @State private var toastMessage: String? = nil
    @State private var flyingReactions: [EunoiaFlyingReaction] = []

    // Room Chat Messages
    @State private var messages: [EunoiaScreenMessage] = [
        EunoiaScreenMessage(
            sender: "System",
            isPartner: false,
            text: "Welcome to Spasiba Eunoia Sync Room. Play music together with 0ms drift correction.",
            timestamp: "Now",
            isSystem: true
        )
    ]

    private struct EunoiaScreenMessage: Identifiable {
        let id = UUID()
        let sender: String
        let isPartner: Bool
        let text: String
        let timestamp: String
        var isSystem: Bool = false
    }

    private struct EunoiaFlyingReaction: Identifiable {
        let id = UUID()
        let emoji: String
        let startX: CGFloat
    }

    public init() {}

    private var screenWidth: CGFloat {
        UIScreen.main.bounds.width
    }

    private var activeSong: Song {
        player.currentSong ?? Song(
            id: "BddP6PYo2gs",
            title: "Kesariya",
            author: "Arijit Singh, Pritam",
            thumbnail: "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg",
            durationSeconds: 268
        )
    }

    private var durationSec: Double {
        max(1.0, player.duration > 0 ? player.duration : Double(activeSong.durationSeconds))
    }

    private var currentProgress: Double {
        if isScrubbing {
            return scrubProgress
        }
        return min(1.0, max(0.0, player.playbackProgress))
    }

    private var posFormatted: String {
        let sec = isScrubbing ? Int(scrubProgress * durationSec) : Int(player.currentTime)
        return String(format: "%d:%02d", sec / 60, sec % 60)
    }

    private var durFormatted: String {
        let sec = Int(durationSec)
        return String(format: "%d:%02d", sec / 60, sec % 60)
    }

    private var isConnected: Bool {
        sessionManager.sessionStatus == .connected
    }

    private var partnerDisplayName: String {
        if let name = sessionManager.partnerName, !name.isEmpty {
            return name
        }
        return "Partner"
    }

    private var liveStatusSubtitle: String {
        switch sessionManager.sessionStatus {
        case .connected:
            return player.isPlaying ? "Synced • Playing (\(posFormatted))" : "Synced • Paused"
        case .inviteSent:
            return "Sending invite... • Waiting"
        case .inviteReceived:
            return "Invite received • Tap to accept"
        case .rejectedBusy:
            return "Partner busy • Tap + to retry"
        case .idle:
            return "Ready • Tap + to pair"
        }
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // 1. Ambient Blurred Artwork Backdrop with Dark Vertical Gradient
            artworkBackdrop

            // 2. Main Eunoia Room Content
            VStack(spacing: 0) {
                // Dual Overlapping Collab Header
                headerBar
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .padding(.bottom, 6)
                    .frame(width: screenWidth)

                // Player Hero (Card-Less Floating Layout)
                playerHeroSection
                    .padding(.horizontal, 16)
                    .padding(.bottom, 6)
                    .frame(width: screenWidth)

                // Room Chat & Activity Stream
                roomChatSection
                    .padding(.horizontal, 12)
                    .frame(width: screenWidth)
            }
            .frame(width: screenWidth)
            .clipped()
            .padding(.bottom, 140) // Clearance for bottom navigation bar and mini player

            // 3. Floating Reaction Animations
            ForEach(flyingReactions) { reaction in
                FlyingReactionView(emoji: reaction.emoji, startX: reaction.startX)
            }

            // 4. Toast Alert HUD
            if let toast = toastMessage {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.85))
                        .clipShape(Capsule())
                        .overlay(Capsule().stroke(Color.white.opacity(0.2), lineWidth: 1))
                        .padding(.bottom, 150)
                }
                .transition(.scale.combined(with: .opacity))
                .animation(.easeInOut, value: toastMessage)
            }
        }
        .frame(width: screenWidth)
        .clipped()
        .sheet(isPresented: $isInviteDialogOpen) {
            invitePartnerSheet
        }
    }

    // MARK: - Ambient Blurred Backdrop
    private var artworkBackdrop: some View {
        ZStack {
            if let url = activeSong.resolvedThumbnailUrl {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: screenWidth)
                            .clipped()
                            .blur(radius: 70)
                            .scaleEffect(1.25)
                            .opacity(0.45)
                    }
                }
            }

            LinearGradient(
                colors: [
                    Color.black.opacity(0.82),
                    Color.black.opacity(0.94),
                    Color.black
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        }
        .frame(width: screenWidth)
        .clipped()
        .ignoresSafeArea()
    }

    // MARK: - Header Bar (YouTube Collab Overlapping Avatars & Session Controls)
    private var headerBar: some View {
        HStack(alignment: .center, spacing: 10) {
            // Overlapping Avatars Stack
            Button(action: { isInviteDialogOpen = true }) {
                HStack(spacing: 0) {
                    ZStack(alignment: .leading) {
                        // My Profile Image (First Avatar)
                        myAvatarThumbnail
                            .frame(width: 36, height: 36)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Color(hex: 0x10B981), lineWidth: 1.5))

                        // Partner Profile Image (Overlapping Avatar)
                        if isConnected || sessionManager.sessionStatus == .inviteSent {
                            partnerAvatarThumbnail
                                .frame(width: 36, height: 36)
                                .clipShape(Circle())
                                .overlay(Circle().stroke(Color(hex: 0x0284C7), lineWidth: 1.5))
                                .offset(x: 20)
                        }
                    }
                    .frame(width: (isConnected || sessionManager.sessionStatus == .inviteSent) ? 58 : 38, height: 38)

                    Spacer().frame(width: 10)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(isConnected ? "Listening with \(partnerDisplayName)" : "Listening with Partner")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .truncationMode(.tail)

                        Text(liveStatusSubtitle)
                            .font(.system(size: 12))
                            .foregroundColor(isConnected ? Color(hex: 0x10B981) : Color(hex: 0x94A3B8))
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .buttonStyle(PlainButtonStyle())

            HStack(spacing: 8) {
                // Red Power Button (Session End / Disconnect)
                Button(action: {
                    sessionManager.endSession()
                    showToast("Session disconnected")
                }) {
                    Circle()
                        .fill(Color.white.opacity(0.12))
                        .frame(width: 36, height: 36)
                        .overlay(
                            Image(systemName: "power")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(Color(hex: 0xEF4444))
                        )
                }
                .buttonStyle(PlainButtonStyle())

                // Invite / Add Partner Button
                Button(action: { isInviteDialogOpen = true }) {
                    Circle()
                        .fill(Color.white.opacity(0.12))
                        .frame(width: 36, height: 36)
                        .overlay(
                            Image(systemName: "person.badge.plus")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.white)
                        )
                }
                .buttonStyle(PlainButtonStyle())
            }
            .fixedSize()
        }
        .frame(width: screenWidth - 32)
    }

    @ViewBuilder
    private var myAvatarThumbnail: some View {
        if !myPhotoUri.isEmpty {
            let uri = myPhotoUri
            if let url = URL(string: uri), url.isFileURL, let img = UIImage(contentsOfFile: url.path) {
                Image(uiImage: img)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } else if let url = URL(string: uri) {
                AsyncImage(url: url) { phase in
                    if let img = phase.image {
                        img.resizable().aspectRatio(contentMode: .fill)
                    } else {
                        defaultUserIcon
                    }
                }
            } else {
                defaultUserIcon
            }
        } else {
            defaultUserIcon
        }
    }

    @ViewBuilder
    private var partnerAvatarThumbnail: some View {
        if let uri = sessionManager.partnerPhotoUri, let url = URL(string: uri) {
            AsyncImage(url: url) { phase in
                if let img = phase.image {
                    img.resizable().aspectRatio(contentMode: .fill)
                } else {
                    defaultPartnerIcon
                }
            }
        } else {
            defaultPartnerIcon
        }
    }

    private var defaultUserIcon: some View {
        ZStack {
            Color(hex: 0x1E1E26)
            Image(systemName: "person.fill")
                .font(.system(size: 16))
                .foregroundColor(.white)
        }
    }

    private var defaultPartnerIcon: some View {
        ZStack {
            Color(hex: 0x0284C7)
            Text(String(partnerDisplayName.prefix(1)).uppercased())
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.white)
        }
    }

    // MARK: - Player Hero (Card-Less Floating Layout)
    private var playerHeroSection: some View {
        VStack(spacing: 6) {
            // Centered Album Artwork (52% screen width, 1:1 aspect ratio, rounded 16pt)
            SongArtworkView(song: activeSong, cornerRadius: 16)
                .id(activeSong.id)
                .frame(width: 170, height: 170)
                .shadow(color: Color.black.opacity(0.6), radius: 16, x: 0, y: 8)
                .padding(.top, 2)

            // Song Title & Author
            VStack(spacing: 2) {
                Text(activeSong.title)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .truncationMode(.tail)

                Text(activeSong.author)
                    .font(.system(size: 13))
                    .foregroundColor(Color.white.opacity(0.7))
                    .lineLimit(1)
                    .truncationMode(.tail)
            }
            .padding(.top, 4)
            .frame(width: screenWidth - 48)

            // Dynamic Seeker Bar with interactive scrub
            VStack(spacing: 3) {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule()
                            .fill(Color.white.opacity(0.2))
                            .frame(height: 4)

                        Capsule()
                            .fill(Color.white)
                            .frame(width: geo.size.width * CGFloat(currentProgress), height: 4)

                        Circle()
                            .fill(Color.white)
                            .frame(width: 12, height: 12)
                            .offset(x: max(0, min(geo.size.width - 12, (geo.size.width * CGFloat(currentProgress)) - 6)))
                    }
                    .frame(height: 20)
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                isScrubbing = true
                                let fraction = max(0.0, min(1.0, Double(value.location.x / geo.size.width)))
                                scrubProgress = fraction
                            }
                            .onEnded { value in
                                let fraction = max(0.0, min(1.0, Double(value.location.x / geo.size.width)))
                                let targetMs = Int64(fraction * durationSec * 1000.0)
                                player.seekTo(targetMs)
                                isScrubbing = false
                            }
                    )
                }
                .frame(height: 20)

                HStack {
                    Text(posFormatted)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Color.white.opacity(0.6))
                    Spacer()
                    Text(durFormatted)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Color.white.opacity(0.6))
                }
            }
            .padding(.horizontal, 8)
            .frame(width: screenWidth - 32)

            // Media Controls Row (Previous, Big Play/Pause, Next, Video/Audio Toggle)
            ZStack {
                // Centered core controls [Prev] [Play/Pause] [Next]
                HStack(spacing: 24) {
                    Button(action: {
                        player.playPrevious()
                    }) {
                        Image(systemName: "backward.fill")
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(PlainButtonStyle())

                    // 52x52 circular white Play/Pause button matching android-native-2
                    Button(action: {
                        player.togglePlayPause()
                    }) {
                        ZStack {
                            Circle()
                                .fill(Color.white)
                                .frame(width: 52, height: 52)
                                .shadow(color: Color.white.opacity(0.2), radius: 6, x: 0, y: 0)

                            if player.isBuffering {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .black))
                                    .scaleEffect(0.9)
                            } else {
                                Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                                    .font(.system(size: 24, weight: .bold))
                                    .foregroundColor(.black)
                                    .offset(x: player.isPlaying ? 0 : 1.5)
                            }
                        }
                    }
                    .buttonStyle(PlainButtonStyle())

                    Button(action: {
                        player.playNext()
                    }) {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(PlainButtonStyle())
                }

                // Video / Audio toggle on far right
                HStack {
                    Spacer()
                    Button(action: {
                        player.isVideoMode.toggle()
                    }) {
                        Image(systemName: player.isVideoMode ? "headphones" : "play.rectangle.on.rectangle")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(player.isVideoMode ? Color(hex: 0x38BDF8) : .white)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .frame(width: screenWidth - 32)
        }
        .frame(width: screenWidth)
    }

    // MARK: - Room Chat & Activity Section (Card-Less Floating Canvas)
    private var roomChatSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("ROOM CHAT & ACTIVITY")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(Color.white.opacity(0.5))
                .tracking(1.0)
                .padding(.horizontal, 4)

            // Chat Messages Stream
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(messages) { msg in
                            chatMessageBubble(msg)
                                .id(msg.id)
                        }
                    }
                    .frame(width: screenWidth - 24)
                    .padding(.vertical, 4)
                }
                .frame(width: screenWidth - 24)
                .clipped()
                .onChange(of: messages.count) { _ in
                    if let last = messages.last {
                        withAnimation {
                            proxy.scrollTo(last.id, anchor: .bottom)
                        }
                    }
                }
            }

            // Chat Input & Flying Reaction Pill Bar
            VStack(spacing: 6) {
                // Quick emoji reaction buttons
                HStack(spacing: 12) {
                    ForEach(["🔥", "❤️", "👏", "⚡", "🎉"], id: \.self) { emoji in
                        Button(action: {
                            triggerFlyingReaction(emoji: emoji)
                        }) {
                            Text(emoji)
                                .font(.system(size: 20))
                                .padding(6)
                                .background(Color.white.opacity(0.08))
                                .clipShape(Circle())
                        }
                    }
                    Spacer()
                }
                .padding(.horizontal, 4)
                .frame(width: screenWidth - 24)

                // Message Input Capsule
                HStack(spacing: 8) {
                    Image(systemName: "slider.horizontal.3")
                        .font(.system(size: 16))
                        .foregroundColor(chatInputValue.isEmpty ? Color.white.opacity(0.4) : .white)
                        .padding(.leading, 12)

                    TextField("Message your partner...", text: $chatInputValue)
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                        .accentColor(.white)

                    Button(action: sendChatMessage) {
                        Circle()
                            .fill(chatInputValue.trimmingCharacters(in: .whitespaces).isEmpty ? Color.white.opacity(0.2) : Color(hex: 0x10B981))
                            .frame(width: 36, height: 36)
                            .overlay(
                                Image(systemName: "arrow.up")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white)
                            )
                            .padding(.trailing, 6)
                    }
                    .buttonStyle(PlainButtonStyle())
                    .disabled(chatInputValue.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                .frame(width: screenWidth - 24, height: 48)
                .background(Color(hex: 0x1E1F28))
                .clipShape(Capsule())
                .overlay(Capsule().stroke(Color.white.opacity(0.14), lineWidth: 1))
            }
            .frame(width: screenWidth - 24)
        }
        .frame(width: screenWidth)
    }

    @ViewBuilder
    private func chatMessageBubble(_ msg: EunoiaScreenMessage) -> some View {
        if msg.isSystem {
            HStack {
                Spacer()
                Text(msg.text)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Color.white.opacity(0.6))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 4)
                    .background(Color.white.opacity(0.06))
                    .clipShape(Capsule())
                    .frame(maxWidth: screenWidth - 40)
                Spacer()
            }
        } else if msg.isPartner {
            HStack(alignment: .bottom, spacing: 8) {
                // Partner Avatar
                partnerAvatarThumbnail
                    .frame(width: 28, height: 28)
                    .clipShape(Circle())

                VStack(alignment: .leading, spacing: 2) {
                    Text(msg.sender)
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(Color.white.opacity(0.6))

                    Text(msg.text)
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color(hex: 0x1C1D27))
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(Color(hex: 0x2C2D3C), lineWidth: 1)
                        )
                        .frame(maxWidth: screenWidth * 0.72, alignment: .leading)

                    Text(msg.timestamp)
                        .font(.system(size: 9))
                        .foregroundColor(Color.white.opacity(0.4))
                }
                Spacer()
            }
        } else {
            HStack(alignment: .bottom, spacing: 8) {
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(msg.text)
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color(hex: 0x8E0E25))
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(Color(hex: 0xE50914).opacity(0.4), lineWidth: 1)
                        )
                        .frame(maxWidth: screenWidth * 0.72, alignment: .trailing)

                    Text(msg.timestamp)
                        .font(.system(size: 9))
                        .foregroundColor(Color.white.opacity(0.4))
                }
            }
        }
    }

    private func sendChatMessage() {
        let clean = chatInputValue.trimmingCharacters(in: .whitespaces)
        guard !clean.isEmpty else { return }

        let timeStr = DateFormatter.localizedString(from: Date(), dateStyle: .none, timeStyle: .short)
        messages.append(
            EunoiaScreenMessage(
                sender: "You",
                isPartner: false,
                text: clean,
                timestamp: timeStr
            )
        )
        chatInputValue = ""

        if isConnected, let pId = sessionManager.partnerDeviceId {
            sessionManager.sendFriendChatMessageSignal(pId, clean)
        }
    }

    private func triggerFlyingReaction(emoji: String) {
        let randomX = CGFloat.random(in: 40...UIScreen.main.bounds.width - 60)
        let item = EunoiaFlyingReaction(emoji: emoji, startX: randomX)
        flyingReactions.append(item)
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            flyingReactions.removeAll(where: { $0.id == item.id })
        }
    }

    private func showToast(_ msg: String) {
        withAnimation {
            toastMessage = msg
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            withAnimation {
                if toastMessage == msg {
                    toastMessage = nil
                }
            }
        }
    }

    // MARK: - Invite Partner Sheet
    private var invitePartnerSheet: some View {
        VStack(spacing: 18) {
            Text("Invite Partner to Eunoia")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)

            Text("Listen synchronously in real-time with 0ms drift.")
                .font(.system(size: 13))
                .foregroundColor(Color.white.opacity(0.6))
                .multilineTextAlignment(.center)

            // Friends List
            VStack(alignment: .leading, spacing: 8) {
                Text("Select from Friends")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Color(hex: 0x10B981))

                if friendManager.friends.isEmpty {
                    Text("No friends yet. Add friends from the Profile screen.")
                        .font(.system(size: 13))
                        .foregroundColor(Color.white.opacity(0.5))
                        .padding(.vertical, 8)
                } else {
                    ScrollView {
                        VStack(spacing: 8) {
                            ForEach(friendManager.friends) { friend in
                                HStack {
                                    Circle()
                                        .fill(Color(hex: 0x222233))
                                        .frame(width: 32, height: 32)
                                        .overlay(Text(friend.displayName.prefix(1)).font(.system(size: 13, weight: .bold)).foregroundColor(.white))

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(friend.displayName)
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(.white)
                                        Text(friend.friendId)
                                            .font(.system(size: 11))
                                            .foregroundColor(Color.white.opacity(0.5))
                                    }

                                    Spacer()

                                    Button("Invite") {
                                        sessionManager.quickInvite(friend: friend)
                                        isInviteDialogOpen = false
                                        showToast("Invite sent to \(friend.displayName)!")
                                    }
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(.black)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(Color(hex: 0x10B981))
                                    .cornerRadius(12)
                                }
                                .padding(8)
                                .background(Color.white.opacity(0.04))
                                .cornerRadius(12)
                            }
                        }
                    }
                    .frame(maxHeight: 180)
                }
            }
            .padding()
            .background(Color.white.opacity(0.04))
            .cornerRadius(16)

            // Manual Code Input
            VStack(alignment: .leading, spacing: 6) {
                Text("Or enter Partner Device Code:")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Color.white.opacity(0.6))

                HStack {
                    TextField("e.g. SPASIBA-8A2F41", text: $inviteCodeInput)
                        .autocapitalization(.allCharacters)
                        .foregroundColor(.white)
                        .accentColor(.white)
                        .padding(10)
                        .background(Color.white.opacity(0.06))
                        .cornerRadius(10)

                    Button("Pair") {
                        let clean = inviteCodeInput.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "@", with: "")
                        if !clean.isEmpty {
                            sessionManager.sendInvite(friendId: clean, friendName: clean)
                            isInviteDialogOpen = false
                            showToast("Invite sent to \(clean)!")
                        }
                    }
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.black)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.white)
                    .cornerRadius(10)
                }
            }
            .padding()
            .background(Color.white.opacity(0.04))
            .cornerRadius(16)

            Button("Cancel") {
                isInviteDialogOpen = false
            }
            .foregroundColor(Color.white.opacity(0.6))
            .padding(.top, 4)

            Spacer()
        }
        .padding(20)
        .background(Color(hex: 0x14141E).ignoresSafeArea())
    }
}

// MARK: - Floating Reaction Animation Component
private struct FlyingReactionView: View {
    let emoji: String
    let startX: CGFloat
    @State private var yOffset: CGFloat = 0
    @State private var opacity: Double = 1.0

    var body: some View {
        Text(emoji)
            .font(.system(size: 32))
            .position(x: startX, y: UIScreen.main.bounds.height - 240 + yOffset)
            .opacity(opacity)
            .onAppear {
                withAnimation(.easeOut(duration: 1.8)) {
                    yOffset = -350
                    opacity = 0.0
                }
            }
    }
}
