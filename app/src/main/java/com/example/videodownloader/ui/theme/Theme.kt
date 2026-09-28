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
    surface = Color(0xFF151D30)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B6FE0),
    secondary = Color(0xFF1F8B7A),
    background = Color(0xFFF5F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7EBF3),
    onSurfaceVariant = Color(0xFF4A5265)
)

@Composable
fun VideoDownloaderTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
