package com.example.ui.components

import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.BookmarkEntry
import com.example.data.DriveInfo
import com.example.data.DriveType
import com.example.data.ExplorerCategory
import com.example.data.FileTag
import com.example.data.FormatUtils
import com.example.ui.components.common.ArcCard
import com.example.ui.components.common.TagDot
import com.example.ui.theme.ArcTheme
import com.example.ui.theme.BinaryAmber
import com.example.ui.theme.BinaryBlue
import com.example.ui.theme.BinaryEmerald
import com.example.ui.theme.BinaryIndigo
import com.example.ui.theme.BinaryOrchid
import com.example.ui.theme.ThemePreference

@Composable
fun ArcSidebarDrawer(
    drives: List<DriveInfo>,
    bookmarks: List<BookmarkEntry>,
    tags: List<FileTag> = emptyList(),
    taggedCountsByTagId: Map<String, Int> = emptyMap(),
    indexedFileCount: Int,
    trashCount: Int,
    hasAllFilesAccess: Boolean,
    showHiddenFiles: Boolean,
    themePreference: ThemePreference,
    onCloseDrawer: () -> Unit,
    onOpenDrive: (DriveInfo) -> Unit,
    onOpenPublicFolder: (String, String) -> Unit,
    onOpenBookmark: (BookmarkEntry) -> Unit,
    onOpenTag: (String) -> Unit = {},
    onOpenCategory: (ExplorerCategory) -> Unit,
    onOpenAnalyzer: () -> Unit,
    onOpenTrash: () -> Unit,
    onMountFolder: () -> Unit,
    onImportMedia: () -> Unit,
    onNewNote: () -> Unit,
    onToggleHiddenFiles: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onCycleTheme: () -> Unit,
    onSelectTheme: (ThemePreference) -> Unit = { onCycleTheme() },
    onOpenSettings: () -> Unit
) {
    var favoriteOpen by remember { mutableStateOf(false) }
    var tagsOpen by remember { mutableStateOf(true) }
    var localOpen by remember { mutableStateOf(true) }
    var libraryOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }

    val internalDrive = drives.firstOrNull { it.type == DriveType.INTERNAL }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxWidth(0.84f)
            .widthIn(max = 316.dp)
            .fillMaxHeight()
            .testTag("arc_sidebar_drawer"),
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerTonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .testTag("arc_sidebar_list"),
            contentPadding = PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = statusTop + 10.dp,
                bottom = navBottom + 14.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                DrawerHeaderCard(
                    indexedCount = indexedFileCount,
                    freeBytes = internalDrive?.freeBytes ?: 0L,
                    hasAllFilesAccess = hasAllFilesAccess
                )
            }

            item {
                ArcCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = ArcTheme.dimens.cardRadius
                ) {
                    DrawerAccordionHeader(
                        icon = Icons.Filled.Star,
                        iconBadgeColor = BinaryAmber,
                        title = "Favorites",
                        badge = if (bookmarks.isNotEmpty()) "${bookmarks.size}" else null,
                        expanded = favoriteOpen,
                        onClick = { favoriteOpen = !favoriteOpen },
                        testTag = "drawer_group_favorites"
                    )
                    AnimatedVisibility(visible = favoriteOpen) {
                        Column(modifier = Modifier.padding(start = 18.dp, end = 6.dp, bottom = 6.dp)) {
                            if (bookmarks.isEmpty()) {
                                Text(
                                    text = "Bookmark any folder from the browser menu",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            } else {
                                bookmarks.forEach { bm ->
                                    DrawerSubItem(
                                        title = bm.title,
                                        onClick = { onCloseDrawer(); onOpenBookmark(bm) }
                                    )
                                }
                            }
                        }
                    }
                    DrawerHairlineDivider()
                    DrawerAccordionHeader(
                        icon = Icons.Filled.Smartphone,
                        iconBadgeColor = BinaryBlue,
                        title = "Locations",
                        expanded = localOpen,
                        onClick = { localOpen = !localOpen },
                        testTag = "drawer_group_local"
                    )
                    AnimatedVisibility(visible = localOpen) {
                        Column(modifier = Modifier.padding(start = 18.dp, end = 6.dp, bottom = 6.dp)) {
                            drives.forEach { drive ->
                                DrawerSubItem(
                                    title = drive.name,
                                    trailing = if (drive.totalBytes > 0L) FormatUtils.formatBytes(drive.freeBytes) else null,
                                    onClick = { onCloseDrawer(); onOpenDrive(drive) }
                                )
                            }
                            DrawerSubItem("Download", onClick = { onCloseDrawer(); onOpenPublicFolder(Environment.DIRECTORY_DOWNLOADS, "Download") })
                            DrawerSubItem("DCIM", onClick = { onCloseDrawer(); onOpenPublicFolder(Environment.DIRECTORY_DCIM, "DCIM") })
                            DrawerSubItem("Documents", onClick = { onCloseDrawer(); onOpenPublicFolder(Environment.DIRECTORY_DOCUMENTS, "Documents") })
                            DrawerSubItem("+ Mount Folder (SAF)", onClick = { onCloseDrawer(); onMountFolder() })
                        }
                    }
                    DrawerHairlineDivider()
                    DrawerAccordionHeader(
                        icon = Icons.Filled.Layers,
                        iconBadgeColor = BinaryIndigo,
                        title = "Library",
                        expanded = libraryOpen,
                        onClick = { libraryOpen = !libraryOpen },
                        testTag = "drawer_group_library"
                    )
                    AnimatedVisibility(visible = libraryOpen) {
                        Column(modifier = Modifier.padding(start = 18.dp, end = 6.dp, bottom = 6.dp)) {
                            ExplorerCategory.entries.forEach { cat ->
                                DrawerSubItem(
                                    title = cat.label,
                                    onClick = { onCloseDrawer(); onOpenCategory(cat) }
                                )
                            }
                        }
                    }
                }
            }

            if (tags.isNotEmpty()) {
                item {
                    val totalTagged = taggedCountsByTagId.values.sum()
                    ArcCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = ArcTheme.dimens.cardRadius
                    ) {
                        DrawerAccordionHeader(
                            icon = Icons.AutoMirrored.Filled.Label,
                            iconBadgeColor = BinaryOrchid,
                            title = "Tags",
                            badge = if (totalTagged > 0) "$totalTagged" else null,
                            expanded = tagsOpen,
                            onClick = { tagsOpen = !tagsOpen },
                            testTag = "drawer_group_tags"
                        )
                        AnimatedVisibility(visible = tagsOpen) {
                            Column(modifier = Modifier.padding(start = 16.dp, end = 6.dp, bottom = 6.dp)) {
                                tags.forEach { tag ->
                                    val count = taggedCountsByTagId[tag.id] ?: 0
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                onCloseDrawer()
                                                onOpenTag(tag.id)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp)
                                            .testTag("drawer_tag_${tag.id}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        TagDot(colorHex = tag.colorHex, size = 11.dp)
                                        Text(
                                            text = tag.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (count > 0) {
                                            Text(
                                                text = "$count",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                ArcCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = ArcTheme.dimens.cardRadius
                ) {
                    DrawerAccordionHeader(
                        icon = Icons.Filled.Build,
                        iconBadgeColor = BinaryEmerald,
                        title = "Tools",
                        expanded = toolsOpen,
                        onClick = { toolsOpen = !toolsOpen },
                        testTag = "drawer_group_tools"
                    )
                    AnimatedVisibility(visible = toolsOpen) {
                        Column(modifier = Modifier.padding(start = 18.dp, end = 6.dp, bottom = 6.dp)) {
                            DrawerSubItem("Space Analyzer", onClick = { onCloseDrawer(); onOpenAnalyzer() })
                            DrawerSubItem("Recycle Bin", trailing = if (trashCount > 0) "$trashCount" else null, onClick = { onCloseDrawer(); onOpenTrash() })
                            DrawerSubItem("Note Editor", onClick = { onCloseDrawer(); onNewNote() })
                            DrawerSubItem("Import Media", onClick = { onCloseDrawer(); onImportMedia() })
                        }
                    }
                    DrawerHairlineDivider()
                    DrawerPillActionRow(
                        icon = Icons.Filled.Visibility,
                        title = "Hidden files",
                        pillText = if (showHiddenFiles) "SHOWN" else "HIDE",
                        pillActive = showHiddenFiles,
                        onClick = onToggleHiddenFiles,
                        testTag = "drawer_hidden_files_toggle"
                    )
                    DrawerHairlineDivider()
                    DrawerPillActionRow(
                        icon = Icons.Filled.LockOpen,
                        title = "All files access",
                        pillText = if (hasAllFilesAccess) "GRANTED" else "UNLOCK",
                        pillActive = hasAllFilesAccess,
                        onClick = { onCloseDrawer(); onRequestAllFilesAccess() },
                        testTag = "drawer_all_files_button"
                    )
                }
            }

            item {
                ArcCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = ArcTheme.dimens.cardRadius
                ) {
                    DrawerThemeSegmentedRow(
                        themePreference = themePreference,
                        onCycleTheme = onCycleTheme,
                        onSelectTheme = onSelectTheme
                    )
                    DrawerHairlineDivider()
                    DrawerNavRow(
                        icon = Icons.Filled.Settings,
                        title = "Settings",
                        onClick = { onCloseDrawer(); onOpenSettings() },
                        testTag = "drawer_settings_item"
                    )
                    DrawerHairlineDivider()
                    DrawerNavRow(
                        icon = Icons.Filled.Close,
                        title = "Close Menu",
                        showChevron = false,
                        onClick = onCloseDrawer,
                        testTag = "drawer_close_item"
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerHairlineDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 46.dp, end = 12.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    )
}

@Composable
private fun DrawerHeaderCard(
    indexedCount: Int,
    freeBytes: Long,
    hasAllFilesAccess: Boolean
) {
    ArcCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = ArcTheme.dimens.cardRadius
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Arc Files",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${FormatUtils.formatBytes(freeBytes)} free · $indexedCount indexed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasAllFilesAccess) "Full Storage Active" else "Scoped Storage Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (hasAllFilesAccess) "Unlocked" else "Limited",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerAccordionHeader(
    icon: ImageVector,
    iconBadgeColor: Color,
    title: String,
    badge: String? = null,
    expanded: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(iconBadgeColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (badge != null) {
            Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DrawerSubItem(
    title: String,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DrawerPillActionRow(
    icon: ImageVector,
    title: String,
    pillText: String,
    pillActive: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (pillActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        ) {
            Text(
                text = pillText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (pillActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun DrawerNavRow(
    icon: ImageVector,
    title: String,
    trailingText: String? = null,
    showChevron: Boolean = true,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (trailingText != null) {
            Text(trailingText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (showChevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DrawerThemeSegmentedRow(
    themePreference: ThemePreference,
    onCycleTheme: () -> Unit,
    onSelectTheme: (ThemePreference) -> Unit
) {
    val activeIcon = when (themePreference) {
        ThemePreference.LIGHT -> Icons.Filled.LightMode
        ThemePreference.DARK -> Icons.Filled.DarkMode
        ThemePreference.SYSTEM -> Icons.Filled.BrightnessAuto
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCycleTheme)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag("drawer_theme_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = activeIcon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Theme",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Triple(ThemePreference.LIGHT, Icons.Filled.LightMode, "Light"),
                    Triple(ThemePreference.DARK, Icons.Filled.DarkMode, "Dark"),
                    Triple(ThemePreference.SYSTEM, Icons.Filled.BrightnessAuto, "Auto")
                ).forEach { (pref, icon, label) ->
                    val selected = themePreference == pref
                    val bgColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "drawer_theme_pill_bg_${pref.name}"
                    )
                    val tintColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "drawer_theme_pill_tint_${pref.name}"
                    )
                    Surface(
                        modifier = Modifier
                            .animateContentSize(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectTheme(pref) }
                            .testTag("drawer_theme_${pref.name.lowercase()}"),
                        shape = RoundedCornerShape(8.dp),
                        color = bgColor,
                        shadowElevation = if (selected) 1.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(13.dp),
                                tint = tintColor
                            )
                            if (selected) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tintColor,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
