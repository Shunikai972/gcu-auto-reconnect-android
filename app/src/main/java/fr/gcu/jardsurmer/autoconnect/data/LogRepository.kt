package fr.gcu.jardsurmer.autoconnect.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import fr.gcu.jardsurmer.autoconnect.model.DiagnosticInfo
import fr.gcu.jardsurmer.autoconnect.model.LogEntry
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

object LogRepository {
    private const val PREFS_LOGS = "gcu_logs_v1"
    private const val KEY_ENTRIES = "entries_json"
    private const val MAX_LOGS = 100
    private const val MAX_DIAGNOSTICS = 50

    private val logList = CopyOnWriteArrayList<LogEntry>()
    private val diagnosticList = CopyOnWriteArrayList<DiagnosticInfo>()
    private val liveLogs = MutableLiveData<List<LogEntry>>()

    fun init(context: Context) {
        synchronized(logList) {
            if (logList.isEmpty()) {
                loadFromDisk(context)
            }
        }
    }

    fun getLiveLogs(): LiveData<List<LogEntry>> = liveLogs

    fun getLogs(): List<LogEntry> = logList.toList()

    fun getDiagnostics(): List<DiagnosticInfo> = diagnosticList.toList()

    fun addLog(context: Context, entry: LogEntry) {
        logList.add(0, entry)
        while (logList.size > MAX_LOGS) {
            logList.removeAt(logList.size - 1)
        }
        liveLogs.postValue(logList.toList())
        saveToDisk(context)
    }

    fun addDiagnostic(diagnostic: DiagnosticInfo) {
        diagnosticList.add(0, diagnostic)
        while (diagnosticList.size > MAX_DIAGNOSTICS) {
            diagnosticList.removeAt(diagnosticList.size - 1)
        }
    }

    fun clear(context: Context) {
        logList.clear()
        diagnosticList.clear()
        liveLogs.postValue(emptyList())
        try {
            context.getSharedPreferences(PREFS_LOGS, Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Throwable) {}
    }

    private fun saveToDisk(context: Context) {
        try {
            val array = JSONArray()
            val snapshot = logList.take(MAX_LOGS)
            for (entry in snapshot) {
                val obj = JSONObject()
                obj.put("id", entry.id)
                obj.put("timestamp", entry.timestamp)
                obj.put("message", entry.message)
                obj.put("isSuccess", entry.isSuccess)
                obj.put("isWaiting", entry.isWaiting)
                obj.put("challenge", entry.challenge ?: "")
                obj.put("preloginOk", entry.preloginOk)
                obj.put("formOk", entry.formOk)
                obj.put("postOk", entry.postOk)
                obj.put("logonOk", entry.logonOk)
                obj.put("http204Ok", entry.http204Ok)
                obj.put("detail", entry.detail ?: "")
                array.put(obj)
            }
            context.getSharedPreferences(PREFS_LOGS, Context.MODE_PRIVATE)
                .edit().putString(KEY_ENTRIES, array.toString()).apply()
        } catch (_: Throwable) {}
    }

    private fun loadFromDisk(context: Context) {
        try {
            val json = context.getSharedPreferences(PREFS_LOGS, Context.MODE_PRIVATE)
                .getString(KEY_ENTRIES, null) ?: return
            val array = JSONArray(json)
            val loaded = mutableListOf<LogEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                loaded.add(
                    LogEntry(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        message = obj.optString("message", ""),
                        isSuccess = obj.optBoolean("isSuccess", false),
                        isWaiting = obj.optBoolean("isWaiting", false),
                        challenge = obj.optString("challenge").ifEmpty { null },
                        preloginOk = obj.optBoolean("preloginOk", false),
                        formOk = obj.optBoolean("formOk", false),
                        postOk = obj.optBoolean("postOk", false),
                        logonOk = obj.optBoolean("logonOk", false),
                        http204Ok = obj.optBoolean("http204Ok", false),
                        detail = obj.optString("detail").ifEmpty { null }
                    )
                )
            }
            logList.clear()
            logList.addAll(loaded)
            liveLogs.postValue(logList.toList())
        } catch (_: Throwable) {}
    }
}
