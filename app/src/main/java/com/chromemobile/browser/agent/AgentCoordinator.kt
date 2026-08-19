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

    private val koogAgent: KoogAIAgent = KoogBrowserAgentFactory.createAgent(
        browserEngine = browserEngine,
        mcpServer = mcpServer,
        llmClient = llmClient
    )

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
                        message = "Starting Koog Agent for goal: \"$goal\""
                    )
                )
            )
        }

        browserEngine.setAgentInteractionEnabled(true)

        agentJob = scope.launch {
            try {
                val result = koogAgent.run(
                    goal = goal,
                    maxTurns = maxTurns,
                    uiStateFlow = _uiState,
                    isPausedProvider = { isPaused },
                    onTurnLog = { log ->
                        _uiState.update { it.copy(logs = it.logs + log) }
                    }
                )

                _uiState.update {
                    it.copy(
                        status = AgentStatus.COMPLETED,
                        finalAnswer = result.finalAnswer
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update {
                    it.copy(
                        status = AgentStatus.IDLE,
                        logs = it.logs + AgentStepLog(turnNumber = 0, phase = "CANCEL", message = "Koog Agent stopped by user.")
                    )
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.toString()
                _uiState.update {
                    it.copy(
                        status = AgentStatus.ERROR,
                        errorMessage = errorMsg,
                        logs = it.logs + AgentStepLog(turnNumber = 0, phase = "ERROR", message = "Koog Agent Error: $errorMsg", isError = true)
                    )
                }
            } finally {
                browserEngine.setAgentInteractionEnabled(false)
                _uiState.update { it.copy(highlightedRect = null) }
            }
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
