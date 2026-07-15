package fr.obdash.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.obdash.core.ObdRepository
import fr.obdash.core.Settings
import fr.obdash.core.SettingsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Instantane immuable de l'etat du chrono (observe par l'UI, recompose seulement s'il change). */
data class PerfSnapshot(
    val phase: String = "Prêt",
    val elapsed: Double = 0.0,
    val speed: Int = 0,
    val t50: Double? = null,
    val t100: Double? = null,
    val d400: Double? = null,
    val v400: Int = 0,
    val r80120: Double? = null
)

/**
 * Chrono base sur la vitesse OBD, avec interpolation lineaire du franchissement des seuils.
 * Départ arrêté (0-50, 0-100, 400 m) et reprise (80-120) detectes automatiquement.
 * Emet un PerfSnapshot : l'UI ne se recompose que lorsqu'il change (silencieux au ralenti).
 */
class PerfEngine {
    private val _flow = MutableStateFlow(PerfSnapshot())
    val flow: StateFlow<PerfSnapshot> = _flow
    val events = mutableListOf<Pair<String, Double>>()

    private var lastV = 0.0
    private var lastT = 0L
    private var dist = 0.0
    private var stActive = false; private var stT0 = 0L
    private var t50: Double? = null; private var t100: Double? = null
    private var d400: Double? = null; private var v400 = 0
    private var rollActive = false; private var rollT0 = 0L
    private var r80120: Double? = null

    private fun cross(target: Double, v: Double, t: Long, t0: Long): Double {
        val frac = if (v != lastV) (target - lastV) / (v - lastV) else 1.0
        return (lastT + frac * (t - lastT) - t0) / 1000.0
    }

    fun update(vNull: Double?, t: Long) {
        events.clear()
        val v = vNull ?: return
        if (lastT != 0L) {
            val dt = (t - lastT) / 1000.0
            if (dt in 0.0..2.0) dist += (v + lastV) / 2.0 / 3.6 * dt
        }
        if (!stActive && lastV <= 2.0 && v > 2.0) {
            stActive = true; stT0 = t; dist = 0.0; t50 = null; t100 = null; d400 = null; v400 = 0
        }
        var elapsed = 0.0
        if (stActive) {
            elapsed = (t - stT0) / 1000.0
            if (t50 == null && v >= 50) t50 = cross(50.0, v, t, stT0)
            if (t100 == null && v >= 100) { t100 = cross(100.0, v, t, stT0); events.add("0100" to t100!!) }
            if (d400 == null && dist >= 400) { d400 = elapsed; v400 = v.toInt(); events.add("400" to d400!!) }
            if (v < 2.0 || elapsed > 40.0 || (t100 != null && d400 != null)) stActive = false
        }
        if (!rollActive && !stActive && lastV < 80 && v >= 80 && v > lastV) { rollActive = true; rollT0 = t }
        if (rollActive) {
            if (v >= 120) { r80120 = cross(120.0, v, t, rollT0); events.add("80120" to r80120!!); rollActive = false }
            else if (v < 78 || (t - rollT0) / 1000.0 > 30) rollActive = false
        }
        val phase = when {
            stActive -> "Départ arrêté"
            rollActive -> "Reprise 80-120"
            t100 != null || r80120 != null || d400 != null -> "Terminé"
            else -> "Prêt"
        }
        val active = stActive || rollActive
        lastV = v; lastT = t
        _flow.value = PerfSnapshot(
            phase, if (stActive) elapsed else (t100 ?: 0.0),
            if (active) v.toInt() else 0, t50, t100, d400, v400, r80120
        )
    }

    fun reset() {
        stActive = false; rollActive = false
        t50 = null; t100 = null; d400 = null; v400 = 0; r80120 = null; dist = 0.0
        _flow.value = PerfSnapshot()
    }
}

@Composable
fun PerformancePage(repo: SettingsRepo, settings: Settings) {
    val scope = rememberCoroutineScope()
    val engine = remember { PerfEngine() }
    val snap by engine.flow.collectAsStateWithLifecycle()
    val current by rememberUpdatedState(settings)

    LaunchedEffect(Unit) {
        ObdRepository.state.collect { st ->
            engine.update(st.speedKmh?.toDouble(), SystemClock.elapsedRealtime())
            for ((which, value) in engine.events) {
                val (key, best) = when (which) {
                    "0100" -> Pair(SettingsRepo.B0100, current.best0100)
                    "80120" -> Pair(SettingsRepo.B80120, current.best80120)
                    else -> Pair(SettingsRepo.B400, current.best400)
                }
                if (best == 0.0 || value < best) scope.launch { repo.setDouble(key, value) }
            }
        }
    }

    Column(Modifier.fillMaxSize()
        .let { if (settings.weave) it.carbonWeave() else it }.vignette(0.4f).padding(16.dp)) {
        ScreenHeader("Performance", "Chrono OBD · G-mètre inertiel")

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // --- Chrono
            Column(Modifier.weight(1.3f).fillMaxHeight()) {
                Text(snap.phase.uppercase(), style = D.eyebrow.copy(
                    color = if (snap.phase == "Prêt") D.textLo else D.accent,
                    fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().background(D.slate, Plate(10.dp))
                    .edgeAccent(D.accent).padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%.2f".format(snap.elapsed),
                            style = D.readout(60, FontWeight.Bold, D.ice))
                        Text("SECONDES  ·  ${snap.speed} KM/H", style = D.eyebrow)
                    }
                }
                Spacer(Modifier.height(10.dp))
                PerfRow("0 → 100 km/h", snap.t100, settings.best0100)
                PerfRow("0 → 50 km/h", snap.t50, null)
                PerfRow("80 → 120 km/h", snap.r80120, settings.best80120)
                PerfRow("400 m départ arrêté", snap.d400, settings.best400,
                    suffix = if (snap.v400 > 0) " · ${snap.v400} km/h" else "")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Nouvelle mesure", Modifier.weight(1f)) { engine.reset() }
                    GhostButton("Effacer records", Modifier.weight(1f)) {
                        scope.launch {
                            repo.setDouble(SettingsRepo.B0100, 0.0)
                            repo.setDouble(SettingsRepo.B80120, 0.0)
                            repo.setDouble(SettingsRepo.B400, 0.0)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().background(D.slate, Plate(10.dp))
                    .edgeAccent(D.accent).padding(14.dp)) {
                    PowerReadout(settings.massKg, D.accent)
                }
                Spacer(Modifier.height(8.dp))
                WheelsRow()
                Spacer(Modifier.height(8.dp))
                Text("Départ arrêté et reprise détectés automatiquement. Précision limitée par " +
                    "la cadence OBD (~0,1-0,2 s), affinée par interpolation. Route privée ou circuit.",
                    style = MaterialTheme.typography.labelSmall, color = D.textLo)
            }

            // --- G-metre inertiel (offset zero persiste)
            Column(Modifier.weight(1f).fillMaxHeight()
                .background(D.slate, Plate(10.dp)).padding(14.dp)) {
                GMeterPanel(
                    Modifier.fillMaxSize(),
                    offLat = settings.gOffLat.toFloat(),
                    offLon = settings.gOffLon.toFloat(),
                    onZero = { la, lo ->
                        scope.launch {
                            repo.setDouble(SettingsRepo.GOX, la.toDouble())
                            repo.setDouble(SettingsRepo.GOY, lo.toDouble())
                        }
                    }
                )
            }
        }
    }
}

/**
 * Vitesses des 4 roues lues dans l'ABS (740/760, base DDT) + angle volant.
 * Detection de patinage : roue qui s'ecarte de plus de 2 km/h de la mediane -> orange.
 * Collecte du flux ICI (pas dans la page) : seules ces tuiles recomposent au rythme OBD.
 */
@Composable
private fun WheelsRow() {
    val state by ObdRepository.state.collectAsState()
    val ws = state.wheels
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val labels = listOf("AVG", "AVD", "ARG", "ARD")
        val median = ws?.sorted()?.let { (it[1] + it[2]) / 2.0 }
        labels.forEachIndexed { i, lab ->
            val v = ws?.getOrNull(i)
            val slip = v != null && median != null && kotlin.math.abs(v - median) > 2.0
            Column(Modifier.weight(1f).background(D.slate.copy(alpha = 0.7f), Plate(7.dp))
                .let { if (slip) it.edgeAccent(D.warn, 2.dp) else it }
                .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(v?.let { "%.1f".format(it) } ?: "--",
                    style = D.readout(15, FontWeight.SemiBold, if (slip) D.warn else D.textHi))
                Text(lab, style = D.eyebrow.copy(fontSize = 9.sp))
            }
        }
        Column(Modifier.weight(1.15f).background(D.slate.copy(alpha = 0.7f), Plate(7.dp))
            .padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.steerDeg?.let { "%+.0f°".format(it) } ?: "--",
                style = D.readout(15, FontWeight.SemiBold, D.textHi))
            Text("VOLANT", style = D.eyebrow.copy(fontSize = 9.sp))
        }
    }
}

@Composable
private fun PerfRow(label: String, value: Double?, best: Double?, suffix: String = "") {
    val beaten = value != null && best != null && best > 0 && value <= best
    Box(Modifier.fillMaxWidth().padding(vertical = 3.dp)
        .background(D.slate.copy(alpha = 0.6f), Plate(8.dp))
        .let { if (beaten) it.edgeAccent(D.mint) else it }
        .padding(horizontal = 13.dp, vertical = 11.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(label.uppercase(), style = D.eyebrow)
                if (best != null && best > 0.0) {
                    Text("RECORD %.2f s".format(best),
                        style = D.eyebrow.copy(color = D.mint, fontSize = 10.sp))
                }
            }
            Text((value?.let { "%.2f".format(it) } ?: "--") + (if (value != null) " s$suffix" else ""),
                style = D.readout(22, FontWeight.Bold, if (beaten) D.mint else D.textHi))
        }
    }
}
