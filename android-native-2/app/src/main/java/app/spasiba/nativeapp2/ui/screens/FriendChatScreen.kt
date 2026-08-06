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
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.lazy.LazyRow
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

    val dateFormatter = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // WhatsApp-style Top Header Bar (Clicking avatar/name opens Contact Details page)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF141420),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
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

                // Clickable User Profile Area -> Opens Friend Profile Details Page
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenDetails() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
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
                                    color = Color(0xFF10B981),
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
                                color = TextStrong,
                                fontSize = 16.sp
                            )
                        )
                        Text(
                            text = "Online • Tap for info",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF10B981),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // ⚡ Quick Invite Button
                IconButton(
                    onClick = {
                        val friendObj = EunoiaFriend(friendId = friendId, name = friendName)
                        onQuickInvite(friendObj)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = "Quick Invite",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 🗑️ Delete All Messages Button
                IconButton(onClick = { isDeleteAllDialogOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete All Messages",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 👤❌ Remove Friend Button
                IconButton(onClick = { isRemoveFriendDialogOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.PersonRemove,
                        contentDescription = "Remove Friend",
                        tint = Color(0xFFEF4444).copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Chat Message Stream
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            if (chatMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "💬 No messages in the last 7 days",
                                style = MaterialTheme.typography.titleSmall.copy(color = TextStrong)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Say hi to $friendName! Share text, stickers or GIFs.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(chatMessages, key = { it.id }) { msg ->
                        val isMe = !msg.isPartner
                        val bubbleColor = if (isMe) Color(0xFF059669) else Color(0xFF1E1E2C)
                        val align = if (isMe) Alignment.End else Alignment.Start

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = align
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isMe) 16.dp else 2.dp,
                                    bottomEnd = if (isMe) 2.dp else 16.dp
                                ),
                                color = bubbleColor,
                                shadowElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth(0.82f)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = if (isMe) "You" else msg.senderName,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isMe) Color(0xFFA7F3D0) else Color(0xFF10B981),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))

                                    val isGif = msg.messageText.startsWith("[GIF:") || msg.messageText.contains("tenor.com") || msg.messageText.contains(".gif")
                                    if (isGif) {
                                        val gifUrl = if (msg.messageText.startsWith("[GIF:")) {
                                            msg.messageText.removePrefix("[GIF:").removePrefix("[STICKER:").removeSuffix("]")
                                        } else msg.messageText

                                        if (gifUrl.startsWith("http")) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(vertical = 4.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color.Black.copy(alpha = 0.4f))
                                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                            ) {
                                                AsyncImage(
                                                    model = gifUrl,
                                                    contentDescription = "Tenor GIF",
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                    modifier = Modifier
                                                        .width(220.dp)
                                                        .height(160.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                )
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color.Black.copy(alpha = 0.35f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "🎬 GIF",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = Color(0xFF10B981),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp
                                                        )
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = gifUrl,
                                                        style = MaterialTheme.typography.bodyLarge.copy(
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 15.sp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = msg.messageText,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = dateFormatter.format(Date(msg.timestampMs)),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 9.sp
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

        // Bottom WhatsApp-style Message Input Bar with GIF/Sticker Picker Trigger
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF141420),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // GIF & Sticker Picker Trigger Button
                IconButton(
                    onClick = { isGifStickerPickerOpen = !isGifStickerPickerOpen },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEmotions,
                        contentDescription = "GIF & Stickers",
                        tint = if (isGifStickerPickerOpen) Color(0xFF10B981) else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = { Text("Message $friendName...", color = TextMuted, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedContainerColor = Color(0xFF1E1E2C),
                        unfocusedContainerColor = Color(0xFF1E1E2C)
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        val text = messageInput.trim()
                        if (text.isNotBlank()) {
                            sessionManager.sendFriendChatMessageSignal(friendId, text)
                            messageInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // Telegram / WhatsApp Style Tenor GIF Search Modal
    if (isGifStickerPickerOpen) {
        var searchQuery by remember { mutableStateOf("") }
        var gifResults by remember { mutableStateOf<List<TenorGifItem>>(emptyList()) }
        var isLoadingGifs by remember { mutableStateOf(false) }

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
                        Text("Search GIFs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TENOR",
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(Color(0xFF10B981).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
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
                            focusedBorderColor = Color(0xFF10B981),
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
