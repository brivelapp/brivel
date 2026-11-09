package app.brivel.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brivel.app.SYNC_ACTIVE
import app.brivel.app.SYNC_AGE
import app.brivel.app.SYNC_CONSENT
import app.brivel.app.SYNC_INTRO
import app.brivel.app.SYNC_JOIN_NOTE
import app.brivel.app.SYNC_NO_RECOVERY
import app.brivel.app.BrivelApp

private enum class SyncView { IDLE, CREATE, JOIN }

/** El código en grupos de 3 para leerlo y copiarlo sin errores. */
private fun grouped(code: String) = code.chunked(3).joinToString(" ")

@Composable
internal fun Consent(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // Toda la fila marca la casilla: el texto es largo y tocar solo el cuadro cuesta
    Row(Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = onChange), verticalAlignment = Alignment.Top) {
        Checkbox(checked, null, colors = CheckboxDefaults.colors(checkedColor = Palette.ok), modifier = Modifier.padding(12.dp))
        Text(text, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

/**
 * Usar en otros dispositivos (docs/SINCRONIZACION.md), como web/src/Sync.tsx. Activar pide el consentimiento explícito
 * para datos de salud (RGPD art. 9.2.a) y la edad mínima, sin marcar. `allowCreate`: false en la bienvenida, donde
 * solo tiene sentido unirse a una copia que ya existe.
 */
@Composable
fun SyncCard(app: BrivelApp, act: Act, @Suppress("UNUSED_PARAMETER") version: Int, allowCreate: Boolean = true) {
    var view by remember { mutableStateOf(SyncView.IDLE) }
    var consent by remember { mutableStateOf(false) }
    var adult by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var showCode by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Module {
        val code = app.syncCode
        when {
            code != null -> {
                SectionTitle("Sincronización activada")
                Secondary(SYNC_ACTIVE)
                if (showCode) {
                    SelectionContainer { Text(grouped(code), fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                    Secondary("Escríbelo en el otro dispositivo, en «Usar en otros dispositivos» → «Tengo un código».")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { act.syncNow() }) { Text("Sincronizar ahora") }
                    OutlinedButton(onClick = { showCode = !showCode }) { Text(if (showCode) "Ocultar código" else "Ver código") }
                }
                TextButton(onClick = { act.run("Este móvil ya no sincroniza. La copia sigue en el servidor") { app.forgetSync() } }) {
                    LinkText("Dejar de sincronizar aquí")
                }
                TextButton(onClick = { confirmDelete = true }) { LinkText("Borrar la copia del servidor", color = Palette.danger) }
            }
            view == SyncView.IDLE -> {
                SectionTitle(if (allowCreate) "Usar en otros dispositivos" else "¿Ya usas Brivel en otro dispositivo?")
                Secondary(if (allowCreate) SYNC_INTRO else "Únete con su código y tendrás aquí los mismos datos.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (allowCreate) Button(onClick = { view = SyncView.CREATE }) { Text("Activar") }
                    OutlinedButton(onClick = { view = SyncView.JOIN }) { Text("Tengo un código") }
                }
            }
            else -> {
                val joining = view == SyncView.JOIN
                SectionTitle(if (joining) "Unirse con un código" else "Activar la sincronización")
                if (joining) {
                    OutlinedTextField(typed, { typed = it }, label = { Text("Código del otro dispositivo") }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 18.sp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth())
                    Secondary(SYNC_JOIN_NOTE)
                }
                Consent(SYNC_CONSENT, consent) { consent = it }
                Consent(SYNC_AGE, adult) { adult = it }
                if (!joining) Secondary(SYNC_NO_RECOVERY)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = consent && adult && (!joining || typed.isNotBlank()), onClick = {
                        if (joining) act.suspend("Sincronizado: se han juntado los datos de este móvil con los de la copia") {
                            app.joinSync(typed)
                            view = SyncView.IDLE
                        } else act.suspend("Activada. Apunta el código: lo necesitas para añadir otro dispositivo") {
                            app.createSync()
                            view = SyncView.IDLE
                            showCode = true
                        }
                    }) { Text(if (joining) "Unirse" else "Activar") }
                    OutlinedButton(onClick = { view = SyncView.IDLE }) { Text("Cancelar") }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar la copia del servidor?") },
            text = { Text("Los demás dispositivos dejarán de sincronizar. Los datos de este móvil se quedan.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    act.suspend("Copia borrada del servidor. Tus datos siguen en este móvil") { app.deleteSync() }
                }) { Text("Borrar", color = Palette.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
    }
}
