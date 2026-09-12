import SwiftUI

public enum SpasibaTab: Int, CaseIterable, Identifiable {
    case home = 0
    case search = 1
    case eunoia = 2
    case library = 3

    public var id: Int { rawValue }

    public var title: String {
        switch self {
        case .home: return "Home"
        case .search: return "Search"
        case .eunoia: return "Eunoia"
        case .library: return "Library"
        }
    }

    public func iconName(isSelected: Bool) -> String {
        switch self {
        case .home:
            return isSelected ? "house.fill" : "house"
        case .search:
            return "magnifyingglass"
        case .eunoia:
            return isSelected ? "waveform.circle.fill" : "waveform"
        case .library:
            return isSelected ? "bookmark.fill" : "bookmark"
        }
    }
}

public struct SpasibaAppShell: View {
    @ObservedObject var player = NativePlayerManager.shared
    @ObservedObject var db = SpasibaDatabase.shared

    // Navigation Stack matching SpasibaNativeAppShell.kt backStack
    // 0 = Home, 1 = Search, 2 = Eunoia, 3 = Library, 4 = Profile,
    // 5 = Playlists, 6 = Friends, 7 = FriendChat, 8 = FriendDetails,
    // 9 = ArtistDetails, 10 = Notifications
    @State private var backStack: [Int] = [0]
    @State private var selectedCategory: CategoryChip? = nil
    @State private var activeChatFriend: EunoiaFriend? = nil
    @State private var selectedArtistName: String? = nil

    // Sheets and Modals
    @State private var isExpandedPlayerVisible: Bool = false
    @State private var selectedOptionsMenuSong: Song? = nil
    @State private var selectedSaveToPlaylistSong: Song? = nil
    @State private var pendingSongForNewPlaylist: Song? = nil
    @State private var isCreatePlaylistDialogOpen: Bool = false
    @State private var isEqualizerModalOpen: Bool = false
    @State private var isKeyboardVisible: Bool = false

    public init(initialScreen: Int = 0) {
        _backStack = State(initialValue: [initialScreen])
    }

    private var currentScreen: Int {
        backStack.last ?? 0
    }

    public func navigateTo(_ screen: Int) {
        selectedCategory = nil
        if screen == 0 {
            backStack = [0]
        } else if backStack.last != screen {
            backStack.append(screen)
        }
    }

    public func navigateBack() {
        if selectedCategory != nil {
            selectedCategory = nil
        } else if backStack.count > 1 {
            backStack.removeLast()
        }
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            SpasibaTheme.background.ignoresSafeArea()

            // 1. Main Navigation Graph Content Area
            ZStack {
                if let cat = selectedCategory {
                    CategoryFeedScreen(
                        category: cat,
                        currentSong: player.currentSong,
                        likedSongs: player.likedSongIds,
                        onBackClick: { selectedCategory = nil },
                        onSongClick: { song, list, idx in
                            player.playQueue(list, startIndex: idx, sourceLabel: "Category: \(cat.label)")
                        },
                        onLikeClick: { songId in
                            if let song = (player.queue).first(where: { $0.id == songId }) ?? player.currentSong {
                                player.toggleLike(song)
                            }
                        },
                        onOptionsClick: { song in
                            selectedOptionsMenuSong = song
                        },
                        onSwipeToQueue: { song in
                            player.playNextInQueue(song)
                        }
                    )
                } else {
                    // 1. Core Primary Navigation Tabs (0: Home, 1: Search, 2: Eunoia, 3: Library)
                    // Retained permanently in memory to prevent view destruction, re-fetching, and artwork reloading.
                    ZStack {
                        HomeScreen(
                            onSearchClick: { navigateTo(1) },
                            onLibraryClick: { navigateTo(3) },
                            onProfileClick: { navigateTo(4) },
                            onNotificationsClick: { navigateTo(10) },
                            onArtistClick: { artist in
                                selectedArtistName = artist
                                navigateTo(9)
                            },
                            onSongClick: { song, list, idx in
                                player.playQueue(list, startIndex: idx, sourceLabel: "Home")
                            },
                            onLikeClick: { songId in
                                if let s = (player.queue).first(where: { $0.id == songId }) ?? player.currentSong {
                                    player.toggleLike(s)
                                }
                            },
                            onOptionsClick: { song in
                                selectedOptionsMenuSong = song
                            }
                        )
                        .opacity(currentScreen == 0 ? 1 : 0)
                        .allowsHitTesting(currentScreen == 0)

                        SearchScreen(
                            onArtistClick: { artist in
                                selectedArtistName = artist
                                navigateTo(9)
                            }
                        )
                        .opacity(currentScreen == 1 ? 1 : 0)
                        .allowsHitTesting(currentScreen == 1)

                        EunoiaScreen()
                            .opacity(currentScreen == 2 ? 1 : 0)
                            .allowsHitTesting(currentScreen == 2)

                        LibraryScreen()
                            .opacity(currentScreen == 3 ? 1 : 0)
                            .allowsHitTesting(currentScreen == 3)
                    }

                    // 2. Secondary & Detail Screens (Screen Index >= 4)
                    if currentScreen >= 4 {
                        switch currentScreen {
                        case 4:
                            ProfileScreen()

                        case 5:
                            PlaylistOperationsScreen(
                                playerManager: player,
                                onBackClick: { navigateBack() }
                            )

                        case 6:
                            FriendsListScreen(
                                onBackClick: { navigateBack() },
                                onQuickInviteFriend: { friend in
                                    EunoiaSessionManager.shared.quickInvite(friend: friend)
                                    navigateTo(2)
                                },
                                onOpenFriendChat: { friend in
                                    activeChatFriend = friend
                                    navigateTo(7)
                                }
                            )

                        case 7:
                            if let friend = activeChatFriend {
                                FriendChatScreen(
                                    friendId: friend.friendId,
                                    friendName: friend.name,
                                    onBackClick: { navigateBack() },
                                    onOpenDetails: { navigateTo(8) },
                                    onRemoveFriend: { targetId in
                                        EunoiaSessionManager.shared.removeFriendSignal(friendId: targetId)
                                    },
                                    onQuickInvite: { targetFriend in
                                        EunoiaSessionManager.shared.sendInvite(friendId: targetFriend.friendId, friendName: targetFriend.name)
                                        navigateTo(2)
                                    }
                                )
                            } else {
                                EmptyView()
                            }

                        case 8:
                            if let friend = activeChatFriend {
                                FriendProfileDetailsScreen(
                                    friendId: friend.friendId,
                                    friendName: friend.name,
                                    onBackClick: { navigateBack() },
                                    onOpenChat: { navigateBack() },
                                    onRemoveFriend: { targetId in
                                        EunoiaSessionManager.shared.removeFriendSignal(friendId: targetId)
                                    },
                                    onQuickInvite: { targetFriend in
                                        EunoiaSessionManager.shared.sendInvite(friendId: targetFriend.friendId, friendName: targetFriend.name)
                                        navigateTo(2)
                                    }
                                )
                            } else {
                                EmptyView()
                            }

                        case 9:
                            ArtistDetailsScreen(
                                artistName: selectedArtistName ?? "Artist",
                                currentSong: player.currentSong,
                                likedSongs: player.likedSongIds,
                                onBackClick: { navigateBack() },
                                onSongClick: { song, list, idx in
                                    player.playQueue(list, startIndex: idx, sourceLabel: "Artist: \(selectedArtistName ?? "")")
                                },
                                onLikeClick: { songId in
                                    if let s = (player.queue).first(where: { $0.id == songId }) ?? player.currentSong {
                                        player.toggleLike(s)
                                    }
                                },
                                onOptionsClick: { song in
                                    selectedOptionsMenuSong = song
                                }
                            )

                        case 10:
                            NotificationsScreen(
                                onBackClick: { navigateBack() },
                                onNavigateToRoom: { _ in navigateTo(2) },
                                onNavigateToEunoia: { navigateTo(2) },
                                onProfileClick: { navigateTo(4) }
                            )

                        default:
                            EmptyView()
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            // 2. Bottom Navigation Panel (MiniPlayer + Tab Bar)
            // Anchored at the true bottom of the screen, never pushed over the keyboard
            if !isKeyboardVisible {
                bottomFloatingBar
                    .ignoresSafeArea(.keyboard, edges: .bottom)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
        .ignoresSafeArea(.keyboard, edges: .bottom)
        // Modals and Sheets
        .fullScreenCover(isPresented: $isExpandedPlayerVisible) {
            ExpandedPlayerSheet()
        }
        .sheet(item: $selectedOptionsMenuSong) { song in
            TrackOptionsMenu(
                song: song,
                isLiked: player.likedSongIds.contains(song.id),
                onDismiss: { selectedOptionsMenuSong = nil },
                onLikeToggle: { player.toggleLike(song) },
                onAddToPlaylist: {
                    selectedSaveToPlaylistSong = song
                    selectedOptionsMenuSong = nil
                },
                onPlayNow: {
                    player.playQueue([song], startIndex: 0)
                    selectedOptionsMenuSong = nil
                },
                onPlayNext: {
                    player.playNextInQueue(song)
                    selectedOptionsMenuSong = nil
                },
                onAddToQueue: {
                    player.playNextInQueue(song)
                    selectedOptionsMenuSong = nil
                },
                onViewArtist: { artist in
                    selectedOptionsMenuSong = nil
                    selectedArtistName = artist
                    navigateTo(9)
                }
            )
        }
        .sheet(item: $selectedSaveToPlaylistSong) { song in
            SaveToPlaylistSheet(
                song: song,
                playlists: db.playlists,
                onDismiss: { selectedSaveToPlaylistSong = nil },
                onSelectPlaylist: { targetPlaylist in
                    Task {
                        await MusicRepository.shared.addSongToPlaylist(playlistId: targetPlaylist.id, song: song)
                    }
                    selectedSaveToPlaylistSong = nil
                },
                onCreateNewPlaylist: {
                    pendingSongForNewPlaylist = song
                    isCreatePlaylistDialogOpen = true
                    selectedSaveToPlaylistSong = nil
                }
            )
        }
        .overlay {
            if isCreatePlaylistDialogOpen {
                CreatePlaylistDialog(
                    onDismiss: {
                        isCreatePlaylistDialogOpen = false
                        pendingSongForNewPlaylist = nil
                    },
                    onCreate: { name in
                        Task {
                            let newPlaylist = await MusicRepository.shared.createPlaylist(name: name)
                            if let pendingSong = pendingSongForNewPlaylist {
                                await MusicRepository.shared.addSongToPlaylist(playlistId: newPlaylist.id, song: pendingSong)
                                pendingSongForNewPlaylist = nil
                            }
                            await MainActor.run {
                                isCreatePlaylistDialogOpen = false
                            }
                        }
                    }
                )
            }
        }
        .allowsHitTesting(true)
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            withAnimation(.easeOut(duration: 0.15)) {
                isKeyboardVisible = true
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            withAnimation(.easeOut(duration: 0.2)) {
                isKeyboardVisible = false
            }
        }
    }

    // MARK: - Bottom Floating MiniPlayerDock & Official Compact Bottom Navigation Bar
    private var bottomFloatingBar: some View {
        VStack(spacing: 0) {
            // 1. Mini Player attached directly above Bottom Navigation
            if let activeSong = player.currentSong {
                MiniPlayerDock(
                    song: activeSong,
                    isPlaying: player.isPlaying,
                    progress: player.playbackProgress,
                    onDockClick: {
                        isExpandedPlayerVisible = true
                    },
                    onPlayPauseClick: {
                        player.togglePlayPause()
                    },
                    onNextClick: {
                        player.playNext()
                    }
                )
                .frame(maxWidth: 680)
                .padding(.bottom, 6)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            // 2. YT Music Official Compact Bottom Navigation Bar (Home, Search, Eunoia, Library)
            HStack(spacing: 0) {
                ForEach(SpasibaTab.allCases) { tab in
                    let isSelected = currentScreen == tab.rawValue && selectedCategory == nil

                    Button(action: {
                        navigateTo(tab.rawValue)
                    }) {
                        VStack(spacing: 2) {
                            Image(systemName: tab.iconName(isSelected: isSelected))
                                .font(.system(size: 20, weight: isSelected ? .bold : .regular))
                                .frame(width: 28, height: 28)
                                .foregroundColor(isSelected ? .white : Color(hex: 0xAAAAAA))

                            Text(tab.title)
                                .font(.system(
                                    size: 11,
                                    weight: isSelected ? .bold : .medium
                                ))
                                .foregroundColor(isSelected ? .white : Color(hex: 0xAAAAAA))
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .frame(maxWidth: 680)
            .frame(height: 52)
        }
        .frame(maxWidth: .infinity)
        .background(
            Color.black
                .ignoresSafeArea(edges: .bottom)
        )
    }
}
