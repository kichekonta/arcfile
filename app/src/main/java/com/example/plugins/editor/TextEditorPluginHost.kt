package com.example.plugins.editor

import android.content.Intent
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.data.FileItem

/**
 * Standalone entry host for TextEditorPlugin inside `plugins/editor`.
 * Accepts either an opened FileItem or `item = null` for launching directly as a
 * standalone Note & Code Editor workspace (e.g. from Home "Note Editor" / "+ New Note").
 */
@Composable
fun TextEditorPluginHost(
    item: FileItem? = null,
    textContent: String? = null,
    defaultSaveParentPathOrUri: String = item?.parentPathOrUri.orEmpty(),
    defaultSaveIsSaf: Boolean = item?.isSafUri == true,
    onDismiss: () -> Unit,
    onSaveText: (String) -> Unit = {},
    onWorkspaceModified: () -> Unit = {},
    onShare: (FileItem) -> Unit = {},
    onOpenWith: (FileItem) -> Unit = {},
    onLaunchExternalIntent: ((Intent, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember(context) { EditorStorageRepository(context) }

    val resolvedDefaultDir = remember(defaultSaveParentPathOrUri, item) {
        defaultSaveParentPathOrUri.ifBlank {
            item?.parentPathOrUri.orEmpty().ifBlank {
                @Suppress("DEPRECATION")
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            }
        }
    }

    val controller = remember(
        storage,
        resolvedDefaultDir,
        defaultSaveIsSaf
    ) {
        EditorSessionController(
            storage = storage,
            scope = scope,
            defaultSaveParentPathOrUri = resolvedDefaultDir,
            defaultSaveIsSaf = defaultSaveIsSaf,
            onPrimaryFileSaved = { savedItem, newContent ->
                if (item != null && savedItem.pathOrUri == item.pathOrUri) {
                    onSaveText(newContent)
                } else {
                    onWorkspaceModified()
                }
            },
            onWorkspaceModified = onWorkspaceModified
        )
    }

    LaunchedEffect(item?.id, textContent) {
        if (item != null) {
            controller.openInitialFile(item, textContent)
        } else {
            controller.openStandaloneWorkspace()
        }
    }

    TextEditorPluginScreen(
        controller = controller,
        onShareFile = { activeItem ->
            if (activeItem != null) {
                onShare(activeItem)
            } else {
                val activeTab = controller.uiState.value.activeTab
                val textBody = activeTab?.value?.text.orEmpty()
                val title = activeTab?.title ?: "Note"
                val shareTextIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, textBody)
                }
                onLaunchExternalIntent?.invoke(shareTextIntent, "Share '$title'")
            }
        },
        onOpenWithFile = { activeItem ->
            onOpenWith(activeItem)
        },
        onDismiss = onDismiss
    )
}
