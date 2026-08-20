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
                    } catch (e) {
                        console.error('[MobileAgent] Runtime injection error:', e);
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

        view?.let {
            injectAgentRuntime(it)
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
}
