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
import androidx.compose.material3.OutlinedButton
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
    var confirmDiscard by remember { mutableStateOf(false) }
    fun start(routineId: String?) = act.run { act.openStrength(app.startWorkout(routineId).date) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Entrenar", subtitle = "Por tu cuenta")
        Module {
            SectionTitle("Entreno libre")
            if (workout != null) {
                Text("Hoy: ${workout.name}", fontWeight = FontWeight.Bold)
                Secondary(if (workout.exercises.size == 1) "1 ejercicio" else "${workout.exercises.size} ejercicios")
                PrimaryButton("Continuar", Modifier.fillMaxWidth()) { act.openStrength(workout.date) }
                TextButton(onClick = { confirmDiscard = true }) { LinkText("Descartar el entreno") }
            } else {
                Secondary("Entrena sin plan: elige los ejercicios sobre la marcha y apunta cada serie. Hoy sustituye a la fuerza de tu plan.")
                PrimaryButton("Empezar entreno vacío", Modifier.fillMaxWidth()) { start(null) }
                remember(version) { app.routines }.forEach { r ->
                    OutlinedButton(onClick = { start(r.id) }, modifier = Modifier.fillMaxWidth()) { Text("Empezar «${r.name}» hoy") }
                }
            }
        }
        RoutinesCard(app, act, version)
        LibraryCard(app)
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
