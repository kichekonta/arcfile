package com.example.ui.screens.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.data.ArcImageLoader
import com.example.data.ClipboardOp
import com.example.data.ClipboardState
import com.example.data.FileItem
import com.example.data.FileTag
import com.example.data.FormatUtils
import com.example.data.MediaThumbnailLoader
import com.example.ui.components.common.ArcThumbnail
import com.example.ui.components.common.TagDotsCluster
import com.example.ui.registry.BinaryFileItemIcon
import com.example.ui.registry.FileActionId
import com.example.ui.registry.FileActionRegistry
import com.example.ui.theme.ArcTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.File
import kotlin.math.roundToInt

val HiddenSystemItemSemanticsKey = SemanticsPropertyKey<Boolean>("HiddenSystemItem")

private fun displayFileName(item: FileItem, showExtensions: Boolean): String {
    if (item.isDirectory || showExtensions) return item.name
    val stripped = item.name.substringBeforeLast('.')
    return if (stripped.isNotBlank()) stripped else item.name
}

private fun initialRenameSelection(item: FileItem): TextRange {
    if (item.isDirectory) return TextRange(0, item.name.length)
    val dotIdx = item.name.lastIndexOf('.')
    return if (dotIdx > 0) TextRange(0, dotIdx) else TextRange(0, item.name.length)
}

private fun isValidFileName(name: String): Boolean {
    val trimmed = name.trim()
    return trimmed.isNotEmpty() && !trimmed.contains('/') && !trimmed.contains('\\')
}

@Composable
private fun rememberInlineRenameDraft(item: FileItem): androidx.compose.runtime.MutableState<TextFieldValue> {
    return remember(item.id, item.name) {
        mutableStateOf(
            TextFieldValue(
                text = item.name,
                selection = initialRenameSelection(item)
            )
        )
    }
}

@Composable
private fun InlineRenameTextBox(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    itemId: String,
    centered: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember(itemId) { FocusRequester() }
    LaunchedEffect(itemId) {
        runCatching { focusRequester.requestFocus() }
    }
    val valid = isValidFileName(value.text)
    Column(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (valid) onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .focusRequester(focusRequester)
                .testTag("inline_rename_input")
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListRow(
    item: FileItem,
    isSelected: Boolean,
    selectionMode: Boolean,
    isRenaming: Boolean = false,
    monochrome: Boolean,
    showExtensions: Boolean = true,
    compactMode: Boolean = false,
    itemTags: List<FileTag> = emptyList(),
    dragState: BrowserDragDropState? = null,
    dragPayloadProvider: () -> List<FileItem> = { listOf(item) },
    onDropMove: (List<FileItem>, DropTargetEntry) -> Unit = { _, _ -> },
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onAction: (FileActionId, List<FileItem>) -> Unit,
    onConfirmRename: (String) -> Unit = {},
    onCancelRename: () -> Unit = {},
    dragPayloadForItem: ((FileItem) -> List<FileItem>)? = null,
    onClickItem: ((FileItem) -> Unit)? = null,
    onLongClickItem: ((FileItem) -> Unit)? = null,
    onConfirmRenameItem: ((FileItem, String) -> Unit)? = null,
    onReorderItems: (List<String>) -> Unit = {},
    isScrollInProgress: () -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    val d = ArcTheme.dimens
    var menuOpen by remember(item.id) { mutableStateOf(false) }
    val folderTargetId = "folder_${item.id}"
    val isBeingDragged = dragState?.isDragged(item.id) == true
    val isDropHovered = item.isDirectory && (dragState?.isDropHovered(folderTargetId) == true)
    val isHiddenSystem = item.isHiddenOrSystem
    val isFadedHidden = isHiddenSystem && !isRenaming && !isDropHovered

    val rowShape = d.cardShape
    val rowBg = when {
        isDropHovered -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        isRenaming -> MaterialTheme.colorScheme.surfaceContainer
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        isHiddenSystem -> MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
        else -> MaterialTheme.colorScheme.surface
    }
    val rowBorderColor = when {
        isDropHovered || isSelected || isRenaming -> MaterialTheme.colorScheme.primary
        isHiddenSystem -> MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
    }
    val rowBorderWidth = if (isDropHovered) 2.dp else if (isSelected || isRenaming) 1.5.dp else d.borderWidth
    val resolvedOnClick = remember(item, onClickItem, onClick) {
        if (onClickItem != null) ({ onClickItem(item) }) else onClick
    }
    val resolvedOnLongClick = remember(item, onLongClickItem, onLongClick) {
        if (onLongClickItem != null) ({ onLongClickItem(item) }) else onLongClick
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isDropHovered || isBeingDragged) {
                    Modifier.graphicsLayer(
                        scaleX = if (isDropHovered) 1.02f else 1f,
                        scaleY = if (isDropHovered) 1.02f else 1f,
                        alpha = if (isBeingDragged) 0.36f else 1f
                    )
                } else Modifier
            )
            .browserDropTarget(
                dragState = dragState,
                targetId = folderTargetId,
                label = item.name,
                pathOrUri = item.pathOrUri,
                isSaf = item.isSafUri,
                enabled = !isRenaming,
                itemId = item.id,
                isDirectory = item.isDirectory
            )
            .clip(rowShape)
            .background(rowBg, rowShape)
            .border(rowBorderWidth, rowBorderColor, rowShape)
            .then(
                when {
                    isRenaming -> Modifier
                    dragState != null -> Modifier
                        .clickable(onClick = resolvedOnClick)
                        .browserDragSource(
                            dragState = dragState,
                            item = item,
                            dragPayloadProvider = dragPayloadProvider,
                            dragPayloadForItem = dragPayloadForItem,
                            enabled = true,
                            onLongClick = resolvedOnLongClick,
                            onLongClickItem = onLongClickItem,
                            onDropMove = onDropMove,
                            onReorderCommit = onReorderItems
                        )
                    else -> Modifier.combinedClickable(onClick = resolvedOnClick, onLongClick = resolvedOnLongClick)
                }
            )
            .semantics { set(HiddenSystemItemSemanticsKey, isHiddenSystem) }
            .testTag("file_row_${item.name}")
    ) {
        val vPad = if (compactMode) 5.dp else 9.dp
        val thumbSize = if (compactMode) 26.dp else d.fileThumbnailSize
        val iconSize = if (compactMode) 22.dp else d.fileIconSize
        val hiddenAlphaMod = if (isFadedHidden) Modifier.graphicsLayer(alpha = 0.54f) else Modifier
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = d.cardPadding, end = 4.dp, top = vPad, bottom = vPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = hiddenAlphaMod) {
                ArcThumbnail(
                    item = item,
                    monochromeBinaryIcons = monochrome,
                    size = thumbSize,
                    iconSize = iconSize,
                    isScrollInProgress = isScrollInProgress
                )
            }

            if (isRenaming) {
                var draft by rememberInlineRenameDraft(item)
                val valid = isValidFileName(draft.text)
                Box(modifier = Modifier.weight(1f)) {
                    InlineRenameTextBox(
                        value = draft,
                        onValueChange = { draft = it },
                        onSubmit = {
                            if (onConfirmRenameItem != null) onConfirmRenameItem(item, draft.text)
                            else onConfirmRename(draft.text)
                        },
                        itemId = item.id,
                        centered = false,
                        textStyle = MaterialTheme.typography.titleSmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCancelRename,
                        modifier = Modifier.size(48.dp).testTag("inline_rename_cancel")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cancel rename",
                            modifier = Modifier.size(d.toolbarIconSize),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            if (valid) {
                                if (onConfirmRenameItem != null) onConfirmRenameItem(item, draft.text)
                                else onConfirmRename(draft.text)
                            }
                        },
                        enabled = valid,
                        modifier = Modifier.size(48.dp).testTag("inline_rename_confirm")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Confirm rename",
                            modifier = Modifier.size(d.toolbarIconSize),
                            tint = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                val displayName = remember(item.name, item.isDirectory, showExtensions) {
                    displayFileName(item, showExtensions)
                }
                val metaText = remember(item.isDirectory, item.childCount, item.sizeBytes, item.lastModifiedMillis) {
                    if (item.isDirectory) {
                        val countLabel = item.childCount?.let { "$it items · " } ?: "Folder · "
                        "$countLabel${FormatUtils.formatShortDate(item.lastModifiedMillis)}"
                    } else {
                        "${FormatUtils.formatBytes(item.sizeBytes)} · ${FormatUtils.formatShortDate(item.lastModifiedMillis)}"
                    }
                }
                Column(modifier = Modifier.weight(1f).then(hiddenAlphaMod)) {
                    Text(displayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(1.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (itemTags.isNotEmpty()) {
                            TagDotsCluster(tags = itemTags, dotSize = 8.dp)
                        }
                        Text(metaText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (isDropHovered) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 10.dp).testTag("drop_target_badge_${item.name}")
                    ) {
                        Text(
                            text = "Drop here",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                } else if (selectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        modifier = Modifier.size(20.dp).padding(end = 6.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.isDirectory) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.MoreHoriz, contentDescription = "More actions for ${item.name}", modifier = Modifier.size(d.toolbarIconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (menuOpen) {
                                DropdownMenu(
                                    expanded = true,
                                    onDismissRequest = { menuOpen = false },
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                                ) {
                                    val singleTarget = listOf(item)
                                    FileActionRegistry.visibleFor(singleTarget).forEach { spec ->
                                        DropdownMenuItem(
                                            text = { Text(spec.label) },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = spec.icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(17.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            onClick = { menuOpen = false; onAction(spec.id, singleTarget) },
                                            modifier = Modifier.testTag("file_menu_${spec.id.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Minimalist Square Grid Item: always 1:1 square container to preserve uniform grid alignment,
 * with responsive concentric border radius for 2-4+ columns and optional ContentScale.Fit original ratio.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridCard(
    item: FileItem,
    isSelected: Boolean,
    selectionMode: Boolean,
    isRenaming: Boolean = false,
    monochrome: Boolean,
    showExtensions: Boolean = true,
    originalRatio: Boolean = true,
    itemTags: List<FileTag> = emptyList(),
    gridMetrics: com.example.ui.theme.GridCardMetrics = ArcTheme.dimens.gridMetricsFor(3, 360.dp),
    dragState: BrowserDragDropState? = null,
    dragPayloadProvider: () -> List<FileItem> = { listOf(item) },
    onDropMove: (List<FileItem>, DropTargetEntry) -> Unit = { _, _ -> },
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onConfirmRename: (String) -> Unit = {},
    onCancelRename: () -> Unit = {},
    dragPayloadForItem: ((FileItem) -> List<FileItem>)? = null,
    onClickItem: ((FileItem) -> Unit)? = null,
    onLongClickItem: ((FileItem) -> Unit)? = null,
    onConfirmRenameItem: ((FileItem, String) -> Unit)? = null,
    onReorderItems: (List<String>) -> Unit = {},
    isScrollInProgress: () -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    val d = ArcTheme.dimens
    val context = LocalContext.current
    val cachedVideoMeta = remember(item.id, item.lastModifiedMillis, item.isVideo) {
        if (item.isVideo) MediaThumbnailLoader.getCached(item, needVideoFrame = true) else null
    }
    val mediaMeta by produceState(
        initialValue = cachedVideoMeta,
        item.id,
        item.lastModifiedMillis,
        item.isVideo
    ) {
        if (item.isVideo && value == null) {
            if (isScrollInProgress()) {
                snapshotFlow { !isScrollInProgress() }.first { it }
            }
            delay(55)
            if (isScrollInProgress()) {
                snapshotFlow { !isScrollInProgress() }.first { it }
            }
            value = MediaThumbnailLoader.loadMeta(context, item, needVideoFrame = true)
        }
    }

    val folderTargetId = "folder_${item.id}"
    val isBeingDragged = dragState?.isDragged(item.id) == true
    val isDropHovered = item.isDirectory && (dragState?.isDropHovered(folderTargetId) == true)
    val isHiddenSystem = item.isHiddenOrSystem
    val isFadedHidden = isHiddenSystem && !isRenaming && !isDropHovered

    val containerColor = when {
        isDropHovered -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.80f)
        isRenaming -> MaterialTheme.colorScheme.surfaceContainer
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val cardShape = remember(gridMetrics.cardCornerRadius) { RoundedCornerShape(gridMetrics.cardCornerRadius) }
    val mediaShape = remember(gridMetrics.mediaCornerRadius) { RoundedCornerShape(gridMetrics.mediaCornerRadius) }
    val badgeShape = remember(gridMetrics.durationBadgeRadius) { RoundedCornerShape(gridMetrics.durationBadgeRadius) }
    val hiddenAlphaMod = if (isFadedHidden) Modifier.graphicsLayer(alpha = 0.54f) else Modifier
    val resolvedOnClick = remember(item, onClickItem, onClick) {
        if (onClickItem != null) ({ onClickItem(item) }) else onClick
    }
    val resolvedOnLongClick = remember(item, onLongClickItem, onLongClick) {
        if (onLongClickItem != null) ({ onLongClickItem(item) }) else onLongClick
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isDropHovered || isBeingDragged) {
                    Modifier.graphicsLayer(
                        scaleX = if (isDropHovered) 1.04f else 1f,
                        scaleY = if (isDropHovered) 1.04f else 1f,
                        alpha = if (isBeingDragged) 0.36f else 1f
                    )
                } else Modifier
            )
            .browserDropTarget(
                dragState = dragState,
                targetId = folderTargetId,
                label = item.name,
                pathOrUri = item.pathOrUri,
                isSaf = item.isSafUri,
                enabled = !isRenaming,
                itemId = item.id,
                isDirectory = item.isDirectory
            )
            .clip(cardShape)
            .then(if (containerColor != Color.Transparent) Modifier.background(containerColor, cardShape) else Modifier)
            .then(
                when {
                    isDropHovered -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, cardShape)
                    isSelected || isRenaming -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, cardShape)
                    else -> Modifier
                }
            )
            .then(
                when {
                    isRenaming -> Modifier
                    dragState != null -> Modifier
                        .clickable(onClick = resolvedOnClick)
                        .browserDragSource(
                            dragState = dragState,
                            item = item,
                            dragPayloadProvider = dragPayloadProvider,
                            dragPayloadForItem = dragPayloadForItem,
                            enabled = true,
                            onLongClick = resolvedOnLongClick,
                            onLongClickItem = onLongClickItem,
                            onDropMove = onDropMove,
                            onReorderCommit = onReorderItems
                        )
                    else -> Modifier.combinedClickable(onClick = resolvedOnClick, onLongClick = resolvedOnLongClick)
                }
            )
            .semantics { set(HiddenSystemItemSemanticsKey, isHiddenSystem) }
            .testTag("file_grid_${item.name}")
    ) {
        Column(
            modifier = Modifier.padding(gridMetrics.innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (gridMetrics.compactText) 4.dp else d.itemGap)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(d.gridIconAspectRatio)
            ) {
                Box(modifier = Modifier.matchParentSize().then(hiddenAlphaMod)) {
                when {
                    item.isImage -> {
                        val imageLoader = remember(context) { ArcImageLoader.get(context) }
                        val rawCellPx = with(LocalDensity.current) { (gridMetrics.cellWidth.toPx() * 1.15f).roundToInt() }
                        val decodePx = when {
                            rawCellPx <= 160 -> 160
                            rawCellPx <= 240 -> 240
                            rawCellPx <= 360 -> 360
                            rawCellPx <= 512 -> 512
                            else -> 680
                        }
                        val imageRequest = remember(item.pathOrUri, item.lastModifiedMillis, item.isSafUri, decodePx) {
                            ImageRequest.Builder(context)
                                .data(if (item.isSafUri) item.pathOrUri else File(item.pathOrUri))
                                .size(decodePx, decodePx)
                                .precision(Precision.INEXACT)
                                .diskCachePolicy(if (item.isSafUri) CachePolicy.ENABLED else CachePolicy.DISABLED)
                                .crossfade(false)
                                .build()
                        }
                        val formatBadge = remember(item.extension) {
                            when (item.extension.lowercase()) {
                                "gif" -> "GIF"
                                "heic", "heics" -> "HEIC"
                                "heif", "heifs", "hif" -> "HEIF"
                                "avif" -> "AVIF"
                                else -> null
                            }
                        }
                        Box(modifier = Modifier.matchParentSize()) {
                            AsyncImage(
                                model = imageRequest,
                                imageLoader = imageLoader,
                                contentDescription = item.name,
                                contentScale = if (originalRatio) ContentScale.Fit else ContentScale.Crop,
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(mediaShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                            )
                            if (formatBadge != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(gridMetrics.overlayPadding)
                                        .background(Color.Black.copy(alpha = 0.68f), badgeShape)
                                        .padding(horizontal = 4.5.dp, vertical = 1.5.dp)
                                ) {
                                    Text(
                                        text = formatBadge,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = gridMetrics.durationFontSize),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                    item.isVideo -> {
                        val frame = mediaMeta?.videoFrame
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(mediaShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (frame != null) {
                                val imageBitmap = remember(frame) { frame.asImageBitmap() }
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = item.name,
                                    contentScale = if (originalRatio) ContentScale.Fit else ContentScale.Crop,
                                    modifier = Modifier.matchParentSize()
                                )
                            } else {
                                BinaryFileItemIcon(
                                    item = item,
                                    monochrome = monochrome,
                                    size = null,
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(gridMetrics.playBadgeSize)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Video",
                                    modifier = Modifier.size(gridMetrics.playBadgeSize * 0.6f),
                                    tint = Color.White
                                )
                            }
                            mediaMeta?.durationMillis?.let { dur ->
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(gridMetrics.overlayPadding)
                                        .background(Color.Black.copy(alpha = 0.65f), badgeShape)
                                        .padding(horizontal = 4.dp, vertical = 1.5.dp)
                                ) {
                                    Text(
                                        text = MediaThumbnailLoader.formatDuration(dur),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = gridMetrics.durationFontSize),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                    item.isApk -> {
                        val cachedApk = remember(item.id, item.lastModifiedMillis) {
                            MediaThumbnailLoader.getCachedApkIcon(item)
                        }
                        val apkBitmap by produceState(initialValue = cachedApk, item.id, item.lastModifiedMillis) {
                            if (value == null && !MediaThumbnailLoader.hasCachedApkAttempt(item)) {
                                if (isScrollInProgress()) {
                                    snapshotFlow { !isScrollInProgress() }.first { it }
                                }
                                delay(55)
                                if (isScrollInProgress()) {
                                    snapshotFlow { !isScrollInProgress() }.first { it }
                                }
                                value = MediaThumbnailLoader.loadApkIcon(context, item, maxSizePx = 144)
                            }
                        }
                        val bmp = apkBitmap
                        if (bmp != null) {
                            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(mediaShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .padding(if (gridMetrics.compactText) 6.dp else 10.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(gridMetrics.overlayPadding)
                                        .background(Color(0xFF047857).copy(alpha = 0.88f), badgeShape)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "APK",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = gridMetrics.durationFontSize),
                                        color = Color.White
                                    )
                                }
                            }
                        } else {
                            BinaryFileItemIcon(
                                item = item,
                                monochrome = monochrome,
                                size = null,
                                modifier = Modifier.matchParentSize()
                            )
                        }
                    }
                    else -> {
                        BinaryFileItemIcon(
                            item = item,
                            monochrome = monochrome,
                            size = null,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
                }

                if (isDropHovered) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = gridMetrics.overlayPadding)
                            .testTag("drop_target_badge_${item.name}")
                    ) {
                        Text(
                            text = "Drop",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else if (selectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(gridMetrics.overlayPadding)
                            .size(gridMetrics.checkIconSize),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isRenaming) 4.dp else 1.dp)
            ) {
                if (isRenaming) {
                    var draft by rememberInlineRenameDraft(item)
                    val valid = isValidFileName(draft.text)
                    InlineRenameTextBox(
                        value = draft,
                        onValueChange = { draft = it },
                        onSubmit = {
                            if (onConfirmRenameItem != null) onConfirmRenameItem(item, draft.text)
                            else onConfirmRename(draft.text)
                        },
                        itemId = item.id,
                        centered = true,
                        textStyle = if (gridMetrics.compactText) {
                            MaterialTheme.typography.titleSmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp)
                        } else {
                            MaterialTheme.typography.titleSmall
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onCancelRename)
                                .testTag("inline_rename_cancel"),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cancel rename",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = valid) {
                                    if (onConfirmRenameItem != null) onConfirmRenameItem(item, draft.text)
                                    else onConfirmRename(draft.text)
                                }
                                .testTag("inline_rename_confirm"),
                            shape = RoundedCornerShape(8.dp),
                            color = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Confirm rename",
                                    modifier = Modifier.size(15.dp),
                                    tint = if (valid) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                } else {
                    val displayName = remember(item.name, item.isDirectory, showExtensions) {
                        displayFileName(item, showExtensions)
                    }
                    val sub = remember(item.isDirectory, item.sizeBytes) {
                        if (item.isDirectory) null else FormatUtils.formatBytes(item.sizeBytes)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(hiddenAlphaMod),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = displayName,
                            style = if (gridMetrics.compactText) {
                                MaterialTheme.typography.labelMedium
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (sub != null || itemTags.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (itemTags.isNotEmpty()) {
                                    TagDotsCluster(tags = itemTags, dotSize = 7.dp)
                                }
                                if (sub != null) {
                                    Text(
                                        text = sub,
                                        style = if (gridMetrics.compactText) {
                                            MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                        } else {
                                            MaterialTheme.typography.labelSmall
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MultiSelectBottomBar(
    selectedItems: List<FileItem>,
    clipboard: ClipboardState? = null,
    canPasteHere: Boolean = false,
    allSelected: Boolean = false,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onPaste: () -> Unit = {},
    onClearClipboard: () -> Unit = {},
    onAction: (FileActionId, List<FileItem>) -> Unit
) {
    val d = ArcTheme.dimens
    val isSelectionMode = selectedItems.isNotEmpty()
    val hasClipboard = clipboard != null && canPasteHere
    if (!isSelectionMode && !hasClipboard) return

    val navBg by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "bottom_nav_bg"
    )
    val accentTint = MaterialTheme.colorScheme.primary

    val secondaryActions = remember(selectedItems) {
        if (selectedItems.isEmpty()) {
            emptyList()
        } else {
            val primaryIds = setOf(
                FileActionId.COPY,
                FileActionId.MOVE,
                FileActionId.RENAME,
                FileActionId.BATCH_RENAME,
                FileActionId.DELETE
            )
            FileActionRegistry.visibleFor(selectedItems).filter { it.id !in primaryIds }
        }
    }
    val selectedFilesBytes = remember(selectedItems) {
        selectedItems.filter { !it.isDirectory }.sumOf { it.sizeBytes }
    }
    var moreMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(navBg)
            .navigationBarsPadding()
            .testTag(if (isSelectionMode) "multi_edit_bottom_bar" else "bottom_clipboard_bar"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )

        Box(
            modifier = Modifier
                .widthIn(max = d.maxContentWidth)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = isSelectionMode,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(120)) },
                label = "bottom_bar_mode"
            ) { selecting ->
                if (selecting) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Slim iOS Files selection strip matching TopTabIsland chip & overflow styling
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 1.dp,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = accentTint
                                    )
                                    Text(
                                        text = "${selectedItems.size} selected",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (selectedFilesBytes > 0L) {
                                        Text(
                                            text = "· ${FormatUtils.formatBytes(selectedFilesBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainer)
                                            .clickable(onClick = onClear)
                                            .testTag("multi_clear_selection"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Clear selection",
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Surface(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { if (allSelected) onClear() else onSelectAll() }
                                    .testTag("multi_select_all"),
                                shape = RoundedCornerShape(10.dp),
                                color = if (allSelected) {
                                    accentTint.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shadowElevation = if (allSelected) 0.dp else 1.dp,
                                border = BorderStroke(
                                    0.5.dp,
                                    if (allSelected) {
                                        accentTint.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SelectAll,
                                        contentDescription = if (allSelected) "Deselect all" else "Select all",
                                        modifier = Modifier.size(15.dp),
                                        tint = if (allSelected) accentTint else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (allSelected) "Deselect" else "Select All",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (allSelected) accentTint else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }

                            if (secondaryActions.isNotEmpty()) {
                                Box {
                                    IconButton(
                                        onClick = { moreMenuOpen = true },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .minimumInteractiveComponentSize()
                                            .testTag("multi_more_actions")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(accentTint.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.MoreHoriz,
                                                contentDescription = "More batch actions",
                                                modifier = Modifier.size(18.dp),
                                                tint = accentTint
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = moreMenuOpen,
                                        onDismissRequest = { moreMenuOpen = false },
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 0.dp,
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                        modifier = Modifier.widthIn(min = 196.dp)
                                    ) {
                                        secondaryActions.forEachIndexed { idx, spec ->
                                            if (idx > 0) {
                                                HorizontalDivider(
                                                    thickness = 0.5.dp,
                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = spec.label,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.Normal
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = spec.icon,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(19.dp),
                                                        tint = accentTint
                                                    )
                                                },
                                                onClick = {
                                                    moreMenuOpen = false
                                                    onAction(spec.id, selectedItems)
                                                },
                                                modifier = Modifier.testTag("action_${spec.id.name.lowercase()}")
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )

                        // Docked 5-tool action bar: Copy · Cut · Paste · Rename · Delete
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomEditToolButton(
                                label = "Copy",
                                icon = Icons.Filled.ContentCopy,
                                onClick = { onAction(FileActionId.COPY, selectedItems) },
                                modifier = Modifier.weight(1f).testTag("action_copy")
                            )
                            BottomEditToolButton(
                                label = "Cut",
                                icon = Icons.Filled.ContentCut,
                                onClick = { onAction(FileActionId.MOVE, selectedItems) },
                                modifier = Modifier.weight(1f).testTag("action_move")
                            )
                            BottomEditToolButton(
                                label = "Paste",
                                icon = Icons.Filled.ContentPaste,
                                enabled = hasClipboard,
                                badgeCount = if (hasClipboard) clipboard?.items?.size else null,
                                highlight = hasClipboard,
                                onClick = onPaste,
                                modifier = Modifier.weight(1f).testTag("action_paste")
                            )
                            BottomEditToolButton(
                                label = "Rename",
                                icon = Icons.Filled.DriveFileRenameOutline,
                                onClick = {
                                    val actionId = if (selectedItems.size > 1) {
                                        FileActionId.BATCH_RENAME
                                    } else {
                                        FileActionId.RENAME
                                    }
                                    onAction(actionId, selectedItems)
                                },
                                modifier = Modifier.weight(1f).testTag(
                                    if (selectedItems.size > 1) "action_batch_rename" else "action_rename"
                                )
                            )
                            BottomEditToolButton(
                                label = "Delete",
                                icon = Icons.Filled.DeleteOutline,
                                isDestructive = true,
                                onClick = { onAction(FileActionId.DELETE, selectedItems) },
                                modifier = Modifier.weight(1f).testTag("action_delete")
                            )
                        }
                    }
                } else if (clipboard != null && canPasteHere) {
                    // Docked iOS Files clipboard paste-ready bar matching TopTabIsland
                    val isCopy = clipboard.operation == ClipboardOp.COPY
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 1.dp,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .height(34.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCopy) Icons.Filled.ContentCopy else Icons.Filled.ContentCut,
                                    contentDescription = null,
                                    tint = accentTint,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "${if (isCopy) "Copy" else "Cut"} · ${clipboard.items.size} ${if (clipboard.items.size == 1) "item" else "items"}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = onPaste,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentTint,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .height(36.dp)
                                .testTag("clipboard_paste_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Paste",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }

                        IconButton(
                            onClick = onClearClipboard,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(40.dp)
                                .testTag("clipboard_clear_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cancel clipboard",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun BottomEditToolButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    highlight: Boolean = false,
    badgeCount: Int? = null
) {
    val contentTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = contentTint
            )
            if (badgeCount != null && badgeCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = contentTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
