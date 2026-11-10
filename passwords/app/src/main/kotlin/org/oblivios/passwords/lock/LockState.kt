// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.lock

/** Why only the passphrase can open the vault now. */
enum class Reason { Restart, BiometricsChanged, FiveFailures, Expired }

sealed interface LockState {
    /** Locked; a strong-biometric authentication or the passphrase opens it. */
    data object Locked : LockState

    /** Locked; only the passphrase opens it. */
    data class NeedsPassphrase(val reason: Reason) : LockState

    /**
     * The hardware secret is gone, so even the right passphrase cannot open the vault. The app says so and offers an
     * import of an export file; it never shows an empty vault or creates a new secret by itself.
     */
    data object SecretLost : LockState

    /** Open; [handle] is the session of the Rust core. */
    data class Unlocked(val handle: Long) : LockState
}
