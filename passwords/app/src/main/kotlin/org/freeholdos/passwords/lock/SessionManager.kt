// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.lock

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import org.freeholdos.passwords.R
import org.freeholdos.passwords.core.Code
import org.freeholdos.passwords.core.CreatedVault
import org.freeholdos.passwords.core.Reply
import org.freeholdos.passwords.core.VaultCore
import org.freeholdos.passwords.core.handleOrCode
import org.freeholdos.passwords.keys.BiometricCipher
import org.freeholdos.passwords.keys.BiometricKeyInvalidated
import org.freeholdos.passwords.keys.KeyGateway

sealed interface PassphraseOutcome {
    data object Opened : PassphraseOutcome

    /** Wrong passphrase; the next attempt is allowed in [waitMs]. */
    data class Wrong(val waitMs: Long) : PassphraseOutcome

    /** Too soon after the last wrong attempt. */
    data class Throttled(val waitMs: Long) : PassphraseOutcome
    data object SecretLost : PassphraseOutcome
    data class Failed(val code: Int) : PassphraseOutcome
}

sealed interface BiometricOutcome {
    data object Opened : BiometricOutcome
    data object UsePassphrase : BiometricOutcome
    data object Cancelled : BiometricOutcome
    data object Invalidated : BiometricOutcome
    data object NoWrapper : BiometricOutcome
    data class Failed(val code: Int) : BiometricOutcome
}

/**
 * The one session of the app process, shared by the screens and the autofill service (which runs in this process).
 * It owns the handle of the core, applies [LockPolicy], and is the only place that touches the biometric prompt. The
 * core never sees a Keystore key, and this class never sees a password except as the arrays it wipes.
 */
class SessionManager(
    private val app: Context,
    private val files: VaultFiles,
    private val gateway: KeyGateway,
    private val policy: LockPolicy,
) {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(LockState) -> Unit>()
    private var startedActivities = 0

    @Volatile
    var state: LockState = policy.stateAtStart()
        private set

    val handle: Long? get() = (state as? LockState.Unlocked)?.handle

    fun hasVault(): Boolean = files.hasVault()
    fun biometricsEnabled(): Boolean = files.hasBiometricWrapper() && gateway.biometricsStillValid()

    fun observe(listener: (LockState) -> Unit) { listeners.add(listener) }
    fun stopObserving(listener: (LockState) -> Unit) { listeners.remove(listener) }

    private fun publish(s: LockState) {
        state = s
        main.post { listeners.forEach { it(s) } }
    }

    // --- locking ---

    private fun lockTo(next: LockState) {
        (state as? LockState.Unlocked)?.let { VaultCore.lock(it.handle) }
        publish(next)
    }

    fun lockNow() = lockTo(policy.onDeviceLocked())

    fun onScreenOff() = lockTo(policy.onDeviceLocked())

    fun onActivityStarted() {
        startedActivities++
        if (startedActivities == 1) policy.onForegrounded()?.let { if (state is LockState.Unlocked) lockTo(it) }
    }

    fun onActivityStopped() {
        startedActivities = maxOf(0, startedActivities - 1)
        if (startedActivities == 0) {
            policy.onBackgrounded()
            scheduleTick()
        }
    }

    private val tick = Runnable { tickNow() }

    private fun scheduleTick() {
        main.removeCallbacks(tick)
        if (state is LockState.Unlocked) main.postDelayed(tick, TICK_MS)
    }

    private fun tickNow() {
        if (state !is LockState.Unlocked) return
        val next = policy.onTick()
        if (next != null) lockTo(next) else scheduleTick()
    }

    // --- creating and opening with the passphrase ---

    /** Makes a new vault, writes it and opens it. Returns a [Code]. */
    fun createVault(passphrase: CharArray): Int {
        val hw = gateway.createHardwareSecret()
        val pass = utf8(passphrase)
        val reply = Reply.of(VaultCore.create(pass, hw))
        pass.fill(0)
        hw.fill(0)
        if (!reply.ok) return reply.status
        val created = CreatedVault.parse(reply.payload()) ?: return Code.INTERNAL
        try {
            if (!files.writeVault(created.file)) return Code.IO
            val h = handleOrCode(VaultCore.unlockWithVaultKey(created.file, created.vaultKey))
            if (h.code != Code.OK) return h.code
            policy.onUnlocked(viaPassphrase = true, inForeground = startedActivities > 0)
            publish(LockState.Unlocked(h.handle))
            scheduleTick()
            return Code.OK
        } finally {
            created.vaultKey.fill(0)
        }
    }

    fun unlockWithPassphrase(passphrase: CharArray): PassphraseOutcome {
        val wait = policy.passphraseWaitMs()
        if (wait > 0) return PassphraseOutcome.Throttled(wait)
        val hw = gateway.hardwareSecret()
        if (hw == null) {
            publish(LockState.SecretLost)
            return PassphraseOutcome.SecretLost
        }
        val file = files.readVault()
        if (file == null) {
            hw.fill(0)
            return PassphraseOutcome.Failed(Code.IO)
        }
        val pass = utf8(passphrase)
        val r = handleOrCode(VaultCore.unlockWithPassphrase(file, pass, hw))
        pass.fill(0)
        hw.fill(0)
        return when (r.code) {
            Code.OK -> {
                policy.onUnlocked(viaPassphrase = true, inForeground = startedActivities > 0)
                dropInvalidatedBiometrics()
                publish(LockState.Unlocked(r.handle))
                scheduleTick()
                PassphraseOutcome.Opened
            }
            Code.BAD_CREDENTIALS -> {
                policy.onPassphraseFailed()
                PassphraseOutcome.Wrong(policy.passphraseWaitMs())
            }
            else -> PassphraseOutcome.Failed(r.code)
        }
    }

    /** Checks a passphrase without changing the state (to confirm the user before a password is shown). */
    fun verifyPassphrase(passphrase: CharArray): PassphraseOutcome {
        val wait = policy.passphraseWaitMs()
        if (wait > 0) return PassphraseOutcome.Throttled(wait)
        val hw = gateway.hardwareSecret() ?: return PassphraseOutcome.SecretLost
        val file = files.readVault()
        if (file == null) {
            hw.fill(0)
            return PassphraseOutcome.Failed(Code.IO)
        }
        val pass = utf8(passphrase)
        val r = handleOrCode(VaultCore.unlockWithPassphrase(file, pass, hw))
        pass.fill(0)
        hw.fill(0)
        return when (r.code) {
            Code.OK -> {
                VaultCore.lock(r.handle)
                PassphraseOutcome.Opened
            }
            Code.BAD_CREDENTIALS -> {
                policy.onPassphraseFailed()
                PassphraseOutcome.Wrong(policy.passphraseWaitMs())
            }
            else -> PassphraseOutcome.Failed(r.code)
        }
    }

    /** A fresh strong-biometric authentication, no key involved: the eye of the detail screen asks for it. */
    fun confirmWithBiometrics(activity: Activity, done: (Boolean) -> Unit) {
        val finished = AtomicBoolean(false)
        fun finish(ok: Boolean) { if (finished.compareAndSet(false, true)) done(ok) }
        val p = prompt(activity, false) { finish(false) }
        p.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = finish(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = finish(false)
        })
    }

    /**
     * Deletes a vault that cannot be opened (its hardware secret is gone) so that a new one can be made. Only on the
     * user's request: this is the one place that removes a vault file.
     */
    fun deleteUnopenableVault() {
        if (state != LockState.SecretLost) return
        files.deleteAll()
        gateway.deleteBiometricKey()
        policy.forget()
        publish(policy.stateAtStart())
    }

    /** After a passphrase unlock a biometric key that the system invalidated is removed, so the switch can be turned on again. */
    private fun dropInvalidatedBiometrics() {
        if (files.hasBiometricWrapper() && !gateway.biometricsStillValid()) disableBiometrics()
    }

    // --- biometrics ---

    private fun prompt(activity: Activity, forFill: Boolean, onUsePassphrase: () -> Unit): BiometricPrompt =
        BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.pm_prompt_title))
            .setSubtitle(activity.getString(if (forFill) R.string.pm_prompt_subtitle_fill else R.string.pm_prompt_subtitle_open))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButton(activity.getString(R.string.pm_use_passphrase), activity.mainExecutor) { _, _ -> onUsePassphrase() }
            .build()

    /** Opens the vault with a fresh strong-biometric authentication. [forFill] chooses the wording of the prompt. */
    fun biometricUnlock(activity: Activity, forFill: Boolean, done: (BiometricOutcome) -> Unit) {
        val wrapped = files.readBiometricWrapper() ?: return done(BiometricOutcome.NoWrapper)
        val file = files.readVault() ?: return done(BiometricOutcome.Failed(Code.IO))
        val cipher = try {
            gateway.biometricCipherForUnwrap(wrapped)
        } catch (e: BiometricKeyInvalidated) {
            publish(policy.stateAtStart())
            return done(BiometricOutcome.Invalidated)
        }
        val finished = AtomicBoolean(false)
        val cancel = CancellationSignal()
        fun finish(o: BiometricOutcome) { if (finished.compareAndSet(false, true)) done(o) }
        val p = prompt(activity, forFill) { finish(BiometricOutcome.UsePassphrase) }
        p.authenticate(BiometricPrompt.CryptoObject(cipher.cipher), cancel, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher ?: return finish(BiometricOutcome.Failed(Code.INTERNAL))
                val vk = try {
                    gateway.finishUnwrap(BiometricCipher(c), wrapped)
                } catch (e: GeneralSecurityException) {
                    return finish(BiometricOutcome.Failed(Code.CORRUPT))
                }
                val h = handleOrCode(VaultCore.unlockWithVaultKey(file, vk))
                vk.fill(0)
                if (h.code != Code.OK) return finish(BiometricOutcome.Failed(h.code))
                policy.onUnlocked(viaPassphrase = false, inForeground = startedActivities > 0)
                publish(LockState.Unlocked(h.handle))
                scheduleTick()
                finish(BiometricOutcome.Opened)
            }

            override fun onAuthenticationFailed() {
                policy.onBiometricFailed()?.let {
                    publish(it)
                    cancel.cancel()
                    finish(BiometricOutcome.UsePassphrase)
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                when (errorCode) {
                    BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT, BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> finish(BiometricOutcome.UsePassphrase)
                    else -> finish(BiometricOutcome.Cancelled)
                }
            }
        })
    }

    /** Switches biometric unlock on: one prompt, which wraps the vault key under the biometric Keystore key. */
    fun enableBiometrics(activity: Activity, done: (Boolean) -> Unit) {
        val h = handle ?: return done(false)
        val cipher = gateway.biometricCipherForWrap()
        val finished = AtomicBoolean(false)
        fun finish(ok: Boolean) { if (finished.compareAndSet(false, true)) done(ok) }
        val p = prompt(activity, false) { finish(false) }
        p.authenticate(BiometricPrompt.CryptoObject(cipher.cipher), CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher ?: return finish(false)
                val reply = Reply.of(VaultCore.vaultKey(h))
                if (!reply.ok) return finish(false)
                val vk = reply.payload()
                val wrapped = try {
                    gateway.finishWrap(BiometricCipher(c), vk)
                } catch (e: GeneralSecurityException) {
                    return finish(false)
                } finally {
                    vk.fill(0)
                }
                finish(files.writeBiometricWrapper(wrapped))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = finish(false)
        })
    }

    fun disableBiometrics() {
        files.deleteBiometricWrapper()
        gateway.deleteBiometricKey()
        if (state !is LockState.Unlocked) publish(policy.stateAtStart())
    }

    private fun utf8(chars: CharArray): ByteArray {
        val buf = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        val out = ByteArray(buf.remaining())
        buf.get(out)
        if (buf.hasArray()) buf.array().fill(0)
        return out
    }

    private companion object {
        const val TICK_MS = 5_000L
    }
}
