package com.example.data.db

import android.net.Uri
import com.example.data.FileItem
import com.example.data.LibrarySection
import com.example.data.PhotoAlbum
import com.example.data.normalizeTagPath
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

data class HybridAlbumCatalog(
    val customAlbums: List<PhotoAlbum>,
    val deviceAlbums: List<PhotoAlbum>,
    val customAlbumItemsById: Map<String, List<FileItem>>,
    val deviceAlbumItemsById: Map<String, List<FileItem>>
)

class AlbumRepository(private val dao: PhotoAlbumDao) {

    fun observeAlbums(): Flow<List<PhotoAlbumEntity>> = dao.observeAlbums()

    fun observeAllAlbumImages(): Flow<List<AlbumImageCrossRefEntity>> = dao.observeAllAlbumImages()

    suspend fun createAlbum(
        name: String,
        initialImages: List<FileItem> = emptyList(),
        mediaSection: LibrarySection = LibrarySection.IMAGES
    ): Result<PhotoAlbumEntity> = runCatching {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Album name cannot be empty" }
        val existing = dao.getAllAlbumsOnce()
        val sameSection = existing.filter { it.mediaSection.equals(mediaSection.name, ignoreCase = true) }
        require(sameSection.none { it.name.equals(cleanName, ignoreCase = true) }) {
            "An album named \"$cleanName\" already exists"
        }
        val now = System.currentTimeMillis()
        val firstCover = initialImages.firstOrNull { !it.isDirectory }
        val entity = PhotoAlbumEntity(
            id = "album_${UUID.randomUUID()}",
            name = cleanName,
            coverPathOrUri = firstCover?.let { normalizeTagPath(it.pathOrUri, it.isSafUri) },
            coverIsSaf = firstCover?.isSafUri ?: false,
            sortOrder = sameSection.size,
            createdAtMillis = now,
            updatedAtMillis = now,
            mediaSection = mediaSection.name
        )
        dao.upsertAlbum(entity)
        if (initialImages.isNotEmpty()) {
            addImagesToAlbum(entity.id, initialImages)
        }
        entity
    }

    suspend fun renameAlbum(albumId: String, newName: String): Result<Unit> = runCatching {
        val cleanName = newName.trim()
        require(cleanName.isNotEmpty()) { "Album name cannot be empty" }
        val existing = dao.getAllAlbumsOnce()
        val targetSection = existing.firstOrNull { it.id == albumId }?.mediaSection ?: LibrarySection.IMAGES.name
        require(
            existing.none {
                it.id != albumId &&
                    it.mediaSection.equals(targetSection, ignoreCase = true) &&
                    it.name.equals(cleanName, ignoreCase = true)
            }
        ) {
            "An album named \"$cleanName\" already exists"
        }
        dao.renameAlbum(albumId, cleanName, System.currentTimeMillis())
    }

    suspend fun deleteAlbum(albumId: String) {
        dao.deleteAlbumAndMappings(albumId)
    }

    suspend fun setAlbumCover(albumId: String, image: FileItem) {
        val key = normalizeTagPath(image.pathOrUri, image.isSafUri)
        dao.updateCover(albumId, key, image.isSafUri, System.currentTimeMillis())
    }

    suspend fun addImagesToAlbum(albumId: String, images: List<FileItem>): Int {
        val validImages = images.filter { !it.isDirectory }
        if (validImages.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val refs = validImages.mapIndexed { idx, item ->
            AlbumImageCrossRefEntity(
                albumId = albumId,
                imagePathOrUri = normalizeTagPath(item.pathOrUri, item.isSafUri),
                fileName = item.name,
                sizeBytes = item.sizeBytes,
                lastModifiedMillis = item.lastModifiedMillis,
                mimeType = item.mimeType,
                extension = item.extension,
                isSafUri = item.isSafUri,
                parentPathOrUri = item.parentPathOrUri,
                addedAtMillis = now - idx // Preserve selection order
            )
        }
        dao.upsertAlbumImages(refs)
        dao.touchAlbum(albumId, now)
        return refs.size
    }

    suspend fun removeImagesFromAlbum(albumId: String, images: List<FileItem>) {
        if (images.isEmpty()) return
        val keys = images.map { normalizeTagPath(it.pathOrUri, it.isSafUri) }
        keys.chunked(200).forEach { batch ->
            dao.deleteImagesFromAlbum(albumId, batch)
        }
        dao.touchAlbum(albumId, System.currentTimeMillis())
    }

    suspend fun purgeDeletedFiles(deletedItems: List<FileItem>) {
        if (deletedItems.isEmpty()) return
        val keys = deletedItems.map { normalizeTagPath(it.pathOrUri, it.isSafUri) }
        keys.chunked(200).forEach { batch ->
            dao.deleteImageAcrossAlbums(batch)
        }
    }

    suspend fun clearAlbumsForSection(mediaSection: LibrarySection): Int =
        dao.clearSectionAlbums(mediaSection.name)

    companion object {
        fun buildHybridCatalog(
            customEntities: List<PhotoAlbumEntity>,
            crossRefs: List<AlbumImageCrossRefEntity>,
            allIndexedImages: List<FileItem>,
            mediaSection: LibrarySection = LibrarySection.IMAGES
        ): HybridAlbumCatalog {
            val sectionEntities = customEntities.filter {
                it.mediaSection.equals(mediaSection.name, ignoreCase = true)
            }
            val indexedByKey = HashMap<String, FileItem>(allIndexedImages.size)
            for (img in allIndexedImages) {
                indexedByKey[normalizeTagPath(img.pathOrUri, img.isSafUri)] = img
            }

            // 1. Custom Virtual Albums
            val refsByAlbumId = crossRefs.groupBy { it.albumId }
            val customAlbumItems = HashMap<String, List<FileItem>>(sectionEntities.size)
            val customAlbums = sectionEntities.map { entity ->
                val refs = refsByAlbumId[entity.id].orEmpty()
                val liveItems = ArrayList<FileItem>(refs.size)
                for (ref in refs) {
                    val live = indexedByKey[ref.imagePathOrUri]
                    if (live != null) {
                        liveItems.add(live)
                    } else if (ref.isSafUri) {
                        liveItems.add(ref.toFileItem())
                    } else {
                        val disk = File(ref.imagePathOrUri)
                        if (disk.exists()) {
                            liveItems.add(
                                ref.toFileItem().copy(
                                    sizeBytes = disk.length(),
                                    lastModifiedMillis = disk.lastModified()
                                )
                            )
                        }
                    }
                }
                customAlbumItems[entity.id] = liveItems

                val coverExists = entity.coverPathOrUri?.let { cPath ->
                    liveItems.any { normalizeTagPath(it.pathOrUri, it.isSafUri) == cPath }
                } == true
                val resolvedCover = if (coverExists) {
                    entity.coverPathOrUri to entity.coverIsSaf
                } else {
                    liveItems.firstOrNull()?.let { it.pathOrUri to it.isSafUri } ?: (null to false)
                }

                val previews = buildList {
                    resolvedCover.first?.let { add(it to resolvedCover.second) }
                    for (item in liveItems) {
                        if (size >= 4) break
                        if (item.pathOrUri != resolvedCover.first) {
                            add(item.pathOrUri to item.isSafUri)
                        }
                    }
                }

                PhotoAlbum(
                    id = entity.id,
                    name = entity.name,
                    mediaSection = mediaSection,
                    isDeviceFolder = false,
                    folderPathOrUri = "",
                    coverPathOrUri = resolvedCover.first,
                    coverIsSaf = resolvedCover.second,
                    previewThumbPaths = previews,
                    itemCount = liveItems.size,
                    totalBytes = liveItems.sumOf { it.sizeBytes },
                    updatedAtMillis = entity.updatedAtMillis,
                    sortOrder = entity.sortOrder
                )
            }

            // 2. Smart Device Folder Albums (grouped by parentPathOrUri)
            val byParent = LinkedHashMap<String, MutableList<FileItem>>()
            for (img in allIndexedImages) {
                val parentKey = normalizeTagPath(img.parentPathOrUri, img.isSafUri)
                    .ifBlank { if (img.isSafUri) "saf_root" else File(img.pathOrUri).parent ?: "root" }
                byParent.getOrPut(parentKey) { ArrayList() }.add(img)
            }

            val deviceAlbumItems = HashMap<String, List<FileItem>>(byParent.size)
            val deviceAlbums = byParent.entries.mapNotNull { (parentKey, rawItems) ->
                if (rawItems.isEmpty()) return@mapNotNull null
                val sortedItems = rawItems.sortedWith(
                    compareByDescending<FileItem> { maxOf(it.createdAtMillis, it.lastModifiedMillis) }
                        .thenBy { it.name.lowercase() }
                )
                val albumId = "device:$parentKey"
                deviceAlbumItems[albumId] = sortedItems
                val sample = sortedItems.first()
                val folderName = resolveFolderAlbumName(parentKey, sample.isSafUri)
                val previews = sortedItems.take(4).map { it.pathOrUri to it.isSafUri }
                PhotoAlbum(
                    id = albumId,
                    name = folderName,
                    mediaSection = mediaSection,
                    isDeviceFolder = true,
                    folderPathOrUri = sample.parentPathOrUri.ifBlank { parentKey },
                    coverPathOrUri = sample.pathOrUri,
                    coverIsSaf = sample.isSafUri,
                    previewThumbPaths = previews,
                    itemCount = sortedItems.size,
                    totalBytes = sortedItems.sumOf { it.sizeBytes },
                    updatedAtMillis = sortedItems.maxOf { maxOf(it.createdAtMillis, it.lastModifiedMillis) },
                    sortOrder = 0
                )
            }.sortedWith(
                compareByDescending<PhotoAlbum> { it.updatedAtMillis }
                    .thenByDescending { it.itemCount }
                    .thenBy { it.name.lowercase() }
            )

            return HybridAlbumCatalog(
                customAlbums = customAlbums,
                deviceAlbums = deviceAlbums,
                customAlbumItemsById = customAlbumItems,
                deviceAlbumItemsById = deviceAlbumItems
            )
        }

        private fun resolveFolderAlbumName(parentKey: String, isSaf: Boolean): String {
            if (!isSaf) {
                val name = File(parentKey).name.trim()
                return name.ifBlank { "Internal Storage" }
            }
            val decoded = runCatching { Uri.parse(parentKey).lastPathSegment }.getOrNull().orEmpty()
            val tail = decoded.substringAfterLast(':').substringAfterLast('/').trim()
            return tail.ifBlank { "Mounted Folder" }
        }
    }
}
