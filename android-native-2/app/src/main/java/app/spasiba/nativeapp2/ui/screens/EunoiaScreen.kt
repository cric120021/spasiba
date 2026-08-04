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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    var isFloatingChatOpen by remember { mutableStateOf(false) }
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
            .background(Color(0xFF070709))
    ) {
        AsyncImage(
            model = activeSong.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
                .scale(1.2f)
                .background(Color.Black.copy(alpha = 0.7f))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.75f),
                            Color(0xFF0A0A0F).copy(alpha = 0.88f),
                            Color(0xFF050508)
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

            // Player Hero Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = activeSong.thumbnail,
                            contentDescription = activeSong.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeSong.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeSong.author,
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 12.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    playerManager.playPrevious()
                                    sessionManager.emitPlaybackAction("previous")
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
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
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    playerManager.playNext()
                                    sessionManager.emitPlaybackAction("next")
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Independent Seekbar
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        androidx.compose.material3.Slider(
                            value = sliderProgress,
                            onValueChange = { targetFraction ->
                                val targetPosMs = (targetFraction * durSec * 1000L).toLong()
                                playerManager.seekTo(targetPosMs)
                            },
                            onValueChangeFinished = {
                                val currentPosMs = playerManager.positionMs.value
                                sessionManager.emitPlaybackAction("seek", org.json.JSONObject().apply {
                                    put("positionMs", currentPosMs)
                                })
                            },
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(posFormatted, style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
                            Text(durFormatted, style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Quick Reaction Flying Emoji Bar (NO TOAST ALERTS)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickReactions = listOf("❤️", "🔥", "✨", "🎵", "💓", "🥺", "🎧")
                items(quickReactions) { reactionEmoji ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                sessionManager.triggerFlyingReaction(reactionEmoji)
                            },
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = reactionEmoji,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // MUSIC CHAT STREAM WITH AUTO SCROLL
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EUNOIA CHAT STREAM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                letterSpacing = 1.sp,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = "My ID: $myDeviceId",
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

                    Spacer(modifier = Modifier.height(4.dp))

                    val sendMessageAction = {
                        if (chatInputValue.isNotBlank()) {
                            sessionManager.sendMessage(chatInputValue)
                            chatInputValue = ""
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                            .clickable { isFloatingChatOpen = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (chatInputValue.isBlank()) "Send lyric or message..." else chatInputValue,
                                color = if (chatInputValue.isBlank()) TextMuted else Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
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

        // FLOATING TEMPORARY EDIT BOX STICKY ATTACHED TO TOP OF KEYBOARD
        if (isFloatingChatOpen) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            Dialog(
                onDismissRequest = { isFloatingChatOpen = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            isFloatingChatOpen = false
                        }
                        .imePadding(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 4.dp)
                            .clickable(enabled = false) {},
                        color = Color(0xFF14141F),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)),
                        shadowElevation = 16.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = chatInputValue,
                                onValueChange = { chatInputValue = it },
                                placeholder = { Text("Write message...", color = Color.Gray, fontSize = 14.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
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
                                            isFloatingChatOpen = false
                                        }
                                    }
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    if (chatInputValue.isNotBlank()) {
                                        sessionManager.sendMessage(chatInputValue)
                                        chatInputValue = ""
                                        isFloatingChatOpen = false
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

    }
}
