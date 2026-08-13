package org.aegisos.guardian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.aegisos.guardian.contracts.GuardianIncidentV1
import org.aegisos.guardianui.screens.GuardianChoicesScreen
import org.aegisos.guardianui.screens.GuardianFirstRunScreen
import org.aegisos.guardianui.screens.GuardianHomeScreen
import org.aegisos.guardianui.screens.GuardianIncidentReviewScreen
import org.aegisos.guardianui.screens.GuardianIncidentUiState
import org.aegisos.guardianui.screens.GuardianRetentionOption
import org.aegisos.guardianui.screens.GuardianTrustScreen
import org.aegisos.guardianui.theme.GuardianColor
import org.aegisos.guardianui.theme.GuardianTheme

/**
 * Destinations in Guardian's UI. [INCIDENT_REVIEW] is named by
 * `GuardianWatcherService`'s notification PendingIntent through
 * `GuardianHomeActivity`'s `EXTRA_ROUTE`, so its name is part of a real
 * cross-process contract — do not rename it without updating both.
 *
 * The set is the five screens of the 2026-08-13 visual-identity design. The
 * superseded UI had two extra routes (GUARDING_NOW, RECENT_CHANGES) for
 * dedicated full-list screens; that design folds both into Home's own
 * "Recent changes" list, so those routes no longer exist. Nothing outside
 * the old navigation shell ever referenced them.
 */
enum class GuardianRoute {
    FIRST_RUN, HOME, TRUST, CHOICES, INCIDENT_REVIEW
}

/** What the incident-review screen is currently reviewing. */
private sealed interface GuardianReviewSource {
    val facts: GuardianIncidentFacts

    /** The head of the pending-detection queue, reviewed through [GuardianUiController]. */
    data class PendingDetection(override val facts: GuardianIncidentFacts) : GuardianReviewSource

    /** An applied, still-active protection, opened so the owner can undo it. */
    data class ActiveProtection(
        override val facts: GuardianIncidentFacts,
        val auditId: String,
    ) : GuardianReviewSource
}

/** The incident screen's two actions, resolved from the controller's state. */
private class IncidentActions(
    val facts: GuardianIncidentFacts,
    val primaryLabel: String,
    val secondaryLabel: String?,
    val onPrimary: () -> Unit,
    val onSecondary: () -> Unit,
)

/**
 * Navigation root. Home / Trust / Choices are bottom-nav tabs, First Run is
 * shown once (persistence is the caller's, via [onFirstRunSeen]), and
 * incident review is a pushed screen reached from a detection notification or
 * from a Home row.
 *
 * Deliberately a plain `when` with no transition animation: the
 * visual-identity design defers all motion work to a separate plan, and the
 * reference module's own navigation does the same.
 *
 * @param pendingIncident head of `GuardianApplication`'s detection queue, or
 *   null when nothing is waiting. Read separately from [controller] because
 *   `GuardianUiState.Detected` carries only a severity and an action ID — the
 *   explanation facts and timestamp the review screen shows live on the
 *   `GuardianIncidentV1` itself, which the controller consumes and discards.
 *   Both read the same queue head, so the two cannot disagree.
 * @param pendingIncidentCount size of that queue, for Home's attention line.
 * @param retention the retention period the audit store is really enforcing.
 *   Shown read-only; see `GuardianTrustUiState.retentionChangeable`.
 */
@Composable
fun GuardianApp(
    controller: GuardianUiController,
    startRoute: GuardianRoute = GuardianRoute.FIRST_RUN,
    onFirstRunSeen: () -> Unit = {},
    onDetectionHandled: () -> Unit = {},
    pendingIncident: () -> GuardianIncidentV1? = { null },
    pendingIncidentCount: () -> Int = { 0 },
    retention: GuardianRetentionOption = GuardianRetentionOption.ThirtyDays,
) {
    GuardianTheme {
        var overview by remember(controller) { mutableStateOf(controller.overview()) }
        var pendingFacts by remember(controller) { mutableStateOf(pendingIncident()?.toIncidentFacts()) }
        var pendingCount by remember(controller) { mutableStateOf(pendingIncidentCount()) }

        // A notification tap asks for INCIDENT_REVIEW; if the detection it
        // referred to has since been resolved there is nothing to review, so
        // fall back to Home rather than opening an empty screen.
        val reviewFromNotification =
            startRoute == GuardianRoute.INCIDENT_REVIEW && pendingFacts != null
        var review by remember(controller) {
            mutableStateOf<GuardianReviewSource?>(
                pendingFacts?.takeIf { reviewFromNotification }
                    ?.let { GuardianReviewSource.PendingDetection(it) },
            )
        }
        // start() is what arms the controller's protect(): only correct to
        // call when a review of the pending detection is actually opening.
        var lifecycle by remember(controller) {
            mutableStateOf(if (reviewFromNotification) controller.start() else null)
        }
        var route by remember(controller) {
            mutableStateOf(
                if (startRoute == GuardianRoute.INCIDENT_REVIEW && !reviewFromNotification) {
                    GuardianRoute.HOME
                } else {
                    startRoute
                },
            )
        }

        fun refresh() {
            overview = controller.overview()
            pendingFacts = pendingIncident()?.toIncidentFacts()
            pendingCount = pendingIncidentCount()
        }

        fun leaveReview() {
            review = null
            lifecycle = null
            refresh()
            route = GuardianRoute.HOME
        }

        fun openPendingReview() {
            val facts = pendingFacts ?: return
            review = GuardianReviewSource.PendingDetection(facts)
            lifecycle = controller.start()
            route = GuardianRoute.INCIDENT_REVIEW
        }

        fun openProtectionReview(action: GuardianHomeRowAction.UndoProtection) {
            review = GuardianReviewSource.ActiveProtection(action.facts, action.auditId)
            lifecycle = null
            route = GuardianRoute.INCIDENT_REVIEW
        }

        fun selectTab(index: Int) {
            when (index) {
                0 -> {
                    refresh()
                    route = GuardianRoute.HOME
                }
                1 -> route = GuardianRoute.TRUST
                2 -> route = GuardianRoute.CHOICES
            }
        }

        val home = remember(overview, pendingFacts, pendingCount) {
            overview.toHomeModel(pendingCount, pendingFacts)
        }
        val source = review
        val effectiveRoute =
            if (route == GuardianRoute.INCIDENT_REVIEW && source == null) GuardianRoute.HOME else route

        when (effectiveRoute) {
            GuardianRoute.FIRST_RUN -> GuardianFirstRunScreen(
                onGetStarted = {
                    onFirstRunSeen()
                    route = GuardianRoute.HOME
                },
            )

            GuardianRoute.HOME -> GuardianHomeScreen(
                state = home.state,
                onSettingsClick = { route = GuardianRoute.CHOICES },
                onRecentChangeClick = { change ->
                    when (val action = change.key?.let { home.actions[it] }) {
                        is GuardianHomeRowAction.ReviewPendingIncident -> openPendingReview()
                        is GuardianHomeRowAction.UndoProtection -> openProtectionReview(action)
                        // Informational row: nothing recorded that the owner
                        // can act on, so tapping it deliberately does nothing.
                        null -> Unit
                    }
                },
                onNavSelect = ::selectTab,
            )

            GuardianRoute.TRUST -> GuardianTrustScreen(
                state = overview.toTrustUiState(retention),
                // Never invoked: the selector is rendered read-only because
                // this build has no reviewed way to change the period.
                onRetentionSelected = {},
                onNavSelect = ::selectTab,
            )

            GuardianRoute.CHOICES -> GuardianChoicesScreen(
                state = guardianChoicesUiState(),
                // Never invoked: every switch on this screen is disabled
                // because it reports a fixed property of this build.
                onWatchToggle = { _, _ -> },
                onNotifyImmediatelyToggle = {},
                onNavSelect = ::selectTab,
            )

            GuardianRoute.INCIDENT_REVIEW -> IncidentReview(
                actions = incidentActions(
                    source = requireNotNull(source),
                    lifecycle = lifecycle,
                    onProtect = { lifecycle = controller.protect() },
                    onUndo = { auditId ->
                        lifecycle = if (auditId == null) controller.undo() else controller.undo(auditId)
                    },
                    // The owner finished with a pending detection, so it must
                    // leave the queue.
                    onResolved = {
                        onDetectionHandled()
                        leaveReview()
                    },
                    // The owner finished with something already recorded;
                    // there is no queue entry to drop.
                    onDismissed = ::leaveReview,
                ),
                // Navigating away is not a decision: a pending detection stays
                // pending and remains reachable from Home.
                onBack = ::leaveReview,
            )
        }
    }
}

/**
 * Resolves the incident screen's two actions from the controller's current
 * state. The visual-identity design puts the whole Observe -> Explain ->
 * Choose -> Enforce -> Verify -> Undo loop on this one screen, so every
 * controller outcome is rendered here rather than on separate result screens.
 */
private fun incidentActions(
    source: GuardianReviewSource,
    lifecycle: GuardianUiState?,
    onProtect: () -> Unit,
    onUndo: (String?) -> Unit,
    onResolved: () -> Unit,
    onDismissed: () -> Unit,
): IncidentActions {
    val pending = source is GuardianReviewSource.PendingDetection
    val undoAuditId = (source as? GuardianReviewSource.ActiveProtection)?.auditId
    val finish = if (pending) onResolved else onDismissed

    return when (lifecycle) {
        is GuardianUiState.Verified -> IncidentActions(
            facts = source.facts.asProtected(),
            primaryLabel = "Done",
            secondaryLabel = UNDO_LABEL,
            onPrimary = finish,
            onSecondary = { onUndo(undoAuditId) },
        )
        is GuardianUiState.Undone -> IncidentActions(
            facts = source.facts.asUndone(),
            primaryLabel = "Done",
            secondaryLabel = null,
            onPrimary = finish,
            onSecondary = {},
        )
        is GuardianUiState.Failure -> IncidentActions(
            facts = source.facts.asFailed(lifecycle.detail),
            primaryLabel = "Done",
            secondaryLabel = "Try again",
            onPrimary = finish,
            onSecondary = { if (pending) onProtect() else onUndo(undoAuditId) },
        )
        is GuardianUiState.Detected, null ->
            if (pending) {
                IncidentActions(
                    facts = source.facts,
                    primaryLabel = "Restrict ${source.facts.permissionLabel.replaceFirstChar { it.lowercase() }}",
                    secondaryLabel = "Not now",
                    onPrimary = onProtect,
                    onSecondary = onResolved,
                )
            } else {
                // An active protection opened from Home: already applied and
                // verified, so Undo is the only thing left to offer.
                IncidentActions(
                    facts = source.facts,
                    primaryLabel = "Done",
                    secondaryLabel = UNDO_LABEL,
                    onPrimary = onDismissed,
                    onSecondary = { onUndo(undoAuditId) },
                )
            }
    }
}

private const val UNDO_LABEL = "Undo"

@Composable
private fun IncidentReview(actions: IncidentActions, onBack: () -> Unit) {
    // Undo is the one control whose tapjacked activation reverses a
    // just-applied protection (ADR-034 residual 5). Guard the whole screen
    // whenever it is on offer.
    val guard =
        if (actions.secondaryLabel == UNDO_LABEL) Modifier.filterTouchesWhenObscured() else Modifier

    Box(modifier = guard) {
        GuardianIncidentReviewScreen(
            state = GuardianIncidentUiState(
                appName = actions.facts.appName,
                permissionLabel = actions.facts.permissionLabel,
                unusualReason = actions.facts.statusLine,
                observed = actions.facts.observed,
                firstSeen = actions.facts.firstSeen,
                ifRestricted = actions.facts.ifRestricted,
                // Deliberately a plain, unbranded placeholder. The design
                // calls for the reviewed app's own launcher icon, which needs
                // a package name guardian-core does not expose on this path
                // (see UNKNOWN_APP_LABEL). Drawing Guardian's own mark here
                // would misrepresent whose icon it is.
                appIconContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(GuardianColor.SurfaceContainerHigh),
                    )
                },
                tone = actions.facts.tone,
            ),
            onBack = onBack,
            onRestrict = actions.onPrimary,
            onNotNow = actions.onSecondary,
            primaryActionLabel = actions.primaryLabel,
            secondaryActionLabel = actions.secondaryLabel,
        )
    }
}
