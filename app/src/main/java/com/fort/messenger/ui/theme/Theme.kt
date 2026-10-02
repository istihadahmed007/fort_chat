package com.fort.messenger.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = RoyalBluePrimary,
    onPrimary = SurfaceWhite,
    primaryContainer = IceBlueTint,
    onPrimaryContainer = RoyalBluePrimary,
    secondary = ElectricBlueAccent,
    onSecondary = SurfaceWhite,
    secondaryContainer = IceBlueBorder,
    onSecondaryContainer = DeepNavyTextPrimary,
    background = IceCanvasLight,
    onBackground = DeepNavyTextPrimary,
    surface = SurfaceWhite,
    onSurface = DeepNavyTextPrimary,
    surfaceVariant = IceBlueTint,
    onSurfaceVariant = SlateNavyTextSecondary,
    outline = SurfaceSubtleBorder,
    error = RoseDestructive,
    onError = SurfaceWhite
)

private val DarkColorScheme = darkColorScheme(
    primary = RoyalBlueDark,
    onPrimary = ObsidianCanvasDark,
    primaryContainer = IceBlueTintDark,
    onPrimaryContainer = ElectricBlueDark,
    secondary = ElectricBlueDark,
    onSecondary = ObsidianCanvasDark,
    secondaryContainer = IceBlueBorderDark,
    onSecondaryContainer = TextPrimaryDark,
    background = ObsidianCanvasDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = SurfaceSubtleBorderDark,
    error = RoseDestructive,
    onError = SurfaceWhite
)

@Composable
fun FortTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FortTypography,
        content = content
    )
}
