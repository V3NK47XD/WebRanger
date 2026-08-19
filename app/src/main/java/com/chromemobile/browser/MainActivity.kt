package com.chromemobile.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.chromemobile.browser.agent.AgentCoordinator
import com.chromemobile.browser.agent.LlmClient
import com.chromemobile.browser.agent.LlmConfig
import com.chromemobile.browser.agent.LlmPreferences
import com.chromemobile.browser.engine.WebViewBrowserEngine
import com.chromemobile.browser.mcp.MobileChromeMcpServer
import com.chromemobile.browser.ui.BrowserScreen
import com.chromemobile.browser.ui.theme.ChromeMobileTheme

class MainActivity : ComponentActivity() {

    private lateinit var browserEngine: WebViewBrowserEngine
    private lateinit var mcpServer: MobileChromeMcpServer
    private lateinit var llmClient: LlmClient
    private lateinit var agentCoordinator: AgentCoordinator
    private lateinit var llmPreferences: LlmPreferences

    private var currentConfig by mutableStateOf(LlmConfig())

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge system bars
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize Browser Engine with Chromium WebView
        browserEngine = WebViewBrowserEngine(context = this)

        // Initialize Mobile Chrome MCP Server
        mcpServer = MobileChromeMcpServer(browserEngine = browserEngine)

        // Load persisted LLM Preferences
        llmPreferences = LlmPreferences(this)
        currentConfig = llmPreferences.loadConfig()

        // Initialize LLM Client with loaded configuration
        llmClient = LlmClient(config = currentConfig)

        // Initialize Agent Coordinator with MCP Server
        agentCoordinator = AgentCoordinator(
            browserEngine = browserEngine,
            mcpServer = mcpServer,
            llmClient = llmClient
        )

        // Load starting page
        val initialUrl = intent?.dataString ?: "https://en.wikipedia.org"
        browserEngine.loadUrl(initialUrl)

        setContent {
            ChromeMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    BrowserScreen(
                        browserEngine = browserEngine,
                        agentCoordinator = agentCoordinator,
                        currentLlmConfig = currentConfig,
                        onSaveLlmConfig = { newConfig ->
                            currentConfig = newConfig
                            llmPreferences.saveConfig(newConfig)
                            llmClient.config = newConfig
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        agentCoordinator.stop()
        browserEngine.destroy()
    }
}
