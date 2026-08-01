package app.spasiba.nativeapp2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onBackClick: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var isSearchSubmitted by remember { mutableStateOf(false) }

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
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
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
	                            textStyle = MaterialTheme.typography.bodyMedium.copy(
	                                color = TextStrong,
	                                fontSize = 16.sp
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
                // Initial State: Show Recent Search Queries (Limit 6) or Empty Prompt
                val limitedHistory = remember(searchHistory) { searchHistory.take(6) }

                if (limitedHistory.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "RECENT SEARCHES",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    )
                                )

                                Text(
                                    text = "Clear All",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = AccentTeal,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.clickable { onClearHistory() }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            limitedHistory.forEach { historyItem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            focusManager.clearFocus()
                                            onQueryChange(historyItem)
                                            isSearchSubmitted = true
                                            onSearch(historyItem)
                                        }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = AccentTeal,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = historyItem,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = TextStrong,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteHistoryItem(historyItem) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Delete",
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
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
                                tint = TextMuted.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Search millions of songs & artists",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }
            } else if (!isSearchSubmitted) {
                // MODE A: LIVE TYPING DROPDOWN (First 2 Text Name Matching + Next 15 Photo Track Cards)
                val textSuggestions = remember(searchResults, query) {
                    if (searchResults.isNotEmpty()) {
                        listOf(
                            searchResults[0].title,
                            if (searchResults.size > 1) searchResults[1].title else "$query hits"
                        )
                    } else if (query.isNotBlank()) {
                        listOf(query, "$query official song")
                    } else emptyList()
                }
                val photoSuggestions = remember(searchResults) { searchResults.take(15) }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    if (isSearching) {
                        items(6) {
                            SongTileSkeleton()
                        }
                    } else {
                        // Section 1: First 2 Name / Text matching suggestions (With insert Arrow on right)
                        itemsIndexed(textSuggestions) { _, suggestText ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        focusManager.clearFocus()
                                        onQueryChange(suggestText)
                                        isSearchSubmitted = true
                                        onSearch(suggestText)
                                    },
                                color = Color.White.copy(alpha = 0.04f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Text(
                                        text = suggestText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = TextStrong,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        ),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    IconButton(
                                        onClick = { onQueryChange(suggestText) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CallMade,
                                            contentDescription = "Insert text",
                                            tint = TextMuted,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .graphicsLayer { rotationZ = -90f }
                                        )
                                    }
                                }
                            }
                        }

                        // Section 2: Next 15 Photo Album Art Track Cards (With 3-dots on right)
                        itemsIndexed(photoSuggestions) { index, song ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        focusManager.clearFocus()
                                        onSongClick(song, photoSuggestions, index)
                                    },
                                color = Color.White.copy(alpha = 0.04f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = song.thumbnail,
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
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
                    } else if (searchResults.isNotEmpty()) {
                        val topMatch = searchResults.first()
                        val remainingResults = searchResults.drop(1)

                        // 1. TOP RESULT / FEATURED FIRST MATCH HERO CARD
                        item {
                            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                                Text(
                                    text = "TOP MATCH RESULT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = AccentTeal,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.4.sp
                                    ),
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { onSongClick(topMatch, searchResults, 0) },
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF1E293B).copy(alpha = 0.95f),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentTeal.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = topMatch.thumbnail,
                                                contentDescription = topMatch.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(90.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            )

                                            Spacer(modifier = Modifier.width(16.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Surface(
                                                    color = AccentTeal.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = "BEST MATCH",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = AccentTeal,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 9.sp,
                                                            letterSpacing = 1.2.sp
                                                        ),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))

                                                Text(
                                                    text = topMatch.title,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextStrong,
                                                        fontSize = 17.sp
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Text(
                                                    text = "${topMatch.author} • ${topMatch.durationFormatted}",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        color = TextMuted,
                                                        fontSize = 13.sp
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = AccentTeal,
                                                modifier = Modifier.clickable { onSongClick(topMatch, searchResults, 0) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Play Top Match",
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "PLAY NOW",
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            color = Color.Black,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 12.sp,
                                                            letterSpacing = 1.0.sp
                                                        )
                                                    )
                                                }
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                val isLiked = likedSongs.contains(topMatch.id)
                                                IconButton(
                                                    onClick = { onLikeClick(topMatch.id) },
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.08f))
                                                ) {
                                                    Icon(
                                                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                        contentDescription = "Like",
                                                        tint = if (isLiked) AccentTeal else Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { onOptionsClick(topMatch) },
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.08f))
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreVert,
                                                        contentDescription = "Options",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. REMAINING RESULTS LIST VIEW
                        if (remainingResults.isNotEmpty()) {
                            item {
                                Text(
                                    text = "OTHER SONGS & TRACKS (${remainingResults.size})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextMuted,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.4.sp
                                    ),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            itemsIndexed(remainingResults) { index, song ->
                                SongTile(
                                    song = song,
                                    isPlaying = song.id == currentSong?.id,
                                    isLiked = likedSongs.contains(song.id),
                                    onSongClick = { onSongClick(song, searchResults, index + 1) },
                                    onLikeClick = { onLikeClick(song.id) },
                                    onOptionsClick = { onOptionsClick(song) },
                                    onSwipeToQueue = { onSwipeToQueue(song) }
                                )
                            }
                        }
                    } else {
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
                    }
                }
            }
        }
    }
}
