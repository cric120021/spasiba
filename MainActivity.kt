package com.spasiba.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AudioPlayerManager.init(this)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    object Home : BottomNavItem("home", Icons.Default.Home, "Home")
    object Search : BottomNavItem("search", Icons.Default.Search, "Search")
    object Library : BottomNavItem("library", Icons.Default.List, "Library")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var currentRoute by remember { mutableStateOf("home") }
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Search,
        BottomNavItem.Library
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = { currentRoute = item.route }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (currentRoute) {
                    "home" -> HomeScreen()
                    "search" -> SearchScreen()
                    "library" -> LibraryScreen()
                }
            }
            MiniPlayer()
        }
    }
}

@Composable
fun MiniPlayer() {
    val currentSong by AudioPlayerManager.currentSong
    val isPlaying by AudioPlayerManager.isPlaying

    if (currentSong != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = currentSong ?: "", style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = {
                if (isPlaying) AudioPlayerManager.pause() else AudioPlayerManager.resume()
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause"
                )
            }
        }
    }
}

@Composable
fun HomeScreen() {
    val mockSongs = listOf(
        Pair("Lofi Study", "https://streams.ilovemusic.de/iloveradio17.mp3"),
        Pair("Chill Hop", "https://streams.ilovemusic.de/iloveradio1.mp3")
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "Recommended for you",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        items(mockSongs) { song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { AudioPlayerManager.play(song.second, song.first) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = song.first, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun SearchScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Search Screen", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun LibraryScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Library Screen", style = MaterialTheme.typography.headlineMedium)
    }
}