package com.example.features.tags

import android.content.Intent
import com.example.data.BookmarkEntry
import com.example.data.BreadcrumbNode
import com.example.data.ExplorerTab
import com.example.data.FileItem
import com.example.data.FileTag
import com.example.data.PreferencesRepository
import com.example.data.TabDestination
import com.example.data.db.FileTagRepository
import com.example.data.db.toFileItem
import com.example.ui.ExplorerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Feature-scoped coordinator owning File Tags, Color Labels, and Home Folder/File Bookmarks.
 */
class TagsAndBookmarksCoordinator(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<ExplorerUiState>,
    private val fileTagRepo: FileTagRepository,
    private val prefsRepo: PreferencesRepository,
    private val onOpenDestination: (
        title: String,
        destination: TabDestination,
        matchPredicate: (ExplorerTab) -> Boolean,
        forceNewTab: Boolean
    ) -> Unit,
    private val onOpenFile: (FileItem, (Intent) -> Unit) -> Unit,
    private val onMessage: (String) -> Unit
) {

    fun observeTags() {
        scope.launch {
            fileTagRepo.ensureDefaultTagsSeeded()
            combine(fileTagRepo.allTagsFlow, fileTagRepo.allRefsFlow) { tags, refs ->
                val tagById = tags.associateBy { it.id }
                val byPath = LinkedHashMap<String, MutableList<FileTag>>()
                val byTagId = LinkedHashMap<String, MutableList<FileItem>>()
                refs.forEach { ref ->
                    val tag = tagById[ref.tagId] ?: return@forEach
                    byPath.getOrPut(ref.filePathOrUri) { mutableListOf() }.add(tag)
                    byTagId.getOrPut(ref.tagId) { mutableListOf() }.add(ref.toFileItem())
                }
                val sortedByPath = byPath.mapValues { (_, list) -> list.sortedBy { it.sortOrder } }
                Triple(tags, sortedByPath, byTagId.mapValues { (_, list) -> list.toList() })
            }.collect { (tags, byPath, byTagId) ->
                uiState.update {
                    it.copy(
                        allTags = tags,
                        itemTagsByPath = byPath,
                        taggedFilesByTagId = byTagId
                    )
                }
            }
        }
    }

    fun openTagTab(tagId: String, forceNewTab: Boolean = false) {
        val tagName = uiState.value.allTags.firstOrNull { it.id == tagId }?.name ?: "Tag"
        onOpenDestination(
            tagName,
            TabDestination.Tag(tagId),
            { (it.destination as? TabDestination.Tag)?.tagId == tagId },
            forceNewTab
        )
    }

    fun openTagPicker(items: List<FileItem>) {
        if (items.isEmpty()) return
        uiState.update { it.copy(tagPickerTargets = items) }
    }

    fun dismissTagPicker() {
        uiState.update { it.copy(tagPickerTargets = null, selectedIds = emptySet()) }
    }

    fun toggleTagOnTargets(tagId: String, items: List<FileItem>) {
        if (items.isEmpty()) return
        scope.launch {
            fileTagRepo.toggleTagForItems(items, tagId)
        }
    }

    fun createCustomTag(name: String, colorHex: Long, applyToTargets: List<FileItem> = emptyList()) {
        val clean = name.trim()
        if (clean.isBlank()) return
        scope.launch {
            val created = fileTagRepo.createCustomTag(clean, colorHex)
            applyToTargets.forEach { item ->
                fileTagRepo.assignTag(item, created.id)
            }
        }
    }

    fun deleteCustomTag(tagId: String) {
        scope.launch {
            fileTagRepo.deleteCustomTag(tagId)
        }
    }

    fun toggleBookmark(item: FileItem) {
        scope.launch {
            val entry = BookmarkEntry(
                title = item.name,
                pathOrUri = item.pathOrUri,
                isSaf = item.isSafUri,
                isDirectory = item.isDirectory
            )
            val added = prefsRepo.toggleBookmark(entry)
            onMessage(if (added) "Bookmarked '${item.name}'" else "Removed '${item.name}' from Bookmarks")
        }
    }

    fun removeBookmark(pathOrUri: String) {
        scope.launch {
            prefsRepo.removeBookmark(pathOrUri)
        }
    }

    fun openBookmark(entry: BookmarkEntry, onLaunchExternal: (Intent) -> Unit) {
        if (entry.isDirectory) {
            val dest = TabDestination.Directory(
                title = entry.title,
                rootId = "bm_${entry.pathOrUri.hashCode()}",
                currentPathOrUri = entry.pathOrUri,
                isSaf = entry.isSaf,
                breadcrumbs = listOf(BreadcrumbNode(entry.title, entry.pathOrUri, entry.isSaf))
            )
            onOpenDestination(
                entry.title,
                dest,
                { tab ->
                    (tab.destination as? TabDestination.Directory)?.currentPathOrUri == entry.pathOrUri
                },
                false
            )
        } else {
            val cached = uiState.value.allIndexedFiles.firstOrNull { it.pathOrUri == entry.pathOrUri }
            if (cached != null) {
                onOpenFile(cached, onLaunchExternal)
            } else {
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        val local = if (!entry.isSaf) File(entry.pathOrUri) else null
                        FileItem(
                            id = entry.pathOrUri,
                            name = entry.title,
                            pathOrUri = entry.pathOrUri,
                            isDirectory = false,
                            sizeBytes = local?.length() ?: 0L,
                            lastModifiedMillis = local?.lastModified() ?: 0L,
                            mimeType = "application/octet-stream",
                            extension = entry.title.substringAfterLast('.', "").lowercase(),
                            isSafUri = entry.isSaf,
                            parentPathOrUri = local?.parent ?: entry.pathOrUri
                        )
                    }
                    onOpenFile(file, onLaunchExternal)
                }
            }
        }
    }
}
