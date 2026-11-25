package com.example.data.db

import com.example.data.FileItem
import com.example.data.FileTag
import com.example.data.IosTagPalette
import com.example.data.normalizeTagPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FileTagRepository(private val dao: FileTagDao) {

    val allTagsFlow: Flow<List<FileTag>> = dao.observeAllTags().map { rows ->
        if (rows.isEmpty()) {
            IosTagPalette.defaultTags
        } else {
            rows.map { it.toModel() }
        }
    }

    val allRefsFlow: Flow<List<FileTagCrossRefEntity>> = dao.observeAllFileTagRefs()

    suspend fun ensureDefaultTagsSeeded() {
        if (dao.countTags() == 0) {
            dao.upsertTags(
                IosTagPalette.defaultTags.map { tag ->
                    TagEntity(
                        id = tag.id,
                        name = tag.name,
                        colorHex = tag.colorHex,
                        sortOrder = tag.sortOrder,
                        isSystemDefault = tag.isSystemDefault
                    )
                }
            )
        }
    }

    suspend fun toggleTagForItems(items: List<FileItem>, tagId: String) {
        if (items.isEmpty()) return
        ensureDefaultTagsSeeded()
        val normalizedPaths = items.map { normalizeTagPath(it.pathOrUri, it.isSafUri) }
        val allHaveTag = normalizedPaths.all { path ->
            dao.getRefsForFile(path).any { it.tagId == tagId }
        }
        if (allHaveTag) {
            normalizedPaths.forEach { path ->
                dao.deleteFileTagRef(path, tagId)
            }
        } else {
            val now = System.currentTimeMillis()
            val refs = items.map { item ->
                val norm = normalizeTagPath(item.pathOrUri, item.isSafUri)
                FileTagCrossRefEntity(
                    filePathOrUri = norm,
                    tagId = tagId,
                    fileName = item.name,
                    isDirectory = item.isDirectory,
                    sizeBytes = item.sizeBytes,
                    lastModifiedMillis = item.lastModifiedMillis,
                    mimeType = item.mimeType,
                    extension = item.extension,
                    isSafUri = item.isSafUri,
                    parentPathOrUri = item.parentPathOrUri,
                    taggedAtMillis = now
                )
            }
            dao.upsertFileTagRefs(refs)
        }
    }

    suspend fun assignTag(item: FileItem, tagId: String) {
        ensureDefaultTagsSeeded()
        val norm = normalizeTagPath(item.pathOrUri, item.isSafUri)
        dao.upsertFileTagRef(
            FileTagCrossRefEntity(
                filePathOrUri = norm,
                tagId = tagId,
                fileName = item.name,
                isDirectory = item.isDirectory,
                sizeBytes = item.sizeBytes,
                lastModifiedMillis = item.lastModifiedMillis,
                mimeType = item.mimeType,
                extension = item.extension,
                isSafUri = item.isSafUri,
                parentPathOrUri = item.parentPathOrUri,
                taggedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun createCustomTag(name: String, colorHex: Long): FileTag {
        ensureDefaultTagsSeeded()
        val cleanName = name.trim().ifBlank { "Custom Tag" }
        val id = "tag_custom_${UUID.randomUUID().toString().take(8)}"
        val nextSort = (dao.maxSortOrder() ?: 6) + 1
        val entity = TagEntity(
            id = id,
            name = cleanName,
            colorHex = colorHex,
            sortOrder = nextSort,
            isSystemDefault = false
        )
        dao.upsertTag(entity)
        return entity.toModel()
    }

    suspend fun updateTag(tag: FileTag) {
        dao.upsertTag(
            TagEntity(
                id = tag.id,
                name = tag.name.trim().ifBlank { tag.name },
                colorHex = tag.colorHex,
                sortOrder = tag.sortOrder,
                isSystemDefault = tag.isSystemDefault
            )
        )
    }

    suspend fun deleteCustomTag(tagId: String) {
        dao.deleteRefsForTag(tagId)
        dao.deleteTag(tagId)
    }

    suspend fun remapFilePath(
        oldPathOrUri: String,
        oldIsSaf: Boolean,
        newPathOrUri: String,
        newIsSaf: Boolean,
        newName: String,
        newParentPathOrUri: String
    ) {
        val oldKey = normalizeTagPath(oldPathOrUri, oldIsSaf)
        val newKey = normalizeTagPath(newPathOrUri, newIsSaf)
        if (oldKey == newKey) return
        val existing = dao.getRefsForFile(oldKey)
        if (existing.isEmpty()) return
        dao.deleteAllTagsForFile(oldKey)
        val ext = if (existing.first().isDirectory) "" else newName.substringAfterLast('.', "").lowercase()
        val updated = existing.map { ref ->
            ref.copy(
                filePathOrUri = newKey,
                fileName = newName,
                extension = ext,
                isSafUri = newIsSaf,
                parentPathOrUri = newParentPathOrUri
            )
        }
        dao.upsertFileTagRefs(updated)
    }

    suspend fun removeTagsForDeletedItems(items: List<FileItem>) {
        items.forEach { item ->
            dao.deleteAllTagsForFile(normalizeTagPath(item.pathOrUri, item.isSafUri))
        }
    }

    suspend fun resetTagsAndAssignments(): Int {
        val removedRefs = dao.clearAllRefs()
        val removedTags = dao.deleteCustomTags()
        return removedRefs + removedTags
    }

    private fun TagEntity.toModel(): FileTag = FileTag(
        id = id,
        name = name,
        colorHex = colorHex,
        sortOrder = sortOrder,
        isSystemDefault = isSystemDefault
    )
}

fun FileTagCrossRefEntity.toFileItem(): FileItem = FileItem(
    id = filePathOrUri,
    name = fileName,
    pathOrUri = filePathOrUri,
    isDirectory = isDirectory,
    sizeBytes = sizeBytes,
    lastModifiedMillis = lastModifiedMillis,
    mimeType = mimeType,
    extension = extension,
    isSafUri = isSafUri,
    parentPathOrUri = parentPathOrUri
)
