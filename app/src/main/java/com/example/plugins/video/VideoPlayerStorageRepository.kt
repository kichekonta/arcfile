package com.example.plugins.video

import android.content.Context
import com.example.core.plugin.ArcPluginScope
import com.example.data.FileItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages sandboxed persistence for VideoPlayerPlugin:
 * - Cache (`cacheDir/plugins/video_player`): regenerable playback telemetry / scrub index snapshots
 * - User Data (`filesDir/plugins/video_player`): per-video resume timestamps (`resume_positions.json`)
 * - Preferences (`ArcPluginScope.pluginPreferences`): playback speed, orientation, resize, repeat & auto-play
 */
class VideoPlayerStorageRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs by lazy { ArcPluginScope.pluginPreferences(appContext, VideoPlayerPlugin.ID) }

    private fun resumeFile(): File {
        val dir = VideoPlayerPlugin.dataDirectory(appContext, createIfMissing = true)
        return File(dir, RESUME_FILE_NAME)
    }

    private fun scrubMetaCacheFile(): File {
        val dir = VideoPlayerPlugin.cacheDirectory(appContext, createIfMissing = true)
        return File(dir, CACHE_INDEX_FILE_NAME)
    }

    fun loadPreferences(): VideoPlayerPreferences {
        val speed = prefs.getFloat(KEY_SPEED, 1.0f).coerceIn(0.25f, 3.0f)
        val orientation = runCatching {
            VideoOrientationMode.valueOf(
                prefs.getString(KEY_ORIENTATION, VideoOrientationMode.AUTO.name)
                    ?: VideoOrientationMode.AUTO.name
            )
        }.getOrDefault(VideoOrientationMode.AUTO)
        val resize = runCatching {
            VideoResizeMode.valueOf(
                prefs.getString(KEY_RESIZE, VideoResizeMode.FIT.name)
                    ?: VideoResizeMode.FIT.name
            )
        }.getOrDefault(VideoResizeMode.FIT)
        val repeat = runCatching {
            VideoRepeatMode.valueOf(
                prefs.getString(KEY_REPEAT, VideoRepeatMode.OFF.name)
                    ?: VideoRepeatMode.OFF.name
            )
        }.getOrDefault(VideoRepeatMode.OFF)
        val autoPlayNext = prefs.getBoolean(KEY_AUTO_PLAY_NEXT, true)
        val muted = prefs.getBoolean(KEY_MUTED, false)
        return VideoPlayerPreferences(
            playbackSpeed = speed,
            orientationMode = orientation,
            resizeMode = resize,
            repeatMode = repeat,
            autoPlayNext = autoPlayNext,
            isMuted = muted
        )
    }

    fun saveSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_SPEED, speed.coerceIn(0.25f, 3.0f)).apply()
    }

    fun saveOrientationMode(mode: VideoOrientationMode) {
        prefs.edit().putString(KEY_ORIENTATION, mode.name).apply()
    }

    fun saveResizeMode(mode: VideoResizeMode) {
        prefs.edit().putString(KEY_RESIZE, mode.name).apply()
    }

    fun saveRepeatMode(mode: VideoRepeatMode) {
        prefs.edit().putString(KEY_REPEAT, mode.name).apply()
    }

    fun saveAutoPlayNext(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY_NEXT, enabled).apply()
    }

    fun saveMuted(muted: Boolean) {
        prefs.edit().putBoolean(KEY_MUTED, muted).apply()
    }

    fun resumeKeyFor(item: FileItem): String = "${item.pathOrUri}|${item.sizeBytes}"

    fun loadResumePosition(item: FileItem): Long {
        val targetKey = resumeKeyFor(item)
        return loadResumeEntries().firstOrNull { it.key == targetKey }?.positionMs ?: 0L
    }

    fun saveResumePosition(item: FileItem, positionMs: Long, durationMs: Long) {
        val entries = loadResumeEntries().toMutableList()
        val key = resumeKeyFor(item)
        entries.removeAll { it.key == key }
        val shouldPersist = positionMs >= 2_500L && (durationMs <= 0L || positionMs < (durationMs - 3_000L))
        if (shouldPersist) {
            entries.add(
                0,
                VideoResumeEntry(
                    key = key,
                    title = item.name,
                    positionMs = positionMs,
                    durationMs = durationMs.coerceAtLeast(0L),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        val bounded = entries.take(MAX_RESUME_ENTRIES)
        writeResumeEntries(bounded)
    }

    fun recordCacheSessionMetadata(item: FileItem, track: VideoTrackSummary) {
        runCatching {
            val file = scrubMetaCacheFile()
            val root = if (file.exists()) {
                runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
            } else {
                JSONObject()
            }
            val payload = JSONObject().apply {
                put("title", item.name)
                put("width", track.width)
                put("height", track.height)
                put("codec", track.codec)
                put("bitrateKbps", track.bitrateKbps)
                put("fps", track.frameRate.toDouble())
                put("cachedAt", System.currentTimeMillis())
            }
            root.put(resumeKeyFor(item), payload)
            // Keep cache index bounded cleanly to 120 items
            while (root.length() > MAX_CACHE_INDEX_ENTRIES) {
                val firstKey = root.keys().next()
                root.remove(firstKey)
            }
            file.writeText(root.toString())
        }
    }

    fun loadResumeEntries(): List<VideoResumeEntry> = runCatching {
        val file = resumeFile()
        if (!file.exists()) return emptyList()
        val raw = file.readText()
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        buildList(array.length()) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val key = obj.optString("key")
                if (key.isBlank()) continue
                add(
                    VideoResumeEntry(
                        key = key,
                        title = obj.optString("title"),
                        positionMs = obj.optLong("positionMs", 0L),
                        durationMs = obj.optLong("durationMs", 0L),
                        updatedAt = obj.optLong("updatedAt", 0L)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun writeResumeEntries(entries: List<VideoResumeEntry>) {
        runCatching {
            val array = JSONArray()
            entries.forEach { entry ->
                array.put(
                    JSONObject().apply {
                        put("key", entry.key)
                        put("title", entry.title)
                        put("positionMs", entry.positionMs)
                        put("durationMs", entry.durationMs)
                        put("updatedAt", entry.updatedAt)
                    }
                )
            }
            resumeFile().writeText(array.toString())
        }
    }

    companion object {
        private const val RESUME_FILE_NAME = "resume_positions.json"
        private const val CACHE_INDEX_FILE_NAME = "playback_cache_meta.json"
        private const val MAX_RESUME_ENTRIES = 150
        private const val MAX_CACHE_INDEX_ENTRIES = 120

        private const val KEY_SPEED = "playback_speed"
        private const val KEY_ORIENTATION = "orientation_mode"
        private const val KEY_RESIZE = "resize_mode"
        private const val KEY_REPEAT = "repeat_mode"
        private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
        private const val KEY_MUTED = "is_muted"
    }
}
