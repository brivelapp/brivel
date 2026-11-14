package app.brivel.app

import app.brivel.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Bloque A de la visión: entreno libre, superseries, tipos de serie, historial, discos y medidas. */
class TrainingTest {
    private val today = LocalDate(2026, 10, 19)  // lunes: el plan tiene fuerza
    private val date = today.toString()
    private val profile = UserProfile(birthYear = 1990, level = "intermediate", strength = "gym",
        cardio = listOf("running"), days = mapOf("MON" to 60, "WED" to 60, "SAT" to 90))

    private fun app(now: () -> LocalDate = { today }) = BrivelApp(MemoryStore(), MemoryStore(), now).apply {
        saveProfile(profile)
        setGoal(GoalKind.FITNESS)
    }

    private val bench = "Press de banca con mancuernas"
    private val row = "Remo con mancuerna"

    @Test fun freeWorkoutReplacesTodaysStrengthAndCanBeDiscarded() {
        val a = app()
        val planned = a.strengthScreen(date)!!.exercises.map { it.name }
        assertNull(a.workoutToday)
        assertFailsWith<IllegalStateException> { a.addWorkoutExercise(bench) }  // sin empezarlo no hay dónde añadir
        a.startWorkout(null)
        // Vacío: hay sesión de fuerza, sin ejercicios todavía, y es la única del día
        val empty = a.strengthScreen(date)!!
        assertTrue(empty.free && empty.exercises.isEmpty())
        assertEquals(listOf("Entreno libre"), a.day(date).items.filter { it.isStrength }.map { it.title })
        a.addWorkoutExercise(bench)
        a.addWorkoutExercise("Plancha frontal")
        assertFailsWith<IllegalArgumentException> { a.addWorkoutExercise(bench) }  // repetido
        assertFailsWith<IllegalArgumentException> { a.addWorkoutExercise("No existe") }
        val s = a.strengthScreen(date)!!
        assertEquals(listOf(bench to 3, "Plancha frontal" to 3), s.exercises.map { it.name to it.sets })
        assertEquals(listOf(8..12, 20..40), s.exercises.map { it.repsLow..it.repsHigh })
        a.changeWorkoutSets(bench, 1)
        assertEquals(4, a.strengthScreen(date)!!.exercises.first().sets)
        assertFailsWith<IllegalArgumentException> { repeat(7) { a.changeWorkoutSets(bench, 1) } }  // tope de 10
        a.logSet(date, bench, 1, 20.0, 10, null)
        a.logSet(date, "Plancha frontal", 1, null, 30, null)
        a.removeWorkoutExercise("Plancha frontal")  // se lleva sus series de hoy
        assertEquals(listOf(bench), a.data.sets.map { it.exercise })
        // Empezar otro el mismo día no pisa el que hay; mañana el plan sigue siendo el suyo
        assertEquals(1, a.startWorkout(null).exercises.size)
        assertTrue(a.strengthScreen(today.plusDays(2).toString())?.free != true)
        a.discardWorkout()
        assertNull(a.workoutToday)
        assertTrue(a.data.sets.isEmpty())
        assertEquals(planned, a.strengthScreen(date)!!.exercises.map { it.name })  // vuelve el plan
    }

    @Test fun freeWorkoutFromARoutineOnADayItIsNotScheduled() {
        val a = app()
        val r = a.saveRoutine(Routine("", "Torso", listOf(RoutineExercise(bench, 4, 6, 10)), days = listOf("THU"), rir = 1, since = ""))
        a.startWorkout(r.id)
        val s = a.strengthScreen(date)!!
        assertEquals("Torso", s.title)
        assertEquals(listOf(4), s.exercises.map { it.sets })
        assertEquals(1, s.exercises.single().rir)
        assertFailsWith<IllegalArgumentException> { app().startWorkout("no-existe") }
    }

    @Test fun freeWorkoutOnARestDayAddsStrength() {
        val tuesday = today.plusDays(1)
        val a = app { tuesday }
        assertNull(a.strengthScreen(tuesday.toString()))
        a.startWorkout(null)
        a.addWorkoutExercise(row)
        assertEquals(listOf(row), a.strengthScreen(tuesday.toString())!!.exercises.map { it.name })
    }

    @Test fun supersetAlternatesAndRestsAfterTheRound() {
        val a = app()
        a.startWorkout(null)
        for (n in listOf(bench, row, "Curl con barra")) a.addWorkoutExercise(n)
        assertFailsWith<IllegalArgumentException> { a.linkWorkoutExercise(bench, true) }  // el primero no tiene anterior
        a.linkWorkoutExercise(row, true)
        val s = a.strengthScreen(date)!!
        assertEquals(listOf("A", "A", null), s.exercises.map { it.superset })
        assertEquals(0, s.exercises[0].restSeconds)  // del press se pasa al remo sin descansar
        assertTrue(s.exercises[1].restSeconds > 0 && s.exercises[2].restSeconds > 0)
        // press 1 → remo 1 → press 2 → remo 2 … y al acabar la superserie, el curl
        val order = mutableListOf<String>()
        var next: NextSet? = NextSet(0, 1)
        while (next != null) {
            val e = a.strengthScreen(date)!!.exercises[next.exercise]
            order += "${e.name.take(4)}${next.set}"
            a.logSet(date, e.name, next.set, 20.0, 10, null)
            next = a.nextSet(date, next.exercise)
        }
        assertEquals("Pres1 Remo1 Pres2 Remo2 Pres3 Remo3 Curl1 Curl2 Curl3", order.joinToString(" "))
        a.linkWorkoutExercise(row, false)
        assertEquals(listOf(null, null, null), a.strengthScreen(date)!!.exercises.map { it.superset })
    }

    @Test fun routineSupersetsReachTheSession() {
        val a = app()
        a.saveRoutine(Routine("", "Torso", listOf(RoutineExercise(bench, 3, 6, 10), RoutineExercise(row, 3, 6, 10, linked = true),
            RoutineExercise("Curl con barra", 2, 8, 12), RoutineExercise("Flexiones", 2, 8, 12, linked = true)), days = listOf("MON"), since = ""))
        assertEquals(listOf("A", "A", "B", "B"), a.strengthScreen(date)!!.exercises.map { it.superset })
    }

    @Test fun nextSetFillsTheFirstGapAndEndsWhenAllDone() {
        val a = app()
        a.startWorkout(null)
        a.addWorkoutExercise(bench)
        a.logSet(date, bench, 1, 20.0, 10, null)
        a.logSet(date, bench, 3, 20.0, 10, null)  // apuntada fuera de orden desde la tabla
        assertEquals(NextSet(0, 2), a.nextSet(date, 0))
        a.logSet(date, bench, 2, 20.0, 10, null)
        assertNull(a.nextSet(date, 0))
    }

    @Test fun warmupAndDropSetsAddToThePlanAndDoNotDriveProgression() {
        val a = app()
        a.startWorkout(null)
        a.addWorkoutExercise(bench)
        a.logSet(date, bench, 1, 10.0, 12, null, "warmup")
        assertEquals(4, a.strengthScreen(date)!!.exercises.single().sets)  // 3 de trabajo + la de calentamiento
        for (n in 2..4) a.logSet(date, bench, n, 30.0, 12, null)
        a.logSet(date, bench, 5, 20.0, 15, null, "drop")
        val e = a.strengthScreen(date)!!.exercises.single()
        assertEquals(5 to 5, e.sets to e.done.size)
        assertEquals(listOf("warmup", null, null, null, "drop"), e.done.map { it.kind })
        // Corregir una serie conserva su tipo
        a.logSet(date, bench, 1, 12.5, 12, null)
        assertEquals("warmup", a.data.sets.single { it.set == 1 }.kind)
        assertFailsWith<IllegalArgumentException> { a.logSet(date, bench, 6, 20.0, 5, null, "raro") }
        // El resumen del día (progresión) y el récord solo miran las de trabajo
        val summary = daySummaries(a.data.sets).single()
        assertEquals(Triple(30.0, 12, 3), Triple(summary.weightKg, summary.reps, summary.sets))
        assertEquals("30 kg × 12", strengthRecords(a.data.sets).single().value)
        val h = a.exerciseHistory(bench)
        assertEquals(3 to 30.0 * 12 * 3, h.days.single().sets to h.days.single().volumeKg)
    }

    @Test fun e1rmFollowsEpleyUpToTenReps() {
        assertEquals(100.0, e1rm(100.0, 1))
        assertEquals(100.0 * (1 + 5 / 30.0), e1rm(100.0, 5))
        assertNull(e1rm(100.0, 11))  // por encima de 10 repeticiones no se estima
        assertNull(e1rm(0.0, 5))
    }

    @Test fun exerciseHistoryHasChartDaysAndRecords() {
        val a = app()
        val days = (0..3).map { today.plusDays(-7 * it).toString() }  // hoy y tres lunes atrás
        for ((i, d) in days.withIndex()) {
            a.logSet(d, bench, 1, 30.0 - 2.5 * i, 8, null)
            a.logSet(d, bench, 2, 30.0 - 2.5 * i, 6, null)
        }
        val h = a.exerciseHistory(bench)
        assertEquals(days, h.days.map { it.date })  // el más reciente primero
        assertEquals("30 kg × 8", h.days.first().best)
        assertEquals(30.0 * 14, h.days.first().volumeKg)
        assertEquals("e1rm" to 4, h.chart!!.key to h.chart!!.points.size)
        assertEquals(days.reversed(), h.chart!!.points.map { it.date })  // la gráfica, de antiguo a reciente
        assertEquals(30.0 * (1 + 8 / 30.0), h.chart!!.latest)
        assertEquals(listOf("Más peso" to "30 kg × 8", "Mejor 1RM estimado" to "38 kg", "Más kilos en un día" to "420 kg"),
            h.records.map { it.title to it.value })
        // Sin peso: repeticiones; por tiempo: segundos
        a.logSet(date, "Flexiones", 1, null, 15, null)
        assertEquals("Más repeticiones" to "15", a.exerciseHistory("Flexiones").records.single().let { it.title to it.value })
        assertEquals(15.0, a.exerciseHistory("Flexiones").chart!!.latest)
        a.logSet(date, "Plancha frontal", 1, null, 45, null)
        assertEquals("Más tiempo" to "45″", a.exerciseHistory("Plancha frontal").records.single().let { it.title to it.value })
        // Sin nada apuntado: vacío, sin gráfica
        assertTrue(a.exerciseHistory(row).let { it.days.isEmpty() && it.chart == null && it.records.isEmpty() })
    }

    @Test fun platesForTheBar() {
        assertEquals(listOf(25.0, 2.5), platesPerSide(75.0))
        assertEquals(listOf(25.0), platesPerSide(65.0, barKg = 15.0))  // con otra barra
        assertEquals(emptyList(), platesPerSide(20.0))
        assertNull(platesPerSide(15.0))  // menos que la barra
        assertNull(platesPerSide(21.0))  // medio kilo por lado: no hay disco
        assertEquals("Por lado: 25 + 2,5 kg (barra de 20 kg)", platesText(75.0))
        assertEquals("Solo la barra (20 kg)", platesText(20.0))
        assertEquals(listOf(25.0, 25.0, 25.0, 25.0, 1.25), platesPerSide(222.5))
        // La sesión marca qué ejercicios van con barra
        val a = app()
        a.startWorkout(null)
        a.addWorkoutExercise("Sentadilla con barra")
        a.addWorkoutExercise(bench)
        assertEquals(listOf(true, false), a.strengthScreen(date)!!.exercises.map { it.barbell })
    }

    @Test fun measurementsShowInProgressWithoutAWatch() {
        val a = app()
        a.logMeasure("waist", 84.0, today.plusDays(-7).toString())
        a.logMeasure("waist", 83.0)
        a.logMeasure("waist", 82.5)  // la del mismo día sustituye a la anterior
        a.logMeasure("weight", 72.4)
        val p = a.progress()
        assertEquals(listOf("Peso", "Cintura"), p.measures.map { it.label })  // en el orden de MEASURES
        val waist = p.measures.last()
        assertEquals(listOf(84.0, 82.5), waist.points.map { it.value })
        assertEquals("cm" to 82.5, waist.unit to waist.latest)
        assertFailsWith<IllegalArgumentException> { a.logMeasure("waist", 5.0) }
        assertFailsWith<IllegalArgumentException> { a.logMeasure("altura", 180.0) }
        assertFailsWith<IllegalArgumentException> { a.logMeasure("waist", 80.0, today.plusDays(1).toString()) }
        a.deleteMeasure(date, "waist")
        assertEquals(listOf(84.0), a.progress().measures.last().points.map { it.value })
    }

    @Test fun oldDataMigratesAndNewFieldsSurviveTheRoundTrip() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        // Formato 9: series sin tipo y rutinas sin superserie
        val old = """{"schema":9,"sets":[{"date":"2026-10-12","exercise":"Flexiones","set":1,"weightKg":null,"reps":10}],
            "routines":[{"id":"r","name":"Torso","exercises":[{"name":"Flexiones","sets":3,"low":8,"high":12}],"days":["MON"],"since":"2026-10-01"}]}"""
        val data = migrate(json.decodeFromString<AppData>(old))
        assertEquals(SCHEMA, data.schema)
        assertNull(data.sets.single().kind)
        assertEquals(false, data.routines.single().exercises.single().linked)
        assertTrue(data.workouts.isEmpty() && data.measurements.isEmpty())
        val a = app()
        a.startWorkout(null)
        a.addWorkoutExercise(bench)
        a.logSet(date, bench, 1, 20.0, 10, null, "failure")
        a.logMeasure("arm", 34.0)
        assertEquals(a.data, json.decodeFromString<AppData>(json.encodeToString(AppData.serializer(), a.data)))
    }
}
