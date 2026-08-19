package app.spasiba.nativeapp2.data.cache

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class LocalCachedAudio(
    val videoId: String,
    val file: File
)

class SpasibaAudioCacheManager(private val context: Context) {
    private val audioDir = File(context.cacheDir, CACHE_DIR_NAME)
    private val trackLocks = ConcurrentHashMap<String, Any>()

    init {
        prepareCacheDir()
    }

    private fun prepareCacheDir() {
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }
    }

    private fun ensureYoutubeDlInitialized() {
        if (!ytDlpInitialized) {
            synchronized(sessionLock) {
                if (!ytDlpInitialized) {
                    runCatching {
                        YoutubeDL.getInstance().init(context.applicationContext)
                        Log.d(LOG_TAG, "YoutubeDL initialized successfully for local audio cache")
                    }.onFailure {
                        Log.e(LOG_TAG, "Failed to initialize YoutubeDL", it)
                    }
                    ytDlpInitialized = true
                }
            }
        }
    }

    fun getOrDownloadAudio(videoId: String): LocalCachedAudio {
        prepareCacheDir()
        val cachedFile = findCachedAudioFile(videoId)

        if (cachedFile != null && cachedFile.length() > 0) {
            cachedFile.setLastModified(System.currentTimeMillis())
            Log.d(LOG_TAG, "CACHE HIT for videoId=$videoId file=${cachedFile.name}")
            return LocalCachedAudio(videoId, cachedFile)
        }

        val lock = trackLocks.getOrPut(videoId) { Any() }
        synchronized(lock) {
            val recheck = findCachedAudioFile(videoId)
            if (recheck != null && recheck.length() > 0) {
                recheck.setLastModified(System.currentTimeMillis())
                return LocalCachedAudio(videoId, recheck)
            }

            return downloadAudioFile(videoId)
        }
    }

    private fun downloadAudioFile(videoId: String): LocalCachedAudio {
        ensureYoutubeDlInitialized()
        deleteTrackFiles(videoId)

        val outputTemplate = File(audioDir, "$videoId.%(ext)s").absolutePath
        val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId").apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--socket-timeout", "20")
            addOption("--retries", "3")
            addOption("--fragment-retries", "3")
            addOption("--extractor-retries", "3")
            addOption("--extractor-args", "youtube:player_client=android_vr")
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
                    Log.d(LOG_TAG, "DOWNLOAD PROGRESS VIDEO_ID=$videoId PERCENT=${progress.toInt()}% ELAPSED_SECONDS=$elapsedSeconds")
                } else if (!line.isNullOrBlank()) {
                    Log.d(LOG_TAG, "DOWNLOAD LOG VIDEO_ID=$videoId LINE=$line")
                }
            }
        } catch (error: Throwable) {
            Log.e(LOG_TAG, "DOWNLOAD FAILED VIDEO_ID=$videoId ELAPSED_SECONDS=${elapsedSecondsSince(startedAtMs)}", error)
            deleteTrackFiles(videoId)
            throw error
        }

        val file = findCachedAudioFile(videoId) ?: throw IllegalStateException("yt-dlp completed without a cached audio file for $videoId.")
        file.setLastModified(System.currentTimeMillis())
        pruneCache()
        Log.d(LOG_TAG, "DOWNLOAD FINISHED VIDEO_ID=$videoId BYTES=${file.length()} FILE=${file.name}")
        return LocalCachedAudio(videoId, file)
    }

    fun findCachedAudioFile(videoId: String): File? =
        audioDir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith("$videoId.") &&
                    !file.name.endsWith(".part") &&
                    !file.name.endsWith(".ytdl") &&
                    file.length() > 0
            }
            ?.maxByOrNull { it.lastModified() }

    private fun deleteTrackFiles(videoId: String) {
        audioDir.listFiles()
            ?.filter { it.name.startsWith("$videoId.") }
            ?.forEach { file -> runCatching { file.delete() } }
    }

    private fun pruneCache() {
        val files = audioDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        files.drop(MAX_CACHED_TRACKS).forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun elapsedSecondsSince(startedAtMs: Long): String =
        String.format(Locale.US, "%.2f", (SystemClock.elapsedRealtime() - startedAtMs) / 1000.0)

    companion object {
        private const val LOG_TAG = "SpasibaAudioCache"
        private const val CACHE_DIR_NAME = "spasiba-native-audio"
        private const val MAX_CACHED_TRACKS = 25
        private const val MAX_AUDIO_FILE_SIZE = "35M"
        private const val FORMAT_SELECTOR = "bestaudio"
        private val sessionLock = Any()

        @Volatile
        private var ytDlpInitialized = false
    }
}
