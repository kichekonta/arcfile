package com.example.plugins.video

import com.example.data.FileItem
import com.example.ui.components.dialogs.MediaLightboxDialog
import androidx.compose.runtime.Composable

/**
 * Standalone entry host for media & video playback plugins inside `plugins/video`.
 * Isolates lightbox & video surface presentation from MainActivity so a full ExoPlayer/Media3
 * VideoPlayerPlugin screen with PiP and subtitle tracks can swap in cleanly without changing callers.
 */
@Composable
fun MediaViewerPluginHost(
    queue: List<FileItem>,
    initialItem: FileItem,
    onDismiss: () -> Unit,
    onActiveItemChanged: (FileItem) -> Unit,
    onOpenWith: (FileItem) -> Unit,
    onOpenExternal: (FileItem) -> Unit,
    onShare: (FileItem) -> Unit,
    onInfo: (FileItem) -> Unit
) {
    if (initialItem.isVideo) {
        VideoPlayerPluginHost(
            queue = queue,
            initialItem = initialItem,
            onDismiss = onDismiss,
            onActiveItemChanged = onActiveItemChanged,
            onOpenWith = onOpenWith,
            onOpenExternal = onOpenExternal,
            onShare = onShare,
            onInfo = onInfo
        )
    } else {
        MediaLightboxDialog(
            queue = queue,
            initialItem = initialItem,
            onDismiss = onDismiss,
            onActiveItemChanged = onActiveItemChanged,
            onOpenWith = onOpenWith,
            onOpenExternal = onOpenExternal,
            onShare = onShare,
            onInfo = onInfo
        )
    }
}
