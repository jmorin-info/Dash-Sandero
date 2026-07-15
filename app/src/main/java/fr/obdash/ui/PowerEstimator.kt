package fr.obdash.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import fr.obdash.core.ObdRepository
import kotlin.math.max

/**
 * Dyno inertiel : puissance a la roue estimee = (force d'inertie + pertes) x vitesse.
 *   P = ( m.a  +  Crr.m.g  +  0.5.rho.CdA.v^2 ) . v
 * a vient de l'accelerometre longitudinal (meme capteur que le G-metre), v de l'OBD.
 * Valeur indicative facon "dyno" de Torque/DashCommand : l'ordre de grandeur et le PIC
 * sont fiables sur une acceleration pleine charge a plat ; la valeur absolue depend de la
 * masse saisie et suppose une route plane. On l'affiche seulement en acceleration (a>0).
 */
private const val CRR = 0.012        // resistance au roulement
private const val RHO = 1.2          // densite air
private const val CDA = 0.72         // Cx.S approx Sandero (Cx ~0.34, S ~2.1)
private const val G = 9.81

@Composable
fun PowerReadout(massKg: Double, accent: Color, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var kw by remember { mutableFloatStateOf(0f) }
    var peak by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val grav = FloatArray(3)
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                var ay = e.values[1]
                if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    grav[1] = 0.9f * grav[1] + 0.1f * ay; ay -= grav[1]
                }
                val now = SystemClock.elapsedRealtime()
                if (now - last < 100) return
                last = now
                val v = (ObdRepository.state.value.speedKmh ?: 0) / 3.6         // m/s
                val a = ay                                                       // m/s^2 (long.)
                if (a <= 0.15 || v < 3) { kw = 0f; return }
                val force = massKg * a + CRR * massKg * G + 0.5 * RHO * CDA * v * v
                val p = (force * v / 1000.0).toFloat()                          // kW
                kw = p
                peak = max(peak, p)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("PUISSANCE ESTIMÉE (ROUE)", style = D.eyebrow)
            Text("dyno inertiel · pic ${"%.0f".format(peak * 1.35962)} ch",
                style = D.eyebrow.copy(color = D.textLo))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%.0f".format(kw), style = D.readout(30, FontWeight.Bold, accent))
            Text(" kW · ", style = D.eyebrow)
            Text("%.0f".format(kw * 1.35962), style = D.readout(22, FontWeight.SemiBold, D.textHi))
            Text(" ch", style = D.eyebrow)
        }
    }
}
