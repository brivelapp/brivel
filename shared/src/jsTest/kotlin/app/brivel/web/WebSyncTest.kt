package app.brivel.web

import app.brivel.sync.SyncCode
import app.brivel.sync.SyncException
import app.brivel.sync.VECTOR_ACCOUNT
import app.brivel.sync.VECTOR_BLOB
import app.brivel.sync.VECTOR_SECRET
import app.brivel.sync.VECTOR_TOKEN
import app.brivel.sync.deriveKeys
import app.brivel.sync.open
import app.brivel.sync.seal
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

/** La clave no exportable de la web (WebSync.kt) frente a la librería y a los vectores hechos con node:crypto. */
class WebSyncTest {
    private val plain = """{"schema":2}""".encodeToByteArray()

    @Test
    fun sameTokenAccountAndFormatAsTheLibrary() = runTest {
        val web = webKeys(VECTOR_SECRET)
        assertEquals(VECTOR_ACCOUNT, web.account)
        assertEquals(VECTOR_TOKEN, web.toSyncKeys().token.toHexString())
        assertContentEquals(plain, open(web.toSyncKeys(), Base64.decode(VECTOR_BLOB), version = 7))
        val library = deriveKeys(SyncCode.fromSecret(VECTOR_SECRET))
        assertContentEquals(plain, open(library, seal(web.toSyncKeys(), plain, version = 3), version = 3))
        assertContentEquals(plain, open(web.toSyncKeys(), seal(library, plain, version = 3), version = 3))
    }

    @Test
    fun keyCannotBeExported() = runTest {
        val key = webKeys(VECTOR_SECRET).key
        assertEquals(false, key.extractable)
        assertFails { js("globalThis.crypto.subtle").exportKey("raw", key).unsafeCast<Promise<Any>>().await() }
    }

    @Test
    fun hungServerTimesOutWithAMessage() = runTest {
        // Un servidor que no contesta nunca: solo la señal de tiempo corta la espera
        val real = js("globalThis.fetch")
        js("globalThis.fetch = function (u, init) { return new Promise(function (_, reject) { init.signal.addEventListener('abort', function () { reject(init.signal.reason) }) }) }")
        try {
            val e = assertFailsWith<SyncException> { WebTransport("https://brivel.app/api/boveda", "x", timeoutMillis = 10).download() }
            assertEquals(0, e.status)
        } finally {
            js("globalThis").fetch = real
        }
    }
}
