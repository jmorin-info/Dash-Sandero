package fr.obdash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.obdash.core.TripRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos

private val dateFull = SimpleDateFormat("EEEE d MMMM · HH:mm", Locale.FRENCH)

@Composable
fun TripDetail(t: TripRecord, onDelete: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(dateFull.format(Date(t.start)).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium, color = D.textHi)
                Text(fmt(t.durationSec) + " de conduite", style = D.eyebrow)
            }
            Text("SUPPRIMER", style = D.eyebrow.copy(color = D.redline, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.clickable { onDelete() }.padding(6.dp))
        }

        // Trace GPS : grande, elle occupe la hauteur disponible
        val pts = parseTrack(t.track)
        Box(Modifier.fillMaxWidth().weight(1f)
            .background(D.slate, Plate(10.dp)).padding(14.dp),
            contentAlignment = Alignment.Center) {
            if (pts.size >= 2) {
                RouteMap(pts, Modifier.fillMaxSize())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TRACÉ GPS", style = D.eyebrow)
                    Text("${pts.size} points", style = D.eyebrow)
                }
            } else {
                Text("Aucune trace GPS pour ce trajet.\nActive « Trace GPS des trajets » dans les Réglages.",
                    style = MaterialTheme.typography.bodySmall, color = D.textLo)
            }
        }
        Spacer(Modifier.height(10.dp))

        // Score de conduite (trajets archives avant la fonction : ecoScore = -1)
        if (t.ecoScore >= 0) {
            ScoreBar(t)
            Spacer(Modifier.height(8.dp))
        }
        val avgSpeed = if (t.durationSec > 0) t.distanceKm / (t.durationSec / 3600.0) else 0.0
        Grid(listOf(
            "Distance OBD" to "%.1f km".format(t.distanceKm),
            "Distance GPS" to if (t.gpsKm > 0) "%.1f km".format(t.gpsKm) else "—",
            "Vitesse moy." to "%.0f km/h".format(avgSpeed),
            "Vitesse max" to "${t.maxSpeed} km/h",
            "Carburant" to "%.2f L".format(t.litres),
            "Conso moy." to "%.1f L/100".format(t.avgL100),
            "Coût" to "%.2f €".format(t.cost),
            "Durée" to fmt(t.durationSec)
        ))
    }
}

@Composable
private fun Grid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    Box(Modifier.weight(1f).background(D.slate, Plate(8.dp)).padding(12.dp)) {
                        Column {
                            Text(label.uppercase(), style = D.eyebrow)
                            Spacer(Modifier.height(2.dp))
                            Text(value, style = D.readout(17, FontWeight.SemiBold, D.textHi))
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Trace de route normalisee (projection lon x cos(lat)), degrade depart -> arrivee. */
@Composable
private fun RouteMap(pts: List<Pair<Double, Double>>, modifier: Modifier) {
    Canvas(modifier) {
        val midLat = pts.map { it.first }.average()
        val k = cos(Math.toRadians(midLat))
        val xs = pts.map { it.second * k }
        val ys = pts.map { it.first }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val spanX = (maxX - minX).coerceAtLeast(1e-6); val spanY = (maxY - minY).coerceAtLeast(1e-6)
        val pad = size.minDimension * 0.10f
        val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        val offX = (size.width - spanX * scale) / 2
        val offY = (size.height - spanY * scale) / 2
        fun px(i: Int) = Offset(
            (offX + (xs[i] - minX) * scale).toFloat(),
            (size.height - (offY + (ys[i] - minY) * scale)).toFloat()
        )
        val path = Path().apply {
            moveTo(px(0).x, px(0).y)
            for (i in 1 until pts.size) lineTo(px(i).x, px(i).y)
        }
        drawPath(path, Brush.linearGradient(listOf(D.mint, D.accent)),
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(D.mint, radius = 6.dp.toPx(), center = px(0))
        drawCircle(D.accent, radius = 6.dp.toPx(), center = px(pts.size - 1))
    }
}

@Composable
private fun ScoreBar(t: TripRecord) {
    val col = when { t.ecoScore >= 75 -> D.mint; t.ecoScore >= 45 -> D.warn; else -> D.redline }
    Box(Modifier.fillMaxWidth().background(D.slate, Plate(8.dp)).edgeAccent(col)
        .padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("SCORE DE CONDUITE", style = D.eyebrow)
                Text("${t.hardAccels} accél. franches · ${t.hardBrakes} freinages appuyés" +
                    if (t.redlineSec > 0) " · ${t.redlineSec}s zone rouge" else "",
                    style = D.eyebrow.copy(color = D.textLo))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${t.ecoScore}", style = D.readout(28, FontWeight.Bold, col))
                Text(" /100", style = D.eyebrow)
            }
        }
    }
}

private fun parseTrack(s: String): List<Pair<Double, Double>> =
    if (s.isBlank()) emptyList()
    else s.split(";").mapNotNull {
        val p = it.split(","); val a = p.getOrNull(0)?.toDoubleOrNull(); val b = p.getOrNull(1)?.toDoubleOrNull()
        if (a != null && b != null) a to b else null
    }

private fun fmt(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d h %02d".format(h, m) else "%d min %02d".format(m, s)
}
