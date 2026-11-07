package app.brivel.app

import app.brivel.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RoutinesTest {
    private val today = LocalDate(2026, 10, 19)  // lunes
    private val profile = UserProfile(birthYear = 1990, level = "intermediate", strength = "gym",
        cardio = listOf("running"), days = mapOf("MON" to 60, "WED" to 60, "SAT" to 90))

    private fun app() = BrivelApp(MemoryStore(), MemoryStore(), { today }).apply {
        saveProfile(profile)
        setGoal(GoalKind.FITNESS)
    }

    private val torso = Routine("", "Torso", listOf(RoutineExercise("Press de banca con mancuernas", 3, 6, 10),
        RoutineExercise("Plancha frontal", 2, 20, 40)), days = listOf("MON", "THU"), rir = 1, since = "")

    @Test fun routineReplacesOrAddsStrength() {
        val a = app()
        val r = a.saveRoutine(torso)
        assertTrue(r.id.isNotBlank())
        val monday = a.day(today.toString()).items.filter { it.isStrength }
        assertEquals(listOf("Torso"), monday.map { it.title })  // una sola fuerza ese día: la rutina
        assertEquals(listOf("Press de banca con mancuernas: 3 × 6–10 repeticiones", "Plancha frontal: 2 × 20–40″"),
            monday.single().exercises)
        // El jueves no tenía fuerza en el plan: se añade
        assertTrue(a.day(today.plusDays(3).toString()).items.any { it.title == "Torso" })
        // La sesión serie a serie la lee como las del plan, con su reserva
        val screen = a.strengthScreen(today.toString())!!
        assertEquals(listOf(3, 2), screen.exercises.map { it.sets })
        assertEquals(1, screen.exercises.first().rir)
        assertTrue(screen.exercises.last().timed)
    }

    @Test fun daysBelongToOneRoutineAndPastStays() {
        val a = app()
        val first = a.saveRoutine(torso)
        a.saveRoutine(torso.copy(name = "Pierna", exercises = listOf(RoutineExercise("Sentadilla con barra", 4, 5, 8)), days = listOf("THU")))
        assertEquals(listOf("MON"), a.routines.first { it.id == first.id }.days)
        assertFailsWith<IllegalArgumentException> { a.saveRoutine(torso.copy(exercises = emptyList())) }
        assertFailsWith<IllegalArgumentException> { a.saveRoutine(torso.copy(exercises = listOf(RoutineExercise("Inventado", 3, 8, 12)))) }
        // Creada hoy: la semana pasada sigue con la fuerza de Brivel
        assertTrue(a.day(today.plusDays(-7).toString()).items.none { it.title == "Torso" })
        a.deleteRoutine(first.id)
        assertTrue(a.day(today.toString()).items.none { it.title == "Torso" })
    }
}
