package com.example.core.plugin

import android.content.Context
import android.content.SharedPreferences
import com.example.data.ArcPluginStorageSpec
import com.example.data.FileItem
import java.io.File

/**
 * Classification of plugin capabilities within Arc Files.
 */
enum class ArcPluginRole {
    PLAYER,
    VIEWER,
    EDITOR,
    TOOL
}

/**
 * Allows plugins that persist state in Preferences, DataStore, or Room tables outside
 * `filesDir/plugins/<id>` to surface accurate counts/bytes in Settings -> Cache & Data Manager.
 */
data class PluginExtraStorageMetrics(
    val cacheBytes: Long = 0L,
    val cacheItems: Int = 0,
    val dataBytes: Long = 0L,
    val dataItems: Int = 0
)

/**
 * Helpers providing isolated per-plugin cache, user-data, and preference namespaces.
 */
object ArcPluginScope {
    fun pluginCacheDir(context: Context, relativeDir: String, createIfMissing: Boolean = true): File {
        val dir = File(context.applicationContext.cacheDir, relativeDir)
        if (createIfMissing && !dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun pluginDataDir(context: Context, relativeDir: String, createIfMissing: Boolean = true): File {
        val dir = File(context.applicationContext.filesDir, relativeDir)
        if (createIfMissing && !dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun preferencesName(pluginId: String): String = "arc_plugin_${pluginId}_state"

    fun pluginPreferences(context: Context, pluginId: String): SharedPreferences =
        context.applicationContext.getSharedPreferences(preferencesName(pluginId), Context.MODE_PRIVATE)

    fun countPreferencesEntries(context: Context, pluginId: String): Int =
        runCatching { pluginPreferences(context, pluginId).all.size }.getOrDefault(0)

    fun clearPreferences(context: Context, pluginId: String): Int {
        val prefs = pluginPreferences(context, pluginId)
        val count = runCatching { prefs.all.size }.getOrDefault(0)
        if (count > 0) {
            prefs.edit().clear().apply()
        }
        return count
    }
}

/**
 * Formal contract every built-in or future reader/player/editor/tool plugin implements.
 * Isolates file-type matching, sandboxed cache vs user-data storage, and manual cleanup hooks.
 */
interface ArcPlugin {
    val storageSpec: ArcPluginStorageSpec
    val role: ArcPluginRole
    val supportedExtensions: Set<String>
    val supportedMimePrefixes: Set<String>

    val id: String
        get() = storageSpec.id

    val isActiveBuiltin: Boolean
        get() = storageSpec.isActiveBuiltin

    fun canHandle(item: FileItem): Boolean {
        if (item.isDirectory) return false
        val ext = item.extension.lowercase()
        if (ext.isNotEmpty() && ext in supportedExtensions) return true
        val mime = item.mimeType.lowercase()
        if (mime.isNotEmpty() && supportedMimePrefixes.any { prefix ->
                if (prefix.endsWith("/")) mime.startsWith(prefix) else mime == prefix
            }
        ) {
            return true
        }
        return false
    }

    fun cacheDirectory(context: Context, createIfMissing: Boolean = true): File =
        ArcPluginScope.pluginCacheDir(context, storageSpec.cacheRelativeDir, createIfMissing)

    fun dataDirectory(context: Context, createIfMissing: Boolean = true): File =
        ArcPluginScope.pluginDataDir(context, storageSpec.dataRelativeDir, createIfMissing)

    suspend fun inspectExtraStorage(context: Context): PluginExtraStorageMetrics {
        val prefEntries = ArcPluginScope.countPreferencesEntries(context, id)
        return PluginExtraStorageMetrics(dataItems = prefEntries)
    }

    /**
     * Plugin-specific cache hook invoked alongside sandboxed cache directory deletion.
     * Override to evict in-memory caches, WebView buffers, or decoder pools.
     */
    suspend fun onClearCache(context: Context): Long = 0L

    /**
     * Plugin-specific user-data hook invoked alongside sandboxed `filesDir/plugins/<id>` deletion.
     * Override to clear plugin-owned Room tables, DataStore keys, or playback/reading history.
     */
    suspend fun onClearUserData(context: Context): Long {
        ArcPluginScope.clearPreferences(context, id)
        return 0L
    }
}
