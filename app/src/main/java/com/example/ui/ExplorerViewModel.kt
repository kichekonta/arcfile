package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppStorageSnapshot
import com.example.data.ArcCacheManager
import com.example.plugins.ArcPluginManager
import com.example.plugins.archive.ArchiveToolsPlugin
import com.example.plugins.editor.TextEditorPlugin
import com.example.data.ArchiveCompressionLevel
import com.example.data.ArchiveFormat
import com.example.data.ArchiveInspectionResult
import com.example.data.ArchiveOverwriteMode
import com.example.data.ArchiveTestResult
import com.example.data.BUILTIN_DOC_VIEWER_ACTIVITY
import com.example.data.BUILTIN_MEDIA_VIEWER_ACTIVITY
import com.example.data.BUILTIN_VIEWER_PACKAGE
import com.example.data.BookmarkEntry
import com.example.data.BreadcrumbNode
import com.example.data.CacheBucket
import com.example.data.CacheBucketStats
import com.example.data.CategorySummary
import com.example.data.ClipboardOp
import com.example.data.ClipboardState
import com.example.data.DirectoryLoadState
import com.example.data.DriveInfo
import com.example.data.DriveType
import com.example.data.EffectiveFolderLayout
import com.example.data.ExplorerCategory
import com.example.data.ExplorerPreferences
import com.example.data.ExplorerTab
import com.example.data.FileExplorerRepository
import com.example.data.FileGroupingUtils
import com.example.data.FileItem
import com.example.data.FileTag
import com.example.data.FileTransferProgress
import com.example.data.FormatUtils
import com.example.data.GalleryPanel
import com.example.data.GroupByField
import com.example.data.LibrarySection
import com.example.data.ManagedStorageId
import com.example.data.OpenWithRequest
import com.example.data.PhotoAlbum
import com.example.data.PreferencesRepository
import com.example.data.ResolvedAppOption
import com.example.data.SortField
import com.example.data.StorageMetadataCounts
import com.example.data.TabDestination
import com.example.data.TransferOperationType
import com.example.data.TransferStatus
import com.example.data.TrashEntry
import com.example.data.ViewMode
import com.example.data.ZipEntryInfo
import android.os.Environment
import com.example.data.db.AlbumRepository
import com.example.data.db.ArcDatabase
import com.example.data.db.FileTagRepository
import com.example.data.db.FolderLayoutEntity
import com.example.data.db.FolderLayoutRepository
import com.example.data.defaultOpenAppKey
import com.example.data.layoutStorageKey
import com.example.data.normalizeTagPath
import com.example.features.archive.ArchiveCoordinator
import com.example.features.files.FileOperationsCoordinator
import com.example.features.library.GalleryLibraryCoordinator
import com.example.features.settings.SettingsAndLayoutCoordinator
import com.example.features.storage.StorageCacheCoordinator
import com.example.features.tags.TagsAndBookmarksCoordinator
import com.example.ui.screens.library.LibraryDomainUtils
import com.example.ui.screens.library.LibraryUiState
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.EnumMap
import java.util.UUID

@Immutable
data class ExplorerUiState(
    val tabs: List<ExplorerTab> = emptyList(),
    val activeTabId: String = "tab_home",
    val drives: List<DriveInfo> = emptyList(),
    val categorySummaries: Map<ExplorerCategory, CategorySummary> = ExplorerCategory.entries.associateWith {
        CategorySummary(it, 0, 0L)
    },
    val librarySummaries: Map<ExplorerCategory, CategorySummary> = ExplorerCategory.entries.associateWith {
        CategorySummary(it, 0, 0L)
    },
    val allIndexedFiles: List<FileItem> = emptyList(),
    val compressedFiles: List<FileItem> = emptyList(),
    val isScanningCompressed: Boolean = false,
    val compressedFormatFilter: String = "ALL",
    val documentFiles: List<FileItem> = emptyList(),
    val isScanningDocuments: Boolean = false,
    val recentFiles: List<FileItem> = emptyList(),
    val directoryState: DirectoryLoadState = DirectoryLoadState.Loaded(emptyList()),
    val directoryStatesByTabId: Map<String, DirectoryLoadState> = emptyMap(),
    val selectedIds: Set<String> = emptySet(),
    val renamingItemId: String? = null,
    val clipboard: ClipboardState? = null,
    val prefs: ExplorerPreferences = ExplorerPreferences(),
    val folderLayouts: Map<String, FolderLayoutEntity> = emptyMap(),
    val activeLayout: EffectiveFolderLayout = EffectiveFolderLayout(),
    val isRefreshing: Boolean = false,
    val previewFile: FileItem? = null,
    val previewQueue: List<FileItem> = emptyList(),
    val previewText: String? = null,
    val archivePreviewFile: FileItem? = null,
    val archiveInspection: ArchiveInspectionResult? = null,
    val archivePreviewLoading: Boolean = false,
    val archiveTestResult: ArchiveTestResult? = null,
    val archiveTesting: Boolean = false,
    val compressTargets: List<FileItem>? = null,
    val trashEntries: List<TrashEntry> = emptyList(),
    val infoFile: FileItem? = null,
    val openWithRequest: OpenWithRequest? = null,
    val allTags: List<FileTag> = emptyList(),
    val itemTagsByPath: Map<String, List<FileTag>> = emptyMap(),
    val taggedFilesByTagId: Map<String, List<FileItem>> = emptyMap(),
    val tagPickerTargets: List<FileItem>? = null,
    val libraryState: LibraryUiState = LibraryUiState(),
    val showNewTabSheet: Boolean = false,
    val hasAllFilesAccess: Boolean = false,
    val activeTransfer: FileTransferProgress? = null,
    val cacheStats: List<CacheBucketStats> = emptyList(),
    val storageSnapshot: AppStorageSnapshot = AppStorageSnapshot(),
    val showSettingsCacheManager: Boolean = false,
    val snackbarMessage: String? = null
) {
    val activeTab: ExplorerTab?
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.firstOrNull()
}

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FileExplorerRepository(application)
    private val prefsRepo = PreferencesRepository(application)
    private val db = ArcDatabase.getInstance(application)
    private val folderLayoutRepo = FolderLayoutRepository(db.folderLayoutDao())
    private val fileTagRepo = FileTagRepository(db.fileTagDao())
    private val albumRepo = AlbumRepository(db.photoAlbumDao())
    private var directoryLoadJob: Job? = null
    private var folderCountEnrichmentJob: Job? = null
    private var sortJob: Job? = null
    private var homeFastLoadJob: Job? = null
    private var trashLoadJob: Job? = null
    private var librarySummaryJob: Job? = null
    private var homeIndexJob: Job? = null
    private var compressedScanJob: Job? = null
    private var documentScanJob: Job? = null
    private var activeTransferJob: Job? = null
    private var initialIndexStarted = false
    private var defaultInternalTabSeeded = false
    private var defaultExternalTabSeeded = false
    private var initialPrefsApplied = false
    private val backgroundTabLoadJobs = mutableMapOf<String, Job>()
    private var pendingApkInstallItem: FileItem? = null

    private val _uiState = MutableStateFlow(
        ExplorerUiState(
            tabs = listOf(
                ExplorerTab(
                    id = "tab_home",
                    title = "Home",
                    isPinned = true,
                    destination = TabDestination.Home
                )
            ),
            activeTabId = "tab_home"
        )
    )
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    val storageCoordinator = StorageCacheCoordinator(
        app = application,
        scope = viewModelScope,
        uiState = _uiState,
        albumRepo = albumRepo,
        folderLayoutRepo = folderLayoutRepo,
        prefsRepo = prefsRepo,
        fileTagRepo = fileTagRepo,
        onLayoutsReset = {
            _uiState.update { it.copy(folderLayouts = emptyMap()).withResolvedLayout() }
            resortLoadedDirectoryInMemory()
        },
        onMessage = ::showMessage
    )

    val galleryCoordinator = GalleryLibraryCoordinator(
        scope = viewModelScope,
        uiState = _uiState,
        albumRepo = albumRepo,
        onOpenFolderDestination = { title, dest, predicate ->
            openOrSwitchDestination(
                title = title,
                destination = dest,
                matchPredicate = predicate,
                forceNewTab = false
            )
        },
        onMessage = ::showMessage
    )

    val archiveCoordinator = ArchiveCoordinator(
        scope = viewModelScope,
        uiState = _uiState,
        repo = repo,
        isTransferActive = { activeTransferJob?.isActive == true },
        registerTransferJob = { job -> activeTransferJob = job },
        onRefreshAfterChange = ::refreshHomeAndActiveTab,
        onMessage = ::showMessage
    )

    val tagsCoordinator = TagsAndBookmarksCoordinator(
        scope = viewModelScope,
        uiState = _uiState,
        fileTagRepo = fileTagRepo,
        prefsRepo = prefsRepo,
        onOpenDestination = { title, dest, predicate, forceNewTab ->
            openOrSwitchDestination(
                title = title,
                destination = dest,
                matchPredicate = predicate,
                forceNewTab = forceNewTab
            )
        },
        onOpenFile = { item, onLaunchExternal -> openFile(item, onLaunchExternal) },
        onMessage = ::showMessage
    )

    val settingsCoordinator = SettingsAndLayoutCoordinator(
        scope = viewModelScope,
        uiState = _uiState,
        prefsRepo = prefsRepo,
        folderLayoutRepo = folderLayoutRepo,
        withResolvedLayout = { state -> state.withResolvedLayout() },
        onResortDirectory = ::resortLoadedDirectoryInMemory,
        onMessage = ::showMessage
    )

    val fileOpsCoordinator = FileOperationsCoordinator(
        scope = viewModelScope,
        uiState = _uiState,
        repo = repo,
        fileTagRepo = fileTagRepo,
        albumRepo = albumRepo,
        isTransferActive = { activeTransferJob?.isActive == true },
        getActiveTransferJob = { activeTransferJob },
        registerTransferJob = { job -> activeTransferJob = job },
        onRemoveFolderState = { path -> settingsCoordinator.removeFolderStateBoth(path) },
        onRefreshAfterChange = ::refreshHomeAndActiveTab,
        onMessage = ::showMessage
    )

    init {
        // Load local drives & trash independently so storage cards appear immediately on frame 1
        loadLocalDrivesOnlyImmediate()
        loadTrashImmediate()
        refreshCacheStats()

        viewModelScope.launch {
            prefsRepo.preferencesFlow.collect { prefs ->
                val prevState = _uiState.value
                val prevPrefs = prevState.prefs
                _uiState.update { it.copy(prefs = prefs).withResolvedLayout() }
                val safChanged = prevPrefs.mountedSafEntries != prefs.mountedSafEntries
                val hiddenChanged = prevPrefs.showHiddenFiles != prefs.showHiddenFiles
                if (!initialPrefsApplied) {
                    initialPrefsApplied = true
                    if (prefs.mountedSafEntries.isNotEmpty()) {
                        loadDrivesImmediate(prefs.mountedSafEntries)
                    }
                    scheduleBackgroundIndex(prefs, staggerForStartup = true)
                    reloadActiveDirectoryIfNeeded()
                } else if (safChanged) {
                    loadDrivesImmediate(prefs.mountedSafEntries)
                    scheduleBackgroundIndex(prefs, staggerForStartup = false)
                    reloadActiveDirectoryIfNeeded()
                } else if (hiddenChanged || !initialIndexStarted) {
                    scheduleBackgroundIndex(prefs, staggerForStartup = false)
                    if (hiddenChanged) reloadActiveDirectoryIfNeeded()
                } else if (prevPrefs.showFolderCounts != prefs.showFolderCounts) {
                    reloadActiveDirectoryIfNeeded()
                } else if (prevPrefs.sortField != prefs.sortField ||
                    prevPrefs.sortAscending != prefs.sortAscending ||
                    prevPrefs.foldersFirst != prefs.foldersFirst
                ) {
                    resortLoadedDirectoryInMemory()
                }
            }
        }
        settingsCoordinator.observeFolderLayouts()
        tagsCoordinator.observeTags()
        galleryCoordinator.observeLibraryAlbums()
    }

    private fun resolveLayout(
        destination: TabDestination?,
        prefs: ExplorerPreferences,
        layouts: Map<String, FolderLayoutEntity>
    ): EffectiveFolderLayout {
        val key = destination?.layoutStorageKey()
        if (key != null) {
            layouts[key]?.let { return it.toEffectiveLayout() }
        }
        val libraryDefaultSort = if (
            destination is TabDestination.Library &&
            (destination.section == LibrarySection.IMAGES ||
                destination.section == LibrarySection.MOVIES ||
                destination.section == LibrarySection.DOWNLOADS)
        ) {
            SortField.DATE to false
        } else {
            prefs.sortField to prefs.sortAscending
        }
        val libraryDefaultView = if (
            destination is TabDestination.Library &&
            (destination.section == LibrarySection.IMAGES || destination.section == LibrarySection.MOVIES)
        ) {
            ViewMode.GRID
        } else {
            prefs.viewMode
        }
        return EffectiveFolderLayout(
            viewMode = libraryDefaultView,
            gridColumns = prefs.gridColumns,
            originalMediaRatio = prefs.originalMediaRatio,
            sortField = libraryDefaultSort.first,
            sortAscending = libraryDefaultSort.second,
            groupBy = prefs.groupBy,
            isCustomSaved = false
        )
    }

    fun layoutFor(destination: TabDestination?): EffectiveFolderLayout {
        val state = _uiState.value
        return resolveLayout(destination, state.prefs, state.folderLayouts)
    }

    private fun ExplorerUiState.withResolvedLayout(): ExplorerUiState =
        copy(activeLayout = resolveLayout(activeTab?.destination, prefs, folderLayouts))

    private fun resortLoadedDirectoryInMemory() {
        val state = _uiState.value
        val active = state.activeTab ?: return
        if (active.destination !is TabDestination.Directory) return
        val loaded = state.directoryState as? DirectoryLoadState.Loaded ?: return
        val sortField = state.activeLayout.sortField
        val sortAscending = state.activeLayout.sortAscending
        val customOrderKeys = state.activeLayout.customOrderKeys
        val foldersFirst = state.prefs.foldersFirst
        sortJob?.cancel()
        if (loaded.items.size <= 300) {
            val sorted = FileGroupingUtils.sortItems(
                items = loaded.items,
                sortField = sortField,
                ascending = sortAscending,
                foldersFirst = foldersFirst,
                customOrderKeys = customOrderKeys
            )
            val nextLoaded = loaded.copy(items = sorted)
            _uiState.update { current ->
                current.copy(
                    directoryState = nextLoaded,
                    directoryStatesByTabId = current.directoryStatesByTabId + (active.id to nextLoaded)
                )
            }
        } else {
            val sourceItems = loaded.items
            sortJob = viewModelScope.launch {
                val sorted = withContext(Dispatchers.Default) {
                    FileGroupingUtils.sortItems(
                        items = sourceItems,
                        sortField = sortField,
                        ascending = sortAscending,
                        foldersFirst = foldersFirst,
                        customOrderKeys = customOrderKeys
                    )
                }
                _uiState.update { current ->
                    val currentLoaded = current.directoryState as? DirectoryLoadState.Loaded ?: return@update current
                    if (currentLoaded.items !== sourceItems) return@update current
                    val nextLoaded = currentLoaded.copy(items = sorted)
                    val curActiveId = current.activeTab?.id
                    current.copy(
                        directoryState = nextLoaded,
                        directoryStatesByTabId = if (curActiveId != null) {
                            current.directoryStatesByTabId + (curActiveId to nextLoaded)
                        } else current.directoryStatesByTabId
                    )
                }
            }
        }
    }

    private fun loadLocalDrivesOnlyImmediate() {
        homeFastLoadJob?.cancel()
        homeFastLoadJob = viewModelScope.launch {
            val accessDeferred = async(Dispatchers.IO) { repo.hasAllFilesAccess() }
            val drivesDeferred = async(Dispatchers.IO) { repo.loadLocalDrivesFast() }
            val hasAccess = accessDeferred.await()
            val drives = drivesDeferred.await()
            applyLoadedDrives(drives, hasAccess, staggerTabPreload = true)
        }
    }

    private fun loadDrivesImmediate(mountedSafEntries: Set<String>) {
        homeFastLoadJob?.cancel()
        homeFastLoadJob = viewModelScope.launch {
            val accessDeferred = async(Dispatchers.IO) { repo.hasAllFilesAccess() }
            val drivesDeferred = async(Dispatchers.IO) { repo.loadDrives(mountedSafEntries) }
            val hasAccess = accessDeferred.await()
            val drives = drivesDeferred.await()
            applyLoadedDrives(drives, hasAccess, staggerTabPreload = false)
        }
    }

    private fun loadTrashImmediate() {
        trashLoadJob?.cancel()
        trashLoadJob = viewModelScope.launch {
            val trash = withContext(Dispatchers.IO) { repo.listTrashEntries() }
            _uiState.update { it.copy(trashEntries = trash) }
        }
    }

    private fun loadDrivesAndTrashImmediate(mountedSafEntries: Set<String>) {
        loadDrivesImmediate(mountedSafEntries)
        loadTrashImmediate()
    }

    private fun applyLoadedDrives(
        drives: List<DriveInfo>,
        hasAccess: Boolean,
        staggerTabPreload: Boolean
    ) {
        val newlySeededTabs = mutableListOf<ExplorerTab>()
        _uiState.update { state ->
            var updatedTabs = state.tabs
            if (!defaultInternalTabSeeded) {
                val internal = drives.firstOrNull { it.type == DriveType.INTERNAL }
                if (internal != null) {
                    defaultInternalTabSeeded = true
                    val alreadyOpen = updatedTabs.any {
                        (it.destination as? TabDestination.Directory)?.rootId == internal.id
                    }
                    if (!alreadyOpen) {
                        val internalTab = ExplorerTab(
                            id = "tab_default_internal",
                            title = "Internal",
                            isPinned = false,
                            destination = TabDestination.Directory(
                                title = internal.name,
                                rootId = internal.id,
                                currentPathOrUri = internal.pathOrUri,
                                isSaf = internal.isSaf,
                                breadcrumbs = listOf(BreadcrumbNode(internal.name, internal.pathOrUri, internal.isSaf))
                            )
                        )
                        updatedTabs = updatedTabs + internalTab
                        newlySeededTabs += internalTab
                    }
                }
            }
            if (!defaultExternalTabSeeded) {
                val external = drives.firstOrNull { it.type == DriveType.EXTERNAL_VOLUME }
                    ?: drives.firstOrNull { it.type == DriveType.SAF_TREE }
                if (external != null && external.pathOrUri.isNotBlank()) {
                    defaultExternalTabSeeded = true
                    val alreadyOpen = updatedTabs.any {
                        (it.destination as? TabDestination.Directory)?.rootId == external.id
                    }
                    if (!alreadyOpen) {
                        val externalTab = ExplorerTab(
                            id = "tab_default_external",
                            title = external.name,
                            isPinned = false,
                            destination = TabDestination.Directory(
                                title = external.name,
                                rootId = external.id,
                                currentPathOrUri = external.pathOrUri,
                                isSaf = external.isSaf,
                                breadcrumbs = listOf(BreadcrumbNode(external.name, external.pathOrUri, external.isSaf))
                            )
                        )
                        updatedTabs = updatedTabs + externalTab
                        newlySeededTabs += externalTab
                    }
                }
            }
            state.copy(
                drives = drives,
                hasAllFilesAccess = hasAccess,
                tabs = updatedTabs
            )
        }
        if (newlySeededTabs.isNotEmpty()) {
            viewModelScope.launch {
                if (staggerTabPreload) {
                    withContext(Dispatchers.Default) { delay(80L) }
                }
                newlySeededTabs.forEach { tab ->
                    ensureDirectoryLoadedForTab(tab.id)
                }
            }
        }
    }

    private fun loadLibrarySummariesImmediate(showHidden: Boolean) {
        librarySummaryJob?.cancel()
        librarySummaryJob = viewModelScope.launch {
            val summaries = repo.loadLibraryFolderSummaries(showHidden)
            _uiState.update { it.copy(librarySummaries = summaries) }
        }
    }

    private fun scheduleBackgroundIndex(prefs: ExplorerPreferences, staggerForStartup: Boolean = false) {
        initialIndexStarted = true
        loadLibrarySummariesImmediate(prefs.showHiddenFiles)
        homeIndexJob?.cancel()
        compressedScanJob?.cancel()
        documentScanJob?.cancel()
        homeIndexJob = viewModelScope.launch {
            if (staggerForStartup) {
                withContext(Dispatchers.Default) { delay(220L) }
            }
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    isScanningCompressed = true,
                    isScanningDocuments = true
                )
            }
            val indexDeferred = async {
                repo.indexAllAccessibleFiles(prefs.mountedSafEntries, prefs.showHiddenFiles)
            }
            val compressedDeferred = async {
                repo.scanAllCompressedFiles(prefs.mountedSafEntries, prefs.showHiddenFiles)
            }
            val documentsDeferred = async {
                repo.scanAllDocuments(prefs.mountedSafEntries, prefs.showHiddenFiles)
            }
            val indexed = indexDeferred.await()
            val scannedCompressed = compressedDeferred.await()
            val scannedDocuments = documentsDeferred.await()
            val mergedCompressed = withContext(Dispatchers.Default) {
                mergeUniqueArchives(scannedCompressed, indexed.filter { it.isArchive })
            }
            val mergedDocuments = withContext(Dispatchers.Default) {
                mergeUniqueDocuments(scannedDocuments, indexed.filter { it.isDocument })
            }
            val grouped = withContext(Dispatchers.Default) {
                val counts = EnumMap<ExplorerCategory, Int>(ExplorerCategory::class.java)
                val bytes = EnumMap<ExplorerCategory, Long>(ExplorerCategory::class.java)
                for (item in indexed) {
                    val cat = repo.categorizeFile(item)
                    counts[cat] = (counts[cat] ?: 0) + 1
                    bytes[cat] = (bytes[cat] ?: 0L) + item.sizeBytes
                }
                counts[ExplorerCategory.ARCHIVES] = mergedCompressed.size
                bytes[ExplorerCategory.ARCHIVES] = mergedCompressed.sumOf { it.sizeBytes }
                counts[ExplorerCategory.DOCUMENTS] = mergedDocuments.size
                bytes[ExplorerCategory.DOCUMENTS] = mergedDocuments.sumOf { it.sizeBytes }
                ExplorerCategory.entries.associateWith { cat ->
                    CategorySummary(
                        category = cat,
                        itemCount = counts[cat] ?: 0,
                        totalBytes = bytes[cat] ?: 0L
                    )
                }
            }
            val libraryGrouped = withContext(Dispatchers.Default) {
                LibraryDomainUtils.computeLibrarySummaries(indexed)
            }
            _uiState.update {
                it.copy(
                    allIndexedFiles = indexed,
                    compressedFiles = scannedCompressed,
                    isScanningCompressed = false,
                    documentFiles = scannedDocuments,
                    isScanningDocuments = false,
                    recentFiles = indexed.take(15),
                    categorySummaries = grouped,
                    librarySummaries = libraryGrouped,
                    isRefreshing = false
                )
            }
        }
    }

    fun refreshCompressedFiles() {
        val prefs = _uiState.value.prefs
        compressedScanJob?.cancel()
        compressedScanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanningCompressed = true) }
            try {
                val scanned = repo.scanAllCompressedFiles(prefs.mountedSafEntries, prefs.showHiddenFiles)
                _uiState.update { state ->
                    val merged = mergeUniqueArchives(scanned, state.allIndexedFiles.filter { it.isArchive })
                    val updatedSummaries = state.categorySummaries.toMutableMap()
                    updatedSummaries[ExplorerCategory.ARCHIVES] = CategorySummary(
                        category = ExplorerCategory.ARCHIVES,
                        itemCount = merged.size,
                        totalBytes = merged.sumOf { it.sizeBytes }
                    )
                    state.copy(
                        compressedFiles = scanned,
                        isScanningCompressed = false,
                        categorySummaries = updatedSummaries
                    )
                }
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isScanningCompressed = false) }
                throw ce
            } catch (_: Throwable) {
                _uiState.update { it.copy(isScanningCompressed = false) }
            }
        }
    }

    fun refreshDocumentFiles() {
        val prefs = _uiState.value.prefs
        documentScanJob?.cancel()
        documentScanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanningDocuments = true) }
            try {
                val scanned = repo.scanAllDocuments(prefs.mountedSafEntries, prefs.showHiddenFiles)
                _uiState.update { state ->
                    val merged = mergeUniqueDocuments(scanned, state.allIndexedFiles.filter { it.isDocument })
                    val updatedSummaries = state.categorySummaries.toMutableMap()
                    updatedSummaries[ExplorerCategory.DOCUMENTS] = CategorySummary(
                        category = ExplorerCategory.DOCUMENTS,
                        itemCount = merged.size,
                        totalBytes = merged.sumOf { it.sizeBytes }
                    )
                    state.copy(
                        documentFiles = scanned,
                        isScanningDocuments = false,
                        categorySummaries = updatedSummaries
                    )
                }
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isScanningDocuments = false) }
                throw ce
            } catch (_: Throwable) {
                _uiState.update { it.copy(isScanningDocuments = false) }
            }
        }
    }

    fun setCompressedFormatFilter(filterId: String) {
        _uiState.update { it.copy(compressedFormatFilter = filterId) }
    }

    private fun mergeUniqueArchives(
        primary: List<FileItem>,
        secondary: List<FileItem>
    ): List<FileItem> {
        if (primary.isEmpty()) return secondary
        if (secondary.isEmpty()) return primary
        val merged = ArrayList<FileItem>(primary.size + secondary.size)
        val seen = HashSet<String>(primary.size + secondary.size)
        for (item in primary) {
            if (!item.isDirectory && item.isArchive && seen.add(normalizeTagPath(item.pathOrUri, item.isSafUri))) {
                merged.add(item)
            }
        }
        for (item in secondary) {
            if (!item.isDirectory && item.isArchive && seen.add(normalizeTagPath(item.pathOrUri, item.isSafUri))) {
                merged.add(item)
            }
        }
        return merged
    }

    private fun mergeUniqueDocuments(
        primary: List<FileItem>,
        secondary: List<FileItem>
    ): List<FileItem> {
        if (primary.isEmpty()) return secondary
        if (secondary.isEmpty()) return primary
        val merged = ArrayList<FileItem>(primary.size + secondary.size)
        val seen = HashSet<String>(primary.size + secondary.size)
        for (item in primary) {
            if (!item.isDirectory && item.isDocument && seen.add(normalizeTagPath(item.pathOrUri, item.isSafUri))) {
                merged.add(item)
            }
        }
        for (item in secondary) {
            if (!item.isDirectory && item.isDocument && seen.add(normalizeTagPath(item.pathOrUri, item.isSafUri))) {
                merged.add(item)
            }
        }
        return merged
    }

    fun refreshHomeAndActiveTab() {
        val currentPrefs = _uiState.value.prefs
        loadDrivesAndTrashImmediate(currentPrefs.mountedSafEntries)
        reloadActiveDirectoryIfNeeded()
        scheduleBackgroundIndex(currentPrefs)
    }

    fun onAppResumed(onLaunchExternal: ((Intent) -> Unit)? = null) {
        val grantedNow = repo.hasAllFilesAccess()
        if (grantedNow != _uiState.value.hasAllFilesAccess) {
            refreshHomeAndActiveTab()
        }
        val pendingApk = pendingApkInstallItem
        if (pendingApk != null && repo.canRequestPackageInstalls() && onLaunchExternal != null) {
            pendingApkInstallItem = null
            launchApkInstall(pendingApk, onLaunchExternal)
        }
    }

    fun buildAllFilesAccessIntent(): Intent = repo.buildAllFilesAccessIntent()

    fun selectTab(tabId: String) {
        _uiState.update { state ->
            val cached = state.directoryStatesByTabId[tabId] ?: state.directoryState
            state.copy(
                activeTabId = tabId,
                directoryState = cached,
                selectedIds = emptySet(),
                renamingItemId = null
            ).withResolvedLayout()
        }
        val activeDest = _uiState.value.activeTab?.destination
        if (activeDest is TabDestination.Settings) {
            refreshCacheStats()
        } else if (activeDest is TabDestination.Category &&
            activeDest.category == ExplorerCategory.ARCHIVES &&
            _uiState.value.compressedFiles.isEmpty() &&
            !_uiState.value.isScanningCompressed
        ) {
            refreshCompressedFiles()
        } else if (activeDest is TabDestination.Library &&
            activeDest.section == LibrarySection.DOCUMENTS &&
            _uiState.value.documentFiles.isEmpty() &&
            !_uiState.value.isScanningDocuments
        ) {
            refreshDocumentFiles()
        }
        reloadActiveDirectoryIfNeeded()
    }

    fun closeTab(tabId: String) {
        backgroundTabLoadJobs.remove(tabId)?.cancel()
        _uiState.update { state ->
            val target = state.tabs.firstOrNull { it.id == tabId } ?: return@update state
            if (target.isPinned || state.tabs.size <= 1) return@update state
            val idx = state.tabs.indexOfFirst { it.id == tabId }
            val remaining = state.tabs.filterNot { it.id == tabId }
            val nextActive = if (state.activeTabId == tabId) {
                remaining.getOrNull((idx - 1).coerceAtLeast(0))?.id ?: remaining.first().id
            } else {
                state.activeTabId
            }
            val remainingStates = state.directoryStatesByTabId - tabId
            val nextDirState = remainingStates[nextActive] ?: state.directoryState
            state.copy(
                tabs = remaining,
                activeTabId = nextActive,
                directoryState = nextDirState,
                directoryStatesByTabId = remainingStates,
                selectedIds = emptySet(),
                renamingItemId = null
            ).withResolvedLayout()
        }
        reloadActiveDirectoryIfNeeded()
    }

    fun openDriveInTab(drive: DriveInfo, forceNewTab: Boolean = false) {
        val dest = TabDestination.Directory(
            title = drive.name,
            rootId = drive.id,
            currentPathOrUri = drive.pathOrUri,
            isSaf = drive.isSaf,
            breadcrumbs = listOf(BreadcrumbNode(drive.name, drive.pathOrUri, drive.isSaf))
        )
        openOrSwitchDestination(
            title = if (drive.type == DriveType.INTERNAL) "Internal" else drive.name,
            destination = dest,
            matchPredicate = { tab ->
                val d = tab.destination as? TabDestination.Directory
                d?.rootId == drive.id
            },
            forceNewTab = forceNewTab
        )
    }

    fun openCategoryInTab(category: ExplorerCategory) {
        if (category == ExplorerCategory.ARCHIVES) {
            refreshCompressedFiles()
        }
        openOrSwitchDestination(
            title = category.label,
            destination = TabDestination.Category(category),
            matchPredicate = { tab ->
                (tab.destination as? TabDestination.Category)?.category == category
            },
            forceNewTab = false
        )
    }

    fun openSearchTab(initialQuery: String = "") {
        openOrSwitchDestination(
            title = "Search",
            destination = TabDestination.Search(initialQuery),
            matchPredicate = { tab -> tab.destination is TabDestination.Search },
            forceNewTab = false
        )
    }

    fun openSettingsTab() {
        _uiState.update { it.copy(showSettingsCacheManager = false) }
        refreshCacheStats()
        openOrSwitchDestination(
            title = "Settings",
            destination = TabDestination.Settings,
            matchPredicate = { tab -> tab.destination is TabDestination.Settings },
            forceNewTab = false
        )
    }

    fun openSettingsCacheManager() {
        _uiState.update { it.copy(showSettingsCacheManager = true) }
        refreshCacheStats()
    }

    fun closeSettingsCacheManager() {
        _uiState.update { it.copy(showSettingsCacheManager = false) }
    }

    fun openAnalyzerTab() {
        openOrSwitchDestination(
            title = "Analyzer",
            destination = TabDestination.Analyzer,
            matchPredicate = { tab -> tab.destination is TabDestination.Analyzer },
            forceNewTab = false
        )
    }

    fun openTrashTab() {
        openOrSwitchDestination(
            title = "Recycle Bin",
            destination = TabDestination.Trash,
            matchPredicate = { tab -> tab.destination is TabDestination.Trash },
            forceNewTab = false
        )
    }

    fun openBookmarksTab() {
        openOrSwitchDestination(
            title = "Bookmarks",
            destination = TabDestination.Bookmarks,
            matchPredicate = { tab -> tab.destination is TabDestination.Bookmarks },
            forceNewTab = false
        )
    }

    fun openLibraryCategory(category: ExplorerCategory) {
        val section = LibrarySection.fromCategory(category)
        if (section != null) {
            openLibrarySection(section)
        } else {
            openCategoryInTab(category)
        }
    }

    fun openLibrarySection(section: LibrarySection) {
        if (section == LibrarySection.DOCUMENTS) {
            refreshDocumentFiles()
        }
        openOrSwitchDestination(
            title = section.label,
            destination = TabDestination.Library(section),
            matchPredicate = { tab ->
                (tab.destination as? TabDestination.Library)?.section == section
            },
            forceNewTab = false
        )
    }

    fun openQuickPublicFolder(dirType: String, title: String) {
        val internal = _uiState.value.drives.firstOrNull { it.type == DriveType.INTERNAL }
        val target = runCatching { Environment.getExternalStoragePublicDirectory(dirType) }.getOrNull()
            ?: internal?.let { File(it.pathOrUri, dirType) }
        if (target == null) {
            if (internal != null) openDriveInTab(internal) else showMessage("$title folder unavailable")
            return
        }
        if (!target.exists()) {
            val created = runCatching { target.mkdirs() }.getOrDefault(false)
            if (!created && !target.exists()) {
                if (internal != null) openDriveInTab(internal) else showMessage("$title folder unavailable")
                return
            }
        }
        val internalRootPath = internal?.pathOrUri
            ?: runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrNull()
            ?: target.parentFile?.absolutePath
            ?: target.absolutePath
        val internalTitle = internal?.name ?: "Internal Storage"
        val crumbs = if (internalRootPath != target.absolutePath) {
            listOf(
                BreadcrumbNode(internalTitle, internalRootPath, false),
                BreadcrumbNode(title, target.absolutePath, false)
            )
        } else {
            listOf(BreadcrumbNode(title, target.absolutePath, false))
        }
        openOrSwitchDestination(
            title = title,
            destination = TabDestination.Directory(
                title = title,
                rootId = "drive_internal",
                currentPathOrUri = target.absolutePath,
                isSaf = false,
                breadcrumbs = crumbs
            ),
            matchPredicate = { tab ->
                (tab.destination as? TabDestination.Directory)?.currentPathOrUri == target.absolutePath
            },
            forceNewTab = false
        )
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            val active = state.activeTab ?: return@update state
            if (active.destination !is TabDestination.Search) return@update state
            val updatedTabs = state.tabs.map {
                if (it.id == active.id) it.copy(destination = TabDestination.Search(query)) else it
            }
            state.copy(tabs = updatedTabs)
        }
    }

    private fun openOrSwitchDestination(
        title: String,
        destination: TabDestination,
        matchPredicate: (ExplorerTab) -> Boolean,
        forceNewTab: Boolean
    ) {
        val existing = if (!forceNewTab) _uiState.value.tabs.firstOrNull(matchPredicate) else null
        if (existing != null) {
            _uiState.update { state ->
                val updatedTabs = state.tabs.map { tab ->
                    if (tab.id == existing.id) {
                        tab.copy(title = title, destination = destination)
                    } else tab
                }
                val pathChanged = (existing.destination as? TabDestination.Directory)?.currentPathOrUri !=
                    (destination as? TabDestination.Directory)?.currentPathOrUri
                val nextCache = if (pathChanged) {
                    state.directoryStatesByTabId - existing.id
                } else {
                    state.directoryStatesByTabId
                }
                val nextDirState = nextCache[existing.id] ?: DirectoryLoadState.Loading
                state.copy(
                    tabs = updatedTabs,
                    activeTabId = existing.id,
                    directoryState = nextDirState,
                    directoryStatesByTabId = nextCache,
                    selectedIds = emptySet(),
                    showNewTabSheet = false
                ).withResolvedLayout()
            }
        } else {
            val newId = "tab_${UUID.randomUUID().toString().take(8)}"
            val newTab = ExplorerTab(
                id = newId,
                title = title,
                isPinned = false,
                destination = destination
            )
            _uiState.update { state ->
                state.copy(
                    tabs = state.tabs + newTab,
                    activeTabId = newId,
                    directoryState = DirectoryLoadState.Loading,
                    selectedIds = emptySet(),
                    showNewTabSheet = false
                ).withResolvedLayout()
            }
        }
        reloadActiveDirectoryIfNeeded()
    }

    fun navigateIntoFolder(folder: FileItem) {
        if (!folder.isDirectory) return
        _uiState.update { state ->
            val active = state.activeTab ?: return@update state
            val currentDir = active.destination as? TabDestination.Directory ?: return@update state
            val nextDest = currentDir.copy(
                title = folder.name,
                currentPathOrUri = folder.pathOrUri,
                isSaf = folder.isSafUri,
                breadcrumbs = currentDir.breadcrumbs + BreadcrumbNode(folder.name, folder.pathOrUri, folder.isSafUri)
            )
            val updatedTabs = state.tabs.map {
                if (it.id == active.id) it.copy(title = folder.name, destination = nextDest) else it
            }
            state.copy(
                tabs = updatedTabs,
                directoryStatesByTabId = state.directoryStatesByTabId - active.id,
                selectedIds = emptySet()
            ).withResolvedLayout()
        }
        reloadActiveDirectoryIfNeeded()
    }

    fun navigateToBreadcrumb(index: Int) {
        _uiState.update { state ->
            val active = state.activeTab ?: return@update state
            val currentDir = active.destination as? TabDestination.Directory ?: return@update state
            if (index !in currentDir.breadcrumbs.indices) return@update state
            val subTrail = currentDir.breadcrumbs.take(index + 1)
            val target = subTrail.last()
            val nextDest = currentDir.copy(
                title = target.title,
                currentPathOrUri = target.pathOrUri,
                isSaf = target.isSaf,
                breadcrumbs = subTrail
            )
            val updatedTabs = state.tabs.map {
                if (it.id == active.id) it.copy(title = target.title, destination = nextDest) else it
            }
            state.copy(
                tabs = updatedTabs,
                directoryStatesByTabId = state.directoryStatesByTabId - active.id,
                selectedIds = emptySet()
            ).withResolvedLayout()
        }
        reloadActiveDirectoryIfNeeded()
    }

    /**
     * Handles system back press: clears selection/modals first, steps up folder breadcrumbs,
     * or returns to Home tab if currently on a secondary tab.
     * Returns true if consumed, false if system should close the activity.
     */
    fun handleBackPress(): Boolean {
        val state = _uiState.value
        val lib = state.libraryState
        if (lib.pickImagesForAlbumId != null) {
            dismissPickImagesForAlbum()
            return true
        }
        if (lib.renameAlbumTarget != null) {
            dismissRenameAlbumDialog()
            return true
        }
        if (lib.showCreateAlbumDialog) {
            dismissCreateAlbumDialog()
            return true
        }
        if (lib.addToAlbumTargets != null) {
            dismissAddToAlbumDialog()
            return true
        }
        if (state.tagPickerTargets != null) {
            dismissTagPicker()
            return true
        }
        if (state.openWithRequest != null) {
            dismissOpenWith()
            return true
        }
        if (state.compressTargets != null) {
            dismissCompressDialog()
            return true
        }
        if (state.archivePreviewFile != null) {
            closeArchivePreview()
            return true
        }
        if (state.previewFile != null) {
            closePreview()
            return true
        }
        if (state.infoFile != null) {
            setInfoFile(null)
            return true
        }
        if (state.showNewTabSheet) {
            _uiState.update { it.copy(showNewTabSheet = false) }
            return true
        }
        if (state.renamingItemId != null) {
            cancelInlineRename()
            return true
        }
        if (state.selectedIds.isNotEmpty()) {
            clearSelection()
            return true
        }
        val active = state.activeTab ?: return false
        if (active.destination is TabDestination.Settings && state.showSettingsCacheManager) {
            closeSettingsCacheManager()
            return true
        }
        if (active.destination is TabDestination.Library &&
            (active.destination.section == LibrarySection.IMAGES || active.destination.section == LibrarySection.MOVIES) &&
            lib.activeAlbumIdFor(active.destination.section) != null
        ) {
            closePhotoAlbum(active.destination.section)
            return true
        }
        val dir = active.destination as? TabDestination.Directory
        if (dir != null && dir.breadcrumbs.size > 1) {
            navigateToBreadcrumb(dir.breadcrumbs.lastIndex - 1)
            return true
        }
        if (active.id != "tab_home") {
            selectTab("tab_home")
            return true
        }
        return false
    }

    fun ensureDirectoryLoadedForTab(tabId: String) {
        val state = _uiState.value
        val tab = state.tabs.firstOrNull { it.id == tabId } ?: return
        val dir = tab.destination as? TabDestination.Directory ?: return
        if (state.directoryStatesByTabId[tabId] is DirectoryLoadState.Loaded) return
        if (backgroundTabLoadJobs[tabId]?.isActive == true) return
        val layout = resolveLayout(dir, state.prefs, state.folderLayouts)
        backgroundTabLoadJobs[tabId] = viewModelScope.launch {
            _uiState.update { current ->
                if (current.directoryStatesByTabId[tabId] is DirectoryLoadState.Loaded) {
                    current
                } else {
                    current.copy(
                        directoryStatesByTabId = current.directoryStatesByTabId + (tabId to DirectoryLoadState.Loading)
                    )
                }
            }
            val prefs = _uiState.value.prefs
            val result = repo.listDirectory(
                pathOrUri = dir.currentPathOrUri,
                isSaf = dir.isSaf,
                showHidden = prefs.showHiddenFiles,
                sortField = layout.sortField,
                ascending = layout.sortAscending,
                foldersFirst = prefs.foldersFirst,
                showFolderCounts = prefs.showFolderCounts,
                customOrderKeys = layout.customOrderKeys
            )
            _uiState.update { current ->
                val stillExists = current.tabs.any { it.id == tabId }
                if (!stillExists) return@update current
                val updatedMap = current.directoryStatesByTabId + (tabId to result)
                if (current.activeTabId == tabId) {
                    current.copy(directoryState = result, directoryStatesByTabId = updatedMap)
                } else {
                    current.copy(directoryStatesByTabId = updatedMap)
                }
            }
        }
    }

    private fun reloadActiveDirectoryIfNeeded() {
        val state = _uiState.value
        val active = state.activeTab ?: return
        val dir = active.destination as? TabDestination.Directory ?: return
        val layout = resolveLayout(dir, state.prefs, state.folderLayouts)
        val tabId = active.id
        val hasCached = state.directoryStatesByTabId[tabId] is DirectoryLoadState.Loaded
        directoryLoadJob?.cancel()
        folderCountEnrichmentJob?.cancel()
        sortJob?.cancel()
        directoryLoadJob = viewModelScope.launch {
            if (!hasCached) {
                _uiState.update {
                    it.copy(
                        directoryState = DirectoryLoadState.Loading,
                        directoryStatesByTabId = it.directoryStatesByTabId + (tabId to DirectoryLoadState.Loading)
                    )
                }
            }
            val prefs = _uiState.value.prefs
            val result = repo.listDirectory(
                pathOrUri = dir.currentPathOrUri,
                isSaf = dir.isSaf,
                showHidden = prefs.showHiddenFiles,
                sortField = layout.sortField,
                ascending = layout.sortAscending,
                foldersFirst = prefs.foldersFirst,
                showFolderCounts = prefs.showFolderCounts,
                customOrderKeys = layout.customOrderKeys
            )
            _uiState.update { current ->
                val updatedMap = current.directoryStatesByTabId + (tabId to result)
                if (current.activeTabId == tabId) {
                    current.copy(directoryState = result, directoryStatesByTabId = updatedMap)
                } else {
                    current.copy(directoryStatesByTabId = updatedMap)
                }
            }

            if (result is DirectoryLoadState.Loaded && prefs.showFolderCounts && !dir.isSaf) {
                val pendingFolders = result.items.filter { it.isDirectory && !it.isSafUri && it.childCount == null }
                if (pendingFolders.isNotEmpty()) {
                    val targetPath = dir.currentPathOrUri
                    folderCountEnrichmentJob = viewModelScope.launch {
                        val counts = repo.countSubfolderChildren(pendingFolders)
                        if (counts.isEmpty()) return@launch
                        _uiState.update { current ->
                            val currentDir = current.activeTab?.destination as? TabDestination.Directory
                                ?: return@update current
                            if (currentDir.currentPathOrUri != targetPath) return@update current
                            val loaded = current.directoryState as? DirectoryLoadState.Loaded ?: return@update current
                            val enriched = loaded.items.map { item ->
                                val c = counts[item.id]
                                if (c != null && item.childCount != c) item.copy(childCount = c) else item
                            }
                            val nextLoaded = loaded.copy(items = enriched)
                            current.copy(
                                directoryState = nextLoaded,
                                directoryStatesByTabId = current.directoryStatesByTabId + (tabId to nextLoaded)
                            )
                        }
                    }
                }
            }
        }
    }

    fun getLibrarySectionItems(section: LibrarySection): List<FileItem> {
        val state = _uiState.value
        val layout = resolveLayout(TabDestination.Library(section), state.prefs, state.folderLayouts)
        val filtered = if (section == LibrarySection.DOCUMENTS) {
            mergeUniqueDocuments(
                primary = state.documentFiles,
                secondary = state.allIndexedFiles.filter { it.isDocument }
            )
        } else {
            LibraryDomainUtils.filterFilesForSection(section, state.allIndexedFiles)
        }
        return repo.sortItems(
            items = filtered,
            sortField = layout.sortField,
            ascending = layout.sortAscending,
            foldersFirst = false,
            customOrderKeys = layout.customOrderKeys
        )
    }

    fun getCategoryFiles(category: ExplorerCategory): List<FileItem> {
        val state = _uiState.value
        val layout = resolveLayout(TabDestination.Category(category), state.prefs, state.folderLayouts)
        val filtered = when (category) {
            ExplorerCategory.ARCHIVES -> mergeUniqueArchives(
                primary = state.compressedFiles,
                secondary = state.allIndexedFiles.filter { it.isArchive }
            )
            ExplorerCategory.DOCUMENTS -> mergeUniqueDocuments(
                primary = state.documentFiles,
                secondary = state.allIndexedFiles.filter { it.isDocument }
            )
            ExplorerCategory.MUSIC -> state.allIndexedFiles.filter { it.isAudio }
            else -> state.allIndexedFiles.filter { repo.categorizeFile(it) == category }
        }
        return repo.sortItems(
            items = filtered,
            sortField = layout.sortField,
            ascending = layout.sortAscending,
            foldersFirst = state.prefs.foldersFirst,
            customOrderKeys = layout.customOrderKeys
        )
    }

    fun getSearchResults(query: String): List<FileItem> {
        val state = _uiState.value
        val layout = resolveLayout(TabDestination.Search(query), state.prefs, state.folderLayouts)
        val q = query.trim().lowercase()
        val source = if (q.isEmpty()) state.recentFiles else state.allIndexedFiles.filter {
            it.name.lowercase().contains(q) || it.extension.lowercase().contains(q)
        }
        return repo.sortItems(
            items = source,
            sortField = layout.sortField,
            ascending = layout.sortAscending,
            foldersFirst = state.prefs.foldersFirst,
            customOrderKeys = layout.customOrderKeys
        )
    }

    fun getTagFiles(tagId: String): List<FileItem> {
        val state = _uiState.value
        val layout = resolveLayout(TabDestination.Tag(tagId), state.prefs, state.folderLayouts)
        val indexedByPath = state.allIndexedFiles.associateBy { normalizeTagPath(it.pathOrUri, it.isSafUri) }
        val raw = state.taggedFilesByTagId[tagId].orEmpty().mapNotNull { cached ->
            if (!cached.isSafUri) {
                val diskFile = File(cached.pathOrUri)
                if (!diskFile.exists()) return@mapNotNull null
                indexedByPath[normalizeTagPath(cached.pathOrUri, false)] ?: cached.copy(
                    sizeBytes = if (diskFile.isDirectory) 0L else diskFile.length(),
                    lastModifiedMillis = diskFile.lastModified()
                )
            } else {
                indexedByPath[normalizeTagPath(cached.pathOrUri, true)] ?: cached
            }
        }
        return repo.sortItems(
            items = raw,
            sortField = layout.sortField,
            ascending = layout.sortAscending,
            foldersFirst = state.prefs.foldersFirst,
            customOrderKeys = layout.customOrderKeys
        )
    }

    fun openTagTab(tagId: String, forceNewTab: Boolean = false) =
        tagsCoordinator.openTagTab(tagId, forceNewTab)

    fun openTagPicker(items: List<FileItem>) = tagsCoordinator.openTagPicker(items)

    fun dismissTagPicker() = tagsCoordinator.dismissTagPicker()

    fun toggleTagOnTargets(tagId: String, items: List<FileItem>) =
        tagsCoordinator.toggleTagOnTargets(tagId, items)

    fun createCustomTag(name: String, colorHex: Long, applyToTargets: List<FileItem> = emptyList()) =
        tagsCoordinator.createCustomTag(name, colorHex, applyToTargets)

    fun deleteCustomTag(tagId: String) = tagsCoordinator.deleteCustomTag(tagId)

    // Selection & Clipboard
    fun toggleSelection(id: String) {
        _uiState.update { state ->
            val next = state.selectedIds.toMutableSet()
            if (!next.add(id)) next.remove(id)
            state.copy(selectedIds = next)
        }
    }

    fun selectAllVisible(visibleItems: List<FileItem>) {
        _uiState.update { it.copy(selectedIds = visibleItems.map { item -> item.id }.toSet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun stageClipboard(items: List<FileItem>, op: ClipboardOp) {
        if (items.isEmpty()) return
        val verb = if (op == ClipboardOp.COPY) "copied" else "ready to move"
        _uiState.update {
            it.copy(
                clipboard = ClipboardState(items, op),
                selectedIds = emptySet(),
                snackbarMessage = "${items.size} item(s) $verb. Open a destination folder and tap Paste."
            )
        }
    }

    fun clearClipboard() = fileOpsCoordinator.clearClipboard()

    fun pasteClipboardToCurrentDirectory() = fileOpsCoordinator.pasteClipboardToCurrentDirectory()

    fun minimizeActiveTransfer() = fileOpsCoordinator.minimizeActiveTransfer()

    fun expandActiveTransfer() = fileOpsCoordinator.expandActiveTransfer()

    fun cancelActiveTransfer() = fileOpsCoordinator.cancelActiveTransfer()

    fun dismissActiveTransfer() = fileOpsCoordinator.dismissActiveTransfer()

    fun moveItemsToFolder(
        items: List<FileItem>,
        targetPathOrUri: String,
        isTargetSaf: Boolean,
        destinationLabel: String
    ) = fileOpsCoordinator.moveItemsToFolder(items, targetPathOrUri, isTargetSaf, destinationLabel)

    // File CRUD (delegated to FileOperationsCoordinator)
    fun createFolder(name: String) = fileOpsCoordinator.createFolder(name)

    fun createNoteFile(name: String, content: String) = fileOpsCoordinator.createNoteFile(name, content)

    fun startInlineRename(item: FileItem) = fileOpsCoordinator.startInlineRename(item)

    fun cancelInlineRename() = fileOpsCoordinator.cancelInlineRename()

    fun commitInlineRename(item: FileItem, newName: String) =
        fileOpsCoordinator.commitInlineRename(item, newName)

    fun renameFile(item: FileItem, newName: String) = fileOpsCoordinator.renameFile(item, newName)

    fun batchRenameFiles(renames: List<Pair<FileItem, String>>) =
        fileOpsCoordinator.batchRenameFiles(renames)

    fun deleteFiles(items: List<FileItem>) = fileOpsCoordinator.deleteFiles(items)

    fun moveFilesToTrash(items: List<FileItem>) = fileOpsCoordinator.moveFilesToTrash(items)

    fun restoreTrashEntry(entry: TrashEntry) = fileOpsCoordinator.restoreTrashEntry(entry)

    fun restoreTrashEntries(entries: List<TrashEntry>) = fileOpsCoordinator.restoreTrashEntries(entries)

    fun deleteTrashEntryForever(entry: TrashEntry) = fileOpsCoordinator.deleteTrashEntryForever(entry)

    fun deleteTrashEntriesForever(entries: List<TrashEntry>) = fileOpsCoordinator.deleteTrashEntriesForever(entries)

    fun emptyTrash() = fileOpsCoordinator.emptyTrash()

    fun getTrashRootPath(): String = repo.getTrashRootPath()

    fun buildSafInitialUriForActiveDirectory(): Uri? {
        val dir = _uiState.value.activeTab?.destination as? TabDestination.Directory ?: return null
        return if (dir.isSaf) {
            Uri.parse(dir.currentPathOrUri)
        } else {
            repo.buildSafInitialUri(dir.currentPathOrUri)
        }
    }

    fun onSafTreePicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val contentResolver = getApplication<Application>().contentResolver
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
                .onFailure {
                    runCatching {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            val label = repo.resolveSafTreeLabel(uri)
            prefsRepo.addMountedSaf(uri.toString(), label)
            val activeDir = _uiState.value.activeTab?.destination as? TabDestination.Directory
            if (activeDir != null && !activeDir.isSaf) {
                val covered = repo.resolveCoveringSafUri(activeDir.currentPathOrUri)
                if (covered != null) {
                    reloadActiveDirectoryIfNeeded()
                } else {
                    val mountedDrive = DriveInfo(
                        id = "saf_${uri.toString().hashCode()}",
                        name = label,
                        subtitle = "SAF Mounted Drive",
                        pathOrUri = uri.toString(),
                        type = DriveType.SAF_TREE,
                        totalBytes = 0L,
                        freeBytes = 0L,
                        isSaf = true
                    )
                    openDriveInTab(mountedDrive, forceNewTab = false)
                }
            }
            showMessage("Mounted '$label'")
        }
    }

    fun unmountSafDrive(drive: DriveInfo) {
        if (!drive.isSaf) return
        viewModelScope.launch {
            prefsRepo.removeMountedSaf(drive.pathOrUri)
            // Close any tabs pointing to this drive
            _uiState.update { state ->
                val filteredTabs = state.tabs.filterNot { tab ->
                    (tab.destination as? TabDestination.Directory)?.rootId == drive.id
                }
                val activeId = if (filteredTabs.any { it.id == state.activeTabId }) state.activeTabId else "tab_home"
                state.copy(tabs = filteredTabs, activeTabId = activeId).withResolvedLayout()
            }
            showMessage("Unmounted '${drive.name}'")
        }
    }

    fun importPickedUris(uris: List<Uri>) = fileOpsCoordinator.importPickedUris(uris)

    // Preview & Inspector
    private fun openBuiltinMedia(item: FileItem, contextQueue: List<FileItem>) {
        val mediaItems = contextQueue.filter { it.isImage || it.isVideo }
        val queue = when {
            mediaItems.isEmpty() -> listOf(item)
            mediaItems.any { it.id == item.id } -> mediaItems
            else -> listOf(item) + mediaItems
        }
        _uiState.update {
            it.copy(
                previewFile = item,
                previewQueue = queue,
                previewText = null
            )
        }
    }

    private fun openBuiltinTextPreview(item: FileItem) {
        viewModelScope.launch {
            val text = repo.readTextPreview(item).getOrDefault("Unable to read text content.")
            _uiState.update {
                it.copy(
                    previewFile = item,
                    previewQueue = emptyList(),
                    previewText = text
                )
            }
        }
    }

    private fun openBuiltinViewer(item: FileItem, contextQueue: List<FileItem>) {
        if (item.isImage || item.isVideo) {
            openBuiltinMedia(item, contextQueue)
        } else {
            openBuiltinTextPreview(item)
        }
    }

    fun openFile(
        item: FileItem,
        contextQueue: List<FileItem> = emptyList(),
        onLaunchExternal: (Intent) -> Unit
    ) {
        if (item.isDirectory) {
            navigateIntoFolder(item)
            return
        }
        if (ArchiveToolsPlugin.canHandle(item)) {
            openArchivePreview(item)
            return
        }
        if (item.isApk) {
            if (!repo.canRequestPackageInstalls()) {
                pendingApkInstallItem = item
                showMessage("Enable 'Allow from this source' to install ${item.name}")
                onLaunchExternal(repo.buildUnknownAppSourcesIntent())
                return
            }
            launchApkInstall(item, onLaunchExternal)
            return
        }
        // Respect explicit Always default (either Built-in Viewer or External App)
        val openKey = defaultOpenAppKey(item)
        val savedDefault = _uiState.value.prefs.parsedDefaultOpenApps()[openKey]
        if (savedDefault != null) {
            if (savedDefault.isBuiltinApp) {
                openBuiltinViewer(item, contextQueue)
                return
            }
            if (repo.isAppInstalledAndHandles(item, savedDefault.packageName, savedDefault.activityName)) {
                launchExternalView(
                    item = item,
                    mimeOverride = null,
                    packageName = savedDefault.packageName,
                    activityName = savedDefault.activityName,
                    failureLabel = savedDefault.appLabel,
                    onLaunchExternal = onLaunchExternal
                )
                return
            } else {
                viewModelScope.launch { prefsRepo.clearDefaultOpenApp(openKey) }
            }
        }
        if (item.isImage || item.isVideo) {
            openBuiltinMedia(item, contextQueue)
            return
        }
        if (TextEditorPlugin.canHandle(item)) {
            if (_uiState.value.prefs.useBuiltinTextViewer) {
                openBuiltinTextPreview(item)
            } else {
                requestOpenWith(item, fromOpenAsAction = false)
            }
            return
        }
        requestOpenWith(item, fromOpenAsAction = false)
    }

    fun launchExternalView(
        item: FileItem,
        mimeOverride: String? = null,
        packageName: String? = null,
        activityName: String? = null,
        failureLabel: String? = null,
        onLaunchExternal: (Intent) -> Unit
    ) {
        if (!item.isSafUri) {
            val intent = repo.buildViewIntent(
                item = item,
                mimeOverride = mimeOverride,
                packageName = packageName,
                activityName = activityName
            )
            runCatching { onLaunchExternal(intent) }
                .onFailure {
                    showMessage(
                        if (!failureLabel.isNullOrBlank()) "Unable to open '${item.name}' with $failureLabel"
                        else "No app installed on this device to open '${item.name}'"
                    )
                }
            return
        }
        viewModelScope.launch {
            val resolvedUri = repo.prepareViewOrShareUri(item)
            val intent = repo.buildViewIntent(
                item = item,
                mimeOverride = mimeOverride,
                packageName = packageName,
                activityName = activityName,
                resolvedUri = resolvedUri
            )
            runCatching { onLaunchExternal(intent) }
                .onFailure {
                    showMessage(
                        if (!failureLabel.isNullOrBlank()) "Unable to open '${item.name}' with $failureLabel"
                        else "No app installed on this device to open '${item.name}'"
                    )
                }
        }
    }

    private fun launchApkInstall(
        item: FileItem,
        onLaunchExternal: (Intent) -> Unit,
        targetPackageName: String? = null
    ) {
        if (!item.isSafUri) {
            val intent = repo.buildApkInstallIntent(item = item, targetPackageName = targetPackageName)
            runCatching { onLaunchExternal(intent) }
                .onFailure { showMessage("No package installer app found to install '${item.name}'") }
            return
        }
        viewModelScope.launch {
            runCatching { repo.prepareApkInstallUri(item) }
                .onSuccess { uri ->
                    val intent = repo.buildApkInstallIntent(
                        item = item,
                        preparedUri = uri,
                        targetPackageName = targetPackageName
                    )
                    runCatching { onLaunchExternal(intent) }
                        .onFailure { showMessage("No package installer app found to install '${item.name}'") }
                }
                .onFailure {
                    showMessage(it.localizedMessage ?: "Unable to prepare APK for installation")
                }
        }
    }

    fun openFile(item: FileItem, onLaunchExternal: (Intent) -> Unit) {
        openFile(item = item, contextQueue = emptyList(), onLaunchExternal = onLaunchExternal)
    }

    fun selectPreviewItem(item: FileItem) {
        _uiState.update { state ->
            if (state.previewFile?.id == item.id) state
            else state.copy(previewFile = item)
        }
    }

    fun requestOpenWith(
        item: FileItem,
        fromOpenAsAction: Boolean = false,
        mimeOverride: String? = null
    ) {
        if (item.isDirectory) return
        val externalApps = repo.queryCompatibleApps(item, mimeOverride = mimeOverride)
        val canOpenBuiltin = item.isTextEditable || item.isImage || item.isVideo
        val builtinOption = if (canOpenBuiltin && (mimeOverride == null || mimeOverride == "text/plain" || mimeOverride == "*/*")) {
            ResolvedAppOption(
                packageName = BUILTIN_VIEWER_PACKAGE,
                activityName = if (item.isImage || item.isVideo) BUILTIN_MEDIA_VIEWER_ACTIVITY else BUILTIN_DOC_VIEWER_ACTIVITY,
                label = when {
                    item.isHtml || item.isMarkdown -> "Arc Preview & Code Viewer"
                    item.isTextEditable -> "Arc Text & Code Viewer"
                    else -> "Arc Media Viewer"
                }
            )
        } else null
        val apps = listOfNotNull(builtinOption) + externalApps
        if (apps.isEmpty()) {
            val noAppsMessage = "No app installed on this device to open '${item.name}'"
            showMessage(noAppsMessage)
            val hasFallbackApps = mimeOverride == null && repo.hasAnyWildcardApp(item)
            if (!hasFallbackApps) {
                _uiState.update { it.copy(openWithRequest = null, selectedIds = emptySet()) }
                return
            }
        }
        val saved = _uiState.value.prefs.parsedDefaultOpenApps()[defaultOpenAppKey(item)]
        val savedKey = saved?.let { "${it.packageName}/${it.activityName}" }
        val preselected = apps.firstOrNull { it.componentKey == savedKey }?.componentKey
            ?: apps.firstOrNull()?.componentKey
        _uiState.update {
            it.copy(
                openWithRequest = OpenWithRequest(
                    item = item,
                    fromOpenAsAction = fromOpenAsAction,
                    apps = apps,
                    preselectedComponentKey = preselected,
                    mimeOverride = mimeOverride
                ),
                selectedIds = emptySet()
            )
        }
    }

    fun confirmOpenWithApp(
        request: OpenWithRequest,
        app: ResolvedAppOption,
        always: Boolean,
        onLaunchExternal: (Intent) -> Unit
    ) {
        _uiState.update { it.copy(openWithRequest = null) }
        val key = defaultOpenAppKey(request.item)
        if (always) {
            viewModelScope.launch {
                prefsRepo.setDefaultOpenApp(
                    key = key,
                    packageName = app.packageName,
                    activityName = app.activityName,
                    appLabel = app.label
                )
            }
        } else if (request.fromOpenAsAction) {
            viewModelScope.launch {
                prefsRepo.clearDefaultOpenApp(key)
            }
        }
        if (app.isBuiltinApp) {
            openBuiltinViewer(request.item, emptyList())
            return
        }
        if (_uiState.value.previewFile?.id == request.item.id) {
            closePreview()
        }
        if (request.item.isApk) {
            if (!repo.canRequestPackageInstalls()) {
                pendingApkInstallItem = request.item
                showMessage("Enable 'Allow from this source' to install ${request.item.name}")
                onLaunchExternal(repo.buildUnknownAppSourcesIntent())
                return
            }
            launchApkInstall(
                item = request.item,
                onLaunchExternal = onLaunchExternal,
                targetPackageName = app.packageName
            )
            return
        }
        launchExternalView(
            item = request.item,
            mimeOverride = request.mimeOverride,
            packageName = app.packageName,
            activityName = app.activityName,
            failureLabel = app.label,
            onLaunchExternal = onLaunchExternal
        )
    }

    fun dismissOpenWith() {
        _uiState.update { it.copy(openWithRequest = null) }
    }

    fun openArchivePreview(item: FileItem, password: String? = null) =
        archiveCoordinator.openArchivePreview(item, password)

    fun closeArchivePreview() = archiveCoordinator.closeArchivePreview()

    fun testArchive(item: FileItem, password: String? = null) =
        archiveCoordinator.testArchive(item, password)

    fun previewArchiveEntry(
        item: FileItem,
        entryPath: String,
        password: String? = null,
        onLaunchExternal: (Intent) -> Unit
    ) {
        archiveCoordinator.previewArchiveEntry(item, entryPath, password) { tempItem ->
            openFile(tempItem, contextQueue = listOf(tempItem), onLaunchExternal = onLaunchExternal)
        }
    }

    fun resolveDefaultExtractDestination(item: FileItem): Pair<String, Boolean> =
        archiveCoordinator.resolveArchiveTargetDirectory(item)

    suspend fun listExtractDestinationSubfolders(pathOrUri: String, isSaf: Boolean): List<FileItem> =
        repo.listDestinationSubfolders(pathOrUri, isSaf)

    suspend fun createExtractDestinationFolder(parentPath: String, folderName: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(parentPath, folderName.trim())
                if (!target.exists() && !target.mkdirs()) {
                    error("Unable to create folder '${folderName.trim()}'")
                }
                target
            }
        }

    fun openLocalDirectoryPath(dir: File) {
        val internal = _uiState.value.drives.firstOrNull { it.type == DriveType.INTERNAL }
        val internalRootPath = internal?.pathOrUri
            ?: runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrNull()
            ?: dir.parentFile?.absolutePath
            ?: dir.absolutePath
        val internalTitle = internal?.name ?: "Internal Storage"
        val crumbs = if (dir.absolutePath.startsWith(internalRootPath) && dir.absolutePath != internalRootPath) {
            val rel = dir.absolutePath.removePrefix(internalRootPath).trimStart(File.separatorChar)
            val acc = mutableListOf(BreadcrumbNode(internalTitle, internalRootPath, false))
            var cur = File(internalRootPath)
            rel.split(File.separatorChar).filter { it.isNotBlank() }.forEach { part ->
                cur = File(cur, part)
                acc += BreadcrumbNode(part, cur.absolutePath, false)
            }
            acc
        } else {
            listOf(BreadcrumbNode(dir.name.ifBlank { "Folder" }, dir.absolutePath, false))
        }
        openOrSwitchDestination(
            title = dir.name.ifBlank { "Folder" },
            destination = TabDestination.Directory(
                title = dir.name.ifBlank { "Folder" },
                rootId = "drive_internal",
                currentPathOrUri = dir.absolutePath,
                isSaf = false,
                breadcrumbs = crumbs
            ),
            matchPredicate = { tab ->
                (tab.destination as? TabDestination.Directory)?.currentPathOrUri == dir.absolutePath
            },
            forceNewTab = false
        )
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
        openDestinationWhenDone: Boolean = false
    ) = archiveCoordinator.extractArchive(
        item = item,
        extractToSubfolder = extractToSubfolder,
        password = password,
        targetParentPathOrUriOverride = targetParentPathOrUriOverride,
        isTargetSafOverride = isTargetSafOverride,
        customSubfolderName = customSubfolderName,
        overwriteMode = overwriteMode,
        selectedEntryPaths = selectedEntryPaths,
        openDestinationWhenDone = openDestinationWhenDone,
        onOpenLocalDestination = ::openLocalDirectoryPath
    )

    fun openCompressDialog(items: List<FileItem>) = archiveCoordinator.openCompressDialog(items)

    fun dismissCompressDialog() = archiveCoordinator.dismissCompressDialog()

    fun createArchive(
        items: List<FileItem>,
        archiveName: String,
        format: ArchiveFormat,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.NORMAL,
        password: String? = null
    ) = archiveCoordinator.createArchive(items, archiveName, format, compressionLevel, password)

    fun saveEditedPreviewText(newText: String) {
        val item = _uiState.value.previewFile ?: return
        viewModelScope.launch {
            repo.saveTextFile(item, newText)
                .onSuccess {
                    _uiState.update { it.copy(previewText = newText, snackbarMessage = "Saved '${item.name}'") }
                    refreshHomeAndActiveTab()
                }
                .onFailure { showMessage(it.localizedMessage ?: "Save failed") }
        }
    }

    fun closePreview() {
        _uiState.update { it.copy(previewFile = null, previewQueue = emptyList(), previewText = null) }
    }

    fun setInfoFile(item: FileItem?) {
        _uiState.update { it.copy(infoFile = item) }
    }

    fun buildShareIntent(items: List<FileItem>): Intent = repo.buildShareIntent(items)
    fun buildViewIntent(item: FileItem, mimeOverride: String? = null): Intent =
        repo.buildViewIntent(item, mimeOverride)

    // Per-folder Layout & Global Settings (delegated to SettingsAndLayoutCoordinator)
    fun setViewMode(mode: ViewMode) = settingsCoordinator.setViewMode(mode)

    fun setGridColumns(columns: Int, switchToGrid: Boolean = false) =
        settingsCoordinator.setGridColumns(columns, switchToGrid)

    fun setOriginalMediaRatio(enabled: Boolean) = settingsCoordinator.setOriginalMediaRatio(enabled)

    fun setSort(field: SortField) = settingsCoordinator.setSort(field)

    fun setSortOrder(field: SortField, ascending: Boolean) =
        settingsCoordinator.setSortOrder(field, ascending)

    fun toggleSortOrder() = settingsCoordinator.toggleSortOrder()

    fun setGroupBy(groupBy: GroupByField) = settingsCoordinator.setGroupBy(groupBy)

    fun commitCustomReorder(orderedIds: List<String>) =
        settingsCoordinator.commitCustomReorder(orderedIds)

    fun setGlobalViewMode(mode: ViewMode) = settingsCoordinator.setGlobalViewMode(mode)

    fun setGlobalGridColumns(columns: Int) = settingsCoordinator.setGlobalGridColumns(columns)

    fun setGlobalOriginalMediaRatio(enabled: Boolean) =
        settingsCoordinator.setGlobalOriginalMediaRatio(enabled)

    fun setGlobalSortField(field: SortField) = settingsCoordinator.setGlobalSortField(field)

    fun setGlobalSortAscending(ascending: Boolean) =
        settingsCoordinator.setGlobalSortAscending(ascending)

    fun setGlobalGroupBy(groupBy: GroupByField) = settingsCoordinator.setGlobalGroupBy(groupBy)

    fun setFoldersFirst(enabled: Boolean) = settingsCoordinator.setFoldersFirst(enabled)

    fun setShowHiddenFiles(show: Boolean) = settingsCoordinator.setShowHiddenFiles(show)

    fun setShowFileExtensions(show: Boolean) = settingsCoordinator.setShowFileExtensions(show)

    fun setShowFolderCounts(show: Boolean) = settingsCoordinator.setShowFolderCounts(show)

    fun setCompactListMode(compact: Boolean) = settingsCoordinator.setCompactListMode(compact)

    fun setRecycleBinEnabled(enabled: Boolean) = settingsCoordinator.setRecycleBinEnabled(enabled)

    fun setConfirmBeforeDelete(confirm: Boolean) = settingsCoordinator.setConfirmBeforeDelete(confirm)

    fun setThemePreference(pref: ThemePreference) = settingsCoordinator.setThemePreference(pref)

    fun setMonochromeBinaryIcons(mono: Boolean) = settingsCoordinator.setMonochromeBinaryIcons(mono)

    fun setUseBuiltinTextViewer(enabled: Boolean) = settingsCoordinator.setUseBuiltinTextViewer(enabled)

    fun setDragReorderEnabled(enabled: Boolean) = settingsCoordinator.setDragReorderEnabled(enabled)

    fun clearDefaultOpenApp(key: String) = settingsCoordinator.clearDefaultOpenApp(key)

    fun clearAllDefaultOpenApps() = settingsCoordinator.clearAllDefaultOpenApps()

    fun resetSettingsToDefaults() = settingsCoordinator.resetSettingsToDefaults()

    @Suppress("unused")
    fun openWorkspaceTab() {
        val ws = _uiState.value.drives.firstOrNull { it.type == DriveType.WORKSPACE } ?: return
        openDriveInTab(ws)
    }

    fun toggleBookmark(item: FileItem) = tagsCoordinator.toggleBookmark(item)

    fun removeBookmark(pathOrUri: String) = tagsCoordinator.removeBookmark(pathOrUri)

    fun openBookmark(entry: BookmarkEntry, onLaunchExternal: (Intent) -> Unit) =
        tagsCoordinator.openBookmark(entry, onLaunchExternal)

    fun toggleShowHomeNewFiles() = settingsCoordinator.toggleShowHomeNewFiles()

    fun toggleShowHomeBookmarks() = settingsCoordinator.toggleShowHomeBookmarks()

    fun cycleThemePreference() = settingsCoordinator.cycleThemePreference()

    // Library & Gallery / Album Management Actions (delegated to GalleryLibraryCoordinator)
    fun selectGalleryPanel(panel: GalleryPanel, section: LibrarySection = LibrarySection.IMAGES) =
        galleryCoordinator.selectGalleryPanel(panel, section)

    fun openPhotoAlbum(albumId: String, section: LibrarySection = LibrarySection.IMAGES) =
        galleryCoordinator.openPhotoAlbum(albumId, section)

    fun closePhotoAlbum(section: LibrarySection = LibrarySection.IMAGES) =
        galleryCoordinator.closePhotoAlbum(section)

    fun openCreateAlbumDialog(
        initialItems: List<FileItem> = emptyList(),
        section: LibrarySection = LibrarySection.IMAGES
    ) = galleryCoordinator.openCreateAlbumDialog(initialItems, section)

    fun dismissCreateAlbumDialog() = galleryCoordinator.dismissCreateAlbumDialog()

    fun createPhotoAlbum(
        name: String,
        initialItems: List<FileItem> = emptyList(),
        section: LibrarySection = LibrarySection.IMAGES
    ) = galleryCoordinator.createPhotoAlbum(name, initialItems, section)

    fun openRenameAlbumDialog(album: PhotoAlbum) = galleryCoordinator.openRenameAlbumDialog(album)

    fun dismissRenameAlbumDialog() = galleryCoordinator.dismissRenameAlbumDialog()

    fun renamePhotoAlbum(albumId: String, newName: String) =
        galleryCoordinator.renamePhotoAlbum(albumId, newName)

    fun deletePhotoAlbum(album: PhotoAlbum) = galleryCoordinator.deletePhotoAlbum(album)

    fun setPhotoAlbumCover(albumId: String, image: FileItem) =
        galleryCoordinator.setPhotoAlbumCover(albumId, image)

    fun openAddToAlbumDialog(items: List<FileItem>, section: LibrarySection = LibrarySection.IMAGES) =
        galleryCoordinator.openAddToAlbumDialog(items, section)

    fun dismissAddToAlbumDialog() = galleryCoordinator.dismissAddToAlbumDialog()

    fun addImagesToAlbums(albumIds: Set<String>, images: List<FileItem>) =
        galleryCoordinator.addImagesToAlbums(albumIds, images)

    fun openPickImagesForAlbum(albumId: String, section: LibrarySection = LibrarySection.IMAGES) =
        galleryCoordinator.openPickImagesForAlbum(albumId, section)

    fun dismissPickImagesForAlbum() = galleryCoordinator.dismissPickImagesForAlbum()

    fun addImagesToSingleAlbum(albumId: String, images: List<FileItem>) =
        galleryCoordinator.addImagesToSingleAlbum(albumId, images)

    fun removeImagesFromAlbum(albumId: String, images: List<FileItem>) =
        galleryCoordinator.removeImagesFromAlbum(albumId, images)

    fun openDeviceAlbumFolder(album: PhotoAlbum) = galleryCoordinator.openDeviceAlbumFolder(album)

    fun setLibrarySubFilter(section: LibrarySection, filterId: String) =
        galleryCoordinator.setLibrarySubFilter(section, filterId)

    // Storage Cache & Plugin Data Manager Actions (delegated to StorageCacheCoordinator)
    fun refreshCacheStats() = storageCoordinator.refreshCacheStats()

    fun clearCacheBucket(bucket: CacheBucket) = storageCoordinator.clearCacheBucket(bucket)

    fun clearManagedStorageEntry(id: ManagedStorageId) =
        storageCoordinator.clearManagedStorageEntry(id)

    fun clearPluginCache(pluginId: String) = storageCoordinator.clearPluginCache(pluginId)

    fun clearPluginData(pluginId: String) = storageCoordinator.clearPluginData(pluginId)

    fun clearAllThumbnailCaches() = storageCoordinator.clearAllThumbnailCaches()

    fun setShowNewTabSheet(show: Boolean) {
        _uiState.update { it.copy(showNewTabSheet = show) }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
