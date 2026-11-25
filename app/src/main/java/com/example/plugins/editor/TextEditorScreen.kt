package com.example.plugins.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.FileItem
import com.example.ui.components.common.ConfigureEdgeToEdgeDialogWindow
import com.example.ui.components.common.HtmlPreviewPanel
import com.example.ui.components.common.MarkdownPreviewPanel
import com.example.ui.theme.RobotoMonoFontFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TextEditorPluginScreen(
    controller: EditorSessionController,
    onShareFile: (FileItem?) -> Unit,
    onOpenWithFile: (FileItem) -> Unit,
    onDismiss: () -> Unit
) {
    val state by controller.uiState.collectAsState()
    val activeTab = state.activeTab
    val palette = EditorThemePalette.resolve(state.themePreset, MaterialTheme.colorScheme)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val uiScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        controller.clearStatusMessage()
    }

    Dialog(
        onDismissRequest = { controller.requestExitEditor(onDismiss) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ConfigureEdgeToEdgeDialogWindow(matchParentSize = true)

        BackHandler {
            when {
                drawerState.isOpen -> uiScope.launch { drawerState.close() }
                state.findReplace.isVisible -> controller.closeFindReplace()
                else -> controller.requestExitEditor(onDismiss)
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                OpenFilesDrawerContent(
                    tabs = state.tabs,
                    activeTabIndex = state.activeTabIndex,
                    palette = palette,
                    onSelectTab = { index ->
                        controller.selectTab(index)
                        uiScope.launch { drawerState.close() }
                    },
                    onCloseTab = { tabId ->
                        controller.requestCloseTab(tabId) {
                            onDismiss()
                        }
                    },
                    onCreateNewTab = {
                        controller.createNewUntitledTab()
                        uiScope.launch { drawerState.close() }
                    },
                    onExitEditor = {
                        uiScope.launch { drawerState.close() }
                        controller.requestExitEditor(onDismiss)
                    }
                )
            }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("text_editor_plugin_screen"),
                color = palette.canvasBg
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        EditorToolbarAndSubBar(
                            activeTab = activeTab,
                            state = state,
                            palette = palette,
                            onOpenDrawer = { uiScope.launch { drawerState.open() } },
                            onToggleReadOnly = controller::toggleReadOnly,
                            onShare = { onShareFile(activeTab?.fileItem) },
                            onNewTab = controller::createNewUntitledTab,
                            onUndo = controller::undo,
                            onSave = controller::saveActiveTab,
                            onOverflowAction = { cmd ->
                                when (cmd) {
                                    EditorMenuCommand.SAVE_AS -> controller.openSaveAsDialog()
                                    EditorMenuCommand.SAVE_ALL -> controller.saveAllTabs()
                                    EditorMenuCommand.LAST_OPENED -> controller.openLastOpenedDialog()
                                    EditorMenuCommand.OPEN_WITH -> activeTab?.fileItem?.let(onOpenWithFile)
                                    EditorMenuCommand.CLOSE_EDITOR -> controller.requestExitEditor(onDismiss)
                                    EditorMenuCommand.REDO -> controller.redo()
                                    EditorMenuCommand.LINE_BREAK -> controller.toggleWordWrap()
                                    EditorMenuCommand.FIND_REPLACE -> controller.openFindReplace()
                                    EditorMenuCommand.JUMP_START -> controller.jumpToStart()
                                    EditorMenuCommand.JUMP_END -> controller.jumpToEnd()
                                    EditorMenuCommand.JUMP_LINE -> controller.openJumpToLineDialog()
                                    EditorMenuCommand.PREV_LOCATION -> controller.jumpPrevLocation()
                                    EditorMenuCommand.NEXT_LOCATION -> controller.jumpNextLocation()
                                    EditorMenuCommand.DOCUMENT_INFO -> controller.openDocumentInfo()
                                    EditorMenuCommand.CHANGE_THEME -> controller.openThemeDialog()
                                    EditorMenuCommand.HIGHLIGHT -> controller.openHighlightDialog()
                                    EditorMenuCommand.ENCODING -> controller.openEncodingDialog()
                                    EditorMenuCommand.RUN -> controller.runActiveDocument()
                                }
                            },
                            onClickEncoding = controller::openEncodingDialog,
                            onClickHighlight = controller::openHighlightDialog,
                            onTogglePreview = controller::toggleInlinePreview
                        )

                        if (state.findReplace.isVisible) {
                            EditorFindReplaceBar(
                                findReplace = state.findReplace,
                                readOnly = activeTab?.readOnly == true,
                                onQueryChange = controller::updateFindQuery,
                                onReplacementChange = controller::updateReplacement,
                                onToggleMatchCase = controller::toggleMatchCase,
                                onPrevMatch = controller::findPrevious,
                                onNextMatch = controller::findNext,
                                onReplaceOne = controller::replaceCurrent,
                                onReplaceAll = controller::replaceAll,
                                onClose = controller::closeFindReplace
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(palette.canvasBg)
                        ) {
                            when {
                                activeTab == null || activeTab.isLoading -> {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                }

                                state.isPreviewTabVisible && activeTab.supportsRichPreview -> {
                                    val mode = activeTab.effectiveHighlightMode
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp)
                                    ) {
                                        if (mode == EditorHighlightMode.MARKDOWN) {
                                            MarkdownPreviewPanel(
                                                markdown = activeTab.value.text,
                                                modifier = Modifier.fillMaxSize(),
                                                testTag = "editor_markdown_preview"
                                            )
                                        } else {
                                            HtmlPreviewPanel(
                                                html = activeTab.value.text,
                                                modifier = Modifier.fillMaxSize(),
                                                testTag = "editor_html_preview"
                                            )
                                        }
                                    }
                                }

                                else -> {
                                    LineNumberedEditorCanvas(
                                        tab = activeTab,
                                        palette = palette,
                                        wordWrap = state.wordWrap,
                                        findReplace = state.findReplace,
                                        onValueChange = controller::onTextValueChange,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(bottom = 12.dp)
                    )
                }
            }
        }

        // Modal Dialogs inside the Editor
        if (state.showSaveAsDialog) {
            val pendingTab = state.tabs.firstOrNull { it.tabId == state.pendingSaveAsTabId } ?: activeTab
            val (parentPath, isSaf) = controller.suggestedSaveAsParent()
            EditorSaveAsDialog(
                initialFileName = pendingTab?.title ?: "Untitled.txt",
                initialParentPathOrUri = parentPath,
                initialIsSaf = isSaf,
                onDismiss = controller::dismissSaveAsDialog,
                onConfirmSaveAs = { name, parent, saf ->
                    controller.commitSaveAs(name, parent, saf)
                }
            )
        }

        if (state.showLastOpenedDialog) {
            EditorLastOpenedDialog(
                entries = state.recentEntries,
                onDismiss = controller::dismissLastOpenedDialog,
                onSelectEntry = controller::openRecentEntry,
                onClearHistory = controller::clearRecentHistory
            )
        }

        if (state.showJumpToLineDialog && activeTab != null) {
            val text = activeTab.value.text
            val totalLines = if (text.isEmpty()) 1 else text.count { it == '\n' } + 1
            val cursorPos = activeTab.value.selection.start.coerceIn(0, text.length)
            val currentLine = text.substring(0, cursorPos).count { it == '\n' } + 1
            EditorJumpToLineDialog(
                totalLines = totalLines,
                currentLine = currentLine,
                onDismiss = controller::dismissJumpToLineDialog,
                onJump = controller::jumpToLine
            )
        }

        if (state.showDocumentInfoDialog && activeTab != null) {
            EditorDocumentInfoDialog(
                tab = activeTab,
                onDismiss = controller::dismissDocumentInfo
            )
        }

        if (state.showThemeDialog) {
            EditorThemePickerDialog(
                selectedPreset = state.themePreset,
                onDismiss = controller::dismissThemeDialog,
                onSelect = controller::selectThemePreset
            )
        }

        if (state.showHighlightDialog && activeTab != null) {
            EditorHighlightPickerDialog(
                selectedMode = activeTab.highlightMode,
                onDismiss = controller::dismissHighlightDialog,
                onSelect = controller::selectHighlightMode
            )
        }

        if (state.showEncodingDialog && activeTab != null) {
            EditorEncodingPickerDialog(
                selectedCharset = activeTab.charsetName,
                onDismiss = controller::dismissEncodingDialog,
                onSelect = controller::selectEncoding
            )
        }

        state.runOutput?.let { output ->
            EditorRunOutputDialog(
                output = output,
                canModifySource = activeTab?.readOnly == false,
                onApplyFormattedJson = controller::applyFormattedJsonFromRun,
                onDismiss = controller::dismissRunOutput
            )
        }

        if (state.pendingCloseTabId != null) {
            val tabToClose = state.tabs.firstOrNull { it.tabId == state.pendingCloseTabId }
            EditorUnsavedConfirmDialog(
                title = "Unsaved Changes",
                message = "'${tabToClose?.title ?: "Document"}' has unsaved edits. Save before closing this tab?",
                onSaveAndProceed = {
                    val closingId = state.pendingCloseTabId ?: return@EditorUnsavedConfirmDialog
                    controller.cancelCloseTabDialog()
                    if (tabToClose?.fileItem == null) {
                        controller.openSaveAsDialog(closingId)
                    } else {
                        controller.saveAllTabs {
                            controller.requestCloseTab(closingId, onDismiss)
                        }
                    }
                },
                onDiscard = {
                    controller.confirmDiscardAndCloseTab(onDismiss)
                },
                onCancel = controller::cancelCloseTabDialog
            )
        } else if (state.showExitUnsavedPrompt) {
            EditorUnsavedConfirmDialog(
                title = "Unsaved Changes",
                message = "You have unsaved notes open. Save changes before closing the editor?",
                onSaveAndProceed = {
                    controller.dismissExitUnsavedPrompt()
                    controller.saveAllTabs {
                        onDismiss()
                    }
                },
                onDiscard = {
                    controller.dismissExitUnsavedPrompt()
                    onDismiss()
                },
                onCancel = controller::dismissExitUnsavedPrompt
            )
        }
    }
}

@Composable
private fun LineNumberedEditorCanvas(
    tab: EditorDocumentTab,
    palette: EditorThemePalette,
    wordWrap: Boolean,
    findReplace: FindReplaceState,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val textMeasurer = rememberTextMeasurer()
    var textLayoutResult by remember(tab.tabId) { mutableStateOf<TextLayoutResult?>(null) }

    val text = tab.value.text
    val logicalLineOffsets = remember(text) {
        buildList {
            add(0)
            var idx = text.indexOf('\n')
            while (idx >= 0) {
                add(idx + 1)
                idx = text.indexOf('\n', idx + 1)
            }
        }
    }
    val activeLogicalLine = remember(text, tab.value.selection.start) {
        val pos = tab.value.selection.start.coerceIn(0, text.length)
        text.substring(0, pos).count { it == '\n' }
    }

    val digitCount = remember(logicalLineOffsets.size) {
        logicalLineOffsets.size.toString().length.coerceAtLeast(2)
    }
    val gutterWidth = (digitCount * 9 + 16).dp

    val monoStyle = TextStyle(
        fontFamily = RobotoMonoFontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = palette.canvasText
    )
    val gutterStyle = TextStyle(
        fontFamily = RobotoMonoFontFamily,
        fontSize = 12.sp,
        lineHeight = 20.sp,
        color = palette.gutterText
    )

    val visualTransformation = remember(
        tab.effectiveHighlightMode,
        palette,
        findReplace.matchRanges,
        findReplace.activeMatchIndex
    ) {
        EditorSyntaxHighlighter.createVisualTransformation(
            mode = tab.effectiveHighlightMode,
            palette = palette,
            findRanges = findReplace.matchRanges,
            activeFindIndex = findReplace.activeMatchIndex
        )
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(palette.canvasBg)
    ) {
        // Synchronized Line Number Gutter
        Canvas(
            modifier = Modifier
                .width(gutterWidth)
                .fillMaxHeight()
                .background(palette.gutterBg)
        ) {
            val layout = textLayoutResult
            val scrollY = verticalScroll.value.toFloat()
            val topPadPx = 10.dp.toPx()

            drawLine(
                color = palette.gutterDivider,
                start = Offset(size.width - 1f, 0f),
                end = Offset(size.width - 1f, size.height),
                strokeWidth = 1f
            )

            if (layout != null && logicalLineOffsets.isNotEmpty()) {
                val maxVisualLine = (layout.lineCount - 1).coerceAtLeast(0)
                logicalLineOffsets.forEachIndexed { lineIdx, charOffset ->
                    val safeOffset = charOffset.coerceIn(0, text.length)
                    val visualLine = runCatching {
                        layout.getLineForOffset(safeOffset).coerceIn(0, maxVisualLine)
                    }.getOrDefault(0)
                    val y = topPadPx + layout.getLineTop(visualLine) - scrollY
                    if (y in -40f..(size.height + 40f)) {
                        val label = (lineIdx + 1).toString()
                        val isCurrent = lineIdx == activeLogicalLine
                        val measured = textMeasurer.measure(
                            text = label,
                            style = gutterStyle.copy(
                                color = if (isCurrent) palette.gutterActiveText else palette.gutterText,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = size.width - measured.size.width - 8.dp.toPx(),
                                y = y
                            )
                        )
                    }
                }
            } else {
                val measured = textMeasurer.measure("1", style = gutterStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(size.width - measured.size.width - 8.dp.toPx(), topPadPx)
                )
            }
        }

        // Scrollable Code / Note Surface
        val fieldScrollModifier = if (wordWrap) {
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(verticalScroll)
        } else {
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(verticalScroll)
                .horizontalScroll(horizontalScroll)
        }

        Box(
            modifier = fieldScrollModifier
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = tab.value,
                onValueChange = onValueChange,
                readOnly = tab.readOnly,
                textStyle = monoStyle,
                cursorBrush = SolidColor(palette.cursorColor),
                visualTransformation = visualTransformation,
                onTextLayout = { result ->
                    if (textLayoutResult?.size != result.size || textLayoutResult?.lineCount != result.lineCount) {
                        textLayoutResult = result
                    }
                },
                modifier = (if (wordWrap) Modifier.fillMaxWidth() else Modifier)
                    .testTag("editor_text_field")
            ) { innerTextField ->
                if (tab.value.text.isEmpty()) {
                    Text(
                        text = if (tab.readOnly) "Empty document" else "Start typing note or code…",
                        style = monoStyle.copy(color = palette.gutterText)
                    )
                }
                innerTextField()
            }
        }
    }
}
