package app.spasiba.nativeapp2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.spasiba.nativeapp2.data.engine.FcmTokenManager
import app.spasiba.nativeapp2.service.SpasibaMediaService
import app.spasiba.nativeapp2.ui.SpasibaNativeAppShell
import app.spasiba.nativeapp2.ui.theme.SpasibaNative2Theme
import app.spasiba.nativeapp2.utils.NavigationUtils
import kotlinx.coroutines.flow.MutableStateFlow

import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.utils.ShareUtils

class MainActivity : ComponentActivity() {

    companion object {
        val requestedTab = MutableStateFlow<Int?>(null)
        val requestedChatFriendId = MutableStateFlow<String?>(null)
        val requestedChatFriendName = MutableStateFlow<String?>(null)
        val autoExpandPlayer = MutableStateFlow(false)
    }

    private val showPermissionDialog = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val app = application as SpasibaApplication
        val playerManager = app.playerManager

        // Initialize FCM token registration
        FcmTokenManager.getInstance(this).fetchAndRegisterFcmToken()

        val navType = NavigationUtils.getSystemNavigationType(this)
        Log.d("MainActivity", "System Navigation Type detected on app opening: $navType")

        // Check mandatory permission on start
        checkMandatoryPermission()

        // Start background media service
        val serviceIntent = Intent(this, SpasibaMediaService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val openEunoia = intent?.getBooleanExtra("open_eunoia_screen", false) == true ||
                !intent?.getStringExtra("sessionId").isNullOrBlank() ||
                !intent?.getStringExtra("extra_session_id").isNullOrBlank()
        val initialScreen = if (openEunoia) 2 else 0
        if (openEunoia) requestedTab.value = 2

        handleIntentExtras(intent)

        setContent {
            SpasibaNative2Theme {
                val needPermissionDialog by showPermissionDialog.collectAsState()

                SpasibaNativeAppShell(playerManager = playerManager, initialScreen = initialScreen)

                if (needPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = { finishAndRemoveTask() },
                        title = { Text("Phone Permission Required") },
                        text = {
                            Text("Spasiba requires Phone Number permission to run. Please allow the permission in App Settings or tap Retry to grant it.")
                        },
                        confirmButton = {
                            Button(onClick = {
                                try {
                                    val settingsIntent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", packageName, null)
                                    )
                                    startActivity(settingsIntent)
                                } catch (e: Exception) {
                                    checkMandatoryPermission()
                                }
                            }) {
                                Text("Open Settings")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                checkMandatoryPermission()
                            }) {
                                Text("Retry")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val phoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
            if (phoneGranted) {
                showPermissionDialog.value = false
                FcmTokenManager.getInstance(this).fetchAndRegisterFcmToken()
            }
        }
    }

    private fun checkMandatoryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val phoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
            if (!phoneGranted) {
                val permissionsToRequest = mutableListOf<String>()
                permissionsToRequest.add(Manifest.permission.READ_PHONE_NUMBERS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 101)
            } else {
                showPermissionDialog.value = false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val phoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
                if (!phoneGranted) {
                    // Show in-app dialog allowing user to Open Settings or Retry
                    showPermissionDialog.value = true
                    return
                } else {
                    showPermissionDialog.value = false
                }
            }
        }
        FcmTokenManager.getInstance(this).fetchAndRegisterFcmToken()
    }

    private fun handleIntentExtras(intent: Intent?) {
        if (intent == null) return
        val openEunoia = intent.getBooleanExtra("open_eunoia_screen", false)
        val sessionId = intent.getStringExtra("sessionId") ?: intent.getStringExtra("extra_session_id")
        val reqTab = intent.getIntExtra("requested_tab", -1)
        val chatFriendId = intent.getStringExtra("requested_chat_friend_id")
        val chatFriendName = intent.getStringExtra("requested_chat_friend_name")

        // Handle Deep Links (spasiba://, https://spasiba.vercel.app/track/..., youtube links)
        val dataUri = intent.data
        if (dataUri != null) {
            val app = application as? SpasibaApplication
            val playerMgr = app?.playerManager
            val host = dataUri.host?.lowercase() ?: ""
            val path = dataUri.path ?: ""

            // Case 1: Track Deep Link
            var videoId = dataUri.getQueryParameter("id") ?: dataUri.getQueryParameter("v") ?: ""
            if (videoId.isBlank() && path.contains("/track/")) {
                videoId = path.substringAfter("/track/").substringBefore("/").substringBefore("?")
            } else if (videoId.isBlank() && path.contains("/watch")) {
                videoId = dataUri.getQueryParameter("v") ?: ""
            } else if (videoId.isBlank() && host == "youtu.be") {
                videoId = path.trimStart('/').substringBefore('/').substringBefore('?')
            }

            if (videoId.isNotBlank() && videoId.length == 11) {
                val title = dataUri.getQueryParameter("title")
                    ?: dataUri.getQueryParameter("t")
                    ?: "Shared Track"
                val author = dataUri.getQueryParameter("author")
                    ?: dataUri.getQueryParameter("a")
                    ?: dataUri.getQueryParameter("artist")
                    ?: "Spasiba Music"
                val thumbnail = dataUri.getQueryParameter("art")
                    ?: dataUri.getQueryParameter("thumbnail")
                    ?: dataUri.getQueryParameter("m")
                    ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                val duration = dataUri.getQueryParameter("dur")?.toIntOrNull() ?: 210

                val song = Song(
                    id = videoId,
                    title = title,
                    author = author,
                    thumbnail = thumbnail,
                    durationSeconds = duration
                )

                if (playerMgr != null) {
                    playerMgr.playSong(song)
                    autoExpandPlayer.value = true
                }
                return
            }

            // Case 2: Playlist Deep Link
            val rawUrl = dataUri.toString()
            val parsedPlaylist = ShareUtils.parsePlaylistShareCode(rawUrl)
            if (parsedPlaylist != null && parsedPlaylist.songs.isNotEmpty() && playerMgr != null) {
                playerMgr.setQueue(parsedPlaylist.songs, parsedPlaylist.songs.firstOrNull()?.id, parsedPlaylist.name)
                playerMgr.playSong(parsedPlaylist.songs.first())
                autoExpandPlayer.value = true
                return
            }
        }

        if (!chatFriendId.isNullOrBlank()) {
            requestedChatFriendId.value = chatFriendId
            requestedChatFriendName.value = chatFriendName ?: chatFriendId
            requestedTab.value = 7
        } else if (reqTab != -1) {
            requestedTab.value = reqTab
        } else if (openEunoia || !sessionId.isNullOrBlank()) {
            Log.d("MainActivity", "Opened via Eunoia notification with sessionId=$sessionId")
            requestedTab.value = 2
        }
    }

    override fun onStart() {
        super.onStart()
        (application as? SpasibaApplication)?.playerManager?.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        (application as? SpasibaApplication)?.playerManager?.onAppBackgrounded()
    }
}
