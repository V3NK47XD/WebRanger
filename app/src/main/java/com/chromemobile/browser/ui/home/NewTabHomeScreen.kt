package com.chromemobile.browser.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chromemobile.browser.agent.LlmConfig
import com.chromemobile.browser.ui.theme.AgentAccent
import com.chromemobile.browser.ui.theme.AgentPurple
import com.chromemobile.browser.ui.theme.BluePrimary

data class WebShortcut(
    val title: String,
    val url: String,
    val icon: ImageVector,
    val gradientColors: List<Color>
)

@Composable
fun NewTabHomeScreen(
    currentLlmConfig: LlmConfig,
    onNavigateUrl: (String) -> Unit,
    onStartAgentGoal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var goalInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val shortcuts = listOf(
        WebShortcut("Wikipedia", "https://en.wikipedia.org", Icons.Default.MenuBook, listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))),
        WebShortcut("Hacker News", "https://news.ycombinator.com", Icons.Default.Newspaper, listOf(Color(0xFFF97316), Color(0xFFEA580C))),
        WebShortcut("GitHub", "https://github.com", Icons.Default.Code, listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))),
        WebShortcut("Reddit", "https://reddit.com", Icons.Default.Public, listOf(Color(0xFFEF4444), Color(0xFFDC2626))),
        WebShortcut("YouTube", "https://youtube.com", Icons.Default.OndemandVideo, listOf(Color(0xFFE11D48), Color(0xFFBE123C))),
        WebShortcut("Google", "https://google.com", Icons.Default.Search, listOf(Color(0xFF10B981), Color(0xFF059669))),
        WebShortcut("ArXiv", "https://arxiv.org", Icons.Default.Language, listOf(Color(0xFF06B6D4), Color(0xFF0891B2))),
        WebShortcut("AI Research", "https://huggingface.co", Icons.Default.SmartToy, listOf(Color(0xFFA855F7), Color(0xFF7E22CE)))
    )

    val sampleGoals = listOf(
        "Summarize top stories on Hacker News",
        "Search trending AI repos on GitHub",
        "Search flights from NYC to Tokyo on Google Flights",
        "Explain quantum computing from Wikipedia"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F172A), Color(0xFF0B1120), Color(0xFF020617))
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Engine & Active Model Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "WebRanger MCP Online • ${currentLlmConfig.model}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "WebRanger",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Text(
                text = "Where should the AI agent take you today?",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )

            // AI Action Prompt Box (Hero Card)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AgentAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Autonomous AI Agent",
                            color = AgentAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it },
                        placeholder = {
                            Text(
                                "e.g. Find cheap flights, summarize articles, search products...",
                                color = Color(0xFF64748B),
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
                                        onStartAgentGoal(goalInput.trim())
                                        goalInput = ""
                                    }
                                },
                                enabled = goalInput.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Run",
                                    tint = if (goalInput.isNotBlank()) AgentAccent else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Suggestion Chips
            Column(modifier = Modifier.fillMaxWidth()) {
                sampleGoals.forEach { sample ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable {
                                onStartAgentGoal(sample)
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AgentPurple, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = sample,
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Web Shortcuts Section
            Text(
                text = "FAVORITE SHORTCUTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )

            // Responsive Shortcuts Grid
            for (row in shortcuts.chunked(4)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (shortcut in row) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateUrl(shortcut.url) }
                                .padding(horizontal = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Brush.linearGradient(shortcut.gradientColors)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = shortcut.icon,
                                    contentDescription = shortcut.title,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = shortcut.title,
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
