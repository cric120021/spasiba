package app.spasiba.nativeapp2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CallMade
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.spasiba.nativeapp2.data.cache.SearchHistoryManager
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp2.data.api.YouTubeMusicApi
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.ui.components.SongTile
import app.spasiba.nativeapp2.ui.components.SongTileSkeleton
import app.spasiba.nativeapp2.ui.theme.AccentTeal
import app.spasiba.nativeapp2.ui.theme.TextMuted
import app.spasiba.nativeapp2.ui.theme.TextStrong
import app.spasiba.nativeapp2.ui.theme.UiSurfaceElevated
import coil.compose.AsyncImage

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    searchResults: List<Song>,
    detectedArtist: YouTubeMusicApi.ArtistSearchResult? = null,
    currentSong: Song?,
    likedSongs: Set<String>,
    searchHistory: List<String>,
    isSearching: Boolean,
    onDeleteHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSongClick: (Song, List<Song>, Int) -> Unit,
    onLikeClick: (String) -> Unit,
    onOptionsClick: (Song) -> Unit = {},
    onSwipeToQueue: (Song) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var isSearchSubmitted by remember { mutableStateOf(false) }

    val processedSearchResults = remember(searchResults) {
        searchResults.sortedWith(
            compareByDescending<Song> { it.views }
                .thenByDescending { song ->
                    val t = song.title.lowercase()
                    if (t.contains("official music video")) 3
                    else if (t.contains("official video")) 2
                    else if (t.contains("official audio")) 1
                    else 0
                }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 18.dp)
    ) {
        // 1. YouTube/Spotify style Search Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                shape = RoundedCornerShape(29.dp),
                color = UiSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = AccentTeal,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                if (query.isNotBlank()) {
                                    focusManager.clearFocus()
                                    isSearchSubmitted = true
                                    onSearch(query)
                                }
                            }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search songs, artists...",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted, fontSize = 16.sp)
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = query,
                            onValueChange = { newQ ->
                                isSearchSubmitted = false
                                onQueryChange(newQ)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            maxLines = 1,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentTeal),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = TextStrong,
                                fontSize = 16.sp,
                                lineHeight = 22.sp
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (query.isNotBlank()) {
                                    focusManager.clearFocus()
                                    isSearchSubmitted = true
                                    onSearch(query)
                                }
                            })
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
	                            tint = TextMuted,
	                            modifier = Modifier
	                                .size(22.dp)
	                                .clickable {
                                    isSearchSubmitted = false
                                    onQueryChange("")
                                }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Voice Search Mic Button
            IconButton(
                onClick = { /* Voice search action */ },
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Search",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Main Search Area
        Box(modifier = Modifier.fillMaxSize()) {
            if (query.isBlank()) {
                val context = LocalContext.current
                val historyManager = remember { SearchHistoryManager.getInstance(context) }
                var playedSongsHistory by remember { mutableStateOf(historyManager.getPlayedSongs()) }
                var recentArtistsHistory by remember { mutableStateOf(historyManager.getRecentArtists()) }
                var textQueriesHistory by remember { mutableStateOf(historyManager.getTextQueries()) }

                LaunchedEffect(Unit) {
                    playedSongsHistory = historyManager.getPlayedSongs()
                    recentArtistsHistory = historyManager.getRecentArtists()
                    textQueriesHistory = historyManager.getTextQueries()
                }

                val hasAnyHistory = playedSongsHistory.isNotEmpty() || recentArtistsHistory.isNotEmpty() || textQueriesHistory.isNotEmpty()

                if (hasAnyHistory) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        // A. Section Header: "Recent searches"
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent searches",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                )

                                Text(
                                    text = "Clear All",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = AccentTeal,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    modifier = Modifier.clickable {
                                        historyManager.clearAllHistory()
                                        playedSongsHistory = emptyList()
                                        recentArtistsHistory = emptyList()
                                        textQueriesHistory = emptyList()
                                        onClearHistory()
                                    }
                                )
                            }
                        }

                        // B. Top Played Songs Horizontal Rail Carousel (Only shown if user played songs)
                        if (playedSongsHistory.isNotEmpty()) {
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(playedSongsHistory) { track ->
                                        Column(
                                            modifier = Modifier
                                                .width(105.dp)
                                                .clickable {
                                                    onSongClick(track, playedSongsHistory, 0)
                                                }
                                        ) {
                                            AsyncImage(
                                                model = track.thumbnail,
                                                contentDescription = track.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(105.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = track.author,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    fontSize = 11.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }

                        // C. Visited Recent Artists List (Only shown if user visited/played artists)
                        if (recentArtistsHistory.isNotEmpty()) {
                            items(recentArtistsHistory) { artistItem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onArtistClick(artistItem.artistName) }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f))
                                    ) {
                                        AsyncImage(
                                            model = artistItem.avatarUrl.takeIf { !it.isNullOrBlank() }
                                                ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300",
                                            contentDescription = artistItem.artistName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = artistItem.artistName,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = artistItem.subtitle,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 12.sp
                                            )
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            historyManager.deleteRecentArtist(artistItem.artistName)
                                            recentArtistsHistory = historyManager.getRecentArtists()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Options",
                                            tint = TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // D. Text-Only Query History List (Queries typed & searched without direct play)
                        if (textQueriesHistory.isNotEmpty()) {
                            items(textQueriesHistory) { historyQuery ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            focusManager.clearFocus()
                                            onQueryChange(historyQuery)
                                            isSearchSubmitted = true
                                            onSearch(historyQuery)
                                        }
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(22.dp)
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Text(
                                        text = historyQuery,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 16.sp
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            onQueryChange(historyQuery)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CallMade,
                                            contentDescription = "Populate",
                                            tint = TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TextMuted.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Search Spasiba Music",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Search for songs, artists, or albums",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }
            } else if (!isSearchSubmitted) {
                // REAL-TIME AUTOCOMPLETE DROPDOWN MODE (YouTube Music get_search_suggestions)
                var suggestionsList by remember { mutableStateOf<List<YouTubeMusicApi.SuggestionItem>>(emptyList()) }
                var isSuggestionsLoading by remember { mutableStateOf(false) }

                LaunchedEffect(query) {
                    if (query.isNotBlank()) {
                        isSuggestionsLoading = true
                        delay(250L) // Debounce typing
                        suggestionsList = YouTubeMusicApi.fetchSearchSuggestions(query)
                        isSuggestionsLoading = false
                    } else {
                        suggestionsList = emptyList()
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    if (isSuggestionsLoading && suggestionsList.isEmpty()) {
                        items(5) {
                            SongTileSkeleton()
                        }
                    } else {
                        items(suggestionsList) { suggestion ->
                            when (suggestion) {
                                is YouTubeMusicApi.SuggestionItem.QuerySuggestion -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                focusManager.clearFocus()
                                                onQueryChange(suggestion.query)
                                                isSearchSubmitted = true
                                                onSearch(suggestion.query)
                                            }
                                            .padding(horizontal = 20.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (suggestion.isHistory) Icons.Default.History else Icons.Default.Search,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = suggestion.query,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 16.sp
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { onQueryChange(suggestion.query) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CallMade,
                                                contentDescription = "Populate",
                                                tint = TextMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                is YouTubeMusicApi.SuggestionItem.SongSuggestion -> {
                                    val song = suggestion.song
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSongClick(song, listOf(song), 0)
                                            }
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = song.thumbnail,
                                            contentDescription = song.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.title,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = song.author,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    fontSize = 12.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { onOptionsClick(song) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Options",
                                                tint = TextMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                is YouTubeMusicApi.SuggestionItem.ArtistSuggestion -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onArtistClick(suggestion.artistName) }
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.08f))
                                        ) {
                                            AsyncImage(
                                                model = suggestion.avatarUrl.takeIf { !it.isNullOrBlank() }
                                                    ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300",
                                                contentDescription = suggestion.artistName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = suggestion.artistName,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = suggestion.subtitle,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    fontSize = 12.sp
                                                )
                                            )
                                        }
                                        IconButton(
                                            onClick = { onArtistClick(suggestion.artistName) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Options",
                                                tint = TextMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // MODE B: EXECUTED SEARCH RESULTS VIEW (FOCUS ON 1ST MATCH + LIST VIEW)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 140.dp)
                ) {
                    if (isSearching) {
                        items(6) {
                            SongTileSkeleton()
                        }
                    } else if (processedSearchResults.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No songs found for \"$query\"",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextMuted,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }
                      } else {
                        // 1. TOP RESULT / FEATURED ARTIST MATCH HERO CARD (Matching YouTube Music Native Layout)
                        if (detectedArtist != null) {
                            val artistName = detectedArtist.artistName
                            val avatarImage = detectedArtist.avatarUrl.takeIf { !it.isNullOrBlank() }
                                ?: processedSearchResults.firstOrNull()?.thumbnail.orEmpty()
                            val top3EmbeddedSongs = detectedArtist.topSongs.take(3).ifEmpty { processedSearchResults.take(3) }

                            item {
                                Column(modifier = Modifier.padding(bottom = 20.dp)) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp)),
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color(0xFF1E1E22),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                    ) {
                                        Column(modifier = Modifier.padding(vertical = 14.dp)) {
                                            // A. Top Header Row (Avatar, Name, Subtitle, Chevron Arrow)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onArtistClick(artistName) }
                                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.08f))
                                                ) {
                                                    AsyncImage(
                                                        model = avatarImage,
                                                        contentDescription = artistName,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(CircleShape)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(14.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = artistName,
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = TextStrong,
                                                            fontSize = 18.sp
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    Spacer(modifier = Modifier.height(3.dp))

                                                    Text(
                                                        text = "Official Artist Profile • Tap to view all",
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            color = TextMuted,
                                                            fontSize = 12.sp
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = "Open Artist",
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            // B. Action Buttons Row ([ 🔀 Shuffle ] and [ ((►)) Mix Radio ])
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                // Solid Shuffle Pill Button
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(42.dp)
                                                        .clickable {
                                                            if (top3EmbeddedSongs.isNotEmpty()) {
                                                                onSongClick(top3EmbeddedSongs.shuffled().first(), top3EmbeddedSongs, 0)
                                                            }
                                                        },
                                                    shape = RoundedCornerShape(21.dp),
                                                    color = Color.White
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxSize(),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Shuffle,
                                                            contentDescription = "Shuffle",
                                                            tint = Color.Black,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "Shuffle",
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                color = Color.Black,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 14.sp
                                                            )
                                                        )
                                                    }
                                                }

                                                // Outlined Mix / Radio Pill Button
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(42.dp)
                                                        .clickable {
                                                            if (top3EmbeddedSongs.isNotEmpty()) {
                                                                onSongClick(top3EmbeddedSongs.first(), top3EmbeddedSongs, 0)
                                                            }
                                                        },
                                                    shape = RoundedCornerShape(21.dp),
                                                    color = Color.White.copy(alpha = 0.08f),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxSize(),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = "Mix",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "Mix Radio",
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 14.sp
                                                            )
                                                        )
                                                    }
                                                }
                                            }

                                            // C. Embedded Top 3 Popular Songs Inside the Card
                                            if (top3EmbeddedSongs.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(14.dp))
                                                androidx.compose.material3.HorizontalDivider(
                                                    color = Color.White.copy(alpha = 0.08f),
                                                    thickness = 1.dp
                                                )

                                                Column(modifier = Modifier.padding(top = 4.dp)) {
                                                    top3EmbeddedSongs.forEachIndexed { idx, song ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable { onSongClick(song, top3EmbeddedSongs, idx) }
                                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            AsyncImage(
                                                                model = song.thumbnail,
                                                                contentDescription = song.title,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier
                                                                    .size(46.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                            )

                                                            Spacer(modifier = Modifier.width(12.dp))

                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = song.title,
                                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                                        color = TextStrong,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 14.sp
                                                                    ),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = "Song • ${song.author}",
                                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                                        color = TextMuted,
                                                                        fontSize = 12.sp
                                                                    ),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }

                                                            IconButton(
                                                                onClick = { onOptionsClick(song) },
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.MoreVert,
                                                                    contentDescription = "Options",
                                                                    tint = TextMuted,
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

                        // 2. REMAINING RESULTS LIST VIEW
                        item {
                            Text(
                                text = "SONGS & MUSIC VIDEOS (${processedSearchResults.size})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.4.sp
                                ),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        itemsIndexed(processedSearchResults) { index, song ->
                            SongTile(
                                song = song,
                                isPlaying = song.id == currentSong?.id,
                                isLiked = likedSongs.contains(song.id),
                                onSongClick = { onSongClick(song, processedSearchResults, index) },
                                onLikeClick = { onLikeClick(song.id) },
                                onOptionsClick = { onOptionsClick(song) },
                                onSwipeToQueue = { onSwipeToQueue(song) }
                            )
                        }
                    }
                }
            }
        }
    }
}
