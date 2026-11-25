package com.example.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.BookmarkEntry
import com.example.data.CategorySummary
import com.example.data.DriveInfo
import com.example.data.DriveType
import com.example.data.ExplorerCategory
import com.example.data.FileItem
import com.example.data.FileTag
import com.example.data.FormatUtils
import com.example.ui.components.common.ArcBadge
import com.example.ui.components.common.ArcCard
import com.example.ui.components.common.ArcThumbnail
import com.example.ui.components.common.TagDot
import com.example.ui.registry.BinaryCategoryIcon
import com.example.ui.registry.BinaryDriveIcon
import com.example.ui.registry.binaryColorsForDrive
import com.example.ui.theme.ArcTheme
import com.example.ui.theme.BinaryAmber
import com.example.ui.theme.BinaryBlue
import com.example.ui.theme.BinaryEmerald
import com.example.ui.theme.BinaryIndigo
import com.example.ui.theme.BinaryOrchid
import com.example.ui.theme.RobotoFontFamily
import com.example.ui.theme.RobotoMonoFontFamily

@Composable
fun IosBrowseHeader(
    onSearchClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_browse_header"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Browse",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            ArcBadge(text = "On Device")
        }

        Surface(
            onClick = onSearchClick,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("home_search_bar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.search_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun IosSectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
fun IosHomeTagRow(
    tag: FileTag,
    itemCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .testTag("home_tag_${tag.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TagDot(colorHex = tag.colorHex, size = 14.dp)
        Text(
            text = tag.name,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (itemCount > 0) {
            Text(
                text = "$itemCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun HomeTopCategoryStrip(
    summaries: Map<ExplorerCategory, CategorySummary>,
    monochrome: Boolean,
    onCategoryClick: (ExplorerCategory) -> Unit
) {
    val d = ArcTheme.dimens
    val topFive = listOf(
        ExplorerCategory.PHOTOS,
        ExplorerCategory.VIDEOS,
        ExplorerCategory.DOCUMENTS,
        ExplorerCategory.MUSIC,
        ExplorerCategory.DOWNLOADS
    )
    ArcCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = d.cardRadius
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            topFive.forEach { cat ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(d.smallRadius))
                        .clickable { onCategoryClick(cat) }
                        .padding(vertical = 6.dp)
                        .testTag("category_tile_${cat.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    BinaryCategoryIcon(
                        category = cat,
                        monochrome = monochrome,
                        size = d.categoryCircleSize
                    )
                    Text(
                        text = cat.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun StorageGaugeSection(
    drives: List<DriveInfo>,
    monochrome: Boolean,
    onDriveClick: (DriveInfo) -> Unit,
    onUnmountDrive: (DriveInfo) -> Unit,
    @Suppress("UNUSED_PARAMETER") onMountFolderClick: () -> Unit
) {
    val activeDrives = drives.filter { it.type != DriveType.WORKSPACE }
    if (activeDrives.isEmpty()) {
        ArcCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("storage_loading_placeholder"),
            cornerRadius = ArcTheme.dimens.cardRadius
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Loading storage…",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }
        }
        return
    }

    val wideLayout = ArcTheme.dimens.isWideHomeLayout
    val gap = 10.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        if (!wideLayout) {
            // Full-width hero storage card for Primary Internal Storage — matches column width 100%
            val primary = activeDrives.first()
            ModernStorageCard(
                drive = primary,
                monochrome = monochrome,
                compact = false,
                onClick = { onDriveClick(primary) },
                onUnmount = if (primary.isSaf) ({ onUnmountDrive(primary) }) else null,
                modifier = Modifier.fillMaxWidth()
            )
            // Additional drives align cleanly in 2-column or full-width rows with zero edge mismatch
            activeDrives.drop(1).chunked(2).forEach { pair ->
                if (pair.size == 1) {
                    val single = pair.first()
                    ModernStorageCard(
                        drive = single,
                        monochrome = monochrome,
                        compact = false,
                        onClick = { onDriveClick(single) },
                        onUnmount = if (single.isSaf) ({ onUnmountDrive(single) }) else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        pair.forEach { drive ->
                            ModernStorageCard(
                                drive = drive,
                                monochrome = monochrome,
                                compact = true,
                                onClick = { onDriveClick(drive) },
                                onUnmount = if (drive.isSaf) ({ onUnmountDrive(drive) }) else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        } else {
            // Wide/Tablet screens: balanced 2-column grid matching exact parent column bounds
            activeDrives.chunked(2).forEach { pair ->
                if (pair.size == 1) {
                    val single = pair.first()
                    ModernStorageCard(
                        drive = single,
                        monochrome = monochrome,
                        compact = false,
                        onClick = { onDriveClick(single) },
                        onUnmount = if (single.isSaf) ({ onUnmountDrive(single) }) else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        pair.forEach { drive ->
                            ModernStorageCard(
                                drive = drive,
                                monochrome = monochrome,
                                compact = true,
                                onClick = { onDriveClick(drive) },
                                onUnmount = if (drive.isSaf) ({ onUnmountDrive(drive) }) else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private val StorageTitleStyle = TextStyle(
    fontFamily = RobotoFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 20.sp
)

private val StorageTitleCompactStyle = TextStyle(
    fontFamily = RobotoFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.5.sp,
    lineHeight = 17.sp
)

private val StorageSubStyle = TextStyle(
    fontFamily = RobotoFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 11.5.sp,
    lineHeight = 14.sp
)

private val StoragePctStyle = TextStyle(
    fontFamily = RobotoMonoFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp
)

private val StorageTelemetryStyle = TextStyle(
    fontFamily = RobotoFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp
)

private val StorageFreeStyle = TextStyle(
    fontFamily = RobotoFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp
)

private val StorageFreeCompactStyle = TextStyle(
    fontFamily = RobotoFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.5.sp
)

@Composable
fun ModernStorageCard(
    drive: DriveInfo,
    monochrome: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
    onUnmount: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val d = ArcTheme.dimens
    val (accentStart, accentEnd) = binaryColorsForDrive(drive.type, monochrome)
    val usedPct = (drive.usedFraction * 100).toInt().coerceIn(0, 100)
    val targetFraction = if (drive.totalBytes > 0L) drive.usedFraction.coerceIn(0.02f, 1f) else 0f
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh

    ArcCard(
        modifier = modifier.testTag("drive_card_${drive.id}"),
        cornerRadius = d.cardRadius,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 12.dp else 13.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 34.dp else 40.dp)
                        .clip(RoundedCornerShape(if (compact) 10.dp else 11.dp))
                        .background(accentStart.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    BinaryDriveIcon(
                        type = drive.type,
                        monochrome = monochrome,
                        size = if (compact) 18.dp else 21.dp
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = drive.name,
                        style = if (compact) StorageTitleCompactStyle else StorageTitleStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = drive.subtitle,
                        style = StorageSubStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (drive.totalBytes > 0L) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accentStart.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "$usedPct%",
                                style = StoragePctStyle,
                                color = accentStart,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (onUnmount != null) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable(onClick = onUnmount),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Unmount ${drive.name}",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Sleek Gradient Storage Bar rendered directly on GPU without relayout churn
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 6.dp else 7.5.dp)
                    .drawWithCache {
                        val corner = CornerRadius(size.height * 0.5f, size.height * 0.5f)
                        val fillWidth = (size.width * targetFraction).coerceAtLeast(if (targetFraction > 0f) size.height else 0f)
                        val brush = Brush.horizontalGradient(
                            colors = listOf(accentStart, accentEnd),
                            startX = 0f,
                            endX = fillWidth.coerceAtLeast(1f)
                        )
                        onDrawBehind {
                            drawRoundRect(color = trackColor, cornerRadius = corner)
                            if (fillWidth > 0f) {
                                drawRoundRect(
                                    brush = brush,
                                    topLeft = Offset.Zero,
                                    size = Size(fillWidth, size.height),
                                    cornerRadius = corner
                                )
                            }
                        }
                    }
            )

            // Bottom Capacity Telemetry
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!compact && drive.totalBytes > 0L) {
                    Text(
                        text = "${FormatUtils.formatBytes(drive.usedBytes)} used of ${FormatUtils.formatBytes(drive.totalBytes)}",
                        style = StorageTelemetryStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = if (drive.totalBytes > 0L) "${FormatUtils.formatBytes(drive.usedBytes)} used" else "Mounted folder",
                        style = StorageSubStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (drive.totalBytes > 0L) {
                        Text(
                            text = "${FormatUtils.formatBytes(drive.freeBytes)} free",
                            style = if (compact) StorageFreeCompactStyle else StorageFreeStyle,
                            color = accentStart,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SpaceAnalyzerHomeCard(
    indexedCount: Int,
    totalIndexedBytes: Long,
    onClick: () -> Unit
) {
    val d = ArcTheme.dimens
    ArcCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_space_analyzer_card"),
        cornerRadius = d.cardRadius,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Canvas(modifier = Modifier.size(34.dp)) {
                drawArc(
                    color = BinaryBlue,
                    startAngle = -90f,
                    sweepAngle = 215f,
                    useCenter = true
                )
                drawArc(
                    color = BinaryAmber,
                    startAngle = 132f,
                    sweepAngle = 72f,
                    useCenter = true
                )
                drawArc(
                    color = Color(0xFF93C5FD),
                    startAngle = 210f,
                    sweepAngle = 54f,
                    useCenter = true
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.space_analyzer), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = if (indexedCount > 0) {
                        "$indexedCount files scanned · ${FormatUtils.formatBytes(totalIndexedBytes)}"
                    } else {
                        stringResource(R.string.space_analyzer_empty)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HomeToolsGridCard(
    trashCount: Int,
    compressedCount: Int = 0,
    onCompressedClick: () -> Unit,
    onNoteEditorClick: () -> Unit,
    onRecycleBinClick: () -> Unit,
    onMountFolderClick: () -> Unit,
    onImportMediaClick: () -> Unit,
    onImportDocsClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenWorkspaceClick: () -> Unit = {},
    onOpenOtherClick: () -> Unit,
    onOpenBookmarksClick: () -> Unit = {}
) {
    val d = ArcTheme.dimens

    ArcCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_tools_card"),
        cornerRadius = d.cardRadius
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                HomeToolTile(
                    icon = Icons.Filled.FolderZip,
                    label = "Compressed",
                    tint = BinaryBlue,
                    modifier = Modifier.weight(1f),
                    testTag = "tool_compressed",
                    badgeCount = compressedCount,
                    onClick = onCompressedClick
                )
                HomeToolTile(Icons.AutoMirrored.Filled.NoteAdd, "Note Editor", BinaryIndigo, Modifier.weight(1f), "tool_note_editor", onClick = onNoteEditorClick)
                HomeToolTile(
                    icon = Icons.Filled.DeleteOutline,
                    label = if (trashCount > 0) "Bin ($trashCount)" else "Recycle Bin",
                    tint = BinaryOrchid,
                    modifier = Modifier.weight(1f),
                    testTag = "tool_recycle_bin",
                    onClick = onRecycleBinClick
                )
                HomeToolTile(Icons.Filled.CreateNewFolder, "Mount SAF", BinaryAmber, Modifier.weight(1f), "tool_mount_saf", onClick = onMountFolderClick)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                HomeToolTile(Icons.Filled.PhotoLibrary, "Import Media", BinaryEmerald, Modifier.weight(1f), "tool_import_media", onClick = onImportMediaClick)
                HomeToolTile(Icons.Filled.FileOpen, "Import Docs", BinaryBlue, Modifier.weight(1f), "tool_import_docs", onClick = onImportDocsClick)
                HomeToolTile(Icons.Filled.Folder, "Other Files", BinaryAmber, Modifier.weight(1f), "category_tile_other", onClick = onOpenOtherClick)
                HomeToolTile(Icons.Filled.Bookmark, "Bookmarks", BinaryOrchid, Modifier.weight(1f), "tool_bookmarks", onClick = onOpenBookmarksClick)
            }
        }
    }
}

@Composable
private fun HomeToolTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    testTag: String,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    val d = ArcTheme.dimens
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(d.smallRadius))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(d.toolCircleSize)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = tint)
            }
            if (badgeCount > 0) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = tint,
                    modifier = Modifier.padding(top = 1.dp, end = 1.dp)
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun HomeExpandableBarCard(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenFull: () -> Unit,
    cardTag: String,
    eyeTag: String,
    expandedContent: @Composable () -> Unit
) {
    val d = ArcTheme.dimens
    ArcCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(cardTag),
        cornerRadius = d.cardRadius,
        onClick = onOpenFull
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (count > 0) {
                    ArcBadge(text = "$count")
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(40.dp).testTag(eyeTag)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = if (isExpanded) "Hide $title preview" else "Show $title preview",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open $title",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    expandedContent()
                }
            }
        }
    }
}

@Composable
fun RecentFileRow(
    item: FileItem,
    monochrome: Boolean,
    showExtensions: Boolean = true,
    onClick: () -> Unit
) {
    val d = ArcTheme.dimens
    val label = if (item.isDirectory || showExtensions) item.name else item.name.substringBeforeLast('.').ifBlank { item.name }
    val fadeAlpha = if (item.isHiddenOrSystem) 0.54f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.smallRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("recent_file_${item.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.graphicsLayer(alpha = fadeAlpha)) {
            ArcThumbnail(item = item, monochromeBinaryIcons = monochrome, size = d.fileThumbnailSize, iconSize = d.fileIconSize)
        }
        Column(modifier = Modifier.weight(1f).graphicsLayer(alpha = fadeAlpha)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${FormatUtils.formatBytes(item.sizeBytes)} · ${FormatUtils.formatShortDate(item.lastModifiedMillis)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BookmarkPreviewRow(
    entry: BookmarkEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val d = ArcTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.smallRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.pathOrUri, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Remove bookmark", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AllFilesAccessBanner(onGrantClick: () -> Unit) {
    val d = ArcTheme.dimens
    ArcCard(
        modifier = Modifier.fillMaxWidth().testTag("home_all_files_banner"),
        cornerRadius = d.cardRadius,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
        onClick = onGrantClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(d.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.all_files_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.all_files_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            ArcBadge(
                text = stringResource(R.string.grant_short),
                containerColor = MaterialTheme.colorScheme.primary,
                textColor = MaterialTheme.colorScheme.onPrimary,
                borderColor = null
            )
        }
    }
}
