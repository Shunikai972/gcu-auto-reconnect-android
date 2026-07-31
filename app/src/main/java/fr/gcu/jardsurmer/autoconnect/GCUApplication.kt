package fr.gcu.jardsurmer.autoconnect

import android.app.Application
import fr.gcu.jardsurmer.autoconnect.data.AppState
import fr.gcu.jardsurmer.autoconnect.data.LogRepository
import fr.gcu.jardsurmer.autoconnect.service.AlarmReceiver
import fr.gcu.jardsurmer.autoconnect.service.AutoConnectService
import fr.gcu.jardsurmer.autoconnect.service.AutoConnectWorker

class GCUApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppState.init(this)
        LogRepository.init(this)

        if (AppState.isEnabled(this)) {
            AutoConnectService.startAuto(this)
            AlarmReceiver.scheduleNextAlarm(this)
            AutoConnectWorker.schedulePeriodicWork(this)
        }
    }
}
