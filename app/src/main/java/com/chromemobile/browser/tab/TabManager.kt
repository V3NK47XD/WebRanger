package com.chromemobile.browser.tab

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.chromemobile.browser.agent.AgentStepLog
import com.chromemobile.browser.engine.WebViewBrowserEngine
import com.chromemobile.browser.preferences.BrowserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class TabManager(
    private val context: Context,
    private val browserPreferences: BrowserPreferences
) {

    private val lock = ReentrantReadWriteLock()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow("")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    init {
        // Initialize with initial tab
        createTab(url = "about:blank", selectImmediately = true)
    }

    private fun <T> runOnMainThreadSync(action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return action()
        }
        val future = CompletableFuture<T>()
        mainHandler.post {
            try {
                future.complete(action())
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }
        return future.get()
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
        return runOnMainThreadSync {
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
            newTab
        }
    }

    fun selectTab(tabId: String) {
        runOnMainThreadSync {
            lock.write {
                val exists = _tabs.value.any { it.id == tabId }
                if (exists) {
                    captureActiveTabThumbnail()
                    _activeTabId.value = tabId
                }
            }
        }
    }

    fun closeTab(tabId: String): Boolean {
        return runOnMainThreadSync {
            var closed = false
            lock.write {
                val currentList = _tabs.value
                val indexToClose = currentList.indexOfFirst { it.id == tabId }
                if (indexToClose >= 0) {
                    val tabToClose = currentList[indexToClose]
                    val remaining = currentList.toMutableList()
                    remaining.removeAt(indexToClose)

                    // Safely detach from parent view before destroying
                    (tabToClose.engine.webView.parent as? ViewGroup)?.removeView(tabToClose.engine.webView)
                    tabToClose.engine.destroy()
                    closed = true

                    if (remaining.isEmpty()) {
                        _tabs.value = emptyList()
                        createTab(url = "about:blank", selectImmediately = true)
                    } else {
                        _tabs.value = remaining
                        if (_activeTabId.value == tabId) {
                            val newIndex = (indexToClose - 1).coerceAtLeast(0)
                            _activeTabId.value = remaining[newIndex].id
                        }
                    }
                }
            }
            closed
        }
    }

    fun closeAllTabs() {
        runOnMainThreadSync {
            lock.write {
                _tabs.value.forEach { tab ->
                    (tab.engine.webView.parent as? ViewGroup)?.removeView(tab.engine.webView)
                    tab.engine.destroy()
                }
                _tabs.value = emptyList()
                createTab(url = "about:blank", selectImmediately = true)
            }
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
            _tabs.update { it.toList() }
        }
    }

    fun captureActiveTabThumbnail(onCaptured: ((Bitmap?) -> Unit)? = null) {
        runOnMainThreadSync {
            val currentTab = getActiveTab()
            currentTab.engine.captureScreenshot { bitmap ->
                if (bitmap != null) {
                    currentTab.previewBitmap = bitmap
                    _tabs.update { it.toList() }
                }
                onCaptured?.invoke(bitmap)
            }
        }
    }

    fun destroyAll() {
        runOnMainThreadSync {
            lock.write {
                _tabs.value.forEach { tab ->
                    (tab.engine.webView.parent as? ViewGroup)?.removeView(tab.engine.webView)
                    tab.engine.destroy()
                }
                _tabs.value = emptyList()
            }
        }
    }
}
