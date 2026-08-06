package app.spasiba.nativeapp2.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp2.data.cache.SearchHistoryManager
import app.spasiba.nativeapp2.data.db.PlaylistEntity
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.data.repository.MusicRepository
import app.spasiba.nativeapp2.ui.components.SilverBrandHeader
import app.spasiba.nativeapp2.ui.components.SongTile
import app.spasiba.nativeapp2.ui.theme.AccentTeal
import app.spasiba.nativeapp2.ui.theme.TextMuted
import app.spasiba.nativeapp2.ui.theme.TextStrong
import app.spasiba.nativeapp2.ui.theme.UiSurfaceElevated
import app.spasiba.nativeapp2.utils.ShareUtils
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    likedSongs: List<Song>,
    playlists: List<PlaylistEntity>,
    historySongs: List<Song>,
    currentSong: Song?,
    repository: MusicRepository? = null,
    downloadManager: app.spasiba.nativeapp2.data.download.SpasibaDownloadManager? = null,
    initialTab: Int = 0,
    onCreatePlaylistClick: () -> Unit,
    onSongClick: (Song, List<Song>, Int) -> Unit,
    onLikeClick: (String) -> Unit,
    onOptionsClick: (Song) -> Unit = {},
    onSwipeToQueue: (Song) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) } // 0 = Playlists, 1 = Liked, 2 = History, 3 = Downloads
    val context = LocalContext.current
    val historyManager = remember { SearchHistoryManager.getInstance(context) }
    var activePlaylistDetail by remember { mutableStateOf<PlaylistEntity?>(null) }
    var selectedPlaylistForOptions by remember { mutableStateOf<PlaylistEntity?>(null) }
    var isImportDialogOpen by remember { mutableStateOf(false) }
    var songToDeleteFromOffline by remember { mutableStateOf<Song?>(null) }
    var songToRemoveFromHistory by remember { mutableStateOf<Song?>(null) }

    val scope = rememberCoroutineScope()

    if (activePlaylistDetail != null && repository != null) {
        val playlist = activePlaylistDetail!!
        val playlistSongsFlow = remember(playlist.id) { repository.getSongsForPlaylist(playlist.id) }
        val playlistSongs by playlistSongsFlow.collectAsState(initial = emptyList())

        // Dedicated YouTube Music Grade Playlist Detail View
        val coverUrl = playlistSongs.firstOrNull { it.thumbnail.isNotBlank() }?.thumbnail

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { activePlaylistDetail = null }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Playlist",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = TextMuted,
                        fontSize = 14.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { ShareUtils.sharePlaylist(context, playlist.name, playlistSongs, playlist.shareCode) }) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                // Hero Banner
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF262626)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!coverUrl.isNullOrBlank()) {
                                coil.compose.AsyncImage(
                                    model = coverUrl,
                                    contentDescription = playlist.name,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val gradientColors = listOf(Color(0xFF6366F1), Color(0xFFA855F7), Color(0xFFEC4899))
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.linearGradient(gradientColors)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 24.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Playlist • ${playlistSongs.size} songs",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Controls Bar (Matching YT Music: Big Play + Shuffle)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (playlistSongs.isNotEmpty()) {
                                // Big White Circular Play Button
                                Surface(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .clickable { onSongClick(playlistSongs.first(), playlistSongs, 0) },
                                    color = Color.White
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play All",
                                            tint = Color.Black,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                // Dark Circular Shuffle Button
                                Surface(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            val shuffled = playlistSongs.shuffled()
                                            onSongClick(shuffled.first(), shuffled, 0)
                                        },
                                    color = Color(0xFF26262A)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Shuffle,
                                            contentDescription = "Shuffle",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (playlistSongs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No songs in this playlist yet.", color = TextMuted)
                        }
                    }
                } else {
                    itemsIndexed(playlistSongs) { index, song ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            SongTile(
                                song = song,
                                isPlaying = song.id == currentSong?.id,
                                isLiked = likedSongs.any { it.id == song.id },
                                enableSwipe = false,
                                onSongClick = { onSongClick(song, playlistSongs, index) },
                                onLikeClick = { onLikeClick(song.id) },
                                onOptionsClick = { onOptionsClick(song) }
                            )
                        }
                    }
                }
            }
        }
        return
    }

    val currentSubtitle = when (selectedTab) {
        0 -> "Your custom & shared playlists"
        1 -> "Your favorite & liked songs"
        2 -> "Recent listening history"
        3 -> "Downloaded tracks & offline audio cache"
        else -> "Your Music Library"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Library Header matching YouTube Music design (Aligned perfectly with HomeScreen header)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SilverBrandHeader(
                modifier = Modifier.weight(1f),
                subtitle = currentSubtitle
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 1. Playlists Icon Button
                IconButton(
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Playlists",
                        tint = if (selectedTab == 0) AccentTeal else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 2. Liked Songs Icon Button
                IconButton(
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Liked Music",
                        tint = if (selectedTab == 1) Color(0xFFEF4444) else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 3. History Icon Button
                IconButton(
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = if (selectedTab == 2) AccentTeal else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 4. Downloaded Songs Icon Button
                IconButton(
                    onClick = { selectedTab = 3 },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Downloaded Tracks",
                        tint = if (selectedTab == 3) AccentTeal else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Super Smooth Animated Content Fragment Transition
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { width -> width } + fadeIn(tween(300, easing = FastOutSlowInEasing)))
                        .togetherWith(slideOutHorizontally { width -> -width } + fadeOut(tween(300, easing = FastOutSlowInEasing)))
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn(tween(300, easing = FastOutSlowInEasing)))
                        .togetherWith(slideOutHorizontally { width -> width } + fadeOut(tween(300, easing = FastOutSlowInEasing)))
                }
            },
            label = "LibraryFragmentTransition",
            modifier = Modifier.fillMaxSize()
        ) { targetTab ->
            when (targetTab) {
                0 -> {
                    // PLAYLISTS TAB
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 140.dp)
                        ) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { isImportDialogOpen = true },
                                    color = Color(0xFF6366F1).copy(alpha = 0.14f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF6366F1)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Import Playlist",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                text = "Import Playlist",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextStrong
                                                )
                                            )
                                            Text(
                                                text = "Paste a Spasiba share link or code",
                                                style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                                            )
                                        }
                                    }
                                }
                            }

                            if (playlists.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No custom playlists created yet.",
                                            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                                        )
                                    }
                                }
                            } else {
                                items(playlists) { playlist ->
                                    val playlistSongsFlow = remember(playlist.id) { repository?.getSongsForPlaylist(playlist.id) }
                                    val pSongs by (playlistSongsFlow?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) })
                                    val coverUrl = pSongs.firstOrNull { it.thumbnail.isNotBlank() }?.thumbnail

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 5.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .combinedClickable(
                                                onClick = { activePlaylistDetail = playlist },
                                                onLongClick = { selectedPlaylistForOptions = playlist }
                                            ),
                                        colors = CardDefaults.cardColors(containerColor = UiSurfaceElevated)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(Color(0xFF262626)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!coverUrl.isNullOrBlank()) {
                                                    coil.compose.AsyncImage(
                                                        model = coverUrl,
                                                        contentDescription = playlist.name,
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    val gradientColors = listOf(Color(0xFF6366F1), Color(0xFFA855F7), Color(0xFFEC4899))
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Brush.linearGradient(gradientColors)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = playlist.name.take(1).uppercase(),
                                                            style = MaterialTheme.typography.titleLarge.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = playlist.name,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextStrong,
                                                        fontSize = 17.sp
                                                    )
                                                )
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = "Custom User Playlist • ${pSongs.size} tracks",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        color = TextMuted,
                                                        fontSize = 13.sp
                                                    )
                                                )
                                            }
                                            IconButton(onClick = { selectedPlaylistForOptions = playlist }) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "Options",
                                                    tint = TextMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        FloatingActionButton(
                            onClick = onCreatePlaylistClick,
                            containerColor = AccentTeal,
                            contentColor = Color.Black,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 18.dp, end = 20.dp)
                                .size(56.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create Playlist",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                1 -> {
                    // LIKED TRACKS TAB (Matching YouTube Music Liked Songs Layout!)
                    val likedCoverUrl = likedSongs.firstOrNull { it.thumbnail.isNotBlank() }?.thumbnail

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 120.dp)
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFFEC4899))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!likedCoverUrl.isNullOrBlank()) {
                                        coil.compose.AsyncImage(
                                            model = likedCoverUrl,
                                            contentDescription = "Liked Songs",
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(64.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Liked Songs",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 24.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Auto playlist • ${likedSongs.size} songs",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextMuted,
                                        fontSize = 14.sp
                                    )
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                if (likedSongs.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // Big White Circular Play Button
                                        Surface(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .clickable { onSongClick(likedSongs.first(), likedSongs, 0) },
                                            color = Color.White
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Play All",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }
                                        }

                                        // Dark Circular Shuffle Button
                                        Surface(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    val shuffled = likedSongs.shuffled()
                                                    onSongClick(shuffled.first(), shuffled, 0)
                                                },
                                            color = Color(0xFF26262A)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Shuffle,
                                                    contentDescription = "Shuffle",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }

                        if (likedSongs.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No liked tracks yet. Heart songs to save them here!",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(likedSongs) { index, song ->
                                SongTile(
                                    song = song,
                                    isPlaying = song.id == currentSong?.id,
                                    isLiked = true,
                                    enableSwipe = false,
                                    onSongClick = { onSongClick(song, likedSongs, index) },
                                    onLikeClick = { onLikeClick(song.id) },
                                    onOptionsClick = { onOptionsClick(song) },
                                    onLongClick = {
                                        onLikeClick(song.id)
                                    }
                                )
                            }
                        }
                    }
                }

                2 -> {
                    // LISTENING HISTORY TAB
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 120.dp)
                    ) {
                        if (historySongs.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No recent listening history.",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(historySongs) { index, song ->
                                SongTile(
                                    song = song,
                                    isPlaying = song.id == currentSong?.id,
                                    isLiked = likedSongs.any { it.id == song.id },
                                    enableSwipe = false,
                                    onSongClick = { onSongClick(song, historySongs, index) },
                                    onLikeClick = { onLikeClick(song.id) },
                                    onOptionsClick = { onOptionsClick(song) },
                                    onLongClick = {
                                        songToRemoveFromHistory = song
                                    }
                                )
                            }
                        }
                    }
                }

                3 -> {
                    // DOWNLOADS & OFFLINE CACHE TAB (Matching ss_10/ss_11/dl_screen1.jpg layout!)
                    val activeDownloadsMap by (downloadManager?.downloads?.collectAsState(initial = emptyMap()) ?: remember { mutableStateOf(emptyMap()) })
                    val activeDownloadsList = activeDownloadsMap.values.filter { it.status != app.spasiba.nativeapp2.data.download.DownloadStatus.COMPLETED }
                    val downloadedList = (downloadManager?.getDownloadedSongs() ?: emptyList()).distinctBy { it.id }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 140.dp)
                    ) {
                        // Subtitle & Header layout matching YouTube Music Downloaded Songs page (dl_screen1.jpg)
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Playlist • ${downloadedList.size} ${if (downloadedList.size == 1) "song" else "songs"}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    ),
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Hero Card Square Box (dl_screen1.jpg gradient checkmark)
                                Box(
                                    modifier = Modifier
                                        .size(190.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF6366F1), Color(0xFF3B82F6))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Downloaded songs",
                                        tint = Color.White,
                                        modifier = Modifier.size(80.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Downloaded songs",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextStrong,
                                        fontSize = 24.sp
                                    )
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Action Buttons Row: [ (✓) ] [ ▶ Play All ] [ ⋮ ]
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape),
                                        color = Color.White.copy(alpha = 0.12f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    if (downloadedList.isNotEmpty()) {
                                        Surface(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .clickable { onSongClick(downloadedList.first(), downloadedList, 0) },
                                            color = Color.White
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Play All",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape),
                                        color = Color.White.copy(alpha = 0.12f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Options",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        if (activeDownloadsList.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Active Downloads",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AccentTeal),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }

                            items(activeDownloadsList) { dItem ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            coil.compose.AsyncImage(
                                                model = dItem.song.thumbnail,
                                                contentDescription = dItem.song.title,
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = dItem.song.title,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextStrong),
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = "${dItem.type.name} • ${dItem.quality} • ${dItem.progress.toInt()}% (${dItem.status})",
                                                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                                                )
                                            }

                                            if (dItem.status == app.spasiba.nativeapp2.data.download.DownloadStatus.DOWNLOADING) {
                                                IconButton(onClick = { downloadManager?.pauseDownload(dItem.song.id) }) {
                                                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.White)
                                                }
                                            } else if (dItem.status == app.spasiba.nativeapp2.data.download.DownloadStatus.PAUSED) {
                                                IconButton(onClick = { downloadManager?.resumeDownload(dItem.song.id) }) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = AccentTeal)
                                                }
                                            }

                                            IconButton(onClick = { downloadManager?.cancelDownload(dItem.song.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                                            }
                                        }

                                        if (dItem.status == app.spasiba.nativeapp2.data.download.DownloadStatus.DOWNLOADING) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            androidx.compose.material3.LinearProgressIndicator(
                                                progress = { (dItem.progress / 100f).coerceIn(0f, 1f) },
                                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                                color = AccentTeal,
                                                trackColor = Color.White.copy(alpha = 0.1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (downloadedList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No downloaded tracks yet. Use Download on any track to listen offline.",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(downloadedList) { index, song ->
                                SongTile(
                                    song = song,
                                    isPlaying = song.id == currentSong?.id,
                                    isLiked = likedSongs.any { it.id == song.id },
                                    isDownloaded = true,
                                    enableSwipe = false,
                                    onSongClick = { onSongClick(song, downloadedList, index) },
                                    onLikeClick = { onLikeClick(song.id) },
                                    onOptionsClick = { onOptionsClick(song) },
                                    onLongClick = { songToDeleteFromOffline = song }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "${downloadedList.size} ${if (downloadedList.size == 1) "song" else "songs"}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isImportDialogOpen && repository != null) {
        var importInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { isImportDialogOpen = false },
            title = { Text("Import Shared Playlist", color = TextStrong, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Paste a Spasiba share link, code, or enter a new playlist title:", color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = importInput,
                        onValueChange = { importInput = it },
                        label = { Text("Share Code / Deep Link / Title") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (importInput.isNotBlank()) {
                        scope.launch {
                            val parsed = ShareUtils.parsePlaylistShareCode(importInput.trim())
                            if (parsed != null && parsed.songs.isNotEmpty()) {
                                repository.importSharedPlaylist(parsed.name, parsed.songs, parsed.shareCode)
                                Toast.makeText(context, "Imported '${parsed.name}' (${parsed.songs.size} tracks)!", Toast.LENGTH_LONG).show()
                            } else {
                                repository.createPlaylist(importInput.trim())
                                Toast.makeText(context, "Created playlist '${importInput.trim()}'", Toast.LENGTH_SHORT).show()
                            }
                            isImportDialogOpen = false
                        }
                    }
                }) {
                    Text("Import Playlist", color = AccentTeal)
                }
            },
            dismissButton = {
                TextButton(onClick = { isImportDialogOpen = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = UiSurfaceElevated
        )
    }

    // Force-Click Confirmation Dialog for Offline Available Songs (Matching Ss12.jpg with Delete option!)
    if (songToDeleteFromOffline != null) {
        AlertDialog(
            onDismissRequest = { songToDeleteFromOffline = null },
            containerColor = Color(0xFF262628),
            title = {
                Text(
                    text = songToDeleteFromOffline!!.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                )
            },
            text = {
                Text(
                    text = "Delete downloaded song?",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = songToDeleteFromOffline
                        if (target != null) {
                            downloadManager?.deleteDownload(target.id)
                            Toast.makeText(context, "Deleted ${target.title} from offline downloads", Toast.LENGTH_SHORT).show()
                        }
                        songToDeleteFromOffline = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { songToDeleteFromOffline = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // Force-Click Confirmation Dialog for Recent History Songs (Matching Ss12.jpg with Remove option!)
    if (songToRemoveFromHistory != null) {
        AlertDialog(
            onDismissRequest = { songToRemoveFromHistory = null },
            containerColor = Color(0xFF262628),
            title = {
                Text(
                    text = songToRemoveFromHistory!!.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                )
            },
            text = {
                Text(
                    text = "Remove from search history?",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = songToRemoveFromHistory
                        if (target != null) {
                            historyManager.deletePlayedSong(target.id)
                            Toast.makeText(context, "Removed ${target.title} from history", Toast.LENGTH_SHORT).show()
                        }
                        songToRemoveFromHistory = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Remove", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { songToRemoveFromHistory = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // Playlist Force-Click / Long-Click Options Dialog (Matching YouTube Music Ss10.jpg & Ss11.jpg grade!)
    if (selectedPlaylistForOptions != null) {
        val targetPl = selectedPlaylistForOptions!!
        val targetPlSongsFlow = remember(targetPl.id) { repository?.getSongsForPlaylist(targetPl.id) }
        val targetPlSongs by (targetPlSongsFlow?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) })

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { selectedPlaylistForOptions = null }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF212124),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF323238))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = targetPl.name,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 20.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Custom User Playlist • ${targetPlSongs.size} tracks",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            )
                        }

                        IconButton(
                            onClick = { selectedPlaylistForOptions = null },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons Grid (Ss10/Ss11 YouTube Music style)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Quick Action 1: Open
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    activePlaylistDetail = targetPl
                                    selectedPlaylistForOptions = null
                                },
                            color = Color(0xFF2C2C32)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = AccentTeal,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Open",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        // Quick Action 2: Share
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    selectedPlaylistForOptions = null
                                    scope.launch {
                                        val songs = repository?.getSongsForPlaylistSync(targetPl.id) ?: emptyList()
                                        ShareUtils.sharePlaylist(context, targetPl.name, songs, targetPl.shareCode)
                                    }
                                },
                            color = Color(0xFF2C2C32)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Share",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        // Quick Action 3: Delete
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    val idToDelete = targetPl.id
                                    selectedPlaylistForOptions = null
                                    scope.launch {
                                        repository?.deletePlaylist(idToDelete)
                                        Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Delete",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color(0xFFEF4444),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Delete Playlist Action Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                val idToDelete = targetPl.id
                                selectedPlaylistForOptions = null
                                scope.launch {
                                    repository?.deletePlaylist(idToDelete)
                                    Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Playlist",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Delete Playlist",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
