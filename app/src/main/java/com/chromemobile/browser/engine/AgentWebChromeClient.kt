package com.chromemobile.browser.engine

import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class AgentWebChromeClient(
    private val browserStateFlow: MutableStateFlow<BrowserState>,
    private val consoleLogsFlow: MutableStateFlow<List<ConsoleMessageEntry>>,
    private val onProgressChange: ((WebView, Int) -> Unit)? = null
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        browserStateFlow.update {
            it.copy(
                progress = newProgress,
                isLoading = newProgress < 100
            )
        }
        view?.let {
            onProgressChange?.invoke(it, newProgress)
        }
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        browserStateFlow.update {
            it.copy(title = title ?: "")
        }
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        browserStateFlow.update {
            it.copy(favicon = icon)
        }
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        if (consoleMessage != null) {
            val messageText = consoleMessage.message() ?: ""

            // Filter out benign feature-policy warnings from third-party iframes (e.g. YouTube web-share, attribution-reporting)
            if (messageText.contains("unrecognized feature", ignoreCase = true) &&
                (messageText.contains("web-share", ignoreCase = true) || messageText.contains("attribution-reporting", ignoreCase = true))
            ) {
                return true
            }

            val level = when (consoleMessage.messageLevel()) {
                ConsoleMessage.MessageLevel.DEBUG -> ConsoleMessageEntry.LogLevel.DEBUG
                ConsoleMessage.MessageLevel.LOG -> ConsoleMessageEntry.LogLevel.LOG
                ConsoleMessage.MessageLevel.TIP -> ConsoleMessageEntry.LogLevel.INFO
                ConsoleMessage.MessageLevel.WARNING -> ConsoleMessageEntry.LogLevel.WARNING
                ConsoleMessage.MessageLevel.ERROR -> ConsoleMessageEntry.LogLevel.ERROR
                else -> ConsoleMessageEntry.LogLevel.LOG
            }

            val entry = ConsoleMessageEntry(
                message = messageText,
                sourceId = consoleMessage.sourceId() ?: "",
                lineNumber = consoleMessage.lineNumber(),
                level = level
            )

            consoleLogsFlow.update { currentList ->
                // Keep the last 200 console logs
                (currentList + entry).takeLast(200)
            }
        }
        return true
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        // Auto-accept alerts during automated agent execution to avoid blocking
        result?.confirm()
        return true
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        result?.confirm()
        return true
    }

    override fun onJsPrompt(
        view: WebView?,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult?
    ): Boolean {
        result?.confirm(defaultValue ?: "")
        return true
    }
}
