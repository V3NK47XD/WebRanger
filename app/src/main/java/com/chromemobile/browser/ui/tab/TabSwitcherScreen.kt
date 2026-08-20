package com.chromemobile.browser.ui.tab

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chromemobile.browser.agent.AgentStatus
import com.chromemobile.browser.tab.BrowserTab
import com.chromemobile.browser.tab.TabAiContext
import com.chromemobile.browser.tab.TabManager
import com.chromemobile.browser.ui.theme.AmoledBlack
import com.chromemobile.browser.ui.theme.AmoledBorder
import com.chromemobile.browser.ui.theme.AmoledCard
import com.chromemobile.browser.ui.theme.AmoledSurface
import com.chromemobile.browser.ui.theme.BlueishGreen
import com.chromemobile.browser.ui.theme.ErrorRed
import com.chromemobile.browser.ui.theme.HotPink
import com.chromemobile.browser.ui.theme.SuccessGreen

@Composable
fun TabSwitcherScreen(
    tabManager: TabManager,
    onCloseSwitcher: () -> Unit
) {
    val tabs by tabManager.tabs.collectAsState()
    val activeTabId by tabManager.activeTabId.collectAsState()

    var activeTooltipTabId by remember { mutableStateOf<String?>(null) }
    var isMenuExpanded by remember { mutableStateOf(false) }

    // Back gesture closes tooltip if open, otherwise closes tab switcher
    BackHandler {
        if (activeTooltipTabId != null) {
            activeTooltipTabId = null
        } else {
            onCloseSwitcher()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = AmoledBlack
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar
                Surface(
                    color = AmoledSurface,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, AmoledBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // New Tab Button
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AmoledBlack,
                            border = BorderStroke(1.dp, HotPink),
                            modifier = Modifier.clickable {
                                tabManager.createTab(url = "about:blank", selectImmediately = true)
                                onCloseSwitcher()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "New Tab", tint = HotPink, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Tab", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Title
                        Text(
                            text = "Tabs (${tabs.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        // Done / More actions
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AmoledBlack,
                                border = BorderStroke(1.dp, AmoledBorder),
                                modifier = Modifier.clickable { onCloseSwitcher() }
                            ) {
                                Text(
                                    text = "Done",
                                    color = BlueishGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            Box {
                                IconButton(onClick = { isMenuExpanded = !isMenuExpanded }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color(0xFF94A3B8))
                                }

                                DropdownMenu(
                                    expanded = isMenuExpanded,
                                    onDismissRequest = { isMenuExpanded = false },
                                    modifier = Modifier.background(Color(0xFF0F1218))
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Close all tabs", color = ErrorRed, fontSize = 13.sp)
                                            }
                                        },
                                        onClick = {
                                            isMenuExpanded = false
                                            tabManager.closeAllTabs()
                                            onCloseSwitcher()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Responsive Multi-Column Tabs Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 155.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        val isActive = tab.id == activeTabId
                        val tabState by tab.engine.state.collectAsState()

                        TabCard(
                            tab = tab,
                            title = tabState.title.ifBlank { if (tabState.currentUrl == "about:blank") "New Tab" else tabState.currentUrl },
                            url = tabState.currentUrl,
                            isActive = isActive,
                            onSelectTab = {
                                if (activeTooltipTabId == null) {
                                    tabManager.selectTab(tab.id)
                                    onCloseSwitcher()
                                }
                            },
                            onCloseTab = {
                                tabManager.closeTab(tab.id)
                            },
                            onToggleContextTooltip = {
                                activeTooltipTabId = if (activeTooltipTabId == tab.id) null else tab.id
                            }
                        )
                    }
                }
            }

            // Dismiss-Anywhere AI Context Tooltip Popover Overlay
            if (activeTooltipTabId != null) {
                val selectedTab = tabs.find { it.id == activeTooltipTabId }
                val ctx = selectedTab?.aiContext

                if (ctx != null) {
                    // Full-screen invisible touch interceptor
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                activeTooltipTabId = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Popover Card
                        Card(
                            modifier = Modifier
                                .widthIn(max = 380.dp)
                                .fillMaxWidth(0.88f)
                                .padding(16.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    activeTooltipTabId = null
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = AmoledCard),
                            border = BorderStroke(1.5.dp, HotPink),
                            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(HotPink),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Tab AI Context", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = BlueishGreen.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, BlueishGreen.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "${ctx.totalTurns} turns",
                                            color = BlueishGreen,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = AmoledBorder)
                                Spacer(modifier = Modifier.height(10.dp))

                                Text("LAST GOAL / TASK", color = HotPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = ctx.lastGoal.ifBlank { "No recorded task" },
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                if (!ctx.finalAnswer.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("SUMMARY / RESULT", color = BlueishGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = ctx.finalAnswer,
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Tap anywhere to dismiss",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabCard(
    tab: BrowserTab,
    title: String,
    url: String,
    isActive: Boolean,
    onSelectTab: () -> Unit,
    onCloseTab: () -> Unit,
    onToggleContextTooltip: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelectTab),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) AmoledCard else AmoledSurface
        ),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) BlueishGreen else AmoledBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 8.dp else 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Tab Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isActive) BlueishGreen.copy(alpha = 0.12f) else AmoledSurface)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = if (isActive) BlueishGreen else Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onCloseTab,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Tab",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Tab Preview Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
                    .background(AmoledBlack),
                contentAlignment = Alignment.Center
            ) {
                if (tab.previewBitmap != null) {
                    Image(
                        bitmap = tab.previewBitmap!!.asImageBitmap(),
                        contentDescription = "Tab Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = Color(0xFF222634),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (url == "about:blank") "New Tab" else url.substringAfter("://").substringBefore("/"),
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // AI Context Tooltip Badge
                if (tab.hasAiContext) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AmoledSurface.copy(alpha = 0.95f),
                        border = BorderStroke(1.dp, HotPink),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clickable { onToggleContextTooltip() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Context",
                                tint = HotPink,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Context",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
