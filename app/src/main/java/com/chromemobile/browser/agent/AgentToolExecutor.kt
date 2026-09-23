package com.chromemobile.browser.agent

import android.graphics.Bitmap
import android.util.Base64
import com.chromemobile.browser.engine.BrowserEngine
import com.chromemobile.browser.engine.WebViewBrowserEngine
import com.chromemobile.browser.password.PasswordManager
import com.chromemobile.browser.password.SavedCredential
import com.chromemobile.browser.preferences.BrowserPreferences
import com.chromemobile.browser.tab.TabManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

class AgentToolExecutor(
    private val browserEngine: BrowserEngine,
    private val passwordManager: PasswordManager? = null,
    private val browserPreferences: BrowserPreferences? = null,
    private val tabManager: TabManager? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true }
) {

    /**
     * Dynamically resolve active browser tab's engine to always act on the currently active tab
     */
    val currentEngine: BrowserEngine
        get() = tabManager?.getActiveTab()?.engine ?: browserEngine

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
                "get_saved_credentials" -> executeGetSavedCredentials(toolCall)
                "save_credential" -> executeSaveCredential(toolCall)
                "autofill_login" -> executeAutofillLogin(toolCall)
                "list_tabs" -> executeListTabs(toolCall)
                "switch_tab" -> executeSwitchTab(toolCall)
                "create_tab" -> executeCreateTab(toolCall)
                "close_tab" -> executeCloseTab(toolCall)
                "get_tab_context" -> executeGetTabContext(toolCall)
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

        currentEngine.loadUrl(url)
        delay(1200)

        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Successfully requested navigation to $url"
        )
    }

    suspend fun fetchDomSnapshot(viewportOnly: Boolean = true): DomSnapshotResponse? {
        (currentEngine as? WebViewBrowserEngine)?.ensureAgentRuntime()
        val script = "window.__mobileAgent ? JSON.stringify(window.__mobileAgent.getDOMSnapshot({ viewportOnly: $viewportOnly })) : null;"
        val rawJson = currentEngine.evaluateJavascriptAsync(script)

        if (rawJson.isNullOrBlank() || rawJson == "null") {
            return null
        }

        val cleanJson = if (rawJson.startsWith("\"") && rawJson.endsWith("\"")) {
            try {
                json.decodeFromString<String>(rawJson)
            } catch (e: Exception) {
                rawJson
            }
        } else {
            rawJson
        }

        return try {
            val response = json.decodeFromString<DomSnapshotResponse>(cleanJson)
            latestSnapshot = response
            response
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun executeGetDomSnapshot(call: AgentToolCall): AgentToolResult {
        val viewportOnly = call.arguments["viewport_only"]?.jsonPrimitive?.booleanOrNull ?: false
        val snapshot = fetchDomSnapshot(viewportOnly = viewportOnly)

        return if (snapshot != null) {
            val sanitizedFormatted = SecurityGuard.sanitizeSnapshotText(snapshot.treeText)
            AgentToolResult(
                toolCallId = call.toolCallId,
                name = call.name,
                success = true,
                output = sanitizedFormatted
            )
        } else {
            errorResult(call, "Failed to capture DOM snapshot. The page might still be loading.")
        }
    }

    private suspend fun executeTakeScreenshot(call: AgentToolCall): AgentToolResult {
        val bitmap = currentEngine.captureScreenshotAsync()
        return if (bitmap != null) {
            val base64 = bitmapToBase64(bitmap)
            AgentToolResult(
                toolCallId = call.toolCallId,
                name = call.name,
                success = true,
                output = "data:image/png;base64,$base64"
            )
        } else {
            errorResult(call, "Failed to capture screen bitmap")
        }
    }

    private suspend fun executeClickElement(call: AgentToolCall): AgentToolResult {
        val elementId = call.arguments["element_id"]?.jsonPrimitive?.intOrNull
            ?: return errorResult(call, "Missing required numeric parameter 'element_id'")

        val element = latestSnapshot?.elements?.firstOrNull { it.id == elementId }
        val safetyCheck = SecurityGuard.checkElementInteractionSafety(
            action = "click",
            element = element,
            typedText = null,
            allowPasswordAccess = browserPreferences?.sharePasswordsWithLlm ?: false
        )
        if (!safetyCheck.isSafe) {
            return errorResult(call, "SecurityGuard blocked click: ${safetyCheck.promptMessage}")
        }

        (currentEngine as? WebViewBrowserEngine)?.ensureAgentRuntime()
        val script = "window.__mobileAgent ? JSON.stringify(window.__mobileAgent.interact('click', { id: $elementId })) : null;"
        val rawResult = currentEngine.evaluateJavascriptAsync(script)

        delay(300)
        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Successfully tapped element #$elementId (${element?.name ?: element?.tag ?: ""})",
            highlightedElementId = elementId
        )
    }

    private suspend fun executeTypeText(call: AgentToolCall): AgentToolResult {
        val elementId = call.arguments["element_id"]?.jsonPrimitive?.intOrNull
            ?: return errorResult(call, "Missing required numeric parameter 'element_id'")
        val text = call.arguments["text"]?.jsonPrimitive?.content
            ?: return errorResult(call, "Missing required string parameter 'text'")
        val clearFirst = call.arguments["clear_first"]?.jsonPrimitive?.booleanOrNull ?: true
        val pressEnter = call.arguments["press_enter"]?.jsonPrimitive?.booleanOrNull ?: false

        val element = latestSnapshot?.elements?.firstOrNull { it.id == elementId }
        val safetyCheck = SecurityGuard.checkElementInteractionSafety(
            action = "type",
            element = element,
            typedText = text,
            allowPasswordAccess = browserPreferences?.sharePasswordsWithLlm ?: false
        )
        if (!safetyCheck.isSafe && !safetyCheck.requiresUserConfirmation) {
            return errorResult(call, "SecurityGuard blocked typing into sensitive field")
        }

        val escapedText = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        (currentEngine as? WebViewBrowserEngine)?.ensureAgentRuntime()
        val script = "window.__mobileAgent ? JSON.stringify(window.__mobileAgent.interact('type', { id: $elementId, text: '$escapedText', clearFirst: $clearFirst, pressEnter: $pressEnter })) : null;"
        val rawResult = currentEngine.evaluateJavascriptAsync(script)

        delay(300)
        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Successfully typed into element #$elementId",
            highlightedElementId = elementId
        )
    }

    private suspend fun executeScrollPage(call: AgentToolCall): AgentToolResult {
        val direction = call.arguments["direction"]?.jsonPrimitive?.content ?: "down"
        val amount = call.arguments["amount"]?.jsonPrimitive?.intOrNull ?: 400

        val scrollScript = when (direction.lowercase()) {
            "top" -> "window.scrollTo({ top: 0, behavior: 'smooth' });"
            "bottom" -> "window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });"
            "up" -> "window.scrollBy({ top: -$amount, behavior: 'smooth' });"
            else -> "window.scrollBy({ top: $amount, behavior: 'smooth' });"
        }

        currentEngine.evaluateJavascriptAsync(scrollScript)
        delay(400)

        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Scrolled page $direction ($amount px)"
        )
    }

    private suspend fun executeConsole(call: AgentToolCall): AgentToolResult {
        val jsCode = call.arguments["js_code"]?.jsonPrimitive?.content
            ?: return errorResult(call, "Missing parameter 'js_code'")

        val result = currentEngine.evaluateJavascriptAsync(jsCode)
        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = result ?: "undefined"
        )
    }

    private suspend fun executeWaitForCondition(call: AgentToolCall): AgentToolResult {
        val timeoutMs = call.arguments["timeout_ms"]?.jsonPrimitive?.intOrNull ?: 3000
        val debounceMs = call.arguments["debounce_ms"]?.jsonPrimitive?.intOrNull ?: 300

        (currentEngine as? WebViewBrowserEngine)?.ensureAgentRuntime()
        val script = "window.__mobileAgent ? window.__mobileAgent.waitForStableDOM($timeoutMs, $debounceMs) : null;"
        val result = withTimeoutOrNull(timeoutMs.toLong() + 500) {
            currentEngine.evaluateJavascriptAsync(script)
        }

        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "DOM stabilized after dynamic mutation: ${result ?: "ok"}"
        )
    }

    private fun executeGetSavedCredentials(call: AgentToolCall): AgentToolResult {
        if (browserPreferences != null && !browserPreferences.sharePasswordsWithLlm) {
            return AgentToolResult(
                toolCallId = call.toolCallId,
                name = call.name,
                success = false,
                output = "Password sharing with AI / MCP tools is currently disabled in Chrome Settings. The user can toggle this on under Settings -> AI Agent & MCP -> 'Allow AI & MCP tools to access saved passwords'.",
                error = "PASSWORD_SHARING_DISABLED"
            )
        }

        val pm = passwordManager
            ?: return errorResult(call, "PasswordManager is not initialized")

        val targetDomain = call.arguments["domain"]?.jsonPrimitive?.content
            ?: call.arguments["url"]?.jsonPrimitive?.content
            ?: SavedCredential.normalizeDomain(currentEngine.state.value.currentUrl)

        val creds = if (targetDomain.isNotBlank()) {
            pm.getCredentialsForDomain(targetDomain)
        } else {
            pm.getAllCredentials()
        }

        if (creds.isEmpty()) {
            return AgentToolResult(
                toolCallId = call.toolCallId,
                name = call.name,
                success = true,
                output = "No saved credentials found for domain: '$targetDomain'. (Available saved domains: ${pm.getAllCredentials().map { it.domain }.distinct()})"
            )
        }

        val serialized = json.encodeToString(creds.map {
            mapOf(
                "domain" to it.domain,
                "username" to it.username,
                "password" to it.password,
                "title" to it.title,
                "originUrl" to it.originUrl
            )
        })

        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Found ${creds.size} saved credential(s) for '$targetDomain':\n$serialized"
        )
    }

    private fun executeSaveCredential(call: AgentToolCall): AgentToolResult {
        if (browserPreferences != null && !browserPreferences.sharePasswordsWithLlm) {
            return AgentToolResult(
                toolCallId = call.toolCallId,
                name = call.name,
                success = false,
                output = "Password access is disabled in Settings. Enable 'Allow AI & MCP tools to access saved passwords' to use credential tools.",
                error = "PASSWORD_SHARING_DISABLED"
            )
        }

        val pm = passwordManager
            ?: return errorResult(call, "PasswordManager is not initialized")

        val domain = call.arguments["domain"]?.jsonPrimitive?.content
            ?: return errorResult(call, "Missing required parameter 'domain'")
        val username = call.arguments["username"]?.jsonPrimitive?.content
            ?: return errorResult(call, "Missing required parameter 'username'")
        val password = call.arguments["password"]?.jsonPrimitive?.content
            ?: return errorResult(call, "Missing required parameter 'password'")
        val title = call.arguments["title"]?.jsonPrimitive?.content ?: domain
        val url = call.arguments["url"]?.jsonPrimitive?.content ?: currentEngine.state.value.currentUrl

        val saved = pm.saveCredential(
            SavedCredential(
                domain = domain,
                originUrl = url,
                username = username,
                password = password,
                title = title
            )
        )

        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Successfully saved credential for user '${saved.username}' on domain '${saved.domain}'."
        )
    }

    private suspend fun executeAutofillLogin(call: AgentToolCall): AgentToolResult {
        if (browserPreferences != null && !browserPreferences.sharePasswordsWithLlm) {
            return AgentToolResult(
                toolCallId = call.toolCallId,
                name = call.name,
                success = false,
                output = "Password sharing with AI / MCP tools is disabled in Settings. Please enable 'Allow AI & MCP tools to access saved passwords'.",
                error = "PASSWORD_SHARING_DISABLED"
            )
        }

        val pm = passwordManager
            ?: return errorResult(call, "PasswordManager is not initialized")

        val currentUrl = currentEngine.state.value.currentUrl
        val currentDomain = SavedCredential.normalizeDomain(currentUrl)
        val savedCreds = pm.getCredentialsForDomain(currentDomain)

        val requestedUsername = call.arguments["username"]?.jsonPrimitive?.content
        val requestedPassword = call.arguments["password"]?.jsonPrimitive?.content
        val autoSubmit = call.arguments["auto_submit"]?.jsonPrimitive?.booleanOrNull ?: false

        val selectedCred = if (!requestedUsername.isNullOrBlank()) {
            savedCreds.firstOrNull { it.username.equals(requestedUsername, ignoreCase = true) }
        } else {
            savedCreds.firstOrNull()
        }

        val usernameToFill = requestedUsername ?: selectedCred?.username
        val passwordToFill = requestedPassword ?: selectedCred?.password

        if (usernameToFill.isNullOrBlank() || passwordToFill.isNullOrBlank()) {
            return errorResult(call, "No matching saved credentials found for domain '$currentDomain'. Please save or provide username and password.")
        }

        selectedCred?.let { pm.markUsed(it.id) }

        val escapedUser = usernameToFill.replace("\\", "\\\\").replace("'", "\\'")
        val escapedPass = passwordToFill.replace("\\", "\\\\").replace("'", "\\'")

        val autofillJs = """
            (function() {
                const userField = document.querySelector('input[type="email"], input[type="text"][name*="user"], input[name*="login"], input[name*="email"], input[autocomplete*="username"], input[autocomplete*="email"]') || document.querySelector('input[type="text"]');
                const passField = document.querySelector('input[type="password"]');
                let userFilled = false;
                let passFilled = false;

                function triggerInputEvents(el, val) {
                    if (!el) return;
                    el.focus();
                    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
                    if (nativeSetter) {
                        nativeSetter.call(el, val);
                    } else {
                        el.value = val;
                    }
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                }

                if (userField) {
                    triggerInputEvents(userField, '$escapedUser');
                    userFilled = true;
                }
                if (passField) {
                    triggerInputEvents(passField, '$escapedPass');
                    passFilled = true;
                }

                if ($autoSubmit) {
                    const submitBtn = document.querySelector('button[type="submit"], input[type="submit"], [role="button"][id*="login"], [role="button"][id*="submit"]');
                    if (submitBtn) {
                        setTimeout(() => submitBtn.click(), 200);
                    } else if (passField && passField.form) {
                        setTimeout(() => passField.form.submit(), 200);
                    }
                }

                return JSON.stringify({ success: true, userFilled: userFilled, passFilled: passFilled, userField: userField ? userField.name || userField.id : null });
            })();
        """.trimIndent()

        val rawResult = currentEngine.evaluateJavascriptAsync(autofillJs)
        delay(400)

        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Successfully autofilled credentials for user '$usernameToFill' on domain '$currentDomain' into webpage login form. Result: $rawResult"
        )
    }

    private fun executeListTabs(call: AgentToolCall): AgentToolResult {
        val tm = tabManager
        val allTabsJson = buildJsonArray {
            if (tm != null) {
                for (tab in tm.tabs.value) {
                    add(buildJsonObject {
                        put("id", tab.id)
                        put("title", tab.engine.state.value.title.ifBlank { "New Tab" })
                        put("url", tab.engine.state.value.currentUrl)
                        put("isActive", tab.id == tm.activeTabId.value)
                        put("hasAiContext", tab.hasAiContext)
                    })
                }
            } else {
                add(buildJsonObject {
                    put("id", "main")
                    put("title", browserEngine.state.value.title.ifBlank { "Active Tab" })
                    put("url", browserEngine.state.value.currentUrl)
                    put("isActive", true)
                    put("hasAiContext", false)
                })
            }
        }

        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Open Browser Tabs:\n" + allTabsJson.toString()
        )
    }

    private suspend fun executeSwitchTab(call: AgentToolCall): AgentToolResult {
        val tm = tabManager ?: return errorResult(call, "TabManager not available")
        val tabId = call.arguments["tab_id"]?.jsonPrimitive?.content
            ?: return errorResult(call, "Missing required parameter 'tab_id'")

        val target = tm.getTabById(tabId)
            ?: return errorResult(call, "Tab with id '$tabId' not found. Available tabs: ${tm.tabs.value.map { it.id }}")

        tm.selectTab(tabId)
        delay(400)

        val active = tm.getActiveTab()
        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Switched to tab '$tabId' (title: '${active.engine.state.value.title}', url: '${active.engine.state.value.currentUrl}'). Tab is now active and visible on screen."
        )
    }

    private suspend fun executeCreateTab(call: AgentToolCall): AgentToolResult {
        val tm = tabManager ?: return errorResult(call, "TabManager not available")
        val url = call.arguments["url"]?.jsonPrimitive?.content ?: "about:blank"

        val createdTab = tm.createTab(url = url, selectImmediately = true)
        delay(600)

        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Created and switched to new tab '${createdTab.id}' (url: '$url'). Tab is now active and visible on screen."
        )
    }

    private fun executeCloseTab(call: AgentToolCall): AgentToolResult {
        val tm = tabManager ?: return errorResult(call, "TabManager not available")
        val tabId = call.arguments["tab_id"]?.jsonPrimitive?.content ?: tm.activeTabId.value

        val closed = tm.closeTab(tabId)
        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = closed,
            output = if (closed) "Closed tab '$tabId'. Active tab is now '${tm.activeTabId.value}'." else "Failed to close tab '$tabId' (not found)"
        )
    }

    private fun executeGetTabContext(call: AgentToolCall): AgentToolResult {
        val tm = tabManager ?: return errorResult(call, "TabManager not available")
        val tabId = call.arguments["tab_id"]?.jsonPrimitive?.content ?: tm.activeTabId.value

        val targetTab = tm.getTabById(tabId)
            ?: return errorResult(call, "Tab '$tabId' not found")

        val ctx = targetTab.aiContext
        if (ctx == null) {
            return AgentToolResult(
                toolCallId = call.toolCallId,
                name = call.name,
                success = true,
                output = "No AI context has been recorded for tab '$tabId' (AI agent was never invoked on this tab)."
            )
        }

        val serialized = json.encodeToString(
            mapOf(
                "tabId" to targetTab.id,
                "lastGoal" to ctx.lastGoal,
                "finalAnswer" to (ctx.finalAnswer ?: "Completed"),
                "totalTurns" to ctx.totalTurns,
                "lastUpdated" to ctx.lastUpdated
            )
        )

        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = true,
            output = "Tab AI Context for '$tabId':\n$serialized"
        )
    }

    private fun executeGoBack(call: AgentToolCall): AgentToolResult {
        val wentBack = currentEngine.goBack()
        return AgentToolResult(
            toolCallId = call.toolCallId,
            name = call.name,
            success = wentBack,
            output = if (wentBack) "Navigated back in history" else "No previous page to go back to"
        )
    }

    private fun executeFinishTask(call: AgentToolCall): AgentToolResult {
        val answer = call.arguments["answer"]?.jsonPrimitive?.content ?: "Task finished"
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
            output = error,
            error = error
        )
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
