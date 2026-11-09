package app.brivel.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brivel.app.BrivelApp
import app.brivel.app.EQUIPMENT
import app.brivel.app.ExerciseCard
import app.brivel.app.PATTERN_LABEL

// Biblioteca de ejercicios (Library.kt del núcleo), como web/src/Library.tsx

@Composable
private fun ExerciseRow(card: ExerciseCard, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Palette.band), contentAlignment = Alignment.Center) {
            ExerciseFigure(card.figure, 36.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(card.name, fontWeight = FontWeight.Bold)
            Secondary(detail)
        }
        RowChevron()
    }
}

/**
 * La ficha: patrón, material y claves de técnica. Con `onSwap`, también por cuáles se puede cambiar (mismo patrón) y,
 * si ya es un cambio (`original`), volver al del plan.
 */
@Composable
fun ExerciseSheet(app: BrivelApp, name: String, original: String?, dismiss: () -> Unit, onSwap: ((String) -> Unit)?) {
    val card = remember(name) { app.exerciseCard(name) } ?: return dismiss()
    AlertDialog(onDismissRequest = dismiss, containerColor = Palette.surface,
        title = { Text(card.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Palette.band), contentAlignment = Alignment.Center) {
                        ExerciseFigure(card.figure, 52.dp)
                    }
                    Secondary(listOf(card.patternLabel, card.equipment.joinToString(", "), if (card.timed) "por tiempo" else "por repeticiones").filter { it.isNotEmpty() }.joinToString(" · "))
                }
                card.cues.forEach { Text("• $it", fontSize = 15.sp) }
                Source(card.cueSource)
                if (onSwap != null) {
                    SectionTitle("Cambiar por")
                    original?.let { o -> OutlinedButton(onClick = { onSwap(o) }) { Text("Volver a $o") } }
                    app.swapOptions(original ?: name).filter { it.name != name }.forEach { o ->
                        ExerciseRow(o, o.equipment.joinToString(", ")) { onSwap(o.name) }
                    }
                    Secondary("El cambio vale desde esta sesión; las anteriores siguen con el que hiciste.")
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Cerrar") } })
}

/** En Perfil: todos los ejercicios, con búsqueda y filtros de patrón y de material. */
@Composable
fun LibraryCard(app: BrivelApp) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf<String?>(null) }
    var equipment by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf<String?>(null) }
    Module {
        SectionTitle("Biblioteca de ejercicios")
        Secondary("Busca entre todos los ejercicios por tipo o material.")
        if (!open) OutlinedButton(onClick = { open = true }) { Text("Ver los ejercicios") }
        else {
            OutlinedTextField(query, { query = it }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ChipRow {
                PATTERN_LABEL.forEach { (k, label) ->
                    FilterChip(selected = pattern == k, label = { Text(label) }, onClick = { pattern = if (pattern == k) null else k })
                }
            }
            ChipRow {
                EQUIPMENT.forEach { t ->
                    FilterChip(selected = equipment == t, label = { Text(t) }, onClick = { equipment = if (equipment == t) null else t })
                }
            }
            val list = app.library(query, pattern, equipment)
            if (list.isEmpty()) Secondary("Ningún ejercicio con esos filtros.")
            list.forEach { c -> ExerciseRow(c, c.patternLabel) { shown = c.name } }
        }
    }
    shown?.let { ExerciseSheet(app, it, null, { shown = null }, null) }
}
