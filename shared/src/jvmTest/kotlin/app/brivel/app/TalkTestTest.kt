package app.brivel.app

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** El test del habla guiado: tramos de 3′ grabados con el GPS y el ritmo suave guardado solo (Recorder.kt). */
class TalkTestTest {
    private val t0 = 1_800_000_000L
    private var t = t0
    private var lat = 38.58

    private fun app() = BrivelApp(MemoryStore(), MemoryStore(), { LocalDate(2026, 10, 19) }).apply {
        saveProfile(UserProfile(birthYear = 1990, level = "beginner", strength = "gym", cardio = listOf("running"),
            days = mapOf("MON" to 60, "WED" to 60, "SAT" to 60)))
        setGoal(GoalKind.FITNESS)
    }

    /** Un tramo a ese ritmo, con un punto del GPS cada 2 s. */
    private fun BrivelApp.run(seconds: Long, paceSecKm: Double) {
        repeat((seconds / 2).toInt()) {
            t += 2
            lat += 2 * 1000 / paceSecKm / 111_195.0
            recordPoint(t, lat, -0.07, null, 5.0)
        }
    }

    @Test fun theEasyPaceIsTheLastComfortableStage() {
        val a = app()
        a.startTalkTest(t)
        assertEquals("3:00", a.recordingView(t)!!.talk!!.left)
        a.run(60, 600.0)
        assertFailsWith<IllegalArgumentException> { a.answerTalk(true, t) }  // el tramo aún no ha acabado
        a.run(120, 600.0)
        assertTrue(a.recordingView(t)!!.talk!!.asking)
        assertNull(a.answerTalk(true, t))
        assertEquals(2, a.recordingView(t)!!.talk!!.stage)
        a.run(180, 500.0)
        t += 60  // se para un minuto antes de contestar: no cambia el ritmo del tramo
        assertNull(a.answerTalk(true, t))
        assertTrue(a.recordingView(t)!!.talk!!.done[1].contains("8:2"), a.recordingView(t)!!.talk!!.done[1])
        a.run(180, 434.0)
        assertNull(a.answerTalk(true, t))
        a.run(180, 380.0)
        val done = a.answerTalk(false, t)!!  // a 6:20 /km ya cuesta: vale el tramo de 7:14
        assertTrue(done.startsWith("Hecho: tu ritmo suave es 7:1"), done)
        assertEquals(434.0, a.data.fieldTests.single().paceSecKm!!, 6.0)  // tras el minuto parado, el primer punto no suma
        val view = a.recordingView(t)!!.talk!!
        assertEquals(3, view.done.size)
        assertFalse(view.asking)
        a.finishRecording()
        assertEquals(1, a.data.fieldTests.size)
    }

    @Test fun finishingHalfwayKeepsWhatWasDone() {
        val a = app()
        a.startTalkTest(t)
        a.run(180, 600.0)
        a.answerTalk(true, t)
        a.run(90, 500.0)
        a.finishRecording()
        assertEquals(600.0, a.data.fieldTests.single().paceSecKm!!, 10.0)  // el primer punto llega a los 2 s
    }

    @Test fun withoutGpsThereIsNoResult() {
        val a = app()
        a.startTalkTest(t)
        t += TALK_STAGE_S
        a.answerTalk(true, t)
        assertTrue(a.stopTalkTest()!!.startsWith("El GPS no ha dado"))
        assertTrue(a.data.fieldTests.isEmpty())
    }

    @Test fun aPauseStopsTheStageClock() {
        val a = app()
        a.startTalkTest(t)
        a.run(60, 600.0)
        a.pauseRecording(t)
        t += 300
        assertEquals("2:00", a.recordingView(t)!!.talk!!.left)
        a.resumeRecording(t)
        assertFalse(a.recordingView(t)!!.talk!!.asking)
    }
}
