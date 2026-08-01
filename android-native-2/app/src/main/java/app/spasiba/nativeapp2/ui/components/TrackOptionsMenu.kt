package app.spasiba.nativeapp2.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Share
import android.content.Context
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.ui.theme.TextMuted
import app.spasiba.nativeapp2.ui.theme.TextStrong
import app.spasiba.nativeapp2.utils.ShareUtils
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsMenu(
    song: Song,
    isLiked: Boolean = false,
    onDismiss: () -> Unit,
    onLikeToggle: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onPlayNow: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onRemoveFromQueue: (() -> Unit)? = null,
    onViewArtist: (String) -> Unit = {},
    onDownloadTrack: (Song, app.spasiba.nativeapp2.data.download.DownloadType, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    var isInfoDialogOpen by remember { mutableStateOf(false) }
    var isDownloadDialogOpen by remember { mutableStateOf(false) }
    var isQualityPickerOpen by remember { mutableStateOf(false) }

    val prefs = context.getSharedPreferences("spasiba_user_profile", Context.MODE_PRIVATE)
    val savedQualityPref = prefs.getString("video_download_quality", "Ask each time") ?: "Ask each time"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121214)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
        ) {
            // Song Header Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.thumbnail,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextStrong
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.author,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextMuted
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            TrackOptionRow(
                icon = androidx.compose.material.icons.Icons.Default.Download,
                title = "Download Track",
                tint = app.spasiba.nativeapp2.ui.theme.AccentTeal,
                onClick = { isDownloadDialogOpen = true }
            )

            TrackOptionRow(
                icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                title = if (isLiked) "Remove from Liked Tracks" else "Save to Liked Tracks",
                tint = Color.White,
                onClick = {
                    onLikeToggle()
                    onDismiss()
                }
            )

            TrackOptionRow(
                icon = Icons.Default.PlaylistAdd,
                title = "Add to Playlist",
                onClick = {
                    onAddToPlaylist()
                    onDismiss()
                }
            )

            TrackOptionRow(
                icon = Icons.Default.Info,
                title = "Details",
                onClick = { isInfoDialogOpen = true }
            )

            if (onRemoveFromQueue != null) {
                TrackOptionRow(
                    icon = Icons.Default.Delete,
                    title = "Remove from Queue",
                    tint = Color(0xFFFB7185),
                    onClick = {
                        onRemoveFromQueue()
                        onDismiss()
                    }
                )
            }

            TrackOptionRow(
                icon = Icons.Default.Person,
                title = "View Artist (${song.author})",
                onClick = {
                    onViewArtist(song.author)
                    onDismiss()
                }
            )

            TrackOptionRow(
                icon = Icons.Default.Share,
                title = "Share Song",
                onClick = {
                    ShareUtils.shareSong(context, song)
                    onDismiss()
                }
            )
        }
    }

    if (isInfoDialogOpen) {
        AlertDialog(
            onDismissRequest = { isInfoDialogOpen = false },
            title = { Text("Track Details", color = TextStrong, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Title: ${song.title}", color = TextStrong, fontSize = 14.sp)
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Artist: ${song.author}", color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Duration: ${song.durationFormatted}", color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Video ID: ${song.id}", color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Quality: 256kbps / High Definition", color = Color.White, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { isInfoDialogOpen = false }) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF121214)
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
                            color = TextStrong
                        )
                    )
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Audio Only Option (Sleek Highlighted Pill - Default)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onDownloadTrack(song, app.spasiba.nativeapp2.data.download.DownloadType.AUDIO_ONLY, "320k")
                                isDownloadDialogOpen = false
                                onDismiss()
                            },
                        color = app.spasiba.nativeapp2.ui.theme.AccentTeal.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, app.spasiba.nativeapp2.ui.theme.AccentTeal)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Audio Only",
                                tint = app.spasiba.nativeapp2.ui.theme.AccentTeal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Audio Only (Default)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextStrong
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
                                if (savedQualityPref == "Ask each time") {
                                    isQualityPickerOpen = true
                                } else {
                                    onDownloadTrack(song, app.spasiba.nativeapp2.data.download.DownloadType.VIDEO_AND_AUDIO, savedQualityPref)
                                    onDismiss()
                                }
                            },
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Video + Audio",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Video + Audio ($savedQualityPref)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextStrong
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
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = Color(0xFF141416)
        )
    }

    if (isQualityPickerOpen) {
        val qualities = listOf("1080p", "720p", "480p", "360p")
        AlertDialog(
            onDismissRequest = { isQualityPickerOpen = false },
            title = { Text("Select Video Quality", color = TextStrong, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    qualities.forEach { q ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onDownloadTrack(song, app.spasiba.nativeapp2.data.download.DownloadType.VIDEO_AND_AUDIO, q)
                                    isQualityPickerOpen = false
                                    onDismiss()
                                },
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = "$q Video Quality",
                                color = TextStrong,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { isQualityPickerOpen = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = Color(0xFF121214)
        )
    }
}

@Composable
private fun TrackOptionRow(
    icon: ImageVector,
    title: String,
    tint: Color = TextStrong,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(color = TextStrong, fontSize = 15.sp)
        )
    }
}
