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
import fr.gcu.jardsurmer.autoconnect.data.DnsResolver
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
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
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
                setStatus("Auto-reconnexion active. Connexion immédiate en cours…", isConnected = false)
                AlarmReceiver.scheduleNextAlarm(this)
                AutoConnectWorker.schedulePeriodicWork(this)
                triggerAttempt(oneShot = false, forceRelogin = true, reason = "activation immédiate")
                return START_STICKY
            }
            ACTION_CONNECT_NOW -> {
                AppState.setEnabled(this, true)
                setStatus("Connexion immédiate en cours…", isConnected = false)
                AlarmReceiver.scheduleNextAlarm(this)
                AutoConnectWorker.schedulePeriodicWork(this)
                triggerAttempt(oneShot = false, forceRelogin = true, reason = "demande manuelle")
                return START_STICKY
            }
            else -> {
                if (AppState.isEnabled(this)) {
                    AlarmReceiver.scheduleNextAlarm(this)
                    AutoConnectWorker.schedulePeriodicWork(this)
                    triggerAttempt(oneShot = false, forceRelogin = true, reason = "redémarrage du service")
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
            var lastBoundaryMinute = -1
            while (isActive) {
                val intervalSecs = AppState.getProbeIntervalSeconds(this@AutoConnectService).coerceIn(3, 60)
                delay(intervalSecs * 1000L)

                if (!AppState.isEnabled(this@AutoConnectService)) continue

                val now = System.currentTimeMillis()
                val currentMinute = ((now / 60000L) % 10).toInt()

                // 1. Check if we are at a 10-minute clock boundary (e.g. :00, :10, :20, :30, :40, :50)
                if (currentMinute == 0 && lastBoundaryMinute != 0) {
                    lastBoundaryMinute = 0
                    AlarmReceiver.scheduleNextAlarm(this@AutoConnectService)
                    triggerAttempt(oneShot = false, forceRelogin = true, reason = "tranche 10 min")
                    continue
                } else if (currentMinute != 0) {
                    lastBoundaryMinute = currentMinute
                }

                // 2. Active Probe & Wi-Fi Range check
                val wifi = NetworkInspector.findWifiNetwork(this@AutoConnectService)
                if (wifi != null && NetworkInspector.isGcuCandidate(this@AutoConnectService, wifi)) {
                    val online = isWifiOnline(wifi)
                    if (!online) {
                        triggerAttempt(oneShot = false, forceRelogin = true, reason = "Wi-Fi GCU à portée / déconnexion")
                    }
                }
            }
        }
    }

    private fun isWifiOnline(wifi: Network): Boolean {
        return try {
            val dnsResolver = DnsResolver(this, wifi)
            val client = OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .dns(dnsResolver)
                .socketFactory(wifi.socketFactory)
                .followRedirects(false)
                .build()
            PortalLoginClient.isOnline(client)
        } catch (_: Throwable) {
            false
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
                        triggerAttempt(oneShot = false, forceRelogin = true, reason = "Wi-Fi GCU connecté")
                    }
                }

                override fun onLost(network: Network) {
                    if (NetworkInspector.findWifiNetwork(this@AutoConnectService) == null) {
                        setStatus("Wi-Fi absent. Recherche du Wi-Fi GCU à portée…", isConnected = false)
                    }
                }
            }
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (_: Throwable) {
            setStatus("Surveillance Wi-Fi active (contrôle régulier).", isConnected = false)
        }
    }

    private fun triggerAttempt(oneShot: Boolean, forceRelogin: Boolean, reason: String) {
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
                val result = PortalLoginClient.login(this@AutoConnectService, wifi, credentials, forceRelogin = forceRelogin)

                val statusMessage = if (reason.isNotEmpty() && reason != "activation") {
                    "${result.message} ($reason)"
                } else {
                    result.message
                }

                when (result) {
                    is LoginResult.Success -> {
                        setStatus(statusMessage, isConnected = true)
                    }
                    is LoginResult.Failure -> {
                        setStatus(statusMessage, isConnected = false)
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
