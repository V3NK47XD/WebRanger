package com.chromemobile.browser.tab

import android.graphics.Bitmap
import com.chromemobile.browser.agent.AgentStepLog
import com.chromemobile.browser.engine.WebViewBrowserEngine
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TabAiContext(
    val lastGoal: String = "",
    val finalAnswer: String? = null,
    val totalTurns: Int = 0,
    val executionLogs: List<AgentStepLog> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val engine: WebViewBrowserEngine,
    val createdAt: Long = System.currentTimeMillis(),
    var previewBitmap: Bitmap? = null,
    var aiContext: TabAiContext? = null
) {
    val hasAiContext: Boolean get() = aiContext != null
}
