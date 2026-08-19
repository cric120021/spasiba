package app.spasiba.nativeapp2.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import coil.compose.AsyncImage
import app.spasiba.nativeapp2.data.api.TenorGifApi
import app.spasiba.nativeapp2.data.api.TenorGifItem
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp2.data.engine.EunoiaFriend
import app.spasiba.nativeapp2.data.engine.EunoiaFriendChatMessage
import app.spasiba.nativeapp2.data.engine.EunoiaFriendManager
import app.spasiba.nativeapp2.data.engine.EunoiaSessionManager
import app.spasiba.nativeapp2.ui.theme.TextMuted
import app.spasiba.nativeapp2.ui.theme.TextStrong
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FriendChatScreen(
    friendId: String,
    friendName: String,
    onBackClick: () -> Unit,
    onOpenDetails: () -> Unit = {},
    onRemoveFriend: (String) -> Unit = {},
    onQuickInvite: (EunoiaFriend) -> Unit = {}
) {
    val context = LocalContext.current
    val sessionManager = remember { EunoiaSessionManager.getInstance(context) }
    val friendManager = remember { EunoiaFriendManager.getInstance(context) }

    val friendEventTime by sessionManager.friendEventFlow.collectAsState()
    val matchedFriend = remember(friendEventTime, friendId) {
        friendManager.getFriends().find { it.friendId.equals(friendId.trim(), ignoreCase = true) }
    }
    val displayName = matchedFriend?.name ?: friendName

    val chatMessages: List<EunoiaFriendChatMessage> by remember(friendEventTime, friendId) {
        mutableStateOf(friendManager.getChatHistory(friendId, days = 7))
    }

    var messageInput by remember { mutableStateOf("") }
    var isDeleteAllDialogOpen by remember { mutableStateOf(false) }
    var isRemoveFriendDialogOpen by remember { mutableStateOf(false) }
    var isGifStickerPickerOpen by remember { mutableStateOf(false) }

    val isStillFriend = remember(friendEventTime, friendId) {
        friendManager.getAcceptedFriends().any { it.friendId.equals(friendId.trim(), ignoreCase = true) }
    }

    LaunchedEffect(isStillFriend) {
        if (!isStillFriend) {
            Toast.makeText(context, "You are no longer friends", Toast.LENGTH_SHORT).show()
            onBackClick()
        }
    }

    val listState = rememberLazyListState()

    // Scroll to bottom when messages update
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val dateFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // WhatsApp Dark Top Header Bar (Matching Main App Black Theme)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF0F0F0F),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF1F1F1F))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                val matchedFriend = remember(friendEventTime, friendId) {
                    friendManager.getFriends().find { it.friendId.equals(friendId.trim(), ignoreCase = true) }
                }
                val displayName = matchedFriend?.name ?: friendName

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenDetails() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A3942)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!matchedFriend?.photoUri.isNullOrBlank()) {
                            AsyncImage(
                                model = matchedFriend?.photoUri,
                                contentDescription = "Friend Photo",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            ),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Online",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF00A884),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Quick Invite Bolt Button
                IconButton(
                    onClick = {
                        val friendObj = matchedFriend ?: EunoiaFriend(friendId = friendId, name = friendName)
                        onQuickInvite(friendObj)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = "Quick Invite",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 3-Dots Menu Button
                IconButton(onClick = { isDeleteAllDialogOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        var replyingToMessage by remember { mutableStateOf<EunoiaFriendChatMessage?>(null) }
        val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri: android.net.Uri? ->
            if (uri != null) {
                friendManager.addChatMessage(
                    friendId = friendId,
                    senderId = sessionManager.myDeviceId.value,
                    senderName = "You",
                    text = "",
                    isPartner = false,
                    replyToText = replyingToMessage?.messageText,
                    replyToSender = replyingToMessage?.senderName,
                    mediaUri = uri.toString()
                )
                replyingToMessage = null
            }
        }

        // Chat Message Stream
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            val matchedFriendPhoto = remember(friendEventTime, friendId) {
                friendManager.getFriends().find { it.friendId.equals(friendId.trim(), ignoreCase = true) }?.photoUri
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(chatMessages.size) { index ->
                    val msg = chatMessages[index]
                    val prevMsg = if (index > 0) chatMessages[index - 1] else null
                    val showDateSeparator = prevMsg == null || (msg.timestampMs - prevMsg.timestampMs > 1800000L)
                    if (showDateSeparator) {
                        val dateStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(msg.timestampMs))
                        DateSeparatorBadge(dateText = dateStr)
                    }

                    val isPartner = msg.isPartner
                    var isVoicePlaying by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = if (isPartner) Arrangement.Start else Arrangement.End,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(
                            horizontalAlignment = if (isPartner) Alignment.Start else Alignment.End,
                            modifier = Modifier.widthIn(max = 290.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomStart = if (isPartner) 2.dp else 12.dp,
                                    bottomEnd = if (isPartner) 12.dp else 2.dp
                                ),
                                color = if (isPartner) Color(0xFF2A2A34) else Color(0xFF005C4B),
                                shadowElevation = 1.dp,
                                modifier = Modifier.clickable { replyingToMessage = msg }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    if (!msg.replyToText.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Black.copy(alpha = 0.3f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 4.dp)
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(3.dp)
                                                        .fillMaxHeight()
                                                        .background(Color(0xFF00A884), RoundedCornerShape(2.dp))
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column {
                                                    Text(
                                                        text = msg.replyToSender ?: "Reply",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = Color(0xFF00A884),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 9.sp
                                                        )
                                                    )
                                                    Text(
                                                        text = msg.replyToText,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = Color.White.copy(alpha = 0.8f),
                                                            fontSize = 11.sp
                                                        ),
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (!msg.mediaUri.isNullOrBlank()) {
                                        AsyncImage(
                                            model = msg.mediaUri,
                                            contentDescription = "Shared Photo",
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1.4f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .padding(bottom = 4.dp)
                                        )
                                    }

                                    // Voice Message Bubble
                                    if (msg.isVoiceNote) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isPartner) Color(0xFF282834) else Color(0xFF00A884).copy(alpha = 0.25f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Call,
                                                    contentDescription = "Call Event",
                                                    tint = Color(0xFF00A884),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = if (isPartner) "Missed voice call" else "Voice Message",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                )
                                                Text(
                                                    text = if (isPartner) "Tap to call back" else "0:${msg.voiceDurationSec.toString().padStart(2, '0')}",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = Color(0xFF888888),
                                                        fontSize = 11.sp
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    if (msg.messageText.isNotBlank()) {
                                        val isGif = msg.messageText.startsWith("[GIF:") || msg.messageText.contains("tenor.com") || msg.messageText.contains(".gif")
                                        if (isGif) {
                                            val gifUrl = if (msg.messageText.startsWith("[GIF:")) {
                                                msg.messageText.removePrefix("[GIF:").removePrefix("[STICKER:").removeSuffix("]")
                                            } else msg.messageText

                                            if (gifUrl.startsWith("http")) {
                                                AsyncImage(
                                                    model = gifUrl,
                                                    contentDescription = "Tenor GIF",
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                    modifier = Modifier
                                                        .width(220.dp)
                                                        .height(160.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                )
                                            } else {
                                                Text(
                                                    text = msg.messageText,
                                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFE9EDEF), fontSize = 14.sp)
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = msg.messageText,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = Color(0xFFE9EDEF),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Normal
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = dateFormatter.format(Date(msg.timestampMs)),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF8696A0),
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (replyingToMessage != null) {
            Surface(
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                color = Color(0xFF202C33),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(24.dp)
                            .background(Color(0xFF00A884), RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Replying to ${replyingToMessage?.senderName}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF00A884),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = replyingToMessage?.messageText ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF8696A0),
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { replyingToMessage = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Reply",
                            tint = Color(0xFF8696A0),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Floating WhatsApp Dark Bottom Input Bar (Matching Screenshot_20260807_011156_WhatsApp.jpg)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Input Pill Container (Matching Top Header Bar #0F0F0F Color)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F0F0F),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F1F24))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Emoji / Sticker Button
                    IconButton(
                        onClick = { isGifStickerPickerOpen = !isGifStickerPickerOpen },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEmotions,
                            contentDescription = "Emoji",
                            tint = Color(0xFF8696A0),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (messageInput.isEmpty()) {
                            Text(
                                text = "Message",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF8696A0), fontSize = 15.sp)
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF00A884)),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFFE9EDEF),
                                fontSize = 15.sp
                            )
                        )
                    }

                    // Attachment Paperclip Button
                    IconButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach File",
                            tint = Color(0xFF8696A0),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Right Floating Voice / Send Circle (#00A884 Green Circle)
            val isSendActive = messageInput.isNotBlank()
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00A884))
                    .clickable {
                        if (isSendActive) {
                            val textToSend = messageInput.trim()
                            messageInput = ""
                            sessionManager.sendFriendChatMessageSignal(friendId, textToSend)
                            replyingToMessage = null
                        } else {
                            friendManager.addChatMessage(
                                friendId = friendId,
                                senderId = sessionManager.myDeviceId.value,
                                senderName = "You",
                                text = "",
                                isPartner = false,
                                replyToText = replyingToMessage?.messageText,
                                replyToSender = replyingToMessage?.senderName,
                                isVoiceNote = true,
                                voiceDurationSec = (3..8).random()
                            )
                            replyingToMessage = null
                            Toast.makeText(context, "Voice note sent 🎙️", Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSendActive) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                    contentDescription = "Action",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
    if (isGifStickerPickerOpen) {
        var searchQuery by remember { mutableStateOf("") }
        var gifResults by remember { mutableStateOf<List<TenorGifItem>>(emptyList()) }
        var isLoadingGifs by remember { mutableStateOf(false) }

        val quickEmojis = listOf("🔥", "🎵", "❤️", "😂", "😍", "👍", "🎉", "😎", "🙌", "✨", "🎧", "⚡", "💬", "🌟", "🥳", "🎶")
        val trendingTags = listOf("Trending", "Dance", "Music", "LOL", "Vibes", "Party", "Love", "Sad", "Hyped")

        LaunchedEffect(searchQuery) {
            isLoadingGifs = true
            gifResults = TenorGifApi.searchGifs(searchQuery)
            isLoadingGifs = false
        }

        AlertDialog(
            onDismissRequest = { isGifStickerPickerOpen = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Emojis & GIFs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TENOR",
                            color = Color(0xFF0284C7),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(Color(0xFF0284C7).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    IconButton(onClick = { isGifStickerPickerOpen = false }, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Quick Emojis Selection Row
                    Text("QUICK EMOJIS", color = Color(0xFF8696A0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        items(quickEmojis) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF202C33))
                                    .clickable {
                                        messageInput += emoji
                                        isGifStickerPickerOpen = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 18.sp)
                            }
                        }
                    }

                    // Search Input Area
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search Tenor GIFs...", color = TextMuted, fontSize = 13.sp) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = TextMuted) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00A884),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedContainerColor = Color(0xFF181824),
                            unfocusedContainerColor = Color(0xFF181824)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Trending Tag Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(trendingTags) { tag ->
                            val isSelected = (searchQuery.equals(tag, ignoreCase = true) || (tag == "Trending" && searchQuery.isEmpty()))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) Color(0xFF10B981) else Color(0xFF242436),
                                modifier = Modifier.clickable {
                                    searchQuery = if (tag == "Trending") "" else tag
                                }
                            ) {
                                Text(
                                    text = tag,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isLoadingGifs) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Loading GIFs...", color = TextMuted, fontSize = 13.sp)
                        }
                    } else if (gifResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No GIFs found", color = TextMuted, fontSize = 13.sp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            items(gifResults, key = { it.id }) { item ->
                                Box(
                                    modifier = Modifier
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1E1E2C))
                                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            sessionManager.sendFriendChatMessageSignal(friendId, "[GIF:${item.gifUrl}]")
                                            isGifStickerPickerOpen = false
                                        }
                                ) {
                                    AsyncImage(
                                        model = item.previewUrl,
                                        contentDescription = item.title,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = Color(0xFF12121E)
        )
    }

    // Confirmation Dialog: Delete All Messages
    if (isDeleteAllDialogOpen) {
        AlertDialog(
            onDismissRequest = { isDeleteAllDialogOpen = false },
            title = { Text("Delete Chat History?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("This will clear all 7-day chat messages for BOTH you and $friendName.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        sessionManager.deleteFriendChatHistorySignal(friendId)
                        isDeleteAllDialogOpen = false
                        Toast.makeText(context, "Chat history deleted for both!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete for Both", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteAllDialogOpen = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = Color(0xFF181824)
        )
    }

    // Confirmation Dialog: Remove Friend / Unfriend
    if (isRemoveFriendDialogOpen) {
        AlertDialog(
            onDismissRequest = { isRemoveFriendDialogOpen = false },
            title = { Text("Remove $friendName?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove $friendName from your friends list? Both of you will be unfriended.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        sessionManager.removeFriendSignal(friendId)
                        isRemoveFriendDialogOpen = false
                        onRemoveFriend(friendId)
                        onBackClick()
                        Toast.makeText(context, "Unfriended $friendName", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Unfriend", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isRemoveFriendDialogOpen = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = Color(0xFF181824)
        )
    }
}
