package de.scenicroutes.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import de.scenicroutes.app.ui.Tokens.Dark as D
import de.scenicroutes.app.ui.Tokens.Light as L

private val shapes = Shapes(
    small = RoundedCornerShape(Tokens.radiusSm), medium = RoundedCornerShape(Tokens.radiusMd),
    large = RoundedCornerShape(Tokens.radiusLg), extraLarge = RoundedCornerShape(Tokens.radiusXl),
)

/** Kartenstil aus den Tokens (app/tools/gen_tokens.py → assets/map/), OpenFreeMap-Kacheln. */
const val STYLE_LIGHT = "asset://map/scenic-light.json"
const val STYLE_DARK = "asset://map/scenic-dark.json"

// ---- Palette 0.6.4: Navy (#1A1A2E/#16213E/#0F3460) + Gold-Akzent #EFC07B, Stefan 30.10.2026 ----
private fun brandScheme(dark: Boolean) = if (dark) Tokens.Dark.let { b -> darkColorScheme(
    primary = b.primary, onPrimary = b.onPrimary, primaryContainer = b.primaryContainer, onPrimaryContainer = b.onPrimaryContainer,
    secondary = b.accent, onSecondary = b.onAccent, secondaryContainer = b.primaryContainer, onSecondaryContainer = b.onPrimaryContainer,
    tertiary = b.onSurfaceVariant, background = b.background, surface = b.surface, onBackground = b.onSurface, onSurface = b.onSurface, onSurfaceVariant = b.onSurfaceVariant,
    surfaceContainerLowest = b.background, surfaceContainerLow = b.surface, surfaceContainer = b.surfaceContainer, surfaceContainerHigh = b.surfaceContainerHigh, surfaceContainerHighest = b.surfaceContainerHigh,
    surfaceVariant = b.surfaceContainerHigh, outline = b.outline, outlineVariant = b.border, surfaceTint = Color.Transparent,
    error = b.error, errorContainer = b.errorContainer, onErrorContainer = b.onErrorContainer,
) } else Tokens.Light.let { b -> lightColorScheme(
    primary = b.primary, onPrimary = b.onPrimary, primaryContainer = b.primaryContainer, onPrimaryContainer = b.onPrimaryContainer,
    secondary = b.primary, onSecondary = b.onPrimary, secondaryContainer = b.primaryContainer, onSecondaryContainer = b.onPrimaryContainer,
    tertiary = b.onSurfaceVariant, background = b.background, surface = b.surface, onBackground = b.onSurface, onSurface = b.onSurface, onSurfaceVariant = b.onSurfaceVariant,
    surfaceContainerLowest = b.background, surfaceContainerLow = b.surface, surfaceContainer = b.surfaceContainer, surfaceContainerHigh = b.surfaceContainerHigh, surfaceContainerHighest = b.surfaceContainerHigh,
    surfaceVariant = b.surfaceContainerHigh, outline = b.outline, outlineVariant = b.border, surfaceTint = Color.Transparent,
    error = b.error, errorContainer = b.errorContainer, onErrorContainer = b.onErrorContainer,
) }

private val Geist = androidx.compose.ui.text.font.FontFamily(listOf(400, 500, 600).map { w ->
    androidx.compose.ui.text.font.Font(de.scenicroutes.app.R.font.geist, androidx.compose.ui.text.font.FontWeight(w),
        variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(w)))
})
private fun ts(t: Triple<androidx.compose.ui.unit.TextUnit, androidx.compose.ui.unit.TextUnit, Int>) =
    androidx.compose.ui.text.TextStyle(fontFamily = Geist, fontSize = t.first, lineHeight = t.second, fontWeight = androidx.compose.ui.text.font.FontWeight(t.third), fontFeatureSettings = "tnum")
/** Sechs Stufen aus den Tokens, auf die Material-Rollen verteilt. */
private val brandType = Tokens.Type.let { y -> androidx.compose.material3.Typography(
    displayLarge = ts(y.display), displayMedium = ts(y.display), displaySmall = ts(y.display),
    headlineLarge = ts(y.headline), headlineMedium = ts(y.headline), headlineSmall = ts(y.headline),
    titleLarge = ts(y.title), titleMedium = ts(y.title), titleSmall = ts(y.title),
    bodyLarge = ts(y.body), bodyMedium = ts(y.body), bodySmall = ts(y.caption),
    labelLarge = ts(y.label), labelMedium = ts(y.label), labelSmall = ts(y.caption),
) }

/** Kartenfarben des aktuellen Themas (nur im Brand-Vorschlag gesetzt). */
data class MapColors(val land: Color, val water: Color, val forest: Color, val road: Color, val roadMajor: Color, val label: Color,
                     val routeActive: Color, val routeCasing: Color, val routeAlt: Color, val approach: Color)
val LocalMapColors = androidx.compose.runtime.staticCompositionLocalOf { brandMap(false) }
val LocalDark = androidx.compose.runtime.staticCompositionLocalOf { false }
fun brandMap(dark: Boolean) = if (dark) Tokens.MapDark.run { MapColors(land, water, forest, road, roadMajor, label, routeActive, routeCasing, routeAlt, approach) }
    else Tokens.MapLight.run { MapColors(land, water, forest, road, roadMajor, label, routeActive, routeCasing, routeAlt, approach) }

@Composable
fun ScenicTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = false, brand: Boolean = true, content: @Composable () -> Unit) {
    // dynamic/brand bleiben als Parameter für alte Aufrufe; es gibt nur noch das freigegebene Markenthema.
    androidx.compose.runtime.CompositionLocalProvider(LocalMapColors provides brandMap(dark), LocalDark provides dark) {
        MaterialTheme(colorScheme = brandScheme(dark), shapes = shapes, typography = brandType, content = content)
    }
}

/** Statusfarben (Fehler/Warnung/Erfolg/Aufnahme) des aktuellen Themas. */
data class StatusColors(val warning: Color, val success: Color, val record: Color, val onRecord: Color)
@Composable fun statusColors() = if (LocalDark.current) Tokens.Dark.run { StatusColors(warning, success, record, onRecord) } else Tokens.Light.run { StatusColors(warning, success, record, onRecord) }

@Composable fun mapStyle() = if (LocalDark.current) STYLE_DARK else STYLE_LIGHT

/** Score-Farbe in Stufen (wie Web scoreColor). */
fun scoreColor(s: Double): Color { var c = Tokens.scoreStops[0].second; for ((v, col) in Tokens.scoreStops) if (s >= v - 0.015) c = col; return c }
/** Abschnittsfarbe: Abschnitts-Score 0…0,40 → Blau … Gold-Akzent (wie Web MapView). */
fun segColor(s: Double): Color {
    val x = 0.15 + minOf(1.0, maxOf(0.0, s) / 0.4) * 0.09
    val st = Tokens.scoreStops
    if (x <= st.first().first) return st.first().second
    for (i in 1 until st.size) if (x <= st[i].first) { val (a, ca) = st[i - 1]; val (b, cb) = st[i]; return lerp(ca, cb, ((x - a) / (b - a)).toFloat()) }
    return st.last().second
}
