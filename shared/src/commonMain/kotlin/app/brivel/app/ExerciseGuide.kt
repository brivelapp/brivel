package app.brivel.app

import kotlinx.datetime.LocalDate

// Clasificación, descripción y rotación de los ejercicios de la biblioteca. Todo sale del patrón, el material y el nombre
// del ejercicio con reglas: no hay un texto escrito a mano por ejercicio. [Inferencia: anatomía funcional general]

/** Zona del cuerpo de cada patrón: lo que se busca al decir «hoy toca pierna» o «espalda». */
val PATTERN_ZONE = mapOf(
    "sentadilla" to "pierna", "bisagra" to "pierna", "zancada" to "pierna", "gemelo" to "pierna", "salto" to "pierna",
    "pierna aislada" to "pierna", "empuje horizontal" to "pecho", "empuje vertical" to "hombro", "hombros" to "hombro",
    "tracción horizontal" to "espalda", "tracción vertical" to "espalda", "brazos" to "brazos", "core" to "core",
    "acondicionamiento" to "cuerpo completo", "movilidad" to "movilidad", "yoga" to "movilidad",
)

private val PATTERN_MUSCLES = mapOf(
    "sentadilla" to listOf("cuádriceps", "glúteos"), "bisagra" to listOf("isquiotibiales", "glúteos", "lumbares"),
    "zancada" to listOf("cuádriceps", "glúteos"), "gemelo" to listOf("gemelos", "sóleo"),
    "salto" to listOf("cuádriceps", "glúteos", "gemelos"), "empuje horizontal" to listOf("pectoral", "tríceps", "deltoides anterior"),
    "empuje vertical" to listOf("deltoides", "tríceps"), "tracción horizontal" to listOf("dorsal", "romboides y trapecio", "bíceps"),
    "tracción vertical" to listOf("dorsal", "bíceps"), "core" to listOf("abdominales", "oblicuos"),
    "acondicionamiento" to listOf("cuerpo completo"), "movilidad" to listOf("movilidad"), "yoga" to listOf("flexibilidad", "equilibrio"),
)

/** Los músculos que más trabaja un ejercicio: los del patrón, afinados por lo que dice el nombre. */
fun muscles(name: String, pattern: String): List<String> {
    val n = plain(name)
    fun has(vararg words: String) = words.any { it in n }
    return when (pattern) {
        "gemelo" -> if (has("tibial", "puntas")) listOf("tibial anterior") else PATTERN_MUSCLES[pattern]!!
        "core" -> if (has("superman", "extension de espalda", "nadador", "arco boca abajo")) listOf("lumbares", "glúteos")
            else if (has("paseo", "sostener")) listOf("abdominales", "antebrazos", "trapecio") else PATTERN_MUSCLES[pattern]!!
        "brazos" -> when {
            has("muneca", "dedos", "pinza", "pronacion", "supinacion", "rodillo") -> listOf("antebrazos")
            has("curl") -> listOf("bíceps", "braquial")
            else -> listOf("tríceps")
        }
        "hombros" -> when {
            has("cuello") -> listOf("cuello")
            has("encogimiento") -> listOf("trapecio")
            has("rotacion", "deslizamiento") -> listOf("manguito rotador")
            has("pajaro", "en y", "en t", "en w", "en i", "en l") -> listOf("deltoides posterior", "trapecio medio e inferior")
            has("remo al menton") -> listOf("deltoides", "trapecio")
            else -> listOf("deltoides")
        }
        "pierna aislada" -> when {
            has("nordico inverso", "extension de cuadriceps") -> listOf("cuádriceps")
            has("curl") -> listOf("isquiotibiales")
            has("aduc", "interior") -> listOf("aductores")
            has("flexion de cadera") -> listOf("flexores de cadera")
            else -> listOf("glúteo medio")
        }
        else -> PATTERN_MUSCLES[pattern] ?: emptyList()
    }
}

private val PATTERN_WHAT = mapOf(
    "sentadilla" to "Doblas y estiras rodillas y cadera a la vez, con el tronco erguido",
    "bisagra" to "Llevas la cadera atrás y la extiendes con la espalda firme; las rodillas se doblan poco",
    "zancada" to "Una pierna delante y otra detrás, o una sola pierna de apoyo, bajando y subiendo con control",
    "gemelo" to "Subes y bajas el talón con la rodilla quieta",
    "salto" to "Un impulso rápido desde el suelo y una caída suave: pocas repeticiones y todas de calidad",
    "empuje horizontal" to "Empujas la carga hacia delante desde el pecho",
    "empuje vertical" to "Empujas la carga por encima de la cabeza",
    "tracción horizontal" to "Tiras de la carga hacia el tronco juntando las escápulas",
    "tracción vertical" to "Tiras de arriba abajo, o subes tu cuerpo hacia la barra",
    "core" to "El tronco aguanta o mueve la carga sin que se hunda ni se arquee la zona lumbar",
    "brazos" to "Solo se mueve el codo o la muñeca: el resto del cuerpo queda quieto",
    "hombros" to "Mueves el brazo desde el hombro con poca carga y sin impulso",
    "pierna aislada" to "Se mueve una sola articulación de la pierna, con el resto del cuerpo apoyado",
    "acondicionamiento" to "Movimiento de todo el cuerpo que sube el pulso",
    "movilidad" to "Movimiento suave o postura mantenida para ganar recorrido, sin llegar al dolor",
    "yoga" to "Postura o secuencia que se hace despacio, acompasada con la respiración",
)

// Ejercicios que están en un patrón por lo que trabajan, pero no se mueven como dice su frase: van sin ella
private val ODD_ONES = mapOf(
    "sentadilla" to listOf("lanzamiento"),
    "bisagra" to listOf("cargada", "arrancada", "tiron alto", "encogimiento", "hiperextension", "extension de espalda", "puente",
        "empuje de cadera", "patada", "curl nordico", "elevacion de gluteo", "elevacion de cadera", "extension de cadera"),
    "empuje horizontal" to listOf("jersey", "aperturas", "cruce", "contractora", "fondos", "pase de pecho"),
    "empuje vertical" to listOf("cargada", "envion", "dos tiempos", "thruster", "sentadilla y press", "pino", "caminata", "curl"),
    "traccion horizontal" to listOf("rotacion externa", "separacion", "retraccion", "peso muerto"),
    "traccion vertical" to listOf("jersey", "colgarse", "trepa", "brazos rectos"),
    "core" to listOf("paseo", "sostener", "gusano", "levantamiento turco", "molino"),
    "acondicionamiento" to listOf("lanzamiento", "golpe"),
)

// Lo que añade cada variante; el orden es el de la frase
private val VARIANT_NOTES = listOf(
    listOf("a una pierna", "a un brazo", "alterno", "alterna") to "Trabaja un lado cada vez: empieza por el más débil y haz lo mismo con los dos",
    listOf("inclinado", "inclinada") to "La inclinación cambia el ángulo y la parte del músculo que más trabaja",
    listOf("declinado", "declinada") to "La declinación cambia el ángulo y la parte del músculo que más trabaja",
    listOf("con pausa", "isometric") to "La pausa quita el rebote: usa menos carga que en la versión normal",
    listOf("deficit") to "El déficit alarga el recorrido: usa menos carga",
    listOf("cadenas", "gomas") to "La resistencia crece al final del recorrido",
    listOf("agarre estrecho", "cerradas", "diamante") to "El agarre estrecho da más trabajo al tríceps",
    listOf("agarre ancho", "abiertas") to "El agarre ancho acorta el recorrido y da más trabajo al pecho o al dorsal",
    listOf("supino", "supinas") to "Con las palmas hacia ti trabaja más el bíceps",
    listOf("neutro") to "Con las palmas enfrentadas el hombro va más cómodo",
    listOf("multipower", "maquina", "polea") to "La máquina guía el recorrido: útil para aprender el gesto o para llegar cerca del fallo con seguridad",
    listOf("fitball", "bosu", "correas", "anillas") to "El apoyo inestable pide más control: usa menos carga",
    listOf("con salto", "explosiv", "pliometric", "con palmada") to "Hazlo rápido y para cuando pierdas velocidad",
    listOf("lastrad", "con peso") to "Añade carga solo cuando la versión sin peso te salga limpia",
)

/** Descripción breve de un ejercicio: qué movimiento es, qué trabaja, con qué se hace y lo que cambia su variante. */
fun describeExercise(name: String, pattern: String, equipment: List<String>, timed: Boolean): String {
    val n = plain(name)
    val parts = mutableListOf<String>()
    if (ODD_ONES[plain(pattern)]?.none { it in n } != false) PATTERN_WHAT[pattern]?.let { parts += it }
    muscles(name, pattern).takeIf { it.isNotEmpty() }?.let { parts += "Trabaja sobre todo ${it.joinToString(", ")}" }
    parts += if (equipment.isEmpty() || equipment == listOf("peso corporal")) "No necesita material" else "Material: ${equipment.joinToString(", ")}"
    parts += if (timed) "Se mide por tiempo" else "Se mide por repeticiones"
    VARIANT_NOTES.filter { (words, _) -> words.any { it in n } }.take(2).forEach { parts += it.second }
    return parts.joinToString(". ") + "."
}

/**
 * Variantes por las que el plan va cambiando cada ejercicio que proponen los motores: mismo patrón, misma forma de medirlo
 * y el mismo material que el original (gimnasio o peso corporal). Elegidas a mano entre las básicas de cada patrón.
 * [Práctica: variar el ejercicio manteniendo el patrón; Fonseca 2014 lo compara con variar solo la carga]
 */
val ROTATION: Map<String, List<String>> = mapOf(
    // Plan de salud, gimnasio
    "Sentadilla copa (goblet) o con barra" to listOf("Sentadilla frontal con barra", "Prensa de piernas", "Sentadilla con mancuernas", "Sentadilla hack en máquina"),
    "Peso muerto rumano" to listOf("Peso muerto con barra", "Peso muerto con mancuernas y piernas rígidas", "Hiperextensión lumbar", "Peso muerto con barra hexagonal"),
    "Zancada con mancuernas" to listOf("Zancada caminando con mancuernas", "Zancada atrás con mancuernas", "Subida a banco con mancuernas", "Sentadilla dividida con mancuernas"),
    "Press de banca con mancuernas" to listOf("Press de banca con barra", "Press inclinado con mancuernas", "Press de pecho en máquina", "Press inclinado con barra"),
    "Press militar con mancuernas" to listOf("Press militar de pie con barra", "Press Arnold", "Press de hombros en máquina", "Press de hombros sentado con mancuernas"),
    "Remo con mancuerna" to listOf("Remo sentado en polea", "Remo con barra", "Remo en máquina", "Remo inclinado con mancuernas en banco"),
    "Jalón al pecho" to listOf("Jalón con agarre neutro", "Jalón con agarre supino", "Dominadas asistidas en máquina", "Jalón con agarre ancho"),
    "Plancha frontal y lateral" to listOf("Posición hueca (hollow)", "Plancha de oso", "Plancha con brazos extendidos"),
    // Plan de salud, peso corporal
    "Sentadilla (a una pierna al progresar)" to listOf("Sentadilla con peso corporal", "Sentadilla del prisionero", "Sentadilla sumo con peso corporal", "Sentadilla a una silla"),
    "Puente de glúteo (a una pierna al progresar)" to listOf("Puente de glúteo con marcha", "Peso muerto a una pierna con peso corporal", "Buenos días con peso corporal"),
    "Zancada atrás" to listOf("Zancada adelante", "Zancada lateral", "Zancada caminando", "Sentadilla dividida"),
    "Flexiones (inclinadas al empezar)" to listOf("Flexiones de rodillas", "Flexiones abiertas", "Flexiones con pausa"),
    "Remo invertido bajo una mesa firme" to listOf("Remo invertido con agarre supino", "Remo invertido con toalla"),
    "Dominadas asistidas con goma" to listOf("Dominadas negativas", "Dominadas con salto", "Dominadas escapulares"),
    // Plan de carrera, gimnasio
    "Sentadilla con barra" to listOf("Sentadilla frontal con barra", "Sentadilla a cajón con barra", "Prensa de piernas"),
    "Zancada búlgara con mancuernas" to listOf("Subida a banco con mancuernas", "Zancada caminando con mancuernas", "Zancada atrás con mancuernas"),
    "Elevación de gemelo de pie y sentado" to listOf("Gemelo de pie en máquina", "Gemelo sentado en máquina", "Gemelo en prensa"),
    "Empuje de cadera con barra (hip thrust)" to listOf("Puente de glúteo con barra", "Empuje de cadera en máquina", "Tirón de cadera en polea (pull through)"),
    "Saltos al cajón" to listOf("Saltos al banco", "Subida explosiva al cajón"),
    "Jalón al pecho o dominadas lastradas" to listOf("Jalón con agarre neutro", "Dominadas pronas", "Jalón con agarre supino"),
    "Tirón a la cara con polea (face pull)" to listOf("Tirón a la cara con rotación externa", "Remo al deltoides posterior con cuerda"),
    // Plan de carrera, peso corporal
    "Sentadilla búlgara (progresión a sentadilla a una pierna)" to listOf("Sentadilla dividida", "Zancada adelante", "Zancada caminando"),
    "Peso muerto rumano a una pierna" to listOf("Peso muerto a una pierna con peso corporal", "Buenos días con peso corporal"),
    "Subida a cajón alto" to listOf("Subida a banco", "Subida lateral a banco", "Bajada controlada del cajón (step down)"),
    "Gemelo a una pierna en escalón" to listOf("Gemelo a una pierna de pie", "Gemelo de pie con peso corporal", "Gemelo a una pierna con rodilla doblada"),
    "Puente de glúteo a una pierna" to listOf("Puente de glúteo con marcha", "Puente de glúteo con extensión de pierna"),
    "Saltos de tobillo seguidos (pogo)" to listOf("Saltos sobre una línea", "Saltos de esquiador"),
    "Saltos de rana" to listOf("Sentadilla con salto", "Salto de longitud sin carrera", "Salto vertical"),
    "Dominadas o negativas" to listOf("Dominadas supinas", "Dominadas con agarre neutro", "Dominadas negativas"),
    "Remo invertido" to listOf("Remo invertido con agarre supino", "Remo invertido con pies elevados"),
    "Flexiones" to listOf("Flexiones diamante", "Flexiones abiertas", "Flexiones con pausa"),
    "Rotación externa con goma" to listOf("Separación de goma con brazos rectos", "Tirón a la cara con goma"),
    // Core de los dos planes
    "Plancha frontal" to listOf("Plancha con brazos extendidos", "Plancha de oso", "Posición hueca (hollow)"),
    "Plancha lateral" to listOf("Plancha lateral con pierna elevada"),
    "Bicho muerto (dead bug)" to listOf("Perro de muestra (bird dog)", "Crunch inverso", "Elevación de piernas tumbado"),
    "Press Pallof antirrotación" to listOf("Press Pallof con rotación", "Leñador en polea"),
)

const val ROTATION_WEEKS = 4

/**
 * La variante de una sesión: se mantiene todo el bloque (`ROTATION_WEEKS` semanas, las mismas que el bloque del motor, con
 * su descarga al final) para poder progresar en ella, y cambia con el bloque. `slot`: el orden de la sesión de fuerza en su
 * semana (0, 1, 2…), para que dos sesiones de la misma semana no repitan variante y mover una sesión de día no la cambie.
 * `allowed`: si la variante se puede hacer (material del usuario); si no, se queda el original.
 */
fun rotated(original: String, block: Int, slot: Int, allowed: (String) -> Boolean = { true }): String {
    val options = listOf(original) + (ROTATION[original] ?: return original)
    return options[(block + slot).mod(options.size)].takeIf { it == original || allowed(it) } ?: original
}

/** El bloque de rotación de una semana sin bloques propios (plan de carrera): cuatro semanas de calendario. */
fun rotationBlock(monday: LocalDate): Int = monday.toEpochDays().floorDiv(7 * ROTATION_WEEKS).toInt()

/**
 * Las líneas del plan con la variante que toca y, de cada variante puesta, el ejercicio del plan del que sale. `keep`: los
 * ejercicios que el usuario ha cambiado o fijado él mismo, que no rotan. Una variante que ya está en la sesión no se
 * repite: se queda el original.
 */
fun rotateLines(lines: List<String>, block: Int, slot: Int, keep: Set<String> = emptySet(),
                allowed: (String) -> Boolean = { true }): Pair<List<String>, Map<String, String>> {
    val used = lines.map { exerciseName(it) }.toMutableSet()
    val from = mutableMapOf<String, String>()
    val out = lines.map { line ->
        val name = exerciseName(line)
        val to = if (name in keep) name else rotated(name, block, slot, allowed)
        if (to == name || !used.add(to)) line else to.also { from[it] = name } + line.removePrefix(name)
    }
    return out to from
}

/**
 * Quién rota: a quien empieza, a los menores, a los mayores de 65 y a quien el cuestionario de salud limita la intensidad
 * se le deja el ejercicio que eligió el motor, que es el más sencillo de su patrón. [Inferencia: prudencia]
 */
fun rotates(level: String, age: Int?, intensityCap: String): Boolean =
    level != "beginner" && intensityCap == "vigorous" && (age == null || age in 18..64)
