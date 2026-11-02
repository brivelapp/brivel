package io.github.mrclusterman.trainsync.sync

// Código de sincronización (docs/SINCRONIZACION.md): 128 bits aleatorios escritos en base32 de Crockford (26 letras)
// más una de control (Luhn mod 32), para que un error al teclearlo se detecte antes de llamar al servidor.

import dev.whyoleg.cryptography.random.CryptographyRandom

private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"  // Crockford: sin I, L, O ni U
private const val SECRET_BYTES = 16

/** El secreto y su forma escrita (27 caracteres). Quien tiene el código tiene los datos: no se guarda en claro. */
class SyncCode private constructor(val secret: ByteArray, val text: String) {
    companion object {
        fun generate(): SyncCode = fromSecret(CryptographyRandom.Default.nextBytes(SECRET_BYTES))

        fun fromSecret(secret: ByteArray): SyncCode {
            require(secret.size == SECRET_BYTES) { "El secreto tiene que ser de $SECRET_BYTES bytes" }
            val body = encode(secret)
            return SyncCode(secret.copyOf(), body + ALPHABET[checkDigit(body)])
        }

        /** Lo que teclea el usuario: da igual mayúsculas, espacios y guiones; O vale 0 e I o L valen 1. Null si no es válido. */
        fun parse(input: String): SyncCode? {
            val text = input.uppercase().filterNot { it == ' ' || it == '-' }
                .replace('O', '0').replace('I', '1').replace('L', '1')
            if (text.length != 27 || text.any { it !in ALPHABET }) return null
            val body = text.dropLast(1)
            if (ALPHABET[checkDigit(body)] != text.last()) return null
            return decode(body)?.let { SyncCode(it, text) }
        }
    }
}

// 2 bits a cero delante de los 128 para llegar a 130, múltiplo de 5
private fun encode(secret: ByteArray): String {
    val bits = "00" + secret.joinToString("") { (it.toInt() and 0xFF).toString(2).padStart(8, '0') }
    return bits.chunked(5).joinToString("") { ALPHABET[it.toInt(2)].toString() }
}

private fun decode(body: String): ByteArray? {
    val bits = body.map { ALPHABET.indexOf(it).toString(2).padStart(5, '0') }.joinToString("")
    if (!bits.startsWith("00")) return null
    return bits.drop(2).chunked(8).map { it.toInt(2).toByte() }.toByteArray()
}

/** Luhn mod N con N = 32: detecta cualquier letra cambiada y casi todos los cambios de orden entre vecinas. */
private fun checkDigit(body: String): Int {
    var factor = 2
    var sum = 0
    for (c in body.reversed()) {
        val addend = factor * ALPHABET.indexOf(c)
        sum += addend / 32 + addend % 32
        factor = 3 - factor
    }
    return (32 - sum % 32) % 32
}
