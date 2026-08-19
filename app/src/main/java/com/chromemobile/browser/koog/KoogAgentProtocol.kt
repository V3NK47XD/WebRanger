package com.chromemobile.browser.koog

import com.chromemobile.browser.agent.AgentStepLog
import com.chromemobile.browser.agent.AgentUIState
import com.chromemobile.browser.agent.ElementRect
import com.chromemobile.browser.agent.LlmClient
import com.chromemobile.browser.agent.LlmMessage
import com.chromemobile.browser.agent.SecurityGuard
import com.chromemobile.browser.engine.BrowserEngine
import com.chromemobile.browser.mcp.McpCallToolRequest
import com.chromemobile.browser.mcp.McpCallToolResponse
import com.chromemobile.browser.mcp.McpTool
import com.chromemobile.browser.mcp.MobileChromeMcpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Koog AI Agent Tool Interface (JetBrains Koog specification)
 */
interface KoogTool {
    val name: String
    val description: String
    val mcpTool: McpTool
    suspend fun execute(arguments: Map<String, JsonElement>): McpCallToolResponse
}

/**
 * Koog MCP Tool Adapter wrapping MobileChromeMcpServer
 */
class KoogMcpToolAdapter(
    override val mcpTool: McpTool,
    private val mcpServer: MobileChromeMcpServer
) : KoogTool {
    override val name: String = mcpTool.name
    override val description: String = mcpTool.description

    override suspend fun execute(arguments: Map<String, JsonElement>): McpCallToolResponse {
        val request = McpCallToolRequest(name = name, arguments = arguments)
        return mcpServer.callTool(request)
    }
}

/**
 * Koog Agent Execution Result
 */
data class KoogAgentResult(
    val finalAnswer: String,
    val success: Boolean,
    val totalTurns: Int,
    val logs: List<AgentStepLog>
)

/**
 * Koog Autonomous Agent State Machine & Runner
 */
class KoogAIAgent(
    val name: String = "KoogMobileBrowserAgent",
    val description: String = "Autonomous Chromium Mobile Browser Agent built with Koog Framework",
    private val tools: List<KoogTool>,
    private val llmClient: LlmClient,
    private val browserEngine: BrowserEngine,
    private val mcpServer: MobileChromeMcpServer
) {
    private val pastActionMemory = mutableListOf<String>()

    suspend fun run(
        goal: String,
        maxTurns: Int = 25,
        uiStateFlow: MutableStateFlow<AgentUIState>,
        isPausedProvider: () -> Boolean,
        onTurnLog: (AgentStepLog) -> Unit
    ): KoogAgentResult {
        pastActionMemory.clear()
        val mcpToolsList = tools.map { it.mcpTool }

        var turn = 1
        var taskFinished = false
        var finalAnswer = "Task ended."
        var finalSuccess = true

        while (turn <= maxTurns && !taskFinished) {
            while (isPausedProvider()) {
                kotlinx.coroutines.delay(500)
            }

            uiStateFlow.update {
                it.copy(
                    status = com.chromemobile.browser.agent.AgentStatus.OBSERVING,
                    currentTurn = turn
                )
            }

            // 1. OBSERVE: Fetch fresh DOM snapshot from browser
            val observeLog = AgentStepLog(turnNumber = turn, phase = "OBSERVE", message = "Koog Agent observing DOM snapshot...")
            onTurnLog(observeLog)

            // Wait if page is loading
            var loadWaitCount = 0
            while (browserEngine.state.value.isLoading && loadWaitCount < 10) {
                kotlinx.coroutines.delay(300)
                loadWaitCount++
            }

            val snapshot = mcpServer.fetchLatestDomSnapshot(viewportOnly = true)
            val currentUrl = browserEngine.state.value.currentUrl
            val pageTitle = browserEngine.state.value.title

            val observationText = if (snapshot != null && snapshot.elements.isNotEmpty()) {
                SecurityGuard.sanitizeSnapshotText(snapshot.treeText)
            } else {
                "Page URL: $currentUrl, Title: \"$pageTitle\"\n(Page is loading or DOM tree has no visible interactive elements yet)"
            }

            val historySummary = if (pastActionMemory.isNotEmpty()) {
                "Past Actions Memory:\n" + pastActionMemory.takeLast(5).joinToString("\n") + "\n\n"
            } else {
                ""
            }

            val activePrompt = """
                ${historySummary}Current Mobile Screen:
                $observationText
                
                Goal: "$goal"
                
                Choose your next mobile action toolcall. Respond with an MCP tool call (chrome_click_element, chrome_type_text, chrome_scroll, chrome_navigate, or chrome_finish_task).
            """.trimIndent()

            val messages = listOf(
                LlmMessage(role = "system", content = buildKoogSystemPrompt(goal)),
                LlmMessage(role = "user", content = activePrompt)
            )

            // 2. REASON: Query LLM via Koog Prompt Executor
            uiStateFlow.update { it.copy(status = com.chromemobile.browser.agent.AgentStatus.REASONING, currentReasoning = "") }
            val reasonLog = AgentStepLog(turnNumber = turn, phase = "REASON", message = "Koog ReAct reasoning on next action...")
            onTurnLog(reasonLog)

            val llmResponse = llmClient.chatCompletion(
                messages = messages,
                tools = mcpToolsList
            ) { chunk ->
                uiStateFlow.update { it.copy(currentReasoning = it.currentReasoning + chunk) }
            }

            if (!llmResponse.reasoning.isNullOrBlank()) {
                onTurnLog(AgentStepLog(turnNumber = turn, phase = "THOUGHT", message = llmResponse.reasoning))
            }

            val toolCalls = llmResponse.toolCalls

            if (toolCalls.isEmpty()) {
                val directText = llmResponse.content ?: ""
                onTurnLog(AgentStepLog(turnNumber = turn, phase = "THOUGHT", message = directText))

                if (turn < maxTurns) {
                    pastActionMemory.add("Turn $turn: Model replied with text -> \"$directText\"")
                    turn++
                    kotlinx.coroutines.delay(600)
                    continue
                } else {
                    taskFinished = true
                    finalAnswer = directText
                    break
                }
            }

            // 3. ACT: Execute Koog Tools
            uiStateFlow.update { it.copy(status = com.chromemobile.browser.agent.AgentStatus.ACTING) }

            for (toolCall in toolCalls) {
                onTurnLog(AgentStepLog(turnNumber = turn, phase = "ACTION", message = "Executing Koog Tool: ${toolCall.name} (args: ${toolCall.arguments})"))

                // Security check
                if (toolCall.name.contains("type_text")) {
                    val elementId = toolCall.arguments["element_id"]?.jsonPrimitive?.content?.toIntOrNull()
                    val text = toolCall.arguments["text"]?.jsonPrimitive?.contentOrNull
                    val matchingEl = snapshot?.elements?.find { it.id == elementId }
                    val safetyCheck = SecurityGuard.checkElementInteractionSafety("type", matchingEl, text)

                    if (safetyCheck.requiresUserConfirmation) {
                        uiStateFlow.update {
                            it.copy(
                                status = com.chromemobile.browser.agent.AgentStatus.AWAITING_CONFIRMATION,
                                pendingConfirmationMessage = safetyCheck.promptMessage
                            )
                        }
                        uiStateFlow.update { it.copy(status = com.chromemobile.browser.agent.AgentStatus.ACTING, pendingConfirmationMessage = null) }
                    }
                }

                // Highlight active target element
                val elementId = toolCall.arguments["element_id"]?.jsonPrimitive?.content?.toIntOrNull()
                if (elementId != null && snapshot != null) {
                    val el = snapshot.elements.find { it.id == elementId }
                    if (el != null) {
                        uiStateFlow.update { it.copy(highlightedRect = el.rect) }
                    }
                }

                val tool = tools.find { it.name == toolCall.name || it.name.removePrefix("chrome_") == toolCall.name.removePrefix("chrome_") }
                val toolResult = if (tool != null) {
                    tool.execute(toolCall.arguments)
                } else {
                    mcpServer.callTool(McpCallToolRequest(toolCall.name, toolCall.arguments))
                }

                val resultText = toolResult.getCombinedText()
                onTurnLog(AgentStepLog(turnNumber = turn, phase = "RESULT", message = resultText, isError = toolResult.isError))

                pastActionMemory.add("Turn $turn: ${toolCall.name}(${toolCall.arguments}) -> $resultText")

                if (toolCall.name.contains("finish_task") || toolCall.name.contains("done")) {
                    taskFinished = true
                    finalAnswer = resultText
                    finalSuccess = !toolResult.isError
                    onTurnLog(AgentStepLog(turnNumber = turn, phase = "FINISH", message = "Goal completed: $resultText"))
                    break
                }
            }

            turn++
            kotlinx.coroutines.delay(600)
        }

        return KoogAgentResult(
            finalAnswer = finalAnswer,
            success = finalSuccess,
            totalTurns = turn,
            logs = uiStateFlow.value.logs
        )
    }

    private fun buildKoogSystemPrompt(goal: String): String {
        return """
            You are an autonomous AI Agent built on the Koog Agent Framework, controlling an Android Mobile Chromium Browser.
            Goal: "$goal"
            
            KOOG MOBILE AGENT RULES:
            1. TOUCHSCREEN CONTEXT: You are on an Android mobile phone. There is NO keyboard shortcuts (Ctrl+K, Cmd+T do not exist), NO hover.
            2. TOOL CALLING: You MUST call one of the provided tools on every turn.
               - `chrome_navigate(url)`: Open a URL.
               - `chrome_click_element(element_id)`: Tap any button or link by its ID [N].
               - `chrome_type_text(element_id, text, press_enter)`: Type into search bars or inputs.
               - `chrome_scroll(direction)`: Scroll the mobile viewport.
               - `chrome_finish_task(answer)`: Finish when the goal is achieved.
            
            MULTI-TURN LOOP:
            Look at the numbered elements [1], [2], [3] in the DOM and call the next action. Continue step-by-step until the goal is fully accomplished.
        """.trimIndent()
    }
}
