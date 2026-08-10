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
import java.io.File

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
    private val retryAttempts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private var prefetchedVideoId: String? = null

    private val baseHttpDataSourceFactory by lazy {
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
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
                500,   // Min buffer: 0.5s
                30000, // Max buffer: 30s
                50,    // Buffer for playback: 50ms
                200    // Buffer after rebuffer: 200ms
            )
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
                        ?: YouTubeMusicApi.resolveStreamResult(context, targetId)

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
        setQueue(listOf(clickedSong), clickedSong.id, sourceLabel)
        playSong(clickedSong)

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
        val clickedSong = songs[startIndex]
        setQueue(songs, clickedSong.id, sourceLabel)
        playSong(clickedSong)
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

    fun disableVideoMode() {
        isVideoModeActive = false
        val song = _currentSong.value ?: return
        playSong(song)
    }

    fun setVideoQuality(quality: String) {
        isVideoModeActive = true
        _currentVideoQuality.value = quality
        val song = _currentSong.value ?: return
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

        if (targetHeight == Int.MAX_VALUE) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearVideoSizeConstraints()
                .build()
        } else {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setMaxVideoSize(Int.MAX_VALUE, targetHeight)
                .setMinVideoSize(0, targetHeight)
                .build()
        }
        Log.d(TAG, "ExoPlayer trackSelectionParameters updated: targetHeight=$targetHeight for quality=$quality")
    }

    fun startProgressiveVideoDownloadAndPlay(song: Song) {
        scope.launch(Dispatchers.IO) {
            _isLoading.value = true

            var targetVideoId = song.id
            if (targetVideoId.length != 11 || targetVideoId.startsWith("track_") || targetVideoId.startsWith("item_") || targetVideoId.startsWith("VL") || targetVideoId.startsWith("MP") || targetVideoId.isBlank()) {
                runCatching {
                    Log.d(TAG, "Resolving actual 11-char videoId for video mode '${song.title}' by ${song.author} (original id='${song.id}')")
                    val searchResults = YouTubeMusicApi.searchSongs("${song.title} ${song.author}")
                    val validTrack = searchResults.firstOrNull { it.id.length == 11 }
                    if (validTrack != null) {
                        targetVideoId = validTrack.id
                        Log.d(TAG, "Resolved videoId=$targetVideoId for '${song.title}'")
                    }
                }
            }

            val fetchedQualities = YouTubeMusicApi.fetchAvailableVideoQualities(targetVideoId)
            _availableVideoQualities.value = fetchedQualities

            Log.d(TAG, "Resolving video stream for '${song.title}' (videoId=$targetVideoId quality=${_currentVideoQuality.value} available=$fetchedQualities)")
            val result = YouTubeMusicApi.resolveVideoStreamResult(context, targetVideoId, _currentVideoQuality.value)
            
            launch(Dispatchers.Main) {
                if (result != null && result.url.isNotBlank()) {
                    val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val mediaMetadata = MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.author)
                        .setArtworkUri(Uri.parse(song.thumbnail))
                        .build()

                    val headers = result.headers.toMutableMap().apply {
                        putIfAbsent("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    }
                    val dataSourceFactory = baseHttpDataSourceFactory
                        .setDefaultRequestProperties(headers)

                    val mediaSourceFactory = DefaultMediaSourceFactory(context, DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true))
                        .setDataSourceFactory(dataSourceFactory)

                    val videoMediaItem = MediaItem.Builder()
                        .setUri(Uri.parse(result.url))
                        .setMediaId(song.id)
                        .setMediaMetadata(mediaMetadata)
                        .build()

                    val videoSource = mediaSourceFactory.createMediaSource(videoMediaItem)

                    val finalSource = if (!result.audioUrl.isNullOrBlank()) {
                        val audioMediaItem = MediaItem.Builder()
                            .setUri(Uri.parse(result.audioUrl))
                            .setMediaId("${song.id}_audio")
                            .build()
                        val audioSource = mediaSourceFactory.createMediaSource(audioMediaItem)
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
                    Log.d(TAG, "ExoPlayer video prepared and playing at pos=$currentPos with quality=${_currentVideoQuality.value}")
                } else {
                    Log.e(TAG, "Failed to resolve video stream result for videoId=$targetVideoId")
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

    fun playSong(song: Song, initialPosMs: Long = 0L) {
        Log.d(TAG, "playSong requested for title='${song.title}' id='${song.id}' initialPosMs=$initialPosMs")
        _isLoading.value = true
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

        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        if (isVideoModeActive) {
            Log.d(TAG, "playSong called in VIDEO MODE -> Loading 720p video stream for '${song.title}'")
            startProgressiveVideoDownloadAndPlay(song)
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                repository.recordPlayedSong(song)

                var targetVideoId = song.id
                if (targetVideoId.length != 11 || targetVideoId.startsWith("track_") || targetVideoId.startsWith("item_") || targetVideoId.startsWith("VL") || targetVideoId.startsWith("MP") || targetVideoId.isBlank()) {
                    runCatching {
                        Log.d(TAG, "Resolving actual 11-char videoId for '${song.title}' by ${song.author} (original id='${song.id}')")
                        val searchResults = YouTubeMusicApi.searchSongs("${song.title} ${song.author}")
                        val validTrack = searchResults.firstOrNull { it.id.length == 11 }
                        if (validTrack != null) {
                            targetVideoId = validTrack.id
                            Log.d(TAG, "Resolved videoId=$targetVideoId for '${song.title}'")
                        }
                    }
                }

                // Check if track is cached or downloaded locally first via DownloadManager and AudioCacheManager
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
                    Log.d(TAG, "Playing local downloaded audio file: ${localFile.absolutePath}")
                    launch(Dispatchers.Main) {
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

                // Check if stream URL was already pre-warmed for instant playback (with TTL validation)
                var result = getValidCachedStream(targetVideoId)
                    ?: repository.resolveStreamResult(targetVideoId)
                    ?: YouTubeMusicApi.resolveStreamResult(context, targetVideoId)

                if (result == null || result.url.isBlank()) {
                    Log.d(TAG, "Direct stream failed for ${song.title} id=$targetVideoId, searching alternate audio track...")
                    runCatching {
                        val fallbackSearch = YouTubeMusicApi.searchSongs("${song.title} ${song.author} audio")
                        val altId = fallbackSearch.firstOrNull { it.id != targetVideoId && it.id.length == 11 }?.id
                        if (altId != null) {
                            result = YouTubeMusicApi.resolveStreamResult(context, altId)
                        }
                    }
                }

                if (result != null && result.url.isNotBlank()) {
                    preResolvedStreamMap[targetVideoId] = CachedStream(result!!, System.currentTimeMillis())
                }

            launch(Dispatchers.Main) {
                if (result == null || result.url.isBlank()) {
                    Log.e(TAG, "Stream URL resolution failed for ${song.title}")
                    _isLoading.value = false
                    android.widget.Toast.makeText(context, "Playback error: Stream unavailable for ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                    playQueueOffset(1)
                    return@launch
                }

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
                    val headers = result.headers.toMutableMap().apply {
                        putIfAbsent("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    }
                    val dataSourceFactory = baseHttpDataSourceFactory
                        .setDefaultRequestProperties(headers)

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
                Log.d(TAG, "ExoPlayer prepared and starting for '${song.title}' URI=$targetUri initialPosMs=$initialPosMs")

                maybePrefetchNextTrack()
            }
        } catch (e: Exception) {
            Log.e(TAG, "playSong stream resolution failed for '${song.title}': ${e.message}", e)
            launch(Dispatchers.Main) {
                _isLoading.value = false
                _isPlaying.value = false
            }
        }
    }
}

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            _isPlaying.value = false
            eunoiaPlaybackActionListener?.invoke("pause", org.json.JSONObject().apply {
                put("positionMs", exoPlayer.currentPosition.coerceAtLeast(0L))
                put("isPlaying", false)
            })
        } else {
            val song = _currentSong.value
            if (song != null) {
                if (exoPlayer.playbackState == ExoPlayer.STATE_IDLE || exoPlayer.playbackState == ExoPlayer.STATE_ENDED) {
                    playSong(song)
                } else {
                    exoPlayer.play()
                    _isPlaying.value = true
                }
            } else if (_queue.value.isNotEmpty()) {
                playSong(_queue.value.first())
            } else {
                val fallbacks = YouTubeMusicApi.getFallbackTracks()
                if (fallbacks.isNotEmpty()) {
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
        playQueueOffset(1)
    }

    fun playPrevious() {
        if (_positionMs.value > RESTART_PREVIOUS_AFTER_MS) {
            seekTo(0L)
        } else {
            playQueueOffset(-1)
        }
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
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

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                if (exoPlayer.isPlaying) {
                    _positionMs.value = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = exoPlayer.duration
                    if (dur != C.TIME_UNSET && dur > 0L) {
                        _durationMs.value = dur
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
            Log.d("SPASIBA_VIDEO_DEBUG", "ExoPlayer onPlaybackStateChanged: $stateName (isPlaying=${exoPlayer.isPlaying})")

            when (playbackState) {
                Player.STATE_BUFFERING -> _isLoading.value = true
                Player.STATE_READY -> {
                    _isLoading.value = false
                    val dur = exoPlayer.duration
                    if (dur != C.TIME_UNSET && dur > 0L) {
                        _durationMs.value = dur
                    }
                }
                Player.STATE_ENDED -> {
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
            Log.d("SPASIBA_VIDEO_DEBUG", "ExoPlayer onIsPlayingChanged: isPlaying=$isPlaying")
            _isPlaying.value = isPlaying
            eunoiaPlaybackActionListener?.invoke(if (isPlaying) "play" else "pause", org.json.JSONObject().apply {
                put("positionMs", exoPlayer.currentPosition.coerceAtLeast(0L))
                put("isPlaying", isPlaying)
            })
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            Log.d("SPASIBA_VIDEO_DEBUG", "ExoPlayer onVideoSizeChanged: width=${videoSize.width} height=${videoSize.height} unappliedRotationDegrees=${videoSize.unappliedRotationDegrees}")
            if (videoSize.width > 0 && videoSize.height > 0) {
                val calculatedRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                val safeRatio = calculatedRatio.coerceIn(0.55f, 2.2f)
                _videoAspectRatio.value = safeRatio
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer playback error: ${error.message}", error)
            val current = _currentSong.value ?: return
            val currentPos = _positionMs.value.coerceAtLeast(0L)
            
            val isNetworkOrHttpError = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                    || error.message?.contains("403") == true
                    || error.message?.contains("404") == true

            if (isNetworkOrHttpError) {
                Log.d(TAG, "HTTP 403/404 Stream Expiration detected for ${current.title}. Attempting silent auto-recovery...")
                _isLoading.value = true
                scope.launch(Dispatchers.IO) {
                    val freshResult = YouTubeMusicApi.resolveStreamResult(context, current.id)
                    withContext(Dispatchers.Main) {
                        if (freshResult != null && freshResult.url.isNotBlank()) {
                            val updatedSong = current.copy(streamUrl = freshResult.url)
                            _currentSong.value = updatedSong
                            val mediaItem = MediaItem.Builder()
                                .setUri(Uri.parse(freshResult.url))
                                .setMediaId(updatedSong.id)
                                .build()
                            exoPlayer.setMediaItem(mediaItem, currentPos)
                            exoPlayer.prepare()
                            exoPlayer.play()
                            _isLoading.value = false
                            Log.d(TAG, "Silent Auto-Recovery SUCCESS for ${current.title} at ${currentPos}ms!")
                        } else {
                            _isLoading.value = false
                            playNext()
                        }
                    }
                }
            } else {
                _isLoading.value = false
            }
        }

        override fun onRenderedFirstFrame() {
            Log.d("SPASIBA_VIDEO_DEBUG", "ExoPlayer onRenderedFirstFrame SUCCESS! Video frame rendered to surface!")
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e("SPASIBA_VIDEO_DEBUG", "ExoPlayer ERROR: errorCode=${error.errorCodeName} message=${error.message}", error)
            val song = _currentSong.value
            if (song != null && (retryAttempts[song.id] ?: 0) < 1) {
                retryAttempts[song.id] = 1
                Log.w(TAG, "Playback failure detected on '${song.title}' (errorCode=${error.errorCodeName}). Auto-evicting cache & retrying with fresh URL...")
                preResolvedStreamMap.remove(song.id)
                _isLoading.value = true
                scope.launch(Dispatchers.IO) {
                    val freshResult = YouTubeMusicApi.resolveStreamResult(context, song.id)
                    if (freshResult != null && freshResult.url.isNotBlank()) {
                        preResolvedStreamMap[song.id] = CachedStream(freshResult, System.currentTimeMillis())
                        launch(Dispatchers.Main) {
                            playSong(song, exoPlayer.currentPosition.coerceAtLeast(0L))
                        }
                    } else {
                        launch(Dispatchers.Main) {
                            _isLoading.value = false
                            _isPlaying.value = false
                        }
                    }
                }
                return
            }
            _isLoading.value = false
            _isPlaying.value = false
        }
    }
}
