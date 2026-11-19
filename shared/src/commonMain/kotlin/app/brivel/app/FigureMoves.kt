package app.brivel.app

// Los movimientos de las figuras G (FigureG.kt) y qué movimiento y qué material lleva cada ejercicio de la biblioteca:
// reglas sobre su patrón, su nombre y su material, sin un dibujo a mano por ejercicio. Posturas de perfil mirando a la
// derecha (de frente, las que se entienden mejor así: elevaciones laterales, aberturas, saltos de tijera).

private fun p(x: Number, y: Number) = Pt(x.toFloat(), y.toFloat())

// ------------------------------------------------------------ posturas base

private val STAND = pose(24, 10.5, 23.5, 22.5, 24, 33, 23.5, 42.3, 29, 43.5, 23.2, 33, 22.7, 42.3, 28.2, 43.5, 24.3, 17.5, 24.6, 23.5, 23.5, 17.5, 23.8, 23.5)
private val FRONT = pose(24, 10.5, 24, 22.5, 26.4, 33, 26.7, 42.3, 28.4, 43.5, 21.6, 33, 21.3, 42.3, 19.6, 43.5, 28, 16.6, 29, 22.6, 20, 16.6, 19, 22.6)
private val SQ_A = pose(24, 9, 23, 21, 24, 32, 23, 43, 28, 43.5, 23, 32, 22, 43, 27, 43.5, 24, 16, 24.3, 22, 23.3, 16, 23.5, 22)
private val SQ_B = pose(24.2, 22.8, 16.5, 32, 27, 33, 23, 43, 28, 43.5, 26, 33.5, 22, 43, 27, 43.5, 23.4, 29, 24, 35, 22.7, 29.3, 23.3, 35.3)
private val HINGE_A = pose(23.5, 9, 23, 21, 23.5, 32, 23, 43, 28, 43.5, 22.6, 32, 22, 43, 27, 43.5, 24, 16, 24.5, 22, 23.2, 16, 23.6, 22)
private val HINGE_B = pose(28.6, 20.4, 17, 23.5, 24, 32.5, 22, 43, 27, 43.5, 23, 32.7, 21, 43, 26, 43.5, 28.6, 27.4, 28.6, 33.4, 27.8, 27.4, 27.8, 33.4)
private val LUNGE_B = pose(23, 20, 23, 32, 34, 32, 34, 43, 39, 43.5, 18, 42, 8.5, 40.5, 6, 43.6, 23.4, 27, 23.6, 33, 22.7, 27, 22.9, 33)
private val CALF_B = pose(24, 8, 23.5, 20, 24, 30.5, 25, 40.5, 29.5, 43.5, 23.2, 30.5, 24.2, 40.5, 28.7, 43.5, 24.3, 15, 24.6, 21, 23.5, 15, 23.8, 21)
private val LIE = pose(26, 26.4, 12, 26.4, 4.6, 29.5, 6, 43, 10.4, 43.5, 5.2, 29.8, 6.6, 43, 11, 43.5, 21.4, 31.6, 23.6, 22.2, 22.2, 31.2, 24.4, 22)
private val PLANK = pose(38.5, 31, 27, 34, 16.4, 37, 5.8, 40, 4, 43.5, 16.4, 37.4, 5.8, 40.4, 4, 43.8, 38.7, 37, 38.9, 43.3, 38, 37, 38.2, 43.3)
private val PUSH_B = pose(38.5, 36.5, 27.2, 38.6, 16.5, 40.2, 5.8, 41.8, 4, 43.5, 16.5, 40.5, 5.8, 42.1, 4, 43.8, 33.5, 37.3, 38.9, 43.3, 33, 37.6, 38.2, 43.3)
private val ELBOW_PLANK = pose(37, 37.5, 25.1, 39.3, 14.3, 40.9, 3.5, 42.5, 2.8, 44.2, 14.3, 41.2, 3.5, 42.8, 2.8, 44.4, 37, 43.3, 43, 43.6, 36.3, 43.3, 42.3, 43.6)
private val SIDE_PLANK = pose(36, 36.3, 24.3, 38.9, 13.6, 41.3, 4, 43.2, 2.6, 44.2, 13.6, 40.6, 4, 42.4, 2.6, 43.4, 36, 43.3, 42, 43.6, 36.5, 29.3, 37, 23.3)
private val HANG = pose(23.2, 16.5, 23, 28.5, 24.5, 37, 21, 44.5, 23.5, 46, 23.7, 37, 20.2, 44.5, 22.7, 46, 22.2, 9.8, 21.6, 3.6, 21.4, 9.8, 20.8, 3.6)
private val CHIN = pose(23.2, 8.4, 23, 20.4, 24.5, 28.9, 21, 36.4, 23.5, 37.9, 23.7, 28.9, 20.2, 36.4, 22.7, 37.9, 17.6, 9.8, 21.6, 3.6, 18.3, 10.2, 20.8, 3.6)
private val ROW_BENCH = pose(27, 18, 15, 20, 18, 31.5, 17, 43, 22, 43.5, 15.5, 29.4, 5.5, 29.4, 3.5, 30.5, 26, 25, 26, 31, 27.6, 24, 28, 29.5)
private val SUPINE = pose(16, 41, 28, 41.5, 34, 34, 39.5, 42.8, 43.5, 43.5, 33.4, 34.3, 38.9, 43, 42.9, 43.6, 13.4, 36.4, 11.6, 40.6, 14, 36.8, 12.2, 41)
private val PRONE = pose(30, 41.6, 18, 42, 9, 42.5, 1.5, 43, 0, 41.8, 9, 42.8, 1.5, 43.3, 0, 42.2, 36, 41.6, 42, 41.6, 35.4, 41.9, 41.4, 41.9)
private val QUAD = pose(32, 31, 18, 31, 18, 43, 9, 43.6, 7, 43.6, 17.4, 43, 8.4, 43.6, 6.4, 43.6, 32, 37, 32, 43, 31.4, 37, 31.4, 43)
private val KNEEL = pose(24, 21, 23.5, 33, 23.5, 43, 14, 43.4, 11.5, 43.6, 22.8, 43, 13.3, 43.4, 10.8, 43.6, 24.3, 27.5, 24.6, 33.5, 23.5, 27.5, 23.8, 33.5)
private val SIT = pose(15, 28.5, 16, 40.6, 27, 40.8, 38, 41.4, 41, 39, 26.4, 41.1, 37.4, 41.7, 40.4, 39.3, 19, 34, 25, 36.5, 18.4, 34.4, 24.4, 36.9)
private val SEATED = pose(22.5, 20.5, 22, 32.4, 31, 32.4, 31, 43, 35, 43.5, 30.4, 32.7, 30.4, 43, 34.4, 43.5, 23, 27, 23.4, 33, 22.3, 27.2, 22.7, 33.2)
private val RUN_A = pose(25.5, 10.5, 23.5, 22.2, 30, 28, 27, 36.5, 31, 37, 21, 32, 16.5, 40.5, 19.5, 43, 20.5, 16, 23.5, 21, 29, 15.5, 33, 11.5)

// ------------------------------------------------------------ movimientos

private val SEAT = Mark("equip", "M16 33h12v3h-12", fill = true)
private val SEAT_LEG = Mark("equip", "M22 36v8.6", 1.6f)
private val WALL = Mark("equip", "M43 2v42.6", 1.6f)
private val BARS = Mark("equip", "M18 25.6h12M28 25.6v19", 1.4f)
private val ROLLER = Mark("plate", x = 25f, y = 41.4f, r = 2.6f)

internal class MoveDef(val move: Move, val extra: List<Mark> = emptyList(), val anchor: Pt? = null)

private fun m(a: Pose, b: Pose, work: Collection<String>, front: List<String>, back: List<String>, la: String = "Inicio",
    lb: String = "Final", props: List<String> = emptyList(), hold: String = "w", floor: Boolean = true,
    extra: List<Mark> = emptyList(), anchor: Pt? = null) = MoveDef(Move(a, b, work.toSet(), front, back, la, lb, props, hold, floor), extra, anchor)

private val LEGS = listOf("quads"); private val GLUTES = listOf("glutes")
internal val MOVES: Map<String, MoveDef> = mapOf(
    // Sentadilla: brazos según el peso
    "squat-back" to m(SQ_A.arms(p(19.5, 15), p(21, 9.4)), SQ_B.arms(p(19.2, 27.4), p(21.4, 23)), setOf("th"), LEGS, GLUTES, hold = "s"),
    "squat-front" to m(SQ_A.arms(p(26.4, 14.6), p(27.8, 11.6)), SQ_B.arms(p(27, 27.4), p(28.6, 24.2)), setOf("th"), LEGS, GLUTES),
    "squat-sides" to m(SQ_A, SQ_B, setOf("th"), LEGS, GLUTES),
    "squat-bw" to m(SQ_A.arms(p(29, 9.6), p(34, 10)), SQ_B.arms(p(29.4, 23), p(34.6, 23.2)), setOf("th"), LEGS, GLUTES),
    "squat-overhead" to m(SQ_A.arms(p(23.6, 3.4), p(23.2, -2.2)), SQ_B.arms(p(22.6, 17.4), p(21.6, 11.8)), setOf("th"), LEGS, GLUTES),
    "squat-wall" to m(STAND, pose(24.5, 21, 24, 32.5, 33, 32.5, 33, 43, 37.5, 43.5, 32.4, 32.8, 32.4, 43, 36.9, 43.5, 26, 27, 30, 31.5, 25.4, 27.3, 29.4, 31.8),
        setOf("th"), LEGS, GLUTES, "Inicio", "Aguanta", extra = listOf(Mark("equip", "M19.5 2v42.6", 1.6f))),
    "leg-press" to m(pose(16, 27, 22, 36, 26.5, 27, 35, 25, 36.5, 21.5, 26, 27.3, 34.4, 25.3, 35.9, 21.8, 18.5, 32.5, 23, 35.5, 18, 32.8, 22.5, 35.8),
        pose(16, 27, 22, 36, 31, 31.5, 40.5, 30, 42, 26.5, 30.4, 31.8, 39.9, 30.3, 41.4, 26.8, 18.5, 32.5, 23, 35.5, 18, 32.8, 22.5, 35.8),
        setOf("th"), LEGS, GLUTES, extra = listOf(Mark("equip", "M11 24L20 40h6", 2.4f))),
    // Bisagra
    "hinge" to m(HINGE_A, HINGE_B, setOf("th"), emptyList(), listOf("hams", "glutes", "erectors")),
    "hinge-bar-back" to m(HINGE_A.arms(p(19.5, 15), p(21, 9.4)), HINGE_B.arms(p(25, 24), p(27.5, 19.6)), setOf("th"), emptyList(),
        listOf("hams", "glutes", "erectors"), hold = "s"),
    "swing" to m(HINGE_B.arms(p(24.5, 29), p(22, 34.5)), STAND.arms(p(29.5, 11.5), p(35, 11)), setOf("th"), emptyList(), listOf("glutes", "hams")),
    "hip-thrust" to m(pose(10, 28.5, 21, 37, 30, 31, 30.5, 43, 35, 43.5, 29.4, 31.3, 29.9, 43, 34.4, 43.5, 14, 33.5, 20, 34.5, 13.4, 33.8, 19.4, 34.8),
        pose(10, 28.5, 21, 29, 31, 29, 31, 43, 35.5, 43.5, 30.4, 29.3, 30.4, 43, 34.9, 43.5, 15.5, 26.5, 21, 25.5, 14.9, 26.8, 20.4, 25.8),
        setOf("th"), emptyList(), listOf("glutes", "hams"), props = listOf("bench"), hold = "w"),
    "bridge" to m(pose(12, 40.5, 24, 41.5, 31, 34.5, 32, 43, 36.5, 43.5, 30.4, 34.8, 31.4, 43, 35.9, 43.5, 18, 41.5, 24, 42, 17.4, 41.8, 23.4, 42.3),
        pose(12, 40.5, 23.5, 34, 32, 34, 32.5, 43, 37, 43.5, 31.4, 34.3, 31.9, 43, 36.4, 43.5, 18, 41.5, 24, 42, 17.4, 41.8, 23.4, 42.3),
        setOf("th"), emptyList(), listOf("glutes", "hams")),
    "back-extension" to m(pose(31, 34, 20, 27, 12.5, 33, 6, 39, 4.5, 41.5, 12, 33.4, 5.5, 39.4, 4, 41.9, 33.5, 39.5, 35.5, 44, 32.8, 39.8, 34.8, 44.3),
        pose(30.5, 21.5, 20, 27, 12.5, 33, 6, 39, 4.5, 41.5, 12, 33.4, 5.5, 39.4, 4, 41.9, 33, 17.5, 37, 21, 32.3, 17.8, 36.3, 21.3),
        setOf("torso"), emptyList(), listOf("erectors", "glutes", "hams"), extra = listOf(Mark("equip", "M14 29L24 33M10 40l6 4.6", 2.4f))),
    "good-morning" to m(HINGE_A.arms(p(19.5, 15), p(21, 9.4)), HINGE_B.arms(p(25, 24), p(27.5, 19.6)), setOf("th"), emptyList(),
        listOf("hams", "glutes", "erectors"), hold = "s"),
    // Zancada y subida
    "lunge" to m(SQ_A, LUNGE_B, setOf("th"), LEGS, GLUTES),
    "lunge-back" to m(SQ_A.arms(p(19.5, 15), p(21, 9.4)), LUNGE_B.arms(p(19, 25), p(21, 20.4)), setOf("th"), LEGS, GLUTES, hold = "s"),
    "lunge-overhead" to m(SQ_A.arms(p(23.6, 3.4), p(23.2, -2.2)), LUNGE_B.arms(p(22.8, 14.2), p(22.6, 8.6)), setOf("th"), LEGS, GLUTES),
    "split-squat" to m(pose(23.5, 12, 23, 24, 31, 33, 31, 43, 35.5, 43.5, 17.5, 33.5, 12, 41, 9.5, 43.6, 23.8, 19, 24.1, 25, 23.1, 19.3, 23.4, 25.3),
        LUNGE_B, setOf("th"), LEGS, GLUTES),
    "bulgarian" to m(pose(23.5, 12, 23, 24, 31, 33, 31, 43, 35.5, 43.5, 15.5, 31.5, 8.5, 30.5, 5.5, 30.5, 23.8, 19, 24.1, 25, 23.1, 19.3, 23.4, 25.3),
        pose(23.5, 19, 22, 31, 33, 32.5, 33, 43, 37.5, 43.5, 13.5, 37, 6.5, 30.8, 5, 30.6, 23.8, 26, 24.1, 32, 23.1, 26.3, 23.4, 32.3),
        setOf("th"), LEGS, GLUTES, extra = listOf(Mark("equip", "M1 31.6h9v2.6h-9", fill = true), Mark("equip", "M3 34v10.6M8 34v10.6", 1.4f))),
    "lateral-lunge" to m(FRONT, pose(22, 21, 21, 32, 31, 35.5, 37, 43, 39, 43.5, 15, 33, 12, 42.4, 10, 43.5, 26, 27, 27, 33, 21, 27.3, 22, 33.3),
        setOf("th"), LEGS + "adductors", GLUTES),
    "step-up" to m(pose(23.5, 10, 23, 22, 28.5, 29, 30, 38.6, 34, 38.6, 23, 32, 22.5, 43, 27, 43.5, 23.8, 17, 24.1, 23, 23.1, 17.3, 23.4, 23.3),
        pose(32, 4.5, 31.5, 16.5, 32, 27.5, 31.5, 38.4, 36, 38.6, 31, 26.5, 29, 34, 33, 35, 32.3, 11.5, 32.6, 17.5, 31.6, 11.8, 31.9, 17.8),
        setOf("th"), LEGS, GLUTES, props = listOf("step")),
    // Gemelo
    "calf" to m(STAND, CALF_B, setOf("sh"), emptyList(), listOf("calves")),
    "calf-seated" to m(pose(22.5, 20.5, 22, 32.4, 31, 32.4, 31, 42.3, 36, 43.5, 30.4, 32.7, 30.4, 42.3, 35.4, 43.5, 26, 26.5, 31, 31.5, 25.4, 26.8, 30.4, 31.8),
        pose(22.5, 20.5, 22, 32.4, 31, 31.2, 32, 40.8, 36.5, 43.5, 30.4, 31.5, 31.4, 40.8, 35.9, 43.5, 26, 25.5, 31, 30.5, 25.4, 25.8, 30.4, 30.8),
        setOf("sh"), emptyList(), listOf("calves"), extra = listOf(SEAT, SEAT_LEG)),
    "tibialis" to m(STAND, pose(24, 10.5, 22.5, 22.5, 23, 33, 22, 42.6, 26, 39.5, 22.4, 33, 21.4, 42.6, 25.4, 39.5, 23.3, 17.5, 23.6, 23.5, 22.5, 17.5, 22.8, 23.5),
        setOf("sh"), listOf("shins"), emptyList(), extra = listOf(Mark("equip", "M18.5 2v42.6", 1.6f))),
    // Saltos
    "box-jump" to m(pose(15, 18.5, 8.5, 28, 17, 34, 12, 43, 17, 43.5, 16.2, 34.4, 11.2, 43, 16.2, 43.5, 10, 23, 5, 26.5, 10.8, 23.4, 5.8, 27),
        pose(33, 9, 28, 19, 37, 24, 34, 33.4, 39, 33.8, 36.2, 24.4, 33.2, 33.4, 38.2, 33.8, 38, 13, 42, 9, 37.2, 13.4, 41.2, 9.4),
        setOf("th", "sh"), LEGS, listOf("glutes", "calves"), "Impulso", "Caída", props = listOf("box")),
    "jump" to m(SQ_B.arms(p(19.5, 27.5), p(15, 31)), pose(24, 4.5, 23.5, 16.5, 24, 27, 24.5, 36, 28.5, 36.8, 23.2, 27, 23.7, 36, 27.7, 36.8, 27, 0, 29, -5, 26.2, 0.3, 28.2, -4.7),
        setOf("th", "sh"), LEGS, listOf("glutes", "calves"), "Impulso", "Salto"),
    "hop" to m(STAND, pose(24, 7, 23.5, 19, 24, 29.5, 24.5, 38.8, 29, 39.5, 23.2, 29.5, 23.7, 38.8, 28.2, 39.5, 24.3, 14, 24.6, 20, 23.5, 14, 23.8, 20),
        setOf("sh"), emptyList(), listOf("calves"), "Apoyo", "Rebote"),
    "bound" to m(RUN_A, pose(28, 9, 25, 20.5, 33, 25, 40, 30.5, 43, 29, 20, 29, 12, 33, 10, 31, 23, 14.5, 26, 19.5, 32, 13, 36, 9),
        setOf("th"), LEGS, listOf("glutes", "calves"), "Impulso", "Vuelo"),
    // Empujes
    "push-up" to m(PLANK, PUSH_B, setOf("ua", "fa", "upper"), listOf("chest", "delts"), listOf("triceps"), "Arriba", "Abajo"),
    "push-up-incline" to m(pose(36, 24, 25, 31, 16, 37.5, 7.5, 42.5, 5, 44.2, 16, 37.8, 7.5, 42.8, 5, 44.4, 37.8, 29.5, 39, 34.6, 37.2, 29.8, 38.4, 34.9),
        pose(36.5, 28, 25, 34, 16, 39, 7.5, 43, 5, 44.2, 16, 39.3, 7.5, 43.3, 5, 44.4, 32, 31, 39, 34.6, 31.5, 31.3, 38.4, 34.9),
        setOf("ua", "fa", "upper"), listOf("chest", "delts"), listOf("triceps"), "Arriba", "Abajo",
        extra = listOf(Mark("equip", "M36 35h10v2.6h-10", fill = true), Mark("equip", "M38 37.6v7M44 37.6v7", 1.4f))),
    "bench-press" to m(LIE.arms(p(21.4, 31.6), p(23.6, 22.2)), LIE.arms(p(25.6, 19.6), p(25.4, 13.2)), setOf("ua", "fa", "upper"),
        listOf("chest", "delts"), listOf("triceps"), props = listOf("bench")),
    "floor-press" to m(pose(26, 40.8, 12, 40.8, 20, 34, 13, 43, 17, 43.5, 19.4, 34.3, 12.4, 43, 16.4, 43.5, 21.4, 43, 23.6, 36.6, 22.2, 42.7, 24.4, 36.4),
        pose(26, 40.8, 12, 40.8, 20, 34, 13, 43, 17, 43.5, 19.4, 34.3, 12.4, 43, 16.4, 43.5, 25.6, 34, 25.4, 27.6, 26.4, 34, 26.2, 27.6),
        setOf("ua", "fa", "upper"), listOf("chest", "delts"), listOf("triceps")),
    "fly" to m(LIE.arms(p(24, 32), p(21.5, 36.5)), LIE.arms(p(25.8, 20), p(25.6, 14)), setOf("ua", "upper"), listOf("chest", "delts"), emptyList(),
        props = listOf("bench")),
    "cable-fly" to m(STAND.arms(p(21, 13), p(17, 9.5)), STAND.arms(p(29, 13.5), p(34, 14.5)), setOf("ua", "upper"), listOf("chest", "delts"), emptyList(),
        anchor = p(4, 4)),
    "dip" to m(pose(24, 13, 23.5, 25, 24, 33, 19, 38, 17.5, 37.5, 23.3, 33, 18.3, 38, 16.8, 37.5, 24.2, 19, 24.4, 25, 23.5, 19.3, 23.7, 25.3),
        pose(24, 19, 23.6, 31, 24, 39, 19, 44, 17.5, 43.5, 23.3, 39, 18.3, 44, 16.8, 43.5, 18.6, 21, 24.4, 25, 18, 21.3, 23.7, 25.3),
        setOf("ua", "upper"), listOf("chest"), listOf("triceps"), "Arriba", "Abajo", floor = false, extra = listOf(BARS)),
    "bench-dip" to m(pose(23, 22, 22.5, 34, 33, 34, 33, 43, 37.5, 43.5, 32.4, 34.3, 32.4, 43, 36.9, 43.5, 19.5, 27.5, 17, 31, 19, 27.8, 16.5, 31.3),
        pose(23, 28, 22.5, 40, 33, 36, 33, 43, 37.5, 43.5, 32.4, 36.3, 32.4, 43, 36.9, 43.5, 15.5, 27, 17, 31, 15, 27.3, 16.5, 31.3),
        setOf("ua"), emptyList(), listOf("triceps"), "Arriba", "Abajo", extra = listOf(Mark("equip", "M4 31h16v3h-16", fill = true), Mark("equip", "M7 34v10.6M17 34v10.6", 1.4f))),
    "press" to m(pose(24, 13.5, 23.3, 24, 23.8, 33.5, 23.3, 43, 28.3, 43.5, 23, 33.5, 22.5, 43, 27.5, 43.5, 27.4, 19, 29, 13.2, 26.6, 19.3, 28.2, 13.5),
        pose(24, 13.5, 23.3, 24, 23.8, 33.5, 23.3, 43, 28.3, 43.5, 23, 33.5, 22.5, 43, 27.5, 43.5, 24.6, 7, 25, 1.6, 23.8, 7, 24.2, 1.6),
        setOf("ua", "fa"), listOf("delts"), listOf("triceps", "delts")),
    "press-seated" to m(SEATED.arms(p(26.4, 25), p(28, 19.2)), SEATED.arms(p(23.1, 13), p(23.5, 7.6)), setOf("ua", "fa"), listOf("delts"),
        listOf("triceps", "delts"), extra = listOf(SEAT, SEAT_LEG)),
    "pike-push-up" to m(pose(33, 30, 22, 19, 15.5, 30, 9, 42, 7.5, 44, 15.5, 30.4, 9, 42.4, 7.5, 44.4, 34.5, 36.5, 36, 43, 33.8, 36.8, 35.3, 43.3),
        pose(33.5, 35, 22, 21, 15.5, 31.5, 9, 42, 7.5, 44, 15.5, 31.9, 9, 42.4, 7.5, 44.4, 29, 37.5, 36, 43, 28.4, 37.8, 35.3, 43.3),
        setOf("ua", "fa"), listOf("delts"), listOf("triceps"), "Arriba", "Abajo"),
    "handstand" to m(pose(24, 34, 23.5, 22, 24.5, 12, 24, 2, 26, 0.5, 23, 12, 22.5, 2, 24.5, 0.5, 24.3, 39, 24.5, 44, 23.6, 39, 23.8, 44),
        pose(24, 37, 23.5, 25, 24.5, 15, 24, 5, 26, 3.5, 23, 15, 22.5, 5, 24.5, 3.5, 19, 39.5, 24.5, 44, 18.4, 39.8, 23.8, 44),
        setOf("ua", "fa"), listOf("delts"), listOf("triceps"), "Arriba", "Abajo", extra = listOf(WALL.copy(d = "M20.5 2v42.6"))),
    // Tracciones
    "row-bench" to m(ROW_BENCH, ROW_BENCH.copy(e1 = p(21, 13.5), w1 = p(23.4, 19.4)), setOf("ua", "fa", "upper"), listOf("biceps"),
        listOf("lats", "upper", "erectors"), props = listOf("bench")),
    "row-bent" to m(HINGE_B, HINGE_B.arms(p(23, 21.5), p(25.5, 27.5)), setOf("ua", "fa", "upper"), listOf("biceps"), listOf("lats", "upper", "erectors")),
    "row-seated" to m(SIT.arms(p(21, 28.5), p(27, 30)), SIT.arms(p(9.5, 29.5), p(15.5, 32)), setOf("ua", "fa", "upper"), listOf("biceps"),
        listOf("lats", "upper"), anchor = p(46, 31)),
    "row-standing" to m(STAND.arms(p(29.5, 15.5), p(35, 17)), STAND.arms(p(18.5, 17), p(24.5, 18.5)), setOf("ua", "fa", "upper"), listOf("biceps"),
        listOf("lats", "upper"), anchor = p(46, 18)),
    "row-inverted" to m(pose(30, 26, 18, 32, 9, 37, 3, 42, 2.5, 44.5, 9, 37.3, 3, 42.3, 2.5, 44.6, 30.3, 22, 30.5, 18, 29.6, 22.3, 29.8, 18.3),
        pose(30, 21, 18, 29.5, 9, 36, 3, 42, 2.5, 44.5, 9, 36.3, 3, 42.3, 2.5, 44.6, 24.5, 20, 30.5, 18, 23.8, 20.3, 29.8, 18.3),
        setOf("ua", "fa", "upper"), listOf("biceps"), listOf("lats", "upper"), extra = listOf(Mark("plate", "M22 18h17", 1.6f))),
    "face-pull" to m(STAND.arms(p(29.5, 11), p(35, 10.5)), STAND.arms(p(21.5, 9.5), p(27, 6.5)), setOf("ua", "upper"), listOf("delts"),
        listOf("upper", "traps"), anchor = p(46, 8)),
    "pull-up" to m(HANG, CHIN, setOf("ua", "fa", "upper"), listOf("biceps"), listOf("lats"), "Colgado", "Arriba", floor = false),
    "pulldown" to m(SEATED.arms(p(23, 13.5), p(23.5, 6.5)), SEATED.arms(p(19.5, 27.5), p(24, 21.5)), setOf("ua", "fa", "upper"), listOf("biceps"),
        listOf("lats"), extra = listOf(SEAT, SEAT_LEG), anchor = p(23.5, -3)),
    "straight-pulldown" to m(STAND.arms(p(30, 11.5), p(35.5, 8)), STAND.arms(p(26, 17), p(26.5, 23)), setOf("ua"), emptyList(), listOf("lats"),
        anchor = p(44, -2)),
    "pullover" to m(LIE.arms(p(31, 22), p(36, 20)), LIE.arms(p(25.8, 20), p(25.6, 14)), setOf("ua", "upper"), listOf("chest"), listOf("lats"),
        props = listOf("bench")),
    "hang" to m(HANG, HANG, setOf("fa"), listOf("forearms"), listOf("lats"), "Agarre", "Aguanta", floor = false),
    // Brazos
    "curl" to m(STAND.arms(p(24.6, 17.5), p(25.2, 23.6)), STAND.arms(p(24.6, 17.5), p(29.8, 13.8)), setOf("ua"), listOf("biceps"), emptyList()),
    "curl-preacher" to m(SEATED.arms(p(29, 24), p(34.5, 28.5)), SEATED.arms(p(29, 24), p(28, 17)), setOf("ua"), listOf("biceps"), emptyList(),
        extra = listOf(SEAT, SEAT_LEG, Mark("equip", "M26 26L33 31v2h-7", fill = true))),
    "wrist-curl" to m(SEATED.arms(p(27, 28.5), p(33, 29)), SEATED.arms(p(27, 28.5), p(33, 27.2)), setOf("fa"), listOf("forearms"), emptyList(),
        extra = listOf(SEAT, SEAT_LEG)),
    "triceps-overhead" to m(STAND.arms(p(25.5, 4), p(20.5, 7.5)), STAND.arms(p(25, 4.5), p(25.5, -1.5)), setOf("ua"), emptyList(), listOf("triceps")),
    "pushdown" to m(STAND.arms(p(24.4, 17.5), p(29, 13.5)), STAND.arms(p(24.4, 17.5), p(26, 23.8)), setOf("ua"), emptyList(), listOf("triceps"),
        anchor = p(30, -3)),
    "kickback" to m(HINGE_B.arms(p(23.5, 22), p(25, 28)), HINGE_B.arms(p(23.5, 22), p(17.5, 21)), setOf("ua"), emptyList(), listOf("triceps")),
    "skull-crusher" to m(LIE.arms(p(25.8, 20), p(25.6, 14)), LIE.arms(p(25.8, 20), p(31, 22.5)), setOf("ua"), emptyList(), listOf("triceps"),
        props = listOf("bench")),
    "grip" to m(STAND.arms(p(24.6, 17.5), p(25.2, 23.6)), STAND.arms(p(24.6, 17.5), p(25.2, 23.6)), setOf("fa"), listOf("forearms"), emptyList(),
        "Agarre", "Aprieta"),
    // Hombros
    "lateral-raise" to m(FRONT, FRONT.copy(e1 = p(30.5, 11.5), w1 = p(36.5, 11.5), e2 = p(17.5, 11.5), w2 = p(11.5, 11.5)), setOf("ua"),
        listOf("delts"), listOf("delts")),
    "front-raise" to m(STAND.arms(p(24.3, 17.5), p(24.6, 23.5)), STAND.arms(p(30, 11), p(36, 11.5)), setOf("ua"), listOf("delts"), emptyList()),
    "rear-delt" to m(HINGE_B, HINGE_B.copy(e1 = p(31, 15), w1 = p(36.5, 12.5), e2 = p(26, 15), w2 = p(20.5, 12.5)), setOf("ua", "upper"),
        emptyList(), listOf("delts", "upper")),
    "upright-row" to m(STAND.arms(p(24.6, 17.5), p(26.5, 23)), STAND.arms(p(21, 9.5), p(26.5, 11.5)), setOf("ua"), listOf("delts"), listOf("traps")),
    "shrug" to m(FRONT, FRONT.copy(s = p(24, 9), e1 = p(28, 15.1), w1 = p(29, 21.1), e2 = p(20, 15.1), w2 = p(19, 21.1)), setOf("upper"),
        emptyList(), listOf("traps")),
    "external-rotation" to m(STAND.arms(p(24.4, 17.5), p(30, 17.6)), STAND.arms(p(24.4, 17.5), p(27.5, 12.2)), setOf("ua"), emptyList(),
        listOf("delts", "upper")),
    "ytw" to m(PRONE.copy(e1 = p(35, 39), w1 = p(40, 37.4)), PRONE.copy(e1 = p(35, 36.6), w1 = p(40.5, 33)), setOf("ua", "upper"),
        emptyList(), listOf("delts", "upper")),
    "neck" to m(STAND, STAND.copy(s = p(24.6, 10.6)), setOf("upper"), listOf("neck"), listOf("neck"), "Recto", "Empuja"),
    // Pierna aislada
    "leg-extension" to m(SEATED.copy(k1 = p(31, 32.4), a1 = p(30, 42.8), t1 = p(33.5, 43.8)), SEATED.copy(k1 = p(31, 32.4), a1 = p(40.5, 30.8), t1 = p(41.5, 27.5)),
        setOf("th"), LEGS, emptyList(), extra = listOf(SEAT, SEAT_LEG)),
    "leg-curl" to m(PRONE, PRONE.copy(a1 = p(13.5, 34), t1 = p(16, 31.5)), setOf("th"), emptyList(), listOf("hams"),
        extra = listOf(Mark("equip", "M6 44.6h32v-0.8h-32", fill = true))),
    "abduction" to m(FRONT, FRONT.copy(k1 = p(29.5, 32), a1 = p(34, 41), t1 = p(36, 42)), setOf("th"), emptyList(), GLUTES),
    "adduction" to m(FRONT.copy(k1 = p(29.5, 32), a1 = p(34, 41), t1 = p(36, 42)), FRONT, setOf("th"), listOf("adductors"), emptyList()),
    "hip-flexion" to m(STAND, STAND.copy(k1 = p(33, 21), a1 = p(33.5, 31), t1 = p(38, 31.5)), setOf("th"), LEGS, emptyList()),
    "kickback-leg" to m(QUAD, QUAD.copy(k1 = p(9, 31.5), a1 = p(1, 31), t1 = p(-0.5, 29)), setOf("th"), emptyList(), GLUTES),
    "fire-hydrant" to m(QUAD, QUAD.copy(k1 = p(18, 36), a1 = p(10, 34)), setOf("th"), emptyList(), GLUTES),
    "monster-walk" to m(FRONT.copy(s = p(24, 13), hip = p(24, 25), k1 = p(27, 34), k2 = p(21, 34)), FRONT.copy(s = p(24, 13), hip = p(24, 25), k1 = p(29.5, 34), a1 = p(31, 42.3), t1 = p(33, 43.5), k2 = p(21, 34)),
        setOf("th"), emptyList(), GLUTES),
    "nordic" to m(KNEEL, KNEEL.copy(s = p(36, 28), e1 = p(37.5, 34), w1 = p(40, 39), e2 = p(37, 34.3), w2 = p(39.5, 39.3)), setOf("th"),
        emptyList(), listOf("hams"), "Arriba", "Abajo"),
    // Core
    "plank" to m(KNEEL.copy(s = p(36, 33), hip = p(24.5, 37), e1 = p(36, 43.3), w1 = p(42, 43.6), e2 = p(35.3, 43.3), w2 = p(41.3, 43.6)), ELBOW_PLANK,
        setOf("torso"), listOf("abs"), listOf("erectors"), "Apoyo", "Aguanta"),
    "plank-high" to m(PLANK, PLANK, setOf("torso"), listOf("abs"), listOf("erectors"), "Apoyo", "Aguanta"),
    "side-plank" to m(SIDE_PLANK.copy(k1 = p(16, 43), a1 = p(8, 43.5), k2 = p(16, 42.6), a2 = p(8, 43.2), hip = p(25, 41.5)), SIDE_PLANK,
        setOf("torso"), listOf("obliques"), listOf("erectors"), "Apoyo", "Aguanta"),
    "crunch" to m(SUPINE, SUPINE.copy(s = p(19.5, 35.5), e1 = p(17, 31), w1 = p(15, 34.5), e2 = p(17.6, 31.3), w2 = p(15.6, 34.8)), setOf("torso"),
        listOf("abs"), emptyList()),
    "sit-up" to m(SUPINE, SUPINE.copy(s = p(26, 29), e1 = p(29, 33.5), w1 = p(33, 30), e2 = p(28.4, 33.8), w2 = p(32.4, 30.3)), setOf("torso"),
        listOf("abs"), emptyList()),
    "reverse-crunch" to m(SUPINE.copy(k1 = p(30, 30), a1 = p(37, 33), t1 = p(39, 31)), SUPINE.copy(hip = p(27, 39), k1 = p(24.5, 28.5), a1 = p(31, 25), t1 = p(32.5, 23)),
        setOf("torso"), listOf("abs"), emptyList()),
    "leg-raise" to m(SUPINE.copy(k1 = p(36.5, 41.6), a1 = p(44.5, 41.6), t1 = p(46, 39.8)), SUPINE.copy(k1 = p(28.5, 31.5), a1 = p(29, 22.5), t1 = p(27, 21)),
        setOf("th"), listOf("abs"), emptyList()),
    "hanging-raise" to m(HANG, HANG.copy(k1 = p(31.5, 26.5), a1 = p(40, 26), t1 = p(42, 24), k2 = p(31, 27), a2 = p(39.5, 26.5), t2 = p(41.5, 24.5)),
        setOf("th", "torso"), listOf("abs"), emptyList(), "Colgado", "Arriba", floor = false),
    "twist" to m(pose(16, 31, 22, 40.5, 29, 33, 35, 36, 37.5, 34.5, 28.4, 33.3, 34.4, 36.3, 36.9, 34.8, 21, 34.5, 27, 34.5, 20.4, 34.8, 26.4, 34.8),
        pose(16, 31, 22, 40.5, 29, 33, 35, 36, 37.5, 34.5, 28.4, 33.3, 34.4, 36.3, 36.9, 34.8, 15.5, 37, 20.5, 40, 14.9, 37.3, 19.9, 40.3),
        setOf("torso"), listOf("obliques", "abs"), emptyList(), "Un lado", "El otro"),
    "dead-bug" to m(SUPINE.copy(k1 = p(26.5, 33), a1 = p(33, 33), t1 = p(34.5, 31), e1 = p(16.5, 35), w1 = p(17, 29)),
        SUPINE.copy(k1 = p(35, 39), a1 = p(44, 39.5), t1 = p(45.5, 37.5), e1 = p(10.5, 38.5), w1 = p(5, 40)),
        setOf("torso"), listOf("abs"), emptyList()),
    "bird-dog" to m(QUAD, QUAD.copy(k1 = p(9, 31.5), a1 = p(1, 31), t1 = p(-0.5, 29), e1 = p(37.5, 30.5), w1 = p(43, 30.5)), setOf("torso"),
        emptyList(), listOf("erectors", "glutes")),
    "superman" to m(PRONE, PRONE.copy(s = p(29.5, 38.6), e1 = p(35.5, 36.6), w1 = p(41, 35), k1 = p(9, 40), a1 = p(1.5, 38), t1 = p(0, 36.5)),
        setOf("torso"), emptyList(), listOf("erectors", "glutes")),
    "rollout" to m(KNEEL.copy(e1 = p(26, 32), w1 = p(28, 40.5)), KNEEL.copy(s = p(35, 34), hip = p(24, 38), e1 = p(40, 37), w1 = p(44.5, 40.5)),
        setOf("torso"), listOf("abs"), listOf("lats"), extra = emptyList()),
    "carry" to m(STAND.copy(k1 = p(27, 32.5), a1 = p(29, 42), t1 = p(33.5, 43.5), k2 = p(20.5, 32.5), a2 = p(17.5, 41.8), t2 = p(21, 43.5)),
        STAND.copy(k1 = p(20.5, 32.5), a1 = p(17.5, 41.8), t1 = p(21, 43.5), k2 = p(27, 32.5), a2 = p(29, 42), t2 = p(33.5, 43.5)),
        setOf("fa", "torso"), listOf("forearms", "abs"), listOf("traps"), "Paso", "Paso"),
    "carry-overhead" to m(STAND.copy(e1 = p(24.6, 4.5), w1 = p(25, -1)), STAND.copy(e1 = p(24.6, 4.5), w1 = p(25, -1), k1 = p(27, 32.5), a1 = p(29, 42)),
        setOf("ua", "torso"), listOf("delts", "abs"), emptyList(), "Paso", "Paso"),
    "side-bend" to m(FRONT, FRONT.copy(s = p(21.5, 11), e1 = p(25.5, 17), w1 = p(26.5, 23), e2 = p(17.5, 17.2), w2 = p(16, 23.6)), setOf("torso"),
        listOf("obliques"), emptyList()),
    "woodchop" to m(STAND.arms(p(21, 6), p(17.5, 1.5)), SQ_B.copy(s = p(25, 20)).arms(p(30, 26), p(33, 32)), setOf("torso"), listOf("obliques", "abs"),
        emptyList(), anchor = p(2, -2)),
    "anti-rotation" to m(STAND.arms(p(27.5, 17), p(27, 16.5)), STAND.arms(p(30, 16.5), p(36, 16.5)), setOf("torso"), listOf("obliques", "abs"),
        emptyList(), anchor = p(46, 17)),
    "l-sit" to m(SIT.copy(s = p(17, 28), hip = p(17.5, 40.5), e1 = p(17.2, 34.5), w1 = p(17.5, 41)), SIT.copy(s = p(17, 24), hip = p(17.5, 36.5),
        k1 = p(28, 36.5), a1 = p(38.5, 36.5), t1 = p(40, 34.5), k2 = p(27.4, 36.8), a2 = p(37.9, 36.8), t2 = p(39.4, 34.8), e1 = p(17.2, 30.5), w1 = p(17.5, 37)),
        setOf("torso", "th"), listOf("abs"), emptyList(), "Apoyo", "Aguanta"),
    "get-up" to m(SUPINE.copy(e1 = p(16.5, 35), w1 = p(17, 29)), pose(24, 11, 23.5, 23, 31, 32.5, 31, 43, 35.5, 43.5, 17.5, 33.5, 12, 41, 9.5, 43.6, 24.6, 4.5, 25, -1, 23.2, 17, 23.5, 23),
        setOf("torso", "ua"), listOf("abs", "delts"), emptyList()),
    // Olímpicos y acondicionamiento
    "clean" to m(pose(30, 23, 19, 27, 28, 32, 23, 43, 28, 43.5, 27.4, 32.3, 22.4, 43, 27.4, 43.5, 30.5, 30, 30.5, 36.5, 29.8, 30.3, 29.8, 36.8),
        STAND.arms(p(29, 14), p(27, 10.5)), setOf("th", "upper"), LEGS, listOf("glutes", "hams", "traps")),
    "snatch" to m(pose(30, 23, 19, 27, 28, 32, 23, 43, 28, 43.5, 27.4, 32.3, 22.4, 43, 27.4, 43.5, 30.5, 30, 30.5, 36.5, 29.8, 30.3, 29.8, 36.8),
        STAND.arms(p(24.6, 4.5), p(25, -1.5)), setOf("th", "upper"), LEGS, listOf("glutes", "hams", "traps")),
    "high-pull" to m(STAND.arms(p(24.6, 17.5), p(26, 23.5)), STAND.arms(p(20.5, 9.5), p(27, 11.5)), setOf("upper"), emptyList(), listOf("traps", "glutes")),
    "thruster" to m(SQ_B.arms(p(27, 27.4), p(28.6, 24.2)), STAND.arms(p(24.6, 4.5), p(25, -1.5)), setOf("th", "ua"), listOf("quads", "delts"), GLUTES),
    "burpee" to m(PLANK, STAND.arms(p(25, 4.5), p(26, -1)), setOf("th", "torso"), listOf("quads", "chest"), GLUTES, "Al suelo", "Salto"),
    "jumping-jack" to m(FRONT, FRONT.copy(k1 = p(28.5, 33), a1 = p(31, 42.3), t1 = p(32.5, 43.5), k2 = p(19.5, 33), a2 = p(17, 42.3), t2 = p(15.5, 43.5),
        e1 = p(30, 6), w1 = p(32.5, 1), e2 = p(18, 6), w2 = p(15.5, 1)), setOf("th", "sh"), listOf("quads", "delts"), listOf("calves"), "Cerrado", "Abierto"),
    "run" to m(RUN_A, RUN_A.copy(k1 = p(21, 32), a1 = p(16.5, 40.5), t1 = p(19.5, 43), k2 = p(30, 28), a2 = p(27, 36.5), t2 = p(31, 37),
        e1 = p(29, 15.5), w1 = p(33, 11.5), e2 = p(20.5, 16), w2 = p(23.5, 21)), setOf("th", "sh"), LEGS, listOf("glutes", "calves"), "Zancada", "Zancada"),
    "bike" to m(pose(21, 15.5, 14, 25, 23, 25, 24, 34.5, 28, 35, 22, 28.5, 18, 37, 22, 38, 28, 20, 32, 22, 27.4, 20.3, 31.4, 22.3),
        pose(21, 15.5, 14, 25, 22, 29, 18.5, 37.5, 22.5, 38.5, 22.5, 24.5, 24, 34, 28, 35, 28, 20, 32, 22, 27.4, 20.3, 31.4, 22.3),
        setOf("th"), LEGS, listOf("glutes"), "Pedalada", "Pedalada",
        extra = listOf(Mark("equip", "M14 26L22 36M22 36L32 22", 1.6f), Mark("plate", x = 21f, y = 36f, r = 5.2f), Mark("equip", "M17 44.6L21 36L25 44.6", 1.6f))),
    "row-machine" to m(SIT.arms(p(21, 28.5), p(27, 30)).copy(s = p(19, 28), hip = p(14, 40.6), k1 = p(22, 31), a1 = p(29, 40), t1 = p(32, 38)),
        SIT.arms(p(9.5, 29.5), p(15.5, 32)).copy(s = p(13, 29), hip = p(24, 40.6), k1 = p(33.5, 39.5), a1 = p(40, 41), t1 = p(43, 38.5)),
        setOf("th", "ua", "upper"), LEGS, listOf("lats", "upper"), extra = listOf(Mark("equip", "M4 42.6h42", 1.6f)), anchor = p(44, 34)),
    "rope" to m(FRONT.copy(e1 = p(27.5, 17), w1 = p(30.5, 21.5), e2 = p(20.5, 17), w2 = p(17.5, 21.5)),
        FRONT.copy(hip = p(24, 21), s = p(24, 9), k1 = p(26, 31.5), a1 = p(26.5, 40.8), t1 = p(28.2, 41.8), k2 = p(22, 31.5), a2 = p(21.5, 40.8), t2 = p(19.8, 41.8),
            e1 = p(27.5, 15.5), w1 = p(30.5, 20), e2 = p(20.5, 15.5), w2 = p(17.5, 20)),
        setOf("sh"), emptyList(), listOf("calves"), "Apoyo", "Salto", extra = listOf(Mark("equip", "M30.5 21.5L35 33L31 44.4L17 44.4L13 33L17.5 21.5", 0.9f))),
    "battle-rope" to m(SQ_A.copy(s = p(24, 14), hip = p(20.5, 25.5)).arms(p(29, 17), p(34, 21)), SQ_A.copy(s = p(24, 14), hip = p(20.5, 25.5)).arms(p(29, 22.5), p(34, 27)),
        setOf("ua", "fa"), listOf("delts"), listOf("upper"), "Arriba", "Abajo", extra = listOf(Mark("equip", "M34 24L38 20L42 27L46 22", 1.4f))),
    "sled" to m(pose(31, 18, 20, 25, 27, 32, 26, 43, 30.5, 43.5, 16, 34, 9, 42, 6.5, 43.6, 35, 21, 39, 23, 34.4, 21.3, 38.4, 23.3),
        pose(31, 18, 20, 25, 16, 34, 9, 42, 6.5, 43.6, 27, 32, 26, 43, 30.5, 43.5, 35, 21, 39, 23, 34.4, 21.3, 38.4, 23.3),
        setOf("th"), LEGS, listOf("glutes", "calves"), "Paso", "Paso", extra = listOf(Mark("equip", "M38 21v8h8v15.6h-8", fill = true))),
    "slam" to m(STAND.arms(p(25, 4.5), p(26, -1)), SQ_B.arms(p(28.5, 30), p(32, 36.5)), setOf("ua", "torso"), listOf("abs", "delts"), listOf("lats")),
    "throw" to m(SQ_B.arms(p(27, 27.4), p(28.6, 24.2)), STAND.arms(p(29.5, 7), p(33, 2.5)), setOf("th", "ua"), listOf("quads", "delts"), GLUTES),
    "chest-pass" to m(STAND.arms(p(27.5, 17.5), p(29, 14)), STAND.arms(p(30, 13), p(36, 13)), setOf("ua"), listOf("chest"), listOf("triceps")),
    "crawl" to m(QUAD.copy(hip = p(18, 29), k1 = p(22, 37.5), a1 = p(15, 43.5), t1 = p(17, 44)), QUAD.copy(hip = p(18, 29), k2 = p(22, 37.5), a2 = p(15, 43.5), t2 = p(17, 44),
        e1 = p(36, 37), w1 = p(39, 43)), setOf("torso", "ua"), listOf("abs", "delts"), emptyList(), "Paso", "Paso"),
    "box-step" to m(STAND, pose(32, 4.5, 31.5, 16.5, 32, 27.5, 31.5, 38.4, 36, 38.6, 31, 26.5, 29, 34, 33, 35, 32.3, 11.5, 32.6, 17.5, 31.6, 11.8, 31.9, 17.8),
        setOf("th"), LEGS, GLUTES, props = listOf("step")),
    "punch" to m(STAND.arms(p(27, 15), p(29, 10.5)), STAND.arms(p(30, 11), p(36, 10.5)), setOf("ua"), listOf("delts"), emptyList(), "Guardia", "Golpe"),
    // Movilidad y estiramientos
    "stretch-fold" to m(STAND, pose(30.5, 24, 22, 20, 23.5, 31.5, 23, 42.3, 28, 43.5, 22.8, 31.5, 22.3, 42.3, 27.3, 43.5, 30, 31, 28, 37.5, 29.4, 31.3, 27.4, 37.8),
        emptyList(), emptyList(), listOf("hams"), "Inicio", "Estira"),
    "stretch-seated" to m(SIT, SIT.copy(s = p(26.5, 32), e1 = p(32, 35.5), w1 = p(37.5, 38.5)), emptyList(), emptyList(), listOf("hams", "erectors"), "Sentado", "Estira"),
    "stretch-quad" to m(STAND, STAND.copy(k1 = p(23.5, 33), a1 = p(16.5, 29), t1 = p(14.5, 25.5), e1 = p(21, 21), w1 = p(17, 27.5)), emptyList(),
        listOf("quads"), emptyList(), "De pie", "Estira"),
    "stretch-lunge" to m(KNEEL, pose(23, 19, 22, 31, 31, 33, 31, 43, 35.5, 43.5, 15, 41, 6, 43.4, 3.5, 43.6, 23.5, 25.5, 24, 31.5, 22.8, 25.8, 23.3, 31.8),
        emptyList(), listOf("quads"), emptyList(), "De rodillas", "Estira"),
    "stretch-calf" to m(STAND, pose(33, 14, 27, 25, 33.5, 33.5, 34, 42.3, 38.5, 43.5, 21, 34.5, 14, 42.5, 12, 43.6, 37.5, 16, 42.4, 16.5, 37, 16.3, 41.9, 16.8),
        emptyList(), emptyList(), listOf("calves"), "De pie", "Estira", extra = listOf(WALL)),
    "stretch-chest" to m(STAND.arms(p(24.3, 17.5), p(24.6, 23.5)), STAND.arms(p(19.5, 13), p(14.5, 12)), emptyList(), listOf("chest"), emptyList(),
        "De pie", "Estira", extra = listOf(Mark("equip", "M13 2v42.6", 1.6f))),
    "stretch-arm" to m(FRONT, FRONT.copy(e1 = p(20.5, 14), w1 = p(16, 15.5)), emptyList(), listOf("delts"), listOf("triceps"), "De pie", "Estira"),
    "stretch-side" to m(FRONT, FRONT.copy(s = p(21.5, 11), e1 = p(28, 4.5), w1 = p(23.5, -0.5)), emptyList(), listOf("obliques"), listOf("lats"), "De pie", "Estira"),
    "stretch-butterfly" to m(SIT.copy(k1 = p(25, 34), a1 = p(19, 41), t1 = p(17.5, 40), k2 = p(24.4, 34.3), a2 = p(18.4, 41.3), t2 = p(16.9, 40.3)),
        SIT.copy(s = p(22, 31), k1 = p(27, 39), a1 = p(19, 41), t1 = p(17.5, 40), k2 = p(26.4, 39.3), a2 = p(18.4, 41.3), t2 = p(16.9, 40.3), e1 = p(22, 37), w1 = p(19.5, 41)),
        emptyList(), listOf("adductors"), emptyList(), "Sentado", "Estira"),
    "stretch-pigeon" to m(QUAD, pose(26, 30, 18, 37, 26, 40.5, 16, 42, 14, 41, 9, 41.5, 1, 43, -0.5, 43.4, 30, 36, 31, 43, 29.4, 36.3, 30.4, 43.3),
        emptyList(), emptyList(), GLUTES, "A cuatro patas", "Estira"),
    "stretch-twist" to m(SUPINE.copy(k1 = p(28, 41.5), a1 = p(37, 41.6), t1 = p(39, 40), e1 = p(12, 41), w1 = p(6, 41.2)),
        SUPINE.copy(k1 = p(31, 35), a1 = p(33, 43), t1 = p(36, 43.5), e1 = p(12, 41), w1 = p(6, 41.2)), emptyList(), listOf("obliques"),
        listOf("glutes"), "Tumbado", "Gira"),
    "child" to m(KNEEL, pose(29, 38, 19, 37, 22, 43, 13, 43.4, 10, 43.6, 21.4, 43, 12.4, 43.4, 9.4, 43.6, 35, 41.5, 41, 42.5, 34.4, 41.8, 40.4, 42.8),
        emptyList(), emptyList(), listOf("erectors", "lats"), "De rodillas", "Estira"),
    "cobra" to m(PRONE, PRONE.copy(s = p(29, 33), e1 = p(30.5, 38.5), w1 = p(32, 43)), emptyList(), listOf("abs"), emptyList(), "Tumbado", "Estira"),
    "cat-cow" to m(QUAD.copy(s = p(32, 28.5)), QUAD.copy(s = p(32, 33)), setOf("torso"), emptyList(), listOf("erectors"), "Arriba", "Abajo"),
    "foam-roll" to m(PRONE.copy(s = p(30, 37), hip = p(18, 38.5), e1 = p(32, 41), w1 = p(34, 43.5)), PRONE.copy(s = p(33, 37), hip = p(21, 38.5), e1 = p(34, 41), w1 = p(36, 43.5)),
        emptyList(), listOf("quads"), emptyList(), "Ida", "Vuelta", extra = listOf(ROLLER)),
    "circles" to m(STAND.arms(p(28, 13.5), p(32.5, 9.5)), STAND.arms(p(21.5, 6), p(19.5, 0.5)), emptyList(), listOf("delts"), emptyList(), "Gira", "Gira"),
    "leg-swing" to m(STAND.copy(k1 = p(30, 31.5), a1 = p(35.5, 39.5), t1 = p(39.5, 39)), STAND.copy(k1 = p(19, 32), a1 = p(13, 39), t1 = p(12, 42)),
        emptyList(), emptyList(), listOf("hams", "glutes"), "Delante", "Atrás"),
    "squat-deep" to m(STAND, pose(23.5, 26, 19, 37, 28, 34, 24, 43, 29, 43.5, 27.4, 34.3, 23.4, 43, 28.4, 43.5, 26, 31, 27.5, 35.5, 25.4, 31.3, 26.9, 35.8),
        emptyList(), listOf("adductors"), GLUTES, "De pie", "Abajo"),
    "breathing" to m(SUPINE, SUPINE, setOf("torso"), listOf("abs"), emptyList(), "Inspira", "Espira"),
    // Yoga y pilates
    "yoga-stand" to m(STAND, STAND.arms(p(24.5, 4), p(24.8, -1.5)), emptyList(), emptyList(), emptyList(), "Inicio", "Postura"),
    "yoga-chair" to m(STAND, SQ_A.copy(s = p(25, 14), hip = p(18.5, 26), k1 = p(27, 31), a1 = p(23, 43), t1 = p(28, 43.5), k2 = p(26.4, 31.3), a2 = p(22.4, 43), t2 = p(27.4, 43.5),
        e1 = p(27.5, 8), w1 = p(30, 2.5), e2 = p(26.9, 8.3), w2 = p(29.4, 2.8)), setOf("th"), LEGS, GLUTES, "Inicio", "Postura"),
    "yoga-tree" to m(STAND, STAND.copy(k1 = p(29.5, 28.5), a1 = p(24.5, 31), t1 = p(23.5, 30), e1 = p(24.5, 4), w1 = p(24.8, -1.5)), emptyList(),
        emptyList(), GLUTES, "Inicio", "Postura"),
    "yoga-warrior" to m(STAND, LUNGE_B.copy(s = p(23, 19.5), k2 = p(16, 39), a2 = p(8, 43), t2 = p(6.5, 43.6), e1 = p(24, 13.5), w1 = p(24.5, 8), e2 = p(23.4, 13.8), w2 = p(23.9, 8.3)),
        setOf("th"), LEGS, GLUTES, "Inicio", "Postura"),
    "yoga-triangle" to m(FRONT, pose(20, 13.5, 24, 23, 29.5, 32.5, 33, 42.3, 35, 43.5, 18.5, 32.5, 15, 42.3, 13, 43.5, 20, 6.5, 20.5, 0.5, 18.5, 20, 16.5, 27),
        emptyList(), listOf("obliques"), listOf("hams"), "Inicio", "Postura"),
    "yoga-dog" to m(PLANK, pose(33, 30, 22, 19, 15.5, 30, 9, 42, 7.5, 44, 15.5, 30.4, 9, 42.4, 7.5, 44.4, 35.5, 36.5, 38, 43, 34.8, 36.8, 37.3, 43.3),
        emptyList(), emptyList(), listOf("hams", "calves", "lats"), "Inicio", "Postura"),
    "yoga-boat" to m(SIT, SIT.copy(s = p(15.5, 29), hip = p(20, 40), k1 = p(27, 32.5), a1 = p(34, 27), t1 = p(35.5, 25), k2 = p(26.4, 32.8), a2 = p(33.4, 27.3), t2 = p(34.9, 25.3),
        e1 = p(21, 32.5), w1 = p(27, 31)), setOf("torso"), listOf("abs"), emptyList(), "Sentado", "Postura"),
    "yoga-wheel" to m(SUPINE, pose(20, 33, 24, 27, 31, 32, 32.5, 43, 37, 43.5, 30.4, 32.3, 31.9, 43, 36.4, 43.5, 15, 37, 11, 43, 14.4, 37.3, 10.4, 43.3),
        setOf("torso"), listOf("abs", "quads"), emptyList(), "Tumbado", "Postura"),
    "yoga-shoulder-stand" to m(SUPINE, pose(16, 40.5, 17, 29, 18, 18.5, 18.5, 8, 21, 6.5, 17.4, 18.5, 17.9, 8, 20.4, 6.5, 18.5, 38, 21.5, 34, 17.9, 38.3, 20.9, 34.3),
        setOf("torso"), listOf("abs"), emptyList(), "Tumbado", "Postura"),
    "yoga-crow" to m(SQ_B.arms(p(30, 31), p(31, 43)), pose(30, 30, 21, 33, 27.5, 31, 22, 35.5, 19, 34, 27, 31.4, 21.5, 35.9, 18.5, 34.4, 30.5, 36.5, 31, 43, 29.8, 36.8, 30.3, 43.3),
        setOf("ua", "torso"), listOf("abs", "delts"), emptyList(), "Inicio", "Postura"),
    "yoga-lying" to m(SUPINE.copy(k1 = p(35, 41.6), a1 = p(44, 41.6), t1 = p(46, 40), e1 = p(22, 41.6), w1 = p(28, 41.8)),
        SUPINE.copy(k1 = p(35, 41.6), a1 = p(44, 41.6), t1 = p(46, 40), e1 = p(22, 41.6), w1 = p(28, 41.8)), emptyList(), emptyList(), emptyList(), "Tumbado", "Respira"),
    "pilates-hundred" to m(SUPINE, SUPINE.copy(s = p(19, 36), k1 = p(32.5, 32.5), a1 = p(40, 28), t1 = p(41.5, 26.5), e1 = p(24, 37.5), w1 = p(30, 38.5)),
        setOf("torso"), listOf("abs"), emptyList()),
    "pilates-side-kick" to m(pose(12, 39, 24, 41.5, 33, 42, 42, 42.5, 44, 41, 33, 42.4, 42, 42.9, 44, 41.4, 9, 36, 7, 32, 12, 41, 18, 41.5),
        pose(12, 39, 24, 41.5, 33, 37.5, 41, 33, 43, 31, 33, 42.4, 42, 42.9, 44, 41.4, 9, 36, 7, 32, 12, 41, 18, 41.5),
        setOf("th"), emptyList(), GLUTES),
)

// ------------------------------------------------------------ qué movimiento y qué material lleva cada ejercicio

/** El material que se dibuja: de su lista (Library) y, si hace falta, de su nombre. */
internal fun kitFor(name: String, equipment: List<String>): String? {
    val n = plain(name)
    return when {
        "pesa rusa" in n || "pesas rusas" in n -> "kb"
        "mancuerna" in n -> "db"
        "balon" in n -> "ball"
        "disco" in n -> "bar"
        "barra" in n && "dominadas" !in n -> "bar"
        "mancuernas" in equipment -> "db"
        "pesa rusa" in equipment -> "kb"
        "barra" in equipment -> "bar"
        "balón medicinal" in equipment -> "ball"
        else -> null
    }
}

/** Movimiento de cada ejercicio por reglas sobre su nombre; si ninguna encaja, el general de su patrón. */
internal fun moveFor(name: String, pattern: String, equipment: List<String>): String {
    val n = plain(name)
    fun has(vararg w: String) = w.any { it in n }
    val kit = kitFor(name, equipment)
    return when (pattern) {
        "sentadilla" -> when {
            has("prensa", "hack", "pendulo", "tumbado en maquina") -> "leg-press"
            has("pared", "isometrica") -> "squat-wall"
            has("por encima", "overhead", "arrancada") -> "squat-overhead"
            has("frontal", "copa", "goblet", "zercher", "abrazando", "balon", "mina", "frankenstein", "pesa rusa") -> "squat-front"
            has("levantarse del banco", "a un banco a una pierna", "pistol", "pistola", "camaron", "patinador", "cosaca") -> "squat-bw"
            kit == "bar" -> "squat-back"
            kit == "db" -> "squat-sides"
            else -> "squat-bw"
        }
        "bisagra" -> when {
            has("buenos dias") -> "good-morning"
            has("swing", "balanceo") -> "swing"
            has("empuje de cadera", "hip thrust") -> "hip-thrust"
            has("puente") -> "bridge"
            has("hiperextension", "extension de espalda", "glute-ham", "ghd") -> "back-extension"
            has("curl nordico") -> "nordic"
            has("patada") -> "kickback-leg"
            has("tiron alto", "encogimiento de cargada") -> "high-pull"
            has("arrancada", "equilibrio de arrancada") -> "snatch"
            has("cargada", "tiron de") -> "clean"
            else -> "hinge"
        }
        "zancada" -> when {
            has("subida", "cajon", "step") && !has("zancada al cajon", "deficit") -> "step-up"
            has("bulgara") -> "bulgarian"
            has("lateral", "cosaca") -> "lateral-lunge"
            has("por encima") -> "lunge-overhead"
            has("dividida") -> "split-squat"
            kit == "bar" && !has("mina") -> "lunge-back"
            else -> "lunge"
        }
        "gemelo" -> when {
            has("tibial", "puntas") -> "tibialis"
            has("sentado") -> "calf-seated"
            else -> "calf"
        }
        "salto" -> when {
            has("cajon", "banco", "caida") -> "box-jump"
            has("tobillo", "pogo", "comba") -> "hop"
            has("brincos", "bounding", "longitud", "triple", "zancada") -> "bound"
            else -> "jump"
        }
        "empuje horizontal" -> when {
            has("fondos en banco", "fondos en el suelo") -> "bench-dip"
            has("fondos") -> "dip"
            has("jersey", "pullover") -> "pullover"
            has("apertura", "contractora", "cruce", "butterfly") -> if (has("polea", "goma")) "cable-fly" else "fly"
            has("pase de pecho", "lanzamiento") -> "chest-pass"
            has("inclinadas", "en la pared", "manos en fitball") -> "push-up-incline"
            has("flexion") -> "push-up"
            has("suelo") -> "floor-press"
            has("polea", "de pie", "goma", "trineo", "correas") -> "chest-pass"
            else -> "bench-press"
        }
        "empuje vertical" -> when {
            has("pino", "wall walk", "caminata a la pared") -> "handstand"
            has("pica") -> "pike-push-up"
            has("sentado", "z sentado") -> "press-seated"
            has("thruster", "sentadilla y press") -> "thruster"
            has("cargada") -> "clean"
            else -> "press"
        }
        "tracción horizontal" -> when {
            has("invertido", "anillas", "correas", "suspension") -> "row-inverted"
            has("tiron a la cara", "face pull") -> "face-pull"
            has("sentado", "remoergometro") -> "row-seated"
            has("de pie en polea", "goma", "trineo", "al cuello") -> "row-standing"
            has("banco", "tumbado", "seal") -> "row-bench"
            has("retraccion") -> "row-standing"
            else -> "row-bent"
        }
        "tracción vertical" -> when {
            has("colgarse") -> "hang"
            has("jersey", "pullover") -> if (has("polea de pie", "maquina")) "straight-pulldown" else "pullover"
            has("brazos rectos") -> "straight-pulldown"
            has("jalon") -> "pulldown"
            has("trepa", "muscle-up") -> "pull-up"
            else -> "pull-up"
        }
        "brazos" -> when {
            has("muneca", "dedos", "pronacion", "supinacion", "rodillo") -> "wrist-curl"
            has("pinza", "grippers") -> "grip"
            has("predicador", "arana", "banco alto") -> "curl-preacher"
            has("curl") -> "curl"
            has("patada") -> "kickback"
            has("fondos en banco", "fondos en el suelo") -> "bench-dip"
            has("fondos") -> "dip"
            has("frances", "tumbado", "jm", "tate", "al menton") -> "skull-crusher"
            has("sobre la cabeza", "tras la cabeza", "jersey") -> "triceps-overhead"
            has("press de triceps", "flexiones", "barra baja") -> "push-up"
            else -> "pushdown"
        }
        "hombros" -> when {
            has("cuello") -> "neck"
            has("encogimiento") -> "shrug"
            has("remo al menton") -> "upright-row"
            has("rotacion externa", "rotacion interna", "press cubano") -> "external-rotation"
            has("en y", "en t", "en w", "en i", "en l") -> "ytw"
            has("pajaro", "deltoides posterior", "aperturas en y") -> "rear-delt"
            has("frontal") -> "front-raise"
            has("halo", "vuelta al mundo", "volante") -> "circles"
            has("wall slide", "deslizamiento", "flexiones escapulares", "extension de hombro") -> "front-raise"
            else -> "lateral-raise"
        }
        "pierna aislada" -> when {
            has("extension de cuadriceps", "curl nordico inverso") -> "leg-extension"
            has("curl femoral") -> "leg-curl"
            has("aduccion", "aductores", "interior") -> "adduction"
            has("monstruo") -> "monster-walk"
            has("boca de incendios") -> "fire-hydrant"
            has("flexion de cadera") -> "hip-flexion"
            has("circulos", "atras de pie") -> "kickback-leg"
            else -> "abduction"
        }
        "core" -> when {
            has("plancha lateral", "navaja lateral", "copenhague", "aduccion en plancha") -> "side-plank"
            has("plancha con pies elevados", "plancha en fitball", "plancha con correas", "plancha sobre bosu") -> "plank-high"
            has("plancha", "pica", "escaladores") -> if (has("pica", "escaladores")) "plank-high" else "plank"
            has("rueda", "rodamiento", "rollout", "fallout") -> "rollout"
            has("colgado", "paralelas", "pica colgado") -> "hanging-raise"
            has("sentada en l", "l-sit") -> "l-sit"
            has("bicho muerto", "dead bug") -> "dead-bug"
            has("superman", "nadador", "arco boca abajo", "extension de espalda") -> "superman"
            has("cuadrupedia", "bird") -> "bird-dog"
            has("giro ruso", "giro sentado", "limpiaparabrisas", "giro completo") -> "twist"
            has("lenador", "giro de torso", "giro con barra") -> "woodchop"
            has("pallof", "antirrotacion") -> "anti-rotation"
            has("paseo por encima", "por encima de la cabeza") -> "carry-overhead"
            has("paseo", "sostener", "granjero", "maleta", "camarero") -> "carry"
            has("inclinacion lateral", "molino") -> "side-bend"
            has("levantamiento turco") -> "get-up"
            has("elevacion de piernas", "bajada", "tijera", "circulos de piernas", "bandera") -> "leg-raise"
            has("crunch inverso", "rodillas al pecho", "encogimiento de rodillas") -> "reverse-crunch"
            has("abdominal", "navaja", "sit") -> "sit-up"
            has("gusano", "inchworm") -> "yoga-dog"
            has("ocho", "pase de pesa") -> "woodchop"
            has("press pallof", "golpe") -> "anti-rotation"
            has("hollow", "posicion hueca") -> "pilates-hundred"
            else -> "crunch"
        }
        "acondicionamiento" -> when {
            has("burpee") -> "burpee"
            has("tijera", "jumping") -> "jumping-jack"
            has("comba") -> "rope"
            has("bicicleta", "eliptica", "escaladora") -> "bike"
            has("remo en maquina", "esqui") -> "row-machine"
            has("cuerda de batalla") -> "battle-rope"
            has("trineo", "yugo", "neumatico") -> "sled"
            has("golpe de balon", "slam", "maza") -> "slam"
            has("lanzamiento de balon desde el pecho", "pase de pecho") -> "chest-pass"
            has("lanzamiento") -> "throw"
            has("gateo") -> "crawl"
            has("escaleras", "cajon") -> "box-step"
            has("boxeo", "saco de boxeo") -> "punch"
            has("cargada") -> "clean"
            has("devil", "thruster") -> "thruster"
            has("paseo", "granjero", "carga de", "al hombro", "piedras") -> "carry"
            else -> "run"
        }
        "movilidad" -> when {
            has("rodillo", "pelota") -> "foam-roll"
            has("isquiotibiales de pie", "inclinacion adelante") -> "stretch-fold"
            has("isquiotibiales", "espalda sobre fitball") -> "stretch-seated"
            has("cuadriceps de pie") -> "stretch-quad"
            has("cuadriceps tumbado", "flexores de cadera", "sofa", "zancada") -> "stretch-lunge"
            has("gemelo", "soleo", "tobillo", "dorsiflexion") -> "stretch-calf"
            has("pectoral", "aperturas de pecho", "biceps") -> "stretch-chest"
            has("hombro", "triceps", "durmiente", "dislocaciones", "antebrazos", "angel") -> "stretch-arm"
            has("lateral de pie", "dorsal", "aperturas de piernas lateral", "apertura de piernas lateral") -> "stretch-side"
            has("mariposa", "rana", "aductores") -> "stretch-butterfly"
            has("paloma", "piriforme", "gluteo", "90/90") -> "stretch-pigeon"
            has("giro", "rodilla al pecho", "libro abierto") -> "stretch-twist"
            has("gato", "cuadrupedia", "enhebrar", "balanceo en cuadrupedia") -> "cat-cow"
            has("cobra", "abdominales", "escorpion") -> "cobra"
            has("sentadilla profunda") -> "squat-deep"
            has("respiracion") -> "breathing"
            has("balanceo de pierna", "patada alta", "rodilla al pecho", "rodilla alta", "valla") -> "leg-swing"
            has("caminata con las manos", "walkout") -> "yoga-dog"
            has("colgarse") -> "hang"
            has("frontal") -> "stretch-lunge"
            else -> "circles"
        }
        "yoga" -> when {
            has("chaturanga", "flexion de pilates") -> "push-up"
            has("apertura de piernas de pie", "pinza de pie") -> "stretch-fold"
            has("silla", "guirnalda", "plie") -> "yoga-chair"
            has("arbol", "aguila", "garza", "media luna") -> "yoga-tree"
            has("guerrero", "zancada", "lagarto") -> "yoga-warrior"
            has("triangulo", "angulo lateral", "puerta", "sirena") -> "yoga-triangle"
            has("perro boca abajo", "delfin", "cachorro") -> "yoga-dog"
            has("perro boca arriba", "cobra", "esfinge", "cisne", "langosta", "arco") -> "cobra"
            has("barca", "teaser", "rodar como", "foca") -> "yoga-boat"
            has("rueda", "camello", "puente", "pez", "mesa invertida") -> "yoga-wheel"
            has("vela", "arado", "rodar hacia atras", "sacacorchos") -> "yoga-shoulder-stand"
            has("cuervo") -> "yoga-crow"
            has("cadaver", "mano al pie", "reloj pelvico") -> "yoga-lying"
            has("nino", "heroe", "rayo") -> "child"
            has("pinza", "cabeza a la rodilla", "bastón", "baston", "estiramiento de columna", "sierra", "angulo atado", "cara de vaca", "facil") -> "stretch-seated"
            has("paloma") -> "stretch-pigeon"
            has("giro") -> "stretch-twist"
            has("gato", "mesa") -> "cat-cow"
            has("plancha", "elevacion de pierna en plancha") -> "plank-high"
            has("cien", "estiramiento de una pierna", "estiramiento de dos piernas", "tijeras", "entrecruzado", "pliegue") -> "pilates-hundred"
            has("rodar hacia arriba", "tiron de cuello") -> "sit-up"
            has("circulos con una pierna", "patada lateral") -> "pilates-side-kick"
            has("natacion", "patada con") -> "superman"
            has("relevé", "releve") -> "calf"
            has("saludo al sol", "montana") -> "yoga-stand"
            else -> "yoga-stand"
        }
        else -> "yoga-stand"
    }
}

/** La figura de un ejercicio, lista para dibujar. */
fun figureView(name: String, pattern: String, equipment: List<String>): FigureView {
    val id = moveFor(name, pattern, equipment)
    val def = MOVES.getValue(id)
    val kit = kitFor(name, equipment)?.takeIf { id !in NO_WEIGHT }
    fun draw(pose: Pose): List<Mark> {
        val marks = drawPose(pose, def.move, kit).toMutableList()
        val cable = def.anchor
        val hand = pose.w1
        val i = marks.indexOfFirst { it.role == "far" }  // la polea o la goma, detrás del muñeco
        if (cable != null) marks.add(i.coerceAtLeast(0), Mark("equip", "M${cable.x} ${cable.y}L${hand.x} ${hand.y}", 0.9f))
        if (def.extra.isNotEmpty()) marks.addAll(i.coerceAtLeast(0), def.extra)
        return marks
    }
    return FigureView(draw(def.move.a), draw(def.move.b), def.move.la, def.move.lb, bodyMap(def.move.front), bodyMap(def.move.back), id)
}

// Movimientos sin peso en la mano aunque el ejercicio lo lleve en el nombre (se apoya o es un estiramiento)
private val NO_WEIGHT = setOf("leg-press", "squat-wall", "push-up", "push-up-incline", "dip", "bench-dip", "handstand", "pike-push-up",
    "row-inverted", "pull-up", "hang", "plank", "plank-high", "side-plank", "crunch", "reverse-crunch", "leg-raise", "hanging-raise",
    "dead-bug", "bird-dog", "superman", "rollout", "l-sit", "leg-curl", "leg-extension", "abduction", "adduction", "hip-flexion",
    "kickback-leg", "fire-hydrant", "monster-walk", "nordic", "back-extension", "bridge", "burpee", "jumping-jack", "run", "bike",
    "row-machine", "rope", "battle-rope", "sled", "crawl", "box-step", "punch", "calf-seated", "tibialis", "hop", "bound",
    "stretch-fold", "stretch-seated", "stretch-quad", "stretch-lunge", "stretch-calf", "stretch-chest", "stretch-arm", "stretch-side",
    "stretch-butterfly", "stretch-pigeon", "stretch-twist", "child", "cobra", "cat-cow", "foam-roll", "circles", "leg-swing",
    "squat-deep", "breathing", "yoga-stand", "yoga-chair", "yoga-tree", "yoga-warrior", "yoga-triangle", "yoga-dog", "yoga-boat",
    "yoga-wheel", "yoga-shoulder-stand", "yoga-crow", "yoga-lying", "pilates-hundred", "pilates-side-kick", "box-jump", "jump",
    "neck", "ytw", "cable-fly", "straight-pulldown", "pulldown", "row-seated", "pushdown", "face-pull", "anti-rotation", "woodchop")
