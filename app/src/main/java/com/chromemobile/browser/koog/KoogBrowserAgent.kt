package com.chromemobile.browser.koog

import com.chromemobile.browser.agent.LlmClient
import com.chromemobile.browser.engine.BrowserEngine
import com.chromemobile.browser.mcp.MobileChromeMcpServer

object KoogBrowserAgentFactory {

    /**
     * Create a fully configured Koog AI Agent with all Mobile Chrome MCP tools
     */
    fun createAgent(
        browserEngine: BrowserEngine,
        mcpServer: MobileChromeMcpServer,
        llmClient: LlmClient
    ): KoogAIAgent {
        val mcpTools = mcpServer.listTools()
        val koogTools = mcpTools.map { mcpTool ->
            KoogMcpToolAdapter(
                mcpTool = mcpTool,
                mcpServer = mcpServer
            )
        }

        return KoogAIAgent(
            name = "KoogChromeMobileAgent",
            description = "Koog-powered autonomous mobile browser agent",
            tools = koogTools,
            llmClient = llmClient,
            browserEngine = browserEngine,
            mcpServer = mcpServer
        )
    }
}
