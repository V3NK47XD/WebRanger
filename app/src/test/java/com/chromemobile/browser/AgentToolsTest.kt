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
        assertTrue(toolNames.contains("get_saved_credentials"))
        assertTrue(toolNames.contains("save_credential"))
        assertTrue(toolNames.contains("autofill_login"))
        assertTrue(toolNames.contains("list_tabs"))
        assertTrue(toolNames.contains("switch_tab"))
        assertTrue(toolNames.contains("create_tab"))
        assertTrue(toolNames.contains("close_tab"))
        assertTrue(toolNames.contains("get_tab_context"))
        assertTrue(toolNames.contains("finish_task"))
    }

    @Test
    fun testGetSavedCredentialsToolDefinition() {
        val tool = AgentTools.GET_SAVED_CREDENTIALS
        assertEquals("get_saved_credentials", tool.name)
        assertTrue(tool.parameters.any { it.name == "domain" })
        val openAiSchema = tool.toOpenAiSchema()
        assertNotNull(openAiSchema)
    }

    @Test
    fun testSaveCredentialToolDefinition() {
        val tool = AgentTools.SAVE_CREDENTIAL
        assertEquals("save_credential", tool.name)
        assertTrue(tool.parameters.any { it.name == "domain" && it.required })
        assertTrue(tool.parameters.any { it.name == "username" && it.required })
        assertTrue(tool.parameters.any { it.name == "password" && it.required })
    }

    @Test
    fun testAutofillLoginToolDefinition() {
        val tool = AgentTools.AUTOFILL_LOGIN
        assertEquals("autofill_login", tool.name)
        assertTrue(tool.parameters.any { it.name == "username" })
        assertTrue(tool.parameters.any { it.name == "password" })
    }

    @Test
    fun testTabToolsDefinitions() {
        assertEquals("list_tabs", AgentTools.LIST_TABS.name)
        assertEquals("switch_tab", AgentTools.SWITCH_TAB.name)
        assertTrue(AgentTools.SWITCH_TAB.parameters.any { it.name == "tab_id" && it.required })
        assertEquals("create_tab", AgentTools.CREATE_TAB.name)
        assertEquals("close_tab", AgentTools.CLOSE_TAB.name)
        assertEquals("get_tab_context", AgentTools.GET_TAB_CONTEXT.name)
    }
}
