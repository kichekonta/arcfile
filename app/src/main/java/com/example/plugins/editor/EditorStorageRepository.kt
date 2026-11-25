package com.example.plugins.editor

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.example.core.plugin.ArcPluginScope
import com.example.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest

/**
 * Isolated storage layer for TextEditorPlugin:
 * - File read/write/Save-As with explicit charset encoding support across local files & SAF URIs
 * - Regenerable syntax/preview cache stored under `cacheDir/plugins/text_editor`
 * - Persisted Last Opened entries, unsaved drafts & editor preferences under `filesDir/plugins/text_editor`
 *   and `ArcPluginScope.pluginPreferences(context, TextEditorPlugin.ID)`
 */
class EditorStorageRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val prefs by lazy { ArcPluginScope.pluginPreferences(appContext, TextEditorPlugin.ID) }

    companion object {
        private const val KEY_THEME = "editor_theme"
        private const val KEY_WORD_WRAP = "editor_word_wrap"
        private const val KEY_DEFAULT_ENCODING = "editor_default_encoding"
        private const val LAST_OPENED_FILE = "last_opened.json"
        private const val DRAFTS_DIR = "drafts"
        private const val PREVIEW_CACHE_DIR = "preview_cache"
        private const val MAX_RECENT_ITEMS = 30
        private const val MAX_READ_BYTES = 1_572_864 // 1.5 MB safe edit cap
    }

    fun loadThemePreset(): EditorThemePreset =
        EditorThemePreset.fromName(prefs.getString(KEY_THEME, EditorThemePreset.CLASSIC_LIGHT.name))

    fun saveThemePreset(preset: EditorThemePreset) {
        prefs.edit().putString(KEY_THEME, preset.name).apply()
    }

    fun loadWordWrap(): Boolean = prefs.getBoolean(KEY_WORD_WRAP, true)

    fun saveWordWrap(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WORD_WRAP, enabled).apply()
    }

    fun loadDefaultEncoding(): String =
        prefs.getString(KEY_DEFAULT_ENCODING, EditorCharsetOption.UTF_8.charsetName)
            ?: EditorCharsetOption.UTF_8.charsetName

    fun saveDefaultEncoding(charsetName: String) {
        prefs.edit().putString(KEY_DEFAULT_ENCODING, charsetName).apply()
    }

    suspend fun readFileContent(
        item: FileItem,
        charsetName: String = "UTF-8"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
            val stream = if (item.isSafUri) {
                appContext.contentResolver.openInputStream(Uri.parse(item.pathOrUri))
            } else {
                File(item.pathOrUri).inputStream()
            }
            stream?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var remaining = MAX_READ_BYTES
                while (remaining > 0) {
                    val n = input.read(buf, 0, minOf(buf.size, remaining))
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    remaining -= n
                }
                String(out.toByteArray(), charset)
            } ?: ""
        }
    }

    suspend fun writeFileContent(
        item: FileItem,
        content: String,
        charsetName: String = "UTF-8"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
            val bytes = content.toByteArray(charset)
            if (item.isSafUri) {
                appContext.contentResolver.openOutputStream(Uri.parse(item.pathOrUri), "wt")?.use { out ->
                    out.write(bytes)
                } ?: error("Cannot write to document URI")
            } else {
                val file = File(item.pathOrUri)
                file.parentFile?.let { if (!it.exists()) it.mkdirs() }
                file.writeBytes(bytes)
            }
            Unit
        }
    }

    suspend fun saveAsNewFile(
        parentPathOrUri: String,
        isSafParent: Boolean,
        fileName: String,
        content: String,
        charsetName: String = "UTF-8",
        overwriteExisting: Boolean = false
    ): Result<FileItem> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = fileName.trim()
            require(cleanName.isNotEmpty() && !cleanName.contains('/') && !cleanName.contains('\\')) {
                "Enter a valid file name"
            }
            val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
            val bytes = content.toByteArray(charset)
            val ext = cleanName.substringAfterLast('.', "txt").lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "text/plain"

            if (isSafParent) {
                val parentUri = Uri.parse(parentPathOrUri)
                val normalizedParent = normalizeSafParentUri(parentUri)
                val docUri = DocumentsContract.createDocument(
                    appContext.contentResolver,
                    normalizedParent,
                    mime,
                    cleanName
                ) ?: error("Unable to create '$cleanName' in mounted drive")
                appContext.contentResolver.openOutputStream(docUri, "wt")?.use { out ->
                    out.write(bytes)
                } ?: error("Unable to write '$cleanName'")
                val now = System.currentTimeMillis()
                FileItem(
                    id = docUri.toString(),
                    name = cleanName,
                    pathOrUri = docUri.toString(),
                    parentPathOrUri = parentPathOrUri,
                    isSafUri = true,
                    isDirectory = false,
                    sizeBytes = bytes.size.toLong(),
                    lastModifiedMillis = now,
                    extension = ext,
                    mimeType = mime
                )
            } else {
                val parentDir = File(parentPathOrUri)
                if (!parentDir.exists()) parentDir.mkdirs()
                val target = File(parentDir, cleanName)
                if (!overwriteExisting) {
                    require(!target.exists()) { "'$cleanName' already exists in this folder" }
                }
                target.writeBytes(bytes)
                FileItem(
                    id = target.absolutePath,
                    name = target.name,
                    pathOrUri = target.absolutePath,
                    parentPathOrUri = parentDir.absolutePath,
                    isSafUri = false,
                    isDirectory = false,
                    sizeBytes = target.length(),
                    lastModifiedMillis = target.lastModified(),
                    extension = ext,
                    mimeType = mime
                )
            }
        }
    }

    private fun normalizeSafParentUri(parentUri: Uri): Uri {
        val raw = parentUri.toString()
        if (raw.contains("/document/")) return parentUri
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(parentUri) }.getOrNull()
        return if (treeDocId != null) {
            DocumentsContract.buildDocumentUriUsingTree(parentUri, treeDocId)
        } else {
            parentUri
        }
    }

    suspend fun loadRecentEntries(): List<EditorRecentEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(TextEditorPlugin.dataDirectory(appContext), LAST_OPENED_FILE)
            if (!file.exists()) return@withContext emptyList()
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val path = obj.optString("pathOrUri")
                    val name = obj.optString("name")
                    if (path.isBlank() || name.isBlank()) continue
                    add(
                        EditorRecentEntry(
                            pathOrUri = path,
                            isSafUri = obj.optBoolean("isSafUri", false),
                            name = name,
                            parentPathOrUri = obj.optString("parentPathOrUri", ""),
                            mimeType = obj.optString("mimeType", "text/plain"),
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            lastOpenedMillis = obj.optLong("lastOpenedMillis", 0L),
                            charsetName = obj.optString("charsetName", "UTF-8")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun recordRecentEntry(entry: EditorRecentEntry): List<EditorRecentEntry> = withContext(Dispatchers.IO) {
        val current = loadRecentEntries()
            .filterNot { it.pathOrUri == entry.pathOrUri }
            .toMutableList()
        current.add(0, entry)
        val trimmed = current.take(MAX_RECENT_ITEMS)
        writeRecentEntries(trimmed)
        trimmed
    }

    suspend fun clearRecentEntries() = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(TextEditorPlugin.dataDirectory(appContext), LAST_OPENED_FILE)
            if (file.exists()) file.delete()
        }
        Unit
    }

    private fun writeRecentEntries(entries: List<EditorRecentEntry>) {
        runCatching {
            val arr = JSONArray()
            entries.forEach { e ->
                arr.put(
                    JSONObject().apply {
                        put("pathOrUri", e.pathOrUri)
                        put("isSafUri", e.isSafUri)
                        put("name", e.name)
                        put("parentPathOrUri", e.parentPathOrUri)
                        put("mimeType", e.mimeType)
                        put("sizeBytes", e.sizeBytes)
                        put("lastOpenedMillis", e.lastOpenedMillis)
                        put("charsetName", e.charsetName)
                    }
                )
            }
            val file = File(TextEditorPlugin.dataDirectory(appContext), LAST_OPENED_FILE)
            file.writeText(arr.toString(), Charsets.UTF_8)
        }
    }

    suspend fun saveDraft(tabKey: String, title: String, content: String) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(TextEditorPlugin.dataDirectory(appContext), DRAFTS_DIR).apply { mkdirs() }
            val file = File(dir, "${sha1Short(tabKey)}.json")
            val payload = JSONObject().apply {
                put("tabKey", tabKey)
                put("title", title)
                put("updatedAt", System.currentTimeMillis())
                put("content", content)
            }
            file.writeText(payload.toString(), Charsets.UTF_8)
        }
        Unit
    }

    suspend fun deleteDraft(tabKey: String) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(TextEditorPlugin.dataDirectory(appContext), DRAFTS_DIR)
            val file = File(dir, "${sha1Short(tabKey)}.json")
            if (file.exists()) file.delete()
        }
        Unit
    }

    suspend fun writePreviewCacheSnapshot(tabKey: String, lineCount: Int, wordCount: Int, highlightMode: String) =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(TextEditorPlugin.cacheDirectory(appContext), PREVIEW_CACHE_DIR).apply { mkdirs() }
                val file = File(dir, "${sha1Short(tabKey)}.cache")
                file.writeText("lines=$lineCount\nwords=$wordCount\nmode=$highlightMode\n", Charsets.UTF_8)
            }
            Unit
        }

    private fun sha1Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }
}
