package com.example.ui.screens.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AppStorageSnapshot
import com.example.data.FormatUtils
import com.example.data.ManagedStorageEntry
import com.example.data.ManagedStorageId
import com.example.data.PluginStorageStats
import com.example.data.StorageDataKind
import com.example.data.StorageDomainGroup
import com.example.ui.components.common.ArcBadge
import com.example.ui.components.common.ArcCard
import com.example.ui.components.common.ArcModalCard
import com.example.ui.components.common.ConfigureEdgeToEdgeDialogWindow
import com.example.ui.theme.ArcTheme

private enum class CacheFilterTab(val id: String, val label: String) {
    ALL("all", "All"),
    LIBRARY("library", "Library"),
    PLUGINS("plugins", "Plugins"),
    FOLDERS_COMMON("folders_common", "Folders & Common")
}

private sealed interface PendingConfirmAction {
    val title: String
    val message: String
    val confirmLabel: String

    data class Entry(
        val id: ManagedStorageId,
        override val title: String,
        override val message: String,
        override val confirmLabel: String = "Reset Data"
    ) : PendingConfirmAction

    data class PluginData(
        val pluginId: String,
        override val title: String,
        override val message: String,
        override val confirmLabel: String = "Clear Data"
    ) : PendingConfirmAction
}

@Composable
fun CacheDataManagerScreen(
    snapshot: AppStorageSnapshot,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onClearStorageEntry: (ManagedStorageId) -> Unit,
    onClearPluginCache: (String) -> Unit,
    onClearPluginData: (String) -> Unit,
    onClearAllCaches: () -> Unit,
    isActivePage: Boolean = true,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = isActivePage) { onBack() }

    val d = ArcTheme.dimens
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var activeFilter by remember { mutableStateOf(CacheFilterTab.ALL) }
    var pendingConfirm by remember { mutableStateOf<PendingConfirmAction?>(null) }

    val libraryEntries = remember(snapshot.entries) {
        snapshot.entries.filter { it.id.group == StorageDomainGroup.LIBRARY }
    }
    val commonEntries = remember(snapshot.entries) {
        snapshot.entries.filter { it.id.group == StorageDomainGroup.FOLDERS_COMMON }
    }

    val totalManagedFormatted = remember(snapshot.totalManagedBytes) {
        if (snapshot.totalManagedBytes > 0L) FormatUtils.formatBytes(snapshot.totalManagedBytes) else "0 B"
    }
    val totalCachesFormatted = remember(snapshot.totalCacheBytes) {
        if (snapshot.totalCacheBytes > 0L) FormatUtils.formatBytes(snapshot.totalCacheBytes) else "0 B"
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = d.maxContentWidth)
                .fillMaxSize()
                .testTag("cache_manager_list"),
            contentPadding = PaddingValues(
                start = d.screenHorizontalPadding,
                end = d.screenHorizontalPadding,
                top = 4.dp,
                bottom = navBottom + 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(d.sectionGap)
        ) {
            // Top bar: Back to Settings + Title + Total badge + Refresh
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onBack)
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                .testTag("cache_manager_back_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back to Settings",
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Cache & Data",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ArcBadge(text = totalManagedFormatted)
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("cache_manager_refresh")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Recalculate cache usage",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Overview Breakdown Card
            item {
                StorageOverviewCard(
                    snapshot = snapshot,
                    totalManagedFormatted = totalManagedFormatted,
                    totalCachesFormatted = totalCachesFormatted,
                    onClearAllCaches = onClearAllCaches
                )
            }

            // Category Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(CacheFilterTab.entries, key = { it.id }) { tab ->
                        val selected = activeFilter == tab
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .clickable { activeFilter = tab }
                                .testTag("cache_filter_${tab.id}"),
                            shape = RoundedCornerShape(99.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = if (selected) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Section 1: Library Caches & Virtual Albums
            if (activeFilter == CacheFilterTab.ALL || activeFilter == CacheFilterTab.LIBRARY) {
                item {
                    val libSubtitle = if (snapshot.totalLibraryBytes > 0L) {
                        FormatUtils.formatBytes(snapshot.totalLibraryBytes)
                    } else {
                        "Isolated per section"
                    }
                    SettingsGroupCard(
                        title = StorageDomainGroup.LIBRARY.label,
                        subtitle = libSubtitle
                    ) {
                        libraryEntries.forEachIndexed { idx, entry ->
                            if (idx > 0) SettingsDivider()
                            ManagedStorageEntryRow(
                                entry = entry,
                                onActionClick = {
                                    if (entry.id.dataKind == StorageDataKind.REGENERABLE_CACHE) {
                                        onClearStorageEntry(entry.id)
                                    } else {
                                        pendingConfirm = PendingConfirmAction.Entry(
                                            id = entry.id,
                                            title = "Clear ${entry.id.title}?",
                                            message = "This removes virtual album collections only. Original files stored on your device will stay completely untouched."
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Section 2: Plugins & Built-in Viewers (Isolated Cache vs Plugin Data)
            if (activeFilter == CacheFilterTab.ALL || activeFilter == CacheFilterTab.PLUGINS) {
                item {
                    val pluginSubtitle = if (snapshot.totalPluginsBytes > 0L) {
                        FormatUtils.formatBytes(snapshot.totalPluginsBytes)
                    } else {
                        "${snapshot.plugins.size} slots"
                    }
                    SettingsGroupCard(
                        title = StorageDomainGroup.PLUGINS.label,
                        subtitle = pluginSubtitle
                    ) {
                        snapshot.plugins.forEachIndexed { idx, plugin ->
                            if (idx > 0) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                )
                            }
                            PluginStorageCardRows(
                                plugin = plugin,
                                onClearCache = { onClearPluginCache(plugin.spec.id) },
                                onClearData = {
                                    pendingConfirm = PendingConfirmAction.PluginData(
                                        pluginId = plugin.spec.id,
                                        title = "Clear ${plugin.spec.title} Data?",
                                        message = "Deletes saved ${plugin.spec.dataDescription.lowercase()} stored by this plugin. Media & document files on your device are never touched."
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Section 3: Folders & Common Data
            if (activeFilter == CacheFilterTab.ALL || activeFilter == CacheFilterTab.FOLDERS_COMMON) {
                item {
                    val commonSubtitle = if (snapshot.totalCommonBytes > 0L) {
                        FormatUtils.formatBytes(snapshot.totalCommonBytes)
                    } else {
                        "Browser & Explorer"
                    }
                    SettingsGroupCard(
                        title = StorageDomainGroup.FOLDERS_COMMON.label,
                        subtitle = commonSubtitle
                    ) {
                        commonEntries.forEachIndexed { idx, entry ->
                            if (idx > 0) SettingsDivider()
                            ManagedStorageEntryRow(
                                entry = entry,
                                onActionClick = {
                                    if (entry.id.dataKind == StorageDataKind.REGENERABLE_CACHE) {
                                        onClearStorageEntry(entry.id)
                                    } else {
                                        pendingConfirm = PendingConfirmAction.Entry(
                                            id = entry.id,
                                            title = "Reset ${entry.id.title}?",
                                            message = when (entry.id) {
                                                ManagedStorageId.COMMON_FOLDER_LAYOUTS ->
                                                    "Resets all saved per-folder view modes, grid column counts, and custom drag-drop sort sequences back to defaults."
                                                ManagedStorageId.COMMON_DEFAULT_APPS ->
                                                    "Clears remembered Always Open With app rules so Arc Files asks before opening those formats next time."
                                                ManagedStorageId.COMMON_BOOKMARKS ->
                                                    "Removes all pinned folder and file shortcuts from Home. The actual folders stay on storage."
                                                ManagedStorageId.COMMON_FILE_TAGS ->
                                                    "Unlinks all file tags and removes custom tags while keeping default color labels. Original files stay untouched."
                                                else -> "Removes this metadata cleanly while leaving all user files intact."
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingConfirm?.let { confirm ->
        ConfirmClearDataDialog(
            title = confirm.title,
            message = confirm.message,
            confirmLabel = confirm.confirmLabel,
            onDismiss = { pendingConfirm = null },
            onConfirm = {
                pendingConfirm = null
                when (confirm) {
                    is PendingConfirmAction.Entry -> onClearStorageEntry(confirm.id)
                    is PendingConfirmAction.PluginData -> onClearPluginData(confirm.pluginId)
                }
            }
        )
    }
}

@Composable
private fun StorageOverviewCard(
    snapshot: AppStorageSnapshot,
    totalManagedFormatted: String,
    totalCachesFormatted: String,
    onClearAllCaches: () -> Unit
) {
    val d = ArcTheme.dimens
    val libColor = MaterialTheme.colorScheme.primary
    val pluginColor = Color(0xFFAF52DE)
    val commonColor = Color(0xFFFF9500)

    val total = snapshot.totalManagedBytes.coerceAtLeast(1L).toFloat()
    val libFrac = (snapshot.totalLibraryBytes / total).coerceIn(0f, 1f)
    val pluginFrac = (snapshot.totalPluginsBytes / total).coerceIn(0f, 1f)
    val commonFrac = (snapshot.totalCommonBytes / total).coerceIn(0f, 1f)

    ArcCard(modifier = Modifier.fillMaxWidth(), cornerRadius = d.cardRadius) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(d.cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IosSettingsIconBadge(icon = Icons.Filled.Storage, color = libColor)
                    Column {
                        Text(
                            text = "App Storage & Cache",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Regenerable caches: $totalCachesFormatted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = totalManagedFormatted,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Segmented Storage Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (snapshot.totalManagedBytes > 0L) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (libFrac > 0.005f) {
                            Box(
                                modifier = Modifier
                                    .weight(libFrac)
                                    .height(8.dp)
                                    .background(libColor)
                            )
                        }
                        if (pluginFrac > 0.005f) {
                            Box(
                                modifier = Modifier
                                    .weight(pluginFrac)
                                    .height(8.dp)
                                    .background(pluginColor)
                            )
                        }
                        if (commonFrac > 0.005f) {
                            Box(
                                modifier = Modifier
                                    .weight(commonFrac)
                                    .height(8.dp)
                                    .background(commonColor)
                            )
                        }
                    }
                }
            }

            // Legend row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendPill("Library", snapshot.totalLibraryBytes, libColor)
                LegendPill("Plugins", snapshot.totalPluginsBytes, pluginColor)
                LegendPill("Folders", snapshot.totalCommonBytes, commonColor)
            }

            // Quick Safe Action: Clear All Caches (does not touch albums/layouts/bookmarks/plugin state)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClearAllCaches)
                    .testTag("cache_manager_clear_all_caches"),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CleaningServices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Clear All Regenerable Caches",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Keeps Albums, Folder Layouts, Bookmarks & Plugin states safe",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    ArcBadge(
                        text = totalCachesFormatted,
                        containerColor = MaterialTheme.colorScheme.primary,
                        textColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendPill(label: String, bytes: Long, dotColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = "$label · ${if (bytes > 0L) FormatUtils.formatBytes(bytes) else "0 B"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ManagedStorageEntryRow(
    entry: ManagedStorageEntry,
    onActionClick: () -> Unit
) {
    val d = ArcTheme.dimens
    val spec = entry.id
    val isCache = spec.dataKind == StorageDataKind.REGENERABLE_CACHE
    val badgeColor = when (spec) {
        ManagedStorageId.LIB_IMAGES_THUMBS -> Color(0xFF007AFF)
        ManagedStorageId.LIB_MOVIES_THUMBS -> Color(0xFF5856D6)
        ManagedStorageId.LIB_IMAGES_ALBUMS -> Color(0xFF34C759)
        ManagedStorageId.LIB_MOVIES_ALBUMS -> Color(0xFFAF52DE)
        ManagedStorageId.COMMON_BROWSER_PREVIEWS -> Color(0xFFFF9500)
        ManagedStorageId.COMMON_STAGING_TEMP -> Color(0xFF00C7BE)
        ManagedStorageId.COMMON_FOLDER_LAYOUTS -> Color(0xFF007AFF)
        ManagedStorageId.COMMON_DEFAULT_APPS -> Color(0xFF5856D6)
        ManagedStorageId.COMMON_BOOKMARKS -> Color(0xFFFF3B30)
        ManagedStorageId.COMMON_FILE_TAGS -> Color(0xFFFF9500)
    }
    val icon: ImageVector = when (spec) {
        ManagedStorageId.LIB_IMAGES_THUMBS -> Icons.Filled.PhotoLibrary
        ManagedStorageId.LIB_MOVIES_THUMBS -> Icons.Filled.VideoLibrary
        ManagedStorageId.LIB_IMAGES_ALBUMS -> Icons.Filled.CollectionsBookmark
        ManagedStorageId.LIB_MOVIES_ALBUMS -> Icons.Filled.Movie
        ManagedStorageId.COMMON_BROWSER_PREVIEWS -> Icons.Filled.FolderOpen
        ManagedStorageId.COMMON_STAGING_TEMP -> Icons.Filled.Cached
        ManagedStorageId.COMMON_FOLDER_LAYOUTS -> Icons.AutoMirrored.Filled.ViewQuilt
        ManagedStorageId.COMMON_DEFAULT_APPS -> Icons.Filled.Apps
        ManagedStorageId.COMMON_BOOKMARKS -> Icons.Filled.Bookmark
        ManagedStorageId.COMMON_FILE_TAGS -> Icons.AutoMirrored.Filled.Label
    }

    val metricSummary = remember(entry.sizeBytes, entry.itemCount, spec) {
        if (entry.sizeBytes > 0L) {
            "${FormatUtils.formatBytes(entry.sizeBytes)} · ${entry.itemCount} ${spec.itemUnitLabel}"
        } else if (isCache) {
            "0 B · ${entry.itemCount} ${spec.itemUnitLabel}"
        } else {
            "${entry.itemCount} ${spec.itemUnitLabel}"
        }
    }

    val hasData = entry.sizeBytes > 0L || entry.itemCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .padding(horizontal = d.cardPadding, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IosSettingsIconBadge(icon = icon, color = badgeColor)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = spec.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ArcBadge(
                    text = metricSummary,
                    containerColor = if (hasData) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
                    textColor = if (hasData) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = spec.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ActionPillButton(
            label = if (isCache) "Clear" else "Reset",
            enabled = hasData,
            isDestructive = !isCache && hasData,
            testTag = "clear_storage_${spec.id}",
            onClick = onActionClick
        )
    }
}

@Composable
private fun PluginStorageCardRows(
    plugin: PluginStorageStats,
    onClearCache: () -> Unit,
    onClearData: () -> Unit
) {
    val d = ArcTheme.dimens
    val spec = plugin.spec
    val icon: ImageVector = when (spec.id) {
        "video_player" -> Icons.Filled.Movie
        "pdf_reader" -> Icons.Filled.PictureAsPdf
        "text_editor" -> Icons.Filled.EditNote
        "archive_tools" -> Icons.Filled.FolderZip
        else -> Icons.Filled.Storage
    }
    val badgeColor = when (spec.id) {
        "video_player" -> Color(0xFF5856D6)
        "pdf_reader" -> Color(0xFFFF3B30)
        "text_editor" -> Color(0xFF007AFF)
        "archive_tools" -> Color(0xFFFF9500)
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = d.cardPadding, vertical = 12.dp)
            .testTag("plugin_storage_block_${spec.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Plugin header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IosSettingsIconBadge(icon = icon, color = badgeColor)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = spec.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    ArcBadge(
                        text = if (spec.isActiveBuiltin) "Active" else "Ready",
                        containerColor = if (spec.isActiveBuiltin) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        textColor = if (spec.isActiveBuiltin) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Text(
                    text = spec.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (plugin.totalBytes > 0L) FormatUtils.formatBytes(plugin.totalBytes) else "0 B",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (plugin.totalBytes > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Split sub-rows: Cache vs Plugin User Data
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val cacheHasData = plugin.cacheBytes > 0L || plugin.cacheFileCount > 0
                val dataHasData = plugin.dataBytes > 0L || plugin.dataFileCount > 0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "Cache · ${if (plugin.cacheBytes > 0L) FormatUtils.formatBytes(plugin.cacheBytes) else "0 B"} (${plugin.cacheFileCount} files)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = spec.cacheDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ActionPillButton(
                        label = "Clear Cache",
                        enabled = cacheHasData,
                        isDestructive = false,
                        testTag = "clear_plugin_cache_${spec.id}",
                        onClick = onClearCache
                    )
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "Plugin Data · ${if (plugin.dataBytes > 0L) FormatUtils.formatBytes(plugin.dataBytes) else "0 B"} (${plugin.dataFileCount} files)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = spec.dataDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ActionPillButton(
                        label = "Clear Data",
                        enabled = dataHasData,
                        isDestructive = dataHasData,
                        testTag = "clear_plugin_data_${spec.id}",
                        onClick = onClearData
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPillButton(
    label: String,
    enabled: Boolean,
    isDestructive: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
        isDestructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(99.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun ConfirmClearDataDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        ConfigureEdgeToEdgeDialogWindow()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            ArcModalCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .clickable(enabled = false, onClick = {})
                    .testTag("confirm_clear_data_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                        }
                    }

                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = onConfirm,
                            shape = RoundedCornerShape(99.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.testTag("confirm_clear_data_button")
                        ) {
                            Text(confirmLabel)
                        }
                    }
                }
            }
        }
    }
}
