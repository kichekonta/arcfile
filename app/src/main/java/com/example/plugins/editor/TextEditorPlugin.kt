package com.example.plugins.editor

import android.content.Context
import com.example.core.plugin.ArcPlugin
import com.example.core.plugin.ArcPluginRole
import com.example.core.plugin.ArcPluginScope
import com.example.core.plugin.PluginExtraStorageMetrics
import com.example.data.ArcPluginStorageSpec
import com.example.data.FileItem

/**
 * Isolated Note, Text & Code Editor plugin specification & lifecycle storage hooks.
 * Handles plain text, source code, Markdown, JSON, XML, and HTML files with isolated
 * cache (`preview_cache`) and user data (`last_opened.json`, `drafts/`, theme/wrap prefs).
 */
object TextEditorPlugin : ArcPlugin {

    const val ID = "text_editor"

    override val role: ArcPluginRole = ArcPluginRole.EDITOR

    override val supportedExtensions: Set<String> = FileItem.TEXT_PREVIEW_EXTENSIONS

    override val supportedMimePrefixes: Set<String> = setOf("text/")

    override val storageSpec: ArcPluginStorageSpec = ArcPluginStorageSpec(
        id = ID,
        title = "Note & Code Editor",
        subtitle = "Multi-tab editor, Markdown/HTML runner & syntax workspace",
        cacheRelativeDir = "plugins/$ID",
        dataRelativeDir = "plugins/$ID",
        cacheDescription = "Rendered preview snapshots & syntax analysis cache",
        dataDescription = "Last opened history, unsaved drafts, theme & wrap settings",
        isActiveBuiltin = true
    )

    override fun canHandle(item: FileItem): Boolean = !item.isDirectory && item.isTextEditable

    override suspend fun inspectExtraStorage(context: Context): PluginExtraStorageMetrics {
        val prefEntries = ArcPluginScope.countPreferencesEntries(context, id)
        return PluginExtraStorageMetrics(dataItems = prefEntries)
    }

    override suspend fun onClearCache(context: Context): Long = 0L

    override suspend fun onClearUserData(context: Context): Long {
        ArcPluginScope.clearPreferences(context, id)
        return 0L
    }
}
