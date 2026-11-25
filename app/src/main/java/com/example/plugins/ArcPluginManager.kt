package com.example.plugins

import com.example.core.plugin.ArcPlugin
import com.example.core.plugin.ArcPluginRole
import com.example.data.FileItem
import com.example.plugins.archive.ArchiveToolsPlugin
import com.example.plugins.editor.TextEditorPlugin
import com.example.plugins.pdf.PdfReaderPlugin
import com.example.plugins.video.VideoPlayerPlugin
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Single source of truth for all registered viewer/player/editor/tool plugins.
 * New plugins register here once to automatically participate in file-type dispatch
 * and Settings -> Cache, Data & Plugins inspection and clearing.
 */
object ArcPluginManager {

    private val builtinPlugins: List<ArcPlugin> = listOf(
        VideoPlayerPlugin,
        PdfReaderPlugin,
        TextEditorPlugin,
        ArchiveToolsPlugin
    )

    private val dynamicPlugins = CopyOnWriteArrayList<ArcPlugin>()

    val plugins: List<ArcPlugin>
        get() = if (dynamicPlugins.isEmpty()) builtinPlugins else builtinPlugins + dynamicPlugins

    fun findById(pluginId: String): ArcPlugin? =
        plugins.firstOrNull { it.id == pluginId }

    fun pluginsByRole(role: ArcPluginRole): List<ArcPlugin> =
        plugins.filter { it.role == role }

    /**
     * Finds any registered plugin capable of opening [item] (active or planned).
     */
    fun resolvePluginFor(item: FileItem): ArcPlugin? =
        plugins.firstOrNull { it.canHandle(item) }

    /**
     * Finds an active built-in plugin that currently handles [item] in-app.
     */
    fun resolveActiveHandler(item: FileItem): ArcPlugin? =
        plugins.firstOrNull { it.isActiveBuiltin && it.canHandle(item) }

    fun register(plugin: ArcPlugin) {
        if (plugins.none { it.id == plugin.id }) {
            dynamicPlugins.add(plugin)
        }
    }

    fun unregisterDynamic(pluginId: String) {
        dynamicPlugins.removeAll { it.id == pluginId }
    }
}
