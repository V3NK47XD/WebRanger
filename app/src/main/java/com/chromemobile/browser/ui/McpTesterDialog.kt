package com.chromemobile.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chromemobile.browser.mcp.McpCallToolRequest
import com.chromemobile.browser.mcp.McpCallToolResponse
import com.chromemobile.browser.mcp.MobileChromeMcpServer
import com.chromemobile.browser.ui.theme.AmoledBlack
import com.chromemobile.browser.ui.theme.AmoledBorder
import com.chromemobile.browser.ui.theme.AmoledCard
import com.chromemobile.browser.ui.theme.BlueishGreen
import com.chromemobile.browser.ui.theme.ErrorRed
import com.chromemobile.browser.ui.theme.HotPink
import com.chromemobile.browser.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun McpTesterDialog(
    mcpServer: MobileChromeMcpServer,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTool by remember { mutableStateOf("chrome_navigate") }
    var navigateUrl by remember { mutableStateOf("https://en.wikipedia.org") }
    var elementIdInput by remember { mutableStateOf("1") }
    var textInput by remember { mutableStateOf("Chromium") }
    var scriptInput by remember { mutableStateOf("document.title") }

    // Credential tool inputs
    var domainInput by remember { mutableStateOf("wikipedia.org") }
    var usernameInput by remember { mutableStateOf("wiki_researcher") }
    var passwordInput by remember { mutableStateOf("WikiPassword2026!") }

    var isExecuting by remember { mutableStateOf(false) }
    var lastResponse by remember { mutableStateOf<McpCallToolResponse?>(null) }
    var executionTimeMs by remember { mutableStateOf<Long?>(null) }

    val presetUrls = listOf(
        "https://en.wikipedia.org",
        "https://news.ycombinator.com",
        "https://www.google.com",
        "https://github.com/login"
    )

    val toolsList = listOf(
        "chrome_navigate",
        "chrome_get_dom_snapshot",
        "chrome_click_element",
        "chrome_type_text",
        "chrome_get_saved_credentials",
        "chrome_save_credential",
        "chrome_autofill_login"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(4.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AmoledCard),
            border = BorderStroke(1.dp, AmoledBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = "MCP Tester",
                            tint = BlueishGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MCP Tool Tester",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Text(
                    text = "Execute live Model Context Protocol (MCP) toolcalls directly on the browser engine & password manager.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Tool selector tabs (scrollable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    toolsList.forEach { tool ->
                        val isSelected = selectedTool == tool
                        val shortLabel = tool.removePrefix("chrome_")
                        Surface(
                            modifier = Modifier.clickable { selectedTool = tool },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) HotPink else AmoledBlack,
                            border = BorderStroke(1.dp, if (isSelected) HotPink else AmoledBorder)
                        ) {
                            Text(
                                text = shortLabel,
                                color = if (isSelected) AmoledBlack else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tool Parameter Inputs
                when (selectedTool) {
                    "chrome_navigate" -> {
                        Text("Destination URL", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = navigateUrl,
                            onValueChange = { navigateUrl = it },
                            placeholder = { Text("https://...", color = Color(0xFF64748B)) },
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

                        // Preset URL quick chips
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presetUrls.forEach { url ->
                                val domain = url.substringAfter("https://").substringBefore("/")
                                Surface(
                                    modifier = Modifier.clickable { navigateUrl = url },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (navigateUrl == url) BlueishGreen.copy(alpha = 0.2f) else AmoledBlack,
                                    border = BorderStroke(1.dp, if (navigateUrl == url) BlueishGreen else AmoledBorder)
                                ) {
                                    Text(
                                        text = domain,
                                        color = if (navigateUrl == url) BlueishGreen else Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    "chrome_click_element" -> {
                        Text("Element ID", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = elementIdInput,
                            onValueChange = { elementIdInput = it },
                            placeholder = { Text("1", color = Color(0xFF64748B)) },
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

                    "chrome_type_text" -> {
                        Text("Element ID", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = elementIdInput,
                            onValueChange = { elementIdInput = it },
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Text to Type", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
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

                    "chrome_get_dom_snapshot" -> {
                        Text(
                            text = "Extracts live DOM tree with all interactive element IDs [N].",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp
                        )
                    }

                    "chrome_get_saved_credentials" -> {
                        Text("Target Domain (optional)", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = domainInput,
                            onValueChange = { domainInput = it },
                            placeholder = { Text("wikipedia.org (leave empty for active page)", color = Color(0xFF64748B)) },
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

                    "chrome_save_credential" -> {
                        Text("Domain", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = domainInput,
                            onValueChange = { domainInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BlueishGreen, unfocusedBorderColor = AmoledBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Username", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BlueishGreen, unfocusedBorderColor = AmoledBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Password", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BlueishGreen, unfocusedBorderColor = AmoledBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                    }

                    "chrome_autofill_login" -> {
                        Text("Username (optional)", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            placeholder = { Text("Leave blank to auto-resolve saved account", color = Color(0xFF64748B)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BlueishGreen, unfocusedBorderColor = AmoledBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Password (optional)", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            placeholder = { Text("Leave blank to auto-resolve from Password Manager", color = Color(0xFF64748B)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BlueishGreen, unfocusedBorderColor = AmoledBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Execute Button
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isExecuting = true
                            lastResponse = null
                            val startTime = System.currentTimeMillis()

                            val args = when (selectedTool) {
                                "chrome_navigate" -> mapOf("url" to JsonPrimitive(navigateUrl))
                                "chrome_click_element" -> mapOf("element_id" to JsonPrimitive(elementIdInput.toIntOrNull() ?: 1))
                                "chrome_type_text" -> mapOf(
                                    "element_id" to JsonPrimitive(elementIdInput.toIntOrNull() ?: 1),
                                    "text" to JsonPrimitive(textInput),
                                    "press_enter" to JsonPrimitive(true)
                                )
                                "chrome_get_saved_credentials" -> mapOf("domain" to JsonPrimitive(domainInput))
                                "chrome_save_credential" -> mapOf(
                                    "domain" to JsonPrimitive(domainInput),
                                    "username" to JsonPrimitive(usernameInput),
                                    "password" to JsonPrimitive(passwordInput)
                                )
                                "chrome_autofill_login" -> mapOf(
                                    "username" to JsonPrimitive(usernameInput),
                                    "password" to JsonPrimitive(passwordInput)
                                )
                                else -> emptyMap()
                            }

                            val request = McpCallToolRequest(name = selectedTool, arguments = args)
                            val response = mcpServer.callTool(request)
                            val elapsed = System.currentTimeMillis() - startTime

                            lastResponse = response
                            executionTimeMs = elapsed
                            isExecuting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedTool.contains("credential") || selectedTool.contains("autofill")) HotPink else BlueishGreen),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isExecuting
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AmoledBlack, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Executing MCP Toolcall...", color = AmoledBlack, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Execute MCP Tool: $selectedTool", color = AmoledBlack, fontWeight = FontWeight.Bold)
                    }
                }

                // Response View
                if (lastResponse != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "MCP Output (${executionTimeMs}ms):",
                        fontWeight = FontWeight.Bold,
                        color = if (lastResponse?.isError == true) ErrorRed else SuccessGreen,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AmoledBlack,
                        border = BorderStroke(1.dp, AmoledBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = lastResponse?.getCombinedText() ?: "",
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}
