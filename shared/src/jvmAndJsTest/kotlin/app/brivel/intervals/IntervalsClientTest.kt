package app.brivel.intervals

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Las llamadas de IntervalsClient a la API: ruta, parámetros, autenticación y errores. */
class IntervalsClientTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val seen = mutableListOf<HttpRequestData>()

    private fun client(oauth: Boolean = false, reply: (HttpRequestData) -> Pair<String, HttpStatusCode> = { "[]" to HttpStatusCode.OK }) =
        IntervalsClient("i123", "secreto", MockEngine { request ->
            seen += request
            val (body, status) = reply(request)
            respond(body, status, json)
        }, oauth)

    private val from = LocalDate(2026, 11, 1)
    private val to = LocalDate(2026, 11, 14)

    @Test fun eachListGoesToItsOwnPathWithTheDateRange() = runTest {
        val c = client()
        c.wellness(from, to); c.activities(from, to); c.events(from, to); c.races(from, to)
        assertEquals(listOf("wellness", "activities", "events", "events"), seen.map { it.url.encodedPath.substringAfterLast('/') })
        assertTrue(seen.all { "/athlete/i123/" in it.url.encodedPath })
        seen.forEach { assertEquals("2026-11-01" to "2026-11-14", it.url.parameters["oldest"] to it.url.parameters["newest"]) }
        assertNull(seen[2].url.parameters["category"])  // todos los eventos
        assertEquals("RACE_A,RACE_B,RACE_C", seen[3].url.parameters["category"])  // solo las carreras
    }

    @Test fun anOldKeyGoesAsBasicAndAnOauthTokenAsBearer() = runTest {
        client().athlete()
        client(oauth = true).athlete()
        assertEquals("Basic QVBJX0tFWTpzZWNyZXRv", seen[0].headers[HttpHeaders.Authorization])  // «API_KEY:secreto» en base64
        assertEquals("Bearer secreto", seen[1].headers[HttpHeaders.Authorization])
    }

    @Test fun streamsComeByTypeAndMissingOrNullSamplesAreKept() = runTest {
        val body = """[{"type":"heartrate","data":[120,null,130]},{"type":"velocity_smooth"},{"type":"grade_smooth","data":[]}]"""
        val streams = client { body to HttpStatusCode.OK }.streams("a9", "heartrate,velocity_smooth,grade_smooth")
        assertEquals(mapOf("heartrate" to listOf(120.0, null, 130.0), "velocity_smooth" to emptyList(), "grade_smooth" to emptyList()), streams)
        assertEquals("/api/v1/activity/a9/streams", seen.single().url.encodedPath)  // fuera de /athlete/{id}
        assertEquals("heartrate,velocity_smooth,grade_smooth", seen.single().url.parameters["types"])
    }

    @Test fun deletingAnEventUsesDeleteOnItsId() = runTest {
        client { "{}" to HttpStatusCode.OK }.deleteEvent(77L)
        assertEquals(HttpMethod.Delete to "/api/v1/athlete/i123/events/77", seen.single().method to seen.single().url.encodedPath)
    }

    @Test fun anErrorStatusBecomesAnExceptionWithItsCodeAndTheStartOfTheText() = runTest {
        val long = "x".repeat(500)
        val e = assertFailsWith<IntervalsException> { client { long to HttpStatusCode.Unauthorized }.wellness(from, to) }
        assertEquals(401, e.status)
        assertEquals(200, e.message!!.length)  // el principio, no una página de error entera
        assertEquals(404, assertFailsWith<IntervalsException> { client { "no" to HttpStatusCode.NotFound }.deleteEvent(1) }.status)
    }
}
