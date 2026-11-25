package com.example.ui.components.dialogs

import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.FileItem
import com.example.data.FormatUtils
import com.example.ui.components.common.ArcThumbnail
import kotlinx.coroutines.launch

@Composable
fun MediaLightboxDialog(
    queue: List<FileItem>,
    initialItem: FileItem,
    onDismiss: () -> Unit,
    onActiveItemChanged: (FileItem) -> Unit,
    onOpenWith: (FileItem) -> Unit,
    onOpenExternal: (FileItem) -> Unit,
    onShare: (FileItem) -> Unit,
    onInfo: (FileItem) -> Unit
) {
    val safeQueue = remember(queue, initialItem.id) {
        if (queue.isEmpty()) listOf(initialItem) else queue
    }
    val initialIndex = remember(safeQueue, initialItem.id) {
        safeQueue.indexOfFirst { it.id == initialItem.id }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { safeQueue.size }
    )
    val filmstripState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()

    var showChrome by remember { mutableStateOf(true) }
    var isCurrentZoomed by remember(pagerState.currentPage) { mutableStateOf(false) }
    var dismissDragFraction by remember(pagerState.currentPage) { mutableFloatStateOf(0f) }

    val activeItem = safeQueue.getOrElse(pagerState.currentPage) { initialItem }

    LaunchedEffect(pagerState.currentPage, safeQueue) {
        val current = safeQueue.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        onActiveItemChanged(current)
        if (current.isVideo) {
            showChrome = true
        }
        if (safeQueue.size > 1) {
            filmstripState.animateScrollToItem(pagerState.currentPage)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val lp = window.attributes
                    if (lp.layoutInDisplayCutoutMode != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES) {
                        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        window.attributes = lp
                    }
                }
                WindowInsetsControllerCompat(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }

        val backdropAlpha = (1f - dismissDragFraction * 0.68f).coerceIn(0.28f, 1f)
        val chromeVisible = showChrome && dismissDragFraction < 0.03f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF06090F).copy(alpha = backdropAlpha))
                .testTag("media_lightbox_dialog")
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isCurrentZoomed && dismissDragFraction < 0.02f,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("lightbox_pager")
            ) { page ->
                val pageItem = safeQueue[page]
                if (pageItem.isVideo) {
                    OriginalVideoPlayer(
                        item = pageItem,
                        onOpenExternal = { onOpenExternal(pageItem) },
                        isActive = pagerState.currentPage == page,
                        onOpenWith = { onOpenWith(pageItem) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    OriginalImageViewer(
                        item = pageItem,
                        darkBackdrop = true,
                        transparentBackground = true,
                        showBottomBadge = showChrome && safeQueue.size == 1,
                        onTap = { showChrome = !showChrome },
                        onZoomChanged = { zoomed ->
                            if (pagerState.currentPage == page) {
                                isCurrentZoomed = zoomed
                            }
                        },
                        onDismiss = onDismiss,
                        onSwipeUpInfo = { onInfo(pageItem) },
                        onDismissDragProgress = { progress ->
                            if (pagerState.currentPage == page) {
                                dismissDragFraction = progress
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Floating translucent top bar (gradient bleeds behind status bar; buttons stay clear of notch)
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.80f),
                                    Color.Black.copy(alpha = 0.38f),
                                    Color.Transparent
                                )
                            )
                        )
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.45f)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("lightbox_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close lightbox",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = activeItem.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val counterText = buildString {
                            append("${pagerState.currentPage + 1} / ${safeQueue.size}")
                            if (activeItem.sizeBytes > 0L) {
                                append(" · ")
                                append(FormatUtils.formatBytes(activeItem.sizeBytes))
                            }
                        }
                        Text(
                            text = counterText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("lightbox_counter_badge")
                        )
                    }

                    IconButton(
                        onClick = { onOpenWith(activeItem) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("lightbox_open_with_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open with external app",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { onShare(activeItem) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("lightbox_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share media",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { onInfo(activeItem) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("lightbox_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Media properties",
                            tint = Color.White
                        )
                    }
                }
            }

            // Bottom edge-to-edge gradient scrim + clean arrowless thumbnail strip
            AnimatedVisibility(
                visible = chromeVisible && safeQueue.size > 1 && !activeItem.isVideo,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.52f),
                                    Color.Black.copy(alpha = 0.80f)
                                )
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF0E141F).copy(alpha = 0.74f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                    ) {
                        LazyRow(
                            state = filmstripState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("lightbox_thumbnail_bar"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            itemsIndexed(safeQueue, key = { _, item -> item.id }) { idx, item ->
                                val isSelected = idx == pagerState.currentPage
                                Surface(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            scope.launch { pagerState.animateScrollToPage(idx) }
                                        }
                                        .testTag("lightbox_thumb_$idx"),
                                    shape = RoundedCornerShape(10.dp),
                                    border = if (isSelected) {
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                                    },
                                    color = Color(0xFF121824)
                                ) {
                                    ArcThumbnail(
                                        item = item,
                                        monochromeBinaryIcons = false,
                                        size = 46.dp,
                                        iconSize = 24.dp,
                                        cornerRadius = 10.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Compact top-right prev/next switcher on video pages when queue has multiple items
            if (safeQueue.size > 1 && activeItem.isVideo && chromeVisible) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 64.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.62f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                    ) {
                        IconButton(
                            onClick = {
                                val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                                scope.launch { pagerState.animateScrollToPage(prev) }
                            },
                            enabled = pagerState.currentPage > 0,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("lightbox_prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous media",
                                tint = if (pagerState.currentPage > 0) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.62f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                    ) {
                        IconButton(
                            onClick = {
                                val next = (pagerState.currentPage + 1).coerceAtMost(safeQueue.lastIndex)
                                scope.launch { pagerState.animateScrollToPage(next) }
                            },
                            enabled = pagerState.currentPage < safeQueue.lastIndex,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("lightbox_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next media",
                                tint = if (pagerState.currentPage < safeQueue.lastIndex) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            }
        }
    }
}
