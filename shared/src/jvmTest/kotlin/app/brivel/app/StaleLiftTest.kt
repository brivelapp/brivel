package app.brivel.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** El último peso deja de valer como referencia cuando hace más de STALE_DAYS que no se hace el ejercicio. */
class StaleLiftTest {
    private val line = "Sentadilla con barra: 3 × 8–12 repeticiones"
    private fun log(date: String, kg: Double?, reps: Int = 10) = listOf(LiftLog(date, "Sentadilla con barra", kg, reps, 3))

    @Test fun upToEightWeeksTheLastSessionStillCounts() {
        assertEquals("Mismo peso (60 kg) y busca 11 repeticiones", liftHint(log("2026-09-01", 60.0), line, date = "2026-10-27").suggestion)  // 56 días
        assertEquals("Mismo peso (60 kg) y busca 11 repeticiones", liftHint(log("2026-09-01", 60.0), line).suggestion)  // sin fecha, como antes
    }

    @Test fun afterEightWeeksItStartsLighter() {
        val hint = liftHint(log("2026-09-01", 60.0), line, date = "2026-10-28")  // 57 días
        assertEquals("Hace más de 8 semanas que no lo haces: empieza con 54 kg y vuelve a subir desde 8", hint.suggestion)
        assertEquals(54.0, hint.weightKg)
        // Aunque la última vez llegara al máximo: no se sube de peso tras un parón
        assertTrue(liftHint(log("2026-09-01", 60.0, reps = 12), line, date = "2026-12-01").suggestion!!.startsWith("Hace más de 8 semanas"))
    }

    @Test fun withoutWeightItRestartsFromTheLowEndOfTheRange() {
        val hint = liftHint(log("2026-09-01", null), line, date = "2026-12-01")
        assertEquals("Hace más de 8 semanas que no lo haces: empieza por 8 repeticiones", hint.suggestion)
        assertNull(hint.weightKg)
    }

    @Test fun timedExercisesAndNewOnesGetNoInventedAdvice() {
        assertNull(liftHint(emptyList(), line, date = "2026-12-01").suggestion)
        assertNull(liftHint(log("2026-09-01", 60.0), "Plancha frontal: 3 × 20–45″", date = "2026-12-01").suggestion)
    }
}
