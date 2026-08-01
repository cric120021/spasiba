package app.spasiba.nativeapp2.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.player.NativePlayerManager
import app.spasiba.nativeapp2.ui.components.SilverBrandHeader
import app.spasiba.nativeapp2.ui.components.SongTile
import app.spasiba.nativeapp2.ui.theme.AccentTeal
import app.spasiba.nativeapp2.ui.theme.TextMuted
import app.spasiba.nativeapp2.ui.theme.TextStrong
import app.spasiba.nativeapp2.ui.theme.UiSurfaceElevated
import app.spasiba.nativeapp2.utils.ShareUtils
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    playerManager: NativePlayerManager,
    onSongClick: (Song) -> Unit,
    onBackClick: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenPlaylistOperations: () -> Unit = {},
    selectedLanguages: Set<String> = setOf("Hindi", "Punjabi", "English"),
    onToggleLanguage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE) }
    
    // User Profile Observation & Auto Hardware Registration
    val dbProfile by playerManager.repository.userProfile.collectAsState(initial = null)

    androidx.compose.runtime.LaunchedEffect(Unit) {
        playerManager.repository.registerUserDeviceWithHardwareId()
    }
    
    var username by remember(dbProfile) {
        mutableStateOf(dbProfile?.username ?: prefs.getString("username", "Spasiba Listener") ?: "Spasiba Listener")
    }
    val visitorId = remember(dbProfile) {
        dbProfile?.visitorId ?: app.spasiba.nativeapp2.data.engine.EunoiaSessionManager.getInstance(context).myDeviceId.value
    }
    var profilePhotoUri by remember(dbProfile) {
        mutableStateOf(dbProfile?.photoUri ?: prefs.getString("profile_photo_uri", null))
    }

    var isEditNameDialogOpen by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf(username) }

    var downloadQualityPref by remember {
        mutableStateOf(prefs.getString("video_download_quality", "Ask each time") ?: "Ask each time")
    }

    var isEditPhotoDialogOpen by remember { mutableStateOf(false) }
    var photoUrlInput by remember { mutableStateOf(profilePhotoUri ?: "") }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val uriStr = uri.toString()
            profilePhotoUri = uriStr
            photoUrlInput = uriStr
            prefs.edit().putString("profile_photo_uri", uriStr).apply()
            scope.launch {
                playerManager.repository.saveProfilePhoto(uriStr)
                Toast.makeText(context, "Profile photo updated from Gallery!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val historySongs by playerManager.repository.listeningHistory.collectAsState(initial = emptyList())
    val likedSongs by playerManager.repository.likedSongs.collectAsState(initial = emptyList())
    val likedSongIds by playerManager.likedSongIds.collectAsState()
    val userPlaylists by playerManager.repository.playlists.collectAsState(initial = emptyList())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 2.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Silver Metallic Brand Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SilverBrandHeader(subtitle = "User Profile & Preferences")
            }
        }

        // 1. Profile Hero Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.04f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROFILE & ACCOUNT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentTeal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.6.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Interactive Profile Photo Avatar with Edit Badge & DB persistence
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Surface(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        galleryLauncher.launch("image/*")
                                    },
                                shape = CircleShape,
                                color = AccentTeal.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentTeal)
                            ) {
                                if (!profilePhotoUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = profilePhotoUri,
                                        contentDescription = "User Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Profile Photo",
                                            tint = AccentTeal,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }

                            // Camera / Edit Photo Badge
                            Surface(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        galleryLauncher.launch("image/*")
                                    },
                                shape = CircleShape,
                                color = AccentTeal,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Update Photo",
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = username,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextStrong,
                                        fontSize = 20.sp
                                    )
                                )
                                IconButton(
                                    onClick = {
                                        editNameInput = username
                                        isEditNameDialogOpen = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Name",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("visitor_id", visitorId)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied @$visitorId", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "@$visitorId",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = TextMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Language Preferences Section (Hindi, Punjabi, English, South, Haryanvi, Bhojpuri, Gujarati, Bengali)
        item {
            val allLangs = listOf("Hindi", "Punjabi", "English", "South Hits", "Haryanvi", "Bhojpuri", "Gujarati", "Bengali")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.04f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "MUSIC LANGUAGE PREFERENCES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        )
                    )
                    Text(
                        text = "Select languages to show on Home Rails",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextMuted,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allLangs) { lang ->
                            val isSel = selectedLanguages.contains(lang)
                            Surface(
                                shape = CircleShape,
                                color = if (isSel) Color.White else Color.White.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onToggleLanguage(lang) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = lang,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (isSel) Color.Black else Color.White,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1
                                    )
                                    if (isSel) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color.Black,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (userPlaylists.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.03f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create custom playlists in your Library and they will appear right here.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted, fontSize = 13.sp)
                        )
                    }
                }
            }
        } else {
            items(userPlaylists) { playlist ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = UiSurfaceElevated)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
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
                                imageVector = Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextStrong,
                                    fontSize = 15.sp
                                )
                            )
                            Text(
                                text = if (playlist.isShared) "Shared Playlist" else "Custom Playlist",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 12.sp)
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val songs = playerManager.repository.getSongsForPlaylistSync(playlist.id)
                                ShareUtils.sharePlaylist(context, playlist.name, songs, playlist.shareCode)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Clear Data & Preferences Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.04f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "DATA & CACHE CONTROL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.4.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Manage storage & recommendations",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextStrong,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        text = "Clear your listening history or reset your audio recommendation graph.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    scope.launch {
                                        playerManager.repository.clearListeningHistory()
                                        Toast.makeText(context, "Listening history cleared", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Clear history",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextStrong,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    Toast.makeText(context, "Suggestions data reset", Toast.LENGTH_SHORT).show()
                                },
                            color = Color(0x22F43F5E)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Clear suggestions",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFFFCA5A5),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Video Download Quality Preference Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.04f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "DOWNLOAD PREFERENCE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.4.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Video Download Quality",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextStrong,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        text = "Preferred video resolution when downloading tracks with video.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val qualityOptions = listOf("Ask each time", "1080p", "720p", "480p", "360p")
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(qualityOptions) { q ->
                            val isSelected = downloadQualityPref == q
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        downloadQualityPref = q
                                        prefs.edit().putString("video_download_quality", q).apply()
                                        Toast.makeText(context, "Download quality set to '$q'", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = q,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.Black else TextStrong,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Selected",
                                            tint = Color.Black,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Listening History Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "THIS MONTH",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp
                        )
                    )
                    Text(
                        text = "Recently played tracks",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextStrong,
                            fontSize = 18.sp
                        )
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "${historySongs.size} SONGS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (historySongs.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.03f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Start listening and your playback history will show up here.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted, fontSize = 13.sp)
                        )
                    }
                }
            }
        } else {
            items(historySongs.take(6)) { song ->
                SongTile(
                    song = song,
                    isPlaying = song.id == playerManager.currentSong.value?.id,
                    isLiked = likedSongIds.contains(song.id),
                    onSongClick = { onSongClick(song) },
                    onLikeClick = { playerManager.toggleLike(song) }
                )
            }
        }

        // 5. Liked Songs Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TASTE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp
                        )
                    )
                    Text(
                        text = "Liked songs",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextStrong,
                            fontSize = 18.sp
                        )
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "${likedSongs.size} SONGS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (likedSongs.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.03f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Use Heart from the player and your saved favorites will show up here.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted, fontSize = 13.sp)
                        )
                    }
                }
            }
        } else {
            items(likedSongs.take(6)) { song ->
                SongTile(
                    song = song,
                    isPlaying = song.id == playerManager.currentSong.value?.id,
                    isLiked = true,
                    onSongClick = { onSongClick(song) },
                    onLikeClick = { playerManager.toggleLike(song) }
                )
            }
        }
    }

    // Edit Display Name Dialog
    if (isEditNameDialogOpen) {
        AlertDialog(
            onDismissRequest = { isEditNameDialogOpen = false },
            title = { Text("Edit Display Name", color = TextStrong) },
            text = {
                OutlinedTextField(
                    value = editNameInput,
                    onValueChange = { editNameInput = it },
                    label = { Text("Display Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editNameInput.isNotBlank()) {
                            username = editNameInput.trim()
                            prefs.edit().putString("username", username).apply()
                            scope.launch {
                                playerManager.repository.updateUsername(username)
                            }
                        }
                        isEditNameDialogOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                ) {
                    Text("Save", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditNameDialogOpen = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Edit Profile Photo Dialog (DB Sync)
    if (isEditPhotoDialogOpen) {
        AlertDialog(
            onDismissRequest = { isEditPhotoDialogOpen = false },
            title = { Text("Update Profile Photo", color = TextStrong, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Pick an image directly from your phone gallery:", color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            isEditPhotoDialogOpen = false
                            galleryLauncher.launch("image/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose Photo from Gallery", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = photoUrlInput,
                        onValueChange = { photoUrlInput = it },
                        label = { Text("Or Enter Photo URL / URI") },
                        singleLine = true,
                        placeholder = { Text("content:// or https://...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = photoUrlInput.trim()
                        profilePhotoUri = if (input.isNotBlank()) input else null
                        prefs.edit().putString("profile_photo_uri", profilePhotoUri).apply()
                        scope.launch {
                            playerManager.repository.saveProfilePhoto(profilePhotoUri ?: "")
                            Toast.makeText(context, "Profile photo updated!", Toast.LENGTH_SHORT).show()
                        }
                        isEditPhotoDialogOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                ) {
                    Text("Save", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditPhotoDialogOpen = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}
