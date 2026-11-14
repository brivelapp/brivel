package app.brivel.app

// Lector de archivos FIT (el formato en que los relojes y ciclocomputadores guardan una actividad), escrito aquí para no
// depender de nada: cabecera, mensajes de definición y de datos, y solo los campos que Brivel usa. Los números de mensaje
// y de campo están comprobados con el perfil de la librería abierta python-fitparse. [Verificado: 2026-11-15]

/** Un punto del registro. `time`: segundos desde 1970 (UTC). */
class FitSample(val time: Long, val lat: Double? = null, val lon: Double? = null, val altitude: Double? = null,
    val hr: Double? = null, val distance: Double? = null, val speed: Double? = null, val power: Double? = null)

/** Una vuelta: tiempo en marcha, distancia y medias. */
class FitLap(val seconds: Double, val meters: Double?, val avgHr: Double?, val avgPower: Double?)

/** Lo leído de un archivo. Los totales vienen del resumen del reloj; null si el archivo no lo trae. */
class FitFile(val sport: String, val start: Long, val seconds: Double?, val meters: Double?, val avgHr: Double?,
    val maxHr: Double?, val avgPower: Double?, val ascent: Double?, val samples: List<FitSample>, val laps: List<FitLap>)

private const val FIT_EPOCH = 631065600L  // los tiempos de FIT cuentan desde el 1989-12-31 (UTC)
private const val SESSION = 18
private const val LAP = 19
private const val RECORD = 20
private const val TIMESTAMP = 253

private class FitField(val num: Int, val size: Int, val type: Int)
private class FitDefinition(val global: Int, val big: Boolean, val fields: List<FitField>, val developerBytes: Int) {
    val size = fields.sumOf { it.size } + developerBytes
}

/** Deporte de FIT (y subdeporte) → deporte de Brivel. */
private fun fitSport(sport: Long?, sub: Long?): String = when (sport) {
    1L -> "running"
    2L, 21L -> "cycling"
    5L -> "swimming_pool"
    11L, 17L -> "walking"
    10L -> if (sub == 20L) "strength" else "other"
    else -> "other"
}

/**
 * Lee un archivo FIT de actividad. Un archivo cortado se lee hasta donde llega; no se comprueba el CRC.
 * shortcut: de un archivo con varios deportes (triatlón) solo se toma el resumen del primero; separar por sesión si se pide.
 */
fun parseFit(bytes: ByteArray): FitFile {
    require(bytes.size >= 12 && bytes.decodeToString(8, 12) == ".FIT") { "No es un archivo FIT" }
    fun u8(p: Int) = bytes[p].toLong() and 0xFF
    fun number(p: Int, size: Int, big: Boolean): Long {
        var v = 0L
        for (i in 0 until size) v = v or (u8(p + i) shl 8 * (if (big) size - 1 - i else i))
        return v
    }
    val headerSize = u8(0).toInt()
    val end = minOf(bytes.size.toLong(), headerSize + number(4, 4, false)).toInt()
    val definitions = HashMap<Int, FitDefinition>()
    val samples = mutableListOf<FitSample>()
    val laps = mutableListOf<FitLap>()
    var session: Map<Int, Long>? = null
    var lastTime = 0L
    var p = headerSize
    while (p < end) {
        val header = u8(p++).toInt()
        val compressed = header and 0x80 != 0
        if (!compressed && header and 0x40 != 0) {
            if (p + 5 > end) break
            val big = u8(p + 1) == 1L
            val global = number(p + 2, 2, big).toInt()
            val count = u8(p + 4).toInt()
            p += 5
            if (p + 3 * count > end) break
            val fields = (0 until count).map { FitField(u8(p + 3 * it).toInt(), u8(p + 3 * it + 1).toInt(), u8(p + 3 * it + 2).toInt()) }
            p += 3 * count
            var developerBytes = 0
            if (header and 0x20 != 0) {  // campos de desarrollador (sensores de terceros): se saltan
                if (p >= end) break
                val n = u8(p++).toInt()
                if (p + 3 * n > end) break
                for (i in 0 until n) developerBytes += u8(p + 3 * i + 1).toInt()
                p += 3 * n
            }
            definitions[header and 0x0F] = FitDefinition(global, big, fields, developerBytes)
            continue
        }
        val definition = definitions[if (compressed) header shr 5 and 3 else header and 0x0F] ?: break
        if (p + definition.size > end) break
        val values = HashMap<Int, Long>()
        var q = p
        for (f in definition.fields) {
            // Solo números de 1, 2 o 4 bytes; el valor con todos los bits a 1 (o el mayor con signo) es «sin dato»
            if (f.size == 1 || f.size == 2 || f.size == 4) {
                val raw = number(q, f.size, definition.big)
                val signed = f.type and 0x1F == 5  // sint32: las coordenadas
                val value = if (signed) raw.toInt().toLong() else raw
                val invalid = if (signed) 0x7FFFFFFFL else (1L shl 8 * f.size) - 1
                if (value != invalid) values[f.num] = value
            }
            q += f.size
        }
        p += definition.size
        if (compressed) {  // la hora va en la cabecera: los 5 bits bajos de la última hora completa
            val offset = (header and 0x1F).toLong()
            lastTime = (lastTime and 0x1FL.inv()) + offset + if (offset < (lastTime and 0x1F)) 32 else 0
            values[TIMESTAMP] = lastTime
        } else values[TIMESTAMP]?.let { lastTime = it }
        when (definition.global) {
            RECORD -> samples += FitSample(
                time = (values[TIMESTAMP] ?: lastTime) + FIT_EPOCH,
                lat = values[0]?.let { it * 180.0 / 2147483648.0 },  // semicírculos
                lon = values[1]?.let { it * 180.0 / 2147483648.0 },
                altitude = (values[78] ?: values[2])?.let { it / 5.0 - 500 },
                hr = values[3]?.toDouble(),
                distance = values[5]?.let { it / 100.0 },
                speed = (values[73] ?: values[6])?.let { it / 1000.0 },
                power = values[7]?.toDouble(),
            )
            LAP -> values[8]?.let { timer ->
                laps += FitLap(timer / 1000.0, values[9]?.let { it / 100.0 }, values[15]?.toDouble(), values[19]?.toDouble())
            }
            SESSION -> if (session == null) session = values
        }
    }
    val s = session
    val start = s?.get(2)?.let { it + FIT_EPOCH } ?: samples.firstOrNull()?.time
    require(start != null) { "El archivo no tiene ninguna actividad" }
    return FitFile(fitSport(s?.get(5), s?.get(6)), start, s?.get(8)?.let { it / 1000.0 }, s?.get(9)?.let { it / 100.0 },
        s?.get(16)?.toDouble(), s?.get(17)?.toDouble(), s?.get(20)?.toDouble(), s?.get(22)?.toDouble(), samples, laps)
}
