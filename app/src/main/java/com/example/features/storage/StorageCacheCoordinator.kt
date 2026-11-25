package com.example.features.storage

import android.app.Application
import com.example.data.ArcCacheManager
import com.example.data.CacheBucket
import com.example.data.FormatUtils
import com.example.data.LibrarySection
import com.example.data.ManagedStorageId
import com.example.data.PreferencesRepository
import com.example.data.StorageMetadataCounts
import com.example.data.db.AlbumRepository
import com.example.data.db.FileTagRepository
import com.example.data.db.FolderLayoutRepository
import com.example.plugins.ArcPluginManager
import com.example.ui.ExplorerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Feature-scoped coordinator managing Storage Cache inspection, domain buckets,
 * Library virtual album metadata resets, and per-plugin cache vs data clearing.
 */
class StorageCacheCoordinator(
    private val app: Application,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<ExplorerUiState>,
    private val albumRepo: AlbumRepository,
    private val folderLayoutRepo: FolderLayoutRepository,
    private val prefsRepo: PreferencesRepository,
    private val fileTagRepo: FileTagRepository,
    private val onLayoutsReset: () -> Unit,
    private val onMessage: (String) -> Unit
) {

    fun buildStorageMetadataCounts(state: ExplorerUiState): StorageMetadataCounts =
        StorageMetadataCounts(
            imagesAlbumCount = state.libraryState.customAlbumsFor(LibrarySection.IMAGES).size,
            moviesAlbumCount = state.libraryState.customAlbumsFor(LibrarySection.MOVIES).size,
            folderLayoutCount = state.folderLayouts.size,
            defaultOpenAppsCount = state.prefs.parsedDefaultOpenApps().size,
            bookmarksCount = state.prefs.parsedBookmarks().size,
            taggedItemsAndCustomTagsCount = state.allTags.count { !it.isSystemDefault } + state.itemTagsByPath.size
        )

    suspend fun inspectAndPublishStorageStats() {
        val meta = buildStorageMetadataCounts(uiState.value)
        val stats = ArcCacheManager.inspectAll(app)
        val snapshot = ArcCacheManager.inspectFullSnapshot(app, meta)
        uiState.update { it.copy(cacheStats = stats, storageSnapshot = snapshot) }
    }

    fun refreshCacheStats() {
        scope.launch {
            inspectAndPublishStorageStats()
        }
    }

    fun clearCacheBucket(bucket: CacheBucket) {
        scope.launch {
            val freed = ArcCacheManager.clearBucket(app, bucket)
            inspectAndPublishStorageStats()
            val freedText = if (freed > 0L) FormatUtils.formatBytes(freed) else "0 B"
            onMessage("Cleared ${bucket.title} ($freedText)")
        }
    }

    fun clearManagedStorageEntry(id: ManagedStorageId) {
        scope.launch {
            val message = when (id) {
                ManagedStorageId.LIB_IMAGES_THUMBS -> {
                    val freed = ArcCacheManager.clearBucket(app, CacheBucket.GALLERY_IMAGES)
                    "Cleared Images thumbnails (${FormatUtils.formatBytes(freed)})"
                }
                ManagedStorageId.LIB_MOVIES_THUMBS -> {
                    val freed = ArcCacheManager.clearBucket(app, CacheBucket.MOVIE_VIDEOS)
                    "Cleared Movies thumbnails (${FormatUtils.formatBytes(freed)})"
                }
                ManagedStorageId.LIB_IMAGES_ALBUMS -> {
                    val removed = albumRepo.clearAlbumsForSection(LibrarySection.IMAGES)
                    "Cleared $removed Images album(s) (original photos kept)"
                }
                ManagedStorageId.LIB_MOVIES_ALBUMS -> {
                    val removed = albumRepo.clearAlbumsForSection(LibrarySection.MOVIES)
                    "Cleared $removed Movies album(s) (original videos kept)"
                }
                ManagedStorageId.COMMON_BROWSER_PREVIEWS -> {
                    val freed = ArcCacheManager.clearBucket(app, CacheBucket.BROWSER_MEDIA)
                    "Cleared browser & APK previews (${FormatUtils.formatBytes(freed)})"
                }
                ManagedStorageId.COMMON_STAGING_TEMP -> {
                    val freed = ArcCacheManager.clearStagingTempCache(app)
                    "Cleared temp open & installer cache (${FormatUtils.formatBytes(freed)})"
                }
                ManagedStorageId.COMMON_FOLDER_LAYOUTS -> {
                    val count = folderLayoutRepo.clearAllLayouts()
                    onLayoutsReset()
                    "Reset $count saved folder layout(s)"
                }
                ManagedStorageId.COMMON_DEFAULT_APPS -> {
                    prefsRepo.clearAllDefaultOpenApps()
                    "Cleared saved Default Open Apps rules"
                }
                ManagedStorageId.COMMON_BOOKMARKS -> {
                    prefsRepo.clearAllBookmarks()
                    "Cleared saved Home bookmarks"
                }
                ManagedStorageId.COMMON_FILE_TAGS -> {
                    fileTagRepo.resetTagsAndAssignments()
                    "Reset file tag assignments and custom labels"
                }
            }
            inspectAndPublishStorageStats()
            onMessage(message)
        }
    }

    fun clearPluginCache(pluginId: String) {
        scope.launch {
            val freed = ArcCacheManager.clearPluginCache(app, pluginId)
            inspectAndPublishStorageStats()
            val title = ArcPluginManager.findById(pluginId)?.storageSpec?.title ?: pluginId
            val freedText = if (freed > 0L) FormatUtils.formatBytes(freed) else "0 B"
            onMessage("Cleared $title cache ($freedText)")
        }
    }

    fun clearPluginData(pluginId: String) {
        scope.launch {
            val freed = ArcCacheManager.clearPluginData(app, pluginId)
            inspectAndPublishStorageStats()
            val title = ArcPluginManager.findById(pluginId)?.storageSpec?.title ?: pluginId
            val freedText = if (freed > 0L) FormatUtils.formatBytes(freed) else "0 B"
            onMessage("Cleared $title data ($freedText)")
        }
    }

    fun clearAllThumbnailCaches() {
        scope.launch {
            val freed = ArcCacheManager.clearAllRegenerableCaches(app)
            inspectAndPublishStorageStats()
            val freedText = if (freed > 0L) FormatUtils.formatBytes(freed) else "0 B"
            onMessage("Cleared all regenerable caches ($freedText)")
        }
    }
}
