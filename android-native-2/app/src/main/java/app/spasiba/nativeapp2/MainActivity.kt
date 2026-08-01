package app.spasiba.nativeapp2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.spasiba.nativeapp2.service.SpasibaMediaService
import app.spasiba.nativeapp2.ui.SpasibaNativeAppShell
import app.spasiba.nativeapp2.ui.theme.SpasibaNative2Theme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.BLACK)
        )
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        val app = application as SpasibaApplication
        val playerManager = app.playerManager

        // Request POST_NOTIFICATIONS permission for Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        // Start background media service
        val serviceIntent = Intent(this, SpasibaMediaService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            SpasibaNative2Theme {
                SpasibaNativeAppShell(playerManager = playerManager)
            }
        }
    }
}
