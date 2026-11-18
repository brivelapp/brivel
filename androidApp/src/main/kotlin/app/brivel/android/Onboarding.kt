package app.brivel.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brivel.app.GoalKind
import app.brivel.app.HEALTH_NOTICE
import app.brivel.app.MIN_AGE
import app.brivel.app.PRIVACY_NOTE
import app.brivel.app.PRIVACY_URL
import androidx.compose.ui.platform.LocalUriHandler
import app.brivel.app.RaceInfo
import app.brivel.app.BrivelApp
import app.brivel.app.UserProfile
import app.brivel.app.ageAllowed
import app.brivel.app.systemToday
import kotlinx.datetime.LocalDate

private val GOALS = listOf(
    GoalKind.RACE to ("Preparar una carrera" to "Con fecha: carrera, bici, natación, triatlón…"),
    GoalKind.FITNESS to ("Cardio y salud" to "Sobre todo cardio (unas 5 h a la semana) y 2 o 3 días de fuerza."),
    GoalKind.STRENGTH to ("Fuerza y músculo" to "Sobre todo pesas (3 o 4 días) y el cardio mínimo para la salud."),
)
private val OWN = GoalKind.OWN to ("Ya tengo mi rutina" to "Apuntas tus rutinas y tus series; Brivel guarda tu progreso.")
private val RACE_SPORTS = listOf("running" to "Carrera a pie", "cycling" to "Bici", "swimming_pool" to "Natación",
    "triathlon" to "Triatlón", "duathlon" to "Duatlón")

private const val QUESTIONS = 7  // objetivo, deportes, semana, fuerza, nivel, año y aviso de salud; sin plan, solo 3

/** Una pregunta por pantalla (lienzo de Pulso, 2026-11-03): atrás, progreso, la pregunta y «Siguiente» abajo. Sin atajos. */
@Composable
private fun Question(
    number: Int, title: String, subtitle: String?, onBack: (() -> Unit)?, next: String, nextEnabled: Boolean,
    onNext: () -> Unit, total: Int = QUESTIONS, content: @Composable ColumnScope.() -> Unit,
) {
    // imePadding: con el teclado abierto, «Siguiente» sube por encima
    Column(Modifier.fillMaxSize().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.size(44.dp).clip(CircleShape).background(Palette.surface)) {
                Glyph(R.drawable.ic_chevron_left, label = "Atrás")
            }
            if (number > 0) Row(Modifier.weight(1f).semantics { contentDescription = "Paso $number de $total" },
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(total) { i ->
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i < number) Palette.ok else Palette.chip))
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
            subtitle?.let { Secondary(it) }
            content()
        }
        Button(onClick = onNext, enabled = nextEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Text(next, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Primera vez: bienvenida y una pregunta por pantalla hasta el aviso de salud. Con perfil, solo el objetivo. */
@Composable
fun Onboarding(app: BrivelApp, act: Act) {
    val hasProfile = app.hasPlanProfile  // sin días (venía sin plan): se pregunta todo
    var step by remember { mutableIntStateOf(if (hasProfile) 1 else 0) }
    var goal by remember { mutableStateOf<GoalKind?>(null) }
    var race by remember { mutableStateOf(RaceInfo("", "running", "")) }
    var profile by remember { mutableStateOf(app.data.profile ?: UserProfile()) }
    var accepted by remember { mutableStateOf(false) }
    val links = LocalUriHandler.current
    val raceOk = goal != GoalKind.RACE || (race.name.isNotBlank() && runCatching { LocalDate.parse(race.date) }
        .getOrNull()?.let { it > systemToday() } == true)
    val year = profile.birthYear
    val ageOk = year != null && ageAllowed(year, systemToday().year)
    val own = goal == GoalKind.OWN  // sin plan: solo el año y el aviso de salud
    val total = if (own) 3 else QUESTIONS
    @Composable fun choice(kind: GoalKind, texts: Pair<String, String>) =
        Module(Modifier.selectable(goal == kind, role = Role.RadioButton) { goal = kind },
            accent = if (goal == kind) Palette.ok else null, background = if (goal == kind) Palette.okSoft else Palette.surface) {
            Text(texts.first, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Secondary(texts.second)
        }

    when (step) {
        0 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("Brivel", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text("El sistema operativo de tu cuerpo", fontSize = 18.sp, color = Palette.ok)
            Secondary("Te dice qué hacer hoy y por qué, con tus datos y según la evidencia. Gratis, sin anuncios y de código abierto.")
            Secondary(PRIVACY_NOTE)
            TextButton(onClick = { links.openUri(PRIVACY_URL) }) { LinkText("Política de privacidad") }
            Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Empezar") }
            SyncCard(app, act, 0, allowCreate = false)
        }
        1 -> Question(if (hasProfile) 0 else 1, "¿Cómo quieres usar Brivel?", "Podrás cambiarlo cuando quieras.",
            if (hasProfile) null else ({ step = 0 }), if (hasProfile) "Guardar objetivo" else "Siguiente", goal != null && raceOk,
            { if (hasProfile) act.run { app.setGoal(goal!!, race.takeIf { goal == GoalKind.RACE }) } else step = if (own) 6 else 2 },
            total) {
            // Muestra B (2026-11-18): los objetivos con plan juntos y «sin plan» aparte, para que no parezca otro objetivo
            GroupTitle("Con un plan que Brivel ajusta cada día")
            GOALS.forEach { (kind, texts) -> choice(kind, texts) }
            if (goal == GoalKind.RACE) RaceForm(race) { race = it }
            GroupTitle("Sin plan")
            choice(OWN.first, OWN.second)
        }
        2 -> Question(2, "¿Qué deportes haces?", "Si no marcas nada, el plan es de caminar.", { step = 1 }, "Siguiente", true,
            { step = 3 }) {
            SportPicker(profile.cardio) { profile = profile.copy(cardio = it) }
        }
        3 -> Question(3, "¿Cuánto tiempo tienes cada día?", "Lo podrás cambiar cuando quieras.", { step = 2 }, "Siguiente",
            profile.days.values.any { it > 0 }, { step = 4 }) {
            Module { WeekBars(profile.days) { profile = profile.copy(days = it) } }
        }
        4 -> Question(4, "¿Dónde haces fuerza?", "La fuerza ayuda a cualquier deporte y a la salud.", { step = 3 }, "Siguiente", true,
            { step = 5 }) {
            ChoiceCards(STRENGTH_CHOICES, profile.strength) { profile = profile.copy(strength = it) }
        }
        5 -> Question(5, "¿Cuánto entrenas ahora?", "Para empezar a tu medida.", { step = 4 }, "Siguiente", true, { step = 6 }) {
            ChoiceCards(LEVEL_CHOICES, profile.level) { profile = profile.copy(level = it) }
        }
        6 -> Question(if (own) 2 else 6, "¿En qué año naciste?", if (own) "Hace falta para usar Brivel." else "Para calcular tus zonas de pulso.",
            { step = if (own) 1 else 5 }, "Siguiente", ageOk, { step = 7 }, total) {
            BirthYearField(year) { profile = profile.copy(birthYear = it) }
            if (year != null && year >= 1000 && !ageOk) Secondary("Hace falta tener al menos $MIN_AGE años.", Palette.caution)
        }
        else -> Question(if (own) 3 else 7, "Antes de empezar", null, { step = 6 }, if (own) "Empezar" else "Ver mi plan", accepted, {
            act.run {
                if (own) app.startWithoutPlan(profile.birthYear)
                else {
                    app.saveProfile(profile)
                    app.setGoal(goal!!, race.takeIf { goal == GoalKind.RACE })
                }
            }
        }, total) {
            Module(background = Palette.cautionSoft) { Secondary(HEALTH_NOTICE, Palette.text) }
            Row(Modifier.toggleable(accepted, role = Role.Checkbox) { accepted = it }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(accepted, null)
                Text("Lo he leído y entreno bajo mi responsabilidad", fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun RaceForm(race: RaceInfo, onChange: (RaceInfo) -> Unit) {
    Module {
        OutlinedTextField(race.name, { onChange(race.copy(name = it)) }, label = { Text("Nombre de la carrera") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        ChipRow {
            RACE_SPORTS.forEach { (key, label) ->
                FilterChip(selected = race.sport == key, label = { Text(label) }, onClick = { onChange(race.copy(sport = key)) })
            }
        }
        OutlinedTextField(race.date, { onChange(race.copy(date = it.trim())) }, label = { Text("Fecha (AAAA-MM-DD)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(race.distanceKm?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "",
            { v -> onChange(race.copy(distanceKm = v.replace(',', '.').toDoubleOrNull())) },
            label = { Text("Distancia en km (opcional)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        if (race.sport == "running") Row(Modifier.toggleable(race.trail, role = Role.Checkbox) { onChange(race.copy(trail = it)) },
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(race.trail, null)
            Text("Es por montaña (trail)")
        }
        ChipRow {
            listOf("A" to "Principal", "B" to "Importante", "C" to "Preparatoria").forEach { (p, label) ->
                FilterChip(selected = race.priority == p, label = { Text(label) }, onClick = { onChange(race.copy(priority = p)) })
            }
        }
    }
}
