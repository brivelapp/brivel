package app.brivel.app

// Un solo motor para todos los objetivos (docs/VISION.md §6): el objetivo decide la periodización (sin fecha o hacia una
// carrera) y la app recibe siempre la misma forma de plan. Reutiliza el motor portado sin reescribirlo.

import app.brivel.engine.CARDIO
import app.brivel.engine.Day
import app.brivel.engine.DayState
import app.brivel.engine.Done
import app.brivel.engine.Goal
import app.brivel.engine.HealthCardio
import app.brivel.engine.HealthProfile
import app.brivel.engine.HealthStrength
import app.brivel.engine.Planned
import app.brivel.engine.Profile
import app.brivel.engine.RaceStrength
import app.brivel.engine.RaceStrengthItem
import app.brivel.engine.Session
import app.brivel.engine.WEEKDAYS
import app.brivel.engine.ScreeningResult
import app.brivel.engine.screen
import app.brivel.engine.adherenceHistory
import app.brivel.engine.adjustDay
import app.brivel.engine.alternatives
import app.brivel.engine.effortNote
import app.brivel.engine.equivalentMinutes
import app.brivel.engine.isoWeek
import app.brivel.engine.matchDay
import app.brivel.engine.phaseFor
import app.brivel.engine.plan
import app.brivel.engine.plusDays
import app.brivel.engine.position
import app.brivel.engine.proposeDay
import app.brivel.engine.watchReadiness
import app.brivel.engine.pyRound
import app.brivel.engine.readiness
import app.brivel.engine.hrvStatus
import app.brivel.engine.weekWithHistory
import app.brivel.engine.weekday
import app.brivel.intervals.dayState
import app.brivel.intervals.fillPhysiology
import app.brivel.intervals.watchSessions
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Un paso de una sesión, ya listo para mostrar. */
@Serializable
data class Step(val name: String, val reps: Int, val minutes: Double, val intensityZone: String, val restMinutes: Double?,
                val detail: String?, val target: String?, val meters: Int? = null)  // meters: natación con CSS

/** Una sesión del plan, de fuerza o de resistencia, con nombre llano y su nombre técnico. */
@Serializable
data class PlanItem(
    val sport: String,
    val kind: String,
    val title: String,
    val hint: String,
    val technicalName: String,
    val durationMin: Int,
    val steps: List<Step>,
    val exercises: List<String>,
    val notes: String?,
    val alternatives: List<PlanItem>,
    val chosen: Boolean,  // la eligió el usuario en lugar de la planificada
    val slot: String,  // deporte de la sesión planificada que ocupa (para el calendario del reloj)
    val movedFrom: String? = null,  // la movió el usuario desde ese día
    @Transient internal val session: Session? = null,  // la web recibe la pantalla en JSON: lo interno no sale
    @Transient internal val healthStrength: HealthStrength? = null,
    @Transient internal val raceStrength: RaceStrength? = null,
    @Transient internal val rotatedFrom: Map<String, String> = emptyMap(),  // variante puesta por la rotación → ejercicio del plan
    @Transient internal val linked: Set<Int> = emptySet(),  // posiciones de `exercises` en superserie con la anterior
) {
    val isStrength: Boolean get() = sport == "strength"
}

/** Un motivo con su etiqueta de evidencia separada. */
@Serializable
data class Reason(val text: String, val evidence: String?)

@Serializable
data class MatchView(val text: String, val counts: Boolean)

@Serializable
data class DayView(
    val date: String,
    val weekday: String,
    val isToday: Boolean,
    val isPast: Boolean,
    val items: List<PlanItem>,
    val note: String?,
    val availableMin: Int?,  // día disponible que el motor deja de descanso extra: se puede recuperar
    val reasons: List<Reason>,
    val adjustments: List<Reason>,  // ajustes por cómo estás hoy (fatiga, sueño, HRV o tus respuestas)
    val matches: List<MatchView>,
    val doneSports: Set<String>,
    val phase: String?,
)

data class WeekView(
    val monday: String,
    val days: List<DayView>,
    val activeMinutes: Int,  // minutos moderados equivalentes (OMS)
    val strengthDays: Int,
    val warnings: List<String>,
    val info: List<String>,
    val block: Int?,  // sin fecha: bloque y semana dentro del bloque
    val weekInBlock: Int?,
    val changed: Boolean,
)

private val RACE_SPORTS = setOf("running", "cycling", "swimming_pool")

/** La sesión `slot` del día `from` se hace el día `to`, de la misma semana. Solo esa vez: la semana tipo no cambia. */
@Serializable
data class SessionMove(val from: String, val slot: String, val to: String)

fun mondayOf(date: LocalDate): LocalDate = date.plusDays(-(date.dayOfWeek.isoDayNumber - 1))

/** Calcula el plan de cualquier semana con los datos guardados en el móvil. */
class Planner(private val data: AppData, private val today: LocalDate) {
    private val profile = data.profile ?: UserProfile()
    private val goal = data.goal

    /**
     * Cuestionario de salud del ACSM, si existe. Decisión 2026-10-21 (docs/CUMPLIMIENTO.md): las apps no lo preguntan,
     * el aviso de salud lo sustituye; el motor aplica el tope si algún día hay respuestas.
     */
    val screening: ScreeningResult? = profile.screening?.let { screen(it) }
    private val intensityCap = screening?.intensityCap ?: "vigorous"
    private val cache = data.deviceData(today)

    /** Fisiología de intervals.icu (LTHR, FC máx., CSS…) si está vinculado. */
    val physiology: Profile? = cache?.let {
        fillPhysiology(Profile(activeSports = listOf("running", "cycling")), it.athlete)
    }

    /** Estado de hoy (carga, HRV, sueño) si hay datos de intervals.icu. */
    val state: DayState? = cache?.let { runCatching { dayState(it.wellness, it.activities, today) }.getOrNull() }

    /** Hay señales de recuperación de hoy (VFC o sueño): si no, se preguntan las tres cosas aunque haya datos del móvil. */
    val hasRecoverySignals: Boolean = state?.let { s ->
        hrvStatus(s.hrvHistory) != null || s.sleepScore != null || (s.sleepHours != null && s.usualSleepHours != null)
    } == true

    private val feltToday: String? = data.readiness[today.toString()]?.let { readiness(it.sleep, it.energy, it.soreness) }
        ?.takeIf { it != "normal" }

    private val strengthFromSets = mutableSetOf<String>()  // días cuya fuerza hecha sale solo de las series apuntadas

    private val done: Map<String, List<Done>> = run {
        val all = (cache?.let { watchSessions(it.activities) } ?: emptyMap()).mapValues { it.value.toMutableList() }
            .toMutableMap()
        for (m in data.manualDone) all.getOrPut(m.date) { mutableListOf() } += Done(m.sport, "manual")
        // Apuntar lo levantado también cuenta como fuerza hecha ese día, aunque no se pulse "hecha"
        for (date in data.sets.map { it.date }.toSet()) {
            val day = all.getOrPut(date) { mutableListOf() }
            if (day.none { it.sport == "strength" }) {
                day += Done("strength", "manual")
                strengthFromSets += date
            }
        }
        all
    }

    fun raceGoal(): Goal? = goal?.race?.takeIf { goal.kind == GoalKind.RACE }?.let {
        val main = LocalDate.parse(it.date)
        Goal(it.name, it.sport, main, it.priority, it.distanceKm, it.trail,
            secondary = goal.otherRaces.map { r -> Goal(r.name, r.sport, LocalDate.parse(r.date), r.priority, r.distanceKm, r.trail) }
                .filter { r -> r.date < main })
    }

    // ------------------------------------------------------------ sin fecha (ponerse en forma, fuerza, salud)

    private val healthProfile: HealthProfile by lazy {
        val ph = physiology
        HealthProfile(
            goal = if (goal?.kind == GoalKind.STRENGTH) "strength" else "fitness",
            days = profile.days.filterValues { it > 0 },
            strength = profile.strength,
            cardio = profile.cardio.filter { it in CARDIO }.ifEmpty { listOf("walking") },
            level = profile.level,
            age = profile.birthYear?.let { today.year - it },
            intensityCap = intensityCap,
            lthr = ph?.lthr, maxHr = ph?.maxHr, css = ph?.css,
        )
    }

    private val blockStart: LocalDate = data.blockStart?.let { LocalDate.parse(it) } ?: mondayOf(today)

    private val changes = data.weekChanges.mapValues { (_, c) -> c.steps() }

    // La adherencia de los bloques terminados se calcula una vez: las semanas futuras usan la de hoy
    private val history: List<Double> by lazy {
        val (block, _) = position(blockStart, mondayOf(today))
        adherenceHistory(healthProfile, blockStart, done, block, changes)
    }

    private fun healthWeek(monday: LocalDate): WeekView {
        val p = healthProfile
        val (block, week) = position(blockStart, monday)
        val adherence = history.take(block)
        val change = data.weekChanges[monday.toString()]
        val plan = weekWithHistory(p, monday, adherence, week, change?.steps() ?: emptyList())
        val rotate = rotates(p.level, p.age, p.intensityCap)
        var slot = 0  // orden de la sesión de fuerza en la semana, según el plan (antes de los ajustes de hoy)
        val days = plan.days.map { d ->
            val mine = slot
            if (d.sessions.any { it is HealthStrength || it is RaceStrengthItem }) slot++
            var sessions: List<Planned> = d.sessions
            var note = d.note
            val adjustments = mutableListOf<Reason>()
            val answers = data.readiness[d.date]
            if (d.date == today.toString()) {
                // Tus respuestas mandan; sin ellas, los datos del reloj o del móvil (sin reloj se te preguntan)
                val watch = if (answers == null) state?.let { watchReadiness(it) } else null
                val status = answers?.let { readiness(it.sleep, it.energy, it.soreness) } ?: watch?.first ?: "normal"
                val (adjusted, adjustNote) = adjustDay(p, sessions, status, monday.isoWeek())
                sessions = adjusted
                if (adjustNote != null) {
                    adjustments += watch?.second?.map { splitEvidence(it).let { (t, e) -> Reason(t, e) } }
                        ?: listOf(Reason(adjustNote, "Inferencia"))
                    note = null
                }
            }
            val items = sessions.map { s ->
                when (s) {
                    is HealthStrength -> strengthItem(s)
                    is HealthCardio -> cardioItem(s.session, alternatives(p, s.session, monday.isoWeek()))
                    is RaceStrengthItem -> raceStrengthItem(s.strength)
                }
            }
            dayView(d.date, d.weekday, applyChoice(d.date, items, block, mine, rotate), note, d.availableMin, emptyList(), adjustments, null)
        }
        return WeekView(monday.toString(), days, activeMinutes(days), days.count { d -> d.items.any { it.isStrength } },
            plan.warnings, plan.info, block, week, plan.changed == true)
    }

    // ------------------------------------------------------------ hacia una carrera

    private fun raceProfile(): Profile {
        val sports = profile.cardio.filter { it in RACE_SPORTS }.ifEmpty {
            listOf(goal?.race?.sport?.takeIf { it in RACE_SPORTS } ?: "running")
        }
        val base = Profile(activeSports = sports, strength = profile.strength,
            restDays = WEEKDAYS.filter { (profile.days[it] ?: 0) == 0 }, dayMinutes = profile.days.filterValues { it > 0 },
            intensityCap = intensityCap)
        return cache?.let { fillPhysiology(base, it.athlete) } ?: base
    }

    /** El perfil de carrera de ese día con los cambios de su semana ("hoy no puedo", "este día tengo X min"). */
    private fun raceProfileFor(base: Profile, date: LocalDate): Profile {
        val changes = data.weekChanges[mondayOf(date).toString()]?.steps()
            ?.lastOrNull { (_, since) -> date >= since }?.first ?: return base
        val minutes = (profile.days + changes).filterValues { it > 0 }
        return base.copy(restDays = WEEKDAYS.filter { (minutes[it] ?: 0) == 0 }, dayMinutes = minutes)
    }

    private fun raceWeek(monday: LocalDate, raceGoal: Goal): WeekView {
        val base = raceProfile()
        val rp = raceProfileFor(base, if (today > monday) today else monday)
        val sunday = monday.plusDays(6)
        val fromToday = if (sunday >= today) {
            val start = if (today > monday) today else monday
            plan(start, (sunday.toEpochDays() - start.toEpochDays()).toInt() + 1, rp, raceGoal,
                if (start == today) state else null)
        } else emptyList()
        val engineDays: List<Day> = (0 until 7).map { d ->
            val date = monday.plusDays(d)
            when {
                // Tus respuestas de hoy ajustan el día igual que el reloj (sin reloj, con un estado neutro)
                date == today && feltToday != null ->
                    proposeDay(date, rp, raceGoal, (state ?: DayState(ctl = 0.0, atl = 0.0)).copy(feltStatus = feltToday))
                else -> fromToday.firstOrNull { it.date == date.toString() } ?: proposeDay(date, raceProfileFor(base, date), raceGoal)
            }
        }
        // Quien prepara una carrera ya entrena: su nivel no frena la rotación; la edad y el cuestionario de salud, sí
        val rotate = healthProfile.let { rotates("fitness", it.age, it.intensityCap) }
        var slot = 0
        val days = engineDays.map { d ->
            val mine = slot
            if (d.strength != null) slot++
            val items = listOfNotNull(d.main?.let { cardioItem(it, d.alternatives) }, d.strength?.let { raceStrengthItem(it) })
            val reasons = d.reasons.filter { it !in d.adjustments }.map { splitEvidence(it).let { (t, e) -> Reason(t, e) } }
            val adjustments = d.adjustments.map { splitEvidence(it).let { (t, e) -> Reason(t, e) } }
            dayView(d.date, d.weekday, applyChoice(d.date, items, rotationBlock(monday), mine, rotate), null, null, reasons, adjustments, d.phase)
        }
        return WeekView(monday.toString(), days, activeMinutes(days), days.count { d -> d.items.any { it.isStrength } },
            rp.warnings, emptyList(), null, null, data.weekChanges.containsKey(monday.toString()))
    }

    // ------------------------------------------------------------ común

    fun week(monday: LocalDate): WeekView {
        val race = raceGoal()
        val week = applyWorkouts(moveSessions(applyRoutines(if (race != null && race.date >= today) raceWeek(monday, race) else healthWeek(monday))))
            .let { w -> zoneLine(w)?.let { w.copy(info = w.info + it) } ?: w }
        val s = screening ?: return week
        return when (s.clearance) {
            // Síntomas con actividad: nada de ejercicio hasta que lo valore un médico
            "stop" -> week.copy(days = week.days.map { it.copy(items = emptyList(), matches = emptyList(), doneSports = emptySet()) },
                activeMinutes = 0, strengthDays = 0, warnings = listOf(s.message) + week.warnings)
            "none" -> week
            else -> week.copy(warnings = listOf(s.message) + week.warnings)
        }
    }

    /** Series de fuerza de la semana por zona del cuerpo y, si empuje y tracción van muy desiguales, el aviso. [Inferencia] */
    private fun zoneLine(week: WeekView): String? {
        val sets = linkedMapOf<String, Int>()
        val byPattern = mutableMapOf<String, Int>()
        for (item in week.days.flatMap { it.items }.filter { it.isStrength }) for (line in item.exercises) {
            val p = prescription(line, item.raceStrength?.dose, data.customExercises) ?: continue
            val pattern = exerciseInfo(exerciseName(line), data.customExercises)?.pattern ?: continue
            val zone = PATTERN_ZONE[pattern] ?: continue
            sets[zone] = (sets[zone] ?: 0) + p.sets
            byPattern[pattern] = (byPattern[pattern] ?: 0) + p.sets
        }
        if (sets.isEmpty()) return null
        val push = (byPattern["empuje horizontal"] ?: 0) + (byPattern["empuje vertical"] ?: 0)
        val pull = (byPattern["tracción horizontal"] ?: 0) + (byPattern["tracción vertical"] ?: 0)
        val uneven = when {
            push >= 2 * pull && push - pull >= 4 -> " Empujas el doble de lo que tiras: añade remos o jalones."
            pull >= 2 * push && pull - push >= 4 -> " Tiras el doble de lo que empujas: añade algún press."
            else -> ""
        }
        return "Fuerza de la semana por zona: " + ZONES.filter { it in sets }.joinToString(", ") { "$it ${sets[it]} series" } + "." + uneven
    }

    /**
     * Rutinas propias (Routines.kt): el día asignado, la fuerza de Brivel pasa a ser la rutina; si ese día no tenía
     * fuerza, se añade. Solo desde `since`: lo pasado se queda como estaba.
     */
    private fun applyRoutines(week: WeekView): WeekView {
        if (data.routines.isEmpty()) return week
        val days = week.days.map { d ->
            val routine = data.routines.firstOrNull { r -> weekdayOf(LocalDate.parse(d.date)) in r.days && d.date >= r.since }
                ?: return@map d
            withStrength(d, routineItem(routine, routine.strength(data.customExercises)))
        }
        return week.copy(days = days, strengthDays = days.count { d -> d.items.any { it.isStrength } })
    }

    /**
     * Entrenos libres (FreeWorkout): ese día la fuerza es la que el usuario ha montado; sustituye a la del plan o a la de
     * su rutina, o se añade si no había. Después de mover sesiones: el entreno libre es de su día y no se mueve.
     */
    private fun applyWorkouts(week: WeekView): WeekView {
        if (data.workouts.none { w -> week.days.any { it.date == w.date } }) return week
        val days = week.days.map { d ->
            val w = data.workouts.firstOrNull { it.date == d.date } ?: return@map d
            val r = Routine("", w.name, w.exercises, emptyList(), w.rir, w.date)
            withStrength(d, routineItem(r, r.strength(data.customExercises)).copy(kind = "free", hint = "Entreno libre"))
        }
        return week.copy(days = days, strengthDays = days.count { d -> d.items.any { it.isStrength } })
    }

    /** El día con esa sesión como su fuerza: en el sitio de la que tuviera, o añadida. */
    private fun withStrength(d: DayView, item: PlanItem): DayView {
        val i = d.items.indexOfFirst { it.isStrength }
        val items = if (i >= 0) d.items.toMutableList().also { it[i] = item } else d.items + item
        return dayView(d.date, d.weekday, items, d.note.takeIf { items.isEmpty() }, d.availableMin, d.reasons, d.adjustments, d.phase)
    }

    private fun routineItem(r: Routine, s: HealthStrength) = PlanItem(
        sport = "strength", kind = "routine", title = r.name, hint = "Tu rutina", technicalName = r.name,
        durationMin = s.durationMin, steps = emptyList(), exercises = s.exercises, notes = effortNote(r.rir, "gym"),
        alternatives = emptyList(), chosen = false, slot = "strength", session = null, healthStrength = s, raceStrength = null,
        linked = r.exercises.linkedIndexes(),
    )

    /**
     * Las sesiones movidas pasan al día elegido. Se aplica sobre la semana ya calculada, así que vale igual sin fecha y
     * hacia una carrera; si el motor ya no pone esa sesión en el día de origen (cambió la semana), el cambio no hace nada.
     */
    private fun moveSessions(week: WeekView): WeekView {
        if (data.moves.isEmpty()) return week
        val items = week.days.associate { it.date to it.items.toMutableList() }
        val left = mutableMapOf<String, String>()  // día que se queda sin la sesión → día al que fue
        for (m in data.moves) {
            val from = items[m.from] ?: continue
            val to = items[m.to] ?: continue
            val i = from.indexOfFirst { it.slot == m.slot }
            if (i < 0 || to.any { it.slot == m.slot }) continue
            val item = from.removeAt(i)
            // Elegir una alternativa el día nuevo se guarda con ese día
            to += (if (item.chosen) item else choose(m.to, listOf(item)).single()).copy(movedFrom = item.movedFrom ?: m.from)
            left[m.from] = m.to
        }
        val days = week.days.map { d ->
            val now = items.getValue(d.date)
            if (now == d.items) d
            else dayView(d.date, d.weekday, now, left[d.date]?.let { "La has movido al ${weekdayName(it)}." } ?: d.note.takeIf { now.isEmpty() },
                d.availableMin, d.reasons, d.adjustments, d.phase)
        }
        return week.copy(days = days, strengthDays = days.count { d -> d.items.any { it.isStrength } })
    }

    private fun weekdayName(date: String) = WEEKDAY_LABEL.getValue(weekdayOf(LocalDate.parse(date))).lowercase()

    fun day(date: LocalDate): DayView = week(mondayOf(date)).days.first { it.date == date.toString() }

    private fun activeMinutes(days: List<DayView>): Int = pyRound(days.sumOf { d ->
        d.items.mapNotNull { it.session }.sumOf { equivalentMinutes(it) }
    })

    private fun dayView(date: String, weekday: String, items: List<PlanItem>, note: String?, availableMin: Int?,
                        reasons: List<Reason>, adjustments: List<Reason>, phase: String?): DayView {
        val planned: List<Planned> = items.map { it.planned() }
        // En pantalla, hecha con la mayoría de las series; la adherencia sí cuenta el trabajo parcial [Inferencia]
        val due = items.filter { it.isStrength }.sumOf { i -> i.exercises.sumOf { prescription(it, i.raceStrength?.dose, data.customExercises)?.sets ?: 0 } }
        val unfinished = date in strengthFromSets && 2 * latestSets(data.sets).count { it.date == date && it.counts() } <= due
        val matches = matchDay(planned, (done[date] ?: emptyList()).filterNot { unfinished && it.sport == "strength" })
        return DayView(date, weekday, date == today.toString(), date < today.toString(), items, note, availableMin,
            reasons, adjustments, matches.map { MatchView(it.why, it.matched != null) },
            matches.mapNotNull { it.matched }.toSet(), phase)
    }

    /**
     * La alternativa elegida, la variante de cada ejercicio que toca en este bloque (ExerciseGuide.kt) y los ejercicios
     * cambiados en la biblioteca. No rota lo que el usuario ha cambiado o fijado él mismo, ni lo que no puede hacer con
     * su material.
     */
    private fun applyChoice(date: String, items: List<PlanItem>, block: Int, slot: Int, rotate: Boolean): List<PlanItem> = choose(date, items).map {
        if (!it.isStrength) return@map it
        val keep = data.swaps.map { s -> s.original }.distinct().filter { o ->
            data.swaps.filter { s -> s.original == o && s.since <= date }.maxByOrNull { s -> s.since }?.replacement != null
        }.toSet()
        val (lines, from) = if (!rotate) it.exercises to emptyMap() else rotateLines(it.exercises, block, slot, keep) { name ->
            data.equipment.isEmpty() || exerciseCard(name)?.let { c -> doable(c, data.equipment) } == true
        }
        val notes = listOfNotNull(it.notes, ROTATION_NOTE.takeIf { from.isNotEmpty() }).joinToString(" ").ifEmpty { null }
        it.copy(exercises = swapLines(lines, data.swaps, date), notes = notes, rotatedFrom = from)
    }

    /** La alternativa elegida pasa a ser la sesión del día; la planificada queda entre las alternativas. */
    private fun choose(date: String, items: List<PlanItem>): List<PlanItem> {
        val picks = data.choices[date] ?: return items
        return items.map { item ->
            val chosenSport = picks[item.sport] ?: return@map item
            val alt = item.alternatives.firstOrNull { it.sport == chosenSport } ?: return@map item
            alt.copy(chosen = true, slot = item.slot,
                alternatives = listOf(item.copy(alternatives = emptyList())) + item.alternatives.filter { it !== alt })
        }
    }

    private fun PlanItem.planned(): Planned = when {
        session != null -> HealthCardio(session.also { if (it.equivalentMin == null) it.equivalentMin = pyRound(equivalentMinutes(it)) })
        healthStrength != null -> healthStrength
        else -> RaceStrengthItem(raceStrength!!)
    }

    private fun cardioItem(s: Session, alts: List<Session>): PlanItem {
        val (title, hint) = PLAIN_SESSION[s.type] ?: (s.name to s.goal)
        return PlanItem(
            sport = s.sport, kind = s.type, title = title, hint = hint, technicalName = s.name, durationMin = s.durationMin,
            steps = s.blocks.map { Step(it.block, it.reps, it.minutes, it.zone, it.restMinutes, it.detail, s.targets[it.zone], it.meters) },
            exercises = emptyList(), notes = "${s.ifBad} ${s.ifGood}",
            alternatives = alts.map { cardioItem(it, emptyList()) }, chosen = false, slot = s.sport,
            session = s, healthStrength = null, raceStrength = null,
        )
    }

    private fun strengthItem(s: HealthStrength) = PlanItem(
        sport = "strength", kind = "strength", title = "Fuerza", hint = PLAIN_SESSION["strength"]!!.second,
        technicalName = "Fuerza", durationMin = s.durationMin, steps = emptyList(), exercises = s.exercises,
        notes = listOfNotNull(warmup(s.exercises), s.notes).joinToString(" "),
        alternatives = emptyList(), chosen = false, slot = "strength", session = null, healthStrength = s, raceStrength = null,
    )

    private fun raceStrengthItem(s: RaceStrength) = PlanItem(
        sport = "strength", kind = "strength", title = "Fuerza", hint = s.dose, technicalName = "Fuerza",
        durationMin = s.durationMin, steps = emptyList(), exercises = s.exercises,
        notes = "${warmup(s.exercises)} Hazla ${s.order}.", alternatives = emptyList(), chosen = false, slot = "strength",
        session = null, healthStrength = null, raceStrength = s,
    )

    /** Fase del plan de carrera hoy, o null sin carrera. */
    fun racePhase(): String? = raceGoal()?.let { phaseFor(today, it, null).first }

    companion object {
        val PHASE_LABEL = mapOf("base_early" to "Base inicial", "base" to "Base", "build" to "Construcción",
            "peak" to "Afinando", "taper" to "Puesta a punto")
        fun weekdayOf(date: LocalDate): String = date.weekday()
    }
}

/** Lo que se dice en la sesión cuando la rotación ha puesto alguna variante. */
const val ROTATION_NOTE = "Este bloque de cuatro semanas, algunos ejercicios son variantes del mismo tipo para variar el estímulo; " +
    "si prefieres el de siempre, toca el ejercicio y vuelve al del plan."

/**
 * Qué hacer en los 10 minutos de calentamiento que ya cuenta la sesión, según lo que se trabaja ese día.
 * [Práctica: calentamiento general, movilidad dinámica y series de aproximación; NSCA, Haff y Triplett 2016]
 */
fun warmup(exercises: List<String>): String {
    val zones = exercises.mapNotNull { LIBRARY[exerciseName(it)]?.pattern?.let { p -> PATTERN_ZONE[p] } }.toSet()
    val drills = buildList {
        if ("pierna" in zones) { add("balanceo de pierna adelante y atrás"); add("zancada con codo al pie") }
        if (zones.any { it in setOf("pecho", "espalda", "hombro", "brazos") }) { add("círculos de brazos"); add("rotación torácica en cuadrupedia") }
        if (isEmpty()) add("gato-camello")
    }
    return "Calentamiento (10 min): 3–5 min de cardio suave, ${drills.joinToString(", ")} y una o dos series ligeras del primer ejercicio."
}
