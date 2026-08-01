package fr.gcu.jardsurmer.autoconnect.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppState {
    private const val PREFS = "gcu_app_state_v4"
    private const val KEY_ENABLED = "auto_reconnect_enabled"
    private const val KEY_SAVE_CREDENTIALS = "save_credentials_enabled"
    private const val KEY_LAST_STATUS = "last_status_message"
    private const val KEY_LAST_RECONNECT_TIME = "last_reconnect_timestamp"
    private const val KEY_PROBE_INTERVAL = "probe_interval_seconds"

    private val liveEnabled = MutableLiveData<Boolean>()
    private val liveStatus = MutableLiveData<String>()
    private val liveLastReconnect = MutableLiveData<String>()
    private val liveIsConnected = MutableLiveData<Boolean>()
    private val liveProbeInterval = MutableLiveData<Int>()

    fun init(context: Context) {
        val enabled = isEnabled(context)
        liveEnabled.postValue(enabled)
        val lastMsg = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_STATUS, "Initialisation…") ?: "Initialisation…"
        liveStatus.postValue(lastMsg)
        val lastTime = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_RECONNECT_TIME, "--:--") ?: "--:--"
        liveLastReconnect.postValue(lastTime)
        liveProbeInterval.postValue(getProbeIntervalSeconds(context))
    }

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        liveEnabled.postValue(enabled)
    }

    fun isSaveCredentials(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SAVE_CREDENTIALS, true)
    }

    fun setSaveCredentials(context: Context, save: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SAVE_CREDENTIALS, save).apply()
    }

    fun getProbeIntervalSeconds(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PROBE_INTERVAL, 5)
    }

    fun setProbeIntervalSeconds(context: Context, seconds: Int) {
        val validSecs = if (seconds in listOf(5, 10, 15, 30)) seconds else 5
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PROBE_INTERVAL, validSecs).apply()
        liveProbeInterval.postValue(validSecs)
    }

    fun getStatus(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_STATUS, "Prêt") ?: "Prêt"
    }

    fun setStatus(context: Context, message: String, isConnected: Boolean = false) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_STATUS, message).apply()
        liveStatus.postValue(message)
        liveIsConnected.postValue(isConnected)
        if (isConnected) {
            val formatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST_RECONNECT_TIME, formatted).apply()
            liveLastReconnect.postValue(formatted)
        }
    }

    fun getLastReconnectTime(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_RECONNECT_TIME, "--:--") ?: "--:--"
    }

    fun getLiveEnabled(): LiveData<Boolean> = liveEnabled
    fun getLiveStatus(): LiveData<String> = liveStatus
    fun getLiveLastReconnect(): LiveData<String> = liveLastReconnect
    fun getLiveIsConnected(): LiveData<Boolean> = liveIsConnected
    fun getLiveProbeInterval(): LiveData<Int> = liveProbeInterval
}
