package fr.obdash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.obdash.core.ObdRepository
import fr.obdash.core.VehicleState
import kotlinx.coroutines.delay

private class Channel(
    val label: String, val color: Color, val fixedMax: Float, val unit: String,
    val pick: (VehicleState) -> Float?
)

/**
 * Construits a l'appel (et non figes a l'init de classe) : les couleurs suivent le theme
 * clair/sombre ET l'accent choisi par l'utilisateur.
 */
private fun channels(): List<Channel> = listOf(
    Channel("Régime", D.accent, 6500f, "tr") { it.rpm?.toFloat() },
    Channel("Vitesse", D.ice, 160f, "km/h") { it.speedKmh?.toFloat() },
    Channel("MAP", D.azure, 110f, "kPa") { it.mapKpa?.toFloat() },
    Channel("Papillon", D.mint, 100f, "%") { it.throttlePct?.toFloat() },
    Channel("Charge", if (D.lightMode) Color(0xFF6C3FD1) else Color(0xFFB98BFF), 100f, "%") { it.loadPct?.toFloat() },
    Channel("Avance", if (D.lightMode) Color(0xFFB65C00) else Color(0xFFFF8A5C), 50f, "°") { it.advanceDeg?.toFloat() },
    Channel("Tension", if (D.lightMode) Color(0xFF0B7B66) else Color(0xFF6FE3C4), 16f, "V") { it.voltage?.toFloat() },
    Channel("Eau", if (D.lightMode) Color(0xFFB63A55) else Color(0xFFFF6A8A), 130f, "°C") { it.coolantC?.toFloat() },
    // Couple modelise EMS3140 : alimente quand la session Renault est ouverte (DID 2037)
    Channel("Couple", if (D.lightMode) Color(0xFF9A6420) else Color(0xFFE0A458), 120f, "Nm") { it.torqueNm?.toFloat() }
)

private const val MAX_SAMPLES = 360      // ~2 min a ~3 Hz

@Composable
fun RecorderPage() {
    val CHANNELS = channels()
    var samples by remember { mutableStateOf(listOf<VehicleState>()) }
    var paused by remember { mutableStateOf(false) }
    var autoScale by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(setOf(0, 1, 2, 3)) }   // indices dans CHANNELS

    LaunchedEffect(paused) {
        while (!paused) {
            val s = ObdRepository.state.value
            if (s.connected) samples = (samples + s).takeLast(MAX_SAMPLES)
            delay(330)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ScreenHeader("Enregistreur", "${selected.size} canaux \u00B7 ~2 min \u00B7 ${if (autoScale) "echelle auto" else "pleine echelle"}") {
            TextButton(onClick = { paused = !paused }) {
                Text(if (paused) "Reprendre" else "Pause", color = D.accent, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { samples = emptyList() }) { Text("Effacer", color = D.textLo) }
        }

        // Selecteur de canaux (puces)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            CHANNELS.forEachIndexed { i, ch ->
                val on = i in selected
                Box(Modifier.padding(end = 6.dp)
                    .border(1.dp, if (on) ch.color else D.hairline, Plate(13.dp))
                    .background(if (on) ch.color.copy(alpha = 0.14f) else Color.Transparent, Plate(13.dp))
                    .clickable {
                        selected = if (on) selected - i else selected + i
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(ch.color, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(ch.label, style = MaterialTheme.typography.bodySmall,
                            color = if (on) D.textHi else D.textLo)
                    }
                }
            }
        }

        // Bascule echelle auto / pleine echelle
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            listOf("Echelle auto" to true, "Pleine echelle" to false).forEach { (lbl, v) ->
                Box(Modifier.padding(end = 6.dp)
                    .border(1.dp, if (autoScale == v) D.accent else D.hairline, Plate(5.dp))
                    .clickable { autoScale = v }.padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text(lbl, style = MaterialTheme.typography.bodySmall,
                        color = if (autoScale == v) D.accent else D.textLo)
                }
            }
        }

        // Legende + valeurs courantes des canaux selectionnes
        val last = samples.lastOrNull()
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp)) {
            CHANNELS.forEachIndexed { i, ch ->
                if (i in selected) {
                    Column(Modifier.padding(end = 18.dp)) {
                        Text(ch.label.uppercase(), style = D.eyebrow, color = ch.color)
                        Text(last?.let { ch.pick(it) }?.let { "%.0f %s".format(it, ch.unit) } ?: "--",
                            style = D.readout(14, FontWeight.SemiBold, ch.color))
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(240.dp)
            .border(1.dp, D.hairline, Plate(9.dp))
            .background(D.slate, Plate(9.dp)).padding(10.dp)) {
            if (samples.size < 2 || selected.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (selected.isEmpty()) "Selectionne au moins un canal"
                        else if (paused) "En pause" else "En attente de donnees...",
                        style = MaterialTheme.typography.bodyMedium, color = D.textLo)
                }
            } else {
                Chart(samples, selected, autoScale, Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (autoScale)
                "Echelle auto : chaque canal est normalise sur son propre min/max de la fenetre " +
                    "-> meme un signal faible reste lisible. L'echelle absolue n'est donc pas comparable entre canaux."
            else
                "Pleine echelle : chaque canal sur son maximum theorique (regime 6500, vitesse 160...). " +
                    "Les amplitudes sont comparables mais un signal faible parait ecrase.",
            style = MaterialTheme.typography.labelSmall, color = D.textLo
        )
    }
}

@Composable
private fun Chart(samples: List<VehicleState>, selected: Set<Int>, autoScale: Boolean, modifier: Modifier) {
    val CHANNELS = channels()
    Canvas(modifier) {
        for (i in 0..4) {
            val y = size.height * (i / 4f)
            drawLine(D.slateHi, androidx.compose.ui.geometry.Offset(0f, y),
                androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
        }
        val n = samples.size
        val dx = size.width / (MAX_SAMPLES - 1).toFloat()
        val x0 = size.width - dx * (n - 1)
        for (idx in selected.sorted()) {
            val ch = CHANNELS[idx]
            val vals = samples.map { ch.pick(it) }
            val present = vals.filterNotNull()
            if (present.isEmpty()) continue
            val lo: Float; val hi: Float
            if (autoScale) {
                lo = present.min(); hi = present.max().coerceAtLeast(lo + 1e-3f)
            } else { lo = 0f; hi = ch.fixedMax }
            val span = (hi - lo).coerceAtLeast(1e-3f)
            val path = androidx.compose.ui.graphics.Path()
            var started = false
            vals.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val frac = ((v - lo) / span).coerceIn(0f, 1f)
                val x = x0 + dx * i
                val y = size.height * (1f - frac)
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            drawPath(path, ch.color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}
