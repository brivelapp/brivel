package app.brivel.intervals

import app.brivel.engine.WorkoutEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64

/** Error de intervals.icu con su código HTTP (401 = clave o ID de atleta incorrectos). */
class IntervalsException(val status: Int, message: String) : Exception(message)

/** Aplicación OAuth de Brivel en intervals.icu (n.º 1731, aprobada el 2026-11-07): cliente público con PKCE, sin secreto. */
const val INTERVALS_CLIENT_ID = "1731"

/** ACTIVITY:WRITE para renombrar actividades; CALENDAR:WRITE para mandar sesiones; WRITE incluye READ. */
const val INTERVALS_SCOPES = "ACTIVITY:WRITE,WELLNESS:READ,CALENDAR:WRITE,SETTINGS:READ"

/** El código de la vuelta por el token, con el verificador PKCE. Devuelve (token, ID de atleta). */
suspend fun exchangeIntervalsCode(code: String, verifier: String, engine: HttpClientEngine? = null): Pair<String, String> {
    val http = if (engine != null) HttpClient(engine) else HttpClient()
    try {
        val response = http.submitForm("https://intervals.icu/api/oauth/token", parameters {
            append("client_id", INTERVALS_CLIENT_ID)
            append("code", code)
            append("code_verifier", verifier)
        })
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw IntervalsException(response.status.value, text.take(200))
        val o = Json.parseToJsonElement(text).jsonObject
        return o["access_token"].str()!! to o["athlete"]!!.jsonObject["id"].str()!!
    } finally {
        http.close()
    }
}

/**
 * Cliente de la API de intervals.icu, desde el propio dispositivo (docs/VISION.md §7): con el token de OAuth (`oauth`)
 * o, en las cuentas vinculadas antes, con la clave del usuario. `engine` solo para tests (MockEngine).
 */
class IntervalsClient(private val athleteId: String, apiKey: String, engine: HttpClientEngine? = null, oauth: Boolean = false) {
    private val http: HttpClient = (if (engine != null) HttpClient(engine) else HttpClient()).config {
        install(HttpTimeout) { requestTimeoutMillis = 20_000 }
        defaultRequest {
            // Clave: Basic Auth con usuario literal "API_KEY" y contraseña la clave del atleta
            header(HttpHeaders.Authorization,
                if (oauth) "Bearer $apiKey" else "Basic " + Base64.encode("API_KEY:$apiKey".encodeToByteArray()))
        }
    }
    private val json = Json { encodeDefaults = true }
    private val athleteUrl = "$BASE_URL/athlete/$athleteId"

    private suspend fun read(response: HttpResponse): JsonElement {
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw IntervalsException(response.status.value, text.take(200))
        return Json.parseToJsonElement(text)
    }

    private suspend fun list(path: String, vararg params: Pair<String, Any>): List<JsonObject> =
        read(http.get(athleteUrl + path) { params.forEach { (k, v) -> parameter(k, v.toString()) } })
            .jsonArray.map { it.jsonObject }

    suspend fun athlete(): JsonObject = read(http.get(athleteUrl)).jsonObject

    suspend fun wellness(oldest: LocalDate, newest: LocalDate) = list("/wellness", "oldest" to oldest, "newest" to newest)

    suspend fun activities(oldest: LocalDate, newest: LocalDate) = list("/activities", "oldest" to oldest, "newest" to newest)

    /** Objetivos = eventos de carrera A/B/C del calendario. */
    suspend fun races(oldest: LocalDate, newest: LocalDate) =
        list("/events", "oldest" to oldest, "newest" to newest, "category" to "RACE_A,RACE_B,RACE_C")

    suspend fun events(oldest: LocalDate, newest: LocalDate) = list("/events", "oldest" to oldest, "newest" to newest)

    /** Datos segundo a segundo de una actividad (endpoint fuera de /athlete/{id}). */
    suspend fun streams(activityId: String, types: String = "heartrate,velocity_smooth,grade_smooth"): Map<String, List<Double?>> =
        read(http.get("$BASE_URL/activity/$activityId/streams") { parameter("types", types) }).jsonArray
            .map { it.jsonObject }
            .associate { s -> s["type"].str()!! to ((s["data"] as? JsonArray)?.map { it.num() } ?: emptyList()) }

    suspend fun deleteEvent(eventId: Long) {
        read(http.delete("$athleteUrl/events/$eventId"))
    }

    /** Crea o actualiza (por external_id) entrenamientos del calendario; intervals.icu los pasa al reloj. */
    suspend fun upsertWorkouts(events: List<WorkoutEvent>): List<JsonObject> = read(http.post("$athleteUrl/events/bulk") {
        parameter("upsert", "true")
        parameter("upsertOnUid", "false")
        parameter("updatePlanApplied", "false")
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(events))
    }).jsonArray.map { it.jsonObject }

    /** Cambia el título de una actividad (p. ej. "Carrera · Tempo (Z3)" en vez del nombre automático del reloj). */
    suspend fun renameActivity(activityId: String, name: String) {
        read(http.put("$BASE_URL/activity/$activityId") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(mapOf("name" to name)))
        })
    }

    /** Retira el permiso de Brivel en intervals.icu (al desvincular con OAuth). */
    suspend fun disconnectApp() {
        val response = http.delete("$BASE_URL/disconnect-app")
        if (!response.status.isSuccess()) throw IntervalsException(response.status.value, response.bodyAsText().take(200))
    }

    fun close() = http.close()

    companion object {
        const val BASE_URL = "https://intervals.icu/api/v1"
    }
}
