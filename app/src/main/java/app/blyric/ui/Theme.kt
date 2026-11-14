package app.blyric.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlyricColors = darkColorScheme(
    primary = Color(0xFF6CB4FF),
    onPrimary = Color(0xFF001B3E),
    background = Color(0xFF0A1226),
    onBackground = Color.White,
    surface = Color(0xFF0A1226),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF16213F),
    onSurfaceVariant = Color(0xFFB8C7E6),
)

@Composable
fun BlyricTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BlyricColors, content = content)
}
