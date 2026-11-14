package app.brivel.app

// Grabar una actividad con el GPS del móvil (docs/VISION.md §9, bloque G). Cada plataforma pide la ubicación y entrega
// los puntos; aquí se filtran y se convierten en una actividad propia, como si fuera un archivo importado. La ubicación
// no sale del dispositivo: el recorrido se guarda con las series, que no se sincronizan (Activities.kt).

import kotlinx.serialization.Serializable

internal const val RECORDING_KEY = DETAIL_KEY + "recording"

/** Los deportes que se graban con el móvil, en su orden. */
val RECORD_SPORTS = listOf("running", "cycling", "walking")

private const val MAX_ACCURACY_M = 30.0  // un punto con más error no se usa [Inferencia]
private const val MIN_MOVE_M = 3.0  // menos que esto es el temblor del GPS estando parado [Inferencia]

@Serializable
internal data class RecordedPoint(val t: Long, val lat: Double, val lon: Double, val alt: Double? = null)

/** Lo que enseña la pantalla mientras se graba. */
@Serializable
data class RecordingView(val sport: String, val time: String, val distance: String, val pace: String, val paused: Boolean, val hasSignal: Boolean)

@Serializable
internal class Recording(val sport: String, val startedAt: Long, val points: MutableList<RecordedPoint> = mutableListOf(),
    var meters: Double = 0.0, var pausedAt: Long? = null, var pausedSeconds: Long = 0) {

    /** Añade el punto si es fiable y hay movimiento desde el último; devuelve si se añadió. */
    fun add(time: Long, lat: Double, lon: Double, altitude: Double?, accuracy: Double): Boolean {
        if (pausedAt != null || accuracy > MAX_ACCURACY_M) return false
        val last = points.lastOrNull()
        if (last != null) {
            if (time <= last.t) return false
            val moved = metersBetween(last.lat, last.lon, lat, lon)
            if (moved < MIN_MOVE_M) return false
            // Tras una pausa (o sin señal) no se suma el salto entre los dos puntos
            if (time - last.t <= 10) meters += moved
        }
        points += RecordedPoint(time, lat, lon, altitude)
        return true
    }

    fun pause(time: Long) { if (pausedAt == null) pausedAt = time }
    fun resume(time: Long) { pausedAt?.let { pausedSeconds += time - it; pausedAt = null } }

    fun view(time: Long): RecordingView {
        val seconds = ((pausedAt ?: time) - startedAt - pausedSeconds).coerceAtLeast(0)
        // El ritmo, con el último minuto de recorrido
        val recent = points.filter { it.t >= time - 60 }
        val speed = if (recent.size >= 2 && recent.last().t > recent.first().t)
            recent.zipWithNext { a, b -> metersBetween(a.lat, a.lon, b.lat, b.lon) }.sum() / (recent.last().t - recent.first().t) else 0.0
        return RecordingView(sport, raceTime(seconds / 60.0), "${app.brivel.engine.fmt(meters / 1000, 2).replace('.', ',')} km",
            if (pausedAt == null) speedText(sport, speed) else "–", pausedAt != null, points.isNotEmpty())
    }

    /** La grabación con la forma de un archivo: el tiempo en marcha sale de los puntos (parado no cuenta). */
    fun file() = FitFile(sport, points.first().t, null, null, null, null, null, null,
        points.map { FitSample(it.t, it.lat, it.lon, it.alt) }, emptyList())
}
