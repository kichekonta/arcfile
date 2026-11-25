package com.example.plugins.pdf

import com.example.core.plugin.ArcPlugin
import com.example.core.plugin.ArcPluginRole
import com.example.data.ArcPluginStorageSpec

/**
 * Isolated PDF Reader plugin specification & lifecycle storage hooks.
 * Keeps rendered page tile bitmaps separate from last-read page markers, bookmarks & annotations.
 */
object PdfReaderPlugin : ArcPlugin {

    const val ID = "pdf_reader"

    override val role: ArcPluginRole = ArcPluginRole.VIEWER

    override val supportedExtensions: Set<String> = setOf("pdf")

    override val supportedMimePrefixes: Set<String> = setOf("application/pdf")

    override val storageSpec: ArcPluginStorageSpec = ArcPluginStorageSpec(
        id = ID,
        title = "PDF Reader Plugin",
        subtitle = "High-DPI document renderer & page tile cache",
        cacheRelativeDir = "plugins/$ID",
        dataRelativeDir = "plugins/$ID",
        cacheDescription = "Rendered page bitmaps & zoom tile buffers",
        dataDescription = "Last read pages, PDF bookmarks & annotations",
        isActiveBuiltin = false
    )
}
