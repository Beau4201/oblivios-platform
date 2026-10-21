// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.core

/**
 * The JNI surface of libvault_core.so (freeholdos-services/vault-core/src/jni.rs). check-jni-sync.sh holds the names and
 * the number of parameters of these declarations to the Rust exports.
 *
 * A call that returns bytes returns `[status][payload]`: status 0 is success, any other value a code (1 bad
 * credentials, 2 corrupt, 3 unsupported version, 4 too large, 5 I/O, 6 dead handle, 7 bad argument); null means the JVM
 * could not allocate the answer. A call that returns a handle returns it positive, or `-code`. The core copies every
 * secret it is given; overwriting the arrays afterwards is the caller's job.
 */
object VaultCore {
    init {
        System.loadLibrary("vault_core")
    }

    external fun selfTest(): Boolean

    /** Payload: `u32 file length | file | vault key (32)`. */
    external fun create(passphrase: ByteArray, hwSecret: ByteArray): ByteArray?
    external fun unlockWithPassphrase(file: ByteArray, passphrase: ByteArray, hwSecret: ByteArray): Long
    external fun unlockWithVaultKey(file: ByteArray, vaultKey: ByteArray): Long
    external fun lock(handle: Long): Int

    /** The 32-byte vault key of an open session, for the biometric wrapper only. Overwrite it at once. */
    external fun vaultKey(handle: Long): ByteArray?

    external fun list(handle: Long): ByteArray?
    external fun search(handle: Long, query: String): ByteArray?
    external fun get(handle: Long, id: ByteArray): ByteArray?

    /** Summaries of the accounts of a site (kind 0: a host) or an app (kind 1: a package name), by the exact rules of the index. */
    external fun matches(handle: Long, subject: String, kind: Int): ByteArray?

    /** The calls that change the vault answer with the new file, which the caller writes with [writeFileAtomic]. */
    external fun addOrEdit(handle: Long, entry: ByteArray): ByteArray?
    external fun delete(handle: Long, id: ByteArray): ByteArray?
    external fun markFilled(handle: Long, id: ByteArray, nowMs: Long): ByteArray?

    external fun checkup(handle: Long): ByteArray?
    external fun export(handle: Long, passphrase: ByteArray): ByteArray?

    /** Payload: `u32 entries added | new file`. */
    external fun importEntries(handle: Long, blob: ByteArray, passphrase: ByteArray): ByteArray?

    external fun indexBuild(handle: Long, key: ByteArray): ByteArray?
    external fun indexContains(index: ByteArray, key: ByteArray, subject: String, kind: Int): Boolean

    external fun generate(length: Int, lower: Boolean, upper: Boolean, digits: Boolean, symbols: Boolean): ByteArray?
    external fun strength(password: ByteArray): Int

    external fun writeFileAtomic(path: String, bytes: ByteArray): Int
    external fun readFileBounded(path: String): ByteArray?
}
