package app.spasiba.nativeapp2.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import app.spasiba.nativeapp2.MainActivity
import app.spasiba.nativeapp2.data.engine.FcmTokenManager
import app.spasiba.nativeapp2.data.engine.EunoiaSessionManager

class SpasibaFcmService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "eunoia_push_invites"
        const val NOTIFICATION_ID = 8888
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("SpasibaFcmService", "New FCM token received: $token")
        FcmTokenManager.getInstance(this).registerTokenWithBackend(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("SpasibaFcmService", "FCM payload received from ${remoteMessage.from}")

        val data = remoteMessage.data
        val messageType = data["type"] ?: ""

        if (messageType == "EUNOIA_INVITE") {
            val sessionId = data["sessionId"] ?: ""
            val inviterDeviceId = data["inviterDeviceId"] ?: "Partner"
            val inviterName = data["inviterName"] ?: inviterDeviceId
            val songTitle = data["songTitle"] ?: "Music Track"

            // Notify in-app session manager with real sessionId
            EunoiaSessionManager.getInstance(this).receiveSimulatedInvite(inviterDeviceId, sessionId)
            app.spasiba.nativeapp2.ui.screens.NotificationState.updateCount(this)

            // Display high priority notification with Accept / Reject buttons
            showInviteNotification(sessionId, inviterDeviceId, inviterName, songTitle)
        } else if (messageType == "FRIEND_REQUEST") {
            val senderId = data["senderId"] ?: data["fromId"] ?: data["inviterDeviceId"] ?: ""
            val senderName = data["senderName"] ?: data["inviterName"] ?: "Friend (${senderId.takeLast(6)})"
            val photoUri = data["photoUri"] ?: data["avatarUrl"]
            if (senderId.isNotBlank()) {
                Log.d("SpasibaFcmService", "FCM FRIEND_REQUEST received from senderId=$senderId ($senderName)")
                app.spasiba.nativeapp2.data.engine.EunoiaFriendManager.getInstance(this).addOrSendFriendRequest(
                    friendId = senderId,
                    name = senderName,
                    isOutgoing = false,
                    photoUri = photoUri
                )
                EunoiaSessionManager.getInstance(this).friendEventFlow.value = System.currentTimeMillis()
                app.spasiba.nativeapp2.ui.screens.NotificationState.updateCount(this)
            }
        } else if (messageType == "FRIEND_ACCEPT") {
            val senderId = data["senderId"] ?: data["fromId"] ?: ""
            val senderName = data["senderName"] ?: data["inviterName"]
            if (senderId.isNotBlank()) {
                Log.d("SpasibaFcmService", "FCM FRIEND_ACCEPT received from senderId=$senderId")
                app.spasiba.nativeapp2.data.engine.EunoiaFriendManager.getInstance(this).acceptFriendRequest(senderId, newName = senderName)
                EunoiaSessionManager.getInstance(this).friendEventFlow.value = System.currentTimeMillis()
                app.spasiba.nativeapp2.ui.screens.NotificationState.updateCount(this)
            }
        } else if (messageType == "FRIEND_REMOVE") {
            val senderId = data["senderId"] ?: data["fromId"] ?: ""
            if (senderId.isNotBlank()) {
                Log.d("SpasibaFcmService", "Silent FCM FRIEND_REMOVE received for senderId=$senderId")
                app.spasiba.nativeapp2.data.engine.EunoiaFriendManager.getInstance(this).removeFriend(senderId)
                EunoiaSessionManager.getInstance(this).friendEventFlow.value = System.currentTimeMillis()
                app.spasiba.nativeapp2.ui.screens.NotificationState.updateCount(this)
            }
        }
    }

    private fun showInviteNotification(
        sessionId: String,
        inviterDeviceId: String,
        inviterName: String,
        songTitle: String
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Eunoia Session Invites",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority notification channel for Eunoia shared listening invites"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap intent -> Open app into Eunoia
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_eunoia_screen", true)
            putExtra("sessionId", sessionId)
        }
        val pendingAppIntent = PendingIntent.getActivity(
            this,
            100,
            appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Accept Action Intent
        val acceptIntent = Intent(this, EunoiaInviteReceiver::class.java).apply {
            action = EunoiaInviteReceiver.ACTION_ACCEPT_INVITE
            putExtra(EunoiaInviteReceiver.EXTRA_SESSION_ID, sessionId)
            putExtra(EunoiaInviteReceiver.EXTRA_INVITER_ID, inviterDeviceId)
        }
        val pendingAcceptIntent = PendingIntent.getBroadcast(
            this,
            101,
            acceptIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Reject Action Intent
        val rejectIntent = Intent(this, EunoiaInviteReceiver::class.java).apply {
            action = EunoiaInviteReceiver.ACTION_REJECT_INVITE
            putExtra(EunoiaInviteReceiver.EXTRA_SESSION_ID, sessionId)
            putExtra(EunoiaInviteReceiver.EXTRA_INVITER_ID, inviterDeviceId)
        }
        val pendingRejectIntent = PendingIntent.getBroadcast(
            this,
            102,
            rejectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🎧 Listening Invite from $inviterName")
            .setContentText("Song: $songTitle • Tap to listen together")
            .setContentIntent(pendingAppIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(android.R.drawable.ic_menu_add, "Accept", pendingAcceptIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reject", pendingRejectIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
