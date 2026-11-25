package com.example.ui.components.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.FileTransferProgress
import com.example.data.FormatUtils
import com.example.data.TransferOperationType
import com.example.data.TransferStatus
import com.example.ui.theme.ArcTheme

private fun operationIcon(op: TransferOperationType): ImageVector = when (op) {
    TransferOperationType.COPY -> Icons.Filled.ContentCopy
    TransferOperationType.MOVE -> Icons.AutoMirrored.Filled.DriveFileMove
    TransferOperationType.DELETE -> Icons.Filled.DeleteOutline
    TransferOperationType.IMPORT -> Icons.Filled.Download
    TransferOperationType.ARCHIVE -> Icons.Filled.Archive
    TransferOperationType.EXTRACT -> Icons.Filled.Unarchive
}

/**
 * Reusable ES File Explorer–style file transfer host.
 * Renders an expanded progress card with route, current file, speed, ETA, Hide & Cancel controls,
 * or smoothly minimizes to a floating bottom pill so the user can continue browsing folders.
 */
@Composable
fun FileTransferProgressHost(
    transfer: FileTransferProgress?,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (transfer != null && !transfer.isMinimized) {
        Dialog(
            onDismissRequest = {
                if (transfer.isRunning) onMinimize() else onDismissError()
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = transfer.isRunning
            )
        ) {
            ConfigureEdgeToEdgeDialogWindow()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                FileTransferProgressCard(
                    transfer = transfer,
                    onMinimize = onMinimize,
                    onCancel = onCancel,
                    onDismissError = onDismissError
                )
            }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = transfer != null && transfer.isMinimized,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
        ) {
            if (transfer != null) {
                FileTransferMiniBar(
                    transfer = transfer,
                    onExpand = onExpand,
                    onCancel = onCancel
                )
            }
        }
    }
}

@Composable
fun FileTransferProgressCard(
    transfer: FileTransferProgress,
    onMinimize: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val d = ArcTheme.dimens
    val isFailed = transfer.status == TransferStatus.FAILED
    val isPreparing = transfer.status == TransferStatus.PREPARING
    val animatedFraction by animateFloatAsState(
        targetValue = transfer.fraction,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "transfer_progress_fraction"
    )
    val accentColor = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val containerAccent = if (isFailed) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
    }
    val onContainerAccent = if (isFailed) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp)
            .testTag("file_transfer_dialog_card"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 14.dp,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Operation Icon + Title + Percentage Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(containerAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFailed) Icons.Filled.ErrorOutline else operationIcon(transfer.operation),
                        contentDescription = transfer.operation.activeVerb,
                        tint = onContainerAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = when {
                            isFailed -> "${transfer.operation.activeVerb} failed"
                            isPreparing -> "Preparing ${transfer.operation.activeVerb.lowercase()}..."
                            else -> "${transfer.operation.activeVerb} files"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val countText = if (transfer.totalItems > 0) {
                        "${transfer.processedItems} of ${transfer.totalItems} item(s)"
                    } else {
                        "Scanning items..."
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = containerAccent
                ) {
                    Text(
                        text = if (isPreparing && transfer.totalBytes <= 0L) "•••" else "${transfer.percent}%",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = onContainerAccent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Route Badge: Source -> Destination
            if (transfer.sourceLabel.isNotBlank() || transfer.destinationLabel.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(d.borderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = "Source folder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = transfer.sourceLabel.ifBlank { "Source" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "To",
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = transfer.destinationLabel.ifBlank { "Destination" },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Active File Name + Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = if (isFailed) {
                                transfer.errorMessage ?: "An unexpected error occurred"
                            } else {
                                transfer.currentFileName.ifBlank { "Calculating file sizes..." }
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isPreparing && transfer.totalBytes <= 0L) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = accentColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        strokeCap = StrokeCap.Round
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { animatedFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = accentColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        strokeCap = StrokeCap.Round
                    )
                }
            }

            // Telemetry Strip: Transferred Bytes | Speed | ETA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransferMetricTile(
                    label = "Transferred",
                    value = if (transfer.totalBytes > 0L) {
                        "${FormatUtils.formatBytes(transfer.copiedBytes)} / ${FormatUtils.formatBytes(transfer.totalBytes)}"
                    } else if (isPreparing) {
                        "Scanning..."
                    } else {
                        "${transfer.processedItems} item(s)"
                    },
                    modifier = Modifier.weight(1.35f)
                )
                TransferMetricTile(
                    label = "Speed",
                    value = if (transfer.speedBytesPerSec > 0L) {
                        "${FormatUtils.formatBytes(transfer.speedBytesPerSec)}/s"
                    } else {
                        "—"
                    },
                    modifier = Modifier.weight(0.9f)
                )
                TransferMetricTile(
                    label = "Remaining",
                    value = FormatUtils.formatEta(transfer.etaMillis),
                    modifier = Modifier.weight(0.85f)
                )
            }

            // ES File Explorer-style Action Row: Hide (Background) + Cancel (or Close on failure)
            if (isFailed) {
                Button(
                    onClick = onDismissError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("transfer_close_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Close")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onMinimize,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("transfer_hide_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Hide to background",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hide", maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("transfer_cancel_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cancel transfer",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FileTransferMiniBar(
    transfer: FileTransferProgress,
    onExpand: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val d = ArcTheme.dimens
    val animatedFraction by animateFloatAsState(
        targetValue = transfer.fraction,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "transfer_mini_fraction"
    )
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onExpand)
            .testTag("transfer_mini_bar"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                if (transfer.status == TransferStatus.PREPARING && transfer.totalBytes <= 0L) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(34.dp),
                        strokeWidth = 3.dp,
                        strokeCap = StrokeCap.Round
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { animatedFraction },
                        modifier = Modifier.size(34.dp),
                        strokeWidth = 3.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
                Text(
                    text = "${transfer.percent}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = "${transfer.operation.activeVerb} (${transfer.processedItems}/${transfer.totalItems})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val speedText = if (transfer.speedBytesPerSec > 0L) {
                    " · ${FormatUtils.formatBytes(transfer.speedBytesPerSec)}/s"
                } else ""
                Text(
                    text = "${transfer.currentFileName.ifBlank { transfer.destinationLabel }}$speedText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onExpand,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("transfer_mini_expand")
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInFull,
                    contentDescription = "Expand transfer details",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("transfer_mini_cancel")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancel transfer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}
