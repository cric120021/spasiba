package app.spasiba.nativeapp2.data.api

import android.content.Context
import android.util.Log
import app.spasiba.nativeapp2.data.model.CategoryChip
import app.spasiba.nativeapp2.data.model.Song
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
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

    private object ResilientDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                val addresses = Dns.SYSTEM.lookup(hostname)
                if (addresses.isNotEmpty()) addresses else InetAddress.getAllByName(hostname).toList()
            } catch (e: Exception) {
                Log.w(TAG, "System DNS failed for $hostname, falling back to InetAddress lookup...", e)
                runCatching { InetAddress.getAllByName(hostname).toList() }.getOrElse { Dns.SYSTEM.lookup(hostname) }
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .dns(ResilientDns)
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    val defaultCategories = listOf(
        CategoryChip("punjabi", "Punjabi", "latest punjabi songs $currentYear official"),
        CategoryChip("hindi", "Hindi Hits", "latest hindi songs $currentYear official"),
        CategoryChip("bollywood", "Bollywood", "latest bollywood songs $currentYear official"),
        CategoryChip("english", "English Pop", "latest english pop songs $currentYear official"),
        CategoryChip("south", "South Hits", "latest tamil telugu hits $currentYear official"),
        CategoryChip("haryanvi", "Haryanvi", "latest haryanvi songs $currentYear official"),
        CategoryChip("bhojpuri", "Bhojpuri", "latest bhojpuri songs $currentYear official"),
        CategoryChip("gujarati", "Gujarati", "latest gujarati songs $currentYear official"),
        CategoryChip("bengali", "Bengali", "latest bengali songs $currentYear official"),
        CategoryChip("marathi", "Marathi", "latest marathi songs $currentYear official"),
        CategoryChip("malayalam", "Malayalam", "latest malayalam songs $currentYear official"),
        CategoryChip("kannada", "Kannada", "latest kannada songs $currentYear official"),
        CategoryChip("latin", "Latin & Reggaeton", "latest latin reggaeton hits $currentYear official"),
        CategoryChip("kpop", "K-Pop", "latest kpop songs $currentYear official"),
        CategoryChip("relax", "Chill & Relax", "chill acoustic songs $currentYear"),
        CategoryChip("energize", "Energize", "top energize songs $currentYear"),
        CategoryChip("workout", "Workout", "top workout gym songs $currentYear"),
        CategoryChip("focus", "Focus & Study", "lofi study beats $currentYear"),
        CategoryChip("party", "Party Hits", "latest party dance hits $currentYear official"),
        CategoryChip("romance", "Romance", "latest romantic love songs $currentYear official"),
        CategoryChip("hiphop", "Hip-Hop & Rap", "latest hip hop rap songs $currentYear official"),
        CategoryChip("rock", "Rock Classics", "classic rock songs $currentYear")
    )

    private val searchCache = android.util.LruCache<String, List<Song>>(200)
    @Volatile
    private var cachedHomeShelves: List<HomeShelf> = emptyList()
    @Volatile
    var cachedHomeRecommendations: List<Song> = emptyList()

    @Volatile
    var cachedTopCharts: List<Song> = emptyList()
    @Volatile
    var cachedPunjabiSongs: List<Song> = emptyList()
    @Volatile
    var cachedHindiSongs: List<Song> = emptyList()
    @Volatile
    var cachedEnglishSongs: List<Song> = emptyList()
    @Volatile
    var cachedSouthSongs: List<Song> = emptyList()
    @Volatile
    var cachedHaryanviSongs: List<Song> = emptyList()
    @Volatile
    var cachedBhojpuriSongs: List<Song> = emptyList()

    suspend fun fetchYouTubeMusicBrowseShelves(browseId: String = "FEmusic_home", params: String? = null): List<HomeShelf> = withContext(Dispatchers.IO) {
        val shelves = mutableListOf<HomeShelf>()
        runCatching {
            val payloadObj = com.google.gson.JsonObject().apply {
                addProperty("browseId", browseId)
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
                        val title = titleRuns?.get(0)?.asJsonObject?.get("text")?.asString ?: "Featured Items"

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

                            // Extract thumbnail URL
                            var thumbUrl = ""
                            val thumbArray = twoRow.getAsJsonObject("thumbnailRenderer")
                                ?.getAsJsonObject("musicThumbnailRenderer")
                                ?.getAsJsonObject("thumbnail")
                                ?.getAsJsonArray("thumbnails")
                                ?: twoRow.getAsJsonObject("thumbnail")
                                    ?.getAsJsonObject("musicThumbnailRenderer")
                                    ?.getAsJsonObject("thumbnail")
                                    ?.getAsJsonArray("thumbnails")

                            if (thumbArray != null && thumbArray.size() > 0) {
                                thumbUrl = thumbArray.get(thumbArray.size() - 1).asJsonObject.get("url")?.asString ?: ""
                            }

                            // If videoId was not in watchEndpoint, try parsing from thumbnail URL
                            if (videoId.length != 11 && thumbUrl.contains("/vi/")) {
                                val extracted = thumbUrl.substringAfter("/vi/").substringBefore("/")
                                if (extracted.length == 11) {
                                    videoId = extracted
                                }
                            }

                            val subtitleRuns = twoRow.getAsJsonObject("subtitle")?.getAsJsonArray("runs")
                                ?: twoRow.getAsJsonObject("longBylineText")?.getAsJsonArray("runs")
                            val authorText = subtitleRuns?.get(0)?.asJsonObject?.get("text")?.asString ?: "YouTube Music"

                            if (videoId.length == 11) {
                                val finalThumb = if (thumbUrl.isNotBlank()) thumbUrl else "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
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
                                title.lowercase().contains("community") || title.lowercase().contains("video") || title.lowercase().contains("watch") -> ShelfLayoutStyle.VIDEO_WIDESCREEN
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
            Log.e(TAG, "Error fetching YouTube Music browse shelves ($browseId, $params): ${e.message}")
        }

        shelves
    }

    suspend fun fetchYouTubeMusicHomeShelves(params: String? = null): List<HomeShelf> = withContext(Dispatchers.IO) {
        if (params.isNullOrBlank() && cachedHomeShelves.isNotEmpty()) {
            Log.d(TAG, "HOME SHELVES CACHE HIT (${cachedHomeShelves.size} shelves)")
            return@withContext cachedHomeShelves
        }

        val shelves = fetchYouTubeMusicBrowseShelves("FEmusic_home", params)
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
        val normalizedQuery = normalizeSearchText(q)
        if (normalizedQuery.isBlank() || songs.isEmpty()) return songs

        val authorMatches = songs.count { song ->
            val a = normalizeSearchText(song.author.lowercase(java.util.Locale.ROOT))
            a.isNotBlank() && (a == normalizedQuery || a.contains(normalizedQuery) || normalizedQuery.contains(a))
        }
        val titleMatches = songs.count { song ->
            val t = normalizeSearchText(song.title.lowercase(java.util.Locale.ROOT))
            t.isNotBlank() && t.contains(normalizedQuery)
        }

        val isArtistSearch = authorMatches > 0 && (titleMatches == 0 || (authorMatches >= songs.size * 0.4 && titleMatches < songs.size * 0.3))

        return songs.distinctBy { it.id }.sortedByDescending { song ->
            var score = 0L
            val t = song.title.lowercase(java.util.Locale.ROOT)
            val a = song.author.lowercase(java.util.Locale.ROOT)
            val normalizedTitle = normalizeSearchText(t)
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

            if (isArtistSearch) {
                // ARTIST SEARCH: Artist matches appear on top
                val normalizedAuthor = normalizeSearchText(a)
                if (normalizedAuthor == normalizedQuery || normalizedAuthor.contains(normalizedQuery) || normalizedQuery.contains(normalizedAuthor)) {
                    score += 80000L
                }
            } else {
                // NORMAL SONG SEARCH: Song title matches MUST appear on top!
                if (t == q) score += 100000L
                if (normalizedTitle == normalizedQuery) score += 140000L
                if (isOneWordQuery && normalizedTitle.startsWith("$normalizedQuery ")) score += 60000L
                else if (t.startsWith(q)) score += 50000L
                else if (t.contains(q)) score += 20000L

                for (w in words) {
                    if (t.contains(w)) score += 2000L
                }

                // Penalize songs whose title does not contain the query so artist top songs/compilations don't jump to the top
                if (!t.contains(q) && !normalizedTitle.contains(normalizedQuery)) {
                    score -= 50000L
                }
            }

            if (t.contains("tutorial") || t.contains("review") || t.contains("gameplay") ||
                t.contains("reaction") || t.contains("unboxing") || t.contains("shorts") ||
                t.contains("interview") || t.contains("vlog") || t.contains("trailer") ||
                t.contains("podcast") || t.contains("episode") || t.contains("teaser")) {
                score -= 30000L
            }

            val isRemixOrDjGarbage = !q.contains("remix") && !q.contains("dj") && !q.contains("mashup") &&
                    (t.contains("remix") || t.contains("dj ") || t.contains("djmix") || t.contains("slowed") ||
                     t.contains("reverb") || t.contains("8d audio") || t.contains("bass boosted") || t.contains("dholki"))

            if (isKidsOrNonSong) score -= 60000L
            if (isCompilation) score -= 25000L
            if (isRemixOrDjGarbage) score -= 45000L

            if (t.contains("official") || t.contains("audio") || t.contains("music video") ||
                t.contains("song") || t.contains("lyric") || t.contains("ft") || t.contains("feat") ||
                t.contains("instrumental")) {
                score += 800L
            }

            if (song.views > 0) {
                score += (song.views / 10_000L).coerceAtMost(100000L)
            } else {
                if (t.contains("official music video")) score += 20000L
                else if (t.contains("official video")) score += 15000L
                else if (t.contains("official audio") || t.contains("official song")) score += 12000L
            }

            score
        }
    }

    suspend fun fetchYouTubeMusicSearch(query: String, params: String? = "egWKAQI="): List<Song> = withContext(Dispatchers.IO) {
        val cleanedQuery = query.replace("%20", " ").trim()
        if (cleanedQuery.isBlank()) return@withContext emptyList()
        val songs = mutableListOf<Song>()
        runCatching {
            val payloadObj = com.google.gson.JsonObject().apply {
                addProperty("query", cleanedQuery)
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
                .url("https://music.youtube.com/youtubei/v1/search")
                .post(payloadObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
                .header("Origin", "https://music.youtube.com")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val json = JsonParser.parseString(body).asJsonObject

                val contents = json.getAsJsonObject("contents")
                    ?.getAsJsonObject("tabbedSearchResultsRenderer")
                    ?.getAsJsonArray("tabs")?.get(0)?.asJsonObject
                    ?.getAsJsonObject("tabRenderer")
                    ?.getAsJsonObject("content")
                    ?.getAsJsonObject("sectionListRenderer")
                    ?.getAsJsonArray("contents") ?: return@runCatching null

                for (sectionElem in contents) {
                    val musicShelf = sectionElem.asJsonObject.getAsJsonObject("musicShelfRenderer") ?: continue
                    val items = musicShelf.getAsJsonArray("contents") ?: continue

                    for (itemElem in items) {
                        val responsiveItem = itemElem.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer") ?: continue
                        val flexColumns = responsiveItem.getAsJsonArray("flexColumns") ?: continue
                        if (flexColumns.size() == 0) continue

                        val titleCol = flexColumns.get(0).asJsonObject
                            .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonObject("text")
                            ?.getAsJsonArray("runs")
                        val title = titleCol?.get(0)?.asJsonObject?.get("text")?.asString ?: continue

                        var videoId = ""
                        val navEndpoint = responsiveItem.getAsJsonObject("navigationEndpoint")
                            ?: responsiveItem.getAsJsonObject("overlay")
                                ?.getAsJsonObject("musicItemHoverOverlayRenderer")
                                ?.getAsJsonObject("content")
                                ?.getAsJsonObject("musicPlayButtonRenderer")
                                ?.getAsJsonObject("playNavigationEndpoint")

                        val watchEndpoint = navEndpoint?.getAsJsonObject("watchEndpoint")
                        if (watchEndpoint != null && watchEndpoint.has("videoId")) {
                            videoId = watchEndpoint.get("videoId").asString
                        }

                        if (videoId.length != 11 && titleCol.size() > 0) {
                            val titleNav = titleCol.get(0).asJsonObject.getAsJsonObject("navigationEndpoint")
                            if (titleNav != null && titleNav.has("watchEndpoint")) {
                                videoId = titleNav.getAsJsonObject("watchEndpoint").get("videoId").asString
                            }
                        }

                        if (videoId.length != 11) continue

                        var author = "Artist"
                        if (flexColumns.size() > 1) {
                            val authorRuns = flexColumns.get(1).asJsonObject
                                .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.getAsJsonObject("text")
                                ?.getAsJsonArray("runs")
                            if (authorRuns != null && authorRuns.size() > 0) {
                                author = authorRuns.get(0).asJsonObject.get("text").asString
                            }
                        }

                        val thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                        songs.add(Song(id = videoId, title = title, author = author, thumbnail = thumbnail, durationSeconds = 210))
                    }
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "Error performing native YouTube Music search: ${e.message}")
        }
        songs
    }

    suspend fun fetchHtmlYoutubeSearch(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val songs = mutableListOf<Song>()
        runCatching {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val html = response.body?.string() ?: return@runCatching null
                val matcher = java.util.regex.Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\".*?\"text\":\"([^\"]+)\"").matcher(html)
                var count = 0
                while (matcher.find() && count < 15) {
                    val id = matcher.group(1)
                    val title = matcher.group(2)
                    if (!id.isNullOrBlank() && id.length == 11 && !songs.any { it.id == id }) {
                        songs.add(
                            Song(
                                id = id,
                                title = title ?: query,
                                author = "YouTube",
                                thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                                durationSeconds = 210
                            )
                        )
                        count++
                    }
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "Error performing HTML YouTube search: ${e.message}")
        }
        songs
    }

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val cacheKey = trimmed.lowercase(java.util.Locale.ROOT)
        val cached = searchCache.get(cacheKey)
        if (!cached.isNullOrEmpty()) {
            Log.d(TAG, "SEARCH CACHE HIT for query='$trimmed'")
            return@withContext cached
        }

        // Tier 0: Direct YouTube Music InnerTube API (Filtered by Songs Only "egWKAQI=")
        val ytMusicNativeResults = fetchYouTubeMusicSearch(trimmed, params = "egWKAQI=")
        if (ytMusicNativeResults.isNotEmpty()) {
            val ranked = rankSongsByRelevance(trimmed, ytMusicNativeResults)
            searchCache.put(cacheKey, ranked)
            return@withContext ranked
        }

        // Tier 1: YouTube Music General Search (Unfiltered for Hinglish/spelling variations like "Tum to ho vhi")
        val ytMusicGeneralResults = fetchYouTubeMusicSearch(trimmed, params = null)
        if (ytMusicGeneralResults.isNotEmpty()) {
            val ranked = rankSongsByRelevance(trimmed, ytMusicGeneralResults)
            searchCache.put(cacheKey, ranked)
            return@withContext ranked
        }

        // Tier 2: Live Server Search API Endpoint
        val webResults = fetchWebSearchSongs(trimmed)
        if (webResults.isNotEmpty()) {
            val ranked = rankSongsByRelevance(trimmed, webResults)
            searchCache.put(cacheKey, ranked)
            return@withContext ranked
        }

        // Tier 3: HTML Scrape Fallback for rare Hinglish queries
        val htmlResults = fetchHtmlYoutubeSearch(trimmed)
        if (htmlResults.isNotEmpty()) {
            val ranked = rankSongsByRelevance(trimmed, htmlResults)
            searchCache.put(cacheKey, ranked)
            return@withContext ranked
        }

        emptyList()
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

    private val artistAvatarCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun getArtistAvatarUrl(artistName: String): String = withContext(Dispatchers.IO) {
        val key = artistName.trim().lowercase(java.util.Locale.ROOT)
        artistAvatarCache[key]?.let { return@withContext it }

        val avatar = runCatching {
            val payload = """
                {
                  "query": "$artistName",
                  "params": "EgIQAg%3D%3D",
                  "context": {
                    "client": {
                      "clientName": "WEB",
                      "clientVersion": "2.20240101.00.00",
                      "hl": "en",
                      "gl": "US"
                    }
                  }
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val json = JsonParser.parseString(body).asJsonObject
                val contents = json.getAsJsonObject("contents")
                    ?.getAsJsonObject("twoColumnSearchResultsRenderer")
                    ?.getAsJsonObject("primaryContents")
                    ?.getAsJsonObject("sectionListRenderer")
                    ?.getAsJsonArray("contents") ?: return@runCatching null

                for (sec in contents) {
                    val itemSection = sec.asJsonObject.getAsJsonObject("itemSectionRenderer") ?: continue
                    val items = itemSection.getAsJsonArray("contents") ?: continue
                    for (item in items) {
                        val channelRenderer = item.asJsonObject.getAsJsonObject("channelRenderer") ?: continue
                        val title = channelRenderer.getAsJsonObject("title")?.get("simpleText")?.asString ?: ""
                        val titleLower = title.lowercase(java.util.Locale.ROOT)
                        if (titleLower.contains(key) || key.contains(titleLower)) {
                            val thumbs = channelRenderer.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                            if (thumbs != null && thumbs.size() > 0) {
                                var url = thumbs.get(thumbs.size() - 1).asJsonObject.get("url").asString
                                if (url.startsWith("//")) url = "https:$url"
                                return@runCatching url
                            }
                        }
                    }
                }
                null
            }
        }.getOrNull()

        val finalAvatar = if (!avatar.isNullOrBlank() && avatar.startsWith("http")) {
            avatar
        } else {
            fetchYouTubeMusicSearch(artistName).firstOrNull()?.thumbnail ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300"
        }
        artistAvatarCache[key] = finalAvatar
        finalAvatar
    }

    suspend fun fetchAvailableVideoQualities(videoId: String): List<String> = withContext(Dispatchers.IO) {
        val qualities = mutableListOf<String>()
        qualities.add("Auto")

        try {
            val payload = """
                {
                  "videoId": "$videoId",
                  "contentCheckOk": true,
                  "racyCheckOk": true,
                  "context": {
                    "client": {
                      "clientName": "ANDROID_VR",
                      "clientVersion": "1.59.19",
                      "hl": "en",
                      "gl": "US"
                    }
                  }
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "com.google.android.apps.youtube.vr.oculus/1.59.19 (Linux; U; Android 12)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JsonParser.parseString(body).asJsonObject
                    val streamingData = json.getAsJsonObject("streamingData")
                    if (streamingData != null) {
                        val formats = streamingData.getAsJsonArray("formats")?.map { it.asJsonObject } ?: emptyList()
                        val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats")?.map { it.asJsonObject } ?: emptyList()
                        val allFmts = formats + adaptiveFormats

                        val heights = allFmts
                            .mapNotNull { it.get("height")?.asInt }
                            .filter { it > 0 }
                            .distinct()
                            .sortedDescending()

                        for (h in heights) {
                            when {
                                h >= 2160 && !qualities.contains("2160p (4K)") -> qualities.add("2160p (4K)")
                                h in 1440..2159 && !qualities.contains("1440p (2K)") -> qualities.add("1440p (2K)")
                                h in 1080..1439 && !qualities.contains("1080p") -> qualities.add("1080p")
                                h in 720..1079 && !qualities.contains("720p") -> qualities.add("720p")
                                h in 480..719 && !qualities.contains("480p") -> qualities.add("480p")
                                h in 360..479 && !qualities.contains("360p") -> qualities.add("360p")
                                h in 240..359 && !qualities.contains("240p") -> qualities.add("240p")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch available video qualities for videoId=$videoId: ${e.message}")
        }

        if (qualities.size <= 1) {
            listOf("Auto", "1080p", "720p", "480p", "360p")
        } else {
            qualities
        }
    }

    fun resolveWithYtDlp(context: Context, videoId: String, isVideo: Boolean = false, quality: String = "720p"): StreamingResult? {
        if (videoId.isBlank()) return null
        return runCatching {
            runCatching { com.yausername.youtubedl_android.YoutubeDL.getInstance().init(context.applicationContext) }

            val cacheDir = java.io.File(context.cacheDir, "spasiba_video")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val cleanQuality = quality.replace(Regex("[^0-9a-zA-Z]"), "")
            val localFile = java.io.File(cacheDir, "${videoId}_${if (isVideo) "video_${cleanQuality}" else "audio"}.mp4")
            if (localFile.exists() && localFile.length() > 500_000L) {
                Log.d(TAG, "LOCAL MP4 HIT for videoId=$videoId (quality=$quality): ${localFile.absolutePath}")
                return StreamingResult(
                    url = localFile.absolutePath,
                    headers = emptyMap()
                )
            }

            val formatOption = if (isVideo) {
                when {
                    quality.contains("2160") || quality.contains("4K", ignoreCase = true) -> "bestvideo[height<=2160][height>=1440]+bestaudio/bestvideo[height<=2160]+bestaudio/bestvideo+bestaudio"
                    quality.contains("1440") || quality.contains("2K", ignoreCase = true) -> "bestvideo[height<=1440][height>=1080]+bestaudio/bestvideo[height<=1440]+bestaudio/bestvideo+bestaudio"
                    quality.contains("1080") -> "bestvideo[height<=1080][height>=720]+bestaudio/bestvideo[height<=1080]+bestaudio/bestvideo+bestaudio"
                    quality.contains("720") -> "bestvideo[height<=720][height>=480]+bestaudio/bestvideo[height<=720]+bestaudio/bestvideo+bestaudio"
                    quality.contains("480") -> "bestvideo[height<=480][height>=360]+bestaudio/bestvideo[height<=480]+bestaudio/bestvideo+bestaudio"
                    quality.contains("360") -> "bestvideo[height<=360]+bestaudio/bestvideo+bestaudio"
                    else -> "bestvideo+bestaudio"
                }
            } else {
                "bestaudio/best"
            }

            val request = com.yausername.youtubedl_android.YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId").apply {
                addOption("-f", formatOption)
                addOption("-g")
                addOption("--no-playlist")
                addOption("--extractor-args", "youtube:player_client=android_vr,web")
            }
            val response = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
            val httpLines = response.out.lines().map { it.trim() }.filter { it.startsWith("http") }
            if (httpLines.isNotEmpty()) {
                val mainUrl = httpLines[0]
                val secUrl = if (httpLines.size > 1) httpLines[1] else null
                Log.d(TAG, "SUCCESS via yt-dlp for videoId=$videoId (quality=$quality): mainUrl=${mainUrl.take(50)} secUrl=${secUrl?.take(50)}")
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

    suspend fun fetchInvidiousStreamResult(videoId: String): StreamingResult? = withContext(Dispatchers.IO) {
        val invidiousInstances = listOf(
            "https://invidious.tiekoetter.com",
            "https://inv.nadeko.net",
            "https://invidious.f5.si",
            "https://yt.chocolatemoo53.com",
            "https://inv.zoomerville.com"
        )

        val fastClient = client.newBuilder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .build()

        for (instance in invidiousInstances) {
            val res = runCatching {
                val req = Request.Builder()
                    .url("$instance/api/v1/videos/$videoId")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                fastClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val body = resp.body?.string() ?: return@runCatching null
                    val json = JsonParser.parseString(body).asJsonObject

                    val audioStreams = json.getAsJsonArray("audioStreams")
                    if (audioStreams != null && audioStreams.size() > 0) {
                        for (elem in audioStreams) {
                            val obj = elem.asJsonObject
                            val url = obj.get("url")?.asString ?: ""
                            if (url.isNotBlank()) {
                                return@runCatching StreamingResult(
                                    url = url,
                                    headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                )
                            }
                        }
                    }

                    val adaptiveFormats = json.getAsJsonArray("adaptiveFormats")
                    if (adaptiveFormats != null) {
                        for (elem in adaptiveFormats) {
                            val obj = elem.asJsonObject
                            val type = obj.get("type")?.asString
                                ?: obj.get("mimeType")?.asString
                                ?: obj.get("container")?.asString
                                ?: ""
                            val url = obj.get("url")?.asString ?: ""
                            if (url.isNotBlank() && (type.contains("audio") || type.contains("m4a") || type.contains("webm") || type.contains("opus"))) {
                                return@runCatching StreamingResult(
                                    url = url,
                                    headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                )
                            }
                        }
                    }
                    null
                }
            }.onFailure {
                Log.e(TAG, "Invidious instance $instance failed for videoId=$videoId: ${it.message}")
            }.getOrNull()

            if (res != null && res.url.isNotBlank()) {
                Log.d(TAG, "SUCCESS via Invidious ($instance) for videoId=$videoId: ${res.url.take(60)}")
                return@withContext res
            }
        }
        null
    }

    suspend fun resolveStreamResult(context: Context, videoId: String): StreamingResult? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Resolving stream URL for videoId=$videoId")

        // 1. Fast High-Speed Native yt-dlp Resolver (~1.2s max delay matching 11:46 PM working build)
        val ytDlpResult = resolveWithYtDlp(context, videoId, isVideo = false)
        if (ytDlpResult != null && ytDlpResult.url.isNotBlank()) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "SUCCESS via Native yt-dlp in ${elapsed}ms for videoId=$videoId: ${ytDlpResult.url.take(60)}")
            return@withContext ytDlpResult
        }

        // 2. Direct YouTube Official InnerTube Endpoints Fallback
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
            ),
            Triple(
                "ANDROID_TESTSUITE",
                "1.9.0",
                "com.google.android.youtube/1.9.0 (Linux; U; Android 14)"
            ),
            Triple(
                "ANDROID_MUSIC",
                "6.42.52",
                "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; gzip)"
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

                    val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats")?.map { it.asJsonObject } ?: emptyList()
                    val formats = streamingData.getAsJsonArray("formats")?.map { it.asJsonObject } ?: emptyList()

                    val pureAudioFormats = adaptiveFormats.filter {
                        val mime = it.get("mimeType")?.asString ?: ""
                        mime.contains("audio")
                    }

                    val directAudioUrl = (pureAudioFormats.ifEmpty { adaptiveFormats + formats })
                        .filter { it.has("url") }
                        .maxByOrNull { it.get("bitrate")?.asInt ?: 0 }
                        ?.get("url")?.asString

                    if (!directAudioUrl.isNullOrBlank()) {
                        return@runCatching StreamingResult(
                            url = directAudioUrl,
                            headers = mapOf("User-Agent" to userAgent)
                        )
                    }

                    for (fmt in (pureAudioFormats.ifEmpty { adaptiveFormats + formats })) {
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

        // 2. Direct Vercel Audio Stream API Proxy
        val vercelResult = runCatching {
            val req = Request.Builder()
                .url("https://spasiba.vercel.app/api/stream?videoId=$videoId")
                .header("User-Agent", "SpasibaNative2/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string() ?: return@runCatching null
                val json = JsonParser.parseString(body).asJsonObject
                val streamUrl = json.get("streamUrl")?.asString
                if (!streamUrl.isNullOrBlank()) {
                    StreamingResult(url = streamUrl, headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"))
                } else null
            }
        }.getOrNull()

        if (vercelResult != null && vercelResult.url.isNotBlank()) {
            Log.d(TAG, "SUCCESS via Vercel Stream Proxy for videoId=$videoId: ${vercelResult.url.take(60)}")
            return@withContext vercelResult
        }

        Log.e(TAG, "All stream resolution attempts exhausted for videoId=$videoId")
        null
    }

    suspend fun resolveVideoStreamResult(context: Context, videoId: String, quality: String = "720p"): StreamingResult? = withContext(Dispatchers.IO) {
        // 1. Try Native yt-dlp FIRST with exact quality format string
        val ytDlpVideoResult = resolveWithYtDlp(context, videoId, isVideo = true, quality = quality)
        if (ytDlpVideoResult != null && ytDlpVideoResult.url.isNotBlank()) {
            Log.d(TAG, "SUCCESS via Native yt-dlp video for videoId=$videoId (quality=$quality): ${ytDlpVideoResult.url.take(60)}")
            return@withContext ytDlpVideoResult
        }

        val targetHeight = when {
            quality.contains("2160") || quality.contains("4K", ignoreCase = true) -> 2160
            quality.contains("1440") || quality.contains("2K", ignoreCase = true) -> 1440
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

                    // 1. For High-Res (1080p, 1440p, 2160p 4K) or adaptive preferences, search adaptiveFormats first!
                    val videoFormats = adaptiveFormats.filter {
                        val mime = it.get("mimeType")?.asString ?: ""
                        mime.contains("video") && it.has("url")
                    }
                    val targetVideoFormat = videoFormats.minByOrNull {
                        val h = it.get("height")?.asInt ?: 0
                        Math.abs(h - targetHeight)
                    }

                    if (targetVideoFormat != null) {
                        val videoUrl = targetVideoFormat.get("url")?.asString
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
                    }

                    // 2. Fallback to muxed formats (single stream video + audio) for low-bandwidth or legacy fallback
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

                    null
                }
            }.getOrNull()

            if (result != null && result.url.isNotBlank()) {
                return@withContext result
            }
        }

        resolveStreamResult(context, videoId)
    }

    data class ArtistPageData(
        val artistName: String,
        val avatarUrl: String?,
        val bannerUrl: String?,
        val topSongs: List<Song>,
        val recentVideos: List<Song>
    )

    private val languageHitsCache = ConcurrentHashMap<String, List<Song>>()
    private val artistPageCache = ConcurrentHashMap<String, ArtistPageData>()

    suspend fun fetchOfficialArtistPage(artistName: String): ArtistPageData = withContext(Dispatchers.IO) {
        val cleanArtist = artistName.trim().lowercase()
        if (cleanArtist.isBlank()) return@withContext ArtistPageData(artistName, null, null, emptyList(), emptyList())
        artistPageCache[cleanArtist]?.let { return@withContext it }

        // 1. Retrieve official YouTube Music artist browseId (UC... ID)
        val browseId = fetchArtistBrowseId(artistName)

        if (!browseId.isNullOrBlank()) {
            val pageData = fetchArtistPageByBrowseId(browseId, artistName)
            if (pageData != null && (pageData.topSongs.isNotEmpty() || pageData.recentVideos.isNotEmpty())) {
                artistPageCache[cleanArtist] = pageData
                return@withContext pageData
            }
        }

        // Fallback using YouTube Music search filters if browseId empty
        val top50 = fetchArtistTop50(artistName)
        val videos = fetchArtistRecentVideos(artistName)
        val avatar = getArtistAvatarUrl(artistName)
        val fallbackData = ArtistPageData(artistName, avatar, null, top50, videos)
        artistPageCache[cleanArtist] = fallbackData
        fallbackData
    }

    private suspend fun fetchArtistBrowseId(artistName: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val payloadObj = com.google.gson.JsonObject().apply {
                addProperty("query", artistName)
                addProperty("params", "EgWKAQIUAWoKEAkQBRAKEAMQBA==")
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
                .url("https://music.youtube.com/youtubei/v1/search")
                .post(payloadObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
                .header("Origin", "https://music.youtube.com")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val json = JsonParser.parseString(body).asJsonObject

                val contents = json.getAsJsonObject("contents")
                    ?.getAsJsonObject("tabbedSearchResultsRenderer")
                    ?.getAsJsonArray("tabs")?.get(0)?.asJsonObject
                    ?.getAsJsonObject("tabRenderer")
                    ?.getAsJsonObject("content")
                    ?.getAsJsonObject("sectionListRenderer")
                    ?.getAsJsonArray("contents") ?: return@runCatching null

                for (sectionElem in contents) {
                    val musicShelf = sectionElem.asJsonObject.getAsJsonObject("musicShelfRenderer") ?: continue
                    val items = musicShelf.getAsJsonArray("contents") ?: continue

                    for (itemElem in items) {
                        val responsiveItem = itemElem.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer")
                            ?: itemElem.asJsonObject.getAsJsonObject("musicTwoRowItemRenderer") ?: continue

                        val navEndpoint = responsiveItem.getAsJsonObject("navigationEndpoint")
                        val browseEndpoint = navEndpoint?.getAsJsonObject("browseEndpoint")
                        val browseId = browseEndpoint?.get("browseId")?.asString
                        if (!browseId.isNullOrBlank() && (browseId.startsWith("UC") || browseId.startsWith("FEmusic_library_privately_owned_artist_detail"))) {
                            return@runCatching browseId
                        }
                    }
                }
                null
            }
        }.getOrNull()
    }

    private suspend fun fetchArtistPageByBrowseId(browseId: String, artistName: String): ArtistPageData? = withContext(Dispatchers.IO) {
        runCatching {
            val payloadObj = com.google.gson.JsonObject().apply {
                addProperty("browseId", browseId)
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

                var avatarUrl: String? = null
                var bannerUrl: String? = null

                val headerObj = json.getAsJsonObject("header")
                    ?.getAsJsonObject("musicImmersiveHeaderRenderer")
                    ?: json.getAsJsonObject("header")?.getAsJsonObject("musicVisualHeaderRenderer")

                if (headerObj != null) {
                    val avatarThumb = headerObj.getAsJsonObject("thumbnail")
                        ?.getAsJsonObject("musicThumbnailRenderer")
                        ?.getAsJsonObject("thumbnail")
                        ?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject?.get("url")?.asString
                    avatarUrl = avatarThumb

                    val bannerThumb = headerObj.getAsJsonObject("foregroundThumbnail")
                        ?.getAsJsonObject("musicThumbnailRenderer")
                        ?.getAsJsonObject("thumbnail")
                        ?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject?.get("url")?.asString
                    bannerUrl = bannerThumb
                }

                val tabs = json.getAsJsonObject("contents")
                    ?.getAsJsonObject("singleColumnBrowseResultsRenderer")
                    ?.getAsJsonArray("tabs") ?: return@runCatching null

                val topSongsList = mutableListOf<Song>()
                val videosList = mutableListOf<Song>()

                for (tabElem in tabs) {
                    val contents = tabElem.asJsonObject.getAsJsonObject("tabRenderer")
                        ?.getAsJsonObject("content")
                        ?.getAsJsonObject("sectionListRenderer")
                        ?.getAsJsonArray("contents") ?: continue

                    for (sectionElem in contents) {
                        val sectionObj = sectionElem.asJsonObject
                        val shelfObj = sectionObj.getAsJsonObject("musicCarouselShelfRenderer")
                            ?: sectionObj.getAsJsonObject("musicShelfRenderer") ?: continue

                        val headerText = shelfObj.getAsJsonObject("header")
                            ?.getAsJsonObject("musicCarouselShelfBasicHeaderRenderer")
                            ?.getAsJsonObject("title")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                            ?: shelfObj.getAsJsonObject("title")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                            ?: ""

                        val itemsArray = shelfObj.getAsJsonArray("contents") ?: continue
                        for (itemElem in itemsArray) {
                            val twoRow = itemElem.asJsonObject.getAsJsonObject("musicTwoRowItemRenderer")
                                ?: itemElem.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer") ?: continue

                            val titleText = twoRow.getAsJsonObject("title")
                                ?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString ?: continue

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

                            if (videoId.length == 11) {
                                val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                val songObj = Song(id = videoId, title = titleText, author = artistName, thumbnail = thumb, durationSeconds = 210)

                                if (headerText.lowercase().contains("video")) {
                                    videosList.add(songObj)
                                } else {
                                    topSongsList.add(songObj)
                                }
                            }
                        }
                    }
                }

                ArtistPageData(
                    artistName = artistName,
                    avatarUrl = avatarUrl,
                    bannerUrl = bannerUrl,
                    topSongs = topSongsList.distinctBy { it.id }.take(50),
                    recentVideos = videosList.distinctBy { it.id }.take(20)
                )
            }
        }.getOrNull()
    }
    private val artistTop50Cache = ConcurrentHashMap<String, List<Song>>()
    private val artistVideosCache = ConcurrentHashMap<String, List<Song>>()

    private val artistGarbageKeywords = listOf(
        "reaction", "reacting", "karaoke", "instrumental tutorial", "guitar lesson", 
        "piano tutorial", "cover by", "dance cover", "review", "status", "shorts", 
        "full album jukebox", "8d audio", "slowed reverb", "lofi flip", "mashup status"
    )

    suspend fun fetchArtistTop50(artistName: String): List<Song> = withContext(Dispatchers.IO) {
        val cleanArtist = artistName.trim().lowercase()
        if (cleanArtist.isBlank()) return@withContext emptyList()
        artistTop50Cache[cleanArtist]?.let { return@withContext it }

        // Query native YouTube Music API with Songs Filter
        val ytSongs = fetchYouTubeMusicSearch("$artistName top songs", params = "EgWKAQIIAWoKEAkQBRAKEAMQBA==")
        val cleanSongs = ytSongs.filter { song ->
            val titleLower = song.title.lowercase()
            artistGarbageKeywords.none { titleLower.contains(it) }
        }.take(50)

        val result = if (cleanSongs.isNotEmpty()) cleanSongs else searchSongs("$artistName top songs official").take(50)
        artistTop50Cache[cleanArtist] = result
        result
    }

    suspend fun fetchArtistRecentVideos(artistName: String): List<Song> = withContext(Dispatchers.IO) {
        val cleanArtist = artistName.trim().lowercase()
        if (cleanArtist.isBlank()) return@withContext emptyList()
        artistVideosCache[cleanArtist]?.let { return@withContext it }

        // Query native YouTube Music API with Videos Filter
        val ytVideos = fetchYouTubeMusicSearch("$artistName official video", params = "EgWKAQIQAWoKEAkQBRAKEAMQBA==")
        val cleanVideos = ytVideos.filter { song ->
            val titleLower = song.title.lowercase()
            artistGarbageKeywords.none { titleLower.contains(it) }
        }.take(20)

        val result = if (cleanVideos.isNotEmpty()) cleanVideos else searchSongs("$artistName official music video").take(20)
        artistVideosCache[cleanArtist] = result
        result
    }

    suspend fun fetchLanguageHits(language: String): List<Song> = withContext(Dispatchers.IO) {
        val cleanLang = language.trim().lowercase()
        if (cleanLang.isBlank()) return@withContext emptyList()

        languageHitsCache[cleanLang]?.let { return@withContext it }

        val searchQuery = when (cleanLang) {
            "punjabi" -> "Punjabi Hits Top 50 2026"
            "english" -> "Global Hits Top 50 2026"
            "hindi" -> "Bollywood Hits Top 50 2026"
            "tamil" -> "Tamil Hits Top 50 2026"
            "telugu" -> "Telugu Hits Top 50 2026"
            "spanish" -> "Spanish Top Hits 2026"
            else -> "$language Top Hits 2026"
        }

        val results = searchSongs(searchQuery)
        if (results.isNotEmpty()) {
            languageHitsCache[cleanLang] = results
        }
        results
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

    suspend fun fetchUnifiedLyrics(videoId: String, title: String, author: String, durationSeconds: Int = 0): List<app.spasiba.nativeapp2.data.repository.LyricLine> = withContext(Dispatchers.IO) {
        Log.d("SPASIBA_LYRICS", "fetchUnifiedLyrics started for title='$title' author='$author' videoId='$videoId'")
        val lrcResult = app.spasiba.nativeapp2.data.repository.LrcLyricsFetcher.fetchSyncedLyrics(title, author, durationSeconds)
        if (lrcResult.isNotEmpty()) {
            Log.d("SPASIBA_LYRICS", "SUCCESS via LRCLIB Synced API for '$title': ${lrcResult.size} lines")
            return@withContext lrcResult
        }

        val ytLyrics = runCatching {
            val nextPayload = """
                {
                  "videoId": "$videoId",
                  "context": {
                    "client": {
                      "clientName": "WEB_REMIX",
                      "clientVersion": "1.20241001.01.00",
                      "hl": "en",
                      "gl": "US"
                    }
                  }
                }
            """.trimIndent()

            val nextReq = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/next")
                .post(nextPayload.toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            client.newCall(nextReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val json = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val tabs = json.getAsJsonObject("contents")
                    ?.getAsJsonObject("singleColumnMusicWatchNextResultsRenderer")
                    ?.getAsJsonObject("tabbedRenderer")
                    ?.getAsJsonObject("watchNextTabbedResultsRenderer")
                    ?.getAsJsonArray("tabs")

                var lyricsBrowseId: String? = null
                tabs?.forEach { tabElem ->
                    val tab = tabElem.asJsonObject.getAsJsonObject("tabRenderer")
                    val bId = tab?.getAsJsonObject("endpoint")
                        ?.getAsJsonObject("browseEndpoint")
                        ?.get("browseId")?.asString
                    if (bId != null && (bId.startsWith("MPLYt_") || tab.get("title")?.asString == "Lyrics")) {
                        lyricsBrowseId = bId
                    }
                }

                if (lyricsBrowseId.isNullOrBlank()) return@use null

                val browsePayload = """
                    {
                      "browseId": "$lyricsBrowseId",
                      "context": {
                        "client": {
                          "clientName": "WEB_REMIX",
                          "clientVersion": "1.20241001.01.00",
                          "hl": "en",
                          "gl": "US"
                        }
                      }
                    }
                """.trimIndent()

                val browseReq = Request.Builder()
                    .url("https://music.youtube.com/youtubei/v1/browse")
                    .post(browsePayload.toRequestBody(JSON_MEDIA_TYPE))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                client.newCall(browseReq).execute().use { bResp ->
                    if (!bResp.isSuccessful) return@use null
                    val bJson = JsonParser.parseString(bResp.body?.string().orEmpty()).asJsonObject
                    val section = bJson.getAsJsonObject("contents")
                        ?.getAsJsonObject("sectionListRenderer")
                        ?.getAsJsonArray("contents")
                        ?.get(0)?.asJsonObject
                        ?.getAsJsonObject("musicDescriptionShelfRenderer")

                    val descriptionText = section?.getAsJsonObject("description")
                        ?.getAsJsonArray("runs")
                        ?.joinToString("") { it.asJsonObject.get("text").asString }

                    if (!descriptionText.isNullOrBlank()) {
                        val rawLines = descriptionText.lines().map { it.trim() }.filter { it.isNotBlank() }
                        if (rawLines.isNotEmpty()) {
                            val durMs = if (durationSeconds > 0) durationSeconds * 1000L else 180_000L
                            val stepMs = durMs / rawLines.size.coerceAtLeast(1)
                            return@use rawLines.mapIndexed { idx, line ->
                                app.spasiba.nativeapp2.data.repository.LyricLine(
                                    timestampMs = idx * stepMs,
                                    text = line
                                )
                            }
                        }
                    }
                    null
                }
            }
        }.getOrNull()

        if (!ytLyrics.isNullOrEmpty()) {
            return@withContext ytLyrics
        }

        listOf(
            app.spasiba.nativeapp2.data.repository.LyricLine(0L, "♪ $title - $author ♪"),
            app.spasiba.nativeapp2.data.repository.LyricLine(2000L, "Lyrics for this track are being synchronized...")
        )
    }
}
