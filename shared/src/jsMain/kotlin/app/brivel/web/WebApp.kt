@file:OptIn(ExperimentalJsExport::class)

package app.brivel.web

// Puente de la versión web: el mismo controlador que las apps, con los datos en el navegador. Las pantallas salen en
// JSON para que la interfaz (web/) reciba objetos planos de JavaScript.

import app.brivel.app.TEST_FIELDS
import app.brivel.app.EFFORTS
import app.brivel.app.EFFORT_LABEL
import app.brivel.app.EVIDENCE_MEANING
import app.brivel.app.GoalKind
import app.brivel.app.HEALTH_NOTICE
import app.brivel.app.CustomExercise
import app.brivel.app.Routine
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
import app.brivel.app.ZONES
import app.brivel.app.polylines
import app.brivel.app.EQUIPMENT
import app.brivel.app.PATTERN_LABEL
import app.brivel.app.MEASURES
import app.brivel.app.OWN_SPORTS
import app.brivel.app.OWN_ZONES
import app.brivel.app.OwnSession
import app.brivel.app.SET_KINDS
import app.brivel.app.GEAR_LABEL
import app.brivel.app.SYNC_POLICY_CHANGED
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
    override fun write(key: String, value: String?) {
        if (value == null) return storage.removeItem(key)
        try {
            storage.setItem(key, value)
        } catch (e: dynamic) {
            // El navegador da unos 5 MB: si se llena, las gráficas de una actividad se quedan sin guardar; lo demás sí debe fallar
            if (!key.startsWith("brivel.activity.")) throw e
        }
    }
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
    private val app = BrivelApp(local, WebStore(sessionStorage), ::browserToday).apply {
        // La hora local de una actividad, con el desfase que da el navegador para ese momento
        utcOffsetSeconds = { epoch -> -Date(epoch * 1000.0).getTimezoneOffset() * 60 }
    }
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

    /** Activada con una política anterior a la vigente: en pausa hasta que se acepte la nueva (política, apartado 13). */
    val syncNeedsConsent: Boolean get() = syncer != null && !app.syncConsentCurrent
    val syncPolicyChanged: String get() = SYNC_POLICY_CHANGED
    fun acceptSyncPolicy() = app.recordSyncConsent()

    /** Al abrir la web: las llaves guardadas en IndexedDB (web/src/sync.ts). */
    fun useSync(keys: WebSyncKeys) {
        syncer = syncerFor(keys)
    }

    /** Código nuevo: sube los datos de aquí y devuelve el código (se enseña una vez) y las llaves (las guarda la web). */
    fun createSync(): Promise<WebSyncStart> = promise {
        val code = SyncCode.generate()
        val keys = webKeys(code.secret)
        syncerFor(keys).also { it.sync(create = true); syncer = it }
        app.recordSyncConsent()
        WebSyncStart(code.text, keys)
    }

    /** Unirse con el código de otro dispositivo: junta los datos de aquí con los de la copia. */
    fun joinSync(text: String): Promise<WebSyncKeys> = promise {
        val code = SyncCode.parse(text) ?: throw IllegalArgumentException("El código no es válido: revisa las letras")
        val keys = webKeys(code.secret)
        syncerFor(keys).also { it.sync(); syncer = it }
        app.recordSyncConsent()
        keys
    }

    /** Devuelve el aviso si los dos dispositivos cambiaron lo mismo; null si no. */
    fun retireFromWatch(): Promise<Unit> = promise { app.retireFromWatch() }
    fun syncNow(): Promise<String?> = promise {
        if (syncNeedsConsent) return@promise null  // en pausa: la tarjeta pide aceptar la política nueva
        conflictMessage(checkNotNull(syncer) { "La sincronización no está activada" }.sync().conflicts)
    }

    /** Deja de sincronizar en este navegador; la copia del servidor se queda. */
    fun forgetSync() {
        syncer?.forget()
        syncer = null
        app.clearSyncConsent()
    }

    /** Borra la copia del servidor (RGPD art. 17); los datos de este navegador se quedan. */
    fun deleteSync(): Promise<Unit> = promise {
        checkNotNull(syncer) { "La sincronización no está activada" }.deleteRemote()
        syncer = null
    }

    fun todayJson(): String = json.encodeToString(app.todayScreen())
    // Widgets de Hoy (Widgets.kt): los de este navegador
    fun todayWidgetsJson(): String = json.encodeToString(app.todayWidgets())
    fun widgetChoicesJson(): String = json.encodeToString(app.widgetChoices())
    fun addWidget(key: String, wide: Boolean) = app.addWidget(key, wide)
    fun removeWidget(key: String) = app.removeWidget(key)
    fun setWidgetWide(key: String, wide: Boolean) = app.setWidgetWide(key, wide)
    fun moveWidget(key: String, to: Int) = app.moveWidget(key, to)
    // Los cuatro de arriba en Progreso
    fun progressTilesJson(): String = json.encodeToString(app.progressTiles())
    fun tileChoicesJson(): String = json.encodeToString(app.tileChoices())
    fun setProgressTiles(keysJson: String) = app.setProgressTiles(Json.decodeFromString<List<String>>(keysJson))

    // Calendario y Progreso, como en las apps
    fun monthJson(year: Int, month: Int): String = json.encodeToString(app.month(year, month))
    fun dayJson(date: String): String = json.encodeToString(app.day(date))
    fun progressJson(): String = json.encodeToString(app.progress())
    fun planOverviewJson(): String? = app.planOverview()?.let { json.encodeToString(it) }
    fun dateLabel(date: String): String = dateLabelCore(date)

    // ------------------------------------------------------------ bienvenida y Perfil (en JSON, como las pantallas)

    private val read = Json { ignoreUnknownKeys = true }

    val hasProfile: Boolean get() = app.hasPlanProfile
    val noPlan: Boolean get() = app.data.goal?.kind == GoalKind.OWN
    fun startWithoutPlan(birthYear: Int?) = app.startWithoutPlan(birthYear)

    /** El perfil guardado o, en la bienvenida, el de partida. */
    fun profileJson(): String = json.encodeToString(app.data.profile ?: UserProfile())

    fun saveProfileJson(text: String) = app.saveProfile(read.decodeFromString<UserProfile>(text))

    /** La carrera principal del objetivo (RaceInfo en JSON); null si el objetivo no es una carrera. */
    fun mainRaceJson(): String? = app.data.goal?.takeIf { it.kind == GoalKind.RACE }?.race?.let { json.encodeToString(it) }
    /** Las carreras preparatorias (B o C) antes de la principal, como en las apps (2026-11-19). */
    fun otherRacesJson(): String = json.encodeToString(app.data.goal?.otherRaces ?: emptyList())
    fun addRace(raceJson: String) = app.addRace(read.decodeFromString<RaceInfo>(raceJson))
    fun removeRace(date: String) = app.removeRace(date)

    /** `kind`: RACE, FITNESS, STRENGTH o HEALTH; `raceJson`, solo para una carrera. Los errores llegan con su texto. */
    fun setGoal(kind: String, raceJson: String?) =
        app.setGoal(GoalKind.valueOf(kind), raceJson?.let { read.decodeFromString<RaceInfo>(it) })

    val goalLabel: String? get() = app.data.goal?.let { labelOf(it) }

    fun abandonGoal() = app.abandonGoal()
    fun exportJson(): String = app.exportJson()

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
    fun moveTargetsJson(date: String, slot: String): String = json.encodeToString(app.moveTargets(date, slot))
    fun moveSession(date: String, slot: String, to: String) = app.moveSession(date, slot, to)
    fun canChangeDay(date: String): Boolean = app.canChangeDay(date)
    fun preferTrain(date: String) = app.changeDay(date, app.minutesToTrain(app.day(date)))
    fun cannotTrain(date: String) = app.changeDay(date, 0)

    /** La sesión de fuerza de ese día serie a serie (StrengthScreen), o null si ese día no tiene fuerza. */
    fun strengthJson(date: String): String? = app.strengthScreen(date)?.let { json.encodeToString(it) }
    fun lastStrengthDate(): String? = app.lastStrengthDate()
    fun figureJson(pattern: String): String? = figureLinesJson(pattern)

    // Rutinas propias (Routines.kt): la web las manda y las recibe en JSON
    fun routinesJson(): String = json.encodeToString(app.routines)
    fun saveRoutineJson(text: String): String = json.encodeToString(app.saveRoutine(read.decodeFromString<Routine>(text)))
    fun deleteRoutine(id: String) = app.deleteRoutine(id)
    fun customExercisesJson(): String = json.encodeToString(app.customExercises)
    fun saveCustomExerciseJson(text: String): String = json.encodeToString(app.saveCustomExercise(read.decodeFromString<CustomExercise>(text)))
    fun deleteCustomExercise(name: String) = app.deleteCustomExercise(name)

    // Biblioteca de ejercicios (Library.kt)
    fun libraryJson(query: String, pattern: String?, equipment: String?): String = json.encodeToString(app.library(query, pattern, equipment))
    fun exerciseCardJson(name: String): String? = app.exerciseCard(name)?.let { json.encodeToString(it) }
    fun exerciseFigureJson(name: String): String? = app.exerciseFigure(name)?.let { json.encodeToString(it) }
    fun swapOptionsJson(name: String): String = json.encodeToString(app.swapOptions(name))
    fun patternLabelsJson(): String = json.encodeToString(PATTERN_LABEL)
    fun equipmentJson(): String = json.encodeToString(EQUIPMENT)
    fun zonesJson(): String = json.encodeToString(ZONES)
    fun libraryByZoneJson(query: String, zone: String?, equipment: String?, mine: Boolean): String =
        json.encodeToString(app.libraryByZone(query, zone, equipment, mine))
    fun myEquipmentJson(): String = json.encodeToString(app.equipment)
    fun setEquipmentJson(text: String) = app.setEquipment(read.decodeFromString<List<String>>(text))
    fun swapExercise(date: String, current: String, replacement: String) = app.swapExercise(date, current, replacement)

    /** Apunta o corrige una serie; `weight` vacío: con tu peso corporal; `rir` null: sin apuntar la reserva; `kind`: SET_KINDS o null. */
    fun logSet(date: String, exercise: String, set: Int, weight: String, reps: Int, rir: Int?, kind: String?) =
        app.logSet(date, exercise, set, weight.replace(',', '.').toDoubleOrNull(), reps, rir, kind)
    fun setKindsJson(): String = json.encodeToString(SET_KINDS)
    fun nextSetJson(date: String, current: Int): String? = app.nextSet(date, current)?.let { json.encodeToString(it) }
    fun exerciseHistoryJson(name: String): String = json.encodeToString(app.exerciseHistory(name))
    fun plates(weight: String): String? = weight.replace(',', '.').toDoubleOrNull()?.let { app.plates(it) }

    // Entreno libre (BrivelApp): la fuerza de hoy la monta el usuario
    fun workoutTodayJson(): String? = app.workoutToday?.let { json.encodeToString(it) }
    fun startWorkout(routineId: String?): String = app.startWorkout(routineId).date
    fun addWorkoutExercise(name: String) = app.addWorkoutExercise(name)
    fun removeWorkoutExercise(name: String) = app.removeWorkoutExercise(name)
    fun changeWorkoutSets(name: String, delta: Int) = app.changeWorkoutSets(name, delta)
    fun linkWorkoutExercise(name: String, linked: Boolean) = app.linkWorkoutExercise(name, linked)
    fun discardWorkout() = app.discardWorkout()
    fun closeStrength() = app.closeStrength()

    // Sesiones propias de resistencia (OwnSessions.kt): la web las manda y las recibe en JSON
    fun ownSessionsJson(): String = json.encodeToString(app.ownSessions)
    fun saveOwnSessionJson(text: String): String = json.encodeToString(app.saveOwnSession(read.decodeFromString<OwnSession>(text)))
    fun deleteOwnSession(id: String) = app.deleteOwnSession(id)
    fun doOwnSessionToday(id: String) = app.doOwnSessionToday(id)
    fun unscheduleOwnSession(id: String, date: String) = app.unscheduleOwnSession(id, date)
    fun ownSportsJson(): String = json.encodeToString(OWN_SPORTS)
    fun ownZonesJson(): String = json.encodeToString(OWN_ZONES.map { (k, label) -> listOf(k, label) })

    // Actividades propias (Activities.kt): en la web se importan de un archivo FIT; grabar con el GPS es de las apps
    fun activitiesJson(): String = json.encodeToString(app.activities())
    fun activityJson(id: String): String? = app.activity(id)?.let { json.encodeToString(it) }
    fun importFit(bytes: ByteArray): String = app.importFit(bytes)
    fun deleteActivity(id: String) = app.deleteActivity(id)

    // Material (Gear.kt): zapatillas y bicis con sus kilómetros
    fun gearJson(): String = json.encodeToString(app.gear())
    fun gearSportsJson(): String = json.encodeToString(GEAR_LABEL.map { (k, label) -> listOf(k, label) })
    fun saveGear(id: String?, name: String, sport: String, since: String, startKm: String, limitKm: String) {
        fun km(text: String) = text.trim().replace(',', '.').let { if (it.isEmpty()) null else it.toDoubleOrNull() ?: throw IllegalArgumentException("Escribe los kilómetros con números") }
        app.saveGear(id, name, sport, since, km(startKm) ?: 0.0, km(limitKm))
    }
    fun useGear(id: String) = app.useGear(id)
    fun retireGear(id: String) = app.retireGear(id)
    fun deleteGear(id: String) = app.deleteGear(id)
    fun moveGearUse(activity: String, gearId: String) = app.moveGearUse(activity, gearId)

    // Medidas corporales (Training.kt): [clave, nombre, unidad]
    fun measuresJson(): String = json.encodeToString(MEASURES.map { (k, m) -> listOf(k, m.label, m.unit) })
    /** Las dos etiquetas del resultado de un test (TEST_FIELDS): [dato principal, pulso o null]. */
    fun testFields(kind: String): Array<String?> = TEST_FIELDS.getValue(kind).let { arrayOf(it.first, it.second) }
    fun saveTestResult(date: String, kind: String, main: String, hr: String?) = app.saveTestResult(date, kind, main, hr)
    val efforts: Array<String> get() = EFFORTS.toTypedArray()
    fun effortLabel(effort: String): String = EFFORT_LABEL.getValue(effort)
    fun rateRun(id: String, effort: String): String = app.rateRun(id, effort)

    fun logMeasure(kind: String, value: String) =
        app.logMeasure(kind, value.replace(',', '.').toDoubleOrNull() ?: throw IllegalArgumentException("Escribe un número"))
    fun deleteMeasure(date: String, kind: String) = app.deleteMeasure(date, kind)

    // intervals.icu por OAuth con PKCE desde el navegador (cliente público, sin secreto); vuelve a brivel.app/intervals.
    // El token, en sessionStorage como el resto de secretos: al cerrar la pestaña hay que volver a conectar
    val intervalsAthlete: String? get() = app.linkedAthlete
    fun startIntervals(): Promise<String> = promise { app.startIntervals(window.location.origin + "/intervals") }
    fun finishIntervals(callback: String): Promise<Unit> = promise { app.finishIntervals(callback) }
    fun syncIntervals(): Promise<Unit> = promise { app.sync() }
    fun disconnectIntervals(): Promise<Unit> = promise { app.disconnectIntervals() }

    // Garmin Connect: la web descifra cada envío del relevo con su clave (IndexedDB) y lo entrega aquí ya en claro
    fun importGarmin(delivery: String) = app.importGarmin(delivery)
    fun clearGarmin() = app.clearGarmin()
    fun garminSyncedAt(): String? = app.garminSyncedAt()
    val garminConnected: Boolean get() = app.garminConnected
    fun garminLinkJson(): String? = app.garminLinkJson()

    /** Las sesiones de hoy (o la de ese deporte) para «Enviar al reloj», en JSON; lanza un error si no hay ninguna. */
    fun watchWorkoutsJson(sport: String?): String = json.encodeToString(app.watchWorkouts(sport))
    val watchSentText: String get() = app.watchSentText
    fun setGarminLink(privateKey: String, publicKey: String, token: String) = app.setGarminLink(privateKey, publicKey, token)
    fun clearGarminLink() = app.clearGarminLink()
}

/** El código, para enseñarlo una sola vez, y las llaves, para guardarlas en IndexedDB. */
@JsExport
class WebSyncStart(val code: String, val keys: WebSyncKeys)
