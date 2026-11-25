package com.example.features.files

import android.net.Uri
import android.os.Environment
import com.example.data.ClipboardOp
import com.example.data.DriveType
import com.example.data.FileExplorerRepository
import com.example.data.FileItem
import com.example.data.FileTransferProgress
import com.example.data.TabDestination
import com.example.data.TransferOperationType
import com.example.data.TransferStatus
import com.example.data.TrashEntry
import com.example.data.db.AlbumRepository
import com.example.data.db.FileTagRepository
import com.example.ui.ExplorerUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Feature-scoped coordinator owning File CRUD, Clipboard Transfers (Copy/Move/Paste),
 * Inline & Batch Renames, Recycle Bin / Permanent Delete, and External URI Import.
 */
class FileOperationsCoordinator(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<ExplorerUiState>,
    private val repo: FileExplorerRepository,
    private val fileTagRepo: FileTagRepository,
    private val albumRepo: AlbumRepository,
    private val isTransferActive: () -> Boolean,
    private val getActiveTransferJob: () -> Job?,
    private val registerTransferJob: (Job?) -> Unit,
    private val onRemoveFolderState: suspend (String) -> Unit,
    private val onRefreshAfterChange: () -> Unit,
    private val onMessage: (String) -> Unit
) {

    fun clearClipboard() {
        uiState.update { it.copy(clipboard = null) }
    }

    private fun resolveFriendlySourceLabel(items: List<FileItem>): String {
        val first = items.firstOrNull() ?: return "Source"
        val matchingDrive = uiState.value.drives.firstOrNull { d ->
            first.parentPathOrUri == d.pathOrUri || first.parentPathOrUri.startsWith(d.pathOrUri)
        }
        return if (first.isSafUri) {
            matchingDrive?.name ?: "External Drive"
        } else {
            val parentName = File(first.parentPathOrUri).name.takeIf { it.isNotBlank() }
            parentName ?: matchingDrive?.name ?: "Internal Storage"
        }
    }

    fun pasteClipboardToCurrentDirectory() {
        val clip = uiState.value.clipboard ?: return
        val dir = uiState.value.activeTab?.destination as? TabDestination.Directory ?: return
        if (isTransferActive()) {
            onMessage("A file transfer is already running")
            return
        }
        val isMove = clip.operation == ClipboardOp.MOVE
        val op = if (isMove) TransferOperationType.MOVE else TransferOperationType.COPY
        val sourceLabel = resolveFriendlySourceLabel(clip.items)
        val destinationLabel = dir.breadcrumbs.lastOrNull()?.title?.takeIf { it.isNotBlank() }
            ?: dir.title.ifBlank { "Destination" }

        val job = scope.launch {
            uiState.update {
                it.copy(
                    activeTransfer = FileTransferProgress(
                        taskId = "pending",
                        operation = op,
                        status = TransferStatus.PREPARING,
                        currentFileName = clip.items.firstOrNull()?.name.orEmpty(),
                        sourceLabel = sourceLabel,
                        destinationLabel = destinationLabel,
                        totalItems = clip.items.size
                    )
                )
            }
            try {
                val result = repo.copyOrMoveItems(
                    items = clip.items,
                    targetParentPathOrUri = dir.currentPathOrUri,
                    isTargetSaf = dir.isSaf,
                    isMove = isMove,
                    sourceLabel = sourceLabel,
                    destinationLabel = destinationLabel,
                    onProgress = { progress ->
                        uiState.update { state ->
                            val keepMinimized = state.activeTransfer?.isMinimized == true
                            state.copy(activeTransfer = progress.copy(isMinimized = keepMinimized))
                        }
                    }
                )
                result.onSuccess { count ->
                    if (isMove) {
                        clip.items.filter { it.isDirectory }.forEach { movedFolder ->
                            onRemoveFolderState(movedFolder.pathOrUri)
                        }
                        if (!dir.isSaf) {
                            clip.items.filter { !it.isSafUri }.forEach { movedItem ->
                                val newPath = File(dir.currentPathOrUri, movedItem.name).absolutePath
                                fileTagRepo.remapFilePath(
                                    oldPathOrUri = movedItem.pathOrUri,
                                    oldIsSaf = false,
                                    newPathOrUri = newPath,
                                    newIsSaf = false,
                                    newName = movedItem.name,
                                    newParentPathOrUri = dir.currentPathOrUri
                                )
                            }
                        }
                    }
                    uiState.update {
                        it.copy(
                            activeTransfer = null,
                            clipboard = if (isMove) null else it.clipboard,
                            snackbarMessage = "${if (isMove) "Moved" else "Copied"} $count item(s)"
                        )
                    }
                    onRefreshAfterChange()
                }.onFailure { err ->
                    val errMsg = err.localizedMessage ?: "Paste failed"
                    uiState.update { state ->
                        val prev = state.activeTransfer
                        state.copy(
                            activeTransfer = prev?.copy(
                                status = TransferStatus.FAILED,
                                errorMessage = errMsg,
                                isMinimized = false
                            ),
                            snackbarMessage = errMsg
                        )
                    }
                }
            } catch (_: CancellationException) {
                uiState.update { state ->
                    state.copy(
                        activeTransfer = null,
                        snackbarMessage = "${if (isMove) "Move" else "Copy"} cancelled"
                    )
                }
                onRefreshAfterChange()
            } finally {
                registerTransferJob(null)
            }
        }
        registerTransferJob(job)
    }

    fun minimizeActiveTransfer() {
        uiState.update { state ->
            val current = state.activeTransfer ?: return@update state
            state.copy(activeTransfer = current.copy(isMinimized = true))
        }
    }

    fun expandActiveTransfer() {
        uiState.update { state ->
            val current = state.activeTransfer ?: return@update state
            state.copy(activeTransfer = current.copy(isMinimized = false))
        }
    }

    fun cancelActiveTransfer() {
        val job = getActiveTransferJob()
        if (job != null && job.isActive) {
            job.cancel()
        } else {
            uiState.update { it.copy(activeTransfer = null) }
        }
    }

    fun dismissActiveTransfer() {
        uiState.update { it.copy(activeTransfer = null) }
    }

    fun moveItemsToFolder(
        items: List<FileItem>,
        targetPathOrUri: String,
        isTargetSaf: Boolean,
        destinationLabel: String
    ) {
        if (items.isEmpty()) return
        if (isTransferActive()) {
            onMessage("A file transfer is already running")
            return
        }
        val sourceLabel = resolveFriendlySourceLabel(items)
        val job = scope.launch {
            var latestProgress: FileTransferProgress? = null
            var allowShowDialog = false
            val showDelayJob = launch {
                delay(180L)
                allowShowDialog = true
                latestProgress?.let { p ->
                    uiState.update { state ->
                        val keepMinimized = state.activeTransfer?.isMinimized == true
                        state.copy(activeTransfer = p.copy(isMinimized = keepMinimized))
                    }
                }
            }
            try {
                val result = repo.copyOrMoveItems(
                    items = items,
                    targetParentPathOrUri = targetPathOrUri,
                    isTargetSaf = isTargetSaf,
                    isMove = true,
                    sourceLabel = sourceLabel,
                    destinationLabel = destinationLabel,
                    onProgress = { progress ->
                        latestProgress = progress
                        if (allowShowDialog) {
                            uiState.update { state ->
                                val keepMinimized = state.activeTransfer?.isMinimized == true
                                state.copy(activeTransfer = progress.copy(isMinimized = keepMinimized))
                            }
                        }
                    }
                )
                showDelayJob.cancel()
                result.onSuccess { count ->
                    items.filter { it.isDirectory }.forEach { movedFolder ->
                        onRemoveFolderState(movedFolder.pathOrUri)
                    }
                    if (!isTargetSaf) {
                        items.filter { !it.isSafUri }.forEach { movedItem ->
                            val newPath = File(targetPathOrUri, movedItem.name).absolutePath
                            fileTagRepo.remapFilePath(
                                oldPathOrUri = movedItem.pathOrUri,
                                oldIsSaf = false,
                                newPathOrUri = newPath,
                                newIsSaf = false,
                                newName = movedItem.name,
                                newParentPathOrUri = targetPathOrUri
                            )
                        }
                    }
                    uiState.update {
                        it.copy(
                            activeTransfer = null,
                            selectedIds = emptySet(),
                            snackbarMessage = "Moved $count item(s) to $destinationLabel"
                        )
                    }
                    onRefreshAfterChange()
                }.onFailure { err ->
                    val errMsg = err.localizedMessage ?: "Move failed"
                    uiState.update { state ->
                        state.copy(
                            activeTransfer = null,
                            snackbarMessage = errMsg
                        )
                    }
                }
            } catch (_: CancellationException) {
                showDelayJob.cancel()
                uiState.update { state ->
                    state.copy(
                        activeTransfer = null,
                        snackbarMessage = "Move cancelled"
                    )
                }
                onRefreshAfterChange()
            } finally {
                showDelayJob.cancel()
                registerTransferJob(null)
            }
        }
        registerTransferJob(job)
    }

    fun resolveTargetDirectoryForCreation(): Pair<String, Boolean> {
        val dir = uiState.value.activeTab?.destination as? TabDestination.Directory
        if (dir != null) return dir.currentPathOrUri to dir.isSaf
        val internal = uiState.value.drives.firstOrNull { it.type == DriveType.INTERNAL }
        return (internal?.pathOrUri ?: Environment.getExternalStorageDirectory().absolutePath) to false
    }

    fun createFolder(name: String) {
        val targetDir = resolveTargetDirectoryForCreation()
        scope.launch {
            repo.createFolder(targetDir.first, targetDir.second, name)
                .onSuccess {
                    onMessage("Folder '$name' created")
                    onRefreshAfterChange()
                }
                .onFailure { onMessage(it.localizedMessage ?: "Could not create folder") }
        }
    }

    fun createNoteFile(name: String, content: String) {
        val fileName = if (name.contains('.')) name else "$name.txt"
        val targetDir = resolveTargetDirectoryForCreation()
        scope.launch {
            repo.createTextFile(targetDir.first, targetDir.second, fileName, content)
                .onSuccess {
                    onMessage("Created '$fileName'")
                    onRefreshAfterChange()
                }
                .onFailure { onMessage(it.localizedMessage ?: "Could not create file") }
        }
    }

    fun startInlineRename(item: FileItem) {
        uiState.update {
            it.copy(
                renamingItemId = item.id,
                selectedIds = emptySet()
            )
        }
    }

    fun cancelInlineRename() {
        uiState.update { it.copy(renamingItemId = null) }
    }

    fun commitInlineRename(item: FileItem, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty() || clean.contains("/") || clean.contains("\\")) {
            onMessage("Enter a valid name")
            return
        }
        uiState.update { it.copy(renamingItemId = null) }
        if (clean == item.name) return
        renameFile(item, clean)
    }

    fun renameFile(item: FileItem, newName: String) {
        scope.launch {
            repo.renameItem(item, newName)
                .onSuccess {
                    if (item.isDirectory) {
                        onRemoveFolderState(item.pathOrUri)
                    }
                    if (!item.isSafUri) {
                        val newFile = File(File(item.pathOrUri).parentFile, newName.trim())
                        fileTagRepo.remapFilePath(
                            oldPathOrUri = item.pathOrUri,
                            oldIsSaf = false,
                            newPathOrUri = newFile.absolutePath,
                            newIsSaf = false,
                            newName = newName.trim(),
                            newParentPathOrUri = item.parentPathOrUri
                        )
                    }
                    uiState.update {
                        it.copy(
                            selectedIds = emptySet(),
                            renamingItemId = null,
                            snackbarMessage = "Renamed to '$newName'"
                        )
                    }
                    onRefreshAfterChange()
                }
                .onFailure { onMessage(it.localizedMessage ?: "Rename failed") }
        }
    }

    fun batchRenameFiles(renames: List<Pair<FileItem, String>>) {
        scope.launch {
            repo.batchRenameItems(renames)
                .onSuccess { count ->
                    renames
                        .filter { (item, target) -> item.isDirectory && target.trim() != item.name }
                        .forEach { (item, _) -> onRemoveFolderState(item.pathOrUri) }
                    renames.forEach { (item, target) ->
                        val clean = target.trim()
                        if (!item.isSafUri && clean.isNotBlank() && clean != item.name) {
                            val newFile = File(File(item.pathOrUri).parentFile, clean)
                            fileTagRepo.remapFilePath(
                                oldPathOrUri = item.pathOrUri,
                                oldIsSaf = false,
                                newPathOrUri = newFile.absolutePath,
                                newIsSaf = false,
                                newName = clean,
                                newParentPathOrUri = item.parentPathOrUri
                            )
                        }
                    }
                    uiState.update {
                        it.copy(
                            selectedIds = emptySet(),
                            snackbarMessage = "Renamed $count item(s)"
                        )
                    }
                    onRefreshAfterChange()
                }
                .onFailure { onMessage(it.localizedMessage ?: "Batch rename failed") }
        }
    }

    fun deleteFiles(items: List<FileItem>) {
        scope.launch {
            repo.deleteItems(items)
                .onSuccess { count ->
                    items.filter { it.isDirectory }.forEach { folder ->
                        onRemoveFolderState(folder.pathOrUri)
                    }
                    fileTagRepo.removeTagsForDeletedItems(items)
                    albumRepo.purgeDeletedFiles(items)
                    val deletedIds = items.mapTo(HashSet()) { it.id }
                    val deletedPaths = items.mapTo(HashSet()) { it.pathOrUri }
                    uiState.update { state ->
                        state.copy(
                            selectedIds = emptySet(),
                            allIndexedFiles = state.allIndexedFiles.filterNot { it.id in deletedIds || it.pathOrUri in deletedPaths },
                            compressedFiles = state.compressedFiles.filterNot { it.id in deletedIds || it.pathOrUri in deletedPaths },
                            documentFiles = state.documentFiles.filterNot { it.id in deletedIds || it.pathOrUri in deletedPaths },
                            recentFiles = state.recentFiles.filterNot { it.id in deletedIds || it.pathOrUri in deletedPaths },
                            snackbarMessage = "Permanently deleted $count item(s)"
                        )
                    }
                    onRefreshAfterChange()
                }
                .onFailure { onMessage(it.localizedMessage ?: "Delete failed") }
        }
    }

    fun moveFilesToTrash(items: List<FileItem>) {
        if (items.isEmpty()) return
        scope.launch {
            repo.moveItemsToTrash(items)
                .onSuccess { moved ->
                    if (moved > 0) {
                        items.filter { it.isDirectory }.forEach { folder ->
                            onRemoveFolderState(folder.pathOrUri)
                        }
                        fileTagRepo.removeTagsForDeletedItems(items)
                        albumRepo.purgeDeletedFiles(items)
                        val refreshedTrash = repo.listTrashEntries()
                        val movedIds = items.mapTo(HashSet()) { it.id }
                        val movedPaths = items.mapTo(HashSet()) { it.pathOrUri }
                        val msg = if (moved == 1) {
                            "Moved '${items.first().name}' to Recycle Bin"
                        } else {
                            "Moved $moved item(s) to Recycle Bin"
                        }
                        uiState.update { state ->
                            state.copy(
                                selectedIds = emptySet(),
                                trashEntries = refreshedTrash,
                                allIndexedFiles = state.allIndexedFiles.filterNot { it.id in movedIds || it.pathOrUri in movedPaths },
                                compressedFiles = state.compressedFiles.filterNot { it.id in movedIds || it.pathOrUri in movedPaths },
                                documentFiles = state.documentFiles.filterNot { it.id in movedIds || it.pathOrUri in movedPaths },
                                recentFiles = state.recentFiles.filterNot { it.id in movedIds || it.pathOrUri in movedPaths },
                                snackbarMessage = msg
                            )
                        }
                        onRefreshAfterChange()
                    } else {
                        onMessage("Unable to move selected item(s) to Recycle Bin")
                    }
                }
                .onFailure { onMessage(it.localizedMessage ?: "Move to Recycle Bin failed") }
        }
    }

    fun restoreTrashEntry(entry: TrashEntry) {
        scope.launch {
            repo.restoreTrashEntry(entry)
                .onSuccess {
                    uiState.update { state ->
                        state.copy(
                            trashEntries = state.trashEntries.filterNot { it.id == entry.id },
                            snackbarMessage = "Restored '${entry.originalName}'"
                        )
                    }
                    onRefreshAfterChange()
                }
                .onFailure { onMessage(it.localizedMessage ?: "Restore failed") }
        }
    }

    fun restoreTrashEntries(entries: List<TrashEntry>) {
        if (entries.isEmpty()) return
        scope.launch {
            repo.restoreTrashEntries(entries)
                .onSuccess { restoredCount ->
                    val refreshed = repo.listTrashEntries()
                    uiState.update { state ->
                        state.copy(
                            trashEntries = refreshed,
                            snackbarMessage = "Restored $restoredCount item(s) from Recycle Bin"
                        )
                    }
                    onRefreshAfterChange()
                }
                .onFailure { onMessage(it.localizedMessage ?: "Restore failed") }
        }
    }

    fun deleteTrashEntryForever(entry: TrashEntry) {
        scope.launch {
            repo.deleteTrashEntryForever(entry)
                .onSuccess {
                    uiState.update { state ->
                        state.copy(
                            trashEntries = state.trashEntries.filterNot { it.id == entry.id },
                            snackbarMessage = "Permanently deleted '${entry.originalName}'"
                        )
                    }
                    onRefreshAfterChange()
                }
                .onFailure { onMessage(it.localizedMessage ?: "Delete failed") }
        }
    }

    fun deleteTrashEntriesForever(entries: List<TrashEntry>) {
        if (entries.isEmpty()) return
        scope.launch {
            repo.deleteTrashEntriesForever(entries)
                .onSuccess { deletedCount ->
                    val refreshed = repo.listTrashEntries()
                    uiState.update { state ->
                        state.copy(
                            trashEntries = refreshed,
                            snackbarMessage = "Permanently deleted $deletedCount item(s)"
                        )
                    }
                    onRefreshAfterChange()
                }
                .onFailure { onMessage(it.localizedMessage ?: "Permanent delete failed") }
        }
    }

    fun emptyTrash() {
        scope.launch {
            repo.emptyTrash()
                .onSuccess { count ->
                    uiState.update { state ->
                        state.copy(
                            trashEntries = emptyList(),
                            snackbarMessage = "Emptied Recycle Bin ($count item(s))"
                        )
                    }
                    onRefreshAfterChange()
                }
                .onFailure { onMessage(it.localizedMessage ?: "Failed to empty Recycle Bin") }
        }
    }

    fun importPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val target = resolveTargetDirectoryForCreation()
        scope.launch {
            repo.importUrisToTarget(uris, target.first, target.second)
                .onSuccess { count ->
                    onMessage("Imported $count file(s)")
                    onRefreshAfterChange()
                }
                .onFailure { onMessage(it.localizedMessage ?: "Import failed") }
        }
    }
}
