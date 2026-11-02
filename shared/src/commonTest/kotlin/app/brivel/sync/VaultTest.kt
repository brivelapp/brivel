package app.brivel.sync

import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

class VaultTest {
    private val plain = """{"schema":2}""".encodeToByteArray()

    @Test
    fun keysMatchReference() = runTest {
        val keys = deriveKeys(SyncCode.fromSecret(VECTOR_SECRET))
        assertEquals(VECTOR_TOKEN, keys.token.toHexString())
        assertEquals(VECTOR_ACCOUNT, keys.account)
        assertEquals(43, keys.bearer.length)
        assertContentEquals(keys.token, Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(keys.bearer))
    }

    @Test
    fun opensReferenceBlock() = runTest {
        val keys = deriveKeys(SyncCode.fromSecret(VECTOR_SECRET))
        assertContentEquals(plain, open(keys, Base64.decode(VECTOR_BLOB), version = 7))
    }

    @Test
    fun sealThenOpen() = runTest {
        val keys = deriveKeys(SyncCode.generate())
        val blob = seal(keys, plain, version = 3)
        assertEquals(1 + 12 + 4096 + 16, blob.size)  // formato, nonce, datos rellenos y etiqueta
        assertContentEquals(plain, open(keys, blob, version = 3))
        assertNotEquals(blob.toList(), seal(keys, plain, version = 3).toList())  // nonce nuevo en cada subida
    }

    @Test
    fun rejectsTamperingOtherAccountsAndOtherVersions() = runTest {
        val keys = deriveKeys(SyncCode.generate())
        val blob = seal(keys, plain, version = 3)
        assertFails { open(keys, blob, version = 2) }  // el servidor devuelve uno viejo como nuevo
        assertFails { open(deriveKeys(SyncCode.generate()), blob, version = 3) }  // de otra cuenta
        assertFails { open(keys, blob.copyOf().also { it[40] = (it[40] + 1).toByte() }, version = 3) }
        assertFails { open(keys, blob.copyOf().also { it[0] = 2 }, version = 3) }  // formato desconocido
    }

    @Test
    fun paddingHidesSize() {
        assertEquals(4096, pad(ByteArray(0)).size)
        assertEquals(4096, pad(ByteArray(4095)).size)
        assertEquals(8192, pad(ByteArray(4096)).size)
        for (n in listOf(0, 1, 4095, 4096, 5000)) {
            val data = ByteArray(n) { 7 }
            assertContentEquals(data, unpad(pad(data)))
        }
    }
}
