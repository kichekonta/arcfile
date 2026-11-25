package com.example.ui.screens.library

import androidx.compose.runtime.Immutable
import com.example.data.CategorySummary
import com.example.data.ExplorerCategory
import com.example.data.FileItem
import com.example.data.GalleryPanel
import com.example.data.LibrarySection
import com.example.data.PhotoAlbum
import com.example.data.normalizeTagPath

@Immutable
data class LibraryFilterOption(
    val id: String,
    val label: String
)

@Immutable
data class LibraryUiState(
    val galleryPanel: GalleryPanel = GalleryPanel.ALL_IMAGES,
    val activeAlbumId: String? = null,
    val customAlbums: List<PhotoAlbum> = emptyList(),
    val deviceAlbums: List<PhotoAlbum> = emptyList(),
    val customAlbumItemsById: Map<String, List<FileItem>> = emptyMap(),
    val deviceAlbumItemsById: Map<String, List<FileItem>> = emptyMap(),
    val galleryPanelsBySection: Map<LibrarySection, GalleryPanel> = emptyMap(),
    val activeAlbumIdsBySection: Map<LibrarySection, String?> = emptyMap(),
    val customAlbumsBySection: Map<LibrarySection, List<PhotoAlbum>> = emptyMap(),
    val deviceAlbumsBySection: Map<LibrarySection, List<PhotoAlbum>> = emptyMap(),
    val customAlbumItemsBySection: Map<LibrarySection, Map<String, List<FileItem>>> = emptyMap(),
    val deviceAlbumItemsBySection: Map<LibrarySection, Map<String, List<FileItem>>> = emptyMap(),
    val subFiltersBySection: Map<LibrarySection, String> = emptyMap(),
    val dialogSection: LibrarySection = LibrarySection.IMAGES,
    val addToAlbumTargets: List<FileItem>? = null,
    val showCreateAlbumDialog: Boolean = false,
    val createAlbumInitialItems: List<FileItem> = emptyList(),
    val renameAlbumTarget: PhotoAlbum? = null,
    val pickImagesForAlbumId: String? = null
) {
    fun panelFor(section: LibrarySection): GalleryPanel =
        galleryPanelsBySection[section]
            ?: if (section == LibrarySection.IMAGES) galleryPanel else GalleryPanel.ALL_IMAGES

    fun activeAlbumIdFor(section: LibrarySection): String? =
        if (section in activeAlbumIdsBySection) activeAlbumIdsBySection[section]
        else if (section == LibrarySection.IMAGES) activeAlbumId
        else null

    fun customAlbumsFor(section: LibrarySection): List<PhotoAlbum> =
        customAlbumsBySection[section]
            ?: if (section == LibrarySection.IMAGES) customAlbums else emptyList()

    fun deviceAlbumsFor(section: LibrarySection): List<PhotoAlbum> =
        deviceAlbumsBySection[section]
            ?: if (section == LibrarySection.IMAGES) deviceAlbums else emptyList()

    fun findAlbum(albumId: String?, section: LibrarySection = LibrarySection.IMAGES): PhotoAlbum? {
        if (albumId == null) return null
        return customAlbumsFor(section).firstOrNull { it.id == albumId }
            ?: deviceAlbumsFor(section).firstOrNull { it.id == albumId }
    }

    fun itemsForAlbum(albumId: String?, section: LibrarySection = LibrarySection.IMAGES): List<FileItem> {
        if (albumId == null) return emptyList()
        val customMap = customAlbumItemsBySection[section]
            ?: if (section == LibrarySection.IMAGES) customAlbumItemsById else emptyMap()
        val deviceMap = deviceAlbumItemsBySection[section]
            ?: if (section == LibrarySection.IMAGES) deviceAlbumItemsById else emptyMap()
        return customMap[albumId] ?: deviceMap[albumId] ?: emptyList()
    }
}

object LibraryDomainUtils {

    private val pdfExt = setOf("pdf")
    private val officeExt = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "csv")
    private val losslessAudioExt = setOf("flac", "wav", "alac", "aiff")
    private val voiceAudioExt = setOf("m4a", "aac", "ogg", "opus")

    fun isInDownloadsFolder(item: FileItem): Boolean {
        val lower = item.pathOrUri.lowercase()
        return lower.contains("/download") ||
            lower.contains("%2fdownload") ||
            lower.contains(":download") ||
            lower.contains("%3adownload")
    }

    fun filterFilesForSection(
        section: LibrarySection,
        allIndexedFiles: List<FileItem>
    ): List<FileItem> = when (section) {
        LibrarySection.IMAGES -> allIndexedFiles.filter { it.isImage }
        LibrarySection.MOVIES -> allIndexedFiles.filter { it.isVideo }
        LibrarySection.DOCUMENTS -> allIndexedFiles.filter { it.isDocument }
        LibrarySection.MUSIC -> allIndexedFiles.filter { it.isAudio }
        LibrarySection.DOWNLOADS -> allIndexedFiles.filter { isInDownloadsFolder(it) }
    }

    private val docPillPriority = listOf(
        "PDF", "DOC", "TXT", "XLS", "PPT", "MD", "CSV", "RTF", "ODT", "EPUB", "HTML", "JSON", "XML", "YAML", "LOG"
    )

    private fun canonicalDocumentPillKey(item: FileItem): String {
        val ext = item.extension.lowercase()
        val mime = item.mimeType.lowercase()
        return when {
            ext == "pdf" || mime == "application/pdf" -> "PDF"
            ext == "doc" || ext == "docx" || mime == "application/msword" ||
                mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "DOC"
            ext == "txt" -> "TXT"
            ext == "md" || ext == "markdown" || ext == "mdx" -> "MD"
            ext == "xls" || ext == "xlsx" || ext == "ods" || mime == "application/vnd.ms-excel" ||
                mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "XLS"
            ext == "ppt" || ext == "pptx" || ext == "odp" || mime == "application/vnd.ms-powerpoint" ||
                mime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "PPT"
            ext == "csv" || ext == "tsv" -> "CSV"
            ext == "rtf" || mime == "application/rtf" || mime == "text/rtf" -> "RTF"
            ext == "odt" -> "ODT"
            ext == "epub" || mime == "application/epub+zip" -> "EPUB"
            ext == "html" || ext == "htm" || ext == "xhtml" -> "HTML"
            ext == "json" || mime == "application/json" -> "JSON"
            ext == "xml" || mime == "application/xml" -> "XML"
            ext == "yaml" || ext == "yml" -> "YAML"
            ext == "log" -> "LOG"
            ext.isNotBlank() && ext.length <= 10 -> ext.uppercase()
            else -> "TEXT"
        }
    }

    fun filterOptionsFor(
        section: LibrarySection,
        items: List<FileItem> = emptyList()
    ): List<LibraryFilterOption> = when (section) {
        LibrarySection.IMAGES -> emptyList()
        LibrarySection.MOVIES -> listOf(
            LibraryFilterOption("ALL", "All Videos"),
            LibraryFilterOption("MP4", "MP4"),
            LibraryFilterOption("MKV_OTHER", "MKV & Other"),
            LibraryFilterOption("LARGE", "Large (>50MB)")
        )
        LibrarySection.DOCUMENTS -> buildDocumentFilterOptions(items)
        LibrarySection.MUSIC -> listOf(
            LibraryFilterOption("ALL", "All Audio"),
            LibraryFilterOption("MP3", "MP3"),
            LibraryFilterOption("LOSSLESS", "Lossless"),
            LibraryFilterOption("VOICE", "M4A / OGG")
        )
        LibrarySection.DOWNLOADS -> listOf(
            LibraryFilterOption("ALL", "All Downloads"),
            LibraryFilterOption("MEDIA", "Media"),
            LibraryFilterOption("DOCS", "Documents"),
            LibraryFilterOption("PACKAGES", "APKs & Archives")
        )
    }

    private fun buildDocumentFilterOptions(items: List<FileItem>): List<LibraryFilterOption> {
        if (items.isEmpty()) return listOf(LibraryFilterOption("ALL", "All"))
        val availableKeys = LinkedHashSet<String>(items.size.coerceAtMost(16))
        for (item in items) {
            if (!item.isDirectory) {
                availableKeys.add(canonicalDocumentPillKey(item))
            }
        }
        val orderedPrioritized = docPillPriority.filter { it in availableKeys }
        val extraKeys = availableKeys.filter { it !in docPillPriority }.sorted()
        return buildList(1 + orderedPrioritized.size + extraKeys.size) {
            add(LibraryFilterOption("ALL", "All"))
            orderedPrioritized.forEach { key -> add(LibraryFilterOption(key, key)) }
            extraKeys.forEach { key -> add(LibraryFilterOption(key, key)) }
        }
    }

    fun applySubFilter(
        section: LibrarySection,
        filterId: String?,
        items: List<FileItem>
    ): List<FileItem> {
        val id = filterId ?: "ALL"
        if (id == "ALL") return items
        return when (section) {
            LibrarySection.IMAGES -> items
            LibrarySection.MOVIES -> when (id) {
                "MP4" -> items.filter { it.extension.equals("mp4", ignoreCase = true) || it.extension.equals("m4v", ignoreCase = true) }
                "MKV_OTHER" -> items.filter { !it.extension.equals("mp4", ignoreCase = true) && !it.extension.equals("m4v", ignoreCase = true) }
                "LARGE" -> items.filter { it.sizeBytes >= 50L * 1024L * 1024L }
                else -> items
            }
            LibrarySection.DOCUMENTS -> when (id) {
                "PDF" -> items.filter { canonicalDocumentPillKey(it) == "PDF" }
                "OFFICE" -> items.filter { it.extension.lowercase() in officeExt }
                "CODE_TEXT" -> items.filter { it.isTextEditable || it.mimeType.startsWith("text/") }
                else -> {
                    val matched = items.filter { canonicalDocumentPillKey(it) == id }
                    if (matched.isNotEmpty() || items.any { canonicalDocumentPillKey(it) == id }) matched
                    else items
                }
            }
            LibrarySection.MUSIC -> when (id) {
                "MP3" -> items.filter { it.extension.equals("mp3", ignoreCase = true) }
                "LOSSLESS" -> items.filter { it.extension.lowercase() in losslessAudioExt }
                "VOICE" -> items.filter { it.extension.lowercase() in voiceAudioExt }
                else -> items
            }
            LibrarySection.DOWNLOADS -> when (id) {
                "MEDIA" -> items.filter { it.isImage || it.isVideo || it.isAudio }
                "DOCS" -> items.filter { it.isDocument }
                "PACKAGES" -> items.filter { it.isArchive || it.isApk }
                else -> items
            }
        }
    }

    fun computeLibrarySummaries(allIndexedFiles: List<FileItem>): Map<ExplorerCategory, CategorySummary> {
        val counts = HashMap<ExplorerCategory, Int>(8)
        val bytes = HashMap<ExplorerCategory, Long>(8)
        for (item in allIndexedFiles) {
            when {
                item.isImage -> {
                    counts[ExplorerCategory.PHOTOS] = (counts[ExplorerCategory.PHOTOS] ?: 0) + 1
                    bytes[ExplorerCategory.PHOTOS] = (bytes[ExplorerCategory.PHOTOS] ?: 0L) + item.sizeBytes
                }
                item.isVideo -> {
                    counts[ExplorerCategory.VIDEOS] = (counts[ExplorerCategory.VIDEOS] ?: 0) + 1
                    bytes[ExplorerCategory.VIDEOS] = (bytes[ExplorerCategory.VIDEOS] ?: 0L) + item.sizeBytes
                }
                item.isAudio -> {
                    counts[ExplorerCategory.MUSIC] = (counts[ExplorerCategory.MUSIC] ?: 0) + 1
                    bytes[ExplorerCategory.MUSIC] = (bytes[ExplorerCategory.MUSIC] ?: 0L) + item.sizeBytes
                }
                item.isDocument -> {
                    counts[ExplorerCategory.DOCUMENTS] = (counts[ExplorerCategory.DOCUMENTS] ?: 0) + 1
                    bytes[ExplorerCategory.DOCUMENTS] = (bytes[ExplorerCategory.DOCUMENTS] ?: 0L) + item.sizeBytes
                }
            }
            if (isInDownloadsFolder(item)) {
                counts[ExplorerCategory.DOWNLOADS] = (counts[ExplorerCategory.DOWNLOADS] ?: 0) + 1
                bytes[ExplorerCategory.DOWNLOADS] = (bytes[ExplorerCategory.DOWNLOADS] ?: 0L) + item.sizeBytes
            }
        }
        return ExplorerCategory.entries.associateWith { cat ->
            CategorySummary(
                category = cat,
                itemCount = counts[cat] ?: 0,
                totalBytes = bytes[cat] ?: 0L
            )
        }
    }

    fun normalizedKey(item: FileItem): String = normalizeTagPath(item.pathOrUri, item.isSafUri)
}
