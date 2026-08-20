package com.chromemobile.browser.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chromemobile.browser.agent.LlmConfig
import com.chromemobile.browser.preferences.Bookmark
import com.chromemobile.browser.preferences.BrowserPreferences
import com.chromemobile.browser.ui.theme.AmoledBlack
import com.chromemobile.browser.ui.theme.AmoledBorder
import com.chromemobile.browser.ui.theme.AmoledCard
import com.chromemobile.browser.ui.theme.BlueishGreen
import com.chromemobile.browser.ui.theme.HotPink

@Composable
fun NewTabHomeScreen(
    currentLlmConfig: LlmConfig,
    onNavigateUrl: (String) -> Unit,
    onStartAgentGoal: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { BrowserPreferences(context) }
    val keyboardController = LocalSoftwareKeyboardController.current

    var goalInput by remember { mutableStateOf("") }
    var bookmarks by remember { mutableStateOf(prefs.bookmarks) }

    // Bookmark editor state
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var editingIndex by remember { mutableStateOf(-1) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            // Wordmark
            Text(
                text = "WebRanger",
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "AI Browser",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4A5568),
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── AI Prompt Card ──────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = AmoledCard,
                border = BorderStroke(1.dp, Color(0xFF1E2635))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {

                    // Model selector chip row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = HotPink,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI Agent",
                                color = HotPink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Active model chip
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF0D1117),
                            border = BorderStroke(1.dp, Color(0xFF2D3748))
                        ) {
                            Text(
                                text = currentLlmConfig.provider.name
                                        .lowercase()
                                        .replaceFirstChar { it.uppercase() } + " · " +
                                        currentLlmConfig.model.take(20).let {
                                            if (currentLlmConfig.model.length > 20) "$it…" else it
                                        },
                                color = Color(0xFF8892A4),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Goal input
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it },
                        placeholder = {
                            Text(
                                "What should the agent do?",
                                color = Color(0xFF4A5568),
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HotPink,
                            unfocusedBorderColor = Color(0xFF1E2635),
                            cursorColor = HotPink,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (goalInput.isNotBlank()) {
                                    onStartAgentGoal(goalInput.trim())
                                    goalInput = ""
                                    keyboardController?.hide()
                                }
                            }
                        ),
                        trailingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (goalInput.isNotBlank())
                                            Brush.linearGradient(listOf(HotPink, BlueishGreen))
                                        else
                                            Brush.linearGradient(listOf(Color(0xFF1E2635), Color(0xFF1E2635)))
                                    )
                                    .clickable(enabled = goalInput.isNotBlank()) {
                                        onStartAgentGoal(goalInput.trim())
                                        goalInput = ""
                                        keyboardController?.hide()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Run",
                                    tint = if (goalInput.isNotBlank()) AmoledBlack else Color(0xFF3D4A5C),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Bookmarks ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = Color(0xFF4A5568),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "BOOKMARKS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4A5568),
                        letterSpacing = 1.5.sp
                    )
                }

                // Add bookmark
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0D1117))
                        .clickable {
                            editingBookmark = null
                            editingIndex = -1
                            showAddDialog = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add bookmark",
                        tint = BlueishGreen,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (bookmarks.isEmpty()) {
                Text(
                    "No bookmarks yet. Tap + to add one.",
                    color = Color(0xFF2D3748),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bookmarks.forEachIndexed { index, bm ->
                        BookmarkChip(
                            bookmark = bm,
                            onClick = { onNavigateUrl(bm.url) },
                            onEdit = {
                                editingBookmark = bm
                                editingIndex = index
                                showAddDialog = true
                            },
                            onDelete = {
                                bookmarks = bookmarks.toMutableList().also { it.removeAt(index) }
                                prefs.bookmarks = bookmarks
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider(color = Color(0xFF0D1117), thickness = 1.dp)
            Spacer(modifier = Modifier.height(20.dp))

            // ── Settings Button ─────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSettings() },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0A0C10),
                border = BorderStroke(1.dp, Color(0xFF1A1F2E))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF4A5568),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Browser Settings",
                        color = Color(0xFF8892A4),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // ── Add / Edit Bookmark Dialog ───────────────────────────────────────
    if (showAddDialog) {
        BookmarkDialog(
            initial = editingBookmark,
            onConfirm = { bm ->
                bookmarks = if (editingIndex >= 0) {
                    bookmarks.toMutableList().also { it[editingIndex] = bm }
                } else {
                    bookmarks + bm
                }
                prefs.bookmarks = bookmarks
                showAddDialog = false
                editingBookmark = null
                editingIndex = -1
            },
            onDismiss = {
                showAddDialog = false
                editingBookmark = null
                editingIndex = -1
            }
        )
    }
}

// ── Bookmark chip (horizontal scroll row) ────────────────────────────────────
@Composable
private fun BookmarkChip(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AmoledCard,
        border = BorderStroke(1.dp, AmoledBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Favicon letter badge
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(HotPink.copy(alpha = 0.5f), BlueishGreen.copy(alpha = 0.5f)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bookmark.title.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(7.dp))

            Text(
                text = bookmark.title,
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 90.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Edit
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable { onEdit() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = Color(0xFF4A5568),
                    modifier = Modifier.size(11.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Delete
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color(0xFF4A5568),
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

// ── Add / Edit bookmark dialog ────────────────────────────────────────────────
@Composable
private fun BookmarkDialog(
    initial: Bookmark?,
    onConfirm: (Bookmark) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = AmoledCard,
            border = BorderStroke(1.dp, AmoledBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (initial == null) "Add Bookmark" else "Edit Bookmark",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name", color = Color(0xFF4A5568), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueishGreen,
                        unfocusedBorderColor = AmoledBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = BlueishGreen
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL", color = Color(0xFF4A5568), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HotPink,
                        unfocusedBorderColor = AmoledBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = HotPink
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF4A5568))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, if (title.isNotBlank() && url.isNotBlank()) HotPink else AmoledBorder),
                        modifier = Modifier.clickable(enabled = title.isNotBlank() && url.isNotBlank()) {
                            val finalUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                            onConfirm(Bookmark(title = title.trim(), url = finalUrl))
                        }
                    ) {
                        Text(
                            text = "Save",
                            color = if (title.isNotBlank() && url.isNotBlank()) HotPink else Color(0xFF4A5568),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
