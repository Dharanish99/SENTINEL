package com.dharanish.spoofguarddialer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SentinalColorScheme = darkColorScheme(
    primary = SentinalEmerald,
    onPrimary = SentinalBg,
    secondary = SentinalAmber,
    onSecondary = SentinalBg,
    error = SentinalRed,
    onError = SentinalTextPrimary,
    background = SentinalBg,
    onBackground = SentinalTextPrimary,
    surface = SentinalCardBg,
    onSurface = SentinalTextPrimary,
    surfaceVariant = SentinalBtnBg,
    onSurfaceVariant = SentinalTextSecondary,
    outline = SentinalDivider
)

@Composable
fun SpoofGuardDialerTheme(
    // SENTINAL is always dark — ignore system theme and dynamic color
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = SentinalColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SentinalTypography,
        content = content
    )
}
