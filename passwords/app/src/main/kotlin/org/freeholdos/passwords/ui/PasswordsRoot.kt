// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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

/** How deep a route sits: the three tabs, then what opens from them, then the detail, then its edit form. */
private fun depth(r: Route): Int = when (r) {
    Route.Passwords, Route.Checkup, Route.Settings, Route.Create -> 0
    Route.Search, Route.Export -> 1
    is Route.Detail -> 2
    is Route.Edit -> 3
}

/**
 * The screens move the way the system apps do: going deeper slides the new screen in from the right by a short distance
 * while it fades in, going back mirrors it, and the tabs fade through each other with a small scale. The leaving screen
 * fades out first (90 ms), so the two never show at full strength together. A lock replaces this host at once instead,
 * outside of any animation.
 */
@Composable
private fun UnlockedHost(c: AppController) {
    AnimatedContent(
        targetState = c.route,
        transitionSpec = {
            val from = depth(initialState)
            val to = depth(targetState)
            val enter = fadeIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing))
            val exit = fadeOut(tween(90, easing = FastOutLinearInEasing))
            when {
                to > from -> (enter + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 12 }) togetherWith
                    (exit + slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 12 })
                to < from -> (enter + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 12 }) togetherWith
                    (exit + slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 12 })
                else -> (enter + scaleIn(tween(210, delayMillis = 90, easing = FastOutSlowInEasing), initialScale = 0.92f)) togetherWith exit
            }.using(SizeTransform(clip = false))
        },
        label = "route",
    ) { r ->
        when (r) {
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
}
