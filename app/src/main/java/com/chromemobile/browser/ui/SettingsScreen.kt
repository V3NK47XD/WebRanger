package com.chromemobile.browser.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chromemobile.browser.agent.LlmConfig
import com.chromemobile.browser.agent.LlmPreferences
import com.chromemobile.browser.agent.LlmProvider
import com.chromemobile.browser.agent.SearchEngine
import com.chromemobile.browser.engine.WebViewBrowserEngine
import com.chromemobile.browser.password.PasswordManager
import com.chromemobile.browser.password.SavedCredential
import com.chromemobile.browser.preferences.BrowserPreferences
import com.chromemobile.browser.preferences.CookiePolicy
import com.chromemobile.browser.preferences.SafeBrowsingLevel
import com.chromemobile.browser.ui.theme.AmoledBlack
import com.chromemobile.browser.ui.theme.AmoledBorder
import com.chromemobile.browser.ui.theme.AmoledCard
import com.chromemobile.browser.ui.theme.AmoledSurface
import com.chromemobile.browser.ui.theme.BlueishGreen
import com.chromemobile.browser.ui.theme.ErrorRed
import com.chromemobile.browser.ui.theme.HotPink
import com.chromemobile.browser.ui.theme.SuccessGreen

enum class SettingsSubpage {
    MAIN,
    PASSWORDS,
    SEARCH_ENGINE,
    COOKIE_POLICY,
    SAFE_BROWSING
}

@Composable
fun SettingsScreen(
    browserEngine: WebViewBrowserEngine,
    browserPreferences: BrowserPreferences,
    passwordManager: PasswordManager,
    currentLlmConfig: LlmConfig,
    onSaveLlmConfig: (LlmConfig) -> Unit,
    onClose: () -> Unit
) {
    var activeSubpage by remember { mutableStateOf(SettingsSubpage.MAIN) }
    var showClearDataDialog by remember { mutableStateOf(false) }

    // Android back gesture/button handler for settings subpages
    BackHandler {
        if (activeSubpage != SettingsSubpage.MAIN) {
            activeSubpage = SettingsSubpage.MAIN
        } else {
            onClose()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = AmoledBlack
    ) {
        when (activeSubpage) {
            SettingsSubpage.MAIN -> {
                MainSettingsList(
                    browserEngine = browserEngine,
                    browserPreferences = browserPreferences,
                    passwordManager = passwordManager,
                    currentLlmConfig = currentLlmConfig,
                    onSaveLlmConfig = onSaveLlmConfig,
                    onNavigateSubpage = { activeSubpage = it },
                    onOpenClearData = { showClearDataDialog = true },
                    onClose = onClose
                )
            }
            SettingsSubpage.PASSWORDS -> {
                PasswordManagerScreen(
                    passwordManager = passwordManager,
                    browserPreferences = browserPreferences,
                    onBack = { activeSubpage = SettingsSubpage.MAIN }
                )
            }
            SettingsSubpage.SEARCH_ENGINE -> {
                SearchEngineSelectorScreen(
                    browserPreferences = browserPreferences,
                    browserEngine = browserEngine,
                    onBack = { activeSubpage = SettingsSubpage.MAIN }
                )
            }
            SettingsSubpage.COOKIE_POLICY -> {
                CookiePolicySelectorScreen(
                    browserPreferences = browserPreferences,
                    browserEngine = browserEngine,
                    onBack = { activeSubpage = SettingsSubpage.MAIN }
                )
            }
            SettingsSubpage.SAFE_BROWSING -> {
                SafeBrowsingSelectorScreen(
                    browserPreferences = browserPreferences,
                    onBack = { activeSubpage = SettingsSubpage.MAIN }
                )
            }
        }

        if (showClearDataDialog) {
            ClearBrowsingDataDialog(
                browserEngine = browserEngine,
                passwordManager = passwordManager,
                onDismiss = { showClearDataDialog = false }
            )
        }
    }
}

@Composable
private fun MainSettingsList(
    browserEngine: WebViewBrowserEngine,
    browserPreferences: BrowserPreferences,
    passwordManager: PasswordManager,
    currentLlmConfig: LlmConfig,
    onSaveLlmConfig: (LlmConfig) -> Unit,
    onNavigateSubpage: (SettingsSubpage) -> Unit,
    onOpenClearData: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val llmPreferences = remember { LlmPreferences(context) }
    var zoomFactor by remember { mutableIntStateOf(browserPreferences.zoomFactor) }
    var sharePasswordsWithLlm by remember { mutableStateOf(browserPreferences.sharePasswordsWithLlm) }
    var savePasswordsEnabled by remember { mutableStateOf(browserPreferences.savePasswordsEnabled) }
    var autoSignInEnabled by remember { mutableStateOf(browserPreferences.autoSignInEnabled) }
    var javascriptEnabled by remember { mutableStateOf(browserPreferences.javascriptEnabled) }
    var domStorageEnabled by remember { mutableStateOf(browserPreferences.domStorageEnabled) }
    var popupsEnabled by remember { mutableStateOf(browserPreferences.popupsEnabled) }
    var doNotTrack by remember { mutableStateOf(browserPreferences.doNotTrack) }
    var httpsFirstMode by remember { mutableStateOf(browserPreferences.httpsFirstMode) }
    var desktopSiteMode by remember { mutableStateOf(browserPreferences.desktopSiteMode) }
    var forceDarkMode by remember { mutableStateOf(browserPreferences.forceDarkMode) }
    var loadImages by remember { mutableStateOf(browserPreferences.loadsImagesAutomatically) }

    // AI Config State
    var selectedProvider by remember { mutableStateOf(currentLlmConfig.provider) }
    var apiKey by remember { mutableStateOf(currentLlmConfig.apiKey) }
    var model by remember { mutableStateOf(currentLlmConfig.model) }
    var baseUrl by remember { mutableStateOf(currentLlmConfig.baseUrl) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var isProviderDropdownExpanded by remember { mutableStateOf(false) }

    var mcpServerEnabled by remember { mutableStateOf(browserPreferences.mcpServerEnabled) }
    var backgroundKeepAlive by remember { mutableStateOf(browserPreferences.backgroundKeepAlive) }
    var mcpServerPort by remember { mutableStateOf(browserPreferences.mcpServerPort.toString()) }
    val clipboardManager = LocalClipboardManager.current
    val savedCredsCount = remember(passwordManager) { passwordManager.getAllCredentials().size }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .background(AmoledBlack)
        ) {
            // App Bar
            Surface(
                color = AmoledSurface,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Scrollable settings list
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {

                // Section: Basics
                SettingsSectionHeader(title = "Basics")

                SettingsNavigationRow(
                    icon = Icons.Default.Search,
                    title = "Search engine",
                    subtitle = browserPreferences.searchEngine.displayName,
                    tint = BlueishGreen,
                    onClick = { onNavigateSubpage(SettingsSubpage.SEARCH_ENGINE) }
                )

                SettingsNavigationRow(
                    icon = Icons.Default.Key,
                    title = "Password Manager",
                    subtitle = "$savedCredsCount saved passwords",
                    tint = HotPink,
                    onClick = { onNavigateSubpage(SettingsSubpage.PASSWORDS) }
                )

                SettingsSwitchRow(
                    icon = Icons.Default.Lock,
                    title = "Offer to save passwords",
                    subtitle = "Save passwords for sites automatically",
                    checked = savePasswordsEnabled,
                    onCheckedChange = {
                        savePasswordsEnabled = it
                        browserPreferences.savePasswordsEnabled = it
                    }
                )

                SettingsSwitchRow(
                    icon = Icons.Default.Check,
                    title = "Auto Sign-in",
                    subtitle = "Automatically sign in to websites using stored credentials",
                    checked = autoSignInEnabled,
                    onCheckedChange = {
                        autoSignInEnabled = it
                        browserPreferences.autoSignInEnabled = it
                    }
                )

                HorizontalDivider(color = AmoledBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                // Section: Privacy and Security
                SettingsSectionHeader(title = "Privacy and security")

                SettingsNavigationRow(
                    icon = Icons.Default.Delete,
                    title = "Clear browsing data",
                    subtitle = "Clear history, cookies, cache, passwords & data",
                    tint = ErrorRed,
                    onClick = onOpenClearData
                )

                SettingsNavigationRow(
                    icon = Icons.Default.Cookie,
                    title = "Third-party cookies",
                    subtitle = browserPreferences.cookiePolicy.displayName,
                    tint = BlueishGreen,
                    onClick = { onNavigateSubpage(SettingsSubpage.COOKIE_POLICY) }
                )

                SettingsNavigationRow(
                    icon = Icons.Default.Shield,
                    title = "Safe Browsing",
                    subtitle = browserPreferences.safeBrowsingLevel.displayName,
                    tint = HotPink,
                    onClick = { onNavigateSubpage(SettingsSubpage.SAFE_BROWSING) }
                )

                SettingsSwitchRow(
                    icon = Icons.Default.Security,
                    title = "Always use secure connections (HTTPS)",
                    subtitle = "Upgrade navigations to HTTPS and warn before loading insecure sites",
                    checked = httpsFirstMode,
                    onCheckedChange = {
                        httpsFirstMode = it
                        browserPreferences.httpsFirstMode = it
                    }
                )

                SettingsSwitchRow(
                    icon = Icons.Default.Public,
                    title = "Send \"Do Not Track\" request",
                    subtitle = "Include a DNT header with your browsing traffic",
                    checked = doNotTrack,
                    onCheckedChange = {
                        doNotTrack = it
                        browserPreferences.doNotTrack = it
                    }
                )

                HorizontalDivider(color = AmoledBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                // Section: Accessibility & Display (Zoom factor 80% default)
                SettingsSectionHeader(title = "Accessibility & Display")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AmoledCard),
                    border = BorderStroke(1.dp, AmoledBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ZoomIn, contentDescription = null, tint = BlueishGreen, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Default Zoom Factor", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text("Sets text and page zoom scaling for all webpages", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (zoomFactor == 80) BlueishGreen.copy(alpha = 0.2f) else AmoledBlack,
                                border = BorderStroke(1.dp, if (zoomFactor == 80) BlueishGreen else AmoledBorder)
                            ) {
                                Text(
                                    text = if (zoomFactor == 80) "80% (Default)" else "$zoomFactor%",
                                    color = if (zoomFactor == 80) BlueishGreen else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Slider(
                            value = zoomFactor.toFloat(),
                            onValueChange = {
                                zoomFactor = it.toInt()
                                browserPreferences.zoomFactor = zoomFactor
                                browserEngine.setZoomFactor(zoomFactor)
                            },
                            valueRange = 50f..200f,
                            steps = 14,
                            colors = SliderDefaults.colors(
                                thumbColor = HotPink,
                                activeTrackColor = BlueishGreen,
                                inactiveTrackColor = AmoledBorder
                            )
                        )

                        // Quick zoom preset chips (Scrollable & Responsive)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(50, 75, 80, 100, 125, 150, 175, 200).forEach { preset ->
                                val isSelected = zoomFactor == preset
                                Surface(
                                    modifier = Modifier.clickable {
                                        zoomFactor = preset
                                        browserPreferences.zoomFactor = preset
                                        browserEngine.setZoomFactor(preset)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) BlueishGreen.copy(alpha = 0.2f) else AmoledBlack,
                                    border = BorderStroke(1.dp, if (isSelected) BlueishGreen else AmoledBorder)
                                ) {
                                    Text(
                                        text = if (preset == 80) "80% (Default)" else "$preset%",
                                        color = if (isSelected) BlueishGreen else Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsSwitchRow(
                    icon = Icons.Default.DesktopWindows,
                    title = "Desktop site by default",
                    subtitle = "Request desktop version of websites",
                    checked = desktopSiteMode,
                    onCheckedChange = {
                        desktopSiteMode = it
                        browserPreferences.desktopSiteMode = it
                        browserEngine.applyPreferences(browserPreferences)
                    }
                )

                SettingsSwitchRow(
                    icon = Icons.Default.DarkMode,
                    title = "Dark theme for web contents",
                    subtitle = "Apply dark mode styling to webpages automatically",
                    checked = forceDarkMode,
                    onCheckedChange = {
                        forceDarkMode = it
                        browserPreferences.forceDarkMode = it
                        browserEngine.applyPreferences(browserPreferences)
                    }
                )

                HorizontalDivider(color = AmoledBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                // Section: Site Settings
                SettingsSectionHeader(title = "Site settings & Permissions")

                SettingsSwitchRow(
                    icon = Icons.Default.Code,
                    title = "JavaScript",
                    subtitle = "Allowed (recommended for interactive sites)",
                    checked = javascriptEnabled,
                    onCheckedChange = {
                        javascriptEnabled = it
                        browserPreferences.javascriptEnabled = it
                        browserEngine.applyPreferences(browserPreferences)
                    }
                )

                SettingsSwitchRow(
                    icon = Icons.Default.Storage,
                    title = "DOM & Local Storage",
                    subtitle = "Allow websites to store client-side app data",
                    checked = domStorageEnabled,
                    onCheckedChange = {
                        domStorageEnabled = it
                        browserPreferences.domStorageEnabled = it
                        browserEngine.applyPreferences(browserPreferences)
                    }
                )

                SettingsSwitchRow(
                    icon = Icons.Default.Language,
                    title = "Pop-ups and redirects",
                    subtitle = if (popupsEnabled) "Allowed" else "Blocked (recommended)",
                    checked = popupsEnabled,
                    onCheckedChange = {
                        popupsEnabled = it
                        browserPreferences.popupsEnabled = it
                        browserEngine.applyPreferences(browserPreferences)
                    }
                )

                HorizontalDivider(color = AmoledBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                // Section: AI Agent & MCP Server
                SettingsSectionHeader(title = "AI Agent & MCP Toolcalling")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AmoledCard),
                    border = BorderStroke(1.dp, AmoledBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Password sharing toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = HotPink, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Allow AI & MCP tools to access saved passwords",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "When enabled, the autonomous AI agent and DevTools MCP server can read stored passwords and autofill login forms via toolcalls.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Switch(
                                checked = sharePasswordsWithLlm,
                                onCheckedChange = {
                                    sharePasswordsWithLlm = it
                                    browserPreferences.sharePasswordsWithLlm = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AmoledBlack,
                                    checkedTrackColor = HotPink
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = AmoledBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Model Provider Dropdown
                        Text("LLM Model Provider", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isProviderDropdownExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                color = AmoledBlack,
                                border = BorderStroke(1.dp, AmoledBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = selectedProvider.name, color = Color.White, fontWeight = FontWeight.Medium)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select", tint = Color.White)
                                }
                            }

                            DropdownMenu(
                                expanded = isProviderDropdownExpanded,
                                onDismissRequest = { isProviderDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF0F1218))
                            ) {
                                LlmProvider.values().forEach { provider ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = provider.name,
                                                color = if (selectedProvider == provider) BlueishGreen else Color.White
                                            )
                                        },
                                        onClick = {
                                            selectedProvider = provider
                                            isProviderDropdownExpanded = false
                                            val suggestedModel = llmPreferences.getDefaultModelForProvider(provider)
                                            model = suggestedModel
                                            if (provider == LlmProvider.OLLAMA && baseUrl.isEmpty()) {
                                                baseUrl = "http://10.0.2.2:11434/v1"
                                            }
                                            onSaveLlmConfig(
                                                LlmConfig(
                                                    provider = selectedProvider,
                                                    apiKey = apiKey.trim(),
                                                    model = model.trim(),
                                                    baseUrl = baseUrl.trim()
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // API Key Field
                        if (selectedProvider != LlmProvider.MOCK) {
                            Text("API Key", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = {
                                    apiKey = it
                                    onSaveLlmConfig(
                                        LlmConfig(
                                            provider = selectedProvider,
                                            apiKey = apiKey.trim(),
                                            model = model.trim(),
                                            baseUrl = baseUrl.trim()
                                        )
                                    )
                                },
                                placeholder = { Text("sk-...", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = "API Key", tint = HotPink) },
                                trailingIcon = {
                                    IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                        Icon(
                                            imageVector = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle Visibility",
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }
                                },
                                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = HotPink,
                                    unfocusedBorderColor = AmoledBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Model Name Field
                        Text("Model Name", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = model,
                            onValueChange = {
                                model = it
                                onSaveLlmConfig(
                                    LlmConfig(
                                        provider = selectedProvider,
                                        apiKey = apiKey.trim(),
                                        model = model.trim(),
                                        baseUrl = baseUrl.trim()
                                    )
                                )
                            },
                            placeholder = { Text("e.g. gpt-4o, claude-3-7-sonnet", color = Color(0xFF64748B)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BlueishGreen,
                                unfocusedBorderColor = AmoledBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        // Model Chips & Custom Model Management
                        val providerModels = remember(selectedProvider) { llmPreferences.getModelsForProvider(selectedProvider) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            providerModels.forEach { suggested ->
                                val isSelected = model == suggested
                                Surface(
                                    modifier = Modifier.clickable {
                                        model = suggested
                                        onSaveLlmConfig(
                                            LlmConfig(
                                                provider = selectedProvider,
                                                apiKey = apiKey.trim(),
                                                model = suggested,
                                                baseUrl = baseUrl.trim()
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) HotPink.copy(alpha = 0.2f) else AmoledBlack,
                                    border = BorderStroke(1.dp, if (isSelected) HotPink else AmoledBorder)
                                ) {
                                    Text(
                                        text = suggested,
                                        color = if (isSelected) HotPink else Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        if (model.isNotBlank() && !providerModels.contains(model.trim())) {
                            Surface(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .clickable {
                                        llmPreferences.addCustomModel(selectedProvider, model.trim())
                                        Toast.makeText(context, "Saved custom model: ${model.trim()}", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(6.dp),
                                color = BlueishGreen.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, BlueishGreen)
                            ) {
                                Text(
                                    text = "+ Save '${model.trim()}' to ${selectedProvider.name} list",
                                    color = BlueishGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Base URL Field
                        Text("Base URL (Optional / Ollama)", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = {
                                baseUrl = it
                                onSaveLlmConfig(
                                    LlmConfig(
                                        provider = selectedProvider,
                                        apiKey = apiKey.trim(),
                                        model = model.trim(),
                                        baseUrl = baseUrl.trim()
                                    )
                                )
                            },
                            placeholder = { Text("http://10.0.2.2:11434/v1", color = Color(0xFF64748B)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BlueishGreen,
                                unfocusedBorderColor = AmoledBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }

                // Section: Localhost MCP Server for Termux / External Agents
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AmoledCard),
                    border = BorderStroke(1.dp, AmoledBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, tint = BlueishGreen, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Embedded MCP Server for Termux",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Exposes standard MCP (SSE & HTTP JSON-RPC) on 127.0.0.1 for omp, Claude Code, and terminal agents.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Switch(
                                checked = mcpServerEnabled,
                                onCheckedChange = {
                                    mcpServerEnabled = it
                                    browserPreferences.mcpServerEnabled = it
                                    if (it) {
                                        val p = mcpServerPort.toIntOrNull() ?: 8765
                                        com.chromemobile.browser.service.WebRangerBackgroundService.mcpServerRef?.let { srv ->
                                            com.chromemobile.browser.service.WebRangerBackgroundService.start(
                                                context, srv,
                                                com.chromemobile.browser.service.WebRangerBackgroundService.tabManagerRef,
                                                p
                                            )
                                        }
                                    } else {
                                        com.chromemobile.browser.service.WebRangerBackgroundService.stop(context)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AmoledBlack,
                                    checkedTrackColor = BlueishGreen
                                )
                            )
                        }

                        if (mcpServerEnabled) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = AmoledBorder, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(14.dp))

                            // Keep alive in background
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Keep Browser Active in Background",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Runs Foreground Service with WakeLock so WebView & DOM never freeze when switched to Termux.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = backgroundKeepAlive,
                                    onCheckedChange = {
                                        backgroundKeepAlive = it
                                        browserPreferences.backgroundKeepAlive = it
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = AmoledBlack,
                                        checkedTrackColor = BlueishGreen
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Port configuration
                            Text("Server Port", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = mcpServerPort,
                                onValueChange = {
                                    mcpServerPort = it.filter { ch -> ch.isDigit() }
                                    it.toIntOrNull()?.let { p ->
                                        browserPreferences.mcpServerPort = p
                                    }
                                },
                                placeholder = { Text("8765", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BlueishGreen,
                                    unfocusedBorderColor = AmoledBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Server Endpoint & Quick Copy
                            val activePort = mcpServerPort.toIntOrNull() ?: 8765
                            val sseUrl = "http://127.0.0.1:$activePort/sse"
                            val claudeCmd = "claude mcp add --transport sse webranger $sseUrl"

                            Text("Claude Code Connection Command:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(claudeCmd))
                                        Toast.makeText(context, "Copied Claude Code command!", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = AmoledBlack,
                                border = BorderStroke(1.dp, AmoledBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = claudeCmd,
                                        color = BlueishGreen,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = AmoledBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                // Section: About WebRanger
                SettingsSectionHeader(title = "About WebRanger")

                SettingsInfoRow(
                    icon = Icons.Default.Info,
                    title = "Application version",
                    value = "1.0.0 (WebRanger AI Edition)"
                )

                SettingsInfoRow(
                    icon = Icons.Default.Public,
                    title = "Chromium Engine",
                    value = "Chrome ${browserEngine.chromeVersion} (Mobile / ARM64 & x86_64)"
                )

                SettingsInfoRow(
                    icon = Icons.Default.Shield,
                    title = "Operating System",
                    value = "Android (API 26–35)"
                )
            }
        }
    }
}

// Subpage: Password Manager
@Composable
fun PasswordManagerScreen(
    passwordManager: PasswordManager,
    browserPreferences: BrowserPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var sharePasswordsWithLlm by remember { mutableStateOf(browserPreferences.sharePasswordsWithLlm) }
    var credentialsList by remember { mutableStateOf(passwordManager.getAllCredentials()) }

    val filteredCredentials = remember(searchQuery, credentialsList) {
        if (searchQuery.isBlank()) {
            credentialsList
        } else {
            credentialsList.filter {
                it.domain.contains(searchQuery, ignoreCase = true) ||
                        it.username.contains(searchQuery, ignoreCase = true) ||
                        it.title.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .background(AmoledBlack)
        ) {
            // App Bar
            Surface(color = AmoledSurface, shadowElevation = 4.dp, border = BorderStroke(1.dp, AmoledBorder)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Password Manager", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Password", tint = HotPink)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // AI Access Toggle Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (sharePasswordsWithLlm) HotPink.copy(alpha = 0.15f) else AmoledCard),
                    border = BorderStroke(1.dp, if (sharePasswordsWithLlm) HotPink else AmoledBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Share passwords with AI / MCP tools", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Allows WebRanger Agent and DevTools MCP server to read credentials and autofill forms", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                        Switch(
                            checked = sharePasswordsWithLlm,
                            onCheckedChange = {
                                sharePasswordsWithLlm = it
                                browserPreferences.sharePasswordsWithLlm = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AmoledBlack, checkedTrackColor = HotPink)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search passwords...", color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueishGreen,
                        unfocusedBorderColor = AmoledBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SAVED PASSWORDS (${filteredCredentials.size})",
                    color = BlueishGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredCredentials.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No saved passwords found", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        }
                    }
                } else {
                    filteredCredentials.forEach { credential ->
                        SavedCredentialCard(
                            credential = credential,
                            onCopyPassword = {
                                clipboardManager.setText(AnnotatedString(credential.password))
                                Toast.makeText(context, "Password copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                passwordManager.deleteCredential(credential.id)
                                credentialsList = passwordManager.getAllCredentials()
                                Toast.makeText(context, "Deleted password for ${credential.username}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCredentialDialog(
            onSave = { newCred ->
                passwordManager.saveCredential(newCred)
                credentialsList = passwordManager.getAllCredentials()
                showAddDialog = false
                Toast.makeText(context, "Saved password for ${newCred.domain}", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun SavedCredentialCard(
    credential: SavedCredential,
    onCopyPassword: () -> Unit,
    onDelete: () -> Unit
) {
    var isPasswordRevealed by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AmoledCard),
        border = BorderStroke(1.dp, AmoledBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = BlueishGreen.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = credential.domain.take(1).uppercase(),
                                color = BlueishGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = credential.title.ifBlank { credential.domain },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = credential.domain,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF94A3B8))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = AmoledBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Username
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Username", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Text(credential.username, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Password
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Password", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isPasswordRevealed) credential.password else "••••••••••••",
                        color = if (isPasswordRevealed) HotPink else Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(onClick = { isPasswordRevealed = !isPasswordRevealed }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isPasswordRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onCopyPassword, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCredentialDialog(
    onSave: (SavedCredential) -> Unit,
    onDismiss: () -> Unit
) {
    var domain by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AmoledCard),
            border = BorderStroke(1.dp, AmoledBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text("Add Password", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain (e.g. example.com)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BlueishGreen, unfocusedBorderColor = AmoledBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username / Email") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BlueishGreen, unfocusedBorderColor = AmoledBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HotPink, unfocusedBorderColor = AmoledBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Account Title (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BlueishGreen, unfocusedBorderColor = AmoledBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = AmoledBorder), shape = RoundedCornerShape(8.dp)) {
                        Text("Cancel", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (domain.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                                onSave(
                                    SavedCredential(
                                        domain = domain.trim(),
                                        username = username.trim(),
                                        password = password.trim(),
                                        title = title.trim().ifBlank { domain.trim() }
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HotPink),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save", color = AmoledBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Dialog: Clear Browsing Data
@Composable
private fun ClearBrowsingDataDialog(
    browserEngine: WebViewBrowserEngine,
    passwordManager: PasswordManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var clearHistory by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }
    var clearPasswords by remember { mutableStateOf(false) }
    var clearStorage by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AmoledCard),
            border = BorderStroke(1.dp, AmoledBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Clear browsing data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(14.dp))

                CheckboxItem(label = "Browsing history", subtitle = "Clears back/forward history", checked = clearHistory, onChecked = { clearHistory = it })
                CheckboxItem(label = "Cookies and site data", subtitle = "Signs you out of most sites", checked = clearCookies, onChecked = { clearCookies = it })
                CheckboxItem(label = "Cached images and files", subtitle = "Frees up storage and reloads fresh web assets", checked = clearCache, onChecked = { clearCache = it })
                CheckboxItem(label = "DOM & Local Storage", subtitle = "Deletes client app storage & SQLite DBs", checked = clearStorage, onChecked = { clearStorage = it })
                CheckboxItem(label = "Saved Passwords", subtitle = "Deletes all stored credentials from Password Manager", checked = clearPasswords, onChecked = { clearPasswords = it })

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = AmoledBorder), shape = RoundedCornerShape(8.dp)) {
                        Text("Cancel", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            browserEngine.clearBrowsingData(
                                clearHistory = clearHistory,
                                clearCookies = clearCookies,
                                clearCache = clearCache,
                                clearStorage = clearStorage
                            )
                            if (clearPasswords) {
                                passwordManager.clearAll()
                            }
                            Toast.makeText(context, "Browsing data cleared", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Clear Data", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckboxItem(
    label: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChecked,
            colors = CheckboxDefaults.colors(checkedColor = BlueishGreen, checkmarkColor = AmoledBlack)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
    }
}

// Subpage: Search Engine
@Composable
fun SearchEngineSelectorScreen(
    browserPreferences: BrowserPreferences,
    browserEngine: WebViewBrowserEngine,
    onBack: () -> Unit
) {
    var selectedEngine by remember { mutableStateOf(browserPreferences.searchEngine) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .background(AmoledBlack)
        ) {
            Surface(color = AmoledSurface, shadowElevation = 4.dp, border = BorderStroke(1.dp, AmoledBorder)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search Engine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                SearchEngine.values().forEach { engine ->
                    val isSelected = selectedEngine == engine
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                selectedEngine = engine
                                browserPreferences.searchEngine = engine
                                browserEngine.applyPreferences(browserPreferences)
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) BlueishGreen.copy(alpha = 0.15f) else AmoledCard),
                        border = BorderStroke(1.dp, if (isSelected) BlueishGreen else AmoledBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(engine.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(engine.searchUrl, color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = BlueishGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Subpage: Cookie Policy
@Composable
fun CookiePolicySelectorScreen(
    browserPreferences: BrowserPreferences,
    browserEngine: WebViewBrowserEngine,
    onBack: () -> Unit
) {
    var selectedPolicy by remember { mutableStateOf(browserPreferences.cookiePolicy) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .background(AmoledBlack)
        ) {
            Surface(color = AmoledSurface, shadowElevation = 4.dp, border = BorderStroke(1.dp, AmoledBorder)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Third-party Cookies", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                CookiePolicy.values().forEach { policy ->
                    val isSelected = selectedPolicy == policy
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable {
                                selectedPolicy = policy
                                browserPreferences.cookiePolicy = policy
                                browserEngine.applyPreferences(browserPreferences)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) BlueishGreen.copy(alpha = 0.15f) else AmoledCard),
                        border = BorderStroke(1.dp, if (isSelected) BlueishGreen else AmoledBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(policy.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(policy.description, color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = BlueishGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Subpage: Safe Browsing
@Composable
fun SafeBrowsingSelectorScreen(
    browserPreferences: BrowserPreferences,
    onBack: () -> Unit
) {
    var selectedLevel by remember { mutableStateOf(browserPreferences.safeBrowsingLevel) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .background(AmoledBlack)
        ) {
            Surface(color = AmoledSurface, shadowElevation = 4.dp, border = BorderStroke(1.dp, AmoledBorder)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Safe Browsing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                SafeBrowsingLevel.values().forEach { level ->
                    val isSelected = selectedLevel == level
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable {
                                selectedLevel = level
                                browserPreferences.safeBrowsingLevel = level
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) HotPink.copy(alpha = 0.15f) else AmoledCard),
                        border = BorderStroke(1.dp, if (isSelected) HotPink else AmoledBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(level.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(level.description, color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = HotPink)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper UI Components
@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = BlueishGreen,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = BlueishGreen,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (subtitle.isNotBlank()) {
                    Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (subtitle.isNotBlank()) {
                    Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AmoledBlack,
                checkedTrackColor = BlueishGreen,
                uncheckedTrackColor = AmoledBorder
            )
        )
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = BlueishGreen, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Text(text = value, color = Color(0xFF94A3B8), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
