package app.brivel.app

// Medallas (pedido de Gabriel, 2026-11-14): logros con puntos y niveles. Las medallas y sus nombres son propios. Premian
// lo que el plan ya busca (constancia semanal, distancias, fuerza y descanso); a propósito no hay rachas de días seguidos
// entrenando, que empujan a saltarse el descanso. Una medalla ganada se guarda con su fecha y no se pierde aunque los
// datos del reloj caduquen.

import app.brivel.engine.plusDays
import app.brivel.engine.pyRound
import app.brivel.intervals.HEALTH_ACTIVITY_SPORTS
import app.brivel.intervals.MIN_ACTIVITY_S
import app.brivel.intervals.date
import app.brivel.intervals.num
import app.brivel.intervals.str
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

private const val SLEEP_HOURS = 7.0  // [Práctica: consenso AASM/SRS, Watson et al. 2015, Sleep 38(6):843-4, PMID 26039963; cita comprobada, la frase de las 7 h no se ha podido leer]

/** Semanas (o noches) seguidas hasta la última completa; la de ahora suma si ya cumple. De la más antigua a la actual. */
internal fun streak(done: List<Boolean>): Int {
    val upTo = if (done.lastOrNull() == true) done.size else done.size - 1
    return done.take(upTo.coerceAtLeast(0)).takeLastWhile { it }.size
}

/** Lo que miran las medallas, calculado una vez. */
internal class BadgeFacts(data: AppData, private val today: LocalDate) {
    private val cache = data.deviceData(today)
    private val acts = (cache?.activities ?: emptyList()).filter { it.date().isNotEmpty() && it.date() <= today.toString() }
    private fun of(sport: String) = acts.filter { HEALTH_ACTIVITY_SPORTS[it["type"].str()] == sport }
    private fun km(a: kotlinx.serialization.json.JsonObject) = (a["distance"].num() ?: 0.0) / 1000

    /** La actividad más larga del deporte: kilómetros y su fecha. */
    fun longest(sport: String): Pair<Double, String?> = of(sport).maxByOrNull { km(it) }?.let { km(it) to it.date() } ?: (0.0 to null)
    fun kmIn(sport: String, days: Int): Pair<Double, String?> =
        of(sport).filter { it.date() > today.plusDays(-days).toString() }.sumOf { km(it) } to null

    val first: Pair<Double, String?> = acts.filter { (it["moving_time"].num() ?: 0.0) >= MIN_ACTIVITY_S }.minOfOrNull { it.date() }
        .let { (if (it == null) 0.0 else 1.0) to it }
    val whoWeeks = streak(weeklyMinutes(acts, today, 52).map { it.value >= 150 }).toDouble() to null

    private val strengthDates = (data.sets.filter { it.counts() }.map { it.date } + of("strength").map { it.date() } +
        data.manualDone.filter { it.sport == "strength" }.map { it.date }).toSet()
    val strengthWeeks = streak((0 until 52).reversed().map { w ->
        val monday = mondayOf(today).plusDays(-7 * w)
        strengthDates.count { it >= monday.toString() && it < monday.plusDays(7).toString() } >= 2
    }).toDouble() to null
    val strengthDays = data.sets.filter { it.counts() }.map { it.date }.distinct().size.toDouble() to null
    /** Kilos movidos en total: peso × repeticiones de las series que cuentan; los ejercicios por tiempo no suman. */
    val volume = data.sets.filter { it.counts() && exerciseInfo(it.exercise, data.customExercises)?.timed != true }
        .sumOf { (it.weightKg ?: 0.0) * it.reps } to null

    /** Noches seguidas durmiendo 7 h o más, hasta la última con dato (la de hoy puede no haber llegado). */
    val sleepNights: Pair<Double, String?> = run {
        val sleep = (cache?.wellness ?: emptyList()).mapNotNull { w -> w["id"].str()?.let { d -> w["sleepSecs"].num()?.takeIf { it > 0 }?.let { d to it / 3600 } } }.toMap()
        var day = if (today.toString() in sleep) today else today.plusDays(-1)
        var nights = 0
        while ((sleep[day.toString()] ?: 0.0) >= SLEEP_HOURS) { nights++; day = day.plusDays(-1) }
        nights.toDouble() to null
    }

    /** La carrera objetivo: hay una actividad de ese día. */
    val race: Pair<Double, String?> = data.goal?.race?.takeIf { data.goal.kind == GoalKind.RACE && it.date <= today.toString() }
        ?.let { r -> if (acts.any { it.date() == r.date }) 1.0 to r.date else null } ?: (0.0 to null)
}

/** `chain`: medallas de la misma familia, de menos a más; solo se enseña la siguiente por ganar. */
internal class BadgeDef(val id: String, val chain: String, val group: String, val title: String, val text: String,
    val points: Int, val target: Double, val unit: String, val value: (BadgeFacts) -> Pair<Double, String?>)

private fun chain(name: String, group: String, unit: String, text: (String) -> String, value: (BadgeFacts) -> Pair<Double, String?>,
    vararg tiers: Triple<Double, Int, String>) = tiers.map { (target, points, title) ->
    BadgeDef("$name-${num(target)}", name, group, title, text(amount(target, unit)), points, target, unit, value)
}

private fun amount(x: Double, unit: String) =
    "${if (x >= 10000) pyRound(x).toString().reversed().chunked(3).joinToString(".").reversed() else num(x)} $unit".trim()

/** Todas las medallas. Puntos: 1, 2, 4 u 8 según lo que cuestan. */
internal val BADGES: List<BadgeDef> = listOf(
    chain("first", "Constancia", "", { "Registra tu primera actividad de 10 minutos o más." }, { it.first }, Triple(1.0, 1, "Primer paso")),
    chain("who", "Constancia", "semanas", { "Cumple $it seguidas la recomendación de la OMS: 150 minutos de actividad moderada a la semana." },
        { it.whoWeeks }, Triple(4.0, 2, "Un mes en marcha"), Triple(12.0, 4, "Un trimestre en marcha"), Triple(26.0, 8, "Medio año en marcha"),
        Triple(52.0, 8, "Un año en marcha")),
    chain("strength-weeks", "Constancia", "semanas", { "Haz fuerza dos días o más por semana durante $it seguidas, como recomienda la OMS." },
        { it.strengthWeeks }, Triple(4.0, 2, "Fuerza al día"), Triple(12.0, 4, "Fuerza de costumbre")),
    chain("race", "Constancia", "", { "Haz la carrera que te pusiste como objetivo." }, { it.race }, Triple(1.0, 8, "Dorsal puesto")),
    chain("run", "Carrera", "km", { "Corre $it en una sola actividad." }, { it.longest("running") },
        Triple(5.0, 1, "5 km"), Triple(10.0, 2, "10 km"), Triple(21.1, 4, "Media maratón"), Triple(42.2, 8, "Maratón")),
    chain("run-month", "Carrera", "km", { "Corre $it en 30 días." }, { it.kmIn("running", 30) },
        Triple(50.0, 1, "Mes de 50"), Triple(100.0, 2, "Mes de 100"), Triple(200.0, 4, "Mes de 200")),
    chain("run-year", "Carrera", "km", { "Corre $it en un año." }, { it.kmIn("running", 365) }, Triple(1000.0, 8, "Mil al año")),
    chain("ride", "Bici", "km", { "Rueda $it en una sola salida." }, { it.longest("cycling") },
        Triple(40.0, 1, "40 km"), Triple(100.0, 4, "Cien"), Triple(160.0, 8, "Cien millas")),
    chain("ride-month", "Bici", "km", { "Rueda $it en 30 días." }, { it.kmIn("cycling", 30) },
        Triple(300.0, 2, "Mes de 300"), Triple(600.0, 4, "Mes de 600"), Triple(1000.0, 8, "Mes de 1.000")),
    chain("ride-year", "Bici", "km", { "Rueda $it en un año." }, { it.kmIn("cycling", 365) }, Triple(5000.0, 8, "Cinco mil al año")),
    chain("swim", "Natación", "km", { "Nada $it en una sola sesión." }, { it.longest("swimming_pool") },
        Triple(1.0, 1, "Primer kilómetro"), Triple(3.0, 4, "Tres mil metros")),
    chain("walk", "Caminar", "km", { "Camina $it en una sola salida." }, { it.longest("walking") },
        Triple(10.0, 1, "Paseo largo"), Triple(20.0, 2, "Caminata de 20")),
    chain("strength-days", "Fuerza", "días", { "Apunta series de fuerza $it distintos." }, { it.strengthDays },
        Triple(1.0, 1, "Primera sesión de fuerza"), Triple(10.0, 1, "10 días de fuerza"), Triple(50.0, 2, "50 días de fuerza"),
        Triple(100.0, 4, "100 días de fuerza")),
    chain("volume", "Fuerza", "kg", { "Mueve $it en total, sumando peso por repeticiones." }, { it.volume },
        Triple(10000.0, 1, "10 toneladas"), Triple(100000.0, 2, "100 toneladas"), Triple(1000000.0, 4, "1.000 toneladas")),
    chain("sleep", "Descanso", "noches", { "Duerme 7 horas o más durante $it seguidas." }, { it.sleepNights },
        Triple(7.0, 2, "Semana bien dormida"), Triple(28.0, 4, "Mes bien dormido")),
).flatten()

/** Puntos con los que se alcanza cada nivel, del 1 en adelante. [Diseño propio] */
internal val LEVELS = listOf(0, 3, 8, 15, 25, 40, 55, 75, 95, 120)

/** Las medallas recién ganadas se añaden con su fecha (la de la actividad, si se sabe; si no, hoy). Nunca se quita ninguna. */
internal fun awardBadges(data: AppData, today: LocalDate): AppData {
    val missing = BADGES.filter { it.id !in data.badges }
    if (missing.isEmpty()) return data
    val facts = BadgeFacts(data, today)
    val earned = missing.mapNotNull { b -> b.value(facts).takeIf { it.first >= b.target }?.let { b.id to (it.second ?: today.toString()) } }
    return if (earned.isEmpty()) data else data.copy(badges = data.badges + earned)
}

/**
 * Las medallas tras borrar una actividad del día `since`: se quitan las ganadas desde ese día que ya no se cumplen. Una
 * medalla depende de una actividad solo si se ganó el día de esa actividad o después, así que las anteriores se quedan
 * (también las que salieron de datos del reloj ya caducados).
 */
internal fun revokeBadges(data: AppData, since: String, today: LocalDate): Map<String, String> {
    val facts = BadgeFacts(data, today)
    val defs = BADGES.associateBy { it.id }
    return data.badges.filter { (id, date) -> date < since || defs[id]?.let { it.value(facts).first >= it.target } != false }
}

@Serializable
data class BadgeView(val id: String, val title: String, val text: String, val group: String, val points: Int,
    val earned: String?,  // "12 nov 2026"; null si falta
    val progress: String?,  // "7,2 de 10 km"
    val fraction: Double)

@Serializable
data class BadgesScreen(
    val level: Int, val points: Int,
    val nextLevel: String?,  // "Te faltan 3 puntos para el nivel 4"
    val earned: List<BadgeView>,  // las más recientes primero
    val next: List<BadgeView>,  // la siguiente de cada familia, las más cercanas primero
    val garminNote: String? = null,  // las medallas salen en parte de actividades de Garmin (atribución obligatoria)
)

internal fun badgesScreen(data: AppData, today: LocalDate): BadgesScreen {
    val facts = BadgeFacts(data, today)
    val earned = BADGES.filter { it.id in data.badges }.sortedByDescending { data.badges[it.id] }.map { b ->
        val date = data.badges[b.id]!!
        BadgeView(b.id, b.title, b.text, b.group, b.points, "${shortDate(date)} ${date.take(4)}", null, 1.0)
    }
    val next = BADGES.filter { it.id !in data.badges }.distinctBy { it.chain }.map { b ->
        val value = b.value(facts).first.coerceAtMost(b.target)
        BadgeView(b.id, b.title, b.text, b.group, b.points, null,
            if (b.target == 1.0) null else "${if (value >= 100) amount(pyRound(value).toDouble(), "") else num(value, 1)} de ${amount(b.target, b.unit)}",
            value / b.target)
    }.sortedByDescending { it.fraction }
    val points = BADGES.filter { it.id in data.badges }.sumOf { it.points }
    val level = LEVELS.indexOfLast { points >= it } + 1
    return BadgesScreen(level, points, LEVELS.getOrNull(level)?.let { "Te faltan ${it - points} ${if (it - points == 1) "punto" else "puntos"} para el nivel ${level + 1}" },
        earned, next, GARMIN_DERIVED.takeIf { data.usesGarmin() })
}
