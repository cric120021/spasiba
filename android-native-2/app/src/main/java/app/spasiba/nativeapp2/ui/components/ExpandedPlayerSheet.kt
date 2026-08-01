package app.spasiba.nativeapp2.ui.components

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.player.NativePlayerManager
import app.spasiba.nativeapp2.player.RepeatState
import app.spasiba.nativeapp2.ui.theme.AccentTeal
import app.spasiba.nativeapp2.ui.theme.TextMuted
import app.spasiba.nativeapp2.utils.ShareUtils
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ExpandedPlayerSheet(
    playerManager: NativePlayerManager,
    song: Song,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    isLiked: Boolean,
    onCollapse: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onLikeClick: () -> Unit,
    onOptionsClick: (Song) -> Unit,
    onSongClickInQueue: (Song) -> Unit,
    repeatState: RepeatState = RepeatState.OFF,
    onRepeatClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentQuality by playerManager.videoQuality.collectAsState()

    var isVideoModeActive by remember { mutableStateOf(false) }
    var isFullscreenVideoOpen by remember { mutableStateOf(false) }
    var showVideoControls by remember { mutableStateOf(true) }
    var activeTab by remember { mutableStateOf("UP NEXT") }
    var isQualityDialogOpen by remember { mutableStateOf(false) }
    var isOptionsMenuExpanded by remember { mutableStateOf(false) }

    val upcomingQueue by playerManager.queue.collectAsState()
    val isLoading by playerManager.isLoading.collectAsState()

    var sheetDragOffset by remember { mutableStateOf(0f) }
    var artDragOffset by remember { mutableStateOf(0f) }

    val durSec = if (durationMs > 0) durationMs / 1000L else song.durationSeconds.toLong()
    val posSec = positionMs / 1000L
    val sliderProgress = if (durSec > 0) (posSec.toFloat() / durSec.toFloat()).coerceIn(0f, 1f) else 0f

    val posFormatted = String.format("%d:%02d", posSec / 60, posSec % 60)
    val durFormatted = String.format("%d:%02d", durSec / 60, durSec % 60)

    val lyricsText by playerManager.currentLyrics.collectAsState()
    val isLyricsLoading by playerManager.isLyricsLoading.collectAsState()

    LaunchedEffect(song.id, activeTab) {
        if (activeTab == "LYRICS") {
            playerManager.fetchLyricsForCurrentSong()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090D))
            .offset { IntOffset(0, sheetDragOffset.roundToInt().coerceAtLeast(0)) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (sheetDragOffset > 200f) {
                            onCollapse()
                        }
                        sheetDragOffset = 0f
                    },
                    onDragCancel = { sheetDragOffset = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 0 || sheetDragOffset > 0) {
                            sheetDragOffset += dragAmount
                        }
                    }
                )
            }
    ) {
        AsyncImage(
            model = song.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(90.dp)
                .scale(1.25f)
                .background(Color.Black.copy(alpha = 0.72f))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color(0xFF0F0F16).copy(alpha = 0.92f),
                            Color(0xFF07070B)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (!isVideoModeActive) Color.White else Color.Transparent,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { isVideoModeActive = false }
                        ) {
                            Text(
                                text = "Audio",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isVideoModeActive) Color.Black else Color.White,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (isVideoModeActive) Color.White else Color.Transparent,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    isVideoModeActive = true
                                    playerManager.setVideoQuality(currentQuality)
                                }
                        ) {
                            Text(
                                text = "Video",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isVideoModeActive) Color.Black else Color.White,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { isOptionsMenuExpanded = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = isOptionsMenuExpanded,
                        onDismissRequest = { isOptionsMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Video Quality ($currentQuality)") },
                            onClick = {
                                isOptionsMenuExpanded = false
                                isQualityDialogOpen = true
                            },
                            trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Change Quality") }
                        )
                        DropdownMenuItem(
                            text = { Text("Save to Playlist") },
                            onClick = {
                                isOptionsMenuExpanded = false
                                onOptionsClick(song)
                            }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isVideoModeActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.Black)
                                        .clickable {
                                            if (isPlaying) showVideoControls = !showVideoControls else onPlayPauseClick()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.ui.viewinterop.AndroidView(
                                        factory = { ctx ->
                                            androidx.media3.ui.PlayerView(ctx).apply {
                                                player = playerManager.exoPlayer
                                                useController = false
                                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                setBackgroundColor(android.graphics.Color.BLACK)
                                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                            }
                                        },
                                        update = { playerView ->
                                            playerView.player = playerManager.exoPlayer
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (!isPlaying || isLoading || showVideoControls) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.55f))
                                                .clickable {
                                                    onPlayPauseClick()
                                                    if (isPlaying) showVideoControls = false
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isLoading) {
                                                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.White, strokeWidth = 2.5.dp)
                                            } else {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Play/Pause",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(12.dp)
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.55f))
                                            .clickable { isFullscreenVideoOpen = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = Icons.Default.Fullscreen, contentDescription = "Maximize Video", tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }
                            } else {
                                AsyncImage(
                                    model = song.thumbnail,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(250.dp)
                                        .offset { IntOffset(artDragOffset.roundToInt(), 0) }
                                        .pointerInput(song.id) {
                                            detectHorizontalDragGestures(
                                                onDragEnd = {
                                                    if (artDragOffset < -80f) {
                                                        onNextClick()
                                                    } else if (artDragOffset > 80f) {
                                                        onPreviousClick()
                                                    }
                                                    artDragOffset = 0f
                                                },
                                                onDragCancel = { artDragOffset = 0f },
                                                onHorizontalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    artDragOffset = (artDragOffset + dragAmount).coerceIn(-150f, 150f)
                                                }
                                            )
                                        }
                                        .clip(RoundedCornerShape(20.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 20.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = song.author,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 13.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = onLikeClick) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Like",
                                        tint = if (isLiked) Color(0xFFEF4444) else Color.White
                                    )
                                }
                                IconButton(onClick = { ShareUtils.shareSong(context, song) }) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color.White.copy(alpha = 0.85f))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Column {
                            Slider(
                                value = sliderProgress,
                                onValueChange = { frac ->
                                    val targetMs = (frac * durSec * 1000).toLong()
                                    onSeekTo(targetMs)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(posFormatted, style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp))
                                Text(durFormatted, style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { }) {
                                Icon(imageVector = Icons.Default.Shuffle, contentDescription = "Shuffle", tint = Color.White.copy(alpha = 0.7f))
                            }

                            IconButton(
                                onClick = onPreviousClick,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                            ) {
                                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                            }

                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(Color.White, CircleShape)
                                    .clickable { onPlayPauseClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.Black,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onNextClick,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                            ) {
                                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                            }

                            IconButton(onClick = onRepeatClick) {
                                Icon(
                                    imageVector = if (repeatState == RepeatState.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    contentDescription = "Repeat",
                                    tint = if (repeatState != RepeatState.OFF) Color.White else Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("UP NEXT", "LYRICS").forEach { tab ->
                                    val isSelected = activeTab == tab
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(CircleShape)
                                            .clickable { activeTab = tab }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = tab,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f),
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                if (activeTab == "UP NEXT") {
                    itemsIndexed(upcomingQueue) { idx, qSong ->
                        val isCurrent = qSong.id == song.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { onSongClickInQueue(qSong) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = qSong.thumbnail,
                                contentDescription = qSong.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = qSong.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) AccentTeal else Color.White
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = qSong.author,
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else if (activeTab == "LYRICS") {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            if (isLyricsLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .align(Alignment.Center),
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = lyricsText ?: "No synchronized lyrics available for this track.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 15.sp,
                                        lineHeight = 24.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isFullscreenVideoOpen) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { isFullscreenVideoOpen = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable {
                            if (isPlaying) showVideoControls = !showVideoControls else onPlayPauseClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            androidx.media3.ui.PlayerView(ctx).apply {
                                player = playerManager.exoPlayer
                                useController = false
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setBackgroundColor(android.graphics.Color.BLACK)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        update = { playerView ->
                            playerView.player = playerManager.exoPlayer
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (!isPlaying || isLoading || showVideoControls) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold), maxLines = 1)
                                Text(song.author, style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f)), maxLines = 1)
                            }
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { isFullscreenVideoOpen = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable {
                                    onPlayPauseClick()
                                    if (isPlaying) showVideoControls = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.White, strokeWidth = 2.5.dp)
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Slider(
                                    value = sliderProgress,
                                    onValueChange = { frac ->
                                        val targetMs = (frac * durSec * 1000).toLong()
                                        onSeekTo(targetMs)
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(posFormatted, style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                                    Text(durFormatted, style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isQualityDialogOpen) {
            val qualities = listOf("Low (360p)", "Medium (480p)", "High (720p)", "Auto")
            AlertDialog(
                onDismissRequest = { isQualityDialogOpen = false },
                containerColor = Color(0xFF181822),
                title = { Text("Select Video Quality", style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)) },
                text = {
                    Column {
                        qualities.forEach { q ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playerManager.setVideoQuality(q)
                                        isQualityDialogOpen = false
                                        Toast.makeText(context, "Video quality set to $q", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (currentQuality == q),
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentTeal)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(q, style = MaterialTheme.typography.bodyMedium.copy(color = Color.White))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isQualityDialogOpen = false }) {
                        Text("Close", color = AccentTeal)
                    }
                }
            )
        }
    }
}
