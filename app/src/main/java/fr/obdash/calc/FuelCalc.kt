package fr.obdash.calc

/**
 * Conso instantanee en speed-density : le 1.0 SCe 73 (B4D) n'a pas de MAF,
 * on synthetise le débit d'air a partir de MAP + régime + temp. admission.
 * Le VE (~0.75 sur un atmo) se calibre en comparant la conso moyenne a un plein réel.
 */
object FuelCalc {
    /** Débit d'air estime en g/s. mapKpa: PID 0B, iatC: PID 0F, dispL: cylindree. */
    fun airGs(rpm: Int, mapKpa: Int, iatC: Int, ve: Double, dispL: Double): Double {
        val iatK = iatC + 273.15
        // (tr/min * kPa * L) -> g/s : masse molaire air 28.97 g/mol, R = 8.314 J/(mol.K)
        return (rpm * mapKpa * ve * dispL * 28.97) / (120.0 * 8.314 * iatK)
    }

    fun fuelLph(airGs: Double, afr: Double, densityGpl: Double): Double =
        airGs * 3600.0 / (afr * densityGpl)

    /** L/100 km, ou null à l'arrêt (afficher les L/h a la place). */
    fun l100(lph: Double, speedKmh: Int): Double? =
        if (speedKmh >= 5) lph / speedKmh * 100.0 else null

    /**
     * Conso via temps d'injection réel (UDS EMS3140), bien plus exacte que le speed-density :
     * volume injecte = débit injecteur x temps d'injection, integre sur les cylindres.
     * Gere nativement la coupure à la décélération (ti ~ 0 -> conso ~ 0).
     * @param tiMs temps d'injection moyen (ms), @param injCcMin débit injecteur (cc/min a la pression de rail)
     */
    /** Estime le rapport engage par le ratio tr/min / vitesse (0 = point mort / embrayage). */
    fun estimateGear(rpm: Int?, speed: Int?, ratios: List<Double>): Int? {
        if (rpm == null || speed == null || ratios.isEmpty()) return null
        if (speed < 4 || rpm < 400) return 0
        val r = rpm.toDouble() / speed
        var best = -1; var err = Double.MAX_VALUE
        ratios.forEachIndexed { i, g -> val e = kotlin.math.abs(r - g) / g; if (e < err) { err = e; best = i } }
        return if (err < 0.18) best + 1 else 0
    }

    fun fuelFromInjection(rpm: Int, tiMs: Double, nCyl: Int, injCcMin: Double): Double {
        if (rpm <= 0 || tiMs <= 0) return 0.0
        val ccPerInj = injCcMin / 60_000.0 * tiMs          // cc par injection
        val injPerSecPerCyl = rpm / 120.0                  // 1 injection / 2 tours (sequentiel)
        val ccPerSec = nCyl * injPerSecPerCyl * ccPerInj
        return ccPerSec * 3.6                              // cc/s -> L/h
    }
}
