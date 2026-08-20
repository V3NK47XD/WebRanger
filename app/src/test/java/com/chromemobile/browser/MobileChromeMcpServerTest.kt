package com.chromemobile.browser

import android.graphics.Bitmap
import com.chromemobile.browser.engine.BrowserEngine
import com.chromemobile.browser.engine.BrowserState
import com.chromemobile.browser.engine.ConsoleMessageEntry
import com.chromemobile.browser.mcp.McpCallToolRequest
import com.chromemobile.browser.mcp.MobileChromeMcpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBrowserEngine : BrowserEngine {
    private val _state = MutableStateFlow(BrowserState(currentUrl = "https://en.wikipedia.org"))
    override val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<ConsoleMessageEntry>>(emptyList())
    override val consoleLogs: StateFlow<List<ConsoleMessageEntry>> = _consoleLogs.asStateFlow()

    var lastLoadedUrl: String? = null
    private var currentZoom: Int = 80

    override fun loadUrl(url: String) {
        lastLoadedUrl = url
        _state.value = _state.value.copy(currentUrl = url)
    }

    override fun reload() {}
    override fun stopLoading() {}
    override fun goBack(): Boolean = true
    override fun goForward(): Boolean = true

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        callback?.invoke("true")
    }

    override suspend fun evaluateJavascriptAsync(script: String): String? {
        return "true"
    }

    override fun captureScreenshot(onCaptured: (Bitmap?) -> Unit) {
        onCaptured(null)
    }

    override suspend fun captureScreenshotAsync(): Bitmap? = null

    override fun setAgentInteractionEnabled(enabled: Boolean) {}
    override fun clearConsoleLogs() {}

    override fun setZoomFactor(percent: Int) {
        currentZoom = percent
    }

    override fun getZoomFactor(): Int = currentZoom

    override fun clearBrowsingData(
        clearHistory: Boolean,
        clearCookies: Boolean,
        clearCache: Boolean,
        clearStorage: Boolean,
        onComplete: (() -> Unit)?
    ) {
        onComplete?.invoke()
    }

    override fun destroy() {}
}

class MobileChromeMcpServerTest {

    @Test
    fun testListToolsIncludesAllCapabilities() {
        val fakeEngine = FakeBrowserEngine()
        val server = MobileChromeMcpServer(browserEngine = fakeEngine)

        val tools = server.listTools()
        val toolNames = tools.map { it.name }

        assertTrue(toolNames.contains("chrome_navigate"))
        assertTrue(toolNames.contains("chrome_get_dom_snapshot"))
        assertTrue(toolNames.contains("chrome_click_element"))
        assertTrue(toolNames.contains("chrome_type_text"))
        assertTrue(toolNames.contains("chrome_scroll"))
        assertTrue(toolNames.contains("chrome_evaluate_script"))
        assertTrue(toolNames.contains("chrome_get_saved_credentials"))
        assertTrue(toolNames.contains("chrome_save_credential"))
        assertTrue(toolNames.contains("chrome_autofill_login"))
        assertTrue(toolNames.contains("chrome_list_tabs"))
        assertTrue(toolNames.contains("chrome_switch_tab"))
        assertTrue(toolNames.contains("chrome_create_tab"))
        assertTrue(toolNames.contains("chrome_close_tab"))
        assertTrue(toolNames.contains("chrome_get_tab_context"))
        assertTrue(toolNames.contains("chrome_finish_task"))
    }

    @Test
    fun testNavigateToolCall() = runBlocking {
        val fakeEngine = FakeBrowserEngine()
        val server = MobileChromeMcpServer(browserEngine = fakeEngine)

        val request = McpCallToolRequest(
            name = "chrome_navigate",
            arguments = mapOf("url" to JsonPrimitive("https://news.ycombinator.com"))
        )

        val response = server.callTool(request)
        assertFalse(response.isError)
        assertEquals("https://news.ycombinator.com", fakeEngine.lastLoadedUrl)
    }

    @Test
    fun testListTabsFallback() = runBlocking {
        val fakeEngine = FakeBrowserEngine()
        val server = MobileChromeMcpServer(browserEngine = fakeEngine)

        val request = McpCallToolRequest(
            name = "chrome_list_tabs",
            arguments = emptyMap()
        )

        val response = server.callTool(request)
        assertFalse(response.isError)
        assertTrue(response.getCombinedText().contains("Open Tabs"))
    }

    @Test
    fun testGetCredentialsWhenPasswordManagerIsNull() = runBlocking {
        val fakeEngine = FakeBrowserEngine()
        val server = MobileChromeMcpServer(browserEngine = fakeEngine, passwordManager = null)

        val request = McpCallToolRequest(
            name = "chrome_get_saved_credentials",
            arguments = mapOf("domain" to JsonPrimitive("github.com"))
        )

        val response = server.callTool(request)
        assertTrue(response.isError)
    }

    @Test
    fun testSaveCredentialMissingRequiredParams() = runBlocking {
        val fakeEngine = FakeBrowserEngine()
        val server = MobileChromeMcpServer(browserEngine = fakeEngine, passwordManager = null)

        val request = McpCallToolRequest(
            name = "chrome_save_credential",
            arguments = mapOf("domain" to JsonPrimitive("example.com"))
        )

        val response = server.callTool(request)
        assertTrue(response.isError)
    }

    @Test
    fun testUnknownToolReturnsError() = runBlocking {
        val fakeEngine = FakeBrowserEngine()
        val server = MobileChromeMcpServer(browserEngine = fakeEngine)

        val request = McpCallToolRequest(
            name = "unknown_tool",
            arguments = emptyMap()
        )

        val response = server.callTool(request)
        assertTrue(response.isError)
    }
}
