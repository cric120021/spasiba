package app.spasiba.nativeapp2.data.cache

import android.content.Context
import android.content.SharedPreferences
import app.spasiba.nativeapp2.data.model.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class QueueSnapshot(
    val songs: List<Song>,
    val currentIndex: Int,
    val timestamp: Long = System.currentTimeMillis()
)

class QueueSnapshotCache(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("spasiba_queue_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_QUEUE = "key_queue_snapshot"
        private const val THREE_DAYS_MS = 3 * 24 * 60 * 60 * 1000L
    }

    fun saveSnapshot(songs: List<Song>, currentIndex: Int) {
        if (songs.isEmpty()) return
        val sanitizedSongs = songs.take(80).map { it.copy(streamUrl = null) }
        val snapshot = QueueSnapshot(sanitizedSongs, currentIndex)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                val json = gson.toJson(snapshot)
                prefs.edit().putString(KEY_QUEUE, json).apply()
            }
        }
    }

    fun restoreSnapshot(): QueueSnapshot? {
        val json = prefs.getString(KEY_QUEUE, null) ?: return null
        return try {
            val type = object : TypeToken<QueueSnapshot>() {}.type
            val snapshot: QueueSnapshot = gson.fromJson(json, type)
            if (System.currentTimeMillis() - snapshot.timestamp > THREE_DAYS_MS) {
                null
            } else {
                val sanitizedSongs = snapshot.songs.map { it.copy(streamUrl = null) }
                snapshot.copy(songs = sanitizedSongs)
            }
        } catch (e: Exception) {
            null
        }
    }
}
