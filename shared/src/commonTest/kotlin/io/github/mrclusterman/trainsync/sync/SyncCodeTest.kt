package io.github.mrclusterman.trainsync.sync

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SyncCodeTest {
    @Test
    fun sameTextAsReference() {
        assertEquals(VECTOR_CODE, SyncCode.fromSecret(VECTOR_SECRET).text)
        assertContentEquals(VECTOR_SECRET, SyncCode.parse(VECTOR_CODE)?.secret)
    }

    @Test
    fun generatedCodesRoundTrip() = repeat(50) {
        val code = SyncCode.generate()
        assertEquals(27, code.text.length)
        assertContentEquals(code.secret, SyncCode.parse(code.text)?.secret)
    }

    @Test
    fun forgivingInput() {
        // Minúsculas, espacios y guiones; O por 0 y L o I por 1, como manda Crockford
        val typed = VECTOR_CODE.lowercase().chunked(3).joinToString("-").replace('0', 'o').replace('1', 'l')
        assertContentEquals(VECTOR_SECRET, assertNotNull(SyncCode.parse(" $typed ")).secret)
    }

    @Test
    fun anyMistypedCharacterIsRejected() {
        for (i in VECTOR_CODE.indices) for (c in "0123456789ABCDEFGHJKMNPQRSTVWXYZ") {
            if (c == VECTOR_CODE[i]) continue
            assertNull(SyncCode.parse(VECTOR_CODE.replaceRange(i, i + 1, c.toString())), "posición $i → $c")
        }
        assertNull(SyncCode.parse(VECTOR_CODE.dropLast(1)))
        assertNull(SyncCode.parse(VECTOR_CODE + "0"))
        assertNull(SyncCode.parse(VECTOR_CODE.replaceRange(5, 6, "U")))
    }
}
