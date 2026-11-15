package app.brivel.app

import app.brivel.engine.plusDays
import app.brivel.sync.SYNCED_ACTIVITIES
import app.brivel.sync.merge
import app.brivel.sync.syncPayload
import kotlinx.datetime.LocalDate
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Escribe un archivo FIT mínimo: lo justo para probar el lector sin archivos de nadie. */
private class FitWriter {
    private val body = ByteArrayOutputStream()
    private val sizes = HashMap<Int, List<Int>>()

    /** fields: número de campo, tamaño y tipo base. */
    fun define(local: Int, global: Int, fields: List<Triple<Int, Int, Int>>, big: Boolean = false) {
        body.write(0x40 or local); body.write(0); body.write(if (big) 1 else 0)
        if (big) { body.write(global shr 8); body.write(global and 0xFF) } else { body.write(global and 0xFF); body.write(global shr 8) }
        body.write(fields.size)
        for ((n, s, t) in fields) { body.write(n); body.write(s); body.write(t) }
        sizes[local] = fields.map { it.second }
        bigs[local] = big
    }
    private val bigs = HashMap<Int, Boolean>()

    fun data(local: Int, values: List<Long>, header: Int = local) {
        body.write(header)
        values.zip(sizes[local]!!).forEach { (v, s) ->
            for (i in 0 until s) body.write(((v shr 8 * (if (bigs[local]!!) s - 1 - i else i)) and 0xFF).toInt())
        }
    }

    fun file(): ByteArray {
        val b = body.toByteArray()
        val header = byteArrayOf(12, 0x10, 0, 0, b.size.toByte(), (b.size shr 8).toByte(), (b.size shr 16).toByte(), (b.size shr 24).toByte()) + ".FIT".encodeToByteArray()
        return header + b + byteArrayOf(0, 0)
    }
}

private const val T0 = 1_100_000_000L  // tiempo de FIT; + 631065600 = 2024-11-08T11:33:20Z
private val RECORD = listOf(Triple(253, 4, 0x86), Triple(0, 4, 0x85), Triple(1, 4, 0x85), Triple(2, 2, 0x84), Triple(3, 1, 2),
    Triple(5, 4, 0x86), Triple(6, 2, 0x84), Triple(7, 2, 0x84))
private fun semicircles(degrees: Double) = (degrees * 2147483648.0 / 180).toLong() and 0xFFFFFFFFL

/** 25 minutos a 3 m/s y 150 ppm hacia el norte, un punto por segundo, con dos vueltas y el resumen del reloj. */
private fun run(sport: Long = 1, power: Long = 0xFFFF): ByteArray = FitWriter().apply {
    define(0, 20, RECORD)
    for (t in 0..1500) data(0, listOf(T0 + t, semicircles(38.5 + t * 3 / 111195.0), semicircles(-0.1), (100 + 500) * 5L,
        if (t < 750) 140L else 160L, t * 300L, 3000, power))
    define(1, 19, listOf(Triple(253, 4, 0x86), Triple(8, 4, 0x86), Triple(9, 4, 0x86), Triple(15, 1, 2)))
    data(1, listOf(T0 + 750, 750_000, 225_000, 140))
    data(1, listOf(T0 + 1500, 750_000, 225_000, 160))
    define(2, 18, listOf(Triple(253, 4, 0x86), Triple(2, 4, 0x86), Triple(5, 1, 0), Triple(6, 1, 0), Triple(8, 4, 0x86),
        Triple(9, 4, 0x86), Triple(16, 1, 2), Triple(17, 1, 2)))
    data(2, listOf(T0 + 1500, T0, sport, 0, 1_500_000, 450_000, 150, 160))
}.file()

class ActivitiesTest {
    private val today = LocalDate(2026, 11, 16)
    private val profile = UserProfile(birthYear = 1990, cardio = listOf("running", "cycling"))
    private fun app(store: KeyValueStore = MemoryStore()) = BrivelApp(store, MemoryStore(), { today }).also { it.saveProfile(profile) }

    @Test
    fun `lee un FIT - deporte, totales, puntos y vueltas`() {
        val fit = parseFit(run())
        assertEquals("running", fit.sport)
        assertEquals(T0 + 631065600, fit.start)
        assertEquals(1500.0, fit.seconds)
        assertEquals(4500.0, fit.meters)
        assertEquals(1501, fit.samples.size)
        assertEquals(2, fit.laps.size)
        val s = fit.samples[100]
        assertEquals(38.5 + 300 / 111195.0, s.lat!!, 1e-6)
        assertEquals(-0.1, s.lon!!, 1e-6)
        assertEquals(100.0, s.altitude)
        assertEquals(3.0, s.speed)
        assertEquals(300.0, s.distance)
        assertNull(s.power)  // 0xFFFF es «sin dato»
        assertEquals("2024-11-08T11:33:20", localTime(fit.start))
    }

    @Test
    fun `lee definiciones en big endian, la hora comprimida y se salta lo que no conoce`() {
        val bytes = FitWriter().apply {
            define(3, 0, listOf(Triple(3, 4, 0x8C), Triple(8, 20, 7)))  // file_id con un texto: no se usa
            data(3, listOf(1234, 0))
            define(0, 20, listOf(Triple(253, 4, 0x86), Triple(3, 1, 2)), big = true)
            data(0, listOf(T0, 120))
            define(1, 20, listOf(Triple(3, 1, 2)))
            data(1, listOf(130), header = 0x80 or (1 shl 5) or ((T0 + 5).toInt() and 0x1F))  // 5 s después, en la cabecera
        }.file()
        val fit = parseFit(bytes)
        assertEquals(listOf(T0 + 631065600, T0 + 5 + 631065600), fit.samples.map { it.time })
        assertEquals(listOf(120.0, 130.0), fit.samples.map { it.hr })
        assertEquals("other", fit.sport)
    }

    @Test
    fun `un archivo que no es FIT o está cortado no rompe nada`() {
        assertFailsWith<IllegalArgumentException> { parseFit("hola, no soy un FIT".encodeToByteArray()) }
        val cut = run().copyOf(4000)  // cortado a mitad de los puntos: se lee lo que hay
        assertTrue(parseFit(cut).samples.size in 100..200)
        val a = app()
        assertEquals("No es un archivo de actividad: Brivel lee FIT, GPX y TCX", assertFailsWith<IllegalArgumentException> { a.importFit(ByteArray(40)) }.message)
        assertTrue(a.activities().isEmpty())
    }

    @Test
    fun `importa GPX y TCX con la distancia calculada o la de las vueltas`() {
        val a = app()
        val gpx = a.importFit(GPX.encodeToByteArray())
        val run = a.data.activities.single { it.id == gpx }
        assertEquals("gpx", run.source)
        assertEquals(6.0, run.meters!!, 0.1)  // 2 × 3 m con el GPS
        assertEquals(2.0, run.seconds)
        assertEquals("Importada de un archivo GPX", a.activity(gpx)!!.source)
        val tcx = a.importFit(TCX.encodeToByteArray())
        val ride = a.data.activities.single { it.id == tcx }
        assertEquals("cycling", ride.sport)
        assertEquals(10100.0, ride.meters)
        assertEquals(2, ride.laps.size)
    }

    @Test
    fun `el resumen sale del registro - zonas, mejores marcas, vueltas y series reducidas`() {
        val (a, detail) = buildActivity(parseFit(run()), "2024-11-09T12:33:20", listOf(120.0, 135.0, 145.0, 155.0, 165.0, 175.0), "fit")
        assertEquals("own-2024-11-09T12:33:20", a.id)
        assertEquals(1500.0, a.seconds)
        assertEquals(150.0, a.avgHr)
        assertEquals(listOf(0.0, 0.0, 750.0, 0.0, 750.0, 0.0, 0.0), a.zoneSeconds)  // 140 ppm es Z3 y 160 es Z5a
        assertEquals(setOf(60, 120, 300, 600, 1200), a.speed.keys)  // nada por debajo de 1 min ni por encima de lo que duró
        assertTrue(a.speed.values.all { it == 3.0 })
        assertTrue(a.power.isEmpty())
        assertEquals(2, a.laps.size)
        assertEquals(0.0, a.ascent)
        assertNotNull(detail)
        assertEquals(300, detail.seconds.size)
        assertEquals(300, detail.lat.size)
        assertTrue(detail.power.isEmpty())
        val screen = activityScreen(a, detail, null)
        assertEquals("5:33 /km", screen.stats.first { it.label == "Ritmo medio" }.value)
        assertEquals("4,50 km", screen.stats.first { it.label == "Distancia" }.value)
        assertEquals(listOf("Pulso", "Ritmo", "Altitud"), screen.charts.map { it.label })
        assertTrue(screen.track.all { it.x in 0.0..1.0 && it.y in 0.0..1.0 })
        assertEquals(1.0, screen.track.first().y)  // empieza al sur: abajo
        assertEquals(0.0, screen.track.last().y)
        assertEquals(2, screen.laps.size)
        assertTrue(activityScreen(a, null, null).charts.isEmpty())  // en otro dispositivo: resumen sin gráficas
    }

    @Test
    fun `la media maxima es la mejor ventana`() {
        val values = List(100) { 100.0 } + List(20) { 300.0 } + List(100) { 100.0 }
        val best = meanMax(values, listOf(5, 20, 40, 1000))
        assertEquals(300.0, best[5]); assertEquals(300.0, best[20]); assertEquals(200.0, best[40])
        assertNull(best[1000])
    }

    @Test
    fun `importar guarda la actividad, cuenta para el plan y se puede borrar`() {
        val store = MemoryStore()
        val a = app(store)
        val id = a.importFit(run())
        assertEquals(id, a.importFitBase64(java.util.Base64.getEncoder().encodeToString(run())))  // otra vez: la sustituye
        assertEquals(1, a.activities().size)
        assertNotNull(store.read(DETAIL_KEY + id))
        assertEquals(listOf("Pulso", "Ritmo", "Altitud"), a.activity(id)!!.charts.map { it.label })
        val progress = a.progress()
        assertTrue(progress.linked)  // sin reloj ni intervals.icu, las actividades propias ya dan datos
        assertEquals(id, progress.recent.single().own)
        assertEquals("Curva de ritmo (carrera)", progress.curves.single().title)
        assertTrue("activityDetails" in a.exportJson())
        a.deleteActivity(id)
        assertTrue(a.activities().isEmpty())
        assertNull(store.read(DETAIL_KEY + id))
    }

    @Test
    fun `una actividad del reloj que es la misma que una propia no cuenta dos veces`() {
        val a = app()
        val id = a.importFit(run())
        val start = a.data.activities.single().start
        val twin = Workout("hc-1", "running", start.take(17) + "59", 25.0)  // mismo minuto
        val other = Workout("hc-2", "cycling", "2024-11-10T09:00:00", 60.0)
        val data = a.data.copy(health = HealthSnapshot("Health Connect", emptyList(), listOf(twin, other), "2026-11-16"))
        val ids = data.deviceData(today)!!.activities.map { (it["id"] as kotlinx.serialization.json.JsonPrimitive).content }
        assertEquals(listOf(id, "hc-2"), ids)
    }

    @Test
    fun `la curva de potencia y el FTP estimado salen de las actividades de bici`() {
        val a = app()
        a.importFit(run(sport = 2, power = 200))
        val curve = a.progress().curves.single()
        assertEquals("Curva de potencia (bici)", curve.title)
        assertEquals("200 W", curve.rows.first { it.duration == "20 min" }.best)
        assertEquals("–", curve.rows.first().recent)  // la actividad es de 2024: fuera de los últimos 90 días
        assertNull(curve.note)
        assertEquals(190.0, estimatedFtp(a.data.activities, "2024-01-01"))
    }

    @Test
    fun `solo se suben las actividades mas recientes y las demas no se pierden`() {
        val all = (0 until SYNCED_ACTIVITIES + 5).map {
            val start = LocalDate(2024, 1, 1).plusDays(it).toString() + "T08:00:00"
            OwnActivity(OWN_ID + start, "running", start, 1800.0)
        }
        val local = AppData(activities = all)
        val payload = syncPayload(local)
        assertEquals(SYNCED_ACTIVITIES, payload.activities.size)
        assertEquals(all.last(), payload.activities.last())
        assertEquals(all, merge(payload, local, payload).data.activities)  // las 5 antiguas siguen en este dispositivo
    }

    // ------------------------------------------------------------ grabar con el GPS

    @Test
    fun `grabar - los puntos fiables suman distancia y al terminar queda una actividad`() {
        val store = MemoryStore()
        val a = app(store)
        val t0 = 1_800_000_000L
        a.startRecording("running", t0)
        assertFailsWith<IllegalArgumentException> { a.startRecording("running", t0) }
        assertFailsWith<IllegalArgumentException> { a.finishRecording() }  // sin recorrido no se guarda nada
        for (t in 0..120) a.recordPoint(t0 + t, 38.5 + t * 4 / 111195.0, -0.1, 50.0, 8.0)  // 4 m/s hacia el norte
        a.recordPoint(t0 + 121, 39.0, -0.1, 50.0, 80.0)  // sin precisión: fuera
        a.recordPoint(t0 + 122, 38.5 + 120 * 4 / 111195.0 + 0.00001, -0.1, 50.0, 8.0)  // 1 m: es el temblor del GPS
        val view = a.recordingView(t0 + 122)!!
        assertEquals("2:02", view.time)
        assertEquals("0,48 km", view.distance)
        assertEquals("4:10 /km", view.pace)
        // La app se cierra y vuelve: la grabación sigue (se guarda cada 20 puntos)
        val again = BrivelApp(store, MemoryStore(), { today })
        assertEquals("0,48 km", again.recordingView(t0 + 122)!!.distance)
        val id = a.finishRecording()
        assertNull(a.recordingView(t0 + 130))
        val activity = a.data.activities.single { it.id == id }
        assertEquals("gps", activity.source)
        assertEquals(120.0, activity.seconds)
        assertEquals(480.0, activity.meters!!, 1.0)
        assertTrue(a.activity(id)!!.track.isNotEmpty())
        assertNull(store.read(RECORDING_KEY))
    }

    @Test
    fun `grabar - un primer punto viejo a miles de kilometros no suma distancia, ni en vivo ni al guardar`() {
        val a = app()
        val t0 = 1_800_000_000L
        a.startRecording("running", t0)
        a.recordPoint(t0, 37.42, -122.08, 0.0, 5.0)  // lo que da el emulador (o una posición guardada) antes del primer punto bueno
        for (t in 1..60) a.recordPoint(t0 + t, 38.5 + t * 4 / 111195.0, -0.1, 0.0, 5.0)
        assertEquals("0,24 km", a.recordingView(t0 + 60)!!.distance)
        val id = a.finishRecording()
        val activity = a.data.activities.single { it.id == id }
        assertEquals(236.0, activity.meters!!, 1.0)  // 59 tramos de 4 m: el salto no cuenta
        assertTrue(activity.speed.values.all { it < 5 })
    }

    @Test
    fun `grabar - en pausa no cuenta ni el tiempo ni los puntos, y se puede descartar`() {
        val a = app()
        val t0 = 1_800_000_000L
        a.startRecording("cycling", t0)
        for (t in 0..10) a.recordPoint(t0 + t, 38.5 + t * 8 / 111195.0, -0.1, null, 5.0)
        a.pauseRecording(t0 + 10)
        a.recordPoint(t0 + 50, 38.6, -0.1, null, 5.0)
        assertEquals("0:10", a.recordingView(t0 + 100)!!.time)
        assertTrue(a.recordingView(t0 + 100)!!.paused)
        a.resumeRecording(t0 + 100)
        a.recordPoint(t0 + 101, 38.6, -0.1, null, 5.0)  // el salto tras la pausa no suma distancia
        assertEquals("0:11", a.recordingView(t0 + 101)!!.time)
        assertEquals("0,08 km", a.recordingView(t0 + 101)!!.distance)
        a.discardRecording()
        assertNull(a.recordingView(t0 + 102))
        assertTrue(a.data.activities.isEmpty())
    }
}
