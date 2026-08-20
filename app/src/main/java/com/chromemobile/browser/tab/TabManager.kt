package com.chromemobile.browser.tab

import android.content.Context
import android.graphics.Bitmap
import com.chromemobile.browser.agent.AgentStepLog
import com.chromemobile.browser.engine.WebViewBrowserEngine
import com.chromemobile.browser.preferences.BrowserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class TabManager(
    private val context: Context,
    private val browserPreferences: BrowserPreferences
) {

    private val lock = ReentrantReadWriteLock()
    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow("")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    init {
        // Initialize with initial tab
        createTab(url = "about:blank", selectImmediately = true)
    }

    fun getActiveTab(): BrowserTab {
        return lock.read {
            val currentId = _activeTabId.value
            _tabs.value.find { it.id == currentId } ?: _tabs.value.first()
        }
    }

    fun getTabById(tabId: String): BrowserTab? {
        return lock.read {
            _tabs.value.find { it.id == tabId }
        }
    }

    fun createTab(url: String = "about:blank", selectImmediately: Boolean = true): BrowserTab {
        val newEngine = WebViewBrowserEngine(
            context = context,
            browserPreferences = browserPreferences
        )
        if (url.isNotBlank() && url != "about:blank") {
            newEngine.loadUrl(url)
        }

        val newTab = BrowserTab(
            engine = newEngine
        )

        lock.write {
            _tabs.update { it + newTab }
            if (selectImmediately || _activeTabId.value.isEmpty()) {
                _activeTabId.value = newTab.id
            }
        }
        return newTab
    }

    fun selectTab(tabId: String) {
        lock.write {
            val exists = _tabs.value.any { it.id == tabId }
            if (exists) {
                // Capture preview of the tab being switched away from
                captureActiveTabThumbnail()
                _activeTabId.value = tabId
            }
        }
    }

    fun closeTab(tabId: String): Boolean {
        var closed = false
        lock.write {
            val currentList = _tabs.value
            val indexToClose = currentList.indexOfFirst { it.id == tabId }
            if (indexToClose >= 0) {
                val tabToClose = currentList[indexToClose]
                val remaining = currentList.toMutableList()
                remaining.removeAt(indexToClose)

                // Clean up engine resources
                tabToClose.engine.destroy()
                closed = true

                if (remaining.isEmpty()) {
                    // If all tabs closed, create a fresh home tab
                    _tabs.value = emptyList()
                    val freshTab = createTab(url = "about:blank", selectImmediately = true)
                } else {
                    _tabs.value = remaining
                    if (_activeTabId.value == tabId) {
                        val newIndex = (indexToClose - 1).coerceAtLeast(0)
                        _activeTabId.value = remaining[newIndex].id
                    }
                }
            }
        }
        return closed
    }

    fun closeAllTabs() {
        lock.write {
            _tabs.value.forEach { it.engine.destroy() }
            _tabs.value = emptyList()
            createTab(url = "about:blank", selectImmediately = true)
        }
    }

    fun recordAiInvocation(
        tabId: String,
        goal: String,
        answer: String?,
        logs: List<AgentStepLog>
    ) {
        lock.write {
            val targetTab = _tabs.value.find { it.id == tabId } ?: return@write
            val newContext = TabAiContext(
                lastGoal = goal,
                finalAnswer = answer,
                totalTurns = logs.mapNotNull { it.turnNumber }.maxOrNull() ?: 1,
                executionLogs = logs,
                lastUpdated = System.currentTimeMillis()
            )
            targetTab.aiContext = newContext
            // Trigger state flow update so UI observers re-render
            _tabs.update { it.toList() }
        }
    }

    fun captureActiveTabThumbnail(onCaptured: ((Bitmap?) -> Unit)? = null) {
        val currentTab = getActiveTab()
        currentTab.engine.captureScreenshot { bitmap ->
            if (bitmap != null) {
                currentTab.previewBitmap = bitmap
                _tabs.update { it.toList() }
            }
            onCaptured?.invoke(bitmap)
        }
    }

    fun destroyAll() {
        lock.write {
            _tabs.value.forEach { it.engine.destroy() }
            _tabs.value = emptyList()
        }
    }
}
