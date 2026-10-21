// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.core

/** The numbers the core reports (freeholdos-services/vault-core/src/api.rs, `Code`); they never change. */
object Code {
    const val OK = 0
    const val BAD_CREDENTIALS = 1
    const val CORRUPT = 2
    const val UNSUPPORTED_VERSION = 3
    const val TOO_LARGE = 4
    const val IO = 5
    const val DEAD_HANDLE = 6
    const val BAD_ARGUMENT = 7

    /** Not a code of the core: the answer was missing or empty (the JVM could not allocate it). */
    const val INTERNAL = 255
}

/** An answer of the core: `[status][payload]`. */
class Reply private constructor(val status: Int, private val raw: ByteArray) {
    val ok: Boolean get() = status == Code.OK

    /** The bytes after the status; empty for an error reply, which carries none. */
    fun payload(): ByteArray = if (ok) raw.copyOfRange(1, raw.size) else ByteArray(0)

    companion object {
        fun of(raw: ByteArray?): Reply =
            if (raw == null || raw.isEmpty()) Reply(Code.INTERNAL, ByteArray(0)) else Reply(raw[0].toInt() and 0xff, raw)
    }
}

/** What `create` hands over: the new vault file and the vault key (for the biometric wrapper). */
class CreatedVault(val file: ByteArray, val vaultKey: ByteArray) {
    companion object {
        private const val KEY_BYTES = 32

        /** Payload `u32 file length | file | vault key (32)`; anything else is refused. */
        fun parse(payload: ByteArray): CreatedVault? {
            if (payload.size < 4 + 1 + KEY_BYTES) return null
            val length = (payload[0].toLong() and 0xff) or ((payload[1].toLong() and 0xff) shl 8) or
                ((payload[2].toLong() and 0xff) shl 16) or ((payload[3].toLong() and 0xff) shl 24)
            if (length < 1 || length != (payload.size - 4 - KEY_BYTES).toLong()) return null
            return CreatedVault(payload.copyOfRange(4, 4 + length.toInt()), payload.copyOfRange(4 + length.toInt(), payload.size))
        }
    }
}

/** A handle call answers a positive handle, or `-code`. */
class HandleOrCode(val handle: Long, val code: Int)

fun handleOrCode(value: Long): HandleOrCode = when {
    value > 0 -> HandleOrCode(value, Code.OK)
    value < 0 -> HandleOrCode(0, (-value).toInt())
    else -> HandleOrCode(0, Code.INTERNAL)
}
