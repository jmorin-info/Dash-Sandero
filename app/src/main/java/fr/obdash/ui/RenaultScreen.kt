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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.obdash.core.ObdRepository
import fr.obdash.renault.RenaultDb

@Composable
fun RenaultScreen() {
    val state by ObdRepository.state.collectAsStateWithLifecycle()
    val active by ObdRepository.renActive.collectAsStateWithLifecycle()
    val values by ObdRepository.renValues.collectAsStateWithLifecycle()
    val selected by ObdRepository.renSelected.collectAsStateWithLifecycle()
    val ident by ObdRepository.renIdent.collectAsStateWithLifecycle()
    val dtc by ObdRepository.renDtc.collectAsStateWithLifecycle()
    var panel by remember { mutableStateOf(0) }   // 0 live, 1 ident, 2 dtc

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        ScreenHeader("Diagnostic Renault", "${RenaultDb.ECU_NAME} · UDS 7E0/7E8 · lecture seule") {
            Text(if (active) "SESSION ÉTENDUE" else "SESSION FERMÉE",
                style = D.eyebrow.copy(color = if (active) D.mint else D.textLo))
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = active, enabled = state.connected,
                onCheckedChange = { on -> ObdRepository.commands.trySend(if (on) "REN_ON" else "REN_OFF") },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = D.accent, checkedThumbColor = D.ink,
                    uncheckedTrackColor = D.slateHi, uncheckedThumbColor = D.textLo)
            )
        }

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // --- Gauche : selection des parametres par groupe
            Column(Modifier.weight(0.78f).fillMaxHeight()) {
                Text("PARAMÈTRES SUIVIS · ${selected.size}", style = D.eyebrow)
                Spacer(Modifier.height(6.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    RenaultDb.params.groupBy { it.group }.forEach { (group, items) ->
                        item {
                            Text(group.uppercase(), style = D.eyebrow.copy(color = D.accent),
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        }
                        items.forEach { p ->
                            item(key = p.did) {
                                val on = p.did in selected
                                Row(Modifier.fillMaxWidth()
                                    .background(if (on) D.accent.copy(alpha = 0.08f) else D.slate,
                                        Plate(6.dp))
                                    .clickable {
                                        ObdRepository.renSelected.value =
                                            if (on) selected - p.did else selected + p.did
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(7.dp).background(
                                        if (on) D.accent else D.slateHi, CircleShape))
                                    Spacer(Modifier.width(9.dp))
                                    Text(p.label, style = MaterialTheme.typography.bodySmall,
                                        color = if (on) D.textHi else D.textLo)
                                }
                            }
                        }
                    }
                }
            }

            // --- Droite : valeurs live / identification / DTC constructeur
            Column(Modifier.weight(1.35f).fillMaxHeight()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SegBtn("VALEURS LIVE", panel == 0) { panel = 0 }
                    SegBtn("IDENTIFICATION", panel == 1) {
                        panel = 1; if (ident.isEmpty()) ObdRepository.commands.trySend("REN_IDENT")
                    }
                    SegBtn("DTC · 19 02", panel == 2) {
                        panel = 2; ObdRepository.commands.trySend("REN_DTC")
                    }
                }
                Spacer(Modifier.height(10.dp))
                when (panel) {
                    0 -> LiveBoard(values, selected, active)
                    1 -> IdentBoard(ident)
                    else -> DtcBoard(dtc)
                }
            }
        }
    }
}

@Composable
private fun SegBtn(label: String, sel: Boolean, onClick: () -> Unit) {
    Box(Modifier
        .background(if (sel) D.accent else D.slate, Blade(8.dp))
        .border(1.dp, if (sel) D.accent else D.hairline, Blade(8.dp))
        .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, style = D.eyebrow.copy(
            color = if (sel) D.ink else D.textLo, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun LiveBoard(values: Map<String, String>, selected: Set<String>, active: Boolean) {
    if (!active) { Hint("Ouvre la session étendue pour lire les paramètres en temps réel."); return }
    if (selected.isEmpty()) { Hint("Sélectionne des paramètres dans la colonne de gauche."); return }
    val items = RenaultDb.params.filter { it.did in selected }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.did }) { p ->
            Box(Modifier.background(D.slate, Plate(8.dp)).padding(12.dp)) {
                Column {
                    Text(p.label.uppercase(), style = D.eyebrow, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    val v = values[p.did]
                    Text(v ?: "--", style = D.readout(19, FontWeight.SemiBold,
                        if (v != null) D.textHi else D.textLo), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun IdentBoard(ident: List<Pair<String, String>>) {
    if (ident.isEmpty()) { Hint("Lecture de l'identification en cours…"); return }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ident.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().background(D.slate, Plate(6.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = D.textLo)
                Text(value, style = D.readout(14, FontWeight.SemiBold, D.textHi))
            }
        }
    }
}

@Composable
private fun DtcBoard(dtc: List<String>) {
    if (dtc.isEmpty()) { Hint("Aucun DTC constructeur remonté (ou lecture en cours)."); return }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        dtc.forEach { code ->
            Row(Modifier.fillMaxWidth().background(D.slate, Plate(6.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(code, style = D.readout(15, FontWeight.SemiBold, D.warn))
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxWidth().height(110.dp)
        .border(1.dp, D.hairline, Plate(9.dp)), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = D.textLo)
    }
}
