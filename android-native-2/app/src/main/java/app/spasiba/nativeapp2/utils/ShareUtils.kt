package app.spasiba.nativeapp2.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import app.spasiba.nativeapp2.data.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SharedPlaylistData(
    val name: String,
    val songs: List<Song>,
    val shareCode: String
)

object ShareUtils {

    fun shareSong(context: Context, song: Song) {
        val encodedTitle = runCatching { URLEncoder.encode(song.title, StandardCharsets.UTF_8.name()) }.getOrDefault(song.title)
        val encodedAuthor = runCatching { URLEncoder.encode(song.author, StandardCharsets.UTF_8.name()) }.getOrDefault(song.author)
        val encodedThumb = runCatching { URLEncoder.encode(song.thumbnail, StandardCharsets.UTF_8.name()) }.getOrDefault(song.thumbnail)
        val songUrl = "https://spasiba.vercel.app/track/${song.id}?title=$encodedTitle&author=$encodedAuthor&art=$encodedThumb&dur=${song.durationSeconds}"
        val shareText = "🎵 Listening to '${song.title}' by ${song.author} on Spasiba Music:\n$songUrl"

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Share Track via")
        context.startActivity(shareIntent)
    }

    fun sharePlaylist(context: Context, playlistName: String, songs: List<Song>, shareCode: String? = null) {
        val code = shareCode ?: "SPASIBA-${playlistName.take(3).uppercase()}-${songs.size}"
        
        // Encode track payload as compact JSON Base64 string
        val jsonArray = JSONArray()
        songs.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("t", s.title)
            obj.put("a", s.author)
            obj.put("d", s.durationSeconds)
            obj.put("m", s.thumbnail)
            jsonArray.put(obj)
        }

        val rootObj = JSONObject()
        rootObj.put("name", playlistName)
        rootObj.put("code", code)
        rootObj.put("tracks", jsonArray)

        val encodedPayload = Base64.encodeToString(
            rootObj.toString().toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE
        )

        val deepLinkUrl = "spasiba://playlist?code=$code&payload=$encodedPayload"
        
        val trackPreview = songs.take(3).joinToString("\n") { "• ${it.title} - ${it.author}" }
        val shareText = """
            🎶 Check out my playlist '$playlistName' (${songs.size} tracks) on Spasiba Music!
            
            Tracks Preview:
            $trackPreview
            ${if (songs.size > 3) "... and ${songs.size - 3} more" else ""}
            
            Import Code: $code
            Deep Link: $deepLinkUrl
        """.trimIndent()

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Share Playlist via")
        context.startActivity(shareIntent)
    }

    fun parsePlaylistShareCode(input: String): SharedPlaylistData? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        try {
            // Case 1: Deep Link containing payload query param
            if (trimmed.contains("payload=")) {
                val uri = Uri.parse(trimmed)
                val payload = uri.getQueryParameter("payload")
                if (!payload.isNullOrBlank()) {
                    val jsonStr = String(Base64.decode(payload, Base64.NO_WRAP or Base64.URL_SAFE), StandardCharsets.UTF_8)
                    return parsePlaylistJson(jsonStr)
                }
            }

            // Case 2: Direct Base64 JSON Payload
            if (trimmed.length > 20 && !trimmed.contains(" ")) {
                runCatching {
                    val decodedBytes = Base64.decode(trimmed, Base64.NO_WRAP or Base64.URL_SAFE)
                    val jsonStr = String(decodedBytes, StandardCharsets.UTF_8)
                    val result = parsePlaylistJson(jsonStr)
                    if (result != null) return result
                }
            }

            // Case 3: Raw JSON string
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                return parsePlaylistJson(trimmed)
            }

            // Case 4: Plain text / Code / Search title fallback
            val codeName = if (trimmed.startsWith("SPASIBA-")) trimmed else "Imported Playlist ($trimmed)"
            return SharedPlaylistData(
                name = codeName,
                songs = emptyList(),
                shareCode = trimmed
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parsePlaylistJson(jsonStr: String): SharedPlaylistData? {
        val root = JSONObject(jsonStr)
        val name = root.optString("name", "Shared Playlist")
        val code = root.optString("code", "SPASIBA-IMPORT")
        val tracksArray = root.optJSONArray("tracks") ?: JSONArray()

        val songs = mutableListOf<Song>()
        for (i in 0 until tracksArray.length()) {
            val item = tracksArray.getJSONObject(i)
            val id = item.getString("id")
            val title = item.optString("t", "Unknown Track")
            val author = item.optString("a", "Unknown Artist")
            val duration = item.optInt("d", 180)
            val thumbnail = item.optString("m", "https://i.ytimg.com/vi/$id/hqdefault.jpg")

            songs.add(Song(id, title, author, thumbnail, duration))
        }

        return SharedPlaylistData(name = name, songs = songs, shareCode = code)
    }
}
