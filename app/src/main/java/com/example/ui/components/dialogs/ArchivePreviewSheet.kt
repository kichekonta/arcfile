package com.example.ui.components.dialogs

import android.os.Environment
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ArchiveFormat
import com.example.data.ArchiveInspectionResult
import com.example.data.ArchiveOverwriteMode
import com.example.data.ArchiveTestResult
import com.example.data.DriveInfo
import com.example.data.DriveType
import com.example.data.FileItem
import com.example.data.FormatUtils
import com.example.data.ZipEntryInfo
import com.example.ui.components.common.ArcIosGroupedCard
import com.example.ui.components.common.ArcIosModal
import com.example.ui.components.common.ArcIosPrimaryButton
import com.example.ui.components.common.ArcIosSecondaryButton
import com.example.ui.components.common.ArcIosSectionHeader
import com.example.ui.components.common.ArcIosSegmentedControl
import com.example.ui.components.common.ArcIosTintedButton
import com.example.ui.components.common.ArcUnderlineTextField
import com.example.ui.registry.BinaryFileItemIcon
import com.example.ui.theme.ArcTheme
import com.example.ui.theme.BinaryAmber
import com.example.ui.theme.BinaryEmerald
import com.example.ui.theme.RobotoFontFamily
import kotlinx.coroutines.launch
import java.io.File

@Immutable
data class ArchiveExtractRequest(
    val targetParentPathOrUri: String? = null,
    val isTargetSaf: Boolean? = null,
    val extractToSubfolder: Boolean = true,
    val customSubfolderName: String? = null,
    val overwriteMode: ArchiveOverwriteMode = ArchiveOverwriteMode.OVERWRITE,
    val selectedEntryPaths: Set<String>? = null,
    val password: String? = null,
    val openDestinationWhenDone: Boolean = false
)

private enum class ArchiveModalPage {
    BROWSE,
    EXTRACT_OPTIONS
}

private enum class ExtractScopeOption(val label: String) {
    ALL("All Files"),
    SELECTED("Selected")
}

private data class ArchiveBrowserRow(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val uncompressedSize: Long,
    val compressedSize: Long,
    val isEncrypted: Boolean,
    val childCount: Int = 0
)

private fun buildVisibleArchiveRows(
    entries: List<ZipEntryInfo>,
    currentFolderPath: String,
    searchQuery: String
): List<ArchiveBrowserRow> {
    if (entries.isEmpty()) return emptyList()
    val query = searchQuery.trim().lowercase()
    if (query.isNotEmpty()) {
        return entries.mapNotNull { entry ->
            val clean = entry.name.replace('\\', '/').trimStart('/').trimEnd('/')
            if (clean.isBlank() || !clean.lowercase().contains(query)) return@mapNotNull null
            ArchiveBrowserRow(
                name = clean,
                fullPath = clean,
                isDirectory = entry.isDirectory,
                uncompressedSize = entry.uncompressedSize,
                compressedSize = entry.compressedSize,
                isEncrypted = entry.isEncrypted
            )
        }.sortedWith(compareBy<ArchiveBrowserRow> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    val prefix = currentFolderPath.trim('/').let { if (it.isEmpty()) "" else "$it/" }
    val folderDescendants = linkedMapOf<String, Int>()
    val folderEncrypted = hashMapOf<String, Boolean>()
    val explicitFolders = hashSetOf<String>()
    val directFiles = mutableListOf<ArchiveBrowserRow>()

    for (entry in entries) {
        val clean = entry.name.replace('\\', '/').trimStart('/').trimEnd('/')
        if (clean.isBlank()) continue
        if (prefix.isNotEmpty() && !clean.startsWith(prefix)) continue
        val rel = if (prefix.isEmpty()) clean else clean.removePrefix(prefix)
        if (rel.isEmpty()) continue

        val slashIdx = rel.indexOf('/')
        if (slashIdx >= 0) {
            val childFolderName = rel.substring(0, slashIdx)
            val childFolderFull = if (prefix.isEmpty()) childFolderName else "$prefix$childFolderName"
            explicitFolders.add(childFolderFull)
            folderDescendants[childFolderFull] = (folderDescendants[childFolderFull] ?: 0) + 1
            if (entry.isEncrypted) folderEncrypted[childFolderFull] = true
        } else if (entry.isDirectory) {
            val childFolderFull = if (prefix.isEmpty()) rel else "$prefix$rel"
            explicitFolders.add(childFolderFull)
            folderDescendants.putIfAbsent(childFolderFull, 0)
            if (entry.isEncrypted) folderEncrypted[childFolderFull] = true
        } else {
            directFiles += ArchiveBrowserRow(
                name = rel,
                fullPath = clean,
                isDirectory = false,
                uncompressedSize = entry.uncompressedSize,
                compressedSize = entry.compressedSize,
                isEncrypted = entry.isEncrypted
            )
        }
    }

    val folderRows = explicitFolders.map { full ->
        ArchiveBrowserRow(
            name = full.substringAfterLast('/'),
            fullPath = full,
            isDirectory = true,
            uncompressedSize = 0L,
            compressedSize = 0L,
            isEncrypted = folderEncrypted[full] == true,
            childCount = folderDescendants[full] ?: 0
        )
    }.sortedBy { it.name.lowercase() }

    val sortedFiles = directFiles.sortedBy { it.name.lowercase() }
    return folderRows + sortedFiles
}

@Composable
fun ArchivePreviewDialog(
    item: FileItem,
    inspection: ArchiveInspectionResult?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRetryWithPassword: (String) -> Unit,
    onExtract: (extractToSubfolder: Boolean, password: String?) -> Unit,
    onOpenAsFallback: () -> Unit,
    isTesting: Boolean = false,
    testResult: ArchiveTestResult? = null,
    drives: List<DriveInfo> = emptyList(),
    defaultExtractPath: Pair<String, Boolean>? = null,
    onLoadSubfolders: (suspend (String, Boolean) -> List<FileItem>)? = null,
    onCreateDestinationFolder: (suspend (String, String) -> Result<File>)? = null,
    onTestArchive: ((password: String?) -> Unit)? = null,
    onPreviewEntry: ((entryPath: String, password: String?) -> Unit)? = null,
    onExtractWithOptions: ((ArchiveExtractRequest) -> Unit)? = null
) {
    val archiveBaseName = remember(item.name) {
        item.name.substringBeforeLast('.', item.name).ifBlank { "extracted" }
    }

    var page by remember(item.id) { mutableStateOf(ArchiveModalPage.BROWSE) }
    var password by remember(item.id) { mutableStateOf("") }
    var showPassword by remember(item.id) { mutableStateOf(false) }
    var currentFolderPath by remember(item.id) { mutableStateOf("") }
    var searchQuery by remember(item.id) { mutableStateOf("") }
    var selectMode by remember(item.id) { mutableStateOf(false) }
    var selectedEntries by remember(item.id) { mutableStateOf(setOf<String>()) }

    // WinRAR Extract Files... page state
    val initialDestPath = remember(item.id, defaultExtractPath) {
        defaultExtractPath?.first ?: item.parentPathOrUri.ifBlank {
            runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrDefault("/storage/emulated/0")
        }
    }
    val initialDestIsSaf = remember(item.id, defaultExtractPath) {
        defaultExtractPath?.second ?: item.isSafUri
    }
    var destPath by remember(item.id) { mutableStateOf(initialDestPath) }
    var destIsSaf by remember(item.id) { mutableStateOf(initialDestIsSaf) }
    var createSubfolderOpt by remember(item.id) { mutableStateOf(true) }
    var customSubfolderName by remember(item.id) { mutableStateOf(archiveBaseName) }
    var overwriteMode by remember(item.id) { mutableStateOf(ArchiveOverwriteMode.OVERWRITE) }
    var openAfterExtract by remember(item.id) { mutableStateOf(true) }
    var extractScope by remember(item.id) { mutableStateOf(ExtractScopeOption.ALL) }
    var destSubfolders by remember(item.id) { mutableStateOf<List<FileItem>>(emptyList()) }
    var destFoldersLoading by remember(item.id) { mutableStateOf(false) }
    var showNewFolderField by remember(item.id) { mutableStateOf(false) }
    var newFolderName by remember(item.id) { mutableStateOf("") }
    var folderCreateError by remember(item.id) { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(page, destPath, destIsSaf) {
        if (page == ArchiveModalPage.EXTRACT_OPTIONS && onLoadSubfolders != null) {
            destFoldersLoading = true
            destSubfolders = runCatching { onLoadSubfolders(destPath, destIsSaf) }.getOrDefault(emptyList())
            destFoldersLoading = false
        }
    }

    val formatLabel = remember(item, inspection) {
        (inspection?.format ?: ArchiveFormat.fromExtension(item.extension))
            ?.extension?.uppercase() ?: item.extension.uppercase().ifBlank { "ARCHIVE" }
    }
    val entries = inspection?.entries.orEmpty()
    val visibleRows = remember(entries, currentFolderPath, searchQuery) {
        buildVisibleArchiveRows(entries, currentFolderPath, searchQuery)
    }
    val isEncrypted = inspection?.isEncrypted == true
    val errorNote = inspection?.errorNote
    val needsPasswordInput = isEncrypted || (errorNote?.contains("password", ignoreCase = true) == true)
    val isUnsupportedRar5 = errorNote?.contains("RAR5", ignoreCase = true) == true
    val canExtract = !isLoading && (entries.isNotEmpty() || needsPasswordInput) && !isUnsupportedRar5
    val totalUnpacked = inspection?.totalUncompressedBytes ?: entries.sumOf { if (it.isDirectory) 0L else it.uncompressedSize }
    val totalEntriesCount = (inspection?.totalEntryCount ?: entries.size).coerceAtLeast(entries.size)
    val compressionRatioPct = remember(item.sizeBytes, totalUnpacked) {
        if (totalUnpacked > 0L && item.sizeBytes > 0L) {
            ((1.0 - (item.sizeBytes.toDouble() / totalUnpacked.toDouble())) * 100.0)
                .toInt()
                .coerceIn(0, 99)
        } else null
    }

    val subtitleText = remember(formatLabel, totalEntriesCount, item.sizeBytes, isEncrypted, page, destPath) {
        if (page == ArchiveModalPage.EXTRACT_OPTIONS) {
            "Destination & WinRAR extraction options"
        } else {
            val encPart = if (isEncrypted) "Encrypted · " else ""
            "$formatLabel · $encPart$totalEntriesCount items · ${FormatUtils.formatBytes(item.sizeBytes)}"
        }
    }

    ArcIosModal(
        title = if (page == ArchiveModalPage.BROWSE) item.name else "Extract Files",
        subtitle = subtitleText,
        onDismiss = onDismiss,
        closeButtonTag = "archive_close_button",
        modifier = Modifier.testTag("archive_preview_dialog"),
        onBackClick = if (page == ArchiveModalPage.EXTRACT_OPTIONS) {
            { page = ArchiveModalPage.BROWSE }
        } else null,
        backLabel = "Archive",
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BinaryAmber.copy(alpha = if (ArcTheme.isDark) 0.22f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderZip,
                    contentDescription = null,
                    tint = BinaryAmber,
                    modifier = Modifier.size(21.dp)
                )
            }
        },
        headerActions = if (page == ArchiveModalPage.BROWSE && !isLoading) {
            {
                if (onTestArchive != null && !isUnsupportedRar5 && entries.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !isTesting) {
                                onTestArchive(password.ifEmpty { null })
                            }
                            .testTag("archive_test_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 1.8.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.VerifiedUser,
                                    contentDescription = "Test Archive",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "Test",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (entries.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectMode) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selectMode = !selectMode
                                if (!selectMode) selectedEntries = emptySet()
                            }
                            .testTag("archive_select_mode_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Checklist,
                                contentDescription = if (selectMode) "Done selecting" else "Select files",
                                tint = if (selectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (selectMode) "Done" else "Select",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        } else null,
        footer = {
            if (page == ArchiveModalPage.BROWSE) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedEntries.isNotEmpty()) {
                        ArcIosPrimaryButton(
                            text = "Extract ${selectedEntries.size} Selected to \"$archiveBaseName/\"",
                            icon = Icons.Filled.Checklist,
                            enabled = canExtract,
                            onClick = {
                                val pwd = password.ifEmpty { null }
                                if (onExtractWithOptions != null) {
                                    onExtractWithOptions(
                                        ArchiveExtractRequest(
                                            extractToSubfolder = true,
                                            customSubfolderName = archiveBaseName,
                                            selectedEntryPaths = selectedEntries,
                                            password = pwd
                                        )
                                    )
                                } else {
                                    onExtract(true, pwd)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("archive_extract_selected_button")
                        )
                    }

                    ArcIosPrimaryButton(
                        text = "Extract to \"$archiveBaseName/\"",
                        icon = Icons.Filled.CreateNewFolder,
                        enabled = canExtract,
                        onClick = { onExtract(true, password.ifEmpty { null }) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("archive_extract_folder_button")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArcIosTintedButton(
                            text = "Extract Here",
                            icon = Icons.Filled.Unarchive,
                            enabled = canExtract,
                            onClick = { onExtract(false, password.ifEmpty { null }) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("archive_extract_here_button")
                        )
                        ArcIosSecondaryButton(
                            text = "Extract Files...",
                            icon = Icons.AutoMirrored.Filled.DriveFileMove,
                            enabled = canExtract,
                            onClick = {
                                extractScope = if (selectedEntries.isNotEmpty()) {
                                    ExtractScopeOption.SELECTED
                                } else {
                                    ExtractScopeOption.ALL
                                }
                                page = ArchiveModalPage.EXTRACT_OPTIONS
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("archive_extract_files_button")
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArcIosSecondaryButton(
                        text = "Back",
                        onClick = { page = ArchiveModalPage.BROWSE },
                        modifier = Modifier.weight(0.8f)
                    )
                    ArcIosPrimaryButton(
                        text = "Extract Now",
                        icon = Icons.Filled.Unarchive,
                        enabled = canExtract && (!createSubfolderOpt || customSubfolderName.isNotBlank()),
                        onClick = {
                            val pwd = password.ifEmpty { null }
                            val chosenPaths = if (extractScope == ExtractScopeOption.SELECTED && selectedEntries.isNotEmpty()) {
                                selectedEntries
                            } else null
                            if (onExtractWithOptions != null) {
                                onExtractWithOptions(
                                    ArchiveExtractRequest(
                                        targetParentPathOrUri = destPath,
                                        isTargetSaf = destIsSaf,
                                        extractToSubfolder = createSubfolderOpt,
                                        customSubfolderName = customSubfolderName.trim().ifBlank { archiveBaseName },
                                        overwriteMode = overwriteMode,
                                        selectedEntryPaths = chosenPaths,
                                        password = pwd,
                                        openDestinationWhenDone = openAfterExtract
                                    )
                                )
                            } else {
                                onExtract(createSubfolderOpt, pwd)
                            }
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("archive_extract_files_confirm_button")
                    )
                }
            }
        }
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "archive_modal_page"
        ) { currentPage ->
            if (currentPage == ArchiveModalPage.BROWSE) {
                ArchiveBrowseBody(
                    item = item,
                    entries = entries,
                    visibleRows = visibleRows,
                    isLoading = isLoading,
                    isEncrypted = isEncrypted,
                    needsPasswordInput = needsPasswordInput,
                    errorNote = errorNote,
                    totalEntriesCount = totalEntriesCount,
                    totalUnpacked = totalUnpacked,
                    compressionRatioPct = compressionRatioPct,
                    testResult = testResult,
                    password = password,
                    onPasswordChange = { password = it },
                    showPassword = showPassword,
                    onToggleShowPassword = { showPassword = !showPassword },
                    onRetryWithPassword = { onRetryWithPassword(password) },
                    onOpenAsFallback = onOpenAsFallback,
                    currentFolderPath = currentFolderPath,
                    onNavigateFolder = { currentFolderPath = it },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    selectMode = selectMode,
                    selectedEntries = selectedEntries,
                    onToggleEntrySelection = { path ->
                        selectedEntries = if (path in selectedEntries) selectedEntries - path else selectedEntries + path
                    },
                    onSelectAllVisible = {
                        val allVisiblePaths = visibleRows.map { it.fullPath }.toSet()
                        selectedEntries = if (allVisiblePaths.isNotEmpty() && allVisiblePaths.all { it in selectedEntries }) {
                            selectedEntries - allVisiblePaths
                        } else {
                            selectedEntries + allVisiblePaths
                        }
                    },
                    onPreviewEntry = onPreviewEntry?.let { cb ->
                        { path -> cb(path, password.ifEmpty { null }) }
                    }
                )
            } else {
                ArchiveExtractOptionsBody(
                    item = item,
                    archiveBaseName = archiveBaseName,
                    drives = drives,
                    destPath = destPath,
                    destIsSaf = destIsSaf,
                    destSubfolders = destSubfolders,
                    destFoldersLoading = destFoldersLoading,
                    onSelectDestination = { newPath, isSaf ->
                        destPath = newPath
                        destIsSaf = isSaf
                        folderCreateError = null
                    },
                    createSubfolderOpt = createSubfolderOpt,
                    onToggleCreateSubfolder = { createSubfolderOpt = it },
                    customSubfolderName = customSubfolderName,
                    onCustomSubfolderNameChange = { customSubfolderName = it },
                    overwriteMode = overwriteMode,
                    onOverwriteModeChange = { overwriteMode = it },
                    selectedCount = selectedEntries.size,
                    extractScope = extractScope,
                    onExtractScopeChange = { extractScope = it },
                    openAfterExtract = openAfterExtract,
                    onToggleOpenAfterExtract = { openAfterExtract = it },
                    showNewFolderField = showNewFolderField,
                    onToggleNewFolderField = {
                        showNewFolderField = it
                        folderCreateError = null
                    },
                    newFolderName = newFolderName,
                    onNewFolderNameChange = { newFolderName = it },
                    folderCreateError = folderCreateError,
                    onCreateFolder = if (!destIsSaf && onCreateDestinationFolder != null) {
                        {
                            val clean = newFolderName.trim()
                            if (clean.isNotEmpty()) {
                                coroutineScope.launch {
                                    onCreateDestinationFolder(destPath, clean)
                                        .onSuccess { createdDir ->
                                            destPath = createdDir.absolutePath
                                            newFolderName = ""
                                            showNewFolderField = false
                                            folderCreateError = null
                                        }
                                        .onFailure { err ->
                                            folderCreateError = err.localizedMessage ?: "Failed to create folder"
                                        }
                                }
                            }
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun ArchiveBrowseBody(
    item: FileItem,
    entries: List<ZipEntryInfo>,
    visibleRows: List<ArchiveBrowserRow>,
    isLoading: Boolean,
    isEncrypted: Boolean,
    needsPasswordInput: Boolean,
    errorNote: String?,
    totalEntriesCount: Int,
    totalUnpacked: Long,
    compressionRatioPct: Int?,
    testResult: ArchiveTestResult?,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onToggleShowPassword: () -> Unit,
    onRetryWithPassword: () -> Unit,
    onOpenAsFallback: () -> Unit,
    currentFolderPath: String,
    onNavigateFolder: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectMode: Boolean,
    selectedEntries: Set<String>,
    onToggleEntrySelection: (String) -> Unit,
    onSelectAllVisible: () -> Unit,
    onPreviewEntry: ((String) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Compact Archive stats inset strip
        if (!isLoading && entries.isNotEmpty()) {
            ArcIosGroupedCard(cornerRadius = 12.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Packed ${FormatUtils.formatBytes(item.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = if (totalUnpacked > 0L) "Unpacked ${FormatUtils.formatBytes(totalUnpacked)}" else "Unpacked —",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = compressionRatioPct?.let { "Saved $it%" } ?: "$totalEntriesCount items",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (compressionRatioPct != null && compressionRatioPct > 0) BinaryEmerald else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }

        if (testResult != null) {
            val accent = if (testResult.passed) BinaryEmerald else MaterialTheme.colorScheme.error
            Surface(
                shape = RoundedCornerShape(13.dp),
                color = accent.copy(alpha = if (ArcTheme.isDark) 0.18f else 0.10f),
                border = BorderStroke(0.6.dp, accent.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("archive_test_result_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (testResult.passed) Icons.Filled.VerifiedUser else Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = testResult.summary,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (!errorNote.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("archive_error_banner")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = errorNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (entries.isEmpty() && !needsPasswordInput) {
                        ArcIosSecondaryButton(
                            text = "Open As External App",
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            onClick = onOpenAsFallback,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("archive_open_as_fallback_button")
                        )
                    }
                }
            }
        }

        if (needsPasswordInput) {
            ArcIosGroupedCard(cornerRadius = 14.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArcUnderlineTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = "Archive Password",
                        placeholder = "Enter password",
                        showClearButton = false,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onToggleShowPassword),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        testTag = "archive_extract_password_input",
                        modifier = Modifier.weight(1f)
                    )
                    ArcIosTintedButton(
                        text = "Unlock",
                        enabled = password.isNotEmpty() && !isLoading,
                        onClick = onRetryWithPassword,
                        modifier = Modifier.testTag("archive_unlock_button")
                    )
                }
            }
        }

        // iOS Search Pill & Folder Breadcrumbs
        if (!isLoading && entries.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search inside archive",
                                    style = TextStyle(
                                        fontFamily = RobotoFontFamily,
                                        fontSize = 13.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = RobotoFontFamily,
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("archive_search_input")
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onSearchQueryChange("") }
                            )
                        }
                    }
                }

                if (selectMode) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onSelectAllVisible)
                            .testTag("archive_select_all_button")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "All",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (searchQuery.isBlank() && currentFolderPath.isNotEmpty()) {
                val segments = currentFolderPath.split('/').filter { it.isNotBlank() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BreadcrumbChip(
                        label = "Root",
                        isActive = false,
                        onClick = { onNavigateFolder("") }
                    )
                    var running = ""
                    segments.forEachIndexed { idx, seg ->
                        running = if (running.isEmpty()) seg else "$running/$seg"
                        val targetPath = running
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        BreadcrumbChip(
                            label = seg,
                            isActive = idx == segments.lastIndex,
                            onClick = { onNavigateFolder(targetPath) }
                        )
                    }
                }
            }
        }

        // Archive Contents Grouped Card
        ArcIosGroupedCard(
            cornerRadius = 16.dp,
            modifier = if (visibleRows.size > 40) {
                Modifier.heightIn(min = 104.dp, max = 320.dp)
            } else {
                Modifier
            }
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
                entries.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (needsPasswordInput) {
                                "Enter the password above to inspect or extract contents."
                            } else {
                                "No readable entries to display."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                visibleRows.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching files found." else "This folder is empty.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                visibleRows.size <= 40 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("archive_entry_list")
                    ) {
                        visibleRows.forEachIndexed { idx, row ->
                            ArchiveEntryRowItem(
                                idx = idx,
                                row = row,
                                isSelected = row.fullPath in selectedEntries,
                                selectMode = selectMode,
                                archiveEncrypted = isEncrypted,
                                onClick = {
                                    if (selectMode) {
                                        onToggleEntrySelection(row.fullPath)
                                    } else if (row.isDirectory) {
                                        onNavigateFolder(row.fullPath)
                                    } else {
                                        onPreviewEntry?.invoke(row.fullPath)
                                    }
                                }
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("archive_entry_list")
                    ) {
                        itemsIndexed(visibleRows, key = { _, row -> "${row.isDirectory}:${row.fullPath}" }) { idx, row ->
                            ArchiveEntryRowItem(
                                idx = idx,
                                row = row,
                                isSelected = row.fullPath in selectedEntries,
                                selectMode = selectMode,
                                archiveEncrypted = isEncrypted,
                                onClick = {
                                    if (selectMode) {
                                        onToggleEntrySelection(row.fullPath)
                                    } else if (row.isDirectory) {
                                        onNavigateFolder(row.fullPath)
                                    } else {
                                        onPreviewEntry?.invoke(row.fullPath)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveEntryRowItem(
    idx: Int,
    row: ArchiveBrowserRow,
    isSelected: Boolean,
    selectMode: Boolean,
    archiveEncrypted: Boolean,
    onClick: () -> Unit
) {
    if (idx > 0) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(start = 48.dp)
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("archive_entry_${row.fullPath}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (selectMode) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        } else if (row.isDirectory) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
            }
        } else {
            val syntheticItem = remember(row.fullPath, row.name, row.uncompressedSize) {
                val ext = row.name.substringAfterLast('.', "").lowercase()
                FileItem(
                    id = row.fullPath,
                    name = row.name,
                    pathOrUri = row.fullPath,
                    isDirectory = false,
                    sizeBytes = row.uncompressedSize,
                    lastModifiedMillis = 0L,
                    mimeType = "",
                    extension = ext,
                    isSafUri = false,
                    parentPathOrUri = ""
                )
            }
            BinaryFileItemIcon(
                item = syntheticItem,
                monochrome = false,
                size = 30.dp
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (row.isDirectory) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!row.isDirectory && row.compressedSize > 0L && row.uncompressedSize > 0L) {
                Text(
                    text = "${FormatUtils.formatBytes(row.uncompressedSize)} · ${FormatUtils.formatBytes(row.compressedSize)} packed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            } else if (row.isDirectory) {
                Text(
                    text = "${row.childCount} item(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (row.isEncrypted || archiveEncrypted && !row.isDirectory && row.compressedSize == 0L) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Encrypted entry",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
        }

        if (!row.isDirectory) {
            Text(
                text = FormatUtils.formatBytes(row.uncompressedSize),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (row.isDirectory && !selectMode) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open folder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ArchiveExtractOptionsBody(
    item: FileItem,
    archiveBaseName: String,
    drives: List<DriveInfo>,
    destPath: String,
    destIsSaf: Boolean,
    destSubfolders: List<FileItem>,
    destFoldersLoading: Boolean,
    onSelectDestination: (String, Boolean) -> Unit,
    createSubfolderOpt: Boolean,
    onToggleCreateSubfolder: (Boolean) -> Unit,
    customSubfolderName: String,
    onCustomSubfolderNameChange: (String) -> Unit,
    overwriteMode: ArchiveOverwriteMode,
    onOverwriteModeChange: (ArchiveOverwriteMode) -> Unit,
    selectedCount: Int,
    extractScope: ExtractScopeOption,
    onExtractScopeChange: (ExtractScopeOption) -> Unit,
    openAfterExtract: Boolean,
    onToggleOpenAfterExtract: (Boolean) -> Unit,
    showNewFolderField: Boolean,
    onToggleNewFolderField: (Boolean) -> Unit,
    newFolderName: String,
    onNewFolderNameChange: (String) -> Unit,
    folderCreateError: String?,
    onCreateFolder: (() -> Unit)?
) {
    val quickDestinations = remember(item, drives) {
        val list = mutableListOf<Triple<String, String, Boolean>>()
        if (!item.isSafUri && item.parentPathOrUri.isNotBlank()) {
            list += Triple("Same Folder", item.parentPathOrUri, false)
        }
        runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) }
            .getOrNull()?.let { list += Triple("Downloads", it.absolutePath, false) }
        runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) }
            .getOrNull()?.let { list += Triple("Documents", it.absolutePath, false) }
        drives.filter { it.type != DriveType.WORKSPACE }.forEach { d ->
            list += Triple(d.name, d.pathOrUri, d.isSaf || d.type == DriveType.SAF_TREE)
        }
        list.distinctBy { it.second }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ArcIosSectionHeader("Destination Folder")

        ArcIosGroupedCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (destIsSaf) "Mounted Drive" else destPath,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (onCreateFolder != null) {
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { onToggleNewFolderField(!showNewFolderField) }
                                .testTag("archive_new_folder_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CreateNewFolder,
                                    contentDescription = "New Folder",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "New",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (showNewFolderField && onCreateFolder != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArcUnderlineTextField(
                            value = newFolderName,
                            onValueChange = onNewFolderNameChange,
                            label = "Create Subfolder",
                            placeholder = "Folder name",
                            isError = folderCreateError != null,
                            testTag = "archive_new_folder_input",
                            modifier = Modifier.weight(1f)
                        )
                        ArcIosTintedButton(
                            text = "Create",
                            enabled = newFolderName.isNotBlank(),
                            onClick = onCreateFolder,
                            modifier = Modifier.testTag("archive_create_folder_confirm")
                        )
                    }
                    if (!folderCreateError.isNullOrBlank()) {
                        Text(
                            text = folderCreateError,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Quick storage targets row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickDestinations.forEach { (label, path, isSaf) ->
                        val active = destPath == path && destIsSaf == isSaf
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectDestination(path, isSaf) }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // Inline subfolder navigator
                if (!destIsSaf) {
                    val parentFile = remember(destPath) { File(destPath).parentFile }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp, max = 148.dp)
                    ) {
                        if (destFoldersLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                if (parentFile != null && parentFile.canRead()) {
                                    item(key = "__parent__") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 42.dp)
                                                .clickable { onSelectDestination(parentFile.absolutePath, false) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ArrowUpward,
                                                contentDescription = "Parent folder",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Up to ${parentFile.name.ifBlank { "/" }}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                itemsIndexed(destSubfolders, key = { _, f -> f.id }) { idx, folder ->
                                    if (idx > 0 || (parentFile != null && parentFile.canRead())) {
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 42.dp)
                                            .clickable { onSelectDestination(folder.pathOrUri, folder.isSafUri) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (destSubfolders.isEmpty() && (parentFile == null || !parentFile.canRead())) {
                                    item(key = "__empty__") {
                                        Text(
                                            text = "No subfolders here",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ArcIosSectionHeader("WinRAR Extraction Settings")

        ArcIosGroupedCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Extract to Subfolder",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Creates a folder named after the archive",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = createSubfolderOpt,
                        onCheckedChange = onToggleCreateSubfolder,
                        modifier = Modifier.testTag("archive_options_subfolder_switch")
                    )
                }

                if (createSubfolderOpt) {
                    ArcUnderlineTextField(
                        value = customSubfolderName,
                        onValueChange = onCustomSubfolderNameChange,
                        label = "Subfolder Name",
                        placeholder = archiveBaseName,
                        testTag = "archive_options_subfolder_input",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (selectedCount > 0) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Files to Extract",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ArcIosSegmentedControl(
                            options = ExtractScopeOption.entries,
                            selectedOption = extractScope,
                            labelOf = {
                                if (it == ExtractScopeOption.SELECTED) "Selected ($selectedCount)" else it.label
                            },
                            onSelect = onExtractScopeChange,
                            tagPrefix = "archive_scope"
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Overwrite Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ArcIosSegmentedControl(
                        options = ArchiveOverwriteMode.entries,
                        selectedOption = overwriteMode,
                        labelOf = { it.shortLabel },
                        onSelect = onOverwriteModeChange,
                        tagPrefix = "archive_overwrite"
                    )
                }

                if (!destIsSaf) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Open Folder After Extract",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Jump straight to extracted files",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = openAfterExtract,
                            onCheckedChange = onToggleOpenAfterExtract,
                            modifier = Modifier.testTag("archive_options_open_dest_switch")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp
            ),
            color = if (highlight) BinaryEmerald else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun BreadcrumbChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !isActive, onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
