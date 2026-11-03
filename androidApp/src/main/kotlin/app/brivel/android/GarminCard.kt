package app.brivel.android

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.brivel.app.BrivelApp
import app.brivel.app.GARMIN_CONSENT
import app.brivel.app.GARMIN_DISCONNECT
import app.brivel.app.GARMIN_HOW
import app.brivel.app.GARMIN_INTRO
import app.brivel.app.GARMIN_TITLE
import app.brivel.app.SYNC_AGE
import app.brivel.app.garminStatus

/**
 * Garmin Connect™ (política de privacidad, apartado 5), como web/src/Garmin.tsx. Conectar pide el consentimiento
 * explícito para datos de salud y la edad mínima, sin marcar; después los datos llegan solos (Root: al abrir y al
 * deslizar hacia abajo). La vuelta desde Garmin llega a MainActivity por brivel://garmin.
 */
@Composable
fun GarminCard(app: BrivelApp, act: Act, @Suppress("UNUSED_PARAMETER") version: Int) {
    var asking by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var adult by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Module {
        SectionTitle(GARMIN_TITLE)
        when {
            app.garminConnected -> {
                Secondary(garminStatus(app.garminSyncedAt()))
                TextButton(onClick = { confirm = true }) { Text("Desconectar Garmin", color = Palette.danger) }
            }
            !asking -> {
                Secondary(GARMIN_INTRO)
                OutlinedButton(onClick = { asking = true }) { Text("Conectar Garmin Connect™") }
            }
            else -> {
                Secondary(GARMIN_HOW)
                Consent(GARMIN_CONSENT, consent) { consent = it }
                Consent(SYNC_AGE, adult) { adult = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = consent && adult, onClick = {
                        act.suspend(null) {
                            val url = app.startGarmin()
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            asking = false
                        }
                    }) { Text("Ir a Garmin") }
                    OutlinedButton(onClick = { asking = false }) { Text("Cancelar") }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(onDismissRequest = { confirm = false },
            title = { Text("¿Desconectar Garmin?") },
            text = { Text(GARMIN_DISCONNECT) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    act.suspend("Garmin desconectado") { app.disconnectGarmin() }
                }) { Text("Desconectar", color = Palette.danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
    }
}
