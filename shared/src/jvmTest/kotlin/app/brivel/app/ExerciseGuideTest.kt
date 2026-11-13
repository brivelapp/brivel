package app.brivel.app

import app.brivel.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseGuideTest {
    @Test fun everyExerciseIsClassifiedDescribedAndDrawn() {
        for (name in LIBRARY.keys) {
            val c = exerciseCard(name)!!
            assertTrue(c.zone.isNotEmpty() && c.muscles.isNotEmpty() && c.description.endsWith("."), name)
            assertTrue(FIGURES[c.figure] != null, name)
        }
        assertEquals(listOf("bíceps", "braquial"), exerciseCard("Curl con barra")!!.muscles)
        assertEquals(listOf("tríceps"), exerciseCard("Press francés con barra Z")!!.muscles)
        assertTrue(library("pierna").any { it.pattern == "bisagra" } && library("espalda").all { it.zone == "espalda" || "espalda" in plain(it.name) })
    }

    @Test fun variantsKeepPatternMeasureAndComeFromThePlan() {
        for ((original, variants) in ROTATION) {
            val info = EXERCISES[original] ?: error("No lo propone ningún motor: $original")
            assertTrue(variants.isNotEmpty() && original !in variants && variants.distinct() == variants, original)
            variants.forEach { assertEquals(info, LIBRARY[it], "$original → $it") }
        }
    }

    @Test fun variantChangesWithBlockAndSlotOnly() {
        val original = "Peso muerto rumano"
        val all = listOf(original) + ROTATION[original]!!
        assertEquals(all, all.indices.map { rotated(original, it, 0) })  // un bloque, una variante; y vuelve a empezar
        assertEquals(original, rotated(original, all.size, 0))
        assertTrue(rotated(original, 3, 0) != rotated(original, 3, 1))  // dos sesiones de la misma semana no coinciden
        assertEquals(original, rotated(original, 1, 0) { false })  // sin el material de la variante, el del plan
        assertEquals("Ejercicio inventado", rotated("Ejercicio inventado", 1, 0))
        assertEquals(rotationBlock(LocalDate.parse("2026-11-02")) + 1, rotationBlock(LocalDate.parse("2026-11-02").plusDays(28)))
    }

    @Test fun linesRotateWithoutRepeatingAndRespectTheUser() {
        val block = (0..9).first { rotated("Plancha frontal", it, 0) == "Plancha de oso" }
        val lines = listOf("Plancha frontal: 3 × 20–45″", "Plancha de oso: 3 × 20–45″")
        assertEquals(lines to emptyMap(), rotateLines(lines, block, 0))  // la variante ya está en la sesión: se queda el original
        assertEquals(listOf("Plancha de oso: 3 × 20–45″") to mapOf("Plancha de oso" to "Plancha frontal"), rotateLines(lines.take(1), block, 0))
        assertEquals(lines.take(1) to emptyMap(), rotateLines(lines.take(1), block, 0, keep = setOf("Plancha frontal")))
    }

    @Test fun onlySeasonedHealthyAdultsRotate() {
        assertTrue(rotates("intermediate", 35, "vigorous") && rotates("intermediate", null, "vigorous"))
        assertTrue(!rotates("beginner", 35, "vigorous") && !rotates("intermediate", 70, "vigorous") &&
            !rotates("intermediate", 16, "vigorous") && !rotates("intermediate", 35, "moderate"))
    }

    @Test fun oddExercisesDoNotClaimThePatternMovement() {
        assertTrue("rodillas se doblan poco" !in exerciseCard("Cargada de potencia")!!.description)
        assertTrue("rodillas se doblan poco" in exerciseCard("Peso muerto con barra")!!.description)
        assertTrue("desde el pecho" !in exerciseCard("Jersey con mancuerna (pullover)")!!.description)
    }

    @Test fun pinningAVariantKeepsThePlanExercise() {
        val today = LocalDate(2026, 10, 22)
        val app = BrivelApp(MemoryStore(), MemoryStore(), { today })
        app.saveProfile(UserProfile(birthYear = 1990, strength = "gym", level = "intermediate",
            days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT").associateWith { 60 }))
        app.setGoal(GoalKind.STRENGTH)
        // Algún día de las próximas semanas trae una variante puesta por la rotación
        val (date, variant) = (0..60).map { today.plusDays(it).toString() }.firstNotNullOf { d ->
            app.strengthScreen(d)?.exercises?.firstOrNull { it.original != null }?.let { d to it }
        }
        val original = variant.original!!
        assertTrue(variant.name in ROTATION[original]!!)
        app.swapExercise(date, variant.name, null)  // «volver al del plan»: queda fijo
        assertTrue(app.strengthScreen(date)!!.exercises.any { it.name == original && it.original == null })
        val later = LocalDate.parse(date).plusDays(28).toString()
        assertTrue(app.strengthScreen(later)?.exercises?.none { it.name in ROTATION[original]!! } != false)
    }
}
