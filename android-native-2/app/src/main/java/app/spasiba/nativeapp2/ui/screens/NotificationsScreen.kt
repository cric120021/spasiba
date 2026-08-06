package app.spasiba.nativeapp2.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp2.data.engine.EunoiaFriendManager
import app.spasiba.nativeapp2.data.engine.EunoiaFriendStatus
import app.spasiba.nativeapp2.data.engine.EunoiaSessionManager
import app.spasiba.nativeapp2.data.engine.EunoiaSessionStatus
import app.spasiba.nativeapp2.ui.theme.AccentTeal
import app.spasiba.nativeapp2.ui.theme.TextMuted
import coil.compose.rememberAsyncImagePainter

object NotificationState {
    val unreadCount = mutableIntStateOf(0)

    fun getDismissedIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("dismissed_notification_ids", emptySet()) ?: emptySet()
    }

    fun dismissNotification(context: Context, id: String) {
        val prefs = context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE)
        val current = prefs.getStringSet("dismissed_notification_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(id)
        prefs.edit().putStringSet("dismissed_notification_ids", current).apply()
        updateCount(context)
    }

    fun clearAllNotifications(context: Context, ids: List<String>) {
        val prefs = context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE)
        val current = prefs.getStringSet("dismissed_notification_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.addAll(ids)
        prefs.edit().putStringSet("dismissed_notification_ids", current).apply()
        markAllAsSeen(context)
    }

    fun updateCount(context: Context) {
        val prefs = context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE)
        val lastSeenTime = prefs.getLong("last_notifications_seen_timestamp", 0L)
        val dismissed = getDismissedIds(context)

        val pendingFriendRequests = EunoiaFriendManager.getInstance(context)
            .getPendingRequests()
            .count { f ->
                f.status == EunoiaFriendStatus.PENDING_INCOMING &&
                !dismissed.contains(f.friendId) &&
                (f.addedAtMs > lastSeenTime || lastSeenTime == 0L)
            }
        
        val sessionManager = EunoiaSessionManager.getInstance(context)
        val currentSender = sessionManager.pendingInviteSender.value
        val currentSessionId = sessionManager.sessionId.value ?: currentSender ?: "active"
        val inviteId = "room_invite_$currentSessionId"

        val hasPendingInvite = (sessionManager.sessionStatus.value == EunoiaSessionStatus.INVITE_RECEIVED || currentSender != null) &&
                !dismissed.contains(inviteId)

        val count = pendingFriendRequests + (if (hasPendingInvite) 1 else 0)
        unreadCount.intValue = count
    }

    fun markAllAsSeen(context: Context) {
        val prefs = context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_notifications_seen_timestamp", System.currentTimeMillis()).apply()
        unreadCount.intValue = 0
    }
}

data class EunoiaRequestItem(
    val id: String,
    val senderName: String,
    val avatarUrl: String,
    val message: String,
    val timeAgo: String,
    val type: String, // "FRIEND", "ROOM"
    val isNew: Boolean = false,
    var actionState: String? = null
)

@Composable
fun NotificationsScreen(
    onBackClick: () -> Unit,
    onNavigateToRoom: (String) -> Unit = {},
    onNavigateToEunoia: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val friendManager = remember { EunoiaFriendManager.getInstance(context) }
    val sessionManager = remember { EunoiaSessionManager.getInstance(context) }

    val friendEventTimestamp by sessionManager.friendEventFlow.collectAsState()
    val sessionStatus by sessionManager.sessionStatus.collectAsState()
    val pendingInviteSender by sessionManager.pendingInviteSender.collectAsState()
    val partnerName by sessionManager.partnerName.collectAsState()

    val userPrefs = remember { context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE) }
    val lastSeenTime = remember { userPrefs.getLong("last_notifications_seen_timestamp", 0L) }

    // When the user opens the notification screen, mark notifications as seen & reset unread count
    LaunchedEffect(Unit) {
        NotificationState.markAllAsSeen(context)
    }

    val dismissedIds = remember(friendEventTimestamp, sessionStatus, pendingInviteSender) { NotificationState.getDismissedIds(context) }

    val pendingFriendRequests = remember(friendEventTimestamp, dismissedIds) {
        friendManager.getPendingRequests()
            .filter { it.status == EunoiaFriendStatus.PENDING_INCOMING && !dismissedIds.contains(it.friendId) }
            .map { f ->
                EunoiaRequestItem(
                    id = f.friendId,
                    senderName = f.name,
                    avatarUrl = f.photoUri ?: "",
                    message = "sent you a friend request on Eunoia",
                    timeAgo = "Just now",
                    type = "FRIEND",
                    isNew = f.addedAtMs > lastSeenTime || lastSeenTime == 0L
                )
            }
    }

    val pendingRoomInvites = remember(sessionStatus, pendingInviteSender, partnerName, dismissedIds) {
        val currentSender = pendingInviteSender
        val currentSessionId = sessionManager.sessionId.value ?: currentSender ?: "active"
        val inviteId = "room_invite_$currentSessionId"

        if ((sessionStatus == EunoiaSessionStatus.INVITE_RECEIVED || currentSender != null) && !dismissedIds.contains(inviteId)) {
            val partnerFriend = if (currentSender != null) friendManager.getFriends().find { it.friendId.equals(currentSender, ignoreCase = true) } else null
            val senderName = partnerFriend?.displayName ?: partnerName?.takeIf { it.isNotBlank() } ?: currentSender ?: "Partner"
            listOf(
                EunoiaRequestItem(
                    id = inviteId,
                    senderName = senderName,
                    avatarUrl = partnerFriend?.photoUri ?: "",
                    message = "invited you to a listening room on Eunoia",
                    timeAgo = "Just now",
                    type = "ROOM",
                    isNew = true
                )
            )
        } else {
            emptyList()
        }
    }

    val allNotifications = remember { mutableStateListOf<EunoiaRequestItem>() }

    LaunchedEffect(pendingFriendRequests, pendingRoomInvites) {
        allNotifications.clear()
        allNotifications.addAll(pendingFriendRequests)
        allNotifications.addAll(pendingRoomInvites)
    }

    val userPhotoUri = remember { userPrefs.getString("profile_photo_uri", null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Activity Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 11.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 24.sp
                ),
                modifier = Modifier.weight(1f)
            )

            if (allNotifications.isNotEmpty()) {
                TextButton(
                    onClick = {
                        val idsToClear = allNotifications.map { it.id }
                        NotificationState.clearAllNotifications(context, idsToClear)
                        allNotifications.forEach { item ->
                            if (item.type == "FRIEND") {
                                friendManager.removeFriend(item.id)
                            }
                        }
                        allNotifications.clear()
                        sessionManager.dismissInviteLocally()
                    }
                ) {
                    Text(
                        text = "Clear All",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = AccentTeal,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Profile Avatar Icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onProfileClick)
            ) {
                if (!userPhotoUri.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(userPhotoUri),
                        contentDescription = "Profile Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // Notifications Content List or Empty State
        if (allNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF18181A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "No new notifications",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 22.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "You're all caught up! When friends send requests or invite you to listening rooms, they will appear here.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextMuted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "New Activity & Invites",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        )
                        Surface(
                            shape = CircleShape,
                            color = AccentTeal.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${allNotifications.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentTeal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                items(allNotifications.size, key = { allNotifications[it].id }) { idx ->
                    val item = allNotifications[idx]
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.04f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                if (item.avatarUrl.isNotBlank()) {
                                    Image(
                                        painter = rememberAsyncImagePainter(item.avatarUrl),
                                        contentDescription = item.senderName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(if (item.type == "ROOM") Color(0xFFEC4899) else AccentTeal),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (item.type == "ROOM") Icons.Default.Headphones else Icons.Default.PersonAdd,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                val requestAnnotatedText = buildAnnotatedString {
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                                        append(item.senderName)
                                    }
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Normal, color = Color.White)) {
                                        append(" ${item.message}")
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = requestAnnotatedText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            lineHeight = 19.sp
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (item.isNew && item.actionState == null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = AccentTeal
                                        ) {
                                            Text(
                                                text = "NEW",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp
                                                ),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = item.timeAgo,
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 12.sp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (item.actionState == "ACCEPTED") {
                                    Text(
                                        text = "✓ Friend Request Accepted",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = AccentTeal, fontWeight = FontWeight.Bold)
                                    )
                                } else if (item.actionState == "DECLINED") {
                                    Text(
                                        text = "Request Declined",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                                    )
                                } else if (item.type == "FRIEND") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = {
                                                item.actionState = "ACCEPTED"
                                                friendManager.acceptFriendRequest(item.id)
                                                NotificationState.dismissNotification(context, item.id)
                                                onNavigateToEunoia()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = Color.Black),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("Accept", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                item.actionState = "DECLINED"
                                                friendManager.removeFriend(item.id)
                                                NotificationState.dismissNotification(context, item.id)
                                                allNotifications.remove(item)
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.height(34.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                        ) {
                                            Text("Decline", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            item.actionState = "JOINED"
                                            NotificationState.dismissNotification(context, item.id)
                                            onNavigateToEunoia()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899), contentColor = Color.White),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Join Room", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    NotificationState.dismissNotification(context, item.id)
                                    if (item.type == "FRIEND") {
                                        friendManager.removeFriend(item.id)
                                    } else {
                                        sessionManager.dismissInviteLocally()
                                    }
                                    allNotifications.remove(item)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
