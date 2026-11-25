package com.example.ui.screens.browser

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BreadcrumbNode
import com.example.data.ClipboardOp
import com.example.data.ClipboardState
import com.example.data.ExplorerCategory
import com.example.data.FileItem
import com.example.data.FormatUtils
import com.example.data.GroupByField
import com.example.data.SortField
import com.example.data.ViewMode
import com.example.plugins.archive.CompressedFormatFilter
import com.example.ui.components.common.ArcUnderlineTextField
import com.example.ui.registry.BinaryCategoryIcon
import com.example.ui.theme.ArcTheme

@Composable
fun BreadcrumbBar(
    breadcrumbs: List<BreadcrumbNode>,
    onBreadcrumbClick: (Int) -> Unit,
    dragState: BrowserDragDropState? = null
) {
    val d = ArcTheme.dimens
    val listState = rememberLazyListState()
    LaunchedEffect(breadcrumbs.size) {
        if (breadcrumbs.isNotEmpty()) {
            listState.animateScrollToItem(breadcrumbs.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .testTag("browser_breadcrumb_bar"),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(breadcrumbs) { idx, node ->
                val isLast = idx == breadcrumbs.lastIndex
                val targetId = "breadcrumb_${idx}_${node.pathOrUri}"
                val isDropHovered = !isLast && (dragState?.isDropHovered(targetId) == true)
                val chipBg by animateColorAsState(
                    targetValue = if (isDropHovered) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    label = "breadcrumb_drop_bg"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = node.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isLast || isDropHovered) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = when {
                            isDropHovered -> MaterialTheme.colorScheme.primary
                            isLast -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .browserDropTarget(
                                dragState = dragState,
                                targetId = targetId,
                                label = node.title,
                                pathOrUri = node.pathOrUri,
                                isSaf = node.isSaf,
                                enabled = !isLast
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .background(chipBg)
                            .then(
                                if (isDropHovered) {
                                    Modifier.border(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(8.dp)
                                    )
                                } else Modifier
                            )
                            .clickable { onBreadcrumbClick(idx) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("breadcrumb_node_$idx")
                    )
                    if (!isLast) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
        )
    }
}

@Composable
fun CategoryHeaderBar(
    category: ExplorerCategory,
    count: Int,
    monochrome: Boolean,
    totalBytes: Long = 0L,
    isScanning: Boolean = false,
    onRescanClick: (() -> Unit)? = null
) {
    val d = ArcTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = d.screenHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BinaryCategoryIcon(category = category, monochrome = monochrome, size = d.categoryIconSize)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = category.label,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onRescanClick != null) {
                val subtitle = when {
                    isScanning && count == 0 -> "Scanning all accessible storage…"
                    isScanning -> "Scanning… · $count found"
                    count == 0 -> "No compressed files indexed"
                    else -> "$count files · ${FormatUtils.formatBytes(totalBytes)}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onRescanClick != null) {
            Surface(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(d.pillRadius))
                    .clickable(enabled = !isScanning, onClick = onRescanClick)
                    .testTag("compressed_rescan_button"),
                shape = RoundedCornerShape(d.pillRadius),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(d.borderWidth, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Rescan compressed files",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = if (isScanning) "Scanning" else "Rescan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            Text(
                text = "$count items",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CompressedFormatFilterRow(
    allArchives: List<FileItem>,
    selectedFilterId: String,
    onSelectFilter: (String) -> Unit
) {
    val d = ArcTheme.dimens
    val countsByFilter = remember(allArchives) {
        CompressedFormatFilter.entries.associateWith { filter ->
            if (filter == CompressedFormatFilter.ALL) allArchives.size
            else allArchives.count { filter.matches(it) }
        }
    }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("compressed_filter_row"),
        contentPadding = PaddingValues(horizontal = d.screenHorizontalPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(CompressedFormatFilter.entries) { _, filter ->
            val count = countsByFilter[filter] ?: 0
            val selected = selectedFilterId == filter.id
            val label = if (count > 0 || filter == CompressedFormatFilter.ALL) {
                "${filter.label} ($count)"
            } else {
                filter.label
            }
            Surface(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(d.pillRadius))
                    .clickable { onSelectFilter(filter.id) }
                    .testTag("compressed_filter_${filter.id.lowercase()}"),
                shape = RoundedCornerShape(d.pillRadius),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                border = BorderStroke(
                    width = if (selected) 1.dp else d.borderWidth,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    }
                )
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
fun SearchHeaderBar(
    query: String,
    resultCount: Int,
    onQueryChange: (String) -> Unit
) {
    val d = ArcTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = d.screenHorizontalPadding, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ArcUnderlineTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.search_placeholder),
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            testTag = "search_input_field",
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = if (query.isBlank()) "Showing latest accessible files" else "$resultCount match(es)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun BrowserToolbar(
    isDirectory: Boolean,
    viewMode: ViewMode,
    sortField: SortField,
    sortAscending: Boolean,
    groupBy: GroupByField,
    onToggleViewMode: () -> Unit,
    onSortOrderChange: (SortField, Boolean) -> Unit,
    onToggleSortOrder: () -> Unit,
    onGroupByChange: (GroupByField) -> Unit,
    onNewFolderClick: () -> Unit,
    onNewNoteClick: () -> Unit
) {
    val d = ArcTheme.dimens
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var groupMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = d.screenHorizontalPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(d.pillRadius))
                        .clickable { sortMenuExpanded = true }
                        .testTag("sort_button"),
                    shape = RoundedCornerShape(d.pillRadius),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(d.borderWidth, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort by",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = sortField.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.testTag("sort_dropdown_menu")
                ) {
                    Text(
                        text = "Sort By",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                    SortField.entries.forEach { field ->
                        val selected = field == sortField
                        DropdownMenuItem(
                            text = { Text(field.label) },
                            trailingIcon = {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                sortMenuExpanded = false
                                onSortOrderChange(field, sortAscending)
                            },
                            modifier = Modifier.testTag("sort_field_${field.name.lowercase()}")
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = "Sort Order",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                    DropdownMenuItem(
                        text = { Text("Ascending") },
                        leadingIcon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (sortAscending) {
                                Icon(Icons.Filled.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = {
                            sortMenuExpanded = false
                            onSortOrderChange(sortField, true)
                        },
                        modifier = Modifier.testTag("sort_order_asc")
                    )
                    DropdownMenuItem(
                        text = { Text("Descending") },
                        leadingIcon = { Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (!sortAscending) {
                                Icon(Icons.Filled.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = {
                            sortMenuExpanded = false
                            onSortOrderChange(sortField, false)
                        },
                        modifier = Modifier.testTag("sort_order_desc")
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(d.pillRadius))
                    .clickable(onClick = onToggleSortOrder)
                    .testTag("sort_order_toggle"),
                shape = RoundedCornerShape(d.pillRadius),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(d.borderWidth, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = if (sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                        contentDescription = if (sortAscending) "Ascending order" else "Descending order",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (sortAscending) "Asc" else "Desc",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }

            Box {
                val groupActive = groupBy != GroupByField.NONE
                Surface(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(d.pillRadius))
                        .clickable { groupMenuExpanded = true }
                        .testTag("group_by_button"),
                    shape = RoundedCornerShape(d.pillRadius),
                    color = if (groupActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        d.borderWidth,
                        if (groupActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Category,
                            contentDescription = "Group by",
                            modifier = Modifier.size(14.dp),
                            tint = if (groupActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (groupActive) "Group: ${groupBy.label}" else "Group",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (groupActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }

                DropdownMenu(
                    expanded = groupMenuExpanded,
                    onDismissRequest = { groupMenuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.testTag("group_dropdown_menu")
                ) {
                    Text(
                        text = "Group By",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                    GroupByField.entries.forEach { option ->
                        val selected = option == groupBy
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            trailingIcon = {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                groupMenuExpanded = false
                                onGroupByChange(option)
                            },
                            modifier = Modifier.testTag("group_option_${option.name.lowercase()}")
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isDirectory) {
                IconButton(onClick = onNewFolderClick, modifier = Modifier.size(44.dp).minimumInteractiveComponentSize().testTag("toolbar_new_folder")) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.new_folder), modifier = Modifier.size(d.toolbarIconSize))
                }
                IconButton(onClick = onNewNoteClick, modifier = Modifier.size(44.dp).minimumInteractiveComponentSize().testTag("toolbar_new_note")) {
                    Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = stringResource(R.string.new_note), modifier = Modifier.size(d.toolbarIconSize))
                }
            }
            IconButton(onClick = onToggleViewMode, modifier = Modifier.size(44.dp).minimumInteractiveComponentSize().testTag("toolbar_view_mode")) {
                Icon(
                    imageVector = if (viewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = "Toggle Grid or List view",
                    modifier = Modifier.size(d.toolbarIconSize)
                )
            }
        }
    }
}

@Composable
fun ClipboardBanner(
    clipboard: ClipboardState,
    onPaste: () -> Unit,
    onClear: () -> Unit
) {
    val d = ArcTheme.dimens
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = d.screenHorizontalPadding, vertical = 4.dp),
        shape = RoundedCornerShape(d.cardRadius),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(d.borderWidth, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val opLabel = if (clipboard.operation == ClipboardOp.COPY) "Copy" else "Move"
            Text("$opLabel ${clipboard.items.size} item(s)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(d.smallRadius)).clickable(onClick = onPaste).testTag("clipboard_paste_button"),
                    shape = RoundedCornerShape(d.smallRadius),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "Paste here", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        Text("Paste", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel clipboard", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
fun ScopedStorageBanner(onGrantClick: () -> Unit) {
    val d = ArcTheme.dimens
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = d.screenHorizontalPadding, vertical = 4.dp)
            .testTag("scoped_storage_banner"),
        shape = RoundedCornerShape(d.cardRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(d.borderWidth, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.scoped_banner_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Surface(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(d.smallRadius))
                    .clickable(onClick = onGrantClick)
                    .testTag("scoped_grant_button"),
                shape = RoundedCornerShape(d.smallRadius),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = stringResource(R.string.grant_short),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
