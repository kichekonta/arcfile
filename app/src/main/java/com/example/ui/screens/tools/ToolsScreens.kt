package com.example.ui.screens.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BookmarkEntry
import com.example.data.CategorySummary
import com.example.data.DriveInfo
import com.example.data.DriveType
import com.example.data.ExplorerCategory
import com.example.data.FileItem
import com.example.data.FormatUtils
import com.example.data.TrashEntry
import com.example.ui.components.common.ArcBadge
import com.example.ui.components.common.ArcCard
import com.example.ui.components.common.ArcEmptyStateCard
import com.example.ui.components.common.ArcIosGroupedCard
import com.example.ui.components.common.ArcIosModal
import com.example.ui.components.common.ArcIosPrimaryButton
import com.example.ui.components.common.ArcIosSecondaryButton
import com.example.ui.components.common.ArcIosTintedButton
import com.example.ui.components.common.ArcSectionHeader
import com.example.ui.components.common.ArcThumbnail
import com.example.ui.components.common.ArcUnifiedEmptyState
import com.example.ui.registry.BinaryCategoryIcon
import com.example.ui.registry.BinaryFileItemIcon
import com.example.ui.theme.ArcTheme

@Composable
fun SpaceAnalyzerScreen(
    drives: List<DriveInfo>,
    summaries: Map<ExplorerCategory, CategorySummary>,
    allIndexedFiles: List<FileItem>,
    monochrome: Boolean,
    onBack: () -> Unit,
    onCategoryClick: (ExplorerCategory) -> Unit,
    onFileClick: (FileItem) -> Unit,
    onDeleteFile: (FileItem) -> Unit,
    isActivePage: Boolean = true
) {
    BackHandler(enabled = isActivePage) { onBack() }
    val d = ArcTheme.dimens
    val internal = drives.firstOrNull { it.type == DriveType.INTERNAL }
    val largestFiles = remember(allIndexedFiles) { allIndexedFiles.sortedByDescending { it.sizeBytes }.take(15) }
    val totalIndexedBytes = remember(summaries) { summaries.values.sumOf { it.totalBytes }.coerceAtLeast(1L) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = d.maxContentWidth)
                .fillMaxSize()
                .testTag("analyzer_screen_list"),
            contentPadding = PaddingValues(
                start = d.screenHorizontalPadding,
                end = d.screenHorizontalPadding,
                top = 4.dp,
                bottom = navBottom + 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(d.sectionGap)
        ) {
            item {
                ScreenTitleRow(title = "Space Analyzer", badgeText = "${allIndexedFiles.size} files", onBack = onBack)
            }

            if (internal != null && internal.totalBytes > 0L) {
                item {
                    ArcCard(cornerRadius = d.cardRadius, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Internal Storage", style = MaterialTheme.typography.titleMedium)
                                Text("${FormatUtils.formatBytes(internal.usedBytes)} / ${FormatUtils.formatBytes(internal.totalBytes)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(
                                progress = { internal.usedFraction },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round
                            )
                            Text("${FormatUtils.formatBytes(internal.freeBytes)} available", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { ArcSectionHeader(title = "By Category", trailing = FormatUtils.formatBytes(totalIndexedBytes)) }

            item {
                ArcCard(cornerRadius = d.cardRadius, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExplorerCategory.entries.forEach { cat ->
                            val sum = summaries[cat] ?: CategorySummary(cat)
                            val frac = (sum.totalBytes.toFloat() / totalIndexedBytes.toFloat()).coerceIn(0f, 1f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                BinaryCategoryIcon(category = cat, monochrome = monochrome, size = 24.dp)
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(cat.label, style = MaterialTheme.typography.titleSmall)
                                        Text("${sum.itemCount} · ${FormatUtils.formatBytes(sum.totalBytes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    LinearProgressIndicator(
                                        progress = { frac },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                                OutlinedButton(onClick = { onCategoryClick(cat) }) {
                                    Text("Open")
                                }
                            }
                        }
                    }
                }
            }

            item { ArcSectionHeader(title = "Largest Files", trailing = "Top ${largestFiles.size}") }

            if (largestFiles.isEmpty()) {
                item {
                    ArcUnifiedEmptyState(
                        icon = Icons.Filled.PieChart,
                        title = "No indexed files yet",
                        subtitle = "Indexed files across internal storage and mounted folders will appear here.",
                        fillMaxSize = false
                    )
                }
            } else {
                items(largestFiles, key = { "lg_${it.id}" }) { file ->
                    ArcCard(cornerRadius = d.cardRadius, modifier = Modifier.fillMaxWidth(), onClick = { onFileClick(file) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ArcThumbnail(item = file, monochromeBinaryIcons = monochrome, size = 34.dp, iconSize = 26.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${FormatUtils.formatBytes(file.sizeBytes)} · ${file.parentPathOrUri}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onDeleteFile(file) }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete ${file.name}", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

        }
    }
}

private enum class RecycleBinFilter(val id: String, val label: String) {
    ALL("all", "All"),
    FOLDERS("folders", "Folders"),
    FILES("files", "Files")
}

private sealed interface TrashPermanentAction {
    data class Single(val entry: TrashEntry) : TrashPermanentAction
    data class Batch(val entries: List<TrashEntry>) : TrashPermanentAction
    data object EmptyAll : TrashPermanentAction
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecycleBinScreen(
    entries: List<TrashEntry>,
    onBack: () -> Unit,
    onRestore: (TrashEntry) -> Unit,
    onDeleteForever: (TrashEntry) -> Unit,
    onEmptyTrash: () -> Unit,
    recycleBinEnabled: Boolean = true,
    monochrome: Boolean = false,
    trashRootPath: String = "",
    onToggleRecycleBinEnabled: (Boolean) -> Unit = {},
    onRestoreSelected: (List<TrashEntry>) -> Unit = { list -> list.forEach(onRestore) },
    onDeleteSelectedForever: (List<TrashEntry>) -> Unit = { list -> list.forEach(onDeleteForever) },
    isActivePage: Boolean = true
) {
    BackHandler(enabled = isActivePage) { onBack() }
    val d = ArcTheme.dimens
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(RecycleBinFilter.ALL) }
    var selectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var inspectedEntry by remember { mutableStateOf<TrashEntry?>(null) }
    var pendingPermanentAction by remember { mutableStateOf<TrashPermanentAction?>(null) }

    val totalBytes = remember(entries) { entries.sumOf { it.sizeBytes } }
    val folderCount = remember(entries) { entries.count { it.isDirectory } }
    val fileCount = entries.size - folderCount

    val filteredEntries = remember(entries, searchQuery, activeFilter) {
        val q = searchQuery.trim().lowercase()
        entries.filter { entry ->
            val typeMatch = when (activeFilter) {
                RecycleBinFilter.ALL -> true
                RecycleBinFilter.FOLDERS -> entry.isDirectory
                RecycleBinFilter.FILES -> !entry.isDirectory
            }
            val queryMatch = q.isEmpty() ||
                entry.originalName.lowercase().contains(q) ||
                entry.originalPath.lowercase().contains(q)
            typeMatch && queryMatch
        }
    }

    val validSelectedIds = remember(selectedIds, entries) {
        val valid = entries.mapTo(HashSet()) { it.id }
        selectedIds.filterTo(HashSet()) { it in valid }
    }
    val selectedEntries = remember(validSelectedIds, entries) {
        entries.filter { it.id in validSelectedIds }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = d.maxContentWidth)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("recycle_bin_list"),
                contentPadding = PaddingValues(
                    start = d.screenHorizontalPadding,
                    end = d.screenHorizontalPadding,
                    top = 4.dp,
                    bottom = if (selectedEntries.isNotEmpty()) 12.dp else navBottom + 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    ScreenTitleRow(
                        title = "Recycle Bin",
                        badgeText = "${entries.size}",
                        onBack = onBack
                    )
                }

                // Active Recycle Bin protection status card
                item {
                    Surface(
                        shape = RoundedCornerShape(d.cardRadius),
                        color = if (recycleBinEnabled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                        },
                        border = BorderStroke(
                            0.6.dp,
                            if (recycleBinEnabled) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                            } else {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recycle_bin_status_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (recycleBinEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                        else MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (recycleBinEnabled) Icons.Filled.RestoreFromTrash else Icons.Filled.DeleteForever,
                                    contentDescription = null,
                                    tint = if (recycleBinEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (recycleBinEnabled) "Recycle Bin Active" else "Recycle Bin Paused",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (recycleBinEnabled) {
                                        if (entries.isEmpty()) "Deleted files and folders move here automatically."
                                        else "${entries.size} item(s) · ${FormatUtils.formatBytes(totalBytes)} recoverable"
                                    } else {
                                        "Turn on to move deleted items here instead of permanent removal."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = recycleBinEnabled,
                                onCheckedChange = onToggleRecycleBinEnabled,
                                modifier = Modifier.testTag("recycle_bin_active_switch")
                            )
                        }
                    }
                }

                if (entries.isNotEmpty()) {
                    // Search + Filter + Actions bar
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Search deleted files & folders…",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                        BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 14.sp
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("recycle_bin_search_input")
                                        )
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(17.dp)
                                                .clickable { searchQuery = "" }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                LazyRow(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(RecycleBinFilter.entries, key = { it.id }) { filter ->
                                        val count = when (filter) {
                                            RecycleBinFilter.ALL -> entries.size
                                            RecycleBinFilter.FOLDERS -> folderCount
                                            RecycleBinFilter.FILES -> fileCount
                                        }
                                        val selected = activeFilter == filter
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                            modifier = Modifier
                                                .heightIn(min = 34.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .clickable { activeFilter = filter }
                                                .testTag("recycle_filter_${filter.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${filter.label} ($count)",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ArcIosTintedButton(
                                        text = if (selectMode) "Done" else "Select",
                                        icon = Icons.Filled.Checklist,
                                        onClick = {
                                            selectMode = !selectMode
                                            if (!selectMode) selectedIds = emptySet()
                                        },
                                        modifier = Modifier.testTag("recycle_select_mode_button")
                                    )
                                    ArcIosTintedButton(
                                        text = "Empty",
                                        icon = Icons.Filled.DeleteForever,
                                        tintColor = MaterialTheme.colorScheme.error,
                                        onClick = { pendingPermanentAction = TrashPermanentAction.EmptyAll },
                                        modifier = Modifier.testTag("empty_trash_button")
                                    )
                                }
                            }

                            if (selectMode && filteredEntries.isNotEmpty()) {
                                val allVisibleSelected = filteredEntries.all { it.id in validSelectedIds }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${selectedEntries.size} selected",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (allVisibleSelected) "Deselect All" else "Select All (${filteredEntries.size})",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedIds = if (allVisibleSelected) {
                                                    validSelectedIds - filteredEntries.map { it.id }.toSet()
                                                } else {
                                                    validSelectedIds + filteredEntries.map { it.id }.toSet()
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("recycle_select_all_button")
                                    )
                                }
                            }
                        }
                    }
                }

                if (entries.isEmpty()) {
                    item {
                        ArcUnifiedEmptyState(
                            icon = Icons.Filled.DeleteOutline,
                            title = "Recycle Bin is empty",
                            subtitle = "Any deleted file or folder moves here first, so you can restore it anytime or delete it permanently.",
                            fillMaxSize = false
                        )
                    }
                } else if (filteredEntries.isEmpty()) {
                    item {
                        ArcUnifiedEmptyState(
                            icon = Icons.Filled.Search,
                            title = "No matching deleted items",
                            subtitle = "Try clearing the search query or switching filters.",
                            fillMaxSize = false
                        )
                    }
                } else {
                    items(filteredEntries, key = { it.id }) { entry ->
                        val isSelected = entry.id in validSelectedIds
                        val displayItem = remember(entry, trashRootPath) {
                            entry.toDisplayFileItem(trashRootPath)
                        }
                        ArcCard(
                            cornerRadius = d.cardRadius,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(d.cardRadius))
                                .combinedClickable(
                                    onClick = {
                                        if (selectMode) {
                                            selectedIds = if (isSelected) selectedIds - entry.id else selectedIds + entry.id
                                        } else {
                                            inspectedEntry = entry
                                        }
                                    },
                                    onLongClick = {
                                        selectMode = true
                                        selectedIds = selectedIds + entry.id
                                    }
                                )
                                .testTag("trash_entry_${entry.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        else Color.Transparent
                                    )
                                    .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (selectMode) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = if (isSelected) "Selected" else "Not selected",
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                BinaryFileItemIcon(
                                    item = displayItem,
                                    monochrome = monochrome,
                                    size = 38.dp
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = entry.originalName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (entry.isDirectory) {
                                            ArcBadge(text = "Folder")
                                        }
                                    }
                                    Text(
                                        text = entry.originalParentDisplay,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${FormatUtils.formatBytes(entry.sizeBytes)} · ${FormatUtils.formatShortDate(entry.trashedAtMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                    )
                                }
                                IconButton(
                                    onClick = { onRestore(entry) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("trash_restore_${entry.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.RestoreFromTrash,
                                        contentDescription = "Restore ${entry.originalName}",
                                        modifier = Modifier.size(21.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { pendingPermanentAction = TrashPermanentAction.Single(entry) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("trash_delete_${entry.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteForever,
                                        contentDescription = "Delete ${entry.originalName} permanently",
                                        modifier = Modifier.size(21.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedEntries.isNotEmpty()) {
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = d.screenHorizontalPadding,
                            end = d.screenHorizontalPadding,
                            top = 8.dp,
                            bottom = navBottom + 10.dp
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArcIosPrimaryButton(
                            text = "Restore (${selectedEntries.size})",
                            icon = Icons.Filled.RestoreFromTrash,
                            onClick = {
                                val batch = selectedEntries
                                selectedIds = emptySet()
                                selectMode = false
                                onRestoreSelected(batch)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("recycle_restore_selected_button")
                        )
                        ArcIosTintedButton(
                            text = "Delete (${selectedEntries.size})",
                            icon = Icons.Filled.DeleteForever,
                            tintColor = MaterialTheme.colorScheme.error,
                            onClick = { pendingPermanentAction = TrashPermanentAction.Batch(selectedEntries) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("recycle_delete_selected_button")
                        )
                    }
                }
            }
        }
    }

    inspectedEntry?.let { entry ->
        val displayItem = remember(entry, trashRootPath) { entry.toDisplayFileItem(trashRootPath) }
        ArcIosModal(
            title = entry.originalName,
            subtitle = if (entry.isDirectory) "Deleted Folder" else "Deleted File",
            onDismiss = { inspectedEntry = null },
            leadingIcon = {
                BinaryFileItemIcon(item = displayItem, monochrome = monochrome, size = 34.dp)
            },
            modifier = Modifier.testTag("trash_entry_detail_dialog"),
            footer = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArcIosPrimaryButton(
                        text = "Restore to Original Location",
                        icon = Icons.Filled.RestoreFromTrash,
                        onClick = {
                            inspectedEntry = null
                            onRestore(entry)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trash_detail_restore_button")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArcIosSecondaryButton(
                            text = "Close",
                            onClick = { inspectedEntry = null },
                            modifier = Modifier.weight(1f)
                        )
                        ArcIosTintedButton(
                            text = "Delete Forever",
                            icon = Icons.Filled.DeleteForever,
                            tintColor = MaterialTheme.colorScheme.error,
                            onClick = {
                                inspectedEntry = null
                                pendingPermanentAction = TrashPermanentAction.Single(entry)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trash_detail_delete_button")
                        )
                    }
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                ArcIosGroupedCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BinaryFileItemIcon(item = displayItem, monochrome = monochrome, size = 44.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = entry.originalName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${FormatUtils.formatBytes(entry.sizeBytes)} · Deleted ${FormatUtils.formatShortDate(entry.trashedAtMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Original Location",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = entry.originalPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    pendingPermanentAction?.let { action ->
        val (title, subtitle, confirmLabel) = when (action) {
            is TrashPermanentAction.Single -> Triple(
                "Delete Permanently?",
                "'${action.entry.originalName}' will be permanently erased from Recycle Bin and cannot be restored.",
                "Delete Forever"
            )
            is TrashPermanentAction.Batch -> Triple(
                "Delete ${action.entries.size} Item(s) Permanently?",
                "These ${action.entries.size} selected items will be permanently erased from Recycle Bin and cannot be restored.",
                "Delete ${action.entries.size} Forever"
            )
            TrashPermanentAction.EmptyAll -> Triple(
                "Empty Recycle Bin?",
                "All ${entries.size} item(s) (${FormatUtils.formatBytes(totalBytes)}) will be permanently erased and cannot be restored.",
                "Empty Recycle Bin"
            )
        }
        ArcIosModal(
            title = title,
            subtitle = "Permanent Action",
            onDismiss = { pendingPermanentAction = null },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            modifier = Modifier.testTag("trash_permanent_confirm_dialog"),
            footer = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArcIosSecondaryButton(
                        text = "Cancel",
                        onClick = { pendingPermanentAction = null },
                        modifier = Modifier.weight(1f)
                    )
                    ArcIosPrimaryButton(
                        text = confirmLabel,
                        icon = Icons.Filled.DeleteForever,
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                        onClick = {
                            val resolved = action
                            pendingPermanentAction = null
                            when (resolved) {
                                is TrashPermanentAction.Single -> {
                                    selectedIds = selectedIds - resolved.entry.id
                                    onDeleteForever(resolved.entry)
                                }
                                is TrashPermanentAction.Batch -> {
                                    selectedIds = emptySet()
                                    selectMode = false
                                    onDeleteSelectedForever(resolved.entries)
                                }
                                TrashPermanentAction.EmptyAll -> {
                                    selectedIds = emptySet()
                                    selectMode = false
                                    onEmptyTrash()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("trash_confirm_permanent_button")
                    )
                }
            }
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkEntry>,
    onBack: () -> Unit,
    onOpenBookmark: (BookmarkEntry) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    isActivePage: Boolean = true
) {
    BackHandler(enabled = isActivePage) { onBack() }
    val d = ArcTheme.dimens
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = d.maxContentWidth)
                .fillMaxSize()
                .testTag("bookmarks_screen_list"),
            contentPadding = PaddingValues(
                start = d.screenHorizontalPadding,
                end = d.screenHorizontalPadding,
                top = 4.dp,
                bottom = navBottom + 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ScreenTitleRow(title = "Bookmarks", badgeText = "${bookmarks.size}", onBack = onBack)
            }

            if (bookmarks.isEmpty()) {
                item {
                    ArcUnifiedEmptyState(
                        icon = Icons.Filled.Bookmark,
                        title = "No bookmarks yet",
                        subtitle = "Open any folder or file menu (⋮) and choose Bookmark to pin it here.",
                        fillMaxSize = false
                    )
                }
            } else {
                items(bookmarks, key = { it.pathOrUri }) { entry ->
                    ArcCard(cornerRadius = d.cardRadius, modifier = Modifier.fillMaxWidth(), onClick = { onOpenBookmark(entry) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.pathOrUri, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onRemoveBookmark(entry.pathOrUri) }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove ${entry.title}", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun ScreenTitleRow(title: String, badgeText: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Back", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        ArcBadge(text = badgeText)
    }
}
