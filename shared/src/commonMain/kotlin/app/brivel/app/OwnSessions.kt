package app.brivel.app

// Sesiones propias de resistencia (docs/VISION.md §9, bloque C): el usuario monta sus pasos (minutos, zona, repeticiones
// y descanso) y elige qué días de la semana la hace, o la pone un día concreto. Esos días sustituye a la sesión de ese
// deporte del plan (o se añade); el resto del plan sigue. Se convierte en una sesión del motor: mismos objetivos por
// zona, mismo envío al reloj y misma carga que las del catálogo.

import app.brivel.engine.Block
import app.brivel.engine.Profile
import app.brivel.engine.Session
import app.brivel.engine.ownSession
import kotlinx.serialization.Serializable

/** Un paso: `minutes` en la zona `zone`, `reps` veces con `restMinutes` suaves entre una y otra. */
@Serializable
data class OwnStep(val name: String, val minutes: Double, val zone: String, val reps: Int = 1, val restMinutes: Double = 0.0)

/**
 * Sesión propia. `days`: días de la semana ("MON"…) en los que se hace, desde `since` (lo pasado no cambia al crearla o
 * editarla); `dates`: días concretos en los que se puso a mano.
 */
@Serializable
data class OwnSession(val id: String, val name: String, val sport: String, val steps: List<OwnStep>,
    val days: List<String> = emptyList(), val dates: List<String> = emptyList(), val since: String)

/** Los deportes en los que se puede montar una sesión. */
val OWN_SPORTS = listOf("running", "cycling", "swimming_pool", "walking")

/** Las zonas que se pueden elegir, con su nombre llano (el técnico va detrás, VISION §4.2). */
val OWN_ZONES: Map<String, String> = linkedMapOf("Z1" to "Muy suave", "Z2" to "Cómodo", "Z3" to "Algo exigente",
    "Z4" to "Fuerte", "Z5" to "Muy fuerte")

/** Las claves de OWN_ZONES en su orden (un diccionario de Swift no lo guarda). */
val OWN_ZONE_KEYS: List<String> = OWN_ZONES.keys.toList()

/** Minutos de la sesión: cada paso por sus repeticiones, con sus descansos. */
fun OwnSession.minutes(): Double = steps.sumOf { it.reps * it.minutes + (if (it.reps > 1) it.reps * it.restMinutes else 0.0) }

/** La sesión del motor: objetivos por zona, techo del cribado y carga, como las del catálogo. */
internal fun OwnSession.session(profile: Profile): Session = ownSession(id, name, sport,
    steps.mapIndexed { i, s -> Block(s.name.ifBlank { "Paso ${i + 1}" }, s.reps, s.minutes, s.zone, s.restMinutes) }, profile)

/** Comprueba una sesión antes de guardarla; devuelve la misma con los textos limpios. */
fun validOwnSession(s: OwnSession): OwnSession {
    val name = s.name.trim()
    require(name.length in 1..60) { "Ponle un nombre a la sesión (60 letras como mucho)" }
    require(s.sport in OWN_SPORTS) { "Deporte no válido" }
    require(s.steps.size in 1..20) { "Entre 1 y 20 pasos" }
    s.steps.forEachIndexed { i, step ->
        val n = i + 1
        require(step.name.length <= 40) { "El nombre del paso $n es demasiado largo" }
        require(step.minutes in 0.5..300.0) { "Paso $n: entre medio minuto y 300 minutos" }
        require(step.zone in OWN_ZONES) { "Paso $n: elige una intensidad" }
        require(step.reps in 1..50) { "Paso $n: entre 1 y 50 repeticiones" }
        require(step.restMinutes in 0.0..30.0) { "Paso $n: el descanso va de 0 a 30 minutos" }
    }
    require(s.days.all { it in WEEKDAY_LABEL }) { "Día de la semana no válido" }
    val clean = s.copy(name = name, steps = s.steps.map { it.copy(name = it.name.trim()) }, days = s.days.distinct())
    require(clean.minutes() <= 600) { "La sesión no puede pasar de 10 horas" }
    return clean
}
