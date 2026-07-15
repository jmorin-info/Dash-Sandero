package fr.obdash.renault

/**
 * Lectures chassis & habitacle multi-calculateurs, extraites de la base DDT du X52.
 * LECTURE SEULE (service 22). Adresses CAN issues du champ obd de chaque fichier DDT :
 *  - ABS/ESC X10.52.79.98 : 740 / 760  (vitesses de roue x0.01 km/h, volant x0.1 deg)
 *  - Cluster X52 2nd Indus : 743 / 763 (odometre affiche, 3 octets, km)
 * Aucune ecriture, aucune session prolongee necessaire : requetes ponctuelles, l'en-tete
 * CAN est repositionne sur le moteur juste apres.
 */
object ChassisDb {
    const val ABS_SEND = "740"; const val ABS_RECV = "760"
    const val CLU_SEND = "743"; const val CLU_RECV = "763"

    // ABS : DID -> (2 octets, x0.01 km/h)
    val WHEEL_DIDS = listOf("4B00", "4B01", "4B02", "4B03")   // AVG, AVD, ARG, ARD
    const val STEER_DID = "0100"                              // x0.1, offset -3276.7 deg

    // Cluster : odometre affiche (3 octets, km bruts)
    const val ODO_DID = "0207"

    /** Extrait la valeur brute d'une reponse 62<did>... (n octets big-endian). */
    fun rawValue(clean: String, did: String, nBytes: Int): Long? {
        val marker = "62" + did.uppercase()
        val i = clean.indexOf(marker)
        if (i < 0) return null
        val hex = clean.substring(i + marker.length)
        if (hex.length < nBytes * 2) return null
        return hex.substring(0, nBytes * 2).toLongOrNull(16)
    }
}
