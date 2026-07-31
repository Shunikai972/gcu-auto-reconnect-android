package fr.gcu.jardsurmer.autoconnect.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import fr.gcu.jardsurmer.autoconnect.data.AppState
import fr.gcu.jardsurmer.autoconnect.data.CredentialStore
import fr.gcu.jardsurmer.autoconnect.data.NetworkInspector
import fr.gcu.jardsurmer.autoconnect.data.PortalLoginClient
import fr.gcu.jardsurmer.autoconnect.model.LoginResult
import fr.gcu.jardsurmer.autoconnect.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class AutoConnectService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val attemptRunning = AtomicBoolean(false)
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var periodicTickerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundServiceNotification("Initialisation de l'auto-reconnexion…")

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        registerWifiCallback()
        startPeriodicTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_AUTO -> {
                AppState.setEnabled(this, true)
                setStatus("Auto-reconnexion active. Recherche du Wi-Fi GCU…", isConnected = false)
                AlarmReceiver.scheduleNextAlarm(this)
                AutoConnectWorker.schedulePeriodicWork(this)
                triggerAttempt(oneShot = false, reason = "activation")
                return START_STICKY
            }
            ACTION_CONNECT_NOW -> {
                AppState.setEnabled(this, true)
                setStatus("Connexion et auto-reconnexion en cours…", isConnected = false)
                AlarmReceiver.scheduleNextAlarm(this)
                AutoConnectWorker.schedulePeriodicWork(this)
                triggerAttempt(oneShot = false, reason = "demande manuelle")
                return START_STICKY
            }
            else -> {
                if (AppState.isEnabled(this)) {
                    AlarmReceiver.scheduleNextAlarm(this)
                    AutoConnectWorker.schedulePeriodicWork(this)
                    triggerAttempt(oneShot = false, reason = "redémarrage du service")
                    return START_STICKY
                }
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun startPeriodicTicker() {
        periodicTickerJob?.cancel()
        periodicTickerJob = serviceScope.launch {
            while (isActive) {
                delay(10 * 60 * 1000L) // 10 minutes
                if (AppState.isEnabled(this@AutoConnectService)) {
                    AlarmReceiver.scheduleNextAlarm(this@AutoConnectService)
                    triggerAttempt(oneShot = false, reason = "ticker 10 minutes")
                }
            }
        }
    }

    private fun registerWifiCallback() {
        val cm = connectivityManager ?: return
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (AppState.isEnabled(this@AutoConnectService)) {
                        triggerAttempt(oneShot = false, reason = "Wi-Fi détecté")
                    }
                }

                override fun onLost(network: Network) {
                    if (NetworkInspector.findWifiNetwork(this@AutoConnectService) == null) {
                        setStatus("Wi-Fi absent. L'auto-reconnexion reste active.", isConnected = false)
                    }
                }
            }
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (_: Throwable) {
            setStatus("Surveillance Wi-Fi active (contrôle périodique toutes les 10 minutes).", isConnected = false)
        }
    }

    private fun triggerAttempt(oneShot: Boolean, reason: String) {
        if (!attemptRunning.compareAndSet(false, true)) return

        serviceScope.launch {
            var wakeLock: PowerManager.WakeLock? = null
            try {
                val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GCUAuto:ServiceLoginAttempt")
                wakeLock?.acquire(90000L)

                val credentials = try {
                    CredentialStore.load(this@AutoConnectService)
                } catch (_: Throwable) {
                    setStatus("Erreur de lecture des identifiants chiffrés. Ré-enregistrez-les.", isConnected = false)
                    return@launch
                }

                val wifi = NetworkInspector.findWifiNetwork(this@AutoConnectService)
                val result = PortalLoginClient.login(this@AutoConnectService, wifi, credentials)

                when (result) {
                    is LoginResult.Success -> {
                        val msg = if (reason.isNotEmpty() && reason != "activation") "${result.message} ($reason)" else result.message
                        setStatus(msg, isConnected = true)
                    }
                    is LoginResult.Failure -> {
                        val msg = if (reason.isNotEmpty() && reason != "activation") "${result.message} ($reason)" else result.message
                        setStatus(msg, isConnected = false)
                    }
                }
            } finally {
                wakeLock?.let { if (it.isHeld) it.release() }
                attemptRunning.set(false)
                if (oneShot && !AppState.isEnabled(this@AutoConnectService)) {
                    stopSelf()
                }
            }
        }
    }

    private fun setStatus(message: String, isConnected: Boolean) {
        AppState.setStatus(this, message, isConnected)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun startForegroundServiceNotification(initialText: String) {
        val notification = buildNotification(initialText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } catch (_: Throwable) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Connexion automatique GCU",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintient la reconnexion au portail Wi-Fi GCU"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= 23) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(this, 0, openIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("GCU Auto Connexion")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(AppState.isEnabled(this))
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    override fun onDestroy() {
        periodicTickerJob?.cancel()
        serviceJob.cancel()
        networkCallback?.let {
            try { connectivityManager?.unregisterNetworkCallback(it) } catch (_: Throwable) {}
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_AUTO = "fr.gcu.jardsurmer.autoconnect.START_AUTO"
        const val ACTION_CONNECT_NOW = "fr.gcu.jardsurmer.autoconnect.CONNECT_NOW"
        private const val CHANNEL_ID = "gcu_auto_connection_channel"
        private const val NOTIFICATION_ID = 4100

        fun startAuto(context: Context) {
            val intent = Intent(context, AutoConnectService::class.java).apply {
                action = ACTION_START_AUTO
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun connectNow(context: Context) {
            val intent = Intent(context, AutoConnectService::class.java).apply {
                action = ACTION_CONNECT_NOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            AlarmReceiver.cancelAlarm(context)
            AutoConnectWorker.cancelPeriodicWork(context)
            val intent = Intent(context, AutoConnectService::class.java)
            context.stopService(intent)
        }
    }
}
