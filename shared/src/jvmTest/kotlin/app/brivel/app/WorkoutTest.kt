package app.brivel.app

import app.brivel.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutTest {
    @Test
    fun prescriptionFromLineOrDose() {
        assertEquals(Prescription(3, 8..12, false), prescription("Sentadilla con barra: 3 × 8–12 repeticiones", null))
        assertEquals(Prescription(2, 20..45, true), prescription("Plancha frontal y lateral: 2 × 20–45″ por lado", null))
        // Plan de carrera: la línea es solo el nombre y la dosis va en la sesión; con horquilla de series, la menor
        assertEquals(Prescription(3, 4..8, false), prescription("Sentadilla con barra", "3–4 × 4–8 RM (fuerza máxima)"))
        assertEquals(Prescription(3, 5..8, false), prescription("Saltos al cajón", "3 × 4–6 RM + 3 × 5–8 saltos"))
        assertNull(prescription("Saltos al cajón", "3–4 × 4–8 RM (fuerza máxima)"))
        assertEquals(Prescription(3, 20..45, true), prescription("Plancha lateral", "3–4 × 4–8 RM (fuerza máxima)"))
        assertEquals(Prescription(3, 8..12, false), prescription("Bicho muerto (dead bug)", "3–4 × 4–8 RM (fuerza máxima)"))
        assertNull(prescription("Equilibrio: apoyo a una pierna 3 × 30″ por lado y marcha en tándem 3 × 10 pasos", null))
        assertEquals(180, restSeconds(Prescription(3, 4..8, false), "sentadilla"))
        assertEquals(60, restSeconds(Prescription(3, 20..45, true), "core"))
    }

    @Test
    fun migratesLiftsToOneSetEach() {
        val old = AppData(schema = 2, lifts = listOf(LiftLog("2026-10-20", "Remo con mancuerna", 20.0, 10, 3)))
        val new = migrate(old)
        assertEquals(SCHEMA, new.schema)
        assertTrue(new.lifts.isEmpty())
        assertEquals((1..3).map { SetLog("2026-10-20", "Remo con mancuerna", it, 20.0, 10) }, new.sets)
        // Un móvil sin actualizar que vuelve a mandar el mismo día no lo duplica
        assertEquals(new.sets, migrate(new.copy(lifts = old.lifts)).sets)
    }

    @Test
    fun setByStepWithLastTimeAndSuggestion() {
        val today = LocalDate(2026, 10, 22)
        val app = BrivelApp(MemoryStore(), MemoryStore(), { today })
        app.saveProfile(UserProfile(birthYear = 1990, strength = "gym", days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT").associateWith { 60 }))
        app.setGoal(GoalKind.STRENGTH)
        val date = (0 until 7).map { LocalDate(2026, 10, 19).plusDays(it).toString() }
            .first { it <= today.toString() && app.strengthScreen(it) != null }
        val first = app.strengthScreen(date)!!.exercises.first()
        assertTrue(first.done.isEmpty() && first.previous.isEmpty() && first.weightKg == null)

        // La semana anterior llegó al máximo en todas las series: hoy propone subir
        val before = LocalDate.parse(date).plusDays(-7).toString()
        (1..first.sets).forEach { app.logSet(before, first.name, it, 40.0, first.repsHigh) }
        val again = app.strengthScreen(date)!!.exercises.first()
        assertEquals(42.0, again.weightKg)
        assertEquals(first.sets, again.previous.size)

        app.logSet(date, first.name, 1, 42.0, first.repsLow)
        app.logSet(date, first.name, 2, 42.0, first.repsLow)
        app.logSet(date, first.name, 1, 42.5, first.repsLow)  // corregir sustituye
        assertEquals(listOf(LoggedSet(1, 42.5, first.repsLow), LoggedSet(2, 42.0, first.repsLow)),
            app.strengthScreen(date)!!.exercises.first().done)
        assertTrue("strength" in app.day(date).doneSports)
        assertEquals(42.0, app.strengthScreen(date)!!.exercises.first().weightKg)  // lo de hoy no cambia lo propuesto hoy

        app.deleteSet(date, first.name, 1)
        assertEquals(listOf(LoggedSet(1, 42.0, first.repsLow)), app.strengthScreen(date)!!.exercises.first().done)
        assertFailsWith<IllegalArgumentException> { app.logSet(today.plusDays(1).toString(), first.name, 1, 40.0, 8) }
        assertFailsWith<IllegalArgumentException> { app.logSet(date, first.name, 1, 40.0, 0) }
    }

    @Test
    fun everyPlannedExerciseIsInTheCatalog() {
        val today = LocalDate(2026, 10, 22)
        for (kit in listOf("gym", "bodyweight")) for (goal in listOf(GoalKind.STRENGTH, GoalKind.FITNESS, GoalKind.HEALTH)) {
            val app = BrivelApp(MemoryStore(), MemoryStore(), { today })
            app.saveProfile(UserProfile(birthYear = 1950, strength = kit, days = listOf("MON", "WED", "FRI", "SAT").associateWith { 60 }))
            app.setGoal(goal)
            for (i in 0 until 14) {
                val s = app.strengthScreen(today.plusDays(i).toString()) ?: continue
                assertTrue(s.exercises.isNotEmpty())
                s.other.forEach { assertTrue(it.startsWith("Equilibrio"), it) }
            }
        }
        for (list in listOf(app.brivel.engine.LOWER, app.brivel.engine.PLYO, app.brivel.engine.UPPER))
            list.values.flatten().forEach { assertNotNull(EXERCISES[it], it) }
        app.brivel.engine.CORE.forEach { assertNotNull(EXERCISES[it], it) }
        app.brivel.engine.PATTERNS.values.forEach { (gym, bw) -> assertNotNull(EXERCISES[gym], gym); assertNotNull(EXERCISES[bw], bw) }
    }
}
