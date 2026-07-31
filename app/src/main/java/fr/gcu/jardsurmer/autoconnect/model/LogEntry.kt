package fr.gcu.jardsurmer.autoconnect.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val isSuccess: Boolean,
    val isWaiting: Boolean = false,
    val challenge: String? = null,
    val preloginOk: Boolean = false,
    val formOk: Boolean = false,
    val postOk: Boolean = false,
    val logonOk: Boolean = false,
    val http204Ok: Boolean = false,
    val detail: String? = null
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formattedDateTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun summaryText(): String {
        val sb = StringBuilder()
        sb.append("[").append(formattedTime()).append("] ")
        if (isSuccess) {
            sb.append("🟢 ").append(message)
        } else if (isWaiting) {
            sb.append("🟡 ").append(message)
        } else {
            sb.append("🔴 ").append(message)
        }
        challenge?.let { sb.append("\n  - Challenge: ").append(it) }
        sb.append("\n  - Prelogin: ").append(if (preloginOk) "OK" else "KO")
        sb.append(" | Form: ").append(if (formOk) "OK" else "KO")
        sb.append(" | POST: ").append(if (postOk) "OK" else "KO")
        sb.append(" | Logon: ").append(if (logonOk) "OK" else "KO")
        sb.append(" | HTTP204: ").append(if (http204Ok) "OK" else "KO")
        detail?.let { sb.append("\n  - Détail: ").append(it) }
        return sb.toString()
    }
}
