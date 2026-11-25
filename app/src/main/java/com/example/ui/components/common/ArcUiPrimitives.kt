package com.example.ui.components.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.snapshotFlow
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.data.ArcImageLoader
import com.example.data.FileItem
import com.example.data.MediaPreviewMeta
import com.example.data.MediaThumbnailLoader
import com.example.ui.registry.BinaryFileItemIcon
import com.example.ui.theme.ArcTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ArcCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = ArcTheme.dimens.cardRadius,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
    borderWidth: Dp = ArcTheme.dimens.borderWidth,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val d = ArcTheme.dimens
    val shape = if (cornerRadius == d.cardRadius) {
        d.cardShape
    } else {
        remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    }
    val clickModifier = if (onClick != null) {
        modifier.clip(shape).clickable(onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = clickModifier,
        shape = shape,
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(content = content)
    }
}

@Composable
fun ArcBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    textColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color? = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(ArcTheme.dimens.badgeRadius),
        color = containerColor,
        border = borderColor?.let { BorderStroke(ArcTheme.dimens.borderWidth, it) }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun ArcSectionHeader(
    title: String,
    trailing: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ArcActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val d = ArcTheme.dimens
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(d.pillRadius))
            .clickable(onClick = onClick)
            .let { if (testTag != null) it.testTag(testTag) else it },
        shape = RoundedCornerShape(d.pillRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(d.borderWidth, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(d.smallIconSize), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * Clean iOS-style thumbnail with subtle hairline border on raster media.
 */
@Composable
fun ArcThumbnail(
    item: FileItem,
    monochromeBinaryIcons: Boolean,
    size: Dp = ArcTheme.dimens.fileThumbnailSize,
    iconSize: Dp = ArcTheme.dimens.fileIconSize,
    cornerRadius: Dp = ArcTheme.dimens.smallRadius,
    isScrollInProgress: () -> Boolean = { false }
) {
    val d = ArcTheme.dimens
    val thumbShape = if (cornerRadius == d.smallRadius) {
        d.smallShape
    } else {
        remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    }
    if (item.isImage) {
        val context = LocalContext.current
        val imageLoader = remember(context) { ArcImageLoader.get(context) }
        val rawPx = with(LocalDensity.current) { (size.toPx() * 1.25f).roundToInt() }
        val targetPx = when {
            rawPx <= 64 -> 64
            rawPx <= 96 -> 96
            rawPx <= 128 -> 128
            rawPx <= 176 -> 176
            else -> 224
        }
        val request = remember(item.pathOrUri, item.lastModifiedMillis, item.isSafUri, targetPx) {
            ImageRequest.Builder(context)
                .data(if (item.isSafUri) item.pathOrUri else File(item.pathOrUri))
                .size(targetPx, targetPx)
                .precision(Precision.INEXACT)
                .diskCachePolicy(if (item.isSafUri) CachePolicy.ENABLED else CachePolicy.DISABLED)
                .crossfade(false)
                .build()
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(thumbShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), thumbShape)
        ) {
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else if (item.isVideo) {
        val context = LocalContext.current
        val cached = remember(item.id, item.lastModifiedMillis) {
            MediaThumbnailLoader.getCached(item, needVideoFrame = true)
        }
        val meta by produceState(initialValue = cached, item.id, item.lastModifiedMillis) {
            if (value == null) {
                if (isScrollInProgress()) {
                    snapshotFlow { !isScrollInProgress() }.first { it }
                }
                delay(55)
                if (isScrollInProgress()) {
                    snapshotFlow { !isScrollInProgress() }.first { it }
                }
                value = MediaThumbnailLoader.loadMeta(context, item, needVideoFrame = true)
            }
        }
        val frame = meta?.videoFrame
        if (frame != null) {
            val imageBitmap = remember(frame) { frame.asImageBitmap() }
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(thumbShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(11.dp), tint = Color.White)
                }
            }
        } else {
            BinaryFileItemIcon(item = item, monochrome = monochromeBinaryIcons, size = iconSize)
        }
    } else if (item.isApk) {
        val context = LocalContext.current
        val cachedApk = remember(item.id, item.lastModifiedMillis) {
            MediaThumbnailLoader.getCachedApkIcon(item)
        }
        val apkBitmap by produceState(initialValue = cachedApk, item.id, item.lastModifiedMillis) {
            if (value == null && !MediaThumbnailLoader.hasCachedApkAttempt(item)) {
                if (isScrollInProgress()) {
                    snapshotFlow { !isScrollInProgress() }.first { it }
                }
                delay(55)
                if (isScrollInProgress()) {
                    snapshotFlow { !isScrollInProgress() }.first { it }
                }
                value = MediaThumbnailLoader.loadApkIcon(context, item, maxSizePx = 108)
            }
        }
        val bmp = apkBitmap
        if (bmp != null) {
            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .clip(thumbShape)
            )
        } else {
            BinaryFileItemIcon(item = item, monochrome = monochromeBinaryIcons, size = iconSize)
        }
    } else {
        BinaryFileItemIcon(item = item, monochrome = monochromeBinaryIcons, size = iconSize)
    }
}

@Composable
fun ArcEmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    caption: String? = null,
    actions: @Composable () -> Unit = {}
) {
    val d = ArcTheme.dimens
    Box(
        modifier = modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        ArcCard(cornerRadius = d.cardRadius) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(d.toolbarIconSize), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (caption != null) {
                    Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(2.dp))
                actions()
            }
        }
    }
}
