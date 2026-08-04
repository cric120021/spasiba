package app.spasiba.nativeapp2.ui.screens

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp2.data.api.YouTubeMusicApi
import app.spasiba.nativeapp2.data.model.CategoryChip
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.ui.components.HeroBanner
import app.spasiba.nativeapp2.ui.components.SongTile
import app.spasiba.nativeapp2.ui.components.SongTileSkeleton
import app.spasiba.nativeapp2.ui.components.SilverBrandHeader

// Spotify & YouTube Music Design System Tokens
import app.spasiba.nativeapp2.ui.theme.TextStrong
import coil.compose.AsyncImage
import java.util.Calendar

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable

// Spotify & YouTube Music Design System Tokens
private val SpotifyGreen = Color(0xFF1DB954)
private val SpotifyDarkBackground = Color.Black
private val SpotifyCardSurface = Color(0xFF282828)
private val SpotifyCardHover = Color(0xFF333333)
private val TextSecondaryGray = Color(0xFFB3B3B3)

@Composable
fun HomeScreen(
    recommendedSongs: List<Song>,
    currentSong: Song?,
    likedSongs: Set<String>,
    recentlyPlayed: List<Song> = emptyList(),
    selectedLanguages: Set<String> = setOf("Hindi", "Punjabi", "English"),
    isLoading: Boolean,
    selectedCategory: CategoryChip?,
    onCategorySelect: (CategoryChip?) -> Unit,
    onSearchClick: () -> Unit = {},
    onLibraryClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSongClick: (Song, List<Song>, Int) -> Unit,
    onLikeClick: (String) -> Unit,
    onOptionsClick: (Song) -> Unit = {},
    onSwipeToQueue: (Song) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    listState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
) {
    // Dynamic greeting calculation based on hour of day
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    var selectedMoodFilter by remember { mutableStateOf<String?>(null) }
    val activeFilterName = selectedCategory?.label ?: selectedMoodFilter
    var categorySearchSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var categoryShelvesState by remember { mutableStateOf<List<app.spasiba.nativeapp2.data.api.HomeShelf>>(emptyList()) }
    var liveHomeShelves by remember { mutableStateOf<List<app.spasiba.nativeapp2.data.api.HomeShelf>>(emptyList()) }

    var topChartsState by remember { mutableStateOf(app.spasiba.nativeapp2.data.api.YouTubeMusicApi.cachedTopCharts) }
    var punjabiSongsState by remember { mutableStateOf(app.spasiba.nativeapp2.data.api.YouTubeMusicApi.cachedPunjabiSongs) }
    var hindiSongsState by remember { mutableStateOf(app.spasiba.nativeapp2.data.api.YouTubeMusicApi.cachedHindiSongs) }
    var englishSongsState by remember { mutableStateOf(app.spasiba.nativeapp2.data.api.YouTubeMusicApi.cachedEnglishSongs) }
    var southSongsState by remember { mutableStateOf(app.spasiba.nativeapp2.data.api.YouTubeMusicApi.cachedSouthSongs) }
    var haryanviSongsState by remember { mutableStateOf(app.spasiba.nativeapp2.data.api.YouTubeMusicApi.cachedHaryanviSongs) }
    var bhojpuriSongsState by remember { mutableStateOf(app.spasiba.nativeapp2.data.api.YouTubeMusicApi.cachedBhojpuriSongs) }

    var isCategoryLoading by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(activeFilterName) {
        if (activeFilterName != null) {
            isCategoryLoading = true
            categoryShelvesState = emptyList()
            val catLower = activeFilterName.lowercase()

            val shelvesList = mutableListOf<app.spasiba.nativeapp2.data.api.HomeShelf>()

            val rawYtParam = when (catLower) {
                "podcasts" -> "ggMPOZ3ZyZXF1ZW5jeT0x"
                "romance" -> "ggMPOZ3ZyZXF1ZW5jeT0y"
                "workout" -> "ggMPOZ3ZyZXF1ZW5jeT0z"
                "energize" -> "ggMPOZ3ZyZXF1ZW5jeT00"
                "relax" -> "ggMPOZ3ZyZXF1ZW5jeT01"
                "focus" -> "ggMPOZ3ZyZXF1ZW5jeT02"
                "commute" -> "ggMPOZ3ZyZXF1ZW5jeT03"
                "sad" -> "ggMPOZ3ZyZXF1ZW5jeT08"
                "sleep" -> "ggMPOZ3ZyZXF1ZW5jeT05"
                else -> null
            }

            val rawYtBrowseId = if (catLower == "podcasts") "FEmusic_podcasts" else "FEmusic_home"
            val directYtShelves = app.spasiba.nativeapp2.data.api.YouTubeMusicApi.fetchYouTubeMusicBrowseShelves(rawYtBrowseId, rawYtParam)
            if (directYtShelves.isNotEmpty()) {
                shelvesList.addAll(directYtShelves)
            }

            kotlinx.coroutines.coroutineScope {
                when (catLower) {
                    "podcasts" -> {
                        val mainAudioDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("The Ranveer Show Raj Shamani BeerBiceps Huberman Lex Fridman podcast audio") }
                        val mainVideoDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("The Ranveer Show Raj Shamani BeerBiceps podcast video episode") }
                        val storiesDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("The Ranveer Show Raj Shamani BeerBiceps interview podcast") }
                        val businessDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("Finshots Nikhil Kamath Raj Shamani tech business podcast") }
                        val mindsetDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("Andrew Huberman Raj Shamani self improvement health podcast") }
                        val crimeDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("Desi Crime Crime Tak true crime podcast") }
                        val healthDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("Huberman Lab health neuroscience podcast") }
                        val geoDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("Palki Sharma World Is One geopolitics news podcast") }
                        val comedyDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("Tanmay Bhat Zakir Khan comedy interview podcast talk") }
                        val moneyDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("Akshat Shrivastava Rachana Ranade personal finance investing podcast") }
                        val spiritDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("Gaur Gopal Das Jaya Kishori spiritual wisdom podcast") }

                        fun filterPodcasts(list: List<Song>): List<Song> = list.filter { s ->
                            !s.title.lowercase().contains("latent") && !s.title.lowercase().contains("standup") && !s.title.lowercase().contains("roast")
                        }

                        val mainAudio = filterPodcasts(mainAudioDef.await())
                        val mainVideo = filterPodcasts(mainVideoDef.await())
                        val stories = filterPodcasts(storiesDef.await())
                        val business = filterPodcasts(businessDef.await())
                        val mindset = filterPodcasts(mindsetDef.await())
                        val crime = filterPodcasts(crimeDef.await())
                        val health = filterPodcasts(healthDef.await())
                        val geo = filterPodcasts(geoDef.await())
                        val comedy = filterPodcasts(comedyDef.await())
                        val money = filterPodcasts(moneyDef.await())
                        val spirit = filterPodcasts(spiritDef.await())

                        if (mainAudio.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Top Podcasts Shows & Episodes", songs = mainAudio.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                        if (mainVideo.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Featured Video Podcasts", songs = mainVideo.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.VIDEO_WIDESCREEN))
                        if (stories.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Stories & Deep Conversations", songs = stories.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                        if (business.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Business, Tech & Entrepreneurship", songs = business.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.VIDEO_WIDESCREEN))
                        if (mindset.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Self Improvement & Mindset", songs = mindset.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                        if (crime.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "True Crime & Mysteries", songs = crime.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                        if (health.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Health, Wellness & Science", songs = health.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.VIDEO_WIDESCREEN))
                        if (geo.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Geopolitics & Global Affairs", songs = geo.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                        if (money.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Finance & Money Management", songs = money.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.VIDEO_WIDESCREEN))
                        if (spirit.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Spirituality & Life Wisdom", songs = spirit.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                        if (comedy.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Humor & Comedy Talks", songs = comedy.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                    }
                    "romance" -> {
                        val mainHitsDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("latest romantic love songs 2026 Arijit Singh Pritam") }
                        val mainVideosDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("official music video romantic love songs 2026") }
                        val hindiLoveDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("Arijit Singh Pritam Shreya Ghoshal romantic love songs") }
                        val punjabiLoveDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("Prabh Gill B Praak Akhil romantic punjabi love songs") }
                        val acousticLoveDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("acoustic guitar romantic unplugged love songs") }
                        val englishLoveDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("english romantic pop ballads Ed Sheeran Taylor Swift") }
                        val rainRomanceDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("monsoon rain romantic bollywood hindi songs") }
                        val classic90sDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("90s 2000s classic bollywood romantic songs Kumar Sanu Alka Yagnik") }

                        val mainHits = mainHitsDef.await()
                        val mainVideos = mainVideosDef.await()
                        val hindiLove = hindiLoveDef.await()
                        val punjabiLove = punjabiLoveDef.await()
                        val acousticLove = acousticLoveDef.await()
                        val englishLove = englishLoveDef.await()
                        val rainRomance = rainRomanceDef.await()
                        val classic90s = classic90sDef.await()

                        if (mainHits.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Top Romantic Hits", songs = mainHits.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                        if (mainVideos.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Featured Romantic Music Videos", songs = mainVideos.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.VIDEO_WIDESCREEN))
                        if (hindiLove.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Hindi Romance Essentials", songs = hindiLove.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                        if (punjabiLove.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Punjabi Love Songs", songs = punjabiLove.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                        if (acousticLove.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Acoustic & Unplugged Romance", songs = acousticLove.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.VIDEO_WIDESCREEN))
                        if (englishLove.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "English Love Ballads", songs = englishLove.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                        if (rainRomance.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Rain & Monsoon Romance", songs = rainRomance.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                        if (classic90s.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Classic 90s & 2000s Romance", songs = classic90s.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.VIDEO_WIDESCREEN))
                    }
                    "workout" -> {
                        val mainHitsDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("workout gym motivation heavy bass songs 2026") }
                        val mainVideosDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("heavy bass gym workout motivation music video") }
                        val heavyBassDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("heavy bass gym workout motivation beats 2026") }
                        val cardioEDMDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("cardio workout edm electro dance music 2026") }
                        val hiphopHypeDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("hip hop gym workout motivation rap songs") }
                        val hardstyleDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("hardstyle gym workout pump motivation") }

                        val mainHits = mainHitsDef.await()
                        val mainVideos = mainVideosDef.await()
                        val heavyBass = heavyBassDef.await()
                        val cardioEDM = cardioEDMDef.await()
                        val hiphopHype = hiphopHypeDef.await()
                        val hardstyle = hardstyleDef.await()

                        if (mainHits.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Top Workout Hits", songs = mainHits.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                        if (mainVideos.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Gym Motivation Music Videos", songs = mainVideos.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.VIDEO_WIDESCREEN))
                        if (heavyBass.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Heavy Bass Gym Beats", songs = heavyBass.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                        if (cardioEDM.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Cardio & EDM Workout", songs = cardioEDM.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.VIDEO_WIDESCREEN))
                        if (hiphopHype.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Hip-Hop Gym Hype", songs = hiphopHype.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                        if (hardstyle.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Hardstyle Gym Pump", songs = hardstyle.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                    }
                    else -> {
                        val songsDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("latest $catLower songs 2026 official") }
                        val videosDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("official music video $catLower 2026") }
                        val essentialsDef = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("best $catLower hits 2026 collection") }

                        val songs = songsDef.await()
                        val videos = videosDef.await().ifEmpty { songs }
                        val essentials = essentialsDef.await().ifEmpty { songs.drop(4) }

                        if (songs.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Top $activeFilterName Hits", songs = songs.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                        if (videos.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "Trending $activeFilterName Videos", songs = videos.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.VIDEO_WIDESCREEN))
                        if (essentials.isNotEmpty()) shelvesList.add(app.spasiba.nativeapp2.data.api.HomeShelf(title = "$activeFilterName Essentials", songs = essentials.take(8), style = app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.CAROUSEL_SQUARE))
                    }
                }
            }

            if (shelvesList.isNotEmpty()) {
                categoryShelvesState = shelvesList
            }
            isCategoryLoading = false
        } else {
            categorySearchSongs = emptyList()
            categoryShelvesState = emptyList()
            isCategoryLoading = false
        }
    }

    val displayShelves = remember(liveHomeShelves, categoryShelvesState, activeFilterName) {
        if (activeFilterName != null) {
            categoryShelvesState
        } else {
            liveHomeShelves
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            val shelvesDeferred = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.fetchYouTubeMusicHomeShelves() }
            val chartsDeferred = async { 
                app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("latest release songs 2026 official")
                    .filter { !it.title.lowercase().contains("jukebox") && !it.title.lowercase().contains("compilation") && !it.title.lowercase().contains("non stop") && !it.title.lowercase().contains("full album") }
            }
            val punjabiDeferred = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("latest punjabi songs 2026 official") }
            val hindiDeferred = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("latest hindi songs 2026 official") }
            val englishDeferred = async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.searchSongs("latest english pop songs 2026 official") }

            val fetchedShelves = shelvesDeferred.await()
            if (fetchedShelves.isNotEmpty()) liveHomeShelves = fetchedShelves

            val fetchedCharts = chartsDeferred.await()
            if (fetchedCharts.isNotEmpty()) {
                app.spasiba.nativeapp2.data.api.YouTubeMusicApi.cachedTopCharts = fetchedCharts
                topChartsState = fetchedCharts
            }

            val fetchedPunjabi = punjabiDeferred.await()
            if (fetchedPunjabi.isNotEmpty()) {
                app.spasiba.nativeapp2.data.api.YouTubeMusicApi.cachedPunjabiSongs = fetchedPunjabi
                punjabiSongsState = fetchedPunjabi
            }

            val fetchedHindi = hindiDeferred.await()
            if (fetchedHindi.isNotEmpty()) {
                app.spasiba.nativeapp2.data.api.YouTubeMusicApi.cachedHindiSongs = fetchedHindi
                hindiSongsState = fetchedHindi
            }

            val fetchedEnglish = englishDeferred.await()
            if (fetchedEnglish.isNotEmpty()) {
                app.spasiba.nativeapp2.data.api.YouTubeMusicApi.cachedEnglishSongs = fetchedEnglish
                englishSongsState = fetchedEnglish
            }
        }
    }

    val filteredSongs = remember(recommendedSongs, activeFilterName, categorySearchSongs) {
        if (activeFilterName == null) {
            recommendedSongs
        } else {
            val filter = activeFilterName.lowercase()
            val matching = recommendedSongs.filter {
                it.title.lowercase().contains(filter) ||
                it.author.lowercase().contains(filter)
            }
            val combined = (categorySearchSongs + matching + recommendedSongs).distinctBy { it.id }
            if (combined.isNotEmpty()) combined else recommendedSongs
        }
    }

    val featuredHeroSong = remember(filteredSongs) { filteredSongs.firstOrNull() }
    val quickPicks = remember(recentlyPlayed, likedSongs, filteredSongs) {
        val likedTrackList = filteredSongs.filter { likedSongs.contains(it.id) }
        (recentlyPlayed + likedTrackList + filteredSongs).distinctBy { it.id }.take(8)
    }
    val quickPickColumns = remember(quickPicks) { quickPicks.chunked(4) }
    val mixedTracks = remember(likedSongs, filteredSongs) {
        val likedTrackList = filteredSongs.filter { likedSongs.contains(it.id) }
        (likedTrackList + filteredSongs.shuffled()).distinctBy { it.id }.take(6)
    }
    val jumpBackInTracks = remember(recentlyPlayed, filteredSongs) {
        if (recentlyPlayed.isNotEmpty()) recentlyPlayed.take(6)
        else filteredSongs.take(6)
    }
    val punjabiSongs = remember(punjabiSongsState) { punjabiSongsState }
    val hindiSongs = remember(hindiSongsState) { hindiSongsState }
    val englishSongs = remember(englishSongsState) { englishSongsState }
    val southSongs = remember(southSongsState) { southSongsState }
    val haryanviSongs = remember(haryanviSongsState) { haryanviSongsState }
    val bhojpuriSongs = remember(bhojpuriSongsState) { bhojpuriSongsState }

    val topChartSongs = remember(topChartsState) { topChartsState.take(5) }

    var artistAvatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val artistNames = remember(recentlyPlayed, likedSongs, filteredSongs) {
        val likedTrackList = filteredSongs.filter { likedSongs.contains(it.id) }
        val allTracks = recentlyPlayed + likedTrackList + filteredSongs
        allTracks
            .map { it.author }
            .filter { it != "Artist" && it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(10)
    }

    androidx.compose.runtime.LaunchedEffect(artistNames) {
        if (artistNames.isNotEmpty()) {
            kotlinx.coroutines.coroutineScope {
                val deferreds = artistNames.map { artist ->
                    artist to async { app.spasiba.nativeapp2.data.api.YouTubeMusicApi.getArtistAvatarUrl(artist) }
                }
                val avatars = mutableMapOf<String, String>()
                deferreds.forEach { (artist, deferred) ->
                    avatars[artist] = deferred.await()
                }
                artistAvatars = avatars
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyDarkBackground),
        contentPadding = PaddingValues(bottom = 150.dp)
    ) {
        // 1. Dynamic Header with Time Greeting & Top Action Target Icons (Spotify style)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentContext = androidx.compose.ui.platform.LocalContext.current
                    val isOnline = remember { app.spasiba.nativeapp2.data.download.SpasibaDownloadManager.isNetworkAvailable(currentContext) }
                    SilverBrandHeader(
                        modifier = Modifier.weight(1f),
                        subtitle = if (!isOnline) "⚡ Offline Mode — No Internet" else null
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    val userPrefs = remember { currentContext.getSharedPreferences("spasiba_user_prefs", android.content.Context.MODE_PRIVATE) }
                    val userPhotoUri = remember { userPrefs.getString("profile_photo_uri", null) }

                    // Top Header Action: Pure Borderless Profile Avatar Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onProfileClick)
                    ) {
                        if (!userPhotoUri.isNullOrBlank()) {
                            AsyncImage(
                                model = userPhotoUri,
                                contentDescription = "Profile Photo",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // YouTube Music Top Horizontal Filter Pills Bar
                val filterChips = listOf(
                    "Podcasts", "Romance", "Energize", "Workout", "Relax", "Commute", "Feel good", "Sad", "Focus", "Sleep",
                    "Hindi", "Punjabi", "English", "South Hits", "Haryanvi", "Bhojpuri", "Gujarati", "Bengali"
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    items(filterChips) { filter ->
                        val isSelected = activeFilterName?.equals(filter, ignoreCase = true) == true
                        val isThisChipLoading = isSelected && isCategoryLoading

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color.White else Color.White.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    val isCurrentSelected = activeFilterName?.equals(filter, ignoreCase = true) == true
                                    if (isCurrentSelected) {
                                        selectedMoodFilter = null
                                        onCategorySelect(null)
                                    } else {
                                        selectedMoodFilter = filter
                                        val catChip = CategoryChip(filter.lowercase(), filter, "top ${filter.lowercase()} songs")
                                        onCategorySelect(catChip)
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                if (isThisChipLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = Color.Black,
                                        strokeWidth = 1.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = filter,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Live YouTube Music Fetched Home Shelves & Category Shelves
        if (displayShelves.isNotEmpty()) {
            items(displayShelves) { shelf ->
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onCategorySelect(CategoryChip(shelf.title.lowercase(), shelf.title, "top ${shelf.title.lowercase()} songs"))
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = shelf.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color.White
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "View Category",
                            tint = TextSecondaryGray
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (shelf.style) {
                        app.spasiba.nativeapp2.data.api.ShelfLayoutStyle.VIDEO_WIDESCREEN -> {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                itemsIndexed(shelf.songs) { index, song ->
                                    AnimatedPressCard(onClick = { onSongClick(song, shelf.songs, index) }) {
                                        Column(modifier = Modifier.width(220.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .width(220.dp)
                                                    .height(124.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            ) {
                                                AsyncImage(
                                                    model = song.thumbnail,
                                                    contentDescription = song.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color.Black.copy(alpha = 0.7f),
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(6.dp)
                                                ) {
                                                    Text(
                                                        text = "VIDEO",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 9.sp
                                                        ),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = song.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 14.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = song.author,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = TextSecondaryGray,
                                                    fontSize = 12.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                itemsIndexed(shelf.songs) { index, song ->
                                    AnimatedPressCard(onClick = { onSongClick(song, shelf.songs, index) }) {
                                        Column(modifier = Modifier.width(148.dp)) {
                                            AsyncImage(
                                                model = song.thumbnail,
                                                contentDescription = song.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(148.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = song.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White,
                                                    fontSize = 13.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = song.author,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = TextSecondaryGray,
                                                    fontSize = 11.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Default Home Sections (Only shown when no Category Filter is active)
        if (activeFilterName == null) {
            // 2A. TOP CHARTS (Most played right now)
            item {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        text = "TOP CHARTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SpotifyGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.4.sp
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Text(
                        text = "Most played right now",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (topChartSongs.isEmpty() || isLoading) {
                        repeat(5) {
                            app.spasiba.nativeapp2.ui.components.ChartItemSkeleton()
                        }
                    } else {
                        topChartSongs.forEachIndexed { index, song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSongClick(song, topChartSongs, index) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (index == 0) SpotifyGreen else Color.White.copy(alpha = 0.7f),
                                    fontSize = 18.sp
                                ),
                                modifier = Modifier.width(28.dp)
                            )

                            AsyncImage(
                                model = song.thumbnail,
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (song.id == currentSong?.id) SpotifyGreen else Color.White,
                                        fontSize = 14.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${song.author} • ${song.durationFormatted}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondaryGray,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { onOptionsClick(song) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = TextSecondaryGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

            // 2B. QUICK PICKS (Placed directly below Most Played right now)
            item {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Quick picks",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "Start radio from any song",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondaryGray,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        if (quickPicks.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onSongClick(quickPicks.first(), quickPicks, 0) },
                                color = Color.White.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "PLAY ALL",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.2.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (quickPickColumns.isEmpty() || isLoading) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(3) {
                                Column(
                                    modifier = Modifier.width(320.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    repeat(4) {
                                        SongTileSkeleton()
                                    }
                                }
                            }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(quickPickColumns) { columnTracks ->
                                Column(
                                    modifier = Modifier.width(320.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    columnTracks.forEach { track ->
                                        val isCurrentPlaying = track.id == currentSong?.id

                                        AnimatedPressCard(
                                            onClick = {
                                                val index = filteredSongs.indexOfFirst { it.id == track.id }
                                                onSongClick(track, filteredSongs, if (index >= 0) index else 0)
                                            }
                                        ) {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isCurrentPlaying) SpotifyGreen.copy(alpha = 0.18f) else Color.Transparent
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    AsyncImage(
                                                        model = track.thumbnail,
                                                        contentDescription = track.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = track.title,
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = if (isCurrentPlaying) SpotifyGreen else Color.White,
                                                                fontSize = 14.sp
                                                            ),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "${track.author} • ${track.durationFormatted}",
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                color = TextSecondaryGray,
                                                                fontSize = 12.sp
                                                            ),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { onOptionsClick(track) },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.MoreVert,
                                                            contentDescription = "Options",
                                                            tint = TextSecondaryGray,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        // 3. YT Music Circular Artist Spotlight Row
        item {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "ARTIST SPOTLIGHT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = SpotifyGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text(
                    text = "Your favorite creators",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(artistNames) { artistName ->
                        val songForArtist = remember(artistName, filteredSongs) {
                            filteredSongs.firstOrNull { it.author.equals(artistName, ignoreCase = true) }
                        }
                        val avatarUrl = artistAvatars[artistName]
                            ?: songForArtist?.thumbnail
                            ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300"
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(100.dp)
                                .clickable { onArtistClick(artistName) }
                        ) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = artistName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = artistName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = "Artist",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextSecondaryGray,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // 4. Language & Genre Horizontal Song Rails (Just like Mixed for you & Jump back in)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (selectedLanguages.contains("Punjabi")) {
                    CategorySongRail(
                        title = "Punjabi Hits",
                        eyebrow = "PUNJABI SPOTLIGHT",
                        songs = punjabiSongs,
                        currentSong = currentSong,
                        onSongClick = onSongClick
                    )
                }

                if (selectedLanguages.contains("Hindi")) {
                    CategorySongRail(
                        title = "Hindi Bollywood",
                        eyebrow = "FOR HINDI MOODS",
                        songs = hindiSongs,
                        currentSong = currentSong,
                        onSongClick = onSongClick
                    )
                }

                if (selectedLanguages.contains("English")) {
                    CategorySongRail(
                        title = "English Pop Hits",
                        eyebrow = "GLOBAL HITS",
                        songs = englishSongs,
                        currentSong = currentSong,
                        onSongClick = onSongClick
                    )
                }

                if (selectedLanguages.contains("South Hits") || selectedLanguages.contains("South")) {
                    CategorySongRail(
                        title = "South Tamil & Telugu",
                        eyebrow = "SOUTH SPOTLIGHT",
                        songs = southSongs,
                        currentSong = currentSong,
                        onSongClick = onSongClick
                    )
                }

                if (selectedLanguages.contains("Haryanvi")) {
                    CategorySongRail(
                        title = "Haryanvi Explosives",
                        eyebrow = "LOCAL HITS",
                        songs = haryanviSongs,
                        currentSong = currentSong,
                        onSongClick = onSongClick
                    )
                }

                if (selectedLanguages.contains("Bhojpuri")) {
                    CategorySongRail(
                        title = "Bhojpuri Party",
                        eyebrow = "FOLK & DANCE",
                        songs = bhojpuriSongs,
                        currentSong = currentSong,
                        onSongClick = onSongClick
                    )
                }
            }
        }

        // 3. Hero Recommendation Spotlight (To solve information overload)
        item {
            if (featuredHeroSong != null && !isLoading) {
                HeroSpotlightCard(
                    song = featuredHeroSong,
                    onPlayClick = { onSongClick(featuredHeroSong, filteredSongs, 0) }
                )
                Spacer(modifier = Modifier.height(18.dp))
            }
        }

        // 4. Spotify 2-Column Shortcuts Grid (6 items, height 56dp, 8dp radius)
        item {
            if (filteredSongs.isNotEmpty()) {
                SpotifyShortcutsGrid(
                    songs = filteredSongs,
                    currentSong = currentSong,
                    likedCount = likedSongs.size,
                    onSongClick = onSongClick,
                    onLikedSongsClick = onLibraryClick
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }



        // 7. "Mixed For You" Horizontal Shelf (Album art 70% of card, 160x160dp)
        item {
            if (!isLoading && mixedTracks.isNotEmpty()) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mixed for you",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Show All",
                            tint = TextSecondaryGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(mixedTracks) { index, song ->
                            AnimatedPressCard(
                                onClick = { onSongClick(song, filteredSongs, index) }
                            ) {
                                Column(modifier = Modifier.width(156.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(156.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    ) {
                                        AsyncImage(
                                            model = song.thumbnail,
                                            contentDescription = song.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                                                    )
                                                )
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Black.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                        ) {
                                            Text(
                                                text = "DAILY MIX ${index + 1}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    letterSpacing = 1.2.sp
                                                ),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "${song.author} and more",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextSecondaryGray,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

// ------------------- Helper Sub-Composables -------------------

/**
 * Spotify-Style 2-Column Shortcuts Grid (Height 56dp, Corner radius 8dp, Charcoal #282828)
 */
@Composable
private fun SpotifyShortcutsGrid(
    songs: List<Song>,
    currentSong: Song?,
    likedCount: Int,
    onSongClick: (Song, List<Song>, Int) -> Unit,
    onLikedSongsClick: () -> Unit
) {
    val displayTracks = songs.take(5)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Liked Songs Special Shortcut Card
            AnimatedPressCard(
                onClick = onLikedSongsClick,
                modifier = Modifier.weight(1f)
            ) {
                ShortcutCardSurface(isPlaying = false) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF450AF5), Color(0xFFC4EFD9))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Liked Songs",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "Liked Songs ($likedCount)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }

            // Track 1
            displayTracks.getOrNull(0)?.let { track ->
                val isPlaying = track.id == currentSong?.id
                AnimatedPressCard(
                    onClick = { onSongClick(track, songs, 0) },
                    modifier = Modifier.weight(1f)
                ) {
                    ShortcutCardSurface(isPlaying = isPlaying) {
                        AsyncImage(
                            model = track.thumbnail,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isPlaying) SpotifyGreen else Color.White
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                }
            }
        }

        // Row 2 & 3
        val remainingTracks = displayTracks.drop(1).chunked(2)
        remainingTracks.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { track ->
                    val isPlaying = track.id == currentSong?.id
                    val trackIndex = songs.indexOfFirst { it.id == track.id }

                    AnimatedPressCard(
                        onClick = { onSongClick(track, songs, if (trackIndex >= 0) trackIndex else 0) },
                        modifier = Modifier.weight(1f)
                    ) {
                        ShortcutCardSurface(isPlaying = isPlaying) {
                            AsyncImage(
                                model = track.thumbnail,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isPlaying) SpotifyGreen else Color.White
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                        }
                    }
                }
                if (rowItems.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ShortcutCardSurface(
    isPlaying: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPlaying) Color(0x331DB954) else SpotifyCardSurface),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Hero Recommendation Spotlight Card (Solves feed clutter)
 */
@Composable
private fun HeroSpotlightCard(
    song: Song,
    onPlayClick: () -> Unit
) {
    AnimatedPressCard(
        onClick = onPlayClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp),
            shape = RoundedCornerShape(12.dp),
            color = SpotifyCardSurface
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF0F0F0F).copy(alpha = 0.95f),
                                    Color(0xFF1E1E1E).copy(alpha = 0.80f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = SpotifyGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "RECOMMENDED FOR YOU",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = SpotifyGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.2.sp
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 18.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = song.author,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondaryGray,
                                fontSize = 13.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = SpotifyGreen,
                            modifier = Modifier.clickable { onPlayClick() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "PLAY",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.8.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    AsyncImage(
                        model = song.thumbnail,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySongRail(
    title: String,
    eyebrow: String? = null,
    songs: List<Song>,
    currentSong: Song?,
    onSongClick: (Song, List<Song>, Int) -> Unit
) {
    if (songs.isEmpty()) return

    Column(modifier = Modifier.padding(bottom = 22.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (eyebrow != null) {
                    Text(
                        text = eyebrow,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SpotifyGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.4.sp
                        )
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(songs) { index, song ->
                AnimatedPressCard(
                    onClick = { onSongClick(song, songs, index) }
                ) {
                    Column(modifier = Modifier.width(140.dp)) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(RoundedCornerShape(10.dp))
                        ) {
                            AsyncImage(
                                model = song.thumbnail,
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (song.id == currentSong?.id) SpotifyGreen else Color.White,
                                fontSize = 13.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = song.author,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondaryGray,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated Touch Scale Wrapper for Micro-Animations (100% -> 96% -> 100%)
 */
@Composable
private fun AnimatedPressCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        content()
    }
}

private fun getCategoryGradient(categoryId: String): Brush {
    return when (categoryId.lowercase()) {
        "punjabi" -> Brush.horizontalGradient(listOf(Color(0xFFFF512F), Color(0xFFDD2476)))
        "hindi", "bollywood" -> Brush.horizontalGradient(listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)))
        "english" -> Brush.horizontalGradient(listOf(Color(0xFF00c6ff), Color(0xFF0072ff)))
        "haryanvi" -> Brush.horizontalGradient(listOf(Color(0xFFF7971E), Color(0xFFFFD200)))
        "south" -> Brush.horizontalGradient(listOf(Color(0xFF11998e), Color(0xFF38ef7d)))
        "bhojpuri" -> Brush.horizontalGradient(listOf(Color(0xFFEB3349), Color(0xFFF45C43)))
        "gujarati" -> Brush.horizontalGradient(listOf(Color(0xFFFA709A), Color(0xFFFEE140)))
        "bengali" -> Brush.horizontalGradient(listOf(Color(0xFF43E97B), Color(0xFF38F9D7)))
        "marathi" -> Brush.horizontalGradient(listOf(Color(0xFFF83600), Color(0xFFFE8C00)))
        "malayalam" -> Brush.horizontalGradient(listOf(Color(0xFF0BA360), Color(0xFF3CBA92)))
        "kannada" -> Brush.horizontalGradient(listOf(Color(0xFFFF5F6D), Color(0xFFFFC371)))
        "latin" -> Brush.horizontalGradient(listOf(Color(0xFFF12711), Color(0xFFF5AF19)))
        "kpop" -> Brush.horizontalGradient(listOf(Color(0xFFEC008C), Color(0xFFFC6767)))
        "relax", "chill" -> Brush.horizontalGradient(listOf(Color(0xFF3A1C71), Color(0xFFD76D77)))
        "workout" -> Brush.horizontalGradient(listOf(Color(0xFF1D976C), Color(0xFF93F9B9)))
        "focus" -> Brush.horizontalGradient(listOf(Color(0xFF614385), Color(0xFF516395)))
        "party" -> Brush.horizontalGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)))
        "romance" -> Brush.horizontalGradient(listOf(Color(0xFFE55D87), Color(0xFF5FC3E4)))
        "hiphop" -> Brush.horizontalGradient(listOf(Color(0xFF232526), Color(0xFF414345)))
        else -> Brush.horizontalGradient(listOf(Color(0xFF282828), Color(0xFF3F3F3F)))
    }
}

private fun getCategoryBackdropUrl(categoryId: String): String {
    return when (categoryId.lowercase()) {
        "punjabi" -> "https://lh3.googleusercontent.com/xW41X55Pvpo2zLCniTPXXNH2wpPgrpFj0IAYqREB4e01RIcMBmE0C73J3o_D9dmJV6P9mMRkZcwDLw=w600-h340-l90-rj"
        "hindi", "bollywood" -> "https://lh3.googleusercontent.com/XLbDsfPCEV3i6okk5itzYlpNb0Tn9KRwvCdlEUnmkVE_J0lTQASwc0_znu6H6MYUv7QOSgswHCxkxQ=w600-h340-l90-rj"
        "english" -> "https://lh3.googleusercontent.com/OXBoCLUN-ZabqHqd0iRepimpWPpD_V7pocuvX9fL83gwqMJ-uHBM6vf01EbAzXboTKkw-GMS8gGSCA=w600-h340-l90-rj"
        "south" -> "https://lh3.googleusercontent.com/boAaN71qGdHlJ9FZUvsga4b0tk3t-BzJ1TkFVF5tWsQK9-_JAyazY1tQKyUEGP4PAl23xXOfMv-1-onF=w600-h340-l90-rj"
        "workout" -> "https://lh3.googleusercontent.com/xW41X55Pvpo2zLCniTPXXNH2wpPgrpFj0IAYqREB4e01RIcMBmE0C73J3o_D9dmJV6P9mMRkZcwDLw=w600-h340-l90-rj"
        "relax", "chill" -> "https://lh3.googleusercontent.com/9_qmVOvLuS7mCYj7nw6Zgy01K3NKSaI8YvLu_spdTL6i1goKHEVoMZGVCetG7RzlI2M4e4gjM6DquZ3L=w600-h340-l90-rj"
        "romance" -> "https://lh3.googleusercontent.com/QHEWxP5BjpPhZkmZ22xsROuXPGy9ttV9QzmDoPVU_UqvLop6AQbebAm-YbFVkMQ7cJ3tmVV67O0jA_I=w600-h340-l90-rj"
        "focus" -> "https://lh3.googleusercontent.com/OXBoCLUN-ZabqHqd0iRepimpWPpD_V7pocuvX9fL83gwqMJ-uHBM6vf01EbAzXboTKkw-GMS8gGSCA=w600-h340-l90-rj"
        else -> "https://lh3.googleusercontent.com/9_qmVOvLuS7mCYj7nw6Zgy01K3NKSaI8YvLu_spdTL6i1goKHEVoMZGVCetG7RzlI2M4e4gjM6DquZ3L=w600-h340-l90-rj"
    }
}

private fun getCategoryEyebrow(categoryId: String): String {
    return when (categoryId.lowercase()) {
        "punjabi" -> "PUNJABI SPOTLIGHT"
        "hindi", "bollywood" -> "FOR HINDI MOODS"
        "english" -> "GLOBAL HITS"
        "south" -> "TAMIL & TELUGU"
        "haryanvi" -> "LOCAL SPOTLIGHT"
        "bhojpuri" -> "FOLK & PARTY"
        "gujarati" -> "GUJARATI VIBES"
        "bengali" -> "BENGALI MELODIES"
        "marathi" -> "MARATHI TOP HITS"
        "malayalam" -> "MALAYALAM SOUNDS"
        "kannada" -> "KANNADA WAVE"
        "latin" -> "REGGAETON & LATIN"
        "kpop" -> "GLOBAL K-POP"
        "workout" -> "FOR YOUR WORKOUT"
        "relax", "chill" -> "EASE INTO IT"
        "romance" -> "WARM & CLOSE"
        "focus" -> "DEEP FOCUS"
        "party" -> "CLUB & DANCE"
        else -> "FEATURED CATEGORY"
    }
}
