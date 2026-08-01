package app.spasiba.nativeapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.spasiba.nativeapp.SpasibaNativeAudioPlayer
import app.spasiba.nativeapp.theme.GlassBorder
import app.spasiba.nativeapp.theme.GlassSurface
import app.spasiba.nativeapp.theme.Slate950
import app.spasiba.nativeapp.theme.TextMuted
import app.spasiba.nativeapp.theme.TextPrimary
import app.spasiba.nativeapp.theme.AccentTeal

sealed class NavItem(val route: String, val icon: ImageVector, val label: String) {
    object Home : NavItem("home", Icons.Default.Home, "Home")
    object Search : NavItem("search", Icons.Default.Search, "Search")
    object Library : NavItem("library", Icons.Default.LibraryMusic, "Library")
    object Profile : NavItem("profile", Icons.Default.Person, "Profile")
}

@Composable
fun SpasibaNativeAppShell(
    player: SpasibaNativeAudioPlayer,
    modifier: Modifier = Modifier
) {
    var currentRoute by remember { mutableStateOf("home") }
    var isPlayerExpanded by remember { mutableStateOf(false) }

    var currentTitle by remember { mutableStateOf(player.currentTitle) }
    var currentArtist by remember { mutableStateOf(player.currentArtist) }
    var currentArtworkUrl by remember { mutableStateOf(player.currentArtworkUrl) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var positionMs by remember { mutableLongStateOf(player.positionMs) }
    var durationMs by remember { mutableLongStateOf(player.durationMs) }

    // Periodically update player state ticks
    LaunchedEffect(Unit) {
        while (true) {
            currentTitle = player.currentTitle
            currentArtist = player.currentArtist
            currentArtworkUrl = player.currentArtworkUrl
            isPlaying = player.isPlaying
            positionMs = player.positionMs
            durationMs = player.durationMs
            kotlinx.coroutines.delay(250)
        }
    }

    val progressFraction = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Box(modifier = modifier.fillMaxSize().background(Slate950)) {
        // 1. Dynamic Blurred Backdrop
        DynamicBackdrop(
            backdropUrl = currentArtworkUrl
        )

        // 2. Main Content & Navigation Shell
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = GlassSurface,
                    contentColor = TextPrimary,
                    tonalElevation = 8.dp
                ) {
                    val navItems = listOf(NavItem.Home, NavItem.Search, NavItem.Library, NavItem.Profile)
                    navItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentRoute == item.route,
                            onClick = { currentRoute = item.route },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentTeal,
                                selectedTextColor = AccentTeal,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = GlassBorder
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                when (currentRoute) {
                    "home" -> NativeHomeScreen(player = player)
                    "search" -> NativeSearchScreen(player = player)
                    "library" -> NativeLibraryScreen(player = player)
                    "profile" -> NativeProfileScreen(player = player)
                }

                // 3. Floating Persistent MiniPlayer Dock
                if (!currentTitle.isNullOrBlank() && !isPlayerExpanded) {
                    MiniPlayerDock(
                        title = currentTitle,
                        artist = currentArtist,
                        artworkUrl = currentArtworkUrl,
                        isPlaying = isPlaying,
                        progressFraction = progressFraction,
                        onTogglePlay = {
                            if (isPlaying) player.pause() else player.resume()
                        },
                        onNext = { player.next() },
                        onExpand = { isPlayerExpanded = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }

        // 4. Expanded Fullscreen Player Sheet
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            ExpandedPlayerSheet(
                title = currentTitle,
                artist = currentArtist,
                artworkUrl = currentArtworkUrl,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                onTogglePlay = {
                    if (isPlaying) player.pause() else player.resume()
                },
                onNext = { player.next() },
                onPrevious = { player.previous() },
                onSeekTo = { player.seekTo(it) },
                onDismiss = { isPlayerExpanded = false }
            )
        }
    }
}

@Composable
fun NativeHomeScreen(player: SpasibaNativeAudioPlayer) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Welcome to Spasiba",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "100% Native Audio Experience",
                color = AccentTeal,
                fontSize = 14.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = GlassSurface)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.CenterStart) {
                    Column {
                        Text("Search & Discovery", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Explore millions of tracks natively", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun NativeSearchScreen(player: SpasibaNativeAudioPlayer) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Native Search Screen", color = TextPrimary, fontSize = 18.sp)
    }
}

@Composable
fun NativeLibraryScreen(player: SpasibaNativeAudioPlayer) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Native Library & Playlists", color = TextPrimary, fontSize = 18.sp)
    }
}

@Composable
fun NativeProfileScreen(player: SpasibaNativeAudioPlayer) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Native User Profile", color = TextPrimary, fontSize = 18.sp)
    }
}
