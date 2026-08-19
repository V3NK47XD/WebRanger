package com.chromemobile.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chromemobile.browser.agent.AgentStatus
import com.chromemobile.browser.agent.AgentStepLog
import com.chromemobile.browser.agent.AgentUIState
import com.chromemobile.browser.ui.theme.AgentAccent
import com.chromemobile.browser.ui.theme.AgentPurple
import com.chromemobile.browser.ui.theme.BluePrimary
import com.chromemobile.browser.ui.theme.ErrorRed
import com.chromemobile.browser.ui.theme.SuccessGreen
import com.chromemobile.browser.ui.theme.WarningYellow

@Composable
fun AgentOverlay(
    uiState: AgentUIState,
    onStartGoal: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var goalInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll logs to bottom when new logs arrive
    LaunchedEffect(uiState.logs.size) {
        if (uiState.logs.isNotEmpty()) {
            listState.animateScrollToItem(uiState.logs.size - 1)
        }
    }

    // Auto-expand overlay when agent starts working
    LaunchedEffect(uiState.status) {
        if (uiState.status != AgentStatus.IDLE) {
            isExpanded = true
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Status Badge & Expand/Collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AgentPurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Agent",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Comet AI Mobile Agent",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        AgentStatusBadge(uiState.status, uiState.currentTurn, uiState.maxTurns)
                    }
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Toggle Expand",
                        tint = Color.White
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (uiState.status == AgentStatus.IDLE) {
                        // Goal Input Box
                        OutlinedTextField(
                            value = goalInput,
                            onValueChange = { goalInput = it },
                            placeholder = { Text("What should the agent do on this page?", color = Color(0xFF94A3B8)) },
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
                                            onStartGoal(goalInput)
                                        }
                                    },
                                    enabled = goalInput.isNotBlank()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Send",
                                        tint = if (goalInput.isNotBlank()) AgentAccent else Color.Gray
                                    )
                                }
                            }
                        )
                    } else {
                        // Active Goal Display & Controls
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Goal: \"${uiState.goal}\"",
                                color = Color(0xFFE2E8F0),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Real-time Action Log
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .padding(8.dp)
                        ) {
                            items(uiState.logs) { log ->
                                LogEntryRow(log)
                            }
                        }

                        // Final Answer banner if completed
                        if (uiState.finalAnswer != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF065F46),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Answer / Summary:",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = uiState.finalAnswer,
                                        color = Color(0xFFD1FAE5),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action Buttons: Pause / Resume / Stop
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (uiState.status == AgentStatus.PAUSED) {
                                Button(
                                    onClick = onResume,
                                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Resume")
                                }
                            } else if (uiState.status != AgentStatus.COMPLETED && uiState.status != AgentStatus.ERROR) {
                                Button(
                                    onClick = onPause,
                                    colors = ButtonDefaults.buttonColors(containerColor = WarningYellow),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pause", color = Color.Black)
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = onStop,
                                colors = ButtonDefaults.buttonColors(containerColor = if (uiState.status == AgentStatus.COMPLETED) BluePrimary else ErrorRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    if (uiState.status == AgentStatus.COMPLETED) Icons.Default.Close else Icons.Default.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (uiState.status == AgentStatus.COMPLETED) "Done" else "Stop")
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
    val (text, color) = when (status) {
        AgentStatus.IDLE -> "Ready" to Color(0xFF94A3B8)
        AgentStatus.PLANNING -> "Planning..." to AgentPurple
        AgentStatus.OBSERVING -> "Observing DOM (Turn $currentTurn/$maxTurns)" to BluePrimary
        AgentStatus.REASONING -> "Thinking..." to AgentAccent
        AgentStatus.ACTING -> "Executing Action..." to WarningYellow
        AgentStatus.AWAITING_CONFIRMATION -> "Confirmation Required" to WarningYellow
        AgentStatus.PAUSED -> "Paused" to WarningYellow
        AgentStatus.COMPLETED -> "Goal Completed" to SuccessGreen
        AgentStatus.ERROR -> "Error" to ErrorRed
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status == AgentStatus.OBSERVING || status == AgentStatus.REASONING || status == AgentStatus.ACTING) {
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 2.dp,
                color = color
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun LogEntryRow(log: AgentStepLog) {
    val badgeColor = when (log.phase) {
        "INIT" -> AgentPurple
        "OBSERVE" -> BluePrimary
        "REASON", "THOUGHT" -> AgentAccent
        "ACTION" -> WarningYellow
        "RESULT" -> SuccessGreen
        "FINISH" -> SuccessGreen
        "ERROR" -> ErrorRed
        else -> Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = badgeColor.copy(alpha = 0.2f),
            modifier = Modifier.padding(top = 1.dp)
        ) {
            Text(
                text = log.phase,
                color = badgeColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = log.message,
            color = if (log.isError) ErrorRed else Color(0xFFE2E8F0),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 15.sp
        )
    }
}
