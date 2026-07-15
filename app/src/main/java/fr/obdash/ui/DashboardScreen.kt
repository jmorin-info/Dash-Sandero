package fr.obdash.ui

import android.provider.Settings as SysSettings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.obdash.core.ObdRepository
import fr.obdash.core.Settings
import fr.obdash.core.SettingsRepo
import fr.obdash.core.VehicleState
import fr.obdash.service.ObdService
import fr.obdash.util.Waze
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    settings: Settings,
    repo: SettingsRepo,
    onOverlayPermission: () -> Unit
) {
    // Collecte ICI et pas dans AppRoot : seul le tableau de bord recompose au rythme OBD
    // (~15 Hz) ; le rail, les transitions et les autres ecrans restent au repos.
    val state by ObdRepository.state.collectAsState()
    val pager = rememberPagerState { 3 }
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> Cockpit(state, settings, repo, onOverlayPermission)
                1 -> PerformancePage(repo, settings)
                else -> RecorderPage()
            }
        }
        PageDots(pager.currentPage, 3)
    }
}

@Composable
private fun Cockpit(
    state: VehicleState, settings: Settings, repo: SettingsRepo, onOverlayPermission: () -> Unit
) {
    val alerts by ObdRepository.alerts.collectAsState()
    val conso by ObdRepository.consoHistory.collectAsState()
    val peaks by ObdRepository.peaks.collectAsState()
    val mode = DriveMode.of(settings.mode)
    val accent = mode.accent
    val scope = rememberCoroutineScope()
    var showCalib by remember { mutableStateOf(false) }
    var editLayout by remember { mutableStateOf(false) }

    if (showCalib) {
        CalibrationDialog(repo, settings, state.tripKm, state.tripL, scope) { showCalib = false }
    }

    BoxWithConstraints(Modifier.fillMaxSize()
        .let { if (settings.weave) it.carbonWeave() else it }.vignette()
        .padding(horizontal = 10.dp, vertical = 6.dp)) {
        val ring = (maxHeight * 0.50f).coerceAtMost(maxWidth * 0.30f)
        Column(Modifier.fillMaxSize()) {
            // ---- Bandeau haut : barre de regime + statut
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("RÉGIME", style = D.eyebrow)
                        Spacer(Modifier.width(10.dp))
                        Text("${state.rpm ?: "--"}", style = D.readout(20, FontWeight.SemiBold, D.textHi))
                        Text(" tr/min", style = D.eyebrow)
                    }
                    Spacer(Modifier.height(5.dp))
                    RpmBar(state.rpm, settings.rpmMax.toFloat(), accent,
                        redlineFrac = (settings.redlineRpm.toFloat() / settings.rpmMax).coerceIn(0.5f, 0.98f),
                        strobe = settings.strobe,
                        modifier = Modifier.fillMaxWidth().height(27.dp))
                }
                Spacer(Modifier.width(18.dp))
                StatusCluster(state)
            }
            Spacer(Modifier.height(10.dp))

            // ---- Zone principale : 3 colonnes
            Row(Modifier.fillMaxWidth().weight(1f)) {
                // Gauche : anneau vitesse + modes + actions
                Column(Modifier.weight(1.05f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(4.dp))
                    SpeedRing(state.speedKmh, state.connected, accent,
                        maxKmh = settings.ringMax.toFloat(),
                        peakKmh = peaks.maxSpeed, modifier = Modifier.size(ring))
                    Spacer(Modifier.height(6.dp))
                    ShiftBanner(
                        shiftHint(mode, state.rpm, state.gear, state.throttlePct, state.speedKmh,
                            remember(settings.gearRatios) {
                                settings.gearRatios.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                                    .ifEmpty { listOf(110.0, 62.0, 43.0, 33.0, 27.0) }
                            },
                            redlineRpm = settings.redlineRpm),
                        accent
                    )
                    Spacer(Modifier.height(6.dp))
                    ModeChips(mode, repo)
                    if (!state.connected) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (settings.sim) "MODE SIMULATION PRÊT — APPUIE SUR CONNEXION"
                            else "BRANCHE LE VLINKER, CONTACT MIS · OU ACTIVE LA SIMULATION (RÉGLAGES)",
                            style = D.eyebrow.copy(fontSize = 9.sp, color = D.textLo)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    ActionsRow(state, settings, onOverlayPermission)
                }

                Spacer(Modifier.width(6.dp))

                // Milieu : rapport + jauges + parametres
                Column(Modifier.weight(1.0f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RAPPORT", style = D.eyebrow)
                    // rapport anime : glisse verticale + fondu au changement, facon afficheur de boite
                    AnimatedContent(
                        targetState = state.gear,
                        transitionSpec = {
                            val upshift = (targetState ?: 0) >= (initialState ?: 0)
                            (slideInVertically(tween(160)) { if (upshift) it / 2 else -it / 2 } +
                                fadeIn(tween(160))) togetherWith
                                (slideOutVertically(tween(110)) { if (upshift) -it / 2 else it / 2 } +
                                    fadeOut(tween(110)))
                        }, label = "gear"
                    ) { g ->
                        Text(when (g) { null -> "-"; 0 -> "N"; else -> "$g" },
                            style = D.readout(72, FontWeight.Bold, if ((g ?: 0) > 0) accent else D.textLo))
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ArcMini(state.instL100?.toFloat() ?: state.instLph?.toFloat(),
                            if (state.instL100 != null) 20f else 8f,
                            if (state.instL100 != null) "L/100" else "L/h", "CONSO", accent, 1,
                            Modifier.size(96.dp))
                        ArcMini(state.coolantC?.toFloat(), 130f, "Eau", "°C",
                            tempColor(state.coolantC), 0, Modifier.size(96.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    VitalsGrid(state)
                    if (settings.showSparkline && conso.size > 2) {
                        Spacer(Modifier.height(10.dp))
                        Column(Modifier.fillMaxWidth()) {
                            Text("DÉBIT INSTANTANÉ · L/H", style = D.eyebrow)
                            Spacer(Modifier.height(4.dp))
                            Sparkline(conso, Modifier.fillMaxWidth().height(34.dp), accent)
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Droite : blocs reordonnables + alerte
                Column(Modifier.weight(0.98f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (editLayout) "GLISSER POUR RÉORGANISER" else "",
                            style = D.eyebrow.copy(color = accent))
                        Row {
                            Text("CALIBRER", style = D.eyebrow.copy(
                                color = D.textLo, fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.clickable { showCalib = true }.padding(4.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(if (editLayout) "OK" else "ORGANISER",
                                style = D.eyebrow.copy(
                                    color = if (editLayout) accent else D.textLo,
                                    fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.clickable { editLayout = !editLayout }.padding(4.dp))
                        }
                    }

                    val order = settings.cockpitOrder.split(",").filter { it.isNotBlank() }
                    order.forEachIndexed { idx, key ->
                        Box(Modifier.fillMaxWidth()) {
                            when (key) {
                                "conso" -> ConsoCard(state, accent, settings.tankL)
                                "trip" -> TripCard(state, accent)
                                "elec" -> ElecCard(state, settings.showVitalsExt, Modifier)
                            }
                            if (editLayout) MoveHandles(idx, order.size) { dir ->
                                val m = order.toMutableList()
                                val j = idx + dir
                                if (j in m.indices) {
                                    val tmp = m[idx]; m[idx] = m[j]; m[j] = tmp
                                    scope.launch { repo.setString(SettingsRepo.ORDER, m.joinToString(",")) }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.weight(1f))
                    if (settings.showPeaks) { PeakStrip(peaks); Spacer(Modifier.height(8.dp)) }
                    AlertChip(alerts)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun StatusCluster(state: VehicleState) {
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(state.connected)
            Spacer(Modifier.width(7.dp))
            Text(if (state.connected) state.protocol else "HORS LIGNE", style = D.eyebrow)
        }
        Spacer(Modifier.height(3.dp))
        Text(if (state.connected) "%.0f Hz".format(state.hz) else "--",
            style = D.readout(13, FontWeight.SemiBold, if (state.connected) D.mint else D.textLo))
        state.odoKm?.let { odo ->
            Spacer(Modifier.height(3.dp))
            // Odometre lu au tableau de bord (cluster 743/763) : le VRAI kilometrage affiche
            Text("ODO %,d KM".format(odo).replace(',', '\u202F'),
                style = D.eyebrow.copy(color = D.textLo))
        }
    }
}

@Composable
private fun LiveDot(connected: Boolean) {
    val a = if (connected) {
        val t = rememberInfiniteTransition(label = "dot")
        t.animateFloat(0.35f, 1f,
            infiniteRepeatable(tween(950, easing = LinearEasing), RepeatMode.Reverse),
            label = "a").value
    } else 1f
    Box(Modifier.size(9.dp).alpha(a)
        .background(if (connected) D.mint else D.redline, CircleShape))
}

@Composable
private fun ModeChips(current: DriveMode, repo: SettingsRepo) {
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DriveMode.entries.forEachIndexed { i, m ->
            val sel = m == current
            Box(Modifier
                .background(if (sel) m.accent else Color.Transparent, Blade(7.dp))
                .border(1.dp, if (sel) m.accent else D.hairline, Blade(7.dp))
                .clickable { scope.launch { repo.setMode(i) } }
                .padding(horizontal = 15.dp, vertical = 7.dp)) {
                Text(m.label.uppercase(),
                    style = D.eyebrow.copy(color = if (sel) D.ink else D.textLo,
                        fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun ActionsRow(state: VehicleState, settings: Settings, onOverlayPermission: () -> Unit) {
    val ctx = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!state.connected) {
            // Respiration douce du bouton quand rien n'est branche : l'oeil sait ou aller.
            // Transition composee UNIQUEMENT hors connexion (cout nul en roulant).
            val t = rememberInfiniteTransition(label = "cta")
            val a by t.animateFloat(0.62f, 1f,
                infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
                label = "ctaA")
            Box(Modifier.alpha(a)) {
                Pill("CONNEXION", filled = true) { ObdService.start(ctx, settings.sim) }
            }
        } else Pill("ARRÊT") { ObdService.stop(ctx) }
        Pill("OVERLAY") {
            if (!SysSettings.canDrawOverlays(ctx)) onOverlayPermission() else ObdService.toggleOverlay(ctx)
        }
        Pill("WAZE") { Waze.launch(ctx, false) }
    }
}

@Composable
private fun Pill(label: String, filled: Boolean = false, onClick: () -> Unit) {
    Box(Modifier
        .background(if (filled) D.accent else D.slate, Blade(8.dp))
        .border(1.dp, if (filled) D.accent else D.hairline, Blade(8.dp))
        .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 11.dp)) {
        Text(label, style = D.eyebrow.copy(
            color = if (filled) D.ink else D.textHi, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun VitalsGrid(state: VehicleState) {
    val cells = listOf(
        "AIR" to "${state.iatC ?: "--"}°", "MAP" to "${state.mapKpa ?: "--"}",
        "PAPILLON" to (state.throttlePct?.let { "%.0f%%".format(it) } ?: "--"),
        "CHARGE" to (state.loadPct?.let { "%.0f%%".format(it) } ?: "--"),
        "AVANCE" to (state.advanceDeg?.let { "%.0f°".format(it) } ?: "--"),
        "BARO" to "${state.baroKpa ?: "--"}"
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { i, (l, v) ->
                    if (i > 0) Box(Modifier.width(1.dp).height(34.dp).background(D.hairline))
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(l, style = D.eyebrow.copy(fontSize = 9.sp))
                        Text(v, style = D.readout(20, FontWeight.SemiBold, D.textHi))
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    accentEdge: Color? = null
) {
    Box(modifier.fillMaxWidth().background(D.slate, Plate(10.dp))
        .let { if (accentEdge != null) it.edgeAccent(accentEdge) else it }
        .padding(start = if (accentEdge != null) 16.dp else 14.dp, end = 14.dp,
            top = 14.dp, bottom = 14.dp)) {
        Column(content = content)
    }
}

@Composable
private fun ConsoCard(state: VehicleState, accent: Color, tankL: Double) {
    Card({
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("MOYENNE", style = D.eyebrow)
            Text(if (state.fuelInjection) "INJECTION" else "ESTIMÉE",
                style = D.eyebrow.copy(color = if (state.fuelInjection) D.mint else D.textLo))
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(state.avgL100?.let { "%.1f".format(it) } ?: "--",
                style = D.readout(34, FontWeight.Bold, accent))
            Text(" L/100 km", style = D.eyebrow)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(D.hairline))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("AUTONOMIE", style = D.eyebrow)
            Text(autonomy(state, tankL)?.let { "$it km" } ?: "--",
                style = D.readout(16, FontWeight.SemiBold, D.textHi))
        }
    }, accentEdge = accent)
}

@Composable
private fun TripCard(state: VehicleState, accent: Color) {
    Card({
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("TRAJET EN COURS", style = D.eyebrow)
            Text("CLÔTURER", style = D.eyebrow.copy(color = accent, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.clickable { ObdRepository.commands.trySend("TRIP_RESET") })
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%.1f".format(state.tripKm), style = D.readout(28, FontWeight.Bold, D.textHi))
            Text(" km", style = D.eyebrow)
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("%.2f L".format(state.tripL), style = D.readout(14, FontWeight.SemiBold, D.textHi))
            Text("%.2f €".format(state.tripCost), style = D.readout(14, FontWeight.SemiBold, accent))
        }
    })
}

@Composable
private fun ElecCard(state: VehicleState, extended: Boolean, modifier: Modifier) {
    Card({
        Text("ÉLECTRIQUE & MOTEUR", style = D.eyebrow)
        Spacer(Modifier.height(8.dp))
        ElecRow("Tension batterie", state.voltage?.let { "%.1f V".format(it) } ?: "--", D.accent)
        ElecRow("Tension calculateur", state.moduleV?.let { "%.1f V".format(it) } ?: "--", D.textHi)
        if (extended) {
            ElecRow("Correction STFT", state.stftPct?.let { "%+.0f %%".format(it) } ?: "--", D.textHi)
            ElecRow("Correction LTFT", state.ltftPct?.let { "%+.0f %%".format(it) } ?: "--", D.textHi)
            ElecRow("Uptime moteur",
                state.runtimeSec?.let { "%d:%02d".format(it / 60, it % 60) } ?: "--", D.textHi)
        }
    }, modifier)
}

@Composable
private fun ElecRow(label: String, value: String, col: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = D.textLo)
        Text(value, style = D.readout(15, FontWeight.SemiBold, col))
    }
}

@Composable
private fun PeakStrip(p: ObdRepository.Peaks) {
    Box(Modifier.fillMaxWidth().border(1.dp, D.hairline, Plate(8.dp))
        .padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PeakCell("VMAX", "${p.maxSpeed}")
            PeakCell("RPM", "${p.maxRpm}")
            PeakCell("EAU", "${p.maxCoolant}°")
            PeakCell("UMIN", if (p.minVolt > 0) "%.1f".format(p.minVolt) else "--")
        }
    }
}

@Composable
private fun PeakCell(l: String, v: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, style = D.readout(15, FontWeight.SemiBold, D.textHi))
        Text(l, style = D.eyebrow.copy(fontSize = 9.sp))
    }
}

@Composable
private fun AlertChip(alerts: List<String>) {
    val ok = alerts.isEmpty()
    val col = if (ok) D.mint else D.redline
    Box(Modifier.fillMaxWidth()
        .background(col.copy(alpha = 0.08f), Plate(8.dp))
        .let { if (!ok) it.hazardStripes(col, alpha = 0.14f) else it }
        .edgeAccent(col)
        .border(1.dp, col.copy(alpha = 0.45f), Plate(8.dp))
        .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
        Text(if (ok) "AUCUNE ALERTE" else alerts.joinToString("  ·  ").uppercase(),
            style = D.eyebrow.copy(color = col, fontWeight = FontWeight.Bold))
    }
}

/** Poignees de reorganisation : monter / descendre un bloc (fiable au doigt en voiture). */
@Composable
private fun MoveHandles(index: Int, count: Int, onMove: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.End) {
        if (index > 0) HandleBtn("\u25B2") { onMove(-1) }
        Spacer(Modifier.width(6.dp))
        if (index < count - 1) HandleBtn("\u25BC") { onMove(+1) }
    }
}

@Composable
private fun HandleBtn(glyph: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).background(D.accent, Plate(6.dp))
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(glyph, style = D.readout(12, FontWeight.Bold, D.ink))
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            Box(Modifier.padding(horizontal = 4.dp).size(if (i == current) 7.dp else 5.dp)
                .background(if (i == current) D.accent else D.textLo.copy(alpha = 0.4f), CircleShape))
        }
    }
}

private fun tempColor(t: Int?): Color = when {
    t == null -> D.textLo
    t < 70 -> D.azure
    t <= 104 -> D.mint
    else -> D.redline
}

private fun autonomy(s: VehicleState, tankL: Double): Int? {
    val avg = s.avgL100 ?: return null
    val pct = s.fuelPct ?: return null
    if (avg <= 0) return null
    return (tankL * pct / 100.0 / avg * 100).toInt()
}
