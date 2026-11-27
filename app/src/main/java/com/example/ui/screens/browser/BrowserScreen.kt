package com.example.ui.screens.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ClipboardState
import com.example.data.DirectoryLoadState
import com.example.data.ExplorerCategory
import com.example.data.FileGroupingUtils
import com.example.data.FileItem
import com.example.data.FileTag
import com.example.data.GroupByField
import com.example.data.SortField
import com.example.data.TabDestination
import com.example.data.ViewMode
import com.example.data.layoutStorageKey
import com.example.data.normalizeTagPath
import com.example.ui.components.common.ArcEmptyStateCard
import com.example.ui.components.common.ArcUnifiedEmptyState
import com.example.ui.components.common.TagHeaderBar
import com.example.ui.registry.FileActionId
import com.example.ui.registry.LocalArcTextMeasurer
import com.example.ui.theme.ArcTheme

private fun itemContentType(item: FileItem): String = when {
    item.isDirectory -> "folder"
    item.isImage -> "image"
    item.isVideo -> "video"
    item.isApk -> "apk"
    else -> "file"
}

@Composable
fun BrowserScreen(
    destination: TabDestination,
    directoryState: DirectoryLoadState,
    categoryItems: List<FileItem>,
    allCompressedItems: List<FileItem> = categoryItems,
    isScanningCompressed: Boolean = false,
    compressedFormatFilter: String = "ALL",
    tagItems: List<FileItem> = emptyList(),
    searchResults: List<FileItem>,
    allTags: List<FileTag> = emptyList(),
    itemTagsByPath: Map<String, List<FileTag>> = emptyMap(),
    selectedIds: Set<String>,
    renamingItemId: String? = null,
    clipboard: ClipboardState?,
    viewMode: ViewMode,
    gridColumns: Int = 3,
    originalMediaRatio: Boolean = true,
    sortField: SortField,
    sortAscending: Boolean,
    groupBy: GroupByField = GroupByField.NONE,
    foldersFirst: Boolean = true,
    monochromeBinaryIcons: Boolean,
    showFileExtensions: Boolean = true,
    compactListMode: Boolean = false,
    dragReorderEnabled: Boolean = true,
    onBack: () -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleViewMode: () -> Unit,
    onSortOrderChange: (SortField, Boolean) -> Unit,
    onToggleSortOrder: () -> Unit,
    onGroupByChange: (GroupByField) -> Unit,
    onNewFolderClick: () -> Unit,
    onNewNoteClick: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onMountFolderClick: () -> Unit,
    onImportMediaClick: () -> Unit,
    onImportDocsClick: () -> Unit,
    onCompressedFilterChange: (String) -> Unit = {},
    onRescanCompressed: () -> Unit = {},
    onPasteClick: () -> Unit,
    onClearClipboard: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onToggleSelect: (FileItem) -> Unit,
    onSelectAll: (List<FileItem>) -> Unit,
    onClearSelection: () -> Unit,
    onFileAction: (FileActionId, List<FileItem>) -> Unit,
    onConfirmInlineRename: (FileItem, String) -> Unit = { _, _ -> },
    onCancelInlineRename: () -> Unit = {},
    onMoveItemsToFolder: (List<FileItem>, String, Boolean, String) -> Unit = { _, _, _, _ -> },
    onReorderItems: (List<String>) -> Unit = {},
    onTagSelected: (String) -> Unit = {},
    isActivePage: Boolean = true,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = isActivePage) { onBack() }
    val d = ArcTheme.dimens

    val itemsToDisplay: List<FileItem> = when (destination) {
        is TabDestination.Directory -> (directoryState as? DirectoryLoadState.Loaded)?.items ?: emptyList()
        is TabDestination.Category -> categoryItems
        is TabDestination.Tag -> tagItems
        is TabDestination.Search -> searchResults
        else -> emptyList()
    }
    val groupsToDisplay = remember(itemsToDisplay, groupBy, sortField, sortAscending, foldersFirst) {
        if (groupBy == GroupByField.NONE) {
            emptyList()
        } else {
            FileGroupingUtils.groupItems(
                items = itemsToDisplay,
                groupBy = groupBy,
                sortField = sortField,
                ascending = sortAscending,
                foldersFirst = foldersFirst,
                alreadySorted = true
            )
        }
    }
    val selectedItems = remember(itemsToDisplay, selectedIds) {
        if (selectedIds.isEmpty()) emptyList() else itemsToDisplay.filter { it.id in selectedIds }
    }
    val locationKey = destination.layoutStorageKey() ?: "default"
    val sharedTextMeasurer = rememberTextMeasurer()
    val supportsDragDrop = destination is TabDestination.Directory ||
        destination is TabDestination.Category ||
        destination is TabDestination.Tag ||
        destination is TabDestination.Search
    val dragState = if (supportsDragDrop) rememberBrowserDragDropState() else null
    val baseOrderIds = remember(itemsToDisplay) { itemsToDisplay.map { it.id } }
    val reorderAllowedHere = dragReorderEnabled && groupBy == GroupByField.NONE && itemsToDisplay.size > 1
    if (dragState != null) {
        SideEffect {
            dragState.reorderEnabled = reorderAllowedHere
            dragState.isGridMode = (viewMode == ViewMode.GRID)
            dragState.syncBaseOrder(baseOrderIds)
        }
    }
    val activePreviewIds = dragState?.activeSession?.previewOrderIds
    val renderedItems = remember(itemsToDisplay, activePreviewIds) {
        if (activePreviewIds.isNullOrEmpty()) {
            itemsToDisplay
        } else {
            val byId = itemsToDisplay.associateBy { it.id }
            val ordered = ArrayList<FileItem>(itemsToDisplay.size)
            val seen = HashSet<String>(itemsToDisplay.size)
            for (id in activePreviewIds) {
                val found = byId[id]
                if (found != null && seen.add(id)) {
                    ordered.add(found)
                }
            }
            for (item in itemsToDisplay) {
                if (seen.add(item.id)) {
                    ordered.add(item)
                }
            }
            ordered
        }
    }
    val onDropMoveHandler = remember(onMoveItemsToFolder) {
        { items: List<FileItem>, target: DropTargetEntry ->
            onMoveItemsToFolder(items, target.pathOrUri, target.isSaf, target.label)
        }
    }
    val hasTags = itemTagsByPath.isNotEmpty()
    val selectionMode = selectedIds.isNotEmpty()
    val currentSelectedIds by rememberUpdatedState(selectedIds)
    val currentSelectedItems by rememberUpdatedState(selectedItems)
    val currentRenamingAny by rememberUpdatedState(renamingItemId != null)
    val currentOnCancelInlineRename by rememberUpdatedState(onCancelInlineRename)
    val currentOnToggleSelect by rememberUpdatedState(onToggleSelect)
    val currentOnFileClick by rememberUpdatedState(onFileClick)
    val currentOnConfirmInlineRename by rememberUpdatedState(onConfirmInlineRename)
    val currentOnReorderItems by rememberUpdatedState(onReorderItems)

    val sharedDragPayloadForItem = remember {
        { item: FileItem ->
            val ids = currentSelectedIds
            val sel = currentSelectedItems
            if (item.id in ids && sel.isNotEmpty()) sel else listOf(item)
        }
    }
    val sharedOnItemClick = remember {
        { item: FileItem ->
            when {
                currentRenamingAny -> currentOnCancelInlineRename()
                currentSelectedIds.isNotEmpty() -> currentOnToggleSelect(item)
                else -> currentOnFileClick(item)
            }
        }
    }
    val sharedOnItemLongClick = remember {
        { item: FileItem -> currentOnToggleSelect(item) }
    }
    val sharedOnConfirmRename = remember {
        { item: FileItem, newName: String -> currentOnConfirmInlineRename(item, newName) }
    }
    val sharedOnReorderCommit = remember {
        { orderedIds: List<String> -> currentOnReorderItems(orderedIds) }
    }

    DisposableEffect(locationKey, dragState) {
        onDispose { dragState?.cancelDrag() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { dragState?.rootCoords = it },
        contentAlignment = Alignment.TopCenter
    ) {
        val isArchivesCategory = (destination as? TabDestination.Category)?.category == ExplorerCategory.ARCHIVES
        Column(modifier = Modifier.widthIn(max = d.maxContentWidth).fillMaxSize()) {
            when (destination) {
                is TabDestination.Directory -> BreadcrumbBar(destination.breadcrumbs, onBreadcrumbClick, dragState)
                is TabDestination.Category -> {
                    val headerItems = if (isArchivesCategory) allCompressedItems else categoryItems
                    val totalBytes = remember(headerItems) { headerItems.sumOf { it.sizeBytes } }
                    CategoryHeaderBar(
                        category = destination.category,
                        count = headerItems.size,
                        monochrome = monochromeBinaryIcons,
                        totalBytes = totalBytes,
                        isScanning = isArchivesCategory && isScanningCompressed,
                        onRescanClick = if (isArchivesCategory) onRescanCompressed else null
                    )
                    if (isArchivesCategory && allCompressedItems.isNotEmpty()) {
                        CompressedFormatFilterRow(
                            allArchives = allCompressedItems,
                            selectedFilterId = compressedFormatFilter,
                            onSelectFilter = onCompressedFilterChange
                        )
                    }
                }
                is TabDestination.Tag -> TagHeaderBar(
                    activeTag = allTags.firstOrNull { it.id == destination.tagId },
                    allTags = allTags,
                    itemCount = tagItems.size,
                    onSelectTag = onTagSelected
                )
                is TabDestination.Search -> SearchHeaderBar(destination.query, searchResults.size, onSearchQueryChange)
                else -> Unit
            }

            val canPasteHere = destination is TabDestination.Directory
            val showBottomEditingBar = selectedIds.isNotEmpty() || (clipboard != null && canPasteHere)
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            val showScopedBanner = destination is TabDestination.Directory &&
                directoryState is DirectoryLoadState.Loaded &&
                directoryState.isScopedStorageLimited
            AnimatedVisibility(visible = showScopedBanner) {
                ScopedStorageBanner(onGrantClick = onRequestAllFilesAccess)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    destination is TabDestination.Directory && directoryState is DirectoryLoadState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.5.dp)
                        }
                    }

                    destination is TabDestination.Directory && directoryState is DirectoryLoadState.RestrictedRequiresSaf -> {
                        ArcEmptyStateCard(
                            title = stringResource(R.string.restricted_folder_title),
                            subtitle = stringResource(R.string.restricted_folder_hint),
                            icon = Icons.Filled.Lock,
                            caption = directoryState.attemptedPath
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onRequestAllFilesAccess,
                                    shape = RoundedCornerShape(d.pillRadius),
                                    modifier = Modifier.testTag("grant_all_files_button")
                                ) {
                                    Text(stringResource(R.string.grant_all_files))
                                }
                                OutlinedButton(
                                    onClick = onMountFolderClick,
                                    shape = RoundedCornerShape(d.pillRadius),
                                    modifier = Modifier.testTag("grant_saf_button")
                                ) {
                                    Text(stringResource(R.string.grant_folder_access))
                                }
                            }
                        }
                    }

                    destination is TabDestination.Directory && directoryState is DirectoryLoadState.Error -> {
                        ArcEmptyStateCard(title = "Unable to open directory", subtitle = directoryState.message) {
                            Button(onClick = onMountFolderClick, shape = RoundedCornerShape(d.pillRadius)) {
                                Text(stringResource(R.string.mount_folder))
                            }
                        }
                    }

                    isArchivesCategory && isScanningCompressed && itemsToDisplay.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("compressed_scanning_indicator"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(strokeWidth = 2.5.dp)
                                Text(
                                    text = "Scanning storage for compressed files…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    itemsToDisplay.isEmpty() -> {
                        EmptyBrowserState(
                            destination = destination,
                            hasUnfilteredArchives = isArchivesCategory && allCompressedItems.isNotEmpty(),
                            onMountFolderClick = onMountFolderClick,
                            onImportMediaClick = onImportMediaClick,
                            onResetCompressedFilter = { onCompressedFilterChange("ALL") },
                            onRescanCompressed = onRescanCompressed
                        )
                    }

                    viewMode == ViewMode.LIST -> {
                        val listState = key(locationKey) { rememberLazyListState() }
                        val listScrollingCheck = remember(listState) { { listState.isScrollInProgress } }
                        CompositionLocalProvider(LocalArcTextMeasurer provides sharedTextMeasurer) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().testTag("browser_file_list"),
                                contentPadding = PaddingValues(
                                    start = d.screenHorizontalPadding,
                                    end = d.screenHorizontalPadding,
                                    top = 6.dp,
                                    bottom = navBottom + (if (showBottomEditingBar) 104.dp else 12.dp)
                                ),
                                verticalArrangement = Arrangement.spacedBy(if (compactListMode) 4.dp else d.itemGap)
                            ) {
                                if (groupBy == GroupByField.NONE) {
                                    items(
                                        items = renderedItems,
                                        key = { it.id },
                                        contentType = { itemContentType(it) }
                                    ) { item ->
                                        val isItemSelected = item.id in selectedIds
                                        val isItemRenaming = renamingItemId == item.id
                                        val itemTags = if (hasTags) {
                                            itemTagsByPath[normalizeTagPath(item.pathOrUri, item.isSafUri)].orEmpty()
                                        } else {
                                            emptyList()
                                        }
                                        FileListRow(
                                            item = item,
                                            isSelected = isItemSelected,
                                            selectionMode = selectionMode,
                                            isRenaming = isItemRenaming,
                                            monochrome = monochromeBinaryIcons,
                                            showExtensions = showFileExtensions,
                                            compactMode = compactListMode,
                                            itemTags = itemTags,
                                            dragState = dragState,
                                            onDropMove = onDropMoveHandler,
                                            onAction = onFileAction,
                                            onCancelRename = onCancelInlineRename,
                                            dragPayloadForItem = sharedDragPayloadForItem,
                                            onClickItem = sharedOnItemClick,
                                            onLongClickItem = sharedOnItemLongClick,
                                            onConfirmRenameItem = sharedOnConfirmRename,
                                            onReorderItems = sharedOnReorderCommit,
                                            isScrollInProgress = listScrollingCheck,
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
                                } else {
                                    groupsToDisplay.forEach { group ->
                                        item(key = "hdr_${group.key}", contentType = "group_header") {
                                            GroupSectionHeader(
                                                groupKey = group.key,
                                                title = group.title,
                                                count = group.items.size
                                            )
                                        }
                                        items(
                                            items = group.items,
                                            key = { it.id },
                                            contentType = { itemContentType(it) }
                                        ) { item ->
                                            val isItemSelected = item.id in selectedIds
                                            val isItemRenaming = renamingItemId == item.id
                                            val itemTags = if (hasTags) {
                                                itemTagsByPath[normalizeTagPath(item.pathOrUri, item.isSafUri)].orEmpty()
                                            } else {
                                                emptyList()
                                            }
                                            FileListRow(
                                                item = item,
                                                isSelected = isItemSelected,
                                                selectionMode = selectionMode,
                                                isRenaming = isItemRenaming,
                                                monochrome = monochromeBinaryIcons,
                                                showExtensions = showFileExtensions,
                                                compactMode = compactListMode,
                                                itemTags = itemTags,
                                                dragState = dragState,
                                                onDropMove = onDropMoveHandler,
                                                onAction = onFileAction,
                                                onCancelRename = onCancelInlineRename,
                                                dragPayloadForItem = sharedDragPayloadForItem,
                                                onClickItem = sharedOnItemClick,
                                                onLongClickItem = sharedOnItemLongClick,
                                                onConfirmRenameItem = sharedOnConfirmRename,
                                                isScrollInProgress = listScrollingCheck
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val cols = if (maxWidth >= 600.dp) {
                                (gridColumns + 1).coerceIn(3, 5)
                            } else {
                                gridColumns.coerceIn(2, 4)
                            }
                            val gridMetrics = remember(cols, maxWidth) {
                                d.gridMetricsFor(cols, maxWidth)
                            }
                            val bottomPad = navBottom + (if (showBottomEditingBar) 104.dp else 12.dp)
                            val gridState = key(locationKey) { rememberLazyGridState() }
                            val gridScrollingCheck = remember(gridState) { { gridState.isScrollInProgress } }
                            CompositionLocalProvider(LocalArcTextMeasurer provides sharedTextMeasurer) {
                                LazyVerticalGrid(
                                    state = gridState,
                                    columns = GridCells.Fixed(cols),
                                    modifier = Modifier.fillMaxSize().testTag("browser_file_grid"),
                                    contentPadding = PaddingValues(
                                        start = d.screenHorizontalPadding,
                                        end = d.screenHorizontalPadding,
                                        top = 6.dp,
                                        bottom = bottomPad
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(gridMetrics.gridGap),
                                    verticalArrangement = Arrangement.spacedBy(gridMetrics.gridGap)
                                ) {
                                    if (groupBy == GroupByField.NONE) {
                                        items(
                                            items = renderedItems,
                                            key = { it.id },
                                            contentType = { itemContentType(it) }
                                        ) { item ->
                                            val isItemSelected = item.id in selectedIds
                                            val isItemRenaming = renamingItemId == item.id
                                            val itemTags = if (hasTags) {
                                                itemTagsByPath[normalizeTagPath(item.pathOrUri, item.isSafUri)].orEmpty()
                                            } else {
                                                emptyList()
                                            }
                                            FileGridCard(
                                                item = item,
                                                isSelected = isItemSelected,
                                                selectionMode = selectionMode,
                                                isRenaming = isItemRenaming,
                                                monochrome = monochromeBinaryIcons,
                                                showExtensions = showFileExtensions,
                                                originalRatio = originalMediaRatio,
                                                itemTags = itemTags,
                                                gridMetrics = gridMetrics,
                                                dragState = dragState,
                                                onDropMove = onDropMoveHandler,
                                                onCancelRename = onCancelInlineRename,
                                                dragPayloadForItem = sharedDragPayloadForItem,
                                                onClickItem = sharedOnItemClick,
                                                onLongClickItem = sharedOnItemLongClick,
                                                onConfirmRenameItem = sharedOnConfirmRename,
                                                onReorderItems = sharedOnReorderCommit,
                                                isScrollInProgress = gridScrollingCheck,
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
                                    } else {
                                        groupsToDisplay.forEach { group ->
                                            item(
                                                key = "hdr_${group.key}",
                                                span = { GridItemSpan(maxLineSpan) },
                                                contentType = "group_header"
                                            ) {
                                                GroupSectionHeader(
                                                    groupKey = group.key,
                                                    title = group.title,
                                                    count = group.items.size
                                                )
                                            }
                                            items(
                                                items = group.items,
                                                key = { it.id },
                                                contentType = { itemContentType(it) }
                                            ) { item ->
                                                val isItemSelected = item.id in selectedIds
                                                val isItemRenaming = renamingItemId == item.id
                                                val itemTags = if (hasTags) {
                                                    itemTagsByPath[normalizeTagPath(item.pathOrUri, item.isSafUri)].orEmpty()
                                                } else {
                                                    emptyList()
                                                }
                                                FileGridCard(
                                                    item = item,
                                                    isSelected = isItemSelected,
                                                    selectionMode = selectionMode,
                                                    isRenaming = isItemRenaming,
                                                    monochrome = monochromeBinaryIcons,
                                                    showExtensions = showFileExtensions,
                                                    originalRatio = originalMediaRatio,
                                                    itemTags = itemTags,
                                                    gridMetrics = gridMetrics,
                                                    dragState = dragState,
                                                    onDropMove = onDropMoveHandler,
                                                    onCancelRename = onCancelInlineRename,
                                                    dragPayloadForItem = sharedDragPayloadForItem,
                                                    onClickItem = sharedOnItemClick,
                                                    onLongClickItem = sharedOnItemLongClick,
                                                    onConfirmRenameItem = sharedOnConfirmRename,
                                                    isScrollInProgress = gridScrollingCheck
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val canPasteHereBottom = destination is TabDestination.Directory
        val showBottomEditingBarOuter = selectedIds.isNotEmpty() || (clipboard != null && canPasteHereBottom)
        AnimatedVisibility(
            visible = showBottomEditingBarOuter,
            enter = slideInVertically(tween(200)) { it / 2 } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(150)) { it / 2 } + fadeOut(tween(130)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val allSelected = itemsToDisplay.isNotEmpty() && selectedIds.size >= itemsToDisplay.size
            MultiSelectBottomBar(
                selectedItems = selectedItems,
                clipboard = clipboard,
                canPasteHere = canPasteHereBottom,
                allSelected = allSelected,
                onSelectAll = { onSelectAll(itemsToDisplay) },
                onClear = onClearSelection,
                onPaste = onPasteClick,
                onClearClipboard = onClearClipboard,
                onAction = onFileAction
            )
        }

        if (dragState != null) {
            BrowserDragGhostOverlay(dragState = dragState)
        }
    }
}

@Composable
private fun EmptyBrowserState(
    destination: TabDestination,
    hasUnfilteredArchives: Boolean = false,
    onMountFolderClick: () -> Unit,
    onImportMediaClick: () -> Unit,
    onResetCompressedFilter: () -> Unit = {},
    onRescanCompressed: () -> Unit = {}
) {
    val isSearch = destination is TabDestination.Search
    val isCategory = destination is TabDestination.Category
    val isArchivesCategory = (destination as? TabDestination.Category)?.category == ExplorerCategory.ARCHIVES
    val isTag = destination is TabDestination.Tag

    val emblemIcon = when {
        isSearch -> Icons.Filled.Search
        isTag -> Icons.AutoMirrored.Outlined.Label
        isArchivesCategory -> Icons.Filled.FolderZip
        isCategory -> Icons.Outlined.PhotoLibrary
        else -> Icons.Outlined.FolderOpen
    }
    val title = when {
        isSearch -> "No matching files"
        isTag -> "No tagged items"
        isArchivesCategory && hasUnfilteredArchives -> "No matching archives"
        isArchivesCategory -> "No compressed files found"
        isCategory -> "Nothing in this category"
        else -> stringResource(R.string.empty_folder_title)
    }
    val subtitle = when {
        isSearch -> "Try a different filename or extension."
        isTag -> "Tap ⋮ → Tags on any file or folder to assign this label."
        isArchivesCategory && hasUnfilteredArchives -> "No files match this format filter. Switch back to All to see every compressed file."
        isArchivesCategory -> "ZIP, RAR, 7z, TAR, GZ, APK and other compressed files across accessible storage will appear here."
        isCategory -> stringResource(R.string.empty_category_hint)
        else -> stringResource(R.string.empty_folder_hint)
    }

    ArcUnifiedEmptyState(
        title = title,
        subtitle = subtitle,
        icon = emblemIcon,
        animationKey = destination.layoutStorageKey() ?: destination::class.simpleName,
        modifier = Modifier.testTag("empty_browser_state")
    )
}

@Composable
private fun GroupSectionHeader(
    groupKey: String,
    title: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 3.dp, start = 4.dp, end = 4.dp)
            .testTag("group_header_$groupKey"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Surface(
            shape = RoundedCornerShape(99.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}
