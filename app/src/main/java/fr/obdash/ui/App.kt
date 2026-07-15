package fr.obdash.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.obdash.core.ObdRepository
import fr.obdash.core.Settings
import fr.obdash.core.SettingsRepo
import kotlinx.coroutines.delay
import java.util.Calendar

private data class Tab(val label: String)

/**
 * Pictogrammes maison du rail : dernier element "stock" remplace. Chaque glyphe est dessine
 * au trait (2 dp, bouts francs) dans le langage de l'app — jauge, losange diagnostic, eclair,
 * trace de route, triangle, terminal, engrenage. Purement vectoriel, aucun asset.
 */
@Composable
private fun TabGlyph(index: Int, tint: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height
        val sw = 2.dp.toPx()
        val stroke = Stroke(sw, cap = StrokeCap.Butt)
        val c = Offset(w / 2f, h / 2f)
        when (index) {
            0 -> { // jauge : arc 270 + aiguille
                drawArc(tint, 135f, 270f, false, Offset(w * .12f, h * .12f),
                    Size(w * .76f, h * .76f), style = stroke)
                drawLine(tint, c, Offset(w * .72f, h * .30f), sw, StrokeCap.Butt)
                drawCircle(tint, sw * .9f, c)
            }
            1 -> { // losange diagnostic
                val p = Path().apply {
                    moveTo(c.x, h * .12f); lineTo(w * .88f, c.y)
                    lineTo(c.x, h * .88f); lineTo(w * .12f, c.y); close()
                }
                drawPath(p, tint, style = stroke)
                drawLine(tint, Offset(w * .34f, c.y), Offset(w * .66f, c.y), sw)
            }
            2 -> { // eclair (actionneurs)
                val p = Path().apply {
                    moveTo(w * .58f, h * .10f); lineTo(w * .28f, h * .56f)
                    lineTo(w * .48f, h * .56f); lineTo(w * .42f, h * .90f)
                    lineTo(w * .74f, h * .42f); lineTo(w * .52f, h * .42f); close()
                }
                drawPath(p, tint)
            }
            3 -> { // trace de route + points depart/arrivee
                val p = Path().apply {
                    moveTo(w * .20f, h * .84f)
                    cubicTo(w * .10f, h * .48f, w * .62f, h * .62f, w * .58f, h * .36f)
                    cubicTo(w * .56f, h * .22f, w * .74f, h * .18f, w * .82f, h * .18f)
                }
                drawPath(p, tint, style = stroke)
                drawCircle(tint, sw, Offset(w * .20f, h * .84f))
                drawCircle(tint, sw, Offset(w * .82f, h * .18f))
            }
            4 -> { // triangle DTC + point
                val p = Path().apply {
                    moveTo(c.x, h * .14f); lineTo(w * .88f, h * .82f)
                    lineTo(w * .12f, h * .82f); close()
                }
                drawPath(p, tint, style = stroke)
                drawLine(tint, Offset(c.x, h * .40f), Offset(c.x, h * .60f), sw)
                drawCircle(tint, sw * .8f, Offset(c.x, h * .71f))
            }
            5 -> { // terminal : chevron + underscore
                drawLine(tint, Offset(w * .16f, h * .30f), Offset(w * .42f, c.y), sw)
                drawLine(tint, Offset(w * .42f, c.y), Offset(w * .16f, h * .70f), sw)
                drawLine(tint, Offset(w * .52f, h * .74f), Offset(w * .84f, h * .74f), sw)
            }
            else -> { // engrenage : cercle + 6 crans + moyeu
                drawCircle(tint, w * .26f, c, style = stroke)
                for (k in 0 until 6) {
                    val a = Math.toRadians(k * 60.0)
                    val r1 = w * .34f; val r2 = w * .45f
                    drawLine(tint,
                        Offset(c.x + (r1 * kotlin.math.cos(a)).toFloat(), c.y + (r1 * kotlin.math.sin(a)).toFloat()),
                        Offset(c.x + (r2 * kotlin.math.cos(a)).toFloat(), c.y + (r2 * kotlin.math.sin(a)).toFloat()),
                        sw, StrokeCap.Butt)
                }
                drawCircle(tint, sw * .8f, c)
            }
        }
    }
}

/** Joue la sequence d'allumage une seule fois par lancement du process. */
private var startupPlayed = false

@Composable
fun AppRoot(onRequestOverlayPermission: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepo(ctx) }
    val settings by repo.flow.collectAsStateWithLifecycle(initialValue = Settings())
    val busy by ObdRepository.busy.collectAsStateWithLifecycle()
    val alerts by ObdRepository.alerts.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    var hour by remember { mutableStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    LaunchedEffect(settings.nightMode) {
        while (true) { hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); delay(60_000) }
    }
    val nightActive = settings.nightMode == 1 || (settings.nightMode == 2 && (hour >= 21 || hour < 7))
    LaunchedEffect(settings.lightTheme) { D.lightMode = settings.lightTheme }
    LaunchedEffect(settings.accentIdx) { D.accentIdx = settings.accentIdx }

    val tabs = listOf(
        Tab("Bord"),
        Tab("Renault"),
        Tab("Actions"),
        Tab("Trajets"),
        Tab("DTC"),
        Tab("Console"),
        Tab("Réglages")
    )

    var booting by remember { mutableStateOf(!startupPlayed) }
    LaunchedEffect(settings.bootAnim) {
        if (!settings.bootAnim && booting) { startupPlayed = true; booting = false }
    }

    DashTheme {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = D.carbon,
                    modifier = Modifier.width(88.dp)
                ) {
                    tabs.forEachIndexed { i, t ->
                        NavigationRailItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { TabGlyph(i, if (tab == i) D.ink else D.textLo) },
                            label = { Text(t.label, style = D.eyebrow) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = D.ink,
                                selectedTextColor = D.accent,
                                unselectedIconColor = D.textLo,
                                unselectedTextColor = D.textLo,
                                indicatorColor = D.accent
                            )
                        )
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    BusyBar(busy)
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            // Glisse laterale discrete dans le sens de la navigation + fondu
                            val dir = if (targetState > initialState) 1 else -1
                            (fadeIn(tween(170)) + slideInHorizontally(tween(170)) { dir * it / 22 }) togetherWith
                                (fadeOut(tween(90)) + slideOutHorizontally(tween(90)) { -dir * it / 26 })
                        },
                        modifier = Modifier.weight(1f),
                        label = "tab"
                    ) { t ->
                        Box(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                            when (t) {
                                0 -> DashboardScreen(settings, repo, onRequestOverlayPermission)
                                1 -> RenaultScreen()
                                2 -> BodyScreen()
                                3 -> TripsScreen()
                                4 -> DtcScreen()
                                5 -> TerminalScreen()
                                else -> SettingsScreen(repo, settings)
                            }
                        }
                    }
                }
            }
            // Alerte critique : lisere rouge pulsant autour de TOUT l'ecran, quel que soit
            // l'onglet — compose uniquement quand une alerte est active (cout nul sinon)
            if (alerts.isNotEmpty()) {
                val t = rememberInfiniteTransition(label = "alert")
                val a by t.animateFloat(0.25f, 0.85f,
                    infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
                    label = "alertA")
                Box(Modifier.fillMaxSize()
                    .border(3.dp, D.redline.copy(alpha = a)))
            }
            if (nightActive) {
                Box(Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = settings.nightDim.toFloat().coerceIn(0f, 0.75f))))
            }
            if (booting) {
                StartupSequence { startupPlayed = true; booting = false }
            }
        }
    }
}

