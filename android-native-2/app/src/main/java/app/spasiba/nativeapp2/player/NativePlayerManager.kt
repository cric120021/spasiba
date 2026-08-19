package app.spasiba.nativeapp2.player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.withContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import app.spasiba.nativeapp2.data.api.StreamingResult
import app.spasiba.nativeapp2.data.api.YouTubeMusicApi
import app.spasiba.nativeapp2.data.cache.QueueSnapshotCache
import app.spasiba.nativeapp2.data.engine.QuickPicksEngine
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import androidx.media3.common.PlaybackException
import java.io.File

data class CachedStream(val result: StreamingResult, val timestampMs: Long)

enum class RepeatState { OFF, ALL, ONE }

class NativePlayerManager(private val context: Context) {
    private val TAG = "NativePlayerManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val repository = MusicRepository(context)
    private val queueCache = QueueSnapshotCache(context)
    companion object {
        private const val MAX_QUEUE_TRACKS = 80
        private const val RESTART_PREVIOUS_AFTER_MS = 3000L
    }

    private val preResolvedStreamMap = java.util.concurrent.ConcurrentHashMap<String, CachedStream>()
    private val standbyVideoResultMap = java.util.concurrent.ConcurrentHashMap<String, StreamingResult>()
    private val retryAttempts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private var prefetchedVideoId: String? = null
    private var activePlayJob: kotlinx.coroutines.Job? = null
    private val activePlayRequestId = java.util.concurrent.atomic.AtomicLong(0L)
    var lastUserClickTimestampMs: Long = 0L
    val totalSessionBytes = java.util.concurrent.atomic.AtomicLong(0L)
    val currentTrackBytes = java.util.concurrent.atomic.AtomicLong(0L)

    private val networkTransferListener = object : androidx.media3.datasource.TransferListener {
        override fun onTransferInitializing(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {}
        override fun onTransferStart(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {}
        override fun onBytesTransferred(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
            if (isNetwork && bytesTransferred > 0) {
                totalSessionBytes.addAndGet(bytesTransferred.toLong())
                currentTrackBytes.addAndGet(bytesTransferred.toLong())
            }
        }
        override fun onTransferEnd(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {}
    }

    private fun createHttpDataSourceFactory(streamUrl: String? = null, headers: Map<String, String>? = null): androidx.media3.datasource.DataSource.Factory {
        val safeHeaders = headers?.toMutableMap() ?: mutableMapOf()
        val matchingUa = when {
            streamUrl?.contains("c=ANDROID_VR") == true -> "com.google.android.apps.youtube.vr.oculus/1.59.19 (Linux; U; Android 12)"
            streamUrl?.contains("c=ANDROID_TESTSUITE") == true -> "com.google.android.youtube/1.9.0 (Linux; U; Android 14)"
            streamUrl?.contains("c=ANDROID_MUSIC") == true -> "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; gzip)"
            streamUrl?.contains("c=ANDROID") == true -> "com.google.android.youtube/19.29.37 (Linux; U; Android 14)"
            streamUrl?.contains("c=IOS") == true -> "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X)"
            streamUrl?.contains("c=TVHTML5") == true || streamUrl?.contains("c=TV") == true -> "Mozilla/5.0 (SmartHub; SMART-TV; U; Linux/SmartTV) AppleWebKit/537.42"
            else -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        }
        safeHeaders["User-Agent"] = matchingUa
        if (streamUrl != null && !streamUrl.contains("c=ANDROID")) {
            safeHeaders["Origin"] = "https://www.youtube.com"
            safeHeaders["Referer"] = "https://www.youtube.com/"
        }
        return DefaultHttpDataSource.Factory()
            .setUserAgent(matchingUa)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)
            .setTransferListener(networkTransferListener)
            .setDefaultRequestProperties(safeHeaders)
    }

    private fun getValidCachedStream(targetId: String): StreamingResult? {
        val cached = preResolvedStreamMap[targetId] ?: return null
        val age = System.currentTimeMillis() - cached.timestampMs
        if (age < 3 * 3600 * 1000L && cached.result.url.isNotBlank()) {
            return cached.result
        }
        preResolvedStreamMap.remove(targetId)
        return null
    }

    val exoPlayer: ExoPlayer by lazy {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000,  // Min buffer: 15s (ensures smooth playback on 4G/5G)
                22000,  // Max buffer: 22s (tight sliding window, prevents heavy data wastage on skips)
                1000,   // Buffer for playback: 1.0s (instant start)
                2000    // Buffer after rebuffer: 2.0s
            )
            .setBackBuffer(10000, false) // Retain 10s back buffer for instant tiny rewinds
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
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
            }
    }

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _queueSourceLabel = MutableStateFlow<String>("Spasiba Radio")
    val queueSourceLabel: StateFlow<String> = _queueSourceLabel.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _repeatState = MutableStateFlow(RepeatState.OFF)
    val repeatState: StateFlow<RepeatState> = _repeatState.asStateFlow()

    private val _likedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()

    private val _currentLyrics = MutableStateFlow<String?>(null)
    val currentLyrics: StateFlow<String?> = _currentLyrics.asStateFlow()

    private val _syncedLyrics = MutableStateFlow<List<app.spasiba.nativeapp2.data.repository.LyricLine>>(emptyList())
    val syncedLyrics: StateFlow<List<app.spasiba.nativeapp2.data.repository.LyricLine>> = _syncedLyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    private val _videoAspectRatio = MutableStateFlow<Float>(16f / 9f)
    val videoAspectRatio: StateFlow<Float> = _videoAspectRatio.asStateFlow()

    fun fetchLyricsForCurrentSong() {
        val song = _currentSong.value ?: return
        scope.launch(Dispatchers.IO) {
            _isLyricsLoading.value = true
            val lines = YouTubeMusicApi.fetchUnifiedLyrics(song.id, song.title, song.author, song.durationSeconds)
            launch(Dispatchers.Main) {
                _syncedLyrics.value = lines
                _currentLyrics.value = lines.joinToString("\n") { it.text }
                _isLyricsLoading.value = false
            }
        }
    }

    private var progressJob: Job? = null

    init {
        startProgressTicker()
        observeLikedSongs()
        restoreQueueSnapshot()
    }

    private fun observeLikedSongs() {
        scope.launch {
            repository.likedSongs.collectLatest { list ->
                _likedSongIds.value = list.map { it.id }.toSet()
            }
        }
    }

    private fun restoreQueueSnapshot() {
        val snapshot = queueCache.restoreSnapshot()
        if (snapshot != null && snapshot.songs.isNotEmpty() && snapshot.currentIndex in snapshot.songs.indices) {
            _queue.value = snapshot.songs
            _currentIndex.value = snapshot.currentIndex
            _currentSong.value = snapshot.songs[snapshot.currentIndex]
            if (snapshot.positionMs > 0L) {
                _positionMs.value = snapshot.positionMs
            }
            maybePrefetchNextTrack()
            Log.d(TAG, "Restored android-native queue snapshot: ${snapshot.songs.size} tracks, index=${snapshot.currentIndex}, posMs=${snapshot.positionMs}")
        } else {
            val fallbacks = YouTubeMusicApi.getFallbackTracks()
            if (fallbacks.isNotEmpty()) {
                _queue.value = fallbacks
                _currentIndex.value = -1
                _currentSong.value = null
            }
        }
    }

    private fun persistQueue() {
        queueCache.saveSnapshot(_queue.value, _currentIndex.value, _positionMs.value)
    }

    /**
     * Exact upsertCurrentTrack from android-native SpasibaNativeAudioPlayer.kt:
     * If track exists in queue, moves index to it; otherwise appends to queue.
     */
    private fun upsertCurrentTrack(song: Song) {
        val currentQueue = _queue.value
        val existingIndex = currentQueue.indexOfFirst { it.id == song.id }

        if (existingIndex >= 0) {
            _currentIndex.value = existingIndex
            _queue.value = currentQueue.mapIndexed { idx, item -> if (idx == existingIndex) song else item }
        } else {
            val newQueue = (currentQueue + song).takeLast(MAX_QUEUE_TRACKS)
            _queue.value = newQueue
            _currentIndex.value = newQueue.lastIndex
        }
        _currentSong.value = song
        persistQueue()
    }

    /**
     * Smart Live Queue Warming Engine (matching audio-controller.ts):
     * Ensures minimum 10 high-quality, diverse upcoming tracks exist in the queue at all times.
     * Uses SmartQueueEngine for artist capping, low-quality video filtering, and score ranking.
     */
    private fun maybePrefetchNextTrack() {
        val currentQueue = _queue.value
        val curIndex = _currentIndex.value
        val currentTrack = _currentSong.value

        // Live Queue Warming: Replenish queue when upcoming tracks count < 6
        if (currentTrack != null && (currentQueue.size - curIndex) < 6) {
            scope.launch(Dispatchers.IO) {
                try {
                    Log.d(TAG, "SMART QUEUE ENGINE: Warming radio queue for '${currentTrack.title}' by ${currentTrack.author}")
                    val signals = SmartQueueEngine.inferQueueSignals(currentTrack, currentQueue)
                    val queries = SmartQueueEngine.buildQueueSearchBankQueries(currentTrack, signals)

                    val fetchedSongs = queries.flatMap { q -> YouTubeMusicApi.searchSongs(q) }

                    if (fetchedSongs.isNotEmpty()) {
                        val updatedQueue = SmartQueueEngine.appendSmartTracks(
                            reference = currentTrack,
                            baseQueue = _queue.value,
                            candidates = fetchedSongs,
                            limit = MAX_QUEUE_TRACKS
                        )

                        if (updatedQueue.size > _queue.value.size) {
                            launch(Dispatchers.Main) {
                                _queue.value = updatedQueue
                                persistQueue()
                                Log.d(TAG, "SMART QUEUE WARMED: New queue size = ${updatedQueue.size}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Smart queue warming failed: ${e.message}")
                }
            }
        }

        val nextIndex = curIndex + 1
        if (nextIndex in currentQueue.indices) {
            val nextTrack = currentQueue[nextIndex]
            if (prefetchedVideoId != nextTrack.id) {
                prefetchedVideoId = nextTrack.id
                Log.d(TAG, "PREFETCHING NEXT TRACK IN QUEUE title='${nextTrack.title}' id=${nextTrack.id}")

                scope.launch(Dispatchers.IO) {
                    var targetId = nextTrack.id
                    if (targetId.length != 11 || targetId.startsWith("track_") || targetId.startsWith("item_") || targetId.startsWith("VL") || targetId.startsWith("MP")) {
                        runCatching {
                            val searchResults = YouTubeMusicApi.searchSongs("${nextTrack.title} ${nextTrack.author}")
                            val validTrack = searchResults.firstOrNull { it.id.length == 11 }
                            if (validTrack != null) targetId = validTrack.id
                        }
                    }

                    val streamResult = repository.resolveStreamResult(targetId)

                    if (streamResult != null && streamResult.url.isNotBlank()) {
                        val entry = CachedStream(streamResult, System.currentTimeMillis())
                        preResolvedStreamMap[nextTrack.id] = entry
                        preResolvedStreamMap[targetId] = entry
                        Log.d(TAG, "PREFETCH SUCCESS for '${nextTrack.title}' (targetId=$targetId)")
                    }
                }
            }
        }
    }

    /**
     * Exact playQueueOffset from android-native SpasibaNativeAudioPlayer.kt:
     * Offset +1 for Next, -1 for Previous. Restarts track if position > 3 seconds.
     */
    private fun playQueueOffset(offset: Int): Boolean {
        val currentQueue = _queue.value
        val curIndex = _currentIndex.value
        val nextIndex = curIndex + offset

        if (nextIndex !in currentQueue.indices) {
            if (offset < 0 && _currentSong.value != null) {
                seekTo(0L)
                return true
            }
            return false
        }

        if (offset > 0 && _currentSong.value != null) {
            app.spasiba.nativeapp2.data.analytics.SessionBrainManager.recordSkip(_currentSong.value!!)
        }

        val nextTrack = currentQueue[nextIndex]
        _currentIndex.value = nextIndex
        playSong(nextTrack)
        maybeReplenishRadioQueue()
        return true
    }

    private fun maybeReplenishRadioQueue() {
        val currentQueue = _queue.value
        val curIndex = _currentIndex.value
        if (currentQueue.isEmpty() || curIndex < 0) return

        val remainingAhead = currentQueue.size - 1 - curIndex
        if (remainingAhead <= 3 && currentQueue.size < 15) {
            val currentTrack = currentQueue[curIndex]
            scope.launch(Dispatchers.IO) {
                try {
                    val candidateTracks = QuickPicksEngine.generateRadioQueue(currentTrack)
                    if (candidateTracks.isNotEmpty()) {
                        launch(Dispatchers.Main) {
                            val activeHistoryAndCurrent = _queue.value.take(curIndex + 1)
                            val deduplicatedCandidates = candidateTracks.filter { cand ->
                                !activeHistoryAndCurrent.any { it.id == cand.id }
                            }
                            val updatedQueue = (activeHistoryAndCurrent + deduplicatedCandidates)
                                .take(curIndex + 1 + SmartQueueEngine.UPCOMING_QUEUE_TARGET)
                            if (updatedQueue.size > _queue.value.size) {
                                _queue.value = updatedQueue
                                persistQueue()
                                Log.d(TAG, "RADIO QUEUE TOP-UP: Queue capped to ${updatedQueue.size} tracks")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to replenish radio queue: ${e.message}")
                }
            }
        }
    }

    fun setQueue(tracks: List<Song>, activeSongId: String?, sourceLabel: String = "Spasiba Radio") {
        val cleanTracks = tracks
            .distinctBy { it.id }
            .take(MAX_QUEUE_TRACKS)

        if (cleanTracks.isEmpty()) return

        _queue.value = cleanTracks
        val targetId = activeSongId ?: _currentSong.value?.id
        val idx = cleanTracks.indexOfFirst { it.id == targetId }
        _currentIndex.value = if (idx >= 0) idx else 0
        _queueSourceLabel.value = sourceLabel
        persistQueue()
    }

    fun playSingleSongWithRadio(clickedSong: Song, sourceLabel: String = "Spasiba Radio") {
        lastUserClickTimestampMs = System.currentTimeMillis()
        setQueue(listOf(clickedSong), clickedSong.id, sourceLabel)
        playSong(clickedSong, clickTimestampMs = lastUserClickTimestampMs)

        scope.launch(Dispatchers.IO) {
            try {
                val radioTracks = QuickPicksEngine.generateRadioQueue(clickedSong)
                if (radioTracks.isNotEmpty()) {
                    launch(Dispatchers.Main) {
                        val updatedQueue = SmartQueueEngine.appendSmartTracks(
                            reference = clickedSong,
                            baseQueue = listOf(clickedSong),
                            candidates = radioTracks,
                            limit = SmartQueueEngine.UPCOMING_QUEUE_TARGET + 1
                        )
                        _queue.value = updatedQueue
                        persistQueue()
                        Log.d(TAG, "PLAY SINGLE SONG RADIO: Queue set to ${updatedQueue.size} smart radio tracks")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Radio queue generation failed: ${e.message}")
            }
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0, sourceLabel: String = "Spasiba Radio") {
        if (songs.isEmpty() || startIndex !in songs.indices) return
        lastUserClickTimestampMs = System.currentTimeMillis()
        val clickedSong = songs[startIndex]
        setQueue(songs, clickedSong.id, sourceLabel)
        playSong(clickedSong, clickTimestampMs = lastUserClickTimestampMs)
    }

    fun addToQueue(song: Song) {
        val currentList = _queue.value.toMutableList()
        if (!currentList.any { it.id == song.id }) {
            currentList.add(song)
        }
        _queue.value = currentList
        persistQueue()
        maybePrefetchNextTrack()
        if (_currentSong.value == null) {
            _currentIndex.value = 0
            playSong(song)
        }
    }

    fun playNextInQueue(song: Song) {
        val currentList = _queue.value.toMutableList()
        currentList.removeAll { it.id == song.id }
        val insertIndex = (_currentIndex.value + 1).coerceAtMost(currentList.size)
        currentList.add(insertIndex, song)
        _queue.value = currentList
        persistQueue()
        maybePrefetchNextTrack()
        if (_currentSong.value == null) {
            _currentIndex.value = 0
            playSong(song)
        }
    }

    fun removeFromQueue(index: Int) {
        val currentList = _queue.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _queue.value = currentList
            persistQueue()
            if (index == _currentIndex.value) {
                if (currentList.isNotEmpty()) {
                    val nextIdx = index % currentList.size
                    _currentIndex.value = nextIdx
                    playSong(currentList[nextIdx])
                } else {
                    _currentSong.value = null
                    exoPlayer.stop()
                    _isPlaying.value = false
                }
            } else if (index < _currentIndex.value) {
                _currentIndex.value = _currentIndex.value - 1
            }
        }
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        val currentList = _queue.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            _queue.value = currentList
            if (_currentIndex.value == fromIndex) {
                _currentIndex.value = toIndex
            }
            persistQueue()
        }
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _currentIndex.value = -1
        _currentSong.value = null
        preResolvedStreamMap.clear()
        prefetchedVideoId = null
        exoPlayer.stop()
        _isPlaying.value = false
        persistQueue()
    }

    private val _currentVideoQuality = MutableStateFlow("720p")
    val videoQuality: StateFlow<String> = _currentVideoQuality.asStateFlow()

    private val _availableVideoQualities = MutableStateFlow<List<String>>(listOf("Auto", "1080p", "720p", "480p", "360p"))
    val availableVideoQualities: StateFlow<List<String>> = _availableVideoQualities.asStateFlow()
    private var lastPlayedSongId: String? = null
    var isVideoModeActive: Boolean = false
    private var isVideoSuspendedForBackground = false

    fun onAppBackgrounded() {
        if (isVideoModeActive && (exoPlayer.isPlaying || exoPlayer.playbackState == Player.STATE_BUFFERING)) {
            isVideoSuspendedForBackground = true
            // Suspend video track rendering and network chunk requests while keeping audio continuous
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
            Log.d("SPASIBA_111", "[DATA_SAVER] Screen-off/Backgrounded: Video stream suspended, audio continues seamlessly at ${exoPlayer.currentPosition}ms (Saving ~80% data & battery)")
        }
    }

    fun onAppForegrounded() {
        if (isVideoModeActive && isVideoSuspendedForBackground) {
            isVideoSuspendedForBackground = false
            // Re-enable video track seamlessly at the exact current position
            applyTrackSelectionQuality(_currentVideoQuality.value)
            Log.d("SPASIBA_111", "[DATA_SAVER] Screen-on/Foregrounded: Video stream re-attached at ${exoPlayer.currentPosition}ms")
        }
    }

    fun disableVideoMode() {
        val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
        Log.d("SPASIBA_111", "[MODE_SWITCH] Disabling Video Mode -> Switching to pure Audio Mode at ${currentPos}ms")
        isVideoModeActive = false

        val currentSong = _currentSong.value
        if (currentSong != null) {
            val cachedAudio = getValidCachedStream(currentSong.id)
            if (cachedAudio != null && cachedAudio.url.isNotBlank()) {
                val dataSourceFactory = createHttpDataSourceFactory(cachedAudio.url, cachedAudio.headers)
                val mediaSourceFactory = DefaultMediaSourceFactory(context, DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true))
                    .setDataSourceFactory(dataSourceFactory)

                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(currentSong.title)
                    .setArtist(currentSong.author)
                    .setArtworkUri(Uri.parse(currentSong.thumbnail))
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(cachedAudio.url))
                    .setMediaId(currentSong.id)
                    .setMediaMetadata(mediaMetadata)
                    .build()

                exoPlayer.setMediaSource(mediaSourceFactory.createMediaSource(mediaItem))
                exoPlayer.prepare()
                if (currentPos > 0L) exoPlayer.seekTo(currentPos)
                exoPlayer.playWhenReady = true
                exoPlayer.play()
                return
            }
        }

        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()
    }

    fun setVideoQuality(quality: String) {
        val song = _currentSong.value ?: return
        isVideoModeActive = true
        _currentVideoQuality.value = quality
        startProgressiveVideoDownloadAndPlay(song)
    }

    private fun applyTrackSelectionQuality(quality: String) {
        val targetHeight = when {
            quality.contains("2160") || quality.contains("4K", ignoreCase = true) -> 2160
            quality.contains("1440") || quality.contains("2K", ignoreCase = true) -> 1440
            quality.contains("1080") -> 1080
            quality.contains("720") -> 720
            quality.contains("480") -> 480
            quality.contains("360") -> 360
            else -> Int.MAX_VALUE
        }

        val builder = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)

        if (targetHeight == Int.MAX_VALUE) {
            builder.clearVideoSizeConstraints()
        } else {
            builder.setMaxVideoSize(Int.MAX_VALUE, targetHeight)
        }
        exoPlayer.trackSelectionParameters = builder.build()
        Log.d("SPASIBA_111", "[TRACK_SELECTION] Video enabled: targetHeight=$targetHeight for quality=$quality")
    }

    fun startProgressiveVideoDownloadAndPlay(song: Song, requestId: Long? = null, clickTimestampMs: Long? = null) {
        val clickTime = clickTimestampMs ?: (if (lastUserClickTimestampMs > 0 && System.currentTimeMillis() - lastUserClickTimestampMs < 5000) lastUserClickTimestampMs else System.currentTimeMillis())
        lastUserClickTimestampMs = clickTime
        activePlayJob?.cancel()
        val currentRequestId = requestId ?: activePlayRequestId.incrementAndGet()
        
        Log.w("SPASIBA_111", "⚡ [VIDEO_CLICK_TRIGGER] 🎬 User triggered VIDEO playback for '${song.title}' (req #$currentRequestId, targetQuality=${_currentVideoQuality.value})")

        // 1. Instant Standby Hit (0-second switch from memory!)
        val standbyHd = standbyVideoResultMap[song.id] ?: standbyVideoResultMap[song.id.takeIf { it.length == 11 } ?: ""]
        if (standbyHd != null && standbyHd.url.isNotBlank()) {
            val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            Log.i("SPASIBA_111", "⚡ [STANDBY_VIDEO_HIT] Instant 0s switch to 720p HD video from standby memory for '${song.title}' at ${currentPos}ms!")

            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.author)
                .setArtworkUri(Uri.parse(song.thumbnail))
                .build()

            val videoDataSourceFactory = createHttpDataSourceFactory(standbyHd.url, standbyHd.headers)
            val videoMediaSourceFactory = DefaultMediaSourceFactory(context, DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true))
                .setDataSourceFactory(videoDataSourceFactory)

            val videoMediaItem = MediaItem.Builder()
                .setUri(Uri.parse(standbyHd.url))
                .setMediaId(song.id)
                .setMediaMetadata(mediaMetadata)
                .build()

            val videoSource = videoMediaSourceFactory.createMediaSource(videoMediaItem)

            val finalSource = if (!standbyHd.audioUrl.isNullOrBlank()) {
                val audioDataSourceFactory = createHttpDataSourceFactory(standbyHd.audioUrl, standbyHd.headers)
                val audioMediaSourceFactory = DefaultMediaSourceFactory(context, DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true))
                    .setDataSourceFactory(audioDataSourceFactory)

                val audioMediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(standbyHd.audioUrl))
                    .setMediaId("${song.id}_audio")
                    .build()
                val audioSource = audioMediaSourceFactory.createMediaSource(audioMediaItem)
                MergingMediaSource(videoSource, audioSource)
            } else {
                videoSource
            }

            applyTrackSelectionQuality(_currentVideoQuality.value)
            exoPlayer.setMediaSource(finalSource)
            exoPlayer.prepare()
            if (currentPos > 0L) exoPlayer.seekTo(currentPos)
            exoPlayer.playWhenReady = true
            exoPlayer.play()
            _isPlaying.value = true
            _isLoading.value = false
            return
        }
        
        // 2. Cold Resolution (if user tapped video before background pre-resolver finished)
        activePlayJob = scope.launch(Dispatchers.IO) {
            _isLoading.value = true

            val idResolveStart = System.currentTimeMillis()
            var targetVideoId = song.id
            if (targetVideoId.length != 11 || targetVideoId.startsWith("track_") || targetVideoId.startsWith("item_") || targetVideoId.startsWith("VL") || targetVideoId.startsWith("MP") || targetVideoId.isBlank()) {
                runCatching {
                    Log.d("SPASIBA_111", "[VIDEO_MODE_RESOLVE_ID] Resolving 11-char canonical videoId for '${song.title}'")
                    val searchResults = YouTubeMusicApi.searchSongs("${song.title} ${song.author}")
                    val validTrack = searchResults.firstOrNull { it.id.length == 11 }
                    if (validTrack != null) {
                        targetVideoId = validTrack.id
                    }
                }
            }
            val idResolveElapsed = System.currentTimeMillis() - idResolveStart

            if (activePlayRequestId.get() != currentRequestId || !isActive) {
                Log.d("SPASIBA_111", "[VIDEO_MODE_ABORT] Request #$currentRequestId superseded before quality fetch")
                return@launch
            }

            val fetchedQualities = YouTubeMusicApi.fetchAvailableVideoQualities(context, targetVideoId)
            _availableVideoQualities.value = fetchedQualities

            if (activePlayRequestId.get() != currentRequestId || !isActive) {
                Log.d("SPASIBA_111", "[VIDEO_MODE_ABORT] Request #$currentRequestId superseded before stream resolve")
                return@launch
            }

            val streamResolveStart = System.currentTimeMillis()
            val result = YouTubeMusicApi.resolveVideoStreamResult(context, targetVideoId, _currentVideoQuality.value)
            val streamResolveElapsed = System.currentTimeMillis() - streamResolveStart
            
            if (result != null && result.url.isNotBlank()) {
                standbyVideoResultMap[song.id] = result
                standbyVideoResultMap[targetVideoId] = result
            }

            withContext(Dispatchers.Main) {
                if (activePlayRequestId.get() != currentRequestId || !isActive) {
                    Log.d("SPASIBA_111", "[VIDEO_MODE_DISCARD] Discarding stale video result for '${song.title}' (req #$currentRequestId vs active #${activePlayRequestId.get()})")
                    return@withContext
                }

                if (result != null && result.url.isNotBlank()) {
                    val exoPrepareStart = System.currentTimeMillis()
                    val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val mediaMetadata = MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.author)
                        .setArtworkUri(Uri.parse(song.thumbnail))
                        .build()

                    val videoDataSourceFactory = createHttpDataSourceFactory(result.url, result.headers)
                    val videoMediaSourceFactory = DefaultMediaSourceFactory(context, DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true))
                        .setDataSourceFactory(videoDataSourceFactory)

                    val videoMediaItem = MediaItem.Builder()
                        .setUri(Uri.parse(result.url))
                        .setMediaId(song.id)
                        .setMediaMetadata(mediaMetadata)
                        .build()

                    val videoSource = videoMediaSourceFactory.createMediaSource(videoMediaItem)

                    val finalSource = if (!result.audioUrl.isNullOrBlank()) {
                        val audioDataSourceFactory = createHttpDataSourceFactory(result.audioUrl, result.headers)
                        val audioMediaSourceFactory = DefaultMediaSourceFactory(context, DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true))
                            .setDataSourceFactory(audioDataSourceFactory)

                        val audioMediaItem = MediaItem.Builder()
                            .setUri(Uri.parse(result.audioUrl))
                            .setMediaId("${song.id}_audio")
                            .build()
                        val audioSource = audioMediaSourceFactory.createMediaSource(audioMediaItem)
                        MergingMediaSource(videoSource, audioSource)
                    } else {
                        videoSource
                    }

                    applyTrackSelectionQuality(_currentVideoQuality.value)
                    exoPlayer.setMediaSource(finalSource)
                    exoPlayer.prepare()
                    if (currentPos > 0) exoPlayer.seekTo(currentPos)
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                    _isPlaying.value = true
                    val exoPrepareElapsed = System.currentTimeMillis() - exoPrepareStart
                    val totalClickToPlayMs = System.currentTimeMillis() - clickTime

                    Log.i("SPASIBA_111", "🟣 ╔══════════════════════════════════════════════════════════════════════")
                    Log.i("SPASIBA_111", "🟣 ║ 🎬 [VIDEO_SPEED_BENCHMARK]")
                    Log.i("SPASIBA_111", "🟣 ║ Track: '${song.title}' | Quality: ${_currentVideoQuality.value}")
                    Log.i("SPASIBA_111", "🟣 ║ ⚡ TOTAL CLICK-TO-PLAY: ${totalClickToPlayMs} ms")
                    Log.i("SPASIBA_111", "🟣 ║ ├── 🔍 ID Canonicalization : ${idResolveElapsed} ms")
                    Log.i("SPASIBA_111", "🟣 ║ ├── 🌐 Stream URLs (yt-dlp) : ${streamResolveElapsed} ms")
                    Log.i("SPASIBA_111", "🟣 ║ └── 🎬 ExoPlayer Prepare/Play: ${exoPrepareElapsed} ms")
                    Log.i("SPASIBA_111", "🟣 ╚══════════════════════════════════════════════════════════════════════")
                } else {
                    Log.e("SPASIBA_111", "🔴 [VIDEO_MODE_ERROR] Failed to resolve video stream result for videoId=$targetVideoId")
                    android.widget.Toast.makeText(context, "Video stream unavailable for ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                }
                _isLoading.value = false
            }
        }
    }

    private fun playLocalMp4File(mp4File: File, song: Song) {
        val currentPos = exoPlayer.currentPosition
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.author)
            .setArtworkUri(Uri.parse(song.thumbnail))
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(mp4File))
            .setMediaId(song.id)
            .setMediaMetadata(mediaMetadata)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (currentPos > 0) exoPlayer.seekTo(currentPos)
        exoPlayer.play()
        _isLoading.value = false
        Log.d(TAG, "ExoPlayer playing local progressive MP4: ${mp4File.absolutePath} size=${mp4File.length()}")
    }

    fun cleanupTempVideoData(songId: String) {
        runCatching {
            val cacheDir = File(context.cacheDir, "spasiba_video")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { f ->
                    if (f.name.contains(songId)) {
                        f.delete()
                        Log.d(TAG, "Deleted temp video file for song $songId: ${f.name}")
                    }
                }
            }
        }
    }

    var eunoiaTrackChangeListener: ((Song) -> Unit)? = null
    var eunoiaPlaybackActionListener: ((action: String, extraPayload: org.json.JSONObject) -> Unit)? = null

    fun playSong(song: Song, initialPosMs: Long = 0L, clickTimestampMs: Long? = null) {
        val clickTime = clickTimestampMs ?: (if (lastUserClickTimestampMs > 0 && System.currentTimeMillis() - lastUserClickTimestampMs < 5000) lastUserClickTimestampMs else System.currentTimeMillis())
        lastUserClickTimestampMs = clickTime
        activePlayJob?.cancel()
        val currentRequestId = activePlayRequestId.incrementAndGet()
        
        Log.w("SPASIBA_111", "⚡ [AUDIO_CLICK_TRIGGER] 🎧 User triggered AUDIO playback for '${song.title}' by ${song.author} (req #$currentRequestId, isVideoModeActive=$isVideoModeActive)")
        _isLoading.value = true
        retryAttempts.remove(song.id)
        eunoiaTrackChangeListener?.invoke(song)
        
        // Auto cleanup video files once previous song is out of picture
        if (lastPlayedSongId != null && lastPlayedSongId != song.id) {
            cleanupTempVideoData(lastPlayedSongId!!)
        }
        lastPlayedSongId = song.id

        _currentSong.value = song
        upsertCurrentTrack(song)
        fetchLyricsForCurrentSong()
        app.spasiba.nativeapp2.data.analytics.SessionBrainManager.recordPlayStart(song)
        _positionMs.value = initialPosMs.coerceAtLeast(0L)
        currentTrackBytes.set(0L)

        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        if (isVideoModeActive) {
            Log.d("SPASIBA_111", "[PLAY_BRANCH] Redirecting to Video Mode pipeline for '${song.title}' (req #$currentRequestId)")
            startProgressiveVideoDownloadAndPlay(song, currentRequestId, clickTime)
            return
        }

        activePlayJob = scope.launch(Dispatchers.IO) {
            try {
                if (activePlayRequestId.get() != currentRequestId || !isActive) return@launch
                repository.recordPlayedSong(song)

                val idResolveStart = System.currentTimeMillis()
                var targetVideoId = song.id
                if (targetVideoId.length != 11 || targetVideoId.startsWith("track_") || targetVideoId.startsWith("item_") || targetVideoId.startsWith("VL") || targetVideoId.startsWith("MP") || targetVideoId.isBlank()) {
                    runCatching {
                        Log.d("SPASIBA_111", "[PLAY_RESOLVE_ID] Resolving canonical 11-char videoId for '${song.title}'")
                        val searchResults = YouTubeMusicApi.searchSongs("${song.title} ${song.author}")
                        val validTrack = searchResults.firstOrNull { it.id.length == 11 }
                        if (validTrack != null) {
                            targetVideoId = validTrack.id
                        }
                    }
                }
                val idResolveElapsed = System.currentTimeMillis() - idResolveStart

                if (activePlayRequestId.get() != currentRequestId || !isActive) {
                    Log.d("SPASIBA_111", "[PLAY_ABORT] Request #$currentRequestId was superseded before local/cache check")
                    return@launch
                }

                // Check if track is cached or downloaded locally first
                val downloadMgr = app.spasiba.nativeapp2.data.download.SpasibaDownloadManager(context)
                val localFile = downloadMgr.getDownloadedFile(song.id)
                    ?: downloadMgr.getDownloadedFile(targetVideoId)
                    ?: app.spasiba.nativeapp2.data.cache.SpasibaAudioCacheManager(context).findCachedAudioFile(song.id)
                    ?: app.spasiba.nativeapp2.data.cache.SpasibaAudioCacheManager(context).findCachedAudioFile(targetVideoId)
                    ?: File(context.cacheDir, "spasiba_audio/${song.id}.m4a").takeIf { it.exists() && it.length() > 0 }
                    ?: File(context.cacheDir, "spasiba_video/${song.id}.mp4").takeIf { it.exists() && it.length() > 0 }
                    ?: File(context.getExternalFilesDir(null), "downloads/${song.id}.m4a").takeIf { it.exists() && it.length() > 0 }
                    ?: File(context.getExternalFilesDir(null), "downloads/${song.id}.mp3").takeIf { it.exists() && it.length() > 0 }

                if (localFile != null && localFile.length() > 0) {
                    val localPlayLatency = System.currentTimeMillis() - clickTime
                    Log.i("SPASIBA_111", "🟢 ╔══════════════════════════════════════════════════════════════════════")
                    Log.i("SPASIBA_111", "🟢 ║ 💾 [OFFLINE_LOCAL_PLAY_SPEED]")
                    Log.i("SPASIBA_111", "🟢 ║ Track: '${song.title}' | ⚡ TOTAL CLICK-TO-PLAY: ${localPlayLatency} ms (100% Offline)")
                    Log.i("SPASIBA_111", "🟢 ╚══════════════════════════════════════════════════════════════════════")

                    withContext(Dispatchers.Main) {
                        if (activePlayRequestId.get() != currentRequestId || !isActive) return@withContext
                        val mediaMetadata = MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.author)
                            .setArtworkUri(Uri.parse(song.thumbnail))
                            .build()

                        val mediaItem = MediaItem.Builder()
                            .setUri(Uri.fromFile(localFile))
                            .setMediaId(song.id)
                            .setMediaMetadata(mediaMetadata)
                            .build()

                        exoPlayer.setMediaItem(mediaItem)
                        exoPlayer.prepare()
                        if (initialPosMs > 0L) exoPlayer.seekTo(initialPosMs)
                        exoPlayer.play()
                        _isLoading.value = false
                    }
                    return@launch
                }

                if (activePlayRequestId.get() != currentRequestId || !isActive) {
                    Log.d("SPASIBA_111", "[PLAY_ABORT] Request #$currentRequestId was superseded before stream resolve")
                    return@launch
                }

                // Check pre-resolved cache or resolve fresh stream
                val cached = getValidCachedStream(targetVideoId)
                val isCacheHit = cached != null

                val streamStart = System.currentTimeMillis()
                var result = cached ?: repository.resolveStreamResult(targetVideoId)
                val streamResolveElapsed = System.currentTimeMillis() - streamStart

                if (result == null || result.url.isBlank()) {
                    if (activePlayRequestId.get() != currentRequestId || !isActive) return@launch
                    runCatching {
                        val fallbackSearch = YouTubeMusicApi.searchSongs("${song.title} ${song.author} audio")
                        val altId = fallbackSearch.firstOrNull { it.id != targetVideoId && it.id.length == 11 }?.id
                        if (altId != null) {
                            result = repository.resolveStreamResult(altId)
                        }
                    }
                }

                if (result != null && result.url.isNotBlank()) {
                    preResolvedStreamMap[targetVideoId] = CachedStream(result!!, System.currentTimeMillis())
                }

                withContext(Dispatchers.Main) {
                    if (activePlayRequestId.get() != currentRequestId || !isActive) {
                        Log.d("SPASIBA_111", "[PLAY_DISCARD] Discarding stale audio stream result for '${song.title}' (req #$currentRequestId vs active #${activePlayRequestId.get()})")
                        return@withContext
                    }

                    if (result == null || result.url.isBlank()) {
                        Log.e("SPASIBA_111", "🔴 [PLAY_ERROR] Stream URL resolution failed completely for ${song.title}")
                        _isLoading.value = false
                        _isPlaying.value = false
                        android.widget.Toast.makeText(context, "Playback error: Stream unavailable for ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    val exoPrepareStart = System.currentTimeMillis()
                    val targetUri = if (result.url.startsWith("/")) {
                        Uri.fromFile(File(result.url))
                    } else {
                        Uri.parse(result.url)
                    }

                    if (targetUri.scheme == "file") {
                        val mediaMetadata = MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.author)
                            .setArtworkUri(Uri.parse(song.thumbnail))
                            .build()
                        val mediaItem = MediaItem.Builder()
                            .setUri(targetUri)
                            .setMediaId(song.id)
                            .setMediaMetadata(mediaMetadata)
                            .build()
                        exoPlayer.setMediaItem(mediaItem)
                    } else {
                        val dataSourceFactory = createHttpDataSourceFactory(result.url, result.headers)
                        val mediaSourceFactory = DefaultMediaSourceFactory(context, DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true))
                            .setDataSourceFactory(dataSourceFactory)

                        val mediaMetadata = MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.author)
                            .setArtworkUri(Uri.parse(song.thumbnail))
                            .build()

                        val mediaItem = MediaItem.Builder()
                            .setUri(targetUri)
                            .setMediaId(song.id)
                            .setMediaMetadata(mediaMetadata)
                            .build()

                        val mediaSource = mediaSourceFactory
                            .createMediaSource(mediaItem)

                        exoPlayer.setMediaSource(mediaSource)
                    }

                    exoPlayer.prepare()
                    if (initialPosMs > 0L) exoPlayer.seekTo(initialPosMs)
                    exoPlayer.playWhenReady = true
                    _isLoading.value = false
                    _isPlaying.value = true
                    val exoPrepareElapsed = System.currentTimeMillis() - exoPrepareStart
                    val totalClickToPlayMs = System.currentTimeMillis() - clickTime

                    Log.i("SPASIBA_111", "🟢 ╔══════════════════════════════════════════════════════════════════════")
                    Log.i("SPASIBA_111", "🟢 ║ 🎧 [AUDIO_SPEED_BENCHMARK]")
                    Log.i("SPASIBA_111", "🟢 ║ Track: '${song.title}' by ${song.author}")
                    Log.i("SPASIBA_111", "🟢 ║ ⚡ TOTAL CLICK-TO-PLAY: ${totalClickToPlayMs} ms")
                    Log.i("SPASIBA_111", "🟢 ║ ├── 🔍 ID Canonicalization : ${idResolveElapsed} ms")
                    Log.i("SPASIBA_111", "🟢 ║ ├── 🌐 Stream URL Resolve  : ${streamResolveElapsed} ms (CacheHit: $isCacheHit)")
                    Log.i("SPASIBA_111", "🟢 ║ └── 🎵 ExoPlayer Buffer/Play: ${exoPrepareElapsed} ms")
                    Log.i("SPASIBA_111", "🟢 ╚══════════════════════════════════════════════════════════════════════")

                    maybePrefetchNextTrack()

                    // Background Standby Video Pre-Resolver & Dynamic Quality Discovery
                    scope.launch(Dispatchers.IO) {
                        if (activePlayRequestId.get() != currentRequestId || !isActive) return@launch
                        try {
                            // 1. Discover exact real available qualities for this video
                            val dynamicQualities = YouTubeMusicApi.fetchAvailableVideoQualities(context, targetVideoId)
                            if (dynamicQualities.isNotEmpty() && activePlayRequestId.get() == currentRequestId) {
                                withContext(Dispatchers.Main) {
                                    _availableVideoQualities.value = dynamicQualities
                                }
                                Log.i("SPASIBA_111", "🎬 [DYNAMIC_QUALITIES] Discovered real resolutions for '${song.title}': $dynamicQualities")
                            }

                            // 2. Pre-resolve 720p HD video on standby in memory
                            Log.d("SPASIBA_111", "🎬 [STANDBY_VIDEO_PRE_RESOLVE] Pre-resolving 720p HD video for '${song.title}' ($targetVideoId) to standby memory")
                            val hdResult = YouTubeMusicApi.resolveVideoStreamResult(context, targetVideoId, _currentVideoQuality.value)
                            if (hdResult != null && hdResult.url.isNotBlank() && activePlayRequestId.get() == currentRequestId) {
                                standbyVideoResultMap[song.id] = hdResult
                                standbyVideoResultMap[targetVideoId] = hdResult
                                Log.i("SPASIBA_111", "🎬 [STANDBY_VIDEO_HOT] 720p HD video is HOT on standby for '${song.title}'! (0s instant video switch ready)")
                            }
                        } catch (e: Exception) {
                            // Silent
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d("SPASIBA_111", "[PLAY_CANCELLED] Playback job for '${song.title}' cancelled cleanly")
                    return@launch
                }
                Log.e("SPASIBA_111", "🔴 [PLAY_EXCEPTION] Failed to play song ${song.title}: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (activePlayRequestId.get() == currentRequestId) {
                        _isLoading.value = false
                        _isPlaying.value = false
                    }
                }
            }
        }
    }

    fun togglePlayPause() {
        Log.d("SPASIBA_111", "[CONTROL_TOGGLE] togglePlayPause called. Currently isPlaying=${exoPlayer.isPlaying} state=${exoPlayer.playbackState}")
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            _isPlaying.value = false
            Log.d("SPASIBA_111", "[CONTROL_TOGGLE] Paused playback at ${exoPlayer.currentPosition}ms")
            eunoiaPlaybackActionListener?.invoke("pause", org.json.JSONObject().apply {
                put("positionMs", exoPlayer.currentPosition.coerceAtLeast(0L))
                put("isPlaying", false)
            })
        } else {
            val song = _currentSong.value
            if (song != null) {
                if (exoPlayer.playbackState == ExoPlayer.STATE_IDLE || exoPlayer.playbackState == ExoPlayer.STATE_ENDED) {
                    Log.d("SPASIBA_111", "[CONTROL_TOGGLE] ExoPlayer in IDLE/ENDED -> Replaying '${song.title}'")
                    playSong(song)
                } else {
                    Log.d("SPASIBA_111", "[CONTROL_TOGGLE] Resuming playback of '${song.title}' at ${exoPlayer.currentPosition}ms")
                    exoPlayer.play()
                    _isPlaying.value = true
                }
            } else if (_queue.value.isNotEmpty()) {
                Log.d("SPASIBA_111", "[CONTROL_TOGGLE] Playing first track from queue")
                playSong(_queue.value.first())
            } else {
                val fallbacks = YouTubeMusicApi.getFallbackTracks()
                if (fallbacks.isNotEmpty()) {
                    Log.d("SPASIBA_111", "[CONTROL_TOGGLE] Playing fallback queue")
                    playQueue(fallbacks, 0)
                }
            }
            eunoiaPlaybackActionListener?.invoke("play", org.json.JSONObject().apply {
                put("positionMs", exoPlayer.currentPosition.coerceAtLeast(0L))
                put("isPlaying", true)
            })
        }
    }

    fun playNext() {
        Log.d("SPASIBA_111", "[CONTROL_NEXT] playNext triggered. Current queue index=${_currentIndex.value} queueSize=${_queue.value.size}")
        playQueueOffset(1)
    }

    fun playPrevious() {
        Log.d("SPASIBA_111", "[CONTROL_PREV] playPrevious triggered. Current pos=${_positionMs.value}ms")
        if (_positionMs.value > RESTART_PREVIOUS_AFTER_MS) {
            Log.d("SPASIBA_111", "[CONTROL_PREV] Restarting current song from 0ms")
            seekTo(0L)
        } else {
            Log.d("SPASIBA_111", "[CONTROL_PREV] Skipping to previous track offset -1")
            playQueueOffset(-1)
        }
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        Log.d("SPASIBA_111", "[CONTROL_SEEK] Seeking to ${safePos}ms (from ${_positionMs.value}ms)")
        exoPlayer.seekTo(safePos)
        _positionMs.value = safePos
        eunoiaPlaybackActionListener?.invoke("seek", org.json.JSONObject().apply {
            put("positionMs", safePos)
            put("isPlaying", _isPlaying.value)
        })
    }

    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
        exoPlayer.setPlaybackSpeed(safeSpeed)
    }

    fun toggleLike(song: Song) {
        app.spasiba.nativeapp2.data.analytics.SessionBrainManager.recordLike(song)
        val currentlyLiked = likedSongIds.value.contains(song.id)
        scope.launch {
            repository.toggleLike(song)
            withContext(Dispatchers.Main) {
                val msg = if (!currentlyLiked) "❤️ Saved to Liked Songs" else "Removed from Liked Songs"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleRepeat() {
        _repeatState.value = when (_repeatState.value) {
            RepeatState.OFF -> RepeatState.ALL
            RepeatState.ALL -> RepeatState.ONE
            RepeatState.ONE -> RepeatState.OFF
        }
    }

    private fun formatDataSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f GB", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f MB", bytes.toDouble() / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes.toDouble() / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format(java.util.Locale.US, "%.1f KB/s", bytesPerSec / 1024.0)
            else -> String.format(java.util.Locale.US, "%.0f B/s", bytesPerSec)
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var lastLogTime = System.currentTimeMillis()
            var lastLogBytes = currentTrackBytes.get()

            while (true) {
                if (exoPlayer.isPlaying || exoPlayer.playbackState == Player.STATE_BUFFERING) {
                    _positionMs.value = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = exoPlayer.duration
                    if (dur != C.TIME_UNSET && dur > 0L) {
                        _durationMs.value = dur
                    }

                    val now = System.currentTimeMillis()
                    val timeDeltaMs = now - lastLogTime
                    if (timeDeltaMs >= 1000L) {
                        val currentBytes = currentTrackBytes.get()
                        val sessionBytes = totalSessionBytes.get()
                        val bytesDelta = (currentBytes - lastLogBytes).coerceAtLeast(0L)
                        val speedBytesPerSec = if (timeDeltaMs > 0) (bytesDelta.toDouble() / (timeDeltaMs.toDouble() / 1000.0)) else 0.0

                        val modeLabel = if (isVideoModeActive) "VIDEO (${_currentVideoQuality.value})" else "AUDIO"

                        Log.d(
                            "SPASIBA_111",
                            "[LIVE_DATA_USAGE] $modeLabel | Speed: ${formatSpeed(speedBytesPerSec)} | Track: ${formatDataSize(currentBytes)} | Session Total: ${formatDataSize(sessionBytes)} | Pos: ${_positionMs.value / 1000}s/${if (_durationMs.value > 0) "${_durationMs.value / 1000}s" else "--"}"
                        )

                        lastLogTime = now
                        lastLogBytes = currentBytes
                    }

                    // Check if 70% of song is reached -> trigger prefetch of next track
                    if (_durationMs.value > 0L && (_positionMs.value.toDouble() / _durationMs.value.toDouble()) >= 0.70) {
                        maybePrefetchNextTrack()
                    }
                }
                delay(200)
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName = when (playbackState) {
                Player.STATE_BUFFERING -> "STATE_BUFFERING"
                Player.STATE_READY -> "STATE_READY"
                Player.STATE_ENDED -> "STATE_ENDED"
                Player.STATE_IDLE -> "STATE_IDLE"
                else -> "UNKNOWN($playbackState)"
            }
            Log.d("SPASIBA_111", "[EXOPLAYER_STATE] State=$stateName isPlaying=${exoPlayer.isPlaying} pos=${exoPlayer.currentPosition}ms bufferPos=${exoPlayer.bufferedPosition}ms dur=${exoPlayer.duration}ms")

            when (playbackState) {
                Player.STATE_BUFFERING -> _isLoading.value = true
                Player.STATE_READY -> {
                    _isLoading.value = false
                    val dur = exoPlayer.duration
                    if (dur != C.TIME_UNSET && dur > 0L) {
                        _durationMs.value = dur
                    }
                    Log.d("SPASIBA_111", "[EXOPLAYER_READY] Playback ready to render! Duration=${_durationMs.value}ms")
                }
                Player.STATE_ENDED -> {
                    Log.d("SPASIBA_111", "[EXOPLAYER_ENDED] Track reached end of stream. RepeatState=${_repeatState.value}")
                    _isPlaying.value = false
                    if (_repeatState.value == RepeatState.ONE) {
                        seekTo(0L)
                        exoPlayer.play()
                    } else {
                        val sessionManager = app.spasiba.nativeapp2.data.engine.EunoiaSessionManager.getInstance(context)
                        val isEunoiaConnected = sessionManager.sessionStatus.value == app.spasiba.nativeapp2.data.engine.EunoiaSessionStatus.CONNECTED
                        if (!isEunoiaConnected || sessionManager.isLeader.value) {
                            playNext()
                        }
                    }
                }
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d("SPASIBA_111", "[EXOPLAYER_PLAYING_CHANGE] isPlaying=$isPlaying currentPos=${exoPlayer.currentPosition}ms")
            _isPlaying.value = isPlaying
            eunoiaPlaybackActionListener?.invoke(if (isPlaying) "play" else "pause", org.json.JSONObject().apply {
                put("positionMs", exoPlayer.currentPosition.coerceAtLeast(0L))
                put("isPlaying", isPlaying)
            })
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            Log.d("SPASIBA_111", "[EXOPLAYER_VIDEO_SIZE] Width=${videoSize.width} Height=${videoSize.height}")
            if (videoSize.width > 0 && videoSize.height > 0) {
                val calculatedRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                val safeRatio = calculatedRatio.coerceIn(0.55f, 2.2f)
                _videoAspectRatio.value = safeRatio
                Log.d("SPASIBA_111", "[EXOPLAYER_VIDEO_ASPECT] AspectRatio=$safeRatio (calculated from ${videoSize.width}x${videoSize.height})")
            }
        }

        override fun onRenderedFirstFrame() {
            val latency = if (lastUserClickTimestampMs > 0) (System.currentTimeMillis() - lastUserClickTimestampMs) else 0L
            Log.i("SPASIBA_111", "🎬 ╔══════════════════════════════════════════════════════════════════════")
            Log.i("SPASIBA_111", "🎬 ║ 🖼️ [FIRST_VIDEO_FRAME_ON_SCREEN]")
            Log.i("SPASIBA_111", "🎬 ║ ⚡ Latency from Click to First Screen Pixel: ${latency} ms")
            Log.i("SPASIBA_111", "🎬 ╚══════════════════════════════════════════════════════════════════════")
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("SPASIBA_111", "[EXOPLAYER_ERROR] ErrorCode=${error.errorCode} Message='${error.message}' Cause=${error.cause?.message}", error)
            val current = _currentSong.value ?: return
            val currentPos = _positionMs.value.coerceAtLeast(0L)
            
            val isNetworkOrHttpError = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                    || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                    || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                    || error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
                    || error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                    || error.message?.contains("403") == true
                    || error.message?.contains("404") == true
                    || error.message?.contains("410") == true
                    || error.message?.contains("Response code") == true
                    || error.message?.contains("Source error") == true
                    || error.message?.contains("BehindLiveWindowException") == true

            if (isNetworkOrHttpError) {
                val attempts = retryAttempts[current.id] ?: 0
                if (attempts >= 2) {
                    Log.w("SPASIBA_111", "[EXOPLAYER_RECOVERY_EXHAUSTED] Retry attempts exhausted for '${current.title}' (${attempts}/2)")
                    _isLoading.value = false
                    _isPlaying.value = false
                    Toast.makeText(context, "Playback error: stream unavailable for ${current.title}", Toast.LENGTH_SHORT).show()
                    return
                }
                retryAttempts[current.id] = attempts + 1
                Log.d("SPASIBA_111", "[EXOPLAYER_RECOVERY_START] Initiating auto-recovery retry ${attempts + 1}/2 for '${current.title}' at ${currentPos}ms")
                preResolvedStreamMap.remove(current.id)
                _isLoading.value = true

                scope.launch(Dispatchers.IO) {
                    if (isVideoModeActive) {
                        val videoResult = YouTubeMusicApi.resolveVideoStreamResult(context, current.id, _currentVideoQuality.value)
                        withContext(Dispatchers.Main) {
                            if (videoResult != null && videoResult.url.isNotBlank()) {
                                val mediaMetadata = MediaMetadata.Builder()
                                    .setTitle(current.title)
                                    .setArtist(current.author)
                                    .setArtworkUri(Uri.parse(current.thumbnail))
                                    .build()

                                val dataSourceFactory = createHttpDataSourceFactory(videoResult.url, videoResult.headers)
                                val mediaSourceFactory = DefaultMediaSourceFactory(context, DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true))
                                    .setDataSourceFactory(dataSourceFactory)

                                val videoMediaItem = MediaItem.Builder()
                                    .setUri(Uri.parse(videoResult.url))
                                    .setMediaId(current.id)
                                    .setMediaMetadata(mediaMetadata)
                                    .build()

                                val videoSource = mediaSourceFactory.createMediaSource(videoMediaItem)
                                val finalSource = if (!videoResult.audioUrl.isNullOrBlank()) {
                                    val audioMediaItem = MediaItem.Builder()
                                        .setUri(Uri.parse(videoResult.audioUrl))
                                        .setMediaId("${current.id}_audio")
                                        .build()
                                    val audioSource = mediaSourceFactory.createMediaSource(audioMediaItem)
                                    MergingMediaSource(videoSource, audioSource)
                                } else {
                                    videoSource
                                }

                                applyTrackSelectionQuality(_currentVideoQuality.value)
                                exoPlayer.setMediaSource(finalSource)
                                exoPlayer.prepare()
                                if (currentPos > 0L) exoPlayer.seekTo(currentPos)
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                                _isLoading.value = false
                                _isPlaying.value = true
                                Log.d("SPASIBA_111", "[EXOPLAYER_RECOVERY_SUCCESS] Video recovered and playing at ${currentPos}ms")
                            } else {
                                Log.e("SPASIBA_111", "[EXOPLAYER_RECOVERY_FAILED] Video recovery failed to resolve new URLs")
                                _isLoading.value = false
                                _isPlaying.value = false
                            }
                        }
                    } else {
                        val newResult = repository.resolveStreamResult(current.id, forceRefresh = true)
                        withContext(Dispatchers.Main) {
                            if (newResult != null && newResult.url.isNotBlank()) {
                                val targetUri = if (newResult.url.startsWith("/")) Uri.fromFile(File(newResult.url)) else Uri.parse(newResult.url)
                                val dataSourceFactory = createHttpDataSourceFactory(newResult.url, newResult.headers)
                                val mediaSourceFactory = DefaultMediaSourceFactory(context, DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true))
                                    .setDataSourceFactory(dataSourceFactory)

                                val mediaMetadata = MediaMetadata.Builder()
                                    .setTitle(current.title)
                                    .setArtist(current.author)
                                    .setArtworkUri(Uri.parse(current.thumbnail))
                                    .build()

                                val mediaItem = MediaItem.Builder()
                                    .setUri(targetUri)
                                    .setMediaId(current.id)
                                    .setMediaMetadata(mediaMetadata)
                                    .build()

                                val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
                                exoPlayer.setMediaSource(mediaSource)
                                exoPlayer.prepare()
                                if (currentPos > 0L) exoPlayer.seekTo(currentPos)
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                                _isLoading.value = false
                                _isPlaying.value = true
                                Log.d("SPASIBA_111", "[EXOPLAYER_RECOVERY_SUCCESS] Audio recovered and playing at ${currentPos}ms")
                            } else {
                                Log.e("SPASIBA_111", "[EXOPLAYER_RECOVERY_FAILED] Audio recovery failed to resolve new URLs")
                                _isLoading.value = false
                                _isPlaying.value = false
                            }
                        }
                    }
                }
            } else {
                Log.e("SPASIBA_111", "[EXOPLAYER_FATAL] Unrecoverable player error for '${current.title}'")
                _isLoading.value = false
                _isPlaying.value = false
            }
        }
    }
}
