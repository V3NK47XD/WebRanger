package com.chromemobile.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
    val webView: WebView = WebView(context)
) : BrowserEngine {

    private val _state = MutableStateFlow(BrowserState())
    override val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<ConsoleMessageEntry>>(emptyList())
    override val consoleLogs: StateFlow<List<ConsoleMessageEntry>> = _consoleLogs.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val agentWebViewClient: AgentWebViewClient
    private val agentWebChromeClient: AgentWebChromeClient

    init {
        configureWebSettings()

        agentWebViewClient = AgentWebViewClient(
            context = context,
            browserStateFlow = _state
        )

        agentWebChromeClient = AgentWebChromeClient(
            browserStateFlow = _state,
            consoleLogsFlow = _consoleLogs,
            onProgressChange = { view, progress ->
                // Proactively inject agent runtime at early interactive stage (>=70%)
                if (progress >= 70) {
                    agentWebViewClient.injectAgentRuntime(view)
                }
            }
        )

        webView.webViewClient = agentWebViewClient
        webView.webChromeClient = agentWebChromeClient
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
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = true

        // Emulate modern Chrome on Android User-Agent
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 ChromeMobileAI/1.0"
    }

    override fun loadUrl(url: String) {
        val formattedUrl = when {
            url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:") -> url
            url.contains(".") && !url.contains(" ") -> "https://$url"
            else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(url, "UTF-8")
        }

        runOnMainThread {
            webView.loadUrl(formattedUrl)
        }
    }

    override fun reload() {
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
        if (webView.canGoBack()) {
            runOnMainThread {
                webView.goBack()
            }
            return true
        }
        return false
    }

    override fun goForward(): Boolean {
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
            SnapshotProvider.captureWebView(webView, onCaptured)
        }
    }

    override suspend fun captureScreenshotAsync(): Bitmap? = withContext(Dispatchers.Main) {
        suspendCoroutine { continuation ->
            SnapshotProvider.captureWebView(webView) { bitmap ->
                continuation.resume(bitmap)
            }
        }
    }

    /**
     * Dispatch real hardware MotionEvent tap onto WebView surface at CSS coordinates
     */
    fun dispatchNativeTap(cssX: Float, cssY: Float) {
        runOnMainThread {
            try {
                val density = webView.context.resources.displayMetrics.density
                val screenX = cssX * density
                val screenY = cssY * density
                val downTime = SystemClock.uptimeMillis()
                val eventTime = SystemClock.uptimeMillis()

                val downEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, screenX, screenY, 0)
                val upEvent = MotionEvent.obtain(downTime, eventTime + 40, MotionEvent.ACTION_UP, screenX, screenY, 0)

                webView.dispatchTouchEvent(downEvent)
                webView.dispatchTouchEvent(upEvent)

                downEvent.recycle()
                upEvent.recycle()
            } catch (e: Exception) {
                // Ignore native tap dispatch errors
            }
        }
    }

    override fun setAgentInteractionEnabled(enabled: Boolean) {
        val command = if (enabled) {
            "window.__mobileAgent && window.__mobileAgent.showElementBadges && window.__mobileAgent.showElementBadges();"
        } else {
            "window.__mobileAgent && window.__mobileAgent.clearElementBadges && window.__mobileAgent.clearElementBadges();"
        }
        evaluateJavascript(command, null)
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
}
