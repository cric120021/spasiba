import re

with open("android-native/app/src/main/java/app/spasiba/nativeapp/MainActivity.kt", "r") as f:
    content = f.read()

# Fix 1: Replace player/cache declarations to use application singleton
old_decl = """    private val playbackBridge = SpasibaPlaybackBridge()
    private val nativeAudioCache by lazy { SpasibaNativeAudioCache(applicationContext) }
    private val nativeAudioPlayer by lazy {
        SpasibaNativeAudioPlayer(applicationContext, nativeAudioCache, ::onNativeAudioStateChanged)
    }"""

new_decl = """    private val playbackBridge = SpasibaPlaybackBridge()
    private val nativeAudioCache by lazy { (application as SpasibaApplication).nativeAudioCache }
    private val nativeAudioPlayer by lazy { (application as SpasibaApplication).nativeAudioPlayer }

    private val stateListener = { state: String, title: String?, artist: String? ->
        onNativeAudioStateChanged(state, title, artist)
    }"""

content = content.replace(old_decl, new_decl)

# Fix 2: Register state listener in onCreate
old_oncreate = """        nativeAudioCache.prepareSessionCache()
        requestNotificationPermission()"""

new_oncreate = """        nativeAudioCache.prepareSessionCache()
        (application as SpasibaApplication).addStateListener(stateListener)
        requestNotificationPermission()"""

content = content.replace(old_oncreate, new_oncreate)

# Fix 3: Fix onDestroy - don't stop player or service
old_ondestroy = """    override fun onDestroy() {
        hideFullscreenView()
        unregisterReceiver(mediaControlReceiver)
        nativeAudioPlayer.stop()
        setNativePlaybackActive(false, null, null, "ended")
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        pendingPermissionRequest?.deny()
        pendingPermissionRequest = null
        if (::webView.isInitialized) {
            nativeAudioPlayer.release()
        }
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        root.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }"""

new_ondestroy = """    override fun onDestroy() {
        hideFullscreenView()
        unregisterReceiver(mediaControlReceiver)
        runCatching { (application as SpasibaApplication).removeStateListener(stateListener) }
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        pendingPermissionRequest?.deny()
        pendingPermissionRequest = null
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        root.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }"""

content = content.replace(old_ondestroy, new_ondestroy)

with open("android-native/app/src/main/java/app/spasiba/nativeapp/MainActivity.kt", "w") as f:
    f.write(content)

print("MainActivity.kt updated successfully")
EOF
