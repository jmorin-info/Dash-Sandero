package fr.obdash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.obdash.core.ObdRepository

private val DTC_FR = mapOf(
    "P0100" to "Débitmètre MAF — circuit", "P0105" to "Capteur MAP — circuit",
    "P0110" to "Temp. air admission — circuit", "P0113" to "Temp. air admission — signal haut",
    "P0115" to "Temp. liquide de refroidissement — circuit", "P0120" to "Position papillon — circuit",
    "P0130" to "Sonde O2 amont (banc 1)", "P0135" to "Réchauffeur sonde O2 amont",
    "P0171" to "Mélange trop pauvre (banc 1)", "P0172" to "Mélange trop riche (banc 1)",
    "P0300" to "Ratés d'allumage multiples", "P0301" to "Raté d'allumage cylindre 1",
    "P0302" to "Raté d'allumage cylindre 2", "P0303" to "Raté d'allumage cylindre 3",
    "P0325" to "Capteur de cliquetis — circuit", "P0335" to "Capteur régime vilebrequin",
    "P0340" to "Capteur position arbre à cames", "P0420" to "Rendement catalyseur sous seuil",
    "P0442" to "Fuite EVAP (petite)", "P0500" to "Capteur vitesse véhicule",
    "P0562" to "Tension réseau de bord basse", "P0605" to "Calculateur — mémoire interne"
)

private fun describe(code: String) = DTC_FR[code] ?: "Code non répertorié (voir DDT4ALL / doc Renault)"
private fun systemColor(code: String) = when (code.firstOrNull()) {
    'P' -> D.warn; 'C' -> D.azure; 'B' -> Color(0xFFB98BFF); 'U' -> D.redline; else -> D.textLo
}
private fun systemLabel(code: String) = when (code.firstOrNull()) {
    'P' -> "Groupe motopropulseur"; 'C' -> "Châssis"; 'B' -> "Carrosserie"; 'U' -> "Réseau"; else -> ""
}

@Composable
fun DtcScreen() {
    val codes by ObdRepository.dtc.collectAsStateWithLifecycle()
    val state by ObdRepository.state.collectAsStateWithLifecycle()
    val readiness by ObdRepository.readiness.collectAsStateWithLifecycle()
    val milInfo by ObdRepository.milInfo.collectAsStateWithLifecycle()
    val freezeDtc by ObdRepository.freezeDtc.collectAsStateWithLifecycle()
    val freeze by ObdRepository.freezeFrame.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        ScreenHeader("Diagnostic", "Codes défaut · image figée · contrôle technique") {
            PrimaryButton("Lire les codes", enabled = state.connected) {
                ObdRepository.commands.trySend("DTC_READ")
            }
            Spacer(Modifier.width(6.dp))
            GhostButton("Image figée", enabled = state.connected) {
                ObdRepository.commands.trySend("FREEZE")
            }
            Spacer(Modifier.width(6.dp))
            GhostButton("Effacer", enabled = state.connected) { confirmClear = true }
        }

        // Cockpit diag : colonne codes | colonne etat vehicule
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // --- Gauche : MIL + liste des codes
            Column(Modifier.weight(1.15f).fillMaxHeight()) {
                milInfo?.let { MilBanner(it) ; Spacer(Modifier.height(10.dp)) }
                if (codes.isEmpty()) {
                    EmptyBox(
                        if (milInfo == null) "Lance une lecture pour interroger les modes 03 (confirmés)\net 07 (en attente), plus l'état de préparation."
                        else "Aucun code mémorisé."
                    )
                } else {
                    Text("CODES DÉFAUT · ${codes.size}", style = D.eyebrow)
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(codes) { d -> DtcCard(d) }
                    }
                }
            }

            // --- Droite : image figee + readiness
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (freeze.isNotEmpty() || freezeDtc != null) {
                    FreezeCard(freezeDtc, freeze)
                    Spacer(Modifier.height(10.dp))
                }
                if (readiness.isNotEmpty()) ReadinessCard(readiness, Modifier.weight(1f))
                else if (freeze.isEmpty()) EmptyBox("L'image figée et l'état de préparation\ns'affichent après une lecture.")
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = D.slate,
            title = { Text("Effacer les codes ?", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text("Le mode 04 efface les DTC et l'image figée, éteint le voyant et remet à zéro " +
                    "les moniteurs de préparation. À éviter juste avant un contrôle technique.",
                    style = MaterialTheme.typography.bodySmall)
            },
            confirmButton = {
                TextButton(onClick = { ObdRepository.commands.trySend("DTC_CLEAR"); confirmClear = false }) {
                    Text("Effacer", color = D.redline, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler", color = D.textLo) } }
        )
    }
}

@Composable
private fun EmptyBox(text: String) {
    Box(Modifier.fillMaxWidth().height(120.dp)
        .border(1.dp, D.hairline, Plate(9.dp)), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = D.textLo)
    }
}

@Composable
private fun MilBanner(info: String) {
    val milOn = info.startsWith("MIL allumé")
    val col = if (milOn) D.redline else D.mint
    Box(Modifier.fillMaxWidth()
        .background(col.copy(alpha = 0.10f), Plate(9.dp))
        .border(1.dp, col.copy(alpha = 0.55f), Plate(9.dp))
        .padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(11.dp).background(col, Plate(5.dp)))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(if (milOn) "VOYANT MOTEUR ALLUMÉ" else "VOYANT MOTEUR ÉTEINT",
                    style = D.readout(14, FontWeight.Bold, col))
                Text(info, style = MaterialTheme.typography.bodySmall, color = D.textLo)
            }
        }
    }
}

@Composable
private fun DtcCard(d: ObdRepository.Dtc) {
    val col = systemColor(d.code)
    Box(Modifier.fillMaxWidth().background(D.slate, Plate(8.dp))) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(col))
            Column(Modifier.padding(14.dp).weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(d.code, style = D.readout(19, FontWeight.Bold, col))
                    StatusPill(if (d.confirmed) "Confirmé" else "En attente",
                        if (d.confirmed) D.redline else D.warn)
                }
                Spacer(Modifier.height(3.dp))
                Text(describe(d.code), style = MaterialTheme.typography.bodyMedium, color = D.textHi)
                Text(systemLabel(d.code).uppercase(), style = D.eyebrow)
            }
        }
    }
}

@Composable
private fun FreezeCard(dtc: String?, values: List<Pair<String, String>>) {
    Box(Modifier.fillMaxWidth().background(D.slate, Plate(9.dp)).padding(14.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("IMAGE FIGÉE", style = D.eyebrow)
                if (dtc != null) Text(dtc, style = D.readout(12, FontWeight.SemiBold, D.warn))
            }
            Text("Conditions du moteur à la mémorisation du défaut",
                style = MaterialTheme.typography.bodySmall, color = D.textLo)
            Spacer(Modifier.height(10.dp))
            values.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    row.forEachIndexed { i, (l, v) ->
                        if (i > 0) Box(Modifier.width(1.dp).height(32.dp).background(D.hairline))
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(l.uppercase(), style = D.eyebrow)
                            Text(v, style = D.readout(16, FontWeight.SemiBold, D.textHi))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ReadinessCard(monitors: List<Pair<String, Boolean?>>, modifier: Modifier) {
    val supported = monitors.filter { it.second != null }
    val allReady = supported.isNotEmpty() && supported.all { it.second == true }
    val col = if (allReady) D.mint else D.warn
    Box(modifier.fillMaxWidth()
        .border(1.dp, col.copy(alpha = 0.5f), Plate(9.dp)).padding(14.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("PRÉPARATION CONTRÔLE TECHNIQUE", style = D.eyebrow)
                StatusPill(if (allReady) "PRÊT" else "NON PRÊT", col)
            }
            Spacer(Modifier.height(10.dp))
            // 2 colonnes de moniteurs (largeur exploitee)
            val half = (monitors.size + 1) / 2
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(monitors.take(half), monitors.drop(half)).forEach { colItems ->
                    Column(Modifier.weight(1f)) {
                        colItems.forEach { (name, ready) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, style = MaterialTheme.typography.bodySmall,
                                    color = if (ready == null) D.textLo else D.textHi)
                                Text(when (ready) { true -> "Prêt"; false -> "Non prêt"; else -> "n/a" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (ready) { true -> D.mint; false -> D.warn; else -> D.textLo })
                            }
                        }
                    }
                }
            }
        }
    }
}
