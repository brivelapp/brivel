package app.brivel.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brivel.app.BrivelApp
import app.brivel.app.CustomExercise
import app.brivel.app.PATTERN_LABEL
import app.brivel.app.Routine
import app.brivel.app.RoutineExercise
import app.brivel.app.WEEKDAY_LABEL

// Rutinas propias (Routines.kt del núcleo), como web/src/Routines.tsx: esos días sustituyen a la fuerza del plan

private val DAYS = listOf("MON" to "L", "TUE" to "M", "WED" to "X", "THU" to "J", "FRI" to "V", "SAT" to "S", "SUN" to "D")

private fun daysText(days: List<String>) =
    if (days.isEmpty()) "sin días: no entra en el plan"
    else DAYS.filter { it.first in days }.joinToString(", ") { WEEKDAY_LABEL.getValue(it.first).lowercase() }

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) =
    OutlinedTextField(if (value == 0) "" else value.toString(), { t -> onChange(t.filter(Char::isDigit).take(3).toIntOrNull() ?: 0) },
        label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineEditor(app: BrivelApp, act: Act, start: Routine, close: () -> Unit) {
    var r by remember { mutableStateOf(start) }
    var picking by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val all = remember { app.library("", null, null) }
    fun timed(name: String) = all.firstOrNull { it.name == name }?.timed == true
    fun setEx(i: Int, e: RoutineExercise) { r = r.copy(exercises = r.exercises.mapIndexed { j, x -> if (j == i) e else x }) }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.surface) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (start.id.isEmpty()) "Nueva rutina" else "Editar rutina", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(r.name, { r = r.copy(name = it) }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SectionTitle("Qué días la haces")
            ChipRow {
                DAYS.forEach { (k, l) ->
                    val on = k in r.days
                    FilterChip(selected = on, label = { Text(l) }, onClick = { r = r.copy(days = if (on) r.days - k else r.days + k) })
                }
            }
            Secondary("Esos días sustituye a la fuerza de tu plan. Un día solo puede tener una rutina.")
            SectionTitle("Ejercicios")
            r.exercises.forEachIndexed { i, e ->
                Module(background = Palette.bg) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { r = r.copy(exercises = r.exercises.filterIndexed { j, _ -> j != i }) }) { LinkText("Quitar") }
                    }
                    val unit = if (timed(e.name)) "s" else "reps"
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("Series", e.sets, Modifier.weight(1f)) { setEx(i, e.copy(sets = it)) }
                        NumberField("Desde ($unit)", e.low, Modifier.weight(1f)) { setEx(i, e.copy(low = it)) }
                        NumberField("Hasta ($unit)", e.high, Modifier.weight(1f)) { setEx(i, e.copy(high = it)) }
                    }
                }
            }
            OutlinedButton(onClick = { picking = true }) { Text("Añadir ejercicio") }
            SectionTitle("Reserva al acabar cada serie")
            ChipRow {
                (0..5).forEach { n ->
                    FilterChip(selected = r.rir == n, label = { Text(if (n == 0) "Hasta no poder más" else "$n") }, onClick = { r = r.copy(rir = n) })
                }
            }
            Secondary("Brivel te propone el peso con lo que apuntes, sin subir nunca más de un 10 % de una vez.")
            Button(onClick = { act.run("Rutina guardada") { app.saveRoutine(r); close() } }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
            if (start.id.isNotEmpty()) TextButton(onClick = { confirmDelete = true }) { LinkText("Borrar rutina") }
        }
    }
    if (picking) AlertDialog(onDismissRequest = { picking = false }, containerColor = Palette.surface,
        title = { Text("Añadir ejercicio") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                app.library(query, null, null).groupBy { it.patternLabel }.forEach { (group, cards) ->
                    SectionTitle(group)
                    cards.forEach { c ->
                        Text(c.name, modifier = Modifier.fillMaxWidth().clickable {
                            r = r.copy(exercises = r.exercises + if (c.timed) RoutineExercise(c.name, 3, 20, 40) else RoutineExercise(c.name, 3, 8, 12))
                            picking = false
                        }.padding(vertical = 10.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { picking = false }) { Text("Cerrar") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, containerColor = Palette.surface,
        title = { Text("¿Borrar la rutina «${start.name}»?") },
        confirmButton = { TextButton(onClick = { act.run("Rutina borrada") { app.deleteRoutine(start.id); close() } }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}

@Composable
private fun NewExercise(app: BrivelApp, act: Act, close: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf(PATTERN_LABEL.keys.first()) }
    var timed by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = close, containerColor = Palette.surface, title = { Text("Nuevo ejercicio") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SectionTitle("Tipo")
                ChipRow { PATTERN_LABEL.forEach { (k, l) -> FilterChip(selected = pattern == k, label = { Text(l) }, onClick = { pattern = k }) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Va por tiempo, no por repeticiones", modifier = Modifier.weight(1f))
                    Switch(timed, { timed = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { act.run("Ejercicio guardado") { app.saveCustomExercise(CustomExercise(name, pattern, timed)); close() } }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancelar") } })
}

/** En Perfil: las rutinas propias y el botón para crear otra. */
@Composable
fun RoutinesCard(app: BrivelApp, act: Act, @Suppress("UNUSED_PARAMETER") version: Int) {
    var editing by remember { mutableStateOf<Routine?>(null) }
    var adding by remember { mutableStateOf(false) }
    Module {
        SectionTitle("Tus rutinas")
        Secondary("Crea tu propia rutina de fuerza y elige qué días la haces. Esos días sustituye a la fuerza del plan.")
        app.routines.forEach { r ->
            Row(Modifier.fillMaxWidth().clickable { editing = r }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontWeight = FontWeight.Bold)
                    Secondary("${r.exercises.size} ejercicios · ${daysText(r.days)}")
                }
                RowChevron()
            }
        }
        OutlinedButton(onClick = { editing = Routine("", "", emptyList(), emptyList(), since = "") }) { Text("Nueva rutina") }
        SectionTitle("Tus ejercicios")
        Secondary("Si falta alguno en la biblioteca, créalo y úsalo en tus rutinas.")
        app.customExercises.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.name, modifier = Modifier.weight(1f))
                TextButton(onClick = { act.run("Ejercicio borrado") { app.deleteCustomExercise(e.name) } }) { LinkText("Borrar") }
            }
        }
        OutlinedButton(onClick = { adding = true }) { Text("Nuevo ejercicio") }
    }
    if (adding) NewExercise(app, act) { adding = false }
    editing?.let { RoutineEditor(app, act, it) { editing = null } }
}
