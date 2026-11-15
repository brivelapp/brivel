package app.brivel.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brivel.app.BadgeView
import app.brivel.app.BadgesScreen
import app.brivel.app.BrivelApp
import app.brivel.app.Curve
import app.brivel.app.GEAR_LABEL
import app.brivel.app.GearRow
import app.brivel.app.XY
import app.brivel.app.dateLabel
import app.brivel.app.systemToday

// Actividades propias (importar un archivo FIT y ver su detalle), material con sus kilómetros y medallas, como
// web/src/Activities.tsx: lo calcula el núcleo y aquí solo se pinta.

/** Una línea del núcleo (puntos dentro de un cuadrado de lado 1) estirada al tamaño que se le dé. */
@Composable
internal fun LineChart(points: List<XY>, label: String, modifier: Modifier, color: Color = Palette.ok) =
    Canvas(modifier.semantics { contentDescription = label }) {
        if (points.size < 2) return@Canvas
        val path = Path()
        points.forEachIndexed { i, p ->
            val (x, y) = p.x.toFloat() * size.width to p.y.toFloat() * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false) = Row(Modifier.fillMaxWidth()) {
    cells.forEach {
        Text(it, Modifier.weight(1f), fontSize = if (header) 12.sp else 14.sp, color = if (header) Palette.muted else Color.Unspecified,
            fontFamily = if (header) null else Mono)
    }
}

/** El detalle de una actividad propia, en una hoja. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitySheet(app: BrivelApp, act: Act, id: String, close: () -> Unit) {
    val a = remember(id) { app.activity(id) } ?: return close()
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(a.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Secondary("${a.dateLabel} · ${a.source}")
            a.stats.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { s ->
                        Column(Modifier.weight(1f)) {
                            Text(s.label, fontSize = 12.sp, color = Palette.muted)
                            Text(s.value, fontFamily = Mono, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (a.track.isNotEmpty()) {
                LineChart(a.track, "Recorrido", Modifier.fillMaxWidth(0.8f).aspectRatio(1f).align(Alignment.CenterHorizontally))
                Source("El recorrido, sin mapa de fondo. Solo está en este móvil.")
            }
            a.charts.forEach { c ->
                Row {
                    Text(c.label, Modifier.weight(1f), fontSize = 15.sp)
                    Text("${c.low} – ${c.high}", fontFamily = Mono, fontSize = 12.sp, color = Palette.muted)
                }
                LineChart(c.line, "${c.label}: de ${c.low} a ${c.high}", Modifier.fillMaxWidth().height(56.dp))
            }
            if (a.charts.isEmpty()) Secondary("Las gráficas y el recorrido se quedan en el dispositivo que importó o grabó la actividad; aquí está su resumen.")
            if (a.zoneMinutes.sum() > 0) {
                Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp))) {
                    a.zoneMinutes.forEachIndexed { i, m ->
                        if (m > 0) Box(Modifier.weight(m.toFloat()).fillMaxHeight().background(Palette.zones["Z${minOf(i + 1, 6)}"] ?: Palette.ok))
                    }
                }
                Secondary(a.zoneMinutes.mapIndexedNotNull { i, m -> if (m > 0) "Z${i + 1} $m′" else null }.joinToString(" · "))
            }
            if (a.laps.isNotEmpty()) {
                TableRow(listOf("Vuelta", "Tiempo", "Distancia", if (a.sport == "cycling") "Velocidad" else "Ritmo", "Pulso"), header = true)
                a.laps.forEach { l -> TableRow(listOf("${l.n}", l.time, l.distance, l.pace, l.hr)) }
            }
            if (a.bests.isNotEmpty()) {
                Secondary("Tu mejor media en cada duración")
                a.bests.forEach { b ->
                    Row { Text(b.label, Modifier.weight(1f), fontSize = 15.sp); Text(b.value, fontFamily = Mono, fontSize = 15.sp) }
                }
            }
            a.gear?.let { Secondary("Material: $it") }
            PrimaryButton("Cerrar", Modifier.fillMaxWidth(), close)
            TextButton(onClick = { confirmDelete = true }) { LinkText("Borrar la actividad") }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, containerColor = Palette.surface,
        title = { Text("¿Borrar esta actividad?") }, text = { Text("Dejará de contar en tu plan y en Progreso.") },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            act.run("Actividad borrada") { app.deleteActivity(id) }
            close()
        }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}

/** Entrenar: las actividades propias y el botón de importar un archivo FIT. */
@Composable
fun ActivitiesCard(app: BrivelApp, act: Act, version: Int) {
    val rows = remember(version) { app.activities() }
    val context = LocalContext.current
    var open by remember { mutableStateOf<String?>(null) }
    var all by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) act.run("Actividad importada") {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("No se pudo abrir el archivo")
            open = app.importFit(bytes)
        }
    }
    open?.let { ActivitySheet(app, act, it) { open = null } }
    Module {
        SectionTitle("Tus actividades")
        Secondary("Las que grabas con el GPS o importas de un archivo FIT de tu reloj o tu ciclocomputador. Cuentan en tu plan y en Progreso como las del reloj.")
        (if (all) rows else rows.take(5)).forEach { r ->
            Row(Modifier.fillMaxWidth().clickable { open = r.id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SportBadge(r.sport, 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(dateLabel(r.date), r.detail).joinToString(" · "), fontSize = 12.sp, color = Palette.muted)
                }
                RowChevron()
            }
        }
        if (!all && rows.size > 5) TextButton(onClick = { all = true }) { LinkText("Ver las ${rows.size}") }
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Importar un archivo FIT") }
    }
}

/** Perfil: zapatillas y bicis con sus kilómetros. */
@Composable
fun GearCard(app: BrivelApp, act: Act, version: Int) {
    val screen = remember(version) { app.gear() }
    var editing by remember { mutableStateOf<GearRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GearRow?>(null) }
    Module {
        SectionTitle("Zapatillas y bicis")
        Secondary("Brivel apunta los kilómetros de cada actividad al material que tengas en uso en ese deporte.")
        screen.garminNote?.let { Source(it) }
        screen.rows.forEach { g ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(g.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(g.label + if (g.retired) " · retirado" else if (g.inUse) " · en uso" else "", fontSize = 12.sp, color = Palette.muted)
                    }
                    Text(g.km, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(g.detail, fontSize = 12.sp, color = Palette.muted)
                g.warning?.let { Secondary(it, Palette.caution) }
                // Pasar una de las últimas actividades a otro material del mismo deporte
                val others = screen.rows.filter { it.id != g.id && it.sport == g.sport }
                if (others.isNotEmpty()) g.uses.forEach { u ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(u.text, Modifier.weight(1f), fontSize = 13.sp)
                        others.forEach { o ->
                            TextButton(onClick = { act.run("Actividad cambiada de material") { app.moveGearUse(u.activity, o.id) } }) {
                                LinkText("A «${o.name}»", fontSize = 13.sp)
                            }
                        }
                    }
                }
                Row {
                    if (!g.inUse) TextButton(onClick = { act.run("${g.name} está en uso") { app.useGear(g.id) } }) { LinkText("Poner en uso") }
                    if (!g.retired) TextButton(onClick = { act.run("${g.name} está retirado: conserva sus kilómetros") { app.retireGear(g.id) } }) { LinkText("Retirar") }
                    TextButton(onClick = { editing = g }) { LinkText("Cambiar") }
                    TextButton(onClick = { deleting = g }) { LinkText("Borrar") }
                }
            }
        }
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Añadir zapatillas o bici") }
    }
    if (adding || editing != null) GearEditor(app, act, editing) { adding = false; editing = null }
    deleting?.let { g ->
        AlertDialog(onDismissRequest = { deleting = null }, containerColor = Palette.surface,
            title = { Text("¿Borrar «${g.name}»?") }, text = { Text("Se borran también sus kilómetros apuntados.") },
            confirmButton = { TextButton(onClick = { deleting = null; act.run("Material borrado") { app.deleteGear(g.id) } }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } })
    }
}

private fun km(text: String): Double? = text.trim().replace(',', '.').let {
    if (it.isEmpty()) null else it.toDoubleOrNull() ?: throw IllegalArgumentException("Escribe los kilómetros con números")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GearEditor(app: BrivelApp, act: Act, old: GearRow?, close: () -> Unit) {
    var name by remember { mutableStateOf(old?.name ?: "") }
    var sport by remember { mutableStateOf(old?.sport ?: GEAR_LABEL.keys.first()) }
    var since by remember { mutableStateOf(old?.since ?: systemToday().toString()) }
    var startKm by remember { mutableStateOf(old?.startKm?.takeIf { it > 0 }?.let { oneDecimal(it) } ?: "") }
    var limitKm by remember { mutableStateOf(old?.limitKm?.let { oneDecimal(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        // El selector trabaja en UTC: la fecha elegida es la de ese instante en UTC; no deja elegir un día futuro
        val state = rememberDatePickerState(
            initialSelectedDateMillis = java.time.LocalDate.parse(since).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            })
        DatePickerDialog(onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { since = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }
                picking = false
            }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } }) { DatePicker(state) }
    }
    fun decimal(t: String) = t.filter { it.isDigit() || it == ',' || it == '.' }
    // En una hoja, como los demás formularios con texto (Routines.kt): con imePadding el teclado no tapa «Guardar»
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.surface) {
        Column(Modifier.imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (old == null) "Añadir material" else "Cambiar material", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (old == null) ChipRow {
                GEAR_LABEL.forEach { (k, label) -> FilterChip(selected = sport == k, label = { Text(label) }, onClick = { sport = k }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cuenta las actividades desde el ${dateLabel(since).substringAfter(' ')}", Modifier.weight(1f), fontSize = 14.sp)
                TextButton(onClick = { picking = true }) { LinkText("Cambiar") }
            }
            OutlinedTextField(startKm, { startKm = decimal(it) }, label = { Text("Kilómetros que ya llevaba") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(limitKm, { limitKm = decimal(it) }, label = { Text("Avisar al pasar de (km, opcional)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            // El error se enseña dentro de la hoja: un aviso de abajo quedaría tapado por ella
            error?.let { Secondary(it, Palette.caution) }
            PrimaryButton("Guardar", Modifier.fillMaxWidth()) {
                try {
                    app.saveGear(old?.id, name, sport, since, km(startKm) ?: 0.0, km(limitKm))
                    act.run("Material guardado") {}
                    close()
                } catch (e: Exception) { error = e.message }
            }
            TextButton(onClick = close) { LinkText("Cancelar") }
        }
    }
}

@Composable
private fun BadgeRow(b: BadgeView) = Row(verticalAlignment = Alignment.Top) {
    val earned = b.earned != null
    Box(Modifier.size(40.dp).clip(CircleShape).then(if (earned) Modifier.background(Palette.ok) else Modifier.border(2.dp, Palette.muted, CircleShape)),
        contentAlignment = Alignment.Center) {
        Text("${b.points}", fontWeight = FontWeight.Bold, color = if (earned) Palette.surface else Palette.muted)
    }
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(b.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(if (earned) "${b.text} Ganada el ${b.earned}." else b.text, fontSize = 12.sp, color = Palette.muted)
        if (!earned) b.progress?.let {
            ProgressLine(b.fraction.toFloat())
            Text(it, fontFamily = Mono, fontSize = 12.sp, color = Palette.muted)
        }
    }
}

/** Progreso: nivel, puntos, medallas ganadas y las siguientes más cercanas. */
@Composable
fun BadgesCard(badges: BadgesScreen) {
    var all by remember { mutableStateOf(false) }
    Module {
        Row(verticalAlignment = Alignment.Bottom) {
            SectionTitle("Medallas")
            Spacer(Modifier.weight(1f))
            Text("Nivel ${badges.level} · ${badges.points} ${if (badges.points == 1) "punto" else "puntos"}", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        badges.garminNote?.let { Source(it) }
        badges.nextLevel?.let { Secondary("$it. Cada medalla vale los puntos de su círculo.") }
        (if (all) badges.earned else badges.earned.take(3)).forEach { BadgeRow(it) }
        Secondary(if (badges.earned.isEmpty()) "Aún no tienes ninguna. Estas son las que tienes más cerca:" else "Las siguientes")
        (if (all) badges.next else badges.next.take(3)).forEach { BadgeRow(it) }
        if (!all && badges.earned.size + badges.next.size > 6) TextButton(onClick = { all = true }) { LinkText("Ver todas") }
    }
}

/** Progreso: curvas de potencia y de ritmo de las actividades propias, como tabla. */
@Composable
fun Curves(curves: List<Curve>) = curves.forEach { c ->
    Module {
        SectionTitle(c.title)
        Secondary("Tu mejor media en cada duración, con las actividades que has importado o grabado.")
        TableRow(listOf("Duración", "Últimos 90 días", "Tu mejor"), header = true)
        c.rows.forEach { r -> TableRow(listOf(r.duration, r.recent, r.best)) }
        c.note?.let { Secondary(it) }
    }
}
