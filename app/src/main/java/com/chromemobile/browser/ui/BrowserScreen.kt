package com.chromemobile.browser.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.key
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.chromemobile.browser.agent.AgentCoordinator
import com.chromemobile.browser.agent.AgentStatus
import com.chromemobile.browser.agent.LlmConfig
import com.chromemobile.browser.engine.WebViewBrowserEngine
import com.chromemobile.browser.password.PasswordManager
import com.chromemobile.browser.preferences.BrowserPreferences
import com.chromemobile.browser.tab.TabManager
import com.chromemobile.browser.ui.home.NewTabHomeScreen
import com.chromemobile.browser.ui.tab.TabSwitcherScreen
import com.chromemobile.browser.ui.theme.AgentAccent
import com.chromemobile.browser.ui.theme.AgentPurple
import com.chromemobile.browser.ui.theme.BluePrimary

@Composable
fun BrowserScreen(
    tabManager: TabManager,
    agentCoordinator: AgentCoordinator,
    currentLlmConfig: LlmConfig,
    onSaveLlmConfig: (LlmConfig) -> Unit,
    browserPreferences: BrowserPreferences = BrowserPreferences(LocalContext.current),
    passwordManager: PasswordManager = PasswordManager(LocalContext.current)
) {
    val tabs by tabManager.tabs.collectAsState()
    val activeTabId by tabManager.activeTabId.collectAsState()
    val activeTab = remember(activeTabId, tabs) { tabManager.getActiveTab() }
    val browserEngine = activeTab.engine

    val browserState by browserEngine.state.collectAsState()
    val agentState by agentCoordinator.uiState.collectAsState()
    val consoleLogs by browserEngine.consoleLogs.collectAsState()
    val isScrollingUp by browserEngine.isScrollingUp.collectAsState()
    val sessionHistory by agentCoordinator.sessionHistory.collectAsState()

    var urlInput by remember { mutableStateOf("") }
    var isHomeViewActive by remember { mutableStateOf(true) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showAgentOverlay by remember { mutableStateOf(false) }
    var showConsoleDrawer by remember { mutableStateOf(false) }
    var showMcpTesterDialog by remember { mutableStateOf(false) }
    var isDevMenuExpanded by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Automatically show AI overlay only when task completes or encounters an error
    androidx.compose.runtime.LaunchedEffect(agentState.status) {
        if (agentState.status == AgentStatus.COMPLETED || agentState.status == AgentStatus.ERROR) {
            showAgentOverlay = true
        }
    }

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

    // Dynamic zoom-scaled heights and font sizes
    val zoomRatio = (browserPreferences.zoomFactor / 80f).coerceIn(0.75f, 1.8f)
    val omniboxHeight = (38 * zoomRatio).coerceIn(34f, 54f).dp
    val omniboxFontSize = (12.5f * zoomRatio).coerceIn(11f, 16f).sp

    // Visibility of URL bar: expands when scrolling up, loading, or on home screen
    val isUrlBarVisible = isScrollingUp || isHomeViewActive || browserState.isLoading

    // Android Hardware / Gesture Back Navigation handler
    val isBackHandlingActive = showTabSwitcher || showSettingsScreen || showAgentOverlay || showConsoleDrawer || showMcpTesterDialog || !isHomeViewActive

    BackHandler(enabled = isBackHandlingActive) {
        when {
            showTabSwitcher -> showTabSwitcher = false
            showSettingsScreen -> showSettingsScreen = false
            showAgentOverlay -> showAgentOverlay = false
            showConsoleDrawer -> showConsoleDrawer = false
            showMcpTesterDialog -> showMcpTesterDialog = false
            !isHomeViewActive && (browserState.canGoBack || browserEngine.webView.canGoBack()) -> {
                browserEngine.goBack()
            }
            !isHomeViewActive -> {
                isHomeViewActive = true
                browserEngine.loadUrl("about:blank")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Main Webview Content Area (Edge-to-Edge with status bar padding)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                // Chromium WebView (Persistent in hierarchy per active tab)
                androidx.compose.runtime.key(activeTab.id) {
                    AndroidView(
                        factory = { ctx ->
                            (browserEngine.webView.parent as? ViewGroup)?.removeView(browserEngine.webView)
                            browserEngine.webView.apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { view ->
                            view.requestLayout()
                        },
                        onRelease = { view ->
                            (view.parent as? ViewGroup)?.removeView(view)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Visual DOM Highlight Badges Overlay for AI Agent
                ElementHighlightOverlay(
                    highlightedRect = agentState.highlightedRect,
                    density = LocalDensity.current.density,
                    modifier = Modifier.fillMaxSize()
                )

                // WebRanger New Tab Home Screen Overlay
                if (isHomeViewActive) {
                    NewTabHomeScreen(
                        currentLlmConfig = currentLlmConfig,
                        onNavigateUrl = { targetUrl ->
                            isHomeViewActive = false
                            urlInput = targetUrl
                            browserEngine.loadUrl(targetUrl)
                        },
                        onStartAgentGoal = { initialGoal ->
                            isHomeViewActive = false
                            showAgentOverlay = true
                            val currentTabId = tabManager.activeTabId.value
                            agentCoordinator.startGoal(
                                goal = initialGoal,
                                includePreviousContext = false,
                                onCompleted = { answer, logs ->
                                    tabManager.recordAiInvocation(currentTabId, initialGoal, answer, logs)
                                }
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Unified Bottom Dock (Attached Omnibox + Bottom Navbar) - Lifts with IME Keyboard
            Surface(
                color = Color(0xFF1E293B),
                shadowElevation = 14.dp,
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxWidth()
                    ) {
                        // Loading Progress Indicator (Attached directly above URL Omnibox)
                        if (browserState.isLoading && !isHomeViewActive) {
                            LinearProgressIndicator(
                                progress = { browserState.progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = BluePrimary,
                                trackColor = Color.Transparent
                            )
                        } else {
                            HorizontalDivider(color = Color(0xFF334155), thickness = 0.5.dp)
                        }

                        // Row 1: Bottom URL Omnibox Bar (Omnibox + Reload + Tab Button)
                        AnimatedVisibility(
                            visible = isUrlBarVisible,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Omnibox text field with clear outline & border
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, Color(0xFF475569)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(omniboxHeight)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (browserState.isSecure && !isHomeViewActive) Icons.Default.Lock else Icons.Default.Search,
                                            contentDescription = "Security",
                                            tint = if (browserState.isSecure && !isHomeViewActive) Color(0xFF10B981) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (urlInput.isEmpty()) {
                                                Text(
                                                    text = "Search or type URL...",
                                                    color = Color(0xFF64748B),
                                                    fontSize = omniboxFontSize,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            BasicTextField(
                                                value = urlInput,
                                                onValueChange = { urlInput = it },
                                                singleLine = true,
                                                maxLines = 1,
                                                textStyle = TextStyle(
                                                    color = Color.White,
                                                    fontSize = omniboxFontSize,
                                                    fontWeight = FontWeight.Normal
                                                ),
                                                cursorBrush = SolidColor(BluePrimary),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                                keyboardActions = KeyboardActions(
                                                    onGo = {
                                                        if (urlInput.isNotBlank()) {
                                                            isHomeViewActive = false
                                                            browserEngine.loadUrl(urlInput)
                                                            keyboardController?.hide()
                                                        }
                                                    }
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Reload Button (Outside text field, clearly visible with border)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable { browserEngine.reload() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reload Page",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Tab Button [ N ] (Replaces settings in row 1, shows active tabs count)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, if (tabs.size > 1) BluePrimary else Color(0xFF475569)),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable {
                                            tabManager.captureActiveTabThumbnail()
                                            showTabSwitcher = true
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${tabs.size}",
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Row 2: Bottom Navigation Controls (Back/Forward, Centered AI Pill, Settings & DevTools)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            // Left: Back & Forward navigation buttons with clear outlines
                            Row(
                                modifier = Modifier.align(Alignment.CenterStart),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, if (browserState.canGoBack) Color(0xFF475569) else Color(0xFF334155)),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable(enabled = browserState.canGoBack) { browserEngine.goBack() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = if (browserState.canGoBack) Color.White else Color(0xFF475569),
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, if (browserState.canGoForward) Color(0xFF475569) else Color(0xFF334155)),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable(enabled = browserState.canGoForward) { browserEngine.goForward() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Forward",
                                            tint = if (browserState.canGoForward) Color.White else Color(0xFF475569),
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }

                            // Middle: Mathematical Dead-Center Horizontal Pill for AI Agent
                            val isAgentWorking = agentState.status == AgentStatus.PLANNING ||
                                    agentState.status == AgentStatus.OBSERVING ||
                                    agentState.status == AgentStatus.REASONING ||
                                    agentState.status == AgentStatus.ACTING

                            val pillText = when {
                                !agentState.activeToolName.isNullOrBlank() -> "Tool: ${agentState.activeToolName}"
                                isAgentWorking -> when (agentState.status) {
                                    AgentStatus.REASONING -> "Thinking..."
                                    AgentStatus.OBSERVING -> "Observing..."
                                    else -> "WebRanger Active (${agentState.currentTurn})"
                                }
                                agentState.status == AgentStatus.COMPLETED -> "Completed ★"
                                agentState.status == AgentStatus.ERROR -> "Error ⚠"
                                showAgentOverlay -> "Hide AI Chat"
                                else -> "Ask WebRanger"
                            }

                            Surface(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .height(36.dp)
                                    .widthIn(min = 135.dp, max = 175.dp)
                                    .clickable { showAgentOverlay = !showAgentOverlay },
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFF0F172A),
                                border = BorderStroke(1.2.dp, if (isAgentWorking) AgentAccent else AgentPurple.copy(alpha = 0.85f)),
                                shadowElevation = if (isAgentWorking) 6.dp else 2.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = if (isAgentWorking) {
                                                Brush.horizontalGradient(listOf(AgentPurple, BluePrimary))
                                            } else {
                                                Brush.horizontalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0F172A)))
                                            }
                                        )
                                        .padding(horizontal = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isAgentWorking) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "AI Agent",
                                            tint = if (showAgentOverlay) Color.White else AgentAccent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    Text(
                                        text = pillText,
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Right: Settings Button + Combined Collapsible DevTools Menu
                            Row(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Settings Button (Moved to Row 2 Navbar)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable { showSettingsScreen = true }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings",
                                            tint = AgentAccent,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }

                                // DevTools Menu Button
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF0F172A),
                                        border = BorderStroke(1.dp, Color(0xFF334155)),
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clickable { isDevMenuExpanded = !isDevMenuExpanded }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Terminal,
                                                contentDescription = "Developer Tools Menu",
                                                tint = if (consoleLogs.isNotEmpty()) Color(0xFFF59E0B) else Color.White,
                                                modifier = Modifier.size(19.dp)
                                            )
                                            if (consoleLogs.isNotEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .align(Alignment.TopEnd)
                                                        .padding(top = 2.dp, end = 2.dp)
                                                        .background(Color(0xFFF59E0B), CircleShape)
                                                )
                                            }
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = isDevMenuExpanded,
                                        onDismissRequest = { isDevMenuExpanded = false },
                                        modifier = Modifier.background(Color(0xFF1E293B))
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Terminal,
                                                        contentDescription = null,
                                                        tint = Color(0xFFF59E0B),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = if (consoleLogs.isNotEmpty()) "Console (${consoleLogs.size})" else "Console",
                                                        color = Color.White,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            },
                                            onClick = {
                                                isDevMenuExpanded = false
                                                showConsoleDrawer = true
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Explore,
                                                        contentDescription = null,
                                                        tint = AgentAccent,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("MCP Tool Tester", color = Color.White, fontSize = 13.sp)
                                                }
                                            },
                                            onClick = {
                                                isDevMenuExpanded = false
                                                showMcpTesterDialog = true
                                            }
                                        )

                                        if (consoleLogs.isNotEmpty()) {
                                            HorizontalDivider(color = Color(0xFF334155))
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = null,
                                                            tint = Color(0xFF94A3B8),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Clear Console", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                                    }
                                                },
                                                onClick = {
                                                    isDevMenuExpanded = false
                                                    browserEngine.clearConsoleLogs()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating AI Agent Overlay Sheet (Directly above the unified bottom dock) - Lifts with IME Keyboard & Centered
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = omniboxHeight + 54.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            AgentOverlay(
                uiState = agentState,
                isExpanded = showAgentOverlay,
                sessionHistory = sessionHistory,
                currentLlmConfig = currentLlmConfig,
                onSaveLlmConfig = onSaveLlmConfig,
                onStartGoal = { goal, includeContext ->
                    val currentTabId = tabManager.activeTabId.value
                    agentCoordinator.startGoal(
                        goal = goal,
                        includePreviousContext = includeContext,
                        onCompleted = { answer, logs ->
                            tabManager.recordAiInvocation(currentTabId, goal, answer, logs)
                        }
                    )
                },
                onPause = { agentCoordinator.pause() },
                onResume = { agentCoordinator.resume() },
                onStop = { agentCoordinator.stop() },
                onClearHistory = { agentCoordinator.clearSessionHistory() },
                onDismiss = { showAgentOverlay = false }
            )
        }

        // Full Screen Tab Switcher Grid Overlay
        AnimatedVisibility(
            visible = showTabSwitcher,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            TabSwitcherScreen(
                tabManager = tabManager,
                onCloseSwitcher = { showTabSwitcher = false }
            )
        }

        // Full Screen Chrome Settings Page
        AnimatedVisibility(
            visible = showSettingsScreen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            SettingsScreen(
                browserEngine = browserEngine,
                browserPreferences = browserPreferences,
                passwordManager = passwordManager,
                currentLlmConfig = currentLlmConfig,
                onSaveLlmConfig = onSaveLlmConfig,
                onClose = { showSettingsScreen = false }
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
