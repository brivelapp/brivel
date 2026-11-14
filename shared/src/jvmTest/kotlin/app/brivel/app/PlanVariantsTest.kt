package app.brivel.app

import app.brivel.engine.Screening
import app.brivel.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** La rotación de variantes dentro del plan entero: quién rota, cuándo cambia y qué respeta. */
class PlanVariantsTest {
    private val today = LocalDate(2026, 10, 22)  // jueves
    private val monday = mondayOf(today)
    private val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT").associateWith { 60 }

    private fun app(level: String = "intermediate", birthYear: Int? = 1990, strength: String = "gym",
                    screening: Screening? = null, goal: GoalKind = GoalKind.STRENGTH) =
        BrivelApp(MemoryStore(), MemoryStore(), { today }).apply {
            saveProfile(UserProfile(birthYear = birthYear, level = level, strength = strength, days = days, screening = screening))
            setGoal(goal)
        }

    /** Las sesiones de fuerza de la semana `w` (0: la de hoy), en orden. */
    private fun strength(a: BrivelApp, w: Int): List<PlanItem> =
        Planner(a.data, today).week(monday.plusDays(7 * w)).days.flatMap { d -> d.items.filter { it.isStrength } }

    private fun names(item: PlanItem) = item.exercises.map { exerciseName(it) }
    private fun variants(a: BrivelApp, weeks: Int = 10) = (0 until weeks).flatMap { strength(a, it) }.flatMap { it.rotatedFrom.keys }

    @Test fun onlySeasonedHealthyAdultsGetVariants() {
        assertTrue(variants(app()).isNotEmpty())
        assertTrue(variants(app(strength = "bodyweight")).isNotEmpty())
        assertEquals(emptyList(), variants(app(level = "beginner")))
        assertEquals(emptyList(), variants(app(birthYear = 1950)))
        assertEquals(emptyList(), variants(app(birthYear = 2010)))
        assertEquals(emptyList(), variants(app(screening = Screening(active = true, knownDisease = true, symptoms = false))))
        assertEquals(emptyList(), variants(app(screening = Screening(active = false, knownDisease = false, symptoms = false))))
        assertTrue(variants(app(screening = Screening(active = true, knownDisease = false, symptoms = false))).isNotEmpty())
    }

    @Test fun everyVariantComesFromItsPlanExerciseAndKeepsItsDose() {
        val a = app()
        for (item in (0 until 10).flatMap { strength(a, it) }) {
            for ((variant, original) in item.rotatedFrom) {
                assertTrue(variant in ROTATION[original]!!, "$original → $variant")
                assertEquals(LIBRARY[original], LIBRARY[variant], variant)
                assertTrue(original !in names(item), "$original sigue en la sesión junto a su variante")
            }
            assertEquals(names(item).size, names(item).toSet().size, "ejercicio repetido: ${names(item)}")
            assertTrue(item.exercises.all { ":" in it || it == app.brivel.engine.BALANCE }, item.exercises.toString())
        }
    }

    @Test fun aBlockKeepsItsVariantsAndTheNextOneChangesThem() {
        val a = app()
        val planner = Planner(a.data, today)
        val weeks = (0 until 12).map { planner.week(monday.plusDays(7 * it)) }
        val byBlock = weeks.groupBy { it.block!! }.mapValues { (_, ws) -> ws.map { w -> w.days.flatMap { d -> d.items.filter { it.isStrength } }.map(::names) } }
        // Dentro de un bloque, la misma sesión de la semana (primera, segunda…) trae los mismos ejercicios
        for ((block, sessions) in byBlock) {
            val count = sessions.minOf { it.size }
            for (slot in 0 until count) assertEquals(1, sessions.map { it[slot] }.toSet().size, "bloque $block, sesión $slot")
        }
        val first = byBlock.values.map { it.first() }
        assertTrue(first.zipWithNext().all { (x, y) -> x != y }, "dos bloques seguidos con los mismos ejercicios")
    }

    @Test fun sessionsOfTheSameWeekDoNotShareVariant() {
        val week = strength(app(goal = GoalKind.FITNESS), 1)
        val sameWork = week.groupBy { it.healthStrength?.patterns }.values.filter { it.size > 1 }
        assertTrue(sameWork.isNotEmpty(), "el caso necesita dos sesiones con los mismos patrones")
        sameWork.forEach { group -> assertTrue(names(group[0]) != names(group[1]), names(group[0]).toString()) }
    }

    @Test fun movingASessionKeepsItsExercises() {
        val a = app()
        val from = (0..6).map { today.plusDays(it).toString() }.first { d -> a.day(d).items.any { it.isStrength } && a.moveTargets(d, "strength").isNotEmpty() }
        val before = a.day(from).items.first { it.isStrength }.exercises
        val to = a.moveTargets(from, "strength").first()
        a.moveSession(from, "strength", to)
        assertEquals(before, a.day(to).items.first { it.isStrength && it.movedFrom == from }.exercises)
    }

    @Test fun ownRoutinesNeverRotate() {
        val a = app()
        val lines = listOf("Peso muerto rumano: 3 × 6–10 repeticiones", "Plancha frontal: 2 × 20–40″")
        a.saveRoutine(Routine("", "Mía", listOf(RoutineExercise("Peso muerto rumano", 3, 6, 10), RoutineExercise("Plancha frontal", 2, 20, 40)),
            days = days.keys.toList(), since = ""))
        val routines = (0 until 10).flatMap { strength(a, it) }.filter { it.kind == "routine" }
        assertTrue(routines.size >= 20)
        routines.forEach { assertEquals(lines, it.exercises) }
    }

    @Test fun variantsRespectTheEquipmentTheUserHas() {
        val a = app()
        a.setEquipment(listOf("mancuernas", "banco o cajón"))
        val seen = variants(a)
        seen.forEach { assertTrue(doable(exerciseCard(it)!!, a.equipment), it) }
        // Con todo el material vuelven a salir las que piden barra o máquina
        a.setEquipment(EQUIPMENT)
        assertTrue(variants(a).any { !doable(exerciseCard(it)!!, listOf("mancuernas", "banco o cajón")) })
    }

    @Test fun equipmentIsValidatedOrderedAndCanBeCleared() {
        val a = app()
        a.setEquipment(listOf("goma", "mancuernas", "goma"))
        assertEquals(listOf("mancuernas", "goma"), a.equipment)  // en el orden del filtro y sin repetir
        assertFailsWith<IllegalArgumentException> { a.setEquipment(listOf("un yunque")) }
        assertEquals(listOf("mancuernas", "goma"), a.equipment)
        a.setEquipment(emptyList())
        assertEquals(emptyList(), a.equipment)
    }

    @Test fun aSwapOnAVariantHoldsInLaterBlocks() {
        val a = app()
        val (date, shown) = (0..60).map { today.plusDays(it).toString() }.firstNotNullOf { d ->
            a.strengthScreen(d)?.exercises?.firstOrNull { it.original != null }?.let { d to it }
        }
        val original = shown.original!!
        val choice = a.swapOptions(original).first { it.name != shown.name && it.name !in ROTATION[original]!! }.name
        a.swapExercise(date, shown.name, choice)
        assertEquals(original, a.data.swaps.single().original)  // guardado sobre el ejercicio del plan, no sobre la variante
        val later = (0..90).map { LocalDate.parse(date).plusDays(it).toString() }.mapNotNull { a.strengthScreen(it) }.flatMap { it.exercises }
        assertTrue(later.any { it.name == choice && it.original == original })
        assertTrue(later.none { it.name == original || it.name in ROTATION[original]!! }, "volvió a rotar un ejercicio cambiado a mano")
        // Los días anteriores al cambio siguen como estaban
        assertFailsWith<IllegalArgumentException> { a.swapExercise(today.plusDays(-1).toString(), shown.name, choice) }
        assertFailsWith<IllegalArgumentException> { a.swapExercise(date, choice, "Curl con barra") }  // otro patrón
    }

    @Test fun notesCarryTheWarmUpAndExplainTheRotationOnlyWhenThereIsOne() {
        val a = app()
        val items = (0 until 10).flatMap { strength(a, it) }
        assertTrue(items.all { it.notes!!.startsWith("Calentamiento (10 min)") })
        items.forEach { assertEquals(it.rotatedFrom.isNotEmpty(), ROTATION_NOTE in it.notes!!, it.exercises.toString()) }
        assertTrue((0 until 10).flatMap { strength(app(level = "beginner"), it) }.none { ROTATION_NOTE in it.notes!! })
    }

    @Test fun warmUpMatchesTheWorkOfTheDay() {
        val legs = warmup(listOf("Sentadilla con barra: 3 × 8–12 repeticiones"))
        val upper = warmup(listOf("Remo con mancuerna: 3 × 8–12 repeticiones"))
        val both = warmup(listOf("Sentadilla con barra", "Press de banca con mancuernas"))
        assertTrue("balanceo de pierna" in legs && "círculos de brazos" !in legs)
        assertTrue("círculos de brazos" in upper && "balanceo de pierna" !in upper)
        assertTrue("balanceo de pierna" in both && "círculos de brazos" in both)
        assertTrue("gato-camello" in warmup(listOf("Plancha frontal: 3 × 20–45″")) && "gato-camello" in warmup(emptyList()))
        listOf(legs, upper, both).forEach { assertTrue(it.startsWith("Calentamiento (10 min): ") && it.endsWith(".")) }
    }

    @Test fun theWeekSaysItsSetsByZoneAndWarnsWhenPushAndPullAreUneven() {
        val a = app()
        val info = Planner(a.data, today).week(monday.plusDays(7)).info
        val line = info.single { it.startsWith("Fuerza de la semana por zona: ") }
        assertTrue("pierna" in line && "series" in line && "Empujas" !in line && "Tiras" !in line, line)
        assertTrue(Planner(app(strength = "none").data, today).week(monday.plusDays(7)).info.none { "por zona" in it })

        a.saveRoutine(Routine("", "Solo press", listOf(RoutineExercise("Press de banca con mancuernas", 4, 8, 12),
            RoutineExercise("Press militar con mancuernas", 4, 8, 12)), days = days.keys.toList(), since = ""))
        val uneven = Planner(a.data, today).week(monday.plusDays(7)).info.single { it.startsWith("Fuerza de la semana por zona: ") }
        assertTrue(uneven.endsWith("Empujas el doble de lo que tiras: añade remos o jalones."), uneven)
    }

    @Test fun whatGoesToTheWatchIsWhatTheScreenShows() {
        val a = app()
        val item = a.day(today.toString()).items.firstOrNull { it.isStrength } ?: return
        val sent = a.watchWorkouts("strength").single().blocks.flatMap { it.steps }.mapNotNull { it.exercise }.distinct()
        assertEquals(a.strengthScreen(today.toString())!!.exercises.map { it.name }, sent)
        assertTrue(sent.all { it in names(item) && a.watchPattern(it) != null })
    }
}
