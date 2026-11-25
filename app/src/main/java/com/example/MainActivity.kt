package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.FileItem
import com.example.data.TabDestination
import com.example.ui.ExplorerViewModel
import com.example.ui.components.ArcSidebarDrawer
import com.example.ui.components.TopTabIsland
import com.example.ui.components.common.FileTransferProgressHost
import com.example.ui.components.common.TagsPickerSheet
import com.example.ui.components.dialogs.ArchivePreviewDialog
import com.example.ui.components.dialogs.BatchRenameDialog
import com.example.ui.components.dialogs.CompressArchiveDialog
import com.example.ui.components.dialogs.CreateFolderDialog
import com.example.plugins.ArcPluginViewerHost
import com.example.ui.components.dialogs.DeleteConfirmDialog
import com.example.ui.components.dialogs.FilePropertiesSheet
import com.example.ui.components.dialogs.NewTabSheet
import com.example.ui.components.dialogs.SelectOpenAppDialog
import com.example.ui.registry.DestinationCallbacks
import com.example.ui.registry.TabDestinationRegistry
import com.example.ui.screens.library.AddToAlbumDialog
import com.example.ui.screens.library.CreateOrRenameAlbumDialog
import com.example.ui.screens.library.LibraryDomainUtils
import com.example.ui.screens.library.PickImagesForAlbumDialog
import com.example.ui.theme.ArcDimens
import com.example.ui.theme.ArcFilesTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            ArcFilesRoot()
        }
    }
}

@Composable
fun ArcFilesRoot(
    viewModel: ExplorerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showStandaloneNoteEditor by remember { mutableStateOf(false) }
    var batchRenameTargets by remember { mutableStateOf<List<FileItem>?>(null) }
    var deleteTargets by remember { mutableStateOf<List<FileItem>?>(null) }

    val safTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> viewModel.onSafTreePicked(uri) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris -> if (uris.isNotEmpty()) viewModel.importPickedUris(uris) }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.importPickedUris(uris) }

    val launchExternalWithTitle: (Intent, String?) -> Unit = { intent, title ->
        val isApkOrSettings = intent.type == "application/vnd.android.package-archive" ||
            intent.action == Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES ||
            intent.action == Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION ||
            intent.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val explicitPkg = intent.component?.packageName ?: intent.`package`
        val directResult = runCatching {
            when {
                isApkOrSettings || intent.component != null -> context.startActivity(intent)
                else -> context.startActivity(Intent.createChooser(intent, title))
            }
        }
        if (directResult.isFailure) {
            // Tier 2: if exact Activity component failed (e.g. alias or non-exported launcher), try package-wide intent first
            val pkgFallback = if (intent.component != null && !explicitPkg.isNullOrBlank()) {
                runCatching {
                    val pkgIntent = Intent(intent).apply {
                        component = null
                        setPackage(explicitPkg)
                    }
                    context.startActivity(pkgIntent)
                }
            } else null

            if (pkgFallback == null || pkgFallback.isFailure) {
                // Tier 3: try system chooser without explicit package/component
                val fallbackGeneric = runCatching {
                    val clean = Intent(intent).apply {
                        component = null
                        setPackage(null)
                    }
                    context.startActivity(
                        if (isApkOrSettings) clean else Intent.createChooser(clean, title)
                    )
                }
                if (fallbackGeneric.isFailure) {
                    // Tier 4: try wildcard */* chooser for uncommon file extensions
                    val anyTypeFallback = runCatching {
                        val uri = intent.data
                        if (uri != null && intent.action == Intent.ACTION_VIEW && !isApkOrSettings) {
                            val wildcardIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "*/*")
                                clipData = intent.clipData
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(wildcardIntent, title))
                        } else {
                            throw fallbackGeneric.exceptionOrNull() ?: IllegalStateException("No handler")
                        }
                    }
                    if (anyTypeFallback.isFailure) {
                        val targetLabel = title?.takeIf { it.isNotBlank() } ?: "this file"
                        viewModel.showMessage("No app installed on this device to open $targetLabel")
                    }
                }
            }
        }
    }
    val launchExternalIntent: (Intent) -> Unit = { intent ->
        launchExternalWithTitle(intent, null)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed(launchExternalIntent)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeSnackbar()
    }

    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    val requestAllFilesAccess: () -> Unit = {
        runCatching {
            context.startActivity(viewModel.buildAllFilesAccessIntent())
        }.onFailure {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }.onFailure {
                    viewModel.showMessage("Unable to open All Files Access settings")
                }
            }
        }
    }

    val destinationCallbacks = remember {
        DestinationCallbacks(
            onRequestAllFilesAccess = requestAllFilesAccess,
            onMountFolder = { safTreeLauncher.launch(viewModel.buildSafInitialUriForActiveDirectory()) },
            onImportMedia = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onImportDocs = { docPickerLauncher.launch(arrayOf("*/*")) },
            onNewFolder = { showCreateFolderDialog = true },
            onNewNote = { showStandaloneNoteEditor = true },
            onBatchRenameRequest = { batchRenameTargets = it },
            onDeleteRequest = { targets ->
                val prefs = viewModel.uiState.value.prefs
                if (prefs.confirmBeforeDelete) {
                    deleteTargets = targets
                } else if (prefs.recycleBinEnabled) {
                    viewModel.moveFilesToTrash(targets)
                } else {
                    viewModel.deleteFiles(targets)
                }
            },
            onLaunchExternal = launchExternalIntent
        )
    }

    val bookmarks = remember(state.prefs.bookmarkEntries) { state.prefs.parsedBookmarks() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val adaptiveDimens = remember(maxWidth) { ArcDimens.forWidth(maxWidth) }
        ArcFilesTheme(themePreference = state.prefs.themePreference, dimens = adaptiveDimens) {
            Box(modifier = Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    val tagCounts = remember(state.taggedFilesByTagId) {
                        state.taggedFilesByTagId.mapValues { it.value.size }
                    }
                    ArcSidebarDrawer(
                        drives = state.drives,
                        bookmarks = bookmarks,
                        tags = state.allTags,
                        taggedCountsByTagId = tagCounts,
                        indexedFileCount = state.allIndexedFiles.size,
                        trashCount = state.trashEntries.size,
                        hasAllFilesAccess = state.hasAllFilesAccess,
                        showHiddenFiles = state.prefs.showHiddenFiles,
                        themePreference = state.prefs.themePreference,
                        onCloseDrawer = { coroutineScope.launch { drawerState.close() } },
                        onOpenDrive = { drive -> viewModel.openDriveInTab(drive) },
                        onOpenPublicFolder = viewModel::openQuickPublicFolder,
                        onOpenBookmark = { bm -> viewModel.openBookmark(bm, launchExternalIntent) },
                        onOpenTag = viewModel::openTagTab,
                        onOpenCategory = viewModel::openLibraryCategory,
                        onOpenAnalyzer = viewModel::openAnalyzerTab,
                        onOpenTrash = viewModel::openTrashTab,
                        onMountFolder = destinationCallbacks.onMountFolder,
                        onImportMedia = destinationCallbacks.onImportMedia,
                        onNewNote = destinationCallbacks.onNewNote,
                        onToggleHiddenFiles = { viewModel.setShowHiddenFiles(!state.prefs.showHiddenFiles) },
                        onRequestAllFilesAccess = requestAllFilesAccess,
                        onCycleTheme = viewModel::cycleThemePreference,
                        onSelectTheme = viewModel::setThemePreference,
                        onOpenSettings = viewModel::openSettingsTab
                    )
                }
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.navigationBarsPadding()
                        )
                    },
                    topBar = {
                        val activeDest = state.activeTab?.destination
                        TopTabIsland(
                            tabs = state.tabs,
                            activeTabId = state.activeTabId,
                            canCreateInDirectory = activeDest is TabDestination.Directory,
                            viewMode = state.activeLayout.viewMode,
                            gridColumns = state.activeLayout.gridColumns,
                            originalMediaRatio = state.activeLayout.originalMediaRatio,
                            sortField = state.activeLayout.sortField,
                            sortAscending = state.activeLayout.sortAscending,
                            groupBy = state.activeLayout.groupBy,
                            onMenuClick = { coroutineScope.launch { drawerState.open() } },
                            onSelectTab = viewModel::selectTab,
                            onCloseTab = viewModel::closeTab,
                            onNewTabClick = { viewModel.setShowNewTabSheet(true) },
                            onNewFolderClick = destinationCallbacks.onNewFolder,
                            onNewNoteClick = destinationCallbacks.onNewNote,
                            onSearchClick = { viewModel.openSearchTab() },
                            onSettingsClick = { viewModel.openSettingsTab() },
                            onViewModeChange = viewModel::setViewMode,
                            onGridColumnsChange = { cols -> viewModel.setGridColumns(cols, switchToGrid = true) },
                            onToggleOriginalMediaRatio = { viewModel.setOriginalMediaRatio(!state.activeLayout.originalMediaRatio) },
                            onSortOrderChange = viewModel::setSortOrder,
                            onGroupByChange = viewModel::setGroupBy,
                            onRefreshClick = { viewModel.refreshHomeAndActiveTab() },
                            onAnalyzerClick = { viewModel.openAnalyzerTab() },
                            onTrashClick = { viewModel.openTrashTab() },
                            onHistoryClick = { viewModel.openSearchTab("") }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = innerPadding.calculateTopPadding())
                    ) {
                        val tabs = state.tabs
                        val realCount = tabs.size
                        val activeIndex = tabs.indexOfFirst { it.id == state.activeTabId }
                            .coerceIn(0, (realCount - 1).coerceAtLeast(0))
                        val isLooping = realCount > 1
                        val loopCycles = 400
                        val virtualPageCount = if (isLooping) realCount * loopCycles else 1
                        val halfVirtual = virtualPageCount / 2
                        val middleBase = if (isLooping) halfVirtual - (halfVirtual % realCount) else 0
                        val pagerState = rememberPagerState(
                            initialPage = if (isLooping) middleBase + activeIndex else 0,
                            pageCount = { virtualPageCount }
                        )
                        val currentTabs by rememberUpdatedState(tabs)
                        val currentActiveTabId by rememberUpdatedState(state.activeTabId)
                        var lastSyncedTabCount by remember { mutableIntStateOf(realCount) }

                        LaunchedEffect(activeIndex, realCount) {
                            if (tabs.isEmpty()) return@LaunchedEffect
                            if (!isLooping) {
                                lastSyncedTabCount = realCount
                                if (pagerState.currentPage != 0) {
                                    pagerState.scrollToPage(0)
                                }
                                return@LaunchedEffect
                            }
                            val countChanged = lastSyncedTabCount != realCount
                            lastSyncedTabCount = realCount
                            val currentPage = pagerState.currentPage
                            val currentReal = Math.floorMod(currentPage, realCount)
                            val nearBoundary = currentPage < realCount * 2 || currentPage >= virtualPageCount - realCount * 2
                            if (countChanged || nearBoundary) {
                                pagerState.scrollToPage(middleBase + activeIndex)
                            } else if (currentReal != activeIndex) {
                                var delta = activeIndex - currentReal
                                if (delta > realCount / 2) delta -= realCount
                                if (delta < -(realCount / 2)) delta += realCount
                                val targetVirtual = (currentPage + delta).coerceIn(0, virtualPageCount - 1)
                                pagerState.animateScrollToPage(targetVirtual)
                            }
                        }

                        LaunchedEffect(pagerState) {
                            snapshotFlow { pagerState.settledPage }
                                .distinctUntilChanged()
                                .collect { settled ->
                                    val list = currentTabs
                                    if (list.isEmpty()) return@collect
                                    val realIdx = if (list.size <= 1) 0 else Math.floorMod(settled, list.size)
                                    val targetTab = list.getOrNull(realIdx)
                                    if (targetTab != null && targetTab.id != currentActiveTabId) {
                                        viewModel.selectTab(targetTab.id)
                                    }
                                }
                        }

                        HorizontalPager(
                            state = pagerState,
                            key = { page ->
                                val list = currentTabs
                                if (list.isEmpty()) {
                                    "empty_$page"
                                } else {
                                    val realIdx = if (list.size <= 1) 0 else Math.floorMod(page, list.size)
                                    "${list[realIdx].id}_$page"
                                }
                            },
                            beyondViewportPageCount = 1,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("tab_horizontal_pager")
                        ) { page ->
                            val tab = if (tabs.isEmpty()) null else {
                                val realIdx = if (tabs.size <= 1) 0 else Math.floorMod(page, tabs.size)
                                tabs.getOrNull(realIdx)
                            }
                            if (tab != null) {
                                LaunchedEffect(tab.id) {
                                    if (tab.destination is TabDestination.Directory) {
                                        viewModel.ensureDirectoryLoadedForTab(tab.id)
                                    }
                                }
                                TabDestinationRegistry.RenderDestination(
                                    destination = tab.destination,
                                    state = state,
                                    viewModel = viewModel,
                                    callbacks = destinationCallbacks,
                                    tabId = tab.id,
                                    isActivePage = (page == pagerState.currentPage && tab.id == state.activeTabId)
                                )
                            }
                        }

                        if (!drawerState.isOpen) {
                            val openThresholdPx = with(LocalDensity.current) { 20.dp.toPx() }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .fillMaxHeight()
                                    .width(16.dp)
                                    .testTag("sidebar_edge_swipe_strip")
                                    .pointerInput(drawerState) {
                                        var totalDragX = 0f
                                        var triggered = false
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                totalDragX = 0f
                                                triggered = false
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                if (!triggered && dragAmount > 0f) {
                                                    totalDragX += dragAmount
                                                    change.consume()
                                                    if (totalDragX >= openThresholdPx) {
                                                        triggered = true
                                                        coroutineScope.launch { drawerState.open() }
                                                    }
                                                }
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }
            }

            FileTransferProgressHost(
                transfer = state.activeTransfer,
                onMinimize = viewModel::minimizeActiveTransfer,
                onExpand = viewModel::expandActiveTransfer,
                onCancel = viewModel::cancelActiveTransfer,
                onDismissError = viewModel::dismissActiveTransfer,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (state.showNewTabSheet) {
            NewTabSheet(
                drives = state.drives,
                monochromeBinaryIcons = state.prefs.monochromeBinaryIcons,
                onDismiss = { viewModel.setShowNewTabSheet(false) },
                onOpenDriveInNewTab = { drive -> viewModel.openDriveInTab(drive, forceNewTab = true) },
                onOpenCategory = { cat -> viewModel.openLibraryCategory(cat) },
                onMountFolder = { safTreeLauncher.launch(null) }
            )
        }

        val activeDirectoryDest = state.activeTab?.destination as? TabDestination.Directory
        ArcPluginViewerHost(
            previewFile = state.previewFile,
            previewQueue = state.previewQueue,
            previewText = state.previewText,
            isStandaloneNoteEditorOpen = showStandaloneNoteEditor,
            onDismissStandaloneNoteEditor = { showStandaloneNoteEditor = false },
            defaultSaveDirectory = activeDirectoryDest?.currentPathOrUri
                ?: state.drives.firstOrNull()?.pathOrUri.orEmpty(),
            defaultSaveIsSaf = activeDirectoryDest?.isSaf == true,
            onDismiss = viewModel::closePreview,
            onActiveItemChanged = viewModel::selectPreviewItem,
            onSaveText = viewModel::saveEditedPreviewText,
            onWorkspaceModified = viewModel::refreshHomeAndActiveTab,
            onRequestOpenWith = { active -> viewModel.requestOpenWith(active, fromOpenAsAction = true) },
            onLaunchExternalView = { active, onIntent -> viewModel.launchExternalView(active, onLaunchExternal = onIntent) },
            onShareItem = { active ->
                launchExternalWithTitle(
                    viewModel.buildShareIntent(listOf(active)),
                    "Share '${active.name}'"
                )
            },
            onInfo = { active -> viewModel.setInfoFile(active) },
            onLaunchExternalIntent = { intent, title -> launchExternalWithTitle(intent, title) }
        )

        state.infoFile?.let { file ->
            FilePropertiesSheet(
                item = file,
                onDismiss = { viewModel.setInfoFile(null) },
                onOpenExternal = {
                    viewModel.setInfoFile(null)
                    viewModel.requestOpenWith(file, fromOpenAsAction = true)
                },
                onShare = { launchExternalWithTitle(viewModel.buildShareIntent(listOf(file)), "Share '${file.name}'") }
            )
        }

        state.openWithRequest?.let { req ->
            SelectOpenAppDialog(
                request = req,
                onDismiss = viewModel::dismissOpenWith,
                onConfirmApp = { app, always ->
                    viewModel.confirmOpenWithApp(req, app, always, launchExternalIntent)
                },
                onTryAllApps = {
                    viewModel.requestOpenWith(
                        item = req.item,
                        fromOpenAsAction = req.fromOpenAsAction,
                        mimeOverride = "*/*"
                    )
                }
            )
        }

        state.archivePreviewFile?.let { archiveFile ->
            ArchivePreviewDialog(
                item = archiveFile,
                inspection = state.archiveInspection,
                isLoading = state.archivePreviewLoading,
                onDismiss = viewModel::closeArchivePreview,
                onRetryWithPassword = { pwd -> viewModel.openArchivePreview(archiveFile, password = pwd) },
                onExtract = { toSubfolder, pwd ->
                    viewModel.extractArchive(
                        item = archiveFile,
                        extractToSubfolder = toSubfolder,
                        password = pwd
                    )
                },
                onOpenAsFallback = {
                    viewModel.closeArchivePreview()
                    viewModel.requestOpenWith(archiveFile, fromOpenAsAction = true)
                },
                isTesting = state.archiveTesting,
                testResult = state.archiveTestResult,
                drives = state.drives,
                defaultExtractPath = remember(archiveFile.id, state.activeTabId) {
                    viewModel.resolveDefaultExtractDestination(archiveFile)
                },
                onLoadSubfolders = viewModel::listExtractDestinationSubfolders,
                onCreateDestinationFolder = viewModel::createExtractDestinationFolder,
                onTestArchive = { pwd -> viewModel.testArchive(archiveFile, password = pwd) },
                onPreviewEntry = { entryPath, pwd ->
                    viewModel.previewArchiveEntry(
                        item = archiveFile,
                        entryPath = entryPath,
                        password = pwd,
                        onLaunchExternal = { intent -> launchExternalWithTitle(intent, "Open Entry") }
                    )
                },
                onExtractWithOptions = { req ->
                    viewModel.extractArchive(
                        item = archiveFile,
                        extractToSubfolder = req.extractToSubfolder,
                        password = req.password,
                        targetParentPathOrUriOverride = req.targetParentPathOrUri,
                        isTargetSafOverride = req.isTargetSaf,
                        customSubfolderName = req.customSubfolderName,
                        overwriteMode = req.overwriteMode,
                        selectedEntryPaths = req.selectedEntryPaths,
                        openDestinationWhenDone = req.openDestinationWhenDone
                    )
                }
            )
        }

        state.compressTargets?.let { targets ->
            CompressArchiveDialog(
                items = targets,
                onDismiss = viewModel::dismissCompressDialog,
                onCreate = { name, format, level, pwd ->
                    viewModel.createArchive(
                        items = targets,
                        archiveName = name,
                        format = format,
                        compressionLevel = level,
                        password = pwd
                    )
                }
            )
        }

        if (showCreateFolderDialog) {
            CreateFolderDialog(
                onDismiss = { showCreateFolderDialog = false },
                onCreate = { name -> showCreateFolderDialog = false; viewModel.createFolder(name) }
            )
        }

        batchRenameTargets?.let { targets ->
            BatchRenameDialog(
                items = targets,
                onDismiss = { batchRenameTargets = null },
                onApply = { renames ->
                    batchRenameTargets = null
                    viewModel.batchRenameFiles(renames)
                }
            )
        }

        deleteTargets?.let { targets ->
            DeleteConfirmDialog(
                items = targets,
                recycleBinEnabled = state.prefs.recycleBinEnabled,
                onDismiss = { deleteTargets = null },
                onMoveToTrash = { deleteTargets = null; viewModel.moveFilesToTrash(targets) },
                onConfirm = { deleteTargets = null; viewModel.deleteFiles(targets) }
            )
        }

        state.tagPickerTargets?.let { targets ->
            val tagCounts = remember(state.taggedFilesByTagId) {
                state.taggedFilesByTagId.mapValues { it.value.size }
            }
            TagsPickerSheet(
                targets = targets,
                allTags = state.allTags,
                itemTagsByPath = state.itemTagsByPath,
                taggedCountsByTagId = tagCounts,
                onToggleTag = { tagId -> viewModel.toggleTagOnTargets(tagId, targets) },
                onCreateTag = { name, colorHex ->
                    viewModel.createCustomTag(name, colorHex, applyToTargets = targets)
                },
                onDeleteTag = viewModel::deleteCustomTag,
                onDismiss = viewModel::dismissTagPicker
            )
        }

        val dialogSection = state.libraryState.dialogSection
        val sectionCustomAlbums = state.libraryState.customAlbumsFor(dialogSection)
        val sectionMediaLabel = if (dialogSection == com.example.data.LibrarySection.MOVIES) "Movies" else "Gallery"
        val sectionOriginalNoun = if (dialogSection == com.example.data.LibrarySection.MOVIES) "videos" else "photos"

        if (state.libraryState.showCreateAlbumDialog) {
            CreateOrRenameAlbumDialog(
                title = "New Album",
                subtitle = "Create a virtual album from your $sectionMediaLabel",
                confirmLabel = "Create",
                existingAlbumNames = sectionCustomAlbums.map { it.name }.toSet(),
                initialItemCount = state.libraryState.createAlbumInitialItems.size,
                mediaSection = dialogSection,
                onDismiss = viewModel::dismissCreateAlbumDialog,
                onConfirm = { name ->
                    viewModel.createPhotoAlbum(name, state.libraryState.createAlbumInitialItems, dialogSection)
                }
            )
        }

        state.libraryState.renameAlbumTarget?.let { targetAlbum ->
            val renameAlbums = state.libraryState.customAlbumsFor(targetAlbum.mediaSection)
            val renameNoun = if (targetAlbum.mediaSection == com.example.data.LibrarySection.MOVIES) "videos" else "photos"
            CreateOrRenameAlbumDialog(
                initialName = targetAlbum.name,
                title = "Rename Album",
                subtitle = "Original $renameNoun stay untouched on storage",
                confirmLabel = "Rename",
                existingAlbumNames = renameAlbums.map { it.name }.toSet(),
                mediaSection = targetAlbum.mediaSection,
                onDismiss = viewModel::dismissRenameAlbumDialog,
                onConfirm = { newName ->
                    viewModel.renamePhotoAlbum(targetAlbum.id, newName)
                }
            )
        }

        state.libraryState.addToAlbumTargets?.let { targets ->
            AddToAlbumDialog(
                targetImages = targets,
                customAlbums = sectionCustomAlbums,
                mediaSection = dialogSection,
                onDismiss = viewModel::dismissAddToAlbumDialog,
                onCreateNewAlbumWithTargets = { viewModel.openCreateAlbumDialog(targets, dialogSection) },
                onConfirmAlbumIds = { albumIds -> viewModel.addImagesToAlbums(albumIds, targets) }
            )
        }

        state.libraryState.pickImagesForAlbumId?.let { albumId ->
            val album = state.libraryState.findAlbum(albumId, dialogSection)
            val allSectionMedia = remember(state.allIndexedFiles, dialogSection) {
                viewModel.getLibrarySectionItems(dialogSection)
            }
            val existingKeys = remember(albumId, state.libraryState, dialogSection) {
                state.libraryState.itemsForAlbum(albumId, dialogSection)
                    .map { LibraryDomainUtils.normalizedKey(it) }
                    .toSet()
            }
            PickImagesForAlbumDialog(
                albumName = album?.name ?: "Album",
                allImages = allSectionMedia,
                alreadyInAlbumKeys = existingKeys,
                mediaSection = dialogSection,
                onDismiss = viewModel::dismissPickImagesForAlbum,
                onConfirmSelection = { chosen ->
                    viewModel.addImagesToSingleAlbum(albumId, chosen)
                }
            )
        }
        }
    }
}
