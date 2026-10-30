package de.scenicroutes.app.ui

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.CornerUpRight
import com.composables.icons.lucide.Maximize2
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.VolumeX
import com.composables.icons.lucide.LocateFixed
import com.composables.icons.lucide.X
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.scenicroutes.app.data.Kpi

/**
 * Fahrmodus-Anzeige: sehr große Werte für Abbiegung und Distanz, Tippziele 64 dp, alles aus Tokens.
 * In der Navigation sitzen [DriveTurnCard] oben und [DriveBar] unten (nav/NavigationScreen.kt).
 */
@Composable
fun DriveHud(distance: String, unit: String, street: String, then: String?, kpis: List<Kpi>, modifier: Modifier = Modifier,
             icon: ImageVector = Lucide.CornerUpRight, onStop: () -> Unit = {}, onMute: (() -> Unit)? = {}, onOverview: (() -> Unit)? = {}, muted: Boolean = false,
             progress: Float? = null) {
    Column(modifier.fillMaxSize().systemBarsPadding().padding(Tokens.spaceMd), verticalArrangement = Arrangement.SpaceBetween) {
        DriveTurnCard(distance, unit, street, then, icon)
        Spacer(Modifier.weight(1f))
        DriveBar(kpis, onStop, onMute, onOverview, muted = muted, progress = progress)
    }
}

@Composable
fun DriveTurnCard(distance: String, unit: String, street: String, then: String?, icon: ImageVector = Lucide.CornerUpRight, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        Surface(shape = RoundedCornerShape(Tokens.radiusLg), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth().testTag("driveTurn")) {
            Row(Modifier.padding(Tokens.spaceLg), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, "Abbiegen", Modifier.size(androidx.compose.ui.unit.Dp(Tokens.driveTurn.value)), tint = cs.primary)
                Spacer(Modifier.width(Tokens.spaceLg))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(distance, style = MaterialTheme.typography.displayLarge.copy(fontSize = Tokens.driveDist, lineHeight = Tokens.driveDist), color = cs.onSurface)
                        Text(" $unit", style = MaterialTheme.typography.titleLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = Tokens.spaceSm))
                    }
                    Text(street, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (then != null) Surface(shape = RoundedCornerShape(Tokens.radiusMd), color = cs.surfaceContainerHigh, modifier = Modifier.padding(top = Tokens.spaceSm)) {
            Text("Danach: $then", Modifier.padding(horizontal = Tokens.spaceMd, vertical = Tokens.spaceSm), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Untere Fahrleiste im ruhigen Tesla-Stil: oben ein dünner Fortschrittsbalken (falls eingeschaltet),
 * links die Hauptzahl groß in Gold, rechts daneben die übrigen Kennzahlen klein und neutral,
 * darunter drei große Icon-Tasten. Gold ist der einzige Akzent.
 */
@Composable
fun DriveBar(kpis: List<Kpi>, onStop: () -> Unit, onMute: (() -> Unit)? = null, onOverview: (() -> Unit)? = null, modifier: Modifier = Modifier,
             muted: Boolean = false, overview: Boolean = false, progress: Float? = null) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(Tokens.radiusXl), color = cs.surfaceContainer, modifier = modifier.fillMaxWidth().testTag("driveBar")) {
        Column {
            Column(Modifier.padding(horizontal = Tokens.spaceXl, vertical = Tokens.spaceLg)) {
                if (progress != null && kpis.any { it.key == "progress" }) LinearProgressIndicator(progress = { progress }, color = cs.primary, trackColor = cs.surfaceContainerHigh,
                    drawStopIndicator = {}, gapSize = Tokens.spaceNone, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Tokens.spaceMd).height(Tokens.driveProgress).testTag("driveProgress"))
                // Bis zu zwei Nebenwerte stehen neben der Hauptzahl, mehr bekommen eine eigene, gleichmäßig verteilte Zeile.
                val rest = kpis.drop(1)
                if (kpis.isNotEmpty()) Column(Modifier.fillMaxWidth().testTag("driveKpis")) {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                        KpiCell(kpis.first(), primary = true, modifier = Modifier.padding(end = Tokens.spaceXl))
                        if (rest.size <= 2) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Tokens.spaceXl, Alignment.End)) { rest.forEach { KpiCell(it, primary = false) } }
                    }
                    // Ab drei Nebenwerten: Raster mit je vier Spalten, damit auch viele Werte ruhig stehen.
                    if (rest.size > 2) rest.chunked(KPI_COLUMNS).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(top = Tokens.spaceMd)) {
                            row.forEach { KpiCell(it, primary = false, modifier = Modifier.weight(1f)) }
                            repeat(KPI_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = if (kpis.isEmpty()) Tokens.spaceNone else Tokens.spaceLg), verticalAlignment = Alignment.CenterVertically) {
                    HudButton(Lucide.X, "Navigation beenden", onStop, "driveStop")
                    Spacer(Modifier.weight(1f))
                    if (onMute != null) HudButton(if (muted) Lucide.VolumeX else Lucide.Volume2, if (muted) "Ansagen einschalten" else "Ansage stumm", onMute, "driveMute", active = muted)
                    if (onOverview != null) { Spacer(Modifier.width(Tokens.spaceMd)); HudButton(if (overview) Lucide.LocateFixed else Lucide.Maximize2, if (overview) "Zurück zur Fahrt" else "Übersicht", onOverview, "driveOverview", active = overview) }
                }
            }
        }
    }
}

private const val KPI_COLUMNS = 4

@Composable
private fun KpiCell(k: Kpi, primary: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.testTag("kpi-${k.key}")) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(k.value, style = MaterialTheme.typography.headlineSmall.copy(fontSize = if (primary) Tokens.driveKpi else Tokens.driveKpiSecondary,
                lineHeight = if (primary) Tokens.driveKpi else Tokens.driveKpiSecondary), fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
                color = if (primary) cs.primary else cs.onSurface, maxLines = 1, softWrap = false)
            if (k.unit.isNotEmpty()) Text(k.unit, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, maxLines = 1, softWrap = false,
                modifier = Modifier.padding(start = Tokens.spaceXs, bottom = if (primary) Tokens.spaceXs else Tokens.spaceNone))
        }
        Text(k.label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** Distanz für die Abbiegeanzeige: unter 1 km in 10-m-Schritten, sonst km mit einer Nachkommastelle. */
fun driveDistance(m: Double): Pair<String, String> = when {
    m < 100 -> "${(m / 10).toInt().coerceAtLeast(0) * 10}" to "m"
    m < 1000 -> "${(m / 50).toInt() * 50}" to "m"
    else -> String.format(java.util.Locale.GERMANY, "%.1f", m / 1000) to "km"
}

/** Große runde Icon-Taste (64 dp). Aktiv (stumm, Übersicht) = Gold-Fläche, sonst ruhig. */
@Composable private fun HudButton(icon: ImageVector, desc: String, onClick: () -> Unit, tag: String = desc, active: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    FilledIconButton(onClick, Modifier.size(Tokens.touchDrive).testTag(tag),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (active) cs.primary else cs.surfaceContainerHigh, contentColor = if (active) cs.onPrimary else cs.onSurface)) {
        Icon(icon, desc, Modifier.size(Tokens.touchDrive / 2.5f))
    }
}
