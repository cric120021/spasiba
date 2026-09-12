import SwiftUI

public struct ProfileScreen: View {
    @ObservedObject var player = NativePlayerManager.shared
    @ObservedObject var repository = MusicRepository.shared
    @ObservedObject var friendManager = EunoiaFriendManager.shared
    @Environment(\.dismiss) private var dismiss

    @State private var username: String = UserDefaults.standard.string(forKey: "username") ?? "Spasiba Listener"
    @State private var visitorId: String = UIDevice.current.identifierForVendor?.uuidString.prefix(8).uppercased().description ?? "SPASIBA-DEVICE"
    @State private var profilePhotoUri: String? = UserDefaults.standard.string(forKey: "profile_photo_uri")
    @State private var isEditNameDialogOpen: Bool = false
    @State private var editNameInput: String = ""
    @State private var downloadQualityPref: String = UserDefaults.standard.string(forKey: "video_download_quality") ?? "Ask each time"
    @State private var isEunoiaEnabled: Bool = UserDefaults.standard.object(forKey: "eunoia_enabled") == nil ? true : UserDefaults.standard.bool(forKey: "eunoia_enabled")
    @State private var isAddFriendDialogOpen: Bool = false
    @State private var addFriendIdInput: String = ""
    @State private var addFriendNameInput: String = ""
    @State private var selectedLanguages: Set<String> = ["Hindi", "Punjabi", "English"]

    public var onBackClick: () -> Void
    public var onOpenAllFriends: () -> Void
    public var onQuickInviteFriend: (EunoiaFriend) -> Void
    public var onOpenFriendChat: (EunoiaFriend) -> Void

    public init(
        onBackClick: @escaping () -> Void = {},
        onOpenAllFriends: @escaping () -> Void = {},
        onQuickInviteFriend: @escaping (EunoiaFriend) -> Void = { _ in },
        onOpenFriendChat: @escaping (EunoiaFriend) -> Void = { _ in }
    ) {
        self.onBackClick = onBackClick
        self.onOpenAllFriends = onOpenAllFriends
        self.onQuickInviteFriend = onQuickInviteFriend
        self.onOpenFriendChat = onOpenFriendChat
    }

    public var body: some View {
        ZStack {
            Color(hex: 0x0D0D0F).ignoresSafeArea()

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    // Header
                    HStack {
                        SilverBrandHeader(subtitle: "Account & Settings")
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)

                    // 1. Spotify-Style Account Header Tile
                    accountHeaderTile
                        .padding(.horizontal, 16)

                    // 2. Section: Listen Together & Friends
                    friendsSection
                        .padding(.horizontal, 16)

                    // 3. Section: Music Language Preferences
                    languagesSection
                        .padding(.horizontal, 16)

                    // 4. Section: Video Download Quality
                    downloadPreferenceSection
                        .padding(.horizontal, 16)

                    // 5. Section: App Settings
                    appSettingsSection
                        .padding(.horizontal, 16)
                }
                .padding(.bottom, 140)
            }
        }
        .sheet(isPresented: $isEditNameDialogOpen) {
            editNameSheet
        }
        .sheet(isPresented: $isAddFriendDialogOpen) {
            addFriendSheet
        }
    }

    // MARK: - Account Header Tile
    private var accountHeaderTile: some View {
        HStack(spacing: 16) {
            ZStack(alignment: .bottomTrailing) {
                ZStack {
                    if let uri = profilePhotoUri, let url = URL(string: uri) {
                        AsyncImage(url: url) { phase in
                            if let img = phase.image {
                                img.resizable().aspectRatio(contentMode: .fill)
                            } else {
                                Color(hex: 0x1DB954).opacity(0.2)
                            }
                        }
                    } else {
                        Color(hex: 0x1DB954).opacity(0.2)
                        Image(systemName: "person.fill")
                            .font(.system(size: 32))
                            .foregroundColor(Color(hex: 0x1DB954))
                    }
                }
                .frame(width: 64, height: 64)
                .clipShape(Circle())

                Image(systemName: "pencil.circle.fill")
                    .font(.system(size: 20))
                    .foregroundColor(Color(hex: 0x1DB954))
                    .background(Color.black.clipShape(Circle()))
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(username)
                    .font(.system(size: 19, weight: .bold))
                    .foregroundColor(.white)

                Button(action: {
                    UIPasteboard.general.string = visitorId
                }) {
                    HStack(spacing: 4) {
                        Text("@\(visitorId)")
                            .font(.system(size: 12))
                            .foregroundColor(Color.white.opacity(0.6))
                        Image(systemName: "doc.on.doc")
                            .font(.system(size: 10))
                            .foregroundColor(Color.white.opacity(0.6))
                    }
                }
                .buttonStyle(PlainButtonStyle())
            }

            Spacer()

            Button(action: {
                editNameInput = username
                isEditNameDialogOpen = true
            }) {
                Image(systemName: "pencil")
                    .font(.system(size: 16))
                    .foregroundColor(Color.white.opacity(0.6))
            }
        }
        .padding(16)
        .background(Color.white.opacity(0.04))
        .cornerRadius(20)
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
    }

    // MARK: - Friends Section
    private var friendsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("LISTEN TOGETHER & FRIENDS")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(Color(hex: 0x10B981))
                .tracking(1.4)

            VStack(spacing: 12) {
                HStack {
                    Text("My Friends (\(friendManager.friends.count))")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)

                    Spacer()

                    Button(action: { isAddFriendDialogOpen = true }) {
                        HStack(spacing: 4) {
                            Image(systemName: "person.badge.plus")
                                .font(.system(size: 12))
                            Text("Add")
                                .font(.system(size: 12, weight: .bold))
                        }
                        .foregroundColor(.black)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color(hex: 0x10B981))
                        .cornerRadius(14)
                    }
                }

                if friendManager.friends.isEmpty {
                    Text("No friends added yet. Add a friend to listen synchronously via Eunoia.")
                        .font(.system(size: 13))
                        .foregroundColor(Color.white.opacity(0.6))
                        .padding(.vertical, 10)
                } else {
                    ForEach(friendManager.friends.prefix(3)) { friend in
                        HStack(spacing: 12) {
                            ZStack {
                                Color(hex: 0x262636)
                                Text(String(friend.name.prefix(1)).uppercased())
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            .frame(width: 36, height: 36)
                            .clipShape(Circle())

                            VStack(alignment: .leading, spacing: 2) {
                                Text(friend.name)
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white)
                                Text(friend.friendId)
                                    .font(.system(size: 11))
                                    .foregroundColor(Color.white.opacity(0.6))
                            }

                            Spacer()

                            Button(action: { onOpenFriendChat(friend) }) {
                                Image(systemName: "message")
                                    .font(.system(size: 16))
                                    .foregroundColor(Color(hex: 0x10B981))
                                    .frame(width: 32, height: 32)
                            }

                            Button(action: { onQuickInviteFriend(friend) }) {
                                HStack(spacing: 2) {
                                    Image(systemName: "bolt.fill")
                                        .font(.system(size: 10))
                                    Text("Invite")
                                        .font(.system(size: 10, weight: .bold))
                                }
                                .foregroundColor(.black)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color(hex: 0x10B981))
                                .cornerRadius(10)
                            }
                        }
                        .padding(10)
                        .background(Color(hex: 0x14141F))
                        .cornerRadius(14)
                    }
                }

                Button(action: onOpenAllFriends) {
                    Text("View All Friends")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(Color(hex: 0x10B981))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 4)
                }
            }
            .padding(16)
            .background(Color.white.opacity(0.03))
            .cornerRadius(20)
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(Color.white.opacity(0.06), lineWidth: 1)
            )
        }
    }

    // MARK: - Language Preferences
    private var languagesSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("MUSIC LANGUAGES")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(Color.white.opacity(0.6))
                .tracking(1.4)

            let allLangs = ["Hindi", "Punjabi", "English", "South Hits", "Haryanvi", "Bhojpuri", "Gujarati", "Bengali"]
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(allLangs, id: \.self) { lang in
                        let isSel = selectedLanguages.contains(lang)
                        Button(action: {
                            if isSel { selectedLanguages.remove(lang) }
                            else { selectedLanguages.insert(lang) }
                        }) {
                            HStack(spacing: 6) {
                                Text(lang)
                                    .font(.system(size: 12, weight: isSel ? .bold : .medium))
                                    .foregroundColor(isSel ? .black : .white)

                                if isSel {
                                    Image(systemName: "xmark")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(.black)
                                }
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(isSel ? Color.white : Color.white.opacity(0.08))
                            .clipShape(Capsule())
                        }
                    }
                }
            }
        }
    }

    // MARK: - Download Preference
    private var downloadPreferenceSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("DOWNLOAD PREFERENCE")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(Color.white.opacity(0.6))
                .tracking(1.4)

            let options = ["Ask each time", "1080p", "720p", "480p", "360p"]
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(options, id: \.self) { opt in
                        let isSel = downloadQualityPref == opt
                        Button(action: {
                            downloadQualityPref = opt
                            UserDefaults.standard.set(opt, forKey: "video_download_quality")
                        }) {
                            Text(opt)
                                .font(.system(size: 12, weight: isSel ? .bold : .medium))
                                .foregroundColor(isSel ? .black : .white)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(isSel ? Color.white : Color.white.opacity(0.08))
                                .clipShape(Capsule())
                        }
                    }
                }
            }
        }
    }

    // MARK: - App Settings Section
    private var appSettingsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("SYSTEM & STORAGE")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(Color.white.opacity(0.6))
                .tracking(1.4)

            VStack(spacing: 0) {
                // Eunoia Real-time Sync Toggle
                HStack {
                    Image(systemName: "antenna.radiowaves.left.and.right")
                        .foregroundColor(Color(hex: 0x10B981))
                        .frame(width: 28)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Eunoia Real-time Sync")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(.white)
                        Text("Synchronize playback in real time with friends")
                            .font(.system(size: 11))
                            .foregroundColor(Color.white.opacity(0.6))
                    }
                    Spacer()
                    Toggle("", isOn: $isEunoiaEnabled)
                        .labelsHidden()
                        .onChange(of: isEunoiaEnabled) { val in
                            UserDefaults.standard.set(val, forKey: "eunoia_enabled")
                        }
                }
                .padding(16)

                Divider().background(Color.white.opacity(0.08))

                // Clear Offline Storage Cache
                Button(action: {
                    SpasibaAudioCacheManager.shared.clearAll()
                }) {
                    HStack {
                        Image(systemName: "trash")
                            .foregroundColor(Color(hex: 0xEF4444))
                            .frame(width: 28)
                        Text("Clear Audio Cache")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(.white)
                        Spacer()
                        Text("Clear")
                            .font(.system(size: 13))
                            .foregroundColor(Color(hex: 0xEF4444))
                    }
                    .padding(16)
                }

                Divider().background(Color.white.opacity(0.08))

                // Build Version
                HStack {
                    Image(systemName: "info.circle")
                        .foregroundColor(Color.white.opacity(0.6))
                        .frame(width: 28)
                    Text("Spasiba Version")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(.white)
                    Spacer()
                    Text("2.0 Native Swift")
                        .font(.system(size: 13))
                        .foregroundColor(Color.white.opacity(0.6))
                }
                .padding(16)
            }
            .background(Color.white.opacity(0.04))
            .cornerRadius(20)
        }
    }

    // MARK: - Sheets
    private var editNameSheet: some View {
        VStack(spacing: 20) {
            Text("Edit Display Name")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)

            TextField("Display Name", text: $editNameInput)
                .textFieldStyle(PlainTextFieldStyle())
                .padding()
                .background(Color.white.opacity(0.08))
                .cornerRadius(12)
                .foregroundColor(.white)

            HStack(spacing: 12) {
                Button("Cancel") {
                    isEditNameDialogOpen = false
                }
                .foregroundColor(Color.white.opacity(0.6))
                .frame(maxWidth: .infinity)
                .padding()

                Button("Save") {
                    let clean = editNameInput.trimmingCharacters(in: .whitespaces)
                    if !clean.isEmpty {
                        username = clean
                        UserDefaults.standard.set(clean, forKey: "username")
                        isEditNameDialogOpen = false
                    }
                }
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color(hex: 0x1DB954))
                .cornerRadius(12)
            }
            Spacer()
        }
        .padding(24)
        .background(Color(hex: 0x121214).ignoresSafeArea())
    }

    private var addFriendSheet: some View {
        VStack(spacing: 20) {
            Text("Add Friend")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)

            TextField("Friend Device ID (@...)", text: $addFriendIdInput)
                .textFieldStyle(PlainTextFieldStyle())
                .padding()
                .background(Color.white.opacity(0.08))
                .cornerRadius(12)
                .foregroundColor(.white)

            TextField("Friend Name", text: $addFriendNameInput)
                .textFieldStyle(PlainTextFieldStyle())
                .padding()
                .background(Color.white.opacity(0.08))
                .cornerRadius(12)
                .foregroundColor(.white)

            HStack(spacing: 12) {
                Button("Cancel") {
                    isAddFriendDialogOpen = false
                    addFriendIdInput = ""
                    addFriendNameInput = ""
                }
                .foregroundColor(Color.white.opacity(0.6))
                .frame(maxWidth: .infinity)
                .padding()

                Button("Add") {
                    let cleanId = addFriendIdInput.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "@", with: "")
                    let cleanName = addFriendNameInput.trimmingCharacters(in: .whitespaces)
                    if !cleanId.isEmpty && !cleanName.isEmpty {
                        _ = friendManager.addOrSendFriendRequest(friendId: cleanId, name: cleanName)
                        isAddFriendDialogOpen = false
                        addFriendIdInput = ""
                        addFriendNameInput = ""
                    }
                }
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color(hex: 0x10B981))
                .cornerRadius(12)
            }
            Spacer()
        }
        .padding(24)
        .background(Color(hex: 0x121214).ignoresSafeArea())
    }
}
