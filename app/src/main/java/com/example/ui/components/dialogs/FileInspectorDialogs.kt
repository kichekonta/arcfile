package com.example.ui.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.data.FileItem
import com.example.data.FormatUtils
import com.example.data.OpenAsType
import com.example.data.OpenWithRequest
import com.example.data.ResolvedAppOption
import com.example.ui.components.common.ArcIosGroupedCard
import com.example.ui.components.common.ArcIosModal
import com.example.ui.components.common.ArcIosPrimaryButton
import com.example.ui.components.common.ArcIosSecondaryButton
import com.example.ui.components.common.ArcIosTintedButton
import com.example.ui.components.common.ArcModalCard
import com.example.ui.components.common.ArcThumbnail
import com.example.ui.components.common.ArcUnderlineTextField
import com.example.ui.components.common.ConfigureEdgeToEdgeDialogWindow
import com.example.ui.registry.BinaryFileItemIcon
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePropertiesSheet(
    item: FileItem,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    onShare: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ArcThumbnail(
                    item = item,
                    monochromeBinaryIcons = false,
                    size = 44.dp,
                    iconSize = 36.dp,
                    cornerRadius = 10.dp
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = if (item.isDirectory) "Folder" else FormatUtils.formatBytes(item.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PropertyRow("Kind", if (item.isDirectory) "Directory" else item.mimeType)
                    if (!item.isDirectory) {
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        PropertyRow("Size", "${FormatUtils.formatBytes(item.sizeBytes)} (${item.sizeBytes} bytes)")
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    PropertyRow("Modified", FormatUtils.formatDate(item.lastModifiedMillis))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    PropertyRow("Where", if (item.isSafUri) "SAF Mounted Volume" else "Local Filesystem")
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    PropertyRow("Location", item.pathOrUri)
                }
            }

            if (!item.isDirectory) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }
                    Button(
                        onClick = onOpenExternal,
                        modifier = Modifier.weight(1f).testTag("properties_open_with_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open With")
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenWithSheet(
    item: FileItem,
    onDismiss: () -> Unit,
    onOpenWithMime: (String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = Modifier.testTag("open_with_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ArcThumbnail(
                    item = item,
                    monochromeBinaryIcons = false,
                    size = 44.dp,
                    iconSize = 36.dp,
                    cornerRadius = 10.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.mimeType.ifBlank { "application/octet-stream" }} · ${FormatUtils.formatBytes(item.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = { onOpenWithMime(null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("open_with_auto_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open with System App Picker")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

            Text(
                text = "Open As (ES-style)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OpenAsType.entries
                    .filter { it != OpenAsType.AUTO }
                    .forEach { openAs ->
                        val icon = when (openAs) {
                            OpenAsType.VIDEO -> Icons.Filled.Movie
                            OpenAsType.AUDIO -> Icons.Filled.MusicNote
                            OpenAsType.IMAGE -> Icons.Filled.Image
                            OpenAsType.TEXT -> Icons.Filled.Description
                            OpenAsType.ANY -> Icons.Filled.Apps
                            OpenAsType.AUTO -> Icons.AutoMirrored.Filled.OpenInNew
                        }
                        Surface(
                            onClick = { onOpenWithMime(openAs.mimeType) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_as_${openAs.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = openAs.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = openAs.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = openAs.mimeType ?: "*/*",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun SelectOpenAppDialog(
    request: OpenWithRequest,
    onDismiss: () -> Unit,
    onConfirmApp: (ResolvedAppOption, Boolean) -> Unit,
    onTryAllApps: () -> Unit
) {
    val context = LocalContext.current
    var selectedKey by remember(request) {
        mutableStateOf(
            request.preselectedComponentKey
                ?: request.apps.firstOrNull()?.componentKey
        )
    }
    val selectedApp = remember(request.apps, selectedKey) {
        request.apps.firstOrNull { it.componentKey == selectedKey } ?: request.apps.firstOrNull()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ConfigureEdgeToEdgeDialogWindow()
        ArcModalCard(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 368.dp)
                .testTag("select_open_app_dialog"),
            cornerRadius = 18.dp
        ) {
            val hasApps = request.apps.isNotEmpty()
            val iconDecodePx = with(LocalDensity.current) {
                34.dp.toPx().roundToInt().coerceIn(48, 192)
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (hasApps) "Select" else "No App Installed",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        fontWeight = if (hasApps) FontWeight.Normal else FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 12.dp)
                )

                if (!hasApps) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Text(
                            text = "No installed app can open '${request.item.name}'.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Install a compatible viewer or try opening with another app on your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (request.mimeOverride == null) {
                            OutlinedButton(
                                onClick = onTryAllApps,
                                modifier = Modifier.testTag("open_app_try_all_button")
                            ) {
                                Text("Show All Apps")
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        request.apps.forEachIndexed { index, app ->
                            val isSelected = selectedApp?.componentKey == app.componentKey
                            val iconTargetPkg = if (app.isBuiltinApp) context.packageName else app.packageName
                            val iconBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                                initialValue = null,
                                iconTargetPkg,
                                iconDecodePx
                            ) {
                                value = withContext(Dispatchers.IO) {
                                    runCatching {
                                        context.packageManager
                                            .getApplicationIcon(iconTargetPkg)
                                            .toBitmap(width = iconDecodePx, height = iconDecodePx)
                                            .asImageBitmap()
                                    }.getOrNull()
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable { selectedKey = app.componentKey }
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                                    .testTag("open_app_option_${app.packageName}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val resolvedIconBitmap = iconBitmap
                                if (resolvedIconBitmap != null) {
                                    Image(
                                        bitmap = resolvedIconBitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Apps,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 15.sp,
                                            fontWeight = if (app.isBuiltinApp) FontWeight.Medium else FontWeight.Normal
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (app.isBuiltinApp) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            modifier = Modifier.testTag("open_app_builtin_badge")
                                        ) {
                                            Text(
                                                text = "Built-in",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = 1.4.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            if (index < request.apps.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.6.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(
                    thickness = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                )

                if (hasApps) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val canConfirm = selectedApp != null
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .clickable(enabled = canConfirm) {
                                    selectedApp?.let { onConfirmApp(it, true) }
                                }
                                .testTag("open_app_always_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Always",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (canConfirm) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight(),
                            thickness = 0.8.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .clickable(enabled = canConfirm) {
                                    selectedApp?.let { onConfirmApp(it, false) }
                                }
                                .testTag("open_app_once_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "This Time Only",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (canConfirm) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(onClick = onDismiss)
                            .testTag("open_app_close_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Close",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun FilePreviewDialog(
    item: FileItem,
    textContent: String?,
    onDismiss: () -> Unit,
    onSaveText: (String) -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit = {}
) {
    if (!item.isImage && !item.isVideo) {
        DocumentViewerDialog(
            item = item,
            textContent = textContent,
            onDismiss = onDismiss,
            onSaveText = onSaveText,
            onShare = onShare,
            onOpenWith = onOpenExternal
        )
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ConfigureEdgeToEdgeDialogWindow(matchParentSize = true)
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close preview")
                    }
                    Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onOpenExternal, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open external")
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Share, contentDescription = "Share file")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                if (item.isImage) {
                    OriginalImageViewer(
                        item = item,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                } else {
                    OriginalVideoPlayer(
                        item = item,
                        onOpenExternal = onOpenExternal,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun CreateFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ConfigureEdgeToEdgeDialogWindow()
        ArcModalCard(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .testTag("create_folder_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.new_folder),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                ArcUnderlineTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Folder name",
                    placeholder = "Untitled folder",
                    testTag = "create_folder_input",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.heightIn(min = 38.dp)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { onCreate(name.trim()) },
                        enabled = name.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .testTag("create_folder_confirm")
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

@Composable
fun CreateNoteDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("note.txt") }
    var content by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ConfigureEdgeToEdgeDialogWindow()
        ArcModalCard(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .testTag("create_note_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.new_note),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                ArcUnderlineTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "File name",
                    placeholder = "note.txt",
                    testTag = "create_note_name_input",
                    modifier = Modifier.fillMaxWidth()
                )

                ArcUnderlineTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = "Content",
                    placeholder = "Write your note…",
                    singleLine = false,
                    minHeight = 96.dp,
                    maxHeight = 180.dp,
                    showClearButton = false,
                    testTag = "create_note_content_input",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.heightIn(min = 38.dp)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { onCreate(name.trim(), content) },
                        enabled = name.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .testTag("create_note_confirm")
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmDialog(
    items: List<FileItem>,
    onDismiss: () -> Unit,
    onMoveToTrash: (() -> Unit)? = null,
    onConfirm: () -> Unit,
    recycleBinEnabled: Boolean = true
) {
    val canTrash = onMoveToTrash != null && recycleBinEnabled && items.isNotEmpty()
    val folderCount = items.count { it.isDirectory }
    val fileCount = items.size - folderCount
    val targetSummary = when {
        items.size == 1 && items.first().isDirectory -> "1 folder"
        items.size == 1 -> "1 file"
        folderCount > 0 && fileCount > 0 -> "$folderCount folder(s), $fileCount file(s)"
        folderCount > 0 -> "$folderCount folder(s)"
        else -> "$fileCount file(s)"
    }
    val modalTitle = if (canTrash) "Move to Recycle Bin?" else "Delete Permanently?"
    val subtitle = if (canTrash) {
        if (items.size == 1) {
            "'${items.first().name}' will move to Recycle Bin where you can restore or permanently delete it."
        } else {
            "${items.size} selected items will move to Recycle Bin where you can restore or delete them permanently."
        }
    } else {
        if (items.size == 1) {
            "'${items.first().name}' will be permanently removed and cannot be recovered."
        } else {
            "${items.size} selected items will be permanently removed and cannot be recovered."
        }
    }

    ArcIosModal(
        title = modalTitle,
        subtitle = targetSummary,
        onDismiss = onDismiss,
        leadingIcon = {
            val iconTint = if (canTrash) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (canTrash) Icons.Filled.DeleteOutline else Icons.Filled.DeleteForever,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        modifier = Modifier.testTag("delete_confirm_dialog"),
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canTrash && onMoveToTrash != null) {
                    ArcIosPrimaryButton(
                        text = "Move to Recycle Bin",
                        icon = Icons.Filled.DeleteOutline,
                        onClick = onMoveToTrash,
                        modifier = Modifier.fillMaxWidth().testTag("delete_move_to_trash_button")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArcIosSecondaryButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        )
                        ArcIosTintedButton(
                            text = "Delete Forever",
                            icon = Icons.Filled.DeleteForever,
                            tintColor = MaterialTheme.colorScheme.error,
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f).testTag("delete_permanent_button")
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArcIosSecondaryButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        )
                        ArcIosPrimaryButton(
                            text = "Delete Forever",
                            icon = Icons.Filled.DeleteForever,
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White,
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f).testTag("delete_permanent_button")
                        )
                    }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (items.isNotEmpty()) {
                ArcIosGroupedCard {
                    val previewItems = items.take(3)
                    previewItems.forEachIndexed { idx, item ->
                        if (idx > 0) {
                            HorizontalDivider(
                                thickness = 0.6.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BinaryFileItemIcon(
                                item = item,
                                monochrome = false,
                                size = 34.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (item.isDirectory) "Folder · ${item.parentPathOrUri.substringBeforeLast('/', item.parentPathOrUri).ifBlank { "Storage" }}"
                                    else "${FormatUtils.formatBytes(item.sizeBytes)} · ${item.parentPathOrUri.ifBlank { "Storage" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (items.size > 3) {
                        HorizontalDivider(
                            thickness = 0.6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                        Text(
                            text = "+${items.size - 3} more item(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
