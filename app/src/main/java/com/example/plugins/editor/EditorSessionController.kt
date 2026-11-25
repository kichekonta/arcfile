package com.example.plugins.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.FileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class EditorSessionController(
    private val storage: EditorStorageRepository,
    private val scope: CoroutineScope,
    private val defaultSaveParentPathOrUri: String,
    private val defaultSaveIsSaf: Boolean,
    private val onPrimaryFileSaved: (FileItem, String) -> Unit,
    private val onWorkspaceModified: () -> Unit
) {
    private val _uiState = MutableStateFlow(
        EditorUiState(
            themePreset = storage.loadThemePreset(),
            wordWrap = storage.loadWordWrap()
        )
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var untitledCounter = 0
    private var draftSaveJob: Job? = null

    init {
        scope.launch {
            val recent = storage.loadRecentEntries()
            _uiState.update { it.copy(recentEntries = recent) }
        }
    }

    fun openInitialFile(item: FileItem, initialContent: String?) {
        val existingIdx = _uiState.value.tabs.indexOfFirst { it.fileItem?.pathOrUri == item.pathOrUri }
        if (existingIdx >= 0) {
            _uiState.update { it.copy(activeTabIndex = existingIdx) }
            return
        }
        val tabId = "tab_file_${item.pathOrUri.hashCode()}_${System.nanoTime()}"
        val defaultEncoding = storage.loadDefaultEncoding()
        val text = initialContent ?: ""
        val initialTab = EditorDocumentTab(
            tabId = tabId,
            fileItem = item,
            title = item.name,
            pathDisplay = item.pathOrUri,
            savedText = text,
            value = TextFieldValue(text = text, selection = TextRange(0)),
            readOnly = false,
            charsetName = defaultEncoding,
            highlightMode = EditorHighlightMode.AUTO,
            isLoading = initialContent == null
        )
        _uiState.update { state ->
            val updatedTabs = state.tabs + initialTab
            state.copy(
                tabs = updatedTabs,
                activeTabIndex = updatedTabs.lastIndex,
                isPreviewTabVisible = false
            )
        }

        if (initialContent == null) {
            scope.launch {
                val loaded = storage.readFileContent(item, defaultEncoding).getOrDefault("")
                updateTab(tabId) {
                    it.copy(
                        savedText = loaded,
                        value = TextFieldValue(text = loaded, selection = TextRange(0)),
                        isLoading = false
                    )
                }
                cacheTabSnapshot(tabId, loaded, item.name)
            }
        } else {
            scope.launch { cacheTabSnapshot(tabId, text, item.name) }
        }

        recordRecent(item, defaultEncoding)
    }

    fun openStandaloneWorkspace() {
        if (_uiState.value.tabs.isEmpty()) {
            createNewUntitledTab()
        }
    }

    fun openFileItem(item: FileItem) {
        val existingIdx = _uiState.value.tabs.indexOfFirst { it.fileItem?.pathOrUri == item.pathOrUri }
        if (existingIdx >= 0) {
            _uiState.update { it.copy(activeTabIndex = existingIdx, isPreviewTabVisible = false) }
            recomputeFindMatches()
            return
        }
        val tabId = "tab_file_${item.pathOrUri.hashCode()}_${System.nanoTime()}"
        val defaultEncoding = storage.loadDefaultEncoding()
        val loadingTab = EditorDocumentTab(
            tabId = tabId,
            fileItem = item,
            title = item.name,
            pathDisplay = item.pathOrUri,
            savedText = "",
            value = TextFieldValue(""),
            charsetName = defaultEncoding,
            isLoading = true
        )
        _uiState.update { state ->
            val updated = state.tabs + loadingTab
            state.copy(
                tabs = updated,
                activeTabIndex = updated.lastIndex,
                isPreviewTabVisible = false
            )
        }
        scope.launch {
            storage.readFileContent(item, defaultEncoding)
                .onSuccess { text ->
                    updateTab(tabId) {
                        it.copy(
                            savedText = text,
                            value = TextFieldValue(text = text, selection = TextRange(0)),
                            isLoading = false
                        )
                    }
                    cacheTabSnapshot(tabId, text, item.name)
                    recomputeFindMatches()
                }
                .onFailure { err ->
                    updateTab(tabId) { it.copy(isLoading = false) }
                    setStatusMessage(err.localizedMessage ?: "Failed to read '${item.name}'")
                }
        }
        recordRecent(item, defaultEncoding)
    }

    fun createNewUntitledTab() {
        untitledCounter++
        val name = "Untitled-$untitledCounter.txt"
        val tabId = "tab_untitled_${untitledCounter}_${System.nanoTime()}"
        val encoding = storage.loadDefaultEncoding()
        val tab = EditorDocumentTab(
            tabId = tabId,
            fileItem = null,
            title = name,
            pathDisplay = "Unsaved workspace note",
            savedText = "",
            value = TextFieldValue(""),
            readOnly = false,
            charsetName = encoding,
            highlightMode = EditorHighlightMode.PLAIN_TEXT
        )
        _uiState.update { state ->
            val updated = state.tabs + tab
            state.copy(
                tabs = updated,
                activeTabIndex = updated.lastIndex,
                isPreviewTabVisible = false
            )
        }
        recomputeFindMatches()
    }

    fun selectTab(index: Int) {
        if (index in _uiState.value.tabs.indices) {
            _uiState.update { it.copy(activeTabIndex = index, isPreviewTabVisible = false) }
            recomputeFindMatches()
        }
    }

    fun requestCloseTab(tabId: String, onAllTabsClosed: () -> Unit) {
        val tab = _uiState.value.tabs.firstOrNull { it.tabId == tabId } ?: return
        if (tab.isDirty) {
            _uiState.update { it.copy(pendingCloseTabId = tabId) }
        } else {
            forceCloseTab(tabId, onAllTabsClosed)
        }
    }

    fun confirmDiscardAndCloseTab(onAllTabsClosed: () -> Unit) {
        val tabId = _uiState.value.pendingCloseTabId ?: return
        _uiState.update { it.copy(pendingCloseTabId = null) }
        forceCloseTab(tabId, onAllTabsClosed)
    }

    fun cancelCloseTabDialog() {
        _uiState.update { it.copy(pendingCloseTabId = null) }
    }

    private fun forceCloseTab(tabId: String, onAllTabsClosed: () -> Unit) {
        scope.launch { storage.deleteDraft(tabId) }
        var isEmptyAfter = false
        _uiState.update { state ->
            val removedIdx = state.tabs.indexOfFirst { it.tabId == tabId }
            if (removedIdx < 0) return@update state
            val updated = state.tabs.filterNot { it.tabId == tabId }
            isEmptyAfter = updated.isEmpty()
            val newIdx = when {
                updated.isEmpty() -> 0
                state.activeTabIndex >= updated.size -> updated.lastIndex
                removedIdx < state.activeTabIndex -> (state.activeTabIndex - 1).coerceAtLeast(0)
                else -> state.activeTabIndex
            }
            state.copy(tabs = updated, activeTabIndex = newIdx, isPreviewTabVisible = false)
        }
        if (isEmptyAfter) {
            onAllTabsClosed()
        } else {
            recomputeFindMatches()
        }
    }

    fun onTextValueChange(newValue: TextFieldValue) {
        val active = _uiState.value.activeTab ?: return
        if (active.isLoading) return

        val textChanged = newValue.text != active.value.text
        if (active.readOnly && textChanged) {
            setStatusMessage("Read-only mode is enabled")
            return
        }

        val updatedUndo = if (textChanged) {
            (active.undoStack + TextSnapshot(active.value.text, active.value.selection.start, active.value.selection.end))
                .takeLast(80)
        } else {
            active.undoStack
        }
        val updatedRedo = if (textChanged) emptyList() else active.redoStack

        var history = active.locationHistory
        var histIdx = active.locationIndex
        val cursor = newValue.selection.start
        val currentRecorded = history.getOrNull(histIdx) ?: 0
        if (kotlin.math.abs(cursor - currentRecorded) >= 48) {
            val truncated = history.take(histIdx + 1)
            history = (truncated + cursor).takeLast(40)
            histIdx = history.lastIndex
        }

        updateTab(active.tabId) {
            it.copy(
                value = newValue,
                undoStack = updatedUndo,
                redoStack = updatedRedo,
                locationHistory = history,
                locationIndex = histIdx
            )
        }

        if (textChanged) {
            if (_uiState.value.findReplace.isVisible && _uiState.value.findReplace.query.isNotEmpty()) {
                recomputeFindMatches()
            }
            scheduleDraftSave(active.tabId, active.title, newValue.text)
        }
    }

    private fun scheduleDraftSave(tabId: String, title: String, content: String) {
        draftSaveJob?.cancel()
        draftSaveJob = scope.launch {
            delay(450)
            storage.saveDraft(tabId, title, content)
        }
    }

    fun toggleReadOnly() {
        val active = _uiState.value.activeTab ?: return
        val next = !active.readOnly
        updateTab(active.tabId) { it.copy(readOnly = next) }
        setStatusMessage(if (next) "Read-only lock enabled" else "Edit mode enabled")
    }

    fun undo() {
        val active = _uiState.value.activeTab ?: return
        if (!active.canUndo) return
        val snap = active.undoStack.last()
        val redoSnap = TextSnapshot(active.value.text, active.value.selection.start, active.value.selection.end)
        val restored = TextFieldValue(
            text = snap.text,
            selection = TextRange(
                snap.selectionStart.coerceIn(0, snap.text.length),
                snap.selectionEnd.coerceIn(0, snap.text.length)
            )
        )
        updateTab(active.tabId) {
            it.copy(
                value = restored,
                undoStack = it.undoStack.dropLast(1),
                redoStack = (it.redoStack + redoSnap).takeLast(80)
            )
        }
        recomputeFindMatches()
    }

    fun redo() {
        val active = _uiState.value.activeTab ?: return
        if (!active.canRedo) return
        val snap = active.redoStack.last()
        val undoSnap = TextSnapshot(active.value.text, active.value.selection.start, active.value.selection.end)
        val restored = TextFieldValue(
            text = snap.text,
            selection = TextRange(
                snap.selectionStart.coerceIn(0, snap.text.length),
                snap.selectionEnd.coerceIn(0, snap.text.length)
            )
        )
        updateTab(active.tabId) {
            it.copy(
                value = restored,
                undoStack = (it.undoStack + undoSnap).takeLast(80),
                redoStack = it.redoStack.dropLast(1)
            )
        }
        recomputeFindMatches()
    }

    fun toggleWordWrap() {
        val next = !_uiState.value.wordWrap
        storage.saveWordWrap(next)
        _uiState.update { it.copy(wordWrap = next) }
    }

    fun saveActiveTab() {
        val active = _uiState.value.activeTab ?: return
        val item = active.fileItem
        if (item == null) {
            openSaveAsDialog(active.tabId)
            return
        }
        scope.launch {
            val content = active.value.text
            storage.writeFileContent(item, content, active.charsetName)
                .onSuccess {
                    storage.deleteDraft(active.tabId)
                    updateTab(active.tabId) {
                        it.copy(savedText = content, lastSavedMillis = System.currentTimeMillis())
                    }
                    cacheTabSnapshot(active.tabId, content, item.name)
                    if (_uiState.value.tabs.firstOrNull()?.tabId == active.tabId) {
                        onPrimaryFileSaved(item, content)
                    } else {
                        onWorkspaceModified()
                    }
                    setStatusMessage("Saved '${item.name}'")
                }
                .onFailure { err ->
                    setStatusMessage(err.localizedMessage ?: "Failed to save '${item.name}'")
                }
        }
    }

    fun saveAllTabs(onAllSaved: (() -> Unit)? = null) {
        val dirtyTabs = _uiState.value.tabs.filter { it.isDirty }
        if (dirtyTabs.isEmpty()) {
            onAllSaved?.invoke()
            return
        }
        val untitled = dirtyTabs.firstOrNull { it.fileItem == null }
        if (untitled != null) {
            openSaveAsDialog(untitled.tabId)
            return
        }
        scope.launch {
            var savedCount = 0
            dirtyTabs.forEach { tab ->
                val fileItem = tab.fileItem ?: return@forEach
                val content = tab.value.text
                if (storage.writeFileContent(fileItem, content, tab.charsetName).isSuccess) {
                    savedCount++
                    storage.deleteDraft(tab.tabId)
                    updateTab(tab.tabId) {
                        it.copy(savedText = content, lastSavedMillis = System.currentTimeMillis())
                    }
                    onPrimaryFileSaved(fileItem, content)
                }
            }
            if (savedCount > 0) {
                onWorkspaceModified()
                setStatusMessage("Saved $savedCount file${if (savedCount == 1) "" else "s"}")
                onAllSaved?.invoke()
            }
        }
    }

    fun openSaveAsDialog(tabId: String? = _uiState.value.activeTab?.tabId) {
        if (tabId == null) return
        _uiState.update {
            it.copy(showSaveAsDialog = true, pendingSaveAsTabId = tabId)
        }
    }

    fun dismissSaveAsDialog() {
        _uiState.update { it.copy(showSaveAsDialog = false, pendingSaveAsTabId = null) }
    }

    fun suggestedSaveAsParent(): Pair<String, Boolean> {
        val targetTab = _uiState.value.tabs.firstOrNull { it.tabId == _uiState.value.pendingSaveAsTabId }
            ?: _uiState.value.activeTab
        val existingParent = targetTab?.fileItem?.parentPathOrUri?.takeIf { it.isNotBlank() }
        return if (existingParent != null) {
            existingParent to (targetTab.fileItem.isSafUri)
        } else {
            defaultSaveParentPathOrUri to defaultSaveIsSaf
        }
    }

    fun commitSaveAs(
        fileName: String,
        parentPathOrUri: String,
        isSafParent: Boolean,
        overwriteExisting: Boolean = true
    ) {
        val tabId = _uiState.value.pendingSaveAsTabId ?: _uiState.value.activeTab?.tabId ?: return
        val tab = _uiState.value.tabs.firstOrNull { it.tabId == tabId } ?: return
        val content = tab.value.text
        scope.launch {
            storage.saveAsNewFile(
                parentPathOrUri = parentPathOrUri,
                isSafParent = isSafParent,
                fileName = fileName,
                content = content,
                charsetName = tab.charsetName,
                overwriteExisting = overwriteExisting
            ).onSuccess { newFile ->
                storage.deleteDraft(tab.tabId)
                updateTab(tab.tabId) {
                    it.copy(
                        fileItem = newFile,
                        title = newFile.name,
                        pathDisplay = newFile.pathOrUri,
                        savedText = content,
                        lastSavedMillis = System.currentTimeMillis()
                    )
                }
                _uiState.update { it.copy(showSaveAsDialog = false, pendingSaveAsTabId = null) }
                recordRecent(newFile, tab.charsetName)
                cacheTabSnapshot(tab.tabId, content, newFile.name)
                onWorkspaceModified()
                setStatusMessage("Saved as '${newFile.name}'")
            }.onFailure { err ->
                setStatusMessage(err.localizedMessage ?: "Save As failed")
            }
        }
    }

    fun openLastOpenedDialog() {
        scope.launch {
            val recent = storage.loadRecentEntries()
            _uiState.update { it.copy(recentEntries = recent, showLastOpenedDialog = true) }
        }
    }

    fun dismissLastOpenedDialog() {
        _uiState.update { it.copy(showLastOpenedDialog = false) }
    }

    fun openRecentEntry(entry: EditorRecentEntry) {
        _uiState.update { it.copy(showLastOpenedDialog = false) }
        if (!entry.isSafUri && !File(entry.pathOrUri).exists()) {
            setStatusMessage("File no longer exists on disk")
            return
        }
        val item = FileItem(
            id = entry.pathOrUri,
            name = entry.name,
            pathOrUri = entry.pathOrUri,
            parentPathOrUri = entry.parentPathOrUri,
            isSafUri = entry.isSafUri,
            isDirectory = false,
            sizeBytes = entry.sizeBytes,
            lastModifiedMillis = entry.lastOpenedMillis,
            extension = entry.name.substringAfterLast('.', "").lowercase(),
            mimeType = entry.mimeType
        )
        openFileItem(item)
    }

    fun clearRecentHistory() {
        scope.launch {
            storage.clearRecentEntries()
            _uiState.update { it.copy(recentEntries = emptyList()) }
            setStatusMessage("Cleared editor recent history")
        }
    }

    fun openFindReplace() {
        _uiState.update { state ->
            state.copy(findReplace = state.findReplace.copy(isVisible = true), isPreviewTabVisible = false)
        }
        recomputeFindMatches()
    }

    fun closeFindReplace() {
        _uiState.update { state ->
            state.copy(findReplace = state.findReplace.copy(isVisible = false, matchRanges = emptyList(), activeMatchIndex = -1))
        }
    }

    fun updateFindQuery(query: String) {
        _uiState.update { state ->
            state.copy(findReplace = state.findReplace.copy(query = query))
        }
        recomputeFindMatches()
    }

    fun updateReplacement(replacement: String) {
        _uiState.update { state ->
            state.copy(findReplace = state.findReplace.copy(replacement = replacement))
        }
    }

    fun toggleMatchCase() {
        _uiState.update { state ->
            state.copy(findReplace = state.findReplace.copy(matchCase = !state.findReplace.matchCase))
        }
        recomputeFindMatches()
    }

    private fun recomputeFindMatches() {
        val state = _uiState.value
        val active = state.activeTab ?: return
        val query = state.findReplace.query
        if (!state.findReplace.isVisible || query.isEmpty()) {
            _uiState.update {
                it.copy(findReplace = it.findReplace.copy(matchRanges = emptyList(), activeMatchIndex = -1))
            }
            return
        }
        val text = active.value.text
        val ignoreCase = !state.findReplace.matchCase
        val ranges = buildList {
            var idx = text.indexOf(query, startIndex = 0, ignoreCase = ignoreCase)
            while (idx >= 0 && size < 2000) {
                add(idx until (idx + query.length))
                idx = text.indexOf(query, startIndex = idx + query.length.coerceAtLeast(1), ignoreCase = ignoreCase)
            }
        }
        val nextActive = if (ranges.isEmpty()) -1 else 0
        _uiState.update {
            it.copy(findReplace = it.findReplace.copy(matchRanges = ranges, activeMatchIndex = nextActive))
        }
    }

    fun findNext() {
        val fr = _uiState.value.findReplace
        val active = _uiState.value.activeTab ?: return
        if (fr.matchRanges.isEmpty()) return
        val nextIndex = if (fr.activeMatchIndex + 1 >= fr.matchRanges.size) 0 else fr.activeMatchIndex + 1
        val target = fr.matchRanges[nextIndex]
        _uiState.update { it.copy(findReplace = it.findReplace.copy(activeMatchIndex = nextIndex)) }
        jumpCursorTo(active.tabId, target.first, target.last + 1)
    }

    fun findPrevious() {
        val fr = _uiState.value.findReplace
        val active = _uiState.value.activeTab ?: return
        if (fr.matchRanges.isEmpty()) return
        val prevIndex = if (fr.activeMatchIndex - 1 < 0) fr.matchRanges.lastIndex else fr.activeMatchIndex - 1
        val target = fr.matchRanges[prevIndex]
        _uiState.update { it.copy(findReplace = it.findReplace.copy(activeMatchIndex = prevIndex)) }
        jumpCursorTo(active.tabId, target.first, target.last + 1)
    }

    fun replaceCurrent() {
        val active = _uiState.value.activeTab ?: return
        if (active.readOnly) {
            setStatusMessage("Read-only mode is enabled")
            return
        }
        val fr = _uiState.value.findReplace
        val target = fr.matchRanges.getOrNull(fr.activeMatchIndex) ?: return
        val text = active.value.text
        val replaced = buildString {
            append(text.substring(0, target.first))
            append(fr.replacement)
            append(text.substring(target.last + 1))
        }
        val newCursor = target.first + fr.replacement.length
        onTextValueChange(TextFieldValue(text = replaced, selection = TextRange(newCursor)))
        recomputeFindMatches()
    }

    fun replaceAll() {
        val active = _uiState.value.activeTab ?: return
        if (active.readOnly) {
            setStatusMessage("Read-only mode is enabled")
            return
        }
        val fr = _uiState.value.findReplace
        if (fr.query.isEmpty() || fr.matchRanges.isEmpty()) return
        val count = fr.matchRanges.size
        val replaced = buildString {
            var lastEnd = 0
            fr.matchRanges.forEach { r ->
                append(active.value.text.substring(lastEnd, r.first))
                append(fr.replacement)
                lastEnd = r.last + 1
            }
            append(active.value.text.substring(lastEnd))
        }
        onTextValueChange(TextFieldValue(text = replaced, selection = TextRange(0)))
        recomputeFindMatches()
        setStatusMessage("Replaced $count occurrence${if (count == 1) "" else "s"}")
    }

    fun jumpToStart() {
        val active = _uiState.value.activeTab ?: return
        jumpCursorTo(active.tabId, 0, 0)
    }

    fun jumpToEnd() {
        val active = _uiState.value.activeTab ?: return
        val end = active.value.text.length
        jumpCursorTo(active.tabId, end, end)
    }

    fun openJumpToLineDialog() {
        _uiState.update { it.copy(showJumpToLineDialog = true) }
    }

    fun dismissJumpToLineDialog() {
        _uiState.update { it.copy(showJumpToLineDialog = false) }
    }

    fun jumpToLine(lineOneBased: Int) {
        val active = _uiState.value.activeTab ?: return
        _uiState.update { it.copy(showJumpToLineDialog = false) }
        val text = active.value.text
        val targetLine = lineOneBased.coerceAtLeast(1)
        var currentLine = 1
        var offset = 0
        while (currentLine < targetLine && offset < text.length) {
            val nl = text.indexOf('\n', offset)
            if (nl < 0) {
                offset = text.length
                break
            }
            offset = nl + 1
            currentLine++
        }
        jumpCursorTo(active.tabId, offset, offset)
    }

    fun jumpPrevLocation() {
        val active = _uiState.value.activeTab ?: return
        if (!active.canJumpPrevLocation) return
        val nextIdx = active.locationIndex - 1
        val pos = active.locationHistory[nextIdx].coerceIn(0, active.value.text.length)
        updateTab(active.tabId) {
            it.copy(
                locationIndex = nextIdx,
                value = it.value.copy(selection = TextRange(pos))
            )
        }
    }

    fun jumpNextLocation() {
        val active = _uiState.value.activeTab ?: return
        if (!active.canJumpNextLocation) return
        val nextIdx = active.locationIndex + 1
        val pos = active.locationHistory[nextIdx].coerceIn(0, active.value.text.length)
        updateTab(active.tabId) {
            it.copy(
                locationIndex = nextIdx,
                value = it.value.copy(selection = TextRange(pos))
            )
        }
    }

    private fun jumpCursorTo(tabId: String, start: Int, end: Int) {
        updateTab(tabId) { tab ->
            val len = tab.value.text.length
            val safeStart = start.coerceIn(0, len)
            val safeEnd = end.coerceIn(safeStart, len)
            val truncated = tab.locationHistory.take(tab.locationIndex + 1)
            val updatedHistory = if (truncated.lastOrNull() == safeStart) {
                truncated
            } else {
                (truncated + safeStart).takeLast(40)
            }
            tab.copy(
                value = tab.value.copy(selection = TextRange(safeStart, safeEnd)),
                locationHistory = updatedHistory,
                locationIndex = updatedHistory.lastIndex
            )
        }
    }

    fun openDocumentInfo() {
        _uiState.update { it.copy(showDocumentInfoDialog = true) }
    }

    fun dismissDocumentInfo() {
        _uiState.update { it.copy(showDocumentInfoDialog = false) }
    }

    fun openThemeDialog() {
        _uiState.update { it.copy(showThemeDialog = true) }
    }

    fun dismissThemeDialog() {
        _uiState.update { it.copy(showThemeDialog = false) }
    }

    fun selectThemePreset(preset: EditorThemePreset) {
        storage.saveThemePreset(preset)
        _uiState.update { it.copy(themePreset = preset, showThemeDialog = false) }
    }

    fun openHighlightDialog() {
        _uiState.update { it.copy(showHighlightDialog = true) }
    }

    fun dismissHighlightDialog() {
        _uiState.update { it.copy(showHighlightDialog = false) }
    }

    fun selectHighlightMode(mode: EditorHighlightMode) {
        val active = _uiState.value.activeTab
        if (active != null) {
            updateTab(active.tabId) { it.copy(highlightMode = mode) }
        }
        _uiState.update { it.copy(showHighlightDialog = false) }
    }

    fun openEncodingDialog() {
        _uiState.update { it.copy(showEncodingDialog = true) }
    }

    fun dismissEncodingDialog() {
        _uiState.update { it.copy(showEncodingDialog = false) }
    }

    fun selectEncoding(charsetName: String) {
        val active = _uiState.value.activeTab
        storage.saveDefaultEncoding(charsetName)
        _uiState.update { it.copy(showEncodingDialog = false) }
        if (active == null) return

        val fileItem = active.fileItem
        if (fileItem != null && !active.isDirty) {
            scope.launch {
                storage.readFileContent(fileItem, charsetName)
                    .onSuccess { reloaded ->
                        updateTab(active.tabId) {
                            it.copy(
                                charsetName = charsetName,
                                savedText = reloaded,
                                value = TextFieldValue(reloaded, TextRange(0))
                            )
                        }
                        setStatusMessage("Reloaded using $charsetName")
                    }
                    .onFailure {
                        updateTab(active.tabId) { it.copy(charsetName = charsetName) }
                        setStatusMessage("Encoding set to $charsetName")
                    }
            }
        } else {
            updateTab(active.tabId) { it.copy(charsetName = charsetName) }
            setStatusMessage("Encoding set to $charsetName for next save")
        }
    }

    fun toggleInlinePreview() {
        _uiState.update { it.copy(isPreviewTabVisible = !it.isPreviewTabVisible) }
    }

    fun runActiveDocument() {
        val active = _uiState.value.activeTab ?: return
        val content = active.value.text
        val mode = active.effectiveHighlightMode
        val output = when (mode) {
            EditorHighlightMode.MARKDOWN -> EditorRunOutput(
                title = active.title,
                kind = RunOutputKind.MARKDOWN_PREVIEW,
                content = content
            )
            EditorHighlightMode.HTML -> EditorRunOutput(
                title = active.title,
                kind = RunOutputKind.HTML_PREVIEW,
                content = content
            )
            EditorHighlightMode.JSON -> {
                val trimmed = content.trim()
                val formatted = runCatching {
                    when {
                        trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                        trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                        else -> error("JSON must start with '{' or '['")
                    }
                }
                EditorRunOutput(
                    title = "${active.title} · JSON Check",
                    kind = RunOutputKind.JSON_VALIDATOR,
                    content = formatted.getOrElse { content },
                    statusBanner = formatted.fold(
                        onSuccess = { "Valid JSON (${it.lines().size} formatted lines)" },
                        onFailure = { "Invalid JSON: ${it.localizedMessage}" }
                    ),
                    formattedText = formatted.getOrNull()
                )
            }
            else -> {
                val lines = if (content.isEmpty()) 0 else content.count { it == '\n' } + 1
                val words = content.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
                EditorRunOutput(
                    title = "${active.title} · Document Output",
                    kind = RunOutputKind.PLAIN_INSPECTOR,
                    content = content,
                    statusBanner = "$lines lines · $words words · ${content.length} chars · ${active.charsetName}"
                )
            }
        }
        _uiState.update { it.copy(runOutput = output) }
    }

    fun applyFormattedJsonFromRun() {
        val formatted = _uiState.value.runOutput?.formattedText ?: return
        val active = _uiState.value.activeTab ?: return
        if (!active.readOnly) {
            onTextValueChange(TextFieldValue(formatted, TextRange(0)))
            setStatusMessage("Formatted JSON applied")
        }
        _uiState.update { it.copy(runOutput = null) }
    }

    fun dismissRunOutput() {
        _uiState.update { it.copy(runOutput = null) }
    }

    fun requestExitEditor(onConfirmedExit: () -> Unit) {
        if (_uiState.value.anyDirty) {
            _uiState.update { it.copy(showExitUnsavedPrompt = true) }
        } else {
            onConfirmedExit()
        }
    }

    fun dismissExitUnsavedPrompt() {
        _uiState.update { it.copy(showExitUnsavedPrompt = false) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun setStatusMessage(msg: String) {
        _uiState.update { it.copy(statusMessage = msg) }
    }

    private fun updateTab(tabId: String, transform: (EditorDocumentTab) -> EditorDocumentTab) {
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { if (it.tabId == tabId) transform(it) else it }
            )
        }
    }

    private fun recordRecent(item: FileItem, charsetName: String) {
        scope.launch {
            val updated = storage.recordRecentEntry(
                EditorRecentEntry(
                    pathOrUri = item.pathOrUri,
                    isSafUri = item.isSafUri,
                    name = item.name,
                    parentPathOrUri = item.parentPathOrUri,
                    mimeType = item.mimeType,
                    sizeBytes = item.sizeBytes,
                    lastOpenedMillis = System.currentTimeMillis(),
                    charsetName = charsetName
                )
            )
            _uiState.update { it.copy(recentEntries = updated) }
        }
    }

    private suspend fun cacheTabSnapshot(tabId: String, text: String, name: String) {
        val lines = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
        val words = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        val mode = EditorHighlightMode.AUTO.resolveForName(name).label
        storage.writePreviewCacheSnapshot(tabId, lines, words, mode)
    }
}
