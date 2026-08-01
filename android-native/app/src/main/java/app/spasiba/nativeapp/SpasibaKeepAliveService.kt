package app.spasiba.nativeapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.session.MediaButtonReceiver
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors

class SpasibaKeepAliveService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var mediaSession: MediaSessionCompat? = null
    private val artworkExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null
    private var isPolling = false
    private var lastTitle: String? = null
    private var lastArtist: String? = null
    private var lastState: String = STATE_PLAYING
    private var lastVideoId: String? = null
    private var lastArtworkUrl: String? = null
    private var lastLargeIcon: Bitmap? = null
    private var lastPositionMs: Long = 0L
    private var lastDurationMs: Long = 0L

    private val player: SpasibaNativeAudioPlayer?
        get() = (application as? SpasibaApplication)?.nativeAudioPlayer

    override fun onCreate() {
        super.onCreate()
        Log.d("SpasibaService", "Service onCreate")
        ensureNotificationChannel()
        setupMediaSession()
        acquireLocks()
        startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING)
        startPolling()
    }

    private fun setupMediaSession() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, this, MediaButtonReceiver::class.java)
        val mediaButtonPendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE)

        mediaSession = MediaSessionCompat(this, "SpasibaKeepAlive").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setSessionActivity(pendingIntent)
            setMediaButtonReceiver(mediaButtonPendingIntent)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    Log.d("SpasibaService", "Media button event: ${keyEvent?.keyCode}")

                    if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY -> player?.resume()
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> player?.pause()
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_HEADSETHOOK,
                            KeyEvent.KEYCODE_SPACE -> {
                                if (lastState == STATE_PLAYING) {
                                    player?.pause()
                                } else {
                                    player?.resume()
                                }
                            }
                            KeyEvent.KEYCODE_MEDIA_NEXT -> player?.next()
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> player?.previous()
                            KeyEvent.KEYCODE_MEDIA_STOP -> player?.stop()
                        }
                    }
                    return true
                }

                override fun onPlay() {
                    Log.d("SpasibaService", "onPlay")
                    player?.resume()
                    sendControlBroadcast("play")
                    updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_PLAYING, lastPositionMs, lastDurationMs)
                    startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_PLAYING, lastPositionMs, lastDurationMs)
                }

                override fun onPause() {
                    Log.d("SpasibaService", "MediaSession onPause")
                    player?.pause()
                    sendControlBroadcast("pause")
                    updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_PAUSED, lastPositionMs, lastDurationMs)
                    startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_PAUSED, lastPositionMs, lastDurationMs)
                }

                override fun onStop() {
                    Log.d("SPASIBA_BG", "MediaSession onStop called")
                    player?.stop()
                    sendControlBroadcast("stop")
                }

                override fun onSkipToNext() {
                    Log.d("SpasibaService", "onSkipToNext")
                    player?.next()
                    sendControlBroadcast("next")
                    updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                    startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                }

                override fun onSkipToPrevious() {
                    Log.d("SpasibaService", "onSkipToPrevious")
                    player?.previous()
                    sendControlBroadcast("previous")
                    updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                    startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                }

                override fun onSeekTo(pos: Long) {
                    Log.d("SpasibaService", "onSeekTo $pos")
                    val seekPos = pos.coerceAtLeast(0L)
                    player?.seekTo(seekPos)
                    sendControlBroadcast("seek", seekPos)
                    lastPositionMs = seekPos
                    updateMediaSession(lastTitle ?: "Spasiba", lastArtist, lastState, lastPositionMs, lastDurationMs)
                    startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, lastState, lastPositionMs, lastDurationMs)
                }
            })
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                    .setActions(MEDIA_SESSION_ACTIONS)
                    .build()
            )
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SPASIBA_BG", "onStartCommand")
        acquireLocks()

        when (intent?.action) {
            ACTION_NOTIFICATION_PLAY -> {
                player?.resume()
                sendControlBroadcast("play")
                updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_PLAYING, lastPositionMs, lastDurationMs)
                startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_PLAYING, lastPositionMs, lastDurationMs)
                return START_STICKY
            }
            ACTION_NOTIFICATION_PAUSE -> {
                player?.pause()
                sendControlBroadcast("pause")
                updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_PAUSED, lastPositionMs, lastDurationMs)
                startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_PAUSED, lastPositionMs, lastDurationMs)
                return START_STICKY
            }
            ACTION_NOTIFICATION_STOP -> {
                player?.stop()
                sendControlBroadcast("stop")
                stopPolling()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_NOTIFICATION_NEXT -> {
                player?.next()
                sendControlBroadcast("next")
                updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                return START_STICKY
            }
            ACTION_NOTIFICATION_PREVIOUS -> {
                player?.previous()
                sendControlBroadcast("previous")
                updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                return START_STICKY
            }
        }
        
        val rawVideoId = intent?.getStringExtra(EXTRA_VIDEO_ID)?.takeIf { it.isNotBlank() }
        val isNewVideoId = rawVideoId != null && rawVideoId != lastVideoId
        val videoId = rawVideoId ?: lastVideoId

        val rawTitle = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
        val title = rawTitle ?: if (isNewVideoId) "Spasiba" else (lastTitle ?: "Spasiba")

        val rawArtist = intent?.getStringExtra(EXTRA_ARTIST)?.takeIf { it.isNotBlank() }
        val artist = rawArtist ?: if (isNewVideoId) null else lastArtist

        val state = intent?.getStringExtra(EXTRA_STATE) ?: lastState
        val artworkUrl = intent?.getStringExtra(EXTRA_ARTWORK_URL)?.takeIf { it.isNotBlank() }
            ?: videoId?.let(::thumbnailUrl)

        val rawPositionMs = if (intent?.hasExtra(EXTRA_POSITION_MS) == true) {
            intent.getLongExtra(EXTRA_POSITION_MS, lastPositionMs).coerceAtLeast(0L)
        } else {
            lastPositionMs
        }
        val positionMs = if (rawPositionMs == 0L && lastPositionMs > 0L && state == lastState) {
            lastPositionMs
        } else {
            rawPositionMs
        }

        val rawDurationMs = if (intent?.hasExtra(EXTRA_DURATION_MS) == true) {
            intent.getLongExtra(EXTRA_DURATION_MS, lastDurationMs).coerceAtLeast(0L)
        } else {
            lastDurationMs
        }
        val durationMs = if (rawDurationMs <= 0L && lastDurationMs > 0L) {
            lastDurationMs
        } else {
            rawDurationMs
        }
        
        if (
            intent != null &&
            title == lastTitle &&
            artist == lastArtist &&
            state == lastState &&
            videoId == lastVideoId &&
            artworkUrl == lastArtworkUrl &&
            positionMs == lastPositionMs &&
            durationMs == lastDurationMs &&
            (wakeLock?.isHeld == true)
        ) {
            return START_STICKY
        }

        Log.d("SpasibaService", "Service onStartCommand: $title - $artist ($state)")
        val artworkChanged = artworkUrl != lastArtworkUrl
        lastTitle = title
        lastArtist = artist
        lastState = state
        lastVideoId = videoId
        lastArtworkUrl = artworkUrl
        lastPositionMs = positionMs
        lastDurationMs = durationMs
        if (artworkChanged) {
            lastLargeIcon = null
        }
        
        updateMediaSession(title, artist, state, positionMs, durationMs)
        startForegroundNotification(title, artist, state, positionMs, durationMs)
        loadArtworkIfNeeded(title, artist, state, artworkUrl)
        
        return START_STICKY
    }

    private fun updateMediaSession(
        title: String,
        artist: String?,
        state: String,
        positionMs: Long = lastPositionMs,
        durationMs: Long = lastDurationMs
    ) {
        val playerDuration = (application as? SpasibaApplication)?.nativeAudioPlayer?.durationMs ?: 0L
        val effectiveDurationMs = when {
            durationMs > 0L -> durationMs
            lastDurationMs > 0L -> lastDurationMs
            playerDuration > 0L -> playerDuration
            else -> 180_000L
        }

        val playerPosition = (application as? SpasibaApplication)?.nativeAudioPlayer?.positionMs ?: 0L
        val effectivePositionMs = when {
            positionMs > 0L -> positionMs
            lastPositionMs > 0L -> lastPositionMs
            playerPosition > 0L -> playerPosition
            else -> 0L
        }

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, effectiveDurationMs)

        val metadata = metadataBuilder
            .apply {
                if (!lastVideoId.isNullOrBlank()) {
                    putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, lastVideoId)
                }
                if (!lastArtworkUrl.isNullOrBlank()) {
                    putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, lastArtworkUrl)
                    putString(MediaMetadataCompat.METADATA_KEY_ART_URI, lastArtworkUrl)
                }
                lastLargeIcon?.let { artwork ->
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artwork)
                }
            }
            .apply {
                if (!artist.isNullOrBlank()) {
                    putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                }
            }
            .build()

        val playbackState = when (state) {
            STATE_PAUSED -> PlaybackStateCompat.STATE_PAUSED
            STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
            else -> PlaybackStateCompat.STATE_PLAYING
        }
        val playbackSpeed = if (playbackState == PlaybackStateCompat.STATE_PLAYING) 1.0f else 0.0f
        
        mediaSession?.apply {
            isActive = true
            setMetadata(metadata)
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(playbackState, effectivePositionMs.coerceAtLeast(0L), playbackSpeed, android.os.SystemClock.elapsedRealtime())
                    .setActions(MEDIA_SESSION_ACTIONS)
                    .build()
            )
        }
    }

    override fun onDestroy() {
        Log.d("SpasibaService", "Service onDestroy")
        stopPolling()
        releaseLocks()
        artworkExecutor.shutdownNow()
        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification(
        title: String,
        artist: String?,
        state: String,
        positionMs: Long = lastPositionMs,
        durationMs: Long = lastDurationMs
    ) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isPaused = state == STATE_PAUSED || state == STATE_ENDED
        val previousIntent = notificationActionIntent(ACTION_NOTIFICATION_PREVIOUS, REQUEST_PREVIOUS)
        val playPauseIntent = notificationActionIntent(
            if (isPaused) ACTION_NOTIFICATION_PLAY else ACTION_NOTIFICATION_PAUSE,
            if (isPaused) REQUEST_PLAY else REQUEST_PAUSE
        )
        val nextIntent = notificationActionIntent(ACTION_NOTIFICATION_NEXT, REQUEST_NEXT)
        val stopIntent = notificationActionIntent(ACTION_NOTIFICATION_STOP, REQUEST_STOP)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist ?: "Spasiba is playing")
            .setContentIntent(pendingIntent)
            .setLargeIcon(lastLargeIcon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                previousIntent
            )
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Play" else "Pause",
                playPauseIntent
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                nextIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopIntent
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder
                .setColor(0xFF7C2D24.toInt())
                .setColorized(true)
        }

        val notification = notificationBuilder.build()

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
        } catch (e: Exception) {
            Log.e("SpasibaService", "Failed to start foreground", e)
        }
    }

    private fun sendControlBroadcast(type: String, seekPositionMs: Long = 0L) {
        val intent = Intent(ACTION_MEDIA_CONTROL).apply {
            setPackage(packageName)
            putExtra(EXTRA_CONTROL_TYPE, type)
            if (seekPositionMs > 0L) {
                putExtra(EXTRA_SEEK_POSITION_MS, seekPositionMs)
            }
        }
        sendBroadcast(intent)
    }

    private fun notificationActionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, SpasibaKeepAliveService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun loadArtworkIfNeeded(title: String, artist: String?, state: String, artworkUrl: String?) {
        val requestedArtworkUrl = artworkUrl?.takeIf { it.isNotBlank() } ?: return
        if (lastLargeIcon != null) {
            return
        }

        artworkExecutor.execute {
            val bitmap = runCatching {
                val connection = URL(requestedArtworkUrl).openConnection().apply {
                    connectTimeout = 6_000
                    readTimeout = 6_000
                }
                connection.getInputStream().use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull() ?: return@execute

            if (requestedArtworkUrl != lastArtworkUrl) {
                return@execute
            }

            lastLargeIcon = bitmap
            updateMediaSession(title, artist, state, lastPositionMs, lastDurationMs)
            startForegroundNotification(title, artist, state, lastPositionMs, lastDurationMs)
        }
    }

    private fun acquireLocks() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Spasiba:KeepAlive").apply {
                setReferenceCounted(false)
            }
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire()
        }

        if (wifiLock == null) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lockType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL
            }
            wifiLock = wifiManager.createWifiLock(lockType, "Spasiba:WifiKeepAlive").apply {
                setReferenceCounted(false)
            }
        }
        if (wifiLock?.isHeld == false) {
            wifiLock?.acquire()
        }
    }

    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null

        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
        wifiLock = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Spasiba playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Spasiba playback alive in the background."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startPolling() {
        if (isPolling) return
        isPolling = true
        val runnable = object : Runnable {
            override fun run() {
                if (!isPolling) return
                updateFromPlayer()
                mainHandler.postDelayed(this, 1000)
            }
        }
        pollRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun stopPolling() {
        isPolling = false
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        pollRunnable = null
    }

    private fun updateFromPlayer() {
        val p = player ?: return
        val activeVideoId = p.currentVideoId()
        if (activeVideoId.isNullOrBlank()) {
            return
        }

        val snapshot = runCatching { JSONObject(p.snapshotJson()) }.getOrNull() ?: return
        val rawVideoId = snapshot.optString("videoId", "").takeIf { it.isNotBlank() } ?: return

        val state = snapshot.optString("state", STATE_PAUSED)
        val rawTitle = snapshot.optString("title", "").takeIf { it.isNotBlank() } ?: return
        val title = rawTitle
        val artist = snapshot.optString("artist", "").takeIf { it.isNotBlank() } ?: lastArtist
        val artworkUrl = snapshot.optString("artworkUrl", "").takeIf { it.isNotBlank() }
            ?: thumbnailUrl(rawVideoId)

        val rawPosition = snapshot.optDouble("currentTime", -1.0)
        val rawDuration = snapshot.optDouble("duration", -1.0)

        val positionMs = if (rawPosition >= 0.0) (rawPosition * 1000.0).toLong() else lastPositionMs
        val durationMs = if (rawDuration > 0.0) (rawDuration * 1000.0).toLong() else lastDurationMs

        val metadataOrStateChanged = (
            title != lastTitle ||
            artist != lastArtist ||
            state != lastState ||
            rawVideoId != lastVideoId ||
            artworkUrl != lastArtworkUrl
        )

        val timingChanged = Math.abs(positionMs - lastPositionMs) >= 800L || durationMs != lastDurationMs

        if (!metadataOrStateChanged && !timingChanged) {
            return
        }

        val artworkChanged = artworkUrl != lastArtworkUrl
        lastTitle = title
        lastArtist = artist
        lastState = state
        lastVideoId = rawVideoId
        lastArtworkUrl = artworkUrl
        lastPositionMs = positionMs
        lastDurationMs = durationMs
        if (artworkChanged) {
            lastLargeIcon = null
        }

        updateMediaSession(title, artist, state, positionMs, durationMs)
        if (metadataOrStateChanged) {
            startForegroundNotification(title, artist, state, positionMs, durationMs)
        }
        loadArtworkIfNeeded(title, artist, state, artworkUrl)
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_STATE = "state"
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_ARTWORK_URL = "artwork_url"
        const val EXTRA_POSITION_MS = "position_ms"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val ACTION_MEDIA_CONTROL = "app.spasiba.nativeapp.MEDIA_CONTROL"
        const val EXTRA_CONTROL_TYPE = "control_type"
        const val EXTRA_KEY_EVENT = "key_event"
        const val EXTRA_SEEK_POSITION_MS = "seek_position_ms"
        private const val ACTION_NOTIFICATION_PLAY = "app.spasiba.nativeapp.NOTIFICATION_PLAY"
        private const val ACTION_NOTIFICATION_PAUSE = "app.spasiba.nativeapp.NOTIFICATION_PAUSE"
        private const val ACTION_NOTIFICATION_STOP = "app.spasiba.nativeapp.NOTIFICATION_STOP"
        private const val ACTION_NOTIFICATION_NEXT = "app.spasiba.nativeapp.NOTIFICATION_NEXT"
        private const val ACTION_NOTIFICATION_PREVIOUS = "app.spasiba.nativeapp.NOTIFICATION_PREVIOUS"
        private const val STATE_LOADING = "loading"
        private const val STATE_PLAYING = "playing"
        private const val STATE_PAUSED = "paused"
        private const val STATE_ENDED = "ended"
        private const val REQUEST_PLAY = 1802
        private const val REQUEST_PAUSE = 1803
        private const val REQUEST_STOP = 1804
        private const val REQUEST_NEXT = 1805
        private const val REQUEST_PREVIOUS = 1806
        private const val CHANNEL_ID = "spasiba_media_channel_v3"
        private const val NOTIFICATION_ID = 1801
        private const val MEDIA_SESSION_ACTIONS =
            PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO

        private fun thumbnailUrl(videoId: String): String =
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }
}
