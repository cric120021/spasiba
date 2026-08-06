package app.spasiba.nativeapp2.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp2.data.engine.EunoiaFriend
import app.spasiba.nativeapp2.data.engine.EunoiaFriendManager
import app.spasiba.nativeapp2.data.engine.EunoiaFriendStatus
import app.spasiba.nativeapp2.data.engine.EunoiaSessionManager
import app.spasiba.nativeapp2.ui.components.SilverBrandHeader
import app.spasiba.nativeapp2.ui.theme.AccentTeal
import app.spasiba.nativeapp2.ui.theme.TextMuted
import app.spasiba.nativeapp2.ui.theme.TextStrong
import coil.compose.AsyncImage

@Composable
fun FriendsListScreen(
    onBackClick: () -> Unit = {},
    onQuickInviteFriend: (EunoiaFriend) -> Unit = {},
    onOpenFriendChat: (EunoiaFriend) -> Unit = {}
) {
    val context = LocalContext.current
    val friendManager = remember { EunoiaFriendManager.getInstance(context) }
    val sessionManager = remember { EunoiaSessionManager.getInstance(context) }

    val friendEventTime by sessionManager.friendEventFlow.collectAsState()

    var friendsList by remember(friendEventTime) { mutableStateOf(friendManager.getFriends()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isAddFriendDialogOpen by remember { mutableStateOf(false) }

    val acceptedFriends = remember(friendsList) { friendsList.filter { it.status == EunoiaFriendStatus.ACCEPTED } }
    val pendingRequests = remember(friendsList) {
        friendsList.filter { it.status == EunoiaFriendStatus.PENDING_INCOMING || it.status == EunoiaFriendStatus.PENDING_OUTGOING }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row aligned to match top padding & back button alignment
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 22.dp, bottom = 8.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "My Friends",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = "${acceptedFriends.size} Connections • ${pendingRequests.size} Pending",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    )
                }
                Button(
                    onClick = { isAddFriendDialogOpen = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Add Friend",
                        tint = Color.Black,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Friend", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Glassmorphic Custom Styled Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = AccentTeal,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = AccentTeal
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Friends (${acceptedFriends.size})",
                            color = if (selectedTab == 0) Color.White else TextMuted,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Pending / Requests (${pendingRequests.size})",
                            color = if (selectedTab == 1) AccentTeal else TextMuted,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main List Content
            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == 0) {
                    if (acceptedFriends.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No friends connected yet.\nTap '+ Add Friend' to send an invite by code!",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted, fontSize = 14.sp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(acceptedFriends, key = { it.friendId }) { friend ->
                                FriendCardItem(
                                    friend = friend,
                                    onInviteClick = { onQuickInviteFriend(friend) },
                                    onChatClick = { onOpenFriendChat(friend) }
                                )
                            }
                        }
                    }
                } else {
                    if (pendingRequests.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No pending friend requests.\nWhen friends invite you, their requests will show up here!",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted, fontSize = 14.sp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val incoming = pendingRequests.filter { it.status == EunoiaFriendStatus.PENDING_INCOMING }
                            val outgoing = pendingRequests.filter { it.status == EunoiaFriendStatus.PENDING_OUTGOING }

                            if (incoming.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "INCOMING REQUESTS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = AccentTeal,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.4.sp
                                        ),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                items(incoming, key = { "inc_${it.friendId}" }) { friend ->
                                    PendingIncomingItem(
                                        friend = friend,
                                        onAccept = {
                                            sessionManager.acceptFriendRequestSignal(friend.friendId, friend.name)
                                            friendsList = friendManager.getFriends()
                                            selectedTab = 0
                                            Toast.makeText(context, "Accepted friend request from ${friend.name}!", Toast.LENGTH_SHORT).show()
                                        },
                                        onDecline = {
                                            sessionManager.removeFriendSignal(friend.friendId)
                                            friendsList = friendManager.getFriends()
                                        }
                                    )
                                }
                            }

                            if (outgoing.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "OUTGOING INVITES",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFF59E0B),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.4.sp
                                        ),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                items(outgoing, key = { "out_${it.friendId}" }) { friend ->
                                    PendingOutgoingItem(
                                        friend = friend,
                                        onCancel = {
                                            sessionManager.removeFriendSignal(friend.friendId)
                                            friendsList = friendManager.getFriends()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Friend Dialog
    if (isAddFriendDialogOpen) {
        var friendCodeInput by remember { mutableStateOf("") }
        var friendNameInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { isAddFriendDialogOpen = false },
            title = {
                Text("Send Friend Request", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                            focusedBorderColor = AccentTeal,
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
                            focusedBorderColor = AccentTeal,
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
                            selectedTab = 1
                            Toast.makeText(context, "Friend request sent to $friendCodeInput!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
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

@Composable
private fun FriendCardItem(
    friend: EunoiaFriend,
    onInviteClick: () -> Unit,
    onChatClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChatClick() },
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentTeal.copy(alpha = 0.2f)),
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
                            color = AccentTeal,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextStrong,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = friend.friendId,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                )
            }

            IconButton(
                onClick = onChatClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Chat",
                    tint = AccentTeal,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Button(
                onClick = onInviteClick,
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = "Invite",
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Invite", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PendingIncomingItem(
    friend: EunoiaFriend,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentTeal.copy(alpha = 0.2f)),
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
                            color = AccentTeal,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = "Sent you a friend request",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AccentTeal,
                        fontSize = 11.sp
                    )
                )
            }

            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Accept",
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Accept", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(onClick = onDecline, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Decline",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PendingOutgoingItem(
    friend: EunoiaFriend,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
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
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = "Request pending...",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFFF59E0B),
                        fontSize = 11.sp
                    )
                )
            }

            IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
