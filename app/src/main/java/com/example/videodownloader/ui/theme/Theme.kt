package com.example.videodownloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.videodownloader.data.settings.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78A7FF),
    secondary = Color(0xFF8BD6C2),
    background = Color(0xFF0B1020),
    surface = Color(0xFF151D30),
    surfaceVariant = Color(0xFF1F293E),
    onSurfaceVariant = Color(0xFFB0B8CC)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B6FE0),
    secondary = Color(0xFF1F8B7A),
    background = Color(0xFFF5F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7EBF3),
    onSurfaceVariant = Color(0xFF4A5265)
)

/** Неоновая — почти чёрный фон + циан и маджента. */
private val NeonColors = darkColorScheme(
    primary = Color(0xFF00E5FF),
    secondary = Color(0xFFFF3CD6),
    background = Color(0xFF05050F),
    surface = Color(0xFF0F1020),
    surfaceVariant = Color(0xFF1A1B33),
    onSurface = Color(0xFFE8E8FF),
    onSurfaceVariant = Color(0xFFA0A4D0),
    onPrimary = Color(0xFF001A20)
)

/** AMOLED — чистый чёрный, экономит батарею на OLED. */
private val AmoledColors = darkColorScheme(
    primary = Color(0xFF78A7FF),
    secondary = Color(0xFF8BD6C2),
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF141414),
    onSurface = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFFAAAAAA)
)

/** Океан — глубокий синий + бирюза. */
private val OceanColors = darkColorScheme(
    primary = Color(0xFF00C8B4),
    secondary = Color(0xFF4FC3F7),
    background = Color(0xFF001A33),
    surface = Color(0xFF002A4D),
    surfaceVariant = Color(0xFF00396B),
    onSurface = Color(0xFFE0F2FF),
    onSurfaceVariant = Color(0xFF9FC4E0)
)

@Composable
fun VideoDownloaderTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.NEON -> NeonColors
        ThemeMode.AMOLED -> AmoledColors
        ThemeMode.OCEAN -> OceanColors
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkColors else LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
