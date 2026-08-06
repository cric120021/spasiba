package app.spasiba.nativeapp2.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.spasiba.nativeapp2.MainActivity
import app.spasiba.nativeapp2.data.engine.EunoiaSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EunoiaInviteReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ACCEPT_INVITE = "app.spasiba.nativeapp2.ACTION_ACCEPT_INVITE"
        const val ACTION_REJECT_INVITE = "app.spasiba.nativeapp2.ACTION_REJECT_INVITE"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_INVITER_ID = "extra_inviter_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
        val inviterId = intent.getStringExtra(EXTRA_INVITER_ID) ?: ""

        Log.d("EunoiaInviteReceiver", "Received action $action for session $sessionId from $inviterId")

        // Auto-dismiss notification on status bar shade
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(SpasibaFcmService.NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e("EunoiaInviteReceiver", "Failed canceling notification: ${e.message}")
        }

        val sessionManager = EunoiaSessionManager.getInstance(context)
        val playerManager = (context.applicationContext as app.spasiba.nativeapp2.SpasibaApplication).playerManager

        when (action) {
            ACTION_ACCEPT_INVITE -> {
                CoroutineScope(Dispatchers.Main).launch {
                    sessionManager.acceptInvite(inviterId, playerManager, sessionId)

                    val mainIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("open_eunoia_screen", true)
                        putExtra(EXTRA_SESSION_ID, sessionId)
                    }
                    context.startActivity(mainIntent)
                }
            }
            ACTION_REJECT_INVITE -> {
                CoroutineScope(Dispatchers.Main).launch {
                    sessionManager.rejectInvite(inviterId)
                }
            }
        }
    }
}
