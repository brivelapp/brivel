package app.brivel.app

// Grabar una actividad con el GPS del móvil (docs/VISION.md §9, bloque G). Cada plataforma pide la ubicación y entrega
// los puntos; aquí se filtran y se convierten en una actividad propia, como si fuera un archivo importado. La ubicación
// no sale del dispositivo: el recorrido se guarda con las series, que no se sincronizan (Activities.kt).

import app.brivel.engine.paceText
import kotlinx.serialization.Serializable

internal const val RECORDING_KEY = DETAIL_KEY + "recording"

/** Los deportes que se graban con el móvil, en su orden. */
val RECORD_SPORTS = listOf("running", "cycling", "walking")

private const val MAX_ACCURACY_M = 30.0  // un punto con más error no se usa [Inferencia]
private const val MIN_MOVE_M = 3.0  // menos que esto es el temblor del GPS estando parado [Inferencia]
private const val MAX_SPEED = 40.0  // m/s (144 km/h): más rápido no es correr ni ir en bici, es un salto del GPS [Inferencia]

@Serializable
internal data class RecordedPoint(val t: Long, val lat: Double, val lon: Double, val alt: Double? = null,
    val d: Double = 0.0)  // metros acumulados hasta este punto: la actividad guardada mide lo mismo que se vio al grabar

/** Lo que enseña la pantalla mientras se graba; `talk`, el test del habla guiado si se graba uno. */
@Serializable
data class RecordingView(val sport: String, val time: String, val distance: String, val pace: String, val paused: Boolean, val hasSignal: Boolean,
    val talk: TalkView? = null)

// Test del habla guiado (2026-11-18): tramos de 3′, cada uno algo más rápido; al acabar cada uno se dice una frase en voz
// alta. El ritmo suave es el del último tramo en que se dijo cómodo (Persinger 2004; FieldTests.kt).
const val TALK_STAGE_S = 180L
private const val TALK_STAGES = 6  // los de R-TEST-TALK: caminar, trotar y 4 tramos más rápidos (unos 18′)
const val TALK_PHRASE = "Estoy saliendo a correr y me encuentro bien. Voy a seguir a este ritmo un poco más, respirando tranquilo."

private fun talkTitle(stage: Int) = when (stage) { 1 -> "Caminar rápido"; 2 -> "Trote muy suave"; else -> "Un poco más rápido" }

/**
 * El test del habla en pantalla: el tramo en marcha y lo que le queda; `asking`, si toca decir la frase y contestar;
 * `done`, los tramos cómodos con su ritmo; `result`, el ritmo suave cuando el test ha acabado.
 */
@Serializable
data class TalkView(val stage: Int, val title: String, val left: String, val asking: Boolean, val done: List<String>, val result: String?)

@Serializable
internal class TalkTest(var from: Long = 0, var fromMeters: Double = 0.0, val paces: MutableList<Double?> = mutableListOf(), var over: Boolean = false,
    var endMeters: Double? = null) {  // metros al acabar el tramo: lo que se tarda en contestar no cuenta en el ritmo
    /** El ritmo suave: el del último tramo cómodo con recorrido del GPS. */
    val result get() = paces.lastOrNull { it != null }
}

@Serializable
internal class Recording(val sport: String, val startedAt: Long, val points: MutableList<RecordedPoint> = mutableListOf(),
    var meters: Double = 0.0, var pausedAt: Long? = null, var pausedSeconds: Long = 0, var talk: TalkTest? = null) {

    /** Segundos en marcha (las pausas no cuentan). */
    fun elapsed(time: Long) = ((pausedAt ?: time) - startedAt - pausedSeconds).coerceAtLeast(0)

    /** Añade el punto si es fiable y hay movimiento desde el último; devuelve si se añadió. */
    fun add(time: Long, lat: Double, lon: Double, altitude: Double?, accuracy: Double): Boolean {
        if (pausedAt != null || accuracy > MAX_ACCURACY_M) return false
        val last = points.lastOrNull()
        if (last != null) {
            if (time <= last.t) return false
            val moved = metersBetween(last.lat, last.lon, lat, lon)
            if (moved < MIN_MOVE_M) return false
            // Tras una pausa (o sin señal) no se suma el salto entre los dos puntos; tampoco un salto imposible del GPS
            // (el primer punto puede ser una posición vieja, a kilómetros de aquí)
            if (time - last.t <= 10 && moved / (time - last.t) <= MAX_SPEED) meters += moved
        }
        points += RecordedPoint(time, lat, lon, altitude, meters)
        talk?.let { t -> if (!t.over && t.endMeters == null && elapsed(time) - t.from >= TALK_STAGE_S) t.endMeters = meters }
        return true
    }

    fun pause(time: Long) { if (pausedAt == null) pausedAt = time }
    fun resume(time: Long) { pausedAt?.let { pausedSeconds += time - it; pausedAt = null } }

    fun view(time: Long): RecordingView {
        val seconds = elapsed(time)
        // El ritmo, con el último minuto de recorrido
        val recent = points.filter { it.t >= time - 60 }
        val speed = if (recent.size >= 2 && recent.last().t > recent.first().t)
            recent.zipWithNext { a, b -> metersBetween(a.lat, a.lon, b.lat, b.lon) }.sum() / (recent.last().t - recent.first().t) else 0.0
        return RecordingView(sport, raceTime(seconds / 60.0), "${app.brivel.engine.fmt(meters / 1000, 2).replace('.', ',')} km",
            if (pausedAt == null) speedText(sport, speed) else "–", pausedAt != null, points.isNotEmpty(), talkView(time))
    }

    private fun talkView(time: Long): TalkView? {
        val t = talk ?: return null
        val stage = t.paces.size + 1
        val left = if (t.over) 0 else (TALK_STAGE_S - (elapsed(time) - t.from)).coerceAtLeast(0)
        return TalkView(stage, talkTitle(stage), raceTime(left / 60.0), !t.over && left == 0L,
            t.paces.mapIndexed { i, p -> "${i + 1} · ${talkTitle(i + 1)}: ${p?.let { "${paceText(it)} /km" } ?: "sin GPS"}, cómodo" },
            if (!t.over) null else t.result?.let { "Tu ritmo suave: ${paceText(it)} /km" } ?: "Sin resultado: no hubo un tramo cómodo con GPS")
    }

    /** Contesta al final del tramo: cómodo pasa al siguiente (hasta 6); costoso acaba el test. */
    fun answerTalk(comfortable: Boolean, time: Long) {
        val t = talk ?: return
        if (t.over) return
        val e = elapsed(time)
        require(e - t.from >= TALK_STAGE_S) { "El tramo aún no ha acabado" }
        if (!comfortable) { t.over = true; return }
        val end = t.endMeters
        val km = ((end ?: meters) - t.fromMeters) / 1000
        t.paces += if (km >= 0.05) (if (end != null) TALK_STAGE_S else e - t.from) / km else null  // menos de 50 m en 3′: sin GPS
        t.endMeters = null
        t.from = e
        t.fromMeters = meters
        if (t.paces.size >= TALK_STAGES) t.over = true
    }

    /** La grabación con la forma de un archivo: el tiempo en marcha sale de los puntos (parado no cuenta). */
    fun file() = FitFile(sport, points.first().t, null, null, null, null, null, null,
        points.map { FitSample(it.t, it.lat, it.lon, it.alt, distance = it.d) }, emptyList())
}
