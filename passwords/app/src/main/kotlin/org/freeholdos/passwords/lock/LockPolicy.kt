// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.lock

/** Wall clock for ages that must survive a restart, monotonic clock for the background timer. */
interface Clock {
    fun nowMs(): Long
    fun elapsedMs(): Long
}

/** What the policy remembers between runs of the process (the app keeps it in its own private preferences). */
interface PolicyStore {
    /** Wall-clock time of the last unlock with the passphrase; 0 when there never was one. */
    var lastPassphraseMs: Long

    /** Boot count of the phone at the last passphrase unlock; -1 when there never was one. */
    var bootCount: Int
    var biometricFailures: Int
    var passphraseFailures: Int
    var lastPassphraseFailureMs: Long
}

/** What the policy asks the phone. */
interface Environment {
    fun bootCount(): Int
    fun hardwareSecretPresent(): Boolean
    fun biometricsValid(): Boolean
}

const val BACKGROUND_LOCK_MS = 60_000L
const val PASSPHRASE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
const val MAX_BIOMETRIC_FAILURES = 5
const val MAX_THROTTLE_MS = 300_000L

/**
 * How long to wait after the n-th consecutive wrong passphrase: nothing for three, then 1 s, 2 s, 4 s ... up to five
 * minutes. On top of the Argon2id cost; the vault is never wiped, whatever n is.
 */
fun passphraseThrottleMs(consecutiveFailures: Int): Long {
    if (consecutiveFailures <= 3) return 0L
    return minOf(MAX_THROTTLE_MS, 1_000L shl minOf(consecutiveFailures - 4, 20))
}

/**
 * When the vault locks and when it needs the passphrase instead of a fingerprint. Pure logic; the Android side
 * (receivers, the biometric prompt) is SessionManager.
 *
 * Priority of the reasons when several hold: a lost hardware secret, a restart, changed biometrics, five failed
 * biometric attempts, the 7 days. A clock set back never extends the 7 days.
 */
class LockPolicy(private val clock: Clock, private val store: PolicyStore, private val env: Environment) {
    private var backgroundedAtMs = -1L

    /** The locked state the vault is in now: what a fresh process shows, and what every lock lands on. */
    fun stateAtStart(): LockState {
        if (!env.hardwareSecretPresent()) return LockState.SecretLost
        if (store.bootCount != env.bootCount()) return LockState.NeedsPassphrase(Reason.Restart)
        if (!env.biometricsValid()) return LockState.NeedsPassphrase(Reason.BiometricsChanged)
        if (store.biometricFailures >= MAX_BIOMETRIC_FAILURES) return LockState.NeedsPassphrase(Reason.FiveFailures)
        val age = clock.nowMs() - store.lastPassphraseMs
        if (store.lastPassphraseMs == 0L || age < 0 || age >= PASSPHRASE_MAX_AGE_MS) return LockState.NeedsPassphrase(Reason.Expired)
        return LockState.Locked
    }

    /** A strong-biometric attempt failed. Returns the new state when this was the fifth, otherwise null. */
    fun onBiometricFailed(): LockState? {
        store.biometricFailures++
        return if (store.biometricFailures >= MAX_BIOMETRIC_FAILURES) stateAtStart() else null
    }

    /**
     * The vault opened. Only a passphrase unlock renews the 7 days and the boot count. When the app is not in front
     * (the autofill service opened it), the minute of background time starts now.
     */
    fun onUnlocked(viaPassphrase: Boolean, inForeground: Boolean = true) {
        backgroundedAtMs = if (inForeground) -1L else clock.elapsedMs()
        store.biometricFailures = 0
        if (viaPassphrase) {
            store.lastPassphraseMs = clock.nowMs()
            store.bootCount = env.bootCount()
            store.passphraseFailures = 0
            store.lastPassphraseFailureMs = 0L
        }
    }

    fun onPassphraseFailed() {
        store.passphraseFailures++
        store.lastPassphraseFailureMs = clock.nowMs()
    }

    /** Milliseconds until another passphrase attempt is allowed; 0 when it is allowed now. */
    fun passphraseWaitMs(): Long {
        val delay = passphraseThrottleMs(store.passphraseFailures)
        if (delay == 0L) return 0L
        val since = clock.nowMs() - store.lastPassphraseFailureMs
        return if (since < 0) delay else maxOf(0L, delay - since)
    }

    fun onBackgrounded() {
        backgroundedAtMs = clock.elapsedMs()
    }

    /** Back in the foreground: the new state when the minute had passed, otherwise null. */
    fun onForegrounded(): LockState? {
        val due = backgroundDue()
        backgroundedAtMs = -1L
        return if (due) stateAtStart() else null
    }

    /** Called now and then while unlocked: the new state when the app has been in the background for a minute. */
    fun onTick(): LockState? {
        if (!backgroundDue()) return null
        backgroundedAtMs = -1L
        return stateAtStart()
    }

    fun onDeviceLocked(): LockState {
        backgroundedAtMs = -1L
        return stateAtStart()
    }

    private fun backgroundDue() = backgroundedAtMs >= 0 && clock.elapsedMs() - backgroundedAtMs >= BACKGROUND_LOCK_MS
}
