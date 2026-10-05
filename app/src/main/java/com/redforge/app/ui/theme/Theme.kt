package com.redforge.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * RedForge is dark-first by design (an "energetic red and black" gym app),
 * but we still expose a light variant for users who prefer it from Settings.
 */
private val ForgeDarkColorScheme = darkColorScheme(
    primary = ForgeRed,
    onPrimary = ForgeWhite,
    primaryContainer = Color(0xFF3A1114),
    onPrimaryContainer = ForgeWhite,
    secondary = ForgeGold,
    onSecondary = ForgeBlack,
    background = ForgeBlack,
    onBackground = ForgeWhite,
    surface = ForgeSurface,
    onSurface = ForgeWhite,
    surfaceVariant = ForgeSurfaceHigh,
    onSurfaceVariant = ForgeAsh,
    error = ForgeRedBright,
    onError = ForgeWhite,
    outline = ForgeSurfaceHigh
)

private val ForgeLightColorScheme = lightColorScheme(
    primary = ForgeRed,
    onPrimary = ForgeWhite,
    primaryContainer = Color(0xFFFFD2D0),
    onPrimaryContainer = ForgeRedDark,
    secondary = ForgeGold,
    background = Color(0xFFFAFAFA),
    onBackground = ForgeBlack,
    surface = Color(0xFFFFFFFF),
    onSurface = ForgeBlack,
    error = ForgeRedDark
)

@Composable
fun RedForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    forceDark: Boolean = true,
    content: @Composable () -> Unit
) {
    // RedForge uses a dark-only visual system; never let the device light theme
    // switch Material text/content colors to black over the dark app surfaces.
    val useDark = true
    val colorScheme = if (useDark) ForgeDarkColorScheme else ForgeLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ForgeTypography,
    ) {
        // Plain Compose Text resolves its default color from LocalContentColor.
        // The app is dark and its root surfaces are transparent, so provide the
        // dark theme foreground explicitly instead of allowing black text.
        CompositionLocalProvider(
            LocalContentColor provides colorScheme.onBackground,
            content = content
        )
    }
}
