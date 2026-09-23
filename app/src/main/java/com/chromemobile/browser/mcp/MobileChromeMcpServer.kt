package com.chromemobile.browser.mcp

import android.graphics.Bitmap
import android.util.Base64
import com.chromemobile.browser.agent.DomSnapshotResponse
import com.chromemobile.browser.agent.SecurityGuard
import com.chromemobile.browser.engine.BrowserEngine
import com.chromemobile.browser.engine.WebViewBrowserEngine
import com.chromemobile.browser.password.PasswordManager
import com.chromemobile.browser.password.SavedCredential
import com.chromemobile.browser.preferences.BrowserPreferences
import com.chromemobile.browser.tab.TabManager
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

/**
 * In-App Mobile Chrome DevTools MCP Server
 * Standardized Model Context Protocol tool provider for autonomous mobile browser automation.
 */
class MobileChromeMcpServer(
    private val browserEngine: BrowserEngine,
    val passwordManager: PasswordManager? = null,
    val browserPreferences: BrowserPreferences? = null,
    val tabManager: TabManager? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true }
) {

    /**
     * Dynamically resolve active browser tab's engine to always act on the currently active tab
     */
    val currentEngine: BrowserEngine
        get() = tabManager?.getActiveTab()?.engine ?: browserEngine

    private var latestSnapshot: DomSnapshotResponse? = null

    /**
     * Expose standard Chrome DevTools MCP tool catalog
     */
    fun listTools(): List<McpTool> {
        return listOf(
            McpTool(
                name = "chrome_navigate",
                description = "Navigate the active browser tab to a destination URL",
                properties = mapOf(
                    "url" to McpProperty("string", "The URL to navigate to (e.g. 'https://en.wikipedia.org')")
                ),
                required = listOf("url")
            ),
            McpTool(
                name = "chrome_get_dom_snapshot",
                description = "Extract active page semantic accessibility tree and visible interactive element index",
                properties = mapOf(
                    "viewport_only" to McpProperty("boolean", "Whether to extract only elements inside the active viewport (default: true)")
                ),
                required = emptyList()
            ),
            McpTool(
                name = "chrome_click_element",
                description = "Perform a touch tap/click on an interactive element in the active tab by its numeric badge ID",
                properties = mapOf(
                    "element_id" to McpProperty("integer", "Numeric element ID from the DOM snapshot to tap/click")
                ),
                required = listOf("element_id")
            ),
            McpTool(
                name = "chrome_type_text",
                description = "Type text into an input field or textarea identified by its element ID in the active tab",
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
                description = "Scroll the active mobile viewport up, down, to the top, or to the bottom",
                properties = mapOf(
                    "direction" to McpProperty("string", "Scroll direction", enumValues = listOf("up", "down", "top", "bottom")),
                    "amount" to McpProperty("integer", "Pixel scroll delta amount (optional)")
                ),
                required = listOf("direction")
            ),
            McpTool(
                name = "chrome_evaluate_script",
                description = "Evaluate arbitrary JavaScript in the active tab webpage context and return the result",
                properties = mapOf(
                    "script" to McpProperty("string", "JavaScript code to execute")
                ),
                required = listOf("script")
            ),
            McpTool(
                name = "chrome_wait",
                description = "Wait for asynchronous DOM mutations, route changes, or dynamic content to settle in active tab",
                properties = mapOf(
                    "timeout_ms" to McpProperty("integer", "Max wait duration in milliseconds (default: 2500)"),
                    "debounce_ms" to McpProperty("integer", "Quiet duration to consider stable (default: 300)")
                ),
                required = emptyList()
            ),
            McpTool(
                name = "chrome_get_saved_credentials",
                description = "Retrieve saved passwords and account credentials from Chrome Password Manager for a given domain or the active webpage",
                properties = mapOf(
                    "domain" to McpProperty("string", "Target website domain (e.g. 'github.com', 'wikipedia.org'). If omitted, uses active webpage domain."),
                    "url" to McpProperty("string", "Target URL to match credentials against")
                ),
                required = emptyList()
            ),
            McpTool(
                name = "chrome_save_credential",
                description = "Save a new username and password credential to the Chrome Password Manager",
                properties = mapOf(
                    "domain" to McpProperty("string", "Website domain (e.g. 'wikipedia.org', 'github.com')"),
                    "username" to McpProperty("string", "Username, email, or handle"),
                    "password" to McpProperty("string", "Account password"),
                    "title" to McpProperty("string", "Friendly account title or label"),
                    "url" to McpProperty("string", "Full login or registration URL")
                ),
                required = listOf("domain", "username", "password")
            ),
            McpTool(
                name = "chrome_autofill_login",
                description = "Automatically detect login fields (username and password) on current webpage and fill them using saved credentials from Password Manager",
                properties = mapOf(
                    "username" to McpProperty("string", "Specific username to fill if multiple accounts exist for this domain"),
                    "password" to McpProperty("string", "Specific password to fill (optional, auto-resolved from Password Manager)"),
                    "auto_submit" to McpProperty("boolean", "Whether to submit the login form automatically after filling")
                ),
                required = emptyList()
            ),
            McpTool(
                name = "chrome_list_tabs",
                description = "List all open browser tabs with their IDs, titles, URLs, active status, and whether they have stored AI context",
                properties = emptyMap(),
                required = emptyList()
            ),
            McpTool(
                name = "chrome_switch_tab",
                description = "Switch the active browser tab to the tab specified by tab_id and make it visible on screen",
                properties = mapOf(
                    "tab_id" to McpProperty("string", "Unique ID of the tab to switch to")
                ),
                required = listOf("tab_id")
            ),
            McpTool(
                name = "chrome_create_tab",
                description = "Open a new browser tab with an optional destination URL and immediately activate it",
                properties = mapOf(
                    "url" to McpProperty("string", "URL to load in the new tab (default: 'about:blank')")
                ),
                required = emptyList()
            ),
            McpTool(
                name = "chrome_close_tab",
                description = "Close a browser tab specified by tab_id or the active tab",
                properties = mapOf(
                    "tab_id" to McpProperty("string", "ID of the tab to close. If omitted, closes the active tab.")
                ),
                required = emptyList()
            ),
            McpTool(
                name = "chrome_get_tab_context",
                description = "Retrieve the AI agent execution context, last goal, and summary for a specific tab",
                properties = mapOf(
                    "tab_id" to McpProperty("string", "ID of the tab to inspect. If omitted, uses active tab.")
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
                description = "Navigate back to the previous page in history in active tab",
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
                toolName.contains("list_tab") -> handleListTabs(request)
                toolName.contains("switch_tab") -> handleSwitchTab(request)
                toolName.contains("create_tab") || toolName.contains("new_tab") -> handleCreateTab(request)
                toolName.contains("close_tab") -> handleCloseTab(request)
                toolName.contains("get_tab_context") -> handleGetTabContext(request)
                toolName.contains("get_saved_credential") || toolName.contains("get_credential") || toolName.contains("list_credential") -> handleGetSavedCredentials(request)
                toolName.contains("save_credential") || toolName.contains("store_credential") || toolName.contains("add_credential") -> handleSaveCredential(request)
                toolName.contains("autofill") || toolName.contains("fill_credential") || toolName.contains("login") -> handleAutofillLogin(request)
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

        currentEngine.loadUrl(url)
        delay(1200)

        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "Navigated to $url"))
        )
    }

    suspend fun fetchLatestDomSnapshot(viewportOnly: Boolean = true): DomSnapshotResponse? {
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

    private suspend fun handleGetDomSnapshot(request: McpCallToolRequest): McpCallToolResponse {
        val viewportOnly = request.arguments["viewport_only"]?.jsonPrimitive?.booleanOrNull ?: true
        val snapshot = fetchLatestDomSnapshot(viewportOnly = viewportOnly)

        return if (snapshot != null) {
            val sanitized = SecurityGuard.sanitizeSnapshotText(snapshot.treeText)
            McpCallToolResponse(
                content = listOf(McpContent(type = "text", text = sanitized))
            )
        } else {
            errorResponse("Failed to extract DOM snapshot. The page may still be loading or the agent runtime has not initialized yet.")
        }
    }

    private suspend fun handleClickElement(request: McpCallToolRequest): McpCallToolResponse {
        val elementId = request.arguments["element_id"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: request.arguments["id"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: return errorResponse("Missing required numeric parameter 'element_id'")

        val element = latestSnapshot?.elements?.firstOrNull { it.id == elementId }
        val safetyCheck = SecurityGuard.checkElementInteractionSafety(
            action = "click",
            element = element,
            typedText = null,
            allowPasswordAccess = browserPreferences?.sharePasswordsWithLlm ?: false
        )
        if (!safetyCheck.isSafe) {
            return errorResponse("SecurityGuard blocked tap: ${safetyCheck.promptMessage}")
        }

        (currentEngine as? WebViewBrowserEngine)?.ensureAgentRuntime()
        val script = "window.__mobileAgent ? JSON.stringify(window.__mobileAgent.interact('click', { id: $elementId })) : null;"
        val rawResult = currentEngine.evaluateJavascriptAsync(script)

        delay(350)
        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "Tapped element #$elementId (${element?.name ?: element?.tag ?: ""}). Result: $rawResult")),
            highlightedElementId = elementId
        )
    }

    private suspend fun handleTypeText(request: McpCallToolRequest): McpCallToolResponse {
        val elementId = request.arguments["element_id"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: request.arguments["id"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: return errorResponse("Missing required numeric parameter 'element_id'")

        val text = request.arguments["text"]?.jsonPrimitive?.content
            ?: request.arguments["value"]?.jsonPrimitive?.content
            ?: return errorResponse("Missing required string parameter 'text'")

        val clearFirst = request.arguments["clear_first"]?.jsonPrimitive?.booleanOrNull ?: true
        val pressEnter = request.arguments["press_enter"]?.jsonPrimitive?.booleanOrNull ?: false

        val element = latestSnapshot?.elements?.firstOrNull { it.id == elementId }
        val safetyCheck = SecurityGuard.checkElementInteractionSafety(
            action = "type",
            element = element,
            typedText = text,
            allowPasswordAccess = browserPreferences?.sharePasswordsWithLlm ?: false
        )
        if (!safetyCheck.isSafe && !safetyCheck.requiresUserConfirmation) {
            return errorResponse("SecurityGuard blocked typing into sensitive field")
        }

        val escapedText = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        (currentEngine as? WebViewBrowserEngine)?.ensureAgentRuntime()
        val script = "window.__mobileAgent ? JSON.stringify(window.__mobileAgent.interact('type', { id: $elementId, text: '$escapedText', clearFirst: $clearFirst, pressEnter: $pressEnter })) : null;"
        val rawResult = currentEngine.evaluateJavascriptAsync(script)

        delay(350)
        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "Typed into element #$elementId. Result: $rawResult")),
            highlightedElementId = elementId
        )
    }

    private suspend fun handleScroll(request: McpCallToolRequest): McpCallToolResponse {
        val direction = request.arguments["direction"]?.jsonPrimitive?.content ?: "down"
        val amount = request.arguments["amount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 450

        val scrollScript = when (direction.lowercase()) {
            "top" -> "window.scrollTo({ top: 0, behavior: 'smooth' });"
            "bottom" -> "window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });"
            "up" -> "window.scrollBy({ top: -$amount, behavior: 'smooth' });"
            else -> "window.scrollBy({ top: $amount, behavior: 'smooth' });"
        }

        currentEngine.evaluateJavascriptAsync(scrollScript)
        delay(400)

        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "Scrolled viewport $direction by $amount px"))
        )
    }

    private suspend fun handleEvaluateScript(request: McpCallToolRequest): McpCallToolResponse {
        val script = request.arguments["script"]?.jsonPrimitive?.content
            ?: request.arguments["code"]?.jsonPrimitive?.content
            ?: return errorResponse("Missing parameter 'script'")

        val result = currentEngine.evaluateJavascriptAsync(script)
        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = result ?: "undefined"))
        )
    }

    private suspend fun handleWait(request: McpCallToolRequest): McpCallToolResponse {
        val timeoutMs = request.arguments["timeout_ms"]?.jsonPrimitive?.content?.toIntOrNull() ?: 2500
        val debounceMs = request.arguments["debounce_ms"]?.jsonPrimitive?.content?.toIntOrNull() ?: 300

        (currentEngine as? WebViewBrowserEngine)?.ensureAgentRuntime()
        val script = "window.__mobileAgent ? window.__mobileAgent.waitForStableDOM($timeoutMs, $debounceMs) : null;"
        val result = currentEngine.evaluateJavascriptAsync(script)
        delay(300)

        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "Waited for DOM stabilization: ${result ?: "ready"}"))
        )
    }

    private fun handleGetSavedCredentials(request: McpCallToolRequest): McpCallToolResponse {
        if (browserPreferences != null && !browserPreferences.sharePasswordsWithLlm) {
            return McpCallToolResponse(
                content = listOf(
                    McpContent(
                        type = "text",
                        text = "Password sharing with AI / MCP tools is currently disabled in Chrome Settings. The user can toggle this on under Settings -> AI Agent & MCP -> 'Allow AI & MCP tools to access saved passwords'."
                    )
                ),
                isError = true
            )
        }

        val pm = passwordManager ?: return errorResponse("PasswordManager is not initialized")

        val targetDomain = request.arguments["domain"]?.jsonPrimitive?.content
            ?: request.arguments["url"]?.jsonPrimitive?.content
            ?: SavedCredential.normalizeDomain(currentEngine.state.value.currentUrl)

        val creds = if (targetDomain.isNotBlank()) {
            pm.getCredentialsForDomain(targetDomain)
        } else {
            pm.getAllCredentials()
        }

        if (creds.isEmpty()) {
            return McpCallToolResponse(
                content = listOf(
                    McpContent(
                        type = "text",
                        text = "No saved credentials found for domain: '$targetDomain'. (Available domains: ${pm.getAllCredentials().map { it.domain }.distinct()})"
                    )
                )
            )
        }

        val jsonResult = json.encodeToString(creds.map {
            mapOf(
                "domain" to it.domain,
                "username" to it.username,
                "password" to it.password,
                "title" to it.title,
                "originUrl" to it.originUrl
            )
        })

        return McpCallToolResponse(
            content = listOf(
                McpContent(
                    type = "text",
                    text = "Found ${creds.size} saved credential(s) for '$targetDomain':\n$jsonResult"
                )
            )
        )
    }

    private fun handleSaveCredential(request: McpCallToolRequest): McpCallToolResponse {
        if (browserPreferences != null && !browserPreferences.sharePasswordsWithLlm) {
            return McpCallToolResponse(
                content = listOf(
                    McpContent(
                        type = "text",
                        text = "Password access is disabled in Settings. Please enable 'Allow AI & MCP tools to access saved passwords'."
                    )
                ),
                isError = true
            )
        }

        val pm = passwordManager ?: return errorResponse("PasswordManager is not initialized")

        val domain = request.arguments["domain"]?.jsonPrimitive?.content
            ?: return errorResponse("Missing required parameter 'domain'")
        val username = request.arguments["username"]?.jsonPrimitive?.content
            ?: return errorResponse("Missing required parameter 'username'")
        val password = request.arguments["password"]?.jsonPrimitive?.content
            ?: return errorResponse("Missing required parameter 'password'")
        val title = request.arguments["title"]?.jsonPrimitive?.content ?: domain
        val url = request.arguments["url"]?.jsonPrimitive?.content ?: currentEngine.state.value.currentUrl

        val saved = pm.saveCredential(
            SavedCredential(
                domain = domain,
                originUrl = url,
                username = username,
                password = password,
                title = title
            )
        )

        return McpCallToolResponse(
            content = listOf(
                McpContent(
                    type = "text",
                    text = "Successfully saved credential for user '${saved.username}' on domain '${saved.domain}'."
                )
            )
        )
    }

    private suspend fun handleAutofillLogin(request: McpCallToolRequest): McpCallToolResponse {
        if (browserPreferences != null && !browserPreferences.sharePasswordsWithLlm) {
            return McpCallToolResponse(
                content = listOf(
                    McpContent(
                        type = "text",
                        text = "Password sharing with AI / MCP tools is disabled in Chrome Settings. Enable 'Allow AI & MCP tools to access saved passwords'."
                    )
                ),
                isError = true
            )
        }

        val pm = passwordManager ?: return errorResponse("PasswordManager is not initialized")

        val currentUrl = currentEngine.state.value.currentUrl
        val currentDomain = SavedCredential.normalizeDomain(currentUrl)
        val savedCreds = pm.getCredentialsForDomain(currentDomain)

        val requestedUsername = request.arguments["username"]?.jsonPrimitive?.content
        val requestedPassword = request.arguments["password"]?.jsonPrimitive?.content
        val autoSubmit = request.arguments["auto_submit"]?.jsonPrimitive?.booleanOrNull ?: false

        val selectedCred = if (!requestedUsername.isNullOrBlank()) {
            savedCreds.firstOrNull { it.username.equals(requestedUsername, ignoreCase = true) }
        } else {
            savedCreds.firstOrNull()
        }

        val usernameToFill = requestedUsername ?: selectedCred?.username
        val passwordToFill = requestedPassword ?: selectedCred?.password

        if (usernameToFill.isNullOrBlank() || passwordToFill.isNullOrBlank()) {
            return errorResponse("No matching saved credentials found for domain '$currentDomain'. Please save or provide username and password.")
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

        return McpCallToolResponse(
            content = listOf(
                McpContent(
                    type = "text",
                    text = "Autofilled credentials for user '$usernameToFill' on domain '$currentDomain' into webpage form. Result: $rawResult"
                )
            )
        )
    }

    private fun handleListTabs(request: McpCallToolRequest): McpCallToolResponse {
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

        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "Open Tabs:\n" + allTabsJson.toString()))
        )
    }

    private suspend fun handleSwitchTab(request: McpCallToolRequest): McpCallToolResponse {
        val tm = tabManager ?: return errorResponse("TabManager is not available")
        val tabId = request.arguments["tab_id"]?.jsonPrimitive?.content
            ?: return errorResponse("Missing required parameter 'tab_id'")

        val target = tm.getTabById(tabId)
            ?: return errorResponse("Tab '$tabId' not found. Available tabs: ${tm.tabs.value.map { it.id }}")

        tm.selectTab(tabId)
        delay(400)

        val active = tm.getActiveTab()
        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "Switched to tab '$tabId' (title: '${active.engine.state.value.title}', url: '${active.engine.state.value.currentUrl}'). Tab is now active and visible on screen."))
        )
    }

    private suspend fun handleCreateTab(request: McpCallToolRequest): McpCallToolResponse {
        val tm = tabManager ?: return errorResponse("TabManager is not available")
        val url = request.arguments["url"]?.jsonPrimitive?.content ?: "about:blank"

        val created = tm.createTab(url = url, selectImmediately = true)
        delay(600)

        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "Created and switched to new tab '${created.id}' (url: '$url'). Tab is now active and visible on screen."))
        )
    }

    private fun handleCloseTab(request: McpCallToolRequest): McpCallToolResponse {
        val tm = tabManager ?: return errorResponse("TabManager is not available")
        val tabId = request.arguments["tab_id"]?.jsonPrimitive?.content ?: tm.activeTabId.value

        val closed = tm.closeTab(tabId)
        return if (closed) {
            McpCallToolResponse(content = listOf(McpContent(type = "text", text = "Closed tab '$tabId'. Active tab is now '${tm.activeTabId.value}'.")))
        } else {
            errorResponse("Failed to close tab '$tabId' (not found)")
        }
    }

    private fun handleGetTabContext(request: McpCallToolRequest): McpCallToolResponse {
        val tm = tabManager ?: return errorResponse("TabManager is not available")
        val tabId = request.arguments["tab_id"]?.jsonPrimitive?.content ?: tm.activeTabId.value

        val targetTab = tm.getTabById(tabId) ?: return errorResponse("Tab '$tabId' not found")
        val ctx = targetTab.aiContext
            ?: return McpCallToolResponse(content = listOf(McpContent(type = "text", text = "No AI context has been recorded for tab '$tabId' (AI agent was never invoked on this tab).")))

        val serialized = json.encodeToString(
            mapOf(
                "tabId" to targetTab.id,
                "lastGoal" to ctx.lastGoal,
                "finalAnswer" to (ctx.finalAnswer ?: "Completed"),
                "totalTurns" to ctx.totalTurns,
                "lastUpdated" to ctx.lastUpdated
            )
        )

        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = "Tab AI Context for '$tabId':\n$serialized"))
        )
    }

    private suspend fun handleTakeScreenshot(request: McpCallToolRequest): McpCallToolResponse {
        val bitmap = currentEngine.captureScreenshotAsync()
        return if (bitmap != null) {
            val base64 = bitmapToBase64(bitmap)
            McpCallToolResponse(
                content = listOf(
                    McpContent(
                        type = "image",
                        data = base64,
                        mimeType = "image/png"
                    )
                )
            )
        } else {
            errorResponse("Failed to capture screenshot bitmap")
        }
    }

    private fun handleGoBack(request: McpCallToolRequest): McpCallToolResponse {
        val success = currentEngine.goBack()
        return McpCallToolResponse(
            content = listOf(McpContent(type = "text", text = if (success) "Navigated back in history" else "No history backward"))
        )
    }

    private fun handleFinishTask(request: McpCallToolRequest): McpCallToolResponse {
        val answer = request.arguments["answer"]?.jsonPrimitive?.content ?: "Completed task"
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

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
