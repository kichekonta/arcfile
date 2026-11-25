package com.example.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Isolated Coil/thumbnail cache buckets so each Library section and browser preview stores thumbnails
 * in its own directory and can be inspected or cleared independently in Cache Manager.
 */
enum class CacheBucket(
    val id: String,
    val relativeDir: String,
    val title: String,
    val subtitle: String,
    val maxBytes: Long
) {
    GALLERY_IMAGES(
        id = "gallery_images",
        relativeDir = "library_thumbs/images",
        title = "Images Gallery Thumbnails",
        subtitle = "Cached thumbnails for Images Library & Albums",
        maxBytes = 48L * 1024L * 1024L
    ),
    MOVIE_VIDEOS(
        id = "movie_videos",
        relativeDir = "library_thumbs/movies",
        title = "Movies & Video Thumbnails",
        subtitle = "Extracted video frames for Movies Library & Albums",
        maxBytes = 64L * 1024L * 1024L
    ),
    BROWSER_MEDIA(
        id = "browser_media",
        relativeDir = "arc_image_cache",
        title = "General File Previews",
        subtitle = "Folder & SAF media previews in File Browser",
        maxBytes = 48L * 1024L * 1024L
    )
}

@Immutable
data class CacheBucketStats(
    val bucket: CacheBucket,
    val sizeBytes: Long = 0L,
    val fileCount: Int = 0
)

enum class StorageDomainGroup(
    val id: String,
    val label: String,
    val subtitle: String
) {
    LIBRARY(
        id = "library",
        label = "Library Cache & Albums",
        subtitle = "Per-section thumbnails and virtual album collections"
    ),
    PLUGINS(
        id = "plugins",
        label = "Plugins & Viewers",
        subtitle = "Isolated cache and user state per viewer or player plugin"
    ),
    FOLDERS_COMMON(
        id = "folders_common",
        label = "Folders & Common Data",
        subtitle = "Browser previews, temp staging, folder layouts, and shortcuts"
    )
}

enum class StorageDataKind(val badgeLabel: String) {
    REGENERABLE_CACHE("Cache"),
    USER_METADATA("App Data")
}

enum class ManagedStorageId(
    val id: String,
    val group: StorageDomainGroup,
    val title: String,
    val subtitle: String,
    val dataKind: StorageDataKind,
    val itemUnitLabel: String
) {
    LIB_IMAGES_THUMBS(
        id = "lib_images_thumbs",
        group = StorageDomainGroup.LIBRARY,
        title = "Images Gallery Thumbnails",
        subtitle = "Cached grid thumbnails in library_thumbs/images",
        dataKind = StorageDataKind.REGENERABLE_CACHE,
        itemUnitLabel = "files"
    ),
    LIB_MOVIES_THUMBS(
        id = "lib_movies_thumbs",
        group = StorageDomainGroup.LIBRARY,
        title = "Movies & Video Thumbnails",
        subtitle = "Extracted video poster frames in library_thumbs/movies",
        dataKind = StorageDataKind.REGENERABLE_CACHE,
        itemUnitLabel = "files"
    ),
    LIB_IMAGES_ALBUMS(
        id = "lib_images_albums",
        group = StorageDomainGroup.LIBRARY,
        title = "Images Custom Albums",
        subtitle = "Virtual image album metadata (original photos stay untouched)",
        dataKind = StorageDataKind.USER_METADATA,
        itemUnitLabel = "albums"
    ),
    LIB_MOVIES_ALBUMS(
        id = "lib_movies_albums",
        group = StorageDomainGroup.LIBRARY,
        title = "Movies Custom Albums",
        subtitle = "Virtual video album metadata (original videos stay untouched)",
        dataKind = StorageDataKind.USER_METADATA,
        itemUnitLabel = "albums"
    ),
    COMMON_BROWSER_PREVIEWS(
        id = "common_browser_previews",
        group = StorageDomainGroup.FOLDERS_COMMON,
        title = "Folder & Browser Previews",
        subtitle = "Directory image, APK icon & SAF preview cache (RAM + disk)",
        dataKind = StorageDataKind.REGENERABLE_CACHE,
        itemUnitLabel = "files"
    ),
    COMMON_STAGING_TEMP(
        id = "common_staging_temp",
        group = StorageDomainGroup.FOLDERS_COMMON,
        title = "Temp Open & APK Installer Cache",
        subtitle = "Temporary SAF open staging & APK installer extracts",
        dataKind = StorageDataKind.REGENERABLE_CACHE,
        itemUnitLabel = "files"
    ),
    COMMON_FOLDER_LAYOUTS(
        id = "common_folder_layouts",
        group = StorageDomainGroup.FOLDERS_COMMON,
        title = "Folder Layouts & Custom Sort Order",
        subtitle = "Saved view modes, grid columns & drag-drop sequences per folder",
        dataKind = StorageDataKind.USER_METADATA,
        itemUnitLabel = "folders"
    ),
    COMMON_DEFAULT_APPS(
        id = "common_default_apps",
        group = StorageDomainGroup.FOLDERS_COMMON,
        title = "Saved Default Open Apps",
        subtitle = "Remembered built-in vs external handler rules per file type",
        dataKind = StorageDataKind.USER_METADATA,
        itemUnitLabel = "rules"
    ),
    COMMON_BOOKMARKS(
        id = "common_bookmarks",
        group = StorageDomainGroup.FOLDERS_COMMON,
        title = "Folder Bookmarks",
        subtitle = "Pinned quick-access shortcuts on Home",
        dataKind = StorageDataKind.USER_METADATA,
        itemUnitLabel = "pins"
    ),
    COMMON_FILE_TAGS(
        id = "common_file_tags",
        group = StorageDomainGroup.FOLDERS_COMMON,
        title = "File Tags & Color Labels",
        subtitle = "Tag links and custom tags (original files stay untouched)",
        dataKind = StorageDataKind.USER_METADATA,
        itemUnitLabel = "tags"
    )
}

/**
 * Extensible descriptor for every viewer, player, or utility plugin in Arc Files.
 * New plugins (Video Player, PDF Reader, Text Editor, Music Player, etc.) automatically
 * surface isolated Cache + User Data controls in the Settings Cache Manager by adding an entry here.
 */
@Immutable
data class ArcPluginStorageSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val cacheRelativeDir: String,
    val dataRelativeDir: String,
    val cacheDescription: String,
    val dataDescription: String,
    val isActiveBuiltin: Boolean = true,
    val includeRootTempPrefixes: List<String> = emptyList()
)

object ArcPluginRegistry {
    val plugins: List<ArcPluginStorageSpec>
        get() = com.example.plugins.ArcPluginManager.plugins.map { it.storageSpec }

    fun find(pluginId: String): ArcPluginStorageSpec? =
        com.example.plugins.ArcPluginManager.findById(pluginId)?.storageSpec
}

@Immutable
data class ManagedStorageEntry(
    val id: ManagedStorageId,
    val sizeBytes: Long = 0L,
    val itemCount: Int = 0
)

@Immutable
data class PluginStorageStats(
    val spec: ArcPluginStorageSpec,
    val cacheBytes: Long = 0L,
    val cacheFileCount: Int = 0,
    val dataBytes: Long = 0L,
    val dataFileCount: Int = 0
) {
    val totalBytes: Long get() = cacheBytes + dataBytes
    val totalFileCount: Int get() = cacheFileCount + dataFileCount
}

data class StorageMetadataCounts(
    val imagesAlbumCount: Int = 0,
    val moviesAlbumCount: Int = 0,
    val folderLayoutCount: Int = 0,
    val defaultOpenAppsCount: Int = 0,
    val bookmarksCount: Int = 0,
    val taggedItemsAndCustomTagsCount: Int = 0
)

@Immutable
data class AppStorageSnapshot(
    val entries: List<ManagedStorageEntry> = emptyList(),
    val plugins: List<PluginStorageStats> = emptyList(),
    val lastRefreshedMillis: Long = 0L
) {
    val totalCacheBytes: Long
        get() = entries
            .filter { it.id.dataKind == StorageDataKind.REGENERABLE_CACHE }
            .sumOf { it.sizeBytes } + plugins.sumOf { it.cacheBytes }

    val totalPluginDataBytes: Long
        get() = plugins.sumOf { it.dataBytes }

    val totalLibraryBytes: Long
        get() = entries.filter { it.id.group == StorageDomainGroup.LIBRARY }.sumOf { it.sizeBytes }

    val totalPluginsBytes: Long
        get() = plugins.sumOf { it.totalBytes }

    val totalCommonBytes: Long
        get() = entries.filter { it.id.group == StorageDomainGroup.FOLDERS_COMMON }.sumOf { it.sizeBytes }

    val totalManagedBytes: Long
        get() = totalLibraryBytes + totalPluginsBytes + totalCommonBytes

    fun entryFor(id: ManagedStorageId): ManagedStorageEntry =
        entries.firstOrNull { it.id == id } ?: ManagedStorageEntry(id)
}

object ArcCacheManager {

    private val stagingDirNames = setOf("apk_install_cache", "open_cache")
    private val stagingFilePrefixes = setOf("arc_apk_icon_")

    fun bucketDirectory(context: Context, bucket: CacheBucket): File {
        val dir = File(context.applicationContext.cacheDir, bucket.relativeDir)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun pluginCacheDirectory(context: Context, pluginId: String, createIfMissing: Boolean = true): File {
        val spec = ArcPluginRegistry.find(pluginId)
        val rel = spec?.cacheRelativeDir ?: "plugins/$pluginId"
        val dir = File(context.applicationContext.cacheDir, rel)
        if (createIfMissing && !dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun pluginDataDirectory(context: Context, pluginId: String, createIfMissing: Boolean = true): File {
        val spec = ArcPluginRegistry.find(pluginId)
        val rel = spec?.dataRelativeDir ?: "plugins/$pluginId"
        val dir = File(context.applicationContext.filesDir, rel)
        if (createIfMissing && !dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun inspectAll(context: Context): List<CacheBucketStats> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        CacheBucket.entries.map { bucket ->
            val dir = File(appContext.cacheDir, bucket.relativeDir)
            computeDirStats(bucket, dir)
        }
    }

    suspend fun inspectFullSnapshot(
        context: Context,
        metadata: StorageMetadataCounts
    ): AppStorageSnapshot = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val imgBucket = computeRawStats(File(appContext.cacheDir, CacheBucket.GALLERY_IMAGES.relativeDir))
        val movBucket = computeRawStats(File(appContext.cacheDir, CacheBucket.MOVIE_VIDEOS.relativeDir))
        val browserBucket = computeRawStats(File(appContext.cacheDir, CacheBucket.BROWSER_MEDIA.relativeDir))
        val stagingStats = computeStagingStats(appContext.cacheDir)

        val pluginStats = com.example.plugins.ArcPluginManager.plugins.map { plugin ->
            val spec = plugin.storageSpec
            val cacheDir = File(appContext.cacheDir, spec.cacheRelativeDir)
            val dataDir = File(appContext.filesDir, spec.dataRelativeDir)
            val cacheBase = computeRawStats(cacheDir)
            val extraTemp = computePrefixStats(appContext.cacheDir, spec.includeRootTempPrefixes)
            val dataBase = computeRawStats(dataDir)
            val extraHookMetrics = runCatching { plugin.inspectExtraStorage(appContext) }.getOrNull()
            PluginStorageStats(
                spec = spec,
                cacheBytes = cacheBase.first + extraTemp.first + (extraHookMetrics?.cacheBytes ?: 0L),
                cacheFileCount = cacheBase.second + extraTemp.second + (extraHookMetrics?.cacheItems ?: 0),
                dataBytes = dataBase.first + (extraHookMetrics?.dataBytes ?: 0L),
                dataFileCount = dataBase.second + (extraHookMetrics?.dataItems ?: 0)
            )
        }

        val entries = listOf(
            ManagedStorageEntry(
                id = ManagedStorageId.LIB_IMAGES_THUMBS,
                sizeBytes = imgBucket.first,
                itemCount = imgBucket.second
            ),
            ManagedStorageEntry(
                id = ManagedStorageId.LIB_MOVIES_THUMBS,
                sizeBytes = movBucket.first,
                itemCount = movBucket.second
            ),
            ManagedStorageEntry(
                id = ManagedStorageId.LIB_IMAGES_ALBUMS,
                sizeBytes = 0L,
                itemCount = metadata.imagesAlbumCount
            ),
            ManagedStorageEntry(
                id = ManagedStorageId.LIB_MOVIES_ALBUMS,
                sizeBytes = 0L,
                itemCount = metadata.moviesAlbumCount
            ),
            ManagedStorageEntry(
                id = ManagedStorageId.COMMON_BROWSER_PREVIEWS,
                sizeBytes = browserBucket.first,
                itemCount = browserBucket.second
            ),
            ManagedStorageEntry(
                id = ManagedStorageId.COMMON_STAGING_TEMP,
                sizeBytes = stagingStats.first,
                itemCount = stagingStats.second
            ),
            ManagedStorageEntry(
                id = ManagedStorageId.COMMON_FOLDER_LAYOUTS,
                sizeBytes = 0L,
                itemCount = metadata.folderLayoutCount
            ),
            ManagedStorageEntry(
                id = ManagedStorageId.COMMON_DEFAULT_APPS,
                sizeBytes = 0L,
                itemCount = metadata.defaultOpenAppsCount
            ),
            ManagedStorageEntry(
                id = ManagedStorageId.COMMON_BOOKMARKS,
                sizeBytes = 0L,
                itemCount = metadata.bookmarksCount
            ),
            ManagedStorageEntry(
                id = ManagedStorageId.COMMON_FILE_TAGS,
                sizeBytes = 0L,
                itemCount = metadata.taggedItemsAndCustomTagsCount
            )
        )

        AppStorageSnapshot(
            entries = entries,
            plugins = pluginStats,
            lastRefreshedMillis = System.currentTimeMillis()
        )
    }

    suspend fun clearBucket(context: Context, bucket: CacheBucket): Long = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val dir = File(appContext.cacheDir, bucket.relativeDir)
        val beforeBytes = computeDirStats(bucket, dir).sizeBytes
        runCatching { ArcImageLoader.clearBucketCaches(appContext, bucket) }
        if (bucket == CacheBucket.BROWSER_MEDIA || bucket == CacheBucket.MOVIE_VIDEOS) {
            runCatching { MediaThumbnailLoader.clearMemoryCaches() }
        }
        clearDirContents(dir)
        beforeBytes
    }

    suspend fun clearStagingTempCache(context: Context): Long = withContext(Dispatchers.IO) {
        val cacheRoot = context.applicationContext.cacheDir
        val before = computeStagingStats(cacheRoot).first
        stagingDirNames.forEach { name ->
            val sub = File(cacheRoot, name)
            if (sub.exists()) runCatching { sub.deleteRecursively() }
        }
        cacheRoot.listFiles()?.forEach { child ->
            if (stagingFilePrefixes.any { child.name.startsWith(it) }) {
                runCatching {
                    if (child.isDirectory) child.deleteRecursively() else child.delete()
                }
            }
        }
        before
    }

    suspend fun clearPluginCache(context: Context, pluginId: String): Long = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val plugin = com.example.plugins.ArcPluginManager.findById(pluginId)
        val spec = plugin?.storageSpec
        val dir = pluginCacheDirectory(appContext, pluginId, createIfMissing = false)
        var freed = computeRawStats(dir).first
        clearDirContents(dir, recreate = dir.exists())
        if (spec != null && spec.includeRootTempPrefixes.isNotEmpty()) {
            freed += computePrefixStats(appContext.cacheDir, spec.includeRootTempPrefixes).first
            appContext.cacheDir.listFiles()?.forEach { child ->
                if (spec.includeRootTempPrefixes.any { child.name.startsWith(it) }) {
                    runCatching {
                        if (child.isDirectory) child.deleteRecursively() else child.delete()
                    }
                }
            }
        }
        freed += runCatching { plugin?.onClearCache(appContext) ?: 0L }.getOrDefault(0L)
        freed
    }

    suspend fun clearPluginData(context: Context, pluginId: String): Long = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val plugin = com.example.plugins.ArcPluginManager.findById(pluginId)
        val dir = pluginDataDirectory(appContext, pluginId, createIfMissing = false)
        var freed = computeRawStats(dir).first
        clearDirContents(dir, recreate = dir.exists())
        freed += runCatching { plugin?.onClearUserData(appContext) ?: 0L }.getOrDefault(0L)
        freed
    }

    /**
     * Safely clears all regenerable caches (Library thumbnails, Browser previews, Staging temp files,
     * and Plugin caches) WITHOUT deleting user metadata (Albums, Bookmarks, Folder Layouts, Plugin user state).
     */
    suspend fun clearAllRegenerableCaches(context: Context): Long = withContext(Dispatchers.IO) {
        var totalFreed = 0L
        for (bucket in CacheBucket.entries) {
            totalFreed += clearBucket(context, bucket)
        }
        totalFreed += clearStagingTempCache(context)
        for (plugin in ArcPluginRegistry.plugins) {
            totalFreed += clearPluginCache(context, plugin.id)
        }
        runCatching { MediaThumbnailLoader.clearMemoryCaches() }
        totalFreed
    }

    suspend fun clearAll(context: Context): Long = clearAllRegenerableCaches(context)

    private fun computeStagingStats(cacheRoot: File): Pair<Long, Int> {
        if (!cacheRoot.exists() || !cacheRoot.isDirectory) return 0L to 0
        var totalBytes = 0L
        var totalFiles = 0
        stagingDirNames.forEach { name ->
            val sub = File(cacheRoot, name)
            val stats = computeRawStats(sub)
            totalBytes += stats.first
            totalFiles += stats.second
        }
        val extra = computePrefixStats(cacheRoot, stagingFilePrefixes.toList())
        return (totalBytes + extra.first) to (totalFiles + extra.second)
    }

    private fun computePrefixStats(parentDir: File, prefixes: List<String>): Pair<Long, Int> {
        if (prefixes.isEmpty() || !parentDir.exists() || !parentDir.isDirectory) return 0L to 0
        var bytes = 0L
        var count = 0
        parentDir.listFiles()?.forEach { child ->
            if (prefixes.any { child.name.startsWith(it) }) {
                if (child.isDirectory) {
                    val sub = computeRawStats(child)
                    bytes += sub.first
                    count += sub.second
                } else {
                    bytes += child.length()
                    count++
                }
            }
        }
        return bytes to count
    }

    private fun clearDirContents(dir: File, recreate: Boolean = true) {
        runCatching {
            if (dir.exists()) {
                dir.listFiles()?.forEach { child ->
                    if (child.isDirectory) child.deleteRecursively() else child.delete()
                }
            }
            if (recreate) {
                dir.mkdirs()
            }
        }
    }

    private fun computeDirStats(bucket: CacheBucket, root: File): CacheBucketStats {
        val (bytes, count) = computeRawStats(root)
        return CacheBucketStats(bucket = bucket, sizeBytes = bytes, fileCount = count)
    }

    private fun computeRawStats(root: File): Pair<Long, Int> {
        if (!root.exists() || !root.isDirectory) {
            return 0L to 0
        }
        var totalBytes = 0L
        var count = 0
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                } else {
                    val name = child.name
                    if (name != "journal" && !name.startsWith("journal.")) {
                        count++
                    }
                    totalBytes += child.length()
                }
            }
        }
        return totalBytes to count
    }
}
