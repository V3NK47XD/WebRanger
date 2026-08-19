package com.chromemobile.browser

import com.chromemobile.browser.agent.AgentTools
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolsTest {

    @Test
    fun testOpenAiSchemaGeneration() {
        val clickTool = AgentTools.CLICK_ELEMENT
        val openAiSchema = clickTool.toOpenAiSchema()

        assertEquals("function", openAiSchema["type"]?.jsonPrimitive?.content)
        val fnObj = openAiSchema["function"]?.jsonObject
        assertNotNull(fnObj)
        assertEquals("click_element", fnObj?.get("name")?.jsonPrimitive?.content)
    }

    @Test
    fun testAnthropicSchemaGeneration() {
        val typeTool = AgentTools.TYPE_TEXT
        val anthropicSchema = typeTool.toAnthropicSchema()

        assertEquals("type_text", anthropicSchema["name"]?.jsonPrimitive?.content)
        val inputSchema = anthropicSchema["input_schema"]?.jsonObject
        assertNotNull(inputSchema)
        assertEquals("object", inputSchema?.get("type")?.jsonPrimitive?.content)
    }

    @Test
    fun testAllToolsListContainsCoreCapabilities() {
        val toolNames = AgentTools.ALL_TOOLS.map { it.name }
        assertTrue(toolNames.contains("navigate"))
        assertTrue(toolNames.contains("get_dom_snapshot"))
        assertTrue(toolNames.contains("take_screenshot"))
        assertTrue(toolNames.contains("click_element"))
        assertTrue(toolNames.contains("type_text"))
        assertTrue(toolNames.contains("scroll_page"))
        assertTrue(toolNames.contains("execute_console"))
        assertTrue(toolNames.contains("finish_task"))
    }
}
