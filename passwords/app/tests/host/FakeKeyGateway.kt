// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.hosttest

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.freeholdos.passwords.keys.BiometricCipher
import org.freeholdos.passwords.keys.BiometricKeyInvalidated
import org.freeholdos.passwords.keys.KeyGateway

/**
 * A software stand-in for the Keystore with the same contract: the hardware secret and the index key are stable until
 * lost, the biometric key survives until the enrolment changes, and nothing is ever created behind the caller's back.
 * Not authenticating anything: the biometric prompt is the caller's business.
 */
class FakeKeyGateway : KeyGateway {
    private val random = SecureRandom()
    private var hw: ByteArray? = null
    private var index: ByteArray? = null
    private var bioKey: ByteArray? = null
    private var enrolmentChanged = false
    var strongBox = true

    /** How many times the gateway made a new hardware secret: a test of "never regenerated" reads it. */
    var hardwareSecretsCreated = 0
        private set

    override fun hardwareSecret(): ByteArray? = hw?.copyOf()

    override fun createHardwareSecret(): ByteArray {
        hw = ByteArray(32).also(random::nextBytes)
        hardwareSecretsCreated++
        return hw!!.copyOf()
    }

    override fun indexKey(): ByteArray {
        if (index == null) index = ByteArray(32).also(random::nextBytes)
        return index!!.copyOf()
    }

    private fun aes(mode: Int, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, SecretKeySpec(bioKey!!, "AES"), GCMParameterSpec(128, iv)) }

    override fun biometricCipherForWrap(): BiometricCipher {
        if (bioKey == null || enrolmentChanged) {
            bioKey = ByteArray(32).also(random::nextBytes)
            enrolmentChanged = false
        }
        return BiometricCipher(aes(Cipher.ENCRYPT_MODE, ByteArray(12).also(random::nextBytes)))
    }

    override fun finishWrap(cipher: BiometricCipher, vaultKey: ByteArray): ByteArray =
        cipher.cipher.iv + cipher.cipher.doFinal(vaultKey)

    override fun biometricCipherForUnwrap(wrapped: ByteArray): BiometricCipher {
        if (bioKey == null || enrolmentChanged) throw BiometricKeyInvalidated()
        return BiometricCipher(aes(Cipher.DECRYPT_MODE, wrapped.copyOfRange(0, 12)))
    }

    override fun finishUnwrap(cipher: BiometricCipher, wrapped: ByteArray): ByteArray =
        cipher.cipher.doFinal(wrapped, 12, wrapped.size - 12)

    override fun biometricsStillValid(): Boolean = bioKey != null && !enrolmentChanged

    override fun strongBoxBacked(): Boolean = strongBox

    override fun deleteBiometricKey() {
        bioKey = null
    }

    // --- what the real Keystore does on its own ---
    fun loseHardwareSecret() {
        hw = null
    }

    fun simulateEnrolmentChange() {
        enrolmentChanged = true
    }
}
