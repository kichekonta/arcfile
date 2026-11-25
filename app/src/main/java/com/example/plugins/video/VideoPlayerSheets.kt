package com.example.plugins.video

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.FormatUtils
import com.example.ui.components.common.ArcThumbnail

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VideoSpeedPickerSheet(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141B27),
        contentColor = Color.White,
        modifier = Modifier.testTag("video_speed_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Playback Speed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Row {
                    TextButton(
                        onClick = { onSelectSpeed(1.0f) },
                        modifier = Modifier.testTag("video_speed_reset_button")
                    ) {
                        Text("Reset 1.0x")
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("video_speed_done_button")
                    ) {
                        Text("Done")
                    }
                }
            }

            Text(
                text = "Current: ${VideoPlaybackSpeeds.formatSpeed(currentSpeed)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.84f),
                modifier = Modifier.testTag("video_speed_current_label")
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoPlaybackSpeeds.presets.forEach { preset ->
                    val label = VideoPlaybackSpeeds.formatSpeed(preset)
                    val selected = kotlin.math.abs(currentSpeed - preset) < 0.02f
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectSpeed(preset) },
                        label = { Text(label) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("video_speed_option_${label.replace('.', '_')}")
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Fine Tune (0.25x – 3.0x)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.72f)
                )
                Slider(
                    value = currentSpeed.coerceIn(0.25f, 3.0f),
                    onValueChange = { raw ->
                        val stepped = kotlin.math.round(raw * 20f) / 20f
                        onSelectSpeed(stepped)
                    },
                    valueRange = 0.25f..3.0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_speed_slider")
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlaylistSheet(
    state: VideoPlayerUiState,
    onSelectVideo: (Int) -> Unit,
    onToggleAutoPlayNext: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141B27),
        contentColor = Color.White,
        modifier = Modifier.testTag("video_playlist_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Up Next Queue",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "${state.queue.size} video${if (state.queue.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.70f)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close queue", tint = Color.White)
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.06f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-play next video",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Switch(
                        checked = state.autoPlayNext,
                        onCheckedChange = { onToggleAutoPlayNext() },
                        modifier = Modifier.testTag("video_autoplay_switch")
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(state.queue, key = { idx, item -> "${idx}_${item.id}" }) { index, item ->
                    val isCurrent = index == state.activeIndex
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                        } else {
                            Color.White.copy(alpha = 0.05f)
                        },
                        border = if (isCurrent) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectVideo(index) }
                            .testTag("video_playlist_item_$index")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ArcThumbnail(
                                item = item,
                                monochromeBinaryIcons = false,
                                size = 46.dp,
                                iconSize = 24.dp,
                                cornerRadius = 8.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = buildString {
                                    append(item.extension.uppercase().ifBlank { "VIDEO" })
                                    if (item.sizeBytes > 0L) {
                                        append(" · ${FormatUtils.formatBytes(item.sizeBytes)}")
                                    }
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.70f),
                                    maxLines = 1
                                )
                            }
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Now playing",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoTrackInfoDialog(
    state: VideoPlayerUiState,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val active = state.activeItem
    val track = state.trackSummary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stream & File Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(active.name, fontWeight = FontWeight.SemiBold)
                Text("Position: ${formatVideoDuration(state.positionMs)} / ${formatVideoDuration(state.durationMs)}")
                if (state.videoWidth > 0 && state.videoHeight > 0) {
                    Text("Resolution: ${state.videoWidth} × ${state.videoHeight}")
                }
                if (track.codec.isNotBlank()) {
                    Text("Codec: ${track.codec}")
                }
                if (track.bitrateKbps > 0) {
                    Text("Bitrate: ${track.bitrateKbps} kbps")
                }
                if (track.frameRate > 0f) {
                    Text("Frame rate: ${"%.1f".format(track.frameRate)} fps")
                }
                if (active.sizeBytes > 0L) {
                    Text("Size: ${FormatUtils.formatBytes(active.sizeBytes)}")
                }
                Text("Speed: ${VideoPlaybackSpeeds.formatSpeed(state.playbackSpeed)} · Orientation: ${state.orientationMode.label}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onDismiss()
                    onOpenWith()
                }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open With")
                }
                TextButton(onClick = {
                    onDismiss()
                    onShare()
                }) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
            }
        },
        modifier = Modifier.testTag("video_info_dialog")
    )
}
