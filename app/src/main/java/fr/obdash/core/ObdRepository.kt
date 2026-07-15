package fr.obdash.core

import android.content.Context
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow

data class VehicleState(
    val connected: Boolean = false,
    val adapter: String = "-",
    val protocol: String = "-",
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val coolantC: Int? = null,
    val iatC: Int? = null,
    val mapKpa: Int? = null,
    val baroKpa: Int? = null,
    val throttlePct: Double? = null,
    val loadPct: Double? = null,
    val fuelPct: Double? = null,
    val voltage: Double? = null,           // tension adaptateur (ATRV)
    val moduleV: Double? = null,           // tension calculateur (PID 42)
    val advanceDeg: Double? = null,        // avance a l'allumage (PID 0E)
    val runtimeSec: Int? = null,           // temps depuis demarrage (PID 1F)
    val stftPct: Double? = null,           // correction court terme (PID 06)
    val ltftPct: Double? = null,           // correction long terme (PID 07)
    val ambientC: Int? = null,             // temp air ambiant (PID 46)
    val distMilKm: Int? = null,            // distance avec MIL (PID 21)
    val gear: Int? = null,                 // rapport engage estime (0 = point mort/embrayage)
    val tiMs: Double? = null,              // temps d'injection moyen (conso précise)
    val fuelInjection: Boolean = false,    // true si conso calculee via injection
    val instLph: Double? = null,
    val instL100: Double? = null,
    val avgL100: Double? = null,
    val tripKm: Double = 0.0,
    val tripL: Double = 0.0,
    val tripCost: Double = 0.0,
    val hz: Double = 0.0,
    val torqueNm: Double? = null,          // couple modelise EMS3140 (DID 2037, session Renault)
    val wheels: List<Double>? = null,      // vitesses AVG/AVD/ARG/ARD (ABS 740/760, x0.01 km/h)
    val steerDeg: Double? = null,          // angle volant (ABS, x0.1 deg)
    val odoKm: Int? = null                 // odometre affiche (cluster 743/763, DID 0207)
)

/** Etat partage service <-> UI (singleton volontaire, app mono-processus). */
object ObdRepository {
    val state = MutableStateFlow(VehicleState())
    val log = MutableStateFlow<List<String>>(emptyList())
    data class Dtc(val code: String, val confirmed: Boolean)
    val dtc = MutableStateFlow<List<Dtc>>(emptyList())
    val readiness = MutableStateFlow<List<Pair<String, Boolean?>>>(emptyList())
    val milInfo = MutableStateFlow<String?>(null)
    val freezeDtc = MutableStateFlow<String?>(null)
    val freezeFrame = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    // --- Diagnostic constructeur (UDS EMS3140)
    val renActive = MutableStateFlow(false)
    val renValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val renSelected = MutableStateFlow(
        setOf("2854", "2855", "2856", "2850", "2004", "2841", "2842", "2843")
    )
    val renIdent = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val renDtc = MutableStateFlow<List<String>>(emptyList())

    // --- Mode atelier actionneurs (BCM / HVAC / CLUSTER)
    val bodyActive = MutableStateFlow(false)
    val bodyStarted = MutableStateFlow<Set<String>>(emptySet())
    val bodyEcuUp = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    // Historique conso instantanee (L/h) pour la courbe temps réel
    val consoHistory = MutableStateFlow<List<Float>>(emptyList())

    // Alertes actives (surchauffe, tension basse...) pour bandeau + synthese vocale
    val alerts = MutableStateFlow<List<String>>(emptyList())

    // Records de la session en cours (pics)
    data class Peaks(
        val maxSpeed: Int = 0, val maxRpm: Int = 0, val maxCoolant: Int = 0, val minVolt: Double = 0.0
    )
    val peaks = MutableStateFlow(Peaks())

    // --- Historique de trajets (partage service <-> UI)
    @Volatile private var _tripStore: TripStore? = null
    fun tripStore(ctx: Context): TripStore =
        _tripStore ?: synchronized(this) {
            _tripStore ?: TripStore(ctx.applicationContext).also { _tripStore = it }
        }

    /** Commandes UI -> boucle de polling. */
    val commands = Channel<String>(Channel.BUFFERED)

    /** Rapport de compatibilite vehicule : (sous-systeme, 0=OK 1=ATTENTION 2=KO, detail). */
    data class Check(val name: String, val level: Int, val detail: String)
    val vehCheck = MutableStateFlow<List<Check>>(emptyList())
    val vehCheckRunning = MutableStateFlow(false)

    // Operation longue en cours (retour visuel UI) ; null = inactif
    val busy = MutableStateFlow<String?>(null)

    private val logFmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.FRANCE)
    fun appendLog(line: String) {
        // Horodatage systematique sauf pour l'echo des trames (le ">" reste net a lire)
        val stamped = if (line.startsWith(">")) line else "${logFmt.format(java.util.Date())}  $line"
        log.value = (log.value + stamped).takeLast(400)
    }
}
