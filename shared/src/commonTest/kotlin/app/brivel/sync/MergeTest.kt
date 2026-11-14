package app.brivel.sync

import app.brivel.app.AppData
import app.brivel.app.GoalKind
import app.brivel.app.HealthSnapshot
import app.brivel.app.IntervalsCache
import app.brivel.app.LiftLog
import app.brivel.app.SetLog
import app.brivel.app.ManualDone
import app.brivel.app.Readiness
import app.brivel.app.SCHEMA
import app.brivel.app.UserGoal
import app.brivel.app.UserProfile
import app.brivel.app.WeekChange
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MergeTest {
    private val run = ManualDone("2026-11-02", "running")
    private val ride = ManualDone("2026-11-03", "cycling")
    private val swim = ManualDone("2026-11-04", "swimming_pool")

    // Todos los campos distintos de los valores por defecto: si AppData gana un campo, añádelo aquí y a merge
    private val full = AppData(
        customExercises = listOf(app.brivel.app.CustomExercise("Curl con TRX", "brazos")),
        profile = UserProfile(birthYear = 1990, level = "advanced"),
        goal = UserGoal(GoalKind.FITNESS, since = "2026-11-02"),
        blockStart = "2026-11-02",
        manualDone = listOf(run),
        weekChanges = mapOf("2026-11-02" to WeekChange(mapOf("2026-11-03" to 0), since = "2026-11-02")),
        readiness = mapOf("2026-11-02" to Readiness(3, 4, 2)),
        choices = mapOf("2026-11-02" to mapOf("running" to "cycling")),
        renamed = setOf("i1"),
        cache = IntervalsCache(JsonObject(emptyMap()), emptyList(), emptyList(), "2026-11-02T08:00"),
        intervalsAthlete = "i123",
        health = HealthSnapshot("Apple Salud", emptyList(), emptyList(), "2026-11-02T08:00"),
        garmin = HealthSnapshot("Garmin", emptyList(), emptyList(), "2026-11-02"),
        lifts = listOf(LiftLog("2026-11-02", "Sentadilla", 60.0, 8, 3)),
        sets = listOf(SetLog("2026-11-02", "Sentadilla", 1, 60.0, 8)),
        reminderHour = 8,
        garminLink = app.brivel.app.GarminLink("privada", "publica", "token", "2026-11-02"),
        swaps = listOf(app.brivel.app.ExerciseSwap("2026-11-02", "Sentadilla con barra", "Prensa de piernas")),
        moves = listOf(app.brivel.app.SessionMove("2026-11-03", "strength", "2026-11-05")),
        routines = listOf(app.brivel.app.Routine("r1", "Torso", listOf(app.brivel.app.RoutineExercise("Flexiones", 3, 8, 12)), listOf("MON"), since = "2026-11-02")),
        equipment = listOf("mancuernas", "goma"),
        workouts = listOf(app.brivel.app.FreeWorkout("2026-11-02", "Entreno libre", listOf(app.brivel.app.RoutineExercise("Flexiones", 3, 8, 12)))),
        measurements = listOf(app.brivel.app.BodyMeasure("2026-11-02", "waist", 82.0)),
    )

    /** Guarda de la lista de arriba: si AppData gana un campo y no se añade a `full`, este test falla y lo nombra. */
    @Test
    fun theFullSampleChangesEveryField() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val empty = json.encodeToJsonElement(AppData.serializer(), AppData()) as JsonObject
        val sample = json.encodeToJsonElement(AppData.serializer(), full) as JsonObject
        assertEquals(emptyList(), empty.keys.filter { it != "schema" && empty[it] == sample[it] })
    }

    @Test
    fun equipmentFollowsTheSideThatChangedAndWarnsWhenBothDid() {
        val mine = full.copy(equipment = listOf("barra"))
        val theirs = full.copy(equipment = listOf("pesa rusa"))
        assertEquals(listOf("barra"), merge(full, mine, full).data.equipment)
        assertEquals(listOf("pesa rusa"), merge(full, full, theirs).data.equipment)
        val both = merge(full, mine, theirs)
        assertEquals(listOf("pesa rusa") to setOf("equipment"), both.data.equipment to both.conflicts)
        assertEquals(emptyList(), merge(full, full.copy(equipment = emptyList()), full).data.equipment)  // vaciarlo también cuenta
    }

    @Test
    fun everyFieldFollowsTheSideThatChanged() {
        assertEquals(full, merge(base = AppData(), local = full, remote = AppData()).data)
        assertEquals(full, merge(base = AppData(), local = AppData(), remote = full).data)
        assertEquals(full, merge(base = full, local = full, remote = full).data)
    }

    @Test
    fun bothChangedTheProfile_serverWinsAndWarns() {
        val base = full
        val local = full.copy(profile = UserProfile(level = "beginner"))
        val remote = full.copy(profile = UserProfile(level = "intermediate"))
        val merged = merge(base, local, remote)
        assertEquals(remote.profile, merged.data.profile)
        assertEquals(setOf("profile"), merged.conflicts)
    }

    @Test
    fun sameChangeOnBothSidesIsNotAConflict() {
        val changed = full.copy(reminderHour = 20)
        assertEquals(emptySet(), merge(full, changed, changed).conflicts)
    }

    @Test
    fun goalAndBlockStartMoveTogether() {
        val local = full.copy(goal = UserGoal(GoalKind.STRENGTH, since = "2026-11-09"), blockStart = "2026-11-09")
        val merged = merge(full, local, full.copy(reminderHour = 7)).data
        assertEquals(local.goal to local.blockStart, merged.goal to merged.blockStart)
        assertEquals(7, merged.reminderHour)
    }

    @Test
    fun listsKeepAdditionsAndDeletionsFromBothSides() {
        val base = full.copy(manualDone = listOf(run, ride))
        val local = base.copy(manualDone = listOf(ride, swim))  // desmarca run, marca swim
        val remote = base.copy(manualDone = listOf(run))  // desmarca ride
        assertEquals(setOf(swim), merge(base, local, remote).data.manualDone.toSet())
    }

    @Test
    fun mapsMergeByDay() {
        val base = full.copy(readiness = mapOf("2026-11-01" to Readiness(3, 3, 3)))
        val local = base.copy(readiness = base.readiness + ("2026-11-02" to Readiness(5, 5, 1)))
        val remote = base.copy(readiness = emptyMap())  // otro dispositivo lo borró
        assertEquals(mapOf("2026-11-02" to Readiness(5, 5, 1)), merge(base, local, remote).data.readiness)
    }

    @Test
    fun downloadedDataNeverWarns() {
        val local = full.copy(cache = full.cache!!.copy(syncedAt = "2026-11-02T09:00"))
        val remote = full.copy(cache = full.cache!!.copy(syncedAt = "2026-11-02T10:00"))
        val merged = merge(full, local, remote)
        assertEquals(remote.cache, merged.data.cache)
        assertTrue(merged.conflicts.isEmpty())
    }

    @Test
    fun localIntervalsCopyStaysButIsNeverUploaded() {
        val uploaded = syncPayload(full)
        assertEquals(null, uploaded.cache)
        val remote = uploaded.copy(reminderHour = 21)  // otro dispositivo cambió algo
        val merged = merge(base = uploaded, local = full, remote = remote).data
        assertEquals(full.cache, merged.cache)
        assertEquals(21, merged.reminderHour)
    }

    @Test
    fun refusesDataFromANewerApp() {
        assertFailsWith<IllegalArgumentException> { merge(full, full, full.copy(schema = SCHEMA + 1)) }
    }
}
