package com.chromemobile.browser.ui.onboarding

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chromemobile.browser.agent.LlmConfig
import com.chromemobile.browser.agent.LlmProvider
import com.chromemobile.browser.ui.theme.AgentAccent
import com.chromemobile.browser.ui.theme.AgentPurple
import com.chromemobile.browser.ui.theme.BluePrimary

@Composable
fun OnboardingScreen(
    initialConfig: LlmConfig,
    onComplete: (LlmConfig) -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var selectedProvider by remember { mutableStateOf(initialConfig.provider) }
    var apiKey by remember { mutableStateOf(initialConfig.apiKey) }
    var model by remember { mutableStateOf(initialConfig.model) }
    var baseUrl by remember { mutableStateOf(initialConfig.baseUrl) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A),
            Color(0xFF0B1120),
            Color(0xFF020617)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width } + fadeOut())
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> width } + fadeOut())
                }
            },
            label = "onboarding_steps"
        ) { currentStep ->
            if (currentStep == 1) {
                // STEP 1: WELCOME & VALUE PROPS
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(modifier = Modifier.height(20.dp))

                        // Glowing Logo Badge
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(AgentPurple, BluePrimary, Color.Transparent)
                                    )
                                )
                                .border(2.dp, AgentAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Logo",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Chrome Mobile AI",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Next-generation Chromium browser with autonomous AI agents & Model Context Protocol.",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                        )

                        // Feature Cards
                        FeatureCard(
                            icon = Icons.Default.Speed,
                            iconColor = BluePrimary,
                            title = "Chromium Web Engine",
                            description = "Hardware-accelerated rendering with full Chrome DevTools Protocol."
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        FeatureCard(
                            icon = Icons.Default.AutoAwesome,
                            iconColor = AgentPurple,
                            title = "Autonomous ReAct Agent",
                            description = "Extracts live DOM snapshots, assigns element badges, and clicks/types autonomously."
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        FeatureCard(
                            icon = Icons.Default.RocketLaunch,
                            iconColor = AgentAccent,
                            title = "Model Context Protocol (MCP)",
                            description = "Standardized MCP tools compatible with Gemini, Gemma, Claude, and OpenAI."
                        )
                    }

                    Button(
                        onClick = { step = 2 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AgentPurple)
                    ) {
                        Text(
                            text = "Configure AI Provider",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            } else {
                // STEP 2: MODEL PROVIDER & API KEY SETUP
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            IconButton(onClick = { step = 1 }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Setup AI Brain",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "Select your preferred model provider and enter your API key to activate the browser agent.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Provider Selection Grid
                        Text(
                            text = "SELECT PROVIDER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgentAccent
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val providers = listOf(
                            Triple(LlmProvider.GEMINI, "Google Gemini", "Gemini 2.0 / 3.5 Flash"),
                            Triple(LlmProvider.OPENAI, "OpenAI", "GPT-4o / GPT-4o-mini"),
                            Triple(LlmProvider.ANTHROPIC, "Anthropic Claude", "Claude 3.7 / 3.5 Sonnet"),
                            Triple(LlmProvider.OLLAMA, "Ollama / Gemma", "gemma-4-31b-it / Local"),
                            Triple(LlmProvider.MOCK, "Mock Mode", "Test without API Key")
                        )

                        providers.forEach { (prov, label, desc) ->
                            val isSelected = selectedProvider == prov
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        selectedProvider = prov
                                        model = when (prov) {
                                            LlmProvider.GEMINI -> "gemini-2.0-flash"
                                            LlmProvider.OPENAI -> "gpt-4o"
                                            LlmProvider.ANTHROPIC -> "claude-3-7-sonnet-20250219"
                                            LlmProvider.OLLAMA -> "gemma-4-31b-it"
                                            LlmProvider.MOCK -> "mock-model"
                                        }
                                        if (prov == LlmProvider.OLLAMA && baseUrl.isEmpty()) {
                                            baseUrl = "http://10.0.2.2:11434/v1"
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) AgentPurple.copy(alpha = 0.2f) else Color(0xFF1E293B),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, AgentPurple) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(text = label, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                        Text(text = desc, color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = AgentAccent, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // API Key Input
                        if (selectedProvider != LlmProvider.MOCK) {
                            Text(
                                text = "API KEY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgentAccent
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                placeholder = { Text(if (selectedProvider == LlmProvider.GEMINI) "AIzaSy..." else "sk-...", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Default.Key, contentDescription = "Key", tint = Color(0xFF94A3B8))
                                },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Paste Button
                                        IconButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                            if (!clip.isNullOrEmpty()) apiKey = clip.trim()
                                        }) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = AgentAccent)
                                        }

                                        // Visibility Toggle
                                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle",
                                                tint = Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                },
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AgentPurple,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Model Name
                        Text(
                            text = "MODEL NAME",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgentAccent
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgentPurple,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        // Quick Model Suggestion Chips
                        val suggestions = when (selectedProvider) {
                            LlmProvider.GEMINI -> listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-2.0-pro-exp")
                            LlmProvider.OPENAI -> listOf("gpt-4o", "gpt-4o-mini", "o3-mini")
                            LlmProvider.ANTHROPIC -> listOf("claude-3-7-sonnet-20250219", "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022")
                            LlmProvider.OLLAMA -> listOf("gemma-4-31b-it", "llama3.2", "qwen2.5")
                            LlmProvider.MOCK -> listOf("mock-model")
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            suggestions.forEach { chip ->
                                Surface(
                                    modifier = Modifier.clickable { model = chip },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (model == chip) AgentPurple.copy(alpha = 0.3f) else Color(0xFF1E293B)
                                ) {
                                    Text(
                                        text = chip,
                                        color = if (model == chip) AgentAccent else Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        if (selectedProvider == LlmProvider.OLLAMA) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "BASE URL (Ollama / Local Proxy)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgentAccent
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = baseUrl,
                                onValueChange = { baseUrl = it },
                                placeholder = { Text("http://10.0.2.2:11434/v1", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AgentPurple,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val finalConfig = LlmConfig(
                                provider = selectedProvider,
                                apiKey = apiKey.trim(),
                                model = model.trim(),
                                baseUrl = baseUrl.trim()
                            )
                            onComplete(finalConfig)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Launch Browser",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Text(text = description, color = Color(0xFF94A3B8), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}
