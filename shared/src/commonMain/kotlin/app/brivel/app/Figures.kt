package app.brivel.app

import kotlinx.serialization.Serializable

// Figuras propias por patrón de movimiento, sobre una rejilla de 48: cabeza, cuerpo y, en verde, lo que trabaja.
// Trazos con solo M, L implícita, h y v para que cada plataforma los dibuje con un lector mínimo.

@Serializable
data class Figure(val headX: Float, val headY: Float, val body: String, val work: String)

val FIGURES: Map<String, Figure> = mapOf(
    "sentadilla" to Figure(27f, 8f, "M26 13 19 26M27 41h6M8 14h28", "M19 26 30 28 27 41"),
    "bisagra" to Figure(35f, 14f, "M31 18 29 30M19 42h6M23 33h12", "M31 18 18 22 20 32 19 42"),
    "zancada" to Figure(24f, 7f, "M24 12v13M17 37 9 39M33 40h4", "M24 25 33 26 33 40M24 25 17 37"),
    "gemelo" to Figure(24f, 6f, "M24 11v14M24 25v9M18 44h16", "M24 34 26 41 31 43"),
    "salto" to Figure(22f, 8f, "M22 13 26 24M22 14 15 8M30 36h14v8M4 44h40", "M26 24 33 21 31 30"),
    "empuje horizontal" to Figure(37f, 21f, "M32 25 8 35 6 37M4 38h40", "M32 25 31 31 33 37"),
    "empuje vertical" to Figure(24f, 13f, "M24 18v14M24 32 20 44M24 32 28 44M11 5h26", "M24 18 18 13 17 5M24 18 30 13 31 5"),
    "tracción horizontal" to Figure(38f, 15f, "M17 21 20 31 18 42 24 42M24 25h7", "M34 18 17 21M33 18 26 12 27.5 24"),
    "tracción vertical" to Figure(24f, 12f, "M8 5h32M24 18v14M24 32 22 44M24 32 26 44", "M18 5 16 13 21 18M30 5 32 13 27 18M21 18h6"),
    "core" to Figure(38f, 21f, "M33 25 33 32 40 32M8 30 7 33M4 34h40", "M33 25 8 30"),
)

/** Los trazos como polilíneas de puntos (x, y): lo que dibujan Android, iOS y la web sin leer SVG. */
fun polylines(path: String): List<List<Pair<Float, Float>>> {
    val out = mutableListOf<MutableList<Pair<Float, Float>>>()
    var x = 0f
    var y = 0f
    Regex("""[MhvL]|-?\d+(?:\.\d+)?""").findAll(path).map { it.value }.toList().let { tokens ->
        var i = 0
        var cmd = "M"
        while (i < tokens.size) {
            val t = tokens[i]
            if (t[0].isLetter()) { cmd = t; i++; continue }
            when (cmd) {
                "h" -> { x += t.toFloat(); i++; out.last() += x to y }
                "v" -> { y += t.toFloat(); i++; out.last() += x to y }
                else -> {
                    x = t.toFloat(); y = tokens[i + 1].toFloat(); i += 2
                    if (cmd == "M") { out += mutableListOf(x to y); cmd = "L" } else out.last() += x to y
                }
            }
        }
    }
    return out
}
