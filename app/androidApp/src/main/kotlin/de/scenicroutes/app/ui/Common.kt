package de.scenicroutes.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.scenicroutes.shared.model.Loop
import kotlin.math.cos
import kotlin.math.max

/** Schematische Rundenform (ohne Kacheln) – für Listen und als Karten-Platzhalter in Tests. */
@Composable
fun RouteShape(loop: Loop, modifier: Modifier, color: Color = MaterialTheme.colorScheme.primary, width: Float = 5f) {
    val casing = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val g = loop.geom; if (g.size < 2) return@Canvas
        val k = cos(Math.toRadians(g[0][1]))
        val xs = g.map { it[0] * k }; val ys = g.map { it[1] }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val pad = Tokens.spaceSm.toPx(); val s = minOf((size.width - 2 * pad) / max(1e-9, maxX - minX), (size.height - 2 * pad) / max(1e-9, maxY - minY))
        val ox = (size.width - (maxX - minX) * s) / 2; val oy = (size.height - (maxY - minY) * s) / 2
        fun p(i: Int) = Offset((ox + (xs[i] - minX) * s).toFloat(), (oy + (maxY - ys[i]) * s).toFloat())
        val path = Path().apply { moveTo(p(0).x, p(0).y); for (i in 1 until g.size) lineTo(p(i).x, p(i).y) }
        drawPath(path, color, style = Stroke(width.dp.toPx() / 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(casing, Tokens.sizeDotMd.toPx(), p(0)); drawCircle(color, Tokens.sizeDotInner.toPx(), p(0))
    }
}

@Composable
fun Fact(icon: ImageVector, text: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = desc, Modifier.size(Tokens.sizeIconSm), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Tokens.spaceXs))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

fun klasseLabel(k: String) = when (k) { "A+" -> "Spitze"; "A" -> "Top"; "B" -> "Gut"; else -> "Solide" }
fun fmtKm(km: Double) = "%.0f km".format(km)
fun fmtMin(min: Int) = when { min < 60 -> "$min min"; min % 60 == 0 -> "${min / 60} h"; else -> "${min / 60} h ${min % 60} min" }
