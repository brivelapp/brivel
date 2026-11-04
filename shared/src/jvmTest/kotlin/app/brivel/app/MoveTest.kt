package app.brivel.app

import app.brivel.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoveTest {
    private val today = LocalDate(2026, 10, 22)  // jueves
    private val profile = UserProfile(birthYear = 1990, level = "intermediate", strength = "gym",
        cardio = listOf("running", "cycling"), days = mapOf("MON" to 60, "TUE" to 60, "THU" to 75, "SAT" to 90, "SUN" to 60))

    private fun app() = BrivelApp(MemoryStore(), MemoryStore(), { today }).apply {
        saveProfile(profile)
        setGoal(GoalKind.FITNESS)
    }

    @Test fun moveGoesToTheOtherDayAndBack() {
        val a = app()
        val from = (0..3).map { today.plusDays(it).toString() }.first { a.day(it).items.isNotEmpty() }
        val item = a.day(from).items.first()
        val to = a.moveTargets(from, item.slot).first()
        val before = a.day(to).items.size

        a.moveSession(from, item.slot, to)
        assertTrue(a.day(from).items.none { it.slot == item.slot })
        assertTrue(a.day(from).note!!.startsWith("La has movido al "))
        val moved = a.day(to).items.single { it.slot == item.slot }
        assertEquals(from, moved.movedFrom)
        assertEquals(item.title, moved.title)
        assertEquals(before + 1, a.day(to).items.size)

        a.moveSession(to, item.slot, from)  // al día del plan: deshace el cambio
        assertTrue(a.data.moves.isEmpty())
        assertNull(a.day(from).items.single { it.slot == item.slot }.movedFrom)
    }

    @Test fun onlyRemainingDaysOfThatWeek() {
        val a = app()
        val past = today.plusDays(-1).toString()
        assertTrue(a.moveTargets(past, "strength").isEmpty())
        val from = (0..3).map { today.plusDays(it).toString() }.first { a.day(it).items.isNotEmpty() }
        val slot = a.day(from).items.first().slot
        val targets = a.moveTargets(from, slot)
        assertTrue(targets.all { it >= today.toString() && it <= "2026-10-25" && it != from })
        assertFailsWith<IllegalArgumentException> { a.moveSession(from, slot, past) }
        assertFailsWith<IllegalArgumentException> { a.moveSession(from, slot, "2026-10-26") }
    }
}
