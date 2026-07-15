package fr.obdash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.obdash.core.ObdRepository
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.obdash.core.Settings
import fr.obdash.core.SettingsRepo
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repo: SettingsRepo, settings: Settings) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        ScreenHeader("Réglages", "Véhicule, consommation, affichage")
        Row(Modifier.weight(1f)) {
          Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 8.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("Système")
        SwitchRow("Mode simulation (dev sans voiture)", settings.sim) { v ->
            scope.launch { repo.setSim(v) }
        }
        SwitchRow("Demarrage auto au boot (autoradio)", settings.autoStart) { v ->
            scope.launch { repo.setAutoStart(v) }
        }

        Section("Affichage & alertes")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mode nuit", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Off", "Nuit", "Auto").forEachIndexed { i, lbl ->
                    val sel = settings.nightMode == i
                    TextButton(onClick = { scope.launch { repo.setInt(SettingsRepo.NIGHT, i) } }) {
                        Text(lbl, color = if (sel) ColAccent else ColDim,
                            fontWeight = if (sel) androidx.compose.ui.text.font.FontWeight.SemiBold
                                else androidx.compose.ui.text.font.FontWeight.Normal)
                    }
                }
            }
        }
        Column {
            Text("Intensite du filtre nuit  ·  ${(settings.nightDim * 100).toInt()} %",
                style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.nightDim.toFloat(),
                onValueChange = { v -> scope.launch { repo.setDouble(SettingsRepo.NDIM, v.toDouble()) } },
                valueRange = 0.15f..0.75f
            )
        }
        SwitchRow("Theme clair (usage de jour)", settings.lightTheme) { v ->
            scope.launch { repo.setBool(SettingsRepo.LIGHT, v) }
        }
        Column {
            Text("Couleur d'accent", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ACCENTS.forEachIndexed { i, a ->
                    val sel = settings.accentIdx == i
                    val c = if (settings.lightTheme) a.light else a.dark
                    Box(Modifier.size(34.dp)
                        .background(c, Blade(8.dp))
                        .border(2.dp, if (sel) D.textHi else Color.Transparent, Blade(8.dp))
                        .clickable { scope.launch { repo.setInt(SettingsRepo.ACCENT, i) } },
                        contentAlignment = Alignment.Center) {
                        if (sel) Text("✓", style = D.readout(14, FontWeight.Bold, D.ink))
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(ACCENTS.getOrElse(settings.accentIdx) { ACCENTS[0] }.name.uppercase(),
                style = D.eyebrow.copy(color = D.accent))
        }
        SwitchRow("Texture carbone des fonds", settings.weave) { v ->
            scope.launch { repo.setBool(SettingsRepo.WEAVE, v) }
        }
        SwitchRow("Séquence d'allumage au lancement", settings.bootAnim) { v ->
            scope.launch { repo.setBool(SettingsRepo.BOOTANIM, v) }
        }
        SwitchRow("Alertes vocales (surchauffe, tension)", settings.voiceAlerts) { v ->
            scope.launch { repo.setBool(SettingsRepo.VOICE, v) }
        }
        SwitchRow("Trace GPS des trajets", settings.gpsTracking) { v ->
            scope.launch { repo.setBool(SettingsRepo.GPS, v) }
        }
        TextFieldRow("Rapports boite (tr/min par km/h, 1..5)", settings.gearRatios) {
            scope.launch { repo.setString(SettingsRepo.GEARS, it) }
        }

          }
          Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 8.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("Tableau de bord")
        SwitchRow("Courbe debit instantane", settings.showSparkline) { v ->
            scope.launch { repo.setBool(SettingsRepo.SPARK, v) }
        }
        SwitchRow("Records de session", settings.showPeaks) { v ->
            scope.launch { repo.setBool(SettingsRepo.PEAKS, v) }
        }
        SwitchRow("Parametres moteur etendus (STFT, LTFT...)", settings.showVitalsExt) { v ->
            scope.launch { repo.setBool(SettingsRepo.VITEXT, v) }
        }

        Section("Consommation")
        SwitchRow("Conso précise via temps d'injection", settings.preciseFuel) { v ->
            scope.launch { repo.setBool(SettingsRepo.PRECISE, v) }
        }
        NumField("Débit injecteur (cc/min) - conso précise", settings.injCcMin) {
            scope.launch { repo.setDouble(SettingsRepo.INJ, it) }
        }
        NumField("Rendement volumétrique VE (mode estime)", settings.ve) {
            scope.launch { repo.setDouble(SettingsRepo.VE, it) }
        }
        NumField("AFR (14.7 stoechio, ~14.1 en E10)", settings.afr) {
            scope.launch { repo.setDouble(SettingsRepo.AFR, it) }
        }
        NumField("Densité carburant (g/L)", settings.densityGpl) {
            scope.launch { repo.setDouble(SettingsRepo.DENSITY, it) }
        }
        NumField("Prix carburant (EUR/L)", settings.fuelPrice) {
            scope.launch { repo.setDouble(SettingsRepo.PRICE, it) }
        }

        var showCheck by remember { mutableStateOf(false) }
        PrimaryButton("Vérifier la compatibilité véhicule", Modifier.fillMaxWidth()) {
            showCheck = true
            ObdRepository.commands.trySend("VEHCHECK")
        }
        Text("Teste chaque sous-système (OBD, injection, ABS, combiné, BCM) sur ce véhicule, " +
            "en lecture seule. Connexion requise.",
            style = MaterialTheme.typography.labelSmall, color = D.textLo)
        if (showCheck) VehicleCheckDialog { showCheck = false }

        Section("Instruments")
        NumField("Pleine échelle vitesse (km/h)", settings.ringMax.toDouble()) {
            scope.launch { repo.setInt(SettingsRepo.RINGMAX, it.toInt().coerceIn(120, 300)) }
        }
        NumField("Pleine échelle régime (tr/min)", settings.rpmMax.toDouble()) {
            scope.launch { repo.setInt(SettingsRepo.RPMMAX, it.toInt().coerceIn(4000, 9000)) }
        }
        NumField("Début zone rouge (tr/min)", settings.redlineRpm.toDouble()) {
            scope.launch { repo.setInt(SettingsRepo.REDLINE, it.toInt().coerceIn(3000, 8500)) }
        }
        SwitchRow("Strobe en zone rouge", settings.strobe) { v ->
            scope.launch { repo.setBool(SettingsRepo.STROBE, v) }
        }

        Section("Véhicule")
        NumField("Cylindrée (L)", settings.dispL) {
            scope.launch { repo.setDouble(SettingsRepo.DISP, it) }
        }
        NumField("Réservoir (L)", settings.tankL) {
            scope.launch { repo.setDouble(SettingsRepo.TANK, it) }
        }
        NumField("Masse en ordre de marche (kg)", settings.massKg) {
            scope.launch { repo.setDouble(SettingsRepo.MASS, it.coerceIn(600.0, 3500.0)) }
        }

        Section("À propos & sources")
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(size = 24.dp)
            Spacer(Modifier.width(10.dp))
            Text("v${fr.obdash.core.AppInfo.VERSION} · Sandero II ph2 · base DDT X52",
                style = MaterialTheme.typography.labelSmall, color = D.textLo)
        }
        Spacer(Modifier.height(8.dp))
        fr.obdash.renault.DdtSources.all.forEach { src ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(src.ecu, style = MaterialTheme.typography.bodySmall, color = D.textHi)
                    Text("CAN " + src.canIds, style = D.readout(11, FontWeight.Medium, D.accent))
                }
                Text(src.file + " — " + src.used,
                    style = MaterialTheme.typography.labelSmall, color = D.textLo)
            }
        }
        Spacer(Modifier.height(10.dp))

        Text(
            "Conso précise (injection) : lit les temps d'injection du calculateur (bien plus " +
                "exact que le speed-density, gere la coupure à la décélération). Si le calculateur " +
                "exige la session étendue, active aussi l'onglet Renault. Calibre le débit injecteur " +
                "comme le VE : ajuste-le par (litres réels / litres OBDash) sur un plein.",
            style = MaterialTheme.typography.labelSmall, color = ColDim
        )
        Text(
            "Calibrage VE (mode speed-density, sans injection) : plein, RAZ trajet, roule, " +
                "puis VE = VE x (litres réels / litres OBDash). 2-3 pleins pour < 5 % d'ecart.",
            style = MaterialTheme.typography.labelSmall, color = ColDim
        )
          }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(),
        style = MaterialTheme.typography.labelMedium, color = ColAccent,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun VehicleCheckDialog(onDismiss: () -> Unit) {
    val items by ObdRepository.vehCheck.collectAsStateWithLifecycle()
    val running by ObdRepository.vehCheckRunning.collectAsStateWithLifecycle()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = D.slate,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Compatibilité véhicule", style = MaterialTheme.typography.titleMedium)
                if (running) {
                    Spacer(Modifier.width(10.dp))
                    androidx.compose.material3.CircularProgressIndicator(
                        Modifier.size(16.dp), color = D.accent, strokeWidth = 2.dp)
                }
            }
        },
        text = {
            Column {
                if (items.isEmpty() && !running)
                    Text("Lance la vérification avec le véhicule connecté, contact mis.",
                        style = MaterialTheme.typography.bodySmall, color = D.textLo)
                items.forEach { c ->
                    val col = when (c.level) { 0 -> D.mint; 1 -> D.warn; else -> D.redline }
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(col, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(c.name, style = MaterialTheme.typography.bodyMedium)
                            Text(c.detail, style = MaterialTheme.typography.labelSmall, color = D.textLo)
                        }
                    }
                }
                if (!running && items.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    val okN = items.count { it.level == 0 }
                    Text("$okN/${items.size} sous-systèmes validés sur ce véhicule",
                        style = D.eyebrow.copy(color = if (okN == items.size) D.mint else D.warn))
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { ObdRepository.commands.trySend("VEHCHECK") }, enabled = !running
            ) { Text("Relancer", color = if (running) D.textLo else D.accent) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Fermer", color = D.textLo) }
        }
    )
}

@Composable
private fun NumField(label: String, initial: Double, onSave: (Double) -> Unit) {
    var txt by remember(initial) { mutableStateOf(initial.toString()) }
    OutlinedTextField(
        value = txt,
        onValueChange = { txt = it },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = {
            TextButton(onClick = {
                txt.replace(',', '.').toDoubleOrNull()?.let(onSave)
            }) { Text("OK") }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TextFieldRow(label: String, initial: String, onSave: (String) -> Unit) {
    var txt by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = txt,
        onValueChange = { txt = it },
        label = { Text(label) },
        singleLine = true,
        trailingIcon = { TextButton(onClick = { onSave(txt) }) { Text("OK") } },
        modifier = Modifier.fillMaxWidth()
    )
}
