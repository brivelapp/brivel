package app.brivel

import kotlin.test.Test
import kotlin.test.assertEquals

class EngineTest {
    @Test
    fun hasVersion() = assertEquals("0.1.0", Engine.VERSION)
}
