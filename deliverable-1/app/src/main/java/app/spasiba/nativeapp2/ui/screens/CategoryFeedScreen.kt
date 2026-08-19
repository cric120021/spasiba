package app.spasiba.nativeapp2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp2.data.model.CategoryChip
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.ui.components.SongTile
import app.spasiba.nativeapp2.ui.components.SongTileSkeleton
import app.spasiba.nativeapp2.ui.theme.AccentTeal
import app.spasiba.nativeapp2.ui.theme.TextMuted
import app.spasiba.nativeapp2.ui.theme.TextStrong
import app.spasiba.nativeapp2.ui.theme.getCategoryAccentColor
import coil.compose.AsyncImage

@Composable
fun CategoryFeedScreen(
    category: CategoryChip,
    categoryFeedSongs: List<Song>,
    currentSong: Song?,
    likedSongs: Set<String>,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onSongClick: (Song, List<Song>, Int) -> Unit,
    onLikeClick: (String) -> Unit,
    onOptionsClick: (Song) -> Unit = {},
    onSwipeToQueue: (Song) -> Unit = {}
) {
    val accentColor = remember(category.id) { getCategoryAccentColor(category.id) }
    val quickPicks = remember(categoryFeedSongs) { categoryFeedSongs.take(8) }
    val quickPickColumns = remember(quickPicks) { quickPicks.chunked(4) }
    val mixedTracks = remember(categoryFeedSongs) { categoryFeedSongs.drop(4).take(6) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // 1. Category Hero Banner Header (matching CategoryFeedPage.tsx)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accentColor.copy(alpha = 0.45f),
                                accentColor.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SPASIBA CATEGORY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 2.2.sp
                            )
                        )
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 28.sp
                            )
                        )
                    }

                    if (categoryFeedSongs.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onSongClick(categoryFeedSongs.first(), categoryFeedSongs, 0) },
                            color = Color.White
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play All",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PLAY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.2.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Category Quick Picks (2-Column Grid Layout)
        item {
            if (quickPickColumns.isNotEmpty()) {
                Column(modifier = Modifier.padding(bottom = 20.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${category.label} Quick Picks",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(quickPickColumns) { columnTracks ->
                            Column(
                                modifier = Modifier.width(300.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                columnTracks.forEach { track ->
                                    val isCurrentPlaying = track.id == currentSong?.id

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                val trackIndex = categoryFeedSongs.indexOfFirst { it.id == track.id }
                                                onSongClick(track, categoryFeedSongs, if (trackIndex >= 0) trackIndex else 0)
                                            },
                                        color = if (isCurrentPlaying) Color.White.copy(alpha = 0.14f) else Color.Transparent
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = track.thumbnail,
                                                contentDescription = track.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = track.title,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color.White,
                                                        fontSize = 13.sp
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${track.author} • ${track.durationFormatted}",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = Color.White.copy(alpha = 0.6f),
                                                        fontSize = 11.sp
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            IconButton(
                                                onClick = { onOptionsClick(track) },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.08f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "Options",
                                                    tint = Color.White.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(16.dp)
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

        // 3. Category Mixed Cards Section
        item {
            if (!isLoading && mixedTracks.isNotEmpty()) {
                Column(modifier = Modifier.padding(bottom = 20.dp)) {
                    Text(
                        text = "${category.label} Mixes",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(mixedTracks) { index, song ->
                            Column(
                                modifier = Modifier
                                    .width(150.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onSongClick(song, categoryFeedSongs, index) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(150.dp)
                                        .clip(RoundedCornerShape(16.dp))
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
                                        shape = RoundedCornerShape(12.dp),
                                        color = accentColor.copy(alpha = 0.75f),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "${category.label.uppercase()} ${index + 1}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                letterSpacing = 1.2.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
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
                                        color = TextMuted,
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

        // 4. All Category Songs List Section
        item {
            Text(
                text = "All ${category.label} Tracks",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextStrong
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (isLoading) {
            items(6) {
                SongTileSkeleton()
            }
        } else {
            itemsIndexed(categoryFeedSongs) { index, song ->
                SongTile(
                    song = song,
                    isPlaying = song.id == currentSong?.id,
                    isLiked = likedSongs.contains(song.id),
                    onSongClick = { onSongClick(song, categoryFeedSongs, index) },
                    onLikeClick = { onLikeClick(song.id) },
                    onOptionsClick = { onOptionsClick(song) },
                    onSwipeToQueue = { onSwipeToQueue(song) }
                )
            }
        }
    }
}
