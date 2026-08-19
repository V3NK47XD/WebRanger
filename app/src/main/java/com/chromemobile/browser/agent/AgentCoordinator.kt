package com.chromemobile.browser.agent

import com.chromemobile.browser.engine.BrowserEngine
import com.chromemobile.browser.mcp.McpCallToolRequest
import com.chromemobile.browser.mcp.MobileChromeMcpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class AgentStatus {
    IDLE,
    PLANNING,
    OBSERVING,
    REASONING,
    ACTING,
    AWAITING_CONFIRMATION,
    PAUSED,
    COMPLETED,
    ERROR
}

@Serializable
data class AgentStepLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val turnNumber: Int,
    val phase: String,
    val message: String,
    val toolName: String? = null,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class AgentUIState(
    val status: AgentStatus = AgentStatus.IDLE,
    val goal: String = "",
    val currentTurn: Int = 0,
    val maxTurns: Int = 25,
    val logs: List<AgentStepLog> = emptyList(),
    val currentReasoning: String = "",
    val highlightedRect: ElementRect? = null,
    val finalAnswer: String? = null,
    val errorMessage: String? = null,
    val pendingConfirmationMessage: String? = null
)

class AgentCoordinator(
    private val browserEngine: BrowserEngine,
    val mcpServer: MobileChromeMcpServer,
    private val llmClient: LlmClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val _uiState = MutableStateFlow(AgentUIState())
    val uiState: StateFlow<AgentUIState> = _uiState.asStateFlow()

    private var agentJob: Job? = null
    private var isPaused = false

    private val pastActionMemory = mutableListOf<String>()

    fun startGoal(goal: String, maxTurns: Int = 25) {
        cancelCurrentTask()

        _uiState.update {
            AgentUIState(
                status = AgentStatus.PLANNING,
                goal = goal,
                maxTurns = maxTurns,
                logs = listOf(
                    AgentStepLog(
                        turnNumber = 0,
                        phase = "INIT",
                        message = "Starting goal: \"$goal\""
                    )
                )
            )
        }

        pastActionMemory.clear()
        browserEngine.setAgentInteractionEnabled(true)

        agentJob = scope.launch {
            runAutonomousReActLoop(goal, maxTurns)
        }
    }

    private suspend fun runAutonomousReActLoop(goal: String, maxTurns: Int) {
        try {
            var turn = 1
            var taskFinished = false
            val mcpTools = mcpServer.listTools()

            while (turn <= maxTurns && !taskFinished) {
                while (isPaused) {
                    delay(500)
                }

                _uiState.update {
                    it.copy(
                        status = AgentStatus.OBSERVING,
                        currentTurn = turn
                    )
                }

                // 1. OBSERVE: Fetch fresh DOM snapshot with post-action stabilization
                addLog(turn, "OBSERVE", "Observing mobile page elements...")
                
                // Wait if page is currently loading from a previous navigation
                var loadWaitCount = 0
                while (browserEngine.state.value.isLoading && loadWaitCount < 10) {
                    delay(300)
                    loadWaitCount++
                }

                val snapshot = mcpServer.fetchLatestDomSnapshot(viewportOnly = true)
                val currentUrl = browserEngine.state.value.currentUrl
                val pageTitle = browserEngine.state.value.title

                val observationText = if (snapshot != null && snapshot.elements.isNotEmpty()) {
                    SecurityGuard.sanitizeSnapshotText(snapshot.treeText)
                } else {
                    "Page URL: $currentUrl, Title: \"$pageTitle\"\n(Page is loading or has no visible interactive elements yet)"
                }

                // Assemble compact history memory to avoid prompt token explosion
                val historySummary = if (pastActionMemory.isNotEmpty()) {
                    "Past Actions History:\n" + pastActionMemory.takeLast(5).joinToString("\n") + "\n\n"
                } else {
                    ""
                }

                val activePrompt = """
                    ${historySummary}Current Active Mobile Screen:
                    $observationText
                    
                    Goal: "$goal"
                    
                    Choose your next mobile MCP action now. Respond with an MCP tool call (e.g. chrome_click_element, chrome_type_text, chrome_scroll, or chrome_finish_task).
                """.trimIndent()

                val messages = listOf(
                    LlmMessage(role = "system", content = buildSystemPrompt(goal)),
                    LlmMessage(role = "user", content = activePrompt)
                )

                // 2. REASON: Send observation to LLM
                _uiState.update { it.copy(status = AgentStatus.REASONING, currentReasoning = "") }
                addLog(turn, "REASON", "AI Agent reasoning on next step...")

                val llmResponse = llmClient.chatCompletion(
                    messages = messages,
                    tools = mcpTools
                ) { reasoningChunk ->
                    _uiState.update { it.copy(currentReasoning = it.currentReasoning + reasoningChunk) }
                }

                if (!llmResponse.reasoning.isNullOrBlank()) {
                    addLog(turn, "THOUGHT", llmResponse.reasoning)
                }

                val toolCalls = llmResponse.toolCalls

                if (toolCalls.isEmpty()) {
                    val directText = llmResponse.content ?: ""
                    addLog(turn, "THOUGHT", directText)

                    // Auto-nudge if model produced conversational text instead of calling a tool
                    if (turn < maxTurns) {
                        pastActionMemory.add("Turn $turn: AI replied with text without calling a tool -> \"$directText\"")
                        turn++
                        delay(600)
                        continue
                    } else {
                        taskFinished = true
                        _uiState.update {
                            it.copy(
                                status = AgentStatus.COMPLETED,
                                finalAnswer = directText
                            )
                        }
                        break
                    }
                }

                // 3. ACT: Execute MCP Tool Calls
                _uiState.update { it.copy(status = AgentStatus.ACTING) }

                for (toolCall in toolCalls) {
                    addLog(turn, "ACTION", "Calling MCP Tool: ${toolCall.name} (args: ${toolCall.arguments})")

                    // Check security & safety on typing actions
                    if (toolCall.name.contains("type_text")) {
                        val elementId = toolCall.arguments["element_id"]?.jsonPrimitive?.content?.toIntOrNull()
                        val text = toolCall.arguments["text"]?.jsonPrimitive?.contentOrNull
                        val matchingEl = snapshot?.elements?.find { it.id == elementId }
                        val safetyCheck = SecurityGuard.checkElementInteractionSafety("type", matchingEl, text)

                        if (safetyCheck.requiresUserConfirmation) {
                            _uiState.update {
                                it.copy(
                                    status = AgentStatus.AWAITING_CONFIRMATION,
                                    pendingConfirmationMessage = safetyCheck.promptMessage
                                )
                            }
                            _uiState.update { it.copy(status = AgentStatus.ACTING, pendingConfirmationMessage = null) }
                        }
                    }

                    // Highlight element if applicable
                    val elementId = toolCall.arguments["element_id"]?.jsonPrimitive?.content?.toIntOrNull()
                    if (elementId != null && snapshot != null) {
                        val el = snapshot.elements.find { it.id == elementId }
                        if (el != null) {
                            _uiState.update { it.copy(highlightedRect = el.rect) }
                        }
                    }

                    // Dispatch tool call to in-app MCP Server
                    val mcpRequest = McpCallToolRequest(
                        name = toolCall.name,
                        arguments = toolCall.arguments
                    )
                    val mcpResult = mcpServer.callTool(mcpRequest)
                    val resultText = mcpResult.getCombinedText()

                    addLog(turn, "RESULT", resultText, isError = mcpResult.isError)

                    // Record compact action memory for next turn
                    pastActionMemory.add("Turn $turn: Executed ${toolCall.name}(${toolCall.arguments}) -> $resultText")

                    if (toolCall.name.contains("finish_task") || toolCall.name.contains("done")) {
                        taskFinished = true
                        _uiState.update {
                            it.copy(
                                status = AgentStatus.COMPLETED,
                                finalAnswer = resultText
                            )
                        }
                        addLog(turn, "FINISH", "Goal completed: $resultText")
                        break
                    }
                }

                turn++
                // Post-action stabilization delay
                delay(600)
            }

            if (!taskFinished && turn > maxTurns) {
                _uiState.update {
                    it.copy(
                        status = AgentStatus.COMPLETED,
                        finalAnswer = "Reached turn limit ($maxTurns). Last state saved."
                    )
                }
            }

        } catch (e: CancellationException) {
            addLog(0, "CANCELLED", "Agent task stopped by user.")
            _uiState.update { it.copy(status = AgentStatus.IDLE) }
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: e.toString()
            addLog(0, "ERROR", "Agent error: $errorMsg", isError = true)
            _uiState.update {
                it.copy(
                    status = AgentStatus.ERROR,
                    errorMessage = errorMsg
                )
            }
        } finally {
            browserEngine.setAgentInteractionEnabled(false)
            _uiState.update { it.copy(highlightedRect = null) }
        }
    }

    private fun buildSystemPrompt(goal: String): String {
        return """
            You are an autonomous AI Agent integrated directly into a mobile Chromium Web Browser (Android mobile touchscreen) via the Model Context Protocol (MCP).
            Your goal is: "$goal".
            
            CRITICAL RULES FOR GEMMA, GEMINI & OPEN MODELS:
            1. TOUCHSCREEN CONTEXT: You are running on an Android mobile phone. There is NO physical keyboard, NO Ctrl/Command keys, NO desktop mouse hover. Never try desktop shortcuts (Ctrl+K, Cmd+T).
            2. TOOL CALLING REQUIREMENT: On every turn, you MUST emit an MCP tool call to interact with the mobile screen.
            3. AVAILABLE MCP TOOLS:
               - `chrome_navigate(url="https://...")`: Go directly to a URL.
               - `chrome_click_element(element_id=N)`: Tap any button, link, search result, tab, or card by its ID [N].
               - `chrome_type_text(element_id=N, text="...", press_enter=true)`: Type into search bars, inputs, or textareas.
               - `chrome_scroll(direction="down")`: Scroll the mobile viewport when content is off-screen.
               - `chrome_finish_task(answer="...", success=true)`: Call this tool when the user's objective is completed.
            
            MULTI-TURN LOOP:
            Inspect the numbered elements [1], [2], [3] in the DOM snapshot and call the next tool to make progress toward "$goal". Do not stop after 1 step.
        """.trimIndent()
    }

    private fun addLog(turn: Int, phase: String, message: String, isError: Boolean = false) {
        val entry = AgentStepLog(
            turnNumber = turn,
            phase = phase,
            message = message,
            isError = isError
        )
        _uiState.update {
            it.copy(logs = it.logs + entry)
        }
    }

    fun pause() {
        isPaused = true
        _uiState.update { it.copy(status = AgentStatus.PAUSED) }
    }

    fun resume() {
        isPaused = false
        _uiState.update { it.copy(status = AgentStatus.REASONING) }
    }

    fun stop() {
        cancelCurrentTask()
        browserEngine.setAgentInteractionEnabled(false)
        _uiState.update {
            it.copy(
                status = AgentStatus.IDLE,
                highlightedRect = null
            )
        }
    }

    private fun cancelCurrentTask() {
        agentJob?.cancel()
        agentJob = null
        isPaused = false
    }
}
