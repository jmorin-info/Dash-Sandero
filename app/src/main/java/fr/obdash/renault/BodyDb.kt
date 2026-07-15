package fr.obdash.renault

/**
 * Commandes actionneurs carrosserie/habitacle (tests atelier), issues de la base DDT
 * pour Dacia Sandero II (X52). LECTURE + ECRITURE ACTIONNEUR, mais uniquement des
 * "output control" de diagnostic : contact mis, moteur coupé, véhicule à l'arrêt.
 * Le calculateur reprend la main des que la session se ferme.
 *
 *  - BCM (UCH, adr 26, CAN 745/765)  : service 30 = InputOutputControl by localId (KWP)
 *  - HVAC (ClimBox, adr 29, CAN 744/764) : service 2F = InputOutputControl by DID (UDS)
 *
 * Aucun de ces deux calculateurs n'expose de SecurityAccess (27) sur ces trames :
 * les tests actionneur passent après une simple session étendue (10 C0).
 */

enum class BodyEcu(val label: String, val sendId: String, val recvId: String) {
    BCM("BCM / UCH (carrosserie)", "745", "765"),
    HVAC("ClimBox (ventilation)", "744", "764"),
    CLUSTER("Tableau de bord", "743", "763")
}

/**
 * @param start  trame complete a envoyer pour ACTIVER
 * @param stop   trame de retour au calculateur (desactive)
 * @param warn   true = effet visible/audible fort (a annoncer)
 */
data class BodyCmd(
    val ecu: BodyEcu,
    val group: String,
    val label: String,
    val start: String,
    val stop: String,
    val warn: Boolean = false
)

object BodyDb {

    // --- BCM : service 30 <localId> 00 <param>  (param 01 = actif, 00 = retour ECU)
    private fun bcm(group: String, label: String, id: String, warn: Boolean = false) =
        BodyCmd(BodyEcu.BCM, group, label, "30${id}0001", "30${id}0000", warn)

    // --- HVAC : 2F <DID> 03 <valeur> = shortTermAdjustment ; 2F <DID> 00 = returnControl
    private fun hvac(group: String, label: String, did: String, value: String, warn: Boolean = false) =
        BodyCmd(BodyEcu.HVAC, group, label, "2F${did}03${value}", "2F${did}00", warn)

    val commands: List<BodyCmd> = listOf(
        // ---- BCM
        bcm("Éclairage", "Feux de détresse (warning)", "08", warn = true),
        bcm("Ouvrants", "Condamnation portes (fermeture)", "01"),
        bcm("Ouvrants", "Super-condamnation", "03"),
        bcm("Ouvrants", "Condamnation par zone", "19"),
        bcm("Ouvrants", "Autorisation leve-vitres", "17"),

        // ---- HVAC (present seulement si clim regulee ; sinon calculateur absent)
        hvac("Ventilation", "Pulseur avant (niveau ~30)", "422F", "1E", warn = true),
        hvac("Ventilation", "Pulseur arriere", "43CE", "1E", warn = true),
        hvac("Ventilation", "Recyclage air", "4230", "01"),
        hvac("Ventilation", "Volet ventilation pieds", "4231", "01"),
        hvac("Ventilation", "Requete compresseur (CAN)", "424F", "01"),
        hvac("Confort", "Siege chauffant AV gauche (fort)", "428B", "01"),
        hvac("Confort", "Siege chauffant AV droit (fort)", "428D", "01"),
        hvac("Confort", "Volant chauffant", "433B", "01"),
        hvac("Confort", "Ioniseur", "424A", "01"),

        // ---- Tableau de bord (tests spectaculaires, moteur coupé)
        BodyCmd(BodyEcu.CLUSTER, "Tableau de bord", "Allumer tous les voyants", "30012001", "30012000", warn = true),
        BodyCmd(BodyEcu.CLUSTER, "Tableau de bord", "Balayage des aiguilles", "30042000", "300411", warn = true),
        BodyCmd(BodyEcu.CLUSTER, "Tableau de bord", "Buzzer / alerte sonore", "30022000", "300211", warn = true),
        BodyCmd(BodyEcu.CLUSTER, "Tableau de bord", "Test éclairage (dimming)", "30052000", "300511"),
        BodyCmd(BodyEcu.CLUSTER, "Tableau de bord", "Test afficheur", "30032000", "300311")
    )

    /** Session étendue KWP/UDS : 10 C0. Reponse positive = "50C0...". */
    const val EXTENDED_SESSION = "10C0"
    const val DEFAULT_SESSION = "1081"

    fun positive(reply: String): Boolean {
        val c = reply.replace(" ", "").uppercase()
        return c.isNotEmpty() && !c.contains("7F") && !c.contains("NODATA") &&
            !c.contains("ERROR") && !c.contains("UNABLE") && !c.contains("STOPPED")
    }
}
