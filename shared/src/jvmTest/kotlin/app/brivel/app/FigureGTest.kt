package app.brivel.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Cada ejercicio de la biblioteca tiene su figura G; con FIGURES_SHEET=<carpeta>, escribe una hoja por patrón para revisarlas. */
class FigureGTest {
    private val colors = mapOf("floor" to "#C9C2B3", "equip" to "#C9C2B3", "far" to "#8C968F", "okFar" to "#5E9C84", "near" to "#1B2420",
        "ok" to "#1F7A5C", "plate" to "#7D766A", "hub" to "#FFFFFF", "sil" to "#E4DED2")

    private fun svg(marks: List<Mark>) = marks.joinToString("") { m ->
        val c = colors.getValue(m.role)
        when {
            m.d.isEmpty() -> """<circle cx="${m.x}" cy="${m.y}" r="${m.r}" fill="$c"/>"""
            m.fill -> """<path d="${m.d}z" fill="$c"/>"""
            else -> """<path d="${m.d}" fill="none" stroke="$c" stroke-width="${m.w}" stroke-linecap="round"/>"""
        }
    }

    @Test fun everyExerciseHasAFigure() {
        val byPattern = LIBRARY.keys.groupBy { LIBRARY.getValue(it).pattern }
        byPattern.forEach { (pattern, names) ->
            names.forEach { n ->
                val f = figureView(n, pattern, exerciseCard(n)!!.equipment)
                assertTrue(f.start.isNotEmpty() && f.end.isNotEmpty(), n)
            }
        }
        val out = System.getenv("FIGURES_SHEET") ?: return
        byPattern.forEach { (pattern, names) ->
            val cols = 6
            val cells = names.sorted().mapIndexed { i, n ->
                val f = figureView(n, pattern, exerciseCard(n)!!.equipment)
                val x = (i % cols) * 250; val y = (i / cols) * 150
                """<g transform="translate($x,$y)"><rect x="4" y="4" width="242" height="142" rx="12" fill="#FFFFFF"/>""" +
                    """<svg x="10" y="8" width="100" height="100" viewBox="0 0 48 48" overflow="visible">${svg(f.start)}</svg>""" +
                    """<svg x="130" y="8" width="100" height="100" viewBox="0 0 48 48" overflow="visible">${svg(f.end)}</svg>""" +
                    """<text x="12" y="124" font-size="11" font-family="sans-serif" fill="#1B2420">${n.take(42)}</text>""" +
                    """<text x="12" y="139" font-size="10" font-family="sans-serif" fill="#5D6762">${f.move}</text></g>"""
            }
            val h = ((names.size + cols - 1) / cols) * 150
            File(out, "${pattern.replace(' ', '-')}.svg").writeText(
                """<svg xmlns="http://www.w3.org/2000/svg" width="${cols * 250}" height="$h"><rect width="100%" height="100%" fill="#F3F0E9"/>${cells.joinToString("")}</svg>""")
        }
    }
}
