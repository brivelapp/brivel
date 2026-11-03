package app.brivel

import app.brivel.app.ageAllowed
import app.brivel.app.toggleCardio
import app.brivel.app.weekTimeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfilePartsTest {
    @Test
    fun walkingIsNeverPickedButIsTheFallback() {
        assertEquals(listOf("running"), toggleCardio(listOf("walking"), "running"))
        assertEquals(listOf("running", "cycling"), toggleCardio(listOf("walking", "running"), "cycling"))
        assertEquals(listOf("walking"), toggleCardio(listOf("running"), "running"))
    }

    @Test
    fun weekTime() {
        assertEquals("0 min", weekTimeText(emptyMap()))
        assertEquals("45 min", weekTimeText(mapOf("MON" to 45)))
        assertEquals("2 h", weekTimeText(mapOf("MON" to 60, "SAT" to 60)))
        assertEquals("4 h 45 min", weekTimeText(mapOf("MON" to 45, "WED" to 60, "FRI" to 30, "SAT" to 90, "SUN" to 60)))
    }

    @Test
    fun halfTypedYearIsNotAnAge() {
        assertFalse(ageAllowed(19, 2026))
        assertFalse(ageAllowed(2013, 2026))
        assertTrue(ageAllowed(2012, 2026))
        assertTrue(ageAllowed(1990, 2026))
    }
}
