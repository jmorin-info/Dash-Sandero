package fr.obdash.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.rememberInfiniteTransition

/**
 * Conseil de passage de rapport, cale sur le B4D (1.0 SCe 73 : couple max ~3500,
 * puissance max ~6250 tr/min). Le seuil depend du mode de conduite — c'est ce qui rend
 * les modes actionnables au lieu d'etre une simple teinte.
 *
 * Éco    : monter tot (~2100) pour rester sous la courbe de conso
 * Normal : ~2700, compromis souplesse / reprise
 * Sport  : garder le regime haut (~5900), sortir la puissance
 * Retrograder si le moteur peine (< 1250 tr/min en charge).
 */
enum class ShiftAdvice { NONE, UP, DOWN }

data class ShiftHint(val advice: ShiftAdvice, val targetGear: Int?, val targetRpm: Int? = null)

/**
 * @param ratios tr/min par km/h pour les rapports 1..5 (reglable, defaut SCe 73).
 * Le regime cible apres changement = vitesse x ratio du rapport vise — c'est le rev-match :
 * au retrogradage, c'est le regime a atteindre au talon-pointe pour un passage sans a-coup.
 */
fun shiftHint(
    mode: DriveMode, rpm: Int?, gear: Int?, throttle: Double?, speed: Int?,
    ratios: List<Double> = listOf(110.0, 62.0, 43.0, 33.0, 27.0),
    redlineRpm: Int = 5600
): ShiftHint {
    if (rpm == null || gear == null || gear == 0 || speed == null || speed < 8)
        return ShiftHint(ShiftAdvice.NONE, null)

    val up = when (mode) {
        DriveMode.ECO -> 2100
        DriveMode.NORMAL -> 2700
        DriveMode.SPORT -> (redlineRpm + 300).coerceAtMost(redlineRpm + 500) // juste avant le strobe
    }
    val load = throttle ?: 0.0
    fun rpmIn(g: Int): Int? = ratios.getOrNull(g - 1)?.let { (speed * it).toInt() }

    if (rpm >= up && gear < 5) return ShiftHint(ShiftAdvice.UP, gear + 1, rpmIn(gear + 1))
    if (rpm <= 1250 && gear > 1 && load > 22) return ShiftHint(ShiftAdvice.DOWN, gear - 1, rpmIn(gear - 1))
    return ShiftHint(ShiftAdvice.NONE, null)
}

/** Bandeau de conseil : discret, mais impossible a manquer quand il compte. */
@Composable
fun ShiftBanner(hint: ShiftHint, accent: Color, modifier: Modifier = Modifier) {
    val show = hint.advice != ShiftAdvice.NONE

    AnimatedVisibility(
        visible = show, modifier = modifier,
        enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.9f),
        exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.94f)
    ) {
        val col = if (hint.advice == ShiftAdvice.UP) accent else D.azure
        // Pulse compose UNIQUEMENT quand la banniere est visible (aucun cout sinon)
        val t = rememberInfiniteTransition(label = "shift")
        val pulse by t.animateFloat(0.55f, 1f,
            infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "p")
        Box(Modifier
            .background(col.copy(alpha = 0.14f), Blade(9.dp))
            .edgeAccent(col)
            .alpha(pulse)
            .padding(start = 14.dp, end = 14.dp, top = 7.dp, bottom = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Text(if (hint.advice == ShiftAdvice.UP) "\u25B2" else "\u25BC",
                    style = D.readout(15, FontWeight.Bold, col))
                Spacer(Modifier.width(9.dp))
                Text(if (hint.advice == ShiftAdvice.UP) "PASSER" else "RÉTROGRADER",
                    style = D.eyebrow.copy(color = col, fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(9.dp))
                Text("${hint.targetGear}", style = D.readout(19, FontWeight.Bold, col))
                if (hint.targetRpm != null) {
                    Spacer(Modifier.width(9.dp))
                    Text("· ${hint.targetRpm / 50 * 50} TR",
                        style = D.eyebrow.copy(color = col.copy(alpha = 0.85f)))
                }
            }
        }
    }
}
