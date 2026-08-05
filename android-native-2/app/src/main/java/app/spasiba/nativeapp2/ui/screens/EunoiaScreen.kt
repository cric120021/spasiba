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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import coil.compose.AsyncImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EunoiaMessageRow(message: EunoiaChatMessage) {
    val isPartner = message.isPartner
    val align = if (isPartner) Alignment.Start else Alignment.End
    val bubbleBg = if (isPartner) Color(0xFF1E1E26) else Color.White.copy(alpha = 0.9f)
    val textColor = if (isPartner) Color.White else Color.Black
    val timeFormatted = remember(message.timestampMs) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestampMs))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
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
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isPartner) 4.dp else 16.dp,
                bottomEnd = if (isPartner) 16.dp else 4.dp
            ),
            color = bubbleBg,
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    text = message.messageText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isPartner) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f),
                        fontSize = 9.sp
                    ),
                    modifier = Modifier.align(Alignment.End)
                )
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
    val positionMs by playerManager.positionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()

    val prefs = remember { context.getSharedPreferences("spasiba_user_prefs", android.content.Context.MODE_PRIVATE) }
    var isEunoiaEnabled by remember { mutableStateOf(prefs.getBoolean("eunoia_enabled", true)) }
    var isInviteDialogOpen by remember { mutableStateOf(false) }
    var inviteInputId by remember { mutableStateOf("") }
    var inviteInputName by remember { mutableStateOf("") }
    var chatInputValue by remember { mutableStateOf("") }
    var liveStatusText by remember { mutableStateOf("Synced • Idle") }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030303))
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
                            Color(0xFF030303).copy(alpha = 0.82f),
                            Color(0xFF030303).copy(alpha = 0.94f),
                            Color(0xFF030303)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // TOP AREA: Header & Partner Display Name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E26)),
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
                            tint = if (sessionStatus == EunoiaSessionStatus.CONNECTED) Color.White else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Listening with $formattedPartner",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextStrong,
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = liveStatusText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = when (sessionStatus) {
                                EunoiaSessionStatus.CONNECTED -> Color(0xFF34D399)
                                EunoiaSessionStatus.REJECTED_BUSY -> Color(0xFFEF4444)
                                else -> TextMuted
                            },
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // End Session Button (Disconnects session & clears chat box)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape)
                        .clickable {
                            sessionManager.endSession()
                            Toast.makeText(context, "Session ended & chat cleared", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "End Session",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

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

            Spacer(modifier = Modifier.height(4.dp))

            // Player Hero (Card-Less Floating Layout)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Centered Full-Bleed Rounded Album Artwork (YouTube Music 1:1 Aspect Ratio)
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

                // Media Playback Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

                    Spacer(modifier = Modifier.width(20.dp))

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
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // MUSIC CHAT STREAM (Card-Less Floating Canvas)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
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
                    Text(
                        text = "ID: $myDeviceId",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentTeal,
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

                // Integrated Text Input Bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    color = Color(0xFF181822),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = chatInputValue,
                            onValueChange = { chatInputValue = it },
                            placeholder = { Text("Write a message...", color = TextMuted, fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
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

                        IconButton(
                            onClick = {
                                if (chatInputValue.isNotBlank()) {
                                    sessionManager.sendMessage(chatInputValue)
                                    chatInputValue = ""
                                }
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
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
                title = { Text("Pair with Friend", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        if (savedFriends.isNotEmpty()) {
                            Text("Quick Select Saved Friend:", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(savedFriends) { friend ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF242436),
                                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
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
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Text("Enter partner's Device ID (e.g. SPASIBA-XXXXXX):", color = TextMuted, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = inviteInputId,
                            onValueChange = { inviteInputId = it },
                            placeholder = { Text("SPASIBA-12D584", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Friend Display Name (Optional):", color = TextMuted, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = inviteInputName,
                            onValueChange = { inviteInputName = it },
                            placeholder = { Text("e.g. Jigar", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (inviteInputId.isNotBlank()) {
                                sessionManager.sendInvite(inviteInputId, inviteInputName, playerManager)
                                isInviteDialogOpen = false
                            }
                        }
                    ) {
                        Text("Send Invite", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isInviteDialogOpen = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                },
                containerColor = Color(0xFF14141E)
            )
        }

        // -----------------------------------------------------------
        // ULTRA-ELASTIC PHYSICS DRAGGABLE REACTION FLOATER (SPRING REBOUND)
        // -----------------------------------------------------------
        var isReactionDockExpanded by remember { mutableStateOf(false) }
        val animOffsetX = remember { Animatable(0f) }
        val animOffsetY = remember { Animatable(0f) }
        val quickReactions = listOf("❤️", "🔥", "✨", "🎵", "💓", "🥺", "🎧", "👏", "😭")

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 130.dp, end = 16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Row(
                modifier = Modifier.offset {
                    IntOffset(animOffsetX.value.roundToInt(), animOffsetY.value.roundToInt())
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Expandable Horizontal Emoji Pack (Appears to the left of the circular button)
                AnimatedVisibility(visible = isReactionDockExpanded) {
                    Surface(
                        modifier = Modifier.padding(end = 8.dp),
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
                                            isReactionDockExpanded = false
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
                        .size(56.dp)
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
                            fontSize = 22.sp,
                            color = if (isReactionDockExpanded) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    }
}
