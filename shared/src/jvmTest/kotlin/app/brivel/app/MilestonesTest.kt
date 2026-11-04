package app.brivel.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MilestonesTest {
    private fun activity(type: String, date: String, meters: Double, seconds: Double): JsonObject = Json.parseToJsonElement(
        """{"type":"$type","start_date_local":"${date}T08:00:00","distance":$meters,"moving_time":$seconds}""").jsonObject

    @Test fun longestPerSportIgnoresShortOnes() {
        val list = longest(listOf(activity("Run", "2026-10-01", 10000.0, 3000.0), activity("TrailRun", "2026-10-05", 15200.0, 5400.0),
            activity("Run", "2026-10-06", 30000.0, 300.0), activity("Swim", "2026-10-07", 1500.0, 1800.0)))
        assertEquals(listOf(Milestone("Carrera más larga", "15,2 km", "2026-10-05"), Milestone("Nado más largo", "1500 m", "2026-10-07")), list)
    }

    @Test fun whoStreakCountsUpToTheLastFullWeek() {
        fun weeks(vararg m: Double) = m.mapIndexed { i, v -> Point("w$i", v) }
        assertEquals("2 semanas", whoStreak(weeks(100.0, 160.0, 200.0, 40.0))!!.value)  // esta semana aún no llega: no corta
        assertEquals("3 semanas", whoStreak(weeks(100.0, 160.0, 200.0, 150.0))!!.value)
        assertNull(whoStreak(weeks(160.0, 100.0, 10.0)))
    }

    @Test fun heaviestSetPerExercise() {
        val sets = listOf(SetLog("2026-10-01", "Sentadilla", 1, 60.0, 8), SetLog("2026-10-08", "Sentadilla", 1, 62.5, 6),
            SetLog("2026-10-08", "Sentadilla", 2, 62.5, 7), SetLog("2026-10-02", "Remo", 1, null, 10))
        assertEquals(listOf(Milestone("Sentadilla", "62,5 kg × 7", "2026-10-08")), strengthRecords(sets))
    }

    @Test fun thresholdsFromIntervalsSettings() {
        val athlete = Json.parseToJsonElement("""{"sportSettings":[{"types":["Run"],"lthr":170,"max_hr":190,"threshold_pace":3.7037},
            {"types":["Ride"],"lthr":165,"ftp":250}]}""").jsonObject
        assertEquals(listOf("FC umbral" to "165 ppm", "Ritmo umbral" to "4:30 /km", "FTP" to "250 W", "FC máxima" to "190 ppm"),
            thresholds(athlete).map { it.label to it.value })
    }
}
