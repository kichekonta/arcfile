package com.example.ui.registry

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.FileItem

enum class FileActionId {
    OPEN_WITH,
    EXTRACT,
    COMPRESS,
    TAGS,
    COPY,
    MOVE,
    RENAME,
    BATCH_RENAME,
    BOOKMARK,
    SHARE,
    INFO,
    DELETE
}

data class FileActionSpec(
    val id: FileActionId,
    val label: String,
    val icon: ImageVector,
    val isDestructive: Boolean = false,
    val isVisibleFor: (List<FileItem>) -> Boolean
)

/**
 * Central Registry for single-item overflow menus and multi-select bottom action bars.
 * Add new file operations here once to expose them across List, Grid, and Multi-Select modes.
 */
object FileActionRegistry {

    val actions: List<FileActionSpec> = listOf(
        FileActionSpec(
            id = FileActionId.OPEN_WITH,
            label = "Open As",
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            isVisibleFor = { it.size == 1 && !it.first().isDirectory }
        ),
        FileActionSpec(
            id = FileActionId.EXTRACT,
            label = "Extract",
            icon = Icons.Filled.Unarchive,
            isVisibleFor = { it.size == 1 && it.first().isExtractableArchive }
        ),
        FileActionSpec(
            id = FileActionId.COMPRESS,
            label = "Compress",
            icon = Icons.Filled.FolderZip,
            isVisibleFor = { it.isNotEmpty() }
        ),
        FileActionSpec(
            id = FileActionId.TAGS,
            label = "Tags",
            icon = Icons.AutoMirrored.Outlined.Label,
            isVisibleFor = { it.isNotEmpty() }
        ),
        FileActionSpec(
            id = FileActionId.COPY,
            label = "Copy",
            icon = Icons.Filled.ContentCopy,
            isVisibleFor = { it.isNotEmpty() }
        ),
        FileActionSpec(
            id = FileActionId.MOVE,
            label = "Cut",
            icon = Icons.Filled.ContentCut,
            isVisibleFor = { it.isNotEmpty() }
        ),
        FileActionSpec(
            id = FileActionId.RENAME,
            label = "Rename",
            icon = Icons.Filled.DriveFileRenameOutline,
            isVisibleFor = { it.size == 1 }
        ),
        FileActionSpec(
            id = FileActionId.BATCH_RENAME,
            label = "Batch Rename",
            icon = Icons.Filled.DriveFileRenameOutline,
            isVisibleFor = { it.size > 1 }
        ),
        FileActionSpec(
            id = FileActionId.BOOKMARK,
            label = "Bookmark",
            icon = Icons.Outlined.BookmarkBorder,
            isVisibleFor = { it.size == 1 }
        ),
        FileActionSpec(
            id = FileActionId.SHARE,
            label = "Share",
            icon = Icons.Filled.Share,
            isVisibleFor = { items -> items.any { !it.isDirectory } }
        ),
        FileActionSpec(
            id = FileActionId.INFO,
            label = "Properties",
            icon = Icons.Filled.Info,
            isVisibleFor = { it.size == 1 }
        ),
        FileActionSpec(
            id = FileActionId.DELETE,
            label = "Delete",
            icon = Icons.Filled.DeleteOutline,
            isDestructive = true,
            isVisibleFor = { it.isNotEmpty() }
        )
    )

    fun visibleFor(items: List<FileItem>): List<FileActionSpec> =
        actions.filter { it.isVisibleFor(items) }
}
