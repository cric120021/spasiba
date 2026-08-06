package app.spasiba.nativeapp2.data.cache

import android.content.Context
import android.content.SharedPreferences
import app.spasiba.nativeapp2.data.model.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class RecentArtistItem(
    val artistName: String,
    val avatarUrl: String?,
    val subtitle: String = "Artist"
)

class SearchHistoryManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("spasiba_search_history_v2", Context.MODE_PRIVATE)
    private val gson = Gson()

    // 1. Text-Only Query History (Queries typed and searched without direct play)
    fun getTextQueries(): List<String> {
        val json = prefs.getString("text_queries", null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type)
        }.getOrDefault(emptyList())
    }

    fun addTextQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = getTextQueries().toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val updated = current.take(15)
        prefs.edit().putString("text_queries", gson.toJson(updated)).apply()
    }

    fun deleteTextQuery(query: String) {
        val current = getTextQueries().toMutableList()
        current.removeAll { it.equals(query, ignoreCase = true) }
        prefs.edit().putString("text_queries", gson.toJson(current)).apply()
    }

    // 2. Played Song History (Horizontal Card Rail)
    fun getPlayedSongs(): List<Song> {
        val json = prefs.getString("played_songs", null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<Song>>() {}.type
            gson.fromJson<List<Song>>(json, type)
        }.getOrDefault(emptyList())
    }

    fun addPlayedSong(song: Song) {
        val current = getPlayedSongs().toMutableList()
        current.removeAll { it.id == song.id }
        current.add(0, song)
        val updated = current.take(20)
        prefs.edit().putString("played_songs", gson.toJson(updated)).apply()

        // Remove song title from text queries so played items live ONLY in rail
        val queries = getTextQueries().toMutableList()
        queries.removeAll { it.equals(song.title, ignoreCase = true) }
        prefs.edit().putString("text_queries", gson.toJson(queries)).apply()
    }

    fun deletePlayedSong(songId: String) {
        val current = getPlayedSongs().toMutableList()
        current.removeAll { it.id == songId }
        prefs.edit().putString("played_songs", gson.toJson(current)).apply()
    }

    fun clearPlayedSongs() {
        prefs.edit().remove("played_songs").apply()
    }

    // 3. Played/Visited Artists History (Recent Artists List)
    fun getRecentArtists(): List<RecentArtistItem> {
        val json = prefs.getString("recent_artists", null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<RecentArtistItem>>() {}.type
            gson.fromJson<List<RecentArtistItem>>(json, type)
        }.getOrDefault(emptyList())
    }

    fun addRecentArtist(artistName: String, avatarUrl: String?, subtitle: String = "Artist") {
        val cleanName = artistName.trim()
        if (cleanName.isBlank()) return
        val current = getRecentArtists().toMutableList()
        val existing = current.find { it.artistName.equals(cleanName, ignoreCase = true) }
        val finalAvatar = avatarUrl.takeIf { !it.isNullOrBlank() } ?: existing?.avatarUrl
        current.removeAll { it.artistName.equals(cleanName, ignoreCase = true) }
        current.add(0, RecentArtistItem(cleanName, finalAvatar, subtitle))
        val updated = current.take(10)
        prefs.edit().putString("recent_artists", gson.toJson(updated)).apply()

        // Remove artist name from text queries so visited artists live in recent artist list
        val queries = getTextQueries().toMutableList()
        queries.removeAll { it.equals(cleanName, ignoreCase = true) }
        prefs.edit().putString("text_queries", gson.toJson(queries)).apply()
    }

    fun deleteRecentArtist(artistName: String) {
        val current = getRecentArtists().toMutableList()
        current.removeAll { it.artistName.equals(artistName, ignoreCase = true) }
        prefs.edit().putString("recent_artists", gson.toJson(current)).apply()
    }

    fun clearAllHistory() {
        prefs.edit().clear().apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: SearchHistoryManager? = null

        fun getInstance(context: Context): SearchHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SearchHistoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
