package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Responsive metrics derived from column count (2-4 on phones, 3-5 on tablets) and available width,
 * ensuring square cards, concentric border radii that never over-clip narrow columns, and balanced gaps.
 */
@Immutable
data class GridCardMetrics(
    val columns: Int,
    val cellWidth: Dp,
    val gridGap: Dp,
    val cardCornerRadius: Dp,
    val mediaCornerRadius: Dp,
    val innerPadding: Dp,
    val playBadgeSize: Dp,
    val durationBadgeRadius: Dp,
    val durationFontSize: TextUnit,
    val overlayPadding: Dp,
    val checkIconSize: Dp,
    val compactText: Boolean
)

/**
 * Single centralized source of truth for standard minimal gaps, radii, and compact sizes.
 * Adjust values here to tune layout density and icon scale across the entire application.
 */
@Immutable
data class ArcDimens(
    // Standard Minimalist Gaps & Paddings
    val screenHorizontalPadding: Dp = 16.dp,
    val sectionGap: Dp = 16.dp,
    val gridGap: Dp = 8.dp,
    val itemGap: Dp = 6.dp,
    val cardPadding: Dp = 12.dp,
    val compactPadding: Dp = 10.dp,
    val maxContentWidth: Dp = 820.dp,

    // Radii & iOS Hairline Borders
    val islandRadius: Dp = 22.dp,
    val cardRadius: Dp = 12.dp,
    val pillRadius: Dp = 10.dp,
    val smallRadius: Dp = 8.dp,
    val badgeRadius: Dp = 999.dp,
    val borderWidth: Dp = 0.5.dp,

    // Top Tab Island
    val islandHorizontalMargin: Dp = 12.dp,
    val islandVerticalMargin: Dp = 6.dp,
    val tabChipHeight: Dp = 34.dp,
    val islandActionSize: Dp = 44.dp,

    // Compact Drive & Category Cards
    val driveCardWidth: Dp = 178.dp,
    val driveCardHeight: Dp = 118.dp,
    val mountCardWidth: Dp = 132.dp,
    val driveIconSize: Dp = 24.dp,
    val categoryIconSize: Dp = 26.dp,
    val progressBarHeight: Dp = 4.dp,

    // File List & Square Grid Icons
    val fileThumbnailSize: Dp = 38.dp,
    val fileIconSize: Dp = 32.dp,
    val gridIconAspectRatio: Float = 1f,
    val toolbarIconSize: Dp = 19.dp,
    val smallIconSize: Dp = 15.dp,

    // Home Category Circles & Tool Badges
    val categoryCircleSize: Dp = 44.dp,
    val donutGaugeSize: Dp = 48.dp,
    val toolCircleSize: Dp = 36.dp,
    val isWideHomeLayout: Boolean = false
) {
    val cardShape: RoundedCornerShape = RoundedCornerShape(cardRadius)
    val pillShape: RoundedCornerShape = RoundedCornerShape(pillRadius)
    val smallShape: RoundedCornerShape = RoundedCornerShape(smallRadius)

    fun gridMetricsFor(columns: Int, availableWidth: Dp): GridCardMetrics {
        val safeCols = columns.coerceIn(2, 5)
        val isNarrow = availableWidth < 350.dp
        val isTablet = availableWidth >= 600.dp
        val gap = when {
            isNarrow && safeCols >= 4 -> 5.dp
            isNarrow && safeCols == 3 -> 7.dp
            safeCols <= 2 -> if (isTablet) 14.dp else 12.dp
            safeCols == 3 -> if (isTablet) 11.dp else 9.dp
            else -> if (isTablet) 9.dp else 7.dp
        }
        val totalGaps = gap * (safeCols - 1)
        val cellWidth = ((availableWidth - screenHorizontalPadding * 2 - totalGaps) / safeCols)
            .coerceAtLeast(52.dp)
        val innerPad = when {
            cellWidth < 78.dp -> 2.5.dp
            safeCols >= 4 && cellWidth < 96.dp -> 3.dp
            safeCols == 3 -> 4.dp
            else -> 5.dp
        }
        val mediaRadius = (cellWidth * 0.095f).coerceIn(5.dp, 14.dp)
        val cardRadius = (mediaRadius + innerPad).coerceIn(7.dp, 16.dp)
        val playSize = (cellWidth * 0.27f).coerceIn(18.dp, 36.dp)
        val durationRadius = (mediaRadius * 0.55f).coerceIn(3.dp, 6.dp)
        val overlayPad = when {
            cellWidth < 78.dp -> 3.dp
            cellWidth < 92.dp -> 4.dp
            else -> 6.dp
        }
        val checkSize = when {
            cellWidth < 78.dp -> 14.dp
            cellWidth < 92.dp -> 15.dp
            else -> 18.dp
        }

        return GridCardMetrics(
            columns = safeCols,
            cellWidth = cellWidth,
            gridGap = gap,
            cardCornerRadius = cardRadius,
            mediaCornerRadius = mediaRadius,
            innerPadding = innerPad,
            playBadgeSize = playSize,
            durationBadgeRadius = durationRadius,
            durationFontSize = when {
                cellWidth < 78.dp -> 8.sp
                cellWidth < 92.dp -> 8.5.sp
                else -> 10.sp
            },
            overlayPadding = overlayPad,
            checkIconSize = checkSize,
            compactText = cellWidth < 92.dp
        )
    }

    companion object {
        fun forWidth(availableWidth: Dp): ArcDimens = when {
            availableWidth < 350.dp -> ArcDimens(
                screenHorizontalPadding = 12.dp,
                sectionGap = 12.dp,
                gridGap = 6.dp,
                itemGap = 5.dp,
                cardPadding = 10.dp,
                compactPadding = 8.dp,
                islandRadius = 18.dp,
                islandHorizontalMargin = 8.dp,
                tabChipHeight = 32.dp,
                islandActionSize = 40.dp,
                driveCardWidth = 156.dp,
                driveCardHeight = 108.dp,
                mountCardWidth = 120.dp,
                driveIconSize = 22.dp,
                categoryIconSize = 24.dp,
                fileThumbnailSize = 34.dp,
                fileIconSize = 28.dp,
                toolbarIconSize = 18.dp,
                categoryCircleSize = 38.dp,
                donutGaugeSize = 42.dp,
                toolCircleSize = 32.dp
            )
            availableWidth >= 840.dp -> ArcDimens(
                screenHorizontalPadding = 24.dp,
                sectionGap = 20.dp,
                gridGap = 10.dp,
                itemGap = 8.dp,
                cardPadding = 16.dp,
                compactPadding = 12.dp,
                maxContentWidth = 1120.dp,
                driveCardWidth = 204.dp,
                driveCardHeight = 126.dp,
                mountCardWidth = 152.dp,
                driveIconSize = 26.dp,
                categoryIconSize = 28.dp,
                fileThumbnailSize = 42.dp,
                fileIconSize = 35.dp,
                toolbarIconSize = 20.dp,
                categoryCircleSize = 50.dp,
                donutGaugeSize = 52.dp,
                toolCircleSize = 40.dp,
                isWideHomeLayout = true
            )
            availableWidth >= 600.dp -> ArcDimens(
                screenHorizontalPadding = 20.dp,
                sectionGap = 18.dp,
                gridGap = 9.dp,
                itemGap = 7.dp,
                cardPadding = 14.dp,
                compactPadding = 11.dp,
                maxContentWidth = 920.dp,
                driveCardWidth = 192.dp,
                driveCardHeight = 122.dp,
                mountCardWidth = 144.dp,
                driveIconSize = 25.dp,
                categoryIconSize = 27.dp,
                fileThumbnailSize = 40.dp,
                fileIconSize = 34.dp,
                toolbarIconSize = 20.dp,
                categoryCircleSize = 48.dp,
                donutGaugeSize = 50.dp,
                toolCircleSize = 38.dp,
                isWideHomeLayout = true
            )
            else -> ArcDimens(isWideHomeLayout = availableWidth >= 520.dp)
        }
    }
}

val LocalArcDimens = staticCompositionLocalOf { ArcDimens() }
