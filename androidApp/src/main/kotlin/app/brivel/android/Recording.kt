package app.brivel.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brivel.app.BrivelApp
import app.brivel.app.RECORD_SPORTS
import app.brivel.app.SPORT_LABEL
import kotlinx.coroutines.delay

/**
 * Graba con el GPS mientras la pantalla está apagada o la app en segundo plano: un servicio en primer plano con su aviso
 * fijo. Solo entrega los puntos al núcleo (Recorder.kt), que los filtra y los guarda; la ubicación no sale del móvil.
 * Usa el GPS del sistema (LocationManager), sin los servicios de Google.
 */
class RecordService : Service(), LocationListener {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Grabación de actividad", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL).setContentTitle("Brivel está grabando tu actividad")
            .setContentText("Usa el GPS hasta que la termines o la descartes.").setSmallIcon(R.drawable.ic_sport_running)
            .setOngoing(true).setContentIntent(open).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(ID, notification)
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                getSystemService(LocationManager::class.java).requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            } else stopSelf()
        } catch (_: Exception) {
            // Sin permiso o sin GPS: no se graba; la tarjeta de Entrenar lo vuelve a intentar al abrirse
            stopSelf()
        }
        return START_NOT_STICKY  // si el sistema lo cierra, no puede volver solo con la app en segundo plano
    }

    override fun onLocationChanged(l: Location) = Core.get(this).recordPoint(l.time / 1000, l.latitude, l.longitude,
        if (l.hasAltitude()) l.altitude else null, if (l.hasAccuracy()) l.accuracy.toDouble() else 999.0)

    // Sin cuerpo por defecto antes de Android 11
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Solo lo llama Android 9 y 10") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onDestroy() {
        getSystemService(LocationManager::class.java).removeUpdates(this)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "grabacion"
        private const val ID = 41

        fun allowed(context: Context) = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Arranca (o deja como está) el servicio; sin permiso no hace nada. */
        fun start(context: Context) {
            if (allowed(context)) context.startForegroundService(Intent(context, RecordService::class.java))
        }

        fun stop(context: Context) = context.stopService(Intent(context, RecordService::class.java))
    }
}

private fun now() = System.currentTimeMillis() / 1000

/** Entrenar: grabar una carrera, una salida en bici o una caminata con el GPS del móvil. */
@Composable
fun RecordCard(app: BrivelApp, act: Act, version: Int) {
    val context = LocalContext.current
    var tick by remember { mutableLongStateOf(now()) }
    val view = remember(version, tick) { app.recordingView(tick) }
    var sport by remember { mutableStateOf(RECORD_SPORTS.first()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<String?>(null) }
    fun begin() = act.run {
        app.startRecording(sport, now())
        RecordService.start(context)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) begin()
        else act.run { throw IllegalArgumentException("Sin el permiso de ubicación precisa no se puede grabar el recorrido") }
    }
    // Mientras se graba: el tiempo corre cada segundo y el servicio sigue en marcha aunque la app se hubiera cerrado
    LaunchedEffect(view != null) {
        if (view != null) RecordService.start(context)
        while (view != null) { delay(1000); tick = now() }
    }
    opened?.let { ActivitySheet(app, act, it) { opened = null } }
    Module {
        SectionTitle("Grabar con el GPS")
        if (view == null) {
            Secondary("Graba el recorrido, la distancia y el ritmo con el GPS de este móvil. La ubicación solo se usa mientras grabas y " +
                "el recorrido se queda en este móvil: no se sube ni se sincroniza.")
            ChipRow {
                RECORD_SPORTS.forEach { s -> FilterChip(selected = sport == s, label = { Text(SPORT_LABEL[s] ?: s) }, onClick = { sport = s }) }
            }
            PrimaryButton("Empezar a grabar", Modifier.fillMaxWidth()) {
                if (RecordService.allowed(context)) begin()
                else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        } else {
            Text("${SPORT_LABEL[view.sport] ?: view.sport}${if (view.paused) " · en pausa" else ""}", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("Tiempo" to view.time, "Distancia" to view.distance, (if (view.sport == "cycling") "Velocidad" else "Ritmo") to view.pace).forEach { (label, value) ->
                    Column {
                        Text(label, fontSize = 12.sp, color = Palette.muted)
                        Text(value, fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (!view.hasSignal) Secondary("Buscando la señal del GPS: sal a cielo abierto. La distancia empieza a contar con el primer punto.")
            else if (!RecordService.allowed(context)) Secondary("Brivel ya no tiene permiso de ubicación: actívalo en los ajustes del móvil para seguir grabando.", Palette.caution)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { act.run { if (view.paused) app.resumeRecording(now()) else app.pauseRecording(now()) } }, modifier = Modifier.weight(1f)) {
                    Text(if (view.paused) "Seguir" else "Pausar")
                }
                PrimaryButton("Terminar", Modifier.weight(1f)) {
                    act.run("Actividad guardada") {
                        opened = app.finishRecording()
                        RecordService.stop(context)
                    }
                }
            }
            TextButton(onClick = { confirmDiscard = true }) { LinkText("Descartar la grabación") }
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, containerColor = Palette.surface,
        title = { Text("¿Descartar la grabación?") }, text = { Text("Se borra lo grabado hasta ahora.") },
        confirmButton = { TextButton(onClick = {
            confirmDiscard = false
            act.run("Grabación descartada") { app.discardRecording(); RecordService.stop(context) }
        }) { Text("Descartar") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancelar") } })
}
