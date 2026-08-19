package app.spasiba.nativeapp2.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp2.data.engine.EunoiaChatMessage
import app.spasiba.nativeapp2.data.engine.EunoiaFriendManager
import app.spasiba.nativeapp2.data.engine.EunoiaSessionManager
import app.spasiba.nativeapp2.data.engine.EunoiaSessionStatus
import app.spasiba.nativeapp2.data.engine.FlyingReaction
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.player.NativePlayerManager
import app.spasiba.nativeapp2.ui.theme.AccentTeal
import app.spasiba.nativeapp2.ui.theme.TextMuted
import app.spasiba.nativeapp2.ui.theme.TextStrong
import app.spasiba.nativeapp2.ui.theme.UiSurfaceElevated
import coil.compose.AsyncImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DateSeparatorBadge(dateText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E1F28),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun EunoiaMessageRow(
    message: EunoiaChatMessage,
    partnerPhotoUri: String? = null,
    onReplySelected: (EunoiaChatMessage) -> Unit = {}
) {
    val isPartner = message.isPartner
    val align = if (isPartner) Alignment.Start else Alignment.End
    val timeFormatted = remember(message.timestampMs) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestampMs))
    }
    var isVoicePlaying by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = if (isPartner) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        if (isPartner) {
            // Partner Avatar DP pinned to left of incoming bubble
            if (!partnerPhotoUri.isNullOrBlank()) {
                AsyncImage(
                    model = partnerPhotoUri,
                    contentDescription = "Partner Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0xFFE50914).copy(alpha = 0.6f), CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF8E0E25), Color(0xFF1C1D27)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = message.senderName.take(1).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = align,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = if (isPartner) message.senderName else "You",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )

            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isPartner) 4.dp else 18.dp,
                    bottomEnd = if (isPartner) 18.dp else 4.dp
                ),
                color = if (isPartner) Color(0xFF1C1D27) else Color(0xFF8E0E25),
                border = BorderStroke(
                    1.dp,
                    if (isPartner) Color(0xFF2C2D3C) else Color(0xFFE50914).copy(alpha = 0.5f)
                ),
                modifier = Modifier.clickable { onReplySelected(message) }
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Quoted Reply Preview Box inside bubble
                    if (!message.replyToText.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .background(Color(0xFFE50914), RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = message.replyToSender ?: "Reply",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFE50914),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    )
                                    Text(
                                        text = message.replyToText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Media Attachment Preview (Photo)
                    if (!message.mediaUri.isNullOrBlank()) {
                        AsyncImage(
                            model = message.mediaUri,
                            contentDescription = "Shared Media",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.4f)
                                .clip(RoundedCornerShape(10.dp))
                                .padding(bottom = 6.dp)
                        )
                    }

                    // Voice Note Player Bubble
                    if (message.isVoiceNote) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            IconButton(
                                onClick = { isVoicePlaying = !isVoicePlaying },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isVoicePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play Voice Note",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Headphones,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Voice Message",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                                Text(
                                    text = "0:${message.voiceDurationSec.toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }

                    // Standard Text Content
                    if (message.messageText.isNotBlank()) {
                        Text(
                            text = message.messageText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp
                        ),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

@Composable
fun FlyingReactionParticle(reaction: FlyingReaction, onFinished: (String) -> Unit = {}) {
    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(reaction.id) {
        launch {
            offsetY.animateTo(
                targetValue = -650f,
                animationSpec = tween(durationMillis = 2400, easing = LinearEasing)
            )
        }
        launch {
            delay(1200)
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1200, easing = LinearEasing)
            )
            onFinished(reaction.id)
        }
    }

    // Incoming partner reaction -> Alignment.BottomStart (LEFT side)
    // Outgoing self reaction -> Alignment.BottomEnd (RIGHT side)
    val align = if (reaction.isPartner) Alignment.BottomStart else Alignment.BottomEnd
    val paddingSide = if (reaction.isPartner) Modifier.padding(start = 28.dp, bottom = 90.dp) else Modifier.padding(end = 28.dp, bottom = 90.dp)

    if (alpha.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(paddingSide),
            contentAlignment = align
        ) {
            Text(
                text = reaction.emoji,
                fontSize = 36.sp,
                modifier = Modifier
                    .offset(
                        x = if (reaction.isPartner) 20.dp else (-20).dp,
                        y = offsetY.value.dp
                    )
                    .scale(1f + (offsetY.value / -1200f))
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EunoiaScreen(
    playerManager: NativePlayerManager,
    onSongClick: (Song, List<Song>, Int) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionManager = remember { EunoiaSessionManager.getInstance(context) }

    LaunchedEffect(playerManager) {
        sessionManager.attachPlayerManager(playerManager)
    }

    val myDeviceId by sessionManager.myDeviceId.collectAsState()
    val sessionStatus by sessionManager.sessionStatus.collectAsState()
    val partnerDeviceId by sessionManager.partnerDeviceId.collectAsState()
    val partnerName by sessionManager.partnerName.collectAsState()
    val partnerPhotoUri by sessionManager.partnerPhotoUri.collectAsState()
    val pendingInviteSender by sessionManager.pendingInviteSender.collectAsState()
    val flyingReactions by sessionManager.flyingReactions.collectAsState()
    val sessionMessages by sessionManager.sessionMessages.collectAsState()
    val isLeader by sessionManager.isLeader.collectAsState()

    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isLoading by playerManager.isLoading.collectAsState()
    val positionMs by playerManager.positionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()
    val videoAspectRatio by playerManager.videoAspectRatio.collectAsState()
    val activePlayer by playerManager.activePlayer.collectAsState()

    val prefs = remember { context.getSharedPreferences("spasiba_user_prefs", android.content.Context.MODE_PRIVATE) }
    val myPhotoUri = remember(prefs) { prefs.getString("profile_photo_uri", null) }
    var isEunoiaEnabled by remember { mutableStateOf(prefs.getBoolean("eunoia_enabled", true)) }
    var isInviteDialogOpen by remember { mutableStateOf(false) }
    var inviteInputId by remember { mutableStateOf("") }
    var inviteInputName by remember { mutableStateOf("") }
    var chatInputValue by remember { mutableStateOf("") }
    var liveStatusText by remember { mutableStateOf("Synced • Idle") }
    var isEunoiaVideoActive by remember { mutableStateOf(false) }
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackSideIsRight by remember { mutableStateOf(false) }

    if (!isEunoiaEnabled) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070709)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Disabled",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Eunoia is Disabled",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Listen Together features are currently turned off in your Profile Settings.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                )
                Spacer(modifier = Modifier.height(24.dp))
                androidx.compose.material3.Button(
                    onClick = {
                        prefs.edit().putBoolean("eunoia_enabled", true).apply()
                        isEunoiaEnabled = true
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Enable Eunoia", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val activeSong = currentSong ?: Song(
        id = "YQHsXMglC9A",
        title = "Hello",
        author = "Adele",
        thumbnail = "https://i.ytimg.com/vi/YQHsXMglC9A/hqdefault.jpg",
        durationSeconds = 295
    )

    val durSec = if (durationMs > 0) durationMs / 1000L else activeSong.durationSeconds.toLong()
    val posSec = positionMs / 1000L
    val sliderProgress = if (durSec > 0) (posSec.toFloat() / durSec.toFloat()).coerceIn(0f, 1f) else 0f
    val posFormatted = String.format("%d:%02d", posSec / 60, posSec % 60)
    val durFormatted = String.format("%d:%02d", durSec / 60, durSec % 60)

    val partnerFriend = remember(partnerDeviceId) {
        if (!partnerDeviceId.isNullOrBlank()) {
            EunoiaFriendManager.getInstance(context).getFriends().find { it.friendId.equals(partnerDeviceId?.trim(), ignoreCase = true) }
        } else null
    }

    val formattedPartner = remember(partnerName, partnerDeviceId, partnerFriend) {
        val cleanPartnerName = partnerName?.takeIf { !it.startsWith("SPASIBA-") && !it.startsWith("Friend (") && it.isNotBlank() }
        val cleanFriendName = partnerFriend?.name?.takeIf { !it.startsWith("SPASIBA-") && !it.startsWith("Friend (") && it.isNotBlank() }
        
        val res = cleanPartnerName ?: cleanFriendName ?: partnerName?.takeIf { it.isNotBlank() && !it.startsWith("SPASIBA-") } ?: "Partner"
        android.util.Log.e("EXACT_EUNOIA_DEBUG", "EunoiaScreen render: formattedPartner='$res' | partnerName='$partnerName' | cleanPartnerName='$cleanPartnerName' | partnerFriendName='${partnerFriend?.name}' | partnerDeviceId='$partnerDeviceId'")
        res
    }

    // LIVE 1-SECOND STATUS TICKER
    LaunchedEffect(sessionStatus, isPlaying, positionMs) {
        while (true) {
            val posStr = String.format("%d:%02d", (positionMs / 1000) / 60, (positionMs / 1000) % 60)
            val stateStr = if (isPlaying) "Playing ($posStr)" else "Paused"

            liveStatusText = when (sessionStatus) {
                EunoiaSessionStatus.CONNECTED -> "Synced • $stateStr"
                EunoiaSessionStatus.INVITE_SENT -> "Sending invite... • $stateStr"
                EunoiaSessionStatus.REJECTED_BUSY -> "Partner busy • $stateStr"
                else -> "Ready • Tap + to pair"
            }
            delay(1000)
        }
    }

    // Auto-scroll list state for Chat
    val chatListState = rememberLazyListState()
    LaunchedEffect(sessionMessages.size) {
        if (sessionMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(sessionMessages.size - 1)
        }
    }

    // Cloud Glass Edit Box Keyboard & Visibility State
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val isImeVisible = WindowInsets.isImeVisible
    var isInputActive by remember { mutableStateOf(false) }
    val showEditBox = isImeVisible || isInputActive || chatInputValue.isNotEmpty()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = activeSong.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(100.dp)
                    .scale(1.3f)
                    .background(Color.Black.copy(alpha = 0.88f))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.82f),
                                Color.Black.copy(alpha = 0.94f),
                                Color.Black
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                // Header Bar (Dual Overlapping YouTube Collab Avatars + Connection Status)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val isConnectionActive = sessionStatus == EunoiaSessionStatus.CONNECTED
                    val partnerFriend = remember(partnerDeviceId) {
                        partnerDeviceId?.let { id ->
                            EunoiaFriendManager.getInstance(context).getFriends().find { it.friendId.equals(id, ignoreCase = true) }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isInviteDialogOpen = true }
                    ) {
                        // YouTube Collab Style Overlapping Avatars Stack
                        Box(
                            modifier = Modifier
                                .width(if (isConnectionActive) 56.dp else 38.dp)
                                .height(38.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // First Avatar (My Profile Image)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E1E26)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!myPhotoUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = myPhotoUri,
                                        contentDescription = "My Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "My Avatar",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Second Avatar (Partner Profile Image - Overlapping YouTube Collab Style, visible ONLY when connected)
                            if (isConnectionActive) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 18.dp)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0284C7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val activePhotoUri = partnerFriend?.photoUri ?: partnerPhotoUri
                                    if (!activePhotoUri.isNullOrBlank()) {
                                        AsyncImage(
                                            model = activePhotoUri,
                                            contentDescription = "Partner Avatar",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Partner Avatar",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            val activePartnerName = partnerFriend?.name ?: partnerName
                            val headerTitle = when {
                                sessionStatus == EunoiaSessionStatus.CONNECTED -> "Listening with ${activePartnerName ?: "Partner"}"
                                sessionStatus == EunoiaSessionStatus.INVITE_SENT -> "Sending invite..."
                                else -> "Listening with Partner"
                            }
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 17.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            val statusSubtitle = when (sessionStatus) {
                                EunoiaSessionStatus.CONNECTED -> if (isLeader) "Room Host • Sync Active" else "Connected • Sync Active"
                                EunoiaSessionStatus.INVITE_SENT -> "Waiting for partner to accept..."
                                EunoiaSessionStatus.REJECTED_BUSY -> "Partner Busy • Tap + to retry"
                                else -> "Ready • Tap + to pair"
                            }
                            Text(
                                text = statusSubtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (sessionStatus == EunoiaSessionStatus.CONNECTED) Color(0xFF10B981) else TextMuted,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    // Session End / Connection Off Button (Red Power Button - Always visible in Eunoia Header)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable {
                                sessionManager.endSession()
                                Toast.makeText(context, "Session disconnected", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Session End / Disconnect",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                            .clickable { isInviteDialogOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Invite Partner",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Player Hero (Card-Less Floating Layout)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Centered Album Artwork / 720p Video Player Mode with Double Tap Seek Gestures
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isEunoiaVideoActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { offset ->
                                                val containerWidth = size.width
                                                val isRightSide = offset.x > (containerWidth / 2f)
                                                val currentPos = playerManager.positionMs.value
                                                val targetPos = if (isRightSide) {
                                                    (currentPos + 10000L).coerceAtMost(durationMs)
                                                } else {
                                                    (currentPos - 10000L).coerceAtLeast(0L)
                                                }
                                                playerManager.seekTo(targetPos)
                                                sessionManager.emitPlaybackAction("seek", org.json.JSONObject().apply {
                                                    put("positionMs", targetPos)
                                                })
                                                seekFeedbackSideIsRight = isRightSide
                                                seekFeedbackText = if (isRightSide) "+10s ▶▶" else "◀◀ -10s"
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.runtime.key(activeSong.id) {
                                    AndroidView(
                                        factory = { ctx ->
                                            androidx.media3.ui.PlayerView(ctx).apply {
                                                player = activePlayer
                                                useController = false
                                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                setBackgroundColor(android.graphics.Color.BLACK)
                                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                            }
                                        },
                                        update = { playerView ->
                                            if (playerView.player != activePlayer) {
                                                playerView.player = activePlayer
                                            }
                                            playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Quick Seek Visual Ripple Overlay (+10s / -10s)
                                seekFeedbackText?.let { text ->
                                    LaunchedEffect(text) {
                                        delay(700L)
                                        seekFeedbackText = null
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(if (seekFeedbackSideIsRight) Alignment.CenterEnd else Alignment.CenterStart)
                                            .padding(horizontal = 24.dp)
                                            .background(Color.Black.copy(alpha = 0.70f), shape = CircleShape)
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = text,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                            ) {
                                AsyncImage(
                                    model = activeSong.thumbnail,
                                    contentDescription = activeSong.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = activeSong.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = activeSong.author,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextMuted,
                            fontSize = 13.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // YouTube Music Native Dynamic Seeker Bar
                    app.spasiba.nativeapp2.ui.components.YouTubeMusicSeekerBar(
                        progressFraction = sliderProgress,
                        positionFormatted = posFormatted,
                        durationFormatted = durFormatted,
                        onSeek = { targetFraction ->
                            val targetPosMs = (targetFraction * durSec * 1000L).toLong()
                            playerManager.seekTo(targetPosMs)
                        },
                        onSeekFinished = {
                            val currentPosMs = playerManager.positionMs.value
                            sessionManager.emitPlaybackAction("seek", org.json.JSONObject().apply {
                                put("positionMs", currentPosMs)
                            })
                        },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Media Playback Controls Row (Core Controls Centered + Video Toggle at Far Right)
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Perfectly Centered Core Controls [Previous] [Play/Pause] [Next]
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    playerManager.playPrevious()
                                    sessionManager.emitPlaybackAction("previous")
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(24.dp))

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable {
                                        if (isPlaying) {
                                            playerManager.exoPlayer.pause()
                                            sessionManager.emitPlaybackAction("pause")
                                        } else {
                                            playerManager.exoPlayer.play()
                                            sessionManager.emitPlaybackAction("play")
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.Black,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(24.dp))

                            IconButton(
                                onClick = {
                                    playerManager.playNext()
                                    sessionManager.emitPlaybackAction("next")
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        // Video / Audio Toggle Button (Positioned at Far Right End)
                        IconButton(
                            onClick = {
                                isEunoiaVideoActive = !isEunoiaVideoActive
                                if (isEunoiaVideoActive) {
                                    playerManager.setVideoQuality("720p")
                                } else {
                                    playerManager.disableVideoMode()
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isEunoiaVideoActive) Icons.Default.Headphones else Icons.Default.OndemandVideo,
                                contentDescription = if (isEunoiaVideoActive) "Switch to Audio" else "Switch to Video",
                                tint = if (isEunoiaVideoActive) Color(0xFF38BDF8) else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }



            // MUSIC CHAT STREAM (Card-Less Floating Canvas)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ROOM CHAT & ACTIVITY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    state = chatListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(sessionMessages) { msg ->
                        EunoiaMessageRow(message = msg)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Premium Pill Text Input Bar (Rides Dynamically Right On Top of Keyboard)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        color = UiSurfaceElevated,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (chatInputValue.isNotBlank()) Color.White else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (chatInputValue.isEmpty()) {
                                    Text(
                                        text = "Write a message in room...",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted, fontSize = 15.sp)
                                    )
                                }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = chatInputValue,
                                    onValueChange = {
                                        chatInputValue = it
                                        isInputActive = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                                    singleLine = true,
                                    maxLines = 1,
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextStrong,
                                        fontSize = 15.sp,
                                        lineHeight = 20.sp
                                    ),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Send
                                    ),
                                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                        onSend = {
                                            if (chatInputValue.isNotBlank()) {
                                                sessionManager.sendMessage(chatInputValue)
                                                chatInputValue = ""
                                            }
                                        }
                                    )
                                )
                            }
                            if (chatInputValue.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = TextMuted,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { chatInputValue = "" }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    val isSendActive = chatInputValue.isNotBlank()
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = UiSurfaceElevated,
                        border = BorderStroke(1.dp, if (isSendActive) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.14f))
                    ) {
                        IconButton(
                            onClick = {
                                if (chatInputValue.isNotBlank()) {
                                    sessionManager.sendMessage(chatInputValue)
                                    chatInputValue = ""
                                }
                            },
                            enabled = isSendActive,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (isSendActive) Color.White else TextMuted.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        flyingReactions.forEach { reaction ->
            androidx.compose.runtime.key(reaction.id) {
                FlyingReactionParticle(
                    reaction = reaction,
                    onFinished = { id -> sessionManager.removeFlyingReaction(id) }
                )
            }
        }

        if (isInviteDialogOpen) {
            val savedFriends = remember { EunoiaFriendManager.getInstance(context).getFriends() }

            AlertDialog(
                onDismissRequest = { isInviteDialogOpen = false },
                shape = RoundedCornerShape(24.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pair with Friend", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        if (savedFriends.isNotEmpty()) {
                            Text("Quick Select Saved Friend:", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(savedFriends) { friend ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = UiSurfaceElevated,
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                        modifier = Modifier.clickable {
                                            inviteInputId = friend.friendId
                                            inviteInputName = friend.name
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(friend.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Text("Enter partner's Device ID (e.g. SPASIBA-XXXXXX):", color = TextMuted, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = UiSurfaceElevated,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (inviteInputId.isEmpty()) {
                                        Text("SPASIBA-12D584", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted.copy(alpha = 0.6f), fontSize = 15.sp))
                                    }
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = inviteInputId,
                                        onValueChange = { inviteInputId = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextStrong, fontSize = 15.sp)
                                    )
                                }
                                if (inviteInputId.isNotEmpty()) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(18.dp).clickable { inviteInputId = "" })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Friend Display Name (Optional):", color = TextMuted, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = UiSurfaceElevated,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (inviteInputName.isEmpty()) {
                                        Text("e.g. Jigar", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted.copy(alpha = 0.6f), fontSize = 15.sp))
                                    }
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = inviteInputName,
                                        onValueChange = { inviteInputName = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextStrong, fontSize = 15.sp)
                                    )
                                }
                                if (inviteInputName.isNotEmpty()) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(18.dp).clickable { inviteInputName = "" })
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = UiSurfaceElevated,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.clickable {
                            if (inviteInputId.isNotBlank()) {
                                sessionManager.sendInvite(inviteInputId, inviteInputName, playerManager)
                                isInviteDialogOpen = false
                            }
                        }
                    ) {
                        Text("Send Invite", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isInviteDialogOpen = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                },
                containerColor = UiSurfaceElevated
            )
        }

        // -----------------------------------------------------------
        // ULTRA-ELASTIC PHYSICS DRAGGABLE REACTION FLOATER (SPRING REBOUND)
        // -----------------------------------------------------------
        var isReactionDockExpanded by remember { mutableStateOf(false) }
        val animOffsetX = remember { Animatable(0f) }
        val animOffsetY = remember { Animatable(0f) }
        val quickReactions = listOf("❤️", "🔥", "✨", "🎵", "💓", "🥺", "🎧", "👏", "😭")

        // Fullscreen touch interceptor to collapse emoji dock when tapping anywhere outside
        if (isReactionDockExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        isReactionDockExpanded = false
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 130.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Row(
                modifier = Modifier.offset {
                    IntOffset(animOffsetX.value.roundToInt(), animOffsetY.value.roundToInt())
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // Expandable Horizontal Emoji Pack (Appears to the left of the circular button, bounded so button never goes offscreen)
                AnimatedVisibility(visible = isReactionDockExpanded) {
                    Surface(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .widthIn(max = 220.dp),
                        shape = CircleShape,
                        color = Color(0xFF1E1E26),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(quickReactions) { reactionEmoji ->
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            sessionManager.triggerFlyingReaction(reactionEmoji)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = reactionEmoji,
                                        fontSize = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Elastic Physics Circular Floater Button (Guaranteed Tap + Physics Drag)
                Surface(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            coroutineScope {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var totalDist = 0f
                                    var isMoved = false

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break

                                        if (change.pressed) {
                                            val dragAmount = change.positionChange()
                                            if (dragAmount != androidx.compose.ui.geometry.Offset.Zero) {
                                                val stepDist = kotlin.math.hypot(dragAmount.x, dragAmount.y)
                                                totalDist += stepDist
                                                if (totalDist > 6f) {
                                                    isMoved = true
                                                    change.consume()
                                                    launch {
                                                        animOffsetX.snapTo(animOffsetX.value + dragAmount.x)
                                                        animOffsetY.snapTo(animOffsetY.value + dragAmount.y)
                                                    }
                                                }
                                            }
                                        } else {
                                            // Finger released
                                            if (!isMoved) {
                                                isReactionDockExpanded = !isReactionDockExpanded
                                            } else {
                                                // Physics Spring rebound
                                                launch {
                                                    animOffsetX.animateTo(
                                                        targetValue = animOffsetX.value,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessLow
                                                        )
                                                    )
                                                }
                                            }
                                            break
                                        }
                                    }
                                }
                            }
                        },
                    shape = CircleShape,
                    color = if (isReactionDockExpanded) AccentTeal else Color(0xFF1E1E26),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isReactionDockExpanded) "✖" else "😀",
                            fontSize = 20.sp,
                            color = if (isReactionDockExpanded) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    }
}
