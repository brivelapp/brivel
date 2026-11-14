package app.brivel.app

// Lo que Brivel te deja decidir a ti (docs/VISION.md §1 y §9, bloque A): qué serie toca en una superserie, el historial
// de cada ejercicio con su 1RM estimado, los discos de la barra y las medidas corporales.

import app.brivel.engine.fmt
import kotlinx.serialization.Serializable

/** Número con coma decimal y sin ceros de más: 62.5 → "62,5", 60.0 → "60". */
internal fun num(v: Double, decimals: Int = 2): String =
    fmt(v, decimals).let { if ('.' in it) it.trimEnd('0').trimEnd('.') else it }.replace('.', ',')

// ---------------------------------------------------------------- superseries

/**
 * La etiqueta de superserie de cada posición (A, B…) y si es la última de la suya; null: va sola. `linked`: las
 * posiciones que van con la anterior.
 */
internal fun supersetLabels(count: Int, linked: Set<Int>): List<Pair<String, Boolean>?> {
    val out = MutableList<Pair<String, Boolean>?>(count) { null }
    var letter = 'A'
    var i = 0
    while (i < count) {
        var j = i
        while (j + 1 < count && j + 1 in linked) j++
        if (j > i) {
            for (k in i..j) out[k] = letter.toString() to (k == j)
            letter++
        }
        i = j + 1
    }
    return out
}

/** La serie que toca: la posición del ejercicio en la sesión y el número de la serie. */
@Serializable
data class NextSet(val exercise: Int, val set: Int)

/**
 * Qué serie toca después de apuntar una en el ejercicio `current`. En una superserie, el ejercicio de su grupo con menos
 * series hechas (así se alternan); si no, el mismo mientras le queden; después, el primero al que le falten. null: sesión
 * completa.
 */
fun nextSet(exercises: List<StrengthExercise>, current: Int): NextSet? {
    fun pending(e: StrengthExercise) = e.done.size < e.sets
    val here = exercises.getOrNull(current)
    val group = here?.superset?.let { g -> exercises.indices.filter { exercises[it].superset == g && pending(exercises[it]) } }
    val index = group?.minByOrNull { exercises[it].done.size }
        ?: current.takeIf { here != null && pending(here) }
        ?: exercises.indexOfFirst { pending(it) }.takeIf { it >= 0 }
        ?: return null
    val taken = exercises[index].done.map { it.set }.toSet()
    return NextSet(index, (1..exercises[index].sets).firstOrNull { it !in taken } ?: (taken.max() + 1))
}

// ---------------------------------------------------------------- 1RM estimado e historial de un ejercicio

/** Por encima de estas repeticiones no se estima el 1RM. */
const val E1RM_MAX_REPS = 10

/**
 * 1RM estimado con la fórmula de Epley: peso × (1 + repeticiones / 30); con una repetición, el propio peso. null con más
 * de 10 repeticiones o sin peso.
 * [Práctica: Epley 1985; el tope de 10 repeticiones, Reynolds, Gordon y Robergs 2006 (PMID 16937972): las ecuaciones
 * lineales pierden precisión por encima de 10 RM]
 */
fun e1rm(weightKg: Double, reps: Int): Double? = when {
    weightKg <= 0 || reps !in 1..E1RM_MAX_REPS -> null
    reps == 1 -> weightKg
    else -> weightKg * (1 + reps / 30.0)
}

const val E1RM_EXPLANATION = "El peso que podrías levantar una sola vez, estimado con tu mejor serie de cada día " +
    "(fórmula de Epley, con series de 10 repeticiones o menos). Es una estimación, no una prueba de máximos."

/** Un día de un ejercicio: su mejor serie, cuántas series de trabajo hizo y los kilos movidos (peso × repeticiones). */
@Serializable
data class ExerciseDay(val date: String, val best: String, val sets: Int, val volumeKg: Double, val e1rm: Double?)

/**
 * El historial de un ejercicio: los últimos días (el más reciente primero), la gráfica (1RM estimado; sin peso, las
 * repeticiones o los segundos de la mejor serie) y sus mejores marcas.
 */
@Serializable
data class ExerciseHistory(val name: String, val days: List<ExerciseDay>, val chart: Metric?, val records: List<Milestone>)

private fun setLabel(s: SetLog, timed: Boolean): String =
    (s.weightKg?.takeIf { it > 0 }?.let { "${num(it)} kg × " } ?: "") + if (timed) "${s.reps}″" else "${s.reps}"

/** Solo cuentan las series de trabajo (ni calentamiento ni descendentes). `limit`: cuántos días enseña la lista. */
fun exerciseHistory(name: String, sets: List<SetLog>, custom: List<CustomExercise> = emptyList(), limit: Int = 10): ExerciseHistory {
    val timed = exerciseInfo(name, custom)?.timed == true
    val byDay = latestSets(sets).filter { it.exercise == name && it.counts() }.groupBy { it.date }.toList().sortedBy { it.first }
    // La mejor serie: la de más 1RM estimado; sin él, más peso y, a igual peso, más repeticiones (o segundos)
    val best = compareBy<SetLog>({ e1rm(it.weightKg ?: 0.0, it.reps) ?: 0.0 }, { it.weightKg ?: 0.0 }, { it.reps })
    val days = byDay.map { (date, day) ->
        val top = day.maxWith(best)
        ExerciseDay(date, setLabel(top, timed), day.size, if (timed) 0.0 else day.sumOf { (it.weightKg ?: 0.0) * it.reps },
            day.mapNotNull { e1rm(it.weightKg ?: 0.0, it.reps) }.maxOrNull())
    }
    val weighted = days.any { it.e1rm != null }
    val points = if (weighted) days.mapNotNull { d -> d.e1rm?.let { Point(d.date, it) } }
        else byDay.map { (date, day) -> Point(date, day.maxOf { it.reps }.toDouble()) }
    val chart = points.takeIf { it.isNotEmpty() }?.let {
        if (weighted) Metric("e1rm", "1RM estimado", "kg", E1RM_EXPLANATION, it, it.last().value, null, null, true)
        else Metric("best", if (timed) "Mejor serie" else "Repeticiones de la mejor serie", if (timed) "s" else "",
            "Lo más que aguantaste o repetiste en una serie cada día.", it, it.last().value, null, null, true)
    }
    val all = byDay.flatMap { it.second }
    val records = listOfNotNull(
        all.filter { (it.weightKg ?: 0.0) > 0 }.maxWithOrNull(compareBy<SetLog>({ it.weightKg }, { it.reps }).thenByDescending { it.date })
            ?.let { Milestone("Más peso", setLabel(it, timed), it.date) },
        days.filter { it.e1rm != null }.maxByOrNull { it.e1rm!! }?.let { Milestone("Mejor 1RM estimado", "${num(it.e1rm!!, 1)} kg", it.date) },
        days.filter { it.volumeKg > 0 }.maxByOrNull { it.volumeKg }?.let { Milestone("Más kilos en un día", "${num(it.volumeKg, 0)} kg", it.date) },
        all.filter { (it.weightKg ?: 0.0) <= 0 }.maxWithOrNull(compareBy<SetLog> { it.reps }.thenByDescending { it.date })
            ?.let { Milestone(if (timed) "Más tiempo" else "Más repeticiones", setLabel(it, timed), it.date) },
    )
    return ExerciseHistory(name, days.takeLast(limit).reversed(), chart, records)
}

// ---------------------------------------------------------------- discos de la barra

/** Los discos habituales de un gimnasio, en kilos. */
val PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

// shortcut: barra olímpica de 20 kg fija; hacerla elegible (15 kg, 10 kg) cuando alguien entrene con otra
const val BAR_KG = 20.0

/** Discos por cada lado para que la barra pese `totalKg`; null si pesa menos que la barra o no sale con esos discos. */
fun platesPerSide(totalKg: Double, barKg: Double = BAR_KG): List<Double>? {
    var left = (totalKg - barKg) / 2
    if (left < 0) return null
    val out = mutableListOf<Double>()
    for (p in PLATES) while (left >= p - 1e-9) {
        out += p
        left -= p
    }
    return out.takeIf { left < 1e-9 }
}

/** «Por lado: 20 + 5 + 2,5 kg (barra de 20 kg)», o null si ese peso no se puede montar. */
fun platesText(totalKg: Double): String? = platesPerSide(totalKg)?.let { side ->
    if (side.isEmpty()) "Solo la barra (${num(BAR_KG)} kg)"
    else "Por lado: ${side.joinToString(" + ") { num(it) }} kg (barra de ${num(BAR_KG)} kg)"
}

// ---------------------------------------------------------------- medidas corporales

/** Una medida que se puede apuntar: nombre, unidad y entre qué valores tiene sentido. */
class Measure(val label: String, val unit: String, val range: ClosedFloatingPointRange<Double>)

/** Las medidas que se apuntan a mano, en el orden de la pantalla. Solo lo que mide una báscula o una cinta (VISION §2). */
val MEASURES: Map<String, Measure> = linkedMapOf(
    "weight" to Measure("Peso", "kg", 20.0..400.0),
    "waist" to Measure("Cintura", "cm", 30.0..250.0),
    "hips" to Measure("Cadera", "cm", 30.0..250.0),
    "chest" to Measure("Pecho", "cm", 30.0..250.0),
    "arm" to Measure("Brazo", "cm", 10.0..100.0),
    "thigh" to Measure("Muslo", "cm", 20.0..150.0),
)

/** Cada medida apuntada como una tendencia de Progreso, en el orden de MEASURES; las que no tienen datos no salen. */
fun measureMetrics(measurements: List<Measurement>): List<Metric> = MEASURES.mapNotNull { (kind, m) ->
    val points = measurements.filter { it.kind == kind }.sortedBy { it.date }.map { Point(it.date, it.value) }
    if (points.isEmpty()) null
    else Metric("measure-$kind", m.label, m.unit, "${m.label}, apuntado por ti.", points, points.last().value, null, null, null)
}
