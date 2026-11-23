package app.brivel.app

import kotlinx.serialization.Serializable
import kotlin.math.hypot

// Figuras G (estilo elegido por Gabriel; muestra de 12 del 2026-11-19): un muñeco con volumen, de perfil o de frente,
// inicio → final, con su material, y el mapa de lo que trabaja delante y detrás. El muñeco va sobre una rejilla de 48 y
// el mapa sobre una de 40 × 80. Cada plataforma dibuja las marcas tal cual (trazos de extremo redondo, círculos y áreas)
// con el color de su papel; aquí solo se decide qué se dibuja.

/**
 * Una marca. `role`: floor, equip, far, okFar, near, ok, plate, hub (color de la tarjeta) o sil (silueta del mapa).
 * Con `d` (polilínea M/L/h/v, la de `polylines`): un trazo de grosor `w` o, con `fill`, un área. Sin `d`: un círculo.
 */
@Serializable
data class Mark(val role: String, val d: String = "", val w: Float = 0f, val x: Float = 0f, val y: Float = 0f,
    val r: Float = 0f, val fill: Boolean = false)

@Serializable
data class FigureView(val start: List<Mark>, val end: List<Mark>, val startLabel: String, val endLabel: String,
    val front: List<Mark>, val back: List<Mark>, val move: String)

internal data class Pt(val x: Float, val y: Float)

/** Hombro, cadera, pierna de delante (rodilla, tobillo, punta), la de atrás y los dos brazos (codo, mano). */
internal data class Pose(val s: Pt, val hip: Pt, val k1: Pt, val a1: Pt, val t1: Pt, val k2: Pt, val a2: Pt, val t2: Pt,
    val e1: Pt, val w1: Pt, val e2: Pt, val w2: Pt) {
    fun arms(e1: Pt, w1: Pt, dx: Float = -0.8f, dy: Float = 0.3f) = copy(e1 = e1, w1 = w1, e2 = Pt(e1.x + dx, e1.y + dy), w2 = Pt(w1.x + dx, w1.y + dy))
}

/** 24 números: s, hip, k1, a1, t1, k2, a2, t2, e1, w1, e2, w2. */
internal fun pose(vararg v: Number): Pose {
    require(v.size == 24)
    fun p(i: Int) = Pt(v[2 * i].toFloat(), v[2 * i + 1].toFloat())
    return Pose(p(0), p(1), p(2), p(3), p(4), p(5), p(6), p(7), p(8), p(9), p(10), p(11))
}

/**
 * Un movimiento: sus dos posturas, lo que se pinta de verde en el muñeco (th muslo, sh pierna, torso, upper espalda
 * alta, ua brazo, fa antebrazo), los músculos del mapa y dónde va el peso (w: en la mano; s: barra a la espalda; c:
 * pegado al pecho). `props`: banco, cajón, barra fija… dibujados detrás del muñeco.
 */
internal class Move(val a: Pose, val b: Pose, val work: Set<String>, val front: List<String>, val back: List<String>,
    val la: String = "Inicio", val lb: String = "Final", val props: List<String> = emptyList(), val hold: String = "w",
    val floor: Boolean = true)

// ------------------------------------------------------------ material

private val BENCH = listOf(Mark("equip", "M4 30h26v3h-26", fill = true), Mark("equip", "M7 33v11M27 33v11", 1.6f))
private val BOX = listOf(Mark("equip", "M28 34h16v10.6h-16", fill = true))
private val STEP = listOf(Mark("equip", "M27 38.6h18v6h-18", fill = true))
private val PROPS: Map<String, List<Mark>> = mapOf("bench" to BENCH, "box" to BOX, "step" to STEP)

/** El peso en (x, y): disco de barra con su centro, mancuerna pequeña con su eje, pesa rusa con asa, balón. */
internal fun weight(kind: String, x: Float, y: Float): List<Mark> = when (kind) {
    "bar" -> listOf(Mark("plate", x = x, y = y, r = 4.2f), Mark("hub", x = x, y = y, r = 0.9f))
    "db" -> listOf(Mark("plate", x = x, y = y, r = 2.4f), Mark("hub", x = x, y = y, r = 0.7f))
    "kb" -> listOf(Mark("plate", "M${x - 1.7f} ${y - 1.6f}L$x ${y - 4.4f}L${x + 1.7f} ${y - 1.6f}", 1.1f),
        Mark("plate", x = x, y = y, r = 2.6f))
    "ball" -> listOf(Mark("plate", x = x, y = y, r = 3.4f))
    "pullbar" -> listOf(Mark("plate", "M${x - 9} ${y}h18", 1.6f))
    else -> emptyList()
}

// ------------------------------------------------------------ dibujo

private fun seg(a: Pt, b: Pt) = "M${a.x} ${a.y}L${b.x} ${b.y}"

private fun head(p: Pose): Pt {
    val dx = p.s.x - p.hip.x; val dy = p.s.y - p.hip.y; val n = hypot(dx, dy)
    return Pt(p.s.x + dx / n * 5.4f, p.s.y + dy / n * 5.4f)
}

/** Las marcas de una postura, de atrás adelante: suelo, material, brazo y pierna de atrás, tronco, los de delante, cabeza. */
internal fun drawPose(p: Pose, m: Move, kit: String?): List<Mark> {
    val w = m.work
    val out = mutableListOf<Mark>()
    if (m.floor) out += Mark("floor", "M1 44.6h46", 0.9f)
    m.props.forEach { out += PROPS[it] ?: emptyList() }
    out += Mark("far", seg(p.s, p.e2), 4.2f); out += Mark("far", seg(p.e2, p.w2), 3.6f)
    if ("ua" in w) out += Mark("okFar", seg(p.s, p.e2), 4.2f)
    out += Mark("far", seg(p.hip, p.k2), 6f); out += Mark("far", seg(p.k2, p.a2), 4.6f); out += Mark("far", seg(p.a2, p.t2), 3f)
    if ("th" in w) out += Mark("okFar", seg(p.hip, p.k2), 6f)
    if ("sh" in w) out += Mark("okFar", seg(p.k2, p.a2), 4.6f)
    out += Mark("near", seg(p.s, p.hip), 7.4f)
    if ("torso" in w) out += Mark("ok", seg(p.s, p.hip), 7.4f)
    if ("upper" in w) out += Mark("ok", seg(p.s, Pt((p.s.x + p.hip.x) / 2, (p.s.y + p.hip.y) / 2)), 7.4f)
    out += Mark("near", seg(p.hip, p.k1), 6f); out += Mark("near", seg(p.k1, p.a1), 4.6f); out += Mark("near", seg(p.a1, p.t1), 3f)
    if ("th" in w) out += Mark("ok", seg(p.hip, p.k1), 6f)
    if ("sh" in w) out += Mark("ok", seg(p.k1, p.a1), 4.6f)
    out += Mark("near", seg(p.s, p.e1), 4.2f); out += Mark("near", seg(p.e1, p.w1), 3.6f)
    if ("ua" in w) out += Mark("ok", seg(p.s, p.e1), 4.2f)
    if ("fa" in w) out += Mark("ok", seg(p.e1, p.w1), 3.6f)
    if (kit != null) out += when (m.hold) {
        "s" -> weight(kit, p.s.x - 0.8f, p.s.y + 0.4f)  // barra a la espalda: el disco en el hombro
        else -> weight(kit, p.w1.x, p.w1.y)
    }
    val h = head(p)
    out += Mark("near", x = h.x, y = h.y, r = 4f)
    return out
}

// El mapa: silueta de frente y, encima, cada músculo siguiendo la línea de su miembro
private val SILHOUETTE = listOf(Mark("sil", x = 20f, y = 8f, r = 6f), Mark("sil", "M20 19L20 39", 15f),
    Mark("sil", "M9 20L6 42M31 20L34 42", 5f), Mark("sil", "M15.5 45L14.5 76M24.5 45L25.5 76", 8f))
internal val MUSCLE_MARKS: Map<String, Mark> = mapOf(
    "quads" to Mark("ok", "M15.4 48L14.95 62M24.6 48L25.05 62", 6.4f),
    "biceps" to Mark("ok", "M8.66 22.5L7.5 31M31.34 22.5L32.5 31", 4f),
    "triceps" to Mark("ok", "M8.66 22.5L7.5 31M31.34 22.5L32.5 31", 4f),
    "forearms" to Mark("ok", "M7.3 33L6.3 41M32.7 33L33.7 41", 3.6f),
    "delts" to Mark("ok", "M10.2 19.6L9.4 22.5M29.8 19.6L30.6 22.5", 6f),
    "chest" to Mark("ok", "M15 23L25 23", 6f),
    "abs" to Mark("ok", "M20 26L20 39", 6f),
    "obliques" to Mark("ok", "M14.6 29L15.4 39M25.4 29L24.6 39", 3f),
    "glutes" to Mark("ok", "M15.45 46.5L15.3 51M24.55 46.5L24.7 51", 8f),
    "hams" to Mark("ok", "M15.2 53.5L14.85 64M24.8 53.5L25.15 64", 6.4f),
    "adductors" to Mark("ok", "M17.6 48L17.2 58M22.4 48L22.8 58", 2.6f),
    "calves" to Mark("ok", "M14.8 65.5L14.65 72M25.2 65.5L25.35 72", 6.4f),
    "shins" to Mark("ok", "M14.8 65.5L14.65 72M25.2 65.5L25.35 72", 3.4f),
    "lats" to Mark("ok", "M15.4 25L17.6 36M24.6 25L22.4 36", 6.4f),
    "upper" to Mark("ok", "M17 20.5L23 20.5", 3.4f),
    "traps" to Mark("ok", "M15.6 18.6L24.4 18.6", 3.4f),
    "erectors" to Mark("ok", "M18.3 30L18.3 42M21.7 30L21.7 42", 2.6f),
    "neck" to Mark("ok", "M20 13.4L20 16", 3.4f),
)

internal fun bodyMap(muscles: List<String>): List<Mark> = SILHOUETTE + muscles.mapNotNull { MUSCLE_MARKS[it] }
