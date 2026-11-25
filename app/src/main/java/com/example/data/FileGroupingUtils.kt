package com.example.data

import androidx.compose.runtime.Immutable
import java.util.Calendar

@Immutable
data class FileGroup(
    val key: String,
    val title: String,
    val items: List<FileItem>
)

object FileGroupingUtils {

    private val TYPE_ORDER = listOf(
        "Folders",
        "Images",
        "Videos",
        "Audio",
        "Documents",
        "Compressed",
        "Other"
    )

    fun sortItems(
        items: List<FileItem>,
        sortField: SortField,
        ascending: Boolean,
        foldersFirst: Boolean = true,
        customOrderKeys: List<String> = emptyList()
    ): List<FileItem> {
        if (items.size <= 1) return items
        val dirSign = if (ascending) 1 else -1

        if (sortField == SortField.CUSTOM && customOrderKeys.isNotEmpty()) {
            val rankByKey = HashMap<String, Int>(customOrderKeys.size * 2)
            customOrderKeys.forEachIndexed { index, key ->
                rankByKey.putIfAbsent(key, index)
            }
            val comparator = Comparator<FileItem> { a, b ->
                val rankA = rankByKey[a.id]
                val rankB = rankByKey[b.id]
                when {
                    rankA != null && rankB != null -> rankA.compareTo(rankB) * dirSign
                    rankA != null -> -1
                    rankB != null -> 1
                    else -> {
                        if (foldersFirst && a.isDirectory != b.isDirectory) {
                            if (a.isDirectory) -1 else 1
                        } else {
                            a.name.compareTo(b.name, ignoreCase = true) * dirSign
                        }
                    }
                }
            }
            return items.sortedWith(comparator)
        }

        val comparator = Comparator<FileItem> { a, b ->
            if (foldersFirst && a.isDirectory != b.isDirectory) {
                return@Comparator if (a.isDirectory) -1 else 1
            }
            val primary = when (sortField) {
                SortField.NAME, SortField.CUSTOM -> a.name.compareTo(b.name, ignoreCase = true)
                SortField.DATE -> {
                    val timeA = if (a.createdAtMillis > 0L) a.createdAtMillis else a.lastModifiedMillis
                    val timeB = if (b.createdAtMillis > 0L) b.createdAtMillis else b.lastModifiedMillis
                    val cmp = timeA.compareTo(timeB)
                    if (cmp != 0) cmp else a.name.compareTo(b.name, ignoreCase = true)
                }
                SortField.SIZE -> {
                    val cmp = a.sizeBytes.compareTo(b.sizeBytes)
                    if (cmp != 0) cmp else a.name.compareTo(b.name, ignoreCase = true)
                }
                SortField.TYPE -> {
                    val cmp = a.extension.compareTo(b.extension, ignoreCase = true)
                    if (cmp != 0) cmp else a.name.compareTo(b.name, ignoreCase = true)
                }
            }
            primary * dirSign
        }
        return items.sortedWith(comparator)
    }

    fun groupItems(
        items: List<FileItem>,
        groupBy: GroupByField,
        sortField: SortField,
        ascending: Boolean,
        foldersFirst: Boolean = true,
        nowMillis: Long = System.currentTimeMillis(),
        alreadySorted: Boolean = false,
        customOrderKeys: List<String> = emptyList()
    ): List<FileGroup> {
        val sorted = if (alreadySorted) items else sortItems(items, sortField, ascending, foldersFirst, customOrderKeys)
        return when (groupBy) {
            GroupByField.NONE -> listOf(FileGroup(key = "all", title = "", items = sorted))
            GroupByField.TYPE -> groupByType(sorted)
            GroupByField.DATE -> groupByDate(sorted, ascending, nowMillis)
        }
    }

    private fun groupByType(sortedItems: List<FileItem>): List<FileGroup> {
        val buckets = LinkedHashMap<String, MutableList<FileItem>>()
        TYPE_ORDER.forEach { buckets[it] = mutableListOf() }

        sortedItems.forEach { item ->
            val label = when {
                item.isDirectory -> "Folders"
                item.isImage -> "Images"
                item.isVideo -> "Videos"
                item.isAudio -> "Audio"
                item.isDocument -> "Documents"
                item.isArchive -> "Compressed"
                else -> "Other"
            }
            buckets.getOrPut(label) { mutableListOf() }.add(item)
        }

        return buckets.entries
            .filter { it.value.isNotEmpty() }
            .map { (label, groupList) ->
                FileGroup(
                    key = "type_${label.lowercase()}",
                    title = label,
                    items = groupList
                )
            }
    }

    private fun groupByDate(
        sortedItems: List<FileItem>,
        ascending: Boolean,
        nowMillis: Long
    ): List<FileGroup> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        val startOfYesterday = startOfToday - 86_400_000L
        val startOfWeek = startOfToday - 6L * 86_400_000L
        val startOfMonth = startOfToday - 29L * 86_400_000L

        val order = if (ascending) {
            listOf("Unknown", "Older", "Last 30 Days", "Last 7 Days", "Yesterday", "Today")
        } else {
            listOf("Today", "Yesterday", "Last 7 Days", "Last 30 Days", "Older", "Unknown")
        }
        val buckets = LinkedHashMap<String, MutableList<FileItem>>()
        order.forEach { buckets[it] = mutableListOf() }

        sortedItems.forEach { item ->
            val ts = item.lastModifiedMillis
            val label = when {
                ts <= 0L -> "Unknown"
                ts >= startOfToday -> "Today"
                ts >= startOfYesterday -> "Yesterday"
                ts >= startOfWeek -> "Last 7 Days"
                ts >= startOfMonth -> "Last 30 Days"
                else -> "Older"
            }
            buckets.getOrPut(label) { mutableListOf() }.add(item)
        }

        return buckets.entries
            .filter { it.value.isNotEmpty() }
            .map { (label, groupList) ->
                FileGroup(
                    key = "date_${label.lowercase().replace(' ', '_')}",
                    title = label,
                    items = groupList
                )
            }
    }
}
