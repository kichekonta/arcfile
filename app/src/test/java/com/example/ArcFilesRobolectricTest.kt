package com.example

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.example.data.DirectoryLoadState
import com.example.data.ExplorerCategory
import com.example.data.FileExplorerRepository
import com.example.data.FileItem
import com.example.data.SortField
import com.example.data.TabDestination
import com.example.data.ViewMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArcFilesRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun repository_crud_and_categorization_work() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val root = repo.getWorkspaceRoot()

        val folderResult = repo.createFolder(root.absolutePath, isSaf = false, name = "AlphaDocs")
        assertTrue(folderResult.isSuccess)

        val dirState = repo.listDirectory(
            pathOrUri = root.absolutePath,
            isSaf = false,
            showHidden = false,
            sortField = SortField.NAME,
            ascending = true
        )
        assertTrue(dirState is DirectoryLoadState.Loaded)
        val items = (dirState as DirectoryLoadState.Loaded).items
        assertTrue(items.any { it.name == "AlphaDocs" && it.isDirectory })

        val docItem = FileItem(
            id = "1",
            name = "spec.md",
            pathOrUri = "/tmp/spec.md",
            isDirectory = false,
            sizeBytes = 128L,
            lastModifiedMillis = 1000L,
            mimeType = "text/markdown",
            extension = "md",
            isSafUri = false,
            parentPathOrUri = "/tmp"
        )
        val photoItem = docItem.copy(id = "2", name = "shot.png", pathOrUri = "/tmp/shot.png", mimeType = "image/png", extension = "png")
        val videoItem = docItem.copy(id = "3", name = "clip.mp4", pathOrUri = "/tmp/clip.mp4", mimeType = "video/mp4", extension = "mp4")
        val dlItem = docItem.copy(id = "4", name = "archive.zip", pathOrUri = "/storage/emulated/0/Download/archive.zip", mimeType = "application/zip", extension = "zip")
        val otherItem = docItem.copy(id = "5", name = "bundle.bin", pathOrUri = "/tmp/bundle.bin", mimeType = "application/octet-stream", extension = "bin")

        assertEquals(ExplorerCategory.DOCUMENTS, repo.categorizeFile(docItem))
        assertEquals(ExplorerCategory.PHOTOS, repo.categorizeFile(photoItem))
        assertEquals(ExplorerCategory.VIDEOS, repo.categorizeFile(videoItem))
        assertEquals(ExplorerCategory.DOWNLOADS, repo.categorizeFile(dlItem))
        assertEquals(ExplorerCategory.OTHER, repo.categorizeFile(otherItem))
    }

    @Test
    fun home_and_top_tab_island_render_all_categories() {
        composeTestRule.setContent {
            ArcFilesRoot()
        }

        val homeList = composeTestRule.onNodeWithTag("home_screen_list")
        composeTestRule.onNodeWithTag("top_tab_island").assertIsDisplayed()
        composeTestRule.onNodeWithTag("island_menu_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_tile_photos").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_tile_videos").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_tile_documents").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_tile_music").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_tile_downloads").assertIsDisplayed()

        homeList.performScrollToNode(hasTestTag("home_space_analyzer_card"))
        composeTestRule.onNodeWithTag("home_space_analyzer_card").assertIsDisplayed()

        homeList.performScrollToNode(hasTestTag("home_tools_card"))
        composeTestRule.onNodeWithTag("home_tools_card").assertIsDisplayed()

        homeList.performScrollToNode(hasTestTag("home_new_files_card"))
        composeTestRule.onNodeWithTag("home_new_files_card").assertIsDisplayed()

        homeList.performScrollToNode(hasTestTag("home_bookmarks_card"))
        composeTestRule.onNodeWithTag("home_bookmarks_card").assertIsDisplayed()
    }

    @Test
    fun registries_cover_all_categories_drives_and_file_actions() {
        assertEquals(
            com.example.data.ExplorerCategory.entries.size,
            com.example.ui.registry.BinaryIconRegistry.categorySpecs.size
        )
        assertEquals(
            com.example.data.DriveType.entries.size,
            com.example.ui.registry.BinaryIconRegistry.driveSpecs.size
        )
        val folderOnly = listOf(
            FileItem("d1", "Docs", "/tmp/Docs", true, 0L, 0L, "vnd.android.document/directory", "", false, "/tmp")
        )
        val folderActions = com.example.ui.registry.FileActionRegistry.visibleFor(folderOnly).map { it.id }
        assertTrue(com.example.ui.registry.FileActionId.RENAME in folderActions)
        assertTrue(com.example.ui.registry.FileActionId.SHARE !in folderActions)
    }

    @Test
    fun nested_folder_files_and_saf_subfolders_are_fetched() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val root = repo.getWorkspaceRoot()

        val subFolder = java.io.File(root, "NestedFolder").apply { mkdirs() }
        val innerFile = java.io.File(subFolder, "notes.txt").apply { writeText("hello inside folder") }
        assertTrue(innerFile.exists())

        val nestedState = repo.listDirectory(
            pathOrUri = subFolder.absolutePath,
            isSaf = false,
            showHidden = false,
            sortField = SortField.NAME,
            ascending = true
        )
        assertTrue(nestedState is DirectoryLoadState.Loaded)
        val nestedItems = (nestedState as DirectoryLoadState.Loaded).items
        assertTrue(nestedItems.any { it.name == "notes.txt" && !it.isDirectory })

        val authority = "com.test.saf.provider"
        val fakeProvider = object : android.content.ContentProvider() {
            override fun onCreate(): Boolean = true
            override fun query(
                uri: android.net.Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?
            ): android.database.Cursor {
                val cursor = android.database.MatrixCursor(
                    arrayOf(
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                    )
                )
                val uriStr = uri.toString()
                if (uriStr.contains("primary%3ARoot%2FSubFolder")) {
                    cursor.addRow(arrayOf("primary:Root/SubFolder/inside.pdf", "inside.pdf", "application/pdf"))
                } else {
                    cursor.addRow(arrayOf("primary:Root/SubFolder", "SubFolder", android.provider.DocumentsContract.Document.MIME_TYPE_DIR))
                }
                return cursor
            }
            override fun getType(uri: android.net.Uri): String? = null
            override fun insert(uri: android.net.Uri, values: android.content.ContentValues?): android.net.Uri? = null
            override fun delete(uri: android.net.Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(uri: android.net.Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        }
        org.robolectric.Robolectric.buildContentProvider(fakeProvider::class.java).create(authority)

        val treeUri = android.provider.DocumentsContract.buildTreeDocumentUri(authority, "primary:Root")
        val rootSafState = repo.listDirectory(treeUri.toString(), isSaf = true, showHidden = false, sortField = SortField.NAME, ascending = true)
        assertTrue(rootSafState is DirectoryLoadState.Loaded)
        val folderItem = (rootSafState as DirectoryLoadState.Loaded).items.first { it.name == "SubFolder" }
        assertTrue(folderItem.isDirectory)

        val subSafState = repo.listDirectory(folderItem.pathOrUri, isSaf = true, showHidden = false, sortField = SortField.NAME, ascending = true)
        assertTrue(subSafState is DirectoryLoadState.Loaded)
        val innerSafItems = (subSafState as DirectoryLoadState.Loaded).items
        assertTrue(innerSafItems.any { it.name == "inside.pdf" && !it.isDirectory })
    }

    @Test
    fun folder_emblem_registry_resolves_special_folders() {
        assertEquals(com.example.ui.registry.FolderEmblem.DOWNLOADS, com.example.ui.registry.resolveFolderEmblem("Download"))
        assertEquals(com.example.ui.registry.FolderEmblem.DOWNLOADS, com.example.ui.registry.resolveFolderEmblem("Downloads"))
        assertEquals(com.example.ui.registry.FolderEmblem.PICTURES, com.example.ui.registry.resolveFolderEmblem("DCIM"))
        assertEquals(com.example.ui.registry.FolderEmblem.PICTURES, com.example.ui.registry.resolveFolderEmblem("Pictures"))
        assertEquals(com.example.ui.registry.FolderEmblem.VIDEOS, com.example.ui.registry.resolveFolderEmblem("Movies"))
        assertEquals(com.example.ui.registry.FolderEmblem.MUSIC, com.example.ui.registry.resolveFolderEmblem("Music"))
        assertEquals(com.example.ui.registry.FolderEmblem.DOCUMENTS, com.example.ui.registry.resolveFolderEmblem("Documents"))
        assertEquals(com.example.ui.registry.FolderEmblem.APPS_SYSTEM, com.example.ui.registry.resolveFolderEmblem("Android"))
        assertEquals(com.example.ui.registry.FolderEmblem.ARCHIVE, com.example.ui.registry.resolveFolderEmblem("Backups"))
        assertEquals(com.example.ui.registry.FolderEmblem.NONE, com.example.ui.registry.resolveFolderEmblem("MyFolder"))
    }

    @Test
    fun all_files_access_intent_and_banner_work() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val intent = repo.buildAllFilesAccessIntent()
        assertEquals(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, intent.action)
        assertTrue(intent.data.toString().contains(context.packageName))

        composeTestRule.setContent {
            ArcFilesRoot()
        }
        composeTestRule.onNodeWithTag("home_all_files_banner").assertIsDisplayed()
    }

    @Test
    fun dedicated_settings_page_opens_with_all_sections() {
        composeTestRule.setContent {
            ArcFilesRoot()
        }
        composeTestRule.onNodeWithTag("island_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("arc_sidebar_list").performScrollToNode(hasTestTag("drawer_settings_item"))
        composeTestRule.onNodeWithTag("drawer_settings_item").performClick()
        composeTestRule.waitForIdle()

        val listNode = composeTestRule.onNodeWithTag("settings_screen_list")
        listNode.assertIsDisplayed()
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        listNode.performScrollToNode(hasTestTag("toggle_file_extensions"))
        composeTestRule.onNodeWithTag("toggle_file_extensions").performScrollTo().assertIsDisplayed().performClick()

        listNode.performScrollToNode(hasText("Browsing & Sorting"))
        composeTestRule.onNodeWithText("Browsing & Sorting").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_folders_first").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_confirm_delete").performScrollTo().assertIsDisplayed()

        listNode.performScrollToNode(hasText("Storage & Permissions"))
        composeTestRule.onNodeWithText("Storage & Permissions").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_all_files_row").performScrollTo().assertIsDisplayed()

        listNode.performScrollToNode(hasText("About"))
        composeTestRule.onNodeWithText("About").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sidebar_drawer_opens_with_sections_and_version() {
        composeTestRule.setContent {
            ArcFilesRoot()
        }
        composeTestRule.onNodeWithTag("island_menu_button").performClick()
        composeTestRule.waitForIdle()

        val drawerList = composeTestRule.onNodeWithTag("arc_sidebar_list")
        composeTestRule.onNodeWithTag("arc_sidebar_drawer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Arc Files").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_group_favorites").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_group_local").assertIsDisplayed()
        drawerList.performScrollToNode(hasTestTag("drawer_hidden_files_toggle"))
        composeTestRule.onNodeWithTag("drawer_hidden_files_toggle").assertIsDisplayed()
        drawerList.performScrollToNode(hasTestTag("drawer_all_files_button"))
        composeTestRule.onNodeWithTag("drawer_all_files_button").assertIsDisplayed()
        drawerList.performScrollToNode(hasTestTag("drawer_settings_item"))
        composeTestRule.onNodeWithTag("drawer_settings_item").assertIsDisplayed()
    }

    @Test
    fun recycle_bin_and_zip_extraction_work() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val root = repo.getWorkspaceRoot()

        val tempFile = java.io.File(root, "trash_sample.txt").apply { writeText("recoverable") }
        val fileItem = FileItem(
            id = tempFile.absolutePath,
            name = tempFile.name,
            pathOrUri = tempFile.absolutePath,
            isDirectory = false,
            sizeBytes = tempFile.length(),
            lastModifiedMillis = tempFile.lastModified(),
            mimeType = "text/plain",
            extension = "txt",
            isSafUri = false,
            parentPathOrUri = root.absolutePath
        )

        val trashRes = repo.moveItemsToTrash(listOf(fileItem))
        assertTrue(trashRes.isSuccess)
        assertTrue(!tempFile.exists())

        val trashed = repo.listTrashEntries()
        assertTrue(trashed.any { it.originalName == "trash_sample.txt" })

        val restoreRes = repo.restoreTrashEntry(trashed.first { it.originalName == "trash_sample.txt" })
        assertTrue(restoreRes.isSuccess)
        assertTrue(tempFile.exists())

        // Create and extract a real ZIP archive
        val zipFile = java.io.File(root, "bundle.zip")
        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("hello.txt"))
            zos.write("zip-content".toByteArray())
            zos.closeEntry()
        }
        val zipItem = fileItem.copy(
            id = zipFile.absolutePath,
            name = "bundle.zip",
            pathOrUri = zipFile.absolutePath,
            mimeType = "application/zip",
            extension = "zip"
        )
        val entries = repo.listZipEntries(zipItem).getOrThrow()
        assertTrue(entries.any { it.name == "hello.txt" })

        val extractCount = repo.extractZipToDirectory(zipItem, root).getOrThrow()
        assertEquals(1, extractCount)
        assertTrue(java.io.File(root, "bundle/hello.txt").exists())
    }

    @Test
    fun overflow_menu_grid_size_and_original_ratio_work() {
        composeTestRule.setContent {
            ArcFilesRoot()
        }

        // Top bar keeps only clean tabs, Search, and Overflow menu (no extra + or [N] buttons)
        assertEquals(0, composeTestRule.onAllNodesWithTag("island_new_tab_button").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("island_tab_manager_button").fetchSemanticsNodes().size)

        // Overflow menu matches clean 6-item layout
        composeTestRule.onNodeWithTag("island_more_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_new_item").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_refresh_item").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_view_item").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_windows_item").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_analyzer_item").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_history_item").assertIsDisplayed()

        // Open View options dialog from overflow menu
        composeTestRule.onNodeWithTag("more_view_item").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_grid_size_2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_grid_size_3").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_grid_size_4").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_original_ratio_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_grid_size_4").performClick()
        composeTestRule.waitForIdle()

        // Open Settings via Drawer and verify Original Ratio toggle
        composeTestRule.onNodeWithTag("island_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("arc_sidebar_list").performScrollToNode(hasTestTag("drawer_settings_item"))
        composeTestRule.onNodeWithTag("drawer_settings_item").performClick()
        composeTestRule.waitForIdle()

        val listNode = composeTestRule.onNodeWithTag("settings_screen_list")
        listNode.performScrollToNode(hasTestTag("toggle_original_ratio"))
        composeTestRule.onNodeWithTag("toggle_original_ratio").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun media_open_file_launches_in_app_original_ratio_viewer() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.ExplorerViewModel(app)
        var externalCalled = false

        val imgItem = FileItem(
            id = "img_1",
            name = "photo.png",
            pathOrUri = "/tmp/photo.png",
            isDirectory = false,
            sizeBytes = 512L,
            lastModifiedMillis = 1000L,
            mimeType = "image/png",
            extension = "png",
            isSafUri = false,
            parentPathOrUri = "/tmp"
        )
        val vidItem = imgItem.copy(
            id = "vid_1",
            name = "clip.mp4",
            pathOrUri = "/tmp/clip.mp4",
            mimeType = "video/mp4",
            extension = "mp4"
        )

        vm.openFile(imgItem) { externalCalled = true }
        assertEquals("img_1", vm.uiState.value.previewFile?.id)
        assertTrue(!externalCalled)
        vm.closePreview()

        vm.openFile(vidItem) { externalCalled = true }
        assertEquals("vid_1", vm.uiState.value.previewFile?.id)
        assertTrue(!externalCalled)
    }

    @Test
    fun responsive_grid_metrics_scale_border_radius_and_gaps() {
        val dimens = com.example.ui.theme.ArcDimens()
        assertEquals(1f, dimens.gridIconAspectRatio)

        val m2 = dimens.gridMetricsFor(2, 360.dp)
        val m3 = dimens.gridMetricsFor(3, 360.dp)
        val m4 = dimens.gridMetricsFor(4, 360.dp)

        assertTrue(m2.mediaCornerRadius > m3.mediaCornerRadius)
        assertTrue(m3.mediaCornerRadius > m4.mediaCornerRadius)
        assertTrue(m2.gridGap >= m3.gridGap && m3.gridGap >= m4.gridGap)
        assertTrue(m4.cardCornerRadius > m4.mediaCornerRadius)
        assertTrue(m4.compactText)
        assertTrue(!m2.compactText)
    }

    @Test
    fun square_grid_cards_render_cleanly_in_drive_tab() {
        composeTestRule.setContent {
            ArcFilesRoot()
        }

        // Wait for async drive load, open Internal Storage drive card, and switch to 4-column grid via View menu
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithTag("drive_card_drive_internal").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_screen_list").performScrollToNode(hasTestTag("drive_card_drive_internal"))
        composeTestRule.onNodeWithTag("drive_card_drive_internal").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("island_more_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_view_item").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_grid_size_4").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("browser_breadcrumb_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("breadcrumb_node_0").assertIsDisplayed()
        // Extra toolbar row is omitted for minimal canvas
        assertEquals(0, composeTestRule.onAllNodesWithTag("toolbar_view_mode").fetchSemanticsNodes().size)
    }

    @Test
    fun tab_manager_dropdown_lists_and_switches_open_tabs() {
        composeTestRule.setContent {
            ArcFilesRoot()
        }

        // Wait for async drive load, open Internal Storage to create a second tab, then return Home via Overflow -> Windows
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithTag("drive_card_drive_internal").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_screen_list").performScrollToNode(hasTestTag("drive_card_drive_internal"))
        composeTestRule.onNodeWithTag("drive_card_drive_internal").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("island_more_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_windows_item").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tab_manager_dropdown").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_manager_item_tab_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_manager_new_tab").assertIsDisplayed()

        composeTestRule.onNodeWithTag("tab_manager_item_tab_home").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()
    }

    @Test
    fun sort_order_and_group_by_type_work_correctly() {
        val items = listOf(
            FileItem("f1", "Work", "/tmp/Work", true, 0L, 2000L, "vnd.android.document/directory", "", false, "/tmp"),
            FileItem("i1", "beach.png", "/tmp/beach.png", false, 400L, 1000L, "image/png", "png", false, "/tmp"),
            FileItem("v1", "clip.mp4", "/tmp/clip.mp4", false, 800L, 3000L, "video/mp4", "mp4", false, "/tmp"),
            FileItem("d1", "notes.txt", "/tmp/notes.txt", false, 120L, 1500L, "text/plain", "txt", false, "/tmp"),
            FileItem("a1", "bundle.zip", "/tmp/bundle.zip", false, 950L, 500L, "application/zip", "zip", false, "/tmp")
        )

        val asc = com.example.data.FileGroupingUtils.sortItems(items, SortField.SIZE, ascending = true, foldersFirst = true)
        assertEquals(listOf("Work", "notes.txt", "beach.png", "clip.mp4", "bundle.zip"), asc.map { it.name })

        val desc = com.example.data.FileGroupingUtils.sortItems(items, SortField.SIZE, ascending = false, foldersFirst = true)
        assertEquals(listOf("Work", "bundle.zip", "clip.mp4", "beach.png", "notes.txt"), desc.map { it.name })

        val grouped = com.example.data.FileGroupingUtils.groupItems(
            items = items,
            groupBy = com.example.data.GroupByField.TYPE,
            sortField = SortField.NAME,
            ascending = true,
            foldersFirst = true
        )
        assertEquals(listOf("Folders", "Images", "Videos", "Documents", "Compressed"), grouped.map { it.title })
    }

    @Test
    fun room_folder_layout_persists_per_folder_and_cleans_up_on_rename_or_delete() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.db.ArcDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val layoutRepo = com.example.data.db.FolderLayoutRepository(db.folderLayoutDao())
            val folderA = "/storage/emulated/0/Projects/Alpha"
            val folderASub = "/storage/emulated/0/Projects/Alpha/Assets"
            val folderB = "/storage/emulated/0/Projects/Beta"

            layoutRepo.saveLayout(
                folderA,
                com.example.data.EffectiveFolderLayout(
                    viewMode = com.example.data.ViewMode.GRID,
                    gridColumns = 4,
                    originalMediaRatio = false,
                    sortField = SortField.SIZE,
                    sortAscending = false,
                    groupBy = com.example.data.GroupByField.TYPE
                )
            )
            layoutRepo.saveLayout(
                folderASub,
                com.example.data.EffectiveFolderLayout(
                    viewMode = com.example.data.ViewMode.LIST,
                    gridColumns = 2,
                    sortField = SortField.DATE,
                    sortAscending = true
                )
            )
            layoutRepo.saveLayout(
                folderB,
                com.example.data.EffectiveFolderLayout(
                    viewMode = com.example.data.ViewMode.LIST,
                    gridColumns = 3,
                    sortField = SortField.NAME,
                    sortAscending = true,
                    groupBy = com.example.data.GroupByField.NONE
                )
            )

            assertEquals(4, layoutRepo.getLayout(folderA)?.gridColumns)
            assertEquals("TYPE", layoutRepo.getLayout(folderA)?.groupBy)
            assertEquals(false, layoutRepo.getLayout(folderA)?.sortAscending)

            // Removing Folder A must delete both Folder A and its nested subfolder Assets, leaving Folder B intact
            val removed = layoutRepo.removeFolderState(folderA)
            assertEquals(2, removed)
            assertEquals(null, layoutRepo.getLayout(folderA))
            assertEquals(null, layoutRepo.getLayout(folderASub))
            assertEquals("LIST", layoutRepo.getLayout(folderB)?.viewMode)

            // Verify SAF descendant URI cleanup too
            val safParent = "content://com.android.externalstorage.documents/tree/primary%3ADocs/document/primary%3ADocs%2FOld"
            val safChild = "content://com.android.externalstorage.documents/tree/primary%3ADocs/document/primary%3ADocs%2FOld%2FSub"
            layoutRepo.saveLayout(safParent, com.example.data.EffectiveFolderLayout(gridColumns = 4))
            layoutRepo.saveLayout(safChild, com.example.data.EffectiveFolderLayout(gridColumns = 2))
            assertEquals(2, layoutRepo.removeFolderState(safParent))
            assertEquals(null, layoutRepo.getLayout(safParent))
            assertEquals(null, layoutRepo.getLayout(safChild))
        } finally {
            db.close()
        }
    }

    @Test
    fun toolbar_sort_order_and_group_by_controls_render_and_update() {
        composeTestRule.setContent {
            ArcFilesRoot()
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithTag("drive_card_drive_internal").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_screen_list").performScrollToNode(hasTestTag("drive_card_drive_internal"))
        composeTestRule.onNodeWithTag("drive_card_drive_internal").performClick()
        composeTestRule.waitForIdle()

        // Open View & Sort dialog from overflow menu
        composeTestRule.onNodeWithTag("island_more_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_view_item").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("view_options_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sort_field_size").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("sort_order_asc").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("sort_order_desc").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("group_option_none").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("group_option_type").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun viewModel_per_folder_layout_and_rename_delete_cleanup() = runTest {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = FileExplorerRepository(app)
        val root = repo.getWorkspaceRoot()
        val folderA = java.io.File(root, "PerFolderA_${System.nanoTime()}").apply { mkdirs() }
        val folderB = java.io.File(root, "PerFolderB_${System.nanoTime()}").apply { mkdirs() }

        val vm = com.example.ui.ExplorerViewModel(app)
        val folderAItem = FileItem(
            id = folderA.absolutePath,
            name = folderA.name,
            pathOrUri = folderA.absolutePath,
            isDirectory = true,
            sizeBytes = 0L,
            lastModifiedMillis = folderA.lastModified(),
            mimeType = "vnd.android.document/directory",
            extension = "",
            isSafUri = false,
            parentPathOrUri = root.absolutePath
        )
        val folderBItem = folderAItem.copy(
            id = folderB.absolutePath,
            name = folderB.name,
            pathOrUri = folderB.absolutePath
        )

        // Open test root directory tab and navigate into Folder A
        vm.openDriveInTab(
            com.example.data.DriveInfo(
                id = "test_local_root",
                name = "Test Root",
                subtitle = root.absolutePath,
                pathOrUri = root.absolutePath,
                type = com.example.data.DriveType.INTERNAL,
                totalBytes = 0L,
                freeBytes = 0L,
                isSaf = false
            )
        )
        vm.navigateIntoFolder(folderAItem)
        vm.setViewMode(com.example.data.ViewMode.GRID)
        vm.setGridColumns(4)
        vm.setSortOrder(SortField.SIZE, ascending = false)
        vm.setGroupBy(com.example.data.GroupByField.TYPE)

        val dao = com.example.data.db.ArcDatabase.getInstance(app).folderLayoutDao()
        var waitCycles = 0
        while (dao.getByKey(folderA.absolutePath)?.groupBy != "TYPE" && waitCycles < 40) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(50)
            waitCycles++
        }
        assertEquals(4, dao.getByKey(folderA.absolutePath)?.gridColumns)
        assertEquals("TYPE", dao.getByKey(folderA.absolutePath)?.groupBy)

        // Step back to root and open Folder B with distinct layout
        vm.navigateToBreadcrumb(0)
        vm.navigateIntoFolder(folderBItem)
        vm.setViewMode(com.example.data.ViewMode.LIST)
        vm.setSortOrder(SortField.DATE, ascending = true)
        waitCycles = 0
        while (dao.getByKey(folderB.absolutePath)?.sortField != "DATE" && waitCycles < 40) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(50)
            waitCycles++
        }
        assertEquals("LIST", dao.getByKey(folderB.absolutePath)?.viewMode)

        // Navigating back into Folder A restores Folder A's saved Room layout in real time
        vm.navigateToBreadcrumb(0)
        vm.navigateIntoFolder(folderAItem)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(4, vm.uiState.value.activeLayout.gridColumns)
        assertEquals(com.example.data.ViewMode.GRID, vm.uiState.value.activeLayout.viewMode)
        assertEquals(false, vm.uiState.value.activeLayout.sortAscending)
        assertEquals(com.example.data.GroupByField.TYPE, vm.uiState.value.activeLayout.groupBy)

        // Renaming Folder A must remove its Room DB state entry automatically
        vm.navigateToBreadcrumb(0)
        val renamedName = "${folderA.name}_Renamed"
        vm.renameFile(folderAItem, renamedName)
        waitCycles = 0
        while (dao.getByKey(folderA.absolutePath) != null && waitCycles < 40) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(50)
            waitCycles++
        }
        assertEquals(null, dao.getByKey(folderA.absolutePath))

        // Deleting Folder B must remove its Room DB state entry automatically
        vm.deleteFiles(listOf(folderBItem))
        waitCycles = 0
        while (dao.getByKey(folderB.absolutePath) != null && waitCycles < 40) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            kotlinx.coroutines.delay(50)
            waitCycles++
        }
        assertEquals(null, dao.getByKey(folderB.absolutePath))

        // Cleanup test folder
        java.io.File(root, renamedName).deleteRecursively()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun batch_rename_engine_supports_numbers_padding_prefix_postfix_and_replace() {
        val sampleItems = listOf(
            FileItem("1", "photoA.jpg", "/tmp/photoA.jpg", false, 100L, 100L, "image/jpeg", "jpg", false, "/tmp"),
            FileItem("2", "photoB.png", "/tmp/photoB.png", false, 100L, 100L, "image/png", "png", false, "/tmp"),
            FileItem("3", "FolderX", "/tmp/FolderX", true, 0L, 100L, "vnd.android.document/directory", "", false, "/tmp")
        )

        // 1. Numbers Only with start index 0 & padding 1 -> 0, 1, 2
        val numStart0Pad1 = com.example.data.BatchRenameUtils.buildPreview(
            sampleItems,
            com.example.data.BatchRenameConfig(
                mode = com.example.data.BatchRenameMode.NUMBER_ONLY,
                startIndex = 0,
                paddingDigits = 1
            )
        )
        assertEquals(listOf("0.jpg", "1.png", "2"), numStart0Pad1.map { it.newName })
        assertTrue(com.example.data.BatchRenameUtils.canApply(numStart0Pad1))

        // 2. Numbers Only with start index 2 & padding 3 -> 002, 003, 004
        val numStart2Pad3 = com.example.data.BatchRenameUtils.buildPreview(
            sampleItems,
            com.example.data.BatchRenameConfig(
                mode = com.example.data.BatchRenameMode.NUMBER_ONLY,
                startIndex = 2,
                paddingDigits = 3
            )
        )
        assertEquals(listOf("002.jpg", "003.png", "004"), numStart2Pad3.map { it.newName })

        // 3. Original name + Prefix & Postfix (without numbering)
        val affix = com.example.data.BatchRenameUtils.buildPreview(
            sampleItems,
            com.example.data.BatchRenameConfig(
                mode = com.example.data.BatchRenameMode.PREFIX_POSTFIX,
                prefix = "ARC_",
                postfix = "_v2",
                includeNumbering = false
            )
        )
        assertEquals(listOf("ARC_photoA_v2.jpg", "ARC_photoB_v2.png", "ARC_FolderX_v2"), affix.map { it.newName })

        // 4. Remove original & add New Name + start index 1, padding 2
        val replace = com.example.data.BatchRenameUtils.buildPreview(
            sampleItems,
            com.example.data.BatchRenameConfig(
                mode = com.example.data.BatchRenameMode.REPLACE_NAME,
                newBaseName = "Trip",
                includeNumbering = true,
                startIndex = 1,
                paddingDigits = 2,
                separator = "_"
            )
        )
        assertEquals(listOf("Trip_01.jpg", "Trip_02.png", "Trip_03"), replace.map { it.newName })

        // Multi-select exposes BATCH_RENAME in FileActionRegistry
        val multiActions = com.example.ui.registry.FileActionRegistry.visibleFor(sampleItems).map { it.id }
        assertTrue(com.example.ui.registry.FileActionId.BATCH_RENAME in multiActions)
    }

    @Test
    fun repository_batch_rename_handles_overlapping_numeric_shift() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val workDir = java.io.File(repo.getWorkspaceRoot(), "BatchTest_${System.nanoTime()}").apply { mkdirs() }
        try {
            val f1 = java.io.File(workDir, "1.txt").apply { writeText("first") }
            val f2 = java.io.File(workDir, "2.txt").apply { writeText("second") }
            val f3 = java.io.File(workDir, "3.txt").apply { writeText("third") }
            val items = listOf(f1, f2, f3).map { f ->
                FileItem(f.absolutePath, f.name, f.absolutePath, false, f.length(), f.lastModified(), "text/plain", "txt", false, workDir.absolutePath)
            }

            // Shifting 1,2,3 -> 0,1,2 collides without 2-phase temp staging
            val renames = listOf(items[0] to "0.txt", items[1] to "1.txt", items[2] to "2.txt")
            val result = repo.batchRenameItems(renames)
            assertTrue(result.isSuccess)
            assertEquals(3, result.getOrNull())
            assertEquals("first", java.io.File(workDir, "0.txt").readText())
            assertEquals("second", java.io.File(workDir, "1.txt").readText())
            assertEquals("third", java.io.File(workDir, "2.txt").readText())
            assertTrue(!f3.exists())
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun batch_rename_dialog_ui_previews_numbers_padding_and_affixes() {
        val items = listOf(
            FileItem("a", "alpha.jpg", "/tmp/alpha.jpg", false, 100L, 100L, "image/jpeg", "jpg", false, "/tmp"),
            FileItem("b", "beta.jpg", "/tmp/beta.jpg", false, 100L, 100L, "image/jpeg", "jpg", false, "/tmp")
        )
        var appliedRenames: List<Pair<FileItem, String>>? = null

        composeTestRule.setContent {
            com.example.ui.theme.ArcFilesTheme {
                com.example.ui.components.dialogs.BatchRenameDialog(
                    items = items,
                    onDismiss = {},
                    onApply = { appliedRenames = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("batch_rename_dialog").assertIsDisplayed()

        // 1. Original + Prefix & Postfix preview
        composeTestRule.onNodeWithTag("batch_prefix_input").performScrollTo().performTextInput("PRE_")
        composeTestRule.onNodeWithTag("batch_postfix_input").performScrollTo().performTextInput("_END")
        composeTestRule.onNodeWithTag("batch_numbering_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("PRE_alpha_END.jpg").assertIsDisplayed()
        composeTestRule.onNodeWithText("PRE_beta_END.jpg").assertIsDisplayed()

        // 2. Replace original with New Name + sequence
        composeTestRule.onNodeWithTag("batch_mode_replace_name").performClick()
        composeTestRule.onNodeWithTag("batch_new_name_input").performScrollTo().performTextInput("Shot")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Shot_01.jpg").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shot_02.jpg").assertIsDisplayed()

        // 3. Numbers Only, start index 0, padding 3 -> 000.jpg, 001.jpg
        composeTestRule.onNodeWithTag("batch_mode_number_only").performClick()
        composeTestRule.onNodeWithTag("batch_start_0").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("batch_pad_3").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("000.jpg").assertIsDisplayed()
        composeTestRule.onNodeWithText("001.jpg").assertIsDisplayed()

        // 4. Change start index to 2 & padding 1 -> 2.jpg, 3.jpg
        composeTestRule.onNodeWithTag("batch_start_2").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("batch_pad_1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2.jpg").assertIsDisplayed()
        composeTestRule.onNodeWithText("3.jpg").assertIsDisplayed()

        // Apply and verify callback payload
        composeTestRule.onNodeWithTag("batch_rename_confirm").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("2.jpg", "3.jpg"), appliedRenames?.map { it.second })
    }

    @Test
    fun folder_icon_proportions_stay_balanced_across_2_to_4_columns() {
        val density = 3f
        val b2 = com.example.ui.registry.BinaryIconRegistry.folderBoundsForCanvas(160f * density, 160f * density, density)
        val b3 = com.example.ui.registry.BinaryIconRegistry.folderBoundsForCanvas(104f * density, 104f * density, density)
        val b4 = com.example.ui.registry.BinaryIconRegistry.folderBoundsForCanvas(76f * density, 76f * density, density)
        val bList = com.example.ui.registry.BinaryIconRegistry.folderBoundsForCanvas(28f * density, 28f * density, density)

        listOf(b2, b3, b4, bList).forEach { bounds ->
            assertTrue("Expected standard iOS folder ratio 1.25..1.30, was ${bounds.aspectRatio}", bounds.aspectRatio in 1.25f..1.30f)
        }
        // Larger 2-col cells get wider horizontal breathing margin than compact 4-col cells
        assertTrue(b2.left / (160f * density) > b3.left / (104f * density))
        assertTrue(b3.left / (104f * density) > b4.left / (76f * density))

        val dimens = com.example.ui.theme.ArcDimens()
        val m2 = dimens.gridMetricsFor(2, 360.dp)
        val m3 = dimens.gridMetricsFor(3, 360.dp)
        val m4 = dimens.gridMetricsFor(4, 360.dp)
        assertTrue(m2.gridGap > m3.gridGap && m3.gridGap > m4.gridGap)
    }

    @Test
    fun inline_rename_works_in_list_and_grid_without_modal() {
        val sampleFile = FileItem("f1", "notes.txt", "/tmp/notes.txt", false, 128L, 1000L, "text/plain", "txt", false, "/tmp")
        var confirmedListName: String? = null
        var confirmedGridName: String? = null
        val listRenaming = androidx.compose.runtime.mutableStateOf(true)
        val gridRenaming = androidx.compose.runtime.mutableStateOf(false)

        composeTestRule.setContent {
            com.example.ui.theme.ArcFilesTheme {
                androidx.compose.foundation.layout.Column {
                    com.example.ui.screens.browser.FileListRow(
                        item = sampleFile,
                        isSelected = false,
                        selectionMode = false,
                        isRenaming = listRenaming.value,
                        monochrome = false,
                        onClick = {},
                        onLongClick = {},
                        onAction = { _, _ -> },
                        onConfirmRename = {
                            confirmedListName = it
                            listRenaming.value = false
                            gridRenaming.value = true
                        },
                        onCancelRename = { listRenaming.value = false }
                    )
                    com.example.ui.screens.browser.FileGridCard(
                        item = sampleFile,
                        isSelected = false,
                        selectionMode = false,
                        isRenaming = gridRenaming.value,
                        monochrome = false,
                        onClick = {},
                        onLongClick = {},
                        onConfirmRename = {
                            confirmedGridName = it
                            gridRenaming.value = false
                        },
                        onCancelRename = { gridRenaming.value = false }
                    )
                }
            }
        }

        // List row inline rename: verify inline input & confirm button appear cleanly
        composeTestRule.onNodeWithTag("inline_rename_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("inline_rename_cancel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("inline_rename_confirm").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals("notes.txt", confirmedListName)

        // Grid card inline rename: verify inline editor & confirm/cancel render cleanly inside grid card
        composeTestRule.onNodeWithTag("inline_rename_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("inline_rename_confirm").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals("notes.txt", confirmedGridName)
    }

    @Test
    fun beta_version_synced_and_default_light_theme_toggles_smoothly() {
        assertTrue(BuildConfig.VERSION_NAME.contains("beta"))
        assertEquals(15, BuildConfig.VERSION_CODE)
        assertEquals(com.example.ui.theme.ThemePreference.LIGHT, com.example.data.ExplorerPreferences().themePreference)

        composeTestRule.setContent {
            ArcFilesRoot()
        }

        // Open sidebar drawer and verify synced beta version + segmented theme toggle
        composeTestRule.onNodeWithTag("island_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("v${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("arc_sidebar_list").performScrollToNode(hasTestTag("drawer_theme_row"))
        composeTestRule.onNodeWithTag("drawer_theme_light").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_theme_dark").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_theme_light").performClick()
        composeTestRule.waitForIdle()

        // Open Settings and verify synced version badge & About Build code
        composeTestRule.onNodeWithTag("arc_sidebar_list").performScrollToNode(hasTestTag("drawer_settings_item"))
        composeTestRule.onNodeWithTag("drawer_settings_item").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("v${BuildConfig.VERSION_NAME.uppercase()}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_screen_list").performScrollToNode(hasText("About"))
        composeTestRule.onNodeWithText("Arc Files · v${BuildConfig.VERSION_NAME}").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Build ${BuildConfig.VERSION_CODE}").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun workspace_hidden_and_storage_card_matches_column_width() {
        composeTestRule.setContent {
            ArcFilesRoot()
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithTag("drive_card_drive_internal").fetchSemanticsNodes().isNotEmpty()
        }

        // Workspace tab, drive card, and tool tile must not be shown
        assertEquals(0, composeTestRule.onAllNodesWithTag("tab_chip_tab_workspace").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("drive_card_drive_workspace").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("tool_workspace").fetchSemanticsNodes().size)

        // Storage card bounds must align pixel-for-pixel with Space Analyzer & Tools grid column bounds
        val homeList = composeTestRule.onNodeWithTag("home_screen_list")
        homeList.performScrollToNode(hasTestTag("drive_card_drive_internal"))
        val storageBounds = composeTestRule.onNodeWithTag("drive_card_drive_internal").getBoundsInRoot()

        homeList.performScrollToNode(hasTestTag("home_space_analyzer_card"))
        val analyzerBounds = composeTestRule.onNodeWithTag("home_space_analyzer_card").getBoundsInRoot()

        homeList.performScrollToNode(hasTestTag("home_tools_card"))
        val toolsBounds = composeTestRule.onNodeWithTag("home_tools_card").getBoundsInRoot()

        assertEquals(analyzerBounds.left.value, storageBounds.left.value, 1f)
        assertEquals(analyzerBounds.right.value, storageBounds.right.value, 1f)
        assertEquals(analyzerBounds.width.value, storageBounds.width.value, 1f)
        assertEquals(toolsBounds.width.value, storageBounds.width.value, 1f)
    }

    @Test
    fun multi_drive_storage_cards_align_flush_with_column_edges() {
        val sampleDrives = listOf(
            com.example.data.DriveInfo(
                id = "drive_internal",
                name = "Internal Storage",
                subtitle = "/storage/emulated/0",
                pathOrUri = "/storage/emulated/0",
                type = com.example.data.DriveType.INTERNAL,
                totalBytes = 128_000_000_000L,
                freeBytes = 42_000_000_000L,
                isSaf = false
            ),
            com.example.data.DriveInfo(
                id = "drive_workspace",
                name = "App Workspace",
                subtitle = "/data/user/0/workspace",
                pathOrUri = "/data/user/0/workspace",
                type = com.example.data.DriveType.WORKSPACE,
                totalBytes = 10_000_000_000L,
                freeBytes = 9_000_000_000L,
                isSaf = false
            ),
            com.example.data.DriveInfo(
                id = "drive_sd",
                name = "SD Card",
                subtitle = "/storage/sdcard",
                pathOrUri = "/storage/sdcard",
                type = com.example.data.DriveType.EXTERNAL_VOLUME,
                totalBytes = 64_000_000_000L,
                freeBytes = 20_000_000_000L,
                isSaf = false
            ),
            com.example.data.DriveInfo(
                id = "drive_usb",
                name = "USB OTG",
                subtitle = "content://usb",
                pathOrUri = "content://usb",
                type = com.example.data.DriveType.SAF_TREE,
                totalBytes = 32_000_000_000L,
                freeBytes = 16_000_000_000L,
                isSaf = true
            )
        )

        composeTestRule.setContent {
            com.example.ui.theme.ArcFilesTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    com.example.ui.screens.home.StorageGaugeSection(
                        drives = sampleDrives,
                        monochrome = false,
                        onDriveClick = {},
                        onUnmountDrive = {},
                        onMountFolderClick = {}
                    )
                }
            }
        }

        // Workspace drive must still be filtered out even if passed in
        assertEquals(0, composeTestRule.onAllNodesWithTag("drive_card_drive_workspace").fetchSemanticsNodes().size)

        val internalBounds = composeTestRule.onNodeWithTag("drive_card_drive_internal").getBoundsInRoot()
        val sdBounds = composeTestRule.onNodeWithTag("drive_card_drive_sd").getBoundsInRoot()
        val usbBounds = composeTestRule.onNodeWithTag("drive_card_drive_usb").getBoundsInRoot()

        // Left edge of first card in 2-col row aligns flush with hero card left edge
        assertEquals(internalBounds.left.value, sdBounds.left.value, 1f)
        // Right edge of second card in 2-col row aligns flush with hero card right edge
        assertEquals(internalBounds.right.value, usbBounds.right.value, 1f)
        // Both half-width cards share identical width
        assertEquals(sdBounds.width.value, usbBounds.width.value, 1f)
    }

    @Test
    fun large_directory_1200_items_loads_and_virtualizes_smoothly_in_list_and_grid() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val bigDir = java.io.File(repo.getWorkspaceRoot(), "HugeFolder_${System.nanoTime()}").apply { mkdirs() }
        try {
            // Create 50 subfolders (some with children) + 1,150 files = 1,200 entries
            for (i in 0 until 50) {
                val sub = java.io.File(bigDir, "Folder_%03d".format(i)).apply { mkdirs() }
                if (i < 5) {
                    java.io.File(sub, "inner.txt").writeText("x")
                }
            }
            for (i in 0 until 1150) {
                java.io.File(bigDir, "Doc_%04d.txt".format(i)).writeText("sample")
            }

            val loaded = repo.listDirectory(
                pathOrUri = bigDir.absolutePath,
                isSaf = false,
                showHidden = false,
                sortField = SortField.NAME,
                ascending = true,
                foldersFirst = true,
                showFolderCounts = true
            ) as DirectoryLoadState.Loaded

            assertEquals(1200, loaded.items.size)
            // Initial large-folder load defers subfolder counts so UI renders immediately
            assertEquals(null, loaded.items.first { it.isDirectory }.childCount)

            // Deferred async subfolder count enrichment resolves counts accurately
            val folders = loaded.items.filter { it.isDirectory }
            val counts = repo.countSubfolderChildren(folders)
            assertEquals(1, counts[folders.first { it.name == "Folder_000" }.id])
            assertEquals(0, counts[folders.first { it.name == "Folder_010" }.id])

            val viewModeState = androidx.compose.runtime.mutableStateOf(com.example.data.ViewMode.LIST)
            val destination = com.example.data.TabDestination.Directory(
                title = "HugeFolder",
                rootId = "test_big",
                currentPathOrUri = bigDir.absolutePath,
                isSaf = false,
                breadcrumbs = listOf(com.example.data.BreadcrumbNode("HugeFolder", bigDir.absolutePath, false))
            )

            composeTestRule.setContent {
                com.example.ui.theme.ArcFilesTheme {
                    com.example.ui.screens.browser.BrowserScreen(
                        destination = destination,
                        directoryState = loaded,
                        categoryItems = emptyList(),
                        searchResults = emptyList(),
                        selectedIds = emptySet(),
                        clipboard = null,
                        viewMode = viewModeState.value,
                        gridColumns = 3,
                        sortField = SortField.NAME,
                        sortAscending = true,
                        groupBy = com.example.data.GroupByField.TYPE,
                        foldersFirst = true,
                        monochromeBinaryIcons = false,
                        onBack = {},
                        onBreadcrumbClick = {},
                        onSearchQueryChange = {},
                        onToggleViewMode = {},
                        onSortOrderChange = { _, _ -> },
                        onToggleSortOrder = {},
                        onGroupByChange = {},
                        onNewFolderClick = {},
                        onNewNoteClick = {},
                        onRequestAllFilesAccess = {},
                        onMountFolderClick = {},
                        onImportMediaClick = {},
                        onImportDocsClick = {},
                        onPasteClick = {},
                        onClearClipboard = {},
                        onFileClick = {},
                        onToggleSelect = {},
                        onSelectAll = {},
                        onClearSelection = {},
                        onFileAction = { _, _ -> }
                    )
                }
            }

            // Virtualization check in LIST mode: only viewport items are composed (far fewer than 1,200!)
            composeTestRule.onNodeWithTag("browser_file_list").assertIsDisplayed()
            assertEquals(0, composeTestRule.onAllNodesWithTag("file_row_Doc_1100.txt").fetchSemanticsNodes().size)
            composeTestRule.onNodeWithTag("browser_file_list").performScrollToNode(hasTestTag("file_row_Doc_1100.txt"))
            composeTestRule.onNodeWithTag("file_row_Doc_1100.txt").assertIsDisplayed()

            // Switch to 3-column GRID mode and verify virtualization + scroll to item #1100
            viewModeState.value = com.example.data.ViewMode.GRID
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("browser_file_grid").assertIsDisplayed()
            assertEquals(0, composeTestRule.onAllNodesWithTag("file_grid_Doc_1100.txt").fetchSemanticsNodes().size)
            composeTestRule.onNodeWithTag("browser_file_grid").performScrollToNode(hasTestTag("file_grid_Doc_1100.txt"))
            composeTestRule.onNodeWithTag("file_grid_Doc_1100.txt").assertIsDisplayed()
        } finally {
            bigDir.deleteRecursively()
        }
    }

    @Test
    fun sort_items_large_list_and_format_utils_work_consistently() {
        val items = (0 until 1500).map { idx ->
            val isDir = idx % 10 == 0
            FileItem(
                id = "id_$idx",
                name = if (isDir) "Dir_${1500 - idx}" else "File_${1500 - idx}.dat",
                pathOrUri = "/tmp/item_$idx",
                isDirectory = isDir,
                sizeBytes = if (isDir) 0L else (idx * 37L),
                lastModifiedMillis = 1_700_000_000_000L + idx * 1000L,
                mimeType = if (isDir) "vnd.android.document/directory" else "application/octet-stream",
                extension = if (isDir) "" else "dat",
                isSafUri = false,
                parentPathOrUri = "/tmp"
            )
        }
        val sortedAsc = com.example.data.FileGroupingUtils.sortItems(items, SortField.NAME, ascending = true, foldersFirst = true)
        assertEquals(1500, sortedAsc.size)
        // All 150 directories must appear before any file
        assertTrue(sortedAsc.take(150).all { it.isDirectory })
        assertTrue(sortedAsc.drop(150).none { it.isDirectory })

        val sortedSizeDesc = com.example.data.FileGroupingUtils.sortItems(items, SortField.SIZE, ascending = false, foldersFirst = true)
        assertEquals(1499 * 37L, sortedSizeDesc[150].sizeBytes)

        assertTrue(com.example.data.FormatUtils.formatShortDate(1_700_000_000_000L).isNotBlank())
        assertTrue(com.example.data.FormatUtils.formatDate(1_700_000_000_000L).isNotBlank())
    }

    @Test
    fun media_lightbox_swipes_queue_and_filters_non_media_items() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.ExplorerViewModel(app)

        val folder = FileItem("dir_1", "Pics", "/tmp/Pics", true, 0L, 1000L, "vnd.android.document/directory", "", false, "/tmp")
        val img1 = FileItem("m1", "one.jpg", "/tmp/one.jpg", false, 100L, 1000L, "image/jpeg", "jpg", false, "/tmp")
        val txt = FileItem("t1", "readme.txt", "/tmp/readme.txt", false, 50L, 1000L, "text/plain", "txt", false, "/tmp")
        val img2 = FileItem("m2", "two.png", "/tmp/two.png", false, 200L, 1000L, "image/png", "png", false, "/tmp")
        val vid1 = FileItem("m3", "clip.mp4", "/tmp/clip.mp4", false, 400L, 1000L, "video/mp4", "mp4", false, "/tmp")

        val mixedQueue = listOf(folder, img1, txt, img2, vid1)
        vm.openFile(img1, mixedQueue) {}

        // previewQueue must contain only media (images & videos), excluding directories and text files
        assertEquals(listOf("m1", "m2", "m3"), vm.uiState.value.previewQueue.map { it.id })
        assertEquals("m1", vm.uiState.value.previewFile?.id)

        var activeReportedId: String? = null
        var openWithRequestedId: String? = null
        var closed = false

        composeTestRule.setContent {
            com.example.ui.theme.ArcFilesTheme {
                com.example.ui.components.dialogs.MediaLightboxDialog(
                    queue = vm.uiState.value.previewQueue,
                    initialItem = img1,
                    onDismiss = { closed = true },
                    onActiveItemChanged = { activeReportedId = it.id },
                    onOpenWith = { openWithRequestedId = it.id },
                    onOpenExternal = {},
                    onShare = {},
                    onInfo = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("media_lightbox_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("one.jpg").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lightbox_thumbnail_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lightbox_thumb_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lightbox_thumb_2").assertIsDisplayed()
        // Thumbnail bar must not contain arrow buttons
        composeTestRule.onNodeWithTag("lightbox_prev_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("lightbox_next_button").assertDoesNotExist()

        // Swipe left horizontally to navigate to next image
        composeTestRule.onNodeWithTag("lightbox_pager").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        assertEquals("m2", activeReportedId)
        composeTestRule.onNodeWithText("two.png").assertIsDisplayed()

        // Tap video thumbnail in bar to jump to video item and trigger Open With button
        composeTestRule.onNodeWithTag("lightbox_thumb_2").performClick()
        composeTestRule.waitForIdle()
        assertEquals("m3", activeReportedId)
        composeTestRule.onNodeWithTag("lightbox_open_with_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("m3", openWithRequestedId)

        // Close lightbox
        composeTestRule.onNodeWithTag("lightbox_close_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(closed)
    }

    @Test
    fun es_style_open_with_supports_any_file_and_mime_overrides() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val root = repo.getWorkspaceRoot()
        val testBin = java.io.File(root, "movie_stream.dat").apply { writeText("payload") }
        try {
            val binItem = FileItem(
                id = testBin.absolutePath,
                name = testBin.name,
                pathOrUri = testBin.absolutePath,
                isDirectory = false,
                sizeBytes = testBin.length(),
                lastModifiedMillis = testBin.lastModified(),
                mimeType = "application/octet-stream",
                extension = "dat",
                isSafUri = false,
                parentPathOrUri = root.absolutePath
            )
            val folderItem = binItem.copy(id = "dir", name = "Sub", isDirectory = true)

            // FileActionRegistry exposes OPEN_WITH for single file of any type, never for directories
            val fileActions = com.example.ui.registry.FileActionRegistry.visibleFor(listOf(binItem)).map { it.id }
            val folderActions = com.example.ui.registry.FileActionRegistry.visibleFor(listOf(folderItem)).map { it.id }
            assertTrue(com.example.ui.registry.FileActionId.OPEN_WITH in fileActions)
            assertTrue(com.example.ui.registry.FileActionId.OPEN_WITH !in folderActions)

            // Repository buildViewIntent applies ES-style MIME overrides and read permissions
            val videoIntent = repo.buildViewIntent(binItem, mimeOverride = "video/*")
            assertEquals("video/*", videoIntent.type)
            assertTrue((videoIntent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)

            val audioIntent = repo.buildViewIntent(binItem, mimeOverride = "audio/*")
            assertEquals("audio/*", audioIntent.type)

            val anyIntent = repo.buildViewIntent(binItem, mimeOverride = "*/*")
            assertEquals("*/*", anyIntent.type)

            // UI sheet renders ES-style Open As options and emits selected MIME override
            var chosenMime: String? = "UNSET"
            composeTestRule.setContent {
                com.example.ui.theme.ArcFilesTheme {
                    com.example.ui.components.dialogs.OpenWithSheet(
                        item = binItem,
                        onDismiss = {},
                        onOpenWithMime = { chosenMime = it }
                    )
                }
            }

            composeTestRule.onNodeWithTag("open_with_sheet").assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_with_auto_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_as_video").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_as_audio").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_as_image").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_as_text").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_as_any").performScrollTo().assertIsDisplayed()

            composeTestRule.onNodeWithTag("open_as_video").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            assertEquals("video/*", chosenMime)
        } finally {
            testBin.delete()
        }
    }

    @Test
    fun ico_and_svg_thumbnails_and_metadata_decode_cleanly() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val workDir = java.io.File(repo.getWorkspaceRoot(), "VectorIcoTest_${System.nanoTime()}").apply { mkdirs() }
        try {
            // 1. Build a valid 4x4 32bpp DIB .ico file
            val w = 4
            val h = 4
            val headerSize = 40
            val xorBytes = w * h * 4
            val andStride = 4
            val andBytes = andStride * h
            val dibSize = headerSize + xorBytes + andBytes
            val imageOffset = 6 + 16
            val icoBytes = java.nio.ByteBuffer.allocate(imageOffset + dibSize)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
                    // ICONDIR
                    putShort(0) // reserved
                    putShort(1) // type = 1 (ICO)
                    putShort(1) // count = 1
                    // ICONDIRENTRY
                    put(w.toByte())
                    put(h.toByte())
                    put(0) // colorCount
                    put(0) // reserved
                    putShort(1) // planes
                    putShort(32) // bitCount
                    putInt(dibSize)
                    putInt(imageOffset)
                    // BITMAPINFOHEADER
                    putInt(40)
                    putInt(w)
                    putInt(h * 2) // XOR + AND height
                    putShort(1) // planes
                    putShort(32) // bpp
                    putInt(0) // BI_RGB
                    putInt(xorBytes)
                    putInt(0); putInt(0); putInt(0); putInt(0)
                    // XOR BGRA pixels (solid blue with full alpha)
                    repeat(w * h) {
                        put(0xFF.toByte()); put(0x40.toByte()); put(0x10.toByte()); put(0xFF.toByte())
                    }
                    // AND mask (all 0 = opaque)
                    repeat(andBytes) { put(0) }
                }.array()

            val icoFile = java.io.File(workDir, "favicon.ico").apply { writeBytes(icoBytes) }
            val svgFile = java.io.File(workDir, "logo.svg").apply {
                writeText("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 80"><circle cx="60" cy="40" r="30" fill="#2563EB"/></svg>""")
            }

            // Decode .ico directly via IcoDecoder
            val decodedIco = com.example.data.IcoDecoder.decodeBytesToBitmap(icoBytes, preferredSize = 64)
            assertTrue(decodedIco != null)
            assertEquals(4, decodedIco!!.width)
            assertEquals(4, decodedIco.height)

            // Verify FileItem & Metadata extraction for .ico and .svg
            val icoItem = FileItem(icoFile.absolutePath, icoFile.name, icoFile.absolutePath, false, icoFile.length(), icoFile.lastModified(), "image/x-icon", "ico", false, workDir.absolutePath)
            val svgItem = FileItem(svgFile.absolutePath, svgFile.name, svgFile.absolutePath, false, svgFile.length(), svgFile.lastModified(), "image/svg+xml", "svg", false, workDir.absolutePath)
            assertTrue(icoItem.isImage)
            assertTrue(svgItem.isImage)
            assertEquals(ExplorerCategory.PHOTOS, repo.categorizeFile(icoItem))
            assertEquals(ExplorerCategory.PHOTOS, repo.categorizeFile(svgItem))

            val icoMeta = com.example.data.MediaThumbnailLoader.loadMeta(context, icoItem, needVideoFrame = false)
            assertEquals(4, icoMeta?.width)
            assertEquals(4, icoMeta?.height)

            val svgMeta = com.example.data.MediaThumbnailLoader.loadMeta(context, svgItem, needVideoFrame = false)
            assertEquals(120, svgMeta?.width)
            assertEquals(80, svgMeta?.height)

            // Verify shared ArcImageLoader has components ready and UI composes cleanly
            val loader = com.example.data.ArcImageLoader.get(context)
            assertTrue(loader.components.decoderFactories.isNotEmpty())
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun apk_file_detection_thumbnail_fallback_and_fast_home_drive_load_work() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = FileExplorerRepository(app)
        val workDir = java.io.File(repo.getWorkspaceRoot(), "ApkThumbTest_${System.nanoTime()}").apply { mkdirs() }
        try {
            val fakeApk = java.io.File(workDir, "sample_app.apk").apply { writeText("not-a-real-apk") }
            val apkItem = FileItem(
                id = fakeApk.absolutePath,
                name = fakeApk.name,
                pathOrUri = fakeApk.absolutePath,
                isDirectory = false,
                sizeBytes = fakeApk.length(),
                lastModifiedMillis = fakeApk.lastModified(),
                mimeType = "application/vnd.android.package-archive",
                extension = "apk",
                isSafUri = false,
                parentPathOrUri = workDir.absolutePath
            )
            assertTrue(apkItem.isApk)

            // Gracefully returns null without throwing for invalid APK bytes and caches negative attempt
            val extracted = kotlinx.coroutines.runBlocking {
                com.example.data.MediaThumbnailLoader.loadApkIcon(app, apkItem)
            }
            assertEquals(null, extracted)
            assertTrue(com.example.data.MediaThumbnailLoader.hasCachedApkAttempt(apkItem))

            // Renders cleanly in both List thumbnail and Grid card
            composeTestRule.setContent {
                com.example.ui.theme.ArcFilesTheme {
                    androidx.compose.foundation.layout.Column {
                        com.example.ui.components.common.ArcThumbnail(
                            item = apkItem,
                            monochromeBinaryIcons = false
                        )
                        com.example.ui.screens.browser.FileGridCard(
                            item = apkItem,
                            isSelected = false,
                            selectionMode = false,
                            monochrome = false,
                            onClick = {},
                            onLongClick = {}
                        )
                    }
                }
            }
            composeTestRule.onNodeWithTag("file_grid_sample_app.apk").assertIsDisplayed()

            // Verify ViewModel loads internal drive immediately without waiting for heavy indexing
            val vm = com.example.ui.ExplorerViewModel(app)
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                vm.uiState.value.drives.any { it.id == "drive_internal" }
            }
            assertTrue(vm.uiState.value.drives.any { it.id == "drive_internal" })
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun lightbox_zoom_tiers_and_focal_point_math_work_for_hd_fhd_uhd_and_svg() {
        val boxW = 1080f
        val boxH = 1920f

        val smallSpec = com.example.ui.components.dialogs.computeZoomTierSpec(800, 600, boxW, boxH)
        val hdSpec = com.example.ui.components.dialogs.computeZoomTierSpec(1280, 720, boxW, boxH)
        val fhdSpec = com.example.ui.components.dialogs.computeZoomTierSpec(1920, 1080, boxW, boxH)
        val uhdSpec = com.example.ui.components.dialogs.computeZoomTierSpec(3840, 2160, boxW, boxH)
        val ultra8kSpec = com.example.ui.components.dialogs.computeZoomTierSpec(8000, 6000, boxW, boxH)
        val svgSpec = com.example.ui.components.dialogs.computeZoomTierSpec(200, 200, boxW, boxH, isSvg = true)

        assertEquals(listOf("HD"), hdSpec.stageLabels)
        assertEquals(listOf("HD", "FHD"), fhdSpec.stageLabels)
        assertEquals(listOf("HD", "FHD", "Ultra HD"), uhdSpec.stageLabels)
        assertEquals(listOf("HD", "Ultra HD"), svgSpec.stageLabels)
        assertTrue(uhdSpec.maxScale >= fhdSpec.maxScale)
        assertTrue(fhdSpec.maxScale >= hdSpec.maxScale)
        assertTrue(ultra8kSpec.stages.last() <= 8.5f)
        assertTrue(smallSpec.doubleTapScale in 2.0f..2.5f)
        assertEquals(10f, svgSpec.maxScale, 0.01f)

        // Progressive stage stepping: 1x -> HD (0) -> FHD (1) -> Ultra HD (2) -> reset (null)
        assertEquals(0, com.example.ui.components.dialogs.nextDoubleTapStageIndex(1f, uhdSpec))
        assertEquals(1, com.example.ui.components.dialogs.nextDoubleTapStageIndex(uhdSpec.stages[0], uhdSpec))
        assertEquals(2, com.example.ui.components.dialogs.nextDoubleTapStageIndex(uhdSpec.stages[1], uhdSpec))
        assertEquals(null, com.example.ui.components.dialogs.nextDoubleTapStageIndex(uhdSpec.stages[2], uhdSpec))
        assertEquals(null, com.example.ui.components.dialogs.activeZoomStageLabel(1f, uhdSpec))
        assertEquals("HD", com.example.ui.components.dialogs.activeZoomStageLabel(uhdSpec.stages[0], uhdSpec))
        assertEquals("FHD", com.example.ui.components.dialogs.activeZoomStageLabel(uhdSpec.stages[1], uhdSpec))
        assertEquals("Ultra HD", com.example.ui.components.dialogs.activeZoomStageLabel(uhdSpec.stages[2], uhdSpec))

        // EXIF orientation mapping for region decoder
        val rect = android.graphics.Rect(100, 200, 300, 500)
        assertEquals(
            rect,
            com.example.ui.components.dialogs.mapDisplayRectToRawRegion(rect, 1000, 2000, android.media.ExifInterface.ORIENTATION_NORMAL)
        )
        assertEquals(
            android.graphics.Rect(200, 700, 500, 900),
            com.example.ui.components.dialogs.mapDisplayRectToRawRegion(rect, 1000, 2000, android.media.ExifInterface.ORIENTATION_ROTATE_90)
        )
        assertEquals(
            android.graphics.Rect(700, 1500, 900, 1800),
            com.example.ui.components.dialogs.mapDisplayRectToRawRegion(rect, 1000, 2000, android.media.ExifInterface.ORIENTATION_ROTATE_180)
        )
        assertEquals(
            android.graphics.Rect(1500, 100, 1800, 300),
            com.example.ui.components.dialogs.mapDisplayRectToRawRegion(rect, 1000, 2000, android.media.ExifInterface.ORIENTATION_ROTATE_270)
        )

        // Fitted size of a 1920x1080 (16:9) photo inside 1080x1920 portrait screen
        val (fitW, fitH) = com.example.ui.components.dialogs.computeFittedSize(1920, 1080, boxW, boxH)
        assertEquals(1080f, fitW, 0.5f)
        assertEquals(607.5f, fitH, 0.5f)

        // 1. 16:9 FHD photo in 1080x1920 portrait screen: horizontal focal shift works while vertical stays clamped (no black gap)
        val topLeftTap = Offset(180f, 700f)
        val bottomRightTap = Offset(900f, 1220f)
        val topLeftOffset = com.example.ui.components.dialogs.calculateFocalZoomOffset(
            focalPoint = topLeftTap,
            containerWidth = boxW,
            containerHeight = boxH,
            fittedWidth = fitW,
            fittedHeight = fitH,
            currentScale = 1f,
            currentOffset = Offset.Zero,
            targetScale = fhdSpec.doubleTapScale
        )
        val bottomRightOffset = com.example.ui.components.dialogs.calculateFocalZoomOffset(
            focalPoint = bottomRightTap,
            containerWidth = boxW,
            containerHeight = boxH,
            fittedWidth = fitW,
            fittedHeight = fitH,
            currentScale = 1f,
            currentOffset = Offset.Zero,
            targetScale = fhdSpec.doubleTapScale
        )

        assertTrue("Top-left double-tap should shift image right, got $topLeftOffset", topLeftOffset.x > 100f)
        assertTrue("Bottom-right double-tap should shift image left, got $bottomRightOffset", bottomRightOffset.x < -100f)
        assertEquals(0f, topLeftOffset.y, 0.01f)

        // 2. 4:3 Ultra-HD photo (4000x3000) zoomed to Ultra HD stage (>=5.0x) overflows BOTH axes -> focal point shifts both X & Y
        val uhdFinalScale = uhdSpec.stages.last()
        val (uhdFitW, uhdFitH) = com.example.ui.components.dialogs.computeFittedSize(4000, 3000, boxW, boxH)
        val uhdTopLeft = com.example.ui.components.dialogs.calculateFocalZoomOffset(
            focalPoint = Offset(180f, 650f),
            containerWidth = boxW,
            containerHeight = boxH,
            fittedWidth = uhdFitW,
            fittedHeight = uhdFitH,
            currentScale = 1f,
            currentOffset = Offset.Zero,
            targetScale = uhdFinalScale
        )
        val uhdBottomRight = com.example.ui.components.dialogs.calculateFocalZoomOffset(
            focalPoint = Offset(900f, 1270f),
            containerWidth = boxW,
            containerHeight = boxH,
            fittedWidth = uhdFitW,
            fittedHeight = uhdFitH,
            currentScale = 1f,
            currentOffset = Offset.Zero,
            targetScale = uhdFinalScale
        )
        assertTrue("UHD top-left tap should shift right+down, got $uhdTopLeft", uhdTopLeft.x > 100f && uhdTopLeft.y > 100f)
        assertTrue("UHD bottom-right tap should shift left+up, got $uhdBottomRight", uhdBottomRight.x < -100f && uhdBottomRight.y < -100f)

        // Offset stays strictly clamped within scaled image bounds
        val maxPanX = ((uhdFitW * uhdFinalScale - boxW) * 0.5f).coerceAtLeast(0f)
        val maxPanY = ((uhdFitH * uhdFinalScale - boxH) * 0.5f).coerceAtLeast(0f)
        assertTrue(kotlin.math.abs(uhdTopLeft.x) <= maxPanX + 0.01f)
        assertTrue(kotlin.math.abs(uhdTopLeft.y) <= maxPanY + 0.01f)
    }

    @Test
    fun lightbox_double_tap_focal_zoom_and_zoom_out_work_smoothly_in_ui() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val workDir = java.io.File(repo.getWorkspaceRoot(), "ZoomUiTest_${System.nanoTime()}").apply { mkdirs() }
        try {
            val fhdFile = java.io.File(workDir, "fhd_sample.png")
            val bmp = android.graphics.Bitmap.createBitmap(1920, 1080, android.graphics.Bitmap.Config.ARGB_8888)
            java.io.FileOutputStream(fhdFile).use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
            }
            bmp.recycle()

            val imgItem = FileItem(
                id = fhdFile.absolutePath,
                name = fhdFile.name,
                pathOrUri = fhdFile.absolutePath,
                isDirectory = false,
                sizeBytes = fhdFile.length(),
                lastModifiedMillis = fhdFile.lastModified(),
                mimeType = "image/png",
                extension = "png",
                isSafUri = false,
                parentPathOrUri = workDir.absolutePath
            )
            var zoomedState = false

            composeTestRule.setContent {
                com.example.ui.theme.ArcFilesTheme {
                    androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.size(360.dp, 640.dp)) {
                        com.example.ui.components.dialogs.OriginalImageViewer(
                            item = imgItem,
                            onZoomChanged = { zoomedState = it }
                        )
                    }
                }
            }

            // Wait for 1920 x 1080 · FHD metadata badge to resolve
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithText("1920 × 1080 · FHD", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("1920 × 1080 · FHD", substring = true).assertIsDisplayed()

            // 1st double-tap near top-left -> smoothly steps into HD stage (never jumps straight to max zoom)
            composeTestRule.onNodeWithTag("original_image_viewer").performTouchInput {
                doubleClick(position = Offset(width * 0.2f, height * 0.4f))
            }
            composeTestRule.waitUntil(5000) {
                zoomedState &&
                    composeTestRule.onAllNodesWithText("1920 × 1080 · FHD · HD · ", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("1920 × 1080 · FHD · HD · ", substring = true).assertIsDisplayed()

            // 2nd double-tap -> smoothly steps into FHD stage
            composeTestRule.onNodeWithTag("original_image_viewer").performTouchInput {
                doubleClick(position = Offset(width * 0.5f, height * 0.5f))
            }
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithText("1920 × 1080 · FHD · FHD · ", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("1920 × 1080 · FHD · FHD · ", substring = true).assertIsDisplayed()

            // 3rd double-tap after final FHD stage -> smoothly zooms back out to 1x
            composeTestRule.onNodeWithTag("original_image_viewer").performTouchInput {
                doubleClick(position = Offset(width * 0.5f, height * 0.5f))
            }
            composeTestRule.waitUntil(5000) { !zoomedState }
            assertTrue(!zoomedState)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun lightbox_uhd_three_stage_double_tap_progresses_hd_fhd_uhd_then_resets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val workDir = java.io.File(repo.getWorkspaceRoot(), "UhdStageTest_${System.nanoTime()}").apply { mkdirs() }
        try {
            val uhdFile = java.io.File(workDir, "uhd_sample.png")
            val bmp = android.graphics.Bitmap.createBitmap(3840, 2160, android.graphics.Bitmap.Config.RGB_565)
            java.io.FileOutputStream(uhdFile).use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 85, out)
            }
            bmp.recycle()

            val imgItem = FileItem(
                id = uhdFile.absolutePath,
                name = uhdFile.name,
                pathOrUri = uhdFile.absolutePath,
                isDirectory = false,
                sizeBytes = uhdFile.length(),
                lastModifiedMillis = uhdFile.lastModified(),
                mimeType = "image/png",
                extension = "png",
                isSafUri = false,
                parentPathOrUri = workDir.absolutePath
            )
            var zoomedState = false

            composeTestRule.setContent {
                com.example.ui.theme.ArcFilesTheme {
                    androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.size(360.dp, 640.dp)) {
                        com.example.ui.components.dialogs.OriginalImageViewer(
                            item = imgItem,
                            onZoomChanged = { zoomedState = it }
                        )
                    }
                }
            }

            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithText("3840 × 2160 · 4K Ultra HD", substring = true).fetchSemanticsNodes().isNotEmpty()
            }

            // Stage 1: HD
            composeTestRule.onNodeWithTag("original_image_viewer").performTouchInput {
                doubleClick(position = Offset(width * 0.5f, height * 0.5f))
            }
            composeTestRule.waitUntil(5000) {
                zoomedState &&
                    composeTestRule.onAllNodesWithText("3840 × 2160 · 4K Ultra HD · HD · ", substring = true).fetchSemanticsNodes().isNotEmpty()
            }

            // Stage 2: FHD
            composeTestRule.onNodeWithTag("original_image_viewer").performTouchInput {
                doubleClick(position = Offset(width * 0.5f, height * 0.5f))
            }
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithText("3840 × 2160 · 4K Ultra HD · FHD · ", substring = true).fetchSemanticsNodes().isNotEmpty()
            }

            // Stage 3: Ultra HD
            composeTestRule.onNodeWithTag("original_image_viewer").performTouchInput {
                doubleClick(position = Offset(width * 0.5f, height * 0.5f))
            }
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithText("3840 × 2160 · 4K Ultra HD · Ultra HD · ", substring = true).fetchSemanticsNodes().isNotEmpty()
            }

            // 4th double-tap resets smoothly to 1x
            composeTestRule.onNodeWithTag("original_image_viewer").performTouchInput {
                doubleClick(position = Offset(width * 0.5f, height * 0.5f))
            }
            composeTestRule.waitUntil(5000) { !zoomedState }
            assertTrue(!zoomedState)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun gif_and_ios_heic_images_supported_in_thumbnails_metadata_and_lightbox() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val workDir = java.io.File(repo.getWorkspaceRoot(), "GifHeicTest_${System.nanoTime()}").apply { mkdirs() }
        try {
            // Minimal valid 2x2 GIF89a binary
            val gifBytes = byteArrayOf(
                0x47, 0x49, 0x46, 0x38, 0x39, 0x61, // GIF89a
                0x02, 0x00, 0x02, 0x00,             // 2x2
                0x80.toByte(), 0x00, 0x00,          // GCT flag + colors
                0xFF.toByte(), 0x00, 0x00,          // Color 0: red
                0x00, 0xFF.toByte(), 0x00,          // Color 1: green
                0x2C, 0x00, 0x00, 0x00, 0x00,       // Image descriptor
                0x02, 0x00, 0x02, 0x00, 0x00,
                0x02, 0x02, 0x44, 0x01, 0x00,
                0x3B                                // Trailer
            )
            val gifFile = java.io.File(workDir, "sticker.gif").apply { writeBytes(gifBytes) }

            // Create iOS image extension items
            val heicFile = java.io.File(workDir, "iphone_shot.heic")
            val bmp = android.graphics.Bitmap.createBitmap(1600, 1200, android.graphics.Bitmap.Config.ARGB_8888)
            java.io.FileOutputStream(heicFile).use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            bmp.recycle()

            val exts = listOf("gif", "heic", "heif", "heics", "heifs", "hif", "avif")
            exts.forEach { ext ->
                val sample = FileItem(
                    id = "/tmp/sample.$ext",
                    name = "sample.$ext",
                    pathOrUri = "/tmp/sample.$ext",
                    isDirectory = false,
                    sizeBytes = 1024L,
                    lastModifiedMillis = 1L,
                    mimeType = "",
                    extension = ext,
                    isSafUri = false,
                    parentPathOrUri = "/tmp"
                )
                assertTrue("Expected $ext to be treated as image", sample.isImage)
            }

            val gifItem = FileItem(
                id = gifFile.absolutePath,
                name = gifFile.name,
                pathOrUri = gifFile.absolutePath,
                isDirectory = false,
                sizeBytes = gifFile.length(),
                lastModifiedMillis = gifFile.lastModified(),
                mimeType = "image/gif",
                extension = "gif",
                isSafUri = false,
                parentPathOrUri = workDir.absolutePath
            )
            val heicItem = FileItem(
                id = heicFile.absolutePath,
                name = heicFile.name,
                pathOrUri = heicFile.absolutePath,
                isDirectory = false,
                sizeBytes = heicFile.length(),
                lastModifiedMillis = heicFile.lastModified(),
                mimeType = "image/heic",
                extension = "heic",
                isSafUri = false,
                parentPathOrUri = workDir.absolutePath
            )

            // Ensure Coil ImageLoader registers GIF/ImageDecoder factory cleanly
            val loader = com.example.data.ArcImageLoader.get(context)
            assertTrue(loader.components.decoderFactories.isNotEmpty())

            val showGifInViewer = androidx.compose.runtime.mutableStateOf(true)
            composeTestRule.setContent {
                com.example.ui.theme.ArcFilesTheme {
                    androidx.compose.foundation.layout.Column {
                        androidx.compose.foundation.layout.Row {
                            androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.size(140.dp)) {
                                com.example.ui.screens.browser.FileGridCard(
                                    item = gifItem,
                                    isSelected = false,
                                    selectionMode = false,
                                    monochrome = false,
                                    onClick = {},
                                    onLongClick = {}
                                )
                            }
                            androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.size(140.dp)) {
                                com.example.ui.screens.browser.FileGridCard(
                                    item = heicItem,
                                    isSelected = false,
                                    selectionMode = false,
                                    monochrome = false,
                                    onClick = {},
                                    onLongClick = {}
                                )
                            }
                        }
                        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.size(320.dp, 420.dp)) {
                            com.example.ui.components.dialogs.OriginalImageViewer(
                                item = if (showGifInViewer.value) gifItem else heicItem
                            )
                        }
                    }
                }
            }

            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithText("2 × 2 · GIF", substring = true).fetchSemanticsNodes().isNotEmpty() &&
                    composeTestRule.onAllNodesWithText("GIF", substring = false).fetchSemanticsNodes().isNotEmpty() &&
                    composeTestRule.onAllNodesWithText("HEIC", substring = false).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("2 × 2 · GIF", substring = true).assertIsDisplayed()

            showGifInViewer.value = false
            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithText("1600 × 1200 · HD · HEIC", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("1600 × 1200 · HD · HEIC", substring = true).assertIsDisplayed()
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun lightbox_touch_gestures_dismiss_info_and_one_handed_zoom_work() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val workDir = java.io.File(repo.getWorkspaceRoot(), "TouchGesturesTest_${System.nanoTime()}").apply { mkdirs() }
        try {
            val imgFile = java.io.File(workDir, "sample.png")
            val bmp = android.graphics.Bitmap.createBitmap(1280, 720, android.graphics.Bitmap.Config.ARGB_8888)
            java.io.FileOutputStream(imgFile).use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
            }
            bmp.recycle()

            val imgItem = FileItem(
                id = imgFile.absolutePath,
                name = imgFile.name,
                pathOrUri = imgFile.absolutePath,
                isDirectory = false,
                sizeBytes = imgFile.length(),
                lastModifiedMillis = imgFile.lastModified(),
                mimeType = "image/png",
                extension = "png",
                isSafUri = false,
                parentPathOrUri = workDir.absolutePath
            )
            var dismissed = false
            var infoOpened = false
            var zoomed = false
            var maxDragProgress = 0f

            composeTestRule.setContent {
                com.example.ui.theme.ArcFilesTheme {
                    androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.size(360.dp, 640.dp)) {
                        com.example.ui.components.dialogs.OriginalImageViewer(
                            item = imgItem,
                            onZoomChanged = { zoomed = it },
                            onDismiss = { dismissed = true },
                            onSwipeUpInfo = { infoOpened = true },
                            onDismissDragProgress = { if (it > maxDragProgress) maxDragProgress = it }
                        )
                    }
                }
            }

            composeTestRule.waitUntil(5000) {
                composeTestRule.onAllNodesWithText("1280 × 720 · HD", substring = true).fetchSemanticsNodes().isNotEmpty()
            }

            // 1. Swipe up at 1x opens media info
            composeTestRule.onNodeWithTag("original_image_viewer").performTouchInput {
                swipeUp(startY = height * 0.72f, endY = height * 0.35f)
            }
            composeTestRule.waitUntil(3000) { infoOpened }
            assertTrue(infoOpened)
            assertTrue(!dismissed)

            // 2. Double-tap + drag down (one-handed continuous zoom) zooms in smoothly
            composeTestRule.onNodeWithTag("original_image_viewer").performTouchInput {
                val anchor = Offset(width * 0.5f, height * 0.35f)
                down(anchor)
                up()
                advanceEventTime(90)
                down(anchor)
                moveBy(Offset(0f, 180f))
                up()
            }
            composeTestRule.waitUntil(3000) { zoomed }
            assertTrue(zoomed)

            // Double-tap to reset back to 1x
            composeTestRule.onNodeWithTag("original_image_viewer").performTouchInput {
                doubleClick(position = Offset(width * 0.5f, height * 0.5f))
            }
            composeTestRule.waitUntil(3000) { !zoomed }

            // 3. Swipe down at 1x drives backdrop progress and dismisses lightbox
            composeTestRule.onNodeWithTag("original_image_viewer").performTouchInput {
                swipeDown(startY = height * 0.25f, endY = height * 0.78f)
            }
            composeTestRule.waitUntil(3000) { dismissed }
            assertTrue(dismissed)
            assertTrue(maxDragProgress > 0.1f)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun repository_copy_and_move_emit_byte_progress_and_prevent_self_nesting() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val workDir = java.io.File(repo.getWorkspaceRoot(), "TransferEngineTest_${System.nanoTime()}").apply { mkdirs() }
        try {
            val srcFolder = java.io.File(workDir, "SourceDrive").apply { mkdirs() }
            val subFolder = java.io.File(srcFolder, "Photos").apply { mkdirs() }
            val fileA = java.io.File(srcFolder, "movie_part.bin").apply { writeBytes(ByteArray(140_000) { 0x2A }) }
            val fileB = java.io.File(subFolder, "photo_raw.dng").apply { writeBytes(ByteArray(80_000) { 0x55 }) }
            val dstFolder = java.io.File(workDir, "DestDrive").apply { mkdirs() }

            val folderItem = FileItem(
                id = srcFolder.absolutePath,
                name = srcFolder.name,
                pathOrUri = srcFolder.absolutePath,
                isDirectory = true,
                sizeBytes = 0L,
                lastModifiedMillis = srcFolder.lastModified(),
                mimeType = "inode/directory",
                extension = "",
                isSafUri = false,
                parentPathOrUri = workDir.absolutePath
            )
            val fileAItem = FileItem(
                id = fileA.absolutePath,
                name = fileA.name,
                pathOrUri = fileA.absolutePath,
                isDirectory = false,
                sizeBytes = fileA.length(),
                lastModifiedMillis = fileA.lastModified(),
                mimeType = "application/octet-stream",
                extension = "bin",
                isSafUri = false,
                parentPathOrUri = srcFolder.absolutePath
            )

            // 1. Copy recursive folder + track PREPARING -> IN_PROGRESS -> COMPLETED byte telemetry
            val copySnapshots = mutableListOf<com.example.data.FileTransferProgress>()
            val copyRes = kotlinx.coroutines.runBlocking {
                repo.copyOrMoveItems(
                    items = listOf(folderItem),
                    targetParentPathOrUri = dstFolder.absolutePath,
                    isTargetSaf = false,
                    isMove = false,
                    sourceLabel = "Internal",
                    destinationLabel = "DestDrive",
                    onProgress = { copySnapshots.add(it) }
                )
            }
            assertTrue(copyRes.isSuccess)
            assertEquals(1, copyRes.getOrNull())
            assertTrue(copySnapshots.any { it.status == com.example.data.TransferStatus.PREPARING })
            assertTrue(copySnapshots.any { it.status == com.example.data.TransferStatus.IN_PROGRESS })
            val finalCopy = copySnapshots.last()
            assertEquals(com.example.data.TransferStatus.COMPLETED, finalCopy.status)
            assertEquals(2, finalCopy.totalItems)
            assertEquals(2, finalCopy.processedItems)
            assertEquals(fileA.length() + fileB.length(), finalCopy.totalBytes)
            assertEquals(finalCopy.totalBytes, finalCopy.copiedBytes)
            assertEquals(100, finalCopy.percent)
            assertTrue(java.io.File(dstFolder, "SourceDrive/movie_part.bin").length() == fileA.length())
            assertTrue(java.io.File(dstFolder, "SourceDrive/Photos/photo_raw.dng").length() == fileB.length())

            // 2. Self-nesting guard rejects copying a folder inside itself
            val selfTarget = java.io.File(srcFolder, "Photos")
            val selfRes = kotlinx.coroutines.runBlocking {
                repo.copyOrMoveItems(
                    items = listOf(folderItem),
                    targetParentPathOrUri = selfTarget.absolutePath,
                    isTargetSaf = false,
                    isMove = false
                )
            }
            assertTrue(selfRes.isFailure)

            // 3. Move emits completion and removes source
            val moveTarget = java.io.File(workDir, "MoveTarget").apply { mkdirs() }
            val moveSnapshots = mutableListOf<com.example.data.FileTransferProgress>()
            val moveRes = kotlinx.coroutines.runBlocking {
                repo.copyOrMoveItems(
                    items = listOf(fileAItem),
                    targetParentPathOrUri = moveTarget.absolutePath,
                    isTargetSaf = false,
                    isMove = true,
                    onProgress = { moveSnapshots.add(it) }
                )
            }
            assertTrue(moveRes.isSuccess)
            assertTrue(!fileA.exists())
            assertTrue(java.io.File(moveTarget, "movie_part.bin").exists())
            assertEquals(com.example.data.TransferStatus.COMPLETED, moveSnapshots.last().status)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun transfer_progress_host_displays_telemetry_minimizes_expands_and_cancels() {
        val transferState = androidx.compose.runtime.mutableStateOf<com.example.data.FileTransferProgress?>(
            com.example.data.FileTransferProgress(
                taskId = "tx_1",
                operation = com.example.data.TransferOperationType.MOVE,
                status = com.example.data.TransferStatus.IN_PROGRESS,
                currentFileName = "vacation_4k.mp4",
                sourceLabel = "Internal Storage",
                destinationLabel = "USB Drive",
                processedItems = 3,
                totalItems = 8,
                copiedBytes = 512L * 1024L,
                totalBytes = 1024L * 1024L,
                speedBytesPerSec = 12L * 1024L * 1024L,
                etaMillis = 5000L,
                isMinimized = false
            )
        )
        var cancelled = false

        composeTestRule.setContent {
            com.example.ui.theme.ArcFilesTheme {
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.size(380.dp, 720.dp)) {
                    com.example.ui.components.common.FileTransferProgressHost(
                        transfer = transferState.value,
                        onMinimize = {
                            transferState.value = transferState.value?.copy(isMinimized = true)
                        },
                        onExpand = {
                            transferState.value = transferState.value?.copy(isMinimized = false)
                        },
                        onCancel = {
                            cancelled = true
                            transferState.value = null
                        },
                        onDismissError = { transferState.value = null }
                    )
                }
            }
        }

        // Expanded ES File Explorer-style dialog shows full telemetry
        composeTestRule.onNodeWithTag("file_transfer_dialog_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Moving files").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 of 8 item(s)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Internal Storage").assertIsDisplayed()
        composeTestRule.onNodeWithText("USB Drive").assertIsDisplayed()
        composeTestRule.onNodeWithText("vacation_4k.mp4").assertIsDisplayed()
        composeTestRule.onNodeWithText("50%").assertIsDisplayed()
        composeTestRule.onNodeWithText("5s").assertIsDisplayed()

        // Tapping Hide switches cleanly to the floating mini pill
        composeTestRule.onNodeWithTag("transfer_hide_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("transfer_mini_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Moving (3/8)").assertIsDisplayed()

        // Tapping Expand restores the full progress card
        composeTestRule.onNodeWithTag("transfer_mini_expand").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("file_transfer_dialog_card").assertIsDisplayed()

        // Tapping Cancel aborts and closes the transfer host
        composeTestRule.onNodeWithTag("transfer_cancel_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(cancelled)
        assertTrue(composeTestRule.onAllNodesWithTag("file_transfer_dialog_card").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithTag("transfer_mini_bar").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun drag_drop_validation_blocks_self_nesting_descendants_and_same_parent_noop() {
        val folderA = FileItem(
            id = "/root/FolderA",
            name = "FolderA",
            pathOrUri = "/root/FolderA",
            isDirectory = true,
            sizeBytes = 0L,
            lastModifiedMillis = 1L,
            mimeType = "inode/directory",
            extension = "",
            isSafUri = false,
            parentPathOrUri = "/root"
        )
        val noteFile = FileItem(
            id = "/root/note.txt",
            name = "note.txt",
            pathOrUri = "/root/note.txt",
            isDirectory = false,
            sizeBytes = 12L,
            lastModifiedMillis = 1L,
            mimeType = "text/plain",
            extension = "txt",
            isSafUri = false,
            parentPathOrUri = "/root"
        )

        // Valid sibling and ancestor targets
        assertTrue(com.example.ui.screens.browser.BrowserDragDropState.isValidDropTarget(listOf(folderA), "/root/Sibling"))
        assertTrue(com.example.ui.screens.browser.BrowserDragDropState.isValidDropTarget(listOf(noteFile), "/root/FolderA"))
        assertTrue(com.example.ui.screens.browser.BrowserDragDropState.isValidDropTarget(listOf(noteFile), "/parent"))

        // Invalid: self-drop, descendant subfolder drop, and current-parent no-op
        assertTrue(!com.example.ui.screens.browser.BrowserDragDropState.isValidDropTarget(listOf(folderA), "/root/FolderA"))
        assertTrue(!com.example.ui.screens.browser.BrowserDragDropState.isValidDropTarget(listOf(folderA), "/root/FolderA/Sub/Deep"))
        assertTrue(!com.example.ui.screens.browser.BrowserDragDropState.isValidDropTarget(listOf(noteFile), "/root"))
        assertTrue(!com.example.ui.screens.browser.BrowserDragDropState.isValidDropTarget(listOf(folderA, noteFile), "/root"))
    }

    @Test
    fun drag_and_drop_move_to_folder_and_breadcrumb_work_in_list_and_grid_preserving_long_press_select() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val rootDir = java.io.File(repo.getWorkspaceRoot(), "DragDropUi_${System.nanoTime()}").apply { mkdirs() }
        val currentDir = java.io.File(rootDir, "Current").apply { mkdirs() }
        try {
            val targetFolder = java.io.File(currentDir, "DestFolder").apply { mkdirs() }
            val alphaFile = java.io.File(currentDir, "alpha.txt").apply { writeText("hello drag") }

            val folderItem = FileItem(
                id = targetFolder.absolutePath,
                name = targetFolder.name,
                pathOrUri = targetFolder.absolutePath,
                isDirectory = true,
                sizeBytes = 0L,
                lastModifiedMillis = targetFolder.lastModified(),
                mimeType = "inode/directory",
                extension = "",
                isSafUri = false,
                parentPathOrUri = currentDir.absolutePath
            )
            val alphaItem = FileItem(
                id = alphaFile.absolutePath,
                name = alphaFile.name,
                pathOrUri = alphaFile.absolutePath,
                isDirectory = false,
                sizeBytes = alphaFile.length(),
                lastModifiedMillis = alphaFile.lastModified(),
                mimeType = "text/plain",
                extension = "txt",
                isSafUri = false,
                parentPathOrUri = currentDir.absolutePath
            )

            val selectedIdsState = androidx.compose.runtime.mutableStateOf(emptySet<String>())
            val viewModeState = androidx.compose.runtime.mutableStateOf(ViewMode.LIST)
            var droppedItems: List<FileItem> = emptyList()
            var droppedTargetPath: String? = null
            var droppedTargetLabel: String? = null

            val dest = TabDestination.Directory(
                title = "Current",
                rootId = "test_root",
                currentPathOrUri = currentDir.absolutePath,
                isSaf = false,
                breadcrumbs = listOf(
                    com.example.data.BreadcrumbNode("RootBox", rootDir.absolutePath, false),
                    com.example.data.BreadcrumbNode("Current", currentDir.absolutePath, false)
                )
            )

            composeTestRule.setContent {
                com.example.ui.theme.ArcFilesTheme {
                    com.example.ui.screens.browser.BrowserScreen(
                        destination = dest,
                        directoryState = DirectoryLoadState.Loaded(
                            items = listOf(folderItem, alphaItem)
                        ),
                        categoryItems = emptyList(),
                        searchResults = emptyList(),
                        selectedIds = selectedIdsState.value,
                        clipboard = null,
                        viewMode = viewModeState.value,
                        sortField = SortField.NAME,
                        sortAscending = true,
                        monochromeBinaryIcons = false,
                        onBack = {},
                        onBreadcrumbClick = {},
                        onSearchQueryChange = {},
                        onToggleViewMode = {},
                        onSortOrderChange = { _, _ -> },
                        onToggleSortOrder = {},
                        onGroupByChange = {},
                        onNewFolderClick = {},
                        onNewNoteClick = {},
                        onRequestAllFilesAccess = {},
                        onMountFolderClick = {},
                        onImportMediaClick = {},
                        onImportDocsClick = {},
                        onPasteClick = {},
                        onClearClipboard = {},
                        onFileClick = {},
                        onToggleSelect = { item ->
                            val next = selectedIdsState.value.toMutableSet()
                            if (!next.add(item.id)) next.remove(item.id)
                            selectedIdsState.value = next
                        },
                        onSelectAll = {},
                        onClearSelection = { selectedIdsState.value = emptySet() },
                        onFileAction = { _, _ -> },
                        onMoveItemsToFolder = { items, targetPath, _, label ->
                            droppedItems = items
                            droppedTargetPath = targetPath
                            droppedTargetLabel = label
                        }
                    )
                }
            }

            // 1. Long-press stationary without moving still toggles selection
            composeTestRule.onNodeWithTag("file_row_alpha.txt").performTouchInput {
                down(center)
                advanceEventTime(650)
                up()
            }
            composeTestRule.waitForIdle()
            assertTrue("Stationary long-press should select alpha.txt", alphaItem.id in selectedIdsState.value)
            selectedIdsState.value = emptySet()
            composeTestRule.waitForIdle()

            // 2. Drag alpha.txt onto DestFolder in List View -> shows ghost pill + drop badge -> invokes move
            val targetCenterInRoot = composeTestRule.onNodeWithTag("file_row_DestFolder")
                .fetchSemanticsNode().boundsInRoot.center
            val alphaBoundsInRoot = composeTestRule.onNodeWithTag("file_row_alpha.txt")
                .fetchSemanticsNode().boundsInRoot
            val targetDelta = targetCenterInRoot - alphaBoundsInRoot.center

            composeTestRule.onNodeWithTag("file_row_alpha.txt").performTouchInput {
                down(center)
                advanceEventTime(650)
                moveBy(targetDelta)
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("drag_ghost_pill").assertIsDisplayed()
            composeTestRule.onNodeWithTag("drop_target_badge_DestFolder", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("Drop to move → DestFolder").assertIsDisplayed()

            composeTestRule.onNodeWithTag("file_row_alpha.txt").performTouchInput {
                up()
            }
            composeTestRule.waitForIdle()

            assertEquals(listOf(alphaItem.id), droppedItems.map { it.id })
            assertEquals(targetFolder.absolutePath, droppedTargetPath)
            assertEquals("DestFolder", droppedTargetLabel)

            // 3. Switch to Grid View and drag file onto parent Breadcrumb node
            droppedItems = emptyList()
            droppedTargetPath = null
            viewModeState.value = ViewMode.GRID
            composeTestRule.waitForIdle()

            val breadcrumbCenter = composeTestRule.onNodeWithTag("breadcrumb_node_0")
                .fetchSemanticsNode().boundsInRoot.center
            val gridCardCenter = composeTestRule.onNodeWithTag("file_grid_alpha.txt")
                .fetchSemanticsNode().boundsInRoot.center
            val toBreadcrumbDelta = breadcrumbCenter - gridCardCenter

            composeTestRule.onNodeWithTag("file_grid_alpha.txt").performTouchInput {
                down(center)
                advanceEventTime(650)
                moveBy(toBreadcrumbDelta)
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("drag_ghost_pill").assertIsDisplayed()
            composeTestRule.onNodeWithText("Drop to move → RootBox").assertIsDisplayed()

            composeTestRule.onNodeWithTag("file_grid_alpha.txt").performTouchInput {
                up()
            }
            composeTestRule.waitForIdle()

            assertEquals(listOf(alphaItem.id), droppedItems.map { it.id })
            assertEquals(rootDir.absolutePath, droppedTargetPath)
            assertEquals("RootBox", droppedTargetLabel)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun minimal_batch_bottom_bar_exposes_copy_cut_paste_rename_delete_and_clipboard_flow() {
        val folder = FileItem(
            id = "/box/Projects",
            name = "Projects",
            pathOrUri = "/box/Projects",
            isDirectory = true,
            sizeBytes = 0L,
            lastModifiedMillis = 1L,
            mimeType = "inode/directory",
            extension = "",
            isSafUri = false,
            parentPathOrUri = "/box"
        )
        val docA = FileItem(
            id = "/box/report.pdf",
            name = "report.pdf",
            pathOrUri = "/box/report.pdf",
            isDirectory = false,
            sizeBytes = 2048L,
            lastModifiedMillis = 1L,
            mimeType = "application/pdf",
            extension = "pdf",
            isSafUri = false,
            parentPathOrUri = "/box"
        )
        val docB = FileItem(
            id = "/box/notes.txt",
            name = "notes.txt",
            pathOrUri = "/box/notes.txt",
            isDirectory = false,
            sizeBytes = 1024L,
            lastModifiedMillis = 1L,
            mimeType = "text/plain",
            extension = "txt",
            isSafUri = false,
            parentPathOrUri = "/box"
        )
        val allItems = listOf(folder, docA, docB)

        val selectedIds = androidx.compose.runtime.mutableStateOf(setOf(folder.id, docA.id))
        val clipboardState = androidx.compose.runtime.mutableStateOf<com.example.data.ClipboardState?>(null)
        val recordedActions = mutableListOf<Pair<com.example.ui.registry.FileActionId, List<String>>>()
        var pasteCount = 0

        val dest = TabDestination.Directory(
            title = "Box",
            rootId = "root",
            currentPathOrUri = "/box",
            isSaf = false,
            breadcrumbs = listOf(com.example.data.BreadcrumbNode("Box", "/box", false))
        )

        composeTestRule.setContent {
            com.example.ui.theme.ArcFilesTheme {
                com.example.ui.screens.browser.BrowserScreen(
                    destination = dest,
                    directoryState = DirectoryLoadState.Loaded(items = allItems),
                    categoryItems = emptyList(),
                    searchResults = emptyList(),
                    selectedIds = selectedIds.value,
                    clipboard = clipboardState.value,
                    viewMode = ViewMode.LIST,
                    sortField = SortField.NAME,
                    sortAscending = true,
                    monochromeBinaryIcons = false,
                    onBack = {},
                    onBreadcrumbClick = {},
                    onSearchQueryChange = {},
                    onToggleViewMode = {},
                    onSortOrderChange = { _, _ -> },
                    onToggleSortOrder = {},
                    onGroupByChange = {},
                    onNewFolderClick = {},
                    onNewNoteClick = {},
                    onRequestAllFilesAccess = {},
                    onMountFolderClick = {},
                    onImportMediaClick = {},
                    onImportDocsClick = {},
                    onPasteClick = { pasteCount += 1 },
                    onClearClipboard = { clipboardState.value = null },
                    onFileClick = {},
                    onToggleSelect = {},
                    onSelectAll = { items -> selectedIds.value = items.map { it.id }.toSet() },
                    onClearSelection = { selectedIds.value = emptySet() },
                    onFileAction = { actionId, items ->
                        recordedActions.add(actionId to items.map { it.id })
                        when (actionId) {
                            com.example.ui.registry.FileActionId.COPY -> {
                                clipboardState.value = com.example.data.ClipboardState(items, com.example.data.ClipboardOp.COPY)
                                selectedIds.value = emptySet()
                            }
                            com.example.ui.registry.FileActionId.MOVE -> {
                                clipboardState.value = com.example.data.ClipboardState(items, com.example.data.ClipboardOp.MOVE)
                                selectedIds.value = emptySet()
                            }
                            else -> Unit
                        }
                    }
                )
            }
        }

        // 1. Multi-selection reveals the minimal bottom editing bar with all 5 batch tools
        composeTestRule.onNodeWithTag("multi_edit_bottom_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 selected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Copy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cut").assertIsDisplayed()
        composeTestRule.onNodeWithText("Paste").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rename").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").assertIsDisplayed()

        // 2. Rename (>1 items) dispatches BATCH_RENAME, Delete dispatches DELETE for batch items
        composeTestRule.onNodeWithTag("action_batch_rename").performClick()
        composeTestRule.onNodeWithTag("action_delete").performClick()
        assertEquals(com.example.ui.registry.FileActionId.BATCH_RENAME, recordedActions[0].first)
        assertEquals(listOf(folder.id, docA.id), recordedActions[0].second)
        assertEquals(com.example.ui.registry.FileActionId.DELETE, recordedActions[1].first)
        assertEquals(listOf(folder.id, docA.id), recordedActions[1].second)

        // 3. Copy stages clipboard and cleanly transitions bottom bar to Paste-ready state
        composeTestRule.onNodeWithTag("action_copy").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_clipboard_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Copy · 2 items").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clipboard_paste_button").performClick()
        assertEquals(1, pasteCount)

        // 4. Selecting items while clipboard is populated enables Paste inside the 5-tool bar too
        selectedIds.value = setOf(docB.id)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("action_paste").performClick()
        assertEquals(2, pasteCount)

        // Single-item Rename dispatches RENAME, and Cut dispatches MOVE
        composeTestRule.onNodeWithTag("action_rename").performClick()
        composeTestRule.onNodeWithTag("action_move").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.ui.registry.FileActionId.RENAME, recordedActions[3].first)
        assertEquals(com.example.ui.registry.FileActionId.MOVE, recordedActions[4].first)
        composeTestRule.onNodeWithText("Cut · 1 item").assertIsDisplayed()
    }

    @Test
    fun library_section_maps_each_tile_to_separate_internal_storage_folder_and_loads_its_data() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = FileExplorerRepository(app)

        val dlDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
        val picDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).apply { mkdirs() }
        val movDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES).apply { mkdirs() }
        val docDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS).apply { mkdirs() }
        val musDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC).apply { mkdirs() }

        val createdFiles = mutableListOf<java.io.File>()
        try {
            // Put distinct files (including an image in Download!) into each separate public folder
            createdFiles += java.io.File(dlDir, "downloaded_photo.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            createdFiles += java.io.File(dlDir, "installer.apk").apply { writeBytes(byteArrayOf(4, 5)) }
            createdFiles += java.io.File(picDir, "wallpaper.jpg").apply { writeBytes(byteArrayOf(6, 7, 8, 9)) }
            createdFiles += java.io.File(movDir, "clip.mp4").apply { writeBytes(byteArrayOf(10, 11)) }
            createdFiles += java.io.File(docDir, "resume.pdf").apply { writeBytes(byteArrayOf(12, 13)) }
            createdFiles += java.io.File(musDir, "track.mp3").apply { writeBytes(byteArrayOf(14, 15)) }

            val summaries = kotlinx.coroutines.runBlocking { repo.loadLibraryFolderSummaries(showHidden = false) }
            assertEquals(2, summaries[ExplorerCategory.DOWNLOADS]?.itemCount)
            assertEquals(1, summaries[ExplorerCategory.PHOTOS]?.itemCount)
            assertEquals(1, summaries[ExplorerCategory.VIDEOS]?.itemCount)
            assertEquals(1, summaries[ExplorerCategory.DOCUMENTS]?.itemCount)
            assertEquals(1, summaries[ExplorerCategory.MUSIC]?.itemCount)

            val vm = com.example.ui.ExplorerViewModel(app)
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                (vm.uiState.value.librarySummaries[ExplorerCategory.DOWNLOADS]?.itemCount ?: 0) >= 2 &&
                    vm.uiState.value.allIndexedFiles.any { it.name == "wallpaper.jpg" }
            }

            // Library sections now open dedicated TabDestination.Library views across all storage
            val expectedSections = listOf(
                ExplorerCategory.PHOTOS to com.example.data.LibrarySection.IMAGES,
                ExplorerCategory.VIDEOS to com.example.data.LibrarySection.MOVIES,
                ExplorerCategory.DOCUMENTS to com.example.data.LibrarySection.DOCUMENTS,
                ExplorerCategory.MUSIC to com.example.data.LibrarySection.MUSIC,
                ExplorerCategory.DOWNLOADS to com.example.data.LibrarySection.DOWNLOADS
            )
            expectedSections.forEach { (cat, section) ->
                vm.openLibraryCategory(cat)
                val activeDest = vm.uiState.value.activeTab?.destination as? TabDestination.Library
                assertEquals(section, activeDest?.section)
            }

            // Images Library aggregates images across both Pictures and Download folders
            val galleryImages = vm.getLibrarySectionItems(com.example.data.LibrarySection.IMAGES)
            assertTrue(galleryImages.any { it.name == "wallpaper.jpg" })
            assertTrue(galleryImages.any { it.name == "downloaded_photo.png" })

            // Sidebar public folder shortcut still opens physical TabDestination.Directory
            vm.openQuickPublicFolder(android.os.Environment.DIRECTORY_DOWNLOADS, "Downloads")
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                val dest = vm.uiState.value.activeTab?.destination as? TabDestination.Directory
                val loaded = vm.uiState.value.directoryState as? DirectoryLoadState.Loaded
                dest?.currentPathOrUri == dlDir.absolutePath &&
                    loaded?.items?.any { it.name == "downloaded_photo.png" } == true
            }
            val dlDest = vm.uiState.value.activeTab?.destination as TabDestination.Directory
            assertEquals(dlDir.absolutePath, dlDest.currentPathOrUri)

            // Virtual category (ARCHIVES) still opens as a Category tab
            vm.openLibraryCategory(ExplorerCategory.ARCHIVES)
            assertTrue(vm.uiState.value.activeTab?.destination is TabDestination.Category)
        } finally {
            createdFiles.forEach { it.delete() }
        }
    }

    @Test
    fun hidden_system_files_starting_with_dot_percent_dollar_render_faded_while_normal_stay_crisp() {
        val dotFolder = FileItem("h1", ".thumbnails", "/box/.thumbnails", true, 0L, 1L, "vnd.android.document/directory", "", false, "/box")
        val dotFile = FileItem("h2", ".nomedia", "/box/.nomedia", false, 0L, 1L, "application/octet-stream", "nomedia", false, "/box")
        val percentFolder = FileItem("h3", "%TEMP%", "/box/%TEMP%", true, 0L, 1L, "vnd.android.document/directory", "", false, "/box")
        val dollarFolder = FileItem("h4", "\$RECYCLE.BIN", "/box/\$RECYCLE.BIN", true, 0L, 1L, "vnd.android.document/directory", "", false, "/box")
        val normalFolder = FileItem("n1", "Downloads", "/box/Downloads", true, 0L, 1L, "vnd.android.document/directory", "", false, "/box")
        val normalFile = FileItem("n2", "my%report.pdf", "/box/my%report.pdf", false, 2048L, 1L, "application/pdf", "pdf", false, "/box")

        assertTrue(dotFolder.isHiddenOrSystem)
        assertTrue(dotFile.isHiddenOrSystem)
        assertTrue(percentFolder.isHiddenOrSystem)
        assertTrue(dollarFolder.isHiddenOrSystem)
        assertFalse(normalFolder.isHiddenOrSystem)
        assertFalse(normalFile.isHiddenOrSystem)

        val items = listOf(dotFolder, dotFile, percentFolder, dollarFolder, normalFolder, normalFile)
        composeTestRule.setContent {
            com.example.ui.theme.ArcFilesTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    items.forEach { entry ->
                        com.example.ui.screens.browser.FileListRow(
                            item = entry,
                            isSelected = false,
                            selectionMode = false,
                            monochrome = false,
                            onClick = {},
                            onLongClick = {},
                            onAction = { _, _ -> }
                        )
                    }
                    com.example.ui.screens.browser.FileGridCard(
                        item = dollarFolder,
                        isSelected = false,
                        selectionMode = false,
                        monochrome = false,
                        onClick = {},
                        onLongClick = {}
                    )
                    com.example.ui.screens.browser.FileGridCard(
                        item = normalFile,
                        isSelected = false,
                        selectionMode = false,
                        monochrome = false,
                        onClick = {},
                        onLongClick = {}
                    )
                    com.example.ui.screens.home.RecentFileRow(
                        item = percentFolder,
                        monochrome = false,
                        onClick = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val hiddenMatcher = SemanticsMatcher.expectValue(com.example.ui.screens.browser.HiddenSystemItemSemanticsKey, true)
        val normalMatcher = SemanticsMatcher.expectValue(com.example.ui.screens.browser.HiddenSystemItemSemanticsKey, false)

        composeTestRule.onNodeWithTag("file_row_.thumbnails").performScrollTo().assertIsDisplayed().assert(hiddenMatcher)
        composeTestRule.onNodeWithTag("file_row_.nomedia").performScrollTo().assertIsDisplayed().assert(hiddenMatcher)
        composeTestRule.onNodeWithTag("file_row_%TEMP%").performScrollTo().assertIsDisplayed().assert(hiddenMatcher)
        composeTestRule.onNodeWithTag("file_row_\$RECYCLE.BIN").performScrollTo().assertIsDisplayed().assert(hiddenMatcher)
        composeTestRule.onNodeWithTag("file_row_Downloads").performScrollTo().assertIsDisplayed().assert(normalMatcher)
        composeTestRule.onNodeWithTag("file_row_my%report.pdf").performScrollTo().assertIsDisplayed().assert(normalMatcher)

        composeTestRule.onNodeWithTag("file_grid_\$RECYCLE.BIN").performScrollTo().assertIsDisplayed().assert(hiddenMatcher)
        composeTestRule.onNodeWithTag("file_grid_my%report.pdf").performScrollTo().assertIsDisplayed().assert(normalMatcher)
        composeTestRule.onNodeWithTag("recent_file_%TEMP%").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun default_tabs_seed_internal_always_and_external_when_available() = runTest {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()

        // 1. Standard device with no external volume: Home + confirmed Internal Storage tab
        val vmNoExternal = com.example.ui.ExplorerViewModel(app)
        composeTestRule.waitUntil(5000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vmNoExternal.uiState.value.tabs.any { it.id == "tab_default_internal" }
        }
        val baseTabs = vmNoExternal.uiState.value.tabs
        assertTrue(baseTabs.any { it.id == "tab_home" })
        assertTrue(baseTabs.any { it.id == "tab_default_internal" && it.title == "Internal" })
        assertFalse(baseTabs.any { it.id == "tab_default_external" })

        // 2. When a mounted external removable volume exists, default External tab is also seeded
        val sdDir = java.io.File(app.cacheDir, "FakeSdCard_${System.nanoTime()}").apply { mkdirs() }
        try {
            val parcel = android.os.Parcel.obtain()
            val volume = try {
                // Create a realistic non-primary mounted StorageVolume via Robolectric shadow
                val ctor = android.os.storage.StorageVolume::class.java.getDeclaredConstructor(
                    String::class.java,
                    java.io.File::class.java,
                    java.io.File::class.java,
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                    android.os.UserHandle::class.java,
                    java.util.UUID::class.java,
                    String::class.java,
                    String::class.java
                )
                ctor.isAccessible = true
                ctor.newInstance(
                    "sd-uuid",
                    sdDir,
                    sdDir,
                    "SD Card",
                    false, // isPrimary
                    true,  // isRemovable
                    false, // isEmulated
                    false, // allowMassStorage
                    false, // maxFileSize unused flag or similar; boolean order checked below
                    0L,
                    android.os.Process.myUserHandle(),
                    null,
                    "8688-4398",
                    android.os.Environment.MEDIA_MOUNTED
                )
            } catch (_: Throwable) {
                null
            } finally {
                parcel.recycle()
            }

            if (volume != null) {
                val storageManager = app.getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager
                org.robolectric.Shadows.shadowOf(storageManager).addStorageVolume(volume)
                val vmWithExternal = com.example.ui.ExplorerViewModel(app)
                composeTestRule.waitUntil(5000) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    vmWithExternal.uiState.value.tabs.any { it.id == "tab_default_external" }
                }
                val extTabs = vmWithExternal.uiState.value.tabs
                assertTrue(extTabs.any { it.id == "tab_default_internal" })
                assertTrue(extTabs.any { it.id == "tab_default_external" && it.title == "SD Card" })
            } else {
                // Fallback verification via mounted SAF external drive entry + fake provider
                val authority = "com.test.ext.saf"
                val fakeProvider = object : android.content.ContentProvider() {
                    override fun onCreate() = true
                    override fun query(u: android.net.Uri, p: Array<out String>?, s: String?, sa: Array<out String>?, so: String?): android.database.Cursor {
                        return android.database.MatrixCursor(arrayOf(
                            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                        ))
                    }
                    override fun getType(u: android.net.Uri): String? = null
                    override fun insert(u: android.net.Uri, v: android.content.ContentValues?): android.net.Uri? = null
                    override fun delete(u: android.net.Uri, s: String?, sa: Array<out String>?) = 0
                    override fun update(u: android.net.Uri, v: android.content.ContentValues?, s: String?, sa: Array<out String>?) = 0
                }
                org.robolectric.Robolectric.buildContentProvider(fakeProvider::class.java).create(authority)
                val extUri = android.provider.DocumentsContract.buildTreeDocumentUri(authority, "8688-4398:")
                app.contentResolver.takePersistableUriPermission(extUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val prefsRepo = com.example.data.PreferencesRepository(app)
                prefsRepo.addMountedSaf(extUri.toString(), "SD Card")
                try {
                    val vmWithSafExt = com.example.ui.ExplorerViewModel(app)
                    composeTestRule.waitUntil(5000) {
                        org.robolectric.shadows.ShadowLooper.idleMainLooper()
                        vmWithSafExt.uiState.value.tabs.any { it.id == "tab_default_external" }
                    }
                    val extTabs = vmWithSafExt.uiState.value.tabs
                    assertTrue(extTabs.any { it.id == "tab_default_internal" })
                    assertTrue(extTabs.any { it.id == "tab_default_external" && it.title == "SD Card" })
                } finally {
                    prefsRepo.removeMountedSaf(extUri.toString())
                }
            }
        } finally {
            sdDir.deleteRecursively()
        }
    }

    @Test
    fun horizontal_pager_tab_swipe_and_edge_only_sidebar_gesture_work_without_conflict() {
        composeTestRule.setContent {
            ArcFilesRoot()
        }

        // Wait until default Internal tab is seeded alongside Home
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithTag("tab_chip_tab_default_internal").fetchSemanticsNodes().isNotEmpty()
        }

        // 1. Swipe left on pager -> smoothly switches to Internal Storage tab
        composeTestRule.onNodeWithTag("tab_horizontal_pager").performTouchInput {
            swipeLeft(startX = width * 0.85f, endX = width * 0.15f)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("browser_breadcrumb_bar").assertIsDisplayed()

        // 2. Swipe right from center -> smoothly returns to Home tab WITHOUT opening sidebar drawer
        composeTestRule.onNodeWithTag("tab_horizontal_pager").performTouchInput {
            swipeRight(startX = width * 0.25f, endX = width * 0.85f)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("arc_sidebar_drawer").assertIsNotDisplayed()

        // 2b. Looping wrap backward from Home (tab 0) -> wraps to last tab (Internal)
        composeTestRule.onNodeWithTag("tab_horizontal_pager").performTouchInput {
            swipeRight(startX = width * 0.25f, endX = width * 0.85f)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("browser_breadcrumb_bar").assertIsDisplayed()

        // 2c. Looping wrap forward from last tab -> wraps smoothly back to Home tab
        composeTestRule.onNodeWithTag("tab_horizontal_pager").performTouchInput {
            swipeLeft(startX = width * 0.85f, endX = width * 0.15f)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()

        // 3. Swipe right from the dedicated far-left edge strip -> opens sidebar drawer cleanly
        composeTestRule.onNodeWithTag("sidebar_edge_swipe_strip").performTouchInput {
            swipeRight(startX = 2f, endX = 220f)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("arc_sidebar_drawer").assertIsDisplayed()
    }

    @Test
    fun open_file_select_app_dialog_supports_this_time_only_always_and_open_as_reset() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager
        val shadowPm = org.robolectric.Shadows.shadowOf(pm)

        val repo = FileExplorerRepository(context)
        val pdfFile = java.io.File(repo.getWorkspaceRoot(), "Untitled_${System.nanoTime()}.pdf").apply {
            writeText("%PDF-1.4")
        }
        val pdfItem = FileItem(
            id = pdfFile.absolutePath,
            name = pdfFile.name,
            pathOrUri = pdfFile.absolutePath,
            isDirectory = false,
            sizeBytes = pdfFile.length(),
            lastModifiedMillis = pdfFile.lastModified(),
            mimeType = "application/pdf",
            extension = "pdf",
            isSafUri = false,
            parentPathOrUri = pdfFile.parent ?: ""
        )

        val queryIntent = repo.buildViewIntent(pdfItem)

        fun registerPdfApp(pkg: String, cls: String, label: String): android.content.ComponentName {
            val cn = android.content.ComponentName(pkg, cls)
            val appInfo = android.content.pm.ApplicationInfo().apply {
                packageName = pkg
                nonLocalizedLabel = label
                enabled = true
                flags = android.content.pm.ApplicationInfo.FLAG_INSTALLED
            }
            val actInfo = android.content.pm.ActivityInfo().apply {
                packageName = pkg
                name = cls
                exported = true
                enabled = true
                nonLocalizedLabel = label
                applicationInfo = appInfo
            }
            val resolveInfo = android.content.pm.ResolveInfo().apply {
                activityInfo = actInfo
                nonLocalizedLabel = label
            }
            shadowPm.addResolveInfoForIntent(queryIntent, resolveInfo)
            shadowPm.addResolveInfoForIntent(
                repo.buildViewIntent(pdfItem, packageName = pkg, activityName = cls),
                resolveInfo
            )
            return cn
        }

        val wpsComponent = registerPdfApp("com.fake.wps", "com.fake.wps.WpsPdfActivity", "WPS Office")
        val driveComponent = registerPdfApp("com.fake.drive", "com.fake.drive.DrivePdfActivity", "Drive PDF Viewer")

        try {
            // Action registry exposes "Open As" for single files in the 3-dot More menu
            val openAsSpec = com.example.ui.registry.FileActionRegistry.visibleFor(listOf(pdfItem))
                .firstOrNull { it.id == com.example.ui.registry.FileActionId.OPEN_WITH }
            assertEquals("Open As", openAsSpec?.label)

            val viewModel = com.example.ui.ExplorerViewModel(context as android.app.Application)
            val launchedIntents = mutableListOf<android.content.Intent>()

            val prefsRepo = com.example.data.PreferencesRepository(context)
            prefsRepo.clearAllDefaultOpenApps()

            // Wait for DataStore preferences initialization
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.uiState.value.tabs.any { it.id == "tab_default_internal" } &&
                    viewModel.uiState.value.prefs.parsedDefaultOpenApps().isEmpty()
            }

            composeTestRule.setContent {
                val state = viewModel.uiState.collectAsState().value
                com.example.ui.theme.ArcFilesTheme {
                    state.openWithRequest?.let { req ->
                        com.example.ui.components.dialogs.SelectOpenAppDialog(
                            request = req,
                            onDismiss = viewModel::dismissOpenWith,
                            onConfirmApp = { app, always ->
                                viewModel.confirmOpenWithApp(req, app, always) { launchedIntents += it }
                            },
                            onTryAllApps = {}
                        )
                    }
                }
            }

            // 1. First tap on PDF opens "Select" dialog with both apps; pick Drive PDF Viewer + "This Time Only"
            composeTestRule.runOnUiThread {
                viewModel.openFile(pdfItem) { launchedIntents += it }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("select_open_app_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithText("Select").assertIsDisplayed()
            composeTestRule.onNodeWithText("WPS Office").assertIsDisplayed()
            composeTestRule.onNodeWithText("Drive PDF Viewer").assertIsDisplayed()

            composeTestRule.onNodeWithTag("open_app_option_com.fake.drive").performClick()
            composeTestRule.onNodeWithTag("open_app_once_button").performClick()
            composeTestRule.waitForIdle()

            assertEquals(1, launchedIntents.size)
            assertEquals(driveComponent, launchedIntents.last().component)
            assertTrue(viewModel.uiState.value.prefs.parsedDefaultOpenApps().isEmpty())

            // 2. Second tap still opens Select dialog; pick WPS Office + "Always"
            composeTestRule.runOnUiThread {
                viewModel.openFile(pdfItem) { launchedIntents += it }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("select_open_app_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_app_option_com.fake.wps").performClick()
            composeTestRule.onNodeWithTag("open_app_always_button").performClick()

            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.uiState.value.prefs.parsedDefaultOpenApps()[com.example.data.defaultOpenAppKey(pdfItem)]?.packageName == "com.fake.wps"
            }
            assertEquals(2, launchedIntents.size)
            assertEquals(wpsComponent, launchedIntents.last().component)

            // 3. Subsequent openFile bypasses popup and launches WPS Office directly!
            composeTestRule.runOnUiThread {
                viewModel.openFile(pdfItem) { launchedIntents += it }
            }
            composeTestRule.waitForIdle()
            assertEquals(0, composeTestRule.onAllNodesWithTag("select_open_app_dialog").fetchSemanticsNodes().size)
            assertEquals(3, launchedIntents.size)
            assertEquals(wpsComponent, launchedIntents.last().component)

            // 4. Select file -> 3-dot More -> Open As brings popup back; picking Drive PDF Viewer + "This Time Only" resets Always
            composeTestRule.runOnUiThread {
                viewModel.requestOpenWith(pdfItem, fromOpenAsAction = true)
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("select_open_app_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_app_option_com.fake.drive").performClick()
            composeTestRule.onNodeWithTag("open_app_once_button").performClick()

            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                com.example.data.defaultOpenAppKey(pdfItem) !in viewModel.uiState.value.prefs.parsedDefaultOpenApps()
            }
            assertEquals(4, launchedIntents.size)
            assertEquals(driveComponent, launchedIntents.last().component)

            // 5. Normal open after reset shows Select dialog again
            composeTestRule.runOnUiThread {
                viewModel.openFile(pdfItem) { launchedIntents += it }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("select_open_app_dialog").assertIsDisplayed()
        } finally {
            pdfFile.delete()
        }
    }

    @Test
    fun ios_style_tags_system_supports_presets_custom_tags_batch_toggle_sidebar_and_tag_browser() = runTest {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = FileExplorerRepository(app)
        val workDir = java.io.File(repo.getWorkspaceRoot(), "TagsTest_${System.nanoTime()}").apply { mkdirs() }
        val fileA = java.io.File(workDir, "invoice.pdf").apply { writeText("pdf") }
        val fileB = java.io.File(workDir, "notes.txt").apply { writeText("txt") }
        val itemA = FileItem(
            id = fileA.absolutePath,
            name = fileA.name,
            pathOrUri = fileA.absolutePath,
            isDirectory = false,
            sizeBytes = fileA.length(),
            lastModifiedMillis = fileA.lastModified(),
            mimeType = "application/pdf",
            extension = "pdf",
            isSafUri = false,
            parentPathOrUri = workDir.absolutePath
        )
        val itemB = FileItem(
            id = fileB.absolutePath,
            name = fileB.name,
            pathOrUri = fileB.absolutePath,
            isDirectory = false,
            sizeBytes = fileB.length(),
            lastModifiedMillis = fileB.lastModified(),
            mimeType = "text/plain",
            extension = "txt",
            isSafUri = false,
            parentPathOrUri = workDir.absolutePath
        )

        try {
            val viewModel = com.example.ui.ExplorerViewModel(app)
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.uiState.value.allTags.size >= 7
            }

            // 1. Default 7 iOS tags are available
            val defaultNames = viewModel.uiState.value.allTags.map { it.name }
            assertTrue(defaultNames.containsAll(listOf("Red", "Orange", "Yellow", "Green", "Blue", "Purple", "Gray")))

            // Action registry exposes "Tags" for both single and batch selection
            assertTrue(com.example.ui.registry.FileActionRegistry.visibleFor(listOf(itemA)).any { it.id == com.example.ui.registry.FileActionId.TAGS })
            assertTrue(com.example.ui.registry.FileActionRegistry.visibleFor(listOf(itemA, itemB)).any { it.id == com.example.ui.registry.FileActionId.TAGS })

            var openedTagFromDrawer: String? = null
            composeTestRule.setContent {
                val state = viewModel.uiState.collectAsState().value
                val normA = com.example.data.normalizeTagPath(itemA.pathOrUri, itemA.isSafUri)
                val normB = com.example.data.normalizeTagPath(itemB.pathOrUri, itemB.isSafUri)
                com.example.ui.theme.ArcFilesTheme {
                    androidx.compose.foundation.layout.Column {
                        com.example.ui.screens.browser.FileListRow(
                            item = itemA,
                            isSelected = false,
                            selectionMode = false,
                            monochrome = false,
                            itemTags = state.itemTagsByPath[normA].orEmpty(),
                            onClick = {},
                            onLongClick = {},
                            onAction = { _, _ -> }
                        )
                        com.example.ui.screens.browser.FileListRow(
                            item = itemB,
                            isSelected = false,
                            selectionMode = false,
                            monochrome = false,
                            itemTags = state.itemTagsByPath[normB].orEmpty(),
                            onClick = {},
                            onLongClick = {},
                            onAction = { _, _ -> }
                        )
                        val activeDest = state.activeTab?.destination
                        if (activeDest is TabDestination.Tag) {
                            com.example.ui.components.common.TagHeaderBar(
                                activeTag = state.allTags.firstOrNull { it.id == activeDest.tagId },
                                allTags = state.allTags,
                                itemCount = viewModel.getTagFiles(activeDest.tagId).size,
                                onSelectTag = viewModel::openTagTab
                            )
                        }
                        com.example.ui.components.ArcSidebarDrawer(
                            drives = state.drives,
                            bookmarks = emptyList(),
                            tags = state.allTags,
                            taggedCountsByTagId = state.taggedFilesByTagId.mapValues { it.value.size },
                            indexedFileCount = state.allIndexedFiles.size,
                            trashCount = state.trashEntries.size,
                            hasAllFilesAccess = true,
                            showHiddenFiles = state.prefs.showHiddenFiles,
                            themePreference = state.prefs.themePreference,
                            onCloseDrawer = {},
                            onOpenDrive = {},
                            onOpenPublicFolder = { _, _ -> },
                            onOpenBookmark = {},
                            onOpenTag = { openedTagFromDrawer = it; viewModel.openTagTab(it) },
                            onOpenCategory = {},
                            onOpenAnalyzer = {},
                            onOpenTrash = {},
                            onMountFolder = {},
                            onImportMedia = {},
                            onNewNote = {},
                            onToggleHiddenFiles = {},
                            onRequestAllFilesAccess = {},
                            onCycleTheme = {},
                            onOpenSettings = {}
                        )
                        state.tagPickerTargets?.let { targets ->
                            com.example.ui.components.common.TagsPickerSheet(
                                targets = targets,
                                allTags = state.allTags,
                                itemTagsByPath = state.itemTagsByPath,
                                taggedCountsByTagId = state.taggedFilesByTagId.mapValues { it.value.size },
                                onToggleTag = { tagId -> viewModel.toggleTagOnTargets(tagId, targets) },
                                onCreateTag = { name, color -> viewModel.createCustomTag(name, color, targets) },
                                onDeleteTag = viewModel::deleteCustomTag,
                                onDismiss = viewModel::dismissTagPicker
                            )
                        }
                    }
                }
            }

            // 2. Open Tag picker on batch [itemA, itemB], toggle Red, and create a custom tag "Important"
            composeTestRule.runOnUiThread {
                viewModel.openTagPicker(listOf(itemA, itemB))
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("tag_picker_sheet").assertIsDisplayed()
            composeTestRule.onNodeWithTag("tag_row_tag_red").performClick()

            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.getTagFiles("tag_red").size == 2
            }

            composeTestRule.onNodeWithTag("add_custom_tag_toggle").performClick()
            composeTestRule.onNodeWithTag("custom_tag_name_input").performTextInput("Important")
            composeTestRule.onNodeWithTag("custom_tag_create_button").performClick()

            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.uiState.value.allTags.any { it.name == "Important" }
            }
            val customTag = viewModel.uiState.value.allTags.first { it.name == "Important" }
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.getTagFiles(customTag.id).size == 2
            }

            // Dismiss picker and verify both items render 2 tag dots via ItemTagsCountSemanticsKey
            composeTestRule.onNodeWithTag("tag_picker_done_button").performClick()
            composeTestRule.waitForIdle()
            val dotNodes = composeTestRule.onAllNodes(
                androidx.compose.ui.test.SemanticsMatcher.expectValue(
                    com.example.ui.components.common.ItemTagsCountSemanticsKey,
                    2
                )
            ).fetchSemanticsNodes()
            assertEquals(2, dotNodes.size)

            // 3. Partial tag on itemA alone with Blue; then batch toggle applies Blue to all
            composeTestRule.runOnUiThread {
                viewModel.toggleTagOnTargets("tag_blue", listOf(itemA))
            }
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.getTagFiles("tag_blue").size == 1
            }
            composeTestRule.runOnUiThread {
                viewModel.toggleTagOnTargets("tag_blue", listOf(itemA, itemB))
            }
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.getTagFiles("tag_blue").size == 2
            }

            // 4. Verify Sidebar Drawer Tags group is expanded and open "Red" tag tab
            val drawerList = composeTestRule.onNodeWithTag("arc_sidebar_list")
            drawerList.performScrollToNode(hasTestTag("drawer_group_tags"))
            composeTestRule.onNodeWithTag("drawer_group_tags").assertIsDisplayed()
            drawerList.performScrollToNode(hasTestTag("drawer_tag_tag_red"))
            composeTestRule.onNodeWithTag("drawer_tag_tag_red").performClick()
            composeTestRule.waitForIdle()

            assertEquals("tag_red", openedTagFromDrawer)
            assertEquals(TabDestination.Tag("tag_red"), viewModel.uiState.value.activeTab?.destination)
            composeTestRule.onNodeWithTag("tag_header_bar").assertIsDisplayed()
            assertEquals(listOf("invoice.pdf", "notes.txt").sorted(), viewModel.getTagFiles("tag_red").map { it.name }.sorted())

            // 5. Renaming a tagged file preserves its tags under the new path
            composeTestRule.runOnUiThread {
                viewModel.renameFile(itemA, "invoice_2026.pdf")
            }
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.getTagFiles("tag_red").any { it.name == "invoice_2026.pdf" }
            }
            assertTrue(viewModel.getTagFiles("tag_red").none { it.name == "invoice.pdf" })

            // 6. Deleting custom tag removes it cleanly
            composeTestRule.runOnUiThread {
                viewModel.deleteCustomTag(customTag.id)
            }
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.uiState.value.allTags.none { it.id == customTag.id }
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun empty_folder_displays_modern_clean_ui_without_create_buttons() {
        val emptyDirDest = TabDestination.Directory(
            rootId = "drive_internal",
            title = "EmptyTest",
            currentPathOrUri = "/storage/emulated/0/EmptyTest",
            isSaf = false,
            breadcrumbs = listOf(
                com.example.data.BreadcrumbNode("Internal", "/storage/emulated/0", false),
                com.example.data.BreadcrumbNode("EmptyTest", "/storage/emulated/0/EmptyTest", false)
            )
        )
        composeTestRule.setContent {
            com.example.ui.theme.ArcFilesTheme {
                com.example.ui.screens.browser.BrowserScreen(
                    destination = emptyDirDest,
                    directoryState = com.example.data.DirectoryLoadState.Loaded(emptyList(), isScopedStorageLimited = false),
                    categoryItems = emptyList(),
                    searchResults = emptyList(),
                    selectedIds = emptySet(),
                    clipboard = null,
                    viewMode = com.example.data.ViewMode.LIST,
                    sortField = SortField.NAME,
                    sortAscending = true,
                    monochromeBinaryIcons = false,
                    onBack = {},
                    onBreadcrumbClick = {},
                    onSearchQueryChange = {},
                    onToggleViewMode = {},
                    onSortOrderChange = { _, _ -> },
                    onToggleSortOrder = {},
                    onGroupByChange = {},
                    onNewFolderClick = {},
                    onNewNoteClick = {},
                    onRequestAllFilesAccess = {},
                    onMountFolderClick = {},
                    onImportMediaClick = {},
                    onImportDocsClick = {},
                    onPasteClick = {},
                    onClearClipboard = {},
                    onFileClick = {},
                    onToggleSelect = {},
                    onSelectAll = {},
                    onClearSelection = {},
                    onFileAction = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_browser_state").assertIsDisplayed()
        composeTestRule.onNodeWithText("This folder is empty").assertIsDisplayed()
        composeTestRule.onNodeWithText("Files and folders added here will appear automatically.").assertIsDisplayed()
        composeTestRule.onNodeWithText("New Folder").assertDoesNotExist()
        composeTestRule.onNodeWithText("Import File").assertDoesNotExist()
    }

    @Test
    fun zip_creation_encryption_inspection_extraction_and_zip_slip_work() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val workDir = java.io.File(repo.getWorkspaceRoot(), "ZipFullTest_${System.nanoTime()}").apply { mkdirs() }
        try {
            val nestedDir = java.io.File(workDir, "docs").apply { mkdirs() }
            val readme = java.io.File(nestedDir, "readme.txt").apply { writeText("Hello Arc Files ZIP!") }
            val topNote = java.io.File(workDir, "notes.txt").apply { writeText("Top-level content 12345") }
            val extractTarget = java.io.File(workDir, "out_zip").apply { mkdirs() }
            val storeExtractTarget = java.io.File(workDir, "out_store").apply { mkdirs() }
            val encExtractTarget = java.io.File(workDir, "out_enc").apply { mkdirs() }

            val srcItems = listOf(
                FileItem(nestedDir.absolutePath, nestedDir.name, nestedDir.absolutePath, true, 0L, 100L, "vnd.android.document/directory", "", false, workDir.absolutePath),
                FileItem(topNote.absolutePath, topNote.name, topNote.absolutePath, false, topNote.length(), 100L, "text/plain", "txt", false, workDir.absolutePath)
            )

            // 1. Create standard NORMAL ZIP & inspect + extract to subfolder
            val createdRes = repo.createArchive(
                items = srcItems,
                targetParentPathOrUri = workDir.absolutePath,
                isTargetSaf = false,
                archiveBaseName = "bundle",
                format = com.example.data.ArchiveFormat.ZIP,
                compressionLevel = com.example.data.ArchiveCompressionLevel.NORMAL
            )
            assertTrue("Expected ZIP creation success: ${createdRes.exceptionOrNull()}", createdRes.isSuccess)
            val zipItem = createdRes.getOrThrow()
            assertEquals("bundle.zip", zipItem.name)
            assertTrue(zipItem.isExtractableArchive)

            val inspected = repo.inspectArchive(zipItem).getOrThrow()
            assertEquals(com.example.data.ArchiveFormat.ZIP, inspected.format)
            assertFalse(inspected.isEncrypted)
            assertTrue(inspected.entries.any { it.name.endsWith("docs/readme.txt") })
            assertTrue(inspected.entries.any { it.name == "notes.txt" })

            val extractedCount = repo.extractArchiveToDirectory(
                item = zipItem,
                targetParentPathOrUri = extractTarget.absolutePath,
                isTargetSaf = false,
                createSubfolder = true
            ).getOrThrow()
            assertEquals(2, extractedCount)
            assertEquals("Hello Arc Files ZIP!", java.io.File(extractTarget, "bundle/docs/readme.txt").readText())
            assertEquals("Top-level content 12345", java.io.File(extractTarget, "bundle/notes.txt").readText())

            // 2. Create STORE ZIP + Extract Here (createSubfolder = false)
            val storeZip = repo.createArchive(
                items = listOf(srcItems[1]),
                targetParentPathOrUri = workDir.absolutePath,
                isTargetSaf = false,
                archiveBaseName = "stored",
                format = com.example.data.ArchiveFormat.ZIP,
                compressionLevel = com.example.data.ArchiveCompressionLevel.STORE
            ).getOrThrow()
            repo.extractArchiveToDirectory(
                item = storeZip,
                targetParentPathOrUri = storeExtractTarget.absolutePath,
                isTargetSaf = false,
                createSubfolder = false
            ).getOrThrow()
            assertEquals("Top-level content 12345", java.io.File(storeExtractTarget, "notes.txt").readText())

            // 3. Create AES-256 Encrypted ZIP -> fails wrong/missing password, succeeds with right password
            val encZip = repo.createArchive(
                items = listOf(srcItems[1]),
                targetParentPathOrUri = workDir.absolutePath,
                isTargetSaf = false,
                archiveBaseName = "secret",
                format = com.example.data.ArchiveFormat.ZIP,
                compressionLevel = com.example.data.ArchiveCompressionLevel.BEST,
                password = "ArcPass2026"
            ).getOrThrow()
            val encInspection = repo.inspectArchive(encZip).getOrThrow()
            assertTrue(encInspection.isEncrypted)

            assertTrue(
                repo.extractArchiveToDirectory(
                    item = encZip,
                    targetParentPathOrUri = encExtractTarget.absolutePath,
                    isTargetSaf = false,
                    password = null
                ).isFailure
            )
            assertTrue(
                repo.extractArchiveToDirectory(
                    item = encZip,
                    targetParentPathOrUri = encExtractTarget.absolutePath,
                    isTargetSaf = false,
                    password = "WrongPassword"
                ).isFailure
            )
            val encOk = repo.extractArchiveToDirectory(
                item = encZip,
                targetParentPathOrUri = encExtractTarget.absolutePath,
                isTargetSaf = false,
                createSubfolder = false,
                password = "ArcPass2026"
            )
            assertTrue("Expected encrypted ZIP extract success: ${encOk.exceptionOrNull()}", encOk.isSuccess)
            assertEquals("Top-level content 12345", java.io.File(encExtractTarget, "notes.txt").readText())

            // 4. Zip-Slip traversal entry must never escape target directory
            val maliciousZip = java.io.File(workDir, "malicious.zip")
            java.util.zip.ZipOutputStream(maliciousZip.outputStream()).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("../../escaped_evil.txt"))
                zos.write("bad".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(java.util.zip.ZipEntry("safe.txt"))
                zos.write("good".toByteArray())
                zos.closeEntry()
            }
            val slipTarget = java.io.File(workDir, "slip_sub/target").apply { mkdirs() }
            val slipItem = FileItem(maliciousZip.absolutePath, maliciousZip.name, maliciousZip.absolutePath, false, maliciousZip.length(), 100L, "application/zip", "zip", false, workDir.absolutePath)
            repo.extractArchiveToDirectory(slipItem, slipTarget.absolutePath, isTargetSaf = false, createSubfolder = false).getOrThrow()
            assertFalse(java.io.File(workDir, "escaped_evil.txt").exists())
            assertEquals("good", java.io.File(slipTarget, "safe.txt").readText())
            assertTrue(readme.exists())
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun seven_z_creation_inspection_and_extraction_work_end_to_end() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val workDir = java.io.File(repo.getWorkspaceRoot(), "SevenZTest_${System.nanoTime()}").apply { mkdirs() }
        try {
            val sub = java.io.File(workDir, "assets").apply { mkdirs() }
            java.io.File(sub, "config.json").writeText("""{"version":7}""")
            val mainFile = java.io.File(workDir, "report.txt").apply { writeText("7-Zip LZMA2 payload verified!") }

            val srcItems = listOf(
                FileItem(sub.absolutePath, sub.name, sub.absolutePath, true, 0L, 100L, "vnd.android.document/directory", "", false, workDir.absolutePath),
                FileItem(mainFile.absolutePath, mainFile.name, mainFile.absolutePath, false, mainFile.length(), 100L, "text/plain", "txt", false, workDir.absolutePath)
            )

            val createdRes = repo.createArchive(
                items = srcItems,
                targetParentPathOrUri = workDir.absolutePath,
                isTargetSaf = false,
                archiveBaseName = "backup_7z",
                format = com.example.data.ArchiveFormat.SEVEN_Z,
                compressionLevel = com.example.data.ArchiveCompressionLevel.NORMAL
            )
            assertTrue("Expected 7z creation success: ${createdRes.exceptionOrNull()}", createdRes.isSuccess)
            val sevenZItem = createdRes.getOrThrow()
            assertEquals("backup_7z.7z", sevenZItem.name)
            assertTrue(sevenZItem.isExtractableArchive)

            val inspection = repo.inspectArchive(sevenZItem).getOrThrow()
            assertEquals(com.example.data.ArchiveFormat.SEVEN_Z, inspection.format)
            assertTrue(inspection.entries.any { it.name == "assets/config.json" })
            assertTrue(inspection.entries.any { it.name == "report.txt" })

            val outDir = java.io.File(workDir, "extracted_7z").apply { mkdirs() }
            val count = repo.extractArchiveToDirectory(
                item = sevenZItem,
                targetParentPathOrUri = outDir.absolutePath,
                isTargetSaf = false,
                createSubfolder = true
            ).getOrThrow()
            assertEquals(2, count)
            assertEquals("""{"version":7}""", java.io.File(outDir, "backup_7z/assets/config.json").readText())
            assertEquals("7-Zip LZMA2 payload verified!", java.io.File(outDir, "backup_7z/report.txt").readText())
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun crc16Rar(bytes: ByteArray, from: Int = 2, to: Int = bytes.size): Int {
        val crc = java.util.zip.CRC32()
        crc.update(bytes, from, to - from)
        return (crc.value and 0xFFFFL).toInt()
    }

    private fun buildValidStoredRarV4(entryName: String, payload: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        // RAR4 Marker block (7 bytes)
        out.write(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00))

        // Main archive header (0x73), 13 bytes
        val mainHeader = ByteArray(13).apply {
            this[2] = 0x73.toByte()
            this[3] = 0x00; this[4] = 0x00 // FLAGS
            this[5] = 13; this[6] = 0x00 // HEAD_SIZE = 13
        }
        val mainCrc = crc16Rar(mainHeader)
        mainHeader[0] = (mainCrc and 0xFF).toByte()
        mainHeader[1] = ((mainCrc ushr 8) and 0xFF).toByte()
        out.write(mainHeader)

        // File header (0x74), stored method 0x30
        val nameBytes = entryName.toByteArray(Charsets.UTF_8)
        val headerSize = 32 + nameBytes.size
        val fileCrc = java.util.zip.CRC32().apply { update(payload) }.value
        val fh = ByteArray(headerSize).apply {
            this[2] = 0x74.toByte() // HEAD_TYPE = FILE
            this[3] = 0x00; this[4] = 0x80.toByte() // LHD_LARGE=0, long block flag 0x8000
            this[5] = (headerSize and 0xFF).toByte()
            this[6] = ((headerSize ushr 8) and 0xFF).toByte()
            // PACK_SIZE (bytes 7..10)
            this[7] = (payload.size and 0xFF).toByte()
            this[8] = ((payload.size ushr 8) and 0xFF).toByte()
            this[9] = ((payload.size ushr 16) and 0xFF).toByte()
            this[10] = ((payload.size ushr 24) and 0xFF).toByte()
            // UNP_SIZE (bytes 11..14)
            this[11] = this[7]; this[12] = this[8]; this[13] = this[9]; this[14] = this[10]
            this[15] = 0x03 // HOST_OS = Unix
            // FILE_CRC (bytes 16..19)
            this[16] = (fileCrc and 0xFFL).toByte()
            this[17] = ((fileCrc ushr 8) and 0xFFL).toByte()
            this[18] = ((fileCrc ushr 16) and 0xFFL).toByte()
            this[19] = ((fileCrc ushr 24) and 0xFFL).toByte()
            // FTIME (bytes 20..23) -> 0
            this[24] = 29 // UNP_VER = 2.9
            this[25] = 0x30 // METHOD = Store
            this[26] = (nameBytes.size and 0xFF).toByte()
            this[27] = ((nameBytes.size ushr 8) and 0xFF).toByte()
            // ATTR (28..31) -> 0
            System.arraycopy(nameBytes, 0, this, 32, nameBytes.size)
        }
        val fhCrc = crc16Rar(fh)
        fh[0] = (fhCrc and 0xFF).toByte()
        fh[1] = ((fhCrc ushr 8) and 0xFF).toByte()
        out.write(fh)
        out.write(payload)

        // End of archive block (0x7B), 7 bytes
        val endHeader = ByteArray(7).apply {
            this[2] = 0x7B.toByte()
            this[3] = 0x00; this[4] = 0x40
            this[5] = 7; this[6] = 0
        }
        val endCrc = crc16Rar(endHeader)
        endHeader[0] = (endCrc and 0xFF).toByte()
        endHeader[1] = ((endCrc ushr 8) and 0xFF).toByte()
        out.write(endHeader)

        return out.toByteArray()
    }

    @Test
    fun rar_extraction_rar5_notice_no_fake_creation_and_action_registry_work() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val workDir = java.io.File(repo.getWorkspaceRoot(), "RarTest_${System.nanoTime()}").apply { mkdirs() }
        try {
            // 1. Valid RAR v4 preview & extraction
            val rarFile = java.io.File(workDir, "sample.rar")
            rarFile.writeBytes(buildValidStoredRarV4("hello_rar.txt", "Decoded genuine RAR v4 content!".toByteArray()))
            val rarItem = FileItem(rarFile.absolutePath, rarFile.name, rarFile.absolutePath, false, rarFile.length(), 100L, "application/vnd.rar", "rar", false, workDir.absolutePath)

            val rarInspection = repo.inspectArchive(rarItem).getOrThrow()
            assertEquals(com.example.data.ArchiveFormat.RAR, rarInspection.format)
            assertEquals("hello_rar.txt", rarInspection.entries.single().name)

            val outDir = java.io.File(workDir, "rar_out").apply { mkdirs() }
            val count = repo.extractArchiveToDirectory(rarItem, outDir.absolutePath, isTargetSaf = false, createSubfolder = true).getOrThrow()
            assertEquals(1, count)
            assertEquals("Decoded genuine RAR v4 content!", java.io.File(outDir, "sample/hello_rar.txt").readText())

            // 2. RAR5 gracefully reports errorNote pointing to Open As instead of crashing
            val rar5File = java.io.File(workDir, "modern_v5.rar").apply {
                writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00, 0x00, 0x00))
            }
            val rar5Item = FileItem(rar5File.absolutePath, rar5File.name, rar5File.absolutePath, false, rar5File.length(), 100L, "application/vnd.rar", "rar", false, workDir.absolutePath)
            val rar5Inspect = repo.inspectArchive(rar5Item).getOrThrow()
            assertTrue(rar5Inspect.entries.isEmpty())
            assertTrue(rar5Inspect.errorNote?.contains("RAR5", ignoreCase = true) == true)

            // 3. Rejects creating fake .rar archive cleanly
            val sampleTxt = java.io.File(workDir, "src.txt").apply { writeText("abc") }
            val srcItem = FileItem(sampleTxt.absolutePath, sampleTxt.name, sampleTxt.absolutePath, false, sampleTxt.length(), 100L, "text/plain", "txt", false, workDir.absolutePath)
            val fakeRarRes = repo.createArchive(
                items = listOf(srcItem),
                targetParentPathOrUri = workDir.absolutePath,
                isTargetSaf = false,
                archiveBaseName = "should_not_exist",
                format = com.example.data.ArchiveFormat.RAR
            )
            assertTrue(fakeRarRes.isFailure)
            assertFalse(java.io.File(workDir, "should_not_exist.rar").exists())

            // 4. FileActionRegistry exposes EXTRACT only for .zip/.7z/.rar, and COMPRESS for all non-empty selections
            val sevenZItem = srcItem.copy(id = "7z", name = "a.7z", extension = "7z")
            val zipItem = srcItem.copy(id = "zip", name = "a.zip", extension = "zip")
            listOf(zipItem, sevenZItem, rarItem).forEach { arc ->
                val ids = com.example.ui.registry.FileActionRegistry.visibleFor(listOf(arc)).map { it.id }
                assertTrue(com.example.ui.registry.FileActionId.EXTRACT in ids)
                assertTrue(com.example.ui.registry.FileActionId.COMPRESS in ids)
            }
            val txtIds = com.example.ui.registry.FileActionRegistry.visibleFor(listOf(srcItem)).map { it.id }
            assertFalse(com.example.ui.registry.FileActionId.EXTRACT in txtIds)
            assertTrue(com.example.ui.registry.FileActionId.COMPRESS in txtIds)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun compress_and_archive_preview_dialogs_render_and_dispatch_correctly() {
        val sampleFile = FileItem("f1", "project_notes.txt", "/tmp/project_notes.txt", false, 4096L, 100L, "text/plain", "txt", false, "/tmp")
        var createdPayload: Triple<String, com.example.data.ArchiveFormat, Pair<com.example.data.ArchiveCompressionLevel, String?>>? = null
        val previewExtractCalls = mutableListOf<Pair<Boolean, String?>>()
        var openAsTriggered = false

        val showCompress = androidx.compose.runtime.mutableStateOf(true)
        val previewInspection = androidx.compose.runtime.mutableStateOf(
            com.example.data.ArchiveInspectionResult(
                format = com.example.data.ArchiveFormat.SEVEN_Z,
                entries = listOf(
                    com.example.data.ZipEntryInfo("docs/", true, 0L),
                    com.example.data.ZipEntryInfo("docs/spec.pdf", false, 2048L, isEncrypted = true)
                ),
                isEncrypted = true
            )
        )

        composeTestRule.setContent {
            com.example.ui.theme.ArcFilesTheme {
                if (showCompress.value) {
                    com.example.ui.components.dialogs.CompressArchiveDialog(
                        items = listOf(sampleFile),
                        onDismiss = {},
                        onCreate = { name, format, level, pwd ->
                            createdPayload = Triple(name, format, level to pwd)
                        }
                    )
                } else {
                    com.example.ui.components.dialogs.ArchivePreviewDialog(
                        item = sampleFile.copy(name = "bundle.rar", extension = "rar"),
                        inspection = previewInspection.value,
                        isLoading = false,
                        onDismiss = {},
                        onRetryWithPassword = {},
                        onExtract = { toFolder, pwd -> previewExtractCalls.add(toFolder to pwd) },
                        onOpenAsFallback = { openAsTriggered = true }
                    )
                }
            }
        }

        // Verify CompressArchiveDialog: RAR notice, 7Z hides encryption, ZIP supports AES password + Best level
        composeTestRule.onNodeWithTag("compress_archive_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("compress_format_rar_info").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("compress_rar_readonly_note").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("compress_format_7z").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("compress_7z_encryption_note").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("compress_format_zip").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("compress_level_best").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("compress_password_toggle").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("compress_password_input").performScrollTo().performTextInput("MySecret")
        composeTestRule.onNodeWithTag("compress_confirm_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("project_notes", createdPayload?.first)
        assertEquals(com.example.data.ArchiveFormat.ZIP, createdPayload?.second)
        assertEquals(com.example.data.ArchiveCompressionLevel.BEST, createdPayload?.third?.first)
        assertEquals("MySecret", createdPayload?.third?.second)

        // Switch to ArchivePreviewDialog and verify entry listing, password field, Extract Here vs To Folder, and RAR5 fallback
        showCompress.value = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("archive_preview_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("archive_entry_list").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("archive_extract_password_input").performScrollTo().performTextInput("Pass123")
        composeTestRule.onNodeWithTag("archive_extract_here_button").performClick()
        composeTestRule.onNodeWithTag("archive_extract_folder_button").performClick()
        assertEquals(listOf(false to "Pass123", true to "Pass123"), previewExtractCalls)

        // RAR5 warning state exposes Open As fallback button
        previewInspection.value = com.example.data.ArchiveInspectionResult(
            format = com.example.data.ArchiveFormat.RAR,
            entries = emptyList(),
            isEncrypted = false,
            errorNote = "RAR5 archives cannot be decoded by the built-in extractor."
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("archive_error_banner").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("archive_open_as_fallback_button").performScrollTo().performClick()
        assertTrue(openAsTriggered)
    }

    @Test
    fun ios_files_browse_ui_displays_header_search_tags_and_supports_dark_mode() {
        composeTestRule.setContent {
            ArcFilesRoot()
        }
        composeTestRule.waitForIdle()

        // iOS Files Browse title + rounded search pill on Home
        composeTestRule.onNodeWithTag("home_browse_header").assertIsDisplayed()
        composeTestRule.onNodeWithText("Browse").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_search_bar").assertIsDisplayed()

        // Collapsible iOS Tags inset group on Home opens Tag tab when clicked
        val homeList = composeTestRule.onNodeWithTag("home_screen_list")
        homeList.performScrollToNode(hasTestTag("home_tags_card"))
        composeTestRule.onNodeWithTag("home_tags_card").assertIsDisplayed()
        homeList.performScrollToNode(hasTestTag("home_tag_tag_red"))
        composeTestRule.onNodeWithTag("home_tag_tag_red").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tag_header_bar").assertIsDisplayed()

        // Tapping Home tab returns to Browse and sidebar toggles Dark/Light cleanly
        composeTestRule.onNodeWithTag("tab_chip_tab_home").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithTag("home_screen_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_screen_list").performScrollToNode(hasTestTag("home_browse_header"))
        composeTestRule.onNodeWithTag("home_browse_header").assertIsDisplayed()

        composeTestRule.onNodeWithTag("island_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("arc_sidebar_list").performScrollToNode(hasTestTag("drawer_theme_dark"))
        composeTestRule.onNodeWithTag("drawer_theme_dark").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_theme_light").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun apk_open_checks_unknown_sources_and_launches_built_in_package_installer() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = FileExplorerRepository(app)
        val apkFile = java.io.File(repo.getWorkspaceRoot(), "sample_update_test.apk").apply {
            writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        }
        val apkItem = FileItem(
            id = apkFile.absolutePath,
            name = apkFile.name,
            pathOrUri = apkFile.absolutePath,
            isDirectory = false,
            sizeBytes = apkFile.length(),
            lastModifiedMillis = apkFile.lastModified(),
            mimeType = "application/vnd.android.package-archive",
            extension = "apk",
            isSafUri = false,
            parentPathOrUri = apkFile.parentFile?.absolutePath ?: ""
        )

        try {
            val spm = Shadows.shadowOf(app.packageManager)
            spm.setCanRequestPackageInstalls(false)

            val viewModel = com.example.ui.ExplorerViewModel(app)
            val launchedIntents = mutableListOf<android.content.Intent>()

            composeTestRule.runOnUiThread {
                viewModel.openFile(apkItem) { launchedIntents += it }
            }
            assertEquals(1, launchedIntents.size)
            assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, launchedIntents.last().action)

            // User grants Unknown App Sources permission and returns to app
            spm.setCanRequestPackageInstalls(true)
            composeTestRule.runOnUiThread {
                viewModel.onAppResumed { launchedIntents += it }
            }

            assertEquals(2, launchedIntents.size)
            val installIntent = launchedIntents.last()
            assertEquals(android.content.Intent.ACTION_VIEW, installIntent.action)
            assertEquals("application/vnd.android.package-archive", installIntent.type)
            assertNull(installIntent.component)
            assertNotNull(installIntent.clipData)
            assertTrue((installIntent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        } finally {
            apkFile.delete()
        }
    }

    @Test
    fun missing_compatible_app_shows_clear_message_and_wildcard_fallback_and_broad_mime_resolves() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = FileExplorerRepository(context)
        val unknownFile = java.io.File(repo.getWorkspaceRoot(), "archive_payload.xyzunknown").apply {
            writeText("custom binary data")
        }
        val unknownItem = FileItem(
            id = unknownFile.absolutePath,
            name = unknownFile.name,
            pathOrUri = unknownFile.absolutePath,
            isDirectory = false,
            sizeBytes = unknownFile.length(),
            lastModifiedMillis = unknownFile.lastModified(),
            mimeType = "application/octet-stream",
            extension = "xyzunknown",
            isSafUri = false,
            parentPathOrUri = unknownFile.parentFile?.absolutePath ?: ""
        )
        val yamlFile = java.io.File(repo.getWorkspaceRoot(), "deploy_config.yaml").apply {
            writeText("env: production\n")
        }
        val yamlItem = FileItem(
            id = yamlFile.absolutePath,
            name = yamlFile.name,
            pathOrUri = yamlFile.absolutePath,
            isDirectory = false,
            sizeBytes = yamlFile.length(),
            lastModifiedMillis = yamlFile.lastModified(),
            mimeType = "application/yaml",
            extension = "yaml",
            isSafUri = false,
            parentPathOrUri = yamlFile.parentFile?.absolutePath ?: ""
        )

        try {
            val viewModel = com.example.ui.ExplorerViewModel(context)
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.uiState.value.tabs.any { it.id == "tab_default_internal" }
            }

            // 1. When no app matches at all (not even */*), opening sets clear "No app installed" snackbar and keeps dialog closed
            composeTestRule.runOnUiThread {
                viewModel.requestOpenWith(unknownItem, fromOpenAsAction = true)
            }
            composeTestRule.waitForIdle()
            assertEquals(
                "No app installed on this device to open 'archive_payload.xyzunknown'",
                viewModel.uiState.value.snackbarMessage
            )
            assertNull(viewModel.uiState.value.openWithRequest)

            // 2. When a text/plain editor is installed, opening .yaml via broad fallback discovers it
            val shadowPm = Shadows.shadowOf(context.packageManager)
            val editorInfo = android.content.pm.ActivityInfo().apply {
                packageName = "com.fake.editor"
                name = "com.fake.editor.TextActivity"
                enabled = true
                exported = true
                applicationInfo = android.content.pm.ApplicationInfo().apply {
                    packageName = "com.fake.editor"
                }
            }
            val editorResolve = android.content.pm.ResolveInfo().apply {
                activityInfo = editorInfo
                nonLocalizedLabel = "Quick Editor"
            }
            val textIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(
                    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", yamlFile),
                    "text/plain"
                )
            }
            shadowPm.addResolveInfoForIntent(textIntent, editorResolve)

            composeTestRule.runOnUiThread {
                viewModel.requestOpenWith(yamlItem, fromOpenAsAction = true)
            }
            val yamlApps = viewModel.uiState.value.openWithRequest?.apps.orEmpty()
            assertTrue(yamlApps.any { it.packageName == "com.fake.editor" })
            composeTestRule.runOnUiThread {
                viewModel.dismissOpenWith()
            }

            // 3. Verify SelectOpenAppDialog's "No App Installed" UI + "Show All Apps" -> wildcard transition
            val tryAllTriggered = mutableListOf<String>()
            val reqState = androidx.compose.runtime.mutableStateOf<com.example.data.OpenWithRequest?>(
                com.example.data.OpenWithRequest(
                    item = unknownItem,
                    fromOpenAsAction = true,
                    apps = emptyList(),
                    mimeOverride = null
                )
            )
            composeTestRule.setContent {
                com.example.ui.theme.ArcFilesTheme {
                    reqState.value?.let { req ->
                        com.example.ui.components.dialogs.SelectOpenAppDialog(
                            request = req,
                            onDismiss = { reqState.value = null },
                            onConfirmApp = { _, _ -> },
                            onTryAllApps = {
                                tryAllTriggered += "*/*"
                                reqState.value = req.copy(
                                    apps = listOf(
                                        com.example.data.ResolvedAppOption(
                                            packageName = "com.fake.hex",
                                            activityName = "com.fake.hex.HexActivity",
                                            label = "Hex Viewer"
                                        )
                                    ),
                                    preselectedComponentKey = "com.fake.hex/com.fake.hex.HexActivity",
                                    mimeOverride = "*/*"
                                )
                            }
                        )
                    }
                }
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("select_open_app_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithText("No App Installed").assertIsDisplayed()
            composeTestRule.onNodeWithText("No installed app can open 'archive_payload.xyzunknown'.").assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_app_try_all_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_app_close_button").assertIsDisplayed()

            composeTestRule.onNodeWithTag("open_app_try_all_button").performClick()
            composeTestRule.waitForIdle()
            assertEquals(listOf("*/*"), tryAllTriggered)
            composeTestRule.onNodeWithText("Hex Viewer").assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_app_always_button").assertIsDisplayed()
        } finally {
            unknownFile.delete()
            yamlFile.delete()
        }
    }

    @Test
    fun adaptive_dimens_and_grid_metrics_scale_across_compact_and_tablet_widths() {
        val compact = com.example.ui.theme.ArcDimens.forWidth(320.dp)
        val standard = com.example.ui.theme.ArcDimens.forWidth(411.dp)
        val expanded = com.example.ui.theme.ArcDimens.forWidth(840.dp)

        assertTrue(compact.screenHorizontalPadding < standard.screenHorizontalPadding)
        assertTrue(expanded.screenHorizontalPadding > standard.screenHorizontalPadding)
        assertTrue(expanded.sectionGap > compact.sectionGap)

        val compact4Cols = compact.gridMetricsFor(columns = 4, availableWidth = 320.dp)
        assertTrue(compact4Cols.cardCornerRadius > 0.dp)
        assertTrue(compact4Cols.cellWidth >= 52.dp)

        val tablet5Cols = expanded.gridMetricsFor(columns = 5, availableWidth = 840.dp)
        assertTrue(tablet5Cols.gridGap > compact4Cols.gridGap)
        assertTrue(tablet5Cols.cellWidth > compact4Cols.cellWidth)
    }

    @Test
    fun readable_empty_directory_renders_clean_empty_folder_view_even_with_scoped_banner() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val emptyDir = java.io.File(repo.getWorkspaceRoot(), "Alarms_Empty_${System.nanoTime()}").apply { mkdirs() }
        try {
            val state = repo.listDirectory(
                pathOrUri = emptyDir.absolutePath,
                isSaf = false,
                showHidden = false,
                sortField = SortField.NAME,
                ascending = true
            )
            assertTrue(state is DirectoryLoadState.Loaded)
            assertTrue((state as DirectoryLoadState.Loaded).items.isEmpty())

            val alarmsDest = TabDestination.Directory(
                rootId = "drive_internal",
                title = "Alarms",
                currentPathOrUri = emptyDir.absolutePath,
                isSaf = false,
                breadcrumbs = listOf(
                    com.example.data.BreadcrumbNode("Internal Storage", repo.getWorkspaceRoot().absolutePath, false),
                    com.example.data.BreadcrumbNode("Alarms", emptyDir.absolutePath, false)
                )
            )
            composeTestRule.setContent {
                com.example.ui.theme.ArcFilesTheme {
                    com.example.ui.screens.browser.BrowserScreen(
                        destination = alarmsDest,
                        directoryState = DirectoryLoadState.Loaded(emptyList(), isScopedStorageLimited = true),
                        categoryItems = emptyList(),
                        searchResults = emptyList(),
                        selectedIds = emptySet(),
                        clipboard = null,
                        viewMode = ViewMode.LIST,
                        sortField = SortField.NAME,
                        sortAscending = true,
                        monochromeBinaryIcons = false,
                        onBack = {},
                        onBreadcrumbClick = {},
                        onSearchQueryChange = {},
                        onToggleViewMode = {},
                        onSortOrderChange = { _, _ -> },
                        onToggleSortOrder = {},
                        onGroupByChange = {},
                        onNewFolderClick = {},
                        onNewNoteClick = {},
                        onRequestAllFilesAccess = {},
                        onMountFolderClick = {},
                        onImportMediaClick = {},
                        onImportDocsClick = {},
                        onPasteClick = {},
                        onClearClipboard = {},
                        onFileClick = {},
                        onToggleSelect = {},
                        onSelectAll = {},
                        onClearSelection = {},
                        onFileAction = { _, _ -> }
                    )
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("empty_browser_state").assertIsDisplayed()
            composeTestRule.onNodeWithText("This folder is empty").assertIsDisplayed()
            composeTestRule.onNodeWithText("Files and folders added here will appear automatically.").assertIsDisplayed()
            composeTestRule.onNodeWithText("Full storage access needed").assertDoesNotExist()
        } finally {
            emptyDir.deleteRecursively()
        }
    }

    @Test
    fun markdown_and_html_open_preview_first_switch_to_source_and_support_builtin_vs_external_app_switching() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileExplorerRepository(context)
        val mdFile = java.io.File(repo.getWorkspaceRoot(), "guide_${System.nanoTime()}.md").apply {
            writeText("# Release Notes\n\n- **Fast** scroll\n- [x] Ready\n")
        }
        val htmlFile = java.io.File(repo.getWorkspaceRoot(), "index_${System.nanoTime()}.html").apply {
            writeText("<!doctype html><html><body><h1>Hello Web</h1><p>Clean preview</p></body></html>")
        }
        val mdItem = FileItem(
            id = mdFile.absolutePath,
            name = mdFile.name,
            pathOrUri = mdFile.absolutePath,
            isDirectory = false,
            sizeBytes = mdFile.length(),
            lastModifiedMillis = mdFile.lastModified(),
            mimeType = "text/markdown",
            extension = "md",
            isSafUri = false,
            parentPathOrUri = mdFile.parentFile?.absolutePath ?: ""
        )
        val htmlItem = FileItem(
            id = htmlFile.absolutePath,
            name = htmlFile.name,
            pathOrUri = htmlFile.absolutePath,
            isDirectory = false,
            sizeBytes = htmlFile.length(),
            lastModifiedMillis = htmlFile.lastModified(),
            mimeType = "text/html",
            extension = "html",
            isSafUri = false,
            parentPathOrUri = htmlFile.parentFile?.absolutePath ?: ""
        )

        val shadowPm = Shadows.shadowOf(context.packageManager)
        val chromeComponent = android.content.ComponentName("com.fake.chrome", "com.fake.chrome.WebActivity")
        val chromeActInfo = android.content.pm.ActivityInfo().apply {
            packageName = chromeComponent.packageName
            name = chromeComponent.className
            enabled = true
            exported = true
            nonLocalizedLabel = "Arc Browser"
            applicationInfo = android.content.pm.ApplicationInfo().apply {
                packageName = chromeComponent.packageName
                nonLocalizedLabel = "Arc Browser"
                enabled = true
                flags = android.content.pm.ApplicationInfo.FLAG_INSTALLED
            }
        }
        val chromeResolve = android.content.pm.ResolveInfo().apply {
            activityInfo = chromeActInfo
            nonLocalizedLabel = "Arc Browser"
        }
        shadowPm.addResolveInfoForIntent(repo.buildViewIntent(htmlItem), chromeResolve)
        shadowPm.addResolveInfoForIntent(
            repo.buildViewIntent(htmlItem, packageName = chromeComponent.packageName, activityName = chromeComponent.className),
            chromeResolve
        )

        try {
            val viewModel = com.example.ui.ExplorerViewModel(context as android.app.Application)
            val launchedIntents = mutableListOf<android.content.Intent>()

            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.uiState.value.tabs.any { it.id == "tab_default_internal" }
            }

            composeTestRule.setContent {
                val state = viewModel.uiState.collectAsState().value
                com.example.ui.theme.ArcFilesTheme {
                    state.previewFile?.let { file ->
                        com.example.ui.components.dialogs.FilePreviewDialog(
                            item = file,
                            textContent = state.previewText,
                            onDismiss = viewModel::closePreview,
                            onSaveText = viewModel::saveEditedPreviewText,
                            onShare = {},
                            onOpenExternal = { viewModel.requestOpenWith(file, fromOpenAsAction = true) }
                        )
                    }
                    state.openWithRequest?.let { req ->
                        com.example.ui.components.dialogs.SelectOpenAppDialog(
                            request = req,
                            onDismiss = viewModel::dismissOpenWith,
                            onConfirmApp = { app, always ->
                                viewModel.confirmOpenWithApp(req, app, always) { launchedIntents += it }
                            },
                            onTryAllApps = {}
                        )
                    }
                }
            }

            // 1. Markdown opens in Preview mode first, can switch to Source and Edit
            composeTestRule.runOnUiThread {
                viewModel.openFile(mdItem) { launchedIntents += it }
            }
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.uiState.value.previewFile?.id == mdItem.id
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("document_viewer_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("viewer_tab_preview").assertIsDisplayed()
            composeTestRule.onNodeWithTag("viewer_tab_source").assertIsDisplayed()
            composeTestRule.onNodeWithTag("viewer_markdown_preview").assertIsDisplayed()
            composeTestRule.onNodeWithText("Release Notes").assertIsDisplayed()
            assertEquals(0, composeTestRule.onAllNodesWithTag("viewer_source_content").fetchSemanticsNodes().size)

            composeTestRule.onNodeWithTag("viewer_tab_source").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("viewer_source_content").assertIsDisplayed()
            composeTestRule.onNodeWithText("# Release Notes", substring = true).assertIsDisplayed()

            composeTestRule.onNodeWithTag("viewer_edit_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("viewer_source_editor").assertIsDisplayed()
            composeTestRule.onNodeWithTag("viewer_save_button").performClick()
            composeTestRule.waitForIdle()

            // 2. HTML opens in Preview mode first, switches cleanly to Source
            composeTestRule.runOnUiThread {
                viewModel.openFile(htmlItem) { launchedIntents += it }
            }
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.uiState.value.previewFile?.id == htmlItem.id
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("viewer_html_preview").assertIsDisplayed()
            composeTestRule.onNodeWithTag("viewer_tab_source").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("<h1>Hello Web</h1>", substring = true).assertIsDisplayed()

            // 3. Viewer's Open With shows separate Built-in option + external Arc Browser; selecting Always switches .html to External
            composeTestRule.onNodeWithTag("viewer_open_with_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("select_open_app_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_app_option_${com.example.data.BUILTIN_VIEWER_PACKAGE}").assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_app_builtin_badge", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_app_option_com.fake.chrome").assertIsDisplayed()

            composeTestRule.onNodeWithTag("open_app_option_com.fake.chrome").performClick()
            composeTestRule.onNodeWithTag("open_app_always_button").performClick()
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.uiState.value.prefs.parsedDefaultOpenApps()[com.example.data.defaultOpenAppKey(htmlItem)]?.packageName == "com.fake.chrome"
            }
            assertEquals(chromeComponent, launchedIntents.last().component)
            assertNull(viewModel.uiState.value.previewFile)

            // 4. Reopening .html now uses External app automatically
            composeTestRule.runOnUiThread {
                viewModel.openFile(htmlItem) { launchedIntents += it }
            }
            composeTestRule.waitForIdle()
            assertEquals(2, launchedIntents.size)
            assertNull(viewModel.uiState.value.previewFile)

            // 5. Switching back to Built-in App + Always restores Built-in Preview
            composeTestRule.runOnUiThread {
                viewModel.requestOpenWith(htmlItem, fromOpenAsAction = true)
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("open_app_option_${com.example.data.BUILTIN_VIEWER_PACKAGE}").performClick()
            composeTestRule.onNodeWithTag("open_app_always_button").performClick()
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.uiState.value.previewFile?.id == htmlItem.id &&
                    viewModel.uiState.value.prefs.parsedDefaultOpenApps()[com.example.data.defaultOpenAppKey(htmlItem)]?.isBuiltinApp == true
            }

            // 6. Disabling Built-in Text Viewer in Settings routes Markdown through picker when no Always default exists
            composeTestRule.runOnUiThread {
                viewModel.closePreview()
                viewModel.setUseBuiltinTextViewer(false)
            }
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                !viewModel.uiState.value.prefs.useBuiltinTextViewer
            }
            composeTestRule.runOnUiThread {
                viewModel.openFile(mdItem) { launchedIntents += it }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("select_open_app_dialog").assertIsDisplayed()
            assertNull(viewModel.uiState.value.previewFile)
        } finally {
            runCatching {
                val prefsRepo = com.example.data.PreferencesRepository(context)
                kotlinx.coroutines.runBlocking {
                    prefsRepo.clearAllDefaultOpenApps()
                    prefsRepo.setUseBuiltinTextViewer(true)
                }
            }
            mdFile.delete()
            htmlFile.delete()
        }
    }

    @Test
    fun drag_and_drop_supports_single_and_batch_custom_sorting_and_settings_toggle() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = com.example.ui.ExplorerViewModel(context)

        val rootDir = java.io.File(context.cacheDir, "dnd_custom_sort_${System.nanoTime()}").apply { mkdirs() }
        val folder = java.io.File(rootDir, "DestFolder").apply { mkdirs() }
        val fAlpha = java.io.File(rootDir, "alpha.txt").apply { writeText("a") }
        val fBeta = java.io.File(rootDir, "beta.txt").apply { writeText("b") }
        val fGamma = java.io.File(rootDir, "gamma.txt").apply { writeText("c") }

        try {
            fun makeItem(f: java.io.File) = FileItem(
                id = f.absolutePath,
                name = f.name,
                pathOrUri = f.absolutePath,
                isDirectory = f.isDirectory,
                sizeBytes = if (f.isDirectory) 0L else f.length(),
                lastModifiedMillis = f.lastModified(),
                mimeType = if (f.isDirectory) "inode/directory" else "text/plain",
                extension = if (f.isDirectory) "" else "txt",
                isSafUri = false,
                parentPathOrUri = rootDir.absolutePath
            )

            val initialItems = listOf(makeItem(folder), makeItem(fAlpha), makeItem(fBeta), makeItem(fGamma))
            val customKeysState = androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
            val sortFieldState = androidx.compose.runtime.mutableStateOf(SortField.NAME)
            val selectedIdsState = androidx.compose.runtime.mutableStateOf<Set<String>>(emptySet())
            val dragReorderEnabledState = androidx.compose.runtime.mutableStateOf(true)

            var movedToFolder: Pair<List<String>, String>? = null
            val reorderCommits = mutableListOf<List<String>>()

            val dest = TabDestination.Directory(
                title = "SortBox",
                rootId = rootDir.absolutePath,
                currentPathOrUri = rootDir.absolutePath,
                isSaf = false,
                breadcrumbs = listOf(com.example.data.BreadcrumbNode("SortBox", rootDir.absolutePath, false))
            )

            composeTestRule.setContent {
                val sortedItems = com.example.data.FileGroupingUtils.sortItems(
                    items = initialItems,
                    sortField = sortFieldState.value,
                    ascending = true,
                    foldersFirst = true,
                    customOrderKeys = customKeysState.value
                )
                com.example.ui.theme.ArcFilesTheme {
                    com.example.ui.screens.browser.BrowserScreen(
                        destination = dest,
                        directoryState = DirectoryLoadState.Loaded(items = sortedItems),
                        categoryItems = emptyList(),
                        searchResults = emptyList(),
                        selectedIds = selectedIdsState.value,
                        clipboard = null,
                        viewMode = ViewMode.LIST,
                        sortField = sortFieldState.value,
                        sortAscending = true,
                        foldersFirst = true,
                        monochromeBinaryIcons = false,
                        dragReorderEnabled = dragReorderEnabledState.value,
                        onBack = {},
                        onBreadcrumbClick = {},
                        onSearchQueryChange = {},
                        onToggleViewMode = {},
                        onSortOrderChange = { _, _ -> },
                        onToggleSortOrder = {},
                        onGroupByChange = {},
                        onNewFolderClick = {},
                        onNewNoteClick = {},
                        onRequestAllFilesAccess = {},
                        onMountFolderClick = {},
                        onImportMediaClick = {},
                        onImportDocsClick = {},
                        onPasteClick = {},
                        onClearClipboard = {},
                        onFileClick = {},
                        onToggleSelect = { item ->
                            val next = selectedIdsState.value.toMutableSet()
                            if (!next.add(item.id)) next.remove(item.id)
                            selectedIdsState.value = next
                        },
                        onSelectAll = {},
                        onClearSelection = { selectedIdsState.value = emptySet() },
                        onFileAction = { _, _ -> },
                        onMoveItemsToFolder = { items, _, _, label ->
                            movedToFolder = items.map { it.name } to label
                        },
                        onReorderItems = { orderedIds ->
                            reorderCommits += orderedIds
                            customKeysState.value = orderedIds
                            sortFieldState.value = SortField.CUSTOM
                        }
                    )
                }
            }

            // 1. Single drag: alpha.txt onto gamma.txt reorders smoothly [DestFolder, beta.txt, gamma.txt, alpha.txt]
            val gammaCenter = composeTestRule.onNodeWithTag("file_row_gamma.txt").fetchSemanticsNode().boundsInRoot.center
            val alphaCenter = composeTestRule.onNodeWithTag("file_row_alpha.txt").fetchSemanticsNode().boundsInRoot.center
            composeTestRule.onNodeWithTag("file_row_alpha.txt").performTouchInput {
                down(center)
                advanceEventTime(650)
                moveBy(gammaCenter - alphaCenter)
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("drag_ghost_pill").assertIsDisplayed()
            composeTestRule.onNodeWithText("Reorder → Position #4").assertIsDisplayed()
            composeTestRule.onNodeWithTag("file_row_alpha.txt").performTouchInput { up() }
            composeTestRule.waitForIdle()

            assertNull("Reordering across files must not trigger folder move", movedToFolder)
            assertEquals(1, reorderCommits.size)
            assertEquals(
                listOf("DestFolder", "beta.txt", "gamma.txt", "alpha.txt"),
                customKeysState.value.map { java.io.File(it).name }
            )

            // 2. Batch drag: select beta.txt + gamma.txt, drag down onto alpha.txt -> moves whole batch together
            selectedIdsState.value = setOf(fBeta.absolutePath, fGamma.absolutePath)
            composeTestRule.waitForIdle()
            val betaCenter = composeTestRule.onNodeWithTag("file_row_beta.txt").fetchSemanticsNode().boundsInRoot.center
            val newAlphaCenter = composeTestRule.onNodeWithTag("file_row_alpha.txt").fetchSemanticsNode().boundsInRoot.center
            composeTestRule.onNodeWithTag("file_row_beta.txt").performTouchInput {
                down(center)
                advanceEventTime(650)
                moveBy(newAlphaCenter - betaCenter)
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Reorder 2 items → #3").assertIsDisplayed()
            composeTestRule.onNodeWithTag("file_row_beta.txt").performTouchInput { up() }
            composeTestRule.waitForIdle()

            assertEquals(
                listOf("DestFolder", "alpha.txt", "beta.txt", "gamma.txt"),
                customKeysState.value.map { java.io.File(it).name }
            )

            // 3. Dragging onto DestFolder center STILL triggers folder move
            selectedIdsState.value = emptySet()
            composeTestRule.waitForIdle()
            val folderCenter = composeTestRule.onNodeWithTag("file_row_DestFolder").fetchSemanticsNode().boundsInRoot.center
            val alphaNowCenter = composeTestRule.onNodeWithTag("file_row_alpha.txt").fetchSemanticsNode().boundsInRoot.center
            composeTestRule.onNodeWithTag("file_row_alpha.txt").performTouchInput {
                down(center)
                advanceEventTime(650)
                moveBy(folderCenter - alphaNowCenter)
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Drop to move → DestFolder").assertIsDisplayed()
            composeTestRule.onNodeWithTag("file_row_alpha.txt").performTouchInput { up() }
            composeTestRule.waitForIdle()
            assertEquals(listOf("alpha.txt") to "DestFolder", movedToFolder)

            // 4. Disabling drag reorder prevents file-on-file sorting while ViewModel persists the toggle
            val commitsBeforeDisable = reorderCommits.size
            dragReorderEnabledState.value = false
            composeTestRule.waitForIdle()

            val gammaBottomCenter = composeTestRule.onNodeWithTag("file_row_gamma.txt").fetchSemanticsNode().boundsInRoot.center
            val alphaAgainCenter = composeTestRule.onNodeWithTag("file_row_alpha.txt").fetchSemanticsNode().boundsInRoot.center
            composeTestRule.onNodeWithTag("file_row_alpha.txt").performTouchInput {
                down(center)
                advanceEventTime(650)
                moveBy(gammaBottomCenter - alphaAgainCenter)
                up()
            }
            composeTestRule.waitForIdle()
            assertEquals(commitsBeforeDisable, reorderCommits.size)

            viewModel.setDragReorderEnabled(false)
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                !viewModel.uiState.value.prefs.dragReorderEnabled
            }
            viewModel.setDragReorderEnabled(true)
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.uiState.value.prefs.dragReorderEnabled
            }
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun room_migration_3_to_4_preserves_existing_data_and_enables_album_tables() {
        val dbFile = java.io.File.createTempFile("arc_mig_3_4_", ".db")
        try {
            val helperFactory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
            val v3Callback = object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS folder_layouts (
                            folderKey TEXT NOT NULL PRIMARY KEY,
                            viewMode TEXT NOT NULL,
                            gridColumns INTEGER NOT NULL,
                            originalMediaRatio INTEGER NOT NULL,
                            sortField TEXT NOT NULL,
                            sortAscending INTEGER NOT NULL,
                            groupBy TEXT NOT NULL,
                            customOrderKeys TEXT NOT NULL DEFAULT '',
                            updatedAtMillis INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS file_tags (
                            id TEXT NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            colorHex TEXT NOT NULL,
                            createdAtMillis INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "INSERT INTO folder_layouts VALUES ('library:IMAGES', 'GRID', 3, 1, 'DATE', 0, 'NONE', '', 1000)"
                    )
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }
            val openHelper = helperFactory.create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                    .name(dbFile.absolutePath)
                    .callback(v3Callback)
                    .build()
            )
            val rawDb = openHelper.writableDatabase
            com.example.data.db.ArcDatabase.MIGRATION_3_4.migrate(rawDb)

            rawDb.query("SELECT viewMode FROM folder_layouts WHERE folderKey = 'library:IMAGES'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("GRID", c.getString(0))
            }

            rawDb.execSQL(
                "INSERT INTO photo_albums VALUES ('alb_1', 'Favorites', NULL, 0, 0, 1000, 1000)"
            )
            rawDb.execSQL(
                "INSERT INTO album_images VALUES ('alb_1', '/tmp/a.jpg', 'a.jpg', 120, 1000, 'image/jpeg', 'jpg', 0, '/tmp', 1000)"
            )
            rawDb.query("SELECT name FROM photo_albums WHERE id = 'alb_1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Favorites", c.getString(0))
            }
            rawDb.query("SELECT fileName FROM album_images WHERE albumId = 'alb_1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("a.jpg", c.getString(0))
            }
            openHelper.close()
        } finally {
            dbFile.delete()
        }
    }

    @Test
    fun album_repository_manages_virtual_albums_without_deleting_disk_files_and_groups_device_folders() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.db.ArcDatabase::class.java
        ).allowMainThreadQueries().build()
        val tempRoot = java.io.File(context.cacheDir, "album_repo_test_${System.nanoTime()}").apply { mkdirs() }
        val cameraDir = java.io.File(tempRoot, "Camera").apply { mkdirs() }
        val screensDir = java.io.File(tempRoot, "Screenshots").apply { mkdirs() }
        try {
            val f1 = java.io.File(cameraDir, "sunset.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val f2 = java.io.File(screensDir, "shot.png").apply { writeBytes(byteArrayOf(5, 6, 7)) }

            val item1 = FileItem(f1.absolutePath, f1.name, f1.absolutePath, false, 4L, 2000L, "image/jpeg", "jpg", false, cameraDir.absolutePath)
            val item2 = FileItem(f2.absolutePath, f2.name, f2.absolutePath, false, 3L, 3000L, "image/png", "png", false, screensDir.absolutePath)

            val repo = com.example.data.db.AlbumRepository(db.photoAlbumDao())
            val created = kotlinx.coroutines.runBlocking { repo.createAlbum("Travel", listOf(item1)).getOrThrow() }
            assertEquals("Travel", created.name)

            kotlinx.coroutines.runBlocking {
                repo.addImagesToAlbum(created.id, listOf(item2))
                repo.setAlbumCover(created.id, item2)
                repo.renameAlbum(created.id, "Trips").getOrThrow()
            }

            val entities = kotlinx.coroutines.runBlocking {
                db.photoAlbumDao().observeAlbums().first()
            }
            val crossRefs = kotlinx.coroutines.runBlocking {
                db.photoAlbumDao().observeAllAlbumImages().first()
            }
            val catalog = com.example.data.db.AlbumRepository.buildHybridCatalog(
                customEntities = entities,
                crossRefs = crossRefs,
                allIndexedImages = listOf(item1, item2)
            )

            assertEquals(1, catalog.customAlbums.size)
            assertEquals("Trips", catalog.customAlbums.first().name)
            assertEquals(2, catalog.customAlbums.first().itemCount)
            assertEquals(f2.absolutePath, catalog.customAlbums.first().coverPathOrUri)

            // Device folders auto-grouped for Camera & Screenshots
            assertEquals(2, catalog.deviceAlbums.size)
            assertTrue(catalog.deviceAlbums.any { it.name == "Camera" && it.isDeviceFolder })
            assertTrue(catalog.deviceAlbums.any { it.name == "Screenshots" && it.isDeviceFolder })

            // Removing image from album and deleting album leaves source files intact on disk
            kotlinx.coroutines.runBlocking {
                repo.removeImagesFromAlbum(created.id, listOf(item1))
                repo.deleteAlbum(created.id)
            }
            assertTrue("Original camera file must remain untouched", f1.exists())
            assertTrue("Original screenshot file must remain untouched", f2.exists())
        } finally {
            db.close()
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun gallery_library_screen_bottom_tabs_folders_and_sorting() {
        val pic1 = FileItem(
            id = "/fake/DCIM/Camera/alpha_old.jpg",
            name = "alpha_old.jpg",
            pathOrUri = "/fake/DCIM/Camera/alpha_old.jpg",
            isDirectory = false,
            sizeBytes = 1024L,
            lastModifiedMillis = 2000L,
            mimeType = "image/jpeg",
            extension = "jpg",
            isSafUri = false,
            parentPathOrUri = "/fake/DCIM/Camera",
            createdAtMillis = 2000L
        )
        val pic2 = FileItem(
            id = "/fake/Pictures/zulu_new.png",
            name = "zulu_new.png",
            pathOrUri = "/fake/Pictures/zulu_new.png",
            isDirectory = false,
            sizeBytes = 2048L,
            lastModifiedMillis = 5000L,
            mimeType = "image/png",
            extension = "png",
            isSafUri = false,
            parentPathOrUri = "/fake/Pictures",
            createdAtMillis = 5000L
        )
        val customAlbum = com.example.data.PhotoAlbum(
            id = "alb_trip",
            name = "Summer Trip",
            coverPathOrUri = pic1.pathOrUri,
            coverIsSaf = false,
            itemCount = 2,
            totalBytes = 3072L,
            isDeviceFolder = false
        )
        val deviceAlbum = com.example.data.PhotoAlbum(
            id = "device:/fake/DCIM/Camera",
            name = "Camera",
            coverPathOrUri = pic1.pathOrUri,
            coverIsSaf = false,
            itemCount = 1,
            totalBytes = 1024L,
            isDeviceFolder = true,
            folderPathOrUri = "/fake/DCIM/Camera"
        )

        val panelState = mutableStateOf(com.example.data.GalleryPanel.ALL_IMAGES)
        val activeAlbumIdState = mutableStateOf<String?>(null)
        val sortFieldState = mutableStateOf(com.example.data.SortField.DATE)
        val sortAscState = mutableStateOf(false)
        var createDialogRequested = 0

        composeTestRule.setContent {
            val libState = com.example.ui.screens.library.LibraryUiState(
                galleryPanel = panelState.value,
                activeAlbumId = activeAlbumIdState.value,
                customAlbums = listOf(customAlbum),
                deviceAlbums = listOf(deviceAlbum),
                customAlbumItemsById = mapOf(customAlbum.id to listOf(pic1, pic2)),
                deviceAlbumItemsById = mapOf(deviceAlbum.id to listOf(pic1))
            )
            com.example.ui.theme.ArcFilesTheme {
                com.example.ui.screens.library.GalleryLibraryScreen(
                    allImages = listOf(pic1, pic2),
                    libraryState = libState,
                    selectedIds = emptySet(),
                    itemTagsByPath = emptyMap(),
                    gridColumns = 3,
                    originalMediaRatio = false,
                    dragReorderEnabled = false,
                    isActivePage = true,
                    sortField = sortFieldState.value,
                    sortAscending = sortAscState.value,
                    onSortOrderChange = { f, asc ->
                        sortFieldState.value = f
                        sortAscState.value = asc
                    },
                    onBack = {},
                    onSelectPanel = {
                        panelState.value = it
                        activeAlbumIdState.value = null
                    },
                    onOpenAlbum = { albumId ->
                        activeAlbumIdState.value = albumId
                        panelState.value = if (albumId.startsWith("device:")) {
                            com.example.data.GalleryPanel.FOLDERS
                        } else {
                            com.example.data.GalleryPanel.ALBUMS
                        }
                    },
                    onCloseAlbum = { activeAlbumIdState.value = null },
                    onCreateAlbumClick = { createDialogRequested++ },
                    onRenameAlbumClick = {},
                    onDeleteAlbumClick = {},
                    onSetAlbumCover = { _, _ -> },
                    onAddToAlbumClick = {},
                    onPickImagesForAlbumClick = {},
                    onRemoveImagesFromAlbum = { _, _ -> },
                    onOpenDeviceAlbumFolder = {},
                    onPhotoClick = { _, _ -> },
                    onToggleSelect = {},
                    onSelectAll = {},
                    onClearSelection = {},
                    onFileAction = { _, _ -> },
                    onReorderImages = {},
                    onImportMediaClick = {},
                    onMountFolderClick = {}
                )
            }
        }

        // Starts on All Images with persistent bottom tab bar & all folders combined
        composeTestRule.onNodeWithTag("gallery_bottom_tab_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_tab_all_images").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_tab_albums").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_tab_folders").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_all_images_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_photo_zulu_new.png").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_photo_alpha_old.jpg").assertIsDisplayed()

        // Default is Newest (createdAt desc) -> zulu_new.png appears to the left of alpha_old.jpg
        val newBoundsDefault = composeTestRule.onNodeWithTag("gallery_photo_zulu_new.png").fetchSemanticsNode().boundsInRoot
        val oldBoundsDefault = composeTestRule.onNodeWithTag("gallery_photo_alpha_old.jpg").fetchSemanticsNode().boundsInRoot
        assertTrue("Newest photo should appear first by default", newBoundsDefault.left < oldBoundsDefault.left)

        // Tap Name sort chip -> Name A-Z puts alpha_old.jpg before zulu_new.png
        composeTestRule.onNodeWithTag("gallery_sort_name").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.SortField.NAME, sortFieldState.value)
        assertTrue(sortAscState.value)
        val oldBoundsNameAsc = composeTestRule.onNodeWithTag("gallery_photo_alpha_old.jpg").fetchSemanticsNode().boundsInRoot
        val newBoundsNameAsc = composeTestRule.onNodeWithTag("gallery_photo_zulu_new.png").fetchSemanticsNode().boundsInRoot
        assertTrue("Name A-Z should place alpha_old.jpg first", oldBoundsNameAsc.left < newBoundsNameAsc.left)

        // Tap Newest sort chip -> restores Newest (createdAt desc)
        composeTestRule.onNodeWithTag("gallery_sort_newest").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.SortField.DATE, sortFieldState.value)
        assertFalse(sortAscState.value)

        // Tap bottom Albums tab -> shows custom virtual albums only (not Camera folder)
        composeTestRule.onNodeWithTag("gallery_tab_albums").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.GalleryPanel.ALBUMS, panelState.value)
        composeTestRule.onNodeWithTag("gallery_albums_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_new_album_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_new_album_card").performClick()
        assertEquals(1, createDialogRequested)
        composeTestRule.onNode(
            hasTestTag("album_card_Camera") and hasAnyAncestor(hasTestTag("gallery_albums_grid"))
        ).assertDoesNotExist()

        composeTestRule.onNodeWithTag("album_card_Summer Trip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_card_Summer Trip").performClick()
        composeTestRule.waitForIdle()
        assertEquals("alb_trip", activeAlbumIdState.value)
        composeTestRule.onNodeWithTag("album_detail_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_detail_add_photos").assertIsDisplayed()
        // Persistent bottom tab bar remains accessible inside album detail!
        composeTestRule.onNodeWithTag("gallery_bottom_tab_bar").assertIsDisplayed()

        // Tap Folders tab directly from inside Album detail -> jumps straight to dedicated Folders grid
        composeTestRule.onNodeWithTag("gallery_tab_folders").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.GalleryPanel.FOLDERS, panelState.value)
        assertNull(activeAlbumIdState.value)
        composeTestRule.onNodeWithTag("gallery_folders_grid").assertIsDisplayed()
        composeTestRule.onNode(
            hasTestTag("album_card_Camera") and hasAnyAncestor(hasTestTag("gallery_folders_grid"))
        ).assertIsDisplayed()

        // Open Camera folder inside Gallery -> shows Camera folder detail & back returns to Folders
        composeTestRule.onNode(
            hasTestTag("album_card_Camera") and hasAnyAncestor(hasTestTag("gallery_folders_grid"))
        ).performClick()
        composeTestRule.waitForIdle()
        assertEquals("device:/fake/DCIM/Camera", activeAlbumIdState.value)
        composeTestRule.onNodeWithTag("album_detail_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_bottom_tab_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_detail_back").performClick()
        composeTestRule.waitForIdle()
        assertNull(activeAlbumIdState.value)
        assertEquals(com.example.data.GalleryPanel.FOLDERS, panelState.value)
    }

    @Test
    fun media_library_screen_filters_documents_by_type_and_toggles_view_mode() {
        val pdfFile = FileItem("/docs/report.pdf", "report.pdf", "/docs/report.pdf", false, 4096L, 2000L, "application/pdf", "pdf", false, "/docs")
        val mdFile = FileItem("/docs/readme.md", "readme.md", "/docs/readme.md", false, 512L, 1000L, "text/markdown", "md", false, "/docs")
        val allDocs = listOf(pdfFile, mdFile)

        val activeFilter = mutableStateOf("ALL")
        val viewMode = mutableStateOf(ViewMode.LIST)

        composeTestRule.setContent {
            val filtered = com.example.ui.screens.library.LibraryDomainUtils.applySubFilter(
                com.example.data.LibrarySection.DOCUMENTS,
                activeFilter.value,
                allDocs
            )
            com.example.ui.theme.ArcFilesTheme {
                com.example.ui.screens.library.MediaLibraryScreen(
                    section = com.example.data.LibrarySection.DOCUMENTS,
                    allSectionItems = allDocs,
                    filteredItems = filtered,
                    activeSubFilterId = activeFilter.value,
                    selectedIds = emptySet(),
                    renamingItemId = null,
                    itemTagsByPath = emptyMap(),
                    viewMode = viewMode.value,
                    gridColumns = 3,
                    originalMediaRatio = false,
                    monochromeBinaryIcons = false,
                    showFileExtensions = true,
                    compactListMode = false,
                    isActivePage = true,
                    onBack = {},
                    onSelectSubFilter = { activeFilter.value = it },
                    onToggleViewMode = {
                        viewMode.value = if (viewMode.value == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                    },
                    onOpenSearch = {},
                    onFileClick = {},
                    onToggleSelect = {},
                    onSelectAll = {},
                    onClearSelection = {},
                    onFileAction = { _, _ -> },
                    onConfirmInlineRename = { _, _ -> },
                    onCancelInlineRename = {},
                    onImportClick = {},
                    onMountFolderClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("library_media_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("file_row_report.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithTag("file_row_readme.md").assertIsDisplayed()

        // Filter to PDFs only
        composeTestRule.onNodeWithTag("library_filter_chip_PDF").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("file_row_report.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithTag("file_row_readme.md").assertDoesNotExist()

        // Toggle Grid mode
        composeTestRule.onNodeWithTag("library_toggle_view_mode").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("library_media_grid").assertIsDisplayed()
    }

    @Test
    fun room_migration_4_to_5_adds_media_section_and_isolates_movie_vs_image_albums() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val dbFile = java.io.File(context.cacheDir, "migration_v4_test_${System.nanoTime()}.db")
        try {
            val helperFactory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
            val v4Callback = object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS photo_albums (
                            id TEXT NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            coverPathOrUri TEXT,
                            coverIsSaf INTEGER NOT NULL,
                            sortOrder INTEGER NOT NULL,
                            createdAtMillis INTEGER NOT NULL,
                            updatedAtMillis INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS album_images (
                            albumId TEXT NOT NULL,
                            imagePathOrUri TEXT NOT NULL,
                            fileName TEXT NOT NULL,
                            sizeBytes INTEGER NOT NULL,
                            lastModifiedMillis INTEGER NOT NULL,
                            mimeType TEXT NOT NULL,
                            extension TEXT NOT NULL,
                            isSafUri INTEGER NOT NULL,
                            parentPathOrUri TEXT NOT NULL,
                            addedAtMillis INTEGER NOT NULL,
                            PRIMARY KEY(albumId, imagePathOrUri)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "INSERT INTO photo_albums VALUES ('alb_img', 'Favorites', '/tmp/a.jpg', 0, 0, 1000, 1000)"
                    )
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }
            val openHelper = helperFactory.create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbFile.absolutePath)
                    .callback(v4Callback)
                    .build()
            )
            val rawDb = openHelper.writableDatabase
            com.example.data.db.ArcDatabase.MIGRATION_4_5.migrate(rawDb)

            rawDb.query("SELECT mediaSection FROM photo_albums WHERE id = 'alb_img'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("IMAGES", c.getString(0))
            }
            openHelper.close()
        } finally {
            dbFile.delete()
        }

        // Verify AlbumRepository creates same-named albums across IMAGES vs MOVIES without collision
        val memDb = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.db.ArcDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repo = com.example.data.db.AlbumRepository(memDb.photoAlbumDao())
            val pic = FileItem("/pics/a.jpg", "a.jpg", "/pics/a.jpg", false, 100L, 1000L, "image/jpeg", "jpg", false, "/pics")
            val vid = FileItem("/movies/trailer.mp4", "trailer.mp4", "/movies/trailer.mp4", false, 2000L, 2000L, "video/mp4", "mp4", false, "/movies")

            kotlinx.coroutines.runBlocking {
                repo.createAlbum("Watchlist", listOf(pic), com.example.data.LibrarySection.IMAGES).getOrThrow()
                repo.createAlbum("Watchlist", listOf(vid), com.example.data.LibrarySection.MOVIES).getOrThrow()
            }

            val entities = kotlinx.coroutines.runBlocking { memDb.photoAlbumDao().observeAlbums().first() }
            val refs = kotlinx.coroutines.runBlocking { memDb.photoAlbumDao().observeAllAlbumImages().first() }

            val imgCatalog = com.example.data.db.AlbumRepository.buildHybridCatalog(
                customEntities = entities,
                crossRefs = refs,
                allIndexedImages = listOf(pic),
                mediaSection = com.example.data.LibrarySection.IMAGES
            )
            val movCatalog = com.example.data.db.AlbumRepository.buildHybridCatalog(
                customEntities = entities,
                crossRefs = refs,
                allIndexedImages = listOf(vid),
                mediaSection = com.example.data.LibrarySection.MOVIES
            )

            assertEquals(1, imgCatalog.customAlbums.size)
            assertEquals(com.example.data.LibrarySection.IMAGES, imgCatalog.customAlbums.first().mediaSection)
            assertEquals("movies", movCatalog.deviceAlbums.first().name)
            assertEquals(1, movCatalog.customAlbums.size)
            assertEquals(com.example.data.LibrarySection.MOVIES, movCatalog.customAlbums.first().mediaSection)
            assertEquals("trailer.mp4", movCatalog.customAlbumItemsById[movCatalog.customAlbums.first().id]?.first()?.name)
        } finally {
            memDb.close()
        }
    }

    @Test
    fun movies_gallery_screen_bottom_tabs_folders_and_sorting() {
        val vidOld = FileItem(
            id = "/fake/Movies/Cinema/alpha_trailer.mp4",
            name = "alpha_trailer.mp4",
            pathOrUri = "/fake/Movies/Cinema/alpha_trailer.mp4",
            isDirectory = false,
            sizeBytes = 2_048_000L,
            lastModifiedMillis = 2000L,
            mimeType = "video/mp4",
            extension = "mp4",
            isSafUri = false,
            parentPathOrUri = "/fake/Movies/Cinema",
            createdAtMillis = 2000L
        )
        val vidNew = FileItem(
            id = "/fake/Downloads/zulu_clip.mkv",
            name = "zulu_clip.mkv",
            pathOrUri = "/fake/Downloads/zulu_clip.mkv",
            isDirectory = false,
            sizeBytes = 5_120_000L,
            lastModifiedMillis = 7000L,
            mimeType = "video/x-matroska",
            extension = "mkv",
            isSafUri = false,
            parentPathOrUri = "/fake/Downloads",
            createdAtMillis = 7000L
        )
        val customMovieAlbum = com.example.data.PhotoAlbum(
            id = "alb_watch",
            name = "Weekend Watch",
            mediaSection = com.example.data.LibrarySection.MOVIES,
            isDeviceFolder = false,
            coverPathOrUri = vidNew.pathOrUri,
            itemCount = 2,
            totalBytes = vidOld.sizeBytes + vidNew.sizeBytes
        )
        val folderMovieAlbum = com.example.data.PhotoAlbum(
            id = "device:/fake/Movies/Cinema",
            name = "Cinema",
            mediaSection = com.example.data.LibrarySection.MOVIES,
            isDeviceFolder = true,
            folderPathOrUri = "/fake/Movies/Cinema",
            coverPathOrUri = vidOld.pathOrUri,
            itemCount = 1,
            totalBytes = vidOld.sizeBytes
        )

        val panelState = mutableStateOf(com.example.data.GalleryPanel.ALL_IMAGES)
        val activeAlbumIdState = mutableStateOf<String?>(null)
        val sortFieldState = mutableStateOf(com.example.data.SortField.DATE)
        val sortAscState = mutableStateOf(false)
        var createDialogRequested = 0

        composeTestRule.setContent {
            val libState = com.example.ui.screens.library.LibraryUiState(
                galleryPanelsBySection = mapOf(com.example.data.LibrarySection.MOVIES to panelState.value),
                activeAlbumIdsBySection = mapOf(com.example.data.LibrarySection.MOVIES to activeAlbumIdState.value),
                customAlbumsBySection = mapOf(com.example.data.LibrarySection.MOVIES to listOf(customMovieAlbum)),
                deviceAlbumsBySection = mapOf(com.example.data.LibrarySection.MOVIES to listOf(folderMovieAlbum)),
                customAlbumItemsBySection = mapOf(
                    com.example.data.LibrarySection.MOVIES to mapOf(customMovieAlbum.id to listOf(vidOld, vidNew))
                ),
                deviceAlbumItemsBySection = mapOf(
                    com.example.data.LibrarySection.MOVIES to mapOf(folderMovieAlbum.id to listOf(vidOld))
                )
            )
            com.example.ui.theme.ArcFilesTheme {
                com.example.ui.screens.library.GalleryLibraryScreen(
                    allImages = listOf(vidOld, vidNew),
                    libraryState = libState,
                    selectedIds = emptySet(),
                    itemTagsByPath = emptyMap(),
                    gridColumns = 3,
                    originalMediaRatio = false,
                    dragReorderEnabled = false,
                    isActivePage = true,
                    section = com.example.data.LibrarySection.MOVIES,
                    sortField = sortFieldState.value,
                    sortAscending = sortAscState.value,
                    onSortOrderChange = { f, asc ->
                        sortFieldState.value = f
                        sortAscState.value = asc
                    },
                    onBack = {},
                    onSelectPanel = {
                        panelState.value = it
                        activeAlbumIdState.value = null
                    },
                    onOpenAlbum = { albumId ->
                        activeAlbumIdState.value = albumId
                        panelState.value = if (albumId.startsWith("device:")) {
                            com.example.data.GalleryPanel.FOLDERS
                        } else {
                            com.example.data.GalleryPanel.ALBUMS
                        }
                    },
                    onCloseAlbum = { activeAlbumIdState.value = null },
                    onCreateAlbumClick = { createDialogRequested++ },
                    onRenameAlbumClick = {},
                    onDeleteAlbumClick = {},
                    onSetAlbumCover = { _, _ -> },
                    onAddToAlbumClick = {},
                    onPickImagesForAlbumClick = {},
                    onRemoveImagesFromAlbum = { _, _ -> },
                    onOpenDeviceAlbumFolder = {},
                    onPhotoClick = { _, _ -> },
                    onToggleSelect = {},
                    onSelectAll = {},
                    onClearSelection = {},
                    onFileAction = { _, _ -> },
                    onReorderImages = {},
                    onImportMediaClick = {},
                    onMountFolderClick = {}
                )
            }
        }

        // Bottom tab bar displays "All Videos", "Albums", "Folders"
        composeTestRule.onNodeWithTag("gallery_bottom_tab_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("All Videos").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_tab_albums").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_tab_folders").assertIsDisplayed()

        // Both videos from different folders appear in All Videos sorted Newest first
        composeTestRule.onNodeWithTag("gallery_photo_zulu_clip.mkv").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_photo_alpha_trailer.mp4").assertIsDisplayed()
        val newBounds = composeTestRule.onNodeWithTag("gallery_photo_zulu_clip.mkv").fetchSemanticsNode().boundsInRoot
        val oldBounds = composeTestRule.onNodeWithTag("gallery_photo_alpha_trailer.mp4").fetchSemanticsNode().boundsInRoot
        assertTrue("Newest video must appear first by default", newBounds.left < oldBounds.left)

        // Tap Name sort chip -> A-Z places alpha_trailer.mp4 first
        composeTestRule.onNodeWithTag("gallery_sort_name").performClick()
        composeTestRule.waitForIdle()
        val oldBoundsAsc = composeTestRule.onNodeWithTag("gallery_photo_alpha_trailer.mp4").fetchSemanticsNode().boundsInRoot
        val newBoundsAsc = composeTestRule.onNodeWithTag("gallery_photo_zulu_clip.mkv").fetchSemanticsNode().boundsInRoot
        assertTrue("Name A-Z should place alpha_trailer.mp4 first", oldBoundsAsc.left < newBoundsAsc.left)

        // Switch to Albums tab -> opens custom movie album
        composeTestRule.onNodeWithTag("gallery_tab_albums").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gallery_albums_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_card_Weekend Watch").performClick()
        composeTestRule.waitForIdle()
        assertEquals("alb_watch", activeAlbumIdState.value)
        composeTestRule.onNodeWithTag("album_detail_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_bottom_tab_bar").assertIsDisplayed()

        // Jump straight to Folders tab from Album detail -> shows Cinema folder
        composeTestRule.onNodeWithTag("gallery_tab_folders").performClick()
        composeTestRule.waitForIdle()
        assertNull(activeAlbumIdState.value)
        composeTestRule.onNode(
            hasTestTag("album_card_Cinema") and hasAnyAncestor(hasTestTag("gallery_folders_grid"))
        ).assertIsDisplayed()
    }

    @Test
    fun isolated_cache_manager_inspects_and_clears_buckets_independently() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val imgDir = com.example.data.ArcCacheManager.bucketDirectory(context, com.example.data.CacheBucket.GALLERY_IMAGES)
        val movDir = com.example.data.ArcCacheManager.bucketDirectory(context, com.example.data.CacheBucket.MOVIE_VIDEOS)
        val browserDir = com.example.data.ArcCacheManager.bucketDirectory(context, com.example.data.CacheBucket.BROWSER_MEDIA)

        assertTrue(imgDir.absolutePath.endsWith("library_thumbs/images"))
        assertTrue(movDir.absolutePath.endsWith("library_thumbs/movies"))
        assertTrue(browserDir.absolutePath.endsWith("arc_image_cache"))

        try {
            java.io.File(imgDir, "thumb_1.bin").writeBytes(ByteArray(1200))
            java.io.File(movDir, "poster_1.bin").writeBytes(ByteArray(3500))
            java.io.File(movDir, "poster_2.bin").writeBytes(ByteArray(1500))
            java.io.File(browserDir, "preview_1.bin").writeBytes(ByteArray(900))

            val initialStats = kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.inspectAll(context)
            }.associateBy { it.bucket }

            assertEquals(1200L, initialStats[com.example.data.CacheBucket.GALLERY_IMAGES]?.sizeBytes)
            assertEquals(1, initialStats[com.example.data.CacheBucket.GALLERY_IMAGES]?.fileCount)
            assertEquals(5000L, initialStats[com.example.data.CacheBucket.MOVIE_VIDEOS]?.sizeBytes)
            assertEquals(2, initialStats[com.example.data.CacheBucket.MOVIE_VIDEOS]?.fileCount)
            assertEquals(900L, initialStats[com.example.data.CacheBucket.BROWSER_MEDIA]?.sizeBytes)

            // Clear ONLY MOVIE_VIDEOS -> Images & Browser caches remain untouched
            val freedMovies = kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.clearBucket(context, com.example.data.CacheBucket.MOVIE_VIDEOS)
            }
            assertEquals(5000L, freedMovies)

            val afterMoviesClear = kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.inspectAll(context)
            }.associateBy { it.bucket }

            assertEquals(0L, afterMoviesClear[com.example.data.CacheBucket.MOVIE_VIDEOS]?.sizeBytes)
            assertEquals(0, afterMoviesClear[com.example.data.CacheBucket.MOVIE_VIDEOS]?.fileCount)
            assertEquals(1200L, afterMoviesClear[com.example.data.CacheBucket.GALLERY_IMAGES]?.sizeBytes)
            assertEquals(900L, afterMoviesClear[com.example.data.CacheBucket.BROWSER_MEDIA]?.sizeBytes)

            // Clear all remaining buckets
            val freedRest = kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.clearAll(context)
            }
            assertEquals(2100L, freedRest)
            val afterAllClear = kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.inspectAll(context)
            }
            assertTrue(afterAllClear.all { it.sizeBytes == 0L && it.fileCount == 0 })
        } finally {
            kotlinx.coroutines.runBlocking { com.example.data.ArcCacheManager.clearAll(context) }
        }
    }

    @Test
    fun storage_manager_isolates_plugin_cache_vs_plugin_data_and_section_albums() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val imgThumbDir = com.example.data.ArcCacheManager.bucketDirectory(context, com.example.data.CacheBucket.GALLERY_IMAGES)
        val vpCacheDir = com.example.data.ArcCacheManager.pluginCacheDirectory(context, "video_player")
        val vpDataDir = com.example.data.ArcCacheManager.pluginDataDirectory(context, "video_player")
        val pdfCacheDir = com.example.data.ArcCacheManager.pluginCacheDirectory(context, "pdf_reader")
        val pdfDataDir = com.example.data.ArcCacheManager.pluginDataDirectory(context, "pdf_reader")
        val openStageDir = java.io.File(context.cacheDir, "open_cache").apply { mkdirs() }
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.db.ArcDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            java.io.File(imgThumbDir, "photo_a.jpg").writeBytes(ByteArray(500))
            java.io.File(vpCacheDir, "scrub_sprite.bin").writeBytes(ByteArray(2400))
            java.io.File(vpDataDir, "resume_positions.json").writeBytes(ByteArray(800))
            java.io.File(pdfCacheDir, "page_1.png").writeBytes(ByteArray(1600))
            java.io.File(pdfDataDir, "reading_marks.json").writeBytes(ByteArray(300))
            val stagedFile = java.io.File(openStageDir, "temp_preview.pdf").apply { writeBytes(ByteArray(1100)) }

            val snapshot1 = kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.inspectFullSnapshot(
                    context = context,
                    metadata = com.example.data.StorageMetadataCounts(
                        imagesAlbumCount = 2,
                        moviesAlbumCount = 1,
                        folderLayoutCount = 4,
                        defaultOpenAppsCount = 3,
                        bookmarksCount = 2,
                        taggedItemsAndCustomTagsCount = 5
                    )
                )
            }
            val vpStats1 = snapshot1.plugins.first { it.spec.id == "video_player" }
            val pdfStats1 = snapshot1.plugins.first { it.spec.id == "pdf_reader" }
            assertEquals(2400L, vpStats1.cacheBytes)
            assertEquals(800L, vpStats1.dataBytes)
            assertEquals(1600L, pdfStats1.cacheBytes)
            assertEquals(300L, pdfStats1.dataBytes)
            assertEquals(1100L, snapshot1.entryFor(com.example.data.ManagedStorageId.COMMON_STAGING_TEMP).sizeBytes)

            // 1. Clearing video_player CACHE must NOT touch video_player USER DATA or pdf_reader or imgThumbDir
            val freedVpCache = kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.clearPluginCache(context, "video_player")
            }
            assertEquals(2400L, freedVpCache)

            val snapshot2 = kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.inspectFullSnapshot(context, com.example.data.StorageMetadataCounts())
            }
            val vpStats2 = snapshot2.plugins.first { it.spec.id == "video_player" }
            val pdfStats2 = snapshot2.plugins.first { it.spec.id == "pdf_reader" }
            assertEquals(0L, vpStats2.cacheBytes)
            assertEquals(800L, vpStats2.dataBytes)
            assertEquals(1600L, pdfStats2.cacheBytes)
            assertEquals(500L, snapshot2.entryFor(com.example.data.ManagedStorageId.LIB_IMAGES_THUMBS).sizeBytes)

            // 2. Clearing Staging Temp clears open_cache without touching plugin data or library thumbs
            val freedTemp = kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.clearStagingTempCache(context)
            }
            assertEquals(1100L, freedTemp)
            assertFalse(stagedFile.exists())
            assertTrue(java.io.File(vpDataDir, "resume_positions.json").exists())

            // 3. Clearing ONLY Images Albums keeps Movies Albums intact
            val albumRepo = com.example.data.db.AlbumRepository(db.photoAlbumDao())
            val pic = FileItem("/p/a.jpg", "a.jpg", "/p/a.jpg", false, 100L, 1000L, "image/jpeg", "jpg", false, "/p")
            val vid = FileItem("/v/m.mp4", "m.mp4", "/v/m.mp4", false, 500L, 1000L, "video/mp4", "mp4", false, "/v")
            kotlinx.coroutines.runBlocking {
                albumRepo.createAlbum("ImgOnly", listOf(pic), com.example.data.LibrarySection.IMAGES).getOrThrow()
                albumRepo.createAlbum("MovOnly", listOf(vid), com.example.data.LibrarySection.MOVIES).getOrThrow()
                albumRepo.clearAlbumsForSection(com.example.data.LibrarySection.IMAGES)
            }
            val remainingAlbums = kotlinx.coroutines.runBlocking { db.photoAlbumDao().observeAlbums().first() }
            assertEquals(1, remainingAlbums.size)
            assertEquals("MovOnly", remainingAlbums.first().name)
            assertEquals("MOVIES", remainingAlbums.first().mediaSection)

            // 4. clearAllRegenerableCaches preserves plugin user DATA while clearing plugin & library CACHES
            kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.clearAllRegenerableCaches(context)
            }
            val afterSafeClear = kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.inspectFullSnapshot(context, com.example.data.StorageMetadataCounts())
            }
            assertEquals(0L, afterSafeClear.totalCacheBytes)
            assertEquals(800L, afterSafeClear.plugins.first { it.spec.id == "video_player" }.dataBytes)
            assertEquals(300L, afterSafeClear.plugins.first { it.spec.id == "pdf_reader" }.dataBytes)
        } finally {
            db.close()
            kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.clearAllRegenerableCaches(context)
                com.example.data.ArcCacheManager.clearPluginData(context, "video_player")
                com.example.data.ArcCacheManager.clearPluginData(context, "pdf_reader")
            }
        }
    }

    @Test
    fun settings_screen_opens_dedicated_cache_manager_page_and_clears_granularity() {
        var openedCacheManager = false
        val showCacheManager = androidx.compose.runtime.mutableStateOf(false)
        val stats = listOf(
            com.example.data.CacheBucketStats(com.example.data.CacheBucket.GALLERY_IMAGES, 2_097_152L, 14)
        )
        val clearedEntries = mutableListOf<com.example.data.ManagedStorageId>()
        val clearedPluginCaches = mutableListOf<String>()
        val clearedPluginData = mutableListOf<String>()
        var clearedAll = false
        var backedOut = false

        val snapshot = com.example.data.AppStorageSnapshot(
            entries = listOf(
                com.example.data.ManagedStorageEntry(com.example.data.ManagedStorageId.LIB_IMAGES_THUMBS, 2_000_000L, 12),
                com.example.data.ManagedStorageEntry(com.example.data.ManagedStorageId.LIB_MOVIES_THUMBS, 4_000_000L, 8),
                com.example.data.ManagedStorageEntry(com.example.data.ManagedStorageId.LIB_IMAGES_ALBUMS, 0L, 2),
                com.example.data.ManagedStorageEntry(com.example.data.ManagedStorageId.LIB_MOVIES_ALBUMS, 0L, 1),
                com.example.data.ManagedStorageEntry(com.example.data.ManagedStorageId.COMMON_BROWSER_PREVIEWS, 512_000L, 5),
                com.example.data.ManagedStorageEntry(com.example.data.ManagedStorageId.COMMON_STAGING_TEMP, 256_000L, 2),
                com.example.data.ManagedStorageEntry(com.example.data.ManagedStorageId.COMMON_FOLDER_LAYOUTS, 0L, 3),
                com.example.data.ManagedStorageEntry(com.example.data.ManagedStorageId.COMMON_DEFAULT_APPS, 0L, 1),
                com.example.data.ManagedStorageEntry(com.example.data.ManagedStorageId.COMMON_BOOKMARKS, 0L, 2),
                com.example.data.ManagedStorageEntry(com.example.data.ManagedStorageId.COMMON_FILE_TAGS, 0L, 4)
            ),
            plugins = com.example.data.ArcPluginRegistry.plugins.map { spec ->
                when (spec.id) {
                    "video_player" -> com.example.data.PluginStorageStats(spec, 3_000_000L, 6, 120_000L, 2)
                    "pdf_reader" -> com.example.data.PluginStorageStats(spec, 1_500_000L, 3, 64_000L, 1)
                    else -> com.example.data.PluginStorageStats(spec, 64_000L, 1, 8_000L, 1)
                }
            }
        )

        composeTestRule.setContent {
            com.example.ui.theme.ArcFilesTheme {
                if (!showCacheManager.value) {
                    com.example.ui.screens.settings.SettingsScreen(
                        prefs = com.example.data.ExplorerPreferences(),
                        drives = emptyList(),
                        indexedFileCount = 42,
                        hasAllFilesAccess = true,
                        cacheStats = stats,
                        totalManagedCacheBytes = 6_291_456L,
                        onOpenCacheManager = {
                            openedCacheManager = true
                            showCacheManager.value = true
                        },
                        onBack = {},
                        onThemeChange = {},
                        onMonochromeChange = {},
                        onShowFileExtensionsChange = {},
                        onCompactListChange = {},
                        onViewModeChange = {},
                        onGridColumnsChange = {},
                        onOriginalMediaRatioChange = {},
                        onSortFieldChange = {},
                        onSortAscendingChange = {},
                        onGroupByChange = {},
                        onFoldersFirstChange = {},
                        onShowHiddenChange = {},
                        onShowFolderCountsChange = {},
                        onConfirmBeforeDeleteChange = {},
                        onRequestAllFilesAccess = {},
                        onMountFolderClick = {},
                        onUnmountSafDrive = {},
                        onReindexStorage = {},
                        onOpenWorkspace = {},
                        onResetDefaults = {}
                    )
                } else {
                    com.example.ui.screens.settings.CacheDataManagerScreen(
                        snapshot = snapshot,
                        onBack = {
                            backedOut = true
                            showCacheManager.value = false
                        },
                        onRefresh = {},
                        onClearStorageEntry = { clearedEntries.add(it) },
                        onClearPluginCache = { clearedPluginCaches.add(it) },
                        onClearPluginData = { clearedPluginData.add(it) },
                        onClearAllCaches = { clearedAll = true }
                    )
                }
            }
        }

        val settingsList = composeTestRule.onNodeWithTag("settings_screen_list")
        settingsList.performScrollToNode(hasTestTag("settings_open_cache_manager"))
        composeTestRule.onNodeWithTag("settings_open_cache_manager").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_open_cache_manager").performClick()
        composeTestRule.waitForIdle()
        assertTrue(openedCacheManager)
        assertTrue(showCacheManager.value)

        // Top Safe Clear All Caches button works directly
        composeTestRule.onNodeWithTag("cache_manager_clear_all_caches").performClick()
        assertTrue(clearedAll)

        // Filter to Library and clear Movies Thumbnails directly
        val cacheList = composeTestRule.onNodeWithTag("cache_manager_list")
        composeTestRule.onNodeWithTag("cache_filter_library").performClick()
        composeTestRule.waitForIdle()
        cacheList.performScrollToNode(hasTestTag("clear_storage_lib_movies_thumbs"))
        composeTestRule.onNodeWithTag("clear_storage_lib_movies_thumbs").performClick()
        composeTestRule.waitForIdle()
        assertTrue(com.example.data.ManagedStorageId.LIB_MOVIES_THUMBS in clearedEntries)

        // Filter to Plugins: clear Video Player CACHE directly, confirm Video Player DATA via modal
        composeTestRule.onNodeWithTag("cache_filter_plugins").performClick()
        composeTestRule.waitForIdle()
        cacheList.performScrollToNode(hasTestTag("clear_plugin_data_video_player"))
        composeTestRule.onNodeWithTag("clear_plugin_cache_video_player").performClick()
        assertEquals(listOf("video_player"), clearedPluginCaches)
        assertTrue(clearedPluginData.isEmpty())

        composeTestRule.onNodeWithTag("clear_plugin_data_video_player").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_clear_data_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_clear_data_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("video_player"), clearedPluginData)

        // Filter to Folders & Common: confirm Folder Layouts reset
        cacheList.performScrollToNode(hasTestTag("cache_filter_all"))
        composeTestRule.onNodeWithTag("cache_filter_folders_common").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        cacheList.performScrollToNode(hasTestTag("clear_storage_common_folder_layouts"))
        composeTestRule.onNodeWithTag("clear_storage_common_folder_layouts").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_clear_data_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(com.example.data.ManagedStorageId.COMMON_FOLDER_LAYOUTS in clearedEntries)

        // Back button returns cleanly
        cacheList.performScrollToNode(hasTestTag("cache_manager_back_button"))
        composeTestRule.onNodeWithTag("cache_manager_back_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(backedOut)
    }

    @Test
    fun arc_plugin_contract_routes_files_and_runs_isolated_cleanup_hooks() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

        val videoFile = FileItem(
            id = "v1", name = "movie.mkv", pathOrUri = "/tmp/movie.mkv", isDirectory = false,
            sizeBytes = 1024L, lastModifiedMillis = 100L, mimeType = "video/x-matroska",
            extension = "mkv", isSafUri = false, parentPathOrUri = "/tmp"
        )
        val pdfFile = FileItem(
            id = "p1", name = "spec.pdf", pathOrUri = "/tmp/spec.pdf", isDirectory = false,
            sizeBytes = 1024L, lastModifiedMillis = 100L, mimeType = "application/pdf",
            extension = "pdf", isSafUri = false, parentPathOrUri = "/tmp"
        )
        val mdFile = FileItem(
            id = "m1", name = "README.md", pathOrUri = "/tmp/README.md", isDirectory = false,
            sizeBytes = 512L, lastModifiedMillis = 100L, mimeType = "text/markdown",
            extension = "md", isSafUri = false, parentPathOrUri = "/tmp"
        )
        val zipFile = FileItem(
            id = "z1", name = "pack.zip", pathOrUri = "/tmp/pack.zip", isDirectory = false,
            sizeBytes = 2048L, lastModifiedMillis = 100L, mimeType = "application/zip",
            extension = "zip", isSafUri = false, parentPathOrUri = "/tmp"
        )

        assertEquals("video_player", com.example.plugins.ArcPluginManager.resolvePluginFor(videoFile)?.id)
        assertEquals("pdf_reader", com.example.plugins.ArcPluginManager.resolvePluginFor(pdfFile)?.id)
        assertEquals("text_editor", com.example.plugins.ArcPluginManager.resolveActiveHandler(mdFile)?.id)
        assertEquals("archive_tools", com.example.plugins.ArcPluginManager.resolveActiveHandler(zipFile)?.id)

        // Verify preference-backed user data is inspected & cleaned by plugin cleanup hooks independently of cache
        val pdfPrefs = com.example.core.plugin.ArcPluginScope.pluginPreferences(context, "pdf_reader")
        pdfPrefs.edit().putInt("last_page_spec", 18).apply()

        var customCacheCleared = false
        var customDataCleared = false
        val customPluginId = "test_audio_plugin"
        val customPlugin = object : com.example.core.plugin.ArcPlugin {
            override val role = com.example.core.plugin.ArcPluginRole.PLAYER
            override val supportedExtensions = setOf("flac")
            override val supportedMimePrefixes = setOf("audio/")
            override val storageSpec = com.example.data.ArcPluginStorageSpec(
                id = customPluginId,
                title = "Audio Plugin",
                subtitle = "Test",
                cacheRelativeDir = "plugins/$customPluginId",
                dataRelativeDir = "plugins/$customPluginId",
                cacheDescription = "Waveforms",
                dataDescription = "Equalizer",
                isActiveBuiltin = true
            )
            override suspend fun inspectExtraStorage(context: android.content.Context) =
                com.example.core.plugin.PluginExtraStorageMetrics(dataItems = 2)

            override suspend fun onClearCache(context: android.content.Context): Long {
                customCacheCleared = true
                return 0L
            }

            override suspend fun onClearUserData(context: android.content.Context): Long {
                customDataCleared = true
                return super.onClearUserData(context)
            }
        }

        com.example.plugins.ArcPluginManager.register(customPlugin)
        try {
            val snapshot = kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.inspectFullSnapshot(context, com.example.data.StorageMetadataCounts())
            }
            val pdfStats = snapshot.plugins.first { it.spec.id == "pdf_reader" }
            assertEquals(1, pdfStats.dataFileCount)
            val customStats = snapshot.plugins.first { it.spec.id == customPluginId }
            assertEquals(2, customStats.dataFileCount)

            kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.clearPluginCache(context, customPluginId)
            }
            assertTrue(customCacheCleared)
            assertFalse(customDataCleared)
            assertEquals(18, pdfPrefs.getInt("last_page_spec", -1))

            kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.clearPluginData(context, customPluginId)
                com.example.data.ArcCacheManager.clearPluginData(context, "pdf_reader")
            }
            assertTrue(customDataCleared)
            assertEquals(-1, pdfPrefs.getInt("last_page_spec", -1))
        } finally {
            com.example.plugins.ArcPluginManager.unregisterDynamic(customPluginId)
            pdfPrefs.edit().clear().apply()
        }
    }

    @Test
    fun note_editor_plugin_supports_multitab_drawer_undo_redo_find_replace_and_dialogs() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val tempDir = File(context.cacheDir, "note_editor_ui_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val notesFile = File(tempDir, "notes.md").apply {
            writeText("# Hello Arc\nLine two body")
        }
        val fileItem = FileItem(
            id = notesFile.absolutePath,
            name = notesFile.name,
            pathOrUri = notesFile.absolutePath,
            parentPathOrUri = tempDir.absolutePath,
            isSafUri = false,
            isDirectory = false,
            sizeBytes = notesFile.length(),
            lastModifiedMillis = notesFile.lastModified(),
            extension = "md",
            mimeType = "text/markdown"
        )
        var savedContent = ""
        try {
            composeTestRule.setContent {
                com.example.ui.theme.ArcFilesTheme {
                    com.example.plugins.editor.TextEditorPluginHost(
                        item = fileItem,
                        textContent = notesFile.readText(),
                        defaultSaveParentPathOrUri = tempDir.absolutePath,
                        defaultSaveIsSaf = false,
                        onDismiss = {},
                        onSaveText = { savedContent = it },
                        onWorkspaceModified = {},
                        onShare = {},
                        onOpenWith = {}
                    )
                }
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("text_editor_plugin_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("editor_subbar_filename").assertTextContains("notes.md", substring = true)
            composeTestRule.onNodeWithTag("editor_subbar_encoding").assertTextContains("UTF-8")
            composeTestRule.onNodeWithTag("editor_subbar_highlight").assertTextContains("Markdown")

            // Toggle inline Markdown preview from subbar and back
            composeTestRule.onNodeWithTag("editor_subbar_preview_toggle").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_markdown_preview").assertIsDisplayed()
            composeTestRule.onNodeWithTag("editor_subbar_preview_toggle").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_text_field").assertIsDisplayed()

            // Toggle Read-Only lock on and back off
            composeTestRule.onNodeWithTag("editor_readonly_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_subbar_readonly_badge").assertIsDisplayed()
            composeTestRule.onNodeWithTag("editor_readonly_button").performClick()
            composeTestRule.waitForIdle()

            // Edit text, Undo, Redo via overflow, then Save to disk
            composeTestRule.onNodeWithTag("editor_text_field").performTextReplacement("# Hello Arc\nUpdated line two")
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_subbar_filename").assertTextContains("* notes.md", substring = true)

            composeTestRule.onNodeWithTag("editor_undo_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_text_field").assertTextContains("Line two body", substring = true)

            composeTestRule.onNodeWithTag("editor_overflow_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("menu_redo").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_text_field").assertTextContains("Updated line two", substring = true)

            composeTestRule.onNodeWithTag("editor_save_button").performClick()
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                savedContent.contains("Updated line two")
            }
            assertEquals("# Hello Arc\nUpdated line two", notesFile.readText())

            // Find and Replace via Overflow menu
            composeTestRule.onNodeWithTag("editor_overflow_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("menu_find_replace").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_find_bar").assertIsDisplayed()
            composeTestRule.onNodeWithTag("editor_find_input").performTextReplacement("Updated")
            composeTestRule.onNodeWithTag("editor_replace_input").performTextReplacement("Final")
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_find_count").assertTextContains("1/1")
            composeTestRule.onNodeWithTag("editor_replace_all_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_text_field").assertTextContains("Final line two", substring = true)
            composeTestRule.onNodeWithTag("editor_find_close").performClick()
            composeTestRule.waitForIdle()

            // Document Info modal via Overflow
            composeTestRule.onNodeWithTag("editor_overflow_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("menu_doc_info").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_doc_info_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("editor_doc_info_close").performClick()
            composeTestRule.waitForIdle()

            // Add new untitled tab with '+' and verify both tabs appear in the left slide-out drawer
            composeTestRule.onNodeWithTag("editor_new_tab_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_subbar_filename").assertTextContains("Untitled-1.txt", substring = true)

            composeTestRule.onNodeWithTag("editor_drawer_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_open_files_drawer").assertIsDisplayed()
            composeTestRule.onNodeWithTag("editor_drawer_tab_notes.md").assertIsDisplayed()
            composeTestRule.onNodeWithTag("editor_drawer_tab_Untitled-1.txt").assertIsDisplayed()

            // Switch back to notes.md tab from drawer
            composeTestRule.onNodeWithTag("editor_drawer_tab_notes.md").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_subbar_filename").assertTextContains("notes.md", substring = true)
        } finally {
            kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.clearPluginCache(context, com.example.plugins.editor.TextEditorPlugin.ID)
                com.example.data.ArcCacheManager.clearPluginData(context, com.example.plugins.editor.TextEditorPlugin.ID)
            }
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun note_editor_controller_handles_save_as_json_run_last_opened_and_isolated_storage_cleanup() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val tempDir = File(context.cacheDir, "note_editor_ctrl_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val jsonFile = File(tempDir, "config.json").apply {
            writeText("{\"app\":\"Arc\",\"version\":2}")
        }
        val jsonItem = FileItem(
            id = jsonFile.absolutePath,
            name = jsonFile.name,
            pathOrUri = jsonFile.absolutePath,
            parentPathOrUri = tempDir.absolutePath,
            isSafUri = false,
            isDirectory = false,
            sizeBytes = jsonFile.length(),
            lastModifiedMillis = jsonFile.lastModified(),
            extension = "json",
            mimeType = "application/json"
        )

        val storage = com.example.plugins.editor.EditorStorageRepository(context)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
        try {
            val controller = com.example.plugins.editor.EditorSessionController(
                storage = storage,
                scope = scope,
                defaultSaveParentPathOrUri = tempDir.absolutePath,
                defaultSaveIsSaf = false,
                onPrimaryFileSaved = { _, _ -> },
                onWorkspaceModified = {}
            )

            controller.openInitialFile(jsonItem, jsonFile.readText())
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            // Verify theme + word wrap persist inside isolated plugin preferences
            controller.selectThemePreset(com.example.plugins.editor.EditorThemePreset.MONOKAI)
            controller.toggleWordWrap()
            assertEquals(com.example.plugins.editor.EditorThemePreset.MONOKAI, storage.loadThemePreset())
            assertFalse(storage.loadWordWrap())

            // Verify Run JSON validator formats minified JSON cleanly
            controller.runActiveDocument()
            val runOutput = controller.uiState.value.runOutput
            assertNotNull(runOutput)
            assertEquals(com.example.plugins.editor.RunOutputKind.JSON_VALIDATOR, runOutput?.kind)
            assertTrue(runOutput?.statusBanner?.startsWith("Valid JSON") == true)
            controller.applyFormattedJsonFromRun()
            assertTrue(controller.uiState.value.activeTab?.value?.text?.contains("\n") == true)

            // Verify Save As writes a brand-new file into tempDir
            controller.openSaveAsDialog()
            controller.commitSaveAs("config_copy.json", tempDir.absolutePath, false)
            val copyFile = File(tempDir, "config_copy.json")
            val cacheDir = com.example.plugins.editor.TextEditorPlugin.cacheDirectory(context)
            val dataDir = com.example.plugins.editor.TextEditorPlugin.dataDirectory(context)
            val deadline = System.currentTimeMillis() + 5000L
            while (System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                if (copyFile.exists() &&
                    File(dataDir, "last_opened.json").exists() &&
                    cacheDir.walkTopDown().any { it.isFile }
                ) {
                    break
                }
                Thread.sleep(20)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertTrue(copyFile.exists())
            assertTrue(copyFile.readText().contains("\"app\": \"Arc\""))

            // Verify isolated Cache vs User Data directories
            assertTrue(File(dataDir, "last_opened.json").exists())
            assertTrue((cacheDir.walkTopDown().count { it.isFile }) > 0)

            // Stop active controller coroutines so background last_opened writes don't race cleanup checks
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            Thread.sleep(60)

            // Clearing Cache wipes preview_cache ONLY; Last Opened and theme preferences remain intact
            kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.clearPluginCache(context, com.example.plugins.editor.TextEditorPlugin.ID)
            }
            assertEquals(0, cacheDir.walkTopDown().count { it.isFile })
            assertTrue(File(dataDir, "last_opened.json").exists())
            assertEquals(com.example.plugins.editor.EditorThemePreset.MONOKAI, storage.loadThemePreset())

            // Clearing User Data wipes Last Opened & plugin preferences
            kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.clearPluginData(context, com.example.plugins.editor.TextEditorPlugin.ID)
            }
            assertFalse(File(dataDir, "last_opened.json").exists())
            assertEquals(com.example.plugins.editor.EditorThemePreset.CLASSIC_LIGHT, storage.loadThemePreset())
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun note_editor_standalone_launch_opens_full_plugin_and_saves_untitled_note() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val tempDir = File(context.cacheDir, "note_editor_standalone_test").apply {
            deleteRecursively()
            mkdirs()
        }
        var refreshed = false
        var dismissed = false
        try {
            composeTestRule.setContent {
                com.example.ui.theme.ArcFilesTheme {
                    com.example.plugins.ArcPluginViewerHost(
                        previewFile = null,
                        previewQueue = emptyList(),
                        previewText = null,
                        isStandaloneNoteEditorOpen = true,
                        onDismissStandaloneNoteEditor = { dismissed = true },
                        defaultSaveDirectory = tempDir.absolutePath,
                        defaultSaveIsSaf = false,
                        onDismiss = {},
                        onActiveItemChanged = {},
                        onSaveText = {},
                        onWorkspaceModified = { refreshed = true },
                        onRequestOpenWith = {},
                        onLaunchExternalView = { _, _ -> },
                        onShareItem = {},
                        onInfo = {},
                        onLaunchExternalIntent = { _, _ -> }
                    )
                }
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("text_editor_plugin_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("editor_subbar_filename").assertTextContains("Untitled-1.txt")

            // Type content and tap Save -> opens Save As dialog cleanly
            composeTestRule.onNodeWithTag("editor_text_field").performTextInput("Quick standalone note")
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_save_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("editor_save_as_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("editor_save_as_filename_input").performTextReplacement("my_note.md")
            composeTestRule.onNodeWithTag("editor_save_as_confirm").performClick()

            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                File(tempDir, "my_note.md").exists() && refreshed
            }
            assertEquals("Quick standalone note", File(tempDir, "my_note.md").readText())
            assertFalse(dismissed)
        } finally {
            kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.clearPluginCache(context, com.example.plugins.editor.TextEditorPlugin.ID)
                com.example.data.ArcCacheManager.clearPluginData(context, com.example.plugins.editor.TextEditorPlugin.ID)
            }
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun video_player_plugin_routes_from_viewer_host_and_controls_next_prev_seek_speed_orientation() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val tempDir = File(context.cacheDir, "video_player_plugin_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val v1 = File(tempDir, "alpha_clip.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val v2 = File(tempDir, "beta_movie.mkv").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val v3 = File(tempDir, "gamma_vlog.webm").apply { writeBytes(byteArrayOf(7, 8, 9)) }

        val items = listOf(v1, v2, v3).mapIndexed { idx, file ->
            FileItem(
                id = "vid_${idx + 1}",
                name = file.name,
                pathOrUri = file.absolutePath,
                isDirectory = false,
                sizeBytes = file.length(),
                lastModifiedMillis = file.lastModified(),
                mimeType = "video/mp4",
                extension = file.extension,
                isSafUri = false,
                parentPathOrUri = tempDir.absolutePath
            )
        }

        val activeHistory = mutableListOf<String>()
        var openWithTarget: String? = null
        var dismissed = false

        try {
            assertEquals("video_player", com.example.plugins.ArcPluginManager.resolveActiveHandler(items[0])?.id)

            composeTestRule.setContent {
                com.example.ui.theme.ArcFilesTheme {
                    com.example.plugins.ArcPluginViewerHost(
                        previewFile = items[0],
                        previewQueue = items,
                        previewText = null,
                        onDismiss = { dismissed = true },
                        onActiveItemChanged = { activeHistory.add(it.name) },
                        onSaveText = {},
                        onRequestOpenWith = { openWithTarget = it.name },
                        onLaunchExternalView = { _, _ -> },
                        onShareItem = {},
                        onInfo = {},
                        onLaunchExternalIntent = { _, _ -> }
                    )
                }
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_player_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("video_title_text").assertTextContains("alpha_clip.mp4")
            composeTestRule.onNodeWithTag("video_subtitle_text").assertTextContains("1 / 3", substring = true)

            // Orientation defaults to AUTO -> cycles to LANDSCAPE -> PORTRAIT -> AUTO
            composeTestRule.onNodeWithTag("video_orientation_button").assertTextContains("Auto")
            composeTestRule.onNodeWithTag("video_orientation_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_orientation_button").assertTextContains("Land")
            composeTestRule.onNodeWithTag("video_orientation_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_orientation_button").assertTextContains("Port")
            composeTestRule.onNodeWithTag("video_orientation_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_orientation_button").assertTextContains("Auto")

            // Forward +10s and Rewind -10s seek controls update position and badge cleanly
            composeTestRule.onNodeWithTag("video_forward_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_position_text").assertTextContains("00:10")
            composeTestRule.onNodeWithTag("video_forward_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_position_text").assertTextContains("00:20")
            composeTestRule.onNodeWithTag("video_rewind_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_position_text").assertTextContains("00:10")

            // Open Playback Speed sheet and pick 1.5x
            composeTestRule.onNodeWithTag("video_speed_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_speed_sheet").assertIsDisplayed()
            composeTestRule.onNodeWithTag("video_speed_option_1_5x").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_speed_current_label").assertTextContains("1.5x", substring = true)
            // Dismiss sheet via Done button -> speed button label updated
            composeTestRule.onNodeWithTag("video_speed_done_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_speed_button").assertTextContains("1.5x")

            // Next & Prev navigation across queue
            composeTestRule.onNodeWithTag("video_next_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_title_text").assertTextContains("beta_movie.mkv")
            composeTestRule.onNodeWithTag("video_subtitle_text").assertTextContains("2 / 3", substring = true)

            composeTestRule.onNodeWithTag("video_prev_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_title_text").assertTextContains("alpha_clip.mp4")

            // Jump via Playlist Queue sheet to index 2
            composeTestRule.onNodeWithTag("video_playlist_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_playlist_sheet").assertIsDisplayed()
            composeTestRule.onNodeWithTag("video_playlist_item_2").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_title_text").assertTextContains("gamma_vlog.webm")

            // Aspect ratio, Mute, Repeat, and Screen Lock toggles
            composeTestRule.onNodeWithTag("video_aspect_button").assertTextContains("Fit")
            composeTestRule.onNodeWithTag("video_aspect_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_aspect_button").assertTextContains("Fill")

            composeTestRule.onNodeWithTag("video_mute_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_mute_button").assertTextContains("Muted")

            composeTestRule.onNodeWithTag("video_lock_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_next_button").assertDoesNotExist()
            // Tap player screen to show unlock pill, then unlock
            composeTestRule.onNodeWithTag("video_player_screen").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_unlock_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("video_unlock_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("video_next_button").assertIsDisplayed()

            // Close player
            composeTestRule.onNodeWithTag("video_close_button").performClick()
            composeTestRule.waitForIdle()
            assertTrue(dismissed)
            assertTrue(activeHistory.containsAll(listOf("alpha_clip.mp4", "beta_movie.mkv", "gamma_vlog.webm")))
        } finally {
            kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.clearPluginCache(context, com.example.plugins.video.VideoPlayerPlugin.ID)
                com.example.data.ArcCacheManager.clearPluginData(context, com.example.plugins.video.VideoPlayerPlugin.ID)
            }
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun video_player_plugin_persists_resume_and_isolates_cache_vs_user_data() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = com.example.plugins.video.VideoPlayerStorageRepository(context)
        val clip = FileItem(
            id = "vp_1",
            name = "cinema.mp4",
            pathOrUri = "/tmp/cinema.mp4",
            isDirectory = false,
            sizeBytes = 5_000_000L,
            lastModifiedMillis = 1234L,
            mimeType = "video/mp4",
            extension = "mp4",
            isSafUri = false,
            parentPathOrUri = "/tmp"
        )

        kotlinx.coroutines.runBlocking {
            com.example.data.ArcCacheManager.clearPluginCache(context, com.example.plugins.video.VideoPlayerPlugin.ID)
            com.example.data.ArcCacheManager.clearPluginData(context, com.example.plugins.video.VideoPlayerPlugin.ID)
        }

        try {
            repo.saveSpeed(1.75f)
            repo.saveOrientationMode(com.example.plugins.video.VideoOrientationMode.LANDSCAPE)
            repo.saveResumePosition(clip, positionMs = 45_000L, durationMs = 120_000L)
            repo.recordCacheSessionMetadata(
                clip,
                com.example.plugins.video.VideoTrackSummary(
                    codec = "AVC",
                    bitrateKbps = 4200,
                    frameRate = 60f,
                    width = 1920,
                    height = 1080
                )
            )

            assertEquals(1.75f, repo.loadPreferences().playbackSpeed, 0.01f)
            assertEquals(com.example.plugins.video.VideoOrientationMode.LANDSCAPE, repo.loadPreferences().orientationMode)
            assertEquals(45_000L, repo.loadResumePosition(clip))

            kotlinx.coroutines.runBlocking {
                val snap1 = com.example.data.ArcCacheManager.inspectFullSnapshot(
                    context = context,
                    metadata = com.example.data.StorageMetadataCounts()
                )
                val stats1 = snap1.plugins.first { it.spec.id == com.example.plugins.video.VideoPlayerPlugin.ID }
                assertTrue(stats1.cacheBytes > 0L)
                assertTrue(stats1.dataBytes > 0L)
                assertTrue(stats1.dataFileCount >= 2)

                // Clearing cache must NOT wipe saved resume timestamp or speed preference
                com.example.data.ArcCacheManager.clearPluginCache(context, com.example.plugins.video.VideoPlayerPlugin.ID)
                assertEquals(45_000L, repo.loadResumePosition(clip))
                assertEquals(1.75f, repo.loadPreferences().playbackSpeed, 0.01f)

                // Clearing user data wipes resume timestamp & resets speed/orientation preferences
                com.example.data.ArcCacheManager.clearPluginData(context, com.example.plugins.video.VideoPlayerPlugin.ID)
                assertEquals(0L, repo.loadResumePosition(clip))
                assertEquals(1.0f, repo.loadPreferences().playbackSpeed, 0.01f)
                assertEquals(com.example.plugins.video.VideoOrientationMode.AUTO, repo.loadPreferences().orientationMode)
            }
        } finally {
            kotlinx.coroutines.runBlocking {
                com.example.data.ArcCacheManager.clearPluginCache(context, com.example.plugins.video.VideoPlayerPlugin.ID)
                com.example.data.ArcCacheManager.clearPluginData(context, com.example.plugins.video.VideoPlayerPlugin.ID)
            }
        }
    }

    @Test
    fun compressed_deep_scanner_finds_all_archive_formats_across_nested_folders() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = FileExplorerRepository(app)
        val extRoot = android.os.Environment.getExternalStorageDirectory()
        val workspaceRoot = repo.getWorkspaceRoot()

        val dlNested = File(extRoot, "Download/Releases/v2").apply { mkdirs() }
        val docNested = File(extRoot, "Documents/Backups").apply { mkdirs() }
        val customDir = File(extRoot, "Telegram/Telegram Documents").apply { mkdirs() }
        val androidMediaDir = File(extRoot, "Android/media/com.whatsapp/Media").apply { mkdirs() }
        val androidDataDir = File(extRoot, "Android/data/com.other.app").apply { mkdirs() }
        val wsSub = File(workspaceRoot, "Archives").apply { mkdirs() }

        val created = mutableListOf<File>()
        try {
            // Create various compressed formats across different directories
            val archiveNamesAndDirs = listOf(
                dlNested to "app_build.zip",
                dlNested to "bundle.tar.gz",
                dlNested to "installer.xapk",
                dlNested to "split_app.apks",
                docNested to "vault.7z",
                docNested to "sources.tgz",
                docNested to "patch.tar.bz2",
                customDir to "comics.cbz",
                customDir to "legacy.rar",
                customDir to "firmware.xz",
                customDir to "snapshot.zst",
                androidMediaDir to "shared_pack.jar",
                wsSub to "workspace_pack.apk"
            )
            archiveNamesAndDirs.forEach { (dir, name) ->
                created += File(dir, name).apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            }
            // Add non-archive noise files that must NOT appear in Compressed scan
            repeat(30) { idx ->
                created += File(dlNested, "photo_$idx.jpg").apply { writeBytes(byteArrayOf(9, 8)) }
                created += File(docNested, "note_$idx.txt").apply { writeText("hello $idx") }
            }
            // File inside Android/data/com.other.app should be skipped on scoped scan
            created += File(androidDataDir, "hidden_other_app.zip").apply { writeBytes(byteArrayOf(5, 6)) }

            val found = kotlinx.coroutines.runBlocking {
                repo.scanAllCompressedFiles(mountedSafEntries = emptySet(), showHidden = false)
            }
            val foundNames = found.map { it.name }.toSet()

            archiveNamesAndDirs.forEach { (_, name) ->
                assertTrue("Expected '$name' in scanAllCompressedFiles, found=$foundNames", name in foundNames)
            }
            assertFalse("photo_0.jpg" in foundNames)
            assertFalse("note_0.txt" in foundNames)
            assertFalse("hidden_other_app.zip" in foundNames)

            // Verify filter classification across formats
            assertTrue(com.example.plugins.archive.CompressedFormatFilter.ZIP.matches(found.first { it.name == "app_build.zip" }))
            assertTrue(com.example.plugins.archive.CompressedFormatFilter.ZIP.matches(found.first { it.name == "comics.cbz" }))
            assertTrue(com.example.plugins.archive.CompressedFormatFilter.RAR.matches(found.first { it.name == "legacy.rar" }))
            assertTrue(com.example.plugins.archive.CompressedFormatFilter.SEVEN_Z.matches(found.first { it.name == "vault.7z" }))
            assertTrue(com.example.plugins.archive.CompressedFormatFilter.TAR_GZ.matches(found.first { it.name == "sources.tgz" }))
            assertTrue(com.example.plugins.archive.CompressedFormatFilter.TAR_GZ.matches(found.first { it.name == "patch.tar.bz2" }))
            assertTrue(com.example.plugins.archive.CompressedFormatFilter.TAR_GZ.matches(found.first { it.name == "firmware.xz" }))
            assertTrue(com.example.plugins.archive.CompressedFormatFilter.APK.matches(found.first { it.name == "installer.xapk" }))
            assertTrue(com.example.plugins.archive.CompressedFormatFilter.APK.matches(found.first { it.name == "split_app.apks" }))
            assertTrue(com.example.plugins.archive.CompressedFormatFilter.OTHER.matches(found.first { it.name == "shared_pack.jar" }))
        } finally {
            created.forEach { it.delete() }
            dlNested.parentFile?.parentFile?.deleteRecursively()
            docNested.deleteRecursively()
            customDir.parentFile?.deleteRecursively()
            androidMediaDir.parentFile?.parentFile?.deleteRecursively()
            androidDataDir.parentFile?.deleteRecursively()
            wsSub.deleteRecursively()
        }
    }

    @Test
    fun compressed_category_screen_displays_all_archives_filters_by_format_and_opens_preview() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val extRoot = android.os.Environment.getExternalStorageDirectory()
        val archiveDir = File(extRoot, "Download/CompressedTestBox").apply { mkdirs() }
        val created = mutableListOf<File>()

        try {
            // Create a valid real ZIP archive plus .rar, .7z, .tgz, .xapk files
            val zipFile = File(archiveDir, "project_bundle.zip")
            java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("readme.txt"))
                zos.write("Arc Files Zip".toByteArray())
                zos.closeEntry()
            }
            created += zipFile
            created += File(archiveDir, "assets_pack.rar").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            created += File(archiveDir, "backup_full.7z").apply { writeBytes(byteArrayOf(4, 5, 6)) }
            created += File(archiveDir, "logs_export.tgz").apply { writeBytes(byteArrayOf(7, 8, 9)) }
            created += File(archiveDir, "game_installer.xapk").apply { writeBytes(byteArrayOf(10, 11, 12)) }

            val vm = com.example.ui.ExplorerViewModel(app)
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                val names = vm.getCategoryFiles(ExplorerCategory.ARCHIVES).map { it.name }
                names.containsAll(listOf("project_bundle.zip", "assets_pack.rar", "backup_full.7z", "logs_export.tgz", "game_installer.xapk"))
            }

            composeTestRule.setContent {
                val currentState = vm.uiState.collectAsState().value
                com.example.ui.theme.ArcFilesTheme {
                    val activeTab = currentState.activeTab
                    if (activeTab != null) {
                        com.example.ui.registry.TabDestinationRegistry.RenderDestination(
                            destination = activeTab.destination,
                            state = currentState,
                            viewModel = vm,
                            callbacks = com.example.ui.registry.DestinationCallbacks(
                                onRequestAllFilesAccess = {},
                                onMountFolder = {},
                                onImportMedia = {},
                                onImportDocs = {},
                                onNewFolder = {},
                                onNewNote = {},
                                onBatchRenameRequest = {},
                                onDeleteRequest = {},
                                onLaunchExternal = {}
                            ),
                            tabId = activeTab.id,
                            isActivePage = true
                        )
                    }
                    currentState.archivePreviewFile?.let { archiveItem ->
                        com.example.ui.components.dialogs.ArchivePreviewDialog(
                            item = archiveItem,
                            inspection = currentState.archiveInspection,
                            isLoading = currentState.archivePreviewLoading,
                            onDismiss = vm::closeArchivePreview,
                            onRetryWithPassword = { pw -> vm.openArchivePreview(archiveItem, pw) },
                            onExtract = { _, _ -> },
                            onOpenAsFallback = {}
                        )
                    }
                }
            }

            // Open Compressed category from Home Quick Actions tile
            composeTestRule.onNodeWithTag("home_screen_list")
                .performScrollToNode(hasTestTag("home_tools_card"))
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("tool_compressed").performClick()
            composeTestRule.waitForIdle()
            if (vm.uiState.value.activeLayout.viewMode != com.example.data.ViewMode.LIST) {
                vm.setViewMode(com.example.data.ViewMode.LIST)
                composeTestRule.waitForIdle()
            }

            // Verify all compressed files are shown by default (scrolling LazyColumn on compact viewport)
            composeTestRule.onNodeWithTag("compressed_filter_row").assertIsDisplayed()
            composeTestRule.onNodeWithTag("compressed_rescan_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("browser_file_list").performScrollToNode(hasText("assets_pack.rar"))
            composeTestRule.onNodeWithText("assets_pack.rar").assertIsDisplayed()
            composeTestRule.onNodeWithTag("browser_file_list").performScrollToNode(hasText("backup_full.7z"))
            composeTestRule.onNodeWithText("backup_full.7z").assertIsDisplayed()
            composeTestRule.onNodeWithTag("browser_file_list").performScrollToNode(hasText("game_installer.xapk"))
            composeTestRule.onNodeWithText("game_installer.xapk").assertIsDisplayed()
            composeTestRule.onNodeWithTag("browser_file_list").performScrollToNode(hasText("logs_export.tgz"))
            composeTestRule.onNodeWithText("logs_export.tgz").assertIsDisplayed()
            composeTestRule.onNodeWithTag("browser_file_list").performScrollToNode(hasText("project_bundle.zip"))
            composeTestRule.onNodeWithText("project_bundle.zip").assertIsDisplayed()

            // Filter by ZIP -> only ZIP shows
            composeTestRule.onNodeWithTag("compressed_filter_zip").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("project_bundle.zip").assertIsDisplayed()
            composeTestRule.onNodeWithText("assets_pack.rar").assertDoesNotExist()

            // Filter by TAR/GZ -> logs_export.tgz shows
            composeTestRule.onNodeWithTag("compressed_filter_tar_gz").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("logs_export.tgz").assertIsDisplayed()
            composeTestRule.onNodeWithText("project_bundle.zip").assertDoesNotExist()

            // Switch back to All -> all 5 archives return
            composeTestRule.onNodeWithTag("compressed_filter_all").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("project_bundle.zip").assertIsDisplayed()
            composeTestRule.onNodeWithText("backup_full.7z").assertIsDisplayed()

            // Tap ZIP archive -> opens ArchivePreviewDialog showing readme.txt inside
            composeTestRule.onNodeWithText("project_bundle.zip").performClick()
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                vm.uiState.value.archiveInspection != null
            }
            composeTestRule.onNodeWithTag("archive_preview_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithText("readme.txt").assertIsDisplayed()
        } finally {
            created.forEach { it.delete() }
            archiveDir.deleteRecursively()
        }
    }

    @Test
    fun documents_section_scans_all_folders_and_shows_dynamic_available_format_pills() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val extRoot = android.os.Environment.getExternalStorageDirectory()
        val docsFolder = File(extRoot, "Documents/StudyNotes").apply { mkdirs() }
        val downloadFolder = File(extRoot, "Download/Invoices").apply { mkdirs() }
        val customFolder = File(extRoot, "BooksAndReports").apply { mkdirs() }
        val created = mutableListOf<File>()

        try {
            created += File(docsFolder, "syllabus.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            created += File(downloadFolder, "receipt.docx").apply { writeBytes(byteArrayOf(4, 5, 6)) }
            created += File(customFolder, "notes.txt").apply { writeText("Hello ArcFiles") }

            val vm = com.example.ui.ExplorerViewModel(app)
            composeTestRule.waitUntil(5000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                val names = vm.getLibrarySectionItems(com.example.data.LibrarySection.DOCUMENTS).map { it.name }
                names.containsAll(listOf("syllabus.pdf", "receipt.docx", "notes.txt"))
            }

            composeTestRule.setContent {
                val currentState = vm.uiState.collectAsState().value
                com.example.ui.theme.ArcFilesTheme {
                    com.example.ui.registry.TabDestinationRegistry.RenderDestination(
                        destination = TabDestination.Library(com.example.data.LibrarySection.DOCUMENTS),
                        state = currentState,
                        viewModel = vm,
                        callbacks = com.example.ui.registry.DestinationCallbacks(
                            onRequestAllFilesAccess = {},
                            onMountFolder = {},
                            onImportMedia = {},
                            onImportDocs = {},
                            onNewFolder = {},
                            onNewNote = {},
                            onBatchRenameRequest = {},
                            onDeleteRequest = {},
                            onLaunchExternal = {}
                        ),
                        tabId = "tab_docs_test",
                        isActivePage = true
                    )
                }
            }

            // Dynamic pills reflect ONLY available file types (All, PDF, DOC, TXT) — not unavailable PPT/EPUB
            composeTestRule.onNodeWithTag("library_filter_chip_ALL").assertIsDisplayed()
            composeTestRule.onNodeWithTag("library_filter_chip_PDF").assertIsDisplayed()
            composeTestRule.onNodeWithTag("library_filter_chip_DOC").assertIsDisplayed()
            composeTestRule.onNodeWithTag("library_filter_chip_TXT").assertIsDisplayed()
            composeTestRule.onNodeWithTag("library_filter_chip_PPT").assertDoesNotExist()
            composeTestRule.onNodeWithTag("library_filter_chip_EPUB").assertDoesNotExist()

            // All documents from across folders are visible together
            composeTestRule.onNodeWithText("syllabus.pdf").assertIsDisplayed()
            composeTestRule.onNodeWithText("receipt.docx").assertIsDisplayed()
            composeTestRule.onNodeWithText("notes.txt").assertIsDisplayed()

            // Quick filter by DOC -> shows receipt.docx only
            composeTestRule.onNodeWithTag("library_filter_chip_DOC").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("receipt.docx").assertIsDisplayed()
            composeTestRule.onNodeWithText("syllabus.pdf").assertDoesNotExist()
            composeTestRule.onNodeWithText("notes.txt").assertDoesNotExist()

            // Switch to TXT -> shows notes.txt only
            composeTestRule.onNodeWithTag("library_filter_chip_TXT").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("notes.txt").assertIsDisplayed()
            composeTestRule.onNodeWithText("receipt.docx").assertDoesNotExist()
        } finally {
            created.forEach { it.delete() }
            docsFolder.deleteRecursively()
            downloadFolder.deleteRecursively()
            customFolder.deleteRecursively()
        }
    }

    @Test
    fun library_sections_omit_redundant_total_counts() {
        val now = 1_750_000_000_000L
        val pic1 = FileItem("p1", "one.jpg", "/storage/emulated/0/Pictures/one.jpg", false, 4096L, now, "image/jpeg", "jpg", false, "/storage/emulated/0/Pictures", createdAtMillis = now)
        val pic2 = FileItem("p2", "two.png", "/storage/emulated/0/Pictures/two.png", false, 2048L, now - 1000L, "image/png", "png", false, "/storage/emulated/0/Pictures", createdAtMillis = now - 1000L)
        val doc1 = FileItem("d1", "guide.pdf", "/storage/emulated/0/Documents/guide.pdf", false, 8192L, now, "application/pdf", "pdf", false, "/storage/emulated/0/Documents", createdAtMillis = now)
        val album = com.example.data.PhotoAlbum(
            id = "alb_1",
            name = "Vacation",
            mediaSection = com.example.data.LibrarySection.IMAGES,
            isDeviceFolder = false,
            itemCount = 2,
            totalBytes = 6144L
        )

        val showGallery = mutableStateOf(true)
        val panelState = mutableStateOf(com.example.data.GalleryPanel.ALL_IMAGES)

        composeTestRule.setContent {
            com.example.ui.theme.ArcFilesTheme {
                if (showGallery.value) {
                    com.example.ui.screens.library.GalleryLibraryScreen(
                        allImages = listOf(pic1, pic2),
                        libraryState = com.example.ui.screens.library.LibraryUiState(
                            galleryPanel = panelState.value,
                            customAlbums = listOf(album),
                            customAlbumItemsById = mapOf(album.id to listOf(pic1, pic2))
                        ),
                        selectedIds = emptySet(),
                        itemTagsByPath = emptyMap(),
                        gridColumns = 3,
                        originalMediaRatio = false,
                        dragReorderEnabled = false,
                        isActivePage = true,
                        onBack = {},
                        onSelectPanel = { panelState.value = it },
                        onOpenAlbum = {},
                        onCloseAlbum = {},
                        onCreateAlbumClick = {},
                        onRenameAlbumClick = {},
                        onDeleteAlbumClick = {},
                        onSetAlbumCover = { _, _ -> },
                        onAddToAlbumClick = {},
                        onPickImagesForAlbumClick = {},
                        onRemoveImagesFromAlbum = { _, _ -> },
                        onOpenDeviceAlbumFolder = {},
                        onPhotoClick = { _, _ -> },
                        onToggleSelect = {},
                        onSelectAll = {},
                        onClearSelection = {},
                        onFileAction = { _, _ -> },
                        onReorderImages = {},
                        onImportMediaClick = {},
                        onMountFolderClick = {}
                    )
                } else {
                    com.example.ui.screens.library.MediaLibraryScreen(
                        section = com.example.data.LibrarySection.DOCUMENTS,
                        allSectionItems = listOf(doc1),
                        filteredItems = listOf(doc1),
                        activeSubFilterId = "ALL",
                        selectedIds = emptySet(),
                        renamingItemId = null,
                        itemTagsByPath = emptyMap(),
                        viewMode = ViewMode.LIST,
                        gridColumns = 3,
                        originalMediaRatio = false,
                        monochromeBinaryIcons = false,
                        showFileExtensions = true,
                        compactListMode = false,
                        isActivePage = true,
                        onBack = {},
                        onSelectSubFilter = {},
                        onToggleViewMode = {},
                        onOpenSearch = {},
                        onFileClick = {},
                        onToggleSelect = {},
                        onSelectAll = {},
                        onClearSelection = {},
                        onFileAction = { _, _ -> },
                        onConfirmInlineRename = { _, _ -> },
                        onCancelInlineRename = {},
                        onImportClick = {},
                        onMountFolderClick = {}
                    )
                }
            }
        }

        // Gallery header & bottom tabs show no "(2)" or "2 photos across all folders" totals
        composeTestRule.onNodeWithText("All Images").assertIsDisplayed()
        composeTestRule.onNodeWithText("All Images (2)").assertDoesNotExist()
        composeTestRule.onNodeWithText("Albums (1)").assertDoesNotExist()
        composeTestRule.onNodeWithText("photos across", substring = true).assertDoesNotExist()

        panelState.value = com.example.data.GalleryPanel.ALBUMS
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Vacation").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 photos").assertDoesNotExist()

        // Documents Library header shows no "1 item ·" total
        showGallery.value = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Documents").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 item", substring = true).assertDoesNotExist()
    }

    @Test
    fun top_tab_island_pins_home_icon_always_visible_with_many_dynamic_tabs() {
        val homeTab = com.example.data.ExplorerTab("tab_home", "Home", isPinned = true, destination = TabDestination.Home)
        val dynamicTabs = (1..8).map { i ->
            com.example.data.ExplorerTab(
                id = "tab_dyn_$i",
                title = "Folder $i",
                isPinned = false,
                destination = TabDestination.Directory(
                    title = "Folder $i",
                    rootId = "drive_internal",
                    currentPathOrUri = "/storage/emulated/0/Folder$i",
                    isSaf = false,
                    breadcrumbs = emptyList()
                )
            )
        }
        val activeTabId = mutableStateOf("tab_dyn_8")

        composeTestRule.setContent {
            com.example.ui.theme.ArcFilesTheme {
                com.example.ui.components.TopTabIsland(
                    tabs = listOf(homeTab) + dynamicTabs,
                    activeTabId = activeTabId.value,
                    onMenuClick = {},
                    onSelectTab = { activeTabId.value = it },
                    onCloseTab = {},
                    onNewTabClick = {},
                    onSearchClick = {},
                    onSettingsClick = {},
                    onGridColumnsChange = {},
                    onToggleOriginalMediaRatio = {},
                    onRefreshClick = {},
                    onAnalyzerClick = {},
                    onTrashClick = {}
                )
            }
        }

        // Home icon remains visible even when the 8th dynamic tab is selected and scrolled to
        composeTestRule.onNodeWithTag("tab_chip_tab_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("top_tab_island_dynamic_row").assertIsDisplayed()

        // Tapping pinned Home icon switches back to Home
        composeTestRule.onNodeWithTag("tab_chip_tab_home").performClick()
        composeTestRule.waitForIdle()
        assertEquals("tab_home", activeTabId.value)
    }

    @Test
    fun ios_archive_preview_modal_supports_winrar_browsing_selection_and_extract_files_options() {
        val archiveItem = FileItem(
            id = "arch_1",
            name = "my_release.zip",
            pathOrUri = "/tmp/my_release.zip",
            isDirectory = false,
            sizeBytes = 2048L,
            lastModifiedMillis = 100L,
            mimeType = "application/zip",
            extension = "zip",
            isSafUri = false,
            parentPathOrUri = "/tmp"
        )
        val inspection = com.example.data.ArchiveInspectionResult(
            format = com.example.data.ArchiveFormat.ZIP,
            entries = listOf(
                com.example.data.ZipEntryInfo("docs/", true, 0L),
                com.example.data.ZipEntryInfo("docs/manual.pdf", false, 4096L, compressedSize = 1500L),
                com.example.data.ZipEntryInfo("README.md", false, 512L, compressedSize = 256L)
            ),
            isEncrypted = false,
            totalEntryCount = 3,
            totalUncompressedBytes = 4608L,
            totalCompressedBytes = 1756L
        )
        val extractCalls = mutableListOf<Pair<Boolean, String?>>()
        val customRequests = mutableListOf<com.example.ui.components.dialogs.ArchiveExtractRequest>()
        val testedPasswords = mutableListOf<String?>()
        val testReportState = mutableStateOf<com.example.data.ArchiveTestResult?>(null)

        composeTestRule.setContent {
            com.example.ui.theme.ArcFilesTheme {
                com.example.ui.components.dialogs.ArchivePreviewDialog(
                    item = archiveItem,
                    inspection = inspection,
                    isLoading = false,
                    onDismiss = {},
                    onRetryWithPassword = {},
                    onExtract = { toSubfolder, pwd -> extractCalls += (toSubfolder to pwd) },
                    onOpenAsFallback = {},
                    testResult = testReportState.value,
                    defaultExtractPath = "/tmp/target" to false,
                    onTestArchive = { pwd ->
                        testedPasswords += pwd
                        testReportState.value = com.example.data.ArchiveTestResult(
                            testedFiles = 2,
                            passed = true,
                            summary = "Integrity verified: 2 file(s) passed CRC & stream checks."
                        )
                    },
                    onExtractWithOptions = { req -> customRequests += req }
                )
            }
        }

        // Verify iOS modal renders WinRAR named-folder action + Extract Here + Extract Files...
        composeTestRule.onNodeWithTag("archive_preview_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Extract to \"my_release/\"").assertIsDisplayed()
        composeTestRule.onNodeWithTag("archive_extract_here_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("archive_extract_files_button").assertIsDisplayed()

        // Test archive button displays integrity verification banner
        composeTestRule.onNodeWithTag("archive_test_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, testedPasswords.size)
        composeTestRule.onNodeWithTag("archive_test_result_banner").performScrollTo().assertIsDisplayed()

        // Hierarchical folder navigation: open "docs" folder -> sees manual.pdf -> return via Root breadcrumb
        composeTestRule.onNodeWithTag("archive_entry_docs").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("archive_entry_docs/manual.pdf").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Root").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("archive_entry_README.md").performScrollTo().assertIsDisplayed()

        // Select mode -> select README.md -> triggers selective extraction
        composeTestRule.onNodeWithTag("archive_select_mode_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("archive_entry_README.md").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("archive_extract_selected_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(setOf("README.md"), customRequests.last().selectedEntryPaths)

        // Open WinRAR Extract Files... page, choose Rename overwrite policy, and confirm Extract Now
        composeTestRule.onNodeWithTag("archive_extract_files_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("archive_overwrite_rename").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("archive_extract_files_confirm_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.ArchiveOverwriteMode.RENAME, customRequests.last().overwriteMode)
        assertEquals("/tmp/target", customRequests.last().targetParentPathOrUri)

        // Go back and test direct "Extract to my_release/" and "Extract Here" buttons
        composeTestRule.onNodeWithText("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("archive_extract_here_button").performClick()
        composeTestRule.onNodeWithTag("archive_extract_folder_button").performClick()
        assertEquals(listOf(false to null, true to null), extractCalls)
    }

    @Test
    fun archive_engine_supports_selective_extraction_overwrite_modes_and_integrity_test() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val tempRoot = File(app.cacheDir, "winrar_engine_test_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val srcA = File(tempRoot, "alpha.txt").apply { writeText("alpha-v1") }
            val srcDir = File(tempRoot, "nested").apply { mkdirs() }
            val srcB = File(srcDir, "beta.txt").apply { writeText("beta-v1") }
            val engine = com.example.data.ArchiveEngine(app)

            val created = engine.createArchive(
                items = listOf(
                    FileItem("s1", srcA.name, srcA.absolutePath, false, srcA.length(), 0L, "text/plain", "txt", false, tempRoot.absolutePath),
                    FileItem("s2", srcDir.name, srcDir.absolutePath, true, 0L, 0L, "", "", false, tempRoot.absolutePath)
                ),
                targetParentPathOrUri = tempRoot.absolutePath,
                isTargetSaf = false,
                archiveBaseName = "pack",
                format = com.example.data.ArchiveFormat.ZIP
            ).getOrThrow()

            // Verify CRC & stream integrity test passes
            val testRes = engine.testArchive(created).getOrThrow()
            assertTrue(testRes.passed)
            assertEquals(2, testRes.testedFiles)

            // Selective extraction: only nested/beta.txt
            val outDir = File(tempRoot, "out").apply { mkdirs() }
            val selCount = engine.extractArchive(
                item = created,
                targetParentPathOrUri = outDir.absolutePath,
                isTargetSaf = false,
                createSubfolder = false,
                selectedEntryPaths = setOf("nested/beta.txt")
            ).getOrThrow()
            assertEquals(1, selCount)
            assertTrue(File(outDir, "nested/beta.txt").exists())
            assertFalse(File(outDir, "alpha.txt").exists())

            // Overwrite mode RENAME preserves existing file and creates "beta (1).txt"
            File(outDir, "nested/beta.txt").writeText("keep-me")
            engine.extractArchive(
                item = created,
                targetParentPathOrUri = outDir.absolutePath,
                isTargetSaf = false,
                createSubfolder = false,
                overwriteMode = com.example.data.ArchiveOverwriteMode.RENAME,
                selectedEntryPaths = setOf("nested/beta.txt")
            ).getOrThrow()
            assertEquals("keep-me", File(outDir, "nested/beta.txt").readText())
            assertEquals("beta-v1", File(outDir, "nested/beta (1).txt").readText())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun ios_files_manager_icons_highlight_pdf_red_useful_types_and_group_remaining_by_type() {
        fun item(name: String, ext: String, mime: String) = FileItem(
            id = name,
            name = name,
            pathOrUri = "/tmp/$name",
            isDirectory = false,
            sizeBytes = 1024L,
            lastModifiedMillis = 0L,
            mimeType = mime,
            extension = ext,
            isSafUri = false,
            parentPathOrUri = "/tmp"
        )

        val pdfSpec = com.example.ui.registry.BinaryIconRegistry.resolveIosFileSpec(
            item("report.pdf", "pdf", "application/pdf")
        )
        assertEquals(com.example.ui.registry.IosFileIconKind.PDF, pdfSpec.kind)
        assertEquals("PDF", pdfSpec.badgeLabel)
        // PDF uses classic PDF red color (high red channel, lower green/blue)
        assertTrue(pdfSpec.accentLight.red > 0.75f && pdfSpec.accentLight.green < 0.30f && pdfSpec.accentLight.blue < 0.30f)

        val docSpec = com.example.ui.registry.BinaryIconRegistry.resolveIosFileSpec(
            item("brief.docx", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        )
        assertEquals(com.example.ui.registry.IosFileIconKind.WORD_DOC, docSpec.kind)
        assertEquals("DOCX", docSpec.badgeLabel)

        val sheetSpec = com.example.ui.registry.BinaryIconRegistry.resolveIosFileSpec(
            item("budget.xlsx", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        )
        assertEquals(com.example.ui.registry.IosFileIconKind.SPREADSHEET, sheetSpec.kind)
        assertEquals("XLSX", sheetSpec.badgeLabel)

        val zipSpec = com.example.ui.registry.BinaryIconRegistry.resolveIosFileSpec(
            item("backup.zip", "zip", "application/zip")
        )
        assertEquals(com.example.ui.registry.IosFileIconKind.ARCHIVE, zipSpec.kind)
        assertEquals("ZIP", zipSpec.badgeLabel)

        // Remaining less-common extensions map to unified type-wise icons without per-ext badges
        val imgSpec = com.example.ui.registry.BinaryIconRegistry.resolveIosFileSpec(
            item("photo.webp", "webp", "image/webp")
        )
        assertEquals(com.example.ui.registry.IosFileIconKind.IMAGE_GENERIC, imgSpec.kind)
        assertNull(imgSpec.badgeLabel)

        val audioSpec = com.example.ui.registry.BinaryIconRegistry.resolveIosFileSpec(
            item("track.flac", "flac", "audio/flac")
        )
        assertEquals(com.example.ui.registry.IosFileIconKind.AUDIO_GENERIC, audioSpec.kind)
        assertNull(audioSpec.badgeLabel)

        val binSpec = com.example.ui.registry.BinaryIconRegistry.resolveIosFileSpec(
            item("dump.bin", "bin", "application/octet-stream")
        )
        val datSpec = com.example.ui.registry.BinaryIconRegistry.resolveIosFileSpec(
            item("cache.dat", "dat", "application/octet-stream")
        )
        assertEquals(com.example.ui.registry.IosFileIconKind.OTHER_GENERIC, binSpec.kind)
        assertEquals(binSpec.kind, datSpec.kind)
        assertNull(binSpec.badgeLabel)
    }

    @Test
    fun active_recycle_bin_moves_files_and_folders_to_bin_and_supports_restore_and_permanent_delete() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = FileExplorerRepository(app)
        repo.emptyTrash()

        val tempRoot = File(app.cacheDir, "active_bin_repo_test_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val docFile = File(tempRoot, "contract.pdf").apply { writeText("pdf-data") }
            val folderDir = File(tempRoot, "ProjectFolder").apply { mkdirs() }
            val nestedFile = File(folderDir, "notes.txt").apply { writeText("inside-folder") }

            val fileItem = FileItem(
                id = docFile.absolutePath,
                name = docFile.name,
                pathOrUri = docFile.absolutePath,
                isDirectory = false,
                sizeBytes = docFile.length(),
                lastModifiedMillis = docFile.lastModified(),
                mimeType = "application/pdf",
                extension = "pdf",
                isSafUri = false,
                parentPathOrUri = tempRoot.absolutePath
            )
            val folderItem = FileItem(
                id = folderDir.absolutePath,
                name = folderDir.name,
                pathOrUri = folderDir.absolutePath,
                isDirectory = true,
                sizeBytes = 0L,
                lastModifiedMillis = folderDir.lastModified(),
                mimeType = "inode/directory",
                extension = "",
                isSafUri = false,
                parentPathOrUri = tempRoot.absolutePath
            )

            // Move both a file and a folder (with nested child) to Recycle Bin
            val moved = repo.moveItemsToTrash(listOf(fileItem, folderItem)).getOrThrow()
            assertEquals(2, moved)
            assertFalse(docFile.exists())
            assertFalse(folderDir.exists())
            assertFalse(nestedFile.exists())

            val entries = repo.listTrashEntries()
            assertEquals(2, entries.size)
            val trashedFile = entries.first { it.originalName == "contract.pdf" }
            val trashedFolder = entries.first { it.originalName == "ProjectFolder" && it.isDirectory }

            // Restore the deleted folder and verify its nested content returns intact
            val restoredCount = repo.restoreTrashEntries(listOf(trashedFolder)).getOrThrow()
            assertEquals(1, restoredCount)
            assertTrue(folderDir.exists())
            assertTrue(File(folderDir, "notes.txt").exists())
            assertEquals("inside-folder", File(folderDir, "notes.txt").readText())

            // Permanently delete the remaining trashed file and confirm bin is empty
            repo.deleteTrashEntryForever(trashedFile).getOrThrow()
            assertFalse(docFile.exists())
            assertTrue(repo.listTrashEntries().isEmpty())
        } finally {
            repo.emptyTrash()
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun recycle_bin_screen_and_delete_dialog_support_filters_selection_restore_and_confirmed_permanent_delete() {
        val sampleFile = FileItem(
            id = "f1",
            name = "invoice.pdf",
            pathOrUri = "/tmp/Docs/invoice.pdf",
            isDirectory = false,
            sizeBytes = 2048L,
            lastModifiedMillis = 1000L,
            mimeType = "application/pdf",
            extension = "pdf",
            isSafUri = false,
            parentPathOrUri = "/tmp/Docs"
        )
        val trashEntriesState = mutableStateOf(
            listOf(
                com.example.data.TrashEntry(
                    id = "t1",
                    originalName = "invoice.pdf",
                    originalPath = "/tmp/Docs/invoice.pdf",
                    trashFileName = "t1_invoice.pdf",
                    isDirectory = false,
                    sizeBytes = 2048L,
                    trashedAtMillis = 1700000000000L,
                    extension = "pdf"
                ),
                com.example.data.TrashEntry(
                    id = "t2",
                    originalName = "ArchiveFolder",
                    originalPath = "/tmp/ArchiveFolder",
                    trashFileName = "t2_ArchiveFolder",
                    isDirectory = true,
                    sizeBytes = 8192L,
                    trashedAtMillis = 1700000010000L
                )
            )
        )
        val recycleEnabled = mutableStateOf(true)
        val showDeleteDialog = mutableStateOf(true)
        var movedToTrashClicked = false
        val restoredIds = mutableListOf<String>()
        val deletedForeverIds = mutableListOf<String>()

        composeTestRule.setContent {
            com.example.ui.theme.ArcFilesTheme {
                com.example.ui.screens.tools.RecycleBinScreen(
                    entries = trashEntriesState.value,
                    recycleBinEnabled = recycleEnabled.value,
                    onBack = {},
                    onToggleRecycleBinEnabled = { recycleEnabled.value = it },
                    onRestore = { entry ->
                        restoredIds += entry.id
                        trashEntriesState.value = trashEntriesState.value.filterNot { it.id == entry.id }
                    },
                    onDeleteForever = { entry ->
                        deletedForeverIds += entry.id
                        trashEntriesState.value = trashEntriesState.value.filterNot { it.id == entry.id }
                    },
                    onEmptyTrash = {
                        deletedForeverIds += trashEntriesState.value.map { it.id }
                        trashEntriesState.value = emptyList()
                    }
                )
                if (showDeleteDialog.value) {
                    com.example.ui.components.dialogs.DeleteConfirmDialog(
                        items = listOf(sampleFile),
                        recycleBinEnabled = true,
                        onDismiss = { showDeleteDialog.value = false },
                        onMoveToTrash = {
                            movedToTrashClicked = true
                            showDeleteDialog.value = false
                        },
                        onConfirm = { showDeleteDialog.value = false }
                    )
                }
            }
        }

        // Delete dialog defaults to "Move to Recycle Bin" when active
        composeTestRule.onNodeWithTag("delete_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("delete_move_to_trash_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(movedToTrashClicked)

        // RecycleBinScreen shows Active status + both file & folder entries
        composeTestRule.onNodeWithTag("recycle_bin_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("trash_entry_t1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("trash_entry_t2").performScrollTo().assertIsDisplayed()

        // Filter to Folders -> only ArchiveFolder visible
        composeTestRule.onNodeWithTag("recycle_filter_folders").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("trash_entry_t2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("trash_entry_t1").assertDoesNotExist()

        // Back to All -> tap Restore on t2 (ArchiveFolder)
        composeTestRule.onNodeWithTag("recycle_filter_all").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("trash_restore_t2").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("t2"), restoredIds)
        composeTestRule.onNodeWithTag("trash_entry_t2").assertDoesNotExist()

        // Delete Forever on t1 -> opens confirmation modal -> confirms permanent delete
        composeTestRule.onNodeWithTag("trash_delete_t1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("trash_permanent_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("trash_confirm_permanent_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("t1"), deletedForeverIds)
        assertTrue(trashEntriesState.value.isEmpty())
    }
}
