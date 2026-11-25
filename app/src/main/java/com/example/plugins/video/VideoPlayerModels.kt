package com.example.plugins.video

import android.content.pm.ActivityInfo
import com.example.data.FileItem
import java.util.Locale

enum class VideoOrientationMode(
    val label: String,
    val shortLabel: String,
    val activityOrientation: Int
) {
    AUTO("Auto Rotate", "Auto", ActivityInfo.SCREEN_ORIENTATION_SENSOR),
    LANDSCAPE("Landscape", "Land", ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
    PORTRAIT("Portrait", "Port", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

    fun next(): VideoOrientationMode = when (this) {
        AUTO -> LANDSCAPE
        LANDSCAPE -> PORTRAIT
        PORTRAIT -> AUTO
    }
}

enum class VideoResizeMode(
    val label: String,
    val shortLabel: String
) {
    FIT("Fit Screen", "Fit"),
    FILL("Fill / Crop", "Fill"),
    RATIO_16_9("16:9 Cinema", "16:9"),
    RATIO_4_3("4:3 Classic", "4:3"),
    STRETCH("Stretch", "Full");

    fun next(): VideoResizeMode = when (this) {
        FIT -> FILL
        FILL -> RATIO_16_9
        RATIO_16_9 -> RATIO_4_3
        RATIO_4_3 -> STRETCH
        STRETCH -> FIT
    }
}

enum class VideoRepeatMode(
    val label: String
) {
    OFF("Repeat Off"),
    ONE("Repeat One"),
    ALL("Repeat All");

    fun next(): VideoRepeatMode = when (this) {
        OFF -> ONE
        ONE -> ALL
        ALL -> OFF
    }
}

object VideoPlaybackSpeeds {
    val presets: List<Float> = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    fun formatSpeed(speed: Float): String {
        val rounded = ((speed * 100f).toInt() / 100f)
        return when {
            rounded % 1f == 0f -> "${rounded.toInt()}.0x"
            (rounded * 10f) % 1f == 0f -> String.format(Locale.US, "%.1fx", rounded)
            else -> String.format(Locale.US, "%.2fx", rounded)
        }
    }
}

data class VideoResumeEntry(
    val key: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

data class VideoTrackSummary(
    val codec: String = "",
    val bitrateKbps: Int = 0,
    val frameRate: Float = 0f,
    val width: Int = 0,
    val height: Int = 0
)

data class VideoPlayerPreferences(
    val playbackSpeed: Float = 1.0f,
    val orientationMode: VideoOrientationMode = VideoOrientationMode.AUTO,
    val resizeMode: VideoResizeMode = VideoResizeMode.FIT,
    val repeatMode: VideoRepeatMode = VideoRepeatMode.OFF,
    val autoPlayNext: Boolean = true,
    val isMuted: Boolean = false
)

data class VideoPlayerUiState(
    val queue: List<FileItem>,
    val activeIndex: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isPrepared: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val isMuted: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val orientationMode: VideoOrientationMode = VideoOrientationMode.AUTO,
    val resizeMode: VideoResizeMode = VideoResizeMode.FIT,
    val repeatMode: VideoRepeatMode = VideoRepeatMode.OFF,
    val autoPlayNext: Boolean = true,
    val isLocked: Boolean = false,
    val showControls: Boolean = true,
    val isPlaylistOpen: Boolean = false,
    val isSpeedSheetOpen: Boolean = false,
    val isInfoDialogOpen: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val trackSummary: VideoTrackSummary = VideoTrackSummary(),
    val seekOverlayDeltaSeconds: Int? = null,
    val scrubPreviewPositionMs: Long? = null,
    val brightnessOverlayPercent: Int? = null,
    val volumeOverlayPercent: Int? = null,
    val statusBannerMessage: String? = null,
    val errorMessage: String? = null
) {
    val activeItem: FileItem
        get() = queue.getOrElse(activeIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))) {
            queue.first()
        }

    val hasPrevious: Boolean
        get() = activeIndex > 0 || repeatMode == VideoRepeatMode.ALL && queue.size > 1

    val hasNext: Boolean
        get() = activeIndex < queue.lastIndex || repeatMode == VideoRepeatMode.ALL && queue.size > 1

    val naturalAspectRatio: Float
        get() = if (videoWidth > 0 && videoHeight > 0) {
            (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(0.32f, 3.0f)
        } else {
            16f / 9f
        }
}

fun formatVideoDuration(millis: Long): String {
    val safeSec = (millis.coerceAtLeast(0L) / 1000L)
    val hours = safeSec / 3600L
    val minutes = (safeSec % 3600L) / 60L
    val seconds = safeSec % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
