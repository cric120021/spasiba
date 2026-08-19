package app.spasiba.nativeapp2.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.spasiba.nativeapp2.MainActivity
import app.spasiba.nativeapp2.R
import app.spasiba.nativeapp2.SpasibaApplication
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.data.network.HttpClientProvider
import app.spasiba.nativeapp2.player.NativePlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL

class SpasibaMediaService : MediaSessionService() {
    private var media3Session: MediaSession? = null
    private var compatSession: MediaSessionCompat? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var artworkJob: Job? = null

    private lateinit var playerManager: NativePlayerManager

    companion object {
        const val NOTIFICATION_ID = 2002
        const val CHANNEL_ID = "spasiba_native2_channel"

        const val ACTION_PLAY = "app.spasiba.nativeapp2.ACTION_PLAY"
        const val ACTION_PAUSE = "app.spasiba.nativeapp2.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "app.spasiba.nativeapp2.ACTION_PREVIOUS"
        const val ACTION_NEXT = "app.spasiba.nativeapp2.ACTION_NEXT"
        const val ACTION_STOP = "app.spasiba.nativeapp2.ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        playerManager = (application as SpasibaApplication).playerManager

        createNotificationChannel()

        media3Session = MediaSession.Builder(this, playerManager.exoPlayer).build()

        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        compatSession = MediaSessionCompat(this, "SpasibaNative2Media").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setSessionActivity(pendingIntent)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = playerManager.togglePlayPause()
                override fun onPause() = playerManager.togglePlayPause()
                override fun onSkipToNext() = playerManager.playNext()
                override fun onSkipToPrevious() = playerManager.playPrevious()
                override fun onSeekTo(pos: Long) = playerManager.seekTo(pos)
                override fun onStop() {
                    playerManager.exoPlayer.pause()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            })
            isActive = true
        }

        // Observe player state changes to update media session and notification
        observePlayerState(pendingIntent)

        val initialNotification = buildNotification(
            song = playerManager.currentSong.value,
            isPlaying = playerManager.isPlaying.value,
            artwork = null,
            pendingIntent = pendingIntent
        )

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        }.onFailure { e ->
            Log.w("SpasibaMediaService", "startForeground deferred: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action
        Log.d("SPASIBA_111", "[NOTIFICATION_ACTION] Received intent action='$action' from system notification/lockscreen controls")
        when (action) {
            ACTION_PLAY, ACTION_PAUSE -> playerManager.togglePlayPause()
            ACTION_PREVIOUS -> playerManager.playPrevious()
            ACTION_NEXT -> playerManager.playNext()
            ACTION_STOP -> {
                playerManager.exoPlayer.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return media3Session
    }

    private var cachedBitmapUrl: String? = null
    private var cachedBitmap: Bitmap? = null
    private var lastNotifiedSongId: String? = null
    private var lastNotifiedIsPlaying: Boolean? = null

    private fun observePlayerState(pendingIntent: PendingIntent) {
        serviceScope.launch {
            playerManager.activePlayer.collectLatest { newPlayer ->
                try {
                    media3Session?.setPlayer(newPlayer)
                    Log.d("SPASIBA_111", "[MEDIA_SESSION_UPDATE] MediaSession player updated to active player instance")
                } catch (e: Exception) {
                    Log.w("SpasibaMediaService", "Failed to update media3Session player: ${e.message}")
                }
                updateNotificationAndSession(playerManager.currentSong.value, playerManager.isPlaying.value, pendingIntent)
            }
        }

        serviceScope.launch {
            playerManager.currentSong.collectLatest { song ->
                updateNotificationAndSession(song, playerManager.isPlaying.value, pendingIntent)
            }
        }

        serviceScope.launch {
            playerManager.isPlaying.collectLatest { isPlaying ->
                updateNotificationAndSession(playerManager.currentSong.value, isPlaying, pendingIntent)
            }
        }
    }

    private fun updateNotificationAndSession(
        song: Song?,
        isPlaying: Boolean,
        pendingIntent: PendingIntent
    ) {
        if (song == null) return

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, playerManager.exoPlayer.currentPosition, 1.0f)
            .build()

        compatSession?.setPlaybackState(playbackState)

        // Only process artwork and metadata if song or play state actually changed
        if (song.id == lastNotifiedSongId && isPlaying == lastNotifiedIsPlaying) {
            return
        }
        val isSongChanged = (song.id != lastNotifiedSongId)
        lastNotifiedSongId = song.id
        lastNotifiedIsPlaying = isPlaying

        Log.d("SPASIBA_111", "[NOTIFICATION_UPDATE] Updating Media Notification for '${song.title}' by ${song.author} (isPlaying=$isPlaying, isSongChanged=$isSongChanged)")

        // Post immediate notification update (0ms latency!) using current/cached bitmap
        val currentBitmap = if (song.thumbnail == cachedBitmapUrl) cachedBitmap else null
        val immediateNotification = buildNotification(song, isPlaying, currentBitmap, pendingIntent)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, immediateNotification)

        if (isSongChanged) {
            artworkJob?.cancel()
            artworkJob = serviceScope.launch {
                val bitmap = if (song.thumbnail == cachedBitmapUrl && cachedBitmap != null) {
                    cachedBitmap
                } else {
                    val fetched = fetchBitmap(song.thumbnail)
                    if (fetched != null) {
                        cachedBitmapUrl = song.thumbnail
                        cachedBitmap = fetched
                    }
                    fetched
                }

                val metadataBuilder = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.author)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.durationSeconds * 1000L)

                if (bitmap != null) {
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                    metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
                    Log.d("SPASIBA_111", "[NOTIFICATION_ARTWORK] Loaded artwork bitmap for '${song.title}'")
                }

                compatSession?.setMetadata(metadataBuilder.build())

                val notification = buildNotification(song, playerManager.isPlaying.value, bitmap, pendingIntent)
                notificationManager.notify(NOTIFICATION_ID, notification)
                Log.d("SPASIBA_111", "[NOTIFICATION_POST] Notification updated with artwork for '${song.title}'")
            }
        }
    }

    private val httpClient get() = HttpClientProvider.client

    private suspend fun fetchBitmap(urlStr: String): Bitmap? = withContext(Dispatchers.IO) {
        if (urlStr.isBlank()) return@withContext null
        runCatching {
            val request = Request.Builder().url(urlStr).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bytes = response.body?.bytes() ?: return@runCatching null
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                options.inSampleSize = calculateInSampleSize(options, 512, 512)
                options.inJustDecodeBounds = false
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        }.getOrNull()
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun buildNotification(
        song: Song?,
        isPlaying: Boolean,
        artwork: Bitmap?,
        pendingIntent: PendingIntent
    ): Notification {
        val title = song?.title ?: "Spasiba Music"
        val artist = song?.author ?: "Tap to play music"

        val prevIntent = PendingIntent.getService(
            this, 1, Intent(this, SpasibaMediaService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseActionIntent = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val playPauseIntent = PendingIntent.getService(
            this, 2, Intent(this, SpasibaMediaService::class.java).apply { action = playPauseActionIntent },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = PendingIntent.getService(
            this, 3, Intent(this, SpasibaMediaService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(artwork)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(compatSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Spasiba Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls and metadata for current playing song"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        artworkJob?.cancel()
        runCatching { playerManager.exoPlayer.pause() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
        notificationManager?.cancelAll()
        compatSession?.run {
            isActive = false
            release()
        }
        media3Session?.release()
        compatSession = null
        media3Session = null
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        artworkJob?.cancel()
        runCatching { playerManager.exoPlayer.pause() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
        notificationManager?.cancelAll()
        media3Session?.run {
            release()
            media3Session = null
        }
        compatSession?.run {
            isActive = false
            release()
            compatSession = null
        }
        super.onDestroy()
    }
}
