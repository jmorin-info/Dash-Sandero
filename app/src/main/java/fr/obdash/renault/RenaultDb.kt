package fr.obdash.renault

/**
 * Extrait de la base DDT : calculateur injection Continental EMS3140 (soft 0C00),
 * Dacia Sandero II (X52) / 1.0 SCe (B4D). UDS sur CAN 500k, requête 7E0, réponse 7E8.
 * LECTURE SEULE : service 22 (ReadDataByIdentifier) + 19 02 (DTC) + 10/3E (session).
 * Les facteurs d'échelle (step/offset) viennent tels quels du fichier DDT
 * EMS3140_0C00_600_570_340_V1.4.
 */

data class RenParam(
    val did: String,          // identifiant 2 octets hex, ex "2002" -> requête "222002"
    val label: String,
    val group: String,
    val firstByte: Int = 4,   // 1-indexe sur la réponse complete (62 + DID + data)
    val bitOffset: Int = 0,   // decalage bits depuis le LSB (champs sous-octet)
    val bits: Int = 16,
    val step: Double = 1.0,
    val offset: Double = 0.0,
    val unit: String = "",
    val decimals: Int = 1,
    val bool: Boolean = false
)

object RenaultDb {
    const val ECU_NAME = "EMS3140 (Continental) \u2014 X52 / 1.0 SCe B4D"
    const val SEND_ID = "7E0"
    const val RECV_ID = "7E8"

    val idents = listOf(
        "F197" to "Type moteur / système",
        "F187" to "Reference piece Renault",
        "F190" to "VIN",
        "F18C" to "N\u00B0 serie calculateur",
        "F194" to "Soft fournisseur",
        "F195" to "Version soft",
        "F182" to "Calibration",
        "F1A0" to "Version diagnostic"
    )

    val params = listOf(
        // --- Moteur / couple
        RenParam("2002", "Régime moteur", "Moteur / couple", bits = 16, step = 0.25, unit = "tr/min", decimals = 0),
        RenParam("2003", "Vitesse véhicule (haute res.)", "Moteur / couple", bits = 16, step = 0.01, unit = "km/h", decimals = 2),
        RenParam("2004", "Couple effectif estime", "Moteur / couple", bits = 16, step = 0.03125, offset = -400.0, unit = "N\u00B7m"),
        RenParam("2024", "Consigne de ralenti", "Moteur / couple", bits = 16, step = 0.25, unit = "tr/min", decimals = 0),
        RenParam("202E", "Position pedale", "Moteur / couple", bits = 16, step = 0.125, unit = "%"),
        RenParam("2807", "Avance allumage", "Moteur / couple", bits = 8, step = 0.375, offset = -23.625, unit = "\u00B0vil"),
        RenParam("2031", "Position VVT admission", "Moteur / couple", bits = 16, step = 0.02, unit = "\u00B0"),
        RenParam("2030", "Consigne VVT admission", "Moteur / couple", bits = 16, step = 0.02, unit = "\u00B0"),
        RenParam("245E", "Charge en air cylindre", "Moteur / couple", bits = 16, step = 0.006103516, unit = "%"),

        // --- Injection / richesse
        RenParam("2854", "Ti injection cyl. 1", "Injection / richesse", bits = 24, step = 0.004, unit = "ms", decimals = 3),
        RenParam("2855", "Ti injection cyl. 2", "Injection / richesse", bits = 24, step = 0.004, unit = "ms", decimals = 3),
        RenParam("2856", "Ti injection cyl. 3", "Injection / richesse", bits = 24, step = 0.004, unit = "ms", decimals = 3),
        RenParam("2850", "Correction richesse (boucle)", "Injection / richesse", bits = 16, step = 1.01725e-5, offset = 0.6666565, decimals = 4),
        RenParam("2851", "Adaptatif richesse \u2014 gain", "Injection / richesse", bits = 8, step = 0.002604167, offset = 0.6666667, decimals = 4),
        RenParam("2852", "Adaptatif richesse \u2014 offset", "Injection / richesse", bits = 8, step = 4.0, offset = -512.0, unit = "\u00B5s", decimals = 0),
        RenParam("284E", "Etat regulation richesse", "Injection / richesse", bits = 8, decimals = 0),

        // --- Thermique / electrique
        RenParam("2001", "T\u00B0 liquide refroidissement", "Thermique / electrique", bits = 16, step = 0.1, offset = -273.0, unit = "\u00B0C"),
        RenParam("240D", "T\u00B0 air admission", "Thermique / electrique", bits = 16, step = 0.1, offset = -273.0, unit = "\u00B0C"),
        RenParam("240B", "Pression collecteur", "Thermique / electrique", bits = 16, step = 1.0, unit = "mbar", decimals = 0),
        RenParam("2009", "Pression atmospherique", "Thermique / electrique", bits = 16, step = 1.0, unit = "mbar", decimals = 0),
        RenParam("2005", "Tension batterie", "Thermique / electrique", bits = 16, step = 0.01, unit = "V", decimals = 2),
        RenParam("2099", "Charge alternateur", "Thermique / electrique", bits = 8, step = 0.3921569, unit = "%", decimals = 0),
        RenParam("2927", "Consigne GMV", "Thermique / electrique", bits = 8, step = 0.3921569, unit = "%", decimals = 0),
        RenParam("2921", "GMV 1 actif", "Thermique / electrique", bits = 1, bitOffset = 7, bool = true),
        RenParam("2922", "GMV 2 actif", "Thermique / electrique", bits = 1, bitOffset = 7, bool = true),
        RenParam("2822", "Purge canister", "Thermique / electrique", bits = 16, step = 0.001525902, unit = "%"),

        // --- Surveillance
        RenParam("2841", "Rates combustion cyl. 1", "Surveillance", bits = 16, decimals = 0),
        RenParam("2842", "Rates combustion cyl. 2", "Surveillance", bits = 16, decimals = 0),
        RenParam("2843", "Rates combustion cyl. 3", "Surveillance", bits = 16, decimals = 0),
        RenParam("2847", "Cliquetis detecte cyl. 1", "Surveillance", bits = 16, decimals = 0),
        RenParam("2848", "Cliquetis detecte cyl. 2", "Surveillance", bits = 16, decimals = 0),
        RenParam("2849", "Cliquetis detecte cyl. 3", "Surveillance", bits = 16, decimals = 0),
        RenParam("284D", "Bruit cliquetis moyen", "Surveillance", bits = 8, decimals = 0),

        // ---- Ajouts extraits du fichier DDT (echelles step/offset verifiees) ----
        // Couple moteur MODELISE par le calculateur : signe (negatif en frein moteur),
        // aucune app OBD generique ne l'expose. C'est le vrai couple vilebrequin estime.
        RenParam("2037", "Couple moteur", "Couple & charge", bits = 16, step = 0.03125, offset = -400.0, unit = "Nm", decimals = 0),
        RenParam("2067", "Couple indique brut", "Couple & charge", bits = 16, step = 0.03125, offset = 0.0, unit = "Nm", decimals = 0),
        RenParam("202E", "Position pedale accel.", "Commandes", bits = 16, step = 0.125, offset = 0.0, unit = "%", decimals = 0),
        RenParam("2024", "Consigne de ralenti", "Ralenti", bits = 16, step = 0.25, offset = 0.0, unit = "tr/min", decimals = 0),
        // Temperature d'huile : le DID existe ; sur SCe 73 sans capteur dedie, valeur modelisee
        RenParam("2007", "Temperature huile", "Thermique / electrique", bits = 16, step = 0.1, offset = -273.0, unit = "°C", decimals = 0),
        RenParam("20A5", "Distance depuis vidange", "Maintenance", bits = 16, step = 1.0, offset = 0.0, unit = "km", decimals = 0),
        RenParam("20AE", "Demarrages cumules", "Maintenance", bits = 16, step = 1.0, offset = 0.0, unit = "", decimals = 0),
        // Etats 2 bits (boite manuelle) : code brut 0-3, se lit changer a l'appui
        RenParam("2027", "Embrayage (code etat)", "Commandes", bits = 2, bitOffset = 0, step = 1.0, offset = 0.0, decimals = 0),
        RenParam("20A2", "Point mort (code etat)", "Commandes", bits = 2, bitOffset = 0, step = 1.0, offset = 0.0, decimals = 0)
    )

    /** Reassemble une réponse ELM mono ou multi-trame ISO-TP ("014", "0:...", "1:..."). */
    fun cleanIsoTp(resp: String): String {
        val toks = resp.trim().uppercase().split(Regex("\\s+"))
        val sb = StringBuilder()
        for (t in toks) {
            val m = Regex("^([0-9A-F]{1,2}):([0-9A-F]*)$").find(t)
            when {
                m != null -> sb.append(m.groupValues[2])
                t.matches(Regex("^[0-9A-F]{3}$")) && toks.size > 1 && sb.isEmpty() -> { /* longueur ISO-TP */ }
                t.matches(Regex("^[0-9A-F]+$")) -> sb.append(t)
            }
        }
        return sb.toString()
    }

    /** Extrait et formate la valeur physique d'un paramètre depuis la réponse nettoyee. */
    fun decode(clean: String, p: RenParam): String? {
        val marker = "62" + p.did.uppercase()
        val i = clean.indexOf(marker)
        if (i < 0) return null
        val bytes = clean.substring(i).chunked(2).mapNotNull { it.toIntOrNull(16) }
        val fb = p.firstByte - 1
        if (fb >= bytes.size) return null
        val raw: Long
        if (p.bits <= 8) {
            raw = ((bytes[fb] shr p.bitOffset) and ((1 shl p.bits) - 1)).toLong()
        } else {
            val nb = (p.bits + 7) / 8
            if (fb + nb > bytes.size) return null
            var acc = 0L
            for (k in 0 until nb) acc = (acc shl 8) or bytes[fb + k].toLong()
            raw = acc
        }
        if (p.bool) return if (raw != 0L) "ON" else "OFF"
        val phys = raw * p.step + p.offset
        val v = String.format("%." + p.decimals + "f", phys)
        return if (p.unit.isEmpty()) v else "$v ${p.unit}"
    }

    /** Comme decode() mais rend la valeur physique numerique (sans mise en forme). */
    fun decodeNumeric(clean: String, p: RenParam): Double? {
        val marker = "62" + p.did.uppercase()
        val i = clean.indexOf(marker)
        if (i < 0) return null
        val bytes = clean.substring(i).chunked(2).mapNotNull { it.toIntOrNull(16) }
        val fb = p.firstByte - 1
        if (fb >= bytes.size) return null
        val raw: Long = if (p.bits <= 8) {
            ((bytes[fb] shr p.bitOffset) and ((1 shl p.bits) - 1)).toLong()
        } else {
            val nb = (p.bits + 7) / 8
            if (fb + nb > bytes.size) return null
            var acc = 0L; for (k in 0 until nb) acc = (acc shl 8) or bytes[fb + k].toLong(); acc
        }
        return raw * p.step + p.offset
    }

    /** Identification : rend l'ASCII si imprimable, sinon l'hex brut. */
    fun decodeAscii(clean: String, did: String): String? {
        val marker = "62" + did.uppercase()
        val i = clean.indexOf(marker)
        if (i < 0) return null
        val hex = clean.substring(i + marker.length)
        val bytes = hex.chunked(2).mapNotNull { it.toIntOrNull(16) }
        if (bytes.isEmpty()) return null
        val printable = bytes.count { it in 32..126 }
        return if (printable >= bytes.size * 7 / 10)
            bytes.map { if (it in 32..126) it.toChar() else '.' }.joinToString("")
        else hex
    }

    /** Decode "59 02 <mask> [DTC(3o) status(1o)]*" -> "P0113.13 [confirme]". */
    fun decodeUdsDtc(clean: String): List<String> {
        val i = clean.indexOf("5902")
        if (i < 0) return emptyList()
        val bytes = clean.substring(i + 6).chunked(2).mapNotNull { it.toIntOrNull(16) }
        val out = mutableListOf<String>()
        var k = 0
        while (k + 3 < bytes.size) {
            val b0 = bytes[k]; val b1 = bytes[k + 1]; val b2 = bytes[k + 2]; val st = bytes[k + 3]
            k += 4
            val sys = when ((b0 shr 6) and 3) { 0 -> "P"; 1 -> "C"; 2 -> "B"; else -> "U" }
            val code = sys + ((b0 shr 4) and 3) +
                String.format("%03X", ((b0 and 0xF) shl 8) or b1) +
                String.format(".%02X", b2)
            val flags = mutableListOf<String>()
            if (st and 0x01 != 0) flags.add("actif")
            if (st and 0x04 != 0) flags.add("en attente")
            if (st and 0x08 != 0) flags.add("confirme")
            out.add(code + if (flags.isEmpty()) "" else "  [" + flags.joinToString(", ") + "]")
        }
        return out
    }
}
