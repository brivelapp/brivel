package app.brivel.app

import app.brivel.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Los tests de campo cambian «rodaje suave» por cifras: min/km, pulso y vatios (FieldTests.kt). */
class FieldTestsTest {
    private val today = LocalDate(2026, 10, 19)  // lunes

    private fun app(cardio: List<String>, level: String = "intermediate") = BrivelApp(MemoryStore(), MemoryStore(), { today }).apply {
        saveProfile(UserProfile(birthYear = 1990, level = level, strength = "gym", cardio = cardio,
            days = mapOf("MON" to 60, "TUE" to 60, "WED" to 60, "THU" to 60, "SAT" to 90)))
        setGoal(GoalKind.FITNESS)
    }

    private fun BrivelApp.cardio(sport: String) =
        (0..6).flatMap { day(today.plusDays(it).toString()).items }.filter { it.sport == sport }

    @Test fun withoutTestsThereIsNoNumber() {
        val a = app(listOf("running"))
        assertTrue(a.cardio("running").isNotEmpty())
        assertTrue(a.cardio("running").all { it.target == null })
        assertEquals("Rodaje suave", a.cardio("running").first { it.kind == "aerobic" }.title)
    }

    @Test fun theThirtyMinuteTestGivesPacesAndAPulseCeiling() {
        val a = app(listOf("running"))
        a.saveFieldTest(FieldTest(today.toString(), "running", "threshold", paceSecKm = 292.0, hr = 168.0))
        val easy = a.cardio("running").first { it.kind == "aerobic" }
        // Friel: Z2 entre el 114 y el 129 % del ritmo umbral (4:52 /km); techo de pulso, umbral − 30
        assertEquals("5:33-6:17 /km", easy.target)
        assertEquals("y por debajo de 138 ppm", easy.targetNote)
        assertTrue(easy.steps.all { it.target == null || it.target!!.endsWith("/km") })
    }

    @Test fun theTalkTestGivesTheEasyPaceAndItsPulse() {
        val a = app(listOf("running"), level = "beginner")
        a.saveFieldTest(FieldTest(today.toString(), "running", "talk", paceSecKm = 434.0, hr = 146.0))  // 7:14 /km
        val easy = a.cardio("running").first { it.kind == "aerobic" }
        assertEquals("7:14-8:06 /km", easy.target)  // hasta un 12 % más lento
        assertEquals("y por debajo de 146 ppm", easy.targetNote)
    }

    @Test fun theFtpTestGivesWattsOnTheBike() {
        val a = app(listOf("cycling"))
        a.saveFieldTest(FieldTest(today.toString(), "cycling", "ftp", watts = 252.0))  // FTP = 95 % = 239 W
        val ride = a.cardio("cycling").first { it.kind == "aerobic" }
        assertEquals("134-180 W", ride.target)  // Coggan Z2: 56-75 %
        assertNull(ride.targetNote)  // sin umbral de pulso de bici no hay techo
    }

    @Test fun theBikePulseTestGivesAPulseCeilingNotAOneBeatWindow() {
        val a = app(listOf("cycling"))
        a.saveFieldTest(FieldTest(today.toString(), "cycling", "lthr", hr = 165.0))
        val ride = a.cardio("cycling").first { it.kind == "aerobic" }
        assertNotNull(ride.target)
        assertEquals("<= 135 ppm", ride.target)  // antes «134-135 ppm»
    }

    @Test fun impossibleResultsAreRejected() {
        val a = app(listOf("running"))
        assertFailsWith<IllegalArgumentException> { a.saveFieldTest(FieldTest(today.toString(), "running", "threshold", paceSecKm = 60.0)) }
        assertFailsWith<IllegalArgumentException> { a.saveFieldTest(FieldTest(today.toString(), "running", "ftp", watts = 200.0)) }
        assertFailsWith<IllegalArgumentException> { a.saveFieldTest(FieldTest(today.plusDays(1).toString(), "running", "talk", paceSecKm = 420.0)) }
        assertTrue(a.data.fieldTests.isEmpty())
    }

    private fun BrivelApp.tests() = (0..6).flatMap { day(today.plusDays(it).toString()).items }.filter { it.testKind != null }

    @Test fun aRegularRunnerGetsTheThirtyMinuteTestAfterAnEasyRun() {
        val a = app(listOf("running"))
        val runs = (0..6).map { today.plusDays(it).toString() }.filter { d -> a.day(d).items.any { it.sport == "running" } }
        val test = a.tests().single()
        assertEquals("Test de 30 minutos", test.title)
        assertEquals("threshold", test.testKind)
        assertTrue(a.day(runs[0]).items.none { it.testKind != null })  // nunca en la primera: antes, un rodaje suave
        assertEquals(1, test.alternatives.size)  // la sesión de siempre, por si no quiere hacerlo hoy
    }

    @Test fun whenNoRunFitsTheTestTakesALongerCardioDay() {
        // Carreras de 45′ y caminata de 90′ el sábado: el test (50′) va el sábado, en lugar de la caminata
        val a = BrivelApp(MemoryStore(), MemoryStore(), { today }).apply {
            saveProfile(UserProfile(birthYear = 1990, level = "intermediate", strength = "gym", cardio = listOf("walking", "running"),
                days = mapOf("MON" to 45, "TUE" to 45, "WED" to 45, "THU" to 45, "FRI" to 45, "SAT" to 90, "SUN" to 45)))
            setGoal(GoalKind.FITNESS)
        }
        val test = a.tests().single()
        assertEquals("threshold", test.testKind)
        val day = (0..6).map { today.plusDays(it).toString() }.first { d -> a.day(d).items.any { it.testKind != null } }
        assertTrue(a.day(day).items.sumOf { it.durationMin } <= 90)
    }

    @Test fun aBeginnerGetsTheTalkTestNeverAnAllOutOne() {
        val a = app(listOf("running"), level = "beginner")
        assertEquals(listOf("talk"), a.tests().map { it.testKind })
        assertEquals("Test del habla", a.tests().single().title)
    }

    @Test fun theTestGoesAwayWithItsResultAndComesBackAfterTenWeeks() {
        val a = app(listOf("running"))
        a.saveTestResult(today.toString(), "threshold", "6,16", "168")
        assertTrue(a.tests().isEmpty())
        assertEquals(292.0, a.data.fieldTests.single().paceSecKm!!, 0.5)  // 30′ en 6,16 km: 4:52 /km
        val later = BrivelApp(MemoryStore(), MemoryStore(), { today.plusDays(77) }).apply {
            saveProfile(a.data.profile!!)
            setGoal(GoalKind.FITNESS)
            saveFieldTest(a.data.fieldTests.single())
        }
        val again = (0..6).flatMap { later.day(today.plusDays(77 + it).toString()).items }.filter { it.testKind != null }
        assertEquals(listOf("threshold"), again.map { it.testKind })
    }

    @Test fun theBikeTestDependsOnThePowerMeter() {
        val a = app(listOf("cycling"))
        assertEquals(listOf("lthr"), a.tests().map { it.testKind })
        a.replaceData(a.data.copy(activities = listOf(OwnActivity(id = "fit1", sport = "cycling", start = "2026-10-10T08:00:00",
            seconds = 3600.0, avgPower = 180.0))))
        assertEquals(listOf("ftp"), a.tests().map { it.testKind })
        assertEquals("Test de 20 minutos", a.tests().single().title)
    }

    @Test fun noPlanMeansNoTests() {
        val a = BrivelApp(MemoryStore(), MemoryStore(), { today }).apply { startWithoutPlan(1990) }
        assertTrue(a.tests().isEmpty())
    }

    @Test fun resultsAreReadAsTheyAreWritten() {
        assertEquals(434.0, parseTestResult("2026-10-19", "talk", "7:14", null).paceSecKm)
        assertEquals(146.0, parseTestResult("2026-10-19", "talk", "7:14", "146").hr)
        assertEquals(240.0, parseTestResult("2026-10-19", "ftp", "240", null).watts)
        assertFailsWith<IllegalArgumentException> { parseTestResult("2026-10-19", "talk", "siete", null) }
        assertFailsWith<IllegalArgumentException> { parseTestResult("2026-10-19", "threshold", "0", null) }
    }
}
