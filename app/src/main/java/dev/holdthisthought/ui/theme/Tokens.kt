package dev.holdthisthought.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Daytrace design tokens, layer 1 of 3:
 *
 *  1. **Tokens** (this file): raw, named colours and sizes. Nothing in the UI uses these directly
 *     except the theme and a few always-dark surfaces (hero, alarm, widget).
 *  2. **Semantic roles** (Theme.kt): Material colour scheme, kind palette and game colours built
 *     from tokens, for light and dark.
 *  3. **Motion** (Motion.kt): durations, easings and springs built on the 12 animation principles.
 *
 * Forest green is the one primary colour. Everything else is a quiet, earthy supporting tone
 * (no orange, nothing saturated) on warm, green-tinted stone neutrals, so the app is easy on the eyes.
 */
object Forest {
    val F50 = Color(0xFFEFF5F1)
    val F100 = Color(0xFFDCE9E1)
    val F200 = Color(0xFFBDD5C6)
    val F300 = Color(0xFF9BC0AA)
    val F400 = Color(0xFF73A087)
    val F500 = Color(0xFF55836B)
    val F600 = Color(0xFF41705F) // hero end
    val F700 = Color(0xFF2C5546) // hero start, primary
    val F800 = Color(0xFF1F4236)
    val F900 = Color(0xFF142D24)
    val F950 = Color(0xFF0C1D17)
}

/** Green-tinted stone neutrals for backgrounds, surfaces and text. */
object Stone {
    val S0 = Color(0xFFFCFDFB)
    val S50 = Color(0xFFF4F6F2)
    val S100 = Color(0xFFEEF1EC)
    val S150 = Color(0xFFE7EBE5)
    val S200 = Color(0xFFDFE5DE)
    val S300 = Color(0xFFC9D1CA)
    val S500 = Color(0xFF7D8982)
    val S600 = Color(0xFF5E6A63)
    val S700 = Color(0xFF3F4A44)
    val S800 = Color(0xFF26302B)
    val S850 = Color(0xFF1F2924)
    val S900 = Color(0xFF19221E)
    val S925 = Color(0xFF151D19)
    val S950 = Color(0xFF111814)
}

/** Quiet supporting accents: one per quest and thought kind. */
object Earth {
    val Moss = Color(0xFF6F8455)
    val MossLight = Color(0xFFB4C49A)
    val Sky = Color(0xFF5F88AE)
    val SkyLight = Color(0xFFA3BFD8)
    val Heather = Color(0xFF7F7898)
    val HeatherLight = Color(0xFFBCB5D0)
    val Ochre = Color(0xFFB59745)
    val OchreLight = Color(0xFFD9C17F)
    val Clay = Color(0xFFA8585C)
    val ClayLight = Color(0xFFDB9A9D)
    val Mint = Color(0xFFB9DCC6)
    val Cream = Color(0xFFF4F1E8)
    val Night = Color(0xFF1B2A2E)
    val NightEnd = Color(0xFF26404A)
}

/** 4-point spacing scale. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val xxxl = 40.dp

    /** Side gutter for every screen. */
    val gutter = 20.dp
}
