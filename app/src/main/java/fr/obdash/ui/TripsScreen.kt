package fr.obdash.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.obdash.core.ObdRepository
import fr.obdash.core.TripRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("EEE d MMM · HH:mm", Locale.FRENCH)

@Composable
fun TripsScreen() {
    val ctx = LocalContext.current
    val store = remember { ObdRepository.tripStore(ctx) }
    val trips by store.flow.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<Long?>(null) }

    // Selection par defaut : le trajet le plus recent (master/detail sur 16:9)
    val selected = trips.firstOrNull { it.id == selectedId } ?: trips.firstOrNull()

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        ScreenHeader("Trajets", if (trips.isEmpty()) "Aucun trajet enregistré" else "${trips.size} trajet(s)") {
            if (trips.isNotEmpty()) {
                TextButton(onClick = { exportCsv(ctx, trips) }) {
                    Text("Exporter CSV", color = D.accent, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { store.clear(); selectedId = null }) {
                    Text("Tout effacer", color = D.textLo)
                }
            }
        }

        if (trips.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Un trajet est archivé à la déconnexion,\nou en clôturant depuis le tableau de bord.",
                    style = MaterialTheme.typography.bodyMedium, color = D.textLo)
            }
            return
        }

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // --- Gauche : synthese + liste
            Column(Modifier.weight(0.92f).fillMaxHeight()) {
                Summary(trips)
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(trips, key = { it.id }) { t ->
                        TripRow(t, t.id == selected?.id) { selectedId = t.id }
                    }
                }
            }
            // --- Droite : detail du trajet selectionne
            Box(Modifier.weight(1.35f).fillMaxHeight()) {
                selected?.let { t ->
                    TripDetail(t, onDelete = { store.delete(t.id); selectedId = null })
                }
            }
        }
    }
}

@Composable
private fun Summary(trips: List<TripRecord>) {
    val totalKm = trips.sumOf { it.distanceKm }
    val totalL = trips.sumOf { it.litres }
    val totalCost = trips.sumOf { it.cost }
    val avg = if (totalKm > 0) totalL / totalKm * 100 else 0.0
    Box(Modifier.fillMaxWidth().background(D.slate, Plate(9.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Cell("DISTANCE", "%.0f km".format(totalKm))
            Cell("CARBURANT", "%.1f L".format(totalL))
            Cell("MOYENNE", "%.1f".format(avg))
            Cell("COÛT", "%.0f €".format(totalCost))
        }
    }
}

@Composable
private fun Cell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = D.readout(17, FontWeight.SemiBold, D.textHi))
        Text(label, style = D.eyebrow)
    }
}

@Composable
private fun TripRow(t: TripRecord, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth()
        .background(if (selected) D.accent.copy(alpha = 0.10f) else D.slate, Plate(8.dp))
        .border(1.dp, if (selected) D.accent.copy(alpha = 0.6f) else D.hairline, Plate(8.dp))
        .clickable { onClick() }.padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Box(Modifier.width(3.dp).height(30.dp).background(D.accent, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(dateFmt.format(Date(t.start)).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium, color = D.textHi,
                    fontWeight = FontWeight.SemiBold)
                Text("%.1f km · %.1f L/100".format(t.distanceKm, t.avgL100), style = D.eyebrow)
            }
            if (t.track.isNotBlank())
                Text("GPS", style = D.eyebrow.copy(color = D.mint))
        }
    }
}

private fun exportCsv(ctx: Context, trips: List<TripRecord>) {
    val sb = StringBuilder("date;duree_s;distance_km;gps_km;litres;moy_L100;vmax_kmh;cout_eur;score_eco;accel_franches;freinages;zone_rouge_s\n")
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE)
    trips.forEach { t ->
        sb.append(fmt.format(Date(t.start))).append(';')
            .append(t.durationSec).append(';')
            .append("%.2f".format(Locale.US, t.distanceKm)).append(';')
            .append("%.2f".format(Locale.US, t.gpsKm)).append(';')
            .append("%.2f".format(Locale.US, t.litres)).append(';')
            .append("%.1f".format(Locale.US, t.avgL100)).append(';')
            .append(t.maxSpeed).append(';')
            .append("%.2f".format(Locale.US, t.cost)).append(';')
            .append(t.ecoScore).append(';').append(t.hardAccels).append(';')
            .append(t.hardBrakes).append(';').append(t.redlineSec).append('\n')
    }
    runCatching {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "obdash_trajets.csv")
        file.writeText(sb.toString())
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(Intent.createChooser(share, "Exporter les trajets").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.onFailure { ObdRepository.appendLog("Export CSV échoué : ${it.message}") }
}
