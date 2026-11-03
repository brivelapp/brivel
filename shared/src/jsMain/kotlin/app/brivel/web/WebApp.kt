@file:OptIn(ExperimentalJsExport::class)

package app.brivel.web

// Puente de la versión web: el mismo controlador que las apps, con los datos en el navegador. Las pantallas salen en
// JSON para que la interfaz (web/) reciba objetos planos de JavaScript.

import app.brivel.app.EVIDENCE_MEANING
import app.brivel.app.GoalKind
import app.brivel.app.HEALTH_NOTICE
import app.brivel.app.KeyValueStore
import app.brivel.app.SPORT_LABEL
import app.brivel.app.BrivelApp
import app.brivel.app.UserProfile
import app.brivel.app.MAX_DAY_MINUTES
import app.brivel.app.MIN_AGE
import app.brivel.app.PICKABLE_CARDIO
import app.brivel.app.RaceInfo
import app.brivel.app.weekTimeText
import app.brivel.app.ageAllowed as ageAllowedCore
import app.brivel.app.goalLabel as labelOf
import app.brivel.app.toggleCardio as toggleCardioCore
import app.brivel.app.dateLabel as dateLabelCore
import app.brivel.app.conflictMessage
import app.brivel.app.FIGURES
import app.brivel.app.polylines
import app.brivel.sync.SyncCode
import app.brivel.sync.Syncer
import kotlinx.browser.window
import kotlin.js.Promise
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
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

/** Figura de un patrón como polilíneas: lo que dibuja la web en SVG (Figures.kt). */
internal fun figureLinesJson(pattern: String): String? = FIGURES[pattern]?.let { f ->
    fun lines(d: String) = JsonArray(polylines(d).map { l -> JsonArray(l.flatMap { (x, y) -> listOf(JsonPrimitive(x), JsonPrimitive(y)) }) })
    buildJsonObject {
        put("head", JsonArray(listOf(JsonPrimitive(f.headX), JsonPrimitive(f.headY))))
        put("body", lines(f.body))
        put("work", lines(f.work))
    }.toString()
}

@JsExport
class WebApp {
    // La clave de intervals.icu no se queda en el navegador: sessionStorage se borra al cerrar la pestaña. Cifrarla no
    // serviría (la llave estaría en el mismo navegador); contra scripts ajenos protege la CSP de web/vite.config.ts
    private val local = WebStore(localStorage)
    private val app = BrivelApp(local, WebStore(sessionStorage), ::browserToday)
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

    // Calendario y Progreso, como en las apps
    fun monthJson(year: Int, month: Int): String = json.encodeToString(app.month(year, month))
    fun dayJson(date: String): String = json.encodeToString(app.day(date))
    fun progressJson(): String = json.encodeToString(app.progress())
    fun dateLabel(date: String): String = dateLabelCore(date)

    // ------------------------------------------------------------ bienvenida y Perfil (en JSON, como las pantallas)

    private val read = Json { ignoreUnknownKeys = true }

    val hasProfile: Boolean get() = app.data.profile != null

    /** El perfil guardado o, en la bienvenida, el de partida. */
    fun profileJson(): String = json.encodeToString(app.data.profile ?: UserProfile())

    fun saveProfileJson(text: String) = app.saveProfile(read.decodeFromString<UserProfile>(text))

    /** `kind`: RACE, FITNESS, STRENGTH o HEALTH; `raceJson`, solo para una carrera. Los errores llegan con su texto. */
    fun setGoal(kind: String, raceJson: String?) =
        app.setGoal(GoalKind.valueOf(kind), raceJson?.let { read.decodeFromString<RaceInfo>(it) })

    val goalLabel: String? get() = app.data.goal?.let { labelOf(it) }

    fun abandonGoal() = app.abandonGoal()

    // Las mismas reglas que las apps (Texts.kt)
    fun toggleCardio(cardio: Array<String>, sport: String): Array<String> = toggleCardioCore(cardio.toList(), sport).toTypedArray()
    fun weekTime(daysJson: String): String = weekTimeText(read.decodeFromString<Map<String, Int>>(daysJson))
    fun ageOk(year: Int): Boolean = ageAllowedCore(year, browserToday().year)
    val pickableCardio: Array<String> get() = PICKABLE_CARDIO.toTypedArray()
    val maxDayMinutes: Int get() = MAX_DAY_MINUTES
    val minAge: Int get() = MIN_AGE

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

    /** La sesión de fuerza de ese día serie a serie (StrengthScreen), o null si ese día no tiene fuerza. */
    fun strengthJson(date: String): String? = app.strengthScreen(date)?.let { json.encodeToString(it) }
    fun lastStrengthDate(): String? = app.lastStrengthDate()
    fun figureJson(pattern: String): String? = figureLinesJson(pattern)

    /** Apunta o corrige una serie; `weight` vacío: con tu peso corporal. */
    fun logSet(date: String, exercise: String, set: Int, weight: String, reps: Int) =
        app.logSet(date, exercise, set, weight.replace(',', '.').toDoubleOrNull(), reps)
}

/** El código, para enseñarlo una sola vez, y las llaves, para guardarlas en IndexedDB. */
@JsExport
class WebSyncStart(val code: String, val keys: WebSyncKeys)
