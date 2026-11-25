package com.example.features.settings

import com.example.data.EffectiveFolderLayout
import com.example.data.GroupByField
import com.example.data.PreferencesRepository
import com.example.data.SortField
import com.example.data.ViewMode
import com.example.data.db.FolderLayoutEntity
import com.example.data.db.FolderLayoutRepository
import com.example.data.layoutStorageKey
import com.example.ui.ExplorerUiState
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Feature-scoped coordinator owning per-folder layout persistence (Room), custom drag-reorder state,
 * and global DataStore user preferences.
 */
class SettingsAndLayoutCoordinator(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<ExplorerUiState>,
    private val prefsRepo: PreferencesRepository,
    private val folderLayoutRepo: FolderLayoutRepository,
    private val withResolvedLayout: (ExplorerUiState) -> ExplorerUiState,
    private val onResortDirectory: () -> Unit,
    private val onMessage: (String) -> Unit
) {

    private val layoutSaveMutex = Mutex()
    private val pendingWriteVersion = AtomicInteger(0)
    private var lastLayoutTimestamp = 0L

    fun observeFolderLayouts() {
        scope.launch {
            folderLayoutRepo.observeAllLayouts().collect { dbLayouts ->
                if (pendingWriteVersion.get() > 0) return@collect
                val prevLayout = uiState.value.activeLayout
                uiState.update { withResolvedLayout(it.copy(folderLayouts = dbLayouts)) }
                val nextLayout = uiState.value.activeLayout
                if (prevLayout.sortField != nextLayout.sortField ||
                    prevLayout.sortAscending != nextLayout.sortAscending ||
                    prevLayout.customOrderKeys != nextLayout.customOrderKeys
                ) {
                    onResortDirectory()
                }
            }
        }
    }

    suspend fun removeFolderStateBoth(pathOrUri: String) {
        uiState.update { state ->
            val filtered = state.folderLayouts.filterKeys { candidate ->
                !folderLayoutRepo.isSameOrDescendantFolderKey(candidate, pathOrUri)
            }
            withResolvedLayout(state.copy(folderLayouts = filtered))
        }
        folderLayoutRepo.removeFolderState(pathOrUri)
    }

    fun saveLayoutForActiveLocation(
        transform: (EffectiveFolderLayout) -> EffectiveFolderLayout
    ) {
        var persistedKey: String? = null
        var updatedLayout: EffectiveFolderLayout? = null
        var sortChanged = false
        val ts = maxOf(System.currentTimeMillis(), lastLayoutTimestamp + 1L)
        lastLayoutTimestamp = ts

        uiState.update { state ->
            val key = state.activeTab?.destination?.layoutStorageKey()
            val prev = state.activeLayout
            val next = transform(prev).copy(isCustomSaved = key != null)
            sortChanged = prev.sortField != next.sortField ||
                prev.sortAscending != next.sortAscending ||
                prev.customOrderKeys != next.customOrderKeys
            updatedLayout = next
            persistedKey = key
            if (key != null) {
                val entity = FolderLayoutEntity.fromEffectiveLayout(key, next, ts)
                state.copy(
                    folderLayouts = state.folderLayouts + (key to entity),
                    activeLayout = next
                )
            } else {
                state.copy(activeLayout = next)
            }
        }

        if (sortChanged) {
            onResortDirectory()
        }

        val keyToPersist = persistedKey
        val targetLayout = updatedLayout ?: return
        if (keyToPersist != null) {
            pendingWriteVersion.incrementAndGet()
            scope.launch {
                try {
                    layoutSaveMutex.withLock {
                        folderLayoutRepo.saveLayout(keyToPersist, targetLayout, ts)
                    }
                } finally {
                    pendingWriteVersion.decrementAndGet()
                }
            }
        } else {
            scope.launch {
                prefsRepo.setViewMode(targetLayout.viewMode)
                prefsRepo.setGridColumns(targetLayout.gridColumns)
                prefsRepo.setOriginalMediaRatio(targetLayout.originalMediaRatio)
                prefsRepo.setSort(targetLayout.sortField, targetLayout.sortAscending)
                prefsRepo.setGroupBy(targetLayout.groupBy)
            }
        }
    }

    fun setViewMode(mode: ViewMode) {
        saveLayoutForActiveLocation { it.copy(viewMode = mode) }
    }

    fun setGridColumns(columns: Int, switchToGrid: Boolean = false) {
        val clamped = columns.coerceIn(2, 4)
        saveLayoutForActiveLocation { current ->
            current.copy(
                gridColumns = clamped,
                viewMode = if (switchToGrid) ViewMode.GRID else current.viewMode
            )
        }
    }

    fun setOriginalMediaRatio(enabled: Boolean) {
        saveLayoutForActiveLocation { it.copy(originalMediaRatio = enabled) }
    }

    fun setSort(field: SortField) {
        saveLayoutForActiveLocation { current ->
            val nextAsc = if (current.sortField == field) !current.sortAscending else current.sortAscending
            current.copy(sortField = field, sortAscending = nextAsc)
        }
    }

    fun setSortOrder(field: SortField, ascending: Boolean) {
        saveLayoutForActiveLocation { it.copy(sortField = field, sortAscending = ascending) }
    }

    fun toggleSortOrder() {
        saveLayoutForActiveLocation { it.copy(sortAscending = !it.sortAscending) }
    }

    fun setGroupBy(groupBy: GroupByField) {
        saveLayoutForActiveLocation { it.copy(groupBy = groupBy) }
    }

    fun commitCustomReorder(orderedIds: List<String>) {
        val cleanIds = orderedIds.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) return
        saveLayoutForActiveLocation { current ->
            current.copy(
                sortField = SortField.CUSTOM,
                sortAscending = true,
                groupBy = GroupByField.NONE,
                customOrderKeys = cleanIds
            )
        }
    }

    fun setGlobalViewMode(mode: ViewMode) {
        scope.launch { prefsRepo.setViewMode(mode) }
    }

    fun setGlobalGridColumns(columns: Int) {
        scope.launch { prefsRepo.setGridColumns(columns.coerceIn(2, 4)) }
    }

    fun setGlobalOriginalMediaRatio(enabled: Boolean) {
        scope.launch { prefsRepo.setOriginalMediaRatio(enabled) }
    }

    fun setGlobalSortField(field: SortField) {
        val asc = uiState.value.prefs.sortAscending
        scope.launch { prefsRepo.setSort(field, asc) }
    }

    fun setGlobalSortAscending(ascending: Boolean) {
        val field = uiState.value.prefs.sortField
        scope.launch { prefsRepo.setSort(field, ascending) }
    }

    fun setGlobalGroupBy(groupBy: GroupByField) {
        scope.launch { prefsRepo.setGroupBy(groupBy) }
    }

    fun setFoldersFirst(enabled: Boolean) {
        scope.launch { prefsRepo.setFoldersFirst(enabled) }
    }

    fun setShowHiddenFiles(show: Boolean) {
        scope.launch { prefsRepo.setShowHiddenFiles(show) }
    }

    fun setShowFileExtensions(show: Boolean) {
        scope.launch { prefsRepo.setShowFileExtensions(show) }
    }

    fun setShowFolderCounts(show: Boolean) {
        scope.launch { prefsRepo.setShowFolderCounts(show) }
    }

    fun setCompactListMode(compact: Boolean) {
        scope.launch { prefsRepo.setCompactListMode(compact) }
    }

    fun setRecycleBinEnabled(enabled: Boolean) {
        scope.launch { prefsRepo.setRecycleBinEnabled(enabled) }
    }

    fun setConfirmBeforeDelete(confirm: Boolean) {
        scope.launch { prefsRepo.setConfirmBeforeDelete(confirm) }
    }

    fun setThemePreference(pref: ThemePreference) {
        scope.launch { prefsRepo.setThemePreference(pref) }
    }

    fun setMonochromeBinaryIcons(mono: Boolean) {
        scope.launch { prefsRepo.setMonochromeBinaryIcons(mono) }
    }

    fun setUseBuiltinTextViewer(enabled: Boolean) {
        scope.launch { prefsRepo.setUseBuiltinTextViewer(enabled) }
    }

    fun setDragReorderEnabled(enabled: Boolean) {
        scope.launch { prefsRepo.setDragReorderEnabled(enabled) }
    }

    fun clearDefaultOpenApp(key: String) {
        scope.launch { prefsRepo.clearDefaultOpenApp(key) }
    }

    fun clearAllDefaultOpenApps() {
        scope.launch {
            prefsRepo.clearAllDefaultOpenApps()
            onMessage("Cleared saved default open apps")
        }
    }

    fun resetSettingsToDefaults() {
        scope.launch {
            prefsRepo.resetExplorerDefaults()
            folderLayoutRepo.clearAllLayouts()
            onMessage("Settings and saved folder layouts reset")
        }
    }

    fun toggleShowHomeNewFiles() {
        val next = !uiState.value.prefs.showHomeNewFiles
        scope.launch { prefsRepo.setShowHomeNewFiles(next) }
    }

    fun toggleShowHomeBookmarks() {
        val next = !uiState.value.prefs.showHomeBookmarks
        scope.launch { prefsRepo.setShowHomeBookmarks(next) }
    }

    fun cycleThemePreference() {
        val next = when (uiState.value.prefs.themePreference) {
            ThemePreference.LIGHT -> ThemePreference.DARK
            ThemePreference.DARK -> ThemePreference.SYSTEM
            ThemePreference.SYSTEM -> ThemePreference.LIGHT
        }
        setThemePreference(next)
    }
}
