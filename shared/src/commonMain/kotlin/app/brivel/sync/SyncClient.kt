package app.brivel.sync

// Cliente de /api/boveda (server/src/index.ts). Solo mueve bloques cifrados: el cifrado va en Vault.kt.

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Error del servidor de sincronización con su código HTTP y el texto que manda. */
class SyncException(val status: Int, message: String) : Exception(message)

/** La copia del servidor: el bloque cifrado y su versión. */
class Remote(val blob: ByteArray, val version: Long)

/** Las cuatro operaciones de /api/boveda. En la web van con fetch (web/WebSync.kt): ktor añadiría ~500 kB. */
interface SyncTransport {
    /** Null si no hay copia: el código cambió o se borró. */
    suspend fun download(): Remote?

    /** Crea la copia como versión 1. Null si ya existe una (otro dispositivo se adelantó). */
    suspend fun create(blob: ByteArray): Long?

    /** Guarda sobre `version`. Null si ya no es la última: otro dispositivo subió antes. */
    suspend fun update(blob: ByteArray, version: Long): Long?

    suspend fun delete()
}

/** `url`: https://brivel.app/api/boveda. `engine`: solo tests. */
class SyncClient(private val url: String, private val bearer: String, engine: HttpClientEngine? = null) : SyncTransport {
    private val http: HttpClient = (if (engine != null) HttpClient(engine) else HttpClient()).config {
        install(HttpTimeout) { requestTimeoutMillis = 20_000 }
    }

    private fun HttpRequestBuilder.auth() = header(HttpHeaders.Authorization, "Bearer $bearer")

    override suspend fun download(): Remote? {
        val response = http.get(url) { auth() }
        if (response.status == HttpStatusCode.NotFound) return null
        return Remote(ok(response).bodyAsBytes(), version(response))
    }

    override suspend fun create(blob: ByteArray): Long? = upload(blob, HttpHeaders.IfNoneMatch to "*")

    override suspend fun update(blob: ByteArray, version: Long): Long? = upload(blob, HttpHeaders.IfMatch to "\"$version\"")

    override suspend fun delete() {
        ok(http.delete(url) { auth() })
    }

    private suspend fun upload(blob: ByteArray, precondition: Pair<String, String>): Long? {
        val response = http.put(url) {
            auth()
            header(precondition.first, precondition.second)
            contentType(ContentType.Application.OctetStream)
            setBody(blob)
        }
        if (response.status == HttpStatusCode.Conflict) return null
        return version(ok(response))
    }

    private suspend fun ok(response: HttpResponse): HttpResponse {
        if (!response.status.isSuccess()) {
            // El servidor manda {"error": "…"} en español; si no (un proxy, una caída), el principio del texto
            val text = response.bodyAsText()
            val message = runCatching { Json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
            throw SyncException(response.status.value, message ?: text.take(200))
        }
        return response
    }

    private fun version(response: HttpResponse): Long =
        response.headers[HttpHeaders.ETag]?.trim('"')?.toLongOrNull()
            ?: throw SyncException(response.status.value, "El servidor no mandó la versión")
}
