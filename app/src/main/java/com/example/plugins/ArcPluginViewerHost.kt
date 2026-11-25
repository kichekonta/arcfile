package com.example.plugins

import android.content.Intent
import androidx.compose.runtime.Composable
import com.example.data.FileItem
import com.example.plugins.editor.TextEditorPlugin
import com.example.plugins.editor.TextEditorPluginHost
import com.example.plugins.video.MediaViewerPluginHost
import com.example.ui.components.dialogs.FilePreviewDialog

/**
 * Isolated plugin viewer host called from MainActivity so activity chrome does not directly
 * couple to individual reader/player/editor UIs.
 */
@Composable
fun ArcPluginViewerHost(
    previewFile: FileItem?,
    previewQueue: List<FileItem>,
    previewText: String?,
    isStandaloneNoteEditorOpen: Boolean = false,
    onDismissStandaloneNoteEditor: () -> Unit = {},
    defaultSaveDirectory: String = "",
    defaultSaveIsSaf: Boolean = false,
    onDismiss: () -> Unit,
    onActiveItemChanged: (FileItem) -> Unit,
    onSaveText: (String) -> Unit,
    onWorkspaceModified: () -> Unit = {},
    onRequestOpenWith: (FileItem) -> Unit,
    onLaunchExternalView: (FileItem, (Intent) -> Unit) -> Unit,
    onShareItem: (FileItem) -> Unit,
    onInfo: (FileItem) -> Unit,
    onLaunchExternalIntent: (Intent, String) -> Unit
) {
    if (isStandaloneNoteEditorOpen) {
        TextEditorPluginHost(
            item = null,
            textContent = null,
            defaultSaveParentPathOrUri = defaultSaveDirectory,
            defaultSaveIsSaf = defaultSaveIsSaf,
            onDismiss = onDismissStandaloneNoteEditor,
            onWorkspaceModified = onWorkspaceModified,
            onShare = { active -> onShareItem(active) },
            onOpenWith = { active -> onRequestOpenWith(active) },
            onLaunchExternalIntent = onLaunchExternalIntent
        )
        return
    }

    val file = previewFile ?: return

    if (file.isImage || file.isVideo) {
        MediaViewerPluginHost(
            queue = previewQueue.ifEmpty { listOf(file) },
            initialItem = file,
            onDismiss = onDismiss,
            onActiveItemChanged = onActiveItemChanged,
            onOpenWith = onRequestOpenWith,
            onOpenExternal = { active ->
                onLaunchExternalView(active) { intent ->
                    onLaunchExternalIntent(intent, "Open '${active.name}' with")
                }
            },
            onShare = { active -> onShareItem(active) },
            onInfo = onInfo
        )
        return
    }

    if (TextEditorPlugin.canHandle(file) || previewText != null) {
        TextEditorPluginHost(
            item = file,
            textContent = previewText,
            defaultSaveParentPathOrUri = defaultSaveDirectory.ifBlank { file.parentPathOrUri },
            defaultSaveIsSaf = if (defaultSaveDirectory.isNotBlank()) defaultSaveIsSaf else file.isSafUri,
            onDismiss = onDismiss,
            onSaveText = onSaveText,
            onWorkspaceModified = onWorkspaceModified,
            onShare = { active -> onShareItem(active) },
            onOpenWith = { active -> onRequestOpenWith(active) },
            onLaunchExternalIntent = onLaunchExternalIntent
        )
        return
    }

    if (!file.isDirectory) {
        FilePreviewDialog(
            item = file,
            textContent = previewText,
            onDismiss = onDismiss,
            onSaveText = onSaveText,
            onShare = { onShareItem(file) },
            onOpenExternal = { onRequestOpenWith(file) }
        )
    }
}
