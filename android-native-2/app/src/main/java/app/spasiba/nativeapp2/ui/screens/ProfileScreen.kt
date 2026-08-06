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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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

import androidx.compose.material.icons.automirrored.filled.ArrowForward
import app.spasiba.nativeapp2.data.engine.EunoiaFriend
import app.spasiba.nativeapp2.data.engine.EunoiaFriendManager
import app.spasiba.nativeapp2.data.engine.EunoiaSessionManager

@Composable
fun ProfileScreen(
    playerManager: NativePlayerManager,
    onSongClick: (Song) -> Unit,
    onBackClick: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenPlaylistOperations: () -> Unit = {},
    onOpenAllFriends: () -> Unit = {},
    onQuickInviteFriend: (EunoiaFriend) -> Unit = {},
    onOpenFriendChat: (EunoiaFriend) -> Unit = {},
    selectedLanguages: Set<String> = setOf("Hindi", "Punjabi", "English"),
    onToggleLanguage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE) }
    
    val sessionManager = remember { EunoiaSessionManager.getInstance(context) }
    val friendManager = remember { EunoiaFriendManager.getInstance(context) }
    val friendEventTime by sessionManager.friendEventFlow.collectAsState()
    var friendsList by remember(friendEventTime) { mutableStateOf(friendManager.getFriends()) }
    var isAddFriendDialogOpen by remember { mutableStateOf(false) }
    
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

    var isEunoiaEnabled by remember {
        mutableStateOf(prefs.getBoolean("eunoia_enabled", true))
    }

    var isEditPhotoDialogOpen by remember { mutableStateOf(false) }
    var photoUrlInput by remember { mutableStateOf(profilePhotoUri ?: "") }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val permanentPath = try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val destFile = java.io.File(context.filesDir, "profile_photo.jpg")
                    val outputStream = java.io.FileOutputStream(destFile)
                    inputStream?.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    Uri.fromFile(destFile).toString()
                } catch (e: Exception) {
                    uri.toString()
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    profilePhotoUri = permanentPath
                    photoUrlInput = permanentPath
                    prefs.edit().putString("profile_photo_uri", permanentPath).apply()
                }

                playerManager.repository.saveProfilePhoto(permanentPath)
                sessionManager.broadcastProfileUpdateSignal(newPhotoUri = permanentPath)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "Profile photo saved permanently!", Toast.LENGTH_SHORT).show()
                }
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Silver Metallic Brand Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SilverBrandHeader(subtitle = "Account & Settings")
                }
            }
        }

        // 1. Spotify-Style Account Header Tile
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { isEditNameDialogOpen = true },
                color = Color.White.copy(alpha = 0.04f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile Photo Avatar
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Surface(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable { galleryLauncher.launch("image/*") },
                            shape = CircleShape,
                            color = AccentTeal.copy(alpha = 0.2f)
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
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }

                        // Edit Badge
                        Surface(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable { galleryLauncher.launch("image/*") },
                            shape = CircleShape,
                            color = AccentTeal,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Update Photo",
                                    tint = Color.Black,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = username,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextStrong,
                                fontSize = 19.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("visitor_id", visitorId)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied ID: @$visitorId", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Text(
                                text = "@$visitorId",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 12.sp
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

                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Name",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Section: Friends & Listen Together
        item {
            Text(
                text = "LISTEN TOGETHER & FRIENDS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981),
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp
                ),
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp, start = 4.dp)
            )
        }

        // Friends & Add Friend Row
        item {
            val pendingCount = friendsList.count { it.status != app.spasiba.nativeapp2.data.engine.EunoiaFriendStatus.ACCEPTED }
            val acceptedList = friendsList.filter { it.status == app.spasiba.nativeapp2.data.engine.EunoiaFriendStatus.ACCEPTED }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.03f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "My Friends (${acceptedList.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextStrong,
                                fontSize = 16.sp
                            )
                        )

                        Button(
                            onClick = { isAddFriendDialogOpen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Add Friend",
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (acceptedList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            acceptedList.take(2).forEach { friend ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenFriendChat(friend) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF14141F)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF262636)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!friend.photoUri.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = friend.photoUri,
                                                    contentDescription = friend.displayName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Text(
                                                    text = friend.displayName.take(1).uppercase(),
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = friend.displayName,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextStrong,
                                                    fontSize = 14.sp
                                                )
                                            )
                                            Text(
                                                text = friend.friendId,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = TextMuted,
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }

                                        IconButton(
                                            onClick = { onOpenFriendChat(friend) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ChatBubbleOutline,
                                                contentDescription = "Chat",
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (!isEunoiaEnabled) {
                                                    Toast.makeText(context, "Please enable Eunoia to send invites.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    onQuickInviteFriend(friend)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Bolt,
                                                contentDescription = "Invite",
                                                tint = Color.Black,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Invite", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = onOpenAllFriends,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (pendingCount > 0) "View All Friends ($pendingCount Pending)" else "View All Friends",
                            color = Color(0xFF10B981),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Section: Language Preferences
        item {
            val allLangs = listOf("Hindi", "Punjabi", "English", "South Hits", "Haryanvi", "Bhojpuri", "Gujarati", "Bengali")
            Text(
                text = "MUSIC LANGUAGES",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp
                ),
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp, start = 4.dp)
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

        // Section: Video Download Quality
        item {
            Text(
                text = "DOWNLOAD PREFERENCE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp
                ),
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp, start = 4.dp)
            )

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
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
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

        // Section: Storage & Cache Control
        item {
            Text(
                text = "DATA & CACHE CONTROL",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp
                ),
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp, start = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
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
                            text = "Clear History",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextStrong,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
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
                            text = "Reset Suggestions",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFFCA5A5),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
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
                            sessionManager.broadcastProfileUpdateSignal(newName = username)
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

    if (isAddFriendDialogOpen) {
        var friendCodeInput by remember { mutableStateOf("") }
        var friendNameInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { isAddFriendDialogOpen = false },
            title = {
                Text("Add Friend with Code", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column {
                    Text("Enter your friend's code or Device ID:", color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = friendCodeInput,
                        onValueChange = { friendCodeInput = it.uppercase() },
                        placeholder = { Text("e.g. SPASIBA-9A2F41", color = Color.Gray, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Friend Name (Optional):", color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = friendNameInput,
                        onValueChange = { friendNameInput = it },
                        placeholder = { Text("e.g. Alex", color = Color.Gray, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (friendCodeInput.isNotBlank()) {
                            sessionManager.sendFriendRequestSignal(friendCodeInput, friendNameInput)
                            friendsList = friendManager.getFriends()
                            isAddFriendDialogOpen = false
                            Toast.makeText(context, "Friend request sent to $friendCodeInput!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Send Request", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddFriendDialogOpen = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = Color(0xFF14141E)
        )
    }
}
