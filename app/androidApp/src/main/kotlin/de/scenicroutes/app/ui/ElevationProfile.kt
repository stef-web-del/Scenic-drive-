package de.scenicroutes.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.scenicroutes.shared.model.Loop

/** Höhenprofil mit hervorgehobenen Highlight-Abschnitten (gelb). */
@Composable
fun ElevationProfile(loop: Loop, modifier: Modifier) {
    val prof = loop.prof
    val line = MaterialTheme.colorScheme.primary
    val hl = MaterialTheme.colorScheme.secondary
    val minH = prof.minOrNull() ?: 0; val maxH = prof.maxOrNull() ?: 1
    Canvas(modifier.semantics { contentDescription = "Höhenprofil, $minH bis $maxH Meter, ${loop.hm} Höhenmeter" }) {
        if (prof.size < 2) return@Canvas
        val totalM = (prof.size - 1) * loop.profStep.toFloat()
        fun x(m: Float) = m / totalM * size.width
        loop.highlights.forEach { h ->
            val x0 = x(h.km.toFloat() * 1000); drawRect(hl.copy(alpha = 0.28f), Offset(x0, 0f), Size(x(h.len.toFloat()), size.height))
        }
        val range = (maxH - minH).coerceAtLeast(50).toFloat(); val top = Tokens.spaceXsm.toPx(); val h = size.height - top
        fun y(v: Int) = top + h - (v - minH) / range * h
        val p = Path().apply { moveTo(0f, y(prof[0])); prof.forEachIndexed { i, v -> lineTo(x(i * loop.profStep.toFloat()), y(v)) } }
        val fill = Path().apply { addPath(p); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), line.copy(alpha = 0.02f))))
        drawPath(p, line, style = Stroke(Tokens.strokeThin.toPx()))
    }
}
