package fr.obdash.ui

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Coins biseautes (chamfer) : la signature formelle du projet.
 * Les plaques de carbone, les badges de course et les afficheurs de competition sont coupes
 * en diagonale, jamais arrondis. C'est ce qui separe une interface motorsport d'une app generique.
 *
 * @param tl,tr,br,bl active le biseau sur chaque coin (les coins inactifs restent droits)
 */
class ChamferShape(
    private val cut: Dp,
    private val tl: Boolean = true,
    private val tr: Boolean = true,
    private val br: Boolean = true,
    private val bl: Boolean = true
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(size.minDimension / 2f)
        val p = Path().apply {
            if (tl) { moveTo(0f, c); lineTo(c, 0f) } else moveTo(0f, 0f)
            if (tr) { lineTo(size.width - c, 0f); lineTo(size.width, c) } else lineTo(size.width, 0f)
            if (br) { lineTo(size.width, size.height - c); lineTo(size.width - c, size.height) }
            else lineTo(size.width, size.height)
            if (bl) { lineTo(c, size.height); lineTo(0f, size.height - c) } else lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(p)
    }
}

/** Plaque technique : les 4 coins coupes. Pour les cartes et panneaux. */
fun Plate(cut: Dp = 10.dp) = ChamferShape(cut)

/** Lame : deux coins opposes coupes -> lecture dynamique. Pour boutons, tuiles actives, tags. */
fun Blade(cut: Dp = 10.dp) = ChamferShape(cut, tl = false, tr = true, br = false, bl = true)

/** Hachures diagonales facon zone technique / bande de danger. */
fun Modifier.hazardStripes(color: Color, spacing: Dp = 8.dp, alpha: Float = 0.10f) = drawBehind {
    val step = spacing.toPx()
    clipRect {
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color.copy(alpha = alpha),
                Offset(x, size.height), Offset(x + size.height, 0f),
                strokeWidth = step * 0.42f, cap = StrokeCap.Butt
            )
            x += step * 2
        }
    }
}

/** Bandeau d'accent lateral (3 px) : marque le bloc actif, facon liseré de course. */
fun Modifier.edgeAccent(color: Color, width: Dp = 3.dp) = drawBehind {
    drawRect(color, Offset.Zero, Size(width.toPx(), size.height))
}

/**
 * Grain de carbone : trame diagonale croisee tres basse intensite. On ne la "voit" pas,
 * on la sent — c'est ce qui separe un fond plat d'une surface matiere.
 */
fun Modifier.carbonWeave(alpha: Float = 0.022f, step: Dp = 6.dp) = drawBehind {
    val s = step.toPx()
    // Le grain doit contraster avec le fond : blanc sur sombre, noir sur clair
    val c = (if (D.lightMode) Color.Black else Color.White).copy(alpha = alpha)
    var x = -size.height
    while (x < size.width + size.height) {
        drawLine(c, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1f)
        drawLine(c, Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 1f)
        x += s
    }
}

/** Vignette : assombrit les bords, concentre le regard au centre. Effet optique, pas decoratif. */
fun Modifier.vignette(strength: Float = 0.55f) = drawBehind {
    drawRect(
        androidx.compose.ui.graphics.Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = strength)),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.maxDimension * 0.75f
        )
    )
}
