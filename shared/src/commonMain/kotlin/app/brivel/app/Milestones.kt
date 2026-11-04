package app.brivel.app

// Progreso: hitos (mejores marcas, semanas seguidas cumpliendo la OMS, récords de fuerza) y umbrales de intervals.icu

import app.brivel.engine.Profile
import app.brivel.engine.fmt
import app.brivel.engine.paceText
import app.brivel.engine.pyRound
import app.brivel.intervals.HEALTH_ACTIVITY_SPORTS
import app.brivel.intervals.MIN_ACTIVITY_S
import app.brivel.intervals.date
import app.brivel.intervals.fillPhysiology
import app.brivel.intervals.num
import app.brivel.intervals.str
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Un hito: qué, cuánto, cuándo y de dónde sale el dato (Garmin lo pide bajo cada dato suyo). */
@Serializable
data class Milestone(val title: String, val value: String, val date: String?, val source: String? = null)

/** Un umbral con el que Brivel calcula tus zonas. */
@Serializable
data class Threshold(val label: String, val value: String, val explanation: String)

private val LONGEST = listOf("running" to "Carrera más larga", "cycling" to "Salida en bici más larga",
    "swimming_pool" to "Nado más largo", "walking" to "Caminata más larga")

/** La actividad más larga de cada deporte en lo guardado (sin las de menos de 10 minutos). */
internal fun longest(activities: List<JsonObject>): List<Milestone> = LONGEST.mapNotNull { (sport, title) ->
    activities.filter { HEALTH_ACTIVITY_SPORTS[it["type"].str()] == sport && (it["moving_time"].num() ?: 0.0) >= MIN_ACTIVITY_S }
        .maxByOrNull { it["distance"].num() ?: 0.0 }?.takeIf { (it["distance"].num() ?: 0.0) > 0 }?.let { a ->
            val m = a["distance"].num()!!
            Milestone(title, if (sport == "swimming_pool") "${pyRound(m)} m" else "${fmt(m / 1000, 1).replace('.', ',')} km", a.date(), a["brivel_source"].str())
        }
}

/**
 * Semanas seguidas con 150 minutos moderados equivalentes o más (OMS 2020), hasta la última completa; la de ahora suma
 * si ya llega. `weekly`: de la más antigua a la actual.
 */
internal fun whoStreak(weekly: List<Point>): Milestone? {
    val done = weekly.map { it.value >= 150 }
    val upTo = if (done.lastOrNull() == true) done.size else done.size - 1
    val streak = done.take(upTo.coerceAtLeast(0)).takeLastWhile { it }.size
    if (streak == 0) return null
    return Milestone("Semanas seguidas cumpliendo la OMS", if (streak == 1) "1 semana" else "$streak semanas", null)
}

/** El mayor peso de cada ejercicio (a igual peso, más repeticiones), los más recientes primero. */
fun strengthRecords(sets: List<SetLog>, limit: Int = 6): List<Milestone> =
    sets.filter { (it.weightKg ?: 0.0) > 0 }.groupBy { it.exercise }.map { (exercise, logs) ->
        logs.maxWith(compareBy<SetLog>({ it.weightKg }, { it.reps }, { it.date }).thenByDescending { it.set })
            .let { best -> Milestone(exercise, "${fmt(best.weightKg!!, 1).removeSuffix(".0").replace('.', ',')} kg × ${best.reps}", best.date) }
    }.sortedByDescending { it.date }.take(limit)

/** Los umbrales de intervals.icu (o los que pusiste allí): FC umbral, ritmo umbral, FTP y CSS. */
internal fun thresholds(athlete: JsonObject): List<Threshold> {
    val p = fillPhysiology(Profile(activeSports = listOf("running", "cycling", "swimming_pool")), athlete)
    return listOfNotNull(
        p.lthr?.takeIf { it > 0 }?.let { Threshold("FC umbral", "${pyRound(it)} ppm", "El pulso que mantienes en un esfuerzo continuo de alrededor de una hora.") },
        p.runPace?.takeIf { it > 0 }?.let { Threshold("Ritmo umbral", "${paceText(it)} /km", "El ritmo de carrera que mantienes en un esfuerzo continuo de alrededor de una hora.") },
        p.ftp?.takeIf { it > 0 }?.let { Threshold("FTP", "${pyRound(it)} W", "La potencia en bici que mantienes en un esfuerzo continuo de alrededor de una hora.") },
        p.css?.takeIf { it > 0 }?.let { Threshold("CSS", "${paceText(it)} /100 m", "El ritmo de natación que aguantas de forma continua (velocidad crítica).") },
        p.maxHr?.takeIf { it > 0 }?.let { Threshold("FC máxima", "${pyRound(it)} ppm", "Tu pulso más alto, de intervals.icu.") },
    )
}
