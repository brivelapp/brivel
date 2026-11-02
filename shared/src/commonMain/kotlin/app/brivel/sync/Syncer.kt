package app.brivel.sync

// Una sincronización (docs/SINCRONIZACION.md): bajar la copia, combinarla a tres bandas con la de este dispositivo y
// subir el resultado si cambió. Si otro dispositivo sube a la vez, el servidor responde 409 y se vuelve a empezar.

import app.brivel.app.AppData
import app.brivel.app.KeyValueStore
import app.brivel.app.SYNC_STATE_KEY
import app.brivel.app.BrivelApp
import app.brivel.app.migrate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Lo último sincronizado: la versión vista (no se acepta una menor) y la copia base para combinar. */
@Serializable
internal data class SyncState(val account: String, val version: Long, val base: AppData)

/** Resultado de una sincronización: la versión guardada y los apartados en los que ganó el otro dispositivo. */
class SyncResult(val version: Long, val conflicts: Set<String>)

class Syncer(
    private val app: BrivelApp,
    private val store: KeyValueStore,
    private val keys: SyncKeys,
    private val client: SyncTransport,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // El estado de otra cuenta (otro código) no vale: se combina como al unirse por primera vez
    private fun state(): SyncState? = store.read(SYNC_STATE_KEY)
        ?.let { runCatching { json.decodeFromString<SyncState>(it) }.getOrNull() }
        ?.takeIf { it.account == keys.account }

    private fun saveState(version: Long, base: AppData) =
        store.write(SYNC_STATE_KEY, json.encodeToString(SyncState(keys.account, version, base)))

    /** Si este dispositivo ya sincronizó con este código. */
    val active: Boolean get() = state() != null

    /**
     * Sincroniza. La primera vez junta los datos con la copia sin borrar nada de ninguno de los dos lados; si no hay
     * copia, la crea solo con `create` (código nuevo): con un código mal tecleado o ya borrado, avisa. Nunca borra los
     * datos de este dispositivo, aunque la copia del servidor ya no exista.
     */
    suspend fun sync(create: Boolean = false): SyncResult {
        repeat(3) {
            val state = state()
            val remote = client.download()
            if (remote == null) {
                if (state != null) throw SyncException(404, "La copia ya no está en el servidor: el código cambió o se borró. Tus datos siguen en este dispositivo.")
                if (!create) throw SyncException(404, "No hay ninguna copia con ese código: revísalo en el otro dispositivo")
                val payload = syncPayload(app.data)
                val version = client.create(seal(keys, encode(payload), 1)) ?: return@repeat  // otro se adelantó
                saveState(version, payload)
                return SyncResult(version, emptySet())
            }
            if (state != null && remote.version < state.version) {
                throw SyncException(409, "El servidor devolvió una copia más vieja que la última que vio este dispositivo")
            }
            val plain = runCatching { open(keys, remote.blob, remote.version) }.getOrElse {
                throw SyncException(0, "No se pudo descifrar la copia del servidor: puede que la hayan alterado. Tus datos siguen aquí.")
            }
            val theirs = migrate(json.decodeFromString<AppData>(plain.decodeToString()))
            val merged = merge(state?.base ?: AppData(), app.data, theirs)
            app.replaceData(merged.data)
            val payload = syncPayload(merged.data)
            if (payload == syncPayload(theirs)) {
                saveState(remote.version, payload)
                return SyncResult(remote.version, merged.conflicts)
            }
            val version = client.update(seal(keys, encode(payload), remote.version + 1), remote.version) ?: return@repeat
            saveState(version, payload)
            return SyncResult(version, merged.conflicts)
        }
        throw SyncException(409, "Otro dispositivo está subiendo cambios a la vez: prueba de nuevo en un momento")
    }

    /** Deja de sincronizar en este dispositivo; la copia del servidor y los datos de aquí se quedan. */
    fun forget() = store.write(SYNC_STATE_KEY, null)

    /** Borra la copia del servidor al momento (RGPD art. 17); los datos de este dispositivo se quedan. */
    suspend fun deleteRemote() {
        client.delete()
        forget()
    }

    private fun encode(data: AppData) = json.encodeToString(data).encodeToByteArray()
}
