package app.spasiba.nativeapp2.ui.screens

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import app.spasiba.nativeapp2.player.NativePlayerManager
import app.spasiba.nativeapp2.ui.components.SilverBrandHeader
import app.spasiba.nativeapp2.ui.components.SongTile
import app.spasiba.nativeapp2.ui.theme.AccentTeal
import app.spasiba.nativeapp2.ui.theme.TextMuted
import app.spasiba.nativeapp2.ui.theme.TextStrong
import app.spasiba.nativeapp2.ui.theme.UiSurfaceElevated
import app.spasiba.nativeapp2.utils.ShareUtils
import kotlinx.coroutines.launch

@Composable
fun PlaylistOperationsScreen(
    playerManager: NativePlayerManager,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = playerManager.repository

    val userProfile by repository.userProfile.collectAsState(initial = null)
    val playlists by repository.playlists.collectAsState(initial = emptyList())
    val likedSongIds by playerManager.likedSongIds.collectAsState()

    var activeSelectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var isCreateDialogOpen by remember { mutableStateOf(false) }
    var createNameInput by remember { mutableStateOf("") }

    var isImportDialogOpen by remember { mutableStateOf(false) }
    var importCodeInput by remember { mutableStateOf("") }

    var isRenameDialogOpen by remember { mutableStateOf(false) }
    var renameTargetPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var playlistMenuTarget by remember { mutableStateOf<PlaylistEntity?>(null) }
    var isSyncing by remember { mutableStateOf(false) }

    // If viewing songs of a specific playlist
    val selectedPlaylistSongs by remember(activeSelectedPlaylist) {
        if (activeSelectedPlaylist != null) {
            repository.getSongsForPlaylist(activeSelectedPlaylist!!.id)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Navigation & Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SilverBrandHeader(
                    modifier = Modifier.weight(1f),
                    subtitle = if (activeSelectedPlaylist != null) "Playlist: ${activeSelectedPlaylist!!.name}" else "Manage & Share Playlists"
                )

                IconButton(
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            repository.forceSyncWithRemoteCloud()
                            val curPlId = activeSelectedPlaylist?.id
                            if (curPlId != null) {
                                val freshSongs = repository.getSongsForPlaylistSync(curPlId)
                                // Trigger active playlist refresh
                                activeSelectedPlaylist = activeSelectedPlaylist?.copy()
                            }
                            isSyncing = false
                            Toast.makeText(context, "Database and playlists synced!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Refresh Playlists",
                        tint = AccentTeal,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2. Quick Action Buttons (+ New Playlist, Import Code)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            createNameInput = ""
                            isCreateDialogOpen = true
                        },
                    color = AccentTeal
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "New Playlist",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            importCodeInput = ""
                            isImportDialogOpen = true
                        },
                    color = Color.White.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Import Playlist",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }

        // 4. Content Area: Detailed Songs View or Playlists Overview
        if (activeSelectedPlaylist != null) {
            val pl = activeSelectedPlaylist!!

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "PLAYLIST DETAILS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentTeal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.4.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pl.name,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextStrong,
                                fontSize = 22.sp
                            )
                        )
                        Text(
                            text = "${selectedPlaylistSongs.size} Tracks • Share Code: ${pl.shareCode ?: "SPASIBA-LOCAL"}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    if (selectedPlaylistSongs.isNotEmpty()) {
                                        playerManager.playQueue(selectedPlaylistSongs, 0, sourceLabel = "Playlist: ${pl.name}")
                                    } else {
                                        Toast.makeText(context, "Playlist is empty", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play All", color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (selectedPlaylistSongs.isNotEmpty()) {
                                        playerManager.playQueue(selectedPlaylistSongs.shuffled(), 0, sourceLabel = "Playlist Shuffle: ${pl.name}")
                                    } else {
                                        Toast.makeText(context, "Playlist is empty", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Shuffle", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val songs = repository.getSongsForPlaylistSync(pl.id)
                                        ShareUtils.sharePlaylist(context, pl.name, songs, pl.shareCode)
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                            }
                        }
                    }
                }
            }

            if (selectedPlaylistSongs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tracks in this playlist yet. Add songs using track options in Search or Home!",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted, fontSize = 13.sp)
                        )
                    }
                }
            } else {
                items(selectedPlaylistSongs.size) { index ->
                    val song = selectedPlaylistSongs[index]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SongTile(
                                song = song,
                                isPlaying = playerManager.currentSong.value?.id == song.id,
                                isLiked = likedSongIds.contains(song.id),
                                onSongClick = { playerManager.playQueue(selectedPlaylistSongs, index, sourceLabel = "Playlist: ${pl.name}") },
                                onLikeClick = { playerManager.toggleLike(song) },
                                onOptionsClick = {}
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    repository.removeSongFromPlaylist(pl.id, song.id)
                                    Toast.makeText(context, "Removed from playlist", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        } else {
            // Playlists List Overview
            item {
                Text(
                    text = "MY PLAYLISTS & COLLECTIONS (${playlists.size})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp
                    )
                )
            }

            if (playlists.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.03f)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No playlists found",
                                style = MaterialTheme.typography.titleMedium.copy(color = TextStrong, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Create a custom playlist or import one via share code to get started.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 12.sp),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                items(playlists) { playlist ->
                    var showDropdown by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { activeSelectedPlaylist = playlist },
                        colors = CardDefaults.cardColors(containerColor = UiSurfaceElevated)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
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

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextStrong,
                                        fontSize = 16.sp
                                    )
                                )
                                Text(
                                    text = if (playlist.isShared) "Shared • Code: ${playlist.shareCode}" else "Custom Room Playlist",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 12.sp)
                                )
                            }

                            Box {
                                IconButton(onClick = { showDropdown = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = TextMuted
                                    )
                                }

                                DropdownMenu(
                                    expanded = showDropdown,
                                    onDismissRequest = { showDropdown = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Open Playlist") },
                                        onClick = {
                                            showDropdown = false
                                            activeSelectedPlaylist = playlist
                                        },
                                        leadingIcon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share Playlist") },
                                        onClick = {
                                            showDropdown = false
                                            scope.launch {
                                                val songs = repository.getSongsForPlaylistSync(playlist.id)
                                                ShareUtils.sharePlaylist(context, playlist.name, songs, playlist.shareCode)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            showDropdown = false
                                            renameTargetPlaylist = playlist
                                            renameInput = playlist.name
                                            isRenameDialogOpen = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = Color.Red) },
                                        onClick = {
                                            showDropdown = false
                                            scope.launch {
                                                repository.deletePlaylist(playlist.id)
                                                Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (isCreateDialogOpen) {
        AlertDialog(
            onDismissRequest = { isCreateDialogOpen = false },
            title = { Text("Create New Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = createNameInput,
                    onValueChange = { createNameInput = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (createNameInput.isNotBlank()) {
                            scope.launch {
                                repository.createPlaylist(createNameInput.trim())
                                isCreateDialogOpen = false
                                Toast.makeText(context, "Playlist '${createNameInput.trim()}' created!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                ) {
                    Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isCreateDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isImportDialogOpen) {
        AlertDialog(
            onDismissRequest = { isImportDialogOpen = false },
            title = { Text("Import Shared Playlist", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Paste a playlist code, deep link, or payload to sync it to your local Room database.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = importCodeInput,
                        onValueChange = { importCodeInput = it },
                        label = { Text("Share Code / Link") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importCodeInput.isNotBlank()) {
                            val parsed = ShareUtils.parsePlaylistShareCode(importCodeInput)
                            if (parsed != null) {
                                scope.launch {
                                    repository.importSharedPlaylist(parsed.name, parsed.songs, parsed.shareCode)
                                    isImportDialogOpen = false
                                    Toast.makeText(context, "Imported playlist '${parsed.name}' (${parsed.songs.size} tracks)", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Invalid share code or payload format", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                ) {
                    Text("Import & Sync", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isImportDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isRenameDialogOpen && renameTargetPlaylist != null) {
        AlertDialog(
            onDismissRequest = { isRenameDialogOpen = false },
            title = { Text("Rename Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            scope.launch {
                                repository.updatePlaylistName(renameTargetPlaylist!!.id, renameInput.trim())
                                isRenameDialogOpen = false
                                Toast.makeText(context, "Renamed to '${renameInput.trim()}'", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isRenameDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
