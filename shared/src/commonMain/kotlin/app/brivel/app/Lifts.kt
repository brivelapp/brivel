package app.brivel.app

// Registro de fuerza y progresión doble (punto 8 del roast): sin apuntar pesos, "sube el peso cuando llegues al máximo
// de repeticiones" es una nota que nadie puede seguir.

/** Nombre del ejercicio sin la dosis: "Sentadilla con barra: 2 × 8–12 repeticiones" → "Sentadilla con barra". */
fun exerciseName(line: String): String = line.substringBefore(":").trim()

/** Rango de repeticiones de la línea del plan ("8–12"), o null si va por tiempo (planchas) o no lo indica. */
fun repRange(line: String): IntRange? {
    val m = Regex("""(\d+)[–-](\d+) repeticiones""").find(line) ?: return null
    return m.groupValues[1].toInt()..m.groupValues[2].toInt()
}

/** Lo último que apuntaste en ese ejercicio y qué toca hoy; `weightKg`: el peso que propone (null: tu peso o sin historial). */
data class LiftHint(val exercise: String, val last: LiftLog?, val suggestion: String?, val weightKg: Double? = null)

/**
 * Las series de cada día como un resumen: el peso más alto, las repeticiones mínimas con ese peso, cuántas series y la
 * menor reserva apuntada con ese peso.
 */
fun daySummaries(sets: List<SetLog>): List<LiftLog> = latestSets(sets).groupBy { it.date to it.exercise }
    .map { (key, day) ->
        val top = day.mapNotNull { it.weightKg }.maxOrNull()
        val atTop = day.filter { it.weightKg == top }
        LiftLog(key.first, key.second, top, atTop.minOf { it.reps }, day.size, atTop.mapNotNull { it.rir }.minOrNull())
    }

/** Una serie por fecha, ejercicio y número: si dos dispositivos corrigieron la misma, gana la última de la lista. */
fun latestSets(sets: List<SetLog>): List<SetLog> = sets.associateBy { Triple(it.date, it.exercise, it.set) }.values.toList()

/**
 * Progresión doble: si la última vez llegaste al máximo del rango en todas las series, sube el peso un ~5 % (redondeado a
 * 0,5 kg; con tu peso, una variante más difícil); si no, el mismo peso buscando una repetición más. Si te quedaste por
 * debajo del rango, o llevas 3 sesiones con el mismo peso sin ganar repeticiones, baja un ~10 % y vuelve a subir.
 * Si llegaste al máximo pero apuntaste menos reserva que `rir` (la que pide el plan), no sube: el peso aún es mucho.
 * [Práctica: ACSM 2009, progresión doble; Helms 2016, progresión por RIR; el 5 % y el 10 % son Inferencia dentro del
 * 2–10 % de la nota]
 */
fun liftHint(lifts: List<LiftLog>, line: String, range: IntRange? = repRange(line), rir: Int? = null): LiftHint {
    val name = exerciseName(line)
    val history = lifts.filter { it.exercise == name }.sortedBy { it.date }
    val last = history.lastOrNull()
    val recent = history.takeLast(3)
    val stalled = recent.size == 3 && last?.weightKg != null && recent.all { it.weightKg == last.weightKg } &&
        recent.last().reps <= recent.first().reps
    fun up(w: Double) = maxOf(kotlin.math.round(w * 1.05 * 2) / 2, w + 0.5)
    fun lighter(w: Double) = maxOf(kotlin.math.round(w * 0.9 * 2) / 2, 0.5)
    val w = last?.weightKg
    val (suggestion, weight) = when {
        last == null || range == null -> null to w
        last.reps >= range.last && rir != null && last.rir != null && last.rir < rir ->
            "Llegaste al máximo, pero más cerca del fallo de lo indicado: repite ${w?.let { kg(it) } ?: "la variante"} " +
                "hasta que te sobren $rir" to w
        last.reps >= range.last && w != null ->
            "Llegaste al máximo: hoy prueba ${kg(up(w))} y vuelve a ${range.first} repeticiones" to up(w)
        last.reps >= range.last -> "Llegaste al máximo: pasa a una variante más difícil y vuelve a ${range.first} repeticiones" to null
        last.reps < range.first && w != null ->
            "Te quedaste por debajo de ${range.first}: hoy baja a ${kg(lighter(w))} y haz ${range.first} o más" to lighter(w)
        last.reps < range.first -> "Te quedaste por debajo de ${range.first}: pasa a una variante más fácil" to null
        stalled -> "Tres sesiones sin ganar repeticiones con ${kg(w!!)}: baja a ${kg(lighter(w))} " +
            "y vuelve a subir desde ${range.first}" to lighter(w)
        w != null -> "Mismo peso (${kg(w)}) y busca ${last.reps + 1} repeticiones" to w
        else -> "Busca ${last.reps + 1} repeticiones" to null
    }
    return LiftHint(name, last, suggestion, weight)
}

fun kg(v: Double): String = (if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ',')) + " kg"
