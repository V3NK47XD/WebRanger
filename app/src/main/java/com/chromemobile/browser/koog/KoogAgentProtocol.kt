package com.chromemobile.browser.koog

import com.chromemobile.browser.agent.AgentStatus
import com.chromemobile.browser.agent.AgentStepLog
import com.chromemobile.browser.agent.AgentUIState
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
import kotlinx.serialization.json.JsonElement
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
    override val name: String get() = mcpTool.name
    override val description: String get() = mcpTool.description

    override suspend fun execute(arguments: Map<String, JsonElement>): McpCallToolResponse {
        return mcpServer.callTool(McpCallToolRequest(name = mcpTool.name, arguments = arguments))
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
    val name: String = "WebRangerBrowserAgent",
    val description: String = "Autonomous Chromium Mobile Browser Agent built with Koog Framework",
    private val tools: List<KoogTool>,
    private val llmClient: LlmClient,
    private val browserEngine: BrowserEngine,
    private val mcpServer: MobileChromeMcpServer
) {

    private val pastActionMemory = mutableListOf<String>()

    /**
     * Run the autonomous Koog Agent loop on active web surface
     */
    suspend fun run(
        goal: String,
        maxTurns: Int = 25,
        uiStateFlow: MutableStateFlow<AgentUIState>,
        isPausedProvider: () -> Boolean,
        initialContextHistory: List<String> = emptyList(),
        onTurnLog: (AgentStepLog) -> Unit
    ): KoogAgentResult {
        pastActionMemory.clear()
        if (initialContextHistory.isNotEmpty()) {
            pastActionMemory.addAll(initialContextHistory)
        }

        var turn = 1
        var finalAnswer = "Goal completed"
        var finalSuccess = true
        var taskFinished = false
        var consecutiveTextCount = 0

        val mcpToolsList = tools.map { it.mcpTool }

        while (turn <= maxTurns && !taskFinished) {
            while (isPausedProvider()) {
                kotlinx.coroutines.delay(300)
            }

            uiStateFlow.update { it.copy(currentTurn = turn, activeToolName = null) }

            // 1. OBSERVE: Capture current DOM snapshot and accessibility tree
            uiStateFlow.update { it.copy(status = AgentStatus.OBSERVING) }
            val snapshot = mcpServer.fetchLatestDomSnapshot(viewportOnly = false)

            val observationText = if (snapshot != null) {
                val sanitized = SecurityGuard.sanitizeSnapshotText(snapshot.treeText)
                sanitized
            } else {
                "Unable to extract DOM elements. Browser page may still be loading."
            }

            val historySummary = if (pastActionMemory.isNotEmpty()) {
                "Previous Actions & Context:\n" + pastActionMemory.takeLast(4).joinToString("\n") + "\n\n"
            } else {
                ""
            }

            val activePrompt = """
                ${historySummary}Current Mobile Screen:
                $observationText
                
                Goal: "$goal"
                
                Choose your next mobile action toolcall now.
            """.trimIndent()

            val messages = listOf(
                LlmMessage(role = "system", content = buildKoogSystemPrompt(goal)),
                LlmMessage(role = "user", content = activePrompt)
            )

            // 2. REASON: Query LLM via Koog Prompt Executor
            uiStateFlow.update { it.copy(status = AgentStatus.REASONING, currentReasoning = "", activeToolName = null) }
            val reasonLog = AgentStepLog(turnNumber = turn, phase = "REASON", message = "Reasoning on next action...")
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
                consecutiveTextCount++

                // If model answered the question or provided a summary, or if 2 consecutive text turns occurred, finish gracefully
                if (consecutiveTextCount >= 2 ||
                    directText.contains("summary", ignoreCase = true) ||
                    directText.contains("result", ignoreCase = true) ||
                    directText.contains("found", ignoreCase = true) ||
                    directText.contains("here is", ignoreCase = true) ||
                    turn >= 4
                ) {
                    taskFinished = true
                    finalAnswer = directText
                    onTurnLog(AgentStepLog(turnNumber = turn, phase = "FINISH", message = directText))
                    break
                }

                pastActionMemory.add("Turn $turn: AI stated \"$directText\"")
                turn++
                kotlinx.coroutines.delay(600)
                continue
            }

            consecutiveTextCount = 0

            // 3. ACT: Execute Koog Tools
            for (toolCall in toolCalls) {
                val cleanToolName = toolCall.name.removePrefix("chrome_")
                uiStateFlow.update { it.copy(status = AgentStatus.ACTING, activeToolName = cleanToolName) }

                onTurnLog(AgentStepLog(turnNumber = turn, phase = "ACTION", toolName = cleanToolName, message = "Calling MCP Tool: $cleanToolName"))

                // Security check
                if (toolCall.name.contains("type_text")) {
                    val elementId = toolCall.arguments["element_id"]?.jsonPrimitive?.content?.toIntOrNull()
                    val text = toolCall.arguments["text"]?.jsonPrimitive?.contentOrNull
                    val matchingEl = snapshot?.elements?.find { it.id == elementId }
                    val safetyCheck = SecurityGuard.checkElementInteractionSafety(
                        action = "type",
                        element = matchingEl,
                        typedText = text,
                        allowPasswordAccess = mcpServer.browserPreferences?.sharePasswordsWithLlm ?: false
                    )

                    if (safetyCheck.requiresUserConfirmation) {
                        uiStateFlow.update {
                            it.copy(
                                status = AgentStatus.AWAITING_CONFIRMATION,
                                pendingConfirmationMessage = safetyCheck.promptMessage
                            )
                        }
                        uiStateFlow.update { it.copy(status = AgentStatus.ACTING, pendingConfirmationMessage = null) }
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
                onTurnLog(AgentStepLog(turnNumber = turn, phase = "RESULT", toolName = cleanToolName, message = resultText, isError = toolResult.isError))

                pastActionMemory.add("Turn $turn: ${toolCall.name} -> $resultText")

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

        uiStateFlow.update { it.copy(activeToolName = null) }

        return KoogAgentResult(
            finalAnswer = finalAnswer,
            success = finalSuccess,
            totalTurns = turn,
            logs = uiStateFlow.value.logs
        )
    }

    private fun buildKoogSystemPrompt(goal: String): String {
        return """
            You are WebRanger AI Agent, an autonomous browser automation agent running directly on Chromium Mobile on Android.
            Your goal: "$goal"
            
            Guidelines:
            1. Use 'chrome_get_dom_snapshot' or read the provided element list to locate numbered badges [ID] on the page.
            2. To click, tap, or follow links, use 'chrome_click_element' with element_id.
            3. To enter text, use 'chrome_type_text' with element_id and text.
            4. To scroll, use 'chrome_scroll'.
            5. To retrieve or fill saved passwords from Password Manager, use 'chrome_get_saved_credentials' or 'chrome_autofill_login'.
            6. When your goal is accomplished, use 'chrome_finish_task' with your final summary or answer.
            
            Be direct and effective. Call the required tool immediately.
        """.trimIndent()
    }
}
