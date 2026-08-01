package app.spasiba.nativeapp2.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp2.data.db.PlaylistEntity
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.data.repository.MusicRepository
import app.spasiba.nativeapp2.ui.components.SongTile
import app.spasiba.nativeapp2.ui.theme.AccentTeal
import app.spasiba.nativeapp2.ui.theme.TextMuted
import app.spasiba.nativeapp2.ui.theme.TextStrong
import app.spasiba.nativeapp2.ui.theme.UiSurfaceElevated
import app.spasiba.nativeapp2.ui.components.SilverBrandHeader
import app.spasiba.nativeapp2.utils.ShareUtils
import android.widget.Toast
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    likedSongs: List<Song>,
    playlists: List<PlaylistEntity>,
    historySongs: List<Song>,
    currentSong: Song?,
    repository: MusicRepository? = null,
    downloadManager: app.spasiba.nativeapp2.data.download.SpasibaDownloadManager? = null,
    onCreatePlaylistClick: () -> Unit,
    onSongClick: (Song, List<Song>, Int) -> Unit,
    onLikeClick: (String) -> Unit,
    onOptionsClick: (Song) -> Unit = {},
    onSwipeToQueue: (Song) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Playlists, 1 = Liked, 2 = History, 3 = Offline
    val tabs = listOf("Playlists", "Liked Tracks", "History", "Downloads")
    val context = LocalContext.current
    var activePlaylistDetail by remember { mutableStateOf<PlaylistEntity?>(null) }
    var isImportDialogOpen by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    if (activePlaylistDetail != null && repository != null) {
        val playlist = activePlaylistDetail!!
        val playlistSongsFlow = remember(playlist.id) { repository.getSongsForPlaylist(playlist.id) }
        val playlistSongs by playlistSongsFlow.collectAsState(initial = emptyList())

        // Dedicated Playlist Detail View
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextStrong
                        )
                    )
                    Text(
                        text = "${playlistSongs.size} tracks",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                    )
                }
                if (playlistSongs.isNotEmpty()) {
                    IconButton(onClick = { onSongClick(playlistSongs.first(), playlistSongs, 0) }) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play All", tint = AccentTeal)
                    }
                }
                IconButton(onClick = { ShareUtils.sharePlaylist(context, playlist.name, playlistSongs, playlist.shareCode) }) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = TextMuted)
                }
            }

            if (playlistSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No songs in this playlist yet.", color = TextMuted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 120.dp)
                ) {
                    itemsIndexed(playlistSongs) { index, song ->
                        SongTile(
                            song = song,
                            isPlaying = song.id == currentSong?.id,
                            isLiked = likedSongs.any { it.id == song.id },
                            onSongClick = { onSongClick(song, playlistSongs, index) },
                            onLikeClick = { onLikeClick(song.id) },
                            onOptionsClick = { onOptionsClick(song) },
                            onSwipeToQueue = { onSwipeToQueue(song) }
                        )
                    }
                }
            }
        }
        return
    }

    val currentSubtitle = when (selectedTab) {
        0 -> "Your custom & shared playlists"
        1 -> "Your favorite & liked songs"
        2 -> "Recent listening history (1 Month)"
        3 -> "Downloaded tracks & offline audio cache"
        else -> "Your Music Library"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Library Header matching LibrarySection.tsx in spasiba.vercel.app
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
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
                // 1. Playlists Icon Button (Default view)
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

                // 3. History Icon Button (1 Month TTL History)
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
                                    .clickable { onCreatePlaylistClick() },
                                color = AccentTeal.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AccentTeal),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Create Playlist",
                                            tint = Color.Black,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = "Create Playlist",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = AccentTeal
                                            )
                                        )
                                        Text(
                                            text = "Build a custom mix for any mood",
                                            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                                        )
                                    }
                                }
                            }

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
                                            imageVector = Icons.Default.Sync,
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
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { activePlaylistDetail = playlist },
                                    colors = CardDefaults.cardColors(containerColor = UiSurfaceElevated)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF6366F1)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlaylistPlay,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = playlist.name,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextStrong
                                                )
                                            )
                                            Text(
                                                text = "Custom User Playlist",
                                                style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                                            )
                                        }
                                        IconButton(onClick = {
                                            scope.launch {
                                                val songs = repository?.getSongsForPlaylistSync(playlist.id) ?: emptyList()
                                                ShareUtils.sharePlaylist(context, playlist.name, songs, playlist.shareCode)
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Share",
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
                // LIKED TRACKS TAB
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 120.dp)
                ) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            color = UiSurfaceElevated
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AccentTeal),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = "Liked",
                                        tint = Color.Black,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Liked Songs",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextStrong
                                        )
                                    )
                                    Text(
                                        text = "${likedSongs.size} favorited tracks",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                                    )
                                }
                                if (likedSongs.isNotEmpty()) {
                                    IconButton(onClick = { onSongClick(likedSongs.first(), likedSongs, 0) }) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play All",
                                            tint = AccentTeal
                                        )
                                    }
                                }
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
                                onSongClick = { onSongClick(song, likedSongs, index) },
                                onLikeClick = { onLikeClick(song.id) },
                                onOptionsClick = { onOptionsClick(song) },
                                onSwipeToQueue = { onSwipeToQueue(song) }
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
                                onSongClick = { onSongClick(song, historySongs, index) },
                                onLikeClick = { onLikeClick(song.id) },
                                onOptionsClick = { onOptionsClick(song) },
                                onSwipeToQueue = { onSwipeToQueue(song) }
                            )
                        }
                    }
                }
            }

            3 -> {
                // DOWNLOADS & OFFLINE CACHE TAB
                val activeDownloadsMap by (downloadManager?.downloads?.collectAsState(initial = emptyMap()) ?: remember { mutableStateOf(emptyMap()) })
                val activeDownloadsList = activeDownloadsMap.values.toList()

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 120.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = UiSurfaceElevated)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = "Downloads & Cache",
                                        tint = AccentTeal,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = "Downloads & Audio Cache",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TextStrong
                                            )
                                        )
                                        Text(
                                            text = "Instant zero-data offline playback",
                                            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                                        )
                                    }
                                }
                            }
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
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Pause", tint = Color.White)
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

                    item {
                        Text(
                            text = "Offline Available Songs",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextStrong),
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }

                    val downloadedList = ((downloadManager?.getDownloadedSongs() ?: emptyList()) + likedSongs + historySongs).distinctBy { it.id }
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
                                onSongClick = { onSongClick(song, downloadedList, index) },
                                onLikeClick = { onLikeClick(song.id) },
                                onOptionsClick = { onOptionsClick(song) },
                                onSwipeToQueue = { onSwipeToQueue(song) }
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
}
