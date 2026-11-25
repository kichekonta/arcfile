package com.example.ui.registry

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.data.ClipboardOp
import com.example.data.DirectoryLoadState
import com.example.data.ExplorerCategory
import com.example.data.FileItem
import com.example.data.LibrarySection
import com.example.data.TabDestination
import com.example.data.ViewMode
import com.example.plugins.archive.CompressedFormatFilter
import com.example.ui.ExplorerUiState
import com.example.ui.ExplorerViewModel
import com.example.ui.screens.browser.BrowserScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.library.GalleryLibraryScreen
import com.example.ui.screens.library.LibraryDomainUtils
import com.example.ui.screens.library.MediaLibraryScreen
import com.example.ui.screens.settings.CacheDataManagerScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.tools.BookmarksScreen
import com.example.ui.screens.tools.RecycleBinScreen
import com.example.ui.screens.tools.SpaceAnalyzerScreen

data class DestinationCallbacks(
    val onRequestAllFilesAccess: () -> Unit,
    val onMountFolder: () -> Unit,
    val onImportMedia: () -> Unit,
    val onImportDocs: () -> Unit,
    val onNewFolder: () -> Unit,
    val onNewNote: () -> Unit,
    val onBatchRenameRequest: (List<FileItem>) -> Unit,
    val onDeleteRequest: (List<FileItem>) -> Unit,
    val onLaunchExternal: (Intent) -> Unit
)

/**
 * Central Registry mapping TabDestination subtypes to modular screen composables.
 * Register new tab destinations (e.g. Analyzer, Trash, Bookmarks, Library) here without bloating MainActivity.
 */
object TabDestinationRegistry {

    @Composable
    fun RenderDestination(
        destination: TabDestination,
        state: ExplorerUiState,
        viewModel: ExplorerViewModel,
        callbacks: DestinationCallbacks,
        tabId: String? = null,
        isActivePage: Boolean = true
    ) {
        when (destination) {
            TabDestination.Home -> {
                val bookmarks = remember(state.prefs.bookmarkEntries) { state.prefs.parsedBookmarks() }
                val tagCounts = remember(state.taggedFilesByTagId) {
                    state.taggedFilesByTagId.mapValues { it.value.size }
                }
                HomeScreen(
                    drives = state.drives,
                    categorySummaries = state.categorySummaries,
                    librarySummaries = state.librarySummaries,
                    recentFiles = state.recentFiles,
                    bookmarks = bookmarks,
                    tags = state.allTags,
                    taggedCountsByTagId = tagCounts,
                    trashCount = state.trashEntries.size,
                    showNewFilesPreview = state.prefs.showHomeNewFiles,
                    showBookmarksPreview = state.prefs.showHomeBookmarks,
                    showFileExtensions = state.prefs.showFileExtensions,
                    monochromeBinaryIcons = state.prefs.monochromeBinaryIcons,
                    hasAllFilesAccess = state.hasAllFilesAccess,
                    onRequestAllFilesAccess = callbacks.onRequestAllFilesAccess,
                    onDriveClick = { drive -> viewModel.openDriveInTab(drive) },
                    onUnmountDrive = viewModel::unmountSafDrive,
                    onMountFolderClick = callbacks.onMountFolder,
                    onCategoryClick = viewModel::openLibraryCategory,
                    onTagClick = viewModel::openTagTab,
                    onSearchBarClick = { viewModel.openSearchTab("") },
                    onAnalyzerClick = viewModel::openAnalyzerTab,
                    onTrashClick = viewModel::openTrashTab,
                    onImportMediaClick = callbacks.onImportMedia,
                    onImportDocsClick = callbacks.onImportDocs,
                    onNewNoteClick = callbacks.onNewNote,
                    onOpenWorkspaceClick = viewModel::openWorkspaceTab,
                    onToggleNewFilesPreview = viewModel::toggleShowHomeNewFiles,
                    onOpenNewFilesFull = { viewModel.openSearchTab("") },
                    onToggleBookmarksPreview = viewModel::toggleShowHomeBookmarks,
                    onOpenBookmarksFull = viewModel::openBookmarksTab,
                    onBookmarkClick = { bm -> viewModel.openBookmark(bm, callbacks.onLaunchExternal) },
                    onRemoveBookmark = viewModel::removeBookmark,
                    onFileClick = { file -> viewModel.openFile(file, state.recentFiles, callbacks.onLaunchExternal) }
                )
            }

            is TabDestination.Library -> {
                val layout = if (isActivePage) state.activeLayout else viewModel.layoutFor(destination)
                val section = destination.section
                val sectionItems = remember(
                    state.allIndexedFiles,
                    state.documentFiles,
                    layout.sortField,
                    layout.sortAscending,
                    layout.customOrderKeys,
                    section
                ) {
                    viewModel.getLibrarySectionItems(section)
                }

                if (section == LibrarySection.IMAGES || section == LibrarySection.MOVIES) {
                    GalleryLibraryScreen(
                        allImages = sectionItems,
                        libraryState = state.libraryState,
                        selectedIds = if (isActivePage) state.selectedIds else emptySet(),
                        itemTagsByPath = state.itemTagsByPath,
                        gridColumns = layout.gridColumns,
                        originalMediaRatio = layout.originalMediaRatio,
                        section = section,
                        sortField = layout.sortField,
                        sortAscending = layout.sortAscending,
                        dragReorderEnabled = state.prefs.dragReorderEnabled,
                        isActivePage = isActivePage,
                        onBack = { viewModel.handleBackPress() },
                        onSelectPanel = { panel -> viewModel.selectGalleryPanel(panel, section) },
                        onSortOrderChange = viewModel::setSortOrder,
                        onOpenAlbum = { albumId -> viewModel.openPhotoAlbum(albumId, section) },
                        onCloseAlbum = { viewModel.closePhotoAlbum(section) },
                        onCreateAlbumClick = { items -> viewModel.openCreateAlbumDialog(items, section) },
                        onRenameAlbumClick = viewModel::openRenameAlbumDialog,
                        onDeleteAlbumClick = viewModel::deletePhotoAlbum,
                        onSetAlbumCover = viewModel::setPhotoAlbumCover,
                        onAddToAlbumClick = { items -> viewModel.openAddToAlbumDialog(items, section) },
                        onPickImagesForAlbumClick = { albumId -> viewModel.openPickImagesForAlbum(albumId, section) },
                        onRemoveImagesFromAlbum = viewModel::removeImagesFromAlbum,
                        onOpenDeviceAlbumFolder = viewModel::openDeviceAlbumFolder,
                        onPhotoClick = { photo, queue ->
                            viewModel.openFile(photo, queue, callbacks.onLaunchExternal)
                        },
                        onToggleSelect = { file -> viewModel.toggleSelection(file.id) },
                        onSelectAll = viewModel::selectAllVisible,
                        onClearSelection = viewModel::clearSelection,
                        onFileAction = { actionId, items ->
                            dispatchFileAction(actionId, items, viewModel, callbacks)
                        },
                        onReorderImages = viewModel::commitCustomReorder,
                        onImportMediaClick = callbacks.onImportMedia,
                        onMountFolderClick = callbacks.onMountFolder
                    )
                } else {
                    val rawFilterId = state.libraryState.subFiltersBySection[section] ?: "ALL"
                    val availableOptions = remember(section, sectionItems) {
                        LibraryDomainUtils.filterOptionsFor(section, sectionItems)
                    }
                    val activeFilterId = if (availableOptions.any { it.id == rawFilterId }) rawFilterId else "ALL"
                    val filteredItems = remember(section, sectionItems, activeFilterId) {
                        LibraryDomainUtils.applySubFilter(section, activeFilterId, sectionItems)
                    }
                    MediaLibraryScreen(
                        section = section,
                        allSectionItems = sectionItems,
                        filteredItems = filteredItems,
                        activeSubFilterId = activeFilterId,
                        selectedIds = if (isActivePage) state.selectedIds else emptySet(),
                        renamingItemId = if (isActivePage) state.renamingItemId else null,
                        itemTagsByPath = state.itemTagsByPath,
                        viewMode = layout.viewMode,
                        gridColumns = layout.gridColumns,
                        originalMediaRatio = layout.originalMediaRatio,
                        monochromeBinaryIcons = state.prefs.monochromeBinaryIcons,
                        showFileExtensions = state.prefs.showFileExtensions,
                        compactListMode = state.prefs.compactListMode,
                        isActivePage = isActivePage,
                        isScanning = section == LibrarySection.DOCUMENTS && state.isScanningDocuments,
                        onRescan = if (section == LibrarySection.DOCUMENTS) viewModel::refreshDocumentFiles else null,
                        onBack = { viewModel.handleBackPress() },
                        onSelectSubFilter = { filterId -> viewModel.setLibrarySubFilter(section, filterId) },
                        onToggleViewMode = {
                            val next = if (layout.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                            viewModel.setViewMode(next)
                        },
                        onOpenSearch = { viewModel.openSearchTab("") },
                        onFileClick = { file -> viewModel.openFile(file, filteredItems, callbacks.onLaunchExternal) },
                        onToggleSelect = { file -> viewModel.toggleSelection(file.id) },
                        onSelectAll = viewModel::selectAllVisible,
                        onClearSelection = viewModel::clearSelection,
                        onFileAction = { actionId, items ->
                            dispatchFileAction(actionId, items, viewModel, callbacks)
                        },
                        onConfirmInlineRename = viewModel::commitInlineRename,
                        onCancelInlineRename = viewModel::cancelInlineRename,
                        onImportClick = {
                            if (section == LibrarySection.MOVIES) {
                                callbacks.onImportMedia()
                            } else {
                                callbacks.onImportDocs()
                            }
                        },
                        onMountFolderClick = callbacks.onMountFolder
                    )
                }
            }

            is TabDestination.Directory,
            is TabDestination.Category,
            is TabDestination.Tag,
            is TabDestination.Search -> {
                val layout = if (isActivePage) state.activeLayout else viewModel.layoutFor(destination)
                val tabDirState = if (tabId != null) {
                    state.directoryStatesByTabId[tabId] ?: if (isActivePage) state.directoryState else DirectoryLoadState.Loading
                } else {
                    state.directoryState
                }
                val allCategoryItems = if (destination is TabDestination.Category) {
                    remember(
                        state.compressedFiles,
                        state.allIndexedFiles,
                        layout.sortField,
                        layout.sortAscending,
                        layout.customOrderKeys,
                        state.prefs.foldersFirst,
                        destination.category
                    ) {
                        viewModel.getCategoryFiles(destination.category)
                    }
                } else emptyList()

                val isArchivesDest = destination is TabDestination.Category &&
                    destination.category == ExplorerCategory.ARCHIVES
                val categoryItems = if (isArchivesDest) {
                    val activeFilter = CompressedFormatFilter.fromId(state.compressedFormatFilter)
                    remember(allCategoryItems, activeFilter) {
                        if (activeFilter == CompressedFormatFilter.ALL) allCategoryItems
                        else allCategoryItems.filter { activeFilter.matches(it) }
                    }
                } else {
                    allCategoryItems
                }

                val tagItems = if (destination is TabDestination.Tag) {
                    remember(state.taggedFilesByTagId, state.allIndexedFiles, layout.sortField, layout.sortAscending, layout.customOrderKeys, state.prefs.foldersFirst, destination.tagId) {
                        viewModel.getTagFiles(destination.tagId)
                    }
                } else emptyList()

                val searchResults = if (destination is TabDestination.Search) {
                    remember(state.allIndexedFiles, layout.sortField, layout.sortAscending, layout.customOrderKeys, state.prefs.foldersFirst, destination.query) {
                        viewModel.getSearchResults(destination.query)
                    }
                } else emptyList()

                val activeQueue = when (destination) {
                    is TabDestination.Directory -> (tabDirState as? DirectoryLoadState.Loaded)?.items ?: emptyList()
                    is TabDestination.Category -> categoryItems
                    is TabDestination.Tag -> tagItems
                    is TabDestination.Search -> searchResults
                    else -> emptyList()
                }

                BrowserScreen(
                    destination = destination,
                    directoryState = tabDirState,
                    categoryItems = categoryItems,
                    allCompressedItems = if (isArchivesDest) allCategoryItems else categoryItems,
                    isScanningCompressed = isArchivesDest && state.isScanningCompressed,
                    compressedFormatFilter = state.compressedFormatFilter,
                    tagItems = tagItems,
                    searchResults = searchResults,
                    allTags = state.allTags,
                    itemTagsByPath = state.itemTagsByPath,
                    selectedIds = if (isActivePage) state.selectedIds else emptySet(),
                    renamingItemId = if (isActivePage) state.renamingItemId else null,
                    clipboard = state.clipboard,
                    viewMode = layout.viewMode,
                    gridColumns = layout.gridColumns,
                    originalMediaRatio = layout.originalMediaRatio,
                    sortField = layout.sortField,
                    sortAscending = layout.sortAscending,
                    groupBy = layout.groupBy,
                    foldersFirst = state.prefs.foldersFirst,
                    monochromeBinaryIcons = state.prefs.monochromeBinaryIcons,
                    showFileExtensions = state.prefs.showFileExtensions,
                    compactListMode = state.prefs.compactListMode,
                    dragReorderEnabled = state.prefs.dragReorderEnabled,
                    onBack = { viewModel.handleBackPress() },
                    onBreadcrumbClick = viewModel::navigateToBreadcrumb,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onToggleViewMode = {
                        val next = if (layout.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                        viewModel.setViewMode(next)
                    },
                    onSortOrderChange = viewModel::setSortOrder,
                    onToggleSortOrder = viewModel::toggleSortOrder,
                    onGroupByChange = viewModel::setGroupBy,
                    onNewFolderClick = callbacks.onNewFolder,
                    onNewNoteClick = callbacks.onNewNote,
                    onRequestAllFilesAccess = callbacks.onRequestAllFilesAccess,
                    onMountFolderClick = callbacks.onMountFolder,
                    onImportMediaClick = callbacks.onImportMedia,
                    onImportDocsClick = callbacks.onImportDocs,
                    onCompressedFilterChange = viewModel::setCompressedFormatFilter,
                    onRescanCompressed = viewModel::refreshCompressedFiles,
                    onPasteClick = viewModel::pasteClipboardToCurrentDirectory,
                    onClearClipboard = viewModel::clearClipboard,
                    onFileClick = { file -> viewModel.openFile(file, activeQueue, callbacks.onLaunchExternal) },
                    onToggleSelect = { file -> viewModel.toggleSelection(file.id) },
                    onSelectAll = viewModel::selectAllVisible,
                    onClearSelection = viewModel::clearSelection,
                    onFileAction = { actionId, items ->
                        dispatchFileAction(actionId, items, viewModel, callbacks)
                    },
                    onConfirmInlineRename = viewModel::commitInlineRename,
                    onCancelInlineRename = viewModel::cancelInlineRename,
                    onMoveItemsToFolder = viewModel::moveItemsToFolder,
                    onReorderItems = viewModel::commitCustomReorder,
                    onTagSelected = viewModel::openTagTab,
                    isActivePage = isActivePage
                )
            }

            TabDestination.Settings -> {
                LaunchedEffect(isActivePage, state.showSettingsCacheManager) {
                    if (isActivePage) viewModel.refreshCacheStats()
                }
                AnimatedContent(
                    targetState = state.showSettingsCacheManager,
                    transitionSpec = {
                        if (targetState) {
                            (slideInHorizontally(tween(220)) { it / 4 } + fadeIn(tween(180)))
                                .togetherWith(slideOutHorizontally(tween(220)) { -it / 5 } + fadeOut(tween(150)))
                        } else {
                            (slideInHorizontally(tween(220)) { -it / 5 } + fadeIn(tween(180)))
                                .togetherWith(slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(150)))
                        }
                    },
                    label = "settings_subpage"
                ) { showCachePage ->
                    if (showCachePage) {
                        CacheDataManagerScreen(
                            snapshot = state.storageSnapshot,
                            onBack = viewModel::closeSettingsCacheManager,
                            onRefresh = viewModel::refreshCacheStats,
                            onClearStorageEntry = viewModel::clearManagedStorageEntry,
                            onClearPluginCache = viewModel::clearPluginCache,
                            onClearPluginData = viewModel::clearPluginData,
                            onClearAllCaches = viewModel::clearAllThumbnailCaches,
                            isActivePage = isActivePage
                        )
                    } else {
                        SettingsScreen(
                            prefs = state.prefs,
                            drives = state.drives,
                            indexedFileCount = state.allIndexedFiles.size,
                            hasAllFilesAccess = state.hasAllFilesAccess,
                            cacheStats = state.cacheStats,
                            totalManagedCacheBytes = state.storageSnapshot.totalManagedBytes,
                            onBack = { viewModel.handleBackPress() },
                            onThemeChange = viewModel::setThemePreference,
                            onMonochromeChange = viewModel::setMonochromeBinaryIcons,
                            onShowFileExtensionsChange = viewModel::setShowFileExtensions,
                            onCompactListChange = viewModel::setCompactListMode,
                            onViewModeChange = viewModel::setGlobalViewMode,
                            onGridColumnsChange = viewModel::setGlobalGridColumns,
                            onOriginalMediaRatioChange = viewModel::setGlobalOriginalMediaRatio,
                            onSortFieldChange = viewModel::setGlobalSortField,
                            onSortAscendingChange = viewModel::setGlobalSortAscending,
                            onGroupByChange = viewModel::setGlobalGroupBy,
                            onFoldersFirstChange = viewModel::setFoldersFirst,
                            onShowHiddenChange = viewModel::setShowHiddenFiles,
                            onShowFolderCountsChange = viewModel::setShowFolderCounts,
                            onRecycleBinEnabledChange = viewModel::setRecycleBinEnabled,
                            onConfirmBeforeDeleteChange = viewModel::setConfirmBeforeDelete,
                            onUseBuiltinTextViewerChange = viewModel::setUseBuiltinTextViewer,
                            onDragReorderChange = viewModel::setDragReorderEnabled,
                            onClearDefaultOpenApp = viewModel::clearDefaultOpenApp,
                            onClearAllDefaultOpenApps = viewModel::clearAllDefaultOpenApps,
                            onOpenCacheManager = viewModel::openSettingsCacheManager,
                            onClearCacheBucket = viewModel::clearCacheBucket,
                            onClearAllCaches = viewModel::clearAllThumbnailCaches,
                            onRequestAllFilesAccess = callbacks.onRequestAllFilesAccess,
                            onMountFolderClick = callbacks.onMountFolder,
                            onUnmountSafDrive = viewModel::unmountSafDrive,
                            onReindexStorage = viewModel::refreshHomeAndActiveTab,
                            onOpenWorkspace = viewModel::openWorkspaceTab,
                            onResetDefaults = viewModel::resetSettingsToDefaults,
                            isActivePage = isActivePage
                        )
                    }
                }
            }

            TabDestination.Analyzer -> {
                SpaceAnalyzerScreen(
                    drives = state.drives,
                    summaries = state.categorySummaries,
                    allIndexedFiles = state.allIndexedFiles,
                    monochrome = state.prefs.monochromeBinaryIcons,
                    onBack = { viewModel.handleBackPress() },
                    onCategoryClick = viewModel::openLibraryCategory,
                    onFileClick = { file -> viewModel.openFile(file, state.allIndexedFiles, callbacks.onLaunchExternal) },
                    onDeleteFile = { file -> callbacks.onDeleteRequest(listOf(file)) },
                    isActivePage = isActivePage
                )
            }

            TabDestination.Trash -> {
                val trashRootPath = remember(viewModel) { viewModel.getTrashRootPath() }
                RecycleBinScreen(
                    entries = state.trashEntries,
                    onBack = { viewModel.handleBackPress() },
                    onRestore = viewModel::restoreTrashEntry,
                    onDeleteForever = viewModel::deleteTrashEntryForever,
                    onEmptyTrash = viewModel::emptyTrash,
                    recycleBinEnabled = state.prefs.recycleBinEnabled,
                    monochrome = state.prefs.monochromeBinaryIcons,
                    trashRootPath = trashRootPath,
                    onToggleRecycleBinEnabled = viewModel::setRecycleBinEnabled,
                    onRestoreSelected = viewModel::restoreTrashEntries,
                    onDeleteSelectedForever = viewModel::deleteTrashEntriesForever,
                    isActivePage = isActivePage
                )
            }

            TabDestination.Bookmarks -> {
                val bookmarks = remember(state.prefs.bookmarkEntries) { state.prefs.parsedBookmarks() }
                BookmarksScreen(
                    bookmarks = bookmarks,
                    onBack = { viewModel.handleBackPress() },
                    onOpenBookmark = { bm -> viewModel.openBookmark(bm, callbacks.onLaunchExternal) },
                    onRemoveBookmark = viewModel::removeBookmark,
                    isActivePage = isActivePage
                )
            }
        }
    }

    private fun dispatchFileAction(
        actionId: FileActionId,
        items: List<FileItem>,
        viewModel: ExplorerViewModel,
        callbacks: DestinationCallbacks
    ) {
        when (actionId) {
            FileActionId.OPEN_WITH -> items.firstOrNull()?.let { viewModel.requestOpenWith(it, fromOpenAsAction = true) }
            FileActionId.EXTRACT -> items.firstOrNull()?.let { viewModel.openArchivePreview(it) }
            FileActionId.COMPRESS -> if (items.isNotEmpty()) viewModel.openCompressDialog(items)
            FileActionId.TAGS -> if (items.isNotEmpty()) viewModel.openTagPicker(items)
            FileActionId.COPY -> viewModel.stageClipboard(items, ClipboardOp.COPY)
            FileActionId.MOVE -> viewModel.stageClipboard(items, ClipboardOp.MOVE)
            FileActionId.RENAME -> items.firstOrNull()?.let(viewModel::startInlineRename)
            FileActionId.BATCH_RENAME -> if (items.isNotEmpty()) callbacks.onBatchRenameRequest(items)
            FileActionId.BOOKMARK -> items.firstOrNull()?.let(viewModel::toggleBookmark)
            FileActionId.INFO -> items.firstOrNull()?.let(viewModel::setInfoFile)
            FileActionId.DELETE -> callbacks.onDeleteRequest(items)
            FileActionId.SHARE -> {
                val filesOnly = items.filter { !it.isDirectory }
                if (filesOnly.isEmpty()) {
                    viewModel.showMessage("Folders cannot be shared directly")
                } else {
                    callbacks.onLaunchExternal(viewModel.buildShareIntent(filesOnly))
                }
            }
        }
    }
}
