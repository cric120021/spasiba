package app.spasiba.nativeapp2.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import app.spasiba.nativeapp2.data.model.Song
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import okhttp3.Request

enum class DownloadType { AUDIO_ONLY, VIDEO_AND_AUDIO }
enum class DownloadStatus { DOWNLOADING, PAUSED, COMPLETED, CANCELLED, FAILED }

data class DownloadItem(
    val song: Song,
    val type: DownloadType,
    val quality: String = "720p",
    val progress: Float = 0f,
    val status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val localFilePath: String? = null,
    val startedAt: Long = System.currentTimeMillis()
)

class SpasibaDownloadManager(private val context: Context) {
    private val downloadsDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "spasiba_downloads")
    private val manifestFile = File(context.filesDir, "downloads_manifest.json")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _downloads = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadItem>> = _downloads.asStateFlow()

    private val processMap = ConcurrentHashMap<String, String>()

    init {
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        loadManifest()
    }

    private fun ensureYoutubeDlInitialized() {
        runCatching {
            YoutubeDL.getInstance().init(context.applicationContext)
            com.yausername.ffmpeg.FFmpeg.getInstance().init(context.applicationContext)
        }
    }

    fun startDownload(song: Song, type: DownloadType, quality: String = "720p") {
        val existing = _downloads.value[song.id]
        if (existing?.status == DownloadStatus.COMPLETED) {
            return
        }

        val newItem = DownloadItem(
            song = song,
            type = type,
            quality = quality,
            progress = 0f,
            status = DownloadStatus.DOWNLOADING
        )

        updateItem(newItem)

        val ext = if (type == DownloadType.AUDIO_ONLY) "m4a" else "mp4"
        val targetFile = File(downloadsDir, "${song.id}_${type.name.lowercase()}.$ext")

        if (type == DownloadType.VIDEO_AND_AUDIO) {
            downloadAndMergeHighQualityVideo(song, quality, targetFile, newItem)
            return
        }

        scope.launch {
            try {
                ensureYoutubeDlInitialized()

                val processId = "download_${song.id}"
                processMap[song.id] = processId

                val formatSpec = "m4a/bestaudio/best"

                val request = YoutubeDLRequest("https://www.youtube.com/watch?v=${song.id}").apply {
                    addOption("-4")
                    addOption("--no-playlist")
                    addOption("--no-warnings")
                    addOption("--socket-timeout", "30")
                    addOption("--extractor-args", "youtube:player_client=android,web")
                    addOption("-f", formatSpec)
                    addOption("-o", targetFile.absolutePath)
                }

                YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
                    if (progress >= 0f) {
                        val current = _downloads.value[song.id]
                        if (current != null && current.status == DownloadStatus.DOWNLOADING) {
                            updateItem(current.copy(progress = progress.coerceIn(0f, 100f)))
                        }
                    }
                }

                if (targetFile.exists() && targetFile.length() > 0) {
                    updateItem(newItem.copy(
                        progress = 100f,
                        status = DownloadStatus.COMPLETED,
                        localFilePath = targetFile.absolutePath
                    ))
                } else {
                    downloadDirectFallback(song, type, quality, targetFile, newItem)
                }
            } catch (e: Exception) {
                Log.w("SpasibaDownloadManager", "yt-dlp download failed for ${song.id}, attempting direct stream download fallback...", e)
                downloadDirectFallback(song, type, quality, targetFile, newItem)
            } finally {
                processMap.remove(song.id)
            }
        }
    }

    private fun downloadAndMergeHighQualityVideo(
        song: Song,
        quality: String,
        targetFile: File,
        newItem: DownloadItem
    ) {
        scope.launch(Dispatchers.IO) {
            val processId = "download_${song.id}"
            processMap[song.id] = processId

            // Step 1: Primary - Native yt-dlp HD Video + Audio Extraction with FFmpeg auto-merging
            try {
                ensureYoutubeDlInitialized()

                val targetRes = when {
                    quality.contains("2160") || quality.contains("4K", ignoreCase = true) -> "2160"
                    quality.contains("1440") || quality.contains("2K", ignoreCase = true) -> "1440"
                    quality.contains("1080") -> "1080"
                    quality.contains("720") -> "720"
                    quality.contains("480") -> "480"
                    quality.contains("360") -> "360"
                    else -> "720"
                }

                // Force video + audio stream extraction and FFmpeg merge
                val formatSpec = "bestvideo[height<=$targetRes][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=$targetRes]+bestaudio/best[height<=$targetRes]/best"

                val request = YoutubeDLRequest("https://www.youtube.com/watch?v=${song.id}").apply {
                    addOption("-4")
                    addOption("--no-playlist")
                    addOption("--no-warnings")
                    addOption("--socket-timeout", "30")
                    addOption("--extractor-args", "youtube:player_client=android_vr,web")
                    addOption("-f", formatSpec)
                    addOption("-o", targetFile.absolutePath)
                }

                YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
                    if (progress >= 0f) {
                        val current = _downloads.value[song.id]
                        if (current != null && current.status == DownloadStatus.DOWNLOADING) {
                            updateItem(current.copy(progress = progress.coerceIn(0f, 100f)))
                        }
                    }
                }

                if (targetFile.exists() && targetFile.length() > 500_000L) {
                    updateItem(newItem.copy(
                        progress = 100f,
                        status = DownloadStatus.COMPLETED,
                        localFilePath = targetFile.absolutePath
                    ))
                    return@launch
                }
            } catch (e: Exception) {
                Log.w("SpasibaDownloadManager", "yt-dlp high quality video download attempt failed for ${song.id}, switching to dual-stream OkHttp + FFmpeg fallback...", e)
            }

            // Step 2: Fallback - Dual-Stream OkHttp Resolution & FFmpeg Muxing
            try {
                val videoRes = app.spasiba.nativeapp2.data.api.YouTubeMusicApi.resolveVideoStreamResult(context, song.id, quality)
                val audioRes = app.spasiba.nativeapp2.data.api.YouTubeMusicApi.resolveStreamResult(context, song.id)

                if (videoRes != null && videoRes.url.isNotBlank() && audioRes != null && audioRes.url.isNotBlank()) {
                    val videoTemp = File(downloadsDir, "${song.id}_video_temp.mp4")
                    val audioTemp = File(downloadsDir, "${song.id}_audio_temp.m4a")

                    // Download 1080p Video Track (0% -> 45%)
                    downloadFileWithProgress(song, videoRes.url, videoRes.headers, videoTemp, 0f, 45f)

                    // Download High-Quality Audio Track (45% -> 90%)
                    downloadFileWithProgress(song, audioRes.url, audioRes.headers, audioTemp, 45f, 90f)

                    // Merge Video + Audio using yt-dlp / FFmpeg command options
                    updateItem(newItem.copy(progress = 92f))
                    val mergeRequest = YoutubeDLRequest("").apply {
                        addOption("-i", videoTemp.absolutePath)
                        addOption("-i", audioTemp.absolutePath)
                        addOption("-c:v", "copy")
                        addOption("-c:a", "aac")
                        addOption("-o", targetFile.absolutePath)
                    }
                    runCatching {
                        YoutubeDL.getInstance().execute(mergeRequest, "merge_${song.id}")
                    }

                    videoTemp.delete()
                    audioTemp.delete()
                } else if (videoRes != null && videoRes.url.isNotBlank()) {
                    downloadFileWithProgress(song, videoRes.url, videoRes.headers, targetFile, 0f, 100f)
                }

                if (targetFile.exists() && targetFile.length() > 0) {
                    updateItem(newItem.copy(
                        progress = 100f,
                        status = DownloadStatus.COMPLETED,
                        localFilePath = targetFile.absolutePath
                    ))
                } else {
                    updateItem(newItem.copy(status = DownloadStatus.FAILED))
                }
            } catch (fallbackEx: Exception) {
                Log.e("SpasibaDownloadManager", "High quality video fallback failed for ${song.id}", fallbackEx)
                updateItem(newItem.copy(status = DownloadStatus.FAILED))
            } finally {
                processMap.remove(song.id)
            }
        }
    }

    private suspend fun downloadDirectFallback(
        song: Song,
        type: DownloadType,
        quality: String,
        targetFile: File,
        newItem: DownloadItem
    ) {
        try {
            val streamRes = if (type == DownloadType.AUDIO_ONLY) {
                app.spasiba.nativeapp2.data.api.YouTubeMusicApi.resolveStreamResult(context, song.id)
            } else {
                app.spasiba.nativeapp2.data.api.YouTubeMusicApi.resolveVideoStreamResult(context, song.id, quality)
            }
            if (streamRes != null && streamRes.url.isNotBlank()) {
                downloadDirectStream(song, streamRes.url, streamRes.headers, targetFile)
            } else {
                updateItem(newItem.copy(status = DownloadStatus.FAILED))
            }
        } catch (ex: Exception) {
            Log.e("SpasibaDownloadManager", "Direct stream download fallback failed for ${song.id}", ex)
            updateItem(newItem.copy(status = DownloadStatus.FAILED))
        }
    }

    private fun downloadFileWithProgress(
        song: Song,
        url: String,
        headers: Map<String, String>,
        targetFile: File,
        startPct: Float,
        endPct: Float
    ) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            val body = response.body ?: throw Exception("Empty body")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val fraction = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            val currentPct = startPct + fraction * (endPct - startPct)
                            val current = _downloads.value[song.id]
                            if (current != null && current.status == DownloadStatus.DOWNLOADING) {
                                updateItem(current.copy(progress = currentPct))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun downloadDirectStream(song: Song, url: String, headers: Map<String, String>, targetFile: File) {
        downloadFileWithProgress(song, url, headers, targetFile, 0f, 100f)

        if (targetFile.exists() && targetFile.length() > 0) {
            val current = _downloads.value[song.id]
            if (current != null) {
                updateItem(current.copy(
                    progress = 100f,
                    status = DownloadStatus.COMPLETED,
                    localFilePath = targetFile.absolutePath
                ))
            }
        } else {
            val current = _downloads.value[song.id]
            if (current != null) updateItem(current.copy(status = DownloadStatus.FAILED))
        }
    }

    fun pauseDownload(videoId: String) {
        val current = _downloads.value[videoId] ?: return
        if (current.status == DownloadStatus.DOWNLOADING) {
            val processId = processMap[videoId]
            if (processId != null) {
                runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
            }
            updateItem(current.copy(status = DownloadStatus.PAUSED))
        }
    }

    fun resumeDownload(videoId: String) {
        val current = _downloads.value[videoId] ?: return
        if (current.status == DownloadStatus.PAUSED || current.status == DownloadStatus.FAILED) {
            startDownload(current.song, current.type, current.quality)
        }
    }

    fun cancelDownload(videoId: String) {
        val current = _downloads.value[videoId]
        val processId = processMap[videoId]
        if (processId != null) {
            runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
        }
        deleteLocalFile(videoId)
        val updatedMap = _downloads.value.toMutableMap()
        updatedMap.remove(videoId)
        _downloads.value = updatedMap
        saveManifest()
    }

    fun deleteDownload(videoId: String) {
        cancelDownload(videoId)
    }

    fun getDownloadedSongs(): List<Song> {
        return _downloads.value.values
            .filter { it.status == DownloadStatus.COMPLETED && it.localFilePath != null && File(it.localFilePath).exists() }
            .map { it.song }
    }

    fun getDownloadedFile(videoId: String): File? {
        val item = _downloads.value[videoId]
        if (item?.status == DownloadStatus.COMPLETED && item.localFilePath != null) {
            val file = File(item.localFilePath)
            if (file.exists()) return file
        }
        return null
    }

    private fun deleteLocalFile(videoId: String) {
        downloadsDir.listFiles()
            ?.filter { it.name.startsWith(videoId) }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun updateItem(item: DownloadItem) {
        val updatedMap = _downloads.value.toMutableMap()
        updatedMap[item.song.id] = item
        _downloads.value = updatedMap
        saveManifest()
    }

    private fun saveManifest() {
        synchronized(this) {
            runCatching {
                val array = JSONArray()
                _downloads.value.values.forEach { item ->
                    val obj = JSONObject().apply {
                        put("id", item.song.id)
                        put("title", item.song.title)
                        put("author", item.song.author)
                        put("thumbnail", item.song.thumbnail)
                        put("durationSeconds", item.song.durationSeconds)
                        put("type", item.type.name)
                        put("quality", item.quality)
                        put("progress", item.progress.toDouble())
                        put("status", item.status.name)
                        put("localFilePath", item.localFilePath ?: "")
                        put("startedAt", item.startedAt)
                    }
                    array.put(obj)
                }
                manifestFile.writeText(array.toString())
            }
        }
    }

    private fun loadManifest() {
        if (!manifestFile.exists()) return
        synchronized(this) {
            runCatching {
                val text = manifestFile.readText()
                val array = JSONArray(text)
                val map = mutableMapOf<String, DownloadItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val song = Song(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        author = obj.getString("author"),
                        thumbnail = obj.getString("thumbnail"),
                        durationSeconds = obj.optInt("durationSeconds", 0)
                    )
                    val type = runCatching { DownloadType.valueOf(obj.getString("type")) }.getOrDefault(DownloadType.AUDIO_ONLY)
                    val status = runCatching { DownloadStatus.valueOf(obj.getString("status")) }.getOrDefault(DownloadStatus.FAILED)
                    val path = obj.optString("localFilePath", "")
                    val item = DownloadItem(
                        song = song,
                        type = type,
                        quality = obj.optString("quality", "720p"),
                        progress = obj.optDouble("progress", 0.0).toFloat(),
                        status = if (status == DownloadStatus.DOWNLOADING) DownloadStatus.PAUSED else status,
                        localFilePath = if (path.isNotBlank()) path else null,
                        startedAt = obj.optLong("startedAt", System.currentTimeMillis())
                    )
                    map[song.id] = item
                }
                _downloads.value = map
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SpasibaDownloadManager? = null

        fun getInstance(context: Context): SpasibaDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpasibaDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun isNetworkAvailable(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
