package com.chromemobile.browser.ui

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.chromemobile.browser.agent.AgentCoordinator
import com.chromemobile.browser.agent.LlmConfig
import com.chromemobile.browser.engine.WebViewBrowserEngine
import com.chromemobile.browser.ui.home.NewTabHomeScreen
import com.chromemobile.browser.ui.theme.AgentAccent
import com.chromemobile.browser.ui.theme.AgentPurple
import com.chromemobile.browser.ui.theme.BluePrimary

@Composable
fun BrowserScreen(
    browserEngine: WebViewBrowserEngine,
    agentCoordinator: AgentCoordinator,
    currentLlmConfig: LlmConfig,
    onSaveLlmConfig: (LlmConfig) -> Unit
) {
    val browserState by browserEngine.state.collectAsState()
    val agentState by agentCoordinator.uiState.collectAsState()
    val consoleLogs by browserEngine.consoleLogs.collectAsState()

    var urlInput by remember { mutableStateOf("") }
    var isHomeViewActive by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showConsoleDrawer by remember { mutableStateOf(false) }
    var showMcpTesterDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Sync current URL to input when loaded
    androidx.compose.runtime.LaunchedEffect(browserState.currentUrl) {
        if (browserState.currentUrl.isNotEmpty() && browserState.currentUrl != "about:blank") {
            urlInput = browserState.currentUrl
            isHomeViewActive = false
        } else {
            isHomeViewActive = true
            urlInput = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top URL Omnibox Bar
            Surface(
                color = Color(0xFF1E293B),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            placeholder = { Text("Search or type URL...", color = Color(0xFF64748B), fontSize = 13.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (browserState.isSecure && !isHomeViewActive) Icons.Default.Lock else Icons.Default.Search,
                                    contentDescription = "Security",
                                    tint = if (browserState.isSecure && !isHomeViewActive) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BluePrimary,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    if (urlInput.isNotBlank()) {
                                        isHomeViewActive = false
                                        browserEngine.loadUrl(urlInput)
                                        keyboardController?.hide()
                                    }
                                }
                            )
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        if (!isHomeViewActive) {
                            IconButton(onClick = { browserEngine.reload() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reload",
                                    tint = Color.White
                                )
                            }
                        }

                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "AI Settings",
                                tint = AgentAccent
                            )
                        }
                    }

                    // Loading Progress Indicator
                    if (browserState.isLoading && !isHomeViewActive) {
                        LinearProgressIndicator(
                            progress = { browserState.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = BluePrimary,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }

            // Center Content: New Tab Home Screen OR Active Chromium WebView
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Chromium WebView (Rendered behind)
                AndroidView(
                    factory = {
                        browserEngine.webView.apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Highlighting Box Overlay on target element
                ElementHighlightOverlay(
                    highlightedRect = agentState.highlightedRect,
                    density = LocalDensity.current.density
                )

                // New Tab Landing Screen (Overlaid when at Home)
                if (isHomeViewActive) {
                    NewTabHomeScreen(
                        currentLlmConfig = currentLlmConfig,
                        onNavigateUrl = { targetUrl ->
                            isHomeViewActive = false
                            browserEngine.loadUrl(targetUrl)
                        },
                        onStartAgentGoal = { goal ->
                            agentCoordinator.startGoal(goal)
                        }
                    )
                }
            }

            // Bottom Navigation Bar (Opera GX / Comet style)
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (!isHomeViewActive) {
                                val didBack = browserEngine.goBack()
                                if (!didBack) isHomeViewActive = true
                            }
                        },
                        enabled = !isHomeViewActive
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (!isHomeViewActive) Color.White else Color(0xFF475569)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (!isHomeViewActive) browserEngine.goForward()
                        },
                        enabled = !isHomeViewActive && browserState.canGoForward
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (!isHomeViewActive && browserState.canGoForward) Color.White else Color(0xFF475569)
                        )
                    }

                    // Home NTP Button
                    IconButton(
                        onClick = {
                            isHomeViewActive = true
                            urlInput = ""
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = if (isHomeViewActive) AgentAccent else Color.White
                        )
                    }

                    // MCP Tool Tester Button
                    IconButton(
                        onClick = { showMcpTesterDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = "MCP Tester",
                            tint = AgentPurple
                        )
                    }

                    // Browser Console Drawer Button
                    IconButton(
                        onClick = { showConsoleDrawer = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Console",
                            tint = if (consoleLogs.any { it.level == com.chromemobile.browser.engine.ConsoleMessageEntry.LogLevel.ERROR }) Color(0xFFEF4444) else AgentAccent
                        )
                    }
                }
            }
        }

        // Floating AI Agent Overlay Sheet (Comet Style)
        AgentOverlay(
            uiState = agentState,
            onStartGoal = { goal -> agentCoordinator.startGoal(goal) },
            onPause = { agentCoordinator.pause() },
            onResume = { agentCoordinator.resume() },
            onStop = { agentCoordinator.stop() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 54.dp)
        )

        // In-App Settings Dialog
        if (showSettingsDialog) {
            SettingsDialog(
                currentConfig = currentLlmConfig,
                onSave = onSaveLlmConfig,
                onDismiss = { showSettingsDialog = false }
            )
        }

        // In-App Browser Console Drawer
        if (showConsoleDrawer) {
            ConsoleDrawer(
                logs = consoleLogs,
                onExecuteJs = { code ->
                    browserEngine.evaluateJavascript(code) { result ->
                        // Evaluated result logged to console
                    }
                },
                onClearLogs = { browserEngine.clearConsoleLogs() },
                onDismiss = { showConsoleDrawer = false }
            )
        }

        // In-App Manual MCP Tool Tester Dialog
        if (showMcpTesterDialog) {
            McpTesterDialog(
                mcpServer = agentCoordinator.mcpServer,
                onDismiss = { showMcpTesterDialog = false }
            )
        }
    }
}
