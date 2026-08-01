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

        scope.launch {
            try {
                ensureYoutubeDlInitialized()
                val ext = if (type == DownloadType.AUDIO_ONLY) "m4a" else "mp4"
                val targetFile = File(downloadsDir, "${song.id}_${type.name.lowercase()}.$ext")

                val processId = "download_${song.id}"
                processMap[song.id] = processId

                val formatSpec = if (type == DownloadType.AUDIO_ONLY) {
                    "bestaudio"
                } else {
                    val height = when (quality) {
                        "1080p" -> "1080"
                        "720p" -> "720"
                        "480p" -> "480"
                        "360p" -> "360"
                        else -> "720"
                    }
                    "bestvideo[height<=$height]+bestaudio/best[height<=$height]/best"
                }

                val request = YoutubeDLRequest("https://www.youtube.com/watch?v=${song.id}").apply {
                    addOption("--no-playlist")
                    addOption("--no-warnings")
                    addOption("--socket-timeout", "30")
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
                    updateItem(newItem.copy(status = DownloadStatus.FAILED))
                }
            } catch (e: Exception) {
                Log.e("SpasibaDownloadManager", "Download failed for ${song.id}", e)
                val current = _downloads.value[song.id]
                if (current?.status != DownloadStatus.CANCELLED) {
                    updateItem(newItem.copy(status = DownloadStatus.FAILED))
                }
            } finally {
                processMap.remove(song.id)
            }
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
        fun isNetworkAvailable(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
