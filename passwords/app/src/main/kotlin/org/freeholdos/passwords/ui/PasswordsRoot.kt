// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.freeholdos.passwords.lock.LockState

/** The whole app: which screen shows follows the lock state and the route. */
@Composable
fun PasswordsRoot(c: AppController) {
    PasswordsTheme {
        Box(Modifier.fillMaxSize().background(pw.bg)) {
            val lock = c.lock
            when {
                !c.hasVault -> if (c.route == Route.Create) CreateScreen(c) else FirstStartScreen(c)
                lock is LockState.Unlocked -> UnlockedHost(c)
                lock == LockState.SecretLost -> SecretLostScreen(c)
                else -> LockedScreen(c)
            }
            Toast(c.toast, Modifier.align(Alignment.BottomCenter))
        }
        BackHandler(enabled = c.route != Route.Passwords || c.lock !is LockState.Unlocked) { if (!c.back()) c.activity.finish() }
    }
}

@Composable
private fun UnlockedHost(c: AppController) {
    when (val r = c.route) {
        Route.Passwords -> PasswordsScreen(c)
        Route.Search -> SearchScreen(c)
        is Route.Detail -> DetailScreen(c, r.id)
        is Route.Edit -> EditScreen(c, r.id)
        Route.Checkup -> CheckupScreen(c)
        Route.Settings -> SettingsScreen(c)
        Route.Export -> ExportScreen(c)
        Route.Create -> PasswordsScreen(c)
    }
}
