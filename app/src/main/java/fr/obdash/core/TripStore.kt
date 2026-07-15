package fr.obdash.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TripRecord(
    val id: Long,
    val start: Long,          // epoch ms
    val durationSec: Long,
    val distanceKm: Double,
    val litres: Double,
    val avgL100: Double,
    val maxSpeed: Int,
    val cost: Double,
    val gpsKm: Double = 0.0,
    val track: String = "",     // "lat,lon;lat,lon;..." (trace GPS downsamplee)
    val ecoScore: Int = -1,     // -1 = trajet archive avant l'introduction du score
    val hardAccels: Int = 0,
    val hardBrakes: Int = 0,
    val redlineSec: Int = 0
)

/** Historique de trajets persiste dans un fichier JSON du stockage prive de l'app. */
class TripStore(context: Context) {
    private val file = File(context.filesDir, "trips.json")
    private val _flow = MutableStateFlow<List<TripRecord>>(emptyList())
    val flow: StateFlow<List<TripRecord>> = _flow

    init { _flow.value = load() }

    private fun load(): List<TripRecord> = try {
        if (!file.exists()) emptyList()
        else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TripRecord(
                    o.getLong("id"), o.getLong("start"), o.getLong("dur"),
                    o.getDouble("km"), o.getDouble("l"), o.getDouble("avg"),
                    o.getInt("vmax"), o.getDouble("cost"), o.optDouble("gps", 0.0),
                    ecoScore = o.optInt("eco", -1), hardAccels = o.optInt("acc", 0),
                    hardBrakes = o.optInt("brk", 0), redlineSec = o.optInt("red", 0),
                    track = o.optString("track", "")
                )
            }.sortedByDescending { it.start }
        }
    } catch (e: Exception) { emptyList() }

    private fun persist(list: List<TripRecord>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id); put("start", t.start); put("dur", t.durationSec)
                put("km", t.distanceKm); put("l", t.litres); put("avg", t.avgL100)
                put("vmax", t.maxSpeed); put("cost", t.cost); put("gps", t.gpsKm)
                put("track", t.track)
                put("eco", t.ecoScore); put("acc", t.hardAccels)
                put("brk", t.hardBrakes); put("red", t.redlineSec)
            })
        }
        runCatching { file.writeText(arr.toString()) }
    }

    @Synchronized
    fun add(t: TripRecord) {
        val list = (listOf(t) + _flow.value).sortedByDescending { it.start }.take(200)
        _flow.value = list; persist(list)
    }

    @Synchronized
    fun delete(id: Long) {
        val list = _flow.value.filterNot { it.id == id }
        _flow.value = list; persist(list)
    }

    @Synchronized
    fun clear() { _flow.value = emptyList(); persist(emptyList()) }
}
