package app.brivel.android

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brivel.app.Metric
import app.brivel.app.Point
import app.brivel.app.ProgressScreen
import app.brivel.app.RecentActivity
import app.brivel.app.BrivelApp
import app.brivel.app.MEASURES
import app.brivel.app.dateLabel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.roundToInt

private val BODY = listOf("sleep", "sleepScore", "deepSleep", "remSleep", "hrv", "restingHR", "spo2", "respiration", "stress", "weight", "bodyFat", "steps")

/** Tu evolución: cómo está tu cuerpo frente a tu rango normal, tu forma y tu actividad (punto 8 de la revisión). */
@Composable
fun ProgressTab(app: BrivelApp, version: Int, act: Act, openProfile: () -> Unit) {
    val screen = remember(version) { app.progress() }
    var openActivity by remember { mutableStateOf<String?>(null) }
    var page by rememberSaveable { mutableStateOf<String?>(null) }  // Medallas y Tus medidas tienen su pantalla (2026-11-23)
    when (page) {
        "medals" -> screen.badges?.let { b -> return SubScreen("Medallas", { page = null }) { BadgesCard(b) } }
        "measures" -> return SubScreen("Tus medidas", { page = null }) { Measures(app, act, screen) }
    }
    openActivity?.let { ActivitySheet(app, act, it) { openActivity = null } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Progreso", subtitle = "Últimos 90 días")
        // Arriba, los cuatro que elige cada uno (decidido el 2026-11-23); debajo, cada parte plegada en una línea
        ProgressTiles(app, version, act)
        if (!screen.linked) Module {
            Secondary("Para ver tu sueño, tu VFC, tu pulso en reposo o tu VO2max, conecta Health Connect " +
                "(los datos de tu reloj o tu báscula en este móvil) o tu cuenta de intervals.icu (gratis) en Perfil → Ajustes.")
            SecondaryButton("Ir a Ajustes", onClick = openProfile)
        }
        screen.badges?.let { b -> RowButton("Medallas", b.next.firstOrNull()?.let { "La más cerca: ${it.title}" } ?: b.nextLevel ?: "") { page = "medals" } }
        val body = screen.metrics.filter { it.key in BODY }
        if (screen.linked && body.isNotEmpty()) Fold("Tu cuerpo", body.take(3).joinToString(", ") { it.label } + if (body.size > 3) "…" else "") {
            Module {
                TitleRow {
                    SectionTitle("Tu cuerpo")
                    Text("último dato · banda = tu rango", fontSize = 12.sp, color = Palette.muted)
                }
                body.forEachIndexed { i, m -> MetricRow(m, first = i == 0) }
            }
        }
        val training = screen.metrics.filter { it.key !in BODY }
        if (screen.linked && (screen.status != null || training.isNotEmpty())) Fold("Forma", screen.status?.label ?: training.take(3).joinToString(", ") { it.label }) {
            Module {
                screen.status?.let { st ->
                    val warn = st.label == "Sobrecarga" || st.label == "Perdiendo forma"
                    SectionTitle("Forma: ${st.label.lowercase()}")
                    screen.garminNote?.let { Source(it) }
                    screen.metrics.firstOrNull { it.key == "ctl" }?.let { Sparkline(it.points, null, null, if (warn) Palette.caution else Palette.ok) }
                    Secondary(st.text)
                }
                training.forEachIndexed { i, m -> MetricRow(m, first = i == 0 && screen.status == null) }
            }
        }
        if (screen.linked) {
            val last = screen.weeklyMinutes.lastOrNull()?.value ?: 0.0
            Fold("Minutos por semana", "Esta semana, ${last.roundToInt()} de 150") {
                Module {
                    TitleRow {
                        SectionTitle("Actividad semanal")
                        Text("${last.roundToInt()} / 150 min", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    ProgressLine((last / 150).toFloat())
                    Secondary("La OMS recomienda al menos 150 minutos moderados (o 75 intensos) a la semana.")
                    Bars(screen.weeklyMinutes, target = 150.0)
                }
            }
        }
        if (screen.predictions.isNotEmpty()) Fold("Predicciones de carrera", screen.predictions.take(2).joinToString(" · ") { "${it.distance} ${it.time}" }) {
            Module {
                SectionTitle("Tiempos previstos")
                screen.garminNote?.let { Source(it) }
                screen.predictions.forEach { p ->
                    Row {
                        Text(p.distance, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(p.time, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                screen.predictionSource?.let { Secondary("Calculado con $it (fórmula de Daniels).") }
            }
        }
        if (screen.recent.isNotEmpty()) Fold("Actividades recientes", screen.recent.first().title) {
            Module {
                SectionTitle("Tus últimos entrenamientos")
                screen.recent.forEach { r -> RecentRow(r) { openActivity = it } }
            }
        }
        if (screen.curves.isNotEmpty()) Fold("Curvas de ritmo y potencia", screen.curves.joinToString(" · ") { it.title }) { Curves(screen.curves) }
        RowButton("Tus medidas", if (screen.measures.isEmpty()) "Apunta tu peso o tus perímetros" else screen.measures.joinToString(", ") { it.label }) { page = "measures" }
        // Sin récords no se abre una pantalla vacía: la fila lo dice y no lleva flecha
        if (screen.strengthRecords.isEmpty() && screen.milestones.isEmpty()) Module {
            Text("Récords y marcas", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Secondary("Saldrán al entrenar")
        } else Fold("Récords y marcas", screen.strengthRecords.firstOrNull()?.title ?: screen.milestones.firstOrNull()?.title) { Milestones(screen) }
        Spacer(Modifier.height(24.dp))
    }
}

/** Los cuatro de arriba; «Editar» abre la lista para elegir otros cuatro. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressTiles(app: BrivelApp, version: Int, act: Act) {
    val tiles = remember(version) { app.progressTiles() }
    var editing by remember { mutableStateOf<List<String>?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { editing = tiles.map { it.key } }) { LinkText("Editar") }
    }
    val pairs = LocalDensity.current.fontScale < 1.5f
    tiles.chunked(if (pairs) 2 else 1).forEach { row ->
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { t ->
                Module(Modifier.weight(1f).fillMaxHeight()) {
                    Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (t.value == null) Secondary(t.empty ?: "") else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(t.value!!, fontFamily = Mono, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                            if (t.unit.isNotEmpty()) Text(" ${t.unit}", fontFamily = Mono, fontSize = 13.sp, color = Palette.muted)
                        }
                        t.detail?.let { Secondary(it) }
                    }
                }
            }
        }
    }
    editing?.let { chosen ->
        ModalBottomSheet(onDismissRequest = { editing = null }, containerColor = Palette.surface) {
            Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Elige 4", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(enabled = chosen.size == 4, onClick = { editing = null; act.run { app.setProgressTiles(chosen) } }) { LinkText("Listo") }
                }
                Secondary("Los cuatro de arriba en Progreso. Lo que no elijas sigue debajo. ${chosen.size} de 4.")
                ChipRow {
                    app.tileChoices().forEach { c ->
                        Chip(selected = c.key in chosen, onClick = {
                            editing = if (c.key in chosen) chosen - c.key else if (chosen.size < 4) chosen + c.key else chosen
                        }, label = { Text(c.title) })
                    }
                }
            }
        }
    }
}

/** Medidas corporales apuntadas a mano (Training.kt): su evolución y el formulario para apuntar la de hoy. */
@Composable
private fun Measures(app: BrivelApp, act: Act, screen: ProgressScreen) {
    var kind by remember { mutableStateOf(MEASURES.keys.first()) }
    var value by remember { mutableStateOf("") }
    Module {
        ChipRow {
            MEASURES.forEach { (k, m) -> Chip(selected = kind == k, label = { Text(m.label) }, onClick = { kind = k }) }
        }
        OutlinedTextField(value, { value = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
            label = { Text("Valor (${MEASURES.getValue(kind).unit})") }, singleLine = true,
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        PrimaryButton("Apuntar", Modifier.fillMaxWidth()) {
            act.run("Medida apuntada") {
                app.logMeasure(kind, value.replace(',', '.').toDoubleOrNull() ?: throw IllegalArgumentException("Escribe un número"))
                value = ""
            }
        }
        if (screen.measures.isNotEmpty()) SectionTitle("Últimas")
        screen.measures.forEachIndexed { i, m -> MetricRow(m, first = i == 0) }
    }
}

/** Con un decimal: en una medida o un 1RM, medio centímetro o medio kilo cuentan. */
internal fun oneDecimal(v: Double?): String = v?.let { ((it * 10).roundToInt() / 10.0).toString().removeSuffix(".0").replace('.', ',') } ?: "—"

/** Mejores marcas, semanas seguidas cumpliendo la OMS y récords de fuerza; los umbrales, aparte (como web/src/Progress.tsx). */
@Composable
private fun Milestones(screen: ProgressScreen) {
    @Composable fun Line(title: String, detail: String?, value: String) = Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            detail?.let { Text(it, fontSize = 12.sp, color = Palette.muted) }
        }
        Spacer(Modifier.width(10.dp))
        Text(value, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, softWrap = false)
    }
    if (screen.milestones.isNotEmpty() || screen.strengthRecords.isNotEmpty()) Module {
        SectionTitle("Tus hitos")
        screen.milestones.forEach { m -> Line(m.title, listOfNotNull(m.date?.let { dateLabel(it) }, m.source).joinToString(" · ").ifEmpty { null }, m.value) }
        if (screen.strengthRecords.isNotEmpty()) Secondary("Lo más pesado que has levantado en cada ejercicio")
        screen.strengthRecords.forEach { m -> Line(m.title, m.date?.let { dateLabel(it) }, m.value) }
    }
    if (screen.thresholds.isNotEmpty()) Module {
        SectionTitle("Tus umbrales")
        Secondary("De intervals.icu: con ellos salen tus zonas. Cámbialos allí si haces un test.")
        screen.thresholds.forEach { t -> Line(t.label, t.explanation, t.value) }
    }
}

/** Un entrenamiento: al tocarlo, lo medido y los minutos en cada zona. */
@Composable
private fun RecentRow(r: RecentActivity, openActivity: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.clickable { open = !open }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SportBadge(r.sport, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                r.source?.let { Source(it) }
                Text(dateLabel(r.date), fontSize = 12.sp, color = Palette.muted)
            }
            Text("${r.minutes}′", fontFamily = Mono, fontSize = 15.sp)
            RowChevron(open)
        }
        if (open) {
            r.own?.let { id -> TextButton(onClick = { openActivity(id) }) { LinkText("Ver la actividad") } }
            r.detail?.let { Secondary(it) }
            if (r.zoneMinutes.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp))) {
                    r.zoneMinutes.forEachIndexed { i, m ->
                        if (m > 0) Box(Modifier.weight(m.toFloat()).fillMaxHeight().background(Palette.zones["Z${minOf(i + 1, 6)}"] ?: Palette.ok))
                    }
                }
                Secondary(r.zoneMinutes.mapIndexedNotNull { i, m -> if (m > 0) "Z${i + 1} $m′" else null }.joinToString(" · "))
            }
        }
    }
}

/** Un indicador: valor, barra frente a tu rango y una frase; al tocarlo, su evolución y qué significa. */
@Composable
private fun MetricRow(m: Metric, first: Boolean) {
    var open by remember { mutableStateOf(false) }
    val low = m.normalLow
    val high = m.normalHigh
    val latest = m.latest
    Column(Modifier.fillMaxWidth().clickable { open = !open }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.chip))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = if (first) 0.dp else 6.dp)) {
            Text(m.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text((if (m.key.startsWith("measure-")) oneDecimal(latest) else format(latest)) + if (m.unit.isNotEmpty()) " ${m.unit}" else "", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            RowChevron(open)
        }
        m.source?.let { Source(it) }
        if (low != null && high != null && latest != null && format(high) != format(low)) {
            val values = m.points.map { it.value }
            val lo = minOf(low, values.min())
            val hi = maxOf(high, values.max())
            val pad = (hi - lo) * 0.15
            val inRange = latest in low..high
            // Fuera de rango hacia el lado bueno (más VO2max, menos pulso en reposo) no es un aviso
            val good = inRange || (m.higherIsBetter == true && latest > high) || (m.higherIsBetter == false && latest < low)
            RangeBar(latest, low, high, lo - pad, hi + pad, good)
            val range = if (low < 0) "${format(low)} a ${format(high)}" else "${format(low)}–${format(high)}"
            Text(
                when {
                    inRange -> "Dentro de tu rango habitual ($range)"
                    latest > high -> "Por encima de tu rango habitual ($range)"
                    else -> "Por debajo de tu rango habitual ($range)"
                },
                fontSize = 12.sp, color = if (good) Palette.muted else Palette.caution,
            )
        }
        if (open) {
            Sparkline(m.points, low, high, Palette.ok)
            Secondary(m.explanation)
        }
    }
}

private fun format(v: Double?): String = when {
    v == null -> "—"
    v >= 20 -> v.roundToInt().toString()
    else -> ((v * 10).roundToInt() / 10.0).toString().replace('.', ',')
}

@Composable
internal fun Sparkline(points: List<Point>, low: Double?, high: Double?, color: Color) {
    if (points.size < 2) return
    val band = Palette.band
    val values = points.map { it.value }
    val min = minOf(values.min(), low ?: values.min())
    val max = maxOf(values.max(), high ?: values.max())
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        fun y(v: Double) = (size.height * (1 - (v - min) / span)).toFloat()
        if (low != null && high != null) {
            drawRect(band, Offset(0f, y(high)), Size(size.width, y(low) - y(high)))
        }
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun Bars(points: List<Point>, target: Double) {
    val max = maxOf(points.maxOfOrNull { it.value } ?: 0.0, target * 1.2)
    val ok = Palette.ok
    val under = Palette.chip
    val line = Palette.muted
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val w = size.width / points.size
        points.forEachIndexed { i, p ->
            val h = (size.height * p.value / max).toFloat()
            drawRoundRect(if (p.value >= target) ok else under, Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h),
                CornerRadius(6f, 6f))
        }
        val ty = (size.height * (1 - target / max)).toFloat()
        drawLine(line, Offset(0f, ty), Offset(size.width, ty), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
    }
}
