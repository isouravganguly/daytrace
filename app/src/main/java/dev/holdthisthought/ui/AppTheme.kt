package dev.holdthisthought.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val WorkColors = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E7E2),
    onPrimaryContainer = Color(0xFF151515),
    background = Color(0xFFF7F7F5),
    onBackground = Color(0xFF151515),
    surface = Color.White,
    onSurface = Color(0xFF151515),
    surfaceVariant = Color(0xFFEDEDEA),
    onSurfaceVariant = Color(0xFF4D4D4D),
    secondary = Color(0xFF6D6D72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E7E2),
    onSecondaryContainer = Color(0xFF151515),
    tertiary = Color(0xFF4E5A61),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4E7E5),
    onTertiaryContainer = Color(0xFF151515),
    outline = Color(0xFFD8D8D2),
    outlineVariant = Color(0xFFE7E7E1),
)

private val RoundedCorner6 = RoundedCornerShape(6.dp)
private val RoundedCorner8 = RoundedCornerShape(8.dp)
private val RoundedCorner10 = RoundedCornerShape(10.dp)
private val RoundedCorner12 = RoundedCornerShape(12.dp)
private val RoundedCorner16 = RoundedCornerShape(16.dp)

private val ThoughtShapes = Shapes(
    extraSmall = RoundedCorner6,
    small = RoundedCorner8,
    medium = RoundedCorner10,
    large = RoundedCorner12,
    extraLarge = RoundedCorner16,
)

@Composable
fun ThoughtTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WorkColors,
        shapes = ThoughtShapes,
        content = content,
    )
}
