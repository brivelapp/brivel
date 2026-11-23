package app.brivel.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.brivel.app.BrivelApp

/** Entrenar (docs/VISION.md §5), como web/src/Train.tsx: empezar un entreno sin plan, tus rutinas y la biblioteca. */
@Composable
fun TrainTab(app: BrivelApp, version: Int, act: Act) {
    val workout = remember(version) { app.workoutToday }
    val routines = remember(version) { app.routines }
    val sessions = remember(version) { app.ownSessions.size }
    val activities = remember(version) { app.activities().size }
    var confirmDiscard by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }  // «Grabar con GPS» abre su parte
    fun start(routineId: String?) = act.run { act.openStrength(app.startWorkout(routineId).date) }
    fun count(n: Int, one: String, many: String) = when (n) { 0 -> "Ninguna todavía"; 1 -> "1 $one"; else -> "$n $many" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Entrenar", subtitle = "Por tu cuenta")
        // Por secciones (2026-11-23): arriba, las dos píldoras de lo que se hace ya; debajo, lo tuyo plegado en una línea
        ChipRow {
            PrimaryButton(if (workout != null) "Seguir con la fuerza" else "Entrenar fuerza") {
                if (workout != null) act.openStrength(workout.date) else start(null)
            }
            SecondaryButton("Grabar con GPS") { recording = true }
        }
        if (workout != null) Module {
            Text("Hoy: ${workout.name}", fontWeight = FontWeight.Bold)
            Secondary(if (workout.exercises.size == 1) "1 ejercicio" else "${workout.exercises.size} ejercicios")
            TextButton(onClick = { confirmDiscard = true }) { LinkText("Descartar el entreno") }
        }
        GroupTitle("Lo tuyo")
        Fold("Grabar con GPS", "Carrera, bici o caminata, desde el móvil", startOpen = recording) { RecordCard(app, act, version) }
        Fold("Sesiones", count(sessions, "sesión", "sesiones") + " · carrera, bici, natación") { SessionsCard(app, act, version) }
        Fold("Rutinas de fuerza", count(routines.size, "rutina", "rutinas") + " · sustituyen a la del plan") {
            if (workout == null) routines.forEach { r ->
                TonalButton(onClick = { start(r.id) }, modifier = Modifier.fillMaxWidth()) { Text("Empezar «${r.name}» hoy") }
            }
            RoutinesCard(app, act, version)
        }
        Fold("Actividades", count(activities, "actividad", "actividades") + " · subir FIT, GPX o TCX") { ActivitiesCard(app, act, version) }
        GroupTitle("Consultar")
        Fold("Biblioteca de ejercicios", "Busca por zona del cuerpo o material") { LibraryCard(app) }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmDiscard) DiscardWorkoutDialog({ confirmDiscard = false }) {
        confirmDiscard = false
        act.run("Entreno descartado: hoy vuelve a tu plan") { app.discardWorkout() }
    }
}

/** Descartar borra también las series apuntadas hoy en ese entreno: se pregunta antes. */
@Composable
fun DiscardWorkoutDialog(dismiss: () -> Unit, confirm: () -> Unit) =
    AlertDialog(onDismissRequest = dismiss, containerColor = Palette.surface,
        title = { Text("¿Descartar el entreno de hoy?") },
        text = { Text("Se borran también las series que has apuntado en él.") },
        confirmButton = { TextButton(onClick = confirm) { Text("Descartar") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
