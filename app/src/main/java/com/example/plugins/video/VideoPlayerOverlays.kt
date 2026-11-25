package com.example.plugins.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.FormatUtils

@Composable
fun VideoPlayerTopBar(
    state: VideoPlayerUiState,
    onClose: () -> Unit,
    onCycleOrientation: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.84f),
                        Color.Black.copy(alpha = 0.45f),
                        Color.Transparent
                    )
                )
            )
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.48f)
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("video_close_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close video player",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = state.activeItem.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("video_title_text")
            )
            val metaLine = buildString {
                append("${state.activeIndex + 1} / ${state.queue.size}")
                if (state.videoWidth > 0 && state.videoHeight > 0) {
                    append(" · ${state.videoWidth}×${state.videoHeight}")
                }
                if (state.playbackSpeed != 1.0f) {
                    append(" · ${VideoPlaybackSpeeds.formatSpeed(state.playbackSpeed)}")
                }
                if (state.activeItem.sizeBytes > 0L) {
                    append(" · ${FormatUtils.formatBytes(state.activeItem.sizeBytes)}")
                }
            }
            Text(
                text = metaLine,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("video_subtitle_text")
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        val orientationIcon = when (state.orientationMode) {
            VideoOrientationMode.AUTO -> Icons.Filled.ScreenRotation
            VideoOrientationMode.LANDSCAPE -> Icons.Filled.StayCurrentLandscape
            VideoOrientationMode.PORTRAIT -> Icons.Filled.StayCurrentPortrait
        }
        Surface(
            onClick = onCycleOrientation,
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.56f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
            modifier = Modifier
                .size(width = 52.dp, height = 48.dp)
                .testTag("video_orientation_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = orientationIcon,
                    contentDescription = "Orientation: ${state.orientationMode.label}",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = state.orientationMode.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }

        if (state.queue.size > 1) {
            IconButton(
                onClick = onOpenPlaylist,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("video_playlist_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = "Video playlist queue",
                    tint = Color.White
                )
            }
        }

        Box {
            IconButton(
                onClick = { overflowExpanded = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("video_more_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More video actions",
                    tint = Color.White
                )
            }

            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false }
            ) {
                if (state.queue.size <= 1) {
                    DropdownMenuItem(
                        text = { Text("Playlist Queue") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null)
                        },
                        onClick = {
                            overflowExpanded = false
                            onOpenPlaylist()
                        },
                        modifier = Modifier.testTag("video_playlist_button")
                    )
                }
                DropdownMenuItem(
                    text = { Text("Open With External App") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    },
                    onClick = {
                        overflowExpanded = false
                        onOpenWith()
                    },
                    modifier = Modifier.testTag("video_open_with_button")
                )
                DropdownMenuItem(
                    text = { Text("Share Video") },
                    leadingIcon = {
                        Icon(Icons.Filled.Share, contentDescription = null)
                    },
                    onClick = {
                        overflowExpanded = false
                        onShare()
                    },
                    modifier = Modifier.testTag("video_share_button")
                )
                DropdownMenuItem(
                    text = { Text("Media & Stream Info") },
                    leadingIcon = {
                        Icon(Icons.Filled.Info, contentDescription = null)
                    },
                    onClick = {
                        overflowExpanded = false
                        onInfo()
                    },
                    modifier = Modifier.testTag("video_info_button")
                )
            }
        }
    }
}

@Composable
fun VideoPlayerCenterControls(
    state: VideoPlayerUiState,
    onPrevious: () -> Unit,
    onRewind10s: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onForward10s: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.52f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = state.hasPrevious,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("video_prev_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous video",
                    tint = if (state.hasPrevious) Color.White else Color.White.copy(alpha = 0.35f)
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.52f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
        ) {
            IconButton(
                onClick = onRewind10s,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("video_rewind_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    tint = Color.White
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
            shadowElevation = 8.dp
        ) {
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(64.dp)
                    .testTag("video_play_pause_button")
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause video" else "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.52f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
        ) {
            IconButton(
                onClick = onForward10s,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("video_forward_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Forward10,
                    contentDescription = "Forward 10 seconds",
                    tint = Color.White
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.52f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
        ) {
            IconButton(
                onClick = onNext,
                enabled = state.hasNext,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("video_next_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next video",
                    tint = if (state.hasNext) Color.White else Color.White.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
fun VideoPlayerBottomBar(
    state: VideoPlayerUiState,
    onScrubChange: (Long) -> Unit,
    onScrubFinish: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenSpeed: () -> Unit,
    onCycleResize: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePos = state.scrubPreviewPositionMs ?: state.positionMs
    val safeDuration = state.durationMs.coerceAtLeast(1L)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.58f),
                        Color.Black.copy(alpha = 0.88f)
                    )
                )
            )
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatVideoDuration(activePos),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("video_position_text")
            )
            Text(
                text = formatVideoDuration(state.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.78f),
                modifier = Modifier.testTag("video_duration_text")
            )
        }

        Slider(
            value = activePos.coerceIn(0L, safeDuration).toFloat(),
            onValueChange = { next -> onScrubChange(next.toLong()) },
            onValueChangeFinished = onScrubFinish,
            valueRange = 0f..safeDuration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.28f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("video_seek_slider")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoQuickActionChip(
                icon = Icons.Filled.Lock,
                label = "Lock",
                onClick = onToggleLock,
                testTag = "video_lock_button"
            )
            VideoQuickActionChip(
                icon = if (state.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                label = if (state.isMuted) "Muted" else "Sound",
                highlighted = state.isMuted,
                onClick = onToggleMute,
                testTag = "video_mute_button"
            )
            VideoQuickActionChip(
                icon = Icons.Filled.Speed,
                label = VideoPlaybackSpeeds.formatSpeed(state.playbackSpeed),
                highlighted = state.playbackSpeed != 1.0f,
                onClick = onOpenSpeed,
                testTag = "video_speed_button"
            )
            VideoQuickActionChip(
                icon = Icons.Filled.AspectRatio,
                label = state.resizeMode.shortLabel,
                highlighted = state.resizeMode != VideoResizeMode.FIT,
                onClick = onCycleResize,
                testTag = "video_aspect_button"
            )
            val repeatIcon = when (state.repeatMode) {
                VideoRepeatMode.ONE -> Icons.Filled.RepeatOne
                else -> Icons.Filled.Repeat
            }
            VideoQuickActionChip(
                icon = repeatIcon,
                label = when (state.repeatMode) {
                    VideoRepeatMode.OFF -> "Loop"
                    VideoRepeatMode.ONE -> "Loop 1"
                    VideoRepeatMode.ALL -> "Loop All"
                },
                highlighted = state.repeatMode != VideoRepeatMode.OFF,
                onClick = onCycleRepeat,
                testTag = "video_repeat_button"
            )
        }
    }
}

@Composable
private fun VideoQuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
    highlighted: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        } else {
            Color.White.copy(alpha = 0.10f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
            } else {
                Color.White.copy(alpha = 0.14f)
            }
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .padding(horizontal = 3.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
fun VideoPlayerGestureHud(
    state: VideoPlayerUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedVisibility(
            visible = state.seekOverlayDeltaSeconds != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val delta = state.seekOverlayDeltaSeconds ?: 0
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.78f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                modifier = Modifier.testTag("video_seek_delta_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (delta >= 0) Icons.Filled.Forward10 else Icons.Filled.Replay10,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (delta >= 0) "+${delta}s (${formatVideoDuration(state.positionMs)})"
                        else "${delta}s (${formatVideoDuration(state.positionMs)})",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.scrubPreviewPositionMs != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val target = state.scrubPreviewPositionMs ?: state.positionMs
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                modifier = Modifier.testTag("video_scrub_preview_badge")
            ) {
                Text(
                    text = "${formatVideoDuration(target)} / ${formatVideoDuration(state.durationMs)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }

        val meterPercent = state.brightnessOverlayPercent ?: state.volumeOverlayPercent
        val isBrightness = state.brightnessOverlayPercent != null
        AnimatedVisibility(
            visible = meterPercent != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val pct = meterPercent ?: 0
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.78f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 160.dp, max = 220.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isBrightness) Icons.Filled.Brightness6 else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isBrightness) "Brightness $pct%" else "Volume $pct%",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.24f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.statusBannerMessage != null && state.seekOverlayDeltaSeconds == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.74f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier.testTag("video_status_banner")
            ) {
                Text(
                    text = state.statusBannerMessage.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun VideoPlayerLockOverlay(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onUnlock,
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.26f)),
        modifier = modifier
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start)
            )
            .padding(16.dp)
            .heightIn(min = 48.dp)
            .testTag("video_unlock_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LockOpen,
                contentDescription = "Unlock player controls",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Locked · Tap to Unlock",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }
    }
}
