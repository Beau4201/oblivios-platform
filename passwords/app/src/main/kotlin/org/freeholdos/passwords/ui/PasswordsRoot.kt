// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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

/** The tab a route belongs to, left to right; a move to a tab on the right counts as going forward, to the left as going back. */
private fun tabIndex(r: Route): Int = when (r) {
    Route.Checkup -> 1
    Route.Settings -> 2
    else -> 0
}

/**
 * The framework's `fast_out_extra_slow_in` (`M 0,0 C 0.05,0 0.133333,0.06 0.166666,0.4 C 0.208333,0.82 0.25,1 1,1`
 * [verified-in-source: frameworks/base/core/res/res/interpolator/fast_out_extra_slow_in.xml]): two cubic segments with x as time.
 */
private val FastOutExtraSlowIn = Easing { x ->
    fun bezier(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        val u = 1f - t
        return u * u * u * a + 3f * u * u * t * b + 3f * u * t * t * c + t * t * t * d
    }
    val first = x <= 0.166666f
    val xs = if (first) floatArrayOf(0f, 0.05f, 0.133333f, 0.166666f) else floatArrayOf(0.166666f, 0.208333f, 0.25f, 1f)
    val ys = if (first) floatArrayOf(0f, 0f, 0.06f, 0.4f) else floatArrayOf(0.4f, 0.82f, 1f, 1f)
    var lo = 0f
    var hi = 1f
    repeat(24) {
        val mid = (lo + hi) / 2f
        if (bezier(xs[0], xs[1], xs[2], xs[3], mid) < x) lo = mid else hi = mid
    }
    bezier(ys[0], ys[1], ys[2], ys[3], (lo + hi) / 2f)
}

/**
 * The screens move the way the settings app does, which is the framework's own activity animation
 * [verified-in-source: frameworks/base/core/res/res/anim/activity_open_enter.xml, activity_open_exit.xml, activity_close_enter.xml,
 * activity_close_exit.xml; the Settings tree sets no animation of its own for its pages, SettingsTransitionHelper is empty].
 * Going deeper: the new screen comes in from 96 dp to the right over 450 ms and fades in over 83 ms after 50 ms, while the old
 * one moves 96 dp to the left, fully visible. Going back mirrors it: the screen below comes in from 96 dp to the left, and the
 * leaving one (on top) moves 96 dp to the right and fades out over 83 ms after 35 ms. The edge extension of the window
 * animation is not needed, the screens fill the host. A lock replaces this host at once instead, outside of any
 * animation.
 *
 * Switching between the three tabs is not opening a page, so it does not slide (owner, 2026-10-29: it looked like closing and
 * opening a page). It is the Material "fade through" for destinations of one level that are not related by depth: the leaving
 * screen fades out in 90 ms, then the new one fades in over 210 ms while it grows from 92 to 100 percent; the tab bar stays
 * where it is and only its pill moves. The bar belongs to the host for that reason: a screen reserves the room for it
 * ([PwNavBarSpace]) and the host draws it over that room. It leaves downwards when a page opens from a tab and comes back
 * when the user returns.
 */
@Composable
private fun UnlockedHost(c: AppController) {
    val shift = with(LocalDensity.current) { 96.dp.roundToPx() }
    var lastTab by remember { mutableIntStateOf(0) }
    val tab = if (depth(c.route) == 0) tabIndex(c.route) else lastTab
    SideEffect { lastTab = tab }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = c.route,
            transitionSpec = {
                val from = depth(initialState)
                val to = depth(targetState)
                val slide = tween<IntOffset>(450, easing = FastOutExtraSlowIn)
                when {
                    from == 0 && to == 0 ->
                        (fadeIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing)) +
                            scaleIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing), initialScale = 0.92f)) togetherWith
                            fadeOut(tween(90, easing = LinearEasing))
                    to >= from ->
                        (fadeIn(tween(83, delayMillis = 50, easing = LinearEasing)) + slideInHorizontally(slide) { shift }) togetherWith
                            slideOutHorizontally(slide) { -shift }
                    else ->
                        ((slideInHorizontally(slide) { -shift }) togetherWith
                            (fadeOut(tween(83, delayMillis = 35, easing = LinearEasing)) + slideOutHorizontally(slide) { shift }))
                            .also { it.targetContentZIndex = -1f }
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
        AnimatedVisibility(
            visible = depth(c.route) == 0, modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(150)) + slideInVertically(tween(250, easing = FastOutSlowInEasing)) { it },
            exit = fadeOut(tween(100)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it },
        ) { PwNavBar(tab) { navigate(c, it) } }
    }
}
