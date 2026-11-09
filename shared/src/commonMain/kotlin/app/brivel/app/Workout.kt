package app.brivel.app

import kotlinx.serialization.Serializable

// Sesión de fuerza serie a serie (pantalla Serie | Sesión): qué toca en cada ejercicio, lo de la última vez y lo de hoy

/** Patrón de movimiento (la figura que lo dibuja) y si va por tiempo en vez de por repeticiones. */
data class ExerciseInfo(val pattern: String, val timed: Boolean = false)

/** Los ejercicios que proponen los dos motores (HealthPlan.PATTERNS y Catalog LOWER/PLYO/UPPER/CORE). */
val EXERCISES: Map<String, ExerciseInfo> = mapOf(
    "Sentadilla copa (goblet) o con barra" to ExerciseInfo("sentadilla"),
    "Sentadilla (a una pierna al progresar)" to ExerciseInfo("sentadilla"),
    "Sentadilla con barra" to ExerciseInfo("sentadilla"),
    "Peso muerto rumano" to ExerciseInfo("bisagra"),
    "Peso muerto rumano a una pierna" to ExerciseInfo("bisagra"),
    "Puente de glúteo (a una pierna al progresar)" to ExerciseInfo("bisagra"),
    "Puente de glúteo a una pierna" to ExerciseInfo("bisagra"),
    "Empuje de cadera con barra (hip thrust)" to ExerciseInfo("bisagra"),
    "Zancada con mancuernas" to ExerciseInfo("zancada"),
    "Zancada atrás" to ExerciseInfo("zancada"),
    "Zancada búlgara con mancuernas" to ExerciseInfo("zancada"),
    "Sentadilla búlgara (progresión a sentadilla a una pierna)" to ExerciseInfo("zancada"),
    "Subida a cajón alto" to ExerciseInfo("zancada"),
    "Elevación de gemelo de pie y sentado" to ExerciseInfo("gemelo"),
    "Gemelo a una pierna en escalón" to ExerciseInfo("gemelo"),
    "Saltos al cajón" to ExerciseInfo("salto"),
    "Saltos de caída desde poca altura (drop jumps)" to ExerciseInfo("salto"),
    "Saltos de tobillo seguidos (pogo)" to ExerciseInfo("salto"),
    "Saltos de rana" to ExerciseInfo("salto"),
    "Press de banca con mancuernas" to ExerciseInfo("empuje horizontal"),
    "Flexiones (inclinadas al empezar)" to ExerciseInfo("empuje horizontal"),
    "Flexiones" to ExerciseInfo("empuje horizontal"),
    "Press militar con mancuernas" to ExerciseInfo("empuje vertical"),
    "Flexiones en pica" to ExerciseInfo("empuje vertical"),
    "Remo con mancuerna" to ExerciseInfo("tracción horizontal"),
    "Remo invertido bajo una mesa firme" to ExerciseInfo("tracción horizontal"),
    "Remo invertido" to ExerciseInfo("tracción horizontal"),
    "Tirón a la cara con polea (face pull)" to ExerciseInfo("tracción horizontal"),
    "Rotación externa con goma" to ExerciseInfo("tracción horizontal"),
    "Jalón al pecho" to ExerciseInfo("tracción vertical"),
    "Jalón al pecho o dominadas lastradas" to ExerciseInfo("tracción vertical"),
    "Dominadas asistidas con goma" to ExerciseInfo("tracción vertical"),
    "Dominadas o negativas" to ExerciseInfo("tracción vertical"),
    "Plancha frontal y lateral" to ExerciseInfo("core", timed = true),
    "Plancha frontal" to ExerciseInfo("core", timed = true),
    "Plancha lateral" to ExerciseInfo("core", timed = true),
    "Plancha de Copenhague" to ExerciseInfo("core", timed = true),
    "Bicho muerto (dead bug)" to ExerciseInfo("core"),
    "Press Pallof antirrotación" to ExerciseInfo("core"),
)

/** Series y rango de repeticiones (o de segundos) de un ejercicio. */
data class Prescription(val sets: Int, val range: IntRange, val timed: Boolean)

private val DOSE = Regex("""(\d+)(?:[–-]\d+)? × (\d+)[–-](\d+)""")

/**
 * Lo que toca: de la propia línea ("Sentadilla: 3 × 8–12 repeticiones", plan de salud) o, si no lo lleva (plan de
 * carrera), de la dosis de la sesión ("3–4 × 4–8 RM"; los saltos, de su parte "3 × 5–8 saltos"). Con una horquilla de
 * series, la menor. null: no se apunta serie a serie (equilibrio).
 */
fun prescription(line: String, dose: String?, custom: List<CustomExercise> = emptyList()): Prescription? {
    val info = exerciseInfo(exerciseName(line), custom) ?: return null
    val own = DOSE.find(line.substringAfter(":", ""))
    val m = own ?: dose?.let { d ->
        if (info.pattern == "salto") Regex("""(\d+) × (\d+)[–-](\d+) saltos""").find(d) ?: return null else DOSE.find(d)
    }
    if (m != null && !(info.pattern == "core" && own == null)) {
        val (sets, low, high) = m.destructured
        return Prescription(sets.toInt(), low.toInt()..high.toInt(), info.timed)
    }
    // El core del plan de carrera no trae dosis propia y la de la sesión es de repeticiones máximas [Práctica]
    return if (info.pattern == "core") Prescription(3, if (info.timed) 20..45 else 8..12, info.timed) else null
}

/** Descanso entre series en segundos: más cuanto más pesada. [Práctica: ACSM 2009, 2–3′ en multiarticulares; tramos: Inferencia] */
fun restSeconds(p: Prescription, pattern: String): Int = when {
    p.timed || pattern == "core" -> 60
    pattern == "salto" -> 90
    p.range.last <= 8 -> 180
    p.range.last <= 12 -> 120
    else -> 90
}

@Serializable
data class LoggedSet(val set: Int, val weightKg: Double?, val reps: Int, val rir: Int? = null)

/** Un ejercicio en la pantalla de sesión. `reps*` son segundos si `timed`. */
@Serializable
data class StrengthExercise(
    val name: String,
    val pattern: String,
    val sets: Int,
    val repsLow: Int,
    val repsHigh: Int,
    val timed: Boolean,
    val restSeconds: Int,
    val rir: Int?,  // repeticiones en reserva al terminar cada serie (null: saltos y ejercicios por tiempo)
    val suggestion: String?,
    val weightKg: Double?,  // el peso que propone para hoy (null: tu peso o sin historial)
    val previous: List<LoggedSet>,  // la última sesión anterior a este día
    val done: List<LoggedSet>,  // lo apuntado este día
    val original: String? = null,  // el ejercicio del plan, si se cambió por este en la biblioteca
)

@Serializable
data class StrengthScreen(val date: String, val title: String, val minutes: Int, val exercises: List<StrengthExercise>,
    val other: List<String>, val notes: String?)  // other: lo que no se apunta serie a serie (equilibrio)

fun strengthScreen(item: PlanItem, date: String, sets: List<SetLog>, custom: List<CustomExercise>): StrengthScreen {
    val all = latestSets(sets)
    val before = all.filter { it.date < date }
    val dose = item.raceStrength?.dose
    // Plan de salud: su RIR (nivel, objetivo y tope del cuestionario); carrera: RIR = 10 − RPE planificado, la misma
    // equivalencia que usa la carga de HealthPlan [Inferencia: Zourdos 2016, escala RPE basada en RIR]
    val target = item.healthStrength?.rir ?: item.raceStrength?.let { 10 - it.rpePlanned }
    val (logged, other) = item.exercises.partition { prescription(it, dose, custom) != null }
    val exercises = logged.map { line ->
        val p = prescription(line, dose, custom)!!
        val name = exerciseName(line)
        val pattern = exerciseInfo(name, custom)!!.pattern
        val rir = target.takeUnless { p.timed || pattern == "salto" }
        val hint = liftHint(daySummaries(before), line, p.range.takeUnless { p.timed }, rir)
        val previousDate = before.filter { it.exercise == name }.maxOfOrNull { it.date }
        fun day(d: String?) = all.filter { it.date == d && it.exercise == name }.sortedBy { it.set }
            .map { LoggedSet(it.set, it.weightKg, it.reps, it.rir) }
        StrengthExercise(name, pattern, p.sets, p.range.first, p.range.last, p.timed,
            restSeconds(p, pattern), rir, hint.suggestion, hint.weightKg, day(previousDate), day(date))
    }
    return StrengthScreen(date, item.title, item.durationMin, exercises, other, item.notes)
}
