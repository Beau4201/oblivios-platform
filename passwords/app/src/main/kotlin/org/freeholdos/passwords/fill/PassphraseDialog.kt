// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.fill

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import org.freeholdos.passwords.R
import org.freeholdos.passwords.lock.Reason

/**
 * The passphrase prompt of the autofill step, in plain framework views (the screens of the app itself are Compose, Task
 * 14). Its window is secure like every window of the app, and it is excluded from autofill so that no service, ours
 * included, offers to fill the passphrase.
 */
object PassphraseDialog {
    /** The sentence that says why only the passphrase opens the vault; the lock screen of the app and this dialog use the same one (PWM-026). */
    fun reasonMessageRes(reason: Reason): Int = when (reason) {
        Reason.Restart -> R.string.pm_reason_restart
        Reason.BiometricsChanged -> R.string.pm_reason_biometrics
        Reason.FiveFailures -> R.string.pm_reason_failures
        Reason.Expired -> R.string.pm_reason_expired
    }

    fun show(
        activity: Activity, forFill: Boolean, error: CharSequence?, onCancel: () -> Unit,
        titleRes: Int = R.string.pm_prompt_title, messageRes: Int? = null, hintRes: Int = R.string.pm_passphrase,
        onSubmit: (CharArray) -> Unit,
    ) {
        val field = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            setHint(hintRes)
            error?.let { setError(it) }
        }
        // The dialog puts a custom view flush against its edges; the 24 dp side inset is the one of the message above it.
        val inset = (24 * activity.resources.displayMetrics.density).toInt()
        val holder = FrameLayout(activity).apply {
            setPadding(inset, inset / 3, inset, 0)
            addView(field)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setMessage(messageRes ?: if (forFill) R.string.pm_prompt_subtitle_fill else R.string.pm_prompt_subtitle_open)
            .setView(holder)
            .setPositiveButton(R.string.pm_unlock) { _, _ ->
                val text = field.text
                val chars = CharArray(text.length) { text[it] }
                text.clear()
                onSubmit(chars)
            }
            .setNegativeButton(R.string.pm_cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .create()
        dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    /** A question with a yes and a no, in a secure window. [onNo] runs for the no button and for every other way out. */
    fun confirm(activity: Activity, titleRes: Int, messageRes: Int, yesRes: Int, onNo: () -> Unit, onYes: () -> Unit) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(yesRes) { _, _ -> onYes() }
            .setNegativeButton(R.string.pm_cancel) { _, _ -> onNo() }
            .setOnCancelListener { onNo() }
            .create()
        dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    /** [confirm] with a message that is built at run time (the site, the user name and the app of a login about to be saved). */
    fun confirmText(activity: Activity, titleRes: Int, message: CharSequence, yesRes: Int, onNo: () -> Unit, onYes: () -> Unit) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(yesRes) { _, _ -> onYes() }
            .setNegativeButton(R.string.pm_cancel) { _, _ -> onNo() }
            .setOnCancelListener { onNo() }
            .create()
        dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    /** The key that protects the vault on this phone is gone: only an import of an export file brings the passwords back. */
    fun showSecretLost(activity: Activity, onClose: () -> Unit) = showMessage(activity, R.string.pm_secret_lost_title, R.string.pm_secret_lost_body, onClose)

    /** A plain message with one button, in a secure window; [onClose] runs when it is dismissed in any way. */
    fun showMessage(activity: Activity, titleRes: Int, messageRes: Int, onClose: () -> Unit) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(R.string.pm_ok) { _, _ -> onClose() }
            .setOnCancelListener { onClose() }
            .create()
        dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }
}
