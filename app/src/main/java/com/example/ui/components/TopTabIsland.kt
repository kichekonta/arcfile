package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ExplorerTab
import com.example.data.GroupByField
import com.example.data.LibrarySection
import com.example.data.SortField
import com.example.data.TabDestination
import com.example.data.ViewMode
import com.example.ui.components.common.ArcModalCard
import com.example.ui.components.common.ConfigureEdgeToEdgeDialogWindow
import com.example.ui.theme.ArcTheme

/**
 * iOS Files translucent navigation chrome:
 * Left system-blue sidebar trigger, Finder/Safari-style segmented tab pills,
 * Search glyph, and circular iOS ellipsis overflow menu.
 */
@Composable
fun TopTabIsland(
    tabs: List<ExplorerTab>,
    activeTabId: String,
    canCreateInDirectory: Boolean = false,
    viewMode: ViewMode = ViewMode.LIST,
    gridColumns: Int = 3,
    originalMediaRatio: Boolean = true,
    sortField: SortField = SortField.NAME,
    sortAscending: Boolean = true,
    groupBy: GroupByField = GroupByField.NONE,
    onMenuClick: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTabClick: () -> Unit,
    onNewFolderClick: () -> Unit = {},
    onNewNoteClick: () -> Unit = {},
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onViewModeChange: (ViewMode) -> Unit = {},
    onGridColumnsChange: (Int) -> Unit,
    onToggleOriginalMediaRatio: () -> Unit,
    onSortOrderChange: (SortField, Boolean) -> Unit = { _, _ -> },
    onGroupByChange: (GroupByField) -> Unit = {},
    onRefreshClick: () -> Unit,
    onAnalyzerClick: () -> Unit,
    onTrashClick: () -> Unit,
    onHistoryClick: () -> Unit = onSearchClick,
    modifier: Modifier = Modifier
) {
    val navBg by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "ios_nav_bg"
    )
    val accentTint = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState()
    val homeTab = remember(tabs) {
        tabs.firstOrNull { it.id == "tab_home" || it.destination is TabDestination.Home }
    }
    val dynamicTabs = remember(tabs, homeTab) {
        if (homeTab != null) tabs.filter { it.id != homeTab.id } else tabs
    }
    val activeDynamicIdx = dynamicTabs.indexOfFirst { it.id == activeTabId }

    var moreExpanded by remember { mutableStateOf(false) }
    var newMenuExpanded by remember { mutableStateOf(false) }
    var viewDialogVisible by remember { mutableStateOf(false) }
    var windowsMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(activeDynamicIdx, dynamicTabs.size) {
        if (activeDynamicIdx >= 0 && dynamicTabs.isNotEmpty()) {
            listState.animateScrollToItem(activeDynamicIdx)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(navBg)
            .statusBarsPadding()
            .testTag("top_tab_island")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(44.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("island_menu_button")
            ) {
                StaggeredMenuIcon(tint = accentTint, modifier = Modifier.size(20.dp))
            }

            if (homeTab != null) {
                PinnedHomeTabChip(
                    tab = homeTab,
                    isActive = homeTab.id == activeTabId,
                    onSelect = { onSelectTab(homeTab.id) }
                )
                if (dynamicTabs.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            LazyRow(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .testTag("top_tab_island_dynamic_row"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(dynamicTabs, key = { it.id }) { tab ->
                    TabIslandChip(
                        tab = tab,
                        isActive = tab.id == activeTabId,
                        onSelect = { onSelectTab(tab.id) },
                        onClose = { onCloseTab(tab.id) }
                    )
                }
            }

            IconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .size(42.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("island_search_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search Files",
                    modifier = Modifier.size(20.dp),
                    tint = accentTint
                )
            }

            Box {
                IconButton(
                    onClick = { moreExpanded = true },
                    modifier = Modifier
                        .size(42.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("island_more_button")
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
                            contentDescription = "More actions",
                            modifier = Modifier.size(18.dp),
                            tint = accentTint
                        )
                    }
                }

                // Primary 6-item iOS popover menu
                DropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = { moreExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.widthIn(min = 204.dp).testTag("more_overflow_menu")
                ) {
                    OverflowMenuRow(
                        label = "New",
                        icon = Icons.Filled.Add,
                        testTag = "more_new_item",
                        onClick = {
                            moreExpanded = false
                            newMenuExpanded = true
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                    OverflowMenuRow(
                        label = "Refresh",
                        icon = Icons.Filled.Refresh,
                        testTag = "more_refresh_item",
                        onClick = {
                            moreExpanded = false
                            onRefreshClick()
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                    OverflowMenuRow(
                        label = "View Options",
                        icon = Icons.Filled.GridView,
                        testTag = "more_view_item",
                        onClick = {
                            moreExpanded = false
                            viewDialogVisible = true
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                    OverflowMenuRow(
                        label = "Tabs & Windows",
                        icon = Icons.Filled.FilterNone,
                        testTag = "more_windows_item",
                        onClick = {
                            moreExpanded = false
                            windowsMenuExpanded = true
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                    OverflowMenuRow(
                        label = "Space Analyzer",
                        icon = Icons.Filled.PieChart,
                        testTag = "more_analyzer_item",
                        onClick = {
                            moreExpanded = false
                            onAnalyzerClick()
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                    OverflowMenuRow(
                        label = "Recents & History",
                        icon = Icons.Filled.History,
                        testTag = "more_history_item",
                        onClick = {
                            moreExpanded = false
                            onHistoryClick()
                        }
                    )
                }

                // New submenu (New Folder / New Note / New Tab)
                DropdownMenu(
                    expanded = newMenuExpanded,
                    onDismissRequest = { newMenuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.widthIn(min = 192.dp).testTag("more_new_menu")
                ) {
                    if (canCreateInDirectory) {
                        DropdownMenuItem(
                            text = { Text("New Folder") },
                            leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { newMenuExpanded = false; onNewFolderClick() },
                            modifier = Modifier.testTag("more_new_folder")
                        )
                        DropdownMenuItem(
                            text = { Text("New Note") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { newMenuExpanded = false; onNewNoteClick() },
                            modifier = Modifier.testTag("more_new_note")
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("New Tab") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { newMenuExpanded = false; onNewTabClick() },
                        modifier = Modifier.testTag("more_new_tab")
                    )
                }

                // Windows (Open Tabs) submenu
                DropdownMenu(
                    expanded = windowsMenuExpanded,
                    onDismissRequest = { windowsMenuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.widthIn(min = 226.dp, max = 288.dp).testTag("tab_manager_dropdown")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tabs (${tabs.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    windowsMenuExpanded = false
                                    onNewTabClick()
                                }
                                .testTag("tab_manager_new_tab"),
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("New", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    tabs.forEach { tab ->
                        val isActive = tab.id == activeTabId
                        val subtitle = tabSubtitle(tab.destination)
                        DropdownMenuItem(
                            modifier = Modifier.testTag("tab_manager_item_${tab.id}"),
                            leadingIcon = {
                                Icon(
                                    imageVector = destinationIcon(tab.destination),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                                        ),
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (subtitle != null) {
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            trailingIcon = {
                                if (!tab.isPinned) {
                                    IconButton(
                                        onClick = { onCloseTab(tab.id) },
                                        modifier = Modifier.size(28.dp).testTag("tab_manager_close_${tab.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Close ${tab.title}",
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else if (isActive) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Active",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                windowsMenuExpanded = false
                                onSelectTab(tab.id)
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    }

    if (viewDialogVisible) {
        ViewOptionsDialog(
            viewMode = viewMode,
            gridColumns = gridColumns,
            originalMediaRatio = originalMediaRatio,
            sortField = sortField,
            sortAscending = sortAscending,
            groupBy = groupBy,
            onDismiss = { viewDialogVisible = false },
            onViewModeChange = { onViewModeChange(it) },
            onGridColumnsChange = { onGridColumnsChange(it) },
            onToggleOriginalMediaRatio = { onToggleOriginalMediaRatio() },
            onSortOrderChange = { field, asc -> onSortOrderChange(field, asc) },
            onGroupByChange = { onGroupByChange(it) }
        )
    }
}

@Composable
private fun OverflowMenuRow(
    label: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        trailingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        onClick = onClick,
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
private fun ViewOptionsDialog(
    viewMode: ViewMode,
    gridColumns: Int,
    originalMediaRatio: Boolean,
    sortField: SortField,
    sortAscending: Boolean,
    groupBy: GroupByField,
    onDismiss: () -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onToggleOriginalMediaRatio: () -> Unit,
    onSortOrderChange: (SortField, Boolean) -> Unit,
    onGroupByChange: (GroupByField) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ConfigureEdgeToEdgeDialogWindow()
        ArcModalCard(
            cornerRadius = 18.dp,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .testTag("view_options_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "View Options",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Text("View As", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ViewOptionChip(
                            label = "List",
                            icon = Icons.AutoMirrored.Filled.ViewList,
                            selected = viewMode == ViewMode.LIST,
                            testTag = "view_mode_list",
                            modifier = Modifier.weight(1f),
                            onClick = { onViewModeChange(ViewMode.LIST) }
                        )
                        ViewOptionChip(
                            label = "Icons",
                            icon = Icons.Filled.GridView,
                            selected = viewMode == ViewMode.GRID,
                            testTag = "view_mode_grid",
                            modifier = Modifier.weight(1f),
                            onClick = { onViewModeChange(ViewMode.GRID) }
                        )
                    }
                }

                Text("Icon Grid Size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(2, 3, 4).forEach { cols ->
                            ViewOptionChip(
                                label = "${cols}×",
                                selected = viewMode == ViewMode.GRID && gridColumns == cols,
                                testTag = "more_grid_size_$cols",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onGridColumnsChange(cols)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleOriginalMediaRatio() }
                        .testTag("more_original_ratio_toggle"),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.AspectRatio, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Original Media Ratio", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (originalMediaRatio) {
                            Icon(Icons.Filled.Check, contentDescription = "Enabled", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                Text("Sort By", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SortField.entries.forEach { field ->
                        ViewOptionChip(
                            label = field.label,
                            selected = sortField == field,
                            testTag = "sort_field_${field.name.lowercase()}",
                            modifier = Modifier.weight(1f),
                            onClick = { onSortOrderChange(field, sortAscending) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ViewOptionChip(
                        label = "Asc",
                        icon = Icons.Filled.ArrowUpward,
                        selected = sortAscending,
                        testTag = "sort_order_asc",
                        modifier = Modifier.weight(1f),
                        onClick = { onSortOrderChange(sortField, true) }
                    )
                    ViewOptionChip(
                        label = "Desc",
                        icon = Icons.Filled.ArrowDownward,
                        selected = !sortAscending,
                        testTag = "sort_order_desc",
                        modifier = Modifier.weight(1f),
                        onClick = { onSortOrderChange(sortField, false) }
                    )
                }

                Text("Group By", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GroupByField.entries.forEach { option ->
                        ViewOptionChip(
                            label = option.label,
                            selected = groupBy == option,
                            testTag = "group_option_${option.name.lowercase()}",
                            modifier = Modifier.weight(1f),
                            onClick = { onGroupByChange(option) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewOptionChip(
    label: String,
    selected: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StaggeredMenuIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val sw = 2.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(tint, Offset(w * 0.12f, h * 0.24f), Offset(w * 0.88f, h * 0.24f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.12f, h * 0.50f), Offset(w * 0.68f, h * 0.50f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.12f, h * 0.76f), Offset(w * 0.48f, h * 0.76f), sw, StrokeCap.Round)
    }
}

@Composable
private fun PinnedHomeTabChip(
    tab: ExplorerTab,
    isActive: Boolean,
    onSelect: () -> Unit
) {
    val activeBg = MaterialTheme.colorScheme.surface
    val bg by animateColorAsState(
        targetValue = if (isActive) activeBg else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        animationSpec = spring(),
        label = "pinnedHomeTabBg"
    )
    val iconTint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .size(width = 36.dp, height = 32.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .testTag("tab_chip_${tab.id}"),
        shape = RoundedCornerShape(10.dp),
        color = bg,
        shadowElevation = if (isActive) 1.dp else 0.dp,
        border = if (isActive) {
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        } else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Home,
                contentDescription = tab.title,
                modifier = Modifier.size(17.dp),
                tint = iconTint
            )
        }
    }
}

@Composable
private fun TabIslandChip(
    tab: ExplorerTab,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val activeBg = MaterialTheme.colorScheme.surface
    val bg by animateColorAsState(
        targetValue = if (isActive) activeBg else Color.Transparent,
        animationSpec = spring(),
        label = "tabIslandBg"
    )
    val iconTint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val icon = destinationIcon(tab.destination)

    Surface(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .testTag("tab_chip_${tab.id}"),
        shape = RoundedCornerShape(10.dp),
        color = bg,
        shadowElevation = if (isActive) 1.dp else 0.dp,
        border = if (isActive) BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)) else null
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (isActive) 10.dp else 7.dp,
                end = when {
                    !isActive -> 7.dp
                    tab.isPinned -> 10.dp
                    else -> 5.dp
                }
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = tab.title, modifier = Modifier.size(15.dp), tint = iconTint)

            if (isActive) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 104.dp)
                )
                if (!tab.isPinned) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable(onClick = onClose)
                            .testTag("close_tab_${tab.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close ${tab.title}",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun destinationIcon(destination: TabDestination): ImageVector = when (destination) {
    TabDestination.Home -> Icons.Filled.Home
    TabDestination.Settings -> Icons.Outlined.Tune
    TabDestination.Analyzer -> Icons.Filled.PieChart
    TabDestination.Trash -> Icons.Filled.DeleteOutline
    TabDestination.Bookmarks -> Icons.Filled.Bookmark
    is TabDestination.Library -> when (destination.section) {
        LibrarySection.IMAGES -> Icons.Filled.PhotoLibrary
        LibrarySection.MOVIES -> Icons.Filled.Movie
        LibrarySection.DOCUMENTS -> Icons.Filled.Description
        LibrarySection.MUSIC -> Icons.Filled.MusicNote
        LibrarySection.DOWNLOADS -> Icons.Filled.Download
    }
    is TabDestination.Tag -> Icons.AutoMirrored.Filled.Label
    is TabDestination.Search -> Icons.Filled.Search
    else -> Icons.Filled.Folder
}

private fun tabSubtitle(destination: TabDestination): String? = when (destination) {
    TabDestination.Home -> "Dashboard"
    is TabDestination.Directory -> destination.breadcrumbs.joinToString(" / ") { it.title }
    is TabDestination.Library -> when (destination.section) {
        LibrarySection.IMAGES -> "Library · Gallery & Albums"
        LibrarySection.MOVIES -> "Library · Movies & Albums"
        else -> "Library · ${destination.section.label}"
    }
    is TabDestination.Category -> "Category · ${destination.category.label}"
    is TabDestination.Tag -> "Tagged Items"
    is TabDestination.Search -> if (destination.query.isBlank()) "All indexed files" else "Query: ${destination.query}"
    TabDestination.Settings -> "Preferences & Storage"
    TabDestination.Analyzer -> "Storage breakdown"
    TabDestination.Trash -> "Recycle Bin"
    TabDestination.Bookmarks -> "Saved shortcuts"
}
