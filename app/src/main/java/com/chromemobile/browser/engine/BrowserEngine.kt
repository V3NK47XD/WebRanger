package com.chromemobile.browser.engine

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

data class BrowserState(
    val currentUrl: String = "about:blank",
    val title: String = "",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = false,
    val favicon: Bitmap? = null
)

data class ConsoleMessageEntry(
    val message: String,
    val sourceId: String,
    val lineNumber: Int,
    val level: LogLevel,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class LogLevel {
        DEBUG, LOG, INFO, WARNING, ERROR
    }
}

interface BrowserEngine {
    val state: StateFlow<BrowserState>
    val consoleLogs: StateFlow<List<ConsoleMessageEntry>>

    fun loadUrl(url: String)
    fun reload()
    fun stopLoading()
    fun goBack(): Boolean
    fun goForward(): Boolean

    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null)
    suspend fun evaluateJavascriptAsync(script: String): String?

    fun captureScreenshot(onCaptured: (Bitmap?) -> Unit)
    suspend fun captureScreenshotAsync(): Bitmap?

    fun setAgentInteractionEnabled(enabled: Boolean)
    fun clearConsoleLogs()
    fun destroy()
}
