@file:OptIn(ExperimentalJsExport::class)

package io.github.mrclusterman.trainsync.web

// Llaves de la sincronización en la web (docs/SINCRONIZACION.md, auditoría 1): la clave AES es una CryptoKey no
// exportable que web/src/sync.ts guarda en IndexedDB. Un XSS podría usarla con la página abierta, pero no llevársela, y
// el código no se guarda: se enseña una vez al activar.

import dev.whyoleg.cryptography.random.CryptographyRandom
import io.github.mrclusterman.trainsync.sync.Aead
import io.github.mrclusterman.trainsync.sync.KEY_LABEL
import io.github.mrclusterman.trainsync.sync.Remote
import io.github.mrclusterman.trainsync.sync.SyncException
import io.github.mrclusterman.trainsync.sync.SyncKeys
import io.github.mrclusterman.trainsync.sync.SyncTransport
import io.github.mrclusterman.trainsync.sync.TOKEN_LABEL
import io.github.mrclusterman.trainsync.sync.accountOf
import kotlinx.browser.window
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.io.encoding.Base64
import kotlin.js.Promise
import kotlin.js.json

private val subtle: dynamic get() = js("globalThis.crypto.subtle")
private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

/** Lo que guarda la web: la clave (CryptoKey no exportable), el token en base64url y la cuenta. */
@JsExport
class WebSyncKeys(val key: dynamic, val bearer: String, val account: String)

// Promesas con la biblioteca estándar: kotlinx.coroutines añadiría peso a la web sin hacer falta
private suspend fun <T> Promise<T>.await(): T = suspendCoroutine { c -> then({ c.resume(it) }, { c.resumeWithException(it) }) }

private suspend fun call(promise: dynamic): dynamic = promise.unsafeCast<Promise<dynamic>>().await()

/** Lo que exporta WebApp a TypeScript: una función suspend como Promise. */
internal fun <T> promise(block: suspend () -> T): Promise<T> = Promise { resolve, reject ->
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result -> result.fold(resolve, reject) })
}

// En JS un ByteArray es un Int8Array: WebCrypto lo acepta tal cual
private fun bytes(buffer: dynamic): ByteArray = Int8Array(buffer.unsafeCast<ArrayBuffer>()).unsafeCast<ByteArray>()

/** Las mismas derivaciones que deriveKeys (Vault.kt), pero con la clave AES no exportable. */
internal suspend fun webKeys(secret: ByteArray): WebSyncKeys {
    val base = call(subtle.importKey("raw", secret, "HKDF", false, arrayOf("deriveKey", "deriveBits")))
    fun hkdf(label: String) = json("name" to "HKDF", "hash" to "SHA-256", "salt" to ByteArray(32), "info" to label.encodeToByteArray())
    val key = call(subtle.deriveKey(hkdf(KEY_LABEL), base, json("name" to "AES-GCM", "length" to 256), false, arrayOf("encrypt", "decrypt")))
    val token = bytes(call(subtle.deriveBits(hkdf(TOKEN_LABEL), base, 256)))
    return WebSyncKeys(key, base64Url.encode(token), accountOf(token))
}

internal fun WebSyncKeys.toSyncKeys() = SyncKeys(WebAead(key), base64Url.decode(bearer), account)

/** AES-GCM con WebCrypto; mismo formato que la librería: nonce de 12 bytes delante y etiqueta de 16 al final. */
private class WebAead(private val key: dynamic) : Aead {
    override suspend fun encrypt(plain: ByteArray, associatedData: ByteArray): ByteArray {
        val iv = CryptographyRandom.Default.nextBytes(12)
        return iv + bytes(call(subtle.encrypt(json("name" to "AES-GCM", "iv" to iv, "additionalData" to associatedData), key, plain)))
    }

    override suspend fun decrypt(sealed: ByteArray, associatedData: ByteArray): ByteArray {
        val params = json("name" to "AES-GCM", "iv" to sealed.copyOfRange(0, 12), "additionalData" to associatedData)
        return bytes(call(subtle.decrypt(params, key, sealed.copyOfRange(12, sealed.size))))
    }
}

/** /api/boveda con fetch, mismas respuestas que SyncClient (ktor) en las apps. */
internal class WebTransport(private val url: String, private val bearer: String) : SyncTransport {
    private suspend fun send(method: String, body: ByteArray? = null, precondition: Pair<String, String>? = null): Response {
        val headers = json("Authorization" to "Bearer $bearer")
        if (precondition != null) headers[precondition.first] = precondition.second
        if (body != null) headers["Content-Type"] = "application/octet-stream"
        // Objeto plano: el RequestInit de Kotlin manda credentials: null y el navegador lo rechaza
        val init = json("method" to method, "headers" to headers, "cache" to "no-store")
        if (body != null) init["body"] = body
        val response = window.fetch(url, init.unsafeCast<RequestInit>()).await()
        if (response.ok || response.status.toInt() == 404 || response.status.toInt() == 409) return response
        val text = response.text().await()
        val message = runCatching { JSON.parse<dynamic>(text).error as? String }.getOrNull()
        throw SyncException(response.status.toInt(), message ?: text.take(200))
    }

    private fun version(response: Response): Long = response.headers.get("ETag")?.trim('"')?.toLongOrNull()
        ?: throw SyncException(response.status.toInt(), "El servidor no mandó la versión")

    override suspend fun download(): Remote? {
        val response = send("GET")
        if (response.status.toInt() == 404) return null
        return Remote(bytes(response.arrayBuffer().await()), version(response))
    }

    override suspend fun create(blob: ByteArray) = upload(blob, "If-None-Match" to "*")

    override suspend fun update(blob: ByteArray, version: Long) = upload(blob, "If-Match" to "\"$version\"")

    private suspend fun upload(blob: ByteArray, precondition: Pair<String, String>): Long? {
        val response = send("PUT", blob, precondition)
        return if (response.status.toInt() == 409) null else version(response)
    }

    override suspend fun delete() {
        send("DELETE")
    }
}
