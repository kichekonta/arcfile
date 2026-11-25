package com.example.data

enum class BatchRenameMode(val label: String) {
    PREFIX_POSTFIX("Original"),
    REPLACE_NAME("New Name"),
    NUMBER_ONLY("Numbers")
}

data class BatchRenameConfig(
    val mode: BatchRenameMode = BatchRenameMode.PREFIX_POSTFIX,
    val prefix: String = "",
    val postfix: String = "",
    val newBaseName: String = "",
    val includeNumbering: Boolean = false,
    val startIndex: Int = 1,
    val paddingDigits: Int = 2,
    val separator: String = "_",
    val keepExtension: Boolean = true
)

data class BatchRenamePreviewEntry(
    val item: FileItem,
    val newName: String,
    val hasConflict: Boolean = false,
    val conflictReason: String? = null
) {
    val isChanged: Boolean get() = newName != item.name
}

object BatchRenameUtils {

    fun formatSequenceNumber(
        offset: Int,
        startIndex: Int,
        paddingDigits: Int
    ): String {
        val value = (startIndex.coerceAtLeast(0) + offset).coerceAtLeast(0)
        val pad = paddingDigits.coerceIn(1, 6)
        return if (pad <= 1) value.toString() else value.toString().padStart(pad, '0')
    }

    fun splitBaseAndExtension(item: FileItem): Pair<String, String> {
        if (item.isDirectory) return item.name to ""
        val dotIndex = item.name.lastIndexOf('.')
        return if (dotIndex > 0) {
            item.name.substring(0, dotIndex) to item.name.substring(dotIndex)
        } else {
            item.name to ""
        }
    }

    fun computeTargetName(
        item: FileItem,
        offset: Int,
        config: BatchRenameConfig
    ): String {
        val (origBase, extWithDot) = splitBaseAndExtension(item)
        val seq = formatSequenceNumber(offset, config.startIndex, config.paddingDigits)
        val base = when (config.mode) {
            BatchRenameMode.NUMBER_ONLY -> seq
            BatchRenameMode.REPLACE_NAME -> {
                val cleanNew = config.newBaseName
                if (config.includeNumbering) {
                    if (cleanNew.isEmpty()) seq else "$cleanNew${config.separator}$seq"
                } else {
                    cleanNew
                }
            }
            BatchRenameMode.PREFIX_POSTFIX -> {
                val affixed = "${config.prefix}$origBase${config.postfix}"
                if (config.includeNumbering) {
                    if (affixed.isEmpty()) seq else "$affixed${config.separator}$seq"
                } else {
                    affixed
                }
            }
        }
        val extSuffix = if (config.keepExtension && !item.isDirectory) extWithDot else ""
        return "$base$extSuffix"
    }

    fun buildPreview(
        items: List<FileItem>,
        config: BatchRenameConfig
    ): List<BatchRenamePreviewEntry> {
        val raw = items.mapIndexed { idx, item ->
            item to computeTargetName(item, idx, config).trim()
        }
        val targetCounts = raw.groupingBy { (item, name) ->
            item.parentPathOrUri to name.lowercase()
        }.eachCount()

        return raw.map { (item, candidate) ->
            val invalidReason = when {
                candidate.isBlank() -> "Name cannot be empty"
                candidate.contains("/") || candidate.contains("\\") -> "Invalid slash character"
                (targetCounts[item.parentPathOrUri to candidate.lowercase()] ?: 0) > 1 -> "Duplicate name"
                else -> null
            }
            BatchRenamePreviewEntry(
                item = item,
                newName = candidate,
                hasConflict = invalidReason != null,
                conflictReason = invalidReason
            )
        }
    }

    fun canApply(entries: List<BatchRenamePreviewEntry>): Boolean =
        entries.isNotEmpty() &&
            entries.none { it.hasConflict } &&
            entries.any { it.isChanged }
}
