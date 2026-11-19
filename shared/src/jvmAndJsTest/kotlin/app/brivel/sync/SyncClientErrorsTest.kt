package app.brivel.sync

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Lo que dice el cliente de sincronización cuando el servidor responde con un error. */
class SyncClientErrorsTest {
    private fun client(body: String, status: HttpStatusCode) =
        SyncClient("https://brivel.app/api/boveda", "token", MockEngine { respond(body, status) })

    @Test fun theServersOwnMessageIsKept() = runTest {
        val e = assertFailsWith<SyncException> { client("""{"error":"Demasiadas subidas"}""", HttpStatusCode.TooManyRequests).download() }
        assertEquals(429 to "Demasiadas subidas", e.status to e.message)
    }

    @Test fun aProxyPageIsCutAndAJsonWithoutErrorFallsBackToTheText() = runTest {
        val page = "<html>" + "x".repeat(400)
        val e = assertFailsWith<SyncException> { client(page, HttpStatusCode.BadGateway).download() }
        assertEquals(502 to 200, e.status to e.message!!.length)
        assertEquals("""{"otro":1}""", assertFailsWith<SyncException> { client("""{"otro":1}""", HttpStatusCode.InternalServerError).download() }.message)
    }

    @Test fun aSuccessfulWriteWithoutVersionIsAnError() = runTest {
        val e = assertFailsWith<SyncException> { client("", HttpStatusCode.OK).create(byteArrayOf(1)) }
        assertTrue("versión" in e.message!!)
    }
}
