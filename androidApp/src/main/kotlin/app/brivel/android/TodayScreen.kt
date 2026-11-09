package app.brivel.android

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
fun TodayTab(app: BrivelApp, version: Int, act: Act, openGoal: () -> Unit) {
    val screen = remember(version) { app.todayScreen() }
    val day = screen.day
    var goalOpen by remember { mutableStateOf(false) }
    if (goalOpen) ModalBottomSheet(onDismissRequest = { goalOpen = false }, containerColor = Palette.surface) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(app.data.goal?.let { goalLabel(it) } ?: "Sin objetivo", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Secondary("Puedes cambiarlo o abandonarlo cuando quieras; el plan se rehace al momento.")
            OutlinedButton(onClick = { goalOpen = false; openGoal() }) { Text("Cambiar objetivo") }
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
        if (screen.goalFinished) {
            Module {
                SectionTitle("Tu carrera ya pasó")
                Secondary("¿Qué quieres ahora? Otra carrera, mantener la forma o descansar unos días.")
                PrimaryButton("Elegir objetivo", onClick = openGoal)
            }
        }
        // Tu cuerpo hoy en una frase: sale de la misma decisión que la sesión
        StateCard(screen.tone, screen.stateTitle, screen.stateDetail, screen.garminNote)
        if (screen.askReadiness) ReadinessCard(act)
        DayContent(app, day, app.watchRoute != null, act, today = true)
        Module {
            Row {
                SectionTitle("Esta semana")
                Spacer(Modifier.weight(1f))
                Text("${screen.weekMinutes} de 150 min", fontFamily = Mono, fontSize = 14.sp, color = Palette.muted)
            }
            WeekDots(screen.weekDots)
            if (screen.meetsWho) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Glyph(R.drawable.ic_check, 18.dp, Palette.ok)
                Text("Cumples la recomendación de la OMS para tu salud", color = Palette.ok, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else Secondary(if (screen.strengthDays >= 2) "Fuerza: ${screen.strengthDays} días, mínimo cumplido" else "Fuerza: ${screen.strengthDays} de 2 días")
            screen.warnings.forEach { ReasonRow(Reason(it, null)) }
            screen.info.forEach { Secondary(it) }
        }
        Spacer(Modifier.height(24.dp))
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            if (today && linked) {
                TextButton(onClick = { act.suspend(app.watchSentText) {
                    app.sendToday(item.sport)
                } }) {
                    Glyph(R.drawable.ic_watch, 18.dp, Palette.ok)
                    Spacer(Modifier.width(6.dp))
                    LinkText("Enviar al reloj")
                }
            }
            if (item.chosen) TextButton(onClick = { act.run { app.clearChoice(day.date) } }) { LinkText("Volver a la planificada") }
            item.movedFrom?.let { Secondary("La has movido aquí desde el ${dateLabel(it).lowercase()}.") }
            val targets = remember(day, item.slot) { if (done || day.isPast) emptyList() else app.moveTargets(day.date, item.slot) }
            var moving by remember { mutableStateOf(false) }
            if (targets.isNotEmpty()) TextButton(onClick = { moving = !moving }) { LinkText(if (moving) "No moverla" else "Moverla a otro día") }
            if (moving) ChipRow {
                targets.forEach { t ->
                    OutlinedButton(onClick = { moving = false; act.run { app.moveSession(day.date, item.slot, t) } }) {
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

@Composable
private fun ReadinessCard(act: Act) {
    var sleep by remember { mutableIntStateOf(3) }
    var energy by remember { mutableIntStateOf(3) }
    var soreness by remember { mutableIntStateOf(3) }
    Module {
        SectionTitle("¿Cómo estás hoy?")
        Secondary("Sin reloj vinculado, tus respuestas ajustan la sesión de hoy: si dormiste mal o estás cargado, será más suave.")
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

// Del 1 al 5, con el mismo selector que el Perfil
private val RATINGS = (1..5).map { n -> "$n" to when (n) { 1 -> "1 mal"; 5 -> "5 bien"; else -> "$n" } }

