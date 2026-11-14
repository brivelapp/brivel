package app.brivel.app

// Controlador común de las dos apps: guarda en el móvil, sincroniza con intervals.icu y expone cada apartado.

import app.brivel.engine.Profile
import app.brivel.engine.WorkoutEvent
import app.brivel.engine.plusDays
import app.brivel.engine.strengthEvent
import app.brivel.engine.workoutEvent
import app.brivel.intervals.INTERVALS_CLIENT_ID
import app.brivel.intervals.INTERVALS_SCOPES
import app.brivel.intervals.IntervalsClient
import app.brivel.intervals.IntervalsException
import app.brivel.intervals.exchangeIntervalsCode
import app.brivel.intervals.str
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlin.io.encoding.Base64
import app.brivel.sync.SyncClient
import app.brivel.sync.SyncCode
import app.brivel.sync.Syncer
import app.brivel.sync.deriveKeys
import io.ktor.client.engine.HttpClientEngine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json

private const val DATA_KEY = "brivel.data"
private const val API_KEY = "intervals.apiKey"  // cuentas vinculadas con clave antes de OAuth (2026-11-07)
private const val INTERVALS_TOKEN = "intervals.token"  // token de OAuth, en el almacén seguro
private const val INTERVALS_PKCE = "intervals.pkce"  // "state verificador" mientras se conecta
internal const val SYNC_STATE_KEY = "brivel.sync"  // sync/Syncer.kt
private const val SYNC_CODE_KEY = "sync.code"  // en el almacén seguro, como la clave de intervals.icu
private const val SYNC_CONSENT_KEY = "brivel.syncConsent"  // fecha y versión de la política aceptadas (CUMPLIMIENTO #6)
private const val SYNC_URL = "https://brivel.app/api/boveda"
private const val GARMIN_URL = "https://brivel.app/api/garmin"
private const val GARMIN_KEY = "garmin.key"  // en el almacén seguro mientras se conecta; después, en AppData.garminLink
private const val GARMIN_PUB = "garmin.pub"
private const val GARMIN_STATE = "garmin.state"
private const val WATCH_SENT_KEY = "garmin.sent"  // lo mandado hoy a Garmin desde este dispositivo: fecha|deporte,deporte
private const val GARMIN_CONSENT_KEY = "brivel.garminConsent"  // fecha y versión de la política aceptadas (CUMPLIMIENTO #6)

/**
 * store: datos de la app; secrets: almacén seguro del sistema (solo la clave de intervals.icu).
 * today: la fecha de hoy (inyectable para los tests); httpEngine: solo para tests.
 */
class BrivelApp(
    private val store: KeyValueStore,
    private val secrets: KeyValueStore,
    private val today: () -> LocalDate,
    private val httpEngine: HttpClientEngine? = null,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // shortcut: claves del nombre anterior, que la web de brivel.app usó hasta el 2026-11-02; quitar cuando nadie las tenga
    init {
        for ((old, new) in listOf("trainsync.data" to DATA_KEY, "trainsync.sync" to SYNC_STATE_KEY, "trainsync.syncConsent" to SYNC_CONSENT_KEY)) {
            val value = store.read(old) ?: continue
            if (store.read(new) == null) store.write(new, value)
            store.write(old, null)
        }
    }

    var data: AppData = store.read(DATA_KEY)?.let { runCatching { migrate(json.decodeFromString<AppData>(it)) }.getOrNull() }
        ?: AppData()
        private set

    private fun save(new: AppData) {
        data = new
        store.write(DATA_KEY, json.encodeToString(new))
    }

    /** Los datos combinados con los de otro dispositivo (sync/Syncer.kt). */
    internal fun replaceData(new: AppData) = save(new)

    // ------------------------------------------------------------ objetivo y perfil

    /** Primera vez: falta el perfil o el objetivo. */
    val needsOnboarding: Boolean get() = data.profile == null || data.goal == null

    fun saveProfile(profile: UserProfile) = save(data.copy(profile = profile))

    /** Nuevo objetivo: los planes sin fecha empiezan un bloque nuevo este lunes. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun setGoal(kind: GoalKind, race: RaceInfo? = null) {
        require(kind != GoalKind.RACE || race != null) { "Una carrera necesita nombre, deporte y fecha" }
        val now = today()
        save(data.copy(goal = UserGoal(kind, race, now.toString()), blockStart = mondayOf(now).toString()))
    }

    /** Otra carrera antes de la principal (preparatoria, B o C): el plan afina unos días antes y recupera después. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun addRace(race: RaceInfo) {
        val goal = data.goal
        val main = goal?.race?.takeIf { goal.kind == GoalKind.RACE }
        require(goal != null && main != null) { "Primero elige una carrera principal como objetivo" }
        val date = LocalDate.parse(race.date)
        require(race.name.isNotBlank()) { "Ponle un nombre a la carrera" }
        require(date >= today() && date < LocalDate.parse(main.date)) { "Tiene que ser antes de ${main.name} y no en el pasado" }
        require(race.priority in setOf("B", "C")) { "Una carrera preparatoria es importante (B) o de preparación (C)" }
        save(data.copy(goal = goal.copy(otherRaces = (goal.otherRaces.filterNot { it.date == race.date } + race).sortedBy { it.date })))
    }

    fun removeRace(date: String) {
        val goal = data.goal ?: return
        save(data.copy(goal = goal.copy(otherRaces = goal.otherRaces.filterNot { it.date == date })))
    }

    /** Abandonar el objetivo: la app vuelve a preguntar qué quieres. */
    fun abandonGoal() = save(data.copy(goal = null))

    // ------------------------------------------------------------ apartados

    fun todayScreen(): TodayScreen = buildToday(data, today())
    fun month(year: Int, month: Int): MonthScreen = buildMonth(data, today(), year, month)
    fun progress(): ProgressScreen = buildProgress(data, today())
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun day(date: String): DayView = Planner(data, today()).day(LocalDate.parse(date))

    // ------------------------------------------------------------ acciones del día

    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun markDone(date: String, sport: String) {
        require(date <= today().toString()) { "No se puede marcar como hecha una sesión futura" }
        if (data.manualDone.none { it.date == date && it.sport == sport }) {
            save(data.copy(manualDone = data.manualDone + ManualDone(date, sport)))
        }
    }

    fun unmarkDone(date: String, sport: String) =
        save(data.copy(manualDone = data.manualDone.filterNot { it.date == date && it.sport == sport }))

    /** La alternativa elegida pasa a ser la sesión del día (en la app y, al enviarla, en el reloj). */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun chooseAlternative(date: String, plannedSport: String, chosenSport: String) {
        val day = (data.choices[date] ?: emptyMap()) + (plannedSport to chosenSport)
        save(data.copy(choices = data.choices + (date to day)))
    }

    fun clearChoice(date: String) = save(data.copy(choices = data.choices - date))

    /** Días de esta semana, de hoy en adelante, a los que se puede mover la sesión `slot` de `date` (sin otra igual). */
    fun moveTargets(date: String, slot: String): List<String> {
        val now = today()
        val from = LocalDate.parse(date)
        if (from < now) return emptyList()
        val week = Planner(data, now).week(mondayOf(from)).days
        return week.filter { it.date != date && it.date >= now.toString() && it.items.none { i -> i.slot == slot } }.map { it.date }
    }

    /** Mueve la sesión a otro día de su semana; moverla al día del plan la devuelve a su sitio. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun moveSession(date: String, slot: String, to: String) {
        require(to in moveTargets(date, slot)) { "Solo se puede mover a un día que queda de esa semana y sin otra sesión igual" }
        val item = day(date).items.first { it.slot == slot }
        val origin = item.movedFrom ?: date
        val others = data.moves.filterNot { it.to == date && it.slot == slot && it.from == origin }
        save(data.copy(moves = if (to == origin) others else others + SessionMove(origin, slot, to)))
    }

    // ------------------------------------------------------------ rutinas propias (Routines.kt)

    val routines: List<Routine> get() = data.routines

    /**
     * Crea (id vacío) o cambia una rutina; los días que se le asignan dejan de ser de las demás. Cuenta desde hoy.
     * shortcut: quitar un día a otra rutina, o borrarla, también cambia sus días ya pasados en el calendario; guardar
     * el historial de asignaciones si alguien lo echa en falta.
     */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun saveRoutine(routine: Routine): Routine {
        val r = validRoutine(routine, data.customExercises).copy(id = routine.id.ifBlank { kotlin.random.Random.nextLong().toULong().toString(36) },
            since = today().toString())
        val others = data.routines.filter { it.id != r.id }.map { it.copy(days = it.days - r.days.toSet()) }
        save(data.copy(routines = others + r))
        return r
    }

    fun deleteRoutine(id: String) = save(data.copy(routines = data.routines.filter { it.id != id }))

    // ------------------------------------------------------------ entreno libre (Planner.applyWorkouts)

    /** El entreno libre de hoy, o null si no se ha empezado ninguno. */
    val workoutToday: FreeWorkout? get() = today().toString().let { d -> data.workouts.firstOrNull { it.date == d } }

    /**
     * Empieza hoy un entreno libre: vacío (`routineId` null) o con los ejercicios de una rutina, aunque hoy no le toque.
     * Hoy sustituye a la fuerza del plan. Si ya había uno empezado, devuelve ese sin cambiarlo.
     * shortcut: solo hoy; apuntar un entreno libre de un día pasado, cuando alguien lo pida.
     */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun startWorkout(routineId: String?): FreeWorkout {
        workoutToday?.let { return it }
        val routine = routineId?.let { id ->
            data.routines.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Esa rutina ya no existe")
        }
        val w = FreeWorkout(today().toString(), routine?.name ?: "Entreno libre", routine?.exercises ?: emptyList(), routine?.rir ?: 2)
        save(data.copy(workouts = data.workouts + w))
        return w
    }

    private fun changeWorkout(change: (FreeWorkout) -> FreeWorkout) {
        val w = workoutToday ?: throw IllegalStateException("Hoy no hay un entreno libre empezado")
        save(data.copy(workouts = data.workouts.filter { it.date != w.date } + change(w)))
    }

    private fun FreeWorkout.with(name: String, change: (RoutineExercise) -> RoutineExercise): FreeWorkout {
        require(exercises.any { it.name == name }) { "Ese ejercicio no está en el entreno" }
        return copy(exercises = exercises.map { if (it.name == name) change(it) else it })
    }

    /** Añade un ejercicio al entreno libre de hoy: 3 series de 8–12 repeticiones, o de 20–40 segundos si va por tiempo. */
    @Throws(Exception::class)
    fun addWorkoutExercise(name: String) {
        val info = exerciseInfo(name, data.customExercises) ?: throw IllegalArgumentException("«$name» no está en la biblioteca")
        changeWorkout { w ->
            require(w.exercises.none { it.name == name }) { "Ese ejercicio ya está en el entreno" }
            require(w.exercises.size < 30) { "Un entreno tiene 30 ejercicios como mucho" }
            w.copy(exercises = w.exercises + if (info.timed) RoutineExercise(name, 3, 20, 40) else RoutineExercise(name, 3, 8, 12))
        }
    }

    /** Quita un ejercicio del entreno libre de hoy, con las series que tuviera apuntadas hoy. */
    @Throws(Exception::class)
    fun removeWorkoutExercise(name: String) {
        val date = today().toString()
        changeWorkout { w -> w.with(name) { it }.copy(exercises = w.exercises.filter { it.name != name }) }
        save(data.copy(sets = data.sets.filterNot { it.date == date && it.exercise == name }))
    }

    /** Una serie más (`delta` 1) o una menos (−1) en un ejercicio del entreno libre de hoy; entre 1 y 10. */
    @Throws(Exception::class)
    fun changeWorkoutSets(name: String, delta: Int) = changeWorkout { w ->
        w.with(name) { e ->
            require(e.sets + delta in 1..10) { "Entre 1 y 10 series" }
            e.copy(sets = e.sets + delta)
        }
    }

    /** Pone o quita la superserie de un ejercicio del entreno libre de hoy con el anterior. */
    @Throws(Exception::class)
    fun linkWorkoutExercise(name: String, linked: Boolean) = changeWorkout { w ->
        require(!linked || w.exercises.firstOrNull()?.name != name) { "El primer ejercicio no tiene uno anterior" }
        w.with(name) { it.copy(linked = linked) }
    }

    /** Descarta el entreno libre de hoy y las series apuntadas hoy en sus ejercicios: el día vuelve a su plan. */
    fun discardWorkout() {
        val w = workoutToday ?: return
        val names = w.exercises.map { it.name }.toSet()
        save(data.copy(workouts = data.workouts.filter { it.date != w.date },
            sets = data.sets.filterNot { it.date == w.date && it.exercise in names }))
    }

    // ------------------------------------------------------------ biblioteca y ejercicios propios (Library.kt)

    val customExercises: List<CustomExercise> get() = data.customExercises

    /** El material que tiene el usuario (vacío: sin indicar). Filtra la rotación de ejercicios y «solo con mi material». */
    val equipment: List<String> get() = data.equipment

    @Throws(Exception::class)
    fun setEquipment(tags: List<String>) {
        require(tags.all { it in EQUIPMENT }) { "Material no válido" }
        save(data.copy(equipment = EQUIPMENT.filter { it in tags }))
    }

    /** La biblioteca por zona del cuerpo; `mine`: solo lo que se puede hacer con el material del usuario. */
    fun libraryByZone(query: String, zone: String?, equipment: String?, mine: Boolean): List<ExerciseCard> =
        library(query, null, equipment, data.customExercises, zone, data.equipment.takeIf { mine && it.isNotEmpty() })

    fun library(query: String, pattern: String?, equipment: String?): List<ExerciseCard> =
        library(query, pattern, equipment, data.customExercises)

    fun exerciseCard(name: String): ExerciseCard? = exerciseCard(name, data.customExercises)

    fun swapOptions(name: String): List<ExerciseCard> = swapOptions(name, data.customExercises)

    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun saveCustomExercise(exercise: CustomExercise): CustomExercise {
        val e = validCustomExercise(exercise, data.customExercises)
        save(data.copy(customExercises = data.customExercises + e))
        return e
    }

    /** Lo ya apuntado se queda en el historial con su nombre; solo se impide si el plan aún lo usa. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun deleteCustomExercise(name: String) {
        data.routines.firstOrNull { r -> r.exercises.any { it.name == name } }?.let {
            throw IllegalArgumentException("Está en la rutina «${it.name}»: quítalo de ahí antes")
        }
        require(data.swaps.none { swapped(it.original, data.swaps, "9999-12-31") == name }) { "Está cambiado por otro en tu plan: deshaz el cambio antes" }
        save(data.copy(customExercises = data.customExercises.filter { it.name != name }))
    }

    /** "Hoy no puedo" (0) o "puedo entrenar este día" (minutos): la semana se recalcula desde hoy. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun changeDay(date: String, minutes: Int) {
        val now = today()
        val target = LocalDate.parse(date)
        val monday = mondayOf(now)
        require(target >= now && target < monday.plusDays(7)) { "Solo se pueden cambiar los días que quedan de esta semana" }
        val key = monday.toString()
        val weekday = Planner.weekdayOf(target)
        val current = data.weekChanges[key]
        val changes = (current?.changes ?: emptyMap()) + (weekday to minutes)
        // Un cambio de otro día guarda el anterior: los días que ya pasaron siguen como estaban
        val earlier = current?.let { if (it.since == now.toString()) it.earlier else it.earlier + it.copy(earlier = emptyList()) } ?: emptyList()
        save(data.copy(weekChanges = data.weekChanges + (key to WeekChange(changes, now.toString(), earlier))))
    }

    /** Se puede cambiar lo propuesto (entrenar o no) en los días que quedan de esta semana. */
    fun canChangeDay(date: String): Boolean {
        val now = today()
        val target = LocalDate.parse(date)
        return target >= now && target < mondayOf(now).plusDays(7)
    }

    /** Minutos para un día de descanso que quieres entrenar: lo que te deja el motor o tu día típico. */
    fun minutesToTrain(day: DayView): Int =
        day.availableMin ?: data.profile?.days?.values?.filter { it > 0 }?.sorted()?.let { it[it.size / 2] } ?: 45

    fun resetWeek() = save(data.copy(weekChanges = data.weekChanges - mondayOf(today()).toString()))

    /** Sin reloj: sueño, energía y agujetas de 1 (mal) a 5 (bien) ajustan la sesión de hoy. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun answerReadiness(sleep: Int, energy: Int, soreness: Int) {
        require(listOf(sleep, energy, soreness).all { it in 1..5 })
        save(data.copy(readiness = data.readiness + (today().toString() to Readiness(sleep, energy, soreness))))
    }

    // ------------------------------------------------------------ intervals.icu

    val linkedAthlete: String? get() =
        data.intervalsAthlete?.takeIf { secrets.read(INTERVALS_TOKEN) != null || secrets.read(API_KEY) != null }

    private fun client(): IntervalsClient? {
        val athlete = data.intervalsAthlete ?: return null
        secrets.read(INTERVALS_TOKEN)?.let { return IntervalsClient(athlete, it, httpEngine, oauth = true) }
        val key = secrets.read(API_KEY) ?: return null
        return IntervalsClient(athlete, key, httpEngine)
    }

    /**
     * Primer paso de conectar: la dirección de intervals.icu que se abre en el navegador. Al aceptar, intervals.icu vuelve
     * a `redirect` (brivel://intervals en las apps, brivel.app/intervals en la web) y entonces `finishIntervals`.
     */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    suspend fun startIntervals(redirect: String): String {
        val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val verifier = b64.encode(CryptographyRandom.Default.nextBytes(32))
        val state = b64.encode(CryptographyRandom.Default.nextBytes(16))
        val challenge = b64.encode(CryptographyProvider.Default.get(SHA256).hasher().hash(verifier.encodeToByteArray()))
        secrets.write(INTERVALS_PKCE, "$state $verifier")
        return URLBuilder("https://intervals.icu/oauth/authorize").apply {
            parameters.append("client_id", INTERVALS_CLIENT_ID)
            parameters.append("redirect_uri", redirect)
            parameters.append("scope", INTERVALS_SCOPES)
            parameters.append("state", state)
            parameters.append("code_challenge", challenge)
            parameters.append("code_challenge_method", "S256")
        }.buildString()
    }

    /** De vuelta de intervals.icu (la dirección completa, con ?code=…&state=…): canjea el código y descarga los datos. */
    @Throws(Exception::class)
    suspend fun finishIntervals(callback: String) {
        val pkce = secrets.read(INTERVALS_PKCE)?.split(' ')
            ?: throw IllegalStateException("La conexión con intervals.icu caducó: vuelve a empezar")
        secrets.write(INTERVALS_PKCE, null)
        val params = Url(callback).parameters
        if (params["error"] != null) throw IllegalArgumentException("No has dado permiso en intervals.icu")
        // El state que mandamos: sin él, otra web podría colar su propia cuenta de intervals.icu
        require(params["state"] == pkce[0]) { "La vuelta de intervals.icu no es de esta conexión: vuelve a empezar" }
        val code = params["code"] ?: throw IllegalArgumentException("intervals.icu no devolvió el código")
        val (token, athlete) = try {
            exchangeIntervalsCode(code, pkce[1], httpEngine)
        } catch (e: IntervalsException) {
            throw IllegalArgumentException("intervals.icu respondió ${e.status}: vuelve a empezar")
        }
        secrets.write(INTERVALS_TOKEN, token)
        secrets.write(API_KEY, null)
        save(data.copy(intervalsAthlete = athlete))
        sync()
    }

    /** Desvincula; con OAuth, antes retira el permiso en intervals.icu (sin red, se desvincula igual aquí). */
    @Throws(Exception::class)
    suspend fun disconnectIntervals() {
        if (secrets.read(INTERVALS_TOKEN) != null) client()?.let { c -> runCatching { c.disconnectApp() }; c.close() }
        unlinkIntervals()
    }

    fun unlinkIntervals() {
        secrets.write(API_KEY, null)
        secrets.write(INTERVALS_TOKEN, null)
        save(data.copy(intervalsAthlete = null, cache = null))
    }

    /** Descarga lo necesario y lo guarda en el móvil; renombra las actividades recientes si el usuario lo quiere. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    suspend fun sync() {
        val c = client() ?: return
        try {
            val now = today()
            val athlete = c.athlete()
            val wellness = c.wellness(now.plusDays(-365), now)
            val activities = c.activities(now.plusDays(-365), now)
            var renamed = data.renamed
            if (data.profile?.renameActivities != false) {
                for (a in activities.filter { it["start_date_local"].str().orEmpty() >= now.plusDays(-14).toString() }) {
                    val id = a["id"].str() ?: continue
                    val title = activityTitle(a) ?: continue
                    if (id in renamed || a["name"].str() == title) continue
                    runCatching { c.renameActivity(id, title) }.onSuccess { renamed = renamed + id }
                }
            }
            val fixed = activities.map { a ->
                val id = a["id"].str()
                val title = activityTitle(a)
                if (id != null && id in renamed && title != null) kotlinx.serialization.json.JsonObject(
                    a + ("name" to kotlinx.serialization.json.JsonPrimitive(title))) else a
            }
            save(data.copy(cache = IntervalsCache(athlete, wellness, fixed, now.toString()), renamed = renamed))
        } finally {
            c.close()
        }
    }

    /** Adónde va «Enviar al reloj»: directo a Garmin Connect si está conectado; si no, a intervals.icu; null: a ningún sitio. */
    val watchRoute: String? get() = when {
        garminConnected -> "Garmin Connect"
        linkedAthlete != null -> "intervals.icu"
        else -> null
    }

    /** Lo que se dice al enviar al reloj. */
    val watchSentText: String get() = if (garminConnected) "Enviada a Garmin Connect: llegará a tu reloj al sincronizarlo"
        else "Enviada a intervals.icu: llegará a tu reloj si tienes la sincronización activada"

    /** Las sesiones de hoy (o solo la del deporte indicado) en el formato del reloj: para el relevo de Garmin, el Apple Watch y Health Connect. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun watchWorkouts(sport: String? = null): List<WatchWorkout> {
        val planner = Planner(data, today())
        val day = planner.day(today())
        val items = day.items.filter { sport == null || it.sport == sport }
        check(items.isNotEmpty()) { "Hoy no hay sesiones que enviar" }
        val physio = planner.physiology ?: Profile(activeSports = listOf("running"))
        val date = day.date.toString()
        val daySports = day.items.map { WATCH_SPORT[it.sport] ?: "other" }.distinct()
        return items.map { item ->
            (item.session?.let { watchWorkout(it, date, physio) }
                ?: watchStrength(strengthScreen(item, date, data.sets, data.customExercises), "Brivel · ${item.title}").let { w ->
                    // Sin ejercicios que se apunten serie a serie (equilibrio): un paso abierto con la lista
                    if (w.blocks.isNotEmpty()) w else w.copy(blocks = listOf(WatchBlock(1,
                        listOf(WatchStep("active", seconds = item.durationMin * 60, note = item.hint.take(500))))))
                }).copy(daySports = daySports)
        }
    }

    /** El patrón de un ejercicio de `WatchStep.exercise`, para que cada plataforma elija su tipo de ejercicio más cercano. */
    fun watchPattern(exercise: String): String? = exerciseInfo(exercise, data.customExercises)?.pattern

    private fun sentToday(): Set<String> = store.read(WATCH_SENT_KEY)?.takeIf { it.startsWith(today().toString() + "|") }
        ?.substringAfter("|")?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    private fun rememberSent(date: String, sports: Set<String>) = store.write(WATCH_SENT_KEY, "$date|${sports.joinToString(",")}")

    /**
     * Quita de Garmin lo enviado hoy que ya no está en el plan (sesión movida, «hoy no puedo», otro deporte sin reenviar).
     * Sin nada que quitar no llama a la red. shortcut: solo conoce lo enviado desde este dispositivo; si hiciera falta
     * en todos, guardarlo en AppData (cambia el formato).
     */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    suspend fun retireFromWatch() {
        val link = data.garminLink ?: return
        val sent = sentToday()
        if (sent.isEmpty()) return
        val plan = Planner(data, today()).day(today()).items.map { WATCH_SPORT[it.sport] ?: "other" }.toSet()
        if ((sent - plan).isEmpty()) return
        try {
            garminClient.pruneWorkouts(link.token, today().toString(), plan.toList())
        } catch (e: GarminException) {
            if (e.status == 401) clearGarminLink()
            throw e
        }
        rememberSent(today().toString(), sent intersect plan)
    }

    /** Envía al reloj las sesiones de hoy o solo la del deporte indicado: por Garmin Connect o por intervals.icu. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    suspend fun sendToday(sport: String? = null): List<String> {
        data.garminLink?.let { link ->
            val workouts = watchWorkouts(sport)
            try {
                garminClient.sendWorkouts(link.token, json.encodeToString(workouts))
            } catch (e: GarminException) {
                if (e.status == 401) clearGarminLink()  // desconectado desde Garmin Connect o desde otro dispositivo
                throw e
            }
            rememberSent(workouts.first().date, sentToday() + workouts.map { it.sport })
            return workouts.map { it.name.removePrefix("Brivel · ") }
        }
        val c = client() ?: throw IllegalStateException("Vincula primero tu cuenta de intervals.icu o conecta Garmin")
        try {
            val planner = Planner(data, today())
            val day = planner.day(today())
            val items = day.items.filter { sport == null || it.sport == sport }
            check(items.isNotEmpty()) { "Hoy no hay sesiones que enviar" }
            val physio = planner.physiology ?: Profile(activeSports = listOf("running"))
            val events: List<WorkoutEvent> = items.map { item ->
                when {
                    item.session != null -> workoutEvent(item.session, day.date, physio, key = item.slot)
                    item.healthStrength != null -> strengthEvent(item.healthStrength.copy(exercises = item.exercises), day.date)
                    else -> WorkoutEvent(startDateLocal = "${day.date}T00:00:00", type = "WeightTraining",
                        name = "Brivel · Fuerza", movingTime = item.durationMin * 60,
                        description = (listOf(item.hint, "") + item.exercises.map { "• $it" } + listOfNotNull(item.notes))
                            .joinToString("\n"),
                        externalId = "brivel-${day.date}-strength")
                }
            }
            c.upsertWorkouts(events)
            return events.map { it.name.removePrefix("Brivel · ") }
        } finally {
            c.close()
        }
    }

    // ------------------------------------------------------------ registro de fuerza

    /** Apunta un ejercicio hecho de una vez: `sets` series iguales (sustituye lo apuntado ese día en ese ejercicio). */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun logLift(date: String, exercise: String, weightKg: Double?, reps: Int, sets: Int) {
        require(date <= today().toString()) { "No se puede apuntar una sesión futura" }
        require(reps in 1..100 && sets in 1..20 && (weightKg == null || weightKg in 0.0..500.0)) { "Revisa el peso y las repeticiones" }
        val name = exerciseName(exercise)
        save(data.copy(sets = data.sets.filterNot { it.date == date && it.exercise == name } +
            (1..sets).map { SetLog(date, name, it, weightKg, reps) }))
    }

    fun liftHint(exerciseLine: String): LiftHint = liftHint(daySummaries(data.sets), exerciseLine)

    /** La sesión de fuerza de ese día serie a serie, o null si ese día no tiene fuerza. */
    fun strengthScreen(date: String): StrengthScreen? =
        day(date).items.firstOrNull { it.isStrength }?.let { item ->
            val s = strengthScreen(item, date, data.sets, data.customExercises)
            s.copy(exercises = s.exercises.map { e -> e.copy(original = originalOf(e.name, date)) })
        }

    /** Los ejercicios se cambian en las sesiones de hoy en adelante: las pasadas se quedan como se hicieron. */
    fun canSwapExercises(date: String): Boolean = date >= today().toString()

    /** El ejercicio del plan que `name` sustituye ese día, o null si no es un cambio. */
    private fun originalOf(name: String, date: String): String? =
        data.swaps.map { it.original }.distinct().firstOrNull { it != name && swapped(it, data.swaps, date) == name }
            ?: rotationOrigin(name, date)

    /** El ejercicio del plan del que `name` es la variante de este bloque, o null si no la puso la rotación. */
    private fun rotationOrigin(name: String, date: String): String? =
        day(date).items.firstOrNull { it.isStrength }?.rotatedFrom?.get(name)

    /**
     * Cambia `current` por `replacement` desde `date` (la sesión que se ve) en adelante; los días anteriores siguen con
     * el que se hizo. `replacement` igual al ejercicio del plan, o null, vuelve a él. Si `current` es la variante que puso
     * la rotación, el cambio se guarda sobre el ejercicio del plan: vale también en los bloques siguientes, y volver al del
     * plan lo deja fijo (deja de rotar).
     */
    @Throws(Exception::class)
    fun swapExercise(date: String, current: String, replacement: String?) {
        require(canSwapExercises(date)) { "Solo se puede cambiar en las sesiones de hoy en adelante" }
        val original = originalOf(current, date) ?: current
        val to = if (rotationOrigin(current, date) != null) replacement ?: original else replacement?.takeIf { it != original }
        require(to == null || to == original || swapOptions(original, data.customExercises).any { it.name == to }) { "Ese ejercicio no es del mismo tipo" }
        save(data.copy(swaps = data.swaps.filterNot { it.original == original && it.since == date } +
            ExerciseSwap(date, original, to)))
    }

    /** El día más reciente de la última semana (hoy incluido) con sesión de fuerza, o null. */
    fun lastStrengthDate(): String? = (0..6).map { today().plusDays(-it).toString() }.firstOrNull { strengthScreen(it) != null }

    /**
     * Apunta o corrige la serie `set` (desde 1) de un ejercicio; `reps` son segundos en los ejercicios por tiempo; `rir`:
     * las repeticiones que te quedaban (null: sin apuntar). Al corregirla conserva su tipo.
     */
    @Throws(Exception::class)
    fun logSet(date: String, exercise: String, set: Int, weightKg: Double?, reps: Int, rir: Int?) =
        logSet(date, exercise, set, weightKg, reps, rir,
            data.sets.lastOrNull { it.date == date && it.exercise == exercise && it.set == set }?.kind)

    /** Como la anterior, con el tipo de la serie (SET_KINDS: calentamiento, descendente o al fallo; null: normal). */
    @Throws(Exception::class)
    fun logSet(date: String, exercise: String, set: Int, weightKg: Double?, reps: Int, rir: Int?, kind: String?) {
        require(date <= today().toString()) { "No se puede apuntar una sesión futura" }
        require(exercise.isNotBlank() && set in 1..20 && reps in 1..600 && (weightKg == null || weightKg in 0.0..500.0) &&
            (rir == null || rir in 0..10)) { "Revisa el peso y las repeticiones" }
        require(kind == null || kind in SET_KINDS) { "Tipo de serie no válido" }
        save(data.copy(sets = data.sets.filterNot { it.date == date && it.exercise == exercise && it.set == set } +
            SetLog(date, exercise, set, weightKg, reps, rir, kind)))
    }

    /** Qué serie toca en la sesión de ese día después de apuntar una en el ejercicio `current`; null: sesión completa. */
    fun nextSet(date: String, current: Int): NextSet? = strengthScreen(date)?.let { nextSet(it.exercises, current) }

    /** El historial de un ejercicio: últimos días, gráfica y mejores marcas (Training.kt). */
    fun exerciseHistory(name: String): ExerciseHistory = exerciseHistory(name, data.sets, data.customExercises)

    /** Los discos por lado para ese peso con la barra de 20 kg; null si no se puede montar. */
    fun plates(weightKg: Double): String? = platesText(weightKg)

    // ------------------------------------------------------------ medidas corporales (Training.kt)

    /** Apunta una medida de hoy; otra de hoy del mismo tipo la sustituye. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun logMeasure(kind: String, value: Double) = logMeasure(kind, value, today().toString())

    /** Apunta una medida de ese día (hoy o pasado); otra del mismo día y tipo la sustituye. */
    @Throws(Exception::class)
    fun logMeasure(kind: String, value: Double, date: String) {
        val m = MEASURES[kind] ?: throw IllegalArgumentException("Medida no válida")
        require(date <= today().toString()) { "No se puede apuntar una medida futura" }
        require(value in m.range) { "${m.label}: entre ${num(m.range.start)} y ${num(m.range.endInclusive)} ${m.unit}" }
        save(data.copy(measurements = data.measurements.filterNot { it.date == date && it.kind == kind } + BodyMeasure(date, kind, value)))
    }

    fun deleteMeasure(date: String, kind: String) =
        save(data.copy(measurements = data.measurements.filterNot { it.date == date && it.kind == kind }))

    /** Borra una serie; las siguientes de ese ejercicio y día bajan un número para que no queden huecos. */
    fun deleteSet(date: String, exercise: String, set: Int) {
        save(data.copy(sets = data.sets.mapNotNull {
            when {
                it.date != date || it.exercise != exercise || it.set < set -> it
                it.set == set -> null
                else -> it.copy(set = it.set - 1)
            }
        }))
    }

    // ------------------------------------------------------------ datos de salud del móvil

    /** Guarda lo leído de Health Connect o Apple Salud (lo lee cada plataforma con su permiso). */
    fun importHealth(snapshot: HealthSnapshot) {
        // Una lectura vacía (permiso retirado, Health Connect no disponible) no borra lo que ya había: para eso, "Olvidar"
        if (snapshot.days.isEmpty() && snapshot.workouts.isEmpty() && data.health != null) return
        save(data.copy(health = snapshot))
    }

    fun clearHealth() = save(data.copy(health = null))

    /** Un envío de Garmin ya descifrado en el dispositivo (el JSON que prepara el relevo). */
    @Throws(Exception::class)
    fun importGarmin(delivery: String) {
        val d = json.decodeFromString<GarminDelivery>(delivery)
        require(d.v == 1) { "Este envío de Garmin es de una versión más nueva: actualiza Brivel" }
        save(data.copy(garmin = mergeGarmin(data.garmin, d, today())))
    }

    /** Lo de Garmin guardado en este dispositivo, al desconectar. */
    fun clearGarmin() = save(data.copy(garmin = null))

    /** Cuándo llegó el último envío de Garmin, o null sin datos de Garmin. */
    fun garminSyncedAt(): String? = data.garmin?.syncedAt

    // Una función y no una propiedad: así la web (que tiene su propio cliente) no arrastra ktor en su paquete
    private val garminClient: GarminClient get() = GarminClient(GARMIN_URL, httpEngine)

    /** Conectado a Garmin en este dispositivo o en otro de la misma sesión de sincronización. */
    val garminConnected: Boolean get() = data.garminLink != null

    /** La conexión en JSON para la web, que descifra con WebCrypto (web/src/garmin.ts); null sin conexión. */
    fun garminLinkJson(): String? = data.garminLink?.let { json.encodeToString(it) }

    /** Guarda la conexión recién hecha (la web, tras recoger el buzón); la sincronización la lleva a los demás. */
    fun setGarminLink(privateKey: String, publicKey: String, token: String) {
        save(data.copy(garminLink = GarminLink(privateKey, publicKey, token, today().toString())))
        store.write(GARMIN_CONSENT_KEY, "${today()} $GARMIN_POLICY_VERSION")
    }

    /** Sin conexión con Garmin (desconectada aquí o desde Garmin Connect); lo descargado se queda. */
    fun clearGarminLink() {
        save(data.copy(garminLink = null))
        store.write(GARMIN_CONSENT_KEY, null)
    }

    /**
     * Primer paso de conectar: crea las claves y devuelve la dirección de Garmin que la app abre en el navegador; al
     * volver (brivel://garmin), `finishGarmin`. Requiere el consentimiento en la pantalla.
     */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    suspend fun startGarmin(): String {
        val keys = newGarminKeys()
        val (url, state) = garminClient.start(keys.publicKey)
        secrets.write(GARMIN_KEY, encodeKey(keys.privateKey))
        secrets.write(GARMIN_PUB, encodeKey(keys.publicKey))
        secrets.write(GARMIN_STATE, state)
        return url
    }

    /** De vuelta de Garmin: recoge el token del buzón y descarga lo que haya. Cuántos envíos llegaron. */
    @Throws(Exception::class)
    suspend fun finishGarmin(): Int {
        val state = secrets.read(GARMIN_STATE) ?: throw GarminException(0, "La conexión con Garmin caducó: vuelve a empezar")
        try {
            val token = garminClient.claimWithRetry(state)
            setGarminLink(secrets.read(GARMIN_KEY)!!, secrets.read(GARMIN_PUB)!!, token)
        } finally {
            for (k in listOf(GARMIN_KEY, GARMIN_PUB, GARMIN_STATE)) secrets.write(k, null)
        }
        return pullGarmin()
    }

    /** Descarga lo pendiente, lo abre aquí y lo guarda; después el servidor lo borra. Sin conexión con Garmin, 0. */
    @Throws(Exception::class)
    suspend fun pullGarmin(): Int {
        val link = data.garminLink ?: return 0
        val keys = GarminKeys(decodeKey(link.privateKey), decodeKey(link.publicKey))
        var count = 0
        while (true) {
            val (items, more) = try {
                garminClient.inbox(link.token)
            } catch (e: GarminException) {
                // Desconectado desde Garmin Connect o desde otro dispositivo: el servidor ya lo borró todo
                if (e.status == 401) clearGarminLink()
                throw e
            }
            if (items.isEmpty()) return count
            for ((_, sealed) in items) importGarmin(openGarmin(sealed, keys))
            garminClient.ack(link.token, items.last().first)
            count += items.size
            if (!more) return count
        }
    }

    /** Desconecta en Garmin y en el servidor, para todos los dispositivos de la sesión; lo descargado se queda. */
    @Throws(Exception::class)
    suspend fun disconnectGarmin() {
        data.garminLink?.let { garminClient.disconnect(it.token) }
        clearGarminLink()
    }

    /** Límites de las zonas de FC para repartir los pulsos de cada entrenamiento al leerlos del móvil. */
    fun zoneBounds(measuredMaxHr: Double?): List<Double> =
        heartRateZoneBounds(data.profile, today(), measuredMaxHr, data.health?.workouts ?: emptyList())  // los de la lectura anterior

    // ------------------------------------------------------------ recordatorio diario

    /** Recordatorio opcional a esa hora (0–23); null lo apaga. Cada plataforma lo programa. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun setReminder(hour: Int?) {
        require(hour == null || hour in 0..23) { "La hora va de 0 a 23" }
        save(data.copy(reminderHour = hour))
    }

    /**
     * Qué dice el recordatorio de ese día: solo lo que toca, sin mensajes comerciales ni reproches.
     * null si no hay nada que recordar (sin objetivo, descanso o ya hecho): ese día no se avisa.
     */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    fun reminderText(date: String): String? {
        if (needsOnboarding) return null
        val day = Planner(data, today()).day(LocalDate.parse(date))
        val pending = day.items.filter { it.slot !in day.doneSports && it.sport !in day.doneSports }.takeIf { it.isNotEmpty() } ?: return null
        return pending.joinToString(" y ") { item ->
            (if (item.isStrength) item.title else "${SPORT_LABEL[item.sport]} · ${item.title}") + ", ${item.durationMin} min"
        }
    }

    // ------------------------------------------------------------ otros dispositivos (docs/SINCRONIZACION.md)

    /** El código guardado en el almacén seguro: se puede volver a enseñar para añadir otro dispositivo. */
    val syncCode: String? get() = secrets.read(SYNC_CODE_KEY)

    private suspend fun syncerFor(code: SyncCode): Syncer {
        val keys = deriveKeys(code)
        return Syncer(this, store, keys, SyncClient(SYNC_URL, keys.bearer, httpEngine))
    }

    private fun startSync(code: SyncCode) {
        secrets.write(SYNC_CODE_KEY, code.text)
        store.write(SYNC_CONSENT_KEY, "${today()} $PRIVACY_POLICY_VERSION")
    }

    /** Código nuevo: sube los datos de este móvil y devuelve el código. Requiere el consentimiento en la pantalla. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    suspend fun createSync(): String {
        val code = SyncCode.generate()
        syncerFor(code).sync(create = true)
        startSync(code)
        return code.text
    }

    /** Unirse con el código de otro dispositivo: junta los datos de aquí con los de la copia sin borrar nada. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    suspend fun joinSync(text: String) {
        val code = SyncCode.parse(text) ?: throw IllegalArgumentException("El código no es válido: revisa las letras")
        syncerFor(code).sync()
        startSync(code)
    }

    /** Sincroniza si está activado. Devuelve el aviso si los dos dispositivos cambiaron lo mismo; null si no. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    suspend fun syncDevices(): String? {
        val code = syncCode?.let { SyncCode.parse(it) } ?: return null
        return conflictMessage(syncerFor(code).sync().conflicts)
    }

    /** Deja de sincronizar en este móvil; la copia del servidor y los datos de aquí se quedan. */
    fun forgetSync() {
        secrets.write(SYNC_CODE_KEY, null)
        store.write(SYNC_STATE_KEY, null)
        store.write(SYNC_CONSENT_KEY, null)
    }

    /** Borra la copia del servidor (RGPD art. 17); los datos de este móvil se quedan. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    suspend fun deleteSync() {
        val code = syncCode?.let { SyncCode.parse(it) } ?: return
        syncerFor(code).deleteRemote()
        forgetSync()
    }

    // ------------------------------------------------------------ tus datos

    /** Todo lo guardado, en JSON (portabilidad, RGPD art. 20). La clave de intervals.icu no se incluye. */
    fun exportJson(): String = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(data.copy(garminLink = null))

    /** Borra todo del móvil, clave incluida, y deja de sincronizar: si no, la próxima sincronización lo borraría también en el servidor. */
    fun deleteAll() {
        secrets.write(API_KEY, null)
        secrets.write(INTERVALS_TOKEN, null)
        store.write(DATA_KEY, null)
        forgetSync()
        data = AppData()
    }
}

/** La fecha de hoy en la zona horaria del móvil. */
@OptIn(kotlin.time.ExperimentalTime::class)
fun systemToday(): LocalDate = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
