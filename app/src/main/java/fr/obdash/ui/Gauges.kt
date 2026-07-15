package fr.obdash.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Alias retro-compatibles (getters -> reactifs au theme clair/sombre)
val ColDim get() = D.textLo
val ColAccent get() = D.accent
val ColCard get() = D.slate

private const val START = 135f
private const val SWEEP = 270f

/**
 * Compte-tours signature : cadran gradue, aiguille avec leger rebond mecanique,
 * zone rouge qui pulse a l'approche, et balayage d'allumage (self-test) au branchement
 * — comme un vrai cluster qui balaie ses aiguilles a la mise du contact.
 */
@Composable
fun Tachometer(
    rpm: Int?,
    connected: Boolean,
    maxRpm: Float = 7000f,
    redline: Float = 5600f,
    modifier: Modifier = Modifier
) {
    // Balayage d'allumage une fois a la connexion
    var sweepDone by remember { mutableStateOf(false) }
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(connected) {
        if (connected) {
            sweepDone = false
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(680, easing = FastOutSlowInEasing))
            sweep.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
            sweepDone = true
        } else sweepDone = false
    }

    val live by animateFloatAsState(
        targetValue = (rpm ?: 0).toFloat(),
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 170f),
        label = "rpm"
    )
    val value = if (connected && !sweepDone) sweep.value * maxRpm else live
    val inRedline = value >= redline

    // Pulsation de la zone rouge
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.18f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val needleColor by animateColorAsState(
        if (inRedline) D.redline else D.accent, tween(200), label = "needle"
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.085f
            val inset = stroke / 2f + size.minDimension * 0.02f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = (size.minDimension - inset * 2) / 2f

            // Piste de fond
            drawArc(D.accentTrack, START, SWEEP, false, topLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))

            // Zone rouge (indice permanent + halo quand on y est)
            val redFrac = (redline / maxRpm)
            val redStart = START + SWEEP * redFrac
            drawArc(D.redline.copy(alpha = if (inRedline) glow else 0.22f),
                redStart, SWEEP * (1 - redFrac), false, topLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))

            // Balayage actif : halo puis trait net
            val frac = (value / maxRpm).coerceIn(0f, 1f)
            drawArc(needleColor.copy(alpha = 0.16f), START, SWEEP * frac, false,
                Offset(inset, inset), arcSize, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
            drawArc(needleColor, START, SWEEP * frac, false, topLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))

            // Graduations
            val nTicks = (maxRpm / 1000f).toInt()
            for (i in 0..nTicks) {
                val a = Math.toRadians((START + SWEEP * (i / nTicks.toFloat())).toDouble())
                val rOut = radius - stroke * 0.7f
                val rIn = rOut - size.minDimension * 0.05f
                val major = D.textLo
                drawLine(
                    major,
                    Offset(cx + (rIn) * cos(a).toFloat(), cy + (rIn) * sin(a).toFloat()),
                    Offset(cx + (rOut) * cos(a).toFloat(), cy + (rOut) * sin(a).toFloat()),
                    strokeWidth = size.minDimension * 0.006f, cap = StrokeCap.Round
                )
            }

            // Aiguille (trait effile + contrepoids) via rotation autour du centre
            val needleAngle = START + SWEEP * frac
            rotate(needleAngle, pivot = Offset(cx, cy)) {
                val len = radius - stroke * 1.1f
                drawLine(
                    needleColor,
                    Offset(cx - radius * 0.12f, cy),
                    Offset(cx + len, cy),
                    strokeWidth = size.minDimension * 0.014f, cap = StrokeCap.Round
                )
            }
            // Halo + moyeu central
            drawCircle(needleColor.copy(alpha = 0.18f), radius = size.minDimension * 0.09f,
                center = Offset(cx, cy))
            drawCircle(D.slateHi, radius = size.minDimension * 0.055f, center = Offset(cx, cy))
            drawCircle(needleColor, radius = size.minDimension * 0.018f, center = Offset(cx, cy))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(1.dp))
            Text(if (rpm != null) "$rpm" else "----", style = D.readout(30, FontWeight.Bold))
            Text("TR/MIN", style = D.eyebrow)
        }
    }
}

/** Grand afficheur numerique anime (vitesse), figures tabulaires. */
@Composable
fun BigReadout(value: Int?, unit: String, color: Color = D.ice, size: Int = 78) {
    val anim by animateFloatAsState(
        targetValue = (value ?: 0).toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f),
        label = "big"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (value != null) "${anim.toInt()}" else "--",
            style = D.readout(size, FontWeight.Bold, color)
        )
        Text(unit, style = D.eyebrow)
    }
}

/** Petite tuile instrument : etiquette en capitales + valeur tabulaire, filet fin. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Box(
        modifier
            .border(1.dp, D.hairline, Plate(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Text(label.uppercase(), style = D.eyebrow)
            Spacer(Modifier.height(5.dp))
            Text(value, style = D.readout(19, FontWeight.SemiBold,
                if (accent) D.accent else D.textHi))
        }
    }
}

/** Barre horizontale animee (0..1), pour trims/charge. */
@Composable
fun BarMeter(fraction: Float, color: Color = D.accent, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(260), label = "bar")
    Canvas(modifier.height(8.dp)) {
        val r = size.height / 2f
        drawRoundRect(D.accentTrack, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
            size = size)
        if (f > 0f) drawRoundRect(color, size = Size(size.width * f, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
    }
}

/** Courbe de conso avec aire degradee et point de tete. */
@Composable
fun Sparkline(data: List<Float>, modifier: Modifier = Modifier, color: Color = D.accent) {
    Canvas(modifier) {
        if (data.size < 2) return@Canvas
        val mn = (data.min()).coerceAtMost(0f)
        val mx = (data.max()).coerceAtLeast(mn + 0.1f)
        val dx = size.width / (data.size - 1)
        fun y(v: Float) = size.height - (v - mn) / (mx - mn) * size.height
        val line = androidx.compose.ui.graphics.Path()
        val area = androidx.compose.ui.graphics.Path()
        line.moveTo(0f, y(data[0])); area.moveTo(0f, size.height); area.lineTo(0f, y(data[0]))
        for (i in 1 until data.size) {
            line.lineTo(i * dx, y(data[i])); area.lineTo(i * dx, y(data[i]))
        }
        area.lineTo(size.width, size.height); area.close()
        drawPath(area, androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
        val lx = size.width; val ly = y(data.last())
        drawCircle(color, radius = 3.dp.toPx(), center = Offset(lx - 1.dp.toPx(), ly))
    }
}

/**
 * Anneau de vitesse "cockpit numerique" : arc fin gradue, chiffre geant tabulaire au centre.
 * Pas d'aiguille -> lecture moderne et epuree. Balayage d'allumage partage avec le tachy.
 */
@Composable
fun SpeedRing(
    speed: Int?,
    connected: Boolean,
    accent: Color,
    maxKmh: Float = 200f,
    peakKmh: Int = 0,
    modifier: Modifier = Modifier
) {
    var sweepDone by remember { mutableStateOf(false) }
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(connected) {
        if (connected) {
            sweepDone = false; sweep.snapTo(0f)
            sweep.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            sweep.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
            sweepDone = true
        } else sweepDone = false
    }
    val live by animateFloatAsState(
        (speed ?: 0).toFloat(),
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 200f), label = "spd"
    )
    val value = if (connected && !sweepDone) sweep.value * maxKmh else live
    val accentAnim by animateColorAsState(accent, tween(300), label = "acc")

    val textMeasurer = rememberTextMeasurer()
    Box(modifier, contentAlignment = Alignment.Center) {
        // COUCHE STATIQUE (drawWithCache) : lentille, arc de fond, graduations chiffrees,
        // reflet. Construite UNE fois par taille/theme/echelle — les mesures de texte ne
        // tournent plus a chaque frame d'animation. Gain net sur head unit modeste.
        val gradColor = D.textLo; val tickHi = D.textHi; val bgArc = D.slateHi
        Spacer(Modifier.fillMaxSize().drawWithCache {
            val cx = size.width / 2f; val cy = size.height / 2f
            val stroke = size.minDimension * 0.05f
            val inset = stroke / 2f + size.minDimension * 0.08f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            val radius = (size.minDimension - inset * 2) / 2f
            val n = (maxKmh / 20f).toInt()
            data class Tick(val p1: Offset, val p2: Offset, val major: Boolean,
                val txt: androidx.compose.ui.text.TextLayoutResult?, val txtTl: Offset)
            val ticks = (0..n).map { i ->
                val a = Math.toRadians((START + SWEEP * (i / n.toFloat())).toDouble())
                val major = i % 2 == 0
                val rOut = radius - stroke * 1.15f
                val rIn = rOut - size.minDimension * (if (major) 0.048f else 0.026f)
                var res: androidx.compose.ui.text.TextLayoutResult? = null
                var tOff = Offset.Zero
                if (major) {
                    val rTxt = rIn - size.minDimension * 0.055f
                    res = textMeasurer.measure((i * 20).toString(), D.readout(
                        (size.minDimension * 0.036f).toInt().coerceIn(9, 15),
                        FontWeight.Medium, gradColor))
                    tOff = Offset(cx + rTxt * cos(a).toFloat() - res.size.width / 2f,
                        cy + rTxt * sin(a).toFloat() - res.size.height / 2f)
                }
                Tick(Offset(cx + rIn * cos(a).toFloat(), cy + rIn * sin(a).toFloat()),
                    Offset(cx + rOut * cos(a).toFloat(), cy + rOut * sin(a).toFloat()), major, res, tOff)
            }
            onDrawBehind {
                drawCircle(bgArc, radius + inset * 0.5f, Offset(cx, cy), style = Stroke(1f))
                drawArc(bgArc, START, SWEEP, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                ticks.forEach { t ->
                    drawLine(if (t.major) tickHi.copy(alpha = 0.9f) else gradColor.copy(alpha = 0.4f),
                        t.p1, t.p2,
                        strokeWidth = if (t.major) size.minDimension * 0.009f else size.minDimension * 0.004f,
                        cap = StrokeCap.Butt)
                    t.txt?.let { drawText(it, topLeft = t.txtTl) }
                }
                drawArc(tickHi.copy(alpha = 0.05f), 205f, 55f, false, tl, arcSize,
                    style = Stroke(stroke * 0.5f, cap = StrokeCap.Round))
            }
        })
        // COUCHE DYNAMIQUE : halo, arcs de progression, marqueur de pic
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f; val cy = size.height / 2f
            val stroke = size.minDimension * 0.05f
            val inset = stroke / 2f + size.minDimension * 0.08f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            val radius = (size.minDimension - inset * 2) / 2f

            for (i in 6 downTo 1) {
                drawCircle(accentAnim.copy(alpha = 0.03f * i), radius * (0.72f + i * 0.03f), Offset(cx, cy))
            }
            val frac = (value / maxKmh).coerceIn(0f, 1f)
            // Degrade balaye : l'arc s'eclaircit vers sa pointe — profondeur sans surcharge.
            // Le brush est aligne sur l'origine de l'arc (140 deg) via rotation du repere.
            val sweepBrush = Brush.sweepGradient(
                0f to accentAnim.copy(alpha = 0.45f),
                (SWEEP / 360f) to accentAnim,
                1f to accentAnim.copy(alpha = 0.45f),
                center = Offset(cx, cy)
            )
            rotate(START, pivot = Offset(cx, cy)) {
                drawArc(accentAnim.copy(alpha = 0.18f), 0f, SWEEP * frac, false, tl, arcSize,
                    style = Stroke(stroke * 2.4f, cap = StrokeCap.Butt))
                drawArc(sweepBrush, 0f, SWEEP * frac, false, tl, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Butt))
            }

            // marqueur du pic de session : trace fin, memoire de l'instrument
            if (peakKmh > 0) {
                val pa = Math.toRadians((START + SWEEP * (peakKmh / maxKmh).coerceIn(0f, 1f)).toDouble())
                val rO = radius + stroke * 0.15f
                val rI = rO - stroke * 1.5f
                drawLine(D.redline,
                    Offset(cx + rI * cos(pa).toFloat(), cy + rI * sin(pa).toFloat()),
                    Offset(cx + rO * cos(pa).toFloat(), cy + rO * sin(pa).toFloat()),
                    strokeWidth = size.minDimension * 0.011f, cap = StrokeCap.Butt)
            }

        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (speed != null) "${value.toInt()}" else "--",
                style = D.readout(96, FontWeight.Bold, D.ice))
            Text("KM/H", style = D.eyebrow.copy(letterSpacing = 4.sp))
        }
    }
}

/**
 * Barre de regime type "shift-light".
 * Le degrade est CONTINU (jaune -> orange -> rouge, interpolation le long de la barre) au lieu
 * de paliers durs : plus doux a l'oeil, la montee en regime se lit comme une rampe thermique.
 * Les segments restent carres (identite instrument) et une echelle chiffree (x1000 tr) est
 * gravee sous la barre. Strobe en zone rouge debrayable.
 */
@Composable
fun RpmBar(
    rpm: Int?,
    maxRpm: Float,
    accent: Color,
    redlineFrac: Float = 0.86f,
    strobe: Boolean = true,
    showScale: Boolean = true,
    modifier: Modifier = Modifier
) {
    val frac by animateFloatAsState(((rpm ?: 0) / maxRpm).coerceIn(0f, 1f), tween(120), label = "rpm")
    val acc by animateColorAsState(accent, tween(300), label = "rpmacc")
    // Strobe : compose uniquement en zone rouge ET si active (aucun cout sinon)
    val inRedline = frac >= redlineFrac
    val flash = if (inRedline && strobe) {
        val t = rememberInfiniteTransition(label = "strobe")
        t.animateFloat(0.30f, 1f,
            infiniteRepeatable(tween(90, easing = LinearEasing), RepeatMode.Reverse),
            label = "flash").value
    } else 1f
    val textMeasurer = rememberTextMeasurer()
    val scaleLo = D.textLo; val scaleRed = D.redline

    Box(modifier) {
        // Echelle gravee : statique (drawWithCache), mesuree une seule fois par taille/theme
        if (showScale) Spacer(Modifier.fillMaxSize().drawWithCache {
            val scaleH = 13.dp.toPx()
            val barH = size.height - scaleH
            val kMax = (maxRpm / 1000f).toInt()
            val marks = (1..kMax).map { k ->
                val fx = k * 1000f / maxRpm * size.width
                val red = k * 1000f / maxRpm >= redlineFrac
                Triple(fx, textMeasurer.measure("$k", D.readout(9, FontWeight.Medium,
                    if (red) scaleRed.copy(alpha = 0.9f) else scaleLo)), red)
            }
            val rx = redlineFrac * size.width
            onDrawBehind {
                marks.forEach { (fx, res, _) ->
                    drawLine(scaleLo.copy(alpha = 0.5f), Offset(fx, barH + 2f), Offset(fx, barH + 5f), 1f)
                    drawText(res, topLeft = Offset(fx - res.size.width / 2f, barH + 5f))
                }
                drawLine(scaleRed.copy(alpha = 0.8f), Offset(rx, barH + 1f), Offset(rx, barH + 7f), 2f)
            }
        })
        Canvas(Modifier.fillMaxSize()) {
        val scaleH = if (showScale) 13.dp.toPx() else 0f
        val barH = size.height - scaleH
        val segs = (size.width / 13.dp.toPx()).toInt().coerceIn(24, 72)
        val gap = 3f
        val sw = (size.width - (segs - 1) * gap) / segs

        // interpolation continue : jaune (0..0.55) -> orange (0.55..redline) -> rouge (redline..1)
        fun ramp(f: Float): Color = when {
            f < 0.55f -> acc
            f < redlineFrac -> lerp(D.warn, acc, ((redlineFrac - f) / (redlineFrac - 0.55f)).coerceIn(0f, 1f))
            else -> lerp(D.redline, D.warn, ((1f - f) / (1f - redlineFrac) * 0.35f).coerceIn(0f, 1f))
        }

        for (i in 0 until segs) {
            val f = i / segs.toFloat()
            val on = f <= frac
            val col = ramp(f)
            val x = i * (sw + gap)
            if (on && f >= redlineFrac) {
                drawRect(col.copy(alpha = 0.30f * flash), Offset(x - 1.5f, -3f), Size(sw + 3f, barH + 6f))
            }
            val segCol = if (on && f >= redlineFrac) col.copy(alpha = flash) else col
            drawRect(if (on) segCol else D.slateHi, Offset(x, 0f), Size(sw, barH))
        }

        }
    }
}

/** Petite jauge 3/4 stylisee (conso, température...) avec valeur centree. */
@Composable
fun ArcMini(
    value: Float?,
    max: Float,
    label: String,
    unit: String,
    arcColor: Color,
    decimals: Int = 1,
    modifier: Modifier = Modifier
) {
    val target = (value ?: 0f).coerceIn(0f, max)
    val anim by animateFloatAsState(target, tween(300), label = "arcmini")
    val col by animateColorAsState(arcColor, tween(300), label = "arcmicol")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.09f
            val inset = stroke / 2f + size.minDimension * 0.05f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(D.slateHi, START, SWEEP, false, tl, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
            val frac = (anim / max).coerceIn(0f, 1f)
            drawArc(col.copy(alpha = 0.18f), START, SWEEP * frac, false, tl, arcSize,
                style = Stroke(stroke * 2f, cap = StrokeCap.Round))
            drawArc(col, START, SWEEP * frac, false, tl, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value?.let { "%.${decimals}f".format(it) } ?: "--",
                style = D.readout(24, FontWeight.SemiBold, D.textHi))
            Text(unit, style = D.eyebrow)
            Spacer(Modifier.height(1.dp))
            Text(label.uppercase(), style = D.eyebrow)
        }
    }
}
