package com.example.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.FileItem
import com.example.data.FileTag
import com.example.data.FormatUtils
import com.example.data.LibrarySection
import com.example.data.ViewMode
import com.example.data.normalizeTagPath
import com.example.ui.registry.FileActionId
import com.example.ui.registry.LocalArcTextMeasurer
import com.example.ui.screens.browser.FileGridCard
import com.example.ui.screens.browser.FileListRow
import com.example.ui.screens.browser.MultiSelectBottomBar
import com.example.ui.theme.ArcTheme
import com.example.ui.theme.BinaryBlue
import com.example.ui.theme.BinaryCoral
import com.example.ui.theme.BinaryCyan
import com.example.ui.theme.BinaryOrchid

@Composable
fun MediaLibraryScreen(
    section: LibrarySection,
    allSectionItems: List<FileItem>,
    filteredItems: List<FileItem>,
    activeSubFilterId: String,
    selectedIds: Set<String>,
    renamingItemId: String?,
    itemTagsByPath: Map<String, List<FileTag>>,
    viewMode: ViewMode,
    gridColumns: Int,
    originalMediaRatio: Boolean,
    monochromeBinaryIcons: Boolean,
    showFileExtensions: Boolean,
    compactListMode: Boolean,
    isActivePage: Boolean,
    onBack: () -> Unit,
    onSelectSubFilter: (String) -> Unit,
    onToggleViewMode: () -> Unit,
    onOpenSearch: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onToggleSelect: (FileItem) -> Unit,
    onSelectAll: (List<FileItem>) -> Unit,
    onClearSelection: () -> Unit,
    onFileAction: (FileActionId, List<FileItem>) -> Unit,
    onConfirmInlineRename: (FileItem, String) -> Unit,
    onCancelInlineRename: () -> Unit,
    onImportClick: () -> Unit,
    onMountFolderClick: () -> Unit,
    modifier: Modifier = Modifier,
    isScanning: Boolean = false,
    onRescan: (() -> Unit)? = null
) {
    BackHandler(enabled = isActivePage) {
        if (selectedIds.isNotEmpty()) onClearSelection() else onBack()
    }

    val d = ArcTheme.dimens
    val filterOptions = remember(section, allSectionItems) {
        LibraryDomainUtils.filterOptionsFor(section, allSectionItems)
    }
    val effectiveFilterId = if (filterOptions.any { it.id == activeSubFilterId }) activeSubFilterId else "ALL"
    val selectedItems = remember(filteredItems, selectedIds) {
        if (selectedIds.isEmpty()) emptyList() else filteredItems.filter { it.id in selectedIds }
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sharedTextMeasurer = rememberTextMeasurer()
    val hasTags = itemTagsByPath.isNotEmpty()
    val selectionMode = selectedIds.isNotEmpty()
    val accent = sectionAccentColor(section)

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_section_screen_${section.name.lowercase()}"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = d.maxContentWidth)
                .fillMaxSize()
        ) {
            LibrarySectionHeaderCard(
                section = section,
                accent = accent,
                viewMode = viewMode,
                isScanning = isScanning,
                onRescan = onRescan,
                onToggleViewMode = onToggleViewMode,
                onOpenSearch = onOpenSearch
            )

            if (filterOptions.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_filter_row_${section.name.lowercase()}"),
                    contentPadding = PaddingValues(horizontal = d.screenHorizontalPadding, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filterOptions, key = { it.id }) { opt ->
                        val active = opt.id == effectiveFilterId
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = if (active) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (active) 1.dp else 0.5.dp,
                                color = if (active) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .clickable { onSelectSubFilter(opt.id) }
                                .testTag("library_filter_chip_${opt.id}")
                        ) {
                            Text(
                                text = opt.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isScanning && filteredItems.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = accent,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Scanning storage for ${section.label.lowercase()}…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    filteredItems.isEmpty() -> {
                        LibraryEmptyState(
                            section = section,
                            accent = accent,
                            isFiltered = effectiveFilterId != "ALL" && allSectionItems.isNotEmpty(),
                            onResetFilter = { onSelectSubFilter("ALL") },
                            onImportClick = onImportClick,
                            onMountFolderClick = onMountFolderClick
                        )
                    }

                    viewMode == ViewMode.GRID -> {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val cols = if (maxWidth >= 600.dp) (gridColumns + 1).coerceIn(3, 5) else gridColumns.coerceIn(2, 4)
                            val gridMetrics = remember(cols, maxWidth) { d.gridMetricsFor(cols, maxWidth) }
                            val gridState = rememberLazyGridState()
                            val scrollingCheck = remember(gridState) { { gridState.isScrollInProgress } }

                            CompositionLocalProvider(LocalArcTextMeasurer provides sharedTextMeasurer) {
                                LazyVerticalGrid(
                                    state = gridState,
                                    columns = GridCells.Fixed(cols),
                                    modifier = Modifier.fillMaxSize().testTag("library_media_grid"),
                                    contentPadding = PaddingValues(
                                        start = d.screenHorizontalPadding,
                                        end = d.screenHorizontalPadding,
                                        top = 6.dp,
                                        bottom = navBottom + if (selectedItems.isNotEmpty()) 104.dp else 14.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(gridMetrics.gridGap),
                                    verticalArrangement = Arrangement.spacedBy(gridMetrics.gridGap)
                                ) {
                                    items(
                                        items = filteredItems,
                                        key = { it.id },
                                        contentType = { "library_grid_item" }
                                    ) { item ->
                                        val isSelected = item.id in selectedIds
                                        val itemTags = if (hasTags) {
                                            itemTagsByPath[normalizeTagPath(item.pathOrUri, item.isSafUri)].orEmpty()
                                        } else emptyList()
                                        FileGridCard(
                                            item = item,
                                            isSelected = isSelected,
                                            selectionMode = selectionMode,
                                            isRenaming = renamingItemId == item.id,
                                            monochrome = monochromeBinaryIcons,
                                            showExtensions = showFileExtensions,
                                            originalRatio = originalMediaRatio,
                                            itemTags = itemTags,
                                            gridMetrics = gridMetrics,
                                            dragState = null,
                                            onDropMove = { _, _ -> },
                                            onCancelRename = onCancelInlineRename,
                                            onClickItem = {
                                                if (renamingItemId != null) onCancelInlineRename()
                                                else if (selectionMode) onToggleSelect(it)
                                                else onFileClick(it)
                                            },
                                            onLongClickItem = onToggleSelect,
                                            onConfirmRenameItem = onConfirmInlineRename,
                                            isScrollInProgress = scrollingCheck,
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = null,
                                                placementSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                fadeOutSpec = null
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        val listState = rememberLazyListState()
                        val scrollingCheck = remember(listState) { { listState.isScrollInProgress } }

                        CompositionLocalProvider(LocalArcTextMeasurer provides sharedTextMeasurer) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().testTag("library_media_list"),
                                contentPadding = PaddingValues(
                                    start = d.screenHorizontalPadding,
                                    end = d.screenHorizontalPadding,
                                    top = 6.dp,
                                    bottom = navBottom + if (selectedItems.isNotEmpty()) 104.dp else 14.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(if (compactListMode) 4.dp else d.itemGap)
                            ) {
                                items(
                                    items = filteredItems,
                                    key = { it.id },
                                    contentType = { "library_list_item" }
                                ) { item ->
                                    val isSelected = item.id in selectedIds
                                    val itemTags = if (hasTags) {
                                        itemTagsByPath[normalizeTagPath(item.pathOrUri, item.isSafUri)].orEmpty()
                                    } else emptyList()
                                    FileListRow(
                                        item = item,
                                        isSelected = isSelected,
                                        selectionMode = selectionMode,
                                        isRenaming = renamingItemId == item.id,
                                        monochrome = monochromeBinaryIcons,
                                        showExtensions = showFileExtensions,
                                        compactMode = compactListMode,
                                        itemTags = itemTags,
                                        dragState = null,
                                        onDropMove = { _, _ -> },
                                        onAction = onFileAction,
                                        onCancelRename = onCancelInlineRename,
                                        onClickItem = {
                                            if (renamingItemId != null) onCancelInlineRename()
                                            else if (selectionMode) onToggleSelect(it)
                                            else onFileClick(it)
                                        },
                                        onLongClickItem = onToggleSelect,
                                        onConfirmRenameItem = onConfirmInlineRename,
                                        isScrollInProgress = scrollingCheck,
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = null,
                                            placementSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            fadeOutSpec = null
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selectedItems.isNotEmpty(),
            enter = slideInVertically(tween(200)) { it / 2 } + fadeIn(tween(170)),
            exit = slideOutVertically(tween(150)) { it / 2 } + fadeOut(tween(130)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MultiSelectBottomBar(
                selectedItems = selectedItems,
                clipboard = null,
                canPasteHere = false,
                allSelected = filteredItems.isNotEmpty() && selectedItems.size >= filteredItems.size,
                onSelectAll = { onSelectAll(filteredItems) },
                onClear = onClearSelection,
                onPaste = {},
                onClearClipboard = {},
                onAction = onFileAction
            )
        }
    }
}

@Composable
private fun LibrarySectionHeaderCard(
    section: LibrarySection,
    accent: Color,
    viewMode: ViewMode,
    isScanning: Boolean,
    onRescan: (() -> Unit)?,
    onToggleViewMode: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val d = ArcTheme.dimens
    val subtitle = if (isScanning) {
        "Scanning storage for ${section.label.lowercase()}…"
    } else {
        "All ${section.label.lowercase()} across storage"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = d.screenHorizontalPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = sectionIcon(section),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = section.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onRescan != null) {
            IconButton(
                onClick = onRescan,
                enabled = !isScanning,
                modifier = Modifier.testTag("library_rescan_${section.name.lowercase()}")
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        color = accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Rescan ${section.label}",
                        tint = accent
                    )
                }
            }
        }
        IconButton(onClick = onOpenSearch) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search ${section.label}",
                tint = accent
            )
        }
        IconButton(
            onClick = onToggleViewMode,
            modifier = Modifier.testTag("library_toggle_view_mode")
        ) {
            Icon(
                imageVector = if (viewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = "Toggle View Mode",
                tint = accent
            )
        }
    }
}

@Composable
private fun LibraryEmptyState(
    section: LibrarySection,
    accent: Color,
    isFiltered: Boolean,
    onResetFilter: () -> Unit,
    onImportClick: () -> Unit,
    onMountFolderClick: () -> Unit
) {
    val d = ArcTheme.dimens
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("library_empty_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 310.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = sectionIcon(section),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = if (isFiltered) "No matching ${section.label.lowercase()}" else "No ${section.label.lowercase()} found",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (isFiltered) {
                    "Switch back to All to view every file in this library section."
                } else {
                    "Files matching ${section.label.lowercase()} across internal storage and mounted folders appear here automatically."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (isFiltered) {
                Button(onClick = onResetFilter, shape = RoundedCornerShape(d.pillRadius)) {
                    Text("Show All")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onMountFolderClick, shape = RoundedCornerShape(d.pillRadius)) {
                        Text("Mount Folder")
                    }
                    Button(onClick = onImportClick, shape = RoundedCornerShape(d.pillRadius)) {
                        Text("Import")
                    }
                }
            }
        }
    }
}

private fun sectionIcon(section: LibrarySection): ImageVector = when (section) {
    LibrarySection.IMAGES -> Icons.Filled.Description
    LibrarySection.MOVIES -> Icons.Filled.Movie
    LibrarySection.DOCUMENTS -> Icons.Filled.Description
    LibrarySection.MUSIC -> Icons.Filled.MusicNote
    LibrarySection.DOWNLOADS -> Icons.Filled.Download
}

private fun sectionAccentColor(section: LibrarySection): Color = when (section) {
    LibrarySection.IMAGES -> BinaryBlue
    LibrarySection.MOVIES -> BinaryOrchid
    LibrarySection.DOCUMENTS -> BinaryCyan
    LibrarySection.MUSIC -> BinaryCoral
    LibrarySection.DOWNLOADS -> BinaryBlue
}
