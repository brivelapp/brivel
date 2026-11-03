package app.brivel.android

// Piezas del perfil (lienzo de Pulso, opción B, 2026-11-03), compartidas por el Perfil y la bienvenida: deportes sin
// «Caminar» (siempre cuenta), la semana como barras que se suben y bajan con el dedo, fuerza, nivel y año de nacimiento.

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brivel.app.MAX_DAY_MINUTES
import app.brivel.app.PICKABLE_CARDIO
import app.brivel.app.WEEKDAY_LABEL
import app.brivel.app.WEEKDAY_SHORT
import app.brivel.app.toggleCardio
import app.brivel.app.weekTimeText
import kotlin.math.roundToInt

private val SPORT_LABEL = mapOf("running" to "Correr", "cycling" to "Bici", "swimming_pool" to "Nadar")
val STRENGTH_CHOICES = listOf(
    Triple("gym", "Gimnasio", "Con máquinas, barras y mancuernas"),
    Triple("bodyweight", "En casa", "Con tu peso y poco material"),
    Triple("none", "Sin fuerza", "Solo resistencia"),
)
val LEVEL_CHOICES = listOf(
    Triple("beginner", "Empiezo", "Hace meses que no entreno"),
    Triple("intermediate", "A veces", "Una o dos veces por semana"),
    Triple("advanced", "A menudo", "Tres o más veces por semana"),
)
private val DAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

/** Correr, bici y nadar como pastillas con icono. Caminar no aparece: siempre cuenta. */
@Composable
fun SportPicker(cardio: List<String>, onChange: (List<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChipRow {
            PICKABLE_CARDIO.forEach { sport ->
                val on = sport in cardio
                val color = if (on) Palette.onOk else Palette.text
                Row(
                    Modifier.heightIn(min = 48.dp).clip(CircleShape).background(if (on) Palette.ok else Palette.chip)
                        .toggleable(on, role = Role.Checkbox) { onChange(toggleCardio(cardio, sport)) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Glyph(sportIcon(sport), size = 20.dp, tint = color)
                    Text(SPORT_LABEL[sport]!!, color = color, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
        Secondary("Caminar siempre cuenta: no hace falta marcarlo.")
    }
}

/**
 * La semana como barras: tocar o arrastrar a una altura pone esos minutos, de 15 en 15; abajo del todo, ese día queda
 * libre. Para arrastrar hay que mantener pulsado (si no, deslizar la pantalla movería las barras). `onCommit` llega
 * al soltar, no en cada movimiento. Con TalkBack cada barra se sube y baja como un deslizador.
 */
@Composable
fun WeekBars(days: Map<String, Int>, editable: Boolean = true, onCommit: (Map<String, Int>) -> Unit = {}) {
    var live by remember(days) { mutableStateOf(days) }
    val commit by rememberUpdatedState(onCommit)
    val area = 240.dp  // la barra más alta (5 h: cada paso de 15 son 12 dp); encima van los minutos
    val haptic = LocalHapticFeedback.current
    // Un toque suave en cada paso de 15 minutos, para notarlos sin mirar
    fun set(d: String, v: Int) {
        if ((live[d] ?: 0) == v) return
        live = live + (d to v)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val empty = Palette.chip
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Secondary(if (live.values.count { it > 0 } == 1) "1 día" else "${live.values.count { it > 0 }} días")
            Text(weekTimeText(live), fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Palette.ok)
        }
        run {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DAYS.forEach { d ->
                    val m = live[d] ?: 0
                    val color = if (m == 0) Palette.muted else Palette.text
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(area + 22.dp)
                                // Toda la columna responde al dedo. Solo donde no hay que desplazar la pantalla (hoja y bienvenida)
                                .then(if (!editable) Modifier else Modifier.pointerInput(d) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        set(d, minutesAt(down.position.y, size.height))
                                        down.consume()
                                        drag(down.id) { set(d, minutesAt(it.position.y, size.height)); it.consume() }
                                        commit(live)
                                    }
                                })
                                .semantics {
                                    contentDescription = WEEKDAY_LABEL[d]!!
                                    stateDescription = if (m == 0) "libre" else "$m minutos"
                                    progressBarRangeInfo = ProgressBarRangeInfo(m.toFloat(), 0f..MAX_DAY_MINUTES.toFloat(), MAX_DAY_MINUTES / 15 - 1)
                                    if (editable) setProgress { v ->
                                        live = live + (d to ((v / 15).roundToInt() * 15).coerceIn(0, MAX_DAY_MINUTES))
                                        commit(live)
                                        true
                                    }
                                },
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            // Los minutos, justo encima de la barra
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (m == 0) "—" else "$m", fontFamily = Mono, fontSize = 12.sp, color = color)
                                if (m == 0) Box(Modifier.fillMaxWidth().height(12.dp).drawBehind {
                                    drawRoundRect(empty, cornerRadius = CornerRadius(10.dp.toPx()),
                                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                                })
                                else Box(Modifier.fillMaxWidth().height(area * m / MAX_DAY_MINUTES).clip(RoundedCornerShape(10.dp)).background(Palette.ok))
                            }
                        }
                        Text(WEEKDAY_SHORT[d]!!, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
                    }
                }
            }
        }
        if (editable) Secondary("Toca o arrastra cada barra hasta los minutos que tienes, hasta 5 h. Abajo del todo, ese día queda libre.")
    }
}

// La barra mide el alto menos los 22 dp de los minutos: el dedo marca su tope. De 15 en 15, de 0 a MAX_DAY_MINUTES
private fun androidx.compose.ui.unit.Density.minutesAt(y: Float, height: Int): Int =
    ((height - y) / (height - 22.dp.toPx()) * MAX_DAY_MINUTES / 15).roundToInt().times(15).coerceIn(0, MAX_DAY_MINUTES)

/** Opciones cortas en una sola fila (fuerza y nivel en el Perfil). */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.chip).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) Palette.ok else Color.Transparent)
                    .selectable(on, role = Role.RadioButton) { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Palette.onOk else Palette.text, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

/** Opciones con explicación, una por tarjeta (fuerza y nivel en la bienvenida). */
@Composable
fun ChoiceCards(options: List<Triple<String, String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (key, title, detail) ->
            val on = key == selected
            Module(Modifier.selectable(on, role = Role.RadioButton) { onSelect(key) },
                accent = if (on) Palette.ok else null, background = if (on) Palette.okSoft else Palette.surface) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Secondary(detail)
            }
        }
    }
}

@Composable
fun BirthYearField(year: Int?, label: String? = "Año de nacimiento", onChange: (Int?) -> Unit) {
    OutlinedTextField(year?.toString() ?: "", { v -> onChange(v.filter { it.isDigit() }.take(4).toIntOrNull()) },
        label = label?.let { { Text(it) } }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}
