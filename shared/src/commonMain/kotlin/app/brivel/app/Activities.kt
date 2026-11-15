package app.brivel.app

// Actividades propias (docs/VISION.md §9, bloques B y G): las que el usuario importa de un archivo FIT o graba con el GPS
// del móvil. El resumen (con vueltas, zonas y mejores marcas por duración) va en AppData y se sincroniza; las series y el
// recorrido se guardan aparte, solo en el dispositivo que las importó o grabó: el recorrido no sale de él.

import app.brivel.engine.fmt
import app.brivel.engine.pyRound
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class Lap(val seconds: Double, val meters: Double? = null, val avgHr: Double? = null, val avgPower: Double? = null)

/**
 * El resumen de una actividad propia. `seconds`: tiempo en marcha. `power` y `speed`: la mejor media de potencia (W, bici)
 * y de velocidad (m/s, carrera) en cada duración de BEST_SECONDS, calculadas al importar con el registro completo.
 */
@Serializable
data class OwnActivity(
    val id: String,
    val sport: String,  // como Workout: walking / running / cycling / swimming_pool / strength / other
    val start: String,  // AAAA-MM-DDTHH:MM:SS, hora local
    val seconds: Double,
    val meters: Double? = null,
    val avgHr: Double? = null,
    val maxHr: Double? = null,
    val ascent: Double? = null,
    val avgPower: Double? = null,
    val zoneSeconds: List<Double>? = null,
    val power: Map<Int, Double> = emptyMap(),
    val speed: Map<Int, Double> = emptyMap(),
    val laps: List<Lap> = emptyList(),
    val source: String = "fit",  // fit, gpx o tcx (archivo importado) o gps (grabada con el móvil)
)

/** Las series de una actividad, reducidas a DETAIL_POINTS puntos; una lista vacía es un canal que no se midió. */
@Serializable
data class ActivityDetail(
    val seconds: List<Double>,  // segundos desde el inicio de cada punto
    val hr: List<Double?> = emptyList(),
    val speed: List<Double?> = emptyList(),
    val altitude: List<Double?> = emptyList(),
    val power: List<Double?> = emptyList(),
    val lat: List<Double?> = emptyList(),
    val lon: List<Double?> = emptyList(),
)

internal const val OWN_ID = "own-"
internal const val DETAIL_KEY = "brivel.activity."  // + id: las series, fuera de AppData (no se sincronizan)

/** Duraciones de las curvas de potencia y de ritmo, en segundos. */
val BEST_SECONDS = listOf(5, 15, 30, 60, 120, 300, 600, 1200, 1800, 3600)
private const val PAUSE_S = 10  // un hueco mayor entre dos puntos es una pausa [Inferencia]
private const val DETAIL_POINTS = 300
private const val MAX_LAPS = 30
private const val CLIMB_M = 3.0  // el desnivel solo suma subidas de 3 m o más: el ruido del GPS no cuenta [Inferencia]

fun OwnActivity.workout() = Workout(id, sport, start, seconds / 60, avgHr, maxHr, zoneSeconds, meters)

/** Metros entre dos puntos (haversine, radio medio de la Tierra 6.371 km). */
internal fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val rad = PI / 180
    val a = sin((lat2 - lat1) * rad / 2).pow(2) + cos(lat1 * rad) * cos(lat2 * rad) * sin((lon2 - lon1) * rad / 2).pow(2)
    return 2 * 6371000.0 * asin(sqrt(a))
}

private fun round(x: Double, decimals: Int): Double = 10.0.pow(decimals).let { round(x * it) / it }

/** Rellena lo que el archivo no trae: la distancia con el GPS y la velocidad con la distancia. */
private fun normalized(samples: List<FitSample>): List<FitSample> {
    var out = samples
    if (out.none { it.distance != null } && out.any { it.lat != null }) {
        var total = 0.0
        var last: FitSample? = null
        out = out.map { s ->
            if (s.lat != null && s.lon != null) {
                last?.let { total += metersBetween(it.lat!!, it.lon!!, s.lat, s.lon) }
                last = s
            }
            FitSample(s.time, s.lat, s.lon, s.altitude, s.hr, total, s.speed, s.power)
        }
    }
    if (out.none { it.speed != null } && out.any { it.distance != null }) {
        out = out.mapIndexed { i, s ->
            val prev = out.getOrNull(i - 1)
            val dt = if (prev == null) 0 else s.time - prev.time
            val speed = if (prev?.distance != null && s.distance != null && dt in 1..PAUSE_S) (s.distance - prev.distance) / dt else null
            FitSample(s.time, s.lat, s.lon, s.altitude, s.hr, s.distance, speed, s.power)
        }
    }
    return out
}

/** Un valor por segundo en marcha: cada punto vale hasta el siguiente; las pausas no cuentan. null si nunca hay dato. */
private fun perSecond(samples: List<FitSample>, value: (FitSample) -> Double?): List<Double>? {
    val out = ArrayList<Double>()
    var any = false
    for (i in 0 until samples.size - 1) {
        val dt = (samples[i + 1].time - samples[i].time).toInt()
        if (dt !in 1..PAUSE_S) continue
        val v = value(samples[i])
        if (v != null) any = true
        repeat(dt) { out += v ?: 0.0 }
    }
    return out.takeIf { any }
}

/** La mejor media en cada duración (media máxima), con sumas acumuladas. */
internal fun meanMax(values: List<Double>, durations: List<Int>): Map<Int, Double> {
    val sums = DoubleArray(values.size + 1)
    for (i in values.indices) sums[i + 1] = sums[i] + values[i]
    return durations.filter { it <= values.size }.associateWith { d -> round((d..values.size).maxOf { sums[it] - sums[it - d] } / d, 2) }
}

private fun zoneSeconds(samples: List<FitSample>, bounds: List<Double>): List<Double>? {
    val zones = DoubleArray(bounds.size + 1)
    for (i in 0 until samples.size - 1) {
        val hr = samples[i].hr ?: continue
        val dt = samples[i + 1].time - samples[i].time
        if (dt in 1..PAUSE_S) zones[bounds.count { hr > it }] += dt.toDouble()
    }
    return zones.toList().takeIf { it.sum() > 0 }
}

private fun ascent(samples: List<FitSample>): Double? {
    var ref = samples.firstNotNullOfOrNull { it.altitude } ?: return null
    var up = 0.0
    for (s in samples) {
        val a = s.altitude ?: continue
        if (a - ref >= CLIMB_M) up += a - ref
        if (kotlin.math.abs(a - ref) >= CLIMB_M) ref = a
    }
    return up
}

/** Vueltas automáticas cada `every` metros cuando el archivo no trae ninguna (lo grabado con el móvil). */
private fun autoLaps(samples: List<FitSample>, every: Double): List<Lap> {
    val laps = mutableListOf<Lap>()
    var seconds = 0.0
    var from = samples.firstOrNull()?.distance ?: return laps
    val hrs = mutableListOf<Double>()
    fun close(to: Double) {
        if (seconds > 0) laps += Lap(seconds, round(to - from, 1), hrs.takeIf { it.isNotEmpty() }?.average()?.let { round(it, 0) })
        seconds = 0.0; from = to; hrs.clear()
    }
    for (i in 1 until samples.size) {
        val dt = samples[i].time - samples[i - 1].time
        if (dt in 1..PAUSE_S) seconds += dt
        samples[i].hr?.let { hrs += it }
        val d = samples[i].distance ?: continue
        if (d - from >= every) close(d)
    }
    samples.lastOrNull { it.distance != null }?.distance?.let { if (it - from > 0) close(it) }
    return laps
}

private fun detail(samples: List<FitSample>): ActivityDetail? {
    if (samples.size < 2) return null
    val size = min(samples.size, DETAIL_POINTS)
    val groups = (0 until size).map { samples.subList(it * samples.size / size, (it + 1) * samples.size / size) }
    fun channel(decimals: Int, value: (FitSample) -> Double?): List<Double?> =
        groups.map { g -> g.mapNotNull(value).takeIf { it.isNotEmpty() }?.average()?.let { round(it, decimals) } }
            .takeIf { c -> c.any { it != null } } ?: emptyList()
    val t0 = samples.first().time
    return ActivityDetail(groups.map { (it.first().time - t0).toDouble() }, channel(0) { it.hr }, channel(2) { it.speed },
        channel(1) { it.altitude }, channel(0) { it.power }, channel(5) { it.lat }, channel(5) { it.lon })
}

/**
 * El resumen y las series de una actividad a partir de su registro. `bounds`: los límites de las zonas de FC del
 * usuario (heartRateZoneBounds). Lo que el reloj ya resumió (tiempo, distancia, medias) manda sobre lo calculado aquí.
 */
internal fun buildActivity(file: FitFile, startLocal: String, bounds: List<Double>, source: String): Pair<OwnActivity, ActivityDetail?> {
    val samples = normalized(file.samples)
    val moving = samples.zipWithNext { a, b -> b.time - a.time }.filter { it in 1..PAUSE_S }.sum().toDouble()
    val seconds = file.seconds ?: moving
    require(seconds > 0) { "La actividad no tiene duración" }
    val hrs = samples.mapNotNull { it.hr }
    val powers = if (file.sport == "cycling") perSecond(samples) { it.power } else null
    val speeds = if (file.sport == "running") perSecond(samples) { it.speed } else null
    val laps = file.laps.map { Lap(round(it.seconds, 1), it.meters?.let { m -> round(m, 1) }, it.avgHr, it.avgPower) }
        .ifEmpty { autoLaps(samples, if (file.sport == "cycling") 5000.0 else 1000.0) }
    val activity = OwnActivity(
        id = OWN_ID + startLocal, sport = file.sport, start = startLocal, seconds = round(seconds, 1),
        meters = (file.meters ?: samples.lastOrNull { it.distance != null }?.distance)?.takeIf { it > 0 }?.let { round(it, 1) },
        avgHr = file.avgHr ?: hrs.takeIf { it.isNotEmpty() }?.average()?.let { round(it, 0) },
        maxHr = file.maxHr ?: hrs.maxOrNull(),
        ascent = (file.ascent ?: ascent(samples))?.let { round(it, 0) },
        avgPower = file.avgPower ?: powers?.average()?.let { round(it, 0) },
        zoneSeconds = zoneSeconds(samples, bounds),
        power = powers?.let { meanMax(it, BEST_SECONDS) } ?: emptyMap(),
        speed = speeds?.let { meanMax(it, BEST_SECONDS.filter { d -> d >= 60 }) } ?: emptyMap(),  // en menos de 1 min manda el ruido del GPS
        laps = laps.take(MAX_LAPS), source = source,
    )
    return activity to detail(samples)
}

// ---------------------------------------------------------------- pantallas

@Serializable
data class Stat(val label: String, val value: String)

@Serializable
data class LapRow(val n: Int, val time: String, val distance: String, val pace: String, val hr: String)

/** Un punto de una línea dentro de un cuadrado de lado 1 (x a la derecha, y hacia abajo): las apps solo la pintan. */
@Serializable
data class XY(val x: Double, val y: Double)

@Serializable
data class Chart(val label: String, val low: String, val high: String, val line: List<XY>)

@Serializable
data class ActivityRow(val id: String, val date: String, val title: String, val sport: String, val detail: String?)

@Serializable
data class ActivityScreen(
    val id: String,
    val title: String,
    val dateLabel: String,
    val sport: String,
    val stats: List<Stat>,
    val zoneMinutes: List<Int>,
    val laps: List<LapRow>,
    val bests: List<Stat>,
    val charts: List<Chart>,  // vacío en un dispositivo que no tiene las series
    val track: List<XY>,  // el recorrido, sin mapa de fondo; vacío sin GPS
    val source: String,
    val gear: String? = null,  // el material con el que se hizo (Gear.kt)
)

private fun km(meters: Double) = "${fmt(meters / 1000, if (meters < 100000) 2 else 1).replace('.', ',')} km"

/** Ritmo a pie (/km), en el agua (/100 m) o velocidad en bici (km/h). */
internal fun speedText(sport: String, mps: Double): String = when {
    mps <= 0 -> "–"
    sport == "cycling" -> "${fmt(mps * 3.6, 1).replace('.', ',')} km/h"
    sport == "swimming_pool" -> "${raceTime(100 / mps / 60)} /100 m"
    else -> "${raceTime(1000 / mps / 60)} /km"
}

internal fun durationLabel(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} h"
}

fun OwnActivity.title(): String = SPORT_LABEL[sport] ?: "Actividad"

internal fun activityRows(activities: List<OwnActivity>): List<ActivityRow> = activities.sortedByDescending { it.start }.map {
    ActivityRow(it.id, it.start.take(10), it.title(), it.sport, activityDetail(it.sport, it.meters, it.seconds, it.avgHr, null))
}

/** Una serie como línea dentro del cuadrado: los huecos toman el valor anterior. */
private fun line(values: List<Double?>): List<XY> {
    val known = values.filterNotNull()
    if (known.size < 2) return emptyList()
    val (low, high) = known.min() to known.max()
    var last = known.first()
    return values.mapIndexed { i, v ->
        last = v ?: last
        XY(round(i.toDouble() / (values.size - 1), 4), round(if (high > low) 1 - (last - low) / (high - low) else 0.5, 4))
    }
}

/** El recorrido dentro del cuadrado, con el norte arriba y sin deformar (un grado de longitud mide cos(latitud) del de latitud). */
private fun track(detail: ActivityDetail): List<XY> {
    val points = detail.lat.zip(detail.lon).mapNotNull { (a, b) -> if (a != null && b != null) a to b else null }
    if (points.size < 2) return emptyList()
    val k = cos(points.map { it.first }.average() * PI / 180)
    val xs = points.map { it.second * k }
    val ys = points.map { -it.first }
    val side = max(xs.max() - xs.min(), ys.max() - ys.min()).takeIf { it > 0 } ?: return emptyList()
    val (dx, dy) = (side - (xs.max() - xs.min())) / 2 to (side - (ys.max() - ys.min())) / 2
    return xs.indices.map { XY(round((xs[it] - xs.min() + dx) / side, 4), round((ys[it] - ys.min() + dy) / side, 4)) }
}

internal fun activityScreen(a: OwnActivity, detail: ActivityDetail?, gear: String?): ActivityScreen {
    val stats = listOfNotNull(
        Stat("Tiempo", raceTime(a.seconds / 60)),
        a.meters?.let { Stat("Distancia", km(it)) },
        a.meters?.let { Stat(if (a.sport == "cycling") "Velocidad media" else "Ritmo medio", speedText(a.sport, it / a.seconds)) },
        a.avgHr?.let { Stat("Pulso medio", "${pyRound(it)} ppm") },
        a.maxHr?.let { Stat("Pulso máximo", "${pyRound(it)} ppm") },
        a.ascent?.takeIf { it > 0 }?.let { Stat("Desnivel", "${pyRound(it)} m") },
        a.avgPower?.let { Stat("Potencia media", "${pyRound(it)} W") },
    )
    val laps = if (a.laps.size < 2) emptyList() else a.laps.mapIndexed { i, l ->
        LapRow(i + 1, raceTime(l.seconds / 60), l.meters?.let { km(it) } ?: "–",
            l.meters?.takeIf { it > 0 && l.seconds > 0 }?.let { speedText(a.sport, it / l.seconds) } ?: "–", l.avgHr?.let { "${pyRound(it)}" } ?: "–")
    }
    val bests = a.power.map { (d, w) -> Stat(durationLabel(d), "${pyRound(w)} W") } +
        a.speed.map { (d, v) -> Stat(durationLabel(d), speedText(a.sport, v)) }
    fun chart(label: String, values: List<Double?>, text: (Double) -> String): Chart? {
        val points = line(values).takeIf { it.isNotEmpty() } ?: return null
        val known = values.filterNotNull()
        return Chart(label, text(known.min()), text(known.max()), points)
    }
    val charts = if (detail == null) emptyList() else listOfNotNull(
        chart("Pulso", detail.hr) { "${pyRound(it)} ppm" },
        chart(if (a.sport == "cycling") "Velocidad" else "Ritmo", detail.speed) { speedText(a.sport, it) },
        chart("Altitud", detail.altitude) { "${pyRound(it)} m" },
        chart("Potencia", detail.power) { "${pyRound(it)} W" },
    )
    return ActivityScreen(a.id, a.title(), dateLabel(a.start.take(10)) + ", " + a.start.substring(11, 16), a.sport, stats,
        a.zoneSeconds?.map { pyRound(it / 60) } ?: emptyList(), laps, bests, charts, detail?.let { track(it) } ?: emptyList(),
        if (a.source == "gps") "Grabada con este móvil" else "Importada de un archivo ${a.source.uppercase()}", gear)
}

// ---------------------------------------------------------------- curvas (Progreso)

@Serializable
data class CurveRow(val duration: String, val recent: String, val best: String)

/** La mejor media en cada duración: en los últimos 90 días y en todo lo guardado. */
@Serializable
data class Curve(val title: String, val note: String?, val rows: List<CurveRow>)

/**
 * FTP estimado: el 95 % de la mejor potencia de 20 min de los últimos 90 días. [Práctica: es el test de 20 min de Allen y
 * Coggan, «Training and Racing with a Power Meter»; el libro no se ha podido leer aquí. Una sesión que no fue a tope lo
 * deja corto.] Solo se enseña: el plan no lo usa.
 */
internal fun estimatedFtp(activities: List<OwnActivity>, since: String): Double? =
    activities.filter { it.start >= since }.mapNotNull { it.power[1200] }.maxOrNull()?.let { 0.95 * it }

internal fun curves(activities: List<OwnActivity>, since: String): List<Curve> {
    fun curve(title: String, note: String?, values: (OwnActivity) -> Map<Int, Double>, text: (Double) -> String): Curve? {
        val rows = BEST_SECONDS.mapNotNull { d ->
            val best = activities.mapNotNull { values(it)[d] }.maxOrNull() ?: return@mapNotNull null
            val recent = activities.filter { it.start >= since }.mapNotNull { values(it)[d] }.maxOrNull()
            CurveRow(durationLabel(d), recent?.let(text) ?: "–", text(best))
        }
        return rows.takeIf { it.isNotEmpty() }?.let { Curve(title, note, it) }
    }
    val ftp = estimatedFtp(activities, since)?.let {
        "FTP estimado: ${pyRound(it)} W, el 95 % de tu mejor potencia de 20 minutos. Si esa sesión no fue a tope, el real es más alto."
    }
    return listOfNotNull(
        curve("Curva de potencia (bici)", ftp, { it.power }) { "${pyRound(it)} W" },
        curve("Curva de ritmo (carrera)", null, { it.speed }) { speedText("running", it) },
    )
}
