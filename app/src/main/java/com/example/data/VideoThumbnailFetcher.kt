package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.pxOrElse
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lightweight native MediaMetadataRetriever-backed Coil Fetcher that extracts sharp video
 * poster frames for both local storage paths and SAF content:// URIs without extra dependencies.
 */
class VideoThumbnailFetcher(
    private val context: Context,
    private val sourceFile: File?,
    private val sourceUri: Uri?,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val targetWidth = options.size.width.pxOrElse { DEFAULT_THUMB_PX }.coerceIn(96, 640)
        val targetHeight = options.size.height.pxOrElse { DEFAULT_THUMB_PX }.coerceIn(96, 640)
        val rawBitmap = extractFrame(targetWidth, targetHeight) ?: return null
        val scaled = downscaleIfNeeded(rawBitmap, targetWidth, targetHeight)
        return DrawableResult(
            drawable = scaled.toDrawable(context.resources),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    private fun extractFrame(targetWidth: Int, targetHeight: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            when {
                sourceFile != null -> {
                    if (!sourceFile.exists() || !sourceFile.canRead() || sourceFile.length() == 0L) return null
                    retriever.setDataSource(sourceFile.absolutePath)
                }
                sourceUri != null -> retriever.setDataSource(context, sourceUri)
                else -> return null
            }

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L) ?: 0L

            val frameTimeUs = when {
                durationMs > 2_000L -> (durationMs / 10L).coerceIn(500L, 5_000L) * 1_000L
                durationMs > 400L -> (durationMs / 2L) * 1_000L
                else -> -1L
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val frame = runCatching {
                    retriever.getScaledFrameAtTime(
                        frameTimeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        targetWidth,
                        targetHeight
                    )
                }.getOrNull()
                if (frame != null) return frame
            }

            val full = runCatching {
                retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }.getOrNull() ?: runCatching { retriever.frameAtTime }.getOrNull()
            if (full != null) return full
        } catch (_: Throwable) {
            // Fall through to ThumbnailUtils fallback below
        } finally {
            runCatching { retriever.release() }
        }

        val localFile = sourceFile
        if (localFile != null && localFile.exists() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return runCatching {
                ThumbnailUtils.createVideoThumbnail(localFile, Size(targetWidth, targetHeight), null)
            }.getOrNull()
        }
        return null
    }

    private fun downscaleIfNeeded(src: Bitmap, maxW: Int, maxH: Int): Bitmap {
        if (src.width <= maxW && src.height <= maxH) return src
        val ratio = min(maxW.toFloat() / src.width.toFloat(), maxH.toFloat() / src.height.toFloat())
        val outW = (src.width * ratio).roundToInt().coerceAtLeast(1)
        val outH = (src.height * ratio).roundToInt().coerceAtLeast(1)
        return runCatching { Bitmap.createScaledBitmap(src, outW, outH, true) }.getOrDefault(src).also {
            if (it !== src) src.recycle()
        }
    }

    class Factory(private val appContext: Context) : Fetcher.Factory<Any> {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            return when (data) {
                is File -> {
                    val ext = data.extension.lowercase()
                    if (ext in FileItem.VIDEO_EXTENSIONS) {
                        VideoThumbnailFetcher(appContext, sourceFile = data, sourceUri = null, options = options)
                    } else null
                }
                is String -> {
                    if (isVideoDataString(appContext, data)) {
                        if (data.startsWith("content://") || data.startsWith("file://")) {
                            VideoThumbnailFetcher(appContext, sourceFile = null, sourceUri = Uri.parse(data), options = options)
                        } else {
                            VideoThumbnailFetcher(appContext, sourceFile = File(data), sourceUri = null, options = options)
                        }
                    } else null
                }
                is Uri -> {
                    if (isVideoUri(appContext, data)) {
                        VideoThumbnailFetcher(appContext, sourceFile = null, sourceUri = data, options = options)
                    } else null
                }
                else -> null
            }
        }

        private fun isVideoDataString(context: Context, value: String): Boolean {
            if (value.startsWith("content://")) {
                return isVideoUri(context, Uri.parse(value))
            }
            val clean = value.substringBefore('?').substringBefore('#')
            val ext = clean.substringAfterLast('.', "").lowercase()
            return ext in FileItem.VIDEO_EXTENSIONS
        }

        private fun isVideoUri(context: Context, uri: Uri): Boolean {
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
            if (mime.startsWith("video/")) return true
            val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
            return ext in FileItem.VIDEO_EXTENSIONS
        }
    }

    private companion object {
        const val DEFAULT_THUMB_PX = 240
    }
}
