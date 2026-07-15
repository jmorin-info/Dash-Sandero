package fr.obdash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight.Companion.SemiBold
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import fr.obdash.core.Settings
import fr.obdash.core.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Calibration a chaud : au plein, on saisit les litres reellement mis a la pompe et la distance
 * parcourue depuis le dernier plein. L'app calcule le facteur de correction et l'applique au
 * bon parametre — le debit injecteur si la conso precise est active, sinon le VE (speed-density).
 * C'est la seule facon d'atteindre une conso juste : le modele est bon, il lui manque la mesure.
 */
@Composable
fun CalibrationDialog(
    repo: SettingsRepo,
    settings: Settings,
    tripKm: Double,
    tripL: Double,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    var realL by remember { mutableStateOf("") }
    var realKm by remember { mutableStateOf(if (tripKm > 1) "%.1f".format(tripKm) else "") }

    val rl = realL.replace(',', '.').toDoubleOrNull()
    val rk = realKm.replace(',', '.').toDoubleOrNull()
    // Litres estimes par l'app sur cette distance (a la moyenne courante du trajet)
    val estL = if (rk != null && tripKm > 0.5) tripL / tripKm * rk else null
    val factor = if (rl != null && estL != null && estL > 0.05) rl / estL else null

    val usesInjection = settings.preciseFuel
    val current = if (usesInjection) settings.injCcMin else settings.ve
    val corrected = factor?.let { current * it }
    val realL100 = if (rl != null && rk != null && rk > 1) rl / rk * 100 else null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = D.slate,
        title = { Text("Calibrer la consommation", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "Après un plein complet, saisis les litres réellement mis à la pompe et la " +
                        "distance parcourue depuis le plein précédent. " +
                        if (usesInjection) "Le débit injecteur sera corrigé."
                        else "Le rendement volumétrique (VE) sera corrigé.",
                    style = MaterialTheme.typography.bodySmall, color = D.textLo
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Litres au plein", realL, Modifier.weight(1f)) { realL = it }
                    Field("Distance (km)", realKm, Modifier.weight(1f)) { realKm = it }
                }
                Spacer(Modifier.height(14.dp))

                if (factor != null && corrected != null) {
                    Box(Modifier.fillMaxWidth()
                        .background(D.accent.copy(alpha = 0.08f), Plate(8.dp))
                        .border(1.dp, D.accent.copy(alpha = 0.45f), Plate(8.dp))
                        .padding(12.dp)) {
                        Column {
                            RowKV("Conso réelle", realL100?.let { "%.1f L/100".format(it) } ?: "—", D.textHi)
                            RowKV("Conso estimée par OBDash",
                                if (rk != null && rk > 1 && estL != null) "%.1f L/100".format(estL / rk * 100) else "—",
                                D.textLo)
                            RowKV("Écart", "%+.0f %%".format((factor - 1) * 100),
                                if (kotlin.math.abs(factor - 1) < 0.05) D.mint else D.warn)
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(D.hairline))
                            Spacer(Modifier.height(6.dp))
                            RowKV(
                                if (usesInjection) "Débit injecteur" else "Rendement VE",
                                "%.1f → %.1f".format(current, corrected), D.accent
                            )
                        }
                    }
                } else {
                    Text("Saisis les deux valeurs pour voir la correction proposée.",
                        style = MaterialTheme.typography.bodySmall, color = D.textLo)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = corrected != null,
                onClick = {
                    corrected?.let { c ->
                        scope.launch {
                            if (usesInjection) repo.setDouble(SettingsRepo.INJ, c)
                            else repo.setDouble(SettingsRepo.VE, c)
                        }
                    }
                    onDismiss()
                }
            ) {
                Text("Appliquer", color = if (corrected != null) D.accent else D.textLo,
                    fontWeight = SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = D.textLo) } }
    )
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = D.eyebrow) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = D.readout(16, FontWeight.SemiBold, D.textHi),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = D.accent, unfocusedBorderColor = D.hairline,
            cursorColor = D.accent),
        modifier = modifier
    )
}

@Composable
private fun RowKV(k: String, v: String, col: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = D.textLo)
        Text(v, style = D.readout(14, FontWeight.SemiBold, col))
    }
}
