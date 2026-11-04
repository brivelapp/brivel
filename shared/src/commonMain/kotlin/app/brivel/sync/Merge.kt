package app.brivel.sync

// Combinar a tres bandas (docs/SINCRONIZACION.md, Conflictos). base: la última copia sincronizada; local: este
// dispositivo; remote: la del servidor. Lo que cambió este dispositivo respecto a la base se aplica sobre la del servidor,
// altas y bajas incluidas. Si los dos cambiaron lo mismo, gana el servidor y se avisa. Sin relojes: pueden ir mal.

import app.brivel.app.AppData
import app.brivel.app.SCHEMA

/**
 * Lo que se sube: todo menos la copia de intervals.icu. Son hasta 365 días de actividades y bienestar (podría pasar del
 * tope de 1 MB) y viven en intervals.icu: cada dispositivo con la clave los vuelve a descargar. Así el servidor guarda
 * menos datos de salud (RGPD art. 5.1.c). La base guardada tampoco la lleva, y `merge` conserva la de este dispositivo.
 */
fun syncPayload(data: AppData): AppData = data.copy(cache = null)

/** Los datos combinados y los apartados en los que el cambio de este dispositivo perdió frente al del servidor. */
class Merged(val data: AppData, val conflicts: Set<String>)

fun merge(base: AppData, local: AppData, remote: AppData): Merged {
    // Una versión más nueva puede tener campos que esta no conoce: combinar y subir los borraría
    require(remote.schema <= SCHEMA) { "Otro dispositivo tiene una versión más nueva de Brivel: actualiza esta para sincronizar" }
    val conflicts = mutableSetOf<String>()

    // name = null: datos que se vuelven a descargar (intervals.icu, Salud); no merece la pena avisar
    fun <T> pick(name: String?, b: T, l: T, r: T): T = when {
        l == b || l == r -> r
        r == b -> l
        else -> r.also { if (name != null) conflicts += name }
    }

    fun <K, V> byKey(name: String, b: Map<K, V>, l: Map<K, V>, r: Map<K, V>): Map<K, V> =
        (r.keys + l.keys + b.keys).mapNotNull { k -> pick(name, b[k], l[k], r[k])?.let { k to it } }.toMap()

    fun <T> items(b: Collection<T>, l: Collection<T>, r: Collection<T>): List<T> {
        val (bs, ls) = b.toSet() to l.toSet()
        return (r + l.filter { it !in bs }).distinct().filterNot { it in bs && it !in ls }
    }

    // El objetivo y el inicio de su bloque cambian juntos (setGoal)
    val (goal, blockStart) = pick("goal", base.goal to base.blockStart, local.goal to local.blockStart,
        remote.goal to remote.blockStart)
    // Desde remote: un campo que se olvide aquí se queda como en el servidor, no vacío
    val data = remote.copy(
        schema = SCHEMA,
        profile = pick("profile", base.profile, local.profile, remote.profile),
        goal = goal,
        blockStart = blockStart,
        manualDone = items(base.manualDone, local.manualDone, remote.manualDone),
        weekChanges = byKey("weekChanges", base.weekChanges, local.weekChanges, remote.weekChanges),
        readiness = byKey("readiness", base.readiness, local.readiness, remote.readiness),
        choices = byKey("choices", base.choices, local.choices, remote.choices),
        renamed = items(base.renamed, local.renamed, remote.renamed).toSet(),
        cache = pick(null, base.cache, local.cache, remote.cache),
        intervalsAthlete = pick("intervalsAthlete", base.intervalsAthlete, local.intervalsAthlete, remote.intervalsAthlete),
        health = pick(null, base.health, local.health, remote.health),
        garmin = pick(null, base.garmin, local.garmin, remote.garmin),
        garminLink = pick(null, base.garminLink, local.garminLink, remote.garminLink),
        lifts = items(base.lifts, local.lifts, remote.lifts),
        sets = items(base.sets, local.sets, remote.sets),
        reminderHour = pick("reminderHour", base.reminderHour, local.reminderHour, remote.reminderHour),
        swaps = items(base.swaps, local.swaps, remote.swaps),
        moves = items(base.moves, local.moves, remote.moves),
    )
    return Merged(data, conflicts)
}
