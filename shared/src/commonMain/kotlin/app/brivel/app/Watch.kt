package app.brivel.app

// Sesión de Brivel → sesión para el reloj en un formato propio, que el relevo de Garmin (brivel-garmin, privado) pasa
// al de Garmin Connect. Así este repositorio, que es público, no refleja la documentación de Garmin.

import app.brivel.engine.Profile
import app.brivel.engine.SWIM
import app.brivel.engine.Session
import app.brivel.engine.describe
import app.brivel.engine.metric
import app.brivel.engine.pyRound
import app.brivel.engine.stepRange
import kotlinx.serialization.Serializable

/** Objetivo de un paso: `hr` en ppm, `power` en vatios o `pace` en s/km; de `low` a `high`. */
@Serializable
data class WatchTarget(val metric: String, val low: Double, val high: Double)

/**
 * Un paso. `kind`: warmup, active, interval, recovery, rest o cooldown. Dura `seconds`, `meters` o `reps` (si no lleva
 * ninguno, hasta pulsar vuelta). `exercise`: el nombre de Brivel del ejercicio de fuerza.
 */
@Serializable
data class WatchStep(
    val kind: String,
    val seconds: Int? = null,
    val meters: Int? = null,
    val reps: Int? = null,
    val target: WatchTarget? = null,
    val exercise: String? = null,
    val weightKg: Double? = null,
    val note: String? = null,
)

/** Pasos que se repiten `times` veces (1: sin repetir). */
@Serializable
data class WatchBlock(val times: Int, val steps: List<WatchStep>)

/** `sport`: running, cycling, swimming, strength, walking u other. */
@Serializable
data class WatchWorkout(val date: String, val sport: String, val name: String, val description: String,
    val blocks: List<WatchBlock>)

private val WATCH_SPORT = mapOf("running" to "running", "cycling" to "cycling", "swimming_pool" to "swimming",
    "swimming_open" to "swimming", "walking" to "walking", "strength" to "strength")

/** Sesión de resistencia → pasos; los objetivos, los mismos que se mandan a intervals.icu (`stepTarget`). */
fun watchWorkout(session: Session, date: String, profile: Profile): WatchWorkout {
    val mode = metric(session, profile)
    fun target(zone: String) = stepRange(zone, session, profile, mode)?.let { (m, lo, hi) -> WatchTarget(m, lo, hi) }
    val swim = session.sport in SWIM
    val blocks = session.blocks.map { b ->
        val kind = when {
            b.block == "calentamiento" -> "warmup"
            b.block == "vuelta a la calma" -> "cooldown"
            b.reps > 1 -> "interval"
            b.zone == "Z1" -> "recovery"
            else -> "active"
        }
        val note = listOfNotNull(b.block, b.detail).joinToString(": ")
        val work = WatchStep(kind, seconds = if (b.meters != null) null else pyRound(b.minutes * 60), meters = b.meters,
            target = target(b.zone), note = note)
        val rest = b.restMinutes?.takeIf { it > 0 && b.reps > 1 }?.let {
            // En la piscina se descansa parado: sin objetivo
            if (swim) WatchStep("rest", seconds = pyRound(it * 60))
            else WatchStep("recovery", seconds = pyRound(it * 60), target = target(b.restZone ?: "Z1"))
        }
        WatchBlock(b.reps.coerceAtLeast(1), listOfNotNull(work, rest))
    }
    return WatchWorkout(date, WATCH_SPORT[session.sport] ?: "other", "Brivel · ${session.name}",
        describe(session, profile).take(1024), blocks)
}

/** Fuerza serie a serie: cada ejercicio, sus series con el peso que propone Brivel y el descanso entre ellas. */
fun watchStrength(s: StrengthScreen, name: String): WatchWorkout {
    val blocks = s.exercises.map { e ->
        val note = (if (e.timed) "${e.repsLow}–${e.repsHigh} s" else "${e.repsLow}–${e.repsHigh} repeticiones") +
            (e.rir?.let { " · deja $it en reserva" } ?: "")
        val work = if (e.timed) WatchStep("active", seconds = e.repsHigh, exercise = e.name, note = note)
            else WatchStep("active", reps = e.repsHigh, exercise = e.name, weightKg = e.weightKg, note = note)
        WatchBlock(e.sets, listOf(work, WatchStep("rest", seconds = e.restSeconds)))
    }
    val description = (s.exercises.map { "• ${it.name}: ${it.sets} × ${it.repsLow}–${it.repsHigh}" } +
        s.other.map { "• $it" } + listOfNotNull(s.notes)).joinToString("\n")
    return WatchWorkout(s.date, "strength", name, description.take(1024), blocks)
}
