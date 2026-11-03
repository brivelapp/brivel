package app.brivel.app

// Lo que la app guarda en el propio móvil (docs/VISION.md §7): sin servidor ni cuentas. Los datos de salud no salen del
// dispositivo; la clave de intervals.icu va aparte, en el almacén seguro del sistema (Keystore o Llavero).

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Almacén de texto clave → valor de cada plataforma (SharedPreferences en Android, UserDefaults en iOS). */
interface KeyValueStore {
    fun read(key: String): String?
    fun write(key: String, value: String?)
}

/** Qué quiere conseguir el usuario. El objetivo vive fuera del perfil y siempre se puede cambiar (VISION §5). */
enum class GoalKind { RACE, FITNESS, STRENGTH, HEALTH }

@Serializable
data class RaceInfo(
    val name: String,
    val sport: String,  // running / cycling / swimming_pool / swimming_open / triathlon / duathlon
    val date: String,  // AAAA-MM-DD
    val priority: String = "A",
    val distanceKm: Double? = null,
    val trail: Boolean = false,
)

@Serializable
data class UserGoal(
    val kind: GoalKind,
    val race: RaceInfo? = null,
    val since: String,
    val otherRaces: List<RaceInfo> = emptyList(),  // preparatorias (B o C) antes de la principal
)

@Serializable
data class UserProfile(
    val birthYear: Int? = null,
    val level: String = "beginner",  // beginner / intermediate / advanced
    val strength: String = "bodyweight",  // gym / bodyweight / none
    val cardio: List<String> = listOf("walking"),  // walking / running / cycling / swimming_pool
    val days: Map<String, Int> = mapOf("MON" to 45, "WED" to 45, "SAT" to 60),  // día → minutos disponibles
    val renameActivities: Boolean = true,  // poner a las actividades de intervals.icu un título que diga lo que fueron
    val screening: app.brivel.engine.Screening? = null,  // cuestionario de salud del ACSM
)

@Serializable
data class ManualDone(val date: String, val sport: String)

/** Lo levantado en un ejercicio un día: peso (null con tu peso corporal), repeticiones por serie y series. Formato v2. */
@Serializable
data class LiftLog(val date: String, val exercise: String, val weightKg: Double?, val reps: Int, val sets: Int,
    val rir: Int? = null)  // rir: solo en el resumen del día (daySummaries), la menor reserva apuntada

/**
 * Una serie de fuerza: peso (null con tu peso corporal) y repeticiones, o segundos en los ejercicios por tiempo; `rir`:
 * las repeticiones que te quedaban, si lo apuntaste.
 */
@Serializable
data class SetLog(val date: String, val exercise: String, val set: Int, val weightKg: Double?, val reps: Int, val rir: Int? = null)

/** Cambios de una semana (acumulados) desde `since`; `earlier`: los anteriores de esa semana, para que lo pasado no cambie. */
@Serializable
data class WeekChange(val changes: Map<String, Int>, val since: String, val earlier: List<WeekChange> = emptyList()) {
    fun steps(): List<Pair<Map<String, Int>, kotlinx.datetime.LocalDate>> =
        (earlier + copy(earlier = emptyList())).map { it.changes to kotlinx.datetime.LocalDate.parse(it.since) }
}

@Serializable
data class Readiness(val sleep: Int, val energy: Int, val soreness: Int)

/** Copia local de lo descargado de intervals.icu: Progreso y el plan funcionan sin conexión. */
@Serializable
data class IntervalsCache(
    val athlete: JsonObject,
    val wellness: List<JsonObject>,
    val activities: List<JsonObject>,
    val syncedAt: String,
)

/**
 * La conexión con Garmin (política de privacidad, apartado 5): la clave X25519 con la que el relevo cifra cada envío y
 * el token del buzón, en base64url. Va en los datos para que la sincronización la lleve, cifrada de extremo a extremo,
 * a los demás dispositivos de la misma sesión: cualquiera de ellos descarga y abre el buzón. No sale al exportar.
 */
@Serializable
data class GarminLink(val privateKey: String, val publicKey: String, val token: String, val since: String)

/** Versión del formato guardado: al cambiarlo, `migrate` adapta los datos antiguos (sin servidor no hay migraciones). */
const val SCHEMA = 4

/** Campo nuevo: añádelo también a `merge` (sync/Merge.kt), o un cambio de otro dispositivo pisará el de este. */
@Serializable
data class AppData(
    val schema: Int = SCHEMA,
    val profile: UserProfile? = null,
    val goal: UserGoal? = null,
    val blockStart: String? = null,  // inicio del primer bloque de 4 semanas (lunes)
    val manualDone: List<ManualDone> = emptyList(),
    val weekChanges: Map<String, WeekChange> = emptyMap(),  // lunes → cambios de esa semana
    val readiness: Map<String, Readiness> = emptyMap(),  // fecha → respuestas del día
    val choices: Map<String, Map<String, String>> = emptyMap(),  // fecha → deporte planificado → deporte elegido
    val renamed: Set<String> = emptySet(),  // actividades ya renombradas en intervals.icu
    val cache: IntervalsCache? = null,
    val intervalsAthlete: String? = null,  // ID del atleta (no es secreto); la clave va en el almacén seguro
    val health: HealthSnapshot? = null,  // Health Connect o Apple Salud, leídos en el propio móvil
    val garmin: HealthSnapshot? = null,  // Garmin Connect, por el relevo cifrado (se acumula: cada envío trae una parte)
    val garminLink: GarminLink? = null,  // conexión con Garmin, compartida por la sincronización
    val lifts: List<LiftLog> = emptyList(),  // formato v2 (un resumen por ejercicio y día): `migrate` lo pasa a `sets`
    val sets: List<SetLog> = emptyList(),  // registro de fuerza, serie a serie
    val reminderHour: Int? = null,  // recordatorio diario a esa hora; null = apagado (se activa a propósito)
)

/**
 * Adapta datos guardados con un formato anterior. v1 → v2: añade los datos de salud del móvil (vacíos). v2 → v3: cada
 * resumen de `lifts` pasa a una serie por cada una que apuntó. También con v3: un móvil sin actualizar aún sincroniza lifts.
 * v3 → v4: añade `garmin` (vacío). Sube para que un dispositivo sin actualizar no sincronice: descartaría `garmin` al
 * leerlo (ignoreUnknownKeys) y lo borraría también en los demás al subir su copia.
 */
fun migrate(data: AppData): AppData = when {
    data.schema >= SCHEMA && data.lifts.isEmpty() -> data
    else -> {
        val logged = data.sets.map { it.date to it.exercise }.toSet()
        val converted = data.lifts.filter { (it.date to it.exercise) !in logged }
            .flatMap { l -> (1..l.sets).map { SetLog(l.date, l.exercise, it, l.weightKg, l.reps) } }
        data.copy(schema = SCHEMA, lifts = emptyList(), sets = data.sets + converted)
    }
}
