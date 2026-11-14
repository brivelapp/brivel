package app.brivel.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.brivel.app.KeyValueStore
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Datos de la app en SharedPreferences: solo en el móvil (allowBackup=false en el manifiesto). */
class PrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("brivel", Context.MODE_PRIVATE)
    // Las series de cada actividad propia y la grabación en curso van en ficheros: SharedPreferences reescribe todo su
    // archivo en cada cambio y con decenas de actividades pesaría varios megas
    private val files = File(context.filesDir, "activities").apply { mkdirs() }
    private fun file(key: String) = key.takeIf { it.startsWith(FILE_PREFIX) }?.let { File(files, it.replace(Regex("[^A-Za-z0-9.-]"), "_")) }

    override fun read(key: String): String? {
        val f = file(key) ?: return prefs.getString(key, null)
        return f.takeIf { it.exists() }?.readText()
    }

    override fun write(key: String, value: String?) {
        val f = file(key)
        when {
            f == null -> prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
            value == null -> f.delete()
            else -> f.writeText(value)
        }
    }

    private companion object { const val FILE_PREFIX = "brivel.activity." }
}

/** La clave de intervals.icu, cifrada con AES-GCM con una clave del Android Keystore que nunca sale del hardware. */
class KeystoreStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("brivel.secure", Context.MODE_PRIVATE)
    private val alias = "brivel-secrets"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    override fun read(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            String(cipher.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
        }.getOrNull()  // clave del Keystore perdida (p. ej. tras restaurar el móvil): se vuelve a vincular
    }

    override fun write(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(data, Base64.NO_WRAP)).apply()
    }
}
