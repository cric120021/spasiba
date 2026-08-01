package com.spasiba.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.compose.runtime.mutableStateOf

const val NOTIFICATION_ID = 12345
const val CHANNEL_ID = "PlaybackChannel"

object AudioPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var activityContext: Context? = null
    var isPlaying = mutableStateOf(false)
    var currentSong = mutableStateOf<String?>(null)

    fun init(context: Context) {
        activityContext = context.applicationContext
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Playback"
            val descriptionText = "Music playback notifications"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = activityContext?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String): Notification {
        val activityIntent = Intent(activityContext, Class.forName("app.spasiba.nativeapp.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            activityContext,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(activityContext!!, CHANNEL_ID)
            .setContentTitle("🎵 Spasiba")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun play(url: String, title: String) {
        if (activityContext == null) {
            return
        }

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(url)
            setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setWakeMode(activityContext, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            prepareAsync()
            setOnPreparedListener {
                start()
                isPlaying.value = true
                currentSong.value = title
                activityContext?.startForegroundServiceCompat(PlaybackService::class.java, title)
            }
            setOnCompletionListener {
                stopPlayback()
            }
            setOnErrorListener { _, _, _ ->
                stopPlayback()
                true
            }
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        isPlaying.value = false
    }

    fun resume() {
        mediaPlayer?.start()
        isPlaying.value = true
    }

    fun stopPlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        }
        mediaPlayer = null
        isPlaying.value = false
        currentSong.value = null
        activityContext?.stopService(Intent(activityContext, PlaybackService::class.java))
    }

    class PlaybackService : Service() {
        private var playerManager: AudioPlayerManager? = null

        override fun onCreate() {
            super.onCreate()
            playerManager = AudioPlayerManager
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            val title = intent?.getStringExtra("title") ?: "Playing"
            val notification = buildNotification(title)
            startForeground(NOTIFICATION_ID, notification)
            return START_STICKY
        }

        override fun onBind(intent: Intent?): IBinder? = null

        override fun onDestroy() {
            super.onDestroy()
            playerManager?.stopPlayback()
        }
    }

    fun Context.startForegroundServiceCompat(serviceClass: Class<*>, title: String) {
        val intent = Intent(this, serviceClass).apply {
            putExtra("title", title)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
