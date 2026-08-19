package app.spasiba.nativeapp2.data.api

import android.content.Context
import android.util.Log
import app.spasiba.nativeapp2.data.model.CategoryChip
import app.spasiba.nativeapp2.data.model.Song
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.Inet4Address
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
                val sortedAddresses = addresses.sortedBy { if (it is Inet4Address) 0 else 1 }
                if (sortedAddresses.isNotEmpty()) sortedAddresses else InetAddress.getAllByName(hostname).toList()
            } catch (e: Exception) {
                Log.w(TAG, "System DNS failed for $hostname, falling back to InetAddress lookup...", e)
                runCatching {
                    InetAddress.getAllByName(hostname).toList().sortedBy { if (it is Inet4Address) 0 else 1 }
                }.getOrElse { Dns.SYSTEM.lookup(hostname) }
            }
        }
    }

    val client = OkHttpClient.Builder()
        .dns(ResilientDns)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
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
    var cachedHomeShelves: List<HomeShelf> = emptyList()
    @Volatile
    var cachedHomeRecommendations: List<Song> = emptyList()

    @Volatile
    private var visitorData: String? = null
    @Volatile
    var dynamicCategoryChips: List<CategoryChip> = emptyList()

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

    private data class BrowseCacheEntry(val timestamp: Long, val shelves: List<HomeShelf>)
    private val browseShelvesCache = java.util.concurrent.ConcurrentHashMap<String, BrowseCacheEntry>()
    private const val TWELVE_HOURS_MS = 12 * 60 * 60 * 1000L

    suspend fun fetchCategoryChips(): List<CategoryChip> = withContext(Dispatchers.IO) {
        if (dynamicCategoryChips.isNotEmpty()) return@withContext dynamicCategoryChips
        fetchYouTubeMusicBrowseShelves("FEmusic_home")
        dynamicCategoryChips.ifEmpty { defaultCategories }
    }

    suspend fun fetchYouTubeMusicBrowseShelves(browseId: String = "FEmusic_home", params: String? = null): List<HomeShelf> = withContext(Dispatchers.IO) {
        val cacheKey = "${browseId}_${params ?: "default"}"
        val cached = browseShelvesCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < TWELVE_HOURS_MS) {
            Log.d(TAG, "12-HOUR BROWSE CACHE HIT for key=$cacheKey (${cached.shelves.size} shelves)")
            return@withContext cached.shelves
        }

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
                        visitorData?.let { addProperty("visitorData", it) }
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

                val respVisitor = json.getAsJsonObject("responseContext")?.get("visitorData")?.asString
                if (!respVisitor.isNullOrBlank()) {
                    visitorData = respVisitor
                }

                var continuationToken: String? = null

                val tabs = json.getAsJsonObject("contents")
                    ?.getAsJsonObject("singleColumnBrowseResultsRenderer")
                    ?.getAsJsonArray("tabs")

                val sectionContents = mutableListOf<com.google.gson.JsonElement>()

                if (tabs != null) {
                    for (tabElem in tabs) {
                        val tabObj = tabElem.asJsonObject.getAsJsonObject("tabRenderer") ?: continue
                        val sectionList = tabObj.getAsJsonObject("content")?.getAsJsonObject("sectionListRenderer") ?: continue
                        
                        // Dynamically extract dynamic category chips from header
                        val headerChips = sectionList.getAsJsonObject("header")
                            ?.getAsJsonObject("chipCloudRenderer")
                            ?.getAsJsonArray("chips")
                        if (headerChips != null && headerChips.size() > 0) {
                            val parsedChips = mutableListOf<CategoryChip>()
                            for (chipElem in headerChips) {
                                val chipData = chipElem.asJsonObject.getAsJsonObject("chipCloudChipRenderer") ?: continue
                                val label = chipData.getAsJsonObject("text")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString ?: continue
                                val chipParam = chipData.getAsJsonObject("navigationEndpoint")
                                    ?.getAsJsonObject("browseEndpoint")
                                    ?.get("params")?.asString ?: ""
                                parsedChips.add(CategoryChip(id = label.lowercase(), label = label, params = chipParam))
                            }
                            if (parsedChips.isNotEmpty()) {
                                dynamicCategoryChips = parsedChips
                            }
                        }

                        val contents = sectionList.getAsJsonArray("contents") ?: continue
                        contents.forEach { sectionContents.add(it) }

                        val continuations = sectionList.getAsJsonArray("continuations")
                        if (continuations != null && continuations.size() > 0) {
                            continuationToken = continuations.get(0).asJsonObject
                                ?.getAsJsonObject("nextContinuationData")
                                ?.get("continuation")?.asString
                        }
                    }
                }

                for (sectionElem in sectionContents) {
                    val sectionObj = sectionElem.asJsonObject

                    if (sectionObj.has("continuationItemRenderer")) {
                        val token = sectionObj.getAsJsonObject("continuationItemRenderer")
                            ?.getAsJsonObject("continuationEndpoint")
                            ?.getAsJsonObject("continuationCommand")
                            ?.get("token")?.asString
                        if (!token.isNullOrBlank()) {
                            continuationToken = token
                        }
                        continue
                    }

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
                            ?: itemObj.getAsJsonObject("musicMultiRowListItemRenderer")
                            ?: itemObj.getAsJsonObject("podcastShowReleaseRenderer")
                            ?: continue

                        val titleText = twoRow.getAsJsonObject("title")
                            ?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                            ?: twoRow.getAsJsonObject("headline")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                            ?: run {
                                val flexCols = twoRow.getAsJsonArray("flexColumns")
                                flexCols?.get(0)?.asJsonObject
                                    ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                                    ?.getAsJsonObject("text")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                            }
                            ?: "Track"

                        var videoId = ""
                        var watchPlaylistId = ""
                        val navEndpoint = twoRow.getAsJsonObject("navigationEndpoint")
                            ?: twoRow.getAsJsonObject("overlay")
                                ?.getAsJsonObject("musicItemHoverOverlayRenderer")
                                ?.getAsJsonObject("content")
                                ?.getAsJsonObject("musicPlayButtonRenderer")
                                ?.getAsJsonObject("playNavigationEndpoint")

                        val watchEndpoint = navEndpoint?.getAsJsonObject("watchEndpoint")
                        if (watchEndpoint != null) {
                            if (watchEndpoint.has("videoId")) {
                                videoId = watchEndpoint.get("videoId").asString
                            }
                            if (watchEndpoint.has("playlistId")) {
                                watchPlaylistId = watchEndpoint.get("playlistId").asString
                            }
                        }

                        val browseEndpoint = navEndpoint?.getAsJsonObject("browseEndpoint")
                        val itemBrowseId = browseEndpoint?.get("browseId")?.asString ?: ""

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

                        val subtitleRuns = twoRow.getAsJsonObject("subtitle")?.getAsJsonArray("runs")
                            ?: twoRow.getAsJsonObject("longBylineText")?.getAsJsonArray("runs")
                        val authorText = subtitleRuns?.mapNotNull { it.asJsonObject.get("text")?.asString }?.joinToString("")
                            ?: run {
                                val flexCols = twoRow.getAsJsonArray("flexColumns")
                                if (flexCols != null && flexCols.size() > 1) {
                                    val runs = flexCols.get(1).asJsonObject
                                        ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                                        ?.getAsJsonObject("text")?.getAsJsonArray("runs")
                                    runs?.mapNotNull { it.asJsonObject.get("text")?.asString }?.joinToString("")
                                } else null
                            }
                            ?: "YouTube Music"

                        val finalId = when {
                            videoId.length == 11 -> videoId
                            watchPlaylistId.isNotBlank() -> watchPlaylistId
                            itemBrowseId.isNotBlank() -> itemBrowseId
                            thumbUrl.contains("/vi/") -> {
                                val extracted = thumbUrl.substringAfter("/vi/").substringBefore("/")
                                if (extracted.length == 11) extracted else "item_${titleText.hashCode()}"
                            }
                            titleText.isNotBlank() -> "item_${titleText.hashCode()}"
                            else -> ""
                        }

                        if (finalId.isNotBlank() && titleText.isNotBlank()) {
                            val finalThumb = when {
                                thumbUrl.startsWith("//") -> "https:$thumbUrl"
                                thumbUrl.startsWith("http://") -> thumbUrl.replace("http://", "https://")
                                thumbUrl.isNotBlank() && !thumbUrl.contains("default/hqdefault.jpg") -> thumbUrl
                                finalId.length == 11 -> "https://i.ytimg.com/vi/$finalId/hqdefault.jpg"
                                else -> "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500"
                            }
                            songsList.add(
                                Song(
                                    id = finalId,
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

                Log.d(TAG, "Initial browse request ($browseId, params=$params) parsed ${shelves.size} shelves. Initial continuation token: ${continuationToken?.take(20)}...")

                // Follow continuation tokens continuously to populate home screen with all live shelves
                var nextToken = continuationToken
                var pageCount = 0
                while (!nextToken.isNullOrBlank() && pageCount < 12) {
                    pageCount++
                    val currentToken = nextToken
                    nextToken = null

                    runCatching {
                        val contPayload = com.google.gson.JsonObject().apply {
                            addProperty("continuation", currentToken)
                            val contextObj = com.google.gson.JsonObject().apply {
                                val clientObj = com.google.gson.JsonObject().apply {
                                    addProperty("clientName", "WEB_REMIX")
                                    addProperty("clientVersion", "1.20260701.01.00")
                                    addProperty("hl", "en")
                                    addProperty("gl", "IN")
                                    visitorData?.let { addProperty("visitorData", it) }
                                }
                                add("client", clientObj)
                            }
                            add("context", contextObj)
                        }

                        val contUrl = "https://music.youtube.com/youtubei/v1/browse?ctoken=$currentToken&continuation=$currentToken"
                        val contRequest = Request.Builder()
                            .url(contUrl)
                            .post(contPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
                            .header("Origin", "https://music.youtube.com")
                            .build()

                        client.newCall(contRequest).execute().use { contResp ->
                            if (!contResp.isSuccessful) {
                                Log.w(TAG, "Continuation page $pageCount HTTP ${contResp.code}")
                                return@use
                            }
                            val contBody = contResp.body?.string() ?: return@use
                            val contJson = JsonParser.parseString(contBody).asJsonObject

                            val contVisitor = contJson.getAsJsonObject("responseContext")?.get("visitorData")?.asString
                            if (!contVisitor.isNullOrBlank()) {
                                visitorData = contVisitor
                            }

                            val secContObj = contJson.getAsJsonObject("continuationContents")
                                ?.getAsJsonObject("sectionListContinuation")
                            val contItems = secContObj?.getAsJsonArray("contents") ?: return@use

                            val contContinuations = secContObj.getAsJsonArray("continuations")
                            if (contContinuations != null && contContinuations.size() > 0) {
                                val nextTok = contContinuations.get(0).asJsonObject
                                    ?.getAsJsonObject("nextContinuationData")
                                    ?.get("continuation")?.asString
                                if (!nextTok.isNullOrBlank()) {
                                    nextToken = nextTok
                                }
                            }

                            val newShelvesCountBefore = shelves.size
                            for (sectionElem in contItems) {
                                val sectionObj = sectionElem.asJsonObject
                                if (sectionObj.has("continuationItemRenderer")) {
                                    val nextTok = sectionObj.getAsJsonObject("continuationItemRenderer")
                                        ?.getAsJsonObject("continuationEndpoint")
                                        ?.getAsJsonObject("continuationCommand")
                                        ?.get("token")?.asString
                                    if (!nextTok.isNullOrBlank()) {
                                        nextToken = nextTok
                                    }
                                    continue
                                }

                                val shelfObj = sectionObj.getAsJsonObject("musicCarouselShelfRenderer")
                                    ?: sectionObj.getAsJsonObject("musicShelfRenderer")
                                    ?: sectionObj.getAsJsonObject("musicImmersiveCarouselShelfRenderer")
                                    ?: continue

                                val headerObj = shelfObj.getAsJsonObject("header")
                                    ?.getAsJsonObject("musicCarouselShelfBasicHeaderRenderer")
                                val titleRuns = headerObj?.getAsJsonObject("title")?.getAsJsonArray("runs")
                                    ?: shelfObj.getAsJsonObject("title")?.getAsJsonArray("runs")
                                val title = titleRuns?.get(0)?.asJsonObject?.get("text")?.asString ?: "More Recommendations"

                                val itemsArray = shelfObj.getAsJsonArray("contents") ?: continue
                                val songsList = mutableListOf<Song>()

                                for (itemElem in itemsArray) {
                                    val twoRow = itemElem.asJsonObject.getAsJsonObject("musicTwoRowItemRenderer")
                                        ?: itemElem.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer")
                                        ?: itemElem.asJsonObject.getAsJsonObject("musicMultiRowListItemRenderer")
                                        ?: itemElem.asJsonObject.getAsJsonObject("podcastShowReleaseRenderer")
                                        ?: continue

                                    val titleText = twoRow.getAsJsonObject("title")
                                        ?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                                        ?: twoRow.getAsJsonObject("headline")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                                        ?: run {
                                            val flexCols = twoRow.getAsJsonArray("flexColumns")
                                            flexCols?.get(0)?.asJsonObject
                                                ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                                                ?.getAsJsonObject("text")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                                        }
                                        ?: "Track"

                                    var videoId = ""
                                    var watchPlaylistId = ""
                                    val navEndpoint = twoRow.getAsJsonObject("navigationEndpoint")
                                        ?: twoRow.getAsJsonObject("overlay")
                                            ?.getAsJsonObject("musicItemHoverOverlayRenderer")
                                            ?.getAsJsonObject("content")
                                            ?.getAsJsonObject("musicPlayButtonRenderer")
                                            ?.getAsJsonObject("playNavigationEndpoint")

                                    val watchEndpoint = navEndpoint?.getAsJsonObject("watchEndpoint")
                                    if (watchEndpoint != null) {
                                        if (watchEndpoint.has("videoId")) {
                                            videoId = watchEndpoint.get("videoId").asString
                                        }
                                        if (watchEndpoint.has("playlistId")) {
                                            watchPlaylistId = watchEndpoint.get("playlistId").asString
                                        }
                                    }

                                    val browseEndpoint = navEndpoint?.getAsJsonObject("browseEndpoint")
                                    val itemBrowseId = browseEndpoint?.get("browseId")?.asString ?: ""

                                    var thumbUrl = ""
                                    val thumbArray = twoRow.getAsJsonObject("thumbnailRenderer")
                                        ?.getAsJsonObject("musicThumbnailRenderer")
                                        ?.getAsJsonObject("thumbnail")
                                        ?.getAsJsonArray("thumbnails")
                                        ?: twoRow.getAsJsonObject("thumbnail")
                                            ?.getAsJsonObject("musicThumbnailRenderer")
                                            ?.getAsJsonObject("thumbnail")
                                            ?.getAsJsonArray("thumbnails")
                                        ?: twoRow.getAsJsonObject("thumbnail")
                                            ?.getAsJsonObject("croppedSquareThumbnailRenderer")
                                            ?.getAsJsonObject("thumbnail")
                                            ?.getAsJsonArray("thumbnails")
                                        ?: twoRow.getAsJsonObject("leftThumbnail")
                                            ?.getAsJsonObject("musicThumbnailRenderer")
                                            ?.getAsJsonObject("thumbnail")
                                            ?.getAsJsonArray("thumbnails")

                                    if (thumbArray != null && thumbArray.size() > 0) {
                                        thumbUrl = thumbArray.get(thumbArray.size() - 1).asJsonObject.get("url")?.asString ?: ""
                                    }

                                    val subtitleRuns = twoRow.getAsJsonObject("subtitle")?.getAsJsonArray("runs")
                                        ?: twoRow.getAsJsonObject("longBylineText")?.getAsJsonArray("runs")
                                    val authorText = subtitleRuns?.mapNotNull { it.asJsonObject.get("text")?.asString }?.joinToString("")
                                        ?: run {
                                            val flexCols = twoRow.getAsJsonArray("flexColumns")
                                            if (flexCols != null && flexCols.size() > 1) {
                                                val runs = flexCols.get(1).asJsonObject
                                                    ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                                                    ?.getAsJsonObject("text")?.getAsJsonArray("runs")
                                                runs?.mapNotNull { it.asJsonObject.get("text")?.asString }?.joinToString("")
                                            } else null
                                        }
                                        ?: "YouTube Music"

                                    val finalId = when {
                                        videoId.length == 11 -> videoId
                                        watchPlaylistId.isNotBlank() -> watchPlaylistId
                                        itemBrowseId.isNotBlank() -> itemBrowseId
                                        thumbUrl.contains("/vi/") -> {
                                            val extracted = thumbUrl.substringAfter("/vi/").substringBefore("/")
                                            if (extracted.length == 11) extracted else "item_${titleText.hashCode()}"
                                        }
                                        titleText.isNotBlank() -> "item_${titleText.hashCode()}"
                                        else -> ""
                                    }

                                    if (finalId.isNotBlank() && titleText.isNotBlank()) {
                                        val finalThumb = when {
                                            thumbUrl.isNotBlank() -> thumbUrl
                                            videoId.length == 11 -> "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                            else -> "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500"
                                        }
                                        songsList.add(Song(id = finalId, title = titleText, author = authorText, thumbnail = finalThumb, durationSeconds = 210))
                                    }
                                }

                                if (songsList.isNotEmpty()) {
                                    shelves.add(HomeShelf(title = title, style = ShelfLayoutStyle.CAROUSEL_SQUARE, songs = songsList))
                                }
                            }
                            Log.d(TAG, "Continuation page $pageCount added ${shelves.size - newShelvesCountBefore} shelves. Next token: ${nextToken?.take(20)}...")
                        }
                    }
                }
                Log.d(TAG, "Completed browse shelves fetch ($browseId, params=$params): Total ${shelves.size} shelves")
            }
        }.onFailure { e ->
            Log.e(TAG, "Error fetching YouTube Music browse shelves ($browseId, $params): ${e.message}")
        }

        if (shelves.isNotEmpty()) {
            browseShelvesCache[cacheKey] = BrowseCacheEntry(System.currentTimeMillis(), shelves)
            Log.d(TAG, "Cached ${shelves.size} shelves for key=$cacheKey (12 hr TTL)")
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

    suspend fun fetchPodcastTopicShelves(): List<HomeShelf> = withContext(Dispatchers.IO) {
        val topics = listOf(
            "Films" to "Films podcast",
            "Motivation" to "Motivation podcast",
            "Business" to "Business podcast",
            "Marketing" to "Marketing podcast",
            "Lifestyle" to "Lifestyle podcast",
            "Science" to "Science podcast",
            "Finances" to "Finances podcast",
            "Spirituality" to "Spirituality podcast",
            "Fiction" to "Fiction podcast",
            "History" to "History podcast",
            "Literature" to "Literature podcast",
            "Government" to "Government podcast",
            "News" to "News podcast",
            "Politics" to "Politics podcast",
            "Comedy" to "Comedy podcast",
            "Horoscopes" to "Horoscopes podcast",
            "Astrology" to "Astrology podcast",
            "Religion" to "Religion podcast",
            "Popular Shows" to "Popular shows podcast"
        )

        coroutineScope {
            topics.map { (title, query) ->
                async {
                    val rawSongs = searchSongs(query)
                    val podcastSongs = rawSongs.filter { song ->
                        val t = song.title.lowercase()
                        !t.contains("jukebox") && !t.contains("compilation") && !t.contains("non stop")
                    }
                    if (podcastSongs.isNotEmpty()) {
                        HomeShelf(
                            title = title,
                            songs = podcastSongs,
                            style = ShelfLayoutStyle.CAROUSEL_SQUARE
                        )
                    } else null
                }
            }.mapNotNull { it.await() }
        }
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

    fun parseViewCount(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val clean = raw.lowercase(java.util.Locale.ROOT)
            .replace("views", "")
            .replace("view", "")
            .replace("plays", "")
            .replace("play", "")
            .replace(",", "")
            .trim()
        val multiplier = when {
            clean.endsWith("b") -> 1_000_000_000.0
            clean.endsWith("m") -> 1_000_000.0
            clean.endsWith("k") -> 1_000.0
            else -> 1.0
        }
        val digits = clean.replace("[^0-9.]".toRegex(), "")
        val num = digits.toDoubleOrNull() ?: return 0L
        return (num * multiplier).toLong()
    }

    private fun rankSongsByRelevance(query: String, songs: List<Song>): List<Song> {
        val q = query.trim().lowercase(java.util.Locale.ROOT)
        if (q.isBlank() || songs.isEmpty()) return songs

        return songs.distinctBy { it.id }.sortedWith(
            compareByDescending<Song> { it.views }
                .thenByDescending { song ->
                    val t = song.title.lowercase(java.util.Locale.ROOT)
                    if (t.contains("official music video")) 3
                    else if (t.contains("official video")) 2
                    else if (t.contains("official audio")) 1
                    else 0
                }
        )
    }

    suspend fun fetchYouTubeMusicSearch(query: String, params: String? = null): List<Song> = withContext(Dispatchers.IO) {
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
                    val sectionObj = sectionElem.asJsonObject

                    // 1. YouTube Music Official Top Result Card (musicCardShelfRenderer)
                    runCatching {
                        val cardShelf = sectionObj.getAsJsonObject("musicCardShelfRenderer")
                        if (cardShelf != null) {
                            val cardTitle = cardShelf.getAsJsonObject("title")
                                ?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString ?: ""

                            var cardVideoId = ""
                            val cardWatchEndpoint = cardShelf.getAsJsonObject("onTap")?.getAsJsonObject("watchEndpoint")
                                ?: cardShelf.getAsJsonArray("buttons")?.get(0)?.asJsonObject
                                    ?.getAsJsonObject("buttonRenderer")?.getAsJsonObject("command")
                                    ?.getAsJsonObject("watchEndpoint")
                            if (cardWatchEndpoint != null && cardWatchEndpoint.has("videoId")) {
                                cardVideoId = cardWatchEndpoint.get("videoId").asString
                            }

                            val cardSubtitleRuns = cardShelf.getAsJsonObject("subtitle")?.getAsJsonArray("runs")
                            var cardAuthor = "Artist"
                            if (cardSubtitleRuns != null) {
                                cardAuthor = cardSubtitleRuns.mapNotNull { it.asJsonObject.get("text")?.asString }.joinToString("")
                            }

                            val cardThumb = cardShelf.getAsJsonObject("thumbnail")
                                ?.getAsJsonObject("musicThumbnailRenderer")
                                ?.getAsJsonObject("thumbnail")
                                ?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject?.get("url")?.asString
                                ?: if (cardVideoId.length == 11) "https://i.ytimg.com/vi/$cardVideoId/hqdefault.jpg" else ""

                            if (cardVideoId.length == 11 && cardTitle.isNotBlank() && !songs.any { it.id == cardVideoId }) {
                                songs.add(Song(id = cardVideoId, title = cardTitle, author = cardAuthor, thumbnail = cardThumb, durationSeconds = 210))
                            }
                        }
                    }

                    // 2. YouTube Music Categorized Shelves (musicShelfRenderer)
                    val musicShelf = sectionObj.getAsJsonObject("musicShelfRenderer") ?: continue
                    val items = musicShelf.getAsJsonArray("contents") ?: continue

                    for (itemElem in items) {
                        runCatching {
                            val responsiveItem = itemElem.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer") ?: return@runCatching
                            val flexColumns = responsiveItem.getAsJsonArray("flexColumns") ?: return@runCatching
                            if (flexColumns.size() == 0) return@runCatching

                            val titleCol = flexColumns.get(0).asJsonObject
                                .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.getAsJsonObject("text")
                                ?.getAsJsonArray("runs")
                            val title = titleCol?.get(0)?.asJsonObject?.get("text")?.asString ?: return@runCatching

                            var videoId = ""

                            // A. Check playlistItemData
                            val playlistItemData = responsiveItem.getAsJsonObject("playlistItemData")
                            if (playlistItemData != null && playlistItemData.has("videoId")) {
                                videoId = playlistItemData.get("videoId").asString
                            }

                            // B. Check overlay playNavigationEndpoint
                            if (videoId.length != 11) {
                                val overlay = responsiveItem.getAsJsonObject("overlay")
                                    ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
                                    ?.getAsJsonObject("content")
                                    ?.getAsJsonObject("musicPlayButtonRenderer")
                                    ?.getAsJsonObject("playNavigationEndpoint")
                                    ?.getAsJsonObject("watchEndpoint")
                                if (overlay != null && overlay.has("videoId")) {
                                    videoId = overlay.get("videoId").asString
                                }
                            }

                            // C. Check navigationEndpoint
                            if (videoId.length != 11) {
                                val nav = responsiveItem.getAsJsonObject("navigationEndpoint")
                                    ?: responsiveItem.getAsJsonObject("overlay")
                                        ?.getAsJsonObject("musicItemHoverOverlayRenderer")
                                        ?.getAsJsonObject("content")
                                        ?.getAsJsonObject("musicPlayButtonRenderer")
                                        ?.getAsJsonObject("playNavigationEndpoint")
                                val watchEndpoint = nav?.getAsJsonObject("watchEndpoint")
                                if (watchEndpoint != null && watchEndpoint.has("videoId")) {
                                    videoId = watchEndpoint.get("videoId").asString
                                }
                            }

                            // D. Check title runs navigationEndpoint
                            if (videoId.length != 11 && titleCol != null) {
                                for (runElem in titleCol) {
                                    val runNav = runElem.asJsonObject.getAsJsonObject("navigationEndpoint")?.getAsJsonObject("watchEndpoint")
                                    if (runNav != null && runNav.has("videoId")) {
                                        videoId = runNav.get("videoId").asString
                                        break
                                    }
                                }
                            }

                            // E. Check thumbnail URL fallback
                            if (videoId.length != 11) {
                                val thumbUrl = responsiveItem.getAsJsonObject("thumbnail")
                                    ?.getAsJsonObject("musicThumbnailRenderer")
                                    ?.getAsJsonObject("thumbnail")
                                    ?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject?.get("url")?.asString ?: ""
                                if (thumbUrl.contains("/vi/")) {
                                    val cand = thumbUrl.substringAfter("/vi/").substringBefore("/")
                                    if (cand.length == 11) videoId = cand
                                }
                            }

                            if (videoId.length != 11) return@runCatching
                            if (songs.any { it.id == videoId }) return@runCatching

                            var author = "Artist"
                            var parsedViews = 0L
                            if (flexColumns.size() > 1) {
                                val authorRuns = flexColumns.get(1).asJsonObject
                                    .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                                    ?.getAsJsonObject("text")
                                    ?.getAsJsonArray("runs")
                                if (authorRuns != null && authorRuns.size() > 0) {
                                    for (runElem in authorRuns) {
                                        val t = runElem.asJsonObject.get("text")?.asString?.trim() ?: continue
                                        if (t.contains("views", ignoreCase = true) || t.contains("plays", ignoreCase = true)) {
                                            parsedViews = parseViewCount(t)
                                        } else if (t.isNotBlank() && t != "•" && t != "Song" && t != "Video" && t != "Explicit" && !t.matches("^\\d+:\\d+(:\\d+)?$".toRegex())) {
                                            if (author == "Artist") {
                                                author = t
                                            }
                                        }
                                    }
                                }
                            }

                            val thumbnail = responsiveItem.getAsJsonObject("thumbnail")
                                ?.getAsJsonObject("musicThumbnailRenderer")
                                ?.getAsJsonObject("thumbnail")
                                ?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject?.get("url")?.asString
                                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                            songs.add(Song(id = videoId, title = title, author = author, thumbnail = thumbnail, durationSeconds = 210, views = parsedViews))
                        }
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

                // 1. Attempt to extract ytInitialData JSON block from HTML
                val initialDataMarker = "var ytInitialData = "
                val startIndex = html.indexOf(initialDataMarker)
                if (startIndex != -1) {
                    val jsonStart = startIndex + initialDataMarker.length
                    val jsonEnd = html.indexOf(";</script>", jsonStart)
                    if (jsonEnd != -1) {
                        val jsonStr = html.substring(jsonStart, jsonEnd)
                        val rootObj = JsonParser.parseString(jsonStr).asJsonObject
                        val contents = rootObj.getAsJsonObject("contents")
                            ?.getAsJsonObject("twoColumnSearchResultsRenderer")
                            ?.getAsJsonObject("primaryContents")
                            ?.getAsJsonObject("sectionListRenderer")
                            ?.getAsJsonArray("contents")

                        if (contents != null) {
                            for (sec in contents) {
                                val itemSection = sec.asJsonObject.getAsJsonObject("itemSectionRenderer") ?: continue
                                val items = itemSection.getAsJsonArray("contents") ?: continue
                                for (item in items) {
                                    val videoRenderer = item.asJsonObject.getAsJsonObject("videoRenderer") ?: continue
                                    val videoId = videoRenderer.get("videoId")?.asString ?: continue
                                    if (videoId.length != 11 || songs.any { it.id == videoId }) continue

                                    val titleRuns = videoRenderer.getAsJsonObject("title")?.getAsJsonArray("runs")
                                    val title = titleRuns?.get(0)?.asJsonObject?.get("text")?.asString ?: continue
                                    if (title.matches("^\\d+:\\d+(:\\d+)?$".toRegex()) || title.endsWith("views")) continue

                                    val ownerRuns = videoRenderer.getAsJsonObject("ownerText")?.getAsJsonArray("runs")
                                        ?: videoRenderer.getAsJsonObject("longBylineText")?.getAsJsonArray("runs")
                                    val author = ownerRuns?.get(0)?.asJsonObject?.get("text")?.asString ?: "Artist"

                                    val thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                    songs.add(Song(id = videoId, title = title, author = author, thumbnail = thumbnail, durationSeconds = 210))
                                    if (songs.size >= 15) break
                                }
                                if (songs.size >= 15) break
                            }
                        }
                    }
                }

                // 2. Regex fallback (with strict timestamp filtering and ownerText extraction)
                if (songs.isEmpty()) {
                    val matcher = java.util.regex.Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\".*?\"title\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"\\}\\]\\}.*?\"ownerText\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"\\}\\]\\}").matcher(html)
                    while (matcher.find() && songs.size < 15) {
                        val id = matcher.group(1) ?: continue
                        val title = matcher.group(2).orEmpty()
                        val author = matcher.group(3).orEmpty().ifBlank { "Artist" }
                        if (id.length == 11 && !songs.any { it.id == id }) {
                            if (title.isNotBlank() && !title.matches("^\\d+:\\d+(:\\d+)?$".toRegex()) && !title.endsWith("views") && !title.contains("ago")) {
                                songs.add(
                                    Song(
                                        id = id,
                                        title = title,
                                        author = author,
                                        thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                                        durationSeconds = 210
                                    )
                                )
                            }
                        }
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

        // Tier 0: Direct YouTube Music InnerTube Search (Filtered by Songs: "egWKAQI=")
        val ytMusicSongs = fetchYouTubeMusicSearch(trimmed, params = "egWKAQI=")
        if (ytMusicSongs.size >= 3) {
            val ytMusicGeneral = runCatching { fetchYouTubeMusicSearch(trimmed, params = null) }.getOrElse { emptyList() }
            val topCard = ytMusicGeneral.firstOrNull { generalSong -> ytMusicSongs.none { it.id == generalSong.id } }
            val finalSongs = if (topCard != null) listOf(topCard) + ytMusicSongs else ytMusicSongs
            val result = finalSongs.distinctBy { it.id }
            searchCache.put(cacheKey, result)
            return@withContext result
        }

        // Tier 1: YouTube Music General Search (Unfiltered fallback)
        val ytMusicGeneral = fetchYouTubeMusicSearch(trimmed, params = null)
        if (ytMusicGeneral.isNotEmpty()) {
            val combined = (ytMusicGeneral + ytMusicSongs).distinctBy { it.id }
            if (combined.size >= 3) {
                searchCache.put(cacheKey, combined)
                return@withContext combined
            }
        }

        // Tier 2: Live Server Search API Endpoint Fallback
        val webResults = fetchWebSearchSongs(trimmed)
        if (webResults.isNotEmpty()) {
            val combined = (ytMusicSongs + webResults).distinctBy { it.id }
            searchCache.put(cacheKey, combined)
            return@withContext combined
        }

        // Tier 3: HTML Scrape Fallback
        val htmlResults = fetchHtmlYoutubeSearch(trimmed)
        if (htmlResults.isNotEmpty()) {
            val combined = (ytMusicSongs + htmlResults).distinctBy { it.id }
            searchCache.put(cacheKey, combined)
            return@withContext combined
        }

        ytMusicSongs.ifEmpty { ytMusicGeneral }
    }

    sealed class SuggestionItem {
        data class QuerySuggestion(val query: String, val isHistory: Boolean = false) : SuggestionItem()
        data class SongSuggestion(val song: Song) : SuggestionItem()
        data class ArtistSuggestion(val artistName: String, val avatarUrl: String?, val subtitle: String = "Artist") : SuggestionItem()
    }

    suspend fun fetchSearchSuggestions(input: String): List<SuggestionItem> = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val nativeSuggestions = runCatching {
            val payloadObj = com.google.gson.JsonObject().apply {
                addProperty("input", trimmed)
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
                .url("https://music.youtube.com/youtubei/v1/music/get_search_suggestions")
                .post(payloadObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
                .header("Origin", "https://music.youtube.com")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val json = JsonParser.parseString(body).asJsonObject

                val contents = json.getAsJsonArray("contents") ?: return@runCatching null
                val itemsList = mutableListOf<SuggestionItem>()

                for (sec in contents) {
                    val sectionRenderer = sec.asJsonObject.getAsJsonObject("searchSuggestionsSectionRenderer") ?: continue
                    val contentsArray = sectionRenderer.getAsJsonArray("contents") ?: continue

                    for (itemElem in contentsArray) {
                        val itemObj = itemElem.asJsonObject
                        val suggestionRenderer = itemObj.getAsJsonObject("searchSuggestionRenderer")
                        if (suggestionRenderer != null) {
                            val text = suggestionRenderer.getAsJsonObject("suggestion")
                                ?.getAsJsonArray("runs")?.joinToString("") { it.asJsonObject.get("text").asString } ?: ""
                            if (text.isNotBlank()) {
                                itemsList.add(SuggestionItem.QuerySuggestion(text, isHistory = false))
                            }
                            continue
                        }

                        val responsiveItem = itemObj.getAsJsonObject("musicResponsiveListItemRenderer") ?: continue
                        val flexColumns = responsiveItem.getAsJsonArray("flexColumns") ?: continue
                        if (flexColumns.size() == 0) continue

                        val titleText = flexColumns.get(0).asJsonObject
                            .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonObject("text")
                            ?.getAsJsonArray("runs")?.joinToString("") { it.asJsonObject.get("text").asString } ?: ""

                        var subtitleText = ""
                        if (flexColumns.size() > 1) {
                            subtitleText = flexColumns.get(1).asJsonObject
                                .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.getAsJsonObject("text")
                                ?.getAsJsonArray("runs")?.joinToString("") { it.asJsonObject.get("text").asString } ?: ""
                        }

                        val thumbUrl = responsiveItem.getAsJsonObject("thumbnail")
                            ?.getAsJsonObject("musicThumbnailRenderer")
                            ?.getAsJsonObject("thumbnail")
                            ?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject?.get("url")?.asString

                        var videoId = ""
                        val navEndpoint = responsiveItem.getAsJsonObject("navigationEndpoint")
                        val watchEndpoint = navEndpoint?.getAsJsonObject("watchEndpoint")
                        if (watchEndpoint != null && watchEndpoint.has("videoId")) {
                            videoId = watchEndpoint.get("videoId").asString
                        }

                        if (videoId.length == 11) {
                            val songObj = Song(
                                id = videoId,
                                title = titleText,
                                author = subtitleText.ifBlank { "Song" },
                                thumbnail = thumbUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                durationSeconds = 210
                            )
                            itemsList.add(SuggestionItem.SongSuggestion(songObj))
                        } else if (subtitleText.lowercase().contains("artist") || subtitleText.lowercase().contains("audience") || subtitleText.lowercase().contains("subscribers")) {
                            itemsList.add(SuggestionItem.ArtistSuggestion(artistName = titleText, avatarUrl = thumbUrl, subtitle = subtitleText))
                        } else if (titleText.isNotBlank()) {
                            itemsList.add(SuggestionItem.QuerySuggestion(titleText, isHistory = false))
                        }
                    }
                }
                itemsList
            }
        }.getOrNull()

        if (!nativeSuggestions.isNullOrEmpty()) {
            return@withContext nativeSuggestions.distinctBy { 
                when (it) {
                    is SuggestionItem.QuerySuggestion -> it.query
                    is SuggestionItem.SongSuggestion -> it.song.id
                    is SuggestionItem.ArtistSuggestion -> it.artistName
                }
            }.take(15)
        }

        val fallbackSongs = searchSongs(trimmed).take(5)
        val fallbackItems = mutableListOf<SuggestionItem>()
        fallbackItems.add(SuggestionItem.QuerySuggestion(trimmed, isHistory = false))
        fallbackSongs.forEach { fallbackItems.add(SuggestionItem.SongSuggestion(it)) }
        fallbackItems
    }

    data class ArtistSearchResult(
        val artistName: String,
        val avatarUrl: String?,
        val browseId: String? = null,
        val topSongs: List<Song> = emptyList(),
        val matchPercentage: Float = 0f
    )

    data class UnifiedSearchResult(
        val query: String,
        val isArtistSearch: Boolean,
        val detectedArtist: ArtistSearchResult? = null,
        val songs: List<Song> = emptyList()
    )

    fun levenshteinDistance(s1: String, s2: String): Int {
        val a = s1.lowercase(java.util.Locale.ROOT)
        val b = s2.lowercase(java.util.Locale.ROOT)
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev else minOf(prev, dp[j], dp[j - 1]) + 1
                prev = temp
            }
        }
        return dp[b.length]
    }

    private fun extractArtistCandidatesFromSong(song: Song): List<String> {
        val candidates = mutableListOf<String>()

        // 1. Author field
        val author = song.author.trim()
        if (author.isNotBlank() && !author.equals("Artist", ignoreCase = true) && !author.equals("YouTube", ignoreCase = true)) {
            candidates.add(author)
        }

        // 2. Split title by common YouTube delimiters: |, -, ,, &, /, (, ), ft., feat.
        val titleClean = song.title
            .replace(Regex("(?i)\\b(official|video|audio|lyric|lyrics|full song|mv|hd|4k|visualizer|remix|cover)\\b"), "")

        val segments = titleClean.split(Regex("[|\\-,&/()\\\\]|(?i)\\b(feat|ft|by|with|x)\\b"))
        for (seg in segments) {
            val s = seg.trim()
            if (s.length >= 3 && !s.matches("^\\d+.*$".toRegex())) {
                candidates.add(s)
            }
        }
        return candidates
    }

    fun isFuzzyArtistMatch(query: String, artistName: String): Boolean {
        val q = query.trim().lowercase(java.util.Locale.ROOT)
        val a = artistName.trim().lowercase(java.util.Locale.ROOT)
        if (q.isBlank() || a.isBlank()) return false

        // 1. Substring or word boundary match
        if (a.contains(q) || q.contains(a)) return true

        val queryWords = q.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val artistWords = a.split("\\s+".toRegex()).filter { it.isNotBlank() }

        // 2. Word token fuzzy match (e.g. query "arjit" matches word "arijit" in "Arijit Singh")
        for (qWord in queryWords) {
            for (aWord in artistWords) {
                if (levenshteinDistance(qWord, aWord) <= 1) return true
            }
        }

        // 3. Full name edit distance <= 3
        if (levenshteinDistance(q, a) <= 3) return true

        return false
    }

    suspend fun searchUnified(
        query: String,
        selectedArtistSuggestion: SuggestionItem.ArtistSuggestion? = null
    ): UnifiedSearchResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext UnifiedSearchResult(query = "", isArtistSearch = false)
        }

        val songs = searchSongs(trimmed)

        // 1. Direct explicit artist suggestion passed from autocomplete dropdown
        if (selectedArtistSuggestion != null) {
            val artistName = selectedArtistSuggestion.artistName
            val avatarUrl = selectedArtistSuggestion.avatarUrl.takeIf { !it.isNullOrBlank() } ?: getArtistAvatarUrl(artistName)
            val artistTopSongs = songs.filter {
                it.author.contains(artistName, ignoreCase = true) ||
                it.title.contains(artistName, ignoreCase = true)
            }.ifEmpty { songs.take(10) }

            val artistResult = ArtistSearchResult(
                artistName = artistName,
                avatarUrl = avatarUrl,
                topSongs = artistTopSongs,
                matchPercentage = 1.0f
            )

            return@withContext UnifiedSearchResult(
                query = trimmed,
                isArtistSearch = true,
                detectedArtist = artistResult,
                songs = songs
            )
        }

        // 2. Check get_search_suggestions API for an exact artist entity match
        val suggestions = fetchSearchSuggestions(trimmed)
        val matchedArtist = suggestions.filterIsInstance<SuggestionItem.ArtistSuggestion>().firstOrNull {
            it.artistName.equals(trimmed, ignoreCase = true) ||
            it.artistName.contains(trimmed, ignoreCase = true) ||
            trimmed.contains(it.artistName, ignoreCase = true)
        }

        if (matchedArtist != null) {
            val artistName = matchedArtist.artistName
            val avatarUrl = matchedArtist.avatarUrl.takeIf { !it.isNullOrBlank() } ?: getArtistAvatarUrl(artistName)
            val artistTopSongs = songs.filter {
                it.author.contains(artistName, ignoreCase = true) ||
                it.title.contains(artistName, ignoreCase = true)
            }.ifEmpty { songs.take(10) }

            val artistResult = ArtistSearchResult(
                artistName = artistName,
                avatarUrl = avatarUrl,
                topSongs = artistTopSongs,
                matchPercentage = 1.0f
            )

            return@withContext UnifiedSearchResult(
                query = trimmed,
                isArtistSearch = true,
                detectedArtist = artistResult,
                songs = songs
            )
        }

        return@withContext UnifiedSearchResult(
            query = trimmed,
            isArtistSearch = false,
            detectedArtist = null,
            songs = songs
        )
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
        if (key.isBlank()) return@withContext ""
        artistAvatarCache[key]?.let { return@withContext it }

        // Tier 1: YouTube Music Artists Filter Search ("EgWKAQIUAWoKEAkQBRAKEAMQBA==")
        val ytMusicAvatar = runCatching {
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

                for (sec in contents) {
                    val musicShelf = sec.asJsonObject.getAsJsonObject("musicShelfRenderer") ?: continue
                    val items = musicShelf.getAsJsonArray("contents") ?: continue
                    for (item in items) {
                        val responsiveItem = item.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer")
                            ?: item.asJsonObject.getAsJsonObject("musicTwoRowItemRenderer") ?: continue
                        val thumbnailObj = responsiveItem.getAsJsonObject("thumbnail")
                            ?.getAsJsonObject("musicThumbnailRenderer")
                            ?.getAsJsonObject("thumbnail")
                            ?: responsiveItem.getAsJsonObject("thumbnail")
                        val thumbs = thumbnailObj?.getAsJsonArray("thumbnails")
                        if (thumbs != null && thumbs.size() > 0) {
                            var bestUrl = thumbs.get(thumbs.size() - 1).asJsonObject.get("url").asString
                            if (bestUrl.startsWith("//")) bestUrl = "https:$bestUrl"
                            bestUrl = bestUrl.replace(Regex("=s\\d+"), "=s512").replace(Regex("=w\\d+-h\\d+"), "=w512-h512")
                            return@runCatching bestUrl
                        }
                    }
                }
                null
            }
        }.getOrNull()

        if (!ytMusicAvatar.isNullOrBlank()) {
            artistAvatarCache[key] = ytMusicAvatar
            return@withContext ytMusicAvatar
        }

        // Tier 2: YouTube Standard Channel Search ("EgIQAg==" fixed params)
        val ytChannelAvatar = runCatching {
            val payloadObj = com.google.gson.JsonObject().apply {
                addProperty("query", artistName)
                addProperty("params", "EgIQAg==")
                val contextObj = com.google.gson.JsonObject().apply {
                    val clientObj = com.google.gson.JsonObject().apply {
                        addProperty("clientName", "WEB")
                        addProperty("clientVersion", "2.20240101.00.00")
                        addProperty("hl", "en")
                        addProperty("gl", "US")
                    }
                    add("client", clientObj)
                }
                add("context", contextObj)
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search")
                .post(payloadObj.toString().toRequestBody(JSON_MEDIA_TYPE))
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
                        val thumbs = channelRenderer.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                        if (thumbs != null && thumbs.size() > 0) {
                            var url = thumbs.get(thumbs.size() - 1).asJsonObject.get("url").asString
                            if (url.startsWith("//")) url = "https:$url"
                            url = url.replace(Regex("=s\\d+"), "=s512")
                            return@runCatching url
                        }
                    }
                }
                null
            }
        }.getOrNull()

        val finalAvatar = if (!ytChannelAvatar.isNullOrBlank() && ytChannelAvatar.startsWith("http")) {
            ytChannelAvatar
        } else {
            fetchYouTubeMusicSearch(artistName).firstOrNull()?.thumbnail ?: ""
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
                      "clientName": "ANDROID",
                      "clientVersion": "19.29.37",
                      "hl": "en",
                      "gl": "US"
                    }
                  }
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "com.google.android.youtube/19.29.37 (Linux; U; Android 14)")
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

    fun resolveWithYtDlp(
        context: Context, 
        videoId: String, 
        isVideo: Boolean = false, 
        quality: String = "720p",
        playerClient: String = "web"
    ): StreamingResult? {
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
                    quality.contains("2160") || quality.contains("4K", ignoreCase = true) -> 
                        "bestvideo[height<=2160]+bestaudio/bestvideo+bestaudio"
                    quality.contains("1440") || quality.contains("2K", ignoreCase = true) -> 
                        "bestvideo[height<=1440]+bestaudio/bestvideo+bestaudio"
                    quality.contains("1080") -> 
                        "bestvideo[height<=1080]+bestaudio/bestvideo+bestaudio"
                    quality.contains("720") -> 
                        "bestvideo[height<=720]+bestaudio/bestvideo+bestaudio"
                    quality.contains("480") -> 
                        "bestvideo[height<=480]+bestaudio/bestvideo+bestaudio"
                    quality.contains("360") -> 
                        "bestvideo[height<=360]+bestaudio/bestvideo+bestaudio"
                    else -> 
                        "bestvideo+bestaudio"
                }
            } else {
                "140/139/bestaudio[ext=m4a]/bestaudio/best"
            }

            val extractorArgs = "youtube:player_client=$playerClient"

            val request = com.yausername.youtubedl_android.YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId").apply {
                addOption("-f", formatOption)
                addOption("-g")
                addOption("--no-playlist")
                addOption("-4")
                addOption("--extractor-args", extractorArgs)
            }
            Log.d("SPASIBA_111", "[YT_DLP_EXEC] Executing yt-dlp for videoId=$videoId client='$playerClient' format='$formatOption'")
            val response = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
            val httpLines = response.out.lines().map { it.trim() }.filter { it.startsWith("http") }
            if (httpLines.isNotEmpty()) {
                val mainUrl = httpLines[0]
                val secUrl = if (httpLines.size > 1) httpLines[1] else null
                val ytUa = when {
                    mainUrl.contains("c=ANDROID_VR") -> "com.google.android.apps.youtube.vr.oculus/1.59.19 (Linux; U; Android 12)"
                    mainUrl.contains("c=ANDROID_TESTSUITE") -> "com.google.android.youtube/1.9.0 (Linux; U; Android 14)"
                    mainUrl.contains("c=ANDROID_MUSIC") -> "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; gzip)"
                    mainUrl.contains("c=ANDROID") -> "com.google.android.youtube/19.29.37 (Linux; U; Android 14)"
                    mainUrl.contains("c=IOS") -> "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X)"
                    mainUrl.contains("c=TVHTML5") || mainUrl.contains("c=TV") -> "Mozilla/5.0 (SmartHub; SMART-TV; U; Linux/SmartTV) AppleWebKit/537.42"
                    mainUrl.contains("c=WEB_EMBEDDED") || mainUrl.contains("c=WEB") -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    else -> "com.google.android.youtube/19.29.37 (Linux; U; Android 14)"
                }
                val headers = mutableMapOf("User-Agent" to ytUa)
                if (mainUrl.contains("c=WEB_EMBEDDED") || mainUrl.contains("c=WEB")) {
                    headers["Origin"] = "https://www.youtube.com"
                    headers["Referer"] = "https://www.youtube.com/"
                }
                StreamingResult(
                    url = mainUrl,
                    audioUrl = secUrl,
                    headers = headers
                )
            } else {
                null
            }
        }.onFailure {
            Log.d("SPASIBA_111", "[YT_DLP_ERR] yt-dlp ($playerClient) failed for videoId=$videoId: ${it.message}")
        }.getOrNull()
    }

    fun probeStreamUrl(url: String, headers: Map<String, String>): Boolean {
        if (url.startsWith("/")) {
            return java.io.File(url).exists()
        }
        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1")
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            client.newCall(reqBuilder.build()).execute().use { response ->
                val code = response.code
                code == 200 || code == 206
            }
        } catch (e: Exception) {
            false
        }
    }

    val backupRacerClients = listOf(
        "YT_DLP_WEB" to "web",
        "YT_DLP_MWEB" to "mweb",
        "YT_DLP_IOS" to "ios"
    )

    suspend fun resolveStreamResult(context: Context, videoId: String): StreamingResult? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.i("SPASIBA_111", "🎯 ╔══════════════════════════════════════════════════════════════════════")
        Log.i("SPASIBA_111", "🎯 ║ 🥇 [CHAMPION_RESOLVE_START] Resolving audio via 'YT_DLP_ANDROID' for '$videoId'")
        Log.i("SPASIBA_111", "🎯 ╚══════════════════════════════════════════════════════════════════════")

        // 1. Primary Champion (0ms probe delay - instant dispatch to ExoPlayer!)
        val championResult = resolveWithYtDlp(context, videoId, isVideo = false, playerClient = "android")
        if (championResult != null && championResult.url.isNotBlank()) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.i("SPASIBA_111", "🏆 ╔══════════════════════════════════════════════════════════════════════")
            Log.i("SPASIBA_111", "🏆 ║ 🥇 [CHAMPION_RESOLVE_SUCCESS]")
            Log.i("SPASIBA_111", "🏆 ║ Winner: YT_DLP_ANDROID (Client: android)")
            Log.i("SPASIBA_111", "🏆 ║ ⚡ Extracted in ${elapsed}ms -> Direct Dispatch to ExoPlayer (0ms probe!)")
            Log.i("SPASIBA_111", "🏆 ╚══════════════════════════════════════════════════════════════════════")
            return@withContext championResult
        }

        // 2. Backup Squadron (Only if Champion fails)
        Log.w("SPASIBA_111", "🛡️ ╔══════════════════════════════════════════════════════════════════════")
        Log.w("SPASIBA_111", "🛡️ ║ [BACKUP_SQUADRON_TRIGGERED] Champion missed. Racing: [YT_DLP_WEB, YT_DLP_MWEB, YT_DLP_IOS]")
        Log.w("SPASIBA_111", "🛡️ ╚══════════════════════════════════════════════════════════════════════")

        coroutineScope {
            val winnerChannel = kotlinx.coroutines.channels.Channel<StreamingResult>(1)
            val isWon = java.util.concurrent.atomic.AtomicBoolean(false)
            val racingJobs = mutableListOf<kotlinx.coroutines.Job>()

            backupRacerClients.forEach { (racerName, clientParam) ->
                val job = launch(Dispatchers.IO) {
                    val racerStart = System.currentTimeMillis()
                    try {
                        if (isWon.get()) return@launch
                        val backupResult = resolveWithYtDlp(context, videoId, isVideo = false, playerClient = clientParam)
                        if (backupResult != null && backupResult.url.isNotBlank() && !isWon.get()) {
                            val fetchTime = System.currentTimeMillis() - racerStart
                            Log.d("SPASIBA_111", "🏃 [BACKUP_FETCH] $racerName extracted stream URL in ${fetchTime}ms. Testing live 2-byte probe...")
                            val probeStart = System.currentTimeMillis()
                            val isProbePassed = probeStreamUrl(backupResult.url, backupResult.headers)
                            val probeElapsed = System.currentTimeMillis() - probeStart

                            if (isProbePassed) {
                                if (isWon.compareAndSet(false, true)) {
                                    val totalRacerTime = System.currentTimeMillis() - racerStart
                                    Log.i("SPASIBA_111", "🏆 [BACKUP_WINNER] $racerName won fallback in ${totalRacerTime}ms (Probe OK)")
                                    winnerChannel.trySend(backupResult)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Cancelled cleanly
                    }
                }
                racingJobs.add(job)
            }

            val fallbackWinner = try {
                winnerChannel.receive()
            } catch (e: Exception) {
                null
            }

            racingJobs.forEach { it.cancel() }
            winnerChannel.close()
            fallbackWinner
        }
    }

    suspend fun resolveVideoStreamResult(context: Context, videoId: String, quality: String = "720p"): StreamingResult? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.i("SPASIBA_111", "🎯 ╔══════════════════════════════════════════════════════════════════════")
        Log.i("SPASIBA_111", "🎯 ║ 🥇 [CHAMPION_VIDEO_RESOLVE_START] Resolving video ($quality) via 'YT_DLP_ANDROID' for '$videoId'")
        Log.i("SPASIBA_111", "🎯 ╚══════════════════════════════════════════════════════════════════════")

        // 1. Primary Champion for Video (0ms probe delay)
        val championResult = resolveWithYtDlp(context, videoId, isVideo = true, quality = quality, playerClient = "android")
        if (championResult != null && championResult.url.isNotBlank()) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.i("SPASIBA_111", "🏆 ╔══════════════════════════════════════════════════════════════════════")
            Log.i("SPASIBA_111", "🏆 ║ 🥇 [CHAMPION_VIDEO_SUCCESS]")
            Log.i("SPASIBA_111", "🏆 ║ Winner: YT_DLP_ANDROID (Quality: $quality)")
            Log.i("SPASIBA_111", "🏆 ║ ⚡ Extracted in ${elapsed}ms -> Direct Dispatch to ExoPlayer (0ms probe!)")
            Log.i("SPASIBA_111", "🏆 ╚══════════════════════════════════════════════════════════════════════")
            return@withContext championResult
        }

        // 2. Backup Squadron for Video
        Log.w("SPASIBA_111", "🛡️ ╔══════════════════════════════════════════════════════════════════════")
        Log.w("SPASIBA_111", "🛡️ ║ [BACKUP_VIDEO_SQUADRON] Champion missed. Racing: [YT_DLP_WEB, YT_DLP_MWEB, YT_DLP_IOS]")
        Log.w("SPASIBA_111", "🛡️ ╚══════════════════════════════════════════════════════════════════════")

        coroutineScope {
            val winnerChannel = kotlinx.coroutines.channels.Channel<StreamingResult>(1)
            val isWon = java.util.concurrent.atomic.AtomicBoolean(false)
            val racingJobs = mutableListOf<kotlinx.coroutines.Job>()

            backupRacerClients.forEach { (racerName, clientParam) ->
                val job = launch(Dispatchers.IO) {
                    val racerStart = System.currentTimeMillis()
                    try {
                        if (isWon.get()) return@launch
                        val backupResult = resolveWithYtDlp(context, videoId, isVideo = true, quality = quality, playerClient = clientParam)
                        if (backupResult != null && backupResult.url.isNotBlank() && !isWon.get()) {
                            val fetchTime = System.currentTimeMillis() - racerStart
                            Log.d("SPASIBA_111", "🏃 [BACKUP_VIDEO_FETCH] $racerName extracted video in ${fetchTime}ms. Testing live 2-byte probe...")
                            val probeStart = System.currentTimeMillis()
                            val isProbePassed = probeStreamUrl(backupResult.url, backupResult.headers)
                            val probeElapsed = System.currentTimeMillis() - probeStart

                            if (isProbePassed) {
                                if (isWon.compareAndSet(false, true)) {
                                    val totalRacerTime = System.currentTimeMillis() - racerStart
                                    Log.i("SPASIBA_111", "🏆 [BACKUP_VIDEO_WINNER] $racerName won video fallback in ${totalRacerTime}ms (Probe OK)")
                                    winnerChannel.trySend(backupResult)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Cancelled cleanly
                    }
                }
                racingJobs.add(job)
            }

            val fallbackWinner = try {
                winnerChannel.receive()
            } catch (e: Exception) {
                null
            }

            racingJobs.forEach { it.cancel() }
            winnerChannel.close()
            fallbackWinner
        }
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
                var moreSongsBrowseId: String? = null

                for (tabElem in tabs) {
                    val contents = tabElem.asJsonObject.getAsJsonObject("tabRenderer")
                        ?.getAsJsonObject("content")
                        ?.getAsJsonObject("sectionListRenderer")
                        ?.getAsJsonArray("contents") ?: continue

                    for (sectionElem in contents) {
                        val sectionObj = sectionElem.asJsonObject
                        val shelfObj = sectionObj.getAsJsonObject("musicCarouselShelfRenderer")
                            ?: sectionObj.getAsJsonObject("musicShelfRenderer") ?: continue

                        // Extract "See All Songs" browseId link if present
                        if (moreSongsBrowseId == null) {
                            val bottomNav = shelfObj.getAsJsonObject("bottomEndpoint")
                                ?: shelfObj.getAsJsonObject("title")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.getAsJsonObject("navigationEndpoint")
                            val bId = bottomNav?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString
                            if (!bId.isNullOrBlank() && bId != browseId) {
                                moreSongsBrowseId = bId
                            }
                        }

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

                // If "See All Songs" link exists, fetch full channel tracks catalog!
                if (!moreSongsBrowseId.isNullOrBlank()) {
                    val fullCatalogSongs = fetchMoreSongsFromBrowseId(moreSongsBrowseId, artistName)
                    if (fullCatalogSongs.isNotEmpty()) {
                        topSongsList.addAll(0, fullCatalogSongs)
                    }
                }

                // If list is small, merge with fetchArtistTop50
                if (topSongsList.size < 30) {
                    val top50Fallback = fetchArtistTop50(artistName)
                    topSongsList.addAll(top50Fallback)
                }

                ArtistPageData(
                    artistName = artistName,
                    avatarUrl = avatarUrl.takeIf { !it.isNullOrBlank() } ?: getArtistAvatarUrl(artistName),
                    bannerUrl = bannerUrl,
                    topSongs = topSongsList.distinctBy { it.id }.take(60),
                    recentVideos = videosList.distinctBy { it.id }.take(25)
                )
            }
        }.getOrNull()
    }

    private suspend fun fetchMoreSongsFromBrowseId(browseId: String, artistName: String): List<Song> = withContext(Dispatchers.IO) {
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

                val songs = mutableListOf<Song>()
                val contents = json.getAsJsonObject("contents")
                    ?.getAsJsonObject("singleColumnBrowseResultsRenderer")
                    ?.getAsJsonArray("tabs")?.get(0)?.asJsonObject
                    ?.getAsJsonObject("tabRenderer")
                    ?.getAsJsonObject("content")
                    ?.getAsJsonObject("sectionListRenderer")
                    ?.getAsJsonArray("contents") ?: return@runCatching null

                for (sec in contents) {
                    val musicShelf = sec.asJsonObject.getAsJsonObject("musicShelfRenderer")
                        ?: sec.asJsonObject.getAsJsonObject("musicPlaylistShelfRenderer") ?: continue
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

                        if (videoId.length == 11) {
                            val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                            songs.add(Song(id = videoId, title = title, author = artistName, thumbnail = thumb, durationSeconds = 210))
                        }
                    }
                }
                songs
            }
        }.getOrNull() ?: emptyList()
    }
    private val artistTop50Cache = ConcurrentHashMap<String, List<Song>>()
    private val artistVideosCache = ConcurrentHashMap<String, List<Song>>()

    private val artistGarbageKeywords = listOf(
        "remix", "dj ", "djmix", " mix", "slowed", "reverb", "lo-fi", "lofi", "8d", "3d", 
        "fan made", "fan video", "status", "bass boosted", "sped up", "speed up", "mashup", 
        "reaction", "reacting", "karaoke", "instrumental tutorial", "guitar lesson", 
        "piano tutorial", "cover by", "dance cover", "review", "shorts", 
        "full album jukebox", "edit", "tiktok", "ringtone", "club mix", "dhol mix"
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
        val ytVideos = fetchYouTubeMusicSearch("$artistName official music video", params = "EgWKAQIQAWoKEAkQBRAKEAMQBA==")
        val cleanVideos = ytVideos.filter { song ->
            val titleLower = song.title.lowercase()
            artistGarbageKeywords.none { titleLower.contains(it) }
        }.take(20)

        val result = if (cleanVideos.isNotEmpty()) {
            cleanVideos
        } else {
            searchSongs("$artistName official music video").filter { song ->
                val titleLower = song.title.lowercase()
                artistGarbageKeywords.none { titleLower.contains(it) }
            }.take(20)
        }
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
