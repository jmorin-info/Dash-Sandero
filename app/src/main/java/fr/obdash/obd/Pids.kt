package fr.obdash.obd

/** Decodage OBD-II mode 01 (SAE J1979). */
object Pids {
    /** Extrait les octets de donnees après l'entete "41<pid>" (réponses sans espaces, ATS0). */
    fun payload(resp: String, pid: Int): IntArray? {
        val clean = resp.replace(" ", "").uppercase()
        val marker = "41" + String.format("%02X", pid)
        val i = clean.indexOf(marker)
        if (i < 0) return null
        val data = clean.substring(i + marker.length)
        val bytes = ArrayList<Int>()
        var k = 0
        while (k + 1 < data.length) {
            val b = data.substring(k, k + 2).toIntOrNull(16) ?: break
            bytes.add(b)
            k += 2
        }
        return if (bytes.isEmpty()) null else bytes.toIntArray()
    }

    /** Decode le bitmask "PIDs supportes" (0100, 0120, 0140...). */
    fun supported(resp: String, base: Int): Set<Int> {
        val b = payload(resp, base) ?: return emptySet()
        val out = mutableSetOf<Int>()
        for (i in 0 until minOf(4, b.size)) {
            for (bit in 0..7) {
                if (b[i] and (1 shl (7 - bit)) != 0) out.add(base + i * 8 + bit + 1)
            }
        }
        return out
    }

    /** Longueur (en octets) des donnees de chaque PID mode 01 qu'on interroge. */
    private val PID_LEN = mapOf(
        0x04 to 1, 0x05 to 1, 0x06 to 1, 0x07 to 1, 0x0B to 1, 0x0C to 2, 0x0D to 1,
        0x0E to 1, 0x0F to 1, 0x11 to 1, 0x1F to 2, 0x21 to 2, 0x2F to 1, 0x33 to 1,
        0x42 to 2, 0x46 to 1, 0x5E to 2
    )

    /**
     * Decode une réponse mode 01 MULTI-PID (une requête "01 0C 0D 0B ..." renvoie tous les PID
     * d'un coup). Gere le multi-trame ISO-TP (marqueurs de ligne "N:" retires) en resynchronisant
     * sur le premier "41" puis en avancant selon la longueur connue de chaque PID.
     * @return map pid -> octets. Vide si rien d'exploitable.
     */
    fun parseMulti(resp: String): Map<Int, IntArray> {
        val cleaned = resp.replace(Regex("[0-9A-Fa-f]?:"), "")
            .replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        val start = cleaned.indexOf("41")
        if (start < 0) return emptyMap()
        var k = start + 2
        val out = LinkedHashMap<Int, IntArray>()
        while (k + 2 <= cleaned.length) {
            val pid = cleaned.substring(k, k + 2).toIntOrNull(16) ?: break
            val len = PID_LEN[pid] ?: break            // PID inconnu -> desynchro, on arrêté
            k += 2
            if (k + len * 2 > cleaned.length) break
            val bytes = IntArray(len) { j ->
                cleaned.substring(k + j * 2, k + j * 2 + 2).toIntOrNull(16) ?: 0
            }
            out[pid] = bytes
            k += len * 2
        }
        return out
    }

    /** Formate un code DTC a partir de 2 octets bruts (ex 0x01,0x13 -> "P0113"). */
    fun formatDtcBytes(b0: Int, b1: Int): String {
        val v = (b0 shl 8) or b1
        val sys = when ((v shr 14) and 0x3) { 0 -> "P"; 1 -> "C"; 2 -> "B"; else -> "U" }
        return sys + ((v shr 12) and 0x3) + String.format("%03X", v and 0xFFF)
    }

    /** Valeurs figées (Mode 02) : marqueur "42<pid>" puis octet de trame ignore, puis data. */
    fun freezeData(resp: String, pid: Int): IntArray? {
        val clean = resp.replace(" ", "").uppercase()
        val marker = "42" + String.format("%02X", pid)
        val i = clean.indexOf(marker)
        if (i < 0) return null
        var data = clean.substring(i + marker.length)
        if (data.length >= 2) data = data.substring(2)   // saute le numéro de trame
        val bytes = ArrayList<Int>()
        var k = 0
        while (k + 1 < data.length) {
            val b = data.substring(k, k + 2).toIntOrNull(16) ?: break
            bytes.add(b); k += 2
        }
        return if (bytes.isEmpty()) null else bytes.toIntArray()
    }

    /**
     * Moniteurs de préparation (Mode 01 PID 01) : etat MIL, nombre de DTC, et etat
     * pret/non prêt de chaque moniteur d'emission (utile avant un contrôle technique).
     * @return (MIL allumé, nb DTC, [(nom, pret?) ]) ; prêt == null si moniteur non supporte.
     */
    fun readiness(resp: String): Triple<Boolean, Int, List<Pair<String, Boolean?>>>? {
        val d = payload(resp, 0x01) ?: return null
        if (d.size < 4) return null
        val a = d[0]; val b = d[1]; val c = d[2]; val e = d[3]
        val mil = a and 0x80 != 0
        val count = a and 0x7F
        val mons = mutableListOf<Pair<String, Boolean?>>()
        fun cont(sup: Int, inc: Int, name: String) {
            val s = b and (1 shl sup) != 0
            mons.add(name to if (!s) null else (b and (1 shl inc) == 0))
        }
        cont(0, 4, "Rates de combustion")
        cont(1, 5, "Système carburant")
        cont(2, 6, "Composants")
        val names = listOf(
            "Catalyseur", "Catalyseur chauffe", "Système EVAP", "Air secondaire",
            "Clim / refrigerant", "Sonde lambda", "Chauffage sonde", "EGR / VVT"
        )
        for (i in 0..7) {
            val s = c and (1 shl i) != 0
            mons.add(names[i] to if (!s) null else (e and (1 shl i) == 0))
        }
        return Triple(mil, count, mons)
    }

    /** Decode une réponse mode 03/07 en liste de codes DTC ("P0113", ...). */
    fun parseDtc(resp: String, service: Int): List<String> {
        val clean = resp.replace(" ", "").uppercase()
        val marker = String.format("%02X", 0x40 + service)
        val i = clean.indexOf(marker)
        if (i < 0) return emptyList()
        var data = clean.substring(i + 2)
        // Sur CAN, le 1er octet est le nombre de DTC : on le saute si coherent.
        if (data.length >= 2) {
            val count = data.substring(0, 2).toIntOrNull(16)
            if (count != null && data.length - 2 >= count * 4) data = data.substring(2)
        }
        val out = mutableListOf<String>()
        var k = 0
        while (k + 3 < data.length) {
            val pair = data.substring(k, k + 4)
            k += 4
            if (pair == "0000") continue
            val v = pair.toIntOrNull(16) ?: continue
            val sys = when ((v shr 14) and 0x3) { 0 -> "P"; 1 -> "C"; 2 -> "B"; else -> "U" }
            out.add(sys + ((v shr 12) and 0x3) + String.format("%03X", v and 0xFFF))
        }
        return out
    }
}
