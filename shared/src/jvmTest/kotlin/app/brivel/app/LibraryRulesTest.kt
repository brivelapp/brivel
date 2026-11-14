package app.brivel.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Reglas de la biblioteca que valen para los 1.310 ejercicios: se comprueban sobre todos, no sobre una muestra. */
class LibraryRulesTest {
    private val cards = LIBRARY.keys.map { exerciseCard(it)!! }

    @Test fun everyPatternHasLabelZoneAndFigure() {
        assertEquals(PATTERN_LABEL.keys, PATTERN_ZONE.keys)
        assertEquals(ZONES.toSet(), PATTERN_ZONE.values.toSet())
        assertEquals(PATTERN_LABEL.keys, LIBRARY.values.map { it.pattern }.toSet())
        PATTERN_LABEL.keys.forEach { assertNotNull(FIGURES[it], it) }
    }

    @Test fun namesAreUniqueCleanAndSearchable() {
        assertEquals(LIBRARY.size, LIBRARY.keys.map { plain(it) }.toSet().size)  // ni con otras tildes o mayúsculas
        for (name in LIBRARY.keys) {
            assertTrue(name == name.trim() && "  " !in name && ':' !in name && '"' !in name, name)
            assertTrue(name.first().isUpperCase() && name.length in 4..90, name)
        }
        // Buscarlo por su nombre lo encuentra (uno de cada diez: la búsqueda recorre toda la biblioteca)
        LIBRARY.keys.filterIndexed { i, _ -> i % 10 == 0 }.forEach { name -> assertTrue(library(name).any { it.name == name }, name) }
    }

    @Test fun everyCardIsComplete() {
        for (c in cards) {
            assertTrue(c.zone in ZONES && c.muscles.isNotEmpty() && c.muscles.none { it.isBlank() }, c.name)
            assertTrue(c.equipment.isNotEmpty() && c.equipment.all { it in EQUIPMENT } && c.equipment.distinct() == c.equipment, c.name)
            val d = c.description
            assertTrue(d.first().isUpperCase() && d.endsWith(".") && ".." !in d && "null" !in d && "  " !in d, "${c.name}: $d")
            assertTrue(d.length in 60..520, "${c.name}: ${d.length} caracteres")
            assertTrue(("Se mide por tiempo" in d) == c.timed && ("Se mide por repeticiones" in d) != c.timed, c.name)
            assertTrue(c.muscles.joinToString(", ") in d, c.name)
        }
    }

    @Test fun musclesFollowTheNameWhereThePatternIsNotEnough() {
        fun m(name: String) = exerciseCard(name)!!.muscles
        assertEquals(listOf("antebrazos"), m("Curl de muñeca con barra"))
        assertEquals(listOf("bíceps", "braquial"), m("Curl martillo"))
        assertEquals(listOf("tríceps"), m("Fondos en banco"))
        assertEquals(listOf("trapecio"), m("Encogimiento de hombros con barra"))
        assertEquals(listOf("manguito rotador"), m("Rotación externa en polea"))
        assertEquals(listOf("deltoides posterior", "trapecio medio e inferior"), m("Pájaro con mancuernas"))
        assertEquals(listOf("deltoides"), m("Elevación lateral con mancuernas"))
        assertEquals(listOf("cuello"), m("Extensión de cuello con disco"))
        assertEquals(listOf("isquiotibiales"), m("Curl femoral tumbado"))
        assertEquals(listOf("cuádriceps"), m("Extensión de cuádriceps"))
        assertEquals(listOf("cuádriceps"), m("Curl nórdico inverso"))
        assertEquals(listOf("aductores"), m("Aductores en máquina"))
        assertEquals(listOf("glúteo medio"), m("Abductores en máquina"))
        assertEquals(listOf("tibial anterior"), m("Elevación de puntas de pie (tibial)"))
        assertEquals(listOf("gemelos", "sóleo"), m("Gemelo de pie en máquina"))
        assertEquals(listOf("lumbares", "glúteos"), m("Superman"))
        assertEquals(listOf("abdominales", "oblicuos"), m("Plancha frontal"))
    }

    @Test fun variantNotesAppearOnlyWhereTheyApply() {
        fun d(name: String) = exerciseCard(name)!!.description
        assertTrue("empieza por el más débil" in d("Peso muerto a una pierna con barra") && "empieza por el más débil" !in d("Peso muerto con barra"))
        assertTrue("La máquina guía el recorrido" in d("Sentadilla hack en máquina") && "La máquina guía" !in d("Sentadilla con barra"))
        assertTrue("más trabajo al tríceps" in d("Flexiones diamante"))
        assertTrue("No necesita material" in d("Flexiones") && "Material: barra" in d("Sentadilla con barra"))
    }

    @Test fun filtersByZoneEquipmentAndWhatTheUserOwns() {
        for (zone in ZONES) {
            val found = library(zone = zone)
            assertTrue(found.isNotEmpty() && found.all { it.zone == zone }, zone)
        }
        assertEquals(LIBRARY.size, ZONES.sumOf { library(zone = it).size })  // cada ejercicio, en una zona y solo en una
        assertTrue(library(zone = "pierna", equipment = "pesa rusa").all { it.zone == "pierna" && "pesa rusa" in it.equipment })
        assertTrue(library("jalon", zone = "pierna").isEmpty())

        val bodyweight = library(owned = emptyList())
        assertTrue(bodyweight.isNotEmpty() && bodyweight.all { it.equipment == listOf("peso corporal") })
        val home = library(owned = listOf("mancuernas", "banco o cajón"))
        assertTrue(home.size > bodyweight.size && home.all { c -> c.equipment.all { it in listOf("peso corporal", "mancuernas", "banco o cajón") } })
        assertEquals(LIBRARY.size, library(owned = EQUIPMENT).size)
        assertTrue(doable(exerciseCard("Flexiones")!!, emptyList()) && !doable(exerciseCard("Sentadilla con barra")!!, listOf("mancuernas")))
    }

    @Test fun cardsAreBuiltOnceButOwnExercisesAlwaysReflectTheirData() {
        assertSame(exerciseCard("Sentadilla con barra"), exerciseCard("Sentadilla con barra"))
        assertNull(exerciseCard("No existe"))
        val mine = listOf(CustomExercise("Curl con cadena", "brazos"))
        assertEquals("brazos", exerciseCard("Curl con cadena", mine)!!.pattern)
        assertEquals("core", exerciseCard("Curl con cadena", listOf(CustomExercise("Curl con cadena", "core", timed = true)))!!.pattern)
        assertNull(exerciseCard("Curl con cadena"))  // sin la lista del usuario no existe: no se quedó guardada
        assertEquals("Curl con cadena", library("cadena", custom = mine).first().name)
    }

    @Test fun rotationPoolsAreSafeToSwapIn() {
        for ((original, variants) in ROTATION) {
            val card = exerciseCard(original)!!
            val bodyweight = card.equipment.all { it == "peso corporal" || it == "banco o cajón" || it == "barra de dominadas" || it == "goma" }
            for (v in variants) {
                val c = exerciseCard(v)!!
                assertEquals(card.zone, c.zone, "$original → $v")
                // Quien entrena sin gimnasio no recibe una variante que pida barra, mancuernas, pesa rusa ni máquina
                if (bodyweight) assertTrue(c.equipment.none { it in listOf("barra", "mancuernas", "pesa rusa", "polea o máquina") }, "$original → $v")
            }
        }
        assertTrue(ROTATION.keys.all { it in EXERCISES } && ROTATION.size >= EXERCISES.size - 6)
    }
}
