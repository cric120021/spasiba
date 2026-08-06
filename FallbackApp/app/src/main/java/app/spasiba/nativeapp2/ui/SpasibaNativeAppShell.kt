package app.spasiba.nativeapp2.ui

import app.spasiba.nativeapp2.MainActivity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary

import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp2.data.api.YouTubeMusicApi
import app.spasiba.nativeapp2.data.model.CategoryChip
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.player.NativePlayerManager
import app.spasiba.nativeapp2.ui.components.CreatePlaylistDialog
import app.spasiba.nativeapp2.ui.components.DynamicBackdrop
import app.spasiba.nativeapp2.ui.components.EqualizerSleepTimerModal
import app.spasiba.nativeapp2.ui.components.ExpandedPlayerSheet
import app.spasiba.nativeapp2.ui.components.MiniPlayerDock
import app.spasiba.nativeapp2.ui.components.SaveToPlaylistSheet
import app.spasiba.nativeapp2.ui.components.TrackOptionsMenu
import app.spasiba.nativeapp2.ui.screens.CategoryFeedScreen
import app.spasiba.nativeapp2.ui.screens.EunoiaScreen
import app.spasiba.nativeapp2.ui.screens.FriendsListScreen
import app.spasiba.nativeapp2.ui.screens.HomeScreen
import app.spasiba.nativeapp2.ui.screens.LibraryScreen
import app.spasiba.nativeapp2.ui.screens.PlaylistOperationsScreen
import app.spasiba.nativeapp2.ui.screens.ProfileScreen
import app.spasiba.nativeapp2.ui.screens.SearchScreen
import app.spasiba.nativeapp2.ui.theme.AccentTeal
import app.spasiba.nativeapp2.ui.theme.SpasibaNative2Theme
import app.spasiba.nativeapp2.ui.theme.TextMuted
import app.spasiba.nativeapp2.ui.theme.UiSurfaceElevated
import app.spasiba.nativeapp2.ui.theme.UiThemeMode
import app.spasiba.nativeapp2.ui.theme.getBackgroundGradient
import kotlinx.coroutines.launch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateListOf

@Composable
fun SpasibaNativeAppShell(playerManager: NativePlayerManager, initialScreen: Int = 0) {
    val backStack = remember { mutableStateListOf(initialScreen) } // 0 = Home, 1 = Search, 2 = Eunoia, 3 = Library, 4 = Profile, 5 = Playlists
    val selectedScreen = backStack.lastOrNull() ?: 0
    val currentScreen = selectedScreen

    var selectedCategory by remember { mutableStateOf<CategoryChip?>(null) }
    var activeChatFriend by remember { mutableStateOf<app.spasiba.nativeapp2.data.engine.EunoiaFriend?>(null) }
    var selectedArtistName by remember { mutableStateOf<String?>(null) }

    fun navigateTo(screen: Int) {
        selectedCategory = null
        if (screen == 0) {
            backStack.clear()
            backStack.add(0)
        } else if (backStack.lastOrNull() != screen) {
            backStack.add(screen)
        }
    }

    val requestedTab by MainActivity.requestedTab.collectAsState()
    val requestedChatFriendId by MainActivity.requestedChatFriendId.collectAsState()
    val requestedChatFriendName by MainActivity.requestedChatFriendName.collectAsState()

    androidx.compose.runtime.LaunchedEffect(requestedTab, requestedChatFriendId) {
        if (!requestedChatFriendId.isNullOrBlank()) {
            activeChatFriend = app.spasiba.nativeapp2.data.engine.EunoiaFriend(
                friendId = requestedChatFriendId!!,
                name = requestedChatFriendName ?: requestedChatFriendId!!
            )
            navigateTo(7)
            MainActivity.requestedChatFriendId.value = null
            MainActivity.requestedChatFriendName.value = null
            MainActivity.requestedTab.value = null
        } else {
            requestedTab?.let { target ->
                navigateTo(target)
                MainActivity.requestedTab.value = null
            }
        }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    // Intercept System Hardware Back Key to navigate stack
    BackHandler(enabled = backStack.size > 1) {
        navigateBack()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    var currentThemeMode by remember { mutableStateOf(UiThemeMode.MODERN_GLASS) }
    var isExpandedPlayerVisible by remember { mutableStateOf(false) }

    var selectedOptionsMenuSong by remember { mutableStateOf<Song?>(null) }
    var selectedSaveToPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var isCreatePlaylistDialogOpen by remember { mutableStateOf(false) }
    var isEqualizerModalOpen by remember { mutableStateOf(false) }

    var currentSpeed by remember { mutableStateOf(1.0f) }
    var currentSleepTimerMinutes by remember { mutableIntStateOf(0) }

    val currentSong by playerManager.currentSong.collectAsState()
    val queue by playerManager.queue.collectAsState()
    val currentIndex by playerManager.currentIndex.collectAsState()
    val queueSourceLabel by playerManager.queueSourceLabel.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isLoading by playerManager.isLoading.collectAsState()
    val positionMs by playerManager.positionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val repeatState by playerManager.repeatState.collectAsState()
    val likedSongIds by playerManager.likedSongIds.collectAsState()
    val likedSongsList by playerManager.repository.likedSongs.collectAsState(initial = emptyList())
    val playlists by playerManager.repository.playlists.collectAsState(initial = emptyList())
    val historySongs by playerManager.repository.listeningHistory.collectAsState(initial = emptyList())
    val downloadManager = remember(context) { app.spasiba.nativeapp2.data.download.SpasibaDownloadManager.getInstance(context) }

    val scope = rememberCoroutineScope()
    var homeSongs by remember { mutableStateOf(YouTubeMusicApi.getFallbackTracks()) }
    var isHomeLoading by remember { mutableStateOf(false) }

    val fcmManager = remember { app.spasiba.nativeapp2.data.engine.FcmTokenManager.getInstance(context) }
    val isDeviceBlocked by fcmManager.isDeviceBlocked.collectAsState()
    val blockReason by fcmManager.blockReason.collectAsState()
    val isEunoiaDisabled by fcmManager.isEunoiaDisabled.collectAsState()
    val isMobileRequired by fcmManager.isMobileRequired.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        fcmManager.initialize(scope)
    }
    var selectedUserLanguages by remember { mutableStateOf(setOf("Hindi", "Punjabi", "English")) }

    val sessionManager = remember { app.spasiba.nativeapp2.data.engine.EunoiaSessionManager.getInstance(context) }
    androidx.compose.runtime.LaunchedEffect(currentScreen, activeChatFriend) {
        sessionManager.activeScreen.value = currentScreen
        sessionManager.activeChatFriendId.value = activeChatFriend?.friendId
    }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var detectedArtistResult by remember { mutableStateOf<YouTubeMusicApi.ArtistSearchResult?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Fetch initial home recommendations
    remember {
        scope.launch {
            isHomeLoading = true
            val fetched = YouTubeMusicApi.getHomeRecommendations()
            homeSongs = fetched
            isHomeLoading = false
        }
    }

    fun cycleTheme() {
        currentThemeMode = when (currentThemeMode) {
            UiThemeMode.MODERN_GLASS -> UiThemeMode.OLED_DARK
            UiThemeMode.OLED_DARK -> UiThemeMode.CLASSIC_MIDNIGHT
            UiThemeMode.CLASSIC_MIDNIGHT -> UiThemeMode.NEON_CYBERPUNK
            UiThemeMode.NEON_CYBERPUNK -> UiThemeMode.MODERN_GLASS
        }
    }

    SpasibaNative2Theme(mode = currentThemeMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(getBackgroundGradient(currentThemeMode))
        ) {
            val activeSong = currentSong
            val progressFraction = if (durationMs > 0L) {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
            val posSec = positionMs / 1000
            val durSec = durationMs / 1000
            val posFormatted = String.format("%d:%02d", posSec / 60, posSec % 60)
            val durFormatted = String.format("%d:%02d", durSec / 60, durSec % 60)
            val upcomingCount = (queue.size - currentIndex - 1).coerceAtLeast(0)

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = if (selectedScreen == 2) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        // 1. Mini Player attached directly above Bottom Navigation (only when a song is active)
                        if (activeSong != null) {
                            MiniPlayerDock(
                                song = activeSong,
                                isPlaying = isPlaying,
                                progress = progressFraction,
                                positionFormatted = posFormatted,
                                durationFormatted = durFormatted,
                                queueCount = upcomingCount,
                                onDockClick = { isExpandedPlayerVisible = true },
                                onPlayPauseClick = { playerManager.togglePlayPause() },
                                onPreviousClick = { playerManager.playPrevious() },
                                onNextClick = { playerManager.playNext() },
                                onOptionsClick = { selectedOptionsMenuSong = activeSong }
                            )
                        }

                            // 2. YT Music Official Compact Bottom Navigation Bar (Elevated Text & Icons)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .background(Color.Black)
                                    .padding(top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val navItems = listOf(
                                    Triple(0, "Home", Pair(Icons.Rounded.Home, Icons.Outlined.Home)),
                                    Triple(1, "Search", Pair(Icons.Rounded.Search, Icons.Outlined.Search)),
                                    Triple(2, "Eunoia", Pair(Icons.Rounded.GraphicEq, Icons.Outlined.GraphicEq)),
                                    Triple(3, "Library", Pair(Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder))
                                )

                                navItems.forEach { (screenId, label, icons) ->
                                    val isSelected = selectedScreen == screenId
                                    val activeIcon = if (isSelected) icons.first else icons.second

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                if (screenId == 2) {
                                                    if (isEunoiaDisabled) {
                                                        android.widget.Toast.makeText(context, "Eunoia feature access has been restricted for your account by administrator.", android.widget.Toast.LENGTH_LONG).show()
                                                        return@clickable
                                                    }
                                                }
                                                navigateTo(screenId)
                                            }
                                            .offset(y = (-6).dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = activeIcon,
                                            contentDescription = label,
                                            tint = if (isSelected) Color.White else Color(0xFFAAAAAA),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color(0xFFAAAAAA),
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.offset(y = (-1).dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        val homeListState = androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
                            androidx.compose.foundation.lazy.LazyListState()
                        }

                        // Active Screen Content
                        when (selectedScreen) {
                            0 -> HomeScreen(
                                recommendedSongs = homeSongs,
                                currentSong = currentSong,
                                likedSongs = likedSongIds,
                                recentlyPlayed = historySongs,
                                selectedLanguages = selectedUserLanguages,
                                isLoading = isHomeLoading,
                                selectedCategory = selectedCategory,
                                onCategorySelect = { cat ->
                                    selectedCategory = cat
                                },
                                onSearchClick = { navigateTo(1) },
                                onNotificationsClick = { navigateTo(10) },
                                onLibraryClick = { navigateTo(3) },
                                onProfileClick = { navigateTo(4) },
                                onSongClick = { song, queueList, idx ->
                                    playerManager.playQueue(queueList, idx, sourceLabel = "Home")
                                },
                                onLikeClick = { songId ->
                                    val s = (homeSongs + historySongs).find { it.id == songId } ?: Song(songId, "Track", "Artist", "https://i.ytimg.com/vi/$songId/hqdefault.jpg")
                                    playerManager.toggleLike(s)
                                },
                                onOptionsClick = { song -> selectedOptionsMenuSong = song },
                                onSwipeToQueue = { song -> playerManager.playNextInQueue(song) },
                                onArtistClick = { artistName ->
                                    selectedArtistName = artistName
                                    navigateTo(9)
                                },
                                listState = homeListState
                            )

                            1 -> SearchScreen(
                                    query = searchQuery,
                                    onQueryChange = { q ->
                                        searchQuery = q
                                        searchJob?.cancel()
                                        if (q.trim().isNotEmpty()) {
                                            searchJob = scope.launch {
                                                delay(500L)
                                                isSearching = true
                                                val unified = YouTubeMusicApi.searchUnified(q)
                                                searchResults = unified.songs
                                                detectedArtistResult = unified.detectedArtist
                                                isSearching = false
                                            }
                                        } else {
                                            searchResults = emptyList()
                                            detectedArtistResult = null
                                            isSearching = false
                                        }
                                    },
                                    onSearch = { q ->
                                        searchJob?.cancel()
                                        if (q.isNotBlank()) {
                                            app.spasiba.nativeapp2.data.cache.SearchHistoryManager.getInstance(context).addTextQuery(q)
                                            searchJob = scope.launch {
                                                isSearching = true
                                                val unified = YouTubeMusicApi.searchUnified(q)
                                                searchResults = unified.songs
                                                detectedArtistResult = unified.detectedArtist
                                                isSearching = false
                                            }
                                        }
                                    },
                                    searchResults = searchResults,
                                    detectedArtist = detectedArtistResult,
                                    currentSong = currentSong,
                                    likedSongs = likedSongIds,
                                    searchHistory = searchHistory,
                                    isSearching = isSearching,
                                    onDeleteHistoryItem = { item ->
                                        app.spasiba.nativeapp2.data.cache.SearchHistoryManager.getInstance(context).deleteTextQuery(item)
                                    },
                                    onClearHistory = {
                                        app.spasiba.nativeapp2.data.cache.SearchHistoryManager.getInstance(context).clearAllHistory()
                                    },
                                    onSongClick = { song, _, _ ->
                                        app.spasiba.nativeapp2.data.cache.SearchHistoryManager.getInstance(context).addPlayedSong(song)
                                        playerManager.playSingleSongWithRadio(song, sourceLabel = "Search Radio")
                                    },
                                    onLikeClick = { songId ->
                                        val s = (searchResults + homeSongs).find { it.id == songId } ?: Song(songId, "Track", "Artist", "https://i.ytimg.com/vi/$songId/hqdefault.jpg")
                                        playerManager.toggleLike(s)
                                    },
                                    onOptionsClick = { song -> selectedOptionsMenuSong = song },
                                    onSwipeToQueue = { song -> playerManager.playNextInQueue(song) },
                                    onArtistClick = { artist ->
                                        val avatar = detectedArtistResult?.avatarUrl
                                        app.spasiba.nativeapp2.data.cache.SearchHistoryManager.getInstance(context).addRecentArtist(artist, avatar)
                                        selectedArtistName = artist
                                        navigateTo(9)
                                    },
                                    onBackClick = { navigateBack() }
                                )

                                2 -> app.spasiba.nativeapp2.ui.screens.EunoiaScreen(
                                    playerManager = playerManager,
                                    onSongClick = { song, queueList, idx ->
                                        playerManager.playQueue(queueList, idx, sourceLabel = "Eunoia Listen Together")
                                    }
                                )

                            3 -> LibraryScreen(
                                likedSongs = likedSongsList,
                                playlists = playlists,
                                historySongs = historySongs,
                                currentSong = currentSong,
                                repository = playerManager.repository,
                                downloadManager = downloadManager,
                                onCreatePlaylistClick = { isCreatePlaylistDialogOpen = true },
                                onSongClick = { song, queueList, idx ->
                                    playerManager.playQueue(queueList, idx, sourceLabel = "Library")
                                },
                                onLikeClick = { songId ->
                                    val s = (homeSongs + searchResults).find { it.id == songId } ?: Song(songId, "Track", "Artist", "https://i.ytimg.com/vi/$songId/hqdefault.jpg")
                                    playerManager.toggleLike(s)
                                },
                                onOptionsClick = { song -> selectedOptionsMenuSong = song },
                                onSwipeToQueue = { song -> playerManager.playNextInQueue(song) },
                                onBackClick = { navigateBack() }
                            )

                            4 -> ProfileScreen(
                                playerManager = playerManager,
                                selectedLanguages = selectedUserLanguages,
                                onToggleLanguage = { lang ->
                                    selectedUserLanguages = if (selectedUserLanguages.contains(lang)) {
                                        if (selectedUserLanguages.size > 1) selectedUserLanguages - lang else selectedUserLanguages
                                    } else {
                                        selectedUserLanguages + lang
                                    }
                                },
                                onSongClick = { song ->
                                    playerManager.playQueue(listOf(song), 0)
                                },
                                onBackClick = { navigateBack() },
                                onOpenLibrary = { navigateTo(3) },
                                onOpenPlaylistOperations = { navigateTo(5) },
                                onOpenAllFriends = { navigateTo(6) },
                                onQuickInviteFriend = { friend ->
                                    val sessionManager = app.spasiba.nativeapp2.data.engine.EunoiaSessionManager.getInstance(context)
                                    sessionManager.sendInvite(friend.friendId, friend.name)
                                    navigateTo(2)
                                    Toast.makeText(context, "Sending invite to ${friend.name}...", Toast.LENGTH_SHORT).show()
                                },
                                onOpenFriendChat = { friend ->
                                    activeChatFriend = friend
                                    navigateTo(7)
                                }
                            )

                            5 -> PlaylistOperationsScreen(
                                playerManager = playerManager,
                                onBackClick = { navigateBack() }
                            )

                            6 -> FriendsListScreen(
                                onBackClick = { navigateBack() },
                                onQuickInviteFriend = { friend ->
                                    val sessionManager = app.spasiba.nativeapp2.data.engine.EunoiaSessionManager.getInstance(context)
                                    sessionManager.sendInvite(friend.friendId, friend.name)
                                    navigateTo(2)
                                    Toast.makeText(context, "Sending invite to ${friend.name}...", Toast.LENGTH_SHORT).show()
                                },
                                onOpenFriendChat = { friend ->
                                    activeChatFriend = friend
                                    navigateTo(7)
                                }
                            )

                            7 -> {
                                activeChatFriend?.let { friend ->
                                    val sessionManager = app.spasiba.nativeapp2.data.engine.EunoiaSessionManager.getInstance(context)
                                    app.spasiba.nativeapp2.ui.screens.FriendChatScreen(
                                        friendId = friend.friendId,
                                        friendName = friend.name,
                                        onBackClick = { navigateBack() },
                                        onOpenDetails = { navigateTo(8) },
                                        onRemoveFriend = { targetId ->
                                            sessionManager.removeFriendSignal(targetId)
                                        },
                                        onQuickInvite = { targetFriend ->
                                            sessionManager.sendInvite(targetFriend.friendId, targetFriend.name)
                                            navigateTo(2)
                                            Toast.makeText(context, "Sending invite to ${targetFriend.name}...", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }

                            8 -> {
                                activeChatFriend?.let { friend ->
                                    val sessionManager = app.spasiba.nativeapp2.data.engine.EunoiaSessionManager.getInstance(context)
                                    app.spasiba.nativeapp2.ui.screens.FriendProfileDetailsScreen(
                                        friendId = friend.friendId,
                                        friendName = friend.name,
                                        onBackClick = { navigateBack() },
                                        onOpenChat = { navigateBack() },
                                        onRemoveFriend = { targetId ->
                                            sessionManager.removeFriendSignal(targetId)
                                        },
                                        onQuickInvite = { targetFriend ->
                                            sessionManager.sendInvite(targetFriend.friendId, targetFriend.name)
                                            navigateTo(2)
                                            Toast.makeText(context, "Sending invite to ${targetFriend.name}...", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }

                            9 -> {
                                selectedArtistName?.let { artist ->
                                    app.spasiba.nativeapp2.ui.screens.ArtistDetailsScreen(
                                        artistName = artist,
                                        currentSong = currentSong,
                                        likedSongs = likedSongIds,
                                        onBackClick = { navigateBack() },
                                        onSongClick = { song, queueList, idx ->
                                            playerManager.playQueue(queueList, idx, sourceLabel = "Artist: $artist")
                                        },
                                        onLikeClick = { songId ->
                                            val s = (homeSongs + historySongs).find { it.id == songId } ?: Song(songId, "Track", artist, "https://i.ytimg.com/vi/$songId/hqdefault.jpg")
                                            playerManager.toggleLike(s)
                                        },
                                        onOptionsClick = { song -> selectedOptionsMenuSong = song }
                                    )
                                }
                            }

                            10 -> app.spasiba.nativeapp2.ui.screens.NotificationsScreen(
                                onBackClick = { navigateBack() },
                                onNavigateToEunoia = { navigateTo(2) },
                                onProfileClick = { navigateTo(4) }
                            )
                        }

                        // Context Option Sheets & Modals
                        selectedOptionsMenuSong?.let { song ->
                            TrackOptionsMenu(
                                song = song,
                                isLiked = likedSongIds.contains(song.id),
                                onDismiss = { selectedOptionsMenuSong = null },
                                onLikeToggle = { playerManager.toggleLike(song) },
                                onAddToPlaylist = {
                                    selectedSaveToPlaylistSong = song
                                    selectedOptionsMenuSong = null
                                },
                                onPlayNext = { playerManager.playNextInQueue(song) },
                                onAddToQueue = { playerManager.playNextInQueue(song) },
                                onDownloadTrack = { targetSong, type, quality ->
                                    downloadManager.startDownload(targetSong, type, quality)
                                    Toast.makeText(context, "📥 Download started for '${targetSong.title}'", Toast.LENGTH_SHORT).show()
                                },
                                onViewArtist = { artistName ->
                                    selectedOptionsMenuSong = null
                                    selectedArtistName = artistName
                                    navigateTo(9)
                                }
                            )
                        }

                        selectedSaveToPlaylistSong?.let { song ->
                            SaveToPlaylistSheet(
                                song = song,
                                playlists = playlists,
                                onDismiss = { selectedSaveToPlaylistSong = null },
                                onSelectPlaylist = { targetPlaylist ->
                                    scope.launch {
                                        playerManager.repository.addSongToPlaylist(targetPlaylist.id, song)
                                        Toast.makeText(context, "Saved to \"${targetPlaylist.name}\"", Toast.LENGTH_SHORT).show()
                                    }
                                    selectedSaveToPlaylistSong = null
                                },
                                onCreateNewPlaylist = {
                                    isCreatePlaylistDialogOpen = true
                                    selectedSaveToPlaylistSong = null
                                }
                            )
                        }

                        if (isCreatePlaylistDialogOpen) {
                            CreatePlaylistDialog(
                                onDismiss = { isCreatePlaylistDialogOpen = false },
                                onCreate = { name ->
                                    scope.launch {
                                        playerManager.repository.createPlaylist(name)
                                        isCreatePlaylistDialogOpen = false
                                    }
                                }
                            )
                        }

                        if (isEqualizerModalOpen) {
                            EqualizerSleepTimerModal(
                                currentSpeed = currentSpeed,
                                currentSleepTimerMinutes = currentSleepTimerMinutes,
                                onDismiss = { isEqualizerModalOpen = false },
                                onSelectSpeed = { speed ->
                                    currentSpeed = speed
                                    playerManager.exoPlayer.setPlaybackSpeed(speed)
                                },
                                onSelectSleepTimer = { mins ->
                                    currentSleepTimerMinutes = mins
                                    isEqualizerModalOpen = false
                                }
                            )
                        }
                    }
                }

                // Full Screen Maximized Expanded Player Sheet Overlay (100% Screen Coverage)
                AnimatedVisibility(
                    visible = isExpandedPlayerVisible && activeSong != null,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (activeSong != null) {
                        ExpandedPlayerSheet(
                            playerManager = playerManager,
                            song = activeSong,
                            isPlaying = isPlaying,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            isLiked = likedSongIds.contains(activeSong.id),
                            onCollapse = { isExpandedPlayerVisible = false },
                            onPlayPauseClick = { playerManager.togglePlayPause() },
                            onPreviousClick = { playerManager.playPrevious() },
                            onNextClick = { playerManager.playNext() },
                            onSeekTo = { pos -> playerManager.seekTo(pos) },
                            onLikeClick = { playerManager.toggleLike(activeSong) },
                            onOptionsClick = { targetSong -> selectedSaveToPlaylistSong = targetSong },
                            onSongClickInQueue = { qSong -> playerManager.playSingleSongWithRadio(qSong) },
                            repeatState = repeatState,
                            onRepeatClick = { playerManager.toggleRepeat() }
                        )
                    }
                }

                // Full Screen Blocked Overlay (Overrides interface only if blocked by Admin)
                if (isDeviceBlocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F0505))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Blocked",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "ACCESS RESTRICTED",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = blockReason,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                            ) {
                                Text(
                                    text = "Device ID: ${fcmManager.deviceId.value}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
