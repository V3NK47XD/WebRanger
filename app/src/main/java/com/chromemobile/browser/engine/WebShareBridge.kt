package com.chromemobile.browser.engine

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface

class WebShareBridge(private val context: Context) {

    @JavascriptInterface
    fun share(title: String?, text: String?, url: String?): Boolean {
        return try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                val combinedText = buildString {
                    if (!title.isNullOrBlank()) append(title).append("\n")
                    if (!text.isNullOrBlank()) append(text).append("\n")
                    if (!url.isNullOrBlank()) append(url)
                }.trim()
                putExtra(Intent.EXTRA_TEXT, combinedText)
                if (!title.isNullOrBlank()) {
                    putExtra(Intent.EXTRA_SUBJECT, title)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, title ?: "Share").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun canShare(): Boolean = true
}
