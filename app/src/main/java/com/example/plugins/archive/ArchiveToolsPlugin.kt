package com.example.plugins.archive

import com.example.core.plugin.ArcPlugin
import com.example.core.plugin.ArcPluginRole
import com.example.data.ArcPluginStorageSpec
import com.example.data.FileItem

/**
 * Isolated Archive & Extractor Engine plugin specification & temp-prefix cleanup definition.
 */
object ArchiveToolsPlugin : ArcPlugin {

    const val ID = "archive_tools"

    override val role: ArcPluginRole = ArcPluginRole.TOOL

    override val supportedExtensions: Set<String> = FileItem.ARCHIVE_EXTENSIONS

    override val supportedMimePrefixes: Set<String> = FileItem.ARCHIVE_MIME_TYPES

    override val storageSpec: ArcPluginStorageSpec = ArcPluginStorageSpec(
        id = ID,
        title = "Archive & Extractor Engine",
        subtitle = "ZIP, 7z & RAR inspection and extraction workspace",
        cacheRelativeDir = "plugins/$ID",
        dataRelativeDir = "plugins/$ID",
        cacheDescription = "Extracted archive preview files & inspection buffers",
        dataDescription = "Compression presets & archive tool history",
        isActiveBuiltin = true,
        includeRootTempPrefixes = listOf("arc_extract_stage_", "arc_inspect_", "arc_out_")
    )

    override fun canHandle(item: FileItem): Boolean = !item.isDirectory && item.isExtractableArchive
}

enum class CompressedFormatFilter(val id: String, val label: String) {
    ALL("ALL", "All"),
    ZIP("ZIP", "ZIP"),
    RAR("RAR", "RAR"),
    SEVEN_Z("SEVEN_Z", "7z"),
    TAR_GZ("TAR_GZ", "TAR / GZ"),
    APK("APK", "APK / XAPK"),
    OTHER("OTHER", "Other");

    fun matches(item: FileItem): Boolean {
        val ext = item.extension.lowercase()
        val mime = item.mimeType.lowercase()
        return when (this) {
            ALL -> true
            ZIP -> ext == "zip" || ext == "cbz" || mime == "application/zip" || mime == "application/x-zip-compressed"
            RAR -> ext == "rar" || ext == "cbr" || mime == "application/vnd.rar" || mime == "application/x-rar-compressed"
            SEVEN_Z -> ext == "7z" || ext == "cb7" || mime == "application/x-7z-compressed"
            TAR_GZ -> ext in TAR_GZ_EXTENSIONS || mime in TAR_GZ_MIME_TYPES
            APK -> ext in APK_EXTENSIONS || mime == "application/vnd.android.package-archive"
            OTHER -> !ZIP.matches(item) && !RAR.matches(item) && !SEVEN_Z.matches(item) &&
                !TAR_GZ.matches(item) && !APK.matches(item)
        }
    }

    companion object {
        private val TAR_GZ_EXTENSIONS = setOf("tar", "gz", "tgz", "bz2", "tbz", "tbz2", "xz", "txz", "zst", "lz4", "lzma")
        private val TAR_GZ_MIME_TYPES = setOf(
            "application/x-tar",
            "application/gzip",
            "application/x-gzip",
            "application/x-bzip2",
            "application/x-xz",
            "application/zstd",
            "application/x-lz4",
            "application/x-lzma"
        )
        private val APK_EXTENSIONS = setOf("apk", "xapk", "apks", "apkm", "aab")

        fun fromId(id: String?): CompressedFormatFilter =
            entries.firstOrNull { it.id == id } ?: ALL
    }
}
