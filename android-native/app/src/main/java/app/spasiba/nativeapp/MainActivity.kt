package app.spasiba.nativeapp

import androidx.activity.compose.setContent
import app.spasiba.nativeapp.theme.SpasibaTheme
import app.spasiba.nativeapp.ui.SpasibaNativeAppShell
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.util.Rational
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.view.KeyEvent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var loading: ProgressBar
    private lateinit var errorPanel: LinearLayout

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var mainFrameFailed = false
    private var playbackActive = false
    private var pendingPlaybackActivation = false
    private var isActivityResumed = false
    private var backgroundTransitionUntilMs = 0L
    private var playbackStartupUntilMs = 0L
    private var lastPlaybackTitle: String? = null
    private var lastPlaybackState: String? = null
    private var lastPlaybackArtworkUrl: String? = null
    private var lastPlaybackPositionMs = 0L
    private var lastPlaybackDurationMs = 0L

    private val playbackBridge = SpasibaPlaybackBridge()
    private val nativeAudioCache by lazy { (application as SpasibaApplication).nativeAudioCache }
    private val nativeAudioPlayer by lazy { (application as SpasibaApplication).nativeAudioPlayer }

    private val stateListener = { state: String, title: String?, artist: String? ->
        onNativeAudioStateChanged(state, title, artist)
    }

    private fun dispatchControlToWebAndNative(action: String, extraParam: Any? = null) {
        runOnUiThread {
            when (action) {
                "play" -> nativeAudioPlayer.resume()
                "pause" -> nativeAudioPlayer.pause()
                "next" -> nativeAudioPlayer.next()
                "previous" -> nativeAudioPlayer.previous()
                "seek" -> {
                    val positionMs = (extraParam as? Long) ?: 0L
                    nativeAudioPlayer.seek(positionMs.coerceAtLeast(0L) / 1000.0)
                }
                "stop" -> {
                    nativeAudioPlayer.stop()
                    setNativePlaybackActive(false, null, null, "ended")
                }
            }

            if (::webView.isInitialized) {
                val jsScript = when (action) {
                    "play" -> "try { window.SpasibaAndroidBridge?.play?.(); } catch (_) {}"
                    "pause" -> "try { window.SpasibaAndroidBridge?.pause?.(); } catch (_) {}"
                    "next" -> "try { window.SpasibaAndroidBridge?.next?.(); } catch (_) {}"
                    "previous" -> "try { window.SpasibaAndroidBridge?.previous?.(); } catch (_) {}"
                    "seek" -> {
                        val sec = ((extraParam as? Long) ?: 0L) / 1000.0
                        "try { window.SpasibaAndroidBridge?.seek?.($sec); } catch (_) {}"
                    }
                    "stop" -> "try { window.SpasibaAndroidBridge?.pause?.(); } catch (_) {}"
                    else -> ""
                }
                if (jsScript.isNotBlank()) {
                    webView.post { webView.evaluateJavascript(jsScript, null) }
                }
            }
        }
    }

    private val mediaControlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SpasibaKeepAliveService.ACTION_MEDIA_CONTROL) {
                val controlType = intent.getStringExtra(SpasibaKeepAliveService.EXTRA_CONTROL_TYPE)
                val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(SpasibaKeepAliveService.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(SpasibaKeepAliveService.EXTRA_KEY_EVENT)
                }

                Log.d(LOG_TAG, "Received media control: $controlType or keyEvent: ${keyEvent?.keyCode}")

                if (!controlType.isNullOrBlank()) {
                    val positionMs = intent.getLongExtra(SpasibaKeepAliveService.EXTRA_SEEK_POSITION_MS, 0L)
                    dispatchControlToWebAndNative(controlType, positionMs)
                } else if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT -> dispatchControlToWebAndNative("next")
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> dispatchControlToWebAndNative("previous")
                        KeyEvent.KEYCODE_MEDIA_PLAY -> dispatchControlToWebAndNative("play")
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> dispatchControlToWebAndNative("pause")
                        KeyEvent.KEYCODE_MEDIA_STOP -> dispatchControlToWebAndNative("stop")
                        else -> togglePlayback()
                    }
                }
            }
        }
    }

    private fun togglePlayback() {
        nativeAudioPlayer.toggle()
    }

    private fun pausePlaybackInWebView() {
        nativeAudioPlayer.pause()
    }

    private fun resumePlaybackInWebView() {
        nativeAudioPlayer.resume()
    }

    private fun nextPlaybackInWebView() {
        nativeAudioPlayer.next()
    }

    private fun previousPlaybackInWebView() {
        nativeAudioPlayer.previous()
    }

    private fun togglePlaybackInWebView() {
        nativeAudioPlayer.toggle()
    }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback ?: return@registerForActivityResult
            fileChooserCallback = null

            val uris = when {
                result.resultCode != Activity.RESULT_OK -> null
                result.data?.clipData != null -> {
                    val clipData = result.data?.clipData
                    Array(clipData?.itemCount ?: 0) { index -> clipData!!.getItemAt(index).uri }
                }
                result.data?.data != null -> arrayOf(result.data!!.data!!)
                else -> null
            }
            callback.onReceiveValue(uris)
        }

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingPermissionRequest ?: return@registerForActivityResult
            pendingPermissionRequest = null

            if (granted) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            } else {
                request.deny()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(LOG_TAG, "Notification permission granted: $granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()
        buildWebShell(savedInstanceState)
        nativeAudioCache.prepareSessionCache()
        (application as SpasibaApplication).addStateListener(stateListener)
        requestNotificationPermission()

        val filter = IntentFilter(SpasibaKeepAliveService.ACTION_MEDIA_CONTROL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaControlReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(mediaControlReceiver, filter)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        fullscreenView != null -> hideFullscreenView()
                        webView.canGoBack() -> webView.goBack()
                        else -> {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        backgroundTransitionUntilMs = 0L
        if (::webView.isInitialized) {
            webView.onResume()
            webView.resumeTimers()
            notifyWebNativeAudioState()
        }
    }

    override fun onUserLeaveHint() {
        if (playbackActive) {
            backgroundTransitionUntilMs = System.currentTimeMillis() + BACKGROUND_TRANSITION_GRACE_MS
        }
        super.onUserLeaveHint()
    }

    override fun onPause() {
        if (playbackActive) {
            backgroundTransitionUntilMs = System.currentTimeMillis() + BACKGROUND_TRANSITION_GRACE_MS
        }
        isActivityResumed = false
        if (::webView.isInitialized) {
            if (playbackActive) {
                webView.resumeTimers()
            } else {
                webView.onPause()
                webView.pauseTimers()
            }
        }
        super.onPause()
    }

    override fun onDestroy() {
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
    }

    private fun configureWindow() {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebShell(savedInstanceState: Bundle?) {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        WebView.setWebContentsDebuggingEnabled(true)

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                userAgentString = ANDROID_CHROME_USER_AGENT
            }

            webViewClient = SpasibaWebViewClient()
            webChromeClient = SpasibaWebChromeClient()
            addJavascriptInterface(playbackBridge, "SpasibaAndroid")
        }
        installDocumentStartRuntimeHints(webView)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        loading = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }
        errorPanel = buildErrorPanel()

        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            loading,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        )
        root.addView(
            errorPanel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        errorPanel.visibility = View.GONE

        setContentView(root)

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(SPASIBA_URL)
        }
    }

    private fun openExternal(url: String): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun isSpasibaUrl(url: String?): Boolean =
        runCatching { Uri.parse(url).host == SPASIBA_HOST }.getOrDefault(false)

    private fun parseNativeAudioTracks(array: JSONArray?): List<NativeAudioTrack> {
        if (array == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until minOf(array.length(), MAX_NATIVE_QUEUE_TRACKS)) {
                val item = array.optJSONObject(index) ?: continue
                val normalizedVideoId = SpasibaNativeAudioCache.normalizeVideoId(item.optString("videoId")) ?: continue
                add(
                    NativeAudioTrack(
                        videoId = normalizedVideoId,
                        title = item.optString("title").takeIf { it.isNotBlank() },
                        artist = item.optString("artist").takeIf { it.isNotBlank() },
                        artworkUrl = item.optString("artworkUrl").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    private data class PlaybackTiming(
        val positionMs: Long,
        val durationMs: Long
    )

    private fun readNativePlaybackTiming(): PlaybackTiming {
        return runCatching {
            val snapshot = JSONObject(nativeAudioPlayer.snapshotJson())
            val positionMs = (snapshot.optDouble("currentTime", 0.0).coerceAtLeast(0.0) * 1000.0).toLong()
            val durationMs = (snapshot.optDouble("duration", 0.0).coerceAtLeast(0.0) * 1000.0).toLong()
            PlaybackTiming(positionMs, durationMs)
        }.getOrDefault(PlaybackTiming(0L, 0L))
    }

    private fun setNativePlaybackActive(
        active: Boolean,
        title: String?,
        artist: String?,
        state: String? = null,
        artworkUrl: String? = null,
        videoId: String? = null
    ) {
        val playbackState = state.orEmpty()
        val displayTitle = listOfNotNull(title?.takeIf { it.isNotBlank() }, artist?.takeIf { it.isNotBlank() })
            .joinToString(" - ")
            .ifBlank { "Spasiba" }

        val nativeVideoId = nativeAudioPlayer.currentVideoId()
        if (!active && playbackState != "ended" && !nativeVideoId.isNullOrBlank()) {
            Log.d(LOG_TAG, "ignoring inactive playback signal while native player owns playback")
            return
        }

        val mediaVideoId = videoId?.takeIf { it.isNotBlank() }
            ?: nativeVideoId
        val mediaArtworkUrl = artworkUrl?.takeIf { it.isNotBlank() }
            ?: if (::webView.isInitialized) nativeAudioPlayer.currentArtworkUrl() else null
        val timing = readNativePlaybackTiming()
        if (
            active == playbackActive &&
            displayTitle == lastPlaybackTitle &&
            playbackState == lastPlaybackState &&
            mediaArtworkUrl == lastPlaybackArtworkUrl &&
            timing.positionMs == lastPlaybackPositionMs &&
            timing.durationMs == lastPlaybackDurationMs
        ) {
            return
        }

        Log.d(LOG_TAG, "setNativePlaybackActive active=$active state=$playbackState title=${title.orEmpty()} artist=${artist.orEmpty()}")
        lastPlaybackTitle = displayTitle
        lastPlaybackState = playbackState
        lastPlaybackArtworkUrl = mediaArtworkUrl
        lastPlaybackPositionMs = timing.positionMs
        lastPlaybackDurationMs = timing.durationMs

        if (active) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            playbackActive = true
            playbackStartupUntilMs = System.currentTimeMillis() + PLAYBACK_STARTUP_GRACE_MS
            val serviceIntent = Intent(this, SpasibaKeepAliveService::class.java).apply {
                putExtra(SpasibaKeepAliveService.EXTRA_TITLE, title ?: "Spasiba")
                putExtra(SpasibaKeepAliveService.EXTRA_ARTIST, artist)
                putExtra(SpasibaKeepAliveService.EXTRA_STATE, playbackState)
                putExtra(SpasibaKeepAliveService.EXTRA_VIDEO_ID, mediaVideoId)
                putExtra(SpasibaKeepAliveService.EXTRA_ARTWORK_URL, mediaArtworkUrl)
                putExtra(SpasibaKeepAliveService.EXTRA_POSITION_MS, timing.positionMs)
                putExtra(SpasibaKeepAliveService.EXTRA_DURATION_MS, timing.durationMs)
            }
            runCatching { ContextCompat.startForegroundService(this, serviceIntent) }
            if (::webView.isInitialized) {
                webView.resumeTimers()
            }
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            playbackActive = false
            playbackStartupUntilMs = 0L
            if (playbackState == "ended" || playbackState == "stopped") {
                runCatching { stopService(Intent(this, SpasibaKeepAliveService::class.java)) }
            }
            if (!isActivityResumed && ::webView.isInitialized) {
                webView.onPause()
                webView.pauseTimers()
            }
        }
    }

    private fun onNativeAudioStateChanged(state: String, title: String?, artist: String?) {
        runOnUiThread {
            notifyWebNativeAudioState()

            val isNonIdle = state == "playing" || state == "loading" || state == "paused"

            if (isNonIdle) {
                pendingPlaybackActivation = true
            }

            val active = state == "playing" ||
                    state == "loading" ||
                    (state == "paused" && nativeAudioPlayer.currentVideoId() != null)

            Log.d(
                "SPASIBA_BG",
                "state=$state active=$active video=${nativeAudioPlayer.currentVideoId()}"
            )

            if (active) {
                pendingPlaybackActivation = false
            }

            setNativePlaybackActive(active, title, artist, state)

            if (!active && state == "ended") {
                pendingPlaybackActivation = false
            }
        }
    }
    private fun notifyWebNativeAudioState() {
        if (!::webView.isInitialized) {
            return
        }

        val snapshot = nativeAudioPlayer.snapshotJson()
        webView.evaluateJavascript(
            """
                (() => {
                  try {
                    const snapshot = JSON.parse(${snapshot.quoteForJavaScript()});
                    window.__spasibaAndroidNativeAudioDidChange?.(snapshot);
                  } catch (_) {}
                })();
            """.trimIndent(),
            null
        )
    }

    private fun shouldIgnoreInactivePlaybackSignal(state: String): Boolean {
        val now = System.currentTimeMillis()
        return !isActivityResumed ||
            now < backgroundTransitionUntilMs ||
            now < playbackStartupUntilMs ||
            pendingPlaybackActivation
    }

    private fun enterPlaybackPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isInPictureInPictureMode) {
            return
        }

        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(1, 1))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(true)
                }
            }
            .build()

        runCatching { enterPictureInPictureMode(params) }
    }

    private fun buildErrorPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(36, 36, 36, 36)
        }

        val title = TextView(this).apply {
            text = "Spasiba could not load"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = android.view.Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Check your connection and try again."
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 18, 0, 24)
        }
        val retry = Button(this).apply {
            text = "Retry"
            setOnClickListener {
                showLoading()
                webView.loadUrl(SPASIBA_URL)
            }
        }

        panel.addView(title)
        panel.addView(subtitle)
        panel.addView(retry)
        return panel
    }

    private fun showLoading() {
        mainFrameFailed = false
        loading.visibility = View.VISIBLE
        errorPanel.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    private fun showLoadedPage() {
        loading.visibility = View.GONE
        errorPanel.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    private fun showLoadError() {
        loading.visibility = View.GONE
        webView.visibility = View.GONE
        errorPanel.visibility = View.VISIBLE
    }

    private fun showFullscreenView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden()
            return
        }

        fullscreenView = view
        fullscreenCallback = callback
        webView.visibility = View.GONE
        loading.visibility = View.GONE
        root.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        WindowInsetsControllerCompat(window, root).hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun hideFullscreenView() {
        val view = fullscreenView ?: return
        root.removeView(view)
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        webView.visibility = View.VISIBLE
        WindowInsetsControllerCompat(window, root).show(WindowInsetsCompat.Type.systemBars())
    }

    private inner class SpasibaWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            showLoading()
            if (isSpasibaUrl(url) && !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                view.evaluateJavascript(SPASIBA_RUNTIME_HINTS, null)
            }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            if (isSpasibaUrl(url)) {
                view.evaluateJavascript(SPASIBA_RUNTIME_HINTS, null)
            }
            if (!mainFrameFailed) {
                showLoadedPage()
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            super.onReceivedError(view, request, error)
            if (request.isForMainFrame) {
                mainFrameFailed = true
                showLoadError()
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            if (request.isForMainFrame && errorResponse.statusCode >= 500) {
                mainFrameFailed = true
                showLoadError()
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            setNativePlaybackActive(false, null, null)
            root.removeView(webView)
            buildWebShell(null)
            return true
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            return nativeAudioCache.intercept(request) ?: super.shouldInterceptRequest(view, request)
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url ?: return false
            if (!request.isForMainFrame) {
                return false
            }

            val scheme = url.scheme.orEmpty()
            val host = url.host.orEmpty()

            if (scheme == "http" || scheme == "https") {
                return if (host == SPASIBA_HOST) {
                    false
                } else {
                    openExternal(url.toString())
                }
            }

            return openExternal(url.toString())
        }
    }

    private inner class SpasibaWebChromeClient : WebChromeClient() {

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            Log.d(
                LOG_TAG,
                "console ${consoleMessage.messageLevel()}: ${consoleMessage.message()} @ ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
            )
            return true
        }

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (!mainFrameFailed) {
                loading.visibility = if (newProgress < 75) View.VISIBLE else View.GONE
            }
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message
        ): Boolean {
            val popup = WebView(this@MainActivity).apply {
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val targetUrl = request.url?.toString() ?: return true
                        openExternal(targetUrl)
                        return true
                    }
                }
            }
            val transport = resultMsg.obj as WebView.WebViewTransport
            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            runOnUiThread {
                val wantsMicrophone = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                if (!wantsMicrophone) {
                    request.deny()
                    return@runOnUiThread
                }

                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                } else {
                    pendingPermissionRequest?.deny()
                    pendingPermissionRequest = request
                    microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = filePathCallback

            val intent = fileChooserParams.createIntent().apply {
                addCategory(Intent.CATEGORY_OPENABLE)
            }

            return try {
                fileChooserLauncher.launch(intent)
                true
            } catch (_: ActivityNotFoundException) {
                filePathCallback.onReceiveValue(null)
                fileChooserCallback = null
                false
            }
        }
    }

    private inner class SpasibaPlaybackBridge {
        @JavascriptInterface
        fun onPlaybackState(state: String?, title: String?, artist: String?) {
            updatePlaybackStateFromBridge(state, title, artist, null, null)
        }

        @JavascriptInterface
        fun onPlaybackStateWithArtwork(
            state: String?,
            title: String?,
            artist: String?,
            artworkUrl: String?,
            videoId: String?
        ) {
            updatePlaybackStateFromBridge(state, title, artist, artworkUrl, videoId)
        }

        private fun updatePlaybackStateFromBridge(
            state: String?,
            title: String?,
            artist: String?,
            artworkUrl: String?,
            videoId: String?
        ) {
            Log.d(LOG_TAG, "UPDATE PLAYBACK STATE FROM BRIDGE: state=$state video=$videoId title=$title")

            val rawVideoId = videoId.takeIf { !it.isNullOrBlank() } ?: nativeAudioPlayer.currentVideoId
            val normalizedVideoId = SpasibaNativeAudioCache.normalizeVideoId(rawVideoId)
            val activeTitle = title.takeIf { !it.isNullOrBlank() } ?: nativeAudioPlayer.currentTitle ?: "Spasiba"
            val activeArtist = artist.takeIf { !it.isNullOrBlank() } ?: nativeAudioPlayer.currentArtist ?: ""
            val activeArtwork = artworkUrl.takeIf { !it.isNullOrBlank() } ?: nativeAudioPlayer.currentArtworkUrl

            val timing = readNativePlaybackTiming()
            if (state == "playing" || state == "loading" || state == "paused") {
                runOnUiThread {
                    val intent = Intent(this@MainActivity, SpasibaKeepAliveService::class.java).apply {
                        putExtra(SpasibaKeepAliveService.EXTRA_TITLE, activeTitle)
                        putExtra(SpasibaKeepAliveService.EXTRA_ARTIST, activeArtist)
                        putExtra(SpasibaKeepAliveService.EXTRA_ARTWORK_URL, activeArtwork)
                        putExtra(SpasibaKeepAliveService.EXTRA_STATE, state)
                        if (timing.positionMs >= 0L) {
                            putExtra(SpasibaKeepAliveService.EXTRA_POSITION_MS, timing.positionMs)
                        }
                        if (timing.durationMs > 0L) {
                            putExtra(SpasibaKeepAliveService.EXTRA_DURATION_MS, timing.durationMs)
                        }
                        if (!normalizedVideoId.isNullOrBlank()) {
                            putExtra(SpasibaKeepAliveService.EXTRA_VIDEO_ID, normalizedVideoId)
                        }
                    }
                    ContextCompat.startForegroundService(this@MainActivity, intent)
                }
            }
        }

        @JavascriptInterface
        fun playCachedAudio(videoId: String?, title: String?, artist: String?) {
            val normalizedVideoId = SpasibaNativeAudioCache.normalizeVideoId(videoId) ?: return

            runOnUiThread {
                Log.d(LOG_TAG, "NATIVE BRIDGE PLAY REQUEST video=$normalizedVideoId title=${title.orEmpty()} artist=${artist.orEmpty()}")
                nativeAudioPlayer.play(normalizedVideoId, title, artist)

                val intent = Intent(this@MainActivity, SpasibaKeepAliveService::class.java).apply {
                    putExtra(SpasibaKeepAliveService.EXTRA_TITLE, title)
                    putExtra(SpasibaKeepAliveService.EXTRA_ARTIST, artist)
                    putExtra(SpasibaKeepAliveService.EXTRA_VIDEO_ID, normalizedVideoId)
                    putExtra(SpasibaKeepAliveService.EXTRA_STATE, "loading")
                }

                ContextCompat.startForegroundService(this@MainActivity, intent)
            }
        }

        @JavascriptInterface
        fun playCachedAudioWithArtwork(
            videoId: String?,
            title: String?,
            artist: String?,
            artworkUrl: String?,
            streamUrl: String? = null
        ) {
            val normalizedVideoId = SpasibaNativeAudioCache.normalizeVideoId(videoId) ?: return

            runOnUiThread {
                Log.d(LOG_TAG, "NATIVE BRIDGE PLAY REQUEST video=$normalizedVideoId title=${title.orEmpty()} artist=${artist.orEmpty()} artwork=${artworkUrl.orEmpty()}")
                nativeAudioPlayer.play(
                    normalizedVideoId,
                    title,
                    artist,
                    artworkUrl,
                    directStreamUrl = streamUrl
                )

                val intent = Intent(this@MainActivity, SpasibaKeepAliveService::class.java).apply {
                    putExtra(SpasibaKeepAliveService.EXTRA_TITLE, title)
                    putExtra(SpasibaKeepAliveService.EXTRA_ARTIST, artist)
                    putExtra(SpasibaKeepAliveService.EXTRA_ARTWORK_URL, artworkUrl)
                    putExtra(SpasibaKeepAliveService.EXTRA_STATE, "playing")
                    putExtra(SpasibaKeepAliveService.EXTRA_VIDEO_ID, normalizedVideoId)
                }

                ContextCompat.startForegroundService(this@MainActivity, intent)
            }
        }

        @JavascriptInterface
        fun setCachedAudioQueue(queueJson: String?, activeVideoId: String?) {
            val rawJson = queueJson?.takeIf { it.isNotBlank() } ?: return
            val tracks = runCatching {
                parseNativeAudioTracks(JSONArray(rawJson))
            }.getOrElse { error ->
                Log.w(LOG_TAG, "Failed to parse native audio queue", error)
                emptyList()
            }

            if (tracks.isEmpty()) {
                return
            }

            runOnUiThread {
                nativeAudioPlayer.setQueue(tracks, activeVideoId)
            }
        }

        @JavascriptInterface
        fun pauseCachedAudio() {
            runOnUiThread {
                nativeAudioPlayer.pause()
            }
        }

        @JavascriptInterface
        fun resumeCachedAudio() {
            runOnUiThread {
                nativeAudioPlayer.resume()
            }
        }

        @JavascriptInterface
        fun nextCachedAudio() {
            runOnUiThread {
                nativeAudioPlayer.next()
            }
        }

        @JavascriptInterface
        fun previousCachedAudio() {
            runOnUiThread {
                nativeAudioPlayer.previous()
            }
        }

        @JavascriptInterface
        fun stopCachedAudio() {
            runOnUiThread {
                nativeAudioPlayer.stop()
            }
        }

        @JavascriptInterface
        fun seekCachedAudio(seconds: Double) {
            nativeAudioPlayer.seek(seconds)
        }

        @JavascriptInterface
        fun setCachedAudioVolume(volume: Int) {
            nativeAudioPlayer.setVolume(volume)
        }

        @JavascriptInterface
        fun nativePlaybackSnapshot(): String = nativeAudioPlayer.snapshotJson()
    }

    private fun installDocumentStartRuntimeHints(target: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return
        }

        runCatching {
            WebViewCompat.addDocumentStartJavaScript(
                target,
                SPASIBA_RUNTIME_HINTS,
                setOf("https://spasiba.vercel.app")
            )
        }
    }

    private companion object {
        const val SPASIBA_HOST = "spasiba.vercel.app"
        const val SPASIBA_URL = "https://spasiba.vercel.app/"
        const val LOG_TAG = "SpasibaNative"
        const val BACKGROUND_TRANSITION_GRACE_MS = 30_000L
        const val PLAYBACK_STARTUP_GRACE_MS = 15_000L
        const val MAX_NATIVE_QUEUE_TRACKS = 200
        const val ANDROID_CHROME_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 8a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36 SpasibaAndroid/1.0"
        val SPASIBA_RUNTIME_HINTS = """
            (() => {
              try {
                const originalMatchMedia = window.matchMedia?.bind(window);
                if (originalMatchMedia && !window.__spasibaAndroidStandalonePatch) {
                  window.__spasibaAndroidStandalonePatch = true;
                  window.matchMedia = (query) => {
                    const queryText = String(query || "");
                    if (queryText.includes("display-mode") && queryText.includes("standalone")) {
                      return {
                        matches: true,
                        media: queryText,
                        onchange: null,
                        addListener() {},
                        removeListener() {},
                        addEventListener() {},
                        removeEventListener() {},
                        dispatchEvent() { return false; }
                      };
                    }
                    return originalMatchMedia(query);
                  };
                }
              } catch (_) {}

              try {
                Object.defineProperty(navigator, "standalone", {
                  configurable: true,
                  get() { return true; }
                });
              } catch (_) {}

              try {
                localStorage.setItem("spasiba_install_prompt_dismissed", "1");
              } catch (_) {}

              try {
                const findControl = (target) => {
                  let node = target;
                  while (node && node !== document) {
                    try {
                      if (node.matches?.("button, [role='button']")) {
                        return node;
                      }
                    } catch (_) {}
                    node = node.parentElement || node.parentNode;
                  }
                  return null;
                };

                const readVideoIdFromSource = (source) => {
                  const text = String(source || "");
                  return (
                    text.match(/\/vi\/([^/?#]+)/)?.[1] ||
                    text.match(/[?&]v=([^&#]+)/)?.[1] ||
                    ""
                  );
                };

                const findVideoIdNearTarget = (target, sourceEvent) => {
                  try {
                    const path = sourceEvent?.composedPath ? sourceEvent.composedPath() : [];
                    for (const item of path) {
                      if (!item?.querySelectorAll) {
                        continue;
                      }
                      const images = Array.from(item.querySelectorAll("img"));
                      for (const image of images) {
                        const videoId = readVideoIdFromSource(image.currentSrc || image.src || image.getAttribute("src"));
                        if (videoId) {
                          return videoId;
                        }
                      }
                    }
                  } catch (_) {}

                  let node = target;
                  let depth = 0;
                  while (node && node !== document && depth < 9) {
                    try {
                      const ownVideoId = readVideoIdFromSource(
                        node.currentSrc || node.src || node.href || node.getAttribute?.("src") || node.getAttribute?.("href")
                      );
                      if (ownVideoId) {
                        return ownVideoId;
                      }

                      const image = node.querySelector?.("img[src*='/vi/'], img[src*='ytimg.com']");
                      const childVideoId = readVideoIdFromSource(image?.currentSrc || image?.src || image?.getAttribute?.("src"));
                      if (childVideoId) {
                        return childVideoId;
                      }
                    } catch (_) {}
                    node = node.parentElement || node.parentNode;
                    depth += 1;
                  }

                  return "";
                };

                const cleanTrackLine = (value) => String(value || "")
                  .replace(/\s+/g, " ")
                  .trim();

                const readImageUrlNearTarget = (target, sourceEvent, videoId = "") => {
                  try {
                    const path = sourceEvent?.composedPath ? sourceEvent.composedPath() : [];
                    for (const item of path) {
                      const image = item?.querySelector?.("img[src*='/vi/'], img[src*='ytimg.com']") || (item?.matches?.("img") ? item : null);
                      const source = image?.currentSrc || image?.src || image?.getAttribute?.("src") || "";
                      if (source && (!videoId || readVideoIdFromSource(source) === videoId)) {
                        return source;
                      }
                    }
                  } catch (_) {}

                  let node = target;
                  let depth = 0;
                  while (node && node !== document && depth < 9) {
                    try {
                      const image = node.querySelector?.("img[src*='/vi/'], img[src*='ytimg.com']") || (node.matches?.("img") ? node : null);
                      const source = image?.currentSrc || image?.src || image?.getAttribute?.("src") || "";
                      if (source && (!videoId || readVideoIdFromSource(source) === videoId)) {
                        return source;
                      }
                    } catch (_) {}
                    node = node.parentElement || node.parentNode;
                    depth += 1;
                  }

                  return videoId ? "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg" : "";
                };

                const readTrackMetadataNearTarget = (videoId, target = null, sourceEvent = null) => {
                  let title = "";
                  let artist = "";
                  let artworkUrl = readImageUrlNearTarget(target, sourceEvent, videoId);

                  try {
                    let node = target;
                    let depth = 0;
                    while (node && node !== document && depth < 8) {
                      const nodeVideoId = findVideoIdNearTarget(node, null);
                      const text = String(node.innerText || node.textContent || "");
                      if (text.trim() && (!videoId || !nodeVideoId || nodeVideoId === videoId)) {
                        const lines = text
                          .split(/\n+/)
                          .map(cleanTrackLine)
                          .filter(Boolean)
                          .filter((line) => !/^\d{1,2}:\d{2}(?::\d{2})?$/.test(line.trim()))
                          .filter((line) => !/\b(Play|Pause|More|Share|Track options|Add to playlist|Add next in queue)\b/i.test(line));
                        if (lines.length) {
                          const titleIndex = /^SPASIBA$/i.test(lines[0] || "") ? 1 : 0;
                          title = lines[titleIndex] || title;
                          artist = (lines[titleIndex + 1] || artist || "").split("•")[0].trim();
                          break;
                        }
                      }
                      node = node.parentElement || node.parentNode;
                      depth += 1;
                    }
                  } catch (_) {}

                  if (!title) {
                    try {
                      const metadata = readPlaybackTitle();
                      title = metadata.title || "";
                      artist = metadata.artist || "";
                    } catch (_) {}
                  }

                  return {
                    videoId: videoId || "",
                    title: cleanTrackLine(title),
                    artist: cleanTrackLine(artist),
                    artworkUrl: artworkUrl || (videoId ? "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg" : "")
                  };
                };

                const collectNativeAudioQueue = (activeVideoId = "", target = null, sourceEvent = null) => {
                  const tracks = [];
                  const seen = new Set();
                  const pushTrack = (track) => {
                    const videoId = String(track?.videoId || "");
                    if (!videoId || seen.has(videoId)) {
                      return;
                    }
                    seen.add(videoId);
                    tracks.push(track);
                  };

                  try {
                    document.querySelectorAll("img[src*='/vi/'], img[src*='ytimg.com']").forEach((image) => {
                      const videoId = readVideoIdFromSource(image.currentSrc || image.src || image.getAttribute("src"));
                      if (!videoId) {
                        return;
                      }
                      pushTrack(readTrackMetadataNearTarget(videoId, image, null));
                    });
                  } catch (_) {}

                  if (activeVideoId && !seen.has(activeVideoId)) {
                    pushTrack(readTrackMetadataNearTarget(activeVideoId, target, sourceEvent));
                  }

                  return tracks.slice(0, 200);
                };

                const keepYouTubeAudioHostRenderable = () => {
                  try {
                    const host = document.getElementById("spasiba-youtube-audio-host");
                    if (!host) {
                      return;
                    }

                    const hostStyle = host.style;
                    hostStyle.setProperty("position", "fixed", "important");
                    hostStyle.setProperty("left", "0", "important");
                    hostStyle.setProperty("top", "0", "important");
                    hostStyle.setProperty("width", "220px", "important");
                    hostStyle.setProperty("height", "220px", "important");
                    hostStyle.setProperty("min-width", "220px", "important");
                    hostStyle.setProperty("min-height", "220px", "important");
                    hostStyle.setProperty("opacity", "0.001", "important");
                    hostStyle.setProperty("visibility", "visible", "important");
                    hostStyle.setProperty("display", "block", "important");
                    hostStyle.setProperty("pointer-events", "none", "important");
                    hostStyle.setProperty("transform", "translate3d(0,0,0)", "important");
                    hostStyle.setProperty("z-index", "0", "important");

                    host.querySelectorAll("iframe").forEach((frame) => {
                      frame.setAttribute(
                        "allow",
                        [
                          frame.getAttribute("allow") || "",
                          "autoplay",
                          "encrypted-media",
                          "picture-in-picture"
                        ].join("; ")
                      );
                      const frameStyle = frame.style;
                      frameStyle.setProperty("width", "220px", "important");
                      frameStyle.setProperty("height", "220px", "important");
                      frameStyle.setProperty("opacity", "1", "important");
                      frameStyle.setProperty("visibility", "visible", "important");
                      frameStyle.setProperty("display", "block", "important");
                    });
                  } catch (_) {}
                };

                const patchYouTubePlayer = (yt) => {
                  try {
                    if (!yt?.Player || yt.Player.__spasibaAndroidNativeAudioPatch) {
                      return;
                    }

                    const OriginalPlayer = yt.Player;
                    const shouldUseNativeCachedAudioPlayer = true;
                    if (shouldUseNativeCachedAudioPlayer && window.SpasibaAndroid?.playCachedAudio) {
                      const playerState = yt.PlayerState || {
                        UNSTARTED: -1,
                        ENDED: 0,
                        PLAYING: 1,
                        PAUSED: 2,
                        BUFFERING: 3,
                        CUED: 5
                      };
                      const nativePlayers = [];

                      const readNativeSnapshot = () => {
                        try {
                          const raw = window.SpasibaAndroid?.nativePlaybackSnapshot?.();
                          return raw ? JSON.parse(raw) : null;
                        } catch (_) {
                          return null;
                        }
                      };

                      const toVideoId = (value) => {
                        try {
                          if (typeof value === "string") {
                            return value;
                          }
                          if (value?.videoId) {
                            return String(value.videoId);
                          }
                        } catch (_) {}
                        return "";
                      };

                      const toStartSeconds = (value, fallback = 0) => {
                        try {
                          if (typeof value === "number") {
                            return value;
                          }
                          if (typeof value?.startSeconds === "number") {
                            return value.startSeconds;
                          }
                        } catch (_) {}
                        return fallback;
                      };

                      const toPlaylist = (value) => {
                        try {
                          if (Array.isArray(value)) {
                            return value.map(String).filter(Boolean);
                          }
                          if (Array.isArray(value?.playlist)) {
                            return value.playlist.map(String).filter(Boolean);
                          }
                        } catch (_) {}
                        return [];
                      };

                      const registerNativePlayer = (player) => {
                        window.__spasibaAndroidYouTubePlayers = window.__spasibaAndroidYouTubePlayers || [];
                        if (!window.__spasibaAndroidYouTubePlayers.includes(player)) {
                          window.__spasibaAndroidYouTubePlayers.push(player);
                        }
                        if (!nativePlayers.includes(player)) {
                          nativePlayers.push(player);
                        }
                      };

                      const previousNativeAudioDidChange = window.__spasibaAndroidNativeAudioDidChange;
                      window.__spasibaAndroidNativeAudioDidChange = (snapshot) => {
                        try {
                          previousNativeAudioDidChange?.(snapshot);
                        } catch (_) {}
                        try {
                          nativePlayers.slice().forEach((player) => {
                            try {
                              player.__spasibaApplyNativeSnapshot?.(snapshot);
                            } catch (_) {}
                          });
                        } catch (_) {}
                      };

                      const NativeCachedPlayer = function (element, config = {}) {
                        const events = config.events || {};
                        let playlist = config.videoId ? [String(config.videoId)] : [];
                        let currentIndex = 0;
                        let currentVideoId = playlist[0] || "";
                        let currentTitle = "";
                        let currentArtist = "";
                        let currentTime = 0;
                        let duration = 0;
                        let state = playerState.UNSTARTED;
                        let lastEmittedState = null;

                        const emitState = (nextState, force = false) => {
                          if (!force && lastEmittedState === nextState) {
                            state = nextState;
                            return;
                          }
                          state = nextState;
                          lastEmittedState = nextState;
                          events.onStateChange?.({ data: nextState, target: api });
                        };

                        const applySnapshot = (snapshot) => {
                          if (!snapshot) {
                            return;
                          }

                          if (snapshot.videoId && snapshot.videoId !== currentVideoId) {
                            currentVideoId = String(snapshot.videoId || "");
                            const nextIndex = playlist.indexOf(currentVideoId);
                            if (nextIndex >= 0) {
                              currentIndex = nextIndex;
                            } else if (currentVideoId) {
                              playlist = [...playlist.slice(0, currentIndex + 1), currentVideoId, ...playlist.slice(currentIndex + 1)];
                              currentIndex += 1;
                            }
                            emitState(playerState.BUFFERING, true);
                          }

                          currentTitle = String(snapshot.title || currentTitle || "");
                          currentArtist = String(snapshot.artist || currentArtist || "");
                          currentTime = Number(snapshot.currentTime || currentTime || 0);
                          duration = Number(snapshot.duration || duration || 0);

                          if (snapshot.state === "playing") {
                            emitState(playerState.PLAYING);
                            return;
                          }

                          if (snapshot.state === "loading") {
                            emitState(playerState.BUFFERING);
                            return;
                          }

                          if (snapshot.state === "ended") {
                            emitState(playerState.ENDED, true);
                            return;
                          }

                          if (snapshot.state === "paused") {
                            emitState(playerState.PAUSED);
                          }
                        };

                        const playCurrent = () => {
                          if (!currentVideoId && playlist.length) {
                            currentVideoId = playlist[currentIndex] || "";
                          }

                          if (!currentVideoId) {
                            return;
                          }

                          const snapshot = readNativeSnapshot();
                          if (snapshot?.videoId === currentVideoId) {
                            if (snapshot.state === "playing" || snapshot.state === "loading") {
                              applySnapshot(snapshot);
                              return;
                            }
                            if (snapshot.state === "paused") {
                              window.__spasibaAndroidPlaybackIntentUntil = Date.now() + 18000;
                              try {
                                window.SpasibaAndroid?.resumeCachedAudio?.();
                              } catch (_) {}
                              emitState(playerState.PLAYING, true);
                              window.setTimeout(() => applySnapshot(readNativeSnapshot()), 250);
                              return;
                            }
                          }

                          window.__spasibaAndroidPlaybackIntentUntil = Date.now() + 18000;
                          emitState(playerState.BUFFERING, true);
                          let metadata = { title: "", artist: "", artworkUrl: "" };
                          try {
                            metadata = readTrackMetadataNearTarget(currentVideoId);
                            const queue = collectNativeAudioQueue(currentVideoId);
                            window.SpasibaAndroid?.setCachedAudioQueue?.(JSON.stringify(queue), currentVideoId);
                          } catch (_) {}
                          try {
                            if (window.SpasibaAndroid?.playCachedAudioWithArtwork) {
                              window.SpasibaAndroid.playCachedAudioWithArtwork(currentVideoId, metadata.title || "", metadata.artist || "", metadata.artworkUrl || "");
                            } else {
                              window.SpasibaAndroid?.playCachedAudio(currentVideoId, metadata.title || "", metadata.artist || "");
                            }
                          } catch (_) {}
                          window.setTimeout(() => applySnapshot(readNativeSnapshot()), 250);
                          window.setTimeout(() => applySnapshot(readNativeSnapshot()), 900);
                        };

                        const api = {
                          __spasibaAndroidNativeAudioPlayer: true,
                          __spasibaApplyNativeSnapshot: applySnapshot,
	                          destroy() {
	                            try {
	                              window.SpasibaAndroid?.stopCachedAudio?.();
	                            } catch (_) {}
	                            const index = nativePlayers.indexOf(api);
                            if (index >= 0) {
                              nativePlayers.splice(index, 1);
                            }
                          },
                          getCurrentTime() {
                            applySnapshot(readNativeSnapshot());
                            return currentTime || 0;
                          },
                          getDuration() {
                            applySnapshot(readNativeSnapshot());
                            return duration || 0;
                          },
                          getPlayerState() {
                            applySnapshot(readNativeSnapshot());
                            return state;
                          },
                          getVideoData() {
                            return {
                              video_id: currentVideoId || "",
                              title: currentTitle || "",
                              author: currentArtist || ""
                            };
                          },
                          getVideoLoadedFraction() {
                            return currentVideoId ? 1 : 0;
                          },
                          loadPlaylist(value) {
                            const nextPlaylist = toPlaylist(value);
                            if (nextPlaylist.length) {
                              playlist = nextPlaylist;
                              currentIndex = Math.max(0, Number(value?.index || 0));
                              currentVideoId = playlist[currentIndex] || playlist[0] || "";
                              currentTime = toStartSeconds(value, 0);
                              playCurrent();
                            }
                          },
                          loadVideoById(value, startSeconds = 0) {
                            const nextVideoId = toVideoId(value);
                            if (!nextVideoId) {
                              return;
                            }
                            const snapshot = readNativeSnapshot();
                            if (snapshot?.videoId === nextVideoId && (snapshot.state === "playing" || snapshot.state === "loading" || snapshot.state === "paused")) {
                              if (snapshot.state === "paused") {
                                try { window.SpasibaAndroid?.resumeCachedAudio?.(); } catch (_) {}
                              }
                              return;
                            }
                            playlist = [nextVideoId];
                            currentIndex = 0;
                            currentVideoId = nextVideoId;
                            currentTime = toStartSeconds(value, startSeconds);
                            playCurrent();
                          },
                          mute() {
                            try {
                              window.SpasibaAndroid?.setCachedAudioVolume?.(0);
                            } catch (_) {}
                          },
                          nextVideo() {
                            if (playlist[currentIndex + 1]) {
                              currentIndex += 1;
                              currentVideoId = playlist[currentIndex];
                              currentTime = 0;
                              playCurrent();
                            }
                          },
                          pauseVideo() {
                            if (document.hidden || document.visibilityState === "hidden") {
                              return;
                            }
                            window.__spasibaAndroidPlaybackIntentUntil = 0;
                            window.__spasibaAndroidManualPauseUntil = Date.now() + 60000;
                            window.__spasibaAndroidPlayRequestVersion = (window.__spasibaAndroidPlayRequestVersion || 0) + 1;
                            try {
                              window.SpasibaAndroid?.pauseCachedAudio?.();
                            } catch (_) {}
                            emitState(playerState.PAUSED, true);
                          },
                          playVideo() {
                            const snapshot = readNativeSnapshot();
                            if (snapshot?.videoId === currentVideoId && snapshot.state === "paused") {
                              try {
                                window.SpasibaAndroid?.resumeCachedAudio?.();
                              } catch (_) {}
                              emitState(playerState.PLAYING, true);
                              return;
                            }
                            playCurrent();
                          },
                          previousVideo() {
                            if (playlist[currentIndex - 1]) {
                              currentIndex -= 1;
                              currentVideoId = playlist[currentIndex];
                              currentTime = 0;
                              playCurrent();
                              return;
                            }
                            currentTime = 0;
                            try {
                              window.SpasibaAndroid?.seekCachedAudio?.(0);
                            } catch (_) {}
                          },
                          seekTo(seconds) {
                            currentTime = Number(seconds || 0);
                            try {
                              window.SpasibaAndroid?.seekCachedAudio?.(currentTime);
                            } catch (_) {}
                          },
                          setVolume(volume) {
                            try {
                              window.SpasibaAndroid?.setCachedAudioVolume?.(Number(volume || 0));
                            } catch (_) {}
                          },
                          unMute() {
                            try {
                              window.SpasibaAndroid?.setCachedAudioVolume?.(100);
                            } catch (_) {}
                          }
                        };

                        registerNativePlayer(api);
                        window.setTimeout(() => {
                          events.onReady?.({ target: api });
                          if (config.playerVars?.autoplay || currentVideoId) {
                            playCurrent();
                          }
                        }, 0);

                        return api;
                      };

                      NativeCachedPlayer.prototype = OriginalPlayer.prototype;
                      Object.setPrototypeOf?.(NativeCachedPlayer, OriginalPlayer);
                      NativeCachedPlayer.__spasibaAndroidNativeAudioPatch = true;
                      yt.Player = NativeCachedPlayer;
                      return;
                    }

                    const patchPlayerInstance = (player) => {
                      if (!player || player.__spasibaAndroidPlayOnlyPatch) {
                        return player;
                      }

                      window.__spasibaAndroidYouTubePlayers = window.__spasibaAndroidYouTubePlayers || [];
                      if (!window.__spasibaAndroidYouTubePlayers.includes(player)) {
                        window.__spasibaAndroidYouTubePlayers.push(player);
                      }

                      const requestPlay = () => {
                        if ((window.__spasibaAndroidManualPauseUntil || 0) > Date.now()) {
                          return;
                        }
                        window.__spasibaAndroidPlaybackIntentUntil = Date.now() + 18000;
                        const requestVersion = window.__spasibaAndroidPlayRequestVersion || 0;
                        [120, 450, 1000, 1800, 3000].forEach((delay) => {
                          window.setTimeout(() => {
                            try {
                              if ((window.__spasibaAndroidPlayRequestVersion || 0) !== requestVersion) {
                                return;
                              }
                              if ((window.__spasibaAndroidPlaybackIntentUntil || 0) < Date.now()) {
                                return;
                              }
                              if ((window.__spasibaAndroidManualPauseUntil || 0) > Date.now()) {
                                return;
                              }
                              player.playVideo?.();
                            } catch (_) {}
                          }, delay);
                        });
                      };

                      const originalLoadPlaylist = player.loadPlaylist?.bind(player);
                      if (originalLoadPlaylist) {
                        player.loadPlaylist = function (...args) {
                          const result = originalLoadPlaylist(...args);
                          requestPlay();
                          return result;
                        };
                      }

                      const originalLoadVideoById = player.loadVideoById?.bind(player);
                      if (originalLoadVideoById) {
                        player.loadVideoById = function (...args) {
                          const result = originalLoadVideoById(...args);
                          requestPlay();
                          return result;
                        };
                      }

                      const originalPauseVideo = player.pauseVideo?.bind(player);
                      if (originalPauseVideo) {
                        player.pauseVideo = function (...args) {
                          window.__spasibaAndroidPlaybackIntentUntil = 0;
                          window.__spasibaAndroidManualPauseUntil = Date.now() + 60000;
                          window.__spasibaAndroidPlayRequestVersion = (window.__spasibaAndroidPlayRequestVersion || 0) + 1;
                          return originalPauseVideo(...args);
                        };
                      }

                      player.__spasibaAndroidPlayOnlyPatch = true;
                      return player;
                    };

                    const PatchedPlayer = function (element, config = {}) {
                      const userEvents = config.events || {};
                      const nextConfig = {
                        ...config,
                        playerVars: {
                          ...(config.playerVars || {}),
                          autoplay: 1,
                          playsinline: 1,
                          enablejsapi: 1,
                          origin: window.location.origin
                        },
                        events: {
                          ...userEvents,
                          onReady(event) {
                            patchPlayerInstance(event?.target);
                            userEvents.onReady?.(event);
                          },
                          onStateChange(event) {
                            userEvents.onStateChange?.(event);
                            const playerState = yt.PlayerState || {};
                            if (
                              (window.__spasibaAndroidPlaybackIntentUntil || 0) > Date.now() &&
                              event?.target &&
                              event.data !== playerState.PLAYING &&
                              (window.__spasibaAndroidManualPauseUntil || 0) < Date.now()
	                            ) {
	                              const requestVersion = window.__spasibaAndroidPlayRequestVersion || 0;
	                              window.setTimeout(() => {
	                                try {
	                                  if ((window.__spasibaAndroidPlayRequestVersion || 0) !== requestVersion) {
	                                    return;
	                                  }
	                                  if ((window.__spasibaAndroidPlaybackIntentUntil || 0) > Date.now()) {
	                                    event.target.playVideo?.();
	                                  }
                                } catch (_) {}
                              }, 300);
                            }
                          }
                        }
                      };
                      return patchPlayerInstance(new OriginalPlayer(element, nextConfig));
                    };

                    PatchedPlayer.prototype = OriginalPlayer.prototype;
                    Object.setPrototypeOf?.(PatchedPlayer, OriginalPlayer);
                    PatchedPlayer.__spasibaAndroidPlayOnlyPatch = true;
                    yt.Player = PatchedPlayer;
                  } catch (_) {}
                };

                const getPrimaryYouTubePlayer = () => {
                  try {
                    const players = (window.__spasibaAndroidYouTubePlayers || []).filter(Boolean);
                    let fallback = null;
                    for (let index = players.length - 1; index >= 0; index -= 1) {
                      const player = players[index];
                      if (!player?.playVideo) {
                        continue;
                      }

                      fallback = fallback || player;
                      const state = player.getPlayerState?.();
                      const videoId = player.getVideoData?.()?.video_id || "";
                      if (videoId || state === 1 || state === 3) {
                        return player;
                      }
                    }

                    return fallback;
                  } catch (_) {
                    return null;
                  }
                };

                const getOrCreateAndroidFallbackHost = () => {
                  let host = document.getElementById("spasiba-android-youtube-audio-host");
                  if (!host) {
                    host = document.createElement("div");
                    host.id = "spasiba-android-youtube-audio-host";
                    host.setAttribute("aria-hidden", "true");
                    document.body.appendChild(host);
                  }

                  const style = host.style;
                  style.setProperty("position", "fixed", "important");
                  style.setProperty("left", "0", "important");
                  style.setProperty("top", "0", "important");
                  style.setProperty("width", "220px", "important");
                  style.setProperty("height", "220px", "important");
                  style.setProperty("min-width", "220px", "important");
                  style.setProperty("min-height", "220px", "important");
                  style.setProperty("opacity", "0.01", "important");
                  style.setProperty("visibility", "visible", "important");
                  style.setProperty("display", "block", "important");
                  style.setProperty("pointer-events", "none", "important");
                  style.setProperty("transform", "translate3d(0,0,0)", "important");
                  style.setProperty("z-index", "0", "important");

                  host.querySelectorAll("iframe").forEach((frame) => {
                    frame.setAttribute(
                      "allow",
                      [
                        frame.getAttribute("allow") || "",
                        "autoplay",
                        "encrypted-media",
                        "picture-in-picture"
                      ].join("; ")
                    );
                    const frameStyle = frame.style;
                    frameStyle.setProperty("width", "220px", "important");
                    frameStyle.setProperty("height", "220px", "important");
                    frameStyle.setProperty("opacity", "1", "important");
                    frameStyle.setProperty("visibility", "visible", "important");
                    frameStyle.setProperty("display", "block", "important");
                  });

                  return host;
                };

                const rememberYouTubePlayer = (player) => {
                  try {
                    if (!player) {
                      return;
                    }
                    window.__spasibaAndroidYouTubePlayers = window.__spasibaAndroidYouTubePlayers || [];
                    if (!window.__spasibaAndroidYouTubePlayers.includes(player)) {
                      window.__spasibaAndroidYouTubePlayers.push(player);
                    }
                  } catch (_) {}
                };

                const hasUsablePlayerForVideo = (videoId) => {
                  try {
                    const players = (window.__spasibaAndroidYouTubePlayers || []).filter(Boolean);
                    for (const player of players) {
                      const state = player?.getPlayerState?.();
                      const loadedVideoId = player?.getVideoData?.()?.video_id || "";
                      const sameVideo = !videoId || !loadedVideoId || loadedVideoId === videoId;
                      if (sameVideo && (state === 1 || state === 3)) {
                        return true;
                      }
                    }
                  } catch (_) {}

                  return false;
                };

                const findCurrentPlaybackVideoId = () => {
                  try {
                    const raw = window.SpasibaAndroid?.nativePlaybackSnapshot?.();
                    const snapshot = raw ? JSON.parse(raw) : null;
                    if (snapshot?.videoId) {
                      return snapshot.videoId;
                    }
                  } catch (_) {}

                  try {
                    const images = Array.from(document.querySelectorAll("img[src*='/vi/'], img[src*='ytimg.com']"));
                    for (const image of images) {
                      const videoId = readVideoIdFromSource(image.currentSrc || image.src || image.getAttribute("src"));
                      if (!videoId) {
                        continue;
                      }

                      let node = image;
                      let depth = 0;
                      while (node && node !== document && depth < 8) {
                        const text = node.innerText || node.textContent || "";
                        if (/\bSPASIBA\b/i.test(text)) {
                          return videoId;
                        }
                        node = node.parentElement || node.parentNode;
                        depth += 1;
                      }
                    }
                  } catch (_) {}

                  return "";
                };

                const pauseAllWebPlayback = () => {
                  try {
	                    document.querySelectorAll("audio, video").forEach((media) => {
	                      try {
	                        if (media.__spasibaAndroidNativeBackedAudio) {
	                          return;
	                        }
	                        media.pause?.();
	                        media.muted = true;
	                      } catch (_) {}
                    });
                  } catch (_) {}

                  try {
                    (window.__spasibaAndroidYouTubePlayers || []).forEach((player) => {
                      try {
                        if (!player?.__spasibaAndroidNativeAudioPlayer) {
                          player.pauseVideo?.();
                        }
                      } catch (_) {}
                    });
                  } catch (_) {}

                  try {
                    const fallback = window.__spasibaAndroidFallbackPlayer;
                    fallback?.pauseVideo?.();
                  } catch (_) {}
                };

                const notifyFallbackPlayback = (state) => {
                  try {
                    const metadata = readPlaybackTitle();
                    if (window.SpasibaAndroid?.onPlaybackStateWithArtwork) {
                      window.SpasibaAndroid.onPlaybackStateWithArtwork(
                        state,
                        metadata.title || "",
                        metadata.artist || "",
                        metadata.artworkUrl || "",
                        metadata.videoId || ""
                      );
                    } else {
                      window.SpasibaAndroid?.onPlaybackState(state, metadata.title || "", metadata.artist || "");
                    }
                  } catch (_) {}
                };

                const playWithNativeCachedAudio = (videoId, target = null, sourceEvent = null) => {
                  try {
                    if (!videoId || !window.SpasibaAndroid?.playCachedAudio || (window.__spasibaAndroidManualPauseUntil || 0) > Date.now()) {
                      return false;
                    }

                    const metadata = readTrackMetadataNearTarget(videoId, target, sourceEvent);
                    const queue = collectNativeAudioQueue(videoId, target, sourceEvent);
                    window.__spasibaAndroidPlaybackIntentUntil = Date.now() + 120000;
                    window.SpasibaAndroid.onPlaybackState?.("loading", metadata.title || "", metadata.artist || "");
                    window.SpasibaAndroid.setCachedAudioQueue?.(JSON.stringify(queue), videoId);
                    if (window.SpasibaAndroid.playCachedAudioWithArtwork) {
                      window.SpasibaAndroid.playCachedAudioWithArtwork(videoId, metadata.title || "", metadata.artist || "", metadata.artworkUrl || "");
                    } else {
                      window.SpasibaAndroid.playCachedAudio(videoId, metadata.title || "", metadata.artist || "");
                    }
                    [0, 120, 450, 1200, 2500, 5000].forEach((delay) => {
                      window.setTimeout(pauseAllWebPlayback, delay);
                    });
                    return true;
                  } catch (_) {}

	                  return false;
	                };

	                const installNativeAudioElementBridge = () => {
	                  try {
	                    if (window.__spasibaAndroidAudioElementBridge || !window.SpasibaAndroid?.playCachedAudio) {
	                      return;
	                    }
	                    window.__spasibaAndroidAudioElementBridge = true;

	                    const nativeAudioElements = new Set();
	                    const nativeAudioState = new WeakMap();
	                    const streamUrlToVideoId = new Map();
	                    let lastStreamRequest = { videoId: "", at: 0 };
	                    let lastPlaybackGesture = { target: null, event: null, at: 0 };

	                    const normalizeMaybeUrl = (value) => {
	                      try {
	                        return new URL(String(value || ""), window.location.href).toString();
	                      } catch (_) {
	                        return String(value || "");
	                      }
	                    };

	                    const rememberStreamUrl = (url, videoId) => {
	                      if (!url || !videoId) {
	                        return;
	                      }
	                      streamUrlToVideoId.set(String(url), videoId);
	                      streamUrlToVideoId.set(normalizeMaybeUrl(url), videoId);
	                    };

	                    const streamVideoIdFromRequest = (request) => {
	                      try {
	                        const raw = String(request?.url || request || "");
	                        const parsed = new URL(raw, window.location.href);
	                        if (parsed.origin === window.location.origin && parsed.pathname === "/api/stream") {
	                          return parsed.searchParams.get("videoId") || readVideoIdFromSource(parsed.searchParams.get("url") || "");
	                        }
	                      } catch (_) {}
	                      return "";
	                    };

	                    const originalFetch = window.fetch?.bind(window);
	                    if (originalFetch && !window.fetch.__spasibaAndroidNativeFetchPatch) {
	                      const patchedFetch = function (...args) {
	                        const videoId = streamVideoIdFromRequest(args[0]);
	                        if (videoId) {
	                          lastStreamRequest = { videoId, at: Date.now() };
	                        }
	                        return originalFetch(...args).then((response) => {
	                          try {
	                            if (videoId) {
	                              response.clone().json().then((payload) => {
	                                try {
	                                  rememberStreamUrl(payload?.streamUrl, videoId);
	                                  (payload?.streamCandidates || []).forEach((candidate) => {
	                                    rememberStreamUrl(candidate?.url, videoId);
	                                  });
	                                } catch (_) {}
	                              }).catch(() => {});
	                            }
	                          } catch (_) {}
	                          return response;
	                        });
	                      };
	                      patchedFetch.__spasibaAndroidNativeFetchPatch = true;
	                      window.fetch = patchedFetch;
	                    }

	                    const dispatchNativeMediaEvent = (element, name) => {
	                      try {
	                        element.dispatchEvent(new Event(name));
	                      } catch (_) {}
	                    };

	                    const isNativeBackedAudio = (element) =>
	                      !!element?.__spasibaAndroidNativeBackedAudio && nativeAudioState.has(element);

	                    const videoIdForAudioElement = (element) => {
	                      try {
	                        const sources = [
	                          element.currentSrc || "",
	                          element.src || "",
	                          element.getAttribute?.("src") || ""
	                        ].map(String).filter(Boolean);

	                        for (const source of sources) {
	                          const exact = streamUrlToVideoId.get(source) || streamUrlToVideoId.get(normalizeMaybeUrl(source));
	                          if (exact) {
	                            return exact;
	                          }
	                          const parsedVideoId = streamVideoIdFromRequest(source) || readVideoIdFromSource(source);
	                          if (parsedVideoId) {
	                            return parsedVideoId;
	                          }
	                        }
	                      } catch (_) {}

	                      if (lastStreamRequest.videoId && Date.now() - lastStreamRequest.at < 30000) {
	                        return lastStreamRequest.videoId;
	                      }

	                      return findCurrentPlaybackVideoId();
	                    };

	                    const markNativeBackedAudio = (element, videoId) => {
	                      const previous = nativeAudioState.get(element) || {};
	                      const snapshot = readAndroidNativeSnapshot?.() || {};
	                      const state = {
	                        videoId: videoId || previous.videoId || snapshot.videoId || "",
	                        currentTime: Number(previous.currentTime || snapshot.currentTime || 0),
	                        duration: Number(previous.duration || snapshot.duration || 0),
	                        paused: snapshot.state ? snapshot.state !== "playing" : true,
	                        ended: snapshot.state === "ended"
	                      };
	                      element.__spasibaAndroidNativeBackedAudio = true;
	                      nativeAudioElements.add(element);
	                      nativeAudioState.set(element, state);
	                      return state;
	                    };

	                    const playNativeAudioElement = (element, videoId) => {
	                      const gesture = Date.now() - lastPlaybackGesture.at < 20000 ? lastPlaybackGesture : { target: null, event: null };
	                      const state = markNativeBackedAudio(element, videoId);
	                      state.paused = false;
	                      state.ended = false;
	                      dispatchNativeMediaEvent(element, "loadstart");
	                      dispatchNativeMediaEvent(element, "waiting");
	                      if (playWithNativeCachedAudio(videoId, gesture.target, gesture.event)) {
	                        return true;
	                      }
	                      return false;
	                    };

	                    const mediaPrototype = window.HTMLMediaElement?.prototype;
	                    if (!mediaPrototype) {
	                      return;
	                    }

	                    const originalPlay = mediaPrototype.play;
	                    const originalPause = mediaPrototype.pause;
	                    const originalLoad = mediaPrototype.load;
	                    const currentTimeDescriptor = Object.getOwnPropertyDescriptor(mediaPrototype, "currentTime");
	                    const durationDescriptor = Object.getOwnPropertyDescriptor(mediaPrototype, "duration");
	                    const pausedDescriptor = Object.getOwnPropertyDescriptor(mediaPrototype, "paused");
	                    const endedDescriptor = Object.getOwnPropertyDescriptor(mediaPrototype, "ended");
	                    const bufferedDescriptor = Object.getOwnPropertyDescriptor(mediaPrototype, "buffered");

	                    mediaPrototype.play = function (...args) {
	                      try {
	                        if (String(this.tagName || "").toUpperCase() === "AUDIO") {
	                          this.muted = true;
	                          this.pause?.();
	                          const videoId = videoIdForAudioElement(this);
	                          if (videoId) {
	                            playNativeAudioElement(this, videoId);
	                          }
	                          return Promise.resolve();
	                        }
	                      } catch (_) {}
	                      return Promise.resolve();
	                    };

	                    mediaPrototype.pause = function (...args) {
	                      try {
	                        if (isNativeBackedAudio(this)) {
	                          const state = nativeAudioState.get(this);
	                          state.paused = true;
	                          state.ended = false;
	                          window.__spasibaAndroidPlaybackIntentUntil = 0;
	                          window.__spasibaAndroidManualPauseUntil = Date.now() + 60000;
	                          window.SpasibaAndroid?.pauseCachedAudio?.();
	                          dispatchNativeMediaEvent(this, "pause");
	                          return;
	                        }
	                      } catch (_) {}
	                      return originalPause.apply(this, args);
	                    };

	                    mediaPrototype.load = function (...args) {
	                      try {
	                        if (String(this.tagName || "").toUpperCase() === "AUDIO") {
	                          const videoId = videoIdForAudioElement(this);
	                          if (videoId) {
	                            markNativeBackedAudio(this, videoId);
	                            dispatchNativeMediaEvent(this, "loadstart");
	                            return;
	                          }
	                        }
	                      } catch (_) {}
	                      return originalLoad.apply(this, args);
	                    };

	                    try {
	                      Object.defineProperty(mediaPrototype, "currentTime", {
	                        configurable: true,
	                        get() {
	                          if (isNativeBackedAudio(this)) {
	                            return Number(nativeAudioState.get(this)?.currentTime || 0);
	                          }
	                          return currentTimeDescriptor?.get?.call(this) || 0;
	                        },
	                        set(value) {
	                          if (isNativeBackedAudio(this)) {
	                            const safeValue = Math.max(0, Number(value || 0));
	                            const state = nativeAudioState.get(this);
	                            state.currentTime = safeValue;
	                            window.SpasibaAndroid?.seekCachedAudio?.(safeValue);
	                            dispatchNativeMediaEvent(this, "timeupdate");
	                            return;
	                          }
	                          currentTimeDescriptor?.set?.call(this, value);
	                        }
	                      });
	                    } catch (_) {}

	                    try {
	                      Object.defineProperty(mediaPrototype, "duration", {
	                        configurable: true,
	                        get() {
	                          if (isNativeBackedAudio(this)) {
	                            return Number(nativeAudioState.get(this)?.duration || 0);
	                          }
	                          return durationDescriptor?.get?.call(this) || 0;
	                        }
	                      });
	                    } catch (_) {}

	                    try {
	                      Object.defineProperty(mediaPrototype, "paused", {
	                        configurable: true,
	                        get() {
	                          if (isNativeBackedAudio(this)) {
	                            return !!nativeAudioState.get(this)?.paused;
	                          }
	                          return pausedDescriptor?.get?.call(this) ?? true;
	                        }
	                      });
	                    } catch (_) {}

	                    try {
	                      Object.defineProperty(mediaPrototype, "ended", {
	                        configurable: true,
	                        get() {
	                          if (isNativeBackedAudio(this)) {
	                            return !!nativeAudioState.get(this)?.ended;
	                          }
	                          return endedDescriptor?.get?.call(this) ?? false;
	                        }
	                      });
	                    } catch (_) {}

	                    try {
	                      Object.defineProperty(mediaPrototype, "buffered", {
	                        configurable: true,
	                        get() {
	                          if (isNativeBackedAudio(this)) {
	                            const state = nativeAudioState.get(this) || {};
	                            const end = Math.max(Number(state.currentTime || 0), Number(state.duration || 0));
	                            return {
	                              length: end > 0 ? 1 : 0,
	                              start() { return 0; },
	                              end() { return end; }
	                            };
	                          }
	                          return bufferedDescriptor?.get?.call(this);
	                        }
	                      });
	                    } catch (_) {}

	                    document.addEventListener("click", (event) => {
	                      try {
	                        const control = findControl(event.target);
	                        const label = [
	                          control?.getAttribute?.("aria-label") || "",
	                          control?.textContent || ""
	                        ].join(" ");
	                        if (/\b(Track options|More actions|Share song|Add to playlist|Add next in queue|Search|Like|Unlike|Save|Profile|Close player|Dismiss player)\b/i.test(label)) {
	                          return;
	                        }
	                        lastPlaybackGesture = { target: event.target, event, at: Date.now() };
	                      } catch (_) {}
	                    }, true);

	                    const previousNativeAudioDidChange = window.__spasibaAndroidNativeAudioDidChange;
	                    window.__spasibaAndroidNativeAudioDidChange = (snapshot) => {
	                      try {
	                        previousNativeAudioDidChange?.(snapshot);
	                      } catch (_) {}
	                      try {
	                        if (!snapshot) {
	                          return;
	                        }
	                        nativeAudioElements.forEach((element) => {
	                          try {
	                            if (!element?.isConnected && nativeAudioElements.delete) {
	                              nativeAudioElements.delete(element);
	                              return;
	                            }
	                            const state = markNativeBackedAudio(element, snapshot.videoId || "");
	                            const previousDuration = state.duration;
	                            state.currentTime = Number(snapshot.currentTime || 0);
	                            state.duration = Number(snapshot.duration || state.duration || 0);
	                            state.paused = snapshot.state !== "playing" && snapshot.state !== "loading";
	                            state.ended = snapshot.state === "ended";
	                            if (previousDuration !== state.duration) {
	                              dispatchNativeMediaEvent(element, "durationchange");
	                            }
	                            dispatchNativeMediaEvent(element, "timeupdate");
	                            if (snapshot.state === "loading") {
	                              dispatchNativeMediaEvent(element, "waiting");
	                            } else if (snapshot.state === "playing") {
	                              dispatchNativeMediaEvent(element, "canplay");
	                              dispatchNativeMediaEvent(element, "playing");
	                            } else if (snapshot.state === "paused") {
	                              dispatchNativeMediaEvent(element, "pause");
	                            } else if (snapshot.state === "ended") {
	                              dispatchNativeMediaEvent(element, "ended");
	                            }
	                          } catch (_) {}
	                        });
	                      } catch (_) {}
	                    };
	                  } catch (_) {}
	                };
	                installNativeAudioElementBridge();

		                const playWithAndroidFallbackPlayer = (videoId) => {
		                  return false;
	                  try {
                    if (!videoId || !window.YT?.Player || (window.__spasibaAndroidManualPauseUntil || 0) > Date.now()) {
                      return false;
                    }

                    patchYouTubePlayer(window.YT);
                    const host = getOrCreateAndroidFallbackHost();
                    const existing = window.__spasibaAndroidFallbackPlayer;

                    if (existing?.loadVideoById && existing?.playVideo) {
                      rememberYouTubePlayer(existing);
                      const loadedVideoId = existing.getVideoData?.()?.video_id || "";
                      if (loadedVideoId !== videoId) {
                        existing.loadVideoById(videoId, 0);
                      }
                      existing.playVideo();
                      return true;
                    }

                    console.info("[SpasibaAndroid] creating fallback player " + videoId);
                    const created = new window.YT.Player(host, {
                      videoId,
                      playerVars: {
                        autoplay: 1,
                        controls: 0,
                        playsinline: 1,
                        rel: 0,
                        modestbranding: 1,
                        enablejsapi: 1,
                        origin: window.location.origin
                      },
                      events: {
                        onReady(event) {
                          try {
                            window.__spasibaAndroidFallbackPlayer = event.target;
                            rememberYouTubePlayer(event.target);
                            event.target.playVideo();
                          } catch (_) {}
                        },
                        onStateChange(event) {
                          try {
                            rememberYouTubePlayer(event.target);
                            const playerState = window.YT?.PlayerState || {};
                            if (event.data === playerState.PLAYING || event.data === 1) {
                              notifyFallbackPlayback("playing");
                              return;
                            }
                            if (event.data === playerState.BUFFERING || event.data === 3) {
                              notifyFallbackPlayback("loading");
                              return;
                            }
                            if (event.data === playerState.PAUSED || event.data === 2) {
                              notifyFallbackPlayback("paused");
                            }
                          } catch (_) {}
                        },
                        onError(event) {
                          console.warn("[SpasibaAndroid] fallback player error " + event.data);
                        }
                      }
                    });

                    window.__spasibaAndroidFallbackPlayer = created;
                    rememberYouTubePlayer(created);
                    return true;
                  } catch (_) {}

                  return false;
                };

	                const nudgeYouTubePlaybackFromUserGesture = (videoId) => {
	                  try {
	                    if ((window.__spasibaAndroidManualPauseUntil || 0) > Date.now()) {
	                      return false;
	                    }
	                    return playWithNativeCachedAudio(videoId);
	                  } catch (_) {}

	                  return false;
	                };

                const installYouTubePlayerPatch = () => {
                  try {
                    patchYouTubePlayer(window.YT);
                    if (!window.__spasibaAndroidYouTubeSetterPatch) {
                      let currentYT = window.YT;
                      Object.defineProperty(window, "YT", {
                        configurable: true,
                        get() {
                          return currentYT;
                        },
                        set(value) {
                          currentYT = value;
                          patchYouTubePlayer(currentYT);
                        }
                      });
                      window.__spasibaAndroidYouTubeSetterPatch = true;
                    }
                  } catch (_) {}
                };

                installYouTubePlayerPatch();

                if (!window.__spasibaAndroidYouTubeHostPatch) {
                  window.__spasibaAndroidYouTubeHostPatch = true;
                  const observer = new MutationObserver(keepYouTubeAudioHostRenderable);
                  observer.observe(document.documentElement, {
                    childList: true,
                    subtree: true
                  });
                  window.setInterval(keepYouTubeAudioHostRenderable, 700);
                  keepYouTubeAudioHostRenderable();
                }

                const readAndroidNativeSnapshot = () => {
                  try {
                    const raw = window.SpasibaAndroid?.nativePlaybackSnapshot?.();
                    return raw ? JSON.parse(raw) : null;
                  } catch (_) {
                    return null;
                  }
                };

                const readYouTubePlayback = () => {
                  try {
                    const players = (window.__spasibaAndroidYouTubePlayers || []).filter(Boolean);
                    const playerState = window.YT?.PlayerState || {};

                    for (let index = players.length - 1; index >= 0; index -= 1) {
                      const player = players[index];
                      const state = player?.getPlayerState?.();
                      const data = player?.getVideoData?.() || {};
                      const videoId = data.video_id || "";
                      const title = data.title || "";
                      const artist = data.author || "";
                      const currentTime = Number(player?.getCurrentTime?.() || 0);
                      const duration = Number(player?.getDuration?.() || 0);

                      if (state === playerState.PLAYING || state === 1) {
                        return { state: "playing", title, artist, videoId, currentTime, duration };
                      }

                      if (state === playerState.BUFFERING || state === 3) {
                        return { state: "loading", title, artist, videoId, currentTime, duration };
                      }

                      if (state === playerState.PAUSED || state === 2) {
                        return { state: "paused", title, artist, videoId, currentTime, duration };
                      }
                    }
                  } catch (_) {}

                  return { state: "", title: "", artist: "", videoId: "", currentTime: 0, duration: 0 };
                };

                const readPlaybackTitle = () => {
                  try {
                    const metadata = navigator.mediaSession?.metadata;
                    if (metadata?.title) {
                      const artwork = Array.isArray(metadata.artwork) ? metadata.artwork : [];
                      const artworkUrl = artwork.length ? (artwork[artwork.length - 1]?.src || "") : "";
                      return {
                        title: metadata.title || "",
                        artist: metadata.artist || "",
                        artworkUrl,
                        videoId: ""
                      };
                    }
                  } catch (_) {}

                  const youtubePlayback = readYouTubePlayback();
                  if (youtubePlayback.title) {
                      return {
                        title: youtubePlayback.title || "",
                        artist: youtubePlayback.artist || "",
                      artworkUrl: youtubePlayback.videoId ? "https://i.ytimg.com/vi/" + youtubePlayback.videoId + "/hqdefault.jpg" : "",
                      videoId: youtubePlayback.videoId || ""
                    };
                  }

                  const lines = (document.body?.innerText || "")
                    .split("\n")
                    .map((line) => line.trim())
                    .filter(Boolean);
                  const markerIndex = lines.lastIndexOf("SPASIBA");
                  return {
                    title: markerIndex >= 0 ? lines[markerIndex + 1] || "" : "",
                    artist: markerIndex >= 0 ? (lines[markerIndex + 2] || "").split("•")[0].trim() : "",
                    artworkUrl: "",
                    videoId: ""
                  };
                };

                const inferPlaybackState = () => {
                  const mediaState = navigator.mediaSession?.playbackState || "none";
                  if (mediaState === "playing") {
                    return "playing";
                  }

                  const youtubePlayback = readYouTubePlayback();
                  if (youtubePlayback.state) {
                    return youtubePlayback.state;
                  }

                  const controls = Array.from(document.querySelectorAll("button, [role='button']"));
                  const hasPauseControl = controls.some((control) => {
                    const label = [
                      control.getAttribute("aria-label") || "",
                      control.textContent || ""
                    ].join(" ");
                    return /\bPause\b/i.test(label);
                  });

                  if (hasPauseControl) {
                    return "playing";
                  }

                  const pageText = document.body?.innerText || "";
                  if (/\bLOADING\.\.\./i.test(pageText)) {
                    return "loading";
                  }

                  const host = document.getElementById("spasiba-youtube-audio-host");
                  const hasYouTubeFrame = !!host?.querySelector?.("iframe");
                  if (hasYouTubeFrame && (window.__spasibaAndroidPlaybackIntentUntil || 0) > Date.now()) {
                    return "loading";
                  }

                  if ((window.__spasibaAndroidPlaybackIntentUntil || 0) > Date.now()) {
                    return "loading";
                  }
                
                  if (mediaState === "none") {
                      try {
                          const snapshot = JSON.parse(window.SpasibaAndroid?.nativePlaybackSnapshot?.() || "{}");

                          if (snapshot.state === "playing") return "playing";
                          if (snapshot.state === "paused")  return "paused";
                          if (snapshot.state === "loading") return "loading";
                      } catch (_) {}
                  }


                  return mediaState;
                };

                const readPlaybackSnapshotForAndroid = () => {
                  try {
                    const youtubePlayback = readYouTubePlayback();
                    const metadata = readPlaybackTitle();
                    const videoId = youtubePlayback.videoId || metadata.videoId || findCurrentPlaybackVideoId() || "";
                    const artworkUrl = metadata.artworkUrl || (videoId ? "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg" : "");
                    const state = inferPlaybackState();
                    return {
                      state,
                      title: metadata.title || youtubePlayback.title || "",
                      artist: metadata.artist || youtubePlayback.artist || "",
                      artworkUrl,
                      videoId,
                      currentTime: Number(youtubePlayback.currentTime || 0),
                      duration: Number(youtubePlayback.duration || 0),
                      queue: collectNativeAudioQueue(videoId)
                    };
                  } catch (_) {
                    return null;
                  }
                };

                window.__spasibaAndroidGetPlaybackSnapshot = () => {
                  try {
                    const snapshot = readPlaybackSnapshotForAndroid();
                    return snapshot ? JSON.stringify(snapshot) : "";
                  } catch (_) {
                    return "";
                  }
                };

                const resolveGestureVideoId = (target = null, sourceEvent = null) => {
                  try {
                    const direct = findVideoIdNearTarget(target, sourceEvent);
                    if (direct) {
                      return direct;
                    }
                  } catch (_) {}

                  try {
                    const snapshot = readPlaybackSnapshotForAndroid();
                    if (snapshot?.videoId) {
                      return snapshot.videoId;
                    }
                  } catch (_) {}

                  try {
                    const metadata = readPlaybackTitle();
                    if (metadata?.videoId) {
                      return metadata.videoId;
                    }
                  } catch (_) {}

                  try {
                    const queue = collectNativeAudioQueue("", target, sourceEvent);
                    if (queue?.[0]?.videoId) {
                      return queue[0].videoId;
                    }
                  } catch (_) {}

                  return findCurrentPlaybackVideoId();
                };

                window.__spasibaAndroidApplyNativePlaybackSnapshot = (rawSnapshot) => {
                  try {
                    const snapshot = typeof rawSnapshot === "string" ? JSON.parse(rawSnapshot) : rawSnapshot;
                    if (!snapshot?.videoId) {
                      return false;
                    }

                    const player = getPrimaryYouTubePlayer();
                    if (!player?.playVideo) {
                      return false;
                    }

                    window.__spasibaAndroidManualPauseUntil = 0;
                    window.__spasibaAndroidPlaybackIntentUntil = Date.now() + 18000;
                    window.__spasibaAndroidPlayRequestVersion = (window.__spasibaAndroidPlayRequestVersion || 0) + 1;

                    const videoId = String(snapshot.videoId || "");
                    const startSeconds = Math.max(0, Number(snapshot.currentTime || 0));
                    const loadedVideoId = player.getVideoData?.()?.video_id || "";
                    if (loadedVideoId && loadedVideoId !== videoId && player.loadVideoById) {
                      player.loadVideoById(videoId, startSeconds);
                    } else {
                      player.seekTo?.(startSeconds, true);
                    }

                    if (snapshot.state === "playing" || snapshot.state === "loading") {
                      player.playVideo?.();
                    } else {
                      player.pauseVideo?.();
                    }

                    window.setTimeout(notifyAndroidPlayback, 300);
                    window.setTimeout(notifyAndroidPlayback, 1000);
                    return true;
                  } catch (_) {
                    return false;
                  }
                };

                const notifyAndroidPlayback = () => {
                  try {
                    const state = inferPlaybackState();
                    const metadata = readPlaybackTitle();
                    if (window.SpasibaAndroid?.onPlaybackStateWithArtwork) {
                      window.SpasibaAndroid.onPlaybackStateWithArtwork(
                        state,
                        metadata.title || "",
                        metadata.artist || "",
                        metadata.artworkUrl || "",
                        metadata.videoId || ""
                      );
                    } else {
                      window.SpasibaAndroid?.onPlaybackState(
                        state,
                        metadata.title || "",
                        metadata.artist || ""
                      );
                    }
                  } catch (_) {}
                };

                if (!window.__spasibaAndroidPlaybackBridge) {
                  window.__spasibaAndroidPlaybackBridge = true;
                  window.setInterval(notifyAndroidPlayback, 800);
                  document.addEventListener("visibilitychange", notifyAndroidPlayback);
                  window.addEventListener("pagehide", notifyAndroidPlayback);
                  window.addEventListener("pageshow", notifyAndroidPlayback);
                  const handlePlaybackGesture = (event) => {
                    try {
                      const control = findControl(event.target);
                      const label = [
                        control?.getAttribute?.("aria-label") || "",
                        control?.textContent || ""
                      ].join(" ");

	                      if (/\b(Close player|Dismiss player)\b/i.test(label)) {
	                        try {
	                          window.__spasibaAndroidPlaybackIntentUntil = 0;
	                          window.__spasibaAndroidManualPauseUntil = Date.now() + 60000;
	                          window.__spasibaAndroidPlayRequestVersion = (window.__spasibaAndroidPlayRequestVersion || 0) + 1;
	                          window.SpasibaAndroid?.stopCachedAudio?.();
	                        } catch (_) {}
	                        window.setTimeout(notifyAndroidPlayback, 400);
	                        return;
	                      }

		                      if (/\b(Track options|More actions|Share song|Add to playlist|Add next in queue|Search|Like|Unlike|Save|Profile)\b/i.test(label)) {
		                        return;
		                      }

                      if (/\b(Previous|Prev)\b/i.test(label)) {
                        window.__spasibaAndroidManualPauseUntil = 0;
                        window.__spasibaAndroidPlaybackIntentUntil = Date.now() + 120000;
                        try {
                          window.SpasibaAndroid?.previousCachedAudio?.();
                          pauseAllWebPlayback();
                        } catch (_) {}
                        window.setTimeout(notifyAndroidPlayback, 300);
                        window.setTimeout(notifyAndroidPlayback, 1200);
                        return;
                      }

                      if (/\bNext\b/i.test(label)) {
                        window.__spasibaAndroidManualPauseUntil = 0;
                        window.__spasibaAndroidPlaybackIntentUntil = Date.now() + 120000;
                        try {
                          window.SpasibaAndroid?.nextCachedAudio?.();
                          pauseAllWebPlayback();
                        } catch (_) {}
                        window.setTimeout(notifyAndroidPlayback, 300);
                        window.setTimeout(notifyAndroidPlayback, 1200);
                        return;
                      }

	                      if (/\bPlay\b|PLAY ALL/i.test(label)) {
	                        window.__spasibaAndroidManualPauseUntil = 0;
	                        window.__spasibaAndroidPlaybackIntentUntil = Date.now() + 120000;
	                        try {
	                          const videoId = resolveGestureVideoId(event.target, event);
	                          if (videoId) {
	                            console.info("[SpasibaAndroid] native play gesture " + videoId);
	                            playWithNativeCachedAudio(videoId, event.target, event);
	                          }
	                        } catch (_) {}
	                        window.setTimeout(notifyAndroidPlayback, 500);
	                        window.setTimeout(notifyAndroidPlayback, 1600);
	                        window.setTimeout(pauseAllWebPlayback, 2200);
	                        return;
	                      }

	                      if (/\bPause\b/i.test(label)) {
	                        window.__spasibaAndroidPlaybackIntentUntil = 0;
	                        window.__spasibaAndroidManualPauseUntil = Date.now() + 2500;
	                        try {
	                          window.SpasibaAndroid?.pauseCachedAudio?.();
	                        } catch (_) {}
	                        window.setTimeout(notifyAndroidPlayback, 500);
	                      }
                    } catch (_) {}
                  };
                  document.addEventListener("click", handlePlaybackGesture, true);
                }

                notifyAndroidPlayback();
              } catch (_) {}
            })();
        """.trimIndent()

        private fun String.quoteForJavaScript(): String =
            "\"" + SpasibaNativeAudioCache.jsonEscape(this) + "\""
    }
}
