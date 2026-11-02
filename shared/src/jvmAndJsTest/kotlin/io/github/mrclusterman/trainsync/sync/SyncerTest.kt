package io.github.mrclusterman.trainsync.sync

import io.github.mrclusterman.trainsync.app.AppData
import io.github.mrclusterman.trainsync.app.IntervalsCache
import io.github.mrclusterman.trainsync.app.KeyValueStore
import io.github.mrclusterman.trainsync.app.ManualDone
import io.github.mrclusterman.trainsync.app.TrainSyncApp
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Servidor falso con las mismas reglas que server/src/index.ts (versiones, 404 y 409). */
private class FakeServer {
    val copies = mutableMapOf<String, Pair<Long, ByteArray>>()  // token → versión y bloque
    var beforeNextPut: (suspend () -> Unit)? = null  // otro dispositivo que sube justo antes
    var puts = 0

    val engine = MockEngine { request ->
        val id = request.headers[HttpHeaders.Authorization]!!.removePrefix("Bearer ")
        val current = copies[id]
        when (request.method) {
            HttpMethod.Get -> current?.let { respond(it.second, HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"${it.first}\"")) }
                ?: respond("""{"error":"No hay copia"}""", HttpStatusCode.NotFound)
            HttpMethod.Delete -> respond("", HttpStatusCode.NoContent).also { copies.remove(id) }
            else -> {
                puts++
                beforeNextPut?.also { beforeNextPut = null }?.invoke()
                val now = copies[id]
                val body = request.body.toByteArray()
                val version = when {
                    request.headers[HttpHeaders.IfNoneMatch] == "*" -> if (now == null) 1L else null
                    else -> request.headers[HttpHeaders.IfMatch]!!.trim('"').toLong().takeIf { it == now?.first }?.plus(1)
                }
                if (version == null) respond("""{"error":"Otro subió antes"}""", HttpStatusCode.Conflict)
                else respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"$version\"")).also { copies[id] = version to body }
            }
        }
    }
}

private class Store : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun read(key: String) = map[key]
    override fun write(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

/** Un dispositivo: sus datos, su almacén y su Syncer contra el servidor falso. */
private class Device(server: FakeServer, val keys: SyncKeys) {
    val store = Store()
    val app = TrainSyncApp(store, Store(), { LocalDate(2026, 11, 2) })
    val syncer = Syncer(app, store, keys, SyncClient("https://brivel.app/api/boveda", keys.bearer, server.engine))
    val done get() = app.data.manualDone.toSet()

    fun mark(date: String, sport: String) = app.markDone(date, sport)
}

class SyncerTest {
    private val server = FakeServer()
    private val run = ManualDone("2026-11-02", "running")
    private val ride = ManualDone("2026-11-01", "cycling")
    private val swim = ManualDone("2026-10-31", "swimming_pool")

    private suspend fun pair(): Pair<Device, Device> {
        val keys = deriveKeys(SyncCode.generate())
        return Device(server, keys) to Device(server, keys)
    }

    @Test
    fun firstDeviceCreatesSecondJoinsAndBothEndEqual() = runTest {
        val (a, b) = pair()
        a.mark(run.date, run.sport)
        b.mark(ride.date, ride.sport)
        assertEquals(1, a.syncer.sync(create = true).version)
        assertEquals(2, b.syncer.sync().version)  // b junta lo suyo con la copia y sube la 2
        a.syncer.sync()
        assertEquals(setOf(run, ride), a.done)
        assertEquals(setOf(run, ride), b.done)
        val putsBefore = server.puts
        a.syncer.sync()  // sin cambios: no sube nada
        assertEquals(putsBefore, server.puts)
    }

    @Test
    fun deletionsTravel() = runTest {
        val (a, b) = pair()
        a.mark(run.date, run.sport)
        a.mark(ride.date, ride.sport)
        a.syncer.sync(create = true)
        b.syncer.sync()
        b.app.unmarkDone(run.date, run.sport)
        b.syncer.sync()
        a.syncer.sync()
        assertEquals(setOf(ride), a.done)
    }

    @Test
    fun anotherDeviceUploadingAtTheSameTimeIsRetried() = runTest {
        val (a, b) = pair()
        a.syncer.sync(create = true)
        b.syncer.sync()
        a.mark(run.date, run.sport)
        b.mark(swim.date, swim.sport)
        server.beforeNextPut = { b.syncer.sync() }  // b sube justo antes que a
        a.syncer.sync()  // 409 → vuelve a bajar, combina y sube
        b.syncer.sync()
        assertEquals(setOf(run, swim), a.done)
        assertEquals(setOf(run, swim), b.done)
    }

    @Test
    fun joiningWithoutACopyFailsInsteadOfCreatingOne() = runTest {
        val (a, _) = pair()
        assertEquals(404, assertFailsWith<SyncException> { a.syncer.sync() }.status)
        assertTrue(server.copies.isEmpty())
    }

    @Test
    fun deletedCopyNeverDeletesLocalData() = runTest {
        val (a, b) = pair()
        a.mark(run.date, run.sport)
        a.syncer.sync(create = true)
        b.syncer.sync()
        a.syncer.deleteRemote()
        assertFalse(a.syncer.active)
        assertEquals(404, assertFailsWith<SyncException> { b.syncer.sync() }.status)
        assertEquals(setOf(run), b.done)
    }

    @Test
    fun olderCopyThanLastSeenIsRejected() = runTest {
        val (a, _) = pair()
        a.syncer.sync(create = true)
        a.mark(run.date, run.sport)
        a.syncer.sync()  // versión 2
        server.copies[a.keys.bearer] = 1L to server.copies[a.keys.bearer]!!.second  // el servidor vuelve atrás
        assertEquals(409, assertFailsWith<SyncException> { a.syncer.sync() }.status)
        assertEquals(setOf(run), a.done)
    }

    @Test
    fun intervalsCopyStaysLocal() = runTest {
        val (a, _) = pair()
        val cache = IntervalsCache(JsonObject(emptyMap()), emptyList(), emptyList(), "2026-11-02T08:00")
        a.app.replaceData(a.app.data.copy(cache = cache))
        a.syncer.sync(create = true)
        val (version, blob) = server.copies.getValue(a.keys.bearer)
        val uploaded = Json { ignoreUnknownKeys = true }.decodeFromString<AppData>(open(a.keys, blob, version).decodeToString())
        assertNull(uploaded.cache)
        assertEquals(cache, a.app.data.cache)
    }

    @Test
    fun deleteAllStopsSyncingSoItIsNotPropagated() = runTest {
        val (a, _) = pair()
        a.mark(run.date, run.sport)
        a.syncer.sync(create = true)
        a.app.deleteAll()
        assertFalse(a.syncer.active)
    }
}
