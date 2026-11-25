package com.example.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.data.ArcImageLoader
import com.example.data.CacheBucket
import com.example.data.FileItem
import com.example.data.FileTag
import com.example.data.FormatUtils
import com.example.data.GalleryPanel
import com.example.data.LibrarySection
import com.example.data.PhotoAlbum
import com.example.data.SortField
import com.example.ui.components.common.TagDotsCluster
import com.example.ui.registry.FileActionId
import com.example.ui.screens.browser.BrowserDragDropState
import com.example.ui.screens.browser.BrowserDragGhostOverlay
import com.example.ui.screens.browser.browserDragSource
import com.example.ui.screens.browser.browserDropTarget
import com.example.ui.screens.browser.rememberBrowserDragDropState
import com.example.ui.theme.ArcTheme
import com.example.ui.theme.BinaryBlue
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import kotlin.math.roundToInt

@Composable
fun GalleryLibraryScreen(
    allImages: List<FileItem>,
    libraryState: LibraryUiState,
    selectedIds: Set<String>,
    itemTagsByPath: Map<String, List<FileTag>>,
    gridColumns: Int,
    originalMediaRatio: Boolean,
    dragReorderEnabled: Boolean,
    isActivePage: Boolean,
    section: LibrarySection = LibrarySection.IMAGES,
    sortField: SortField = SortField.DATE,
    sortAscending: Boolean = false,
    onSortOrderChange: (SortField, Boolean) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    onSelectPanel: (GalleryPanel) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onCloseAlbum: () -> Unit,
    onCreateAlbumClick: (List<FileItem>) -> Unit,
    onRenameAlbumClick: (PhotoAlbum) -> Unit,
    onDeleteAlbumClick: (PhotoAlbum) -> Unit,
    onSetAlbumCover: (String, FileItem) -> Unit,
    onAddToAlbumClick: (List<FileItem>) -> Unit,
    onPickImagesForAlbumClick: (String) -> Unit,
    onRemoveImagesFromAlbum: (String, List<FileItem>) -> Unit,
    onOpenDeviceAlbumFolder: (PhotoAlbum) -> Unit,
    onPhotoClick: (FileItem, List<FileItem>) -> Unit,
    onToggleSelect: (FileItem) -> Unit,
    onSelectAll: (List<FileItem>) -> Unit,
    onClearSelection: () -> Unit,
    onFileAction: (FileActionId, List<FileItem>) -> Unit,
    onReorderImages: (List<String>) -> Unit,
    onImportMediaClick: () -> Unit,
    onMountFolderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMovieMode = section == LibrarySection.MOVIES
    val cacheBucket = if (isMovieMode) CacheBucket.MOVIE_VIDEOS else CacheBucket.GALLERY_IMAGES
    val activePanel = libraryState.panelFor(section)
    val activeAlbumId = libraryState.activeAlbumIdFor(section)
    val activeAlbum = libraryState.findAlbum(activeAlbumId, section)
    val customAlbums = libraryState.customAlbumsFor(section)
    val deviceAlbums = libraryState.deviceAlbumsFor(section)
    BackHandler(enabled = isActivePage) {
        when {
            selectedIds.isNotEmpty() -> onClearSelection()
            activeAlbum != null -> onCloseAlbum()
            else -> onBack()
        }
    }

    val d = ArcTheme.dimens
    val panelPagerState = rememberPagerState(
        initialPage = activePanel.ordinal,
        pageCount = { GalleryPanel.entries.size }
    )
    val currentPanel by rememberUpdatedState(activePanel)
    val hasActiveAlbum by rememberUpdatedState(activeAlbum != null)

    LaunchedEffect(activePanel, activeAlbum) {
        if (activeAlbum == null) {
            val targetPage = activePanel.ordinal
            if (panelPagerState.currentPage != targetPage) {
                panelPagerState.animateScrollToPage(targetPage)
            }
        }
    }

    LaunchedEffect(panelPagerState) {
        snapshotFlow { panelPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (!hasActiveAlbum) {
                    val mapped = GalleryPanel.entries.getOrElse(page) { GalleryPanel.ALL_IMAGES }
                    if (mapped != currentPanel) {
                        onSelectPanel(mapped)
                    }
                }
            }
    }

    val sortedAllImages = remember(allImages, sortField, sortAscending) {
        if (sortField == SortField.CUSTOM) allImages
        else sortGalleryPhotos(allImages, sortField, sortAscending)
    }

    val dragState = rememberBrowserDragDropState()
    val baseOrderIds = remember(sortedAllImages) { sortedAllImages.map { it.id } }
    SideEffect {
        dragState.reorderEnabled = dragReorderEnabled &&
            activeAlbum == null &&
            activePanel == GalleryPanel.ALL_IMAGES &&
            sortedAllImages.size > 1
        dragState.isGridMode = true
        dragState.syncBaseOrder(baseOrderIds)
    }
    val activePreviewIds = dragState.activeSession?.previewOrderIds
    val renderedImages = remember(sortedAllImages, activePreviewIds) {
        if (activePreviewIds.isNullOrEmpty()) {
            sortedAllImages
        } else {
            val byId = sortedAllImages.associateBy { it.id }
            val ordered = ArrayList<FileItem>(sortedAllImages.size)
            val seen = HashSet<String>(sortedAllImages.size)
            for (id in activePreviewIds) {
                val found = byId[id]
                if (found != null && seen.add(id)) ordered.add(found)
            }
            for (item in sortedAllImages) {
                if (seen.add(item.id)) ordered.add(item)
            }
            ordered
        }
    }

    DisposableEffect(dragState) {
        onDispose { dragState.cancelDrag() }
    }

    val sortedAlbumPhotos = remember(activeAlbum, libraryState, section, sortField, sortAscending) {
        if (activeAlbum == null) {
            emptyList()
        } else {
            sortGalleryPhotos(
                photos = libraryState.itemsForAlbum(activeAlbum.id, section),
                sortField = sortField,
                sortAscending = sortAscending
            )
        }
    }
    val sortedCustomAlbums = remember(customAlbums, sortField, sortAscending) {
        sortGalleryAlbums(customAlbums, sortField, sortAscending)
    }
    val sortedDeviceAlbums = remember(deviceAlbums, sortField, sortAscending) {
        sortGalleryAlbums(deviceAlbums, sortField, sortAscending)
    }

    val activeVisibleList = if (activeAlbum != null) sortedAlbumPhotos else renderedImages
    val selectedImages = remember(activeVisibleList, selectedIds) {
        if (selectedIds.isEmpty()) emptyList() else activeVisibleList.filter { it.id in selectedIds }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { dragState.rootCoords = it }
            .testTag("gallery_library_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedContent(
                    targetState = activeAlbum,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                    label = "gallery_album_transition",
                    modifier = Modifier
                        .widthIn(max = d.maxContentWidth)
                        .fillMaxSize()
                ) { openedAlbum ->
                    if (openedAlbum != null) {
                        AlbumDetailView(
                            album = openedAlbum,
                            isMovieMode = isMovieMode,
                            cacheBucket = cacheBucket,
                            photos = sortedAlbumPhotos,
                            selectedIds = selectedIds,
                            itemTagsByPath = itemTagsByPath,
                            gridColumns = gridColumns,
                            originalMediaRatio = originalMediaRatio,
                            sortField = sortField,
                            sortAscending = sortAscending,
                            onSortOrderChange = onSortOrderChange,
                            onBackToAlbums = onCloseAlbum,
                            onPickImagesClick = { onPickImagesForAlbumClick(openedAlbum.id) },
                            onOpenFolderClick = { onOpenDeviceAlbumFolder(openedAlbum) },
                            onRenameAlbum = { onRenameAlbumClick(openedAlbum) },
                            onDeleteAlbum = { onDeleteAlbumClick(openedAlbum) },
                            onPhotoClick = { photo -> onPhotoClick(photo, sortedAlbumPhotos) },
                            onToggleSelect = onToggleSelect
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            GalleryTopControls(
                                activePanel = activePanel,
                                isMovieMode = isMovieMode,
                                allImagesCount = allImages.size,
                                allImagesBytes = remember(allImages) { allImages.sumOf { it.sizeBytes } },
                                customAlbumCount = customAlbums.size,
                                folderCount = deviceAlbums.size,
                                sortField = sortField,
                                sortAscending = sortAscending,
                                onSortOrderChange = onSortOrderChange,
                                onNewAlbumClick = { onCreateAlbumClick(emptyList()) }
                            )

                            HorizontalPager(
                                state = panelPagerState,
                                beyondViewportPageCount = 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .testTag("gallery_panel_pager")
                            ) { panelIndex ->
                                when (GalleryPanel.entries.getOrElse(panelIndex) { GalleryPanel.ALL_IMAGES }) {
                                    GalleryPanel.ALL_IMAGES -> AllImagesPanel(
                                        images = renderedImages,
                                        isMovieMode = isMovieMode,
                                        cacheBucket = cacheBucket,
                                        selectedIds = selectedIds,
                                        itemTagsByPath = itemTagsByPath,
                                        gridColumns = gridColumns,
                                        originalMediaRatio = originalMediaRatio,
                                        dragState = dragState,
                                        bottomClearance = 14.dp,
                                        onPhotoClick = { item ->
                                            if (selectedIds.isNotEmpty()) onToggleSelect(item)
                                            else onPhotoClick(item, renderedImages)
                                        },
                                        onToggleSelect = onToggleSelect,
                                        onReorderImages = onReorderImages,
                                        onImportMediaClick = onImportMediaClick,
                                        onMountFolderClick = onMountFolderClick
                                    )

                                    GalleryPanel.ALBUMS -> AlbumsPanel(
                                        customAlbums = sortedCustomAlbums,
                                        isMovieMode = isMovieMode,
                                        cacheBucket = cacheBucket,
                                        bottomClearance = 14.dp,
                                        onCreateAlbumClick = { onCreateAlbumClick(emptyList()) },
                                        onAlbumClick = { album -> onOpenAlbum(album.id) },
                                        onAddPhotosToAlbum = { album -> onPickImagesForAlbumClick(album.id) },
                                        onRenameAlbum = onRenameAlbumClick,
                                        onDeleteAlbum = onDeleteAlbumClick
                                    )

                                    GalleryPanel.FOLDERS -> FoldersPanel(
                                        deviceAlbums = sortedDeviceAlbums,
                                        isMovieMode = isMovieMode,
                                        cacheBucket = cacheBucket,
                                        bottomClearance = 14.dp,
                                        onFolderClick = { folder -> onOpenAlbum(folder.id) },
                                        onOpenPhysicalFolder = onOpenDeviceAlbumFolder,
                                        onMountFolderClick = onMountFolderClick,
                                        onImportMediaClick = onImportMediaClick
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedImages.isNotEmpty(),
                enter = slideInVertically(tween(200)) { it / 2 } + fadeIn(tween(170)),
                exit = slideOutVertically(tween(150)) { it / 2 } + fadeOut(tween(130))
            ) {
                GallerySelectionBottomBar(
                    selectedItems = selectedImages,
                    allSelected = activeVisibleList.isNotEmpty() && selectedImages.size >= activeVisibleList.size,
                    isInsideCustomAlbum = activeAlbum != null && !activeAlbum.isDeviceFolder,
                    includeNavBarPadding = false,
                    onSelectAll = { onSelectAll(activeVisibleList) },
                    onClear = onClearSelection,
                    onAddToAlbum = { onAddToAlbumClick(selectedImages) },
                    onSetCover = {
                        val targetAlbum = activeAlbum
                        val firstPhoto = selectedImages.firstOrNull()
                        if (targetAlbum != null && firstPhoto != null) {
                            onSetAlbumCover(targetAlbum.id, firstPhoto)
                            onClearSelection()
                        }
                    },
                    onRemoveFromAlbum = {
                        val targetAlbum = activeAlbum
                        if (targetAlbum != null) {
                            onRemoveImagesFromAlbum(targetAlbum.id, selectedImages)
                            onClearSelection()
                        }
                    },
                    onAction = onFileAction
                )
            }

            GalleryBottomTabBar(
                activePanel = activePanel,
                isMovieMode = isMovieMode,
                allImagesCount = allImages.size,
                albumCount = customAlbums.size,
                folderCount = deviceAlbums.size,
                onSelectTab = { panel ->
                    if (activeAlbum != null && panel == activePanel) {
                        onCloseAlbum()
                    } else {
                        onSelectPanel(panel)
                    }
                }
            )
        }

        BrowserDragGhostOverlay(dragState = dragState)
    }
}

private fun effectiveImageTimestamp(item: FileItem): Long =
    maxOf(item.createdAtMillis, item.lastModifiedMillis)

private fun sortGalleryPhotos(
    photos: List<FileItem>,
    sortField: SortField,
    sortAscending: Boolean
): List<FileItem> {
    if (photos.size <= 1) return photos
    val dir = if (sortAscending) 1 else -1
    return when (sortField) {
        SortField.NAME -> photos.sortedWith { a, b -> a.name.compareTo(b.name, ignoreCase = true) * dir }
        SortField.SIZE -> photos.sortedWith { a, b ->
            val cmp = a.sizeBytes.compareTo(b.sizeBytes)
            (if (cmp != 0) cmp else a.name.compareTo(b.name, ignoreCase = true)) * dir
        }
        SortField.TYPE -> photos.sortedWith { a, b ->
            val cmp = a.extension.compareTo(b.extension, ignoreCase = true)
            (if (cmp != 0) cmp else a.name.compareTo(b.name, ignoreCase = true)) * dir
        }
        SortField.DATE, SortField.CUSTOM -> photos.sortedWith { a, b ->
            val cmp = effectiveImageTimestamp(a).compareTo(effectiveImageTimestamp(b))
            if (cmp != 0) cmp * dir else a.name.compareTo(b.name, ignoreCase = true)
        }
    }
}

private fun sortGalleryAlbums(
    albums: List<PhotoAlbum>,
    sortField: SortField,
    sortAscending: Boolean
): List<PhotoAlbum> {
    if (albums.size <= 1) return albums
    val dir = if (sortAscending) 1 else -1
    return if (sortField == SortField.NAME) {
        albums.sortedWith { a, b -> a.name.compareTo(b.name, ignoreCase = true) * dir }
    } else {
        albums.sortedWith { a, b ->
            val cmp = a.updatedAtMillis.compareTo(b.updatedAtMillis)
            if (cmp != 0) cmp * dir else a.name.compareTo(b.name, ignoreCase = true)
        }
    }
}

@Composable
private fun GalleryTopControls(
    activePanel: GalleryPanel,
    isMovieMode: Boolean,
    allImagesCount: Int,
    allImagesBytes: Long,
    customAlbumCount: Int,
    folderCount: Int,
    sortField: SortField,
    sortAscending: Boolean,
    onSortOrderChange: (SortField, Boolean) -> Unit,
    onNewAlbumClick: () -> Unit
) {
    val d = ArcTheme.dimens
    val mediaPlural = if (isMovieMode) "videos" else "photos"
    val title = when (activePanel) {
        GalleryPanel.ALL_IMAGES -> if (isMovieMode) "Movies" else "Images"
        GalleryPanel.ALBUMS -> "My Albums"
        GalleryPanel.FOLDERS -> if (isMovieMode) "Video Folders" else "Image Folders"
    }
    val subtitle = when (activePanel) {
        GalleryPanel.ALL_IMAGES -> if (isMovieMode) "All folders & mounted videos" else "All folders & mounted photos"
        GalleryPanel.ALBUMS -> "Virtual collections · original $mediaPlural untouched"
        GalleryPanel.FOLDERS -> "Dedicated storage folders"
    }
    val headerIcon = when (activePanel) {
        GalleryPanel.ALL_IMAGES -> if (isMovieMode) Icons.Filled.Movie else Icons.Filled.PhotoLibrary
        GalleryPanel.ALBUMS -> Icons.Filled.Collections
        GalleryPanel.FOLDERS -> Icons.Filled.FolderOpen
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = d.screenHorizontalPadding, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BinaryBlue.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
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
            }

            if (activePanel == GalleryPanel.ALBUMS) {
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .clickable(onClick = onNewAlbumClick)
                        .testTag("gallery_header_new_album")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New Album",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "New Album",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        GallerySortStrip(
            sortField = sortField,
            sortAscending = sortAscending,
            onSortOrderChange = onSortOrderChange
        )
    }
}

@Composable
private fun GallerySortStrip(
    sortField: SortField,
    sortAscending: Boolean,
    onSortOrderChange: (SortField, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isNewestActive = sortField == SortField.DATE
    val isNameActive = sortField == SortField.NAME
    val isCustomActive = sortField == SortField.CUSTOM

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GallerySortChip(
            label = if (isNewestActive && sortAscending) "Oldest" else "Newest",
            selected = isNewestActive,
            ascending = if (isNewestActive) sortAscending else false,
            testTag = "gallery_sort_newest",
            onClick = {
                if (isNewestActive) {
                    onSortOrderChange(SortField.DATE, !sortAscending)
                } else {
                    onSortOrderChange(SortField.DATE, false)
                }
            }
        )
        GallerySortChip(
            label = if (isNameActive && !sortAscending) "Name Z–A" else "Name A–Z",
            selected = isNameActive,
            ascending = if (isNameActive) sortAscending else true,
            testTag = "gallery_sort_name",
            onClick = {
                if (isNameActive) {
                    onSortOrderChange(SortField.NAME, !sortAscending)
                } else {
                    onSortOrderChange(SortField.NAME, true)
                }
            }
        )
        if (isCustomActive) {
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Text(
                    text = "Custom order",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun GallerySortChip(
    label: String,
    selected: Boolean,
    ascending: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.dp else 0.5.dp,
            color = if (selected) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Icon(
                imageVector = if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun GalleryBottomTabBar(
    activePanel: GalleryPanel,
    isMovieMode: Boolean,
    allImagesCount: Int,
    albumCount: Int,
    folderCount: Int,
    onSelectTab: (GalleryPanel) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gallery_bottom_tab_bar"),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GalleryBottomTabItem(
                    label = if (isMovieMode) "All Videos" else "All Images",
                    count = allImagesCount,
                    selected = activePanel == GalleryPanel.ALL_IMAGES,
                    selectedIcon = if (isMovieMode) Icons.Filled.Movie else Icons.Filled.PhotoLibrary,
                    unselectedIcon = if (isMovieMode) Icons.Outlined.Movie else Icons.Outlined.PhotoLibrary,
                    testTag = "gallery_tab_all_images",
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectTab(GalleryPanel.ALL_IMAGES) }
                )
                GalleryBottomTabItem(
                    label = "Albums",
                    count = albumCount,
                    selected = activePanel == GalleryPanel.ALBUMS,
                    selectedIcon = Icons.Filled.Collections,
                    unselectedIcon = Icons.Outlined.Collections,
                    testTag = "gallery_tab_albums",
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectTab(GalleryPanel.ALBUMS) }
                )
                GalleryBottomTabItem(
                    label = "Folders",
                    count = folderCount,
                    selected = activePanel == GalleryPanel.FOLDERS,
                    selectedIcon = Icons.Filled.FolderOpen,
                    unselectedIcon = Icons.Outlined.Folder,
                    testTag = "gallery_tab_folders",
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectTab(GalleryPanel.FOLDERS) }
                )
            }
        }
    }
}

@Composable
private fun GalleryBottomTabItem(
    label: String,
    count: Int,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val fg = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(19.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AllImagesPanel(
    images: List<FileItem>,
    isMovieMode: Boolean,
    cacheBucket: CacheBucket,
    selectedIds: Set<String>,
    itemTagsByPath: Map<String, List<FileTag>>,
    gridColumns: Int,
    originalMediaRatio: Boolean,
    dragState: BrowserDragDropState?,
    bottomClearance: androidx.compose.ui.unit.Dp,
    onPhotoClick: (FileItem) -> Unit,
    onToggleSelect: (FileItem) -> Unit,
    onReorderImages: (List<String>) -> Unit,
    onImportMediaClick: () -> Unit,
    onMountFolderClick: () -> Unit
) {
    val d = ArcTheme.dimens
    if (images.isEmpty()) {
        GalleryEmptyPlaceholder(
            icon = if (isMovieMode) Icons.Outlined.Movie else Icons.Outlined.PhotoLibrary,
            title = if (isMovieMode) "No videos found" else "No photos found",
            subtitle = if (isMovieMode) {
                "Import videos or mount your Movies/DCIM folder to organize them in your Movies Library."
            } else {
                "Import images or mount your Pictures/DCIM folder to organize them in your Gallery."
            },
            primaryLabel = "Import Media",
            onPrimaryClick = onImportMediaClick,
            secondaryLabel = "Mount Folder",
            onSecondaryClick = onMountFolderClick
        )
        return
    }

    val currentSelectedIds by rememberUpdatedState(selectedIds)
    val currentImages by rememberUpdatedState(images)
    val dragPayloadProvider = remember {
        { item: FileItem ->
            val ids = currentSelectedIds
            if (item.id in ids) currentImages.filter { it.id in ids } else listOf(item)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cols = if (maxWidth >= 600.dp) (gridColumns + 1).coerceIn(3, 5) else gridColumns.coerceIn(2, 4)
        val gridState = rememberLazyGridState()
        val cellPx = with(LocalDensity.current) { ((maxWidth / cols).toPx() * 1.1f).roundToInt() }
        val decodePx = when {
            cellPx <= 160 -> 160
            cellPx <= 240 -> 240
            cellPx <= 360 -> 360
            else -> 480
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(cols),
            modifier = Modifier
                .fillMaxSize()
                .testTag("gallery_all_images_grid"),
            contentPadding = PaddingValues(
                start = d.screenHorizontalPadding,
                end = d.screenHorizontalPadding,
                top = 4.dp,
                bottom = bottomClearance
            ),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(
                items = images,
                key = { it.id },
                contentType = { "gallery_photo" }
            ) { item ->
                val isSelected = item.id in selectedIds
                val tags = if (itemTagsByPath.isEmpty()) emptyList() else {
                    itemTagsByPath[LibraryDomainUtils.normalizedKey(item)].orEmpty()
                }
                GalleryPhotoTile(
                    item = item,
                    cacheBucket = cacheBucket,
                    isSelected = isSelected,
                    selectionMode = selectedIds.isNotEmpty(),
                    decodePx = decodePx,
                    originalRatio = originalMediaRatio,
                    tags = tags,
                    dragState = dragState,
                    dragPayloadProvider = dragPayloadProvider,
                    onClick = { onPhotoClick(item) },
                    onLongClick = { onToggleSelect(item) },
                    onReorderCommit = onReorderImages,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryPhotoTile(
    item: FileItem,
    cacheBucket: CacheBucket,
    isSelected: Boolean,
    selectionMode: Boolean,
    decodePx: Int,
    originalRatio: Boolean,
    tags: List<FileTag>,
    dragState: BrowserDragDropState?,
    dragPayloadProvider: ((FileItem) -> List<FileItem>)?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onReorderCommit: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = remember(context, cacheBucket) { ArcImageLoader.getForBucket(context, cacheBucket) }
    val isDragged = dragState?.isDragged(item.id) == true
    val isVideoTile = item.isVideo || cacheBucket == CacheBucket.MOVIE_VIDEOS
    val request = remember(item.pathOrUri, item.lastModifiedMillis, item.isSafUri, decodePx, cacheBucket) {
        ImageRequest.Builder(context)
            .data(if (item.isSafUri) item.pathOrUri else File(item.pathOrUri))
            .size(decodePx, decodePx)
            .precision(Precision.INEXACT)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }
    val extBadge = remember(item.extension, isVideoTile) {
        when (val ext = item.extension.lowercase()) {
            "gif" -> "GIF"
            "heic", "heics" -> "HEIC"
            "heif", "heifs", "hif" -> "HEIF"
            "avif" -> "AVIF"
            "svg" -> "SVG"
            else -> if (isVideoTile && ext.isNotBlank()) ext.uppercase() else null
        }
    }
    val shape = RoundedCornerShape(7.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (originalRatio) 0.92f else 1f)
            .graphicsLayer { alpha = if (isDragged) 0.35f else 1f }
            .then(
                if (dragState != null) {
                    Modifier.browserDropTarget(
                        dragState = dragState,
                        targetId = "gallery_${item.id}",
                        label = item.name,
                        pathOrUri = item.pathOrUri,
                        isSaf = item.isSafUri,
                        enabled = true,
                        itemId = item.id,
                        isDirectory = false
                    )
                } else Modifier
            )
            .clip(shape)
            .background(if (isVideoTile) Color(0xFF141821) else MaterialTheme.colorScheme.surfaceContainer)
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            )
            .then(
                if (dragState != null && dragPayloadProvider != null) {
                    Modifier
                        .clickable(onClick = onClick)
                        .browserDragSource(
                            dragState = dragState,
                            item = item,
                            dragPayloadForItem = dragPayloadProvider,
                            enabled = true,
                            onLongClick = onLongClick,
                            onDropMove = { _, _ -> },
                            onReorderCommit = onReorderCommit
                        )
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                }
            )
            .testTag("gallery_photo_${item.name}")
    ) {
        if (isVideoTile) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (isVideoTile) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.48f))
                    .border(1.dp, Color.White.copy(alpha = 0.78f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = FormatUtils.formatBytes(item.sizeBytes),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
            )
        }

        if (extBadge != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp),
                shape = RoundedCornerShape(5.dp),
                color = Color.Black.copy(alpha = 0.56f)
            ) {
                Text(
                    text = extBadge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }

        if (tags.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
            ) {
                TagDotsCluster(tags = tags)
            }
        }

        if (selectionMode || isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.36f)
                    )
                    .border(1.2.dp, Color.White.copy(alpha = 0.92f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumsPanel(
    customAlbums: List<PhotoAlbum>,
    isMovieMode: Boolean,
    cacheBucket: CacheBucket,
    bottomClearance: androidx.compose.ui.unit.Dp,
    onCreateAlbumClick: () -> Unit,
    onAlbumClick: (PhotoAlbum) -> Unit,
    onAddPhotosToAlbum: (PhotoAlbum) -> Unit,
    onRenameAlbum: (PhotoAlbum) -> Unit,
    onDeleteAlbum: (PhotoAlbum) -> Unit
) {
    val d = ArcTheme.dimens
    val nounPlural = if (isMovieMode) "videos" else "photos"
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cols = if (maxWidth >= 600.dp) 3 else 2
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier
                .fillMaxSize()
                .testTag("gallery_albums_grid"),
            contentPadding = PaddingValues(
                start = d.screenHorizontalPadding,
                end = d.screenHorizontalPadding,
                top = 4.dp,
                bottom = bottomClearance
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(span = { GridItemSpan(cols) }, key = "hdr_my_albums") {
                AlbumSectionHeader(
                    title = "My Albums",
                    count = customAlbums.size,
                    subtitle = "Virtual collections · original $nounPlural stay untouched"
                )
            }

            item(key = "create_album_card") {
                NewAlbumTileCard(onClick = onCreateAlbumClick)
            }

            items(customAlbums, key = { it.id }) { album ->
                AlbumCardTile(
                    album = album,
                    isMovieMode = isMovieMode,
                    cacheBucket = cacheBucket,
                    onClick = { onAlbumClick(album) },
                    onAddPhotos = { onAddPhotosToAlbum(album) },
                    onRename = { onRenameAlbum(album) },
                    onDelete = { onDeleteAlbum(album) },
                    onOpenFolder = {}
                )
            }
        }
    }
}

@Composable
private fun FoldersPanel(
    deviceAlbums: List<PhotoAlbum>,
    isMovieMode: Boolean,
    cacheBucket: CacheBucket,
    bottomClearance: androidx.compose.ui.unit.Dp,
    onFolderClick: (PhotoAlbum) -> Unit,
    onOpenPhysicalFolder: (PhotoAlbum) -> Unit,
    onMountFolderClick: () -> Unit,
    onImportMediaClick: () -> Unit
) {
    val d = ArcTheme.dimens
    if (deviceAlbums.isEmpty()) {
        GalleryEmptyPlaceholder(
            icon = Icons.Outlined.Folder,
            title = if (isMovieMode) "No video folders found" else "No image folders found",
            subtitle = if (isMovieMode) {
                "Mount a device folder or import videos to browse clips organized by folder."
            } else {
                "Mount a device folder or import photos to browse images organized by folder."
            },
            primaryLabel = "Mount Folder",
            onPrimaryClick = onMountFolderClick,
            secondaryLabel = "Import Media",
            onSecondaryClick = onImportMediaClick
        )
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cols = if (maxWidth >= 600.dp) 3 else 2
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier
                .fillMaxSize()
                .testTag("gallery_folders_grid"),
            contentPadding = PaddingValues(
                start = d.screenHorizontalPadding,
                end = d.screenHorizontalPadding,
                top = 4.dp,
                bottom = bottomClearance
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(span = { GridItemSpan(cols) }, key = "hdr_folders") {
                AlbumSectionHeader(
                    title = if (isMovieMode) "Video Folders" else "Image Folders",
                    count = deviceAlbums.size,
                    subtitle = "Dedicated folders across device & mounted storage"
                )
            }

            items(deviceAlbums, key = { it.id }) { folder ->
                AlbumCardTile(
                    album = folder,
                    isMovieMode = isMovieMode,
                    cacheBucket = cacheBucket,
                    onClick = { onFolderClick(folder) },
                    onAddPhotos = {},
                    onRename = {},
                    onDelete = {},
                    onOpenFolder = { onOpenPhysicalFolder(folder) }
                )
            }
        }
    }
}

@Composable
private fun AlbumSectionHeader(
    title: String,
    @Suppress("UNUSED_PARAMETER") count: Int,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NewAlbumTileCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("gallery_new_album_card"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Create Album",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = "New Album",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun AlbumCardTile(
    album: PhotoAlbum,
    isMovieMode: Boolean,
    cacheBucket: CacheBucket,
    onClick: () -> Unit,
    onAddPhotos: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpenFolder: () -> Unit
) {
    val context = LocalContext.current
    val imageLoader = remember(context, cacheBucket) { ArcImageLoader.getForBucket(context, cacheBucket) }
    var menuOpen by remember { mutableStateOf(false) }
    val itemSingular = if (isMovieMode) "video" else "photo"
    val itemPlural = if (isMovieMode) "videos" else "photos"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("album_card_${album.name}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(16.dp),
            color = if (isMovieMode) Color(0xFF141821) else MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    album.previewThumbPaths.size >= 4 -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(1.5.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                            ) {
                                MosaicThumbCell(album.previewThumbPaths[0], imageLoader, Modifier.weight(1f).fillMaxHeight())
                                MosaicThumbCell(album.previewThumbPaths[1], imageLoader, Modifier.weight(1f).fillMaxHeight())
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                            ) {
                                MosaicThumbCell(album.previewThumbPaths[2], imageLoader, Modifier.weight(1f).fillMaxHeight())
                                MosaicThumbCell(album.previewThumbPaths[3], imageLoader, Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                    album.coverPathOrUri != null -> {
                        MosaicThumbCell(
                            thumb = album.coverPathOrUri to album.coverIsSaf,
                            imageLoader = imageLoader,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isMovieMode) Color(0xFF1A202B)
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isMovieMode) Icons.Outlined.Movie else Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }

                // Kind pill (Device folder vs Video/Custom)
                if (album.isDeviceFolder) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(7.dp),
                        color = Color.Black.copy(alpha = 0.56f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Folder",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White
                            )
                        }
                    }
                } else if (isMovieMode) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(7.dp),
                        color = Color.Black.copy(alpha = 0.56f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Videos",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.44f))
                            .clickable { menuOpen = true }
                            .testTag("album_menu_${album.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Album options",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        if (!album.isDeviceFolder) {
                            DropdownMenuItem(
                                text = { Text(if (isMovieMode) "Add Videos" else "Add Photos") },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                onClick = { menuOpen = false; onAddPhotos() }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename Album") },
                                leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                                onClick = { menuOpen = false; onRename() }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Album", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = { menuOpen = false; onDelete() }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Open Physical Folder") },
                                leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                                onClick = { menuOpen = false; onOpenFolder() }
                            )
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 3.dp)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MosaicThumbCell(
    thumb: Pair<String, Boolean>,
    imageLoader: coil.ImageLoader,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val req = remember(thumb.first, thumb.second) {
        ImageRequest.Builder(context)
            .data(if (thumb.second) thumb.first else File(thumb.first))
            .size(220, 220)
            .precision(Precision.INEXACT)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = req,
        imageLoader = imageLoader,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer)
    )
}

@Composable
private fun AlbumDetailView(
    album: PhotoAlbum,
    isMovieMode: Boolean,
    cacheBucket: CacheBucket,
    photos: List<FileItem>,
    selectedIds: Set<String>,
    itemTagsByPath: Map<String, List<FileTag>>,
    gridColumns: Int,
    originalMediaRatio: Boolean,
    sortField: SortField,
    sortAscending: Boolean,
    onSortOrderChange: (SortField, Boolean) -> Unit,
    onBackToAlbums: () -> Unit,
    onPickImagesClick: () -> Unit,
    onOpenFolderClick: () -> Unit,
    onRenameAlbum: () -> Unit,
    onDeleteAlbum: () -> Unit,
    onPhotoClick: (FileItem) -> Unit,
    onToggleSelect: (FileItem) -> Unit
) {
    val d = ArcTheme.dimens
    var detailMenuOpen by remember { mutableStateOf(false) }
    val itemSingular = if (isMovieMode) "video" else "photo"
    val itemPlural = if (isMovieMode) "videos" else "photos"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("album_detail_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.screenHorizontalPadding, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onBackToAlbums,
                modifier = Modifier.testTag("album_detail_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (album.isDeviceFolder) "Back to Folders" else "Back to Albums"
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (album.isDeviceFolder) "Storage Folder" else "Virtual Album",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!album.isDeviceFolder) {
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .clickable(onClick = onPickImagesClick)
                        .testTag("album_detail_add_photos")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = if (isMovieMode) "Add videos" else "Add photos",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Box {
                    IconButton(onClick = { detailMenuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Album menu")
                    }
                    DropdownMenu(
                        expanded = detailMenuOpen,
                        onDismissRequest = { detailMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename Album") },
                            leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                            onClick = { detailMenuOpen = false; onRenameAlbum() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Album", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { detailMenuOpen = false; onDeleteAlbum() }
                        )
                    }
                }
            } else {
                TextButton(
                    onClick = onOpenFolderClick,
                    modifier = Modifier.testTag("album_detail_open_folder")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Files")
                }
            }
        }

        GallerySortStrip(
            sortField = sortField,
            sortAscending = sortAscending,
            onSortOrderChange = onSortOrderChange,
            modifier = Modifier.padding(horizontal = d.screenHorizontalPadding, vertical = 4.dp)
        )

        if (photos.isEmpty()) {
            GalleryEmptyPlaceholder(
                icon = if (isMovieMode) Icons.Outlined.Movie else Icons.Outlined.PhotoLibrary,
                title = if (album.isDeviceFolder) "No $itemPlural in this folder" else "This album is empty",
                subtitle = if (album.isDeviceFolder) {
                    "New $itemPlural saved into this folder will appear here automatically."
                } else {
                    "Tap Add to include $itemPlural from your ${if (isMovieMode) "Movies" else "Gallery"} without moving original files."
                },
                primaryLabel = if (!album.isDeviceFolder) {
                    if (isMovieMode) "Add Videos" else "Add Photos"
                } else "Back",
                onPrimaryClick = if (!album.isDeviceFolder) onPickImagesClick else onBackToAlbums,
                secondaryLabel = null,
                onSecondaryClick = {}
            )
        } else {
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val cols = if (maxWidth >= 600.dp) (gridColumns + 1).coerceIn(3, 5) else gridColumns.coerceIn(2, 4)
                val cellPx = with(LocalDensity.current) { ((maxWidth / cols).toPx() * 1.1f).roundToInt() }
                val decodePx = when {
                    cellPx <= 160 -> 160
                    cellPx <= 240 -> 240
                    cellPx <= 360 -> 360
                    else -> 480
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("album_detail_grid"),
                    contentPadding = PaddingValues(
                        start = d.screenHorizontalPadding,
                        end = d.screenHorizontalPadding,
                        top = 4.dp,
                        bottom = 14.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(photos, key = { it.id }) { photo ->
                        val isSelected = photo.id in selectedIds
                        val tags = if (itemTagsByPath.isEmpty()) emptyList() else {
                            itemTagsByPath[LibraryDomainUtils.normalizedKey(photo)].orEmpty()
                        }
                        GalleryPhotoTile(
                            item = photo,
                            cacheBucket = cacheBucket,
                            isSelected = isSelected,
                            selectionMode = selectedIds.isNotEmpty(),
                            decodePx = decodePx,
                            originalRatio = originalMediaRatio,
                            tags = tags,
                            dragState = null,
                            dragPayloadProvider = null,
                            onClick = {
                                if (selectedIds.isNotEmpty()) onToggleSelect(photo)
                                else onPhotoClick(photo)
                            },
                            onLongClick = { onToggleSelect(photo) },
                            onReorderCommit = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GallerySelectionBottomBar(
    selectedItems: List<FileItem>,
    allSelected: Boolean,
    isInsideCustomAlbum: Boolean,
    includeNavBarPadding: Boolean = true,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onAddToAlbum: () -> Unit,
    onSetCover: () -> Unit,
    onRemoveFromAlbum: () -> Unit,
    onAction: (FileActionId, List<FileItem>) -> Unit
) {
    val d = ArcTheme.dimens
    val accentTint = MaterialTheme.colorScheme.primary
    val totalBytes = remember(selectedItems) { selectedItems.sumOf { it.sizeBytes } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .then(if (includeNavBarPadding) Modifier.navigationBarsPadding() else Modifier)
            .testTag("gallery_selection_bar"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
        Column(
            modifier = Modifier
                .widthIn(max = d.maxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${selectedItems.size} selected · ${FormatUtils.formatBytes(totalBytes)}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onSelectAll) {
                        Icon(Icons.Filled.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (allSelected) "All Selected" else "Select All")
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable(onClick = onClear)
                            .testTag("gallery_clear_selection"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear selection",
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isInsideCustomAlbum) {
                    GalleryActionChip(
                        icon = Icons.Filled.RemoveCircleOutline,
                        label = "Remove",
                        tint = accentTint,
                        modifier = Modifier.weight(1f).testTag("gallery_action_remove_from_album"),
                        onClick = onRemoveFromAlbum
                    )
                    if (selectedItems.size == 1) {
                        GalleryActionChip(
                            icon = Icons.Filled.Image,
                            label = "Set Cover",
                            tint = accentTint,
                            modifier = Modifier.weight(1f).testTag("gallery_action_set_cover"),
                            onClick = onSetCover
                        )
                    }
                } else {
                    GalleryActionChip(
                        icon = Icons.Filled.Collections,
                        label = "Album",
                        tint = accentTint,
                        highlighted = true,
                        modifier = Modifier.weight(1.15f).testTag("gallery_action_add_to_album"),
                        onClick = onAddToAlbum
                    )
                }

                GalleryActionChip(
                    icon = Icons.Filled.Share,
                    label = "Share",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).testTag("gallery_action_share"),
                    onClick = { onAction(FileActionId.SHARE, selectedItems) }
                )

                if (selectedItems.size == 1) {
                    GalleryActionChip(
                        icon = Icons.Filled.Info,
                        label = "Info",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.9f).testTag("gallery_action_info"),
                        onClick = { onAction(FileActionId.INFO, selectedItems) }
                    )
                }

                GalleryActionChip(
                    icon = Icons.Filled.DeleteOutline,
                    label = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(0.9f).testTag("gallery_action_delete"),
                    onClick = { onAction(FileActionId.DELETE, selectedItems) }
                )
            }
        }
    }
}

@Composable
private fun GalleryActionChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) tint.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GalleryEmptyPlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String?,
    onSecondaryClick: () -> Unit
) {
    val d = ArcTheme.dimens
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("gallery_empty_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 310.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (secondaryLabel != null) {
                    OutlinedButton(
                        onClick = onSecondaryClick,
                        shape = RoundedCornerShape(d.pillRadius)
                    ) {
                        Text(secondaryLabel)
                    }
                }
                Button(
                    onClick = onPrimaryClick,
                    shape = RoundedCornerShape(d.pillRadius)
                ) {
                    Text(primaryLabel)
                }
            }
        }
    }
}
