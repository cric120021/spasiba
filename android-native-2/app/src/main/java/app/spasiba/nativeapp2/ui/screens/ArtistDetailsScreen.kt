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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Share
import java.net.URLEncoder
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.spasiba.nativeapp2.data.api.YouTubeMusicApi
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.ui.components.SongTileSkeleton
import app.spasiba.nativeapp2.ui.theme.AccentTeal
import app.spasiba.nativeapp2.ui.theme.TextMuted
import app.spasiba.nativeapp2.ui.theme.TextStrong
import coil.compose.AsyncImage
import kotlinx.coroutines.async

@Composable
fun ArtistDetailsScreen(
    artistName: String,
    currentSong: Song?,
    likedSongs: Set<String>,
    onBackClick: () -> Unit,
    onSongClick: (Song, List<Song>, Int) -> Unit,
    onLikeClick: (String) -> Unit,
    onOptionsClick: (Song) -> Unit
) {
    var top50Songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var recentVideos by remember { mutableStateOf<List<Song>>(emptyList()) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(artistName) {
        isLoading = true
        val pageData = YouTubeMusicApi.fetchOfficialArtistPage(artistName)
        avatarUrl = pageData.avatarUrl ?: pageData.bannerUrl
        top50Songs = pageData.topSongs
        recentVideos = pageData.recentVideos
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0F))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // 1. YouTube Music Dynamic Immersive Header Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    val displayAvatar = avatarUrl ?: top50Songs.firstOrNull()?.thumbnail ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600"
                    
                    AsyncImage(
                        model = displayAvatar,
                        contentDescription = artistName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Dark Immersive Vignette Blend
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Transparent,
                                        Color(0xFF0D0D0F).copy(alpha = 0.7f),
                                        Color(0xFF0D0D0F)
                                    )
                                )
                            )
                    )

                    // Top Action Bar Icons (Back, Radio Mix, Share)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 12.dp, end = 12.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val context = LocalContext.current

                            IconButton(
                                onClick = {
                                    if (top50Songs.isNotEmpty()) onSongClick(top50Songs.first(), top50Songs, 0)
                                },
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Radio Mix",
                                    tint = Color.White
                                )
                            }

                            IconButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, artistName)
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Listen to $artistName on YouTube Music: https://music.youtube.com/search?q=${runCatching { URLEncoder.encode(artistName, "UTF-8") }.getOrDefault(artistName)}"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Artist Profile"))
                                },
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Artist Title & Monthly Audience Text
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 36.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Official Artist Profile • YouTube Music",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }

            // 2. Action Control Bar (Subscribe Pill, Radio Icon, Circular White Play Button)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Subscribe Pill Button
                        var isSubscribed by remember { mutableStateOf(false) }
                        Surface(
                            modifier = Modifier
                                .height(44.dp)
                                .clickable { isSubscribed = !isSubscribed },
                            shape = RoundedCornerShape(22.dp),
                            color = if (isSubscribed) Color.White.copy(alpha = 0.15f) else Color.White
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isSubscribed) "Subscribed" else "Subscribe",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isSubscribed) Color.White else Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }

                        // Shuffle Pill Button
                        Surface(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (top50Songs.isNotEmpty()) {
                                        val shuffled = top50Songs.shuffled()
                                        onSongClick(shuffled.first(), shuffled, 0)
                                    }
                                },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Main Big Circular Play Button
                    Surface(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (top50Songs.isNotEmpty()) onSongClick(top50Songs.first(), top50Songs, 0)
                            },
                        shape = CircleShape,
                        color = Color.White
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // 3. Featured Single / Release Card
            if (top50Songs.isNotEmpty()) {
                item {
                    val latestSingle = top50Songs.first()
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSongClick(latestSingle, top50Songs, 0) },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E1E22),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = latestSingle.thumbnail,
                                contentDescription = latestSingle.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Listen to the top single",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = latestSingle.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Open Single",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // 3. SECTION 1: TOP 20 MOST RECENT CHANNEL VIDEOS (Widescreen Video Carousel)
            if (recentVideos.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Latest Official Videos",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextStrong,
                                    fontSize = 18.sp
                                )
                            )
                            Text(
                                text = "Top 20",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentTeal,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(recentVideos) { idx, video ->
                                Column(
                                    modifier = Modifier
                                        .width(220.dp)
                                        .clickable { onSongClick(video, recentVideos, idx) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(220.dp)
                                            .height(124.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    ) {
                                        AsyncImage(
                                            model = video.thumbnail,
                                            contentDescription = video.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color.Red.copy(alpha = 0.85f),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                        ) {
                                            Text(
                                                text = "OFFICIAL VIDEO",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp
                                                ),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = video.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 13.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = video.author,
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

            // 4. SECTION 2: TOP 50 MOST LISTENED SONGS
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Top 50 Most Listened Songs",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextStrong,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "${top50Songs.size} Tracks",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    )
                }
            }

            // 5. Songs List or Loading Skeleton
            if (isLoading) {
                items(8) {
                    SongTileSkeleton()
                }
            } else if (top50Songs.isEmpty()) {
                item {
                    Text(
                        text = "No songs found for $artistName.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
                        modifier = Modifier.padding(20.dp)
                    )
                }
            } else {
                itemsIndexed(top50Songs) { index, song ->
                    val isPlayingThis = song.id == currentSong?.id
                    val isLiked = likedSongs.contains(song.id)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSongClick(song, top50Songs, index) }
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isPlayingThis) Color(0xFF1DB954) else Color.White.copy(alpha = 0.5f),
                                fontSize = 16.sp
                            ),
                            modifier = Modifier.width(32.dp)
                        )

                        AsyncImage(
                            model = song.thumbnail,
                            contentDescription = song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPlayingThis) Color(0xFF1DB954) else TextStrong,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${song.author} • ${song.durationFormatted}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { onLikeClick(song.id) }) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (isLiked) Color(0xFFEF4444) else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = { onOptionsClick(song) }) {
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
