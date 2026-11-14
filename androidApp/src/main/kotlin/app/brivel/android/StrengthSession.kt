package app.brivel.android

import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brivel.app.BrivelApp
import app.brivel.app.FIGURES
import app.brivel.app.LoggedSet
import app.brivel.app.SET_KINDS
import app.brivel.app.StrengthExercise
import app.brivel.app.kg
import app.brivel.app.polylines
import kotlinx.coroutines.delay

// Sesión de fuerza a pantalla completa: Serie (una cada vez, números grandes) y Sesión (toda la sesión en tabla)

/** Figura del patrón de movimiento: cuerpo en el color del texto y, en verde, lo que trabaja. */
@Composable
fun ExerciseFigure(pattern: String, size: Dp) {
    val figure = FIGURES[pattern] ?: return
    val body = Palette.text
    val work = Palette.ok
    Canvas(Modifier.size(size)) {
        val k = this.size.width / 48f
        fun draw(d: String, color: Color, width: Float) = polylines(d).forEach { line ->
            val path = Path().apply {
                moveTo(line[0].first * k, line[0].second * k)
                line.drop(1).forEach { (x, y) -> lineTo(x * k, y * k) }
            }
            drawPath(path, color, style = Stroke(width * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        draw(figure.body, body, 2.2f)
        draw(figure.work, work, 3f)
        drawCircle(body, 3f * k, Offset(figure.headX * k, figure.headY * k), style = Stroke(2.2f * k))
    }
}

private fun amount(e: StrengthExercise, reps: Int) = if (e.timed) "$reps″" else "$reps"
private fun weightText(w: Double?) = w?.let { kg(it).removeSuffix(" kg") } ?: "Sin peso"
private fun effort(rir: Int) = if (rir == 0) "Hasta no poder más" else "Termina con $rir en reserva"
private fun setText(s: LoggedSet, timed: Boolean) =
    (s.weightKg?.let { "${kg(it).removeSuffix(" kg")} × " } ?: "") + if (timed) "${s.reps}″" else "${s.reps}"

/** Al girar el móvil o cambiar de modo, la sesión sigue en la misma serie y con el mismo descanso. */
@Composable
fun StrengthSession(app: BrivelApp, date: String, version: Int, act: Act, close: () -> Unit) {
    val screen = remember(version) { app.strengthScreen(date) } ?: return close()
    val exercises = screen.exercises
    BackHandler(onBack = close)
    // La pantalla no se apaga entre series
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    fun firstPending() = exercises.indexOfFirst { it.done.size < it.sets }
    var mode by rememberSaveable { mutableStateOf("set") }
    var current by rememberSaveable { mutableIntStateOf(firstPending().coerceAtLeast(0)) }
    var setNo by rememberSaveable { mutableIntStateOf((exercises.getOrNull(current)?.done?.size ?: 0) + 1) }
    var restEnd by rememberSaveable { mutableLongStateOf(0L) }
    var restTotal by rememberSaveable { mutableIntStateOf(0) }
    var logged by remember { mutableStateOf<Pair<StrengthExercise, LoggedSet>?>(null) }  // la última serie apuntada
    // Tras "Serie hecha", el descanso a pantalla; al abrir una serie de la tabla, la serie y el descanso abajo
    var restView by rememberSaveable { mutableStateOf(false) }  // la serie recién apuntada, en el descanso
    var picking by remember { mutableStateOf(false) }  // entreno libre: eligiendo un ejercicio que añadir
    var confirmDiscard by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<String?>(null) }  // ejercicio con series que se va a quitar

    /** La serie que toca según el núcleo (alterna las superseries); sin ninguna, la sesión completa. */
    fun goNext(from: Int): Boolean {
        val next = app.nextSet(date, from)
        if (next != null) { current = next.exercise; setNo = next.set }
        else {
            val fresh = app.strengthScreen(date)?.exercises ?: emptyList()
            current = from.coerceIn(0, (fresh.size - 1).coerceAtLeast(0))
            setNo = (fresh.getOrNull(from)?.sets ?: 0) + 1
        }
        return next != null
    }
    // Entreno libre: cada cambio vuelve a buscar la serie que toca (puede haber una nueva, o haberse ido la actual)
    fun edit(success: String? = null, block: () -> Unit) { act.run(success, block); goNext(current) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(restEnd) {
        while (restEnd > 0) {
            now = System.currentTimeMillis()
            if (now >= restEnd) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); restEnd = 0 }
            delay(250)
        }
    }
    val restLeft = if (restEnd > 0) ((restEnd - now + 999) / 1000).toInt().coerceAtLeast(0) else 0
    // Con la app cerrada, el móvil avisa al acabar el descanso; al volver, el aviso pendiente se quita
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (restEnd > System.currentTimeMillis()) scheduleRestAlert(context, restEnd) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { scheduleRestAlert(context, null) }
    DisposableEffect(Unit) { onDispose { scheduleRestAlert(context, null) } }
    var notifyOk by remember { mutableStateOf(canNotify(context)) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifyOk = it }

    Column(Modifier.fillMaxSize().background(Palette.bg)) {
        Column(Modifier.padding(horizontal = 18.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close, modifier = Modifier.semantics { contentDescription = "Cerrar la sesión" }) {
                    Glyph(R.drawable.ic_chevron_left, 24.dp)
                }
                Text(screen.title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("${exercises.sumOf { it.done.size }}/${exercises.sumOf { it.sets }} series", fontFamily = Mono, fontSize = 13.sp, color = Palette.muted)
            }
            Segmented(listOf("set" to "Serie", "session" to "Sesión"), mode) { mode = it }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val e = exercises.getOrNull(current)
            when {
                screen.free && exercises.isEmpty() -> Module { Secondary("Tu entreno está vacío. Añade el primer ejercicio y apunta cada serie.") }
                mode == "session" -> {
                    screen.notes?.let { Secondary(it) }  // calentamiento y esfuerzo, como en la tarjeta de Hoy
                    exercises.forEachIndexed { i, ex ->
                        SessionRows(ex, i == current, setNo, extra = {
                            if (screen.free) {
                                Row {
                                    TextButton(onClick = { edit { app.changeWorkoutSets(ex.name, 1) } }) { LinkText("Otra serie") }
                                    if (ex.sets > maxOf(1, ex.done.size)) TextButton(onClick = { edit { app.changeWorkoutSets(ex.name, -1) } }) { LinkText("Una serie menos") }
                                    TextButton(onClick = {
                                        if (ex.done.isEmpty()) edit("Ejercicio quitado") { app.removeWorkoutExercise(ex.name) } else removing = ex.name
                                    }, modifier = Modifier.semantics { contentDescription = "Quitar ${ex.name}" }) { LinkText("Quitar") }
                                }
                                if (i > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("En superserie con el anterior", fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Switch(ex.superset != null && exercises[i - 1].superset == ex.superset, { on -> edit { app.linkWorkoutExercise(ex.name, on) } })
                                }
                            }
                        }) { set ->
                            current = i; setNo = set; mode = "set"; restView = false
                        }
                    }
                }
                restEnd > 0 && restView -> {
                    if (!notifyOk && Build.VERSION.SDK_INT >= 33) TextButton(onClick = { askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        LinkText("Avisarme al acabar el descanso si salgo de la app")
                    }
                    RestView(restLeft, restTotal, e, setNo, logged,
                        adjust = { restEnd += it * 1000L; if (restEnd <= System.currentTimeMillis()) restEnd = 0 },
                        skip = { restEnd = 0 },
                        rate = { rir ->
                            logged?.let { (ex, s) ->
                                act.run { app.logSet(date, ex.name, s.set, s.weightKg, s.reps, rir, s.kind) }
                                logged = ex to s.copy(rir = rir)
                            }
                        })
                }
                e == null || setNo > e.sets && firstPending() < 0 -> Finished(screen.other, close)
                else -> SetView(app, e, setNo, exercises.getOrNull(current + 1),
                    swap = { r: String -> act.run { app.swapExercise(date, e.name, r) } }
                        .takeIf { !screen.free && app.canSwapExercises(date) && e.done.isEmpty() }) { weight, reps, kind ->
                    val editing = e.done.firstOrNull { it.set == setNo }
                    act.run { app.logSet(date, e.name, setNo, weight, reps, editing?.rir, kind) }
                    logged = e to LoggedSet(setNo, weight, reps, editing?.rir, kind)
                    val more = goNext(current)
                    // En una superserie no se descansa hasta acabar la ronda (restSeconds 0)
                    if (editing == null && more && e.restSeconds > 0) {
                        restTotal = e.restSeconds
                        restEnd = System.currentTimeMillis() + e.restSeconds * 1000L
                        restView = true
                    }
                }
            }
            if (screen.free) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Añadir ejercicio") { picking = true }
                if (mode == "session") TextButton(onClick = { confirmDiscard = true }) { LinkText("Descartar el entreno") }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (restEnd > 0 && (mode == "session" || !restView)) RestBar(restLeft, restTotal)
    }
    if (picking) ExercisePicker(app, { picking = false }) { name ->
        picking = false
        restView = false
        edit { app.addWorkoutExercise(name) }
    }
    removing?.let { name ->
        AlertDialog(onDismissRequest = { removing = null }, containerColor = Palette.surface,
            title = { Text("¿Quitar $name y sus series de hoy?") },
            confirmButton = { TextButton(onClick = { removing = null; edit("Ejercicio quitado") { app.removeWorkoutExercise(name) } }) { Text("Quitar") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } })
    }
    if (confirmDiscard) DiscardWorkoutDialog({ confirmDiscard = false }) {
        confirmDiscard = false
        act.run("Entreno descartado: hoy vuelve a tu plan") { app.discardWorkout() }
        close()
    }
}

@Composable
private fun SetView(app: BrivelApp, e: StrengthExercise, setNo: Int, next: StrengthExercise?, swap: ((String) -> Unit)?, done: (Double?, Int, String?) -> Unit) {
    var sheet by remember { mutableStateOf<String?>(null) }  // "card" o "swap"
    val today = e.done.firstOrNull { it.set == setNo }
    val previous = today ?: e.previous.firstOrNull { it.set == setNo }
    // El peso de partida: al corregir, el de esa serie; si no, el de la última serie de trabajo de hoy, el propuesto o
    // el de la última vez
    val lastToday = e.done.lastOrNull { it.kind != "warmup" && it.kind != "drop" }
    var weight by remember(e.name, setNo) { mutableStateOf(if (today != null) today.weightKg else lastToday?.weightKg ?: e.weightKg ?: previous?.weightKg) }
    var kind by remember(e.name, setNo) { mutableStateOf(today?.kind) }
    // Con el mismo peso, las repeticiones de la última vez; si cambia el peso, el mínimo del rango (progresión doble)
    var reps by remember(e.name, setNo) { mutableIntStateOf(e.done.firstOrNull { it.set == setNo }?.reps
        ?: previous?.takeIf { it.weightKg == e.weightKg }?.reps ?: e.repsLow) }
    var typing by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..maxOf(e.sets, e.done.size)).forEach { n ->
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                .background(if (e.done.any { it.set == n }) Palette.ok else Palette.chip))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Palette.band), contentAlignment = Alignment.Center) {
            ExerciseFigure(e.pattern, 52.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(e.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp)
            Secondary("Serie $setNo de ${e.sets} · ${e.repsLow}–${e.repsHigh} ${if (e.timed) "segundos" else "repeticiones"}")
            e.rir?.let { Text(effort(it), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            e.original?.let { Secondary("En lugar de $it") }
            e.superset?.let { Secondary("Superserie $it: alterna sus ejercicios y descansa al acabar la ronda") }
        }
    }
    Row {
        TextButton(onClick = { sheet = "card" }) { LinkText("Cómo se hace") }
        if (swap != null) TextButton(onClick = { sheet = "swap" }) { LinkText("Cambiar ejercicio") }
    }
    sheet?.let { s ->
        ExerciseSheet(app, e.name, e.original, { sheet = null }, swap?.takeIf { s == "swap" }?.let { f -> { r: String -> sheet = null; f(r) } })
    }
    e.suggestion?.let {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.okSoft).padding(12.dp)) {
            Text(it, fontSize = 14.sp)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stepper("kg", weightText(weight), Modifier.weight(1f), onTap = { typing = true },
            minus = { weight = weight?.let { w -> (w - 2.5).takeIf { it > 0 } } },
            plus = { weight = (weight ?: 0.0) + 2.5 })
        Stepper(if (e.timed) "segundos" else "repeticiones", amount(e, reps), Modifier.weight(1f),
            minus = { reps = (reps - if (e.timed) 5 else 1).coerceAtLeast(1) },
            plus = { reps = (reps + if (e.timed) 5 else 1).coerceAtMost(600) })
    }
    if (e.barbell) weight?.let { w -> app.plates(w)?.let { Secondary(it) } }
    // Tipo de serie: las de calentamiento y las descendentes se suman a las del plan y no cuentan para subir el peso
    ChipRow {
        FilterChip(selected = kind == null, label = { Text("Normal") }, onClick = { kind = null })
        SET_KINDS.forEach { (k, label) -> FilterChip(selected = kind == k, label = { Text(label) }, onClick = { kind = k }) }
    }
    PrimaryButton(if (e.done.any { it.set == setNo }) "Guardar cambio" else "Serie hecha", Modifier.fillMaxWidth()) { done(weight, reps, kind) }
    previous?.takeIf { e.done.none { it.set == setNo } }?.let { Secondary("La última vez: ${setText(it, e.timed)}") }
    next?.let {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Secondary("Después: ")
            Text(it.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
    }
    if (typing) WeightDialog(weight, { typing = false }) { weight = it; typing = false }
}

@Composable
private fun Stepper(label: String, value: String, modifier: Modifier, onTap: (() -> Unit)? = null, minus: () -> Unit, plus: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Palette.surface).padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Secondary(label)
        // Misma altura con "Sin peso" que con un número, para que las dos tarjetas queden parejas
        Box(Modifier.height(48.dp).then(if (onTap != null) Modifier.clickable(onClickLabel = "Escribir el peso", onClick = onTap) else Modifier),
            contentAlignment = Alignment.Center) {
            Text(value, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = if (value.length > 5) 26.sp else 38.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepButton("−", "Menos $label", minus)
            StepButton("+", "Más $label", plus)
        }
    }
}

@Composable
private fun StepButton(text: String, description: String, onClick: () -> Unit) {
    Box(Modifier.width(56.dp).heightIn(min = 48.dp).clip(CircleShape).background(Palette.chip)
        .clickable(onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WeightDialog(weight: Double?, dismiss: () -> Unit, save: (Double?) -> Unit) {
    var text by remember { mutableStateOf(weight?.let { kg(it).removeSuffix(" kg") } ?: "") }
    AlertDialog(onDismissRequest = dismiss, containerColor = Palette.surface,
        title = { Text("Peso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, label = { Text("kg") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Secondary("Vacío: con tu peso corporal.")
            }
        },
        confirmButton = { TextButton(onClick = { save(text.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.5..500.0 }) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

@Composable
private fun RestView(left: Int, total: Int, e: StrengthExercise?, setNo: Int, logged: Pair<StrengthExercise, LoggedSet>?,
                     adjust: (Int) -> Unit, skip: () -> Unit, rate: (Int) -> Unit) {
    val ok = Palette.ok
    val track = Palette.chip
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(220.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 14.dp.toPx()
                drawArc(track, 0f, 360f, false, style = Stroke(w))
                drawArc(ok, -90f, 360f * left / total.coerceAtLeast(1), false, style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%d:%02d".format(left / 60, left % 60), fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 44.sp)
                Secondary("descanso de %d:%02d".format(total / 60, total % 60))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        SecondaryButton("−15 s") { adjust(-15) }
        SecondaryButton("+15 s") { adjust(15) }
        SecondaryButton("Saltar", onClick = skip)
    }
    logged?.let { (ex, s) ->
        Module(background = Palette.okSoft) {
            Secondary("${ex.name} · serie ${s.set} apuntada")
            Text(setText(s, ex.timed), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            // Opcional: con lo que te quedaba, Brivel no sube el peso si fuiste más cerca del fallo de lo indicado
            ex.rir?.let {
                Secondary("¿Cuántas más podías hacer?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..4).forEach { n ->
                        val on = s.rir == n
                        Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(CircleShape)
                            .background(if (on) Palette.ok else Palette.surface)
                            .clickable(onClickLabel = "Me quedaban ${if (n == 4) "4 o más" else n}") { rate(n) },
                            contentAlignment = Alignment.Center) {
                            Text(if (n == 4) "4+" else "$n", fontFamily = Mono, fontWeight = FontWeight.SemiBold,
                                color = if (on) Palette.onOk else Palette.text)
                        }
                    }
                }
            }
        }
    }
    e?.let {
        Module {
            Secondary("Ahora")
            Text("${it.name} · serie $setNo", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            (it.done.firstOrNull { d -> d.set == setNo } ?: it.previous.firstOrNull { p -> p.set == setNo })
                ?.let { s -> Secondary("La última vez: ${setText(s, it.timed)}") }
        }
    }
}

@Composable
private fun RestBar(left: Int, total: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).clip(CircleShape).background(Palette.text)
        .padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Descanso", color = Palette.bg, fontWeight = FontWeight.Bold)
        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Palette.muted)) {
            Box(Modifier.fillMaxWidth(left / total.coerceAtLeast(1).toFloat()).height(6.dp).background(Palette.ok))
        }
        Text("%d:%02d".format(left / 60, left % 60), color = Palette.bg, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

/** Un ejercicio en la vista Sesión: una fila por serie con la última vez, lo de hoy y la serie actual marcada. */
@Composable
private fun SessionRows(e: StrengthExercise, isCurrent: Boolean, currentSet: Int, extra: @Composable () -> Unit = {}, open: (Int) -> Unit) {
    Module {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${e.done.size}/${e.sets}", fontFamily = Mono, fontSize = 13.sp, color = Palette.muted)
        }
        e.superset?.let { Source("Superserie $it") }
        Row(Modifier.fillMaxWidth()) {
            listOf("SERIE" to 0.7f, "ANTERIOR" to 1.4f, if (e.timed) "PESO" to 1f else "KG" to 1f, (if (e.timed) "SEG" else "REPS") to 1f).forEach { (h, w) ->
                Text(h, Modifier.weight(w), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.muted)
            }
            Spacer(Modifier.width(36.dp))
        }
        (1..maxOf(e.sets, e.done.size)).forEach { n ->
            val today = e.done.firstOrNull { it.set == n }
            val before = e.previous.firstOrNull { it.set == n }
            val now = isCurrent && n == currentSet && today == null
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Abrir la serie $n") { open(n) }
                .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // La letra del tipo de serie (C, D o F) junto al número; TalkBack lee el nombre entero
                Text("$n" + (today?.kind?.let { " " + SET_KINDS.getValue(it).first() } ?: ""), Modifier.weight(0.7f)
                    .semantics { contentDescription = "Serie $n" + (today?.kind?.let { ", " + SET_KINDS.getValue(it).lowercase() } ?: "") }, fontFamily = Mono)
                Text(before?.let { setText(it, e.timed) } ?: "—", Modifier.weight(1.4f), fontFamily = Mono, fontSize = 13.sp, color = Palette.muted)
                Cell(today?.let { weightText(it.weightKg) } ?: "—", today != null, now, Modifier.weight(1f))
                Cell(today?.let { amount(e, it.reps) } ?: "—", today != null, now, Modifier.weight(1f))
                Box(Modifier.padding(start = 6.dp).size(30.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (today != null) Palette.ok else Palette.chip), contentAlignment = Alignment.Center) {
                    Glyph(R.drawable.ic_check, 16.dp, if (today != null) Palette.onOk else Palette.muted)
                }
            }
        }
        extra()
    }
}

@Composable
private fun Cell(text: String, done: Boolean, now: Boolean, modifier: Modifier) {
    Box(modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(10.dp))
        .background(if (done) Palette.okSoft else if (now) Palette.surface else Palette.chip)
        .then(if (now) Modifier.border(2.dp, Palette.ok, RoundedCornerShape(10.dp)) else Modifier)
        .padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, fontFamily = Mono, fontSize = if (text.length > 5) 11.sp else 13.sp, color = if (done) Palette.text else Palette.muted)
    }
}

@Composable
private fun Finished(other: List<String>, close: () -> Unit) {
    Module(background = Palette.okSoft) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Glyph(R.drawable.ic_check, 22.dp, Palette.ok)
            SectionTitle("Sesión completa")
        }
        Secondary("Queda apuntada: la próxima vez Brivel te propone el peso con estas series.")
        other.forEach { Secondary(it) }
        PrimaryButton("Terminar", Modifier.fillMaxWidth(), onClick = close)
    }
}
