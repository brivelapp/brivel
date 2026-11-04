package app.brivel.android

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Aviso de fin de descanso con la app cerrada: se programa al salir de la app con un descanso en marcha y se quita al
// volver (con la app delante ya vibra ella). Alarma exacta si el sistema la permite; si no, la inexacta, que puede
// llegar algo tarde.

private const val CHANNEL = "rest"
private const val ID = 2

private fun restIntent(context: Context) = PendingIntent.getBroadcast(context, 1,
    Intent(context, RestReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

/** Avisa a esa hora (milisegundos), o quita el aviso con null. */
fun scheduleRestAlert(context: Context, at: Long?) {
    val alarms = context.getSystemService(AlarmManager::class.java)
    alarms.cancel(restIntent(context))
    context.getSystemService(NotificationManager::class.java).cancel(ID)
    if (at == null || !canNotify(context)) return
    if (android.os.Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, restIntent(context))
    } else {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, restIntent(context))
    }
}

class RestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Fin del descanso", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        manager.notify(ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Se acabó el descanso")
            .setContentText("Toca la siguiente serie.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60 * 1000L)
            .build())
    }
}
