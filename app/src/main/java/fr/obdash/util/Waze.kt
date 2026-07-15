package fr.obdash.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Waze n'expose pas de SDK d'integration embarquable : on pilote l'appli
 * officielle par intent (lancement, split-screen) et deep links de navigation.
 */
object Waze {
    fun launch(ctx: Context, splitScreen: Boolean = false) {
        val i = ctx.packageManager.getLaunchIntentForPackage("com.waze")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul"))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (splitScreen) {
            // Demande a Android d'ouvrir Waze a cote (multi-fenetre)
            i.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        runCatching { ctx.startActivity(i) }
    }

    /** Navigation directe vers des coordonnees (deep link officiel). */
    fun navigateTo(ctx: Context, lat: Double, lon: Double) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("waze://?ll=$lat,$lon&navigate=yes"))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }
}
