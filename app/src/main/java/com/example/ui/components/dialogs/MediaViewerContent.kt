package com.example.ui.components.dialogs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.media.ExifInterface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.example.data.ArcImageLoader
import com.example.data.FileItem
import com.example.data.FormatUtils
import com.example.data.MediaThumbnailLoader
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt

internal data class ZoomTierSpec(
    val stages: List<Float>,
    val stageLabels: List<String>,
    val maxScale: Float,
    val oneToOneScale: Float
) {
    val doubleTapScale: Float get() = stages.firstOrNull() ?: 2.0f
}

internal fun computeZoomTierSpec(
    imageWidth: Int,
    imageHeight: Int,
    containerWidth: Float,
    containerHeight: Float,
    isSvg: Boolean = false
): ZoomTierSpec {
    if (isSvg) {
        return ZoomTierSpec(
            stages = listOf(2.0f, 4.2f),
            stageLabels = listOf("HD", "Ultra HD"),
            maxScale = 10f,
            oneToOneScale = 2.0f
        )
    }
    if (imageWidth <= 0 || imageHeight <= 0 || containerWidth <= 0f || containerHeight <= 0f) {
        return ZoomTierSpec(
            stages = listOf(2.2f),
            stageLabels = listOf("HD"),
            maxScale = 5.5f,
            oneToOneScale = 1f
        )
    }
    val baseFitScale = minOf(
        containerWidth / imageWidth.toFloat(),
        containerHeight / imageHeight.toFloat()
    ).coerceAtLeast(0.01f)
    val oneToOne = (1f / baseFitScale).coerceAtLeast(1f)
    val maxDim = maxOf(imageWidth, imageHeight)

    return when {
        // Large / 3K / 4K Ultra HD / UUHD (e.g., 3840x2160, 4000x3000, 8000x6000, 108MP) -> progressive HD -> FHD -> Ultra HD
        maxDim >= 2800 -> {
            val hdScale = 1.9f
            val fhdScale = 3.3f
            val uhdScale = maxOf(5.2f, minOf(oneToOne, 8.5f)).coerceIn(5.0f, 8.5f)
            ZoomTierSpec(
                stages = listOf(hdScale, fhdScale, uhdScale),
                stageLabels = listOf("HD", "FHD", "Ultra HD"),
                maxScale = maxOf(8.5f, uhdScale * 1.6f).coerceIn(8.5f, 14.0f),
                oneToOneScale = oneToOne
            )
        }
        // FHD / QHD (e.g., 1920x1080, 2560x1440) -> progressive HD -> FHD
        maxDim >= 1920 -> {
            val hdScale = 1.9f
            val fhdScale = maxOf(2.9f, oneToOne).coerceIn(2.9f, 4.2f)
            ZoomTierSpec(
                stages = listOf(hdScale, fhdScale),
                stageLabels = listOf("HD", "FHD"),
                maxScale = maxOf(6.0f, fhdScale * 2.0f).coerceIn(6.0f, 8.5f),
                oneToOneScale = oneToOne
            )
        }
        // HD (e.g., 1280x720)
        maxDim >= 1200 -> {
            val hdScale = maxOf(1.9f, oneToOne).coerceIn(1.9f, 2.5f)
            ZoomTierSpec(
                stages = listOf(hdScale),
                stageLabels = listOf("HD"),
                maxScale = maxOf(4.5f, oneToOne * 2.2f).coerceIn(4.5f, 6.0f),
                oneToOneScale = oneToOne
            )
        }
        // Small raster / .ico / small GIF
        else -> ZoomTierSpec(
            stages = listOf(2.2f),
            stageLabels = listOf("Zoom"),
            maxScale = 4.5f,
            oneToOneScale = oneToOne
        )
    }
}

internal fun nextDoubleTapStageIndex(currentScale: Float, spec: ZoomTierSpec): Int? {
    if (currentScale <= 1.05f) return 0
    val nextIdx = spec.stages.indexOfFirst { it > currentScale + 0.25f }
    return if (nextIdx >= 0) nextIdx else null
}

internal fun activeZoomStageLabel(currentScale: Float, spec: ZoomTierSpec): String? {
    if (currentScale <= 1.05f || spec.stages.isEmpty()) return null
    var matchedIdx = 0
    for (i in spec.stages.indices) {
        if (currentScale >= spec.stages[i] - 0.30f) {
            matchedIdx = i
        }
    }
    return spec.stageLabels.getOrNull(matchedIdx)
}

internal fun computeFittedSize(
    imageWidth: Int,
    imageHeight: Int,
    containerWidth: Float,
    containerHeight: Float
): Pair<Float, Float> {
    if (containerWidth <= 0f || containerHeight <= 0f) return 0f to 0f
    if (imageWidth <= 0 || imageHeight <= 0) return containerWidth to containerHeight
    val imgAspect = imageWidth.toFloat() / imageHeight.toFloat()
    val boxAspect = containerWidth / containerHeight
    return if (imgAspect > boxAspect) {
        containerWidth to (containerWidth / imgAspect)
    } else {
        (containerHeight * imgAspect) to containerHeight
    }
}

internal fun clampImageOffset(
    offset: Offset,
    scale: Float,
    fittedWidth: Float,
    fittedHeight: Float,
    containerWidth: Float,
    containerHeight: Float
): Offset {
    val maxPanX = ((fittedWidth * scale - containerWidth) * 0.5f).coerceAtLeast(0f)
    val maxPanY = ((fittedHeight * scale - containerHeight) * 0.5f).coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-maxPanX, maxPanX),
        y = offset.y.coerceIn(-maxPanY, maxPanY)
    )
}

internal fun calculateFocalZoomOffset(
    focalPoint: Offset,
    containerWidth: Float,
    containerHeight: Float,
    fittedWidth: Float,
    fittedHeight: Float,
    currentScale: Float,
    currentOffset: Offset,
    targetScale: Float,
    panDelta: Offset = Offset.Zero
): Offset {
    val center = Offset(containerWidth * 0.5f, containerHeight * 0.5f)
    val halfVisW = fittedWidth * currentScale * 0.5f
    val halfVisH = fittedHeight * currentScale * 0.5f
    val clampedFocal = Offset(
        x = focalPoint.x.coerceIn(
            (center.x + currentOffset.x - halfVisW).coerceAtMost(center.x),
            (center.x + currentOffset.x + halfVisW).coerceAtLeast(center.x)
        ),
        y = focalPoint.y.coerceIn(
            (center.y + currentOffset.y - halfVisH).coerceAtMost(center.y),
            (center.y + currentOffset.y + halfVisH).coerceAtLeast(center.y)
        )
    )
    val deltaFromCenter = clampedFocal - center
    val safeCurScale = currentScale.coerceAtLeast(0.01f)
    val imagePointFromCenter = (deltaFromCenter - currentOffset) / safeCurScale
    val unclamped = deltaFromCenter + panDelta - imagePointFromCenter * targetScale
    return clampImageOffset(unclamped, targetScale, fittedWidth, fittedHeight, containerWidth, containerHeight)
}

internal fun mapDisplayRectToRawRegion(
    displayRect: Rect,
    displayWidth: Int,
    displayHeight: Int,
    exifOrientation: Int
): Rect {
    val l = displayRect.left
    val t = displayRect.top
    val r = displayRect.right
    val b = displayRect.bottom
    return when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> {
            // display (W,H) = (rawH, rawW): x_disp = rawH - y_raw, y_disp = x_raw
            Rect(t, displayWidth - r, b, displayWidth - l)
        }
        ExifInterface.ORIENTATION_ROTATE_180 -> {
            Rect(displayWidth - r, displayHeight - b, displayWidth - l, displayHeight - t)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> {
            // display (W,H) = (rawH, rawW): x_disp = y_raw, y_disp = rawW - x_raw
            Rect(displayHeight - b, l, displayHeight - t, r)
        }
        else -> Rect(l, t, r, b)
    }
}

private fun rotateRegionBitmapIfNeeded(bitmap: Bitmap, exifOrientation: Int): Bitmap {
    val degrees = when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return bitmap
    }
    val matrix = Matrix().apply { postRotate(degrees) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) {
        bitmap.recycle()
    }
    return rotated
}

private data class UltraHdRegionTile(
    val bitmap: ImageBitmap,
    val region: Rect
)

private fun openRegionDecoder(context: Context, item: FileItem): BitmapRegionDecoder? = runCatching {
    val stream = if (item.isSafUri) {
        context.contentResolver.openInputStream(Uri.parse(item.pathOrUri))
    } else {
        val f = File(item.pathOrUri)
        if (f.exists() && f.canRead()) f.inputStream() else null
    } ?: return@runCatching null

    stream.use { input ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(input)
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(input, false)
        }
    }
}.getOrNull()

private suspend fun decodeRegionWithCachedDecoder(
    decoder: BitmapRegionDecoder,
    rawRegion: Rect,
    targetSampleSize: Int,
    exifOrientation: Int
): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        if (rawRegion.width() <= 1 || rawRegion.height() <= 1) return@runCatching null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = targetSampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = synchronized(decoder) {
            if (decoder.isRecycled) null else decoder.decodeRegion(rawRegion, opts)
        } ?: return@runCatching null
        rotateRegionBitmapIfNeeded(decoded, exifOrientation)
    }.getOrNull()
}

@Composable
fun OriginalImageViewer(
    item: FileItem,
    modifier: Modifier = Modifier,
    darkBackdrop: Boolean = true,
    transparentBackground: Boolean = false,
    showBottomBadge: Boolean = true,
    onTap: () -> Unit = {},
    onZoomChanged: (Boolean) -> Unit = {},
    onDismiss: (() -> Unit)? = null,
    onSwipeUpInfo: (() -> Unit)? = null,
    onDismissDragProgress: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val cachedMeta = remember(item.id, item.lastModifiedMillis) {
        MediaThumbnailLoader.getCached(item, needVideoFrame = false)
    }
    val meta by produceState(initialValue = cachedMeta, item.id, item.lastModifiedMillis) {
        value = cachedMeta ?: run {
            value = null
            MediaThumbnailLoader.loadMeta(context, item, needVideoFrame = false)
        }
    }

    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnSwipeUpInfo by rememberUpdatedState(onSwipeUpInfo)
    val currentOnDismissDragProgress by rememberUpdatedState(onDismissDragProgress)

    val scaleAnim = remember(item.id) { Animatable(1f) }
    val offsetAnim = remember(item.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val dismissOffsetYAnim = remember(item.id) { Animatable(0f) }
    var isGestureActive by remember(item.id) { mutableStateOf(false) }
    var activeTargetStageIdx by remember(item.id) { mutableStateOf<Int?>(null) }
    var zoomAnimJob by remember(item.id) { mutableStateOf<Job?>(null) }
    var ultraHdTile by remember(item.id) { mutableStateOf<UltraHdRegionTile?>(null) }
    var cachedRegionDecoder by remember(item.id) { mutableStateOf<BitmapRegionDecoder?>(null) }

    DisposableEffect(item.id) {
        onDispose {
            zoomAnimJob?.cancel()
            runCatching { cachedRegionDecoder?.recycle() }
            cachedRegionDecoder = null
        }
    }

    val zoomDecelEasing = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
    val zoomInScaleSpec = remember { tween<Float>(durationMillis = 380, easing = zoomDecelEasing) }
    val zoomInOffsetSpec = remember { tween<Offset>(durationMillis = 380, easing = zoomDecelEasing) }
    val zoomOutScaleSpec = remember { tween<Float>(durationMillis = 340, easing = zoomDecelEasing) }
    val zoomOutOffsetSpec = remember { tween<Offset>(durationMillis = 340, easing = zoomDecelEasing) }
    val gallerySpring = remember {
        spring<Float>(dampingRatio = 0.90f, stiffness = 240f)
    }
    val galleryOffsetSpring = remember {
        spring<Offset>(dampingRatio = 0.90f, stiffness = 240f)
    }

    LaunchedEffect(item.id) {
        activeTargetStageIdx = null
        currentOnZoomChanged(false)
        currentOnDismissDragProgress(0f)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (transparentBackground) {
                    Modifier
                } else {
                    Modifier.background(if (darkBackdrop) Color(0xFF06090F) else MaterialTheme.colorScheme.surfaceContainer)
                }
            )
            .testTag("original_image_viewer"),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val boxW = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val boxH = with(density) { maxHeight.toPx().coerceAtLeast(1f) }
        val imgW = meta?.width ?: 0
        val imgH = meta?.height ?: 0
        val exifOrientation = meta?.exifOrientation ?: ExifInterface.ORIENTATION_NORMAL
        val ext = item.extension.lowercase()
        val isSvg = ext == "svg"
        val (fittedW, fittedH) = computeFittedSize(imgW, imgH, boxW, boxH)
        val zoomSpec = remember(imgW, imgH, boxW, boxH, isSvg) {
            computeZoomTierSpec(imgW, imgH, boxW, boxH, isSvg = isSvg)
        }

        val dismissDragFraction = (abs(dismissOffsetYAnim.value) / (boxH * 0.30f).coerceAtLeast(120f)).coerceIn(0f, 1f)
        LaunchedEffect(dismissDragFraction) {
            currentOnDismissDragProgress(dismissDragFraction)
        }

        // Optimized on-demand region sharpener: base stays capped at <=2048px so UUHD never decodes whole frame;
        // when zoomed past 1.35x, only the visible crop is decoded via cached BitmapRegionDecoder.
        val currentScale = scaleAnim.value
        val currentOffset = offsetAnim.value
        val isZoomAnimating = scaleAnim.isRunning || offsetAnim.isRunning
        val needsUltraHdTile = !isSvg &&
            ext != "ico" &&
            ext != "gif" &&
            maxOf(imgW, imgH) > 2048 &&
            currentScale > 1.35f &&
            !isGestureActive &&
            !isZoomAnimating &&
            fittedW > 0f &&
            fittedH > 0f

        LaunchedEffect(
            item.id,
            needsUltraHdTile,
            isZoomAnimating,
            currentScale,
            currentOffset,
            imgW,
            imgH,
            exifOrientation,
            boxW,
            boxH
        ) {
            if (!needsUltraHdTile) {
                if (currentScale <= 1.15f) ultraHdTile = null
                return@LaunchedEffect
            }
            delay(50L)
            val centerX = boxW * 0.5f
            val centerY = boxH * 0.5f
            val dispW = fittedW * currentScale
            val dispH = fittedH * currentScale
            val imgLeftOnScreen = centerX + currentOffset.x - dispW * 0.5f
            val imgTopOnScreen = centerY + currentOffset.y - dispH * 0.5f

            val pxPerScreenPxX = imgW.toFloat() / dispW
            val pxPerScreenPxY = imgH.toFloat() / dispH

            val rawLeft = ((0f - imgLeftOnScreen) * pxPerScreenPxX).toInt()
            val rawTop = ((0f - imgTopOnScreen) * pxPerScreenPxY).toInt()
            val rawRight = ((boxW - imgLeftOnScreen) * pxPerScreenPxX).toInt()
            val rawBottom = ((boxH - imgTopOnScreen) * pxPerScreenPxY).toInt()

            val displayRegion = Rect(
                rawLeft.coerceIn(0, imgW - 1),
                rawTop.coerceIn(0, imgH - 1),
                rawRight.coerceIn(1, imgW),
                rawBottom.coerceIn(1, imgH)
            )
            if (displayRegion.width() <= 16 || displayRegion.height() <= 16) return@LaunchedEffect

            val decoder = cachedRegionDecoder ?: withContext(Dispatchers.IO) {
                openRegionDecoder(context, item)
            }?.also { cachedRegionDecoder = it } ?: return@LaunchedEffect

            val rawRegion = mapDisplayRectToRawRegion(displayRegion, imgW, imgH, exifOrientation)
            val clampedRaw = Rect(
                rawRegion.left.coerceIn(0, decoder.width - 1),
                rawRegion.top.coerceIn(0, decoder.height - 1),
                rawRegion.right.coerceIn(1, decoder.width),
                rawRegion.bottom.coerceIn(1, decoder.height)
            )
            if (clampedRaw.width() <= 16 || clampedRaw.height() <= 16) return@LaunchedEffect

            val rawMax = maxOf(clampedRaw.width(), clampedRaw.height())
            var sampleSize = 1
            while ((rawMax / sampleSize) > 1800) {
                sampleSize *= 2
            }
            val bmp = decodeRegionWithCachedDecoder(decoder, clampedRaw, sampleSize, exifOrientation)
            if (bmp != null) {
                ultraHdTile = UltraHdRegionTile(bmp.asImageBitmap(), displayRegion)
            }
        }

        val imageLoader = remember(context) { ArcImageLoader.get(context) }
        val imageRequest = remember(item.pathOrUri, item.lastModifiedMillis, ext) {
            ImageRequest.Builder(context)
                .data(if (item.isSafUri) item.pathOrUri else File(item.pathOrUri))
                .apply {
                    when {
                        ext == "gif" || ext == "ico" -> size(Size.ORIGINAL)
                        // Precision.INEXACT never upscales small/HD/FHD images, while capping Ultra-HD/UUHD base texture to <=2048px
                        else -> size(2048, 2048).precision(Precision.INEXACT)
                    }
                }
                .crossfade(false)
                .build()
        }

        val dismissThresholdPx = minOf(boxH * 0.16f, with(density) { 140.dp.toPx() })
        val swipeUpThresholdPx = minOf(boxH * 0.10f, with(density) { 72.dp.toPx() })

        fun settleZoomAfterGesture(velocityX: Float, velocityY: Float) {
            activeTargetStageIdx = null
            zoomAnimJob?.cancel()
            zoomAnimJob = scope.launch {
                val settledScale = scaleAnim.value
                if (settledScale < 1.05f) {
                    currentOnZoomChanged(false)
                    offsetAnim.updateBounds(null, null)
                    launch { scaleAnim.animateTo(1f, zoomOutScaleSpec) }
                    launch { offsetAnim.animateTo(Offset.Zero, zoomOutOffsetSpec) }
                } else if (settledScale > zoomSpec.maxScale) {
                    val targetScale = zoomSpec.maxScale
                    val clamped = clampImageOffset(
                        offsetAnim.value,
                        targetScale,
                        fittedW,
                        fittedH,
                        boxW,
                        boxH
                    )
                    currentOnZoomChanged(true)
                    offsetAnim.updateBounds(null, null)
                    launch { scaleAnim.animateTo(targetScale, gallerySpring) }
                    launch { offsetAnim.animateTo(clamped, galleryOffsetSpring) }
                } else {
                    currentOnZoomChanged(true)
                    val speedSq = velocityX * velocityX + velocityY * velocityY
                    if (speedSq > 1600f) {
                        val maxPanX = ((fittedW * settledScale - boxW) * 0.5f).coerceAtLeast(0f)
                        val maxPanY = ((fittedH * settledScale - boxH) * 0.5f).coerceAtLeast(0f)
                        offsetAnim.updateBounds(
                            lowerBound = Offset(-maxPanX, -maxPanY),
                            upperBound = Offset(maxPanX, maxPanY)
                        )
                        offsetAnim.animateDecay(
                            initialVelocity = Offset(velocityX, velocityY),
                            animationSpec = exponentialDecay(frictionMultiplier = 2.4f)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    item.id,
                    boxW,
                    boxH,
                    fittedW,
                    fittedH,
                    zoomSpec,
                    currentOnDismiss != null,
                    currentOnSwipeUpInfo != null
                ) {
                    val touchSlop = viewConfiguration.touchSlop
                    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                    val doubleTapSlop = touchSlop * 4.5f

                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            if (dismissOffsetYAnim.isRunning) dismissOffsetYAnim.stop()
                        }

                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(firstDown.uptimeMillis, firstDown.position)

                        // 0=undecided, 1=zoomPanOrPinch, 2=verticalDismissOrInfo, 3=handedOffToPager
                        var mode = 0
                        var totalDx = 0f
                        var totalDy = 0f
                        var lastUpChange: PointerInputChange? = null

                        do {
                            val event = awaitPointerEvent()
                            val pressedChanges = event.changes.filter { it.pressed }
                            val pointerCount = pressedChanges.size
                            val isZoomed = scaleAnim.value > 1.05f

                            if (pointerCount >= 2 && mode != 1) {
                                mode = 1
                                isGestureActive = true
                                activeTargetStageIdx = null
                                zoomAnimJob?.cancel()
                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    scaleAnim.stop()
                                    offsetAnim.stop()
                                    offsetAnim.updateBounds(null, null)
                                    dismissOffsetYAnim.snapTo(0f)
                                }
                            } else if (mode == 0 && pointerCount == 1) {
                                val pan = event.calculatePan()
                                totalDx += pan.x
                                totalDy += pan.y
                                if (hypot(totalDx, totalDy) > touchSlop) {
                                    if (isZoomed) {
                                        mode = 1
                                        isGestureActive = true
                                        activeTargetStageIdx = null
                                        zoomAnimJob?.cancel()
                                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                            scaleAnim.stop()
                                            offsetAnim.stop()
                                            offsetAnim.updateBounds(null, null)
                                        }
                                    } else if (abs(totalDy) > abs(totalDx) * 1.15f &&
                                        (currentOnDismiss != null || currentOnSwipeUpInfo != null)
                                    ) {
                                        mode = 2
                                        isGestureActive = true
                                    } else {
                                        mode = 3
                                    }
                                }
                            }

                            if (mode == 1 && pointerCount >= 1) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                if (pointerCount == 1) {
                                    pressedChanges.firstOrNull()?.let { c ->
                                        velocityTracker.addPosition(c.uptimeMillis, c.position)
                                    }
                                }
                                val prevScale = scaleAnim.value
                                val nextScale = (prevScale * zoomChange).coerceIn(0.85f, zoomSpec.maxScale * 1.18f)
                                val focal = if (centroid != Offset.Unspecified &&
                                    centroid.x.isFinite() && centroid.y.isFinite()
                                ) {
                                    centroid
                                } else {
                                    Offset(boxW * 0.5f, boxH * 0.5f)
                                }
                                val nextOffset = if (nextScale >= 1f) {
                                    calculateFocalZoomOffset(
                                        focalPoint = focal,
                                        containerWidth = boxW,
                                        containerHeight = boxH,
                                        fittedWidth = fittedW,
                                        fittedHeight = fittedH,
                                        currentScale = prevScale,
                                        currentOffset = offsetAnim.value,
                                        targetScale = nextScale,
                                        panDelta = panChange
                                    )
                                } else {
                                    Offset.Zero
                                }
                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    scaleAnim.snapTo(nextScale)
                                    offsetAnim.snapTo(nextOffset)
                                }
                                currentOnZoomChanged(nextScale > 1.05f)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (mode == 2 && pointerCount == 1) {
                                pressedChanges.firstOrNull()?.let { c ->
                                    velocityTracker.addPosition(c.uptimeMillis, c.position)
                                }
                                val dy = event.calculatePan().y
                                val curY = dismissOffsetYAnim.value
                                val dampedDy = if (curY + dy < 0f) dy * 0.48f else dy
                                val nextY = (curY + dampedDy).coerceIn(
                                    if (currentOnSwipeUpInfo != null) -180f else 0f,
                                    boxH * 0.65f
                                )
                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    dismissOffsetYAnim.snapTo(nextY)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }

                            if (pointerCount == 0 && mode == 0) {
                                lastUpChange = event.changes.firstOrNull { !it.pressed }
                            }
                        } while (event.changes.any { it.pressed })

                        when (mode) {
                            1 -> {
                                isGestureActive = false
                                val velocity = velocityTracker.calculateVelocity()
                                settleZoomAfterGesture(velocity.x, velocity.y)
                            }
                            2 -> {
                                isGestureActive = false
                                val vy = velocityTracker.calculateVelocity().y
                                val currentY = dismissOffsetYAnim.value
                                if (currentOnDismiss != null &&
                                    (currentY > dismissThresholdPx || (vy > 950f && currentY > 24f))
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentOnDismiss?.invoke()
                                } else if (currentOnSwipeUpInfo != null &&
                                    (currentY < -swipeUpThresholdPx || (vy < -950f && currentY < -20f))
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch { dismissOffsetYAnim.animateTo(0f, gallerySpring) }
                                    currentOnSwipeUpInfo?.invoke()
                                } else {
                                    scope.launch { dismissOffsetYAnim.animateTo(0f, gallerySpring) }
                                }
                            }
                            3 -> Unit // Pager handled horizontal swipe
                            0 -> {
                                val tapUp = lastUpChange
                                if (tapUp != null) {
                                    val secondDown = withTimeoutOrNull(doubleTapTimeout) {
                                        awaitFirstDown(requireUnconsumed = false)
                                    }
                                    if (secondDown == null ||
                                        (secondDown.position - tapUp.position).getDistance() > doubleTapSlop
                                    ) {
                                        currentOnTap()
                                    } else {
                                        // Touch #2: Double-Tap focal zoom OR Double-Tap + Drag one-handed continuous zoom
                                        val secondAnchor = secondDown.position
                                        var secondMode = 0 // 0=tap, 1=one-handed drag zoom, 2=pinch
                                        var tap2Dx = 0f
                                        var tap2Dy = 0f
                                        val tap2VelocityTracker = VelocityTracker()
                                        tap2VelocityTracker.addPosition(secondDown.uptimeMillis, secondDown.position)

                                        do {
                                            val ev2 = awaitPointerEvent()
                                            val pressed2 = ev2.changes.filter { it.pressed }
                                            val count2 = pressed2.size

                                            if (count2 >= 2 && secondMode != 2) {
                                                secondMode = 2
                                                isGestureActive = true
                                                activeTargetStageIdx = null
                                                zoomAnimJob?.cancel()
                                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                    scaleAnim.stop()
                                                    offsetAnim.stop()
                                                    offsetAnim.updateBounds(null, null)
                                                }
                                            } else if (secondMode == 0 && count2 == 1) {
                                                val pan2 = ev2.calculatePan()
                                                tap2Dx += pan2.x
                                                tap2Dy += pan2.y
                                                if (hypot(tap2Dx, tap2Dy) > touchSlop) {
                                                    secondMode = 1
                                                    isGestureActive = true
                                                    activeTargetStageIdx = null
                                                    zoomAnimJob?.cancel()
                                                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                        scaleAnim.stop()
                                                        offsetAnim.stop()
                                                        offsetAnim.updateBounds(null, null)
                                                    }
                                                }
                                            }

                                            if (secondMode == 1 && count2 == 1) {
                                                val dy2 = ev2.calculatePan().y
                                                val prevScale = scaleAnim.value
                                                val zoomFactor = exp(dy2 * 0.0055f)
                                                val nextScale = (prevScale * zoomFactor).coerceIn(0.85f, zoomSpec.maxScale * 1.18f)
                                                val nextOffset = if (nextScale >= 1f) {
                                                    calculateFocalZoomOffset(
                                                        focalPoint = secondAnchor,
                                                        containerWidth = boxW,
                                                        containerHeight = boxH,
                                                        fittedWidth = fittedW,
                                                        fittedHeight = fittedH,
                                                        currentScale = prevScale,
                                                        currentOffset = offsetAnim.value,
                                                        targetScale = nextScale
                                                    )
                                                } else {
                                                    Offset.Zero
                                                }
                                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                    scaleAnim.snapTo(nextScale)
                                                    offsetAnim.snapTo(nextOffset)
                                                }
                                                currentOnZoomChanged(nextScale > 1.05f)
                                                ev2.changes.forEach { if (it.positionChanged()) it.consume() }
                                            } else if (secondMode == 2 && count2 >= 1) {
                                                if (count2 == 1) {
                                                    pressed2.firstOrNull()?.let { c ->
                                                        tap2VelocityTracker.addPosition(c.uptimeMillis, c.position)
                                                    }
                                                }
                                                val zoomChange = ev2.calculateZoom()
                                                val panChange = ev2.calculatePan()
                                                val centroid = ev2.calculateCentroid(useCurrent = true)
                                                val prevScale = scaleAnim.value
                                                val nextScale = (prevScale * zoomChange).coerceIn(0.85f, zoomSpec.maxScale * 1.18f)
                                                val focal = if (centroid != Offset.Unspecified &&
                                                    centroid.x.isFinite() && centroid.y.isFinite()
                                                ) {
                                                    centroid
                                                } else {
                                                    secondAnchor
                                                }
                                                val nextOffset = if (nextScale >= 1f) {
                                                    calculateFocalZoomOffset(
                                                        focalPoint = focal,
                                                        containerWidth = boxW,
                                                        containerHeight = boxH,
                                                        fittedWidth = fittedW,
                                                        fittedHeight = fittedH,
                                                        currentScale = prevScale,
                                                        currentOffset = offsetAnim.value,
                                                        targetScale = nextScale,
                                                        panDelta = panChange
                                                    )
                                                } else {
                                                    Offset.Zero
                                                }
                                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                    scaleAnim.snapTo(nextScale)
                                                    offsetAnim.snapTo(nextOffset)
                                                }
                                                currentOnZoomChanged(nextScale > 1.05f)
                                                ev2.changes.forEach { if (it.positionChanged()) it.consume() }
                                            }
                                        } while (ev2.changes.any { it.pressed })

                                        if (secondMode == 0) {
                                            // Progressive Xiaomi Gallery double-tap stepping: 1x -> HD -> FHD -> Ultra HD -> 1x
                                            val curStage = activeTargetStageIdx
                                            val nextStageIdx = if (curStage != null) {
                                                if (curStage + 1 < zoomSpec.stages.size) curStage + 1 else null
                                            } else {
                                                nextDoubleTapStageIndex(scaleAnim.value, zoomSpec)
                                            }
                                            activeTargetStageIdx = nextStageIdx
                                            zoomAnimJob?.cancel()
                                            zoomAnimJob = scope.launch {
                                                offsetAnim.updateBounds(null, null)
                                                if (nextStageIdx == null) {
                                                    currentOnZoomChanged(false)
                                                    launch { scaleAnim.animateTo(1f, zoomOutScaleSpec) }
                                                    launch { offsetAnim.animateTo(Offset.Zero, zoomOutOffsetSpec) }
                                                } else {
                                                    val targetScale = zoomSpec.stages[nextStageIdx]
                                                    val targetOffset = calculateFocalZoomOffset(
                                                        focalPoint = secondAnchor,
                                                        containerWidth = boxW,
                                                        containerHeight = boxH,
                                                        fittedWidth = fittedW,
                                                        fittedHeight = fittedH,
                                                        currentScale = scaleAnim.value,
                                                        currentOffset = offsetAnim.value,
                                                        targetScale = targetScale
                                                    )
                                                    currentOnZoomChanged(true)
                                                    launch { scaleAnim.animateTo(targetScale, zoomInScaleSpec) }
                                                    launch { offsetAnim.animateTo(targetOffset, zoomInOffsetSpec) }
                                                }
                                            }
                                        } else {
                                            isGestureActive = false
                                            val vel = tap2VelocityTracker.calculateVelocity()
                                            settleZoomAfterGesture(vel.x, vel.y)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val dismissScale = (1f - (abs(dismissOffsetYAnim.value) / (boxH * 2.4f).coerceAtLeast(900f))).coerceIn(0.80f, 1f)
            val totalScale = scaleAnim.value * dismissScale
            val totalTranslationY = offsetAnim.value.y + dismissOffsetYAnim.value

            AsyncImage(
                model = imageRequest,
                imageLoader = imageLoader,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = totalScale,
                        scaleY = totalScale,
                        translationX = offsetAnim.value.x,
                        translationY = totalTranslationY
                    )
            )

            // Crisp native-pixel region overlay for Ultra-HD (>4096px) images when zoomed in
            val activeTile = ultraHdTile
            if (activeTile != null && needsUltraHdTile && imgW > 0 && imgH > 0) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dispW = fittedW * currentScale
                    val dispH = fittedH * currentScale
                    val imgLeft = boxW * 0.5f + currentOffset.x - dispW * 0.5f
                    val imgTop = boxH * 0.5f + currentOffset.y - dispH * 0.5f
                    val scaleX = dispW / imgW.toFloat()
                    val scaleY = dispH / imgH.toFloat()

                    val dstLeft = (imgLeft + activeTile.region.left * scaleX).roundToInt()
                    val dstTop = (imgTop + activeTile.region.top * scaleY).roundToInt()
                    val dstWidth = (activeTile.region.width() * scaleX).roundToInt().coerceAtLeast(1)
                    val dstHeight = (activeTile.region.height() * scaleY).roundToInt().coerceAtLeast(1)

                    drawImage(
                        image = activeTile.bitmap,
                        dstOffset = IntOffset(dstLeft, dstTop),
                        dstSize = IntSize(dstWidth, dstHeight),
                        filterQuality = FilterQuality.High
                    )
                }
            }
        }

        val showZoomOnlyPill = !showBottomBadge && scaleAnim.value > 1.05f && dismissDragFraction < 0.03f
        val zoomStageLabel = activeZoomStageLabel(scaleAnim.value, zoomSpec)
        val formatLabel = when (ext) {
            "gif" -> "GIF"
            "heic", "heics" -> "HEIC"
            "heif", "heifs", "hif" -> "HEIF"
            "avif" -> "AVIF"
            "svg" -> "SVG"
            "ico" -> "ICO"
            else -> null
        }
        if ((showBottomBadge && dismissDragFraction < 0.03f) || showZoomOnlyPill) {
            val badgeText = if (showZoomOnlyPill) {
                buildString {
                    if (zoomStageLabel != null) {
                        append(zoomStageLabel)
                        append(" · ")
                    }
                    append(String.format(java.util.Locale.US, "%.1fx", scaleAnim.value))
                }
            } else {
                buildString {
                    if (meta != null && meta!!.width > 0 && meta!!.height > 0) {
                        append("${meta!!.width} × ${meta!!.height}")
                        val maxDim = maxOf(meta!!.width, meta!!.height)
                        val resTag = when {
                            maxDim >= 7680 -> "8K UUHD"
                            maxDim >= 3840 -> "4K Ultra HD"
                            maxDim >= 2560 -> "QHD"
                            maxDim >= 1920 -> "FHD"
                            maxDim >= 1280 -> "HD"
                            else -> null
                        }
                        if (resTag != null) {
                            append(" · ")
                            append(resTag)
                        }
                        if (formatLabel != null) {
                            append(" · ")
                            append(formatLabel)
                        }
                    } else {
                        append(formatLabel ?: item.extension.uppercase().ifBlank { "IMAGE" })
                    }
                    if (scaleAnim.value > 1.05f) {
                        if (zoomStageLabel != null) {
                            append(" · ")
                            append(zoomStageLabel)
                        }
                        append(" · ")
                        append(String.format(java.util.Locale.US, "%.1fx", scaleAnim.value))
                    } else if (item.sizeBytes > 0L) {
                        append(" · ")
                        append(FormatUtils.formatBytes(item.sizeBytes))
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .align(if (showZoomOnlyPill) Alignment.TopCenter else Alignment.BottomStart)
                    .then(
                        if (showZoomOnlyPill) {
                            Modifier.statusBarsPadding().padding(top = 64.dp)
                        } else {
                            Modifier.navigationBarsPadding().padding(14.dp)
                        }
                    ),
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.62f)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun OriginalVideoPlayer(
    item: FileItem,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    onOpenWith: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val cachedMeta = remember(item.id, item.lastModifiedMillis) {
        MediaThumbnailLoader.getCached(item, needVideoFrame = false)
    }
    val meta by produceState(initialValue = cachedMeta, item.id, item.lastModifiedMillis) {
        value = cachedMeta ?: run {
            value = null
            MediaThumbnailLoader.loadMeta(context, item, needVideoFrame = false)
        }
    }

    var isPrepared by remember(item.id) { mutableStateOf(false) }
    var isPlaying by remember(item.id) { mutableStateOf(false) }
    var isMuted by remember(item.id) { mutableStateOf(false) }
    var hasError by remember(item.id) { mutableStateOf(false) }
    var videoWidth by remember(item.id) { mutableIntStateOf(0) }
    var videoHeight by remember(item.id) { mutableIntStateOf(0) }
    var durationMs by remember(item.id) { mutableLongStateOf(0L) }
    var positionMs by remember(item.id) { mutableLongStateOf(0L) }
    var isSeeking by remember(item.id) { mutableStateOf(false) }

    val mediaPlayer = remember(item.id) { MediaPlayer() }
    var activeSurface by remember(item.id) { mutableStateOf<Surface?>(null) }

    DisposableEffect(item.id) {
        onDispose {
            runCatching {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.reset()
                mediaPlayer.release()
            }
            runCatching { activeSurface?.release() }
        }
    }

    LaunchedEffect(isActive, isPrepared) {
        if (!isActive && isPrepared) {
            runCatching {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                }
            }
            isPlaying = false
        }
    }

    LaunchedEffect(isPlaying, isPrepared, isActive) {
        while (isPlaying && isPrepared && isActive) {
            if (!isSeeking) {
                runCatching { positionMs = mediaPlayer.currentPosition.toLong().coerceAtLeast(0L) }
            }
            delay(250L)
        }
    }

    val effectiveWidth = if (videoWidth > 0) videoWidth else (meta?.width ?: 0)
    val effectiveHeight = if (videoHeight > 0) videoHeight else (meta?.height ?: 0)
    val naturalRatio = if (effectiveWidth > 0 && effectiveHeight > 0) {
        (effectiveWidth.toFloat() / effectiveHeight.toFloat()).coerceIn(0.32f, 3.0f)
    } else {
        meta?.aspectRatio?.coerceIn(0.32f, 3.0f) ?: (16f / 9f)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B111A))
            .testTag("original_video_player")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (hasError) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BrokenImage,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "Unable to play this video format in-app",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Button(
                        onClick = onOpenExternal,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.testTag("video_open_external_fallback")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open in External Player")
                    }
                }
            } else {
                val maxContainerRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 1f
                val videoModifier = if (naturalRatio > maxContainerRatio) {
                    Modifier.fillMaxWidth().aspectRatio(naturalRatio)
                } else {
                    Modifier.height(maxHeight).aspectRatio(naturalRatio, matchHeightConstraintsFirst = true)
                }

                Box(
                    modifier = videoModifier.clip(RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        runCatching {
                                            val surface = Surface(st)
                                            activeSurface = surface
                                            mediaPlayer.reset()
                                            mediaPlayer.setSurface(surface)
                                            if (item.isSafUri) {
                                                mediaPlayer.setDataSource(context, Uri.parse(item.pathOrUri))
                                            } else {
                                                mediaPlayer.setDataSource(item.pathOrUri)
                                            }
                                            mediaPlayer.setOnVideoSizeChangedListener { _, vw, vh ->
                                                if (vw > 0 && vh > 0) {
                                                    videoWidth = vw
                                                    videoHeight = vh
                                                }
                                            }
                                            mediaPlayer.setOnPreparedListener { mp ->
                                                isPrepared = true
                                                durationMs = mp.duration.toLong().coerceAtLeast(0L)
                                                val vol = if (isMuted) 0f else 1f
                                                mp.setVolume(vol, vol)
                                                if (isActive) {
                                                    mp.start()
                                                    isPlaying = true
                                                }
                                            }
                                            mediaPlayer.setOnCompletionListener {
                                                isPlaying = false
                                                positionMs = durationMs
                                            }
                                            mediaPlayer.setOnErrorListener { _, _, _ ->
                                                hasError = true
                                                isPlaying = false
                                                true
                                            }
                                            mediaPlayer.prepareAsync()
                                        }.onFailure {
                                            hasError = true
                                        }
                                    }

                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        runCatching {
                                            mediaPlayer.setSurface(null)
                                            activeSurface?.release()
                                            activeSurface = null
                                        }
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                                }
                            }
                        }
                    )

                    if (!isPrepared) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            if (effectiveWidth > 0 && effectiveHeight > 0 && !hasError) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(top = 64.dp, start = 14.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.56f)
                ) {
                    Text(
                        text = "${effectiveWidth} × ${effectiveHeight}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Playback Controls (bleeds cleanly behind bottom gesture bar)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF121A26)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val safeDuration = durationMs.coerceAtLeast(1L)
                Slider(
                    value = positionMs.coerceIn(0L, safeDuration).toFloat(),
                    onValueChange = { next ->
                        isSeeking = true
                        positionMs = next.toLong()
                    },
                    onValueChangeFinished = {
                        isSeeking = false
                        if (isPrepared) {
                            runCatching { mediaPlayer.seekTo(positionMs.toInt()) }
                        }
                    },
                    valueRange = 0f..safeDuration.toFloat(),
                    enabled = isPrepared && !hasError,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            IconButton(
                                onClick = {
                                    if (!isPrepared || hasError) return@IconButton
                                    runCatching {
                                        if (mediaPlayer.isPlaying) {
                                            mediaPlayer.pause()
                                            isPlaying = false
                                        } else {
                                            mediaPlayer.start()
                                            isPlaying = true
                                        }
                                    }
                                },
                                modifier = Modifier.size(44.dp).testTag("video_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val nextMuted = !isMuted
                                isMuted = nextMuted
                                val vol = if (nextMuted) 0f else 1f
                                runCatching { mediaPlayer.setVolume(vol, vol) }
                            },
                            modifier = Modifier.size(44.dp).testTag("video_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = "${MediaThumbnailLoader.formatDuration(positionMs)} / ${MediaThumbnailLoader.formatDuration(durationMs.takeIf { it > 0L } ?: meta?.durationMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }

                    Surface(
                        onClick = onOpenWith ?: onOpenExternal,
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.testTag("video_open_external_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in external player",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "External",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
