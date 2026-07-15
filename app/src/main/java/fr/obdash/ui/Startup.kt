package fr.obdash.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Sequence d'allumage : self-test facon combine d'instruments a la mise du contact.
 * L'anneau balaie sa pleine echelle, les segments de regime s'allument en cascade, le
 * nom s'inscrit, puis tout s'efface pour laisser place au cockpit. Joue une seule fois
 * par lancement de l'app (pas a chaque reconnexion).
 */
@Composable
fun StartupSequence(onDone: () -> Unit) {
    val sweep = remember { Animatable(0f) }      // balayage de l'anneau 0 -> 1 -> 0
    val cascade = remember { Animatable(0f) }    // remplissage de la barre de segments
    var showName by remember { mutableStateOf(false) }
    val fade = remember { Animatable(1f) }       // fondu de sortie de tout l'ecran

    LaunchedEffect(Unit) {
        cascade.animateTo(1f, tween(520, easing = LinearEasing))
        sweep.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        showName = true
        sweep.animateTo(0f, tween(560, easing = FastOutSlowInEasing))
        cascade.animateTo(0f, tween(260, easing = LinearEasing))
        delay(220)
        fade.animateTo(0f, tween(340))
        onDone()
    }

    Box(Modifier.fillMaxSize().background(D.deep).alpha(fade.value),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = size.minDimension * 0.05f
                    val inset = stroke / 2f + size.minDimension * 0.08f
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val tl = Offset(inset, inset)
                    val cx = size.width / 2f; val cy = size.height / 2f
                    val radius = (size.minDimension - inset * 2) / 2f

                    for (i in 6 downTo 1) {
                        drawCircle(D.accent.copy(alpha = 0.035f * i * sweep.value),
                            radius * (0.72f + i * 0.03f), Offset(cx, cy))
                    }
                    drawArc(D.slateHi, 135f, 270f, false, tl, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(D.accent.copy(alpha = 0.22f), 135f, 270f * sweep.value, false, tl, arcSize,
                        style = Stroke(stroke * 2.6f, cap = StrokeCap.Round))
                    drawArc(D.accent, 135f, 270f * sweep.value, false, tl, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
                if (showName) LogoMark(size = 34.dp)
                else Text("%.0f".format(sweep.value * 200),
                    style = D.readout(46, FontWeight.Bold, D.ice))
            }
            Spacer(Modifier.height(22.dp))
            // Barre de segments : cascade d'allumage facon shift-light
            Canvas(Modifier.size(260.dp, 10.dp)) {
                val segs = 30
                val gap = 3f
                val sw = (size.width - (segs - 1) * gap) / segs
                for (i in 0 until segs) {
                    val f = i / segs.toFloat()
                    val on = f <= cascade.value
                    val col = when {
                        f >= 0.86f -> D.redline
                        f >= 0.72f -> D.warn
                        else -> D.accent
                    }
                    drawRect(if (on) col else D.slateHi,
                        Offset(i * (sw + gap), 0f), Size(sw, size.height))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (showName) "SANDERO · 1.0 SCe · EMS3140" else "AUTOTEST",
                style = D.eyebrow.copy(letterSpacing = 3.sp)
            )
        }
    }
}
