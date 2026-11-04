// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.fill

import org.freeholdos.passwords.R
import org.freeholdos.passwords.SecureActivity
import org.freeholdos.passwords.lock.BiometricOutcome
import org.freeholdos.passwords.lock.LockState
import org.freeholdos.passwords.lock.PassphraseOutcome
import org.freeholdos.passwords.lock.SessionManager

/**
 * Opens the vault for a service that was waiting for it (autofill or the credential provider): the system biometric
 * prompt first, the passphrase when biometrics are off, changed or declined, and a plain message when the hardware
 * secret is gone. Calls [onOpened] with the vault open, or [onCancel] when the user gives up.
 */
object UnlockFlow {
    fun run(activity: SecureActivity, session: SessionManager, forFill: Boolean, onOpened: () -> Unit, onCancel: () -> Unit) {
        session.refreshLocked() // the 7 days may have passed since the vault locked (P-11)
        when (session.state) {
            is LockState.Unlocked -> onOpened()
            LockState.SecretLost -> PassphraseDialog.showSecretLost(activity, onCancel)
            is LockState.NeedsPassphrase -> askPassphrase(activity, session, forFill, null, onOpened, onCancel)
            LockState.Locked ->
                if (session.biometricsEnabled()) {
                    session.biometricUnlock(activity, forFill) { outcome ->
                        when (outcome) {
                            BiometricOutcome.Opened -> onOpened()
                            BiometricOutcome.Cancelled -> onCancel()
                            else -> askPassphrase(activity, session, forFill, null, onOpened, onCancel)
                        }
                    }
                } else {
                    askPassphrase(activity, session, forFill, null, onOpened, onCancel)
                }
        }
    }

    private fun askPassphrase(
        activity: SecureActivity, session: SessionManager, forFill: Boolean, error: CharSequence?,
        onOpened: () -> Unit, onCancel: () -> Unit,
    ) {
        // The state still says why only the passphrase opens the vault (restart, new fingerprint, failures, a week); the dialog names it.
        val reason = (session.state as? LockState.NeedsPassphrase)?.reason
        PassphraseDialog.show(activity, forFill, error, onCancel, messageRes = reason?.let(PassphraseDialog::reasonMessageRes)) { chars ->
            val outcome = session.unlockWithPassphrase(chars)
            chars.fill('\u0000')
            when (outcome) {
                PassphraseOutcome.Opened -> onOpened()
                is PassphraseOutcome.Wrong -> askPassphrase(activity, session, forFill, activity.getString(R.string.pm_passphrase_wrong), onOpened, onCancel)
                is PassphraseOutcome.Throttled -> {
                    val s = ((outcome.waitMs + 999) / 1000).toInt().coerceAtLeast(1)
                    askPassphrase(activity, session, forFill, activity.resources.getQuantityString(R.plurals.pm_passphrase_wait, s, s), onOpened, onCancel)
                }
                PassphraseOutcome.SecretLost -> PassphraseDialog.showSecretLost(activity, onCancel)
                is PassphraseOutcome.Failed -> onCancel()
            }
        }
    }
}
