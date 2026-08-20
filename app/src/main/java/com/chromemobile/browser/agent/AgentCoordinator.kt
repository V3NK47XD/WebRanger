package com.chromemobile.browser.agent

import com.chromemobile.browser.engine.BrowserEngine
import com.chromemobile.browser.koog.KoogAIAgent
import com.chromemobile.browser.koog.KoogBrowserAgentFactory
import com.chromemobile.browser.mcp.MobileChromeMcpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

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
    val activeToolName: String? = null,
    val highlightedRect: ElementRect? = null,
    val finalAnswer: String? = null,
    val errorMessage: String? = null,
    val pendingConfirmationMessage: String? = null
)

class AgentCoordinator(
    private val browserEngine: BrowserEngine,
    val mcpServer: MobileChromeMcpServer,
    val llmClient: LlmClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val _uiState = MutableStateFlow(AgentUIState())
    val uiState: StateFlow<AgentUIState> = _uiState.asStateFlow()

    private val _sessionHistory = MutableStateFlow<List<AgentTaskSession>>(emptyList())
    val sessionHistory: StateFlow<List<AgentTaskSession>> = _sessionHistory.asStateFlow()

    private var agentJob: Job? = null
    private var isPaused = false

    private val koogAgent: KoogAIAgent = KoogBrowserAgentFactory.createAgent(
        browserEngine = browserEngine,
        mcpServer = mcpServer,
        llmClient = llmClient
    )

    fun startGoal(
        goal: String,
        maxTurns: Int = 25,
        includePreviousContext: Boolean = false
    ) {
        cancelCurrentTask()

        val initialLogs = listOf(
            AgentStepLog(
                turnNumber = 0,
                phase = "INIT",
                message = "Starting WebRanger Agent for: \"$goal\""
            )
        )

        _uiState.update {
            AgentUIState(
                status = AgentStatus.PLANNING,
                goal = goal,
                maxTurns = maxTurns,
                logs = initialLogs
            )
        }

        browserEngine.setAgentInteractionEnabled(true)

        val previousContextStrings = if (includePreviousContext) {
            _sessionHistory.value.take(4).map { session ->
                "Previous Task: \"${session.goal}\" -> Result: ${session.finalAnswer ?: "Finished"}"
            }
        } else {
            emptyList()
        }

        agentJob = scope.launch {
            try {
                val result = koogAgent.run(
                    goal = goal,
                    maxTurns = maxTurns,
                    uiStateFlow = _uiState,
                    isPausedProvider = { isPaused },
                    initialContextHistory = previousContextStrings,
                    onTurnLog = { log ->
                        _uiState.update { it.copy(logs = it.logs + log) }
                    }
                )

                val finalStatus = if (result.success) AgentStatus.COMPLETED else AgentStatus.ERROR
                val finalAnswerText = result.finalAnswer.ifBlank { "Task finished" }

                _uiState.update {
                    it.copy(
                        status = finalStatus,
                        finalAnswer = finalAnswerText,
                        highlightedRect = null,
                        activeToolName = null
                    )
                }

                // Record completed task into session history
                val sessionRecord = AgentTaskSession(
                    goal = goal,
                    finalAnswer = finalAnswerText,
                    status = finalStatus,
                    turns = result.totalTurns,
                    logs = result.logs
                )
                _sessionHistory.update { listOf(sessionRecord) + it }

            } catch (e: CancellationException) {
                _uiState.update {
                    it.copy(
                        status = AgentStatus.IDLE,
                        highlightedRect = null,
                        activeToolName = null
                    )
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.toString()
                _uiState.update {
                    it.copy(
                        status = AgentStatus.ERROR,
                        errorMessage = errorMsg,
                        highlightedRect = null,
                        activeToolName = null
                    )
                }

                val sessionRecord = AgentTaskSession(
                    goal = goal,
                    finalAnswer = "Error: $errorMsg",
                    status = AgentStatus.ERROR,
                    turns = 1,
                    logs = _uiState.value.logs
                )
                _sessionHistory.update { listOf(sessionRecord) + it }
            } finally {
                browserEngine.setAgentInteractionEnabled(false)
            }
        }
    }

    fun clearSessionHistory() {
        _sessionHistory.value = emptyList()
    }

    fun pause() {
        isPaused = true
        _uiState.update { it.copy(status = AgentStatus.PAUSED) }
    }

    fun resume() {
        isPaused = false
        _uiState.update { it.copy(status = AgentStatus.PLANNING) }
    }

    fun stop() {
        cancelCurrentTask()
        _uiState.update {
            it.copy(
                status = AgentStatus.IDLE,
                highlightedRect = null,
                activeToolName = null
            )
        }
        browserEngine.setAgentInteractionEnabled(false)
    }

    private fun cancelCurrentTask() {
        agentJob?.cancel()
        agentJob = null
        isPaused = false
    }
}
