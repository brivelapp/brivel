package io.github.mrclusterman.trainsync.sync

// Cifrado de extremo a extremo de la copia del servidor (docs/SINCRONIZACION.md, Cifrado y Auditoría): el servidor guarda
// bloques que no puede leer, ni cambiar por los de otra cuenta o por uno viejo sin que el dispositivo lo note.

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlin.io.encoding.Base64

private const val FORMAT: Byte = 1  // primer byte del bloque: permite cambiar el cifrado más adelante
private const val PAD = 4096  // relleno a múltiplos de 4 KB: el tamaño no revela cuánto se entrena

/** Lo que sale del código: la clave AES-256-GCM (nunca sale del dispositivo), el token de acceso y la cuenta. */
class SyncKeys internal constructor(internal val key: AES.GCM.Key, val token: ByteArray, val account: String) {
    /** `Authorization: Bearer …`: el token en base64url sin relleno (43 caracteres), como lo lee server/src/index.ts. */
    val bearer: String get() = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(token)
}

/** HKDF-SHA256 con etiquetas distintas para la clave y el token; la cuenta es SHA-256(token) en hexadecimal. */
suspend fun deriveKeys(code: SyncCode): SyncKeys {
    val provider = CryptographyProvider.Default
    // Sal de ceros, la que fija RFC 5869 si no hay sal: el secreto ya tiene 128 bits de entropía
    suspend fun hkdf(label: String) = provider.get(HKDF)
        .secretDerivation(SHA256, 32.bytes, salt = ByteArray(32), info = label.encodeToByteArray())
        .deriveSecretToByteArray(code.secret)
    val token = hkdf("brivel-sync-token-v1")
    val key = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, hkdf("brivel-sync-key-v1"))
    return SyncKeys(key, token, provider.get(SHA256).hasher().hash(token).toHexString())
}

/** Cifra `plain` para subirlo como `version`. El nonce de 96 bits es aleatorio en cada llamada. */
suspend fun seal(keys: SyncKeys, plain: ByteArray, version: Long): ByteArray =
    byteArrayOf(FORMAT) + keys.key.cipher().encrypt(pad(plain), associatedData(keys, version))

/** Descifra un bloque bajado con la versión de su ETag. Falla si lo cambiaron, es de otra cuenta o de otra versión. */
suspend fun open(keys: SyncKeys, blob: ByteArray, version: Long): ByteArray {
    require(blob.isNotEmpty() && blob[0] == FORMAT) { "Esta copia tiene un formato más nuevo: actualiza Brivel" }
    return unpad(keys.key.cipher().decrypt(blob.copyOfRange(1, blob.size), associatedData(keys, version)))
}

private fun associatedData(keys: SyncKeys, version: Long) = "$FORMAT:${keys.account}:$version".encodeToByteArray()

// ISO/IEC 7816-4: un byte 0x80 y ceros hasta el siguiente múltiplo de PAD
internal fun pad(plain: ByteArray): ByteArray =
    plain.copyOf((plain.size / PAD + 1) * PAD).also { it[plain.size] = 0x80.toByte() }

internal fun unpad(padded: ByteArray): ByteArray {
    val end = padded.indexOfLast { it != 0.toByte() }
    check(end >= 0 && padded[end] == 0x80.toByte()) { "Relleno no válido" }
    return padded.copyOf(end)
}
