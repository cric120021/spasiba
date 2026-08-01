package app.spasiba.nativeapp2.data.api

import android.content.Context
import android.util.Log
import app.spasiba.nativeapp2.data.model.CategoryChip
import app.spasiba.nativeapp2.data.model.Song
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class StreamingResult(
    val url: String,
    val audioUrl: String? = null,
    val headers: Map<String, String> = emptyMap()
)

enum class ShelfLayoutStyle {
    QUICK_PICKS_GRID,
    CAROUSEL_SQUARE,
    VIDEO_WIDESCREEN,
    CHARTS_RANKED,
    ARTIST_SPOTLIGHT,
    HERO_BANNER
}

data class HomeShelf(
    val title: String,
    val subtitle: String? = null,
    val style: ShelfLayoutStyle = ShelfLayoutStyle.CAROUSEL_SQUARE,
    val songs: List<Song>
)

object YouTubeMusicApi {
    private const val TAG = "YouTubeMusicApi"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val defaultCategories = listOf(
        CategoryChip("punjabi", "Punjabi", "top punjabi songs"),
        CategoryChip("hindi", "Hindi", "top hindi bollywood songs"),
        CategoryChip("english", "English", "top english pop hits"),
        CategoryChip("south", "South Hits", "top tamil telugu songs"),
        CategoryChip("haryanvi", "Haryanvi", "top haryanvi songs"),
        CategoryChip("bhojpuri", "Bhojpuri", "top bhojpuri songs"),
        CategoryChip("gujarati", "Gujarati", "top gujarati songs"),
        CategoryChip("bengali", "Bengali", "top bengali hits"),
        CategoryChip("marathi", "Marathi", "top marathi songs"),
        CategoryChip("malayalam", "Malayalam", "top malayalam songs"),
        CategoryChip("kannada", "Kannada", "top kannada songs"),
        CategoryChip("latin", "Latin & Reggaeton", "top latin reggaeton hits"),
        CategoryChip("kpop", "K-Pop", "top kpop hits"),
        CategoryChip("relax", "Chill & Relax", "chill music"),
        CategoryChip("energize", "Energize", "pop energize music"),
        CategoryChip("workout", "Workout", "gym workout beats"),
        CategoryChip("focus", "Focus & Study", "lofi study beats"),
        CategoryChip("party", "Party Hits", "top party dance hits"),
        CategoryChip("romance", "Romance", "romantic love songs"),
        CategoryChip("hiphop", "Hip-Hop & Rap", "hip hop rap top 50"),
        CategoryChip("rock", "Rock Classics", "classic rock hits"),
        CategoryChip("bollywood", "Bollywood", "top bollywood hindi hits")
    )

    private val searchCache = android.util.LruCache<String, List<Song>>(200)
    @Volatile
    private var cachedHomeShelves: List<HomeShelf> = emptyList()
    @Volatile
    private var cachedHomeRecommendations: List<Song> = emptyList()

    suspend fun fetchYouTubeMusicHomeShelves(params: String? = null): List<HomeShelf> = withContext(Dispatchers.IO) {
        if (params.isNullOrBlank() && cachedHomeShelves.isNotEmpty()) {
            Log.d(TAG, "HOME SHELVES CACHE HIT (${cachedHomeShelves.size} shelves)")
            return@withContext cachedHomeShelves
        }

        val shelves = mutableListOf<HomeShelf>()
        runCatching {
            val payloadObj = com.google.gson.JsonObject().apply {
                addProperty("browseId", "FEmusic_home")
                if (!params.isNullOrBlank()) {
                    addProperty("params", params)
                }
                val contextObj = com.google.gson.JsonObject().apply {
                    val clientObj = com.google.gson.JsonObject().apply {
                        addProperty("clientName", "WEB_REMIX")
                        addProperty("clientVersion", "1.20260701.01.00")
                        addProperty("hl", "en")
                        addProperty("gl", "IN")
                    }
                    add("client", clientObj)
                }
                add("context", contextObj)
            }

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/browse")
                .post(payloadObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
                .header("Origin", "https://music.youtube.com")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val json = JsonParser.parseString(body).asJsonObject

                val tabs = json.getAsJsonObject("contents")
                    ?.getAsJsonObject("singleColumnBrowseResultsRenderer")
                    ?.getAsJsonArray("tabs") ?: return@runCatching null

                for (tabElem in tabs) {
                    val contents = tabElem.asJsonObject.getAsJsonObject("tabRenderer")
                        ?.getAsJsonObject("content")
                        ?.getAsJsonObject("sectionListRenderer")
                        ?.getAsJsonArray("contents") ?: continue

                    for (sectionElem in contents) {
                        val sectionObj = sectionElem.asJsonObject
                        val shelfObj = sectionObj.getAsJsonObject("musicCarouselShelfRenderer")
                            ?: sectionObj.getAsJsonObject("musicShelfRenderer")
                            ?: sectionObj.getAsJsonObject("musicImmersiveCarouselShelfRenderer")
                            ?: continue

                        val headerObj = shelfObj.getAsJsonObject("header")
                            ?.getAsJsonObject("musicCarouselShelfBasicHeaderRenderer")
                        val titleRuns = headerObj?.getAsJsonObject("title")?.getAsJsonArray("runs")
                            ?: shelfObj.getAsJsonObject("title")?.getAsJsonArray("runs")
                        val title = titleRuns?.get(0)?.asJsonObject?.get("text")?.asString ?: "Featured Music"

                        val itemsArray = shelfObj.getAsJsonArray("contents") ?: continue
                        val songsList = mutableListOf<Song>()

                        for (itemElem in itemsArray) {
                            val itemObj = itemElem.asJsonObject
                            val twoRow = itemObj.getAsJsonObject("musicTwoRowItemRenderer")
                                ?: itemObj.getAsJsonObject("musicResponsiveListItemRenderer")
                                ?: continue

                            val titleText = twoRow.getAsJsonObject("title")
                                ?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString ?: "Track"

                            var videoId = ""
                            val navEndpoint = twoRow.getAsJsonObject("navigationEndpoint")
                                ?: twoRow.getAsJsonObject("overlay")
                                    ?.getAsJsonObject("musicItemHoverOverlayRenderer")
                                    ?.getAsJsonObject("content")
                                    ?.getAsJsonObject("musicPlayButtonRenderer")
                                    ?.getAsJsonObject("playNavigationEndpoint")

                            val watchEndpoint = navEndpoint?.getAsJsonObject("watchEndpoint")
                            if (watchEndpoint != null && watchEndpoint.has("videoId")) {
                                videoId = watchEndpoint.get("videoId").asString
                            }

                            val subtitleRuns = twoRow.getAsJsonObject("subtitle")?.getAsJsonArray("runs")
                                ?: twoRow.getAsJsonObject("longBylineText")?.getAsJsonArray("runs")
                            val authorText = subtitleRuns?.get(0)?.asJsonObject?.get("text")?.asString ?: "Artist"

                            // Skip items that are playlists, albums or artists instead of individual songs
                            val isPlaylistOrAlbum = titleText.lowercase().contains("playlist") ||
                                    titleText.lowercase().contains("album") ||
                                    authorText.lowercase().contains("playlist")

                            if (!isPlaylistOrAlbum && videoId.length == 11) {
                                // Guaranteed 100% 1-to-1 pairing: exact official thumbnail for this video ID
                                val finalThumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                songsList.add(
                                    Song(
                                        id = videoId,
                                        title = titleText,
                                        author = authorText,
                                        thumbnail = finalThumb,
                                        durationSeconds = 210
                                    )
                                )
                            }
                        }

                        if (songsList.isNotEmpty()) {
                            val layoutStyle = when {
                                title.lowercase().contains("quick") -> ShelfLayoutStyle.QUICK_PICKS_GRID
                                title.lowercase().contains("community") || title.lowercase().contains("video") || title.lowercase().contains("dance") -> ShelfLayoutStyle.VIDEO_WIDESCREEN
                                title.lowercase().contains("chart") || title.lowercase().contains("top") -> ShelfLayoutStyle.CHARTS_RANKED
                                title.lowercase().contains("artist") -> ShelfLayoutStyle.ARTIST_SPOTLIGHT
                                else -> ShelfLayoutStyle.CAROUSEL_SQUARE
                            }

                            shelves.add(HomeShelf(title = title, style = layoutStyle, songs = songsList))
                        }
                    }
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "Error fetching live YouTube Music home shelves: ${e.message}")
        }

        if (params.isNullOrBlank() && shelves.isNotEmpty()) {
            cachedHomeShelves = shelves
        }

        shelves
    }

    suspend fun fetchWebSearchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        val results = runCatching {
            val url = "https://spasiba.vercel.app/api/search?q=$encodedQuery&limit=25"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SpasibaNative2/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val json = JsonParser.parseString(body).asJsonObject
                val resultsArray = json.getAsJsonArray("results") ?: return@runCatching null

                val songs = mutableListOf<Song>()
                for (elem in resultsArray) {
                    val obj = elem.asJsonObject
                    val id = obj.get("id")?.asString ?: obj.get("videoId")?.asString ?: continue
                    val title = obj.get("title")?.asString ?: "Track"
                    val author = obj.get("author")?.asString ?: obj.get("artist")?.asString ?: "Artist"
                    val thumbnail = obj.get("thumbnail")?.asString ?: obj.get("artworkUrl")?.asString ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                    val duration = obj.get("durationSeconds")?.asInt ?: 210
                    val views = obj.get("views")?.asLong ?: 0L

                    songs.add(Song(id, title, author, thumbnail, duration, views = views))
                }
                songs
            }
        }.getOrNull()

        results ?: emptyList()
    }

    private fun normalizeSearchText(value: String): String =
        value
            .lowercase(java.util.Locale.ROOT)
            .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun containsAny(value: String, vararg needles: String): Boolean {
        val normalized = value.lowercase(java.util.Locale.ROOT)
        return needles.any { normalized.contains(it) }
    }

    private fun rankSongsByRelevance(query: String, songs: List<Song>): List<Song> {
        val q = query.trim().lowercase(java.util.Locale.ROOT)
        val words = q.split("\\s+".toRegex()).filter { it.isNotBlank() }

        return songs.distinctBy { it.id }.sortedByDescending { song ->
            var score = 0L
            val t = song.title.lowercase(java.util.Locale.ROOT)
            val a = song.author.lowercase(java.util.Locale.ROOT)
            val normalizedTitle = normalizeSearchText(t)
            val normalizedQuery = normalizeSearchText(q)
            val isOneWordQuery = words.size == 1
            val isKidsOrNonSong = containsAny(
                "$t $a",
                "kids", "kid song", "nursery", "rhyme", "rhymes", "cocomelon", "baby",
                "cartoon", "children", "abc", "phonics", "kindergarten", "preschool",
                "learning", "school", "lullaby", "sleep music", "story", "toy"
            )
            val isCompilation = containsAny(
                t,
                "playlist", "jukebox", "non stop", "non-stop", "collection", "compilation",
                "top 100", "top songs", "hour", "hours", "mix", "full album"
            )

            if (t == q) score += 10000L
            if (normalizedTitle == normalizedQuery) score += 14000L
            if (isOneWordQuery && normalizedTitle.startsWith("$normalizedQuery ")) score += 6000L
            else if (t.startsWith(q)) score += 5000L
            else if (t.contains(q)) score += 2000L

            for (w in words) {
                if (t.contains(w)) score += 500L
                if (a.contains(w)) score += 300L
            }

            if (t.contains("tutorial") || t.contains("review") || t.contains("gameplay") ||
                t.contains("reaction") || t.contains("unboxing") || t.contains("shorts") ||
                t.contains("interview") || t.contains("vlog") || t.contains("trailer") ||
                t.contains("podcast") || t.contains("episode") || t.contains("teaser")) {
                score -= 10000L
            }

            if (isKidsOrNonSong) score -= 25000L
            if (isCompilation) score -= 8000L

            if (t.contains("official") || t.contains("audio") || t.contains("music video") ||
                t.contains("song") || t.contains("lyric") || t.contains("ft") || t.contains("feat") ||
                t.contains("instrumental")) {
                score += 800L
            }

            if (t.contains("official music video") || t.contains("official audio")) score += 2500L

            if (song.views > 0) {
                score += (song.views / 1_000_000).coerceAtMost(500L)
            }

            score
        }
    }

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext getFallbackTracks()

        val cacheKey = trimmed.lowercase(java.util.Locale.ROOT)
        val cached = searchCache.get(cacheKey)
        if (!cached.isNullOrEmpty()) {
            Log.d(TAG, "SEARCH CACHE HIT for query='$trimmed'")
            return@withContext cached
        }

        // Targeted Search Strategy: prefer song-specific official results for broad queries.
        val lower = trimmed.lowercase(java.util.Locale.ROOT)
        val searchQueries = if (!lower.contains("song") && !lower.contains("audio") &&
            !lower.contains("music") && !lower.contains("track") && !lower.contains("full song") &&
            !lower.contains("hits") && !lower.contains("remix") && !lower.contains("radio") && !lower.contains("singles")) {
            listOf(
                "$trimmed official music video",
                "$trimmed official audio",
                "$trimmed song"
            )
        } else listOf(trimmed)

        // Tier 0: Live Server Search API Endpoint
        val webResults = searchQueries
            .flatMap { fetchWebSearchSongs(it) }
            .ifEmpty { fetchWebSearchSongs(trimmed) }
        if (webResults.isNotEmpty()) {
            val ranked = rankSongsByRelevance(trimmed, webResults)
            searchCache.put(cacheKey, ranked)
            return@withContext ranked
        }

        // Tier 1: Direct YouTube HTML/ytInitialData Search
        val ytDirectResults = runCatching {
            val encodedQuery = URLEncoder.encode(searchQueries.first(), "UTF-8")
            val request = Request.Builder()
                .url("https://www.youtube.com/results?search_query=$encodedQuery")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val html = response.body?.string() ?: return@runCatching null
                val matcher = Pattern.compile("var ytInitialData = (\\{.*?\\});</script>").matcher(html)
                if (!matcher.find()) return@runCatching null
                val jsonStr = matcher.group(1) ?: return@runCatching null

                val json = JsonParser.parseString(jsonStr).asJsonObject
                val contents = json.getAsJsonObject("contents")
                    ?.getAsJsonObject("twoColumnSearchResultsRenderer")
                    ?.getAsJsonObject("primaryContents")
                    ?.getAsJsonObject("sectionListRenderer")
                    ?.getAsJsonArray("contents") ?: return@runCatching null

                val songs = mutableListOf<Song>()
                for (item in contents) {
                    val itemSection = item.asJsonObject.getAsJsonObject("itemSectionRenderer") ?: continue
                    val renderers = itemSection.getAsJsonArray("contents") ?: continue
                    for (r in renderers) {
                        val videoRenderer = r.asJsonObject.getAsJsonObject("videoRenderer") ?: continue
                        val videoId = videoRenderer.get("videoId")?.asString ?: continue
                        val title = videoRenderer.getAsJsonObject("title")
                            ?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString ?: "Song"
                        val author = videoRenderer.getAsJsonObject("ownerText")
                            ?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString ?: "Artist"
                        val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                        songs.add(Song(videoId, title, author, thumb, 210))
                    }
                }
                songs
            }
        }.getOrNull()

        if (!ytDirectResults.isNullOrEmpty()) {
            val ranked = rankSongsByRelevance(trimmed, ytDirectResults)
            searchCache.put(cacheKey, ranked)
            return@withContext ranked
        }

        getFallbackTracks()
    }

    suspend fun fetchWebRelatedSongs(videoId: String): List<Song> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()

        val results = runCatching {
            val url = "https://spasiba.vercel.app/api/search?relatedTo=$videoId&limit=20"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SpasibaNative2/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val json = JsonParser.parseString(body).asJsonObject
                val resultsArray = json.getAsJsonArray("results") ?: return@runCatching null

                val songs = mutableListOf<Song>()
                for (elem in resultsArray) {
                    val obj = elem.asJsonObject
                    val id = obj.get("id")?.asString ?: obj.get("videoId")?.asString ?: continue
                    val title = obj.get("title")?.asString ?: "Track"
                    val author = obj.get("author")?.asString ?: obj.get("artist")?.asString ?: "Artist"
                    val thumbnail = obj.get("thumbnail")?.asString ?: obj.get("artworkUrl")?.asString ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                    val duration = obj.get("durationSeconds")?.asInt ?: 210

                    songs.add(Song(id, title, author, thumbnail, duration))
                }
                songs
            }
        }.getOrNull()

        results ?: emptyList()
    }

    suspend fun fetchYouTubeMusicNextRadioQueue(videoId: String): List<Song> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()

        val results = runCatching {
            val payload = """
                {
                  "videoId": "$videoId",
                  "playlistId": "RDAMVM$videoId",
                  "enableAutomix": true,
                  "isRadio": true,
                  "context": {
                    "client": {
                      "clientName": "WEB_REMIX",
                      "clientVersion": "1.20240101.01.00",
                      "hl": "en",
                      "gl": "US"
                    }
                  }
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/next")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Origin", "https://music.youtube.com")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val json = JsonParser.parseString(body).asJsonObject

                val songs = mutableListOf<Song>()
                val contents = json.getAsJsonObject("contents")
                    ?.getAsJsonObject("singleColumnMusicWatchNextResultsRenderer")
                    ?.getAsJsonObject("tabbedRenderer")
                    ?.getAsJsonObject("watchNextTabbedResultsRenderer")
                    ?.getAsJsonArray("tabs")?.get(0)?.asJsonObject
                    ?.getAsJsonObject("tabRenderer")
                    ?.getAsJsonObject("content")
                    ?.getAsJsonObject("musicQueueRenderer")
                    ?.getAsJsonObject("content")
                    ?.getAsJsonObject("playlistPanelRenderer")
                    ?.getAsJsonArray("contents")

                if (contents != null) {
                    for (item in contents) {
                        val panelVideo = item.asJsonObject.getAsJsonObject("playlistPanelVideoRenderer") ?: continue
                        val vid = panelVideo.get("videoId")?.asString ?: continue
                        val title = panelVideo.getAsJsonObject("title")
                            ?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString ?: "Track"
                        val author = panelVideo.getAsJsonObject("longBylineText")
                            ?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString ?: "Artist"
                        val thumb = "https://i.ytimg.com/vi/$vid/hqdefault.jpg"

                        songs.add(Song(vid, title, author, thumb, 210))
                    }
                }
                songs
            }
        }.getOrNull()

        results ?: emptyList()
    }

    fun resolveWithYtDlp(context: Context, videoId: String, isVideo: Boolean = false): StreamingResult? {
        if (videoId.isBlank()) return null
        return runCatching {
            val cacheDir = java.io.File(context.cacheDir, "spasiba_video")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val localFile = java.io.File(cacheDir, "${videoId}_${if (isVideo) "video" else "audio"}.mp4")
            if (localFile.exists() && localFile.length() > 500_000L) {
                Log.d(TAG, "LOCAL MP4 HIT for videoId=$videoId: ${localFile.absolutePath}")
                return StreamingResult(
                    url = localFile.absolutePath,
                    headers = emptyMap()
                )
            }

            val request = com.yausername.youtubedl_android.YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId").apply {
                addOption("-f", if (isVideo) "best[ext=mp4]/18/22/bestvideo+bestaudio/best" else "bestaudio[ext=m4a]/bestaudio/best")
                addOption("-g")
                addOption("--no-playlist")
            }
            val response = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
            val httpLines = response.out.lines().map { it.trim() }.filter { it.startsWith("http") }
            if (httpLines.isNotEmpty()) {
                val mainUrl = httpLines[0]
                val secUrl = if (httpLines.size > 1) httpLines[1] else null
                Log.d(TAG, "SUCCESS via yt-dlp for videoId=$videoId: mainUrl=${mainUrl.take(50)} secUrl=${secUrl?.take(50)}")
                StreamingResult(
                    url = mainUrl,
                    audioUrl = secUrl,
                    headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                )
            } else null
        }.onFailure {
            Log.e(TAG, "yt-dlp resolution failed for videoId=$videoId: ${it.message}")
        }.getOrNull()
    }

    suspend fun resolveStreamResult(context: Context, videoId: String): StreamingResult? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Resolving stream URL for videoId=$videoId")

        val ytDlpResult = resolveWithYtDlp(context, videoId, isVideo = false)
        if (ytDlpResult != null && ytDlpResult.url.isNotBlank()) {
            return@withContext ytDlpResult
        }

        // Direct YouTube Official InnerTube Endpoints (Instant ~20ms response!)
        val innerTubeClients = listOf(
            Triple(
                "ANDROID_VR",
                "1.59.19",
                "com.google.android.apps.youtube.vr.oculus/1.59.19 (Linux; U; Android 12)"
            ),
            Triple(
                "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                "2.0",
                "Mozilla/5.0 (SmartHub; SMART-TV; U; Linux/SmartTV) AppleWebKit/537.42"
            ),
            Triple(
                "WEB_EMBEDDED_PLAYER",
                "5.20241001.01.00",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
        )

        for ((clientName, clientVersion, userAgent) in innerTubeClients) {
            val result = runCatching {
                val payload = """
                    {
                      "videoId": "$videoId",
                      "contentCheckOk": true,
                      "racyCheckOk": true,
                      "context": {
                        "client": {
                          "clientName": "$clientName",
                          "clientVersion": "$clientVersion",
                          "hl": "en",
                          "gl": "US"
                        },
                        "thirdParty": {
                          "embedUrl": "https://www.youtube.com/watch?v=$videoId"
                        }
                      }
                    }
                """.trimIndent()

                val request = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .header("User-Agent", userAgent)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body?.string() ?: return@runCatching null
                    val json = JsonParser.parseString(body).asJsonObject
                    val streamingData = json.getAsJsonObject("streamingData") ?: return@runCatching null

                    val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats")
                    val audioFormats = adaptiveFormats?.map { it.asJsonObject }
                        ?.filter {
                            val mime = it.get("mimeType")?.asString ?: ""
                            mime.contains("audio")
                        }

                    val directAudioUrl = audioFormats
                        ?.filter { it.has("url") }
                        ?.maxByOrNull { it.get("bitrate")?.asInt ?: 0 }
                        ?.get("url")?.asString

                    if (!directAudioUrl.isNullOrBlank()) {
                        return@runCatching StreamingResult(
                            url = directAudioUrl,
                            headers = mapOf("User-Agent" to userAgent)
                        )
                    }

                    for (fmt in audioFormats ?: emptyList()) {
                        val cipher = fmt.get("signatureCipher")?.asString ?: fmt.get("cipher")?.asString ?: continue
                        val params = cipher.split("&").associate {
                            val parts = it.split("=")
                            val key = parts.getOrNull(0) ?: ""
                            val value = parts.getOrNull(1) ?: ""
                            val decoded = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
                            key to decoded
                        }
                        val rawUrl = params["url"] ?: continue
                        if (rawUrl.isNotBlank()) {
                            return@runCatching StreamingResult(
                                url = rawUrl,
                                headers = mapOf("User-Agent" to userAgent)
                            )
                        }
                    }

                    null
                }
            }.getOrNull()

            if (result != null && result.url.isNotBlank()) {
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "SUCCESS via InnerTube $clientName in ${elapsed}ms: ${result.url}")
                return@withContext result
            }
        }

        Log.e(TAG, "All stream resolution attempts exhausted for videoId=$videoId")
        null
    }

    suspend fun resolveVideoStreamResult(context: Context, videoId: String, quality: String = "720p"): StreamingResult? = withContext(Dispatchers.IO) {
        val targetHeight = when {
            quality.contains("1080") -> 1080
            quality.contains("720") -> 720
            quality.contains("480") -> 480
            quality.contains("360") -> 360
            else -> 720
        }

        val innerTubeClients = listOf(
            Triple("ANDROID_VR", "1.59.19", "com.google.android.apps.youtube.vr.oculus/1.59.19 (Linux; U; Android 12)"),
            Triple("TVHTML5_SIMPLY_EMBEDDED_PLAYER", "2.0", "Mozilla/5.0 (SmartHub; SMART-TV; U; Linux/SmartTV) AppleWebKit/537.42"),
            Triple("WEB_EMBEDDED_PLAYER", "5.20241001.01.00", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        )

        for ((clientName, clientVersion, userAgent) in innerTubeClients) {
            val result = runCatching {
                val payload = """
                    {
                      "videoId": "$videoId",
                      "contentCheckOk": true,
                      "racyCheckOk": true,
                      "context": {
                        "client": {
                          "clientName": "$clientName",
                          "clientVersion": "$clientVersion",
                          "hl": "en",
                          "gl": "US"
                        },
                        "thirdParty": {
                          "embedUrl": "https://www.youtube.com/watch?v=$videoId"
                        }
                      }
                    }
                """.trimIndent()

                val request = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .header("User-Agent", userAgent)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body?.string() ?: return@runCatching null
                    val json = JsonParser.parseString(body).asJsonObject
                    val streamingData = json.getAsJsonObject("streamingData") ?: return@runCatching null

                    val muxedFormats = streamingData.getAsJsonArray("formats")?.map { it.asJsonObject } ?: emptyList()
                    val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats")?.map { it.asJsonObject } ?: emptyList()

                    // Check for muxed format with video and audio in single stream first
                    val combinedFormat = muxedFormats
                        .filter { it.has("url") }
                        .minByOrNull {
                            val h = it.get("height")?.asInt ?: 0
                            Math.abs(h - targetHeight)
                        } ?: muxedFormats.firstOrNull { it.has("url") }

                    if (combinedFormat != null) {
                        val url = combinedFormat.get("url")?.asString
                        if (!url.isNullOrBlank()) {
                            return@runCatching StreamingResult(url = url, headers = mapOf("User-Agent" to userAgent))
                        }
                    }

                    // Otherwise check adaptive video format + adaptive audio format
                    val videoFormats = adaptiveFormats.filter {
                        val mime = it.get("mimeType")?.asString ?: ""
                        mime.contains("video") && it.has("url")
                    }
                    val targetVideoFormat = videoFormats.minByOrNull {
                        val h = it.get("height")?.asInt ?: 0
                        Math.abs(h - targetHeight)
                    } ?: videoFormats.firstOrNull()

                    val videoUrl = targetVideoFormat?.get("url")?.asString
                    if (!videoUrl.isNullOrBlank()) {
                        val audioFormat = adaptiveFormats.firstOrNull {
                            val mime = it.get("mimeType")?.asString ?: ""
                            mime.contains("audio") && it.has("url")
                        }
                        val audioUrl = audioFormat?.get("url")?.asString
                        return@runCatching StreamingResult(
                            url = videoUrl,
                            audioUrl = audioUrl,
                            headers = mapOf("User-Agent" to userAgent)
                        )
                    }
                    null
                }
            }.getOrNull()

            if (result != null && result.url.isNotBlank()) {
                return@withContext result
            }
        }

        val ytDlpVideoResult = resolveWithYtDlp(context, videoId, isVideo = true)
        if (ytDlpVideoResult != null && ytDlpVideoResult.url.isNotBlank()) {
            return@withContext ytDlpVideoResult
        }

        resolveStreamResult(context, videoId)
    }

    suspend fun getHomeRecommendations(): List<Song> = withContext(Dispatchers.IO) {
        if (cachedHomeRecommendations.isNotEmpty()) {
            return@withContext cachedHomeRecommendations
        }
        val trendingSongs = searchSongs("top music 2026")
        val result = if (trendingSongs.isNotEmpty()) trendingSongs else getFallbackTracks()
        if (result.isNotEmpty()) {
            cachedHomeRecommendations = result
        }
        result
    }

    fun getFallbackTracks(): List<Song> = listOf(
        Song("BddP6PYo2gs", "Kesariya", "Arijit Singh, Pritam", "https://i.ytimg.com/vi/BddP6PYo2gs/hqdefault.jpg", 268),
        Song("cUM9sK4D-G8", "Softly", "Karan Aujla, Ikky", "https://i.ytimg.com/vi/cUM9sK4D-G8/hqdefault.jpg", 154),
        Song("RLzC55ai0eo", "Heeriye", "Jasleen Royal, Arijit Singh", "https://i.ytimg.com/vi/RLzC55ai0eo/hqdefault.jpg", 194),
        Song("Umqb9KENgmk", "Tum Hi Ho", "Arijit Singh, Mithoon", "https://i.ytimg.com/vi/Umqb9KENgmk/hqdefault.jpg", 262),
        Song("3tmd-ClpJxA", "Blinding Lights", "The Weeknd", "https://i.ytimg.com/vi/3tmd-ClpJxA/hqdefault.jpg", 200),
        Song("kJQP7kiw5Fk", "Despacito", "Luis Fonsi ft. Daddy Yankee", "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg", 228)
    )
}
