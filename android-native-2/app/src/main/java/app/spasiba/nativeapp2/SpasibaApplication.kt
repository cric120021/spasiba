package app.spasiba.nativeapp2

import android.app.Application
import app.spasiba.nativeapp2.player.NativePlayerManager
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SpasibaApplication : Application() {
    val playerManager by lazy { NativePlayerManager(this) }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                YoutubeDL.getInstance().init(this@SpasibaApplication)
                YoutubeDL.getInstance().updateYoutubeDL(this@SpasibaApplication, YoutubeDL.UpdateChannel.STABLE)
            }
        }
    }
}
