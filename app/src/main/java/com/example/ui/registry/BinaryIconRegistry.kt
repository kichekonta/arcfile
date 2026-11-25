package com.example.ui.registry

import android.util.LruCache
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import com.example.ui.theme.ArcTheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DriveType
import com.example.data.ExplorerCategory
import com.example.data.FileItem
import com.example.ui.theme.BinaryAmber
import com.example.ui.theme.BinaryAmberDark
import com.example.ui.theme.BinaryAmberDarkEnd
import com.example.ui.theme.BinaryAmberEnd
import com.example.ui.theme.BinaryBlue
import com.example.ui.theme.BinaryBlueDark
import com.example.ui.theme.BinaryBlueDarkEnd
import com.example.ui.theme.BinaryBlueEnd
import com.example.ui.theme.BinaryCoral
import com.example.ui.theme.BinaryCoralDark
import com.example.ui.theme.BinaryCoralDarkEnd
import com.example.ui.theme.BinaryCoralEnd
import com.example.ui.theme.BinaryCyan
import com.example.ui.theme.BinaryCyanDark
import com.example.ui.theme.BinaryCyanDarkEnd
import com.example.ui.theme.BinaryCyanEnd
import com.example.ui.theme.BinaryOrchid
import com.example.ui.theme.BinaryOrchidDark
import com.example.ui.theme.BinaryOrchidDarkEnd
import com.example.ui.theme.BinaryOrchidEnd
import com.example.ui.theme.BinaryEmerald
import com.example.ui.theme.BinaryEmeraldDark
import com.example.ui.theme.BinaryEmeraldDarkEnd
import com.example.ui.theme.BinaryEmeraldEnd
import com.example.ui.theme.BinaryIndigo
import com.example.ui.theme.BinaryIndigoDark
import com.example.ui.theme.BinaryIndigoDarkEnd
import com.example.ui.theme.BinaryIndigoEnd
import com.example.ui.theme.BinarySlate
import com.example.ui.theme.BinarySlateDark
import com.example.ui.theme.BinarySlateDarkEnd
import com.example.ui.theme.BinarySlateEnd
import com.example.ui.theme.FolderBackDark
import com.example.ui.theme.FolderBackLight
import com.example.ui.theme.FolderFrontEndDark
import com.example.ui.theme.FolderFrontEndLight
import com.example.ui.theme.FolderFrontStartDark
import com.example.ui.theme.FolderFrontStartLight
import com.example.ui.theme.RobotoFontFamily

fun interface ModernGlyphRenderer {
    fun DrawScope.drawGlyph(
        primaryBrush: Brush,
        secondaryColor: Color,
        accentColor: Color,
        monochrome: Boolean
    )
}

data class BinaryIconSpec(
    val startLight: Color,
    val endLight: Color,
    val startDark: Color,
    val endDark: Color,
    val backLight: Color = startLight,
    val backDark: Color = startDark,
    val renderer: ModernGlyphRenderer
)

enum class IosFileIconKind {
    PDF,
    WORD_DOC,
    SPREADSHEET,
    PRESENTATION,
    TEXT_NOTE,
    CODE,
    APK,
    ARCHIVE,
    IMAGE_GENERIC,
    VIDEO_GENERIC,
    AUDIO_GENERIC,
    OTHER_GENERIC
}

data class IosFileVisualSpec(
    val kind: IosFileIconKind,
    val accentLight: Color,
    val accentDark: Color,
    val badgeLabel: String?
)

/**
 * Registry for Aesthetic Blue Two-Tone Folder & iOS Files Manager Style File Icons.
 * Folders use a crisp layered two-tone silhouette with optional semantic emblems (Download, Pictures, etc.).
 * Files use an iOS Files paper card with a folded corner, iconic PDF/useful-type colors & emblems,
 * and unified type-wise icons for remaining media/generic files.
 */
object BinaryIconRegistry {

    private data class ExtTextCacheKey(
        val badgeText: String,
        val sheetHeightBucket: Int,
        val tintValue: ULong,
        val densityBits: Int,
        val fontScaleBits: Int
    )

    private val extTextCache = LruCache<ExtTextCacheKey, androidx.compose.ui.text.TextLayoutResult>(96)

    data class FolderCanvasBounds(
        val left: Float,
        val right: Float,
        val topTab: Float,
        val topBody: Float,
        val pocketTop: Float,
        val bottom: Float,
        val cornerRadius: Float
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - topTab
        val aspectRatio: Float get() = if (height > 0f) width / height else 1f
    }

    fun folderBoundsForCanvas(w: Float, h: Float, density: Float): FolderCanvasBounds {
        val safeDensity = density.coerceAtLeast(0.75f)
        val wDp = w / safeDensity
        val insetX = when {
            wDp >= 120f -> 0.10f   // 2-col large cells: 80% width, generous 10% side margins
            wDp >= 86f -> 0.085f   // 3-col medium cells: 83% width, 8.5% side margins
            else -> 0.07f          // 4-col compact cells & list icons: 86% width for crisp legibility
        }
        val targetAspectRatio = 1.27f // Standard iOS Files / Finder folder W:H ratio
        val bodyW = (w * (1f - insetX * 2f)).coerceAtLeast(1f)
        val bodyH = (bodyW / targetAspectRatio).coerceAtMost(h * 0.84f)
        val left = (w - bodyW) * 0.5f
        val right = left + bodyW
        val topTab = (h - bodyH) * 0.5f
        val bottom = topTab + bodyH
        val topBody = topTab + bodyH * 0.115f
        val pocketTop = topTab + bodyH * 0.195f
        val cornerRadius = bodyH * 0.115f
        return FolderCanvasBounds(
            left = left,
            right = right,
            topTab = topTab,
            topBody = topBody,
            pocketTop = pocketTop,
            bottom = bottom,
            cornerRadius = cornerRadius
        )
    }

    fun drawModernFolderCached(
        cacheScope: CacheDrawScope,
        emblem: FolderEmblem,
        startColor: Color,
        endColor: Color,
        backColor: Color,
        emblemTint: Color,
        monochrome: Boolean
    ): DrawResult = with(cacheScope) {
        val w = size.width
        val h = size.height
        val bounds = folderBoundsForCanvas(w, h, density)
        val r = bounds.cornerRadius
        val tabR = r * 0.72f
        val left = bounds.left
        val right = bounds.right
        val bodyW = bounds.width
        val bodyH = bounds.height
        val topTab = bounds.topTab
        val topBody = bounds.topBody
        val bottom = bounds.bottom
        val pocketTop = bounds.pocketTop

        val backPath = Path().apply {
            moveTo(left + tabR, topTab)
            lineTo(left + bodyW * 0.34f, topTab)
            cubicTo(
                left + bodyW * 0.405f, topTab,
                left + bodyW * 0.445f, topBody,
                left + bodyW * 0.505f, topBody
            )
            lineTo(right - r, topBody)
            quadraticTo(right, topBody, right, topBody + r)
            lineTo(right, bottom - r)
            quadraticTo(right, bottom, right - r, bottom)
            lineTo(left + r, bottom)
            quadraticTo(left, bottom, left, bottom - r)
            lineTo(left, topTab + tabR)
            quadraticTo(left, topTab, left + tabR, topTab)
            close()
        }

        val frontPath = Path().apply {
            moveTo(left + r, pocketTop)
            lineTo(right - r, pocketTop)
            quadraticTo(right, pocketTop, right, pocketTop + r)
            lineTo(right, bottom - r)
            quadraticTo(right, bottom, right - r, bottom)
            lineTo(left + r, bottom)
            quadraticTo(left, bottom, left, bottom - r)
            lineTo(left, pocketTop + r)
            quadraticTo(left, pocketTop, left + r, pocketTop)
            close()
        }

        val frontBrush = Brush.linearGradient(
            colors = listOf(startColor, endColor),
            start = Offset(0f, h * 0.25f),
            end = Offset(w, h)
        )
        val pocketCenterY = (pocketTop + bottom) * 0.5f
        val pocketHeight = bottom - pocketTop
        val monoStrokeW = (minOf(bodyW, bodyH) * 0.058f).coerceIn(1.5.dp.toPx(), 2.6.dp.toPx())
        val monoBackFill = backColor.copy(alpha = 0.08f)
        val creaseColor = Color.Black.copy(alpha = 0.10f)
        val highlightColor = Color.White.copy(alpha = 0.28f)
        val creaseY = pocketTop - 0.5.dp.toPx()
        val highlightY = pocketTop + 0.8.dp.toPx()
        val lineStrokePx = 1.dp.toPx()

        onDrawBehind {
            if (monochrome) {
                drawPath(backPath, color = monoBackFill)
                drawPath(backPath, color = backColor, style = Stroke(monoStrokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(backColor, Offset(left, pocketTop), Offset(right, pocketTop), monoStrokeW * 0.85f)
                drawFolderEmblem(emblem, backColor, centerY = pocketCenterY, pocketHeight = pocketHeight)
            } else {
                drawPath(backPath, color = backColor)
                drawLine(
                    color = creaseColor,
                    start = Offset(left + r * 0.5f, creaseY),
                    end = Offset(right - r * 0.5f, creaseY),
                    strokeWidth = lineStrokePx
                )
                drawPath(frontPath, brush = frontBrush)
                drawLine(
                    color = highlightColor,
                    start = Offset(left + r * 0.65f, highlightY),
                    end = Offset(right - r * 0.65f, highlightY),
                    strokeWidth = lineStrokePx,
                    cap = StrokeCap.Round
                )
                drawFolderEmblem(emblem, emblemTint, centerY = pocketCenterY, pocketHeight = pocketHeight)
            }
        }
    }

    fun drawIosFileCardCached(
        cacheScope: CacheDrawScope,
        kind: IosFileIconKind,
        badgeLabel: String?,
        textMeasurer: TextMeasurer,
        paperColor: Color,
        paperEndColor: Color,
        borderColor: Color,
        foldColor: Color,
        accentColor: Color,
        monochrome: Boolean
    ): DrawResult = with(cacheScope) {
        val w = size.width
        val h = size.height
        val padX = w * 0.15f
        val top = h * 0.08f
        val right = w * 0.85f
        val bottom = h * 0.92f
        val sheetW = right - padX
        val sheetH = bottom - top
        val corner = minOf(sheetW, sheetH) * 0.105f
        val foldSize = minOf(sheetW, sheetH) * 0.25f

        val sheetPath = Path().apply {
            moveTo(padX + corner, top)
            lineTo(right - foldSize, top)
            lineTo(right, top + foldSize)
            lineTo(right, bottom - corner)
            quadraticTo(right, bottom, right - corner, bottom)
            lineTo(padX + corner, bottom)
            quadraticTo(padX, bottom, padX, bottom - corner)
            lineTo(padX, top + corner)
            quadraticTo(padX, top, padX + corner, top)
            close()
        }

        val foldPath = Path().apply {
            moveTo(right - foldSize, top)
            lineTo(right - foldSize, top + foldSize - corner * 0.4f)
            quadraticTo(right - foldSize, top + foldSize, right - foldSize + corner * 0.4f, top + foldSize)
            lineTo(right, top + foldSize)
            close()
        }

        val sheetBrush = Brush.linearGradient(
            colors = listOf(paperColor, paperEndColor),
            start = Offset(padX, top),
            end = Offset(right, bottom)
        )
        val borderStrokeW = (minOf(w, h) * 0.036f).coerceIn(0.9.dp.toPx(), 1.8.dp.toPx())
        val monoStrokeW = (minOf(w, h) * 0.052f).coerceIn(1.3.dp.toPx(), 2.4.dp.toPx())
        val monoFill = accentColor.copy(alpha = 0.08f)
        val monoFoldFill = accentColor.copy(alpha = 0.20f)

        val pillH = sheetH * 0.27f
        val pillMarginX = sheetW * 0.10f
        val pillW = sheetW - pillMarginX * 2f
        val pillTop = bottom - sheetH * 0.10f - pillH
        val pillLeft = padX + pillMarginX
        val pillRadius = CornerRadius(pillH * 0.28f, pillH * 0.28f)
        val effectiveAccent = if (monochrome) accentColor else accentColor
        val pillTextTint = if (monochrome) accentColor else Color.White

        val showBadge = !badgeLabel.isNullOrBlank() && w >= 24.dp.toPx()
        val measured = if (showBadge) {
            val badgeText = badgeLabel!!.uppercase().take(4)
            val heightBucket = (sheetH * 0.5f).toInt()
            val cacheKey = ExtTextCacheKey(
                badgeText = badgeText,
                sheetHeightBucket = heightBucket,
                tintValue = pillTextTint.value,
                densityBits = density.toBits(),
                fontScaleBits = fontScale.toBits()
            )
            extTextCache.get(cacheKey) ?: run {
                val fontSizeSp = when {
                    badgeText.length <= 2 -> (pillH * 0.68f).toSp()
                    badgeText.length == 3 -> (pillH * 0.60f).toSp()
                    else -> (pillH * 0.49f).toSp()
                }
                textMeasurer.measure(
                    text = badgeText,
                    style = TextStyle(
                        fontFamily = RobotoFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = fontSizeSp,
                        color = pillTextTint
                    )
                ).also { extTextCache.put(cacheKey, it) }
            }
        } else null

        val textTopLeft = measured?.let {
            Offset(
                x = pillLeft + (pillW - it.size.width) * 0.5f,
                y = pillTop + (pillH - it.size.height) * 0.5f
            )
        }

        val cx = padX + sheetW * 0.5f
        val glyphCenterY = if (badgeLabel != null) top + sheetH * 0.36f else top + sheetH * 0.54f
        val lineStrokeW = (minOf(w, h) * 0.052f).coerceIn(1.1.dp.toPx(), 2.2.dp.toPx())

        onDrawBehind {
            if (monochrome) {
                drawPath(sheetPath, color = monoFill)
                drawPath(sheetPath, color = accentColor, style = Stroke(monoStrokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(foldPath, color = monoFoldFill)
            } else {
                drawPath(sheetPath, brush = sheetBrush)
                drawPath(sheetPath, color = borderColor, style = Stroke(borderStrokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(foldPath, color = foldColor)
            }

            // Type emblem inside the iOS Files sheet
            val glyphInk = effectiveAccent
            when (kind) {
                IosFileIconKind.PDF -> {
                    // Iconic PDF trefoil/loop ribbon in classic PDF red
                    val r = minOf(sheetW, sheetH) * 0.16f
                    val loop = Path().apply {
                        moveTo(cx, glyphCenterY - r * 0.95f)
                        cubicTo(
                            cx + r * 0.35f, glyphCenterY - r * 0.15f,
                            cx + r * 0.95f, glyphCenterY + r * 0.45f,
                            cx + r * 0.72f, glyphCenterY + r * 0.80f
                        )
                        cubicTo(
                            cx + r * 0.45f, glyphCenterY + r * 1.05f,
                            cx - r * 0.45f, glyphCenterY + r * 0.55f,
                            cx - r * 0.78f, glyphCenterY + r * 0.72f
                        )
                        cubicTo(
                            cx - r * 1.02f, glyphCenterY + r * 0.85f,
                            cx - r * 0.30f, glyphCenterY - r * 0.25f,
                            cx, glyphCenterY - r * 0.95f
                        )
                    }
                    drawPath(
                        path = loop,
                        color = glyphInk,
                        style = Stroke(width = lineStrokeW * 1.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
                IosFileIconKind.WORD_DOC, IosFileIconKind.TEXT_NOTE -> {
                    val lx = padX + sheetW * 0.22f
                    val rx = padX + sheetW * 0.78f
                    val sy = glyphCenterY - sheetH * 0.08f
                    drawLine(glyphInk, Offset(lx, sy), Offset(rx, sy), lineStrokeW, StrokeCap.Round)
                    drawLine(glyphInk.copy(alpha = 0.78f), Offset(lx, sy + sheetH * 0.11f), Offset(rx * 0.92f + lx * 0.08f, sy + sheetH * 0.11f), lineStrokeW, StrokeCap.Round)
                    drawLine(glyphInk.copy(alpha = 0.62f), Offset(lx, sy + sheetH * 0.22f), Offset(padX + sheetW * 0.58f, sy + sheetH * 0.22f), lineStrokeW, StrokeCap.Round)
                }
                IosFileIconKind.SPREADSHEET -> {
                    val gw = sheetW * 0.48f
                    val gh = sheetH * 0.24f
                    val gx = cx - gw * 0.5f
                    val gy = glyphCenterY - gh * 0.5f
                    drawRoundRect(
                        color = glyphInk,
                        topLeft = Offset(gx, gy),
                        size = Size(gw, gh),
                        cornerRadius = CornerRadius(gw * 0.12f, gw * 0.12f),
                        style = Stroke(lineStrokeW * 0.9f)
                    )
                    drawLine(glyphInk, Offset(gx, gy + gh * 0.5f), Offset(gx + gw, gy + gh * 0.5f), lineStrokeW * 0.8f)
                    drawLine(glyphInk, Offset(gx + gw * 0.45f, gy), Offset(gx + gw * 0.45f, gy + gh), lineStrokeW * 0.8f)
                }
                IosFileIconKind.PRESENTATION -> {
                    val sw = sheetW * 0.50f
                    val sh = sheetH * 0.23f
                    val sx = cx - sw * 0.5f
                    val sy = glyphCenterY - sh * 0.5f
                    drawRoundRect(
                        color = glyphInk,
                        topLeft = Offset(sx, sy),
                        size = Size(sw, sh),
                        cornerRadius = CornerRadius(sw * 0.14f, sw * 0.14f),
                        style = Stroke(lineStrokeW * 0.95f)
                    )
                    drawCircle(glyphInk, radius = sh * 0.20f, center = Offset(cx, glyphCenterY))
                }
                IosFileIconKind.CODE -> {
                    val cw = sheetW * 0.22f
                    val ch = sheetH * 0.10f
                    val leftBracket = Path().apply {
                        moveTo(cx - cw * 0.35f, glyphCenterY - ch)
                        lineTo(cx - cw, glyphCenterY)
                        lineTo(cx - cw * 0.35f, glyphCenterY + ch)
                    }
                    val rightBracket = Path().apply {
                        moveTo(cx + cw * 0.35f, glyphCenterY - ch)
                        lineTo(cx + cw, glyphCenterY)
                        lineTo(cx + cw * 0.35f, glyphCenterY + ch)
                    }
                    drawPath(leftBracket, glyphInk, style = Stroke(lineStrokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(rightBracket, glyphInk, style = Stroke(lineStrokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                IosFileIconKind.APK -> {
                    val boxW = sheetW * 0.40f
                    val boxH = sheetH * 0.22f
                    val bx = cx - boxW * 0.5f
                    val by = glyphCenterY - boxH * 0.5f
                    drawRoundRect(
                        color = glyphInk,
                        topLeft = Offset(bx, by),
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(boxW * 0.18f, boxW * 0.18f),
                        style = Stroke(lineStrokeW * 0.95f)
                    )
                    drawLine(glyphInk, Offset(bx, by + boxH * 0.36f), Offset(bx + boxW, by + boxH * 0.36f), lineStrokeW * 0.85f)
                    drawLine(glyphInk, Offset(cx, by), Offset(cx, by + boxH * 0.36f), lineStrokeW * 0.85f)
                }
                IosFileIconKind.ARCHIVE -> {
                    // Iconic iOS Files vertical zipper teeth track
                    val trackTop = top + sheetH * 0.11f
                    val trackBottom = pillTop - sheetH * 0.05f
                    val toothW = sheetW * 0.10f
                    val toothH = (trackBottom - trackTop) / 5.5f
                    var yPos = trackTop
                    var leftSide = true
                    while (yPos + toothH * 0.6f < trackBottom) {
                        val tx = if (leftSide) cx - toothW * 1.05f else cx + toothW * 0.05f
                        drawRoundRect(
                            color = glyphInk.copy(alpha = 0.86f),
                            topLeft = Offset(tx, yPos),
                            size = Size(toothW, toothH * 0.62f),
                            cornerRadius = CornerRadius(toothH * 0.2f, toothH * 0.2f)
                        )
                        yPos += toothH
                        leftSide = !leftSide
                    }
                    // Zipper pull tab
                    drawRoundRect(
                        color = glyphInk,
                        topLeft = Offset(cx - toothW * 0.72f, trackBottom - toothH * 1.1f),
                        size = Size(toothW * 1.44f, toothH * 1.05f),
                        cornerRadius = CornerRadius(toothH * 0.28f, toothH * 0.28f)
                    )
                }
                IosFileIconKind.IMAGE_GENERIC -> {
                    val fw = sheetW * 0.56f
                    val fh = sheetH * 0.34f
                    val fx = cx - fw * 0.5f
                    val fy = glyphCenterY - fh * 0.5f
                    drawRoundRect(
                        color = glyphInk.copy(alpha = 0.18f),
                        topLeft = Offset(fx, fy),
                        size = Size(fw, fh),
                        cornerRadius = CornerRadius(fw * 0.16f, fw * 0.16f)
                    )
                    drawRoundRect(
                        color = glyphInk,
                        topLeft = Offset(fx, fy),
                        size = Size(fw, fh),
                        cornerRadius = CornerRadius(fw * 0.16f, fw * 0.16f),
                        style = Stroke(lineStrokeW * 0.9f)
                    )
                    val mtn = Path().apply {
                        moveTo(fx + fw * 0.14f, fy + fh * 0.80f)
                        lineTo(fx + fw * 0.42f, fy + fh * 0.44f)
                        lineTo(fx + fw * 0.60f, fy + fh * 0.66f)
                        lineTo(fx + fw * 0.76f, fy + fh * 0.50f)
                        lineTo(fx + fw * 0.88f, fy + fh * 0.80f)
                    }
                    drawPath(mtn, glyphInk, style = Stroke(lineStrokeW * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawCircle(glyphInk, radius = fw * 0.09f, center = Offset(fx + fw * 0.74f, fy + fh * 0.28f))
                }
                IosFileIconKind.VIDEO_GENERIC -> {
                    val vw = sheetW * 0.54f
                    val vh = sheetH * 0.32f
                    val vx = cx - vw * 0.5f
                    val vy = glyphCenterY - vh * 0.5f
                    drawRoundRect(
                        color = glyphInk.copy(alpha = 0.16f),
                        topLeft = Offset(vx, vy),
                        size = Size(vw, vh),
                        cornerRadius = CornerRadius(vw * 0.18f, vw * 0.18f)
                    )
                    drawRoundRect(
                        color = glyphInk,
                        topLeft = Offset(vx, vy),
                        size = Size(vw, vh),
                        cornerRadius = CornerRadius(vw * 0.18f, vw * 0.18f),
                        style = Stroke(lineStrokeW * 0.9f)
                    )
                    val play = Path().apply {
                        moveTo(cx - vw * 0.11f, glyphCenterY - vh * 0.24f)
                        lineTo(cx + vw * 0.18f, glyphCenterY)
                        lineTo(cx - vw * 0.11f, glyphCenterY + vh * 0.24f)
                        close()
                    }
                    drawPath(play, color = glyphInk)
                }
                IosFileIconKind.AUDIO_GENERIC -> {
                    val r = minOf(sheetW, sheetH) * 0.095f
                    drawCircle(glyphInk, radius = r, center = Offset(cx - r * 0.9f, glyphCenterY + r * 1.15f))
                    drawLine(glyphInk, Offset(cx - r * 0.05f, glyphCenterY + r * 1.15f), Offset(cx - r * 0.05f, glyphCenterY - r * 1.45f), lineStrokeW, StrokeCap.Round)
                    drawLine(glyphInk, Offset(cx - r * 0.05f, glyphCenterY - r * 1.45f), Offset(cx + r * 1.35f, glyphCenterY - r * 1.0f), lineStrokeW, StrokeCap.Round)
                }
                IosFileIconKind.OTHER_GENERIC -> {
                    val lx = padX + sheetW * 0.22f
                    val rx = padX + sheetW * 0.78f
                    val sy = top + sheetH * 0.42f
                    drawLine(glyphInk.copy(alpha = 0.75f), Offset(lx, sy), Offset(rx, sy), lineStrokeW, StrokeCap.Round)
                    drawLine(glyphInk.copy(alpha = 0.65f), Offset(lx, sy + sheetH * 0.13f), Offset(rx, sy + sheetH * 0.13f), lineStrokeW, StrokeCap.Round)
                    drawLine(glyphInk.copy(alpha = 0.50f), Offset(lx, sy + sheetH * 0.26f), Offset(padX + sheetW * 0.56f, sy + sheetH * 0.26f), lineStrokeW, StrokeCap.Round)
                }
            }

            // Bottom iOS colored pill badge for PDF & useful file formats
            if (badgeLabel != null) {
                if (monochrome) {
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.14f),
                        topLeft = Offset(pillLeft, pillTop),
                        size = Size(pillW, pillH),
                        cornerRadius = pillRadius
                    )
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(pillLeft, pillTop),
                        size = Size(pillW, pillH),
                        cornerRadius = pillRadius,
                        style = Stroke(borderStrokeW)
                    )
                } else {
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(pillLeft, pillTop),
                        size = Size(pillW, pillH),
                        cornerRadius = pillRadius
                    )
                }
                if (measured != null && textTopLeft != null) {
                    drawText(textLayoutResult = measured, topLeft = textTopLeft)
                }
            }
        }
    }

    fun drawFoldedFileCardCached(
        cacheScope: CacheDrawScope,
        extensionLabel: String,
        textMeasurer: TextMeasurer,
        startColor: Color,
        endColor: Color,
        foldColor: Color,
        monochrome: Boolean
    ): DrawResult = drawIosFileCardCached(
        cacheScope = cacheScope,
        kind = IosFileIconKind.OTHER_GENERIC,
        badgeLabel = extensionLabel.ifBlank { null },
        textMeasurer = textMeasurer,
        paperColor = startColor,
        paperEndColor = endColor,
        borderColor = foldColor.copy(alpha = 0.45f),
        foldColor = foldColor,
        accentColor = foldColor,
        monochrome = monochrome
    )

    private val categoryDriveRenderer = ModernGlyphRenderer { primaryBrush, secondary, accent, mono ->
        val w = size.width
        val h = size.height
        val corner = CornerRadius(w * 0.18f, w * 0.18f)
        if (mono) {
            val sw = (minOf(w, h) * 0.068f).coerceIn(1.5.dp.toPx(), 2.6.dp.toPx())
            drawRoundRect(secondary, Offset(w * 0.10f, h * 0.18f), Size(w * 0.80f, h * 0.64f), corner, style = Stroke(sw))
            drawLine(secondary, Offset(w * 0.10f, h * 0.56f), Offset(w * 0.90f, h * 0.56f), sw * 0.85f)
            drawCircle(secondary, w * 0.055f, Offset(w * 0.74f, h * 0.69f))
        } else {
            drawRoundRect(primaryBrush, Offset(w * 0.10f, h * 0.18f), Size(w * 0.80f, h * 0.64f), corner)
            drawLine(Color.White.copy(alpha = 0.28f), Offset(w * 0.14f, h * 0.56f), Offset(w * 0.86f, h * 0.56f), 1.4.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.9f), Offset(w * 0.22f, h * 0.69f), Offset(w * 0.44f, h * 0.69f), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(Color.White, radius = w * 0.05f, center = Offset(w * 0.74f, h * 0.69f))
        }
    }

    val categorySpecs: Map<ExplorerCategory, BinaryIconSpec> = mapOf(
        ExplorerCategory.PHOTOS to BinaryIconSpec(BinaryAmber, BinaryAmberEnd, BinaryAmberDark, BinaryAmberDarkEnd) { brush, sec, _, mono ->
            val w = size.width; val h = size.height; val r = CornerRadius(w * 0.20f, w * 0.20f)
            val tint = if (mono) sec else Color.White
            if (mono) drawRoundRect(sec, Offset(w * 0.10f, h * 0.14f), Size(w * 0.80f, h * 0.72f), r, style = Stroke(2.dp.toPx()))
            else drawRoundRect(brush, Offset(w * 0.10f, h * 0.14f), Size(w * 0.80f, h * 0.72f), r)
            val horizon = Path().apply {
                moveTo(w * 0.20f, h * 0.70f); lineTo(w * 0.40f, h * 0.48f); lineTo(w * 0.55f, h * 0.62f); lineTo(w * 0.69f, h * 0.50f); lineTo(w * 0.80f, h * 0.68f)
            }
            drawPath(horizon, color = tint, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(tint, radius = w * 0.07f, center = Offset(w * 0.66f, h * 0.34f))
        },
        ExplorerCategory.DOWNLOADS to BinaryIconSpec(BinaryEmerald, BinaryEmeraldEnd, BinaryEmeraldDark, BinaryEmeraldDarkEnd) { brush, sec, _, mono ->
            val w = size.width; val h = size.height; val tint = if (mono) sec else Color.White
            if (mono) drawCircle(sec, w * 0.40f, Offset(w * 0.5f, h * 0.5f), style = Stroke(2.dp.toPx()))
            else drawCircle(brush, w * 0.40f, Offset(w * 0.5f, h * 0.5f))
            drawLine(tint, Offset(w * 0.5f, h * 0.28f), Offset(w * 0.5f, h * 0.66f), 2.1.dp.toPx(), StrokeCap.Round)
            val chevron = Path().apply { moveTo(w * 0.35f, h * 0.53f); lineTo(w * 0.50f, h * 0.68f); lineTo(w * 0.65f, h * 0.53f) }
            drawPath(chevron, color = tint, style = Stroke(2.1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        },
        ExplorerCategory.VIDEOS to BinaryIconSpec(BinaryOrchid, BinaryOrchidEnd, BinaryOrchidDark, BinaryOrchidDarkEnd) { brush, sec, _, mono ->
            val w = size.width; val h = size.height; val r = CornerRadius(w * 0.20f, w * 0.20f); val tint = if (mono) sec else Color.White
            if (mono) drawRoundRect(sec, Offset(w * 0.10f, h * 0.18f), Size(w * 0.80f, h * 0.64f), r, style = Stroke(2.dp.toPx()))
            else drawRoundRect(brush, Offset(w * 0.10f, h * 0.18f), Size(w * 0.80f, h * 0.64f), r)
            val play = Path().apply { moveTo(w * 0.42f, h * 0.36f); lineTo(w * 0.66f, h * 0.50f); lineTo(w * 0.42f, h * 0.64f); close() }
            drawPath(play, color = tint)
        },
        ExplorerCategory.DOCUMENTS to BinaryIconSpec(BinaryBlue, BinaryBlueEnd, BinaryBlueDark, BinaryBlueDarkEnd) { brush, sec, _, mono ->
            val w = size.width; val h = size.height; val r = CornerRadius(w * 0.16f, w * 0.16f); val tint = if (mono) sec else Color.White
            if (mono) drawRoundRect(sec, Offset(w * 0.16f, h * 0.12f), Size(w * 0.68f, h * 0.76f), r, style = Stroke(2.dp.toPx()))
            else drawRoundRect(brush, Offset(w * 0.16f, h * 0.12f), Size(w * 0.68f, h * 0.76f), r)
            drawLine(tint, Offset(w * 0.30f, h * 0.38f), Offset(w * 0.70f, h * 0.38f), 2.dp.toPx(), StrokeCap.Round)
            drawLine(tint, Offset(w * 0.30f, h * 0.53f), Offset(w * 0.70f, h * 0.53f), 2.dp.toPx(), StrokeCap.Round)
            drawLine(tint, Offset(w * 0.30f, h * 0.68f), Offset(w * 0.54f, h * 0.68f), 2.dp.toPx(), StrokeCap.Round)
        },
        ExplorerCategory.MUSIC to BinaryIconSpec(BinaryCoral, BinaryCoralEnd, BinaryCoralDark, BinaryCoralDarkEnd) { brush, sec, _, mono ->
            val w = size.width; val h = size.height; val tint = if (mono) sec else Color.White
            if (mono) drawCircle(sec, w * 0.40f, Offset(w * 0.5f, h * 0.5f), style = Stroke(2.dp.toPx()))
            else drawCircle(brush, w * 0.40f, Offset(w * 0.5f, h * 0.5f))
            drawCircle(tint, w * 0.10f, Offset(w * 0.40f, h * 0.64f))
            drawLine(tint, Offset(w * 0.48f, h * 0.64f), Offset(w * 0.48f, h * 0.30f), 2.dp.toPx(), StrokeCap.Round)
            drawLine(tint, Offset(w * 0.48f, h * 0.30f), Offset(w * 0.66f, h * 0.36f), 2.dp.toPx(), StrokeCap.Round)
        },
        ExplorerCategory.ARCHIVES to BinaryIconSpec(BinaryIndigo, BinaryIndigoEnd, BinaryIndigoDark, BinaryIndigoDarkEnd) { brush, sec, _, mono ->
            val w = size.width; val h = size.height; val r = CornerRadius(w * 0.16f, w * 0.16f); val tint = if (mono) sec else Color.White
            if (mono) drawRoundRect(sec, Offset(w * 0.16f, h * 0.14f), Size(w * 0.68f, h * 0.72f), r, style = Stroke(2.dp.toPx()))
            else drawRoundRect(brush, Offset(w * 0.16f, h * 0.14f), Size(w * 0.68f, h * 0.72f), r)
            drawLine(tint, Offset(w * 0.50f, h * 0.22f), Offset(w * 0.50f, h * 0.78f), 2.dp.toPx(), StrokeCap.Round)
            drawRoundRect(tint, Offset(w * 0.40f, h * 0.44f), Size(w * 0.20f, h * 0.16f), CornerRadius(3f, 3f))
        },
        ExplorerCategory.OTHER to BinaryIconSpec(BinarySlate, BinarySlateEnd, BinarySlateDark, BinarySlateDarkEnd) { brush, sec, _, mono ->
            val w = size.width; val h = size.height; val r = CornerRadius(w * 0.12f, w * 0.12f); val box = Size(w * 0.31f, h * 0.31f)
            if (mono) {
                drawRoundRect(sec, Offset(w * 0.14f, h * 0.14f), box, r, style = Stroke(1.8.dp.toPx()))
                drawRoundRect(sec, Offset(w * 0.55f, h * 0.14f), box, r, style = Stroke(1.8.dp.toPx()))
                drawRoundRect(sec, Offset(w * 0.14f, h * 0.55f), box, r, style = Stroke(1.8.dp.toPx()))
                drawRoundRect(sec, Offset(w * 0.55f, h * 0.55f), box, r, style = Stroke(1.8.dp.toPx()))
            } else {
                drawRoundRect(brush, Offset(w * 0.14f, h * 0.14f), box, r)
                drawRoundRect(brush, Offset(w * 0.55f, h * 0.14f), box, r, alpha = 0.72f)
                drawRoundRect(brush, Offset(w * 0.14f, h * 0.55f), box, r, alpha = 0.72f)
                drawRoundRect(brush, Offset(w * 0.55f, h * 0.55f), box, r)
            }
        }
    )

    val driveSpecs: Map<DriveType, BinaryIconSpec> = mapOf(
        DriveType.INTERNAL to BinaryIconSpec(BinaryBlue, BinaryBlueEnd, BinaryBlueDark, BinaryBlueDarkEnd, renderer = categoryDriveRenderer),
        DriveType.WORKSPACE to BinaryIconSpec(BinaryCyan, BinaryCyanEnd, BinaryCyanDark, BinaryCyanDarkEnd, renderer = categoryDriveRenderer),
        DriveType.EXTERNAL_VOLUME to BinaryIconSpec(BinaryEmerald, BinaryEmeraldEnd, BinaryEmeraldDark, BinaryEmeraldDarkEnd, renderer = categoryDriveRenderer),
        DriveType.SAF_TREE to BinaryIconSpec(BinaryIndigo, BinaryIndigoEnd, BinaryIndigoDark, BinaryIndigoDarkEnd, renderer = categoryDriveRenderer)
    )

    private val folderSpecCached = BinaryIconSpec(
        startLight = FolderFrontStartLight,
        endLight = FolderFrontEndLight,
        startDark = FolderFrontStartDark,
        endDark = FolderFrontEndDark,
        backLight = FolderBackLight,
        backDark = FolderBackDark
    ) { _, _, _, _ -> }

    private val pdfFileColorLight = Color(0xFFDC2626)
    private val pdfFileColorDark = Color(0xFFEF4444)
    private val wordFileColorLight = Color(0xFF2563EB)
    private val wordFileColorDark = Color(0xFF60A5FA)
    private val sheetFileColorLight = Color(0xFF16A34A)
    private val sheetFileColorDark = Color(0xFF4ADE80)
    private val slideFileColorLight = Color(0xFFEA580C)
    private val slideFileColorDark = Color(0xFFFB923C)
    private val noteFileColorLight = Color(0xFF475569)
    private val noteFileColorDark = Color(0xFF94A3B8)
    private val codeFileColorLight = Color(0xFF0891B2)
    private val codeFileColorDark = Color(0xFF22D3EE)
    private val apkFileColorLight = Color(0xFF059669)
    private val apkFileColorDark = Color(0xFF34D399)
    private val archiveFileColorLight = Color(0xFF5856D6)
    private val archiveFileColorDark = Color(0xFF818CF8)
    private val imageFileColorLight = Color(0xFFF59E0B)
    private val imageFileColorDark = Color(0xFFFBBF24)
    private val videoFileColorLight = Color(0xFF9333EA)
    private val videoFileColorDark = Color(0xFFC084FC)
    private val audioFileColorLight = Color(0xFFE11D48)
    private val audioFileColorDark = Color(0xFFFB7185)
    private val genericFileColorLight = Color(0xFF64748B)
    private val genericFileColorDark = Color(0xFF94A3B8)

    fun resolveIosFileSpec(item: FileItem): IosFileVisualSpec {
        val ext = item.extension.lowercase().trim()
        return when {
            ext == "pdf" -> IosFileVisualSpec(IosFileIconKind.PDF, pdfFileColorLight, pdfFileColorDark, "PDF")
            ext in setOf("doc", "docx", "rtf", "odt", "pages") ->
                IosFileVisualSpec(IosFileIconKind.WORD_DOC, wordFileColorLight, wordFileColorDark, if (ext == "docx") "DOCX" else ext.uppercase().take(4))
            ext in setOf("xls", "xlsx", "csv", "ods", "numbers") ->
                IosFileVisualSpec(IosFileIconKind.SPREADSHEET, sheetFileColorLight, sheetFileColorDark, if (ext == "xlsx") "XLSX" else ext.uppercase().take(4))
            ext in setOf("ppt", "pptx", "odp", "key") ->
                IosFileVisualSpec(IosFileIconKind.PRESENTATION, slideFileColorLight, slideFileColorDark, if (ext == "pptx") "PPTX" else ext.uppercase().take(4))
            ext in setOf("txt", "md", "markdown", "log") ->
                IosFileVisualSpec(IosFileIconKind.TEXT_NOTE, noteFileColorLight, noteFileColorDark, if (ext == "markdown") "MD" else ext.uppercase().take(4))
            ext in setOf("json", "xml", "html", "htm", "css", "js", "ts", "kt", "kts", "java", "py", "sh", "yml", "yaml", "sql", "c", "cpp", "h") ->
                IosFileVisualSpec(IosFileIconKind.CODE, codeFileColorLight, codeFileColorDark, ext.uppercase().take(4))
            item.isApk || ext in setOf("apk", "xapk", "apks", "aab") ->
                IosFileVisualSpec(IosFileIconKind.APK, apkFileColorLight, apkFileColorDark, ext.ifBlank { "apk" }.uppercase().take(4))
            item.isArchive || ext in setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "cbz", "cbr") ->
                IosFileVisualSpec(IosFileIconKind.ARCHIVE, archiveFileColorLight, archiveFileColorDark, ext.ifBlank { "zip" }.uppercase().take(4))
            item.isImage -> IosFileVisualSpec(IosFileIconKind.IMAGE_GENERIC, imageFileColorLight, imageFileColorDark, null)
            item.isVideo -> IosFileVisualSpec(IosFileIconKind.VIDEO_GENERIC, videoFileColorLight, videoFileColorDark, null)
            item.isAudio -> IosFileVisualSpec(IosFileIconKind.AUDIO_GENERIC, audioFileColorLight, audioFileColorDark, null)
            else -> IosFileVisualSpec(IosFileIconKind.OTHER_GENERIC, genericFileColorLight, genericFileColorDark, null)
        }
    }

    private val filePdfSpec = BinaryIconSpec(pdfFileColorLight, pdfFileColorLight, pdfFileColorDark, pdfFileColorDark, Color(0xFFB91C1C), Color(0xFFDC2626)) { _, _, _, _ -> }
    private val fileDocSpec = BinaryIconSpec(wordFileColorLight, wordFileColorLight, wordFileColorDark, wordFileColorDark, FolderBackLight, FolderBackDark) { _, _, _, _ -> }
    private val fileVideoSpec = BinaryIconSpec(videoFileColorLight, videoFileColorLight, videoFileColorDark, videoFileColorDark, Color(0xFF6B21A8), Color(0xFF7E22CE)) { _, _, _, _ -> }
    private val fileAudioSpec = BinaryIconSpec(audioFileColorLight, audioFileColorLight, audioFileColorDark, audioFileColorDark, Color(0xFFBE123C), Color(0xFFE11D48)) { _, _, _, _ -> }
    private val fileApkSpec = BinaryIconSpec(apkFileColorLight, apkFileColorLight, apkFileColorDark, apkFileColorDark, Color(0xFF047857), Color(0xFF059669)) { _, _, _, _ -> }
    private val fileArchiveSpec = BinaryIconSpec(archiveFileColorLight, archiveFileColorLight, archiveFileColorDark, archiveFileColorDark, Color(0xFF312E81), Color(0xFF4338CA)) { _, _, _, _ -> }
    private val fileOtherSpec = BinaryIconSpec(BinarySlate, BinarySlateEnd, BinarySlateDark, BinarySlateDarkEnd, Color(0xFF334155), Color(0xFF475569)) { _, _, _, _ -> }

    fun folderSpec(): BinaryIconSpec = folderSpecCached

    fun fileSpecColors(item: FileItem): BinaryIconSpec = when {
        item.extension.equals("pdf", ignoreCase = true) -> filePdfSpec
        item.isApk || item.extension == "xapk" -> fileApkSpec
        item.isDocument -> fileDocSpec
        item.isVideo -> fileVideoSpec
        item.isAudio -> fileAudioSpec
        item.isArchive -> fileArchiveSpec
        else -> fileOtherSpec
    }
}

val LocalArcTextMeasurer = staticCompositionLocalOf<TextMeasurer?> { null }

@Composable
private fun resolveColors(spec: BinaryIconSpec, monochrome: Boolean): Triple<Color, Color, Color> {
    if (monochrome) {
        val ink = MaterialTheme.colorScheme.onSurface
        return Triple(ink, ink.copy(alpha = 0.65f), ink)
    }
    val progress = ArcTheme.darkProgress
    return when {
        progress <= 0.001f -> Triple(spec.startLight, spec.endLight, spec.backLight)
        progress >= 0.999f -> Triple(spec.startDark, spec.endDark, spec.backDark)
        else -> Triple(
            lerp(spec.startLight, spec.startDark, progress),
            lerp(spec.endLight, spec.endDark, progress),
            lerp(spec.backLight, spec.backDark, progress)
        )
    }
}

@Composable
fun binaryAccentForDrive(type: DriveType, monochrome: Boolean): Color =
    resolveColors(BinaryIconRegistry.driveSpecs.getValue(type), monochrome).first

@Composable
fun binaryColorsForDrive(type: DriveType, monochrome: Boolean): Pair<Color, Color> {
    val (start, end, _) = resolveColors(BinaryIconRegistry.driveSpecs.getValue(type), monochrome)
    return start to end
}

@Composable
private fun RenderSpecCanvas(spec: BinaryIconSpec, monochrome: Boolean, modifier: Modifier) {
    val (start, end, back) = resolveColors(spec, monochrome)
    Spacer(
        modifier = modifier.drawWithCache {
            val primaryBrush = Brush.linearGradient(listOf(start, end), Offset.Zero, Offset(size.width, size.height))
            onDrawBehind {
                with(spec.renderer) { drawGlyph(primaryBrush, back, start, monochrome) }
            }
        }
    )
}

@Composable
fun BinaryCategoryIcon(category: ExplorerCategory, monochrome: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    RenderSpecCanvas(BinaryIconRegistry.categorySpecs.getValue(category), monochrome, modifier.size(size))
}

@Composable
fun BinaryDriveIcon(type: DriveType, monochrome: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    RenderSpecCanvas(BinaryIconRegistry.driveSpecs.getValue(type), monochrome, modifier.size(size))
}

@Composable
fun BinaryFileItemIcon(item: FileItem, monochrome: Boolean, modifier: Modifier = Modifier, size: Dp? = 26.dp) {
    val sizedModifier = if (size != null) modifier.size(size) else modifier
    if (item.isDirectory) {
        val (start, end, back) = resolveColors(BinaryIconRegistry.folderSpec(), monochrome)
        val emblem = resolveFolderEmblem(item.name)
        val emblemTint = Color.White.copy(alpha = 0.92f)
        Spacer(
            modifier = sizedModifier.drawWithCache {
                BinaryIconRegistry.drawModernFolderCached(
                    cacheScope = this,
                    emblem = emblem,
                    startColor = start,
                    endColor = end,
                    backColor = back,
                    emblemTint = emblemTint,
                    monochrome = monochrome
                )
            }
        )
    } else {
        val textMeasurer = LocalArcTextMeasurer.current ?: rememberTextMeasurer()
        val iosSpec = BinaryIconRegistry.resolveIosFileSpec(item)
        val progress = ArcTheme.darkProgress
        val accentColor = if (monochrome) {
            MaterialTheme.colorScheme.onSurface
        } else {
            lerp(iosSpec.accentLight, iosSpec.accentDark, progress)
        }
        val paperColor = lerp(Color(0xFFFFFFFF), Color(0xFF282A32), progress)
        val paperEndColor = lerp(Color(0xFFF6F7FA), Color(0xFF1F2128), progress)
        val borderColor = lerp(Color(0xFFD0D5DD), Color(0xFF3D404B), progress)
        val foldColor = lerp(Color(0xFFE3E7EE), Color(0xFF353844), progress)
        Spacer(
            modifier = sizedModifier.drawWithCache {
                BinaryIconRegistry.drawIosFileCardCached(
                    cacheScope = this,
                    kind = iosSpec.kind,
                    badgeLabel = iosSpec.badgeLabel,
                    textMeasurer = textMeasurer,
                    paperColor = paperColor,
                    paperEndColor = paperEndColor,
                    borderColor = borderColor,
                    foldColor = foldColor,
                    accentColor = accentColor,
                    monochrome = monochrome
                )
            }
        )
    }
}
