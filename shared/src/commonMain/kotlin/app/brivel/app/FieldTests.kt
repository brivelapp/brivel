package app.brivel.app

import app.brivel.engine.Profile
import app.brivel.intervals.str
import kotlinx.serialization.Serializable

/**
 * El resultado de un test de campo (formato 12). Con él, Brivel da ritmos, pulsos y vatios en vez de «ritmo cómodo»:
 * - `talk` (carrera, principiantes): test del habla, tramos de 3′ cada vez más rápidos; ritmo y pulso del último tramo
 *   en que se hablaba cómodo ≈ primer umbral ventilatorio (Persinger 2004). Da el techo de lo suave. [Verificado: resumen]
 * - `threshold` (carrera): 30′ al máximo sostenible; ritmo medio = ritmo umbral y pulso medio de los últimos 20′ = umbral
 *   de pulso (Friel; McGehee 2005, sin diferencia con el laboratorio). [Verificado: resumen]
 * - `ftp` (bici con potenciómetro): 20′ al máximo; FTP = 95 % de la potencia media (Allen y Coggan). [Práctica]
 * - `lthr` (bici sin potenciómetro): 30′ al máximo; pulso medio de los últimos 20′ = umbral de pulso (Friel). [Práctica]
 */
@Serializable
data class FieldTest(
    val date: String,
    val sport: String,  // running / cycling
    val kind: String,  // talk / threshold / ftp / lthr
    val paceSecKm: Double? = null,  // talk y threshold
    val hr: Double? = null,  // talk (pulso del último tramo cómodo), threshold y lthr (media de los últimos 20′)
    val watts: Double? = null,  // ftp: potencia media de los 20′ (no el FTP)
)

val FIELD_TEST_KINDS = mapOf("running" to setOf("talk", "threshold"), "cycling" to setOf("ftp", "lthr"))

/** Lo que se apunta tiene que poder ser de una persona: si no, se rechaza antes de cambiar los ritmos de todo el plan. */
fun FieldTest.check() {
    require(kind in (FIELD_TEST_KINDS[sport] ?: emptySet())) { "Ese test no es de ese deporte" }
    // 2:30 a 20:00 min/km: de un corredor de élite a caminar despacio
    paceSecKm?.let { require(it in 150.0..1200.0) { "Ese ritmo no parece real: revisa la distancia" } }
    hr?.let { require(it in 80.0..220.0) { "Ese pulso no parece real" } }
    watts?.let { require(it in 50.0..600.0) { "Esa potencia no parece real" } }
    when (kind) {
        "talk", "threshold" -> require(paceSecKm != null) { "Falta el ritmo" }
        "ftp" -> require(watts != null) { "Falta la potencia media" }
        "lthr" -> require(hr != null) { "Falta el pulso medio" }
    }
}

/** El último resultado de cada test por deporte. */
fun AppData.latestTest(sport: String, kind: String): FieldTest? =
    fieldTests.filter { it.sport == sport && it.kind == kind }.maxByOrNull { it.date }

/**
 * Los tests de Brivel mandan sobre lo que traiga intervals.icu: son de la persona y más recientes [Inferencia]. El del
 * habla solo da el techo de lo suave; con el de 30′, el ritmo umbral manda sobre él.
 */
internal fun Profile?.withTests(data: AppData): Profile? {
    val run = data.latestTest("running", "threshold")
    val talk = data.latestTest("running", "talk")
    val ftp = data.latestTest("cycling", "ftp")
    val bike = data.latestTest("cycling", "lthr")
    if (run == null && talk == null && ftp == null && bike == null) return this
    val p = this ?: Profile(activeSports = listOf("running", "cycling"))
    val lthr = p.lthrBySport.toMutableMap()
    run?.hr?.let { lthr["running"] = it }
    bike?.hr?.let { lthr["cycling"] = it }
    return p.copy(lthrBySport = lthr, runPace = run?.paceSecKm ?: p.runPace, ftp = ftp?.watts?.let { 0.95 * it } ?: p.ftp,
        easyPace = talk?.paceSecKm, easyHr = talk?.hr,
        needsTest = p.needsTest - setOfNotNull("running".takeIf { run != null || talk != null }, "cycling".takeIf { ftp != null || bike != null }))
}

/**
 * Sus salidas en bici traen potencia medida: actividades propias (FIT) con potencia media o de intervals.icu con
 * potenciómetro (`device_watts`). shortcut: no mira Garmin ni Health Connect; añadirlos si alguien con potenciómetro
 * no recibe el test de 20′.
 */
fun AppData.hasPowerMeter(): Boolean =
    activities.any { it.sport == "cycling" && (it.avgPower ?: 0.0) > 0 } ||
        cache?.activities?.any { a -> a["type"].str()?.contains("Ride") == true && (a["device_watts"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true" } == true

/**
 * El resultado de un test tal como se escribe: `main` es el ritmo («7:14»), la distancia en km, los vatios o el pulso,
 * según el test (TEST_FIELDS); `hr`, el pulso opcional. Comas o puntos valen.
 */
fun parseTestResult(date: String, kind: String, main: String, hr: String?): FieldTest {
    val sport = if (kind == "talk" || kind == "threshold") "running" else "cycling"
    fun number(s: String) = s.trim().replace(',', '.').toDoubleOrNull()
    val value = main.trim()
    val pulse = hr?.takeIf { it.isNotBlank() }?.let { number(it) ?: throw IllegalArgumentException("El pulso es un número") }
    return when (kind) {
        "talk" -> {
            val parts = value.split(':')
            val sec = if (parts.size == 2) parts[0].trim().toIntOrNull()?.let { m -> parts[1].trim().toIntOrNull()?.let { s -> m * 60 + s } } else null
            FieldTest(date, sport, kind, paceSecKm = sec?.toDouble() ?: throw IllegalArgumentException("Escribe el ritmo como 7:14"), hr = pulse)
        }
        "threshold" -> {
            val km = number(value)?.takeIf { it > 0 } ?: throw IllegalArgumentException("Escribe los km, por ejemplo 6,2")
            FieldTest(date, sport, kind, paceSecKm = 1800 / km, hr = pulse)
        }
        "ftp" -> FieldTest(date, sport, kind, watts = number(value) ?: throw IllegalArgumentException("Escribe los vatios"))
        "lthr" -> FieldTest(date, sport, kind, hr = number(value) ?: throw IllegalArgumentException("Escribe el pulso"))
        else -> throw IllegalArgumentException("Ese test no existe")
    }
}
