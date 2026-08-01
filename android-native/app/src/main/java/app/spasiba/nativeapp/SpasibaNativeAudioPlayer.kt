package app.spasiba.nativeapp

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

data class NativeAudioTrack(
    val videoId: String,
    val title: String?,
    val artist: String?,
    val artworkUrl: String?
)

class SpasibaNativeAudioPlayer(
    private val context: Context,
    private val cache: SpasibaNativeAudioCache,
    private val onStateChanged: (state: String, title: String?, artist: String?) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger()
    private var exoPlayer: ExoPlayer? = null
    var currentVideoId: String? = null
        private set
    var currentTitle: String? = null
        private set
    var currentArtist: String? = null
        private set
    var currentArtworkUrl: String? = null
        private set
    private var currentState = "paused"
    val isPlaying: Boolean get() = currentState == "playing"
    @Volatile private var lastKnownPositionMs = 0L
    val positionMs: Long
        get() {
            val p = exoPlayer?.currentPosition?.coerceAtLeast(0L)
            return p ?: lastKnownPositionMs
        }
    @Volatile private var lastKnownDurationMs = 0L
    val durationMs: Long
        get() {
            val dur = exoPlayer?.duration
            return if (dur != null && dur != C.TIME_UNSET && dur > 0L) dur else lastKnownDurationMs
        }
    private var currentVolume = 1f
    private var playerPrepared = false
    private var queue: List<NativeAudioTrack> = emptyList()
    private var queueIndex = -1
    private var released = false
    private var progressTickerRunning = false

    fun seekTo(positionMs: Long) {
        mainHandler.post { seekOnMain(positionMs) }
    }

    private var prefetchedVideoId: String? = null

    private fun maybePrefetchNextTrack() {
        val nextIndex = queueIndex + 1
        if (nextIndex in queue.indices) {
            val nextTrack = queue[nextIndex]
            if (prefetchedVideoId != nextTrack.videoId) {
                prefetchedVideoId = nextTrack.videoId
                Log.d(LOG_TAG, "PREFETCHING NEXT TRACK IN QUEUE videoId=${nextTrack.videoId}")
                cache.prefetchStream(nextTrack.videoId)
            }
        }
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            if (released) {
                progressTickerRunning = false
                return
            }

            refreshKnownTiming()
            if (currentState == "playing") {
                updateState("playing")
            }

            if (lastKnownDurationMs > 0L && (lastKnownPositionMs.toDouble() / lastKnownDurationMs.toDouble()) >= 0.70) {
                maybePrefetchNextTrack()
            }

            if (currentState == "playing" || currentState == "loading") {
                mainHandler.postDelayed(this, PROGRESS_UPDATE_MS)
            } else {
                progressTickerRunning = false
            }
        }
    }

    private val playerListener = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = exoPlayer ?: return
            refreshKnownTiming()

            val stateName = when (playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> playbackState.toString()
            }

            Log.d(
                LOG_TAG,
                "ExoPlayer state=$stateName playWhenReady=${player.playWhenReady} isPlaying=${player.isPlaying} currentState=$currentState position=${player.currentPosition} buffered=${player.bufferedPosition}"
            )

            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    playerPrepared = false
                    if (player.playWhenReady || currentState == "loading") {
                        updateState("loading")
                        startProgressTicker()
                    }
                }

                Player.STATE_READY -> {
                    playerPrepared = true
                    refreshKnownTiming()
                    updateState(if (player.playWhenReady) "playing" else "paused")
                    if (player.playWhenReady) {
                        startProgressTicker()
                    }
                }

                Player.STATE_ENDED -> {
                    playerPrepared = false
                    refreshKnownTiming()
                    lastKnownPositionMs = lastKnownDurationMs
                    if (playQueueOffset(1, restartCurrentWhenMissing = false)) {
                        return
                    }
                    updateState("ended")
                }

                Player.STATE_IDLE -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val player = exoPlayer ?: return
            refreshKnownTiming()

            Log.d(
                LOG_TAG,
                "ExoPlayer isPlaying=$isPlaying state=${player.playbackState} playWhenReady=${player.playWhenReady}"
            )

            if (isPlaying) {
                playerPrepared = true
                updateState("playing")
                startProgressTicker()
            } else if (!player.playWhenReady) {
                updateState("paused")
            } else if (player.playbackState == Player.STATE_BUFFERING) {
                updateState("loading")
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(LOG_TAG, "ExoPlayer playback failed", error)
            releaseCurrentPlayer()
            updateState("paused")
        }
    }
    fun setQueue(tracks: List<NativeAudioTrack>, activeVideoId: String?) {
        val cleanTracks = tracks
            .mapNotNull { track ->
                val videoId = SpasibaNativeAudioCache.normalizeVideoId(track.videoId) ?: return@mapNotNull null
                NativeAudioTrack(
                    videoId = videoId,
                    title = track.title?.takeIf { it.isNotBlank() },
                    artist = track.artist?.takeIf { it.isNotBlank() },
                    artworkUrl = track.artworkUrl?.takeIf { it.isNotBlank() } ?: thumbnailUrl(videoId)
                )
            }
            .distinctBy { it.videoId }
            .take(MAX_QUEUE_TRACKS)

        if (cleanTracks.isEmpty()) {
            return
        }

        queue = cleanTracks
        queueIndex = findQueueIndex(activeVideoId ?: currentVideoId)
        maybePrefetchNextTrack()
    }

    fun play(
        rawVideoId: String,
        title: String?,
        artist: String?,
        artworkUrl: String? = null,
        startSeconds: Double = 0.0,
        directStreamUrl: String? = null
    ) {
        val videoId = SpasibaNativeAudioCache.normalizeVideoId(rawVideoId) ?: return
        val sameTrack = videoId == currentVideoId
        upsertCurrentTrack(videoId, title, artist, artworkUrl)
        currentVideoId = videoId
        val matchedTrack = findQueueTrack(videoId)
        currentTitle = title?.takeIf { it.isNotBlank() } ?: matchedTrack?.title ?: if (!sameTrack) null else currentTitle
        currentArtist = artist?.takeIf { it.isNotBlank() } ?: matchedTrack?.artist ?: if (!sameTrack) null else currentArtist
        currentArtworkUrl = artworkUrl?.takeIf { it.isNotBlank() } ?: matchedTrack?.artworkUrl ?: thumbnailUrl(videoId)

        if (sameTrack) {
            if (currentState == "loading") {
                return
            }

            val player = exoPlayer
            if (player != null) {
                mainHandler.post {
                    if (exoPlayer !== player) {
                        return@post
                    }
                    if (startSeconds > 0.0) {
                        seekOnMain((startSeconds * 1000.0).toLong())
                    }
                    player.playWhenReady = true
                    refreshKnownTiming()
                    updateState(if (player.playbackState == Player.STATE_READY) "playing" else "loading")
                    startProgressTicker()
                }
                return
            }
        }

        val requestGeneration = generation.incrementAndGet()
        mainHandler.post {
            releaseCurrentPlayer()
            lastKnownPositionMs = (startSeconds.coerceAtLeast(0.0) * 1000.0).toLong()
            lastKnownDurationMs = 0L
            updateState("playing")
        }

        executor.execute {
            try {
                val stream = directStreamUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    StreamingAudio(videoId = videoId, url = url, mimeType = null, headers = emptyMap(), durationSeconds = 0)
                } ?: cache.resolveStreamingAudio(videoId)
                mainHandler.post {
                    if (released || generation.get() != requestGeneration || currentVideoId != videoId) {
                        return@post
                    }
                    prepareAndStart(stream, startSeconds, requestGeneration)
                }
            } catch (error: Throwable) {
                Log.e(LOG_TAG, "native streaming playback failed for $videoId", error)
                mainHandler.post {
                    if (generation.get() == requestGeneration) {
                        updateState("paused")
                    }
                }
            }
        }
    }

    fun next(): Boolean = playQueueOffset(1, restartCurrentWhenMissing = false)

    fun previous(): Boolean {
        if (playerPrepared && lastKnownPositionMs > RESTART_PREVIOUS_AFTER_MS) {
            seek(0.0)
            return true
        }

        return playQueueOffset(-1, restartCurrentWhenMissing = true)
    }

    fun pause(): Boolean {
        val player = exoPlayer

        if (player == null) {
            if (currentState == "loading" || currentState == "playing") {
                generation.incrementAndGet()
                currentState = "paused"
                mainHandler.post { updateState("paused") }
                return true
            }

            return false
        }

        currentState = "paused"
        mainHandler.post {
            if (exoPlayer !== player) {
                return@post
            }
            player.playWhenReady = false
            refreshKnownTiming()
            updateState("paused")
        }
        return true
    }

    fun resume(): Boolean {
        val player = exoPlayer ?: run {
            val videoId = currentVideoId ?: return currentState == "loading"
            if (currentState == "paused" || currentState == "ended") {
                play(videoId, currentTitle, currentArtist, currentArtworkUrl, lastKnownPositionMs / 1000.0)
                return true
            }
            return currentState == "loading"
        }

        currentState = "playing"
        mainHandler.post {
            if (exoPlayer !== player) {
                return@post
            }
            player.playWhenReady = true
            refreshKnownTiming()
            updateState(if (player.playbackState == Player.STATE_READY) "playing" else "loading")
            startProgressTicker()
        }
        return true
    }

    fun toggle(): Boolean {
        val player = exoPlayer ?: return currentState == "loading"
        return if (player.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun stop(): Boolean {
        val hadPlaybackState = exoPlayer != null || currentVideoId != null || currentState == "loading" || currentState == "playing"
        if (!hadPlaybackState) {
            return false
        }

        generation.incrementAndGet()
        mainHandler.post {
            releaseCurrentPlayer()
            currentVideoId = null
            currentTitle = null
            currentArtist = null
            currentArtworkUrl = null
            currentState = "ended"
            lastKnownPositionMs = 0L
            lastKnownDurationMs = 0L
            queue = emptyList()
            queueIndex = -1
            progressTickerRunning = false
            mainHandler.removeCallbacks(progressTicker)
            onStateChanged("ended", null, null)
        }
        return true
    }

    fun seek(seconds: Double) {
        val safeMs = (seconds.coerceAtLeast(0.0) * 1000.0).toLong()
        mainHandler.post {
            seekOnMain(safeMs)
            updateState(currentState)
        }
    }

    fun setVolume(percent: Int) {
        currentVolume = (percent.coerceIn(0, 100) / 100f)
        mainHandler.post {
            exoPlayer?.volume = currentVolume
        }
    }

    fun snapshotJson(): String {
        val state = currentState
        return """
            {"state":"${SpasibaNativeAudioCache.jsonEscape(state)}","videoId":"${SpasibaNativeAudioCache.jsonEscape(currentVideoId.orEmpty())}","title":"${SpasibaNativeAudioCache.jsonEscape(currentTitle.orEmpty())}","artist":"${SpasibaNativeAudioCache.jsonEscape(currentArtist.orEmpty())}","artworkUrl":"${SpasibaNativeAudioCache.jsonEscape(currentArtworkUrl.orEmpty())}","currentTime":${formatSeconds(lastKnownPositionMs / 1000.0)},"duration":${formatSeconds(lastKnownDurationMs / 1000.0)}}
        """.trimIndent()
    }

    fun currentVideoId(): String? = currentVideoId

    fun currentArtworkUrl(): String? = currentArtworkUrl

    fun isPlayingOrLoading(): Boolean = currentState == "playing" || currentState == "loading"

    fun release() {
        released = true
        generation.incrementAndGet()
        executor.shutdownNow()
        mainHandler.post {
            releaseCurrentPlayer()
            updateState("paused")
        }
    }

    private fun prepareAndStart(stream: StreamingAudio, startSeconds: Double, requestGeneration: Int) {
        if (released || generation.get() != requestGeneration || currentVideoId != stream.videoId) {
            return
        }

        releaseCurrentPlayer()
        playerPrepared = false
        lastKnownPositionMs = (startSeconds.coerceAtLeast(0.0) * 1000.0).toLong()
        lastKnownDurationMs = (stream.durationSeconds.coerceAtLeast(0) * 1000L)
        updateState("loading")

        val player = ensurePlayer()
        player.volume = currentVolume
        player.setMediaSource(buildMediaSource(stream))
        player.prepare()
        if (lastKnownPositionMs > 0L) {
            player.seekTo(lastKnownPositionMs)
        }
        player.playWhenReady = true
        startProgressTicker()
    }

    private fun ensurePlayer(): ExoPlayer {
        exoPlayer?.let { return it }

        Log.d(LOG_TAG, "Creating ExoPlayer")

        val player = ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_NETWORK)
            addListener(playerListener)
            Log.d(LOG_TAG, "Player.Listener attached")

            volume = currentVolume
        }

        exoPlayer = player
        return player
    }
    private fun buildMediaSource(stream: StreamingAudio): ProgressiveMediaSource {
        val headers = stream.headers.toMutableMap().apply {
            putIfAbsent("User-Agent", DEFAULT_USER_AGENT)
        }
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(HTTP_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_TIMEOUT_MS)
            .setDefaultRequestProperties(headers)
        val dataSourceFactory = CacheDataSource.Factory()
            .setCache(getStreamCache(context))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val metadata = MediaMetadata.Builder()
            .apply {
                currentTitle?.takeIf { it.isNotBlank() }?.let(::setTitle)
                currentArtist?.takeIf { it.isNotBlank() }?.let(::setArtist)
                currentArtworkUrl?.takeIf { it.isNotBlank() }?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(stream.url))
            .setMediaMetadata(metadata)
            .apply {
                stream.mimeType?.takeIf { it.isNotBlank() }?.let(::setMimeType)
            }
            .build()

        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
    }

    private fun refreshKnownTiming() {
        val player = exoPlayer ?: return
        runCatching {
            val playbackState = player.playbackState
            val pos = player.currentPosition.coerceAtLeast(0L)
            if (playbackState == Player.STATE_READY || pos > lastKnownPositionMs || lastKnownPositionMs == 0L) {
                lastKnownPositionMs = pos
            }
            val duration = player.duration
            if (duration != C.TIME_UNSET && duration > 0L) {
                lastKnownDurationMs = duration
            }
        }
    }

    private fun seekOnMain(positionMs: Long) {
        val safeMs = positionMs.coerceAtLeast(0L)
        lastKnownPositionMs = safeMs
        val player = exoPlayer
        if (player != null) {
            runCatching {
                player.seekTo(safeMs)
                player.playWhenReady = true
            }
        }
        updateState(currentState)
    }

    private fun startProgressTicker() {
        if (progressTickerRunning) {
            return
        }

        progressTickerRunning = true
        mainHandler.removeCallbacks(progressTicker)
        mainHandler.post(progressTicker)
    }

    private fun upsertCurrentTrack(videoId: String, title: String?, artist: String?, artworkUrl: String?) {
        val track = NativeAudioTrack(
            videoId = videoId,
            title = title?.takeIf { it.isNotBlank() },
            artist = artist?.takeIf { it.isNotBlank() },
            artworkUrl = artworkUrl?.takeIf { it.isNotBlank() } ?: thumbnailUrl(videoId)
        )

        val existingIndex = findQueueIndex(videoId)
        if (existingIndex >= 0) {
            queueIndex = existingIndex
            queue = queue.mapIndexed { index, item -> if (index == existingIndex) mergeTrack(item, track) else item }
        } else {
            queue = (queue + track).takeLast(MAX_QUEUE_TRACKS)
            queueIndex = queue.lastIndex
        }
    }

    private fun mergeTrack(old: NativeAudioTrack, new: NativeAudioTrack): NativeAudioTrack =
        NativeAudioTrack(
            videoId = old.videoId,
            title = new.title ?: old.title,
            artist = new.artist ?: old.artist,
            artworkUrl = new.artworkUrl ?: old.artworkUrl
        )

    private fun playQueueOffset(offset: Int, restartCurrentWhenMissing: Boolean): Boolean {
        val currentIndex = findQueueIndex(currentVideoId).takeIf { it >= 0 } ?: queueIndex
        val nextIndex = currentIndex + offset

        if (nextIndex !in queue.indices) {
            if (restartCurrentWhenMissing && currentVideoId != null) {
                seek(0.0)
                return true
            }
            return false
        }

        val track = queue[nextIndex]
        queueIndex = nextIndex
        play(track.videoId, track.title, track.artist, track.artworkUrl)
        return true
    }

    private fun findQueueIndex(videoId: String?): Int {
        val normalizedVideoId = SpasibaNativeAudioCache.normalizeVideoId(videoId) ?: return -1
        return queue.indexOfFirst { it.videoId == normalizedVideoId }
    }

    private fun findQueueTrack(videoId: String?): NativeAudioTrack? {
        val normalizedVideoId = SpasibaNativeAudioCache.normalizeVideoId(videoId) ?: return null
        return queue.firstOrNull { it.videoId == normalizedVideoId }
    }

    private fun releaseCurrentPlayer() {
        val player = exoPlayer
        exoPlayer = null
        playerPrepared = false
        progressTickerRunning = false
        mainHandler.removeCallbacks(progressTicker)
        runCatching {
            player?.removeListener(playerListener)
            player?.release()
        }
    }

    private fun updateState(state: String) {
        currentState = state
        onStateChanged(state, currentTitle, currentArtist)
    }
    private fun formatSeconds(value: Double): String =
        String.format(Locale.US, "%.3f", value.coerceAtLeast(0.0))

    companion object {
        private const val LOG_TAG = "SpasibaNativePlayer"
        private const val MAX_QUEUE_TRACKS = 200
        private const val RESTART_PREVIOUS_AFTER_MS = 5_000L
        private const val PROGRESS_UPDATE_MS = 1_000L
        private const val HTTP_TIMEOUT_MS = 20_000
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Mobile Safari/537.36"

        fun thumbnailUrl(videoId: String): String =
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        fun clearSessionStreamCache(context: Context) {
            synchronized(STREAM_CACHE_LOCK) {
                if (streamCache != null) {
                    return
                }
                val cacheDir = File(context.cacheDir, STREAM_CACHE_DIR_NAME)
                runCatching { cacheDir.deleteRecursively() }
            }
        }

        private fun getStreamCache(context: Context): SimpleCache =
            streamCache ?: synchronized(STREAM_CACHE_LOCK) {
                streamCache ?: SimpleCache(
                    File(context.cacheDir, STREAM_CACHE_DIR_NAME),
                    LeastRecentlyUsedCacheEvictor(MAX_STREAM_CACHE_BYTES),
                    StandaloneDatabaseProvider(context)
                ).also { streamCache = it }
            }

        private const val STREAM_CACHE_DIR_NAME = "spasiba-exoplayer-stream-cache"
        private const val MAX_STREAM_CACHE_BYTES = 360L * 1024L * 1024L
        private val STREAM_CACHE_LOCK = Any()

        @Volatile
        private var streamCache: SimpleCache? = null
    }
}
