package app.brivel.app

// Material (docs/VISION.md §9, bloque F): los kilómetros de cada zapatilla y cada bici. Cada actividad con distancia se
// apunta una vez en `gearUses` al material en uso de su deporte. El apunte es permanente: las actividades del reloj se
// guardan uno o dos años, y sin él los kilómetros bajarían al caducar.

import app.brivel.engine.fmt
import app.brivel.engine.pyRound
import app.brivel.intervals.HEALTH_ACTIVITY_SPORTS
import app.brivel.intervals.date
import app.brivel.intervals.num
import app.brivel.intervals.str
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** `since`: desde qué día cuentan las actividades; `startKm`: los que ya llevaba; `limitKm`: avisar al pasarlos (opcional). */
@Serializable
data class Gear(val id: String, val name: String, val sport: String, val since: String, val startKm: Double = 0.0,
    val limitKm: Double? = null, val retired: Boolean = false)

/** Una actividad apuntada a un material. */
@Serializable
data class GearUse(val activity: String, val gear: String, val start: String, val km: Double) {  // start: AAAA-MM-DDTHH:MM…
    val date: String get() = start.take(10)
}

val GEAR_LABEL = mapOf("running" to "Zapatillas de correr", "cycling" to "Bici", "walking" to "Calzado de caminar")
val GEAR_SPORTS: List<String> = GEAR_LABEL.keys.toList()

@Serializable
data class GearUseRow(val activity: String, val text: String)

@Serializable
data class GearRow(
    val id: String, val name: String, val sport: String, val label: String,
    val km: String,  // "412 km"
    val detail: String,  // "37 actividades desde el 3 de marzo"
    val warning: String?,  // pasó de su límite
    val inUse: Boolean,  // el que se apunta las actividades nuevas de su deporte
    val retired: Boolean,
    val uses: List<GearUseRow>,  // las últimas, para pasarlas a otro
    val since: String, val startKm: Double, val limitKm: Double?,
)

internal fun validGear(g: Gear): String? = when {
    g.name.isBlank() -> "Ponle un nombre"
    g.sport !in GEAR_SPORTS -> "Elige si es de correr, de bici o de caminar"
    runCatching { LocalDate.parse(g.since) }.isFailure -> "La fecha no es válida"
    g.startKm < 0 || g.startKm > 500000 -> "Los kilómetros que ya llevaba no son válidos"
    g.limitKm != null && g.limitKm <= 0 -> "El aviso tiene que ser a partir de algún kilómetro"
    else -> null
}

/** El material en uso de cada deporte: el primero de la lista que no está retirado. */
private fun inUse(gear: List<Gear>): Map<String, Gear> =
    GEAR_SPORTS.mapNotNull { s -> gear.firstOrNull { it.sport == s && !it.retired }?.let { s to it } }.toMap()

/**
 * Apunta al material en uso las actividades con distancia que aún no están apuntadas y son de su fecha en adelante. La
 * misma actividad puede llegar por dos fuentes con ids distintos: una que empieza a menos de 5 minutos de otra ya
 * apuntada es la misma (como en combinedHealth).
 */
internal fun countGear(data: AppData, today: LocalDate): AppData {
    val current = inUse(data.gear)
    if (current.isEmpty()) return data
    val sportOf = data.gear.associate { it.id to it.sport }
    val uses = data.gearUses.toMutableList()
    val counted = uses.map { it.activity }.toMutableSet()
    for (a in data.deviceData(today)?.activities ?: emptyList()) {
        val id = a["id"].str() ?: continue
        val sport = HEALTH_ACTIVITY_SPORTS[a["type"].str()]
        val gear = current[sport] ?: continue
        val km = (a["distance"].num() ?: 0.0) / 1000
        if (id in counted || km <= 0 || a.date() < gear.since || a.date() > today.toString()) continue
        val start = a["start_date_local"].str()!!.take(19)
        if (start.length < 16 || uses.any { sportOf[it.gear] == sport && sameStart(it.start, start) }) continue
        uses += GearUse(id, gear.id, start, km)
        counted += id
    }
    return if (uses.size == data.gearUses.size) data else data.copy(gearUses = uses)
}

private fun sameStart(a: String, b: String) = a.take(10) == b.take(10) && abs(minuteOf(a) - minuteOf(b)) < 5

/** Tras combinar dos dispositivos: una actividad apuntada dos veces (por id o por empezar a la vez) se queda una. */
internal fun dedupeUses(uses: List<GearUse>): List<GearUse> {
    val kept = mutableListOf<GearUse>()
    for (u in uses.distinctBy { it.activity }) if (kept.none { sameStart(it.start, u.start) }) kept += u
    return kept
}

internal fun shortDate(date: String): String = LocalDate.parse(date).let { "${it.day} ${MONTH_LABEL[it.month.number - 1].take(3)}" }

internal fun kmText(km: Double) = "${fmt(km, 1).replace('.', ',')} km"

/** El material y, si los kilómetros salen en parte de actividades de Garmin, la nota que sus normas piden. */
@Serializable
data class GearScreen(val rows: List<GearRow>, val garminNote: String? = null)

internal fun gearScreen(data: AppData) = GearScreen(gearRows(data), GARMIN_DERIVED.takeIf { data.usesGarmin() && data.gearUses.any { it.activity.startsWith(GARMIN_ID) } })

internal fun gearRows(data: AppData): List<GearRow> {
    val current = inUse(data.gear)
    return data.gear.map { g ->
        val uses = data.gearUses.filter { it.gear == g.id }.sortedByDescending { it.date }
        val km = g.startKm + uses.sumOf { it.km }
        GearRow(g.id, g.name, g.sport, GEAR_LABEL[g.sport] ?: g.sport, "${pyRound(km)} km",
            "${uses.size} ${if (uses.size == 1) "actividad" else "actividades"} desde el ${dateLabel(g.since).substringAfter(' ')}",
            g.limitKm?.takeIf { km >= it }?.let { "Ha pasado de los ${pyRound(it)} km que pusiste de aviso." },
            current[g.sport]?.id == g.id, g.retired, uses.take(5).map { GearUseRow(it.activity, "${shortDate(it.date)} · ${kmText(it.km)}") },
            g.since, g.startKm, g.limitKm)
    }
}

/** El nombre del material con el que se hizo una actividad. */
internal fun gearOf(data: AppData, activity: String): String? =
    data.gearUses.firstOrNull { it.activity == activity }?.let { u -> data.gear.firstOrNull { it.id == u.gear }?.name }
