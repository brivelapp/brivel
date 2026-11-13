package app.brivel.app

import kotlinx.serialization.Serializable

// Biblioteca de ejercicios: la ficha de cada uno (patrón, material y claves de técnica), buscar y filtrar, y cambiar un
// ejercicio del plan por otro del mismo patrón. Los ejercicios: LIBRARY (ExerciseCatalog.kt) y los propios del usuario.

/** Nombre del patrón para la pantalla. */
val PATTERN_LABEL = mapOf(
    "sentadilla" to "Sentadilla", "bisagra" to "Bisagra de cadera", "zancada" to "Zancada", "gemelo" to "Gemelo",
    "salto" to "Saltos", "empuje horizontal" to "Empuje horizontal", "empuje vertical" to "Empuje vertical",
    "tracción horizontal" to "Tracción horizontal", "tracción vertical" to "Tracción vertical", "core" to "Core",
    "brazos" to "Brazos", "hombros" to "Hombros (aislamiento)", "pierna aislada" to "Pierna (aislamiento)",
    "acondicionamiento" to "Cuerpo completo y cardio", "movilidad" to "Movilidad y estiramientos", "yoga" to "Yoga y pilates",
)

/**
 * Claves de técnica por patrón, las mismas para todos sus ejercicios. Pautas generales, no individualizadas.
 * [Práctica: pautas de técnica de la NSCA; Haff y Triplett, Essentials of Strength Training and Conditioning, 2016]
 */
val PATTERN_CUES = mapOf(
    "sentadilla" to listOf("Pies a la anchura de los hombros, con las puntas algo abiertas",
        "Las rodillas siguen la dirección de los pies", "Espalda neutra: baja solo hasta donde la mantengas"),
    "bisagra" to listOf("Lleva la cadera atrás con las rodillas un poco dobladas",
        "Espalda neutra y el peso cerca de las piernas", "Sube apretando los glúteos, sin arquear la zona lumbar"),
    "zancada" to listOf("La rodilla de delante, encima del pie", "Tronco erguido: baja en vertical",
        "Empuja con todo el pie de delante para subir"),
    "gemelo" to listOf("Sube despacio hasta la punta del pie", "Para un momento arriba y baja controlando",
        "Rodilla estirada de pie; doblada sentado"),
    "salto" to listOf("Aterriza suave, con las rodillas en la línea de los pies",
        "Calidad antes que cantidad: para si la técnica empeora", "Descansa del todo entre series"),
    "empuje horizontal" to listOf("Escápulas atrás y abajo", "Codos a unos 45° del cuerpo",
        "En las flexiones, el cuerpo en línea de la cabeza a los talones"),
    "empuje vertical" to listOf("Abdomen y glúteos firmes, sin arquear la espalda", "Empuja en vertical, cerca de la cara",
        "Baja controlando hasta la altura de los hombros"),
    "tracción horizontal" to listOf("Espalda neutra y quieta", "Tira llevando el codo hacia la cadera",
        "Junta las escápulas al final del movimiento"),
    "tracción vertical" to listOf("Baja los hombros antes de tirar", "Lleva los codos hacia las costillas",
        "Baja controlando hasta estirar los brazos"),
    "core" to listOf("Columna neutra: sin hundir ni subir la cadera", "Respira sin soltar el abdomen",
        "Para cuando pierdas la posición"),
)

/** Material que pide cada ejercicio, con las etiquetas del filtro (`EQUIPMENT`). */
private val EXERCISE_EQUIPMENT = mapOf(
    "Sentadilla copa (goblet) o con barra" to listOf("mancuernas", "barra"),
    "Sentadilla (a una pierna al progresar)" to listOf("peso corporal"),
    "Sentadilla con barra" to listOf("barra"),
    "Peso muerto rumano" to listOf("barra", "mancuernas"),
    "Peso muerto rumano a una pierna" to listOf("mancuernas"),
    "Puente de glúteo (a una pierna al progresar)" to listOf("peso corporal"),
    "Puente de glúteo a una pierna" to listOf("peso corporal"),
    "Empuje de cadera con barra (hip thrust)" to listOf("barra", "banco o cajón"),
    "Zancada con mancuernas" to listOf("mancuernas"),
    "Zancada atrás" to listOf("peso corporal"),
    "Zancada búlgara con mancuernas" to listOf("mancuernas", "banco o cajón"),
    "Sentadilla búlgara (progresión a sentadilla a una pierna)" to listOf("peso corporal", "banco o cajón"),
    "Subida a cajón alto" to listOf("banco o cajón"),
    "Elevación de gemelo de pie y sentado" to listOf("peso corporal"),
    "Gemelo a una pierna en escalón" to listOf("peso corporal"),
    "Saltos al cajón" to listOf("banco o cajón"),
    "Saltos de caída desde poca altura (drop jumps)" to listOf("banco o cajón"),
    "Saltos de tobillo seguidos (pogo)" to listOf("peso corporal"),
    "Saltos de rana" to listOf("peso corporal"),
    "Press de banca con mancuernas" to listOf("mancuernas", "banco o cajón"),
    "Flexiones (inclinadas al empezar)" to listOf("peso corporal"),
    "Flexiones" to listOf("peso corporal"),
    "Press militar con mancuernas" to listOf("mancuernas"),
    "Flexiones en pica" to listOf("peso corporal"),
    "Remo con mancuerna" to listOf("mancuernas", "banco o cajón"),
    "Remo invertido bajo una mesa firme" to listOf("peso corporal"),
    "Remo invertido" to listOf("barra"),
    "Tirón a la cara con polea (face pull)" to listOf("polea o máquina"),
    "Rotación externa con goma" to listOf("goma"),
    "Jalón al pecho" to listOf("polea o máquina"),
    "Jalón al pecho o dominadas lastradas" to listOf("polea o máquina", "barra de dominadas"),
    "Dominadas asistidas con goma" to listOf("barra de dominadas", "goma"),
    "Dominadas o negativas" to listOf("barra de dominadas"),
    "Plancha frontal y lateral" to listOf("peso corporal"),
    "Plancha frontal" to listOf("peso corporal"),
    "Plancha lateral" to listOf("peso corporal"),
    "Plancha de Copenhague" to listOf("banco o cajón"),
    "Bicho muerto (dead bug)" to listOf("peso corporal"),
    "Press Pallof antirrotación" to listOf("goma", "polea o máquina"),
)

/** Las etiquetas del filtro de material, en orden. */
val EQUIPMENT = listOf("peso corporal", "mancuernas", "barra", "banco o cajón", "polea o máquina", "goma", "barra de dominadas", "pesa rusa",
    "balón medicinal", "fitball", "suspensión o anillas", "comba", "rodillo de espuma", "otro material")

/**
 * La ficha de un ejercicio. `figure`: el patrón cuya figura lo dibuja (FIGURES). `zone`, `muscles` y `description` salen de
 * reglas sobre el patrón, el material y el nombre (ExerciseGuide.kt).
 */
@Serializable
data class ExerciseCard(val name: String, val pattern: String, val patternLabel: String, val equipment: List<String>,
    val timed: Boolean, val cues: List<String>, val cueSource: String, val figure: String,
    val zone: String = "", val muscles: List<String> = emptyList(), val description: String = "")

const val CUE_SOURCE = "Pautas generales de técnica de la NSCA (Haff y Triplett, 2016). No sustituyen a un entrenador."

const val NO_CUES = "Aún sin claves de técnica: no tenemos una fuente verificada para este tipo de ejercicio."

/** Un ejercicio creado por el usuario: el nombre lo identifica (las series apuntadas guardan el nombre). */
@Serializable
data class CustomExercise(val name: String, val pattern: String, val timed: Boolean = false)

/** Patrón y medida de un ejercicio, de la biblioteca o de los propios. */
fun exerciseInfo(name: String, custom: List<CustomExercise>): ExerciseInfo? =
    LIBRARY[name] ?: custom.firstOrNull { it.name == name }?.let { ExerciseInfo(it.pattern, it.timed) }

private val CARDS = mutableMapOf<String, ExerciseCard>()  // las fichas de la biblioteca no cambian: se calculan una vez

fun exerciseCard(name: String, custom: List<CustomExercise> = emptyList()): ExerciseCard? =
    if (name in LIBRARY) CARDS.getOrPut(name) { buildCard(name, custom)!! } else buildCard(name, custom)

private fun buildCard(name: String, custom: List<CustomExercise>): ExerciseCard? = exerciseInfo(name, custom)?.let { info ->
    val cues = PATTERN_CUES[info.pattern]
    ExerciseCard(name, info.pattern, PATTERN_LABEL[info.pattern]!!, EXERCISE_EQUIPMENT[name] ?: CATALOG_EQUIPMENT[name] ?: emptyList(), info.timed,
        cues ?: emptyList(), if (cues != null) CUE_SOURCE else NO_CUES, info.pattern)
        .let { c -> c.copy(zone = PATTERN_ZONE[c.pattern] ?: "", muscles = muscles(name, c.pattern),
            description = describeExercise(name, c.pattern, c.equipment, c.timed)) }
}

/** Sin tildes ni mayúsculas, para buscar «busqueda» y encontrar «búsqueda». */
internal fun plain(s: String) = s.lowercase().map { c -> "áàäâéèëêíìïîóòöôúùüûñ".indexOf(c).let { if (it < 0) c else "aaaaeeeeiiiioooouuuun"[it] } }
    .joinToString("")

/** Comprueba un ejercicio nuevo; devuelve el mismo con el nombre limpio. El «:» separa el nombre de la dosis en el plan. */
fun validCustomExercise(e: CustomExercise, custom: List<CustomExercise>): CustomExercise {
    val name = e.name.trim()
    require(name.length in 2..60) { "El nombre va de 2 a 60 letras" }
    require(':' !in name) { "El nombre no puede llevar dos puntos" }
    require((LIBRARY.keys + custom.map { it.name }).none { plain(it) == plain(name) }) { "Ya hay un ejercicio con ese nombre" }
    require(e.pattern in PATTERN_LABEL) { "Tipo de ejercicio no válido" }
    return e.copy(name = name)
}

/** Las zonas del cuerpo, en el orden del filtro. */
val ZONES = listOf("pierna", "pecho", "espalda", "hombro", "brazos", "core", "cuerpo completo", "movilidad")

/** Si el ejercicio se puede hacer con ese material; el peso corporal siempre se tiene. */
fun doable(card: ExerciseCard, owned: Collection<String>): Boolean = card.equipment.all { it == "peso corporal" || it in owned }

/**
 * Buscar por nombre, patrón o zona y filtrar por patrón, por material y por zona; primero los propios y luego por patrón.
 * `owned`: solo lo que se puede hacer con ese material (null: todo).
 */
fun library(query: String = "", pattern: String? = null, equipment: String? = null, custom: List<CustomExercise> = emptyList(),
            zone: String? = null, owned: Collection<String>? = null): List<ExerciseCard> {
    val words = plain(query).split(' ').filter { it.isNotBlank() }
    return (custom.map { it.name } + LIBRARY.keys).mapNotNull { exerciseCard(it, custom) }.filter { c ->
        (pattern == null || c.pattern == pattern) && (equipment == null || equipment in c.equipment) &&
            (zone == null || c.zone == zone) && (owned == null || doable(c, owned)) &&
            words.all { w -> plain(c.name).contains(w) || plain(c.patternLabel).contains(w) || plain(c.zone).contains(w) }
    }
}

/** Por qué otros se puede cambiar: mismo patrón y misma forma de medirlo (repeticiones o tiempo). */
fun swapOptions(name: String, custom: List<CustomExercise> = emptyList()): List<ExerciseCard> {
    val info = exerciseInfo(name, custom) ?: return emptyList()
    return library(pattern = info.pattern, custom = custom).filter { it.name != name && it.timed == info.timed }
}

/** Desde `since` (incluido), `original` se hace como `replacement`; null: vuelve al original. */
@Serializable
data class ExerciseSwap(val since: String, val original: String, val replacement: String?)

/** El ejercicio que toca ese día en lugar de `original`: el último cambio con fecha hasta ese día. */
fun swapped(original: String, swaps: List<ExerciseSwap>, date: String): String =
    swaps.filter { it.original == original && it.since <= date }.maxByOrNull { it.since }?.replacement ?: original

/** Las líneas del plan con los cambios de ese día ("Sentadilla con barra: 3 × 8–12" → "Zancada…: 3 × 8–12"). */
fun swapLines(lines: List<String>, swaps: List<ExerciseSwap>, date: String): List<String> = lines.map { line ->
    val name = exerciseName(line)
    val to = swapped(name, swaps, date)
    if (to == name) line else to + line.removePrefix(name)
}
