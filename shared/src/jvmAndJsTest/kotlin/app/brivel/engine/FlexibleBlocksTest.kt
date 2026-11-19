package app.brivel.engine

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Bloques flexibles del plan sin fecha (decisión de Gabriel, 2026-11-19; MOTOR_ENTRENAMIENTO.md §4). */
class FlexibleBlocksTest {
    private val p = HealthProfile("fitness", mapOf("MON" to 60, "WED" to 60, "FRI" to 60, "SAT" to 90), strength = "gym",
        cardio = listOf("running"), level = "beginner")
    private val start = LocalDate(2026, 1, 5)  // lunes

    private fun run(weeks: Int, adherence: Double = 1.0, soft: (Int) -> Int = { 0 }): List<WeekStep> =
        (0 until weeks).fold(listOf(firstStep(p))) { out, w -> out + nextStep(p, out.last(), adherence, soft(w)) }

    @Test fun goingWellItRisesFiveWeeksThenRestsAndComesBackALittleHigher() {
        val s = run(8)
        val minutes = s.map { stepDose(p, it).aerobic }
        assertEquals(listOf(180, 198, 217, 238, 261), minutes.take(5))  // +10 % (hacia abajo) cada semana
        assertEquals((0..4).toList(), s.take(5).map { it.loadWeek })
        assertTrue(s[5].deload)
        assertEquals(183, minutes[5])  // el 70 % de la última de subida
        assertEquals(274, minutes[6])  // vuelve un 5 % por encima de 261
        assertEquals(1, s[6].block)
        assertFalse(s[6].deload)
    }

    @Test fun twoSoftDaysBringTheRestForwardButNotInTheFirstWeeks() {
        assertFalse(run(1, soft = { 3 })[1].deload)  // tras 1 semana de subida, aún no
        val s = run(3, soft = { if (it == 1) 2 else 0 })
        assertTrue(s[2].deload)  // tras 2 semanas, con 2 días suaves
        assertEquals(stepDose(p, s[1]).aerobic * 0.7, stepDose(p, s[2]).aerobic.toDouble(), 1.0)
        assertFalse(run(3, soft = { 1 })[2].deload)  // un día suave no basta
    }

    @Test fun halfDoneRepeatsAndLittleDoneGoesDownButNotBelowAnHour() {
        val held = run(3, adherence = 0.7)
        assertTrue(held.dropLast(1).all { it.aerobic == 180.0 })  // repite, pero cuenta como semana de subida
        val low = run(10, adherence = 0.2)
        assertEquals(144.0, low[1].aerobic)  // −20 %
        assertTrue(low.all { it.aerobic >= 60.0 })
    }

    @Test fun theDoseStopsAtTheGoal() {
        val s = run(40)
        assertTrue(s.all { it.aerobic <= 300.0 && it.sets <= 10.0 })
        assertEquals(300, s.filterNot { it.deload }.maxOf { stepDose(p, it).aerobic })
    }

    @Test fun theHistoryRebuildsEachWeekFromWhatWasDone() {
        // Todo lo planificado, hecho a mano: la misma progresión que con adherencia 1
        val done = mutableMapOf<String, List<Done>>()
        var step = firstStep(p)
        repeat(3) { w ->
            weekPlan(p, start.plusDays(7 * w), step).days.forEach { d -> done[d.date] = d.sessions.map { Done(it.sport, "manual") } }
            step = nextStep(p, step, 1.0)
        }
        assertEquals(run(3), stepHistory(p, start, done, 3))
        // Sin nada hecho, baja; y los días suaves llegan a nextStep
        assertEquals(144.0, stepHistory(p, start, emptyMap(), 1)[1].aerobic)
        val soft = stepHistory(p, start, done, 3) { monday -> if (monday == start.plusDays(7)) 2 else 0 }
        assertTrue(soft[2].deload)
    }
}
