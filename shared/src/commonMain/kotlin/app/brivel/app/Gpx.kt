package app.brivel.app

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// Lectores de GPX y TCX, los dos formatos de texto (XML) en que Strava, Garmin Connect y casi todas las apps exportan una
// actividad. Se leen con expresiones regulares y no con un lector de XML para no depender de nada: solo se toman las
// etiquetas que Brivel usa, con o sin prefijo de espacio de nombres (gpxtpx:hr, ns3:Watts).
// GPX 1.1: https://www.topografix.com/GPX/1/1/ y la extensión TrackPointExtension v1 de Garmin (hr).
// TCX: esquema TrainingCenterDatabase v2 de Garmin (Activity, Lap, Trackpoint) y ActivityExtension v2 (Speed, Watts).
// [Práctica: etiquetas comprobadas con exportaciones de Strava y Garmin Connect, 2026-11-15]

private fun tagRegex(name: String) = Regex("<(?:[\\w-]+:)?$name\\b[^>]*>\\s*([^<]*?)\\s*</")
private val TRKPT = Regex("<(?:[\\w-]+:)?trkpt\\b([^>]*?)(?:/>|>([\\s\\S]*?)</(?:[\\w-]+:)?trkpt>)")
private val TRACKPOINT = Regex("<(?:[\\w-]+:)?Trackpoint\\b[^>]*>([\\s\\S]*?)</(?:[\\w-]+:)?Trackpoint>")
private val TCX_LAP = Regex("<(?:[\\w-]+:)?Lap\\b[^>]*>([\\s\\S]*?)</(?:[\\w-]+:)?Lap>")
private val TRACK = Regex("<(?:[\\w-]+:)?Track\\b[^>]*>[\\s\\S]*?</(?:[\\w-]+:)?Track>")
private val ACTIVITY = Regex("<(?:[\\w-]+:)?Activity\\b[^>]*?Sport\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>([\\s\\S]*?)</(?:[\\w-]+:)?Activity>")
private val LAT = Regex("\\blat\\s*=\\s*[\"']([^\"']+)")
private val LON = Regex("\\blon\\s*=\\s*[\"']([^\"']+)")
private val TIME = tagRegex("time")
private val ELE = tagRegex("ele")
private val HR = tagRegex("hr")
private val POWER = Regex("<(?:[\\w-]+:)?(?:power|PowerInWatts)\\b[^>]*>\\s*([^<]*?)\\s*</")
private val TYPE = tagRegex("type")
private val TCX_TIME = tagRegex("Time")
private val LATITUDE = tagRegex("LatitudeDegrees")
private val LONGITUDE = tagRegex("LongitudeDegrees")
private val ALTITUDE = tagRegex("AltitudeMeters")
private val DISTANCE = tagRegex("DistanceMeters")
private val HR_VALUE = Regex("<(?:[\\w-]+:)?HeartRateBpm\\b[^>]*>\\s*<(?:[\\w-]+:)?Value>\\s*([^<]*?)\\s*</")
private val AVG_HR = Regex("<(?:[\\w-]+:)?AverageHeartRateBpm\\b[^>]*>\\s*<(?:[\\w-]+:)?Value>\\s*([^<]*?)\\s*</")
private val SPEED = tagRegex("Speed")
private val WATTS = tagRegex("Watts")
private val TOTAL_TIME = tagRegex("TotalTimeSeconds")
private val ID = tagRegex("Id")

private fun Regex.number(text: String) = find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it.isFinite() }

@OptIn(ExperimentalTime::class)
private fun epoch(text: String?): Long? = text?.let { t ->  // sin zona, se toma UTC, como hace Strava
    runCatching { Instant.parse(t).epochSeconds }.recoverCatching { Instant.parse(t + "Z").epochSeconds }.getOrNull()
}

/** El nombre del deporte de GPX o TCX → deporte de Brivel. Los tipos numéricos de algunas apps no se entienden. */
private fun textSport(name: String?): String {
    val s = name?.lowercase() ?: return "other"
    return when {
        "run" in s -> "running"
        "bik" in s || "cycl" in s || "ride" in s -> "cycling"
        "walk" in s || "hik" in s -> "walking"
        "swim" in s -> "swimming_pool"
        else -> "other"
    }
}

/** Lee un GPX: los puntos del recorrido de todos los tramos (trk/trkseg). Sin resumen: la app calcula los totales. */
fun parseGpx(text: String): FitFile {
    val samples = TRKPT.findAll(text).mapNotNull { m ->
        val body = m.groupValues[2]
        val time = epoch(TIME.find(body)?.groupValues?.get(1)) ?: return@mapNotNull null
        val lat = LAT.number(m.groupValues[1])?.takeIf { it in -90.0..90.0 }
        val lon = LON.number(m.groupValues[1])?.takeIf { it in -180.0..180.0 }
        FitSample(time, lat?.takeIf { lon != null }, lon?.takeIf { lat != null }, ELE.number(body), HR.number(body), power = POWER.number(body))
    }.sortedBy { it.time }.toList()
    require(samples.isNotEmpty()) { "El archivo GPX no tiene ningún punto con hora" }
    return FitFile(textSport(TYPE.find(text.substringAfter("<trk", ""))?.groupValues?.get(1)), samples.first().time,
        null, null, null, null, null, null, samples, emptyList())
}

/** Lee un TCX: la primera actividad, con sus vueltas y sus puntos. Los totales son la suma de las vueltas. */
fun parseTcx(text: String): FitFile {
    val activity = ACTIVITY.find(text)
    require(activity != null) { "El archivo TCX no tiene ninguna actividad" }
    val body = activity.groupValues[2]
    val samples = TRACKPOINT.findAll(body).mapNotNull { m ->
        val p = m.groupValues[1]
        val time = epoch(TCX_TIME.find(p)?.groupValues?.get(1)) ?: return@mapNotNull null
        val lat = LATITUDE.number(p)?.takeIf { it in -90.0..90.0 }
        val lon = LONGITUDE.number(p)?.takeIf { it in -180.0..180.0 }
        FitSample(time, lat?.takeIf { lon != null }, lon?.takeIf { lat != null }, ALTITUDE.number(p), HR_VALUE.number(p),
            DISTANCE.number(p), SPEED.number(p), WATTS.number(p))
    }.sortedBy { it.time }.toList()
    // Los totales de la vuelta, sin sus puntos (que también tienen DistanceMeters)
    val laps = TCX_LAP.findAll(body).mapNotNull { m ->
        val lap = TRACK.replace(m.groupValues[1], "")
        TOTAL_TIME.number(lap)?.takeIf { it > 0 }?.let { FitLap(it, DISTANCE.number(lap), AVG_HR.number(lap), null) }
    }.toList()
    val start = epoch(ID.find(body)?.groupValues?.get(1)) ?: samples.firstOrNull()?.time
    require(start != null) { "El archivo TCX no tiene ninguna actividad con hora" }
    return FitFile(textSport(activity.groupValues[1]), start, laps.sumOf { it.seconds }.takeIf { it > 0 },
        laps.mapNotNull { it.meters }.takeIf { it.isNotEmpty() }?.sum(), null, null, null, null, samples, laps)
}

/** Lee un archivo de actividad FIT, GPX o TCX según lo que lleva dentro, no según su nombre. Devuelve el formato. */
fun parseActivityFile(bytes: ByteArray): Pair<FitFile, String> {
    if (bytes.size >= 12 && bytes.decodeToString(8, 12) == ".FIT") return parseFit(bytes) to "fit"
    val text = bytes.decodeToString()
    return when {
        "<TrainingCenterDatabase" in text -> parseTcx(text) to "tcx"
        "<gpx" in text -> parseGpx(text) to "gpx"
        else -> throw IllegalArgumentException("No es un archivo de actividad: Brivel lee FIT, GPX y TCX")
    }
}
