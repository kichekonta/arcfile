package com.example.ui.theme

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemePreference {
    SYSTEM, LIGHT, DARK
}

val LocalArcIsDarkTheme = staticCompositionLocalOf { false }
val LocalArcDarkProgress = compositionLocalOf { 0f }

object ArcTheme {
    val dimens: ArcDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalArcDimens.current

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalArcIsDarkTheme.current

    val darkProgress: Float
        @Composable
        @ReadOnlyComposable
        get() = LocalArcDarkProgress.current
}

private val ArcDarkColorScheme = darkColorScheme(
    primary = CobaltPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = CobaltSoftDark,
    onPrimaryContainer = Color(0xFFD6EAFF),
    secondary = BinarySlateDark,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceContainer,
    onSecondaryContainer = DarkInk,
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkMuted,
    surfaceTint = Color.Unspecified,
    surfaceContainerLow = Color(0xFF151517),
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF48484A),
    error = ErrorRedDark,
    onError = Color.White
)

private val ArcLightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltSoftLight,
    onPrimaryContainer = Color(0xFF004080),
    secondary = BinarySlate,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceContainer,
    onSecondaryContainer = LightInk,
    background = LightBackground,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightInkMuted,
    surfaceTint = Color.Unspecified,
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = Color(0xFFD1D1D6),
    outline = LightOutline,
    outlineVariant = Color(0xFFD1D1D6),
    error = ErrorRed,
    onError = Color.White
)

val ArcShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

private fun interpolateColorScheme(light: ColorScheme, dark: ColorScheme, fraction: Float): ColorScheme {
    return light.copy(
        primary = lerp(light.primary, dark.primary, fraction),
        onPrimary = lerp(light.onPrimary, dark.onPrimary, fraction),
        primaryContainer = lerp(light.primaryContainer, dark.primaryContainer, fraction),
        onPrimaryContainer = lerp(light.onPrimaryContainer, dark.onPrimaryContainer, fraction),
        secondary = lerp(light.secondary, dark.secondary, fraction),
        onSecondary = lerp(light.onSecondary, dark.onSecondary, fraction),
        secondaryContainer = lerp(light.secondaryContainer, dark.secondaryContainer, fraction),
        onSecondaryContainer = lerp(light.onSecondaryContainer, dark.onSecondaryContainer, fraction),
        background = lerp(light.background, dark.background, fraction),
        onBackground = lerp(light.onBackground, dark.onBackground, fraction),
        surface = lerp(light.surface, dark.surface, fraction),
        onSurface = lerp(light.onSurface, dark.onSurface, fraction),
        surfaceVariant = lerp(light.surfaceVariant, dark.surfaceVariant, fraction),
        onSurfaceVariant = lerp(light.onSurfaceVariant, dark.onSurfaceVariant, fraction),
        surfaceTint = Color.Unspecified,
        surfaceContainerLow = lerp(light.surfaceContainerLow, dark.surfaceContainerLow, fraction),
        surfaceContainer = lerp(light.surfaceContainer, dark.surfaceContainer, fraction),
        surfaceContainerHigh = lerp(light.surfaceContainerHigh, dark.surfaceContainerHigh, fraction),
        outline = lerp(light.outline, dark.outline, fraction),
        outlineVariant = lerp(light.outlineVariant, dark.outlineVariant, fraction),
        error = lerp(light.error, dark.error, fraction),
        onError = lerp(light.onError, dark.onError, fraction)
    )
}

@Composable
fun ArcFilesTheme(
    themePreference: ThemePreference = ThemePreference.LIGHT,
    dimens: ArcDimens = ArcDimens(),
    content: @Composable () -> Unit
) {
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val darkProgressAnim = remember { Animatable(if (darkTheme) 1f else 0f) }
    LaunchedEffect(darkTheme) {
        val target = if (darkTheme) 1f else 0f
        if (darkProgressAnim.value != target) {
            darkProgressAnim.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
        }
    }
    val darkProgress = darkProgressAnim.value
    val activeColorScheme = when {
        darkProgress <= 0.001f -> ArcLightColorScheme
        darkProgress >= 0.999f -> ArcDarkColorScheme
        else -> interpolateColorScheme(ArcLightColorScheme, ArcDarkColorScheme, darkProgress)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
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
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalArcDimens provides dimens,
        LocalArcIsDarkTheme provides darkTheme,
        LocalArcDarkProgress provides darkProgress
    ) {
        MaterialTheme(
            colorScheme = activeColorScheme,
            typography = ArcTypography,
            shapes = ArcShapes,
            content = content
        )
    }
}
