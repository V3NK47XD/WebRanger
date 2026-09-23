package com.chromemobile.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.BufferedReader
import java.io.InputStreamReader

class AgentWebViewClient(
    private val context: Context,
    private val browserStateFlow: MutableStateFlow<BrowserState>,
    private val onPageFinishedCallback: ((String) -> Unit)? = null
) : WebViewClient() {

    var isAgentActive: Boolean = false

    private var cachedAgentScript: String? = null

    init {
        loadAgentScript()
    }

    private fun loadAgentScript() {
        try {
            val inputStream = context.assets.open("agent_runtime.js")
            val reader = BufferedReader(InputStreamReader(inputStream))
            cachedAgentScript = reader.readText()
            reader.close()
            inputStream.close()
        } catch (e: Exception) {
            // Failed to load agent runtime asset
        }
    }

    fun injectAgentRuntime(webView: WebView) {
        val script = cachedAgentScript
        if (!script.isNullOrEmpty()) {
            webView.evaluateJavascript(
                """
                (function() {
                    try {
                        if (!window.__mobileAgent) {
                            $script
                        }
                    } catch (_: Exception) {
                    }
                })();
                """.trimIndent(),
                null
            )
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        val validUrl = url ?: "about:blank"
        browserStateFlow.update {
            it.copy(
                currentUrl = validUrl,
                isLoading = true,
                isSecure = validUrl.startsWith("https://"),
                favicon = favicon,
                canGoBack = view?.canGoBack() ?: false,
                canGoForward = view?.canGoForward() ?: false
            )
        }

        view?.let {
            injectBrowserEnvironmentPolyfills(it)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val validUrl = url ?: "about:blank"
        browserStateFlow.update {
            it.copy(
                currentUrl = validUrl,
                title = view?.title ?: "",
                isLoading = false,
                isSecure = validUrl.startsWith("https://"),
                canGoBack = view?.canGoBack() ?: false,
                canGoForward = view?.canGoForward() ?: false
            )
        }

        if (isAgentActive && !isChallengeUrl(validUrl)) {
            view?.let {
                injectAgentRuntime(it)
            }
        }

        onPageFinishedCallback?.invoke(validUrl)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            browserStateFlow.update {
                it.copy(isLoading = false)
            }
        }
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
    ) {
        // Proceed gracefully without dropping main page rendering into blank state
        handler?.proceed()
        browserStateFlow.update {
            it.copy(isSecure = false)
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:") || url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("javascript:")) {
            return false // Let WebView handle standard web links and internal redirects
        }
        return true // Block unsupported third-party intent schemes
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        // Handle renderer crash recovery to prevent persistent blank white screen
        view?.let { webView ->
            val parent = webView.parent as? ViewGroup
            parent?.removeView(webView)
            webView.destroy()
        }
        browserStateFlow.update {
            it.copy(isLoading = false)
        }
        return true
    }

    private fun injectBrowserEnvironmentPolyfills(webView: WebView) {
        val polyfillScript = """
            (function() {
                try {
                    // Standard window.chrome stub expected by bot detection and web apps
                    if (!window.chrome) {
                        Object.defineProperty(window, 'chrome', {
                            value: {
                                app: {
                                    isInstalled: false,
                                    InstallState: { DISABLED: 'disabled', INSTALLED: 'installed', NOT_INSTALLED: 'not_installed' },
                                    RunningState: { CANNOT_RUN: 'cannot_run', READY_TO_RUN: 'ready_to_run', RUNNING: 'running' }
                                },
                                csi: function() {},
                                loadTimes: function() {}
                            },
                            writable: true,
                            configurable: true,
                            enumerable: false
                        });
                    }

                    // Normalize Client Hints brands to eliminate "Android WebView" detection token
                    if (navigator.userAgentData) {
                        const ua = navigator.userAgent || '';
                        const chromeMatch = ua.match(/Chrome\/(\d+)/);
                        const majorVer = chromeMatch ? chromeMatch[1] : '134';
                        const realBrands = [
                            { brand: 'Chromium', version: majorVer },
                            { brand: 'Google Chrome', version: majorVer },
                            { brand: 'Not(A:Brand', version: '24' }
                        ];
                        try {
                            Object.defineProperty(navigator.userAgentData, 'brands', {
                                get: function() { return realBrands; },
                                configurable: true,
                                enumerable: true
                            });
                        } catch (_) {}
                    }
                } catch (_) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(polyfillScript, null)
    }

    companion object {
        fun isChallengeUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val lower = url.lowercase()
            return lower.contains("challenges.cloudflare.com") ||
                   lower.contains("cdn-cgi/challenge-platform") ||
                   lower.contains("turnstile") ||
                   lower.contains("hcaptcha.com") ||
                   lower.contains("recaptcha") ||
                   lower.contains("arkoselabs")
        }
    }
}
