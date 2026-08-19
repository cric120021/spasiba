package app.spasiba.nativeapp2

import android.app.Application
import android.os.Build
import app.spasiba.nativeapp2.player.NativePlayerManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import coil.disk.DiskCache
import coil.memory.MemoryCache

class SpasibaApplication : Application(), ImageLoaderFactory {
    val playerManager by lazy { NativePlayerManager(this) }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(300 * 1024 * 1024L) // 300 MB Disk Cache
                    .build()
            }
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        app.spasiba.nativeapp2.data.engine.EunoiaSessionManager.getInstance(this)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                YoutubeDL.getInstance().init(this@SpasibaApplication)
                YoutubeDL.getInstance().updateYoutubeDL(this@SpasibaApplication, YoutubeDL.UpdateChannel.STABLE)
                android.util.Log.d("SpasibaApplication", "YoutubeDL initialized & updated to STABLE successfully!")
            }.onFailure {
                android.util.Log.e("SpasibaApplication", "YoutubeDL init/update failed: ${it.message}", it)
            }
        }
    }
}
