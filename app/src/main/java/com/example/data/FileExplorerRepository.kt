package com.example.data

import android.content.ClipData
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class FileExplorerRepository(private val context: Context) {

    private val archiveEngine by lazy { ArchiveEngine(context) }
    private val trashLock = Any()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val backgroundScanDispatcher = Dispatchers.IO.limitedParallelism(2)

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            true
        }
    }

    fun buildAllFilesAccessIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        }
    }

    fun getWorkspaceRoot(): File {
        val ext = context.getExternalFilesDir(null)
        return (ext ?: context.filesDir).apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun loadLocalDrivesFast(): List<DriveInfo> = withContext(Dispatchers.IO) {
        val drives = ArrayList<DriveInfo>(4)

        // 1. Primary Internal Storage stats via StatFs
        val dataDir = Environment.getDataDirectory()
        val dataStat = runCatching { StatFs(dataDir.absolutePath) }.getOrNull()
        val totalInternal = dataStat?.totalBytes ?: 0L
        val freeInternal = dataStat?.availableBytes ?: 0L
        val extStorageDir = Environment.getExternalStorageDirectory()

        drives.add(
            DriveInfo(
                id = "drive_internal",
                name = "Internal Storage",
                subtitle = "/storage/emulated/0",
                pathOrUri = extStorageDir.absolutePath,
                type = DriveType.INTERNAL,
                totalBytes = totalInternal,
                freeBytes = freeInternal,
                isSaf = false
            )
        )

        // 2. Any additional removable storage volumes reported by StorageManager
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        if (storageManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storageManager.storageVolumes
                .filter { !it.isPrimary && it.directory != null && it.state == Environment.MEDIA_MOUNTED }
                .forEachIndexed { idx, vol ->
                    val dir = vol.directory ?: return@forEachIndexed
                    if (!dir.exists()) return@forEachIndexed
                    val stat = runCatching { StatFs(dir.absolutePath) }.getOrNull()
                    drives.add(
                        DriveInfo(
                            id = "drive_vol_$idx",
                            name = vol.getDescription(context)?.takeIf { it.isNotBlank() } ?: "External Storage",
                            subtitle = dir.absolutePath,
                            pathOrUri = dir.absolutePath,
                            type = DriveType.EXTERNAL_VOLUME,
                            totalBytes = stat?.totalBytes ?: 0L,
                            freeBytes = stat?.availableBytes ?: 0L,
                            isSaf = false
                        )
                    )
                }
        }
        drives
    }

    suspend fun loadDrives(mountedSafEntries: Set<String>): List<DriveInfo> = withContext(Dispatchers.IO) {
        val drives = loadLocalDrivesFast().toMutableList()
        if (mountedSafEntries.isEmpty()) return@withContext drives

        val primary = drives.firstOrNull { it.type == DriveType.INTERNAL }
        val totalInternal = primary?.totalBytes ?: 0L
        val freeInternal = primary?.freeBytes ?: 0L

        val readablePersistedUris = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri }
            .toHashSet()
        mountedSafEntries.forEach { entry ->
            val parts = entry.split("|", limit = 2)
            val uriStr = parts.getOrNull(0) ?: return@forEach
            val label = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Mounted Folder"
            val uri = Uri.parse(uriStr)
            if (uri in readablePersistedUris) {
                drives.add(
                    DriveInfo(
                        id = "saf_${uriStr.hashCode()}",
                        name = label,
                        subtitle = "SAF Mounted Drive",
                        pathOrUri = uriStr,
                        type = DriveType.SAF_TREE,
                        totalBytes = totalInternal,
                        freeBytes = freeInternal,
                        isSaf = true
                    )
                )
            }
        }

        drives
    }

    suspend fun listDirectory(
        pathOrUri: String,
        isSaf: Boolean,
        showHidden: Boolean,
        sortField: SortField,
        ascending: Boolean,
        foldersFirst: Boolean = true,
        showFolderCounts: Boolean = true,
        customOrderKeys: List<String> = emptyList()
    ): DirectoryLoadState = withContext(Dispatchers.IO) {
        if (isSaf) {
            listSafDirectory(pathOrUri, showHidden, sortField, ascending, foldersFirst, customOrderKeys)
        } else {
            listLocalDirectory(pathOrUri, showHidden, sortField, ascending, foldersFirst, showFolderCounts, customOrderKeys)
        }
    }

    private suspend fun listLocalDirectory(
        path: String,
        showHidden: Boolean,
        sortField: SortField,
        ascending: Boolean,
        foldersFirst: Boolean = true,
        showFolderCounts: Boolean = true,
        customOrderKeys: List<String> = emptyList()
    ): DirectoryLoadState {
        val allFilesGranted = hasAllFilesAccess()
        if (!allFilesGranted) {
            resolveCoveringSafUri(path)?.let { safUri ->
                return listSafDirectory(safUri.toString(), showHidden, sortField, ascending, foldersFirst, customOrderKeys)
            }
        }
        val dir = File(path)
        if (!dir.exists()) {
            return DirectoryLoadState.Error("Directory does not exist")
        }
        val children = dir.listFiles()
        if (children == null) {
            return DirectoryLoadState.RestrictedRequiresSaf(path)
        }
        val scopedRestricted = !allFilesGranted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            isSharedExternalStoragePath(path)
        val hasPersistedExternalSaf = !allFilesGranted && scopedRestricted &&
            context.contentResolver.persistedUriPermissions.any {
                it.isReadPermission && it.uri.authority == "com.android.externalstorage.documents"
            }

        // For large folders (120+ entries), defer per-subfolder child counting to an async pass
        // so initial load of 1,000+ items renders immediately without blocking.
        val countChildrenInline = showFolderCounts && children.size <= LARGE_DIR_INLINE_COUNT_LIMIT
        val mapped = ArrayList<FileItem>(children.size)
        val ctx = currentCoroutineContext()
        for (idx in children.indices) {
            if ((idx and 0xFF) == 0) ctx.ensureActive()
            val child = children[idx]
            val name = child.name
            if (!showHidden && name.startsWith(".")) continue
            val isDir = child.isDirectory
            val countAllowed = isDir && countChildrenInline &&
                (!scopedRestricted || (hasPersistedExternalSaf && resolveCoveringSafUri(child.absolutePath) != null))
            mapped.add(
                fileToItemFast(
                    file = child,
                    parentPath = path,
                    isDir = isDir,
                    computeChildCount = countAllowed
                )
            )
        }

        return DirectoryLoadState.Loaded(
            items = sortItems(mapped, sortField, ascending, foldersFirst, customOrderKeys),
            isScopedStorageLimited = scopedRestricted,
            localPathForSaf = if (scopedRestricted) path else null
        )
    }

    suspend fun countSubfolderChildren(
        folders: List<FileItem>,
        maxFolders: Int = 500
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        if (folders.isEmpty()) return@withContext emptyMap()
        val allGranted = hasAllFilesAccess()
        val scopedRestricted = !allGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val counts = HashMap<String, Int>(minOf(folders.size, maxFolders))
        val ctx = currentCoroutineContext()
        var processed = 0
        for (folder in folders) {
            if (processed >= maxFolders) break
            if ((processed and 0x1F) == 0) ctx.ensureActive()
            if (!folder.isDirectory || folder.isSafUri) continue
            if (scopedRestricted && isSharedExternalStoragePath(folder.pathOrUri) &&
                resolveCoveringSafUri(folder.pathOrUri) == null
            ) {
                continue
            }
            val size = File(folder.pathOrUri).list()?.size
            if (size != null) {
                counts[folder.id] = size
            }
            processed++
        }
        counts
    }

    private fun listSafDirectory(
        documentUriString: String,
        showHidden: Boolean,
        sortField: SortField,
        ascending: Boolean,
        foldersFirst: Boolean = true,
        customOrderKeys: List<String> = emptyList()
    ): DirectoryLoadState {
        return try {
            val uri = Uri.parse(documentUriString)
            val treeDocId = extractSafDocumentId(uri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, treeDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            val items = mutableListOf<FileItem>()
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (idIdx < 0) return@use

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx) ?: continue
                    val rawName = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val name = rawName?.takeIf { it.isNotBlank() }
                        ?: docId.substringAfterLast('/').substringAfterLast(':').ifBlank { "Unnamed" }
                    if (!showHidden && name.startsWith(".")) continue
                    val mime = (if (mimeIdx >= 0) cursor.getString(mimeIdx) else null)
                        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                        ?: "application/octet-stream"
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val modified = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                    val ext = if (isDir) "" else name.substringAfterLast('.', "").lowercase()
                    items.add(
                        FileItem(
                            id = childUri.toString(),
                            name = name,
                            pathOrUri = childUri.toString(),
                            isDirectory = isDir,
                            sizeBytes = if (isDir) 0L else size,
                            lastModifiedMillis = modified,
                            mimeType = mime,
                            extension = ext,
                            isSafUri = true,
                            parentPathOrUri = documentUriString,
                            childCount = null
                        )
                    )
                }
            }
            DirectoryLoadState.Loaded(sortItems(items, sortField, ascending, foldersFirst, customOrderKeys))
        } catch (e: SecurityException) {
            DirectoryLoadState.RestrictedRequiresSaf(documentUriString)
        } catch (e: Exception) {
            DirectoryLoadState.Error(e.localizedMessage ?: "Failed to read mounted folder")
        }
    }

    suspend fun indexAllAccessibleFiles(
        mountedSafEntries: Set<String>,
        showHidden: Boolean
    ): List<FileItem> = withContext(backgroundScanDispatcher) {
        val allGranted = hasAllFilesAccess()
        val readablePersistedUris = if (mountedSafEntries.isNotEmpty()) {
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri }
                .toHashSet()
        } else emptySet()

        val perSourceResults = coroutineScope {
            val tasks = mutableListOf<kotlinx.coroutines.Deferred<List<FileItem>>>()

            // 1. Workspace scan
            tasks += async {
                val list = ArrayList<FileItem>(64)
                traverseLocal(getWorkspaceRoot(), showHidden, depth = 0, maxDepth = 5, maxFiles = 600) {
                    list.add(it)
                }
                list
            }

            // 2. Shared storage scan
            if (allGranted) {
                val extRoot = Environment.getExternalStorageDirectory()
                if (extRoot.exists() && extRoot.canRead()) {
                    tasks += async {
                        val list = ArrayList<FileItem>(256)
                        traverseLocal(
                            dir = extRoot,
                            showHidden = showHidden,
                            depth = 0,
                            maxDepth = 6,
                            maxFiles = 4000,
                            skipAndroidRestricted = true
                        ) { list.add(it) }
                        list
                    }
                }
            } else {
                val publicDirs = listOf(
                    Environment.DIRECTORY_DCIM,
                    Environment.DIRECTORY_PICTURES,
                    Environment.DIRECTORY_DOWNLOADS,
                    Environment.DIRECTORY_MOVIES,
                    Environment.DIRECTORY_DOCUMENTS,
                    Environment.DIRECTORY_MUSIC
                ).mapNotNull { type ->
                    runCatching { Environment.getExternalStoragePublicDirectory(type) }.getOrNull()
                }.filter { it.exists() && it.canRead() }

                publicDirs.forEach { dir ->
                    tasks += async {
                        val list = ArrayList<FileItem>(128)
                        traverseLocal(dir, showHidden, depth = 0, maxDepth = 4, maxFiles = 600) {
                            list.add(it)
                        }
                        list
                    }
                }
            }

            // 3. Mounted SAF trees scanned concurrently
            mountedSafEntries.forEach { entry ->
                val uriStr = entry.substringBefore("|")
                val uri = Uri.parse(uriStr)
                if (uri in readablePersistedUris) {
                    tasks += async {
                        val list = ArrayList<FileItem>(64)
                        traverseSafTree(uri, extractSafDocumentId(uri), showHidden, depth = 0, maxDepth = 5, maxFiles = 1000) {
                            list.add(it)
                        }
                        list
                    }
                }
            }

            tasks.awaitAll()
        }

        val collected = ArrayList<FileItem>(perSourceResults.sumOf { it.size })
        val seenIds = HashSet<String>(perSourceResults.sumOf { it.size }.coerceAtLeast(16))
        perSourceResults.forEach { batch ->
            batch.forEach { item ->
                if (!item.isDirectory && seenIds.add(item.id)) {
                    collected.add(item)
                }
            }
        }
        collected.sortByDescending { maxOf(it.createdAtMillis, it.lastModifiedMillis) }
        collected
    }

    suspend fun scanAllCompressedFiles(
        mountedSafEntries: Set<String>,
        showHidden: Boolean
    ): List<FileItem> = scanAllMatchingFiles(
        mountedSafEntries = mountedSafEntries,
        showHidden = showHidden,
        maxMatchesPerRoot = 5_000,
        matchesRaw = { ext, mime ->
            ext in FileItem.ARCHIVE_EXTENSIONS || mime.lowercase() in FileItem.ARCHIVE_MIME_TYPES
        },
        matchesItem = { !it.isDirectory && it.isArchive }
    )

    suspend fun scanAllDocuments(
        mountedSafEntries: Set<String>,
        showHidden: Boolean
    ): List<FileItem> = scanAllMatchingFiles(
        mountedSafEntries = mountedSafEntries,
        showHidden = showHidden,
        maxMatchesPerRoot = 8_000,
        matchesRaw = { ext, mime ->
            val lowerMime = mime.lowercase()
            ext in FileItem.DOCUMENT_EXTENSIONS ||
                lowerMime.startsWith("text/") ||
                lowerMime in FileItem.DOCUMENT_MIME_TYPES
        },
        matchesItem = { !it.isDirectory && it.isDocument }
    )

    private suspend fun scanAllMatchingFiles(
        mountedSafEntries: Set<String>,
        showHidden: Boolean,
        maxMatchesPerRoot: Int,
        matchesRaw: (ext: String, mime: String) -> Boolean,
        matchesItem: (FileItem) -> Boolean
    ): List<FileItem> = withContext(backgroundScanDispatcher) {
        val allGranted = hasAllFilesAccess()
        val readablePersistedUris = if (mountedSafEntries.isNotEmpty()) {
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri }
                .toHashSet()
        } else emptySet()

        val localVolumeRoots = buildList<File> {
            add(getWorkspaceRoot())
            runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let { add(it) }
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (storageManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                storageManager.storageVolumes
                    .filter { it.directory != null && it.state == Environment.MEDIA_MOUNTED }
                    .forEach { vol -> vol.directory?.let { add(it) } }
            }
            if (!allGranted) {
                listOf(
                    Environment.DIRECTORY_DOWNLOADS,
                    Environment.DIRECTORY_DOCUMENTS,
                    Environment.DIRECTORY_DCIM,
                    Environment.DIRECTORY_PICTURES,
                    Environment.DIRECTORY_MOVIES,
                    Environment.DIRECTORY_MUSIC
                ).forEach { dirType ->
                    runCatching { Environment.getExternalStoragePublicDirectory(dirType) }
                        .getOrNull()
                        ?.let { add(it) }
                }
            }
        }.filter { it.exists() && it.canRead() }
            .distinctBy { root -> runCatching { root.canonicalPath }.getOrElse { root.absolutePath } }

        val visitedDirPaths = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        val perSourceResults = coroutineScope {
            val tasks = mutableListOf<kotlinx.coroutines.Deferred<List<FileItem>>>()

            localVolumeRoots.forEach { rootDir ->
                tasks += async {
                    val matches = ArrayList<FileItem>(64)
                    traverseLocalMatching(
                        dir = rootDir,
                        showHidden = showHidden,
                        depth = 0,
                        maxDepth = 10,
                        maxVisitedFiles = 60_000,
                        maxMatches = maxMatchesPerRoot,
                        visitedDirPaths = visitedDirPaths,
                        matchesRaw = matchesRaw,
                        matches = matches
                    )
                    matches
                }
            }

            if (!allGranted) {
                tasks += async { queryMediaStoreMatching(showHidden, matchesRaw) }
            }

            mountedSafEntries.forEach { entry ->
                val uriStr = entry.substringBefore("|")
                val uri = Uri.parse(uriStr)
                if (uri in readablePersistedUris) {
                    tasks += async {
                        val matches = ArrayList<FileItem>(64)
                        traverseSafTree(
                            treeUri = uri,
                            docId = extractSafDocumentId(uri),
                            showHidden = showHidden,
                            depth = 0,
                            maxDepth = 7,
                            maxFiles = 10_000
                        ) { item ->
                            if (matchesItem(item)) matches.add(item)
                        }
                        matches
                    }
                }
            }

            tasks.awaitAll()
        }

        val collected = ArrayList<FileItem>(perSourceResults.sumOf { it.size })
        val seenKeys = HashSet<String>(perSourceResults.sumOf { it.size }.coerceAtLeast(16))
        perSourceResults.forEach { batch ->
            batch.forEach { item ->
                if (matchesItem(item)) {
                    val normKey = normalizeTagPath(item.pathOrUri, item.isSafUri)
                    if (seenKeys.add(normKey)) {
                        collected.add(item)
                    }
                }
            }
        }
        collected.sortByDescending { maxOf(it.createdAtMillis, it.lastModifiedMillis) }
        collected
    }

    private fun traverseLocalMatching(
        dir: File,
        showHidden: Boolean,
        depth: Int,
        maxDepth: Int,
        maxVisitedFiles: Int,
        maxMatches: Int,
        visitedDirPaths: MutableSet<String>,
        visitedCount: IntArray = intArrayOf(0),
        matchesRaw: (ext: String, mime: String) -> Boolean,
        matches: MutableList<FileItem>
    ) {
        if (depth > maxDepth || visitedCount[0] >= maxVisitedFiles || matches.size >= maxMatches) return
        val canon = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
        if (!visitedDirPaths.add(canon)) return
        val children = dir.listFiles() ?: return
        val dirPath = dir.absolutePath
        val inAndroidDir = dir.name.equals("Android", ignoreCase = true)
        for (child in children) {
            if (visitedCount[0] >= maxVisitedFiles || matches.size >= maxMatches) break
            val name = child.name
            if (!showHidden && name.startsWith(".")) continue
            if (child.isDirectory) {
                if (inAndroidDir && (name.equals("data", ignoreCase = true) || name.equals("obb", ignoreCase = true))) {
                    continue
                }
                traverseLocalMatching(
                    dir = child,
                    showHidden = showHidden,
                    depth = depth + 1,
                    maxDepth = maxDepth,
                    maxVisitedFiles = maxVisitedFiles,
                    maxMatches = maxMatches,
                    visitedDirPaths = visitedDirPaths,
                    visitedCount = visitedCount,
                    matchesRaw = matchesRaw,
                    matches = matches
                )
            } else {
                visitedCount[0]++
                val ext = name.substringAfterLast('.', "").let {
                    if (it.length == name.length) "" else it.lowercase()
                }
                val mime = resolveMimeFast(ext)
                if (matchesRaw(ext, mime)) {
                    matches.add(fileToItemFast(child, dirPath, isDir = false, computeChildCount = false))
                }
            }
        }
    }

    private fun queryMediaStoreMatching(
        showHidden: Boolean,
        matchesRaw: (ext: String, mime: String) -> Boolean
    ): List<FileItem> {
        val result = ArrayList<FileItem>(64)
        runCatching {
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATA
            )
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val modCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                while (cursor.moveToNext() && result.size < 5_000) {
                    val dataPath = if (dataCol >= 0 && !cursor.isNull(dataCol)) cursor.getString(dataCol) else null
                    val rawName = if (nameCol >= 0 && !cursor.isNull(nameCol)) cursor.getString(nameCol) else null
                    val name = rawName?.takeIf { it.isNotBlank() }
                        ?: dataPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                        ?: continue
                    if (!showHidden && name.startsWith(".")) continue
                    val ext = name.substringAfterLast('.', "").let {
                        if (it.length == name.length) "" else it.lowercase()
                    }
                    val mime = (if (mimeCol >= 0 && !cursor.isNull(mimeCol)) cursor.getString(mimeCol) else null)
                        ?.takeIf { it.isNotBlank() }
                        ?: resolveMimeFast(ext)
                    if (!matchesRaw(ext, mime)) continue

                    val localFile = dataPath?.let { File(it) }
                    if (localFile != null && localFile.exists() && localFile.isFile && localFile.canRead()) {
                        result.add(
                            fileToItemFast(
                                file = localFile,
                                parentPath = localFile.parentFile?.absolutePath ?: localFile.absolutePath,
                                isDir = false,
                                computeChildCount = false
                            )
                        )
                    } else if (idCol >= 0 && !cursor.isNull(idCol)) {
                        val mediaId = cursor.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(collection, mediaId)
                        val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                        val modSec = if (modCol >= 0 && !cursor.isNull(modCol)) cursor.getLong(modCol) else 0L
                        val modMillis = if (modSec > 10_000_000_000L) modSec else modSec * 1000L
                        result.add(
                            FileItem(
                                id = contentUri.toString(),
                                name = name,
                                pathOrUri = contentUri.toString(),
                                isDirectory = false,
                                sizeBytes = size,
                                lastModifiedMillis = modMillis,
                                mimeType = mime,
                                extension = ext,
                                isSafUri = true,
                                parentPathOrUri = localFile?.parentFile?.absolutePath ?: collection.toString(),
                                childCount = null,
                                createdAtMillis = modMillis
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    private fun traverseLocal(
        dir: File,
        showHidden: Boolean,
        depth: Int,
        maxDepth: Int,
        maxFiles: Int,
        skipAndroidRestricted: Boolean = false,
        countRef: IntArray = intArrayOf(0),
        onFile: (FileItem) -> Unit
    ) {
        if (depth > maxDepth || countRef[0] >= maxFiles) return
        val children = dir.listFiles() ?: return
        val dirPath = dir.absolutePath
        for (child in children) {
            if (countRef[0] >= maxFiles) break
            val name = child.name
            if (!showHidden && name.startsWith(".")) continue
            if (child.isDirectory) {
                if (skipAndroidRestricted && depth == 0 && name.equals("Android", ignoreCase = true)) {
                    continue
                }
                traverseLocal(child, showHidden, depth + 1, maxDepth, maxFiles, skipAndroidRestricted, countRef, onFile)
            } else {
                countRef[0]++
                onFile(fileToItemFast(child, dirPath, isDir = false, computeChildCount = false))
            }
        }
    }

    private fun traverseSafTree(
        treeUri: Uri,
        docId: String,
        showHidden: Boolean,
        depth: Int,
        maxDepth: Int,
        maxFiles: Int,
        countRef: IntArray = intArrayOf(0),
        onFile: (FileItem) -> Unit
    ) {
        if (depth > maxDepth || countRef[0] >= maxFiles) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (idIdx < 0) return@use

                val subDirs = mutableListOf<String>()
                while (cursor.moveToNext() && countRef[0] < maxFiles) {
                    val childId = cursor.getString(idIdx) ?: continue
                    val rawName = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val name = rawName?.takeIf { it.isNotBlank() }
                        ?: childId.substringAfterLast('/').substringAfterLast(':').ifBlank { continue }
                    if (!showHidden && name.startsWith(".")) continue
                    val mime = (if (mimeIdx >= 0) cursor.getString(mimeIdx) else null)
                        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                        ?: "application/octet-stream"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        subDirs.add(childId)
                    } else {
                        countRef[0]++
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                        val modified = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L
                        onFile(
                            FileItem(
                                id = childUri.toString(),
                                name = name,
                                pathOrUri = childUri.toString(),
                                isDirectory = false,
                                sizeBytes = size,
                                lastModifiedMillis = modified,
                                mimeType = mime,
                                extension = name.substringAfterLast('.', "").lowercase(),
                                isSafUri = true,
                                parentPathOrUri = treeUri.toString(),
                                childCount = null
                            )
                        )
                    }
                }
                subDirs.forEach { subId ->
                    if (countRef[0] < maxFiles) {
                        traverseSafTree(treeUri, subId, showHidden, depth + 1, maxDepth, maxFiles, countRef, onFile)
                    }
                }
            }
        }
    }

    fun publicDirectoryTypeFor(category: ExplorerCategory): String? = when (category) {
        ExplorerCategory.PHOTOS -> Environment.DIRECTORY_PICTURES
        ExplorerCategory.VIDEOS -> Environment.DIRECTORY_MOVIES
        ExplorerCategory.DOCUMENTS -> Environment.DIRECTORY_DOCUMENTS
        ExplorerCategory.MUSIC -> Environment.DIRECTORY_MUSIC
        ExplorerCategory.DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
        ExplorerCategory.ARCHIVES, ExplorerCategory.OTHER -> null
    }

    fun libraryFolderDisplayTitle(category: ExplorerCategory): String = when (category) {
        ExplorerCategory.PHOTOS -> "Pictures"
        ExplorerCategory.VIDEOS -> "Movies"
        ExplorerCategory.DOCUMENTS -> "Documents"
        ExplorerCategory.MUSIC -> "Music"
        ExplorerCategory.DOWNLOADS -> "Download"
        else -> category.label
    }

    suspend fun loadLibraryFolderSummaries(showHidden: Boolean): Map<ExplorerCategory, CategorySummary> =
        withContext(backgroundScanDispatcher) {
            val folderCategories = listOf(
                ExplorerCategory.PHOTOS,
                ExplorerCategory.VIDEOS,
                ExplorerCategory.DOCUMENTS,
                ExplorerCategory.MUSIC,
                ExplorerCategory.DOWNLOADS
            )
            val extRoot = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
            val results = coroutineScope {
                folderCategories.map { category ->
                    async {
                        val dirType = publicDirectoryTypeFor(category)
                        val publicDir = dirType?.let { type ->
                            runCatching { Environment.getExternalStoragePublicDirectory(type) }.getOrNull()
                                ?: extRoot?.let { File(it, type) }
                        }
                        if (publicDir == null || !publicDir.exists() || !publicDir.canRead()) {
                            return@async category to CategorySummary(category, 0, 0L)
                        }
                        var count = 0
                        var totalBytes = 0L
                        traverseLocal(
                            dir = publicDir,
                            showHidden = showHidden,
                            depth = 0,
                            maxDepth = 4,
                            maxFiles = 1500
                        ) { fileItem ->
                            count++
                            totalBytes += fileItem.sizeBytes
                        }
                        category to CategorySummary(category, count, totalBytes)
                    }
                }.awaitAll()
            }
            val map = java.util.EnumMap<ExplorerCategory, CategorySummary>(ExplorerCategory::class.java)
            ExplorerCategory.entries.forEach { map[it] = CategorySummary(it, 0, 0L) }
            results.forEach { (cat, summary) -> map[cat] = summary }
            map
        }

    fun categorizeFile(item: FileItem): ExplorerCategory {
        val lowerPath = item.pathOrUri.lowercase()
        val inDownloadsDir = lowerPath.contains("/download") ||
            lowerPath.contains("%2fdownload") ||
            lowerPath.contains(":download") ||
            lowerPath.contains("%3adownload")
        return when {
            item.isImage -> ExplorerCategory.PHOTOS
            item.isVideo -> ExplorerCategory.VIDEOS
            item.isAudio -> ExplorerCategory.MUSIC
            inDownloadsDir -> ExplorerCategory.DOWNLOADS
            item.isDocument -> ExplorerCategory.DOCUMENTS
            item.isArchive -> ExplorerCategory.ARCHIVES
            else -> ExplorerCategory.OTHER
        }
    }

    fun sortItems(
        items: List<FileItem>,
        sortField: SortField,
        ascending: Boolean,
        foldersFirst: Boolean = true,
        customOrderKeys: List<String> = emptyList()
    ): List<FileItem> = FileGroupingUtils.sortItems(items, sortField, ascending, foldersFirst, customOrderKeys)

    suspend fun createFolder(parentPathOrUri: String, isSaf: Boolean, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = name.trim()
            require(cleanName.isNotEmpty() && !cleanName.contains("/")) { "Invalid folder name" }
            if (isSaf) {
                val parentUri = Uri.parse(parentPathOrUri)
                val created = DocumentsContract.createDocument(
                    context.contentResolver,
                    normalizeSafDocumentUri(parentUri),
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    cleanName
                )
                requireNotNull(created) { "Could not create folder in mounted drive" }
            } else {
                val target = File(parentPathOrUri, cleanName)
                require(!target.exists()) { "Folder already exists" }
                require(target.mkdirs()) { "Permission denied or unable to create folder" }
            }
            Unit
        }
    }

    suspend fun createTextFile(parentPathOrUri: String, isSaf: Boolean, name: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = name.trim()
            require(cleanName.isNotEmpty() && !cleanName.contains("/")) { "Invalid file name" }
            val ext = cleanName.substringAfterLast('.', "txt").lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "text/plain"
            if (isSaf) {
                val parentUri = Uri.parse(parentPathOrUri)
                val docUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    normalizeSafDocumentUri(parentUri),
                    mime,
                    cleanName
                ) ?: error("Unable to create file in mounted drive")
                context.contentResolver.openOutputStream(docUri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
            } else {
                val target = File(parentPathOrUri, cleanName)
                require(!target.exists()) { "File already exists" }
                target.writeText(content, Charsets.UTF_8)
            }
            Unit
        }
    }

    suspend fun saveTextFile(item: FileItem, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (item.isSafUri) {
                context.contentResolver.openOutputStream(Uri.parse(item.pathOrUri), "wt")?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot write to document")
            } else {
                File(item.pathOrUri).writeText(content, Charsets.UTF_8)
            }
            Unit
        }
    }

    suspend fun readTextPreview(item: FileItem, maxBytes: Int = 262_144): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val stream: InputStream? = if (item.isSafUri) {
                context.contentResolver.openInputStream(Uri.parse(item.pathOrUri))
            } else {
                File(item.pathOrUri).inputStream()
            }
            stream?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var remaining = maxBytes
                while (remaining > 0) {
                    val toRead = minOf(chunk.size, remaining)
                    val n = input.read(chunk, 0, toRead)
                    if (n <= 0) break
                    out.write(chunk, 0, n)
                    remaining -= n
                }
                out.toString(Charsets.UTF_8.name())
            } ?: ""
        }
    }

    suspend fun renameItem(item: FileItem, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = newName.trim()
            require(clean.isNotEmpty() && !clean.contains("/")) { "Invalid name" }
            if (item.isSafUri) {
                val renamed = DocumentsContract.renameDocument(
                    context.contentResolver,
                    Uri.parse(item.pathOrUri),
                    clean
                )
                requireNotNull(renamed) { "Unable to rename SAF item" }
            } else {
                val src = File(item.pathOrUri)
                val dst = File(src.parentFile, clean)
                require(!dst.exists()) { "An item named '$clean' already exists" }
                require(src.renameTo(dst)) { "Rename failed" }
            }
            Unit
        }
    }

    suspend fun batchRenameItems(renames: List<Pair<FileItem, String>>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val active = renames
                .map { (item, name) -> item to name.trim() }
                .filter { (item, clean) -> clean != item.name }
            if (active.isEmpty()) return@runCatching 0

            val seenTargets = HashSet<Pair<String, String>>()
            for ((item, clean) in active) {
                require(clean.isNotEmpty() && !clean.contains("/") && !clean.contains("\\")) {
                    "Invalid filename '$clean'"
                }
                val key = item.parentPathOrUri to clean.lowercase()
                require(seenTargets.add(key)) { "Duplicate target name '$clean'" }
            }

            val localItems = active.filter { !it.first.isSafUri }
            val safItems = active.filter { it.first.isSafUri }
            var renamedCount = 0

            if (localItems.isNotEmpty()) {
                val participantCanonicalPaths = localItems.map { File(it.first.pathOrUri).canonicalPath }.toSet()
                for ((item, clean) in localItems) {
                    val src = File(item.pathOrUri)
                    require(src.exists()) { "'${item.name}' no longer exists" }
                    val dst = File(src.parentFile, clean)
                    if (dst.exists() && dst.canonicalPath !in participantCanonicalPaths) {
                        error("An item named '$clean' already exists")
                    }
                }

                // 2-phase rename via unique temporary names so overlapping swaps (e.g. 1,2,3 -> 0,1,2) never collide
                val batchToken = UUID.randomUUID().toString().take(8)
                val tempMoves = mutableListOf<Triple<File, File, File>>()
                try {
                    for ((idx, pair) in localItems.withIndex()) {
                        val (item, clean) = pair
                        val src = File(item.pathOrUri)
                        val tmp = File(src.parentFile, ".__arc_rn_${batchToken}_${idx}_${src.name}")
                        val dst = File(src.parentFile, clean)
                        require(src.renameTo(tmp)) { "Failed staging '${item.name}'" }
                        tempMoves.add(Triple(src, tmp, dst))
                    }
                    for ((orig, tmp, dst) in tempMoves) {
                        require(tmp.renameTo(dst)) { "Failed renaming '${orig.name}' to '${dst.name}'" }
                        renamedCount++
                    }
                } catch (e: Throwable) {
                    tempMoves.forEach { (orig, tmp, _) ->
                        if (tmp.exists() && !orig.exists()) {
                            runCatching { tmp.renameTo(orig) }
                        }
                    }
                    throw e
                }
            }

            if (safItems.isNotEmpty()) {
                val sourceNames = safItems.map { it.first.parentPathOrUri to it.first.name.lowercase() }.toSet()
                val safToken = UUID.randomUUID().toString().take(6)
                val staged = mutableListOf<Pair<Uri, String>>()
                for ((idx, pair) in safItems.withIndex()) {
                    val (item, clean) = pair
                    val origUri = Uri.parse(item.pathOrUri)
                    if ((item.parentPathOrUri to clean.lowercase()) in sourceNames) {
                        val tmpUri = DocumentsContract.renameDocument(
                            context.contentResolver,
                            origUri,
                            ".__arc_rn_${safToken}_${idx}_${item.name}"
                        ) ?: error("Unable to stage SAF item '${item.name}'")
                        staged.add(tmpUri to clean)
                    } else {
                        DocumentsContract.renameDocument(context.contentResolver, origUri, clean)
                            ?: error("Unable to rename '${item.name}'")
                        renamedCount++
                    }
                }
                for ((tmpUri, finalName) in staged) {
                    DocumentsContract.renameDocument(context.contentResolver, tmpUri, finalName)
                        ?: error("Unable to finalize SAF rename to '$finalName'")
                    renamedCount++
                }
            }

            renamedCount
        }
    }

    suspend fun deleteItems(items: List<FileItem>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var count = 0
            for (item in items) {
                val deleted = if (item.isSafUri) {
                    DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(item.pathOrUri))
                } else {
                    File(item.pathOrUri).deleteRecursively()
                }
                if (deleted) count++
            }
            count
        }
    }

    suspend fun importUrisToTarget(
        uris: List<Uri>,
        targetParentPathOrUri: String,
        isTargetSaf: Boolean
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var imported = 0
            for (srcUri in uris) {
                val displayName = resolveDisplayName(srcUri) ?: "imported_${System.currentTimeMillis()}"
                val mime = context.contentResolver.getType(srcUri)
                    ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(displayName.substringAfterLast('.', "").lowercase())
                    ?: "application/octet-stream"

                context.contentResolver.openInputStream(srcUri)?.use { input ->
                    if (isTargetSaf) {
                        val dstUri = DocumentsContract.createDocument(
                            context.contentResolver,
                            normalizeSafDocumentUri(Uri.parse(targetParentPathOrUri)),
                            mime,
                            displayName
                        )
                        if (dstUri != null) {
                            context.contentResolver.openOutputStream(dstUri)?.use { output ->
                                input.copyTo(output)
                            }
                            imported++
                        }
                    } else {
                        val targetDir = File(targetParentPathOrUri).apply { if (!exists()) mkdirs() }
                        var dstFile = File(targetDir, displayName)
                        if (dstFile.exists()) {
                            val base = displayName.substringBeforeLast('.')
                            val ext = displayName.substringAfterLast('.', "")
                            val suffix = if (ext.isNotEmpty() && base != displayName) ".$ext" else ""
                            dstFile = File(targetDir, "${base}_${System.currentTimeMillis() % 10000}$suffix")
                        }
                        dstFile.outputStream().use { output -> input.copyTo(output) }
                        imported++
                    }
                }
            }
            imported
        }
    }

    private data class TransferTotals(val itemCount: Int, val totalBytes: Long)

    private class TransferTracker(
        private val taskId: String,
        private val operation: TransferOperationType,
        private val sourceLabel: String,
        private val destinationLabel: String,
        private val onProgress: ((FileTransferProgress) -> Unit)?
    ) {
        var totalItems: Int = 0
        var totalBytes: Long = 0L
        var copiedBytes: Long = 0L
        var processedItems: Int = 0
        var currentFileName: String = ""
        private val startTimeMs: Long = System.currentTimeMillis()
        private var lastEmitMs: Long = 0L
        private var smoothedSpeedBps: Long = 0L

        fun emit(status: TransferStatus, force: Boolean = false) {
            val callback = onProgress ?: return
            val now = System.currentTimeMillis()
            if (!force && (now - lastEmitMs) < 75L) return
            lastEmitMs = now

            val elapsedMs = (now - startTimeMs).coerceAtLeast(1L)
            if (copiedBytes > 0L && elapsedMs >= 120L) {
                val avgBps = (copiedBytes * 1000L) / elapsedMs
                smoothedSpeedBps = if (smoothedSpeedBps <= 0L) avgBps else ((smoothedSpeedBps * 3L + avgBps) / 4L)
            }
            val remainingBytes = (totalBytes - copiedBytes).coerceAtLeast(0L)
            val etaMs = if (status == TransferStatus.IN_PROGRESS && smoothedSpeedBps > 0L && remainingBytes > 0L) {
                (remainingBytes * 1000L) / smoothedSpeedBps
            } else if (status == TransferStatus.COMPLETED) {
                0L
            } else null

            callback(
                FileTransferProgress(
                    taskId = taskId,
                    operation = operation,
                    status = status,
                    currentFileName = currentFileName,
                    sourceLabel = sourceLabel,
                    destinationLabel = destinationLabel,
                    processedItems = processedItems,
                    totalItems = totalItems,
                    copiedBytes = copiedBytes,
                    totalBytes = totalBytes,
                    speedBytesPerSec = smoothedSpeedBps,
                    etaMillis = etaMs
                )
            )
        }
    }

    suspend fun copyOrMoveItems(
        items: List<FileItem>,
        targetParentPathOrUri: String,
        isTargetSaf: Boolean,
        isMove: Boolean,
        sourceLabel: String = "",
        destinationLabel: String = "",
        onProgress: ((FileTransferProgress) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val op = if (isMove) TransferOperationType.MOVE else TransferOperationType.COPY
        val tracker = TransferTracker(
            taskId = UUID.randomUUID().toString(),
            operation = op,
            sourceLabel = sourceLabel,
            destinationLabel = destinationLabel,
            onProgress = onProgress
        )
        try {
            tracker.currentFileName = items.firstOrNull()?.name ?: ""
            tracker.totalItems = items.size
            tracker.emit(TransferStatus.PREPARING, force = true)

            // Guard against copying/moving a folder into itself or one of its descendants
            if (!isTargetSaf) {
                val dstCanon = File(targetParentPathOrUri).canonicalPath
                for (item in items) {
                    if (item.isDirectory && !item.isSafUri) {
                        val srcCanon = File(item.pathOrUri).canonicalPath
                        if (dstCanon == srcCanon || dstCanon.startsWith("$srcCanon${File.separator}")) {
                            throw IllegalArgumentException(
                                "Cannot ${if (isMove) "move" else "copy"} '${item.name}' into itself"
                            )
                        }
                    }
                }
            }

            // Pre-scan exact leaf file count and byte size so progress bar and ETA are accurate
            val perItemTotals = items.map { item ->
                currentCoroutineContext().ensureActive()
                measureTransferTotals(item)
            }
            tracker.totalItems = perItemTotals.sumOf { it.itemCount }.coerceAtLeast(items.size)
            tracker.totalBytes = perItemTotals.sumOf { it.totalBytes }
            tracker.emit(TransferStatus.IN_PROGRESS, force = true)

            var topLevelProcessed = 0
            for ((index, item) in items.withIndex()) {
                currentCoroutineContext().ensureActive()
                val itemStats = perItemTotals[index]
                tracker.currentFileName = item.name
                tracker.emit(TransferStatus.IN_PROGRESS, force = true)

                if (isMove && !item.isSafUri && !isTargetSaf) {
                    val srcFile = File(item.pathOrUri)
                    val dstDir = File(targetParentPathOrUri).apply { if (!exists()) mkdirs() }
                    val sameParent = srcFile.parentFile?.canonicalPath == dstDir.canonicalPath
                    if (sameParent) {
                        tracker.copiedBytes += itemStats.totalBytes
                        tracker.processedItems += itemStats.itemCount
                        tracker.emit(TransferStatus.IN_PROGRESS, force = true)
                        topLevelProcessed++
                        continue
                    }
                    val directTarget = File(dstDir, item.name)
                    if (!directTarget.exists() && srcFile.renameTo(directTarget)) {
                        tracker.copiedBytes += itemStats.totalBytes
                        tracker.processedItems += itemStats.itemCount
                        tracker.emit(TransferStatus.IN_PROGRESS, force = true)
                        topLevelProcessed++
                        continue
                    }
                }

                if (item.isDirectory) {
                    copyDirectoryRecursive(item, targetParentPathOrUri, isTargetSaf, tracker)
                    if (isMove) {
                        deleteItemRaw(item)
                    }
                    topLevelProcessed++
                } else {
                    copySingleFile(item, targetParentPathOrUri, isTargetSaf, tracker)
                    if (isMove) {
                        deleteItemRaw(item)
                    }
                    topLevelProcessed++
                }
            }

            tracker.processedItems = tracker.totalItems
            tracker.copiedBytes = tracker.totalBytes
            tracker.emit(TransferStatus.COMPLETED, force = true)
            Result.success(topLevelProcessed)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun measureTransferTotals(item: FileItem): TransferTotals {
        currentCoroutineContext().ensureActive()
        if (!item.isDirectory) {
            val bytes = if (!item.isSafUri) {
                File(item.pathOrUri).length().coerceAtLeast(item.sizeBytes.coerceAtLeast(0L))
            } else {
                item.sizeBytes.coerceAtLeast(0L)
            }
            return TransferTotals(itemCount = 1, totalBytes = bytes)
        }
        val children = if (item.isSafUri) {
            when (val res = listSafDirectory(item.pathOrUri, showHidden = true, SortField.NAME, true)) {
                is DirectoryLoadState.Loaded -> res.items
                else -> emptyList()
            }
        } else {
            File(item.pathOrUri).listFiles()?.map { fileToItem(it) } ?: emptyList()
        }
        if (children.isEmpty()) {
            return TransferTotals(itemCount = 1, totalBytes = 0L)
        }
        var count = 0
        var bytes = 0L
        for (child in children) {
            val childTotals = measureTransferTotals(child)
            count += childTotals.itemCount
            bytes += childTotals.totalBytes
        }
        return TransferTotals(itemCount = count.coerceAtLeast(1), totalBytes = bytes)
    }

    private fun deleteItemRaw(item: FileItem): Boolean {
        return if (item.isSafUri) {
            DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(item.pathOrUri))
        } else {
            File(item.pathOrUri).deleteRecursively()
        }
    }

    private suspend fun copyDirectoryRecursive(
        dirItem: FileItem,
        targetParentPathOrUri: String,
        isTargetSaf: Boolean,
        tracker: TransferTracker
    ) {
        currentCoroutineContext().ensureActive()
        val newTargetPathOrUri = if (isTargetSaf) {
            val created = DocumentsContract.createDocument(
                context.contentResolver,
                normalizeSafDocumentUri(Uri.parse(targetParentPathOrUri)),
                DocumentsContract.Document.MIME_TYPE_DIR,
                dirItem.name
            ) ?: return
            created.toString()
        } else {
            val dst = File(targetParentPathOrUri, dirItem.name).apply { mkdirs() }
            dst.absolutePath
        }

        val children = if (dirItem.isSafUri) {
            when (val res = listSafDirectory(dirItem.pathOrUri, showHidden = true, SortField.NAME, true)) {
                is DirectoryLoadState.Loaded -> res.items
                else -> emptyList()
            }
        } else {
            File(dirItem.pathOrUri).listFiles()?.map { fileToItem(it) } ?: emptyList()
        }

        if (children.isEmpty()) {
            tracker.processedItems++
            tracker.emit(TransferStatus.IN_PROGRESS, force = true)
            return
        }

        for (child in children) {
            currentCoroutineContext().ensureActive()
            if (child.isDirectory) {
                copyDirectoryRecursive(child, newTargetPathOrUri, isTargetSaf, tracker)
            } else {
                copySingleFile(child, newTargetPathOrUri, isTargetSaf, tracker)
            }
        }
    }

    private fun resolveUniqueTargetFile(targetDir: File, fileName: String, extension: String): File {
        var candidate = File(targetDir, fileName)
        if (!candidate.exists()) return candidate
        val base = if (extension.isNotEmpty() && fileName.endsWith(".$extension", ignoreCase = true)) {
            fileName.dropLast(extension.length + 1)
        } else {
            fileName.substringBeforeLast('.', fileName)
        }
        val suffix = if (extension.isNotEmpty() && base != fileName) ".$extension" else ""
        candidate = File(targetDir, "${base}_copy$suffix")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(targetDir, "${base}_copy_${counter}$suffix")
            counter++
        }
        return candidate
    }

    private suspend fun copySingleFile(
        item: FileItem,
        targetParentPathOrUri: String,
        isTargetSaf: Boolean,
        tracker: TransferTracker
    ) {
        currentCoroutineContext().ensureActive()
        tracker.currentFileName = item.name
        tracker.emit(TransferStatus.IN_PROGRESS, force = true)

        val input: InputStream? = if (item.isSafUri) {
            context.contentResolver.openInputStream(Uri.parse(item.pathOrUri))
        } else {
            File(item.pathOrUri).inputStream()
        }
        var createdSafUri: Uri? = null
        var createdLocalFile: File? = null
        try {
            input?.use { inStream ->
                val output: OutputStream? = if (isTargetSaf) {
                    val created = DocumentsContract.createDocument(
                        context.contentResolver,
                        normalizeSafDocumentUri(Uri.parse(targetParentPathOrUri)),
                        item.mimeType,
                        item.name
                    )
                    createdSafUri = created
                    created?.let { context.contentResolver.openOutputStream(it) }
                } else {
                    val targetDir = File(targetParentPathOrUri).apply { if (!exists()) mkdirs() }
                    val dst = resolveUniqueTargetFile(targetDir, item.name, item.extension)
                    createdLocalFile = dst
                    dst.outputStream()
                }
                output?.use { outStream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = inStream.read(buffer)
                        if (read < 0) break
                        outStream.write(buffer, 0, read)
                        tracker.copiedBytes += read.toLong()
                        tracker.emit(TransferStatus.IN_PROGRESS, force = false)
                    }
                    outStream.flush()
                }
            }
            tracker.processedItems++
            tracker.emit(TransferStatus.IN_PROGRESS, force = true)
        } catch (t: Throwable) {
            // Remove incomplete target artifact if cancelled or failed mid-stream
            runCatching { createdLocalFile?.delete() }
            createdSafUri?.let { uri ->
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            }
            throw t
        }
    }

    fun getShareOrViewUri(item: FileItem): Uri {
        return if (item.isSafUri) {
            Uri.parse(item.pathOrUri)
        } else {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(item.pathOrUri)
            )
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(true)
        } else {
            true
        }
    }

    fun buildUnknownAppSourcesIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun prepareApkInstallUri(item: FileItem): Uri = withContext(Dispatchers.IO) {
        if (!item.isSafUri) {
            return@withContext FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(item.pathOrUri)
            )
        }
        val stageDir = File(context.cacheDir, "apk_install_cache").apply {
            if (!exists()) mkdirs()
            val cutoff = System.currentTimeMillis() - 3_600_000L
            listFiles()?.forEach { old ->
                if (old.lastModified() < cutoff) runCatching { old.delete() }
            }
        }
        val safeName = item.name.replace(Regex("""[^a-zA-Z0-9._-]"""), "_").ifBlank { "package.apk" }
        val targetFile = File(stageDir, "${item.lastModifiedMillis}_$safeName")
        if (!targetFile.exists() || targetFile.length() == 0L) {
            context.contentResolver.openInputStream(Uri.parse(item.pathOrUri))?.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to read APK from mounted storage")
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
    }

    fun buildApkInstallIntent(
        item: FileItem,
        preparedUri: Uri? = null,
        targetPackageName: String? = null
    ): Intent {
        val uri = preparedUri ?: getShareOrViewUri(item)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri(item.name, uri)
            if (!targetPackageName.isNullOrBlank()) {
                setPackage(targetPackageName)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }

    suspend fun prepareViewOrShareUri(item: FileItem): Uri = withContext(Dispatchers.IO) {
        if (!item.isSafUri) {
            return@withContext getShareOrViewUri(item)
        }
        val rawUri = Uri.parse(item.pathOrUri)
        if (item.sizeBytes > 128L * 1024L * 1024L) {
            return@withContext rawUri
        }
        runCatching {
            val stageDir = File(context.cacheDir, "open_cache").apply {
                if (!exists()) mkdirs()
                val cutoff = System.currentTimeMillis() - 3_600_000L
                listFiles()?.forEach { old ->
                    if (old.lastModified() < cutoff) runCatching { old.delete() }
                }
            }
            val safeName = item.name.replace(Regex("""[^a-zA-Z0-9._-]"""), "_").ifBlank { "shared_file" }
            val hashPrefix = item.pathOrUri.hashCode().toUInt().toString(16)
            val targetFile = File(stageDir, "${hashPrefix}_${item.lastModifiedMillis}_$safeName")
            if (!targetFile.exists() || targetFile.length() == 0L) {
                context.contentResolver.openInputStream(rawUri)?.use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext rawUri
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
        }.getOrDefault(rawUri)
    }

    fun buildViewIntent(
        item: FileItem,
        mimeOverride: String? = null,
        packageName: String? = null,
        activityName: String? = null,
        resolvedUri: Uri? = null
    ): Intent {
        val uri = resolvedUri ?: getShareOrViewUri(item)
        val inferred = if (item.mimeType.isBlank() || item.mimeType == "application/octet-stream") {
            resolveMimeFast(item.extension)
        } else {
            item.mimeType
        }
        val effectiveMime = mimeOverride?.takeIf { it.isNotBlank() } ?: inferred
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, effectiveMime)
            clipData = ClipData.newRawUri(item.name, uri)
            if (!packageName.isNullOrBlank() && !activityName.isNullOrBlank()) {
                component = ComponentName(packageName, activityName)
            } else if (!packageName.isNullOrBlank()) {
                setPackage(packageName)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun queryCompatibleApps(item: FileItem, mimeOverride: String? = null): List<ResolvedAppOption> {
        val pm = context.packageManager
        fun queryForMime(mime: String?): List<ResolvedAppOption> {
            val intent = runCatching { buildViewIntent(item, mimeOverride = mime) }.getOrNull()
                ?: return emptyList()
            val resolveInfos = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(
                        intent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                }
            }.getOrDefault(emptyList())
            val seen = LinkedHashMap<String, ResolvedAppOption>()
            for (info in resolveInfos) {
                val act = info.activityInfo ?: continue
                if (!act.enabled) continue
                val pkg = act.packageName ?: continue
                val cls = act.name ?: continue
                if (pkg == context.packageName) continue
                val key = "$pkg/$cls"
                if (key in seen) continue
                val label = runCatching { info.loadLabel(pm)?.toString() }.getOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: pkg
                seen[key] = ResolvedAppOption(
                    packageName = pkg,
                    activityName = cls,
                    label = label
                )
            }
            return seen.values.toList()
        }

        val primary = queryForMime(mimeOverride)
        if (primary.isNotEmpty()) return primary
        if (mimeOverride == null) {
            val baseMime = if (item.mimeType.isBlank() || item.mimeType == "application/octet-stream") {
                resolveMimeFast(item.extension)
            } else {
                item.mimeType
            }
            val familyFallback = when {
                baseMime.startsWith("text/") ||
                    baseMime == "application/json" ||
                    baseMime == "application/xml" ||
                    baseMime == "application/javascript" -> "text/plain"
                baseMime.startsWith("audio/") && baseMime != "audio/*" -> "audio/*"
                baseMime.startsWith("video/") && baseMime != "video/*" -> "video/*"
                baseMime.startsWith("image/") && baseMime != "image/*" -> "image/*"
                else -> null
            }
            if (familyFallback != null && familyFallback != baseMime) {
                val familyMatches = queryForMime(familyFallback)
                if (familyMatches.isNotEmpty()) return familyMatches
            }
        }
        return emptyList()
    }

    fun hasAnyWildcardApp(item: FileItem): Boolean {
        return queryCompatibleApps(item, mimeOverride = "*/*").isNotEmpty()
    }

    fun isAppInstalledAndHandles(item: FileItem, packageName: String, activityName: String): Boolean {
        val pm = context.packageManager
        val intent = runCatching {
            buildViewIntent(item, packageName = packageName, activityName = activityName)
        }.getOrNull() ?: return false
        val matches = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        }.getOrDefault(emptyList())
        if (matches.isNotEmpty()) return true
        // Also check package-level filter in case an activity alias or component renamed on app update
        val pkgIntent = runCatching {
            buildViewIntent(item, packageName = packageName)
        }.getOrNull() ?: return false
        val pkgMatches = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    pkgIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(pkgIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        }.getOrDefault(emptyList())
        return pkgMatches.isNotEmpty()
    }

    fun buildShareIntent(items: List<FileItem>): Intent {
        val filesOnly = items.filter { !it.isDirectory }
        if (filesOnly.size == 1) {
            val single = filesOnly.first()
            val uri = getShareOrViewUri(single)
            return Intent(Intent.ACTION_SEND).apply {
                type = single.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(single.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val uris = ArrayList(filesOnly.map { getShareOrViewUri(it) })
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            if (uris.isNotEmpty()) {
                clipData = ClipData.newRawUri(filesOnly.first().name, uris.first()).apply {
                    for (i in 1 until uris.size) {
                        addItem(ClipData.Item(uris[i]))
                    }
                }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun resolveSafTreeLabel(treeUri: Uri): String {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: treeUri.lastPathSegment?.substringAfterLast(':') ?: "Mounted Folder"
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    fun isSharedExternalStoragePath(path: String): Boolean {
        val extRoot = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        val clean = path.trimEnd('/')
        if (clean != extRoot && !clean.startsWith("$extRoot/")) return false
        val sandboxPaths = listOfNotNull(
            getWorkspaceRoot().absolutePath.trimEnd('/'),
            context.filesDir.absolutePath.trimEnd('/'),
            context.cacheDir.absolutePath.trimEnd('/'),
            "$extRoot/Android/data/${context.packageName}",
            "$extRoot/Android/obb/${context.packageName}"
        )
        return sandboxPaths.none { clean == it || clean.startsWith("$it/") }
    }

    fun buildSafInitialUri(localPath: String?): Uri? {
        if (localPath.isNullOrBlank()) return null
        val extRoot = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        val clean = localPath.trimEnd('/')
        if (clean != extRoot && !clean.startsWith("$extRoot/")) return null
        val rel = clean.removePrefix(extRoot).trim('/')
        val docId = if (rel.isEmpty()) "primary:" else "primary:$rel"
        return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
    }

    fun resolveCoveringSafUri(localPath: String): Uri? {
        val extRoot = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        val clean = localPath.trimEnd('/')
        if (clean != extRoot && !clean.startsWith("$extRoot/")) return null
        val relPath = clean.removePrefix(extRoot).trim('/')

        var bestMatch: Pair<Int, Uri>? = null
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && it.uri.authority == "com.android.externalstorage.documents" }
            .forEach { perm ->
                val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(perm.uri) }.getOrNull()
                    ?: return@forEach
                if (!treeDocId.startsWith("primary:")) return@forEach
                val treeRel = treeDocId.removePrefix("primary:").trim('/')
                val covers = treeRel.isEmpty() || relPath == treeRel || relPath.startsWith("$treeRel/")
                if (covers) {
                    val specificity = treeRel.length
                    if (bestMatch == null || specificity > bestMatch!!.first) {
                        val targetDocId = if (relPath.isEmpty()) "primary:" else "primary:$relPath"
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(perm.uri, targetDocId)
                        bestMatch = specificity to docUri
                    }
                }
            }
        return bestMatch?.second
    }

    private fun extractSafDocumentId(uri: Uri): String {
        return runCatching { DocumentsContract.getDocumentId(uri) }
            .getOrElse { DocumentsContract.getTreeDocumentId(uri) }
    }

    private fun normalizeSafDocumentUri(uri: Uri): Uri {
        val docId = extractSafDocumentId(uri)
        return DocumentsContract.buildDocumentUriUsingTree(uri, docId)
    }

    private fun resolveMimeFast(ext: String): String {
        if (ext.isEmpty()) return "application/octet-stream"
        return FAST_MIME_MAP[ext]
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }

    private fun fileToItemFast(
        file: File,
        parentPath: String,
        isDir: Boolean,
        computeChildCount: Boolean
    ): FileItem {
        val name = file.name
        val path = file.absolutePath
        val ext = if (isDir) "" else name.substringAfterLast('.', "").let {
            if (it.length == name.length) "" else it.lowercase()
        }
        val mime = if (isDir) DocumentsContract.Document.MIME_TYPE_DIR else resolveMimeFast(ext)
        val count = if (isDir && computeChildCount) file.list()?.size else null
        val mod = file.lastModified()
        return FileItem(
            id = path,
            name = name,
            pathOrUri = path,
            isDirectory = isDir,
            sizeBytes = if (isDir) 0L else file.length(),
            lastModifiedMillis = mod,
            mimeType = mime,
            extension = ext,
            isSafUri = false,
            parentPathOrUri = parentPath,
            childCount = count,
            createdAtMillis = mod
        )
    }

    private fun fileToItem(file: File, showFolderCounts: Boolean = true): FileItem {
        val isDir = file.isDirectory
        val hideMisleadingCount = isDir &&
            !hasAllFilesAccess() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            isSharedExternalStoragePath(file.absolutePath) &&
            resolveCoveringSafUri(file.absolutePath) == null
        return fileToItemFast(
            file = file,
            parentPath = file.parentFile?.absolutePath ?: file.absolutePath,
            isDir = isDir,
            computeChildCount = isDir && showFolderCounts && !hideMisleadingCount
        )
    }

    companion object {
        private const val LARGE_DIR_INLINE_COUNT_LIMIT = 40

        private val FAST_MIME_MAP = mapOf(
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "webp" to "image/webp",
            "gif" to "image/gif",
            "bmp" to "image/bmp",
            "heic" to "image/heic",
            "heif" to "image/heif",
            "heics" to "image/heic-sequence",
            "heifs" to "image/heif-sequence",
            "hif" to "image/heif",
            "avif" to "image/avif",
            "svg" to "image/svg+xml",
            "ico" to "image/x-icon",
            "mp4" to "video/mp4",
            "mkv" to "video/x-matroska",
            "webm" to "video/webm",
            "mov" to "video/quicktime",
            "avi" to "video/x-msvideo",
            "3gp" to "video/3gpp",
            "mp3" to "audio/mpeg",
            "wav" to "audio/wav",
            "ogg" to "audio/ogg",
            "flac" to "audio/flac",
            "aac" to "audio/aac",
            "m4a" to "audio/mp4",
            "pdf" to "application/pdf",
            "txt" to "text/plain",
            "md" to "text/markdown",
            "json" to "application/json",
            "xml" to "text/xml",
            "csv" to "text/csv",
            "log" to "text/plain",
            "html" to "text/html",
            "css" to "text/css",
            "js" to "application/javascript",
            "kt" to "text/plain",
            "java" to "text/plain",
            "py" to "text/plain",
            "doc" to "application/msword",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xls" to "application/vnd.ms-excel",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "ppt" to "application/vnd.ms-powerpoint",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "zip" to "application/zip",
            "rar" to "application/vnd.rar",
            "7z" to "application/x-7z-compressed",
            "tar" to "application/x-tar",
            "gz" to "application/gzip",
            "tgz" to "application/gzip",
            "bz2" to "application/x-bzip2",
            "tbz" to "application/x-bzip2",
            "tbz2" to "application/x-bzip2",
            "xz" to "application/x-xz",
            "txz" to "application/x-xz",
            "zst" to "application/zstd",
            "lz4" to "application/x-lz4",
            "lzma" to "application/x-lzma",
            "apk" to "application/vnd.android.package-archive",
            "apks" to "application/vnd.android.package-archive",
            "xapk" to "application/vnd.android.package-archive",
            "apkm" to "application/vnd.android.package-archive",
            "aab" to "application/vnd.android.package-archive",
            "jar" to "application/java-archive",
            "war" to "application/java-archive",
            "ear" to "application/java-archive",
            "cab" to "application/vnd.ms-cab-compressed",
            "iso" to "application/x-iso9660-image",
            "opus" to "audio/ogg",
            "m4b" to "audio/mp4",
            "flv" to "video/x-flv",
            "wmv" to "video/x-ms-wmv",
            "ts" to "video/mp2t",
            "m4v" to "video/mp4",
            "epub" to "application/epub+zip",
            "vcf" to "text/x-vcard",
            "ics" to "text/calendar",
            "sh" to "text/plain",
            "yaml" to "text/plain",
            "yml" to "text/plain",
            "ini" to "text/plain",
            "conf" to "text/plain",
            "properties" to "text/plain",
            "toml" to "text/plain",
            "gradle" to "text/plain",
            "kts" to "text/plain",
            "cpp" to "text/plain",
            "c" to "text/plain",
            "h" to "text/plain",
            "rs" to "text/plain",
            "go" to "text/plain",
            "php" to "text/plain",
            "rb" to "text/plain",
            "swift" to "text/plain",
            "srt" to "text/plain",
            "vtt" to "text/vtt",
            "rtf" to "application/rtf",
            "odt" to "application/vnd.oasis.opendocument.text",
            "ods" to "application/vnd.oasis.opendocument.spreadsheet",
            "odp" to "application/vnd.oasis.opendocument.presentation",
            "cbz" to "application/vnd.comicbook+zip",
            "cbr" to "application/vnd.comicbook-rar",
            "cb7" to "application/x-7z-compressed",
            "djvu" to "image/vnd.djvu",
            "m3u" to "audio/x-mpegurl",
            "m3u8" to "application/x-mpegURL",
            "pls" to "audio/x-scpls",
            "mid" to "audio/midi",
            "midi" to "audio/midi",
            "amr" to "audio/amr",
            "wma" to "audio/x-ms-wma",
            "vob" to "video/mpeg",
            "mts" to "video/mp2t",
            "m2ts" to "video/mp2t",
            "torrent" to "application/x-bittorrent"
        )
    }

    private fun calculateDirSizeBounded(dir: File, maxDepth: Int, depth: Int = 0): Long {
        if (!dir.exists() || depth > maxDepth) return 0L
        val children = dir.listFiles() ?: return 0L
        var total = 0L
        for (child in children) {
            total += if (child.isDirectory) calculateDirSizeBounded(child, maxDepth, depth + 1) else child.length()
        }
        return total
    }

    // Recycle Bin (.arc_trash)
    fun getTrashRootPath(): String = getTrashRoot().absolutePath

    private fun getTrashRoot(): File = File(context.filesDir, ".arc_trash").apply {
        if (!exists()) mkdirs()
    }

    private fun getTrashManifestFile(): File = File(getTrashRoot(), "trash_index.json")

    private fun readTrashEntriesInternal(): MutableList<TrashEntry> {
        val manifest = getTrashManifestFile()
        if (!manifest.exists()) return mutableListOf()
        val list = mutableListOf<TrashEntry>()
        var prunedStale = false
        runCatching {
            val arr = JSONArray(manifest.readText(Charsets.UTF_8))
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val trashFile = File(getTrashRoot(), obj.getString("trashFileName"))
                if (trashFile.exists()) {
                    list.add(
                        TrashEntry(
                            id = obj.getString("id"),
                            originalName = obj.getString("originalName"),
                            originalPath = obj.getString("originalPath"),
                            trashFileName = obj.getString("trashFileName"),
                            isDirectory = obj.optBoolean("isDirectory", false),
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            trashedAtMillis = obj.optLong("trashedAtMillis", 0L),
                            isSafOrigin = obj.optBoolean("isSafOrigin", false),
                            safParentUri = obj.optString("safParentUri", ""),
                            mimeType = obj.optString("mimeType", ""),
                            extension = obj.optString("extension", "")
                        )
                    )
                } else {
                    prunedStale = true
                }
            }
        }
        if (prunedStale) {
            runCatching { writeTrashEntriesInternal(list) }
        }
        return list
    }

    private fun writeTrashEntriesInternal(entries: List<TrashEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("originalName", e.originalName)
                    put("originalPath", e.originalPath)
                    put("trashFileName", e.trashFileName)
                    put("isDirectory", e.isDirectory)
                    put("sizeBytes", e.sizeBytes)
                    put("trashedAtMillis", e.trashedAtMillis)
                    put("isSafOrigin", e.isSafOrigin)
                    put("safParentUri", e.safParentUri)
                    put("mimeType", e.mimeType)
                    put("extension", e.extension)
                }
            )
        }
        getTrashManifestFile().writeText(arr.toString(), Charsets.UTF_8)
    }

    private fun notifyMediaScanner(path: String) {
        if (path.isBlank()) return
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        }
    }

    private fun copySafItemToLocalTrash(item: FileItem, targetFile: File): Long? {
        return runCatching {
            if (item.isDirectory) {
                targetFile.mkdirs()
                var totalBytes = 0L
                val children = when (val res = listSafDirectory(item.pathOrUri, showHidden = true, SortField.NAME, true)) {
                    is DirectoryLoadState.Loaded -> res.items
                    else -> emptyList()
                }
                for (child in children) {
                    val childDst = File(targetFile, child.name)
                    val copied = copySafItemToLocalTrash(child, childDst) ?: return null
                    totalBytes += copied
                }
                totalBytes
            } else {
                targetFile.parentFile?.mkdirs()
                var totalBytes = 0L
                val input = context.contentResolver.openInputStream(Uri.parse(item.pathOrUri)) ?: return null
                input.use { inStream ->
                    targetFile.outputStream().use { outStream ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val read = inStream.read(buffer)
                            if (read < 0) break
                            outStream.write(buffer, 0, read)
                            totalBytes += read
                        }
                        outStream.flush()
                    }
                }
                totalBytes
            }
        }.getOrNull()
    }

    private fun restoreLocalTreeToSaf(
        src: File,
        targetParentUri: String,
        targetName: String,
        mimeType: String
    ): Boolean {
        return runCatching {
            val parentUri = normalizeSafDocumentUri(Uri.parse(targetParentUri))
            if (src.isDirectory) {
                val createdDir = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    targetName
                ) ?: return false
                val children = src.listFiles() ?: emptyArray()
                for (child in children) {
                    val childMime = if (child.isDirectory) {
                        DocumentsContract.Document.MIME_TYPE_DIR
                    } else {
                        val ext = child.name.substringAfterLast('.', "").lowercase()
                        resolveMimeFast(ext)
                    }
                    if (!restoreLocalTreeToSaf(child, createdDir.toString(), child.name, childMime)) {
                        return false
                    }
                }
                true
            } else {
                val ext = targetName.substringAfterLast('.', "").lowercase()
                val effectiveMime = mimeType.ifBlank { resolveMimeFast(ext) }
                val created = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    effectiveMime,
                    targetName
                ) ?: return false
                context.contentResolver.openOutputStream(created)?.use { out ->
                    src.inputStream().use { inp -> inp.copyTo(out) }
                } ?: return false
                true
            }
        }.getOrDefault(false)
    }

    suspend fun listTrashEntries(): List<TrashEntry> = withContext(Dispatchers.IO) {
        synchronized(trashLock) {
            readTrashEntriesInternal().sortedByDescending { it.trashedAtMillis }
        }
    }

    suspend fun moveItemsToTrash(items: List<FileItem>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val trashRoot = getTrashRoot()
            val addedEntries = mutableListOf<TrashEntry>()
            val scannedPaths = mutableListOf<String>()

            for (item in items) {
                val id = UUID.randomUUID().toString().take(8)
                val trashName = "${id}_${item.name}"
                val dst = File(trashRoot, trashName)

                if (item.isSafUri) {
                    val copiedBytes = copySafItemToLocalTrash(item, dst)
                    if (copiedBytes != null && dst.exists()) {
                        val deletedSaf = runCatching {
                            DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(item.pathOrUri))
                        }.getOrDefault(false)
                        if (deletedSaf) {
                            addedEntries.add(
                                TrashEntry(
                                    id = id,
                                    originalName = item.name,
                                    originalPath = item.pathOrUri,
                                    trashFileName = trashName,
                                    isDirectory = item.isDirectory,
                                    sizeBytes = copiedBytes.coerceAtLeast(item.sizeBytes),
                                    trashedAtMillis = System.currentTimeMillis(),
                                    isSafOrigin = true,
                                    safParentUri = item.parentPathOrUri,
                                    mimeType = item.mimeType,
                                    extension = item.extension
                                )
                            )
                        } else {
                            dst.deleteRecursively()
                        }
                    } else {
                        dst.deleteRecursively()
                    }
                } else {
                    val src = File(item.pathOrUri)
                    if (!src.exists()) continue
                    val size = if (src.isDirectory) calculateDirSizeBounded(src, maxDepth = 6) else src.length()
                    val moved = if (src.renameTo(dst)) {
                        true
                    } else {
                        val copied = src.copyRecursively(dst, overwrite = true)
                        if (copied) {
                            src.deleteRecursively()
                        } else {
                            dst.deleteRecursively()
                            false
                        }
                    }
                    if (moved) {
                        scannedPaths.add(src.absolutePath)
                        addedEntries.add(
                            TrashEntry(
                                id = id,
                                originalName = item.name,
                                originalPath = item.pathOrUri,
                                trashFileName = trashName,
                                isDirectory = item.isDirectory,
                                sizeBytes = size,
                                trashedAtMillis = System.currentTimeMillis(),
                                isSafOrigin = false,
                                safParentUri = "",
                                mimeType = item.mimeType,
                                extension = item.extension
                            )
                        )
                    }
                }
            }

            if (addedEntries.isNotEmpty()) {
                synchronized(trashLock) {
                    val entries = readTrashEntriesInternal()
                    entries.addAll(addedEntries)
                    writeTrashEntriesInternal(entries)
                }
                scannedPaths.forEach(::notifyMediaScanner)
            }
            addedEntries.size
        }
    }

    private fun restoreSingleEntryInternal(entry: TrashEntry): String? {
        val src = File(getTrashRoot(), entry.trashFileName)
        require(src.exists()) { "Trashed item no longer exists" }

        if (entry.isSafOrigin && entry.safParentUri.isNotBlank()) {
            val ok = restoreLocalTreeToSaf(
                src = src,
                targetParentUri = entry.safParentUri,
                targetName = entry.originalName,
                mimeType = entry.mimeType
            )
            require(ok) { "Unable to restore '${entry.originalName}' to mounted storage" }
            src.deleteRecursively()
            return null
        }

        var dst = File(entry.originalPath)
        dst.parentFile?.mkdirs()
        if (dst.exists()) {
            val base = entry.originalName.substringBeforeLast('.')
            val ext = entry.originalName.substringAfterLast('.', "")
            val suffix = if (!entry.isDirectory && ext.isNotEmpty() && base != entry.originalName) ".$ext" else ""
            var candidate = File(dst.parentFile, "${base}_restored$suffix")
            var counter = 2
            while (candidate.exists()) {
                candidate = File(dst.parentFile, "${base}_restored_$counter$suffix")
                counter++
            }
            dst = candidate
        }
        val ok = src.renameTo(dst) || run {
            val copied = src.copyRecursively(dst, overwrite = true)
            if (copied) src.deleteRecursively() else false
        }
        require(ok) { "Unable to restore '${entry.originalName}'" }
        return dst.absolutePath
    }

    suspend fun restoreTrashEntry(entry: TrashEntry): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val restoredPath = restoreSingleEntryInternal(entry)
            synchronized(trashLock) {
                val remaining = readTrashEntriesInternal().filterNot { it.id == entry.id }
                writeTrashEntriesInternal(remaining)
            }
            restoredPath?.let(::notifyMediaScanner)
            Unit
        }
    }

    suspend fun restoreTrashEntries(entries: List<TrashEntry>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val restoredIds = mutableSetOf<String>()
            val restoredPaths = mutableListOf<String>()
            for (entry in entries) {
                val path = runCatching { restoreSingleEntryInternal(entry) }.getOrNull()
                val srcStillExists = File(getTrashRoot(), entry.trashFileName).exists()
                if (!srcStillExists) {
                    restoredIds.add(entry.id)
                    path?.let { restoredPaths.add(it) }
                }
            }
            if (restoredIds.isNotEmpty()) {
                synchronized(trashLock) {
                    val remaining = readTrashEntriesInternal().filterNot { it.id in restoredIds }
                    writeTrashEntriesInternal(remaining)
                }
                restoredPaths.forEach(::notifyMediaScanner)
            }
            restoredIds.size
        }
    }

    suspend fun deleteTrashEntryForever(entry: TrashEntry): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            File(getTrashRoot(), entry.trashFileName).deleteRecursively()
            synchronized(trashLock) {
                val remaining = readTrashEntriesInternal().filterNot { it.id == entry.id }
                writeTrashEntriesInternal(remaining)
            }
        }
    }

    suspend fun deleteTrashEntriesForever(entries: List<TrashEntry>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val deletedIds = mutableSetOf<String>()
            for (entry in entries) {
                val f = File(getTrashRoot(), entry.trashFileName)
                if (!f.exists() || f.deleteRecursively()) {
                    deletedIds.add(entry.id)
                }
            }
            if (deletedIds.isNotEmpty()) {
                synchronized(trashLock) {
                    val remaining = readTrashEntriesInternal().filterNot { it.id in deletedIds }
                    writeTrashEntriesInternal(remaining)
                }
            }
            deletedIds.size
        }
    }

    suspend fun emptyTrash(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var removed = 0
            synchronized(trashLock) {
                val entries = readTrashEntriesInternal()
                entries.forEach { e ->
                    if (File(getTrashRoot(), e.trashFileName).deleteRecursively()) removed++
                }
                writeTrashEntriesInternal(emptyList())
            }
            removed
        }
    }

    // Archive Inspection, Extraction & Creation (ZIP, 7Z, RAR)
    suspend fun inspectArchive(
        item: FileItem,
        password: String? = null,
        maxEntries: Int = 1500
    ): Result<ArchiveInspectionResult> = withContext(Dispatchers.IO) {
        archiveEngine.inspectArchive(item, password = password, maxEntries = maxEntries)
    }

    suspend fun testArchive(
        item: FileItem,
        password: String? = null
    ): Result<ArchiveTestResult> = withContext(Dispatchers.IO) {
        archiveEngine.testArchive(item, password = password)
    }

    suspend fun extractSingleEntryToCache(
        item: FileItem,
        entryPath: String,
        password: String? = null
    ): Result<FileItem> = withContext(Dispatchers.IO) {
        archiveEngine.extractSingleEntryToCache(item, entryPath, password).map { file ->
            fileToItemFast(
                file = file,
                parentPath = file.parent ?: context.cacheDir.absolutePath,
                isDir = false,
                computeChildCount = false
            )
        }
    }

    suspend fun listDestinationSubfolders(
        pathOrUri: String,
        isSaf: Boolean
    ): List<FileItem> = withContext(Dispatchers.IO) {
        when (val state = listDirectory(
            pathOrUri = pathOrUri,
            isSaf = isSaf,
            showHidden = false,
            sortField = SortField.NAME,
            ascending = true,
            foldersFirst = true,
            showFolderCounts = false
        )) {
            is DirectoryLoadState.Loaded -> state.items.filter { it.isDirectory }
            else -> emptyList()
        }
    }

    suspend fun extractArchiveToDirectory(
        item: FileItem,
        targetParentPathOrUri: String,
        isTargetSaf: Boolean,
        createSubfolder: Boolean = true,
        customSubfolderName: String? = null,
        overwriteMode: ArchiveOverwriteMode = ArchiveOverwriteMode.OVERWRITE,
        selectedEntryPaths: Set<String>? = null,
        password: String? = null,
        destinationLabel: String = "",
        onProgress: ((FileTransferProgress) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val tracker = TransferTracker(
            taskId = UUID.randomUUID().toString(),
            operation = TransferOperationType.EXTRACT,
            sourceLabel = item.name,
            destinationLabel = destinationLabel.ifBlank {
                if (isTargetSaf) "Mounted Drive" else File(targetParentPathOrUri).name.ifBlank { "Folder" }
            },
            onProgress = onProgress
        )
        try {
            tracker.currentFileName = item.name
            tracker.totalItems = 1
            tracker.totalBytes = item.sizeBytes.coerceAtLeast(0L)
            tracker.emit(TransferStatus.PREPARING, force = true)

            val res = archiveEngine.extractArchive(
                item = item,
                targetParentPathOrUri = targetParentPathOrUri,
                isTargetSaf = isTargetSaf,
                createSubfolder = createSubfolder,
                customSubfolderName = customSubfolderName,
                overwriteMode = overwriteMode,
                selectedEntryPaths = selectedEntryPaths,
                password = password,
                onProgress = { p ->
                    tracker.totalItems = p.totalItems.coerceAtLeast(1)
                    tracker.processedItems = p.processedItems
                    tracker.currentFileName = p.currentName
                    tracker.totalBytes = p.totalBytes
                    tracker.copiedBytes = p.processedBytes
                    tracker.emit(TransferStatus.IN_PROGRESS, force = false)
                }
            )
            res.onSuccess {
                tracker.processedItems = tracker.totalItems
                tracker.copiedBytes = tracker.totalBytes
                tracker.emit(TransferStatus.COMPLETED, force = true)
            }
            res
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun createArchive(
        items: List<FileItem>,
        targetParentPathOrUri: String,
        isTargetSaf: Boolean,
        archiveBaseName: String,
        format: ArchiveFormat,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.NORMAL,
        password: String? = null,
        destinationLabel: String = "",
        onProgress: ((FileTransferProgress) -> Unit)? = null
    ): Result<FileItem> = withContext(Dispatchers.IO) {
        val tracker = TransferTracker(
            taskId = UUID.randomUUID().toString(),
            operation = TransferOperationType.ARCHIVE,
            sourceLabel = if (items.size == 1) items.first().name else "${items.size} items",
            destinationLabel = destinationLabel.ifBlank {
                "$archiveBaseName.${format.extension}"
            },
            onProgress = onProgress
        )
        try {
            tracker.currentFileName = items.firstOrNull()?.name.orEmpty()
            tracker.totalItems = items.size.coerceAtLeast(1)
            tracker.emit(TransferStatus.PREPARING, force = true)

            val res = archiveEngine.createArchive(
                items = items,
                targetParentPathOrUri = targetParentPathOrUri,
                isTargetSaf = isTargetSaf,
                archiveBaseName = archiveBaseName,
                format = format,
                compressionLevel = compressionLevel,
                password = password,
                onProgress = { p ->
                    tracker.totalItems = p.totalItems.coerceAtLeast(1)
                    tracker.processedItems = p.processedItems
                    tracker.currentFileName = p.currentName
                    tracker.totalBytes = p.totalBytes
                    tracker.copiedBytes = p.processedBytes
                    tracker.emit(TransferStatus.IN_PROGRESS, force = false)
                }
            )
            res.onSuccess {
                tracker.processedItems = tracker.totalItems
                tracker.copiedBytes = tracker.totalBytes
                tracker.emit(TransferStatus.COMPLETED, force = true)
            }
            res
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    // Legacy compatibility wrappers for existing callers/tests
    suspend fun listZipEntries(item: FileItem, maxEntries: Int = 200): Result<List<ZipEntryInfo>> =
        inspectArchive(item, maxEntries = maxEntries).map { it.entries }

    suspend fun extractZipToDirectory(item: FileItem, targetParentDir: File): Result<Int> =
        extractArchiveToDirectory(
            item = item,
            targetParentPathOrUri = targetParentDir.absolutePath,
            isTargetSaf = false,
            createSubfolder = true
        )
}
