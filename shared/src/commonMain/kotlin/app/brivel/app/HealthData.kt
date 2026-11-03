package app.brivel.app

// Datos de salud del propio móvil (Health Connect en Android, Apple Salud en iOS): la app funciona sin intervals.icu
// (docs/VISION.md §7, fase 3). Cada plataforma lee sus registros y los entrega con esta forma común; aquí se convierten
// al formato que ya usa el motor, así el plan, el calendario y Progreso no distinguen de dónde vienen.

import app.brivel.engine.K_ATL
import app.brivel.engine.K_CTL
import app.brivel.engine.hrTss
import app.brivel.engine.nextLoad
import app.brivel.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.max
import kotlin.math.min

/** Un día de datos del reloj o la báscula, tal como los guarda el sistema. */
@Serializable
data class DailyHealth(
    val date: String,  // AAAA-MM-DD
    val sleepMinutes: Double? = null,
    val hrv: Double? = null,  // ms (RMSSD en Health Connect; SDNN en Apple Salud)
    val restingHr: Double? = null,
    val weightKg: Double? = null,
    val steps: Double? = null,
    val vo2max: Double? = null,
)

/** Un entrenamiento registrado en el móvil. `zoneSeconds`: tiempo en las 7 zonas de FC, si se pudo calcular. */
@Serializable
data class Workout(
    val id: String,
    val sport: String,  // walking / running / cycling / swimming_pool / strength / other
    val start: String,  // AAAA-MM-DDTHH:MM:SS, hora local
    val minutes: Double,
    val avgHr: Double? = null,
    val maxHr: Double? = null,
    val zoneSeconds: List<Double>? = null,
    val distanceMeters: Double? = null,  // para los tiempos previstos de Progreso
    val device: String? = null,  // modelo del reloj (Garmin): su atribución lo pide
)

@Serializable
data class HealthSnapshot(
    val source: String,  // "Health Connect" o "Apple Salud"
    val days: List<DailyHealth>,
    val workouts: List<Workout>,
    val syncedAt: String,
)

private val ACTIVITY_TYPE = mapOf("running" to "Run", "cycling" to "Ride", "swimming_pool" to "Swim",
    "walking" to "Walk", "strength" to "WeightTraining")

/** FC máxima estimada por la edad (Tanaka 2001: 208 − 0,7 × edad) cuando no hay una medida. [Verificado: Tanaka 2001] */
fun estimatedMaxHr(age: Int): Double = 208 - 0.7 * age

/** FC máx.: la más alta medida en tus entrenamientos (sin picos imposibles, > 220) o la de tu edad si es mayor. */
private fun maxHrOf(workouts: List<Workout>, age: Int): Double =
    max(workouts.mapNotNull { it.maxHr }.filter { it <= 220 }.maxOrNull() ?: 0.0, estimatedMaxHr(age))

/**
 * Umbral (LTHR) de un deporte con tus entrenamientos: la FC media de tu sesión más dura de 20–70 min. En el test de Friel
 * la FC media de 20 min a tope ≈ LTHR [Práctica]; un entrenamiento normal queda por debajo, así que solo vale si fue duro
 * de verdad (≥ 85 % de tu FC máx.) y nunca pasa del 95 % [Inferencia en los dos límites]. null: sin una sesión así, el
 * plan usa el esfuerzo percibido y propone un test.
 */
fun estimatedLthr(workouts: List<Workout>, sport: String, maxHr: Double): Double? =
    workouts.filter { it.sport == sport && it.minutes in 20.0..70.0 }.mapNotNull { it.avgHr }.maxOrNull()
        ?.takeIf { it >= 0.85 * maxHr }?.let { min(it, 0.95 * maxHr) }

private val SETTING_TYPE = mapOf("running" to "Run", "cycling" to "Ride")

/** Carga por hora sin pulso, a esfuerzo moderado de cada deporte (un paseo no carga como un rodaje). [Inferencia] */
private val LOAD_PER_HOUR = mapOf("walking" to 25.0, "running" to 60.0, "cycling" to 45.0, "swimming_pool" to 50.0,
    "strength" to 35.0)

/**
 * Los datos del móvil en el formato de intervals.icu que usa el motor. La carga de cada entrenamiento se estima con la
 * FC media (hrTSS, con tu umbral estimado o el 90 % de la FC máx.) o, sin pulso, por hora según el deporte
 * (`LOAD_PER_HOUR`); con ella salen la forma física (CTL) y la fatiga (ATL) de cada día. [Inferencia: aproximaciones]
 */
fun healthCache(snapshot: HealthSnapshot, profile: UserProfile?, today: LocalDate): IntervalsCache {
    val age = profile?.birthYear?.let { today.year - it } ?: 40
    val maxHr = maxHrOf(snapshot.workouts, age)
    val lthrs = SETTING_TYPE.keys.associateWith { estimatedLthr(snapshot.workouts, it, maxHr) }
    val rest = snapshot.days.mapNotNull { it.restingHr }.takeIf { it.isNotEmpty() }?.average() ?: 60.0

    fun load(w: Workout): Double = w.avgHr?.takeIf { it > rest }
        ?.let { hrTss(w.minutes * 60, it, rest, lthrs[w.sport] ?: (0.9 * maxHr)) }
        ?: (w.minutes / 60 * (LOAD_PER_HOUR[w.sport] ?: 40.0))

    val loadByDay = snapshot.workouts.groupBy { it.start.take(10) }.mapValues { (_, ws) -> ws.sumOf { load(it) } }
    val byDay = snapshot.days.associateBy { it.date }
    val first = (snapshot.days.map { it.date } + loadByDay.keys).minOrNull()?.let { LocalDate.parse(it) } ?: today
    var ctl = 0.0
    var atl = 0.0
    val wellness = mutableListOf<JsonObject>()
    var d = first
    while (d <= today) {
        val key = d.toString()
        val tss = loadByDay[key] ?: 0.0
        ctl = nextLoad(ctl, tss, K_CTL)
        atl = nextLoad(atl, tss, K_ATL)
        val h = byDay[key]
        wellness += buildJsonObject {
            put("id", key)
            put("ctl", ctl)
            put("atl", atl)
            h?.sleepMinutes?.let { put("sleepSecs", it * 60) }
            h?.hrv?.let { put("hrv", it) }
            h?.restingHr?.let { put("restingHR", it) }
            h?.weightKg?.let { put("weight", it) }
            h?.steps?.let { put("steps", it) }
            h?.vo2max?.let { put("vo2max", it) }
        }
        d = d.plusDays(1)
    }
    val activities = snapshot.workouts.mapNotNull { w ->
        val type = ACTIVITY_TYPE[w.sport] ?: return@mapNotNull null
        buildJsonObject {
            put("id", w.id)
            put("type", type)
            put("start_date_local", w.start)
            put("moving_time", w.minutes * 60)
            put("icu_training_load", load(w))
            w.avgHr?.let { put("average_heartrate", it) }
            w.distanceMeters?.takeIf { it > 0 }?.let { put("distance", it) }
            if (w.id.startsWith(GARMIN_ID)) put("brivel_source", garminSource(w.device))
            w.zoneSeconds?.let { z -> put("icu_hr_zone_times", JsonArray(z.map { JsonPrimitive(it) })) }
        }
    }
    // Como los ajustes de deporte de intervals.icu: así fillPhysiology da zonas y objetivos de FC con tus datos
    val athlete = buildJsonObject {
        put("icu_resting_hr", rest)
        put("sportSettings", JsonArray(lthrs.mapNotNull { (sport, lthr) ->
            lthr?.let { buildJsonObject { put("types", JsonArray(listOf(JsonPrimitive(SETTING_TYPE[sport]!!)))); put("lthr", it); put("max_hr", maxHr) } }
        }))
    }
    return IntervalsCache(athlete, wellness, activities, snapshot.syncedAt)
}

/** Los límites de las 7 zonas de FC (ppm) con los que cada plataforma reparte los pulsos de un entrenamiento. */
fun heartRateZoneBounds(profile: UserProfile?, today: LocalDate, measuredMaxHr: Double?, workouts: List<Workout> = emptyList()): List<Double> {
    val age = profile?.birthYear?.let { today.year - it } ?: 40
    val maxHr = max(measuredMaxHr ?: 0.0, maxHrOf(workouts, age))
    // Tu umbral estimado si hay una sesión dura de carrera (o de bici); si no, el 90 % de la FC máx. [Inferencia]
    val lthr = estimatedLthr(workouts, "running", maxHr) ?: estimatedLthr(workouts, "cycling", maxHr) ?: (0.9 * maxHr)
    // Límites superiores de Z1, Z2, Z3, Z4, Z5a y Z5b en % de la LTHR (como intervals.icu con zonas de Friel)
    return listOf(0.81, 0.89, 0.93, 0.99, 1.02, 1.06).map { it * lthr }
}

/** Los datos del reloj o del móvil, sea cual sea la fuente: intervals.icu primero (es más completo), si no el móvil y Garmin. */
fun AppData.deviceData(today: LocalDate): IntervalsCache? = cache ?: combinedHealth(health, garmin)?.let { healthCache(it, profile, today) }

// ---------------------------------------------------------------- atribución de Garmin (Garmin API Brand Guidelines)

internal const val GARMIN_ID = "garmin-"  // id de las actividades que llegan por el relevo (brivel-garmin/src/convert.ts)

/** «Garmin [modelo]» bajo cada dato de Garmin; sin modelo, «Garmin». El modelo a veces ya empieza por «Garmin». */
fun garminSource(device: String?): String = when {
    device.isNullOrBlank() -> "Garmin"
    device.startsWith("Garmin", ignoreCase = true) -> device
    else -> "Garmin $device"
}

/** Para lo calculado con sus datos (el plan, la forma, los tiempos previstos): las normas piden decir que contribuyen. */
const val GARMIN_DERIVED = "Calculado en parte con datos de dispositivos Garmin."

/** Si lo que usa el motor viene en parte de Garmin (sin intervals.icu, que manda si está vinculado). */
internal fun AppData.usesGarmin(): Boolean = cache == null && garmin != null && (garmin.days.isNotEmpty() || garmin.workouts.isNotEmpty())

/** Un envío de Garmin ya descifrado en el dispositivo: el relevo lo prepara con el formato de Brivel. */
@Serializable
data class GarminDelivery(val v: Int, val days: List<DailyHealth> = emptyList(), val workouts: List<Workout> = emptyList())

private const val GARMIN_DAYS = 730  // lo que se guarda de Garmin: dos años

/**
 * Un envío con lo que ya había: Garmin manda cada dato por separado y lo corrige al volver a sincronizar el reloj, así
 * que en cada día el dato nuevo sustituye al anterior sin borrar los demás; las actividades, por id.
 */
fun mergeGarmin(old: HealthSnapshot?, delivery: GarminDelivery, today: LocalDate): HealthSnapshot {
    val since = today.plusDays(-GARMIN_DAYS).toString()
    val days = (old?.days ?: emptyList()).associateBy { it.date }.toMutableMap()
    for (n in delivery.days) {
        val o = days[n.date] ?: DailyHealth(n.date)
        days[n.date] = DailyHealth(n.date, n.sleepMinutes ?: o.sleepMinutes, n.hrv ?: o.hrv, n.restingHr ?: o.restingHr,
            n.weightKg ?: o.weightKg, n.steps ?: o.steps, n.vo2max ?: o.vo2max)
    }
    val workouts = ((old?.workouts ?: emptyList()) + delivery.workouts).associateBy { it.id }.values
    return HealthSnapshot("Garmin", days.values.filter { it.date >= since }.sortedBy { it.date },
        workouts.filter { it.start >= since }.sortedBy { it.start }, today.toString())
}

/**
 * Móvil y Garmin juntos. Cada día manda lo de Garmin (lo midió el reloj) y el móvil rellena lo que falte. Una actividad
 * del móvil que empieza a menos de 5 minutos de una de Garmin es la misma, que Garmin Connect copia en Salud.
 * [Inferencia: los 5 minutos]
 */
fun combinedHealth(phone: HealthSnapshot?, garmin: HealthSnapshot?): HealthSnapshot? {
    if (phone == null || garmin == null) return phone ?: garmin
    val fromGarmin = garmin.days.associateBy { it.date }
    val days = (phone.days.map { it.date } + garmin.days.map { it.date }).distinct().sorted().map { date ->
        val g = fromGarmin[date]
        val p = phone.days.firstOrNull { it.date == date }
        DailyHealth(date, g?.sleepMinutes ?: p?.sleepMinutes, g?.hrv ?: p?.hrv, g?.restingHr ?: p?.restingHr,
            g?.weightKg ?: p?.weightKg, g?.steps ?: p?.steps, g?.vo2max ?: p?.vo2max)
    }
    val starts = garmin.workouts.map { minuteOf(it.start) }
    val phoneOnly = phone.workouts.filter { w -> starts.none { kotlin.math.abs(it - minuteOf(w.start)) < 5 } }
    return HealthSnapshot("${phone.source} y Garmin", days, (garmin.workouts + phoneOnly).sortedBy { it.start },
        maxOf(phone.syncedAt, garmin.syncedAt))
}

private fun minuteOf(start: String): Long =
    kotlinx.datetime.LocalDateTime.parse(start.take(19)).let { it.date.toEpochDays() * 1440L + it.hour * 60 + it.minute }
