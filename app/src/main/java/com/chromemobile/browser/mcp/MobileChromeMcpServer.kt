package com.chromemobile.browser.mcp

import android.graphics.Bitmap
import android.util.Base64
import com.chromemobile.browser.agent.DomSnapshotResponse
import com.chromemobile.browser.agent.SecurityGuard
import com.chromemobile.browser.engine.BrowserEngine
import com.chromemobile.browser.engine.WebViewBrowserEngine
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream

/**
 * In-App Mobile Chrome DevTools MCP Server
 * Standardized Model Context Protocol tool provider for autonomous mobile browser automation.
 */
class MobileChromeMcpServer(
    private val browserEngine: BrowserEngine,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    private var latestSnapshot: DomSnapshotResponse? = null

    /**
     * Expose standard Chrome DevTools MCP tool catalog
     */
    fun listTools(): List<McpTool> {
        return listOf(
            McpTool(
                name = "chrome_navigate",
                description = "Navigate the mobile browser to a destination URL",
                properties = mapOf(
                    "url" to McpProperty("string", "The URL to navigate to (e.g. 'https://en.wikipedia.org')")
                ),
                required = listOf("url")
            ),
            McpTool(
                name = "chrome_get_dom_snapshot",
                description = "Extract current page semantic accessibility tree and visible interactive element index",
                properties = mapOf(
                    "viewport_only" to McpProperty("boolean", "Whether to extract only elements inside the active viewport (default: true)")
                ),
                required = emptyList()
            ),
            McpTool(
                name = "chrome_click_element",
                description = "Perform a mobile touch tap/click on an interactive element by its numeric badge ID",
                properties = mapOf(
                    "element_id" to McpProperty("integer", "Numeric element ID from the DOM snapshot to tap/click")
                ),
                required = listOf("element_id")
            ),
            McpTool(
                name = "chrome_type_text",
                description = "Type text into an input field or textarea identified by its element ID",
                properties = mapOf(
                    "element_id" to McpProperty("integer", "Numeric ID of the input element"),
                    "text" to McpProperty("string", "Text string to type into the field"),
                    "clear_first" to McpProperty("boolean", "Whether to clear existing text in the input before typing"),
                    "press_enter" to McpProperty("boolean", "Whether to simulate pressing Enter/Submit after typing")
                ),
                required = listOf("element_id", "text")
            ),
            McpTool(
                name = "chrome_scroll",
                description = "Scroll the mobile viewport up, down, to the top, or to the bottom",
                properties = mapOf(
                    "direction" to McpProperty("string", "Scroll direction", enumValues = listOf("up", "down", "top", "bottom")),
                    "amount" to McpProperty("integer", "Pixel scroll delta amount (optional)")
                ),
                required = listOf("direction")
            ),
            McpTool(
                name = "chrome_evaluate_script",
                description = "Evaluate arbitrary JavaScript in the webpage context and return the result",
                properties = mapOf(
                    "script" to McpProperty("string", "JavaScript code to execute")
                ),
                required = listOf("script")
            ),
            McpTool(
                name = "chrome_wait",
                description = "Wait for asynchronous DOM mutations, route changes, or dynamic content to settle",
                properties = mapOf(
                    "timeout_ms" to McpProperty("integer", "Max wait duration in milliseconds (default: 2500)"),
                    "debounce_ms" to McpProperty("integer", "Quiet duration to consider stable (default: 300)")
                ),
                required = emptyList()
            ),
            McpTool(
                name = "chrome_take_screenshot",
                description = "Capture a visual screenshot image of the active mobile viewport",
                properties = emptyMap(),
                required = emptyList()
            ),
            McpTool(
                name = "chrome_go_back",
                description = "Navigate back to the previous page in history",
                properties = emptyMap(),
                required = emptyList()
            ),
            McpTool(
                name = "chrome_finish_task",
                description = "Finish the agent execution loop and provide the final answer or summary to the user",
                properties = mapOf(
                    "answer" to McpProperty("string", "Final answer, extracted summary, or completion report"),
                    "success" to McpProperty("boolean", "Whether the objective was accomplished successfully")
                ),
                required = listOf("answer")
            )
        )
    }

    /**
     * Dispatch an MCP tool call request with flexible tool naming and argument parsing
     */
    suspend fun callTool(request: McpCallToolRequest): McpCallToolResponse {
        val toolName = request.name.lowercase().trim()
        return try {
            when {
                toolName.contains("navigate") || toolName == "open_url" -> handleNavigate(request)
                toolName.contains("snapshot") || toolName.contains("dom") -> handleGetDomSnapshot(request)
                toolName.contains("click") || toolName.contains("tap") -> handleClickElement(request)
                toolName.contains("type") || toolName.contains("fill") || toolName.contains("input") -> handleTypeText(request)
                toolName.contains("scroll") -> handleScroll(request)
                toolName.contains("evaluate") || toolName.contains("console") || toolName.contains("script") -> handleEvaluateScript(request)
                toolName.contains("wait") -> handleWait(request)
                toolName.contains("screenshot") -> handleTakeScreenshot(request)
                toolName.contains("back") -> handleGoBack(request)
                toolName.contains("finish") || toolName.contains("done") || toolName.contains("complete") -> handleFinishTask(request)
                else -> McpCallToolResponse(
                    content = listOf(McpContent(type = "text", text = "Error: Unknown MCP tool '${request.name}'")),
                    isError = true
                )
            }
        } catch (e: Exception) {
            McpCallToolResponse(
                content = listOf(McpContent(type = "text", text = "Tool execution error: ${e.message}")),
                isError = true
            )
        }
    }

    private suspend fun handleNavigate(request: McpCallToolRequest): McpCallToolResponse {
        val url = request.arguments["url"]?.jsonPrimitive?.content
            ?: request.arguments["target"]?.jsonPrimitive?.content
            ?: return errorResponse("Missing required 'url' parameter")

        browserEngine.loadUrl(url)
        delay(1200)

        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "Navigated to $url"))
        )
    }

    suspend fun fetchLatestDomSnapshot(viewportOnly: Boolean = true): DomSnapshotResponse? {
        val script = """
            (function() {
                if (window.__mobileAgent && window.__mobileAgent.getDOMSnapshot) {
                    return JSON.stringify(window.__mobileAgent.getDOMSnapshot({ viewportOnly: $viewportOnly }));
                }
                return null;
            })();
        """.trimIndent()

        var rawResult = browserEngine.evaluateJavascriptAsync(script)
        
        // If runtime not injected yet, wait briefly and retry once
        if (rawResult.isNullOrEmpty() || rawResult == "null" || rawResult == "undefined") {
            delay(400)
            rawResult = browserEngine.evaluateJavascriptAsync(script)
        }

        if (rawResult.isNullOrEmpty() || rawResult == "null" || rawResult == "undefined") {
            return null
        }

        val cleanedJson = if (rawResult.startsWith("\"") && rawResult.endsWith("\"")) {
            json.decodeFromString<String>(rawResult)
        } else {
            rawResult
        }

        return try {
            val parsed = json.decodeFromString<DomSnapshotResponse>(cleanedJson)
            latestSnapshot = parsed
            parsed
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun handleGetDomSnapshot(request: McpCallToolRequest): McpCallToolResponse {
        val viewportOnly = request.arguments["viewport_only"]?.jsonPrimitive?.booleanOrNull ?: true
        val snapshot = fetchLatestDomSnapshot(viewportOnly)

        return if (snapshot != null) {
            val sanitizedText = SecurityGuard.sanitizeSnapshotText(snapshot.treeText)
            McpCallToolResponse(
                content = listOf(McpContent(type = "text", text = sanitizedText))
            )
        } else {
            errorResponse("Failed to extract DOM snapshot: agent runtime not ready on current page")
        }
    }

    private suspend fun handleClickElement(request: McpCallToolRequest): McpCallToolResponse {
        // Robust ID parsing: accept integer or string
        val elementId = request.arguments["element_id"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: request.arguments["id"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: request.arguments["elementId"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: return errorResponse("Missing required 'element_id' parameter")

        val script = """
            (function() {
                if (window.__mobileAgent && window.__mobileAgent.interact) {
                    return JSON.stringify(window.__mobileAgent.interact('click', { id: $elementId }));
                }
                return JSON.stringify({ success: false, error: 'Runtime not initialized' });
            })();
        """.trimIndent()

        val rawResult = browserEngine.evaluateJavascriptAsync(script)

        // Try parsing coordinates to also fire a native physical tap on the WebView
        try {
            if (!rawResult.isNullOrEmpty() && rawResult != "null") {
                val cleaned = if (rawResult.startsWith("\"") && rawResult.endsWith("\"")) {
                    json.decodeFromString<String>(rawResult)
                } else rawResult

                val obj = json.parseToJsonElement(cleaned).jsonObject
                val coordsObj = obj["coords"]?.jsonObject
                val x = coordsObj?.get("x")?.jsonPrimitive?.floatOrNull
                val y = coordsObj?.get("y")?.jsonPrimitive?.floatOrNull

                if (x != null && y != null) {
                    (browserEngine as? WebViewBrowserEngine)?.dispatchNativeTap(x, y)
                }
            }
        } catch (e: Exception) {
            // Ignore coordinate parse issues
        }

        delay(400)

        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "Tapped element #$elementId. Result: $rawResult")),
            highlightedElementId = elementId
        )
    }

    private suspend fun handleTypeText(request: McpCallToolRequest): McpCallToolResponse {
        val elementId = request.arguments["element_id"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: request.arguments["id"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: request.arguments["elementId"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: return errorResponse("Missing required 'element_id' parameter")

        val text = request.arguments["text"]?.jsonPrimitive?.content
            ?: request.arguments["value"]?.jsonPrimitive?.content
            ?: return errorResponse("Missing required 'text' parameter")

        val clearFirst = request.arguments["clear_first"]?.jsonPrimitive?.booleanOrNull ?: false
        val pressEnter = request.arguments["press_enter"]?.jsonPrimitive?.booleanOrNull ?: true // Default true for searches

        val escapedText = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

        val script = """
            (function() {
                if (window.__mobileAgent && window.__mobileAgent.interact) {
                    return JSON.stringify(window.__mobileAgent.interact('type', {
                        id: $elementId,
                        text: "$escapedText",
                        clearFirst: $clearFirst,
                        pressEnter: $pressEnter
                    }));
                }
                return JSON.stringify({ success: false, error: 'Runtime not initialized' });
            })();
        """.trimIndent()

        val rawResult = browserEngine.evaluateJavascriptAsync(script)
        delay(400)

        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "Typed into element #$elementId. Result: $rawResult")),
            highlightedElementId = elementId
        )
    }

    private suspend fun handleScroll(request: McpCallToolRequest): McpCallToolResponse {
        val direction = request.arguments["direction"]?.jsonPrimitive?.content ?: "down"
        val amount = request.arguments["amount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 600

        val script = """
            (function() {
                if (window.__mobileAgent && window.__mobileAgent.interact) {
                    return JSON.stringify(window.__mobileAgent.interact('scroll', {
                        direction: '$direction',
                        amount: $amount
                    }));
                }
                return JSON.stringify({ success: false, error: 'Runtime not initialized' });
            })();
        """.trimIndent()

        val rawResult = browserEngine.evaluateJavascriptAsync(script)
        delay(400)

        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "Scrolled $direction by $amount px. Result: $rawResult"))
        )
    }

    private suspend fun handleEvaluateScript(request: McpCallToolRequest): McpCallToolResponse {
        val scriptCode = request.arguments["script"]?.jsonPrimitive?.content
            ?: request.arguments["code"]?.jsonPrimitive?.content
            ?: request.arguments["js_code"]?.jsonPrimitive?.content
            ?: return errorResponse("Missing required 'script' parameter")

        val escapedCode = scriptCode.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

        val script = """
            (function() {
                if (window.__mobileAgent && window.__mobileAgent.executeConsole) {
                    return JSON.stringify(window.__mobileAgent.executeConsole("$escapedCode"));
                }
                return JSON.stringify({ success: false, error: 'Runtime not initialized' });
            })();
        """.trimIndent()

        val rawResult = browserEngine.evaluateJavascriptAsync(script)

        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "Script output: $rawResult"))
        )
    }

    private suspend fun handleWait(request: McpCallToolRequest): McpCallToolResponse {
        val timeoutMs = request.arguments["timeout_ms"]?.jsonPrimitive?.content?.toIntOrNull() ?: 2500
        val debounceMs = request.arguments["debounce_ms"]?.jsonPrimitive?.content?.toIntOrNull() ?: 300

        val script = """
            (function() {
                if (window.__mobileAgent && window.__mobileAgent.waitForStableDOM) {
                    return window.__mobileAgent.waitForStableDOM($timeoutMs, $debounceMs);
                }
                return Promise.resolve({ stable: true });
            })();
        """.trimIndent()

        browserEngine.evaluateJavascriptAsync(script)
        delay(debounceMs.toLong() + 100)

        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "DOM stabilized"))
        )
    }

    private suspend fun handleTakeScreenshot(request: McpCallToolRequest): McpCallToolResponse {
        val bitmap = browserEngine.captureScreenshotAsync()
        return if (bitmap != null) {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            McpCallToolResponse(
                content = listOf(
                    McpContent(type = "image", data = base64, mimeType = "image/jpeg"),
                    McpContent(type = "text", text = "Screenshot captured (${bitmap.width}x${bitmap.height}px)")
                )
            )
        } else {
            errorResponse("Failed to capture screen bitmap")
        }
    }

    private fun handleGoBack(request: McpCallToolRequest): McpCallToolResponse {
        val canGoBack = browserEngine.goBack()
        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = if (canGoBack) "Navigated back" else "No previous page"))
        )
    }

    private fun handleFinishTask(request: McpCallToolRequest): McpCallToolResponse {
        val answer = request.arguments["answer"]?.jsonPrimitive?.content
            ?: request.arguments["summary"]?.jsonPrimitive?.content
            ?: request.arguments["result"]?.jsonPrimitive?.content
            ?: "Task complete."
        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = answer))
        )
    }

    private fun errorResponse(message: String): McpCallToolResponse {
        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "Error: $message")),
            isError = true
        )
    }
}
