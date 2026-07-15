package fr.obdash.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private data class GState(
    val lat: Float = 0f, val lon: Float = 0f,          // corriges de l'offset
    val rawLat: Float = 0f, val rawLon: Float = 0f,    // bruts (pour capturer le zero)
    val trail: List<Pair<Float, Float>> = emptyList(),
    val peakLat: Float = 0f, val peakAccel: Float = 0f, val peakBrake: Float = 0f
)

/**
 * G-metre inertiel, optimise et calibrable :
 * - le capteur tourne a ~50 Hz mais l'etat Compose n'est emis qu'a ~30 Hz maximum
 *   (les pics sont accumules entre deux emissions : rien n'est perdu, mais 40 % de
 *   recompositions en moins sur la boucle chaude d'un head unit modeste) ;
 * - bouton ZERO : capture l'inclinaison de montage de la tablette comme reference
 *   (persistee) — indispensable si la dalle n'est pas parfaitement verticale.
 */
@Composable
fun GMeterPanel(
    modifier: Modifier = Modifier,
    accent: Color = D.accent,
    offLat: Float = 0f,
    offLon: Float = 0f,
    onZero: ((Float, Float) -> Unit)? = null
) {
    val ctx = LocalContext.current
    var g by remember { mutableStateOf(GState()) }
    val off by rememberUpdatedState(offLat to offLon)

    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val grav = FloatArray(3)
        var lastEmit = 0L
        var accLat = 0f; var accAccel = 0f; var accBrake = 0f   // pics entre deux emissions
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                var ax = e.values[0]; var ay = e.values[1]
                if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    grav[0] = 0.9f * grav[0] + 0.1f * ax
                    grav[1] = 0.9f * grav[1] + 0.1f * ay
                    ax -= grav[0]; ay -= grav[1]
                }
                val rawLat = ax / 9.81f
                val rawLon = ay / 9.81f
                val lat = rawLat - off.first
                val lon = rawLon - off.second
                accLat = max(accLat, abs(lat)); accAccel = max(accAccel, lon); accBrake = max(accBrake, -lon)

                val now = SystemClock.elapsedRealtime()
                if (now - lastEmit >= 33) {           // ~30 Hz max vers Compose
                    lastEmit = now
                    g = g.copy(
                        lat = lat, lon = lon, rawLat = rawLat, rawLon = rawLon,
                        trail = (g.trail + (lat to lon)).takeLast(40),
                        peakLat = max(g.peakLat, accLat),
                        peakAccel = max(g.peakAccel, accAccel),
                        peakBrake = max(g.peakBrake, accBrake)
                    )
                    accLat = 0f; accAccel = 0f; accBrake = 0f
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("G-MÈTRE", style = D.eyebrow)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onZero != null) {
                    Text("ZÉRO", style = D.eyebrow.copy(color = D.textLo, fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .clickable { onZero(g.rawLat, g.rawLon) }
                            .padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Text("%.2f g".format(sqrt(g.lat * g.lat + g.lon * g.lon)),
                    style = D.readout(13, FontWeight.SemiBold, accent))
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f; val cy = size.height / 2f
                val r = size.minDimension / 2f * 0.88f
                val maxG = 1.0f
                for (i in 1..4) {
                    val rr = r * i / 4f
                    drawCircle(if (i == 4) D.hairline else D.slateHi.copy(alpha = 0.7f),
                        rr, Offset(cx, cy), style = Stroke(1f))
                }
                val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
                drawLine(D.slateHi, Offset(cx - r, cy), Offset(cx + r, cy), 1f, pathEffect = dash)
                drawLine(D.slateHi, Offset(cx, cy - r), Offset(cx, cy + r), 1f, pathEffect = dash)
                g.trail.forEachIndexed { i, (lx, ly) ->
                    val a = (i + 1f) / g.trail.size * 0.5f
                    drawCircle(accent.copy(alpha = a * 0.5f), 2.5f,
                        Offset(cx + (lx / maxG) * r, cy - (ly / maxG) * r))
                }
                val px = cx + (g.lat / maxG).coerceIn(-1.2f, 1.2f) * r
                val py = cy - (g.lon / maxG).coerceIn(-1.2f, 1.2f) * r
                drawCircle(accent.copy(alpha = 0.25f), 11f, Offset(px, py))
                drawCircle(accent, 5f, Offset(px, py))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Peak("ACCÉL.", g.peakAccel, D.mint)
            Peak("FREIN", g.peakBrake, D.redline)
            Peak("VIRAGE", g.peakLat, accent)
        }
    }
}

@Composable
private fun Peak(label: String, v: Float, col: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%.2f".format(v), style = D.readout(15, FontWeight.SemiBold, col))
        Text(label, style = D.eyebrow)
    }
}
