package app.brivel.app

// Cliente del relevo de Garmin Connect (/api/garmin, Worker privado brivel-garmin; política de privacidad, apartado 5)
// para Android e iOS; la web tiene el suyo en web/src/garmin.ts. El móvil crea un par de claves X25519 y solo da al
// servidor la pública: cada envío de Garmin llega cifrado con ella y solo este móvil puede abrirlo.

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.XDH
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

/** Error del relevo de Garmin con su código HTTP y el texto que manda el servidor. */
class GarminException(val status: Int, message: String) : Exception(message)

@Serializable private class StartBody(val key: String, val back: String)
@Serializable private class Started(val url: String, val state: String)
@Serializable private class StateBody(val state: String)
@Serializable private class Claimed(val token: String)
@Serializable private class Item(val id: Long, val data: String)
@Serializable private class Inbox(val items: List<Item>, val more: Boolean)
@Serializable private class AckBody(val upTo: Long)

private val INFO = "brivel-garmin-1".encodeToByteArray()
private val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

/** Claves del dispositivo: la privada en bruto (32 bytes, en el almacén seguro) y la pública que se da al servidor. */
internal class GarminKeys(val privateKey: ByteArray, val publicKey: ByteArray)

internal suspend fun newGarminKeys(): GarminKeys {
    val pair = CryptographyProvider.Default.get(XDH).keyPairGenerator(XDH.Curve.X25519).generateKey()
    return GarminKeys(pair.privateKey.encodeToByteArray(XDH.PrivateKey.Format.RAW),
        pair.publicKey.encodeToByteArray(XDH.PublicKey.Format.RAW))
}

/** Abre un envío: 0x01 | clave efímera (32) | nonce (12) | cifrado y etiqueta (X25519, HKDF-SHA256, AES-256-GCM). */
internal suspend fun openGarmin(sealed: ByteArray, keys: GarminKeys): String {
    if (sealed.isEmpty() || sealed[0] != 1.toByte()) throw GarminException(0, "Este envío de Garmin es de una versión más nueva: actualiza Brivel")
    val provider = CryptographyProvider.Default
    val xdh = provider.get(XDH)
    val epk = sealed.copyOfRange(1, 33)
    val peer = xdh.publicKeyDecoder(XDH.Curve.X25519).decodeFromByteArray(XDH.PublicKey.Format.RAW, epk)
    val own = xdh.privateKeyDecoder(XDH.Curve.X25519).decodeFromByteArray(XDH.PrivateKey.Format.RAW, keys.privateKey)
    val shared = own.sharedSecretGenerator().generateSharedSecretToByteArray(peer)
    val aes = provider.get(HKDF).secretDerivation(SHA256, 32.bytes, salt = epk + keys.publicKey, info = INFO)
        .deriveSecretToByteArray(shared)
    val cipher = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, aes).cipher()
    return cipher.decrypt(sealed.copyOfRange(33, sealed.size)).decodeToString()  // nonce | cifrado | etiqueta
}

/** Las llamadas a /api/garmin. `engine`: solo tests. */
internal class GarminClient(private val base: String, engine: HttpClientEngine? = null) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = (if (engine != null) HttpClient(engine) else HttpClient()).config {
        install(HttpTimeout) { requestTimeoutMillis = 20_000 }
    }

    suspend fun start(publicKey: ByteArray): Pair<String, String> {
        val started = json.decodeFromString<Started>(ok(http.post("$base/start") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StartBody(b64.encode(publicKey), "app")))
        }).bodyAsText())
        return started.url to started.state
    }

    /** El token del buzón, o null si Garmin aún no ha devuelto al usuario (202). */
    suspend fun claim(state: String): String? {
        val response = http.post("$base/claim") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StateBody(state)))
        }
        if (response.status == HttpStatusCode.Accepted) return null
        return json.decodeFromString<Claimed>(ok(response).bodyAsText()).token
    }

    /** Una página del buzón: (id, envío cifrado) y si quedan más. */
    suspend fun inbox(token: String): Pair<List<Pair<Long, ByteArray>>, Boolean> {
        val inbox = json.decodeFromString<Inbox>(ok(http.get("$base/inbox") { auth(token) }).bodyAsText())
        return inbox.items.map { it.id to b64.decode(it.data) } to inbox.more
    }

    suspend fun ack(token: String, upTo: Long) {
        ok(http.post("$base/inbox/ack") {
            auth(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AckBody(upTo)))
        })
    }

    suspend fun disconnect(token: String) {
        val response = http.post("$base/disconnect") { auth(token) }
        if (response.status != HttpStatusCode.Unauthorized) ok(response)  // ya desconectado: nada que borrar
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth(token: String) = header(HttpHeaders.Authorization, "Bearer $token")

    private suspend fun ok(response: HttpResponse): HttpResponse {
        if (!response.status.isSuccess()) {
            val text = response.bodyAsText()
            val message = runCatching { Json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
            throw GarminException(response.status.value, message ?: "Garmin no responde: prueba más tarde")
        }
        return response
    }
}

/** Espera a que el servidor tenga el buzón tras volver de Garmin: hasta 10 intentos, uno por segundo. */
internal suspend fun GarminClient.claimWithRetry(state: String): String {
    repeat(10) {
        claim(state)?.let { return it }
        delay(1000)
    }
    throw GarminException(202, "Garmin tarda en responder: vuelve a intentarlo")
}

internal fun encodeKey(b: ByteArray) = b64.encode(b)
internal fun decodeKey(s: String) = b64.decode(s)
