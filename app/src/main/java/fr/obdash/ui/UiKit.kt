package fr.obdash.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Composants d'interface partages, pour un rendu coherent sur tous les ecrans. */

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Liseré d'accent + titre : signature d'en-tete
        Box(Modifier.width(3.dp).height(34.dp).background(D.accent))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), fontFamily = D.display, fontSize = 21.sp,
                fontWeight = FontWeight.Bold, color = D.textHi, letterSpacing = 1.sp)
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(subtitle, style = D.eyebrow)
            }
        }
        trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = D.eyebrow, color = D.accent,
        modifier = modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier.fillMaxWidth()
            .border(1.dp, D.hairline, Plate(9.dp))
            .background(D.slate, Plate(9.dp))
            .padding(14.dp)
    ) { Column(content = content) }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier, enabled = enabled,
        shape = Blade(9.dp),
        colors = ButtonDefaults.buttonColors(containerColor = D.accent, contentColor = D.ink,
            disabledContainerColor = D.slateHi, disabledContentColor = D.textLo)
    ) {
        Text(text.uppercase(), style = D.eyebrow.copy(
            color = if (enabled) D.ink else D.textLo, fontWeight = FontWeight.Bold))
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier, enabled = enabled,
        shape = Blade(9.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = D.textHi)
    ) {
        Text(text.uppercase(), style = D.eyebrow.copy(
            color = if (enabled) D.textHi else D.textLo, fontWeight = FontWeight.SemiBold))
    }
}

/** Barre d'activite indeterminee affichee pendant les operations longues (lecture DTC...). */
@Composable
fun BusyBar(label: String?) {
    AnimatedVisibility(visible = label != null, enter = fadeIn(), exit = fadeOut()) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text((label ?: "").uppercase(), style = D.eyebrow, color = D.accent)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = D.accent, trackColor = D.slateHi
            )
        }
    }
}

/** Tag d'etat : lame biseautee + liseré lateral, facon marquage technique. */
/**
 * Wordmark OBDash : picto jauge (arc 270° + aiguille, comme l'icone de l'app) + lettrage
 * Saira espace. Purement vectoriel, teinte par l'accent — utilise dans la sequence
 * d'allumage et le pied de page des Reglages.
 */
@Composable
fun LogoMark(size: androidx.compose.ui.unit.Dp = 26.dp, withText: Boolean = true,
             tint: androidx.compose.ui.graphics.Color = D.accent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val w = this.size.width; val c = Offset(w / 2f, w / 2f)
            val r = w * 0.40f
            drawArc(tint, 135f, 270f, false, Offset(w * 0.10f, w * 0.10f),
                Size(w * 0.80f, w * 0.80f),
                style = Stroke(w * 0.10f, cap = StrokeCap.Round))
            val a = Math.toRadians(-45.0)
            drawLine(D.textHi, c,
                Offset(c.x + (r * 0.78f * kotlin.math.cos(a)).toFloat(),
                       c.y + (r * 0.78f * kotlin.math.sin(a)).toFloat()),
                w * 0.08f, StrokeCap.Round)
            drawCircle(tint, w * 0.07f, c)
        }
        if (withText) {
            Spacer(Modifier.width(9.dp))
            Text("OBDASH", fontFamily = D.display, fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.62f).sp, color = D.textHi, letterSpacing = 2.5.sp)
        }
    }
}

@Composable
fun StatusPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(Modifier
        .background(color.copy(alpha = 0.12f), Blade(6.dp))
        .edgeAccent(color, 2.dp)
        .padding(start = 8.dp, end = 10.dp, top = 3.dp, bottom = 3.dp)) {
        Text(text.uppercase(), style = D.eyebrow.copy(color = color, fontWeight = FontWeight.Bold))
    }
}
