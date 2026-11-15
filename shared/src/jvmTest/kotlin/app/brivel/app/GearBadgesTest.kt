package app.brivel.app

import app.brivel.sync.merge
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GearBadgesTest {
    private val today = LocalDate(2026, 11, 16)  // lunes
    private fun app() = BrivelApp(MemoryStore(), MemoryStore(), { today }).also { it.saveProfile(UserProfile(birthYear = 1990)) }
    private fun run(start: String, km: Double, id: String = "hc-$start") = Workout(id, "running", start, km * 6, distanceMeters = km * 1000)
    private fun health(vararg workouts: Workout, days: List<DailyHealth> = emptyList()) =
        HealthSnapshot("Health Connect", days, workouts.toList(), today.toString())

    // ------------------------------------------------------------ material

    @Test
    fun `el material en uso se apunta las actividades de su deporte desde su fecha`() {
        val a = app()
        a.saveGear(null, "Pegasus", "running", "2026-11-01", 100.2, 600.0)
        a.importHealth(health(run("2026-10-30T08:00:00", 10.0), run("2026-11-03T08:00:00", 4.5), run("2026-11-10T08:00:00", 8.0),
            Workout("hc-bici", "cycling", "2026-11-11T08:00:00", 60.0, distanceMeters = 30000.0)))
        val row = a.gear().rows.single()
        assertEquals("113 km", row.km)  // 100,2 + 4,5 + 8; la del 30 de octubre es anterior y la bici no es suya
        assertEquals("2 actividades desde el 1 de noviembre", row.detail)
        assertTrue(row.inUse)
        assertNull(row.warning)
        assertEquals("10 nov · 8,0 km", row.uses.first().text)
        // Los apuntes son permanentes: aunque el reloj deje de traer esas actividades, los kilómetros siguen
        a.importHealth(health())
        assertEquals("113 km", a.gear().rows.single().km)
    }

    @Test
    fun `la misma actividad por dos fuentes cuenta una vez, y la ida y la vuelta cuentan las dos`() {
        val a = app()
        a.saveGear(null, "Bici", "cycling", "2026-11-01", 0.0, null)
        fun ride(id: String, start: String) = Workout(id, "cycling", start, 20.0, distanceMeters = 8000.0)
        a.importHealth(health(ride("hc-1", "2026-11-10T08:00:00"), ride("hc-2", "2026-11-10T17:30:00")))
        assertEquals("16 km", a.gear().rows.single().km)
        // Llega también por Garmin, con otro id y dos minutos de diferencia
        a.importHealth(health(ride("otro-id", "2026-11-10T08:02:00"), ride("hc-2", "2026-11-10T17:30:00")))
        assertEquals("16 km", a.gear().rows.single().km)
        val twice = listOf(GearUse("a", "g", "2026-11-10T08:00:00", 8.0), GearUse("b", "g", "2026-11-10T08:03:00", 8.0),
            GearUse("a", "g", "2026-11-10T08:00:00", 8.0), GearUse("c", "g", "2026-11-10T17:30:00", 8.0))
        assertEquals(listOf("a", "c"), dedupeUses(twice).map { it.activity })
    }

    @Test
    fun `uno nuevo pasa a estar en uso, se puede pasar una actividad a otro, retirarlo y borrarlo`() {
        val a = app()
        a.saveGear(null, "Viejas", "running", "2026-11-01", 0.0, 5.0)
        a.importHealth(health(run("2026-11-03T08:00:00", 6.0)))
        assertNotNull(a.gear().rows.single().warning)  // pasó de los 5 km del aviso
        a.saveGear(null, "Nuevas", "running", "2026-11-05", 0.0, null)
        a.importHealth(health(run("2026-11-03T08:00:00", 6.0), run("2026-11-10T08:00:00", 8.0)))
        val (new, old) = a.gear().rows
        assertEquals(listOf("Nuevas", "8 km", "true"), listOf(new.name, new.km, new.inUse.toString()))
        assertEquals(listOf("Viejas", "6 km", "false"), listOf(old.name, old.km, old.inUse.toString()))
        a.moveGearUse("hc-2026-11-10T08:00:00", old.id)
        assertEquals(listOf("0 km", "14 km"), a.gear().rows.map { it.km })
        a.saveGear(null, "Bici", "cycling", "2026-11-01", 0.0, null)
        assertFailsWith<IllegalArgumentException> { a.moveGearUse("hc-2026-11-10T08:00:00", a.gear().rows.first().id) }  // otro deporte
        a.retireGear(new.id)
        assertTrue(a.gear().rows.first { it.name == "Viejas" }.inUse)  // retirada la nueva, vuelve a apuntarse la vieja
        a.useGear(new.id)
        assertEquals("Nuevas", a.gear().rows.first().name)
        a.deleteGear(old.id)
        assertTrue(a.data.gearUses.none { it.gear == old.id })
        assertFailsWith<IllegalArgumentException> { a.saveGear(null, " ", "running", "2026-11-01", 0.0, null) }
        assertFailsWith<IllegalArgumentException> { a.saveGear(null, "Tabla", "surf", "2026-11-01", 0.0, null) }
    }

    // ------------------------------------------------------------ medallas

    @Test
    fun `las medallas se ganan con su fecha, dan puntos y nivel, y no se pierden`() {
        val a = app()
        assertEquals(0, a.progress().badges!!.points)
        assertEquals(1, a.progress().badges!!.level)
        a.importHealth(health(run("2026-11-03T08:00:00", 4.5), run("2026-11-10T08:00:00", 10.2)))
        val badges = a.progress().badges!!
        assertEquals(setOf("Primer paso", "5 km", "10 km"), badges.earned.map { it.title }.toSet())
        assertEquals("10 nov 2026", badges.earned.first { it.title == "10 km" }.earned)  // la fecha de la actividad
        assertEquals("3 nov 2026", badges.earned.first { it.title == "Primer paso" }.earned)
        assertEquals(4, badges.points)  // 1 + 1 + 2
        assertEquals(2, badges.level)
        assertEquals("Te faltan 4 puntos para el nivel 3", badges.nextLevel)
        val half = badges.next.first { it.title == "Media maratón" }  // de su familia, solo la siguiente
        assertEquals("10,2 de 21,1 km", half.progress)
        assertTrue(badges.next.none { it.title == "Maratón" })
        assertEquals(badges.next.map { it.fraction }.sortedDescending(), badges.next.map { it.fraction })
        a.importHealth(health())  // el reloj ya no trae esas actividades
        assertEquals(4, a.progress().badges!!.points)
    }

    @Test
    fun `la fuerza, el sueño y la constancia tambien dan medallas, y Hoy avisa de la nueva`() {
        val a = app()
        a.logSet(today.toString(), "Sentadilla", 1, 60.0, 8, null)
        assertTrue("fuerza" in a.progress().badges!!.earned.single().title)
        assertEquals(480.0, BadgeFacts(a.data, today).volume.first)
        a.logSet(today.toString(), "Plancha frontal", 1, 10.0, 60, null)  // por tiempo: no suma kilos
        assertEquals(480.0, BadgeFacts(a.data, today).volume.first)
        // Ocho noches de 7 h 30 hasta ayer; hoy aún no ha llegado el dato
        a.importHealth(health(days = (1..8).map { DailyHealth(today.toString().take(8) + (16 - it).toString().padStart(2, '0'), sleepMinutes = 450.0) }))
        assertTrue(a.progress().badges!!.earned.any { it.title == "Semana bien dormida" })
        assertEquals("8 de 28 noches", a.progress().badges!!.next.first { it.title == "Mes bien dormido" }.progress)
        assertEquals(listOf(false, true, true), listOf(streak(listOf(true, false, true, true)) == 3, streak(listOf(true, true, false)) == 2, streak(emptyList()) == 0))
        assertEquals(2, streak(listOf(false, true, true)))
    }

    @Test
    fun `borrar una actividad quita sus kilometros y las medallas que solo se cumplian con ella`() {
        val a = app()
        a.saveGear(null, "Pegasus", "running", "2026-11-01", 0.0, null)
        a.importHealth(health(run("2026-11-03T08:00:00", 5.2)))  // del reloj: 5 km y primer paso
        val t0 = 1_794_700_000L  // 2026-11-14
        a.startRecording("running", t0)
        for (t in 0..400) a.recordPoint(t0 + t, 38.5 + t * 30 / 111195.0, -0.1, null, 5.0)  // mal grabada: 12 km en 7 min
        val bad = a.finishRecording()
        assertTrue("run-10" in a.data.badges)
        assertEquals(2, a.data.gearUses.size)
        a.deleteActivity(bad)
        assertEquals(setOf("first-1", "run-5"), a.data.badges.keys)  // las del reloj se quedan
        assertEquals("5 km", a.gear().rows.single().km)
    }

    @Test
    fun `al combinar dos dispositivos se quedan las medallas de los dos con la fecha mas antigua`() {
        val base = AppData()
        val mine = base.copy(badges = mapOf("run-5" to "2026-11-10", "first-1" to "2026-11-03"))
        val theirs = base.copy(badges = mapOf("run-5" to "2026-11-08", "run-10" to "2026-11-12"))
        assertEquals(mapOf("run-5" to "2026-11-08", "first-1" to "2026-11-03", "run-10" to "2026-11-12"), merge(base, mine, theirs).data.badges)
    }

    @Test
    fun `los ids de las medallas no se repiten y los niveles suben`() {
        assertEquals(BADGES.size, BADGES.map { it.id }.toSet().size)
        assertTrue(BADGES.all { it.points in setOf(1, 2, 4, 8) && it.target > 0 && it.text.isNotBlank() })
        assertEquals(LEVELS.sorted(), LEVELS)
        assertTrue(BADGES.sumOf { it.points } >= LEVELS.last())  // el último nivel se puede alcanzar
    }

    // ------------------------------------------------------------ más salud

    @Test
    fun `los datos nuevos de salud llegan a Progreso y cada fuente rellena lo que le falta a la otra`() {
        val day = DailyHealth("2026-11-15", sleepMinutes = 450.0, deepSleepMinutes = 80.0, spo2 = 96.0)
        val other = DailyHealth("2026-11-15", sleepMinutes = 400.0, remSleepMinutes = 95.0, respiratoryRate = 14.0, bodyFat = 18.5, stress = 30.0)
        val both = day.or(other)
        assertEquals(listOf(450.0, 80.0, 95.0, 96.0, 14.0, 18.5, 30.0),
            listOf(both.sleepMinutes, both.deepSleepMinutes, both.remSleepMinutes, both.spo2, both.respiratoryRate, both.bodyFat, both.stress))
        val a = app()
        a.importHealth(health(days = listOf(both)))
        val keys = a.progress().metrics.map { it.key }
        assertTrue(keys.containsAll(listOf("deepSleep", "remSleep", "spo2", "respiration", "stress", "bodyFat")), keys.toString())
        assertEquals(96.0, a.progress().metrics.first { it.key == "spo2" }.latest)
        // Garmin manda cada dato por separado: el estrés llega después y no borra el sueño
        val merged = mergeGarmin(HealthSnapshot("Garmin", listOf(day), emptyList(), "2026-11-15"),
            GarminDelivery(1, listOf(DailyHealth("2026-11-15", stress = 41.0))), today)
        assertEquals(listOf(450.0, 41.0), merged.days.single().let { listOf(it.sleepMinutes, it.stress) })
        assertFalse(a.progress().metrics.any { it.key == "sleepScore" })
    }
}
