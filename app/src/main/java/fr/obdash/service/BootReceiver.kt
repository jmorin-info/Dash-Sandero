package fr.obdash.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.obdash.core.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Autoradio Android : la tablette (re)boote a la mise du contact.
 * Si l'option est activee, on relance la liaison OBD automatiquement.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (SettingsRepo(context).snapshot().autoStart) {
                    ObdService.start(context, sim = false)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
