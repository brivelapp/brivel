package app.brivel.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// En commonTest para que las expresiones regulares se prueben también en JavaScript (la web).

/** Como lo exporta Strava: tipo de actividad, extensión de Garmin con el pulso y un punto sin hora que se ignora. */
internal val GPX = """<?xml version="1.0" encoding="UTF-8"?>
<gpx creator="StravaGPX" version="1.1" xmlns="http://www.topografix.com/GPX/1/1"
  xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
 <metadata><time>2024-11-08T07:00:00Z</time></metadata>
 <trk>
  <name>Rodaje</name>
  <type>running</type>
  <trkseg>
   <trkpt lat="38.5000000" lon="-0.1000000">
    <ele>100.0</ele>
    <time>2024-11-08T07:30:00Z</time>
    <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>140</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>
   </trkpt>
   <trkpt lat="38.5000270" lon="-0.1000000"><ele>101.0</ele><time>2024-11-08T07:30:01.000Z</time>
    <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>142</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions></trkpt>
   <trkpt lat="38.6" lon="-0.2"/>
   <trkpt lat='38.5000540' lon='-0.1000000'><time>2024-11-08T08:30:02+01:00</time></trkpt>
  </trkseg>
 </trk>
</gpx>"""

/** Como lo exporta Garmin Connect: dos vueltas con sus totales y la extensión con velocidad y vatios. */
internal val TCX = """<?xml version="1.0" encoding="UTF-8"?>
<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
  xmlns:ns3="http://www.garmin.com/xmlschemas/ActivityExtension/v2">
 <Activities>
  <Activity Sport="Biking">
   <Id>2024-11-08T07:30:00.000Z</Id>
   <Lap StartTime="2024-11-08T07:30:00.000Z">
    <TotalTimeSeconds>600.0</TotalTimeSeconds>
    <DistanceMeters>5000.0</DistanceMeters>
    <AverageHeartRateBpm><Value>130</Value></AverageHeartRateBpm>
    <Track>
     <Trackpoint>
      <Time>2024-11-08T07:30:00.000Z</Time>
      <Position><LatitudeDegrees>38.5</LatitudeDegrees><LongitudeDegrees>-0.1</LongitudeDegrees></Position>
      <AltitudeMeters>100.0</AltitudeMeters>
      <DistanceMeters>0.0</DistanceMeters>
      <HeartRateBpm><Value>128</Value></HeartRateBpm>
      <Extensions><ns3:TPX><ns3:Speed>8.3</ns3:Speed><ns3:Watts>210</ns3:Watts></ns3:TPX></Extensions>
     </Trackpoint>
     <Trackpoint>
      <Time>2024-11-08T07:30:01.000Z</Time>
      <DistanceMeters>8.3</DistanceMeters>
      <HeartRateBpm><Value>129</Value></HeartRateBpm>
     </Trackpoint>
    </Track>
   </Lap>
   <Lap StartTime="2024-11-08T07:40:00.000Z">
    <TotalTimeSeconds>590.5</TotalTimeSeconds>
    <DistanceMeters>5100.0</DistanceMeters>
    <Track/>
   </Lap>
  </Activity>
 </Activities>
</TrainingCenterDatabase>"""

private const val START = 1731051000L  // 2024-11-08T07:30:00Z

class GpxTest {
    @Test
    fun `lee un GPX - deporte, puntos con hora, pulso y altitud`() {
        val gpx = parseGpx(GPX)
        assertEquals("running", gpx.sport)
        assertEquals(START, gpx.start)
        assertEquals(listOf(START, START + 1, START + 2), gpx.samples.map { it.time })  // +01:00 pasa a UTC
        assertEquals(listOf(140.0, 142.0, null), gpx.samples.map { it.hr })
        assertEquals(101.0, gpx.samples[1].altitude)
        assertEquals(38.500054, gpx.samples[2].lat!!, 1e-9)
        assertNull(gpx.seconds)
        assertNull(gpx.meters)
    }

    @Test
    fun `lee un TCX - vueltas sin confundir su distancia con la de los puntos`() {
        val tcx = parseTcx(TCX)
        assertEquals("cycling", tcx.sport)
        assertEquals(START, tcx.start)
        assertEquals(1190.5, tcx.seconds)
        assertEquals(10100.0, tcx.meters)
        assertEquals(listOf(600.0, 590.5), tcx.laps.map { it.seconds })
        assertEquals(listOf(5000.0, 5100.0), tcx.laps.map { it.meters })
        assertEquals(130.0, tcx.laps[0].avgHr)
        val p = tcx.samples[0]
        assertEquals(listOf(38.5, -0.1, 100.0, 0.0, 128.0, 8.3, 210.0), listOf(p.lat, p.lon, p.altitude, p.distance, p.hr, p.speed, p.power))
        assertNull(tcx.samples[1].lat)
    }

    @Test
    fun `elige el formato por el contenido`() {
        assertEquals("gpx", parseActivityFile(GPX.encodeToByteArray()).second)
        assertEquals("tcx", parseActivityFile(TCX.encodeToByteArray()).second)
        assertFailsWith<IllegalArgumentException> { parseActivityFile("<html></html>".encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { parseActivityFile("<gpx><trk><trkseg></trkseg></trk></gpx>".encodeToByteArray()) }
    }
}
