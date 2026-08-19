package app.spasiba.nativeapp2.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.ui.theme.AccentTeal
import app.spasiba.nativeapp2.ui.theme.TextMuted
import app.spasiba.nativeapp2.ui.theme.TextStrong
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongTile(
    song: Song,
    isPlaying: Boolean = false,
    isLiked: Boolean = false,
    isDownloaded: Boolean = false,
    enableSwipe: Boolean = false,
    onSongClick: () -> Unit,
    onLikeClick: () -> Unit = {},
    onOptionsClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onSwipeToQueue: () -> Unit = {}
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
    ) {
        // Swipe Background Action Indicators (Only when enableSwipe is true)
        if (enableSwipe) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (offsetX.value > 0) AccentTeal.copy(alpha = 0.25f)
                        else Color(0xFF10B981).copy(alpha = 0.25f)
                    )
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (offsetX.value > 0) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Like Track",
                        tint = AccentTeal,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                } else if (offsetX.value < 0) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.QueuePlayNext,
                        contentDescription = "Queue Next",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Foreground Song Content Tile
        Row(
            modifier = Modifier
                .then(
                    if (enableSwipe) Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    else Modifier
                )
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isPlaying) Color.White.copy(alpha = 0.12f)
                    else Color.Transparent
                )
                .combinedClickable(
                    onClick = { onSongClick() },
                    onLongClick = { onLongClick?.invoke() }
                )
                .then(
                    if (enableSwipe) {
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (offsetX.value > 120f) {
                                            onLikeClick()
                                        } else if (offsetX.value < -120f) {
                                            onSwipeToQueue()
                                        }
                                        offsetX.animateTo(0f, animationSpec = tween(250))
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    scope.launch {
                                        val newOffset = (offsetX.value + dragAmount).coerceIn(-180f, 180f)
                                        offsetX.snapTo(newOffset)
                                    }
                                }
                            )
                        }
                    } else Modifier
                )
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.thumbnail,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPlaying) {
                        EqualizerWaveformAnimation(isPlaying = true)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (isPlaying) AccentTeal else TextStrong
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (song.title.contains("explicit", ignoreCase = true)) {
                        SongBadge(text = "E", color = Color(0x33FF0055), textColor = Color(0xFFFF2A6D))
                    }
                    if (song.title.contains("official", ignoreCase = true) || song.views > 1_000_000L) {
                        SongBadge(text = "HD", color = Color(0x3310B981), textColor = Color(0xFF10B981))
                    }
                    val displayAuthor = if (song.author.isNotBlank() && !song.author.equals("Artist", ignoreCase = true) && !song.author.equals("YouTube Music", ignoreCase = true)) song.author else "Spasiba Music"
                    val durationInfo = if (song.durationSeconds > 0) " • ${song.durationFormatted}" else ""
                    Text(
                        text = "$displayAuthor$durationInfo",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextMuted,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onLikeClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) AccentTeal else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SongBadge(
    text: String,
    color: Color = Color(0x332DD4BF),
    textColor: Color = AccentTeal
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        )
    }
}
