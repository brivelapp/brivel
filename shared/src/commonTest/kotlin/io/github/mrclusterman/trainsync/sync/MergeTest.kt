package io.github.mrclusterman.trainsync.sync

import io.github.mrclusterman.trainsync.app.AppData
import io.github.mrclusterman.trainsync.app.GoalKind
import io.github.mrclusterman.trainsync.app.HealthSnapshot
import io.github.mrclusterman.trainsync.app.IntervalsCache
import io.github.mrclusterman.trainsync.app.LiftLog
import io.github.mrclusterman.trainsync.app.ManualDone
import io.github.mrclusterman.trainsync.app.Readiness
import io.github.mrclusterman.trainsync.app.SCHEMA
import io.github.mrclusterman.trainsync.app.UserGoal
import io.github.mrclusterman.trainsync.app.UserProfile
import io.github.mrclusterman.trainsync.app.WeekChange
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
        lifts = listOf(LiftLog("2026-11-02", "Sentadilla", 60.0, 8, 3)),
        reminderHour = 8,
    )

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
