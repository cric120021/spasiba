import re

with open("android-native/app/src/main/java/app/spasiba/nativeapp/SpasibaKeepAliveService.kt", "r") as f:
    content = f.read()

# Add imports for Handler, Looper, JSONObject
old_imports = """import androidx.media.session.MediaButtonReceiver
import java.net.URL
import java.util.concurrent.Executors"""

new_imports = """import androidx.media.session.MediaButtonReceiver
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors"""

content = content.replace(old_imports, new_imports)

# Add handler and polling fields after artworkExecutor
old_fields = """    private val artworkExecutor = Executors.newSingleThreadExecutor()
    private var lastTitle: String? = null"""

new_fields = """    private val artworkExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null
    private var isPolling = false
    private var lastTitle: String? = null"""

content = content.replace(old_fields, new_fields)

# Add player helper property after fields
old_after_fields = """    private var lastPositionMs: Long = 0L
    private var lastDurationMs: Long = 0L"""

new_after_fields = """    private var lastPositionMs: Long = 0L
    private var lastDurationMs: Long = 0L

    private val player: SpasibaNativeAudioPlayer?
        get() = (application as? SpasibaApplication)?.nativeAudioPlayer"""

content = content.replace(old_after_fields, new_after_fields)

# Fix onCreate to start polling
old_oncreate = """    override fun onCreate() {
        super.onCreate()
        Log.d("SpasibaService", "Service onCreate")
        ensureNotificationChannel()
        setupMediaSession()
        acquireLocks()
        startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING)
    }"""

new_oncreate = """    override fun onCreate() {
        super.onCreate()
        Log.d("SpasibaService", "Service onCreate")
        ensureNotificationChannel()
        setupMediaSession()
        acquireLocks()
        startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING)
        startPolling()
    }"""

content = content.replace(old_oncreate, new_oncreate)

# Fix MediaSession callbacks to directly control player
old_mediasession = """                override fun onPlay() {
                    Log.d("SpasibaService", "onPlay")
                    sendMediaControl("play")
                }

                override fun onPause() {
                    Log.d("SpasibaService", "onPause")
                    sendMediaControl("pause")
                }

                override fun onStop() {
                    Log.d("SpasibaService", "onStop")
                    sendMediaControl("stop")
                }

                override fun onSkipToNext() {
                    Log.d("SpasibaService", "onSkipToNext")
                    sendMediaControl("next")
                }

                override fun onSkipToPrevious() {
                    Log.d("SpasibaService", "onSkipToPrevious")
                    sendMediaControl("previous")
                }

                override fun onSeekTo(pos: Long) {
                    Log.d("SpasibaService", "onSeekTo $pos")
                    lastPositionMs = pos.coerceAtLeast(0L)
                    sendMediaControl("seek", lastPositionMs)
                    updateMediaSession(lastTitle ?: "Spasiba", lastArtist, lastState, lastPositionMs, lastDurationMs)
                    startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, lastState, lastPositionMs, lastDurationMs)
                }"""

new_mediasession = """                override fun onPlay() {
                    Log.d("SpasibaService", "onPlay")
                    player?.resume()
                    sendMediaControl("play")
                }

                override fun onPause() {
                    Log.d("SpasibaService", "onPause")
                    player?.pause()
                    sendMediaControl("pause")
                }

                override fun onStop() {
                    Log.d("SpasibaService", "onStop")
                    player?.stop()
                    sendMediaControl("stop")
                    stopPolling()
                    stopSelf()
                }

                override fun onSkipToNext() {
                    Log.d("SpasibaService", "onSkipToNext")
                    player?.next()
                    sendMediaControl("next")
                }

                override fun onSkipToPrevious() {
                    Log.d("SpasibaService", "onSkipToPrevious")
                    player?.previous()
                    sendMediaControl("previous")
                }

                override fun onSeekTo(pos: Long) {
                    Log.d("SpasibaService", "onSeekTo $pos")
                    player?.seek(pos.coerceAtLeast(0L) / 1000.0)
                    lastPositionMs = pos.coerceAtLeast(0L)
                    sendMediaControl("seek", lastPositionMs)
                    updateMediaSession(lastTitle ?: "Spasiba", lastArtist, lastState, lastPositionMs, lastDurationMs)
                    startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, lastState, lastPositionMs, lastDurationMs)
                }"""

content = content.replace(old_mediasession, new_mediasession)

# Fix notification action handlers to directly control player
old_notification_actions = """            ACTION_NOTIFICATION_PLAY -> {
                sendMediaControl("play")
                updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_PLAYING, lastPositionMs, lastDurationMs)
                startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_PLAYING, lastPositionMs, lastDurationMs)
                return START_STICKY
            }
            ACTION_NOTIFICATION_PAUSE -> {
                sendMediaControl("pause")
                updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_PAUSED, lastPositionMs, lastDurationMs)
                startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_PAUSED, lastPositionMs, lastDurationMs)
                return START_STICKY
            }
            ACTION_NOTIFICATION_STOP -> {
                sendMediaControl("stop")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_NOTIFICATION_NEXT -> {
                sendMediaControl("next")
                updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                return START_STICKY
            }
            ACTION_NOTIFICATION_PREVIOUS -> {
                sendMediaControl("previous")
                updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                return START_STICKY
            }"""

new_notification_actions = """            ACTION_NOTIFICATION_PLAY -> {
                player?.resume()
                sendMediaControl("play")
                updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_PLAYING, lastPositionMs, lastDurationMs)
                startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_PLAYING, lastPositionMs, lastDurationMs)
                return START_STICKY
            }
            ACTION_NOTIFICATION_PAUSE -> {
                player?.pause()
                sendMediaControl("pause")
                updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_PAUSED, lastPositionMs, lastDurationMs)
                startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_PAUSED, lastPositionMs, lastDurationMs)
                return START_STICKY
            }
            ACTION_NOTIFICATION_STOP -> {
                player?.stop()
                sendMediaControl("stop")
                stopPolling()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_NOTIFICATION_NEXT -> {
                player?.next()
                sendMediaControl("next")
                updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                return START_STICKY
            }
            ACTION_NOTIFICATION_PREVIOUS -> {
                player?.previous()
                sendMediaControl("previous")
                updateMediaSession(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                startForegroundNotification(lastTitle ?: "Spasiba", lastArtist, STATE_LOADING, lastPositionMs, lastDurationMs)
                return START_STICKY
            }"""

content = content.replace(old_notification_actions, new_notification_actions)

# Fix onDestroy to stop polling
old_ondestroy = """    override fun onDestroy() {
        Log.d("SpasibaService", "Service onDestroy")
        releaseLocks()
        artworkExecutor.shutdownNow()
        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }"""

new_ondestroy = """    override fun onDestroy() {
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
    }"""

content = content.replace(old_ondestroy, new_ondestroy)

# Add polling methods before companion object
old_companion = "    companion object {"

new_companion = """    private fun startPolling() {
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
        val snapshot = runCatching { JSONObject(p.snapshotJson()) }.getOrNull() ?: return
        val state = snapshot.optString("state", STATE_PAUSED)
        val title = snapshot.optString("title", "").takeIf { it.isNotBlank() } ?: lastTitle ?: "Spasiba"
        val artist = snapshot.optString("artist", "").takeIf { it.isNotBlank() } ?: lastArtist
        val videoId = snapshot.optString("videoId", "").takeIf { it.isNotBlank() } ?: lastVideoId
        val artworkUrl = snapshot.optString("artworkUrl", "").takeIf { it.isNotBlank() }
            ?: videoId?.let(::thumbnailUrl) ?: lastArtworkUrl
        val positionMs = (snapshot.optDouble("currentTime", 0.0).coerceAtLeast(0.0) * 1000.0).toLong()
        val durationMs = (snapshot.optDouble("duration", 0.0).coerceAtLeast(0.0) * 1000.0).toLong()

        if (
            title == lastTitle &&
            artist == lastArtist &&
            state == lastState &&
            videoId == lastVideoId &&
            artworkUrl == lastArtworkUrl &&
            positionMs == lastPositionMs &&
            durationMs == lastDurationMs
        ) {
            return
        }

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
    }

    companion object {"""

content = content.replace(old_companion, new_companion)

with open("android-native/app/src/main/java/app/spasiba/nativeapp/SpasibaKeepAliveService.kt", "w") as f:
    f.write(content)

print("SpasibaKeepAliveService.kt updated successfully")
