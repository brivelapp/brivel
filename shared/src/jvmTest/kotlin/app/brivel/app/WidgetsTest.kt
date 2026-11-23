package app.brivel.app

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Hoy a gusto de cada uno: widgets que se añaden, se quitan, se ordenan y son medios o anchos, guardados en el dispositivo. */
class WidgetsTest {
    private val today = LocalDate(2026, 11, 23)
    private val profile = UserProfile(birthYear = 1990, level = "intermediate", strength = "gym",
        cardio = listOf("running"), days = mapOf("MON" to 45, "WED" to 45, "SAT" to 60))

    private fun app(store: KeyValueStore = MemoryStore()) = BrivelApp(store, MemoryStore(), { today }).apply {
        if (needsOnboarding) { saveProfile(profile); setGoal(GoalKind.FITNESS) }
    }

    @Test fun `de serie las preguntas y la semana anchas`() {
        assertEquals(DEFAULT_WIDGETS, app().widgetSlots)
        assertEquals(listOf("readiness" to true, "week" to true), app().todayWidgets().map { it.key to it.wide })
    }

    @Test fun `se añaden y se ordenan y cambian de tamaño y se quitan en este dispositivo`() {
        val store = MemoryStore()
        val a = app(store)
        a.addWidget("sleep", wide = false)
        a.addWidget("hrv", wide = false)
        a.addWidget("sleep", wide = true)  // ya está: no se duplica ni cambia
        assertFailsWith<IllegalArgumentException> { a.addWidget("tiempo", wide = false) }
        a.moveWidget("hrv", 0)
        a.setWidgetWide("week", false)
        a.removeWidget("readiness")
        val expected = listOf(WidgetSlot("hrv", false), WidgetSlot("week", false), WidgetSlot("sleep", false))
        assertEquals(expected, a.widgetSlots)
        assertEquals(expected, app(store).widgetSlots)  // otra vez abierta, igual
        assertTrue(a.widgetChoices().single { it.key == "hrv" }.inUse)
        assertTrue(!a.widgetChoices().single { it.key == "readiness" }.inUse)
        a.moveWidget("sleep", 99)  // fuera de rango: al final
        assertEquals("sleep", a.widgetSlots.last().key)
        // El orden no va en los datos que se sincronizan
        assertTrue(!store.read("brivel.data")!!.contains("\"hrv\",\"wide\""))
        a.deleteAll()
        assertNull(store.read("brivel.widgets.today"))
    }

    @Test fun `lo guardado por una versión vieja no rompe Hoy`() {
        assertEquals(listOf(WidgetSlot("sleep", true)),
            cleanSlots(listOf(WidgetSlot("viejo", false), WidgetSlot("sleep", true), WidgetSlot("sleep", false))))
        val store = MemoryStore().apply { write("brivel.widgets.today", "esto no es JSON") }
        assertEquals(DEFAULT_WIDGETS, app(store).widgetSlots)
    }

    @Test fun `sin reloj dicen cómo tener el dato y con datos dan el valor frente a tu normal`() {
        val store = MemoryStore()
        val a = app(store)
        listOf("sleep", "hrv", "restingHR", "steps", "weight", "form", "nextRace", "badge").forEach { a.addWidget(it, false) }
        val empty = a.todayWidgets().associateBy { it.key }
        assertNull(empty.getValue("hrv").value)
        assertEquals("Conecta tu reloj, Health Connect o Apple Salud", empty.getValue("hrv").empty)
        assertEquals("Apúntalo en Progreso", empty.getValue("weight").empty)
        assertEquals("Sin carrera en tu objetivo", empty.getValue("nextRace").empty)
        assertTrue(empty.getValue("badge").value != null)  // la medalla más cerca siempre hay

        a.logMeasure("weight", 72.5)
        val wellness = (0 until 30).map { d ->
            buildJsonObject {
                put("id", today.minusDays(d).toString())
                put("sleepSecs", if (d == 0) 6 * 3600 + 40 * 60 else 7 * 3600 + (d % 3) * 600)
                put("hrv", 58.0 + (d % 5)); put("restingHR", 49 + d % 3); put("steps", 9640)
                put("ctl", 50.0); put("atl", 62.0)
            }
        }
        store.write("brivel.data", Json.encodeToString(AppData.serializer(),
            a.data.copy(cache = IntervalsCache(buildJsonObject {}, wellness, emptyList(), today.toString()))))
        val w = app(store).todayWidgets().associateBy { it.key }
        assertEquals("6 h 40", w.getValue("sleep").value)
        assertEquals("por debajo de tu normal", w.getValue("sleep").detail)
        assertEquals("ms", w.getValue("hrv").unit)
        assertEquals("72,5", w.getValue("weight").value)
        assertEquals("9.640", w.getValue("steps").value)
        assertEquals("-12", w.getValue("form").value)
        assertEquals("cargado: entrenando fuerte", w.getValue("form").detail)
    }

    @Test fun `la próxima carrera cuenta los días que faltan`() {
        val a = BrivelApp(MemoryStore(), MemoryStore(), { today })
        a.saveProfile(profile)
        a.setGoal(GoalKind.RACE, RaceInfo("10 km de prueba", "running", "2026-12-19", distanceKm = 10.0))
        a.addWidget("nextRace", wide = true)
        val race = a.todayWidgets().single { it.key == "nextRace" }
        assertEquals("26" to "días", race.value to race.unit)
        assertEquals("10 km de prueba", race.detail)
    }

    private fun LocalDate.minusDays(d: Int) = LocalDate.fromEpochDays(toEpochDays() - d)
}
