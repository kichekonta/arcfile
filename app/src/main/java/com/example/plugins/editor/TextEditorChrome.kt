package com.example.plugins.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditOff
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun OpenFilesDrawerContent(
    tabs: List<EditorDocumentTab>,
    activeTabIndex: Int,
    palette: EditorThemePalette,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (String) -> Unit,
    onCreateNewTab: () -> Unit,
    onExitEditor: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = palette.drawerBg,
        modifier = Modifier
            .widthIn(max = 312.dp)
            .fillMaxHeight()
            .testTag("editor_open_files_drawer")
    ) {
        // Stylized workspace banner matching Screenshot 1
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .background(
                    Brush.linearGradient(
                        listOf(palette.drawerHeaderGradientStart, palette.drawerHeaderGradientEnd)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = size.height * 0.65f,
                    center = Offset(size.width * 0.85f, size.height * 0.15f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    radius = size.height * 0.45f,
                    center = Offset(size.width * 0.2f, size.height * 0.9f)
                )
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Open Files",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "${tabs.size} active tab${if (tabs.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.86f)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            itemsIndexed(tabs, key = { _, t -> t.tabId }) { index, tab ->
                val isActive = index == activeTabIndex
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .background(if (isActive) palette.drawerActiveRowBg else Color.Transparent)
                            .clickable { onSelectTab(index) }
                            .testTag("editor_drawer_tab_${tab.title}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(if (isActive) palette.drawerActiveAccent else Color.Transparent)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = if (tab.isDirty) "* ${tab.title}" else tab.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (isActive) palette.drawerActiveAccent else palette.drawerText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = tab.pathDisplay,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isActive) palette.drawerActiveAccent.copy(alpha = 0.85f) else palette.drawerSubtext,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onCloseTab(tab.tabId) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("editor_drawer_close_tab_${tab.title}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close ${tab.title}",
                                tint = palette.drawerSubtext,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = palette.gutterDivider.copy(alpha = 0.7f)
                    )
                }
            }
        }

        HorizontalDivider(color = palette.gutterDivider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCreateNewTab,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("editor_drawer_new_note")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Note")
            }
            TextButton(
                onClick = onExitEditor,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("editor_drawer_exit")
            ) {
                Text("Exit Editor")
            }
        }
    }
}

@Composable
fun EditorToolbarAndSubBar(
    activeTab: EditorDocumentTab?,
    state: EditorUiState,
    palette: EditorThemePalette,
    onOpenDrawer: () -> Unit,
    onToggleReadOnly: () -> Unit,
    onShare: () -> Unit,
    onNewTab: () -> Unit,
    onUndo: () -> Unit,
    onSave: () -> Unit,
    onOverflowAction: (EditorMenuCommand) -> Unit,
    onClickEncoding: () -> Unit,
    onClickHighlight: () -> Unit,
    onTogglePreview: () -> Unit
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.toolbarBg)
            .statusBarsPadding()
    ) {
        // Primary Action Row matching Screenshot 2 (7 well-spaced 48dp targets)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ToolbarIconButton(
                icon = Icons.Outlined.FilterNone,
                contentDescription = "Open files drawer",
                tint = palette.toolbarContent,
                testTag = "editor_drawer_button",
                onClick = onOpenDrawer
            )
            ToolbarIconButton(
                icon = if (activeTab?.readOnly == true) Icons.Outlined.EditOff else Icons.Outlined.Edit,
                contentDescription = if (activeTab?.readOnly == true) "Switch to Edit mode" else "Switch to Read-only mode",
                tint = palette.toolbarContent,
                testTag = "editor_readonly_button",
                onClick = onToggleReadOnly
            )
            ToolbarIconButton(
                icon = Icons.Filled.Share,
                contentDescription = "Share document",
                tint = palette.toolbarContent,
                testTag = "editor_share_button",
                onClick = onShare
            )
            ToolbarIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "New note tab",
                tint = palette.toolbarContent,
                testTag = "editor_new_tab_button",
                onClick = onNewTab
            )
            ToolbarIconButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo edit",
                tint = palette.toolbarContent,
                enabled = activeTab?.canUndo == true,
                testTag = "editor_undo_button",
                onClick = onUndo
            )
            ToolbarIconButton(
                icon = Icons.Outlined.Save,
                contentDescription = "Save document",
                tint = palette.toolbarContent,
                enabled = activeTab?.isDirty == true,
                testTag = "editor_save_button",
                onClick = onSave
            )
            Box {
                ToolbarIconButton(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = "Editor menu",
                    tint = palette.toolbarContent,
                    testTag = "editor_overflow_button",
                    onClick = { overflowExpanded = true }
                )
                TextEditorOverflowMenu(
                    expanded = overflowExpanded,
                    activeTab = activeTab,
                    state = state,
                    onDismiss = { overflowExpanded = false },
                    onCommand = { cmd ->
                        overflowExpanded = false
                        onOverflowAction(cmd)
                    }
                )
            }
        }

        // Document Info Strip matching Screenshot 2
        if (activeTab != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.subBarBg)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (activeTab.isDirty) "* ${activeTab.title}" else activeTab.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.subBarContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("editor_subbar_filename")
                )
                if (activeTab.readOnly) {
                    Text(
                        text = "READ ONLY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = palette.subBarContent.copy(alpha = 0.9f),
                        modifier = Modifier.testTag("editor_subbar_readonly_badge")
                    )
                }
                Text(
                    text = activeTab.charsetName,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.subBarContent.copy(alpha = 0.92f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onClickEncoding)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("editor_subbar_encoding")
                )
                Text(
                    text = activeTab.effectiveHighlightMode.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.subBarContent.copy(alpha = 0.92f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onClickHighlight)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("editor_subbar_highlight")
                )
                if (activeTab.supportsRichPreview) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.toolbarContent.copy(alpha = if (state.isPreviewTabVisible) 0.28f else 0.14f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onTogglePreview)
                            .testTag("editor_subbar_preview_toggle")
                    ) {
                        Text(
                            text = if (state.isPreviewTabVisible) "Source" else "Preview",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = palette.toolbarContent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    enabled: Boolean = true,
    testTag: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(22.dp)
        )
    }
}

enum class EditorMenuCommand {
    SAVE_AS,
    SAVE_ALL,
    LAST_OPENED,
    OPEN_WITH,
    CLOSE_EDITOR,
    REDO,
    LINE_BREAK,
    FIND_REPLACE,
    JUMP_START,
    JUMP_END,
    JUMP_LINE,
    PREV_LOCATION,
    NEXT_LOCATION,
    DOCUMENT_INFO,
    CHANGE_THEME,
    HIGHLIGHT,
    ENCODING,
    RUN
}

@Composable
private fun TextEditorOverflowMenu(
    expanded: Boolean,
    activeTab: EditorDocumentTab?,
    state: EditorUiState,
    onDismiss: () -> Unit,
    onCommand: (EditorMenuCommand) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 210.dp)
            .testTag("editor_overflow_menu")
    ) {
        MenuSectionHeader("File")
        OverflowItem("Save As", Icons.Outlined.SaveAs, true, "menu_save_as") {
            onCommand(EditorMenuCommand.SAVE_AS)
        }
        OverflowItem("Save All", Icons.Outlined.DoneAll, state.anyDirty, "menu_save_all") {
            onCommand(EditorMenuCommand.SAVE_ALL)
        }
        OverflowItem("Last opened", Icons.Outlined.Schedule, true, "menu_last_opened") {
            onCommand(EditorMenuCommand.LAST_OPENED)
        }
        if (activeTab?.fileItem != null) {
            OverflowItem("Open with…", Icons.AutoMirrored.Filled.OpenInNew, true, "menu_open_with") {
                onCommand(EditorMenuCommand.OPEN_WITH)
            }
        }
        OverflowItem("Close", Icons.Filled.Close, true, "menu_close_editor") {
            onCommand(EditorMenuCommand.CLOSE_EDITOR)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        MenuSectionHeader("Edit")
        OverflowItem("Redo", Icons.AutoMirrored.Filled.Redo, activeTab?.canRedo == true, "menu_redo") {
            onCommand(EditorMenuCommand.REDO)
        }
        DropdownMenuItem(
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Line break")
                    if (state.wordWrap) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Enabled",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.WrapText, contentDescription = null)
            },
            onClick = { onCommand(EditorMenuCommand.LINE_BREAK) },
            modifier = Modifier.testTag("menu_line_break")
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        MenuSectionHeader("Search")
        OverflowItem("Find/Replace...", Icons.Outlined.Search, true, "menu_find_replace") {
            onCommand(EditorMenuCommand.FIND_REPLACE)
        }
        OverflowItem("Jump to Start", Icons.Outlined.VerticalAlignTop, true, "menu_jump_start") {
            onCommand(EditorMenuCommand.JUMP_START)
        }
        OverflowItem("Jump to End", Icons.Outlined.VerticalAlignBottom, true, "menu_jump_end") {
            onCommand(EditorMenuCommand.JUMP_END)
        }
        OverflowItem("Jump to Line", Icons.Outlined.FormatListNumbered, true, "menu_jump_line") {
            onCommand(EditorMenuCommand.JUMP_LINE)
        }
        OverflowItem(
            "Prev location",
            Icons.AutoMirrored.Filled.ArrowBack,
            activeTab?.canJumpPrevLocation == true,
            "menu_prev_location"
        ) {
            onCommand(EditorMenuCommand.PREV_LOCATION)
        }
        OverflowItem(
            "Next location",
            Icons.AutoMirrored.Filled.ArrowForward,
            activeTab?.canJumpNextLocation == true,
            "menu_next_location"
        ) {
            onCommand(EditorMenuCommand.NEXT_LOCATION)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        MenuSectionHeader("View")
        OverflowItem("Document Info", Icons.Outlined.Info, activeTab != null, "menu_doc_info") {
            onCommand(EditorMenuCommand.DOCUMENT_INFO)
        }
        OverflowItem("Change Theme", Icons.Outlined.Palette, true, "menu_change_theme") {
            onCommand(EditorMenuCommand.CHANGE_THEME)
        }
        OverflowItem("Highlight", Icons.Outlined.Code, activeTab != null, "menu_highlight") {
            onCommand(EditorMenuCommand.HIGHLIGHT)
        }
        OverflowItem("Encoding", Icons.Outlined.Terminal, activeTab != null, "menu_encoding") {
            onCommand(EditorMenuCommand.ENCODING)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        MenuSectionHeader("Other")
        OverflowItem("Run", Icons.Filled.PlayArrow, activeTab != null, "menu_run") {
            onCommand(EditorMenuCommand.RUN)
        }
    }
}

@Composable
private fun MenuSectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
    )
}

@Composable
private fun OverflowItem(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                modifier = Modifier.alpha(if (enabled) 1f else 0.42f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.alpha(if (enabled) 1f else 0.42f)
            )
        },
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
fun EditorFindReplaceBar(
    findReplace: FindReplaceState,
    readOnly: Boolean,
    onQueryChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onToggleMatchCase: () -> Unit,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("editor_find_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = findReplace.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Find…") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("editor_find_input")
                )
                val matchCountText = if (findReplace.query.isEmpty()) {
                    ""
                } else if (findReplace.matchRanges.isEmpty()) {
                    "0/0"
                } else {
                    "${findReplace.activeMatchIndex + 1}/${findReplace.matchRanges.size}"
                }
                if (matchCountText.isNotEmpty()) {
                    Text(
                        text = matchCountText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("editor_find_count")
                    )
                }
                FilterChip(
                    selected = findReplace.matchCase,
                    onClick = onToggleMatchCase,
                    label = { Text("Aa") },
                    modifier = Modifier.testTag("editor_find_match_case")
                )
                IconButton(
                    onClick = onPrevMatch,
                    enabled = findReplace.matchRanges.isNotEmpty(),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("editor_find_prev")
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
                }
                IconButton(
                    onClick = onNextMatch,
                    enabled = findReplace.matchRanges.isNotEmpty(),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("editor_find_next")
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("editor_find_close")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close find bar")
                }
            }

            if (!readOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = findReplace.replacement,
                        onValueChange = onReplacementChange,
                        placeholder = { Text("Replace with…") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_replace_input")
                    )
                    TextButton(
                        onClick = onReplaceOne,
                        enabled = findReplace.matchRanges.isNotEmpty(),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("editor_replace_one_button")
                    ) {
                        Text("Replace")
                    }
                    TextButton(
                        onClick = onReplaceAll,
                        enabled = findReplace.matchRanges.isNotEmpty(),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("editor_replace_all_button")
                    ) {
                        Text("All")
                    }
                }
            }
        }
    }
}
