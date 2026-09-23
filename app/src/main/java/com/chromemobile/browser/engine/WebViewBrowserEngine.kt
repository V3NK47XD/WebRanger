package com.chromemobile.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.WebViewCompat
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
    val chromeVersion: String = detectChromeVersion(context, webView)
    val mobileUserAgent: String = buildMobileUserAgent(chromeVersion)
    val desktopUserAgent: String = buildDesktopUserAgent(chromeVersion)

    fun getUserAgent(): String = webView.settings.userAgentString


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
                // Inject agent runtime when page DOM reaches interactive ready stage and agent is active
                if (progress >= 85 && _isAgentInteractionEnabled.value) {
                    val currentUrl = view.url
                    if (!AgentWebViewClient.isChallengeUrl(currentUrl)) {
                        agentWebViewClient.injectAgentRuntime(view)
                    }
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

        // Set mobile Chrome User-Agent with real Chrome engine version
        settings.userAgentString = mobileUserAgent

        // Configure CookieManager
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(
            webView,
            browserPreferences.cookiePolicy == CookiePolicy.ALLOW_ALL
        )
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
                desktopUserAgent
            } else {
                mobileUserAgent
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

    private val _isAgentInteractionEnabled = MutableStateFlow(false)
    val isAgentInteractionEnabled: StateFlow<Boolean> = _isAgentInteractionEnabled.asStateFlow()

    override fun setAgentInteractionEnabled(enabled: Boolean) {
        _isAgentInteractionEnabled.value = enabled
        agentWebViewClient.isAgentActive = enabled
        if (enabled) {
            runOnMainThread {
                agentWebViewClient.injectAgentRuntime(webView)
            }
        }
    }

    fun ensureAgentRuntime() {
        runOnMainThread {
            agentWebViewClient.injectAgentRuntime(webView)
        }
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
        const val DEFAULT_CHROME_VERSION = "131.0.0.0"
        const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        fun detectChromeVersion(context: Context?, webView: WebView? = null): String {
            // 1. Query WebViewCompat for the active WebView provider package
            if (context != null) {
                try {
                    val packageInfo = WebViewCompat.getCurrentWebViewPackage(context)
                    val versionName = packageInfo?.versionName
                    if (!versionName.isNullOrBlank()) {
                        return versionName
                    }
                } catch (_: Throwable) {
                    // Fallback to next detection strategy
                }
            }

            // 2. Query default User-Agent from WebSettings
            if (context != null) {
                try {
                    val defaultUa = WebSettings.getDefaultUserAgent(context)
                    if (!defaultUa.isNullOrBlank()) {
                        val match = Regex("""Chrome/([0-9.]+)""").find(defaultUa)
                        val version = match?.groupValues?.get(1)
                        if (!version.isNullOrBlank()) {
                            return version
                        }
                    }
                } catch (_: Throwable) {
                    // Fallback to next detection strategy
                }
            }

            // 3. Query User-Agent from WebView instance
            if (webView != null) {
                try {
                    val ua = webView.settings.userAgentString
                    if (!ua.isNullOrBlank()) {
                        val match = Regex("""Chrome/([0-9.]+)""").find(ua)
                        val version = match?.groupValues?.get(1)
                        if (!version.isNullOrBlank()) {
                            return version
                        }
                    }
                } catch (_: Throwable) {
                    // Fallback to default
                }
            }

            return DEFAULT_CHROME_VERSION
        }

        fun buildMobileUserAgent(chromeVersion: String): String {
            val androidVersion = try {
                Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "14"
            } catch (_: Throwable) {
                "14"
            }
            return "Mozilla/5.0 (Linux; Android $androidVersion; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Mobile Safari/537.36"
        }

        fun buildDesktopUserAgent(chromeVersion: String): String {
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"
        }
    }
}
