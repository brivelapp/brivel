package app.brivel.app

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.XDH
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

/** Cifra como brivel-garmin/src/seal.ts: X25519 efímera, HKDF-SHA256 con las dos públicas de sal y AES-256-GCM. */
private suspend fun seal(recipient: ByteArray, plain: String): ByteArray {
    val p = CryptographyProvider.Default
    val xdh = p.get(XDH)
    val eph = xdh.keyPairGenerator(XDH.Curve.X25519).generateKey()
    val epk = eph.publicKey.encodeToByteArray(XDH.PublicKey.Format.RAW)
    val peer = xdh.publicKeyDecoder(XDH.Curve.X25519).decodeFromByteArray(XDH.PublicKey.Format.RAW, recipient)
    val shared = eph.privateKey.sharedSecretGenerator().generateSharedSecretToByteArray(peer)
    val key = p.get(HKDF).secretDerivation(SHA256, 32.bytes, salt = epk + recipient, info = "brivel-garmin-1".encodeToByteArray())
        .deriveSecretToByteArray(shared)
    val cipher = p.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key).cipher()
    return byteArrayOf(1) + epk + cipher.encrypt(plain.encodeToByteArray())
}

/** El relevo de Garmin en pequeño: /start guarda la clave pública, /claim da el token y el buzón entrega y borra. */
private class FakeRelay {
    var publicKey: ByteArray? = null
    var pendingClaims = 1  // un 202 antes del token, como mientras Garmin aún no ha vuelto
    val inbox = mutableListOf<Pair<Long, ByteArray>>()
    var disconnected = false

    val engine = MockEngine { request ->
        val path = request.url.encodedPath.removePrefix("/api/garmin")
        val authorized = request.headers[HttpHeaders.Authorization] == "Bearer buzon" && !disconnected
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        when (path) {
            "/start" -> {
                val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                publicKey = b64.decode(body["key"]!!.jsonPrimitive.content)
                respond("""{"url":"https://connect.garmin.com/oauth2Confirm?x=1","state":"st"}""", HttpStatusCode.OK, json)
            }
            "/claim" -> if (pendingClaims-- > 0) respond("", HttpStatusCode.Accepted) else respond("""{"token":"buzon"}""", HttpStatusCode.OK, json)
            "/inbox" -> if (!authorized) respond("""{"error":"Falta el token"}""", HttpStatusCode.Unauthorized, json)
                else respond("""{"items":[${inbox.joinToString { """{"id":${it.first},"data":"${b64.encode(it.second)}"}""" }}],"more":false}""", HttpStatusCode.OK, json)
            "/inbox/ack" -> {
                val upTo = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject["upTo"]!!.jsonPrimitive.content.toLong()
                inbox.removeAll { it.first <= upTo }
                respond("", HttpStatusCode.NoContent)
            }
            "/disconnect" -> respond("", HttpStatusCode.NoContent).also { disconnected = true; inbox.clear() }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }
}

class GarminRelayTest {
    @Test
    fun connectPullAndDisconnect() = runTest {
        val relay = FakeRelay()
        val app = BrivelApp(MemoryStore(), MemoryStore(), { LocalDate(2026, 11, 3) }, relay.engine)
        assertFalse(app.garminConnected)
        assertTrue(app.startGarmin().startsWith("https://connect.garmin.com/"))
        assertEquals(32, relay.publicKey!!.size)

        relay.inbox += 1L to seal(relay.publicKey!!, """{"v":1,"days":[{"date":"2026-11-03","hrv":47.0}]}""")
        relay.inbox += 2L to seal(relay.publicKey!!, """{"v":1,"workouts":[{"id":"garmin-77","sport":"running","start":"2026-11-03T08:00:00","minutes":45.0,"device":"Forerunner 965"}]}""")
        assertEquals(2, app.finishGarmin())
        assertTrue(app.garminConnected)
        assertEquals(47.0, app.data.garmin!!.days.single().hrv)
        assertEquals("Forerunner 965", app.data.garmin!!.workouts.single().device)
        assertTrue(relay.inbox.isEmpty())  // descargado: el servidor lo borra
        assertEquals(0, app.pullGarmin())

        // Otro dispositivo de la misma sesión recibe la conexión por la sincronización y abre el buzón
        val other = BrivelApp(MemoryStore(), MemoryStore(), { LocalDate(2026, 11, 3) }, relay.engine)
        val link = Json.decodeFromString<GarminLink>(app.garminLinkJson()!!)
        other.setGarminLink(link.privateKey, link.publicKey, link.token)
        relay.inbox += 3L to seal(relay.publicKey!!, """{"v":1,"days":[{"date":"2026-11-04","steps":5000.0}]}""")
        assertEquals(1, other.pullGarmin())
        assertEquals(5000.0, other.data.garmin!!.days.single().steps)
        assertFalse("garminLink\": {" in app.exportJson())  // el token y la clave no salen al exportar

        app.disconnectGarmin()
        assertFalse(app.garminConnected)
        assertEquals(401, assertFailsWith<GarminException> { other.pullGarmin() }.status)  // desconectado para todos
        assertFalse(other.garminConnected)
        assertEquals(1, app.data.garmin!!.workouts.size)  // lo descargado se queda
        assertEquals(0, app.pullGarmin())
    }

    @Test
    fun disconnectedFromGarminConnectForgetsTheMailbox() = runTest {
        val relay = FakeRelay().apply { pendingClaims = 0 }
        val app = BrivelApp(MemoryStore(), MemoryStore(), { LocalDate(2026, 11, 3) }, relay.engine)
        app.startGarmin()
        app.finishGarmin()
        relay.disconnected = true  // el usuario quitó el permiso en Garmin Connect
        assertEquals(401, assertFailsWith<GarminException> { app.pullGarmin() }.status)
        assertFalse(app.garminConnected)
    }
}
