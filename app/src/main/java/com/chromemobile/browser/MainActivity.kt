package com.chromemobile.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
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
import com.chromemobile.browser.password.PasswordManager
import com.chromemobile.browser.preferences.BrowserPreferences
import com.chromemobile.browser.tab.TabManager
import com.chromemobile.browser.service.WebRangerBackgroundService
import com.chromemobile.browser.ui.BrowserScreen
import com.chromemobile.browser.ui.onboarding.OnboardingScreen
import com.chromemobile.browser.ui.theme.ChromeMobileTheme

class MainActivity : ComponentActivity() {

    private lateinit var tabManager: TabManager
    private lateinit var browserEngine: WebViewBrowserEngine
    private lateinit var mcpServer: MobileChromeMcpServer
    private lateinit var llmClient: LlmClient
    private lateinit var agentCoordinator: AgentCoordinator
    private lateinit var llmPreferences: LlmPreferences
    private lateinit var browserPreferences: BrowserPreferences
    private lateinit var passwordManager: PasswordManager

    private var currentConfig by mutableStateOf(LlmConfig())
    private var isOnboardingCompleted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable modern edge-to-edge system bars
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize Browser Preferences & Password Manager
        browserPreferences = BrowserPreferences(this)
        passwordManager = PasswordManager(this)

        // Initialize Multi-Tab Manager
        tabManager = TabManager(
            context = this,
            browserPreferences = browserPreferences
        )
        browserEngine = tabManager.getActiveTab().engine

        // If intent has URL, load it in active tab
        val initialUrl = intent?.dataString
        if (!initialUrl.isNullOrEmpty()) {
            browserEngine.loadUrl(initialUrl)
        }

        // Initialize Mobile Chrome MCP Server with TabManager, Browser Engine & Credentials
        mcpServer = MobileChromeMcpServer(
            browserEngine = browserEngine,
            passwordManager = passwordManager,
            browserPreferences = browserPreferences,
            tabManager = tabManager
        )

        // Load persisted LLM Preferences and Onboarding state
        llmPreferences = LlmPreferences(this)
        currentConfig = llmPreferences.loadConfig()
        isOnboardingCompleted = llmPreferences.isOnboardingCompleted()

        // Initialize LLM Client with loaded configuration
        llmClient = LlmClient(config = currentConfig)

        // Initialize Agent Coordinator with MCP Server & TabManager
        agentCoordinator = AgentCoordinator(
            browserEngine = browserEngine,
            mcpServer = mcpServer,
            llmClient = llmClient,
            tabManager = tabManager
        )

        // Start WebRanger Background Service and Embedded MCP Server for external agents (omp, Claude Code)
        if (browserPreferences.mcpServerEnabled) {
            WebRangerBackgroundService.start(
                context = this,
                mcpServer = mcpServer,
                tabManager = tabManager,
                port = browserPreferences.mcpServerPort
            )
        }
        setContent {
            ChromeMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    Crossfade(
                        targetState = isOnboardingCompleted,
                        label = "onboarding_crossfade"
                    ) { completed ->
                        if (!completed) {
                            OnboardingScreen(
                                initialConfig = currentConfig,
                                onComplete = { finalConfig ->
                                    currentConfig = finalConfig
                                    llmPreferences.saveConfig(finalConfig)
                                    llmPreferences.setOnboardingCompleted(true)
                                    llmClient.config = finalConfig
                                    isOnboardingCompleted = true
                                }
                            )
                        } else {
                            BrowserScreen(
                                tabManager = tabManager,
                                agentCoordinator = agentCoordinator,
                                currentLlmConfig = currentConfig,
                                onSaveLlmConfig = { newConfig ->
                                    currentConfig = newConfig
                                    llmPreferences.saveConfig(newConfig)
                                    llmClient.config = newConfig
                                },
                                browserPreferences = browserPreferences,
                                passwordManager = passwordManager
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        agentCoordinator.stop()
        if (!browserPreferences.backgroundKeepAlive) {
            WebRangerBackgroundService.stop(this)
            tabManager.destroyAll()
        }
    }
}
