package app.brivel.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryTest {
    @Test fun everyExerciseHasACard() {
        for (name in LIBRARY.keys) {
            val c = exerciseCard(name)!!
            assertTrue(c.equipment.isNotEmpty() && c.equipment.all { it in EQUIPMENT }, name)
            assertEquals(c.pattern in PATTERN_CUES, c.cues.isNotEmpty(), name)
        }
        assertEquals(LIBRARY.size, library().size)
        assertTrue(LIBRARY.size >= 300 && LIBRARY.keys.containsAll(EXERCISES.keys))
        assertEquals(PATTERN_LABEL.keys.toList(), library().map { it.pattern }.distinct())  // agrupada por patrón
        assertTrue(LIBRARY.keys.none { ':' in it })  // el «:» separa el nombre de la dosis en el plan
    }

    @Test fun searchIgnoresAccentsAndFilters() {
        assertEquals(listOf("Zancada búlgara con mancuernas", "Sentadilla búlgara (progresión a sentadilla a una pierna)"),
            library("BULGARA").map { it.name })
        assertTrue(library(pattern = "core").all { it.pattern == "core" })
        assertTrue(library(equipment = "goma").all { "goma" in it.equipment })
        assertTrue(library("traccion vertical").isNotEmpty())
    }

    @Test fun swapsKeepTheSameKindOfExercise() {
        val options = swapOptions("Plancha frontal").map { it.name }
        assertTrue("Plancha lateral" in options && "Bicho muerto (dead bug)" !in options)  // por tiempo, no por repeticiones
        assertTrue(swapOptions("Sentadilla con barra").all { it.pattern == "sentadilla" && it.name != "Sentadilla con barra" })
    }

    @Test fun swapsApplyFromTheirDateOn() {
        val swaps = listOf(ExerciseSwap("2026-11-04", "Sentadilla con barra", "Sentadilla copa (goblet) o con barra"),
            ExerciseSwap("2026-11-10", "Sentadilla con barra", null))
        val line = listOf("Sentadilla con barra: 3 × 8–12 repeticiones")
        assertEquals(line, swapLines(line, swaps, "2026-11-03"))
        assertEquals(listOf("Sentadilla copa (goblet) o con barra: 3 × 8–12 repeticiones"), swapLines(line, swaps, "2026-11-04"))
        assertEquals(line, swapLines(line, swaps, "2026-11-10"))
    }
}
