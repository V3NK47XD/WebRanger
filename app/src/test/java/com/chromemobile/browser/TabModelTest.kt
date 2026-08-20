package com.chromemobile.browser

import com.chromemobile.browser.agent.AgentStatus
import com.chromemobile.browser.agent.AgentStepLog
import com.chromemobile.browser.tab.TabAiContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabModelTest {

    @Test
    fun testTabAiContextSerialization() {
        val context = TabAiContext(
            lastGoal = "Find quantum computing articles on Wikipedia",
            finalAnswer = "Quantum computing harnesses superposition and entanglement.",
            totalTurns = 3,
            executionLogs = listOf(
                AgentStepLog(turnNumber = 1, phase = "ACTION", toolName = "navigate", message = "Navigated to wikipedia.org"),
                AgentStepLog(turnNumber = 2, phase = "RESULT", toolName = "navigate", message = "Loaded page"),
                AgentStepLog(turnNumber = 3, phase = "FINISH", message = "Summary created")
            )
        )

        val json = Json { ignoreUnknownKeys = true }
        val serialized = json.encodeToString(context)
        val deserialized = json.decodeFromString<TabAiContext>(serialized)

        assertEquals(context.lastGoal, deserialized.lastGoal)
        assertEquals(context.finalAnswer, deserialized.finalAnswer)
        assertEquals(3, deserialized.totalTurns)
        assertEquals(3, deserialized.executionLogs.size)
    }

    @Test
    fun testTabAiContextDefaults() {
        val context = TabAiContext(lastGoal = "Summarize news")
        assertEquals("Summarize news", context.lastGoal)
        assertEquals(null, context.finalAnswer)
        assertEquals(0, context.totalTurns)
        assertTrue(context.executionLogs.isEmpty())
    }
}
