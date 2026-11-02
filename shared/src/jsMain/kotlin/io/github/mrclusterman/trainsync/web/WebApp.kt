@file:OptIn(ExperimentalJsExport::class)

package io.github.mrclusterman.trainsync.web

// Puente de la versión web: el mismo controlador que las apps, con los datos en el navegador. Las pantallas salen en
// JSON para que la interfaz (web/) reciba objetos planos de JavaScript.

import io.github.mrclusterman.trainsync.app.EVIDENCE_MEANING
import io.github.mrclusterman.trainsync.app.GoalKind
import io.github.mrclusterman.trainsync.app.HEALTH_NOTICE
import io.github.mrclusterman.trainsync.app.KeyValueStore
import io.github.mrclusterman.trainsync.app.SPORT_LABEL
import io.github.mrclusterman.trainsync.app.TrainSyncApp
import io.github.mrclusterman.trainsync.app.UserProfile
import io.github.mrclusterman.trainsync.app.kg
import io.github.mrclusterman.trainsync.app.conflictMessage
import io.github.mrclusterman.trainsync.app.repRange
import io.github.mrclusterman.trainsync.sync.SyncCode
import io.github.mrclusterman.trainsync.sync.Syncer
import kotlinx.browser.window
import kotlin.js.Promise
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.js.Date
import org.w3c.dom.Storage

private class WebStore(private val storage: Storage) : KeyValueStore {
    override fun read(key: String) = storage.getItem(key)
    override fun write(key: String, value: String?) = if (value == null) storage.removeItem(key) else storage.setItem(key, value)
}

// La fecha local del navegador; TimeZone.currentSystemDefault() en JS necesita la base de zonas horarias aparte
private fun browserToday() = Date().let { LocalDate(it.getFullYear(), it.getMonth() + 1, it.getDate()) }

internal fun liftJson(app: TrainSyncApp, line: String): String? {
    if (repRange(line) == null) return null
    val hint = app.liftHint(line)
    return buildJsonObject {
        put("exercise", hint.exercise)
        put("last", hint.last?.let { "La última vez: ${it.weightKg?.let(::kg) ?: "tu peso"} × ${it.reps} (${it.sets} series)" })
        put("suggestion", hint.suggestion)
        put("weight", hint.last?.weightKg?.let { kg(it).removeSuffix(" kg") } ?: "")
    }.toString()
}

@JsExport
class WebApp {
    // La clave de intervals.icu no se queda en el navegador: sessionStorage se borra al cerrar la pestaña. Cifrarla no
    // serviría (la llave estaría en el mismo navegador); contra scripts ajenos protege la CSP de web/vite.config.ts
    private val local = WebStore(localStorage)
    private val app = TrainSyncApp(local, WebStore(sessionStorage), ::browserToday)
    private val json = Json { encodeDefaults = true }

    val needsOnboarding: Boolean get() = app.needsOnboarding

    val healthNotice: String get() = HEALTH_NOTICE

    /** Como "Borrar todo de este móvil" en Perfil de las apps; deja de sincronizar (la web borra sus llaves). */
    fun deleteAll() {
        app.deleteAll()
        syncer = null
    }

    // ------------------------------------------------------------ sincronización (docs/SINCRONIZACION.md)

    private var syncer: Syncer? = null

    private fun syncerFor(keys: WebSyncKeys) =
        Syncer(app, local, keys.toSyncKeys(), WebTransport(window.location.origin + "/api/boveda", keys.bearer))

    // Activada = hay llaves. Si el estado local se perdió, la próxima sincronización se junta como la primera vez
    val syncActive: Boolean get() = syncer != null

    /** Al abrir la web: las llaves guardadas en IndexedDB (web/src/sync.ts). */
    fun useSync(keys: WebSyncKeys) {
        syncer = syncerFor(keys)
    }

    /** Código nuevo: sube los datos de aquí y devuelve el código (se enseña una vez) y las llaves (las guarda la web). */
    fun createSync(): Promise<WebSyncStart> = promise {
        val code = SyncCode.generate()
        val keys = webKeys(code.secret)
        syncerFor(keys).also { it.sync(create = true); syncer = it }
        WebSyncStart(code.text, keys)
    }

    /** Unirse con el código de otro dispositivo: junta los datos de aquí con los de la copia. */
    fun joinSync(text: String): Promise<WebSyncKeys> = promise {
        val code = SyncCode.parse(text) ?: throw IllegalArgumentException("El código no es válido: revisa las letras")
        val keys = webKeys(code.secret)
        syncerFor(keys).also { it.sync(); syncer = it }
        keys
    }

    /** Devuelve el aviso si los dos dispositivos cambiaron lo mismo; null si no. */
    fun syncNow(): Promise<String?> = promise {
        conflictMessage(checkNotNull(syncer) { "La sincronización no está activada" }.sync().conflicts)
    }

    /** Deja de sincronizar en este navegador; la copia del servidor se queda. */
    fun forgetSync() {
        syncer?.forget()
        syncer = null
    }

    /** Borra la copia del servidor (RGPD art. 17); los datos de este navegador se quedan. */
    fun deleteSync(): Promise<Unit> = promise {
        checkNotNull(syncer) { "La sincronización no está activada" }.deleteRemote()
        syncer = null
    }

    fun todayJson(): String = json.encodeToString(app.todayScreen())

    /** Datos de prueba: el perfil de los tests de la app y un objetivo de forma física. */
    fun startDemo() {
        app.saveProfile(UserProfile(birthYear = 1990, level = "intermediate", strength = "gym",
            cardio = listOf("running", "cycling"), days = mapOf("MON" to 60, "TUE" to 60, "THU" to 75, "SAT" to 90, "SUN" to 60)))
        app.setGoal(GoalKind.FITNESS)
    }

    fun sportLabel(sport: String): String = SPORT_LABEL[sport] ?: sport

    fun evidenceMeaning(tag: String): String = "${EVIDENCE_MEANING[tag]} Fuentes en la documentación del motor."

    // Acciones de Hoy, las mismas que en DayContent de Android; los errores llegan a la web como excepciones con su texto
    fun markDone(date: String, sport: String) = app.markDone(date, sport)
    fun unmarkDone(date: String, sport: String) = app.unmarkDone(date, sport)
    fun answerReadiness(sleep: Int, energy: Int, soreness: Int) = app.answerReadiness(sleep, energy, soreness)
    fun chooseAlternative(date: String, plannedSport: String, chosenSport: String) = app.chooseAlternative(date, plannedSport, chosenSport)
    fun clearChoice(date: String) = app.clearChoice(date)
    fun canChangeDay(date: String): Boolean = app.canChangeDay(date)
    fun preferTrain(date: String) = app.changeDay(date, app.minutesToTrain(app.day(date)))
    fun cannotTrain(date: String) = app.changeDay(date, 0)

    /** Un ejercicio con repeticiones: lo de la última vez y la sugerencia de hoy; null si va por tiempo (planchas). */
    fun liftJson(line: String): String? = liftJson(app, line)

    /** Como LiftRows de Android: las series salen de la línea del plan ("3 × 8–12"). */
    fun logLift(date: String, line: String, weight: String, reps: String) {
        val sets = Regex("""(\d+) ×""").find(line)?.groupValues?.get(1)?.toInt() ?: 1
        app.logLift(date, line, weight.replace(',', '.').toDoubleOrNull(), reps.toIntOrNull() ?: 0, sets)
    }
}

/** El código, para enseñarlo una sola vez, y las llaves, para guardarlas en IndexedDB. */
@JsExport
class WebSyncStart(val code: String, val keys: WebSyncKeys)
