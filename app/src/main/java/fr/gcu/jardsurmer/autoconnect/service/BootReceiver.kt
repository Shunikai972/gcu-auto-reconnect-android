package fr.gcu.jardsurmer.autoconnect.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.gcu.jardsurmer.autoconnect.data.AppState

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.net.wifi.WIFI_STATE_CHANGED" ||
            action == "android.net.conn.CONNECTIVITY_CHANGE"
        ) {
            if (AppState.isEnabled(context)) {
                AutoConnectService.startAuto(context)
                AlarmReceiver.scheduleNextAlarm(context)
                AutoConnectWorker.schedulePeriodicWork(context)
            }
        }
    }
}
