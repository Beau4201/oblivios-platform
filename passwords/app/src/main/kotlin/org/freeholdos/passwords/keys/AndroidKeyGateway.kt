// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.keys

import android.security.KeyStoreException
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * [KeyGateway] on the Android Keystore. Three AES or HMAC keys, each in a StrongBox when the device has one:
 *  - `pm_hw_secret`: HMAC-SHA256, no authentication, usable only while the device is unlocked. Its output over a fixed
 *    label is the hardware secret; the key itself cannot leave the secure hardware.
 *  - `pm_index_key`: the same kind, a separate key, for the domain index.
 *  - `pm_bio_key`: AES-256-GCM, a fresh strong-biometric authentication for every use, invalidated by a change of the
 *    enrolled biometrics, never usable with the screen-lock credential.
 */
class AndroidKeyGateway : KeyGateway {
    private val store: KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    override fun hasHardwareSecret(): Boolean = store.containsAlias(HW_ALIAS)

    override fun hardwareSecret(): ByteArray? = if (store.containsAlias(HW_ALIAS)) hmac(HW_ALIAS, HW_LABEL) else null

    override fun createHardwareSecret(): ByteArray {
        if (!store.containsAlias(HW_ALIAS)) generateHmacKey(HW_ALIAS)
        return hmac(HW_ALIAS, HW_LABEL)
    }

    override fun indexKey(): ByteArray {
        if (!store.containsAlias(INDEX_ALIAS)) generateHmacKey(INDEX_ALIAS)
        return hmac(INDEX_ALIAS, INDEX_LABEL)
    }

    override fun biometricCipherForWrap(): BiometricCipher {
        if (!store.containsAlias(BIO_ALIAS)) generateBiometricKey()
        return BiometricCipher(Cipher.getInstance(AES_GCM).apply { init(Cipher.ENCRYPT_MODE, key(BIO_ALIAS)) })
    }

    override fun finishWrap(cipher: BiometricCipher, vaultKey: ByteArray): ByteArray =
        cipher.cipher.iv + cipher.cipher.doFinal(vaultKey)

    override fun biometricCipherForUnwrap(wrapped: ByteArray): BiometricCipher {
        if (!store.containsAlias(BIO_ALIAS) || wrapped.size <= IV_BYTES) throw BiometricKeyInvalidated()
        return try {
            val c = Cipher.getInstance(AES_GCM)
            c.init(Cipher.DECRYPT_MODE, key(BIO_ALIAS), GCMParameterSpec(TAG_BITS, wrapped, 0, IV_BYTES))
            BiometricCipher(c)
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw BiometricKeyInvalidated()
        }
    }

    override fun finishUnwrap(cipher: BiometricCipher, wrapped: ByteArray): ByteArray =
        cipher.cipher.doFinal(wrapped, IV_BYTES, wrapped.size - IV_BYTES)

    override fun biometricsStillValid(): Boolean {
        if (!store.containsAlias(BIO_ALIAS)) return false
        return try {
            // Initialising a cipher with an invalidated key throws before any authentication is asked for.
            Cipher.getInstance(AES_GCM).init(Cipher.DECRYPT_MODE, key(BIO_ALIAS), GCMParameterSpec(TAG_BITS, ByteArray(IV_BYTES)))
            true
        } catch (e: KeyPermanentlyInvalidatedException) {
            false
        } catch (e: InvalidKeyException) {
            // A locked device cannot answer (the key needs it unlocked; Keystore reports DEVICE_LOCKED as this public code):
            // the key is not invalid, so it is neither dropped nor called changed. Any other failure is not hidden.
            if ((e.cause as? KeyStoreException)?.numericErrorCode == KeyStoreException.ERROR_USER_AUTHENTICATION_REQUIRED) true else throw e
        }
    }

    override fun strongBoxBacked(): Boolean {
        if (!store.containsAlias(HW_ALIAS)) return false
        val info = SecretKeyFactory.getInstance(key(HW_ALIAS).algorithm, PROVIDER).getKeySpec(key(HW_ALIAS), KeyInfo::class.java) as KeyInfo
        return info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
    }

    override fun deleteBiometricKey() {
        if (store.containsAlias(BIO_ALIAS)) store.deleteEntry(BIO_ALIAS)
    }

    private fun key(alias: String): SecretKey = store.getKey(alias, null) as SecretKey

    private fun hmac(alias: String, label: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(key(alias)) }.doFinal(label)

    /** StrongBox first; the TEE when the device has no StrongBox. */
    private fun generateWith(algorithm: String, build: (Boolean) -> KeyGenParameterSpec) {
        for (strongBox in booleanArrayOf(true, false)) {
            try {
                KeyGenerator.getInstance(algorithm, PROVIDER).apply { init(build(strongBox)) }.generateKey()
                return
            } catch (e: StrongBoxUnavailableException) {
                if (!strongBox) throw e
            }
        }
    }

    private fun generateHmacKey(alias: String) = generateWith(KeyProperties.KEY_ALGORITHM_HMAC_SHA256) { strongBox ->
        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setUserAuthenticationRequired(false)
            .setUnlockedDeviceRequired(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
    }

    private fun generateBiometricKey() = generateWith(KeyProperties.KEY_ALGORITHM_AES) { strongBox ->
        KeyGenParameterSpec.Builder(BIO_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .setUnlockedDeviceRequired(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val AES_GCM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val HW_ALIAS = "pm_hw_secret"
        const val INDEX_ALIAS = "pm_index_key"
        const val BIO_ALIAS = "pm_bio_key"
        val HW_LABEL = "freeholdos-passwords/hardware-secret/v1".toByteArray()
        val INDEX_LABEL = "freeholdos-passwords/index-key/v1".toByteArray()
    }
}
