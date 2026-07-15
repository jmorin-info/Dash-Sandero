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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.obdash.core.ObdRepository

private data class Quick(val cmd: String, val hint: String)
private val GROUPS = listOf(
    "ADAPTATEUR" to listOf(
        Quick("ATRV", "Tension"), Quick("ATDPN", "Protocole"), Quick("ATZ", "Reset ELM")),
    "OBD" to listOf(
        Quick("0100", "PIDs supportés"), Quick("010C", "Régime"), Quick("010D", "Vitesse"),
        Quick("0105", "Temp. eau"), Quick("0101", "État MIL"), Quick("03", "DTC confirmés"),
        Quick("0902", "VIN")),
    "UDS · EMS3140" to listOf(
        Quick("10C0", "Session Renault"), Quick("1003", "Session étendue"), Quick("3E00", "Tester present"),
        Quick("22F187", "Réf. pièce"), Quick("1902AF", "DTC constructeur"))
)

@Composable
fun TerminalScreen() {
    val log by ObdRepository.log.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1)
    }

    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        ScreenHeader("Console", "AT · OBD · UDS — trafic brut de la liaison") {
            TextButton(onClick = { exportLog(ctx, log) }) {
                Text("Exporter", color = D.accent, fontWeight = FontWeight.SemiBold)
            }
        }

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // --- Journal (colonne principale) + saisie
            Column(Modifier.weight(1.6f).fillMaxHeight()) {
                Box(Modifier.fillMaxWidth().weight(1f)
                    .background(D.deep, Plate(9.dp))
                    .border(1.dp, D.hairline, Plate(9.dp))
                    .padding(10.dp)) {
                    LazyColumn(state = listState) {
                        items(log) { line ->
                            Text(line, style = D.readout(12, FontWeight.Normal, logColor(line)),
                                modifier = Modifier.padding(vertical = 1.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.uppercase() },
                    placeholder = { Text("Commande hex ou AT… (ex. 010C)", color = D.textLo) },
                    singleLine = true,
                    textStyle = D.readout(14, FontWeight.SemiBold, D.textHi),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = D.accent, unfocusedBorderColor = D.hairline,
                        cursorColor = D.accent),
                    trailingIcon = {
                        Text("ENVOYER", style = D.eyebrow.copy(
                            color = if (input.isBlank()) D.textLo else D.accent,
                            fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(end = 10.dp).clickable {
                                val c = input.trim()
                                if (c.isNotEmpty()) { ObdRepository.commands.trySend("RAW:$c"); input = "" }
                            })
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // --- Commandes rapides groupees, avec description
            Column(Modifier.weight(0.85f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                GROUPS.forEach { (title, cmds) ->
                    Text(title, style = D.eyebrow.copy(color = D.accent),
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                    cmds.forEach { q ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            .background(D.slate, Plate(6.dp))
                            .clickable { ObdRepository.commands.trySend("RAW:${q.cmd}") }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(q.cmd, style = D.readout(13, FontWeight.SemiBold, D.accent))
                            Text(q.hint, style = MaterialTheme.typography.bodySmall,
                                color = D.textLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Un envoi manuel peut changer l'en-tête CAN : la boucle le repositionne " +
                    "automatiquement au cycle suivant.",
                    style = MaterialTheme.typography.labelSmall, color = D.textLo)
            }
        }
    }
}

/** Ecrit le journal complet dans un fichier texte et ouvre la feuille de partage. */
private fun exportLog(ctx: android.content.Context, log: List<String>) {
    runCatching {
        val dir = java.io.File(ctx.cacheDir, "exports").apply { mkdirs() }
        val fmt = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.FRANCE)
        val file = java.io.File(dir, "obdash_journal_${fmt.format(java.util.Date())}.txt")
        file.writeText(log.joinToString("\n"))
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctx, ctx.packageName + ".fileprovider", file)
        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(android.content.Intent.createChooser(share, "Exporter le journal").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.onFailure { ObdRepository.appendLog("Export journal échoué : ${it.message}") }
}

private fun logColor(line: String) = when {
    line.startsWith(">") -> D.accent
    line.contains("REFUS") || line.contains("Échec") || line.contains("échoué") ||
        line.contains("7F") || line.contains("NO DATA") || line.contains("absent") ||
        line.contains("perdue") -> D.redline
    line.contains("OK") || line.contains("Connecté") || line.contains("joignable") ||
        line.contains("ouverte") || line.contains("enregistré") -> D.mint
    else -> D.textLo
}
