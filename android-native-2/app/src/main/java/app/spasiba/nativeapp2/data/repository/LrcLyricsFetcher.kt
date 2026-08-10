package app.spasiba.nativeapp2.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import app.spasiba.nativeapp2.data.network.HttpClientProvider

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

object LrcLyricsFetcher {
    private val client get() = HttpClientProvider.client

    private val lrcCache = mutableMapOf<String, List<LyricLine>>()

    suspend fun fetchSyncedLyrics(title: String, author: String, durationSeconds: Int = 0): List<LyricLine> = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTrackTitle(title)
        val cleanArtist = cleanArtistName(author)
        val cacheKey = "$cleanTitle::$cleanArtist"

        lrcCache[cacheKey]?.let { return@withContext it }

        runCatching {
            val urlBuilder = "https://lrclib.net/api/get".toHttpUrlOrNull()?.newBuilder() ?: return@withContext emptyList()
            urlBuilder.addQueryParameter("track_name", cleanTitle)
            if (cleanArtist.isNotBlank()) {
                urlBuilder.addQueryParameter("artist_name", cleanArtist)
            }
            if (durationSeconds > 0) {
                urlBuilder.addQueryParameter("duration", durationSeconds.toString())
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", "SpasibaMusicApp/2.0")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val syncedLyrics = json.optString("syncedLyrics")
                if (syncedLyrics.isNotBlank()) {
                    val parsed = parseLrc(syncedLyrics)
                    if (parsed.isNotEmpty()) {
                        lrcCache[cacheKey] = parsed
                        return@withContext parsed
                    }
                }
                val plainLyrics = json.optString("plainLyrics")
                if (plainLyrics.isNotBlank()) {
                    val rawLines = plainLyrics.lines().map { it.trim() }.filter { it.isNotBlank() }
                    if (rawLines.isNotEmpty()) {
                        val durMs = if (durationSeconds > 0) durationSeconds * 1000L else 180_000L
                        val stepMs = durMs / rawLines.size.coerceAtLeast(1)
                        val generated = rawLines.mapIndexed { idx, line ->
                            LyricLine(timestampMs = idx * stepMs, text = line)
                        }
                        lrcCache[cacheKey] = generated
                        return@withContext generated
                    }
                }
            }

            val searchQuery = "$cleanArtist $cleanTitle".trim()
            val searchUrl = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("q", searchQuery)
                ?.build() ?: return@withContext emptyList()

            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "SpasibaMusicApp/2.0")
                .build()

            val searchResp = client.newCall(searchReq).execute()
            if (searchResp.isSuccessful) {
                val searchBody = searchResp.body?.string().orEmpty()
                val array = JSONArray(searchBody)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val synced = item.optString("syncedLyrics")
                    if (synced.isNotBlank()) {
                        val parsed = parseLrc(synced)
                        if (parsed.isNotEmpty()) {
                            lrcCache[cacheKey] = parsed
                            return@withContext parsed
                        }
                    }
                    val plain = item.optString("plainLyrics")
                    if (plain.isNotBlank()) {
                        val rawLines = plain.lines().map { it.trim() }.filter { it.isNotBlank() }
                        if (rawLines.isNotEmpty()) {
                            val durMs = if (durationSeconds > 0) durationSeconds * 1000L else 180_000L
                            val stepMs = durMs / rawLines.size.coerceAtLeast(1)
                            val generated = rawLines.mapIndexed { idx, line ->
                                LyricLine(timestampMs = idx * stepMs, text = line)
                            }
                            lrcCache[cacheKey] = generated
                            return@withContext generated
                        }
                    }
                }
            }
        }.onFailure {
            Log.e("SPASIBA_LYRICS", "Failed to fetch synced lyrics for $cleanTitle by $cleanArtist", it)
        }

        emptyList()
    }

    fun parseLrc(lrcText: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

        lrcText.lines().forEach { line ->
            val match = regex.find(line.trim())
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = match.groupValues[3]
                val ms = if (fracStr.length == 2) fracStr.toLong() * 10 else fracStr.toLong()
                val timestampMs = (min * 60 + sec) * 1000 + ms
                val text = match.groupValues[4].trim()
                if (text.isNotBlank()) {
                    lines.add(LyricLine(timestampMs, text))
                }
            }
        }

        return lines.sortedBy { it.timestampMs }
    }

    private fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\b(official|video|music|audio|hd|4k|lyric|lyrics|full|song|remix|ft\\.?|feat\\.?).*"), "")
            .replace(Regex("[\\(\\)\\[\\]\\{\\}]"), " ")
            .trim()
    }

    private fun cleanArtistName(artist: String): String {
        return artist
            .replace(Regex("(?i)\\b(VEVO|Official|Topic|Music).*"), "")
            .replace(Regex("(?i)- Topic"), "")
            .trim()
    }
}
