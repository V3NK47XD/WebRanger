package com.chromemobile.browser.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String>? = null
)

@Serializable
data class AgentToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>
) {
    /**
     * Convert to OpenAI function definition JSON schema
     */
    fun toOpenAiSchema(): JsonObject {
        return buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", name)
                put("description", description)
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        for (param in parameters) {
                            put(param.name, buildJsonObject {
                                put("type", param.type)
                                put("description", param.description)
                                if (!param.enumValues.isNullOrEmpty()) {
                                    put("enum", buildJsonArray {
                                        for (v in param.enumValues) {
                                            add(kotlinx.serialization.json.JsonPrimitive(v))
                                        }
                                    })
                                }
                            })
                        }
                    })
                    put("required", buildJsonArray {
                        for (param in parameters.filter { it.required }) {
                            add(kotlinx.serialization.json.JsonPrimitive(param.name))
                        }
                    })
                })
            })
        }
    }

    /**
     * Convert to Anthropic Claude Tool JSON schema
     */
    fun toAnthropicSchema(): JsonObject {
        return buildJsonObject {
            put("name", name)
            put("description", description)
            put("input_schema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    for (param in parameters) {
                        put(param.name, buildJsonObject {
                            put("type", param.type)
                            put("description", param.description)
                            if (!param.enumValues.isNullOrEmpty()) {
                                put("enum", buildJsonArray {
                                    for (v in param.enumValues) {
                                        add(kotlinx.serialization.json.JsonPrimitive(v))
                                    }
                                })
                            }
                        })
                    }
                })
                put("required", buildJsonArray {
                    for (param in parameters.filter { it.required }) {
                        add(kotlinx.serialization.json.JsonPrimitive(param.name))
                    }
                })
            })
        }
    }
}

object AgentTools {

    val NAVIGATE = AgentToolDefinition(
        name = "navigate",
        description = "Navigate the browser to a specific URL",
        parameters = listOf(
            ToolParameter("url", "string", "The destination web URL (e.g. 'https://en.wikipedia.org')")
        )
    )

    val GET_DOM_SNAPSHOT = AgentToolDefinition(
        name = "get_dom_snapshot",
        description = "Extract current page semantic accessibility/DOM snapshot with numbered element IDs and text layout",
        parameters = listOf(
            ToolParameter("viewport_only", "boolean", "If true, extracts only elements currently visible in viewport", required = false)
        )
    )

    val TAKE_SCREENSHOT = AgentToolDefinition(
        name = "take_screenshot",
        description = "Capture visual screenshot of current mobile screen",
        parameters = emptyList()
    )

    val CLICK_ELEMENT = AgentToolDefinition(
        name = "click_element",
        description = "Perform a mobile touch tap/click on an interactive element by its numeric badge ID",
        parameters = listOf(
            ToolParameter("element_id", "integer", "Numeric ID of the element from the DOM snapshot to tap")
        )
    )

    val TYPE_TEXT = AgentToolDefinition(
        name = "type_text",
        description = "Type text into an input field or textarea identified by its element ID",
        parameters = listOf(
            ToolParameter("element_id", "integer", "Numeric ID of the input/textarea element"),
            ToolParameter("text", "string", "The text to type into the field"),
            ToolParameter("clear_first", "boolean", "Whether to clear existing text in the input before typing", required = false),
            ToolParameter("press_enter", "boolean", "Whether to simulate pressing Enter/Submit after typing", required = false)
        )
    )

    val SCROLL_PAGE = AgentToolDefinition(
        name = "scroll_page",
        description = "Scroll the mobile viewport up, down, to the top, or to the bottom",
        parameters = listOf(
            ToolParameter("direction", "string", "Scroll direction", enumValues = listOf("up", "down", "top", "bottom")),
            ToolParameter("amount", "integer", "Number of pixels to scroll (optional)", required = false)
        )
    )

    val EXECUTE_CONSOLE = AgentToolDefinition(
        name = "execute_console",
        description = "Evaluate arbitrary JavaScript in the active webpage context and return the result",
        parameters = listOf(
            ToolParameter("js_code", "string", "JavaScript code snippet to evaluate")
        )
    )

    val WAIT_FOR_CONDITION = AgentToolDefinition(
        name = "wait_for_condition",
        description = "Wait for dynamic DOM mutations or a specific CSS selector to appear",
        parameters = listOf(
            ToolParameter("timeout_ms", "integer", "Maximum time to wait in milliseconds (default: 3000)", required = false),
            ToolParameter("debounce_ms", "integer", "Quiet period with no mutations to consider stable (default: 300)", required = false)
        )
    )

    val GET_SAVED_CREDENTIALS = AgentToolDefinition(
        name = "get_saved_credentials",
        description = "Retrieve saved passwords and account credentials from Chrome Password Manager for a given domain or the active webpage",
        parameters = listOf(
            ToolParameter("domain", "string", "Target website domain (e.g. 'github.com', 'wikipedia.org'). If omitted, checks active webpage domain.", required = false),
            ToolParameter("url", "string", "Specific target URL to match credentials against", required = false)
        )
    )

    val SAVE_CREDENTIAL = AgentToolDefinition(
        name = "save_credential",
        description = "Save a new username and password credential to the Chrome Password Manager",
        parameters = listOf(
            ToolParameter("domain", "string", "Website domain (e.g. 'wikipedia.org', 'github.com')"),
            ToolParameter("username", "string", "Username, handle, or email address"),
            ToolParameter("password", "string", "Password for the account"),
            ToolParameter("title", "string", "Friendly account title or label", required = false),
            ToolParameter("url", "string", "Full login or registration URL", required = false)
        )
    )

    val AUTOFILL_LOGIN = AgentToolDefinition(
        name = "autofill_login",
        description = "Automatically detect username and password fields on the current webpage and fill them using saved credentials from Password Manager",
        parameters = listOf(
            ToolParameter("username", "string", "Specific username to fill if multiple credentials exist for this domain", required = false),
            ToolParameter("password", "string", "Specific password to fill (optional, auto-resolved from Password Manager if omitted)", required = false),
            ToolParameter("auto_submit", "boolean", "Whether to submit the login form automatically after filling", required = false)
        )
    )

    val GO_BACK = AgentToolDefinition(
        name = "go_back",
        description = "Navigate back to the previous page in history",
        parameters = emptyList()
    )

    val FINISH_TASK = AgentToolDefinition(
        name = "finish_task",
        description = "Complete the agent execution with a final answer or completion summary",
        parameters = listOf(
            ToolParameter("answer", "string", "Final answer, extracted summary, or task completion message")
        )
    )

    val ALL_TOOLS = listOf(
        NAVIGATE,
        GET_DOM_SNAPSHOT,
        TAKE_SCREENSHOT,
        CLICK_ELEMENT,
        TYPE_TEXT,
        SCROLL_PAGE,
        EXECUTE_CONSOLE,
        WAIT_FOR_CONDITION,
        GET_SAVED_CREDENTIALS,
        SAVE_CREDENTIAL,
        AUTOFILL_LOGIN,
        GO_BACK,
        FINISH_TASK
    )
}

@Serializable
data class AgentToolCall(
    val toolCallId: String,
    val name: String,
    val arguments: Map<String, kotlinx.serialization.json.JsonElement>
)

@Serializable
data class AgentToolResult(
    val toolCallId: String,
    val name: String,
    val success: Boolean,
    val output: String,
    val error: String? = null,
    val highlightedElementId: Int? = null
)
