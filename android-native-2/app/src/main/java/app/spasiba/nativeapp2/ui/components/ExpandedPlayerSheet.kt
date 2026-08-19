package app.spasiba.nativeapp2.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoLibrary
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

import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone

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
    val downloadManager = remember(context) { app.spasiba.nativeapp2.data.download.SpasibaDownloadManager.getInstance(context) }
    val downloadsState by downloadManager.downloads.collectAsState()
    val downloadItem = downloadsState[song.id]

    val currentQuality by playerManager.videoQuality.collectAsState()
    val videoAspectRatio by playerManager.videoAspectRatio.collectAsState()
    var isVideoModeActive by remember { mutableStateOf(false) }
    var isFullscreenVideoOpen by remember { mutableStateOf(false) }
    var showVideoControls by remember { mutableStateOf(true) }
    var isVideoZoomed by remember { mutableStateOf(false) }
    var doubleTapSeekFeedback by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf("UP NEXT") }
    var isQualityDialogOpen by remember { mutableStateOf(false) }
    var isDownloadDialogOpen by remember { mutableStateOf(false) }
    var isDownloadQualityPickerOpen by remember { mutableStateOf(false) }
    var isOptionsMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(showVideoControls, isPlaying, isVideoModeActive) {
        if (isVideoModeActive && showVideoControls && isPlaying) {
            delay(3500)
            showVideoControls = false
        }
    }

    LaunchedEffect(isFullscreenVideoOpen) {
        val activity = context as? android.app.Activity
        if (activity != null) {
            if (isFullscreenVideoOpen) {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    activity.window.insetsController?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                    activity.window.insetsController?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    @Suppress("DEPRECATION")
                    activity.window.decorView.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
                }
            } else {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    activity.window.insetsController?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                } else {
                    @Suppress("DEPRECATION")
                    activity.window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
    }

    LaunchedEffect(doubleTapSeekFeedback) {
        if (doubleTapSeekFeedback != null) {
            delay(800)
            doubleTapSeekFeedback = null
        }
    }

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
    val syncedLines by playerManager.syncedLyrics.collectAsState()
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
        if (!isVideoModeActive) {
            val (dominantColor, darkMutedColor) = rememberPaletteColors(
                imageUrl = song.thumbnail,
                defaultDominant = Color(0xFF1E1C1A),
                defaultDark = Color(0xFF0F0E0C)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to dominantColor,
                            0.45f to dominantColor.copy(alpha = 0.90f),
                            0.80f to darkMutedColor,
                            1.0f to Color.Black
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF020203))
            )
        }

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
                                .clickable {
                                    isVideoModeActive = false
                                    playerManager.disableVideoMode()
                                }
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
                        onDismissRequest = { isOptionsMenuExpanded = false },
                        modifier = Modifier.background(Color(0xFF121214))
                    ) {
                        // 1. Video Quality (Icon First + Trailing Chevron >)
                        DropdownMenuItem(
                            text = { Text("Video Quality ($currentQuality)", color = Color.White) },
                            onClick = {
                                isOptionsMenuExpanded = false
                                isQualityDialogOpen = true
                            },
                            leadingIcon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Video Quality", tint = Color.White) },
                            trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Change Quality", tint = Color.White.copy(alpha = 0.7f)) }
                        )

                        // 2. Download Track (Icon First)
                        val isDownloaded = remember(downloadsState, song.id) {
                            downloadsState[song.id]?.status == app.spasiba.nativeapp2.data.download.DownloadStatus.COMPLETED || downloadManager.getDownloadedFile(song.id) != null
                        }

                        if (isDownloaded) {
                            DropdownMenuItem(
                                text = { Text("Remove download", color = Color.White) },
                                onClick = {
                                    isOptionsMenuExpanded = false
                                    downloadManager.deleteDownload(song.id)
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Remove download", tint = Color.White) }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Download Track", color = Color.White) },
                                onClick = {
                                    isOptionsMenuExpanded = false
                                    isDownloadDialogOpen = true
                                },
                                leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = "Download Track", tint = AccentTeal) }
                            )
                        }

                        // 3. Add to Playlist (Icon First)
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = Color.White) },
                            onClick = {
                                isOptionsMenuExpanded = false
                                onOptionsClick(song)
                            },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to Playlist", tint = Color.White) }
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                                if (isVideoModeActive) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .background(Color.Black)
                                            .clickable { showVideoControls = !showVideoControls },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!isFullscreenVideoOpen) {
                                            androidx.compose.ui.viewinterop.AndroidView(
                                                factory = { ctx ->
                                                    val inflater = android.view.LayoutInflater.from(ctx)
                                                    (inflater.inflate(app.spasiba.nativeapp2.R.layout.spasiba_player_view, null, false) as androidx.media3.ui.PlayerView).apply {
                                                        player = playerManager.exoPlayer
                                                        resizeMode = if (isVideoZoomed) {
                                                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                        } else {
                                                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                        }
                                                    }
                                                },
                                                update = { playerView ->
                                                    if (playerView.player != playerManager.exoPlayer) {
                                                        playerView.player = playerManager.exoPlayer
                                                    }
                                                    playerView.resizeMode = if (isVideoZoomed) {
                                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                      } else {
                                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = !isPlaying || isLoading || showVideoControls,
                                        enter = fadeIn(),
                                        exit = fadeOut()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.5f))
                                        ) {
                                            // Top Right Fullscreen Maximize Button
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .align(Alignment.TopCenter)
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = { isFullscreenVideoOpen = true },
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Black.copy(alpha = 0.55f))
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Fullscreen,
                                                        contentDescription = "Landscape Fullscreen Mode",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        val newPos = (positionMs - 10000L).coerceAtLeast(0L)
                                                        onSeekTo(newPos)
                                                        doubleTapSeekFeedback = "-10s"
                                                    },
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Black.copy(alpha = 0.45f))
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Replay10,
                                                        contentDescription = "Rewind 10s",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .size(60.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.25f))
                                                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                                        .clickable { onPlayPauseClick() },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isLoading) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(28.dp),
                                                            color = Color.White,
                                                            strokeWidth = 2.5.dp
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                            contentDescription = "Play/Pause",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(36.dp)
                                                        )
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val newPos = (positionMs + 10000L).coerceAtMost(durationMs)
                                                        onSeekTo(newPos)
                                                        doubleTapSeekFeedback = "+10s"
                                                    },
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Black.copy(alpha = 0.45f))
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Forward10,
                                                        contentDescription = "Forward 10s",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (isLoading) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = song.thumbnail,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .alpha(0.5f)
                                            )
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 3.dp,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                AsyncImage(
                                    model = song.thumbnail,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth(0.82f)
                                        .aspectRatio(1f)
                                        .offset { IntOffset(artDragOffset.roundToInt(), 0) }
                                        .pointerInput(song.id) {
                                            detectHorizontalDragGestures(
                                                onDragEnd = {
                                                    if (artDragOffset > 80f) {
                                                        onPreviousClick()
                                                    } else if (artDragOffset < -80f) {
                                                        onNextClick()
                                                    }
                                                    artDragOffset = 0f
                                                },
                                                onHorizontalDrag = { _, dragAmount ->
                                                    artDragOffset += dragAmount
                                                }
                                            )
                                        }
                                        .clip(RoundedCornerShape(24.dp))
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.Start
                                ) {
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

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
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

                        YouTubeMusicSeekerBar(
                            progressFraction = sliderProgress,
                            positionFormatted = posFormatted,
                            durationFormatted = durFormatted,
                            onSeek = { frac ->
                                val targetMs = (frac * durSec * 1000).toLong()
                                onSeekTo(targetMs)
                            }
                        )

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
                        KaraokeLyricsView(
                            syncedLines = syncedLines,
                            positionMs = positionMs,
                            isLoading = isLyricsLoading,
                            onSeekTo = { timestamp ->
                                playerManager.seekTo(timestamp)
                            }
                        )
                    }
                }
            }
        }

        if (isFullscreenVideoOpen) {
            androidx.activity.compose.BackHandler(enabled = true) {
                isFullscreenVideoOpen = false
            }

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
                        val inflater = android.view.LayoutInflater.from(ctx)
                        (inflater.inflate(app.spasiba.nativeapp2.R.layout.spasiba_player_view, null, false) as androidx.media3.ui.PlayerView).apply {
                            player = playerManager.exoPlayer
                            resizeMode = if (isVideoZoomed) {
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            } else {
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }
                    },
                    update = { playerView ->
                        if (playerView.player != playerManager.exoPlayer) {
                            playerView.player = playerManager.exoPlayer
                        }
                        playerView.resizeMode = if (isVideoZoomed) {
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = !isPlaying || isLoading || showVideoControls,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.7f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.8f)
                                    )
                                )
                            )
                    ) {
                        // Top Bar: Back Button, Title, Channel & Aspect Mode Switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = { isFullscreenVideoOpen = false },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Exit Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = song.author,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White.copy(alpha = 0.7f)
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }

                            IconButton(
                                onClick = { isVideoZoomed = !isVideoZoomed },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                            ) {
                                Icon(
                                    imageVector = if (isVideoZoomed) Icons.Default.AspectRatio else Icons.Default.FitScreen,
                                    contentDescription = "Aspect Ratio Mode",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Center Controls: Rewind 10s, Glass Play/Pause, Forward 10s
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val newPos = (positionMs - 10000L).coerceAtLeast(0L)
                                    onSeekTo(newPos)
                                },
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                                    .clickable { onPlayPauseClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = Color.White,
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val newPos = (positionMs + 10000L).coerceAtMost(durationMs)
                                    onSeekTo(newPos)
                                },
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Bottom Seek Bar & Timestamps
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
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
                                    Text(
                                        text = posFormatted,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    )
                                    Text(
                                        text = durFormatted,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isQualityDialogOpen) {
            val qualities by playerManager.availableVideoQualities.collectAsState()
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

        if (isDownloadDialogOpen) {
            AlertDialog(
                onDismissRequest = { isDownloadDialogOpen = false },
                title = null,
                text = {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = "Download Track",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Audio Only Option (Sleek Highlighted Pill)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    isDownloadDialogOpen = false
                                    downloadManager.startDownload(song, app.spasiba.nativeapp2.data.download.DownloadType.AUDIO_ONLY, "320k")
                                },
                            color = AccentTeal.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, AccentTeal)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Audio Only",
                                    tint = AccentTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Audio Only (Default)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Video + Audio Option
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    isDownloadDialogOpen = false
                                    isDownloadQualityPickerOpen = true
                                },
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = "Video + Audio",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Video + Audio (Select Quality)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { isDownloadDialogOpen = false }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                    }
                },
                containerColor = Color(0xFF141416)
            )
        }

        if (isDownloadQualityPickerOpen) {
            val qualities = listOf("1080p", "720p", "480p", "360p")
            AlertDialog(
                onDismissRequest = { isDownloadQualityPickerOpen = false },
                title = { Text("Select Video Quality", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        qualities.forEach { q ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        isDownloadQualityPickerOpen = false
                                        downloadManager.startDownload(song, app.spasiba.nativeapp2.data.download.DownloadType.VIDEO_AND_AUDIO, q)
                                    },
                                color = Color.White.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = "$q Video Quality",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { isDownloadQualityPickerOpen = false }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                    }
                },
                containerColor = Color(0xFF121214)
            )
        }
    }
}

@Composable
fun KaraokeLyricsView(
    syncedLines: List<app.spasiba.nativeapp2.data.repository.LyricLine>,
    positionMs: Long,
    isLoading: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(260.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
        }
        return
    }

    if (syncedLines.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No lyrics available for this track.",
                style = MaterialTheme.typography.bodyLarge.copy(color = Color.White.copy(alpha = 0.6f))
            )
        }
        return
    }

    val activeIndex = remember(syncedLines, positionMs) {
        val idx = syncedLines.indexOfLast { it.timestampMs <= positionMs }
        if (idx >= 0) idx else 0
    }

    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex in syncedLines.indices) {
            listState.animateScrollToItem(
                index = (activeIndex - 2).coerceAtLeast(0)
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(450.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        itemsIndexed(syncedLines) { index, line ->
            val isActive = index == activeIndex
            val fontScale by animateFloatAsState(targetValue = if (isActive) 1.05f else 1.0f, label = "scale")
            val alpha by animateFloatAsState(targetValue = if (isActive) 1.0f else 0.45f, label = "alpha")

            Text(
                text = line.text,
                style = TextStyle(
                    fontSize = if (isActive) 23.sp else 17.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = Color.White.copy(alpha = alpha),
                    lineHeight = if (isActive) 32.sp else 25.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = fontScale
                        scaleY = fontScale
                    }
                    .clickable {
                        onSeekTo(line.timestampMs)
                    }
            )
        }
    }
}

@Composable
fun YouTubeMusicSeekerBar(
    progressFraction: Float,
    positionFormatted: String,
    durationFormatted: String,
    onSeek: (Float) -> Unit,
    onSeekFinished: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isSeeking by remember { mutableStateOf(false) }
    var userFraction by remember { mutableFloatStateOf(progressFraction) }
    val displayFraction = if (isSeeking) userFraction else progressFraction

    val barHeight by animateDpAsState(
        targetValue = if (isSeeking) 10.dp else 5.dp,
        animationSpec = tween(durationMillis = 150),
        label = "barHeight"
    )
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isSeeking) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "thumbAlpha"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isSeeking = true
                        val width = size.width.toFloat()
                        if (width > 0) {
                            userFraction = (down.position.x / width).coerceIn(0f, 1f)
                            onSeek(userFraction)
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                change.consume()
                                if (width > 0) {
                                    userFraction = (change.position.x / width).coerceIn(0f, 1f)
                                    onSeek(userFraction)
                                }
                            } else {
                                onSeekFinished(userFraction)
                                isSeeking = false
                                break
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val thumbSizePx = with(LocalDensity.current) { 14.dp.toPx() }
            val thumbOffsetPx = ((totalWidthPx - thumbSizePx) * displayFraction).coerceAtLeast(0f)

            // Unplayed Track Background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )

            // Played Track Foreground Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(displayFraction)
                    .height(barHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
            )

            // Glowing Seeking Thumb Pin (Only visible when dragging/seeking)
            if (thumbAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = thumbAlpha))
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = positionFormatted,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = durationFormatted,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
