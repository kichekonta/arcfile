package com.example.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BookmarkEntry
import com.example.data.CategorySummary
import com.example.data.DriveInfo
import com.example.data.ExplorerCategory
import com.example.data.FileItem
import com.example.data.FileTag
import com.example.ui.theme.ArcTheme

@Composable
fun HomeScreen(
    drives: List<DriveInfo>,
    categorySummaries: Map<ExplorerCategory, CategorySummary>,
    librarySummaries: Map<ExplorerCategory, CategorySummary> = categorySummaries,
    recentFiles: List<FileItem>,
    bookmarks: List<BookmarkEntry>,
    tags: List<FileTag> = emptyList(),
    taggedCountsByTagId: Map<String, Int> = emptyMap(),
    trashCount: Int,
    showNewFilesPreview: Boolean,
    showBookmarksPreview: Boolean,
    showFileExtensions: Boolean,
    monochromeBinaryIcons: Boolean,
    hasAllFilesAccess: Boolean,
    onRequestAllFilesAccess: () -> Unit,
    onDriveClick: (DriveInfo) -> Unit,
    onUnmountDrive: (DriveInfo) -> Unit,
    onMountFolderClick: () -> Unit,
    onCategoryClick: (ExplorerCategory) -> Unit,
    onTagClick: (String) -> Unit = {},
    onSearchBarClick: () -> Unit = {},
    onAnalyzerClick: () -> Unit,
    onTrashClick: () -> Unit,
    onImportMediaClick: () -> Unit,
    onImportDocsClick: () -> Unit,
    onNewNoteClick: () -> Unit,
    onOpenWorkspaceClick: () -> Unit,
    onToggleNewFilesPreview: () -> Unit,
    onOpenNewFilesFull: () -> Unit,
    onToggleBookmarksPreview: () -> Unit,
    onOpenBookmarksFull: () -> Unit,
    onBookmarkClick: (BookmarkEntry) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onFileClick: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val d = ArcTheme.dimens
    val totalIndexedCount = remember(categorySummaries) { categorySummaries.values.sumOf { it.itemCount } }
    val totalIndexedBytes = remember(categorySummaries) { categorySummaries.values.sumOf { it.totalBytes } }
    var showTagsSection by rememberSaveable { mutableStateOf(true) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = d.maxContentWidth)
                .fillMaxSize()
                .testTag("home_screen_list"),
            contentPadding = PaddingValues(
                start = d.screenHorizontalPadding,
                end = d.screenHorizontalPadding,
                top = 8.dp,
                bottom = navBottom + 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "home_browse_header", contentType = "browse_header") {
                IosBrowseHeader(
                    onSearchClick = onSearchBarClick
                )
            }

            item(key = "home_categories", contentType = "category_strip") {
                HomeTopCategoryStrip(
                    summaries = librarySummaries,
                    monochrome = monochromeBinaryIcons,
                    onCategoryClick = onCategoryClick
                )
            }

            if (!hasAllFilesAccess) {
                item(key = "all_files_banner", contentType = "access_banner") {
                    AllFilesAccessBanner(onGrantClick = onRequestAllFilesAccess)
                }
            }

            item(key = "home_storage_gauges", contentType = "storage_gauges") {
                IosSectionLabel(title = "Locations")
                Spacer(modifier = Modifier.padding(top = 6.dp))
                StorageGaugeSection(
                    drives = drives,
                    monochrome = monochromeBinaryIcons,
                    onDriveClick = onDriveClick,
                    onUnmountDrive = onUnmountDrive,
                    onMountFolderClick = onMountFolderClick
                )
            }

            item(key = "home_space_analyzer", contentType = "space_analyzer") {
                SpaceAnalyzerHomeCard(
                    indexedCount = totalIndexedCount,
                    totalIndexedBytes = totalIndexedBytes,
                    onClick = onAnalyzerClick
                )
            }

            item(key = "home_tools_grid", contentType = "tools_grid") {
                IosSectionLabel(title = "Quick Actions")
                Spacer(modifier = Modifier.padding(top = 6.dp))
                HomeToolsGridCard(
                    trashCount = trashCount,
                    compressedCount = categorySummaries[ExplorerCategory.ARCHIVES]?.itemCount ?: 0,
                    onCompressedClick = { onCategoryClick(ExplorerCategory.ARCHIVES) },
                    onNoteEditorClick = onNewNoteClick,
                    onRecycleBinClick = onTrashClick,
                    onMountFolderClick = onMountFolderClick,
                    onImportMediaClick = onImportMediaClick,
                    onImportDocsClick = onImportDocsClick,
                    onOpenOtherClick = { onCategoryClick(ExplorerCategory.OTHER) },
                    onOpenBookmarksClick = onOpenBookmarksFull
                )
            }

            if (tags.isNotEmpty()) {
                item(key = "home_tags", contentType = "expandable_card") {
                    val totalTagged = remember(taggedCountsByTagId) { taggedCountsByTagId.values.sum() }
                    HomeExpandableBarCard(
                        title = "Tags",
                        count = totalTagged,
                        isExpanded = showTagsSection,
                        onToggleExpand = { showTagsSection = !showTagsSection },
                        onOpenFull = {
                            tags.firstOrNull()?.let { onTagClick(it.id) }
                        },
                        cardTag = "home_tags_card",
                        eyeTag = "home_tags_eye"
                    ) {
                        tags.forEachIndexed { index, tag ->
                            IosHomeTagRow(
                                tag = tag,
                                itemCount = taggedCountsByTagId[tag.id] ?: 0,
                                onClick = { onTagClick(tag.id) }
                            )
                            if (index < tags.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 34.dp, end = 6.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "home_new_files", contentType = "expandable_card") {
                HomeExpandableBarCard(
                    title = stringResource(R.string.new_files),
                    count = recentFiles.size,
                    isExpanded = showNewFilesPreview,
                    onToggleExpand = onToggleNewFilesPreview,
                    onOpenFull = onOpenNewFilesFull,
                    cardTag = "home_new_files_card",
                    eyeTag = "home_new_files_eye"
                ) {
                    if (recentFiles.isEmpty()) {
                        Text(
                            text = "No recent files indexed yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(4.dp)
                        )
                    } else {
                        recentFiles.take(5).forEach { file ->
                            RecentFileRow(
                                item = file,
                                monochrome = monochromeBinaryIcons,
                                showExtensions = showFileExtensions,
                                onClick = { onFileClick(file) }
                            )
                        }
                    }
                }
            }

            item(key = "home_bookmarks", contentType = "expandable_card") {
                HomeExpandableBarCard(
                    title = stringResource(R.string.bookmarks_title),
                    count = bookmarks.size,
                    isExpanded = showBookmarksPreview,
                    onToggleExpand = onToggleBookmarksPreview,
                    onOpenFull = onOpenBookmarksFull,
                    cardTag = "home_bookmarks_card",
                    eyeTag = "home_bookmarks_eye"
                ) {
                    if (bookmarks.isEmpty()) {
                        Text(
                            text = "No bookmarks yet. Tap ⋮ on any folder or file to bookmark it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(4.dp)
                        )
                    } else {
                        bookmarks.take(5).forEach { entry ->
                            BookmarkPreviewRow(
                                entry = entry,
                                onClick = { onBookmarkClick(entry) },
                                onRemove = { onRemoveBookmark(entry.pathOrUri) }
                            )
                        }
                    }
                }
            }

        }
    }
}
