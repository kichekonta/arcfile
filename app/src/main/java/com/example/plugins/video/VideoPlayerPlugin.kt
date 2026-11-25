package com.example.plugins.video

import android.content.Context
import com.example.core.plugin.ArcPlugin
import com.example.core.plugin.ArcPluginRole
import com.example.core.plugin.ArcPluginScope
import com.example.core.plugin.PluginExtraStorageMetrics
import com.example.data.ArcPluginStorageSpec
import com.example.data.MediaThumbnailLoader

/**
 * Isolated Video Player plugin specification & lifecycle storage hooks.
 * Keeps decoder/scrubber metadata caches separate from resume timestamps, speed & orientation preferences.
 */
object VideoPlayerPlugin : ArcPlugin {

    const val ID = "video_player"

    override val role: ArcPluginRole = ArcPluginRole.PLAYER

    override val supportedExtensions: Set<String> = setOf(
        "mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "ts", "flv", "wmv"
    )

    override val supportedMimePrefixes: Set<String> = setOf("video/")

    override val storageSpec: ArcPluginStorageSpec = ArcPluginStorageSpec(
        id = ID,
        title = "Video Player Plugin",
        subtitle = "Media3 ExoPlayer with auto-orientation, seek gestures & speed controls",
        cacheRelativeDir = "plugins/$ID",
        dataRelativeDir = "plugins/$ID",
        cacheDescription = "Decoder probes, frame thumbnails & playback metadata cache",
        dataDescription = "Resume timestamps, playback speed, orientation & repeat settings",
        isActiveBuiltin = true
    )

    override suspend fun inspectExtraStorage(context: Context): PluginExtraStorageMetrics {
        val prefEntries = ArcPluginScope.countPreferencesEntries(context, id)
        return PluginExtraStorageMetrics(dataItems = prefEntries)
    }

    override suspend fun onClearCache(context: Context): Long {
        runCatching { MediaThumbnailLoader.clearMemoryCaches() }
        return 0L
    }

    override suspend fun onClearUserData(context: Context): Long {
        ArcPluginScope.clearPreferences(context, id)
        return 0L
    }
}
