package app.brivel.app

// Biblioteca ampliada (pedido 2026-11-07): para las rutinas propias y para cambiar ejercicios; los motores siguen
// proponiendo solo EXERCISES. Selección y nombres en español propios, tomando como referencia free-exercise-db
// (yuhonas, Unlicense); no se usan sus textos ni sus imágenes.

private class Group(val pattern: String, equipment: String, vararg names: String, val timed: Boolean = false) {
    val equipment = equipment.split(",")
    val names = names.toList()
}

private val GROUPS = listOf(
    Group("sentadilla", "barra", "Sentadilla frontal con barra", "Sentadilla completa con barra", "Sentadilla con barra y piernas abiertas",
        "Sentadilla Zercher", "Sentadilla hack con barra", "Sentadilla por encima de la cabeza"),
    Group("sentadilla", "barra,banco o cajón", "Sentadilla a cajón con barra", "Sentadilla frontal a un banco con barra"),
    Group("sentadilla", "polea o máquina", "Prensa de piernas", "Prensa de piernas con pies juntos", "Sentadilla hack en máquina",
        "Sentadilla en multipower", "Prensa de piernas en multipower", "Sentadilla tumbado en máquina", "Sentadilla a una pierna en multipower"),
    Group("sentadilla", "mancuernas", "Sentadilla con mancuernas", "Sentadilla plié con mancuerna"),
    Group("sentadilla", "mancuernas,banco o cajón", "Sentadilla a un banco con mancuernas"),
    Group("sentadilla", "pesa rusa", "Sentadilla frontal con dos pesas rusas", "Sentadilla a una pierna con pesa rusa",
        "Sentadilla por encima con pesa rusa a un brazo"),
    Group("sentadilla", "peso corporal", "Sentadilla con peso corporal", "Sentadilla a una silla", "Sentadilla sissy"),
    Group("sentadilla", "banco o cajón", "Sentadilla a una pierna a cajón alto"),
    Group("sentadilla", "goma", "Sentadilla con goma"),
    Group("sentadilla", "peso corporal", "Sentadilla isométrica en la pared", timed = true),

    Group("bisagra", "barra", "Peso muerto con barra", "Peso muerto sumo", "Peso muerto con déficit", "Peso muerto en rack",
        "Peso muerto con piernas rígidas", "Peso muerto con barra hexagonal", "Buenos días con barra", "Buenos días sentado con barra",
        "Puente de glúteo con barra", "Cargada de potencia", "Cargada colgante", "Arrancada de potencia", "Arrancada colgante"),
    Group("bisagra", "mancuernas", "Peso muerto con mancuernas y piernas rígidas", "Cargada con mancuernas"),
    Group("bisagra", "mancuernas,banco o cajón", "Empuje de cadera con mancuerna"),
    Group("bisagra", "pesa rusa", "Swing con pesa rusa", "Swing a un brazo con pesa rusa", "Peso muerto a una pierna con pesa rusa", "Cargada con pesa rusa"),
    Group("bisagra", "polea o máquina", "Tirón de cadera en polea (pull through)", "Patada de glúteo en polea", "Hiperextensión inversa",
        "Hiperextensión lumbar", "Elevación de glúteo y femoral (glute-ham)"),
    Group("bisagra", "goma", "Buenos días con goma", "Extensión de cadera con goma", "Puente de glúteo con goma", "Peso muerto con goma"),
    Group("bisagra", "peso corporal", "Curl nórdico", "Patada de glúteo en cuadrupedia", "Elevación de cadera con rodillas dobladas"),

    Group("zancada", "barra", "Zancada con barra", "Zancada caminando con barra", "Zancada lateral con barra"),
    Group("zancada", "mancuernas", "Zancada caminando con mancuernas", "Zancada atrás con mancuernas", "Zancada lateral con mancuernas", "Sentadilla dividida con mancuernas"),
    Group("zancada", "peso corporal", "Zancada caminando", "Zancada lateral", "Zancada cruzada"),
    Group("zancada", "banco o cajón", "Zancada atrás con déficit", "Subida a banco con elevación de rodilla"),
    Group("zancada", "barra,banco o cajón", "Subida a banco con barra"),
    Group("zancada", "mancuernas,banco o cajón", "Subida a banco con mancuernas"),
    Group("zancada", "polea o máquina", "Sentadilla dividida en multipower"),
    Group("zancada", "pesa rusa", "Zancada con pesa rusa"),

    Group("gemelo", "barra", "Gemelo de pie con barra", "Gemelo sentado con barra"),
    Group("gemelo", "polea o máquina", "Gemelo de pie en máquina", "Gemelo sentado en máquina", "Gemelo en prensa", "Gemelo tipo burro", "Gemelo en multipower"),
    Group("gemelo", "mancuernas", "Gemelo de pie con mancuernas", "Gemelo sentado a una pierna con mancuerna"),
    Group("gemelo", "goma", "Gemelo con goma"),
    Group("gemelo", "peso corporal", "Elevación de puntas de pie (tibial)"),

    Group("salto", "peso corporal", "Salto de longitud sin carrera", "Sentadilla con salto", "Zancada con salto", "Saltos con rodillas al pecho",
        "Saltos de obstáculos", "Saltos laterales sobre un cono", "Salto en estrella", "Saltos a una pierna", "Saltos laterales a una pierna",
        "Saltos laterales de patinador"),
    Group("salto", "banco o cajón", "Saltos laterales al cajón", "Saltos al banco", "Saltos alternos al cajón", "Salto de caída con impulso largo"),
    Group("salto", "mancuernas", "Sentadilla con salto y mancuernas"),

    Group("empuje horizontal", "barra,banco o cajón", "Press de banca con barra", "Press de banca con agarre ancho", "Press de banca con agarre estrecho",
        "Press inclinado con barra", "Press declinado con barra"),
    Group("empuje horizontal", "barra", "Press de suelo con barra"),
    Group("empuje horizontal", "mancuernas,banco o cajón", "Press inclinado con mancuernas", "Press declinado con mancuernas", "Press de banca con mancuernas y agarre neutro",
        "Press de banca a un brazo con mancuerna", "Aperturas con mancuernas", "Aperturas inclinadas con mancuernas", "Aperturas declinadas con mancuernas",
        "Jersey con mancuerna (pullover)"),
    Group("empuje horizontal", "mancuernas", "Press de suelo con mancuernas"),
    Group("empuje horizontal", "polea o máquina", "Press de pecho en máquina", "Press inclinado en máquina", "Press declinado en máquina", "Press de pecho en polea",
        "Cruce de poleas", "Cruce de poleas bajas", "Aperturas en polea tumbado", "Contractora de pecho (butterfly)", "Press de banca en multipower", "Press inclinado en multipower"),
    Group("empuje horizontal", "peso corporal", "Flexiones inclinadas", "Flexiones abiertas", "Flexiones cerradas", "Flexiones a un brazo", "Flexiones pliométricas",
        "Flexiones con giro a plancha lateral", "Fondos en paralelas para pecho"),
    Group("empuje horizontal", "banco o cajón", "Flexiones con los pies elevados"),
    Group("empuje horizontal", "goma", "Press de banca con goma", "Cruce con goma"),
    Group("empuje horizontal", "pesa rusa", "Press de suelo con pesa rusa"),

    Group("empuje vertical", "barra", "Press militar de pie con barra", "Press militar sentado con barra", "Push press", "Cargada y press con barra", "Dos tiempos (clean and jerk)", "Press en mina (landmine)"),
    Group("empuje vertical", "mancuernas", "Press de hombros sentado con mancuernas", "Press Arnold", "Press de hombros a un brazo", "Press alterno de pie con mancuernas", "Press de hombros con agarre neutro"),
    Group("empuje vertical", "polea o máquina", "Press de hombros en máquina", "Press de hombros en polea", "Press militar en multipower"),
    Group("empuje vertical", "goma", "Press de hombros con goma"),
    Group("empuje vertical", "pesa rusa", "Press con pesa rusa", "Press con dos pesas rusas", "Push press con pesa rusa", "Sentadilla y press con pesa rusa (thruster)"),
    Group("empuje vertical", "peso corporal", "Flexiones en pino"),

    Group("tracción horizontal", "barra", "Remo con barra", "Remo con barra y agarre supino", "Remo en T"),
    Group("tracción horizontal", "mancuernas", "Remo con dos mancuernas", "Remo con dos mancuernas y agarre neutro", "Remo renegado"),
    Group("tracción horizontal", "mancuernas,banco o cajón", "Remo inclinado con mancuernas en banco"),
    Group("tracción horizontal", "polea o máquina", "Remo sentado en polea", "Remo a un brazo en polea", "Remo en máquina", "Remo en multipower", "Remo al cuello en polea"),
    Group("tracción horizontal", "pesa rusa", "Remo con pesa rusa", "Remo alterno con dos pesas rusas"),
    Group("tracción horizontal", "peso corporal", "Remo invertido con correas"),
    Group("tracción horizontal", "goma", "Separación de goma con brazos rectos"),

    Group("tracción vertical", "barra de dominadas", "Dominadas supinas", "Dominadas pronas", "Dominadas con agarre neutro", "Dominadas lastradas", "Dominadas escapulares",
        "Dominadas con agarre ancho", "Muscle-up"),
    Group("tracción vertical", "polea o máquina", "Jalón con agarre ancho", "Jalón con agarre estrecho", "Jalón con agarre supino", "Jalón con agarre en V", "Jalón a un brazo",
        "Jalón con brazos rectos", "Dominadas asistidas en máquina"),
    Group("tracción vertical", "barra,banco o cajón", "Jersey con barra (pullover)"),
    Group("tracción vertical", "barra de dominadas", "Colgarse de la barra", timed = true),

    Group("core", "peso corporal", "Encogimiento abdominal (crunch)", "Crunch inverso", "Crunch oblicuo", "Crunch de bicicleta", "Abdominal completo (sit-up)", "Giro ruso",
        "Elevación de piernas tumbado", "Elevación de piernas colgado", "Elevación de rodillas en paralelas", "Escaladores", "Superman", "Perro de muestra (bird dog)", "Rueda abdominal"),
    Group("core", "banco o cajón", "Crunch declinado", "Elevación de piernas en banco"),
    Group("core", "polea o máquina", "Crunch en polea", "Crunch en máquina", "Giro ruso en polea", "Leñador en polea", "Inclinación lateral en polea"),
    Group("core", "mancuernas", "Inclinación lateral con mancuerna"),
    Group("core", "barra", "Inclinación lateral con barra", "Giro con barra en mina (landmine)"),
    Group("core", "goma", "Press Pallof con rotación"),
    Group("core", "pesa rusa", "Molino con pesa rusa", "Levantamiento turco con pesa rusa"),
    Group("core", "peso corporal", "Patadas de tijera", "Posición hueca (hollow)", timed = true),
    Group("core", "mancuernas", "Paseo del granjero", timed = true),

    Group("brazos", "barra", "Curl con barra", "Curl con barra Z", "Curl inverso con barra", "Curl de arrastre con barra", "Curl de muñeca con barra", "Curl de muñeca inverso con barra",
        "Press francés con barra Z", "Press JM"),
    Group("brazos", "banco o cajón,barra", "Curl predicador con barra"),
    Group("brazos", "mancuernas", "Curl con mancuernas", "Curl alterno con mancuernas", "Curl martillo", "Curl martillo cruzado", "Curl concentrado", "Curl Zottman",
        "Curl de muñeca con mancuernas", "Press francés con mancuernas", "Extensión de tríceps sobre la cabeza con mancuerna", "Patada de tríceps"),
    Group("brazos", "mancuernas,banco o cajón", "Curl inclinado con mancuernas", "Curl predicador con mancuerna", "Curl araña"),
    Group("brazos", "polea o máquina", "Curl en polea", "Curl martillo con cuerda", "Curl alto en polea", "Curl predicador en máquina", "Extensión de tríceps en polea",
        "Extensión de tríceps con cuerda", "Extensión de tríceps con barra en V", "Extensión de tríceps con agarre supino", "Extensión de tríceps sobre la cabeza con cuerda",
        "Extensión de tríceps a un brazo en polea", "Fondos en máquina", "Extensión de tríceps en máquina"),
    Group("brazos", "peso corporal", "Fondos en paralelas para tríceps"),
    Group("brazos", "banco o cajón", "Fondos en banco"),
    Group("brazos", "goma", "Curl con goma", "Extensión de tríceps con goma"),
    Group("brazos", "barra", "Pinza con discos", timed = true),

    Group("hombros", "mancuernas", "Elevación lateral con mancuernas", "Elevación lateral sentado con mancuernas", "Elevación lateral a un brazo", "Elevación frontal con mancuernas",
        "Pájaro con mancuernas", "Pájaro sentado con mancuernas", "Remo al mentón con mancuernas", "Encogimiento de hombros con mancuernas", "Elevación en escápula con mancuernas", "Press cubano"),
    Group("hombros", "barra", "Elevación frontal con barra", "Elevación frontal con disco", "Remo al mentón con barra", "Encogimiento de hombros con barra"),
    Group("hombros", "polea o máquina", "Elevación lateral en polea", "Elevación frontal en polea", "Pájaro en polea", "Pájaro en máquina", "Remo al mentón en polea",
        "Encogimiento de hombros en polea", "Encogimiento de hombros en máquina", "Rotación externa en polea", "Rotación interna en polea"),
    Group("hombros", "goma", "Elevación lateral con goma", "Rotación interna con goma"),
    Group("hombros", "pesa rusa", "Halo con pesa rusa"),

    Group("pierna aislada", "polea o máquina", "Extensión de cuádriceps", "Extensión de cuádriceps a una pierna", "Curl femoral tumbado", "Curl femoral sentado", "Curl femoral de pie",
        "Abductores en máquina", "Aductores en máquina", "Aducción de cadera en polea"),
    Group("pierna aislada", "goma", "Curl femoral con goma", "Aducción de cadera con goma", "Paseo lateral con goma", "Flexión de cadera con goma"),
)

/** Todos los ejercicios de la biblioteca, ordenados por patrón. */
val LIBRARY: Map<String, ExerciseInfo> = (EXERCISES.toList() + GROUPS.flatMap { g -> g.names.map { it to ExerciseInfo(g.pattern, g.timed) } })
    .sortedBy { (_, info) -> PATTERN_LABEL.keys.indexOf(info.pattern) }.toMap()

internal val CATALOG_EQUIPMENT: Map<String, List<String>> = GROUPS.flatMap { g -> g.names.map { it to g.equipment } }.toMap()
