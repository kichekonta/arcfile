package com.example.ui.screens.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.CacheBucket
import com.example.data.CacheBucketStats
import com.example.data.DriveInfo
import com.example.data.ExplorerPreferences
import com.example.data.FormatUtils
import com.example.data.GroupByField
import com.example.data.SortField
import com.example.data.ViewMode
import com.example.ui.components.common.ArcBadge
import com.example.ui.theme.ArcTheme
import com.example.ui.theme.ThemePreference

@Composable
fun SettingsScreen(
    prefs: ExplorerPreferences,
    drives: List<DriveInfo>,
    indexedFileCount: Int,
    hasAllFilesAccess: Boolean,
    onBack: () -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onMonochromeChange: (Boolean) -> Unit,
    onShowFileExtensionsChange: (Boolean) -> Unit,
    onCompactListChange: (Boolean) -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onOriginalMediaRatioChange: (Boolean) -> Unit,
    onSortFieldChange: (SortField) -> Unit,
    onSortAscendingChange: (Boolean) -> Unit,
    onGroupByChange: (GroupByField) -> Unit,
    onFoldersFirstChange: (Boolean) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit,
    onShowFolderCountsChange: (Boolean) -> Unit,
    onRecycleBinEnabledChange: (Boolean) -> Unit = {},
    onConfirmBeforeDeleteChange: (Boolean) -> Unit,
    onUseBuiltinTextViewerChange: (Boolean) -> Unit = {},
    onDragReorderChange: (Boolean) -> Unit = {},
    onClearDefaultOpenApp: (String) -> Unit = {},
    onClearAllDefaultOpenApps: () -> Unit = {},
    cacheStats: List<CacheBucketStats> = emptyList(),
    totalManagedCacheBytes: Long = cacheStats.sumOf { it.sizeBytes },
    onOpenCacheManager: () -> Unit = {},
    onClearCacheBucket: (CacheBucket) -> Unit = {},
    onClearAllCaches: () -> Unit = {},
    onRequestAllFilesAccess: () -> Unit,
    onMountFolderClick: () -> Unit,
    onUnmountSafDrive: (DriveInfo) -> Unit,
    onReindexStorage: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onResetDefaults: () -> Unit,
    isActivePage: Boolean = true,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = isActivePage) { onBack() }
    val d = ArcTheme.dimens
    val safDrives = drives.filter { it.isSaf }
    val savedDefaultApps = prefs.parsedDefaultOpenApps().values.sortedBy { it.key }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = d.maxContentWidth)
                .fillMaxSize()
                .testTag("settings_screen_list"),
            contentPadding = PaddingValues(
                start = d.screenHorizontalPadding,
                end = d.screenHorizontalPadding,
                top = 4.dp,
                bottom = navBottom + 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(d.sectionGap)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onBack)
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                .testTag("settings_back_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back",
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Back", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                    ArcBadge(text = "v${BuildConfig.VERSION_NAME.uppercase()}")
                }
            }

            item {
                SettingsGroupCard(title = "Appearance", subtitle = "iOS Clean") {
                    SettingsChoiceRow(
                        icon = Icons.Filled.Palette,
                        title = "Theme Mode",
                        options = listOf(ThemePreference.LIGHT, ThemePreference.DARK, ThemePreference.SYSTEM),
                        selected = prefs.themePreference,
                        labelOf = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                        onSelect = onThemeChange,
                        tagPrefix = "settings_theme"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Filled.Style,
                        title = "Monochrome Icons",
                        subtitle = "Render folders and file badges in minimal monochrome line-art",
                        checked = prefs.monochromeBinaryIcons,
                        onCheckedChange = onMonochromeChange,
                        testTag = "toggle_monochrome_icons"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Filled.Info,
                        title = "Show File Extensions",
                        subtitle = "Display .pdf, .jpg, .zip suffixes in file lists and grids",
                        checked = prefs.showFileExtensions,
                        onCheckedChange = onShowFileExtensionsChange,
                        testTag = "toggle_file_extensions"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Filled.FormatListNumbered,
                        title = "Compact List Rows",
                        subtitle = "Tighter vertical density for browsing large directories",
                        checked = prefs.compactListMode,
                        onCheckedChange = onCompactListChange,
                        testTag = "toggle_compact_list"
                    )
                }
            }

            item {
                SettingsGroupCard(title = "Browsing & Sorting", subtitle = "Explorer Defaults") {
                    SettingsChoiceRow(
                        icon = Icons.Filled.GridView,
                        title = "Default Layout",
                        options = ViewMode.entries,
                        selected = prefs.viewMode,
                        labelOf = { if (it == ViewMode.LIST) "List" else "Grid" },
                        onSelect = onViewModeChange,
                        tagPrefix = "settings_view"
                    )
                    SettingsDivider()
                    SettingsChoiceRow(
                        icon = Icons.Filled.GridView,
                        title = "Grid Size (Columns)",
                        options = listOf(2, 3, 4),
                        selected = prefs.gridColumns,
                        labelOf = { "${it}×" },
                        onSelect = onGridColumnsChange,
                        tagPrefix = "settings_grid_cols"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Filled.AspectRatio,
                        title = "Original Media Ratio",
                        subtitle = "Keep photos & videos in their true aspect ratio in grid and preview",
                        checked = prefs.originalMediaRatio,
                        onCheckedChange = onOriginalMediaRatioChange,
                        testTag = "toggle_original_ratio"
                    )
                    SettingsDivider()
                    SettingsChoiceRow(
                        icon = Icons.AutoMirrored.Filled.Sort,
                        title = "Sort By",
                        options = SortField.entries,
                        selected = prefs.sortField,
                        labelOf = { it.label },
                        onSelect = onSortFieldChange,
                        tagPrefix = "settings_sort"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.AutoMirrored.Filled.Sort,
                        title = "Ascending Order",
                        subtitle = "A-Z, oldest-first, or smallest-first when enabled",
                        checked = prefs.sortAscending,
                        onCheckedChange = onSortAscendingChange,
                        testTag = "toggle_sort_ascending"
                    )
                    SettingsDivider()
                    SettingsChoiceRow(
                        icon = Icons.Filled.Category,
                        title = "Default Group By",
                        options = GroupByField.entries,
                        selected = prefs.groupBy,
                        labelOf = { it.label },
                        onSelect = onGroupByChange,
                        tagPrefix = "settings_group"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Filled.FolderOpen,
                        title = "Folders Before Files",
                        subtitle = "Always pin subdirectories above regular files",
                        checked = prefs.foldersFirst,
                        onCheckedChange = onFoldersFirstChange,
                        testTag = "toggle_folders_first"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Filled.Visibility,
                        title = "Show Hidden Files",
                        subtitle = "Include dotfiles and directories beginning with '.'",
                        checked = prefs.showHiddenFiles,
                        onCheckedChange = onShowHiddenChange,
                        testTag = "toggle_hidden_files"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Filled.FormatListNumbered,
                        title = "Show Folder Item Counts",
                        subtitle = "Calculate child item totals inside local directories",
                        checked = prefs.showFolderCounts,
                        onCheckedChange = onShowFolderCountsChange,
                        testTag = "toggle_folder_counts"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Filled.Description,
                        title = "Built-in Preview & Code Viewer",
                        subtitle = "Open HTML, Markdown & text in Arc Viewer (turn off to pick external apps)",
                        checked = prefs.useBuiltinTextViewer,
                        onCheckedChange = onUseBuiltinTextViewerChange,
                        testTag = "toggle_builtin_text_viewer"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Filled.DragIndicator,
                        title = "Drag & Drop Custom Sort",
                        subtitle = "Long-press & drag items over files or folder edges to reorder smoothly",
                        checked = prefs.dragReorderEnabled,
                        onCheckedChange = onDragReorderChange,
                        testTag = "toggle_drag_reorder"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Filled.DeleteOutline,
                        title = "Active Recycle Bin",
                        subtitle = "Move deleted files and folders to Recycle Bin so you can restore them later",
                        checked = prefs.recycleBinEnabled,
                        onCheckedChange = onRecycleBinEnabledChange,
                        testTag = "toggle_recycle_bin"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Filled.DeleteOutline,
                        title = "Confirm Before Delete",
                        subtitle = "Show confirmation prompt before moving to Recycle Bin or permanently deleting",
                        checked = prefs.confirmBeforeDelete,
                        onCheckedChange = onConfirmBeforeDeleteChange,
                        testTag = "toggle_confirm_delete"
                    )
                }
            }

            if (savedDefaultApps.isNotEmpty()) {
                item {
                    SettingsGroupCard(
                        title = "Default Open Apps",
                        subtitle = "${savedDefaultApps.size} saved"
                    ) {
                        savedDefaultApps.forEachIndexed { idx, entry ->
                            if (idx > 0) SettingsDivider()
                            val targetType = entry.key.removePrefix("ext:").removePrefix("mime:")
                            SettingsActionRow(
                                icon = Icons.Filled.Description,
                                title = ".$targetType → ${entry.appLabel}",
                                subtitle = if (entry.isBuiltinApp) "Arc Built-in Viewer · Tap to clear" else "External App · Tap to clear",
                                onClick = { onClearDefaultOpenApp(entry.key) },
                                testTag = "settings_clear_default_${entry.key}"
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear default for $targetType",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        SettingsDivider()
                        SettingsActionRow(
                            icon = Icons.Filled.RestartAlt,
                            title = "Reset All Default Open Apps",
                            subtitle = "Ask Built-in vs External app next time you open those files",
                            onClick = onClearAllDefaultOpenApps,
                            testTag = "settings_clear_default_apps"
                        )
                    }
                }
            }

            item {
                SettingsGroupCard(title = "Storage & Permissions", subtitle = "${safDrives.size} SAF mounts") {
                    SettingsActionRow(
                        icon = Icons.Filled.LockOpen,
                        title = "All Files Access",
                        subtitle = if (hasAllFilesAccess) "Full Internal Storage access granted" else "Tap to allow full storage browsing in Android Settings",
                        onClick = onRequestAllFilesAccess,
                        testTag = "settings_all_files_row"
                    ) {
                        ArcBadge(
                            text = if (hasAllFilesAccess) "Granted" else "Allow",
                            containerColor = if (hasAllFilesAccess) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                            textColor = if (hasAllFilesAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Filled.CreateNewFolder,
                        title = "Mount External Folder (SAF)",
                        subtitle = "Add SD card, USB OTG, or specific folder via system picker",
                        onClick = onMountFolderClick,
                        testTag = "settings_mount_saf_row"
                    )
                    safDrives.forEach { saf ->
                        SettingsDivider()
                        SettingsActionRow(
                            icon = Icons.Filled.FolderOpen,
                            title = saf.name,
                            subtitle = "Persisted SAF Drive · Tap to unmount",
                            onClick = { onUnmountSafDrive(saf) },
                            testTag = "settings_unmount_${saf.id}"
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Unmount ${saf.name}", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Filled.Refresh,
                        title = "Re-index Storage Now",
                        subtitle = "$indexedFileCount accessible files currently indexed",
                        onClick = onReindexStorage,
                        testTag = "settings_reindex_row"
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Filled.RestartAlt,
                        title = "Reset Explorer Preferences",
                        subtitle = "Restore default layout, sorting, and display toggles",
                        onClick = onResetDefaults,
                        testTag = "settings_reset_defaults_row"
                    )
                }
            }

            item {
                val badgeText = if (totalManagedCacheBytes > 0L) FormatUtils.formatBytes(totalManagedCacheBytes) else "0 B"
                SettingsGroupCard(title = "Cache, Data & Plugins", subtitle = badgeText) {
                    SettingsActionRow(
                        icon = Icons.Filled.Storage,
                        title = "Manage App Cache & Data",
                        subtitle = "Library thumbnails & albums, Video/PDF/Text plugin data, folder layouts & temp cache",
                        onClick = onOpenCacheManager,
                        testTag = "settings_open_cache_manager"
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ArcBadge(
                                text = badgeText,
                                containerColor = if (totalManagedCacheBytes > 0L) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                textColor = if (totalManagedCacheBytes > 0L) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Open Cache Manager",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroupCard(title = "About", subtitle = "Build ${BuildConfig.VERSION_CODE}") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(d.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Arc Files · v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Package: ${BuildConfig.APPLICATION_ID}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Multi-tab Top Island explorer with All Files Access, SAF mounts, archive compression/extraction, tags, and iOS Files-inspired clean minimalism.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

        }
    }
}
