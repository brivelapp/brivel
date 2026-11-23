package app.brivel.app

import app.brivel.engine.DROP_JUMPS
import app.brivel.engine.Profile
import app.brivel.engine.buildStrength
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Los saltos de caída piden base de fuerza; sin ella, un salto vertical (NSCA; buildStrength). */
class JumpsTest {
    private fun build(base: Boolean) =
        buildStrength("running", Profile(activeSports = listOf("running"), strength = "gym", strengthBase = base), "build").exercises

    @Test fun withoutStrengthBaseThereAreNoDropJumps() {
        assertFalse(DROP_JUMPS in build(false))
        assertTrue("Salto vertical" in build(false))
        assertTrue(DROP_JUMPS in build(true))
    }
}
