package app.spasiba.nativeapp2.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

data class EunoiaMessage(
    val id: String,
    val senderName: String,
    val isPartner: Boolean,
    val messageText: String = "",
    val timestamp: String
)

@Composable
fun EunoiaMessageRow(message: app.spasiba.nativeapp2.data.engine.EunoiaChatMessage) {
    val isPartner = message.isPartner
    val align = if (isPartner) Alignment.Start else Alignment.End
    val bubbleBg = if (isPartner) Color(0xFF1E1E26) else Color.White.copy(alpha = 0.9f)
    val textColor = if (isPartner) Color.White else Color.Black

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
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
            Text(
                text = message.messageText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun FlyingReactionParticle(reaction: FlyingReaction) {
    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(reaction.id) {
        launch {
            offsetY.animateTo(
                targetValue = -600f,
                animationSpec = tween(durationMillis = 2400, easing = LinearEasing)
            )
        }
        launch {
            delay(1200)
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1200, easing = LinearEasing)
            )
        }
    }

    if (alpha.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                text = reaction.emoji,
                fontSize = 32.sp,
                modifier = Modifier
                    .offset(
                        x = (reaction.startXRatio * 320).dp,
                        y = offsetY.value.dp
                    )
                    .scale(1f + (offsetY.value / -1200f))
                    .blur(0.dp)
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

    val myDeviceId by sessionManager.myDeviceId.collectAsState()
    val sessionStatus by sessionManager.sessionStatus.collectAsState()
    val partnerDeviceId by sessionManager.partnerDeviceId.collectAsState()
    val partnerName by sessionManager.partnerName.collectAsState()
    val pendingInviteSender by sessionManager.pendingInviteSender.collectAsState()
    val flyingReactions by sessionManager.flyingReactions.collectAsState()
    val sessionMessages by sessionManager.sessionMessages.collectAsState()

    val currentSong by playerManager.currentSong.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val positionMs by playerManager.positionMs.collectAsState()
    val durationMs by playerManager.durationMs.collectAsState()

    var partnerPhotoUri by remember { mutableStateOf<String?>(null) }
    var isInviteDialogOpen by remember { mutableStateOf(false) }
    var inviteInputId by remember { mutableStateOf("") }
    var chatInputValue by remember { mutableStateOf("") }
    var currentReactionEvent by remember { mutableStateOf<String?>(null) }

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

    val messages = remember {
        mutableStateListOf(
            EunoiaMessage(
                id = "1",
                senderName = partnerName,
                isPartner = true,
                messageText = "Connected to Eunoia session 🎧",
                timestamp = "Just now"
            )
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "EunoiaPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

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
            // TOP AREA: Header & Premium Glassmorphic Invite Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E26))
                        .border(1.5.dp, if (sessionStatus == EunoiaSessionStatus.CONNECTED) Color.White else Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!partnerPhotoUri.isNullOrBlank()) {
                        AsyncImage(
                            model = partnerPhotoUri,
                            contentDescription = partnerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Partner Avatar",
                            tint = if (sessionStatus == EunoiaSessionStatus.CONNECTED) Color.White else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (sessionStatus == EunoiaSessionStatus.CONNECTED) "Eunoia with $partnerName" else "Eunoia Listen Together",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextStrong,
                                fontSize = 16.sp
                            )
                        )
                        if (sessionStatus == EunoiaSessionStatus.CONNECTED) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = "Synced",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = when (sessionStatus) {
                            EunoiaSessionStatus.CONNECTED -> "Synced • Auto Queue Union (10 max)"
                            EunoiaSessionStatus.INVITE_SENT -> "Invite sent to $partnerDeviceId..."
                            EunoiaSessionStatus.REJECTED_BUSY -> "Partner is busy right now"
                            else -> "Tap Invite to pair via Device ID"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = when (sessionStatus) {
                                EunoiaSessionStatus.CONNECTED -> Color(0xFF34D399)
                                EunoiaSessionStatus.REJECTED_BUSY -> Color(0xFFEF4444)
                                else -> TextMuted
                            },
                            fontSize = 11.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                        .clickable { isInviteDialogOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Invite Partner",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = currentReactionEvent != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                currentReactionEvent?.let { ev ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = ev,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // CENTER: Showcase Card with ULTRA-THIN 4dp MINI-SEEKBAR
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                playerManager.toggleLike(activeSong)
                                sessionManager.triggerFlyingReaction("❤️")
                                Toast.makeText(context, "Sent ❤️", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .scale(if (isPlaying) pulseScale else 1f)
                        ) {
                            AsyncImage(
                                model = activeSong.thumbnail,
                                contentDescription = activeSong.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeSong.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextStrong,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeSong.author,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextMuted,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        playerManager.playPrevious()
                                        sessionManager.checkAndCorrectPlaybackDrift(playerManager, positionMs)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        playerManager.togglePlayPause()
                                        sessionManager.checkAndCorrectPlaybackDrift(playerManager, positionMs)
                                    },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        playerManager.playNext()
                                        sessionManager.checkAndCorrectPlaybackDrift(playerManager, positionMs)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Ultra-Thin Miniplayer-Style Seekbar Line & Timestamps
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(sliderProgress)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White)
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))

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

            // Quick Reaction Flying Emoji Bar
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
                                Toast.makeText(context, "Sent $reactionEmoji ❤️", Toast.LENGTH_SHORT).show()
                                currentReactionEvent = "Sent flying reaction $reactionEmoji"
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

            // MUSIC CHAT STREAM (INPUT BAR RESPONDS TO SOFT KEYBOARD VIA IME PADDING ONLY ON INPUT)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
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
                            text = "Share ID: $myDeviceId",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentTeal,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn(
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

                    // LIFTED CHAT EDITTEXT BAR (imePadding ONLY ON INPUT ROW FOR PERFECT KEYBOARD OVERLAY)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = chatInputValue,
                            onValueChange = { chatInputValue = it },
                            placeholder = { Text("Send lyric or message...", color = TextMuted, fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Send
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSend = { sendMessageAction() }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { sendMessageAction() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color.Black, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        flyingReactions.forEach { reaction ->
            FlyingReactionParticle(reaction = reaction)
        }

        if (isInviteDialogOpen) {
            AlertDialog(
                onDismissRequest = { isInviteDialogOpen = false },
                containerColor = Color(0xFF181822),
                title = {
                    Text(
                        text = "Listen Together Invite",
                        style = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Your Unified Device & Share ID:",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = myDeviceId,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = AccentTeal,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Device ID", myDeviceId))
                                    Toast.makeText(context, "Copied Device ID: $myDeviceId", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy ID", tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Enter Partner's Device ID:",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                        )
                        OutlinedTextField(
                            value = inviteInputId,
                            onValueChange = { inviteInputId = it },
                            placeholder = { Text("e.g. A1B2C3 or @A1B2C3", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    sessionManager.receiveSimulatedInvite("SPASIBA-8F329A")
                                    isInviteDialogOpen = false
                                }
                            ) {
                                Text("Simulate Incoming Request", color = AccentTeal, fontSize = 11.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (inviteInputId.isNotBlank()) {
                                sessionManager.sendInvite(inviteInputId)
                                isInviteDialogOpen = false
                                Toast.makeText(context, "Invite sent to ${inviteInputId.uppercase()}", Toast.LENGTH_SHORT).show()
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
                }
            )
        }

        if (sessionStatus == EunoiaSessionStatus.INVITE_RECEIVED && pendingInviteSender != null) {
            val sender = pendingInviteSender!!
            AlertDialog(
                onDismissRequest = { },
                containerColor = Color(0xFF1E1E2A),
                title = {
                    Text(
                        text = "Incoming Eunoia Co-Listen Request 🎵",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Text(
                        text = "Device $sender wants to Listen Together with you in Eunoia! Accept to sync music & chat?",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            sessionManager.acceptInvite(sender, playerManager)
                            messages.add(
                                EunoiaMessage(
                                    id = System.currentTimeMillis().toString(),
                                    senderName = "...",
                                    isPartner = true,
                                    messageText = "Joined your Eunoia co-listening session! 🎧",
                                    timestamp = "Just now"
                                )
                            )
                            Toast.makeText(context, "Connected to Eunoia session! Queues automatically unioned (10 max) 🎵", Toast.LENGTH_LONG).show()
                        }
                    ) {
                        Text("Accept", color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            sessionManager.rejectInvite(sender)
                            Toast.makeText(context, "Invite rejected", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Reject (Busy)", color = Color(0xFFEF4444))
                    }
                }
            )
        }
    }
}
