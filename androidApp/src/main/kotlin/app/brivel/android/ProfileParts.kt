package app.brivel.android

// Piezas del perfil (lienzo de Pulso, opción B, 2026-11-03), compartidas por el Perfil y la bienvenida: deportes sin
// «Caminar» (siempre cuenta), la semana como barras que se suben y bajan con el dedo, fuerza, nivel y año de nacimiento.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.TextButton
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.BasicTextField
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

internal val CARDIO_LABEL = mapOf("running" to "Correr", "cycling" to "Bici", "swimming_pool" to "Nadar")  // las pastillas del Perfil
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
                    Text(CARDIO_LABEL[sport]!!, color = color, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

/** La semana con − y + de 15 en 15 minutos (decidido el 2026-11-23: solo botones, sin deslizador); el número se puede escribir. */
@Composable
fun WeekBars(days: Map<String, Int>, onCommit: (Map<String, Int>) -> Unit) {
    var live by remember(days) { mutableStateOf(days) }
    fun put(d: String, m: Int) { live = live + (d to m.coerceIn(0, MAX_DAY_MINUTES)); onCommit(live) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Secondary(if (live.values.count { it > 0 } == 1) "1 día" else "${live.values.count { it > 0 }} días")
            Text(weekTimeText(live), fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Palette.ok)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.surface)) {
            DAYS.forEachIndexed { i, d ->
                val m = live[d] ?: 0
                val name = WEEKDAY_LABEL[d]!!
                var text by remember(m) { mutableStateOf(if (m == 0) "" else "$m") }
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.chip))
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, Modifier.weight(1f), fontWeight = if (m > 0) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp,
                        color = if (m == 0) Palette.muted else Palette.text)
                    StepButton("−", "Menos minutos el ${name.lowercase()}", m > 0) { put(d, m - 15) }
                    BasicTextField(text, { v -> text = v.filter(Char::isDigit).take(3); put(d, text.toIntOrNull() ?: 0) },
                        modifier = Modifier.width(56.dp).semantics { contentDescription = "Minutos el ${name.lowercase()}" },
                        textStyle = LocalTextStyle.current.copy(fontFamily = Mono, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center, color = Palette.text),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                        decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { if (text.isEmpty()) Text("—", color = Palette.muted, fontFamily = Mono); inner() } })
                    StepButton("+", "Más minutos el ${name.lowercase()}", m < MAX_DAY_MINUTES) { put(d, m + 15) }
                }
            }
        }
    }
}

@Composable
private fun StepButton(sign: String, description: String, enabled: Boolean, onClick: () -> Unit) =
    Box(Modifier.size(40.dp).clip(CircleShape).background(Palette.chip.copy(alpha = if (enabled) 1f else 0.4f))
        .clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Text(sign, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (enabled) Palette.text else Palette.muted)
    }

/** Lista de opciones con ✓ (una o varias): lo elegido se ve sin depender del color. */
@Composable
fun CheckList(options: List<Triple<String, String, String?>>, selected: Collection<String>, onToggle: (String) -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.surface)) {
        options.forEachIndexed { i, (key, label, hint) ->
            val on = key in selected
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.chip))
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(on, role = androidx.compose.ui.semantics.Role.Checkbox) { onToggle(key) }
                .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    hint?.let { Secondary(it) }
                }
                if (on) Text("✓", color = Palette.ok, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
        }
    }

/** Una pantalla propia dentro de una pestaña: título una vez, arriba, con «‹» para volver (y el atrás del sistema). */
@Composable
fun SubScreen(title: String, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.activity.compose.BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back, modifier = Modifier.semantics { contentDescription = "Volver" }) { Text("‹", fontSize = 26.sp, color = Palette.text) }
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        content()
        Spacer(Modifier.height(24.dp))
    }
}

/** Una fila que abre su propia pantalla: título, valor y ›. */
@Composable
fun RowButton(title: String, value: String, onClick: () -> Unit) = Module(Modifier.clickable(onClick = onClick)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Secondary(value)
        }
        RowChevron()
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
