package com.example.videodownloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.videodownloader.data.settings.ThemeMode

// ================ БРЕНДОВЫЕ ГРАДИЕНТЫ ================

/** Основной градиент: розовый → фиолетовый → голубой */
val BrandGradient = Brush.linearGradient(
    listOf(
        Color(0xFFFE2C55),  // TikTok pink
        Color(0xFF9B4EFF),  // фиолетовый
        Color(0xFF00C8FF),  // голубой
    )
)

/** Горизонтальный градиент для кнопок */
val BrandGradientHorizontal = Brush.horizontalGradient(
    listOf(Color(0xFFFE2C55), Color(0xFF9B4EFF))
)

/** Мягкий градиент для фонов карточек */
val SoftGradient = Brush.linearGradient(
    listOf(
        Color(0xFF1A1F35),
        Color(0xFF0F1225),
    )
)

// ================ ЦВЕТОВЫЕ СХЕМЫ ================

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFE2C55),
    onPrimary = Color.White,
    secondary = Color(0xFF9B4EFF),
    onSecondary = Color.White,
    tertiary = Color(0xFF00C8FF),
    onTertiary = Color(0xFF001A20),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFE8E8FF),
    surface = Color(0xFF151D30),
    onSurface = Color(0xFFE8E8FF),
    surfaceVariant = Color(0xFF1F293E),
    onSurfaceVariant = Color(0xFFB0B8CC),
    outline = Color(0xFF2A3555),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF3D1A1A),
    onErrorContainer = Color(0xFFFFD0D0),
    primaryContainer = Color(0xFF3D1530),
    onPrimaryContainer = Color(0xFFFFD0E8),
    tertiaryContainer = Color(0xFF0F2A3D),
    onTertiaryContainer = Color(0xFFC8E8FF),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFD81E45),
    onPrimary = Color.White,
    secondary = Color(0xFF7B3FE0),
    onSecondary = Color.White,
    tertiary = Color(0xFF0091B8),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF101528),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101528),
    surfaceVariant = Color(0xFFEBEFF7),
    onSurfaceVariant = Color(0xFF4A5265),
    error = Color(0xFFD32F2F),
    errorContainer = Color(0xFFFFE5E5),
    onErrorContainer = Color(0xFF5A0000),
)

private val NeonColors = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF001A20),
    secondary = Color(0xFFFF3CD6),
    onSecondary = Color(0xFF1A001A),
    tertiary = Color(0xFFB14EFF),
    background = Color(0xFF05050F),
    onBackground = Color(0xFFE8E8FF),
    surface = Color(0xFF0F1020),
    onSurface = Color(0xFFE8E8FF),
    surfaceVariant = Color(0xFF1A1B33),
    onSurfaceVariant = Color(0xFFA0A4D0),
)

private val AmoledColors = darkColorScheme(
    primary = Color(0xFFFE2C55),
    onPrimary = Color.White,
    secondary = Color(0xFF9B4EFF),
    onSecondary = Color.White,
    tertiary = Color(0xFF00C8FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEEEEEE),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFEEEEEE),
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFAAAAAA),
)

private val OceanColors = darkColorScheme(
    primary = Color(0xFF00C8B4),
    onPrimary = Color(0xFF001A17),
    secondary = Color(0xFF4FC3F7),
    onSecondary = Color(0xFF001A2E),
    tertiary = Color(0xFF00E5FF),
    background = Color(0xFF001A33),
    onBackground = Color(0xFFE0F2FF),
    surface = Color(0xFF002A4D),
    onSurface = Color(0xFFE0F2FF),
    surfaceVariant = Color(0xFF00396B),
    onSurfaceVariant = Color(0xFF9FC4E0),
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
