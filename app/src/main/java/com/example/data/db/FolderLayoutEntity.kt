package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.EffectiveFolderLayout
import com.example.data.GroupByField
import com.example.data.SortField
import com.example.data.ViewMode

@Entity(tableName = "folder_layouts")
data class FolderLayoutEntity(
    @PrimaryKey val folderKey: String,
    val viewMode: String,
    val gridColumns: Int,
    val originalMediaRatio: Boolean,
    val sortField: String,
    val sortAscending: Boolean,
    val groupBy: String,
    val updatedAtMillis: Long,
    val customOrderKeys: String = ""
) {
    fun toEffectiveLayout(): EffectiveFolderLayout = EffectiveFolderLayout(
        viewMode = runCatching { ViewMode.valueOf(viewMode) }.getOrDefault(ViewMode.LIST),
        gridColumns = gridColumns.coerceIn(2, 4),
        originalMediaRatio = originalMediaRatio,
        sortField = runCatching { SortField.valueOf(sortField) }.getOrDefault(SortField.NAME),
        sortAscending = sortAscending,
        groupBy = runCatching { GroupByField.valueOf(groupBy) }.getOrDefault(GroupByField.NONE),
        customOrderKeys = decodeCustomOrder(customOrderKeys),
        isCustomSaved = true
    )

    companion object {
        fun encodeCustomOrder(keys: List<String>): String {
            if (keys.isEmpty()) return ""
            return keys.asSequence()
                .map { it.replace("\n", "").replace("\r", "") }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString("\n")
        }

        fun decodeCustomOrder(raw: String): List<String> {
            if (raw.isEmpty()) return emptyList()
            return raw.split('\n').filter { it.isNotEmpty() }
        }

        fun fromEffectiveLayout(
            folderKey: String,
            layout: EffectiveFolderLayout,
            updatedAtMillis: Long = System.currentTimeMillis()
        ): FolderLayoutEntity = FolderLayoutEntity(
            folderKey = folderKey,
            viewMode = layout.viewMode.name,
            gridColumns = layout.gridColumns.coerceIn(2, 4),
            originalMediaRatio = layout.originalMediaRatio,
            sortField = layout.sortField.name,
            sortAscending = layout.sortAscending,
            groupBy = layout.groupBy.name,
            updatedAtMillis = updatedAtMillis,
            customOrderKeys = encodeCustomOrder(layout.customOrderKeys)
        )
    }
}
