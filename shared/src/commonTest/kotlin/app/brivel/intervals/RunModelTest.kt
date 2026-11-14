package app.brivel.intervals

import app.brivel.engine.Profile
import app.brivel.engine.paceAt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** El aviso de LTHR y ritmo umbral desajustados, y las carreras de las que sale la relación FC-velocidad. */
class RunModelTest {
    private val model = 1.0 to 0.02  // velocidad (m/s) = 1 + 0,02·FC: a 170 ppm, 4,4 m/s (3:47 /km)

    private fun profile(lthr: Double? = 170.0, runPace: Double? = paceAt(170.0, model), runModel: Pair<Double, Double>? = model) =
        Profile(listOf("running"), lthr = lthr, runPace = runPace, runModel = runModel)

    @Test fun noWarningWhenEverythingAgreesOrSomethingIsMissing() {
        assertNull(runModelWarning(profile()))
        assertNull(runModelWarning(profile(runPace = paceAt(170.0, model) * 1.05)))  // justo en el 5 %: aún encaja
        assertNull(runModelWarning(profile(runModel = null)))
        assertNull(runModelWarning(profile(lthr = null)))
        assertNull(runModelWarning(profile(lthr = 0.0)))
        assertNull(runModelWarning(profile(runPace = null)))
        assertNull(runModelWarning(profile(runPace = 0.0)))
    }

    @Test fun warnsWithBothPacesWhenTheyDisagreeByMoreThanFivePercent() {
        val slow = assertNotNull(runModelWarning(profile(runPace = 270.0)))  // 4:30 /km frente a 3:47
        assertTrue("LTHR de carrera (170)" in slow && "3:47/km" in slow && "4:30/km" in slow && "R-TEST-LTHR" in slow, slow)
        assertNotNull(runModelWarning(profile(runPace = 200.0)))  // también si el de intervals.icu es más rápido
        // El LTHR de carrera manda sobre el general
        val bySport = Profile(listOf("running"), lthr = 150.0, lthrBySport = mapOf("running" to 170.0), runPace = paceAt(170.0, model), runModel = model)
        assertNull(runModelWarning(bySport))
    }

    private fun activity(type: String, date: String, hr: Double?) = JsonObject(buildMap {
        put("type", JsonPrimitive(type)); put("start_date_local", JsonPrimitive(date))
        hr?.let { put("average_heartrate", JsonPrimitive(it)) }
    })

    @Test fun onlyRecentRunsWithHeartRateFromOldestToNewest() {
        val runs = (1..15).map { activity("Run", "2026-10-${it.toString().padStart(2, '0')}T08:00:00", 150.0) }
        val noise = listOf(activity("Ride", "2026-10-20T08:00:00", 140.0), activity("Run", "2026-10-21T08:00:00", null),
            activity("Run", "2026-10-22T08:00:00", 0.0))
        val picked = runsForModel((runs + noise).shuffled())
        assertEquals(RUNS_FOR_MODEL, picked.size)
        assertEquals((4..15).map { "2026-10-${it.toString().padStart(2, '0')}T08:00:00" }, picked.map { (it["start_date_local"] as JsonPrimitive).content })
        assertEquals(emptyList(), runsForModel(noise))
    }
}
