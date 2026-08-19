package com.chromemobile.browser.agent

import android.graphics.Bitmap
import android.util.Base64
import com.chromemobile.browser.engine.BrowserEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream

class AgentToolExecutor(
    private val browserEngine: BrowserEngine,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    private var latestSnapshot: DomSnapshotResponse? = null

    suspend fun executeTool(toolCall: AgentToolCall): AgentToolResult {
        return try {
            when (toolCall.name) {
                "navigate" -> executeNavigate(toolCall)
                "get_dom_snapshot" -> executeGetDomSnapshot(toolCall)
                "take_screenshot" -> executeTakeScreenshot(toolCall)
                "click_element" -> executeClickElement(toolCall)
                "type_text" -> executeTypeText(toolCall)
                "scroll_page" -> executeScrollPage(toolCall)
                "execute_console" -> executeConsole(toolCall)
                "wait_for_condition" -> executeWaitForCondition(toolCall)
                "go_back" -> executeGoBack(toolCall)
                "finish_task" -> executeFinishTask(toolCall)
                else -> AgentToolResult(
                    toolCallId = toolCall.toolCallId,
                    name = toolCall.name,
                    success = false,
                    output = "Unknown tool: ${toolCall.name}",
                    error = "Tool not found"
                )
            }
        } catch (e: Exception) {
            AgentToolResult(
                toolCallId = toolCall.toolCallId,
                name = toolCall.name,
                success = false,
                output = "Execution failed: ${e.message}",
                error = e.localizedMessage ?: e.toString()
            )
        }
    }

    private suspend fun executeNavigate(call: AgentToolCall): AgentToolResult {
        val url = call.arguments["url"]?.jsonPrimitive?.content
            ?: return errorResult(call, "Missing required parameter 'url'")

        browserEngine.loadUrl(url)
        // Give the page initial time to start loading
        delay(1200)

        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Successfully requested navigation to $url"
        )
    }

    suspend fun fetchDomSnapshot(viewportOnly: Boolean = true): DomSnapshotResponse? {
        val script = """
            (function() {
                if (window.__mobileAgent && window.__mobileAgent.getDOMSnapshot) {
                    return JSON.stringify(window.__mobileAgent.getDOMSnapshot({ viewportOnly: $viewportOnly }));
                }
                return null;
            })();
        """.trimIndent()

        val rawResult = browserEngine.evaluateJavascriptAsync(script)
        if (rawResult == null || rawResult == "null" || rawResult == "undefined") {
            return null
        }

        // Result from evaluateJavascript is JSON-encoded string
        val cleanedJson = if (rawResult.startsWith("\"") && rawResult.endsWith("\"")) {
            json.decodeFromString<String>(rawResult)
        } else {
            rawResult
        }

        val parsed = json.decodeFromString<DomSnapshotResponse>(cleanedJson)
        latestSnapshot = parsed
        return parsed
    }

    private suspend fun executeGetDomSnapshot(call: AgentToolCall): AgentToolResult {
        val viewportOnly = call.arguments["viewport_only"]?.jsonPrimitive?.booleanOrNull ?: true
        val snapshot = fetchDomSnapshot(viewportOnly)

        return if (snapshot != null) {
            AgentToolResult(
                toolCallId = call.toolCallId,
                name = call.name,
                success = true,
                output = snapshot.treeText
            )
        } else {
            AgentToolResult(
                toolCallId = call.toolCallId,
                name = call.name,
                success = false,
                output = "Failed to extract DOM snapshot: agent runtime not ready",
                error = "Runtime not ready"
            )
        }
    }

    private suspend fun executeTakeScreenshot(call: AgentToolCall): AgentToolResult {
        val bitmap = browserEngine.captureScreenshotAsync()
        return if (bitmap != null) {
            val base64 = bitmapToBase64(bitmap)
            AgentToolResult(
                toolCallId = call.toolCallId,
                name = call.name,
                success = true,
                output = "Screenshot captured successfully (${bitmap.width}x${bitmap.height}px, base64 length: ${base64.length})"
            )
        } else {
            errorResult(call, "Failed to capture screen bitmap")
        }
    }

    private suspend fun executeClickElement(call: AgentToolCall): AgentToolResult {
        val elementId = call.arguments["element_id"]?.jsonPrimitive?.intOrNull
            ?: return errorResult(call, "Missing required parameter 'element_id'")

        val script = """
            (function() {
                if (window.__mobileAgent && window.__mobileAgent.interact) {
                    return JSON.stringify(window.__mobileAgent.interact('click', { id: $elementId }));
                }
                return JSON.stringify({ success: false, error: 'Runtime not initialized' });
            })();
        """.trimIndent()

        val rawResult = browserEngine.evaluateJavascriptAsync(script)
        // Wait a short moment for DOM update or navigation
        delay(400)

        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Tapped element #$elementId. Interaction result: $rawResult",
            highlightedElementId = elementId
        )
    }

    private suspend fun executeTypeText(call: AgentToolCall): AgentToolResult {
        val elementId = call.arguments["element_id"]?.jsonPrimitive?.intOrNull
            ?: return errorResult(call, "Missing required parameter 'element_id'")
        val text = call.arguments["text"]?.jsonPrimitive?.content
            ?: return errorResult(call, "Missing required parameter 'text'")
        val clearFirst = call.arguments["clear_first"]?.jsonPrimitive?.booleanOrNull ?: false
        val pressEnter = call.arguments["press_enter"]?.jsonPrimitive?.booleanOrNull ?: false

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
        delay(300)

        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Typed text into element #$elementId. Result: $rawResult",
            highlightedElementId = elementId
        )
    }

    private suspend fun executeScrollPage(call: AgentToolCall): AgentToolResult {
        val direction = call.arguments["direction"]?.jsonPrimitive?.content ?: "down"
        val amount = call.arguments["amount"]?.jsonPrimitive?.intOrNull ?: 600

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

        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Scrolled page $direction by $amount px. Result: $rawResult"
        )
    }

    private suspend fun executeConsole(call: AgentToolCall): AgentToolResult {
        val jsCode = call.arguments["js_code"]?.jsonPrimitive?.content
            ?: return errorResult(call, "Missing required parameter 'js_code'")

        val escapedCode = jsCode.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

        val script = """
            (function() {
                if (window.__mobileAgent && window.__mobileAgent.executeConsole) {
                    return JSON.stringify(window.__mobileAgent.executeConsole("$escapedCode"));
                }
                return JSON.stringify({ success: false, error: 'Runtime not initialized' });
            })();
        """.trimIndent()

        val rawResult = browserEngine.evaluateJavascriptAsync(script)

        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Console execution output: $rawResult"
        )
    }

    private suspend fun executeWaitForCondition(call: AgentToolCall): AgentToolResult {
        val timeoutMs = call.arguments["timeout_ms"]?.jsonPrimitive?.intOrNull ?: 2500
        val debounceMs = call.arguments["debounce_ms"]?.jsonPrimitive?.intOrNull ?: 300

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

        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "DOM stabilized or timeout reached (${timeoutMs}ms)"
        )
    }

    private fun executeGoBack(call: AgentToolCall): AgentToolResult {
        val canBack = browserEngine.goBack()
        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = canBack,
            output = if (canBack) "Navigated back in history" else "No previous history page"
        )
    }

    private fun executeFinishTask(call: AgentToolCall): AgentToolResult {
        val answer = call.arguments["answer"]?.jsonPrimitive?.content ?: "Task finished."
        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = answer
        )
    }

    private fun errorResult(call: AgentToolCall, error: String): AgentToolResult {
        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = false,
            output = "Error: $error",
            error = error
        )
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
