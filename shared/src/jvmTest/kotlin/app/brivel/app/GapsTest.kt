package app.brivel.app

import app.brivel.engine.Block
import app.brivel.engine.Profile
import app.brivel.engine.Session
import app.brivel.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Casos que el informe de cobertura del 2026-11-14 señaló sin ejecutar. */
class GapsTest {
    private val today = LocalDate(2026, 10, 22)  // jueves
    private val profile = UserProfile(birthYear = 1990, level = "intermediate", strength = "gym",
        cardio = listOf("running", "cycling"), days = mapOf("MON" to 60, "TUE" to 60, "THU" to 75, "SAT" to 90, "SUN" to 60))

    private fun app() = BrivelApp(MemoryStore(), MemoryStore(), { today }).apply {
        saveProfile(profile)
        setGoal(GoalKind.FITNESS)
    }

    @Test fun aSteadyBlockIsActiveAndAnEasyOneIsRecovery() {
        fun kinds(zone: String) = watchWorkout(Session("R-2", "running", "vo2max", "Rodaje", "Base", 40, 30,
            listOf(Block("calentamiento", 1, 10.0, "Z1"), Block("principal", 1, 30.0, zone)), emptyMap(), ifBad = "", ifGood = ""),
            "2026-11-04", Profile(listOf("running"), lthr = 170.0, maxHr = 190.0)).blocks.map { it.steps.single().kind }
        assertEquals(listOf("warmup", "active"), kinds("Z2"))
        assertEquals(listOf("warmup", "recovery"), kinds("Z1"))
    }

    @Test fun withoutAWatchConnectionThereIsNowhereToSend() {
        val a = app()
        assertNull(a.watchRoute)
        assertTrue("intervals.icu" in a.watchSentText)  // el texto por defecto nombra la vía que queda
    }

    @Test fun aChosenAlternativeCanBeUndone() {
        val a = app()
        val date = (0..6).map { today.plusDays(it).toString() }.first { d -> a.day(d).items.any { it.alternatives.isNotEmpty() } }
        val item = a.day(date).items.first { it.alternatives.isNotEmpty() }
        val other = item.alternatives.first()
        a.chooseAlternative(date, item.slot, other.sport)
        assertTrue(a.day(date).items.single { it.slot == item.slot }.let { it.chosen && it.sport == other.sport })
        a.clearChoice(date)
        assertTrue(a.data.choices.isEmpty())
        assertTrue(a.day(date).items.single { it.slot == item.slot }.let { !it.chosen && it.sport == item.sport })
    }

    @Test fun theWeekGoesBackToThePlanAfterAChange() {
        val a = app()
        val before = (0..6).map { a.day(mondayOf(today).plusDays(it).toString()).items.map { i -> i.title } }
        a.changeDay(today.plusDays(2).toString(), 0)  // el sábado no puede
        assertTrue(a.data.weekChanges.isNotEmpty())
        a.resetWeek()
        assertTrue(a.data.weekChanges.isEmpty())
        assertEquals(before, (0..6).map { a.day(mondayOf(today).plusDays(it).toString()).items.map { i -> i.title } })
    }

    @Test fun theLastStrengthDayIsTheMostRecentOneOfTheWeek() {
        val a = app()
        val expected = (0..6).map { today.plusDays(-it).toString() }.firstOrNull { a.strengthScreen(it) != null }
        assertEquals(expected, a.lastStrengthDate())
        assertNull(BrivelApp(MemoryStore(), MemoryStore(), { today }).apply {
            saveProfile(profile.copy(strength = "none")); setGoal(GoalKind.FITNESS)
        }.lastStrengthDate())
    }

    @Test fun theLibraryByZoneUsesTheUsersEquipmentOnlyWhenAsked() {
        val a = app()
        val all = a.libraryByZone("", "pierna", null, false)
        assertEquals(all, a.libraryByZone("", "pierna", null, true))  // sin material indicado, «solo con el mío» no filtra
        a.setEquipment(listOf("mancuernas"))
        val mine = a.libraryByZone("", "pierna", null, true)
        assertTrue(mine.size < all.size && mine.all { c -> c.zone == "pierna" && c.equipment.all { it == "peso corporal" || it == "mancuernas" } })
        assertEquals(all, a.libraryByZone("", "pierna", null, false))
        assertTrue(a.libraryByZone("sentadilla", null, "barra", false).all { "barra" in it.equipment })
    }

    @Test fun heartRateZonesGrowAndFollowTheHighestPulseSeen() {
        val a = app()
        val byAge = a.zoneBounds(null)
        assertEquals(6, byAge.size)
        assertTrue(byAge.zipWithNext().all { (x, y) -> x < y })
        val measured = a.zoneBounds(205.0)  // un máximo medido por encima del de la edad sube todas las zonas
        assertTrue(measured.zip(byAge).all { (m, b) -> m > b })
        assertEquals(byAge, a.zoneBounds(100.0))  // uno por debajo no las baja
    }
}
