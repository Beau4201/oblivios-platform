// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.keys

import javax.crypto.Cipher

/** A cipher that the system biometric prompt must authenticate (as a CryptoObject) before it can be used. */
class BiometricCipher(val cipher: Cipher)

/** The biometric key is gone or was invalidated, because the enrolled biometrics changed: only the passphrase is left. */
class BiometricKeyInvalidated : Exception()

/**
 * The keys that live in the Android Keystore and never in the app: the hardware secret that is mixed into the
 * passphrase wrapper, the key of the domain index, and the biometric key that wraps the vault key.
 *
 * Rules the implementations keep:
 *  - nothing is created behind the caller's back: [hardwareSecret] answers `null` when the key is gone, and only
 *    [createHardwareSecret] makes a new one (a new secret cannot open an old vault, so the caller must know);
 *  - the arrays returned are copies that the caller may overwrite;
 *  - the biometric key needs a fresh strong-biometric authentication for every use, wrapping included, and is
 *    invalidated by a change of the enrolled biometrics. The screen-lock credential is never an alternative.
 */
interface KeyGateway {
    /** The 32-byte output of the non-exportable HMAC key, or `null` when that key does not exist (any more). */
    fun hardwareSecret(): ByteArray?

    /** Makes the HMAC key if it does not exist and returns its 32-byte output. Replaces a key that is gone, never a live one. */
    fun createHardwareSecret(): ByteArray

    /** A 32-byte key for the domain index, from a separate Keystore key that needs no authentication. */
    fun indexKey(): ByteArray

    /** Creates the biometric key if needed and returns an encrypting cipher for the biometric prompt. */
    fun biometricCipherForWrap(): BiometricCipher

    /** After the prompt succeeded: `iv || ciphertext` of the vault key. */
    fun finishWrap(cipher: BiometricCipher, vaultKey: ByteArray): ByteArray

    /** A decrypting cipher for the biometric prompt. Throws [BiometricKeyInvalidated] when the key is gone or invalidated. */
    fun biometricCipherForUnwrap(wrapped: ByteArray): BiometricCipher

    /** After the prompt succeeded: the vault key. */
    fun finishUnwrap(cipher: BiometricCipher, wrapped: ByteArray): ByteArray

    /** False when the biometric key is gone or the enrolled biometrics changed since it was made. */
    fun biometricsStillValid(): Boolean

    /** True when the keys sit in a StrongBox. */
    fun strongBoxBacked(): Boolean

    fun deleteBiometricKey()
}
