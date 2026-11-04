package app.brivel.app

import app.brivel.engine.Block
import app.brivel.engine.Profile
import app.brivel.engine.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchTest {
    private fun session(sport: String) = Session("R-1", sport, "vo2max", "Series", "VO2máx", 50, 60, listOf(
        Block("calentamiento", 1, 15.0, "Z1"),
        Block("principal", 5, 3.0, "Z5", restMinutes = 2.0, restZone = "Z1"),
        Block("vuelta a la calma", 1, 10.0, "Z1"),
    ), emptyMap(), ifBad = "", ifGood = "")

    @Test fun runningByHeartRate() {
        val w = watchWorkout(session("running"), "2026-11-04", Profile(listOf("running"), lthr = 170.0, maxHr = 190.0))
        assertEquals("running", w.sport)
        assertEquals(listOf("warmup", "interval", "cooldown"), w.blocks.map { it.steps.first().kind })
        val series = w.blocks[1]
        assertEquals(5, series.times)
        assertEquals(180, series.steps[0].seconds)
        val t = series.steps[0].target!!
        assertEquals("hr", t.metric)
        assertTrue(t.low < t.high && t.high <= 190.0)
        assertEquals("recovery", series.steps[1].kind)
        assertEquals(120, series.steps[1].seconds)
    }

    @Test fun cyclingByPowerInWatts() {
        val t = watchWorkout(session("cycling"), "2026-11-04", Profile(listOf("cycling"), ftp = 250.0)).blocks[1].steps[0].target!!
        assertEquals("power", t.metric)
        assertEquals(250.0 * 106 / 100, t.low)
        assertEquals(250.0 * 120 / 100, t.high)
    }

    @Test fun swimmingWithoutTargetAndStillRest() {
        val w = watchWorkout(session("swimming_pool"), "2026-11-04", Profile(listOf("swimming_pool"), css = 100.0))
        assertEquals("swimming", w.sport)
        assertNull(w.blocks[1].steps[0].target)
        assertEquals("rest", w.blocks[1].steps[1].kind)
    }

    @Test fun strengthSetsWithRest() {
        val s = StrengthScreen("2026-11-04", "Fuerza", 40, listOf(
            StrengthExercise("Sentadilla con barra", "sentadilla", 3, 8, 12, false, 120, 2, null, 60.0, emptyList(), emptyList()),
            StrengthExercise("Plancha frontal", "core", 3, 20, 45, true, 60, null, null, null, emptyList(), emptyList()),
        ), emptyList(), null)
        val w = watchStrength(s, "Brivel · Fuerza")
        assertEquals(3, w.blocks[0].times)
        assertEquals(WatchStep("active", reps = 12, exercise = "Sentadilla con barra", weightKg = 60.0,
            note = "8–12 repeticiones · deja 2 en reserva"), w.blocks[0].steps[0])
        assertEquals(WatchStep("rest", seconds = 120), w.blocks[0].steps[1])
        assertEquals(45, w.blocks[1].steps[0].seconds)
    }
}
