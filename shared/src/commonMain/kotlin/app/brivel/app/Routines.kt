package app.brivel.app

// Rutinas propias (pedido 2026-11-07): el usuario elige ejercicios, series y repeticiones y los días de la semana en los
// que la hace. Esos días sustituye a la fuerza de Brivel (o la añade si el plan no la tenía); el resto del plan sigue.
// La rutina se convierte en la misma sesión de fuerza que las del plan: serie a serie, peso propuesto y envío al reloj.

import app.brivel.engine.HealthStrength
import app.brivel.engine.pyRound
import app.brivel.engine.strengthTss
import kotlinx.serialization.Serializable

/** Un ejercicio de la rutina: series y rango de repeticiones (o de segundos, si el ejercicio va por tiempo). */
@Serializable
data class RoutineExercise(val name: String, val sets: Int, val low: Int, val high: Int)

/**
 * Rutina propia. `days`: días de la semana ("MON"…) en los que se hace; `rir`: repeticiones en reserva que pide en cada
 * serie; `since`: desde ese día cuenta en el plan (lo pasado no cambia al crearla o editarla).
 */
@Serializable
data class Routine(val id: String, val name: String, val exercises: List<RoutineExercise>, val days: List<String>,
    val rir: Int = 2, val since: String)

private const val WARMUP_MIN = 10  // como las sesiones de fuerza del plan de salud

/** La línea del plan ("Sentadilla con barra: 3 × 8–12 repeticiones"): la lee la sesión serie a serie (prescription). */
fun RoutineExercise.line(custom: List<CustomExercise>): String =
    "$name: $sets × $low–$high" + if (exerciseInfo(name, custom)?.timed == true) "″" else " repeticiones"

/** Minutos: calentamiento más cada serie con su descanso (restSeconds) y unos 45″ de trabajo [Inferencia]. */
fun Routine.minutes(custom: List<CustomExercise>): Int = WARMUP_MIN + pyRound(exercises.sumOf { e ->
    val info = exerciseInfo(e.name, custom)
    val p = Prescription(e.sets, e.low..e.high, info?.timed == true)
    e.sets * (restSeconds(p, info?.pattern ?: "") + 45)
} / 60.0)

/** La rutina como sesión de fuerza: la carga semanal y el emparejamiento con lo hecho la tratan como las del plan. */
internal fun Routine.strength(custom: List<CustomExercise>): HealthStrength {
    val min = minutes(custom)
    return HealthStrength(name = name, patterns = exercises.mapNotNull { exerciseInfo(it.name, custom)?.pattern }.distinct(),
        setsPerExercise = exercises.maxOfOrNull { it.sets } ?: 0, exercises = exercises.map { it.line(custom) }, rir = rir,
        durationMin = min, tss = pyRound(strengthTss((10 - rir).toDouble(), min.toDouble())), notes = "")
}

/** Comprueba una rutina antes de guardarla; devuelve la misma con el nombre limpio. */
fun validRoutine(r: Routine, custom: List<CustomExercise>): Routine {
    val name = r.name.trim()
    require(name.isNotEmpty()) { "Ponle un nombre a la rutina" }
    require(r.exercises.isNotEmpty()) { "Añade al menos un ejercicio" }
    for (e in r.exercises) {
        require(exerciseInfo(e.name, custom) != null) { "«${e.name}» no está en la biblioteca" }
        require(e.sets in 1..10) { "Entre 1 y 10 series en ${e.name}" }
        require(e.low in 1..300 && e.high in e.low..300) { "Revisa el rango de ${e.name}" }
    }
    require(r.days.all { it in WEEKDAY_LABEL }) { "Día de la semana no válido" }
    require(r.rir in 0..5) { "La reserva va de 0 a 5" }
    return r.copy(name = name, days = r.days.distinct())
}
