package app.spasiba.nativeapp2.ui.screens

import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp2.data.engine.EunoiaFriend
import app.spasiba.nativeapp2.data.engine.EunoiaFriendManager
import app.spasiba.nativeapp2.data.engine.EunoiaSessionManager
import app.spasiba.nativeapp2.ui.theme.TextMuted
import app.spasiba.nativeapp2.ui.theme.TextStrong

@Composable
fun FriendProfileDetailsScreen(
    friendId: String,
    friendName: String,
    onBackClick: () -> Unit,
    onOpenChat: () -> Unit,
    onRemoveFriend: (String) -> Unit = {},
    onQuickInvite: (EunoiaFriend) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val sessionManager = remember { EunoiaSessionManager.getInstance(context) }
    val friendManager = remember { EunoiaFriendManager.getInstance(context) }

    val friendEventTime by sessionManager.friendEventFlow.collectAsState()
    val chatMessages by remember(friendEventTime, friendId) {
        mutableStateOf(friendManager.getChatHistory(friendId, days = 7))
    }

    val matchedFriend = remember(friendEventTime, friendId) {
        friendManager.getFriends().find { it.friendId.equals(friendId.trim(), ignoreCase = true) }
    }

    val isStillFriend = remember(friendEventTime, friendId) {
        friendManager.getAcceptedFriends().any { it.friendId.equals(friendId.trim(), ignoreCase = true) }
    }

    LaunchedEffect(isStillFriend) {
        if (!isStillFriend) {
            Toast.makeText(context, "You are no longer friends", Toast.LENGTH_SHORT).show()
            onBackClick()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Music, 1 = GIFs/Stickers, 2 = Links/Docs
    var isMuted by remember { mutableStateOf(false) }
    var isDeleteAllDialogOpen by remember { mutableStateOf(false) }
    var isRemoveFriendDialogOpen by remember { mutableStateOf(false) }

    val musicItems = remember(chatMessages) {
        chatMessages.filter { it.messageText.contains("http") || it.messageText.startsWith("🎵") }
    }
    val gifItems = remember(chatMessages) {
        chatMessages.filter { it.messageText.contains("[GIF:") || it.messageText.contains("[STICKER:") }
    }
    val docItems = remember(chatMessages) {
        chatMessages.filter { it.messageText.contains("www.") || it.messageText.contains(".com") || it.messageText.contains("doc") }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Top Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Contact Info",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    )
                }
            }

            // Big Profile Photo Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22222E)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!matchedFriend?.photoUri.isNullOrBlank()) {
                            coil.compose.AsyncImage(
                                model = matchedFriend.photoUri,
                                contentDescription = friendName,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Text(
                                text = friendName.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 46.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = friendName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextStrong,
                            fontSize = 22.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(friendId))
                                Toast.makeText(context, "Copied ID: $friendId", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = friendId,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFFCCCCCC),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy ID",
                            tint = Color(0xFFCCCCCC),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Quick Actions Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Chat Action
                    Surface(
                        onClick = onOpenChat,
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E1E28),
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Chat", tint = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Chat", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Quick Invite Action
                    Surface(
                        onClick = {
                            val friendObj = EunoiaFriend(friendId = friendId, name = friendName)
                            onQuickInvite(friendObj)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E1E28),
                        modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Rounded.Bolt, contentDescription = "Invite", tint = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Invite", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Mute Notifications Toggle Action
                    Surface(
                        onClick = {
                            isMuted = !isMuted
                            Toast.makeText(context, if (isMuted) "Muted notifications for $friendName" else "Unmuted notifications", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E1E28),
                        modifier = Modifier.weight(1f).padding(start = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                contentDescription = "Mute",
                                tint = if (isMuted) Color(0xFFF59E0B) else Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(if (isMuted) "Muted" else "Mute", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Shared Media, Links & Docs Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SHARED MEDIA, LINKS & DOCUMENTS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFA0A0A0),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.2.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Category Tabs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChipTab(
                                label = "Music (${musicItems.size})",
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 }
                            )
                            FilterChipTab(
                                label = "GIFs (${gifItems.size})",
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 }
                            )
                            FilterChipTab(
                                label = "Links & Docs (${docItems.size})",
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val activeList = when (selectedTab) {
                            0 -> musicItems
                            1 -> gifItems
                            else -> docItems
                        }

                        if (activeList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No shared items in this category yet.\nItems sent over 7 days show up here!",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 12.sp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                activeList.take(5).forEach { item ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF22222C),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = when (selectedTab) {
                                                    0 -> Icons.Default.MusicNote
                                                    1 -> Icons.Default.PhotoLibrary
                                                    else -> Icons.Default.Link
                                                },
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = item.messageText,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Management Options
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column {
                        // Clear Chat for Both
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDeleteAllDialogOpen = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Clear Chat History", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Deletes 7-day messages for both you and $friendName", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        // Unfriend / Remove
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isRemoveFriendDialogOpen = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PersonRemove, contentDescription = "Unfriend", tint = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Unfriend $friendName", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Remove connection from both devices", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialogs
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

    if (isRemoveFriendDialogOpen) {
        AlertDialog(
            onDismissRequest = { isRemoveFriendDialogOpen = false },
            title = { Text("Remove $friendName?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove $friendName? Both of you will be unfriended.", color = TextMuted) },
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

@Composable
private fun FilterChipTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.White else Color(0xFF1E1E2C),
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) Color.Black else Color.White,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
