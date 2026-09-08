package com.chromemobile.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.chromemobile.browser.agent.SearchEngine
import com.chromemobile.browser.preferences.BrowserPreferences
import com.chromemobile.browser.preferences.CookiePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("SetJavaScriptEnabled")
class WebViewBrowserEngine(
    private val context: Context,
    val webView: WebView = WebView(context),
    private val browserPreferences: BrowserPreferences = BrowserPreferences(context)
) : BrowserEngine {

    private val _state = MutableStateFlow(BrowserState())
    override val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<ConsoleMessageEntry>>(emptyList())
    override val consoleLogs: StateFlow<List<ConsoleMessageEntry>> = _consoleLogs.asStateFlow()

    private val _isScrollingUp = MutableStateFlow(true)
    val isScrollingUp: StateFlow<Boolean> = _isScrollingUp.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val agentWebViewClient: AgentWebViewClient
    private val agentWebChromeClient: AgentWebChromeClient

    init {
        configureWebSettings()
        applyPreferences(browserPreferences)

        // Register Web Share Bridge for native Android sharing support
        webView.addJavascriptInterface(WebShareBridge(context), "__androidWebShare")

        // Track vertical scroll direction to collapse/expand URL bar
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val delta = scrollY - oldScrollY
            if (delta > 12 && scrollY > 50) {
                if (_isScrollingUp.value) {
                    _isScrollingUp.value = false
                }
            } else if (delta < -12 || scrollY <= 25) {
                if (!_isScrollingUp.value) {
                    _isScrollingUp.value = true
                }
            }
        }

        agentWebViewClient = AgentWebViewClient(
            context = context,
            browserStateFlow = _state
        )

        agentWebChromeClient = AgentWebChromeClient(
            browserStateFlow = _state,
            consoleLogsFlow = _consoleLogs,
            onProgressChange = { view, progress ->
                // Inject agent runtime when page DOM reaches interactive ready stage
                if (progress >= 85) {
                    agentWebViewClient.injectAgentRuntime(view)
                }
            }
        )

        webView.webViewClient = agentWebViewClient
        webView.webChromeClient = agentWebChromeClient

        // Ensure the WebView has default mobile dimensions even when offscreen or in background
        ensureMeasured()
        webView.resumeTimers()
    }

    private fun configureWebSettings() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setGeolocationEnabled(true)

        // Ensure proper rendering layer & background color
        webView.setBackgroundColor(Color.WHITE)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.isClickable = true

        // Set default zoom factor to 80%
        settings.textZoom = browserPreferences.zoomFactor

        // Default mobile Chrome User-Agent (Chrome 131)
        settings.userAgentString = MOBILE_USER_AGENT

        // Configure CookieManager
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, false)
    }

    fun applyPreferences(prefs: BrowserPreferences) {
        runOnMainThread {
            val settings = webView.settings
            settings.textZoom = prefs.zoomFactor
            settings.javaScriptEnabled = prefs.javascriptEnabled
            settings.domStorageEnabled = prefs.domStorageEnabled
            settings.databaseEnabled = prefs.domStorageEnabled
            settings.javaScriptCanOpenWindowsAutomatically = prefs.popupsEnabled
            settings.loadsImagesAutomatically = prefs.loadsImagesAutomatically

            // Desktop Site Mode toggle
            settings.userAgentString = if (prefs.desktopSiteMode) {
                DESKTOP_USER_AGENT
            } else {
                MOBILE_USER_AGENT
            }

            // Cookie Policy
            val cookieManager = CookieManager.getInstance()
            when (prefs.cookiePolicy) {
                CookiePolicy.ALLOW_ALL -> {
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, true)
                }
                CookiePolicy.BLOCK_THIRD_PARTY -> {
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, false)
                }
                CookiePolicy.BLOCK_ALL -> {
                    cookieManager.setAcceptCookie(false)
                    cookieManager.setAcceptThirdPartyCookies(webView, false)
                }
            }
        }
    }

    override fun setZoomFactor(percent: Int) {
        runOnMainThread {
            browserPreferences.zoomFactor = percent
            webView.settings.textZoom = percent
        }
    }

    override fun getZoomFactor(): Int {
        return webView.settings.textZoom
    }

    override fun clearBrowsingData(
        clearHistory: Boolean,
        clearCookies: Boolean,
        clearCache: Boolean,
        clearStorage: Boolean,
        onComplete: (() -> Unit)?
    ) {
        runOnMainThread {
            if (clearHistory) {
                webView.clearHistory()
            }
            if (clearCache) {
                webView.clearCache(true)
            }
            if (clearCookies) {
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                }
            }
            if (clearStorage) {
                WebStorage.getInstance().deleteAllData()
            }
            onComplete?.invoke()
        }
    }

    override fun loadUrl(url: String) {
        _isScrollingUp.value = true
        val searchEngine = browserPreferences.searchEngine
        val formattedUrl = when {
            url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:") || url.startsWith("data:") || url.startsWith("blob:") -> url
            url.contains(".") && !url.contains(" ") -> "https://$url"
            else -> searchEngine.searchUrl + java.net.URLEncoder.encode(url, "UTF-8")
        }

        runOnMainThread {
            webView.loadUrl(formattedUrl)
        }
    }

    override fun reload() {
        _isScrollingUp.value = true
        runOnMainThread {
            webView.reload()
        }
    }

    override fun stopLoading() {
        runOnMainThread {
            webView.stopLoading()
        }
    }

    override fun goBack(): Boolean {
        _isScrollingUp.value = true
        if (webView.canGoBack()) {
            runOnMainThread {
                webView.goBack()
            }
            return true
        }
        return false
    }

    override fun goForward(): Boolean {
        _isScrollingUp.value = true
        if (webView.canGoForward()) {
            runOnMainThread {
                webView.goForward()
            }
            return true
        }
        return false
    }

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        runOnMainThread {
            webView.evaluateJavascript(script) { result ->
                callback?.invoke(result)
            }
        }
    }

    override suspend fun evaluateJavascriptAsync(script: String): String? = withContext(Dispatchers.Main) {
        suspendCoroutine { continuation ->
            webView.evaluateJavascript(script) { result ->
                continuation.resume(result)
            }
        }
    }

    override fun captureScreenshot(onCaptured: (Bitmap?) -> Unit) {
        runOnMainThread {
            ensureMeasured()
            SnapshotProvider.captureWebView(webView, fullResolution = true, onCaptured)
        }
    }

    override suspend fun captureScreenshotAsync(): Bitmap? = withContext(Dispatchers.Main) {
        suspendCoroutine { continuation ->
            ensureMeasured()
            SnapshotProvider.captureWebView(webView, fullResolution = true) { bitmap ->
                continuation.resume(bitmap)
            }
        }
    }

    fun ensureMeasured() {
        runOnMainThread {
            if (webView.width <= 0 || webView.height <= 0) {
                val dm = context.resources.displayMetrics
                val w = if (dm.widthPixels > 0) dm.widthPixels else 1080
                val h = if (dm.heightPixels > 0) dm.heightPixels else 2400
                webView.measure(
                    View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
                )
                webView.layout(0, 0, w, h)
            }
        }
    }

    override fun setAgentInteractionEnabled(enabled: Boolean) {
        // No-op: Agent interaction is non-destructive
    }

    override fun clearConsoleLogs() {
        _consoleLogs.value = emptyList()
    }

    override fun destroy() {
        runOnMainThread {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    companion object {
        const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 ChromeMobileAI/1.0"
        const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 ChromeMobileAI/1.0"
    }
}
