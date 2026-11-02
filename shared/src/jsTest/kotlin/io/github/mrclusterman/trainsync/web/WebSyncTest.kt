package io.github.mrclusterman.trainsync.web

import io.github.mrclusterman.trainsync.sync.SyncCode
import io.github.mrclusterman.trainsync.sync.VECTOR_ACCOUNT
import io.github.mrclusterman.trainsync.sync.VECTOR_BLOB
import io.github.mrclusterman.trainsync.sync.VECTOR_SECRET
import io.github.mrclusterman.trainsync.sync.VECTOR_TOKEN
import io.github.mrclusterman.trainsync.sync.deriveKeys
import io.github.mrclusterman.trainsync.sync.open
import io.github.mrclusterman.trainsync.sync.seal
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

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
}
