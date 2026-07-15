package fr.obdash.renault

/**
 * Tracabilite : chaque parametre non-standard de l'app provient d'un fichier de la base DDT
 * (ecu.zip) correspondant au X52. Cette table est la reference affichee dans Reglages ;
 * si un comportement etonne, c'est ici qu'on retrouve la source exacte.
 */
data class DdtSource(val ecu: String, val file: String, val canIds: String, val used: String)

object DdtSources {
    val all = listOf(
        DdtSource("Injection EMS3140 (Continental)",
            "EMS3140_0C00_600_570_340_V1.4 (2021)", "7E0 / 7E8",
            "41 paramètres live, identification F1xx, DTC 19 02"),
        DdtSource("ABS / ESC X10.52.79.98",
            "ABSESC_X10527998 v4.3 (2021)", "740 / 760",
            "Vitesses des 4 roues (4B00-03), angle volant (0100)"),
        DdtSource("Tableau de bord X52",
            "CLUSTER_X52_2nd_Indus v3.3 (2016)", "743 / 763",
            "Odomètre affiché (0207), tests combiné"),
        DdtSource("BCM / UCH (carrosserie)",
            "T4_BCM_BIS_DDT2000_SW2_1 (2012)", "745 / 765",
            "Actionneurs : warning, condamnation, lève-vitres…"),
        DdtSource("ClimBox Gen3 (si clim régulée)",
            "ClimBoxGen3 v10.2 (2023)", "744 / 764",
            "Pulseur, volets — absent sur clim manuelle")
    )
}
