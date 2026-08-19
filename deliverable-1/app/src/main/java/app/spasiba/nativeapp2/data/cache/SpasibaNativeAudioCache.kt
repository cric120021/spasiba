package app.spasiba.nativeapp2.data.cache

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLConnection
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

data class CachedAudio(
    val videoId: String,
    val file: File,
    val mimeType: String
)

data class StreamingAudio(
    val videoId: String,
    val url: String,
    val mimeType: String?,
    val headers: Map<String, String>,
    val durationSeconds: Int
)

class SpasibaNativeAudioCache(private val context: Context) {
    private val audioDir = File(context.cacheDir, CACHE_DIR_NAME)
    private val trackLocks = ConcurrentHashMap<String, Any>()

    init {
        prepareSessionCache()
    }

    fun prepareSessionCache() {
        if (sessionPrepared) {
            audioDir.mkdirs()
            return
        }

        synchronized(sessionLock) {
            if (!sessionPrepared) {
                audioDir.mkdirs()
                evictOldAudioCacheIfNecessary()
                sessionPrepared = true
                Log.d(LOG_TAG, "Initialized persistent 200MB LRU audio cache")
            }
        }
    }

    private fun evictOldAudioCacheIfNecessary() {
        val maxBytes = 200 * 1024 * 1024L // 200 MB LRU
        val files = audioDir.listFiles() ?: return
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes > maxBytes) {
            val sortedFiles = files.sortedBy { it.lastModified() }
            for (file in sortedFiles) {
                val size = file.length()
                if (file.delete()) {
                    totalBytes -= size
                    if (totalBytes <= maxBytes) break
                }
            }
        }
    }

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (!request.method.equals("GET", ignoreCase = true)) {
            return null
        }

        val uri = request.url ?: return null
        if (uri.host != SPASIBA_HOST) {
            return null
        }

        return when {
            uri.path == STREAM_ROUTE -> interceptStreamRoute(uri)
            uri.pathSegments.firstOrNull() == AUDIO_ROUTE_SEGMENT -> interceptAudioRoute(uri, request.requestHeaders)
            else -> null
        }
    }

    fun getOrDownload(rawVideoId: String): CachedAudio {
        prepareSessionCache()
        val videoId = normalizeVideoId(rawVideoId) ?: throw IllegalArgumentException("Invalid YouTube video id.")
        val cached = findCachedAudioFile(videoId)

        if (cached != null) {
            cached.setLastModified(System.currentTimeMillis())
            return CachedAudio(videoId, cached, inferMimeType(cached))
        }

        val lock = trackLocks.getOrPut(videoId) { Any() }
        synchronized(lock) {
            val existing = findCachedAudioFile(videoId)
            if (existing != null) {
                existing.setLastModified(System.currentTimeMillis())
                return CachedAudio(videoId, existing, inferMimeType(existing))
            }

            return downloadAudio(videoId)
        }
    }

    private val streamCacheMap = ConcurrentHashMap<String, Pair<StreamingAudio, Long>>()
    private val resolveLocks = ConcurrentHashMap<String, Any>()

    fun resolveStreamingAudio(rawVideoId: String): StreamingAudio {
        val videoId = normalizeVideoId(rawVideoId) ?: throw IllegalArgumentException("Invalid YouTube video id.")
        
        val cached = streamCacheMap[videoId]
        val now = SystemClock.elapsedRealtime()
        if (cached != null && (now - cached.second) < STREAM_CACHE_TTL_MS) {
            Log.d(LOG_TAG, "STREAM RESOLVE HIT IN-MEMORY CACHE VIDEO_ID=$videoId")
            return cached.first
        }

        val lock = resolveLocks.getOrPut(videoId) { Any() }
        synchronized(lock) {
            val recheck = streamCacheMap[videoId]
            if (recheck != null && (SystemClock.elapsedRealtime() - recheck.second) < STREAM_CACHE_TTL_MS) {
                Log.d(LOG_TAG, "STREAM RESOLVE HIT RECHECK CACHE VIDEO_ID=$videoId")
                return recheck.first
            }

            ensureYoutubeDlInitialized()
            val startedAtMs = SystemClock.elapsedRealtime()
            val request = baseYoutubeRequest(videoId).apply {
                addOption("-f", FORMAT_SELECTOR)
            }

        Log.d(LOG_TAG, "STREAM RESOLVE START VIDEO_ID=$videoId FORMAT=$FORMAT_SELECTOR")
        val info = try {
            YoutubeDL.getInstance().getInfo(request)
        } catch (error: Throwable) {
            Log.e(LOG_TAG, "STREAM RESOLVE FAILED VIDEO_ID=$videoId ELAPSED_SECONDS=${elapsedSecondsSince(startedAtMs)}", error)
            throw error
        }

        val selectedFormat = selectBestAudioFormat(info)
        val streamUrl = info.url?.takeIf { it.isNotBlank() }
            ?: selectedFormat?.url?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("yt-dlp did not return a playable best-audio URL.")
        val extension = selectedFormat?.ext?.takeIf { it.isNotBlank() } ?: info.ext
        val headers = selectedFormat?.httpHeaders?.takeIf { it.isNotEmpty() }
            ?: info.httpHeaders?.takeIf { it.isNotEmpty() }
            ?: emptyMap()
        val duration = info.duration.coerceAtLeast(0)
        val mimeType = inferMimeTypeForExtension(extension)

        val result = StreamingAudio(
            videoId = videoId,
            url = streamUrl,
            mimeType = mimeType,
            headers = headers,
            durationSeconds = duration
        )

        streamCacheMap[videoId] = Pair(result, SystemClock.elapsedRealtime())

        Log.d(
            LOG_TAG,
            "STREAM RESOLVE FINISHED VIDEO_ID=$videoId ELAPSED_SECONDS=${elapsedSecondsSince(startedAtMs)} " +
                "EXT=${extension.orEmpty()} MIME=${mimeType.orEmpty()} DURATION_SECONDS=$duration HEADERS=${headers.keys.joinToString(",")}"
        )
        return result
        }
    }

    fun prefetchStream(rawVideoId: String) {
        val videoId = normalizeVideoId(rawVideoId) ?: return
        val cached = streamCacheMap[videoId]
        val now = SystemClock.elapsedRealtime()
        if (cached != null && (now - cached.second) < STREAM_CACHE_TTL_MS) {
            return
        }

        Executors.newSingleThreadExecutor().execute {
            runCatching {
                resolveStreamingAudio(videoId)
            }
        }
    }

    private fun interceptStreamRoute(uri: Uri): WebResourceResponse {
        val rawTarget = uri.getQueryParameter("videoId") ?: uri.getQueryParameter("url").orEmpty()
        val videoId = normalizeVideoId(rawTarget)

        if (videoId.isNullOrBlank()) {
            return jsonResponse(400, """{"error":"A valid videoId or YouTube URL is required."}""")
        }

        return try {
            val stream = resolveStreamingAudio(videoId)
            val json = """
                {"streamUrl":"${jsonEscape(stream.url)}","mimeType":"${jsonEscape(stream.mimeType.orEmpty())}","expiresAt":null,"streamCandidates":[{"url":"${jsonEscape(stream.url)}","mimeType":"${jsonEscape(stream.mimeType.orEmpty())}","expiresAt":null}]}
            """.trimIndent()
            jsonResponse(200, json, mapOf("X-Spasiba-Native-Stream" to "ready"))
        } catch (error: Throwable) {
            Log.e(LOG_TAG, "native stream route failed for $videoId", error)
            jsonResponse(502, """{"error":"${jsonEscape(error.message ?: "Native audio stream resolve failed.")}"}""")
        }
    }

    private fun interceptAudioRoute(uri: Uri, headers: Map<String, String>): WebResourceResponse {
        val videoId = uri.pathSegments.getOrNull(1)?.let(::normalizeVideoId)

        if (videoId.isNullOrBlank()) {
            return jsonResponse(404, """{"error":"Cached audio not found."}""")
        }

        val file = findCachedAudioFile(videoId) ?: return jsonResponse(404, """{"error":"Cached audio not found."}""")
        val mimeType = inferMimeType(file)
        val length = file.length()
        val rangeHeader = headers.entries.firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value
        val range = parseRange(rangeHeader, length)
        val stream = FileInputStream(file)

        file.setLastModified(System.currentTimeMillis())

        return if (range == null) {
            WebResourceResponse(
                mimeType,
                null,
                200,
                "OK",
                mapOf(
                    "Accept-Ranges" to "bytes",
                    "Cache-Control" to "no-store",
                    "Content-Length" to length.toString()
                ),
                stream
            )
        } else {
            skipFully(stream, range.start)
            val contentLength = range.endInclusive - range.start + 1
            WebResourceResponse(
                mimeType,
                null,
                206,
                "Partial Content",
                mapOf(
                    "Accept-Ranges" to "bytes",
                    "Cache-Control" to "no-store",
                    "Content-Length" to contentLength.toString(),
                    "Content-Range" to "bytes ${range.start}-${range.endInclusive}/$length"
                ),
                LimitedInputStream(stream, contentLength)
            )
        }
    }

    private fun downloadAudio(videoId: String): CachedAudio {
        ensureYoutubeDlInitialized()
        deleteTrackFiles(videoId)
        val outputTemplate = File(audioDir, "$videoId.%(ext)s").absolutePath
        val request = baseYoutubeRequest(videoId).apply {
            addOption("--no-mtime")
            addOption("--newline")
            addOption("-f", FORMAT_SELECTOR)
            addOption("--max-filesize", MAX_AUDIO_FILE_SIZE)
            addOption("-o", outputTemplate)
        }

        val startedAtMs = SystemClock.elapsedRealtime()

        try {
            Log.d(LOG_TAG, "DOWNLOAD START VIDEO_ID=$videoId FORMAT=$FORMAT_SELECTOR")
            YoutubeDL.getInstance().execute(request, "spasiba-$videoId") { progress, _, line ->
                val elapsedSeconds = elapsedSecondsSince(startedAtMs)
                if (progress >= 0f) {
                    Log.d(LOG_TAG, "DOWNLOAD PROGRESS VIDEO_ID=$videoId PERCENT=${progress.toInt()} ELAPSED_SECONDS=$elapsedSeconds LINE=${line.orEmpty()}")
                } else if (!line.isNullOrBlank()) {
                    Log.d(LOG_TAG, "DOWNLOAD LOG VIDEO_ID=$videoId ELAPSED_SECONDS=$elapsedSeconds LINE=$line")
                }
            }
        } catch (error: Throwable) {
            Log.e(LOG_TAG, "DOWNLOAD FAILED VIDEO_ID=$videoId ELAPSED_SECONDS=${elapsedSecondsSince(startedAtMs)}", error)
            deleteTrackFiles(videoId)
            throw error
        }

        val file = findCachedAudioFile(videoId) ?: throw IllegalStateException("yt-dlp completed without a cached audio file.")
        if (file.length() > MAX_AUDIO_FILE_BYTES) {
            deleteTrackFiles(videoId)
            val error = IllegalStateException("yt-dlp selected an unexpectedly large media file instead of audio-only.")
            Log.e(LOG_TAG, "DOWNLOAD REJECTED VIDEO_ID=$videoId ELAPSED_SECONDS=${elapsedSecondsSince(startedAtMs)} BYTES=${file.length()} FILE=${file.name}", error)
            throw error
        }

        file.setLastModified(System.currentTimeMillis())
        pruneCache()
        Log.d(LOG_TAG, "DOWNLOAD FINISHED VIDEO_ID=$videoId ELAPSED_SECONDS=${elapsedSecondsSince(startedAtMs)} BYTES=${file.length()} FILE=${file.name} MIME=${inferMimeType(file)}")
        return CachedAudio(videoId, file, inferMimeType(file))
    }

    private fun baseYoutubeRequest(videoId: String): YoutubeDLRequest =
        YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId").apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--socket-timeout", "20")
            addOption("--retries", "2")
            addOption("--fragment-retries", "2")
            addOption("--extractor-retries", "2")
            addOption("--extractor-args", "youtube:player_client=android_vr")
        }

    private fun selectBestAudioFormat(info: VideoInfo): VideoFormat? {
        val requested = info.requestedFormats.orEmpty()
            .filter { it.hasPlayableAudioUrl() }
        if (requested.isNotEmpty()) {
            return requested.maxByOrNull { it.audioScore() }
        }

        return info.formats.orEmpty()
            .filter { it.hasPlayableAudioUrl() }
            .maxByOrNull { it.audioScore() }
    }

    private fun VideoFormat.hasPlayableAudioUrl(): Boolean =
        !url.isNullOrBlank() && !acodec.equals("none", ignoreCase = true)

    private fun VideoFormat.audioScore(): Int =
        maxOf(abr, tbr, 0)

    private fun elapsedSecondsSince(startedAtMs: Long): String =
        String.format(Locale.US, "%.2f", (SystemClock.elapsedRealtime() - startedAtMs) / 1000.0)

    private fun ensureYoutubeDlInitialized() {
        if (ytDlpInitialized) {
            return
        }

        synchronized(sessionLock) {
            if (!ytDlpInitialized) {
                YoutubeDL.getInstance().init(context.applicationContext)
                ytDlpInitialized = true
                Log.d(LOG_TAG, "yt-dlp initialized")
            }
        }
    }

    private fun updateYoutubeDlIfNeeded() {
        if (ytDlpUpdateAttempted) {
            return
        }

        ytDlpUpdateAttempted = true
        runCatching {
            val status = YoutubeDL.getInstance().updateYoutubeDL(
                context.applicationContext,
                YoutubeDL.UpdateChannel.STABLE
            )
            Log.d(LOG_TAG, "yt-dlp update status=$status version=${YoutubeDL.getInstance().versionName(context.applicationContext)}")
        }.onFailure { error ->
            Log.w(LOG_TAG, "yt-dlp update skipped/failed; using bundled extractor", error)
        }
    }

    private fun findCachedAudioFile(videoId: String): File? =
        audioDir
            .listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith("$videoId.") &&
                    !file.name.endsWith(".part") &&
                    !file.name.endsWith(".ytdl")
            }
            ?.maxByOrNull { it.lastModified() }

    private fun deleteTrackFiles(videoId: String) {
        audioDir.listFiles()
            ?.filter { it.name.startsWith("$videoId.") }
            ?.forEach { file -> runCatching { file.delete() } }
    }

    private fun pruneCache() {
        val files = audioDir
            .listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        files.drop(MAX_CACHED_TRACKS).forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun buildNativeAudioUrl(cached: CachedAudio): String {
        val encodedId = Uri.encode(cached.videoId)
        val encodedName = Uri.encode(cached.file.name)
        return "https://$SPASIBA_HOST/$AUDIO_ROUTE_SEGMENT/$encodedId/$encodedName"
    }

    private fun jsonResponse(statusCode: Int, body: String, extraHeaders: Map<String, String> = emptyMap()): WebResourceResponse {
        val headers = mutableMapOf(
            "Cache-Control" to "no-store",
            "Content-Type" to "application/json; charset=utf-8",
            "Access-Control-Allow-Origin" to "https://$SPASIBA_HOST"
        )
        headers.putAll(extraHeaders)
        return WebResourceResponse(
            "application/json",
            "UTF-8",
            statusCode,
            if (statusCode < 400) "OK" else "Native Audio Error",
            headers,
            ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        )
    }

    private data class ByteRange(val start: Long, val endInclusive: Long)

    private fun parseRange(rangeHeader: String?, fileLength: Long): ByteRange? {
        if (rangeHeader.isNullOrBlank() || !rangeHeader.startsWith("bytes=") || fileLength <= 0L) {
            return null
        }

        val parts = rangeHeader.removePrefix("bytes=").split("-", limit = 2)
        if (parts.size != 2) {
            return null
        }

        val start = parts[0].toLongOrNull()
        val end = parts[1].toLongOrNull()

        return when {
            start != null -> {
                val safeStart = start.coerceIn(0L, fileLength - 1)
                val safeEnd = (end ?: fileLength - 1).coerceIn(safeStart, fileLength - 1)
                ByteRange(safeStart, safeEnd)
            }
            end != null -> {
                val suffixLength = end.coerceIn(1L, fileLength)
                ByteRange(fileLength - suffixLength, fileLength - 1)
            }
            else -> null
        }
    }

    private fun skipFully(stream: InputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) {
                break
            }
            remaining -= skipped
        }
    }

    private class LimitedInputStream(
        private val delegate: InputStream,
        private var remaining: Long
    ) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0L) {
                return -1
            }
            val value = delegate.read()
            if (value >= 0) {
                remaining -= 1
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0L) {
                return -1
            }
            val maxLength = minOf(length.toLong(), remaining).toInt()
            val count = delegate.read(buffer, offset, maxLength)
            if (count > 0) {
                remaining -= count
            }
            return count
        }

        override fun close() {
            delegate.close()
        }
    }

    companion object {
        private const val SPASIBA_HOST = "spasiba.vercel.app"
        private const val STREAM_ROUTE = "/api/stream"
        private const val AUDIO_ROUTE_SEGMENT = "__spasiba_native_audio"
        private const val CACHE_DIR_NAME = "spasiba-native-audio"
        private const val MAX_CACHED_TRACKS = 20
        private const val MAX_AUDIO_FILE_SIZE = "30M"
        private const val MAX_AUDIO_FILE_BYTES = 30L * 1024L * 1024L
        private const val STREAM_CACHE_TTL_MS = 3 * 3600 * 1000L
        private const val LOG_TAG = "SpasibaNativeAudio"
        private const val FORMAT_SELECTOR = "bestaudio"
        private val VIDEO_ID_PATTERN = Regex("^[A-Za-z0-9_-]{6,32}$")
        private val sessionLock = Any()

        @Volatile
        private var sessionPrepared = false

        @Volatile
        private var ytDlpInitialized = false

        @Volatile
        private var ytDlpUpdateAttempted = false

        fun normalizeVideoId(rawValue: String?): String? {
            val value = rawValue?.trim().orEmpty()
            if (value.isBlank()) {
                return null
            }

            if (VIDEO_ID_PATTERN.matches(value)) {
                return value
            }

            return runCatching {
                val uri = Uri.parse(value)
                val queryId = uri.getQueryParameter("v")
                when {
                    !queryId.isNullOrBlank() && VIDEO_ID_PATTERN.matches(queryId) -> queryId
                    uri.host?.contains("youtu.be") == true -> uri.pathSegments.firstOrNull()?.takeIf { VIDEO_ID_PATTERN.matches(it) }
                    uri.pathSegments.size >= 2 && uri.pathSegments[0] in setOf("embed", "shorts", "live") ->
                        uri.pathSegments[1].takeIf { VIDEO_ID_PATTERN.matches(it) }
                    else -> null
                }
            }.getOrNull()
        }

        fun inferMimeType(file: File): String {
            val name = file.name.lowercase(Locale.US)
            val extension = name.substringAfterLast('.', missingDelimiterValue = "")
            return inferMimeTypeForExtension(extension)
                ?: URLConnection.guessContentTypeFromName(file.name)
                ?: "application/octet-stream"
        }

        fun inferMimeTypeForExtension(extension: String?): String? {
            val ext = extension
                ?.trim()
                ?.removePrefix(".")
                ?.lowercase(Locale.US)

            return when (ext) {
                "m4a", "mp4", "aac" -> "audio/mp4"
                "webm" -> "audio/webm"
                "mp3" -> "audio/mpeg"
                "ogg", "opus" -> "audio/ogg"
                else -> null
            }
        }

        fun jsonEscape(value: String): String =
            buildString(value.length + 16) {
                value.forEach { char ->
                    when (char) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\b' -> append("\\b")
                        '\u000C' -> append("\\f")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> {
                            if (char.code < 0x20) {
                                append("\\u")
                                append(char.code.toString(16).padStart(4, '0'))
                            } else {
                                append(char)
                            }
                        }
                    }
                }
            }
    }
}
