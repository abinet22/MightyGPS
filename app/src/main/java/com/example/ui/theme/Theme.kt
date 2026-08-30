package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = MC.AccentPrimary,
    onPrimary = MC.TextPrimary,
    secondary = MC.AccentSecondary,
    onSecondary = MC.TextPrimary,
    tertiary = MC.AccentCyan,
    background = MC.Surface0,
    onBackground = MC.TextPrimary,
    surface = MC.Surface1,
    onSurface = MC.TextPrimary,
    surfaceVariant = MC.Surface2,
    onSurfaceVariant = MC.TextSecondary,
    outline = MC.Surface3,
    error = MC.StatusOffline,
    onError = MC.TextPrimary
)

private val LightColorScheme = DarkColorScheme // Fleet dark theme first

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = MC.Surface0.toArgb()
                window.navigationBarColor = MC.Surface0.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
