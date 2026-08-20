package com.chromemobile.browser.agent

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AgentTaskSession(
    val id: String = UUID.randomUUID().toString(),
    val goal: String,
    val finalAnswer: String? = null,
    val status: AgentStatus = AgentStatus.COMPLETED,
    val turns: Int = 1,
    val logs: List<AgentStepLog> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
