package app.brivel.app

// Lenguaje llano primero, el término técnico al tocar (docs/VISION.md §4, principio 2).

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray

val SPORT_LABEL = mapOf("running" to "Carrera", "cycling" to "Bici", "swimming_pool" to "Natación",
    "swimming_open" to "Aguas abiertas", "walking" to "Caminar", "strength" to "Fuerza", "triathlon" to "Triatlón",
    "duathlon" to "Duatlón")

val WEEKDAY_SHORT = mapOf("MON" to "L", "TUE" to "M", "WED" to "X", "THU" to "J", "FRI" to "V", "SAT" to "S", "SUN" to "D")
val WEEKDAY_LABEL = mapOf("MON" to "Lunes", "TUE" to "Martes", "WED" to "Miércoles", "THU" to "Jueves",
    "FRI" to "Viernes", "SAT" to "Sábado", "SUN" to "Domingo")
val MONTH_LABEL = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre",
    "octubre", "noviembre", "diciembre")

/** Nombre llano y cómo saber si vas bien (prueba del habla). [Práctica: ACSM] */
val PLAIN_SESSION: Map<String, Pair<String, String>> = mapOf(
    "recovery" to ("Suave" to "Muy tranquilo: podrías ir cantando."),
    "aerobic" to ("Ritmo cómodo" to "Puedes mantener una conversación sin quedarte sin aire."),
    "long" to ("Larga a ritmo cómodo" to "Más tiempo que otros días, pero siempre pudiendo hablar."),
    "threshold" to ("Ritmo exigente" to "Constante y exigente: solo puedes decir frases cortas."),
    "vo2max" to ("Series intensas" to "En cada serie casi no puedes hablar; entre series, muy suave."),
    "tempo" to ("Ritmo de carrera larga" to "Firme pero controlado: frases cortas, como en una maratón."),
    "anaerobic" to ("Esfuerzos muy intensos" to "Series muy cortas a tope, con descanso largo entre ellas."),
    "neuromuscular" to ("Aceleraciones cortas" to "Arrancadas de pocos segundos con recuperación completa."),
    "race_specific" to ("Ritmo de tu carrera" to "Bloques al ritmo que quieres llevar el día de la prueba."),
    "brick" to ("Bici y carrera seguidas" to "Bici y, sin parar, a correr: acostumbra las piernas al cambio."),
    "test" to ("Test para fijar tus zonas" to "Al máximo que puedas sostener; sirve para calcular tus ritmos."),
    "strength" to ("Fuerza" to "Ejercicios de fuerza con el esfuerzo indicado en cada serie."),
)

/** Términos técnicos con su explicación (se muestran al tocar). */
val GLOSSARY: Map<String, String> = mapOf(
    "Forma física" to "Lo entrenado en las últimas 6 semanas (CTL). Sube despacio cuando entrenas con constancia.",
    "Fatiga" to "Lo entrenado en la última semana (ATL). Sube rápido tras días duros y baja al descansar.",
    "Forma" to "Forma física menos fatiga (TSB). Negativa: estás cargando; positiva: estás descansado para rendir.",
    "VFC" to "Variabilidad de la frecuencia cardíaca (HRV). Si se sale de tu rango normal, tu cuerpo está más cansado.",
    "FC en reposo" to "Pulsaciones en reposo. Si sube varios días seguidos, suele indicar cansancio o enfermedad.",
    "VO2max" to "El oxígeno máximo que puedes usar. Es una estimación de tu reloj; sube con el entrenamiento.",
)

/** Etiqueta de evidencia al final de un motivo ("… [Verificado]") separada del texto. */
fun splitEvidence(text: String): Pair<String, String?> {
    val m = Regex("""^(.*?)\s*\[(Verificado|Práctica|Inferencia)\]$""").find(text) ?: return text to null
    return m.groupValues[1] to m.groupValues[2]
}

val EVIDENCE_MEANING = mapOf(
    "Verificado" to "Lo respaldan estudios publicados.",
    "Práctica" to "Práctica habitual de entrenadores, sin un ensayo que la compare con otras.",
    "Inferencia" to "Regla propia razonada a partir de las fuentes; aún sin comprobar directamente.",
)

fun goalLabel(goal: UserGoal): String = when (goal.kind) {
    GoalKind.RACE -> goal.race?.name ?: "Carrera"
    GoalKind.FITNESS -> "Ponerme en forma"
    GoalKind.STRENGTH -> "Ganar fuerza y músculo"
    GoalKind.HEALTH -> "Mejorar mi salud a largo plazo"
}

/**
 * Título de una actividad según lo que fue de verdad, por su tiempo en las 7 zonas de FC de intervals.icu
 * (Z1, Z2, Z3, Z4, Z5a, Z5b, Z5c), en vez del nombre automático del reloj. [Inferencia: umbrales propios]
 */
fun activityTitle(activity: JsonObject): String? {
    val sport = app.brivel.intervals.HEALTH_ACTIVITY_SPORTS[
        (activity["type"] as? JsonPrimitive)?.content] ?: return null
    val label = SPORT_LABEL[sport] ?: return null
    if (sport == "strength") return "$label · Fuerza"
    val zones = (activity["icu_hr_zone_times"] as? kotlinx.serialization.json.JsonArray)
        ?.map { (it as? JsonPrimitive)?.doubleOrNull ?: 0.0 } ?: return null
    val total = zones.sum()
    if (total <= 0 || zones.size < 7) return null
    val f = zones.map { it / total }
    val minutes = ((activity["moving_time"] as? JsonPrimitive)?.doubleOrNull ?: 0.0) / 60
    val kind = when {
        f[5] + f[6] >= 0.05 && f[3] + f[4] + f[5] + f[6] >= 0.15 -> "Series muy intensas"
        f[4] >= 0.10 -> "Series VO2max"
        f[3] >= 0.20 -> "Umbral"
        f[2] >= 0.25 -> "Tempo (Z3)"
        minutes >= 90 && f[0] + f[1] >= 0.8 -> "Larga a ritmo cómodo (Z2)"
        f[0] >= 0.7 && minutes < 50 -> "Recuperación (Z1)"
        else -> "Ritmo cómodo (Z2)"
    }
    return "$label · $kind"
}

/** Aviso de salud: sustituye al cuestionario de cribado (decisión del 2026-10-21, docs/CUMPLIMIENTO.md). */
const val HEALTH_NOTICE = "Brivel no es un servicio médico ni sustituye el consejo de un profesional sanitario. " +
    "Consulta a tu médico antes de empezar o de subir la intensidad si tienes una enfermedad del corazón, metabólica " +
    "(como la diabetes) o del riñón, si estás embarazada o tienes una lesión. Si al entrenar notas dolor en el pecho, " +
    "falta de aire con poco esfuerzo, mareos o palpitaciones, para y consulta. Al continuar confirmas que entrenas bajo " +
    "tu responsabilidad."

const val MIN_AGE = 14  // LOPDGDD art. 7: edad mínima para consentir en España (CUMPLIMIENTO #17)

// Desde 1900: un año a medio teclear («19») no es una edad
fun ageAllowed(birthYear: Int, currentYear: Int): Boolean = birthYear >= 1900 && currentYear - birthYear >= MIN_AGE

const val PRIVACY_NOTE = "Tus datos se guardan en este móvil. Solo si activas «Usar en otros dispositivos», Brivel guarda " +
    "además una copia cifrada que solo tus dispositivos pueden leer. Si vinculas intervals.icu, la app habla directamente " +
    "con intervals.icu con tu clave, que se guarda cifrada en el almacén seguro del sistema."

private val CONFLICT_LABEL = mapOf("profile" to "tu perfil", "goal" to "tu objetivo", "weekChanges" to "los cambios de la semana",
    "readiness" to "cómo estabas un día", "choices" to "el deporte elegido un día", "intervalsAthlete" to "el atleta de intervals.icu",
    "reminderHour" to "el recordatorio")

/** Aviso tras sincronizar cuando los dos dispositivos cambiaron lo mismo (sync/Merge.kt): gana el del servidor. */
fun conflictMessage(conflicts: Set<String>): String? = conflicts.takeIf { it.isNotEmpty() }
    ?.joinToString(", ") { CONFLICT_LABEL[it] ?: it }
    ?.let { "Cambiaste $it aquí y en otro dispositivo a la vez: se ha quedado lo del otro." }

/** Versión de la política de privacidad que se acepta al activar la sincronización (web/public/privacidad.html). */
const val PRIVACY_POLICY_VERSION = "2026-11-02.2"

// Sincronización (docs/SINCRONIZACION.md): los mismos textos que web/src/Sync.tsx
const val SYNC_INTRO = "Opcional. Sin activarlo, tus datos siguen solo en este móvil."
const val SYNC_ACTIVE = "Una copia cifrada en el servidor que solo tus dispositivos pueden leer."
const val SYNC_CONSENT = "Acepto que Brivel guarde en su servidor una copia cifrada de mis datos, también los de salud, para usarlos " +
    "en mis dispositivos. Puedo retirarlo cuando quiera borrando la copia. Detalles en brivel.app/privacidad."
const val SYNC_AGE = "Tengo 14 años o más"
const val SYNC_NO_RECOVERY = "Nadie puede recuperar el código. Si lo pierdes y no te queda ningún dispositivo con Brivel, la copia " +
    "se pierde; los datos de cada dispositivo siguen en él."
const val SYNC_JOIN_NOTE = "Se juntan los datos de este móvil con los de la copia."

// Garmin Connect (política de privacidad, apartado 5): los mismos textos que web/src/Garmin.tsx
const val GARMIN_POLICY_VERSION = "2026-11-03.4"
const val GARMIN_TITLE = "Garmin Connect™"
const val GARMIN_INTRO = "Opcional y en pruebas. Tus actividades, sueño, pasos, pulso en reposo y variabilidad cardiaca del reloj, " +
    "para adaptar tu plan."
const val GARMIN_HOW = "Garmin manda tus datos al servidor de Brivel, que los cifra al llegar para tus dispositivos y los borra en " +
    "cuanto uno los descarga (o a los 30 días). Brivel no puede leerlos ni los vende. Si sincronizas, la conexión vale para " +
    "todos tus dispositivos."
const val GARMIN_CONSENT = "Acepto que Brivel reciba mis datos de Garmin, también los de salud, para adaptar mi plan. Puedo retirarlo " +
    "cuando quiera desconectando. Detalles en brivel.app/privacidad."
const val GARMIN_DISCONNECT = "Se desconecta en todos tus dispositivos: el servidor borra al momento lo pendiente y deja de recibir tus datos. " +
    "Lo ya descargado se queda."

/** Lo que enseña la tarjeta de Garmin conectada: los datos llegan solos al abrir la app o al deslizar hacia abajo. */
fun garminStatus(syncedAt: String?): String =
    (if (syncedAt != null) "Conectado. Último envío: ${dateLabel(syncedAt)}." else "Conectado. Aún no ha llegado nada: sincroniza el reloj.") +
        " Se actualiza solo al abrir Brivel o al deslizar hacia abajo."

// Enlaces que la app tiene que tener a mano (CUMPLIMIENTO #1, #2 y #19: la AGPL pide ofrecer el código)
const val PRIVACY_URL = "https://brivel.app/privacidad"
const val TERMS_URL = "https://brivel.app/terminos"
const val SOURCE_URL = "https://github.com/brivelapp/brivel"

// Perfil (lienzo de Pulso, opción B, 2026-11-03): caminar no se elige; siempre cuenta y es el plan si no hay otro deporte
val PICKABLE_CARDIO = listOf("running", "cycling", "swimming_pool")
const val MAX_DAY_MINUTES = 300  // 5 h: las tiradas largas de bici del domingo

/** Marca o desmarca un deporte. Sin ninguno queda caminar: el plan necesita al menos uno (Planner.kt). */
fun toggleCardio(cardio: List<String>, sport: String): List<String> {
    val picked = cardio.filter { it in PICKABLE_CARDIO }
    return (if (sport in picked) picked - sport else picked + sport).ifEmpty { listOf("walking") }
}

/** El tiempo de la semana: «45 min», «2 h», «4 h 45 min». */
fun weekTimeText(days: Map<String, Int>): String {
    val m = days.values.sum()
    return when {
        m < 60 -> "$m min"
        m % 60 == 0 -> "${m / 60} h"
        else -> "${m / 60} h ${m % 60} min"
    }
}
