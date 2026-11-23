package app.brivel.app

import app.brivel.engine.Profile
import app.brivel.engine.plusDays
import app.brivel.intervals.num
import app.brivel.intervals.str
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * El resultado de un test de campo (formato 12). Con él, Brivel da ritmos, pulsos y vatios en vez de «ritmo cómodo».
 * Quien empieza no hace test (Gabriel, 2026-11-18: ni a tope ni el del habla): su ritmo sale de sus carreras fáciles
 * (learnedEasyPace).
 * - `threshold` (carrera): 30′ al máximo sostenible; ritmo medio = ritmo umbral y pulso medio de los últimos 20′ = umbral
 *   de pulso (Friel; McGehee 2005, sin diferencia con el laboratorio). [Verificado: resumen]
 * - `ftp` (bici con potenciómetro): 20′ al máximo; FTP = 95 % de la potencia media (Allen y Coggan). [Práctica]
 * - `lthr` (bici sin potenciómetro): 30′ al máximo; pulso medio de los últimos 20′ = umbral de pulso (Friel). [Práctica]
 */
@Serializable
data class FieldTest(
    val date: String,
    val sport: String,  // running / cycling
    val kind: String,  // threshold / ftp / lthr
    val paceSecKm: Double? = null,  // threshold
    val hr: Double? = null,  // threshold y lthr (media de los últimos 20′)
    val watts: Double? = null,  // ftp: potencia media de los 20′ (no el FTP)
)

val FIELD_TEST_KINDS = mapOf("running" to setOf("threshold"), "cycling" to setOf("ftp", "lthr"))

/** Lo que se apunta tiene que poder ser de una persona: si no, se rechaza antes de cambiar los ritmos de todo el plan. */
fun FieldTest.check() {
    require(kind in (FIELD_TEST_KINDS[sport] ?: emptySet())) { "Ese test no es de ese deporte" }
    // 2:30 a 20:00 min/km: de un corredor de élite a caminar despacio
    paceSecKm?.let { require(it in 150.0..1200.0) { "Ese ritmo no parece real: revisa la distancia" } }
    hr?.let { require(it in 80.0..220.0) { "Ese pulso no parece real" } }
    watts?.let { require(it in 50.0..600.0) { "Esa potencia no parece real" } }
    when (kind) {
        "threshold" -> require(paceSecKm != null) { "Falta el ritmo" }
        "ftp" -> require(watts != null) { "Falta la potencia media" }
        "lthr" -> require(hr != null) { "Falta el pulso medio" }
    }
}

/** El último resultado de cada test por deporte. */
fun AppData.latestTest(sport: String, kind: String): FieldTest? =
    fieldTests.filter { it.sport == sport && it.kind == kind }.maxByOrNull { it.date }

/**
 * Los tests de Brivel mandan sobre lo que traiga intervals.icu: son de la persona y más recientes [Inferencia]. El ritmo
 * aprendido de las carreras fáciles solo da lo suave; con el ritmo umbral, manda este.
 */
internal fun Profile?.withTests(data: AppData, easyPace: Pair<Double, Double>? = null): Profile? {
    val run = data.latestTest("running", "threshold")
    val ftp = data.latestTest("cycling", "ftp")
    val bike = data.latestTest("cycling", "lthr")
    if (run == null && easyPace == null && ftp == null && bike == null) return this
    val p = this ?: Profile(activeSports = listOf("running", "cycling"))
    val lthr = p.lthrBySport.toMutableMap()
    run?.hr?.let { lthr["running"] = it }
    bike?.hr?.let { lthr["cycling"] = it }
    return p.copy(lthrBySport = lthr, runPace = run?.paceSecKm ?: p.runPace, ftp = ftp?.watts?.let { 0.95 * it } ?: p.ftp,
        easyPace = easyPace?.first, easyPaceSlow = easyPace?.second,
        needsTest = p.needsTest - setOfNotNull("running".takeIf { run != null || easyPace != null }, "cycling".takeIf { ftp != null || bike != null }))
}

/**
 * Sus salidas en bici traen potencia medida: actividades propias (FIT) con potencia media o de intervals.icu con
 * potenciómetro (`device_watts`). shortcut: no mira Garmin ni Health Connect; añadirlos si alguien con potenciómetro
 * no recibe el test de 20′.
 */
fun AppData.hasPowerMeter(): Boolean =
    activities.any { it.sport == "cycling" && (it.avgPower ?: 0.0) > 0 } ||
        cache?.activities?.any { a -> a["type"].str()?.contains("Ride") == true && (a["device_watts"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true" } == true

/** Cómo le fue una carrera a quien la hizo (EFFORT_LABEL): con las fáciles, Brivel aprende su ritmo suave. */
val EFFORTS = listOf("easy", "normal", "hard")
internal const val EASY_RUNS = 3

/** Las carreras de 15′ y 1 km o más de las últimas 10 semanas, de la más reciente a la más antigua. */
internal fun recentRuns(activities: List<JsonObject>, today: LocalDate): List<JsonObject> {
    val from = today.plusDays(-70).toString()
    return activities.filter {
        it["type"].str() == "Run" && (it["moving_time"].num() ?: 0.0) >= 15 * 60 && (it["distance"].num() ?: 0.0) >= 1000 &&
            (it["start_date_local"].str() ?: "") >= from
    }.sortedByDescending { it["start_date_local"].str() }
}

internal fun JsonObject.runId(): String? = (this["id"] as? JsonPrimitive)?.content
internal fun JsonObject.runPace(): Double = this["moving_time"].num()!! / (this["distance"].num()!! / 1000)

/**
 * Quien empieza no hace test: tras cada carrera dice cómo le fue y, con 3 fáciles en las últimas 10 semanas, lo suave
 * va del ritmo más rápido al más lento de esas 3 (s/km); null hasta entonces. Se descartan ritmos imposibles (saltos del
 * GPS). «Fácil» es «ligero» en la escala de Borg, y el umbral láctico cae ahí (RPE 10,8 ± 1,8 de 20 en 2.560 personas):
 * lo que se siente fácil va por debajo [Verificado: Scherr et al. 2013, resumen. Inferencia: 3 carreras y 10 semanas]
 */
internal fun AppData.learnedEasyPace(activities: List<JsonObject>, today: LocalDate): Pair<Double, Double>? {
    val paces = recentRuns(activities, today).filter { efforts[it.runId()] == "easy" }.map { it.runPace() }
        .filter { it in 150.0..1200.0 }.take(EASY_RUNS)
    return if (paces.size < EASY_RUNS) null else paces.min() to paces.max()
}

/**
 * El resultado de un test tal como se escribe: `main` es la distancia en km, los vatios o el pulso, según el test
 * (TEST_FIELDS); `hr`, el pulso opcional. Comas o puntos valen.
 */
fun parseTestResult(date: String, kind: String, main: String, hr: String?): FieldTest {
    val sport = if (kind == "threshold") "running" else "cycling"
    fun number(s: String) = s.trim().replace(',', '.').toDoubleOrNull()
    val value = main.trim()
    val pulse = hr?.takeIf { it.isNotBlank() }?.let { number(it) ?: throw IllegalArgumentException("El pulso es un número") }
    return when (kind) {
        "threshold" -> {
            val km = number(value)?.takeIf { it > 0 } ?: throw IllegalArgumentException("Escribe los km, por ejemplo 6,2")
            FieldTest(date, sport, kind, paceSecKm = 1800 / km, hr = pulse)
        }
        "ftp" -> FieldTest(date, sport, kind, watts = number(value) ?: throw IllegalArgumentException("Escribe los vatios"))
        "lthr" -> FieldTest(date, sport, kind, hr = number(value) ?: throw IllegalArgumentException("Escribe el pulso"))
        else -> throw IllegalArgumentException("Ese test no existe")
    }
}
