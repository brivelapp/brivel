package app.brivel.app

import app.brivel.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LiftsTest {
    private val line = "Sentadilla con barra: 3 × 8–12 repeticiones"

    @Test
    fun doubleProgression() {
        val app = BrivelApp(MemoryStore(), MemoryStore(), { LocalDate(2026, 10, 22) })
        assertNull(app.liftHint(line).suggestion)  // sin historial no se inventa un peso
        app.logLift("2026-10-20", line, 60.0, 10, 3)
        assertEquals("Mismo peso (60 kg) y busca 11 repeticiones", app.liftHint(line).suggestion)
        app.logLift("2026-10-22", line, 60.0, 12, 3)
        assertEquals("Llegaste al máximo: hoy prueba 63 kg y vuelve a 8 repeticiones", app.liftHint(line).suggestion)
        app.logLift("2026-10-22", line, 62.5, 8, 3)  // mismo día: sustituye
        assertEquals(6, app.data.sets.size)  // 3 series por día
        assertEquals("Sentadilla con barra", app.data.sets.last().exercise)
        assertFailsWith<IllegalArgumentException> { app.logLift("2026-10-23", line, 60.0, 8, 3) }
        assertNull(repRange("Plancha frontal y lateral: 2 × 20–45″ por lado"))
        // 4 kg → 4,5 kg sería un 12,5 %: más que el tope, así que mantiene el peso
        val curl = "Remo con mancuerna: 3 × 8–12 repeticiones"
        app.logLift("2026-10-22", curl, 4.0, 12, 3)
        assertEquals("Llegaste al máximo, pero subir de 4 kg sería más de un 10 %: sigue con 4 kg y haz 13 repeticiones",
            app.liftHint(curl).suggestion)
        assertEquals(4.0, app.liftHint(curl).weightKg)
        app.logLift("2026-10-22", "Flexiones (inclinadas al empezar): 2 × 10–15 repeticiones", null, 15, 2)
        assertEquals("Llegaste al máximo: pasa a una variante más difícil y vuelve a 10 repeticiones",
            app.liftHint("Flexiones (inclinadas al empezar): 2 × 10–15 repeticiones").suggestion)
    }

    @Test
    fun belowRangeOrStalledMeansLighter() {
        val app = BrivelApp(MemoryStore(), MemoryStore(), { LocalDate(2026, 10, 22) })
        app.logLift("2026-10-15", line, 70.0, 6, 3)
        assertEquals("Te quedaste por debajo de 8: hoy baja a 63 kg y haz 8 o más", app.liftHint(line).suggestion)
        app.logLift("2026-10-17", line, 70.0, 9, 3)
        app.logLift("2026-10-20", line, 70.0, 9, 3)
        assertEquals("Mismo peso (70 kg) y busca 10 repeticiones", app.liftHint(line).suggestion)  // 6 → 9: progresa
        app.logLift("2026-10-22", line, 70.0, 9, 3)
        assertEquals("Tres sesiones sin ganar repeticiones con 70 kg: baja a 63 kg y vuelve a subir desde 8", app.liftHint(line).suggestion)
    }

    @Test
    fun loggingLiftsCountsAsStrengthDone() {
        val today = LocalDate(2026, 10, 22)
        val app = BrivelApp(MemoryStore(), MemoryStore(), { today })
        app.saveProfile(UserProfile(birthYear = 1990, strength = "gym", days = (listOf("MON", "TUE", "WED", "THU", "FRI", "SAT")).associateWith { 60 }))
        app.setGoal(GoalKind.STRENGTH)
        val day = (0 until 7).map { app.day(LocalDate(2026, 10, 19).plusDays(it).toString()) }
            .first { d -> d.date <= today.toString() && d.items.any { it.isStrength } }
        val ex = day.items.first { it.isStrength }.exercises.first()
        app.logLift(day.date, ex, 50.0, 10, 3)
        kotlin.test.assertTrue("strength" in app.day(day.date).doneSports)
    }
}
