package com.chromemobile.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chromemobile.browser.agent.AgentCoordinator
import com.chromemobile.browser.agent.AgentStatus
import com.chromemobile.browser.agent.AgentStepLog
import com.chromemobile.browser.agent.AgentTaskSession
import com.chromemobile.browser.agent.AgentUIState
import com.chromemobile.browser.agent.LlmConfig
import com.chromemobile.browser.agent.LlmPreferences
import com.chromemobile.browser.ui.theme.AgentAccent
import com.chromemobile.browser.ui.theme.AgentPurple
import com.chromemobile.browser.ui.theme.BluePrimary
import com.chromemobile.browser.ui.theme.ErrorRed
import com.chromemobile.browser.ui.theme.SuccessGreen
import com.chromemobile.browser.ui.theme.WarningYellow

@Composable
fun AgentOverlay(
    uiState: AgentUIState,
    isExpanded: Boolean,
    sessionHistory: List<AgentTaskSession>,
    currentLlmConfig: LlmConfig,
    onSaveLlmConfig: (LlmConfig) -> Unit,
    onStartGoal: (goal: String, includeContext: Boolean) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val llmPreferences = remember { LlmPreferences(context) }
    var goalInput by remember { mutableStateOf("") }
    var includeContext by remember { mutableStateOf(false) }
    var showHistoryDrawer by remember { mutableStateOf(false) }
    var isModelMenuExpanded by remember { mutableStateOf(false) }
    var selectedSessionView by remember { mutableStateOf<AgentTaskSession?>(null) }
    val listState = rememberLazyListState()

    val availableModels = remember(currentLlmConfig.provider) {
        llmPreferences.getModelsForProvider(currentLlmConfig.provider)
    }

    // Auto-scroll logs to bottom when new logs arrive
    LaunchedEffect(uiState.logs.size) {
        if (uiState.logs.isNotEmpty()) {
            listState.animateScrollToItem(uiState.logs.size - 1)
        }
    }

    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 460.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Main Chat & Execution Layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header: History Drawer Toggle, WebRanger Title, and Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // History Drawer button on the left
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (showHistoryDrawer) AgentPurple else Color(0xFF0F172A),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable { showHistoryDrawer = !showHistoryDrawer }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "Session History",
                                        tint = if (showHistoryDrawer) Color.White else AgentAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(AgentPurple),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Agent",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column {
                                Text(
                                    text = "WebRanger AI Agent",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                AgentStatusBadge(uiState.status, uiState.currentTurn, uiState.maxTurns)
                            }
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Middle: Active Task or Historical Session Log Content
                    val displayedLogs = selectedSessionView?.logs ?: uiState.logs
                    val displayedGoal = selectedSessionView?.goal ?: uiState.goal
                    val displayedFinalAnswer = selectedSessionView?.finalAnswer ?: uiState.finalAnswer

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (displayedGoal.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0F172A),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Task: \"$displayedGoal\"",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (selectedSessionView != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF334155),
                                            modifier = Modifier
                                                .clickable { selectedSessionView = null }
                                                .padding(start = 6.dp)
                                        ) {
                                            Text(
                                                text = "Current",
                                                color = AgentAccent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Real-time Action Log
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .padding(8.dp)
                        ) {
                            if (displayedLogs.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Ready to explore, search, or automate tasks on this page.",
                                            color = Color(0xFF64748B),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            } else {
                                items(displayedLogs) { log ->
                                    LogEntryRow(log)
                                }
                            }
                        }

                        // Final Answer banner if completed
                        if (!displayedFinalAnswer.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SuccessGreen.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "Final Answer:",
                                        color = SuccessGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = displayedFinalAnswer,
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Error Banner if error occurred
                        if (!uiState.errorMessage.isNullOrBlank() && selectedSessionView == null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ErrorRed.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Error: ${uiState.errorMessage}",
                                    color = ErrorRed,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bottom Action Area: Context Toggle, Model Selector & Continuous Input Box
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Toolbar Row: Context Memory Toggle & Upward Model Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Context Memory Toggle Button (Changes color on toggle)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (includeContext) AgentPurple.copy(alpha = 0.3f) else Color.Transparent,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (includeContext) AgentAccent else Color(0xFF334155)
                                ),
                                modifier = Modifier.clickable { includeContext = !includeContext }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = "Context Memory",
                                        tint = if (includeContext) AgentAccent else Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Context",
                                        color = if (includeContext) Color.White else Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = if (includeContext) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            // Model Selector Dropdown (Expands upwards!)
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    modifier = Modifier.clickable { isModelMenuExpanded = !isModelMenuExpanded }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = currentLlmConfig.model,
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 140.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropUp,
                                            contentDescription = "Choose Model",
                                            tint = AgentAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = isModelMenuExpanded,
                                    onDismissRequest = { isModelMenuExpanded = false },
                                    modifier = Modifier.background(Color(0xFF1E293B))
                                ) {
                                    Text(
                                        text = "${currentLlmConfig.provider.name} Models",
                                        color = AgentAccent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )

                                    availableModels.forEach { modelName ->
                                        val isSelected = currentLlmConfig.model == modelName
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = modelName,
                                                        color = if (isSelected) AgentAccent else Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                    if (isSelected) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = AgentAccent, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            },
                                            onClick = {
                                                isModelMenuExpanded = false
                                                val updatedConfig = currentLlmConfig.copy(model = modelName)
                                                onSaveLlmConfig(updatedConfig)
                                                llmPreferences.recordModelUsed(modelName)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Execution Controls or Always-Available Input Box
                        val isRunning = uiState.status == AgentStatus.PLANNING ||
                                uiState.status == AgentStatus.OBSERVING ||
                                uiState.status == AgentStatus.REASONING ||
                                uiState.status == AgentStatus.ACTING

                        if (isRunning) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (uiState.status == AgentStatus.PAUSED) {
                                    Button(
                                        onClick = onResume,
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Resume", fontSize = 12.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = onPause,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pause", fontSize = 12.sp)
                                    }
                                }

                                Button(
                                    onClick = onStop,
                                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Stop Agent", fontSize = 12.sp)
                                }
                            }
                        } else {
                            // Continuous Chat Input Field (Always accessible after task completes / errors / idle)
                            OutlinedTextField(
                                value = goalInput,
                                onValueChange = { goalInput = it },
                                placeholder = {
                                    Text(
                                        text = if (uiState.status == AgentStatus.COMPLETED) "Enter follow-up task or prompt..." else "Ask WebRanger or enter task...",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.5.sp
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AgentPurple,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (goalInput.isNotBlank()) {
                                                selectedSessionView = null
                                                onStartGoal(goalInput, includeContext)
                                                goalInput = ""
                                            }
                                        },
                                        enabled = goalInput.isNotBlank()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "Send Goal",
                                            tint = if (goalInput.isNotBlank()) AgentAccent else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // Collapsible Session History Drawer (Slides out from the Left)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showHistoryDrawer,
                    enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .fillMaxWidth(0.85f)
                            .fillMaxHeight(),
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        shadowElevation = 16.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = AgentAccent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Task Sessions", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                }
                                IconButton(onClick = { showHistoryDrawer = false }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Drawer", tint = Color(0xFF94A3B8))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // New Session Button
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AgentPurple.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, AgentPurple),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSessionView = null
                                        showHistoryDrawer = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "New Session", tint = AgentAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("+ New Task Session", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFF334155))
                            Spacer(modifier = Modifier.height(8.dp))

                            // Session List
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                if (sessionHistory.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 30.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No past task sessions yet", color = Color(0xFF64748B), fontSize = 11.sp)
                                        }
                                    }
                                } else {
                                    items(sessionHistory) { session ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selectedSessionView?.id == session.id) AgentPurple.copy(alpha = 0.2f) else Color(0xFF1E293B),
                                            border = BorderStroke(1.dp, if (selectedSessionView?.id == session.id) AgentAccent else Color(0xFF334155)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                                .clickable {
                                                    selectedSessionView = session
                                                    showHistoryDrawer = false
                                                }
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    text = session.goal,
                                                    color = Color.White,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "${session.turns} turns",
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 9.5.sp
                                                    )
                                                    Text(
                                                        text = if (session.status == AgentStatus.COMPLETED) "Completed" else "Error",
                                                        color = if (session.status == AgentStatus.COMPLETED) SuccessGreen else ErrorRed,
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (sessionHistory.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onClearHistory()
                                            selectedSessionView = null
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Clear", tint = ErrorRed, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Clear History", color = ErrorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgentStatusBadge(status: AgentStatus, currentTurn: Int, maxTurns: Int) {
    val (label, color) = when (status) {
        AgentStatus.IDLE -> "Ready" to Color(0xFF94A3B8)
        AgentStatus.PLANNING -> "Planning turn $currentTurn/$maxTurns" to AgentAccent
        AgentStatus.OBSERVING -> "Observing DOM..." to AgentAccent
        AgentStatus.REASONING -> "Reasoning..." to AgentPurple
        AgentStatus.ACTING -> "Executing action..." to BluePrimary
        AgentStatus.AWAITING_CONFIRMATION -> "Awaiting confirmation" to WarningYellow
        AgentStatus.PAUSED -> "Paused" to WarningYellow
        AgentStatus.COMPLETED -> "Task Completed" to SuccessGreen
        AgentStatus.ERROR -> "Error" to ErrorRed
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        val isWorking = status == AgentStatus.PLANNING ||
                status == AgentStatus.OBSERVING ||
                status == AgentStatus.REASONING ||
                status == AgentStatus.ACTING

        if (isWorking) {
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                color = color,
                strokeWidth = 1.5.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun LogEntryRow(log: AgentStepLog) {
    val iconColor = if (log.isError) ErrorRed else when (log.phase) {
        "TOOL", "ACTION" -> AgentPurple
        "LLM", "REASON", "THOUGHT" -> AgentAccent
        "RESULT", "FINISH" -> SuccessGreen
        else -> BluePrimary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = iconColor.copy(alpha = 0.2f),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Text(
                text = log.toolName ?: log.phase,
                color = iconColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = log.message,
            color = if (log.isError) ErrorRed else Color(0xFFCBD5E1),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
