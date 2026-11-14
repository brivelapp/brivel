package app.brivel.app

import app.brivel.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Bloque D de la visión: el plan entero, semana a semana. */
class PlanOverviewTest {
    private val today = LocalDate(2026, 10, 21)  // miércoles
    private val profile = UserProfile(birthYear = 1990, level = "intermediate", strength = "gym",
        cardio = listOf("running"), days = mapOf("MON" to 60, "WED" to 60, "SAT" to 90))

    private fun app() = BrivelApp(MemoryStore(), MemoryStore(), { today }).apply { saveProfile(profile) }

    @Test fun noGoalNoOverview() = assertNull(app().planOverview())

    @Test fun raceOverviewRunsFromThisWeekToRaceWeek() {
        val a = app()
        val raceDay = today.plusDays(7 * 12 + 4)  // domingo, dentro de 12 semanas
        a.setGoal(GoalKind.RACE, RaceInfo("Media de Benidorm", "running", raceDay.toString(), distanceKm = 21.1))
        val o = a.planOverview()!!
        assertEquals("Media de Benidorm", o.title)
        assertEquals(13, o.weeks.size)  // esta semana y las 12 siguientes
        assertEquals("13 semanas hasta Media de Benidorm", o.subtitle)
        assertEquals(mondayOf(today).toString(), o.weeks.first().monday)
        assertEquals("19 de octubre", o.weeks.first().title)
        assertEquals(listOf(true) + List(12) { false }, o.weeks.map { it.isCurrent })
        // Solo la última semana lleva la carrera, y cada semana tiene su fase en llano
        assertEquals(List(12) { null } + "Media de Benidorm", o.weeks.map { it.race })
        assertTrue(o.weeks.all { it.phase in Planner.PHASE_LABEL.values }, o.weeks.map { it.phase }.toString())
        assertEquals("Puesta a punto", o.weeks.last().phase)
        // Lo mismo que el planificador da para esa semana
        val third = Planner(a.data, today).week(mondayOf(today).plusDays(14)).days.flatMap { it.items }
        assertEquals(third.sumOf { it.durationMin } to third.size, o.weeks[2].minutes to o.weeks[2].sessions)
        assertTrue(o.weeks.all { it.minutes > 0 && it.key != null })
    }

    @Test fun withoutADateItShowsWhatIsLeftOfTheBlock() {
        val a = app()
        a.setGoal(GoalKind.FITNESS)
        val o = a.planOverview()!!
        assertEquals("Ponerme en forma", o.title)
        assertEquals(listOf("Semana 1 de 4", "Semana 2 de 4", "Semana 3 de 4", "Semana 4 de 4 (más suave)"), o.weeks.map { it.phase })
        assertEquals(1, o.weeks.count { it.isCurrent })
        assertTrue(o.weeks.all { it.race == null && it.strengthDays > 0 })
        assertTrue(o.subtitle.startsWith("Bloque 1:"))
    }
}
