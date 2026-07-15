package fr.obdash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.obdash.core.ObdRepository
import fr.obdash.renault.BodyCmd
import fr.obdash.renault.BodyDb
import fr.obdash.renault.BodyEcu

@Composable
fun BodyScreen() {
    val state by ObdRepository.state.collectAsStateWithLifecycle()
    val active by ObdRepository.bodyActive.collectAsStateWithLifecycle()
    val started by ObdRepository.bodyStarted.collectAsStateWithLifecycle()
    val ecuUp by ObdRepository.bodyEcuUp.collectAsStateWithLifecycle()
    val grouped = BodyDb.commands.groupBy { it.ecu }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        ScreenHeader("Actionneurs", "Tests de diagnostic · écriture sur le bus") {
            if (active && started.isNotEmpty()) {
                Box(Modifier.background(D.redline, Plate(6.dp))
                    .clickable { ObdRepository.commands.trySend("BODY_OFF") }
                    .padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("TOUT COUPER · ${started.size}",
                        style = D.eyebrow.copy(color = D.textHi, fontWeight = FontWeight.Bold))
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(if (active) "MODE ATELIER" else "INACTIF", style = D.eyebrow)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = active, enabled = state.connected,
                onCheckedChange = { on -> ObdRepository.commands.trySend(if (on) "BODY_ON" else "BODY_OFF") },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = D.accent, checkedThumbColor = D.ink,
                    uncheckedTrackColor = D.slateHi, uncheckedThumbColor = D.textLo
                )
            )
        }

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // --- Gauche : securite + etat des calculateurs
            Column(Modifier.weight(0.72f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth()
                    .background(D.warn.copy(alpha = 0.06f), Plate(9.dp))
                    .hazardStripes(D.warn, alpha = 0.12f)
                    .edgeAccent(D.warn)
                    .border(1.dp, D.warn.copy(alpha = 0.45f), Plate(9.dp))
                    .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)) {
                    Column {
                        Text("À L'ARRÊT · FREIN À MAIN SERRÉ",
                            style = D.readout(13, FontWeight.Bold, D.warn))
                        Spacer(Modifier.height(6.dp))
                        Text("Moteur coupé recommandé : la ventilation et les aiguilles l'exigent, " +
                            "la condamnation passe souvent moteur tournant. Commandes momentanées — " +
                            "le calculateur reprend la main à la fermeture de session.",
                            style = MaterialTheme.typography.bodySmall, color = D.textLo)
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("CALCULATEURS", style = D.eyebrow)
                Spacer(Modifier.height(6.dp))
                BodyEcu.entries.forEach { ecu ->
                    val up = ecuUp[ecu.name]
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(ecu.label, style = MaterialTheme.typography.bodyMedium, color = D.textHi)
                            Text("CAN ${ecu.sendId} / ${ecu.recvId}", style = D.eyebrow)
                        }
                        when (up) {
                            true -> StatusPill("joignable", D.mint)
                            false -> StatusPill("absent", D.redline)
                            else -> StatusPill("—", D.textLo)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Le BCM est présent sur toutes les Sandero II. La ventilation n'existe qu'avec " +
                    "une clim régulée — en clim manuelle elle s'affiche « absent ». " +
                    "« Autorisation lève-vitres » active les commandes, elle ne monte pas la vitre.",
                    style = MaterialTheme.typography.bodySmall, color = D.textLo)
            }

            // --- Droite : commandes en grille 2 colonnes
            LazyColumn(Modifier.weight(1.28f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.connected) {
                    item {
                        Box(Modifier.fillMaxWidth().height(90.dp)
                            .border(1.dp, D.hairline, Plate(9.dp)),
                            contentAlignment = Alignment.Center) {
                            Text("Connecte-toi au véhicule pour activer le mode atelier.",
                                style = MaterialTheme.typography.bodyMedium, color = D.textLo)
                        }
                    }
                }
                grouped.forEach { (ecu, cmds) ->
                    item {
                        Text(ecu.label.uppercase(), style = D.eyebrow.copy(color = D.accent),
                            modifier = Modifier.padding(top = 4.dp))
                    }
                    items(cmds.chunked(2)) { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { cmd ->
                                BodyTile(cmd, cmd.label in started,
                                    active && ecuUp[ecu.name] != false, Modifier.weight(1f))
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** Tuile de commande : la tuile entiere est cliquable (cible large, gants/conduite). */
@Composable
private fun BodyTile(cmd: BodyCmd, on: Boolean, enabled: Boolean, modifier: Modifier) {
    val border = if (on) D.mint else D.hairline
    Box(modifier
        .background(if (on) D.mint.copy(alpha = 0.10f) else D.slate, Plate(8.dp))
        .border(1.dp, border, Plate(8.dp))
        .clickable(enabled = enabled) { send(cmd, !on) }
        .padding(horizontal = 12.dp, vertical = 11.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(
                if (on) D.mint else if (cmd.warn) D.warn else D.slateHi, CircleShape))
            Spacer(Modifier.width(9.dp))
            Text(cmd.label, style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) D.textHi else D.textLo, modifier = Modifier.weight(1f))
            Text(if (on) "STOP" else "ACTIVER",
                style = D.eyebrow.copy(
                    color = if (!enabled) D.textLo else if (on) D.mint else D.accent,
                    fontWeight = FontWeight.Bold))
        }
    }
}

private fun send(cmd: BodyCmd, start: Boolean) {
    val hex = if (start) cmd.start else cmd.stop
    val mode = if (start) "START" else "STOP"
    ObdRepository.commands.trySend("BODY:${cmd.ecu.sendId}:${cmd.ecu.recvId}:$hex:$mode:${cmd.label}")
}
