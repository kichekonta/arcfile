package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Locale

data class MediaPreviewMeta(
    val width: Int = 0,
    val height: Int = 0,
    val aspectRatio: Float = 1.24f,
    val durationMillis: Long? = null,
    val videoFrame: Bitmap? = null,
    val exifOrientation: Int = ExifInterface.ORIENTATION_NORMAL
)

/**
 * Lazy, bounded-RAM loader for image dimensions/aspect ratio (raster, SVG, ICO),
 * video frame thumbnails, and APK embedded app icons.
 * Runs off the UI thread with bounded concurrency and caches results in byte-sized LruCaches.
 */
object MediaThumbnailLoader {

    private data class ApkCacheEntry(val bitmap: Bitmap?)

    private val maxCacheKb: Int =
        ((Runtime.getRuntime().maxMemory() / 1024L).toInt() / 16).coerceIn(4_096, 24_576)

    private val cache = object : LruCache<String, MediaPreviewMeta>(maxCacheKb) {
        override fun sizeOf(key: String, value: MediaPreviewMeta): Int {
            return value.videoFrame?.let { (it.allocationByteCount / 1024).coerceAtLeast(1) } ?: 1
        }
    }

    private val apkCacheKb: Int = (maxCacheKb / 2).coerceIn(2_048, 8_192)
    private val apkIconCache = object : LruCache<String, ApkCacheEntry>(apkCacheKb) {
        override fun sizeOf(key: String, value: ApkCacheEntry): Int {
            return value.bitmap?.let { (it.allocationByteCount / 1024).coerceAtLeast(1) } ?: 1
        }
    }

    // Prevents saturating IO/decoder threads when flinging through 100+ videos or APKs
    private val videoDecodeSemaphore = Semaphore(2)
    private val apkDecodeSemaphore = Semaphore(2)

    private val svgViewBoxRegex = Regex("""viewBox\s*=\s*["']\s*[\d.\-]+\s+[\d.\-]+\s+([\d.]+)\s+([\d.]+)\s*["']""", RegexOption.IGNORE_CASE)
    private val svgWidthRegex = Regex("""<svg[^>]*\bwidth\s*=\s*["']([\d.]+)(?:px|dp|pt)?["']""", RegexOption.IGNORE_CASE)
    private val svgHeightRegex = Regex("""<svg[^>]*\bheight\s*=\s*["']([\d.]+)(?:px|dp|pt)?["']""", RegexOption.IGNORE_CASE)

    fun cacheKey(item: FileItem, needVideoFrame: Boolean): String =
        "${item.pathOrUri}|${item.lastModifiedMillis}|$needVideoFrame"

    fun apkCacheKey(item: FileItem): String =
        "${item.pathOrUri}|${item.lastModifiedMillis}|${item.sizeBytes}"

    fun getCached(key: String): MediaPreviewMeta? = cache.get(key)

    fun getCached(item: FileItem, needVideoFrame: Boolean): MediaPreviewMeta? =
        cache.get(cacheKey(item, needVideoFrame))

    fun getCachedApkIcon(item: FileItem): Bitmap? =
        apkIconCache.get(apkCacheKey(item))?.bitmap

    fun hasCachedApkAttempt(item: FileItem): Boolean =
        apkIconCache.get(apkCacheKey(item)) != null

    fun clearMemoryCaches() {
        cache.evictAll()
        apkIconCache.evictAll()
    }

    suspend fun loadMeta(context: Context, item: FileItem, needVideoFrame: Boolean): MediaPreviewMeta? {
        if (!item.isImage && !item.isVideo) return null
        val key = cacheKey(item, needVideoFrame)
        cache.get(key)?.let { return it }

        val loaded = if (item.isImage) {
            withContext(Dispatchers.IO) { loadImageMeta(context, item) }
        } else {
            videoDecodeSemaphore.withPermit {
                cache.get(key)?.let { return it }
                withContext(Dispatchers.IO) { loadVideoMeta(context, item, needVideoFrame) }
            }
        }
        if (loaded != null) {
            cache.put(key, loaded)
        }
        return loaded
    }

    suspend fun loadApkIcon(context: Context, item: FileItem, maxSizePx: Int = 144): Bitmap? {
        if (!item.isApk) return null
        val key = apkCacheKey(item)
        apkIconCache.get(key)?.let { return it.bitmap }

        return apkDecodeSemaphore.withPermit {
            apkIconCache.get(key)?.let { return@withPermit it.bitmap }
            val decoded = withContext(Dispatchers.IO) { extractApkIconBitmap(context, item, maxSizePx) }
            apkIconCache.put(key, ApkCacheEntry(decoded))
            decoded
        }
    }

    private fun extractApkIconBitmap(context: Context, item: FileItem, maxSizePx: Int): Bitmap? = runCatching {
        val localFile: File
        var tempFile: File? = null
        if (item.isSafUri) {
            if (item.sizeBytes <= 0L || item.sizeBytes > 16L * 1024L * 1024L) return@runCatching null
            val cachedTmp = File.createTempFile("arc_apk_icon_", ".apk", context.cacheDir)
            tempFile = cachedTmp
            context.contentResolver.openInputStream(Uri.parse(item.pathOrUri))?.use { input ->
                cachedTmp.outputStream().use { out -> input.copyTo(out) }
            } ?: return@runCatching null
            localFile = cachedTmp
        } else {
            localFile = File(item.pathOrUri)
            if (!localFile.exists() || !localFile.canRead()) return@runCatching null
        }

        try {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(localFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(localFile.absolutePath, 0)
            } ?: return@runCatching null

            val appInfo = pkgInfo.applicationInfo ?: return@runCatching null
            appInfo.sourceDir = localFile.absolutePath
            appInfo.publicSourceDir = localFile.absolutePath
            val drawable = appInfo.loadIcon(pm) ?: return@runCatching null
            drawableToBoundedBitmap(drawable, maxSizePx = maxSizePx)
        } finally {
            tempFile?.delete()
        }
    }.getOrNull()

    private fun drawableToBoundedBitmap(drawable: Drawable, maxSizePx: Int): Bitmap? {
        if (drawable is BitmapDrawable) {
            val raw = drawable.bitmap
            if (raw != null && !raw.isRecycled) {
                if (raw.width <= maxSizePx && raw.height <= maxSizePx) return raw
                val scale = maxSizePx.toFloat() / maxOf(raw.width, raw.height).toFloat()
                val dstW = (raw.width * scale).toInt().coerceAtLeast(1)
                val dstH = (raw.height * scale).toInt().coerceAtLeast(1)
                return Bitmap.createScaledBitmap(raw, dstW, dstH, true)
            }
        }
        val rawW = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxSizePx
        val rawH = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxSizePx
        val scale = if (maxOf(rawW, rawH) > maxSizePx) {
            maxSizePx.toFloat() / maxOf(rawW, rawH).toFloat()
        } else 1f
        val w = (rawW * scale).toInt().coerceIn(24, maxSizePx)
        val h = (rawH * scale).toInt().coerceIn(24, maxSizePx)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }

    private fun loadImageMeta(context: Context, item: FileItem): MediaPreviewMeta? = runCatching {
        val ext = item.extension.lowercase()
        if (ext == "ico") {
            val dims = openImageStream(context, item)?.use { IcoDecoder.inspectDimensions(it) }
                ?: return@runCatching null
            val ratio = (dims.first.toFloat() / dims.second.coerceAtLeast(1).toFloat()).coerceIn(0.4f, 2.5f)
            return@runCatching MediaPreviewMeta(width = dims.first, height = dims.second, aspectRatio = ratio)
        }
        if (ext == "svg") {
            val dims = openImageStream(context, item)?.use { parseSvgDimensions(it) }
                ?: (512 to 512)
            val ratio = (dims.first.toFloat() / dims.second.coerceAtLeast(1).toFloat()).coerceIn(0.4f, 2.5f)
            return@runCatching MediaPreviewMeta(width = dims.first, height = dims.second, aspectRatio = ratio)
        }
        if (ext == "gif") {
            val dims = openImageStream(context, item)?.use { parseGifDimensions(it) }
            if (dims != null) {
                val ratio = (dims.first.toFloat() / dims.second.coerceAtLeast(1).toFloat()).coerceIn(0.4f, 2.5f)
                return@runCatching MediaPreviewMeta(width = dims.first, height = dims.second, aspectRatio = ratio)
            }
        }

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (item.isSafUri) {
            context.contentResolver.openInputStream(Uri.parse(item.pathOrUri))?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } else {
            BitmapFactory.decodeFile(item.pathOrUri, opts)
        }
        var w = opts.outWidth
        var h = opts.outHeight

        val exif: ExifInterface? = runCatching {
            if (item.isSafUri) {
                context.contentResolver.openInputStream(Uri.parse(item.pathOrUri))?.use { ExifInterface(it) }
            } else {
                ExifInterface(item.pathOrUri)
            }
        }.getOrNull()

        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        ) ?: ExifInterface.ORIENTATION_NORMAL

        if (w <= 0 || h <= 0) {
            // Fallback for iOS HEIC/HEIF/AVIF containers via EXIF tags or Android 9+ ImageDecoder header
            val exifW = exif?.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)?.takeIf { it > 0 }
                ?: exif?.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0) ?: 0
            val exifH = exif?.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)?.takeIf { it > 0 }
                ?: exif?.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0) ?: 0
            if (exifW > 0 && exifH > 0) {
                w = exifW
                h = exifH
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching {
                    val src = if (item.isSafUri) {
                        ImageDecoder.createSource(context.contentResolver, Uri.parse(item.pathOrUri))
                    } else {
                        ImageDecoder.createSource(File(item.pathOrUri))
                    }
                    ImageDecoder.decodeDrawable(src) { decoder, info, _ ->
                        w = info.size.width
                        h = info.size.height
                        decoder.setTargetSize(1, 1)
                    }
                }
            }
        }

        if (w <= 0 || h <= 0) return@runCatching null

        if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270) {
            val tmp = w; w = h; h = tmp
        }
        val ratio = (w.toFloat() / h.toFloat()).coerceIn(0.4f, 2.5f)
        MediaPreviewMeta(width = w, height = h, aspectRatio = ratio, exifOrientation = orientation)
    }.getOrNull()

    private fun openImageStream(context: Context, item: FileItem): InputStream? {
        return if (item.isSafUri) {
            context.contentResolver.openInputStream(Uri.parse(item.pathOrUri))
        } else {
            val f = File(item.pathOrUri)
            if (f.exists() && f.canRead()) f.inputStream() else null
        }
    }

    private fun parseSvgDimensions(input: InputStream): Pair<Int, Int>? {
        val buf = ByteArray(4096)
        var readTotal = 0
        while (readTotal < buf.size) {
            val n = input.read(buf, readTotal, buf.size - readTotal)
            if (n <= 0) break
            readTotal += n
        }
        if (readTotal <= 0) return null
        val header = String(buf, 0, readTotal, Charsets.UTF_8)
        svgViewBoxRegex.find(header)?.let { match ->
            val vw = match.groupValues[1].toFloatOrNull()?.toInt()
            val vh = match.groupValues[2].toFloatOrNull()?.toInt()
            if (vw != null && vh != null && vw > 0 && vh > 0) return vw to vh
        }
        val w = svgWidthRegex.find(header)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.toInt()
        val h = svgHeightRegex.find(header)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.toInt()
        return if (w != null && h != null && w > 0 && h > 0) (w to h) else null
    }

    private fun parseGifDimensions(input: InputStream): Pair<Int, Int>? {
        val header = ByteArray(10)
        var readTotal = 0
        while (readTotal < header.size) {
            val n = input.read(header, readTotal, header.size - readTotal)
            if (n <= 0) break
            readTotal += n
        }
        if (readTotal < 10) return null
        val sig = String(header, 0, 6, Charsets.US_ASCII)
        if (sig != "GIF87a" && sig != "GIF89a") return null
        val w = (header[6].toInt() and 0xFF) or ((header[7].toInt() and 0xFF) shl 8)
        val h = (header[8].toInt() and 0xFF) or ((header[9].toInt() and 0xFF) shl 8)
        return if (w > 0 && h > 0) (w to h) else null
    }

    private fun loadVideoMeta(context: Context, item: FileItem, needFrame: Boolean): MediaPreviewMeta? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            if (item.isSafUri) {
                retriever.setDataSource(context, Uri.parse(item.pathOrUri))
            } else {
                val file = File(item.pathOrUri)
                if (!file.exists()) return@runCatching null
                retriever.setDataSource(file.absolutePath)
            }
            var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) {
                val tmp = w; w = h; h = tmp
            }
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()

            val frame = if (needFrame) {
                val targetTimeUs = ((dur ?: 2_000L).coerceAtMost(4_000L) / 2L) * 1000L
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 288, 288)
                } else {
                    retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } else null

            val ratio = if (w > 0 && h > 0) {
                (w.toFloat() / h.toFloat()).coerceIn(0.4f, 2.5f)
            } else if (frame != null && frame.height > 0) {
                (frame.width.toFloat() / frame.height.toFloat()).coerceIn(0.4f, 2.5f)
            } else 1.24f

            MediaPreviewMeta(
                width = w,
                height = h,
                aspectRatio = ratio,
                durationMillis = dur,
                videoFrame = frame
            )
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()

    fun formatDuration(millis: Long?): String {
        if (millis == null || millis <= 0L) return "0:00"
        val totalSec = millis / 1000L
        val hrs = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return if (hrs > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }
}
