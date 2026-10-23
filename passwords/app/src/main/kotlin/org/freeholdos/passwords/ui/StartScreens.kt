// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import android.hardware.biometrics.BiometricManager
import org.freeholdos.passwords.R
import org.freeholdos.passwords.lock.LockState
import org.freeholdos.passwords.lock.Reason

@Composable
fun FirstStartScreen(c: AppController) {
    Column(Modifier.fillMaxSize().background(pw.bg).statusBarPadding().navigationBarPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 48.dp)) {
        AppLogoTile(56.dp)
        Spacer(Modifier.height(24.dp))
        Title(stringResource(R.string.pm_first_title))
        Spacer(Modifier.height(16.dp))
        Body(stringResource(R.string.pm_first_body_1))
        Spacer(Modifier.height(8.dp))
        Body(stringResource(R.string.pm_first_body_2))
        Spacer(Modifier.height(32.dp))
        Group {
            item { PrefRow(stringResource(R.string.pm_first_create), icon = R.drawable.ic_add, onClick = { c.go(Route.Create) }) }
            item { PrefRow(stringResource(R.string.pm_first_import), icon = R.drawable.ic_download, onClick = { c.importAfterCreate = true; c.go(Route.Create) }) }
        }
    }
}

@Composable
fun CreateScreen(c: AppController) {
    val pass = remember { TextFieldState() }
    val repeat = remember { TextFieldState() }
    var show by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val canBiometrics = remember {
        c.activity.getSystemService(BiometricManager::class.java).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
    var biometrics by remember { mutableStateOf(canBiometrics) }
    val strength = remember(pass.text) { val ch = pass.chars(); c.strengthOf(ch).also { ch.fill('\u0000') } }
    val tooWeak = stringResource(R.string.pm_create_weak)
    val mismatch = stringResource(R.string.pm_create_mismatch)
    Column(Modifier.fillMaxSize().background(pw.bg)) {
        PwTopBar(null, onBack = { pass.clearText(); repeat.clearText(); c.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Title(stringResource(R.string.pm_create_title))
            Spacer(Modifier.height(16.dp))
            Body(stringResource(R.string.pm_create_body))
            Spacer(Modifier.height(24.dp))
            PwSecretField(pass, stringResource(R.string.pm_passphrase), visible = show, isError = error != null,
                trailing = { IconAction(if (show) R.drawable.ic_visibility_off else R.drawable.ic_visibility, stringResource(if (show) R.string.pm_cd_hide else R.string.pm_cd_show), { show = !show }) })
            Spacer(Modifier.height(8.dp))
            StrengthBars(strength, Modifier.padding(start = 16.dp))
            Spacer(Modifier.height(16.dp))
            PwSecretField(repeat, stringResource(R.string.pm_create_repeat), visible = show, supporting = error, isError = error != null)
            if (canBiometrics) {
                Spacer(Modifier.height(24.dp))
                Group { item { PrefRow(stringResource(R.string.pm_settings_biometrics), summary = stringResource(R.string.pm_settings_biometrics_sub), trailing = { PwSwitch(biometrics) { biometrics = it } }) } }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp).navigationBarPadding(), horizontalArrangement = Arrangement.End) {
            PwButton(stringResource(R.string.pm_create_button), onClick = {
                val a = pass.chars()
                val b = repeat.chars()
                when {
                    !a.contentEquals(b) -> error = mismatch
                    a.size < 8 || strength < 1 -> error = tooWeak
                    else -> {
                        error = null
                        if (c.createVault(a, biometrics && canBiometrics)) {
                            c.top(Route.Passwords)
                            if (c.importAfterCreate) {
                                c.importAfterCreate = false
                                c.pickImportSource?.invoke()
                            }
                        }
                    }
                }
                a.fill('\u0000')
                b.fill('\u0000')
                pass.clearText()
                repeat.clearText()
            })
        }
    }
}

@Composable
fun LockedScreen(c: AppController) {
    val state = c.lock
    val pass = remember { TextFieldState() }
    var show by remember { mutableStateOf(false) }
    var usePassphrase by remember(state) { mutableStateOf(state is LockState.NeedsPassphrase || !c.hasBiometrics()) }
    LaunchedEffect(state) { c.autoPromptIfNeeded() }
    val body = when ((state as? LockState.NeedsPassphrase)?.reason) {
        Reason.Restart -> R.string.pm_reason_restart
        Reason.BiometricsChanged -> R.string.pm_reason_biometrics
        Reason.FiveFailures -> R.string.pm_reason_failures
        Reason.Expired -> R.string.pm_reason_expired
        null -> R.string.pm_locked_body
    }
    Column(Modifier.fillMaxSize().background(pw.bg).statusBarPadding().navigationBarPadding().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(64.dp))
        AppLogoTile(88.dp)
        Spacer(Modifier.height(24.dp))
        Title(stringResource(R.string.pm_locked_title))
        Spacer(Modifier.height(8.dp))
        Body(stringResource(body))
        Spacer(Modifier.height(24.dp))
        if (usePassphrase) {
            PwSecretField(pass, stringResource(R.string.pm_passphrase), visible = show, isError = c.lockedError != null, supporting = c.lockedError,
                trailing = { IconAction(if (show) R.drawable.ic_visibility_off else R.drawable.ic_visibility, stringResource(if (show) R.string.pm_cd_hide else R.string.pm_cd_show), { show = !show }) })
            Spacer(Modifier.height(16.dp))
            PwButton(stringResource(R.string.pm_unlock), icon = R.drawable.ic_lock_open, onClick = {
                c.unlockWithPassphrase(pass.chars())
                pass.clearText()
            })
            if (c.hasBiometrics() && state == LockState.Locked) PwTextButton(stringResource(R.string.pm_prompt_title), onClick = { usePassphrase = false; c.unlockWithBiometrics() })
        } else {
            PwButton(stringResource(R.string.pm_unlock), icon = R.drawable.ic_fingerprint, onClick = { c.unlockWithBiometrics() })
            PwTextButton(stringResource(R.string.pm_use_passphrase), onClick = { usePassphrase = true })
        }
    }
}

@Composable
fun SecretLostScreen(c: AppController) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(pw.bg).statusBarPadding().navigationBarPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(64.dp))
        AppLogoTile(56.dp)
        Spacer(Modifier.height(24.dp))
        Title(stringResource(R.string.pm_secret_lost_title))
        Spacer(Modifier.height(16.dp))
        Body(stringResource(R.string.pm_secret_lost_body))
        Spacer(Modifier.height(24.dp))
        PwOutlinedButton(stringResource(R.string.pm_secret_lost_new), onClick = { confirm = true }, icon = R.drawable.ic_delete)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.pm_secret_lost_new)) },
            text = { Text(stringResource(R.string.pm_secret_lost_confirm)) },
            confirmButton = { PwTextButton(stringResource(R.string.pm_delete_confirm_yes), onClick = { confirm = false; c.deleteUnopenableVault() }) },
            dismissButton = { PwTextButton(stringResource(R.string.pm_cancel), onClick = { confirm = false }) },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            containerColor = pw.row,
        )
    }
}
