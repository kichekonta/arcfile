package com.example.features.archive

import com.example.data.ArchiveCompressionLevel
import com.example.data.ArchiveFormat
import com.example.data.ArchiveInspectionResult
import com.example.data.ArchiveOverwriteMode
import com.example.data.ArchiveTestResult
import com.example.data.FileExplorerRepository
import com.example.data.FileItem
import com.example.data.FileTransferProgress
import com.example.data.TabDestination
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
 * Feature-scoped coordinator owning Archive Inspection, Extraction, and Compression workflows.
 */
class ArchiveCoordinator(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<ExplorerUiState>,
    private val repo: FileExplorerRepository,
    private val isTransferActive: () -> Boolean,
    private val registerTransferJob: (Job?) -> Unit,
    private val onRefreshAfterChange: () -> Unit,
    private val onMessage: (String) -> Unit
) {

    fun openArchivePreview(item: FileItem, password: String? = null) {
        uiState.update {
            it.copy(
                archivePreviewFile = item,
                archivePreviewLoading = true,
                archiveInspection = if (password == null) null else it.archiveInspection,
                archiveTestResult = null,
                archiveTesting = false,
                selectedIds = emptySet()
            )
        }
        scope.launch {
            val res = repo.inspectArchive(item, password = password)
            res.onSuccess { inspection ->
                uiState.update { state ->
                    if (state.archivePreviewFile?.id != item.id) state
                    else state.copy(archiveInspection = inspection, archivePreviewLoading = false)
                }
            }.onFailure { err ->
                val fmt = ArchiveFormat.fromExtension(item.extension) ?: ArchiveFormat.ZIP
                uiState.update { state ->
                    if (state.archivePreviewFile?.id != item.id) state
                    else state.copy(
                        archiveInspection = ArchiveInspectionResult(
                            format = fmt,
                            entries = emptyList(),
                            isEncrypted = false,
                            errorNote = err.localizedMessage ?: "Unable to read archive"
                        ),
                        archivePreviewLoading = false
                    )
                }
            }
        }
    }

    fun closeArchivePreview() {
        uiState.update {
            it.copy(
                archivePreviewFile = null,
                archiveInspection = null,
                archivePreviewLoading = false,
                archiveTestResult = null,
                archiveTesting = false
            )
        }
    }

    fun testArchive(item: FileItem, password: String? = null) {
        if (uiState.value.archiveTesting) return
        uiState.update { it.copy(archiveTesting = true, archiveTestResult = null) }
        scope.launch {
            val result = repo.testArchive(item, password = password)
            result.onSuccess { report ->
                uiState.update { state ->
                    if (state.archivePreviewFile?.id != item.id) state
                    else state.copy(archiveTesting = false, archiveTestResult = report)
                }
            }.onFailure { err ->
                uiState.update { state ->
                    if (state.archivePreviewFile?.id != item.id) state
                    else state.copy(
                        archiveTesting = false,
                        archiveTestResult = ArchiveTestResult(
                            testedFiles = 0,
                            passed = false,
                            summary = err.localizedMessage ?: "Archive test failed"
                        )
                    )
                }
            }
        }
    }

    fun previewArchiveEntry(
        item: FileItem,
        entryPath: String,
        password: String? = null,
        onExtractedReady: (FileItem) -> Unit
    ) {
        scope.launch {
            repo.extractSingleEntryToCache(item, entryPath, password)
                .onSuccess { extractedItem ->
                    onExtractedReady(extractedItem)
                }
                .onFailure { err ->
                    onMessage(err.localizedMessage ?: "Unable to preview entry")
                }
        }
    }

    fun resolveArchiveTargetDirectory(sourceItem: FileItem?): Pair<String, Boolean> {
        val activeDir = uiState.value.activeTab?.destination as? TabDestination.Directory
        if (activeDir != null) return activeDir.currentPathOrUri to activeDir.isSaf
        if (sourceItem != null && !sourceItem.isSafUri) {
            val parent = File(sourceItem.parentPathOrUri)
            if (parent.exists() && parent.canWrite()) {
                return parent.absolutePath to false
            }
        }
        return repo.getWorkspaceRoot().absolutePath to false
    }

    fun extractArchive(
        item: FileItem,
        extractToSubfolder: Boolean = true,
        password: String? = null,
        targetParentPathOrUriOverride: String? = null,
        isTargetSafOverride: Boolean? = null,
        customSubfolderName: String? = null,
        overwriteMode: ArchiveOverwriteMode = ArchiveOverwriteMode.OVERWRITE,
        selectedEntryPaths: Set<String>? = null,
        openDestinationWhenDone: Boolean = false,
        onOpenLocalDestination: ((File) -> Unit)? = null
    ) {
        if (isTransferActive()) {
            onMessage("An operation is already running")
            return
        }
        val (defaultPathOrUri, defaultIsSaf) = resolveArchiveTargetDirectory(item)
        val targetPathOrUri = targetParentPathOrUriOverride?.takeIf { it.isNotBlank() } ?: defaultPathOrUri
        val isTargetSaf = isTargetSafOverride ?: defaultIsSaf
        val folderName = customSubfolderName?.takeIf { it.isNotBlank() }
            ?: item.name.substringBeforeLast('.', item.name).ifBlank { "archive" }

        val destLabel = if (isTargetSaf) {
            if (extractToSubfolder) "Mounted Drive/$folderName" else "Mounted Drive"
        } else {
            val baseName = File(targetPathOrUri).name.ifBlank { "Folder" }
            if (extractToSubfolder) "$baseName/$folderName" else baseName
        }

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
                val result = repo.extractArchiveToDirectory(
                    item = item,
                    targetParentPathOrUri = targetPathOrUri,
                    isTargetSaf = isTargetSaf,
                    createSubfolder = extractToSubfolder,
                    customSubfolderName = customSubfolderName,
                    overwriteMode = overwriteMode,
                    selectedEntryPaths = selectedEntryPaths,
                    password = password,
                    destinationLabel = destLabel,
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
                    uiState.update { state ->
                        state.copy(
                            activeTransfer = null,
                            archivePreviewFile = null,
                            archiveInspection = null,
                            archivePreviewLoading = false,
                            archiveTestResult = null,
                            archiveTesting = false,
                            snackbarMessage = "Extracted $count file(s) to $destLabel"
                        )
                    }
                    onRefreshAfterChange()
                    if (openDestinationWhenDone && !isTargetSaf && onOpenLocalDestination != null) {
                        val destDir = if (extractToSubfolder) {
                            File(targetPathOrUri, folderName).takeIf { it.exists() } ?: File(targetPathOrUri)
                        } else {
                            File(targetPathOrUri)
                        }
                        if (destDir.exists()) {
                            onOpenLocalDestination(destDir)
                        }
                    }
                }.onFailure { err ->
                    val errMsg = err.localizedMessage ?: "Extraction failed"
                    val isPassIssue = errMsg.contains("password", ignoreCase = true)
                    uiState.update { state ->
                        val updatedInspection = if (state.archivePreviewFile?.id == item.id && isPassIssue) {
                            (state.archiveInspection ?: ArchiveInspectionResult(
                                format = ArchiveFormat.fromExtension(item.extension) ?: ArchiveFormat.ZIP,
                                entries = emptyList(),
                                isEncrypted = true
                            )).copy(isEncrypted = true, errorNote = errMsg)
                        } else state.archiveInspection
                        state.copy(
                            activeTransfer = null,
                            archiveInspection = updatedInspection,
                            snackbarMessage = errMsg
                        )
                    }
                }
            } catch (_: CancellationException) {
                showDelayJob.cancel()
                uiState.update {
                    it.copy(activeTransfer = null, snackbarMessage = "Extraction cancelled")
                }
                onRefreshAfterChange()
            } finally {
                showDelayJob.cancel()
                registerTransferJob(null)
            }
        }
        registerTransferJob(job)
    }

    fun openCompressDialog(items: List<FileItem>) {
        if (items.isEmpty()) return
        uiState.update { it.copy(compressTargets = items) }
    }

    fun dismissCompressDialog() {
        uiState.update { it.copy(compressTargets = null, selectedIds = emptySet()) }
    }

    fun createArchive(
        items: List<FileItem>,
        archiveName: String,
        format: ArchiveFormat,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.NORMAL,
        password: String? = null
    ) {
        if (items.isEmpty()) return
        if (!format.supportsCreation) {
            onMessage("${format.label} is extract-only. Select ZIP or 7-Zip.")
            return
        }
        if (isTransferActive()) {
            onMessage("An operation is already running")
            return
        }
        val (targetPathOrUri, isTargetSaf) = resolveArchiveTargetDirectory(items.firstOrNull())
        val baseName = archiveName.trim().ifBlank { "archive" }
        uiState.update { it.copy(compressTargets = null, selectedIds = emptySet()) }

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
                val result = repo.createArchive(
                    items = items,
                    targetParentPathOrUri = targetPathOrUri,
                    isTargetSaf = isTargetSaf,
                    archiveBaseName = baseName,
                    format = format,
                    compressionLevel = compressionLevel,
                    password = password,
                    destinationLabel = "$baseName.${format.extension}",
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
                result.onSuccess { createdItem ->
                    uiState.update {
                        it.copy(
                            activeTransfer = null,
                            snackbarMessage = "Created '${createdItem.name}'"
                        )
                    }
                    onRefreshAfterChange()
                }.onFailure { err ->
                    val errMsg = err.localizedMessage ?: "Compression failed"
                    uiState.update {
                        it.copy(activeTransfer = null, snackbarMessage = errMsg)
                    }
                }
            } catch (_: CancellationException) {
                showDelayJob.cancel()
                uiState.update {
                    it.copy(activeTransfer = null, snackbarMessage = "Compression cancelled")
                }
                onRefreshAfterChange()
            } finally {
                showDelayJob.cancel()
                registerTransferJob(null)
            }
        }
        registerTransferJob(job)
    }
}
