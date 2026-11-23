package app.brivel.android

// Hoy a gusto de cada uno (decidido el 2026-11-23), como Widgets.tsx y TodayWidgets.swift: debajo de la sesión, widgets
// medios (dos por fila) o anchos. Mantener pulsado o «⋯» abren su menú: Medio, Ancho, Ordenar los widgets y Quitar de
// Hoy (opción B). «Añadir a Hoy» enseña los dos tamaños antes de ponerlo (opción C). El orden es de este móvil.

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brivel.app.BrivelApp
import app.brivel.app.Reason
import app.brivel.app.TodayScreen
import app.brivel.app.WidgetChoice
import app.brivel.app.WidgetView

@Composable
fun TodayWidgets(app: BrivelApp, version: Int, act: Act, screen: TodayScreen) {
    val widgets = remember(version) { app.todayWidgets() }
    var reordering by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    // Con letra muy grande, dos por fila no caben: cada uno a lo ancho (regla de tamaños del 2026-11-18)
    val pairs = LocalDensity.current.fontScale < 1.5f
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (reordering) TextButton(onClick = { reordering = false }) { LinkText("Listo") }
        else TextButton(onClick = { adding = true }) { LinkText("Añadir a Hoy") }
    }
    if (widgets.isEmpty()) Secondary("Sin widgets. Con «Añadir a Hoy» pones los que quieras.")
    // Filas: un ancho solo; dos medios seguidos, juntos
    var i = 0
    while (i < widgets.size) {
        val w = widgets[i]
        val next = widgets.getOrNull(i + 1)
        if (!w.wide && pairs && next != null && !next.wide) {
            val first = i
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { WidgetCard(app, act, screen, w, first, widgets.size, reordering) { reordering = true } }
                Box(Modifier.weight(1f)) { WidgetCard(app, act, screen, next, first + 1, widgets.size, reordering) { reordering = true } }
            }
            i += 2
        } else {
            val at = i
            if (!w.wide && pairs) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { WidgetCard(app, act, screen, w, at, widgets.size, reordering) { reordering = true } }
                Spacer(Modifier.weight(1f))
            } else WidgetCard(app, act, screen, w, at, widgets.size, reordering) { reordering = true }
            i++
        }
    }
    if (adding) AddWidgetSheet(app, act) { adding = false }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WidgetCard(app: BrivelApp, act: Act, screen: TodayScreen, w: WidgetView, index: Int, count: Int,
                       reordering: Boolean, startReorder: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Module(Modifier
            .then(if (reordering) Modifier.border(2.dp, Palette.muted, RoundedCornerShape(24.dp)) else Modifier)
            .combinedClickable(onClick = {}, onLongClick = { menu = true }, onLongClickLabel = "Opciones de ${w.title}")
            .semantics { contentDescription = w.title }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (reordering) {
                    TextButton(onClick = { act.run { app.moveWidget(w.key, index - 1) } }, enabled = index > 0) { Text("↑", fontSize = 18.sp) }
                    TextButton(onClick = { act.run { app.moveWidget(w.key, index + 1) } }, enabled = index < count - 1) { Text("↓", fontSize = 18.sp) }
                } else Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { menu = true }
                    .semantics { contentDescription = "Opciones de ${w.title}" }, contentAlignment = Alignment.Center) {
                    Text("⋯", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.muted)
                }
            }
            when (w.key) {
                "readiness" -> ReadinessWidget(act, screen.askReadiness)
                "week" -> WeekWidget(screen, w.wide)
                else -> ValueWidget(w)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Palette.surface,
            modifier = Modifier.widthIn(min = 220.dp)) {
            for ((wide, label) in listOf(false to "Medio", true to "Ancho")) {
                DropdownMenuItem(
                    text = { Text(label, fontWeight = if (w.wide == wide) FontWeight.Bold else FontWeight.Normal, color = if (w.wide == wide) Palette.ok else Palette.text) },
                    leadingIcon = { SizeShape(wide, if (w.wide == wide) Palette.ok else Palette.text) },
                    trailingIcon = { if (w.wide == wide) Text("✓", color = Palette.ok) },
                    onClick = { menu = false; act.run { app.setWidgetWide(w.key, wide) } })
            }
            DropdownMenuItem(text = { Text("Ordenar los widgets") }, onClick = { menu = false; startReorder() })
            DropdownMenuItem(text = { Text("Quitar de Hoy", color = Palette.danger) },
                onClick = { menu = false; act.run("${w.title}: quitado de Hoy") { app.removeWidget(w.key) } })
        }
    }
}

/** Los dos tamaños dibujados: un cuadrado (medio) o un rectángulo largo (ancho). */
@Composable
private fun SizeShape(wide: Boolean, color: androidx.compose.ui.graphics.Color) =
    Box(Modifier.width(if (wide) 30.dp else 14.dp).height(12.dp).border(2.dp, color, RoundedCornerShape(4.dp)))

@Composable
private fun ColumnScope.ValueWidget(w: WidgetView) {
    if (w.value == null) { Secondary(w.empty ?: ""); return }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(w.value!!, fontFamily = Mono, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        if (w.unit.isNotEmpty()) Text(" ${w.unit}", fontFamily = Mono, fontSize = 13.sp, color = Palette.muted)
    }
    w.detail?.let { Secondary(it) }
}

@Composable
private fun ColumnScope.WeekWidget(screen: TodayScreen, wide: Boolean) {
    WeekDots(screen.weekDots)
    // Sin plan, la fuerza hecha frente a los 2 días; con plan, los minutos de cardio y la fuerza hecha
    if (screen.noPlan) {
        Secondary("${screen.strengthDone} de 2 días de fuerza")
        if (screen.strengthDone >= 2) Text("Cumples los 2 días de fuerza", color = Palette.ok, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    } else {
        Secondary("Cardio ${screen.weekMinutes} de 150 min")
        Secondary(screen.strengthLine)
    }
    if (wide) {
        screen.warnings.forEach { ReasonRow(Reason(it, null)) }
        screen.info.forEach { Secondary(it) }
    } else if (screen.warnings.isNotEmpty()) {
        Secondary(if (screen.warnings.size == 1) "1 aviso: ábrelo en ancho" else "${screen.warnings.size} avisos: ábrelo en ancho", Palette.caution)
    }
}

/** Sueño, energía y agujetas de 1 a 5: sin reloj, ajustan la sesión de hoy. Se abren al tocar «Responder». */
@Composable
private fun ColumnScope.ReadinessWidget(act: Act, ask: Boolean) {
    var open by remember { mutableStateOf(false) }
    var sleep by remember { mutableStateOf(3) }
    var energy by remember { mutableStateOf(3) }
    var soreness by remember { mutableStateOf(3) }
    when {
        !ask -> Secondary("Hoy ya está ajustado con tus respuestas o con los datos de tu reloj.")
        !open -> TonalButton(onClick = { open = true }, Modifier.fillMaxWidth()) { Text("Responder 3 preguntas") }
        else -> {
            Secondary("Si dormiste mal o estás cargado, la sesión de hoy será más suave.")
            for ((label, value, set) in listOf(
                Triple("Sueño", sleep) { v: Int -> sleep = v },
                Triple("Energía", energy) { v: Int -> energy = v },
                Triple("Agujetas o molestias", soreness) { v: Int -> soreness = v },
            )) {
                Text(label, fontSize = 14.sp)
                Segmented(RATINGS, value.toString()) { set(it.toInt()) }
            }
            PrimaryButton("Ajustar hoy") { act.run { act.app.answerReadiness(sleep, energy, soreness) } }
        }
    }
}

// Del 1 al 5, con el mismo selector que el Perfil
private val RATINGS = (1..5).map { n -> "$n" to when (n) { 1 -> "1 mal"; 5 -> "5 bien"; else -> "$n" } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWidgetSheet(app: BrivelApp, act: Act, close: () -> Unit) {
    var picked by remember { mutableStateOf<WidgetChoice?>(null) }
    val choices = remember { app.widgetChoices().filterNot { it.inUse } }
    ModalBottomSheet(onDismissRequest = close, containerColor = Palette.surface) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val p = picked
            Text(p?.title ?: "Añadir a Hoy", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            when {
                p != null -> {
                    Secondary("${p.description}. Elige el tamaño:")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for ((wide, label, weight) in listOf(Triple(false, "Medio · dos por fila", 1f), Triple(true, "Ancho · toda la fila", 2f))) {
                            Column(Modifier.weight(weight).heightIn(min = 96.dp).clip(RoundedCornerShape(18.dp))
                                .border(1.dp, Palette.chip, RoundedCornerShape(18.dp))
                                .clickable { close(); act.run("${p.title}: añadido a Hoy") { app.addWidget(p.key, wide) } }
                                .padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Text(p.title, fontWeight = FontWeight.Bold)
                                Secondary(label)
                            }
                        }
                    }
                    TextButton(onClick = { picked = null }) { LinkText("Elegir otro") }
                }
                choices.isEmpty() -> Secondary("Ya tienes todos en Hoy.")
                else -> choices.forEach { c ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { picked = c }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title, fontWeight = FontWeight.SemiBold)
                            Secondary(c.description)
                        }
                        RowChevron()
                    }
                }
            }
        }
    }
}
