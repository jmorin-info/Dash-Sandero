package fr.obdash.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import fr.obdash.MainActivity
import fr.obdash.R
import fr.obdash.calc.FuelCalc
import fr.obdash.core.ObdRepository
import fr.obdash.core.Settings as AppSettings
import fr.obdash.core.SettingsRepo
import fr.obdash.core.TripRecord
import fr.obdash.core.VehicleState
import fr.obdash.elm.Elm327
import fr.obdash.obd.Pids
import fr.obdash.overlay.OverlayView
import fr.obdash.renault.BodyDb
import fr.obdash.renault.BodyEcu
import fr.obdash.renault.ChassisDb
import fr.obdash.renault.RenaultDb
import fr.obdash.transport.ObdTransport
import fr.obdash.transport.SimTransport
import fr.obdash.transport.UsbTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Service foreground : detient le port serie, la boucle de polling, le TTS,
 * le GPS optionnel et l'overlay. L'UI ne parle jamais directement au port.
 */
class ObdService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "obdash"
        const val ACTION_STOP = "fr.obdash.action.STOP"
        const val ACTION_OVERLAY = "fr.obdash.action.OVERLAY"
        const val EXTRA_SIM = "sim"
        private const val CYL = 3               // 3 cylindres (B4D 1.0 SCe)

        fun start(ctx: Context, sim: Boolean) {
            val i = Intent(ctx, ObdService::class.java).putExtra(EXTRA_SIM, sim)
            ContextCompat.startForegroundService(ctx, i)
        }
        fun stop(ctx: Context) =
            ctx.startService(Intent(ctx, ObdService::class.java).setAction(ACTION_STOP))
        fun toggleOverlay(ctx: Context) =
            ctx.startService(Intent(ctx, ObdService::class.java).setAction(ACTION_OVERLAY))
    }

    private var job: Job? = null
    private var transport: ObdTransport? = null
    private var overlay: OverlayView? = null

    private var renRotation = 0
    private var tripStartMs: Long = 0L
    private var tripMaxSpeed = 0
    // Style de conduite (cumules par trajet)
    private var tripEcoMs = 0L          // temps en zone eco
    private var tripRedMs = 0L          // temps en zone rouge
    private var tripAccels = 0          // accelerations franches (fronts)
    private var tripBrakes = 0          // freinages appuyes (fronts)
    private var styleLastT = 0L
    private var styleLastV = -1
    private var throttleHigh = false
    private var brakingHard = false
    private val bodySessions = LinkedHashMap<String, String>()
    private val bodyStops = LinkedHashMap<String, Triple<String, String, String>>()

    private var currentHeader: String? = null          // en-tete CAN courant (cache)
    private val tiSlots = arrayOfNulls<Double>(CYL)     // temps d'injection par cylindre (ms)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var coolantAlarm = false
    private var voltAlarm = false
    private var fuelAlarm = false

    // GPS optionnel
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var lastLoc: Location? = null
    private var gpsKm = 0.0
    private val track = ArrayList<Pair<Double, Double>>()
    private var lastTrackLat = 0.0
    private var lastTrackLon = 0.0

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.language = Locale.FRENCH
                ttsReady = true
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> { teardown(); stopSelf() }
            ACTION_OVERLAY -> toggleOverlay()
            else -> {
                startAsForeground()
                startPolling(intent?.getBooleanExtra(EXTRA_SIM, false) ?: false)
            }
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------ helpers ELM

    /** Change l'en-tete CAN uniquement si necessaire (evite le matraquage de l'ELM). */
    private suspend fun setTarget(elm: Elm327, send: String, recv: String) {
        if (currentHeader == send) return
        elm.command("ATSH$send"); elm.command("ATCRA$recv")
        currentHeader = send
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "obdash")
    }

    // ------------------------------------------------------------ polling

    private fun startPolling(sim: Boolean) {
        if (job?.isActive == true) return
        job = lifecycleScope.launch(Dispatchers.IO) {
            val cfg = SettingsRepo(this@ObdService).snapshot()
            tripStartMs = System.currentTimeMillis(); tripMaxSpeed = 0; gpsKm = 0.0
            synchronized(track) { track.clear() }
            ObdRepository.peaks.value = ObdRepository.Peaks()
            if (cfg.gpsTracking) startGps()

            // Boucle de (re)connexion : on retente indefiniment tant que le service vit,
            // sans clore le trajet en cours sur une coupure transitoire.
            while (isActive) {
                val tr: ObdTransport = if (sim) SimTransport() else UsbTransport(this@ObdService)
                transport = tr
                currentHeader = null
                ObdRepository.state.update { it.copy(connected = false, adapter = tr.name) }

                if (!tr.open()) {
                    ObdRepository.appendLog("Attente adaptateur / permission USB...")
                    delay(2000); tr.close(); continue
                }
                val elm = Elm327(tr)
                ObdRepository.appendLog("Init ELM327...")
                if (!elm.init()) {
                    ObdRepository.appendLog("Echec init/protocole (contact mis ?)")
                    tr.close(); delay(2500); continue
                }
                ObdRepository.state.update {
                    it.copy(connected = true, protocol = elm.protocol,
                        adapter = "${tr.name} \u2022 ${elm.version}")
                }
                ObdRepository.appendLog("Connecte. Protocole ATDPN=${elm.protocol}")

                val supported = Pids.supported(elm.command("0100"), 0x00) +
                    Pids.supported(elm.command("0120"), 0x20) +
                    Pids.supported(elm.command("0140"), 0x40)
                ObdRepository.appendLog("PIDs supportes: " +
                    supported.sorted().joinToString(",") { String.format("%02X", it) })

                val has5E = supported.contains(0x5E)
                // Essentiels cockpit : TOUJOURS tentés, même si le bitmap 0100 les sous-déclare
                // (certains calculateurs Renault n'annoncent pas 0C/0D/11 mais y répondent).
                val fast = (listOf(0x0C, 0x0D, 0x11)
                    + (if (0x0B in supported || sim) listOf(0x0B) else emptyList())
                    + (if (has5E) listOf(0x5E) else emptyList())).distinct()
                val slowEss = listOf(0x05, 0x0F, 0x04)   // eau, air, charge : toujours tentés
                val slowOpt = listOf(0x2F, 0x33, 0x0E, 0x1F, 0x06, 0x07, 0x46, 0x42, 0x21)
                    .filter { it in supported || sim }
                val slow = (slowEss + slowOpt).distinct()

                if (!pollLoop(elm, cfg, fast, slow, has5E, sim)) break  // false = service arrêté
                tr.close()
                ObdRepository.state.update { it.copy(connected = false, hz = 0.0) }
                if (!isActive) break
                ObdRepository.appendLog("Connexion perdue, reconnexion...")
                delay(1500)
            }
        }
    }

    /** @return true si on doit tenter une reconnexion, false si le service s'arrêté. */
    private suspend fun pollLoop(
        elm: Elm327, cfg: AppSettings,
        fast: List<Int>, slow: List<Int>, has5E: Boolean, sim: Boolean
    ): Boolean {
        val fastCmd = "01" + fast.joinToString("") { String.format("%02X", it) }
        val slowChunks = slow.chunked(6).map { chunk -> chunk to "01" + chunk.joinToString("") { String.format("%02X", it) } }
        var multiMode = false
        var multiTested = false
        var multiMiss = 0

        var cycle = 0
        var fails = 0
        var lastT = SystemClock.elapsedRealtime()
        while (coroutineContext.isActive) {
            drainCommands(elm)

            if (ObdRepository.bodyActive.value) {
                if (cycle % 12 == 0) for ((send, recv) in bodySessions) {
                    setTarget(elm, send, recv); elm.command("3E00", 700)
                }
                cycle++; delay(200); continue
            }

            var s = ObdRepository.state.value

            // --- Lecture rapide : multi-PID si accepté, sinon lecture individuelle.
            // Repli DYNAMIQUE : si le régime disparaît du multi-PID, on rebascule tout seul.
            var fastMap: Map<Int, IntArray> = when {
                fast.isEmpty() -> emptyMap()
                !multiTested -> {
                    val m = Pids.parseMulti(elm.command(fastCmd, 1200))
                    multiMode = m.size >= 3 && m.containsKey(0x0C)
                    multiTested = true
                    ObdRepository.appendLog(
                        if (multiMode) "Multi-PID actif (${fast.size} PID / requête)"
                        else "Multi-PID non supporté, lecture individuelle")
                    if (multiMode) m else readIndividually(elm, fast)
                }
                multiMode -> {
                    val m = Pids.parseMulti(elm.command(fastCmd, 1000))
                    if (!m.containsKey(0x0C)) {
                        if (++multiMiss >= 3) {
                            multiMode = false
                            ObdRepository.appendLog("Multi-PID instable, bascule en lecture individuelle")
                        }
                        readIndividually(elm, fast)      // repli immédiat : ce cycle n'est pas perdu
                    } else { multiMiss = 0; m }
                }
                else -> readIndividually(elm, fast)
            }
            // Filet de sécurité universel : jamais d'écran vide si l'individuel peut répondre
            if (fastMap.isEmpty() && fast.isNotEmpty()) fastMap = readIndividually(elm, fast)
            s = applyValues(fastMap, s)
            val got = fastMap.isNotEmpty()

            // --- Chassis (ABS 740/760) : 4 roues + volant, ~1 lecture / 12 cycles.
            // L'en-tete CAN est bascule puis IMMEDIATEMENT repositionne sur le moteur.
            if (cycle % 12 == 5) {
                setTarget(elm, ChassisDb.ABS_SEND, ChassisDb.ABS_RECV)
                val ws = ArrayList<Double>(4)
                for (didW in ChassisDb.WHEEL_DIDS) {
                    val r = RenaultDb.cleanIsoTp(elm.command("22$didW", 900))
                    ChassisDb.rawValue(r, didW, 2)?.let { ws.add(it * 0.01) }
                }
                val st = RenaultDb.cleanIsoTp(elm.command("22${ChassisDb.STEER_DID}", 900))
                val steer = ChassisDb.rawValue(st, ChassisDb.STEER_DID, 2)?.let { it * 0.1 - 3276.7 }
                setTarget(elm, "7E0", "7E8")
                if (ws.size == 4) s = s.copy(wheels = ws, steerDeg = steer)
            }
            // Odometre (cluster) : a la connexion puis ~toutes les 4 min
            if (cycle == 3 || cycle % 2400 == 0) {
                setTarget(elm, ChassisDb.CLU_SEND, ChassisDb.CLU_RECV)
                val r = RenaultDb.cleanIsoTp(elm.command("22${ChassisDb.ODO_DID}", 1000))
                setTarget(elm, "7E0", "7E8")
                ChassisDb.rawValue(r, ChassisDb.ODO_DID, 3)?.let { odo ->
                    if (odo in 1..2_000_000) {
                        s = s.copy(odoKm = odo.toInt())
                        ObdRepository.appendLog("Odomètre cluster : $odo km")
                    }
                }
            }

            // --- Lecture lente : un bloc (multi-PID) toutes les 8 iterations
            if (slowChunks.isNotEmpty() && cycle % 8 == 0) {
                val (chunk, chunkCmd) = slowChunks[(cycle / 8) % slowChunks.size]
                val sm = if (multiMode) Pids.parseMulti(elm.command(chunkCmd, 1200))
                         else readIndividually(elm, chunk)
                s = applyValues(sm, s)
                if (cycle % 16 == 0) elm.voltage()?.let { v -> s = s.copy(voltage = v) }
            }

            // Conso précise : lecture des temps d'injection (UDS EMS3140, même calculateur)
            var tiMs: Double? = null
            if (cfg.preciseFuel && cycle % 3 == 0) {
                setTarget(elm, RenaultDb.SEND_ID, RenaultDb.RECV_ID)
                val idx = (cycle / 3) % CYL
                val did = arrayOf("2854", "2855", "2856")[idx]
                RenaultDb.params.firstOrNull { it.did == did }?.let { pr ->
                    val raw = RenaultDb.cleanIsoTp(elm.command("22$did", 900))
                    RenaultDb.decodeNumeric(raw, pr)?.let { tiSlots[idx] = it }
                }
            }
            if (cfg.preciseFuel) {
                val vals = tiSlots.filterNotNull()
                if (vals.isNotEmpty()) tiMs = vals.average()
            }

            // Diagnostic constructeur temps réel
            if (ObdRepository.renActive.value) {
                setTarget(elm, RenaultDb.SEND_ID, RenaultDb.RECV_ID)
                val sel = ObdRepository.renSelected.value.toList().sorted()
                if (sel.isNotEmpty()) {
                    val vals = ObdRepository.renValues.value.toMutableMap()
                    var reads = 0
                    while (reads < 3 && reads < sel.size) {
                        val did = sel[renRotation % sel.size]; renRotation++
                        RenaultDb.params.firstOrNull { it.did == did }?.let { pr ->
                            val r = RenaultDb.cleanIsoTp(elm.command("22$did", 1200))
                            RenaultDb.decode(r, pr)?.let { vals[did] = it }
                            if (did == "2037") RenaultDb.decodeNumeric(r, pr)?.let { nm ->
                                ObdRepository.state.value =
                                    ObdRepository.state.value.copy(torqueNm = nm)
                            }
                        }
                        reads++
                    }
                    ObdRepository.renValues.value = vals
                }
                if (cycle % 15 == 0) elm.command("3E00", 700)
            }

            val now = SystemClock.elapsedRealtime()
            val dt = (now - lastT) / 1000.0; lastT = now
            s = integrate(s, dt, has5E, cfg, tiMs)
            if (dt > 0) s = s.copy(hz = 1.0 / dt)
            s.speedKmh?.let { tripMaxSpeed = max(tripMaxSpeed, it) }
            updateDrivingStyle(s)
            // Pics : on n'emet que si une valeur change réellement (evite emissions/recompositions)
            val pk = ObdRepository.peaks.value
            val np = pk.copy(
                maxSpeed = max(pk.maxSpeed, s.speedKmh ?: 0),
                maxRpm = max(pk.maxRpm, s.rpm ?: 0),
                maxCoolant = max(pk.maxCoolant, s.coolantC ?: 0),
                minVolt = s.voltage?.let { v -> if (pk.minVolt == 0.0 || v < pk.minVolt) v else pk.minVolt } ?: pk.minVolt
            )
            if (np != pk) ObdRepository.peaks.value = np

            ObdRepository.state.value = s
            if (cycle % 4 == 0) overlay?.let { ov -> ov.post { ov.update(s) } }   // overlay ~5 Hz
            checkAlerts(s, cfg)
            if (cycle % 8 == 0) s.instLph?.let { v ->
                ObdRepository.consoHistory.value =
                    (ObdRepository.consoHistory.value + v.toFloat()).takeLast(80)
            }

            // Détection de perte de liaison (hors simulation)
            if (!sim) {
                if (!got) fails++ else fails = 0
                if (fails >= 20) return true      // reconnexion
            }
            cycle++
            delay(25)                              // cadence ~ cible (menagement CPU)
        }
        return false                               // isActive == false -> arret
    }

    /** Lecture PID par PID (repli si le multi-PID n'est pas supporte). */
    /**
     * Ouvre la meilleure session UDS sur l'injection. Renault refuse souvent la 10 03
     * standard (7F 10 12) : on tente 10 C0 (session Renault) d'abord, puis 10 03, puis
     * 10 01 (défaut). Renvoie le code retenu, ou null si tout est refusé.
     */
    private suspend fun openRenaultSession(elm: Elm327): String? {
        setTarget(elm, RenaultDb.SEND_ID, RenaultDb.RECV_ID)
        for (sess in listOf("10C0", "1003", "1001")) {
            val r = RenaultDb.cleanIsoTp(elm.command(sess, 3000))
            if (r.contains("50" + sess.substring(2))) return sess   // 50C0 / 5003 / 5001
        }
        return null
    }

    /** Traduit un refus UDS (7F <service> <NRC>) en message lisible dans le journal. */
    private fun nrcReason(reply: String): String {
        val r = reply.replace(" ", "").uppercase()
        val i = r.indexOf("7F")
        if (i < 0 || i + 6 > r.length) return "réponse vide ou inattendue"
        return when (r.substring(i + 4, i + 6)) {
            "10" -> "refus général"
            "11" -> "service non supporté"
            "12" -> "sous-fonction non supportée"
            "13" -> "longueur de message incorrecte"
            "22" -> "conditions non réunies (état/contact)"
            "31" -> "identifiant hors plage (mauvais actionneur)"
            "33" -> "accès sécurité requis"
            "78" -> "réponse différée (calculateur occupé)"
            "7F" -> "refusé : conditions ou identifiant non supporté par cet UCH"
            else -> "code " + r.substring(i + 4, i + 6)
        }
    }

    private suspend fun readIndividually(elm: Elm327, pids: List<Int>): Map<Int, IntArray> {
        val m = HashMap<Int, IntArray>(pids.size)
        for (p in pids) {
            val d = Pids.payload(elm.command(String.format("01%02X", p), 1000), p)
            if (d != null) m[p] = d
        }
        return m
    }

    /** Applique un lot de PID en UNE seule copie d'etat (moins d'allocations que le chainage). */
    private fun applyValues(m: Map<Int, IntArray>, s: VehicleState): VehicleState {
        if (m.isEmpty()) return s
        var rpm = s.rpm; var speed = s.speedKmh; var map = s.mapKpa; var throttle = s.throttlePct
        var load = s.loadPct; var coolant = s.coolantC; var iat = s.iatC; var fuel = s.fuelPct
        var baro = s.baroKpa; var lph = s.instLph; var adv = s.advanceDeg; var run = s.runtimeSec
        var stft = s.stftPct; var ltft = s.ltftPct; var amb = s.ambientC; var mv = s.moduleV; var dmil = s.distMilKm
        m[0x0C]?.let { if (it.size >= 2) rpm = (it[0] * 256 + it[1]) / 4 }
        m[0x0D]?.let { speed = it[0] }
        m[0x0B]?.let { map = it[0] }
        m[0x11]?.let { throttle = it[0] * 100.0 / 255.0 }
        m[0x04]?.let { load = it[0] * 100.0 / 255.0 }
        m[0x05]?.let { coolant = it[0] - 40 }
        m[0x0F]?.let { iat = it[0] - 40 }
        m[0x2F]?.let { fuel = it[0] * 100.0 / 255.0 }
        m[0x33]?.let { baro = it[0] }
        m[0x5E]?.let { if (it.size >= 2) lph = (it[0] * 256 + it[1]) / 20.0 }
        m[0x0E]?.let { adv = it[0] / 2.0 - 64.0 }
        m[0x1F]?.let { if (it.size >= 2) run = it[0] * 256 + it[1] }
        m[0x06]?.let { stft = (it[0] - 128) * 100.0 / 128.0 }
        m[0x07]?.let { ltft = (it[0] - 128) * 100.0 / 128.0 }
        m[0x46]?.let { amb = it[0] - 40 }
        m[0x42]?.let { if (it.size >= 2) mv = (it[0] * 256 + it[1]) / 1000.0 }
        m[0x21]?.let { if (it.size >= 2) dmil = it[0] * 256 + it[1] }
        return s.copy(
            rpm = rpm, speedKmh = speed, mapKpa = map, throttlePct = throttle, loadPct = load,
            coolantC = coolant, iatC = iat, fuelPct = fuel, baroKpa = baro, instLph = lph,
            advanceDeg = adv, runtimeSec = run, stftPct = stft, ltftPct = ltft,
            ambientC = amb, moduleV = mv, distMilKm = dmil
        )
    }

    /** Conso : injection (exact) si dispo, sinon PID 5E, sinon speed-density. */
    private fun integrate(
        s0: VehicleState, dt: Double, has5E: Boolean, cfg: AppSettings, tiMs: Double?
    ): VehicleState {
        var s = s0
        val rpm = s.rpm ?: 0
        val useInj = cfg.preciseFuel && tiMs != null && rpm > 0
        val lph: Double? = when {
            useInj -> FuelCalc.fuelFromInjection(rpm, tiMs!!, CYL, cfg.injCcMin)
            has5E && s.instLph != null -> s.instLph
            rpm > 300 && s.mapKpa != null ->
                FuelCalc.fuelLph(FuelCalc.airGs(rpm, s.mapKpa!!, s.iatC ?: 25, cfg.ve, cfg.dispL),
                    cfg.afr, cfg.densityGpl)
            else -> null
        }
        s = s.copy(tiMs = tiMs, fuelInjection = useInj)
        if (lph != null) {
            s = s.copy(
                instLph = lph,
                instL100 = s.speedKmh?.let { FuelCalc.l100(lph, it) },
                tripL = s.tripL + lph * dt / 3600.0
            )
        }
        s = s.copy(gear = FuelCalc.estimateGear(s.rpm, s.speedKmh,
            cfg.gearRatios.split(",").mapNotNull { it.trim().toDoubleOrNull() }))
        s.speedKmh?.let { v -> s = s.copy(tripKm = s.tripKm + v * dt / 3600.0) }
        if (s.tripKm > 0.3 && s.tripL > 0) s = s.copy(avgL100 = s.tripL / s.tripKm * 100.0)
        return s.copy(tripCost = s.tripL * cfg.fuelPrice)
    }

    /** Alertes a hysteresis (surchauffe, tension basse) + synthese vocale optionnelle. */
    private fun checkAlerts(s: VehicleState, cfg: AppSettings) {
        val list = mutableListOf<String>()
        s.coolantC?.let { c ->
            if (c >= 110) coolantAlarm = true else if (c < 105) coolantAlarm = false
            if (coolantAlarm) list.add(if (c >= 119) "SURCHAUFFE MOTEUR" else "Température moteur elevee")
        }
        s.voltage?.let { v ->
            if (v in 6.0..11.8) voltAlarm = true else if (v > 12.3) voltAlarm = false
            if (voltAlarm) list.add("Tension batterie basse")
        }
        s.fuelPct?.let { f ->
            if (f in 0.1..8.0) fuelAlarm = true else if (f > 12.0) fuelAlarm = false
            if (fuelAlarm) list.add("Niveau carburant bas")
        }
        val prev = ObdRepository.alerts.value
        if (list != prev) {
            val added = list.filterNot { it in prev }
            if (cfg.voiceAlerts && added.isNotEmpty()) {
                speak(when {
                    added.any { it.startsWith("SURCHAUFFE") } -> "Surchauffe moteur, arretez le véhicule"
                    added.any { it.startsWith("Température") } -> "Attention, température moteur elevee"
                    added.any { it.startsWith("Niveau") } -> "Niveau de carburant bas"
                    else -> "Tension batterie basse"
                })
            }
            ObdRepository.alerts.value = list
        }
    }

    private suspend fun drainCommands(elm: Elm327) {
        while (true) {
            val c = ObdRepository.commands.tryReceive().getOrNull() ?: break
            when {
                c == "TRIP_RESET" -> {
                    finalizeTrip()
                    ObdRepository.consoHistory.value = emptyList()
                    tripStartMs = System.currentTimeMillis(); tripMaxSpeed = 0; gpsKm = 0.0
                    synchronized(track) { track.clear() }
                    ObdRepository.peaks.value = ObdRepository.Peaks()
                    ObdRepository.state.update {
                        it.copy(tripKm = 0.0, tripL = 0.0, tripCost = 0.0, avgL100 = null)
                    }
                }
                c == "DTC_READ" -> {
                    ObdRepository.busy.value = "Lecture des codes"
                    setTarget(elm, "7DF", "7E8")
                    val confirmed = Pids.parseDtc(elm.command("03", 6000), 3)
                    val pending = Pids.parseDtc(elm.command("07", 6000), 7)
                    ObdRepository.dtc.value =
                        confirmed.map { ObdRepository.Dtc(it, true) } +
                        pending.filterNot { it in confirmed }.map { ObdRepository.Dtc(it, false) }
                    Pids.readiness(elm.command("0101", 2000))?.let { (mil, cnt, mons) ->
                        ObdRepository.readiness.value = mons
                        ObdRepository.milInfo.value =
                            (if (mil) "MIL allumé" else "MIL éteint") + " · $cnt DTC mémorisé(s)"
                    }
                    ObdRepository.appendLog("DTC: ${confirmed.size} confirme(s), ${pending.size} en attente")
                    ObdRepository.busy.value = null
                }
                c == "FREEZE" -> {
                    ObdRepository.busy.value = "Lecture de l'image figée"
                    setTarget(elm, "7DF", "7E8")
                    val fd = Pids.freezeData(elm.command("020200", 2500), 0x02)
                    ObdRepository.freezeDtc.value =
                        if (fd != null && fd.size >= 2) Pids.formatDtcBytes(fd[0], fd[1]) else null
                    val frame = mutableListOf<Pair<String, String>>()
                    suspend fun read(pid: Int, label: String, f: (IntArray) -> String) {
                        val d = Pids.freezeData(elm.command(String.format("02%02X00", pid), 1500), pid)
                        if (d != null && d.isNotEmpty()) frame.add(label to f(d))
                    }
                    read(0x0C, "Régime") { if (it.size >= 2) "${(it[0] * 256 + it[1]) / 4} tr/min" else "--" }
                    read(0x0D, "Vitesse") { "${it[0]} km/h" }
                    read(0x04, "Charge") { "%.0f%%".format(it[0] * 100.0 / 255) }
                    read(0x05, "Temp. eau") { "${it[0] - 40} °C" }
                    read(0x0F, "Temp. air") { "${it[0] - 40} °C" }
                    read(0x0B, "MAP") { "${it[0]} kPa" }
                    read(0x11, "Papillon") { "%.0f%%".format(it[0] * 100.0 / 255) }
                    ObdRepository.freezeFrame.value = frame
                    ObdRepository.appendLog("Freeze frame : ${frame.size} valeur(s) figée(s)")
                    ObdRepository.busy.value = null
                }
                c == "DTC_CLEAR" -> {
                    setTarget(elm, "7DF", "7E8")
                    elm.command("04", 6000)
                    ObdRepository.dtc.value = emptyList()
                    ObdRepository.freezeFrame.value = emptyList()
                    ObdRepository.freezeDtc.value = null
                    ObdRepository.appendLog("Codes effaces (mode 04) + extinction MIL")
                }
                c == "VEHCHECK" -> runVehicleCheck(elm)
                c == "BODY_ON" -> enterWorkshop(elm)
                c == "BODY_OFF" -> exitWorkshop(elm)
                c.startsWith("BODY:") -> handleBodyCmd(elm, c)
                c == "REN_ON" -> {
                    ObdRepository.busy.value = "Ouverture de la session"
                    val used = openRenaultSession(elm)
                    val ok = used == "10C0" || used == "1003"   // sessions étendues
                    ObdRepository.renActive.value = ok
                    ObdRepository.appendLog(when {
                        used == null -> "Échec session : 10C0, 1003 et 1001 tous refusés"
                        ok -> "Session étendue ouverte via $used (7E0/7E8)"
                        else -> "Session défaut ($used) seulement — paramètres limités"
                    })
                    ObdRepository.busy.value = null
                }
                c == "REN_OFF" -> {
                    setTarget(elm, RenaultDb.SEND_ID, RenaultDb.RECV_ID)
                    elm.command("1001")
                    ObdRepository.renActive.value = false
                    ObdRepository.appendLog("Retour session UDS par défaut")
                }
                c == "REN_IDENT" -> {
                    ObdRepository.busy.value = "Identification calculateur"
                    setTarget(elm, RenaultDb.SEND_ID, RenaultDb.RECV_ID)
                    val out = mutableListOf<Pair<String, String>>()
                    for ((did, lbl) in RenaultDb.idents) {
                        val r = RenaultDb.cleanIsoTp(elm.command("22$did", 3000))
                        RenaultDb.decodeAscii(r, did)?.let { out.add(lbl to it) }
                    }
                    ObdRepository.renIdent.value = out
                    ObdRepository.appendLog("Identification calculateur : ${out.size} champs")
                    ObdRepository.busy.value = null
                }
                c == "REN_DTC" -> {
                    ObdRepository.busy.value = "Lecture DTC constructeur"
                    setTarget(elm, RenaultDb.SEND_ID, RenaultDb.RECV_ID)
                    val r = RenaultDb.cleanIsoTp(elm.command("1902AF", 6000))
                    ObdRepository.renDtc.value = RenaultDb.decodeUdsDtc(r)
                    ObdRepository.appendLog("DTC UDS : ${ObdRepository.renDtc.value.size} code(s)")
                    ObdRepository.busy.value = null
                }
                c.startsWith("RAW:") -> {
                    val raw = c.removePrefix("RAW:")
                    ObdRepository.appendLog("> $raw")
                    ObdRepository.appendLog(elm.command(raw, 6000).ifBlank { "(réponse vide)" })
                    currentHeader = null   // l'utilisateur a pu changer l'en-tete
                }
            }
        }
    }

    // ------------------------------------------------------------ atelier actionneurs

    private suspend fun ensureSession(elm: Elm327, send: String, recv: String): Boolean {
        if (bodySessions.containsKey(send)) return true
        setTarget(elm, send, recv)
        val ok = BodyDb.positive(elm.command(BodyDb.EXTENDED_SESSION, 2500))
        if (ok) bodySessions[send] = recv
        return ok
    }

    private suspend fun enterWorkshop(elm: Elm327) {
        ObdRepository.bodyActive.value = true
        ObdRepository.busy.value = "Détection des calculateurs"
        ObdRepository.appendLog("Mode atelier : véhicule à l'arrêt.")
        val up = HashMap<String, Boolean>()
        for (ecu in BodyEcu.entries) {
            val ok = ensureSession(elm, ecu.sendId, ecu.recvId)
            up[ecu.name] = ok
            ObdRepository.appendLog("${ecu.label} : ${if (ok) "joignable" else "non présent (option absente ?)"}")
        }
        ObdRepository.bodyEcuUp.value = up
        ObdRepository.busy.value = null
    }

    private suspend fun handleBodyCmd(elm: Elm327, c: String) {
        // format : BODY:<send>:<recv>:<hex>:<START|STOP>:<label>
        val p = c.removePrefix("BODY:").split(":", limit = 5)
        if (p.size < 5) return
        val (send, recv, hex, mode, label) = p
        if (!ensureSession(elm, send, recv)) {
            ObdRepository.appendLog("Session refusee sur $send, commande ignoree"); return
        }
        setTarget(elm, send, recv)
        val r = elm.command(hex, 2500)
        val ok = BodyDb.positive(r)
        val why = if (ok) "" else " (${nrcReason(r)})"
        ObdRepository.appendLog("${if (ok) "OK" else "REFUS"} [$label]$why $hex -> ${r.ifBlank { "(vide)" }}")
        if (ok) {
            val started = ObdRepository.bodyStarted.value.toMutableSet()
            if (mode == "START") { started.add(label); bodyStops[label] = Triple(send, recv, hex) }
            else { started.remove(label); bodyStops.remove(label) }
            ObdRepository.bodyStarted.value = started
        }
    }

    private suspend fun exitWorkshop(elm: Elm327) {
        for ((label, t) in bodyStops.toList()) {
            setTarget(elm, t.first, t.second)
            BodyDb.commands.firstOrNull { it.label == label }?.let { elm.command(it.stop, 1500) }
        }
        for ((send, recv) in bodySessions) {
            setTarget(elm, send, recv); elm.command(BodyDb.DEFAULT_SESSION, 1500)
        }
        bodyStops.clear(); bodySessions.clear()
        ObdRepository.bodyStarted.value = emptySet()
        ObdRepository.bodyActive.value = false
        setTarget(elm, "7E0", "7E8")
        ObdRepository.appendLog("Mode atelier ferme : calculateurs rendus a leur etat normal.")
    }

    // ------------------------------------------------------------ GPS

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun startGps() {
        if (!hasLocationPermission()) {
            ObdRepository.appendLog("GPS active mais permission de localisation refusee")
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return
        locationManager = lm
        val listener = LocationListener { loc ->
            val prev = lastLoc
            if (prev != null && loc.accuracy < 30f) {
                val d = haversine(prev.latitude, prev.longitude, loc.latitude, loc.longitude)
                if (d in 1.0..200.0) gpsKm += d / 1000.0
            }
            lastLoc = loc
            if (loc.hasSpeed()) tripMaxSpeed = max(tripMaxSpeed, (loc.speed * 3.6f).toInt())
            if (loc.accuracy < 40f) synchronized(track) {
                val moved = track.isEmpty() ||
                    haversine(lastTrackLat, lastTrackLon, loc.latitude, loc.longitude) > 15.0
                if (moved) {
                    track.add(loc.latitude to loc.longitude)
                    lastTrackLat = loc.latitude; lastTrackLon = loc.longitude
                    if (track.size > 400) {   // downsample : garde 1 point sur 2
                        val keep = track.filterIndexed { i, _ -> i % 2 == 0 }
                        track.clear(); track.addAll(keep)
                    }
                }
            }
        }
        locationListener = listener
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 3f, listener, Looper.getMainLooper())
        }.onFailure { ObdRepository.appendLog("GPS indisponible: ${it.message}") }
    }

    private fun stopGps() {
        locationListener?.let { locationManager?.removeUpdates(it) }
        locationListener = null; lastLoc = null
    }

    private fun encodeTrack(): String = synchronized(track) {
        track.joinToString(";") { "%.5f,%.5f".format(Locale.US, it.first, it.second) }
    }

    private fun haversine(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
        val r = 6_371_000.0
        val dLa = Math.toRadians(la2 - la1); val dLo = Math.toRadians(lo2 - lo1)
        val a = sin(dLa / 2) * sin(dLa / 2) +
            cos(Math.toRadians(la1)) * cos(Math.toRadians(la2)) * sin(dLo / 2) * sin(dLo / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // ------------------------------------------------------------ overlay

    private fun toggleOverlay() {
        if (overlay != null) { removeOverlay(); return }
        if (!Settings.canDrawOverlays(this)) {
            ObdRepository.appendLog("Permission 'affichage par-dessus' manquante (Réglages Android)")
            return
        }
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val v = OverlayView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 30; y = 160 }
        v.attach(wm, lp); wm.addView(v, lp); overlay = v
        ObdRepository.appendLog("Overlay actif : lance Waze, les jauges restent par-dessus")
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } }
        overlay = null
    }

    // ------------------------------------------------------------ cycle de vie

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "OBDash", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notif: Notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_obdash)
            .setContentTitle("OBDash")
            .setContentText("Liaison OBD active")
            .setContentIntent(pi).setOngoing(true).build()
        var type = if (Build.VERSION.SDK_INT >= 29)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        if (Build.VERSION.SDK_INT >= 30 && hasLocationPermission())
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        ServiceCompat.startForeground(this, 1, notif, type)
    }

    /**
     * Compteurs de style : zone eco (< 2500 tr et papillon doux), zone rouge, fronts
     * d'acceleration franche (papillon > 85 %) et de freinage appuye (decel > 3 m/s²
     * mesuree sur la vitesse OBD). Detection sur FRONT montant : un appui long compte 1.
     */
    private fun updateDrivingStyle(s: fr.obdash.core.VehicleState) {
        val now = SystemClock.elapsedRealtime()
        if (styleLastT != 0L && s.speedKmh != null && (s.speedKmh ?: 0) > 2) {
            val dt = now - styleLastT
            if (dt in 1..2000) {
                val rpm = s.rpm ?: 0
                if (rpm in 1..2499 && (s.throttlePct ?: 0.0) < 45.0) tripEcoMs += dt
                if (rpm >= 5600) tripRedMs += dt
                if (styleLastV >= 0) {
                    val decel = (styleLastV - (s.speedKmh ?: 0)) / 3.6 / (dt / 1000.0)
                    val hard = decel > 3.0
                    if (hard && !brakingHard) tripBrakes++
                    brakingHard = hard
                }
            }
        }
        val high = (s.throttlePct ?: 0.0) > 85.0
        if (high && !throttleHigh && (s.speedKmh ?: 0) > 2) tripAccels++
        throttleHigh = high
        styleLastT = now
        styleLastV = s.speedKmh ?: styleLastV
    }

    /**
     * Auto-diagnostic de compatibilite : teste chaque sous-systeme de l'app sur CE vehicule,
     * en lecture seule, et publie un verdict par ligne. Repond a la question "est-ce que tout
     * marchera sur ma voiture ?" par des faits mesures plutot que des promesses.
     */
    private suspend fun runVehicleCheck(elm: Elm327) {
        if (ObdRepository.vehCheckRunning.value) return
        ObdRepository.vehCheckRunning.value = true
        val out = ArrayList<ObdRepository.Check>()
        fun add(n: String, l: Int, d: String) { out.add(ObdRepository.Check(n, l, d)); ObdRepository.vehCheck.value = out.toList() }
        fun ok(r: String) = RenaultDb.cleanIsoTp(r).let { it.isNotEmpty() && !it.contains("7F") &&
            !it.contains("NODATA") && !it.contains("ERROR") && !it.contains("STOPPED") }
        try {
            ObdRepository.appendLog("Vérification de compatibilité lancée")
            // 1. Adaptateur
            val rv = elm.command("ATRV", 900)
            add("Adaptateur ELM (vLinker)", if (rv.contains("V")) 0 else 2, rv.trim().take(18))
            val dpn = elm.command("ATDPN", 900).trim()
            add("Protocole CAN", if (dpn.contains("6") || dpn.contains("A6")) 0 else 1,
                "ATDPN=$dpn (attendu 6 : 11 bits / 500k)")
            // 2. OBD standard
            val p0100 = elm.command("0100", 1200)
            add("OBD-II standard (PID 0100)", if (p0100.contains("4100")) 0 else 2,
                if (p0100.contains("4100")) "Bitmap PID reçue" else p0100.trim().take(20))
            // 3. EMS3140 : identification + DID cle
            setTarget(elm, "7E0", "7E8")
            val f187 = RenaultDb.cleanIsoTp(elm.command("22F187", 1200))
            val ref = RenaultDb.decodeAscii(f187, "F187") ?: "?"
            add("Injection EMS3140 — identité", if (ok(f187)) 0 else 2, "Réf. pièce : $ref")
            val t2037 = RenaultDb.cleanIsoTp(elm.command("222037", 1200))
            add("Paramètres DDT (couple 2037)", if (ok(t2037)) 0 else 1,
                if (ok(t2037)) "Lecture UDS acceptée" else "Peut nécessiter session étendue (auto)")
            // 4. ABS : roues, avec repli session etendue
            setTarget(elm, ChassisDb.ABS_SEND, ChassisDb.ABS_RECV)
            var w = RenaultDb.cleanIsoTp(elm.command("224B00", 1000))
            var absNote = "Session par défaut"
            if (!ok(w)) {
                elm.command("10C0", 900); w = RenaultDb.cleanIsoTp(elm.command("224B00", 1000))
                absNote = "Après session étendue 10C0"
            }
            add("ABS/ESC (740/760) — 4 roues", if (ok(w)) 0 else 2,
                if (ok(w)) absNote else "Pas de réponse (variante ABS différente ?)")
            // 5. Cluster : odometre
            setTarget(elm, ChassisDb.CLU_SEND, ChassisDb.CLU_RECV)
            val od = RenaultDb.cleanIsoTp(elm.command("22${ChassisDb.ODO_DID}", 1200))
            val km = ChassisDb.rawValue(od, ChassisDb.ODO_DID, 3)
            add("Tableau de bord (743/763) — odomètre", if (km != null) 0 else 2,
                if (km != null) "$km km lus au combiné" else "Pas de réponse")
            // 6. BCM : simple presence (AUCUN actionneur declenche)
            setTarget(elm, "745", "765")
            val bcm = elm.command(BodyDb.EXTENDED_SESSION, 1000)
            if (BodyDb.positive(bcm)) elm.command(BodyDb.DEFAULT_SESSION, 700)
            add("BCM/UCH (745/765) — actionneurs", if (BodyDb.positive(bcm)) 0 else 1,
                if (BodyDb.positive(bcm)) "Session atelier acceptée" else "Injoignable (contact mis ?)")
            setTarget(elm, "7E0", "7E8")
            ObdRepository.appendLog("Vérification terminée : ${out.count { it.level == 0 }}/${out.size} OK")
        } catch (e: Exception) {
            add("Vérification interrompue", 2, e.message ?: "erreur")
        } finally {
            ObdRepository.vehCheckRunning.value = false
        }
    }

    private fun resetStyleCounters() {
        tripEcoMs = 0; tripRedMs = 0; tripAccels = 0; tripBrakes = 0
        throttleHigh = false; brakingHard = false; styleLastV = -1; styleLastT = 0L
    }

    private fun finalizeTrip() {
        val s = ObdRepository.state.value
        // Trajet avorte (< 100 m) : rien a archiver, mais les compteurs de style doivent
        // repartir de zero, sinon ils fuient dans le trajet suivant.
        if (s.tripKm < 0.1 || tripStartMs == 0L) { resetStyleCounters(); return }
        val dur = (System.currentTimeMillis() - tripStartMs) / 1000
        // Score eco 0-100 : penalites accelerations/freinages (ramenees a la distance),
        // temps en zone rouge, et surconsommation vs ~5.5 L/100 de reference du SCe 73.
        val per10k = (10.0 / s.tripKm.coerceAtLeast(0.5))
        val avg = s.avgL100 ?: if (s.tripKm > 0) s.tripL / s.tripKm * 100 else 0.0
        val eco = (100.0
            - tripAccels * 4.0 * per10k
            - tripBrakes * 5.0 * per10k
            - (tripRedMs / 1000.0) * 0.8
            - ((avg - 5.5).coerceAtLeast(0.0)) * 7.0
            ).coerceIn(0.0, 100.0).toInt()
        ObdRepository.tripStore(this).add(
            TripRecord(
                id = tripStartMs, start = tripStartMs, durationSec = dur,
                distanceKm = s.tripKm, litres = s.tripL,
                avgL100 = avg,
                maxSpeed = tripMaxSpeed, cost = s.tripCost, gpsKm = gpsKm, track = encodeTrack(),
                ecoScore = eco, hardAccels = tripAccels, hardBrakes = tripBrakes,
                redlineSec = (tripRedMs / 1000).toInt()
            )
        )
        ObdRepository.appendLog("Trajet enregistré : %.1f km, %.2f L, score éco %d".format(s.tripKm, s.tripL, eco))
        tripStartMs = 0L
        resetStyleCounters()
    }

    private fun teardown() {
        finalizeTrip()
        stopGps()
        job?.cancel(); job = null
        removeOverlay()
        transport?.close(); transport = null
        ObdRepository.renActive.value = false
        ObdRepository.bodyActive.value = false
        ObdRepository.bodyStarted.value = emptySet()
        ObdRepository.alerts.value = emptyList()
        ObdRepository.busy.value = null
        bodySessions.clear(); bodyStops.clear()
        ObdRepository.state.update { it.copy(connected = false, hz = 0.0) }
        ObdRepository.appendLog("Déconnecté.")
    }

    override fun onDestroy() {
        teardown()
        tts?.shutdown(); tts = null
        super.onDestroy()
    }
}
