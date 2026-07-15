package fr.obdash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.obdash.R

/**
 * Direction : "Sirius" — esprit competition Renault Sport, mais discipline.
 *
 * Couleur : asphalte quasi noir, panneaux carbone, et le jaune Sirius employe comme un outil
 * chirurgical, jamais comme une teinture. Un seul element jaune par zone : la valeur qui compte.
 * L'orange et le rouge course prennent le relais pour l'alerte et le danger — la gradation
 * jaune -> orange -> rouge est celle d'un shift-light, elle porte du sens.
 *
 * Forme : coins BISEAUTES (voir Shapes.kt). Les plaques de carbone et les badges de course sont
 * coupes en diagonale ; c'est le detail qui distingue un afficheur de competition d'une app.
 *
 * Typo : Saira Condensed pour les etiquettes (condense + capitales espacees = marquage de
 * carrosserie), Chakra Petch pour les chiffres d'instrument (geometrie carree, figures tabulaires).
 * Le contraste de largeur entre les deux porte la hierarchie sans ajouter de couleur.
 */
private class Palette(
    val carbon: Color, val deep: Color, val slate: Color, val slateHi: Color, val hairline: Color,
    val accent: Color, val accentSoft: Color, val accentTrack: Color,
    val ice: Color, val mint: Color, val redline: Color, val azure: Color, val warn: Color,
    val textHi: Color, val textLo: Color, val ink: Color
)

private val DARK = Palette(
    carbon = Color(0xFF101013), deep = Color(0xFF0A0A0C), slate = Color(0xFF17171B),
    slateHi = Color(0xFF232328), hairline = Color(0x14FFFFFF),
    accent = Color(0xFFFFD100),                 // Jaune Sirius
    accentSoft = Color(0x2EFFD100), accentTrack = Color(0xFF2A2410),
    ice = Color(0xFFF4F4F2),                    // craie
    mint = Color(0xFF00D07A), redline = Color(0xFFE8112D),   // rouge course
    azure = Color(0xFF3C82F6), warn = Color(0xFFFF6A00),     // orange, distinct du jaune
    textHi = Color(0xFFF4F4F2), textLo = Color(0xFF83838C), ink = Color(0xFF141200)
)

private val LIGHT = Palette(
    carbon = Color(0xFFF2F2F0), deep = Color(0xFFE6E6E3), slate = Color(0xFFFFFFFF),
    slateHi = Color(0xFFE4E4E1), hairline = Color(0x1A101010),
    accent = Color(0xFF9A7A00),                 // Sirius assombri : lisible sur blanc
    accentSoft = Color(0x229A7A00), accentTrack = Color(0xFFE8E0C4),
    ice = Color(0xFF141416),
    mint = Color(0xFF00875A), redline = Color(0xFFC00021),
    azure = Color(0xFF1F5FD0), warn = Color(0xFFC24E00),
    textHi = Color(0xFF141416), textLo = Color(0xFF63636B), ink = Color(0xFFFFFFFF)
)

private val saira = FontFamily(
    Font(R.font.saira_medium, FontWeight.Medium),
    Font(R.font.saira_semibold, FontWeight.SemiBold),
    Font(R.font.saira_bold, FontWeight.Bold)
)
private val chakra = FontFamily(
    Font(R.font.chakra_regular, FontWeight.Normal),
    Font(R.font.chakra_medium, FontWeight.Medium),
    Font(R.font.chakra_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_bold, FontWeight.Bold)
)

/** Accents proposes a l'utilisateur : (nom, teinte sombre, teinte claire lisible sur blanc). */
data class AccentDef(val name: String, val dark: Color, val light: Color)
val ACCENTS = listOf(
    AccentDef("Sirius", Color(0xFFFFD100), Color(0xFF9A7A00)),
    AccentDef("Ion", Color(0xFF4CC7E6), Color(0xFF0E86A6)),
    AccentDef("Course", Color(0xFFFF4D5A), Color(0xFFC00021)),
    AccentDef("Circuit", Color(0xFF00D07A), Color(0xFF00875A)),
    AccentDef("Nuit", Color(0xFFB98BFF), Color(0xFF6C3FD1)),
    AccentDef("Cuivre", Color(0xFFFF9E45), Color(0xFFB65C00))
)

object D {
    var lightMode by mutableStateOf(false)
    var accentIdx by mutableStateOf(0)
    private fun p() = if (lightMode) LIGHT else DARK

    val carbon get() = p().carbon
    val deep get() = p().deep
    val slate get() = p().slate
    val slateHi get() = p().slateHi
    val hairline get() = p().hairline
    val accent get() = ACCENTS.getOrElse(accentIdx) { ACCENTS[0] }
        .let { if (lightMode) it.light else it.dark }
    val accentSoft get() = accent.copy(alpha = 0.18f)
    val accentTrack get() = androidx.compose.ui.graphics.lerp(carbon, accent, 0.16f)
    val ice get() = p().ice
    val mint get() = p().mint
    val redline get() = p().redline
    val azure get() = p().azure
    val warn get() = p().warn
    val textHi get() = p().textHi
    val textLo get() = p().textLo
    val ink get() = p().ink

    val bg: Brush get() = Brush.verticalGradient(listOf(carbon, deep))
    val display = saira      // etiquettes, titres, boutons
    val mono = chakra        // chiffres d'instrument

    /** Chiffre d'instrument : Chakra Petch, figures tabulaires (aucun tremblement de largeur). */
    fun readout(size: Int, weight: FontWeight = FontWeight.SemiBold, color: Color = textHi) =
        TextStyle(fontFamily = chakra, fontSize = size.sp, fontWeight = weight, color = color,
            fontFeatureSettings = "tnum", letterSpacing = (-0.5).sp)

    /** Etiquette : Saira Condensed, capitales espacees — marquage de carrosserie. */
    val eyebrow: TextStyle get() = TextStyle(
        fontFamily = saira, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.9.sp, color = textLo
    )
}

@Composable
fun DashTheme(content: @Composable () -> Unit) {
    val scheme = if (D.lightMode)
        lightColorScheme(primary = D.accent, onPrimary = D.ink, secondary = D.mint,
            background = D.deep, surface = D.slate, onSurface = D.textHi, error = D.redline)
    else
        darkColorScheme(primary = D.accent, onPrimary = D.ink, secondary = D.mint,
            background = D.deep, surface = D.slate, onSurface = D.textHi, error = D.redline)

    fun t(size: Int, w: FontWeight, c: Color, ls: Double = 0.0) =
        TextStyle(fontFamily = D.display, fontSize = size.sp, fontWeight = w, color = c, letterSpacing = ls.sp)
    val typo = Typography(
        titleLarge = t(22, FontWeight.Bold, D.textHi, 0.5),
        titleMedium = t(17, FontWeight.SemiBold, D.textHi, 0.3),
        titleSmall = t(14, FontWeight.SemiBold, D.textHi, 0.5),
        bodyMedium = t(14, FontWeight.Medium, D.textHi),
        bodySmall = t(13, FontWeight.Medium, D.textLo),
        labelMedium = D.eyebrow,
        labelSmall = t(12, FontWeight.Medium, D.textLo)
    )

    MaterialTheme(colorScheme = scheme, typography = typo) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(D.bg)) {
            content()
        }
    }
}

/** Modes de conduite — gradation shift-light : jaune (normal), rouge course (sport), vert (eco). */
enum class DriveMode(val label: String) {
    NORMAL("Normal"), SPORT("Sport"), ECO("Éco");
    val accent: Color get() = when (this) { NORMAL -> D.accent; SPORT -> D.redline; ECO -> D.mint }
    companion object { fun of(i: Int) = entries.getOrElse(i) { NORMAL } }
}
