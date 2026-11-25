package com.example.data

import android.util.LruCache
import androidx.compose.runtime.Immutable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

enum class ExplorerCategory(val label: String) {
    PHOTOS("Images"),
    VIDEOS("Movies"),
    DOCUMENTS("Documents"),
    MUSIC("Music"),
    DOWNLOADS("Downloads"),
    ARCHIVES("Compressed"),
    OTHER("Other")
}

enum class LibrarySection(val label: String, val category: ExplorerCategory) {
    IMAGES("Images", ExplorerCategory.PHOTOS),
    MOVIES("Movies", ExplorerCategory.VIDEOS),
    DOCUMENTS("Documents", ExplorerCategory.DOCUMENTS),
    MUSIC("Music", ExplorerCategory.MUSIC),
    DOWNLOADS("Downloads", ExplorerCategory.DOWNLOADS);

    companion object {
        fun fromCategory(category: ExplorerCategory): LibrarySection? =
            entries.firstOrNull { it.category == category }
    }
}

enum class GalleryPanel {
    ALL_IMAGES,
    ALBUMS,
    FOLDERS
}

@Immutable
data class PhotoAlbum(
    val id: String,
    val name: String,
    val mediaSection: LibrarySection = LibrarySection.IMAGES,
    val isDeviceFolder: Boolean = false,
    val folderPathOrUri: String = "",
    val coverPathOrUri: String? = null,
    val coverIsSaf: Boolean = false,
    val previewThumbPaths: List<Pair<String, Boolean>> = emptyList(),
    val itemCount: Int = 0,
    val totalBytes: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val sortOrder: Int = 0
)

enum class DriveType {
    INTERNAL,
    EXTERNAL_VOLUME,
    SAF_TREE,
    WORKSPACE
}

@Immutable
data class DriveInfo(
    val id: String,
    val name: String,
    val subtitle: String,
    val pathOrUri: String,
    val type: DriveType,
    val totalBytes: Long,
    val freeBytes: Long,
    val isSaf: Boolean = false
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction: Float
        get() = if (totalBytes > 0L) (usedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
}

@Immutable
data class CategorySummary(
    val category: ExplorerCategory,
    val itemCount: Int = 0,
    val totalBytes: Long = 0L
)

@Immutable
data class FileItem(
    val id: String,
    val name: String,
    val pathOrUri: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mimeType: String,
    val extension: String,
    val isSafUri: Boolean,
    val parentPathOrUri: String,
    val childCount: Int? = null,
    val createdAtMillis: Long = lastModifiedMillis
) {
    val isImage: Boolean
        get() = !isDirectory && (mimeType.startsWith("image/") || extension in IMAGE_EXTENSIONS)
    val isVideo: Boolean
        get() = !isDirectory && (mimeType.startsWith("video/") || extension in VIDEO_EXTENSIONS)
    val isAudio: Boolean
        get() = !isDirectory && (mimeType.startsWith("audio/") || extension in AUDIO_EXTENSIONS)
    val isDocument: Boolean
        get() = !isDirectory && (
            extension.lowercase() in DOCUMENT_EXTENSIONS ||
                mimeType.startsWith("text/", ignoreCase = true) ||
                mimeType.lowercase() in DOCUMENT_MIME_TYPES
            )
    val isArchive: Boolean
        get() = !isDirectory && (
            extension.lowercase() in ARCHIVE_EXTENSIONS ||
                mimeType.lowercase() in ARCHIVE_MIME_TYPES
            )
    val isExtractableArchive: Boolean
        get() = !isDirectory && extension.lowercase() in EXTRACTABLE_ARCHIVE_EXTENSIONS
    val isApk: Boolean
        get() = !isDirectory && extension.equals("apk", ignoreCase = true)
    val isHiddenOrSystem: Boolean
        get() = name.startsWith(".") || name.startsWith("%") || name.startsWith("$")
    val isTextEditable: Boolean
        get() = !isDirectory && (
            mimeType.startsWith("text/") ||
                mimeType.equals("application/json", ignoreCase = true) ||
                mimeType.equals("application/xml", ignoreCase = true) ||
                mimeType.equals("application/javascript", ignoreCase = true) ||
                mimeType.equals("application/x-sh", ignoreCase = true) ||
                extension.lowercase() in TEXT_PREVIEW_EXTENSIONS
            )
    val isMarkdown: Boolean
        get() = !isDirectory && extension.lowercase() in MARKDOWN_EXTENSIONS
    val isHtml: Boolean
        get() = !isDirectory && extension.lowercase() in HTML_EXTENSIONS
    val supportsRichPreview: Boolean
        get() = isMarkdown || isHtml

    companion object {
        val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "gif", "bmp",
            "heic", "heif", "heics", "heifs", "hif", "avif",
            "svg", "ico"
        )
        val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v")
        val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "flac", "aac", "m4a", "opus")
        val DOCUMENT_EXTENSIONS = setOf(
            "pdf",
            "doc", "docx", "odt", "rtf",
            "txt", "log",
            "md", "markdown", "mdx",
            "xls", "xlsx", "ods", "csv", "tsv",
            "ppt", "pptx", "odp",
            "epub",
            "json", "xml", "yaml", "yml",
            "html", "htm", "xhtml",
            "ini", "cfg", "conf", "toml", "properties",
            "tex", "rst"
        )
        val DOCUMENT_MIME_TYPES = setOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            "application/rtf",
            "text/rtf",
            "application/epub+zip",
            "application/json",
            "application/xml",
            "text/csv",
            "text/tab-separated-values"
        )
        val ARCHIVE_EXTENSIONS = setOf(
            "zip", "rar", "7z",
            "tar", "gz", "tgz", "bz2", "tbz", "tbz2", "xz", "txz", "zst", "lz4", "lzma",
            "cbz", "cbr", "cb7",
            "jar", "war", "ear", "cab", "iso",
            "apk", "xapk", "apks", "apkm", "aab"
        )
        val ARCHIVE_MIME_TYPES = setOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/vnd.rar",
            "application/x-rar-compressed",
            "application/x-7z-compressed",
            "application/x-tar",
            "application/gzip",
            "application/x-gzip",
            "application/x-bzip2",
            "application/x-xz",
            "application/zstd",
            "application/x-lz4",
            "application/x-lzma",
            "application/vnd.comicbook+zip",
            "application/vnd.comicbook-rar",
            "application/java-archive",
            "application/vnd.ms-cab-compressed",
            "application/x-iso9660-image",
            "application/vnd.android.package-archive"
        )
        val EXTRACTABLE_ARCHIVE_EXTENSIONS = setOf("zip", "7z", "rar")
        val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "mdx")
        val HTML_EXTENSIONS = setOf("html", "htm", "xhtml")
        val TEXT_PREVIEW_EXTENSIONS = setOf(
            "txt", "md", "markdown", "mdx", "json", "xml", "csv", "tsv", "log",
            "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx", "html", "htm", "xhtml",
            "css", "scss", "yaml", "yml", "ini", "cfg", "conf", "toml", "properties", "env",
            "gradle", "sh", "bash", "zsh", "bat", "ps1", "sql", "c", "cpp", "h", "hpp",
            "rs", "go", "rb", "php", "dart", "swift", "srt", "vtt", "ass", "ssa",
            "rst", "tex", "diff", "patch", "pem", "pub", "key", "gitignore"
        )
    }
}

@Immutable
data class BreadcrumbNode(
    val title: String,
    val pathOrUri: String,
    val isSaf: Boolean
)

@Immutable
sealed interface TabDestination {
    data object Home : TabDestination

    data class Directory(
        val title: String,
        val rootId: String,
        val currentPathOrUri: String,
        val isSaf: Boolean,
        val breadcrumbs: List<BreadcrumbNode>
    ) : TabDestination

    data class Category(
        val category: ExplorerCategory
    ) : TabDestination

    data class Library(
        val section: LibrarySection
    ) : TabDestination

    data class Search(
        val query: String = ""
    ) : TabDestination

    data object Settings : TabDestination

    data object Analyzer : TabDestination

    data object Trash : TabDestination

    data object Bookmarks : TabDestination

    data class Tag(val tagId: String) : TabDestination
}

@Immutable
data class BookmarkEntry(
    val title: String,
    val pathOrUri: String,
    val isSaf: Boolean,
    val isDirectory: Boolean
)

@Immutable
data class TrashEntry(
    val id: String,
    val originalName: String,
    val originalPath: String,
    val trashFileName: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val trashedAtMillis: Long,
    val isSafOrigin: Boolean = false,
    val safParentUri: String = "",
    val mimeType: String = "",
    val extension: String = ""
) {
    val originalParentDisplay: String
        get() = if (isSafOrigin) {
            "Mounted Storage (SAF)"
        } else {
            originalPath.substringBeforeLast('/', "").ifBlank { "Storage" }
        }

    fun toDisplayFileItem(trashRootPath: String = ""): FileItem = FileItem(
        id = id,
        name = originalName,
        pathOrUri = if (trashRootPath.isNotBlank()) "$trashRootPath/$trashFileName" else trashFileName,
        isDirectory = isDirectory,
        sizeBytes = sizeBytes,
        lastModifiedMillis = trashedAtMillis,
        mimeType = mimeType.ifBlank { if (isDirectory) "inode/directory" else "*/*" },
        extension = extension.ifBlank {
            if (isDirectory) "" else originalName.substringAfterLast('.', "").lowercase()
        },
        isSafUri = false,
        parentPathOrUri = originalPath.substringBeforeLast('/', "")
    )
}

@Immutable
data class ZipEntryInfo(
    val name: String,
    val isDirectory: Boolean,
    val uncompressedSize: Long,
    val isEncrypted: Boolean = false,
    val compressedSize: Long = 0L,
    val lastModifiedMillis: Long = 0L
)

enum class ArchiveOverwriteMode(val label: String, val shortLabel: String) {
    OVERWRITE("Overwrite Existing", "Overwrite"),
    SKIP("Skip Existing", "Skip"),
    RENAME("Rename Existing", "Rename")
}

@Immutable
data class ArchiveTestResult(
    val testedFiles: Int,
    val passed: Boolean,
    val summary: String
)

enum class ArchiveFormat(
    val extension: String,
    val label: String,
    val mimeType: String,
    val supportsCreation: Boolean,
    val supportsEncryptionOnCreate: Boolean
) {
    ZIP("zip", "ZIP (.zip)", "application/zip", supportsCreation = true, supportsEncryptionOnCreate = true),
    SEVEN_Z("7z", "7-Zip (.7z)", "application/x-7z-compressed", supportsCreation = true, supportsEncryptionOnCreate = false),
    RAR("rar", "RAR (.rar)", "application/vnd.rar", supportsCreation = false, supportsEncryptionOnCreate = false);

    companion object {
        fun fromExtension(ext: String): ArchiveFormat? = when (ext.lowercase()) {
            "zip" -> ZIP
            "7z" -> SEVEN_Z
            "rar" -> RAR
            else -> null
        }
    }
}

enum class ArchiveCompressionLevel(val label: String) {
    STORE("Store"),
    FAST("Fast"),
    NORMAL("Normal"),
    BEST("Best")
}

@Immutable
data class ArchiveInspectionResult(
    val format: ArchiveFormat,
    val entries: List<ZipEntryInfo>,
    val isEncrypted: Boolean = false,
    val errorNote: String? = null,
    val totalEntryCount: Int = entries.size,
    val totalUncompressedBytes: Long = entries.sumOf { if (it.isDirectory) 0L else it.uncompressedSize.coerceAtLeast(0L) },
    val totalCompressedBytes: Long = entries.sumOf { if (it.isDirectory) 0L else it.compressedSize.coerceAtLeast(0L) }
)

@Immutable
data class ExplorerTab(
    val id: String,
    val title: String,
    val isPinned: Boolean = false,
    val destination: TabDestination,
    val history: List<TabDestination> = emptyList()
)

enum class SortField(val label: String) {
    NAME("Name"),
    DATE("Date"),
    SIZE("Size"),
    TYPE("Type"),
    CUSTOM("Custom")
}

enum class GroupByField(val label: String) {
    NONE("None"),
    TYPE("Type"),
    DATE("Date")
}

enum class ViewMode {
    LIST,
    GRID
}

@Immutable
data class EffectiveFolderLayout(
    val viewMode: ViewMode = ViewMode.LIST,
    val gridColumns: Int = 3,
    val originalMediaRatio: Boolean = true,
    val sortField: SortField = SortField.NAME,
    val sortAscending: Boolean = true,
    val groupBy: GroupByField = GroupByField.NONE,
    val customOrderKeys: List<String> = emptyList(),
    val isCustomSaved: Boolean = false
)

fun TabDestination.layoutStorageKey(): String? = when (this) {
    is TabDestination.Directory -> {
        if (isSaf) currentPathOrUri
        else if (currentPathOrUri.length > 1) currentPathOrUri.trimEnd('/')
        else currentPathOrUri
    }
    is TabDestination.Category -> "category:${category.name}"
    is TabDestination.Library -> "library:${section.name}"
    is TabDestination.Search -> "search"
    is TabDestination.Tag -> "tag:$tagId"
    else -> null
}

@Immutable
data class FileTag(
    val id: String,
    val name: String,
    val colorHex: Long,
    val sortOrder: Int = 0,
    val isSystemDefault: Boolean = false
)

object IosTagPalette {
    val RED = 0xFFFF3B30L
    val ORANGE = 0xFFFF9500L
    val YELLOW = 0xFFFFCC00L
    val GREEN = 0xFF34C759L
    val BLUE = 0xFF007AFFL
    val PURPLE = 0xFFAF52DEL
    val GRAY = 0xFF8E8E93L

    val presetColors: List<Long> = listOf(RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE, GRAY)

    val defaultTags: List<FileTag> = listOf(
        FileTag(id = "tag_red", name = "Red", colorHex = RED, sortOrder = 0, isSystemDefault = true),
        FileTag(id = "tag_orange", name = "Orange", colorHex = ORANGE, sortOrder = 1, isSystemDefault = true),
        FileTag(id = "tag_yellow", name = "Yellow", colorHex = YELLOW, sortOrder = 2, isSystemDefault = true),
        FileTag(id = "tag_green", name = "Green", colorHex = GREEN, sortOrder = 3, isSystemDefault = true),
        FileTag(id = "tag_blue", name = "Blue", colorHex = BLUE, sortOrder = 4, isSystemDefault = true),
        FileTag(id = "tag_purple", name = "Purple", colorHex = PURPLE, sortOrder = 5, isSystemDefault = true),
        FileTag(id = "tag_gray", name = "Gray", colorHex = GRAY, sortOrder = 6, isSystemDefault = true)
    )
}

fun normalizeTagPath(pathOrUri: String, isSaf: Boolean): String =
    if (isSaf || pathOrUri.length <= 1) pathOrUri else pathOrUri.trimEnd('/')

enum class ClipboardOp {
    COPY,
    MOVE
}

@Immutable
data class ClipboardState(
    val items: List<FileItem>,
    val operation: ClipboardOp
)

enum class TransferOperationType(val activeVerb: String, val completedVerb: String) {
    COPY("Copying", "Copied"),
    MOVE("Moving", "Moved"),
    DELETE("Deleting", "Deleted"),
    IMPORT("Importing", "Imported"),
    ARCHIVE("Compressing", "Compressed"),
    EXTRACT("Extracting", "Extracted")
}

enum class TransferStatus {
    PREPARING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    FAILED
}

@Immutable
data class FileTransferProgress(
    val taskId: String,
    val operation: TransferOperationType,
    val status: TransferStatus = TransferStatus.PREPARING,
    val currentFileName: String = "",
    val sourceLabel: String = "",
    val destinationLabel: String = "",
    val processedItems: Int = 0,
    val totalItems: Int = 0,
    val copiedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaMillis: Long? = null,
    val isMinimized: Boolean = false,
    val errorMessage: String? = null
) {
    val fraction: Float
        get() = when {
            status == TransferStatus.COMPLETED -> 1f
            totalBytes > 0L -> (copiedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
            totalItems > 0 -> (processedItems.toFloat() / totalItems.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }

    val percent: Int
        get() = (fraction * 100f).toInt().coerceIn(0, 100)

    val isRunning: Boolean
        get() = status == TransferStatus.PREPARING || status == TransferStatus.IN_PROGRESS
}

enum class OpenAsType(val label: String, val subtitle: String, val mimeType: String?) {
    AUTO("Default / Auto", "Use detected file type", null),
    VIDEO("Video Player", "VLC, MX Player, System Video", "video/*"),
    AUDIO("Audio Player", "Music & audio players", "audio/*"),
    IMAGE("Image Viewer", "Gallery & photo editors", "image/*"),
    TEXT("Text / Code", "Text editors & browsers", "text/plain"),
    ANY("All Apps (*/*)", "Show any compatible handler", "*/*")
}

const val BUILTIN_VIEWER_PACKAGE = "com.example.arcfiles.builtin"
const val BUILTIN_DOC_VIEWER_ACTIVITY = "BuiltinDocumentViewer"
const val BUILTIN_MEDIA_VIEWER_ACTIVITY = "BuiltinMediaViewer"

@Immutable
data class ResolvedAppOption(
    val packageName: String,
    val activityName: String,
    val label: String
) {
    val componentKey: String get() = "$packageName/$activityName"
    val isBuiltinApp: Boolean get() = packageName == BUILTIN_VIEWER_PACKAGE
}

@Immutable
data class DefaultOpenApp(
    val key: String,
    val packageName: String,
    val activityName: String,
    val appLabel: String
) {
    val isBuiltinApp: Boolean get() = packageName == BUILTIN_VIEWER_PACKAGE
}

@Immutable
data class OpenWithRequest(
    val item: FileItem,
    val fromOpenAsAction: Boolean = false,
    val apps: List<ResolvedAppOption> = emptyList(),
    val preselectedComponentKey: String? = null,
    val mimeOverride: String? = null
)

fun defaultOpenAppKey(item: FileItem): String {
    val ext = item.extension.trim().lowercase()
    if (ext.isNotBlank()) return "ext:$ext"
    val mime = item.mimeType.trim().lowercase()
    if (mime.isNotBlank() && mime != "*/*" && mime != "application/octet-stream") {
        return "mime:$mime"
    }
    return "item:${item.id}"
}

@Immutable
sealed interface DirectoryLoadState {
    data object Loading : DirectoryLoadState
    @Immutable
    data class Loaded(
        val items: List<FileItem>,
        val isScopedStorageLimited: Boolean = false,
        val localPathForSaf: String? = null
    ) : DirectoryLoadState
    data class RestrictedRequiresSaf(val attemptedPath: String) : DirectoryLoadState
    data class Error(val message: String) : DirectoryLoadState
}

object FormatUtils {
    private val fullDateFormatter = object : ThreadLocal<Pair<Locale, SimpleDateFormat>>() {}
    private val shortDateFormatter = object : ThreadLocal<Pair<Locale, SimpleDateFormat>>() {}
    private val shortDateDayCache = LruCache<Long, String>(128)
    @Volatile
    private var cachedShortDateLocale: Locale? = null

    private fun getFormatter(
        holder: ThreadLocal<Pair<Locale, SimpleDateFormat>>,
        pattern: String
    ): SimpleDateFormat {
        val locale = Locale.getDefault()
        val cached = holder.get()
        if (cached != null && cached.first == locale) {
            return cached.second
        }
        val created = SimpleDateFormat(pattern, locale)
        holder.set(locale to created)
        return created
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        val unit = 1024.0
        val exp = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceAtMost(5)
        val prefix = "KMGTPE"[exp - 1]
        val value = bytes / unit.pow(exp.toDouble())
        return if (value >= 100) {
            String.format(Locale.US, "%.0f %sB", value, prefix)
        } else {
            String.format(Locale.US, "%.1f %sB", value, prefix)
        }
    }

    fun formatEta(millis: Long?): String {
        if (millis == null || millis < 0L) return "—"
        val totalSec = (millis / 1000L).coerceAtLeast(0L)
        if (totalSec <= 0L) return "<1s"
        val hours = totalSec / 3600L
        val minutes = (totalSec % 3600L) / 60L
        val seconds = totalSec % 60L
        return when {
            hours > 0L -> "${hours}h ${minutes}m"
            minutes > 0L -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun formatDate(millis: Long): String {
        if (millis <= 0L) return "—"
        return getFormatter(fullDateFormatter, "MMM d, yyyy · HH:mm").format(Date(millis))
    }

    fun formatShortDate(millis: Long): String {
        if (millis <= 0L) return "—"
        val locale = Locale.getDefault()
        if (cachedShortDateLocale != locale) {
            synchronized(shortDateDayCache) {
                if (cachedShortDateLocale != locale) {
                    shortDateDayCache.evictAll()
                    cachedShortDateLocale = locale
                }
            }
        }
        val dayKey = millis / 86_400_000L
        shortDateDayCache.get(dayKey)?.let { return it }
        val formatted = getFormatter(shortDateFormatter, "MMM d").format(Date(millis))
        shortDateDayCache.put(dayKey, formatted)
        return formatted
    }
}
