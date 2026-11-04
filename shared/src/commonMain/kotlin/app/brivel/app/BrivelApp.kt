package app.brivel.app

// Controlador común de las dos apps: guarda en el móvil, sincroniza con intervals.icu y expone cada apartado.

import app.brivel.engine.Profile
import app.brivel.engine.WorkoutEvent
import app.brivel.engine.plusDays
import app.brivel.engine.strengthEvent
import app.brivel.engine.workoutEvent
import app.brivel.intervals.IntervalsClient
import app.brivel.intervals.IntervalsException
import app.brivel.intervals.str
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
private const val API_KEY = "intervals.apiKey"
internal const val SYNC_STATE_KEY = "brivel.sync"  // sync/Syncer.kt
private const val SYNC_CODE_KEY = "sync.code"  // en el almacén seguro, como la clave de intervals.icu
private const val SYNC_CONSENT_KEY = "brivel.syncConsent"  // fecha y versión de la política aceptadas (CUMPLIMIENTO #6)
private const val SYNC_URL = "https://brivel.app/api/boveda"
private const val GARMIN_URL = "https://brivel.app/api/garmin"
private const val GARMIN_KEY = "garmin.key"  // en el almacén seguro mientras se conecta; después, en AppData.garminLink
private const val GARMIN_PUB = "garmin.pub"
private const val GARMIN_STATE = "garmin.state"
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

    val linkedAthlete: String? get() = data.intervalsAthlete?.takeIf { secrets.read(API_KEY) != null }

    private fun client(): IntervalsClient? {
        val athlete = data.intervalsAthlete ?: return null
        val key = secrets.read(API_KEY) ?: return null
        return IntervalsClient(athlete, key, httpEngine)
    }

    /** Vincula la cuenta una vez: comprueba la clave contra intervals.icu antes de guardarla. */
    @Throws(Exception::class)  // Swift recibe el error en vez de cerrarse la app
    suspend fun linkIntervals(athleteId: String, apiKey: String) {
        val id = athleteId.trim()
        val key = apiKey.trim()
        val probe = IntervalsClient(id, key, httpEngine)
        try {
            probe.athlete()
        } catch (e: IntervalsException) {
            throw IllegalArgumentException(
                if (e.status == 401 || e.status == 403) "intervals.icu no acepta ese ID de atleta o esa clave" else "intervals.icu respondió ${e.status}")
        } finally {
            probe.close()
        }
        secrets.write(API_KEY, key)
        save(data.copy(intervalsAthlete = id))
        sync()
    }

    fun unlinkIntervals() {
        secrets.write(API_KEY, null)
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

    /** Las sesiones de hoy (o solo la del deporte indicado) en el formato del reloj, para el relevo de Garmin. */
    fun watchWorkouts(sport: String? = null): List<WatchWorkout> {
        val planner = Planner(data, today())
        val day = planner.day(today())
        val items = day.items.filter { sport == null || it.sport == sport }
        check(items.isNotEmpty()) { "Hoy no hay sesiones que enviar" }
        val physio = planner.physiology ?: Profile(activeSports = listOf("running"))
        val date = day.date.toString()
        return items.map { item ->
            item.session?.let { watchWorkout(it, date, physio) }
                ?: watchStrength(strengthScreen(item, date, data.sets), "Brivel · ${item.title}").let { w ->
                    // Sin ejercicios que se apunten serie a serie (equilibrio): un paso abierto con la lista
                    if (w.blocks.isNotEmpty()) w else w.copy(blocks = listOf(WatchBlock(1,
                        listOf(WatchStep("active", seconds = item.durationMin * 60, note = item.hint.take(500))))))
                }
        }
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
                    item.healthStrength != null -> strengthEvent(item.healthStrength, day.date)
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
        day(date).items.firstOrNull { it.isStrength }?.let { strengthScreen(it, date, data.sets) }

    /** El día más reciente de la última semana (hoy incluido) con sesión de fuerza, o null. */
    fun lastStrengthDate(): String? = (0..6).map { today().plusDays(-it).toString() }.firstOrNull { strengthScreen(it) != null }

    /**
     * Apunta o corrige la serie `set` (desde 1) de un ejercicio; `reps` son segundos en los ejercicios por tiempo; `rir`:
     * las repeticiones que te quedaban (null: sin apuntar).
     */
    @Throws(Exception::class)
    fun logSet(date: String, exercise: String, set: Int, weightKg: Double?, reps: Int, rir: Int?) {
        require(date <= today().toString()) { "No se puede apuntar una sesión futura" }
        require(exercise.isNotBlank() && set in 1..20 && reps in 1..600 && (weightKg == null || weightKg in 0.0..500.0) &&
            (rir == null || rir in 0..10)) { "Revisa el peso y las repeticiones" }
        save(data.copy(sets = data.sets.filterNot { it.date == date && it.exercise == exercise && it.set == set } +
            SetLog(date, exercise, set, weightKg, reps, rir)))
    }

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
        store.write(DATA_KEY, null)
        forgetSync()
        data = AppData()
    }
}

/** La fecha de hoy en la zona horaria del móvil. */
@OptIn(kotlin.time.ExperimentalTime::class)
fun systemToday(): LocalDate = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
