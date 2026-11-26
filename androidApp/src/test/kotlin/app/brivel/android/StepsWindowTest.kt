package app.brivel.android

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** Los pasos de cada día se cuentan de medianoche a medianoche, como Health Connect (los de la madre de Gabriel, 2026-11-18). */
class StepsWindowTest {
    @Test fun daysStartAtMidnightNotAtSyncTime() {
        val (from, until) = stepsWindow(LocalDate.of(2026, 11, 18), 30, LocalDateTime.of(2026, 11, 18, 17, 42))
        assertEquals(LocalDateTime.of(2026, 10, 19, 0, 0), from)
        assertEquals(LocalDateTime.of(2026, 11, 18, 17, 42), until)
    }
}
