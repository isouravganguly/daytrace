package dev.holdthisthought.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.holdthisthought.data.local.ThoughtKind

private val LightColors = lightColorScheme(
    primary = Color(0xFF3A47A8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E6FA),
    onPrimaryContainer = Color(0xFF1A2160),
    secondary = Color(0xFF6B675E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE8DF),
    onSecondaryContainer = Color(0xFF26241F),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF1D1C19),
    surface = Color(0xFFF7F5F0),
    onSurface = Color(0xFF1D1C19),
    surfaceVariant = Color(0xFFEDEAE2),
    onSurfaceVariant = Color(0xFF6B675E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFBF8),
    surfaceContainer = Color(0xFFF1EEE7),
    surfaceContainerHigh = Color(0xFFEBE8E0),
    surfaceContainerHighest = Color(0xFFE5E1D8),
    outline = Color(0xFFD6D1C5),
    outlineVariant = Color(0xFFE6E2D8),
    error = Color(0xFFB3261E),
    inverseSurface = Color(0xFF2B2A26),
    inverseOnSurface = Color(0xFFF4F1EA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C1FF),
    onPrimary = Color(0xFF1A2160),
    primaryContainer = Color(0xFF2E3A8C),
    onPrimaryContainer = Color(0xFFE3E6FA),
    secondary = Color(0xFFB4AFA4),
    onSecondary = Color(0xFF2B2A26),
    secondaryContainer = Color(0xFF3A3832),
    onSecondaryContainer = Color(0xFFECE8DF),
    background = Color(0xFF151513),
    onBackground = Color(0xFFEAE7E0),
    surface = Color(0xFF151513),
    onSurface = Color(0xFFEAE7E0),
    surfaceVariant = Color(0xFF2A2925),
    onSurfaceVariant = Color(0xFFB4AFA4),
    surfaceContainerLowest = Color(0xFF10100E),
    surfaceContainerLow = Color(0xFF1B1A18),
    surfaceContainer = Color(0xFF201F1C),
    surfaceContainerHigh = Color(0xFF2A2925),
    surfaceContainerHighest = Color(0xFF34332E),
    outline = Color(0xFF4A4842),
    outlineVariant = Color(0xFF34332E),
    inverseSurface = Color(0xFFEAE7E0),
    inverseOnSurface = Color(0xFF2B2A26),
)

/** Each kind gets its own quiet accent so the library is scannable at a glance. */
@Immutable
data class KindColors(val accent: Color, val container: Color, val onContainer: Color)

@Immutable
data class KindPalette(val thought: KindColors, val learning: KindColors, val todo: KindColors) {
    fun of(kind: ThoughtKind) = when (kind) {
        ThoughtKind.THOUGHT -> thought
        ThoughtKind.LEARNING -> learning
        ThoughtKind.TODO -> todo
    }
}

private val LightKinds = KindPalette(
    thought = KindColors(Color(0xFF4F5FD0), Color(0xFFE6E9FB), Color(0xFF26307A)),
    learning = KindColors(Color(0xFFB26B12), Color(0xFFFBEEDB), Color(0xFF6A3F06)),
    todo = KindColors(Color(0xFF23845F), Color(0xFFDDF1E8), Color(0xFF0F4D36)),
)

private val DarkKinds = KindPalette(
    thought = KindColors(Color(0xFFA9B4FF), Color(0xFF2A3166), Color(0xFFE0E4FF)),
    learning = KindColors(Color(0xFFF2BE73), Color(0xFF4A3414), Color(0xFFFBE6C6)),
    todo = KindColors(Color(0xFF7DD3AE), Color(0xFF173F30), Color(0xFFCDEFDF)),
)

val LocalKindPalette = staticCompositionLocalOf { LightKinds }

private val Serif = FontFamily.Serif

private val AppTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontFamily = Serif, fontWeight = FontWeight.Normal),
        headlineLarge = base.headlineLarge.copy(fontFamily = Serif, fontWeight = FontWeight.Normal),
        headlineMedium = base.headlineMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Normal),
        headlineSmall = base.headlineSmall.copy(fontFamily = Serif, fontWeight = FontWeight.Normal),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Large writing style for the capture canvas. */
val CanvasTextStyle = TextStyle(fontFamily = Serif, fontSize = 24.sp, lineHeight = 34.sp)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun HoldThisThoughtTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalKindPalette provides if (darkTheme) DarkKinds else LightKinds) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
