// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.lock

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import java.io.File
import org.freeholdos.passwords.core.Reply
import org.freeholdos.passwords.core.VaultCore
import org.freeholdos.passwords.keys.KeyGateway

class AndroidClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun elapsedMs(): Long = SystemClock.elapsedRealtime()
}

/** The policy's memory in the app's own private preferences; written synchronously, so a killed process loses no count. */
class PrefsPolicyStore(private val prefs: SharedPreferences) : PolicyStore {
    override var lastPassphraseMs: Long
        get() = prefs.getLong("last_passphrase_ms", 0L)
        set(v) { prefs.edit().putLong("last_passphrase_ms", v).commit() }
    override var bootCount: Int
        get() = prefs.getInt("boot_count", -1)
        set(v) { prefs.edit().putInt("boot_count", v).commit() }
    override var biometricFailures: Int
        get() = prefs.getInt("biometric_failures", 0)
        set(v) { prefs.edit().putInt("biometric_failures", v).commit() }
    override var passphraseFailures: Int
        get() = prefs.getInt("passphrase_failures", 0)
        set(v) { prefs.edit().putInt("passphrase_failures", v).commit() }
    override var lastPassphraseFailureMs: Long
        get() = prefs.getLong("last_passphrase_failure_ms", 0L)
        set(v) { prefs.edit().putLong("last_passphrase_failure_ms", v).commit() }
}

/** What the phone says. Biometrics that were never switched on are not "changed". */
class AndroidEnvironment(
    private val context: Context,
    private val gateway: KeyGateway,
    private val files: VaultFiles,
) : Environment {
    override fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -2)

    override fun hardwareSecretPresent(): Boolean {
        val s = gateway.hardwareSecret() ?: return false
        s.fill(0)
        return true
    }

    override fun biometricsValid(): Boolean = !files.hasBiometricWrapper() || gateway.biometricsStillValid()
}

/** The two files of the app, in its private (credential-encrypted) storage, written by the core's atomic store. */
class VaultFiles(dir: File) {
    private val vault = File(dir, "vault.fhpf")
    private val bio = File(dir, "vault.bio")
    private val index = File(dir, "domains.idx")

    fun hasVault(): Boolean = vault.isFile
    fun hasBiometricWrapper(): Boolean = bio.isFile

    fun readVault(): ByteArray? = read(vault)
    fun readBiometricWrapper(): ByteArray? = read(bio)
    fun writeVault(bytes: ByteArray): Boolean = VaultCore.writeFileAtomic(vault.path, bytes) == 0
    fun writeBiometricWrapper(bytes: ByteArray): Boolean = VaultCore.writeFileAtomic(bio.path, bytes) == 0
    fun deleteBiometricWrapper() { bio.delete() }

    /** Removes the vault, its biometric wrapper and the index. Only for a vault that can no longer be opened, on request. */
    fun deleteAll() {
        vault.delete()
        bio.delete()
        index.delete()
    }

    private fun read(f: File): ByteArray? {
        if (!f.isFile) return null
        val r = Reply.of(VaultCore.readFileBounded(f.path))
        return if (r.ok) r.payload() else null
    }
}
