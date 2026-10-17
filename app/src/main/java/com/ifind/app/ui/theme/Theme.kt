package com.ifind.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ifind.app.data.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = BrandCyan,
    onPrimary = Color.Black,
    secondary = BrandBlue,
    onSecondary = Color.White,
    tertiary = AccentEmerald,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = AccentCoral
)

private val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandCyan,
    onSecondary = Color.Black,
    tertiary = AccentEmerald,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = AccentCoral
)

@Composable
fun IFindTheme(
    appTheme: AppTheme = AppTheme.FOLLOW_SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        AppTheme.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
