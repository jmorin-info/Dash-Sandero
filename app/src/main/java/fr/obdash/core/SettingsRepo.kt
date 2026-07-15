package fr.obdash.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("settings")

data class Settings(
    val sim: Boolean = false,
    val autoStart: Boolean = false,   // reconnexion auto au boot (autoradio Android)
    val ve: Double = 0.75,            // rendement volumétrique (SCe 73 atmo, a calibrer)
    val afr: Double = 14.7,           // stoechio essence (E10 réel ~14.1)
    val densityGpl: Double = 745.0,   // densité essence g/L
    val dispL: Double = 0.999,        // cylindree B4D 1.0 SCe
    val fuelPrice: Double = 1.85,     // EUR/L
    val tankL: Double = 50.0,
    val mode: Int = 0,           // 0 Normal, 1 Sport, 2 Eco
    val voiceAlerts: Boolean = true,
    val preciseFuel: Boolean = false,
    val injCcMin: Double = 120.0, // débit injecteur (cc/min) pour la conso précise
    val gpsTracking: Boolean = false,
    val best0100: Double = 0.0,
    val best80120: Double = 0.0,
    val best400: Double = 0.0,
    val gearRatios: String = "110,62,43,33,27",  // tr/min par km/h, rapports 1..5 (SCe 73)
    val nightMode: Int = 2,                      // 0 off, 1 nuit, 2 auto (21h-7h)
    val nightDim: Double = 0.45,                 // opacite du filtre nuit (0..0.75)
    val lightTheme: Boolean = false,
    val showSparkline: Boolean = true,
    val showPeaks: Boolean = true,
    val showVitalsExt: Boolean = true,
    val cockpitOrder: String = "conso,trip,elec",  // ordre des blocs, colonne droite
    val gOffLat: Double = 0.0,                     // calibration zero G-metre (lateral)
    val gOffLon: Double = 0.0,                     //  " (longitudinal)
    val ringMax: Int = 200,                        // pleine echelle de l'anneau (km/h)
    val rpmMax: Int = 6500,                        // pleine echelle du regime
    val redlineRpm: Int = 5600,                    // debut de zone rouge
    val strobe: Boolean = true,                    // clignotement en zone rouge
    val massKg: Double = 1055.0,                   // masse en ordre de marche (dyno inertiel)
    val accentIdx: Int = 0,                        // teinte d'accent (voir ui.ACCENTS)
    val weave: Boolean = true,                     // texture carbone des fonds
    val bootAnim: Boolean = true                   // sequence d'allumage au lancement
)

class SettingsRepo(private val ctx: Context) {
    companion object {
        val SIM = booleanPreferencesKey("sim")
        val AUTOSTART = booleanPreferencesKey("autostart")
        val VE = doublePreferencesKey("ve")
        val AFR = doublePreferencesKey("afr")
        val DENSITY = doublePreferencesKey("density")
        val DISP = doublePreferencesKey("disp")
        val PRICE = doublePreferencesKey("price")
        val TANK = doublePreferencesKey("tank")
        val MODE = intPreferencesKey("mode")
        val VOICE = booleanPreferencesKey("voice")
        val PRECISE = booleanPreferencesKey("précise")
        val INJ = doublePreferencesKey("inj")
        val GPS = booleanPreferencesKey("gps")
        val B0100 = doublePreferencesKey("b0100")
        val B80120 = doublePreferencesKey("b80120")
        val B400 = doublePreferencesKey("b400")
        val GEARS = androidx.datastore.preferences.core.stringPreferencesKey("gears")
        val NIGHT = intPreferencesKey("night")
        val NDIM = doublePreferencesKey("ndim")
        val LIGHT = booleanPreferencesKey("light")
        val SPARK = booleanPreferencesKey("spark")
        val PEAKS = booleanPreferencesKey("peaks")
        val VITEXT = booleanPreferencesKey("vitext")
        val ORDER = androidx.datastore.preferences.core.stringPreferencesKey("order")
        val GOX = doublePreferencesKey("gox")
        val GOY = doublePreferencesKey("goy")
        val RINGMAX = intPreferencesKey("ringmax")
        val RPMMAX = intPreferencesKey("rpmmax")
        val REDLINE = intPreferencesKey("redline")
        val STROBE = booleanPreferencesKey("strobe")
        val MASS = doublePreferencesKey("mass")
        val ACCENT = intPreferencesKey("accent")
        val WEAVE = booleanPreferencesKey("weave")
        val BOOTANIM = booleanPreferencesKey("bootanim")
    }

    val flow: Flow<Settings> = ctx.dataStore.data.map { p ->
        Settings(
            sim = p[SIM] ?: false,
            autoStart = p[AUTOSTART] ?: false,
            ve = p[VE] ?: 0.75,
            afr = p[AFR] ?: 14.7,
            densityGpl = p[DENSITY] ?: 745.0,
            dispL = p[DISP] ?: 0.999,
            fuelPrice = p[PRICE] ?: 1.85,
            tankL = p[TANK] ?: 50.0,
            mode = p[MODE] ?: 0,
            voiceAlerts = p[VOICE] ?: true,
            preciseFuel = p[PRECISE] ?: false,
            injCcMin = p[INJ] ?: 120.0,
            gpsTracking = p[GPS] ?: false,
            best0100 = p[B0100] ?: 0.0,
            best80120 = p[B80120] ?: 0.0,
            best400 = p[B400] ?: 0.0,
            gearRatios = p[GEARS] ?: "110,62,43,33,27",
            nightMode = p[NIGHT] ?: 2,
            nightDim = p[NDIM] ?: 0.45,
            lightTheme = p[LIGHT] ?: false,
            showSparkline = p[SPARK] ?: true,
            showPeaks = p[PEAKS] ?: true,
            showVitalsExt = p[VITEXT] ?: true,
            cockpitOrder = p[ORDER] ?: "conso,trip,elec",
            gOffLat = p[GOX] ?: 0.0,
            gOffLon = p[GOY] ?: 0.0,
            ringMax = p[RINGMAX] ?: 200,
            rpmMax = p[RPMMAX] ?: 6500,
            redlineRpm = p[REDLINE] ?: 5600,
            strobe = p[STROBE] ?: true,
            massKg = p[MASS] ?: 1055.0,
            accentIdx = p[ACCENT] ?: 0,
            weave = p[WEAVE] ?: true,
            bootAnim = p[BOOTANIM] ?: true
        )
    }

    suspend fun snapshot(): Settings = flow.first()
    suspend fun setSim(v: Boolean) = ctx.dataStore.edit { it[SIM] = v }
    suspend fun setMode(v: Int) = ctx.dataStore.edit { it[MODE] = v }
    suspend fun setInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, v: Int) =
        ctx.dataStore.edit { it[key] = v }
    suspend fun setString(key: androidx.datastore.preferences.core.Preferences.Key<String>, v: String) =
        ctx.dataStore.edit { it[key] = v }
    suspend fun setBool(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, v: Boolean) =
        ctx.dataStore.edit { it[key] = v }
    suspend fun setAutoStart(v: Boolean) = ctx.dataStore.edit { it[AUTOSTART] = v }
    suspend fun setDouble(key: androidx.datastore.preferences.core.Preferences.Key<Double>, v: Double) =
        ctx.dataStore.edit { it[key] = v }
}
