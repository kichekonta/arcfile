package com.example.features.library

import com.example.data.BreadcrumbNode
import com.example.data.FileItem
import com.example.data.GalleryPanel
import com.example.data.LibrarySection
import com.example.data.PhotoAlbum
import com.example.data.TabDestination
import com.example.data.db.AlbumRepository
import com.example.data.db.HybridAlbumCatalog
import com.example.ui.ExplorerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Feature-scoped coordinator owning Images & Movies gallery panels, hybrid virtual + device
 * folder album catalogs, album dialogs, and section sub-filters.
 */
class GalleryLibraryCoordinator(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<ExplorerUiState>,
    private val albumRepo: AlbumRepository,
    private val onOpenFolderDestination: (
        title: String,
        destination: TabDestination.Directory,
        matchPredicate: (com.example.data.ExplorerTab) -> Boolean
    ) -> Unit,
    private val onMessage: (String) -> Unit
) {

    fun observeLibraryAlbums() {
        scope.launch {
            val indexedImagesFlow = uiState
                .map { state -> state.allIndexedFiles.filter { it.isImage } }
                .distinctUntilChanged()
            val indexedVideosFlow = uiState
                .map { state -> state.allIndexedFiles.filter { it.isVideo } }
                .distinctUntilChanged()
            combine(
                albumRepo.observeAlbums(),
                albumRepo.observeAllAlbumImages(),
                indexedImagesFlow,
                indexedVideosFlow
            ) { albums, refs, images, videos ->
                val imagesCatalog = AlbumRepository.buildHybridCatalog(
                    customEntities = albums,
                    crossRefs = refs,
                    allIndexedImages = images,
                    mediaSection = LibrarySection.IMAGES
                )
                val moviesCatalog = AlbumRepository.buildHybridCatalog(
                    customEntities = albums,
                    crossRefs = refs,
                    allIndexedImages = videos,
                    mediaSection = LibrarySection.MOVIES
                )
                imagesCatalog to moviesCatalog
            }
                .flowOn(Dispatchers.Default)
                .collect { (imagesCatalog, moviesCatalog) ->
                    uiState.update { state ->
                        val curLib = state.libraryState
                        fun resolveActive(section: LibrarySection, cat: HybridAlbumCatalog): String? {
                            val existingId = curLib.activeAlbumIdFor(section) ?: return null
                            val exists = cat.customAlbums.any { it.id == existingId } ||
                                cat.deviceAlbums.any { it.id == existingId }
                            return if (exists) existingId else null
                        }
                        val nextImagesActive = resolveActive(LibrarySection.IMAGES, imagesCatalog)
                        val nextMoviesActive = resolveActive(LibrarySection.MOVIES, moviesCatalog)
                        state.copy(
                            libraryState = curLib.copy(
                                activeAlbumId = nextImagesActive,
                                customAlbums = imagesCatalog.customAlbums,
                                deviceAlbums = imagesCatalog.deviceAlbums,
                                customAlbumItemsById = imagesCatalog.customAlbumItemsById,
                                deviceAlbumItemsById = imagesCatalog.deviceAlbumItemsById,
                                activeAlbumIdsBySection = curLib.activeAlbumIdsBySection +
                                    (LibrarySection.IMAGES to nextImagesActive) +
                                    (LibrarySection.MOVIES to nextMoviesActive),
                                customAlbumsBySection = mapOf(
                                    LibrarySection.IMAGES to imagesCatalog.customAlbums,
                                    LibrarySection.MOVIES to moviesCatalog.customAlbums
                                ),
                                deviceAlbumsBySection = mapOf(
                                    LibrarySection.IMAGES to imagesCatalog.deviceAlbums,
                                    LibrarySection.MOVIES to moviesCatalog.deviceAlbums
                                ),
                                customAlbumItemsBySection = mapOf(
                                    LibrarySection.IMAGES to imagesCatalog.customAlbumItemsById,
                                    LibrarySection.MOVIES to moviesCatalog.customAlbumItemsById
                                ),
                                deviceAlbumItemsBySection = mapOf(
                                    LibrarySection.IMAGES to imagesCatalog.deviceAlbumItemsById,
                                    LibrarySection.MOVIES to moviesCatalog.deviceAlbumItemsById
                                )
                            )
                        )
                    }
                }
        }
    }

    fun selectGalleryPanel(panel: GalleryPanel, section: LibrarySection = LibrarySection.IMAGES) {
        uiState.update { state ->
            val lib = state.libraryState
            state.copy(
                selectedIds = emptySet(),
                libraryState = lib.copy(
                    galleryPanel = if (section == LibrarySection.IMAGES) panel else lib.galleryPanel,
                    activeAlbumId = if (section == LibrarySection.IMAGES) null else lib.activeAlbumId,
                    galleryPanelsBySection = lib.galleryPanelsBySection + (section to panel),
                    activeAlbumIdsBySection = lib.activeAlbumIdsBySection + (section to null)
                )
            )
        }
    }

    fun openPhotoAlbum(albumId: String, section: LibrarySection = LibrarySection.IMAGES) {
        uiState.update { state ->
            val lib = state.libraryState
            val targetPanel = if (albumId.startsWith("device:")) {
                GalleryPanel.FOLDERS
            } else {
                GalleryPanel.ALBUMS
            }
            state.copy(
                selectedIds = emptySet(),
                libraryState = lib.copy(
                    galleryPanel = if (section == LibrarySection.IMAGES) targetPanel else lib.galleryPanel,
                    activeAlbumId = if (section == LibrarySection.IMAGES) albumId else lib.activeAlbumId,
                    galleryPanelsBySection = lib.galleryPanelsBySection + (section to targetPanel),
                    activeAlbumIdsBySection = lib.activeAlbumIdsBySection + (section to albumId)
                )
            )
        }
    }

    fun closePhotoAlbum(section: LibrarySection = LibrarySection.IMAGES) {
        uiState.update { state ->
            val lib = state.libraryState
            state.copy(
                selectedIds = emptySet(),
                libraryState = lib.copy(
                    activeAlbumId = if (section == LibrarySection.IMAGES) null else lib.activeAlbumId,
                    activeAlbumIdsBySection = lib.activeAlbumIdsBySection + (section to null)
                )
            )
        }
    }

    fun openCreateAlbumDialog(
        initialItems: List<FileItem> = emptyList(),
        section: LibrarySection = LibrarySection.IMAGES
    ) {
        uiState.update { state ->
            state.copy(
                libraryState = state.libraryState.copy(
                    dialogSection = section,
                    showCreateAlbumDialog = true,
                    createAlbumInitialItems = initialItems,
                    addToAlbumTargets = null
                )
            )
        }
    }

    fun dismissCreateAlbumDialog() {
        uiState.update { state ->
            state.copy(
                libraryState = state.libraryState.copy(
                    showCreateAlbumDialog = false,
                    createAlbumInitialItems = emptyList()
                )
            )
        }
    }

    fun createPhotoAlbum(
        name: String,
        initialItems: List<FileItem> = emptyList(),
        section: LibrarySection = LibrarySection.IMAGES
    ) {
        val noun = if (section == LibrarySection.MOVIES) "video(s)" else "photo(s)"
        scope.launch {
            albumRepo.createAlbum(name, initialItems, mediaSection = section)
                .onSuccess { created ->
                    uiState.update { state ->
                        val lib = state.libraryState
                        state.copy(
                            selectedIds = emptySet(),
                            libraryState = lib.copy(
                                showCreateAlbumDialog = false,
                                createAlbumInitialItems = emptyList(),
                                galleryPanel = if (section == LibrarySection.IMAGES) GalleryPanel.ALBUMS else lib.galleryPanel,
                                activeAlbumId = if (section == LibrarySection.IMAGES) created.id else lib.activeAlbumId,
                                galleryPanelsBySection = lib.galleryPanelsBySection + (section to GalleryPanel.ALBUMS),
                                activeAlbumIdsBySection = lib.activeAlbumIdsBySection + (section to created.id)
                            ),
                            snackbarMessage = if (initialItems.isEmpty()) {
                                "Created album '${created.name}'"
                            } else {
                                "Created '${created.name}' with ${initialItems.size} $noun"
                            }
                        )
                    }
                }
                .onFailure { onMessage(it.localizedMessage ?: "Unable to create album") }
        }
    }

    fun openRenameAlbumDialog(album: PhotoAlbum) {
        if (album.isDeviceFolder) return
        uiState.update { state ->
            state.copy(
                libraryState = state.libraryState.copy(
                    dialogSection = album.mediaSection,
                    renameAlbumTarget = album
                )
            )
        }
    }

    fun dismissRenameAlbumDialog() {
        uiState.update { state ->
            state.copy(libraryState = state.libraryState.copy(renameAlbumTarget = null))
        }
    }

    fun renamePhotoAlbum(albumId: String, newName: String) {
        scope.launch {
            albumRepo.renameAlbum(albumId, newName)
                .onSuccess {
                    uiState.update { state ->
                        state.copy(
                            libraryState = state.libraryState.copy(renameAlbumTarget = null),
                            snackbarMessage = "Renamed album to '${newName.trim()}'"
                        )
                    }
                }
                .onFailure { onMessage(it.localizedMessage ?: "Rename failed") }
        }
    }

    fun deletePhotoAlbum(album: PhotoAlbum) {
        if (album.isDeviceFolder) return
        val noun = if (album.mediaSection == LibrarySection.MOVIES) "videos" else "photos"
        scope.launch {
            albumRepo.deleteAlbum(album.id)
            uiState.update { state ->
                val lib = state.libraryState
                val sectionActive = lib.activeAlbumIdFor(album.mediaSection)
                val nextSectionActive = if (sectionActive == album.id) null else sectionActive
                val nextCompatActive = if (album.mediaSection == LibrarySection.IMAGES && lib.activeAlbumId == album.id) {
                    null
                } else {
                    lib.activeAlbumId
                }
                state.copy(
                    selectedIds = emptySet(),
                    libraryState = lib.copy(
                        activeAlbumId = nextCompatActive,
                        activeAlbumIdsBySection = lib.activeAlbumIdsBySection + (album.mediaSection to nextSectionActive)
                    ),
                    snackbarMessage = "Deleted album '${album.name}' (original $noun kept)"
                )
            }
        }
    }

    fun setPhotoAlbumCover(albumId: String, image: FileItem) {
        scope.launch {
            albumRepo.setAlbumCover(albumId, image)
            onMessage("Updated album cover")
        }
    }

    fun openAddToAlbumDialog(items: List<FileItem>, section: LibrarySection = LibrarySection.IMAGES) {
        if (items.isEmpty()) return
        uiState.update { state ->
            state.copy(
                libraryState = state.libraryState.copy(
                    dialogSection = section,
                    addToAlbumTargets = items
                )
            )
        }
    }

    fun dismissAddToAlbumDialog() {
        uiState.update { state ->
            state.copy(
                libraryState = state.libraryState.copy(addToAlbumTargets = null)
            )
        }
    }

    fun addImagesToAlbums(albumIds: Set<String>, images: List<FileItem>) {
        if (albumIds.isEmpty() || images.isEmpty()) return
        val noun = if (images.any { it.isVideo }) "video(s)" else "photo(s)"
        scope.launch {
            albumIds.forEach { id -> albumRepo.addImagesToAlbum(id, images) }
            uiState.update { state ->
                state.copy(
                    selectedIds = emptySet(),
                    libraryState = state.libraryState.copy(addToAlbumTargets = null),
                    snackbarMessage = "Added ${images.size} $noun to ${albumIds.size} album(s)"
                )
            }
        }
    }

    fun openPickImagesForAlbum(albumId: String, section: LibrarySection = LibrarySection.IMAGES) {
        uiState.update { state ->
            state.copy(
                libraryState = state.libraryState.copy(
                    dialogSection = section,
                    pickImagesForAlbumId = albumId
                )
            )
        }
    }

    fun dismissPickImagesForAlbum() {
        uiState.update { state ->
            state.copy(libraryState = state.libraryState.copy(pickImagesForAlbumId = null))
        }
    }

    fun addImagesToSingleAlbum(albumId: String, images: List<FileItem>) {
        if (images.isEmpty()) return
        val noun = if (images.any { it.isVideo }) "video(s)" else "photo(s)"
        scope.launch {
            albumRepo.addImagesToAlbum(albumId, images)
            uiState.update { state ->
                state.copy(
                    libraryState = state.libraryState.copy(pickImagesForAlbumId = null),
                    snackbarMessage = "Added ${images.size} $noun to album"
                )
            }
        }
    }

    fun removeImagesFromAlbum(albumId: String, images: List<FileItem>) {
        if (images.isEmpty()) return
        val noun = if (images.any { it.isVideo }) "video(s)" else "photo(s)"
        scope.launch {
            albumRepo.removeImagesFromAlbum(albumId, images)
            uiState.update { state ->
                state.copy(
                    selectedIds = emptySet(),
                    snackbarMessage = "Removed ${images.size} $noun from album"
                )
            }
        }
    }

    fun openDeviceAlbumFolder(album: PhotoAlbum) {
        if (!album.isDeviceFolder || album.folderPathOrUri.isBlank()) return
        val isSaf = album.coverIsSaf
        val dest = TabDestination.Directory(
            title = album.name,
            rootId = "album_dir_${album.folderPathOrUri.hashCode()}",
            currentPathOrUri = album.folderPathOrUri,
            isSaf = isSaf,
            breadcrumbs = listOf(BreadcrumbNode(album.name, album.folderPathOrUri, isSaf))
        )
        onOpenFolderDestination(
            album.name,
            dest
        ) { tab ->
            (tab.destination as? TabDestination.Directory)?.currentPathOrUri == album.folderPathOrUri
        }
    }

    fun setLibrarySubFilter(section: LibrarySection, filterId: String) {
        uiState.update { state ->
            state.copy(
                selectedIds = emptySet(),
                libraryState = state.libraryState.copy(
                    subFiltersBySection = state.libraryState.subFiltersBySection + (section to filterId)
                )
            )
        }
    }
}
