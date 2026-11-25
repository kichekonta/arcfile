package com.example.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.explorerDataStore: DataStore<Preferences> by preferencesDataStore(name = "arc_files_prefs")

@Immutable
data class ExplorerPreferences(
    val mountedSafEntries: Set<String> = emptySet(),
    val bookmarkEntries: Set<String> = emptySet(),
    val showHomeNewFiles: Boolean = false,
    val showHomeBookmarks: Boolean = false,
    val viewMode: ViewMode = ViewMode.LIST,
    val gridColumns: Int = 3,
    val originalMediaRatio: Boolean = true,
    val sortField: SortField = SortField.NAME,
    val sortAscending: Boolean = true,
    val groupBy: GroupByField = GroupByField.NONE,
    val foldersFirst: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val showFileExtensions: Boolean = true,
    val showFolderCounts: Boolean = true,
    val compactListMode: Boolean = false,
    val recycleBinEnabled: Boolean = true,
    val confirmBeforeDelete: Boolean = true,
    val themePreference: ThemePreference = ThemePreference.LIGHT,
    val monochromeBinaryIcons: Boolean = false,
    val useBuiltinTextViewer: Boolean = true,
    val dragReorderEnabled: Boolean = true,
    val defaultOpenAppEntries: Set<String> = emptySet()
) {
    fun parsedBookmarks(): List<BookmarkEntry> = bookmarkEntries.mapNotNull { raw ->
        val parts = raw.split("|", limit = 4)
        if (parts.size < 4) return@mapNotNull null
        BookmarkEntry(
            pathOrUri = parts[0],
            isSaf = parts[1].toBooleanStrictOrNull() ?: false,
            isDirectory = parts[2].toBooleanStrictOrNull() ?: true,
            title = parts[3]
        )
    }.sortedBy { it.title.lowercase() }

    fun parsedDefaultOpenApps(): Map<String, DefaultOpenApp> = defaultOpenAppEntries.mapNotNull { raw ->
        val parts = raw.split("|", limit = 4)
        if (parts.size < 4) return@mapNotNull null
        val key = parts[0]
        if (key.isBlank() || parts[1].isBlank() || parts[2].isBlank()) return@mapNotNull null
        DefaultOpenApp(
            key = key,
            packageName = parts[1],
            activityName = parts[2],
            appLabel = parts[3]
        )
    }.associateBy { it.key }
}

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val MOUNTED_SAF = stringSetPreferencesKey("mounted_saf_entries")
        val BOOKMARKS = stringSetPreferencesKey("bookmark_entries")
        val SHOW_HOME_NEW_FILES = booleanPreferencesKey("show_home_new_files")
        val SHOW_HOME_BOOKMARKS = booleanPreferencesKey("show_home_bookmarks")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val ORIGINAL_MEDIA_RATIO = booleanPreferencesKey("original_media_ratio")
        val SORT_FIELD = stringPreferencesKey("sort_field")
        val SORT_ASC = booleanPreferencesKey("sort_ascending")
        val GROUP_BY = stringPreferencesKey("group_by")
        val FOLDERS_FIRST = booleanPreferencesKey("folders_first")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden_files")
        val SHOW_EXTENSIONS = booleanPreferencesKey("show_file_extensions")
        val SHOW_FOLDER_COUNTS = booleanPreferencesKey("show_folder_counts")
        val COMPACT_LIST = booleanPreferencesKey("compact_list_mode")
        val RECYCLE_BIN_ENABLED = booleanPreferencesKey("recycle_bin_enabled")
        val CONFIRM_DELETE = booleanPreferencesKey("confirm_before_delete")
        val THEME_PREF = stringPreferencesKey("theme_preference")
        val MONO_BINARY = booleanPreferencesKey("monochrome_binary_icons")
        val USE_BUILTIN_TEXT_VIEWER = booleanPreferencesKey("use_builtin_text_viewer")
        val DRAG_REORDER_ENABLED = booleanPreferencesKey("drag_reorder_enabled")
        val DEFAULT_OPEN_APPS = stringSetPreferencesKey("default_open_apps")
    }

    val preferencesFlow: Flow<ExplorerPreferences> = context.explorerDataStore.data.map { prefs ->
        ExplorerPreferences(
            mountedSafEntries = prefs[Keys.MOUNTED_SAF] ?: emptySet(),
            bookmarkEntries = prefs[Keys.BOOKMARKS] ?: emptySet(),
            showHomeNewFiles = prefs[Keys.SHOW_HOME_NEW_FILES] ?: false,
            showHomeBookmarks = prefs[Keys.SHOW_HOME_BOOKMARKS] ?: false,
            viewMode = prefs[Keys.VIEW_MODE]?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() } ?: ViewMode.LIST,
            gridColumns = (prefs[Keys.GRID_COLUMNS] ?: 3).coerceIn(2, 4),
            originalMediaRatio = prefs[Keys.ORIGINAL_MEDIA_RATIO] ?: true,
            sortField = prefs[Keys.SORT_FIELD]?.let { runCatching { SortField.valueOf(it) }.getOrNull() } ?: SortField.NAME,
            sortAscending = prefs[Keys.SORT_ASC] ?: true,
            groupBy = prefs[Keys.GROUP_BY]?.let { runCatching { GroupByField.valueOf(it) }.getOrNull() } ?: GroupByField.NONE,
            foldersFirst = prefs[Keys.FOLDERS_FIRST] ?: true,
            showHiddenFiles = prefs[Keys.SHOW_HIDDEN] ?: false,
            showFileExtensions = prefs[Keys.SHOW_EXTENSIONS] ?: true,
            showFolderCounts = prefs[Keys.SHOW_FOLDER_COUNTS] ?: true,
            compactListMode = prefs[Keys.COMPACT_LIST] ?: false,
            recycleBinEnabled = prefs[Keys.RECYCLE_BIN_ENABLED] ?: true,
            confirmBeforeDelete = prefs[Keys.CONFIRM_DELETE] ?: true,
            themePreference = prefs[Keys.THEME_PREF]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() } ?: ThemePreference.LIGHT,
            monochromeBinaryIcons = prefs[Keys.MONO_BINARY] ?: false,
            useBuiltinTextViewer = prefs[Keys.USE_BUILTIN_TEXT_VIEWER] ?: true,
            dragReorderEnabled = prefs[Keys.DRAG_REORDER_ENABLED] ?: true,
            defaultOpenAppEntries = prefs[Keys.DEFAULT_OPEN_APPS] ?: emptySet()
        )
    }

    suspend fun toggleBookmark(entry: BookmarkEntry): Boolean {
        var added = false
        val encoded = "${entry.pathOrUri}|${entry.isSaf}|${entry.isDirectory}|${entry.title.replace("|", " ")}"
        context.explorerDataStore.edit { prefs ->
            val current = prefs[Keys.BOOKMARKS]?.toMutableSet() ?: mutableSetOf()
            val existing = current.firstOrNull { it.startsWith("${entry.pathOrUri}|") }
            if (existing != null) {
                current.remove(existing)
                added = false
            } else {
                current.add(encoded)
                added = true
            }
            prefs[Keys.BOOKMARKS] = current
        }
        return added
    }

    suspend fun removeBookmark(pathOrUri: String) {
        context.explorerDataStore.edit { prefs ->
            val current = prefs[Keys.BOOKMARKS]?.filterNot { it.startsWith("$pathOrUri|") }?.toSet() ?: emptySet()
            prefs[Keys.BOOKMARKS] = current
        }
    }

    suspend fun setShowHomeNewFiles(show: Boolean) {
        context.explorerDataStore.edit { it[Keys.SHOW_HOME_NEW_FILES] = show }
    }

    suspend fun setShowHomeBookmarks(show: Boolean) {
        context.explorerDataStore.edit { it[Keys.SHOW_HOME_BOOKMARKS] = show }
    }

    suspend fun addMountedSaf(uriString: String, displayName: String) {
        val sanitizedName = displayName.replace("|", " ")
        context.explorerDataStore.edit { prefs ->
            val current = prefs[Keys.MOUNTED_SAF]?.filterNot { it.startsWith("$uriString|") }?.toMutableSet()
                ?: mutableSetOf()
            current.add("$uriString|$sanitizedName")
            prefs[Keys.MOUNTED_SAF] = current
        }
    }

    suspend fun removeMountedSaf(uriString: String) {
        context.explorerDataStore.edit { prefs ->
            val current = prefs[Keys.MOUNTED_SAF]?.filterNot { it.startsWith("$uriString|") }?.toSet()
                ?: emptySet()
            prefs[Keys.MOUNTED_SAF] = current
        }
    }

    suspend fun setViewMode(mode: ViewMode) {
        context.explorerDataStore.edit { it[Keys.VIEW_MODE] = mode.name }
    }

    suspend fun setGridColumns(columns: Int) {
        context.explorerDataStore.edit { it[Keys.GRID_COLUMNS] = columns.coerceIn(2, 4) }
    }

    suspend fun setOriginalMediaRatio(enabled: Boolean) {
        context.explorerDataStore.edit { it[Keys.ORIGINAL_MEDIA_RATIO] = enabled }
    }

    suspend fun setSort(field: SortField, ascending: Boolean) {
        context.explorerDataStore.edit {
            it[Keys.SORT_FIELD] = field.name
            it[Keys.SORT_ASC] = ascending
        }
    }

    suspend fun setGroupBy(groupBy: GroupByField) {
        context.explorerDataStore.edit { it[Keys.GROUP_BY] = groupBy.name }
    }

    suspend fun setFoldersFirst(enabled: Boolean) {
        context.explorerDataStore.edit { it[Keys.FOLDERS_FIRST] = enabled }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        context.explorerDataStore.edit { it[Keys.SHOW_HIDDEN] = show }
    }

    suspend fun setShowFileExtensions(show: Boolean) {
        context.explorerDataStore.edit { it[Keys.SHOW_EXTENSIONS] = show }
    }

    suspend fun setShowFolderCounts(show: Boolean) {
        context.explorerDataStore.edit { it[Keys.SHOW_FOLDER_COUNTS] = show }
    }

    suspend fun setCompactListMode(compact: Boolean) {
        context.explorerDataStore.edit { it[Keys.COMPACT_LIST] = compact }
    }

    suspend fun setRecycleBinEnabled(enabled: Boolean) {
        context.explorerDataStore.edit { it[Keys.RECYCLE_BIN_ENABLED] = enabled }
    }

    suspend fun setConfirmBeforeDelete(confirm: Boolean) {
        context.explorerDataStore.edit { it[Keys.CONFIRM_DELETE] = confirm }
    }

    suspend fun setThemePreference(pref: ThemePreference) {
        context.explorerDataStore.edit { it[Keys.THEME_PREF] = pref.name }
    }

    suspend fun setMonochromeBinaryIcons(mono: Boolean) {
        context.explorerDataStore.edit { it[Keys.MONO_BINARY] = mono }
    }

    suspend fun setUseBuiltinTextViewer(enabled: Boolean) {
        context.explorerDataStore.edit { it[Keys.USE_BUILTIN_TEXT_VIEWER] = enabled }
    }

    suspend fun setDragReorderEnabled(enabled: Boolean) {
        context.explorerDataStore.edit { it[Keys.DRAG_REORDER_ENABLED] = enabled }
    }

    suspend fun setDefaultOpenApp(
        key: String,
        packageName: String,
        activityName: String,
        appLabel: String
    ) {
        val cleanKey = key.replace("|", "_")
        val cleanLabel = appLabel.replace("|", " ")
        context.explorerDataStore.edit { prefs ->
            val current = prefs[Keys.DEFAULT_OPEN_APPS]?.filterNot { it.startsWith("$cleanKey|") }?.toMutableSet()
                ?: mutableSetOf()
            current.add("$cleanKey|$packageName|$activityName|$cleanLabel")
            prefs[Keys.DEFAULT_OPEN_APPS] = current
        }
    }

    suspend fun clearDefaultOpenApp(key: String) {
        val cleanKey = key.replace("|", "_")
        context.explorerDataStore.edit { prefs ->
            val current = prefs[Keys.DEFAULT_OPEN_APPS]?.filterNot { it.startsWith("$cleanKey|") }?.toSet()
                ?: emptySet()
            prefs[Keys.DEFAULT_OPEN_APPS] = current
        }
    }

    suspend fun clearAllDefaultOpenApps() {
        context.explorerDataStore.edit { prefs ->
            prefs.remove(Keys.DEFAULT_OPEN_APPS)
        }
    }

    suspend fun clearAllBookmarks() {
        context.explorerDataStore.edit { prefs ->
            prefs.remove(Keys.BOOKMARKS)
        }
    }

    suspend fun resetExplorerDefaults() {
        context.explorerDataStore.edit { prefs ->
            prefs.remove(Keys.VIEW_MODE)
            prefs.remove(Keys.GRID_COLUMNS)
            prefs.remove(Keys.ORIGINAL_MEDIA_RATIO)
            prefs.remove(Keys.SORT_FIELD)
            prefs.remove(Keys.SORT_ASC)
            prefs.remove(Keys.GROUP_BY)
            prefs.remove(Keys.FOLDERS_FIRST)
            prefs.remove(Keys.SHOW_HIDDEN)
            prefs.remove(Keys.SHOW_EXTENSIONS)
            prefs.remove(Keys.SHOW_FOLDER_COUNTS)
            prefs.remove(Keys.COMPACT_LIST)
            prefs.remove(Keys.RECYCLE_BIN_ENABLED)
            prefs.remove(Keys.CONFIRM_DELETE)
            prefs.remove(Keys.THEME_PREF)
            prefs.remove(Keys.MONO_BINARY)
            prefs.remove(Keys.USE_BUILTIN_TEXT_VIEWER)
            prefs.remove(Keys.DRAG_REORDER_ENABLED)
        }
    }
}
