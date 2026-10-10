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

// Semantic roles (layer 2): forest green is the single primary across the whole app; see Tokens.kt.
private val LightColors = lightColorScheme(
    primary = Forest.F700,
    onPrimary = Color.White,
    primaryContainer = Forest.F100,
    onPrimaryContainer = Forest.F900,
    inversePrimary = Forest.F300,
    secondary = Earth.Moss,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E9DA),
    onSecondaryContainer = Color(0xFF2B3720),
    tertiary = Earth.Sky,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE7EF),
    onTertiaryContainer = Color(0xFF22384C),
    background = Stone.S50,
    onBackground = Stone.S800,
    surface = Stone.S50,
    onSurface = Stone.S800,
    surfaceVariant = Stone.S150,
    onSurfaceVariant = Stone.S600,
    surfaceTint = Forest.F700,
    surfaceContainerLowest = Stone.S0,
    surfaceContainerLow = Color(0xFFF8FAF6),
    surfaceContainer = Stone.S100,
    surfaceContainerHigh = Stone.S150,
    surfaceContainerHighest = Stone.S200,
    outline = Stone.S300,
    outlineVariant = Stone.S200,
    error = Earth.Clay,
    onError = Color.White,
    errorContainer = Color(0xFFF3DEDF),
    onErrorContainer = Color(0xFF4A1F22),
    inverseSurface = Stone.S800,
    inverseOnSurface = Stone.S100,
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Forest.F300,
    onPrimary = Forest.F950,
    primaryContainer = Forest.F700,
    onPrimaryContainer = Forest.F100,
    inversePrimary = Forest.F700,
    secondary = Earth.MossLight,
    onSecondary = Color(0xFF1F2A15),
    secondaryContainer = Color(0xFF3C4A2E),
    onSecondaryContainer = Color(0xFFE4E9DA),
    tertiary = Earth.SkyLight,
    onTertiary = Color(0xFF17293A),
    tertiaryContainer = Color(0xFF34495C),
    onTertiaryContainer = Color(0xFFDDE7EF),
    background = Stone.S950,
    onBackground = Color(0xFFE1E8E3),
    surface = Stone.S950,
    onSurface = Color(0xFFE1E8E3),
    surfaceVariant = Stone.S850,
    onSurfaceVariant = Color(0xFFA4B1A9),
    surfaceTint = Forest.F300,
    surfaceContainerLowest = Stone.S925,
    surfaceContainerLow = Stone.S900,
    surfaceContainer = Color(0xFF1B2520),
    surfaceContainerHigh = Stone.S850,
    surfaceContainerHighest = Color(0xFF29342E),
    outline = Color(0xFF46524B),
    outlineVariant = Color(0xFF2C3731),
    error = Earth.ClayLight,
    onError = Color(0xFF3E1416),
    errorContainer = Color(0xFF5A2A2D),
    onErrorContainer = Color(0xFFF3DEDF),
    inverseSurface = Color(0xFFE1E8E3),
    inverseOnSurface = Stone.S800,
    scrim = Color.Black,
)

val LocalDarkTheme = staticCompositionLocalOf { false }

/** Each kind gets its own quiet accent so the library is scannable at a glance. */
@Immutable
data class KindColors(val accent: Color, val container: Color, val onContainer: Color)

@Immutable
data class KindPalette(
    val thought: KindColors,
    val learning: KindColors,
    val todo: KindColors,
    val desire: KindColors,
) {
    fun of(kind: ThoughtKind) = when (kind) {
        ThoughtKind.THOUGHT -> thought
        ThoughtKind.LEARNING -> learning
        ThoughtKind.TODO -> todo
        ThoughtKind.DESIRE -> desire
    }
}

private val LightKinds = KindPalette(
    thought = KindColors(Earth.Sky, Color(0xFFDDE7EF), Color(0xFF22384C)),
    learning = KindColors(Earth.Ochre, Color(0xFFEFE8D3), Color(0xFF4A3C14)),
    todo = KindColors(Forest.F600, Forest.F100, Forest.F900),
    desire = KindColors(Earth.Heather, Color(0xFFE6E3EE), Color(0xFF342F45)),
)

private val DarkKinds = KindPalette(
    thought = KindColors(Earth.SkyLight, Color(0xFF34495C), Color(0xFFDDE7EF)),
    learning = KindColors(Earth.OchreLight, Color(0xFF4A3F22), Color(0xFFEFE8D3)),
    todo = KindColors(Forest.F300, Forest.F700, Forest.F100),
    desire = KindColors(Earth.HeatherLight, Color(0xFF4A4560), Color(0xFFE6E3EE)),
)

/** Quiet accents for the game layer: one per quest, plus XP gold, streak flame and hero gradient. */
@Immutable
data class GameColors(
    val rise: Color,
    val reflect: Color,
    val fuel: Color,
    val move: Color,
    val verdict: Color,
    val xp: Color,
    val flame: Color,
    val track: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val glow: Color,
)

private val LightGame = GameColors(
    rise = Earth.Ochre,
    reflect = Earth.Heather,
    fuel = Earth.Moss,
    move = Forest.F600,
    verdict = Earth.Sky,
    xp = Color(0xFFC2A04A),
    flame = Earth.Ochre,
    track = Color(0x1426302B),
    heroStart = Forest.F700,
    heroEnd = Forest.F600,
    glow = Earth.Mint,
)

private val DarkGame = GameColors(
    rise = Earth.OchreLight,
    reflect = Earth.HeatherLight,
    fuel = Earth.MossLight,
    move = Forest.F300,
    verdict = Earth.SkyLight,
    xp = Color(0xFFD9BE74),
    flame = Earth.OchreLight,
    track = Color(0x1AFFFFFF),
    heroStart = Forest.F800,
    heroEnd = Color(0xFF305B4B),
    glow = Earth.Mint,
)

val LocalGameColors = staticCompositionLocalOf { LightGame }

val LocalKindPalette = staticCompositionLocalOf { LightKinds }

private val Serif = FontFamily.Serif

// Chunky, game-like sans for headings and numbers; the serif is kept for writing.
private val AppTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Large writing style for the capture canvas. */
val CanvasTextStyle = TextStyle(fontFamily = Serif, fontSize = 24.sp, lineHeight = 34.sp)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun HoldThisThoughtTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalKindPalette provides if (darkTheme) DarkKinds else LightKinds,
        LocalGameColors provides if (darkTheme) DarkGame else LightGame,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
