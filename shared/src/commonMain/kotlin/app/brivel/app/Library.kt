package app.brivel.app

import kotlinx.serialization.Serializable

// Biblioteca de ejercicios: la ficha de cada uno (patrón, material y claves de técnica), buscar y filtrar, y cambiar un
// ejercicio del plan por otro del mismo patrón. Solo los ejercicios que proponen los motores (EXERCISES).

/** Nombre del patrón para la pantalla. */
val PATTERN_LABEL = mapOf(
    "sentadilla" to "Sentadilla", "bisagra" to "Bisagra de cadera", "zancada" to "Zancada", "gemelo" to "Gemelo",
    "salto" to "Saltos", "empuje horizontal" to "Empuje horizontal", "empuje vertical" to "Empuje vertical",
    "tracción horizontal" to "Tracción horizontal", "tracción vertical" to "Tracción vertical", "core" to "Core",
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
val EQUIPMENT = listOf("peso corporal", "mancuernas", "barra", "banco o cajón", "polea o máquina", "goma", "barra de dominadas")

/** La ficha de un ejercicio. `figure`: el patrón cuya figura lo dibuja (FIGURES). */
@Serializable
data class ExerciseCard(val name: String, val pattern: String, val patternLabel: String, val equipment: List<String>,
    val timed: Boolean, val cues: List<String>, val cueSource: String, val figure: String)

const val CUE_SOURCE = "Pautas generales de técnica de la NSCA (Haff y Triplett, 2016). No sustituyen a un entrenador."

fun exerciseCard(name: String): ExerciseCard? = EXERCISES[name]?.let { info ->
    ExerciseCard(name, info.pattern, PATTERN_LABEL[info.pattern]!!, EXERCISE_EQUIPMENT[name] ?: emptyList(), info.timed,
        PATTERN_CUES[info.pattern]!!, CUE_SOURCE, info.pattern)
}

/** Sin tildes ni mayúsculas, para buscar «busqueda» y encontrar «búsqueda». */
private fun plain(s: String) = s.lowercase().map { c -> "áàäâéèëêíìïîóòöôúùüûñ".indexOf(c).let { if (it < 0) c else "aaaaeeeeiiiioooouuuun"[it] } }
    .joinToString("")

/** Buscar por nombre o patrón y filtrar por patrón y por material; en el orden de EXERCISES (por patrón). */
fun library(query: String = "", pattern: String? = null, equipment: String? = null): List<ExerciseCard> {
    val words = plain(query).split(' ').filter { it.isNotBlank() }
    return EXERCISES.keys.mapNotNull(::exerciseCard).filter { c ->
        (pattern == null || c.pattern == pattern) && (equipment == null || equipment in c.equipment) &&
            words.all { w -> plain(c.name).contains(w) || plain(c.patternLabel).contains(w) }
    }
}

/** Por qué otros se puede cambiar: mismo patrón y misma forma de medirlo (repeticiones o tiempo). */
fun swapOptions(name: String): List<ExerciseCard> {
    val info = EXERCISES[name] ?: return emptyList()
    return library(pattern = info.pattern).filter { it.name != name && it.timed == info.timed }
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
