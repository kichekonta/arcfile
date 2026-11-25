package com.example.plugins.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.TextureView
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.FileItem
import kotlin.math.abs

@Composable
fun VideoPlayerPluginHost(
    queue: List<FileItem>,
    initialItem: FileItem,
    onDismiss: () -> Unit,
    onActiveItemChanged: (FileItem) -> Unit,
    onOpenWith: (FileItem) -> Unit,
    onOpenExternal: (FileItem) -> Unit,
    onShare: (FileItem) -> Unit,
    onInfo: (FileItem) -> Unit,
    createExoPlayer: Boolean = true
) {
    val context = LocalContext.current
    val videoQueue = remember(queue, initialItem.id) {
        val filtered = queue.filter { it.isVideo }
        when {
            filtered.isEmpty() -> listOf(initialItem)
            filtered.any { it.id == initialItem.id } -> filtered
            else -> listOf(initialItem) + filtered
        }
    }
    val controller = remember(videoQueue, initialItem.id) {
        VideoPlayerSessionController(
            context = context,
            initialQueue = videoQueue,
            initialItem = initialItem,
            onActiveItemChanged = onActiveItemChanged,
            createExoPlayer = createExoPlayer
        )
    }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    VideoPlayerScreen(
        controller = controller,
        onDismiss = onDismiss,
        onOpenWith = onOpenWith,
        onOpenExternal = onOpenExternal,
        onShare = onShare,
        onInfo = onInfo
    )
}

@Composable
fun VideoPlayerScreen(
    controller: VideoPlayerSessionController,
    onDismiss: () -> Unit,
    onOpenWith: (FileItem) -> Unit,
    onOpenExternal: (FileItem) -> Unit,
    onShare: (FileItem) -> Unit,
    onInfo: (FileItem) -> Unit
) {
    val state by controller.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findActivity() }
    var currentBrightness by remember { mutableFloatStateOf(0.6f) }

    // Synchronize Activity orientation cleanly with VideoOrientationMode and restore on exit
    DisposableEffect(hostActivity) {
        val prevOrientation = hostActivity?.requestedOrientation
        val lpBrightness = hostActivity?.window?.attributes?.screenBrightness ?: -1f
        if (lpBrightness in 0.05f..1.0f) {
            currentBrightness = lpBrightness
        }
        onDispose {
            if (hostActivity != null && prevOrientation != null) {
                runCatching { hostActivity.requestedOrientation = prevOrientation }
            }
        }
    }

    LaunchedEffect(state.orientationMode, hostActivity) {
        if (hostActivity != null) {
            runCatching {
                hostActivity.requestedOrientation = state.orientationMode.activityOrientation
            }
        }
    }

    Dialog(
        onDismissRequest = {
            controller.persistCurrentPositionNow()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window

        SideEffect {
            if (dialogWindow != null) {
                WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
                dialogWindow.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                @Suppress("DEPRECATION")
                dialogWindow.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                dialogWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    dialogWindow.isStatusBarContrastEnforced = false
                    dialogWindow.isNavigationBarContrastEnforced = false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val lp = dialogWindow.attributes
                    if (lp.layoutInDisplayCutoutMode != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES) {
                        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        dialogWindow.attributes = lp
                    }
                }
                WindowInsetsControllerCompat(dialogWindow, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }

        DisposableEffect(dialogWindow, state.isPlaying) {
            if (dialogWindow != null) {
                if (state.isPlaying) {
                    dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            onDispose {
                dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        BackHandler {
            when {
                state.isSpeedSheetOpen -> controller.setSpeedSheetOpen(false)
                state.isPlaylistOpen -> controller.setPlaylistOpen(false)
                state.isInfoDialogOpen -> controller.setInfoDialogOpen(false)
                state.isLocked -> controller.toggleScreenLock()
                else -> {
                    controller.persistCurrentPositionNow()
                    onDismiss()
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF04060A))
                .testTag("video_player_screen")
        ) {
            // Video render viewport with resize mode math + touch/drag/double-tap gestures
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(state.isLocked, state.durationMs, state.positionMs) {
                        if (state.isLocked) {
                            detectTapGestures(onTap = { controller.toggleControls() })
                        } else {
                            detectTapGestures(
                                onTap = { controller.toggleControls() },
                                onDoubleTap = { offset ->
                                    val isLeftSide = offset.x < (size.width / 2f)
                                    if (isLeftSide) {
                                        controller.seekBy(-VideoPlayerSessionController.SEEK_STEP_MS)
                                    } else {
                                        controller.seekBy(VideoPlayerSessionController.SEEK_STEP_MS)
                                    }
                                }
                            )
                        }
                    }
                    .pointerInput(state.isLocked, state.durationMs) {
                        if (state.isLocked) return@pointerInput
                        var dragMode = DragGestureMode.NONE
                        var startPosMs = 0L
                        var accumulatedHorizontalPx = 0f
                        var isLeftSide = true

                        detectDragGestures(
                            onDragStart = { startOffset ->
                                dragMode = DragGestureMode.NONE
                                startPosMs = state.positionMs
                                accumulatedHorizontalPx = 0f
                                isLeftSide = startOffset.x < (size.width / 2f)
                            },
                            onDragEnd = {
                                if (dragMode == DragGestureMode.HORIZONTAL_SEEK) {
                                    controller.onScrubFinished()
                                }
                                controller.clearAllTransientOverlays()
                                dragMode = DragGestureMode.NONE
                            },
                            onDragCancel = {
                                if (dragMode == DragGestureMode.HORIZONTAL_SEEK) {
                                    controller.onScrubFinished()
                                }
                                controller.clearAllTransientOverlays()
                                dragMode = DragGestureMode.NONE
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (dragMode == DragGestureMode.NONE) {
                                    dragMode = if (abs(dragAmount.x) > abs(dragAmount.y) * 1.15f) {
                                        DragGestureMode.HORIZONTAL_SEEK
                                    } else {
                                        if (isLeftSide) DragGestureMode.VERTICAL_BRIGHTNESS
                                        else DragGestureMode.VERTICAL_VOLUME
                                    }
                                }
                                when (dragMode) {
                                    DragGestureMode.HORIZONTAL_SEEK -> {
                                        accumulatedHorizontalPx += dragAmount.x
                                        val widthPx = size.width.coerceAtLeast(1)
                                        val deltaMs = ((accumulatedHorizontalPx / widthPx) * 90_000f).toLong()
                                        controller.onScrubChange(startPosMs + deltaMs)
                                    }

                                    DragGestureMode.VERTICAL_BRIGHTNESS -> {
                                        val heightPx = size.height.coerceAtLeast(1)
                                        val delta = -dragAmount.y / (heightPx * 0.75f)
                                        currentBrightness = (currentBrightness + delta).coerceIn(0.05f, 1.0f)
                                        if (dialogWindow != null) {
                                            val lp = dialogWindow.attributes
                                            lp.screenBrightness = currentBrightness
                                            dialogWindow.attributes = lp
                                        }
                                        controller.updateBrightnessOverlay((currentBrightness * 100f).toInt())
                                    }

                                    DragGestureMode.VERTICAL_VOLUME -> {
                                        val heightPx = size.height.coerceAtLeast(1)
                                        val delta = -dragAmount.y / (heightPx * 0.70f)
                                        controller.adjustVolumeBy(delta)
                                    }

                                    DragGestureMode.NONE -> Unit
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val targetRatio = when (state.resizeMode) {
                    VideoResizeMode.FIT, VideoResizeMode.FILL -> state.naturalAspectRatio
                    VideoResizeMode.RATIO_16_9 -> 16f / 9f
                    VideoResizeMode.RATIO_4_3 -> 4f / 3f
                    VideoResizeMode.STRETCH -> {
                        if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 16f / 9f
                    }
                }
                val containerRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 16f / 9f

                val surfaceModifier = when (state.resizeMode) {
                    VideoResizeMode.STRETCH -> Modifier.fillMaxSize()
                    VideoResizeMode.FILL -> {
                        if (targetRatio > containerRatio) {
                            val fillWidth = maxHeight * targetRatio
                            Modifier.width(fillWidth).height(maxHeight)
                        } else {
                            val fillHeight = maxWidth / targetRatio
                            Modifier.width(maxWidth).height(fillHeight)
                        }
                    }

                    VideoResizeMode.FIT, VideoResizeMode.RATIO_16_9, VideoResizeMode.RATIO_4_3 -> {
                        if (targetRatio > containerRatio) {
                            Modifier.width(maxWidth).aspectRatio(targetRatio)
                        } else {
                            Modifier.height(maxHeight).aspectRatio(targetRatio, matchHeightConstraintsFirst = true)
                        }
                    }
                }

                Box(
                    modifier = surfaceModifier,
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("video_surface_view"),
                        factory = { ctx ->
                            TextureView(ctx).also { tv ->
                                controller.attachTextureView(tv)
                            }
                        },
                        update = { tv ->
                            controller.attachTextureView(tv)
                        }
                    )
                }

                if (state.isBuffering) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                }

                if (state.errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF101622).copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 132.dp, start = 20.dp, end = 20.dp)
                            .testTag("video_error_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BrokenImage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = state.errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                            Button(
                                onClick = { onOpenExternal(state.activeItem) },
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.testTag("video_open_external_fallback")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open in External Player")
                            }
                        }
                    }
                }
            }

            // Gesture & status HUD (always visible even when controls are hidden)
            VideoPlayerGestureHud(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 92.dp)
            )

            // Chrome overlays when unlocked
            AnimatedVisibility(
                visible = state.showControls && !state.isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                VideoPlayerTopBar(
                    state = state,
                    onClose = {
                        controller.persistCurrentPositionNow()
                        onDismiss()
                    },
                    onCycleOrientation = controller::cycleOrientationMode,
                    onOpenPlaylist = { controller.setPlaylistOpen(true) },
                    onOpenWith = {
                        controller.pause()
                        onOpenWith(state.activeItem)
                    },
                    onShare = { onShare(state.activeItem) },
                    onInfo = { controller.setInfoDialogOpen(true) }
                )
            }

            AnimatedVisibility(
                visible = state.showControls && !state.isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                VideoPlayerCenterControls(
                    state = state,
                    onPrevious = { controller.previousVideo() },
                    onRewind10s = { controller.seekBy(-VideoPlayerSessionController.SEEK_STEP_MS) },
                    onTogglePlayPause = controller::togglePlayPause,
                    onForward10s = { controller.seekBy(VideoPlayerSessionController.SEEK_STEP_MS) },
                    onNext = { controller.nextVideo() }
                )
            }

            AnimatedVisibility(
                visible = state.showControls && !state.isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                VideoPlayerBottomBar(
                    state = state,
                    onScrubChange = controller::onScrubChange,
                    onScrubFinish = controller::onScrubFinished,
                    onToggleLock = controller::toggleScreenLock,
                    onToggleMute = controller::toggleMute,
                    onOpenSpeed = { controller.setSpeedSheetOpen(true) },
                    onCycleResize = controller::cycleResizeMode,
                    onCycleRepeat = controller::cycleRepeatMode
                )
            }

            if (state.isLocked && state.showControls) {
                VideoPlayerLockOverlay(
                    onUnlock = controller::toggleScreenLock,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
        }

        if (state.isSpeedSheetOpen) {
            VideoSpeedPickerSheet(
                currentSpeed = state.playbackSpeed,
                onSelectSpeed = controller::setPlaybackSpeed,
                onDismiss = { controller.setSpeedSheetOpen(false) }
            )
        }

        if (state.isPlaylistOpen) {
            VideoPlaylistSheet(
                state = state,
                onSelectVideo = controller::selectVideoAt,
                onToggleAutoPlayNext = controller::toggleAutoPlayNext,
                onDismiss = { controller.setPlaylistOpen(false) }
            )
        }

        if (state.isInfoDialogOpen) {
            VideoTrackInfoDialog(
                state = state,
                onOpenWith = { onOpenWith(state.activeItem) },
                onShare = { onShare(state.activeItem) },
                onDismiss = { controller.setInfoDialogOpen(false) }
            )
        }
    }
}

private enum class DragGestureMode {
    NONE,
    HORIZONTAL_SEEK,
    VERTICAL_BRIGHTNESS,
    VERTICAL_VOLUME
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
