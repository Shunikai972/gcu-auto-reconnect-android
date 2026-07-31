package fr.gcu.jardsurmer.autoconnect.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import fr.gcu.jardsurmer.autoconnect.data.AppState

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!AppState.isEnabled(context)) return

        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GCUAuto:AlarmReceiverWakeLock")
        wakeLock?.acquire(60000L)

        try {
            AutoConnectService.connectNow(context)
        } finally {
            scheduleNextAlarm(context)
            wakeLock?.let { if (it.isHeld) it.release() }
        }
    }

    companion object {
        const val ACTION_ALARM_TICK = "fr.gcu.jardsurmer.autoconnect.ALARM_TICK"
        private const val REQUEST_CODE = 9988

        fun scheduleNextAlarm(context: Context) {
            if (!AppState.isEnabled(context)) return

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_ALARM_TICK
            }

            val flags = if (Build.VERSION.SDK_INT >= 23) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)

            // Compute next 10-minute slot (e.g. :00, :10, :20, :30, :40, :50)
            val now = System.currentTimeMillis()
            val tenMinutesMs = 10 * 60 * 1000L
            val remainder = now % tenMinutesMs
            var nextTrigger = now + (tenMinutesMs - remainder)
            if (nextTrigger - now < 30000L) { // ensure at least 30s gap
                nextTrigger += tenMinutesMs
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
                }
            } catch (_: SecurityException) {
                // Fallback for Android 12+ if exact alarms not permitted
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
            } catch (_: Throwable) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
            }
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_ALARM_TICK
            }
            val flags = if (Build.VERSION.SDK_INT >= 23) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
            alarmManager.cancel(pendingIntent)
        }
    }
}
