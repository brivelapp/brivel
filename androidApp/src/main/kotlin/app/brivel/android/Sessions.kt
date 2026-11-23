package app.brivel.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brivel.app.BrivelApp
import app.brivel.app.OWN_SPORTS
import app.brivel.app.OWN_ZONES
import app.brivel.app.OwnSession
import app.brivel.app.OwnStep
import app.brivel.app.SPORT_LABEL
import app.brivel.app.minutes
import kotlin.math.roundToInt

// Sesiones propias de resistencia (OwnSessions.kt del núcleo), como web/src/Sessions.tsx: los días elegidos sustituyen
// a la sesión de ese deporte del plan; también se puede hacer hoy, solo esta vez

/** Minutos con decimales (30″ = 0,5): se escribe como texto para poder teclear la coma. */
@Composable
private fun MinutesField(label: String, value: Double, modifier: Modifier, onChange: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (value == 0.0) "" else oneDecimal(value)) }
    OutlinedTextField(text, { t ->
        text = t.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5)
        onChange(text.replace(',', '.').toDoubleOrNull() ?: 0.0)
    }, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionEditor(app: BrivelApp, act: Act, start: OwnSession, close: () -> Unit) {
    var s by remember { mutableStateOf(start) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun setStep(i: Int, step: OwnStep) { s = s.copy(steps = s.steps.mapIndexed { j, x -> if (j == i) step else x }) }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.surface) {
        Column(Modifier.imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (start.id.isEmpty()) "Nueva sesión" else "Editar sesión", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(s.name, { s = s.copy(name = it) }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SectionTitle("Deporte")
            ChipRow {
                OWN_SPORTS.forEach { k -> Chip(selected = s.sport == k, label = { Text(SPORT_LABEL[k] ?: k) }, onClick = { s = s.copy(sport = k) }) }
            }
            SectionTitle("Pasos")
            s.steps.forEachIndexed { i, step ->
                // Al quitar un paso, los campos de texto de los siguientes no heredan lo escrito en el quitado
                key(i, s.steps.size) {
                    Module(background = Palette.bg) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Paso ${i + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (s.steps.size > 1) TextButton(onClick = { s = s.copy(steps = s.steps.filterIndexed { j, _ -> j != i }) },
                                modifier = Modifier.semantics { contentDescription = "Quitar el paso ${i + 1}" }) { LinkText("Quitar") }
                        }
                        OutlinedTextField(step.name, { setStep(i, step.copy(name = it)) }, label = { Text("Nombre del paso (opcional)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        Secondary("Intensidad")
                        ChipRow {
                            OWN_ZONES.forEach { (k, label) ->
                                Chip(selected = step.intensityZone == k, label = { Text("$label ($k)") }, onClick = { setStep(i, step.copy(intensityZone = k)) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MinutesField("Minutos", step.minutes, Modifier.weight(1f)) { setStep(i, s.steps[i].copy(minutes = it)) }
                            NumberField("Veces", step.reps, Modifier.weight(1f)) { setStep(i, s.steps[i].copy(reps = it)) }
                            if (step.reps > 1) MinutesField("Descanso (min)", step.restMinutes, Modifier.weight(1f)) { setStep(i, s.steps[i].copy(restMinutes = it)) }
                        }
                    }
                }
            }
            TonalButton(onClick = { s = s.copy(steps = s.steps + OwnStep("", 10.0, "Z2")) }) { Text("Añadir paso") }
            Secondary("Total: ${s.minutes().roundToInt()} min. El pulso o el ritmo de cada paso salen de tus zonas, como en las sesiones del plan.")
            SectionTitle("Qué días la haces")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DAY_LETTERS.forEach { (k, l) ->
                    val on = k in s.days
                    Chip(selected = on, label = { Text(l) }, modifier = Modifier.weight(1f), onClick = { s = s.copy(days = if (on) s.days - k else s.days + k) })
                }
            }
            Secondary("Esos días sustituye a la sesión de ese deporte de tu plan. Sin días, queda guardada para hacerla cuando quieras.")
            error?.let { Text(it, color = Palette.danger) }
            Button(onClick = { attempt({ error = it }) { app.saveOwnSession(s); act.run("Sesión guardada") { close() } } }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
            if (start.id.isNotEmpty()) TextButton(onClick = { confirmDelete = true }) { LinkText("Borrar sesión") }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, containerColor = Palette.surface,
        title = { Text("¿Borrar la sesión «${start.name}»?") },
        confirmButton = { TextButton(onClick = { act.run("Sesión borrada") { app.deleteOwnSession(start.id); close() } }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}

/** En Entrenar: las sesiones propias, hacer una hoy y crear otra. */
@Composable
fun SessionsCard(app: BrivelApp, act: Act, @Suppress("UNUSED_PARAMETER") version: Int) {
    var editing by remember { mutableStateOf<OwnSession?>(null) }
    Module {
        SectionTitle("Tus sesiones")
        Secondary("Monta tus propias sesiones de carrera, bici, natación o caminata, paso a paso. Van al calendario y al reloj como las del plan.")
        app.ownSessions.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { editing = s }.padding(vertical = 6.dp)) {
                    Text(s.name, fontWeight = FontWeight.Bold)
                    Secondary("${SPORT_LABEL[s.sport] ?: s.sport} · ${s.minutes().roundToInt()} min · ${if (s.days.isEmpty()) "sin días fijos" else daysText(s.days)}")
                }
                TextButton(onClick = { act.run("Puesta hoy: la tienes en Hoy") { app.doOwnSessionToday(s.id) } },
                    modifier = Modifier.semantics { contentDescription = "Hacer ${s.name} hoy" }) { LinkText("Hoy") }
            }
        }
        TonalButton(onClick = { editing = OwnSession("", "", "running", listOf(OwnStep("", 30.0, "Z2")), since = "") }, Modifier.fillMaxWidth()) { Text("Nueva sesión") }
    }
    editing?.let { SessionEditor(app, act, it) { editing = null } }
}
