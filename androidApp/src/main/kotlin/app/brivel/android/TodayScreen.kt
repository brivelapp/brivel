package app.brivel.android

import app.brivel.app.TEST_FIELDS
import app.brivel.app.EFFORTS
import app.brivel.app.EFFORT_LABEL
import app.brivel.app.RunToRate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.sp
import app.brivel.app.goalLabel
import app.brivel.app.DayView
import app.brivel.app.Reason
import app.brivel.app.SPORT_LABEL
import app.brivel.app.BrivelApp
import app.brivel.app.dateLabel
import androidx.compose.ui.Alignment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayTab(app: BrivelApp, version: Int, act: Act, openTrain: () -> Unit = {}, openGoal: () -> Unit) {
    val screen = remember(version) { app.todayScreen() }
    val day = screen.day
    var goalOpen by remember { mutableStateOf(false) }
    if (goalOpen) ModalBottomSheet(onDismissRequest = { goalOpen = false }, containerColor = Palette.surface) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(app.data.goal?.let { goalLabel(it) } ?: "Sin objetivo", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Secondary("Puedes cambiarlo o abandonarlo cuando quieras; el plan se rehace al momento.")
            TonalButton(onClick = { goalOpen = false; openGoal() }) { Text("Cambiar objetivo") }
            OtherRaces(app, act)
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Hoy", subtitle = screen.dateLabel)
        screen.goalLine?.let {
            Secondary(it)
            TextButton(onClick = { goalOpen = true }) { LinkText("Tu objetivo") }
        }
        if (screen.noPlan) TextButton(onClick = openGoal) { LinkText("Quiero un plan de Brivel") }
        if (screen.goalFinished) {
            Module {
                SectionTitle("Tu carrera ya pasó")
                Secondary("¿Qué quieres ahora? Otra carrera, mantener la forma o descansar unos días.")
                PrimaryButton("Elegir objetivo", onClick = openGoal)
            }
        }
        // Tu cuerpo hoy en una frase: sale de la misma decisión que la sesión
        if (screen.stateTitle.isNotEmpty()) StateCard(screen.tone, screen.stateTitle, screen.stateDetail, screen.garminNote)
        screen.rateRun?.let { RateRun(app, act, it) }
        if (screen.noPlan && day.items.isEmpty()) NoRoutineToday(app, screen.nextRoutine, version, act, openTrain)
        DayContent(app, day, app.watchRoute != null, act, today = true)
        // Widgets (TodayWidgets.kt): las preguntas y la semana de serie, y los que se añadan
        TodayWidgets(app, version, act, screen)
        Spacer(Modifier.height(24.dp))
    }
}

/** El resultado de un test de campo (TEST_FIELDS): con él, cada sesión lleva su ritmo, sus vatios o su pulso. */
@Composable
private fun TestResult(app: BrivelApp, act: Act, date: String, kind: String) {
    var open by remember { mutableStateOf(false) }
    var main by remember { mutableStateOf("") }
    var hr by remember { mutableStateOf("") }
    val (mainLabel, hrLabel) = TEST_FIELDS.getValue(kind)
    PrimaryButton("Apuntar el resultado", Modifier.fillMaxWidth()) { main = ""; hr = ""; open = true }
    if (open) AlertDialog(onDismissRequest = { open = false }, containerColor = Palette.surface,
        title = { Text("Resultado del test") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(main, { main = it }, label = { Text(mainLabel) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                hrLabel?.let { OutlinedTextField(hr, { v -> hr = v.filter { it.isDigit() } }, label = { Text(it) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
            }
        },
        confirmButton = { TextButton(onClick = {
            act.run("Hecho: tus sesiones ya llevan cifras") { app.saveTestResult(date, kind, main, hr.ifBlank { null }); open = false }
        }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } })
}

/** Quien empieza: cómo fue su última carrera. Con las fáciles, Brivel aprende su ritmo suave (sin test). */
@Composable
private fun RateRun(app: BrivelApp, act: Act, run: RunToRate) = Module {
    SectionTitle(run.question)
    run.source?.let { Source(it) }  // «Garmin [modelo]» bajo el título (normas de marca de Garmin)
    Text(run.detail, fontFamily = Mono, fontSize = 16.sp)
    ChipRow {
        EFFORTS.forEach { e -> SecondaryButton(EFFORT_LABEL.getValue(e)) { act.run { act.show(app.rateRun(run.id, e)) } } }
    }
    Secondary(run.note)
}

/** Sin plan y sin rutina hoy: la próxima, tus rutinas para hacer una hoy o un entreno libre; sin rutinas, crear la primera. */
@Composable
private fun NoRoutineToday(app: BrivelApp, next: String?, version: Int, act: Act, openTrain: () -> Unit) {
    val routines = remember(version) { app.routines }
    fun start(id: String?) = act.run { act.openStrength(app.startWorkout(id).date) }
    Module {
        if (routines.isEmpty()) {
            Text("Empieza por tu rutina", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Secondary("Apunta sus ejercicios y los días que la haces. Esos días te saldrá aquí, lista para empezar, y Brivel guardará cada serie.")
            PrimaryButton("Crear mi rutina", Modifier.fillMaxWidth(), onClick = openTrain)  // en Entrenar, «Tus rutinas»
            SecondaryButton("Entrenar sin rutina", Modifier.fillMaxWidth()) { start(null) }
        } else {
            Text("Hoy no te toca rutina", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            next?.let { Secondary("$it. Si te apetece entrenar hoy, elige una o empieza en blanco.") }
            routines.forEach { r -> TonalButton(onClick = { start(r.id) }, modifier = Modifier.fillMaxWidth()) { Text("Hacer «${r.name}» hoy") } }
            SecondaryButton("Entreno libre", Modifier.fillMaxWidth()) { start(null) }
        }
    }
}

/** Las sesiones de un día con sus acciones: hecha, cambiar de deporte, enviar al reloj, hoy no puedo. */
@Composable
fun DayContent(app: BrivelApp, day: DayView, linked: Boolean, act: Act, today: Boolean) {
    // En Hoy la tarjeta de estado ya dice que descansas: aquí solo lo que se puede hacer
    val changeable = app.canChangeDay(day.date)
    if (day.items.isEmpty() && (!today || changeable)) {
        Module {
            if (!today) SectionTitle("Descanso")
            if (!today) day.note?.let { Secondary(it) }
            if (changeable) {
                SecondaryButton(if (today) "Prefiero entrenar hoy" else "Prefiero entrenar este día") {
                    act.run { app.changeDay(day.date, app.minutesToTrain(day)) }
                }
            }
        }
    }
    for (item in day.items) {
        val done = item.slot in day.doneSports || item.sport in day.doneSports
        SessionCard(item, done) {
            var open by remember { mutableStateOf(false) }
            val canChange = !done && !day.isPast && item.alternatives.isNotEmpty()
            // En flujo: con letra grande, «Empezar» se partía letra a letra al lado de «La he hecho»
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.isStrength && (day.isPast || today)) {
                    // Apuntar las series ya la cuenta como hecha
                    PrimaryButton(if (done) "Ver las series" else "Empezar", Modifier.weight(1f)) { act.openStrength(day.date) }
                    if (!done) SecondaryButton("La he hecho") { act.run { app.markDone(day.date, item.sport) } }
                } else if (!done && (day.isPast || today)) {
                    PrimaryButton("La he hecho", Modifier.weight(1f)) { act.run { app.markDone(day.date, item.sport) } }
                }
                if (canChange) SecondaryButton(if (open) "Cerrar" else "Cambiar") { open = !open }
            }
            if (open) item.alternatives.forEach { alt ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SportBadge(alt.sport, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("${alt.title} · ${alt.durationMin}′", modifier = Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = { act.run { app.chooseAlternative(day.date, item.slot, alt.sport) } }) { LinkText("Haré esta") }
                }
            }
            if (today) SendToWatch(app, act, item.sport)
            item.testKind?.takeIf { today || day.isPast }?.let { TestResult(app, act, day.date, it) }
            if (item.chosen) TextButton(onClick = { act.run { app.clearChoice(day.date) } }) { LinkText("Volver a la planificada") }
            item.movedFrom?.let { Secondary("La has movido aquí desde el ${dateLabel(it).lowercase()}.") }
            val targets = remember(day, item.slot) { if (done || day.isPast) emptyList() else app.moveTargets(day.date, item.slot) }
            var moving by remember { mutableStateOf(false) }
            if (targets.isNotEmpty()) TextButton(onClick = { moving = !moving }) { LinkText(if (moving) "No moverla" else "Moverla a otro día") }
            if (moving) ChipRow {
                targets.forEach { t ->
                    TonalButton(onClick = { moving = false; act.run { app.moveSession(day.date, item.slot, t) } }) {
                        Text(dateLabel(t) + if (t == item.movedFrom) " (su día)" else "")
                    }
                }
            }
        }
    }
    day.matches.forEach { ReasonRow(Reason(it.text, null), if (it.counts) Palette.ok else Palette.caution) }
    if (!today || day.adjustments.isNotEmpty()) day.reasons.forEach { ReasonRow(it, Palette.ok) }  // en Hoy, sin ajuste, ya van en la tarjeta de estado
    if (changeable && day.items.isNotEmpty()) {
        TextButton(onClick = { act.run { app.changeDay(day.date, 0) } }) { LinkText(if (today) "Hoy no puedo entrenar" else "Este día no puedo entrenar", color = Palette.muted) }
    }
}


