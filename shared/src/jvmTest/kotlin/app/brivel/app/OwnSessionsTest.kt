package app.brivel.app

import app.brivel.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Bloque C de la visión: sesiones propias de resistencia en el plan, el calendario y el reloj. */
class OwnSessionsTest {
    private val today = LocalDate(2026, 10, 19)  // lunes
    private val profile = UserProfile(birthYear = 1990, level = "intermediate", strength = "gym",
        cardio = listOf("running"), days = mapOf("MON" to 60, "WED" to 60, "SAT" to 90))

    private fun app(now: () -> LocalDate = { today }) = BrivelApp(MemoryStore(), MemoryStore(), now).apply {
        saveProfile(profile)
        setGoal(GoalKind.FITNESS)
    }

    private val series = OwnSession("", "  Series de 3 minutos ", "running", listOf(
        OwnStep("Calentamiento", 15.0, "Z2"), OwnStep("Rápido", 3.0, "Z4", reps = 5, restMinutes = 2.0), OwnStep("", 10.0, "Z1")),
        days = listOf("WED"), since = "")

    private fun running(a: BrivelApp, date: LocalDate) = a.day(date.toString()).items.filter { it.sport == "running" }

    @Test fun ownSessionReplacesThatSportOnItsWeekday() {
        val a = app()
        val wednesday = today.plusDays(2)
        assertEquals(1, running(a, wednesday).size)  // el plan ya tenía carrera el miércoles
        val s = a.saveOwnSession(series)
        assertTrue(s.id.isNotBlank())
        assertEquals("Series de 3 minutos", s.name)
        val item = running(a, wednesday).single()  // sigue habiendo una sola: la del usuario
        assertEquals("Series de 3 minutos" to "Tu sesión", item.title to item.hint)
        assertEquals(15 + 5 * (3 + 2) + 10, item.durationMin)
        assertEquals(listOf("Calentamiento", "Rápido", "Paso 3"), item.steps.map { it.name })
        assertEquals(listOf(1, 5, 1), item.steps.map { it.reps })
        assertEquals(listOf("Z2", "Z4", "Z1"), item.steps.map { it.intensityZone })
        assertEquals(2.0, item.steps[1].restMinutes)
        // La fuerza de ese día y los demás días siguen como estaban
        assertTrue(a.day(today.toString()).items.none { it.title == s.name })
        // La semana que viene también; la pasada, no (cuenta desde hoy)
        assertEquals(s.name, running(a, wednesday.plusDays(7)).single().title)
        assertTrue(running(a, wednesday.plusDays(-7)).none { it.title == s.name })
    }

    @Test fun ownSessionOnARestDayIsAddedAndCountsAsActiveMinutes() {
        val a = app()
        val before = Planner(a.data, today).week(today).activeMinutes
        a.saveOwnSession(series.copy(days = listOf("TUE")))
        assertEquals("Series de 3 minutos", running(a, today.plusDays(1)).single().title)
        assertTrue(Planner(a.data, today).week(today).activeMinutes > before)
    }

    @Test fun scheduledOnADateWinsOverTheWeeklyOneAndCanBeRemoved() {
        val a = app()
        val weekly = a.saveOwnSession(series)
        val easy = a.saveOwnSession(OwnSession("", "Rodaje", "running", listOf(OwnStep("Suave", 40.0, "Z2")), since = ""))
        val wednesday = today.plusDays(2)
        a.scheduleOwnSession(easy.id, wednesday.toString())
        assertEquals("Rodaje", running(a, wednesday).single().title)
        assertEquals(weekly.name, running(a, wednesday.plusDays(7)).single().title)  // solo ese día
        a.unscheduleOwnSession(easy.id, wednesday.toString())
        assertEquals(weekly.name, running(a, wednesday).single().title)
        // Hoy mismo, y nunca en el pasado
        a.doOwnSessionToday(easy.id)
        assertEquals("Rodaje", running(a, today).single().title)
        assertFailsWith<IllegalArgumentException> { a.scheduleOwnSession(easy.id, today.plusDays(-1).toString()) }
        assertFailsWith<IllegalArgumentException> { a.scheduleOwnSession("no-existe", today.toString()) }
        // Editarla conserva los días puestos a mano
        assertEquals(listOf(today.toString()), a.saveOwnSession(easy.copy(name = "Rodaje largo")).dates)
        a.deleteOwnSession(easy.id)
        assertTrue(running(a, today).none { it.title.startsWith("Rodaje") })
    }

    @Test fun oneOwnSessionPerSportAndWeekday() {
        val a = app()
        val first = a.saveOwnSession(series)
        val second = a.saveOwnSession(series.copy(name = "Otra", days = listOf("WED", "SAT")))
        assertEquals(emptyList(), a.ownSessions.first { it.id == first.id }.days)  // el miércoles pasa a la nueva
        assertEquals(listOf("WED", "SAT"), a.ownSessions.first { it.id == second.id }.days)
        // Otro deporte no le quita el día
        val ride = a.saveOwnSession(OwnSession("", "Rodillo", "cycling", listOf(OwnStep("Z2", 45.0, "Z2")), listOf("WED"), since = ""))
        assertEquals(listOf("WED", "SAT"), a.ownSessions.first { it.id == second.id }.days)
        assertEquals(listOf("cycling", "running"), a.day(today.plusDays(2).toString()).items.map { it.sport }.filter { it != "strength" }.sorted())
        assertTrue(ride.id != second.id)
    }

    @Test fun goesToTheWatchWithItsStepsAndRepeats() {
        val a = app()
        val s = a.saveOwnSession(series)
        a.doOwnSessionToday(s.id)
        val w = a.watchWorkouts("running").single()
        assertEquals("Brivel · Series de 3 minutos", w.name)
        assertEquals(listOf(1, 5, 1), w.blocks.map { it.times })
        assertEquals(listOf(900, 180, 600), w.blocks.map { it.steps.first().seconds })
        assertEquals(120, w.blocks[1].steps.last().seconds)  // el descanso entre series
        assertEquals("recovery", w.blocks[1].steps.last().kind)
    }

    @Test fun intensityKindFollowsTheHardestStep() {
        val a = app()
        fun kind(zone: String) = a.saveOwnSession(OwnSession("", "Prueba $zone", "running", listOf(OwnStep("x", 20.0, zone)), since = ""))
            .let { s -> a.doOwnSessionToday(s.id); a.day(today.toString()).items.first { it.sport == "running" }.kind }
        assertEquals(listOf("recovery", "aerobic", "tempo", "threshold", "vo2max"), OWN_ZONE_KEYS.map { kind(it) })
    }

    @Test fun invalidSessionsAreRejectedWithAReason() {
        val a = app()
        val ok = OwnStep("x", 10.0, "Z2")
        for (bad in listOf(series.copy(name = " "), series.copy(sport = "strength"), series.copy(steps = emptyList()),
            series.copy(steps = List(21) { ok }), series.copy(steps = listOf(ok.copy(minutes = 0.0))), series.copy(steps = listOf(ok.copy(intensityZone = "Z9"))),
            series.copy(steps = listOf(ok.copy(reps = 0))), series.copy(steps = listOf(ok.copy(restMinutes = 31.0))),
            series.copy(days = listOf("LUNES")), series.copy(steps = listOf(ok.copy(minutes = 300.0, reps = 3)))))
            assertFailsWith<IllegalArgumentException> { a.saveOwnSession(bad) }
        assertTrue(a.ownSessions.isEmpty())
    }
}
