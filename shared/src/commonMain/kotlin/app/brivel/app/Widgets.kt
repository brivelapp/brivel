package app.brivel.app

import app.brivel.engine.fmt
import app.brivel.engine.pyRound
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

// Hoy a gusto de cada uno (decidido por Gabriel el 2026-11-23): la sesión de hoy va siempre fija arriba y, debajo,
// widgets que se añaden, se quitan, se ordenan y son medios (dos por fila) o anchos. El orden es de cada dispositivo:
// se guarda fuera de AppData y no se sincroniza (el ordenador tiene sitio para más).

/** Un widget puesto en Hoy: cuál y si ocupa toda la fila. */
@Serializable
data class WidgetSlot(val key: String, val wide: Boolean)

/**
 * Lo que enseña un widget. `readiness` y `week` los pinta cada app con lo de TodayScreen (preguntas y puntos de la
 * semana); los demás, con estos textos. Sin datos, `value` es null y `empty` dice cómo tenerlos.
 */
@Serializable
data class WidgetView(
    val key: String,
    val title: String,
    val wide: Boolean,
    val value: String? = null,  // «6 h 40», «58», «9.640»
    val unit: String = "",
    val detail: String? = null,  // «en tu normal», «en 26 días»
    val empty: String? = null,  // «Conecta tu reloj o Apple Salud»
)

/** Un widget del catálogo, para la lista de «Añadir a Hoy». */
@Serializable
data class WidgetChoice(val key: String, val title: String, val description: String, val inUse: Boolean)

/** Catálogo: clave, título y para qué sirve. El orden es el de la lista de «Añadir». */
val WIDGETS: List<Triple<String, String, String>> = listOf(
    Triple("readiness", "¿Cómo estás hoy?", "Tres preguntas que ajustan la sesión si no hay datos del reloj"),
    Triple("week", "Esta semana", "Minutos de cardio y días de fuerza"),
    Triple("sleep", "Sueño", "Horas de anoche frente a tu normal"),
    Triple("hrv", "VFC", "Tu variabilidad cardiaca frente a tu normal"),
    Triple("restingHR", "Pulso en reposo", "Frente a tu normal"),
    Triple("steps", "Pasos de ayer", "Los de un día completo"),
    Triple("nextRace", "Próxima carrera", "Cuántos días faltan"),
    Triple("badge", "Medalla más cerca", "La que tienes más a mano"),
    Triple("weight", "Peso", "El último que apuntaste o que dio tu báscula"),
    Triple("form", "Forma", "Forma física menos fatiga, de intervals.icu"),
)
private val WIDGET_KEYS = WIDGETS.map { it.first }.toSet()

/**
 * Progreso: los cuatro de arriba los elige cada uno (decidido el 2026-11-23); el resto de Progreso va debajo en
 * tarjetas. Mismos textos que los widgets de Hoy, más los que solo tienen sentido aquí.
 */
val PROGRESS_TILES: List<Triple<String, String, String>> = listOf(
    Triple("sleep", "Sueño", "Horas de anoche frente a tu normal"),
    Triple("activity", "Actividad", "Minutos de cardio esta semana"),
    Triple("weight", "Peso", "El último que apuntaste o que dio tu báscula"),
    Triple("medals", "Medallas", "Tu nivel y lo que falta para el siguiente"),
    Triple("hrv", "VFC", "Tu variabilidad cardiaca frente a tu normal"),
    Triple("restingHR", "Pulso en reposo", "Frente a tu normal"),
    Triple("vo2max", "VO2max", "El que estima tu reloj"),
    Triple("steps", "Pasos de ayer", "Los de un día completo"),
    Triple("form", "Forma", "Forma física menos fatiga, de intervals.icu"),
    Triple("waist", "Cintura", "La última que apuntaste"),
)
const val TILE_COUNT = 4
val DEFAULT_TILES = PROGRESS_TILES.take(TILE_COUNT).map { it.first }
private val TITLES = (WIDGETS + PROGRESS_TILES).associate { it.first to it.second }

/** Los cuatro de Progreso: cuatro claves distintas del catálogo; si no, los de serie. */
internal fun cleanTiles(keys: List<String>): List<String> {
    val valid = keys.filter { k -> PROGRESS_TILES.any { it.first == k } }.distinct()
    return if (valid.size == TILE_COUNT) valid else DEFAULT_TILES
}

/** Lo que trae Hoy de serie: las preguntas y la semana, como estaba antes de los widgets. */
val DEFAULT_WIDGETS = listOf(WidgetSlot("readiness", true), WidgetSlot("week", true))

/** Quita claves que ya no existen (una versión vieja) y repetidas: lo guardado nunca rompe Hoy. */
internal fun cleanSlots(slots: List<WidgetSlot>): List<WidgetSlot> =
    slots.filter { it.key in WIDGET_KEYS }.distinctBy { it.key }

internal fun addSlot(slots: List<WidgetSlot>, key: String, wide: Boolean): List<WidgetSlot> {
    require(key in WIDGET_KEYS) { "No existe el widget $key" }
    return if (slots.any { it.key == key }) slots else slots + WidgetSlot(key, wide)
}

internal fun moveSlot(slots: List<WidgetSlot>, key: String, to: Int): List<WidgetSlot> {
    val slot = slots.firstOrNull { it.key == key } ?: return slots
    val rest = slots - slot
    return rest.toMutableList().apply { add(to.coerceIn(0, rest.size), slot) }
}

/** Los widgets de Hoy con sus datos de hoy. */
internal fun widgetViews(slots: List<WidgetSlot>, data: AppData, today: LocalDate): List<WidgetView> =
    slotViews(cleanSlots(slots), data, today)

internal fun tileViews(keys: List<String>, data: AppData, today: LocalDate): List<WidgetView> =
    slotViews(cleanTiles(keys).map { WidgetSlot(it, false) }, data, today)

private fun slotViews(slots: List<WidgetSlot>, data: AppData, today: LocalDate): List<WidgetView> {
    val progress by lazy { buildProgress(data, today) }
    fun metric(key: String) = progress.metrics.firstOrNull { it.key == key }
    // «en tu normal» si cae en la media ± 1 DT de los últimos 60 días; si no, por encima o por debajo
    fun normal(m: Metric): String? {
        val v = m.latest ?: return null
        val low = m.normalLow ?: return null
        val high = m.normalHigh ?: return null
        return when { v < low -> "por debajo de tu normal"; v > high -> "por encima de tu normal"; else -> "en tu normal" }
    }
    val noWatch = "Conecta tu reloj, Health Connect o Apple Salud"
    return slots.map { s ->
        val title = TITLES.getValue(s.key)
        val base = WidgetView(s.key, title, s.wide)
        when (s.key) {
            "sleep" -> metric("sleep")?.let { m ->
                val h = m.latest ?: 0.0
                base.copy(value = "${h.toInt()} h ${pyRound((h - h.toInt()) * 60).toString().padStart(2, '0')}", detail = normal(m))
            } ?: base.copy(empty = noWatch)
            "hrv" -> metric("hrv")?.let { m -> base.copy(value = pyRound(m.latest ?: 0.0).toString(), unit = "ms", detail = normal(m)) }
                ?: base.copy(empty = noWatch)
            "restingHR" -> metric("restingHR")?.let { m -> base.copy(value = pyRound(m.latest ?: 0.0).toString(), unit = "ppm", detail = normal(m)) }
                ?: base.copy(empty = noWatch)
            "steps" -> metric("steps")?.let { m ->
                base.copy(value = thousands(pyRound(m.latest ?: 0.0)), detail = if (m.label == "Pasos de ayer") null else "último día con datos")
            } ?: base.copy(empty = noWatch)
            "weight" -> (data.measurements.filter { it.kind == "weight" }.maxByOrNull { it.date }?.value ?: metric("weight")?.latest)
                ?.let { base.copy(value = fmt(it, 1).replace('.', ','), unit = "kg") }
                ?: base.copy(empty = "Apúntalo en «Tus medidas», en Progreso")
            "form" -> metric("tsb")?.let { m -> base.copy(value = pyRound(m.latest ?: 0.0).toString(), detail = formText(m.latest ?: 0.0)) }
                ?: base.copy(empty = "Vincula intervals.icu")
            "nextRace" -> nextRace(data, today)?.let { (name, days) ->
                base.copy(value = if (days == 0) "Hoy" else "$days", unit = if (days == 0) "" else if (days == 1) "día" else "días", detail = name)
            } ?: base.copy(empty = "Sin carrera en tu objetivo")
            "badge" -> progress.badges?.next?.firstOrNull()?.let { b -> base.copy(value = b.title, detail = b.progress ?: b.text) }
                ?: base.copy(empty = "Ya las tienes todas")
            "vo2max" -> metric("vo2max")?.let { m -> base.copy(value = pyRound(m.latest ?: 0.0).toString(), detail = normal(m)) }
                ?: base.copy(empty = noWatch)
            "waist" -> data.measurements.filter { it.kind == "waist" }.maxByOrNull { it.date }
                ?.let { base.copy(value = fmt(it.value, 1).replace('.', ','), unit = "cm") } ?: base.copy(empty = "Apúntala en «Tus medidas», en Progreso")
            "activity" -> progress.weeklyMinutes.lastOrNull()?.let { p -> base.copy(value = pyRound(p.value).toString(), unit = "min", detail = "de ${progress.weeklyTarget} esta semana") }
                ?: base.copy(empty = noWatch)
            "medals" -> progress.badges?.let { b -> base.copy(value = "Nivel ${b.level}", detail = b.nextLevel) } ?: base
            else -> base  // readiness y week: los pinta cada app
        }
    }
}

/** La carrera de objetivo o la preparatoria más cercana que aún no ha pasado, y los días que faltan. */
private fun nextRace(data: AppData, today: LocalDate): Pair<String, Int>? {
    val goal = data.goal ?: return null
    val races = listOfNotNull(goal.race?.let { it.name to it.date }) + goal.otherRaces.map { it.name to it.date }
    return races.map { (name, date) -> name to (LocalDate.parse(date).toEpochDays() - today.toEpochDays()).toInt() }
        .filter { it.second >= 0 }.minByOrNull { it.second }
}

// Umbrales de la forma (TSB) [Inferencia: los rangos que usa intervals.icu para «fresco» y «óptimo»]
private fun formText(tsb: Double) = when {
    tsb > 5 -> "fresco"
    tsb < -30 -> "muy cargado"
    tsb < -10 -> "cargado: entrenando fuerte"
    else -> "en equilibrio"
}

private fun thousands(n: Int) = n.toString().reversed().chunked(3).joinToString(".").reversed()
