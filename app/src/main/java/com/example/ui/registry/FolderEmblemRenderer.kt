package com.example.ui.registry

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

enum class FolderEmblem {
    NONE,
    DOWNLOADS,
    PICTURES,
    VIDEOS,
    MUSIC,
    DOCUMENTS,
    APPS_SYSTEM,
    ARCHIVE
}

private val DOWNLOADS_FOLDER_NAMES = setOf("download", "downloads")
private val PICTURES_FOLDER_NAMES = setOf("pictures", "picture", "photos", "photo", "dcim", "camera", "screenshots", "images")
private val VIDEOS_FOLDER_NAMES = setOf("movies", "movie", "videos", "video", "clips")
private val MUSIC_FOLDER_NAMES = setOf("music", "audio", "songs", "podcasts", "recordings", "ringtones", "alarms", "notifications", "audiobooks")
private val DOCUMENTS_FOLDER_NAMES = setOf("documents", "document", "docs", "doc", "notes", "books")
private val APPS_SYSTEM_FOLDER_NAMES = setOf("android", "apps", "app", "workspace", "system", "code", "projects")
private val ARCHIVE_FOLDER_NAMES = setOf("archive", "archives", "backup", "backups", "zip")

fun resolveFolderEmblem(folderName: String): FolderEmblem {
    val name = folderName.trim().lowercase()
    return when {
        name in DOWNLOADS_FOLDER_NAMES || name.contains("download") -> FolderEmblem.DOWNLOADS
        name in PICTURES_FOLDER_NAMES -> FolderEmblem.PICTURES
        name in VIDEOS_FOLDER_NAMES -> FolderEmblem.VIDEOS
        name in MUSIC_FOLDER_NAMES -> FolderEmblem.MUSIC
        name in DOCUMENTS_FOLDER_NAMES -> FolderEmblem.DOCUMENTS
        name in APPS_SYSTEM_FOLDER_NAMES -> FolderEmblem.APPS_SYSTEM
        name in ARCHIVE_FOLDER_NAMES -> FolderEmblem.ARCHIVE
        else -> FolderEmblem.NONE
    }
}

fun DrawScope.drawFolderEmblem(
    emblem: FolderEmblem,
    tint: Color,
    centerY: Float = size.height * 0.565f,
    pocketHeight: Float = size.height * 0.52f
) {
    if (emblem == FolderEmblem.NONE) return
    val w = size.width
    val cx = w * 0.50f
    val cy = centerY
    val unit = (pocketHeight * 0.27f).coerceAtMost(minOf(w, size.height) * 0.16f)
    val stroke = (unit * 0.22f).coerceAtLeast(1.4f)

    when (emblem) {
        FolderEmblem.DOWNLOADS -> {
            // Arrow stem + chevron + rounded tray
            drawLine(
                color = tint,
                start = Offset(cx, cy - unit * 0.95f),
                end = Offset(cx, cy + unit * 0.35f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            val chevron = Path().apply {
                moveTo(cx - unit * 0.55f, cy - unit * 0.12f)
                lineTo(cx, cy + unit * 0.42f)
                lineTo(cx + unit * 0.55f, cy - unit * 0.12f)
            }
            drawPath(chevron, color = tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val tray = Path().apply {
                moveTo(cx - unit * 0.78f, cy + unit * 0.48f)
                lineTo(cx - unit * 0.78f, cy + unit * 0.85f)
                lineTo(cx + unit * 0.78f, cy + unit * 0.85f)
                lineTo(cx + unit * 0.78f, cy + unit * 0.48f)
            }
            drawPath(tray, color = tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        FolderEmblem.PICTURES -> {
            val boxW = unit * 1.7f
            val boxH = unit * 1.35f
            drawRoundRect(
                color = tint,
                topLeft = Offset(cx - boxW / 2f, cy - boxH / 2f),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(unit * 0.26f, unit * 0.26f),
                style = Stroke(stroke)
            )
            val peaks = Path().apply {
                moveTo(cx - boxW * 0.42f, cy + boxH * 0.36f)
                lineTo(cx - boxW * 0.12f, cy - boxH * 0.05f)
                lineTo(cx + boxW * 0.08f, cy + boxH * 0.20f)
                lineTo(cx + boxW * 0.24f, cy + boxH * 0.02f)
                lineTo(cx + boxW * 0.44f, cy + boxH * 0.34f)
            }
            drawPath(peaks, color = tint, style = Stroke(stroke * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color = tint, radius = unit * 0.16f, center = Offset(cx + boxW * 0.22f, cy - boxH * 0.20f))
        }

        FolderEmblem.VIDEOS -> {
            drawCircle(color = tint, radius = unit * 0.92f, center = Offset(cx, cy), style = Stroke(stroke))
            val play = Path().apply {
                moveTo(cx - unit * 0.28f, cy - unit * 0.42f)
                lineTo(cx + unit * 0.46f, cy)
                lineTo(cx - unit * 0.28f, cy + unit * 0.42f)
                close()
            }
            drawPath(play, color = tint)
        }

        FolderEmblem.MUSIC -> {
            val stemX = cx + unit * 0.22f
            drawCircle(color = tint, radius = unit * 0.32f, center = Offset(cx - unit * 0.14f, cy + unit * 0.48f))
            drawLine(
                color = tint,
                start = Offset(stemX - unit * 0.08f, cy + unit * 0.45f),
                end = Offset(stemX - unit * 0.08f, cy - unit * 0.72f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            val flag = Path().apply {
                moveTo(stemX - unit * 0.08f, cy - unit * 0.72f)
                quadraticTo(cx + unit * 0.68f, cy - unit * 0.56f, cx + unit * 0.58f, cy - unit * 0.14f)
            }
            drawPath(flag, color = tint, style = Stroke(stroke, cap = StrokeCap.Round))
        }

        FolderEmblem.DOCUMENTS -> {
            val lineW = unit * 0.72f
            drawLine(tint, Offset(cx - lineW, cy - unit * 0.48f), Offset(cx + lineW, cy - unit * 0.48f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(cx - lineW, cy), Offset(cx + lineW, cy), stroke, StrokeCap.Round)
            drawLine(tint, Offset(cx - lineW, cy + unit * 0.48f), Offset(cx + unit * 0.25f, cy + unit * 0.48f), stroke, StrokeCap.Round)
        }

        FolderEmblem.APPS_SYSTEM -> {
            val sq = unit * 0.62f
            val r = CornerRadius(unit * 0.16f, unit * 0.16f)
            val gap = unit * 0.16f
            drawRoundRect(tint, Offset(cx - gap - sq, cy - gap - sq), Size(sq, sq), r)
            drawRoundRect(tint, Offset(cx + gap, cy - gap - sq), Size(sq, sq), r)
            drawRoundRect(tint, Offset(cx - gap - sq, cy + gap), Size(sq, sq), r)
            drawRoundRect(tint, Offset(cx + gap, cy + gap), Size(sq, sq), r, style = Stroke(stroke * 0.9f))
        }

        FolderEmblem.ARCHIVE -> {
            val box = unit * 1.45f
            drawRoundRect(
                color = tint,
                topLeft = Offset(cx - box / 2f, cy - box / 2f),
                size = Size(box, box),
                cornerRadius = CornerRadius(unit * 0.24f, unit * 0.24f),
                style = Stroke(stroke)
            )
            drawLine(tint, Offset(cx, cy - box * 0.46f), Offset(cx, cy + box * 0.46f), stroke * 0.9f, StrokeCap.Round)
            drawRoundRect(tint, Offset(cx - unit * 0.24f, cy - unit * 0.18f), Size(unit * 0.48f, unit * 0.36f), CornerRadius(unit * 0.1f, unit * 0.1f))
        }

        FolderEmblem.NONE -> Unit
    }
}
