package app.brivel.web

import app.brivel.app.GoalKind
import app.brivel.app.KeyValueStore
import app.brivel.app.BrivelApp
import app.brivel.app.UserProfile
import app.brivel.app.DailyHealth
import app.brivel.app.HealthSnapshot
import app.brivel.app.Workout
import app.brivel.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

private val fs: dynamic = js("process.getBuiltinModule('fs')")

/** web/src/types.ts está escrito a mano: cada campo que declara tiene que salir en el JSON de Hoy, la sesión de fuerza, una figura, el mes o el progreso. */
class WebTypesTest {
    private class MemoryStore : KeyValueStore {
        private val map = mutableMapOf<String, String>()
        override fun read(key: String) = map[key]
        override fun write(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    private fun keys(e: JsonElement): Set<String> = when (e) {
        is JsonObject -> e.keys + e.values.flatMap { keys(it) }
        is JsonArray -> e.flatMap { keys(it) }.toSet()
        else -> emptySet()
    }

    @Test
    fun typesMatchTodayJson() {
        // Viernes de descanso (con su motivo) y sábado con bici por pasos y fuerza: entre los dos salen todos los tipos
        var today = LocalDate(2026, 10, 30)
        val app = BrivelApp(MemoryStore(), MemoryStore(), { today })
        app.saveProfile(UserProfile(birthYear = 1990, level = "intermediate", strength = "gym",
            cardio = listOf("running", "cycling"), days = mapOf("MON" to 60, "TUE" to 60, "THU" to 75, "SAT" to 90, "SUN" to 60)))
        app.setGoal(GoalKind.FITNESS)
        val json = Json { encodeDefaults = true }
        val sent = listOf(30, 31).flatMap { d ->
            today = LocalDate(2026, 10, d)
            val screen = app.todayScreen()
            // La sesión de fuerza con una serie de la semana anterior y otra de hoy, y una figura
            val strength = app.strengthScreen(today.toString())?.let { s ->
                val e = s.exercises.first()
                app.logSet(today.plusDays(-7).toString(), e.name, 1, 40.0, e.repsHigh)
                app.logSet(today.toString(), e.name, 1, 42.0, e.repsLow)
                json.encodeToString(app.strengthScreen(today.toString()))
            }
            (listOfNotNull(json.encodeToString(screen), strength, figureLinesJson("sentadilla"))).flatMap { keys(Json.parseToJsonElement(it)) }
        }.toSet()
        // Calendario y Progreso, con datos de Salud (días, entrenamientos con pulso, zonas y distancia)
        app.importHealth(HealthSnapshot("Apple Salud",
            (1..40).map { n -> DailyHealth(today.plusDays(-n).toString(), 420.0 + n % 7, 50.0 + n % 9, 50.0 + n % 4, 72.0, 8000.0 + 100 * n) },
            (1..20).map { n -> Workout("w$n", if (n % 2 == 0) "running" else "cycling", "${today.plusDays(-2 * n)}T08:00:00", 50.0,
                145.0, 170.0, listOf(300.0, 1200.0, 900.0, 300.0, 200.0, 100.0, 0.0), 9000.0) }, today.toString()))
        val screens = listOf(json.encodeToString(app.month(2026, 10)), json.encodeToString(app.progress()))
        val all = sent + screens.flatMap { keys(Json.parseToJsonElement(it)) }
        val types = fs.readFileSync(js("process.env.WEB_TYPES"), "utf8") as String
        val declared = Regex("""(\w+)\??:\s""").findAll(types.substringAfter("export type Tone")).map { it.groupValues[1] }.toSet()
        assertEquals(emptySet(), declared - all, "campos de web/src/types.ts que el núcleo ya no manda")
    }
}
