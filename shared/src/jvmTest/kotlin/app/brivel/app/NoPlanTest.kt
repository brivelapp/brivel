package app.brivel.app

import app.brivel.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «Ya tengo mi rutina» (GoalKind.OWN): sin sesiones de Brivel; Hoy y el calendario enseñan solo lo tuyo. */
class NoPlanTest {
    private val today = LocalDate(2026, 10, 19)  // lunes
    private val legs = Routine("", "Pierna", listOf(RoutineExercise("Sentadilla con barra", 4, 6, 8)),
        days = listOf("MON", "WED"), since = "")
    private val upper = Routine("", "Torso", listOf(RoutineExercise("Press de banca con mancuernas", 3, 6, 10)),
        days = listOf("TUE", "FRI"), since = "")

    private fun app() = BrivelApp(MemoryStore(), MemoryStore(), { today }).apply {
        saveProfile(UserProfile(birthYear = 1990))
        setGoal(GoalKind.OWN)
    }

    @Test fun noRoutinesMeansAnEmptyWeek() {
        val a = app()
        assertFalse(a.needsOnboarding)
        assertTrue((0..6).all { a.day(today.plusDays(it).toString()).items.isEmpty() })
        val t = a.todayScreen()
        assertTrue(t.noPlan)
        assertNull(t.goalLine)
        assertFalse(t.askReadiness)
        assertEquals("", t.stateTitle)  // sin reloj ni rutina: sin tarjeta de estado
        assertNull(t.nextRoutine)
        assertNull(a.planOverview())
        assertFalse(a.canChangeDay(today.toString()))
    }

    @Test fun routinesFillTheirDaysAndNothingElse() {
        val a = app()
        a.saveRoutine(legs)
        a.saveRoutine(upper)
        val week = (0..6).map { a.day(today.plusDays(it).toString()).items.map { i -> i.title } }
        assertEquals(listOf(listOf("Pierna"), listOf("Torso"), listOf("Pierna"), emptyList(), listOf("Torso"), emptyList(), emptyList()), week)
        val t = a.todayScreen()
        assertEquals("Pierna", t.day.items.single().title)
        assertTrue(t.headline.startsWith("Hoy: Pierna"))
        assertNull(t.nextRoutine)  // hoy ya toca una
    }

    @Test fun aFreeDayNamesTheNextRoutine() {
        val a = BrivelApp(MemoryStore(), MemoryStore(), { today.plusDays(3) }).apply {  // jueves
            saveProfile(UserProfile(birthYear = 1990))
            setGoal(GoalKind.OWN)
            saveRoutine(upper)
            saveRoutine(legs)
        }
        assertEquals("Mañana, Torso", a.todayScreen().nextRoutine)
        val saturday = BrivelApp(MemoryStore(), MemoryStore(), { today.plusDays(5) }).apply {
            saveProfile(UserProfile(birthYear = 1990))
            setGoal(GoalKind.OWN)
            saveRoutine(upper)
        }
        assertEquals("El martes, Torso", saturday.todayScreen().nextRoutine)
    }

    @Test fun startingWithoutPlanAsksEverythingWhenAPlanComes() {
        val a = BrivelApp(MemoryStore(), MemoryStore(), { today })
        a.startWithoutPlan(1990)
        assertFalse(a.needsOnboarding)
        assertEquals(1990, a.data.profile!!.birthYear)
        assertFalse(a.hasPlanProfile)  // sin días: si pide un plan, se le pregunta todo
        a.abandonGoal()
        assertTrue(a.needsOnboarding)
        // Quien ya tenía un plan y pasa a sin plan conserva su perfil
        val b = BrivelApp(MemoryStore(), MemoryStore(), { today }).apply {
            saveProfile(UserProfile(birthYear = 1985, days = mapOf("MON" to 45)))
            setGoal(GoalKind.FITNESS)
            startWithoutPlan(null)
        }
        assertTrue(b.hasPlanProfile)
        assertEquals(1985, b.data.profile!!.birthYear)
        assertEquals(GoalKind.OWN, b.data.goal!!.kind)
    }

    @Test fun loggedStrengthCountsAsDoneForTheWeek() {
        val a = app()
        a.saveRoutine(legs)
        a.logSet(today.toString(), "Sentadilla con barra", 1, 80.0, 8, null)
        assertEquals(0, a.todayScreen().strengthDone)  // 1 de 4 series: aún no cuenta como hecha
        for (set in 2..3) a.logSet(today.toString(), "Sentadilla con barra", set, 80.0, 8, null)
        assertEquals(1, a.todayScreen().strengthDone)
    }
}
