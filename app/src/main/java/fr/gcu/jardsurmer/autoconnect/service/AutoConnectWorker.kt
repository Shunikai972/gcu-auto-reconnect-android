package fr.gcu.jardsurmer.autoconnect.service

import android.content.Context
import android.os.PowerManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.gcu.jardsurmer.autoconnect.data.AppState
import fr.gcu.jardsurmer.autoconnect.data.CredentialStore
import fr.gcu.jardsurmer.autoconnect.data.NetworkInspector
import fr.gcu.jardsurmer.autoconnect.data.PortalLoginClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AutoConnectWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!AppState.isEnabled(context)) {
            return@withContext Result.success()
        }

        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GCUAuto:WorkerWakeLock")
        wakeLock?.acquire(90000L)

        try {
            AlarmReceiver.scheduleNextAlarm(context)
            val credentials = CredentialStore.load(context)
            val wifi = NetworkInspector.findWifiNetwork(context)
            PortalLoginClient.login(context, wifi, credentials)
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        } finally {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
    }

    companion object {
        private const val WORK_NAME = "gcu_auto_connect_periodic"

        fun schedulePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<AutoConnectWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun cancelPeriodicWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
