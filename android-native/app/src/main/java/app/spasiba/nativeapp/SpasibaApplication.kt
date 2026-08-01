package app.spasiba.nativeapp

import android.app.Application

class SpasibaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SpasibaNativeAudioPlayer.clearSessionStreamCache(this)
    }

    val nativeAudioCache: SpasibaNativeAudioCache by lazy {
        SpasibaNativeAudioCache(this)
    }

    val nativeAudioPlayer: SpasibaNativeAudioPlayer by lazy {
        SpasibaNativeAudioPlayer(this, nativeAudioCache) { state, title, artist ->
            synchronized(stateListeners) {
                stateListeners.forEach { listener ->
                    try {
                        listener(state, title, artist)
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    private val stateListeners = mutableListOf<(String, String?, String?) -> Unit>()

    fun addStateListener(listener: (String, String?, String?) -> Unit) {
        synchronized(stateListeners) {
            stateListeners.add(listener)
        }
    }

    fun removeStateListener(listener: (String, String?, String?) -> Unit) {
        synchronized(stateListeners) {
            stateListeners.remove(listener)
        }
    }
}
