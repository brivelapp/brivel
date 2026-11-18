package app.brivel.android

// Piezas del perfil (lienzo de Pulso, opción B, 2026-11-03), compartidas por el Perfil y la bienvenida: deportes sin
// «Caminar» (siempre cuenta), la semana como barras que se suben y bajan con el dedo, fuerza, nivel y año de nacimiento.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brivel.app.MAX_DAY_MINUTES
import app.brivel.app.PICKABLE_CARDIO
import app.brivel.app.WEEKDAY_LABEL
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
    }
}

/**
 * La semana, un día por fila: deslizar de 15 en 15 o escribir los minutos (de 0 a 5 h; sin minutos, ese día queda
 * libre). En horizontal, desplazar la pantalla no mueve nada, y escribir la cifra evita afinar con el dedo (con las
 * barras verticales costaba acertar en 45). `onCommit` llega al soltar el deslizador o con cada cifra escrita.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekBars(days: Map<String, Int>, onCommit: (Map<String, Int>) -> Unit) {
    var live by remember(days) { mutableStateOf(days) }
    fun put(d: String, m: Int) = (live + (d to m.coerceIn(0, MAX_DAY_MINUTES))).also { live = it }
    val width = rememberTextWidth()
    val nameStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val fieldStyle = LocalTextStyle.current.copy(fontFamily = Mono, textAlign = TextAlign.End)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Las medidas salen del texto con la letra de la persona: con 84 dp fijos, «Miércoles» se partía en dos
        val nameWidth = DAYS.maxOf { width(WEEKDAY_LABEL[it]!!, nameStyle) }
        val fieldWidth = maxOf(72.dp, width("300", fieldStyle) + 32.dp)  // el campo deja 16 dp a cada lado de la cifra
        val unitWidth = width("min", LocalTextStyle.current.copy(fontSize = 14.sp))
        // El deslizador necesita sitio para el dedo; si no queda, el nombre del día va encima de su fila
        val stacked = maxWidth - nameWidth - fieldWidth - unitWidth - 24.dp < 96.dp
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Secondary(if (live.values.count { it > 0 } == 1) "1 día" else "${live.values.count { it > 0 }} días")
                Text(weekTimeText(live), fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Palette.ok)
            }
            DAYS.forEach { d ->
                val m = live[d] ?: 0
                val name = WEEKDAY_LABEL[d]!!
                var text by remember(m) { mutableStateOf(if (m == 0) "" else "$m") }
                if (stacked) Text(name, Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (m == 0) Palette.muted else Palette.text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!stacked) Text(name, Modifier.width(nameWidth), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (m == 0) Palette.muted else Palette.text)
                    Slider(m.toFloat(), { put(d, (it / 15).roundToInt() * 15) }, Modifier.weight(1f).semantics {
                        contentDescription = name
                        stateDescription = if (m == 0) "libre" else "$m minutos"
                    }, valueRange = 0f..MAX_DAY_MINUTES.toFloat(), steps = MAX_DAY_MINUTES / 15 - 1,
                        onValueChangeFinished = { onCommit(live) },
                        // Pista fina y punto pequeño con aro (muestra A, 2026-11-17), igual en web e iOS
                        thumb = { Box(Modifier.size(20.dp).background(Palette.surface, CircleShape).border(3.dp, Palette.ok, CircleShape)) },
                        track = { state -> SliderDefaults.Track(state, Modifier.height(6.dp), drawStopIndicator = null, thumbTrackGapSize = 0.dp,
                            colors = SliderDefaults.colors(activeTrackColor = Palette.ok, inactiveTrackColor = Palette.chip,
                                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)) })
                    OutlinedTextField(text, { t ->
                        text = t.filter { it.isDigit() }.take(3)
                        onCommit(put(d, text.toIntOrNull() ?: 0))
                    }, Modifier.width(fieldWidth).semantics { contentDescription = "Minutos el ${name.lowercase()}" },
                        placeholder = { Text("—") }, singleLine = true,
                        textStyle = fieldStyle,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Secondary("min")
                }
            }
            Secondary("Desliza o escribe los minutos de cada día, hasta 5 h. Sin minutos, ese día queda libre.")
        }
    }
}

/**
 * Opciones cortas en una sola fila (fuerza y nivel en el Perfil). Si con la letra grande del sistema una palabra no cabe
 * entera en su hueco («Gimna-sio»), van una debajo de otra.
 */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val width = rememberTextWidth()
    val style = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = (maxWidth - 8.dp - 4.dp * (options.size - 1)) / options.size
        // Partir en dos líneas por un espacio («Sin / fuerza») se lee bien; por media palabra, no
        val stacked = options.any { (_, label) -> label.split(' ').any { width(it, style) + 16.dp > cell } }
        val group = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.chip).padding(4.dp).selectableGroup()
        if (stacked) Column(group, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (key, label) -> SegmentOption(label, key == selected, Modifier.fillMaxWidth()) { onSelect(key) } }
        } else Row(group, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (key, label) -> SegmentOption(label, key == selected, Modifier.weight(1f)) { onSelect(key) } }
        }
    }
}

@Composable
private fun SegmentOption(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) =
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
            .background(if (on) Palette.ok else Color.Transparent)
            .selectable(on, role = Role.RadioButton, onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) Palette.onOk else Palette.text, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 14.sp, textAlign = TextAlign.Center, style = LocalTextStyle.current.copy(hyphens = Hyphens.None))
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
