package app.brivel.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Las claves propias de cada ejercicio, con su fuente (ExerciseCues.kt); el resto, las de su patrón. */
class ExerciseCuesTest {
    @Test fun ownCuesCarryTheirSourceAndCertainty() {
        val card = exerciseCard("Plancha de Copenhague")!!
        assertEquals("Si te cuesta, apoya la rodilla", card.cues.last())  // sin pauta de dolor (Gabriel, 2026-11-19)
        assertTrue(card.cueSource.startsWith("Claves de Registro ISRCTN98514933"), card.cueSource)
        assertTrue("Certeza media" in card.cueSource)
    }

    @Test fun withoutASolidSourceThePatternCuesStay() {
        assertEquals(PATTERN_CUES["bisagra"], exerciseCard("Peso muerto rumano")!!.cues)
        assertEquals(CUE_SOURCE, exerciseCard("Peso muerto rumano")!!.cueSource)
    }

    @Test fun everyOwnCueBelongsToAnEngineExerciseAndFitsOnScreen() {
        assertEquals(33, EXERCISE_CUES.size)
        EXERCISE_CUES.forEach { (name, c) ->
            assertTrue(name in EXERCISES, name)
            assertEquals(3, c.cues.size, name)
            c.cues.forEach { assertTrue(it.length <= 80, "$name: $it") }
        }
    }
}
